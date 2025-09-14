package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum HouseStyle {
    BASIC_WOOD(1, new WorldPoint(1858, 5707, 0)),
    BASIC_STONE(2, new WorldPoint(1858, 5707, 1)),
    WHITEWASHED_STONE(3, new WorldPoint(1858, 5707, 2)),
    FREMENNIK_STYLE_WOOD(4, new WorldPoint(1858, 5707, 3)),
    TROPICAL_WOOD(5, new WorldPoint(1922, 5707, 0)),
    FANCY_STONE(6, new WorldPoint(1922, 5707, 1)),
    DEADLY_MANSION(7, new WorldPoint(1922, 5707, 2)),
    TWISTED_THEME(8, new WorldPoint(1922, 5707, 3)),
    COSY_CABIN(10, new WorldPoint(1986, 5707, 0)),
    HOSIDIUS(11, new WorldPoint(1986, 5707, 0)),
    UNKNOWN(12, new WorldPoint(1986, 5707, 1)),
    CIVITAS(13, new WorldPoint(1986, 5707, 2)),
    CANIFIS(14, new WorldPoint(1986, 5707, 3)),
    ;

    private final int varbitValue;
    private final WorldPoint pohExitWorldPoint;

    public static HouseStyle getStyle() {
        int varbitValue = Microbot.getVarbitValue(VarbitID.POH_HOUSE_STYLE);
        for (HouseStyle style : values()) {
            if (varbitValue == style.varbitValue) {
                return style;
            }
        }
        return null;
    }

    public static WorldPoint[] getExitPortalLocations() {
        return Arrays.stream(values()).map(HouseStyle::getPohExitWorldPoint).distinct().toArray(WorldPoint[]::new);
    }

    public static boolean isPohExitLocation(WorldPoint pohLocation) {
        return Arrays.asList(getExitPortalLocations()).contains(pohLocation);
    }

    public static WorldPoint getPohExitPoint() {
        HouseStyle style = getStyle();
        return style != null ? style.getPohExitWorldPoint() : null;
    }
}

