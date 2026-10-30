package net.runelite.client.plugins.microbot.questhelper;

/**
 * Pure stage-escalation core of the quest self-healing watchdog. Fed one progress signature per
 * tick (player tile, step text, quest varp, inventory hash, in-dialogue); if it does not change for
 * {@code stageIntervalMs} the watchdog enters the next recovery stage, one stage per interval, up to
 * {@link Stage#GIVE_UP} at {@code 5 * stageIntervalMs}. Any signature change resets it. Ticks flagged
 * {@code suppressed} (short break, awaiting setup, combat recovery) restart the clock instead of
 * counting. No client state: unit-testable.
 */
public final class QuestStuckWatchdog
{
	public static final long DEFAULT_STAGE_INTERVAL_MS = 60_000L;

	public enum Stage
	{
		NONE,
		/** Cancel walk, clear interim route, re-plan. */
		REPLAN,
		/** Blacklist the failed edge/target for the session, re-plan. */
		BLACKLIST,
		/** Leave a non-zero plane / enclosed area via nearest stairs, ladder or door. */
		PLANE_ESCAPE,
		/** Teleport toward the step's region. */
		TELEPORT,
		/** Stop the quester with a clear reason. */
		GIVE_UP
	}

	private final long stageIntervalMs;
	private long lastSignature;
	private boolean haveSignature;
	private long lastProgressAtMs;
	private Stage stage = Stage.NONE;

	public QuestStuckWatchdog(long stageIntervalMs)
	{
		this.stageIntervalMs = Math.max(1L, stageIntervalMs);
	}

	/**
	 * @return the stage newly entered by this tick, or {@code null} if nothing changed.
	 */
	public synchronized Stage tick(long nowMs, long signature, boolean suppressed)
	{
		if (suppressed || !haveSignature || signature != lastSignature)
		{
			lastSignature = signature;
			haveSignature = true;
			lastProgressAtMs = nowMs;
			stage = Stage.NONE;
			return null;
		}
		Stage[] all = Stage.values();
		int next = stage.ordinal() + 1;
		if (next >= all.length || nowMs - lastProgressAtMs < next * stageIntervalMs)
		{
			return null;
		}
		stage = all[next];
		return stage;
	}

	public synchronized void reset(long nowMs)
	{
		haveSignature = false;
		lastProgressAtMs = nowMs;
		stage = Stage.NONE;
	}

	public synchronized Stage current()
	{
		return stage;
	}

	public synchronized long stuckForMs(long nowMs)
	{
		return haveSignature ? Math.max(0L, nowMs - lastProgressAtMs) : 0L;
	}
}
