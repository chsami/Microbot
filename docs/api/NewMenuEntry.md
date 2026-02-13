# NewMenuEntry Class Documentation

## [Back](development.md)

## Overview
The `NewMenuEntry` class implements the `MenuEntry` interface and serves as a builder for creating and configuring menu entries. It allows for fluent chaining of methods to set properties like option, target, identifier, type, and more.

## Constructors

### `NewMenuEntry()`
- **Description**: Creates a new, empty `NewMenuEntry`.

### `NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String target)`
- **Description**: Creates a new `NewMenuEntry` with specified parameters.

### `NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String target, int worldViewId)`
- **Description**: Creates a new `NewMenuEntry` with specified parameters including world view ID.

### `NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String target, Actor actor)`
- **Description**: Creates a new `NewMenuEntry` for an actor interaction with default option "Use".

### `NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String target, Actor actor, String option)`
- **Description**: Creates a new `NewMenuEntry` for an actor interaction with a specified option.

### `NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String option, String target, TileObject gameObject)`
- **Description**: Creates a new `NewMenuEntry` for a game object interaction.

### `NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String option, String target, TileObject gameObject, int worldViewId)`
- **Description**: Creates a new `NewMenuEntry` for a game object interaction including world view ID.

### `NewMenuEntry(String option, int param0, int param1, int opcode, int identifier, int itemId, String target)`
- **Description**: Creates a new `NewMenuEntry` with specified option and parameters.

### `NewMenuEntry(String option, String target, int identifier, MenuAction type, int param0, int param1, boolean forceLeftClick)`
- **Description**: Creates a new `NewMenuEntry` with full specification.

## Methods

### `actor`
- **Signature**: `public NewMenuEntry actor(Actor actor)`
- **Description**: Sets the actor for the menu entry.

### `findIdentifier`
- **Signature**: `public static int findIdentifier(int menuOption)`
- **Description**: Calculates the identifier for a given menu option using the default offset of +6.

### `findIdentifier`
- **Signature**: `public static int findIdentifier(int menuOption, int offset)`
- **Description**: Calculates the identifier for a given menu option using a custom offset.

### `forceLeftClick`
- **Signature**: `public NewMenuEntry forceLeftClick(boolean forceLeftClick)`
- **Description**: Sets whether the menu entry should force a left click.

### `gameObject`
- **Signature**: `public NewMenuEntry gameObject(TileObject gameObject)`
- **Description**: Sets the game object for the menu entry.

### `getActor`
- **Signature**: `public Actor getActor()`
- **Description**: Retrieves the actor associated with the menu entry.

### `getGameObject`
- **Signature**: `public TileObject getGameObject()`
- **Description**: Retrieves the game object associated with the menu entry.

### `getIdentifier`
- **Signature**: `public int getIdentifier()`
- **Description**: Gets the identifier of the menu entry.

### `getItemId`
- **Signature**: `public int getItemId()`
- **Description**: Gets the item ID associated with the menu entry.

### `getNpc`
- **Signature**: `public NPC getNpc()`
- **Description**: Retrieves the NPC if the actor is an NPC.

### `getOption`
- **Signature**: `public String getOption()`
- **Description**: Gets the option text of the menu entry.

### `getParam0`
- **Signature**: `public int getParam0()`
- **Description**: Gets the first parameter of the menu entry.

### `getParam1`
- **Signature**: `public int getParam1()`
- **Description**: Gets the second parameter of the menu entry.

### `getPlayer`
- **Signature**: `public Player getPlayer()`
- **Description**: Retrieves the Player if the actor is a Player.

### `getTarget`
- **Signature**: `public String getTarget()`
- **Description**: Gets the target text of the menu entry.

### `getType`
- **Signature**: `public MenuAction getType()`
- **Description**: Gets the menu action type.

### `getWidget`
- **Signature**: `public Widget getWidget()`
- **Description**: Retrieves the widget associated with the menu entry.

### `identifier`
- **Signature**: `public NewMenuEntry identifier(int identifier)`
- **Description**: Sets the identifier for the menu entry.

### `itemId`
- **Signature**: `public NewMenuEntry itemId(int itemId)`
- **Description**: Sets the item ID for the menu entry.

### `opcode`
- **Signature**: `public NewMenuEntry opcode(int opcode)`
- **Description**: Sets the menu action type using an opcode ID.

### `option`
- **Signature**: `public NewMenuEntry option(String option)`
- **Description**: Sets the option text for the menu entry.

### `param0`
- **Signature**: `public NewMenuEntry param0(int param0)`
- **Description**: Sets the first parameter for the menu entry.

### `param1`
- **Signature**: `public NewMenuEntry param1(int param1)`
- **Description**: Sets the second parameter for the menu entry.

### `target`
- **Signature**: `public NewMenuEntry target(String target)`
- **Description**: Sets the target text for the menu entry.

### `type`
- **Signature**: `public NewMenuEntry type(MenuAction type)`
- **Description**: Sets the menu action type.

### `widget`
- **Signature**: `public NewMenuEntry widget(Widget widget)`
- **Description**: Sets the widget for the menu entry.

### `worldViewId`
- **Signature**: `public NewMenuEntry worldViewId(int worldViewId)`
- **Description**: Sets the world view ID for the menu entry.
