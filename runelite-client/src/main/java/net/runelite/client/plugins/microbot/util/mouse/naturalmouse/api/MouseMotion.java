package net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api;

import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DoublePoint;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.mousemotion.MouseMovement;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.mousemotion.MovementFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.MathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Random;

/**
 * Contains instructions to move cursor smoothly to the destination coordinates from where ever the cursor
 * currently is. The class is reusable, meaning user can keep calling it and the cursor returns in a random,
 * but reliable way, described in this class, to the destination.
 */
public class MouseMotion {
    private static final Logger log = LoggerFactory.getLogger(MouseMotion.class);
    private static final int SLEEP_AFTER_ADJUSTMENT_MS = 2;
    private final int minSteps;
    private final int effectFadeSteps;
    private final int reactionTimeBaseMs;
    private final int reactionTimeVariationMs;
    private final double timeToStepsDivider;
    private final Dimension screenSize;
    private final SystemCalls systemCalls;
    private final DeviationProvider deviationProvider;
    private final NoiseProvider noiseProvider;
    private final SpeedManager speedManager;
    private final OvershootManager overshootManager;
    private final int xDest;
    private final int yDest;
    private final Random random;
    private final MouseInfoAccessor mouseInfo;
    private Point mousePosition;

    /**
     * @param nature the nature that defines how mouse is moved
     * @param xDest  the x-coordinate of destination
     * @param yDest  the y-coordinate of destination
     * @param random the random used for unpredictability
     */
    public MouseMotion(MouseMotionNature nature, Random random, int xDest, int yDest) {
        this.deviationProvider = nature.getDeviationProvider();
        this.noiseProvider = nature.getNoiseProvider();
        this.systemCalls = nature.getSystemCalls();
        this.screenSize = systemCalls.getScreenSize();
        this.xDest = limitByScreenWidth(xDest);
        this.yDest = limitByScreenHeight(yDest);
        this.random = random;
        this.mouseInfo = nature.getMouseInfo();
        this.speedManager = nature.getSpeedManager();
        this.timeToStepsDivider = nature.getTimeToStepsDivider();
        this.minSteps = nature.getMinSteps();
        this.effectFadeSteps = nature.getEffectFadeSteps();
        this.reactionTimeBaseMs = nature.getReactionTimeBaseMs();
        this.reactionTimeVariationMs = nature.getReactionTimeVariationMs();
        this.overshootManager = nature.getOvershootManager();
    }

    /**
     * Blocking call, starts to move the cursor to the specified location from where it currently is.
     *
     * @throws InterruptedException when interrupted
     */
    public void move() throws InterruptedException {
        move((x, y) ->
        {
        });
    }

