package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model;

import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * immutable model for moving hazards with trajectory prediction
 * predicts future positions based on movement towards player
 * used for tornadoes, moving objects, and other dynamic threats
 */
@Value
@Builder(toBuilder = true)
public class MovingHazard {
    int hazardId;
    WorldPoint currentPosition;
    WorldPoint lastPosition;
    int spawnTick;
    int lastUpdateTick;
    int activeDuration;  // total ticks hazard is active (0 = unlimited)
    double movementSpeed;  // tiles per tick
    List<WorldPoint> predictedPath;  // future positions

    /**
     * get remaining ticks until hazard despawns
     * returns Integer.MAX_VALUE if unlimited duration
     */
    public int getRemainingTicks(int currentTick) {
        if (activeDuration <= 0) {
            return Integer.MAX_VALUE;
        }

        int ticksAlive = currentTick - spawnTick;
        return Math.max(0, activeDuration - ticksAlive);
    }

    /**
     * check if hazard is still active
     */
    public boolean isActive(int currentTick) {
        return getRemainingTicks(currentTick) > 0;
    }

    /**
     * get predicted position at future tick
     */
    public WorldPoint getPredictedPosition(int futureTick) {
        if (predictedPath == null || predictedPath.isEmpty()) {
            return currentPosition;
        }

        int ticksInFuture = futureTick - lastUpdateTick;
        if (ticksInFuture < 0) {
            return currentPosition;
        }

        if (ticksInFuture >= predictedPath.size()) {
            // return last predicted position
            return predictedPath.get(predictedPath.size() - 1);
        }

        return predictedPath.get(ticksInFuture);
    }

    /**
     * check if predicted to be at location during tick range
     */
    public boolean willBeAt(WorldPoint location, int startTick, int endTick) {
        for (int tick = startTick; tick <= endTick; tick++) {
            WorldPoint predicted = getPredictedPosition(tick);
            if (predicted.equals(location)) {
                return true;
            }
        }
        return false;
    }

    /**
     * get distance to hazard at specific tick
     */
    public int distanceTo(WorldPoint location, int tick) {
        WorldPoint predicted = getPredictedPosition(tick);
        return predicted.distanceTo(location);
    }

    /**
     * normalize vector (for direction calculation)
     */
    public static double[] normalizeVector(double dx, double dy) {
        double magnitude = Math.sqrt(dx * dx + dy * dy);
        if (magnitude == 0) {
            return new double[]{0, 0};
        }
        return new double[]{dx / magnitude, dy / magnitude};
    }

    /**
     * calculate predicted path towards target
     * updates predicted positions for remaining duration
     */
    public static List<WorldPoint> calculatePredictedPath(
        WorldPoint currentPos,
        WorldPoint targetPos,
        int currentTick,
        int spawnTick,
        int activeDuration,
        double movementSpeed
    ) {
        List<WorldPoint> path = new ArrayList<>();

        // calculate remaining ticks
        int ticksAlive = currentTick - spawnTick;
        int remainingTicks = activeDuration > 0 ? activeDuration - ticksAlive : 50; // default 50 if unlimited

        if (remainingTicks <= 0) {
            return path;
        }

        // calculate direction towards target
        double dx = targetPos.getX() - currentPos.getX();
        double dy = targetPos.getY() - currentPos.getY();
        double[] normalized = normalizeVector(dx, dy);

        // predict positions for remaining duration
        for (int i = 1; i <= remainingTicks; i++) {
            int newX = currentPos.getX() + (int) Math.round(normalized[0] * movementSpeed * i);
            int newY = currentPos.getY() + (int) Math.round(normalized[1] * movementSpeed * i);

            WorldPoint predicted = new WorldPoint(newX, newY, currentPos.getPlane());
            path.add(predicted);
        }

        return path;
    }

    /**
     * create updated moving hazard with new position and predicted path
     */
    public MovingHazard withUpdatedPosition(WorldPoint newPosition, WorldPoint playerPosition, int currentTick) {
        // calculate new predicted path
        List<WorldPoint> newPath = calculatePredictedPath(
            newPosition,
            playerPosition,
            currentTick,
            spawnTick,
            activeDuration,
            movementSpeed
        );

        return this.toBuilder()
            .lastPosition(currentPosition)
            .currentPosition(newPosition)
            .lastUpdateTick(currentTick)
            .predictedPath(newPath)
            .build();
    }
}
