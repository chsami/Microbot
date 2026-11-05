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
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.awt.event.KeyEvent.CHAR_UNDEFINED;

/**
 * Utility class for simulating keyboard input.
 */
public class Rs2Keyboard
{
	/** Keys the bot currently holds down. See {@link #releaseHeldKeys()}. */
	private static final Set<Integer> HELD_KEYS = ConcurrentHashMap.newKeySet();
	private static final ThreadLocal<Long> INPUT_GENERATION = ThreadLocal.withInitial(() -> -1L);

	private static long beginInputSequence()
	{
		long previous = INPUT_GENERATION.get();
		if (previous < 0)
		{
			INPUT_GENERATION.set(ScriptPauseController.getGeneration());
		}
		return previous;
	}

	private static void endInputSequence(long previous)
	{
		if (previous < 0)
		{
			INPUT_GENERATION.remove();
		}
		else
		{
			INPUT_GENERATION.set(previous);
		}
	}

	private static boolean isInputAllowed()
	{
		long sequenceGeneration = INPUT_GENERATION.get();
		return !ScriptPauseController.isPaused()
			&& (sequenceGeneration < 0 || sequenceGeneration == ScriptPauseController.getGeneration());
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
	 * Delivers a synthetic KeyEvent to the canvas's registered listeners directly,
	 * bypassing AWT's focus-aware dispatch pipeline. This is what eliminates the
	 * focus-steal that {@code Canvas.dispatchEvent} combined with a focusable flip
	 * used to cause.
	 *
	 * @param id       the KeyEvent type (e.g. KEY_TYPED, KEY_PRESSED, etc.)
	 * @param keyCode  the key code from {@link KeyEvent}
	 * @param keyChar  the character to type, if applicable
	 */
	private static boolean dispatchKeyEvent(int id, int keyCode, char keyChar)
	{
		// Keyboard emission never goes through InputLoop, so this is its only checkpoint. Without
		// it a takeover mid-typeString sprays the rest of the string into whatever the human just
		// took over. RELEASED is exempt: releaseHeldKeys runs while the human owns input, and
		// suppressing it would strand a key down.
		if (id != KeyEvent.KEY_RELEASED && (!isInputAllowed() || InputArbiter.isHuman()))
		{
			return false;
		}
		Canvas canvas = getCanvas();
		KeyEvent event = new KeyEvent(canvas, id, System.currentTimeMillis(), modifiersFor(id, keyCode), keyCode, keyChar);
		KeyListener[] listeners = canvas.getKeyListeners();
		// The arbiter's observe-only KeyListener is one of these, so without the guard the bot
		// reads its own keystrokes as a takeover.
		BotEventGuard.begin();
		try
		{
			for (KeyListener l : listeners)
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
		}
		finally
		{
			BotEventGuard.end();
		}
		return true;
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
		long previous = beginInputSequence();
		try
		{
			for (char c : word.toCharArray())
			{
				if (!isInputAllowed() || !typeChar(c))
				{
					return;
				}
				Global.sleep(Rs2Random.logNormalBounded(KEY_GAP_MIN_MS, KEY_GAP_MAX_MS));
			}
		}
		finally
		{
			endInputSequence(previous);
		}
	}

	/**
	 * Simulates pressing a single character.
	 *
	 * @param key the character to press
	 */
	public static void keyPress(final char key)
	{
		long previous = beginInputSequence();
		try
		{
			typeChar(key);
		}
		finally
		{
			endInputSequence(previous);
		}
	}

	private static boolean typeChar(char c)
	{
		int vk = keyCodeFor(c);
		if (vk == KeyEvent.VK_UNDEFINED)
		{
			return dispatchKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, c);
		}

		boolean pressShift = needsShift(c) && !HELD_KEYS.contains(KeyEvent.VK_SHIFT);
		if (pressShift)
		{
			if (!hold(KeyEvent.VK_SHIFT))
			{
				return false;
			}
			Global.sleep(Rs2Random.logNormalBounded(SHIFT_LEAD_MIN_MS, SHIFT_LEAD_MAX_MS));
		}

		boolean pressed = dispatchKeyEvent(KeyEvent.KEY_PRESSED, vk, c);
		if (pressed)
		{
			dispatchKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, c);
			Global.sleep(Rs2Random.logNormalBounded(KEY_HOLD_MIN_MS, KEY_HOLD_MAX_MS));
			dispatchKeyEvent(KeyEvent.KEY_RELEASED, vk, c);
		}

