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
        int button = rightClick ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1;
        // A human clicking where the pointer already is sends no fresh MOVED, so emit one only
        // when it is not there, which happens when NaturalMouse was skipped. No ENTERED/EXITED:
        // no human click sends those, and the EXITED wrote (-1,-1) into the tracked position.
        if (!PointerState.isAt(point.getX(), point.getY())) {
            emit.move(point.getX(), point.getY());
        }
        emit.press(point.getX(), point.getY(), button);
        emit.release(point.getX(), point.getY(), button);
        emit.click(point.getX(), point.getY(), button);
        setLastClick(point);
    }

    private boolean shouldMoveNaturally(Point point) {
        return point.getX() > 1
                && point.getY() > 1
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
        if (point == null) return this;

        runGesture(() -> InputLoop.run(emit -> {
            Point newPoint = point;
            if (shouldMoveNaturally(point)) {
                Microbot.naturalMouse.moveTo(point.getX(), point.getY());

                if (Rs2UiHelper.hasActor(entry)) {
                    Rectangle rectangle = Rs2UiHelper.getActorClickbox(entry.getActor());
                    if (!Rs2UiHelper.isMouseWithinRectangle(rectangle)) {
                        newPoint = Rs2UiHelper.getClickingPoint(rectangle, true);
                        Microbot.naturalMouse.moveTo(newPoint.getX(), newPoint.getY());
                    }
                }

                if (Rs2UiHelper.isGameObject(entry)) {
                    Rectangle rectangle = Rs2UiHelper.getObjectClickbox(entry.getGameObject());
                    if (!Rs2UiHelper.isMouseWithinRectangle(rectangle)) {
                        newPoint = Rs2UiHelper.getClickingPoint(rectangle, true);
                        Microbot.naturalMouse.moveTo(newPoint.getX(), newPoint.getY());

                    }
                }
            }

            Microbot.targetMenu = entry;
            handleClick(emit, newPoint, rightClick);
        }));

        return this;
    }


    public Mouse click(int x, int y) {
        return click(new Point(x, y), false);
    }

    public Mouse click(double x, double y) {
        return click(new Point((int) x, (int) y), false);
    }

    public Mouse click(Rectangle rectangle) {
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
    public boolean tryClick(Point point, NewMenuEntry entry, java.util.function.BooleanSupplier valid) {
        if (point == null || Microbot.getClient().isClientThread() || Thread.currentThread().isInterrupted()) return false;
        final boolean[] acknowledged = {false};
        InputLoop.Result result = InputLoop.run(emit -> {
            if (shouldMoveNaturally(point)) Microbot.naturalMouse.moveTo(point.getX(), point.getY());
            emit.checkpoint();
            if (!valid.getAsBoolean()) return;
            try (net.runelite.client.plugins.microbot.util.menu.PendingMenuAction pending =
                         new net.runelite.client.plugins.microbot.util.menu.PendingMenuAction(entry)) {
                emit.move(point.getX(), point.getY());
                if (!net.runelite.client.plugins.microbot.util.Global.sleepUntil(pending::isPrepared, 1200)) return;
                emit.checkpoint();
                if (!valid.getAsBoolean()) return;
                handleClick(emit, point, false);
                acknowledged[0] = net.runelite.client.plugins.microbot.util.Global.sleepUntil(pending::isAcknowledged, 1200);
            }
        });
        return result == InputLoop.Result.COMPLETED && acknowledged[0];
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
