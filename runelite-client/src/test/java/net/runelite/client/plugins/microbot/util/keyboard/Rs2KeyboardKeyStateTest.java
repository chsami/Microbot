package net.runelite.client.plugins.microbot.util.keyboard;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "A key the bot has down" is one the canvas received a bot KEY_PRESSED for and no KEY_RELEASED
 * since. Every assertion here reads that from the canvas's own events, plus isKeyHeld and the pause
 * futures, so it holds whatever the keyboard keeps internally to track it.
 *
 * <p>Pauses are requested from inside a canvas listener or the canvas lookup, both of which run on
 * the thread emitting the event, so each lands at an exact point of the emit.
 */
public class Rs2KeyboardKeyStateTest
{
	private static final long LISTENER_WAIT_MS = 8_000L;
	private static final long JOIN_MS = 15_000L;
	/**
	 * How long a thread inside an emit waits for a pause it requested there. A correct cleanup may
	 * itself wait for that emit to return, so the wait has to give up rather than deadlock.
	 */
	private static final long INSIDE_EMIT_WAIT_MS = 300L;
	/** A call on a UI thread that does not wait has returned well within this. */
	private static final long UI_PROMPT_MS = 2_000L;

	private static final class Record
	{
		final KeyEvent event;
		final Thread thread;

		Record(KeyEvent event, Thread thread)
		{
			this.event = event;
			this.thread = thread;
		}
	}

	private final class Caller
	{
		final String label;
		final Thread thread;
		volatile Throwable failure;

		Caller(String label, Runnable call)
		{
			this.label = label;
			this.thread = new Thread(() ->
			{
				try
				{
					call.run();
				}
				catch (Throwable t)
				{
					failure = t;
				}
			}, "kb-key-state-test-" + label);
			thread.setDaemon(true);
			callers.add(this);
		}

		Caller start()
		{
			thread.start();
			return this;
		}
	}

	private final List<Record> records = Collections.synchronizedList(new ArrayList<>());
	private final List<Caller> callers = new CopyOnWriteArrayList<>();
	private final List<CountDownLatch> releaseOnTeardown = new CopyOnWriteArrayList<>();
	private volatile Consumer<Record> onEvent = r -> { };
	/** Runs on the thread looking up the canvas, which every emit does before it reaches a listener. */
	private volatile Runnable onCanvasLookup = () -> { };
	private volatile Thread clientThread;
	private Object previousClient;
	private Canvas canvas;
	private KeyListener recorder;

	@Before
	public void before() throws Exception
	{
		ScriptPauseController.setPaused(false).get(5, TimeUnit.SECONDS);

		canvas = new Canvas();
		recorder = new KeyListener()
		{
			@Override
			public void keyTyped(KeyEvent e)
			{
				record(e);
			}

			@Override
			public void keyPressed(KeyEvent e)
			{
				record(e);
			}

			@Override
			public void keyReleased(KeyEvent e)
			{
				record(e);
			}
		};
		canvas.addKeyListener(recorder);

		Client client = mock(Client.class);
		when(client.getCanvas()).thenAnswer(invocation ->
		{
			onCanvasLookup.run();
			return canvas;
		});
		when(client.isClientThread()).thenAnswer(invocation -> Thread.currentThread() == clientThread);
		previousClient = swapClient(client);

		InputArbiter.resetForTest();
		Rs2Keyboard.releaseHeldKeys();
		records.clear();
	}

	@After
	public void after() throws Exception
	{
		for (CountDownLatch latch : releaseOnTeardown)
		{
			latch.countDown();
		}
		onEvent = r -> { };
		onCanvasLookup = () -> { };
		List<String> stillRunning = new ArrayList<>();
		for (Caller caller : callers)
		{
			caller.thread.join(JOIN_MS);
			if (caller.thread.isAlive())
			{
				stillRunning.add(caller.label);
			}
		}

		ScriptPauseController.setPaused(false).get(5, TimeUnit.SECONDS);
		InputArbiter.resetForTest();
		Rs2Keyboard.releaseHeldKeys();
		clientThread = null;
		swapClient(previousClient);
		while (BotEventGuard.isSynthetic())
		{
			BotEventGuard.end();
		}

		assertTrue("threads started by the test never finished: " + stillRunning, stillRunning.isEmpty());
	}

	// KS1, with KS2 and KS5 checked on the same run

	@Test(timeout = 30_000)
	public void thePauseCleanupReleasesTheKeyAStringHasDown() throws Exception
	{
		assertCleanupReleasesKeyDownIn("typeString(\"ab\")", () -> Rs2Keyboard.typeString("ab"), 'a');
	}

	@Test(timeout = 30_000)
	public void thePauseCleanupReleasesTheKeyAKeyPressCharHasDown() throws Exception
	{
		assertCleanupReleasesKeyDownIn("keyPress('a')", () -> Rs2Keyboard.keyPress('a'), 'a');
	}

	@Test(timeout = 30_000)
	public void thePauseCleanupReleasesTheKeyAKeyPressKeyCodeHasDown() throws Exception
	{
		assertCleanupReleasesKeyDownIn("keyPress(VK_1)", () -> Rs2Keyboard.keyPress(KeyEvent.VK_1), '1');
	}

	@Test(timeout = 30_000)
	public void thePauseCleanupReleasesTheKeyAnEnterHasDown() throws Exception
	{
		assertCleanupReleasesKeyDownIn("enter()", Rs2Keyboard::enter, '\n');
	}

