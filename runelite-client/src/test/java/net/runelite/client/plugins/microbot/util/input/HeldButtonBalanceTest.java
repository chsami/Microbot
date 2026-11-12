package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A synthetic button that went down comes up exactly once, and a press that never reached the
 * canvas ends its gesture there: nothing of that gesture follows it, not even a RELEASED.
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
	/** PRESSED, RELEASED and CLICKED, in arrival order. */
	private final List<Recorded> received = Collections.synchronizedList(new ArrayList<>());
	/** MOVED and DRAGGED, kept apart so the button sequence assertions are not about motion. */
	private final List<Recorded> motions = Collections.synchronizedList(new ArrayList<>());
	private final List<Thread> started = new ArrayList<>();

	/** While set, the client reports no canvas. */
	private final AtomicBoolean canvasGone = new AtomicBoolean();
	/** One-shot, run inside the first PRESSED dispatch: on the gesture thread, under the emitter's lock. */
	private final AtomicReference<Runnable> onFirstPressed = new AtomicReference<>();
	/** One-shot, run inside the first MOVED or DRAGGED dispatch, on the gesture thread. */
	private final AtomicReference<Runnable> onFirstMotion = new AtomicReference<>();
	/** One-shot, answers the first canvas lookup made from inside a gesture after it is set. */
	private final AtomicReference<Supplier<Canvas>> firstCanvasInGesture = new AtomicReference<>();

	private final AtomicReference<CompletableFuture<Void>> pause = new AtomicReference<>();
	private final CountDownLatch pauseRequested = new CountDownLatch(1);
	/** Holds a gesture body still, inside its gesture, until the test has done its part. */
	private final CountDownLatch carryOn = new CountDownLatch(1);

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
				record(received, e);
				runOnce(onFirstPressed);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				record(received, e);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				record(received, e);
			}
		});
		canvas.addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				record(motions, e);
				runOnce(onFirstMotion);
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				record(motions, e);
				runOnce(onFirstMotion);
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
			carryOn.countDown();
			onFirstPressed.set(null);
			onFirstMotion.set(null);
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
		// Inside the PRESSED dispatch the emitter's lock is held, and the pause cleanup needs it, so
		// the cleanup cannot finish, nor the resume follow, before the press is recorded as held.
		onFirstPressed.set(this::requestPauseWithoutWaiting);

		Thread gesture = startDaemon("click-gesture", () -> mouse.click(TARGET, false));
		awaitPauseCleanupThenResume();
		joinBounded(gesture, "the click after resume");

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
		joinBounded(gesture, "the drag after resume");

		assertEquals("pause during a drag's hold, then resume: the PRESSED must be released exactly once,"
				+ " but the canvas saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), receivedIds());
	}

	// B3, B5a: a press that never reached the canvas is followed by nothing from its gesture.

	@Test(timeout = 15_000)
	public void pressWithNoCanvasThenTakeoverSendsNoRelease()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		// The press finds no canvas, and the human takes over at the same moment.
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
	public void clickWhosePressFoundNoCanvasSendsNoReleaseAndNoClick()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		// Only the press finds no canvas; it is back for anything the click might send afterwards.
		firstCanvasInGesture.set(() -> null);

		mouse.click(TARGET, false);

		assertEquals("setup: the press must not have reached the canvas, saw " + describe(),
			0, count(MouseEvent.MOUSE_PRESSED));
		assertEquals("a press the game never saw must produce no RELEASED, but the canvas saw " + describe(),
			0, count(MouseEvent.MOUSE_RELEASED));
		assertEquals("a click whose press never reached the canvas must end there and send no CLICKED,"
				+ " but the canvas saw " + describe(),
			0, count(MouseEvent.MOUSE_CLICKED));
	}

	@Test(timeout = 15_000)
	public void dragWhosePressFoundNoCanvasDoesNotTravelToTheEndPoint()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		// Armed by the move to the start point, so it is the press, the next canvas lookup, that
		// finds no canvas. The canvas is back for any travel after it.
		onFirstMotion.set(() -> firstCanvasInGesture.set(() -> null));

		mouse.drag(TARGET, DRAG_END);

		assertTrue("setup: the drag's move to its start point must have reached the canvas, saw "
				+ describeMotions(),
			motionReached(TARGET));
		assertEquals("setup: the press must not have reached the canvas, saw " + describe(),
			0, count(MouseEvent.MOUSE_PRESSED));
		assertFalse("a drag whose press never reached the canvas must end there, but it travelled to the"
				+ " end point: motion seen " + describeMotions(),
			motionReached(DRAG_END));
		assertEquals("a drag whose press never reached the canvas must send no RELEASED, saw " + describe(),
			0, count(MouseEvent.MOUSE_RELEASED));
	}

	// B5b

	@Test(timeout = 15_000)
	public void gestureWhosePressFoundNoCanvasStopsThereAndIsAborted()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		firstCanvasInGesture.set(() -> null);
		AtomicBoolean bodyContinued = new AtomicBoolean();

		InputLoop.Result result = InputLoop.run(emit ->
		{
			emit.press(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1);
			bodyContinued.set(true);
		});

		assertEquals("setup: the press must not have reached the canvas, saw " + describe(),
			0, count(MouseEvent.MOUSE_PRESSED));
		assertFalse("the gesture body ran on past a press that never reached the canvas, and InputLoop.run"
				+ " returned " + result,
			bodyContinued.get());
		assertEquals("InputLoop.run must report a gesture whose press never reached the canvas as ABORTED",
			InputLoop.Result.ABORTED, result);
	}

	@Test(timeout = 15_000)
	public void gestureWhosePressAPauseRejectedStopsThereAndIsAborted() throws Exception
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		// The pause lands after the press passed its checkpoint but before the emitter dispatched it.
		firstCanvasInGesture.set(() ->
		{
			requestPauseWithoutWaiting();
			return canvas;
		});
		AtomicBoolean bodyContinued = new AtomicBoolean();
		AtomicReference<InputLoop.Result> result = new AtomicReference<>();

		Thread gesture = startDaemon("raw-gesture", () -> result.set(InputLoop.run(emit ->
		{
			emit.press(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1);
			bodyContinued.set(true);
		})));
		awaitPauseCleanup();
		// No resume until the gesture is over, so the dispatch cannot see the pause already ended.
		joinBounded(gesture, "the gesture during the pause");

		assertEquals("setup: the press must not have reached the canvas, saw " + describe(),
			0, count(MouseEvent.MOUSE_PRESSED));
		assertFalse("the gesture body ran on past a press the pause rejected, and InputLoop.run returned "
				+ result.get(),
			bodyContinued.get());
		assertEquals("InputLoop.run must report a gesture whose press the pause rejected as ABORTED",
			InputLoop.Result.ABORTED, result.get());
	}

	// B5c

	@Test(timeout = 15_000)
	public void menuClickWhosePressFoundNoCanvasLeavesNoEntryArmed()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		firstCanvasInGesture.set(() -> null);

		mouse.click(TARGET, new NewMenuEntry());

		assertEquals("setup: the press must not have reached the canvas, saw " + describe(),
			0, count(MouseEvent.MOUSE_PRESSED));
		assertNull("a menu click whose press never reached the canvas left its entry armed, where the"
				+ " human's next click would consume it",
			Microbot.targetMenu);
	}

	@Test(timeout = 15_000)
	public void deliveredMenuClickKeepsItsEntryForTheClient()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		NewMenuEntry entry = new NewMenuEntry();

		mouse.click(TARGET, entry);

		assertEquals("an uninterrupted menu click is the full triad, saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED), receivedIds());
		assertSame("the client consumes the entry on the click it was armed for", entry, Microbot.targetMenu);
	}

	// B5d

	@Test(timeout = 15_000)
	public void clickWhosePressFoundNoCanvasIsNotRecordedAsTheLastClick()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		firstCanvasInGesture.set(() -> null);

		mouse.click(TARGET, false);

		assertEquals("setup: the press must not have reached the canvas, saw " + describe(),
			0, count(MouseEvent.MOUSE_PRESSED));
		Point last = mouse.getLastClick();
		assertFalse("a click whose press never reached the canvas was recorded as the last click at "
				+ last.getX() + "," + last.getY(),
			last.getX() == TARGET.getX() && last.getY() == TARGET.getY());
	}

	// B5e, with B3 and B5a for the same press

	@Test(timeout = 15_000)
	public void clickWhosePressAPauseRejectedReturnsDuringThePauseAndSendsNothing() throws Exception
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		// The pause lands after the press passed its checkpoint but before the emitter dispatched it.
		firstCanvasInGesture.set(() ->
		{
			requestPauseWithoutWaiting();
			return canvas;
		});

		Thread gesture = startDaemon("click-gesture", () -> mouse.click(TARGET, false));
		awaitPauseCleanup();
		// Joined before any resume. Resuming first could end the pause before the gesture thread
		// reached the dispatch, letting the press through: the outcome would depend on which thread
		// ran first.
		gesture.join(BOUND_MS);
		assertTrue("setup: the pause must still be in force", ScriptPauseController.isPaused());
		assertFalse("a click whose press the pause rejected must return while the pause is in force,"
				+ " but it is still running " + BOUND_MS + "ms later, parked until resume",
			gesture.isAlive());

		awaitCleanly(ScriptPauseController.setPaused(false), "resume");

		assertEquals("a click whose press the pause rejected must send nothing at all, before or after"
				+ " the resume, but the canvas saw " + describe(),
			ids(), receivedIds());
	}

	// B6: a gesture releases only a press it made itself and that is still down.

	@Test(timeout = 15_000)
	public void aPressByAnotherCallerAfterThePauseReleasedTheGesturesButtonIsNotTheGesturesToRelease()
		throws Exception
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		// Under the emitter's lock, so the cleanup releases the gesture's press only once it is recorded.
		onFirstPressed.set(this::requestPauseWithoutWaiting);

		Thread gesture = startDaemon("raw-gesture", () -> InputLoop.run(emit ->
		{
			emit.press(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1);
			// A latch, not a sleep: the body stays inside its gesture until the other caller's press
			// is down. Its pause generation is stale by then, so its next emit ends it and the
			// end-of-gesture sweep runs with that press down.
			awaitQuietly(carryOn);
			emit.release(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1);
		}));
		awaitPauseCleanupThenResume();
		assertEquals("setup: the pause cleanup must have released the gesture's press, saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), receivedIds());

		AwtEmitter.pressed(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1);
		assertEquals("setup: the other caller's press must be down",
			InputEvent.BUTTON1_DOWN_MASK, AwtEmitter.heldMask());
		carryOn.countDown();
		joinBounded(gesture, "the gesture after the other caller's press");

		assertEquals("the pause already released the gesture's own press, so the later press of the same"
				+ " button by another caller is not the gesture's to release, but the canvas saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_PRESSED), receivedIds());
		assertEquals("the other caller's press must stay down after the gesture ended",
			InputEvent.BUTTON1_DOWN_MASK, AwtEmitter.heldMask());

		// Unchanged: a pause releases every button that is down, whoever pressed it.
		awaitCleanly(ScriptPauseController.setPaused(true), "pause cleanup of the other caller's press");
		assertEquals("a pause must release the other caller's press, saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_PRESSED,
				MouseEvent.MOUSE_RELEASED),
			receivedIds());
		assertEquals("nothing is tracked as held once the pause released it", 0, AwtEmitter.heldMask());
	}

	@Test(timeout = 15_000)
	public void releaseOfAButtonTheGestureNeverPressedSendsNothingWhileAnotherCallerHoldsIt()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		AwtEmitter.pressed(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1);
		assertEquals("setup: the other caller's press must be down",
			InputEvent.BUTTON1_DOWN_MASK, AwtEmitter.heldMask());

		InputLoop.run(emit -> emit.release(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1));

		List<Integer> seen = receivedIds();
		String seenDescribed = describe();
		boolean stillDown = AwtEmitter.heldMask() == InputEvent.BUTTON1_DOWN_MASK;
		// The other caller releases its own press, so teardown does not report it as leaked.
		AwtEmitter.released(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1);
		assertEquals("a gesture that never pressed the button must send no RELEASED for it, even while"
				+ " another caller holds it down, but the canvas saw " + seenDescribed,
			ids(MouseEvent.MOUSE_PRESSED), seen);
		assertTrue("the other caller's press must stay down after the gesture ended", stillDown);
	}

	@Test(timeout = 15_000)
	public void releaseOfAButtonNobodyPressedSendsNothing()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());

		InputLoop.run(emit -> emit.release(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1));

		assertEquals("a release for a button nobody pressed must send nothing, saw " + describe(),
			ids(), receivedIds());
	}

	@Test(timeout = 15_000)
	public void aDirectReleaseOfTheGesturesPressIsTheOnlyRelease() throws Exception
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());
		CountDownLatch pressed = new CountDownLatch(1);

		Thread gesture = startDaemon("raw-gesture", () -> InputLoop.run(emit ->
		{
			emit.press(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1);
			pressed.countDown();
			awaitQuietly(carryOn);
			emit.release(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1);
		}));
		assertTrue("setup: the gesture never pressed", pressed.await(BOUND_MS, TimeUnit.MILLISECONDS));

		AwtEmitter.released(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1);
		carryOn.countDown();
		joinBounded(gesture, "the gesture after the direct release");

		assertEquals("a direct release of the gesture's press must be the only RELEASED: the gesture must"
				+ " send no second one, but the canvas saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), receivedIds());
		assertEquals("nothing is tracked as held once the press was released", 0, AwtEmitter.heldMask());
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
	public void completedClickLeavesNothingHeldAndIsRecordedAsTheLastClick()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());

		mouse.click(TARGET, false);

		assertEquals("an uninterrupted click is the full triad, saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED), receivedIds());
		assertEquals("nothing is tracked as held after a click that ended normally", 0, AwtEmitter.heldMask());
		Point last = mouse.getLastClick();
		assertTrue("a delivered click is recorded as the last click, but last click is "
				+ last.getX() + "," + last.getY(),
			last.getX() == TARGET.getX() && last.getY() == TARGET.getY());
	}

	@Test(timeout = 15_000)
	public void completedDragLeavesNothingHeld()
	{
		PointerState.setFromBot(TARGET.getX(), TARGET.getY());

		mouse.drag(TARGET, DRAG_END);

		assertEquals("an uninterrupted drag presses and releases once, saw " + describe(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), receivedIds());
		assertTrue("an uninterrupted drag travels to its end point, motion seen " + describeMotions(),
			motionReached(DRAG_END));
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

	private static void awaitQuietly(CountDownLatch latch)
	{
		try
		{
			latch.await(BOUND_MS, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
		}
	}

	private void awaitPauseCleanup() throws Exception
	{
		assertTrue("the pause hook never fired, so the scenario under test did not happen",
			pauseRequested.await(BOUND_MS, TimeUnit.MILLISECONDS));
		awaitCleanly(pause.get(), "pause cleanup");
	}

	private void awaitPauseCleanupThenResume() throws Exception
	{
		awaitPauseCleanup();
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

	private static void joinBounded(Thread thread, String what) throws InterruptedException
	{
		thread.join(BOUND_MS);
		assertFalse(what + " did not finish within " + BOUND_MS + "ms", thread.isAlive());
	}

	private static void runOnce(AtomicReference<Runnable> hook)
	{
		Runnable once = hook.getAndSet(null);
		if (once != null)
		{
			once.run();
		}
	}

	private static void takeOverAt(Point point)
	{
		PointerState.setFromReal(point.getX(), point.getY());
		InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
	}

	private void assertReleasedAtHumanPoint()
	{
		Recorded release = snapshot(received).get(1);
		assertEquals("the abort's RELEASED must land where the pointer actually is, not the stale target",
			HUMAN_POINT.getX(), release.x);
		assertEquals(HUMAN_POINT.getY(), release.y);
	}

	private boolean motionReached(Point point)
	{
		for (Recorded motion : snapshot(motions))
		{
			if (motion.x == point.getX() && motion.y == point.getY())
			{
				return true;
			}
		}
		return false;
	}

	private static void record(List<Recorded> into, MouseEvent e)
	{
		into.add(new Recorded(e.getID(), e.getX(), e.getY(), Thread.currentThread().getName()));
	}

	private static List<Recorded> snapshot(List<Recorded> list)
	{
		synchronized (list)
		{
			return new ArrayList<>(list);
		}
	}

	private List<Integer> receivedIds()
	{
		List<Integer> out = new ArrayList<>();
		for (Recorded event : snapshot(received))
		{
			out.add(event.id);
		}
		return out;
	}

	private int count(int id)
	{
		int n = 0;
		for (Recorded event : snapshot(received))
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
		for (Recorded event : snapshot(received))
		{
			out.add(name(event.id) + " from " + event.thread);
		}
		return out.toString();
	}

	private String describeMotions()
	{
		List<String> out = new ArrayList<>();
		for (Recorded event : snapshot(motions))
		{
			out.add(name(event.id) + " at " + event.x + "," + event.y);
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
			case MouseEvent.MOUSE_MOVED:
				return "MOVED";
			case MouseEvent.MOUSE_DRAGGED:
				return "DRAGGED";
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
