package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.gameval.AnimationID;
import net.runelite.api.GameObject;
import net.runelite.api.InventoryID;
import net.runelite.api.Skill;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Condition that tracks items gathered from resource nodes (mining, fishing, woodcutting, farming, etc.).
 * Distinguishes between items gathered from resources versus those obtained from other sources.
 */
@Slf4j
@Getter
public class GatheredResourceCondition extends ResourceCondition {

    public static String getVersion() {
        return "0.0.1";
    }
    private final boolean includeNoted;
    private final String itemName;
    private final int targetCountMin;
    private final int targetCountMax;
    
    private final List<Skill> relevantSkills;
    
    // Gathering state tracking
    private transient boolean isCurrentlyGathering = false;
    private transient Instant lastGatheringActivity = Instant.now();
    private transient Map<String, Integer> previousItemCounts = new HashMap<>();
    private transient Map<String, Integer> gatheredItemCounts = new HashMap<>();
    private transient int currentTargetCount;
    private transient int currentGatheredCount;
    private transient boolean satisfied = false;
    
    // Animation tracking for gathering activities
    private static final int[] GATHERING_ANIMATIONS = {
        // Woodcutting
        AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE, AnimationID.HUMAN_WOODCUTTING_IRON_AXE, AnimationID.HUMAN_WOODCUTTING_STEEL_AXE,
        AnimationID.HUMAN_WOODCUTTING_BLACK_AXE, AnimationID.HUMAN_WOODCUTTING_MITHRIL_AXE, AnimationID.HUMAN_WOODCUTTING_ADAMANT_AXE,
        AnimationID.HUMAN_WOODCUTTING_RUNE_AXE, AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE, AnimationID.HUMAN_WOODCUTTING_INFERNAL_AXE,
        AnimationID.HUMAN_WOODCUTTING_3A_AXE, AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE, AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE_NO_INFERNAL,
        
        // Fishing        
        AnimationID.HUMAN_HARPOON, AnimationID.HUMAN_LOBSTER, AnimationID.HUMAN_LARGENET,
        AnimationID.HUMAN_SMALLNET, AnimationID.HUMAN_FISHING_CASTING, AnimationID.HUMAN_FISH_ONSPOT_PEARL_OILY,
        AnimationID.HUMAN_FISH_ONSPOT_PEARL,AnimationID.HUMAN_FISH_ONSPOT_PEARL_FLY,AnimationID.HUMAN_FISH_ONSPOT_PEARL_BRUT,
        AnimationID.HUMAN_FISHING_CASTING_PEARL,
        AnimationID.HUMAN_FISHING_CASTING_PEARL,AnimationID.HUMAN_FISHING_CASTING_PEARL_FLY,AnimationID.HUMAN_FISHING_CASTING_PEARL_BRUT,
        
        AnimationID.HUMAN_HARPOON_BARBED, AnimationID.HUMAN_HARPOON_DRAGON, AnimationID.HUMAN_HARPOON_INFERNAL,
        AnimationID.HUMAN_HARPOON_TRAILBLAZER_NO_INFERNAL, AnimationID.HUMAN_HARPOON_CRYSTAL, AnimationID.HUMAN_HARPOON_GAUNTLET_HM,
        AnimationID.BRUT_PLAYER_HAND_FISHING_READY, AnimationID.BRUT_PLAYER_HAND_FISHING_START,
        AnimationID.BRUT_PLAYER_HAND_FISHING_END_SHARK_1, AnimationID.BRUT_PLAYER_HAND_FISHING_END_SHARK_2,
        AnimationID.BRUT_PLAYER_HAND_FISHING_END_SWORDFISH_1, AnimationID.BRUT_PLAYER_HAND_FISHING_END_SWORDFISH_2,
        AnimationID.BRUT_PLAYER_HAND_FISHING_END_TUNA_1, AnimationID.BRUT_PLAYER_HAND_FISHING_END_TUNA_2,        
        AnimationID.HUMAN_FISHING_CASTING_BRUT, AnimationID.HUMAN_FISHING_ONSPOT_BRUT,
        AnimationID.SNAKEBOSS_SLICEEEL, //FISHING_CUTTING_SACRED_EELS
        AnimationID.INFERNALEEL_BREAK, AnimationID.INFERNALEEL_BREAK_IMCANDO,//FISHING_CRUSHING_INFERNAL_EELS
        AnimationID.HUMAN_OCTOPUS_POT,//FISHING_KARAMBWAN        
        // Mining
        AnimationID.HUMAN_MINING_BRONZE_PICKAXE, AnimationID.HUMAN_MINING_IRON_PICKAXE, AnimationID.HUMAN_MINING_STEEL_PICKAXE,
        AnimationID.HUMAN_MINING_BLACK_PICKAXE, AnimationID.HUMAN_MINING_MITHRIL_PICKAXE, AnimationID.HUMAN_MINING_ADAMANT_PICKAXE,
        AnimationID.HUMAN_MINING_RUNE_PICKAXE, AnimationID.HUMAN_MINING_DRAGON_PICKAXE,AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY,
        AnimationID.HUMAN_MINING_ZALCANO_PICKAXE, AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE,AnimationID.HUMAN_MINING_3A_PICKAXE,
        AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL,AnimationID.HUMAN_MINING_INFERNAL_PICKAXE, AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE,
        AnimationID.HUMAN_MINING_ZALCANO_LEAGUE_TRAILBLAZER_PICKAXE,

        //CRASHEDSTAR mining
        AnimationID.HUMAN_MINING_BRONZE_PICKAXE_NOREACHFORWARD, AnimationID.HUMAN_MINING_IRON_PICKAXE_NOREACHFORWARD, AnimationID.HUMAN_MINING_STEEL_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_BLACK_PICKAXE_NOREACHFORWARD, AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_NOREACHFORWARD, AnimationID.HUMAN_MINING_ADAMANT_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_RUNE_PICKAXE_NOREACHFORWARD, AnimationID.HUMAN_MINING_DRAGON_PICKAXE_NOREACHFORWARD,AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_ZALCANO_PICKAXE_NOREACHFORWARD, AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_NOREACHFORWARD,AnimationID.HUMAN_MINING_3A_PICKAXE_NOREACHFORWARD,
        AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL_NOREACHFORWARD,AnimationID.HUMAN_MINING_INFERNAL_PICKAXE_NOREACHFORWARD, AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE_NOREACHFORWARD,
        //Motherload Mine mining
        AnimationID.HUMAN_MINING_BRONZE_PICKAXE_WALL, AnimationID.HUMAN_MINING_IRON_PICKAXE_WALL, AnimationID.HUMAN_MINING_STEEL_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_BLACK_PICKAXE_WALL, AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_WALL, AnimationID.HUMAN_MINING_ADAMANT_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_RUNE_PICKAXE_WALL, AnimationID.HUMAN_MINING_DRAGON_PICKAXE_WALL,AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY_WALL,
        AnimationID.HUMAN_MINING_ZALCANO_PICKAXE_WALL, AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_WALL,AnimationID.HUMAN_MINING_3A_PICKAXE_WALL,
        AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL_WALL,AnimationID.HUMAN_MINING_INFERNAL_PICKAXE_WALL, AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE_WALL,        
        // Farming
        AnimationID.ULTRACOMPOST_MAKE, AnimationID.HUMAN_FARMING, AnimationID.FARMING_RAKING,
        AnimationID.FARMING_PICK_MUSHROOM
    };
    
