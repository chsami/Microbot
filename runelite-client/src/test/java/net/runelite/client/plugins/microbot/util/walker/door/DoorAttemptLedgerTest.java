package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Characterization of the ATTEMPTED facet of the door-attempt ledger (D3 slice 1). Every row pins a
 * behaviour the two pre-ledger stores ({@code recentDoorAttemptByEdge} and
 * {@code routeState.lastDoorAttempt*}) exhibited live — the fold must change where the facts live,
 * not what they say.
 */
public class DoorAttemptLedgerTest
{
	private static final long COOLDOWN_MS = 2_500;
	private static final long T0 = 1_000_000L;

	private final WorldPoint near = new WorldPoint(1875, 5240, 0);
	private final WorldPoint far = new WorldPoint(1876, 5239, 0);
	private final WorldPoint otherNear = new WorldPoint(1879, 5239, 0);
	private final WorldPoint otherFar = new WorldPoint(1879, 5240, 0);

	private DoorAttemptLedger ledger;

	@Before
	public void setUp()
	{
		ledger = new DoorAttemptLedger();
	}

	// ---- the anti-hammer cooldown (formerly recentDoorAttemptByEdge) ----

	@Test
	public void attemptThrottlesTheSameEdgeWithinTheCooldown()
	{
		ledger.markAttempt(null, near, far, T0);
		assertTrue(ledger.shouldThrottleAttempt(null, near, far, COOLDOWN_MS, T0 + 1_000));
	}

	@Test
	public void detectionClaimsTheEdgeWithoutDelayingTheFirstInteraction()
	{
		ledger.claimDetectedEdge(near, far, T0);
		assertNotNull(ledger.latestAttempt(COOLDOWN_MS, T0 + 1));
		assertFalse(ledger.shouldThrottleAttempt(null, near, far, COOLDOWN_MS, T0 + 1));
	}

	@Test
	public void theCooldownIsDirectionBlind()
	{
		// The edge key normalizes direction: clicking the gate from the far side one second after
		// clicking it from the near side is still hammering the same door.
		ledger.markAttempt(null, near, far, T0);
		assertTrue(ledger.shouldThrottleAttempt(null, far, near, COOLDOWN_MS, T0 + 1_000));
	}

	@Test
	public void theCooldownExpires()
	{
		ledger.markAttempt(null, near, far, T0);
		assertFalse(ledger.shouldThrottleAttempt(null, near, far, COOLDOWN_MS, T0 + COOLDOWN_MS + 1));
	}

	@Test
	public void aDifferentEdgeIsNeverThrottledByThisOne()
	{
		// Chaining is not hammering — the Stronghold's gates are three tiles apart and the walk
		// must be free to attempt the NEXT gate immediately.
		ledger.markAttempt(null, near, far, T0);
		assertFalse(ledger.shouldThrottleAttempt(null, otherNear, otherFar, COOLDOWN_MS, T0 + 100));
	}

	@Test
	public void tileKeyedAttemptsFeedTheCooldownButNeverBecomeTheClaim()
	{
		// A probe-only door (no resolved edge) has always been cooldown-tracked by its tile without
		// becoming "the door the walker is working on".
		WorldPoint doorTile = new WorldPoint(1859, 5239, 0);
		ledger.markAttempt(doorTile, null, null, T0);
		assertTrue(ledger.shouldThrottleAttempt(doorTile, null, null, COOLDOWN_MS, T0 + 100));
		assertNull(ledger.latestAttempt());
	}

	@Test
	public void attemptTimesAreReadableForTheAgeHeuristics()
	{
		ledger.markAttempt(null, near, far, T0);
		assertEquals(Long.valueOf(T0), ledger.attemptAtMs(near, far));
		assertEquals("age reads are direction-blind like the cooldown",
			Long.valueOf(T0), ledger.attemptAtMs(far, near));
		assertNull(ledger.attemptAtMs(otherNear, otherFar));
	}

	// ---- the latest claim (formerly routeState.lastDoorAttempt*) ----

	@Test
	public void theLatestClaimAnswersTheActiveEdgeQuestionInBothDirections()
	{
		// The live-collision route validator asks "does the executor own this edge" without caring
		// which way the crossing runs (fightarena_door1 lesson).
		ledger.markAttempt(null, near, far, T0);
		DoorAttemptLedger.Attempt claim = ledger.latestAttempt(6_000, T0 + 1_000);
		assertNotNull(claim);
		assertTrue(claim.matchesEdge(near, far));
		assertTrue(claim.matchesEdge(far, near));
		assertFalse(claim.matchesEdge(otherNear, otherFar));
	}

