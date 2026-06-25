package net.runelite.client.plugins.microbot.util.camera;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.camera.CameraPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.awt.*;
import java.awt.event.KeyEvent;

@Slf4j
public class Rs2Camera {
    private static final NpcTracker NPC_TRACKER = new NpcTracker();
    private static final int LEGACY_ANGLE_UNITS = 2048;
    private static final int CLIENT_ANGLE_SCALE = 8;
    private static final int LEGACY_MIN_PITCH = 128;
    private static final int LEGACY_MAX_PITCH = 383;

    public static int angleToTile(Actor t) {
        return Microbot.getClientThread().invoke(() -> {
            var playerLocation = new Rs2PlayerModel().getWorldLocation();
            int angle = (int) Math.toDegrees(Math.atan2(t.getWorldLocation().getY() - playerLocation.getY(),
                    t.getWorldLocation().getX() - playerLocation.getX()));
            return angle >= 0 ? angle : 360 + angle;
        });
    }

    public static int angleToTile(TileObject t) {
        return Microbot.getClientThread().invoke(() -> {
            var playerLocation = new Rs2PlayerModel().getWorldLocation();
            int angle = (int) Math.toDegrees(Math.atan2(t.getWorldLocation().getY() - playerLocation.getY(),
                    t.getWorldLocation().getX() - playerLocation.getX()));
            return angle >= 0 ? angle : 360 + angle;
        });
    }

    public static int angleToTile(LocalPoint localPoint) {
        return Microbot.getClientThread().invoke(() -> {
            var playerLocation = new Rs2PlayerModel().getWorldLocation();
            int angle = (int) Math.toDegrees(Math.atan2(localPoint.getY() - playerLocation.getY(),
                    localPoint.getX() - playerLocation.getX()));
            return angle >= 0 ? angle : 360 + angle;
        });
    }

    public static int angleToTile(WorldPoint worldPoint) {
        return Microbot.getClientThread().invoke(() -> {
            var playerLocation = new Rs2PlayerModel().getWorldLocation();
            int angle = (int) Math.toDegrees(Math.atan2(worldPoint.getY() - playerLocation.getY(),
                    worldPoint.getX() - playerLocation.getX()));
            return angle >= 0 ? angle : 360 + angle;
        });
    }

    public static void turnTo(final Actor actor) {
        int angle = getCharacterAngle(actor);
        setAngle(angle, 40);
    }

    public static void turnTo(final Actor actor, int maxAngle) {
        int angle = getCharacterAngle(actor);
        setAngle(angle, maxAngle);
    }

    public static void turnTo(final TileObject tileObject) {
        int angle = getObjectAngle(tileObject);
        setAngle(angle, 40);
    }

    public static void turnTo(final TileObject tileObject, int maxAngle) {
        int angle = getObjectAngle(tileObject);
        setAngle(angle, maxAngle);
    }

    public static void turnTo(final LocalPoint localPoint) {
        int angle = (angleToTile(localPoint) - 90) % 360;
        setAngle(angle, 40);
    }

