package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCBehavior;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.function.Supplier;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class DeathRecoveryBehavior implements CCBehavior {

    private static final int DEATH_VARP = 4517;
    private static final int DEATH_REGION = 12633;

    private final Supplier<WorldPoint> activityLocationSupplier;

    public DeathRecoveryBehavior(Supplier<WorldPoint> activityLocationSupplier) {
        this.activityLocationSupplier = activityLocationSupplier;
    }

    @Override public int priority() { return 5; }
    @Override public String name() { return "DeathRecovery"; }

    @Override
    public boolean shouldActivate() {
        return isPlayerDead();
    }

    @Override
    public void execute() {
        log.info("Death detected — recovering");
        // Wait for respawn
        sleepUntil(() -> !isPlayerDead(), 10000);
        sleep(1000);

        // Walk back to activity location
        WorldPoint loc = activityLocationSupplier.get();
        if (loc != null) {
            Rs2Walker.walkTo(loc);
            log.info("Walking back to activity location: {}", loc);
        } else {
            log.warn("No activity location saved — staying at spawn");
        }
    }

    @Override
    public void reset() { /* stateless */ }

    protected boolean isPlayerDead() {
        try {
            return Microbot.getVarbitPlayerValue(DEATH_VARP) == 1
                && Rs2Player.getWorldLocation() != null
                && Rs2Player.getWorldLocation().getRegionID() == DEATH_REGION;
        } catch (Exception e) {
            return false;
        }
    }
}
