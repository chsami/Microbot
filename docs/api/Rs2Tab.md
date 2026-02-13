# Rs2Tab Class Documentation

## [Back](development.md)

## Overview
The `Rs2Tab` class manages interface tabs in the game, such as Inventory, Combat, Skills, and others. It provides methods to switch between tabs and check the currently active tab.

## Methods

### `getCurrentTab`
- **Signature**: `public static InterfaceTab getCurrentTab()`
- **Description**: Retrieves the currently open interface tab based on the `TOPLEVEL_PANEL` client variable.

### `getSpellBookTab`
- **Signature**: `public static Widget getSpellBookTab()`
- **Description**: Retrieves the widget corresponding to the spellbook tab, accounting for different game layouts (resizable, fixed).

### `isCurrentTab`
- **Signature**: `public static boolean isCurrentTab(InterfaceTab tab)`
- **Description**: Checks if the specified tab is currently selected.

### `switchTo`
- **Signature**: `public static boolean switchTo(InterfaceTab tab)`
- **Description**: Switches to the specified interface tab using hotkeys. Returns `true` if the switch was successful or the tab was already active.

### `switchToAccountManagementTab`
- **Signature**: `public static boolean switchToAccountManagementTab()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.ACC_MAN)`.

### `switchToCombatOptionsTab`
- **Signature**: `public static boolean switchToCombatOptionsTab()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.COMBAT)`.

### `switchToEmotesTab`
- **Signature**: `public static boolean switchToEmotesTab()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.EMOTES)`.

### `switchToEquipmentTab`
- **Signature**: `public static boolean switchToEquipmentTab()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.EQUIPMENT)`.

### `switchToFriendsTab`
- **Signature**: `public static boolean switchToFriendsTab()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.FRIENDS)`.

### `switchToGroupingTab`
- **Signature**: `public static boolean switchToGroupingTab()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.CHAT)`.

### `switchToInventoryTab`
- **Signature**: `public static boolean switchToInventoryTab()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.INVENTORY)`.

### `switchToLogout`
- **Signature**: `public static boolean switchToLogout()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.LOGOUT)`.

### `switchToMagicTab`
- **Signature**: `public static boolean switchToMagicTab()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.MAGIC)`.

### `switchToMusicTab`
- **Signature**: `public static boolean switchToMusicTab()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.MUSIC)`.

### `switchToPrayerTab`
- **Signature**: `public static boolean switchToPrayerTab()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.PRAYER)`.

### `switchToQuestTab`
- **Signature**: `public static boolean switchToQuestTab()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.QUESTS)`.

### `switchToSettingsTab`
- **Signature**: `public static boolean switchToSettingsTab()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.SETTINGS)`.

### `switchToSkillsTab`
- **Signature**: `public static boolean switchToSkillsTab()`
- **Description**: Deprecated. Use `switchTo(InterfaceTab.SKILLS)`.
