package net.runelite.client.plugins.microbot.frosty.trueblood.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ObjectID;
import net.runelite.api.World;
import net.runelite.api.coords.WorldPoint;

@Getter
@RequiredArgsConstructor
public enum Altar {
    TRUE_BLOOD("Blood Altar", ObjectID.ALTAR_43479, 43477, new WorldPoint(3560, 9780, 0));


    private final String altarName;
    private final int altarID;
    private final int ruinsID;
    private final WorldPoint altarWorldPoint;
}