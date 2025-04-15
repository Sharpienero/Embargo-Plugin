package gg.embargo.eastereggs.sounds;

import lombok.Getter;


@Getter
public enum Sound {
    MY_TOB_PURPLE("tob", "my_tob_purple.wav"),
    TOB_WHITE_LIGHT("tob", "my_tob_white.wav");

    private final String directoryName;
    private final String resourceName;


    Sound(String directoryName, String resourceName)
    {
        this.directoryName = directoryName;
        this.resourceName = resourceName;
    }
}