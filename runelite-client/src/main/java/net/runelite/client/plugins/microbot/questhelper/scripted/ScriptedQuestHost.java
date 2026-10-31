package net.runelite.client.plugins.microbot.questhelper.scripted;

/** What the quest script offers a {@link ScriptedQuest}: the machinery that already works, reused rather than copied. */
public interface ScriptedQuestHost
{
	/** Where an earning routine stands after one call. */
	enum Progress
	{
		WORKING,
		DONE,
		FAILED
	}

	/**
	 * Runs one action of the Ectofuntus loop toward holding {@code tokens} Ecto-tokens (the entry toll of Port Phasmatys
	 * is added by the loop itself, and only when it still has to be paid).
	 */
	Progress earnEctoTokens(int tokens);

	/** Lets the preflight run once more: it fetches or buys whatever the bank and the inventory no longer hold. */
	void rearmPreflight();

	/** The quest is complete: stops the quest helper without an error (a running queue moves on by itself). */
	void finish(String reason);
}
