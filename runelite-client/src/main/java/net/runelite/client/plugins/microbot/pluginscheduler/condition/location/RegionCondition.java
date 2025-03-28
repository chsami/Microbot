package net.runelite.client.plugins.microbot.pluginscheduler.condition.location;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Condition that is met when the player enters a specific region
 */
@Slf4j
public class RegionCondition implements Condition {
    @Getter
    private final Set<Integer> targetRegions;
    private boolean isInRegion = false;
    private boolean registered = false;

    /**
     * Create a condition that is met when the player enters any of the specified regions
     * 
     * @param regionIds The region IDs to check for
     */
    public RegionCondition(int... regionIds) {
        this.targetRegions = new HashSet<>();
        for (int id : regionIds) {
            targetRegions.add(id);
        }
    }

    @Override
    public boolean isSatisfied() {
        if (!registered) {
            Microbot.getEventBus().register(this);
            registered = true;
            // Check immediately on first call
            checkRegion();
        }
        return isInRegion;
    }

    @Override
    public void reset() {
        isInRegion = false;
    }
    @Override
    public void reset(boolean randomize) {
        reset();
    }

    @Override
    public ConditionType getType() {
        return ConditionType.LOCATION;
    }

    @Override
    public String getDescription() {
        WorldPoint location = Rs2Player.getWorldLocation();
        String currentRegionInfo = "";
        
        if (location != null) {
            int currentRegion = location.getRegionID();
            boolean inTargetRegion = targetRegions.contains(currentRegion);
            currentRegionInfo = String.format(" (current region: %d, %s)", 
                    currentRegion, inTargetRegion ? "matched" : "not matched");
        }
        
        return "Player in regions: " + Arrays.toString(targetRegions.toArray()) + currentRegionInfo;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        checkRegion();
    }

    private void checkRegion() {
        Client client = Microbot.getClient();
        if (client == null || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        try {
            WorldPoint location = Rs2Player.getWorldLocation();
            if (location != null) {
                int currentRegion = location.getRegionID();
                isInRegion = targetRegions.contains(currentRegion);
                
                if (isInRegion) {
                    log.debug("Player entered target region: {}", currentRegion);
                }
            }
        } catch (Exception e) {
            log.error("Error checking player region", e);
        }
    }

  
}