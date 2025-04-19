package net.runelite.client.plugins.microbot.util.mouse;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

@Slf4j
public class VirtualMouse extends Mouse {

    private final ScheduledExecutorService scheduledExecutorService;
    private boolean exited = true;

    @Inject
    public VirtualMouse() {
        super();
        this.scheduledExecutorService = Executors.newScheduledThreadPool(10);
        //getCanvas().setFocusable(false);
    }

    public void setLastClick(Point point) {
        lastClick2 = lastClick;
        lastClick = point;
    }

    public void setLastMove(Point point) {
        lastMove = point;
        points.add(point);
        if (points.size() > MAX_POINTS) {
            try {
                points.removeFirst();
            } catch(Exception ex) {
                points.clear();
            }
        }
    }

    private void handleClick(Point point, boolean rightClick) {
        entered(point);
        exited(point);
        moved(point);
        pressed(point, rightClick ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1);
        released(point, rightClick ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1);
        clicked(point, rightClick ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1);
        setLastClick(point);
    }
    public Mouse click(Point point, boolean rightClick) {
        if (point == null) return this;

        Runnable clickAction = () -> {
            if (Rs2AntibanSettings.naturalMouse && point.getX() > 1 && point.getY() > 1) {
                Microbot.naturalMouse.moveTo(point.getX(), point.getY());
            }
            handleClick(point, rightClick);
        };

        if (Microbot.getClient().isClientThread()) {
            scheduledExecutorService.schedule(clickAction, 0, TimeUnit.MILLISECONDS);
        } else {
            clickAction.run();
        }

        return this;
    }


