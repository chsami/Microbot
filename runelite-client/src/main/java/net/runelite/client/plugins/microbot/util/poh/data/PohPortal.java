package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.worldmap.TeleportLocationData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public enum PohPortal implements PohTeleport {
    // Standard spellbook
    VARROCK("Varrock", TeleportLocationData.VARROCK.getLocation(),
            ObjectID.POH_PORTAL_MAG_VARROCK, ObjectID.POH_PORTAL_MARBLE_VARROCK, ObjectID.POH_PORTAL_TEAK_VARROCK,
            ObjectID.POH_PORTAL_MAHOGANY_VARROCK_VARROCK1ST, ObjectID.POH_PORTAL_LEAGUE_5_VARROCK),
    GRAND_EXCHANGE("Grand Exchange", TeleportLocationData.GRAND_EXCHANGE.getLocation(),
            ObjectID.POH_PORTAL_MAHOGANY_VARROCK_GE1ST, ObjectID.POH_PORTAL_TEAK_VARROCK_GE1ST, ObjectID.POH_PORTAL_LEAGUE_5_VARROCK_GE1ST,
            ObjectID.POH_PORTAL_MARBLE_VARROCK_GE1ST),
    LUMBRIDGE("Lumbridge", TeleportLocationData.LUMBRIDGE.getLocation(),
            ObjectID.POH_PORTAL_MAG_LUMBRIDGE, ObjectID.POH_PORTAL_MARBLE_LUMBRIDGE, ObjectID.POH_PORTAL_TEAK_LUMBRIDGE,
            ObjectID.POH_PORTAL_LEAGUE_5_LUMBRIDGE),
    FALADOR("Falador", TeleportLocationData.FALADOR.getLocation(),
            ObjectID.POH_PORTAL_MAG_FALADOR, ObjectID.POH_PORTAL_MARBLE_FALADOR, ObjectID.POH_PORTAL_TEAK_FALADOR,
            ObjectID.POH_PORTAL_LEAGUE_5_FALADOR),
    CAMELOT("Camelot", TeleportLocationData.CAMELOT.getLocation(),
            ObjectID.POH_PORTAL_MAG_CAMELOT, ObjectID.POH_PORTAL_MARBLE_CAMELOT, ObjectID.POH_PORTAL_TEAK_CAMELOT,
            ObjectID.POH_PORTAL_LEAGUE_5_CAMELOT),
    ARDOUGNE("Ardougne", TeleportLocationData.ARDOUGNE.getLocation(),
            ObjectID.POH_PORTAL_MAG_ARDOUGNE, ObjectID.POH_PORTAL_MARBLE_ARDOUGNE, ObjectID.POH_PORTAL_TEAK_ARDOUGNE,
            ObjectID.POH_PORTAL_LEAGUE_5_ARDOUGNE),
    WATCHTOWER("Watchtower", TeleportLocationData.WATCHTOWER.getLocation(),
            ObjectID.POH_PORTAL_MAHOGANY_YANILLE_WATCHTOWER1ST, ObjectID.POH_PORTAL_MARBLE_YANILLE_WATCHTOWER1ST, ObjectID.POH_PORTAL_TEAK_YANILLE_WATCHTOWER1ST,
            ObjectID.POH_PORTAL_LEAGUE_5_YANILLE_WATCHTOWER1ST),
    TROLL_STRONGHOLD("Troll Stronghold", TeleportLocationData.TROLLHEIM.getLocation(),
            ObjectID.POH_PORTAL_MAG_STRONGHOLD, ObjectID.POH_PORTAL_MARBLE_STRONGHOLD, ObjectID.POH_PORTAL_TEAK_STRONGHOLD,
            ObjectID.POH_PORTAL_LEAGUE_5_STRONGHOLD),
    APE_ATOLL("Ape Atoll", TeleportLocationData.APE_ATOLL.getLocation(),
            ObjectID.POH_PORTAL_MAG_APE_ATOLL, ObjectID.POH_PORTAL_MARBLE_APE_ATOLL, ObjectID.POH_PORTAL_TEAK_APE_ATOLL,
            ObjectID.POH_PORTAL_LEAGUE_5_APE_ATOLL),
    KOUREND_CASTLE("Kourend Castle", TeleportLocationData.KOUREND.getLocation(),
            ObjectID.POH_PORTAL_MAG_KOUREND, ObjectID.POH_PORTAL_MARBLE_KOUREND, ObjectID.POH_PORTAL_TEAK_KOUREND,
            ObjectID.POH_PORTAL_LEAGUE_5_KOUREND),

    // Ancient Magicks
    SENNTISTEN("Senntisten", TeleportLocationData.SENNTISTEN.getLocation(),
            ObjectID.POH_PORTAL_MAG_SENNTISTEN, ObjectID.POH_PORTAL_MARBLE_SENNTISTEN, ObjectID.POH_PORTAL_TEAK_SENNTISTEN,
            ObjectID.POH_PORTAL_LEAGUE_5_SENNTISTEN),
    KHARYRLL("Kharyrll", TeleportLocationData.KHARYRLL.getLocation(),
            ObjectID.POH_PORTAL_MAG_KHARYRLL, ObjectID.POH_PORTAL_MARBLE_KHARYRLL, ObjectID.POH_PORTAL_TEAK_KHARYRLL,
            ObjectID.POH_PORTAL_LEAGUE_5_KHARYRLL),
    CARRALLANGAR("Carrallangar", TeleportLocationData.CARRALLANGER.getLocation(),
            ObjectID.POH_PORTAL_MAG_CARRALLANGAR, ObjectID.POH_PORTAL_MARBLE_CARRALLANGAR, ObjectID.POH_PORTAL_TEAK_CARRALLANGAR,
            ObjectID.POH_PORTAL_LEAGUE_5_CARRALLANGAR),
    ANNAKARL("Annakarl", TeleportLocationData.ANNAKARL.getLocation(),
            ObjectID.POH_PORTAL_MAG_ANNAKARL, ObjectID.POH_PORTAL_MARBLE_ANNAKARL, ObjectID.POH_PORTAL_TEAK_ANNAKARL,
            ObjectID.POH_PORTAL_LEAGUE_5_ANNAKARL),
    GHORROCK("Ghorrock", TeleportLocationData.GHORROCK.getLocation(),
            ObjectID.POH_PORTAL_MAG_GHORROCK, ObjectID.POH_PORTAL_MARBLE_GHORROCK, ObjectID.POH_PORTAL_TEAK_GHORROCK,
            ObjectID.POH_PORTAL_LEAGUE_5_GHORROCK),

    // Lunar spellbook
    LUNAR_ISLE("Lunar Isle", TeleportLocationData.MOONCLAN.getLocation(),
            ObjectID.POH_PORTAL_MAG_LUNARISLE, ObjectID.POH_PORTAL_MARBLE_LUNARISLE, ObjectID.POH_PORTAL_TEAK_LUNARISLE,
            ObjectID.POH_PORTAL_LEAGUE_5_LUNARISLE),
    WATERBIRTH("Waterbirth", TeleportLocationData.WATERBIRTH.getLocation(),
            ObjectID.POH_PORTAL_MAG_WATERBIRTH, ObjectID.POH_PORTAL_MARBLE_WATERBIRTH, ObjectID.POH_PORTAL_TEAK_WATERBIRTH,
            ObjectID.POH_PORTAL_LEAGUE_5_WATERBIRTH),
    CATHERBY("Catherby", TeleportLocationData.CATHERBY.getLocation(),
            ObjectID.POH_PORTAL_MAG_CATHERBY, ObjectID.POH_PORTAL_MARBLE_CATHERBY, ObjectID.POH_PORTAL_TEAK_CATHERBY,
            ObjectID.POH_PORTAL_LEAGUE_5_CATHERBY),

    // Arceuus spellbook
    ARCEUUS_LIBRARY("Arceuus Library", TeleportLocationData.ARCEUUS_LIBRARY.getLocation(),
            ObjectID.POH_PORTAL_MAG_ARCEUUS_LIBRARY, ObjectID.POH_PORTAL_MARBLE_ARCEUUS_LIBRARY, ObjectID.POH_PORTAL_TEAK_ARCEUUS_LIBRARY,
            ObjectID.POH_PORTAL_LEAGUE_5_ARCEUUS_LIBRARY),
    DRAYNOR_MANOR("Draynor Manor", TeleportLocationData.DRAYNOR_MANOR.getLocation(),
            ObjectID.POH_PORTAL_MAG_DRAYNOR_MANOR, ObjectID.POH_PORTAL_MARBLE_DRAYNOR_MANOR, ObjectID.POH_PORTAL_TEAK_DRAYNOR_MANOR,
            ObjectID.POH_PORTAL_LEAGUE_5_DRAYNOR_MANOR),
    BATTLEFRONT("Battlefront", TeleportLocationData.BATTLEFRONT.getLocation(),
            ObjectID.POH_PORTAL_MAG_BATTLEFRONT, ObjectID.POH_PORTAL_MARBLE_BATTLEFRONT, ObjectID.POH_PORTAL_TEAK_BATTLEFRONT,
            ObjectID.POH_PORTAL_LEAGUE_5_BATTLEFRONT),
    MIND_ALTAR("Mind Altar", TeleportLocationData.MIND_ALTAR.getLocation(),
            ObjectID.POH_PORTAL_MAG_MIND_ALTAR, ObjectID.POH_PORTAL_MARBLE_MIND_ALTAR, ObjectID.POH_PORTAL_TEAK_MIND_ALTAR,
            ObjectID.POH_PORTAL_LEAGUE_5_MIND_ALTAR),
    SALVE_GRAVEYARD("Salve Graveyard", TeleportLocationData.SALVE_GRAVEYARD.getLocation(),
            ObjectID.POH_PORTAL_MAG_SALVE_GRAVEYARD, ObjectID.POH_PORTAL_MARBLE_SALVE_GRAVEYARD, ObjectID.POH_PORTAL_TEAK_SALVE_GRAVEYARD,
            ObjectID.POH_PORTAL_LEAGUE_5_SALVE_GRAVEYARD),
    FENKENSTRAINS_CASTLE("Fenkenstrain's Castle", TeleportLocationData.FENKENSTRAINS_CASTLE.getLocation(),
            ObjectID.POH_PORTAL_MAG_FENKENSTRAIN, ObjectID.POH_PORTAL_MARBLE_FENKENSTRAIN, ObjectID.POH_PORTAL_TEAK_FENKENSTRAIN,
            ObjectID.POH_PORTAL_LEAGUE_5_FENKENSTRAIN),
    WEST_ARDOUGNE("West Ardougne", TeleportLocationData.WEST_ARDOUGNE.getLocation(),
            ObjectID.POH_PORTAL_MAG_WEST_ARDOUGNE, ObjectID.POH_PORTAL_MARBLE_WEST_ARDOUGNE, ObjectID.POH_PORTAL_TEAK_WEST_ARDOUGNE,
            ObjectID.POH_PORTAL_LEAGUE_5_WEST_ARDOUGNE),
    HARMONY_ISLAND("Harmony Island", TeleportLocationData.HARMONY_ISLAND.getLocation(),
            ObjectID.POH_PORTAL_MAG_HARMONY_ISLAND, ObjectID.POH_PORTAL_MARBLE_HARMONY_ISLAND, ObjectID.POH_PORTAL_TEAK_HARMONY_ISLAND,
            ObjectID.POH_PORTAL_LEAGUE_5_HARMONY_ISLAND),
    BARROWS("Barrows", TeleportLocationData.BARROWS.getLocation(),
            ObjectID.POH_PORTAL_MAG_BARROWS, ObjectID.POH_PORTAL_MARBLE_BARROWS, ObjectID.POH_PORTAL_TEAK_BARROWS,
            ObjectID.POH_PORTAL_LEAGUE_5_BARROWS),

    //Extra
    WEISS("Weiss", TeleportLocationData.WEISS_ICY_BASALT.getLocation(),
            ObjectID.POH_PORTAL_MAG_WEISS, ObjectID.POH_PORTAL_MARBLE_WEISS, ObjectID.POH_PORTAL_TEAK_WEISS,
            ObjectID.POH_PORTAL_LEAGUE_5_WEISS),

    // Backfilled 2026-07-20 from upstream Skretzo/shortest-path teleportation_portals_poh.tsv
    // (destination = authoritative arrival tile from that data set). MAG/MARBLE/TEAK object-id
    // families verified present; League variants omitted intentionally.
    BARBARIAN_OUTPOST("Barbarian Outpost", new WorldPoint(2543, 3569, 0),
            ObjectID.POH_PORTAL_MAG_BARBARIAN, ObjectID.POH_PORTAL_MARBLE_BARBARIAN, ObjectID.POH_PORTAL_TEAK_BARBARIAN),
    FISHING_GUILD("Fishing Guild", new WorldPoint(2613, 3391, 0),
            ObjectID.POH_PORTAL_MAG_FISHINGGUILD, ObjectID.POH_PORTAL_MARBLE_FISHINGGUILD, ObjectID.POH_PORTAL_TEAK_FISHINGGUILD),
    PORT_KHAZARD("Port Khazard", new WorldPoint(2636, 3167, 0),
            ObjectID.POH_PORTAL_MAG_KHAZARD, ObjectID.POH_PORTAL_MARBLE_KHAZARD, ObjectID.POH_PORTAL_TEAK_KHAZARD),
    MARIM("Marim", new WorldPoint(2797, 2798, 1),
            ObjectID.POH_PORTAL_MAG_MARIM, ObjectID.POH_PORTAL_MARBLE_MARIM, ObjectID.POH_PORTAL_TEAK_MARIM),
    CIVITAS_ILLA_FORTIS("Civitas illa Fortis", new WorldPoint(1681, 3133, 0),
            ObjectID.POH_PORTAL_MAG_FORTIS, ObjectID.POH_PORTAL_MARBLE_FORTIS, ObjectID.POH_PORTAL_TEAK_FORTIS),
    OURANIA("Ourania", new WorldPoint(2468, 3246, 0),
            ObjectID.POH_PORTAL_MAG_OURANIA, ObjectID.POH_PORTAL_MARBLE_OURANIA, ObjectID.POH_PORTAL_TEAK_OURANIA),
    CEMETERY("Cemetery", new WorldPoint(2981, 3764, 0),
            ObjectID.POH_PORTAL_MAG_CEMETERY, ObjectID.POH_PORTAL_MARBLE_CEMETERY, ObjectID.POH_PORTAL_TEAK_CEMETERY),
    DAREEYAK("Dareeyak", new WorldPoint(2968, 3696, 0),
            ObjectID.POH_PORTAL_MAG_DAREEYAK, ObjectID.POH_PORTAL_MARBLE_DAREEYAK, ObjectID.POH_PORTAL_TEAK_DAREEYAK),
    TROLLHEIM("Trollheim", new WorldPoint(2890, 3679, 0),
            ObjectID.POH_PORTAL_MAG_TROLLHEIM, ObjectID.POH_PORTAL_MARBLE_TROLLHEIM, ObjectID.POH_PORTAL_TEAK_TROLLHEIM),
    LASSAR("Lassar", new WorldPoint(3004, 3470, 0),
            ObjectID.POH_PORTAL_MAG_LASSAR, ObjectID.POH_PORTAL_MARBLE_LASSAR, ObjectID.POH_PORTAL_TEAK_LASSAR),
    ICE_PLATEAU("Ice Plateau", new WorldPoint(2975, 3938, 0),
            ObjectID.POH_PORTAL_MAG_ICEPLATEAU, ObjectID.POH_PORTAL_MARBLE_ICEPLATEAU, ObjectID.POH_PORTAL_TEAK_ICEPLATEAU),
    PADDEWWA("Paddewwa", new WorldPoint(3097, 9881, 0),
            ObjectID.POH_PORTAL_MAG_PADDEWWA, ObjectID.POH_PORTAL_MARBLE_PADDEWWA, ObjectID.POH_PORTAL_TEAK_PADDEWWA),
    ;

    PohPortal(String displayName, WorldPoint destination, Integer... objectIds) {
        this.displayName = displayName;
        this.destination = destination;
        this.objectIds = objectIds;
    }

    private final String displayName;
    private final WorldPoint destination;
    private final Integer[] objectIds;
    private final int duration = 4;

    public GameObject getPortal() {
        return Rs2GameObject.getGameObject(objectIds);
    }

    @Override
    public String displayInfo() {
        return "PoHPortal -> " + displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    @Override
    public boolean execute() {
        GameObject portal = getPortal();
        return Rs2GameObject.interact(portal, "enter");
    }

    private static final Map<Integer, PohPortal> BY_OBJECT_ID;

    static {
        BY_OBJECT_ID = new HashMap<>();
        for (PohPortal p : PohPortal.values()) {
            for (int id : p.objectIds) {
                BY_OBJECT_ID.put(id, p);
            }
        }
    }

    public static PohPortal getPohPortal(GameObject portal) {
        if (portal == null) {
            return null;
        }
        return BY_OBJECT_ID.get(portal.getId());
    }

    public static List<PohPortal> findPortalsInPoh() {
        List<PohPortal> portals = new ArrayList<>();
        for (GameObject obj : Rs2GameObject.getGameObjects(o -> BY_OBJECT_ID.containsKey(o.getId()))) {
            PohPortal portal = BY_OBJECT_ID.get(obj.getId());
            if (portal != null && !portals.contains(portal)) {
                portals.add(portal);
            }
        }
        return portals;
    }
}