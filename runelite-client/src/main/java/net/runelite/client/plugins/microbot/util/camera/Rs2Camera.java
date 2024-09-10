package net.runelite.client.plugins.microbot.util.camera;

import net.runelite.api.Actor;
import net.runelite.api.Perspective;
import net.runelite.api.ScriptID;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

import java.awt.*;
import java.awt.event.KeyEvent;

public class Rs2Camera {

    public static int angleToTile(Actor t) {
        int angle = (int) Math.toDegrees(Math.atan2(t.getWorldLocation().getY() - Microbot.getClient().getLocalPlayer().getWorldLocation().getY(),
                t.getWorldLocation().getX() - Microbot.getClient().getLocalPlayer().getWorldLocation().getX()));
        return angle >= 0 ? angle : 360 + angle;
    }

    public static int angleToTile(TileObject t) {
        int angle = (int) Math.toDegrees(Math.atan2(t.getWorldLocation().getY() - Microbot.getClient().getLocalPlayer().getWorldLocation().getY(),
                t.getWorldLocation().getX() - Microbot.getClient().getLocalPlayer().getWorldLocation().getX()));
        return angle >= 0 ? angle : 360 + angle;
    }

    public static int angleToTile(LocalPoint localPoint) {
        int angle = (int) Math.toDegrees(Math.atan2(localPoint.getY() - Microbot.getClient().getLocalPlayer().getWorldLocation().getY(),
                localPoint.getX() - Microbot.getClient().getLocalPlayer().getWorldLocation().getX()));
        return angle >= 0 ? angle : 360 + angle;
    }

    public static void turnTo(final Actor actor) {
        int angle = getCharacterAngle(actor);
        setAngle(angle, actor);
        Rs2Keyboard.keyRelease((char) KeyEvent.VK_RIGHT);
        Rs2Keyboard.keyRelease((char) KeyEvent.VK_LEFT);
    }

    public static void turnTo(final TileObject tileObject) {
        int angle = getObjectAngle(tileObject);
        setAngle(angle, tileObject);
        Rs2Keyboard.keyRelease((char) KeyEvent.VK_RIGHT);
        Rs2Keyboard.keyRelease((char) KeyEvent.VK_LEFT);
    }

    public static void turnTo(final LocalPoint localPoint) {
        int angle = angleToTile(localPoint);
        setAngle(angle, localPoint);
        Rs2Keyboard.keyRelease((char) KeyEvent.VK_RIGHT);
        Rs2Keyboard.keyRelease((char) KeyEvent.VK_LEFT);
    }

    public static int getCharacterAngle(Actor actor) {
        return getTileAngle(actor);
    }

    public static int getObjectAngle(TileObject tileObject) {
        return getTileAngle(tileObject);
    }

    public static int getTileAngle(Actor actor) {
        int a = (angleToTile(actor) - 90) % 360;
        return a < 0 ? a + 360 : a;
    }

    public static int getTileAngle(TileObject tileObject) {
        int a = (angleToTile(tileObject) - 90) % 360;
        return a < 0 ? a + 360 : a;
    }

    public static void setAngle(int degrees) {
        if (getAngleTo(degrees) > 5) {
            Rs2Keyboard.keyHold(KeyEvent.VK_LEFT);
            Global.awaitExecutionUntil(() -> Rs2Keyboard.keyRelease((char) KeyEvent.VK_LEFT),
                    () -> Math.abs(getAngleTo(degrees)) <= 5, 10);
        } else if (getAngleTo(degrees) < -5) {
            Rs2Keyboard.keyHold(KeyEvent.VK_RIGHT);
            Global.awaitExecutionUntil(() -> Rs2Keyboard.keyRelease((char) KeyEvent.VK_RIGHT),
                    () -> Math.abs(getAngleTo(degrees)) <= 5, 10);
        }
    }


    public static void setAngle(int degrees, Actor actor) {
        if (getAngleTo(degrees) > 5) {
            Rs2Keyboard.keyHold(KeyEvent.VK_LEFT);
            Global.awaitExecutionUntil(() -> Rs2Keyboard.keyRelease((char) KeyEvent.VK_LEFT),
                    () -> Perspective.localToCanvas(Microbot.getClient(), actor.getLocalLocation(), Microbot.getClient().getPlane()) != null, 10);
        } else if (getAngleTo(degrees) < -5) {
            Rs2Keyboard.keyHold(KeyEvent.VK_RIGHT);
            Global.awaitExecutionUntil(() -> Rs2Keyboard.keyRelease((char) KeyEvent.VK_RIGHT),
                    () -> Perspective.localToCanvas(Microbot.getClient(), actor.getLocalLocation(), Microbot.getClient().getPlane()) != null, 10);
        }
    }

