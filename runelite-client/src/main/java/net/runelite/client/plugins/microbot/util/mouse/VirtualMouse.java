package net.runelite.client.plugins.microbot.util.mouse;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.AwtEmitter;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

@Slf4j
public class VirtualMouse extends Mouse {

    public static final int CLICK_HOLD_MIN_MS = 55;
    public static final int CLICK_HOLD_MAX_MS = 140;
    public static final int QUICK_CLICK_HOLD_MIN_MS = 30;
    public static final int QUICK_CLICK_HOLD_MAX_MS = 65;
    public static final int QUICK_CLICK_SETTLE_MIN_MS = 10;
    public static final int QUICK_CLICK_SETTLE_MAX_MS = 25;

    private final ScheduledExecutorService scheduledExecutorService;

    @Inject
    public VirtualMouse() {
        super();
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    }

    public void setLastClick(Point point) {
        lastClick2 = lastClick;
        lastClick = point;
    }

    // Feeds the debug overlay's fading trail only; position itself lives in PointerState.
    private void recordTrailPoint(Point point) {
        points.add(point);
        if (points.size() > MAX_POINTS) {
            points.pollFirst();
        }
    }

    private void handleClick(InputLoop.Emit emit, Point point, boolean rightClick) {
        handleClick(emit, point, rightClick, false, () -> { });
    }

    private void handleClick(InputLoop.Emit emit, Point point, boolean rightClick, boolean quick, Runnable beforePress) {
        int button = rightClick ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1;
        // A human clicking where the pointer already is sends no fresh MOVED, so emit one only
        // when it is not there, which happens when NaturalMouse was skipped. No ENTERED/EXITED:
        // no human click sends those, and the EXITED wrote (-1,-1) into the tracked position.
        if (!PointerState.isAt(point.getX(), point.getY())) {
            emit.move(point.getX(), point.getY());
            if (quick) {
                sleep(Rs2Random.logNormalBounded(QUICK_CLICK_SETTLE_MIN_MS, QUICK_CLICK_SETTLE_MAX_MS));
            }
        }
        beforePress.run();
        emit.press(point.getX(), point.getY(), button);
        sleep(quick
                ? Rs2Random.logNormalBounded(QUICK_CLICK_HOLD_MIN_MS, QUICK_CLICK_HOLD_MAX_MS)
                : Rs2Random.logNormalBounded(CLICK_HOLD_MIN_MS, CLICK_HOLD_MAX_MS));
        emit.release(point.getX(), point.getY(), button);
        emit.click(point.getX(), point.getY(), button);
        setLastClick(point);
    }

    private boolean shouldMoveNaturally(Point point) {
        return point.getX() >= 0
                && point.getY() >= 0
                && Microbot.naturalMouse != null;
    }

    /**
     * A gesture takes the {@link InputLoop} lock and sleeps while holding it, and neither may
     * happen on the client thread. Every gesture goes through here so none can forget.
     */
    private void runGesture(Runnable gesture) {
        if (Microbot.getClient().isClientThread()) {
            scheduledExecutorService.schedule(gesture, 0, TimeUnit.MILLISECONDS);
        } else {
            gesture.run();
        }
    }

    public Mouse click(Point point, boolean rightClick) {
        if (point == null) return this;

        runGesture(() -> InputLoop.run(emit -> {
            if (shouldMoveNaturally(point)) {
                Microbot.naturalMouse.moveTo(point.getX(), point.getY());
            }
            handleClick(emit, point, rightClick);
        }));

        return this;
    }


    public Mouse click(Point point, boolean rightClick, NewMenuEntry entry) {
        return click(point, rightClick, entry, false);
    }

    @Override
    public Mouse quickClick(Point point, NewMenuEntry entry) {
        return click(point, false, entry, true);
    }

    private Mouse click(Point point, boolean rightClick, NewMenuEntry entry, boolean quick) {
        if (point == null) return this;

        runGesture(() -> InputLoop.run(emit -> {
            Point newPoint = point;
            if (!quick && shouldMoveNaturally(point)) {
                Microbot.naturalMouse.moveTo(point.getX(), point.getY());

                if (Rs2UiHelper.hasActor(entry)) {
                    newPoint = retarget(Rs2UiHelper.getActorClickbox(entry.getActor()), newPoint);
                }

                if (Rs2UiHelper.isGameObject(entry)) {
                    newPoint = retarget(Rs2UiHelper.getObjectClickbox(entry.getGameObject()), newPoint);
                }
            }

            handleClick(emit, newPoint, rightClick, quick, () -> Microbot.targetMenu = entry);
        }));

        return this;
    }


