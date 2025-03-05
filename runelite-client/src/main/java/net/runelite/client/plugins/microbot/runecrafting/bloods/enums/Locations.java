package net.runelite.client.plugins.microbot.runecrafting.bloods.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

@Getter
@RequiredArgsConstructor
public enum Locations {
    TRUE_BLOOD("Blood Locations", 43479, 25380, new WorldPoint(3231, 4831, 0),
            new WorldPoint(3560, 9780, 0));


    private final String altarName;
    private final int altarID;
    private final int ruinsID;
    private final WorldPoint altarWorldPoint;
    private final WorldPoint ruinsWorldPoint;
}
