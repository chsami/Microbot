package net.runelite.client.plugins.microbot.util.input;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Runs one input gesture at a time, since there is one cursor, and unwinds it if the human takes
 * over, a pause begins, its thread is interrupted or one of its events is refused partway through.
 *
 * <p>A gesture answers to the pause generation in force when it was requested, not when its turn
 * came, so a request made before a pause never dispatches after it. Its {@link Emit} is its context
 * and works only inside it.
 *
 * <p>A lock, not a thread and queue: script threads should block, and the client thread already
 * defers through an executor. A dedicated thread would stall every script whenever one gesture
 * waited on a busy client thread.
 *
 * <p>A NaturalMouse trajectory started outside any gesture runs as a gesture of its own, so it
 * takes turns here too. Called inside a gesture, it runs under that gesture's turn.
 *
 * <p>What a gesture must unwind is read from {@link AwtEmitter}'s one record of buttons that are
 * down, which notes the gesture whose press put each one there: drag and the hold inside a click
 * are the gestures that hold one, and a trajectory has nothing to unwind. A gesture releases only
 * its own press, and the pause cleanup releases from the same record, so each press comes up once.
 * Keys are not gesture-scoped and live in {@code Rs2Keyboard}.
 */
public final class InputLoop
{
	private static final ReentrantLock LOCK = new ReentrantLock();
	/** The gesture running on this thread, or null. Its Emit is the gesture's whole context. */
	private static final ThreadLocal<Emit> CURRENT = new ThreadLocal<>();

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

	public static boolean isInGesture()
	{
		return CURRENT.get() != null;
	}

	public static boolean isInputAllowed()
	{
		Emit gesture = CURRENT.get();
		return !ScriptPauseController.isPaused()
			&& (gesture == null || gesture.generation == ScriptPauseController.getGeneration());
	}

	/**
	 * The pause generation an emit made now answers to: its gesture's, or outside a gesture the
	 * current one, taken when the call starts.
	 */
	static long emitGeneration()
	{
		Emit gesture = CURRENT.get();
		return gesture != null ? gesture.generation : ScriptPauseController.getGeneration();
	}

	/**
	 * Whether a synthetic event other than a RELEASED may go out, for an emit that took
	 * {@code generation} from {@link #emitGeneration()}. {@link AwtEmitter} asks immediately before
	 * each dispatch.
	 */
	static boolean mayEmit(long generation)
	{
		return allowed(generation, isInGesture());
	}

	/**
	 * The one test behind every decision here. A gesture also yields to the human and to an
	 * interrupt; a direct emit outside one only ever answered to a pause.
	 *
	 * <p>Interrupt is checked because the waits inside a gesture return at once for an interrupted
	 * thread: before it was, an interrupted click still pressed, released and clicked at once.
	 * isInterrupted() leaves the flag for the caller to see.
	 */
	private static boolean allowed(long generation, boolean asGesture)
	{
		boolean beforeAnyPause = !ScriptPauseController.isPaused()
			&& generation == ScriptPauseController.getGeneration();
		if (!asGesture)
		{
			return beforeAnyPause;
		}
		return beforeAnyPause && !InputArbiter.isHuman() && !Thread.currentThread().isInterrupted();
	}

	/** Runs a gesture requested now, in the current pause generation. */
	public static Result run(Gesture gesture)
	{
		return run(ScriptPauseController.getGeneration(), gesture);
	}

