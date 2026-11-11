package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An {@link InputLoop.Emit} works only inside the gesture it was handed to, on the thread running
 * that gesture. Anywhere else every method dispatches nothing and throws
 * {@link IllegalStateException}: after the gesture returned, from another thread while it runs, and
 * from inside a later gesture on the same thread.
 */
public class GestureEmitLifetimeTest
{
	private static final long BOUND_MS = 3_000L;
	/** Longer than any wait on the test side, so the held gesture cannot end on its own first. */
	private static final long GESTURE_HOLD_MS = 10_000L;
	private static final int X = 120;
	private static final int Y = 110;

	private enum Use
	{
		MOVE,
		PRESS,
		RELEASE,
		CLICK,
		WHEEL,
		CHECKPOINT;

		void on(InputLoop.Emit emit)
		{
			switch (this)
			{
				case MOVE:
					emit.move(X, Y);
					break;
				case PRESS:
					emit.press(X, Y, MouseEvent.BUTTON1);
					break;
				case RELEASE:
					emit.release(X, Y, MouseEvent.BUTTON1);
					break;
				case CLICK:
					emit.click(X, Y, MouseEvent.BUTTON1);
					break;
				case WHEEL:
					emit.wheel(X, Y, 2, 10);
					break;
				default:
					emit.checkpoint();
					break;
			}
		}
	}

	private Client client;
	private Canvas canvas;
	/** Event names with the dispatching thread, in arrival order. */
	private final List<String> received = Collections.synchronizedList(new ArrayList<>());
	private final List<Thread> started = new ArrayList<>();
	private final CountDownLatch endGesture = new CountDownLatch(1);

	private Object previousClient;
	private Object previousNaturalMouse;