		if (pressShift)
		{
			Global.sleep(Rs2Random.logNormalBounded(SHIFT_LEAD_MIN_MS, SHIFT_LEAD_MAX_MS));
			keyRelease(KeyEvent.VK_SHIFT);
		}
		return pressed;
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
		for (Integer held : HELD_KEYS)
		{
			mask |= modifierMask(held);
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
		hold(KeyEvent.VK_SHIFT);
	}

	/**
	 * Not locked against {@link #releaseHeldKeys()}. A takeover landing between the dispatch and
	 * the add leaves the key down until the next {@code Script.run} tick releases it, at most 600ms.
	 * A lock here would have to be held across the dispatch, which runs the client's own key
	 * handler, and stalling every script thread behind that is the worse failure.
	 */
	private static boolean hold(int key)
	{
		// Only if the press went out, or releaseHeldKeys would release a key never held.
		if (dispatchKeyEvent(KeyEvent.KEY_PRESSED, key, CHAR_UNDEFINED))
		{
			HELD_KEYS.add(key);
			return true;
		}
		return false;
	}

	/**
	 * Simulates releasing the Shift key using a KEY_RELEASED event.
	 */
	public static void releaseShift()
	{
		keyRelease(KeyEvent.VK_SHIFT);
	}

	/**
	 * Simulates holding down a key using a KEY_PRESSED event.
	 *
	 * @param key the key code from {@link KeyEvent}
	 */
	public static void keyHold(int key)
	{
		hold(key);
	}

	/**
	 * Simulates releasing a key using a KEY_RELEASED event.
	 *
	 * @param key the key code from {@link KeyEvent}
	 */
	public static void keyRelease(int key)
	{
		// Only for a key this class saw go down. A takeover suppresses the press, and releasing
		// anyway sends a RELEASED with no PRESSED before it, which no keyboard produces.
		if (!HELD_KEYS.remove(key))
		{
			return;
		}
		dispatchKeyEvent(KeyEvent.KEY_RELEASED, key, CHAR_UNDEFINED);
	}

	/**
	 * Releases every key the bot still holds. A hold spans arbitrary script code rather than one
	 * gesture, so InputLoop cannot unwind it; {@code Script.run} calls this on a takeover instead.
	 *
	 * <p>Idempotent and safe from several script threads at once.
	 */
	public static void releaseHeldKeys()
	{
		for (Integer key : new ArrayList<>(HELD_KEYS))
		{
			if (HELD_KEYS.remove(key))
			{
				dispatchKeyEvent(KeyEvent.KEY_RELEASED, key, CHAR_UNDEFINED);
			}
		}
	}

	static boolean isKeyHeld(int key)
	{
		return HELD_KEYS.contains(key);
	}

	/**
	 * Simulates pressing and releasing a key in quick succession.
	 *
	 * @param key the key code from {@link KeyEvent}
	 */
	public static void keyPress(int key)
	{
		long previous = beginInputSequence();
		try
		{
			char typed = toTypedChar(key);
			if (typed == CHAR_UNDEFINED)
			{
				if (hold(key))
				{
					Global.sleep(Rs2Random.logNormalBounded(KEY_HOLD_MIN_MS, KEY_HOLD_MAX_MS));
					keyRelease(key);
				}
				return;
			}

			// A suppressed press must not be followed by a release.
			if (!dispatchKeyEvent(KeyEvent.KEY_PRESSED, key, typed))
			{
				return;
			}
			dispatchKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, typed);
			Global.sleep(Rs2Random.logNormalBounded(KEY_HOLD_MIN_MS, KEY_HOLD_MAX_MS));
			// Unconditional: the press went out, so the release owes the client its pair even if the
			// human took over in between.
			dispatchKeyEvent(KeyEvent.KEY_RELEASED, key, typed);
		}
		finally
		{
			endInputSequence(previous);
		}
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
		long previous = beginInputSequence();
		try
		{
			keyPress(KeyEvent.VK_ENTER);

			// This is to make sure the enter event gets released, because for some reason it
			// stays pressed and auto logs for jagex accounts
			resetEnter();
		}
		finally
		{
			endInputSequence(previous);
		}

	}

	/**
	 * Sends a KEY_TYPED event for the Enter key to ensure it is released.
	 */
	public static void resetEnter() {
		dispatchKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, '\n');
	}
}
