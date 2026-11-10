package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A NaturalMouse trajectory hit by a pause, a human takeover or a thread interrupt ends there:
 * it neither parks forever, spins until the human goes idle, nor finishes the click it was part of.
 *
 * <p>Every disturbance is applied from the canvas listener, which runs on the dispatching thread
 * during dispatch, so it lands at an exact step of the trajectory rather than after a guessed sleep.
 */
public class TrajectoryExitTest
{
	private static final int CANVAS_W = 765;
	private static final int CANVAS_H = 503;
	private static final Point START = new Point(60, 60);
	private static final Point TARGET = new Point(700, 440);
	private static final Point DRAG_START = new Point(200, 200);
	private static final Point DRAG_END = new Point(650, 420);
	/**
	 * Every first movement here spans hundreds of pixels, and on any profile MouseMotion gives a
	 * movement at least its minimum step count (10) unless it is shorter than that many pixels, so
	 * the fourth step always has more after it.
	 */
	private static final int TRIGGER_AT_STEP = 4;
	/**
	 * How long a negative check watches. A trajectory that ignored the disturbance would dispatch
	 * its next step within one step time, about 8 ms on the fastest profile.
	 */
	private static final long OBSERVE_MS = 150L;
	private static final long LOCK_TIMEOUT_MS = 1_000L;

	private static final class Recorded
	{
		final int id;
		final int x;
		final int y;

		Recorded(MouseEvent event)
		{
			id = event.getID();
			x = event.getX();
			y = event.getY();
		}

		@Override
		public String toString()
		{
			return name(id) + "(" + x + "," + y + ")";
		}
	}

	private final List<Recorded> events = new CopyOnWriteArrayList<>();
	private final List<Thread> started = new CopyOnWriteArrayList<>();
	private final AtomicInteger triggerCount = new AtomicInteger();
	private final CountDownLatch triggered = new CountDownLatch(1);
	private final AtomicLong fakeNanos = new AtomicLong(1_000_000_000L);

	private volatile Thread subject;
	private volatile int triggerId;
	private volatile Runnable triggerAction;
	private volatile int mark = -1;
	private volatile CompletableFuture<Void> pauseFuture;
	/** Where a stuck trajectory is heading; cleanup writes it so the trajectory's loop can end. */
	private volatile Point nudgeTarget;
	private volatile java.awt.Point stubPosition;
	private volatile java.awt.Point stubDestination;
	private volatile boolean humanInsideTheTrajectory;
	private volatile boolean markingActivity;

	private Canvas canvas;
	private VirtualMouse mouse;
	private Object previousClient;
	private Object previousMouse;
	private Object previousNaturalMouse;
	private ActivityIntensity previousIntensity;
	private boolean previousDynamicIntensity;

	@Before
	public void before() throws Exception
	{
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);

		canvas = new Canvas();
		canvas.setSize(CANVAS_W, CANVAS_H);
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

			@Override
			public void mouseEntered(MouseEvent e)
			{
				record(e);
			}

			@Override
			public void mouseExited(MouseEvent e)
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

		Client client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenReturn(false);
		when(client.isStretchedEnabled()).thenReturn(false);
		// Without a size, MouseMotion clamps every step to (0,0).
		when(client.getCanvasWidth()).thenReturn(CANVAS_W);
		when(client.getCanvasHeight()).thenReturn(CANVAS_H);

		mouse = new VirtualMouse();
		previousClient = swapStatic("client", client);
		previousMouse = swapStatic("mouse", mouse);
		previousNaturalMouse = swapStatic("naturalMouse", new NaturalMouse());

