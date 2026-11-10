package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dispatches synthetic AWT mouse events on the game canvas, taking canvas coordinates.
 *
 * <p>Whether an event may go out is decided once, at its dispatch, by {@link InputLoop#mayEmit}, and
 * the package-private try methods report that decision. Inside a gesture a refused event ends the
 * gesture, whichever method it came through. A RELEASED is never refused. The tracked pointer in
 * {@link PointerState} changes only with an event that went out.
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

	/*
	 * The canvas's focusable flag is derived in applyFocusable and only there, from the user's setting
	 * and from whether any synthetic dispatch is guarding that canvas, both written only under
	 * FOCUSABLE_LOCK. Bug fix: each dispatch used to read the flag, clear it, and write back what it
	 * had read, so a setting the user wrote while a dispatch was in flight was undone when it
	 * returned, and of two overlapping dispatches the first to return made the canvas focusable under
	 * the second.
	 *
	 * The lock is held for the bookkeeping and the one setFocusable call, which runs the component's
	 * "focusable" property listeners on the calling thread; never across dispatchEvent.
	 */
	private static final Object FOCUSABLE_LOCK = new Object();
	// True before anyone sets it: the Component default and the input-enabled state, so a canvas
	// nobody configured comes out of a dispatch focusable, as it always did.
	private static boolean focusableSetting = true;
	/** Synthetic dispatches guarding each canvas; a canvas is in here only while its count is above zero. */
	private static final Map<Canvas, Integer> FOCUS_GUARDS = new IdentityHashMap<>();

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
		button(MouseEvent.MOUSE_RELEASED, canvasX, canvasY, button, null);
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
	 * The try methods return false when their event was refused, by InputLoop.mayEmit at the
	 * dispatch or for want of a canvas, and true when it went out or, for a move off the canvas,
	 * nothing was due. Each takes its pause generation on entry: the gesture's inside one, the current
	 * one outside, so an event never goes out once a pause has begun after its gesture or its call
	 * started. Nothing is decided before the dispatch, and PointerState changes only after one.
	 *
	 * Bug fix: PointerState used to be written before the dispatch, so an event refused there still
	 * moved the tracked pointer, or recorded it as inside or outside, though the game never saw it.
	 * A pointer recorded inside after a refused ENTERED never announced its return.
	 */

	static boolean tryMove(int canvasX, int canvasY)
	{
		long generation = InputLoop.emitGeneration();
		// Here, not at the callers: every motion funnels through this method.
		if (isOffCanvas(canvasX, canvasY))
		{
			return exitCanvas(canvasX, canvasY, generation);
		}
		Canvas canvas = canvas();
		if (canvas == null || !enterIfOutside(canvas, canvasX, canvasY, generation))
		{
			return false;
		}
		Point component = StretchMapper.toComponent(canvasX, canvasY);
		int held = heldMask();
		int id = held == 0 ? MouseEvent.MOUSE_MOVED : MouseEvent.MOUSE_DRAGGED;
		if (!dispatch(canvas, new MouseEvent(canvas, id, System.currentTimeMillis(), held,
			component.getX(), component.getY(), 0, false), generation))
		{
			return false;
		}
		recordPosition(canvasX, canvasY);
		return true;
	}

	/**
	 * A RELEASED is owed only for a press this returned true for.
	 *
	 * @param gesture the gesture the press belongs to, or null for a direct press
	 */
	static boolean tryPress(int canvasX, int canvasY, int button, InputLoop.Emit gesture)
	{
		return button(MouseEvent.MOUSE_PRESSED, canvasX, canvasY, button, gesture);
	}

	static boolean tryClick(int canvasX, int canvasY, int button)
	{
		return button(MouseEvent.MOUSE_CLICKED, canvasX, canvasY, button, null);
	}

	static boolean tryWheel(int canvasX, int canvasY, int wheelRotation, int unitsToScroll)
	{
		long generation = InputLoop.emitGeneration();
		Canvas canvas = canvas();
		if (canvas == null || !enterIfOutside(canvas, canvasX, canvasY, generation))
		{
			return false;
		}
		Point component = StretchMapper.toComponent(canvasX, canvasY);
		if (!dispatch(canvas, new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
			component.getX(), component.getY(), 0, false, 0, unitsToScroll, wheelRotation), generation))
		{
			return false;
		}
		recordPosition(canvasX, canvasY);
		return true;
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
				releaseEvent(canvas, canvasX, canvasY, button);
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
					releaseEvent(canvas, at.getX(), at.getY(), held.getKey());
				}
			}
		}
	}

	private static boolean button(int id, int canvasX, int canvasY, int button, InputLoop.Emit gesture)
	{
		long generation = InputLoop.emitGeneration();
		// Outside the lock: the pause cleanup takes it, and a lookup that waits on the client would
		// otherwise hold the cleanup up with it.
		Canvas canvas = canvas();
		if (canvas == null)
		{
			return false;
		}
		// Outside the lock too, so no lock is held across a dispatch that is not a button's. A
		// RELEASED announces no entry: a real release after the pointer left reaches the canvas
		// without one, since the canvas that took the press keeps the button's events.
		if (id != MouseEvent.MOUSE_RELEASED && !enterIfOutside(canvas, canvasX, canvasY, generation))
		{
			return false;
		}
		synchronized (HELD_BUTTONS_LOCK)
		{
			return buttonEvent(canvas, id, canvasX, canvasY, button, generation, gesture);
		}
	}

	/** A RELEASED is never refused, so the generation it is dispatched under does not matter. */
	private static boolean releaseEvent(Canvas canvas, int canvasX, int canvasY, int button)
	{
		return buttonEvent(canvas, MouseEvent.MOUSE_RELEASED, canvasX, canvasY, button, InputLoop.emitGeneration(),
			null);
	}

	/**
	 * Under {@link #HELD_BUTTONS_LOCK}, so the dispatch and the record it changes are one step against
	 * the pause cleanup. A press, click or release only; any ENTERED went out before the lock.
	 *
	 * @param gesture the gesture a press belongs to, or null for a direct press
	 */
	private static boolean buttonEvent(Canvas canvas, int id, int canvasX, int canvasY, int button, long generation,
		InputLoop.Emit gesture)
	{
		if (canvas == null)
		{
			return false;
		}
		Point component = StretchMapper.toComponent(canvasX, canvasY);
		int modifiers = heldMask();
		if (id == MouseEvent.MOUSE_PRESSED)
		{
			modifiers |= InputEvent.getMaskForButton(button);
		}
		else if (id == MouseEvent.MOUSE_RELEASED)
		{
			modifiers &= ~InputEvent.getMaskForButton(button);
		}
		if (!dispatch(canvas, new MouseEvent(canvas, id, System.currentTimeMillis(), modifiers,
			component.getX(), component.getY(), 1, false, button), generation))
		{
			return false;
		}
		recordPosition(canvasX, canvasY);
		if (id == MouseEvent.MOUSE_PRESSED)
		{
			HELD_BUTTONS.put(button, gesture != null ? gesture : NO_GESTURE);
		}
		else if (id == MouseEvent.MOUSE_RELEASED)
		{
			HELD_BUTTONS.remove(button);
		}
		return true;
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
		// Outside the lock, for the reason given in button().
		Canvas canvas = canvas();
		synchronized (HELD_BUTTONS_LOCK)
		{
			Point at = PointerState.get();
			for (Integer button : HELD_BUTTONS.keySet().toArray(new Integer[0]))
			{
				releaseEvent(canvas, at.getX(), at.getY(), button);
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
	 * @return false when the ENTERED was refused, which leaves the pointer recorded as outside, so
	 * the next event that does go out announces the return
	 */
	private static boolean enterIfOutside(Canvas canvas, int fallbackX, int fallbackY, long generation)
	{
		if (!PointerState.isOutside())
		{
			return true;
		}
		Point at = PointerState.get();
		int exitX = at.getX() < 0 ? fallbackX : at.getX();
		int exitY = at.getY() < 0 ? fallbackY : at.getY();

		Point entry = reentryPoint(exitX, exitY, canvasWidth(), canvasHeight(), ThreadLocalRandom.current());
		Point component = StretchMapper.toComponent(entry.getX(), entry.getY());
		if (!dispatch(canvas, new MouseEvent(canvas, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0,
			component.getX(), component.getY(), 0, false), generation))
		{
			return false;
		}
		PointerState.markInside();
		// Recorded too, or the next click presses where the pointer never travelled.
		recordPosition(entry.getX(), entry.getY());
		return true;
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
	 * @return false when the EXITED was refused, which leaves the pointer recorded as inside
	 */
	private static boolean exitCanvas(int canvasX, int canvasY, long generation)
	{
		if (PointerState.isOutside())
		{
			return true;
		}
		// Real events win, same rule recordPosition applies: outside a gesture a synthetic exit must
		// not move the human's pointer off the canvas underneath them, and there is nothing to send.
		// Inside one the dispatch decides, and a takeover refuses it.
		if (!InputLoop.isInGesture() && InputArbiter.isHuman())
		{
			return true;
		}

		Canvas canvas = canvas();
		if (canvas == null)
		{
			return false;
		}
		Point component = StretchMapper.toComponent(canvasX, canvasY);
		if (!dispatch(canvas, new MouseEvent(canvas, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0,
			component.getX(), component.getY(), 0, false), generation))
		{
			return false;
		}
		// Through recordPosition, so the bot reference moves out with the pointer. Left behind, the
		// motion threshold would be measured from a point the bot has already left.
		recordPosition(canvasX, canvasY);
		PointerState.markOutside();
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

	/**
	 * The user's input setting for the canvas's focusable flag. The flag must not be written any
	 * other way, or a synthetic dispatch ending later overwrites the write.
	 *
	 * <p>Takes effect at once when no synthetic dispatch is guarding the canvas, otherwise when the
	 * last one returns. Never waits for a dispatch: the lock it takes is held only around
	 * bookkeeping and the write to the canvas, never across a listener the game runs.
	 */
	public static void setCanvasFocusable(boolean focusable)
	{
		// Outside the lock, for the reason given in button().
		Canvas canvas = canvas();
		synchronized (FOCUSABLE_LOCK)
		{
			focusableSetting = focusable;
			if (canvas != null)
			{
				applyFocusable(canvas);
			}
		}
	}

	/** Derives the flag; call under {@link #FOCUSABLE_LOCK}. */
	private static void applyFocusable(Canvas canvas)
	{
		canvas.setFocusable(focusableSetting && !FOCUS_GUARDS.containsKey(canvas));
	}

	private static void enterFocusGuard(Canvas canvas)
	{
		synchronized (FOCUSABLE_LOCK)
		{
			FOCUS_GUARDS.merge(canvas, 1, Integer::sum);
			applyFocusable(canvas);
		}
	}

	private static void exitFocusGuard(Canvas canvas)
	{
		synchronized (FOCUSABLE_LOCK)
		{
			FOCUS_GUARDS.computeIfPresent(canvas, (guarded, count) -> count == 1 ? null : count - 1);
			applyFocusable(canvas);
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
		boolean guard = !canvas.isFocusOwner();
		try
		{
			// Inside the try: the count goes up before anything here can throw, so it always comes
			// down again.
			if (guard)
			{
				enterFocusGuard(canvas);
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
			}
		}
		finally
		{
			if (guard)
			{
				exitFocusGuard(canvas);
			}
		}
	}
}
