package net.runelite.client.plugins.microbot.questhelper.questhelpers;

import java.lang.reflect.Field;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class QuestHelperStartUpStepTest
{
	private QuestHelper helper;
	private EventBus eventBus;
	private QuestStep step;

	@Before
	public void setUp() throws Exception
	{
		helper = mock(QuestHelper.class, CALLS_REAL_METHODS);
		eventBus = mock(EventBus.class);
		step = mock(QuestStep.class);
		Field field = QuestHelper.class.getDeclaredField("eventBus");
		field.setAccessible(true);
		field.set(helper, eventBus);
	}

	@Test
	public void registersTheStartedStep()
	{
		helper.startUpStep(step);

		assertSame(step, helper.getCurrentStep());
		verify(eventBus).register(step);
		verify(eventBus, never()).unregister(step);
		verify(step, never()).shutDown();
	}

	@Test
	public void stepStoppedDuringStartUpIsNotLeftRegistered()
	{
		doAnswer(invocation ->
		{
			helper.shutDownStep();
			return null;
		}).when(step).startUp();

		helper.startUpStep(step);

		assertNull(helper.getCurrentStep());
		verify(eventBus, never()).register(null);
		InOrder order = inOrder(eventBus);
		order.verify(eventBus).register(step);
		order.verify(eventBus).unregister(step);
		verify(step, atLeastOnce()).shutDown();
	}
}
