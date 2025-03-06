package gg.embargo;

import com.google.common.collect.HashMultimap;
import com.google.gson.JsonParseException;
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
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;
import okhttp3.*;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
	name = "Embargo Clan",
	description = "A plugin to sync your account with Embargo",
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

	@Inject
	private EmbargoConfig config;

	@Inject
	private NoticeBoardManager noticeBoardManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private SyncButtonManager syncButtonManager;

	@Inject
	private ScheduledExecutorService scheduledExecutorService;

	@Inject
	private Gson gson;

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


	private NavigationButton navButton;
	private final HashMultimap<Integer, Integer> varpToVarbitMapping = HashMultimap.create();
	private final HashMap<String, Integer> skillLevelCache = new HashMap<>();
	private final int SECONDS_BETWEEN_UPLOADS = 30;
	private final int SECONDS_BETWEEN_MANIFEST_CHECKS = 5*60;
	private final int VARBITS_ARCHIVE_ID = 14;
	private static final Pattern COLLECTION_LOG_ITEM_REGEX = Pattern.compile("New item added to your collection log: (.*)");
	private final HashMap<String, LocalDateTime> lastLootTime = new HashMap<>();
	private final int SECONDS_BETWEEN_PROFILE_UPDATES = 15;
	private final String CONFIG_GROUP = "embargo";

	// CollectionLog Sync Stuff
	private static final String MANIFEST_URL = "https://embargo.gg/api/runelite/manifest";
	private static final String SUBMIT_URL = "https://embargo.gg/api/runelite/uploadcollectionlog";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private Map<Integer, VarbitComposition> varbitCompositions = new HashMap<>();

	private Manifest manifest;
	private Map<PlayerProfile, PlayerData> playerDataMap = new HashMap<>();
	private boolean webSocketStarted;
	private int cyclesSinceSuccessfulCall = 0;

	// Keeps track of what collection log slots the user has set.
	private static final BitSet clogItemsBitSet = new BitSet();
	private static Integer clogItemsCount = null;
	// Map item ids to bit index in the bitset
	private static final HashMap<Integer, Integer> collectionLogItemIdToBitsetIndex = new HashMap<>();
	private int tickCollectionLogScriptFired = -1;
	private final HashSet<Integer> collectionLogItemIdsFromCache = new HashSet<>();


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

		if (config != null && config.highlightClan()) {
			noticeBoardManager.setTOBNoticeBoard();
			noticeBoardManager.setTOANoticeBoard();
		}

		//CollectionLog Stuff
		clientThread.invoke(() -> {
			if (client.getIndexConfig() == null || client.getGameState().ordinal() < GameState.LOGIN_SCREEN.ordinal())
			{
				log.debug("Failed to get varbitComposition, state = {}", client.getGameState());
				return false;
			}
			collectionLogItemIdsFromCache.addAll(parseCacheForClog());
			populateCollectionLogItemIdToBitsetIndex();
			final int[] varbitIds = client.getIndexConfig().getFileIds(VARBITS_ARCHIVE_ID);
			for (int id : varbitIds)
			{
				varbitCompositions.put(id, client.getVarbit(id));
			}
			return true;
		});

		checkManifest();
		if (config.showCollectionLogSyncButton()) {
			syncButtonManager.startUp();
		}
	}

	@Override
	protected void shutDown() {
		log.info("Embargo Clan plugin stopped!");
		dataManager.clearData();
		panel.reset();
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;

		//checkProfileChange();
		noticeBoardManager.unsetNoticeBoard();

		//CollectionLog Stuff
		clogItemsBitSet.clear();
		clogItemsCount = null;
		syncButtonManager.shutDown();
	}

	/**
	 * Finds the index this itemId is assigned to in the collections mapping.
	 * @param itemId: The itemId to look up
	 * @return The index of the bit that represents the given itemId, if it is in the map. -1 otherwise.
	 */
	private int lookupCollectionLogItemIndex(int itemId) {
		// The map has not loaded yet, or failed to load.
		if (collectionLogItemIdToBitsetIndex.isEmpty()) {
			return -1;
		}
		Integer result = collectionLogItemIdToBitsetIndex.get(itemId);
		if (result == null) {
			log.debug("Item id {} not found in the mapping of items", itemId);
			return -1;
		}
		return result;
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
			panel.logOut();
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

	@Schedule(
		period = SECONDS_BETWEEN_PROFILE_UPDATES,
		unit = ChronoUnit.SECONDS,
		asynchronous = true
	)
	public void checkProfileChanged() {
		if (client.getLocalPlayer() != null && client.getGameState() == GameState.LOGGED_IN) {
			panel.updateLoggedIn(true);
			clientThread.invokeLater(this::checkProfileChange);
		}
	}

	@Getter
	public enum MinigameCompletionMessages {
		WINTERTODT("Your subdued Wintertodt count is:"), //g
		TEMPOROSS("Your Tempoross kill count is:"),
		GOTR("Amount of rifts you have closed:"),
		SOUL_WARS("team has defeated the Avatar"),
		BARBARIAN_ASSAULT("Wave 10 duration"),
		VOLCANIC_MINE("Your fragments disintegrate"); //g

		private final String completionMessage;

		MinigameCompletionMessages(String completionMessage) {
			this.completionMessage = completionMessage;
		}
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

		//Point History generation for new collection log
		Matcher m = COLLECTION_LOG_ITEM_REGEX.matcher(chatMessage.getMessage());
		RuneScapeProfileType profType = RuneScapeProfileType.getCurrent(client);
		if (profType == RuneScapeProfileType.STANDARD && chatMessage.getType() == ChatMessageType.GAMEMESSAGE && m.matches()) {
			String obtainedItemName = Text.removeTags(m.group(1));
			dataManager.uploadCollectionLogUnlock(obtainedItemName, player.getName());
		}

		if (profType == RuneScapeProfileType.STANDARD && (chatMessage.getType() == ChatMessageType.GAMEMESSAGE || chatMessage.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION || chatMessage.getType() == ChatMessageType.SPAM))
		{
			String message = chatMessage.getMessage();
			handleActivityCompletion(message);
		}
	}

	public void handleActivityCompletion(String chatMessage) {
		for (RaidCompletionMessages r : RaidCompletionMessages.values())
		{
			if (chatMessage.contains(r.getCompletionMessage()))
			{
				dataManager.uploadRaidCompletion(r.name(), chatMessage);
			}
		}

		for (MinigameCompletionMessages mg : MinigameCompletionMessages.values())
		{
			if (chatMessage.contains(mg.getCompletionMessage()))
			{
				dataManager.uploadMinigameCompletion(mg.name(), chatMessage);
			}
		}
	}

	//CollectionLog Subscribe
	@Subscribe
	public void onScriptPreFired(ScriptPreFired preFired) {
		if (syncButtonManager.isSyncAllowed() && preFired.getScriptId() == 4100) {
			tickCollectionLogScriptFired = client.getTickCount();
			if (collectionLogItemIdToBitsetIndex.isEmpty())
			{
				return;
			}
			clogItemsCount = collectionLogItemIdsFromCache.size();
			Object[] args = preFired.getScriptEvent().getArguments();
			int itemId = (int) args[1];
			int idx = lookupCollectionLogItemIndex(itemId);
			// We should never return -1 under normal circumstances
			if (idx != -1)
				clogItemsBitSet.set(idx);
		}
	}

	@Schedule(
			period = SECONDS_BETWEEN_UPLOADS,
			unit = ChronoUnit.SECONDS,
			asynchronous = true
	)
	public void queueSubmitTask() {
		scheduledExecutorService.execute(this::submitTask);
	}

	synchronized public void submitTask()
	{
		// TODO: do we want other GameStates?
		if (client.getGameState() != GameState.LOGGED_IN || varbitCompositions.isEmpty())
		{
			return;
		}

		if (manifest == null || client.getLocalPlayer() == null)
		{
			log.debug("Skipped due to bad manifest: {}", manifest);
			return;
		}

		String username = client.getLocalPlayer().getName();
		RuneScapeProfileType profileType = RuneScapeProfileType.getCurrent(client);
		PlayerProfile profileKey = new PlayerProfile(username, profileType);

		PlayerData newPlayerData = getPlayerData();
		PlayerData oldPlayerData = playerDataMap.computeIfAbsent(profileKey, k -> new PlayerData());

		// Subtraction is done in place so newPlayerData becomes a map of only changed fields
		subtract(newPlayerData, oldPlayerData);
		if (newPlayerData.isEmpty())
		{
			return;
		}
		submitPlayerData(profileKey, newPlayerData, oldPlayerData);
	}

	@Schedule(
			period = SECONDS_BETWEEN_MANIFEST_CHECKS,
			unit = ChronoUnit.SECONDS,
			asynchronous = true
	)
	public void manifestTask()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			checkManifest();
		}
	}

	private PlayerData getPlayerData()
	{
		PlayerData out = new PlayerData();
		for (int varbitId : manifest.varbits)
		{
			out.varb.put(varbitId, getVarbitValue(varbitId));
		}
		for (int varpId : manifest.varps)
		{
			out.varp.put(varpId, client.getVarpValue(varpId));
		}
		for(Skill s : Skill.values())
		{
			out.level.put(s.getName(), client.getRealSkillLevel(s));
		}
		out.collectionLogSlots = Base64.getEncoder().encodeToString(clogItemsBitSet.toByteArray());
		out.collectionLogItemCount = clogItemsCount;
		return out;
	}

	private void subtract(PlayerData newPlayerData, PlayerData oldPlayerData)
	{
		oldPlayerData.varb.forEach(newPlayerData.varb::remove);
		oldPlayerData.varp.forEach(newPlayerData.varp::remove);
		oldPlayerData.level.forEach(newPlayerData.level::remove);
		if (newPlayerData.collectionLogSlots.equals(oldPlayerData.collectionLogSlots))
			newPlayerData.clearCollectionLog();
	}

	private void merge(PlayerData oldPlayerData, PlayerData delta)
	{
		oldPlayerData.varb.putAll(delta.varb);
		oldPlayerData.varp.putAll(delta.varp);
		oldPlayerData.level.putAll(delta.level);
		oldPlayerData.collectionLogSlots = delta.collectionLogSlots;
		oldPlayerData.collectionLogItemCount = delta.collectionLogItemCount;
	}

	private void submitPlayerData(PlayerProfile profileKey, PlayerData delta, PlayerData old)
	{
		// If cyclesSinceSuccessfulCall is not a perfect square, we should not try to submit.
		// This gives us quadratic backoff.
		cyclesSinceSuccessfulCall += 1;
		if (Math.pow((int) Math.sqrt(cyclesSinceSuccessfulCall), 2) != cyclesSinceSuccessfulCall)
		{
			return;
		}

		PlayerDataSubmission submission = new PlayerDataSubmission(
				profileKey.getUsername(),
				profileKey.getProfileType().name(),
				delta
		);

		Request request = new Request.Builder()
				.url(SUBMIT_URL)
				.post(RequestBody.create(JSON, gson.toJson(submission)))
				.build();

		Call call = okHttpClient.newCall(request);
		call.timeout().timeout(3, TimeUnit.SECONDS);
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Failed to submit: ", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try
				{
					if (!response.isSuccessful()) {
						log.debug("Failed to submit: {}", response.code());
						return;
					}
					merge(old, delta);
					cyclesSinceSuccessfulCall = 0;
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	private void checkManifest()
	{
		Request request = new Request.Builder()
				.url(MANIFEST_URL)
				.build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Failed to get manifest: ", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try
				{
					if (!response.isSuccessful())
					{
						log.debug("Failed to get manifest: {}", response.code());
						return;
					}
					log.debug("Got manifest: {}", response.body());
					InputStream in = response.body().byteStream();
					manifest = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Manifest.class);
					populateCollectionLogItemIdToBitsetIndex();
				}
				catch (JsonParseException e)
				{
					log.debug("Failed to parse manifest: ", e);
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	private void populateCollectionLogItemIdToBitsetIndex()
	{
		if (manifest == null)
		{
			log.debug("Manifest is not present so the collection log bitset index will not be updated");
			return;
		}
		clientThread.invoke(() -> {
			// Add missing keys in order to the map. Order is extremely important here so
			// we get a stable map given the same cache data.
			List<Integer> itemIdsMissingFromManifest = collectionLogItemIdsFromCache
					.stream()
					.filter((t) -> !manifest.collections.contains(t))
					.sorted()
					.collect(Collectors.toList());

			int currentIndex = 0;
			collectionLogItemIdToBitsetIndex.clear();
			for (Integer itemId : manifest.collections)
				collectionLogItemIdToBitsetIndex.put(itemId, currentIndex++);
			for (Integer missingItemId : itemIdsMissingFromManifest) {
				collectionLogItemIdToBitsetIndex.put(missingItemId, currentIndex++);
			}
		});
	}

	/**
	 * Parse the enums and structs in the cache to figure out which item ids
	 * exist in the collection log. This can be diffed with the manifest to
	 * determine the item ids that need to be appended to the end of the
	 * bitset we send to the Embargo server.
	 */
	private HashSet<Integer> parseCacheForClog()
	{
		HashSet<Integer> itemIds = new HashSet<>();
		// 2102 - Struct that contains the highest level tabs in the collection log (Bosses, Raids, etc)
		// https://chisel.weirdgloop.org/structs/index.html?type=enums&id=2102
		int[] topLevelTabStructIds = client.getEnum(2102).getIntVals();
		for (int topLevelTabStructIndex : topLevelTabStructIds)
		{
			// The collection log top level tab structs contain a param that points to the enum
			// that contains the pointers to sub tabs.
			// ex: https://chisel.weirdgloop.org/structs/index.html?type=structs&id=471
			StructComposition topLevelTabStruct = client.getStructComposition(topLevelTabStructIndex);

			// Param 683 contains the pointer to the enum that contains the subtabs ids
			// ex: https://chisel.weirdgloop.org/structs/index.html?type=enums&id=2103
			int[] subtabStructIndices = client.getEnum(topLevelTabStruct.getIntValue(683)).getIntVals();
			for (int subtabStructIndex : subtabStructIndices) {

				// The subtab structs are for subtabs in the collection log (Commander Zilyana, Chambers of Xeric, etc.)
				// and contain a pointer to the enum that contains all the item ids for that tab.
				// ex subtab struct: https://chisel.weirdgloop.org/structs/index.html?type=structs&id=476
				// ex subtab enum: https://chisel.weirdgloop.org/structs/index.html?type=enums&id=2109
				StructComposition subtabStruct = client.getStructComposition(subtabStructIndex);
				int[] clogItems = client.getEnum(subtabStruct.getIntValue(690)).getIntVals();
				for (int clogItemId : clogItems) itemIds.add(clogItemId);
			}
		}

		// Some items with data saved on them have replacements to fix a duping issue (satchels, flamtaer bag)
		// Enum 3721 contains a mapping of the item ids to replace -> ids to replace them with
		EnumComposition replacements = client.getEnum(3721);
		for (int badItemId : replacements.getKeys())
			itemIds.remove(badItemId);
		for (int goodItemId : replacements.getIntVals())
			itemIds.add(goodItemId);

		return itemIds;
	}



	private int getVarbitValue(int varbitId)
	{
		VarbitComposition v = varbitCompositions.get(varbitId);
		if (v == null)
		{
			return -1;
		}

		int value = client.getVarpValue(v.getIndex());
		int lsb = v.getLeastSignificantBit();
		int msb = v.getMostSignificantBit();
		int mask = (1 << ((msb - lsb) + 1)) - 1;
		return (value >> lsb) & mask;
	}

	//Required for collectionLog stuff
	@Subscribe
	public void onGameTick(GameTick gameTick) {
		// Submit the collection log data two ticks after the first script prefires
		if (tickCollectionLogScriptFired != -1 &&
				tickCollectionLogScriptFired + 2 > client.getTickCount()) {
			tickCollectionLogScriptFired = -1;
			if (manifest == null) {
				client.addChatMessage(ChatMessageType.CONSOLE, "Embargo", "Failed to sync collection log. Try restarting the Embargo plugin.", "Embargo");
				return;
			}
			scheduledExecutorService.execute(this::submitTask);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		//CollectionLog Stuff
		switch (event.getGameState())
		{
			// When hopping, we need to clear any state related to the player
			case HOPPING:
			case LOGGING_IN:
			case CONNECTION_LOST:
				clogItemsBitSet.clear();
				clogItemsCount = null;
				break;
		}

		if (event.getGameState() == GameState.LOADING) {
			return;
		}

		if (event.getGameState() == GameState.LOGIN_SCREEN) {
			panel.isLoggedIn = false;
			panel.logOut();
			return;
		}

		if (client != null && client.getLocalPlayer() != null && client.getLocalPlayer().getName() == null && event.getGameState() == GameState.LOGGED_IN) {
			clientThread.invokeLater(() -> {
				if (client.getLocalPlayer().getName() == null) return false;

				panel.isLoggedIn = true;
				panel.updateLoggedIn(true);
				if (config != null && config.highlightClan()) {
					noticeBoardManager.setTOBNoticeBoard();
					noticeBoardManager.setTOANoticeBoard();
				}
				return true;
			});
		}
	}

	public void checkProfileChange()
	{
		if (client == null) return;

		RuneScapeProfileType r = RuneScapeProfileType.getCurrent(client);
		if (r == RuneScapeProfileType.STANDARD && r != lastProfile && client != null && varbitsToCheck != null && varpsToCheck != null && this.client.getGameState() == GameState.LOGGED_IN)
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
		for (int varbIndex : varbitsToCheck)
		{
			dataManager.storeVarbitChanged(varbIndex, client.getVarbitValue(varbIndex));
		}

		for (int varpIndex : varpsToCheck)
		{
			dataManager.storeVarpChanged(varpIndex, client.getVarpValue(varpIndex));
		}
		for (Skill s : Skill.values())
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
            lastLootTime.put(username, LocalDateTime.now().plusMinutes(3));
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

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		clientThread.invokeLater(() ->
		{
			// TOB
			if (widgetLoaded.getGroupId() == 364 || widgetLoaded.getGroupId() == 50)
			{
				noticeBoardManager.setTOBNoticeBoard();
			}

			// TOA
			if (widgetLoaded.getGroupId() == 772 || widgetLoaded.getGroupId() == 774) {
				noticeBoardManager.setTOANoticeBoard();
			}
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals(CONFIG_GROUP))
		{
			return;
		}

		noticeBoardManager.unsetNoticeBoard();
		if (config.highlightClan()) {
			noticeBoardManager.setTOBNoticeBoard();
			noticeBoardManager.setTOANoticeBoard();
		}

		if (config.showCollectionLogSyncButton()) {
			syncButtonManager.startUp();
		} else {
			syncButtonManager.shutDown();
		}
	}
}
