package gg.embargo.eastereggs;

import gg.embargo.EmbargoConfig;
import gg.embargo.eastereggs.sounds.TobChestLight;
import gg.embargo.manifest.ManifestManager;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;

public class SoundManager {

    private final EventBus eventBus;
    private final EmbargoConfig config;

    @Inject
    public SoundManager(EventBus eventBus, EmbargoConfig config) {
        this.eventBus = eventBus;
        this.config = config;
    }

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Inject
    private TobChestLight tobChestLight;

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (config.enableClanEasterEggs())
            tobChestLight.onVarbitChanged(event);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (config.enableClanEasterEggs())
            tobChestLight.onGameTick(event);
    }

    @Subscribe
    private void onGameObjectSpawned(GameObjectSpawned event)
    {
        if (config.enableClanEasterEggs())
            tobChestLight.onGameObjectSpawned(event);
    }

    @Subscribe
    private void onGameObjectDespawned(GameObjectDespawned event)
    {
        if (config.enableClanEasterEggs())
            tobChestLight.onGameObjectDespawned(event);
    }
}
