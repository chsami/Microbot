package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Per-walk, non-negative cost jitter for plain walking steps so near-equal routes (a different
 * street, door or staircase) are chosen on different walks. Costs only ever increase and are a pure
 * function of (seed, tile), so the search stays consistent and the extra distance stays small.
 * Transports, targets and ignored-collision tiles are never penalised; blocked tiles are not
 * unblocked (this only adds cost to tiles the collision map already allows).
 */
public final class PathJitter
{
	/** Strength levels: 0 off, 1 low, 2 medium, 3 high. */
	private static volatile int level;
	private static volatile int seed;
	private static final Object LOCK = new Object();
	private static Object walkKey;
	private static long walkStamp;
	private static final Map<Object, Integer> LAST_SEED = new HashMap<>();
	private static final long RESEED_AFTER_MS = 120_000L;

    /** Which part of the pass a thread is in; base searches suspend jitter on their thread only. */
    private static final ThreadLocal<Boolean> SUSPENDED = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Map<Object, int[]> ROUTE_HISTORY = new HashMap<>();
    private static final int HISTORY_DEPTH = 3;
    private static volatile boolean detourOn;
    private static volatile int detourX;
    private static volatile int detourY;
    private static volatile int detourPlane;
    private static final int DETOUR_RADIUS = 2;
    private static final int DETOUR_PENALTY = 6;

    /** Maximum real length of a varied route relative to the base route. */
    public static final double MAX_LENGTH_FACTOR = 1.2;

    /** Runs {@code body} with jitter off on this thread (used to measure the base route). */
    public static void withoutJitter(Runnable body)
    {
        SUSPENDED.set(Boolean.TRUE);
        try
        {
            body.run();
        }
        finally
        {
            SUSPENDED.set(Boolean.FALSE);
        }
    }

    /** Same walk, new seed and detour; used when a route repeats a recent one. */
    public static void reroll(Object key, long nowMs, int startPacked, int targetPacked)
    {
        synchronized (LOCK)
        {
            walkKey = null;
        }
        beginWalk(key, nowMs, startPacked, targetPacked);
    }

    public static void beginWalk(Object key, long nowMs, int startPacked, int targetPacked)
    {
        beginWalk(key, nowMs);
        synchronized (LOCK)
        {
            // pick the detour once per seed: about one walk in four bends around a spot on the direct line
            Random rng = new Random(seed);
            boolean on = rng.nextInt(4) == 0;
            double f = 0.3 + rng.nextDouble() * 0.4;
            int sx = net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil.unpackWorldX(startPacked);
            int sy = net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil.unpackWorldY(startPacked);
            int tx = net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil.unpackWorldX(targetPacked);
            int ty = net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil.unpackWorldY(targetPacked);
            detourX = (int) Math.round(sx + (tx - sx) * f);
            detourY = (int) Math.round(sy + (ty - sy) * f);
            detourPlane = net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil.unpackWorldPlane(startPacked);
            detourOn = on && Math.max(Math.abs(tx - sx), Math.abs(ty - sy)) > 20;
        }
    }

    /** Extra cost for entering a transport destination; small so only near-equal transports flip. */
    public static int transportPenalty(int destinationPacked)
    {
        int lv = level;
        if (lv == 0 || SUSPENDED.get())
        {
            return 0;
        }
        return Integer.remainderUnsigned(mix(destinationPacked * 0x7FEB352D ^ seed), lv + 2);
    }

    /** Extra cost for arriving at one of several equivalent targets (e.g. banks) so close ones alternate. */
    public static int targetPenalty(int targetPacked)
    {
        int lv = level;
        if (lv == 0 || SUSPENDED.get())
        {
            return 0;
        }
        return Integer.remainderUnsigned(mix(targetPacked * 0x2C1B3C6D ^ seed), 2 * lv + 2);
    }

