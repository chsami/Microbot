package net.runelite.client.plugins.microbot;

import java.awt.Canvas;
import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.util.input.AwtEmitter;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Manual input goes off while a script runs and comes back after, without overriding the user's own choice. */
public class InputSelectorScriptsTest
{
	private Component game;
	private Object previous;
	private Object previousClient;
	private InputSelector selector;

	@Before
	public void before() throws Exception
	{
		assertFalse("fixture: a previous test left scripts paused", ScriptPauseController.isPaused());
		game = new Canvas();
		previous = swapGame(game);
		// pausing releases held keys and buttons through the game client, and puts focus on its canvas
		Client client = mock(Client.class);
		when(client.getCanvas()).thenReturn(new Canvas());
		previousClient = swapClient(client);
		focusableBefore = focusableSetting().getBoolean(null);
		selector = new InputSelector(mock(ClientToolbar.class));
	}

	@After
	public void after() throws Exception
	{
		if (ScriptPauseController.isPaused())
		{
			ScriptPauseController.setPaused(false).get(5, TimeUnit.SECONDS);
		}
		swapGame(previous);
		swapClient(previousClient);
		AwtEmitter.setCanvasFocusable(focusableBefore);
	}

	private boolean focusableBefore;

	private static java.lang.reflect.Field focusableSetting() throws NoSuchFieldException
	{
		java.lang.reflect.Field field = AwtEmitter.class.getDeclaredField("focusableSetting");
		field.setAccessible(true);
		return field;
	}

	@Test
	public void inputGoesOffWhenAScriptStartsAndComesBackWhenTheLastStops()
	{
		selector.scriptsRunning(true);
		assertFalse(game.isEnabled());

		selector.scriptsRunning(true); // a second script starting changes nothing
		selector.scriptsRunning(false);
		assertTrue(game.isEnabled());
	}

	@Test
	public void inputTheUserHadTurnedOffBeforehandStaysOff()
	{
		selector.disableClick();

		selector.scriptsRunning(true);
		selector.scriptsRunning(false);

		assertFalse(game.isEnabled());
	}

	@Test
	public void theUsersOwnChoiceDuringARunStands()
	{
		selector.scriptsRunning(true);
		selector.enableClick();
		selector.disableClick();
		selector.scriptsRunning(false);
		assertFalse("the user turned input off last, so stopping must not turn it on", game.isEnabled());

		selector.scriptsRunning(true);
		selector.enableClick();
		selector.scriptsRunning(false);
		assertTrue(game.isEnabled());
	}

	@Test
	public void aScriptStartedWhilePausedTurnsInputOffOnlyAfterTheResume() throws Exception
	{
		togglePause();
		assertTrue("pausing hands input to the user", game.isEnabled());

		selector.scriptsRunning(true);
		assertTrue("still the user's while paused", game.isEnabled());

		togglePause();
		assertFalse(game.isEnabled());
	}

	@Test
	public void aChoiceMadeWhilePausedCancelsTheWaitingChange() throws Exception
	{
		togglePause();
		selector.scriptsRunning(true);
		selector.disableClick();
		selector.enableClick();

		togglePause();

		assertTrue("the user turned input on last, so the resume must not turn it off", game.isEnabled());
	}

	@Test
	public void aScriptThatStopsWhilePausedLeavesInputOnAfterTheResume() throws Exception
	{
		selector.scriptsRunning(true);
		togglePause();
		selector.scriptsRunning(false);

		togglePause();

		assertTrue("no script is left, so input must not go back to off", game.isEnabled());
	}

	/** Clicks the pause button and waits until the pause or resume has fully finished. */
	private void togglePause() throws Exception
	{
		Method toggle = InputSelector.class.getDeclaredMethod("togglePause");
		toggle.setAccessible(true);
		Field pending = InputSelector.class.getDeclaredField("transitionPending");
		pending.setAccessible(true);
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				toggle.invoke(selector);
			}
			catch (ReflectiveOperationException e)
			{
				throw new IllegalStateException(e);
			}
		});
		long deadline = System.currentTimeMillis() + 5_000;
		boolean[] busy = {true};
		while (busy[0] && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(10);
			SwingUtilities.invokeAndWait(() ->
			{
				try
				{
					busy[0] = pending.getBoolean(selector);
				}
				catch (IllegalAccessException e)
				{
					throw new IllegalStateException(e);
				}
			});
		}
		assertFalse("the pause or resume did not finish", busy[0]);
	}

	private static Object swapClient(Object value) throws Exception
	{
		Field field = Microbot.class.getDeclaredField("client");
		field.setAccessible(true);
		Object old = field.get(null);
		field.set(null, value);
		return old;
	}

	private static Object swapGame(Object value) throws Exception
	{
		Field field = ClientUI.class.getDeclaredField("client");
		field.setAccessible(true);
		Object old = field.get(null);
		field.set(null, value);
		return old;
	}
}
