package net.runelite.client.plugins.microbot.questhelper;

/** Fully automated mode: whether the purchases of one quest may go ahead without asking. Pure. */
public final class QuestSpendRules
{
	/** What to do with a quest's purchases. */
	public enum Decision
	{
		BUY, OVER_MAX_SPEND, CANNOT_AFFORD
	}

	private QuestSpendRules()
	{
	}

	/**
	 * @param total     estimated cost of the shortfall (the bank has already been counted)
	 * @param maxSpend  the per quest limit in gp
	 * @param coins     coins in the inventory and the bank
	 */
	public static Decision decide(long total, long maxSpend, long coins)
	{
		if (total > Math.max(0, maxSpend))
		{
			return Decision.OVER_MAX_SPEND;
		}
		return total > coins ? Decision.CANNOT_AFFORD : Decision.BUY;
	}
}
