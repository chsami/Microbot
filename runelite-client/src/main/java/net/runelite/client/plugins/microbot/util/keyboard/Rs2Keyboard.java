package net.runelite.client.plugins.microbot.util.keyboard;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

import static java.awt.event.KeyEvent.CHAR_UNDEFINED;

/**
 * Utility class for simulating keyboard input.
 *
 * <p>{@link #typeString}, {@link #keyPress(char)}, {@link #keyPress(int)} and {@link #enter()} each
 * own the keyboard for their whole call. A call made while another thread is in one of them waits
 * for it to finish, up to {@link #DEFAULT_SEQUENCE_LOCK_TIMEOUT_MS}, and types nothing if it cannot
 * get the keyboard in that time. Calls on the client thread or the Swing event dispatch thread never
 * wait. A sequence whose thread is interrupted starts no further character, still releases any key it
 * has down, and leaves the interrupt flag set; one started on an interrupted thread types nothing.
 *
 * <p>Every key the bot presses, whichever call pressed it, is recorded until its release, so
 * {@link #releaseHeldKeys()} releases it on a pause or takeover and the pressing call then sends no
 * second release.
 */
public class Rs2Keyboard
{
	/**
	 * Bug fix: the one record of the keys the canvas has down from the bot, for every press this class
	 * sends. Only keys pressed through keyHold and holdShift used to be recorded; a key pressed inside
	 * typeString, keyPress or enter lived on the call stack, so the pause cleanup could not release it
	 * and the game saw it down for the whole pause.
	 */
	private static final Map<Integer, Press> KEYS_DOWN = new ConcurrentHashMap<>();

	/**
	 * Bug fix: script threads used to type at the same time, interleaving their characters, and every
	 * event's modifier mask is read from the shared {@link #KEYS_DOWN}, so one thread's Shift
	 * went out on another thread's keys. Held for a whole sequence, the way InputLoop serializes
	 * mouse gestures, but separate from InputLoop's lock so a long string never holds up a click.
	 * Fair, so a waiter is served in turn instead of timing out behind threads that keep re-taking it.
	 */
	private static final ReentrantLock SEQUENCE_LOCK = new ReentrantLock(true);
	/** Only catches a wedge: a waiter has to outlast a long string typed by another thread. */
	static final long DEFAULT_SEQUENCE_LOCK_TIMEOUT_MS = 30_000L;
	private static volatile long sequenceLockTimeoutMs = DEFAULT_SEQUENCE_LOCK_TIMEOUT_MS;

	/** Shrunk by tests so exercising the timeout is not a thirty second wait. */
	static void setSequenceLockTimeoutForTest(long millis)
	{
		sequenceLockTimeoutMs = millis;
	}

	/**
	 * One key press, from its claim in {@link #KEYS_DOWN} to its release. Nothing guards the record
	 * while a listener runs: the listeners are foreign code (RuneLite's key manager, plugin hotkeys, the game's
	 * handler), they run on script threads, the client thread, the event dispatch thread and the pause
	 * cleanup alike, and a listener that waits for the pause cleanup would deadlock against a sweep
	 * waiting for that listener's press. So the press and a release coming from another thread meet in
	 * this state instead, and whichever side the race leaves holding the release sends it.
	 */
	private static final class Press
	{
		/** Claimed: its KEY_PRESSED is being decided or delivered. */
		static final int PRESSING = 0;
		/** Its KEY_PRESSED reached the listeners. */
		static final int DOWN = 1;
		/** Released while PRESSING. The pressing call sends the KEY_RELEASED once its delivery returns. */
		static final int RELEASE_OWED = 2;
		/** Its KEY_RELEASED is being delivered. The entry leaves the record once that returns. */
		static final int RELEASING = 3;
		static final int HELD_PRESSING = 4;
		static final int HELD_DOWN = 5;

		/** Released with the same character it was pressed with, whoever releases it. */
		final char keyChar;
		final AtomicInteger state = new AtomicInteger(PRESSING);

		Press(char keyChar)
		{
			this.keyChar = keyChar;
		}

		void adopt()
		{
			if (!state.compareAndSet(PRESSING, HELD_PRESSING))
			{
				state.compareAndSet(DOWN, HELD_DOWN);
			}
		}
	}

	/** One call to typeString, keyPress or enter, with everything it dispatches. */
	private static final class Sequence
	{
		/**
		 * Taken when the sequence is requested, before any wait for the keyboard, so a pause that
		 * begins while it waits still stops it.
		 */
		final long generation;

