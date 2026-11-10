package net.runelite.client.plugins.microbot.util.keyboard;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
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
 * Keyboard emission is shared by every script thread, and each event's modifier mask is computed
 * from one shared set of held keys. A sequence (one call to typeString, keyPress or enter) must
 * therefore own the keyboard for its whole length, the way InputLoop serializes mouse gestures.
 *
 * <p>The canvas listener runs on the dispatching thread, so the tests act from inside it: other
 * threads are started while one is provably mid-sequence, instead of hoping a sleep lines up.
 *
 * <p>Every typed character costs a real hold and gap (see the timing constants in Rs2Keyboard),
 * so strings are as short as the claim allows: two characters where a lock taken per character
 * must be told apart from one taken per sequence, one where the string only has to hold the
 * keyboard.
 */
public class Rs2KeyboardSequenceLockTest
{
	private static final String TIMEOUT_HOOK = "setSequenceLockTimeoutForTest";
	private static final String DEFAULT_TIMEOUT_FIELD = "DEFAULT_SEQUENCE_LOCK_TIMEOUT_MS";
	/** The contract's default bound. No getter exists, so teardown restores this when the hook does. */
	private static final long DEFAULT_TIMEOUT_MS = 30_000L;
	/** Long enough that a serialized caller always outlasts the sequence ahead of it. */
	private static final long PATIENT_TIMEOUT_MS = 20_000L;
	private static final long LISTENER_WAIT_MS = 8_000L;
	/**
	 * How long a mid-sequence thread lets the others try to dispatch. Unserialized code dispatches
	 * within milliseconds; serialized code always spends the whole window, so it stays short.
	 */
	private static final long INTRUSION_WINDOW_MS = 200L;
	/** A call that must not wait for the keyboard has returned well within this. */
	private static final long AT_ONCE_MS = 1_000L;

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
		final Runnable call;
		final Thread thread;
		final CountDownLatch requested = new CountDownLatch(1);
		volatile boolean dispatched;
		volatile Throwable failure;

		Caller(String label, Runnable call)
		{
			this.label = label;
			this.call = call;
			this.thread = new Thread(() ->
			{
				requested.countDown();
				try
				{
					call.run();
				}
				catch (Throwable t)
				{
					failure = t;
				}
			}, "kb-sequence-test-" + label);
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
	/** Runs on the asking thread whenever anything asks the client whether it is the client thread. */
	private volatile Runnable onClientThreadQuery = () -> { };
	private volatile Thread clientThread;
	private Object previousClient;

	@Before
	public void before() throws Exception
	{
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);

		Canvas canvas = new Canvas();
		canvas.addKeyListener(new KeyListener()
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
		});

		Client client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		// Only the designated thread is the client thread, so Global's sleeps stay real on the others.
		when(client.isClientThread()).thenAnswer(invocation ->
		{
			onClientThreadQuery.run();
			return Thread.currentThread() == clientThread;
		});
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
		List<String> stillRunning = new ArrayList<>();
		for (Caller caller : callers)
		{
			caller.thread.join(PATIENT_TIMEOUT_MS);
			if (caller.thread.isAlive())
			{
				stillRunning.add(caller.label);
			}
		}

		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		InputArbiter.resetForTest();
		Rs2Keyboard.releaseHeldKeys();
		Method hook = findTimeoutHook();
		if (hook != null)
		{
			hook.invoke(null, DEFAULT_TIMEOUT_MS);
		}
		clientThread = null;
		swapClient(previousClient);
		while (BotEventGuard.isSynthetic())
		{
			BotEventGuard.end();
		}

