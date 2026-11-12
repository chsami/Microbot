package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.api.Point;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The tracked pointer, {@link PointerState}, changes only with synthetic events that actually went
 * out. A refused event leaves the position, the last bot point and the outside flag as they were,
 * so the record and the game keep agreeing about where the pointer is and whether one is over the
 * canvas.
 *
 * <p>A pause that lands inside an emit is driven from the canvas lookup the emitter makes on the
 * calling thread: it lands after the emitter's entry check and before its dispatch, without any race.
 */
public class PointerRecordGateTest
{
	private static final long BOUND_MS = 3_000L;
	private static final int CANVAS_WIDTH = 765;
	private static final int CANVAS_HEIGHT = 503;
	private static final Point START = new Point(100, 100);
	private static final Point TARGET = new Point(120, 110);
	private static final Point NEXT = new Point(130, 110);
	private static final Point OFF_CANVAS = new Point(CANVAS_WIDTH + 60, 110);
	private static final Point HUMAN_POINT = new Point(640, 480);

	private enum Kind
	{
		MOVE,
		PRESS,
		CLICK,
		WHEEL;

		void direct(Point at)
		{
			switch (this)
			{
				case MOVE:
					AwtEmitter.moved(at.getX(), at.getY());
					break;
				case PRESS:
					AwtEmitter.pressed(at.getX(), at.getY(), MouseEvent.BUTTON1);
					break;
				case CLICK:
					AwtEmitter.clicked(at.getX(), at.getY(), MouseEvent.BUTTON1);
					break;
				default:
					AwtEmitter.wheel(at.getX(), at.getY(), 2, 10);
					break;
			}
		}

		void emit(InputLoop.Emit emit, Point at)
		{
			switch (this)
			{
				case MOVE:
					emit.move(at.getX(), at.getY());
					break;
				case PRESS:
					emit.press(at.getX(), at.getY(), MouseEvent.BUTTON1);
					break;
				case CLICK:
					emit.click(at.getX(), at.getY(), MouseEvent.BUTTON1);
					break;
				default:
					emit.wheel(at.getX(), at.getY(), 2, 10);
					break;
			}
		}

		/** Through a gesture of the test's own, or as a direct call outside any gesture. */
		void send(boolean inGesture, Point at)
		{
			if (inGesture)
			{
				InputLoop.run(emit -> emit(emit, at));
			}
			else
			{
				direct(at);
			}
		}
	}

	/** The parts of {@link PointerState} this clause is about, captured at one moment. */
	private static final class Record
	{
		private final Point position;
		private final Point lastBot;
		private final boolean hasBot;
		private final boolean outside;

		private Record()
		{
			position = PointerState.get();
			lastBot = PointerState.lastBotPoint();
			hasBot = PointerState.hasBotPoint();
			outside = PointerState.isOutside();
		}

		/** What moved between this record and a later one, empty when nothing did. */
		private List<String> changesTo(Record later)
		{
			List<String> out = new ArrayList<>();
			if (!same(position, later.position))
			{
				out.add("position " + text(position) + " -> " + text(later.position));
			}
			if (!same(lastBot, later.lastBot))
			{
				out.add("lastBotPoint " + text(lastBot) + " -> " + text(later.lastBot));
			}
			if (hasBot != later.hasBot)
			{
				out.add("hasBotPoint " + hasBot + " -> " + later.hasBot);
			}
			if (outside != later.outside)
			{
				out.add("isOutside " + outside + " -> " + later.outside);
			}
			return out;
		}

		private static boolean same(Point a, Point b)
		{
			return a.getX() == b.getX() && a.getY() == b.getY();
		}
	}

	private Client client;
	private Canvas canvas;
	/** Every mouse event the canvas receives, in arrival order. */
	private final List<Integer> received = Collections.synchronizedList(new ArrayList<>());

