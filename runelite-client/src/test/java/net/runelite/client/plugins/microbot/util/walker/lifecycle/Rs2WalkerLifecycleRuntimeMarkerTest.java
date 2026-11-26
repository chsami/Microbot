package net.runelite.client.plugins.microbot.util.walker.lifecycle;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class Rs2WalkerLifecycleRuntimeMarkerTest
{
	private static final WorldPoint TARGET = new WorldPoint(3222, 3218, 0);

	private MockedStatic<Microbot> microbot;
	private MockedStatic<Rs2PathApi> pathApi;
	private WorldMapPointManager wmm;

	@Before
	public void setUp()
	{
		wmm = mock(WorldMapPointManager.class);
		microbot = Mockito.mockStatic(Microbot.class);
		microbot.when(Microbot::isLoggedIn).thenReturn(true);
		microbot.when(Microbot::getClient).thenReturn(mock(Client.class));
		microbot.when(Microbot::getClientThread).thenReturn(mock(ClientThread.class));
		microbot.when(Microbot::getWorldMapPointManager).thenReturn(wmm);

		pathApi = Mockito.mockStatic(Rs2PathApi.class, Mockito.CALLS_REAL_METHODS);
		pathApi.when(Rs2PathApi::isStartPointSet).thenReturn(true);
		pathApi.when(Rs2PathApi::getActiveRouteStart).thenReturn(Optional.empty());
		pathApi.when(Rs2PathApi::getMarker).thenReturn(null);
		pathApi.when(() -> Rs2PathApi.setMarker(any())).thenAnswer(inv -> null);
		pathApi.when(() -> Rs2PathApi.setLastLocation(any())).thenAnswer(inv -> null);
	}

	@After
	public void tearDown()
	{
		pathApi.close();
		microbot.close();
	}

	@Test
	public void applyWalkerDestinationSurvivesMarkerClearedByAnotherThread()
	{
		Rs2WalkerLifecycleRuntime.applyWalkerDestination(TARGET);

		ArgumentCaptor<WorldMapPoint> published = ArgumentCaptor.forClass(WorldMapPoint.class);
		pathApi.verify(() -> Rs2PathApi.setMarker(published.capture()));
		ArgumentCaptor<WorldMapPoint> added = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(wmm).add(added.capture());

		WorldMapPoint marker = added.getValue();
		assertSame(published.getValue(), marker);
		assertEquals(TARGET, marker.getWorldPoint());
		assertEquals(TARGET, marker.getTarget());
		assertEquals("Target", marker.getName());
		assertTrue(marker.isJumpOnClick());
	}

	@Test
	public void applyWalkerDestinationDoesNotQueueOnSharedClientThreadSlot()
	{
		ClientThread clientThread = mock(ClientThread.class);
		microbot.when(Microbot::getClientThread).thenReturn(clientThread);

		Rs2WalkerLifecycleRuntime.applyWalkerDestination(TARGET);

		verify(clientThread, never()).runOnSeperateThread(any());
	}
}
