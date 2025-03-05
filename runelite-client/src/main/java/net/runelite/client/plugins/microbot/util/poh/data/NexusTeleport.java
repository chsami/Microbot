package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.worldmap.TeleportType;

@Getter
@RequiredArgsConstructor
public enum NexusTeleport {
    VARROCK(TeleportType.NORMAL_MAGIC, "Varrock", new WorldPoint(3213, 3424, 0)),
    VARROCK_GE(TeleportType.NORMAL_MAGIC, "Grand Exchange", new WorldPoint(3164, 3478, 0)),
    LUMBRIDGE(TeleportType.NORMAL_MAGIC, "Lumbridge", new WorldPoint(3222, 3218, 0)),
    FALADOR(TeleportType.NORMAL_MAGIC, "Falador", new WorldPoint(2965, 3381, 0)),
    ARDOUGNE(TeleportType.NORMAL_MAGIC, "Ardougne", new WorldPoint(2664, 3306, 0)),
    CAMELOT(TeleportType.NORMAL_MAGIC, "Camelot", new WorldPoint(2757, 3477, 0)),
    WATCHTOWER(TeleportType.NORMAL_MAGIC, "Watchtower", new WorldPoint(2547, 3114, 0)),
    KOUREND(TeleportType.NORMAL_MAGIC, "Kourend Castle", new WorldPoint(1643, 3672, 0)),
    MARIM(TeleportType.NORMAL_MAGIC, "Marim", new WorldPoint(2797, 2798, 1)),
    SENNTISTEN(TeleportType.ANCIENT_MAGICKS, "Senntisten", new WorldPoint(3319, 3336, 0)),
    KHARYRLL(TeleportType.ANCIENT_MAGICKS, "Kharyrll", new WorldPoint(3494, 3473, 0)),
    CARRALLANGER(TeleportType.ANCIENT_MAGICKS, "Carrallanger", new WorldPoint(3157, 3667, 0)),
    ANNAKARL(TeleportType.ANCIENT_MAGICKS, "Annakarl", new WorldPoint(3288, 3888, 0)),
    LUNAR_ISLE(TeleportType.LUNAR_MAGIC, "Lunar Isle", new WorldPoint(2093, 3912, 0)),
    WATERBIRTH(TeleportType.LUNAR_MAGIC, "Waterbirth Island", new WorldPoint(2546, 3755, 0)),
    FISHING_GUILD(TeleportType.LUNAR_MAGIC, "Fishing Guild", new WorldPoint(2612, 3391, 0)),
    ARCEUUS_LIBRARY(TeleportType.ARCEUUS_MAGIC, "Arceuus Library", new WorldPoint(1632, 3838, 0)),
    DRAYNOR_MANOR(TeleportType.ARCEUUS_MAGIC, "Draynor Manor", new WorldPoint(3108, 3352, 0)),
    BATTLEFRONT(TeleportType.ARCEUUS_MAGIC, "Battlefront", new WorldPoint(1349, 3739, 0)),
    MIND_ALTAR(TeleportType.ARCEUUS_MAGIC, "Mind Altar", new WorldPoint(2979, 3509, 0)),
    FENKENSTRAINS_CASTLE(TeleportType.ARCEUUS_MAGIC, "Fenken' Castle", new WorldPoint(3548, 3528, 0)),
    SALVE_GRAVEYARD(TeleportType.ARCEUUS_MAGIC, "Salve Graveyard", new WorldPoint(3433, 3461, 0)),
    WEST_ARDOUGNE(TeleportType.ARCEUUS_MAGIC, "West Ardougne", new WorldPoint(2500, 3291, 0)),
    HARMONY_ISLAND(TeleportType.ARCEUUS_MAGIC, "Harmony Island", new WorldPoint(3797, 2866, 0)),
    CEMETERY(TeleportType.ARCEUUS_MAGIC, "Cemetery", new WorldPoint(2978, 3763, 0)),
    BARROWS(TeleportType.ARCEUUS_MAGIC, "Barrows", new WorldPoint(3565, 3315, 0)),
    APE_ATOLL_DUNGEON(TeleportType.ARCEUUS_MAGIC, "Ape Atoll Dungeon", new WorldPoint(2770, 2703, 0)),
    CATHERBY(TeleportType.LUNAR_MAGIC, "Catherby", new WorldPoint(2802, 3449, 0)),
    GHORROCK(TeleportType.ANCIENT_MAGICKS, "Ghorrock", new WorldPoint(2977, 3872, 0)),
    WEISS(TeleportType.OTHER, "Weiss", new WorldPoint(2846, 3940, 0)),
    TROLL_STRONGHOLD(TeleportType.OTHER, "Troll Stronghold", new WorldPoint(2838, 3693, 0)),
    CIVITAS_ILLA_FORTIS(TeleportType.NORMAL_MAGIC, "Civitas illa Fortis", new WorldPoint(1681, 3133, 0));


    private final TeleportType type;
    private final String text;
    private final WorldPoint location;

}
