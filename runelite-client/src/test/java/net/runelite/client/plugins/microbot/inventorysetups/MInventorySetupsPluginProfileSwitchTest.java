package net.runelite.client.plugins.microbot.inventorysetups;

import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsPluginPanel;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MInventorySetupsPluginProfileSwitchTest
{
	private Client client;
	private InventorySetupsPersistentDataManager dataManager;
	private BooleanSupplier queuedLoad;

	@Before
	public void setUp() throws Exception
	{
		MInventorySetupsPlugin plugin = new MInventorySetupsPlugin();
		client = mock(Client.class);
		ClientThread clientThread = mock(ClientThread.class);
		dataManager = mock(InventorySetupsPersistentDataManager.class);
		setField(plugin, "client", client);
		setField(plugin, "clientThread", clientThread);
		setField(plugin, "dataManager", dataManager);
		setField(plugin, "panel", mock(InventorySetupsPluginPanel.class));

		plugin.onProfileChanged(new ProfileChanged());

		ArgumentCaptor<BooleanSupplier> captor = ArgumentCaptor.forClass(BooleanSupplier.class);
		verify(clientThread).invokeLater(captor.capture());
		queuedLoad = captor.getValue();
	}

	private static void setField(Object target, String name, Object value) throws Exception
	{
		Field field = MInventorySetupsPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	@Test
	public void profileSwitchWaitsWhileClientIsStillLoading()
	{
		for (GameState state : new GameState[]{GameState.STARTING, GameState.UNKNOWN, GameState.LOADING})
		{
			when(client.getGameState()).thenReturn(state);
			assertFalse(queuedLoad.getAsBoolean());
		}
		verify(dataManager, never()).loadConfig();
	}

	@Test
	public void profileSwitchLoadsSetupsOnceClientIsReady()
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		assertTrue(queuedLoad.getAsBoolean());
		verify(dataManager).loadConfig();
	}
}
