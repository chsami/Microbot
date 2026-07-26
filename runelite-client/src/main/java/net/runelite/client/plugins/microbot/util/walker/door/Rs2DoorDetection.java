package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;

/**
 * Object-level door detection: resolve an object's (impostor-aware) composition and decide whether
 * it is a walk-through-able door/gate. Stateless — combines {@link Rs2DoorClassifier} name/action
 * heuristics with a live composition lookup. Extracted from {@code Rs2Walker}.
 */
public final class Rs2DoorDetection {

    private Rs2DoorDetection() {
    }

    /** Resolves an object's composition, following impostor transforms (varbit/varp swaps). */
    public static ObjectComposition resolveCompositionForDoorProbe(TileObject object) {
        ObjectComposition comp = Rs2GameObject.convertToObjectComposition(object);
        if (comp == null) {
            return null;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ObjectComposition c = comp;
            for (int depth = 0; depth < 4 && c != null && c.getImpostorIds() != null; depth++) {
                c = c.getImpostor();
            }
            return c;
        }).orElse(comp);
    }

    /** Whether {@code object} is a door/gate the walker can pass through (not an open/close-only state). */
    public static boolean isDoorLikeSceneObject(TileObject object) {
        if (object == null || object.getWorldLocation() == null) {
            return false;
        }
        ObjectComposition comp = resolveCompositionForDoorProbe(object);
        if (comp == null
                || Rs2DoorClassifier.isNullOrPlaceholderObjectName(comp.getName())
                || Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(comp)) {
            return false;
        }
        String action = Rs2DoorClassifier.pickWalkDoorAction(comp);
        return Rs2DoorClassifier.isDoorLikeGameObjectName(comp.getName())
                || (action != null && Rs2DoorClassifier.doorActionPriorityIndex(action) < Integer.MAX_VALUE);
    }
}
