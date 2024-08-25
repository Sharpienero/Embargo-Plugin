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
            name = "Raid Notice Boards",
            description = "Section that houses Notice Board options",
            position = 1
    )
    String noticeBoardSettings = "NoticeBoardSettings";


    @ConfigItem(
            keyName = "highlightClan",
            name = "Highlight Embargo Members",
            description = "Whether or not to highlight clan chat members' names on notice boards (ToA, Tob)",
            position = 1,
            section = noticeBoardSettings
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
            section = noticeBoardSettings
    )
    default Color clanColor()
    {
        return new Color(53, 201, 255);
    }
}

