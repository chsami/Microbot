package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dispatches synthetic AWT mouse events on the game canvas, taking canvas coordinates.
 *
 * <p>Does not decide whether an emit is allowed; that is {@link InputArbiter}'s job, upstream.
 */
public final class AwtEmitter
{
	/** Exit coordinates can land a pixel outside the component. */
	private static final int EDGE_SLOP = 2;

	// Re-entry spread. Guesses, not a model of a person; the point is only that entry stops being
	// a function of exit. Wider after a covered exit, where nothing anchors the pointer.
	private static final int EDGE_SIGMA_PX = 35;
	private static final int COVERED_SIGMA_PX = 120;
	private static final double COVERED_EDGE_RETURN_CHANCE = 0.35;

	private static final Set<Integer> HELD_BUTTONS = ConcurrentHashMap.newKeySet();
	private static final Object HELD_BUTTONS_LOCK = new Object();

	private AwtEmitter()
	{
	}

	public static void moved(int canvasX, int canvasY)
	{
		if (!InputLoop.isInputAllowed())
		{
			return;
		}
		// Here, not at the callers: every motion funnels through this method.
		if (exitIfOutside(canvasX, canvasY))
		{
			return;
		}
		Canvas canvas = canvas();
		if (canvas == null)
		{
			return;
		}
		enterIfOutside(canvas, canvasX, canvasY);
		Point component = StretchMapper.toComponent(canvasX, canvasY);
		recordPosition(canvasX, canvasY);
		int held = heldMask();
		int id = held == 0 ? MouseEvent.MOUSE_MOVED : MouseEvent.MOUSE_DRAGGED;
		dispatch(canvas, new MouseEvent(canvas, id, System.currentTimeMillis(), held,
			component.getX(), component.getY(), 0, false));
	}

	public static void pressed(int canvasX, int canvasY, int button)
	{
		button(MouseEvent.MOUSE_PRESSED, canvasX, canvasY, button);
	}

	public static void released(int canvasX, int canvasY, int button)
	{
		button(MouseEvent.MOUSE_RELEASED, canvasX, canvasY, button);
	}

	public static void clicked(int canvasX, int canvasY, int button)
	{
		button(MouseEvent.MOUSE_CLICKED, canvasX, canvasY, button);
	}

