package net.runelite.client.plugins.microbot.questhelper.scripted;

import java.util.HashMap;
import java.util.Map;

/**
 * Bounds how often one scripted action may repeat without the quest moving on. The count restarts whenever the
 * progress signature changes (the quest stage or what the inventory holds), so only a stall is ever cut off.
 */
public final class AttemptBudget
{
	private final Map<String, Integer> used = new HashMap<>();
	private long signature = Long.MIN_VALUE;

	/**
	 * Spends one attempt of {@code key}.
	 *
	 * @param progress the current progress signature; a change forgets every earlier attempt
	 * @param limit    attempts allowed under one signature
	 * @return true while the action may run, false once its budget is used up
	 */
	public boolean tryUse(String key, long progress, int limit)
	{
		if (progress != signature)
		{
			signature = progress;
			used.clear();
		}
		int count = used.merge(key, 1, Integer::sum);
		return count <= limit;
	}


	public void reset()
	{
		used.clear();
		signature = Long.MIN_VALUE;
	}
}