    public static void turnTo(final LocalPoint localPoint, int maxAngle) {
        int angle = (angleToTile(localPoint) - 90) % 360;
        setAngle(angle, maxAngle);
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

    /**
     * <h1> Checks if the angle to the target is within the desired max angle </h1>
     * <p>
     * The desired max angle should not go over 80-90 degrees as the target will be out of view
     *
     * @param targetAngle     the angle to the target
     * @param desiredMaxAngle the maximum angle to the target (Should be a positive number)
     * @return true if the angle to the target is within the desired max angle
     */
    public static boolean isAngleGood(int targetAngle, int desiredMaxAngle) {
        return Math.abs(getAngleTo(targetAngle)) <= desiredMaxAngle;
    }

    public static void setAngle(int targetDegrees, int maxAngle) {
        // Default camera speed is 1
        double defaultCameraSpeed = 1f;

        // If the camera plugin is enabled, get the camera speed from the config in case it has been changed
        if (Microbot.isPluginEnabled(CameraPlugin.class)) {
            String configGroup = "zoom";
            String configKey = "cameraSpeed";
            defaultCameraSpeed = Microbot.getInjector().getInstance(ConfigManager.class).getConfiguration(configGroup, configKey, double.class);
        }
        // Set the camera speed to 3 to make the camera move faster
        Microbot.getClient().setCameraSpeed(3f);

        if (getAngleTo(targetDegrees) > maxAngle) {
            Rs2Keyboard.keyHold(KeyEvent.VK_LEFT);
            Global.sleepUntilTrue(() -> Math.abs(getAngleTo(targetDegrees)) <= maxAngle, 50, 5000);
            Rs2Keyboard.keyRelease(KeyEvent.VK_LEFT);
        } else if (getAngleTo(targetDegrees) < -maxAngle) {
            Rs2Keyboard.keyHold(KeyEvent.VK_RIGHT);
            Global.sleepUntilTrue(() -> Math.abs(getAngleTo(targetDegrees)) <= maxAngle, 50, 5000);
            Rs2Keyboard.keyRelease(KeyEvent.VK_RIGHT);
        }
        Microbot.getClient().setCameraSpeed((float) defaultCameraSpeed);
    }

    public static void adjustPitch(float percentage) {
        float currentPitchPercentage = cameraPitchPercentage();

        if (currentPitchPercentage < percentage) {
            Rs2Keyboard.keyHold(KeyEvent.VK_UP);
            Global.sleepUntilTrue(() -> cameraPitchPercentage() >= percentage, 50, 5000);
            Rs2Keyboard.keyRelease(KeyEvent.VK_UP);
        } else {
            Rs2Keyboard.keyHold(KeyEvent.VK_DOWN);
            Global.sleepUntilTrue(() -> cameraPitchPercentage() <= percentage, 50, 5000);
            Rs2Keyboard.keyRelease(KeyEvent.VK_DOWN);
        }
    }

    public static int getPitch() {
        return fromClientAngleUnits(Microbot.getClient().getCameraPitch());
    }

    // set camera pitch
    public static void setPitch(int pitch) {
        smoothTo(pitch, true);
    }

    public static void setPitchInstant(int pitch) {
        pitch = Math.max(LEGACY_MIN_PITCH, Math.min(LEGACY_MAX_PITCH, pitch));
        Microbot.getClient().setCameraPitchTarget(toClientAngleUnits(pitch));
    }

    public static float cameraPitchPercentage() {
        int currentPitch = getPitch();

        int adjustedPitch = currentPitch - LEGACY_MIN_PITCH;
        int adjustedMaxPitch = LEGACY_MAX_PITCH - LEGACY_MIN_PITCH;

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
        return (int) (getYaw() * (360.0 / LEGACY_ANGLE_UNITS));
    }

    /**
     * Calculates the CameraYaw based on the given NPC or object angle.
     *
     * @param npcAngle the angle of the NPC or object relative to the player (0-359 degrees)
     * @return the calculated CameraYaw (0-2047)
     */
    public static int calculateCameraYaw(int npcAngle) {
        // Convert the NPC angle to CameraYaw using the derived formula
        return (1536 + (int) Math.round(npcAngle * (LEGACY_ANGLE_UNITS / 360.0))) % LEGACY_ANGLE_UNITS;
    }

    /**
     * Track the NPC with the camera
     *
     * @param npcId the ID of the NPC to track
     */
    public static void trackNpc(int npcId) {
        NPC_TRACKER.startTracking(npcId);
    }

    /**
     * Stop tracking the NPC with the camera
     */
    public static void stopTrackingNpc() {
        NPC_TRACKER.stopTracking();
    }

    /**
     * Checks if a NPC is being tracked
     *
     * @return true if a NPC is being tracked, false otherwise
     */
    public static boolean isTrackingNpc() {
        return NPC_TRACKER.isTracking();
    }

    public static boolean isTileOnScreen(TileObject tileObject) {
        int viewportHeight = Microbot.getClient().getViewportHeight();
        int viewportWidth = Microbot.getClient().getViewportWidth();


        Polygon poly = Perspective.getCanvasTilePoly(Microbot.getClient(), tileObject.getLocalLocation());

        if (poly == null) return false;

        return poly.getBounds2D().getX() <= viewportWidth && poly.getBounds2D().getY() <= viewportHeight;
    }

    public static boolean isTileOnScreen(LocalPoint localPoint) {
        Client client = Microbot.getClient();
        int viewportHeight = client.getViewportHeight();
        int viewportWidth = client.getViewportWidth();

        Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
        if (poly == null) return false;

        // Check if any part of the polygon intersects with the screen bounds
        Rectangle viewportBounds = new Rectangle(0, 0, viewportWidth, viewportHeight);
        if (!poly.intersects(viewportBounds)) return false;

        // Optionally, check if the tile is in front of the camera
        Point canvasPoint = Perspective.localToCanvas(client, localPoint, client.getPlane());
        return canvasPoint != null;
    }

    // get the camera zoom
    public static int getZoom() {
        return Microbot.getClient().getVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT);
    }

