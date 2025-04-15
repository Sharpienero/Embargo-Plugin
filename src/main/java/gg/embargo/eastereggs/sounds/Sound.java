package gg.embargo.eastereggs.sounds;

import lombok.Getter;


@Getter
public enum Sound {
    TOB_PURPLE_CHEST("/", "tbowscythe.wav");

    private final String directoryName;
    private final String resourceName;


    Sound(String directoryName, String resourceName)
    {
        this.directoryName = directoryName;
        this.resourceName = resourceName;
    }
}