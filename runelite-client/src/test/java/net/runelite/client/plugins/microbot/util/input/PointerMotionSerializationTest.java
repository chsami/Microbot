package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * There is one pointer, so a NaturalMouse trajectory started outside any gesture (a hover, an
 * antiban off-screen or random move) must take turns with clicks exactly as gestures do, below the
 * call sites, since plugins outside this repository call NaturalMouse directly.
 *
 * <p>Every event is recorded with the thread that dispatched it. The listener runs on that thread
 * during dispatch, so a test can hold one side at an exact point and give the other side a window
 * it can only use when nothing serializes the two.
 */
public class PointerMotionSerializationTest
{
	private static final int CANVAS_W = 765;
	private static final int CANVAS_H = 503;

	/**
	 * How long one side is held at a dispatch so the other side can show whether it gets in. Today
	 * the other side gets in within milliseconds; once serialized every hold runs to its end.
	 */
	private static final long OVERLAP_WAIT_MS = 200L;
	private static final long JOIN_MS = 15_000L;
	/**
	 * The least a slow-profile motion (see useSlowProfileWithoutOvershoots) can take when it waits
	 * between its steps. Its one movement is planned at 400ms or more; MouseMotion splits it into
	 * ceil(time / 8) steps and floors each step's share to whole milliseconds, which loses at most an
	 * eighth: 7 / 8 of 400ms.
	 */
	private static final long PLANNED_FLOOR_MS = 350L;
	/**
	 * The same motion with its waits skipped took 6 to 8ms here, and 11ms at most with every core
	 * saturated (measured 2026-11-09 against a one-line fix). This leaves over 18 times that for a
	 * loaded machine and still sits 150ms under PLANNED_FLOOR_MS, which no machine can undercut.
	 */
	private static final long NO_WAIT_CEILING_MS = 200L;

	private static final Point START = new Point(40, 40);
	private static final Point CLICK_TARGET = new Point(620, 90);
	private static final Point FAR_DEST = new Point(720, 460);
	private static final Point OTHER_DEST = new Point(100, 460);

	private Client client;
	private Canvas canvas;
	private NaturalMouse naturalMouse;
	private volatile Thread designatedClientThread;

	private final List<Dispatched> events = new ArrayList<>();
	private volatile Consumer<Dispatched> hook = d -> { };

	private final List<Thread> started = new CopyOnWriteArrayList<>();
	private final List<CountDownLatch> holdReleases = new CopyOnWriteArrayList<>();
	private final List<Throwable> threadFailures = new CopyOnWriteArrayList<>();

	private Object previousClient;
	private Object previousNaturalMouse;
	private ActivityIntensity previousIntensity;
	private boolean previousDynamicIntensity;
	private boolean previousSimulateMistakes;

	private static final class Dispatched
	{
		final int id;
		final int x;
		final int y;
		final Thread thread;

		Dispatched(MouseEvent event)
		{
			this.id = event.getID();
			this.x = event.getX();
			this.y = event.getY();
			this.thread = Thread.currentThread();
		}

		boolean isMoveTo(Point point)
		{
			return id == MouseEvent.MOUSE_MOVED && x == point.getX() && y == point.getY();
		}

		@Override
		public String toString()
		{
			return name(id) + "(" + x + "," + y + ")";
		}
	}

	@Before
	public void before() throws Exception
	{
		canvas = new Canvas();
		MouseAdapter recorder = new MouseAdapter()
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
		};
		canvas.addMouseListener(recorder);
		canvas.addMouseMotionListener(recorder);

		client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenAnswer(invocation -> Thread.currentThread() == designatedClientThread);
		when(client.isStretchedEnabled()).thenReturn(false);
		when(client.getCanvasWidth()).thenReturn(CANVAS_W);
		when(client.getCanvasHeight()).thenReturn(CANVAS_H);

		naturalMouse = new NaturalMouse();
		// moveOffScreen and moveRandom read the injected client, not Microbot.getClient().
		Field injected = NaturalMouse.class.getDeclaredField("client");
		injected.setAccessible(true);
		injected.set(naturalMouse, client);

		previousClient = swapStatic("client", client);
		previousNaturalMouse = swapStatic("naturalMouse", naturalMouse);
		previousIntensity = Rs2Antiban.getActivityIntensity();
		previousDynamicIntensity = Rs2AntibanSettings.dynamicIntensity;
		previousSimulateMistakes = Rs2AntibanSettings.simulateMistakes;

