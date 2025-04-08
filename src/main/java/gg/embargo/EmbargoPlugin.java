package gg.embargo;

import com.google.inject.Provides;
import gg.embargo.collections.*;
import gg.embargo.ui.EmbargoPanel;
import gg.embargo.ui.SyncButtonManager;
import gg.embargo.noticeboard.NoticeBoardManager;
import gg.embargo.untrackables.UntrackableItemManager;
import lombok.Getter;
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

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
	name = "Embargo Clan",
	description = "A plugin to sync your account with Embargo",
	tags = {"embargo", "clan", "embargo.gg", "ironman"}
)
public class EmbargoPlugin extends Plugin {

	private static final String CONFIG_GROUP = "embargo";
	private static final int SECONDS_BETWEEN_UPLOADS = 30;
	private static final int SECONDS_BETWEEN_PROFILE_UPDATES = 15;
	private static final Pattern COLLECTION_LOG_ITEM_REGEX = Pattern.compile("New item added to your collection log: (.*)");

	@Inject
	private DataManager dataManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	@Inject
	private EmbargoConfig config;

	@Inject
	private EmbargoPanel embargoPanel;

	@Inject
	private NoticeBoardManager noticeBoardManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private SyncButtonManager syncButtonManager;

	@Inject
	private CollectionLogManager clogManager;

	@Inject
	private UntrackableItemManager untrackableItemManager;

	private RuneScapeProfileType lastProfile;

	@Getter
	private EmbargoPanel panel;

	private NavigationButton navButton;

	private final Map<String, Integer> skillLevelCache = new HashMap<>();

	@Provides
	EmbargoConfig getConfig(ConfigManager configManager) {
		return configManager.getConfig(EmbargoConfig.class);
	}

	@Override
	protected void startUp() {
		log.info("Embargo Clan plugin started!");

		initializePanel();
		initializeManagers();
		
		lastProfile = null;
		dataManager.resetVarbsAndVarpsToCheck();
		skillLevelCache.clear();
		dataManager.getManifest();
	}

	private void initializePanel() {
		panel = injector.getInstance(EmbargoPanel.class);
		panel.init();
		panel.updateLoggedIn(false);
		
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
		navButton = NavigationButton.builder()
				.tooltip("Embargo Clan")
				.icon(icon)
				.priority(0)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
	}
	
	private void initializeManagers() {
		if (config != null && config.showCollectionLogSyncButton()) {
			syncButtonManager.startUp();
		}

		clogManager.startUp(syncButtonManager);
		untrackableItemManager.startUp();
		noticeBoardManager.startUp();

		if (config != null && config.highlightClan()) {
			noticeBoardManager.setNoticeBoards();
		}
	}

	@Override
	protected void shutDown() {
		log.info("Embargo Clan plugin stopped!");
		
		dataManager.clearData();
		panel.reset();
		clientToolbar.removeNavigation(navButton);
		
		shutDownManagers();
		
		panel = null;
		navButton = null;
	}
	
	private void shutDownManagers() {
		noticeBoardManager.shutDown();
		clogManager.shutDown();
		untrackableItemManager.shutDown();
		syncButtonManager.shutDown();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		GameState gameState = event.getGameState();
		
		if (gameState == GameState.LOGGED_IN) {
			handleLoggedIn();
		} else if (gameState == GameState.LOGIN_SCREEN) {
			handleLoggedOut();
		}
	}
	
