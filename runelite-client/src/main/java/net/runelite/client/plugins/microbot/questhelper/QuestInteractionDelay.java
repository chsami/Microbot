package net.runelite.client.plugins.microbot.questhelper;

import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;
import net.runelite.client.plugins.microbot.util.antiban.HumanizedTiming;

/**
 * The occasional extra pause before a quest step clicks an object or an NPC, as a person hesitates before a ladder or a
 * door. The length comes from {@link HumanizedTiming} (log-normal, longer when tired); this class only decides whether
 * to pause and keeps the pause inside bounds the stuck watchdog never notices.
 */
public final class QuestInteractionDelay
{
	/** Chance of a pause with no fatigue; it rises to {@link #MAX_CHANCE} when fully tired. */
	static final double MIN_CHANCE = 0.30;
	static final double MAX_CHANCE = 0.50;
	/** Median pause before fatigue and the log-normal spread. */
	static final long BASE_DELAY_MS = 1_000;
	static final long MIN_DELAY_MS = 600;
	static final long MAX_DELAY_MS = 2_000;

	/** Step texts that mean the click is timed or repeated: never pause before these. */
	private static final Pattern TIMED_STEP_WORDS = Pattern.compile(
		"\\b(?:run|runs|escape|flee|dodge|quickly|avoid|hurry|pick the lock|pick-lock|lockpick|prison"
			+ "|repeatedly|keep|spam|until|trap|before)\\b");

	private QuestInteractionDelay()
	{
	}

	/**
	 * Whether a pause is allowed at all: not in combat, on a kill step, in a dialogue, at low health, or on a step whose
	 * text says the click is timed or repeated (running away, dodging, picking a prison lock).
	 */
	public static boolean allowed(boolean inCombat, boolean killStep, boolean inDialogue, boolean lowHealth,
		String stepText)
	{
		if (inCombat || killStep || inDialogue || lowHealth)
		{
			return false;
		}
		if (stepText == null)
		{
			return true;
		}
		return !TIMED_STEP_WORDS.matcher(stepText.toLowerCase(Locale.ROOT)).find();
	}

	/** The pause in milliseconds, or 0 for none. */
	public static long delayMs(HumanizedTiming timing, Random random, boolean allowed)
	{
		if (!allowed || timing == null)
		{
			return 0;
		}
		double chance = MIN_CHANCE + (MAX_CHANCE - MIN_CHANCE) * timing.fatigue();
		if (random.nextDouble() >= chance)
		{
			return 0;
		}
		return Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, timing.nextDelayMs(BASE_DELAY_MS)));
	}
}
