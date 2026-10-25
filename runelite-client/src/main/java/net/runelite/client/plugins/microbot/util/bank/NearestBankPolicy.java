package net.runelite.client.plugins.microbot.util.bank;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteResult;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteStep;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Pure selection rules for "nearest bank". The pathfinder prices minigame teleports (Soul Wars, Clan Wars, ...)
 * as nearly free, so a bank behind one can win a multi-target search from anywhere in the world. These
 * rules keep such banks out of the target set and reject results that are far worse than the nearest bank
 * by tiles, while leaving genuine teleports (glory, ring of dueling, spells) usable.
 */
final class NearestBankPolicy {
    /** A minigame-only bank is a candidate only when the player is already this close to it. */
    static final int MINIGAME_BANK_MAX_TILES = 40;
    /** Reject a non-teleport route to a bank this many times farther (in tiles) than the nearest bank. */
    static final int MAX_DISTANCE_RATIO = 3;
    /** The nearest-bank distance is floored to this so tiny distances do not make the ratio check noisy. */
    static final int MIN_NEAREST_FOR_RATIO = 10;

    private static final Set<BankLocation> MINIGAME_BANKS = EnumSet.of(
            BankLocation.ISLE_OF_SOULS,
            BankLocation.CLAN_HALL,
            BankLocation.HALLOWED_SEPULCHRE,
            BankLocation.GUARDIANS_OF_THE_RIFT);

    /** Re-seed the search from standable tiles within this radius when the start tile is enclosed. */
    static final int SEED_RADIUS = 3;
    static final int MAX_SEEDS = 3;
    /** Back off re-running a failed search from the same tile for this long. */
    static final long BACKOFF_MS = 45_000L;
    /** Straight-line fallback only considers banks within this many tiles. */
    static final int FALLBACK_MAX_TILES = 150;

    private NearestBankPolicy() {
    }

    /** Standable tiles around {@code start} (nearest ring first, start excluded), at most {@code limit}. */
    static List<WorldPoint> seedTiles(WorldPoint start, Predicate<WorldPoint> standable, int maxRadius, int limit) {
        List<WorldPoint> seeds = new ArrayList<>();
        if (start == null) {
            return seeds;
        }
        for (int r = 1; r <= maxRadius && seeds.size() < limit; r++) {
            for (int dx = -r; dx <= r && seeds.size() < limit; dx++) {
                for (int dy = -r; dy <= r && seeds.size() < limit; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    WorldPoint p = new WorldPoint(start.getX() + dx, start.getY() + dy, start.getPlane());
                    if (standable.test(p)) {
                        seeds.add(p);
                    }
                }
            }
        }
        return seeds;
    }

    /** Whether a search from {@code tile} failed recently enough that it should not be repeated yet. */
    static boolean shouldBackOff(WorldPoint lastFailureTile, long lastFailureMs, long nowMs, WorldPoint tile) {
        return lastFailureTile != null && lastFailureTile.equals(tile) && nowMs - lastFailureMs < BACKOFF_MS;
    }

    /** Nearest bank by straight-line tiles within {@code maxTiles} (same plane preferred); null if none. */
    static BankLocation nearestByDistance(WorldPoint start, Collection<BankLocation> banks, int maxTiles) {
        if (start == null) {
            return null;
        }
        BankLocation best = null;
        long bestScore = Long.MAX_VALUE;
        for (BankLocation b : banks) {
            if (isMinigameBank(b) && !isEligibleTarget(start, b)) {
                continue;
            }
            int d = start.distanceTo2D(b.getWorldPoint());
            if (d > maxTiles) {
                continue;
            }
            long score = d + (b.getWorldPoint().getPlane() == start.getPlane() ? 0 : 1);
            if (score < bestScore) {
                bestScore = score;
                best = b;
            }
        }
        return best;
    }

    static boolean isMinigameBank(BankLocation bank) {
        return MINIGAME_BANKS.contains(bank);
    }

    /** Whether a bank may be a pathfinding target from {@code start}. */
    static boolean isEligibleTarget(WorldPoint start, BankLocation bank) {
        if (!isMinigameBank(bank)) {
            return true;
        }
        return start != null && start.getPlane() == bank.getWorldPoint().getPlane()
                && start.distanceTo2D(bank.getWorldPoint()) <= MINIGAME_BANK_MAX_TILES;
    }

    static boolean usesMinigameTransport(Rs2RouteResult route) {
        return hasTransport(route, false);
    }

    static boolean usesItemOrSpellTeleport(Rs2RouteResult route) {
        return hasTransport(route, true);
    }

    private static boolean hasTransport(Rs2RouteResult route, boolean itemOrSpell) {
        if (route == null) {
            return false;
        }
        for (Rs2RouteStep step : route.getTransportSteps()) {
            Rs2TransportEdge edge = step.getTransport().orElse(null);
            if (edge == null) {
                continue;
            }
            Rs2TransportType type = edge.getType();
            if (itemOrSpell) {
                if (type == Rs2TransportType.TELEPORTATION_ITEM || type == Rs2TransportType.TELEPORTATION_SPELL
                        || type == Rs2TransportType.TELEPORTATION_SPELL_HOME) {
                    return true;
                }
            } else if (type == Rs2TransportType.TELEPORTATION_MINIGAME) {
                return true;
            }
        }
        return false;
    }

    /** Nearest candidate by straight-line tiles on {@code start}'s plane (any plane if none share it); -1 if none. */
    static int nearestTiles(WorldPoint start, Collection<WorldPoint> candidates) {
        int best = -1;
        int bestAnyPlane = -1;
        for (WorldPoint c : candidates) {
            int d = start.distanceTo2D(c);
            if (bestAnyPlane < 0 || d < bestAnyPlane) {
                bestAnyPlane = d;
            }
            if (c.getPlane() == start.getPlane() && (best < 0 || d < best)) {
                best = d;
            }
        }
        return best >= 0 ? best : bestAnyPlane;
    }

    /**
     * @param chosen          bank the pathfinder picked
     * @param candidates      all banks that were offered to the pathfinder
     * @param usesMinigame    route goes through a minigame teleport
     * @param usesItemOrSpell route goes through an item or spell teleport (genuinely shortens travel)
     */
    static boolean isPlausible(WorldPoint start, WorldPoint chosen, Collection<WorldPoint> candidates,
                               boolean usesMinigame, boolean usesItemOrSpell) {
        if (start == null || chosen == null) {
            return true;
        }
        int chosenTiles = start.distanceTo2D(chosen);
        if (usesMinigame && chosenTiles > MINIGAME_BANK_MAX_TILES) {
            return false;
        }
        if (usesItemOrSpell) {
            return true;
        }
        int nearest = Math.max(nearestTiles(start, candidates), MIN_NEAREST_FOR_RATIO);
        return chosenTiles <= (long) nearest * MAX_DISTANCE_RATIO;
    }
}