		Sequence(long generation)
		{
			this.generation = generation;
		}

		boolean mayContinue()
		{
			return mayEmit(generation);
		}
	}

	/** The sequence running on this thread, absent outside one. */
	private static final ThreadLocal<Sequence> CURRENT_SEQUENCE = new ThreadLocal<>();

	/**
	 * Runs {@code body} as one sequence: it owns the keyboard from its first event to its last, and
	 * every event it sends belongs to the pause generation it was requested in. The only place the
	 * sequence lock is taken or released.
	 */
	private static void runSequence(Consumer<Sequence> body)
	{
		Sequence outer = CURRENT_SEQUENCE.get();
		if (outer != null)
		{
			// enter() calls keyPress: the inner call is part of the outer sequence, which already owns
			// the keyboard. Not refused on an interrupt either, which would cut enter() in half.
			body.accept(outer);
			return;
		}
		Sequence sequence = new Sequence(ScriptPauseController.getGeneration());
		// Checked before waiting: a call made while paused or during a takeover returns at once
		// instead of queueing behind a thread that a pause may have parked mid-sequence. An
		// interrupted thread starts nothing, the way InputLoop refuses a gesture on one.
		if (!sequence.mayContinue() || Thread.currentThread().isInterrupted())
		{
			return;
		}
		if (isUiThread())
		{
			// Neither UI thread may freeze behind another thread's typing, which can last the whole
			// bound. They take no lock and type at once, as before the lock existed.
			run(sequence, body);
			return;
		}
		try
		{
			if (!SEQUENCE_LOCK.tryLock(sequenceLockTimeoutMs, TimeUnit.MILLISECONDS))
			{
				return;
			}
		}
		catch (InterruptedException interrupted)
		{
			// Interrupted while waiting. tryLock cleared the flag, so put it back for the caller.
			Thread.currentThread().interrupt();
			return;
		}
		try
		{
			// A pause or takeover may have begun while this thread waited.
			if (sequence.mayContinue())
			{
				run(sequence, body);
			}
		}
		finally
		{
			SEQUENCE_LOCK.unlock();
		}
	}

	private static void run(Sequence sequence, Consumer<Sequence> body)
	{
		CURRENT_SEQUENCE.set(sequence);
		try
		{
			body.accept(sequence);
		}
		finally
		{
			CURRENT_SEQUENCE.remove();
		}
	}

	private static boolean isUiThread()
	{
		return Microbot.getClient().isClientThread() || SwingUtilities.isEventDispatchThread();
	}

	/**
	 * The pause generation this call's events belong to: its sequence's, or outside a sequence the
	 * generation when the call started, so a pause that begins and ends within one keyHold still
	 * stops it.
	 */
	private static long callGeneration()
	{
		Sequence sequence = CURRENT_SEQUENCE.get();
		return sequence != null ? sequence.generation : ScriptPauseController.getGeneration();
	}

	/**
	 * Gets the current game canvas.
	 *
	 * @return the game canvas
	 */
	private static Canvas getCanvas()
	{
		return Microbot.getClient().getCanvas();
	}

	/**
	 * The one decision every KEY_PRESSED and KEY_TYPED gets, made after the canvas lookup and right
	 * before the listeners. Keyboard emission never goes through InputLoop, so without it a takeover
	 * mid-typeString sprays the rest of the string into whatever the human just took over. Bug fix:
	 * it used to be made before the canvas lookup, so a pause landing in the lookup did not stop the
	 * event, and outside a sequence it ignored a pause that had already ended. KEY_RELEASED never gets
	 * it: a release is owed for a press that went out, and suppressing it would strand the key down.
	 */
	private static boolean mayEmit(long generation)
	{
		return !ScriptPauseController.isPaused()
			&& generation == ScriptPauseController.getGeneration()
			&& !InputArbiter.isHuman();
	}

	/**
	 * What one press call achieved, for the call that asked. Closing it releases the press this call
	 * sent and nothing else, so a try-with-resources around a character's press, KEY_TYPED and hold
	 * leaves nothing in the record on any path, a listener that throws included.
	 */
	private static final class KeyDown implements AutoCloseable
	{
		private final int keyCode;
		/** The press this call sent and so owes a release; null when it sent none. */
		private final Press sent;
		/** The key is down now, by this call's press or by another caller's. False when refused. */
		final boolean down;

