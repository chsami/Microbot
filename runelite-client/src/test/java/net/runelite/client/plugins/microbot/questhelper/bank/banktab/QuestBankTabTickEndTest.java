package net.runelite.client.plugins.microbot.questhelper.bank.banktab;

import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuestBankTabTickEndTest
{
	@Mock private Client client;
	@Mock private ClientThread clientThread;
	@Mock private QuestBankTabInterface questBankTabInterface;
	@Mock private QuestHelperPlugin questHelper;
	@InjectMocks private QuestBankTab questBankTab;

	private Widget itemContainer;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);
		itemContainer = mock(Widget.class);
		when(itemContainer.getChildren()).thenReturn(new Widget[0]);
		when(itemContainer.getDynamicChildren()).thenReturn(new Widget[0]);
		when(client.getWidget(InterfaceID.Bankmain.ITEMS)).thenReturn(itemContainer);
		when(questBankTabInterface.isQuestTabActive()).thenReturn(true);
	}

	private Runnable captureTickEndTask()
	{
		questBankTab.onScriptPostFired(new ScriptPostFired(ScriptID.BANKMAIN_FINISHBUILDING));
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(clientThread).invokeAtTickEnd(task.capture());
		return task.getValue();
	}

	private void assertSkipped()
	{
		verify(questHelper, never()).getPluginBankTagItemsForSections();
		verify(itemContainer, never()).createChild(anyInt(), anyInt());
	}

	@Test
	public void skipsWhenContainerUnloadedBeforeTickEnd()
	{
		Runnable task = captureTickEndTask();
		when(client.getWidget(InterfaceID.Bankmain.ITEMS)).thenReturn(null);

		task.run();

		assertSkipped();
	}

	@Test
	public void skipsWhenContainerRebuiltBeforeTickEnd()
	{
		Runnable task = captureTickEndTask();
		when(client.getWidget(InterfaceID.Bankmain.ITEMS)).thenReturn(mock(Widget.class));

		task.run();

		assertSkipped();
	}

	@Test
	public void skipsWhenContainerHiddenBeforeTickEnd()
	{
		Runnable task = captureTickEndTask();
		when(itemContainer.isHidden()).thenReturn(true);

		task.run();

		assertSkipped();
	}

	@Test
	public void skipsWhenQuestTabClosedBeforeTickEnd()
	{
		Runnable task = captureTickEndTask();
		when(questBankTabInterface.isQuestTabActive()).thenReturn(false);

		task.run();

		assertSkipped();
	}

	@Test
	public void buildsLayoutWhenContainerStillLoaded()
	{
		Runnable task = captureTickEndTask();

		task.run();

		verify(questHelper).getPluginBankTagItemsForSections();
	}
}