    /**
     * Basic constructor with only item name and target count
     */
    public GatheredResourceCondition(String itemName, int targetCount, boolean includeNoted) {
        super(itemName);
        this.itemName = itemName;        
        this.targetCountMin = targetCount;
        this.targetCountMax = targetCount;
        this.currentTargetCount = targetCount;
        this.includeNoted = includeNoted;
        this.relevantSkills = determineRelevantSkills(itemName);
        initializeItemCounts();
    }
    
    /**
     * Full constructor with builder support
     */
    @Builder
    public GatheredResourceCondition(String itemName, int targetCountMin, int targetCountMax, 
                                   boolean includeNoted, List<Skill> relevantSkills) {
        super(itemName);                           
        this.itemName = itemName;        
        this.targetCountMin = Math.max(0, targetCountMin);
        this.targetCountMax = Math.min(Integer.MAX_VALUE, targetCountMax);                
        this.currentTargetCount = Rs2Random.between(this.targetCountMin, this.targetCountMax);
        this.includeNoted = includeNoted;
        this.relevantSkills = relevantSkills != null ? relevantSkills : determineRelevantSkills(itemName);
        initializeItemCounts();
    }
    
    /**
     * Initialize tracking of inventory item counts
     */
    private void initializeItemCounts() {
        updatePreviousItemCounts();
        gatheredItemCounts.clear();
        currentGatheredCount = 0;
    }
    
