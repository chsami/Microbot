# Rs2Settings Class Documentation

## [Back](development.md)

## Overview
The `Rs2Settings` class provides methods to interact with and toggle various in-game settings, such as shift-dropping, hiding roofs, notifications, and audio settings.

## Methods

### `disableLevelUpNotifications`
- **Signature**: `public static boolean disableLevelUpNotifications()`
- **Description**: Disables level-up notifications in the settings. Convenience method that also closes the interface.

### `disableLevelUpNotifications`
- **Signature**: `public static boolean disableLevelUpNotifications(boolean closeInterface)`
- **Description**: Disables level-up notifications in the settings, with an option to keep the interface open.

### `disableWorldSwitcherConfirmation`
- **Signature**: `public static boolean disableWorldSwitcherConfirmation()`
- **Description**: Disables the confirmation dialog when switching worlds. Convenience method that also closes the interface.

### `disableWorldSwitcherConfirmation`
- **Signature**: `public static boolean disableWorldSwitcherConfirmation(boolean closeInterface)`
- **Description**: Disables the confirmation dialog when switching worlds, with an option to keep the interface open.

### `enableBankSlotLocking`
- **Signature**: `public static boolean enableBankSlotLocking()`
- **Description**: Enables bank slot locking if currently disabled. Navigates bank settings to toggle the feature.

### `enableDropShiftSetting`
- **Signature**: `public static boolean enableDropShiftSetting()`
- **Description**: Enables shift-click dropping in the settings. Convenience method that also closes the interface.

### `enableDropShiftSetting`
- **Signature**: `public static boolean enableDropShiftSetting(boolean closeInterface)`
- **Description**: Enables shift-click dropping in the settings, with an option to keep the interface open.

### `enableSpellFiltering`
- **Signature**: `public static void enableSpellFiltering()`
- **Description**: Enables spell filtering in the magic spellbook if it is currently disabled.

### `getMinimumItemValueAlchemyWarning`
- **Signature**: `public static int getMinimumItemValueAlchemyWarning()`
- **Description**: Retrieves the minimum item value that triggers a high alchemy warning.

### `hideRoofs`
- **Signature**: `public static boolean hideRoofs()`
- **Description**: Hides roofs in the settings. Convenience method that also closes the interface.

### `hideRoofs`
- **Signature**: `public static boolean hideRoofs(boolean closeInterface)`
- **Description**: Hides roofs in the settings, with an option to keep the interface open.

### `isBankSlotLockingEnabled`
- **Signature**: `public static boolean isBankSlotLockingEnabled()`
- **Description**: Checks if bank slot locking is enabled.

### `isDropShiftSettingEnabled`
- **Signature**: `public static boolean isDropShiftSettingEnabled()`
- **Description**: Checks if shift-click dropping is enabled.

### `isEscCloseInterfaceSettingEnabled`
- **Signature**: `public static boolean isEscCloseInterfaceSettingEnabled()`
- **Description**: Checks if the "Esc closes interface" setting is enabled.

### `isHideRoofsEnabled`
- **Signature**: `public static boolean isHideRoofsEnabled()`
- **Description**: Checks if roofs are currently hidden.

### `isLevelUpNotificationsEnabled`
- **Signature**: `public static boolean isLevelUpNotificationsEnabled()`
- **Description**: Checks if level-up notifications are enabled.

### `isSpellFilteringEnabled`
- **Signature**: `public static boolean isSpellFilteringEnabled()`
- **Description**: Checks if spell filtering is enabled in the magic spellbook.

### `isWorldSwitcherConfirmationEnabled`
- **Signature**: `public static boolean isWorldSwitcherConfirmationEnabled()`
- **Description**: Checks if the world switcher confirmation is enabled.

### `openSettings`
- **Signature**: `public static boolean openSettings()`
- **Description**: Opens the main settings interface.

### `turnOffMusic`
- **Signature**: `public static void turnOffMusic()`
- **Description**: Turns off music, sound effects, and area sounds in the settings tab.
