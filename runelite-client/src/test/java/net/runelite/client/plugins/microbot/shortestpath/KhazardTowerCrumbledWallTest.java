package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class KhazardTowerCrumbledWallTest {
	private static final int CRUMBLED_WALL_ID = 2185;
	private static final int TREE_GNOME_VILLAGE_VARP = 111;
	private static final WorldPoint DOOR_OUTSIDE = new WorldPoint(2502, 3250, 0);
	private static final WorldPoint DOOR_INSIDE = new WorldPoint(2502, 3251, 0);
	private static final WorldPoint WALL_OUTSIDE = new WorldPoint(2509, 3252, 0);
	private static final WorldPoint WALL_INSIDE = new WorldPoint(2509, 3254, 0);
	private static final WorldPoint LADDER_APPROACH = new WorldPoint(2502, 3252, 0);
	private static final int PLANE = 0;
	private static final int MIN_X = 2480;
	private static final int MAX_X = 2530;
	private static final int MIN_Y = 3230;
	private static final int MAX_Y = 3280;

	private static SplitFlagMap collisionData;
	private static Map<WorldPoint, Set<Transport>> transports;

	@BeforeClass
	public static void load() {
		collisionData = SplitFlagMap.fromResources();
		transports = Transport.loadAllFromResources();
	}

	@Test
	public void lockedDoorEdgeIsPermanentlyBlockedBothWays() {
		PathfinderConfig config = createConfig();

		assertTrue(config.isBlockedTransportStep(
				WorldPointUtil.packWorldPoint(DOOR_OUTSIDE), WorldPointUtil.packWorldPoint(DOOR_INSIDE)));
		assertTrue(config.isBlockedTransportStep(
				WorldPointUtil.packWorldPoint(DOOR_INSIDE), WorldPointUtil.packWorldPoint(DOOR_OUTSIDE)));
	}

	@Test
	public void crumbledWallEntryRequiresBallistaHit() {
		Transport entry = findCrumbledWall(WALL_OUTSIDE, WALL_INSIDE);

		assertEquals("Climb-over", entry.getAction());
		assertEquals("Crumbled wall", entry.getName());
		assertEquals(1, entry.getVarplayers().size());
		TransportVarPlayer requirement = entry.getVarplayers().iterator().next();
		assertEquals(TREE_GNOME_VILLAGE_VARP, requirement.getVarplayerId());
		assertFalse(requirement.matches(4));
		assertTrue(requirement.matches(5));
		assertTrue(requirement.matches(9));
	}

	@Test
	public void crumbledWallExitHasNoRequirement() {
		Transport exit = findCrumbledWall(WALL_INSIDE, WALL_OUTSIDE);

		assertEquals("Climb-over", exit.getAction());
		assertTrue(exit.getVarplayers().isEmpty());
	}

	@Test
	public void ladderIsOnlyReachableOnFootFromInsideTheCrumbledWall() {
		PathfinderConfig config = createConfig();
		CollisionMap map = new CollisionMap(collisionData);

		assertTrue(reachableOnFoot(map, config, WALL_INSIDE).contains(LADDER_APPROACH));
		assertFalse(reachableOnFoot(map, config, WALL_OUTSIDE).contains(LADDER_APPROACH));
		assertTrue(reachableOnFoot(map, config, WALL_OUTSIDE).contains(DOOR_OUTSIDE));
	}

	private static Transport findCrumbledWall(WorldPoint origin, WorldPoint destination) {
		Set<Transport> atOrigin = transports.get(origin);
		assertNotNull("no transports at " + origin, atOrigin);
		return atOrigin.stream()
				.filter(t -> t.getObjectId() == CRUMBLED_WALL_ID && destination.equals(t.getDestination()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no Crumbled wall transport " + origin + " -> " + destination));
	}

	private static Set<WorldPoint> reachableOnFoot(CollisionMap map, PathfinderConfig config, WorldPoint start) {
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
					WorldPoint next = new WorldPoint(cur.getX() + dx, cur.getY() + dy, PLANE);
					if (next.getX() < MIN_X || next.getX() > MAX_X || next.getY() < MIN_Y || next.getY() > MAX_Y) {
						continue;
					}
					if (seen.contains(next) || !map.canStep(cur.getX(), cur.getY(), PLANE, dx, dy)) {
						continue;
					}
					if (config.isBlockedTransportStep(
							WorldPointUtil.packWorldPoint(cur), WorldPointUtil.packWorldPoint(next))) {
						continue;
					}
					seen.add(next);
					queue.add(next);
				}
			}
		}
		return seen;
	}

	private static PathfinderConfig createConfig() {
		return new PathfinderConfig(collisionData, new HashMap<>(), Collections.emptyList(), null, null);
	}
}
