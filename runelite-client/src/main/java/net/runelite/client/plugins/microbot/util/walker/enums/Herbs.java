package net.runelite.client.plugins.microbot.util.walker.enums;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@Getter
public enum Herbs {
    NONE("None"),
    ARDOUGNE("Ardougne", new WorldPoint(2668, 3375, 0)),
    CATHERBY("Catherby", new WorldPoint(2811, 3465, 0)),
    CIVITAS_ILLA_FORTIS("Civitas illa Fortis", new WorldPoint(1583, 3094, 0)),
    FALADOR("Falador", new WorldPoint(3056, 3310, 0)),
    FARMING_GUILD("Farming Guild", new WorldPoint(1238, 3729, 0)),
    KOUREND("Kourend", new WorldPoint(1736, 3553, 0)),
    MORYTANIA("Morytania", new WorldPoint(3600, 3523, 0)),
    TROLLIEHM("Troll Stronghold", new WorldPoint(2828, 3695, 0)),
    WEISS("Weiss", new WorldPoint(2847, 3934, 0));

    private final String name;
    private WorldPoint worldPoint;

    Herbs(String name, WorldPoint worldPoint) {
        this.name = name;
        this.worldPoint = worldPoint;
    }

    Herbs(String name) {
        this.name = name;
    }
}