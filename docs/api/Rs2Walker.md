# Rs2Walker Class Documentation

## [Back](development.md)

## Overview
The `Rs2Walker` class is the primary utility for handling player movement and pathfinding. It supports walking to specific coordinates, handling obstacles, using transports (teleports, ships, etc.), and interacting with the minimap.

## Methods

### `canReach`
- **Signature**: `public static boolean canReach(WorldPoint worldPoint)`
- **Description**: Checks if a specific world point is reachable from the current location.

### `getWalkPath`
- **Signature**: `public static List<WorldPoint> getWalkPath(WorldPoint target)`
- **Description**: Retrieves the walk path from the player's current location to the specified target location.

### `getTotalTiles`
- **Signature**: `public static int getTotalTiles(WorldPoint destination)`
- **Description**: Gets the total amount of tiles to travel to destination. Returns `Integer.MAX_VALUE` if unreachable.

### `setTarget`
- **Signature**: `public static void setTarget(WorldPoint target)`
- **Description**: Sets the current target for the walker. Can be used to update or clear the target.

### `walkFastCanvas`
- **Signature**: `public static boolean walkFastCanvas(WorldPoint worldPoint)`
- **Description**: interacting with the game canvas to click the tile. Useful for short distances or when the tile is on screen.

### `walkMiniMap`
- **Signature**: `public static boolean walkMiniMap(WorldPoint worldPoint)`
- **Description**: Walks to the specified world point by clicking on the minimap.

### `walkTo`
- **Signature**: `public static boolean walkTo(int x, int y, int plane)`
- **Description**: Walks to the specified coordinates.

### `walkTo`
- **Signature**: `public static boolean walkTo(WorldPoint target)`
- **Description**: Walks to the specified `WorldPoint`. Uses the shortest pathfinder and handles transports/obstacles.

### `walkTo`
- **Signature**: `public static boolean walkTo(WorldPoint target, int distance)`
- **Description**: Walks to the specified `WorldPoint`, considering the action complete when within the specified distance.
