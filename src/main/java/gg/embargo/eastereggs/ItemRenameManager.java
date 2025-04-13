package gg.embargo.eastereggs;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import gg.embargo.EmbargoConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;
import net.runelite.client.eventbus.EventBus;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Singleton
public class ItemRenameManager {

    private final EventBus eventBus;
    private final EmbargoConfig config;

    private static final Set<MenuAction> ITEM_MENU_ACTIONS = ImmutableSet.of(
            MenuAction.GROUND_ITEM_FIRST_OPTION, MenuAction.GROUND_ITEM_SECOND_OPTION,
            MenuAction.GROUND_ITEM_THIRD_OPTION, MenuAction.GROUND_ITEM_FOURTH_OPTION,
            MenuAction.GROUND_ITEM_FIFTH_OPTION, MenuAction.EXAMINE_ITEM_GROUND,
            // Inventory + Using Item on Players/NPCs/Objects
            MenuAction.CC_OP, MenuAction.CC_OP_LOW_PRIORITY, MenuAction.WIDGET_TARGET,
            MenuAction.WIDGET_TARGET_ON_PLAYER, MenuAction.WIDGET_TARGET_ON_NPC,
            MenuAction.WIDGET_TARGET_ON_GAME_OBJECT, MenuAction.WIDGET_TARGET_ON_GROUND_ITEM,
            MenuAction.WIDGET_TARGET_ON_WIDGET);

    // Default item name remappings
    private static final ImmutableMap<String, String> DEFAULT_ITEM_REMAP = ImmutableMap.<String, String>builder()
            .put("Dragon warhammer", "Bonker")
            .put("Zaryte crossbow", "Kitty ear crossbow (MEOW)")
            .put("Ghommal's_lucky_penny", "H4vell's lucky quid")
            .put("Fire cape", "Cheese cape")
            .put("Fire max cape", "Special cape")
            .build();

    // Map for custom renamings
    private final Map<String, String> customItemRemap = new HashMap<>();

    @Inject
    public ItemRenameManager(EventBus eventBus, EmbargoConfig config) {
        this.eventBus = eventBus;
        this.config = config;
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        // Only process if easter eggs are enabled
        if (!config.enableClanEasterEggs()) {
            return;
        }
        
        MenuEntry entry = event.getMenuEntry();
        if (ITEM_MENU_ACTIONS.contains(entry.getType())) {
            remapMenuEntryText(entry, customItemRemap);
        }
    }

    public void startUp() {
        eventBus.register(this);
        setupMenuRenames();
    }

    public void shutDown() {
        customItemRemap.clear();
        eventBus.unregister(this);
    }

    public void setupMenuRenames() {
        customItemRemap.clear();
        customItemRemap.putAll(DEFAULT_ITEM_REMAP);
    }

    /**
     * Remaps a menu entry's text if the target matches an entry in the provided map.
     * 
     * @param menuEntry The menu entry to potentially modify
     * @param map The map of original names to replacement names
     */
    private void remapMenuEntryText(MenuEntry menuEntry, Map<String, String> map) {
        String target = menuEntry.getTarget();
        String cleanTarget;
        
        NPC npc = menuEntry.getNpc();
        cleanTarget = npc != null ? Text.removeTags(npc.getName()) : Text.removeTags(target);
        
        String replacement = map.get(cleanTarget);
        if (replacement != null) {
            menuEntry.setTarget(target.replace(cleanTarget, replacement));
        }
    }
}
