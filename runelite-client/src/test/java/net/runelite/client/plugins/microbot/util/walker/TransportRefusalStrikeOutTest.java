package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.PrimitiveIntHashMap;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import net.runelite.client.plugins.microbot.util.walker.transport.TransportDispatchTrace;
import net.runelite.client.plugins.microbot.util.walker.transport.TransportDispatchTrace.DeliberateSkip;
import net.runelite.client.plugins.microbot.util.walker.transport.TransportDispatchTrace.Refusal;
import net.runelite.client.plugins.microbot.util.walker.transport.TransportDispatchTrace.Verdict;
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
		collisionMap = sharedCollisionMap();
	}

	static synchronized SplitFlagMap sharedCollisionMap()
	{
		if (collisionMap == null)
		{
			collisionMap = SplitFlagMap.fromResources();
		}
		return collisionMap;
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
	public void interactionRefusalRequiresThePlayerToStillBeAtTheOrigin()
	{
		WorldPoint door = new WorldPoint(3201, 3169, 0);
		WorldPoint inside = new WorldPoint(3202, 3169, 0);
		assertTrue(TransportRefusalLedger.isStillAtOrigin(door, door, inside));
		assertTrue(TransportRefusalLedger.isStillAtOrigin(ORIGIN, ORIGIN, DESTINATION));

		assertFalse("already across the edge",
			TransportRefusalLedger.isStillAtOrigin(inside, door, inside));
		assertFalse("walked away from the origin",
			TransportRefusalLedger.isStillAtOrigin(new WorldPoint(3198, 3169, 0), door, inside));
		assertFalse("plane mismatch",
			TransportRefusalLedger.isStillAtOrigin(new WorldPoint(3201, 3169, 1), door, inside));
		assertFalse(TransportRefusalLedger.isStillAtOrigin(null, door, inside));
	}

	@Test
	public void fallThroughsStrikeOutOnTheirOwnHigherLimit()
	{
		TransportRefusalLedger ledger = new TransportRefusalLedger();
		for (int i = 1; i < TransportRefusalLedger.FALL_THROUGH_STRIKE_LIMIT; i++)
		{
			assertFalse(TransportRefusalLedger.isFallThroughStrikeOut(ledger.registerFallThrough(ORIGIN, DESTINATION)));
		}
		assertTrue(TransportRefusalLedger.isFallThroughStrikeOut(ledger.registerFallThrough(ORIGIN, DESTINATION)));
		assertEquals("fall-throughs do not count as interaction strikes", 0, ledger.strikes(ORIGIN, DESTINATION));
		ledger.clear(ORIGIN, DESTINATION);
		assertEquals(0, ledger.fallThroughs(ORIGIN, DESTINATION));
	}

	@Test
	public void dispatchClassificationSeparatesDeliberateSkipsFromRefusals()
	{
		assertEquals(Verdict.HANDLED,
			TransportDispatchTrace.classify(true, true, null, Refusal.HANDLER_FAILED, true));
		assertEquals(Verdict.REFUSED_AFTER_INTERACTION,
			TransportDispatchTrace.classify(false, true, null, Refusal.HANDLER_FAILED, true));
		assertEquals(Verdict.INTERACTION_LEFT_ORIGIN,
			TransportDispatchTrace.classify(false, true, null, null, false));
		for (DeliberateSkip skip : DeliberateSkip.values())
		{
			assertEquals(skip.name(), Verdict.DELIBERATE_SKIP,
				TransportDispatchTrace.classify(false, false, skip, Refusal.ORIGIN_UNREACHABLE, true));
		}
		for (Refusal refusal : Refusal.values())
		{
			assertEquals(refusal.name(), Verdict.REFUSED_WITHOUT_INTERACTION,
				TransportDispatchTrace.classify(false, false, null, refusal, true));
		}
		assertEquals(Verdict.NOT_ATTEMPTED,
			TransportDispatchTrace.classify(false, false, null, null, true));
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
		return plan(config, ORIGIN, DESTINATION);
	}

	static List<WorldPoint> plan(PathfinderConfig config, WorldPoint start, WorldPoint goal)
	{
		Pathfinder pathfinder = new Pathfinder(config, start, Set.of(goal));
		pathfinder.run();
		return pathfinder.getPath();
	}

	@SuppressWarnings("unchecked")
	static PathfinderConfig configWithTransports(Map<WorldPoint, Set<Transport>> catalog) throws Exception
	{
		PathfinderConfig config = new PathfinderConfig(
			sharedCollisionMap(), new HashMap<>(catalog), Collections.emptyList(), null, null);
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
