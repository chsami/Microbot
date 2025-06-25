package net.runelite.client.plugins.microbot.MKE.wintertodt.startup.gear;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;

import java.util.Map;
import java.util.HashMap;

/**
 * Represents a gear item for Wintertodt optimization.
 * Contains all necessary information for intelligent gear selection including
 * requirements, priority ratings, and special properties.
 * 
 * @author MakeCD
 * @version 1.0.0
 */
public class WintertodtGearItem {
    
    private final int itemId;
    private final String itemName;
    private final EquipmentInventorySlot slot;
    private final int priority;
    private final Map<Skill, Integer> levelRequirements;
    private final String questRequirement;
    private final GearCategory category;
    private final String description;
    private final boolean isTradeable;
    
    // Special properties for Wintertodt
    private final boolean providesWarmth;
    private final boolean reducesDamage;
    private final int weight; // negative = reduces weight
    private final boolean hasSpecialEffect;
    
    public enum GearCategory {
        PYROMANCER("Pyromancer gear - Best for Wintertodt"),
        WARM_GEAR("Warm clothing - Good for cold resistance"),
        GRACEFUL("Graceful outfit - Weight reduction"),
        SKILL_GEAR("Skill-specific gear"),
        COMBAT_GEAR("Combat equipment"),
        UTILITY("Utility items"),
        FASHIONSCAPE("Fashionscape items - Style over substance"),
        COSMETIC("Cosmetic items");
        
        private final String description;
        
        GearCategory(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    private WintertodtGearItem(Builder builder) {
        this.itemId = builder.itemId;
        this.itemName = builder.itemName;
        this.slot = builder.slot;
        this.priority = builder.priority;
        this.levelRequirements = new HashMap<>(builder.levelRequirements);
        this.questRequirement = builder.questRequirement;
        this.category = builder.category;
        this.description = builder.description;
        this.isTradeable = builder.isTradeable;
        this.providesWarmth = builder.providesWarmth;
        this.reducesDamage = builder.reducesDamage;
        this.weight = builder.weight;
        this.hasSpecialEffect = builder.hasSpecialEffect;
    }
    
    /**
     * Builder pattern for creating WintertodtGearItem instances.
     */
    public static class Builder {
        private int itemId;
        private String itemName;
        private EquipmentInventorySlot slot;
        private int priority = 0;
        private Map<Skill, Integer> levelRequirements = new HashMap<>();
        private String questRequirement = null;
        private GearCategory category = GearCategory.UTILITY;
        private String description = "";
        private boolean isTradeable = true;
        private boolean providesWarmth = false;
        private boolean reducesDamage = false;
        private int weight = 0;
        private boolean hasSpecialEffect = false;
        
        public Builder(int itemId, String itemName, EquipmentInventorySlot slot) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.slot = slot;
        }
        
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder levelRequirement(Skill skill, int level) {
            this.levelRequirements.put(skill, level);
            return this;
        }
        
        public Builder questRequirement(String quest) {
            this.questRequirement = quest;
            return this;
        }
        
        public Builder category(GearCategory category) {
            this.category = category;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder untradeable() {
            this.isTradeable = false;
            return this;
        }
        
        public Builder providesWarmth() {
            this.providesWarmth = true;
            return this;
        }
        
        public Builder reducesDamage() {
            this.reducesDamage = true;
            return this;
        }
        
        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }
        
        public Builder hasSpecialEffect() {
            this.hasSpecialEffect = true;
            return this;
        }
        
        public WintertodtGearItem build() {
            return new WintertodtGearItem(this);
        }
    }
    
    // Getters
    public int getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public EquipmentInventorySlot getSlot() { return slot; }
    public int getPriority() { return priority; }
    public Map<Skill, Integer> getLevelRequirements() { return new HashMap<>(levelRequirements); }
    public String getQuestRequirement() { return questRequirement; }
    public GearCategory getCategory() { return category; }
    public String getDescription() { return description; }
    public boolean isTradeable() { return isTradeable; }
    public boolean providesWarmth() { return providesWarmth; }
    public boolean reducesDamage() { return reducesDamage; }
    public int getWeight() { return weight; }
    public boolean hasSpecialEffect() { return hasSpecialEffect; }
    
    /**
     * Checks if the player meets all requirements to wear this item.
     * @param playerLevels Map of player's current skill levels
     * @param completedQuests Set of completed quest names
     * @return true if player can wear this item
     */
    public boolean meetsRequirements(Map<Skill, Integer> playerLevels, java.util.Set<String> completedQuests) {
        // Check level requirements
        for (Map.Entry<Skill, Integer> requirement : levelRequirements.entrySet()) {
            Integer playerLevel = playerLevels.get(requirement.getKey());
            if (playerLevel == null || playerLevel < requirement.getValue()) {
                return false;
            }
        }
        
        // Check quest requirements
        if (questRequirement != null && !completedQuests.contains(questRequirement)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Calculates the effective priority for this item considering Wintertodt-specific bonuses.
     * @return effective priority score
     */
    public int getEffectivePriority() {
        int effectivePriority = priority;
        
        // Bonus for warmth-providing items
        if (providesWarmth) {
            effectivePriority += 50;
        }
        
        // Bonus for damage reduction
        if (reducesDamage) {
            effectivePriority += 30;
        }
        
        // Bonus for weight reduction (negative weight)
        if (weight < 0) {
            effectivePriority += Math.abs(weight) * 5;
        }
        
        // Bonus for special effects
        if (hasSpecialEffect) {
            effectivePriority += 20;
        }
        
        return effectivePriority;
    }
    
    @Override
    public String toString() {
        return String.format("%s (ID: %d, Priority: %d/%d, Slot: %s)", 
                itemName, itemId, priority, getEffectivePriority(), slot.name());
    }
} 