	@Test
	public void theClaimGoesStale()
	{
		ledger.markAttempt(null, near, far, T0);
		assertNull("a claim older than its window satisfies nothing",
			ledger.latestAttempt(6_000, T0 + 6_001));
		assertNotNull("but the un-aged read still sees it (same-edge cooldown semantics)",
			ledger.latestAttempt());
	}

	@Test
	public void theSameEdgeCooldownCheckIsDirectionAware()
	{
		// shouldThrottleGlobalDoorInteraction's same-edge test was always directional — approaching
		// the door from the other side is a new interaction context, not a re-click.
		ledger.markAttempt(null, near, far, T0);
		DoorAttemptLedger.Attempt claim = ledger.latestAttempt();
		assertTrue(claim.isSameDirectedEdge(near, far));
		assertFalse(claim.isSameDirectedEdge(far, near));
	}

	@Test
	public void aNewerAttemptReplacesTheClaim()
	{
		// Stronghold 2026-08-12: once gate 2 is attempted, gate 1 must no longer be "the door the
		// walker is working on" — the victory-lap bug was exactly a stale claim outliving its door.
		ledger.markAttempt(null, near, far, T0);
		ledger.markAttempt(null, otherNear, otherFar, T0 + 500);
		DoorAttemptLedger.Attempt claim = ledger.latestAttempt(6_000, T0 + 600);
		assertTrue(claim.matchesEdge(otherNear, otherFar));
		assertFalse(claim.matchesEdge(near, far));
	}

	// ---- the REFUSED facet: strike counting (formerly Rs2DoorHandler.registerDoorCrossFailure) ----
	//
	// Seeded from the Tithe Farm incident (2026-08-12): Farm door 27445 refused to pass a seedless
	// player, and with no strike-out the walker ping-ponged door->recovery for 4+ minutes until a
	// human cancelled it. Three concluded-but-uncrossed attempts must strike the edge out.

	private static final long DECAY_MS = 300_000L;
	private static final int STRIKE_LIMIT = 3;

	@Test
	public void thirdConclusiveFailureStrikesOut()
	{
		assertEquals(DoorAttemptLedger.Strike.COUNTED,
			ledger.registerCrossFailure(near, far, true, T0, DECAY_MS, STRIKE_LIMIT));
		assertEquals(DoorAttemptLedger.Strike.COUNTED,
			ledger.registerCrossFailure(near, far, true, T0 + 12_000, DECAY_MS, STRIKE_LIMIT));
		assertEquals(DoorAttemptLedger.Strike.STRIKE_OUT,
			ledger.registerCrossFailure(near, far, true, T0 + 24_000, DECAY_MS, STRIKE_LIMIT));
		// The strike-out consumed the entry: the edge starts fresh if it is ever attempted again.
		assertEquals(DoorAttemptLedger.Strike.COUNTED,
			ledger.registerCrossFailure(near, far, true, T0 + 25_000, DECAY_MS, STRIKE_LIMIT));
	}

	/** Strikes are direction-blind like every other edge fact: refusing to pass is a property of the door. */
	@Test
	public void strikesAccumulateAcrossDirections()
	{
		ledger.registerCrossFailure(near, far, true, T0, DECAY_MS, STRIKE_LIMIT);
		ledger.registerCrossFailure(far, near, true, T0 + 1_000, DECAY_MS, STRIKE_LIMIT);
		assertEquals(DoorAttemptLedger.Strike.STRIKE_OUT,
			ledger.registerCrossFailure(near, far, true, T0 + 2_000, DECAY_MS, STRIKE_LIMIT));
	}

	/** A moving or cancelled sample proves only that the approach was in flight — the Wydin lesson. */
	@Test
	public void inconclusiveSamplesNeverCount()
	{
		for (int i = 0; i < 10; i++)
		{
			assertEquals(DoorAttemptLedger.Strike.NOT_COUNTED,
				ledger.registerCrossFailure(near, far, false, T0 + i, DECAY_MS, STRIKE_LIMIT));
		}
		assertEquals(DoorAttemptLedger.Strike.COUNTED,
			ledger.registerCrossFailure(near, far, true, T0 + 100, DECAY_MS, STRIKE_LIMIT));
	}

