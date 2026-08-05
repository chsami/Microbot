package net.runelite.client.plugins.microbot.util.walker.obstacle;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;

/**
 * Dynamic path obstacles the pathfinder cannot model in the static collision map (Motherlode Mine
 * rockfalls today; more to follow). Decomposed out of {@code Rs2Walker}.
 * <p>
 * The rockfall handler returns a {@link RockfallResult} instead of mutating walker state directly:
 * the "no pickaxe" abort used to call {@code Rs2Walker.setTarget(null)} from inside here, which
 * coupled this obstacle logic to the walker's route state. Keeping the mutation in the facade
 * (which maps {@link RockfallResult#NO_PICKAXE} to clearing the target) lets this class stay a pure
 * obstacle handler.
 */
@Slf4j
public final class Rs2ObstacleHandler {

    /** Motherlode Mine — the only region containing {@code MOTHERLODE_ROCKFALL_1/2} obstacles. */
    public static final int MOTHERLODE_MINE_REGION = 14936;

    /** Outcome of a rockfall handling attempt; the walker applies any route-state change. */
    public enum RockfallResult {
        /** No rockfall in the way (or not in the mine) — nothing done. */
        NOT_APPLICABLE,
        /** A rockfall was mined and cleared. */
        MINED,
        /** A rockfall blocks the way but no pickaxe is available — the walk should be abandoned. */
        NO_PICKAXE
    }

    private Rs2ObstacleHandler() {
    }

    /**
     * Whether a rockfall could plausibly sit at {@code path[index]} or {@code path[index + 1]}, used
     * to gate the (comparatively expensive) scene lookup in {@link #handleRockfall}. Rockfalls only
     * exist in the Motherlode Mine, so the region check is legitimate — it tests where the rockfall
     * could <em>be</em>: the player's tile, or the path tiles about to be traversed.
     */
    public static boolean isMotherlodeRockfallCandidate(WorldPoint playerLoc, List<WorldPoint> path, int index) {
        if (path == null || path.isEmpty() || index < 0 || index >= path.size()) {
            return false;
        }
        if (playerLoc != null && playerLoc.getRegionID() == MOTHERLODE_MINE_REGION) {
            return true;
        }
        int lastCandidate = Math.min(index + 1, path.size() - 1);
        for (int i = index; i <= lastCandidate; i++) {
            WorldPoint candidate = path.get(i);
            if (candidate != null && candidate.getRegionID() == MOTHERLODE_MINE_REGION) {
                return true;
            }
        }
        return false;
    }

    /** Mines a Motherlode rockfall blocking {@code path[index]}/{@code path[index + 1]} if present. */
    public static RockfallResult handleRockfall(List<WorldPoint> path, int index) {
        if (path == null || path.isEmpty() || index < 0 || index >= path.size()) {
            return RockfallResult.NOT_APPLICABLE;
        }
        if (!Rs2PathApi.getActiveRouteStatus().isPresent()) return RockfallResult.NOT_APPLICABLE;

        if (index == path.size() - 1) return RockfallResult.NOT_APPLICABLE;

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null) return RockfallResult.NOT_APPLICABLE;

        // In an instance the region IDs are the instance template's, not MOTHERLODE_MINE_REGION, so the
        // region gates below would reject every tile. Skip the region checks while instanced and rely on
        // the rockfall object-id check to stay correct; outside instances keep the MLM region restriction.
        final boolean inInstance = Microbot.getClient().getTopLevelWorldView().isInstance();

        final int lastCandidate = Math.min(index + 1, path.size() - 1);
        if (!inInstance && !isMotherlodeRockfallCandidate(playerLoc, path, index)) return RockfallResult.NOT_APPLICABLE;

        // Check current index & next index for rockfall
        for (int rockIndex = index; rockIndex <= lastCandidate; rockIndex++) {
            var point = path.get(rockIndex);
            if (point == null || (!inInstance && point.getRegionID() != MOTHERLODE_MINE_REGION)) continue;

            TileObject object = null;
            var tile = Rs2GameObject.getTiles(3).stream()
                    .filter(x -> x.getWorldLocation().equals(point))
                    .findFirst().orElse(null);

            if (tile != null)
                object = Rs2GameObject.getGameObject(point);

            if (object == null) continue;

            if (object.getId() == ObjectID.MOTHERLODE_ROCKFALL_1 || object.getId() == ObjectID.MOTHERLODE_ROCKFALL_2) {
                // Only abandon the walk once a rockfall is actually in the way. Checking for a
                // pickaxe up front killed any route merely passing through the mine.
                if (!Rs2Inventory.hasItem("pickaxe") && !Rs2Equipment.isWearing("pickaxe")) {
                    log.error("Unable to find pickaxe to mine rockfall at {}", point);
                    return RockfallResult.NO_PICKAXE;
                }
                Rs2GameObject.interact(object, "mine");
                return Global.sleepUntil(() -> Rs2GameObject.getGameObject(point) == null)
                        ? RockfallResult.MINED : RockfallResult.NOT_APPLICABLE;
            }
        }

        return RockfallResult.NOT_APPLICABLE;
    }
    // handleRockfallInRawSegment removed (P2): the segment scan now lives in Rs2Walker.resolveRockfallOnSegment,
    // which dispatches this per-edge handleRockfall through the unified MineableResolver.
}
