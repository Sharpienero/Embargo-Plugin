package gg.embargo;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("embargo")
public interface EmbargoConfig extends Config {
    @ConfigItem(
			keyName = "greeting",
			name = "Welcome Greeting",
			description = "The message to show to the user when they log in"
	)
	default String greeting() {
		return "Embargo Greeting";
	}
}
