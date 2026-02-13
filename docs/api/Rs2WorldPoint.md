# Rs2WorldPoint Class Documentation

## [Back](development.md)

## Overview
The `Rs2WorldPoint` class acts as a wrapper and utility for `WorldPoint`, providing additional functionality for pathfinding, distance calculation, and coordinate conversion, especially useful for instanced areas.

## Methods

### `convertInstancedWorldPoint`
- **Signature**: `public static WorldPoint convertInstancedWorldPoint(WorldPoint worldPoint)`
- **Description**: Converts an instanced `WorldPoint` coordinate to a global `WorldPoint`. This is useful for locating objects within instanced rooms relative to the global map.

### `distanceToPath`
- **Signature**: `public int distanceToPath(WorldPoint other)`
- **Description**: Calculates the distance to a target `WorldPoint` along a path. Returns `Integer.MAX_VALUE` if no path is found or the path does not end at the target.

### `getPlane`
- **Signature**: `public int getPlane()`
- **Description**: Gets the plane (height level) of the `Rs2WorldPoint`.

### `getWorldPoint`
- **Signature**: `public WorldPoint getWorldPoint()`
- **Description**: Retrieves the underlying `WorldPoint` object.

### `getX`
- **Signature**: `public int getX()`
- **Description**: Gets the X coordinate of the `Rs2WorldPoint`.

### `getY`
- **Signature**: `public int getY()`
- **Description**: Gets the Y coordinate of the `Rs2WorldPoint`.

### `normalizeY`
- **Signature**: `public static int normalizeY(WorldPoint point)`
- **Description**: Normalizes the Y coordinate of a `WorldPoint`. This is typically used for comparing locations in caves or dungeons where Y coordinates are shifted.

### `pathTo`
- **Signature**: `public List<WorldPoint> pathTo(WorldPoint other)`
- **Description**: Calculates a path to the specified `WorldPoint`. Returns a list of `WorldPoint`s representing the path, or `null` if no path is found.

### `pathTo`
- **Signature**: `public List<WorldPoint> pathTo(WorldPoint other, boolean fullPath)`
- **Description**: Calculates a path to the specified `WorldPoint`, optionally returning the full list of tiles (including intermediate steps) instead of just key checkpoints.

### `quickDistance`
- **Signature**: `public static int quickDistance(WorldPoint a, WorldPoint b)`
- **Description**: Calculates the Chebyshev distance between two `WorldPoint`s. This is a quick approximation of distance often used in tile-based games.

### `toLocalInstance`
- **Signature**: `public static WorldPoint toLocalInstance(WorldPoint worldPoint)`
- **Description**: Converts a global `WorldPoint` to a local instance `WorldPoint` if the player is currently in an instance.
