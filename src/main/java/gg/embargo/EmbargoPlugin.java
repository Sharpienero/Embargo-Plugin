package gg.embargo;

import com.google.inject.Provides;
import gg.embargo.collections.*;
import gg.embargo.commands.CommandManager;
import gg.embargo.eastereggs.NPCRenameManager;
import gg.embargo.eastereggs.SoundManager;
import gg.embargo.manifest.ManifestManager;
import gg.embargo.ui.EmbargoPanel;
import gg.embargo.eastereggs.ItemRenameManager;
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
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(name = "Embargo Clan", description = "A plugin to sync your account with Embargo", tags = { "embargo",
		"clan", "embargo.gg", "ironman" })
public class EmbargoPlugin extends Plugin {

	private static final String CONFIG_GROUP = "embargo";
	private static final int SECONDS_BETWEEN_UPLOADS = 30;
	private static final int SECONDS_BETWEEN_PROFILE_UPDATES = 15;
	private static final Pattern COLLECTION_LOG_ITEM_REGEX = Pattern
			.compile("New item added to your collection log: (.*)");

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

	@Inject
	private ItemRenameManager itemRenameManager;

	@Inject
	private NPCRenameManager npcRenameManager;

	@Inject
	private SoundManager soundManager;

	@Inject
	public ManifestManager manifestManager;

	@Inject
	public CommandManager commandManager;

	private RuneScapeProfileType lastProfile;

	private NavigationButton navButton;

	private final Map<String, Integer> skillLevelCache = new HashMap<>();

	AtomicBoolean isUsernameRegistered = new AtomicBoolean(false);

	@Provides
	EmbargoConfig getConfig(ConfigManager configManager) {
		return configManager.getConfig(EmbargoConfig.class);
	}

	@Override
	protected void startUp() {
		log.info("Embargo Clan plugin started!");

		if (dataManager.stopTryingForAccount.get()) {
			return;
		}

		initializePanel();
		initializeManagers();

		lastProfile = null;
		dataManager.resetVarbsAndVarpsToCheck();
		skillLevelCache.clear();
		dataManager.getManifest();

		itemRenameManager.setupMenuRenames();

		if (client != null) {
			if (client.getGameState() == GameState.LOGGED_IN) {
				dataManager.isUserRegisteredAsync(client.getLocalPlayer().getName(), isRegistered -> {
					if (isRegistered) {
						embargoPanel.updateLoggedIn(false);
					}
				});
			}
		}
	}

	private void initializePanel() {
		embargoPanel = injector.getInstance(EmbargoPanel.class);
		embargoPanel.init();
		embargoPanel.updateLoggedIn(false);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
		navButton = NavigationButton.builder()
				.tooltip("Embargo Clan")
				.icon(icon)
				.priority(0)
				.panel(embargoPanel)
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
		commandManager.startUp();

		if (config != null && config.highlightClan()) {
			noticeBoardManager.setNoticeBoards();
		}

		if (config != null && config.enableClanEasterEggs()) {
			itemRenameManager.startUp();
			npcRenameManager.startUp();
			soundManager.startUp();
		}
	}

	@Override
	protected void shutDown() {
		log.info("Embargo Clan plugin stopped!");

		dataManager.clearData();
		embargoPanel.reset();
		clientToolbar.removeNavigation(navButton);

		shutDownManagers();

		embargoPanel = null;
		navButton = null;
	}

	private void shutDownManagers() {
		noticeBoardManager.shutDown();
		clogManager.shutDown();
		untrackableItemManager.shutDown();
		syncButtonManager.shutDown();
		itemRenameManager.shutDown();
		npcRenameManager.shutDown();
		soundManager.shutDown();
		commandManager.shutDown();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		GameState gameState = event.getGameState();
		if (gameState == GameState.LOADING)
			return;

		if (gameState == GameState.LOGGED_IN && !embargoPanel.isLoggedIn) {
			log.debug("inside of condition, handling loggedIn");
			handleLoggedIn();
		} else if (gameState == GameState.LOGIN_SCREEN) {
			handleLoggedOut();
		}
	}

