# Rs2Widget Class Documentation

## [Back](development.md)

## Overview
The `Rs2Widget` class provides a comprehensive set of methods for finding, checking, and interacting with widgets in the game interface. It supports finding widgets by text, ID, or structure, and interacting with them.

## Methods

### `calculateTextSimilarity`
- **Signature**: `public static double calculateTextSimilarity(String source, String target)`
- **Description**: Calculates text similarity score between two strings (0.0 to 1.0).

### `checkBoundsOverlapWidgetInMainModal`
- **Signature**: `public static boolean checkBoundsOverlapWidgetInMainModal(Rectangle overlayBoundsCanvas, int viewportXOffset, int viewportYOffset)`
- **Description**: Checks if a given rectangle overlaps with any widget in the main modal interface.

### `clickChildWidget`
- **Signature**: `public static boolean clickChildWidget(int id, int childId)`
- **Description**: Clicks on a child widget specified by the parent ID and child index.

### `clickWidget`
- **Signature**: `public static boolean clickWidget(String text)`
- **Description**: Clicks a widget containing the specified text (partial match).

### `clickWidget`
- **Signature**: `public static boolean clickWidget(String text, boolean exact)`
- **Description**: Clicks a widget containing the specified text, with an option for exact matching.

### `clickWidget`
- **Signature**: `public static boolean clickWidget(String text, Optional<Integer> widgetId, int childId, boolean exact)`
- **Description**: Clicks a widget matching text criteria, optionally scoped to a specific parent widget ID.

### `clickWidget`
- **Signature**: `public static boolean clickWidget(Widget widget)`
- **Description**: Clicks the specified widget instance.

### `clickWidget`
- **Signature**: `public static boolean clickWidget(int id)`
- **Description**: Clicks a widget specified by its ID.

### `clickWidget`
- **Signature**: `public static boolean clickWidget(int parentId, int childId)`
- **Description**: Clicks a widget specified by its parent ID and child index.

### `clickWidgetFast`
- **Signature**: `public static void clickWidgetFast(Widget widget)`
- **Description**: Performs a fast click on a widget using menu entry invocation.

### `clickWidgetFast`
- **Signature**: `public static void clickWidgetFast(Widget widget, int param0)`
- **Description**: Performs a fast click on a widget with a specified parameter.

### `clickWidgetFast`
- **Signature**: `public static void clickWidgetFast(Widget widget, int param0, int identifier)`
- **Description**: Performs a fast click on a widget with specified parameters and identifier.

### `clickWidgetFast`
- **Signature**: `public static void clickWidgetFast(int packetId, int identifier)`
- **Description**: Performs a fast click on a widget specified by its packet ID.

### `enableQuantityOption`
- **Signature**: `public static boolean enableQuantityOption(String quantity)`
- **Description**: Enables a specific quantity option (e.g., "All") in processing interfaces.

### `enterWilderness`
- **Signature**: `public static boolean enterWilderness()`
- **Description**: Detects the wilderness warning interface and clicks the enter button.

### `findBestMatchingWidget`
- **Signature**: `public static Widget findBestMatchingWidget(int widgetId, String targetText)`
- **Description**: Finds the best matching widget within a parent based on exact match, contains match, or word similarity.

### `findSimilarWidgets`
- **Signature**: `public static List<Widget> findSimilarWidgets(List<Widget> widgets, String targetText, double threshold)`
- **Description**: Finds all widgets that match above a text similarity threshold.

### `findWidget`
- **Signature**: `public static Widget findWidget(String text)`
- **Description**: Finds a widget containing the specified text (partial match).

### `findWidget`
- **Signature**: `public static Widget findWidget(String text, boolean exact)`
- **Description**: Finds a widget containing the specified text with matching option.

### `findWidget`
- **Signature**: `public static Widget findWidget(String text, List<Widget> children)`
- **Description**: Finds a widget containing the specified text within a list of children (partial match).

### `findWidget`
- **Signature**: `public static Widget findWidget(String text, List<Widget> children, boolean exact)`
- **Description**: Searches for a widget with matching text, either in the provided children or across all root widgets.

### `findWidget`
- **Signature**: `public static Widget findWidget(int spriteId, List<Widget> children)`
- **Description**: Searches for a widget with the specified sprite ID among root widgets or specified children.

### `findWidgetsWithAction`
- **Signature**: `public static Map<Widget, String> findWidgetsWithAction(String actionText, int widgetGroupId, boolean clickWidget)`
- **Description**: Finds widgets with specific action text in a group.

### `findWidgetsWithAction`
- **Signature**: `public static Map<Widget, String> findWidgetsWithAction(String actionText, int widgetGroupId, int widgetSubGroupId, boolean clickWidget)`
- **Description**: Finds all widgets with specific action text in a subgroup.

