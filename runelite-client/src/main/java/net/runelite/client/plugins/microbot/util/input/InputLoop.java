package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Runs one input gesture at a time, since there is one cursor, and unwinds it if the human takes
 * over partway through.
 *
 * <p>A lock, not a thread and queue: script threads should block, and the client thread already
 * defers through an executor. A dedicated thread would stall every script whenever one gesture
 * waited on a busy client thread.
 *
 * <p>The held-button set exists for drag. A triad is three events in microseconds and a
 * trajectory has nothing to unwind. Keys are not gesture-scoped and live in {@code Rs2Keyboard}.
 */
public final class InputLoop
{
	private static final ReentrantLock LOCK = new ReentrantLock();
	private static final ThreadLocal<Boolean> IN_GESTURE = ThreadLocal.withInitial(() -> Boolean.FALSE);

	/** Generous: a healthy gesture holds the lock for milliseconds, so this only catches a wedge. */
	private static volatile long lockTimeoutMs = 5_000L;

	/** Shrunk by tests so exercising the timeout is not a five second wait. */
	static void setLockTimeoutForTest(long millis)
	{
		lockTimeoutMs = millis;
	}

	public enum Result
	{
		COMPLETED,
		ABORTED
	}

	@FunctionalInterface
	public interface Gesture
	{
		void run(Emit emit);
	}

	private InputLoop()
	{
	}

	public static Result run(Gesture gesture)
	{
		// An inner run would get its own Emit, so an inner abort would clear targetMenu and release
		// its buttons while the outer gesture carried on believing it held them.
		if (IN_GESTURE.get())
		{
			throw new IllegalStateException("InputLoop.run is already running a gesture on this thread");
		}
		if (InputArbiter.isHuman())
		{
			return Result.ABORTED;
		}
		try
		{
			// Bounded: a gesture can block on the client thread, and unbounded one wedged gesture
			// would hold every other script's input for as long as it stayed wedged.
			if (!LOCK.tryLock(lockTimeoutMs, TimeUnit.MILLISECONDS))
			{
				return Result.ABORTED;
			}
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			return Result.ABORTED;
		}
		IN_GESTURE.set(Boolean.TRUE);
		try
		{
			// The human may have taken over while this thread waited, and a deferred client-thread
			// item may have been queued long before it ran.
			if (InputArbiter.isHuman())
			{
				return Result.ABORTED;
			}
			Emit emit = new Emit();
			try
			{
				gesture.run(emit);
				return Result.COMPLETED;
			}
			catch (Aborted aborted)
			{
				// The menu-aware click path arms targetMenu immediately before dispatching, so an
				// abort in between leaves the bot's entry loaded for the human's next click.
				Microbot.targetMenu = null;
				return Result.ABORTED;
			}
			finally
			{
				releaseHeldButtons(emit);
			}
		}
		finally
		{
			IN_GESTURE.remove();
			LOCK.unlock();
		}
	}

	/**
	 * Emits the matching RELEASED for anything still held, at the current point rather than where
	 * the gesture was heading. Also runs on normal completion and on an unexpected throw.
	 */
	private static void releaseHeldButtons(Emit emit)
	{
		if (emit.heldButtons.isEmpty())
		{
			return;
		}
		Point at = PointerState.get();
		for (Integer button : new ArrayList<>(emit.heldButtons))
		{
			AwtEmitter.released(at.getX(), at.getY(), button);
		}
		emit.heldButtons.clear();
	}

	/** The only way to emit inside a gesture; every method checks first. */
	public static final class Emit
	{
		private final Set<Integer> heldButtons = new LinkedHashSet<>();

		private Emit()
		{
		}

		public void move(int canvasX, int canvasY)
		{
			checkpoint();
			AwtEmitter.moved(canvasX, canvasY);
		}

		public void press(int canvasX, int canvasY, int button)
		{
			checkpoint();
			AwtEmitter.pressed(canvasX, canvasY, button);
			heldButtons.add(button);
		}

		public void release(int canvasX, int canvasY, int button)
		{
			checkpoint();
			AwtEmitter.released(canvasX, canvasY, button);
			heldButtons.remove(button);
		}

		public void click(int canvasX, int canvasY, int button)
		{
			checkpoint();
			AwtEmitter.clicked(canvasX, canvasY, button);
		}

		public void wheel(int canvasX, int canvasY, int wheelRotation, int unitsToScroll)
		{
			checkpoint();
			AwtEmitter.wheel(canvasX, canvasY, wheelRotation, unitsToScroll);
		}

		public void checkpoint()
		{
			if (InputArbiter.isHuman() || Thread.currentThread().isInterrupted())
			{
				throw ABORTED;
			}
		}
	}

	private static final Aborted ABORTED = new Aborted();

	/** Control flow, not an error. Shared and stack-traceless: aborting is an ordinary event. */
	private static final class Aborted extends RuntimeException
	{
		private Aborted()
		{
			super(null, null, false, false);
		}
	}
}