	/** Strikes older than the decay window reset; two failures an hour apart are not a pattern. */
	@Test
	public void staleStrikesDecay()
	{
		ledger.registerCrossFailure(near, far, true, T0, DECAY_MS, STRIKE_LIMIT);
		ledger.registerCrossFailure(near, far, true, T0 + 1_000, DECAY_MS, STRIKE_LIMIT);
		// Third failure arrives after the decay window: the old two evaporate, count restarts at 1.
		assertEquals(DoorAttemptLedger.Strike.COUNTED,
			ledger.registerCrossFailure(near, far, true, T0 + 1_000 + DECAY_MS + 1, DECAY_MS, STRIKE_LIMIT));
	}

	@Test
	public void edgesStrikeIndependently()
	{
		ledger.registerCrossFailure(near, far, true, T0, DECAY_MS, STRIKE_LIMIT);
		ledger.registerCrossFailure(near, far, true, T0 + 1_000, DECAY_MS, STRIKE_LIMIT);
		assertEquals(DoorAttemptLedger.Strike.COUNTED,
			ledger.registerCrossFailure(otherNear, otherFar, true, T0 + 2_000, DECAY_MS, STRIKE_LIMIT));
	}

	/** A successful crossing forgives accumulated strikes (transient refusals must not accrue). */
	@Test
	public void successfulCrossingClearsStrikes()
	{
		ledger.registerCrossFailure(near, far, true, T0, DECAY_MS, STRIKE_LIMIT);
		ledger.registerCrossFailure(near, far, true, T0 + 1_000, DECAY_MS, STRIKE_LIMIT);
		ledger.clearCrossFailures(near, far);
		assertEquals(DoorAttemptLedger.Strike.COUNTED,
			ledger.registerCrossFailure(near, far, true, T0 + 2_000, DECAY_MS, STRIKE_LIMIT));
	}

	// ---- the tile facets: recently-opened suppression and the session blacklist ----

	private static final long SUPPRESS_MS = 10_000L;

	/** Re-clicking a just-opened door closes it again — the original two-clicks-per-door bug. */
	@Test
	public void aJustOpenedDoorSuppressesProbesOnItsSegment()
	{
		WorldPoint doorTile = new WorldPoint(1875, 5240, 0);
		ledger.markStationaryDoorOpened(doorTile, T0);
		assertTrue("segment ending beside the opened door must be suppressed",
			ledger.recentlyOpenedDoorOnSegment(near, far, SUPPRESS_MS, T0 + 1_000));
		assertTrue(ledger.wasStationaryDoorOpenedWithin(doorTile, SUPPRESS_MS, T0 + 1_000));
	}

	@Test
	public void theSuppressionExpires()
	{
		WorldPoint doorTile = new WorldPoint(1875, 5240, 0);
		ledger.markStationaryDoorOpened(doorTile, T0);
		assertFalse(ledger.recentlyOpenedDoorOnSegment(near, far, SUPPRESS_MS, T0 + SUPPRESS_MS + 1));
		assertFalse(ledger.wasStationaryDoorOpenedWithin(doorTile, SUPPRESS_MS, T0 + SUPPRESS_MS + 1));
	}

	@Test
	public void aFarAwayOpenedDoorSuppressesNothing()
	{
		ledger.markStationaryDoorOpened(new WorldPoint(1990, 5300, 0), T0);
		assertFalse("suppression is local (within 2 tiles of a segment end), not global",
			ledger.recentlyOpenedDoorOnSegment(near, far, SUPPRESS_MS, T0 + 1_000));
	}

	@Test
	public void blacklistedDoorsAreSessionPermanent()
	{
		WorldPoint doorTile = new WorldPoint(1907, 5223, 0);
		assertFalse(ledger.isDoorBlacklisted(doorTile));
		ledger.blacklistDoor(doorTile);
		assertTrue(ledger.isDoorBlacklisted(doorTile));
		assertFalse("plane is part of the tile identity",
			ledger.isDoorBlacklisted(new WorldPoint(1907, 5223, 1)));
	}

	// ---- the REFUSED facet: walk-scoped blocks ----

	/** The museum lesson: a strike-out blocks the edge for THIS walk only; the next walk withdraws it. */
	@Test
	public void walkScopedBlocksDrainOnceAndInOrder()
	{
		ledger.recordWalkScopedBlock(near, far);
		ledger.recordWalkScopedBlock(far, near);

		java.util.List<WorldPoint[]> drained = ledger.drainWalkScopedBlocks();
		assertEquals(2, drained.size());
		assertEquals(near, drained.get(0)[0]);
		assertEquals(far, drained.get(0)[1]);
		assertEquals(far, drained.get(1)[0]);
		assertEquals(near, drained.get(1)[1]);
		assertTrue("a second drain must find nothing — blocks are withdrawn exactly once",
			ledger.drainWalkScopedBlocks().isEmpty());
	}

