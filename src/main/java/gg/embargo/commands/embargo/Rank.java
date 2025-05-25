package gg.embargo.commands.embargo;

import lombok.Getter;

import java.awt.*;

public enum Rank {
    BRONZE("Bronze", Color.orange),
    IRON("Iron", Color.darkGray),
    STEEL("Steel", Color.lightGray),
    MITHRIL("Mithril", Color.blue),
    ADAMANT("Adamant", Color.green),
    RUNE("Rune", Color.cyan),
    DRAGON("Dragon", Color.red),
    BEAST("Beast", Color.yellow);

    private final String name;
    @Getter
    private final Color color;

    Rank(String name, Color color) {
        this.name = name;
        this.color = color;
    }

    public static Color getColorByName(String name) {
        for (Rank rank : values()) {
            if (rank.name.equalsIgnoreCase(name)) {
                return rank.color;
            }
        }
        return Color.WHITE;
    }
}