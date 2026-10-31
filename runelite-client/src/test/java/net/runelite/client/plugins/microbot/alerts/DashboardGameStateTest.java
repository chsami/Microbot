package net.runelite.client.plugins.microbot.alerts;

import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.game.ItemManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DashboardGameStateTest
{
	@Test
	public void loggedInStateCarriesThePlayerName()
	{
		JsonObject state = capture(GameState.LOGGED_IN, player("Iron Man"));

		assertEquals("LOGGED_IN", state.get("status").getAsString());
		assertEquals("Iron Man", state.get("playerName").getAsString());
	}

	@Test
	public void playerNameIsNullUntilTheNameLoads()
	{
		JsonObject state = capture(GameState.LOGGED_IN, player(null));

		assertTrue(state.get("playerName").isJsonNull());
		assertTrue(state.has("world"));
	}

	@Test
	public void playerNameIsLeftOutWhenNotLoggedIn()
	{
		assertFalse(capture(GameState.LOGIN_SCREEN, player("Iron Man")).has("playerName"));
		assertFalse(capture(GameState.LOGGED_IN, null).has("playerName"));
	}

	private static Player player(String name)
	{
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		return player;
	}

	private static JsonObject capture(GameState gameState, Player player)
	{
		Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(gameState);
		when(client.getLocalPlayer()).thenReturn(player);
		return DashboardGameState.capture(client, mock(ItemManager.class), new XpTracker());
	}
}
