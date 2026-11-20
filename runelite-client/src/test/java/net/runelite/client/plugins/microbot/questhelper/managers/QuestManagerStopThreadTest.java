package net.runelite.client.plugins.microbot.questhelper.managers;

import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.panel.QuestHelperPanel;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuestManagerStopThreadTest
{
	private QuestManager manager;
	private QuestHelperPanel panel;
	private QuestHelper helper;

	@Before
	public void setUp() throws Exception
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
		when(helper.getQuest()).thenReturn(QuestHelperQuest.COOKS_ASSISTANT);

		manager.startUp(panel);
		manager.startUpQuest(helper, false);
		SwingUtilities.invokeAndWait(() -> { });
		clearInvocations(manager.clientThread, panel, helper);
	}

	@Test
	public void stopOnClientThreadRunsImmediately() throws Exception
	{
		manager.shutDownQuest(false);
		SwingUtilities.invokeAndWait(() -> { });

		verify(helper).shutDown();
		verify(panel).removeQuest();
		verify(manager.clientThread, never()).invokeLater(any(Runnable.class));
		assertNull(manager.getSelectedQuest());
	}

	@Test
	public void stopOffClientThreadWaitsForClientThread() throws Exception
	{
		when(manager.client.isClientThread()).thenReturn(false);

		manager.shutDownQuest(false);

		verify(helper, never()).shutDown();
		assertSame(helper, manager.getSelectedQuest());

		runDeferredStop();

		verify(helper).shutDown();
		verify(panel).removeQuest();
		assertNull(manager.getSelectedQuest());
	}

	@Test
	public void managerStopOffClientThreadStillClearsThePanelQuest() throws Exception
	{
		when(manager.client.isClientThread()).thenReturn(false);

		manager.shutDown();

		verify(helper, never()).shutDown();

		runDeferredStop();

		verify(helper).shutDown();
		verify(panel).removeQuest();
		assertNull(manager.getSelectedQuest());
	}

	private void runDeferredStop() throws Exception
	{
		ArgumentCaptor<Runnable> deferred = ArgumentCaptor.forClass(Runnable.class);
		verify(manager.clientThread).invokeLater(deferred.capture());
		when(manager.client.isClientThread()).thenReturn(true);
		deferred.getValue().run();
		SwingUtilities.invokeAndWait(() -> { });
	}
}