	/** One-shot, answers the test thread's first canvas lookup after it is set. */
	private final AtomicReference<Supplier<Canvas>> nextLookup = new AtomicReference<>();
	private volatile Thread hookOwner;
	/** While set, the client reports no canvas. */
	private volatile boolean canvasGone;

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
			if (Thread.currentThread() == hookOwner)
			{
				Supplier<Canvas> hook = nextLookup.getAndSet(null);
				if (hook != null)
				{
					return hook.get();
				}
			}
			return canvasGone ? null : canvas;
		});
		when(client.isClientThread()).thenReturn(false);
		when(client.isStretchedEnabled()).thenReturn(false);
		// A size, so a point off the canvas is recognised as one and an exit can happen.
		when(client.getCanvasWidth()).thenReturn(CANVAS_WIDTH);
		when(client.getCanvasHeight()).thenReturn(CANVAS_HEIGHT);

		previousClient = swapStatic("client", client);
		previousNaturalMouse = swapStatic("naturalMouse", null);
		Microbot.targetMenu = null;

		PointerState.reset();
		InputArbiter.resetForTest();
		AwtEmitter.resetHeldButtonsForTest();
		InputLoop.setLockTimeoutForTest(5_000L);
		PointerState.setFromBot(START.getX(), START.getY());
	}

	@After
	public void after() throws Exception
	{
		List<String> problems = new ArrayList<>();
		try
		{
			nextLookup.set(null);
			hookOwner = null;
			canvasGone = false;
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

	// P1a: a pause lands inside the emit, after its entry check and before its dispatch.

	@Test(timeout = 15_000)
	public void moveOutsideAGestureRefusedByAPauseLandingInsideItLeavesTheRecord()
	{
		assertRecordKeptWhenAPauseLandsInside(Kind.MOVE, false);
	}

	@Test(timeout = 15_000)
	public void pressOutsideAGestureRefusedByAPauseLandingInsideItLeavesTheRecord()
	{
		assertRecordKeptWhenAPauseLandsInside(Kind.PRESS, false);
	}

	@Test(timeout = 15_000)
	public void clickOutsideAGestureRefusedByAPauseLandingInsideItLeavesTheRecord()
	{
		assertRecordKeptWhenAPauseLandsInside(Kind.CLICK, false);
	}

	@Test(timeout = 15_000)
	public void wheelOutsideAGestureRefusedByAPauseLandingInsideItLeavesTheRecord()
	{
		assertRecordKeptWhenAPauseLandsInside(Kind.WHEEL, false);
	}

	@Test(timeout = 15_000)
	public void moveInsideAGestureRefusedByAPauseLandingInsideItLeavesTheRecord()
	{
		assertRecordKeptWhenAPauseLandsInside(Kind.MOVE, true);
	}

	@Test(timeout = 15_000)
	public void pressInsideAGestureRefusedByAPauseLandingInsideItLeavesTheRecord()
	{
		assertRecordKeptWhenAPauseLandsInside(Kind.PRESS, true);
	}

	@Test(timeout = 15_000)
	public void clickInsideAGestureRefusedByAPauseLandingInsideItLeavesTheRecord()
	{
		assertRecordKeptWhenAPauseLandsInside(Kind.CLICK, true);
	}

	@Test(timeout = 15_000)
	public void wheelInsideAGestureRefusedByAPauseLandingInsideItLeavesTheRecord()
	{
		assertRecordKeptWhenAPauseLandsInside(Kind.WHEEL, true);
	}

	// P1a: refusals that already leave the record alone, pinned so a restructuring keeps it so.

	@Test(timeout = 15_000)
	public void moveOutsideAGestureDuringAPauseLeavesTheRecord() throws Exception
	{
		assertRecordKeptDuringAPause(Kind.MOVE);
	}

	@Test(timeout = 15_000)
	public void pressOutsideAGestureDuringAPauseLeavesTheRecord() throws Exception
	{
		assertRecordKeptDuringAPause(Kind.PRESS);
	}

	@Test(timeout = 15_000)
	public void moveOutsideAGestureWithNoCanvasLeavesTheRecord()
	{
		assertRecordKeptWithNoCanvas(Kind.MOVE, false);
	}

	@Test(timeout = 15_000)
	public void pressOutsideAGestureWithNoCanvasLeavesTheRecord()
	{
		assertRecordKeptWithNoCanvas(Kind.PRESS, false);
	}

	@Test(timeout = 15_000)
	public void moveInsideAGestureWithNoCanvasLeavesTheRecord()
	{
		assertRecordKeptWithNoCanvas(Kind.MOVE, true);
	}

	@Test(timeout = 15_000)
	public void pressInsideAGestureWithNoCanvasLeavesTheRecord()
	{
		assertRecordKeptWithNoCanvas(Kind.PRESS, true);
	}

	@Test(timeout = 15_000)
	public void moveInsideAGestureAfterATakeoverLeavesTheRecord()
	{
		assertRecordKeptAfterATakeover(Kind.MOVE);
	}

	@Test(timeout = 15_000)
	public void pressInsideAGestureAfterATakeoverLeavesTheRecord()
	{
		assertRecordKeptAfterATakeover(Kind.PRESS);
	}

	// P1b: a refused event leaves an outside pointer outside, and the next one announces the return.

	@Test(timeout = 15_000)
	public void refusedMoveOutsideAGestureKeepsThePointerOutsideAndTheNextMoveEntersFirst() throws Exception
	{
		assertStillOutsideAfterARefusal(Kind.MOVE, false);
	}

	@Test(timeout = 15_000)
	public void refusedPressInsideAGestureKeepsThePointerOutsideAndTheNextMoveEntersFirst() throws Exception
	{
		assertStillOutsideAfterARefusal(Kind.PRESS, true);
	}

	// P1c

	@Test(timeout = 15_000)
	public void moveOffTheCanvasWhoseExitIsRefusedDoesNotRecordThePointerAsOutside() throws Exception
	{
		Record before = new Record();
		pauseInsideTheNextLookup();

		AwtEmitter.moved(OFF_CANVAS.getX(), OFF_CANVAS.getY());

		List<Integer> seen = snapshot();
		List<String> moved = before.changesTo(new Record());
		assertEquals("setup: the EXITED must have been refused, saw " + names(seen), Collections.emptyList(), seen);
		assertEquals("a move off the canvas whose EXITED was refused must leave the pointer record as it was,"
				+ " but " + moved + ", while the canvas saw " + names(seen),
			Collections.emptyList(), moved);

		resume();
		AwtEmitter.moved(NEXT.getX(), NEXT.getY());
		assertEquals("the game never saw the pointer leave, so the next move must not announce a return,"
				+ " saw " + names(),
			Collections.singletonList(MouseEvent.MOUSE_MOVED), snapshot());
	}

	// P1d

	@Test(timeout = 15_000)
	public void pauseCleanupReleasingAButtonWhileOutsideKeepsThePointerOutside() throws Exception
	{
		AwtEmitter.pressed(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1);
		// The human's pointer leaves the canvas with the bot's button still down.
		PointerState.setOutside(OFF_CANVAS.getX(), OFF_CANVAS.getY());
		received.clear();

		ScriptPauseController.setPaused(true).get(BOUND_MS, TimeUnit.MILLISECONDS);

		List<Integer> duringPause = snapshot();
		boolean outsideAfterCleanup = PointerState.isOutside();
		assertEquals("the pause cleanup must release the held button and send no ENTERED with it, saw "
				+ names(duringPause),
			Collections.singletonList(MouseEvent.MOUSE_RELEASED), duringPause);
		assertTrue("the ENTERED was refused, so the pointer must still be recorded as outside after the"
				+ " pause cleanup released the button, but isOutside is false",
			outsideAfterCleanup);

		resume();
		received.clear();
		AwtEmitter.moved(NEXT.getX(), NEXT.getY());
		assertEquals("after the resume the first synthetic move must announce the return with ENTERED,"
				+ " saw " + names(),
			Arrays.asList(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_MOVED), snapshot());
	}

	// Unchanged

	@Test(timeout = 15_000)
	public void aMoveThatWentOutUpdatesTheRecord()
	{
		AwtEmitter.moved(TARGET.getX(), TARGET.getY());

		assertEquals("setup: the move must have gone out, saw " + names(),
			Collections.singletonList(MouseEvent.MOUSE_MOVED), snapshot());
		Record after = new Record();
		assertTrue("a move that went out moves the recorded position and the last bot point to it",
			Record.same(after.position, TARGET) && Record.same(after.lastBot, TARGET));
	}

	@Test(timeout = 15_000)
	public void anEmitWhileTheHumanOwnsInputNeverOverwritesTheHumansPosition()
	{
		// The takeover lands inside the emit, after its checkpoint.
		armNextLookup(() ->
		{
			PointerState.setFromReal(HUMAN_POINT.getX(), HUMAN_POINT.getY());
			InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
			return canvas;
		});

		InputLoop.run(emit -> emit.move(TARGET.getX(), TARGET.getY()));

		Point position = PointerState.get();
		assertTrue("a synthetic event while the human owns input must not overwrite the human's position,"
				+ " but it is " + text(position),
			Record.same(position, HUMAN_POINT));
	}

	private void assertRecordKeptWhenAPauseLandsInside(Kind kind, boolean inGesture)
	{
		Record before = new Record();
		pauseInsideTheNextLookup();

		kind.send(inGesture, TARGET);

		assertRecordKept(before, kind + " " + where(inGesture) + " refused by a pause that landed inside it");
	}

	private void assertRecordKeptDuringAPause(Kind kind) throws Exception
	{
		ScriptPauseController.setPaused(true).get(BOUND_MS, TimeUnit.MILLISECONDS);
		Record before = new Record();

		kind.direct(TARGET);

		assertRecordKept(before, kind + " outside a gesture refused by a pause in force");
	}

	private void assertRecordKeptWithNoCanvas(Kind kind, boolean inGesture)
	{
		Record before = new Record();
		canvasGone = true;

		kind.send(inGesture, TARGET);

		assertRecordKept(before, kind + " " + where(inGesture) + " that found no canvas");
	}

	private void assertRecordKeptAfterATakeover(Kind kind)
	{
		Record[] before = new Record[1];

		InputLoop.run(emit ->
		{
			PointerState.setFromReal(HUMAN_POINT.getX(), HUMAN_POINT.getY());
			InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
			before[0] = new Record();
			kind.emit(emit, TARGET);
		});

		assertRecordKept(before[0], kind + " inside a gesture after the human took over");
	}

	private void assertStillOutsideAfterARefusal(Kind kind, boolean inGesture) throws Exception
	{
		// What the real listener records when the human's pointer leaves the canvas.
		PointerState.setOutside(OFF_CANVAS.getX(), OFF_CANVAS.getY());
		Record before = new Record();
		pauseInsideTheNextLookup();

		kind.send(inGesture, TARGET);

		assertRecordKept(before, kind + " " + where(inGesture) + " refused while the pointer was outside");

		resume();
		AwtEmitter.moved(NEXT.getX(), NEXT.getY());
		assertEquals("the pointer is still outside as far as the game knows, so the next synthetic event that"
				+ " goes out must announce the return with ENTERED first, saw " + names(),
			Arrays.asList(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_MOVED), snapshot());
	}

	private void assertRecordKept(Record before, String what)
	{
		List<Integer> seen = snapshot();
		List<String> moved = before.changesTo(new Record());
		// A press that wrongly went out is released so teardown does not report a second failure.
		if (AwtEmitter.heldMask() != 0)
		{
			AwtEmitter.released(TARGET.getX(), TARGET.getY(), MouseEvent.BUTTON1);
		}
		assertEquals("setup: the " + what + " must not have reached the canvas, saw " + names(seen),
			Collections.emptyList(), seen);
		assertEquals("a " + what + " must leave the pointer record as it was, but " + moved
				+ ", while the canvas saw " + names(seen),
			Collections.emptyList(), moved);
	}

	/** Requests a pause from the calling thread's next canvas lookup, without waiting for its cleanup. */
	private void pauseInsideTheNextLookup()
	{
		armNextLookup(() ->
		{
			ScriptPauseController.setPaused(true);
			return canvas;
		});
	}

	private void armNextLookup(Supplier<Canvas> hook)
	{
		hookOwner = Thread.currentThread();
		nextLookup.set(hook);
	}

	/** Waits for the pause cleanup and the resume, then forgets the events seen so far. */
	private void resume() throws Exception
	{
		ScriptPauseController.setPaused(false).get(BOUND_MS, TimeUnit.MILLISECONDS);
		received.clear();
	}

	private static String where(boolean inGesture)
	{
		return inGesture ? "inside a gesture" : "outside a gesture";
	}

	private static String text(Point point)
	{
		return "(" + point.getX() + "," + point.getY() + ")";
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