		PointerState.reset();
		InputArbiter.resetForTest();
		AwtEmitter.resetHeldButtonsForTest();
		InputLoop.setLockTimeoutForTest(5_000L);
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		Microbot.targetMenu = null;
		// Pinned, not inherited from whatever an earlier test class left. The windows come from the
		// dispatch holds, not from how long a trajectory lasts, so the fastest profile is enough.
		Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);
	}

	@After
	public void after() throws Exception
	{
		try
		{
			hook = d -> { };
			designatedClientThread = null;
			// A deferral's worker is not one of ours to join, so a hold left closed would park it for good.
			for (CountDownLatch release : holdReleases)
			{
				release.countDown();
			}
			// Whatever a failed test left behind, end the states that can park a trajectory.
			ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
			InputArbiter.resetForTest();

			for (Thread thread : started)
			{
				thread.join(JOIN_MS);
			}
			for (Thread thread : started)
			{
				assertFalse("helper thread " + thread.getName() + " is still running after the test", thread.isAlive());
			}

			InputLoop.setLockTimeoutForTest(1_000L);
			assertEquals("InputLoop must be free once the test is over", InputLoop.Result.COMPLETED,
				InputLoop.run(emit -> { }));
			assertTrue("helper threads threw: " + threadFailures, threadFailures.isEmpty());
		}
		finally
		{
			swapStatic("client", previousClient);
			swapStatic("naturalMouse", previousNaturalMouse);
			Rs2Antiban.setActivityIntensity(previousIntensity);
			Rs2AntibanSettings.dynamicIntensity = previousDynamicIntensity;
			Rs2AntibanSettings.simulateMistakes = previousSimulateMistakes;
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
	}

	@Test(timeout = 30_000)
	public void aQuickClickStartedDuringABareMoveToWaitsForTheTrajectory() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());

		Thread gesture = bareFirstThenGesture(
			() -> naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY()),
			() -> new VirtualMouse().quickClick(CLICK_TARGET, new NewMenuEntry()));

		assertSerialized("bare NaturalMouse.moveTo, then VirtualMouse.quickClick", gesture, "moveTo", "quickClick");
		assertClickHappened(gesture);
	}

	@Test(timeout = 30_000)
	public void aBareMoveToStartedDuringAQuickClickWaitsForTheClick() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());

		Thread bare = daemon("bare-trajectory", () -> naturalMouse.moveTo(OTHER_DEST.getX(), OTHER_DEST.getY()));
		Thread gesture = daemon("click-gesture", () -> new VirtualMouse().quickClick(CLICK_TARGET, new NewMenuEntry()));
		AtomicBoolean triggered = new AtomicBoolean();
		hook = d -> {
			if (d.thread == gesture && d.id == MouseEvent.MOUSE_MOVED && triggered.compareAndSet(false, true))
			{
				bare.start();
				// The click is between its MOVED and its PRESSED here.
				awaitEvent(e -> e.thread == bare, OVERLAP_WAIT_MS);
			}
		};
		gesture.start();
		joinOrFail(gesture, "quickClick");
		assertTrue("the quickClick never dispatched its MOVED, so the scenario did not run", triggered.get());
		joinOrFail(bare, "bare moveTo");

		assertSerialized("VirtualMouse.quickClick, then bare NaturalMouse.moveTo", gesture, "moveTo", "quickClick");
		assertClickHappened(gesture);
		assertTrajectoryReached(bare, OTHER_DEST, "the bare moveTo must still run once the click is done");
	}

	@Test(timeout = 30_000)
	public void aClickStartedDuringABareMoveToWaitsForTheTrajectory() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());

		Thread gesture = bareFirstThenGesture(
			() -> naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY()),
			() -> new VirtualMouse().click(CLICK_TARGET, false));

		assertSerialized("bare NaturalMouse.moveTo, then VirtualMouse.click", gesture, "moveTo", "click");
		assertClickHappened(gesture);
	}

	@Test(timeout = 30_000)
	public void aBareMoveToStartedDuringAClickWaitsForTheWholeClick() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());

		AtomicReference<Thread> bareRef = new AtomicReference<>();
		// The click reads the entry's actor after its own trajectory and before its press, which is
		// the window a bare trajectory would use.
		NewMenuEntry entry = new SlowActorLookupEntry(bareRef);
		Thread bare = daemon("bare-trajectory", () -> naturalMouse.moveTo(OTHER_DEST.getX(), OTHER_DEST.getY()));
		bareRef.set(bare);
		Thread gesture = daemon("click-gesture", () -> new VirtualMouse().click(CLICK_TARGET, false, entry));
		AtomicBoolean triggered = new AtomicBoolean();
		hook = d -> {
			if (d.thread == gesture && d.id == MouseEvent.MOUSE_MOVED && triggered.compareAndSet(false, true))
			{
				bare.start();
			}
		};
		gesture.start();
		joinOrFail(gesture, "click");
		assertTrue("the click never moved the pointer, so the scenario did not run", triggered.get());
		joinOrFail(bare, "bare moveTo");

		assertSerialized("VirtualMouse.click, then bare NaturalMouse.moveTo", gesture, "moveTo", "click");
		assertClickHappened(gesture);
		assertTrajectoryReached(bare, OTHER_DEST, "the bare moveTo must still run once the click is done");
	}

	@Test(timeout = 30_000)
	public void aQuickClickStartedDuringMoveOffScreenWaitsForIt() throws Exception
	{
		PointerState.setFromBot(CANVAS_W / 2, CANVAS_H / 2);

		Thread gesture = bareFirstThenGesture(
			() -> naturalMouse.moveOffScreen(),
			() -> new VirtualMouse().quickClick(CLICK_TARGET, new NewMenuEntry()));

		assertSerialized("NaturalMouse.moveOffScreen, then VirtualMouse.quickClick", gesture, "moveOffScreen", "quickClick");
		assertClickHappened(gesture);
	}

	@Test(timeout = 30_000)
	public void aQuickClickStartedDuringMoveRandomWaitsForIt() throws Exception
	{
		// A corner start keeps the random destination far away, so the trajectory has steps left
		// after the click starts.
		PointerState.setFromBot(0, 0);

		Thread gesture = bareFirstThenGesture(
			() -> naturalMouse.moveRandom(),
			() -> new VirtualMouse().quickClick(CLICK_TARGET, new NewMenuEntry()));

		assertSerialized("NaturalMouse.moveRandom, then VirtualMouse.quickClick", gesture, "moveRandom", "quickClick");
		assertClickHappened(gesture);
	}

	@Test(timeout = 30_000)
	public void moveToInsideAGestureStillMovesAndTheGestureCarriesOn() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());
		AtomicReference<InputLoop.Result> result = new AtomicReference<>();

		Thread gesture = daemon("nesting-gesture", () -> result.set(InputLoop.run(emit -> {
			naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY());
			emit.press(FAR_DEST.getX(), FAR_DEST.getY(), MouseEvent.BUTTON1);
			emit.release(FAR_DEST.getX(), FAR_DEST.getY(), MouseEvent.BUTTON1);
			emit.click(FAR_DEST.getX(), FAR_DEST.getY(), MouseEvent.BUTTON1);
		})));
		gesture.start();
		gesture.join(JOIN_MS);

		assertFalse("a gesture calling NaturalMouse.moveTo deadlocked", gesture.isAlive());
		assertTrue("NaturalMouse.moveTo inside a gesture threw: " + threadFailures, threadFailures.isEmpty());
		assertEquals("the gesture around the nested moveTo must complete", InputLoop.Result.COMPLETED, result.get());

		List<Dispatched> all = snapshot();
		int press = indexOf(all, e -> e.id == MouseEvent.MOUSE_PRESSED);
		assertTrue("the gesture's PRESSED after the nested moveTo was never dispatched: " + all, press >= 0);
		List<Dispatched> beforePress = all.subList(0, press);
		assertTrue("the nested moveTo dispatched no MOVED before the gesture went on: " + all,
			beforePress.stream().anyMatch(e -> e.id == MouseEvent.MOUSE_MOVED));
		assertTrue("the nested moveTo had not reached its destination when the gesture went on: " + beforePress,
			beforePress.get(beforePress.size() - 1).isMoveTo(FAR_DEST));
		assertEquals("the gesture's own emits after the nested moveTo",
			names(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED),
			namesOf(all.subList(press, all.size())));
	}

	@Test(timeout = 30_000)
	public void aBareTrajectoryThatCannotGetThePointerGivesUpWithoutDispatching() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());
		InputLoop.setLockTimeoutForTest(200L);
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		Thread hog = daemon("gesture-holding-the-pointer", () -> InputLoop.run(emit -> {
			holding.countDown();
			awaitQuietly(release, JOIN_MS);
		}));
		Thread bare = daemon("bare-trajectory", () -> naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY()));
		try
		{
			hog.start();
			assertTrue("the holding gesture never started", holding.await(2, TimeUnit.SECONDS));

			bare.start();
			bare.join(5_000L);
			boolean returned = !bare.isAlive();
			List<Dispatched> fromBare = from(bare);

			assertTrue("a bare moveTo dispatched " + fromBare.size() + " events while another gesture held the"
				+ " pointer past InputLoop's lock timeout: " + fromBare, fromBare.isEmpty());
			assertTrue("a bare moveTo must give up after InputLoop's lock timeout, not wait for the pointer", returned);
		}
		finally
		{
			release.countDown();
		}
	}

	@Test(timeout = 30_000)
	public void aGestureHoldingThePointerAndABareTrajectoryNeverStallEachOther() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());
		// Long enough that a stall cannot pass for a slow machine.
		InputLoop.setLockTimeoutForTest(3_000L);
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch go = new CountDownLatch(1);
		AtomicReference<InputLoop.Result> result = new AtomicReference<>();

		// The same lock order a click has: InputLoop's lock first, then the nested moveTo. Parked
		// between the two, so the bare call arrives while the gesture owns the pointer.
		Thread gesture = daemon("gesture-then-nested-moveTo", () -> result.set(InputLoop.run(emit -> {
			holding.countDown();
			awaitQuietly(go, JOIN_MS);
			naturalMouse.moveTo(CLICK_TARGET.getX(), CLICK_TARGET.getY());
		})));
		Thread bare = daemon("bare-trajectory", () -> naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY()));
		long releasedAt;
		try
		{
			gesture.start();
			assertTrue("the gesture never took the pointer", holding.await(2, TimeUnit.SECONDS));
			bare.start();
			awaitParkedOrDone(bare, 1_000L);
		}
		finally
		{
			releasedAt = System.nanoTime();
			go.countDown();
		}

		gesture.join(1_500L);
		long gestureMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - releasedAt);
		assertFalse("the gesture's nested moveTo stalled behind the bare moveTo for " + gestureMs
			+ "ms: the two take InputLoop's lock and NaturalMouse in opposite orders", gesture.isAlive());
		assertEquals("the gesture around the nested moveTo must complete", InputLoop.Result.COMPLETED, result.get());
		joinOrFail(bare, "bare moveTo");
		assertTrajectoryReached(bare, FAR_DEST, "the bare moveTo must still run once the gesture is done");
	}

	@Test(timeout = 30_000)
	public void aClientThreadRequestReturnsAtOnceAndItsMotionStillTakesTurns() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());

		CountDownLatch clientReturned = new CountDownLatch(1);
		Thread clientThread = daemon("client-thread", () -> {
			naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY());
			clientReturned.countDown();
		});
		designatedClientThread = clientThread;

		AtomicReference<Thread> gestureRef = new AtomicReference<>();
		AtomicReference<String> clientThreadBlocked = new AtomicReference<>();
		CountDownLatch gestureStarted = new CountDownLatch(1);
		AtomicBoolean triggered = new AtomicBoolean();
		hook = d -> {
			if (d.id == MouseEvent.MOUSE_MOVED && triggered.compareAndSet(false, true))
			{
				// Parked at its first MOVED, the trajectory cannot finish, so a call that waits for
				// it cannot return here.
				if (Thread.currentThread() == clientThread)
				{
					clientThreadBlocked.set("the trajectory ran on the client thread itself");
				}
				else if (!awaitQuietly(clientReturned, 2_000L))
				{
					clientThreadBlocked.set("the client-thread call had not returned 2000ms into its trajectory");
				}
				Thread gesture = daemon("click-gesture", () -> new VirtualMouse().quickClick(CLICK_TARGET, new NewMenuEntry()));
				gestureRef.set(gesture);
				gestureStarted.countDown();
				gesture.start();
				awaitEvent(e -> e.thread == gesture, OVERLAP_WAIT_MS);
			}
		};

		clientThread.start();

		assertTrue("the trajectory requested from the client thread never dispatched a MOVED",
			gestureStarted.await(5, TimeUnit.SECONDS));
		Thread gesture = gestureRef.get();
		assertNotNull(gesture);
		assertNull("moveTo on the client thread blocked it: " + clientThreadBlocked.get(), clientThreadBlocked.get());
		joinOrFail(clientThread, "moveTo on the client thread");
		joinOrFail(gesture, "quickClick");
		assertTrue("the trajectory requested from the client thread never reached its destination",
			awaitEvent(e -> e.thread != gesture && e.isMoveTo(FAR_DEST), JOIN_MS));
		awaitQuiet(150L, JOIN_MS);

		assertSerialized("moveTo requested from the client thread, then VirtualMouse.quickClick", gesture,
			"client-thread moveTo", "quickClick");
		assertClickHappened(gesture);
	}

	@Test(timeout = 30_000)
	public void aBareTrajectoryDispatchesNothingWhileTheHumanOwnsInput() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());
		InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
		assertTrue(InputArbiter.isHuman());

		Thread bare = daemon("bare-trajectory", () -> naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY()));
		List<Dispatched> duringTakeover;
		try
		{
			bare.start();
			// Absence can only be observed over a window; the trajectory's first step is immediate.
			bare.join(300L);
			duringTakeover = snapshot();
		}
		finally
		{
			// Today the trajectory spins until the pointer is at its destination, so end it there.
			PointerState.setFromBot(FAR_DEST.getX(), FAR_DEST.getY());
			InputArbiter.resetForTest();
		}
		assertTrue("a bare trajectory dispatched while the human owned input: " + duringTakeover,
			duringTakeover.isEmpty());
	}

	@Test(timeout = 30_000)
	public void aBareTrajectoryDispatchesNothingWhileScriptsArePaused() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());
		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);

		Thread bare = daemon("bare-trajectory", () -> naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY()));
		List<Dispatched> duringPause;
		try
		{
			bare.start();
			bare.join(300L);
			duringPause = snapshot();
		}
		finally
		{
			PointerState.setFromBot(FAR_DEST.getX(), FAR_DEST.getY());
			ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		}
		assertTrue("a bare trajectory dispatched while scripts were paused: " + duringPause, duringPause.isEmpty());
	}

	@Test(timeout = 30_000)
	public void twoBareTrajectoriesOnTwoThreadsTakeTurns() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());

		Thread second = bareFirstThenGesture(
			() -> naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY()),
			() -> naturalMouse.moveTo(OTHER_DEST.getX(), OTHER_DEST.getY()));

		assertSerialized("two bare NaturalMouse.moveTo calls", second, "first moveTo", "second moveTo");
		assertTrajectoryReached(second, OTHER_DEST, "the second moveTo must run once the first is done");
	}

	@Test(timeout = 30_000)
	public void anUndisturbedBareMoveToEndsOnTheDestination() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());

		Thread bare = daemon("bare-trajectory", () -> naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY()));
		bare.start();
		joinOrFail(bare, "bare moveTo");

		assertTrajectoryReached(bare, FAR_DEST, "an undisturbed bare moveTo");
		assertTrue("the pointer must rest on the destination", PointerState.isAt(FAR_DEST.getX(), FAR_DEST.getY()));
	}

	@Test(timeout = 30_000)
	public void aMotionBuiltFromTheFactoryStillMovesWithoutAGesture() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());

		Thread mover = daemon("factory-motion", () -> {
			try
			{
				naturalMouse.getFactory().build(FAR_DEST.getX(), FAR_DEST.getY()).move();
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		});
		mover.start();
		joinOrFail(mover, "factory motion");

		assertTrue("the factory motion threw: " + threadFailures, threadFailures.isEmpty());
		assertTrajectoryReached(mover, FAR_DEST, "a motion built from NaturalMouse.getFactory()");
		assertTrue("the pointer must rest on the destination", PointerState.isAt(FAR_DEST.getX(), FAR_DEST.getY()));
	}

	@Test(timeout = 30_000)
	public void aFactoryMotionOnTheClientThreadNeverWaitsBetweenItsSteps() throws Exception
	{
		useSlowProfileWithoutOvershoots();
		designatedClientThread = Thread.currentThread();
		// Class loading and first-use cost stay out of the measured span.
		PointerState.setFromBot(START.getX(), START.getY());
		naturalMouse.getFactory().build(START.getX() + 60, START.getY() + 40).move();

		PointerState.setFromBot(START.getX(), START.getY());
		MouseMotion motion = naturalMouse.getFactory().build(FAR_DEST.getX(), FAR_DEST.getY());
		int before = snapshot().size();
		long t0 = System.nanoTime();
		motion.move();
		long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

		assertTrue("a factory motion on the client thread took " + tookMs + "ms; one that waits between its steps"
				+ " takes at least " + PLANNED_FLOOR_MS + "ms, and one that does not stays under " + NO_WAIT_CEILING_MS + "ms",
			tookMs < NO_WAIT_CEILING_MS);
		List<Dispatched> moved = since(before);
		assertTrue("a factory motion on the client thread must still dispatch its steps", moved.size() > 1);
		assertTrue("a factory motion on the client thread must still end on its destination: " + describe(moved),
			moved.get(moved.size() - 1).isMoveTo(FAR_DEST) && PointerState.isAt(FAR_DEST.getX(), FAR_DEST.getY()));
	}

	@Test(timeout = 30_000)
	public void aFactoryMotionOffTheClientThreadStillTakesItsPlannedTime() throws Exception
	{
		useSlowProfileWithoutOvershoots();
		PointerState.setFromBot(START.getX(), START.getY());
		MouseMotion motion = naturalMouse.getFactory().build(FAR_DEST.getX(), FAR_DEST.getY());
		int before = snapshot().size();
		long t0 = System.nanoTime();
		motion.move();
		long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

		assertTrue("a factory motion off the client thread took " + tookMs + "ms, under the " + PLANNED_FLOOR_MS
			+ "ms its plan allows, so it skipped the waits between its steps", tookMs >= PLANNED_FLOOR_MS);
		List<Dispatched> moved = since(before);
		assertTrue("a factory motion off the client thread must end on its destination: " + describe(moved),
			!moved.isEmpty() && moved.get(moved.size() - 1).isMoveTo(FAR_DEST));
	}

	/**
	 * VERY_LOW builds FactoryTemplates.createAverageComputerUserMotionFactory: a 400ms base time, to
	 * which DefaultSpeedManager adds 0 to 100 percent and more for idle flow buckets. Without
	 * mistakes there are no overshoots, so a motion is one movement planned at 400ms or more.
	 */
	private static void useSlowProfileWithoutOvershoots()
	{
		Rs2Antiban.setActivityIntensity(ActivityIntensity.VERY_LOW);
		Rs2AntibanSettings.simulateMistakes = false;
	}

	@Test(timeout = 30_000)
	public void aDeferredMoveToQueuedAcrossAPauseAndResumeDispatchesNothing() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());

		List<Dispatched> late = requestBeforeAPause(
			() -> naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY()),
			() -> naturalMouse.moveTo(OTHER_DEST.getX(), OTHER_DEST.getY()),
			true);

		assertTrue("moveTo requested on the client thread before a pause dispatched after the resume: "
			+ describe(late), late.isEmpty());
	}

	@Test(timeout = 30_000)
	public void aDeferredMoveOffScreenQueuedAcrossAPauseAndResumeDispatchesNothing() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());

		List<Dispatched> late = requestBeforeAPause(
			() -> naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY()),
			() -> naturalMouse.moveOffScreen(),
			true);

		assertTrue("moveOffScreen requested on the client thread before a pause dispatched after the resume: "
			+ describe(late), late.isEmpty());
	}

	@Test(timeout = 30_000)
	public void aDeferredMoveRandomQueuedAcrossAPauseAndResumeDispatchesNothing() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());

		List<Dispatched> late = requestBeforeAPause(
			() -> naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY()),
			() -> naturalMouse.moveRandom(),
			true);

		assertTrue("moveRandom requested on the client thread before a pause dispatched after the resume: "
			+ describe(late), late.isEmpty());
	}

	@Test(timeout = 30_000)
	public void aDeferredMoveToWhoseTurnComesDuringThePauseDispatchesNothing() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());

		List<Dispatched> late = requestBeforeAPause(
			() -> naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY()),
			() -> naturalMouse.moveTo(OTHER_DEST.getX(), OTHER_DEST.getY()),
			false);

		assertTrue("moveTo requested on the client thread before a pause dispatched during or after it: "
			+ describe(late), late.isEmpty());
	}

	@Test(timeout = 30_000)
	public void aDeferredClickQueuedAcrossAPauseAndResumeDispatchesNothing() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());
		VirtualMouse mouse = new VirtualMouse();

		List<Dispatched> late = requestBeforeAPause(
			() -> mouse.click(CLICK_TARGET, false),
			() -> mouse.click(OTHER_DEST, false),
			true);

		assertTrue("a click requested on the client thread before a pause dispatched after the resume: "
			+ describe(late), late.isEmpty());
	}

	@Test(timeout = 30_000)
	public void aDeferredDragQueuedAcrossAPauseAndResumeDispatchesNothing() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());
		VirtualMouse mouse = new VirtualMouse();

		List<Dispatched> late = requestBeforeAPause(
			() -> mouse.click(CLICK_TARGET, false),
			() -> mouse.drag(new Point(300, 300), new Point(420, 300)),
			true);

		assertTrue("a drag requested on the client thread before a pause dispatched after the resume: "
			+ describe(late), late.isEmpty());
	}

	@Test(timeout = 30_000)
	public void aDeferredScrollQueuedAcrossAPauseAndResumeDispatchesNothing() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());
		VirtualMouse mouse = new VirtualMouse();

		List<Dispatched> late = requestBeforeAPause(
			() -> mouse.click(CLICK_TARGET, false),
			() -> mouse.scrollDown(new Point(300, 200)),
			true);

		assertTrue("a scroll requested on the client thread before a pause dispatched after the resume: "
			+ describe(late), late.isEmpty());
	}

	@Test(timeout = 30_000)
	public void aDeferredClickWhoseTurnComesDuringThePauseDispatchesNothing() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());
		VirtualMouse mouse = new VirtualMouse();

		List<Dispatched> late = requestBeforeAPause(
			() -> mouse.click(CLICK_TARGET, false),
			() -> mouse.click(OTHER_DEST, false),
			false);

		assertTrue("a click requested on the client thread before a pause dispatched during or after it: "
			+ describe(late), late.isEmpty());
	}

	@Test(timeout = 30_000)
	public void requestsWaitingForThePointerAcrossAPauseAndResumeDispatchNothing() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());
		Hold hold = holdNextMove();

		Thread older = daemon("older-bare-moveTo", () -> naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY()));
		older.start();
		assertTrue("the older moveTo never dispatched, so nothing could wait behind it",
			hold.entered.await(5, TimeUnit.SECONDS));

		// The older moveTo is parked mid-turn in the listener, so neither of these can dispatch yet.
		Thread bare = daemon("waiting-bare-moveTo", () -> naturalMouse.moveTo(OTHER_DEST.getX(), OTHER_DEST.getY()));
		Thread click = daemon("waiting-click", () -> new VirtualMouse().click(new Point(300, 200), false));
		bare.start();
		click.start();
		awaitParkedOrDone(bare, 1_000L);
		awaitParkedOrDone(click, 1_000L);
		int madeAt = snapshot().size();

		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		hold.release.countDown();
		joinOrFail(older, "the older moveTo");
		joinOrFail(bare, "the waiting moveTo");
		joinOrFail(click, "the waiting click");

		List<Dispatched> late = since(madeAt);
		assertTrue("requests made before a pause, waiting for the pointer, dispatched after the resume: "
			+ describe(late), late.isEmpty());
	}

	@Test(timeout = 30_000)
	public void aMoveToRequestedAfterTheResumeRunsWhileAnOlderOneUnwinds() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());
		designatedClientThread = Thread.currentThread();
		Hold hold = holdNextMove();

		naturalMouse.moveTo(FAR_DEST.getX(), FAR_DEST.getY());
		assertTrue("the older client-thread moveTo never dispatched", hold.entered.await(5, TimeUnit.SECONDS));
		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);

		naturalMouse.moveTo(OTHER_DEST.getX(), OTHER_DEST.getY());
		designatedClientThread = null;
		hold.release.countDown();

		assertTrue("moveTo requested on the client thread after the resume never reached its destination: "
				+ describe(snapshot()),
			awaitEvent(e -> e.isMoveTo(OTHER_DEST), 5_000L));
	}

	@Test(timeout = 30_000)
	public void aClickRequestedAfterTheResumeRunsWhileAnOlderOneUnwinds() throws Exception
	{
		PointerState.setFromBot(START.getX(), START.getY());
		VirtualMouse mouse = new VirtualMouse();
		designatedClientThread = Thread.currentThread();
		Hold hold = holdNextMove();

		mouse.click(CLICK_TARGET, false);
		assertTrue("the older client-thread click never dispatched", hold.entered.await(5, TimeUnit.SECONDS));
		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);

		mouse.quickClick(OTHER_DEST, new NewMenuEntry());
		designatedClientThread = null;
		hold.release.countDown();

		assertTrue("a click requested on the client thread after the resume never clicked: " + describe(snapshot()),
			awaitEvent(e -> e.id == MouseEvent.MOUSE_CLICKED && e.x == OTHER_DEST.getX() && e.y == OTHER_DEST.getY(),
				5_000L));
	}

	/** One-shot: the first MOVED dispatched after arming is held in the listener until released. */
	private static final class Hold
	{
		final CountDownLatch entered = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		volatile Thread thread;
	}

	/**
	 * Arms a {@link Hold}. Whatever dispatches that MOVED is mid-turn on the pointer while it is held,
	 * so no request made after it can dispatch until it opens (S1, S6), on whatever thread it waits.
	 */
	private Hold holdNextMove()
	{
		Hold hold = new Hold();
		holdReleases.add(hold.release);
		AtomicBoolean armed = new AtomicBoolean(true);
		hook = d -> {
			if (d.id == MouseEvent.MOUSE_MOVED && armed.compareAndSet(true, false))
			{
				hold.thread = Thread.currentThread();
				hold.entered.countDown();
				awaitQuietly(hold.release, JOIN_MS);
			}
		};
		return hold;
	}

	/**
	 * Makes {@code older} on the client thread and holds it at its first MOVED, makes
	 * {@code underTest} on the client thread behind it, then pauses (and resumes, if asked) before
	 * the hold opens, so the request under test cannot have started before the pause. Returns what
	 * was dispatched from the moment the request under test was made.
	 */
	private List<Dispatched> requestBeforeAPause(Runnable older, Runnable underTest, boolean resumeBeforeItsTurn)
		throws Exception
	{
		designatedClientThread = Thread.currentThread();
		Hold hold = holdNextMove();
		older.run();
		assertTrue("the older client-thread request never dispatched, so nothing was queued behind it",
			hold.entered.await(5, TimeUnit.SECONDS));
		underTest.run();
		designatedClientThread = null;
		int madeAt = snapshot().size();

		ScriptPauseController.setPaused(true).get(2, TimeUnit.SECONDS);
		if (resumeBeforeItsTurn)
		{
			ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		}
		hold.release.countDown();
		awaitIdle(hold.thread, 5_000L);
		if (!resumeBeforeItsTurn)
		{
			ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		}
		awaitQuiet(100L, JOIN_MS);
		return since(madeAt);
	}

	/**
	 * Waits until a deferral's worker has run out of queued work: parked untimed on a
	 * java.util.concurrent blocker, the way an idle executor waits for its next task, or gone.
	 * Running out of time is not a failure; the quiet wait after it still bounds the observation.
	 */
	private static void awaitIdle(Thread worker, long timeoutMs) throws InterruptedException
	{
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
		while (System.nanoTime() < deadline)
		{
			if (!worker.isAlive()
				|| (worker.getState() == Thread.State.WAITING && LockSupport.getBlocker(worker) != null))
			{
				return;
			}
			Thread.sleep(1L);
		}
	}

	private List<Dispatched> since(int index)
	{
		List<Dispatched> all = snapshot();
		return new ArrayList<>(all.subList(Math.min(index, all.size()), all.size()));
	}

	private static String describe(List<Dispatched> list)
	{
		List<String> out = new ArrayList<>();
		for (Dispatched event : list)
		{
			out.add(event + " on " + event.thread.getName());
		}
		if (out.size() > 8)
		{
			return out.size() + " events " + out.subList(0, 4) + " ... " + out.subList(out.size() - 2, out.size());
		}
		return out.toString();
	}

	/**
	 * Starts the bare call, and at its first MOVED starts the gesture on another thread and holds the
	 * bare side until the gesture dispatches or the overlap window ends.
	 */
	private Thread bareFirstThenGesture(Runnable bareCall, Runnable gestureCall) throws InterruptedException
	{
		AtomicReference<Thread> gestureRef = new AtomicReference<>();
		AtomicBoolean triggered = new AtomicBoolean();
		Thread bare = daemon("bare-trajectory", bareCall);
		hook = d -> {
			if (d.thread == bare && d.id == MouseEvent.MOUSE_MOVED && triggered.compareAndSet(false, true))
			{
				Thread gesture = daemon("second-caller", gestureCall);
				gestureRef.set(gesture);
				gesture.start();
				awaitEvent(e -> e.thread == gesture, OVERLAP_WAIT_MS);
			}
		};
		bare.start();
		joinOrFail(bare, "the first call");
		assertTrue("the first call never dispatched a MOVED, so the scenario did not run", triggered.get());
		Thread gesture = gestureRef.get();
		joinOrFail(gesture, "the second call");
		return gesture;
	}

	/** Reads the actor once the click's own trajectory is done, waiting for a bare trajectory to dispatch. */
	private final class SlowActorLookupEntry extends NewMenuEntry
	{
		private final AtomicReference<Thread> bare;
		private final AtomicBoolean waited = new AtomicBoolean();

		SlowActorLookupEntry(AtomicReference<Thread> bare)
		{
			this.bare = bare;
		}

		@Override
		public Actor getActor()
		{
			if (waited.compareAndSet(false, true))
			{
				awaitEvent(e -> e.thread == bare.get(), OVERLAP_WAIT_MS);
			}
			return null;
		}
	}

	private void assertSerialized(String what, Thread gesture, String bareName, String gestureName)
	{
		List<Dispatched> all = snapshot();
		assertTrue(what + ": the " + bareName + " side dispatched nothing", all.stream().anyMatch(e -> e.thread != gesture));
		assertTrue(what + ": the " + gestureName + " side dispatched nothing", all.stream().anyMatch(e -> e.thread == gesture));

		StringBuilder runs = new StringBuilder();
		int switches = -1;
		Boolean current = null;
		List<String> run = new ArrayList<>();
		for (Dispatched event : all)
		{
			boolean isGesture = event.thread == gesture;
			if (current == null || current != isGesture)
			{
				flushRun(runs, current, run, bareName, gestureName);
				current = isGesture;
				switches++;
			}
			run.add(event.toString());
		}
		flushRun(runs, current, run, bareName, gestureName);

		assertTrue(what + " interleaved on the one pointer; dispatch order by caller: " + runs, switches <= 1);
	}

	private static void flushRun(StringBuilder out, Boolean isGesture, List<String> run, String bareName, String gestureName)
	{
		if (isGesture == null || run.isEmpty())
		{
			return;
		}
		out.append("\n  [").append(isGesture ? gestureName : bareName).append(" x").append(run.size()).append("] ");
		if (run.size() <= 6)
		{
			out.append(run);
		}
		else
		{
			out.append(run.subList(0, 3)).append(" ... ").append(run.subList(run.size() - 2, run.size()));
		}
		run.clear();
	}

	private void assertClickHappened(Thread gesture)
	{
		List<String> buttons = new ArrayList<>();
		for (Dispatched event : from(gesture))
		{
			if (event.id == MouseEvent.MOUSE_PRESSED || event.id == MouseEvent.MOUSE_RELEASED
				|| event.id == MouseEvent.MOUSE_CLICKED)
			{
				buttons.add(name(event.id));
			}
		}
		assertEquals("the click gesture must still press, release and click",
			names(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED), buttons);
	}

	private void assertTrajectoryReached(Thread thread, Point destination, String what)
	{
		List<Dispatched> fromThread = from(thread);
		assertFalse(what + ": dispatched nothing", fromThread.isEmpty());
		assertTrue(what + ": last MOVED is not on " + destination + ": " + fromThread,
			fromThread.get(fromThread.size() - 1).isMoveTo(destination));
	}

	private Thread daemon(String name, Runnable body)
	{
		Thread thread = new Thread(() -> {
			try
			{
				body.run();
			}
			catch (Throwable t)
			{
				threadFailures.add(t);
			}
		}, name);
		thread.setDaemon(true);
		started.add(thread);
		return thread;
	}

	private static void joinOrFail(Thread thread, String what) throws InterruptedException
	{
		thread.join(JOIN_MS);
		if (thread.isAlive())
		{
			fail(what + " did not return within " + JOIN_MS + "ms");
		}
	}

	private void record(MouseEvent event)
	{
		Dispatched dispatched = new Dispatched(event);
		synchronized (events)
		{
			events.add(dispatched);
			events.notifyAll();
		}
		hook.accept(dispatched);
	}

	private boolean awaitEvent(Predicate<Dispatched> condition, long timeoutMs)
	{
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
		synchronized (events)
		{
			while (true)
			{
				for (Dispatched event : events)
				{
					if (condition.test(event))
					{
						return true;
					}
				}
				long leftMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
				if (leftMs <= 0)
				{
					return false;
				}
				try
				{
					events.wait(leftMs);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					return false;
				}
			}
		}
	}

	/** Waits until no event has arrived for {@code quietMs}, so a deferred motion has finished. */
	private void awaitQuiet(long quietMs, long timeoutMs) throws InterruptedException
	{
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
		int seen = -1;
		while (System.nanoTime() < deadline)
		{
			int now = snapshot().size();
			if (now == seen)
			{
				return;
			}
			seen = now;
			Thread.sleep(quietMs);
		}
		fail("events kept arriving for " + timeoutMs + "ms");
	}

	private List<Dispatched> snapshot()
	{
		synchronized (events)
		{
			return new ArrayList<>(events);
		}
	}

	private List<Dispatched> from(Thread thread)
	{
		List<Dispatched> out = new ArrayList<>();
		for (Dispatched event : snapshot())
		{
			if (event.thread == thread)
			{
				out.add(event);
			}
		}
		return out;
	}

	private static int indexOf(List<Dispatched> list, Predicate<Dispatched> condition)
	{
		for (int i = 0; i < list.size(); i++)
		{
			if (condition.test(list.get(i)))
			{
				return i;
			}
		}
		return -1;
	}

	private static boolean awaitQuietly(CountDownLatch latch, long timeoutMs)
	{
		try
		{
			return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Waits until the thread is parked on a lock or monitor, or has finished. Running out of time
	 * is not a failure: the scenario still runs, only without the guarantee that it is parked.
	 */
	private static void awaitParkedOrDone(Thread thread, long timeoutMs) throws InterruptedException
	{
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
		while (System.nanoTime() < deadline)
		{
			Thread.State state = thread.getState();
			// A sleeping trajectory step is TIMED_WAITING with no blocker, which is not parked.
			if (!thread.isAlive() || state == Thread.State.BLOCKED || state == Thread.State.WAITING
				|| LockSupport.getBlocker(thread) != null)
			{
				return;
			}
			Thread.sleep(1L);
		}
	}

	private static List<String> namesOf(List<Dispatched> list)
	{
		List<String> out = new ArrayList<>();
		for (Dispatched event : list)
		{
			out.add(name(event.id));
		}
		return out;
	}

	private static List<String> names(int... ids)
	{
		List<String> out = new ArrayList<>();
		for (int id : ids)
		{
			out.add(name(id));
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
				return "ID" + id;
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
