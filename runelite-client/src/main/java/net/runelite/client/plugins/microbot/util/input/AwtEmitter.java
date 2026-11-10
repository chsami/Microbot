package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dispatches synthetic AWT mouse events on the game canvas, taking canvas coordinates.
 *
 * <p>Whether an event may go out is decided once, at its dispatch, by {@link InputLoop#mayEmit}, and
 * the package-private try methods report that decision so a gesture can end on it. A RELEASED is
 * never refused.
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

	/**
	 * Every synthetic button the game sees down, with the press that owns it: the gesture's
	 * {@link InputLoop.Emit} that pressed it, or {@link #NO_GESTURE} for a direct press. Written only
	 * under {@link #HELD_BUTTONS_LOCK}, together with the dispatch that changed it.
	 */
	private static final Map<Integer, Object> HELD_BUTTONS = new ConcurrentHashMap<>();
	private static final Object HELD_BUTTONS_LOCK = new Object();
	private static final Object NO_GESTURE = new Object();

	private AwtEmitter()
	{
	}

	/*
	 * Called inside a gesture, a refused move, press, click or wheel ends that gesture, as it would
	 * through the gesture's Emit. Bug fix: a NaturalMouse step comes through here, and a refused step
	 * used to be dropped, leaving NaturalMouse to look at the input state again afterwards; by then a
	 * takeover could have lapsed and the gesture went on to jump to its target and press.
	 */

	public static void moved(int canvasX, int canvasY)
	{
		endGestureUnless(tryMove(canvasX, canvasY));
	}

	public static void pressed(int canvasX, int canvasY, int button)
	{
		endGestureUnless(tryPress(canvasX, canvasY, button, null));
	}

	public static void released(int canvasX, int canvasY, int button)
	{
		button(MouseEvent.MOUSE_RELEASED, canvasX, canvasY, button, InputLoop.emitGeneration(), null);
	}

	public static void clicked(int canvasX, int canvasY, int button)
	{
		endGestureUnless(tryClick(canvasX, canvasY, button));
	}

	public static void wheel(int canvasX, int canvasY, int wheelRotation, int unitsToScroll)
	{
		endGestureUnless(tryWheel(canvasX, canvasY, wheelRotation, unitsToScroll));
	}

	private static void endGestureUnless(boolean accepted)
	{
		if (!accepted)
		{
			InputLoop.endGestureOnRefusal();
		}
	}

	/*
	 * The try methods return false when their event was refused, by InputLoop.mayEmit or for want
	 * of a canvas, and true when it went out or, for a move off the canvas, nothing was due. Each
	 * takes its pause generation on entry: the gesture's inside one, the current one outside, so an
	 * event never goes out once a pause has begun after its gesture or its call started.
	 */

	static boolean tryMove(int canvasX, int canvasY)
	{
		long generation = InputLoop.emitGeneration();
		// Early out only, so a move already refused leaves the tracked pointer alone. It is the
		// predicate the dispatch applies, and a refusal here reaches the caller exactly as one there
		// does. For a pause the two cannot differ, since the generation only ever moves on.
		if (!InputLoop.mayEmit(generation))
		{
			return false;
		}
		// Here, not at the callers: every motion funnels through this method.
		if (isOffCanvas(canvasX, canvasY))
		{
			return exitCanvas(canvasX, canvasY, generation);
		}
		Canvas canvas = canvas();
		if (canvas == null)
		{
			return false;
		}
		if (!enterIfOutside(canvas, canvasX, canvasY, generation))
		{
			return false;
		}
		Point component = StretchMapper.toComponent(canvasX, canvasY);
		recordPosition(canvasX, canvasY);
		int held = heldMask();
		int id = held == 0 ? MouseEvent.MOUSE_MOVED : MouseEvent.MOUSE_DRAGGED;
		return dispatch(canvas, new MouseEvent(canvas, id, System.currentTimeMillis(), held,
			component.getX(), component.getY(), 0, false), generation);
	}

	/**
	 * A RELEASED is owed only for a press this returned true for.
	 *
	 * @param gesture the gesture the press belongs to, or null for a direct press
	 */
	static boolean tryPress(int canvasX, int canvasY, int button, InputLoop.Emit gesture)
	{
		return button(MouseEvent.MOUSE_PRESSED, canvasX, canvasY, button, InputLoop.emitGeneration(), gesture);
	}

	static boolean tryClick(int canvasX, int canvasY, int button)
	{
		return button(MouseEvent.MOUSE_CLICKED, canvasX, canvasY, button, InputLoop.emitGeneration(), null);
	}

	static boolean tryWheel(int canvasX, int canvasY, int wheelRotation, int unitsToScroll)
	{
		long generation = InputLoop.emitGeneration();
		// Early out only, as in tryMove.
		if (!InputLoop.mayEmit(generation))
		{
			return false;
		}
		Canvas canvas = canvas();
		if (canvas == null)
		{
			return false;
		}
		if (!enterIfOutside(canvas, canvasX, canvasY, generation))
		{
			return false;
		}
		Point component = StretchMapper.toComponent(canvasX, canvasY);
		recordPosition(canvasX, canvasY);
		return dispatch(canvas, new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
			component.getX(), component.getY(), 0, false, 0, unitsToScroll, wheelRotation), generation);
	}

	/**
	 * A gesture's release, sent only while this gesture's own press of the button is down. Bug fix:
	 * this used to ask only whether the button was down, so once the pause cleanup had released the
	 * gesture's press, a later press of the same button by another caller was released as if it were
	 * the gesture's, and a gesture that never pressed released whatever another caller held. Checked
	 * under the lock the pause cleanup sweeps under, so a press comes up once whichever of the two
	 * gets there first.
	 */
	static void releaseOwned(int canvasX, int canvasY, int button, InputLoop.Emit gesture)
	{
		// Outside the lock, for the reason given in button().
		Canvas canvas = canvas();
		synchronized (HELD_BUTTONS_LOCK)
		{
			if (HELD_BUTTONS.get(button) == gesture)
			{
				buttonEvent(canvas, MouseEvent.MOUSE_RELEASED, canvasX, canvasY, button, InputLoop.emitGeneration(),
					gesture);
			}
		}
	}

	/** The end-of-gesture sweep: releases what the gesture pressed that is still its own and down. */
	static void releaseAllOwned(InputLoop.Emit gesture)
	{
		Canvas canvas = canvas();
		synchronized (HELD_BUTTONS_LOCK)
		{
			Point at = PointerState.get();
			for (Map.Entry<Integer, Object> held : HELD_BUTTONS.entrySet())
			{
				if (held.getValue() == gesture)
				{
					buttonEvent(canvas, MouseEvent.MOUSE_RELEASED, at.getX(), at.getY(), held.getKey(),
						InputLoop.emitGeneration(), gesture);
				}
			}
		}
	}

	private static boolean button(int id, int canvasX, int canvasY, int button, long generation,
		InputLoop.Emit gesture)
	{
		boolean releasing = id == MouseEvent.MOUSE_RELEASED;
		// Early out only, as in tryMove. A RELEASED is never refused: it ends a hold the game sees.
		if (!releasing && !InputLoop.mayEmit(generation))
		{
			return false;
		}
		// Outside the lock: the pause cleanup takes it, and a lookup that waits on the client would
		// otherwise hold the cleanup up with it.
		Canvas canvas = canvas();
		synchronized (HELD_BUTTONS_LOCK)
		{
			return buttonEvent(canvas, id, canvasX, canvasY, button, generation, gesture);
		}
	}

	/** Under {@link #HELD_BUTTONS_LOCK}, so the dispatch and the record it changes are one step. */
	private static boolean buttonEvent(Canvas canvas, int id, int canvasX, int canvasY, int button, long generation,
		InputLoop.Emit gesture)
	{
		boolean releasing = id == MouseEvent.MOUSE_RELEASED;
		if (canvas == null)
		{
			return false;
		}
		// A refused ENTERED does not hold back a RELEASED, which must still end the hold.
		if (!enterIfOutside(canvas, canvasX, canvasY, generation) && !releasing)
		{
			return false;
		}
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
			component.getX(), component.getY(), 1, false, button), generation);
		if (delivered && id == MouseEvent.MOUSE_PRESSED)
		{
			HELD_BUTTONS.put(button, gesture != null ? gesture : NO_GESTURE);
		}
		else if (delivered && id == MouseEvent.MOUSE_RELEASED)
		{
			HELD_BUTTONS.remove(button);
		}
		return delivered;
	}

	public static int heldMask()
	{
		int mask = 0;
		for (Integer button : HELD_BUTTONS.keySet())
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

	/**
	 * Releases synthetic buttons after the pause has fenced off new input, whoever pressed them. A
	 * gesture holding one of them sends no RELEASED for it afterwards, see {@link #releaseOwned}.
	 */
	public static void releaseHeldButtons()
	{
		synchronized (HELD_BUTTONS_LOCK)
		{
			Point at = PointerState.get();
			for (Integer button : HELD_BUTTONS.keySet().toArray(new Integer[0]))
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
	 *
	 * @return false when the ENTERED was refused
	 */
	private static boolean enterIfOutside(Canvas canvas, int fallbackX, int fallbackY, long generation)
	{
		if (!PointerState.isOutside())
		{
			return true;
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
		return dispatch(canvas, new MouseEvent(canvas, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0,
			component.getX(), component.getY(), 0, false), generation);
	}

	private static boolean isOffCanvas(int canvasX, int canvasY)
	{
		int width = canvasWidth();
		int height = canvasHeight();
		// Unknown size: call everything inside, rather than silencing all motion.
		if (width <= 0 || height <= 0)
		{
			return false;
		}
		return canvasX < 0 || canvasX >= width || canvasY < 0 || canvasY >= height;
	}

	/**
	 * Mirror of {@link #enterIfOutside}, for a move to a point off the canvas. Off-canvas is a real
	 * destination: antiban parks the cursor there, so sending nothing because the pointer is already
	 * out is not a refusal.
	 *
	 * @return false when the EXITED was refused
	 */
	private static boolean exitCanvas(int canvasX, int canvasY, long generation)
	{
		if (PointerState.isOutside())
		{
			return true;
		}
		// Real events win, same rule recordPosition applies: a synthetic exit must not move the
		// human's pointer off the canvas underneath them. Outside a gesture that is simply nothing to
		// send; inside one a takeover refuses the move, as the dispatch would have.
		if (InputArbiter.isHuman())
		{
			return !InputLoop.isInGesture();
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
		return dispatch(canvas, new MouseEvent(canvas, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0,
			component.getX(), component.getY(), 0, false), generation);
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
	private static boolean dispatch(Canvas canvas, AWTEvent event, long generation)
	{
		// The decision whether this event goes out, made once, as late as it can be. Bug fix: this
		// used to test only the paused flag, so a pause and resume landing between an emit's entry
		// and here let the event through into the game after the pause.
		if (event.getID() != MouseEvent.MOUSE_RELEASED && !InputLoop.mayEmit(generation))
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
