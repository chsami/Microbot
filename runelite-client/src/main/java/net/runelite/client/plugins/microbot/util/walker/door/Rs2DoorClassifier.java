package net.runelite.client.plugins.microbot.util.walker.door;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import net.runelite.api.ObjectComposition;

/**
 * Stateless classification of door/gate objects: whether a name looks door-like, which menu
 * action to use to walk through (never a close/shut), and whether a composition is in the
 * open state (only close/shut actions). Extracted from {@code Rs2Walker} — pure string/action
 * heuristics over names and {@link ObjectComposition}, no walker state.
 */
public final class Rs2DoorClassifier {

    private static final String[] DOOR_LIKE_NAME_FRAGMENTS = {
            "door", "gate", "barrier", "stile", "portcullis", "archway", "cattlegate", "fence"
    };

    /** {@code fence} must be whole-word — substring matches {@code defence} ("fence" inside) otherwise. */
    private static final Pattern FENCE_AS_WORD = Pattern.compile("\\bfence\\b", Pattern.CASE_INSENSITIVE);

    /** Lower index = higher priority when multiple actions match (prefix, ASCII lower). */
    private static final List<String> DOOR_ACTION_PRIORITY = List.of(
            "pay-toll", "pick-lock", "walk-through", "go-through", "open", "pass", "enter",
            "push", "climb-over", "climb-through", "squeeze-through", "cross", "force", "exit"
    );

    private Rs2DoorClassifier() {
    }

    public static boolean isNullOrPlaceholderObjectName(String name) {
        if (name == null) {
            return true;
        }
        String t = name.trim();
        return t.isEmpty() || "null".equalsIgnoreCase(t);
    }

    public static int doorActionPriorityIndex(String action) {
        if (action == null) {
            return Integer.MAX_VALUE;
        }
        String al = action.toLowerCase(Locale.ROOT);
        for (int i = 0; i < DOOR_ACTION_PRIORITY.size(); i++) {
            if (al.startsWith(DOOR_ACTION_PRIORITY.get(i))) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /** Walker must never choose menu actions that close an open door/gate. */
    public static boolean isDoorCloseOrShutAction(String action) {
        if (action == null) {
            return false;
        }
        String al = action.toLowerCase(Locale.ROOT).trim();
        return al.startsWith("close") || al.startsWith("shut");
    }

    /** True when every non-null action is Close/Shut (typical open-door state). */
    public static boolean doorCompositionSpecifiesOnlyCloseOrShut(ObjectComposition comp) {
        if (comp == null || comp.getActions() == null) {
            return false;
        }
        boolean sawNonNull = false;
        for (String a : comp.getActions()) {
            if (a == null) {
                continue;
            }
            sawNonNull = true;
            if (!isDoorCloseOrShutAction(a)) {
                return false;
            }
        }
        return sawNonNull;
    }

    /**
     * Best door action for walking through, excluding close/shut. {@code null} if none
     * (empty defs or only close/shut).
     */
    public static String pickWalkDoorAction(ObjectComposition comp) {
        if (comp == null || comp.getActions() == null) {
            return null;
        }
        return Arrays.stream(comp.getActions())
                .filter(Objects::nonNull)
                .filter(a -> !isDoorCloseOrShutAction(a))
                .min(Comparator.comparingInt(Rs2DoorClassifier::doorActionPriorityIndex))
                .orElse(null);
    }

    public static boolean isDoorLikeGameObjectName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        for (String f : DOOR_LIKE_NAME_FRAGMENTS) {
            if ("fence".equals(f)) {
                if (FENCE_AS_WORD.matcher(n).find()) {
                    return true;
                }
            } else if (n.contains(f)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a (real, non-impostor) composition exposes one of {@code doorActions}. */
    public static boolean isDoorComposition(ObjectComposition comp, List<String> doorActions) {
        if (comp == null || comp.getImpostorIds() != null || isNullOrPlaceholderObjectName(comp.getName()) || comp.getActions() == null) {
            return false;
        }
        return getDoorAction(comp, doorActions) != null;
    }

    /** The highest-priority matching {@code doorActions} entry the composition exposes, or null. */
    public static String getDoorAction(ObjectComposition comp, List<String> doorActions) {
        if (comp == null || comp.getActions() == null) {
            return null;
        }
        return Arrays.stream(comp.getActions())
                .filter(Objects::nonNull)
                .filter(act -> doorActions.stream().anyMatch(dact -> act.toLowerCase().startsWith(dact.toLowerCase())))
                .min(Comparator.comparing(act -> doorActions.indexOf(doorActions.stream()
                        .filter(dact -> act.toLowerCase().startsWith(dact.toLowerCase()))
                        .findFirst()
                        .orElse(""))))
                .orElse(null);
    }
}