	@Test(timeout = 30_000)
	public void thePauseCleanupReleasesACapitalAndTheShiftAroundIt() throws Exception
	{
		assertCleanupReleasesKeyDownIn("typeString(\"A\")", () -> Rs2Keyboard.typeString("A"), 'A');
	}

	@Test(timeout = 30_000)
	public void thePauseCleanupReleasesKeysHeldWithKeyHoldAndHoldShift() throws Exception
	{
		Rs2Keyboard.keyHold(KeyEvent.VK_UP);
		Rs2Keyboard.holdShift();
		int pauseIndex = records.size();

		awaitFuture(ScriptPauseController.setPaused(true), "pause cleanup");
		List<String> downAtCleanup = keysDown(snapshot());
		boolean upRecorded = Rs2Keyboard.isKeyHeld(KeyEvent.VK_UP);
		boolean shiftRecorded = Rs2Keyboard.isKeyHeld(KeyEvent.VK_SHIFT);
		awaitFuture(ScriptPauseController.setPaused(false), "resume");
		// The calls that pressed them reach their own releases after the resume, as Rs2Camera does.
		Rs2Keyboard.keyRelease(KeyEvent.VK_UP);
		Rs2Keyboard.releaseShift();

		assertTrue("the pause cleanup completed with keys held through keyHold/holdShift still down: " + downAtCleanup,
			downAtCleanup.isEmpty());
		assertFalse("the cleanup released UP but it is still recorded as held", upRecorded);
		assertFalse("the cleanup released Shift but it is still recorded as held", shiftRecorded);
		assertPairing("keyHold and holdShift across a pause", snapshot());
		assertOnlyReleasesAfter("keyHold and holdShift across a pause", pauseIndex, snapshot());
	}

	// KS3

	@Test(timeout = 30_000)
	public void releaseHeldKeysReleasesTheKeyASequenceOnAnotherThreadHasDown() throws Exception
	{
		Caller typing = new Caller("typeString(\"ab\")", () -> Rs2Keyboard.typeString("ab"));
		AtomicBoolean aDownWhenReturned = new AtomicBoolean(true);
		Caller releaser = new Caller("releaseHeldKeys", () ->
		{
			Rs2Keyboard.releaseHeldKeys();
			aDownWhenReturned.set(keysDown(snapshot()).contains(KeyEvent.getKeyText(KeyEvent.VK_A)));
		});
		AtomicBoolean fired = new AtomicBoolean();
		onEvent = r ->
		{
			// 'a' is down: its PRESSED went out and its RELEASED cannot come before this listener returns.
			if (r.thread == typing.thread && r.event.getID() == KeyEvent.KEY_TYPED && r.event.getKeyChar() == 'a'
				&& fired.compareAndSet(false, true))
			{
				releaser.start();
				joinQuietly(releaser.thread, INSIDE_EMIT_WAIT_MS);
			}
		};
		typing.start();
		join(typing, releaser);

		assertTrue("the release was never triggered", fired.get());
		assertFalse("releaseHeldKeys returned while 'a', pressed by a typeString on another thread, was still down at "
			+ "the canvas: " + describeAll(snapshot()), aDownWhenReturned.get());
		assertPairing("releaseHeldKeys during another thread's typeString", snapshot());
		assertEquals("string as typed", "ab", typed(snapshot()));
	}

	// KS4

	@Test(timeout = 30_000)
	public void holdShiftPressedAsThePauseCleanupRunsIsNotLeftDown() throws Exception
	{
		assertKeyPressedAsCleanupRunsEndsUp("holdShift()", Rs2Keyboard::holdShift, KeyEvent.VK_SHIFT, true);
	}

	@Test(timeout = 30_000)
	public void keyHoldPressedAsThePauseCleanupRunsIsNotLeftDown() throws Exception
	{
		assertKeyPressedAsCleanupRunsEndsUp("keyHold(VK_UP)", () -> Rs2Keyboard.keyHold(KeyEvent.VK_UP), KeyEvent.VK_UP, true);
	}

	@Test(timeout = 30_000)
	public void aStringsKeyPressedAsThePauseCleanupRunsIsNotLeftDown() throws Exception
	{
		assertKeyPressedAsCleanupRunsEndsUp("typeString(\"a\")", () -> Rs2Keyboard.typeString("a"), KeyEvent.VK_A, false);
	}

	@Test(timeout = 30_000)
	public void aNonPrintableKeyPressPressedAsThePauseCleanupRunsIsNotLeftDown() throws Exception
	{
		assertKeyPressedAsCleanupRunsEndsUp("keyPress(VK_F1)", () -> Rs2Keyboard.keyPress(KeyEvent.VK_F1), KeyEvent.VK_F1, false);
	}

	// KS5

	@Test(timeout = 30_000)
	public void aStringPausedDuringItsFirstCanvasLookupSendsNothing() throws Exception
	{
		assertPausedInLookupSendsNothing("typeString(\"ab\")", () -> Rs2Keyboard.typeString("ab"), false);
	}

	@Test(timeout = 30_000)
	public void aStringPausedAndResumedDuringItsFirstCanvasLookupSendsNothing() throws Exception
	{
		assertPausedInLookupSendsNothing("typeString(\"ab\")", () -> Rs2Keyboard.typeString("ab"), true);
	}

