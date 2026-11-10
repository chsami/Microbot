package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * While {@code Microbot.targetMenu} is armed, every menu the client builds is rewritten to the
 * bot's entry, so a human click arriving before the game consumed it performs the bot's action.
 * The human's press is the moment the bot loses its claim to the next click.
 */
public class RealPressDisarmsTargetMenuTest
{
	private Client client;
	private Canvas canvas;
	private final AtomicLong now = new AtomicLong(1_000_000L);
	private final AtomicInteger syntheticPresses = new AtomicInteger();
	private Object previousClient;
	private Object previousNaturalMouse;
	private Thread gestureThread;
	private CountDownLatch gestureGo;

	@Before
	public void before() throws Exception
	{
		canvas = new Canvas();
		canvas.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (BotEventGuard.isSynthetic())
				{
					syntheticPresses.incrementAndGet();
				}
			}
		});
		client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenReturn(false);
		when(client.isStretchedEnabled()).thenReturn(false);
		previousClient = swapStatic("client", client);
		// Null naturalMouse means a click emits only what handleClick itself produces.
		previousNaturalMouse = swapStatic("naturalMouse", null);

		PointerState.reset();
		InputArbiter.resetForTest();
		InputArbiter.setClockForTest(now::get);
		AwtEmitter.resetHeldButtonsForTest();
		Microbot.targetMenu = null;
		CanvasInputListener.detach();
		CanvasInputListener.attach();
		assertTrue("fixture: the listener must be on the test canvas", CanvasInputListener.isAttachedTo(canvas));
	}

	@After
	public void after() throws Exception
	{
		if (gestureGo != null)
		{
			gestureGo.countDown();
		}
		if (gestureThread != null)
		{
			gestureThread.join(5_000);
		}
		CanvasInputListener.detach();
		swapStatic("client", previousClient);
		swapStatic("naturalMouse", previousNaturalMouse);
		Microbot.targetMenu = null;
		PointerState.reset();
		InputArbiter.resetForTest();
		AwtEmitter.resetHeldButtonsForTest();
		while (BotEventGuard.isSynthetic())
		{
			BotEventGuard.end();
		}
	}

	@Test(timeout = 10_000)
	public void aRealPressDisarmsTheBotsEntry()
	{
		NewMenuEntry entry = new NewMenuEntry();
		Microbot.targetMenu = entry;

		realPress(MouseEvent.BUTTON1);

		assertNull("the human pressed while the bot's entry was armed: left armed, the menu the client "
			+ "builds for this press is rewritten to the bot's entry and the human's click performs it",
			Microbot.targetMenu);
	}

	@Test(timeout = 10_000)
	public void aRealRightPressDisarmsTheBotsEntryToo()
	{
		Microbot.targetMenu = new NewMenuEntry();

		realPress(MouseEvent.BUTTON3);

		assertNull("a right press opens a menu, which the armed entry would rewrite to the bot's action",
			Microbot.targetMenu);
	}

	@Test(timeout = 10_000)
	public void aRealPressDisarmsTheEntryWhileTheGestureThatArmedItIsStillRunning() throws Exception
	{
		PointerState.setFromBot(100, 100);
		NewMenuEntry entry = new NewMenuEntry();
		CountDownLatch armed = new CountDownLatch(1);
		gestureGo = new CountDownLatch(1);
		// Arms and then parks before its press, the order the menu-aware click uses, so the gesture
		// is mid-flight and holds the input lock when the human presses.
		gestureThread = new Thread(() -> InputLoop.run(emit -> {
			Microbot.targetMenu = entry;
			armed.countDown();
			try
			{
				gestureGo.await(5, TimeUnit.SECONDS);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			emit.press(100, 100, MouseEvent.BUTTON1);
		}));
		gestureThread.setDaemon(true);
		gestureThread.start();
		assertTrue("fixture: the gesture must have armed its entry", armed.await(5, TimeUnit.SECONDS));

		realPress(MouseEvent.BUTTON1);

		// Read before the gesture resumes: its abort clears the slot too, which would hide the defect.
		assertNull("a gesture is running, but the human's press still must not be given the bot's entry",
			Microbot.targetMenu);
	}

	@Test(timeout = 10_000)
	public void aRealPressDisarmsTheEntryEvenWithYieldingDisabled()
	{
		InputArbiter.setDisabled(true);
		Microbot.targetMenu = new NewMenuEntry();

		realPress(MouseEvent.BUTTON1);

		assertFalse("fixture: yielding is disabled, so the arbiter never reports HUMAN", InputArbiter.isHuman());
		assertNull("disabling the yield keeps the bot running, it does not hand the human's click to the "
			+ "bot's entry", Microbot.targetMenu);
	}

	@Test(timeout = 10_000)
	public void theSyntheticPressTheEntryWasArmedForKeepsIt()
	{
		PointerState.setFromBot(100, 50);
		NewMenuEntry entry = new NewMenuEntry();

		new VirtualMouse().click(new Point(100, 50), entry);

		assertEquals("fixture: the bot's press must have reached the canvas, or this passes vacuously", 1,
			syntheticPresses.get());
		assertSame("the client consumes the entry on the click it was armed for", entry, Microbot.targetMenu);
	}

	@Test(timeout = 10_000)
	public void aSyntheticPressEmittedDirectlyKeepsTheEntry()
	{
		NewMenuEntry entry = new NewMenuEntry();
		Microbot.targetMenu = entry;

		AwtEmitter.pressed(100, 50, MouseEvent.BUTTON1);
		AwtEmitter.released(100, 50, MouseEvent.BUTTON1);

		assertEquals("fixture: the bot's press must have reached the canvas", 1, syntheticPresses.get());
		assertSame(entry, Microbot.targetMenu);
	}

	@Test(timeout = 10_000)
	public void aRealMoveKeepsTheEntry()
	{
		PointerState.setFromBot(100, 100);
		NewMenuEntry entry = new NewMenuEntry();
		Microbot.targetMenu = entry;

		// Far enough to cross the motion threshold, so the move counts as activity.
		canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, now.get(), 0, 400, 300, 0, false));

		assertTrue("fixture: the move reached the arbiter as a takeover", InputArbiter.isHuman());
		assertSame("motion alone is not a click, so it does not take the bot's next click away", entry,
			Microbot.targetMenu);
	}

	@Test(timeout = 10_000)
	public void aRealReleaseKeepsTheEntry()
	{
		NewMenuEntry entry = new NewMenuEntry();
		Microbot.targetMenu = entry;

		canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, now.get(), 0, 10, 10, 1, false,
			MouseEvent.BUTTON1));

		assertTrue("fixture: the release reached the arbiter", InputArbiter.millisSinceRealActivity() >= 0);
		assertSame(entry, Microbot.targetMenu);
	}

	@Test(timeout = 10_000)
	public void aRealKeyKeepsTheEntry()
	{
		NewMenuEntry entry = new NewMenuEntry();
		Microbot.targetMenu = entry;

		KeyEvent pressed = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, now.get(), 0, KeyEvent.VK_A, 'a');
		KeyEvent released = new KeyEvent(canvas, KeyEvent.KEY_RELEASED, now.get(), 0, KeyEvent.VK_A, 'a');
		// Handed to the listeners directly, as FocusLossReleasesHeldInputTest does: the test canvas is
		// in no window, so it never owns keyboard focus.
		for (KeyListener listener : canvas.getKeyListeners())
		{
			listener.keyPressed(pressed);
		}
		assertTrue("fixture: the key reached the arbiter", InputArbiter.realKeysDown().contains(KeyEvent.VK_A));
		for (KeyListener listener : canvas.getKeyListeners())
		{
			listener.keyReleased(released);
		}

		assertSame(entry, Microbot.targetMenu);
	}

	@Test(timeout = 10_000)
	public void aRealPressWithNothingArmedLeavesTheSlotEmpty()
	{
		realPress(MouseEvent.BUTTON1);

		assertNull(Microbot.targetMenu);
		assertTrue("the press is recorded as usual", InputArbiter.realButtonsDown().contains(MouseEvent.BUTTON1));
	}

	private void realPress(int button)
	{
		assertFalse("fixture: a real press must not run inside a synthetic dispatch", BotEventGuard.isSynthetic());
		canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, now.get(), 0, 10, 10, 1, false,
			button));
		assertTrue("fixture: the press reached the arbiter through the listener",
			InputArbiter.realButtonsDown().contains(button));
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
