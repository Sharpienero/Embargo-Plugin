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

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.google.gson.Gson;
import okhttp3.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(name = "Embargo Clan", description = "A plugin to sync your account with Embargo", tags = { "embargo",
		"clan", "embargo.gg", "ironman" })
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
	private SyncButtonManager syncButtonManager;

	@Inject
	private ScheduledExecutorService scheduledExecutorService;

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient okHttpClient;

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
	private NavigationButton navButton;

	private final HashMultimap<Integer, Integer> varpToVarbitMapping = HashMultimap.create();
	private final HashMap<String, Integer> skillLevelCache = new HashMap<>();
	private final int SECONDS_BETWEEN_UPLOADS = 30;
	private final int SECONDS_BETWEEN_MANIFEST_CHECKS = 5 * 60;
	private final int VARBITS_ARCHIVE_ID = 14;
	private static final Pattern COLLECTION_LOG_ITEM_REGEX = Pattern
			.compile("New item added to your collection log: (.*)");
	private final HashMap<String, LocalDateTime> lastLootTime = new HashMap<>();
	private final int SECONDS_BETWEEN_PROFILE_UPDATES = 15;
	private final String CONFIG_GROUP = "embargo";

	// Keeps track of what collection log slots the user has set.
	private static final BitSet clogItemsBitSet = new BitSet();
	private static Integer clogItemsCount = null;
	// Map item ids to bit index in the bitset
	private static final HashMap<Integer, Integer> collectionLogItemIdToBitsetIndex = new HashMap<>();
	private int tickCollectionLogScriptFired = -1;
	private final HashSet<Integer> collectionLogItemIdsFromCache = new HashSet<>();
	private Map<Integer, VarbitComposition> varbitCompositions = new HashMap<>();
	private Manifest manifest;

	private Map<PlayerProfile, PlayerData> playerDataMap = new HashMap<>();

	private boolean webSocketStarted;

	private int cyclesSinceSuccessfulCall = 0;
	private static final String SUBMIT_URL = "https://embargo.gg/api/runelite/uploadcollectionlog";

	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	@Provides
	EmbargoConfig getConfig(ConfigManager configManager) {
		return configManager.getConfig(EmbargoConfig.class);
	}

	@Override
	protected void startUp() {
		log.info("Embargo Clan plugin started!");

		// Let's build out the side panels
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

		clientThread.invoke(() -> {
			if (client.getIndexConfig() == null || client.getGameState().ordinal() < GameState.LOGIN_SCREEN.ordinal()) {
				log.debug("Failed to get varbitComposition, state = {}", client.getGameState());
				return false;
			}
			collectionLogItemIdsFromCache.addAll(parseCacheForClog());
			populateCollectionLogItemIdToBitsetIndex();
			final int[] varbitIds = client.getIndexConfig().getFileIds(VARBITS_ARCHIVE_ID);
			for (int id : varbitIds) {
				varbitCompositions.put(id, client.getVarbit(id));
			}
			return true;
		});

		syncButtonManager.startUp();
	}

	/**
	 * Parse the enums and structs in the cache to figure out which item ids
	 * exist in the collection log. This can be diffed with the manifest to
	 * determine the item ids that need to be appended to the end of the
	 * bitset we send to the Embargo server.
	 */

	private HashSet<Integer> parseCacheForClog() {
		HashSet<Integer> itemIds = new HashSet<>();
		// 2102 - Struct that contains the highest level tabs in the collection log
		// (Bosses, Raids, etc)
		// https://chisel.weirdgloop.org/structs/index.html?type=enums&id=2102
		int[] topLevelTabStructIds = client.getEnum(2102).getIntVals();
		for (int topLevelTabStructIndex : topLevelTabStructIds) {
			// The collection log top level tab structs contain a param that points to the
			// enum
			// that contains the pointers to sub tabs.
			// ex: https://chisel.weirdgloop.org/structs/index.html?type=structs&id=471

			StructComposition topLevelTabStruct = client.getStructComposition(topLevelTabStructIndex);

			// Param 683 contains the pointer to the enum that contains the subtabs ids
			// ex: https://chisel.weirdgloop.org/structs/index.html?type=enums&id=2103

			int[] subtabStructIndices = client.getEnum(topLevelTabStruct.getIntValue(683)).getIntVals();
			for (int subtabStructIndex : subtabStructIndices) {
				// The subtab structs are for subtabs in the collection log (Commander Zilyana,
				// Chambers of Xeric, etc.)
				// and contain a pointer to the enum that contains all the item ids for that
				// tab.
				// ex subtab struct:
				// https://chisel.weirdgloop.org/structs/index.html?type=structs&id=476
				// ex subtab enum:
				// https://chisel.weirdgloop.org/structs/index.html?type=enums&id=2109
				StructComposition subtabStruct = client.getStructComposition(subtabStructIndex);
				int[] clogItems = client.getEnum(subtabStruct.getIntValue(690)).getIntVals();
				for (int clogItemId : clogItems)
					itemIds.add(clogItemId);
			}
		}

		// Some items with data saved on them have replacements to fix a duping issue
		// (satchels, flamtaer bag)
		// Enum 3721 contains a mapping of the item ids to replace -> ids to replace
		// them with

		EnumComposition replacements = client.getEnum(3721);
		for (int badItemId : replacements.getKeys())
			itemIds.remove(badItemId);

		for (int goodItemId : replacements.getIntVals())
			itemIds.add(goodItemId);

		return itemIds;
	}

	private void populateCollectionLogItemIdToBitsetIndex() {
		if (lastManifestVersion == -1) {
			log.debug("Manifest is not present so the collection log bitset index will not be updated");
			return;
		}

		ArrayList<Integer> hc_clogs = new ArrayList<>();
		hc_clogs.add(1249);
		hc_clogs.add(2366);
		hc_clogs.add(2577);
		hc_clogs.add(2579);
		hc_clogs.add(2581);
		hc_clogs.add(2583);
		hc_clogs.add(2585);
		hc_clogs.add(2587);
		hc_clogs.add(2589);
		hc_clogs.add(2591);
		hc_clogs.add(2593);
		hc_clogs.add(2595);
		hc_clogs.add(2597);
		hc_clogs.add(2599);
		hc_clogs.add(2601);
		hc_clogs.add(2603);
		hc_clogs.add(2605);
		hc_clogs.add(2607);
		hc_clogs.add(2609);
		hc_clogs.add(2611);
		hc_clogs.add(2613);
		hc_clogs.add(2615);
		hc_clogs.add(2617);
		hc_clogs.add(2619);
		hc_clogs.add(2621);
		hc_clogs.add(2623);
		hc_clogs.add(2625);
		hc_clogs.add(2627);
		hc_clogs.add(2629);
		hc_clogs.add(2631);
		hc_clogs.add(2633);
		hc_clogs.add(2635);
		hc_clogs.add(2637);
		hc_clogs.add(2639);
		hc_clogs.add(2641);
		hc_clogs.add(2643);
		hc_clogs.add(2645);
		hc_clogs.add(2647);
		hc_clogs.add(2649);
		hc_clogs.add(2651);
		hc_clogs.add(2653);
		hc_clogs.add(2655);
		hc_clogs.add(2657);
		hc_clogs.add(2659);
		hc_clogs.add(2661);
		hc_clogs.add(2663);
		hc_clogs.add(2665);
		hc_clogs.add(2667);
		hc_clogs.add(2669);
		hc_clogs.add(2671);
		hc_clogs.add(2673);
		hc_clogs.add(2675);
		hc_clogs.add(2978);
		hc_clogs.add(2979);
		hc_clogs.add(2980);
		hc_clogs.add(2981);
		hc_clogs.add(2982);
		hc_clogs.add(2983);
		hc_clogs.add(2984);
		hc_clogs.add(2985);
		hc_clogs.add(2986);
		hc_clogs.add(2987);
		hc_clogs.add(2988);
		hc_clogs.add(2989);
		hc_clogs.add(2990);
		hc_clogs.add(2991);
		hc_clogs.add(2992);
		hc_clogs.add(2993);
		hc_clogs.add(2994);
		hc_clogs.add(2995);
		hc_clogs.add(2996);
		hc_clogs.add(2997);
		hc_clogs.add(3057);
		hc_clogs.add(3058);
		hc_clogs.add(3059);
		hc_clogs.add(3060);
		hc_clogs.add(3061);
		hc_clogs.add(3140);
		hc_clogs.add(3470);
		hc_clogs.add(3472);
		hc_clogs.add(3473);
		hc_clogs.add(3474);
		hc_clogs.add(3475);
		hc_clogs.add(3476);
		hc_clogs.add(3477);
		hc_clogs.add(3478);
		hc_clogs.add(3479);
		hc_clogs.add(3480);
		hc_clogs.add(3481);
		hc_clogs.add(3483);
		hc_clogs.add(3485);
		hc_clogs.add(3486);
		hc_clogs.add(3488);
		hc_clogs.add(3827);
		hc_clogs.add(3828);
		hc_clogs.add(3829);
		hc_clogs.add(3830);
		hc_clogs.add(3831);
		hc_clogs.add(3832);
		hc_clogs.add(3833);
		hc_clogs.add(3834);
		hc_clogs.add(3835);
		hc_clogs.add(3836);
		hc_clogs.add(3837);
		hc_clogs.add(3838);
		hc_clogs.add(4068);
		hc_clogs.add(4069);
		hc_clogs.add(4070);
		hc_clogs.add(4071);
		hc_clogs.add(4072);
		hc_clogs.add(4099);
		hc_clogs.add(4101);
		hc_clogs.add(4103);
		hc_clogs.add(4105);
		hc_clogs.add(4107);
		hc_clogs.add(4109);
		hc_clogs.add(4111);
		hc_clogs.add(4113);
		hc_clogs.add(4115);
		hc_clogs.add(4117);
		hc_clogs.add(4119);
		hc_clogs.add(4121);
		hc_clogs.add(4123);
		hc_clogs.add(4125);
		hc_clogs.add(4127);
		hc_clogs.add(4129);
		hc_clogs.add(4131);
		hc_clogs.add(4151);
		hc_clogs.add(4153);
		hc_clogs.add(4207);
		hc_clogs.add(4503);
		hc_clogs.add(4504);
		hc_clogs.add(4505);
		hc_clogs.add(4506);
		hc_clogs.add(4507);
		hc_clogs.add(4508);
		hc_clogs.add(4509);
		hc_clogs.add(4510);
		hc_clogs.add(4511);
		hc_clogs.add(4512);
		hc_clogs.add(4513);
		hc_clogs.add(4514);
		hc_clogs.add(4515);
		hc_clogs.add(4516);
		hc_clogs.add(4708);
		hc_clogs.add(4710);
		hc_clogs.add(4712);
		hc_clogs.add(4714);
		hc_clogs.add(4716);
		hc_clogs.add(4718);
		hc_clogs.add(4720);
		hc_clogs.add(4722);
		hc_clogs.add(4724);
		hc_clogs.add(4726);
		hc_clogs.add(4728);
		hc_clogs.add(4730);
		hc_clogs.add(4732);
		hc_clogs.add(4734);
		hc_clogs.add(4736);
		hc_clogs.add(4738);
		hc_clogs.add(4740);
		hc_clogs.add(4745);
		hc_clogs.add(4747);
		hc_clogs.add(4749);
		hc_clogs.add(4751);
		hc_clogs.add(4753);
		hc_clogs.add(4755);
		hc_clogs.add(4757);
		hc_clogs.add(4759);
		hc_clogs.add(5553);
		hc_clogs.add(5554);
		hc_clogs.add(5555);
		hc_clogs.add(5556);
		hc_clogs.add(5557);
		hc_clogs.add(6180);
		hc_clogs.add(6181);
		hc_clogs.add(6182);
		hc_clogs.add(6183);
		hc_clogs.add(6522);
		hc_clogs.add(6523);
		hc_clogs.add(6524);
		hc_clogs.add(6525);
		hc_clogs.add(6526);
		hc_clogs.add(6528);
		hc_clogs.add(6562);
		hc_clogs.add(6568);
		hc_clogs.add(6570);
		hc_clogs.add(6571);
		hc_clogs.add(6573);
		hc_clogs.add(6654);
		hc_clogs.add(6655);
		hc_clogs.add(6656);
		hc_clogs.add(6665);
		hc_clogs.add(6666);
		hc_clogs.add(6724);
		hc_clogs.add(6731);
		hc_clogs.add(6733);
		hc_clogs.add(6735);
		hc_clogs.add(6737);
		hc_clogs.add(6739);
		hc_clogs.add(6798);
		hc_clogs.add(6799);
		hc_clogs.add(6800);
		hc_clogs.add(6801);
		hc_clogs.add(6802);
		hc_clogs.add(6803);
		hc_clogs.add(6804);
		hc_clogs.add(6805);
		hc_clogs.add(6806);
		hc_clogs.add(6807);
		hc_clogs.add(6809);
		hc_clogs.add(6889);
		hc_clogs.add(6908);
		hc_clogs.add(6910);
		hc_clogs.add(6912);
		hc_clogs.add(6914);
		hc_clogs.add(6916);
		hc_clogs.add(6918);
		hc_clogs.add(6920);
		hc_clogs.add(6922);
		hc_clogs.add(6924);
		hc_clogs.add(6926);
		hc_clogs.add(7158);
		hc_clogs.add(7319);
		hc_clogs.add(7321);
		hc_clogs.add(7323);
		hc_clogs.add(7325);
		hc_clogs.add(7327);
		hc_clogs.add(7329);
		hc_clogs.add(7330);
		hc_clogs.add(7331);
		hc_clogs.add(7332);
		hc_clogs.add(7334);
		hc_clogs.add(7336);
		hc_clogs.add(7338);
		hc_clogs.add(7340);
		hc_clogs.add(7342);
		hc_clogs.add(7344);
		hc_clogs.add(7346);
		hc_clogs.add(7348);
		hc_clogs.add(7350);
		hc_clogs.add(7352);
		hc_clogs.add(7354);
		hc_clogs.add(7356);
		hc_clogs.add(7358);
		hc_clogs.add(7360);
		hc_clogs.add(7362);
		hc_clogs.add(7364);
		hc_clogs.add(7366);
		hc_clogs.add(7368);
		hc_clogs.add(7370);
		hc_clogs.add(7372);
		hc_clogs.add(7374);
		hc_clogs.add(7376);
		hc_clogs.add(7378);
		hc_clogs.add(7380);
		hc_clogs.add(7382);
		hc_clogs.add(7384);
		hc_clogs.add(7386);
		hc_clogs.add(7388);
		hc_clogs.add(7390);
		hc_clogs.add(7392);
		hc_clogs.add(7394);
		hc_clogs.add(7396);
		hc_clogs.add(7398);
		hc_clogs.add(7399);
		hc_clogs.add(7400);
		hc_clogs.add(7416);
		hc_clogs.add(7418);
		hc_clogs.add(7536);
		hc_clogs.add(7538);
		hc_clogs.add(7592);
		hc_clogs.add(7593);
		hc_clogs.add(7594);
		hc_clogs.add(7595);
		hc_clogs.add(7596);
		hc_clogs.add(7975);
		hc_clogs.add(7976);
		hc_clogs.add(7977);
		hc_clogs.add(7978);
		hc_clogs.add(7979);
		hc_clogs.add(7980);
		hc_clogs.add(7981);
		hc_clogs.add(7989);
		hc_clogs.add(7991);
		hc_clogs.add(7993);
		hc_clogs.add(8839);
		hc_clogs.add(8840);
		hc_clogs.add(8841);
		hc_clogs.add(8842);
		hc_clogs.add(8844);
		hc_clogs.add(8845);
		hc_clogs.add(8846);
		hc_clogs.add(8847);
		hc_clogs.add(8848);
		hc_clogs.add(8849);
		hc_clogs.add(8850);
		hc_clogs.add(8901);
		hc_clogs.add(8940);
		hc_clogs.add(8941);
		hc_clogs.add(8952);
		hc_clogs.add(8953);
		hc_clogs.add(8954);
		hc_clogs.add(8955);
		hc_clogs.add(8956);
		hc_clogs.add(8957);
		hc_clogs.add(8958);
		hc_clogs.add(8959);
		hc_clogs.add(8960);
		hc_clogs.add(8961);
		hc_clogs.add(8962);
		hc_clogs.add(8963);
		hc_clogs.add(8964);
		hc_clogs.add(8965);
		hc_clogs.add(8966);
		hc_clogs.add(8967);
		hc_clogs.add(8968);
		hc_clogs.add(8969);
		hc_clogs.add(8970);
		hc_clogs.add(8971);
		hc_clogs.add(8988);
		hc_clogs.add(8991);
		hc_clogs.add(8992);
		hc_clogs.add(8993);
		hc_clogs.add(8994);
		hc_clogs.add(8995);
		hc_clogs.add(8996);
		hc_clogs.add(8997);
		hc_clogs.add(9007);
		hc_clogs.add(9008);
		hc_clogs.add(9010);
		hc_clogs.add(9011);
		hc_clogs.add(9469);
		hc_clogs.add(9470);
		hc_clogs.add(9472);
		hc_clogs.add(9475);

		clientThread.invoke(() -> {
			// Add missing keys in order to the map. Order is extremely important here, so
			// we get a stable map given the same cache data.
			List<Integer> itemIdsMissingFromManifest = collectionLogItemIdsFromCache
					.stream()
					.filter((t) -> !hc_clogs.contains(t))
					.sorted()
					.collect(Collectors.toList());

			int currentIndex = 0;
			collectionLogItemIdToBitsetIndex.clear();
			for (Integer itemId : hc_clogs)
				collectionLogItemIdToBitsetIndex.put(itemId, currentIndex++);
			for (Integer missingItemId : itemIdsMissingFromManifest) {
				collectionLogItemIdToBitsetIndex.put(missingItemId, currentIndex++);
			}
		});
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
		noticeBoardManager.unsetNoticeBoard();
		clogItemsBitSet.clear();
		clogItemsCount = null;
		syncButtonManager.shutDown();
	}

	@Schedule(period = SECONDS_BETWEEN_UPLOADS, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void submitToAPI() {
		if (client != null
				&& (client.getGameState() != GameState.HOPPING && client.getGameState() != GameState.LOGIN_SCREEN)) {
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

	@Schedule(period = SECONDS_BETWEEN_MANIFEST_CHECKS, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void resyncManifest() {
		log.debug("Attempting to resync manifest");
		if (dataManager.getVersion() != lastManifestVersion) {
			dataManager.getManifest();
		}
	}

	@Schedule(period = SECONDS_BETWEEN_PROFILE_UPDATES, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void checkProfileChanged() {
		if (client.getLocalPlayer() != null && client.getGameState() == GameState.LOGGED_IN) {
			panel.updateLoggedIn(true);
			clientThread.invokeLater(this::checkProfileChange);
		}
	}

	@Getter
	public enum MinigameCompletionMessages {
		WINTERTODT("Your subdued Wintertodt count is:"), // g
		TEMPOROSS("Your Tempoross kill count is:"),
		GOTR("Amount of rifts you have closed:"),
		SOUL_WARS("team has defeated the Avatar"),
		BARBARIAN_ASSAULT("Wave 10 duration"),
		VOLCANIC_MINE("Your fragments disintegrate"); // g

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
	public void onChatMessage(ChatMessage chatMessage) {
		Player player = client.getLocalPlayer();

		if (player == null) {
			return;
		}

		// Point History generation for new collection log
		Matcher m = COLLECTION_LOG_ITEM_REGEX.matcher(chatMessage.getMessage());
		RuneScapeProfileType profType = RuneScapeProfileType.getCurrent(client);
		if (profType == RuneScapeProfileType.STANDARD && chatMessage.getType() == ChatMessageType.GAMEMESSAGE
				&& m.matches()) {
			String obtainedItemName = Text.removeTags(m.group(1));
			dataManager.uploadCollectionLogUnlock(obtainedItemName, player.getName());
		}

		if (profType == RuneScapeProfileType.STANDARD && (chatMessage.getType() == ChatMessageType.GAMEMESSAGE
				|| chatMessage.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION
				|| chatMessage.getType() == ChatMessageType.SPAM)) {
			String message = chatMessage.getMessage();
			handleActivityCompletion(message);
		}
	}

	public void handleActivityCompletion(String chatMessage) {
		for (RaidCompletionMessages r : RaidCompletionMessages.values()) {
			if (chatMessage.contains(r.getCompletionMessage())) {
				dataManager.uploadRaidCompletion(r.name(), chatMessage);
			}
		}

		for (MinigameCompletionMessages mg : MinigameCompletionMessages.values()) {
			if (chatMessage.contains(mg.getCompletionMessage())) {
				dataManager.uploadMinigameCompletion(mg.name(), chatMessage);
			}
		}
	}

	/**
	 * Finds the index this itemId is assigned to in the collections mapping.
	 * 
	 * @param itemId: The itemId to look up
	 * @return The index of the bit that represents the given itemId, if it is in
	 *         the map. -1 otherwise.
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

	@Schedule(period = SECONDS_BETWEEN_UPLOADS, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void queueSubmitTask() {
		scheduledExecutorService.execute(this::submitTask);
	}

	private int getVarbitValue(int varbitId) {
		VarbitComposition v = varbitCompositions.get(varbitId);
		if (v == null) {
			return -1;
		}

		int value = client.getVarpValue(v.getIndex());
		int lsb = v.getLeastSignificantBit();
		int msb = v.getMostSignificantBit();
		int mask = (1 << ((msb - lsb) + 1)) - 1;
		return (value >> lsb) & mask;
	}

	synchronized public void submitTask() {
		// TODO: do we want other GameStates?
		if (client.getGameState() != GameState.LOGGED_IN || varbitCompositions.isEmpty()) {
			return;
		}

		if (lastManifestVersion == -1 || client.getLocalPlayer() == null) {
			log.debug("Skipped due to bad manifest: {}", manifest);
			return;
		}

		String username = client.getLocalPlayer().getName();
		RuneScapeProfileType profileType = RuneScapeProfileType.getCurrent(client);
		PlayerProfile profileKey = new PlayerProfile(username, profileType);

		PlayerData newPlayerData = getPlayerData();
		PlayerData oldPlayerData = playerDataMap.computeIfAbsent(profileKey, k -> new PlayerData());

		// Subtraction is done in place so newPlayerData becomes a map of only changed
		// fields
		subtract(newPlayerData, oldPlayerData);
		if (newPlayerData.isEmpty()) {
			return;
		}
		submitPlayerData(profileKey, newPlayerData, oldPlayerData);
	}

	private PlayerData getPlayerData() {
		PlayerData out = new PlayerData();
		for (int varbitId : varbitsToCheck) {
			out.varb.put(varbitId, getVarbitValue(varbitId));
		}
		for (int varpId : varpsToCheck) {
			out.varp.put(varpId, client.getVarpValue(varpId));
		}
		for (Skill s : Skill.values()) {
			out.level.put(s.getName(), client.getRealSkillLevel(s));
		}
		out.collectionLogSlots = Base64.getEncoder().encodeToString(clogItemsBitSet.toByteArray());
		log.debug(Base64.getEncoder().encodeToString(clogItemsBitSet.toByteArray()));
		out.collectionLogItemCount = clogItemsCount;
		return out;
	}

	private void submitPlayerData(PlayerProfile profileKey, PlayerData delta, PlayerData old) {
		// If cyclesSinceSuccessfulCall is not a perfect square, we should not try to
		// submit.
		// This gives us quadratic backoff.
		cyclesSinceSuccessfulCall += 1;
		if (Math.pow((int) Math.sqrt(cyclesSinceSuccessfulCall), 2) != cyclesSinceSuccessfulCall) {
			return;
		}

		PlayerDataSubmission submission = new PlayerDataSubmission(
				profileKey.getUsername(),
				profileKey.getProfileType().name(),
				delta);

		Request request = new Request.Builder()
				.url(SUBMIT_URL)
				.post(RequestBody.create(JSON, gson.toJson(submission)))
				.build();

		Call call = okHttpClient.newCall(request);
		call.timeout().timeout(3, TimeUnit.SECONDS);
		call.enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				log.debug("Failed to submit: ", e);
			}

			@Override
			public void onResponse(Call call, Response response) {
				try {
					if (!response.isSuccessful()) {
						log.debug("Failed to submit: {}", response.code());
						return;
					}
					merge(old, delta);
					cyclesSinceSuccessfulCall = 0;
				} finally {
					response.close();
				}
			}
		});
	}

	private void merge(PlayerData oldPlayerData, PlayerData delta) {
		oldPlayerData.varb.putAll(delta.varb);
		oldPlayerData.varp.putAll(delta.varp);
		oldPlayerData.level.putAll(delta.level);
		oldPlayerData.collectionLogSlots = delta.collectionLogSlots;
		oldPlayerData.collectionLogItemCount = delta.collectionLogItemCount;
	}

	private void subtract(PlayerData newPlayerData, PlayerData oldPlayerData)

	{

		oldPlayerData.varb.forEach(newPlayerData.varb::remove);

		oldPlayerData.varp.forEach(newPlayerData.varp::remove);

		oldPlayerData.level.forEach(newPlayerData.level::remove);

		if (newPlayerData.collectionLogSlots.equals(oldPlayerData.collectionLogSlots))

			newPlayerData.clearCollectionLog();

	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {
		// Submit the collection log data two ticks after the first script prefires
		if (tickCollectionLogScriptFired != -1 &&
				tickCollectionLogScriptFired + 2 > client.getTickCount()) {
			tickCollectionLogScriptFired = -1;
			if (lastManifestVersion == -1) {
				client.addChatMessage(ChatMessageType.CONSOLE, "Embargo",
						"Failed to sync collection log. Try restarting the Embargo plugin.", "Embargo");
				return;
			}
			scheduledExecutorService.execute(this::submitTask);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {

		switch (event.getGameState()) {
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

		if (client != null && client.getLocalPlayer() != null && client.getLocalPlayer().getName() == null
				&& event.getGameState() == GameState.LOGGED_IN) {
			clientThread.invokeLater(() -> {
				if (client.getLocalPlayer().getName() == null)
					return false;

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

	public void checkProfileChange() {
		if (client == null)
			return;

		RuneScapeProfileType r = RuneScapeProfileType.getCurrent(client);
		if (r == RuneScapeProfileType.STANDARD && r != lastProfile && client != null && varbitsToCheck != null
				&& varpsToCheck != null) {
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
	private void setupVarpTracking() {
		// Init stuff to keep track of varb changes
		varpToVarbitMapping.clear();

		if (oldVarps == null) {
			oldVarps = new int[client.getVarps().length];
		}

		// Set oldVarps to be the current varps
		System.arraycopy(client.getVarps(), 0, oldVarps, 0, oldVarps.length);

		// For all varbits, add their ids to the multimap with the varp index as their
		// key
		clientThread.invoke(() -> {
			if (client.getIndexConfig() == null) {
				return false;
			}
			IndexDataBase indexVarbits = client.getIndexConfig();
			final int[] varbitIds = indexVarbits.getFileIds(VARBITS_ARCHIVE_ID);
			for (int id : varbitIds) {
				VarbitComposition varbit = client.getVarbit(id);
				if (varbit != null) {
					varpToVarbitMapping.put(varbit.getIndex(), id);
				}
			}
			return true;
		});
	}

	public void loadInitialData() {
		for (int varbIndex : varbitsToCheck) {
			dataManager.storeVarbitChanged(varbIndex, client.getVarbitValue(varbIndex));
		}

		for (int varpIndex : varpsToCheck) {
			dataManager.storeVarpChanged(varpIndex, client.getVarpValue(varpIndex));
		}
		for (Skill s : Skill.values()) {
			dataManager.storeSkillChanged(s.getName(), client.getRealSkillLevel(s));
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged) {
		if (client == null || varbitsToCheck == null || varpsToCheck == null)
			return;
		if (oldVarps == null)
			setupVarpTracking();

		int varpIndexChanged = varbitChanged.getIndex();
		if (varpsToCheck.contains(varpIndexChanged)) {
			dataManager.storeVarpChanged(varpIndexChanged, client.getVarpValue(varpIndexChanged));
		}
		for (Integer i : varpToVarbitMapping.get(varpIndexChanged)) {
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
	public void onStatChanged(StatChanged statChanged) {
		if (statChanged.getSkill() == null)
			return;
		Integer cachedLevel = skillLevelCache.get(statChanged.getSkill().getName());
		if (cachedLevel == null || cachedLevel != statChanged.getLevel()) {
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
	public void onScriptPreFired(ScriptPreFired preFired) {
		if (syncButtonManager.isSyncAllowed() && preFired.getScriptId() == 4100) {
			tickCollectionLogScriptFired = client.getTickCount();
			if (collectionLogItemIdToBitsetIndex.isEmpty()) {
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

	@Subscribe
	public void onLootReceived(final LootReceived event) {
		if (event.getType() != LootRecordType.NPC && event.getType() != LootRecordType.EVENT) {
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
	public void onWidgetLoaded(WidgetLoaded widgetLoaded) {
		clientThread.invokeLater(() -> {
			// TOB
			if (widgetLoaded.getGroupId() == 364 || widgetLoaded.getGroupId() == 50) {
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
		if (!event.getGroup().equals(CONFIG_GROUP)) {
			return;
		}

		noticeBoardManager.unsetNoticeBoard();
		if (config.highlightClan()) {
			noticeBoardManager.setTOBNoticeBoard();
			noticeBoardManager.setTOANoticeBoard();
		}
	}
}
