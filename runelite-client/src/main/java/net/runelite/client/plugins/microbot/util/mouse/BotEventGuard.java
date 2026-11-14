package net.runelite.client.plugins.microbot.util.mouse;

public final class BotEventGuard {
	private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
	private static volatile long lastSyntheticNanos;
	private static volatile boolean everSynthetic;

	private BotEventGuard() {
	}

	public static void begin() {
		DEPTH.set(DEPTH.get() + 1);
		lastSyntheticNanos = System.nanoTime();
		everSynthetic = true;
	}

	public static long nanosSinceLastSynthetic() {
		return everSynthetic ? System.nanoTime() - lastSyntheticNanos : Long.MAX_VALUE;
	}

	public static void end() {
		int next = DEPTH.get() - 1;
		if (next <= 0) {
			DEPTH.remove();
		} else {
			DEPTH.set(next);
		}
	}

	public static boolean isSynthetic() {
		return DEPTH.get() > 0;
	}
}
