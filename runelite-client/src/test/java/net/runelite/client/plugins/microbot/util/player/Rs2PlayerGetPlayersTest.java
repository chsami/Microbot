package net.runelite.client.plugins.microbot.util.player;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("removal")
public class Rs2PlayerGetPlayersTest
{
	private final Map<String, Object> original = new HashMap<>();
	private final AtomicBoolean onClientThread = new AtomicBoolean();

	private Client client;
	private ClientThread clientThread;
	private Player local;
	private Player other;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		replace("client", client);
		replace("clientThread", clientThread);
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
		{
			Callable<?> callable = invocation.getArgument(0);
			onClientThread.set(true);
			try
			{
				return Optional.ofNullable(callable.call());
			}
			finally
			{
				onClientThread.set(false);
			}
		});

		local = mock(Player.class);
		other = mock(Player.class);
		IndexedObjectSet<Player> players = new IndexedObjectSet<>()
		{
			@Override
			public Player byIndex(int index)
			{
				return null;
			}

			@Override
			public Iterator<Player> iterator()
			{
				Iterator<Player> delegate = Arrays.asList(local, null, other).iterator();
				return new Iterator<>()
				{
					@Override
					public boolean hasNext()
					{
						return guarded(delegate.hasNext());
					}

					@Override
					public Player next()
					{
						return guarded(delegate.next());
					}
				};
			}
		};

		WorldView worldView = mock(WorldView.class);
		doReturn(players).when(worldView).players();
		when(client.getTopLevelWorldView()).thenAnswer(invocation -> guarded(worldView));
		when(client.getLocalPlayer()).thenAnswer(invocation -> guarded(local));
	}

	private void replace(String name, Object value) throws Exception
	{
		Field field = Microbot.class.getDeclaredField(name);
		field.setAccessible(true);
		original.put(name, field.get(null));
		field.set(null, value);
	}

	@After
	public void tearDown() throws Exception
	{
		for (Map.Entry<String, Object> entry : original.entrySet())
		{
			Field field = Microbot.class.getDeclaredField(entry.getKey());
			field.setAccessible(true);
			field.set(null, entry.getValue());
		}
	}

	private <T> T guarded(T value)
	{
		assertTrue("client state must only be read inside the client-thread task", onClientThread.get());
		return value;
	}

	@Test
	public void snapshotIsTakenInsideClientThreadTaskAndExcludesLocalPlayer()
	{
		List<Rs2PlayerModel> players = Rs2Player.getPlayers(p -> true, false).collect(Collectors.toList());

		assertEquals(1, players.size());
		assertSame(other, players.get(0).getPlayer());
		verify(clientThread, times(1)).runOnClientThreadOptional(any());
	}

	@Test
	public void includeLocalPlayerKeepsLocalPlayer()
	{
		assertEquals(2, Rs2Player.getPlayers(p -> true, true).count());
	}

	@Test
	public void predicateIsAppliedToSnapshot()
	{
		List<Rs2PlayerModel> players = Rs2Player.getPlayers(p -> p.getPlayer() == local, true).collect(Collectors.toList());

		assertEquals(1, players.size());
		assertSame(local, players.get(0).getPlayer());
	}

	@Test
	public void missingWorldViewReturnsEmptyStream()
	{
		doAnswer(invocation -> guarded(null)).when(client).getTopLevelWorldView();

		assertEquals(0, Rs2Player.getPlayers(p -> true, false).count());
	}

	@Test
	public void clientThreadFailureReturnsEmptyStreamWithoutReadingClient()
	{
		doReturn(Optional.empty()).when(clientThread).runOnClientThreadOptional(any());

		assertEquals(0, Rs2Player.getPlayers(p -> true, false).count());
		verifyNoInteractions(client);
	}
}
