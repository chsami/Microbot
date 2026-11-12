package net.runelite.client.plugins.microbot.util.mouse.naturalmouse;

import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseInfoAccessor;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SystemCalls;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultMouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultSpeedManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FactoryTemplates;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;

import javax.inject.Inject;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NaturalMouse {
    /** How late a trajectory's wait between steps notices a pause or a takeover. */
    private static final long STEP_WAIT_SLICE_MS = 10;
    public final MouseMotionNature nature;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    @Inject
    private Client client;
    @Getter
    @Setter
    private List<Flow> flows = List.of(
            new Flow(FlowTemplates.variatingFlow()),
            new Flow(FlowTemplates.slowStartupFlow()),
            new Flow(FlowTemplates.slowStartup2Flow()),
            new Flow(FlowTemplates.jaggedFlow()),
            new Flow(FlowTemplates.interruptedFlow()),
            new Flow(FlowTemplates.interruptedFlow2()),
            new Flow(FlowTemplates.stoppingFlow()),
            new Flow(FlowTemplates.adjustingFlow()),
            new Flow(FlowTemplates.random())
    );

    private volatile CachedFactory cachedFactory;

    @Value
    private static class FactoryKey {
        ActivityIntensity intensity;
        boolean simulateFatigue;
        boolean simulateMistakes;
        long fatigueStepMs;
    }

    @Value
    private static class CachedFactory {
        FactoryKey key;
        MouseMotionFactory factory;
    }

    @Inject
    public NaturalMouse() {
        nature = new DefaultMouseMotionNature();
        nature.setSystemCalls(new SystemCallsImpl());
        nature.setMouseInfo(new MouseInfoImpl());
    }

    public void moveTo(int dx, int dy) {
//		if(Rs2UiHelper.isStretchedEnabled())
//		{
//			dx = Rs2UiHelper.stretchX(dx);
//			dy = Rs2UiHelper.stretchY(dy);
//		}
        int finalDx = dx;
        int finalDy = dy;
        Point mousePos = Microbot.getMouse().getMousePosition();
        // check if current mouse position is already at the destination
        if (mousePos.x == finalDx && mousePos.y == finalDy) {
            return;
        }

        long generation = ScriptPauseController.getGeneration();
        if (!Microbot.getClient().isClientThread()) {
            inTurn(generation, () -> move(finalDx, finalDy));
        } else {
            executorService.submit(() -> inTurn(generation, () -> move(finalDx, finalDy)));
        }
    }

    /**
     * Bug fix: a trajectory started outside any gesture (a hover helper, an antiban off-screen or
     * random move, a plugin calling moveTo) used to take only this object's monitor, so its steps
     * landed between another thread's click MOVED and PRESSED. Run as a gesture of its own, it takes
     * turns with clicks on InputLoop's lock. Inside a gesture the lock is already this thread's.
     *
     * <p>InputLoop's lock is the only lock on any trajectory path, so no lock order can invert. A
     * bare trajectory that cannot get the lock within InputLoop's timeout, or starts during a takeover
     * or pause, returns without dispatching.
     *
     * <p>Bug fix: {@code generation} is the pause generation when the trajectory was requested. A
     * request deferred from the client thread used to take its generation only when its turn came,
     * so one made before a pause and queued behind another trajectory ran after the resume.
     */
    private static void inTurn(long generation, Runnable trajectory) {
        if (InputLoop.isInGesture()) {
            trajectory.run();
        } else {
            InputLoop.run(generation, emit -> trajectory.run());
        }
    }

    /**
     * Always inside a gesture (see inTurn). Each step goes out through the emitter, whose refusal
     * of a step, for a pause, a takeover or an interrupt, ends the gesture right there, so nothing
     * after the trajectory goes out: not a click's press, not a drag's release point, not
     * moveOffScreen's crossing.
     *
     * <p>Bug fix: a stopped trajectory used to be stopped by one check and its gesture ended by a
     * second look at the input state afterwards. A takeover that lapsed in between let the gesture
     * carry on, jumping the pointer to the target and pressing.
     */
    private void move(int dx, int dy) {
        var motion = getFactory().build(dx, dy);
        try {
            motion.move();
        } catch (InterruptedException e) {
            // Kept set, so the gesture's next emit is refused and ends it.
            Thread.currentThread().interrupt();
        }
    }

    public MouseMotionFactory getFactory() {
        ActivityIntensity intensity = Rs2Antiban.getActivityIntensity();
        FactoryKey key = new FactoryKey(intensity, Rs2AntibanSettings.simulateFatigue, Rs2AntibanSettings.simulateMistakes,
                Rs2AntibanSettings.simulateFatigue ? (long) (AntibanPlugin.ticksSinceLogin * Rs2Antiban.mouseFatigue.increaseRate) : 0);
        CachedFactory cached = cachedFactory;
        if (cached != null && cached.key.equals(key)) {
            return cached.factory;
        }
        MouseMotionFactory factory;
        if (intensity == ActivityIntensity.VERY_LOW) {
            log.debug("Creating average computer user motion factory");
            factory = FactoryTemplates.createAverageComputerUserMotionFactory(nature);
        } else if (intensity == ActivityIntensity.LOW) {
            log.debug("Creating normal gamer motion factory");
            factory = FactoryTemplates.createNormalGamerMotionFactory(nature);
        } else if (intensity == ActivityIntensity.MODERATE) {
            log.debug("Creating fast gamer motion factory");
            factory = FactoryTemplates.createFastGamerMotionFactory(nature);
        } else if (intensity == ActivityIntensity.HIGH) {
            log.debug("Creating fast gamer motion factory");
            factory = FactoryTemplates.createFastGamerMotionFactory(nature);
        } else if (intensity == ActivityIntensity.EXTREME) {
            log.debug("Creating super fast gamer motion factory");
            factory = FactoryTemplates.createSuperFastGamerMotionFactory(nature);
        } else {
            log.debug("Default: Creating super fast gamer motion factory");
            factory = FactoryTemplates.createSuperFastGamerMotionFactory(nature);
        }
        cachedFactory = new CachedFactory(key, factory);
        return factory;

//		var factory = new MouseMotionFactory();
//		factory.setNature(nature);
//
//		var manager = new SpeedManagerImpl(flows);
//		factory.setDeviationProvider(new SinusoidalDeviationProvider(15.0));
//		factory.setNoiseProvider(new DefaultNoiseProvider(2.0));
//		factory.getNature().setReactionTimeVariationMs(120);
//		manager.setMouseMovementBaseTimeMs(130);
//
//		var overshootManager = (DefaultOvershootManager) factory.getOvershootManager();
//		overshootManager.setOvershoots(2);
//		factory.setSpeedManager(manager);
//
//		return factory;
    }

    /**
     * Moves the mouse off screen with a default 100% chance.
     * This method will always move the mouse off screen when called.
     */
    public void moveOffScreen() {
        // Always move the mouse off screen (default behavior)
        moveOffScreen(100.0); // Calls the overloaded method with a 100% chance
    }

    /**
     * Moves the mouse off screen based on a given percentage chance.
     *
     * @param chancePercentage the chance (in percentage) to move the mouse off screen.
     *                         This value should be between 0.0 and 100.0 (inclusive).
     *                         Note: This parameter should not be a fractional value between
     *                         0.0 and 0.99; use values representing a whole percentage (e.g., 25.0, 50.0).
     */
    public void moveOffScreen(double chancePercentage) {
        if (chancePercentage < 100 && !Rs2Random.dicePercentage(chancePercentage)) {
            return;
        }

        // Bug fix: these draws used a ThreadLocalRandom stored when this object was built. Its state
        // lives in whichever thread calls it, and only current() seeds that state, so a thread that
        // had not called current() itself drew a fixed sequence and left at a fixed point.
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int horizontal = random.nextBoolean() ? -1 : client.getCanvasWidth() + 1;
        int vertical = random.nextBoolean() ? -1 : client.getCanvasHeight() + 1;

        boolean exitHorizontally = random.nextBoolean();
        int targetX = exitHorizontally ? horizontal : random.nextInt(0, client.getCanvasWidth() + 1);
        int targetY = exitHorizontally ? random.nextInt(0, client.getCanvasHeight() + 1) : vertical;

        Runnable travelThenCross = () -> {
            // MouseMotion clamps to the canvas, so this reaches the edge and the next call crosses.
            // A travel cut short ends the gesture inside move, so it never crosses.
            move(targetX, targetY);
            Microbot.getMouse().move(targetX, targetY);
        };

        // One task and one turn: moveTo is async on the client thread, where two calls would cross
        // first, and a click must not land between the travel and the crossing.
        long generation = ScriptPauseController.getGeneration();
        if (Microbot.getClient().isClientThread()) {
            executorService.submit(() -> inTurn(generation, travelThenCross));
        } else {
            inTurn(generation, travelThenCross);
        }
    }

    // Move to a random point on the screen
    public void moveRandom() {
        // current() on the calling thread, for the reason in moveOffScreen.
        ThreadLocalRandom random = ThreadLocalRandom.current();
        moveTo(random.nextInt(0, client.getCanvasWidth() + 1), random.nextInt(0, client.getCanvasHeight() + 1));
    }

    private static class SpeedManagerImpl extends DefaultSpeedManager {
        private SpeedManagerImpl(Collection<Flow> flows) {
            super(flows);
        }

        @Override
        public Pair<Flow, Long> getFlowWithTime(double distance) {
            var pair = super.getFlowWithTime(distance);
            return new Pair<>(pair.x, pair.y);
        }
    }

    private static class MouseInfoImpl implements MouseInfoAccessor {
        @Override
        public Point getMousePosition() {
            return Microbot.getMouse().getMousePosition();
        }
    }

    private class SystemCallsImpl implements SystemCalls {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        /**
         * Not Global.sleep, which parks through a pause while this trajectory holds the pointer's
         * turn. The wait is only cut short here; what ends the trajectory is its next step, which the
         * emitter refuses.
         *
         * <p>Bug fix: the client thread never waits, as Global.sleep already ensured. A motion built
         * from getFactory() and run there slept between its steps once this wait replaced it.
         */
        @Override
        public void sleep(long time) {
            if (Microbot.getClient().isClientThread()) {
                return;
            }
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(time);
            long left;
            while ((left = deadline - System.nanoTime()) > 0) {
                if (ScriptPauseController.isPaused() || InputArbiter.isHuman()) {
                    return;
                }
                try {
                    Thread.sleep(Math.min(TimeUnit.NANOSECONDS.toMillis(left) + 1, STEP_WAIT_SLICE_MS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        @Override
        public Dimension getScreenSize() {
            Client client = Microbot.getClient();
            return new Dimension(client.getCanvasWidth(), client.getCanvasHeight());
        }

        @Override
        public void setMousePosition(int x, int y) {
            Microbot.getMouse().move(x, y);

        }
    }
}
