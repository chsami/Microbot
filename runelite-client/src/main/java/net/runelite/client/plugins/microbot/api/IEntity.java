package net.runelite.client.plugins.microbot.api;

import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public interface IEntity {
    int getId();
    String getName();
    WorldPoint getWorldLocation();
    LocalPoint getLocalLocation();
    boolean click();
    boolean click(String action);
}
