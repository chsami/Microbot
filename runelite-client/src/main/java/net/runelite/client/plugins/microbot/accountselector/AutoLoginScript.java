package net.runelite.client.plugins.microbot.accountselector;

import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.http.api.worlds.WorldRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class AutoLoginScript extends Script {

    private List<WorldRegion> getAllowedRegions(AutoLoginConfig config) {
        List<WorldRegion> allowedRegions = new ArrayList<>();

        if (config.allowUK()) {
            allowedRegions.add(WorldRegion.UNITED_KINGDOM);
        }
        if (config.allowUS()) {
            allowedRegions.add(WorldRegion.UNITED_STATES_OF_AMERICA);
        }
        if (config.allowGermany()) {
            allowedRegions.add(WorldRegion.GERMANY);
        }
        if (config.allowAustralia()) {
            allowedRegions.add(WorldRegion.AUSTRALIA);
        }

        return allowedRegions;
    }

    private int getRandomWorldWithRegionFilter(AutoLoginConfig config) {
        List<WorldRegion> allowedRegions = getAllowedRegions(config);

        if (allowedRegions.isEmpty()) {
            // If no regions allowed, use default method
            return Login.getRandomWorld(config.isMember());
        }

        // Pick a random region from allowed regions
        Random random = new Random();
        WorldRegion selectedRegion = allowedRegions.get(random.nextInt(allowedRegions.size()));

        return Login.getRandomWorld(config.isMember(), selectedRegion);
    }

    public boolean run(AutoLoginConfig autoLoginConfig) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (BreakHandlerScript.isBreakActive() || BreakHandlerScript.isMicroBreakActive()) return;

                if (Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN) {
                    if (autoLoginConfig.useRandomWorld()) {
                        final int world = getRandomWorldWithRegionFilter(autoLoginConfig);
                        Microbot.log(Level.INFO, String.format("Auto-logging into random %s world: %d", autoLoginConfig.isMember() ? "member" : "free", world));
                        new Login(world);
                    } else {
                        Microbot.log(Level.INFO, String.format("Auto-logging into world: %d", autoLoginConfig.world()));
                        new Login(autoLoginConfig.world());
                    }
                    sleep(5000);
                }


            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }
}