    /**
     * Create a map of current inventory item counts for tracking purposes
     */
    private void updatePreviousItemCounts() {
        previousItemCounts.clear();
        
        // Include all inventory items matching our pattern
        List<Rs2ItemModel> items = new ArrayList<>();
        items.addAll(getUnNotedItems());
        if (includeNoted) {
            items.addAll(getNotedItems());
        }
        
        // Count matching items
        for (Rs2ItemModel item : items) {
            if (item != null && itemPattern.matcher(item.getName()).matches()) {
                String name = item.getName();
                previousItemCounts.put(name, previousItemCounts.getOrDefault(name, 0) + 1);
            }
        }
    }
    
    /**
     * Attempts to determine which skills are relevant for the specified item
     */
    private List<Skill> determineRelevantSkills(String itemName) {
        List<Skill> skills = new ArrayList<>();
        
        // Pattern matching approach - could be more sophisticated with a proper mapping
        String lowerName = itemName.toLowerCase();
        
        // Mining related
        if (lowerName.contains("ore") || lowerName.contains("rock") || lowerName.contains("coal") || 
            lowerName.contains("gem") || lowerName.contains("granite") || lowerName.contains("sandstone") ||
            lowerName.contains("clay")) {
            skills.add(Skill.MINING);
        }
        
        // Fishing related
        if (lowerName.contains("fish") || lowerName.contains("shrimp") || lowerName.contains("trout") || 
            lowerName.contains("salmon") || lowerName.contains("lobster") || lowerName.contains("shark") ||
            lowerName.contains("karambwan") || lowerName.contains("monkfish") || lowerName.contains("anglerfish")) {
            skills.add(Skill.FISHING);
        }
        
        // Woodcutting related
        if (lowerName.contains("log") || lowerName.contains("root") || lowerName.contains("bark")) {
            skills.add(Skill.WOODCUTTING);
        }
        
        // Farming related
        if (lowerName.contains("seed") || lowerName.contains("sapling") || lowerName.contains("herb") || 
            lowerName.contains("leaf") || lowerName.contains("fruit") || lowerName.contains("berry") || 
            lowerName.contains("vegetable") || lowerName.contains("coconut") || lowerName.contains("banana") ||
            lowerName.contains("papaya") || lowerName.contains("watermelon") || lowerName.contains("strawberry") ||
            lowerName.contains("tomato") || lowerName.contains("potato") || lowerName.contains("onion") ||
            lowerName.contains("cabbage")) {
            skills.add(Skill.FARMING);
        }
        
        // Default to all resource-gathering skills if no match
        if (skills.isEmpty()) {
            skills.add(Skill.MINING);
            skills.add(Skill.FISHING);
            skills.add(Skill.WOODCUTTING);
            skills.add(Skill.FARMING);
            skills.add(Skill.HUNTER);
        }
        
        return skills;
    }
    
