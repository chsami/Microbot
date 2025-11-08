package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Constants;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2CombatHandler;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * pvm-specific utility methods for boss fights
 * 
 * provides helpers for:
 * - widget-based boss health/state tracking
 * - multi-target priority systems
 * - bailout and emergency teleport logic
 * - consumable management for extended fights
 * 
 * usage:
 * <pre>
 * // boss health tracking
 * if (Rs2PvMUtil.getBossHealthPercent(BOSS_HEALTH_WIDGET) < 30) {
 *     log.info("Boss entering final phase");
 * }
 * 
 * // multi-target priority
 * Rs2NpcModel target = Rs2PvMUtil.getNearestTarget(
 *     Arrays.asList(NpcID.JAGUAR, NpcID.BLOOD_MOON),
 *     npc -> npc.getHealthRatio() > 0
 * );
 * 
 * // emergency bailout
 * if (Rs2Player.getCurrentHealthPercent() < 20) {
 *     Rs2PvMUtil.emergencyTeleport();
 * }
 * </pre>
 */
@Slf4j
public class Rs2PvMUtil {
    
    // ========================================
    // widget-based boss tracking
    // ========================================
    
    /**
     * checks if boss is alive using widget health bar
     * 
     * @param widgetID widget parent id
     * @param childID widget child id
     * @return true if boss health widget exists and > 0
     */
    public static boolean isBossAlive(int widgetID, int childID) {
        Widget healthWidget = Rs2Widget.getWidget(widgetID, childID);
        if (healthWidget == null) {
            return false;
        }
        
        // widget exists and not hidden = boss alive
        return !healthWidget.isHidden();
    }
    
    /**
     * gets boss health percentage from widget
     * 
     * @param widgetID widget parent id
     * @param childID widget child id
     * @return health percent (0-100), or -1 if widget not found
     */
    public static int getBossHealthPercent(int widgetID, int childID) {
        Widget healthWidget = Rs2Widget.getWidget(widgetID, childID);
        if (healthWidget == null || healthWidget.isHidden()) {
            return -1;
        }
        
        // attempt to parse health text (e.g., "Health: 80%")
        String text = healthWidget.getText();
        if (text != null && text.contains("%")) {
            try {
                String numericPart = text.replaceAll("[^0-9]", "");
                return Integer.parseInt(numericPart);
            } catch (NumberFormatException ex) {
                log.warn("Failed to parse boss health from widget text: {}", text);
            }
        }
        
        return -1;
    }
    
    /**
     * checks if boss phase widget is visible
     * 
     * @param widgetID widget parent id
     * @param childID widget child id
     * @param phaseText expected phase text (e.g., "Phase 2")
     * @return true if widget shows expected phase
     */
    public static boolean isBossInPhase(int widgetID, int childID, String phaseText) {
        Widget phaseWidget = Rs2Widget.getWidget(widgetID, childID);
        if (phaseWidget == null || phaseWidget.isHidden()) {
            return false;
        }
        
        String text = phaseWidget.getText();
        return text != null && text.toLowerCase().contains(phaseText.toLowerCase());
    }
    
    /**
     * waits until boss health drops below threshold
     * 
     * @param widgetID widget parent id
     * @param childID widget child id
     * @param threshold health percent threshold (0-100)
     * @param timeoutMs maximum wait time in milliseconds
     * @return true if threshold reached, false if timeout
     */
    public static boolean waitUntilBossHealthBelow(int widgetID, int childID, int threshold, int timeoutMs) {
        log.debug("Waiting for boss health below {}%", threshold);
        return sleepUntil(() -> getBossHealthPercent(widgetID, childID) < threshold, timeoutMs);
    }
    
    // ========================================
    // multi-target priority system
    // ========================================
    
    /**
     * gets nearest npc from priority list
     * 
     * @param npcIDs list of npc ids (ordered by priority)
     * @return nearest npc matching any id, or null if none found
     */
    public static Rs2NpcModel getNearestTarget(List<Integer> npcIDs) {
        return getNearestTarget(npcIDs, npc -> true);
    }
    
