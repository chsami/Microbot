package net.runelite.client.plugins.microbot.util.antiban;

import java.util.Random;

/**
 * Pure timing model shared by scripts (quest automation, DD Chopper): skewed (log-normal) action delays, a fatigue value that rises with
 * continuous play and decays during breaks, and rare long hesitations. No client access, so it is unit testable
 * with a seeded Random. Time is passed in; nothing here sleeps.
 */
public class HumanizedTiming {
	public static final double FATIGUE_FULL_AFTER_MINUTES = 90.0;
	public static final double RECOVERY_MINUTES = 10.0;
	public static final long MIN_DELAY_MS = 60;
	public static final long MAX_DELAY_MS = 4_000;
	public static final int HESITATION_MIN_MS = 10_000;
	public static final int HESITATION_MAX_MS = 20_000;
	public static final long MIN_HESITATION_GAP_MS = 120_000;

	private final Random random;
	private final boolean fatigueEnabled;
	private double fatigue;
	private long lastUpdateMs = -1;
	private long lastHesitationMs = Long.MIN_VALUE / 2;

	public HumanizedTiming(Random random, boolean fatigueEnabled) {
		this.random = random;
		this.fatigueEnabled = fatigueEnabled;
	}

	/** Advances fatigue by the time since the last call; rises while working, decays while on a break. */
	public void update(long nowMs, boolean onBreak) {
		if (lastUpdateMs >= 0 && fatigueEnabled) {
			double minutes = Math.max(0, nowMs - lastUpdateMs) / 60_000.0;
			if (onBreak) {
				fatigue -= minutes / RECOVERY_MINUTES;
			} else {
				fatigue += minutes / FATIGUE_FULL_AFTER_MINUTES;
			}
			fatigue = Math.max(0.0, Math.min(1.0, fatigue));
		}
		lastUpdateMs = nowMs;
	}

	public double fatigue() {
		return fatigueEnabled ? fatigue : 0.0;
	}

	/** Log-normal delay around baseMs with a floor; fatigue shifts the median up to 60 percent and widens the tail. */
	public long nextDelayMs(long baseMs) {
		double f = fatigue();
		double sigma = 0.35 + 0.25 * f;
		double median = Math.max(1, baseMs) * (1.0 + 0.6 * f);
		double delay = median * Math.exp(sigma * random.nextGaussian());
		return Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, Math.round(delay)));
	}

	/** Chance of a hesitation on this decision: base chance scaled up to 3x by fatigue. */
	public double hesitationProbability(double baseChance) {
		double b = Math.max(0.0, Math.min(1.0, baseChance));
		return Math.min(1.0, b * (1.0 + 2.0 * fatigue()));
	}

	/** Returns a hesitation length in ms, or 0 for none. Never while unsafe, and not twice within two minutes. */
	public long maybeHesitate(long nowMs, double baseChance, boolean safe) {
		return maybeHesitate(nowMs, baseChance, safe, HESITATION_MIN_MS, HESITATION_MAX_MS);
	}

	/** As above with a custom hesitation length range. */
	public long maybeHesitate(long nowMs, double baseChance, boolean safe, long minMs, long maxMs) {
		if (!safe || nowMs - lastHesitationMs < MIN_HESITATION_GAP_MS) return 0;
		if (random.nextDouble() >= hesitationProbability(baseChance)) return 0;
		lastHesitationMs = nowMs;
		return minMs + (long) (random.nextDouble() * Math.max(0, maxMs - minMs));
	}
}
