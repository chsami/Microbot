package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.state.WalkerRouteState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class Rs2WalkerFinalTileTest
{
	private static final WorldPoint TARGET = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint TWO_TILES_OFF = TARGET.dx(-2);
	private static final List<WorldPoint> PATH = List.of(
		TARGET.dx(-4),
		TARGET.dx(-3),
		TWO_TILES_OFF,
		TARGET.dx(-1),
		TARGET);
	private static final List<WorldPoint> PATH_ENDING_SHORT = List.of(
		TARGET.dx(-5),
		TARGET.dx(-4),
		TARGET.dx(-3),
		TWO_TILES_OFF);

	private ShortestPathConfig originalConfig;
	private WorldPoint originalTarget;
	private ShortestPathConfig config;
	private MockedStatic<Rs2Tile> tile;
	private MockedStatic<Microbot> microbot;
	private MockedStatic<Rs2Player> player;
	private MockedStatic<Rs2PathApi> pathApi;
	private Rs2ActiveRouteStatus status;

	@Before
	public void setUp()
	{
		originalConfig = Rs2Walker.config;
		originalTarget = Rs2Walker.currentTarget;
		config = mock(ShortestPathConfig.class);
		Rs2Walker.config = config;
		Rs2Walker.clearWalkerDedupeForTesting();
		WalkerRouteState routeState = Rs2Walker.routeStateForTesting();
		routeState.lastRouteClearReason = "";
		routeState.interimTargetWp = null;
		routeState.interimTargetIdx = -1;
		routeState.stuckCount = 0;
		routeState.lastMovedTimeMs = System.currentTimeMillis();
		routeState.suppressTryDirectShortWalkUntilMs = 0L;

		tile = Mockito.mockStatic(Rs2Tile.class);
		tile.when(() -> Rs2Tile.isTileReachable(any(WorldPoint.class))).thenReturn(true);
		tile.when(() -> Rs2Tile.getReachableTilesFromTile(any(WorldPoint.class), anyInt()))
			.thenAnswer(invocation -> reachableAround(invocation.getArgument(0), invocation.getArgument(1)));

		microbot = Mockito.mockStatic(Microbot.class);
		microbot.when(Microbot::getClient).thenReturn(mock(Client.class));
		microbot.when(Microbot::isLoggedIn).thenReturn(true);
		ClientThread clientThread = mock(ClientThread.class);
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation -> {
			Callable<?> callable = invocation.getArgument(0);
			return Optional.ofNullable(callable.call());
		});
		microbot.when(Microbot::getClientThread).thenReturn(clientThread);

		player = Mockito.mockStatic(Rs2Player.class);
		player.when(Rs2Player::getWorldLocation).thenReturn(TWO_TILES_OFF);

		status = mock(Rs2ActiveRouteStatus.class);
		when(status.isPresent()).thenReturn(true);
		when(status.isReady()).thenReturn(true);
		when(status.getRawPath()).thenReturn(PATH_ENDING_SHORT);
		when(status.getWalkablePath()).thenReturn(PATH_ENDING_SHORT);
		when(status.getMetrics()).thenReturn(Optional.empty());
		pathApi = Mockito.mockStatic(Rs2PathApi.class);
		pathApi.when(Rs2PathApi::getActiveRouteStatus).thenReturn(status);
		pathApi.when(() -> Rs2PathApi.hasWalkableTileWithin(any(WorldPoint.class), anyInt())).thenReturn(true);

		Rs2Walker.currentTarget = TARGET;
	}

	@After
	public void tearDown()
	{
		pathApi.close();
		player.close();
		microbot.close();
		tile.close();
		Rs2Walker.currentTarget = originalTarget;
		Rs2Walker.config = originalConfig;
		Rs2Walker.clearWalkerDedupeForTesting();
	}

	@Test
	public void randomizeOnSpreadsFinishAroundGoalUpToThreeTiles()
	{
		when(config.randomizeFinalTile()).thenReturn(true);

		Set<WorldPoint> zone = Rs2Walker.randomizedFinishZone(TARGET, TARGET, 5);

		assertEquals(49, zone.size());
		assertTrue(zone.contains(TARGET.dx(3).dy(-3)));
		tile.verify(() -> Rs2Tile.getReachableTilesFromTile(eq(TARGET), eq(3)));
	}

	@Test
	public void randomizeOnHonoursSmallerFinishDistance()
	{
		when(config.randomizeFinalTile()).thenReturn(true);

		assertEquals(9, Rs2Walker.randomizedFinishZone(TARGET, TARGET, 1).size());
		assertEquals(Collections.singleton(TARGET), Rs2Walker.randomizedFinishZone(TARGET, TARGET, 0));
	}

	@Test
	public void randomizeOffKeepsExactGoal()
	{
		when(config.randomizeFinalTile()).thenReturn(false);

		assertTrue(Rs2Walker.randomizedFinishZone(TARGET, TARGET, 5).isEmpty());
		tile.verify(() -> Rs2Tile.getReachableTilesFromTile(any(WorldPoint.class), anyInt()), never());
	}

	@Test
	public void noZoneWhenGoalUnreachableOrPathEndsFarAway()
	{
		when(config.randomizeFinalTile()).thenReturn(true);

		assertTrue(Rs2Walker.randomizedFinishZone(TARGET, TARGET.dx(5), 5).isEmpty());
		assertTrue(Rs2Walker.randomizedFinishZone(TARGET, new WorldPoint(3222, 3218, 1), 5).isEmpty());

		tile.when(() -> Rs2Tile.isTileReachable(any(WorldPoint.class))).thenReturn(false);
		assertTrue(Rs2Walker.randomizedFinishZone(TARGET, TARGET, 5).isEmpty());
	}

	@Test
	public void pickFinishTileStaysInsideZone()
	{
		when(config.randomizeFinalTile()).thenReturn(true);
		Set<WorldPoint> zone = Rs2Walker.randomizedFinishZone(TARGET, TARGET, 5);

		Set<WorldPoint> picked = new HashSet<>();
		for (int i = 0; i < 500; i++)
		{
			WorldPoint finalTile = Rs2Walker.pickFinishTile(zone, TARGET);
			assertTrue(zone.contains(finalTile));
			picked.add(finalTile);
		}
		assertTrue(picked.stream().anyMatch(p -> p.distanceTo(TARGET) > 1));
	}

	@Test
	public void pickFinishTileFallsBackWhenZoneEmpty()
	{
		assertEquals(TARGET, Rs2Walker.pickFinishTile(Collections.emptySet(), TARGET));
	}

	@Test
	public void shortWalkInsideFinishZoneArrivesWithoutClicking()
	{
		when(config.randomizeFinalTile()).thenReturn(true);

		WalkerState state = Rs2WalkerMovement.tryDirectShortWalk(TARGET, 5, PATH, PATH, true);

		assertEquals(WalkerState.ARRIVED, state);
		assertEquals("rs2walker:tryDirectShortWalk:already-in-finish-zone", Rs2Walker.getLastRouteClearReason());
		assertNull(Rs2Walker.currentTarget);
		player.verify(Rs2Player::isMoving, never());
	}

	@Test
	public void pathEndpointInsideFinishZoneArrives()
	{
		when(config.randomizeFinalTile()).thenReturn(true);

		WalkerState state = Rs2Walker.processWalkForTesting(TARGET, 5);

		assertEquals(WalkerState.ARRIVED, state);
		assertEquals("rs2walker:processWalk:reached-path-endpoint", Rs2Walker.getLastRouteClearReason());
	}

	@Test
	public void pathEndpointTwoTilesShortWithRandomizeOffDoesNotArrive()
	{
		when(config.randomizeFinalTile()).thenReturn(false);

		WalkerState state = Rs2Walker.processWalkForTesting(TARGET, 5);

		assertEquals(WalkerState.EXIT, state);
		assertEquals("rs2walker:processWalk:reached-path-endpoint", Rs2Walker.getLastRouteClearReason());
	}

	private static HashMap<WorldPoint, Integer> reachableAround(WorldPoint origin, int radius)
	{
		HashMap<WorldPoint, Integer> tiles = new HashMap<>();
		for (int dx = -radius; dx <= radius; dx++)
		{
			for (int dy = -radius; dy <= radius; dy++)
			{
				tiles.put(origin.dx(dx).dy(dy), Math.max(Math.abs(dx), Math.abs(dy)));
			}
		}
		return tiles;
	}
}
