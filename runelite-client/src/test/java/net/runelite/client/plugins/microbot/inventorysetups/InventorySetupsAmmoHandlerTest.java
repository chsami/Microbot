package net.runelite.client.plugins.microbot.inventorysetups;

import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsPluginPanel;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InventorySetupsAmmoHandlerTest
{
	@Test
	public void runePouchDataIfInContainerIsNullBeforeLoggedIn() throws Exception
	{
		final MInventorySetupsPlugin plugin = mock(MInventorySetupsPlugin.class);
		when(plugin.containerContainsItemFromSet(anySet(), anyList(), anyBoolean(), anyBoolean())).thenReturn(true);
		final Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOADING);
		final InventorySetupsAmmoHandler handler = new InventorySetupsAmmoHandler(plugin, client, mock(ItemManager.class),
			mock(InventorySetupsPluginPanel.class), mock(MInventorySetupsConfig.class));

		assertNull(handler.getRunePouchDataIfInContainer(Collections.emptyList()));
		verify(client, never()).getVarbitValue(anyInt());
	}

	@Test
	public void pouchChecksSkipItemLookupsBeforeLoggedIn() throws Exception
	{
		final MInventorySetupsPlugin plugin = mock(MInventorySetupsPlugin.class);
		final Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.STARTING);
		final InventorySetupsAmmoHandler handler = new InventorySetupsAmmoHandler(plugin, client, mock(ItemManager.class),
			mock(InventorySetupsPluginPanel.class), mock(MInventorySetupsConfig.class));

		assertNull(handler.getRunePouchDataIfInContainer(Collections.emptyList()));
		assertNull(handler.getBoltPouchDataIfInContainer(Collections.emptyList()));
		verify(plugin, never()).containerContainsItemFromSet(anySet(), anyList(), anyBoolean(), anyBoolean());
		verify(plugin, never()).containerContainsItem(anyInt(), anyList(), anyBoolean(), anyBoolean());
	}

	@Test
	public void runePouchDataIfInContainerReadsVarbitsWhenLoggedIn() throws Exception
	{
		final MInventorySetupsPlugin plugin = mock(MInventorySetupsPlugin.class);
		when(plugin.containerContainsItemFromSet(anySet(), anyList(), anyBoolean(), anyBoolean())).thenReturn(true);
		final Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		final InventorySetupsAmmoHandler handler = new InventorySetupsAmmoHandler(plugin, client, mock(ItemManager.class),
			mock(InventorySetupsPluginPanel.class), mock(MInventorySetupsConfig.class));

		final List<InventorySetupsItem> runePouch = handler.getRunePouchDataIfInContainer(Collections.emptyList());
		assertEquals(InventorySetupsRunePouchType.NORMAL.getSize(), runePouch.size());
		verify(client, atLeastOnce()).getVarbitValue(anyInt());
	}

	@Test
	public void boltPouchDataIfInContainerIsNullBeforeLoggedIn() throws Exception
	{
		final MInventorySetupsPlugin plugin = mock(MInventorySetupsPlugin.class);
		when(plugin.containerContainsItem(anyInt(), anyList(), anyBoolean(), anyBoolean())).thenReturn(true);
		final Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOADING);
		final InventorySetupsAmmoHandler handler = new InventorySetupsAmmoHandler(plugin, client, mock(ItemManager.class),
			mock(InventorySetupsPluginPanel.class), mock(MInventorySetupsConfig.class));

		assertNull(handler.getBoltPouchDataIfInContainer(Collections.emptyList()));
		verify(client, never()).getVarbitValue(anyInt());
	}

	@Test
	public void quiverDataIfInSetupIsNullBeforeLoggedIn() throws Exception
	{
		final MInventorySetupsPlugin plugin = mock(MInventorySetupsPlugin.class);
		when(plugin.containerContainsItemFromSet(anySet(), anyList(), anyBoolean(), anyBoolean())).thenReturn(true);
		final Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOADING);
		final InventorySetupsAmmoHandler handler = new InventorySetupsAmmoHandler(plugin, client, mock(ItemManager.class),
			mock(InventorySetupsPluginPanel.class), mock(MInventorySetupsConfig.class));

		assertNull(handler.getQuiverDataIfInSetup(Collections.emptyList(), Collections.emptyList()));
		verify(client, never()).getVarpValue(anyInt());
	}

	@Test
	public void specialContainersKeepSavedPouchesBeforeLoggedIn() throws Exception
	{
		final MInventorySetupsPlugin plugin = mock(MInventorySetupsPlugin.class);
		when(plugin.containerContainsItemFromSet(anySet(), anyList(), anyBoolean(), anyBoolean())).thenReturn(true);
		when(plugin.containerContainsItem(anyInt(), anyList(), anyBoolean(), anyBoolean())).thenReturn(true);
		final Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOADING);
		final InventorySetupsAmmoHandler handler = new InventorySetupsAmmoHandler(plugin, client, mock(ItemManager.class),
			mock(InventorySetupsPluginPanel.class), mock(MInventorySetupsConfig.class));
		final InventorySetup setup = mock(InventorySetup.class);

		handler.updateSpecialContainersInSetup(setup, Collections.emptyList(), Collections.emptyList());
		verify(setup, never()).updateRunePouch(anyList());
		verify(setup, never()).updateBoltPouch(anyList());
		verify(setup, never()).updateQuiver(anyList());
		verify(client, never()).getVarbitValue(anyInt());
		verify(client, never()).getVarpValue(anyInt());
	}
}
