package net.runelite.client.plugins.microbot.questhelper.training;

import java.util.List;

/** What a trainer may ask of the run: its current level and whether a purchase is allowed. */
public interface TrainerContext
{
	int level();

	/** Whether buying these items for about this many gp is allowed (approval, bank-only, max spend). */
	boolean mayBuy(List<String> itemNames, long totalGp);

	/** Counts a purchase that went ahead. */
	void spent(long totalGp);
}