		KeyDown(int keyCode, Press sent, boolean down)
		{
			this.keyCode = keyCode;
			this.sent = sent;
			this.down = down;
		}

		boolean sentPress()
		{
			return sent != null;
		}

		@Override
		public void close()
		{
			if (sent != null)
			{
				release(keyCode, sent, true);
			}
		}
	}

	/**
	 * Presses a key and records it as down. Sends nothing when the decision refuses it, and nothing
	 * either when the key is already down, by keyHold or by a press on another thread: a second
	 * KEY_PRESSED would leave the canvas expecting two releases, and that caller's press must stay
	 * down. The result says which, so a character can still be typed on a key that is already down.
	 */
	private static KeyDown press(int keyCode, char keyChar, long generation)
	{
		return press(keyCode, keyChar, generation, false);
	}

	private static KeyDown press(int keyCode, char keyChar, long generation, boolean hold)
	{
		Canvas canvas = getCanvas();
		Press press = new Press(keyChar);
		// Claimed before the decision. A pause sets its flag before its sweep runs, so either the
		// decision below sees the pause, or the sweep sees this claim and leaves the release to it.
		Press existing = KEYS_DOWN.putIfAbsent(keyCode, press);
		if (existing != null)
		{
			if (hold)
			{
				existing.adopt();
			}
			return new KeyDown(keyCode, null, true);
		}
		if (!mayEmit(generation))
		{
			KEYS_DOWN.remove(keyCode, press);
			return new KeyDown(keyCode, null, false);
		}
		try
		{
			deliver(canvas, KeyEvent.KEY_PRESSED, keyCode, keyChar);
		}
		catch (Throwable thrown)
		{
			// Bug fix: a listener that threw here used to leave the entry recorded as down with nobody
			// to release it, which blocked every later press of the key. Every listener got the press
			// (deliver reaches them all before rethrowing a RuntimeException), so every one gets its
			// release now. An Error stops delivery where it was thrown and is released the same way.
			press.state.set(Press.RELEASING);
			try
			{
				sendRelease(keyCode, press);
			}
			catch (Throwable alsoThrown)
			{
				thrown.addSuppressed(alsoThrown);
			}
			throw thrown;
		}
		if (!press.state.compareAndSet(Press.PRESSING, Press.DOWN)
			&& !press.state.compareAndSet(Press.HELD_PRESSING, Press.HELD_DOWN))
		{
			// A release came while the press was being delivered. Sent here, after the press it pairs
			// with, rather than by the releasing thread, which would have had to wait.
			press.state.set(Press.RELEASING);
			sendRelease(keyCode, press);
		}
		return new KeyDown(keyCode, press, true);
	}

	/**
	 * Releases a key the record has down, exactly once per press, whoever asks. Never waits: a key
	 * whose press is still being delivered is released by the pressing call when its delivery returns.
	 *
	 * @param expected the press to release, or null for whichever press has the key down. A call
	 *                 releasing its own press passes it, so a release that comes after the pause
	 *                 cleanup already released the key cannot release a later press of the same key.
	 */
	private static void release(int keyCode, Press expected, boolean own)
	{
		Press press = KEYS_DOWN.get(keyCode);
		if (press == null || (expected != null && press != expected))
		{
			return;
		}
		while (true)
		{
			int state = press.state.get();
			boolean held = state == Press.HELD_PRESSING || state == Press.HELD_DOWN;
			if (own && held)
			{
				return;
			}
			if (state == Press.DOWN || state == Press.HELD_DOWN)
			{
				if (press.state.compareAndSet(state, Press.RELEASING))
				{
					sendRelease(keyCode, press);
					return;
				}
			}
			else if (state == Press.PRESSING || state == Press.HELD_PRESSING)
			{
				if (press.state.compareAndSet(state, Press.RELEASE_OWED))
				{
					return;
				}
			}
			else
			{
				// Already owed or being released by another thread.
				return;
			}
		}
	}

	/** Leaves the record only after delivery, so the key cannot be pressed again before its release. */
	private static void sendRelease(int keyCode, Press press)
	{
		try
		{
			deliver(getCanvas(), KeyEvent.KEY_RELEASED, keyCode, press.keyChar);
		}
		finally
		{
			KEYS_DOWN.remove(keyCode, press);
		}
	}

	private static boolean type(char keyChar, long generation)
	{
		Canvas canvas = getCanvas();
		if (!mayEmit(generation))
		{
			return false;
		}
		deliver(canvas, KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, keyChar);
		return true;
	}

