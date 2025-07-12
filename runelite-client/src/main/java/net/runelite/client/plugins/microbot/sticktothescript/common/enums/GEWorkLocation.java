package net.runelite.client.plugins.microbot.sticktothescript.common.enums;

import net.runelite.api.coords.WorldPoint;

public enum GEWorkLocation {
    NORTH_EAST("North East", new WorldPoint(3168, 3490, 0)),
    SOUTH_EAST("South East", new WorldPoint(3168, 3489, 0)),
    NORTH_WEST("North West", new WorldPoint(3161, 3490, 0)),
    SOUTH_WEST("South West", new WorldPoint(3161, 3489, 0));

    private final String locationName;
    private final WorldPoint worldPoint;

    GEWorkLocation(final String _locationName,
            final WorldPoint _worldPoint) {
        locationName = _locationName;
        worldPoint = _worldPoint;
    }

    public String getName() {
        return locationName;
    }

    public WorldPoint getWorldPoint() {
        return worldPoint;
    }
}
