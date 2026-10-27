package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

/** Shared bank-access step for the collaborators that need the bank open. */
final class BankTrip
{
	private BankTrip()
	{
	}

	/** Blocks while walking to the nearest bank and opening it if needed; true once the bank interface is open. */
	static boolean walkToAndOpen()
	{
		if (!Rs2Bank.isOpen())
		{
			Rs2Bank.walkToBankAndUseBank();
		}
		return Rs2Bank.isOpen();
	}
}
