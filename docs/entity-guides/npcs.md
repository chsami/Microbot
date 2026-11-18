# NPCs, players and ground items on screen

## 1. Click only the part of an NPC, player or ground item that is clear of the interfaces

`Rs2Camera.isTileOnScreen` accepts a tile when any part of it is in the viewport, and in resizable mode the viewport is the whole canvas, under the chatbox, side panel and minimap. `Microbot.doInvoke` only clips the rectangle it is given to the canvas. An NPC, player or ground-item click therefore has to pass the rectangle through `Rs2UiHelper.clearClickArea` first, which keeps the part inside the viewport and clear of those interfaces (`clearOfInterfaces` with `visibleSceneOverlayBounds`). When nothing is clear it turns the camera to face the target within 5 degrees once and checks again. When the target is still covered it logs why (at most one warning every 10 seconds) and the click method returns `false` without clicking. It does not walk closer the way `Rs2TileObjectModel.click` does ([objects gotcha 2](objects.md#2-a-tile-on-screen-does-not-mean-the-object-is-clickable)), because walking up to an NPC breaks safespots and ranged fights. A target with no clickbox keeps its fallback rectangle and is sent from the resting point as before.

**Why this matters:** a Discord report described the mouse travelling onto the inventory to click NPCs and objects standing behind it. `VirtualMouse.retarget` only ran after the pointer had already moved to the covered point, kept that point when no part of the clickbox was clear, and never ran for ground items.

**Pattern to follow:**

```java
// Wrong: the whole clickbox, including the part under the inventory
Microbot.doInvoke(entry, Rs2UiHelper.getActorClickbox(npc));

// Right: the clear part only, and no click when there is none
Rectangle clickArea = Rs2UiHelper.clearActorClickArea(npc, npc.getName());
if (clickArea == null) {
    return false;
}
Microbot.doInvoke(entry, clickArea);
```

**Where this applies:** `Rs2NpcModel.click`, `Rs2Npc.interact`, the `Rs2Player` player actions (attack, trade, follow, challenge, cast, use), `Rs2TileItemModel.click` and the deprecated `util.tileobject.Rs2TileObjectModel.click`. A caller that gets `false` should retry on its next loop; the camera turn has usually cleared the target by then.

**Defensive check:** `Rs2UiHelperClearClickAreaTest` covers a clear target, a half-covered one, a covered one that the camera turn clears, one that stays covered and the fallback rectangle.
