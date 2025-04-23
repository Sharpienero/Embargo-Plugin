package gg.embargo.eastereggs;

import gg.embargo.EmbargoConfig;
import gg.embargo.eastereggs.sounds.TobChestLight;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import java.io.File;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import javax.inject.Inject;

public class SoundManager {

    private static final File DOWNLOAD_DIR = new File(RuneLite.RUNELITE_DIR.getPath() + File.separator + "embargo-sounds");
    private static final String DELETE_WARNING_FILENAME = "EXTRA_FILES_WILL_BE_DELETED_BUT_FOLDERS_WILL_REMAIN";
    private static final String SOUNDVERSION_FILENAME = "SOUNDVERSION";
    private static final File DELETE_WARNING_FILE = new File(DOWNLOAD_DIR, DELETE_WARNING_FILENAME);
    private static final HttpUrl RAW_GITHUB = HttpUrl.parse("https://raw.githubusercontent.com/sharpienero/embargo-plugin/sounds");

    private final EventBus eventBus;
    private final EmbargoConfig config;

    @Inject
    public OkHttpClient okHttpClient;

    @Inject
    public SoundManager(EventBus eventBus, EmbargoConfig config) {
        this.eventBus = eventBus;
        this.config = config;
    }

    public void startUp() {
        eventBus.register(this);
        SoundFileManager.ensureDownloadDirectoryExists();
        SoundFileManager.downloadAllMissingSounds(okHttpClient);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    public boolean featureEnabled() {
        return config.enableClanEasterEggs() && config.enableCustomSounds();
    }

    @Inject
    private TobChestLight tobChestLight;

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (featureEnabled())
            tobChestLight.onVarbitChanged(event);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (featureEnabled())
            tobChestLight.onGameTick(event);
    }

    @Subscribe
    private void onGameObjectSpawned(GameObjectSpawned event)
    {
        if (featureEnabled())
            tobChestLight.onGameObjectSpawned(event);
    }

    @Subscribe
    private void onGameObjectDespawned(GameObjectDespawned event)
    {
        if (featureEnabled())
            tobChestLight.onGameObjectDespawned(event);
    }
}
