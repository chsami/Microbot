package net.runelite.client.plugins.microbot.pluginscheduler.condition.npc;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Condition that tracks the number of NPCs killed by the player.
 * Is satisfied when the player has killed a certain number of NPCs.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class NpcKillCountCondition extends NpcCondition {
    
    public static String getVersion() {
        return "0.0.1";
    }
    private final String npcName;
    private final Pattern npcNamePattern;
    private final int targetCountMin;
    private final int targetCountMax;
    private transient int currentTargetCount;
    private transient int currentKillCount;
    private transient boolean satisfied = false;
    private transient boolean registered = false;
    
    // Set to track NPCs we're currently interacting with
    private final Set<Integer> interactingNpcIndices = new HashSet<>();
        
    private long startTimeMillis = System.currentTimeMillis();
    private long lastKillTimeMillis = 0;
    
    /**
     * Creates a condition with a fixed target count
     */
    
    public NpcKillCountCondition(String npcName, int targetCount) {
        this.npcName = npcName;
        this.npcNamePattern = createNpcNamePattern(npcName);
        this.targetCountMin = targetCount;
        this.targetCountMax = targetCount;
        this.currentTargetCount = targetCount;

    }
    
    /**
     * Creates a condition with a randomized target count between min and max
     */
    @Builder
    public NpcKillCountCondition(String npcName, int targetCountMin, int targetCountMax) {
        this.npcName = npcName;
        this.npcNamePattern = createNpcNamePattern(npcName);
        this.targetCountMin = Math.max(0, targetCountMin);
        this.targetCountMax = Math.max(this.targetCountMin, targetCountMax);
        this.currentTargetCount = Rs2Random.between(this.targetCountMin, this.targetCountMax);        
    }
    
    /**
     * Creates a condition with randomized target between min and max
     */
    public static NpcKillCountCondition createRandomized(String npcName, int minCount, int maxCount) {
        return NpcKillCountCondition.builder()
                .npcName(npcName)
                .targetCountMin(minCount)
                .targetCountMax(maxCount)
                .build();
    }
    
    /**
     * Creates an AND logical condition requiring kills for multiple NPCs with individual targets
     * All conditions must be satisfied (must kill the required number of each NPC)
     */
    public static LogicalCondition createAndCondition(List<String> npcNames, List<Integer> targetCountsMins, List<Integer> targetCountsMaxs) {
        if (npcNames == null || npcNames.isEmpty()) {
            throw new IllegalArgumentException("NPC name list cannot be null or empty");
        }
        
        // Determine the smallest list size for safe iteration
        int minSize = Math.min(npcNames.size(), 
                      Math.min(targetCountsMins != null ? targetCountsMins.size() : 0,
                               targetCountsMaxs != null ? targetCountsMaxs.size() : 0));
        
        // If target counts not provided or empty, default to single kill per NPC
        if (targetCountsMins == null || targetCountsMins.isEmpty()) {
            targetCountsMins = new ArrayList<>(npcNames.size());
            for (int i = 0; i < npcNames.size(); i++) {
                targetCountsMins.add(1);
            }
        }
        
        if (targetCountsMaxs == null || targetCountsMaxs.isEmpty()) {
            targetCountsMaxs = new ArrayList<>(targetCountsMins);
        }
        
        // Create the logical condition
        AndCondition andCondition = new AndCondition();
        
        // Add a kill count condition for each NPC
        for (int i = 0; i < minSize; i++) {
            NpcKillCountCondition killCondition = NpcKillCountCondition.builder()
                    .npcName(npcNames.get(i))
                    .targetCountMin(targetCountsMins.get(i))
                    .targetCountMax(targetCountsMaxs.get(i))
                    .build();
                    
            andCondition.addCondition(killCondition);
        }
        
        return andCondition;
    }
    
    /**
     * Creates an OR logical condition requiring kills for multiple NPCs with individual targets
     * Any condition can be satisfied (must kill the required number of any one NPC)
     */
    public static LogicalCondition createOrCondition(List<String> npcNames, List<Integer> targetCountsMins, List<Integer> targetCountsMaxs) {
        if (npcNames == null || npcNames.isEmpty()) {
            throw new IllegalArgumentException("NPC name list cannot be null or empty");
        }
        
        // Determine the smallest list size for safe iteration
        int minSize = Math.min(npcNames.size(), 
                      Math.min(targetCountsMins != null ? targetCountsMins.size() : 0,
                               targetCountsMaxs != null ? targetCountsMaxs.size() : 0));
        
        // If target counts not provided or empty, default to single kill per NPC
        if (targetCountsMins == null || targetCountsMins.isEmpty()) {
            targetCountsMins = new ArrayList<>(npcNames.size());
            for (int i = 0; i < npcNames.size(); i++) {
                targetCountsMins.add(1);
            }
        }
        
        if (targetCountsMaxs == null || targetCountsMaxs.isEmpty()) {
            targetCountsMaxs = new ArrayList<>(targetCountsMins);
        }
        
        // Create the logical condition
        OrCondition orCondition = new OrCondition();
        
        // Add a kill count condition for each NPC
        for (int i = 0; i < minSize; i++) {
            NpcKillCountCondition killCondition = NpcKillCountCondition.builder()
                    .npcName(npcNames.get(i))
                    .targetCountMin(targetCountsMins.get(i))
                    .targetCountMax(targetCountsMaxs.get(i))
                    .build();
                    
            orCondition.addCondition(killCondition);
        }
        
        return orCondition;
    }
    
    /**
     * Creates an AND logical condition requiring kills for multiple NPCs with the same target for all
     * All conditions must be satisfied (must kill the required number of each NPC)
     */
    public static LogicalCondition createAndCondition(List<String> npcNames, int targetCountMin, int targetCountMax) {
        if (npcNames == null || npcNames.isEmpty()) {
            throw new IllegalArgumentException("NPC name list cannot be null or empty");
        }
        
        // Create the logical condition
        AndCondition andCondition = new AndCondition();
        
        // Add a kill count condition for each NPC with the same targets
        for (String npcName : npcNames) {
            NpcKillCountCondition killCondition = NpcKillCountCondition.builder()
                    .npcName(npcName)
                    .targetCountMin(targetCountMin)
                    .targetCountMax(targetCountMax)
                    .build();
                    
            andCondition.addCondition(killCondition);
        }
        
        return andCondition;
    }
    
    /**
     * Creates an OR logical condition requiring kills for multiple NPCs with the same target for all
     * Any condition can be satisfied (must kill the required number of any one NPC)
     */
    public static LogicalCondition createOrCondition(List<String> npcNames, int targetCountMin, int targetCountMax) {
        if (npcNames == null || npcNames.isEmpty()) {
            throw new IllegalArgumentException("NPC name list cannot be null or empty");
        }
        
        // Create the logical condition
        OrCondition orCondition = new OrCondition();
        
        // Add a kill count condition for each NPC with the same targets
        for (String npcName : npcNames) {
            NpcKillCountCondition killCondition = NpcKillCountCondition.builder()
                    .npcName(npcName)
                    .targetCountMin(targetCountMin)
                    .targetCountMax(targetCountMax)
                    .build();
                    
            orCondition.addCondition(killCondition);
        }
        
        return orCondition;
    }
    
    
    @Override
    public boolean isSatisfied() {
        // Once satisfied, stay satisfied until reset
        if (satisfied) {
            return true;
        }
        
        // Check if current count meets or exceeds target
        if (currentKillCount >= currentTargetCount) {
            satisfied = true;
            return true;
        }
        
        return false;
    }
    
    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        String npcDisplayName = npcName != null && !npcName.isEmpty() ? npcName : "NPCs";
        
        sb.append(String.format("Kill %d %s", currentTargetCount, npcDisplayName));
        
        // Add randomization info if applicable
        if (targetCountMin != targetCountMax) {
            sb.append(String.format(" (randomized from %d-%d)", targetCountMin, targetCountMax));
        }
        
        // Add progress tracking
        sb.append(String.format(" (%d/%d, %.1f%%)", 
                currentKillCount,
                currentTargetCount,
                getProgressPercentage()));
                
        return sb.toString();
    }
    
    /**
     * Returns a detailed description of the kill condition with additional status information
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        String npcDisplayName = npcName != null && !npcName.isEmpty() ? npcName : "NPCs";
        
        // Basic description
        sb.append(String.format("Kill %d %s", currentTargetCount, npcDisplayName));
        
        // Add randomization info if applicable
        if (targetCountMin != targetCountMax) {
            sb.append(String.format(" (randomized from %d-%d)", targetCountMin, targetCountMax));
        }
        
        sb.append("\n");
        
        // Status information
        sb.append("Status: ").append(satisfied ? "Satisfied" : "Not satisfied").append("\n");
        sb.append("Progress: ").append(String.format("%d/%d (%.1f%%)", 
                currentKillCount, 
                currentTargetCount,
                getProgressPercentage())).append("\n");
        
        // NPC information
        if (npcName != null && !npcName.isEmpty()) {
            sb.append("NPC Name: ").append(npcName).append("\n");
            
            if (!npcNamePattern.pattern().equals(".*")) {
                sb.append("Pattern: ").append(npcNamePattern.pattern()).append("\n");
            }
        } else {
            sb.append("NPC: Any\n");
        }
        
        // Tracking information
        sb.append("Currently tracking ").append(interactingNpcIndices.size()).append(" NPCs");
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // Basic information
        sb.append("NpcKillCountCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ NPC: ").append(npcName != null && !npcName.isEmpty() ? npcName : "Any").append("\n");
        
        if (npcNamePattern != null && !npcNamePattern.pattern().equals(".*")) {
            sb.append("  │ Pattern: ").append(npcNamePattern.pattern()).append("\n");
        }
        
        sb.append("  │ Target Count: ").append(currentTargetCount).append("\n");
        
        // Randomization
        sb.append("  ├─ Randomization ────────────────────────────\n");
        boolean hasRandomization = targetCountMin != targetCountMax;
        sb.append("  │ Randomization: ").append(hasRandomization ? "Enabled" : "Disabled").append("\n");
        if (hasRandomization) {
            sb.append("  │ Target Range: ").append(targetCountMin).append("-").append(targetCountMax).append("\n");
        }
        
        // Status information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        sb.append("  │ Satisfied: ").append(satisfied).append("\n");
        sb.append("  │ Current Kill Count: ").append(currentKillCount).append("\n");
        sb.append("  │ Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        
        // Tracking info
        sb.append("  └─ Tracking ────────────────────────────────\n");
        sb.append("    Active Interactions: ").append(interactingNpcIndices.size()).append("\n");
        
        // List tracked NPCs if there are any
        if (!interactingNpcIndices.isEmpty()) {
            sb.append("    Tracked NPCs: ").append(interactingNpcIndices.toString()).append("\n");
        }
        
        return sb.toString();
    }
    
    @Override
    public void reset() {
        reset(false);
    }
    
    @Override
    public void reset(boolean randomize) {
        if (randomize && targetCountMin != targetCountMax) {
            currentTargetCount = Rs2Random.between(targetCountMin, targetCountMax);
        }
        satisfied = false;
        currentKillCount = 0;
        interactingNpcIndices.clear();
    }
    
    @Override
    public double getProgressPercentage() {
        if (satisfied) {
            return 100.0;
        }
        
        if (currentTargetCount <= 0) {
            return 100.0;
        }
        
        return Math.min(100.0, (currentKillCount * 100.0) / currentTargetCount);
    }
    
    // NOTE: This approach tracks player interactions with NPCs
    // It may need adjustment based on how interaction events fire in the game
    @Subscribe
    public void onInteractingChanged(InteractingChanged event) {
        // Only care if the player is doing the interaction
        if (event.getSource() != Microbot.getClient().getLocalPlayer()) {
            return;
        }
        
        // If the player is now interacting with an NPC, track it
        if (event.getTarget() instanceof NPC) {
            NPC npc = (NPC) event.getTarget();
            
            // Only track NPCs that match our pattern if we have one
            if (npcName == null || npcName.isEmpty() || npcNamePattern.matcher(npc.getName()).matches()) {
                interactingNpcIndices.add(npc.getIndex());
            }
        }
    }
    
    // NOTE: This part checks if an NPC that the player was interacting with died
    // It assumes player killed it, which may not always be true in multi-combat areas
    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        
        // Check if we were tracking this NPC
        if (interactingNpcIndices.contains(npc.getIndex())) {
            // If the NPC is dead, count it as a kill
            if (npc.isDead()) {
                // Only count NPCs that match our pattern if we have one
                if (npcName == null || npcName.isEmpty() || npcNamePattern.matcher(npc.getName()).matches()) {
                    currentKillCount++;
                    lastKillTimeMillis = System.currentTimeMillis();
                }
            }
            
            // Remove the NPC from our tracking regardless
            interactingNpcIndices.remove(npc.getIndex());
        }
    }
    
    @Override
    public int getTotalConditionCount() {
        return 1;
    }
    
    @Override
    public int getMetConditionCount() {
        return isSatisfied() ? 1 : 0;
    }
    
    /**
     * Manually increments the kill counter.
     * Useful for testing or when external systems detect kills.
     * 
     * @param count Number of kills to add
     */
    public void incrementKillCount(int count) {
        currentKillCount += count;
        lastKillTimeMillis = System.currentTimeMillis();
        
        // Update satisfaction status
        if (currentKillCount >= currentTargetCount && !satisfied) {
            satisfied = true;
        }
    }
    
    /**
     * Gets the estimated kills per hour based on current progress.
     * 
     * @return Kills per hour or 0 if not enough data
     */
    public double getKillsPerHour() {
        long timeElapsedMs = System.currentTimeMillis() - startTimeMillis;
        
        // Require at least 30 seconds of data and at least one kill
        if (timeElapsedMs < 30000 || currentKillCount == 0) {
            return 0;
        }
        
        double hoursElapsed = timeElapsedMs / (1000.0 * 60 * 60);
        return currentKillCount / hoursElapsed;
    }
    
    /**
     * Gets the time since the last kill in milliseconds.
     * 
     * @return Time since last kill in ms, or -1 if no kills yet
     */
    public long getTimeSinceLastKill() {
        if (lastKillTimeMillis == 0) {
            return -1;
        }
        
        return System.currentTimeMillis() - lastKillTimeMillis;
    }
    
    
}