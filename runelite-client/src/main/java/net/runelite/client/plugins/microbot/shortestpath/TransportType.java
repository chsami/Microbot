package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;

public enum TransportType {
    TRANSPORT,
    AGILITY_SHORTCUT,
    GRAPPLE_SHORTCUT,
    BOAT,
    CANOE,
    CHARTER_SHIP,
    SHIP,
    FAIRY_RING,
    QUETZAL,
    GNOME_GLIDER,
    MINECART,
    POH,
    SPIRIT_TREE,
    TELEPORTATION_LEVER,
    TELEPORTATION_PORTAL,
    TELEPORTATION_MINIGAME,
    TELEPORTATION_ITEM,
    TELEPORTATION_SPELL,
    WILDERNESS_OBELISK,
    MAGIC_CARPET,
    HOT_AIR_BALLOON,
    MAGIC_MUSHTREE,
    SEASONAL_TRANSPORT,
    NPC;

    /*
     * Indicates whether a TransportType is a teleport.
     * Levers, portals and wilderness obelisks are considered transports
     * and not teleports because they have a pre-defined origin and no
     * wilderness level limit.
     */
    /**
     * Teleport classification when origin is unknown. Seasonal rows default to {@code true} so
     * disabling teleports / item detection stays conservative.
     */
    public static boolean isTeleport(TransportType transportType) {
        return isTeleport(transportType, null);
    }

    /**
     * Whether this transport should follow teleport costing and walker teleport branches.
     * {@link #SEASONAL_TRANSPORT} rows with a non-null origin (object/NPC anchored) are treated like
     * ordinary transports: walk to origin, then use — no {@code distanceBeforeUsingTeleport} penalty.
     *
     * @param origin transport origin; {@code null} means originless (catalog teleport-style seasonal)
     */
    public static boolean isTeleport(TransportType transportType, WorldPoint origin) {
        if (transportType == null) {
            return false;
        }
        if (transportType == SEASONAL_TRANSPORT) {
            return origin == null;
        }
        switch (transportType) {
            case TELEPORTATION_ITEM:
            case TELEPORTATION_MINIGAME:
            case TELEPORTATION_SPELL:
                return true;
            default:
                return false;
        }
    }

}