    /**
     * Checks if the player is currently performing a gathering animation
     */
    private boolean isCurrentlyGatheringAnimation() {
        int currentAnimation = Rs2Player.getAnimation();
        for (int animation : GATHERING_ANIMATIONS) {
            if (currentAnimation == animation) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean isSatisfied() {
        // Once satisfied, stay satisfied until reset
        if (satisfied) {
            return true;
        }
        
        // Check if gathered count meets or exceeds target
        if (currentGatheredCount >= currentTargetCount) {
            satisfied = true;
            return true;
        }
        
        return false;
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
        isCurrentlyGathering = false;
        gatheredItemCounts.clear();
        currentGatheredCount = 0;
        updatePreviousItemCounts();
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.RESOURCE;
    }
    
    @Override
    public String getDescription() {
        String itemTypeDesc = includeNoted ? " (including noted)" : "";
        String randomRangeInfo = "";
        
        if (targetCountMin != targetCountMax) {
            randomRangeInfo = String.format(" (randomized from %d-%d)", targetCountMin, targetCountMax);
        }
        
        return String.format("Gather %d %s%s%s (%d/%d, %.1f%%)", 
                currentTargetCount, 
                itemName != null && !itemName.isEmpty() ? itemName : "resources",
                itemTypeDesc,
                randomRangeInfo,
                currentGatheredCount,
                currentTargetCount,
                getProgressPercentage());
    }
    
    @Override
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Basic description
        sb.append("Gathered Resource Condition: ").append(itemName != null && !itemName.isEmpty() ? itemName : "Any resource").append("\n");
        
        // Add randomization info if applicable
        if (targetCountMin != targetCountMax) {
            sb.append("Target Range: ").append(targetCountMin).append("-").append(targetCountMax)
              .append(" (current target: ").append(currentTargetCount).append(")\n");
        } else {
            sb.append("Target Count: ").append(currentTargetCount).append("\n");
        }
        
        // Status information
        sb.append("Status: ").append(isSatisfied() ? "Satisfied" : "Not satisfied").append("\n");
        sb.append("Progress: ").append(currentGatheredCount).append("/").append(currentTargetCount)
          .append(" (").append(String.format("%.1f%%", getProgressPercentage())).append(")\n");
        
        // Configuration information
        sb.append("Include Noted Items: ").append(includeNoted ? "Yes" : "No").append("\n");
        sb.append("Currently Gathering: ").append(isCurrentlyGathering ? "Yes" : "No").append("\n");
        
        // Relevant skills
        sb.append("Tracking XP in skills: ");
        for (int i = 0; i < relevantSkills.size(); i++) {
            sb.append(relevantSkills.get(i).getName());
            if (i < relevantSkills.size() - 1) {
                sb.append(", ");
            }
        }
        
        return sb.toString();
    }
    
    @Override
    public double getProgressPercentage() {
        if (satisfied) {
            return 100.0;
        }
        
        if (currentTargetCount <= 0) {
            return 100.0;
        }
        
        return Math.min(100.0, (currentGatheredCount * 100.0) / currentTargetCount);
    }
    
    @Override
    public void onAnimationChanged(AnimationChanged event) {
        // Check if this is our player
        if (event.getActor() != Microbot.getClient().getLocalPlayer()) {
            return;
        }
        
        // Update gathering state based on animation
        if (isCurrentlyGatheringAnimation()) {
            isCurrentlyGathering = true;
            lastGatheringActivity = Instant.now();
        }
    }
    
    @Override
    public void onStatChanged(StatChanged event) {
        // Check if XP was gained in a relevant skill
        if (relevantSkills.contains(event.getSkill()) && event.getXp() > 0) {
            isCurrentlyGathering = true;
            lastGatheringActivity = Instant.now();
        }
    }
    
    @Override
    public void onInteractingChanged(InteractingChanged event) {
        // Check if this is our player
        if (event.getSource() != Microbot.getClient().getLocalPlayer()) {
            return;
        }
        
        // If player starts interacting with something, consider it gathering
        if (event.getTarget() != null) {
            isCurrentlyGathering = true;
            lastGatheringActivity = Instant.now();
        }
    }
    
    @Override
    public void onGameTick(GameTick event) {
        // Check if we've timed out on gathering activity
        if (isCurrentlyGathering && Instant.now().minusSeconds(5).isAfter(lastGatheringActivity)) {
            isCurrentlyGathering = false;
        }
    }
    
    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        // Only process inventory changes
        if (event.getContainerId() != InventoryID.INVENTORY.getId()) {
            return;
        }
        
        // Don't process changes if bank is open (banking items)
        if (Rs2Bank.isOpen()) {
            updatePreviousItemCounts();
            return;
        }
        
        // Process inventory changes only when actively gathering or within 3 seconds of gathering
        if (isCurrentlyGathering || Instant.now().minusSeconds(3).isBefore(lastGatheringActivity)) {
            processInventoryChanges();
        } else {
            // Just update previous counts if not gathering
            updatePreviousItemCounts();
        }
    }
    
    /**
     * Process inventory changes to detect newly gathered items
     */
    private void processInventoryChanges() {
        Map<String, Integer> currentCounts = new HashMap<>();
        
        // Get current inventory counts
        List<Rs2ItemModel> currentItems = new ArrayList<>();
        currentItems.addAll(getUnNotedItems());
        if (includeNoted) {
            currentItems.addAll(getNotedItems());
        }
        
        // Count matching items
        for (Rs2ItemModel item : currentItems) {
            if (item != null && itemPattern.matcher(item.getName()).matches()) {
                String name = item.getName();
                currentCounts.put(name, currentCounts.getOrDefault(name, 0) + item.getQuantity());
            }
        }
        
        // Calculate differences
        for (Map.Entry<String, Integer> entry : currentCounts.entrySet()) {
            String itemName = entry.getKey();
            int currentCount = entry.getValue();
            int previousCount = previousItemCounts.getOrDefault(itemName, 0);
            
            // If current count is higher, items were gathered
            if (currentCount > previousCount) {
                int newItems = currentCount - previousCount;
                log.debug("Detected {} newly gathered {}", newItems, itemName);
                
                // Add to gathered count
                gatheredItemCounts.put(itemName, gatheredItemCounts.getOrDefault(itemName, 0) + newItems);
                currentGatheredCount += newItems;
            }
        }
        
        // Update previous counts for next comparison
        previousItemCounts = currentCounts;
    }
    