	// ---- the pass budget (formerly processWalk's doorEdgesAttemptedThisTail map) ----

	@Test
	public void anEdgeIsClaimableOncePerPassFromTheSameStand()
	{
		WorldPoint fromWp = new WorldPoint(2465, 3494, 0);
		WorldPoint toWp = new WorldPoint(2465, 3493, 0);
		WorldPoint stand = new WorldPoint(2465, 3494, 0);
		assertTrue(ledger.tryClaimEdgeThisPass(fromWp, toWp, stand));
		assertFalse(ledger.tryClaimEdgeThisPass(fromWp, toWp, stand));
	}

	@Test
	public void theReverseEdgeIsTheSameClaim()
	{
		WorldPoint fromWp = new WorldPoint(2465, 3494, 0);
		WorldPoint toWp = new WorldPoint(2465, 3493, 0);
		WorldPoint stand = new WorldPoint(2465, 3494, 0);
		assertTrue(ledger.tryClaimEdgeThisPass(fromWp, toWp, stand));
		assertFalse(ledger.tryClaimEdgeThisPass(toWp, fromWp, stand));
	}

	@Test
	public void movingReArmsTheClaim()
	{
		WorldPoint fromWp = new WorldPoint(2465, 3494, 0);
		WorldPoint toWp = new WorldPoint(2465, 3493, 0);
		assertTrue(ledger.tryClaimEdgeThisPass(fromWp, toWp, new WorldPoint(2465, 3494, 0)));
		assertTrue("retry should be allowed after moving away from same-edge attempt tile",
			ledger.tryClaimEdgeThisPass(fromWp, toWp, new WorldPoint(2462, 3491, 0)));
	}

	@Test
	public void aNewPassAndAReleaseEachReArmTheClaim()
	{
		WorldPoint fromWp = new WorldPoint(2465, 3494, 0);
		WorldPoint toWp = new WorldPoint(2465, 3493, 0);
		WorldPoint stand = new WorldPoint(2465, 3494, 0);
		ledger.tryClaimEdgeThisPass(fromWp, toWp, stand);
		ledger.releaseEdgeThisPass(fromWp, toWp);
		assertTrue("a released claim (no interaction happened) must be attemptable this pass",
			ledger.tryClaimEdgeThisPass(fromWp, toWp, stand));
		ledger.beginTailPass();
		assertTrue("a new pass owes a fresh budget", ledger.tryClaimEdgeThisPass(fromWp, toWp, stand));
	}

	// ---- the settle window, global cooldown and raw-scan focus (walk-runtime facets) ----

	@Test
	public void theSettleWindowStoresAndEndsEarly()
	{
		WorldPoint farSide = new WorldPoint(1876, 5239, 0);
		ledger.markSettling(farSide, T0, 900);
		assertEquals(T0, ledger.settleStartedAtMs());
		assertEquals(T0 + 900, ledger.settleUntilMs());
		assertEquals(farSide, ledger.settleFarSide());
		ledger.endSettleEarly();
		assertEquals("early end clears the ceiling, not the start (heartbeat still reads it)",
			0L, ledger.settleUntilMs());
		assertNull(ledger.settleFarSide());
		assertEquals(T0, ledger.settleStartedAtMs());
	}

	@Test
	public void theRawScanFocusIsABoundedCommitment()
	{
		ledger.setRawScanFocus(7, T0);
		assertEquals(Integer.valueOf(7), ledger.rawScanFocusDoorIdx());
		assertEquals(T0, ledger.rawScanFocusSetAtMs());
		ledger.recordRawScanFocusAttempt();
		ledger.recordRawScanFocusAttempt();
		assertEquals(2, ledger.rawScanFocusAttempts());
		ledger.clearRawScanFocus();
		assertNull(ledger.rawScanFocusDoorIdx());
		assertEquals(0, ledger.rawScanFocusAttempts());
	}

	@Test
	public void withdrawingTheClaimLeavesTheCooldownStanding()
	{
		// The crossed-axis clearing (conquered door) and the walk-start reset both withdraw the
		// claim; neither may forgive the anti-hammer cooldown. Two lifetimes, one owner.
		ledger.markAttempt(null, near, far, T0);
		ledger.clearLatestAttempt();
		assertNull(ledger.latestAttempt());
		assertTrue(ledger.shouldThrottleAttempt(null, near, far, COOLDOWN_MS, T0 + 100));
	}
}