	/**
	 * Delivers a synthetic KeyEvent to the canvas's registered listeners directly,
	 * bypassing AWT's focus-aware dispatch pipeline. This is what eliminates the
	 * focus-steal that {@code Canvas.dispatchEvent} combined with a focusable flip
	 * used to cause.
	 *
	 * @param id       the KeyEvent type (e.g. KEY_TYPED, KEY_PRESSED, etc.)
	 * @param keyCode  the key code from {@link KeyEvent}
	 * @param keyChar  the character to type, if applicable
	 */
	private static void deliver(Canvas canvas, int id, int keyCode, char keyChar)
	{
		KeyEvent event = new KeyEvent(canvas, id, System.currentTimeMillis(), modifiersFor(id, keyCode), keyCode, keyChar);
		KeyListener[] listeners = canvas.getKeyListeners();
		// Every listener gets the event even when one throws, and the throw is rethrown afterwards:
		// a release must reach every listener that saw its press, and with presses delivered the same
		// way that is every listener, so none is left with a key down or sent a release it never
		// saw the press for.
		RuntimeException failure = null;
		// The arbiter's observe-only KeyListener is one of these, so without the guard the bot
		// reads its own keystrokes as a takeover.
		BotEventGuard.begin();
		try
		{
			for (KeyListener l : listeners)
			{
				try
				{
					switch (id)
					{
						case KeyEvent.KEY_TYPED:
							l.keyTyped(event);
							break;
						case KeyEvent.KEY_PRESSED:
							l.keyPressed(event);
							break;
						case KeyEvent.KEY_RELEASED:
							l.keyReleased(event);
							break;
					}
				}
				catch (RuntimeException thrown)
				{
					failure = collect(failure, thrown);
				}
			}
		}
		finally
		{
			BotEventGuard.end();
		}
		if (failure != null)
		{
			throw failure;
		}
	}

	/** Keeps the first failure and attaches later ones to it, so none is lost. */
	private static RuntimeException collect(RuntimeException first, RuntimeException next)
	{
		if (first == null)
		{
			return next;
		}
		first.addSuppressed(next);
		return first;
	}

	static final int KEY_HOLD_MIN_MS = 50;
	static final int KEY_HOLD_MAX_MS = 130;
	static final int KEY_GAP_MIN_MS = 40;
	static final int KEY_GAP_MAX_MS = 160;
	static final int SHIFT_LEAD_MIN_MS = 20;
	static final int SHIFT_LEAD_MAX_MS = 80;

	private static final String SHIFTED_DIGITS = ")!@#$%^&*(";
	private static final String PUNCTUATION_KEYS = "-=[]\\;',./`";
	private static final String SHIFTED_PUNCTUATION = "_+{}|:\"<>?~";
	private static final int[] PUNCTUATION_VKS = {
		KeyEvent.VK_MINUS, KeyEvent.VK_EQUALS, KeyEvent.VK_OPEN_BRACKET, KeyEvent.VK_CLOSE_BRACKET,
		KeyEvent.VK_BACK_SLASH, KeyEvent.VK_SEMICOLON, KeyEvent.VK_QUOTE, KeyEvent.VK_COMMA,
		KeyEvent.VK_PERIOD, KeyEvent.VK_SLASH, KeyEvent.VK_BACK_QUOTE
	};

	/**
	 * Types out a string character-by-character, each one pressed, typed and released the way a
	 * physical key produces it, with shift held around characters that need it.
	 *
	 * @param word the string to type into the game
	 */
	public static void typeString(final String word)
	{
		runSequence(sequence ->
		{
			for (char c : word.toCharArray())
			{
				// Bug fix: an interrupt only cut the sleeps short, so the rest of the string went
				// out at once instead of stopping.
				if (!sequence.mayContinue() || Thread.currentThread().isInterrupted() || !typeChar(c, sequence.generation))
				{
					return;
				}
				Global.sleep(Rs2Random.logNormalBounded(KEY_GAP_MIN_MS, KEY_GAP_MAX_MS));
			}
		});
	}

	/**
	 * Simulates pressing a single character.
	 *
	 * @param key the character to press
	 */
	public static void keyPress(final char key)
	{
		runSequence(sequence -> typeChar(key, sequence.generation));
	}

