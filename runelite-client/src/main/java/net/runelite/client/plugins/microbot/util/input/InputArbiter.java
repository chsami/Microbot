package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Point;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Decides whether the bot or the human currently owns input.
 *
 * <p>No timer thread: {@link #isHuman()} evaluates the idle window on every read, so a gesture
 * holding a lock cannot delay the return to BOT.
 *
 * <p>Deliberately does not write {@code pauseAllScripts}. That flag has many writers and
 * {@code Script.shutdown()} clears it unconditionally, so an unrelated script finishing would
 * un-pause the bot mid-takeover, and the idle resume would cancel a Break Handler break.
 */
public final class InputArbiter
{
	private static final int DEFAULT_MOTION_THRESHOLD_PX = 10;
	private static final long DEFAULT_IDLE_RESUME_MS = 1800L;

	/** Not 0: {@link System#nanoTime()} has an arbitrary origin and may return it. */
	private static final long NEVER = Long.MIN_VALUE;

	private static final Set<Integer> REAL_BUTTONS_DOWN = ConcurrentHashMap.newKeySet();
	private static final Set<Integer> REAL_KEYS_DOWN = ConcurrentHashMap.newKeySet();
	private static final AtomicLong LAST_REAL_ACTIVITY_NANOS = new AtomicLong(NEVER);

	private static volatile int motionThresholdPx = DEFAULT_MOTION_THRESHOLD_PX;
	private static volatile long idleResumeMs = DEFAULT_IDLE_RESUME_MS;
	private static volatile boolean disabled;

	/**
	 * Monotonic nanos, not a wall clock. A wall clock steps backwards on NTP correction or a VM
	 * resuming, which makes the elapsed comparison negative, reads as "inside the idle window",
	 * and pins HUMAN until real time catches up.
	 */
	private static volatile LongSupplier clock = System::nanoTime;

	private InputArbiter()
	{
	}

	public static boolean isHuman()
	{
		if (disabled)
		{
			return false;
		}
		// Holding a button generates no further events, so the idle window alone would resume the
		// bot underneath the user's hand.
		if (!REAL_BUTTONS_DOWN.isEmpty() || !REAL_KEYS_DOWN.isEmpty())
		{
			return true;
		}
		long last = LAST_REAL_ACTIVITY_NANOS.get();
		if (last == NEVER)
		{
			return false;
		}
		long elapsed = clock.getAsLong() - last;
		// Negative should be impossible, but fail towards resuming: an early resume is visible and
		// recoverable, a permanent HUMAN is neither.
		return elapsed >= 0 && elapsed < idleResumeMs * 1_000_000L;
	}

	/**
	 * Measured from the last position the bot wrote, not the previous real event: per-event deltas
	 * never accumulate, so twenty 3px moves would be 60px of travel and never cross the threshold.
	 *
	 * <p>Before the first bot emit there is no reference and nothing to abort, so motion alone does
	 * not flip HUMAN. Buttons and keys still do.
	 */
	public static void onRealMove(int canvasX, int canvasY)
	{
		if (!PointerState.hasBotPoint())
		{
			return;
		}
		Point reference = PointerState.lastBotPoint();
		int dx = canvasX - reference.getX();
		int dy = canvasY - reference.getY();
		if ((long) dx * dx + (long) dy * dy >= (long) motionThresholdPx * motionThresholdPx)
		{
			markActivity();
		}
	}

	public static void onRealButtonPressed(int button)
	{
		REAL_BUTTONS_DOWN.add(button);
		markActivity();
	}

	// Activity first, then the set. The other order leaves a window where a reader sees nothing
	// held and the stale timestamp, and resumes mid-click.
	public static void onRealButtonReleased(int button)
	{
		markActivity();
		REAL_BUTTONS_DOWN.remove(button);
	}

	public static void onRealKeyPressed(int keyCode)
	{
		REAL_KEYS_DOWN.add(keyCode);
		markActivity();
	}

	public static void onRealKeyReleased(int keyCode)
	{
		markActivity();
		REAL_KEYS_DOWN.remove(keyCode);
	}

	public static boolean isRealButtonOrKeyDown()
	{
		return !REAL_BUTTONS_DOWN.isEmpty() || !REAL_KEYS_DOWN.isEmpty();
	}

	/**
	 * Drops everything held: a key down when the window deactivates never delivers its
	 * KEY_RELEASED, and since a held key suppresses idle resume the stale entry would pin HUMAN
	 * forever. Ctrl held for a screenshot is the usual way in. {@link CanvasInputListener#detach()}
	 * calls it too, since a listener that stops listening never hears those releases either.
	 *
	 * <p>Marks activity rather than clearing it, so the idle window runs from the focus loss.
	 */
	public static void onFocusLost()
	{
		if (REAL_BUTTONS_DOWN.isEmpty() && REAL_KEYS_DOWN.isEmpty())
		{
			return;
		}
		markActivity();
		REAL_BUTTONS_DOWN.clear();
		REAL_KEYS_DOWN.clear();
	}

	public static Set<Integer> realButtonsDown()
	{
		return new TreeSet<>(REAL_BUTTONS_DOWN);
	}

	public static Set<Integer> realKeysDown()
	{
		return new TreeSet<>(REAL_KEYS_DOWN);
	}

	/** Milliseconds since the last real input, or -1 if there has not been any. */
	public static long millisSinceRealActivity()
	{
		long last = LAST_REAL_ACTIVITY_NANOS.get();
		return last == NEVER ? -1L : (clock.getAsLong() - last) / 1_000_000L;
	}

	public static int motionThresholdPx()
	{
		return motionThresholdPx;
	}

	public static long idleResumeMs()
	{
		return idleResumeMs;
	}

	/** Forces BOT. Without it, one false positive stops every script with no way to recover. */
	public static void setDisabled(boolean value)
	{
		disabled = value;
	}

	public static boolean isDisabled()
	{
		return disabled;
	}

	// Clamped: the config fields behind these have no range on them, and a negative idle window
	// makes isHuman() false immediately after activity, switching the yield off with no sign of it.
	public static void setMotionThresholdPx(int value)
	{
		motionThresholdPx = Math.max(0, value);
	}

	public static void setIdleResumeMs(long value)
	{
		idleResumeMs = Math.max(0L, value);
	}

	/**
	 * Test fixture reset, public only because tests in other packages need it. Never call it from
	 * production: it discards the user's configured threshold and idle window.
	 */
	public static void resetForTest()
	{
		REAL_BUTTONS_DOWN.clear();
		REAL_KEYS_DOWN.clear();
		LAST_REAL_ACTIVITY_NANOS.set(NEVER);
		motionThresholdPx = DEFAULT_MOTION_THRESHOLD_PX;
		idleResumeMs = DEFAULT_IDLE_RESUME_MS;
		disabled = false;
		clock = System::nanoTime;
	}

	/** Supplies nanos. */
	static void setClockForTest(LongSupplier value)
	{
		clock = value;
	}

	private static void markActivity()
	{
		LAST_REAL_ACTIVITY_NANOS.set(clock.getAsLong());
	}
}