    /**
     * gets nearest npc from priority list with filter
     * 
     * @param npcIDs list of npc ids (ordered by priority)
     * @param filter predicate to filter valid targets
     * @return nearest valid npc, or null if none found
     */
    public static Rs2NpcModel getNearestTarget(List<Integer> npcIDs, Predicate<Rs2NpcModel> filter) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return null;
        }
        
        // check each id in priority order
        for (int npcID : npcIDs) {
            List<Rs2NpcModel> npcs = Rs2Npc.getNpcs(npcID).collect(Collectors.toList());
            if (npcs.isEmpty()) continue;
            
            Optional<Rs2NpcModel> nearest = npcs.stream()
                .filter(filter)
                .min(Comparator.comparingInt(npc -> 
                    npc.getWorldLocation().distanceTo(playerLocation)));
            
            if (nearest.isPresent()) {
                log.debug("Selected target: {} (ID: {})", nearest.get().getName(), npcID);
                return nearest.get();
            }
        }
        
        log.debug("No valid targets found from priority list");
        return null;
    }
    
    /**
     * gets highest priority target (health-based)
     * 
     * @param npcIDs list of npc ids to consider
     * @param prioritizeLowest if true, targets lowest health; if false, highest health
     * @return npc with priority health, or null if none found
     */
    public static Rs2NpcModel getHealthPriorityTarget(List<Integer> npcIDs, boolean prioritizeLowest) {
        List<Rs2NpcModel> allTargets = npcIDs.stream()
            .flatMap(id -> Rs2Npc.getNpcs(id))
            .filter(npc -> npc.getHealthRatio() > 0)
            .collect(Collectors.toList());
        
        if (allTargets.isEmpty()) {
            return null;
        }
        
        Comparator<Rs2NpcModel> comparator = prioritizeLowest
            ? Comparator.comparingInt(Rs2NpcModel::getHealthRatio)
            : Comparator.comparingInt(Rs2NpcModel::getHealthRatio).reversed();
        
        Optional<Rs2NpcModel> target = allTargets.stream().min(comparator);
        if (target.isPresent()) {
            log.debug("Selected {} health target: {}", 
                prioritizeLowest ? "lowest" : "highest", target.get().getName());
        }
        
        return target.orElse(null);
    }
    
    /**
     * attacks nearest target from priority list
     * 
     * @param npcIDs list of npc ids (ordered by priority)
     * @return true if attack initiated, false if no target found
     */
    public static boolean attackNearestTarget(List<Integer> npcIDs) {
        Rs2NpcModel target = getNearestTarget(npcIDs);
        if (target == null) {
            log.debug("No target found to attack");
            return false;
        }
        
        return attackTarget(target);
    }
    
    /**
     * attacks specific target with validation
     * 
     * @param target npc to attack
     * @return true if attack initiated successfully
     */
    public static boolean attackTarget(Rs2NpcModel target) {
        if (target == null) {
            return false;
        }
        
        // check if already in combat with target
        Actor interacting = Rs2Player.getInteracting();
        if (interacting != null && interacting.equals(target)) {
            log.debug("Already attacking target: {}", target.getName());
            return true;
        }
        
        // ensure target is on screen - simplified check
        WorldPoint targetLocation = target.getWorldLocation();
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        LocalPoint localPoint = targetLocation == null ? null
            : LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), targetLocation);

        if (targetLocation != null && playerLocation != null &&
            (targetLocation.distanceTo(playerLocation) > 15 || localPoint == null || !Rs2Camera.isTileOnScreen(localPoint))) {
            Rs2Camera.turnTo(target);
            sleepUntil(() -> {
                WorldPoint updatedLocation = target.getWorldLocation();
                LocalPoint updatedLocalPoint = updatedLocation == null ? null
                    : LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), updatedLocation);
                return updatedLocalPoint != null && Rs2Camera.isTileOnScreen(updatedLocalPoint);
            }, Constants.GAME_TICK_LENGTH * 3);
        }
        
        log.info("Attacking target: {} (HP: {})", target.getName(), target.getHealthRatio());
        return Rs2Npc.attack(target);
    }
    
    // ========================================
    // bailout and emergency systems
    // ========================================
    
    /**
     * checks if emergency bailout needed
     * 
     * @param healthThreshold health percent to trigger bailout (0-100)
     * @param prayerThreshold prayer percent to trigger bailout (0-100)
     * @return true if bailout conditions met
     */
    public static boolean shouldBailout(int healthThreshold, int prayerThreshold) {
        int currentHealth = (int) (Rs2Player.getHealthPercentage() * 100);
        int currentPrayer = Rs2Player.getPrayerPercentage();
        
        boolean bailout = currentHealth <= healthThreshold || currentPrayer <= prayerThreshold;
        
        if (bailout) {
            log.warn("Bailout conditions met - Health: {}%, Prayer: {}%", 
                currentHealth, currentPrayer);
        }
        
        return bailout;
    }
    
    /**
     * executes emergency teleport
     * attempts multiple teleport methods in priority order
     * 
     * @return true if teleport initiated
     */
    public static boolean emergencyTeleport() {
        log.warn("Executing emergency teleport");
        
        // simplified implementation - use home teleport
        // production version would check inventory for teleport items
        log.info("Attempting home teleport");
        
        // todo: implement proper teleport logic
        // Rs2Magic.cast(Spell.HOME_TELEPORT);
        
        return false; // placeholder
    }
    
    /**
     * attempts to exit boss arena by walking to exit
     * 
     * @param exitLocation world point of arena exit
     * @param timeoutMs maximum time to reach exit
     * @return true if exit reached
     */
    public static boolean exitBossArena(WorldPoint exitLocation, int timeoutMs) {
        log.info("Attempting to exit boss arena");
        
        if (exitLocation == null) {
            log.error("Exit location is null");
            return false;
        }
        
        // todo: implement pathfinding to exit
        // Rs2Walker.walkTo(exitLocation);
        
        boolean exited = sleepUntil(() -> {
            WorldPoint current = Rs2Player.getWorldLocation();
            return current != null && current.distanceTo(exitLocation) <= 2;
        }, timeoutMs);
        
        if (exited) {
            log.info("Successfully exited boss arena");
        } else {
            log.warn("Failed to exit arena within timeout");
        }
        
        return exited;
    }
    
    // ========================================
    // consumable management
    // ========================================
    
    /**
     * checks if player should eat food
     * 
     * @param healthThreshold health percent to trigger eating (0-100)
     * @return true if health below threshold
     */
    public static boolean shouldEat(int healthThreshold) {
        int currentHealth = (int) (Rs2Player.getHealthPercentage() * 100);
        return currentHealth <= healthThreshold;
    }
    
    /**
     * checks if player should drink prayer potion
     * 
     * @param prayerThreshold prayer percent to trigger drinking (0-100)
     * @return true if prayer below threshold
     */
    public static boolean shouldDrinkPrayer(int prayerThreshold) {
        int currentPrayer = Rs2Player.getPrayerPercentage();
        return currentPrayer <= prayerThreshold;
    }
    
    /**
     * checks if combat stats should be boosted
     * 
     * @param statLevel current stat level
     * @param baseStat base stat level (unboosted)
     * @return true if stat dropped below base level
     */
    public static boolean shouldBoostStat(int statLevel, int baseStat) {
        return statLevel < baseStat;
    }
    
    /**
     * checks if player can attack this tick (no cooldown, no tick loss)
     * 
     * @return true if attack action available
     */
    public static boolean canAttackThisTick() {
        return Rs2CombatHandler.getInstance().canAttackThisTick();
    }
    
    /**
     * gets ticks until next attack available
     * automatically tracks weapon speed and attack cooldown
     * 
     * @return ticks remaining until next attack (0 if ready)
     */
    public static int ticksUntilNextAttack() {
        return Rs2CombatHandler.getInstance().getTicksUntilNextAttack();
    }
    
    /**
     * gets current weapon attack speed in ticks
     * 
     * @return weapon speed (e.g., 4 for whip, 5 for godsword)
     */
    public static int getWeaponAttackSpeed() {
        return Rs2CombatHandler.getInstance().getWeaponAttackSpeed();
    }
    
    // ========================================
    // utility helpers
    // ========================================
    
    /**
     * checks if any npc from list is within attack range
     * 
     * @param npcIDs list of npc ids to check
     * @param maxDistance maximum distance in tiles
     * @return true if any npc within range
     */
    public static boolean isNpcInRange(List<Integer> npcIDs, int maxDistance) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return false;
        }
        
        return npcIDs.stream()
            .flatMap(id -> Rs2Npc.getNpcs(id))
            .anyMatch(npc -> npc.getWorldLocation().distanceTo(playerLocation) <= maxDistance);
    }
    
    /**
     * counts alive npcs from list
     * 
     * @param npcIDs list of npc ids to count
     * @return number of alive npcs
     */
    public static int countAliveNpcs(List<Integer> npcIDs) {
        return (int) npcIDs.stream()
            .flatMap(id -> Rs2Npc.getNpcs(id))
            .filter(npc -> npc.getHealthRatio() > 0)
            .count();
    }
    
    /**
     * gets all npcs from priority list
     * 
     * @param npcIDs list of npc ids
     * @return list of all matching npcs
     */
    public static List<Rs2NpcModel> getAllTargets(List<Integer> npcIDs) {
        return npcIDs.stream()
            .flatMap(id -> Rs2Npc.getNpcs(id))
            .collect(Collectors.toList());
    }
}
