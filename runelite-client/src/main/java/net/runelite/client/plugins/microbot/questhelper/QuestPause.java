package net.runelite.client.plugins.microbot.questhelper;

import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.coords.WorldPoint;
import java.util.function.BooleanSupplier;

/**
 * The pause switch of the quest automation. Pausing freezes the script between actions and keeps the quest, step,
 * queue and preflight progress; resuming continues where it stopped. Stopping (automation switched off) overrides a
 * pause. Shared by the side panel button, the overlay hotkey and the Agent Server.
 */
public final class QuestPause
{
	/** What a long action should do before its next step. */
	public enum Action
	{
		/** Carry on. */
		RUN,
		/** Paused: wait here, do nothing. */
		WAIT,
		/** Stopped: give up now. */
		ABORT
	}

	private static final AtomicBoolean PAUSED = new AtomicBoolean(false);

	private QuestPause()
	{
	}

	public static boolean isPaused()
	{
		return PAUSED.get();
	}

	public static void pause()
	{
		PAUSED.set(true);
	}

	public static void resume()
	{
		PAUSED.set(false);
	}

	/** Flips the pause; returns the new state. */
	public static boolean toggle()
	{
		while (true)
		{
			boolean now = PAUSED.get();
			if (PAUSED.compareAndSet(now, !now))
			{
				return !now;
			}
		}
	}

	/**
	 * Whether a pause may cancel the walker's current walk: only when the quest script's own loop is the one
	 * walking. A walk started by the Agent Server or another plugin is left alone.
	 */
	public static boolean shouldCancelWalk(boolean questWalkInFlight, WorldPoint currentWalkTarget)
	{
		return questWalkInFlight && currentWalkTarget != null;
	}

	/** Stop beats pause; pause beats run. */
	public static Action decide(boolean automationOn, boolean paused)
	{
		if (!automationOn)
		{
			return Action.ABORT;
		}
		return paused ? Action.WAIT : Action.RUN;
	}

	/**
	 * Called between the actions of a long routine (a buying loop, a walk): blocks while paused and returns true when
	 * the routine may go on, false when automation was stopped or the thread was interrupted meanwhile.
	 */
	public static boolean awaitResume(BooleanSupplier automationOn, long pollMs)
	{
		while (true)
		{
			if (Thread.currentThread().isInterrupted())
			{
				return false;
			}
			switch (decide(automationOn.getAsBoolean(), PAUSED.get()))
			{
				case ABORT:
					return false;
				case RUN:
					return true;
				default:
					try
					{
						Thread.sleep(Math.max(1, pollMs));
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
						return false;
					}
			}
		}
	}
}
