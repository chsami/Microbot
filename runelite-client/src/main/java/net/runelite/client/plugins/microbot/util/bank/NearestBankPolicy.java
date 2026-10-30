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

    private static final Set<Rs2TransportType> TRAVEL_TRANSPORTS = EnumSet.of(
            Rs2TransportType.BOAT, Rs2TransportType.CANOE, Rs2TransportType.CHARTER_SHIP, Rs2TransportType.SHIP,
            Rs2TransportType.FAIRY_RING, Rs2TransportType.QUETZAL, Rs2TransportType.QUETZAL_WHISTLE,
            Rs2TransportType.GNOME_GLIDER, Rs2TransportType.MINECART, Rs2TransportType.SPIRIT_TREE,
            Rs2TransportType.MAGIC_CARPET, Rs2TransportType.HOT_AIR_BALLOON, Rs2TransportType.MAGIC_MUSHTREE);

    /** Ships, charters, quetzals, gliders and the like: transports that carry the player to another region. */
    static boolean isTravelTransport(Rs2TransportType type) {
        return type != null && TRAVEL_TRANSPORTS.contains(type);
    }

    /** Whether a route needs a ship, charter, quetzal or similar long-distance transport. */
    static boolean usesTravelTransport(Rs2RouteResult route) {
        if (route == null) {
            return false;
        }
        for (Rs2RouteStep step : route.getTransportSteps()) {
            Rs2TransportEdge edge = step.getTransport().orElse(null);
            if (edge != null && isTravelTransport(edge.getType())) {
                return true;
            }
        }
        return false;
    }

    static boolean usesMinigameTransport(Rs2RouteResult route) {
        return hasTransport(route, false);
    }

    static boolean usesHomeTeleport(Rs2RouteResult route) {
        if (route == null) {
            return false;
        }
        for (Rs2RouteStep step : route.getTransportSteps()) {
            Rs2TransportEdge edge = step.getTransport().orElse(null);
            if (edge != null && edge.getType() == Rs2TransportType.TELEPORTATION_SPELL_HOME) {
                return true;
            }
        }
        return false;
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

    /** A bank within this many walking tiles of the player is preferred unless another is strictly cheaper. */
    static final int NEARBY_WALK_TILES = 40;
    /** Home teleport is only a candidate when the bank is farther than this many tiles away. */
    static final int HOME_TELEPORT_MIN_TILES = 150;
    /** Cast (~10 s) plus cooldown awareness, in the planner's tick-like cost units. */
    static final long HOME_TELEPORT_COST = 50;
    /** Added when the return leg changes plane. */
    static final int PLANE_CHANGE_COST = 4;

    /** One planned way to reach a bank. {@code toBankCost} is the planner cost from the player to the bank. */
    static final class BankOption {
        final BankLocation bank;
        final long toBankCost;
        final boolean homeTeleport;
        /** The route needs a ship, charter, quetzal or similar. */
        final boolean travelTransport;

        BankOption(BankLocation bank, long toBankCost, boolean homeTeleport) {
            this(bank, toBankCost, homeTeleport, false);
        }

        BankOption(BankLocation bank, long toBankCost, boolean homeTeleport, boolean travelTransport) {
            this.bank = bank;
            this.toBankCost = toBankCost;
            this.homeTeleport = homeTeleport;
            this.travelTransport = travelTransport;
        }
    }

    /** Whether a route using the home teleport spell may be considered for a bank this far (straight line) away. */
    static boolean homeTeleportAllowed(WorldPoint start, WorldPoint bank) {
        return start == null || bank == null || start.distanceTo2D(bank) > HOME_TELEPORT_MIN_TILES;
    }

    /** Straight-line approximation of the walk from the bank back to the point the caller needs to resume at. */
    static long returnLegCost(WorldPoint bank, WorldPoint returnPoint) {
        if (bank == null || returnPoint == null) {
            return 0;
        }
        long cost = bank.distanceTo2D(returnPoint);
        if (bank.getPlane() != returnPoint.getPlane()) {
            cost += PLANE_CHANGE_COST;
        }
        return cost;
    }

    /** Round trip: player to bank (planner cost, home teleport priced realistically) plus bank to return point. */
    static long roundTripScore(BankOption option, WorldPoint returnPoint) {
        long to = option.toBankCost + (option.homeTeleport ? HOME_TELEPORT_COST : 0);
        return to + returnLegCost(option.bank.getWorldPoint(), returnPoint);
    }

    /**
     * Picks the option with the lowest round-trip score, except that an accessible bank within
     * {@link #NEARBY_WALK_TILES} of the player wins unless another bank is strictly cheaper.
     * Home-teleport options for banks closer than {@link #HOME_TELEPORT_MIN_TILES} are dropped.
     */
    static BankOption choose(WorldPoint player, WorldPoint returnPoint, Collection<BankOption> options) {
        boolean anyOnFoot = false;
        for (BankOption o : options) {
            if (o != null && o.bank != null && !o.travelTransport) {
                anyOnFoot = true;
            }
        }
        BankOption best = null;
        long bestScore = Long.MAX_VALUE;
        BankOption nearby = null;
        long nearbyScore = Long.MAX_VALUE;
        for (BankOption o : options) {
            if (o == null || o.bank == null || (anyOnFoot && o.travelTransport)) {
                continue;
            }
            WorldPoint bp = o.bank.getWorldPoint();
            if (o.homeTeleport && !homeTeleportAllowed(player, bp)) {
                continue;
            }
            long score = roundTripScore(o, returnPoint);
            if (score < bestScore) {
                bestScore = score;
                best = o;
            }
            boolean isNearby = player != null && !o.homeTeleport && player.distanceTo2D(bp) <= NEARBY_WALK_TILES;
            if (isNearby && score < nearbyScore) {
                nearbyScore = score;
                nearby = o;
            }
        }
        if (nearby != null && bestScore >= nearbyScore) {
            return nearby;
        }
        return best;
    }
}