	private static boolean typeChar(char c, long generation)
	{
		int vk = keyCodeFor(c);
		if (vk == KeyEvent.VK_UNDEFINED)
		{
			return type(c, generation);
		}

		// Both presses are closed on every path, a listener that throws included, the letter before the
		// Shift around it. A null resource is skipped on close.
		try (KeyDown shift = needsShift(c) && !KEYS_DOWN.containsKey(KeyEvent.VK_SHIFT)
			? press(KeyEvent.VK_SHIFT, CHAR_UNDEFINED, generation)
			: null)
		{
			if (shift != null)
			{
				if (!shift.down)
				{
					return false;
				}
				Global.sleep(Rs2Random.logNormalBounded(SHIFT_LEAD_MIN_MS, SHIFT_LEAD_MAX_MS));
			}

			boolean typed = false;
			// An interrupt during the Shift lead stops the character here; the Shift is still released.
			if (!Thread.currentThread().isInterrupted())
			{
				try (KeyDown key = press(vk, c, generation))
				{
					// Bug fix: a key already down, held by keyHold or being pressed on another thread,
					// used to end the string here. The character is typed on it instead, without a
					// press or release of this call's own, so that caller's press stays down.
					typed = key.down;
					if (typed)
					{
						type(c, generation);
						if (key.sentPress())
						{
							Global.sleep(Rs2Random.logNormalBounded(KEY_HOLD_MIN_MS, KEY_HOLD_MAX_MS));
						}
					}
				}
			}

			if (shift != null && shift.sentPress())
			{
				Global.sleep(Rs2Random.logNormalBounded(SHIFT_LEAD_MIN_MS, SHIFT_LEAD_MAX_MS));
			}
			return typed;
		}
	}

	static int keyCodeFor(char c)
	{
		if (c >= 'a' && c <= 'z') return KeyEvent.VK_A + (c - 'a');
		if (c >= 'A' && c <= 'Z') return KeyEvent.VK_A + (c - 'A');
		if (c >= '0' && c <= '9') return KeyEvent.VK_0 + (c - '0');
		if (c == ' ') return KeyEvent.VK_SPACE;
		int shiftedDigit = SHIFTED_DIGITS.indexOf(c);
		if (shiftedDigit >= 0) return KeyEvent.VK_0 + shiftedDigit;
		int punctuation = PUNCTUATION_KEYS.indexOf(c);
		if (punctuation >= 0) return PUNCTUATION_VKS[punctuation];
		int shiftedPunctuation = SHIFTED_PUNCTUATION.indexOf(c);
		if (shiftedPunctuation >= 0) return PUNCTUATION_VKS[shiftedPunctuation];
		return KeyEvent.VK_UNDEFINED;
	}

	static boolean needsShift(char c)
	{
		return (c >= 'A' && c <= 'Z') || SHIFTED_DIGITS.indexOf(c) >= 0 || SHIFTED_PUNCTUATION.indexOf(c) >= 0;
	}

	static int modifiersFor(int id, int keyCode)
	{
		int mask = 0;
		for (Integer down : KEYS_DOWN.keySet())
		{
			mask |= modifierMask(down);
		}
		if (id == KeyEvent.KEY_PRESSED)
		{
			mask |= modifierMask(keyCode);
		}
		else if (id == KeyEvent.KEY_RELEASED)
		{
			mask &= ~modifierMask(keyCode);
		}
		return mask;
	}

	private static int modifierMask(int keyCode)
	{
		switch (keyCode)
		{
			case KeyEvent.VK_SHIFT:
				return InputEvent.SHIFT_DOWN_MASK;
			case KeyEvent.VK_CONTROL:
				return InputEvent.CTRL_DOWN_MASK;
			case KeyEvent.VK_ALT:
				return InputEvent.ALT_DOWN_MASK;
			default:
				return 0;
		}
	}

	/**
	 * Simulates holding the Shift key using a KEY_PRESSED event.
	 */
	public static void holdShift()
	{
		press(KeyEvent.VK_SHIFT, CHAR_UNDEFINED, callGeneration(), true);
	}

	/**
	 * Simulates releasing the Shift key using a KEY_RELEASED event.
	 */
	public static void releaseShift()
	{
		release(KeyEvent.VK_SHIFT, null, false);
	}

	/**
	 * Simulates holding down a key using a KEY_PRESSED event.
	 *
	 * @param key the key code from {@link KeyEvent}
	 */
	public static void keyHold(int key)
	{
		press(key, CHAR_UNDEFINED, callGeneration(), true);
	}