	@Test(timeout = 30_000)
	public void keyHoldPausedDuringItsCanvasLookupSendsNothing() throws Exception
	{
		assertPausedInLookupSendsNothing("keyHold(VK_UP)", () -> Rs2Keyboard.keyHold(KeyEvent.VK_UP), false);
	}

	@Test(timeout = 30_000)
	public void holdShiftPausedAndResumedDuringItsCanvasLookupSendsNothing() throws Exception
	{
		assertPausedInLookupSendsNothing("holdShift()", Rs2Keyboard::holdShift, true);
	}

	// KS7

	@Test(timeout = 60_000)
	public void noCallOnAUiThreadWaitsForAnotherThreadInsideAKeyPress() throws Exception
	{
		// Whatever a correct implementation holds while a key press is being emitted, these two hold it:
		// a sequence (with the keyboard) and a bare hold, each parked inside its own KEY_PRESSED.
		CountDownLatch release = releasable();
		CountDownLatch sequenceParked = new CountDownLatch(1);
		CountDownLatch holdParked = new CountDownLatch(1);
		Caller sequence = new Caller("typeString(\"a\")", () -> Rs2Keyboard.typeString("a"));
		Caller hold = new Caller("keyHold(VK_UP)", () -> Rs2Keyboard.keyHold(KeyEvent.VK_UP));
		AtomicBoolean sequenceFired = new AtomicBoolean();
		AtomicBoolean holdFired = new AtomicBoolean();
		onEvent = r ->
		{
			if (r.event.getID() != KeyEvent.KEY_PRESSED)
			{
				return;
			}
			if (r.thread == sequence.thread && sequenceFired.compareAndSet(false, true))
			{
				sequenceParked.countDown();
				awaitQuietly(release, LISTENER_WAIT_MS);
			}
			else if (r.thread == hold.thread && holdFired.compareAndSet(false, true))
			{
				holdParked.countDown();
				awaitQuietly(release, LISTENER_WAIT_MS);
			}
		};
		sequence.start();
		hold.start();
		assertTrue("the sequence never reached its key press", sequenceParked.await(5, TimeUnit.SECONDS));
		assertTrue("the hold never reached its key press", holdParked.await(5, TimeUnit.SECONDS));

		List<String> waited = new ArrayList<>();
		try
		{
			waited.addAll(runOnUiThreads(this::everyKeyboardCall));
		}
		finally
		{
			release.countDown();
		}
		join(sequence, hold);
		Rs2Keyboard.releaseHeldKeys();

		assertTrue("calls on a UI thread waited while other threads were inside a key press: " + waited, waited.isEmpty());
		assertPairing("UI-thread calls during other threads' key presses", snapshot());
	}

	@Test(timeout = 60_000)
	public void noCallOnAUiThreadWaitsForThePauseCleanupInsideARelease() throws Exception
	{
		Rs2Keyboard.keyHold(KeyEvent.VK_W);
		CountDownLatch release = releasable();
		CountDownLatch cleanupParked = new CountDownLatch(1);
		Thread testThread = Thread.currentThread();
		AtomicBoolean fired = new AtomicBoolean();
		// Whatever the cleanup holds while it releases a key, it holds here. UI threads are excluded so
		// that a UI-thread release of W, if one happens, is never what parks.
		onEvent = r ->
		{
			if (r.event.getID() == KeyEvent.KEY_RELEASED && r.event.getKeyCode() == KeyEvent.VK_W
				&& r.thread != testThread && r.thread != clientThread && !SwingUtilities.isEventDispatchThread()
				&& fired.compareAndSet(false, true))
			{
				cleanupParked.countDown();
				awaitQuietly(release, LISTENER_WAIT_MS);
			}
		};
		Future<Void> pause = ScriptPauseController.setPaused(true);
		assertTrue("the pause cleanup never released W", cleanupParked.await(5, TimeUnit.SECONDS));

		List<String> waited = new ArrayList<>();
		try
		{
			waited.addAll(runOnUiThreads(this::everyKeyboardCall));
		}
		finally
		{
			release.countDown();
		}
		awaitFuture(pause, "pause cleanup");
		awaitFuture(ScriptPauseController.setPaused(false), "resume");

		assertTrue("calls on a UI thread waited while the pause cleanup was inside a key release: " + waited,
			waited.isEmpty());
		assertPairing("UI-thread calls during the pause cleanup", snapshot());
	}

	// KS9

	@Test(timeout = 30_000)
	public void aStringTypesACharacterWhoseKeyIsHeld() throws Exception
	{
		Rs2Keyboard.keyHold(KeyEvent.VK_A);
		int start = records.size();
		Rs2Keyboard.typeString("cab");
		List<Record> during = since(start);
		boolean stillHeld = Rs2Keyboard.isKeyHeld(KeyEvent.VK_A);
		Rs2Keyboard.keyRelease(KeyEvent.VK_A);

		assertEquals("typeString(\"cab\") with A held by keyHold must type every character, but sent "
			+ describeAll(during), "cab", typed(during));
		assertNoPressOrRelease(KeyEvent.VK_A, during, "typeString(\"cab\") with A held by keyHold");
		assertTrue("A, held by keyHold, is no longer recorded as held after typeString(\"cab\")", stillHeld);
		assertPairing("typeString(\"cab\") with A held", snapshot());
	}

