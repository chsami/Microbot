package net.runelite.client.plugins.microbot.util.input;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;

import java.awt.Canvas;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
 * Observe-only listeners on the game canvas: they read real input and never consume, transform or
 * dispatch anything. The one write outside the arbiter is disarming {@code Microbot.targetMenu} on
 * a real press.
 *
 * <p>Synthetic events are filtered by {@link BotEventGuard}, which works because
 * {@code Canvas.dispatchEvent} runs listeners synchronously on the dispatching thread.
 * {@code Rs2Keyboard} hand-delivers to {@code canvas.getKeyListeners()} instead, so it has to
 * raise the same guard or the bot reads its own keystrokes as a takeover.
 */
@Slf4j
public final class CanvasInputListener implements MouseListener, MouseMotionListener, KeyListener, FocusListener
{
	private static final CanvasInputListener INSTANCE = new CanvasInputListener();

	private static volatile Canvas attachedCanvas;

	private CanvasInputListener()
	{
	}

	/**
	 * Idempotent. Re-attaches if the canvas instance ever changes, since a stale registration
	 * leaves the arbiter silently deaf with no other symptom.
	 *
	 * <p>Measured: a fixed/resizable switch does not replace the canvas, so this is currently
	 * untriggered. Kept because it is a per-tick identity comparison and a renderer swap is a
	 * plausible trigger nobody has tested.
	 */
	public static synchronized void attach()
	{
		Canvas canvas = canvas();
		if (canvas == null || canvas == attachedCanvas)
		{
			return;
		}
		detach();
		canvas.addMouseListener(INSTANCE);
		canvas.addMouseMotionListener(INSTANCE);
		canvas.addKeyListener(INSTANCE);
		canvas.addFocusListener(INSTANCE);
		attachedCanvas = canvas;
		// Info, not debug: fires once per canvas, and the alternative is a silent no-op at plugin
		// start followed by a silent recovery on the first game tick.
		log.info("Input arbiter listening on canvas {}", System.identityHashCode(canvas));
	}

	public static synchronized void detach()
	{
		Canvas previous = attachedCanvas;
		if (previous == null)
		{
			return;
		}
		previous.removeMouseListener(INSTANCE);
		previous.removeMouseMotionListener(INSTANCE);
		previous.removeKeyListener(INSTANCE);
		previous.removeFocusListener(INSTANCE);
		attachedCanvas = null;
		// Bug fix: anything held now can never deliver its release to a removed listener, so the
		// arbiter used to stay pinned on HUMAN until the next focus loss. Same transition as one.
		InputArbiter.onFocusLost();
	}

	static boolean isAttachedTo(Canvas canvas)
	{
		return attachedCanvas == canvas;
	}

	/** False if the registration was left behind on a replaced canvas. */
	public static boolean isAttachedToLiveCanvas()
	{
		Canvas canvas = canvas();
		return canvas != null && attachedCanvas == canvas;
	}

	/**
	 * Real key events only arrive while the canvas owns focus, which is why keys typed in another
	 * window never yield.
	 */
	public static boolean isCanvasFocused()
	{
		Canvas canvas = canvas();
		return canvas != null && canvas.isFocusOwner();
	}

	@Override
	public void mouseMoved(MouseEvent event)
	{
		position(event);
	}

	@Override
	public void mouseDragged(MouseEvent event)
	{
		position(event);
	}

	@Override
	public void mousePressed(MouseEvent event)
	{
		if (BotEventGuard.isSynthetic())
		{
			return;
		}
		// Bug fix: an armed entry rewrites every menu the client builds, so a human click arriving
		// before the game consumed it performed the bot's action. The human's press ends the bot's
		// claim to the next click, regardless of the arbiter's verdict or whether yielding is disabled.
		Microbot.targetMenu = null;
		position(event);
		InputArbiter.onRealButtonPressed(event.getButton());
	}

	@Override
	public void mouseReleased(MouseEvent event)
	{
		if (BotEventGuard.isSynthetic())
		{
			return;
		}
		position(event);
		InputArbiter.onRealButtonReleased(event.getButton());
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		if (BotEventGuard.isSynthetic())
		{
			return;
		}
		InputArbiter.onRealKeyPressed(event.getKeyCode());
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
		if (BotEventGuard.isSynthetic())
		{
			return;
		}
		InputArbiter.onRealKeyReleased(event.getKeyCode());
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
		// No key code, and always follows a KEY_PRESSED.
	}

	/**
	 * Anything held when focus leaves never delivers its release, and the stale entry would
	 * suppress idle resume forever.
	 *
	 * <p>Not filtered on {@link FocusEvent#isTemporary()}: window deactivation reports a temporary
	 * loss, which is precisely the case this exists for.
	 */
	@Override
	public void focusLost(FocusEvent event)
	{
		InputArbiter.onFocusLost();
	}

	@Override
	public void focusGained(FocusEvent event)
	{
		// Nothing to restore: a key still physically down announces itself on its next press.
	}

	@Override
	public void mouseClicked(MouseEvent event)
	{
		// PRESSED and RELEASED already cover the button.
	}

	/**
	 * Recorded, but does not flip HUMAN: crossing the boundary is not an intent to take over, and
	 * the motion either side of it already speaks for itself.
	 */
	@Override
	public void mouseEntered(MouseEvent event)
	{
		if (BotEventGuard.isSynthetic())
		{
			return;
		}
		Point canvasPoint = StretchMapper.toCanvas(event.getX(), event.getY());
		PointerState.setInside(canvasPoint.getX(), canvasPoint.getY());
	}

	@Override
	public void mouseExited(MouseEvent event)
	{
		if (BotEventGuard.isSynthetic())
		{
			return;
		}
		Point canvasPoint = StretchMapper.toCanvas(event.getX(), event.getY());
		PointerState.setOutside(canvasPoint.getX(), canvasPoint.getY());
	}

	private void position(MouseEvent event)
	{
		if (BotEventGuard.isSynthetic())
		{
			return;
		}
		Point canvasPoint = StretchMapper.toCanvas(event.getX(), event.getY());
		PointerState.setFromReal(canvasPoint.getX(), canvasPoint.getY());
		InputArbiter.onRealMove(canvasPoint.getX(), canvasPoint.getY());
	}

	private static Canvas canvas()
	{
		try
		{
			Client client = Microbot.getClient();
			return client == null ? null : client.getCanvas();
		}
		catch (Exception ex)
		{
			return null;
		}
	}
}
