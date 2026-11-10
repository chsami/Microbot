package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A synthetic button that went down comes up exactly once, and one that never reached the canvas
 * does not come up at all, whichever of the gesture or the pause cleanup does the releasing.
 *
 * <p>Asserted on the events a real {@link Canvas} receives. Pauses and takeovers are started from
 * hooks that run on the gesture's own thread at a known point, never by polling from outside: the
 * hold inside a click is tens of milliseconds and a poller can miss it.
 */
public class HeldButtonBalanceTest
{
	private static final long BOUND_MS = 3_000L;
	private static final Point TARGET = new Point(100, 100);
	private static final Point DRAG_END = new Point(300, 200);
	private static final Point HUMAN_POINT = new Point(640, 480);

	private Client client;
	private Canvas canvas;
	private VirtualMouse mouse;
	private final List<Recorded> received = Collections.synchronizedList(new ArrayList<>());
	private final List<Thread> started = new ArrayList<>();

	/** While set, the client reports no canvas. */
	private final AtomicBoolean canvasGone = new AtomicBoolean();
	/** One-shot, run inside the first PRESSED dispatch: on the gesture thread, under the emitter's lock. */
	private final AtomicReference<Runnable> onFirstPressed = new AtomicReference<>();
	/** One-shot, answers the first canvas lookup made from inside a gesture. */
	private final AtomicReference<Supplier<Canvas>> firstCanvasInGesture = new AtomicReference<>();

	private final AtomicReference<CompletableFuture<Void>> pause = new AtomicReference<>();
	private final CountDownLatch pauseRequested = new CountDownLatch(1);

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
				Runnable hook = onFirstPressed.getAndSet(null);
				if (hook != null)
				{
					hook.run();
				}
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

		client = mock(Client.class);
		when(client.getCanvas()).thenAnswer(invocation ->
		{
			if (InputLoop.isInGesture())
			{
				Supplier<Canvas> hook = firstCanvasInGesture.getAndSet(null);
				if (hook != null)
				{
					return hook.get();
				}
			}
			return canvasGone.get() ? null : canvas;
		});
		when(client.isClientThread()).thenReturn(false);
		when(client.isStretchedEnabled()).thenReturn(false);

		previousClient = swapStatic("client", client);
		// Null, so each leg of a drag is a single move and a click emits only its own triad.
		previousNaturalMouse = swapStatic("naturalMouse", null);
		Microbot.targetMenu = null;

		PointerState.reset();
		InputArbiter.resetForTest();
		AwtEmitter.resetHeldButtonsForTest();
		InputLoop.setLockTimeoutForTest(5_000L);