	@Test(timeout = 30_000)
	public void keyPressCharTypesACharacterWhoseKeyIsHeld() throws Exception
	{
		assertTypedWhileHeld("keyPress('a')", () -> Rs2Keyboard.keyPress('a'), KeyEvent.VK_A, "a");
	}

	@Test(timeout = 30_000)
	public void keyPressPrintableKeyCodeTypesACharacterWhoseKeyIsHeld() throws Exception
	{
		assertTypedWhileHeld("keyPress(VK_1)", () -> Rs2Keyboard.keyPress(KeyEvent.VK_1), KeyEvent.VK_1, "1");
	}

	@Test(timeout = 30_000)
	public void keyPressNonPrintableKeyCodeSendsNothingForAKeyThatIsHeld() throws Exception
	{
		assertTypedWhileHeld("keyPress(VK_F1)", () -> Rs2Keyboard.keyPress(KeyEvent.VK_F1), KeyEvent.VK_F1, "");
	}

	@Test(timeout = 30_000)
	public void aStringTypesACharacterWhoseKeyAnotherThreadIsPressing() throws Exception
	{
		CountDownLatch release = releasable();
		CountDownLatch parked = new CountDownLatch(1);
		Caller hold = new Caller("keyHold(VK_A)", () -> Rs2Keyboard.keyHold(KeyEvent.VK_A));
		AtomicBoolean fired = new AtomicBoolean();
		// Parked inside its own KEY_PRESSED: the press is in flight for as long as the test needs.
		onEvent = r ->
		{
			if (r.thread == hold.thread && r.event.getID() == KeyEvent.KEY_PRESSED && fired.compareAndSet(false, true))
			{
				parked.countDown();
				awaitQuietly(release, LISTENER_WAIT_MS);
			}
		};
		hold.start();
		assertTrue("the other thread never reached its press of A", parked.await(5, TimeUnit.SECONDS));

		Caller string = new Caller("typeString(\"ab\")", () -> Rs2Keyboard.typeString("ab")).start();
		// A correct string may type at once or wait for the press in flight; the outcome below is the
		// same either way, so the press is released whether or not the string has finished.
		string.thread.join(3_000);
		release.countDown();
		join(hold, string);
		int beforeOwnRelease = records.size();
		boolean stillHeld = Rs2Keyboard.isKeyHeld(KeyEvent.VK_A);
		Rs2Keyboard.keyRelease(KeyEvent.VK_A);

		List<Record> stringEvents = from(string.thread, snapshot());
		assertEquals("typeString(\"ab\") while another thread was mid-press of A must type both characters, but sent "
			+ describeAll(stringEvents), "ab", typed(stringEvents));
		assertNoPressOrRelease(KeyEvent.VK_A, stringEvents, "typeString(\"ab\") while another thread was pressing A");
		List<String> earlyReleases = new ArrayList<>();
		for (Record r : snapshot().subList(0, beforeOwnRelease))
		{
			if (r.event.getID() == KeyEvent.KEY_RELEASED && r.event.getKeyCode() == KeyEvent.VK_A)
			{
				earlyReleases.add(describe(r.event) + "@" + r.thread.getName());
			}
		}
		assertTrue("A was released before its holder released it, so the other thread's press did not survive the "
			+ "string: " + earlyReleases, earlyReleases.isEmpty());
		assertTrue("the other thread's press of A is no longer recorded as held after the string", stillHeld);
		assertPairing("typeString(\"ab\") during another thread's press of A", snapshot());
	}

	// KS10

	@Test(timeout = 30_000)
	public void aListenerThrowingOnAStringsKeyTypedStrandsNoKey() throws Exception
	{
		assertThrowStrandsNoKey("typeString(\"ab\")", () -> Rs2Keyboard.typeString("ab"),
			e -> e.getID() == KeyEvent.KEY_TYPED && e.getKeyChar() == 'a', KeyEvent.VK_A);
		int later = records.size();
		Rs2Keyboard.typeString("xay");
		Rs2Keyboard.keyPress('a');
		assertTypedNormally("typeString(\"xay\") then keyPress('a') after the throw", since(later), "xaya");
	}

	@Test(timeout = 30_000)
	public void aListenerThrowingOnAStringsKeyPressedStrandsNoKey() throws Exception
	{
		assertThrowStrandsNoKey("typeString(\"ab\")", () -> Rs2Keyboard.typeString("ab"),
			e -> e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_A, KeyEvent.VK_A);
		int later = records.size();
		Rs2Keyboard.typeString("a");
		assertTypedNormally("typeString(\"a\") after the throw", since(later), "a");
	}

	@Test(timeout = 30_000)
	public void aListenerThrowingOnAStringsKeyReleasedStrandsNoKey() throws Exception
	{
		assertThrowStrandsNoKey("typeString(\"ab\")", () -> Rs2Keyboard.typeString("ab"),
			e -> e.getID() == KeyEvent.KEY_RELEASED && e.getKeyCode() == KeyEvent.VK_A, KeyEvent.VK_A);
		int later = records.size();
		Rs2Keyboard.typeString("a");
		assertTypedNormally("typeString(\"a\") after the throw", since(later), "a");
	}