    /**
     * Blocking call, starts to move the cursor to the specified location from where it currently is.
     * Returns once the cursor reads as arrived or the planned movements are used up, whichever is first.
     *
     * @param observer Provide observer if you are interested receiving the location of mouse on every step
     * @throws InterruptedException when the thread is interrupted, checked before every step
     */
    public void move(MouseMotionObserver observer) throws InterruptedException {
        updateMouseInfo();

        MovementFactory movementFactory = new MovementFactory(xDest, yDest, speedManager, overshootManager, screenSize);
        ArrayDeque<MouseMovement> movements = movementFactory.createMovements(mousePosition);
        // Bug fix: this loop used to re-plan from the current position whenever the plan ran out
        // short of the destination, so a pointer that never moved (every step dropped) never
        // ended. The plan's last movement ends with a correcting step onto the destination, so
        // an undisturbed motion always arrives within its own plan; the bound is that plan,
        // overshoots and reaction pauses included, however slow the profile.
        while ((mousePosition.x != xDest || mousePosition.y != yDest) && !movements.isEmpty()) {
            MouseMovement movement = movements.removeFirst();
            double distance = movement.distance;
            long mouseMovementMs = movement.time;
            Flow flow = movement.flow;
            double xDistance = movement.xDistance;
            double yDistance = movement.yDistance;

      /* Number of steps is calculated from the movement time and limited by minimal amount of steps
         (should have at least MIN_STEPS) and distance (shouldn't have more steps than pixels travelled) */
            int steps = (int) Math.ceil(Math.min(distance, Math.max(mouseMovementMs / timeToStepsDivider, minSteps)));

            long startTime = systemCalls.currentTimeMillis();
            long stepTime = (long) (mouseMovementMs / (double) steps);

            updateMouseInfo();
            double simulatedMouseX = mousePosition.x;
            double simulatedMouseY = mousePosition.y;

            double deviationMultiplierX = (random.nextDouble() - 0.5) * 2;
            double deviationMultiplierY = (random.nextDouble() - 0.5) * 2;

            double completedXDistance = 0;
            double completedYDistance = 0;
            double noiseX = 0;
            double noiseY = 0;

            for (int i = 0; i < steps; i++) {
                // All steps take equal amount of time. This is a value from 0...1 describing how far along the process is.
                double timeCompletion = i / (double) steps;

                double effectFadeStep = Math.max(i - (steps - effectFadeSteps) + 1, 0);
                // value from 0 to 1, when effectFadeSteps remaining steps, starts to decrease to 0 linearly
                // This is here so noise and deviation wouldn't add offset to mouse final position, when we need accuracy.
                double effectFadeMultiplier = (effectFadeSteps - effectFadeStep) / effectFadeSteps;

                double xStepSize = flow.getStepSize(xDistance, steps, timeCompletion);
                double yStepSize = flow.getStepSize(yDistance, steps, timeCompletion);

                completedXDistance += xStepSize;
                completedYDistance += yStepSize;
                double completedDistance = Math.hypot(completedXDistance, completedYDistance);
                double completion = Math.min(1, completedDistance / distance);

                DoublePoint noise = noiseProvider.getNoise(random, xStepSize, yStepSize);
                DoublePoint deviation = deviationProvider.getDeviation(distance, completion);

                noiseX += noise.getX();
                noiseY += noise.getY();
                simulatedMouseX += xStepSize;
                simulatedMouseY += yStepSize;

                long endTime = startTime + stepTime * (i + 1);
                int mousePosX = MathUtil.roundTowards(simulatedMouseX + deviation.getX() * deviationMultiplierX * effectFadeMultiplier + noiseX * effectFadeMultiplier, movement.destX);

                int mousePosY = MathUtil.roundTowards(simulatedMouseY + deviation.getY() * deviationMultiplierY * effectFadeMultiplier + noiseY * effectFadeMultiplier, movement.destY);

                mousePosX = limitByScreenWidth(mousePosX);
                mousePosY = limitByScreenHeight(mousePosY);

                setMousePosition(mousePosX, mousePosY);

                // Allow other action to take place or just observe, we'll later compensate by sleeping less.
                observer.observe(mousePosX, mousePosY);

                long timeLeft = endTime - systemCalls.currentTimeMillis();
                sleepAround(Math.max(timeLeft, 0), 0);
            }
            updateMouseInfo();

            if (mousePosition.x != movement.destX || mousePosition.y != movement.destY) {
                // It's possible that mouse is manually moved or for some other reason.
                // Let's start next step from pre-calculated location to prevent errors from accumulating.
                // But print warning as this is not expected behavior.
                setMousePosition(movement.destX, movement.destY);
                // Let's wait a bit before getting mouse info.
                sleepAround(SLEEP_AFTER_ADJUSTMENT_MS, 0);
                updateMouseInfo();
            }

            if (mousePosition.x != xDest || mousePosition.y != yDest) {
                // We are dealing with overshoot, let's sleep a bit to simulate human reaction time.
                sleepAround(reactionTimeBaseMs, reactionTimeVariationMs);
            }
        }
    }

    // Bug fix: nothing here honored the interrupt this method declares, and SystemCalls.sleep may
    // return at once for an interrupted thread, so the rest of the trajectory went out in a burst.
    private void setMousePosition(int x, int y) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        systemCalls.setMousePosition(x, y);
    }

    private int limitByScreenWidth(int value) {
        return Math.max(0, Math.min(screenSize.width - 1, value));
    }

    private int limitByScreenHeight(int value) {
        return Math.max(0, Math.min(screenSize.height - 1, value));
    }

    private void sleepAround(long sleepMin, long randomPart) {
        long sleepTime = (long) (sleepMin + random.nextDouble() * randomPart);
        if (log.isTraceEnabled() && sleepTime > 0) {
            updateMouseInfo();
        }
        systemCalls.sleep(sleepTime);
    }

    private void updateMouseInfo() {
        mousePosition = mouseInfo.getMousePosition();
    }

}
