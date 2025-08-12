package net.runelite.client.plugins.microbot.bga.autoherbiboar;

import net.runelite.api.coords.WorldPoint;

enum AutoHerbiboarSpots {
    A_MUSHROOM(new WorldPoint(3670,3889,0)),
    A_PATCH(new WorldPoint(3672,3890,0)),
    B_SEAWEED(new WorldPoint(3728,3893,0)),
    C_MUSHROOM(new WorldPoint(3697,3875,0)),
    C_PATCH(new WorldPoint(3699,3875,0)),
    D_PATCH(new WorldPoint(3708,3876,0)),
    D_SEAWEED(new WorldPoint(3710,3877,0)),
    E_MUSHROOM(new WorldPoint(3668,3865,0)),
    E_PATCH(new WorldPoint(3667,3862,0)),
    F_MUSHROOM(new WorldPoint(3681,3860,0)),
    F_PATCH(new WorldPoint(3681,3859,0)),
    G_MUSHROOM(new WorldPoint(3694,3847,0)),
    G_PATCH(new WorldPoint(3698,3847,0)),
    H_SEAWEED_EAST(new WorldPoint(3715,3851,0)),
    H_SEAWEED_WEST(new WorldPoint(3713,3850,0)),
    I_MUSHROOM(new WorldPoint(3680,3838,0)),
    I_PATCH(new WorldPoint(3680,3836,0)),
    J_PATCH(new WorldPoint(3713,3840,0)),
    K_PATCH(new WorldPoint(3706,3811,0));

    private final WorldPoint location;
    AutoHerbiboarSpots(WorldPoint location){this.location=location;}
    WorldPoint getLocation(){return location;}
}