    static Point retarget(Rectangle clickbox, Point current) {
        if (Rs2UiHelper.isFallbackRectangle(clickbox)) {
            return current;
        }
        Rectangle clear = Rs2UiHelper.clearOfInterfaces(clickbox, Rs2UiHelper.sceneViewportBounds(),
                Rs2UiHelper.visibleSceneOverlayBounds());
        if (clear == null || clear.contains(Microbot.getMouse().getMousePosition())) {
            return current;
        }
        Point target = Rs2UiHelper.getClickingPoint(clear, true, false);
        Microbot.naturalMouse.moveTo(target.getX(), target.getY());
        return target;
    }

    public Mouse click(int x, int y) {
        return click(new Point(x, y), false);
    }

    public Mouse click(double x, double y) {
        return click(new Point((int) x, (int) y), false);
    }

    public Mouse click(Rectangle rectangle) {
        if (Rs2UiHelper.isFallbackRectangle(rectangle)) {
            return this;
        }
        return click(Rs2UiHelper.getClickingPoint(rectangle, true), false);
    }

    @Override
    public Mouse click(int x, int y, boolean rightClick) {
        return click(new Point(x, y), rightClick);
    }

    @Override
    public Mouse click(Point point) {
        return click(point, false);
    }

    @Override
    public Mouse click(Point point, NewMenuEntry entry) {
        return click(point, false, entry);
    }

    @Override
    public Mouse click() {
        return click(Microbot.getClient().getMouseCanvasPosition());
    }

    // NaturalMouse steps through here, so this one check also stops a trajectory mid-curve.
    public Mouse move(Point point) {
        if (InputArbiter.isHuman()) {
            return this;
        }
        if (AwtEmitter.heldMask() != 0 && !InputLoop.isInGesture()) {
            return this;
        }
        recordTrailPoint(point);
        AwtEmitter.moved(point.getX(), point.getY());
        return this;
    }

    public Mouse move(Rectangle rect) {
        return move(new Point((int) rect.getCenterX(), (int) rect.getCenterY()));
    }

    public Mouse move(Polygon polygon) {
        return move(new Point((int) polygon.getBounds().getCenterX(), (int) polygon.getBounds().getCenterY()));
    }

    public Mouse scrollDown(Point point) {
        return scroll(point, 2, 10);
    }

    public Mouse scrollUp(Point point) {
        return scroll(point, -2, -10);
    }

    /**
     * One gesture covering both the move and the wheel, so another script's click cannot land
     * between them and leave the wheel firing at a point the cursor has left.
     *
     * <p>The pause stays: a human turns the wheel a moment after arriving, not in the same
     * instant. A takeover during it aborts at the wheel's checkpoint.
     */
    private Mouse scroll(Point point, int wheelRotation, int unitsToScroll) {
        if (point == null) return this;

        runGesture(() -> InputLoop.run(emit -> {
            emit.move(point.getX(), point.getY());
            recordTrailPoint(point);
            sleep(Rs2Random.logNormalBounded(40, 100));
            emit.wheel(point.getX(), point.getY(), wheelRotation, unitsToScroll);
        }));

        return this;
    }

    @Override
    public java.awt.Point getMousePosition() {
        Point point = PointerState.get();
        return new java.awt.Point(point.getX(), point.getY());
    }

    @Override
    public Mouse move(int x, int y) {
        return move(new Point(x, y));
    }

    @Override
    public Mouse move(double x, double y) {
        return move(new Point((int) x, (int) y));
    }

    public void shutdown() {
        scheduledExecutorService.shutdownNow();
    }

    private void moveTowards(Point point) {
        if (shouldMoveNaturally(point))
            Microbot.naturalMouse.moveTo(point.getX(), point.getY());
        else
            move(point);
    }

    // The one gesture holding a button across time, and the reason the held-button set exists.
    // The sleeps return immediately under a takeover and the next emit aborts, releasing at the
    // human's point rather than falling through to a RELEASED at the stale end point.
    public Mouse drag(Point startPoint, Point endPoint) {
        if (startPoint == null || endPoint == null) return this;

        runGesture(() -> InputLoop.run(emit -> {
            moveTowards(startPoint);
            sleep(Rs2Random.logNormalBounded(50, 80));
            emit.press(startPoint.getX(), startPoint.getY(), MouseEvent.BUTTON1);
            sleep(Rs2Random.logNormalBounded(80, 120));
            moveTowards(endPoint);
            sleep(Rs2Random.logNormalBounded(80, 120));
            emit.release(endPoint.getX(), endPoint.getY(), MouseEvent.BUTTON1);
        }));

        return this;
    }
}
