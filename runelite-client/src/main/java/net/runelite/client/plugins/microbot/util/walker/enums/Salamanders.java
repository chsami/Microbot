package net.runelite.client.plugins.microbot.util.walker.enums;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@Getter
public enum Salamanders {
    
    BLACK_SALAMANDER("Black Salamander (Boneyard Hunter Area)", new WorldPoint(3294, 3673, 0)),
    GREEN_SALAMANDER("Green Salamander (South Canifis)", new WorldPoint(3537, 3448, 0)),
    GREEN_SALAMANDER_2("Green Salamander (South-east Canifis)", new WorldPoint(3553, 3439, 0)),
    ORANGE_SALAMANDER("Orange Salamander (Necropolis)", new WorldPoint(3285, 2739, 0)),
    ORANGE_SALAMANDER_2("Orange Salamander (Uzer Hunter Area)", new WorldPoint(3401, 3104, 0)),
    RED_SALAMANDER("Red Salamander (Ourania Hunter Area East)", new WorldPoint(2450, 3223, 0)),
    RED_SALAMANDER_2("Red Salamander (Ourania Hunter Area South)", new WorldPoint(2475, 3240, 0)),
    TECU_SALAMANDER("Tecu Salamander (Ralos Rise)", new WorldPoint(1475, 3096, 0));


    private final String name;
    private WorldPoint worldPoint;

    Salamanders(String name, WorldPoint worldPoint) {
        this.name = name;
        this.worldPoint = worldPoint;
    }

    Salamanders(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