	@Test(timeout = 30_000)
	public void aListenerThrowingOnACapitalStrandsNeitherItNorShift() throws Exception
	{
		assertThrowStrandsNoKey("typeString(\"Ab\")", () -> Rs2Keyboard.typeString("Ab"),
			e -> e.getID() == KeyEvent.KEY_TYPED && e.getKeyChar() == 'A', KeyEvent.VK_A);
		assertFalse("Shift left recorded as held after typeString(\"Ab\") threw", Rs2Keyboard.isKeyHeld(KeyEvent.VK_SHIFT));
		int later = records.size();
		Rs2Keyboard.typeString("A");
		List<Record> after = since(later);
		assertEquals("typeString(\"A\") after the throw, as typed: " + describeAll(after), "A", typed(after));
		assertPairing("typeString(\"A\") after the throw", after);
	}

	@Test(timeout = 30_000)
	public void aListenerThrowingOnKeyHoldsPressLeavesTheKeyNotHeld() throws Exception
	{
		assertThrowStrandsNoKey("keyHold(VK_UP)", () -> Rs2Keyboard.keyHold(KeyEvent.VK_UP),
			e -> e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_UP, KeyEvent.VK_UP);
		int later = records.size();
		Rs2Keyboard.keyHold(KeyEvent.VK_UP);
		boolean held = Rs2Keyboard.isKeyHeld(KeyEvent.VK_UP);
		Rs2Keyboard.keyRelease(KeyEvent.VK_UP);
		assertTrue("a later keyHold(VK_UP) did not hold UP", held);
		assertPressThenRelease("keyHold then keyRelease of UP after the throw", since(later), KeyEvent.VK_UP);
	}

	@Test(timeout = 30_000)
	public void aListenerThrowingOnHoldShiftsPressLeavesShiftNotHeld() throws Exception
	{
		assertThrowStrandsNoKey("holdShift()", Rs2Keyboard::holdShift,
			e -> e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_SHIFT, KeyEvent.VK_SHIFT);
		int later = records.size();
		Rs2Keyboard.holdShift();
		boolean held = Rs2Keyboard.isKeyHeld(KeyEvent.VK_SHIFT);
		Rs2Keyboard.releaseShift();
		assertTrue("a later holdShift() did not hold Shift", held);
		assertPressThenRelease("holdShift then releaseShift after the throw", since(later), KeyEvent.VK_SHIFT);
	}

	// Scenarios

	/**
	 * Holds the key with keyHold, then makes the call: it must send exactly the KEY_TYPED of
	 * {@code typedText} for that key, no press or release of it, and leave the hold down.
	 */
	private void assertTypedWhileHeld(String name, Runnable call, int keyCode, String typedText)
	{
		Rs2Keyboard.keyHold(keyCode);
		int start = records.size();
		call.run();
		List<Record> during = since(start);
		boolean stillHeld = Rs2Keyboard.isKeyHeld(keyCode);
		Rs2Keyboard.keyRelease(keyCode);

		String key = KeyEvent.getKeyText(keyCode);
		assertEquals(name + " with " + key + " held by keyHold must send " + (typedText.isEmpty() ? "nothing"
			: "its KEY_TYPED") + ", but sent " + describeAll(during), typedText, typed(during));
		assertNoPressOrRelease(keyCode, during, name + " with " + key + " held by keyHold");
		assertTrue(key + ", held by keyHold, is no longer recorded as held after " + name, stillHeld);
		assertPairing(name + " with " + key + " held", snapshot());
	}

	/**
	 * Makes the call with a listener, registered after the recording one, that throws once on the
	 * matching event. The recording listener therefore sees every event the throwing one sees, so any
	 * KEY_PRESSED it records is one at least one listener saw, and that press must get its release.
	 */
	private void assertThrowStrandsNoKey(String name, Runnable call, java.util.function.Predicate<KeyEvent> when,
		int keyCode)
	{
		AtomicBoolean thrown = new AtomicBoolean();
		// Disarmed once the call returns, so later calls and the teardown's release never meet it.
		AtomicBoolean armed = new AtomicBoolean(true);
		canvas.addKeyListener(new KeyListener()
		{
			@Override
			public void keyTyped(KeyEvent e)
			{
				maybeThrow(e);
			}

			@Override
			public void keyPressed(KeyEvent e)
			{
				maybeThrow(e);
			}

			@Override
			public void keyReleased(KeyEvent e)
			{
				maybeThrow(e);
			}

			private void maybeThrow(KeyEvent e)
			{
				if (armed.get() && when.test(e) && thrown.compareAndSet(false, true))
				{
					throw new ListenerFailure("listener threw on " + describe(e));
				}
			}
		});
		assertTrue("the recording listener must come first, so it sees every event the throwing one sees",
			canvas.getKeyListeners()[0] == recorder);

		Throwable failure = null;
		try
		{
			call.run();
		}
		catch (Throwable t)
		{
			failure = t;
		}
		armed.set(false);
		List<Record> events = snapshot();
		List<String> down = keysDown(events);
		boolean recorded = Rs2Keyboard.isKeyHeld(keyCode);

		String key = KeyEvent.getKeyText(keyCode);
		assertTrue("the listener never threw during " + name, thrown.get());
		assertTrue("the listener's exception must reach the caller of " + name + ", but it got " + failure,
			failure instanceof ListenerFailure);
		assertTrue("after " + name + " threw, keys it pressed were left down at the canvas: " + down + "; events: "
			+ describeAll(events), down.isEmpty());
		assertFalse("after " + name + " threw, " + key + " was left recorded as down", recorded);
		assertPairing(name + " with a throwing listener", events);
	}

