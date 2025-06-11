package net.runelite.client.plugins.microbot.util.keyboard;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;

import java.awt.*;
import java.awt.event.KeyEvent;

import static java.awt.event.KeyEvent.CHAR_UNDEFINED;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

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
	 * Executes a given action with the canvas temporarily made focusable if it wasn't already.
	 * This ensures key events are properly dispatched to the game client.
	 *
	 * @param action the code to run while the canvas is focusable
	 */
	private static void withFocusCanvas(Runnable action)
	{
		Canvas canvas = getCanvas();
		boolean originalFocus = canvas.isFocusable();
		if (!originalFocus) canvas.setFocusable(true);

		try
		{
			action.run();
		}
		finally
		{
			if (!originalFocus) canvas.setFocusable(false);
		}
	}

	/**
	 * Dispatches a low-level KeyEvent to the canvas after a specified delay.
	 *
	 * @param id       the KeyEvent type (e.g. KEY_TYPED, KEY_PRESSED, etc.)
	 * @param keyCode  the key code from {@link KeyEvent}
	 * @param keyChar  the character to type, if applicable
	 * @param delay    the delay in milliseconds before the event time is set
	 */
	private static void dispatchKeyEvent(int id, int keyCode, char keyChar, int delay)
	{
		KeyEvent event = new KeyEvent(
			getCanvas(),
			id,
			System.currentTimeMillis() + delay,
			0,
			keyCode,
			keyChar
		);
		getCanvas().dispatchEvent(event);
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
				int delay = Rs2Random.between(20, 200);
				dispatchKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, c, delay);
				Global.sleep(100, 200);
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
			int delay = Rs2Random.between(20, 200);
			dispatchKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, key, delay);
		});
	}

	/**
	 * Simulates holding the Shift key using a KEY_PRESSED event.
	 */
	public static void holdShift()
	{
		withFocusCanvas(() -> {
			int delay = Rs2Random.between(20, 200);
			dispatchKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, CHAR_UNDEFINED, delay);
		});
	}

	/**
	 * Simulates releasing the Shift key using a KEY_RELEASED event.
	 */
	public static void releaseShift()
	{
		withFocusCanvas(() -> {
			int delay = Rs2Random.between(20, 200);
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
			int delay = Rs2Random.between(20, 200);
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
		keyHold(key);
		keyRelease(key);
	}

	/**
	 * Simulates pressing the Enter key.
	 * If the player is not logged in, this uses KEY_TYPED to avoid auto-login triggers.
	 */
	public static void enter()
	{
		// this is to avoid automatically login with jagex account when you are on the login screen
		if (!Microbot.isLoggedIn()) {
			dispatchKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, '\n', 0);
			return;
		}
		
		keyPress(KeyEvent.VK_ENTER);
	}
}