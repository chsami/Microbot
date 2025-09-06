package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.worldmap.TeleportLocationData;

import static net.runelite.api.ObjectID.PORTAL_28822;

@Getter
@RequiredArgsConstructor
public enum HouseLocation {
    RIMMINGTON(1, TeleportLocationData.CONSTRUCTION_CAPE_RIMMINGTON.getLocation(), ObjectID.POH_RIMMINGTON_PORTAL),
    TAVERLY(2, TeleportLocationData.CONSTRUCTION_CAPE_TAVERLEY.getLocation(), ObjectID.POH_TAVERLY_PORTAL),
    POLIVNEACH(3, TeleportLocationData.CONSTRUCTION_CAPE_POLLNIVNEACH.getLocation(), ObjectID.POH_POLLNIVNEACH_PORTAL),
    RELLEKKA(4, TeleportLocationData.CONSTRUCTION_CAPE_RELLEKKA.getLocation(), ObjectID.POH_RELLEKKA_PORTAL),
    BRIMHAVEN(5, TeleportLocationData.CONSTRUCTION_CAPE_BRIMHAVEN.getLocation(), ObjectID.POH_BRIMHAVEN_PORTAL),
    YANILLE(6, TeleportLocationData.CONSTRUCTION_CAPE_YANILLE.getLocation(), ObjectID.POH_YANILLE_PORTAL),
    HOSIDIUS(8, TeleportLocationData.CONSTRUCTION_CAPE_HOSIDIUS.getLocation(), PORTAL_28822),
    PRIFDDINAS(9, TeleportLocationData.CONSTRUCTION_CAPE_PRIFDDINAS.getLocation(), ObjectID.POH_PRIFDDINAS_PORTAL),
    ALDARIN(13, TeleportLocationData.CONSTRUCTION_CAPE_ALDARIN.getLocation(), ObjectID.POH_ALDARIN_PORTAL),
    ;

    private final int varbitValue;
    private final WorldPoint portalLocation;
    private final int portalId;


    public static HouseLocation getHouseLocation() {
        int varbitValue = Microbot.getVarbitValue(VarbitID.POH_HOUSE_LOCATION);
        for (HouseLocation hl : values()) {
            if (hl.varbitValue == varbitValue) {
                return hl;
            }
        }
        return null;
    }

    public static String buildPortalMappingTSV() {
        StringBuilder sb = new StringBuilder(
                "Origin\tDestination\tisMembers\tmenuOption menuTarget objectID\tVarbits\tDisplay info\tDuration\n"
        );

        for (HouseLocation location : HouseLocation.values()) {
            for (HouseStyle style : HouseStyle.values()) {

                String origin = formatWorldPoint(location.getPortalLocation());
                String destination = formatWorldPoint(style.getPohLocation());

                String isMembers = "Y";
                String menu = "Home;Portal;" + location.getPortalId();

                String varbits = String.format("%s=%s;%s=%s", VarbitID.POH_HOUSE_STYLE, style.getVarbitValue(), VarbitID.POH_HOUSE_LOCATION, location.getVarbitValue());

                String displayInfo = location.name() + " -> PoH";
                String duration = "1";

                sb.append(origin).append("\t")
                        .append(destination).append("\t")
                        .append(isMembers).append("\t")
                        .append(menu).append("\t")
                        .append(varbits).append("\t")
                        .append(displayInfo).append("\t")
                        .append(duration).append("\n");
            }
        }

        return sb.toString();
    }

    private static String formatWorldPoint(WorldPoint worldPoint) {
        return worldPoint.getX() + " " + worldPoint.getY() + " " + worldPoint.getPlane();
    }
}
