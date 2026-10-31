package net.runelite.client.plugins.microbot.util.walker.recovery;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Owns the nested walker call used to recover a failed object or NPC interaction.
 *
 * <p>The legacy walker may need to interact with a closed door while approaching the original
 * interaction target. The global can't-reach flag must remain set until that approach succeeds,
 * but it must not recursively start another global recovery from the door interaction on the
 * same thread.</p>
 */
public final class CantReachTargetRecovery
{
	static final long RETRY_WINDOW_MS = TimeUnit.MINUTES.toMillis(5);

	private static final ThreadLocal<Integer> RECOVERY_DEPTH = ThreadLocal.withInitial(() -> 0);
	private static volatile long lastAttemptAt;

	private CantReachTargetRecovery()
	{
	}

	public static boolean shouldStart(boolean detectionEnabled, boolean cantReachTarget)
	{
		return detectionEnabled && cantReachTarget && RECOVERY_DEPTH.get() == 0 && !onClientThread();
	}

	public static boolean retryExhausted(int retries, int retryLimit)
	{
		return retries >= retryLimit;
	}

	public static int retries()
	{
		return retries(System.currentTimeMillis());
	}

	static synchronized int retries(long now)
	{
		if (now - lastAttemptAt > RETRY_WINDOW_MS)
		{
			Microbot.cantReachTargetRetries = 0;
		}
		return Microbot.cantReachTargetRetries;
	}

	public static int countAttempt()
	{
		return countAttempt(System.currentTimeMillis());
	}

	static synchronized int countAttempt(long now)
	{
		int attempt = retries(now) + 1;
		Microbot.cantReachTargetRetries = attempt;
		lastAttemptAt = now;
		return attempt;
	}

	public static synchronized int giveUp()
	{
		int retries = Microbot.cantReachTargetRetries;
		reset();
		return retries;
	}

	public static synchronized void reset()
	{
		Microbot.cantReachTarget = false;
		Microbot.cantReachTargetRetries = 0;
	}

	public static @Nullable WorldPoint templateLocation(@Nullable WorldPoint location)
	{
		if (location == null)
		{
			return null;
		}
		WorldPoint template = Microbot.getClientThread().invoke(
			() -> templateLocation(Microbot.getClient().getTopLevelWorldView(), location));
		return template != null ? template : location;
	}

	static WorldPoint templateLocation(@Nullable WorldView worldView, WorldPoint location)
	{
		if (worldView == null || worldView.getScene() == null || !worldView.getScene().isInstance())
		{
			return location;
		}
		LocalPoint local = LocalPoint.fromWorld(worldView, location.getX(), location.getY());
		return local != null ? WorldPoint.fromLocalInstance(worldView.getScene(), local, location.getPlane()) : location;
	}

	private static boolean onClientThread()
	{
		Client client = Microbot.getClient();
		return client != null && client.isClientThread();
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
