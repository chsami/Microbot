package net.runelite.client.plugins.microbot.pluginscheduler.condition.npc;

import lombok.Builder;
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
public class NpcKillCountCondition extends NpcCondition {
    private final String npcName;
    private final Pattern npcNamePattern;
    private final int targetCountMin;
    private final int targetCountMax;
    private int currentTargetCount;
    private int currentKillCount;
    private boolean satisfied = false;
    private boolean registered = false;
    
    // Set to track NPCs we're currently interacting with
    private final Set<Integer> interactingNpcIndices = new HashSet<>();
    
    /**
     * Creates a condition with a fixed target count
     */
    
    public NpcKillCountCondition(String npcName, int targetCount) {
        this.npcName = npcName;
        this.npcNamePattern = createNpcNamePattern(npcName);
        this.targetCountMin = targetCount;
        this.targetCountMax = targetCount;
        this.currentTargetCount = targetCount;
        registerEvents();
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
        registerEvents();
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
    
    private void registerEvents() {
        if (!registered) {
            Microbot.getEventBus().register(this);
            registered = true;
        }
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
        return String.format("Kill %d %s (%d/%d)", 
                currentTargetCount, 
                npcName != null && !npcName.isEmpty() ? npcName : "NPCs",
                currentKillCount,
                currentTargetCount);
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
    

}