package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.worldmap.TeleportLocationData;
import net.runelite.client.plugins.worldmap.TeleportType;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.api.gameval.VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE;


/**
 *
 */
@Getter
@RequiredArgsConstructor
public enum NexusPortal implements PohTeleport {
    VARROCK(TeleportType.NORMAL_MAGIC, "Varrock", TeleportLocationData.VARROCK.getLocation()),
    VARROCK_GE(TeleportType.NORMAL_MAGIC, "Grand Exchange", TeleportLocationData.VARROCK_GE.getLocation()),
    LUMBRIDGE(TeleportType.NORMAL_MAGIC, "Lumbridge", TeleportLocationData.LUMBRIDGE.getLocation()),
    FALADOR(TeleportType.NORMAL_MAGIC, "Falador", TeleportLocationData.FALADOR.getLocation()),
    CAMELOT(TeleportType.NORMAL_MAGIC, "Camelot", TeleportLocationData.CAMELOT.getLocation()),
    KOUREND(TeleportType.NORMAL_MAGIC, "Kourend Castle", TeleportLocationData.KOUREND.getLocation()),
    ARDOUGNE(TeleportType.NORMAL_MAGIC, "Ardougne", TeleportLocationData.ARDOUGNE.getLocation()),
    WATCHTOWER(TeleportType.NORMAL_MAGIC, "Watchtower", TeleportLocationData.WATCHTOWER.getLocation()),
    MARIM(TeleportType.NORMAL_MAGIC, "Marim", TeleportLocationData.APE_ATOLL.getLocation()),
    SENNTISTEN(TeleportType.ANCIENT_MAGICKS, "Senntisten", TeleportLocationData.SENNTISTEN.getLocation()),
    KHARYRLL(TeleportType.ANCIENT_MAGICKS, "Kharyrll", TeleportLocationData.KHARYRLL.getLocation()),
    CARRALLANGER(TeleportType.ANCIENT_MAGICKS, "Carrallanger", TeleportLocationData.CARRALLANGER.getLocation()),
    WATERBIRTH(TeleportType.LUNAR_MAGIC, "Waterbirth Island", TeleportLocationData.WATERBIRTH.getLocation()),
    ANNAKARL(TeleportType.ANCIENT_MAGICKS, "Annakarl", TeleportLocationData.ANNAKARL.getLocation()),
    GHORROCK(TeleportType.ANCIENT_MAGICKS, "Ghorrock", TeleportLocationData.GHORROCK.getLocation()),
    LUNAR_ISLE(TeleportType.LUNAR_MAGIC, "Lunar Isle", TeleportLocationData.MOONCLAN.getLocation()),
    CATHERBY(TeleportType.LUNAR_MAGIC, "Catherby", TeleportLocationData.CATHERBY.getLocation()),
    FISHING_GUILD(TeleportType.LUNAR_MAGIC, "Fishing Guild", TeleportLocationData.FISHING_GUILD.getLocation()),
    TROLL_STRONGHOLD(TeleportType.OTHER, "Troll Stronghold", TeleportLocationData.TROLLHEIM.getLocation()),
    WEISS(TeleportType.OTHER, "Weiss", TeleportLocationData.WEISS_ICY_BASALT.getLocation()),
    ARCEUUS_LIBRARY(TeleportType.ARCEUUS_MAGIC, "Arceuus Library", TeleportLocationData.ARCEUUS_LIBRARY.getLocation()),
    DRAYNOR_MANOR(TeleportType.ARCEUUS_MAGIC, "Draynor Manor", TeleportLocationData.DRAYNOR_MANOR.getLocation()),
    BATTLEFRONT(TeleportType.ARCEUUS_MAGIC, "Battlefront", TeleportLocationData.BATTLEFRONT.getLocation()),
    MIND_ALTAR(TeleportType.ARCEUUS_MAGIC, "Mind Altar", TeleportLocationData.MIND_ALTAR.getLocation()),
    SALVE_GRAVEYARD(TeleportType.ARCEUUS_MAGIC, "Salve Graveyard", TeleportLocationData.SALVE_GRAVEYARD.getLocation()),
    FENKENSTRAINS_CASTLE(TeleportType.ARCEUUS_MAGIC, "Fenken' Castle", TeleportLocationData.FENKENSTRAINS_CASTLE.getLocation()),
    WEST_ARDOUGNE(TeleportType.ARCEUUS_MAGIC, "West Ardougne", TeleportLocationData.WEST_ARDOUGNE.getLocation()),
    HARMONY_ISLAND(TeleportType.ARCEUUS_MAGIC, "Harmony Island", TeleportLocationData.HARMONY_ISLAND.getLocation()),
    CEMETERY(TeleportType.ARCEUUS_MAGIC, "Cemetery", TeleportLocationData.CEMETERY.getLocation()),
    BARROWS(TeleportType.ARCEUUS_MAGIC, "Barrows", TeleportLocationData.BARROWS.getLocation()),
    APE_ATOLL_DUNGEON(TeleportType.ARCEUUS_MAGIC, "Ape Atoll Dungeon", TeleportLocationData.APE_ATOLL_ARCEUUS.getLocation()),
    CIVITAS_ILLA_FORTIS(TeleportType.NORMAL_MAGIC, "Civitas illa Fortis", TeleportLocationData.CIVITAS_ILLA_FORTIS.getLocation());

