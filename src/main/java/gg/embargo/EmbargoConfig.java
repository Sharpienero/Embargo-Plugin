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
            name="Embargo Profile",
            description = "Section that houses settings related to Embargo clan related settings.",
            position=1
    )
    String embargoProfileSettings = "embargoProfileSettings";

    @ConfigItem(
            keyName="embargoProfile",
            name="Add Embargo Profile Option?",
            description="Whether or not to add an option to open the members' Embargo profile",
            position=1,
            section = embargoProfileSettings
    )
    default boolean addEmbargoProfileOption()
    {
        return true;
    }

    @ConfigSection(
            name = "TOB Notice Board",
            description = "Section that houses TOB Notice Board settings",
            position = 2
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

