package net.runelite.client.plugins.microbot.ddchopper;

import java.util.Random;
import net.runelite.client.plugins.microbot.util.antiban.HumanizedTiming;

/**
 * DD Chopper's human pacing, built on the shared {@link HumanizedTiming} model: reaction delays before the next
 * tree, drop pacing, rare idle pauses and a mouse-noise value that grows with fatigue. Pure: time is passed in,
 * nothing sleeps, so it is testable with a seeded Random.
 */
public final class HumanPacing
{
	static final long REACTION_BASE_MS = 800;
	static final long REACTION_FLOOR_MS = 300;
	static final long REACTION_CAP_MS = 3_000;
	static final double DISTRACTED_CHANCE = 0.08;
	static final long DROP_BASE_MS = 230;
	static final long DROP_FLOOR_MS = 90;
	static final long IDLE_MIN_MS = 5_000;
	static final long IDLE_MAX_MS = 20_000;
	static final double MOUSE_NOISE_BASE = 5.0;
	static final double MOUSE_NOISE_FATIGUE_GAIN = 10.0;

	private final Random random;
	private final boolean enabled;
	private final double idleChancePercent;
	private final HumanizedTiming timing;

	public HumanPacing(Random random, boolean enabled, boolean fatigue, int idleChancePercent)
	{
		this.random = random;
		this.enabled = enabled;
		this.idleChancePercent = Math.max(0, Math.min(100, idleChancePercent));
		this.timing = new HumanizedTiming(random, enabled && fatigue);
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	/** Call every tick so fatigue rises while playing and decays on breaks. */
	public void update(long nowMs, boolean onBreak)
	{
		timing.update(nowMs, onBreak);
	}

	public double fatigue()
	{
		return timing.fatigue();
	}

	/** Mouse noise amplitude for Rs2Antiban.mouseFatigue: more fatigue, sloppier mouse. */
	public double mouseNoiseAmplitude()
	{
		return MOUSE_NOISE_BASE + MOUSE_NOISE_FATIGUE_GAIN * fatigue();
	}

	/**
	 * Wait before clicking the next tree: log-normal, skewed, floored at 300 ms, with an occasional distracted
	 * long wait, capped at 3 s. Zero when humanizing is off.
	 */
	public long reactionDelayMs()
	{
		if (!enabled)
		{
			return 0;
		}
		long d = timing.nextDelayMs(REACTION_BASE_MS);
		if (random.nextDouble() < DISTRACTED_CHANCE * (1.0 + fatigue()))
		{
			d = Math.round(d * (2.0 + random.nextDouble() * 1.5));
		}
		return Math.max(REACTION_FLOOR_MS, Math.min(REACTION_CAP_MS, d));
	}

	/** Delay between two drops; a plain short random value when humanizing is off. */
	public long dropDelayMs()
	{
		if (!enabled)
		{
			return 150 + random.nextInt(151);
		}
		return Math.max(DROP_FLOOR_MS, timing.nextDelayMs(DROP_BASE_MS));
	}

	/** Length of an idle pause (5 to 20 s) to take now, or 0 for none. Chance rises with fatigue. */
	public long idlePauseMs(long nowMs, boolean safe)
	{
		if (!enabled || idleChancePercent <= 0)
		{
			return 0;
		}
		return timing.maybeHesitate(nowMs, idleChancePercent / 100.0, safe, IDLE_MIN_MS, IDLE_MAX_MS);
	}
}
