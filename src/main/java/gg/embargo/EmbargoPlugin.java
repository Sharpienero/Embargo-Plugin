package gg.embargo;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.loottracker.LootRecordType;
import net.runelite.client.ui.ClientToolbar;

import java.awt.image.BufferedImage;
import java.util.Collection;

@Slf4j
@PluginDescriptor(
	name = "Embargo Clan",
	description = "A plugin to help Embargo Clan properly track items and accomplishments.",
	tags = {"embargo", "clan", "embargo.gg", "ironman"}
)
public class EmbargoPlugin extends Plugin {
	@Inject
	private Client client;

	@Inject
	private EmbargoConfig config;

	@Getter
	private EmbargoPanel panel;

	@Inject
	private ClientToolbar clientToolbar;

	private BufferedImage icon;
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception {
		log.info("Starting to build side panel");
		buildSidePanel();
		log.info("Done building side panel");
		log.info("Embargo Clan started!");
	}

	@Override
	protected void shutDown() throws Exception {
		log.info("Embargo Clan stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Embargo Clan says " + config.greeting(), null);
		}
	}

	String bossName;

	@Subscribe
	public void onLootReceived(final LootReceived event) {
		if (event.getType() != LootRecordType.NPC && event.getType() != LootRecordType.EVENT) {
			return;
		}

		//itemStacks = event.getItems();
		bossName = event.getName();

		// Send to API for pointHistories
	}

	@Provides
	EmbargoConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(EmbargoConfig.class);
	}

	private void buildSidePanel() {
		log.info("Inside of buildSidePanel");
		panel = injector.getInstance(EmbargoPanel.class);
		panel.sidePanelInitializer();
		icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
		navButton = NavigationButton.builder().tooltip("Embargo Clan").icon(icon).priority(6).panel(panel).build();
		clientToolbar.addNavigation(navButton);
	}
}
