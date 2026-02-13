# Mouse Class Documentation

## [Back](development.md)

## Overview
The `Mouse` class defines the abstract interface for mouse interactions. It provides methods for clicking, moving, scrolling, and dragging the mouse.

## Methods

### `click`
- **Signature**: `public abstract Mouse click()`
- **Description**: Performs a left click at the current mouse position.

### `click`
- **Signature**: `public abstract Mouse click(int x, int y)`
- **Description**: Clicks at the specified X and Y coordinates.

### `click`
- **Signature**: `public abstract Mouse click(int x, int y, boolean rightClick)`
- **Description**: Clicks at the specified X and Y coordinates, optionally using a right click.

### `click`
- **Signature**: `public abstract Mouse click(Point point)`
- **Description**: Clicks at the specified `Point`.

### `click`
- **Signature**: `public abstract Mouse click(Point point, boolean rightClick)`
- **Description**: Clicks at the specified `Point`, optionally using a right click.

### `click`
- **Signature**: `public abstract Mouse click(Point point, NewMenuEntry entry)`
- **Description**: Clicks at the specified `Point` associated with a `NewMenuEntry`.

### `click`
- **Signature**: `public abstract Mouse click(Rectangle rectangle)`
- **Description**: Clicks at a random point within the specified `Rectangle`.

### `click`
- **Signature**: `public abstract Mouse click(double x, double y)`
- **Description**: Clicks at the specified double coordinates.

### `drag`
- **Signature**: `public abstract Mouse drag(Point startPoint, Point endPoint)`
- **Description**: Drags the mouse from the start point to the end point.

### `getCanvas`
- **Signature**: `public Canvas getCanvas()`
- **Description**: Retrieves the game canvas.

### `getMousePosition`
- **Signature**: `public abstract java.awt.Point getMousePosition()`
- **Description**: Gets the current position of the mouse.

### `getRainbowColor`
- **Signature**: `public Color getRainbowColor()`
- **Description**: Generates a rainbow color cycling through hues.

### `move`
- **Signature**: `public abstract Mouse move(int x, int y)`
- **Description**: Moves the mouse to the specified X and Y coordinates.

### `move`
- **Signature**: `public abstract Mouse move(Point point)`
- **Description**: Moves the mouse to the specified `Point`.

### `move`
- **Signature**: `public abstract Mouse move(Rectangle rect)`
- **Description**: Moves the mouse to a random point within the specified `Rectangle`.

### `move`
- **Signature**: `public abstract Mouse move(double x, double y)`
- **Description**: Moves the mouse to the specified double coordinates.

### `move`
- **Signature**: `public abstract Mouse move(Polygon polygon)`
- **Description**: Moves the mouse to a point within the specified `Polygon`.

### `randomizeClick`
- **Signature**: `public int randomizeClick()`
- **Description**: Generates a random offset for clicking.

### `scrollDown`
- **Signature**: `public abstract Mouse scrollDown(Point point)`
- **Description**: Scrolls down at the specified `Point`.

### `scrollUp`
- **Signature**: `public abstract Mouse scrollUp(Point point)`
- **Description**: Scrolls up at the specified `Point`.

### `setLastClick`
- **Signature**: `public abstract void setLastClick(Point point)`
- **Description**: Sets the last click position.

### `setLastMove`
- **Signature**: `public abstract void setLastMove(Point point)`
- **Description**: Sets the last move position.