    /**
     * Creates a condition with randomized target between min and max
     */
    public static GatheredResourceCondition createRandomized(String itemName, int minCount, int maxCount, 
                                                           boolean includeNoted) {
        return GatheredResourceCondition.builder()
                .itemName(itemName)
                .targetCountMin(minCount)
                .targetCountMax(maxCount)
                .includeNoted(includeNoted)
                .build();
    }
    
    /**
     * Creates an AND logical condition requiring multiple gathered items with individual targets
     */
    public static LogicalCondition createAndCondition(List<String> itemNames, 
                                                     List<Integer> targetCountsMins,
                                                     List<Integer> targetCountsMaxs,
                                                     boolean includeNoted) {
        if (itemNames == null || itemNames.isEmpty()) {
            throw new IllegalArgumentException("Item name list cannot be null or empty");
        }
        
        // Handle missing target counts
        int minSize = Math.min(itemNames.size(), 
                     Math.min(targetCountsMins != null ? targetCountsMins.size() : 0,
                              targetCountsMaxs != null ? targetCountsMaxs.size() : 0));
        
        if (targetCountsMins == null || targetCountsMins.isEmpty()) {
            targetCountsMins = new ArrayList<>(itemNames.size());
            for (int i = 0; i < itemNames.size(); i++) {
                targetCountsMins.add(1);
            }
        }
        
        if (targetCountsMaxs == null || targetCountsMaxs.isEmpty()) {
            targetCountsMaxs = new ArrayList<>(targetCountsMins);
        }
        
        // Create AND condition
        AndCondition andCondition = new AndCondition();
        
        // Add condition for each item
        for (int i = 0; i < minSize; i++) {
            GatheredResourceCondition itemCondition = GatheredResourceCondition.builder()
                    .itemName(itemNames.get(i))
                    .targetCountMin(targetCountsMins.get(i))
                    .targetCountMax(targetCountsMaxs.get(i))
                    .includeNoted(includeNoted)
                    .build();
                    
            andCondition.addCondition(itemCondition);
        }
        
        return andCondition;
    }
    
    /**
     * Creates an OR logical condition requiring any of multiple gathered items with individual targets
     */
    public static LogicalCondition createOrCondition(List<String> itemNames, 
                                                    List<Integer> targetCountsMins,
                                                    List<Integer> targetCountsMaxs,
                                                    boolean includeNoted) {
        if (itemNames == null || itemNames.isEmpty()) {
            throw new IllegalArgumentException("Item name list cannot be null or empty");
        }
        
        // Handle missing target counts
        int minSize = Math.min(itemNames.size(), 
                     Math.min(targetCountsMins != null ? targetCountsMins.size() : 0,
                              targetCountsMaxs != null ? targetCountsMaxs.size() : 0));
        
        if (targetCountsMins == null || targetCountsMins.isEmpty()) {
            targetCountsMins = new ArrayList<>(itemNames.size());
            for (int i = 0; i < itemNames.size(); i++) {
                targetCountsMins.add(1);
            }
        }
        
        if (targetCountsMaxs == null || targetCountsMaxs.isEmpty()) {
            targetCountsMaxs = new ArrayList<>(targetCountsMins);
        }
        
        // Create OR condition
        OrCondition orCondition = new OrCondition();
        
        // Add condition for each item
        for (int i = 0; i < minSize; i++) {
            GatheredResourceCondition itemCondition = GatheredResourceCondition.builder()
                    .itemName(itemNames.get(i))
                    .targetCountMin(targetCountsMins.get(i))
                    .targetCountMax(targetCountsMaxs.get(i))
                    .includeNoted(includeNoted)
                    .build();
                    
            orCondition.addCondition(itemCondition);
        }
        
        return orCondition;
    }
}