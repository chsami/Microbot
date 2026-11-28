package net.runelite.client.plugins.microbot.util.grandexchange;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;

public class Rs2GrandExchangePinTest
{
	/** The pad shows a tick after the clerk is clicked; it is not there when the click returns. */
	private static boolean open(boolean pinWorks, AtomicInteger entries)
	{
		Rs2NpcModel clerk = mock(Rs2NpcModel.class);
		AtomicInteger polls = new AtomicInteger();
		boolean[] entered = new boolean[1];
		try (MockedStatic<Rs2GrandExchange> ge = Mockito.mockStatic(Rs2GrandExchange.class, Answers.CALLS_REAL_METHODS);
			MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class);
			MockedStatic<Rs2Npc> npcs = Mockito.mockStatic(Rs2Npc.class);
			MockedStatic<Rs2Bank> bank = Mockito.mockStatic(Rs2Bank.class);
			MockedStatic<Global> global = Mockito.mockStatic(Global.class))
		{
			npcs.when(() -> Rs2Npc.getNpc("Grand Exchange Clerk")).thenReturn(clerk);
			npcs.when(() -> Rs2Npc.interact(clerk, "exchange")).thenReturn(true);
			ge.when(Rs2GrandExchange::isOpen).thenAnswer(i -> entered[0]);
			bank.when(Rs2Bank::isBankPinWidgetVisible).thenAnswer(i -> polls.get() > 1 && !entered[0]);
			bank.when(() -> Rs2Bank.handleBankPin()).thenAnswer(i ->
			{
				entries.incrementAndGet();
				entered[0] = pinWorks;
				return pinWorks;
			});
			global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt())).thenAnswer(i ->
			{
				for (int n = 0; n < 5; n++)
				{
					polls.incrementAndGet();
					if (((BooleanSupplier) i.getArgument(0)).getAsBoolean())
					{
						return true;
					}
				}
				return false;
			});
			return Rs2GrandExchange.openExchange();
		}
	}

	@Test
	public void aPadThatAppearsAfterTheClickIsAnswered()
	{
		AtomicInteger entries = new AtomicInteger();
		assertTrue(open(true, entries));
		assertEquals(1, entries.get());
	}

	@Test
	public void aFailedEntryIsReportedAndNotRepeated()
	{
		AtomicInteger entries = new AtomicInteger();
		assertFalse(open(false, entries));
		assertEquals(1, entries.get());
	}
}
