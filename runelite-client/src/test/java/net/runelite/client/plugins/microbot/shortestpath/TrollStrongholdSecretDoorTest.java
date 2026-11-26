package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
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
import static org.junit.Assert.assertTrue;

public class TrollStrongholdSecretDoorTest {
	private static final int SECRET_DOOR_ID = 3762;
	private static final WorldPoint DOOR_OUTSIDE = new WorldPoint(2827, 3646, 0);
	private static final WorldPoint DOOR_INSIDE = new WorldPoint(2823, 10050, 0);

	@Test
	public void secretDoorNeedsTheBackExitUnlock() {
		List<Transport> doors = Transport.loadAllFromResources().values().stream()
				.flatMap(Set::stream)
				.filter(t -> t.getObjectId() == SECRET_DOOR_ID)
				.collect(Collectors.toList());

		assertEquals(2, doors.size());
		for (Transport door : doors) {
			assertEquals(DOOR_INSIDE, door.getDestination());
			assertEquals(1, door.getVarbits().size());
			TransportVarbit unlock = door.getVarbits().iterator().next();
			assertEquals(VarbitID.TROLL_OPENED_BACK_EXIT, unlock.getVarbitId());
			assertFalse(unlock.matches(0));
			assertTrue(unlock.matches(1));
		}
	}

	@Test
	public void routeFromDoorWithoutUnlockDoesNotOpenIt() {
		Pathfinder pathfinder = new Pathfinder(configForPlayer(0), DOOR_OUTSIDE, DOOR_INSIDE);
		pathfinder.run();

		assertTrue(pathfinder.isDone());
		assertFalse("route uses the secret door", usesSecretDoor(pathfinder.getPath()));
	}

	@Test
	public void routeFromDoorWithUnlockOpensIt() {
		Pathfinder pathfinder = new Pathfinder(configForPlayer(1), DOOR_OUTSIDE, DOOR_INSIDE);
		pathfinder.run();

		assertTrue(pathfinder.isDone());
		List<WorldPoint> path = pathfinder.getPath();
		assertFalse(path.isEmpty());
		assertEquals(DOOR_INSIDE, path.get(path.size() - 1));
		assertTrue("route skips the secret door", usesSecretDoor(path));
	}

	private static boolean usesSecretDoor(List<WorldPoint> path) {
		for (int i = 0; i + 1 < path.size(); i++) {
			WorldPoint from = path.get(i);
			if (path.get(i + 1).equals(DOOR_INSIDE) && from.getPlane() == 0 && from.getY() == 3646
					&& (from.getX() == 2827 || from.getX() == 2828)) {
				return true;
			}
		}
		return false;
	}

	private static PathfinderConfig configForPlayer(int backExitVarbit) {
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
					.filter(t -> !t.isQuestLocked())
					.filter(t -> t.getItemRequirements().stream().allMatch(r -> r.isSatisfiedBy(id -> 0)))
					.filter(t -> t.getVarbits().stream().allMatch(v -> v.matches(
							v.getVarbitId() == VarbitID.TROLL_OPENED_BACK_EXIT ? backExitVarbit : 0)))
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
