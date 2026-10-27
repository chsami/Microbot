package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

/**
 * Bank access shared by the axe supplier and the log handler. It counts consecutive failures to open the bank and,
 * once they exceed the limit, reports the bank as unavailable for the rest of the run so callers fall back to
 * something that needs no bank instead of walking there forever.
 */
final class BankTrip
{
	private final AxePlanner.Attempts failures = new AxePlanner.Attempts();
	private final Runnable onUnavailable;
	private boolean unavailable;

	BankTrip(Runnable onUnavailable)
	{
		this.onUnavailable = onUnavailable;
	}

	/** Blocks while walking to the nearest bank and opening it if needed; true once the bank interface is open. */
	boolean walkToAndOpen()
	{
		if (unavailable)
		{
			return false;
		}
		if (!Rs2Bank.isOpen())
		{
			Rs2Bank.walkToBankAndUseBank();
		}
		if (Rs2Bank.isOpen())
		{
			failures.progress(AxePlanner.Step.OPEN_BANK);
			return true;
		}
		if (!failures.tryStep(AxePlanner.Step.OPEN_BANK))
		{
			unavailable = true;
			Microbot.log("DD Chopper: the bank will not open, no longer using it");
			onUnavailable.run();
		}
		return false;
	}

	boolean isUnavailable()
	{
		return unavailable;
	}
}