    private final TeleportType type;
    private final String text;
    private final WorldPoint location;

    private final int duration = 6;

    @Override
    public String displayInfo() {
        return "NexusTeleport -> " + text;
    }

    @Override
    public WorldPoint getDestination() {
        return location;
    }

    public int varbitValue() {
        int ordinal = ordinal();
        //Since you get Varrock GE for free with Varrock, there's no varbit value for it
        return ordinal > 2 ? ordinal : ordinal - 1;
    }


    @Override
    public boolean execute() {
        return PohTeleports.usePortalNexus(this);
    }

    public final static Integer[] PORTAL_IDS = {ObjectID.POH_NEXUS_PORTAL_1, ObjectID.POH_NEXUS_PORTAL_2, ObjectID.POH_NEXUS_PORTAL_3, ObjectID.POH_NEXUS_PORTAL_LEAGUE_5};

    public static boolean isNexusPortal(GameObject go) {
        if (go == null) return false;
        for (int id : PORTAL_IDS) {
            if (id == go.getId()) return true;
        }
        return false;
    }

    public static List<NexusPortal> getAvailableTeleports() {
        List<NexusPortal> teleports = new ArrayList<>();
        for (int varbit : VARBITS) {
            int value = Microbot.getVarbitValue(varbit);
            if (value <= 0) continue;

            if (value == 1) {
                teleports.add(NexusPortal.VARROCK);
                if (Microbot.getVarbitValue(VARROCK_DIARY_MEDIUM_COMPLETE) == 1) {
                    teleports.add(NexusPortal.VARROCK_GE);
                }
                continue;
            }
            NexusPortal tp = NexusPortal.values()[value];
            teleports.add(tp);
        }
        return teleports;
    }

    public static final int[] VARBITS = new int[]{
            VarbitID.POH_NEXUS_TELE_1,
            VarbitID.POH_NEXUS_TELE_2,
            VarbitID.POH_NEXUS_TELE_3,
            VarbitID.POH_NEXUS_TELE_4,
            VarbitID.POH_NEXUS_TELE_5,
            VarbitID.POH_NEXUS_TELE_6,
            VarbitID.POH_NEXUS_TELE_7,
            VarbitID.POH_NEXUS_TELE_8,
            VarbitID.POH_NEXUS_TELE_9,
            VarbitID.POH_NEXUS_TELE_10,
            VarbitID.POH_NEXUS_TELE_11,
            VarbitID.POH_NEXUS_TELE_12,
            VarbitID.POH_NEXUS_TELE_13,
            VarbitID.POH_NEXUS_TELE_14,
            VarbitID.POH_NEXUS_TELE_15,
            VarbitID.POH_NEXUS_TELE_16,
            VarbitID.POH_NEXUS_TELE_17,
            VarbitID.POH_NEXUS_TELE_18,
            VarbitID.POH_NEXUS_TELE_19,
            VarbitID.POH_NEXUS_TELE_20,
            VarbitID.POH_NEXUS_TELE_21,
            VarbitID.POH_NEXUS_TELE_22,
            VarbitID.POH_NEXUS_TELE_23,
            VarbitID.POH_NEXUS_TELE_24,
            VarbitID.POH_NEXUS_TELE_25,
            VarbitID.POH_NEXUS_TELE_26,
            VarbitID.POH_NEXUS_TELE_27,
            VarbitID.POH_NEXUS_TELE_28,
            VarbitID.POH_NEXUS_TELE_29,
            VarbitID.POH_NEXUS_TELE_30,
            VarbitID.POH_NEXUS_TELE_31,
            VarbitID.POH_NEXUS_TELE_32,
            VarbitID.POH_NEXUS_TELE_33,
            VarbitID.POH_NEXUS_TELE_34,
            VarbitID.POH_NEXUS_TELE_35,
    };

}