	@Before
	public void before() throws Exception
	{
		ScriptPauseController.setPaused(false).get(BOUND_MS, TimeUnit.MILLISECONDS);

		canvas = new Canvas();
		canvas.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				record(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				record(e);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				record(e);
			}
		});
		canvas.addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				record(e);
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				record(e);
			}
		});
		canvas.addMouseWheelListener(this::record);

		client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenReturn(false);
		when(client.isStretchedEnabled()).thenReturn(false);

		previousClient = swapStatic("client", client);
		previousNaturalMouse = swapStatic("naturalMouse", null);
		Microbot.targetMenu = null;

		PointerState.reset();
		InputArbiter.resetForTest();
		AwtEmitter.resetHeldButtonsForTest();
		InputLoop.setLockTimeoutForTest(5_000L);
		PointerState.setFromBot(100, 100);
	}

	@After
	public void after() throws Exception
	{
		List<String> problems = new ArrayList<>();
		try
		{
			endGesture.countDown();
			for (Thread thread : started)
			{
				thread.join(BOUND_MS);
				if (thread.isAlive())
				{
					problems.add("helper thread " + thread.getName() + " is still running");
				}
			}
			try
			{
				ScriptPauseController.setPaused(false).get(BOUND_MS, TimeUnit.MILLISECONDS);
			}
			catch (Exception ex)
			{
				problems.add("resume in teardown failed: " + ex);
			}
			InputArbiter.resetForTest();

			int held = AwtEmitter.heldMask();
			if (held != 0)
			{
				problems.add("a synthetic button is still tracked as held after the test, heldMask=" + held);
			}

			// A no-op gesture only completes if nothing still owns the gesture lock.
			InputLoop.setLockTimeoutForTest(1_000L);
			InputLoop.Result probe = InputLoop.run(emit -> { });
			if (probe != InputLoop.Result.COMPLETED)
			{
				problems.add("InputLoop is not free after the test: a no-op gesture returned " + probe);
			}
		}
		finally
		{
			swapStatic("client", previousClient);
			swapStatic("naturalMouse", previousNaturalMouse);
			Microbot.targetMenu = null;
			PointerState.reset();
			InputArbiter.resetForTest();
			AwtEmitter.resetHeldButtonsForTest();
			InputLoop.setLockTimeoutForTest(5_000L);
			while (BotEventGuard.isSynthetic())
			{
				BotEventGuard.end();
			}
		}
		assertTrue("teardown found leaked state: " + problems, problems.isEmpty());
	}

	// E1a: after InputLoop.run has returned.

	@Test(timeout = 15_000)
	public void moveAfterTheGestureReturnedIsRejected()
	{
		assertRejectedAfterReturn(Use.MOVE);
	}

	@Test(timeout = 15_000)
	public void pressAfterTheGestureReturnedIsRejected()
	{
		assertRejectedAfterReturn(Use.PRESS);
	}

	@Test(timeout = 15_000)
	public void releaseAfterTheGestureReturnedIsRejected()
	{
		assertRejectedAfterReturn(Use.RELEASE);
	}

	@Test(timeout = 15_000)
	public void clickAfterTheGestureReturnedIsRejected()
	{
		assertRejectedAfterReturn(Use.CLICK);
	}

	@Test(timeout = 15_000)
	public void wheelAfterTheGestureReturnedIsRejected()
	{
		assertRejectedAfterReturn(Use.WHEEL);
	}

	@Test(timeout = 15_000)
	public void checkpointAfterTheGestureReturnedIsRejected()
	{
		assertRejectedAfterReturn(Use.CHECKPOINT);
	}

	// E1b: from another thread while the gesture is still running.

	@Test(timeout = 15_000)
	public void moveFromAnotherThreadWhileTheGestureRunsIsRejected() throws Exception
	{
		assertRejectedFromAnotherThread(Use.MOVE);
	}

	@Test(timeout = 15_000)
	public void pressFromAnotherThreadWhileTheGestureRunsIsRejected() throws Exception
	{
		assertRejectedFromAnotherThread(Use.PRESS);
	}

	@Test(timeout = 15_000)
	public void releaseFromAnotherThreadWhileTheGestureRunsIsRejected() throws Exception
	{
		assertRejectedFromAnotherThread(Use.RELEASE);
	}

	@Test(timeout = 15_000)
	public void clickFromAnotherThreadWhileTheGestureRunsIsRejected() throws Exception
	{
		assertRejectedFromAnotherThread(Use.CLICK);
	}

	@Test(timeout = 15_000)
	public void wheelFromAnotherThreadWhileTheGestureRunsIsRejected() throws Exception
	{
		assertRejectedFromAnotherThread(Use.WHEEL);
	}

	@Test(timeout = 15_000)
	public void checkpointFromAnotherThreadWhileTheGestureRunsIsRejected() throws Exception
	{
		assertRejectedFromAnotherThread(Use.CHECKPOINT);
	}

	// E1c: from inside a later gesture on the same thread.

	@Test(timeout = 15_000)
	public void moveInsideALaterGestureIsRejected()
	{
		assertRejectedInsideALaterGesture(Use.MOVE);
	}

	@Test(timeout = 15_000)
	public void pressInsideALaterGestureIsRejected()
	{
		assertRejectedInsideALaterGesture(Use.PRESS);
	}

	@Test(timeout = 15_000)
	public void releaseInsideALaterGestureIsRejected()
	{
		assertRejectedInsideALaterGesture(Use.RELEASE);
	}

	@Test(timeout = 15_000)
	public void clickInsideALaterGestureIsRejected()
	{
		assertRejectedInsideALaterGesture(Use.CLICK);
	}

	@Test(timeout = 15_000)
	public void wheelInsideALaterGestureIsRejected()
	{
		assertRejectedInsideALaterGesture(Use.WHEEL);
	}

	@Test(timeout = 15_000)
	public void checkpointInsideALaterGestureIsRejected()
	{
		assertRejectedInsideALaterGesture(Use.CHECKPOINT);
	}

	// Unchanged: inside its own gesture every method works.

	@Test(timeout = 15_000)
	public void everyMethodWorksInsideItsOwnGesture()
	{
		InputLoop.Result result = InputLoop.run(emit ->
		{
			emit.checkpoint();
			emit.move(X, Y);
			emit.press(X, Y, MouseEvent.BUTTON1);
			emit.release(X, Y, MouseEvent.BUTTON1);
			emit.click(X, Y, MouseEvent.BUTTON1);
			emit.wheel(X, Y, 2, 10);
		});

		assertEquals("inside its own gesture every method dispatches its event, saw " + received,
			Arrays.asList("MOVED", "PRESSED", "RELEASED", "CLICKED", "WHEEL"),
			eventNames());
		assertEquals("a gesture whose emits all went out completes", InputLoop.Result.COMPLETED, result);
	}

	private void assertRejectedAfterReturn(Use use)
	{
		AtomicReference<InputLoop.Emit> leaked = new AtomicReference<>();
		InputLoop.run(leaked::set);
		assertNotNull("setup: the gesture never ran", leaked.get());

		Throwable thrown = call(use, leaked.get());

		assertRejected(use + " on an Emit after its InputLoop.run returned", thrown, snapshotAndRelease());
	}

	private void assertRejectedFromAnotherThread(Use use) throws Exception
	{
		AtomicReference<InputLoop.Emit> handed = new AtomicReference<>();
		CountDownLatch inside = new CountDownLatch(1);
		AtomicBoolean bodyEnded = new AtomicBoolean();
		// The gesture emits nothing of its own, so whatever reaches the canvas is the other thread's,
		// and it cannot end before endGesture opens, which happens only after the check below.
		Thread gesture = new Thread(() -> InputLoop.run(emit ->
		{
			handed.set(emit);
			inside.countDown();
			try
			{
				endGesture.await(GESTURE_HOLD_MS, TimeUnit.MILLISECONDS);
			}
			catch (InterruptedException interrupted)
			{
				Thread.currentThread().interrupt();
			}
			bodyEnded.set(true);
		}), "owning-gesture");
		gesture.setDaemon(true);
		started.add(gesture);
		gesture.start();
		assertTrue("setup: the owning gesture never started", inside.await(BOUND_MS, TimeUnit.MILLISECONDS));

		Throwable thrown = call(use, handed.get());
		List<String> seen = snapshotAndRelease();

		assertFalse("setup: the owning gesture must still be running when the other thread's call returns",
			bodyEnded.get());
		assertTrue("setup: the owning gesture's thread must still be alive", gesture.isAlive());
		assertRejected(use + " on an Emit from another thread while its gesture runs", thrown, seen);
	}

	private void assertRejectedInsideALaterGesture(Use use)
	{
		AtomicReference<InputLoop.Emit> earlier = new AtomicReference<>();
		InputLoop.run(earlier::set);
		assertNotNull("setup: the earlier gesture never ran", earlier.get());
		AtomicReference<Throwable> thrown = new AtomicReference<>();
		AtomicBoolean laterRan = new AtomicBoolean();

		// The later gesture emits nothing of its own, and catches what the stale call throws so it
		// can be inspected rather than escaping InputLoop.run.
		InputLoop.run(emit ->
		{
			laterRan.set(true);
			thrown.set(call(use, earlier.get()));
		});

		assertTrue("setup: the later gesture never ran", laterRan.get());
		assertRejected(use + " on an earlier gesture's Emit from inside a later gesture on the same thread",
			thrown.get(), snapshotAndRelease());
	}

	private static Throwable call(Use use, InputLoop.Emit emit)
	{
		try
		{
			use.on(emit);
			return null;
		}
		catch (Throwable thrown)
		{
			return thrown;
		}
	}

	private static void assertRejected(String what, Throwable thrown, List<String> seen)
	{
		assertEquals(what + ": nothing may reach the canvas, but it saw " + seen, Collections.emptyList(), seen);
		assertTrue(what + ": must throw IllegalStateException, but "
				+ (thrown == null ? "nothing was thrown" : "it threw " + thrown),
			thrown instanceof IllegalStateException);
	}

	/**
	 * Snapshot first, then release anything a wrongly accepted press left down, so teardown does not
	 * report it as a second, misleading failure.
	 */
	private List<String> snapshotAndRelease()
	{
		List<String> seen;
		synchronized (received)
		{
			seen = new ArrayList<>(received);
		}
		if (AwtEmitter.heldMask() != 0)
		{
			AwtEmitter.released(X, Y, MouseEvent.BUTTON1);
		}
		return seen;
	}

	private List<String> eventNames()
	{
		List<String> out = new ArrayList<>();
		synchronized (received)
		{
			for (String event : received)
			{
				out.add(event.substring(0, event.indexOf(' ')));
			}
		}
		return out;
	}

	private void record(MouseEvent e)
	{
		received.add(name(e.getID()) + " from " + Thread.currentThread().getName());
	}

	private static String name(int id)
	{
		switch (id)
		{
			case MouseEvent.MOUSE_PRESSED:
				return "PRESSED";
			case MouseEvent.MOUSE_RELEASED:
				return "RELEASED";
			case MouseEvent.MOUSE_CLICKED:
				return "CLICKED";
			case MouseEvent.MOUSE_MOVED:
				return "MOVED";
			case MouseEvent.MOUSE_DRAGGED:
				return "DRAGGED";
			case MouseEvent.MOUSE_WHEEL:
				return "WHEEL";
			default:
				return String.valueOf(id);
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
