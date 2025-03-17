package net.runelite.client.plugins.microbot.kaas.pyrefox.helpers;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

public class BankHelper
{
	public static boolean walkToAndOpenBank()
	{
		if (Rs2Bank.isOpen())
			return true;

		// Wait until our bank is opened.
		boolean isNearBank = Rs2Bank.walkToBank();
		Rs2Player.waitForWalking();
		if (!isNearBank || !Rs2Bank.isNearBank(6))
		{
			Microbot.status = "Walking to bank.";
			return false;
		}

		if (!Rs2Bank.isOpen()) {
			Microbot.status = "Opening bank.";
			Rs2Bank.useBank();
			Rs2Player.waitForWalking();
			return false;
		}
		return true;
	}

	public static boolean walkToAndOpenBank(BankLocation bankLocation)
	{
		if (Rs2Bank.isOpen())
			return true;

		// Wait until our bank is opened.
		boolean isNearBank = Rs2Bank.walkToBankAndUseBank(bankLocation);
		Rs2Player.waitForWalking();

		if (!isNearBank || !Rs2Bank.isNearBank(bankLocation, 6))
		{
			Microbot.status = "Walking to bank.";
			return false;
		}

		if (!Rs2Bank.isOpen()) {
			Microbot.status = "Opening bank.";
			Rs2Bank.useBank();
			Rs2Player.waitForWalking();
			return false;
		}
		return true;
	}
}
