package gg.embargo;

import net.runelite.client.config.*;

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

    @ConfigSection(
            name = "Clan Easter Eggs",
            description = "Enables fun item name replacements like 'Dragon warhammer' to 'Bonker'",
            position = 3
    )
    String easterEggSettings = "EasterEggSettings";

    @ConfigItem(
        keyName = "enableClanEasterEggs",
        name = "Enable Easter Eggs",
        description = "A top level control to enable/disable the feature",
        position = 3,
        section = easterEggSettings
    )
    default boolean enableClanEasterEggs() {
        return true;
    }


    @ConfigItem(
            keyName = "enableItemRenames",
            name = "Enable Item Renames",
            description = "Enables item name replacements like 'Dragon warhammer' to 'Bonker'",
            position = 4,
            section = easterEggSettings
    )
    default boolean enableItemRenames() {
        return true;
    }

    @ConfigItem(
            keyName = "enableNpcRenames",
            name = "Enable NPC Renames",
            description = "Enables NPC name changes, like 'Pestilent Bloat' to 'Dr D1sconnect'",
            position = 5,
            section = easterEggSettings
    )
    default boolean enableNpcRenames() {
        return true;
    }

    @ConfigItem(
            keyName = "enableCustomSounds",
            name = "Enable Custom Sounds",
            description = "Enables custom sounds; e.g white light @ tob/purple @ tob",
            position = 6,
            section = easterEggSettings
    )
    default boolean enableCustomSounds() {
        return true;
    }

    @Range(
            min=0,
            max=200
    )
    @ConfigItem(
            keyName = "easterEggVolume",
            name = "Sound volume",
            description = "Controls the volume",
            position = 7,
            section = easterEggSettings
    )
    default int announcementVolume()
    {
        return 60;
    }

}

