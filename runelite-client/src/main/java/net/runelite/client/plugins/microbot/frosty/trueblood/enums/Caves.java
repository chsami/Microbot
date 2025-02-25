package net.runelite.client.plugins.microbot.frosty.trueblood.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldPoint;


@Getter
@RequiredArgsConstructor
public enum Caves {
    FIRST_CAVE("First", ObjectID.CAVE_ENTRANCE_16308, new WorldPoint(3447, 9821, 0)),
    SECOND_CAVE("Second", ObjectID.CAVE_ENTRANCE_5046, new WorldPoint(3467, 9820, 0)),
    THIRD_CAVE("Third", ObjectID.CAVE_ENTRANCE_12770, new WorldPoint(3484, 9832, 0)),
    FOURTH_CAVE("Fourth", ObjectID.CAVE_ENTRANCE_12771, new WorldPoint(3492, 9861, 0)),
    AGILITY_74("Fifth", ObjectID.CAVE_43755, new WorldPoint(3560, 9813, 0)),
    AGILITY_75("Sixth", ObjectID.CAVE_43758, new WorldPoint(3555, 9787, 0));


    private final String whichCave;
    private final int idCave;
    private final WorldPoint caveWorldPoint;
}