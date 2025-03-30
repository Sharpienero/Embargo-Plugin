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

import java.util.*;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
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

	private final HashMap<String, Integer> skillLevelCache = new HashMap<>();
	private final int SECONDS_BETWEEN_UPLOADS = 30;

	private static final Pattern COLLECTION_LOG_ITEM_REGEX = Pattern.compile("New item added to your collection log: (.*)");

	private final int SECONDS_BETWEEN_PROFILE_UPDATES = 15;
	private final String CONFIG_GROUP = "embargo";

	@Provides
	EmbargoConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(EmbargoConfig.class);
	}

	@Override
	protected void startUp() {
		log.info("Embargo Clan plugin started!");

		//Build out the side panel
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
		dataManager.resetVarbsAndVarpsToCheck();
		skillLevelCache.clear();
		dataManager.getManifest();
		panel.updateLoggedIn(false);

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
		panel = null;
		navButton = null;


		noticeBoardManager.shutDown();
		clogManager.shutDown();
		untrackableItemManager.shutDown();
		syncButtonManager.shutDown();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {
			clientThread.invokeLater(() -> {
				if (client == null) {
					return false;
				}

				if (client.getLocalPlayer() != null) {
					String username = client.getLocalPlayer().getName();
					embargoPanel.updateLoggedIn(true);
					if (dataManager.checkRegistered(username)) {
						panel.updateLoggedIn(true);
						return true;
					}
				} else {
					return false;
				}

				return false;
			});
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN) {
			log.debug("User logged out");
			if (embargoPanel == null) {
				log.debug("embargoPanel is null!!!");
			}
			embargoPanel.logOut();
		}
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


	public void checkProfileChange()
	{
		if (client == null) return;

		RuneScapeProfileType r = RuneScapeProfileType.getCurrent(client);
		if (r == RuneScapeProfileType.STANDARD && r != lastProfile && client != null && dataManager.getVarbitsToCheck() != null && dataManager.getVarpsToCheck() != null && this.client.getGameState() == GameState.LOGGED_IN)
		{
			// profile change, we should clear the dataManager and do a new initial dump
			log.debug("Profile seemed to change... Reloading all data and updating profile");
			lastProfile = r;
			dataManager.clearData();
			dataManager.loadInitialData();
		}
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
	public void onLootReceived(final LootReceived event)
	{
		if (event.getType() != LootRecordType.NPC && event.getType() != LootRecordType.EVENT)
		{
			return;
		}

		if (dataManager.shouldTrackLoot(event.getName())) {
            log.debug("Player killed {}", event.getName());
			dataManager.uploadLoot(event);
		} else {
            log.debug("Player killed {} , nothing to log", event.getName());
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals(CONFIG_GROUP))
		{
			return;
		}

		noticeBoardManager.unsetNoticeBoards();
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