    public static void setAngle(int degrees, TileObject tileObject) {
        if (getAngleTo(degrees) > 5) {
            Rs2Keyboard.keyHold(KeyEvent.VK_LEFT);
            Global.awaitExecutionUntil(() -> Rs2Keyboard.keyRelease((char) KeyEvent.VK_LEFT),
                    () -> Perspective.localToCanvas(Microbot.getClient(), tileObject.getLocalLocation(), Microbot.getClient().getPlane()) != null, 600);
        } else if (getAngleTo(degrees) < -5) {
            Rs2Keyboard.keyHold(KeyEvent.VK_RIGHT);
            Global.awaitExecutionUntil(() -> Rs2Keyboard.keyRelease((char) KeyEvent.VK_RIGHT),
                    () -> Perspective.localToCanvas(Microbot.getClient(), tileObject.getLocalLocation(), Microbot.getClient().getPlane()) != null, 600);
        }
    }

    public static void setAngle(int degrees, LocalPoint localPoint) {
        if (getAngleTo(degrees) > 5) {
            Rs2Keyboard.keyHold(KeyEvent.VK_LEFT);
            Global.awaitExecutionUntil(() -> Rs2Keyboard.keyRelease((char) KeyEvent.VK_LEFT),
                    () -> Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getPlane()) != null, 600);
        } else if (getAngleTo(degrees) < -5) {
            Rs2Keyboard.keyHold(KeyEvent.VK_RIGHT);
            Global.awaitExecutionUntil(() -> Rs2Keyboard.keyRelease((char) KeyEvent.VK_RIGHT),
                    () -> Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getPlane()) != null, 600);
        }
    }

    public static void setPitch(float percentage) {
        float currentPitchPercentage = cameraPitchPercentage();

        if (currentPitchPercentage < percentage) {
            Rs2Keyboard.keyHold(KeyEvent.VK_UP);
            Global.awaitExecutionUntil(() -> Rs2Keyboard.keyRelease((char) KeyEvent.VK_UP),
                    () -> cameraPitchPercentage() >= percentage, 600);
        } else {
            Rs2Keyboard.keyHold(KeyEvent.VK_DOWN);
            Global.awaitExecutionUntil(() -> Rs2Keyboard.keyRelease((char) KeyEvent.VK_DOWN),
                    () -> cameraPitchPercentage() <= percentage, 600);
        }
    }

    public static float cameraPitchPercentage() {
        int minPitch = 128;
        int maxPitch = 383;
        int currentPitch = Microbot.getClient().getCameraPitch();

        int adjustedPitch = currentPitch - minPitch;
        int adjustedMaxPitch = maxPitch - minPitch;

        return (float) adjustedPitch / (float) adjustedMaxPitch;
    }

    public static int getAngleTo(int degrees) {
        int ca = getAngle();
        if (ca < degrees) {
            ca += 360;
        }
        int da = ca - degrees;
        if (da > 180) {
            da -= 360;
        }
        return da;
    }

    public static int getAngle() {
        // the client uses fixed point radians 0 - 2^14
        // degrees = yaw * 360 / 2^14 = yaw / 45.5111...
        // This leaves it on a scale of 45 versus a scale of 360 so we multiply it by 8 to fix that.
        return (int) Math.abs(Microbot.getClient().getCameraYaw() / 45.51 * 8);
    }

    public static boolean isTileOnScreen(TileObject tileObject) {
        int viewportHeight = Microbot.getClient().getViewportHeight();
        int viewportWidth = Microbot.getClient().getViewportWidth();


        Polygon poly = Perspective.getCanvasTilePoly(Microbot.getClient(), tileObject.getLocalLocation());

        if (poly == null) return false;

        return poly.getBounds2D().getX() <= viewportWidth && poly.getBounds2D().getY() <= viewportHeight;
    }

    public static boolean isTileOnScreen(LocalPoint localPoint) {
        int viewportHeight = Microbot.getClient().getViewportHeight();
        int viewportWidth = Microbot.getClient().getViewportWidth();


        Polygon poly = Perspective.getCanvasTilePoly(Microbot.getClient(), localPoint);

        if (poly == null) return false;

        return poly.getBounds2D().getX() <= viewportWidth && poly.getBounds2D().getY() <= viewportHeight;
    }

    public static void setZoom(int zoom) {
        Microbot.getClientThread().invokeLater(() -> {
            Microbot.getClient().runScript(ScriptID.CAMERA_DO_ZOOM, zoom, zoom);
        });
    }
}
