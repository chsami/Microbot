package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.questhelper.managers.QuestManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class QuestHelperPluginTurnOffTest
{
	@Mock private QuestHelperConfig config;
	@Mock private QuestManager questManager;
	@Mock private ClientThread clientThread;
	@Mock private QuestScript questScript;
	@InjectMocks private QuestHelperPlugin plugin;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);
	}

	private ConfigChanged turnOn(String newValue)
	{
		ConfigChanged event = new ConfigChanged();
		event.setGroup(QuestHelperConfig.QUEST_HELPER_GROUP);
		event.setKey("TurnOn");
		event.setNewValue(newValue);
		return event;
	}

	@Test
	public void turningOffCancelsTheInFlightIteration()
	{
		plugin.onConfigChanged(turnOn("false"));

		verify(questScript).cancelInFlightIteration();
	}

	@Test
	public void turningOnLeavesTheIterationRunning()
	{
		plugin.onConfigChanged(turnOn("true"));

		verify(questScript, never()).cancelInFlightIteration();
	}
}
