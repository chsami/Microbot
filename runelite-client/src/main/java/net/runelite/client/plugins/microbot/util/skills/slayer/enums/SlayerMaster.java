package net.runelite.client.plugins.microbot.util.skills.slayer.enums;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
@Getter
public enum SlayerMaster {
    NONE("", null),
    TURAEL("Turael", new WorldPoint(2931, 3536, 0)),
    SPRIA("Spria", new WorldPoint(3091, 3267, 0)),
    KRYSTILIA("Krystilia", new WorldPoint(3109, 3516, 0)),
    MAZCHNA("Mazchna", new WorldPoint(3510, 3507, 0)),
    VANNAKA("Vannaka", new WorldPoint(3145, 9914, 0)),
    CHAELDAR("Chaeldar", new WorldPoint(2444, 4431, 0)),
    KONAR_QUO_MATEN("Konar", new WorldPoint(1308, 3786, 0)),
    NIEVE("Nieve", new WorldPoint(2432, 3423, 0)),
    STEVE("Steve", new WorldPoint(2432, 3423, 0)),
    DURADEL("Duradel", new WorldPoint(2869, 2982, 1));

    private final String name;
    private final WorldPoint worldPoint;

    SlayerMaster(String name, WorldPoint worldPoint) {
        this.name = name;
        this.worldPoint = worldPoint;
    }
}