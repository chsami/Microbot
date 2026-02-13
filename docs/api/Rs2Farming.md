# Rs2Farming Class Documentation

## [Back](development.md)

## Overview
The `Rs2Farming` class provides utility methods for farming operations, including retrieving farming patches, checking patch states, and managing farming-related tasks.

## Methods

### `getClosestPatch`
- **Signature**: `public static Optional<FarmingPatch> getClosestPatch(List<FarmingPatch> patches)`
- **Description**: Finds the closest patch to the player's current location from a list of patches.

### `getEmptyPatches`
- **Signature**: `public static List<FarmingPatch> getEmptyPatches(List<FarmingPatch> patches)`
- **Description**: Gets empty patches ready for planting from a list of patches.

### `getFruitTreePatches`
- **Signature**: `public static List<FarmingPatch> getFruitTreePatches()`
- **Description**: Get all fruit tree farming patches.

### `getHarvestablePatches`
- **Signature**: `public static List<FarmingPatch> getHarvestablePatches(List<FarmingPatch> patches)`
- **Description**: Gets patches that are healthy and ready for harvest from a list of patches.

### `getHerbPatches`
- **Signature**: `public static List<FarmingPatch> getHerbPatches()`
- **Description**: Get all herb farming patches.

### `getPatchByRegionAndVarbit`
- **Signature**: `public static Optional<FarmingPatch> getPatchByRegionAndVarbit(String regionName, int varbit)`
- **Description**: Get a patch by its region name and varbit for unique identification.

### `getPatchesByTab`
- **Signature**: `public static List<FarmingPatch> getPatchesByTab(Tab tab)`
- **Description**: Get all farming patches of a specific type (tab).

### `getPatchesNeedingAttention`
- **Signature**: `public static List<FarmingPatch> getPatchesNeedingAttention(List<FarmingPatch> patches)`
- **Description**: Gets patches that need attention (diseased or dead) from a list of patches.

### `getPatchesWithinDistance`
- **Signature**: `public static List<FarmingPatch> getPatchesWithinDistance(List<FarmingPatch> patches, WorldPoint location, int maxDistance)`
- **Description**: Get patches within a certain distance of a location.

### `getReadyPatches`
- **Signature**: `public static List<FarmingPatch> getReadyPatches(List<FarmingPatch> patches)`
- **Description**: Get patches that are ready for action (not growing). This includes harvestable, diseased, dead, or empty patches.

### `getSpiritTreePatches`
- **Signature**: `public static List<FarmingPatch> getSpiritTreePatches()`
- **Description**: Get all spirit tree farming patches.

### `getTreePatches`
- **Signature**: `public static List<FarmingPatch> getTreePatches()`
- **Description**: Get all tree farming patches (regular trees).

### `hasQuestRequirement`
- **Signature**: `public static boolean hasQuestRequirement(QuestState questState)`
- **Description**: Check if player has completed required quests for farming.

### `hasRequiredFarmingLevel`
- **Signature**: `public static boolean hasRequiredFarmingLevel(int requiredLevel)`
- **Description**: Check if player has the required farming level for a specific patch type.

### `isFarmingSystemReady`
- **Signature**: `public static boolean isFarmingSystemReady()`
- **Description**: Check if farming tracking is properly initialized.

### `isPatchAccessible`
- **Signature**: `public static boolean isPatchAccessible(FarmingPatch patch, int maxDistance)`
- **Description**: Check if a patch is accessible (player is within range).

### `logPatchStates`
- **Signature**: `public static void logPatchStates(List<FarmingPatch> patches)`
- **Description**: Log farming patch states for debugging.

### `predictPatchState`
- **Signature**: `public static CropState predictPatchState(FarmingPatch patch)`
- **Description**: Predict the state of a farming patch based on time tracking data.
