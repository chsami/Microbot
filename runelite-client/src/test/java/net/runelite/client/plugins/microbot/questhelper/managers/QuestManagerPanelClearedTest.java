package net.runelite.client.plugins.microbot.questhelper.managers;

import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.panel.QuestHelperPanel;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuestManagerPanelClearedTest
{
	private QuestManager manager;
	private QuestHelperPanel panel;
	private QuestHelper helper;

	@Before
	public void setUp()
	{
		manager = new QuestManager();
		manager.client = mock(Client.class);
		manager.clientThread = mock(ClientThread.class);
		manager.eventBus = mock(EventBus.class);
		manager.questBankManager = mock(QuestBankManager.class);
		manager.config = mock(QuestHelperConfig.class);
		when(manager.client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(manager.client.isClientThread()).thenReturn(true);

		panel = mock(QuestHelperPanel.class);
		helper = mock(QuestHelper.class);
		when(helper.getCurrentStep()).thenReturn(mock(QuestStep.class));
	}

	@Test
	public void deferredItemRequirementsUpdateSurvivesPanelBeingCleared() throws Exception
	{
		manager.startUp(panel);
		manager.startUpQuest(helper, false);
		SwingUtilities.invokeAndWait(() -> { });

		ArgumentCaptor<Runnable> deferred = ArgumentCaptor.forClass(Runnable.class);
		verify(manager.clientThread).invokeLater(deferred.capture());

		manager.shutDown();
		deferred.getValue().run();

		verify(panel).addQuest(helper, true);
		verify(panel).updateItemRequirements(manager.client);
	}

	@Test
	public void swingCallbackSkipsPanelWorkWhenPanelAlreadyCleared() throws Exception
	{
		AtomicReference<Throwable> uncaught = new AtomicReference<>();
		Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> uncaught.set(throwable));
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				manager.startUp(panel);
				manager.startUpQuest(helper, false);
				manager.shutDown();
			});
			SwingUtilities.invokeAndWait(() -> { });
		}
		finally
		{
			Thread.setDefaultUncaughtExceptionHandler(previous);
		}

		assertNull(uncaught.get());
		verify(panel, never()).addQuest(any(), anyBoolean());
		verify(manager.clientThread, never()).invokeLater(any(Runnable.class));
	}
}
