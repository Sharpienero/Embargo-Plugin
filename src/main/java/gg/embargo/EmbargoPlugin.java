package gg.embargo;

import com.google.common.collect.HashMultimap;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.loottracker.LootRecordType;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;

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
	private UntrackableItemManager untrackableItemManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	@Getter
	@Setter
	private int lastManifestVersion = -1;

	private int[] oldVarps;

	private RuneScapeProfileType lastProfile;

	@Setter
	private HashSet<Integer> varbitsToCheck;

	@Setter
	private HashSet<Integer> varpsToCheck;

	@Getter
	private EmbargoPanel panel;

	@Inject
	private ClientToolbar clientToolbar;

	private BufferedImage icon;
	private NavigationButton navButton;

	private final HashMultimap<Integer, Integer> varpToVarbitMapping = HashMultimap.create();
	private final HashMap<String, Integer> skillLevelCache = new HashMap<>();
	private final int SECONDS_BETWEEN_UPLOADS = 30;
	private final int SECONDS_BETWEEN_MANIFEST_CHECKS = 5*60;
	private final int VARBITS_ARCHIVE_ID = 14;
	private final int MINUTES_BETWEEN_UNTRACKABLE_ITEM_CHECKS = 3;

    //instantiate map that takes String: datetime
	private final HashMap<String, LocalDateTime> lastLootTime = new HashMap<>();

	@Provides
	EmbargoConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(EmbargoConfig.class);
	}

	@Override
	protected void startUp() {
		log.info("Embargo Clan plugin started!");

		//Let's build out the side panels
		panel = injector.getInstance(EmbargoPanel.class);
		panel.init();
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Embargo Clan")
				.icon(icon)
				.priority(0)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);

		lastProfile = null;
		varbitsToCheck = null;
		varpsToCheck = null;
		skillLevelCache.clear();
		dataManager.getManifest();
		panel.updateLoggedIn(false);
	}

	@Override
	protected void shutDown() {
		log.info("Embargo Clan plugin stopped!");
		dataManager.clearData();
		panel.reset();
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;

		checkProfileChange();
	}
	@Schedule(
			period = SECONDS_BETWEEN_UPLOADS,
			unit = ChronoUnit.SECONDS,
			asynchronous = true
	)
	public void submitToAPI()
	{
		if (client != null && (client.getGameState() != GameState.HOPPING && client.getGameState() != GameState.LOGIN_SCREEN)) {
			dataManager.submitToAPI();
			if (client.getLocalPlayer() != null) {
				String username = client.getLocalPlayer().getName();
				if (dataManager.checkRegistered(username)) {
					log.debug("updateProfileAfterLoggedIn Member registered");
					panel.updateLoggedIn(true);
				}
			}
		} else {
			log.debug("User is hopping or logged out, do not send data");
		}
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
		if (!panel.isLoggedIn && client.getLocalPlayer() != null) {
			panel.updateLoggedIn(false);
		}
		// Call a helper function since it needs to be called from DataManager as well
		checkProfileChange();
	}

	@Getter
	public enum RaidCompletionMessages {
		COX("Congratulations - your raid is complete!"),
		COX_CM("Your completed Chambers of Xeric Challenge Mode count is:"),
		TOB("Theatre of Blood total completion time:"),
		HM_TOB("Your completed Theatre of Blood: Hard Mode count is:"),
		TOA("Tombs of Amascut total completion time:"),
		TOA_EXPERT("Tombs of Amascut: Expert Mode total completion time:");

		private final String completionMessage;

		RaidCompletionMessages(String completionMessage) {
			this.completionMessage = completionMessage;
		}

	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		Player player = client.getLocalPlayer();

		if (player == null)
		{
			return;
		}

		if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE || chatMessage.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION || chatMessage.getType() == ChatMessageType.SPAM)
		{
			String message = chatMessage.getMessage();
			for (RaidCompletionMessages r : RaidCompletionMessages.values())
			{
				if (message.contains(r.getCompletionMessage()))
				{
					dataManager.uploadRaidCompletion(r.name(), message);
				}
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			log.info("Resetting Embargo Side Panel");
			panel.isLoggedIn = false;
			panel.logOut();
		}
	}

	public void checkProfileChange()
	{
		if (client.getLocalPlayer() != null) {

		}

		RuneScapeProfileType r = RuneScapeProfileType.getCurrent(client);
		if (r == RuneScapeProfileType.STANDARD && r != lastProfile && client != null && varbitsToCheck != null && varpsToCheck != null )
		{
			// profile change, we should clear the dataManager and do a new initial dump
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
		if (statChanged.getSkill() == null)
			return;
		Integer cachedLevel = skillLevelCache.get(statChanged.getSkill().getName());
		if (cachedLevel == null || cachedLevel != statChanged.getLevel())
		{
			skillLevelCache.put(statChanged.getSkill().getName(), statChanged.getLevel());
			dataManager.storeSkillChanged(statChanged.getSkill().getName(), statChanged.getLevel());
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == 277) {
			if (client == null || client.getLocalPlayer() == null) {
				return;
			}

			var username = client.getLocalPlayer().getName();

            if (lastLootTime.containsKey(username)) {
                LocalDateTime lastLootTimestamp = lastLootTime.get(username);

                if (LocalDateTime.now().isBefore(lastLootTimestamp)) {
                    log.debug("Player has opened bank within the last 3 minutes, not checking for untrackable items");
                    return;
                }

            }
            untrackableItemManager.getUntrackableItems(username);
            lastLootTime.put(username, LocalDateTime.now().plusMinutes(MINUTES_BETWEEN_UNTRACKABLE_ITEM_CHECKS));
        }
	}

	@Subscribe
	public void onLootReceived(final LootReceived event)
	{
		if (event.getType() != LootRecordType.NPC && event.getType() != LootRecordType.EVENT)
		{
			return;
		}

		if (dataManager.shouldTrackLoot(event.getName())) {
			log.debug("Player killed " + event.getName());
			dataManager.uploadLoot(event);
		} else {
			log.debug("Player killed " + event.getName() + " , nothing to log");
		}
	}
}