    public static boolean seenRoute(Object key, int routeHash)
    {
        synchronized (LOCK)
        {
            int[] h = ROUTE_HISTORY.get(key);
            if (h != null)
            {
                for (int v : h)
                {
                    if (v == routeHash)
                    {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public static void recordRoute(Object key, int routeHash)
    {
        synchronized (LOCK)
        {
            if (ROUTE_HISTORY.size() > 256)
            {
                ROUTE_HISTORY.clear();
            }
            int[] old = ROUTE_HISTORY.get(key);
            int[] next = new int[old == null ? 1 : Math.min(HISTORY_DEPTH, old.length + 1)];
            next[0] = routeHash;
            if (old != null)
            {
                System.arraycopy(old, 0, next, 1, next.length - 1);
            }
            ROUTE_HISTORY.put(key, next);
        }
    }

    /** Order-sensitive hash of a route sampled every few points. */
    public static int routeHash(java.util.List<net.runelite.api.coords.WorldPoint> path)
    {
        int h = 17;
        for (int i = 0; i < path.size(); i += 4)
        {
            h = h * 31 + path.get(i).hashCode();
        }
        return h;
    }

    /** Walked length: Chebyshev steps; jumps over 2 tiles (transports) cost nothing. */
    public static int routeLength(java.util.List<net.runelite.api.coords.WorldPoint> path)
    {
        int len = 0;
        for (int i = 1; i < path.size(); i++)
        {
            net.runelite.api.coords.WorldPoint a = path.get(i - 1);
            net.runelite.api.coords.WorldPoint b = path.get(i);
            int d = a.getPlane() != b.getPlane() ? 99 : a.distanceTo2D(b);
            if (d <= 2)
            {
                len += d;
            }
        }
        return len;
    }

    /** Cost bound: a varied route may cost at most this much more than the base route (pathfinder cost, jumps included). */
    public static final double MAX_COST_FACTOR = 1.2;
    /** A transport the base route did not use may only come in when the whole route stays within this factor. */
    public static final double NEW_TRANSPORT_COST_FACTOR = 1.1;

    /**
     * Variation may only add short local transports (doors, stairs, shortcuts); never teleports, quetzals,
     * charter ships, boats, fairy rings, gliders and the like.
     */
    public static boolean mayAddTransport(net.runelite.client.plugins.microbot.shortestpath.TransportType type)
    {
        return type == net.runelite.client.plugins.microbot.shortestpath.TransportType.TRANSPORT
            || type == net.runelite.client.plugins.microbot.shortestpath.TransportType.AGILITY_SHORTCUT
            || type == net.runelite.client.plugins.microbot.shortestpath.TransportType.GRAPPLE_SHORTCUT;
    }

    /**
     * Whether a varied route is acceptable against the base route.
     *
     * @param variedCost    pathfinder cost of the varied route without the jitter penalties
     * @param baseCost      pathfinder cost of the base route
     * @param newLocal      the varied route uses a local transport the base route did not
     * @param newForbidden  the varied route uses a transport the base route did not and variation may not add
     */
    public static boolean acceptVaried(long variedCost, long baseCost, boolean newLocal, boolean newForbidden)
    {
        if (newForbidden)
        {
            return false;
        }
        double factor = newLocal ? NEW_TRANSPORT_COST_FACTOR : MAX_COST_FACTOR;
        return variedCost <= (long) Math.floor(baseCost * factor + 1e-6);
    }

    public static boolean withinBound(int variedLength, int baseLength)
    {
        return variedLength <= Math.ceil(baseLength * MAX_LENGTH_FACTOR);
    }

	private PathJitter()
	{
	}

	public static void configure(boolean enabled, int strengthLevel)
	{
		level = enabled ? Math.max(1, Math.min(3, strengthLevel)) : 0;
	}

	public static boolean isActive()
	{
		return level > 0 && !SUSPENDED.get();
	}

	/**
	 * Picks the seed for a walk to {@code key}. The same key keeps its seed (re-paths mid-walk must not
	 * flip routes); a new key, or the same key after a pause, gets a seed different from its last one.
	 */
	public static void beginWalk(Object key, long nowMs)
	{
		if (level == 0)
		{
			return;
		}
		synchronized (LOCK)
		{
			if (java.util.Objects.equals(key, walkKey) && nowMs - walkStamp < RESEED_AFTER_MS)
			{
				walkStamp = nowMs;
				return;
			}
			Integer previous = LAST_SEED.get(key);
			int next;
			Random rng = new Random(System.nanoTime() ^ nowMs);
			do
			{
				next = rng.nextInt();
			}
			while (previous != null && next == previous);
			if (LAST_SEED.size() > 256)
			{
				LAST_SEED.clear();
			}
			LAST_SEED.put(key, next);
			walkKey = key;
			walkStamp = nowMs;
			seed = next;
		}
	}

	/** Extra cost of entering {@code packedTile}, 0 when inactive. */
	public static int penalty(int packedTile)
	{
		int lv = SUSPENDED.get() ? 0 : level;
		return lv == 0 ? 0 : penalty(packedTile, seed, lv) + corridor(packedTile);
	}

	/** Pure form for tests. Low: 1/16 tiles +1; medium: 1/10 tiles +1..2; high: 1/6 tiles +1..3. */
	public static int penalty(int packedTile, int seed, int lv)
	{
		if (lv <= 0)
		{
			return 0;
		}
		int h = mix(packedTile * 0x9E3779B1 ^ seed);
		int every = lv == 1 ? 16 : lv == 2 ? 10 : 6;
		int max = lv;
		if (Integer.remainderUnsigned(h, every) != 0)
		{
			return 0;
		}
		return 1 + Integer.remainderUnsigned(h >>> 8, max);
	}

	private static int corridor(int packedTile)
	{
		if (!detourOn)
		{
			return 0;
		}
		int x = net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil.unpackWorldX(packedTile);
		int y = net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil.unpackWorldY(packedTile);
		int p = net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil.unpackWorldPlane(packedTile);
		return p == detourPlane && Math.abs(x - detourX) <= DETOUR_RADIUS && Math.abs(y - detourY) <= DETOUR_RADIUS ? DETOUR_PENALTY : 0;
	}

	private static int mix(int x)
	{
		x ^= x >>> 16;
		x *= 0x85EBCA6B;
		x ^= x >>> 13;
		x *= 0xC2B2AE35;
		x ^= x >>> 16;
		return x;
	}
}
