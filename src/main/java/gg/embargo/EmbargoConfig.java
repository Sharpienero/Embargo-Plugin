package gg.embargo;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;


@ConfigGroup("embargo")
public interface EmbargoConfig extends Config {
    public JPanel discordButton = new JPanel();

    @ConfigItem(
			keyName = "greeting",
			name = "Welcome Greeting",
			description = "The message to show to the user when they log in"
	)
	default String greeting() {
		return "Embargo Greeting";
	}

	@ConfigSection(
			name = "Discord",
			description = "Join our discord",
			position = -20,
			closedByDefault = false
	)
	String webhookSection = "Webhook Overrides";

	JPanel sidePanel = new JPanel();
}