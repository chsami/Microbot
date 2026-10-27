# Object Gotchas

## 1. Ask a tile object for an action it actually has

`Rs2TileObjectModel.click(action)` and `query().interact(action)` refuse an action the object does not have: they return `false` and send nothing. `click()` (or a null or blank action) sends the first option, and with an item or spell selected any action uses it on the object. The deprecated `Rs2GameObject.interact` / `clickObject` still sends the first option for a missing action and logs a warning, so code that worked there can stop working when migrated.

**Why this matters:** both click paths used to send the first option whenever the action was missing, and still returned `true`. Asking a ground-floor Lumbridge staircase for `Bottom-floor` sent `Climb-up`. Dozens of callers depend on that by accident: walker transport rows say `Jump to` where the object has `Jump-to`; bank chests offer `Use`, not `Bank`; the Moons of Peril supply crate offers `Take-from`, not `Take from`. A clue step that tried `Search` then `Investigate` never got past `Search`, because the wrong option "succeeded".

**Pattern to follow:**

```java
// Wrong: the action is a guess, and a bank chest only offers Use / Collect
bank.click("Bank");

// Right: resolve the action from the object, then click it
String action = bank.hasAction("Bank") ? "Bank" : "Use";
bank.click(action);
```

Check names against the game cache (or `observe --nearby` in the CLI) before hardcoding them. Case and colour tags don't matter, hyphens and spaces do.

**Where this applies:** `Rs2TileObjectModel.click`, every `Rs2TileObjectQueryable.interact` overload, and any migration from `Rs2GameObject` to the query API (Rs2GameObject retirement M3/M4).

**Defensive check:** `Rs2TileObjectModelClickActionTest` covers refusal, first option, colour tags, impostors and the selected-widget path.

## 2. A tile on screen does not mean the object is clickable

`Rs2Camera.isTileOnScreen` accepts a tile when any part of it is in the viewport, and in resizable mode the viewport is the whole canvas, under the chatbox, side panel and minimap. `Rs2TileObjectModel.click` therefore clicks only the part of the clickbox that is inside the viewport and clear of those interfaces (`Rs2UiHelper.clearOfInterfaces` with `visibleSceneOverlayBounds`). When nothing is clear it turns the camera to the object once, and if the object is still covered it returns `false` without clicking. After the NaturalMouse travel, `VirtualMouse.retarget` picks a new point from the same clear area (for objects and actors), so a moving player or camera cannot push the click back onto an interface. A clear area that reaches the canvas top-left corner is moved in by 2 px, because a rectangle at (0,0) or (1,1) is read as the fallback rectangle and clicked at the resting point.

**Why this matters:** the walker clicked the Lumbridge castle stairs and the Edgeville trapdoor at range with a random point in the whole clickbox, which often landed on the chatbox Report button.

**Where this applies:** every `Rs2TileObjectModel.click` caller, including `Rs2GameObject` and the walker's route objects. Scene tile clicks use the same interface list through `Rs2WalkerMovement`.

**Defensive check:** `Rs2UiHelperInterfaceClearanceTest`, `Rs2TileObjectModelInterfaceCoverTest` and `VirtualMouseRetargetTest`.
