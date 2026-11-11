package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A button or key that is down when the listener stops listening can never deliver its release to
 * it, and a held entry pins HUMAN. So stopping listening has to drop what the arbiter believes is
 * held, the way a focus loss does.
 */
public class ListenerDetachForgetsHeldInputTest
{
	private Client client;
	private Canvas canvas;
	private final AtomicLong now = new AtomicLong(1_000_000L);
	private Object previousClient;

	@Before
	public void before() throws Exception
	{
		canvas = new Canvas();
		client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isStretchedEnabled()).thenReturn(false);
		previousClient = swapStatic("client", client);

		PointerState.reset();
		InputArbiter.resetForTest();
		InputArbiter.setClockForTest(now::get);
		CanvasInputListener.detach();
		CanvasInputListener.attach();
		assertTrue("fixture: the listener must be on the test canvas", CanvasInputListener.isAttachedTo(canvas));
	}

	@After
	public void after() throws Exception
	{
		CanvasInputListener.detach();
		swapStatic("client", previousClient);
		PointerState.reset();
		InputArbiter.resetForTest();
	}

	@Test(timeout = 10_000)
	public void detachForgetsEveryHeldButtonAndKey()
	{
		pressButton(MouseEvent.BUTTON1);
		pressButton(MouseEvent.BUTTON3);
		pressKey(KeyEvent.VK_CONTROL);
		assertEquals("fixture: both buttons reached the arbiter through the listener", 2,
			InputArbiter.realButtonsDown().size());
		assertEquals("fixture: the key reached the arbiter through the listener", 1,
			InputArbiter.realKeysDown().size());

		CanvasInputListener.detach();

		assertTrue("buttons held at detach can never deliver their release to a detached listener, "
			+ "so the arbiter must stop believing they are down", InputArbiter.realButtonsDown().isEmpty());
		assertTrue("keys held at detach can never deliver their release to a detached listener, "
			+ "so the arbiter must stop believing they are down", InputArbiter.realKeysDown().isEmpty());
	}

	@Test(timeout = 10_000)
	public void detachCountsAsActivitySoHumanLapsesAfterTheIdleWindowFromThatMoment()
	{
		pressButton(MouseEvent.BUTTON1);
		pressKey(KeyEvent.VK_SHIFT);
		// Long past the idle window: only the held entries keep HUMAN now.
		advanceMs(60_000);
		assertTrue("fixture: held input pins HUMAN", InputArbiter.isHuman());

		CanvasInputListener.detach();

		assertFalse("detach left the held button and key pinned in the arbiter, so HUMAN never lapses",
			InputArbiter.isRealButtonOrKeyDown());
		assertTrue("detach is activity, like a focus loss: HUMAN must not end at that instant",
			InputArbiter.isHuman());
		advanceMs(1_799);
		assertTrue("the idle window runs from the detach, not from the press a minute earlier",
			InputArbiter.isHuman());
		advanceMs(2);
		assertFalse("once the idle window after detach has passed the bot must resume", InputArbiter.isHuman());
	}

	@Test(timeout = 10_000)
	public void attachMovingToAnotherCanvasForgetsHeldInputAndCountsAsActivity()
	{
		pressButton(MouseEvent.BUTTON1);
		pressKey(KeyEvent.VK_CONTROL);
		advanceMs(60_000);
		Canvas replacement = new Canvas();
		when(client.getCanvas()).thenReturn(replacement);

		CanvasInputListener.attach();

		assertTrue("fixture: attach must have moved to the replacement canvas",
			CanvasInputListener.isAttachedTo(replacement));
		assertTrue("moving to a new canvas stops listening to the old one, whose held button can never "
			+ "deliver its release, so the arbiter must forget it", InputArbiter.realButtonsDown().isEmpty());
		assertTrue("moving to a new canvas stops listening to the old one, whose held key can never "
			+ "deliver its release, so the arbiter must forget it", InputArbiter.realKeysDown().isEmpty());
		assertTrue("the move is activity: HUMAN must not end at that instant", InputArbiter.isHuman());
		advanceMs(1_799);
		assertTrue("the idle window runs from the move", InputArbiter.isHuman());
		advanceMs(2);
		assertFalse("once the idle window after the move has passed the bot must resume", InputArbiter.isHuman());
	}

	@Test(timeout = 10_000)
	public void detachWithNothingHeldDoesNotLookLikeActivity()
	{
		pressButton(MouseEvent.BUTTON1);
		releaseButton(MouseEvent.BUTTON1);
		advanceMs(1_000);
		assertEquals("fixture: the release was the last activity", 1_000L, InputArbiter.millisSinceRealActivity());

		CanvasInputListener.detach();

		assertEquals("detach with nothing held must not restart the idle window", 1_000L,
			InputArbiter.millisSinceRealActivity());
		advanceMs(801);
		assertFalse("the window still runs from the release, so HUMAN has lapsed", InputArbiter.isHuman());
	}

	@Test(timeout = 10_000)
	public void detachWithNoActivityEverLeavesTheArbiterUntouched()
	{
		CanvasInputListener.detach();

		assertEquals("no real activity has happened, and detach is not one", -1L,
			InputArbiter.millisSinceRealActivity());
		assertFalse(InputArbiter.isHuman());
	}

	@Test(timeout = 10_000)
	public void attachToTheCanvasAlreadyAttachedChangesNothing()
	{
		pressButton(MouseEvent.BUTTON1);
		pressKey(KeyEvent.VK_CONTROL);
		advanceMs(60_000);

		CanvasInputListener.attach();

		assertTrue(CanvasInputListener.isAttachedTo(canvas));
		assertTrue("the listener never stopped listening, so the button's release can still arrive",
			InputArbiter.realButtonsDown().contains(MouseEvent.BUTTON1));
		assertTrue("the listener never stopped listening, so the key's release can still arrive",
			InputArbiter.realKeysDown().contains(KeyEvent.VK_CONTROL));
		assertEquals("an idempotent attach is not activity", 60_000L, InputArbiter.millisSinceRealActivity());
	}

	@Test(timeout = 10_000)
	public void aReleaseThatArrivesStillClearsTheHeldEntry()
	{
		pressButton(MouseEvent.BUTTON1);
		pressKey(KeyEvent.VK_CONTROL);
		advanceMs(60_000);

		releaseButton(MouseEvent.BUTTON1);
		releaseKey(KeyEvent.VK_CONTROL);

		assertFalse(InputArbiter.isRealButtonOrKeyDown());
		assertTrue("the release is activity", InputArbiter.isHuman());
		advanceMs(1_801);
		assertFalse(InputArbiter.isHuman());
	}

	private void advanceMs(long millis)
	{
		now.addAndGet(millis * 1_000_000L);
	}

	private void pressButton(int button)
	{
		canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, now.get(), 0, 10, 10, 1, false,
			button));
	}

	private void releaseButton(int button)
	{
		canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, now.get(), 0, 10, 10, 1, false,
			button));
	}

	// Handed to the listeners directly, as FocusLossReleasesHeldInputTest does: the test canvas is
	// in no window, so it never owns keyboard focus.
	private void pressKey(int keyCode)
	{
		KeyEvent event = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, now.get(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
		for (KeyListener listener : canvas.getKeyListeners())
		{
			listener.keyPressed(event);
		}
	}

	private void releaseKey(int keyCode)
	{
		KeyEvent event = new KeyEvent(canvas, KeyEvent.KEY_RELEASED, now.get(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
		for (KeyListener listener : canvas.getKeyListeners())
		{
			listener.keyReleased(event);
		}
	}

	private static Object swapStatic(String name, Object value) throws Exception
	{
		Field field = Microbot.class.getDeclaredField(name);
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, value);
		return previous;
	}
}