	private void handleLoggedIn() {
		clientThread.invokeLater(() -> {
			if (client == null) {
				return false;
			}

			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null) {
				String username = localPlayer.getName();
				embargoPanel.updateLoggedIn(true);
				if (dataManager.checkRegistered(username)) {
					panel.updateLoggedIn(true);
					return true;
				}
			}
			return false;
		});
	}
	
	private void handleLoggedOut() {
		log.debug("User logged out");
		if (embargoPanel != null) {
			embargoPanel.logOut();
		} else {
			log.debug("embargoPanel is null!!!");
		}
	}

	@Schedule(
			period = SECONDS_BETWEEN_UPLOADS,
			unit = ChronoUnit.SECONDS,
			asynchronous = true
	)
	public void submitToAPI() {
		if (client == null) {
			return;
		}
		
		GameState gameState = client.getGameState();
		if (gameState != GameState.HOPPING && gameState != GameState.LOGIN_SCREEN) {
			dataManager.submitToAPI();
			updatePlayerRegistrationStatus();
		} else {
			log.debug("User is hopping or logged out, do not send data");
			panel.logOut();
		}
	}
	
	private void updatePlayerRegistrationStatus() {
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null) {
			String username = localPlayer.getName();
			if (dataManager.checkRegistered(username)) {
				log.debug("updateProfileAfterLoggedIn Member registered");
				panel.updateLoggedIn(true);
			}
		}
	}

	@Schedule(
		period = SECONDS_BETWEEN_PROFILE_UPDATES,
		unit = ChronoUnit.SECONDS,
		asynchronous = true
	)
	public void checkProfileChanged() {
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null && client.getGameState() == GameState.LOGGED_IN) {
			panel.updateLoggedIn(true);
			clientThread.invokeLater(this::checkProfileChange);
		}
	}

	@Getter
	public enum MinigameCompletionMessages {
		WINTERTODT("Your subdued Wintertodt count is:"),
		TEMPOROSS("Your Tempoross kill count is:"),
		GOTR("Amount of rifts you have closed:"),
		SOUL_WARS("team has defeated the Avatar"),
		BARBARIAN_ASSAULT("Wave 10 duration"),
		VOLCANIC_MINE("Your fragments disintegrate");

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

		String message = chatMessage.getMessage();
		ChatMessageType messageType = chatMessage.getType();
		RuneScapeProfileType profileType = RuneScapeProfileType.getCurrent(client);
		
		// Only process for standard profile
		if (profileType != RuneScapeProfileType.STANDARD) {
			return;
		}
		
		// Check for collection log items
		if (messageType == ChatMessageType.GAMEMESSAGE) {
			Matcher matcher = COLLECTION_LOG_ITEM_REGEX.matcher(message);
			if (matcher.matches()) {
				String obtainedItemName = Text.removeTags(matcher.group(1));
				dataManager.uploadCollectionLogUnlock(obtainedItemName, player.getName());
			}
		}
		
		// Check for activity completions
		if (messageType == ChatMessageType.GAMEMESSAGE || 
			messageType == ChatMessageType.FRIENDSCHATNOTIFICATION || 
			messageType == ChatMessageType.SPAM) {
			handleActivityCompletion(message);
		}
	}

	public void handleActivityCompletion(String chatMessage) {
		// Check for raid completions
		for (RaidCompletionMessages raid : RaidCompletionMessages.values()) {
			if (chatMessage.contains(raid.getCompletionMessage())) {
				dataManager.uploadRaidCompletion(raid.name(), chatMessage);
				return; // Return early once we've found a match
			}
		}

		// Check for minigame completions
		for (MinigameCompletionMessages minigame : MinigameCompletionMessages.values()) {
			if (chatMessage.contains(minigame.getCompletionMessage())) {
				dataManager.uploadMinigameCompletion(minigame.name(), chatMessage);
				return; // Return early once we've found a match
			}
		}
	}

	public void checkProfileChange() {
		if (client == null) {
			return;
		}

		RuneScapeProfileType currentProfile = RuneScapeProfileType.getCurrent(client);
		boolean isStandardProfile = currentProfile == RuneScapeProfileType.STANDARD;
		boolean profileChanged = isStandardProfile && currentProfile != lastProfile;
		boolean dataAvailable = dataManager.getVarbitsToCheck() != null && dataManager.getVarpsToCheck() != null;
		boolean isLoggedIn = client.getGameState() == GameState.LOGGED_IN;
		
		if (profileChanged && dataAvailable && isLoggedIn) {
			// Profile change, we should clear the dataManager and do a new initial dump
			log.debug("Profile changed to standard. Reloading all data and updating profile");
			lastProfile = currentProfile;
			dataManager.clearData();
			dataManager.loadInitialData();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged) {
		Skill skill = statChanged.getSkill();
		if (skill == null) {
			return;
		}
		
		String skillName = skill.getName();
		int newLevel = statChanged.getLevel();
		Integer cachedLevel = skillLevelCache.get(skillName);
		
		if (cachedLevel == null || cachedLevel != newLevel) {
			skillLevelCache.put(skillName, newLevel);
			dataManager.storeSkillChanged(skillName, newLevel);
		}
	}

	@Subscribe
	public void onLootReceived(final LootReceived event) {
		LootRecordType eventType = event.getType();
		if (eventType != LootRecordType.NPC && eventType != LootRecordType.EVENT) {
			return;
		}

		String npcName = event.getName();
		if (dataManager.shouldTrackLoot(npcName)) {
			log.debug("Player killed {}", npcName);
			dataManager.uploadLoot(event);
		} else {
			log.debug("Player killed {}, nothing to log", npcName);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals(CONFIG_GROUP)) {
			return;
		}

		// Update notice boards based on config
		noticeBoardManager.unsetNoticeBoards();
		if (config.highlightClan()) {
			noticeBoardManager.setTOBNoticeBoard();
			noticeBoardManager.setTOANoticeBoard();
		}

		// Update sync button based on config
		if (config.showCollectionLogSyncButton()) {
			syncButtonManager.startUp();
		} else {
			syncButtonManager.shutDown();
		}
	}
}
