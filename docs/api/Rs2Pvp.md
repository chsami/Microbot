# Rs2Pvp Class Documentation

## [Back](development.md)

## Overview
The `Rs2Pvp` class provides utility methods for player-vs-player (PVP) related checks, such as wilderness levels, attackability of other players, and risk calculation.

## Methods

### `calculateRisk`
- **Signature**: `public static int calculateRisk(Client client, ItemManager itemManager)`
- **Description**: Calculates the total wealth risk of the player based on inventory and equipment. Considers tradeable value and high alchemy value for untradeables.

### `getWildernessLevelFrom`
- **Signature**: `public static int getWildernessLevelFrom(WorldPoint point)`
- **Description**: Calculates the wilderness level at a specific `WorldPoint`. Handles various wilderness areas including main wilderness, God Wars Dungeon wilderness, underground areas, and specific PVP hotspots like Ferox Enclave or BH craters.

### `isAttackable`
- **Signature**: `public static boolean isAttackable()`
- **Description**: Checks if there are any players nearby who are attackable by the local player, considering wilderness levels and combat level differences.

### `isAttackable`
- **Signature**: `public static boolean isAttackable(Player player)`
- **Description**: Checks if a specific `Player` is attackable by the local player.

### `isAttackable`
- **Signature**: `public static boolean isAttackable(Rs2PlayerModel rs2Player)`
- **Description**: Checks if a specific `Rs2PlayerModel` is attackable by the local player.

### `isAttackable`
- **Signature**: `public static boolean isAttackable(Rs2PlayerModel rs2Player, boolean isDeadManworld, boolean isPvpWorld, int wildernessLevel)`
- **Description**: Checks if a specific `Rs2PlayerModel` is attackable given specific world conditions and wilderness level.

### `isInWilderness`
- **Signature**: `public static boolean isInWilderness()`
- **Description**: Checks if the local player is currently in the wilderness based on varbit values.