		previousIntensity = Rs2Antiban.getActivityIntensity();
		previousDynamicIntensity = Rs2AntibanSettings.dynamicIntensity;
		// The fastest profile: disturbances land at an exact step from the listener, so a slower
		// motion would only add runtime.
		Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);

		PointerState.reset();
		PointerState.setFromBot(START.getX(), START.getY());
		InputArbiter.resetForTest();
		AwtEmitter.resetHeldButtonsForTest();
		InputLoop.setLockTimeoutForTest(LOCK_TIMEOUT_MS);
		Microbot.targetMenu = null;
	}

	@After
	public void after() throws Exception
	{
		try
		{
			unstick();
			List<String> alive = new ArrayList<>();
			for (Thread thread : started)
			{
				thread.join(1_000);
				if (thread.isAlive())
				{
					alive.add(thread.getName() + " " + thread.getState());
				}
			}
			assertTrue("threads this test started are still running after cleanup: " + alive, alive.isEmpty());
			assertEquals("InputLoop must be free once the test is over",
				InputLoop.Result.COMPLETED, gestureFromAnotherThread());
		}
		finally
		{
			ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
			swapStatic("client", previousClient);
			swapStatic("mouse", previousMouse);
			swapStatic("naturalMouse", previousNaturalMouse);
			Rs2Antiban.setActivityIntensity(previousIntensity);
			Rs2AntibanSettings.dynamicIntensity = previousDynamicIntensity;
			mouse.shutdown();
			PointerState.reset();
			InputArbiter.resetForTest();
			AwtEmitter.resetHeldButtonsForTest();
			InputLoop.setLockTimeoutForTest(5_000L);
			Microbot.targetMenu = null;
			while (BotEventGuard.isSynthetic())
			{
				BotEventGuard.end();
			}
		}
	}

	// T1: pause mid-trajectory, then resume.

	@Test(timeout = 20_000)
	public void pauseMidTrajectoryEndsTheClickWithoutPressing() throws Exception
	{
		nudgeTarget = TARGET;
		arm(MouseEvent.MOUSE_MOVED, this::pause);
		Thread click = startSubject("click", () -> mouse.click(TARGET, false));

		pauseThenResumeAndAwaitReturn(click, "click");

		assertNoEventsSinceMark("click after the pause took effect");
		assertFalse("a click paused mid-trajectory must never press: " + events, contains(MouseEvent.MOUSE_PRESSED));
		assertEquals("another thread's gesture must get InputLoop once the paused click returned",
			InputLoop.Result.COMPLETED, gestureFromAnotherThread());
	}

	@Test(timeout = 20_000)
	public void pauseMidTrajectoryOfAMenuClickLeavesNoEntryArmed() throws Exception
	{
		nudgeTarget = TARGET;
		arm(MouseEvent.MOUSE_MOVED, this::pause);
		Thread click = startSubject("menu click", () -> mouse.click(TARGET, new NewMenuEntry()));

		pauseThenResumeAndAwaitReturn(click, "menu click");

		assertNoEventsSinceMark("menu click after the pause took effect");
		assertFalse("a click paused mid-trajectory must never press: " + events, contains(MouseEvent.MOUSE_PRESSED));
		assertNull("an entry left armed is consumed by the human's next click", Microbot.targetMenu);
		assertEquals("another thread's gesture must get InputLoop once the paused click returned",
			InputLoop.Result.COMPLETED, gestureFromAnotherThread());
	}

	// T2: takeover mid-trajectory.

	@Test(timeout = 20_000)
	public void takeoverMidTrajectoryEndsTheClickWhileTheHumanStillOwnsInput() throws Exception
	{
		useFakeArbiterClock();
		nudgeTarget = TARGET;
		arm(MouseEvent.MOUSE_MOVED, this::takeOver);
		Thread click = startSubject("click", () -> mouse.click(TARGET, false));

		takeOverAndAwaitReturn(click, "click");
		letTheIdleWindowLapse();

		assertNoEventsSinceMark("click from the takeover on, including after the idle window lapsed");
		assertEquals("another thread's gesture must get InputLoop once the human went idle",
			InputLoop.Result.COMPLETED, gestureFromAnotherThread());
	}

	@Test(timeout = 20_000)
	public void takeoverMidTrajectoryOfAMenuClickLeavesNoEntryArmed() throws Exception
	{
		useFakeArbiterClock();
		nudgeTarget = TARGET;
		arm(MouseEvent.MOUSE_MOVED, this::takeOver);
		Thread click = startSubject("menu click", () -> mouse.click(TARGET, new NewMenuEntry()));

		takeOverAndAwaitReturn(click, "menu click");
		letTheIdleWindowLapse();

		assertNoEventsSinceMark("menu click from the takeover on, including after the idle window lapsed");
		assertNull("an entry left armed is consumed by the human's next click", Microbot.targetMenu);
		assertEquals("another thread's gesture must get InputLoop once the human went idle",
			InputLoop.Result.COMPLETED, gestureFromAnotherThread());
	}

	// T3: interrupt mid-trajectory.

	@Test(timeout = 20_000)
	public void interruptMidTrajectoryEndsTheClickWithoutPressing() throws Exception
	{
		nudgeTarget = TARGET;
		AtomicBoolean flagAfterReturn = new AtomicBoolean();
		arm(MouseEvent.MOUSE_MOVED, () -> Thread.currentThread().interrupt());
		Thread click = startSubject("click", () ->
		{
			mouse.click(TARGET, false);
			flagAfterReturn.set(Thread.currentThread().isInterrupted());
		});

		awaitTrigger();
		click.join(1_000);

		assertFalse("click still running 1 s after its thread was interrupted mid-trajectory", click.isAlive());
		assertEquals("an interrupted click must not press, release or click",
			"[]", buttonEvents(events).toString());
		int moved = count(sinceMark(), MouseEvent.MOUSE_MOVED);
		assertTrue("an interrupted click kept stepping its trajectory: " + moved + " more MOVED after the interrupt",
			moved <= 1);
		assertTrue("the click swallowed its thread's interrupt", flagAfterReturn.get());
		assertEquals("another thread's gesture must get InputLoop once the interrupted click returned",
			InputLoop.Result.COMPLETED, gestureFromAnotherThread());
	}

	// T4: a trajectory with no gesture around it.

	@Test(timeout = 20_000)
	public void takeoverMidDirectMoveEndsTheMove() throws Exception
	{
		useFakeArbiterClock();
		NaturalMouse naturalMouse = Microbot.naturalMouse;
		arm(MouseEvent.MOUSE_MOVED, this::takeOver);
		Thread move = startSubject("moveTo", () -> naturalMouse.moveTo(TARGET.getX(), TARGET.getY()));

		awaitTrigger();
		move.join(1_000);

		assertTrue("the takeover must still be in force", InputArbiter.isHuman());
		assertFalse("a direct moveTo still running 1 s into a takeover", move.isAlive());
		assertNoEventsSinceMark("direct moveTo from the takeover on");
	}

	@Test(timeout = 20_000)
	public void interruptMidDirectMoveEndsTheMove() throws Exception
	{
		NaturalMouse naturalMouse = Microbot.naturalMouse;
		arm(MouseEvent.MOUSE_MOVED, () -> Thread.currentThread().interrupt());
		Thread move = startSubject("moveTo", () -> naturalMouse.moveTo(TARGET.getX(), TARGET.getY()));

		awaitTrigger();
		move.join(1_000);

		assertFalse("a direct moveTo still running 1 s after its thread was interrupted", move.isAlive());
		int moved = count(sinceMark(), MouseEvent.MOUSE_MOVED);
		assertTrue("a direct moveTo kept stepping after its thread was interrupted: " + moved + " more MOVED",
			moved <= 1);
	}

	@Test(timeout = 20_000)
	public void pauseMidDirectMoveDispatchesNothingAndReturnsAfterResume() throws Exception
	{
		nudgeTarget = TARGET;
		NaturalMouse naturalMouse = Microbot.naturalMouse;
		arm(MouseEvent.MOUSE_MOVED, this::pause);
		Thread move = startSubject("moveTo", () -> naturalMouse.moveTo(TARGET.getX(), TARGET.getY()));

		awaitTrigger();
		pauseFuture.get(2, TimeUnit.SECONDS);
		Thread.sleep(OBSERVE_MS);
		List<Recorded> whilePaused = sinceMark();
		resume();
		move.join(2_000);

		assertEquals("a direct moveTo dispatched while paused", "[]", whilePaused.toString());
		assertFalse("a direct moveTo did not return within 2 s of the resume", move.isAlive());
	}

	// T5: steps that never move the pointer.

	@Test(timeout = 20_000)
	public void aTrajectoryThatNeverArrivesStillReturns() throws Exception
	{
		stubPosition = new java.awt.Point(100, 100);
		stubDestination = new java.awt.Point(600, 400);
		Mouse inert = mock(Mouse.class);
		when(inert.getMousePosition()).thenAnswer(invocation -> new java.awt.Point(stubPosition));
		swapStatic("mouse", inert);
		NaturalMouse naturalMouse = Microbot.naturalMouse;

		// The fastest profile plans well under half a second of motion, so 2 s is several plans long.
		Thread move = startSubject("moveTo", () -> naturalMouse.moveTo(600, 400));
		move.join(2_000);

		assertFalse("moveTo still running 2 s on a pointer that never moves; its loop has no exit but arrival",
			move.isAlive());
	}

	// T6: a button held across the trajectory.

	@Test(timeout = 20_000)
	public void pauseDuringADragLegReturnsAndReleasesTheButton() throws Exception
	{
		nudgeTarget = DRAG_END;
		arm(MouseEvent.MOUSE_DRAGGED, this::pause);
		Thread drag = startSubject("drag", () -> mouse.drag(DRAG_START, DRAG_END));

		pauseThenResumeAndAwaitReturn(drag, "drag");

		assertEquals("the bot still holds a button after a paused drag returned", 0, AwtEmitter.heldMask());
		assertTrue("no RELEASED followed the drag's PRESSED: " + events, releasedAfterPressed());
	}

	@Test(timeout = 20_000)
	public void takeoverDuringADragLegReturnsAndReleasesTheButton() throws Exception
	{
		useFakeArbiterClock();
		nudgeTarget = DRAG_END;
		arm(MouseEvent.MOUSE_DRAGGED, this::takeOver);
		Thread drag = startSubject("drag", () -> mouse.drag(DRAG_START, DRAG_END));

		takeOverAndAwaitReturn(drag, "drag");

		assertEquals("the bot still holds a button after a drag ended by a takeover", 0, AwtEmitter.heldMask());
		assertTrue("no RELEASED followed the drag's PRESSED: " + events, releasedAfterPressed());
	}

	// T7: undisturbed behavior.

	@Test(timeout = 20_000)
	public void anUndisturbedClickTravelsThenPressesReleasesAndClicksAtTheTarget() throws Exception
	{
		Thread click = startSubject("click", () -> mouse.click(TARGET, false));
		click.join(10_000);

		assertFalse("an undisturbed click did not finish", click.isAlive());
		assertClickSequence(events, TARGET);
	}

	@Test(timeout = 20_000)
	public void aPauseBetweenTwoClicksDoesNotDisturbTheSecond() throws Exception
	{
		Point first = new Point(400, 120);
		Thread firstClick = startSubject("first click", () -> mouse.click(first, false));
		firstClick.join(10_000);
		assertFalse("the first click did not finish", firstClick.isAlive());
		assertClickSequence(events, first);

		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		events.clear();

		Thread secondClick = startSubject("second click", () -> mouse.click(TARGET, false));
		secondClick.join(10_000);

		assertFalse("the click after a pause and resume did not finish", secondClick.isAlive());
		assertClickSequence(events, TARGET);
	}

	// T8: a trajectory stopped because input was not the bot's ends its gesture there, even when
	// input is the bot's again by the time anything after the trajectory asks.

	@Test(timeout = 20_000)
	public void aClickWhoseTrajectoryStoppedNeitherJumpsToTheTargetNorPresses() throws Exception
	{
		nudgeTarget = TARGET;
		humanOnlyWhileTheTrajectoryRuns();
		arm(MouseEvent.MOUSE_MOVED, () -> humanInsideTheTrajectory = true);
		Thread click = startSubject("click", () -> mouse.click(TARGET, false));

		awaitTrigger();
		click.join(1_000);

		assertFalse("click still running 1 s after its trajectory stopped", click.isAlive());
		assertTheTrajectoryStoppedAtTheTrigger(MouseEvent.MOUSE_MOVED, TARGET);
		assertEquals("a click whose trajectory stopped while input was not the bot's went on once input was"
			+ " the bot's again, jumping to the target and pressing", "[]", sinceMark().toString());
		assertFalse("the pointer was moved onto the target after the trajectory stopped short of it",
			PointerState.isAt(TARGET.getX(), TARGET.getY()));
		assertEquals("another thread's gesture must get InputLoop once the stopped click returned",
			InputLoop.Result.COMPLETED, gestureFromAnotherThread());
	}

	@Test(timeout = 20_000)
	public void aDragWhoseTravelStoppedNeverJumpsToItsEndPoint() throws Exception
	{
		nudgeTarget = DRAG_END;
		humanOnlyWhileTheTrajectoryRuns();
		arm(MouseEvent.MOUSE_DRAGGED, () -> humanInsideTheTrajectory = true);
		Thread drag = startSubject("drag", () -> mouse.drag(DRAG_START, DRAG_END));

		awaitTrigger();
		drag.join(1_000);

		assertFalse("drag still running 1 s after its travel stopped", drag.isAlive());
		assertTheTrajectoryStoppedAtTheTrigger(MouseEvent.MOUSE_DRAGGED, DRAG_END);
		// The release that unwinds the held button is expected; where it lands is the question.
		List<Recorded> atTheEnd = at(sinceMark(), DRAG_END);
		assertEquals("a drag whose travel stopped while input was not the bot's went on once input was the"
			+ " bot's again, dispatching at its end point " + DRAG_END + "; everything after the stop: "
			+ sinceMark(), "[]", atTheEnd.toString());
		assertFalse("the pointer was moved onto the drag's end point after its travel stopped short of it",
			PointerState.isAt(DRAG_END.getX(), DRAG_END.getY()));
		assertEquals("another thread's gesture must get InputLoop once the stopped drag returned",
			InputLoop.Result.COMPLETED, gestureFromAnotherThread());
	}

	@Test(timeout = 20_000)
	public void aMoveOffScreenWhoseTravelStoppedNeverCrossesTheEdge() throws Exception
	{
		NaturalMouse naturalMouse = Microbot.naturalMouse;
		// moveOffScreen reads the injected client, not Microbot.getClient().
		Field injected = NaturalMouse.class.getDeclaredField("client");
		injected.setAccessible(true);
		injected.set(naturalMouse, Microbot.getClient());
		// From the centre every edge is hundreds of pixels away, so the trigger step is mid-travel.
		PointerState.setFromBot(CANVAS_W / 2, CANVAS_H / 2);
		humanOnlyWhileTheTrajectoryRuns();
		arm(MouseEvent.MOUSE_MOVED, () -> humanInsideTheTrajectory = true);
		Thread mover = startSubject("moveOffScreen", naturalMouse::moveOffScreen);

		awaitTrigger();
		mover.join(1_000);

		assertFalse("moveOffScreen still running 1 s after its travel stopped", mover.isAlive());
		assertTheTrajectoryStoppedAtTheTrigger(MouseEvent.MOUSE_MOVED, null);
		assertEquals("a moveOffScreen whose travel stopped while input was not the bot's went on once input"
			+ " was the bot's again and crossed the edge", "[]", sinceMark().toString());
		assertFalse("the pointer was taken off the canvas after the travel stopped", PointerState.isOutside());
		assertEquals("another thread's gesture must get InputLoop once the stopped moveOffScreen returned",
			InputLoop.Result.COMPLETED, gestureFromAnotherThread());
	}

	// Scenario steps.

	private void pauseThenResumeAndAwaitReturn(Thread subjectThread, String what) throws Exception
	{
		awaitTrigger();
		pauseFuture.get(2, TimeUnit.SECONDS);
		Thread.sleep(OBSERVE_MS);
		resume();
		subjectThread.join(2_000);
		assertFalse(what + " paused mid-trajectory did not return within 2 s of the resume",
			subjectThread.isAlive());
	}

	private void takeOverAndAwaitReturn(Thread subjectThread, String what) throws Exception
	{
		awaitTrigger();
		subjectThread.join(1_000);
		// The fake clock is frozen and the button is still down, so this holds whatever the timing.
		assertTrue("the takeover must still be in force", InputArbiter.isHuman());
		assertFalse(what + " still running 1 s into a takeover; it must end while the human owns input,"
			+ " not wait for the idle window", subjectThread.isAlive());
	}

	private void letTheIdleWindowLapse() throws InterruptedException
	{
		InputArbiter.onRealButtonReleased(MouseEvent.BUTTON1);
		fakeNanos.addAndGet((InputArbiter.idleResumeMs() + 1) * 1_000_000L);
		assertFalse("the idle window must have lapsed", InputArbiter.isHuman());
		// Anything still pending would surface now that the bot owns input again.
		Thread.sleep(OBSERVE_MS);
	}

	private void pause()
	{
		pauseFuture = ScriptPauseController.setPaused(true);
	}

	private static void resume() throws Exception
	{
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
	}

	private void takeOver()
	{
		InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
	}

	/** The idle window then lapses only when the test says so, never because a join ran long. */
	private void useFakeArbiterClock()
	{
		InputArbiter.setClockForTest(fakeNanos::get);
	}

	/**
	 * Once armed, the arbiter answers "human" to every question the subject thread asks from inside
	 * MouseMotion, and "not human" to every question asked anywhere else. So the trajectory's own
	 * check is the one that sees the human, and anything after the trajectory sees the bot again,
	 * with no dependence on timing or on how many times either side reads the arbiter.
	 *
	 * <p>The answer goes through the arbiter's clock: the idle window is the only part of
	 * {@link InputArbiter#isHuman()} that is read through code a test supplies.
	 */
	private void humanOnlyWhileTheTrajectoryRuns()
	{
		long activityAt = 1_000_000_000L;
		long longIdle = TimeUnit.SECONDS.toNanos(60);
		InputArbiter.setClockForTest(() ->
			markingActivity || (humanInsideTheTrajectory && Thread.currentThread() == subject && insideMouseMotion())
				? activityAt
				: activityAt + longIdle);
		// A press and release with no button left down: from here on the idle window alone decides.
		markingActivity = true;
		InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
		InputArbiter.onRealButtonReleased(MouseEvent.BUTTON1);
		markingActivity = false;
		assertFalse("setup: input must be the bot's until the trigger step", InputArbiter.isHuman());
	}

	private static boolean insideMouseMotion()
	{
		String motion = MouseMotion.class.getName();
		for (StackTraceElement frame : Thread.currentThread().getStackTrace())
		{
			String name = frame.getClassName();
			if (name.equals(motion) || name.startsWith(motion + "$"))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Without this a test could pass or fail for the wrong reason: if the "human" answers never reach
	 * a check that stops the trajectory, it simply carries on, and what follows is a normal click.
	 * Steps at the destination are left out because a jump there is the defect, not a carried-on
	 * trajectory, which always has intermediate steps left after the trigger.
	 */
	private void assertTheTrajectoryStoppedAtTheTrigger(int stepId, Point destination)
	{
		List<Recorded> steps = new ArrayList<>();
		for (Recorded event : sinceMark())
		{
			boolean atDestination = destination != null && event.x == destination.getX()
				&& event.y == destination.getY();
			if (event.id == stepId && !atDestination)
			{
				steps.add(event);
			}
		}
		assertEquals("setup: the trajectory kept stepping after the arbiter answered human inside it, so no"
			+ " check in the trajectory stopped it and this scenario did not run", "[]", steps.toString());
	}

	private static List<Recorded> at(List<Recorded> recorded, Point point)
	{
		List<Recorded> out = new ArrayList<>();
		for (Recorded event : recorded)
		{
			if (event.x == point.getX() && event.y == point.getY())
			{
				out.add(event);
			}
		}
		return out;
	}

	// Harness.

	private void record(MouseEvent event)
	{
		events.add(new Recorded(event));
		Runnable action = triggerAction;
		if (action != null && event.getID() == triggerId && Thread.currentThread() == subject
			&& triggerCount.incrementAndGet() == TRIGGER_AT_STEP)
		{
			mark = events.size();
			action.run();
			triggered.countDown();
		}
	}

	private void arm(int eventId, Runnable action)
	{
		triggerId = eventId;
		triggerAction = action;
	}

	private void awaitTrigger() throws InterruptedException
	{
		assertTrue("setup: the trajectory never reached step " + TRIGGER_AT_STEP + ", so the disturbance was never applied: "
			+ events, triggered.await(5, TimeUnit.SECONDS));
	}

	private Thread startSubject(String name, Runnable body)
	{
		Thread thread = new Thread(body, "TrajectoryExitTest " + name);
		thread.setDaemon(true);
		started.add(thread);
		subject = thread;
		thread.start();
		return thread;
	}

	private InputLoop.Result gestureFromAnotherThread() throws InterruptedException
	{
		AtomicReference<InputLoop.Result> result = new AtomicReference<>();
		Thread probe = new Thread(() -> result.set(InputLoop.run(InputLoop.Emit::checkpoint)),
			"TrajectoryExitTest probe");
		probe.setDaemon(true);
		started.add(probe);
		probe.start();
		probe.join(LOCK_TIMEOUT_MS + 2_000);
		return result.get();
	}

	/**
	 * Before the fix a disturbed trajectory never ends, in one case while holding InputLoop's lock,
	 * and later test classes share this JVM. Its loop exits once the pointer reads as arrived.
	 */
	private void unstick() throws Exception
	{
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		InputArbiter.resetForTest();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		for (Thread thread : started)
		{
			while (thread.isAlive() && System.nanoTime() < deadline)
			{
				Point target = nudgeTarget;
				if (target != null)
				{
					PointerState.setFromBot(target.getX(), target.getY());
				}
				java.awt.Point destination = stubDestination;
				if (destination != null)
				{
					stubPosition = destination;
				}
				thread.interrupt();
				thread.join(50);
			}
		}
	}

	private void assertNoEventsSinceMark(String what)
	{
		assertEquals(what + " dispatched", "[]", sinceMark().toString());
	}

	private static void assertClickSequence(List<Recorded> recorded, Point target)
	{
		int n = recorded.size();
		assertTrue("expected MOVED steps then PRESSED, RELEASED, CLICKED: " + recorded, n >= 5);
		for (int i = 0; i < n - 3; i++)
		{
			assertEquals("everything before the press is a trajectory step: " + recorded,
				MouseEvent.MOUSE_MOVED, recorded.get(i).id);
		}
		Recorded lastStep = recorded.get(n - 4);
		assertEquals("the trajectory ends at the target: " + recorded, target.getX(), lastStep.x);
		assertEquals("the trajectory ends at the target: " + recorded, target.getY(), lastStep.y);
		int[] tail = {MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED};
		for (int i = 0; i < tail.length; i++)
		{
			Recorded event = recorded.get(n - 3 + i);
			assertEquals("the click ends PRESSED, RELEASED, CLICKED: " + recorded, name(tail[i]), name(event.id));
			assertEquals(name(event.id) + " lands on the target", target.getX(), event.x);
			assertEquals(name(event.id) + " lands on the target", target.getY(), event.y);
		}
	}

	private List<Recorded> sinceMark()
	{
		List<Recorded> snapshot = new ArrayList<>(events);
		return new ArrayList<>(snapshot.subList(Math.min(mark, snapshot.size()), snapshot.size()));
	}

	private boolean contains(int id)
	{
		return count(events, id) > 0;
	}

	private boolean releasedAfterPressed()
	{
		boolean pressed = false;
		for (Recorded event : events)
		{
			if (event.id == MouseEvent.MOUSE_PRESSED)
			{
				pressed = true;
			}
			else if (pressed && event.id == MouseEvent.MOUSE_RELEASED)
			{
				return true;
			}
		}
		return false;
	}

	private static int count(List<Recorded> recorded, int id)
	{
		int count = 0;
		for (Recorded event : recorded)
		{
			if (event.id == id)
			{
				count++;
			}
		}
		return count;
	}

	private static List<Recorded> buttonEvents(List<Recorded> recorded)
	{
		List<Recorded> out = new ArrayList<>();
		for (Recorded event : recorded)
		{
			if (event.id == MouseEvent.MOUSE_PRESSED || event.id == MouseEvent.MOUSE_RELEASED
				|| event.id == MouseEvent.MOUSE_CLICKED)
			{
				out.add(event);
			}
		}
		return out;
	}

	private static String name(int id)
	{
		switch (id)
		{
			case MouseEvent.MOUSE_MOVED:
				return "MOVED";
			case MouseEvent.MOUSE_DRAGGED:
				return "DRAGGED";
			case MouseEvent.MOUSE_PRESSED:
				return "PRESSED";
			case MouseEvent.MOUSE_RELEASED:
				return "RELEASED";
			case MouseEvent.MOUSE_CLICKED:
				return "CLICKED";
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
