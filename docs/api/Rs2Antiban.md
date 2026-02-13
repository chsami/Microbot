# Rs2Antiban Class Documentation

## [Back](development.md)

## Overview
The `Rs2Antiban` class provides a comprehensive anti-ban system that simulates human-like behavior during various in-game activities. This system includes features such as mouse fatigue, random intervals, micro-breaks, action cooldowns, and contextually aware mouse movements, all aimed at reducing the risk of detection.

## Methods

### `actionCooldown`
- **Signature**: `public static void actionCooldown()`
- **Description**: Handles the execution of an action cooldown based on anti-ban behaviors. Controls the flow for activating the cooldown either with certainty or based on a chance. Includes logic to adjust behaviors such as non-linear intervals, behavioral variability, and random mouse movements.

### `activateAntiban`
- **Signature**: `public static void activateAntiban()`
- **Description**: Activates the antiban system.

### `checkForCookingEvent`
- **Signature**: `public static boolean checkForCookingEvent(ChatMessage event)`
- **Description**: Checks if a chat message corresponds to a cooking event.

### `deactivateAntiban`
- **Signature**: `public static void deactivateAntiban()`
- **Description**: Deactivates the antiban system.

### `isIdle`
- **Signature**: `public static boolean isIdle()`
- **Description**: Checks if the player is currently idle.

### `isIdleTooLong`
- **Signature**: `public static boolean isIdleTooLong(int timeoutTicks)`
- **Description**: Checks if the player has been idle for too long based on a specified timeout in ticks.

### `isMining`
- **Signature**: `public static boolean isMining()`
- **Description**: Checks if the player is currently performing a mining animation.

### `isWoodcutting`
- **Signature**: `public static boolean isWoodcutting()`
- **Description**: Checks if the player is currently performing a woodcutting animation.

### `moveMouseOffScreen`
- **Signature**: `public static void moveMouseOffScreen()`
- **Description**: Moves the mouse off the screen with a 100% chance to trigger. Used to simulate a user taking a break.

### `moveMouseOffScreen`
- **Signature**: `public static void moveMouseOffScreen(double chance)`
- **Description**: Moves the mouse off the screen based on a specified chance percentage.

### `moveMouseRandomly`
- **Signature**: `public static void moveMouseRandomly()`
- **Description**: Moves the mouse randomly based on the settings. Used to simulate natural mouse behavior.

### `renderAntibanOverlayComponents`
- **Signature**: `public static void renderAntibanOverlayComponents(PanelComponent panelComponent)`
- **Description**: Renders an overlay component that displays various anti-ban settings and information within a panel. Populates a `PanelComponent` with details regarding the current anti-ban system's state.

### `resetAntibanSettings`
- **Signature**: `public static void resetAntibanSettings()`
- **Description**: Resets all antiban settings to their default values.

### `resetAntibanSettings`
- **Signature**: `public static void resetAntibanSettings(boolean forceReset)`
- **Description**: Resets all antiban settings, optionally forcing the reset even if overwrite settings are disabled.

### `setActivity`
- **Signature**: `public static void setActivity(@NotNull Activity activity)`
- **Description**: Sets the current activity and adjusts antiban settings based on the activity type.

### `setActivityIntensity`
- **Signature**: `public static void setActivityIntensity(ActivityIntensity activityIntensity)`
- **Description**: Sets the intensity level of the current activity.

### `takeMicroBreakByChance`
- **Signature**: `public static boolean takeMicroBreakByChance()`
- **Description**: Attempts to trigger a micro-break based on a random chance, as configured in settings. Simulates human-like pauses.