    public Mouse click(Point point, boolean rightClick, NewMenuEntry entry) {
        if (point == null) return this;

        Runnable clickAction = () -> {
            Point newPoint = point;
            if (Rs2AntibanSettings.naturalMouse && point.getX() > 1 && point.getY() > 1) {
                Microbot.naturalMouse.moveTo(point.getX(), point.getY());

                if (Rs2UiHelper.hasActor(entry)) {
                    log.info("Actor found: " + entry.getActor().getName());
                    Rectangle rectangle = Rs2UiHelper.getActorClickbox(entry.getActor());
                    if (!Rs2UiHelper.isMouseWithinRectangle(rectangle)) {
                        newPoint = Rs2UiHelper.getClickingPoint(rectangle, true);
                        Microbot.naturalMouse.moveTo(newPoint.getX(), newPoint.getY());
                    }
                }

                if (Rs2UiHelper.isGameObject(entry)) {
                    log.info("Game Object found: " + entry.getGameObject().toString());
                    Rectangle rectangle = Rs2UiHelper.getObjectClickbox(entry.getGameObject());
                    if (!Rs2UiHelper.isMouseWithinRectangle(rectangle)) {
                        newPoint = Rs2UiHelper.getClickingPoint(rectangle, true);
                        Microbot.naturalMouse.moveTo(newPoint.getX(), newPoint.getY());

                    }
                }
            }

            Microbot.targetMenu = entry;
            handleClick(newPoint, rightClick);
        };

        if (Microbot.getClient().isClientThread()) {
            scheduledExecutorService.schedule(clickAction, 0, TimeUnit.MILLISECONDS);
        } else {
            clickAction.run();
        }

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
    public Mouse click() {
        return click(Microbot.getClient().getMouseCanvasPosition());
    }

    public Mouse move(Point point) {
        setLastMove(point);
        MouseEvent mouseMove = new MouseEvent(Microbot.getClient().getCanvas(), MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, point.getX(), point.getY(), 0, false);
        mouseMove.setSource("Microbot");
        getCanvas().dispatchEvent(mouseMove);

        return this;
    }

    public Mouse move(Rectangle rect) {
        MouseEvent mouseMove = new MouseEvent(Microbot.getClient().getCanvas(), MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, (int) rect.getCenterX(), (int) rect.getCenterY(), 0, false);
        mouseMove.setSource("Microbot");
        getCanvas().dispatchEvent(mouseMove);

        return this;
    }

    public Mouse move(Polygon polygon) {
        Point point = new Point((int) polygon.getBounds().getCenterX(), (int) polygon.getBounds().getCenterY());

        MouseEvent mouseMove = new MouseEvent(getCanvas(), MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, point.getX(), point.getY(), 0, false);
        mouseMove.setSource("Microbot");
        getCanvas().dispatchEvent(mouseMove);

        return this;
    }

    public Mouse scrollDown(Point point) {
        long time = System.currentTimeMillis();

        move(point);

        scheduledExecutorService.schedule(() -> {
            MouseEvent mouseScroll = new MouseWheelEvent(getCanvas(), MouseEvent.MOUSE_WHEEL, time, 0, point.getX(), point.getY(), 0, false,
                    0, 10, 2);
            mouseScroll.setSource("Microbot");
            getCanvas().dispatchEvent(mouseScroll);
        }, Rs2Random.between(40,100), TimeUnit.MILLISECONDS);
        return this;
    }

    public Mouse scrollUp(Point point) {
        long time = System.currentTimeMillis();

        move(point);

        scheduledExecutorService.schedule(() -> {
            MouseEvent mouseScroll = new MouseWheelEvent(getCanvas(), MouseEvent.MOUSE_WHEEL, time, 0, point.getX(), point.getY(), 0, false,
                    0, -10, -2);
            mouseScroll.setSource("Microbot");
            getCanvas().dispatchEvent(mouseScroll);
        }, Rs2Random.between(40,100), TimeUnit.MILLISECONDS);
        return this;
    }

    @Override
    public java.awt.Point getMousePosition() {
        Point point = lastMove;
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

    @Deprecated
    private void mouseEvent(int id, Point point, boolean rightClick) {
        int button = rightClick ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1;
        MouseEvent e = new MouseEvent(Microbot.getClient().getCanvas(), id, System.currentTimeMillis(), 0, point.getX(), point.getY(), 1, false, button);
        getCanvas().dispatchEvent(e);
    }

    private synchronized void pressed(Point point, int button) {
        MouseEvent event = new MouseEvent(Microbot.getClient().getCanvas(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, point.getX(), point.getY(), 1, false, button);
        event.setSource("Microbot");
        getCanvas().dispatchEvent(event);
    }

    private synchronized void released(Point point, int button) {
        MouseEvent event = new MouseEvent(Microbot.getClient().getCanvas(), MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, point.getX(), point.getY(), 1, false, button);
        event.setSource("Microbot");
        getCanvas().dispatchEvent(event);
    }

    private synchronized void clicked(Point point, int button) {
        MouseEvent event = new MouseEvent(Microbot.getClient().getCanvas(), MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, point.getX(), point.getY(), 1, false, button);
        event.setSource("Microbot");
        getCanvas().dispatchEvent(event);
    }

    private synchronized void exited(Point point) {
        MouseEvent event = new MouseEvent(Microbot.getClient().getCanvas(), MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, point.getX(), point.getY(), 0, false);
        event.setSource("Microbot");
        getCanvas().dispatchEvent(event);
        exited = true;
    }

    private synchronized void entered(Point point) {
        MouseEvent event = new MouseEvent(Microbot.getClient().getCanvas(), MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, point.getX(), point.getY(), 0, false);
        event.setSource("Microbot");
        getCanvas().dispatchEvent(event);
        exited = false;
    }

    private synchronized void moved(Point point) {
        MouseEvent event = new MouseEvent(Microbot.getClient().getCanvas(), MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, point.getX(), point.getY(), 0, false);
        event.setSource("Microbot");
        getCanvas().dispatchEvent(event);
    }

    // New drag method
    public Mouse drag(Point startPoint, Point endPoint) {
        if (startPoint == null || endPoint == null) return this;

        if (Rs2AntibanSettings.naturalMouse && (startPoint.getX() > 1 && startPoint.getY() > 1))
            Microbot.naturalMouse.moveTo(startPoint.getX(), startPoint.getY());
        else
            move(startPoint);
        sleep(50, 80);
        // Press the mouse button at the start point
        pressed(startPoint, MouseEvent.BUTTON1);
        sleep(80, 120);
        // Move to the end point while holding the button down
        if (Rs2AntibanSettings.naturalMouse && (endPoint.getX() > 1 && endPoint.getY() > 1))
            Microbot.naturalMouse.moveTo(endPoint.getX(), endPoint.getY());
        else
            move(endPoint);
        sleep(80, 120);
        // Release the mouse button at the end point
        released(endPoint, MouseEvent.BUTTON1);

        return this;
    }
}
