package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.questhelper.managers.QuestManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class QuestHelperPluginShortestPathToggleTest
{
	@Mock private QuestHelperConfig config;
	@Mock private QuestManager questManager;
	@Mock private ClientThread clientThread;
	@InjectMocks private QuestHelperPlugin plugin;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);
	}

	private ConfigChanged toggle(String newValue)
	{
		ConfigChanged event = new ConfigChanged();
		event.setGroup(QuestHelperConfig.QUEST_HELPER_GROUP);
		event.setKey("useShortestPath");
		event.setNewValue(newValue);
		return event;
	}

	@Test
	public void enablingDefersActivationToClientThread()
	{
		plugin.onConfigChanged(toggle("true"));

		verify(questManager, never()).activateShortestPath();
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(clientThread).invokeLater(task.capture());

		task.getValue().run();

		verify(questManager).activateShortestPath();
	}

	@Test
	public void disablingDefersClearToClientThread()
	{
		plugin.onConfigChanged(toggle("false"));

		verify(questManager, never()).disableShortestPath();
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(clientThread).invokeLater(task.capture());

		task.getValue().run();

		verify(questManager).disableShortestPath();
		verify(questManager, never()).activateShortestPath();
	}
}
