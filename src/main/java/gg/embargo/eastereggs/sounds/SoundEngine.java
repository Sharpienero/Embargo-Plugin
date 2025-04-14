package gg.embargo.eastereggs.sounds;
import gg.embargo.EmbargoConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.*;
import java.io.*;
import java.util.concurrent.Executor;

@Singleton
@Slf4j
public class SoundEngine
{
    private static final long CLIP_MTIME_UNLOADED = -2;

    private long lastClipMTime = CLIP_MTIME_UNLOADED;
    private Clip clip = null;

    private boolean loadClip(Sound sound)
    {
        try (InputStream stream = new BufferedInputStream(getSoundStream(sound));
             AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(stream))
        {
            clip.open(audioInputStream);
            return true;
        }
        catch (UnsupportedAudioFileException | IOException | LineUnavailableException e)
        {
            log.warn("Failed to load Embargo sound " + sound, e);
        }
        return false;
    }

    public void playClip(Sound sound, Executor executor)
    {
        executor.execute(() -> playClip(sound));
    }

    private void playClip(Sound sound)
    {
        long currentMTime = System.currentTimeMillis();
        if (clip == null || currentMTime != lastClipMTime || !clip.isOpen())
        {
            if (clip != null && clip.isOpen())
            {
                clip.close();
            }

            try
            {
                clip = AudioSystem.getClip();
            }
            catch (LineUnavailableException e)
            {
                lastClipMTime = CLIP_MTIME_UNLOADED;
                log.warn("Failed to get clip for Embargo sound " + sound, e);
                return;
            }

            lastClipMTime = currentMTime;
            if (!loadClip(sound))
            {
                return;
            }
        }

        // User configurable volume
        FloatControl volume = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float gain = 20f * (float) Math.log10(20 / 100f);//Math.log10(config.announcementVolume() / 100f);
        gain = Math.min(gain, volume.getMaximum());
        gain = Math.max(gain, volume.getMinimum());
        volume.setValue(gain);

        // From RuneLite base client Notifier class:
        // Using loop instead of start + setFramePosition prevents the clip
        // from not being played sometimes, presumably a race condition in the
        // underlying line driver
        clip.loop(0);
    }

    public void close()
    {
        if (clip != null && clip.isOpen())
        {
            clip.close();
        }
    }

    public static InputStream getSoundStream(Sound sound) throws FileNotFoundException {
        if (sound == null) {
            throw new FileNotFoundException("Sound cannot be null");
        }
        
        // Define the base directory for sounds
        File soundDir = new File(RuneLite.RUNELITE_DIR.getPath() + File.separator);
        
        // Get the specific directory for this sound category
        File soundCategoryDir = new File(soundDir, sound.getDirectory());
        
        // Check for custom sounds first
        File customSoundDir = new File(soundCategoryDir, "custom");
        
        // Try to find sound files
        File[] soundFiles = null;
        
        // First check custom directory
        if (customSoundDir.exists() && customSoundDir.isDirectory()) {
            soundFiles = customSoundDir.listFiles(file -> 
                !file.isDirectory() && isSupportedAudioFile(file.getName()));
        }
        
        // If no custom sounds, check the main category directory
        if ((soundFiles == null || soundFiles.length == 0) && soundCategoryDir.exists() && soundCategoryDir.isDirectory()) {
            soundFiles = soundCategoryDir.listFiles(file -> 
                !file.isDirectory() && isSupportedAudioFile(file.getName()));
        }
        
        // If we found sound files, pick a random one
        if (soundFiles != null && soundFiles.length > 0) {
            // Select a random sound file
            File selectedFile = soundFiles[new java.util.Random().nextInt(soundFiles.length)];
            return new FileInputStream(selectedFile);
        }
        
        // If we get here, no sound files were found
        throw new FileNotFoundException("No sound files found for: " + sound.getDirectory());
    }
    
    private static boolean isSupportedAudioFile(String fileName) {
        String lowerCaseName = fileName.toLowerCase();
        return lowerCaseName.endsWith(".wav") || 
               lowerCaseName.endsWith(".mp3") || 
               lowerCaseName.endsWith(".ogg");
    }
}