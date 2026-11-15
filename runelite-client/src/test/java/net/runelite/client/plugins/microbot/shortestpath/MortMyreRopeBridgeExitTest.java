package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MortMyreRopeBridgeExitTest {
	private static final int BRIDGE_TREE_ID = 5003;
	private static final WorldPoint BRIDGE_NORTH_END = new WorldPoint(3502, 3430, 0);
	private static final WorldPoint NORTH_BANK = new WorldPoint(3502, 3432, 0);
	private static final WorldPoint SOUTH_BANK = new WorldPoint(3502, 3425, 0);
	private static final WorldPoint CURPILE_FYOD = new WorldPoint(3508, 3440, 0);
	private static final int MIN_X = 3480;
	private static final int MAX_X = 3520;
	private static final int MIN_Y = 3431;
	private static final int MAX_Y = 3464;

	private static SplitFlagMap collisionData;
	private static Map<WorldPoint, Set<Transport>> transports;

	@BeforeClass
	public static void load() {
		collisionData = SplitFlagMap.fromResources();
		transports = Transport.loadAllFromResources();
	}

	@Test
	public void repairedBridgeEndHasAnExitToTheNorthBank() {
		Transport exit = findTree(BRIDGE_NORTH_END, NORTH_BANK);

		assertEquals("Cross-bridge", exit.getAction());
		assertEquals("Tree", exit.getName());
	}

	@Test
	public void bankToBankCrossingsAreStillShipped() {
		assertEquals("Cross-bridge", findTree(SOUTH_BANK, NORTH_BANK).getAction());
		assertEquals("Cross-bridge", findTree(NORTH_BANK, SOUTH_BANK).getAction());
	}

	@Test
	public void curpileIsReachableOnFootFromWhereTheExitLands() {
		CollisionMap map = new CollisionMap(collisionData);

		assertTrue(reachableOnFoot(map, NORTH_BANK).contains(CURPILE_FYOD));
	}

	private static Transport findTree(WorldPoint origin, WorldPoint destination) {
		Set<Transport> atOrigin = transports.get(origin);
		assertNotNull("no transports at " + origin, atOrigin);
		return atOrigin.stream()
				.filter(t -> t.getObjectId() == BRIDGE_TREE_ID && destination.equals(t.getDestination()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no transport " + origin + " -> " + destination));
	}

	private static Set<WorldPoint> reachableOnFoot(CollisionMap map, WorldPoint start) {
		final int plane = start.getPlane();
		Set<WorldPoint> seen = new HashSet<>();
		ArrayDeque<WorldPoint> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		while (!queue.isEmpty()) {
			WorldPoint cur = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					if (dx == 0 && dy == 0) {
						continue;
					}
					WorldPoint next = new WorldPoint(cur.getX() + dx, cur.getY() + dy, plane);
					if (next.getX() < MIN_X || next.getX() > MAX_X || next.getY() < MIN_Y || next.getY() > MAX_Y) {
						continue;
					}
					if (seen.contains(next) || !map.canStep(cur.getX(), cur.getY(), plane, dx, dy)) {
						continue;
					}
					seen.add(next);
					queue.add(next);
				}
			}
		}
		return seen;
	}
}
