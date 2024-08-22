package gg.embargo;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

import java.awt.Color;

@ConfigGroup("embargo")
public interface EmbargoConfig extends Config
{

    @ConfigSection(
            name = "TOB Notice Board",
            description = "Section that houses TOB Notice Board settings",
            position = 1
    )
    String tobNoticeBoardSettings = "tobNoticeBoardSettings";


    @ConfigItem(
            keyName = "highlightClan",
            name = "Highlight Embargo Members?",
            description = "Whether or not to highlight clan chat members' names on the notice board",
            position = 1,
            section = tobNoticeBoardSettings
    )
    default boolean highlightClan()
    {
        return true;
    }

    @ConfigItem(
            keyName = "clanColor",
            name = "Highlight Color",
            description = "The color with which to highlight names from your current clan chat",
            position = 2,
            section = tobNoticeBoardSettings
    )
    default Color clanColor()
    {
        return new Color(53, 201, 255);
    }
}