		mouse = new VirtualMouse();
	}

	@After
	public void after() throws Exception
	{
		List<String> problems = new ArrayList<>();
		try
		{
			onFirstPressed.set(null);
			firstCanvasInGesture.set(null);
			canvasGone.set(false);
			// First, so a gesture parked in a paused sleep can wake and finish.
			try
			{
				ScriptPauseController.setPaused(false).get(BOUND_MS, TimeUnit.MILLISECONDS);
			}
			catch (Exception ex)
			{
				problems.add("resume in teardown failed: " + ex);
			}
			for (Thread thread : started)
			{
				thread.join(BOUND_MS);
				if (thread.isAlive())
				{
					problems.add("helper thread " + thread.getName() + " is still running");
				}
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
			mouse.shutdown();
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

	// B1

	@Test(timeout = 15_000)
	public void pauseDuringClickHoldThenResumeReleasesTheButtonExactlyOnce() throws Exception
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		onFirstPressed.set(this::requestPauseWithoutWaiting);

		Thread gesture = startDaemon("click-gesture", () -> mouse.click(TARGET, false));
		awaitPauseCleanupThenResume();
		joinBounded(gesture);

		assertEquals("pause during a click's hold, then resume: the PRESSED must be released exactly once"
				+ " and the cancelled click must send no CLICKED, but the canvas saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), receivedIds());
	}

	// B2

	@Test(timeout = 15_000)
	public void pauseDuringDragHoldThenResumeReleasesTheButtonExactlyOnce() throws Exception
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		onFirstPressed.set(this::requestPauseWithoutWaiting);

		Thread gesture = startDaemon("drag-gesture", () -> mouse.drag(TARGET, DRAG_END));
		awaitPauseCleanupThenResume();
		joinBounded(gesture);

		assertEquals("pause during a drag's hold, then resume: the PRESSED must be released exactly once,"
				+ " but the canvas saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), receivedIds());
	}

	// B3

	@Test(timeout = 15_000)
	public void pressWithNoCanvasThenTakeoverSendsNoRelease()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		// The press finds no canvas, and the human takes over before the click's own release.
		firstCanvasInGesture.set(() ->
		{
			takeOverAt(HUMAN_POINT);
			return null;
		});

		mouse.click(TARGET, false);

		assertEquals("setup: the press must not have reached the canvas, saw " + describe(),
			0, count(MouseEvent.MOUSE_PRESSED));
		assertEquals("a press the game never saw must produce no RELEASED when its gesture aborts,"
				+ " but the canvas saw " + describe(),
			0, count(MouseEvent.MOUSE_RELEASED));
	}

	@Test(timeout = 15_000)
	public void pressRejectedByAPauseLandingBeforeDispatchSendsNoReleaseAfterResume() throws Exception
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		// The pause lands after the press passed its checkpoint but before the emitter dispatched it.
		firstCanvasInGesture.set(() ->
		{
			requestPauseWithoutWaiting();
			return canvas;
		});

		Thread gesture = startDaemon("click-gesture", () -> mouse.click(TARGET, false));
		awaitPauseCleanupThenResume();
		joinBounded(gesture);

		assertEquals("setup: the press must not have reached the canvas, saw " + describe(),
			0, count(MouseEvent.MOUSE_PRESSED));
		assertEquals("a press the paused emitter never dispatched must produce no RELEASED when its"
				+ " gesture ends after resume, but the canvas saw " + describe(),
			0, count(MouseEvent.MOUSE_RELEASED));
	}

	@Test(timeout = 15_000)
	public void pressWithNoCanvasInACompletedClickSendsNoRelease()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		// Only the press finds no canvas; the rest of the click runs normally.
		firstCanvasInGesture.set(() -> null);

		mouse.click(TARGET, false);

		assertEquals("setup: the press must not have reached the canvas, saw " + describe(),
			0, count(MouseEvent.MOUSE_PRESSED));
		assertEquals("a press the game never saw must produce no RELEASED even when the click runs to"
				+ " completion, but the canvas saw " + describe(),
			0, count(MouseEvent.MOUSE_RELEASED));
	}

	// B4: behavior that already holds and must keep holding.

	@Test(timeout = 15_000)
	public void takeoverDuringClickHoldReleasesOnceAtTheHumansPointAndDoesNotClick()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		onFirstPressed.set(() -> takeOverAt(HUMAN_POINT));

		mouse.click(TARGET, false);

		assertEquals("takeover during a click's hold: exactly one RELEASED and no CLICKED, saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), receivedIds());
		assertReleasedAtHumanPoint();
	}

	@Test(timeout = 15_000)
	public void takeoverDuringDragHoldReleasesOnceAtTheHumansPoint()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		onFirstPressed.set(() -> takeOverAt(HUMAN_POINT));

		mouse.drag(TARGET, DRAG_END);

		assertEquals("takeover during a drag's hold: exactly one RELEASED, saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), receivedIds());
		assertReleasedAtHumanPoint();
	}

	@Test(timeout = 15_000)
	public void undeliveredReleaseStaysHeldAndThePauseReleasesItOnce() throws Exception
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		// The canvas disappears while the button is down, so the click's own release cannot land.
		onFirstPressed.set(() -> canvasGone.set(true));

		mouse.click(TARGET, false);

		assertEquals("setup: the click's release must not have reached the canvas, saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED), receivedIds());
		assertEquals("a RELEASED that could not be delivered must leave the button tracked as held",
			InputEvent.BUTTON1_DOWN_MASK, AwtEmitter.heldMask());

		canvasGone.set(false);
		awaitCleanly(ScriptPauseController.setPaused(true), "pause cleanup with the canvas back");

		assertEquals("the pause cleanup must release the still-held button exactly once, saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), receivedIds());
		assertEquals("nothing is tracked as held once the pause released it", 0, AwtEmitter.heldMask());
	}

	@Test(timeout = 15_000)
	public void undeliveredReleaseMakesThePauseReportFailureWhileTheCanvasIsStillGone() throws Exception
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		onFirstPressed.set(() -> canvasGone.set(true));

		mouse.click(TARGET, false);

		assertEquals("a RELEASED that could not be delivered must leave the button tracked as held",
			InputEvent.BUTTON1_DOWN_MASK, AwtEmitter.heldMask());

		CompletableFuture<Void> cleanup = ScriptPauseController.setPaused(true);
		try
		{
			cleanup.get(BOUND_MS, TimeUnit.MILLISECONDS);
			fail("the pause cleanup reported success although the held button could not be released");
		}
		catch (ExecutionException expected)
		{
			// The failure report this clause asks for.
		}

		// Resume retries a failed cleanup; with the canvas back the button finally comes up once.
		canvasGone.set(false);
		awaitCleanly(ScriptPauseController.setPaused(false), "resume retrying the failed cleanup");
		assertEquals("the retried cleanup releases the button exactly once, saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), receivedIds());
	}

	@Test(timeout = 15_000)
	public void completedClickLeavesNothingHeld()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());

		mouse.click(TARGET, false);

		assertEquals("an uninterrupted click is the full triad, saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED), receivedIds());
		assertEquals("nothing is tracked as held after a click that ended normally", 0, AwtEmitter.heldMask());
	}

	@Test(timeout = 15_000)
	public void completedDragLeavesNothingHeld()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());

		mouse.drag(TARGET, DRAG_END);

		assertEquals("an uninterrupted drag presses and releases once, saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), receivedIds());
		assertEquals("nothing is tracked as held after a drag that ended normally", 0, AwtEmitter.heldMask());
	}

	/**
	 * Requests the pause and returns at once. It runs on the gesture thread, sometimes inside the
	 * PRESSED dispatch where the emitter's lock is held, and the cleanup needs that lock: waiting
	 * here would deadlock.
	 */
	private void requestPauseWithoutWaiting()
	{
		pause.set(ScriptPauseController.setPaused(true));
		pauseRequested.countDown();
	}

	private void awaitPauseCleanupThenResume() throws Exception
	{
		assertTrue("the pause hook never fired, so the scenario under test did not happen",
			pauseRequested.await(BOUND_MS, TimeUnit.MILLISECONDS));
		awaitCleanly(pause.get(), "pause cleanup");
		awaitCleanly(ScriptPauseController.setPaused(false), "resume");
	}

	private static void awaitCleanly(CompletableFuture<Void> future, String what) throws Exception
	{
		try
		{
			future.get(BOUND_MS, TimeUnit.MILLISECONDS);
		}
		catch (ExecutionException failed)
		{
			fail(what + " failed: " + failed.getCause());
		}
	}

	private Thread startDaemon(String name, Runnable body)
	{
		Thread thread = new Thread(body, name);
		thread.setDaemon(true);
		started.add(thread);
		thread.start();
		return thread;
	}

	private static void joinBounded(Thread thread) throws InterruptedException
	{
		thread.join(BOUND_MS);
		assertFalse(thread.getName() + " did not finish after resume", thread.isAlive());
	}

	private static void takeOverAt(Point point)
	{
		PointerState.setFromReal(point.getX(), point.getY());
		InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
	}

	private void assertReleasedAtHumanPoint()
	{
		Recorded release = snapshot().get(1);
		assertEquals("the abort's RELEASED must land where the pointer actually is, not the stale target",
			HUMAN_POINT.getX(), release.x);
		assertEquals(HUMAN_POINT.getY(), release.y);
	}

	private void record(MouseEvent e)
	{
		received.add(new Recorded(e.getID(), e.getX(), e.getY(), Thread.currentThread().getName()));
	}

	private List<Recorded> snapshot()
	{
		synchronized (received)
		{
			return new ArrayList<>(received);
		}
	}

	private List<Integer> receivedIds()
	{
		List<Integer> out = new ArrayList<>();
		for (Recorded event : snapshot())
		{
			out.add(event.id);
		}
		return out;
	}

	private int count(int id)
	{
		int n = 0;
		for (Recorded event : snapshot())
		{
			if (event.id == id)
			{
				n++;
			}
		}
		return n;
	}

	/** Event names with the thread that dispatched each, so a failure shows who sent the extra one. */
	private String describe()
	{
		List<String> out = new ArrayList<>();
		for (Recorded event : snapshot())
		{
			out.add(name(event.id) + " from " + event.thread);
		}
		return out.toString();
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
			default:
				return String.valueOf(id);
		}
	}

	private static List<Integer> ids(int... values)
	{
		List<Integer> out = new ArrayList<>();
		for (int value : values)
		{
			out.add(value);
		}
		return out;
	}

	private static Object swapStatic(String name, Object value) throws Exception
	{
		Field field = Microbot.class.getDeclaredField(name);
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, value);
		return previous;
	}

	private static final class Recorded
	{
		private final int id;
		private final int x;
		private final int y;
		private final String thread;

		private Recorded(int id, int x, int y, String thread)
		{
			this.id = id;
			this.x = x;
			this.y = y;
			this.thread = thread;
		}
	}
}