	/**
	 * Runs a gesture requested earlier, in the pause generation that was current when it was
	 * requested, from {@link ScriptPauseController#getGeneration()}. For a request whose turn is
	 * deferred to another thread or comes after a wait for the lock: it never dispatches once a pause
	 * has begun after it was made, even if that pause is over by the time its turn comes. Public
	 * because the deferring callers, VirtualMouse and NaturalMouse, live in other packages.
	 */
	public static Result run(long generation, Gesture gesture)
	{
		// An inner run would get its own Emit, so an inner abort would clear targetMenu and release
		// its buttons while the outer gesture carried on believing it held them.
		if (CURRENT.get() != null)
		{
			throw new IllegalStateException("InputLoop.run is already running a gesture on this thread");
		}
		// Before waiting for the lock, so a request already stale does not queue for it.
		if (!allowed(generation, true))
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
		Emit emit = new Emit(generation);
		CURRENT.set(emit);
		try
		{
			// The human may have taken over while this thread waited, and a deferred client-thread
			// item may have been queued long before it ran.
			if (!allowed(generation, true))
			{
				return Result.ABORTED;
			}
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
				// At the current point rather than where the gesture was heading. Also runs on
				// normal completion and on an unexpected throw.
				AwtEmitter.releaseAllOwned(emit);
			}
		}
		finally
		{
			CURRENT.remove();
			LOCK.unlock();
		}
	}

	/**
	 * The only way to emit inside a gesture. A move, press, click or wheel either puts its event on
	 * the canvas or ends the gesture, on the emitter's own decision at the dispatch. A move to a
	 * point off the canvas that sends nothing, because the pointer is already out, carries on.
	 *
	 * <p>Bug fix: these used to check before the emit and then ignore what the emitter did, so an
	 * event refused for a missing canvas or a pause landing inside the emit was dropped while the
	 * gesture ran on, and a pause and resume inside the emit let the event out.
	 *
	 * <p>Works only inside its own gesture, on the thread running it. Bug fix: an Emit kept past its
	 * gesture, or handed to another thread, used to dispatch outside any turn on the pointer, under
	 * whatever gesture happened to be running there.
	 */
	public static final class Emit
	{
		/** The pause generation the gesture's request was made in. */
		private final long generation;

		private Emit(long generation)
		{
			this.generation = generation;
		}

		public void move(int canvasX, int canvasY)
		{
			requireOwnTurn();
			endUnless(AwtEmitter.tryMove(canvasX, canvasY));
		}

		// Recorded as this gesture's press only once delivered: one the game never saw is owed no
		// RELEASED.
		public void press(int canvasX, int canvasY, int button)
		{
			requireOwnTurn();
			endUnless(AwtEmitter.tryPress(canvasX, canvasY, button, this));
		}

		// The checkpoint is not a dispatch decision, since a RELEASED is never refused. It sends a
		// gesture the human took over to the sweep, which releases where the pointer now is rather
		// than at this stale point. Sends nothing unless this gesture's own press of the button is
		// down: not for one the pause cleanup has already released, nor for another caller's.
		public void release(int canvasX, int canvasY, int button)
		{
			checkpoint();
			AwtEmitter.releaseOwned(canvasX, canvasY, button, this);
		}

		public void click(int canvasX, int canvasY, int button)
		{
			requireOwnTurn();
			endUnless(AwtEmitter.tryClick(canvasX, canvasY, button));
		}

		public void wheel(int canvasX, int canvasY, int wheelRotation, int unitsToScroll)
		{
			requireOwnTurn();
			endUnless(AwtEmitter.tryWheel(canvasX, canvasY, wheelRotation, unitsToScroll));
		}

		public void checkpoint()
		{
			requireOwnTurn();
			if (!allowed(generation, true))
			{
				throw ABORTED;
			}
		}

		/**
		 * Misuse, so an exception the caller sees rather than the gesture's silent abort, which would
		 * end whatever unrelated gesture is running on this thread.
		 */
		private void requireOwnTurn()
		{
			if (CURRENT.get() != this)
			{
				throw new IllegalStateException("Emit used outside its own gesture");
			}
		}

		private static void endUnless(boolean accepted)
		{
			if (!accepted)
			{
				throw ABORTED;
			}
		}
	}

	/**
	 * Ends the gesture running on this thread, for an emit refused on a path that is handed no
	 * {@link Emit}: a NaturalMouse step goes through VirtualMouse to {@link AwtEmitter#moved}. The
	 * refusal is the decision; nothing is checked again. Does nothing outside a gesture.
	 */
	static void endGestureOnRefusal()
	{
		if (CURRENT.get() != null)
		{
			throw ABORTED;
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
