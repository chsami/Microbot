package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.PrimitiveIntHashMap;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import net.runelite.client.plugins.microbot.util.walker.transport.TransportRefusalLedger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransportRefusalStrikeOutTest
{
	private static final WorldPoint ORIGIN = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint FALLBACK_ORIGIN = new WorldPoint(3222, 3220, 0);
	private static final WorldPoint DESTINATION = new WorldPoint(3222, 9618, 0);

	private static SplitFlagMap collisionMap;

	@BeforeClass
	public static void loadMap()
	{
		collisionMap = SplitFlagMap.fromResources();
	}

	@Test
	public void secondRefusalStrikesOut()
	{
		TransportRefusalLedger ledger = new TransportRefusalLedger();
		assertFalse(TransportRefusalLedger.isStrikeOut(ledger.registerRefusal(ORIGIN, DESTINATION)));
		assertTrue(TransportRefusalLedger.isStrikeOut(ledger.registerRefusal(ORIGIN, DESTINATION)));
		assertFalse("only the exact refusal that reaches the limit strikes out",
			TransportRefusalLedger.isStrikeOut(ledger.registerRefusal(ORIGIN, DESTINATION)));
		assertEquals(0, ledger.registerRefusal(null, DESTINATION));
	}

	@Test
	public void successClearsStrikesAndWalkStartDrainsBlocks()
	{
		TransportRefusalLedger ledger = new TransportRefusalLedger();
		ledger.registerRefusal(ORIGIN, DESTINATION);
		ledger.clear(ORIGIN, DESTINATION);
		assertFalse(TransportRefusalLedger.isStrikeOut(ledger.registerRefusal(ORIGIN, DESTINATION)));

		ledger.recordWalkScopedBlock(ORIGIN, DESTINATION);
		List<WorldPoint[]> drained = ledger.drainWalkScopedBlocks();
		assertEquals(1, drained.size());
		assertEquals(ORIGIN, drained.get(0)[0]);
		assertEquals(DESTINATION, drained.get(0)[1]);
		assertEquals("walk start resets strikes", 0, ledger.strikes(ORIGIN, DESTINATION));
		assertTrue(ledger.drainWalkScopedBlocks().isEmpty());
	}

	@Test
	public void transportRefusedTwiceIsAvoidedByTheReplan() throws Exception
	{
		Transport refused = new Transport(
			ORIGIN, DESTINATION, "refused", TransportType.TRANSPORT, false,
			"Climb-down", "Trapdoor", 1001, 1);
		Transport fallback = new Transport(
			FALLBACK_ORIGIN, DESTINATION, "fallback", TransportType.TRANSPORT, false,
			"Climb-down", "Ladder", 1002, 5);
		Map<WorldPoint, Set<Transport>> catalog = new HashMap<>();
		catalog.put(ORIGIN, Set.of(refused));
		catalog.put(FALLBACK_ORIGIN, Set.of(fallback));
		PathfinderConfig config = configWithTransports(catalog);

		List<WorldPoint> before = plan(config);
		assertEquals(List.of(ORIGIN, DESTINATION), before);

		TransportRefusalLedger ledger = new TransportRefusalLedger();
		assertFalse(TransportRefusalLedger.isStrikeOut(ledger.registerRefusal(ORIGIN, DESTINATION)));
		assertTrue(TransportRefusalLedger.isStrikeOut(ledger.registerRefusal(ORIGIN, DESTINATION)));
		assertTrue(config.learnBlockedEdge(ORIGIN, DESTINATION, "transport-refused"));

		List<WorldPoint> after = plan(config);
		assertEquals(DESTINATION, after.get(after.size() - 1));
		assertTrue("replan must board the fallback transport", after.contains(FALLBACK_ORIGIN));
		for (int i = 0; i + 1 < after.size(); i++)
		{
			assertFalse("replan must not reuse the refused edge",
				after.get(i).equals(ORIGIN) && after.get(i + 1).equals(DESTINATION));
		}

		assertTrue(config.unlearnBlockedEdge(ORIGIN, DESTINATION, "walk ended"));
		assertEquals(List.of(ORIGIN, DESTINATION), plan(config));
	}

	@Test
	public void staticBlockedEdgeDoesNotHideItsOwnTransport() throws Exception
	{
		PathfinderConfig config = configWithTransports(Collections.emptyMap());
		int gateOrigin = WorldPointUtil.packWorldPoint(3267, 3227, 0);
		int gateDestination = WorldPointUtil.packWorldPoint(3268, 3227, 0);
		assertTrue(config.isBlockedTransportEdge(gateOrigin, gateDestination));
		assertFalse(config.isLearnedBlockedTransport(gateOrigin, gateDestination));
	}

	private static List<WorldPoint> plan(PathfinderConfig config)
	{
		Pathfinder pathfinder = new Pathfinder(config, ORIGIN, Set.of(DESTINATION));
		pathfinder.run();
		return pathfinder.getPath();
	}

	@SuppressWarnings("unchecked")
	private static PathfinderConfig configWithTransports(Map<WorldPoint, Set<Transport>> catalog) throws Exception
	{
		PathfinderConfig config = new PathfinderConfig(
			collisionMap, new HashMap<>(catalog), Collections.emptyList(), null, null);
		Field cutoff = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
		cutoff.setAccessible(true);
		cutoff.setLong(config, 10_000L);

		Field transportsField = PathfinderConfig.class.getDeclaredField("transports");
		transportsField.setAccessible(true);
		((Map<WorldPoint, Set<Transport>>) transportsField.get(config)).putAll(catalog);

		Field packedField = PathfinderConfig.class.getDeclaredField("transportsPacked");
		packedField.setAccessible(true);
		PrimitiveIntHashMap<Set<Transport>> packed =
			(PrimitiveIntHashMap<Set<Transport>>) packedField.get(config);
		for (Map.Entry<WorldPoint, Set<Transport>> entry : catalog.entrySet())
		{
			packed.put(WorldPointUtil.packWorldPoint(entry.getKey()), entry.getValue());
		}
		return config;
	}
}
