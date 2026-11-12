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
import java.awt.event.MouseMotionAdapter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * No event of a gesture reaches the canvas once a pause has begun after that gesture started,
 * even when the pause is already over by the time the emitter dispatches, and the emit that was
 * refused ends its gesture. An emit either puts its event on the canvas or ends the gesture. The
 * same gate holds for an emitting call made outside any gesture, which simply sends nothing.
 *
 * <p>The pause, and the resume, are driven from the canvas lookup the emitter makes on the
 * calling thread, so they land between the emit's entry and its dispatch without any race.
 * Gestures here are bodies of the test's own, so what ran after the emit and what
 * {@link InputLoop#run} returned are observed directly.
 */
public class GesturePauseGateTest
{
	private static final long BOUND_MS = 3_000L;
	private static final int START_X = 100;
	private static final int START_Y = 100;
	private static final int EMIT_X = 120;
	private static final int EMIT_Y = 110;
	private static final int CANVAS_WIDTH = 765;
	private static final int CANVAS_HEIGHT = 503;
	private static final int OFF_CANVAS_X = CANVAS_WIDTH + 60;

	/** Emitting calls made outside any gesture. */
	private enum Direct
	{
		VIRTUAL_MOUSE_MOVE(MouseEvent.MOUSE_MOVED),
		MOVED(MouseEvent.MOUSE_MOVED),
		PRESSED(MouseEvent.MOUSE_PRESSED),
		CLICKED(MouseEvent.MOUSE_CLICKED),
		WHEEL(MouseEvent.MOUSE_WHEEL);

		private final int eventId;

		Direct(int eventId)
		{
			this.eventId = eventId;
		}

		void call(VirtualMouse mouse)
		{
			switch (this)
			{
				case VIRTUAL_MOUSE_MOVE:
					mouse.move(new Point(EMIT_X, EMIT_Y));
					break;
				case MOVED:
					AwtEmitter.moved(EMIT_X, EMIT_Y);
					break;
				case PRESSED:
					AwtEmitter.pressed(EMIT_X, EMIT_Y, MouseEvent.BUTTON1);
					break;
				case CLICKED:
					AwtEmitter.clicked(EMIT_X, EMIT_Y, MouseEvent.BUTTON1);
					break;
				default:
					AwtEmitter.wheel(EMIT_X, EMIT_Y, 2, 10);
					break;
			}
		}
	}

	private enum Kind
	{
		MOVE(MouseEvent.MOUSE_MOVED),
		PRESS(MouseEvent.MOUSE_PRESSED),
		CLICK(MouseEvent.MOUSE_CLICKED),
		WHEEL(MouseEvent.MOUSE_WHEEL);

		private final int eventId;

		Kind(int eventId)
		{
			this.eventId = eventId;
		}

		void emit(InputLoop.Emit emit)
		{
			switch (this)
			{
				case MOVE:
					emit.move(EMIT_X, EMIT_Y);
					break;
				case PRESS:
					emit.press(EMIT_X, EMIT_Y, MouseEvent.BUTTON1);
					break;
				case CLICK:
					emit.click(EMIT_X, EMIT_Y, MouseEvent.BUTTON1);
					break;
				default:
					emit.wheel(EMIT_X, EMIT_Y, 2, 10);
					break;
			}
		}
	}

	private Client client;
	private Canvas canvas;
	private VirtualMouse mouse;
	/** Every mouse event the canvas receives, in arrival order. */
	private final List<Integer> received = Collections.synchronizedList(new ArrayList<>());

	/** One-shot, answers the first canvas lookup made from inside a gesture after it is set. */
	private final AtomicReference<Supplier<Canvas>> firstCanvasInGesture = new AtomicReference<>();
	/**
	 * One-shot, answers the first canvas lookup the test thread makes outside any gesture. Tied to
	 * the arming thread so a lookup by the pause cleanup cannot consume it.
	 */
	private final AtomicReference<Supplier<Canvas>> firstCanvasOutsideGesture = new AtomicReference<>();
	private volatile Thread outsideHookOwner;
	/** While set, the client reports no canvas. */
	private final AtomicBoolean canvasGone = new AtomicBoolean();
	private final AtomicBoolean hookRan = new AtomicBoolean();
	/** Whether the resume completed inside the lookup, so a failure says which window was tested. */
	private final AtomicBoolean resumedInsideLookup = new AtomicBoolean();
	private final List<String> hookProblems = Collections.synchronizedList(new ArrayList<>());

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
				received.add(e.getID());
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				received.add(e.getID());
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				received.add(e.getID());
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				received.add(e.getID());
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				received.add(e.getID());
			}
		});
		canvas.addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				received.add(e.getID());
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				received.add(e.getID());
			}
		});
		canvas.addMouseWheelListener(e -> received.add(e.getID()));

		client = mock(Client.class);
		when(client.getCanvas()).thenAnswer(invocation ->
		{
			if (InputLoop.isInGesture())
			{
				Supplier<Canvas> hook = firstCanvasInGesture.getAndSet(null);
				if (hook != null)
				{
					hookRan.set(true);
					return hook.get();
				}
			}
			else if (Thread.currentThread() == outsideHookOwner)
			{
				Supplier<Canvas> hook = firstCanvasOutsideGesture.getAndSet(null);
				if (hook != null)
				{
					hookRan.set(true);
					return hook.get();
				}
			}
			return canvasGone.get() ? null : canvas;
		});
		when(client.isClientThread()).thenReturn(false);
		when(client.isStretchedEnabled()).thenReturn(false);

		previousClient = swapStatic("client", client);
		previousNaturalMouse = swapStatic("naturalMouse", null);
		Microbot.targetMenu = null;

		PointerState.reset();
		InputArbiter.resetForTest();
		AwtEmitter.resetHeldButtonsForTest();
		InputLoop.setLockTimeoutForTest(5_000L);
		PointerState.setFromBot(START_X, START_Y);

		mouse = new VirtualMouse();
	}

	@After
	public void after() throws Exception
	{
		List<String> problems = new ArrayList<>();
		try
		{
			firstCanvasInGesture.set(null);
			firstCanvasOutsideGesture.set(null);
			outsideHookOwner = null;
			canvasGone.set(false);
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

	// G1a and G1b: a whole pause and resume inside the emit.

	@Test(timeout = 15_000)
	public void moveIsRefusedAndEndsTheGestureWhenAPauseAndResumeLandInsideIt()
	{
		assertRefusedAfterPauseAndResume(Kind.MOVE);
	}

	@Test(timeout = 15_000)
	public void pressIsRefusedAndEndsTheGestureWhenAPauseAndResumeLandInsideIt()
	{
		assertRefusedAfterPauseAndResume(Kind.PRESS);
	}

	@Test(timeout = 15_000)
	public void clickIsRefusedAndEndsTheGestureWhenAPauseAndResumeLandInsideIt()
	{
		assertRefusedAfterPauseAndResume(Kind.CLICK);
	}

	@Test(timeout = 15_000)
	public void wheelIsRefusedAndEndsTheGestureWhenAPauseAndResumeLandInsideIt()
	{
		assertRefusedAfterPauseAndResume(Kind.WHEEL);
	}

	// G1a and G1b: a pause still in force at the dispatch. The press is in HeldButtonBalanceTest.

	@Test(timeout = 15_000)
	public void moveIsRefusedAndEndsTheGestureWhenAPauseBeginsInsideIt()
	{
		assertRefusedDuringPause(Kind.MOVE);
	}

	@Test(timeout = 15_000)
	public void clickIsRefusedAndEndsTheGestureWhenAPauseBeginsInsideIt()
	{
		assertRefusedDuringPause(Kind.CLICK);
	}

	@Test(timeout = 15_000)
	public void wheelIsRefusedAndEndsTheGestureWhenAPauseBeginsInsideIt()
	{
		assertRefusedDuringPause(Kind.WHEEL);
	}

	// Unchanged: with no pause the event goes out and the gesture completes.

	@Test(timeout = 15_000)
	public void moveWithNoPauseIsDispatchedAndTheGestureCompletes()
	{
		assertDispatchedWithNoPause(Kind.MOVE);
	}

	@Test(timeout = 15_000)
	public void pressWithNoPauseIsDispatchedAndTheGestureCompletes()
	{
		assertDispatchedWithNoPause(Kind.PRESS);
	}

	@Test(timeout = 15_000)
	public void clickWithNoPauseIsDispatchedAndTheGestureCompletes()
	{
		assertDispatchedWithNoPause(Kind.CLICK);
	}

	@Test(timeout = 15_000)
	public void wheelWithNoPauseIsDispatchedAndTheGestureCompletes()
	{
		assertDispatchedWithNoPause(Kind.WHEEL);
	}

	private void assertRefusedAfterPauseAndResume(Kind kind)
	{
		firstCanvasInGesture.set(this::pauseAndResumeThenReturnCanvas);
		AtomicBoolean bodyContinued = new AtomicBoolean();

		InputLoop.Result result = runEmitting(kind, bodyContinued);

		assertTrue("setup: the emit never looked up the canvas inside the gesture, so no pause landed in it",
			hookRan.get());
		assertTrue("setup: the hook failed: " + hookProblems, hookProblems.isEmpty());
		String window = resumedInsideLookup.get()
			? "a pause and its resume both landed inside the " + kind + " emit"
			: "a pause landed inside the " + kind + " emit (its resume could not, the cleanup was held up)";
		assertEquals(window + ", after the gesture started: no event of that gesture may reach the canvas,"
				+ " but it saw " + names(),
			Collections.emptyList(), snapshot());
		assertFalse(window + ": the refused emit must end the gesture, but the body ran on past it, and"
				+ " InputLoop.run returned " + result,
			bodyContinued.get());
		assertEquals(window + ": InputLoop.run must report the gesture as ABORTED",
			InputLoop.Result.ABORTED, result);
	}

	private void assertRefusedDuringPause(Kind kind)
	{
		// Not awaited: the pause is in force from here until teardown, whatever the cleanup does.
		firstCanvasInGesture.set(() ->
		{
			ScriptPauseController.setPaused(true);
			return canvas;
		});
		AtomicBoolean bodyContinued = new AtomicBoolean();

		InputLoop.Result result = runEmitting(kind, bodyContinued);

		assertTrue("setup: the emit never looked up the canvas inside the gesture, so no pause landed in it",
			hookRan.get());
		assertEquals("a pause began inside the " + kind + " emit: no event of that gesture may reach the"
				+ " canvas, but it saw " + names(),
			Collections.emptyList(), snapshot());
		assertFalse("a pause began inside the " + kind + " emit: the refused emit must end the gesture, but"
				+ " the body ran on past it, and InputLoop.run returned " + result,
			bodyContinued.get());
		assertEquals("a pause began inside the " + kind + " emit: InputLoop.run must report the gesture as"
				+ " ABORTED",
			InputLoop.Result.ABORTED, result);
	}

	private void assertDispatchedWithNoPause(Kind kind)
	{
		AtomicBoolean bodyContinued = new AtomicBoolean();

		InputLoop.Result result = runEmitting(kind, bodyContinued);

		List<Integer> seen = snapshot();
		assertFalse("with no pause the " + kind + " must reach the canvas, but it saw " + names(), seen.isEmpty());
		assertEquals("with no pause the " + kind + " is the gesture's first event, saw " + names(),
			kind.eventId, (int) seen.get(0));
		assertTrue("with no pause the gesture body runs on past the " + kind, bodyContinued.get());
		assertEquals("with no pause the gesture completes", InputLoop.Result.COMPLETED, result);
	}

	// G1c: an emit either puts its event on the canvas or ends the gesture. The press is in
	// HeldButtonBalanceTest.

	@Test(timeout = 15_000)
	public void moveThatFindsNoCanvasEndsTheGesture()
	{
		assertNoCanvasEndsTheGesture(Kind.MOVE);
	}

	@Test(timeout = 15_000)
	public void clickThatFindsNoCanvasEndsTheGesture()
	{
		assertNoCanvasEndsTheGesture(Kind.CLICK);
	}

	@Test(timeout = 15_000)
	public void wheelThatFindsNoCanvasEndsTheGesture()
	{
		assertNoCanvasEndsTheGesture(Kind.WHEEL);
	}

	// Unchanged: leaving the canvas is a destination, not a failure.

	@Test(timeout = 15_000)
	public void moveOffTheCanvasSendsExitedAndTheGestureCompletes()
	{
		giveTheCanvasASize();
		AtomicBoolean bodyContinued = new AtomicBoolean();

		InputLoop.Result result = InputLoop.run(emit ->
		{
			emit.move(OFF_CANVAS_X, EMIT_Y);
			bodyContinued.set(true);
		});

		assertEquals("a move off the canvas announces the exit instead of a MOVED, saw " + names(),
			Collections.singletonList(MouseEvent.MOUSE_EXITED), snapshot());
		assertTrue("a move off the canvas is not a failure: the gesture body must run on past it",
			bodyContinued.get());
		assertEquals("a move off the canvas is not a failure: the gesture completes",
			InputLoop.Result.COMPLETED, result);
	}

	@Test(timeout = 15_000)
	public void moveOffTheCanvasWhileAlreadyOutsideSendsNothingAndTheGestureCompletes()
	{
		giveTheCanvasASize();
		PointerState.setOutside(OFF_CANVAS_X, EMIT_Y);
		AtomicBoolean bodyContinued = new AtomicBoolean();

		InputLoop.Result result = InputLoop.run(emit ->
		{
			emit.move(OFF_CANVAS_X + 40, EMIT_Y);
			bodyContinued.set(true);
		});

		assertEquals("a move between two points off the canvas has nothing to send, saw " + names(),
			Collections.emptyList(), snapshot());
		assertTrue("a move off the canvas is not a failure: the gesture body must run on past it",
			bodyContinued.get());
		assertEquals("a move off the canvas is not a failure: the gesture completes",
			InputLoop.Result.COMPLETED, result);
	}

	// G1d: the same gate for emits made outside any gesture.

	@Test(timeout = 15_000)
	public void virtualMouseMoveOutsideAGestureIsNotDispatchedAfterAPauseAndResumeLandInsideIt()
	{
		assertNotDispatchedAfterPauseAndResumeOutsideAGesture(Direct.VIRTUAL_MOUSE_MOVE);
	}

	@Test(timeout = 15_000)
	public void directMovedIsNotDispatchedAfterAPauseAndResumeLandInsideIt()
	{
		assertNotDispatchedAfterPauseAndResumeOutsideAGesture(Direct.MOVED);
	}

	@Test(timeout = 15_000)
	public void directPressedIsNotDispatchedAfterAPauseAndResumeLandInsideIt()
	{
		assertNotDispatchedAfterPauseAndResumeOutsideAGesture(Direct.PRESSED);
	}

	@Test(timeout = 15_000)
	public void directClickedIsNotDispatchedAfterAPauseAndResumeLandInsideIt()
	{
		assertNotDispatchedAfterPauseAndResumeOutsideAGesture(Direct.CLICKED);
	}

	@Test(timeout = 15_000)
	public void directWheelIsNotDispatchedAfterAPauseAndResumeLandInsideIt()
	{
		assertNotDispatchedAfterPauseAndResumeOutsideAGesture(Direct.WHEEL);
	}

	// Unchanged outside a gesture.

	@Test(timeout = 15_000)
	public void virtualMouseMoveOutsideAGestureWithNoPauseIsDispatched()
	{
		assertDispatchedOutsideAGestureWithNoPause(Direct.VIRTUAL_MOUSE_MOVE);
	}

	@Test(timeout = 15_000)
	public void directMovedWithNoPauseIsDispatched()
	{
		assertDispatchedOutsideAGestureWithNoPause(Direct.MOVED);
	}

	@Test(timeout = 15_000)
	public void directPressedWithNoPauseIsDispatched()
	{
		assertDispatchedOutsideAGestureWithNoPause(Direct.PRESSED);
	}

	@Test(timeout = 15_000)
	public void directClickedWithNoPauseIsDispatched()
	{
		assertDispatchedOutsideAGestureWithNoPause(Direct.CLICKED);
	}

	@Test(timeout = 15_000)
	public void directWheelWithNoPauseIsDispatched()
	{
		assertDispatchedOutsideAGestureWithNoPause(Direct.WHEEL);
	}

	@Test(timeout = 15_000)
	public void directReleasedForAHeldButtonGoesOutDuringAPause() throws Exception
	{
		AwtEmitter.pressed(EMIT_X, EMIT_Y, MouseEvent.BUTTON1);
		assertEquals("setup: the press must be tracked as held", InputEvent.BUTTON1_DOWN_MASK, AwtEmitter.heldMask());

		// With no canvas the pause cleanup cannot release the button, so it is still down once the
		// pause is in force.
		canvasGone.set(true);
		try
		{
			ScriptPauseController.setPaused(true).get(BOUND_MS, TimeUnit.MILLISECONDS);
		}
		catch (ExecutionException expected)
		{
			// The cleanup reports the button it could not release.
		}
		canvasGone.set(false);
		assertTrue("setup: the pause must be in force", ScriptPauseController.isPaused());
		assertEquals("setup: the button must still be down during the pause",
			InputEvent.BUTTON1_DOWN_MASK, AwtEmitter.heldMask());

		AwtEmitter.released(EMIT_X, EMIT_Y, MouseEvent.BUTTON1);

		assertEquals("a RELEASED owed for a button that is down must go out during a pause, saw " + names(),
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), snapshot());
		assertEquals("nothing is tracked as held once the RELEASED went out", 0, AwtEmitter.heldMask());
	}

	private void assertNoCanvasEndsTheGesture(Kind kind)
	{
		firstCanvasInGesture.set(() -> null);
		AtomicBoolean bodyContinued = new AtomicBoolean();

		InputLoop.Result result = runEmitting(kind, bodyContinued);

		assertTrue("setup: the emit never looked up the canvas inside the gesture", hookRan.get());
		assertEquals("setup: with no canvas nothing can reach it, saw " + names(),
			Collections.emptyList(), snapshot());
		assertFalse("a " + kind + " that found no canvas must end the gesture, but the body ran on past it,"
				+ " and InputLoop.run returned " + result,
			bodyContinued.get());
		assertEquals("a " + kind + " that found no canvas: InputLoop.run must report the gesture as ABORTED",
			InputLoop.Result.ABORTED, result);
	}

	private void assertNotDispatchedAfterPauseAndResumeOutsideAGesture(Direct direct)
	{
		outsideHookOwner = Thread.currentThread();
		firstCanvasOutsideGesture.set(this::pauseAndResumeThenReturnCanvas);

		direct.call(mouse);

		List<Integer> seen = snapshot();
		// A press that went out would otherwise be left down for the teardown check to report as a
		// second, misleading failure.
		if (AwtEmitter.heldMask() != 0)
		{
			AwtEmitter.releaseHeldButtons();
		}
		assertTrue("setup: the call never looked up the canvas, so no pause landed in it", hookRan.get());
		assertTrue("setup: the hook failed: " + hookProblems, hookProblems.isEmpty());
		String window = resumedInsideLookup.get()
			? "a pause and its resume both landed inside " + direct + " called outside any gesture"
			: "a pause landed inside " + direct + " called outside any gesture (its resume could not)";
		assertEquals(window + ": nothing may reach the canvas, but it saw " + names(seen),
			Collections.emptyList(), seen);
	}

	private void assertDispatchedOutsideAGestureWithNoPause(Direct direct)
	{
		direct.call(mouse);

		List<Integer> seen = snapshot();
		if (AwtEmitter.heldMask() != 0)
		{
			AwtEmitter.released(EMIT_X, EMIT_Y, MouseEvent.BUTTON1);
		}
		assertEquals("with no pause, " + direct + " outside a gesture is dispatched as before, saw " + names(seen),
			Collections.singletonList(direct.eventId), seen);
	}

	private void giveTheCanvasASize()
	{
		// Without a size the emitter treats every point as inside and never exits.
		when(client.getCanvasWidth()).thenReturn(CANVAS_WIDTH);
		when(client.getCanvasHeight()).thenReturn(CANVAS_HEIGHT);
	}

	private static InputLoop.Result runEmitting(Kind kind, AtomicBoolean bodyContinued)
	{
		return InputLoop.run(emit ->
		{
			kind.emit(emit);
			bodyContinued.set(true);
		});
	}

	/**
	 * Runs on the gesture thread, inside the emit. Waiting here is safe while the emitter does not
	 * hold its button lock across the lookup, which the cleanup needs. If an emitter did, no resume
	 * could land inside the emit at all; the wait is bounded and the pause alone then stays in force
	 * at the dispatch, which the contract treats the same.
	 */
	private Canvas pauseAndResumeThenReturnCanvas()
	{
		CompletableFuture<Void> cleanup = ScriptPauseController.setPaused(true);
		try
		{
			cleanup.get(BOUND_MS, TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException heldUp)
		{
			return canvas;
		}
		catch (Exception ex)
		{
			hookProblems.add("pause cleanup failed: " + ex);
			return canvas;
		}
		try
		{
			ScriptPauseController.setPaused(false).get(BOUND_MS, TimeUnit.MILLISECONDS);
			resumedInsideLookup.set(true);
		}
		catch (Exception ex)
		{
			hookProblems.add("resume failed: " + ex);
		}
		return canvas;
	}

	private List<Integer> snapshot()
	{
		synchronized (received)
		{
			return new ArrayList<>(received);
		}
	}

	private String names()
	{
		return names(snapshot());
	}

	private static String names(List<Integer> events)
	{
		List<String> out = new ArrayList<>();
		for (int id : events)
		{
			out.add(name(id));
		}
		return out.toString();
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
			case MouseEvent.MOUSE_ENTERED:
				return "ENTERED";
			case MouseEvent.MOUSE_EXITED:
				return "EXITED";
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
