package net.runelite.client.plugins.microbot.util.walker.recovery;

import java.util.Objects;
import java.util.function.BiPredicate;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.QuestWalkRule;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Owns the nested walker call used to recover a failed object or NPC interaction.
 *
 * <p>The legacy walker may need to interact with a closed door while approaching the original
 * interaction target. The global can't-reach flag must remain set until that approach succeeds,
 * but it must not recursively start another global recovery from the door interaction on the
 * same thread.</p>
 */
@Slf4j
public final class CantReachTargetRecovery
{
	private static final ThreadLocal<Integer> RECOVERY_DEPTH = ThreadLocal.withInitial(() -> 0);

	private CantReachTargetRecovery()
	{
	}

	public static boolean shouldStart(boolean detectionEnabled, boolean cantReachTarget)
	{
		return detectionEnabled && cantReachTarget && RECOVERY_DEPTH.get() == 0;
	}

	public static boolean retryExhausted(int retries, int retryLimit)
	{
		return retries >= retryLimit;
	}

	public static int giveUp()
	{
		int retries = Microbot.cantReachTargetRetries;
		reset();
		return retries;
	}

	public static void reset()
	{
		Microbot.cantReachTarget = false;
		Microbot.cantReachTargetRetries = 0;
	}

	/**
	 * Gives up on a target the game keeps answering "I can't reach that!" for: logs it, clears the can't-reach latch and
	 * the retry counter, and leaves the scripts running. It used to pause every script and show a modal asking a person
	 * to look, which a hands-off run cannot answer and which left later clicks latched. The failed click just returns
	 * false, so the caller skips this target.
	 */
	public static void giveUp(String what)
	{
		int retries = giveUp();
		log.warn("[Interact] gave up on {} after {} can't-reach attempts; skipping it", what, retries);
		Microbot.status = "Skipping " + what + " that cannot be reached";
	}

	/**
	 * Called when the retries are used up. A hands-off quest run skips the target ({@link #giveUp(String)}); anything else
	 * keeps the old behaviour of pausing every script and asking a person to look, because a skilling or combat script
	 * would otherwise loop silently on a target it cannot reach.
	 */
	public static void onExhausted(String what)
	{
		if (QuestWalkRule.isQuestAutomationActive())
		{
			giveUp(what);
			return;
		}
		int retries = giveUp();
		Microbot.pauseAllScripts.compareAndSet(false, true);
		Microbot.showMessage("Your bot tried to interact with " + what + " for "
			+ retries + " times but failed. Please take a look at what is happening.");
	}

	public static boolean walkTo(WorldPoint target, int distance)
	{
		return walkTo(target, distance, Rs2Walker::walkTo);
	}

	static boolean walkTo(WorldPoint target, int distance, BiPredicate<WorldPoint, Integer> walker)
	{
		Objects.requireNonNull(walker, "recovery walker");
		int previousDepth = RECOVERY_DEPTH.get();
		RECOVERY_DEPTH.set(previousDepth + 1);
		try
		{
			return walker.test(target, distance);
		}
		finally
		{
			if (previousDepth == 0)
			{
				RECOVERY_DEPTH.remove();
			}
			else
			{
				RECOVERY_DEPTH.set(previousDepth);
			}
		}
	}
}