    public static void setZoom(int zoom) {
        Microbot.getClientThread().invokeLater(() -> {
            Microbot.getClient().runScript(ScriptID.CAMERA_DO_ZOOM, zoom, zoom);
        });
    }
    // Get camera/compass facing
    public static int getYaw() {
        return fromClientAngleUnits(Microbot.getClient().getCameraYaw());
    }

    // Set camera/compass facing
    // North = 0, 2048
    // East = 1536
    // South = 1024
    // West = 512

    public static void setYaw(int yaw) {
        if (yaw < 0 || yaw >= LEGACY_ANGLE_UNITS) return;
        smoothTo(yaw, false);
    }

    public static void setYawInstant(int yaw) {
        if (yaw >= 0 && yaw < LEGACY_ANGLE_UNITS) {
            Microbot.getClient().setCameraYawTarget(toClientAngleUnits(yaw));
        }
    }

    static int toClientAngleUnits(int legacyAngle) {
        return legacyAngle * CLIENT_ANGLE_SCALE;
    }

    static int fromClientAngleUnits(int clientAngle) {
        return clientAngle / CLIENT_ANGLE_SCALE;
    }

    private static void setCameraTargetOnClientThread(int target, boolean isPitch) {
        int clientTarget = toClientAngleUnits(target);
        Runnable update = () -> {
            if (isPitch) {
                Microbot.getClient().setCameraPitchTarget(clientTarget);
            } else {
                Microbot.getClient().setCameraYawTarget(clientTarget);
            }
        };

        if (Microbot.getClient().isClientThread()) {
            update.run();
        } else {
            Microbot.getClientThread().invokeLater(update);
        }
    }

    static final int SMOOTH_MIN_DURATION_MS = 220;
    static final int SMOOTH_MAX_DURATION_MS = 780;
    static final int SMOOTH_STEPS = 10;

    // Short rotation hops (<3 units) are below the noise floor of the internal camera speed
    // and would be invisible anyway, so skip smoothing for those — avoids a 200ms stall on no-ops.
    private static void smoothTo(int target, boolean isPitch) {
        int start = isPitch ? getPitch() : getYaw();
        int clampedTarget;
        if (isPitch) {
            clampedTarget = Math.max(LEGACY_MIN_PITCH, Math.min(LEGACY_MAX_PITCH, target));
        } else {
            clampedTarget = Math.max(0, Math.min(LEGACY_ANGLE_UNITS - 1, target));
        }
        int delta = shortestDelta(clampedTarget - start, isPitch);
        if (Math.abs(delta) < 3 || Microbot.getClient().isClientThread()) {
            setCameraTargetOnClientThread(clampedTarget, isPitch);
            return;
        }
        int totalMs = Rs2Random.logNormalBounded(SMOOTH_MIN_DURATION_MS, SMOOTH_MAX_DURATION_MS);
        int stepMs = Math.max(1, totalMs / SMOOTH_STEPS);
        for (int i = 1; i <= SMOOTH_STEPS; i++) {
            double t = i / (double) SMOOTH_STEPS;
            double eased = easeInOut(t);
            int next = start + (int) Math.round(delta * eased);
            if (!isPitch) {
                next = ((next % LEGACY_ANGLE_UNITS) + LEGACY_ANGLE_UNITS) % LEGACY_ANGLE_UNITS;
            }
            setCameraTargetOnClientThread(next, isPitch);
            if (i < SMOOTH_STEPS) Global.sleep(stepMs);
        }
    }