	/** Each character pressed, typed and released in order, lowercase only. */
	private static void assertTypedNormally(String what, List<Record> events, String text)
	{
		List<String> expected = new ArrayList<>();
		for (char c : text.toCharArray())
		{
			int keyCode = Rs2Keyboard.keyCodeFor(c);
			expected.add("PRESSED(" + KeyEvent.getKeyText(keyCode) + "," + c + ")");
			expected.add("TYPED(" + KeyEvent.getKeyText(KeyEvent.VK_UNDEFINED) + "," + c + ")");
			expected.add("RELEASED(" + KeyEvent.getKeyText(keyCode) + "," + c + ")");
		}
		List<String> actual = new ArrayList<>();
		for (Record r : events)
		{
			actual.add(describe(r.event));
		}
		assertEquals(what + " must press, type and release each character", expected, actual);
	}

	private static void assertPressThenRelease(String what, List<Record> events, int keyCode)
	{
		List<String> actual = new ArrayList<>();
		for (Record r : events)
		{
			actual.add(describe(r.event));
		}
		String key = KeyEvent.getKeyText(keyCode);
		List<String> expected = new ArrayList<>();
		expected.add("PRESSED(" + key + ",-)");
		expected.add("RELEASED(" + key + ",-)");
		assertEquals(what, expected, actual);
	}

	private static void assertNoPressOrRelease(int keyCode, List<Record> events, String what)
	{
		List<String> sent = new ArrayList<>();
		for (Record r : events)
		{
			if (r.event.getKeyCode() == keyCode
				&& (r.event.getID() == KeyEvent.KEY_PRESSED || r.event.getID() == KeyEvent.KEY_RELEASED))
			{
				sent.add(describe(r.event) + "@" + r.thread.getName());
			}
		}
		assertTrue(what + " sent a KEY_PRESSED or KEY_RELEASED for " + KeyEvent.getKeyText(keyCode)
			+ ", which is already down: " + sent, sent.isEmpty());
	}

	/** Thrown by a test listener, so the test can tell it from anything else reaching the caller. */
	private static final class ListenerFailure extends RuntimeException
	{
		ListenerFailure(String message)
		{
			super(message);
		}
	}

	/**
	 * Pauses from the listener at the trigger character's KEY_TYPED, when its PRESSED has gone out and its
	 * RELEASED cannot have. Nothing is awaited inside the listener, so the cleanup may wait for it.
	 */
	private void assertCleanupReleasesKeyDownIn(String name, Runnable call, char trigger) throws Exception
	{
		Caller caller = new Caller(name, call);
		AtomicReference<Future<Void>> pause = new AtomicReference<>();
		AtomicInteger pauseIndex = new AtomicInteger(-1);
		CountDownLatch requested = new CountDownLatch(1);
		AtomicBoolean fired = new AtomicBoolean();
		onEvent = r ->
		{
			if (r.thread == caller.thread && r.event.getID() == KeyEvent.KEY_TYPED && r.event.getKeyChar() == trigger
				&& fired.compareAndSet(false, true))
			{
				pauseIndex.set(records.size());
				pause.set(ScriptPauseController.setPaused(true));
				requested.countDown();
			}
		};
		caller.start();
		assertTrue(name + " never typed its trigger character", requested.await(5, TimeUnit.SECONDS));
		awaitFuture(pause.get(), "pause cleanup");
		List<Record> atCleanup = snapshot();
		awaitFuture(ScriptPauseController.setPaused(false), "resume");
		join(caller);

		List<String> downAtCleanup = keysDown(atCleanup);
		assertTrue(name + ": the pause cleanup completed while the canvas still had these bot keys down: " + downAtCleanup
			+ "; events until then: " + describeAll(atCleanup), downAtCleanup.isEmpty());
		assertPairing(name + " across a pause", snapshot());
		assertOnlyReleasesAfter(name + " across a pause", pauseIndex.get(), snapshot());
	}

	/**
	 * Pauses from inside the listener of the key's own KEY_PRESSED and waits there a bounded time for the
	 * cleanup. Today that wait completes with the key missed; a correct cleanup either waits for the
	 * dispatch or leaves the release to the pressing call. Both end with the key up, released once.
	 */
	private void assertKeyPressedAsCleanupRunsEndsUp(String name, Runnable call, int keyCode, boolean returnsWhilePaused)
		throws Exception
	{
		Caller caller = new Caller(name, call);
		AtomicReference<Future<Void>> pause = new AtomicReference<>();
		AtomicInteger pauseIndex = new AtomicInteger(-1);
		CountDownLatch requested = new CountDownLatch(1);
		AtomicBoolean fired = new AtomicBoolean();
		onEvent = r ->
		{
			if (r.thread == caller.thread && r.event.getID() == KeyEvent.KEY_PRESSED && r.event.getKeyCode() == keyCode
				&& fired.compareAndSet(false, true))
			{
				pauseIndex.set(records.size());
				pause.set(ScriptPauseController.setPaused(true));
				requested.countDown();
				getQuietly(pause.get(), INSIDE_EMIT_WAIT_MS);
			}
		};
		caller.start();
		assertTrue(name + " never pressed its key", requested.await(5, TimeUnit.SECONDS));
		awaitFuture(pause.get(), "pause cleanup");
		String key = KeyEvent.getKeyText(keyCode);
		if (returnsWhilePaused)
		{
			// A hold returns at once, so both have finished here, still inside the pause.
			join(caller);
			List<String> down = keysDown(snapshot());
			boolean recorded = Rs2Keyboard.isKeyHeld(keyCode);
			awaitFuture(ScriptPauseController.setPaused(false), "resume");
			assertTrue(name + " returned and the pause cleanup finished, with " + key + " still down at the canvas: "
				+ describeAll(snapshot()), down.isEmpty());
			assertFalse(name + " returned and the pause cleanup finished, with " + key + " still recorded as held",
				recorded);
		}
		else
		{
			awaitFuture(ScriptPauseController.setPaused(false), "resume");
			join(caller);
		}

		assertTrue(key + " still down after " + name + " and the pause cleanup finished: " + describeAll(snapshot()),
			keysDown(snapshot()).isEmpty());
		assertFalse(key + " still recorded as held after " + name + " and the pause cleanup finished",
			Rs2Keyboard.isKeyHeld(keyCode));
		assertPairing(name + " pressed as the pause cleanup ran", snapshot());
		assertOnlyReleasesAfter(name + " pressed as the pause cleanup ran", pauseIndex.get(), snapshot());
	}

