package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Abstract base class for all resource-based conditions.
 * Provides common functionality for tracking items, materials and other resources.
 */
@Slf4j
public abstract class ResourceCondition implements Condition {
    
    @Getter
    protected boolean registered = false;
     
    
    @Override
    public ConditionType getType() {
        return ConditionType.RESOURCE;
    }
    
    public static boolean isNoted(int itemId) {
        try {
            return Microbot.getClientThread().runOnClientThread(() -> {
                    ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
                    

                    int linkedId = itemComposition.getLinkedNoteId();
                    if (linkedId <= 0) {
                        return false;
                    }
                    ItemComposition linkedItemComposition = Microbot.getItemManager().getItemComposition(linkedId);
                    boolean isNoted = itemComposition.getNote() == 799;
                    
                    assert linkedItemComposition != null 
                            && linkedItemComposition.isTradeable() != itemComposition.isTradeable() 
                            && linkedItemComposition.isStackable() != itemComposition.isStackable();
                    if (linkedItemComposition==null) {
                        return false;
                    }
                    boolean isNoteable = isNoteable(itemId);                    
                            
                    return isNoted && !isNoteable;
            });
        } catch (Exception e) {
            log.error("Error getting item noted status error:- { }",e);
            return false;
        }
    }
    public static boolean isNoteable(int itemId) {
        if (itemId <0) {
            return false;
        }
        try {
            return Microbot.getClientThread().runOnClientThread(() -> 
                {   ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
                    int linkedId = itemComposition.getLinkedNoteId();
                    if (linkedId <= 0) {
                        return false;
                    }      
                         
                    ItemComposition linkedItemComposition = Microbot.getItemManager().getItemComposition(linkedId);
                    if   (linkedItemComposition==null) {
                        return false;
                    }
                    boolean linkedIsNoted = linkedItemComposition.getNote() == 799;
                    boolean unlinkedIsNoted = itemComposition.getNote() == 799;       
                    return !unlinkedIsNoted;
                }                    
            );
        } catch (Exception e) {
            
            log.error("Error getting item tradeable status for item: " +  " with id {} error:- { }", itemId,e);
            return false;
        }
      
    }

    public static List<Rs2ItemModel> getNotedItems() {
       return Rs2Inventory.all().stream()
           .filter(item -> isNoted(item) && item.isStackable())
           .collect(Collectors.toList());
    }
    public static List<Rs2ItemModel> getUnNotedItems() {
        return Rs2Inventory.all().stream()
            .filter(item -> isUnNoted(item) && isNoteable(item.getId()))
            .collect(Collectors.toList());
    }
    public static boolean isNoted(Rs2ItemModel notedItem) {
        boolean isNoted= notedItem != null 
                    && isNoted(notedItem.getId());
        return isNoted;
    }
    public static boolean isUnNoted(Rs2ItemModel item) {
        if (item == null) {
            return false;
        }
        return !isNoted(item.getId());
    }
    /**
   
    
    /**
     * Default implementation for calculating progress percentage
     * Subclasses should override for more specific calculations
     */
    @Override
    public double getProgressPercentage() {
        return isSatisfied() ? 100.0 : 0.0;
    }
    
    /**
     * Default handler for item container changes
     * Subclasses should override to implement specific tracking
     */
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        // Default implementation does nothing
    }
    
    /**
     * Default GameTick handler that subclasses can override
     */
    @Subscribe
    public void onGameTick(GameTick gameTick) {
        // Default implementation does nothing
    }
    
    /**
     * Utility method to normalize item names for comparison
     */
    protected String normalizeItemName(String name) {
        if (name == null) return "";
        return name.toLowerCase().trim();
    }

    /**
     * Creates a pattern for matching NPC names
     */
    protected Pattern createItemPattern(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return Pattern.compile(".*");
        }
        
        // Check if the name is already a regex pattern
        if (itemName.startsWith("^") || itemName.endsWith("$") || 
            itemName.contains(".*") || itemName.contains("[") || 
            itemName.contains("(")) {
            return Pattern.compile(itemName);
        }
        
        // Otherwise, create a contains pattern
        return Pattern.compile(".*" + Pattern.quote(itemName) + ".*", Pattern.CASE_INSENSITIVE);
    }
}