	private void handleLoggedIn() {
		clientThread.invokeLater(() -> {
			if (client == null || dataManager.stopTryingForAccount.get()) {
				return false;
			}

			if (isUsernameRegistered.get()) {
				embargoPanel.updateLoggedIn(true);
				return true;
			}

			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null) {
				String username = localPlayer.getName();

				dataManager.isUserRegisteredAsync(username, isRegistered -> {
					if (isRegistered) {
						embargoPanel.updateLoggedIn(true);
						isUsernameRegistered.set(true);
					}
				});
			}
			return isUsernameRegistered.get();
		});
	}

	private void handleLoggedOut() {
		log.debug("User logged out");

		// Clear both panel references
		if (embargoPanel != null) {
			SwingUtilities.invokeLater(() -> embargoPanel.logOut());
		} else {
			log.debug("embargoPanel is null!!!");
		}

		// Also clear the panel reference (which is different from embargoPanel)
		if (embargoPanel != null) {
			embargoPanel.reset();
			embargoPanel.updateLoggedIn(false);
		}

		// Clear data in DataManager to ensure complete reset
		dataManager.clearData();

		// Reset skill cache
		skillLevelCache.clear();
	}

	@Schedule(period = SECONDS_BETWEEN_UPLOADS, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void ensureLatestManifest() {
		if (manifestManager.getLatestManifest() != null) {
			if (!(manifestManager.getLastCheckedManifestVersion() == manifestManager.getLatestManifest()
					.getVersion())) {
				manifestManager.getLatestManifest();
			}
		}
	}

	@Schedule(period = SECONDS_BETWEEN_UPLOADS, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void submitToAPI() {
		if (client == null) {
			return;
		}

		GameState gameState = client.getGameState();
		if (gameState != GameState.HOPPING && gameState != GameState.LOGIN_SCREEN) {
			dataManager.submitToAPI();
			updatePlayerRegistrationStatus();
		} else {
			// log.debug("User is hopping or logged out, do not send data");
			embargoPanel.logOut();
		}
	}

	private void updatePlayerRegistrationStatus() {
		if (dataManager.stopTryingForAccount.get()) {
			return;
		}
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null) {
			String username = localPlayer.getName();
			dataManager.isUserRegisteredAsync(username, isRegistered -> {
				if (isRegistered) {
					log.debug("updateProfileAfterLoggedIn Member registered");
					embargoPanel.updateLoggedIn(true);
				}
			});
		}
	}

	@Schedule(period = SECONDS_BETWEEN_PROFILE_UPDATES, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void checkProfileChanged() {
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null && client.getGameState() == GameState.LOGGED_IN) {
			embargoPanel.updateLoggedIn(true);
			clientThread.invokeLater(this::checkProfileChange);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage) {
		if (client == null || manifestManager.getLatestManifest() == null)
			return;

		Player player = client.getLocalPlayer();
		if (player == null)
			return;

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

			if (processCompletionMessages(manifestManager.getLatestManifest().getRaidCompletionMessages(), message,
					(name, _message) -> dataManager.uploadRaidCompletion(name, _message))) {
				// return early as it saves time in case it gets processed here, otherwise it's
				// most likely a minigame completion message or unrelated
				return;
			}

			processCompletionMessages(manifestManager.getLatestManifest().minigameCompletionMessages, message,
					(name, _message) -> dataManager.uploadMinigameCompletion(name, _message));
		}
	}

	private boolean processCompletionMessages(Map<String, String> messageMap, String chatMessage,
			BiConsumer<String, String> uploadAction) {
		for (Map.Entry<String, String> entry : messageMap.entrySet()) {
			String name = entry.getKey();
			String completionMessage = entry.getValue();

			if (chatMessage.contains(completionMessage)) {
				log.debug("Sending API request for completed activity");
				uploadAction.accept(name, chatMessage);
				return true;
			}
		}
		return false;
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

		// Handle item rename config changes
		if (event.getKey().equals("enableClanEasterEggs")) {
			if (config.enableClanEasterEggs()) {
				itemRenameManager.startUp();
				npcRenameManager.startUp();
			} else {
				itemRenameManager.shutDown();
				npcRenameManager.shutDown();
			}
		}
	}

}