	/**
	 * Pauses from inside the call's first canvas lookup, before any listener has seen its event. With
	 * {@code resumeThere} the resume is requested there too, so the pause is over before the emit goes on.
	 */
	private void assertPausedInLookupSendsNothing(String name, Runnable call, boolean resumeThere) throws Exception
	{
		Caller caller = new Caller(name, call);
		AtomicReference<Future<Void>> pause = new AtomicReference<>();
		AtomicReference<Future<Void>> resume = new AtomicReference<>();
		AtomicInteger pauseIndex = new AtomicInteger(-1);
		CountDownLatch requested = new CountDownLatch(1);
		AtomicBoolean fired = new AtomicBoolean();
		onCanvasLookup = () ->
		{
			if (Thread.currentThread() == caller.thread && fired.compareAndSet(false, true))
			{
				pauseIndex.set(records.size());
				pause.set(ScriptPauseController.setPaused(true));
				getQuietly(pause.get(), INSIDE_EMIT_WAIT_MS);
				if (resumeThere)
				{
					resume.set(ScriptPauseController.setPaused(false));
					getQuietly(resume.get(), INSIDE_EMIT_WAIT_MS);
				}
				requested.countDown();
			}
		};
		caller.start();
		assertTrue(name + " never looked up the canvas", requested.await(5, TimeUnit.SECONDS));
		awaitFuture(pause.get(), "pause cleanup");
		if (!resumeThere)
		{
			// A hold returns while paused; a sequence may park until the resume. Either way it gets
			// the chance to send something before the resume.
			caller.thread.join(1_000);
			resume.set(ScriptPauseController.setPaused(false));
		}
		awaitFuture(resume.get(), "resume");
		join(caller);
		Rs2Keyboard.releaseHeldKeys();

		assertOnlyReleasesAfter(name + " paused " + (resumeThere ? "and resumed " : "") + "inside its canvas lookup",
			pauseIndex.get(), snapshot());
		assertPairing(name + " paused inside its canvas lookup", snapshot());
	}

	/** Returns the labels of the calls that did not return promptly, on the client thread and then the EDT. */
	private List<String> runOnUiThreads(Runnable calls) throws Exception
	{
		List<String> waited = new ArrayList<>();
		Caller client = new Caller("client thread", calls);
		clientThread = client.thread;
		client.start();
		client.thread.join(UI_PROMPT_MS);
		if (client.thread.isAlive())
		{
			waited.add("client thread");
		}

		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		SwingUtilities.invokeLater(() ->
		{
			try
			{
				calls.run();
			}
			catch (Throwable t)
			{
				failure.set(t);
			}
			finally
			{
				done.countDown();
			}
		});
		if (!done.await(UI_PROMPT_MS, TimeUnit.MILLISECONDS))
		{
			waited.add("event dispatch thread");
		}
		if (failure.get() != null)
		{
			throw new AssertionError("a keyboard call on the event dispatch thread threw", failure.get());
		}
		return waited;
	}

	/** Every public entry point, with each hold released again so the UI thread leaves nothing down. */
	private void everyKeyboardCall()
	{
		Rs2Keyboard.releaseHeldKeys();
		Rs2Keyboard.isKeyHeld(KeyEvent.VK_UP);
		Rs2Keyboard.keyHold(KeyEvent.VK_LEFT);
		Rs2Keyboard.keyRelease(KeyEvent.VK_LEFT);
		Rs2Keyboard.holdShift();
		Rs2Keyboard.releaseShift();
		Rs2Keyboard.typeString("x");
		Rs2Keyboard.keyPress('q');
		Rs2Keyboard.keyPress(KeyEvent.VK_1);
		Rs2Keyboard.keyPress(KeyEvent.VK_F1);
		Rs2Keyboard.enter();
	}

	// Assertions and helpers

	/** Keys with a KEY_PRESSED and no KEY_RELEASED since, in these events. */
	private static List<String> keysDown(List<Record> events)
	{
		List<Integer> down = new ArrayList<>();
		for (Record r : events)
		{
			Integer key = r.event.getKeyCode();
			if (r.event.getID() == KeyEvent.KEY_PRESSED && !down.contains(key))
			{
				down.add(key);
			}
			else if (r.event.getID() == KeyEvent.KEY_RELEASED)
			{
				down.remove(key);
			}
		}
		List<String> names = new ArrayList<>();
		for (Integer key : down)
		{
			names.add(KeyEvent.getKeyText(key));
		}
		return names;
	}