	public static void wheel(int canvasX, int canvasY, int wheelRotation, int unitsToScroll)
	{
		if (!InputLoop.isInputAllowed())
		{
			return;
		}
		Canvas canvas = canvas();
		if (canvas == null)
		{
			return;
		}
		enterIfOutside(canvas, canvasX, canvasY);
		Point component = StretchMapper.toComponent(canvasX, canvasY);
		recordPosition(canvasX, canvasY);
		dispatch(canvas, new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
			component.getX(), component.getY(), 0, false, 0, unitsToScroll, wheelRotation));
	}

	private static void button(int id, int canvasX, int canvasY, int button)
	{
		if (id != MouseEvent.MOUSE_RELEASED && !InputLoop.isInputAllowed())
		{
			return;
		}
		Canvas canvas = canvas();
		if (canvas == null)
		{
			return;
		}
		synchronized (HELD_BUTTONS_LOCK)
		{
			enterIfOutside(canvas, canvasX, canvasY);
			Point component = StretchMapper.toComponent(canvasX, canvasY);
			recordPosition(canvasX, canvasY);
			int modifiers = heldMask();
			if (id == MouseEvent.MOUSE_PRESSED)
			{
				modifiers |= InputEvent.getMaskForButton(button);
			}
			else if (id == MouseEvent.MOUSE_RELEASED)
			{
				modifiers &= ~InputEvent.getMaskForButton(button);
			}
			boolean delivered = dispatch(canvas, new MouseEvent(canvas, id, System.currentTimeMillis(), modifiers,
				component.getX(), component.getY(), 1, false, button));
			if (delivered && id == MouseEvent.MOUSE_PRESSED)
			{
				HELD_BUTTONS.add(button);
			}
			else if (delivered && id == MouseEvent.MOUSE_RELEASED)
			{
				HELD_BUTTONS.remove(button);
			}
		}
	}

	public static int heldMask()
	{
		int mask = 0;
		for (Integer button : HELD_BUTTONS)
		{
			mask |= InputEvent.getMaskForButton(button);
		}
		return mask;
	}

	static void resetHeldButtonsForTest()
	{
		synchronized (HELD_BUTTONS_LOCK)
		{
			HELD_BUTTONS.clear();
		}
	}

	/** Releases synthetic buttons after the pause has fenced off new input. */
	public static void releaseHeldButtons()
	{
		synchronized (HELD_BUTTONS_LOCK)
		{
			Point at = PointerState.get();
			for (Integer button : HELD_BUTTONS.toArray(new Integer[0]))
			{
				released(at.getX(), at.getY(), button);
			}
			if (!HELD_BUTTONS.isEmpty())
			{
				throw new IllegalStateException("Could not release all synthetic mouse buttons");
			}
		}
	}

	/**
	 * Announces the pointer's return before any other event, when the client believes none is over
	 * the canvas. A real MOUSE_EXITED leaves it tracking (-1,-1), so a bare MOVED or PRESSED after
	 * that delivers motion for a pointer it does not think exists.
	 */
	private static void enterIfOutside(Canvas canvas, int fallbackX, int fallbackY)
	{
		if (!PointerState.isOutside())
		{
			return;
		}
		// Cleared first: the ENTERED below must not re-enter this method.
		PointerState.markInside();

		Point at = PointerState.get();
		int exitX = at.getX() < 0 ? fallbackX : at.getX();
		int exitY = at.getY() < 0 ? fallbackY : at.getY();

		Point entry = reentryPoint(exitX, exitY, canvasWidth(), canvasHeight(), ThreadLocalRandom.current());
		Point component = StretchMapper.toComponent(entry.getX(), entry.getY());
		// Recorded too, or the next click presses where the pointer never travelled.
		recordPosition(entry.getX(), entry.getY());
		dispatch(canvas, new MouseEvent(canvas, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0,
			component.getX(), component.getY(), 0, false));
	}

	/**
	 * Mirror of {@link #enterIfOutside}. Off-canvas is a real destination: antiban parks the
	 * cursor there.
	 *
	 * @return true when the point is off the canvas, so there is no motion left to send
	 */
	private static boolean exitIfOutside(int canvasX, int canvasY)
	{
		int width = canvasWidth();
		int height = canvasHeight();
		// Unknown size: call everything inside, rather than silencing all motion.
		if (width <= 0 || height <= 0)
		{
			return false;
		}
		if (canvasX >= 0 && canvasX < width && canvasY >= 0 && canvasY < height)
		{
			return false;
		}
		if (PointerState.isOutside())
		{
			return true;
		}
		// Real events win, same rule recordPosition applies: a synthetic exit must not move the
		// human's pointer off the canvas underneath them.
		if (InputArbiter.isHuman())
		{
			return true;
		}

		Canvas canvas = canvas();
		if (canvas == null)
		{
			return false;
		}
		// Through recordPosition, so the bot reference moves out with the pointer. Left behind, the
		// motion threshold would be measured from a point the bot has already left.
		recordPosition(canvasX, canvasY);
		PointerState.markOutside();
		Point component = StretchMapper.toComponent(canvasX, canvasY);
		dispatch(canvas, new MouseEvent(canvas, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0,
			component.getX(), component.getY(), 0, false));
		return true;
	}

	/**
	 * Where the pointer comes back in, given where it went out. An edge exit returns along that
	 * edge; a mid-canvas exit means a window covered the client and the position is unknown, so
	 * the return is drawn from inside or across an edge. The exact exit point stays reachable:
	 * that is the user who never touched the mouse.
	 *
	 * <p>Randomness is a parameter so tests can exercise the distribution.
	 */
	static Point reentryPoint(int exitX, int exitY, int width, int height, Random random)
	{
		if (width <= 0 || height <= 0)
		{
			return new Point(exitX, exitY);
		}

		boolean onVerticalEdge = exitX <= EDGE_SLOP || exitX >= width - 1 - EDGE_SLOP;
		boolean onHorizontalEdge = exitY <= EDGE_SLOP || exitY >= height - 1 - EDGE_SLOP;

		if (onVerticalEdge)
		{
			return new Point(clamp(exitX, width), clamp(gaussian(random, exitY, EDGE_SIGMA_PX), height));
		}
		if (onHorizontalEdge)
		{
			return new Point(clamp(gaussian(random, exitX, EDGE_SIGMA_PX), width), clamp(exitY, height));
		}

		if (random.nextDouble() < COVERED_EDGE_RETURN_CHANCE)
		{
			return randomEdgePoint(width, height, random);
		}
		return new Point(clamp(gaussian(random, exitX, COVERED_SIGMA_PX), width),
			clamp(gaussian(random, exitY, COVERED_SIGMA_PX), height));
	}

	private static Point randomEdgePoint(int width, int height, Random random)
	{
		switch (random.nextInt(4))
		{
			case 0:
				return new Point(0, random.nextInt(height));
			case 1:
				return new Point(width - 1, random.nextInt(height));
			case 2:
				return new Point(random.nextInt(width), 0);
			default:
				return new Point(random.nextInt(width), height - 1);
		}
	}

	private static int gaussian(Random random, int mean, int sigma)
	{
		return (int) Math.round(mean + random.nextGaussian() * sigma);
	}

	private static int clamp(int value, int size)
	{
		return Math.max(0, Math.min(size - 1, value));
	}

	private static int canvasWidth()
	{
		try
		{
			return Microbot.getClient() == null ? 0 : Microbot.getClient().getCanvasWidth();
		}
		catch (Exception ex)
		{
			return 0;
		}
	}

	private static int canvasHeight()
	{
		try
		{
			return Microbot.getClient() == null ? 0 : Microbot.getClient().getCanvasHeight();
		}
		catch (Exception ex)
		{
			return 0;
		}
	}

	/**
	 * Real events win: while the human owns input a synthetic emit must not move the recorded
	 * position. The abort path still emits its RELEASED, it just does not drag the position along.
	 */
	private static void recordPosition(int canvasX, int canvasY)
	{
		if (InputArbiter.isHuman())
		{
			return;
		}
		PointerState.setFromBot(canvasX, canvasY);
	}

	private static Canvas canvas()
	{
		try
		{
			return Microbot.getClient() == null ? null : Microbot.getClient().getCanvas();
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	// Jagex's MOUSE_PRESSED listener calls canvas.requestFocus(), stealing OS focus from whatever
	// the user is typing in. Non-focusable for the dispatch neuters it; mouse delivery ignores
	// focusable state. Skipped when the canvas already owns focus, where setFocusable(false) would
	// hand focus to the parent instead, which is the thing being prevented.
	private static boolean dispatch(Canvas canvas, AWTEvent event)
	{
		if (ScriptPauseController.isPaused()
			&& (!(event instanceof MouseEvent) || ((MouseEvent) event).getID() != MouseEvent.MOUSE_RELEASED))
		{
			return false;
		}
		boolean canvasIsFocused = canvas.isFocusOwner();
		boolean wasFocusable = canvas.isFocusable();
		boolean shouldGuard = wasFocusable && !canvasIsFocused;
		if (shouldGuard)
		{
			canvas.setFocusable(false);
		}
		BotEventGuard.begin();
		try
		{
			canvas.dispatchEvent(event);
			return true;
		}
		finally
		{
			BotEventGuard.end();
			if (shouldGuard)
			{
				canvas.setFocusable(true);
			}
		}
	}
}
