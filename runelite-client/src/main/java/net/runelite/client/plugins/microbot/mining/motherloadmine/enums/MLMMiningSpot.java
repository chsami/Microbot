package net.runelite.client.plugins.microbot.mining.motherloadmine.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum MLMMiningSpot {
    // Internal / placeholder states
    IDLE(null, null),
    ANY(null, null),
    NORTH(null, null), // Not currently used

    // Lower floor mining areas
    WEST_LOWER(Arrays.asList(
            new WorldPoint(3731, 5659, 0),
            new WorldPoint(3731, 5663, 0)
    ), false),

    WEST_MID(Arrays.asList(
            new WorldPoint(3730, 5666, 0),
            new WorldPoint(3731, 5669, 0)
    ), false),

    SOUTH_EAST(Arrays.asList(
            new WorldPoint(3753, 5650, 0),
            new WorldPoint(3756, 5653, 0)
    ), false),

    SOUTH_WEST(Arrays.asList(
            new WorldPoint(3740, 5648, 0),
            new WorldPoint(3740, 5648, 0)
    ), false),

    // Upper floor mining areas
    WEST_UPPER(Arrays.asList(
            new WorldPoint(3752, 5683, 0),
            new WorldPoint(3752, 5680, 0)
    ), true),

    EAST_UPPER(Arrays.asList(
            new WorldPoint(3760, 5673, 0),
            new WorldPoint(3759, 5673, 0)
    ), true);

    private final List<WorldPoint> worldPoint;
    private final Boolean isUpstairs;

    /**
     * Safely checks if the spot is upstairs.
     * Returns false if unknown.
     */
    public boolean isUpstairs() {
        return Boolean.TRUE.equals(isUpstairs);
    }

    /**
     * Safely checks if the spot is downstairs.
     * Returns false if unknown.
     */
    public boolean isDownstairs() {
        return Boolean.FALSE.equals(isUpstairs);
    }
}