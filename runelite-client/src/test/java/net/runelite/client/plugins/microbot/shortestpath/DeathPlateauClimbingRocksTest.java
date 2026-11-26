package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DeathPlateauClimbingRocksTest {
	private static final int ROCKS_SOUTH_ID = 3722;
	private static final int ROCKS_NORTH_ID = 3723;

	private static Map<WorldPoint, Set<Transport>> transports;

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
}
