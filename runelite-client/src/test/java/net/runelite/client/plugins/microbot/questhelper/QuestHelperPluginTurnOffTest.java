package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.questhelper.managers.QuestBankManager;
import net.runelite.client.plugins.microbot.questhelper.managers.QuestManager;
import net.runelite.client.plugins.microbot.questhelper.managers.QuestOverlayManager;
import net.runelite.client.plugins.microbot.questhelper.runeliteobjects.extendedruneliteobjects.RuneliteObjectManager;
import net.runelite.client.plugins.microbot.questhelper.statemanagement.PlayerStateManager;
import net.runelite.client.plugins.microbot.questhelper.util.worldmap.WorldMapAreaManager;
import net.runelite.client.ui.ClientToolbar;
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
	@Mock private RuneliteObjectManager runeliteObjectManager;
	@Mock private EventBus eventBus;
	@Mock private PlayerStateManager playerStateManager;
	@Mock private WorldMapAreaManager worldMapAreaManager;
	@Mock private QuestOverlayManager questOverlayManager;
	@Mock private ClientToolbar clientToolbar;
	@Mock private QuestBankManager questBankManager;
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

	@Test
	public void stoppingThePluginStopsOnlyTheQuestLoop()
	{
		plugin.shutDown();

		verify(questScript).stopLoop();
		verify(questScript, never()).shutdown();
	}
}
