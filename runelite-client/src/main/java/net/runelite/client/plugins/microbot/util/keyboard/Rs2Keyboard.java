package net.runelite.client.plugins.microbot.util.keyboard;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import static java.awt.event.KeyEvent.CHAR_UNDEFINED;

/**
 * Utility class for simulating keyboard input.
 */
public class Rs2Keyboard
{

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
	 * Kept as a no-op wrapper so existing call sites still compile / read naturally.
	 * The previous implementation toggled {@code Canvas.setFocusable(true)} around
	 * dispatch; on many window managers that call nudges the OS to grant focus to the
	 * game window, stealing it from whatever app the user was actually typing in.
	 * Direct-listener dispatch (see {@link #dispatchKeyEvent}) makes the toggle
	 * unnecessary, so this wrapper just runs the action.
	 */
	private static void withFocusCanvas(Runnable action)
	{
		action.run();
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
	 * @param delay    the delay in milliseconds before the event time is set
	 */
	private static void dispatchKeyEvent(int id, int keyCode, char keyChar, int delay)
	{
		Canvas canvas = getCanvas();
		KeyEvent event = new KeyEvent(canvas, id, System.currentTimeMillis() + delay, 0, keyCode, keyChar);
		KeyListener[] listeners = canvas.getKeyListeners();
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

	/**
	 * Types out a string character-by-character using KEY_TYPED events.
	 * Each character is sent with a short randomized delay and sleep between characters.
	 *
	 * @param word the string to type into the game
	 */
	public static void typeString(final String word)
	{
		withFocusCanvas(() -> {
			for (char c : word.toCharArray())
			{
				int delay = Rs2Random.logNormalBounded(20, 200);
				dispatchKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, c, delay);
				Global.sleep(Rs2Random.logNormalBounded(100, 200));
			}
		});
	}

	/**
	 * Simulates pressing a single character using a KEY_TYPED event.
	 *
	 * @param key the character to press
	 */
	public static void keyPress(final char key)
	{
		withFocusCanvas(() -> {
			int delay = Rs2Random.logNormalBounded(20, 200);
			dispatchKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, key, delay);
		});
	}

	/**
	 * Simulates holding the Shift key using a KEY_PRESSED event.
	 */
	public static void holdShift()
	{
		withFocusCanvas(() -> {
			int delay = Rs2Random.logNormalBounded(20, 200);
			dispatchKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, CHAR_UNDEFINED, delay);
		});
	}

	/**
	 * Simulates releasing the Shift key using a KEY_RELEASED event.
	 */
	public static void releaseShift()
	{
		withFocusCanvas(() -> {
			int delay = Rs2Random.logNormalBounded(20, 200);
			dispatchKeyEvent(KeyEvent.KEY_RELEASED, KeyEvent.VK_SHIFT, CHAR_UNDEFINED, delay);
		});
	}

	/**
	 * Simulates holding down a key using a KEY_PRESSED event.
	 *
	 * @param key the key code from {@link KeyEvent}
	 */
	public static void keyHold(int key)
	{
		withFocusCanvas(() ->
				dispatchKeyEvent(KeyEvent.KEY_PRESSED, key, CHAR_UNDEFINED, 0)
		);
	}

	/**
	 * Simulates releasing a key using a KEY_RELEASED event.
	 *
	 * @param key the key code from {@link KeyEvent}
	 */
	public static void keyRelease(int key)
	{
		withFocusCanvas(() -> {
			int delay = Rs2Random.logNormalBounded(20, 200);
			dispatchKeyEvent(KeyEvent.KEY_RELEASED, key, CHAR_UNDEFINED, delay);
		});
	}

	/**
	 * Simulates pressing and releasing a key in quick succession.
	 *
	 * @param key the key code from {@link KeyEvent}
	 */
	public static void keyPress(int key)
	{
		char typed = toTypedChar(key);
		if (typed == CHAR_UNDEFINED)
		{
			keyHold(key);
			keyRelease(key);
			return;
		}

		withFocusCanvas(() -> {
			dispatchKeyEvent(KeyEvent.KEY_PRESSED, key, typed, 0);
			int delay = Rs2Random.logNormalBounded(20, 200);
			dispatchKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, typed, delay);
			int releaseDelay = Rs2Random.between(20, 200);
			dispatchKeyEvent(KeyEvent.KEY_RELEASED, key, CHAR_UNDEFINED, releaseDelay);
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
		keyPress(KeyEvent.VK_ENTER);

		// This is to make sure the enter event gets released, because for some reason it
		// stays pressed and auto logs for jagex accounts
		resetEnter();

	}

	/**
	 * Sends a KEY_TYPED event for the Enter key to ensure it is released.
	 */
	public static void resetEnter() {
		withFocusCanvas(() -> dispatchKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, '\n', 10));
	}
}