    private static int shortestDelta(int rawDelta, boolean isPitch) {
        if (isPitch) return rawDelta;
        int d = ((rawDelta % LEGACY_ANGLE_UNITS) + LEGACY_ANGLE_UNITS) % LEGACY_ANGLE_UNITS;
        return d > LEGACY_ANGLE_UNITS / 2 ? d - LEGACY_ANGLE_UNITS : d;
    }

    private static double easeInOut(double t) {
        return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    }

    /**
     * Resets the camera pitch to 280 if it is currently less than 280.
     */
    public static void resetPitch() {
        // Set the camera pitch to 280
        if (getPitch() < 280)
            setPitch(280);
    }

    /**
     * Resets the camera zoom to 200 if it is currently greater than 200.
     */
    public static void resetZoom() {
        // Set the camera zoom to 200
        if (getZoom() > 200)
            setZoom(200);
    }

    /**
     * Determines whether the specified tile is centered on the screen within a given tolerance.
     * <p>
     * Projects the tile to screen space, computes its bounding rectangle, and then checks
     * whether that rectangle lies entirely inside a centered “box” whose width and height
     * are the given percentage of the viewport dimensions.
     * </p>
     *
     * @param tile             the local tile coordinate to test (may not be null)
     * @param marginPercentage the size of the centered tolerance box, expressed as a percentage
     *                         of the viewport (e.g. 10.0 for 10%)
     * @return {@code true} if the tile’s screen bounds lie entirely within the centered margin box;
     * {@code false} if the tile cannot be projected or lies outside that box
     */
    public static boolean isTileCenteredOnScreen(LocalPoint tile, double marginPercentage) {
        Polygon poly = Perspective.getCanvasTilePoly(Microbot.getClient(), tile);
        if (poly == null) return false;

        Rectangle tileBounds = poly.getBounds();
        int viewportWidth = Microbot.getClient().getViewportWidth();
        int viewportHeight = Microbot.getClient().getViewportHeight();
        int centerX = viewportWidth / 2;
        int centerY = viewportHeight / 2;

        int marginX = (int) (viewportWidth * (marginPercentage / 100.0));
        int marginY = (int) (viewportHeight * (marginPercentage / 100.0));

        Rectangle centerBox = new Rectangle(
                centerX - marginX / 2,
                centerY - marginY / 2,
                marginX,
                marginY
        );

        return centerBox.contains(tileBounds);
    }

    /**
     * Determines whether the specified tile is centered on the screen, using a default
     * margin tolerance of 10%.
     *
     * @param tile the local tile coordinate to test (may not be null)
     * @return {@code true} if the tile’s screen bounds lie entirely within the centered
     * 10% margin box; {@code false} otherwise
     * @see #isTileCenteredOnScreen(LocalPoint, double)
     */
    public static boolean isTileCenteredOnScreen(LocalPoint tile) {
        return isTileCenteredOnScreen(tile, 10);
    }

    /**
     * Rotates the camera to center on the specified tile, if it is not already within
     * the given margin tolerance.
     * <p>
     * Computes the bearing from the camera to the tile, adjusts it into a [0–360) range,
     * and then issues a small-angle camera turn if {@link #isTileCenteredOnScreen(LocalPoint, double)}
     * returns {@code false}.
     * </p>
     *
     * @param tile             the local tile coordinate to center on (may not be null)
     * @param marginPercentage the size of the centered tolerance box, expressed as a percentage
     *                         of the viewport (e.g. 10.0 for 10%)
     * @see #angleToTile(LocalPoint)
     * @see #setAngle(int, int)
     */
    public static void centerTileOnScreen(LocalPoint tile, double marginPercentage) {
        // Calculate the desired camera angle for the tile
        int rawAngle = angleToTile(tile) - 90;
        int angle = rawAngle < 0 ? rawAngle + 360 : rawAngle;
        // Center if not already within margin
        if (!isTileCenteredOnScreen(tile, marginPercentage)) {
            setAngle(angle, 5); // Use small max angle for precision
        }
    }

    /**
     * Rotates the camera to center on the specified tile, using a default
     * margin tolerance of 10%.
     *
     * @param tile the local tile coordinate to center on (may not be null)
     * @see #centerTileOnScreen(LocalPoint, double)
     */
    public static void centerTileOnScreen(LocalPoint tile) {
        centerTileOnScreen(tile, 10.0);
    }
}