### `getChildWidgetSpriteID`
- **Signature**: `public static int getChildWidgetSpriteID(int id, int childId)`
- **Description**: Retrieves the sprite ID of a child widget.

### `getChildWidgetText`
- **Signature**: `public static String getChildWidgetText(int id, int childId)`
- **Description**: Retrieves the text of a child widget.

### `getWidget`
- **Signature**: `public static Widget getWidget(int id)`
- **Description**: Retrieves a widget by its ID.

### `getWidget`
- **Signature**: `public static Widget getWidget(int id, int child)`
- **Description**: Retrieves a widget by parent ID and child index.

### `getWidgetsKeyMap`
- **Signature**: `public static Map<Integer, Integer> getWidgetsKeyMap(int widgetGroupId, int widgetSubGroupId)`
- **Description**: Gets keyboard shortcut keys for widgets in processing interfaces.

### `handleProcessConfirmation`
- **Signature**: `public static boolean handleProcessConfirmation(int widgetGroupId)`
- **Description**: Handles chat/trade dialogue confirmations.

### `handleProcessingInterface`
- **Signature**: `public static boolean handleProcessingInterface(String actionText)`
- **Description**: Handles generic processing interface interactions with quantity selection.

### `hasVisibleWidgetText`
- **Signature**: `public static boolean hasVisibleWidgetText(String text)`
- **Description**: Checks if specific widget text exists and is visible.

### `hasWidget`
- **Signature**: `public static boolean hasWidget(String text)`
- **Description**: Checks if a widget containing the specified text exists.

### `hasWidgetText`
- **Signature**: `public static boolean hasWidgetText(String text, int componentId, boolean exact)`
- **Description**: Checks if a root widget or its descendants contain the specified text.

### `hasWidgetText`
- **Signature**: `public static boolean hasWidgetText(String text, int widgetId, int childId, boolean exact)`
- **Description**: Checks if a specific widget or its descendants contain the specified text.

### `isDepositBoxWidgetOpen`
- **Signature**: `public static boolean isDepositBoxWidgetOpen()`
- **Description**: Checks if the deposit box widget is open.

### `isGoldCraftingWidgetOpen`
- **Signature**: `public static boolean isGoldCraftingWidgetOpen()`
- **Description**: Checks if the gold crafting widget is open.

### `isHidden`
- **Signature**: `public static boolean isHidden(int id)`
- **Description**: Checks if a widget is hidden by ID.

### `isHidden`
- **Signature**: `public static boolean isHidden(int parentId, int childId)`
- **Description**: Checks if a child widget is hidden.

### `isProductionWidgetOpen`
- **Signature**: `public static boolean isProductionWidgetOpen()`
- **Description**: Checks if the production widget is open.

### `isSilverCraftingWidgetOpen`
- **Signature**: `public static boolean isSilverCraftingWidgetOpen()`
- **Description**: Checks if the silver crafting widget is open.

### `isSmithingWidgetOpen`
- **Signature**: `public static boolean isSmithingWidgetOpen()`
- **Description**: Checks if the smithing widget is open.

### `isWidgetVisible`
- **Signature**: `public static boolean isWidgetVisible(int id)`
- **Description**: Checks if a widget is visible by ID.

### `isWidgetVisible`
- **Signature**: `public static boolean isWidgetVisible(int widgetId, int childId)`
- **Description**: Checks if a child widget is visible.

### `isWildernessInterfaceOpen`
- **Signature**: `public static boolean isWildernessInterfaceOpen()`
- **Description**: Checks if the wilderness warning interface is open.

### `searchChildren`
- **Signature**: `public static Widget searchChildren(String text, Widget child, boolean exact)`
- **Description**: Recursively searches through child widgets for a text match.

### `searchChildren`
- **Signature**: `public static Widget searchChildren(int spriteId, Widget child)`
- **Description**: Recursively searches through child widgets for a sprite ID match.

### `sleepUntilHasNotWidgetText`
- **Signature**: `public static boolean sleepUntilHasNotWidgetText(String text, int widgetId, int childId, boolean exact, int sleep)`
- **Description**: Waits until a widget does NOT contain the specified text.

### `sleepUntilHasWidget`
- **Signature**: `public static boolean sleepUntilHasWidget(String text)`
- **Description**: Waits until a widget containing the specified text appears.

### `sleepUntilHasWidgetText`
- **Signature**: `public static boolean sleepUntilHasWidgetText(String text, int widgetId, int childId, boolean exact, int sleep)`
- **Description**: Waits until a widget contains the specified text.

### `waitForWidget`
- **Signature**: `public static boolean waitForWidget(String text, int timeout)`
- **Description**: Waits for a widget to appear with specified text.
