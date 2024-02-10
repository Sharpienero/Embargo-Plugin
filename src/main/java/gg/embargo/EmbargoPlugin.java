package gg.embargo;

import com.google.common.collect.HashMultimap;
import com.google.gson.Gson;
import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.task.Schedule;
import net.runelite.api.InventoryID;

import net.runelite.client.callback.ClientThread;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@PluginDescriptor(
	name = "Embargo Clan",
	description = "A plugin to help Embargo Clan properly track items and accomplishments.",
	tags = {"embargo", "clan", "embargo.gg", "ironman"}
)
public class EmbargoPlugin extends Plugin {

	@Inject
	private DataManager dataManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	@Inject
	private EmbargoConfig config;

	@Getter
	@Setter
	private int lastManifestVersion = -1;

	private int[] oldVarps;
	private RuneScapeProfileType lastProfile;

	@Setter
	private HashSet<Integer> varbitsToCheck;

	@Setter
	private HashSet<Integer> varpsToCheck;

	private final HashMultimap<Integer, Integer> varpToVarbitMapping = HashMultimap.create();
	private final HashMap<String, Integer> skillLevelCache = new HashMap<>();
	private final int SECONDS_BETWEEN_UPLOADS = 10;
	private final int SECONDS_BETWEEN_MANIFEST_CHECKS = 20*60;
	private final int VARBITS_ARCHIVE_ID = 14;

	public static final String CONFIG_GROUP_KEY = "WikiSync";
	// THIS VERSION SHOULD BE INCREMENTED EVERY RELEASE WHERE WE ADD A NEW TOGGLE
	public static final int VERSION = 1;

	@Provides
	EmbargoConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(EmbargoConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("WikiSync started!");
		setTogglesBasedOnVersion();
		lastProfile = null;
		varbitsToCheck = null;
		varpsToCheck = null;
		skillLevelCache.clear();
		dataManager.getManifest();
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("WikiSync stopped!");
		dataManager.clearData();
	}

	@Schedule(
			period = SECONDS_BETWEEN_UPLOADS,
			unit = ChronoUnit.SECONDS,
			asynchronous = true
	)
	public void submitToAPI()
	{
		if (client != null && client.getGameState() != GameState.HOPPING)
			dataManager.submitToAPI();
	}

