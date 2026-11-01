package net.runelite.client.plugins.microbot.questhelper.steps;

import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.steps.tools.DefinedPoint;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DetailedQuestStepShortestPathTest
{
	private Client client;
	private EventBus eventBus;
	private DetailedQuestStep step;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		eventBus = mock(EventBus.class);
		QuestHelperConfig config = mock(QuestHelperConfig.class);
		when(config.useShortestPath()).thenReturn(true);
		QuestHelper questHelper = mock(QuestHelper.class);
		when(questHelper.getConfig()).thenReturn(config);

		step = new DetailedQuestStep(questHelper, new WorldPoint(3222, 3218, 0), "Walk");
		Field clientField = QuestStep.class.getDeclaredField("client");
		clientField.setAccessible(true);
		clientField.set(step, client);
		step.eventBus = eventBus;
	}

	@Test
	public void skipsPathWhenLocalPlayerIsMissing()
	{
		when(client.getLocalPlayer()).thenReturn(null);

		step.setShortestPath();

		verify(eventBus, never()).post(any());
	}

	@Test
	public void requestsPathFromPlayerLocation()
	{
		Player player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
		when(client.getLocalPlayer()).thenReturn(player);

		step.setShortestPath();

		verify(eventBus).post(any(PluginMessage.class));
	}

	@Test
	public void disableSkipsStepWithoutDefinedPoint()
	{
		step.setWorldPoint((DefinedPoint) null);

		step.disableShortestPath();

		verify(eventBus, never()).post(any());
	}

	@Test
	public void disableClearsPathForDefinedPoint()
	{
		step.disableShortestPath();

		verify(eventBus).post(any(PluginMessage.class));
	}
}
