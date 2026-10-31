package net.runelite.client.plugins.microbot.util.tabs;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2TabSwitchToTest
{
	private MockedStatic<Microbot> microbot;
	private Client client;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		ClientThread clientThread = mock(ClientThread.class);
		when(clientThread.runOnClientThreadOptional(any(), anyLong())).thenAnswer(inv ->
		{
			Callable<?> callable = inv.getArgument(0);
			return Optional.ofNullable(callable.call());
		});
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv ->
		{
			Callable<?> callable = inv.getArgument(0);
			return Optional.ofNullable(callable.call());
		});
		microbot = Mockito.mockStatic(Microbot.class);
		microbot.when(Microbot::getClient).thenReturn(client);
		microbot.when(Microbot::getClientThread).thenReturn(clientThread);
		microbot.when(Microbot::isLoggedIn).thenAnswer(inv -> client.getGameState() == GameState.LOGGED_IN);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
	}

	@After
	public void tearDown()
	{
		microbot.close();
	}

	@Test
	public void alreadyOpenTabSucceedsEvenWhenTheCacheMissedTheChange()
	{
		when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenReturn(InterfaceTab.INVENTORY.getVarcIntIndex());
		assertTrue(Rs2Tab.switchTo(InterfaceTab.INVENTORY));

		when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenReturn(InterfaceTab.EQUIPMENT.getVarcIntIndex());
		assertTrue(Rs2Tab.switchTo(InterfaceTab.EQUIPMENT));

		verify(client, never()).runScript(anyInt(), any());
		assertEquals(InterfaceTab.EQUIPMENT, Rs2Tab.getCurrentTab());
	}

	@Test
	public void doesNotRunTheTabScriptWhenNotLoggedIn()
	{
		when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenReturn(InterfaceTab.INVENTORY.getVarcIntIndex());
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_OSRS_STRETCH);

		assertFalse(Rs2Tab.switchTo(InterfaceTab.SKILLS));

		verify(client, never()).getVarcIntValue(anyInt());
		verify(client, never()).runScript(anyInt(), any());
	}

	@Test
	public void liveTabReadReturnsTheCachedTabWhenTheClientLeavesTheLoggedInState()
	{
		when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenReturn(InterfaceTab.INVENTORY.getVarcIntIndex());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN, GameState.LOGGED_IN, GameState.LOGGED_IN, GameState.LOADING);
		when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_OSRS_STRETCH);
		assertTrue(Rs2Tab.switchTo(InterfaceTab.INVENTORY));

		assertFalse(Rs2Tab.switchTo(InterfaceTab.PRAYER));

		verify(client, times(1)).getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
		verify(client, never()).runScript(anyInt(), any());
		assertEquals(InterfaceTab.INVENTORY, Rs2Tab.getCurrentTab());
	}

	@Test
	public void doesNotRunTheTabScriptWhenTheTopLevelInterfaceIsNotLoaded()
	{
		when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenReturn(InterfaceTab.INVENTORY.getVarcIntIndex());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getTopLevelInterfaceId()).thenReturn(-1);

		assertFalse(Rs2Tab.switchTo(InterfaceTab.SKILLS));

		verify(client, never()).runScript(anyInt(), any());
	}

	@Test
	public void runsTheTabScriptWhenLoggedInWithTheTopLevelInterfaceLoaded()
	{
		AtomicInteger panel = new AtomicInteger(InterfaceTab.INVENTORY.getVarcIntIndex());
		when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenAnswer(inv -> panel.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_OSRS_STRETCH);
		doAnswer(inv ->
		{
			panel.set(InterfaceTab.SKILLS.getVarcIntIndex());
			return null;
		}).when(client).runScript(anyInt(), any());

		assertTrue(Rs2Tab.switchTo(InterfaceTab.SKILLS));

		verify(client).runScript(915, InterfaceTab.SKILLS.getVarcIntIndex());
	}
}
