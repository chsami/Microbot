package net.runelite.client.plugins.microbot.aiofighter.combat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DodgeProjectileScript extends Script {

    public final ArrayList<Projectile> projectiles = new ArrayList<>();

    public boolean run(AIOFighterConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!config.dodgeProjectiles()) return;
            int cycle = Microbot.getClient().getGameCycle();
            projectiles.removeIf(projectile -> cycle >= projectile.getEndCycle());

            if (projectiles.isEmpty()) return;
            WorldPoint[] dangerousPoints = projectiles.stream().map(Projectile::getTargetPoint).toArray(WorldPoint[]::new);
            WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();

            if (projectiles.stream().anyMatch(p -> p.getTargetPoint().distanceTo(playerLocation) < 2)) {
                WorldPoint safePoint = calculateSafePoint(playerLocation, dangerousPoints);
                Rs2Walker.walkFastCanvas(safePoint);
            }

        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }


    private WorldPoint calculateSafePoint(WorldPoint playerLocation, WorldPoint[] dangerousPoints) {
        // Define the search radius around the player
        int searchRadius = 5;
        int minDistance = Integer.MAX_VALUE;
        WorldPoint bestPoint = playerLocation;

        // Search in a square area around the player
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                WorldPoint candidate = playerLocation.dx(dx).dy(dy);

                // Check if this point is safe (at least 2 tiles away from all dangerous points)
                if (Arrays.stream(dangerousPoints).allMatch(p -> p.distanceTo(candidate) >= 2)) {
                    // If the point is safe, check if it's closer to the player than our current best
                    int distanceToPlayer = candidate.distanceTo(playerLocation);
                    if (distanceToPlayer < minDistance && Rs2Tile.isTileReachable(candidate)) {
                        minDistance = distanceToPlayer;
                        bestPoint = candidate;
                    }
                }
            }
        }

        return bestPoint;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
