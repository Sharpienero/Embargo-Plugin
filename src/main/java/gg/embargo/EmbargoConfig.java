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

    @ConfigSection(
            name = "Collection Log Sync Button",
            description = "Add a button to the collection log interface to sync your collection log with Embargo",
            position = 2
    )
    String collectionLogSettings = "CollectionLogSettings";

    @ConfigItem(
            keyName = "showCollectionLogSyncButton",
            name = "Show Collection Log Sync Button",
            description = "Whether or not to render the Embargo collection log sync button",
            position = 1,
            section = collectionLogSettings
    )
    default boolean showCollectionLogSyncButton() { return true; }

    @ConfigItem(
        keyName = "enableClanEasterEggs",
        name = "Enable Clan Easter Eggs",
        description = "Enables fun item name replacements like 'Dragon warhammer' to 'Bonker'",
        position = 3
    )
    default boolean enableClanEasterEggs() {
        return true;
    }
}

