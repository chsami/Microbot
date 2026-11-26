package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DeathPlateauClimbingRocksTest {
	private static final int ROCKS_SOUTH_ID = 3722;
	private static final int ROCKS_NORTH_ID = 3723;
	private static final WorldPoint DENULTH = new WorldPoint(2896, 3529, 0);
	private static final WorldPoint SECRET_PATH = new WorldPoint(2865, 3609, 0);
	private static final WorldPoint STILE_SOUTH = new WorldPoint(2817, 3561, 0);
	private static final WorldPoint STILE_NORTH = new WorldPoint(2817, 3564, 0);

	private static HashMap<WorldPoint, Set<Transport>> transports;

	@BeforeClass
	public static void load() {
		transports = Transport.loadAllFromResources();
	}

	@Test
	public void everyRockClimbNeedsClimbingBoots() {
		assertNeedsClimbingBoots(find(new WorldPoint(2880, 3596, 0), new WorldPoint(2880, 3593, 0), ROCKS_SOUTH_ID));
		assertNeedsClimbingBoots(find(new WorldPoint(2880, 3593, 0), new WorldPoint(2880, 3596, 0), ROCKS_NORTH_ID));
		assertNeedsClimbingBoots(find(new WorldPoint(2881, 3596, 0), new WorldPoint(2881, 3593, 0), ROCKS_SOUTH_ID));
		assertNeedsClimbingBoots(find(new WorldPoint(2881, 3593, 0), new WorldPoint(2881, 3596, 0), ROCKS_NORTH_ID));
	}

	@Test
	public void secretPathRouteWithoutBootsTakesTenzingsStile() {
		Pathfinder pathfinder = new Pathfinder(configWithoutItems(), DENULTH, SECRET_PATH);
		pathfinder.run();

		assertTrue(pathfinder.isDone());
		List<WorldPoint> path = pathfinder.getPath();
		assertFalse(path.isEmpty());
		assertTrue("route ended at " + path.get(path.size() - 1), path.get(path.size() - 1).distanceTo(SECRET_PATH) <= 1);
		for (int x = 2880; x <= 2881; x++) {
			assertFalse("route climbs the rocks at x=" + x,
					hasStep(path, new WorldPoint(x, 3596, 0), new WorldPoint(x, 3593, 0)));
		}
		assertTrue("route skips Tenzing's stile", hasStep(path, STILE_SOUTH, STILE_NORTH));
	}

	private static void assertNeedsClimbingBoots(Transport transport) {
		List<TransportItemRequirement> requirements = transport.getItemRequirements();
		assertEquals(1, requirements.size());
		TransportItemRequirement requirement = requirements.get(0);
		assertFalse(requirement.isSatisfiedBy(id -> 0));
		assertTrue(requirement.isSatisfiedBy(id -> id == ItemID.DEATH_CLIMBINGBOOTS ? 1 : 0));
		assertTrue(requirement.isSatisfiedBy(id -> id == ItemID.CLIMBING_BOOTS_G ? 1 : 0));
	}

	private static Transport find(WorldPoint origin, WorldPoint destination, int objectId) {
		Set<Transport> atOrigin = transports.get(origin);
		assertNotNull("no transports at " + origin, atOrigin);
		return atOrigin.stream()
				.filter(t -> t.getObjectId() == objectId && destination.equals(t.getDestination()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no transport " + origin + " -> " + destination));
	}

	private static boolean hasStep(List<WorldPoint> path, WorldPoint a, WorldPoint b) {
		for (int i = 0; i + 1 < path.size(); i++) {
			WorldPoint from = path.get(i);
			WorldPoint to = path.get(i + 1);
			if ((from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a))) {
				return true;
			}
		}
		return false;
	}

	private static PathfinderConfig configWithoutItems() {
		HashMap<WorldPoint, Set<Transport>> all = Transport.loadAllFromResources();
		SplitFlagMap collisionMap = SplitFlagMap.fromResources();
		PathfinderConfig config = new PathfinderConfig(collisionMap, all, Collections.emptyList(), null, null);
		try {
			Field cutoff = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
			cutoff.setAccessible(true);
			cutoff.setLong(config, 10000);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		for (Map.Entry<WorldPoint, Set<Transport>> entry : all.entrySet()) {
			if (entry.getKey() == null) {
				continue;
			}
			Set<Transport> usable = entry.getValue().stream()
					.filter(t -> t.getItemRequirements().stream().allMatch(r -> r.isSatisfiedBy(id -> 0)))
					.collect(Collectors.toSet());
			entry.getValue().stream()
					.filter(t -> !usable.contains(t))
					.forEach(config::addBlockedTransportEdgeIfNeeded);
			if (!usable.isEmpty()) {
				config.getTransports().put(entry.getKey(), usable);
				config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(entry.getKey()), usable);
			}
		}
		return config;
	}
}