	@Schedule(
			period = SECONDS_BETWEEN_MANIFEST_CHECKS,
			unit = ChronoUnit.SECONDS,
			asynchronous = true
	)
	public void resyncManifest()
	{
		log.debug("Attempting to resync manifest");
		if (dataManager.getVersion() != lastManifestVersion)
		{
			dataManager.getManifest();
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		// Call a helper function since it needs to be called from DataManager as well
		checkProfileChange();
	}

	public void checkProfileChange()
	{
		RuneScapeProfileType r = RuneScapeProfileType.getCurrent(client);
		if (r != lastProfile && client != null && varbitsToCheck != null && varpsToCheck != null)
		{
			// profile change, we should clear the datamanager and do a new initial dump
			log.debug("Profile seemed to change... Reloading all data and updating profile");
			lastProfile = r;
			dataManager.clearData();
			loadInitialData();
		}
	}

	// Need to keep track of old varps and what varps each varb is in.
	// On change
	// Get varp, if varp in hashset, queue it.
	// Get each varb index in varp. If varb changed and varb in hashset, queue it.
	// Checking if varb has changed requires us to keep track of old varps
	private void setupVarpTracking()
	{
		// Init stuff to keep track of varb changes
		varpToVarbitMapping.clear();

		if (oldVarps == null)
		{
			oldVarps = new int[client.getVarps().length];
		}

		// Set oldVarps to be the current varps
		System.arraycopy(client.getVarps(), 0, oldVarps, 0, oldVarps.length);

		// For all varbits, add their ids to the multimap with the varp index as their key
		clientThread.invoke(() -> {
			if (client.getIndexConfig() == null)
			{
				return false;
			}
			IndexDataBase indexVarbits = client.getIndexConfig();
			final int[] varbitIds = indexVarbits.getFileIds(VARBITS_ARCHIVE_ID);
			for (int id : varbitIds)
			{
				VarbitComposition varbit = client.getVarbit(id);
				if (varbit != null)
				{
					varpToVarbitMapping.put(varbit.getIndex(), id);
				}
			}
			return true;
		});
	}

	public void loadInitialData()
	{
		for(int varbIndex : varbitsToCheck)
		{
			dataManager.storeVarbitChanged(varbIndex, client.getVarbitValue(varbIndex));
		}

		for(int varpIndex : varpsToCheck)
		{
			dataManager.storeVarpChanged(varpIndex, client.getVarpValue(varpIndex));
		}
		for(Skill s : Skill.values())
		{
			if (s != Skill.OVERALL)
				dataManager.storeSkillChanged(s.getName(), client.getRealSkillLevel(s));
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		if (client == null || varbitsToCheck == null || varpsToCheck == null)
			return;
		if (oldVarps == null)
			setupVarpTracking();

		int varpIndexChanged = varbitChanged.getIndex();
		if (varpsToCheck.contains(varpIndexChanged))
		{
			dataManager.storeVarpChanged(varpIndexChanged, client.getVarpValue(varpIndexChanged));
		}
		for (Integer i : varpToVarbitMapping.get(varpIndexChanged))
		{
			if (!varbitsToCheck.contains(i))
				continue;
			// For each varbit index, see if it changed.
			int oldValue = client.getVarbitValue(oldVarps, i);
			int newValue = client.getVarbitValue(i);
			if (oldValue != newValue)
				dataManager.storeVarbitChanged(i, newValue);
		}
		oldVarps[varpIndexChanged] = client.getVarpValue(varpIndexChanged);
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged)
	{
		if (statChanged.getSkill() == null || statChanged.getSkill() == Skill.OVERALL)
			return;
		Integer cachedLevel = skillLevelCache.get(statChanged.getSkill().getName());
		if (cachedLevel == null || cachedLevel != statChanged.getLevel())
		{
			skillLevelCache.put(statChanged.getSkill().getName(), statChanged.getLevel());
			dataManager.storeSkillChanged(statChanged.getSkill().getName(), statChanged.getLevel());
		}
	}

	private void setTogglesBasedOnVersion()
	{
//		// Conditionally turn off certain features by default
//		Integer version = configManager.getConfiguration(CONFIG_GROUP_KEY, WikiSyncConfig.WIKISYNC_VERSION_KEYNAME, Integer.class);
//		if (version == null)
//			return;
//		int maxVersion = version;
//		/* EXAMPLE TOGGLE SETTING CLAUSE */
//		/* if (version < 2)
//		{
//			// Location tracking was added in deploy 2
//			configManager.setConfiguration(CONFIG_GROUP_KEY, WikiSyncConfig.WIKISYNC_TOGGLE_KEYNAME, false);
//			maxVersion = 2;
//		}
//		*/
//
//		// This is done here and not in each block because we don't want to rely on the order of the if clauses being correct.
//		//configManager.setConfiguration(CONFIG_GROUP_KEY, WikiSyncConfig.WIKISYNC_VERSION_KEYNAME, maxVersion);
//		log.debug("WikiSync version set to deployment number " + version);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == 277) {
			this.getUntrackableItems(786445, InventoryID.BANK);
		}
	}

	@Getter
	enum UntrackableItems {

		BOOK_OF_THE_DEAD(25818, 25819);


		private final int itemId;

		private final int placeholderId;

		private UntrackableItems(int itemId, int placeholderId) {
			this.itemId = itemId;
			this.placeholderId = placeholderId;
		}
	}

	private void getUntrackableItems(int componentId, InventoryID inventoryID) {
		Widget widget = this.client.getWidget(componentId);
		ItemContainer itemContainer = this.client.getItemContainer(inventoryID);
		Widget[] children = widget.getChildren();
		if (itemContainer != null && children != null) {

			for(int i = 0; i < itemContainer.size(); ++i) {
				Widget child = children[i];
				var id = child.getItemId();
				System.out.println(id);
			}

		}

	}

}