	/**
	 * Simulates releasing a key using a KEY_RELEASED event. Sends nothing for a key the bot does not
	 * have down: a takeover suppresses the press, and releasing anyway sends a RELEASED with no PRESSED
	 * before it, which no keyboard produces.
	 *
	 * @param key the key code from {@link KeyEvent}
	 */
	public static void keyRelease(int key)
	{
		release(key, null, false);
	}

	/**
	 * Releases every key the bot has down, whichever call pressed it, including a key a sequence on
	 * another thread is holding mid-character; that sequence then sends no second release. A hold
	 * spans arbitrary script code rather than one gesture, so InputLoop cannot unwind it;
	 * {@code Script.run} calls this on a takeover and the pause cleanup calls it when a pause begins.
	 *
	 * <p>Never waits, so it is safe on any thread: a key whose KEY_PRESSED another thread is still
	 * delivering is released by that thread as soon as the delivery returns. Idempotent and safe from
	 * several threads at once.
	 */
	public static void releaseHeldKeys()
	{
		// One listener throwing on one release must not leave the other keys down: the pause cleanup
		// runs this first and reports a failure through its future, so every key is released and the
		// first failure is rethrown afterwards.
		RuntimeException failure = null;
		for (Map.Entry<Integer, Press> down : KEYS_DOWN.entrySet())
		{
			try
			{
				release(down.getKey(), down.getValue(), false);
			}
			catch (RuntimeException thrown)
			{
				failure = collect(failure, thrown);
			}
		}
		if (failure != null)
		{
			throw failure;
		}
	}

	static boolean isKeyHeld(int key)
	{
		return KEYS_DOWN.containsKey(key);
	}

	/**
	 * Simulates pressing and releasing a key in quick succession.
	 *
	 * @param key the key code from {@link KeyEvent}
	 */
	public static void keyPress(int key)
	{
		runSequence(sequence ->
		{
			char typed = toTypedChar(key);
			try (KeyDown down = press(key, typed, sequence.generation))
			{
				// OSRS dialog options react to the KEY_TYPED, so only a printable key sends one, also
				// on a key that is already down. A non-printable key already down has nothing to send.
				if (down.down && typed != CHAR_UNDEFINED)
				{
					type(typed, sequence.generation);
				}
				if (down.sentPress())
				{
					Global.sleep(Rs2Random.logNormalBounded(KEY_HOLD_MIN_MS, KEY_HOLD_MAX_MS));
				}
			}
		});
	}

	/**
	 * Maps a Java {@link KeyEvent} virtual-key code to the printable character it produces
	 * when typed without modifiers. Returns {@link KeyEvent#CHAR_UNDEFINED} for non-printable keys.
	 *
	 * Needed because OSRS dialog option widgets react to {@code KEY_TYPED} char events,
	 * not the raw {@code KEY_PRESSED}/{@code KEY_RELEASED} pair that {@code keyHold}/{@code keyRelease} emit.
	 */
	static char toTypedChar(int vk)
	{
		if (vk >= KeyEvent.VK_0 && vk <= KeyEvent.VK_9) return (char) ('0' + (vk - KeyEvent.VK_0));
		if (vk >= KeyEvent.VK_A && vk <= KeyEvent.VK_Z) return (char) ('a' + (vk - KeyEvent.VK_A));
		if (vk == KeyEvent.VK_SPACE) return ' ';
		if (vk == KeyEvent.VK_ENTER) return '\n';
		if (vk == KeyEvent.VK_TAB) return '\t';
		if (vk == KeyEvent.VK_BACK_SPACE) return '\b';
		if (vk == KeyEvent.VK_ESCAPE) return (char) 27;
		return CHAR_UNDEFINED;
	}

	/**
	 * Simulates pressing the Enter key.
	 * If the player is not logged in, this uses KEY_TYPED to avoid auto-login triggers.
	 */
	public static void enter()
	{
		// One sequence around both, rather than only the one inside keyPress, so the trailing
		// KEY_TYPED belongs to it and no other thread's key can land between the two.
		runSequence(sequence ->
		{
			keyPress(KeyEvent.VK_ENTER);

			// This is to make sure the enter event gets released, because for some reason it
			// stays pressed and auto logs for jagex accounts
			resetEnter();
		});
	}

	/**
	 * Sends a KEY_TYPED event for the Enter key to ensure it is released.
	 */
	public static void resetEnter() {
		type('\n', callGeneration());
	}
}
