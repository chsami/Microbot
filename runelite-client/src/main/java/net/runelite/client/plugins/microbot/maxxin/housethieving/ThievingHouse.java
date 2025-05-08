package net.runelite.client.plugins.microbot.maxxin.housethieving;

import net.runelite.api.coords.WorldPoint;

import static net.runelite.client.plugins.microbot.maxxin.housethieving.HouseThievingConstants.*;


public enum ThievingHouse {
    LAVINIA(
            "Lavinia",
            LAVINIA_HOUSE_CENTER,
            LAVINIA_LOCKED_DOOR_ENTRANCE,
            LAVINIA_LOCKED_DOOR_EGRESS,
            LAVINIA_WINDOW_ENTRANCE,
            LAVINIA_WINDOW_EGRESS,
            LAVINIA_INITIAL_THIEVING_CHEST
    ),
    VICTOR(
            "Victor",
            VICTOR_HOUSE_CENTER,
            VICTOR_LOCKED_DOOR_ENTRANCE,
            VICTOR_LOCKED_DOOR_EGRESS,
            VICTOR_WINDOW_ENTRANCE,
            VICTOR_WINDOW_EGRESS,
            VICTOR_INITIAL_THIEVING_CHEST
    ),
    CAIUS(
            "Caius",
            CAIUS_HOUSE_CENTER,
            CAIUS_LOCKED_DOOR_ENTRANCE,
            CAIUS_LOCKED_DOOR_EGRESS,
            CAIUS_WINDOW_ENTRANCE,
            CAIUS_WINDOW_EGRESS,
            CAIUS_INITIAL_THIEVING_CHEST
    );

    final String npcName;
    final WorldPoint houseCenter;
    final WorldPoint lockedDoorEntrance;
    final WorldPoint lockedDoorEgress;
    final WorldPoint windowEntrance;
    final WorldPoint windowEgress;
    final WorldPoint initialThievingChest;

    ThievingHouse(
            final String npcName,
            final WorldPoint houseCenter,
            final WorldPoint lockedDoorEntrance,
            final WorldPoint lockedDoorEgress,
            final WorldPoint windowEntrance,
            final WorldPoint windowEgress,
            final WorldPoint initialThievingChest
    ) {
        this.npcName = npcName;
        this.houseCenter = houseCenter;
        this.lockedDoorEntrance = lockedDoorEntrance;
        this.lockedDoorEgress = lockedDoorEgress;
        this.windowEntrance = windowEntrance;
        this.windowEgress = windowEgress;
        this.initialThievingChest = initialThievingChest;
    }
}