	/** Every KEY_PRESSED followed by exactly one KEY_RELEASED for that key, whoever sends it, and none before. */
	private static void assertPairing(String what, List<Record> events)
	{
		List<Integer> down = new ArrayList<>();
		List<String> violations = new ArrayList<>();
		for (int i = 0; i < events.size(); i++)
		{
			KeyEvent e = events.get(i).event;
			Integer key = e.getKeyCode();
			if (e.getID() == KeyEvent.KEY_PRESSED)
			{
				if (down.contains(key))
				{
					violations.add(i + ": " + KeyEvent.getKeyText(key) + " pressed again while already down");
				}
				else
				{
					down.add(key);
				}
			}
			else if (e.getID() == KeyEvent.KEY_RELEASED && !down.remove(key))
			{
				violations.add(i + ": " + KeyEvent.getKeyText(key) + " released while not down (a second release, or "
					+ "one before its press), from " + events.get(i).thread.getName());
			}
		}
		for (Integer key : down)
		{
			violations.add(KeyEvent.getKeyText(key) + " never released");
		}
		assertTrue(what + ": every KEY_PRESSED must be followed by exactly one KEY_RELEASED, but " + violations
			+ "; events: " + describeAll(events), violations.isEmpty());
	}

	/** After the pause began, only KEY_RELEASED may reach the canvas. */
	private static void assertOnlyReleasesAfter(String what, int pauseIndex, List<Record> events)
	{
		assertTrue(what + ": the pause point was never reached", pauseIndex >= 0);
		List<String> sent = new ArrayList<>();
		for (int i = pauseIndex; i < events.size(); i++)
		{
			if (events.get(i).event.getID() != KeyEvent.KEY_RELEASED)
			{
				sent.add(describe(events.get(i).event) + " from " + events.get(i).thread.getName());
			}
		}
		assertTrue(what + ": after the pause began, events other than an owed KEY_RELEASED reached the canvas: " + sent
			+ "; events: " + describeAll(events), sent.isEmpty());
	}

	private void join(Caller... toJoin) throws InterruptedException
	{
		for (Caller caller : toJoin)
		{
			caller.thread.join(JOIN_MS);
			assertFalse(caller.label + " never finished", caller.thread.isAlive());
			if (caller.failure != null)
			{
				throw new AssertionError(caller.label + " threw", caller.failure);
			}
		}
	}

	private static void awaitFuture(Future<?> future, String what) throws Exception
	{
		assertTrue(what + " was never requested", future != null);
		try
		{
			future.get(5, TimeUnit.SECONDS);
		}
		catch (java.util.concurrent.TimeoutException e)
		{
			throw new AssertionError(what + " did not complete within 5s", e);
		}
	}

	/** For waits inside an emit, which must give up rather than fail: the other side may wait for the emit. */
	private static void getQuietly(Future<?> future, long millis)
	{
		try
		{
			future.get(millis, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		catch (Exception ignored)
		{
			// Timed out or failed: the test checks the outcome once both sides have finished.
		}
	}

	private static void joinQuietly(Thread thread, long millis)
	{
		try
		{
			thread.join(millis);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	private static void awaitQuietly(CountDownLatch latch, long millis)
	{
		try
		{
			latch.await(millis, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	private CountDownLatch releasable()
	{
		CountDownLatch latch = new CountDownLatch(1);
		releaseOnTeardown.add(latch);
		return latch;
	}

	private void record(KeyEvent event)
	{
		Record r = new Record(event, Thread.currentThread());
		records.add(r);
		onEvent.accept(r);
	}

	private List<Record> since(int start)
	{
		List<Record> all = snapshot();
		return new ArrayList<>(all.subList(start, all.size()));
	}

	private static List<Record> from(Thread thread, List<Record> events)
	{
		List<Record> out = new ArrayList<>();
		for (Record r : events)
		{
			if (r.thread == thread)
			{
				out.add(r);
			}
		}
		return out;
	}

	private List<Record> snapshot()
	{
		synchronized (records)
		{
			return new ArrayList<>(records);
		}
	}

	private static String typed(List<Record> events)
	{
		StringBuilder typed = new StringBuilder();
		for (Record r : events)
		{
			if (r.event.getID() == KeyEvent.KEY_TYPED)
			{
				typed.append(r.event.getKeyChar());
			}
		}
		return typed.toString();
	}

	private static String describe(KeyEvent e)
	{
		String id = e.getID() == KeyEvent.KEY_PRESSED ? "PRESSED" : e.getID() == KeyEvent.KEY_RELEASED ? "RELEASED" : "TYPED";
		String ch = e.getKeyChar() == KeyEvent.CHAR_UNDEFINED ? "-" : e.getKeyChar() == '\n' ? "\\n" : String.valueOf(e.getKeyChar());
		return id + "(" + KeyEvent.getKeyText(e.getKeyCode()) + "," + ch + ")";
	}

	private static List<String> describeAll(List<Record> events)
	{
		List<String> out = new ArrayList<>();
		for (Record r : events)
		{
			out.add(describe(r.event) + "@" + r.thread.getName());
		}
		return out;
	}

	private static Object swapClient(Object value) throws Exception
	{
		Field field = Microbot.class.getDeclaredField("client");
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, value);
		return previous;
	}
}
