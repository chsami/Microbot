package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;

@Getter
@RequiredArgsConstructor
public enum HouseStyle {
    BASIC_WOOD(1, new WorldPoint(1858, 5707, 0)),
    BASIC_STONE(2, new WorldPoint(1858, 5707, 0)),
    WHITEWASHED_STONE(3, new WorldPoint(1858, 5707, 0)),
    FREMENNIK_STYLE_WOOD(4, new WorldPoint(1858, 5707, 0)),
    TROPICAL_WOOD(5, new WorldPoint(1922, 5707, 0)),
    FANCY_STONE(6, new WorldPoint(1922, 5707, 0)),
    DEADLY_MANSION(7, new WorldPoint(1922, 5707, 0)),
    TWISTED_THEME(8, new WorldPoint(1922, 5707, 0)),
    COSY_CABIN(10, new WorldPoint(1986, 5707, 0)),
    HOSIDIUS(11, new WorldPoint(1986, 5707, 0)),
    CIVITAS(13, new WorldPoint(1986, 5707, 0)),
    CANIFIS(14, new WorldPoint(1986, 5707, 0)),
    ;

    private final int varbitValue;
    private final WorldPoint pohLocation;

    public static HouseStyle getStyle() {
        int varbitValue = Microbot.getVarbitValue(VarbitID.POH_HOUSE_STYLE);
        for (HouseStyle style : values()) {
            if (varbitValue == style.varbitValue) {
                return style;
            }
        }
        return null;
    }
}
