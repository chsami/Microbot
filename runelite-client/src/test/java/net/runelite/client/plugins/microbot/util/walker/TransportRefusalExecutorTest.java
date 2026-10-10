package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Scene;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorDetection;
import net.runelite.client.plugins.microbot.util.walker.transport.TransportDispatchTrace;
import net.runelite.client.plugins.microbot.util.walker.transport.TransportRefusalLedger;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransportRefusalExecutorTest
{
	private static final WorldPoint ORIGIN = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint FALLBACK_ORIGIN = new WorldPoint(3222, 3220, 0);
	private static final WorldPoint DESTINATION = new WorldPoint(3222, 9618, 0);

	private final List<MockedStatic<?>> statics = new ArrayList<>();
	private final List<String> learnedEdges = new ArrayList<>();
	private PathfinderConfig config;
	private WorldPoint playerLocation;
	private boolean spiritTreeTravelEnabled;
	private boolean originReachable;
	private int travelClicks;
	private Rs2PathApi.ActiveTransportSelection selection;

	@Before
	public void setUp()
	{
		playerLocation = ORIGIN;
		spiritTreeTravelEnabled = true;
		originReachable = true;
		Rs2WalkerTransports.withdrawWalkScopedTransportBlocks();
	}

	@After
	public void tearDown()
	{
		Rs2WalkerTransports.withdrawWalkScopedTransportBlocks();
		for (int i = statics.size() - 1; i >= 0; i--)
		{
			statics.get(i).close();
		}
		statics.clear();
	}

	@Test
	public void spiritTreeThatClicksTravelButCannotPickTheDestinationIsStruckAndReplanned() throws Exception
	{
		Transport spiritTree = new Transport(ORIGIN, DESTINATION, "Tree Gnome Village",
			TransportType.SPIRIT_TREE, true, "Travel", "Spirit tree", 1293, 1);
		install(spiritTree);
		assertEquals(List.of(ORIGIN, DESTINATION), TransportRefusalStrikeOutTest.plan(config, ORIGIN, DESTINATION));

		Rs2WalkerTransports.DispatchResult first = dispatch();
		assertFalse(first.handled);
		assertEquals(TransportDispatchTrace.Verdict.REFUSED_AFTER_INTERACTION, first.verdict);
		assertFalse(first.struckOut);
		assertTrue("Travel was clicked", travelClicks > 0);
		assertEquals(1, Rs2WalkerTransports.transportRefusalStrikes(ORIGIN, DESTINATION));

		Rs2WalkerTransports.DispatchResult second = dispatch();
		assertTrue(second.struckOut);
		assertEquals(List.of(edge(ORIGIN, DESTINATION)), learnedEdges);
		assertReplanAvoidsRefusedEdge();
	}

	@Test
	public void recoveryOriginUnreachableBailIsStruckAfterRepeatedFallThroughs() throws Exception
	{
		Transport door = new Transport(ORIGIN, DESTINATION, "Zanaris shed",
			TransportType.TRANSPORT, true, "Open", "Door", 2406, 1);
		install(door);
		originReachable = false;
		playerLocation = new WorldPoint(ORIGIN.getX() - 1, ORIGIN.getY(), 0);
		List<WorldPoint> path = List.of(ORIGIN, DESTINATION);

		for (int pass = 1; pass < TransportRefusalLedger.FALL_THROUGH_STRIKE_LIMIT; pass++)
		{
			assertFalse("pass " + pass + " must fall through", Rs2WalkerTransports.dispatchRecoveryTransport(path, 0));
			assertEquals(pass, Rs2WalkerTransports.transportFallThroughs(ORIGIN, DESTINATION));
		}
		assertTrue(learnedEdges.isEmpty());

		assertTrue("third fall-through strikes the edge out", Rs2WalkerTransports.dispatchRecoveryTransport(path, 0));
		assertEquals(List.of(edge(ORIGIN, DESTINATION)), learnedEdges);
		assertReplanAvoidsRefusedEdge();
	}

	@Test
	public void recoveryDispatchClassifiesTheOriginUnreachableBailAsARefusal() throws Exception
	{
		Transport door = new Transport(ORIGIN, DESTINATION, "Zanaris shed",
			TransportType.TRANSPORT, true, "Open", "Door", 2406, 1);
		install(door);
		originReachable = false;

		Rs2WalkerTransports.DispatchResult result = dispatch();
		assertEquals(TransportDispatchTrace.Verdict.REFUSED_WITHOUT_INTERACTION, result.verdict);
		assertEquals(TransportDispatchTrace.Refusal.ORIGIN_UNREACHABLE, result.trace.refusal());
		assertEquals("a fall-through outside recovery is not counted", 0,
			Rs2WalkerTransports.transportFallThroughs(ORIGIN, DESTINATION));
	}

	@Test
	public void spiritTreeTravelSwitchedOffIsADeliberateSkipThatNeverStrikes() throws Exception
	{
		Transport spiritTree = new Transport(ORIGIN, DESTINATION, "Tree Gnome Village",
			TransportType.SPIRIT_TREE, true, "Travel", "Spirit tree", 1293, 1);
		install(spiritTree);
		spiritTreeTravelEnabled = false;
		List<WorldPoint> path = List.of(ORIGIN, DESTINATION);

		for (int pass = 0; pass < 6; pass++)
		{
			assertFalse(Rs2WalkerTransports.dispatchRecoveryTransport(path, 0));
		}
		assertEquals(0, travelClicks);
		assertEquals(0, Rs2WalkerTransports.transportRefusalStrikes(ORIGIN, DESTINATION));
		assertEquals(0, Rs2WalkerTransports.transportFallThroughs(ORIGIN, DESTINATION));
		assertTrue(learnedEdges.isEmpty());
		assertEquals(TransportDispatchTrace.DeliberateSkip.SPIRIT_TREE_TRAVEL_DISABLED, dispatch().trace.deliberateSkip());
	}

	@Test
	public void planeMismatchIsADeliberateSkipThatNeverStrikes() throws Exception
	{
		Transport door = new Transport(ORIGIN, DESTINATION, "Zanaris shed",
			TransportType.TRANSPORT, true, "Open", "Door", 2406, 1);
		install(door);
		originReachable = false;
		playerLocation = new WorldPoint(ORIGIN.getX(), ORIGIN.getY(), 1);
		List<WorldPoint> path = List.of(ORIGIN, DESTINATION);

		for (int pass = 0; pass < 6; pass++)
		{
			assertFalse(Rs2WalkerTransports.dispatchRecoveryTransport(path, 0));
		}
		assertEquals(0, Rs2WalkerTransports.transportFallThroughs(ORIGIN, DESTINATION));
		assertTrue(learnedEdges.isEmpty());
		assertEquals(TransportDispatchTrace.Verdict.DELIBERATE_SKIP, dispatch().verdict);
	}

	private Rs2WalkerTransports.DispatchResult dispatch()
	{
		return Rs2WalkerTransports.dispatchSelectedTransport(List.of(ORIGIN, DESTINATION), 0, selection, false);
	}

	private void assertReplanAvoidsRefusedEdge()
	{
		List<WorldPoint> after = TransportRefusalStrikeOutTest.plan(config, ORIGIN, DESTINATION);
		assertEquals(DESTINATION, after.get(after.size() - 1));
		assertTrue("replan must board the fallback transport", after.contains(FALLBACK_ORIGIN));
		for (int i = 0; i + 1 < after.size(); i++)
		{
			assertFalse("replan must not reuse the refused edge",
				after.get(i).equals(ORIGIN) && after.get(i + 1).equals(DESTINATION));
		}
	}

	private static String edge(WorldPoint origin, WorldPoint destination)
	{
		return origin + "->" + destination;
	}

	private void install(Transport refused) throws Exception
	{
		Transport fallback = new Transport(FALLBACK_ORIGIN, DESTINATION, "fallback", TransportType.TRANSPORT, false,
			"Climb-down", "Ladder", 1002, 5);
		Map<WorldPoint, Set<Transport>> catalog = new HashMap<>();
		catalog.put(ORIGIN, Set.of(refused));
		catalog.put(FALLBACK_ORIGIN, Set.of(fallback));
		config = TransportRefusalStrikeOutTest.configWithTransports(catalog);
		selection = selectionFor(refused);

		Client client = mock(Client.class);
		WorldView worldView = mock(WorldView.class);
		Scene scene = mock(Scene.class);
		when(client.getTopLevelWorldView()).thenReturn(worldView);
		when(client.isClientThread()).thenReturn(true);
		when(worldView.getScene()).thenReturn(scene);
		when(worldView.contains(any(WorldPoint.class))).thenReturn(true);
		ClientThread clientThread = mock(ClientThread.class);
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
			Callable<?> callable = inv.getArgument(0);
			return Optional.ofNullable(callable.call());
		});

		MockedStatic<Microbot> microbot = open(Microbot.class);
		microbot.when(Microbot::getClient).thenReturn(client);
		microbot.when(Microbot::getClientThread).thenReturn(clientThread);

		MockedStatic<Rs2Player> player = open(Rs2Player.class);
		player.when(Rs2Player::getWorldLocation).thenAnswer(inv -> playerLocation);

		MockedStatic<Rs2PathApi> pathApi = open(Rs2PathApi.class);
		pathApi.when(() -> Rs2PathApi.getActiveTransportSelection(any(), anyInt()))
			.thenAnswer(inv -> Optional.of(selection));
		pathApi.when(Rs2PathApi::isSpiritTreeTravelEnabled).thenAnswer(inv -> spiritTreeTravelEnabled);
		pathApi.when(() -> Rs2PathApi.learnBlockedEdge(any(), any(), anyString())).thenAnswer(inv -> {
			WorldPoint origin = inv.getArgument(0);
			WorldPoint destination = inv.getArgument(1);
			learnedEdges.add(edge(origin, destination));
			return config.learnBlockedEdge(origin, destination, inv.getArgument(2));
		});
		pathApi.when(() -> Rs2PathApi.unlearnBlockedEdge(any(), any(), anyString())).thenAnswer(inv ->
			config.unlearnBlockedEdge(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

		open(Rs2LeaguesTransport.class);
		open(Rs2Widget.class);
		open(Rs2DoorDetection.class);

		MockedStatic<Rs2Tile> tile = open(Rs2Tile.class);
		tile.when(() -> Rs2Tile.isTileReachable(any(WorldPoint.class))).thenAnswer(inv -> originReachable);

		GameObject sceneObject = mock(GameObject.class);
		when(sceneObject.getId()).thenReturn(refused.getObjectId());
		when(sceneObject.getWorldLocation()).thenReturn(ORIGIN);
		MockedStatic<Rs2GameObject> gameObjects = open(Rs2GameObject.class);
		gameObjects.when(() -> Rs2GameObject.findObjectById(anyInt())).thenReturn(sceneObject);
		gameObjects.when(() -> Rs2GameObject.interact(any(TileObject.class), eq("Travel"))).thenAnswer(inv -> {
			travelClicks++;
			return true;
		});
		gameObjects.when(() -> Rs2GameObject.getAll(any(), any(WorldPoint.class), anyInt())).thenAnswer(inv ->
			refused.getType() == TransportType.SPIRIT_TREE ? List.of() : List.of(sceneObject));
	}

	private <T> MockedStatic<T> open(Class<T> type)
	{
		MockedStatic<T> mocked = Mockito.mockStatic(type);
		statics.add(mocked);
		return mocked;
	}

	private static Rs2PathApi.ActiveTransportSelection selectionFor(Transport transport) throws Exception
	{
		Method toEdge = Rs2PathApi.class.getDeclaredMethod("toTransportEdge", Transport.class);
		toEdge.setAccessible(true);
		Object edge = toEdge.invoke(null, transport);
		Constructor<Rs2PathApi.ActiveTransportSelection> ctor = Rs2PathApi.ActiveTransportSelection.class
			.getDeclaredConstructor(int.class, edge.getClass(), Transport.class);
		ctor.setAccessible(true);
		return ctor.newInstance(0, edge, transport);
	}
}
