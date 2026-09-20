package net.runelite.client.plugins.microbot.api;

import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.reachable.Rs2Reachable;

public interface IEntity {
    int getId();
    String getName();
    WorldPoint getWorldLocation();
    /** Coordinates in the originating view, without main-world projection. */
    default WorldPoint getSceneWorldLocation() { return getWorldLocation(); }
    LocalPoint getLocalLocation();
    WorldView getWorldView();
    boolean click();
    boolean click(String action);
    default boolean isReachable() {
        return net.runelite.client.plugins.microbot.Microbot.getClientThread().invoke(
                (java.util.function.Supplier<Boolean>) () -> getWorldView() != null
                        && Rs2Reachable.isReachable(getSceneWorldLocation(), getWorldView()));
    }
}