		assertTrue("threads started by the test never finished: " + stillRunning, stillRunning.isEmpty());
	}

	// K1 and K2

	@Test(timeout = 60_000)
	public void twoConcurrentStringsAreDispatchedOneAfterTheOther() throws Exception
	{
		setTimeoutIfHookExists(PATIENT_TIMEOUT_MS);
		// Two characters each, so a lock taken per character, which lets the other thread in between
		// them, fails as surely as no lock at all. The capital puts Shift in the shared held set.
		Caller shifted = new Caller("typeString(\"Ab\")", () -> Rs2Keyboard.typeString("Ab"));
		Caller plain = new Caller("typeString(\"cd\")", () -> Rs2Keyboard.typeString("cd"));

		// The letter's own PRESSED: Shift is recorded as held only after its PRESSED went out, so from
		// here until this character ends the shared set carries Shift.
		startDuring(shifted, e -> e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_A, plain);
		join(shifted, plain);

		List<String> leaked = new ArrayList<>();
		List<KeyEvent> plainEvents = eventsOf(plain);
		for (int i = 0; i < plainEvents.size(); i++)
		{
			KeyEvent e = plainEvents.get(i);
			if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0)
			{
				leaked.add(i + ":" + describe(e));
			}
		}
		String intrusions = intrusions(shifted, plain);
		assertTrue("two concurrent typeString calls must each own the keyboard for their whole length. "
			+ (intrusions.isEmpty() ? "" : "Interleaved: " + intrusions + ". ")
			+ (leaked.isEmpty() ? "" : leaked.size() + " of " + plainEvents.size() + " events of the string needing "
				+ "no Shift carried SHIFT_DOWN_MASK from the other thread's Shift: " + leaked),
			intrusions.isEmpty() && leaked.isEmpty());
		assertEquals("first string as typed", "Ab", typedBy(shifted));
		assertEquals("second string as typed", "cd", typedBy(plain));
	}

	// K3

	@Test(timeout = 60_000)
	public void keyPressAndEnterDoNotLandInsideAnotherThreadsString() throws Exception
	{
		setTimeoutIfHookExists(PATIENT_TIMEOUT_MS);
		Caller string = new Caller("typeString(\"ab\")", () -> Rs2Keyboard.typeString("ab"));
		Caller keyChar = new Caller("keyPress('q')", () -> Rs2Keyboard.keyPress('q'));
		Caller keyPrintable = new Caller("keyPress(VK_1)", () -> Rs2Keyboard.keyPress(KeyEvent.VK_1));
		// The non-printable path presses and releases through the same code as keyHold, but as one
		// call it is still a sequence.
		Caller keyNonPrintable = new Caller("keyPress(VK_F1)", () -> Rs2Keyboard.keyPress(KeyEvent.VK_F1));
		Caller enter = new Caller("enter()", Rs2Keyboard::enter);

		startDuring(string, e -> true, keyChar, keyPrintable, keyNonPrintable, enter);
		join(string, keyChar, keyPrintable, keyNonPrintable, enter);

		String intrusions = intrusions(string, keyChar, keyPrintable, keyNonPrintable, enter);
		assertTrue("each call's events must stay contiguous on the keyboard, but " + intrusions, intrusions.isEmpty());
		assertEquals("string as typed", "ab", typedBy(string));
		assertEvents(keyChar, 3, "q");
		assertEvents(keyPrintable, 3, "1");
		assertEvents(keyNonPrintable, 2, "");
		assertEvents(enter, 4, "\n\n");
	}

	@Test(timeout = 60_000)
	public void aStringDoesNotLandInsideAnotherThreadsEnter() throws Exception
	{
		setTimeoutIfHookExists(PATIENT_TIMEOUT_MS);
		Caller enter = new Caller("enter()", Rs2Keyboard::enter);
		Caller string = new Caller("typeString(\"x\")", () -> Rs2Keyboard.typeString("x"));

		// enter() is keyPress plus a trailing KEY_TYPED, so the whole of it, not just its keyPress, is
		// one sequence.
		startDuring(enter, e -> e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ENTER, string);
		join(enter, string);

		String intrusions = intrusions(enter, string);
		assertTrue("each call's events must stay contiguous on the keyboard, but " + intrusions, intrusions.isEmpty());
		assertEvents(enter, 4, "\n\n");
		assertEquals("string as typed", "x", typedBy(string));
	}

	// K4

	@Test(timeout = 60_000)
	public void theDefaultKeyboardWaitIsThirtySeconds() throws Exception
	{
		Field field;
		try
		{
			field = Rs2Keyboard.class.getDeclaredField(DEFAULT_TIMEOUT_FIELD);
		}
		catch (NoSuchFieldException e)
		{
			fail("Rs2Keyboard." + DEFAULT_TIMEOUT_FIELD + " is missing: the default wait for the keyboard is not "
				+ "exposed as a constant");
			return;
		}
		int modifiers = field.getModifiers();
		assertTrue(DEFAULT_TIMEOUT_FIELD + " must be static", Modifier.isStatic(modifiers));
		assertTrue(DEFAULT_TIMEOUT_FIELD + " must be final", Modifier.isFinal(modifiers));
		assertEquals(DEFAULT_TIMEOUT_FIELD + " type", long.class, field.getType());
		field.setAccessible(true);
		assertEquals("the default wait must outlast a long string typed by another thread",
			DEFAULT_TIMEOUT_MS, field.getLong(null));
	}

	@Test(timeout = 60_000)
	public void aSequenceThatCannotGetTheKeyboardInTimeDispatchesNothing() throws Exception
	{
		requireTimeoutHook().invoke(null, 200L);
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch release = releasable();
		Caller holder = new Caller("holder", () -> Rs2Keyboard.typeString("a"));
		parkFirstEventOf(holder, holding, release);
		holder.start();
		assertTrue("holder never dispatched its first key", holding.await(5, TimeUnit.SECONDS));

		List<Caller> blocked = startAll(entryPoints());
		List<String> stillWaiting = joinBy(blocked, 3_000);

		release.countDown();
		join(holder);
		joinAll(blocked);

		assertTrue("still waiting for the keyboard 3s into a 200ms bound: " + stillWaiting, stillWaiting.isEmpty());
		assertTrue("calls that could not get the keyboard within their bound must dispatch nothing, but "
			+ dispatchedBy(blocked), dispatchedBy(blocked).isEmpty());
		assertEquals("holder string as typed", "a", typedBy(holder));
	}

	// K5

	@Test(timeout = 60_000)
	public void theClientThreadNeverWaitsForTheKeyboard() throws Exception
	{
		setTimeoutIfHookExists(PATIENT_TIMEOUT_MS);
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch release = releasable();
		Caller script = new Caller("script", () -> Rs2Keyboard.typeString("a"));
		parkFirstEventOf(script, holding, release);
		script.start();
		assertTrue("script thread never dispatched its first key", holding.await(5, TimeUnit.SECONDS));

		// Global's sleeps are no-ops on the client thread, so these cost no typing time.
		Caller client = new Caller("client", () ->
		{
			Rs2Keyboard.typeString("xyz");
			Rs2Keyboard.keyPress('q');
			Rs2Keyboard.keyPress(KeyEvent.VK_1);
			Rs2Keyboard.keyPress(KeyEvent.VK_F1);
			Rs2Keyboard.enter();
		});
		clientThread = client.thread;
		client.start();
		client.thread.join(2_000);
		boolean finishedWhileHeld = !client.thread.isAlive();

		release.countDown();
		join(script, client);

		assertTrue("a sequence on the client thread waited for the keyboard another thread held mid-sequence",
			finishedWhileHeld);
		assertEquals("client-thread sequences as typed, dispatched as they are today", "xyzq1\n\n", typedBy(client));
		assertEquals("client-thread events", 3 * 3 + 3 + 3 + 2 + 4, eventsOf(client).size());
		assertEquals("script string as typed", "a", typedBy(script));
	}

	@Test(timeout = 60_000)
	public void theEventDispatchThreadNeverWaitsForTheKeyboard() throws Exception
	{
		setTimeoutIfHookExists(PATIENT_TIMEOUT_MS);
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch release = releasable();
		Caller script = new Caller("script", () -> Rs2Keyboard.typeString("a"));
		parkFirstEventOf(script, holding, release);
		script.start();
		assertTrue("script thread never dispatched its first key", holding.await(5, TimeUnit.SECONDS));

		// invokeLater plus a bounded latch, not invokeAndWait: a call that waits for the keyboard must
		// fail this test, not hang the test thread behind the EDT.
		AtomicReference<Thread> edt = new AtomicReference<>();
		AtomicReference<Throwable> edtFailure = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);
		boolean returnedPromptly;
		try
		{
			// The EDT is not the client thread here, so Global's sleeps are real. One enter(), which
			// nests keyPress, keeps the typing time small; the client-thread test covers every entry point.
			SwingUtilities.invokeLater(() ->
			{
				edt.set(Thread.currentThread());
				try
				{
					Rs2Keyboard.enter();
				}
				catch (Throwable t)
				{
					edtFailure.set(t);
				}
				finally
				{
					done.countDown();
				}
			});
			returnedPromptly = done.await(2, TimeUnit.SECONDS);
		}
		finally
		{
			release.countDown();
		}
		assertTrue("the event dispatch thread never finished its calls", done.await(PATIENT_TIMEOUT_MS + 5_000, TimeUnit.MILLISECONDS));
		join(script);

		assertTrue("a sequence on the event dispatch thread waited for the keyboard another thread held mid-sequence",
			returnedPromptly);
		assertNull("a call on the event dispatch thread threw", edtFailure.get());
		assertEquals("event dispatch thread enter() as typed, dispatched as it is today", "\n\n",
			typedOn(edt.get()));
		assertEquals("script string as typed", "a", typedBy(script));
	}

	// K6

	@Test(timeout = 60_000)
	public void holdsAndReleasesDoNotWaitForAnotherThreadsSequence() throws Exception
	{
		setTimeoutIfHookExists(PATIENT_TIMEOUT_MS);
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch release = releasable();
		Caller sequence = new Caller("sequence", () -> Rs2Keyboard.typeString("a"));
		// Parked between events, in the sleep that holds 'a' down, not inside a dispatch: the pause
		// cleanup may wait for a dispatch in flight, and that wait is not a wait for the sequence.
		parkInSleepAfterFirstTyped(sequence, holding, release);
		sequence.start();
		assertTrue("sequence thread never reached the sleep after its first key", holding.await(5, TimeUnit.SECONDS));

		Caller holds = new Caller("holds", () ->
		{
			Rs2Keyboard.keyHold(KeyEvent.VK_UP);
			Rs2Keyboard.keyRelease(KeyEvent.VK_UP);
			Rs2Keyboard.holdShift();
			Rs2Keyboard.releaseShift();
			Rs2Keyboard.keyHold(KeyEvent.VK_W);
			Rs2Keyboard.releaseHeldKeys();
		}).start();
		holds.thread.join(2_000);
		boolean finishedWhileHeld = !holds.thread.isAlive();

		release.countDown();
		join(sequence, holds);

		assertTrue("keyHold/keyRelease/holdShift/releaseShift/releaseHeldKeys waited for another thread's sequence",
			finishedWhileHeld);
		// releaseHeldKeys may also release the 'a' the sequence has down, so only the keys this thread
		// pressed are counted here; the pairing check below covers 'a'.
		List<KeyEvent> events = new ArrayList<>();
		for (KeyEvent e : eventsOf(holds))
		{
			if (e.getKeyCode() != KeyEvent.VK_A)
			{
				events.add(e);
			}
		}
		assertEquals("hold and release events: " + describeAll(events), 6, events.size());
		assertPressOrRelease(events.get(0), KeyEvent.KEY_PRESSED, KeyEvent.VK_UP);
		assertPressOrRelease(events.get(1), KeyEvent.KEY_RELEASED, KeyEvent.VK_UP);
		assertPressOrRelease(events.get(2), KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT);
		assertPressOrRelease(events.get(3), KeyEvent.KEY_RELEASED, KeyEvent.VK_SHIFT);
		assertPressOrRelease(events.get(4), KeyEvent.KEY_PRESSED, KeyEvent.VK_W);
		assertPressOrRelease(events.get(5), KeyEvent.KEY_RELEASED, KeyEvent.VK_W);
		assertEquals("sequence string as typed", "a", typedBy(sequence));
		List<String> pairing = pairingViolations(snapshot());
		assertTrue("every KEY_PRESSED must be followed by exactly one KEY_RELEASED: " + pairing, pairing.isEmpty());
	}

	@Test(timeout = 60_000)
	public void aKeyHeldOnAnotherThreadDoesNotMakeASequenceWait() throws Exception
	{
		setTimeoutIfHookExists(PATIENT_TIMEOUT_MS);
		// Rs2Camera.holdUntil keeps an arrow key down across a multi-second wait.
		CountDownLatch held = new CountDownLatch(1);
		CountDownLatch letGo = releasable();
		Caller camera = new Caller("camera", () ->
		{
			Rs2Keyboard.keyHold(KeyEvent.VK_UP);
			held.countDown();
			awaitQuietly(letGo, LISTENER_WAIT_MS);
			Rs2Keyboard.keyRelease(KeyEvent.VK_UP);
		}).start();
		assertTrue("camera thread never held its key", held.await(5, TimeUnit.SECONDS));

		Caller sequence = new Caller("sequence", () -> Rs2Keyboard.typeString("a")).start();
		sequence.thread.join(3_000);
		boolean finishedWhileHeld = !sequence.thread.isAlive();

		letGo.countDown();
		join(camera, sequence);

		assertTrue("a typeString waited for a key another thread holds with keyHold", finishedWhileHeld);
		assertEquals("string as typed", "a", typedBy(sequence));
		List<KeyEvent> cameraEvents = eventsOf(camera);
		assertEquals("camera key events: " + describeAll(cameraEvents), 2, cameraEvents.size());
		assertPressOrRelease(cameraEvents.get(0), KeyEvent.KEY_PRESSED, KeyEvent.VK_UP);
		assertPressOrRelease(cameraEvents.get(1), KeyEvent.KEY_RELEASED, KeyEvent.VK_UP);
	}

	// K7

	@Test(timeout = 60_000)
	public void enterNestsKeyPressWithoutDeadlock() throws Exception
	{
		setTimeoutIfHookExists(PATIENT_TIMEOUT_MS);
		Caller enter = new Caller("enter", Rs2Keyboard::enter).start();
		enter.thread.join(3_000);

		assertFalse("enter() calls keyPress on the same thread and must not wait on itself", enter.thread.isAlive());
		assertNull("enter() threw", enter.failure);
		List<KeyEvent> events = eventsOf(enter);
		assertEquals("enter() events: " + describeAll(events), 4, events.size());
		assertPressOrRelease(events.get(0), KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER);
		assertEquals("enter() typed", "\n\n", typedBy(enter));
		assertPressOrRelease(events.get(2), KeyEvent.KEY_RELEASED, KeyEvent.VK_ENTER);
	}

	@Test(timeout = 60_000)
	public void aStringThatHoldsShiftInternallyCompletes() throws Exception
	{
		setTimeoutIfHookExists(PATIENT_TIMEOUT_MS);
		Caller string = new Caller("string", () -> Rs2Keyboard.typeString("B")).start();
		string.thread.join(3_000);

		assertFalse("typeString holding Shift inside its own sequence must not wait on itself", string.thread.isAlive());
		assertNull("typeString threw", string.failure);
		List<KeyEvent> events = eventsOf(string);
		assertEquals("events: " + describeAll(events), 5, events.size());
		assertPressOrRelease(events.get(0), KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT);
		assertPressOrRelease(events.get(4), KeyEvent.KEY_RELEASED, KeyEvent.VK_SHIFT);
		assertEquals("string as typed", "B", typedBy(string));
		assertFalse("Shift left held after the string", Rs2Keyboard.isKeyHeld(KeyEvent.VK_SHIFT));
	}

	// K8, unchanged behavior

	@Test(timeout = 60_000)
	public void aStringCrossingAPauseStopsAfterTheInterruptedCharacter() throws Exception
	{
		CountDownLatch pauseBegan = new CountDownLatch(1);
		Caller string = new Caller("string", () -> Rs2Keyboard.typeString("ab"));
		AtomicBoolean fired = new AtomicBoolean();
		onEvent = r ->
		{
			if (r.thread == string.thread && r.event.getID() == KeyEvent.KEY_TYPED && fired.compareAndSet(false, true))
			{
				ScriptPauseController.setPaused(true);
				pauseBegan.countDown();
			}
		};
		string.start();
		assertTrue("the pause was never triggered", pauseBegan.await(5, TimeUnit.SECONDS));
		awaitFuture(ScriptPauseController.setPaused(true), "pause cleanup");
		awaitFuture(ScriptPauseController.setPaused(false), "resume");
		join(string);

		// Every thread's events, not only the string's: the pause cleanup may send the release of 'a'.
		List<KeyEvent> events = new ArrayList<>();
		for (Record r : snapshot())
		{
			events.add(r.event);
		}
		assertEquals("a typeString crossing a pause stops after the character the pause interrupted: "
			+ describeAll(events), 3, events.size());
		assertPressOrRelease(events.get(0), KeyEvent.KEY_PRESSED, KeyEvent.VK_A);
		assertEquals(KeyEvent.KEY_TYPED, events.get(1).getID());
		assertPressOrRelease(events.get(2), KeyEvent.KEY_RELEASED, KeyEvent.VK_A);
	}

	@Test(timeout = 60_000)
	public void aStringMeetingATakeoverStops() throws Exception
	{
		Caller string = new Caller("string", () -> Rs2Keyboard.typeString("ab"));
		AtomicBoolean fired = new AtomicBoolean();
		onEvent = r ->
		{
			if (r.thread == string.thread && r.event.getID() == KeyEvent.KEY_TYPED && fired.compareAndSet(false, true))
			{
				InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
			}
		};
		string.start();
		join(string);

		List<KeyEvent> events = eventsOf(string);
		assertEquals("a typeString meeting a takeover stops at that character: " + describeAll(events), 3, events.size());
		assertPressOrRelease(events.get(2), KeyEvent.KEY_RELEASED, KeyEvent.VK_A);
	}

	// K8, sequences waiting across a pause

	@Test(timeout = 60_000)
	public void aSequenceRequestedBeforeAPauseNeverDispatchesAfterIt() throws Exception
	{
		setTimeoutIfHookExists(PATIENT_TIMEOUT_MS);
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch release = releasable();
		Caller first = new Caller("first", () -> Rs2Keyboard.typeString("ab"));
		parkFirstEventOf(first, holding, release);
		first.start();
		assertTrue("first thread never dispatched its first key", holding.await(5, TimeUnit.SECONDS));

		Caller waiting = new Caller("waiting", () -> Rs2Keyboard.typeString("zz")).start();
		assertTrue("second thread never made its call", waiting.requested.await(5, TimeUnit.SECONDS));
		awaitParked(waiting.thread);

		// Neither future is awaited while the first thread is parked inside a dispatch: the cleanup may
		// wait for that dispatch to return. The pause begins when setPaused returns, before the cleanup.
		int pauseIndex = records.size();
		Future<Void> pause = ScriptPauseController.setPaused(true);
		Future<Void> resume = ScriptPauseController.setPaused(false);
		release.countDown();
		awaitFuture(pause, "pause cleanup");
		awaitFuture(resume, "resume");
		join(first, waiting);

		List<String> dispatched = new ArrayList<>();
		List<Record> snapshot = snapshot();
		for (int i = 0; i < snapshot.size(); i++)
		{
			Record r = snapshot.get(i);
			if (r.thread == waiting.thread)
			{
				dispatched.add((i < pauseIndex ? "before pause " : "after pause ") + describe(r.event));
			}
		}
		assertTrue("a typeString requested while another thread was mid-sequence and before a pause began must "
			+ "dispatch nothing: it has to wait for the keyboard, and the pause cancels it before it gets it. "
			+ "Dispatched: " + dispatched, dispatched.isEmpty());
		assertTrue("the first string kept typing after the pause: " + typedBy(first), typedBy(first).length() <= 1);
	}

	// K9

	@Test(timeout = 60_000)
	public void sequencesRequestedWhilePausedReturnAtOnce() throws Exception
	{
		assertReturnAtOnceWithoutInput(true);
	}

	@Test(timeout = 60_000)
	public void sequencesRequestedDuringATakeoverReturnAtOnce() throws Exception
	{
		assertReturnAtOnceWithoutInput(false);
	}

	// K10

	@Test(timeout = 60_000)
	public void aStringStopsTypingWhenItsThreadIsInterrupted() throws Exception
	{
		String word = "aBcd";
		AtomicBoolean flagAfterReturn = new AtomicBoolean();
		Caller string = new Caller("typeString(\"aBcd\")", () ->
		{
			Rs2Keyboard.typeString(word);
			flagAfterReturn.set(Thread.currentThread().isInterrupted());
		});
		// Interrupted while 'B' and the Shift around it are down, so the releases owed for keys already
		// down are part of what is checked. The listener runs on the typing thread, so the interrupt lands
		// at exactly this event.
		AtomicBoolean fired = new AtomicBoolean();
		onEvent = r ->
		{
			if (r.thread == string.thread && r.event.getID() == KeyEvent.KEY_TYPED && r.event.getKeyChar() == 'B'
				&& fired.compareAndSet(false, true))
			{
				Thread.currentThread().interrupt();
			}
		};
		string.start();
		join(string);
		assertTrue("the interrupt was never delivered", fired.get());

		List<KeyEvent> events = eventsOf(string);
		List<String> laterPresses = new ArrayList<>();
		for (KeyEvent e : events)
		{
			if ((e.getID() == KeyEvent.KEY_PRESSED && (e.getKeyCode() == KeyEvent.VK_C || e.getKeyCode() == KeyEvent.VK_D))
				|| (e.getID() == KeyEvent.KEY_TYPED && (e.getKeyChar() == 'c' || e.getKeyChar() == 'd')))
			{
				laterPresses.add(describe(e));
			}
		}
		assertTrue("an interrupted typeString kept typing the characters after the interrupt: " + laterPresses
			+ "; all events: " + describeAll(events), laterPresses.isEmpty());
		assertEquals("keys pressed but never released: " + describeAll(events), Collections.emptyList(), unreleased(events));
		assertFalse("Shift left held after the interrupted string", Rs2Keyboard.isKeyHeld(KeyEvent.VK_SHIFT));
		String typed = typedBy(string);
		assertTrue("typed text \"" + typed + "\" is not a prefix of \"" + word + "\"", word.startsWith(typed));
		assertTrue("the interrupt flag must still be set when typeString returns", flagAfterReturn.get());
	}

	@Test(timeout = 60_000)
	public void sequencesStartedOnAnInterruptedThreadDispatchNothing() throws Exception
	{
		List<Caller> calls = new ArrayList<>();
		List<AtomicBoolean> flags = new ArrayList<>();
		for (Caller entry : entryPoints())
		{
			callers.remove(entry);
			AtomicBoolean flag = new AtomicBoolean();
			flags.add(flag);
			Runnable call = entry.call;
			calls.add(new Caller(entry.label, () ->
			{
				Thread.currentThread().interrupt();
				call.run();
				flag.set(Thread.currentThread().isInterrupted());
			}));
		}
		for (Caller call : calls)
		{
			call.start();
		}
		joinAll(calls);

		assertTrue("calls started on an already interrupted thread must dispatch nothing, but " + dispatchedBy(calls),
			dispatchedBy(calls).isEmpty());
		for (int i = 0; i < calls.size(); i++)
		{
			assertTrue(calls.get(i).label + " cleared the interrupt flag", flags.get(i).get());
		}
	}

	// Scenarios

	/**
	 * Catches a sequence that waits for the keyboard before it checks whether input is allowed at all:
	 * with a holder parked mid-sequence and a long bound, such a wait cannot be missed.
	 */
	private void assertReturnAtOnceWithoutInput(boolean pause) throws Exception
	{
		setTimeoutIfHookExists(PATIENT_TIMEOUT_MS);
		String state = pause ? "while scripts are paused" : "while the human owns input";
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch release = releasable();
		Caller holder = new Caller("holder", () -> Rs2Keyboard.typeString("a"));
		parkFirstEventOf(holder, holding, release);
		holder.start();
		assertTrue("holder never dispatched its first key", holding.await(5, TimeUnit.SECONDS));

		List<Caller> calls;
		List<String> waited;
		Future<Void> cleanup = null;
		try
		{
			if (pause)
			{
				// Not awaited until the holder is released: the cleanup may wait for the holder's dispatch
				// in flight. The pause itself has begun once setPaused returns.
				cleanup = ScriptPauseController.setPaused(true);
			}
			else
			{
				InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
			}
			calls = startAll(entryPoints());
			waited = joinBy(calls, AT_ONCE_MS);
		}
		finally
		{
			release.countDown();
			if (cleanup != null)
			{
				awaitFuture(cleanup, "pause cleanup");
			}
			if (pause)
			{
				awaitFuture(ScriptPauseController.setPaused(false), "resume");
			}
			else
			{
				InputArbiter.resetForTest();
			}
		}
		join(holder);
		joinAll(calls);

		assertTrue("requested " + state + ", these waited for the keyboard another thread held instead of returning "
			+ "at once: " + waited, waited.isEmpty());
		assertTrue("requested " + state + ", calls must dispatch nothing, but " + dispatchedBy(calls),
			dispatchedBy(calls).isEmpty());
	}

	private Caller[] entryPoints()
	{
		return new Caller[]{
			new Caller("typeString(\"zz\")", () -> Rs2Keyboard.typeString("zz")),
			new Caller("keyPress('q')", () -> Rs2Keyboard.keyPress('q')),
			new Caller("keyPress(VK_1)", () -> Rs2Keyboard.keyPress(KeyEvent.VK_1)),
			new Caller("keyPress(VK_F1)", () -> Rs2Keyboard.keyPress(KeyEvent.VK_F1)),
			new Caller("enter()", Rs2Keyboard::enter)
		};
	}

	/**
	 * Starts {@code others} from inside {@code first}'s dispatch, so they are requested while it is
	 * provably mid-sequence, then gives them a bounded chance to dispatch before it moves on.
	 */
	private void startDuring(Caller first, Predicate<KeyEvent> trigger, Caller... others) throws Exception
	{
		AtomicBoolean fired = new AtomicBoolean();
		onEvent = r ->
		{
			if (r.thread == first.thread && trigger.test(r.event) && fired.compareAndSet(false, true))
			{
				for (Caller other : others)
				{
					other.start();
				}
				for (Caller other : others)
				{
					awaitQuietly(other.requested, 2_000);
				}
				awaitAnyDispatch(others, INTRUSION_WINDOW_MS);
			}
		};
		first.start();
		first.thread.join(PATIENT_TIMEOUT_MS + 5_000);
		assertTrue("the point where the other calls start was never reached", fired.get());
	}

	/**
	 * Blocks the caller at its first {@link Global#sleep} after its first KEY_TYPED: mid-sequence with a
	 * key down, but between events rather than inside a dispatch. Global.sleep asks the client whether
	 * it is on the client thread before sleeping, which is where the mock parks it.
	 */
	private void parkInSleepAfterFirstTyped(Caller caller, CountDownLatch holding, CountDownLatch release)
	{
		AtomicBoolean typed = new AtomicBoolean();
		AtomicBoolean fired = new AtomicBoolean();
		onEvent = r ->
		{
			if (r.thread == caller.thread && r.event.getID() == KeyEvent.KEY_TYPED)
			{
				typed.set(true);
			}
		};
		onClientThreadQuery = () ->
		{
			if (Thread.currentThread() == caller.thread && typed.get() && insideGlobalSleep()
				&& fired.compareAndSet(false, true))
			{
				holding.countDown();
				awaitQuietly(release, LISTENER_WAIT_MS);
			}
		};
	}

	private static boolean insideGlobalSleep()
	{
		for (StackTraceElement frame : Thread.currentThread().getStackTrace())
		{
			if (frame.getClassName().equals(Global.class.getName()) && frame.getMethodName().equals("sleep"))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Every KEY_PRESSED must be followed by exactly one KEY_RELEASED for that key, from any thread, and
	 * no KEY_RELEASED may come for a key that is not down.
	 */
	private static List<String> pairingViolations(List<Record> events)
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
				violations.add(i + ": " + KeyEvent.getKeyText(key) + " released while not down, from "
					+ events.get(i).thread.getName());
			}
		}
		for (Integer key : down)
		{
			violations.add(KeyEvent.getKeyText(key) + " never released");
		}
		return violations;
	}

	/** Blocks the caller inside its first dispatched event, so it holds the keyboard mid-sequence. */
	private void parkFirstEventOf(Caller caller, CountDownLatch holding, CountDownLatch release)
	{
		AtomicBoolean fired = new AtomicBoolean();
		onEvent = r ->
		{
			if (r.thread == caller.thread && fired.compareAndSet(false, true))
			{
				holding.countDown();
				awaitQuietly(release, LISTENER_WAIT_MS);
			}
		};
	}

	// Assertions and helpers

	/**
	 * Names every call whose events landed between the first and last event of another call. Empty
	 * when each call's events are contiguous in dispatch order.
	 */
	private String intrusions(Caller... involved)
	{
		List<Record> snapshot = snapshot();
		StringBuilder order = new StringBuilder();
		List<String> found = new ArrayList<>();
		for (Caller host : involved)
		{
			int first = -1;
			int last = -1;
			for (int i = 0; i < snapshot.size(); i++)
			{
				if (snapshot.get(i).thread == host.thread)
				{
					if (first < 0)
					{
						first = i;
					}
					last = i;
				}
			}
			for (Caller guest : involved)
			{
				if (guest == host)
				{
					continue;
				}
				for (int i = first + 1; i < last; i++)
				{
					if (snapshot.get(i).thread == guest.thread)
					{
						found.add(guest.label + " landed inside " + host.label);
						break;
					}
				}
			}
		}
		if (found.isEmpty())
		{
			return "";
		}
		for (Record r : snapshot)
		{
			for (int c = 0; c < involved.length; c++)
			{
				if (r.thread == involved[c].thread)
				{
					order.append((char) ('A' + c));
				}
			}
		}
		StringBuilder key = new StringBuilder();
		for (int c = 0; c < involved.length; c++)
		{
			key.append(c == 0 ? "" : ", ").append((char) ('A' + c)).append('=').append(involved[c].label);
		}
		return found + "; dispatch order (" + key + "): " + order;
	}

	private void assertEvents(Caller caller, int count, String typed)
	{
		assertEquals(caller.label + " events: " + describeAll(eventsOf(caller)), count, eventsOf(caller).size());
		assertEquals(caller.label + " as typed", typed, typedBy(caller));
	}

	private static void assertPressOrRelease(KeyEvent event, int id, int keyCode)
	{
		assertEquals("event id of " + describe(event), id, event.getID());
		assertEquals("key code of " + describe(event), keyCode, event.getKeyCode());
	}

	private static List<Caller> startAll(Caller... toStart)
	{
		List<Caller> started = new ArrayList<>();
		for (Caller caller : toStart)
		{
			started.add(caller.start());
		}
		return started;
	}

	/** Joins every caller against one shared deadline and returns the labels of those still running. */
	private static List<String> joinBy(List<Caller> toJoin, long millis) throws InterruptedException
	{
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
		List<String> running = new ArrayList<>();
		for (Caller caller : toJoin)
		{
			long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
			if (left > 0)
			{
				caller.thread.join(left);
			}
			if (caller.thread.isAlive())
			{
				running.add(caller.label);
			}
		}
		return running;
	}

	private void joinAll(List<Caller> toJoin) throws InterruptedException
	{
		join(toJoin.toArray(new Caller[0]));
	}

	private void join(Caller... toJoin) throws InterruptedException
	{
		for (Caller caller : toJoin)
		{
			caller.thread.join(PATIENT_TIMEOUT_MS + 5_000);
			assertFalse(caller.label + " never finished", caller.thread.isAlive());
			if (caller.failure != null)
			{
				throw new AssertionError(caller.label + " threw", caller.failure);
			}
		}
	}

	private List<String> dispatchedBy(List<Caller> calls)
	{
		List<String> out = new ArrayList<>();
		for (Caller caller : calls)
		{
			List<KeyEvent> events = eventsOf(caller);
			if (!events.isEmpty())
			{
				out.add(caller.label + " dispatched " + describeAll(events));
			}
		}
		return out;
	}

	private static void awaitAnyDispatch(Caller[] others, long millis)
	{
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
		while (System.nanoTime() < deadline)
		{
			for (Caller other : others)
			{
				if (other.dispatched)
				{
					return;
				}
			}
			try
			{
				Thread.sleep(2);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	/** Waits until the thread is blocked or waiting, which is where a caller queued for the keyboard sits. */
	private static void awaitParked(Thread thread) throws InterruptedException
	{
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline)
		{
			Thread.State state = thread.getState();
			if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING
				|| state == Thread.State.BLOCKED || state == Thread.State.TERMINATED)
			{
				return;
			}
			Thread.sleep(5);
		}
		fail(thread.getName() + " never parked");
	}

	private static void awaitFuture(Future<?> future, String what) throws Exception
	{
		try
		{
			future.get(5, TimeUnit.SECONDS);
		}
		catch (java.util.concurrent.TimeoutException e)
		{
			throw new AssertionError(what + " did not complete within 5s", e);
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
		for (Caller caller : callers)
		{
			if (caller.thread == r.thread)
			{
				caller.dispatched = true;
			}
		}
		onEvent.accept(r);
	}

	private List<Record> snapshot()
	{
		synchronized (records)
		{
			return new ArrayList<>(records);
		}
	}

	private List<KeyEvent> eventsOf(Caller caller)
	{
		List<KeyEvent> events = new ArrayList<>();
		for (Record r : snapshot())
		{
			if (r.thread == caller.thread)
			{
				events.add(r.event);
			}
		}
		return events;
	}

	private String typedBy(Caller caller)
	{
		return typedOn(caller.thread);
	}

	/** Key codes pressed in these events and never released after. */
	private static List<String> unreleased(List<KeyEvent> events)
	{
		List<Integer> down = new ArrayList<>();
		for (KeyEvent e : events)
		{
			if (e.getID() == KeyEvent.KEY_PRESSED && !down.contains(e.getKeyCode()))
			{
				down.add(e.getKeyCode());
			}
			else if (e.getID() == KeyEvent.KEY_RELEASED)
			{
				down.remove(Integer.valueOf(e.getKeyCode()));
			}
		}
		List<String> names = new ArrayList<>();
		for (Integer keyCode : down)
		{
			names.add(KeyEvent.getKeyText(keyCode));
		}
		return names;
	}

	private String typedOn(Thread thread)
	{
		StringBuilder typed = new StringBuilder();
		for (Record r : snapshot())
		{
			if (r.thread != thread)
			{
				continue;
			}
			KeyEvent e = r.event;
			if (e.getID() == KeyEvent.KEY_TYPED)
			{
				typed.append(e.getKeyChar());
			}
		}
		return typed.toString();
	}

	private static String describe(KeyEvent e)
	{
		String id = e.getID() == KeyEvent.KEY_PRESSED ? "PRESSED" : e.getID() == KeyEvent.KEY_RELEASED ? "RELEASED" : "TYPED";
		String ch = e.getKeyChar() == KeyEvent.CHAR_UNDEFINED ? "-" : e.getKeyChar() == '\n' ? "\\n" : String.valueOf(e.getKeyChar());
		return id + "(" + KeyEvent.getKeyText(e.getKeyCode()) + "," + ch + ",mods=" + e.getModifiersEx() + ")";
	}

	private static List<String> describeAll(List<KeyEvent> events)
	{
		List<String> out = new ArrayList<>();
		for (KeyEvent e : events)
		{
			out.add(describe(e));
		}
		return out;
	}

	// The hook is called reflectively so this class compiles before it exists.

	private static Method findTimeoutHook()
	{
		try
		{
			Method hook = Rs2Keyboard.class.getDeclaredMethod(TIMEOUT_HOOK, long.class);
			hook.setAccessible(true);
			return hook;
		}
		catch (NoSuchMethodException e)
		{
			return null;
		}
	}

	private static Method requireTimeoutHook()
	{
		Method hook = findTimeoutHook();
		assertNotNull("Rs2Keyboard." + TIMEOUT_HOOK + "(long) is missing: the wait for the keyboard has no bound "
			+ "a test can set, mirroring InputLoop.setLockTimeoutForTest", hook);
		assertTrue("Rs2Keyboard." + TIMEOUT_HOOK + "(long) must be static", Modifier.isStatic(hook.getModifiers()));
		return hook;
	}

	private static void setTimeoutIfHookExists(long millis) throws Exception
	{
		Method hook = findTimeoutHook();
		if (hook != null)
		{
			hook.invoke(null, millis);
		}
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
