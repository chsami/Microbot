package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * moveOffScreen is a trajectory to the edge and then one move across it. A trajectory ended by an
 * interrupt must not be followed by that crossing: it would send the game an EXITED from a thread
 * that was told to stop.
 */
public class MoveOffScreenInterruptTest
{
	private static final int CANVAS_W = 765;
	private static final int CANVAS_H = 503;
	/** Far enough into a trajectory from the canvas centre that steps remain after it. */
	private static final int INTERRUPT_AT_MOVE = 3;

	private final List<Integer> afterInterrupt = new CopyOnWriteArrayList<>();
	private final AtomicInteger moves = new AtomicInteger();
	private volatile boolean interrupted;

	private Object previousClient;
	private Object previousNaturalMouse;
	private ActivityIntensity previousIntensity;
	private NaturalMouse naturalMouse;

	@Before
	public void before() throws Exception
	{
		Canvas canvas = new Canvas();
		MouseAdapter recorder = new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				record(e);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				record(e);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				record(e);
			}
		};
		canvas.addMouseListener(recorder);
		canvas.addMouseMotionListener(recorder);

		Client client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenReturn(false);
		when(client.isStretchedEnabled()).thenReturn(false);
		when(client.getCanvasWidth()).thenReturn(CANVAS_W);
		when(client.getCanvasHeight()).thenReturn(CANVAS_H);

		naturalMouse = new NaturalMouse();
		// moveOffScreen reads the injected client, not Microbot.getClient().
		Field injected = NaturalMouse.class.getDeclaredField("client");
		injected.setAccessible(true);
		injected.set(naturalMouse, client);

		previousClient = swapStatic("client", client);
		previousNaturalMouse = swapStatic("naturalMouse", naturalMouse);
		previousIntensity = Rs2Antiban.getActivityIntensity();
		Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);

		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		PointerState.reset();
		InputArbiter.resetForTest();
		AwtEmitter.resetHeldButtonsForTest();
		InputLoop.setLockTimeoutForTest(5_000L);
	}

	@After
	public void after() throws Exception
	{
		swapStatic("client", previousClient);
		swapStatic("naturalMouse", previousNaturalMouse);
		Rs2Antiban.setActivityIntensity(previousIntensity);
		PointerState.reset();
		InputArbiter.resetForTest();
		AwtEmitter.resetHeldButtonsForTest();
		while (BotEventGuard.isSynthetic())
		{
			BotEventGuard.end();
		}
	}

	@Test(timeout = 15_000)
	public void anInterruptedMoveOffScreenNeverCrossesTheEdge() throws Exception
	{
		PointerState.setFromBot(CANVAS_W / 2, CANVAS_H / 2);

		Thread mover = new Thread(naturalMouse::moveOffScreen, "move-off-screen");
		mover.setDaemon(true);
		mover.start();
		mover.join(10_000L);

		assertFalse("moveOffScreen did not return after its thread was interrupted", mover.isAlive());
		assertTrue("the trajectory never reached the interrupt point, so the scenario did not run", interrupted);
		assertFalse("an interrupted moveOffScreen still crossed off the canvas: " + afterInterrupt,
			afterInterrupt.contains(MouseEvent.MOUSE_EXITED));
		assertEquals("an interrupted moveOffScreen kept dispatching: " + afterInterrupt, 0, afterInterrupt.size());
	}

	private void record(MouseEvent event)
	{
		if (interrupted)
		{
			afterInterrupt.add(event.getID());
			return;
		}
		if (event.getID() == MouseEvent.MOUSE_MOVED && moves.incrementAndGet() == INTERRUPT_AT_MOVE)
		{
			interrupted = true;
			Thread.currentThread().interrupt();
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
