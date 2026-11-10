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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The canvas's focusable flag has two writers: each synthetic mouse dispatch clears it for its own
 * length and then restores what it read beforehand, and the user's input setting writes it from
 * the EDT. A setting written while a dispatch is in flight is undone by that restore. The contract
 * routes the setting through {@code AwtEmitter.setCanvasFocusable(boolean)}, the same owner that runs
 * the guard.
 *
 * <p>Dispatches are held inside a canvas listener on their own thread, so every interleaving here is
 * fixed by the test rather than by the scheduler.
 */
public class CanvasFocusableOwnerTest
{
	private static final String METHOD_NAME = "setCanvasFocusable";
	private static final long WAIT_S = 5;

	private Canvas canvas;
	private Object previousClient;

	/** Signalled once per synthetic dispatch that has reached the listener. */
	private final Semaphore entered = new Semaphore(0);
	/** Per dispatching thread: counted down to let that thread's dispatch return. */
	private final Map<Thread, CountDownLatch> holds = new ConcurrentHashMap<>();
	private final List<Thread> dispatchers = new ArrayList<>();

	@Before
	public void before() throws Exception
	{
		assertFalse("fixture: a previous test left scripts paused, which drops every synthetic dispatch",
			ScriptPauseController.isPaused());
		canvas = new Canvas();
		canvas.addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				holdSyntheticDispatch();
			}
		});
		canvas.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				holdSyntheticDispatch();
			}
		});
		Client client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenReturn(false);
		when(client.isStretchedEnabled()).thenReturn(false);
		previousClient = swapStatic("client", client);

		PointerState.reset();
		InputArbiter.resetForTest();
		AwtEmitter.resetHeldButtonsForTest();
		assertFalse("fixture: the canvas must not own focus, or the guard is skipped by design",
			canvas.isFocusOwner());
	}

	@After
	public void after() throws Exception
	{
		for (CountDownLatch hold : holds.values())
		{
			hold.countDown();
		}
		for (Thread dispatcher : dispatchers)
		{
			dispatcher.join(WAIT_S * 1_000);
		}
		// The owner's setting is static once it exists. Put it back to the Component default, which
		// is also the input-enabled state, while this test's canvas is still the client's, so no
		// later test inherits a non-focusable setting.
		Method method = findMethod();
		if (method != null)
		{
			invoke(method, true);
		}
		swapStatic("client", previousClient);
		PointerState.reset();
		InputArbiter.resetForTest();
		AwtEmitter.resetHeldButtonsForTest();
		while (BotEventGuard.isSynthetic())
		{
			BotEventGuard.end();
		}
	}

	// FO1

	@Test(timeout = 20_000)
	public void theSettingReachesTheCanvasWithNoDispatchInFlight()
	{
		Method method = requireMethod();

		invoke(method, false);
		assertFalse("the user's last setting was not focusable", canvas.isFocusable());

		invoke(method, true);
		assertTrue("the user's last setting was focusable", canvas.isFocusable());
	}

	@Test(timeout = 20_000)
	public void aSettingOfNotFocusableWrittenDuringADispatchSurvivesItsRestore() throws Exception
	{
		Method method = requireMethod();
		invoke(method, true);
		Held dispatch = startHeldDispatch();

		invoke(method, false);
		finish(dispatch);

		assertFalse("the user disabled input while a synthetic dispatch was in flight, and the dispatch's "
			+ "restore of the value it read beforehand made the canvas focusable again", canvas.isFocusable());
	}

	@Test(timeout = 20_000)
	public void aSettingOfFocusableWrittenDuringAnUnguardedDispatchHolds() throws Exception
	{
		Method method = requireMethod();
		invoke(method, false);
		Held dispatch = startHeldDispatch();

		invoke(method, true);
		finish(dispatch);

		assertTrue("the user's last setting was focusable", canvas.isFocusable());
	}

	@Test(timeout = 20_000)
	public void theLastSettingWinsAcrossOverlappingDispatches() throws Exception
	{
		Method method = requireMethod();
		invoke(method, true);
		Held first = startHeldDispatch();
		Held second = startHeldDispatch();

		invoke(method, false);
		finish(first);
		finish(second);

		assertFalse("the user disabled input while two synthetic dispatches overlapped, and a restore "
			+ "made the canvas focusable again", canvas.isFocusable());
	}

	@Test(timeout = 20_000)
	public void theLastOfSeveralSettingsWrittenDuringADispatchWins() throws Exception
	{
		Method method = requireMethod();
		invoke(method, true);
		Held dispatch = startHeldDispatch();

		invoke(method, false);
		invoke(method, true);
		invoke(method, false);
		finish(dispatch);

		assertFalse("the user's last setting, written mid-dispatch, was not focusable", canvas.isFocusable());
	}

	// FO2

	@Test(timeout = 20_000)
	public void theSettingDoesNotWaitForASyntheticMoveInProgress() throws Exception
	{
		Method method = requireMethod();
		invoke(method, true);

		assertSettingReturnsWhileHeld(method, startHeldDispatch(() -> AwtEmitter.moved(100, 100)));
	}

	@Test(timeout = 20_000)
	public void theSettingDoesNotWaitForASyntheticPressInProgress() throws Exception
	{
		Method method = requireMethod();
		invoke(method, true);

		// A press dispatches under the held-buttons lock, unlike a move, so this catches a setting
		// that waits on that lock.
		assertSettingReturnsWhileHeld(method,
			startHeldDispatch(() -> AwtEmitter.pressed(100, 100, MouseEvent.BUTTON1)));
	}

	// FO3, unchanged behavior

	@Test(timeout = 20_000)
	public void aFocusableCanvasIsNotFocusableDuringADispatchAndFocusableAfter() throws Exception
	{
		applySetting(true);
		assertTrue(canvas.isFocusable());
		Held dispatch = startHeldDispatch();

		assertFalse("during a synthetic dispatch the game's press handler must not be able to take focus",
			canvas.isFocusable());
		finish(dispatch);

		assertTrue("the dispatch must hand the canvas back focusable", canvas.isFocusable());
	}

	@Test(timeout = 20_000)
	public void aNotFocusableCanvasStaysNotFocusableBeforeDuringAndAfter() throws Exception
	{
		applySetting(false);
		assertFalse("before", canvas.isFocusable());
		Held dispatch = startHeldDispatch();

		assertFalse("during", canvas.isFocusable());
		finish(dispatch);

		assertFalse("a dispatch must never make a canvas focusable that the setting says is not",
			canvas.isFocusable());
	}

	// FO4

	@Test(timeout = 20_000)
	public void overlappingDispatchesKeepTheCanvasNotFocusableUntilTheLastReturnsFirstStartedFirstDone()
		throws Exception
	{
		applySetting(true);
		Held first = startHeldDispatch();
		Held second = startHeldDispatch();

		finish(first);

		assertFalse("a synthetic dispatch is still in flight, but the first one's restore made the canvas "
			+ "focusable underneath it", canvas.isFocusable());
		finish(second);
		assertTrue("the last dispatch has returned and the setting is focusable", canvas.isFocusable());
	}

	@Test(timeout = 20_000)
	public void overlappingDispatchesKeepTheCanvasNotFocusableUntilTheLastReturnsLastStartedFirstDone()
		throws Exception
	{
		applySetting(true);
		Held first = startHeldDispatch();
		Held second = startHeldDispatch();

		finish(second);

		assertFalse("a synthetic dispatch is still in flight", canvas.isFocusable());
		finish(first);
		assertTrue("the last dispatch has returned and the setting is focusable", canvas.isFocusable());
	}

	/**
	 * Starts a synthetic dispatch on a thread of its own and returns once it is inside the canvas
	 * listener, where it stays until {@link #finish} releases it.
	 */
	/** Runs on the dispatching thread, inside the synthetic dispatch. */
	private void holdSyntheticDispatch()
	{
		if (!BotEventGuard.isSynthetic())
		{
			return;
		}
		CountDownLatch hold = holds.get(Thread.currentThread());
		entered.release();
		if (hold == null)
		{
			return;
		}
		try
		{
			hold.await(WAIT_S * 4, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex)
		{
			Thread.currentThread().interrupt();
		}
	}

	private Held startHeldDispatch() throws InterruptedException
	{
		return startHeldDispatch(() -> AwtEmitter.moved(100, 100));
	}

	private Held startHeldDispatch(Runnable emit) throws InterruptedException
	{
		CountDownLatch hold = new CountDownLatch(1);
		Thread dispatcher = new Thread(emit, "synthetic-dispatch-" + dispatchers.size());
		dispatcher.setDaemon(true);
		// Registered before start, so the listener finds its latch on the first look.
		holds.put(dispatcher, hold);
		dispatchers.add(dispatcher);
		dispatcher.start();
		assertTrue("fixture: the synthetic dispatch never reached the canvas listener",
			entered.tryAcquire(WAIT_S, TimeUnit.SECONDS));
		return new Held(dispatcher, hold);
	}

	private static void assertSettingReturnsWhileHeld(Method method, Held dispatch) throws InterruptedException
	{
		CountDownLatch returned = new CountDownLatch(1);
		Thread setter = new Thread(() -> {
			invoke(method, false);
			returned.countDown();
		}, "focusable-setter");
		setter.setDaemon(true);
		setter.start();

		// The test holds the dispatch until finish() below, so it cannot return before this check.
		boolean returnedWhileHeld = returned.await(WAIT_S, TimeUnit.SECONDS);
		assertTrue("fixture: the dispatch must still be held inside its listener", dispatch.thread.isAlive());
		assertTrue("the user's setting waited for a synthetic dispatch held in a canvas listener; called on "
			+ "the EDT it would freeze the UI for as long as the game's handler runs", returnedWhileHeld);
		finish(dispatch);
		setter.join(WAIT_S * 1_000);
	}

	/** Lets one held dispatch return, and waits until it has. */
	private static void finish(Held held) throws InterruptedException
	{
		held.hold.countDown();
		held.thread.join(WAIT_S * 1_000);
		assertFalse("fixture: the synthetic dispatch did not return", held.thread.isAlive());
	}

	private static final class Held
	{
		private final Thread thread;
		private final CountDownLatch hold;

		private Held(Thread thread, CountDownLatch hold)
		{
			this.thread = thread;
			this.hold = hold;
		}
	}

	/**
	 * The setting through its owner where one exists, otherwise the raw write {@code InputSelector}
	 * makes today, so behavior the contract keeps unchanged is checked on the code as it is.
	 */
	private void applySetting(boolean focusable)
	{
		Method method = findMethod();
		if (method == null)
		{
			canvas.setFocusable(focusable);
		}
		else
		{
			invoke(method, focusable);
		}
	}

	private static Method requireMethod()
	{
		Method method = findMethod();
		if (method == null)
		{
			fail("AwtEmitter has no public static " + METHOD_NAME + "(boolean): the user's input setting has "
				+ "no single way to reach the canvas's focusable flag, so a synthetic dispatch restoring the "
				+ "value it read beforehand can undo the user's write");
		}
		return method;
	}

	private static Method findMethod()
	{
		try
		{
			Method method = AwtEmitter.class.getMethod(METHOD_NAME, boolean.class);
			return Modifier.isStatic(method.getModifiers()) ? method : null;
		}
		catch (NoSuchMethodException ex)
		{
			return null;
		}
	}

	private static void invoke(Method method, boolean focusable)
	{
		try
		{
			method.invoke(null, focusable);
		}
		catch (IllegalAccessException ex)
		{
			throw new AssertionError(METHOD_NAME + " must be public", ex);
		}
		catch (InvocationTargetException ex)
		{
			Throwable cause = ex.getCause();
			if (cause instanceof RuntimeException)
			{
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error)
			{
				throw (Error) cause;
			}
			throw new AssertionError(cause);
		}
	}

	private static Object swapStatic(String name, Object value) throws Exception
	{
		java.lang.reflect.Field field = Microbot.class.getDeclaredField(name);
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, value);
		return previous;
	}
}
