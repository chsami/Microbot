package net.runelite.client.plugins.microbot.questhelper;

/** Where the gold for a Grand Exchange retry comes from: the inventory first, then the bank. Pure. */
public final class QuestCoins
{
	private QuestCoins()
	{
	}

	/**
	 * @param cost       gp the next offer needs
	 * @param invCoins   coins in the inventory
	 * @param bankCoins  coins in the bank, or a negative number when the bank has not been read
	 * @return gp to take out of the bank (0 when the inventory already covers it), or -1 when the inventory and the
	 *         bank together cannot pay for it
	 */
	public static long toWithdraw(long cost, long invCoins, long bankCoins)
	{
		long missing = cost - Math.max(0, invCoins);
		if (missing <= 0)
		{
			return 0;
		}
		if (bankCoins < 0)
		{
			return missing; // bank unknown: try, and check the inventory afterwards
		}
		return bankCoins >= missing ? missing : -1;
	}

	/** The stop message when the inventory and the bank together cannot pay for the retry. */
	public static String shortMessage(String item, long eachPrice, long cost, long invCoins, long bankCoins)
	{
		return String.format(
			"Not enough gp to retry %s at %,d gp each (need %,d, have %,d in the inventory and %,d in the bank)",
			item, eachPrice, cost, Math.max(0, invCoins), Math.max(0, bankCoins));
	}
}
