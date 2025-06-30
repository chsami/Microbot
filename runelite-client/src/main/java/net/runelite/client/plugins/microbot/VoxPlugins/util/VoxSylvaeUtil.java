
package net.runelite.client.plugins.microbot.VoxPlugins.util;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.http.api.item.ItemPrice;
import java.awt.event.KeyEvent;
/**
 * Utility class with various static helper methods for Old School RuneScape automation.
 * This class provides utility functions for item information, NPC interaction, and
 * player movement that can be used across different Microbot plugins.
 */
@Slf4j
public class VoxSylvaeUtil {
        
    /**
     * Gets the name of an item from its ID
     * @param itemId The ID of the item
     * @return The name of the item
     */
    public static String getItemName(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> 
            Microbot.getItemManager().getItemComposition(itemId).getName()
        ).orElse("");
    }
    
    /**
     * Gets the ID of an item from its name
     * @param itemName The name of the item
     * @return The ID of the item, or -1 if not found
     */
    public static int getItemIdByName(String itemName) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            for (int i = 0; i < Microbot.getClient().getItemCount(); i++) {
                ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(i);
                if (itemComposition.getName().equalsIgnoreCase(itemName)) {
                    return i;
                }
            }
            return -1; // Item not found
        }).orElse(-1);
    }
    
    /**
     * Gets the stats of an item
     * @param itemId The ID of the item
     * @return The ItemStats object containing the item's stats
     */
    public static ItemStats getItemStats(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> 
            Microbot.getItemManager().getItemStats(itemId)
        ).orElse(null);
    }
    
    /**
     * Gets the equipment stats of an item
     * @param itemId The ID of the item
     * @return The ItemEquipmentStats object, or null if the item is not equipable
     */
    public static ItemEquipmentStats getEquipmentStats(int itemId) {
        try {
            final ItemStats itemStats = getItemStats(itemId);

            if (itemStats == null || !itemStats.isEquipable()) {
                return null;
            }

            final ItemEquipmentStats equipmentStats = itemStats.getEquipment();

            if (equipmentStats == null) {
                return null;
            }        
            return equipmentStats;
        } catch (Exception e) {
            String name = getItemName(itemId);
            log.error("Error getting item equipment stats for item: " + name + " with id {} error:- { }", itemId, e);
            return null;
        }
    }
    
    /**
     * Retrieves the best matching item price from a list based on input string
     * @param itemPrices List of item prices to search
     * @param originalInput Original input string to match
     * @return The best matching ItemPrice, or null if none found
     */
    public static ItemPrice retrieveItemPriceFromList(List<ItemPrice> itemPrices, String originalInput) {
        ItemPrice shortest = null;
        for (ItemPrice itemPrice : itemPrices) {
            if (itemPrice.getName().toLowerCase().equals(originalInput.toLowerCase())) {
                return itemPrice;
            }

            if (shortest == null || itemPrice.getName().length() < shortest.getName().length()) {
                shortest = itemPrice;
            }
        }

        // Take a guess
        return shortest;
    }

    /**
     * Checks if an item is noted
     * @param itemId The ID of the item
     * @return true if the item is noted, false otherwise
     */
    public static boolean isNoted(int itemId) {
        try {
            return Microbot.getClientThread().runOnClientThread(() -> {
                ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
                
                int linkedId = itemComposition.getLinkedNoteId();
                return itemComposition.getId() != linkedId && linkedId != -1;
            });
        } catch (Exception e) {
            log.error("Error checking if item is noted", e);
            return false;
        }
    }
    
    /**
     * Checks if an item is noteable
     * @param itemId The ID of the item
     * @return true if the item is noteable, false otherwise
     */
    public static boolean isNoteable(int itemId) {
        try {
            return Microbot.getClientThread().runOnClientThread(() -> {
                ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
                
                int linkedId = itemComposition.getLinkedNoteId();
                return linkedId != -1;
            });
        } catch (Exception e) {
            log.error("Error checking if item is noteable", e);
            return false;
        }
    }
    
    /**
     * Gets the linked note ID for an item
     * @param itemId The ID of the item
     * @return The linked note ID, or -1 if none
     */
    public static int getLinkedNoteId(int itemId) {
        try {
            return Microbot.getClientThread().runOnClientThread(() -> {
                ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
                return itemComposition.getLinkedNoteId();
            });
        } catch (Exception e) {
            log.error("Error getting linked note ID", e);
            return -1;
        }
    }
    
    /**
     * Checks if an item is tradeable
     * @param itemId The ID of the item
     * @return true if the item is tradeable, false otherwise
     */
    public static boolean isTradeable(int itemId) {
        try {
            return Microbot.getClientThread().runOnClientThread(() -> {
                ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
                return itemComposition.isTradeable();
            });
        } catch (Exception e) {
            log.error("Error checking if item is tradeable", e);
            return false;
        }
    }
    
    /**
     * Checks if an item is stackable
     * @param itemId The ID of the item
     * @return true if the item is stackable, false otherwise
     */
    public static boolean isStackableOnClientThread(int itemId) {
        try {
            return Microbot.getClientThread().runOnClientThread(() -> {
                ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
                return itemComposition.isStackable();
            });
        } catch (Exception e) {
            log.error("Error checking if item is stackable", e);
            return false;
        }
    }
    
    /**
     * Checks if an item is members-only
     * @param itemId The ID of the item
     * @return true if the item is members-only, false otherwise
     */
    public static boolean isMembersOnClientThread(int itemId) {
        try {
            return Microbot.getClientThread().runOnClientThread(() -> {
                ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
                return itemComposition.isMembers();
            });
        } catch (Exception e) {
            log.error("Error checking if item is members-only", e);
            return false;
        }
    }
    
    /**
     * Gets the available inventory options for an item
     * @param itemId The ID of the item
     * @return List of option strings
     */
    public static List<String> getOptionsOnClientThread(int itemId) {
        try {
            return Microbot.getClientThread().runOnClientThread(() -> {
                ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
                String[] actions = itemComposition.getInventoryActions();
                List<String> options = new ArrayList<>();
                for (String action : actions) {
                    if (action != null) {
                        options.add(action);
                    }
                }
                return options;
            });
        } catch (Exception e) {
            log.error("Error getting item options", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Gets the Wiki average price for an item
     * @param itemId The ID of the item
     * @return The Wiki average price, or 0 if not available
     */
    public static int getWikiAveragePrice(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemStats itemStats = Microbot.getItemManager().getItemStats(itemId);
            ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
            int wikiPrice = itemComposition != null ? itemComposition.getPrice() : 0;                                        
            return wikiPrice;
        }).orElse(null);
    }
    
    /**
     * Gets all item IDs matching a name
     * @param itemName The name of the item
     * @return List of matching item IDs
     */
    public static List<Integer> getItemIDs(String itemName) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < Microbot.getClient().getItemCount(); i++) {
                ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(i);
                if (itemComposition.getName().equalsIgnoreCase(itemName)) {
                    ids.add(i);
                }
            }
            return ids;
        }).orElse(new ArrayList<>());
    }
    
    /**
     * Gets the shop value of an item
     * @param itemId The ID of the item
     * @return The shop value, or 0 if not available
     */
    public static int getItemValueOnClientThread(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
            return itemComposition.getPrice();
        }).orElse(-1);
    }
    
    /**
     * Gets the high alchemy value of an item
     * @param itemId The ID of the item
     * @return The high alchemy value, or 0 if not available
     */
    public static int getItemHighAlchPriceOnClientThread(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
            int price = itemComposition.getPrice();
            return Math.round(price * 0.6f);
        }).orElse(null);
    }
    
    /**
     * Gets the lowest price for an item by name
     * @param itemName The name of the item
     * @return The lowest price, or 0 if not available
     */
    public static int getLowestBestPrice(String itemName) {
        List<Integer> itemIds = getItemIDs(itemName);
        int lowestPrice = Integer.MAX_VALUE;
        
        for (int itemId : itemIds) {
            int price = getItemValueOnClientThread(itemId);
            if (price > 0 && price < lowestPrice) {
                lowestPrice = price;
            }
        }
        
        return lowestPrice == Integer.MAX_VALUE ? 0 : lowestPrice;
    }
    
    /**
     * Attempts to parse an item ID from text
     * @param text The text to parse
     * @param withSearch Whether to search for matching items
     * @return The parsed item ID, or null if parsing fails
     */
    public static Integer TryParseItemIdFromText(String text, boolean withSearch) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            if (withSearch) {
                List<Integer> ids = getItemIDs(text);
                if (!ids.isEmpty()) {
                    return ids.get(0);
                }
            }
            return null;
        }
    }
    
    /**
     * Gets an NPC that is interacting with the player and matches the given IDs
     * @param NPCIds List of NPC IDs to check
     * @return The interacting NPC, or null if none found
     */
    public static Rs2NpcModel getInteractingNpc(List<Integer> NPCIds) {
        List<Rs2NpcModel> npcs = Rs2Npc.getNpcs(npc -> {
            if (npc == null || npc.isDead()) {
                return false;
            }
            
            if (NPCIds != null && !NPCIds.isEmpty() && !NPCIds.contains(npc.getId())) {
                return false;
            }
            
            return npc.getInteracting() == Microbot.getClient().getLocalPlayer();
        }).collect(Collectors.toList());
        
        return npcs.isEmpty() ? null : npcs.get(0);
    }
    
    /**
     * Gets all NPCs that are interacting with the player and match the given IDs
     * @param NPCIds List of NPC IDs to check
     * @return List of interacting NPCs
     */
    public static List<Rs2NpcModel> getInteractingNpcs(List<Integer> NPCIds) {
        return Rs2Npc.getNpcs(npc -> {
            if (npc == null || npc.isDead()) {
                return false;
            }
            
            if (NPCIds != null && !NPCIds.isEmpty() && !NPCIds.contains(npc.getId())) {
                return false;
            }
            
            return npc.getInteracting() == Microbot.getClient().getLocalPlayer();
        }).collect(Collectors.toList());
      }
    
    /**
     * Retrieves a list of NPCs within a specified range of the local player
     * @param NPCIds List of NPC IDs to check
     * @param range The maximum distance from the local player
     * @return List of NPCs within range
     */
    public static List<Rs2NpcModel> getNPCsWithinRange(List<Integer> NPCIds, int range) {
        return Rs2Npc.getNpcs(npc -> {
            if (npc == null || npc.isDead()) {
                return false;
            }
            
            if (NPCIds != null && !NPCIds.isEmpty() && !NPCIds.contains(npc.getId())) {
                return false;
            }
            
            WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
            WorldPoint npcLocation = npc.getWorldLocation();
            
            return playerLocation.distanceTo(npcLocation) <= range;
        }).collect(Collectors.toList());
    }
    
    /**
     * Calculates the current health of an NPC
     * @param target The NPC to check
     * @return The current health, or -1 if unavailable
     */
    public static int calculateHealth(NPC target) {
        if (target == null) {
            return -1;
        }
        
        int ratio = target.getHealthRatio();
        int scale = target.getHealthScale();
        
        if (ratio == -1 || scale == -1) {
            return -1;
        }
        
        // Calculate health percentage
        double percent = (double) ratio / scale;
        
        // Estimate max health based on NPC level
        int maxHealth = getMaxHealth(target);
        
        // Calculate current health
        int currentHealth = (int) Math.ceil(percent * maxHealth);
        
        return currentHealth;
    }
    
    /**
     * Estimates the maximum health of an NPC
     * @param target The NPC to check
     * @return The estimated maximum health
     */
    public static int getMaxHealth(NPC target) {
        if (target == null) {
            return 0;
        }
        
        int combatLevel = target.getCombatLevel();
        
        // Rough estimate based on combat level
        return combatLevel * 2;
    }
    
    /**
     * Gets a random movable world point near the specified point
     * @param point The center point
     * @param width The width of the area
     * @param height The height of the area
     * @param numberOfPoints Number of points to check
     * @return A random movable world point, or the original point if none found
     */
    public static WorldPoint getRandomMovableWorldPoint(WorldPoint point, int width, int height, int numberOfPoints) {
        for (int i = 0; i < numberOfPoints; i++) {
            int offsetX = Rs2Random.between(-width, width);
            int offsetY = Rs2Random.between(-height, height);
            
            WorldPoint newPoint = new WorldPoint(
                point.getX() + offsetX,
                point.getY() + offsetY,
                point.getPlane()
            );
            LocalPoint local = LocalPoint.fromWorld(Microbot.getClient(), newPoint);
            if (local == null) {
                continue; // Skip if the point is not valid in the current plane
            }
            // Check if the point is walkable
            if (Microbot.getClient().getCollisionMaps() != null && 
                Microbot.getClient().getCollisionMaps()[point.getPlane()].getFlags()[local.getSceneX()][local.getSceneY()] != 0 && Rs2Tile.isWalkable(newPoint)) {
                    
                return newPoint;
            }
        }
        
        // Return the original point if no valid point was found
        return point;
    }
    
    /**
     * Gets a list of random movable world points within an area
     * @param area The area to search
     * @param numberOfPoints Number of points to find
     * @return List of random movable world points
     */
    public static List<WorldPoint> getRandomMovableWorldPoints(WorldArea area, int numberOfPoints) {
        List<WorldPoint> points = new ArrayList<>();
        
        for (int i = 0; i < numberOfPoints; i++) {
            int offsetX = Rs2Random.between(0, area.getWidth());
            int offsetY = Rs2Random.between(0, area.getHeight());
            
            WorldPoint newPoint = new WorldPoint(
                area.getX() + offsetX,
                area.getY() + offsetY,
                area.getPlane()
            );
            LocalPoint local = LocalPoint.fromWorld(Microbot.getClient(), newPoint);
            if (local == null) {
                continue; // Skip if the point is not valid in the current plane
            }
            
            // Check if the point is walkable
            if (Microbot.getClient().getCollisionMaps() != null && 
                Microbot.getClient().getCollisionMaps()[area.getPlane()].getFlags()[local.getSceneX()][local.getSceneY()] != 0 && Rs2Tile.isWalkable(newPoint)) {
                points.add(newPoint);
            }
        }
        
        return points;
    }
    
    /**
     * Gets the player's experience in a skill
     * @param skill The skill to check
     * @return The experience in the skill
     */
    public static int getExperience(Skill skill) {
        return Microbot.getClient().getSkillExperience(skill);
    }
    
    /**
     * Gets the player's level in a skill
     * @param skill The skill to check
     * @return The level in the skill
     */
    public static int getLevel(Skill skill) {
        return Microbot.getClient().getRealSkillLevel(skill);
    }
    
    /**
     * Gets the player's boosted level in a skill
     * @param skill The skill to check
     * @return The boosted level in the skill
     */
    public static int getBoostedLevel(Skill skill) {
        return Microbot.getClient().getBoostedSkillLevel(skill);
    }
    




    public static void enablePlugin(Class pluginClass,  boolean devDebug){
        if (!Microbot.isPluginEnabled(pluginClass)) {
            String pluginName = pluginClass.getName();
            if (devDebug)
                Microbot.showMessage("Current plugin depend on the plugin \""+pluginName +"\", enabling it now.");

            Microbot.log("\""+pluginName +"\""+"not enabled, enabling it now.");
            
            Plugin PluginObject = Microbot.getPluginManager().getPlugins().stream()
                    .filter(x -> x.getClass().getName().equals(pluginName))
                    .findFirst()
                    .orElse(null);
            Microbot.startPlugin(PluginObject);
        }
    }
    public static boolean isPluginEnabled(Class pluginClass) {
        return Microbot.isPluginEnabled(pluginClass);
    }
    public static Plugin getPluginByName(String pluginName) {
        for (Plugin plugin : Microbot.getPluginManager().getPlugins()) {
            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            if (descriptor != null && descriptor.name().contains(pluginName)) {
                return plugin;
            }
        }
        return null;
    }
    public static String startPluginByName(String pluginName) {
            System.out.println("Starting startPlugin"); // Debug line
            try {
                Microbot.getPluginManager().setPluginEnabled(getPluginByName(pluginName), true);
                sleep(100);
                Microbot.getPluginManager().startPlugins();
                //if (!(currentPluginName == null))
                //    lastPluginName = currentPluginName;
                //currentPluginName = pluginName;
                //activity = "running";
                //pluginStartTime = System.currentTimeMillis();
                System.out.println("started plugin: " + pluginName);
                return pluginName;
            } catch (Exception e) {
                System.out.println("Failed to start plugin: " + e.getMessage());
                return null;
            }
        }

    public static void stopPluginByName(String pluginName) {
        try {
            Microbot.getPluginManager().setPluginEnabled(getPluginByName(pluginName), false);
            sleep(500);
            SwingUtilities.invokeLater(() -> {
                try {
                    Microbot.getPluginManager().stopPlugin(getPluginByName(pluginName));
                    System.out.println("stopped plugin: " + pluginName);
                } catch (PluginInstantiationException e) {
                    System.out.println("error stopPlugin"); // Debug line
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            System.out.println("Failed to stop plugin: " + e.getMessage());
        }
    }
     /**
     * Hops to a new world
     */
    private static void hopWorld() {
        // Stock level dropped below minimum, pause or stop execution
        System.out.println("Hopping world");
        Rs2Shop.closeShop();
        Rs2Bank.closeBank();
        sleep(2400, 4800); // this sleep is required to avoid the message: please finish what you're doing before using the world switcher.
        // This is where we need to hop worlds.
        int world = Login.getRandomWorld(true, null);
        boolean isHopped = Microbot.hopToWorld(world);
        if (!isHopped) return;
        Rs2Widget.sleepUntilHasWidget("Switch World");
        sleepUntil(() -> Rs2Widget.findWidget("Switch World", null, false) != null, 5000);
        Widget SwitchWorldWidget = Rs2Widget.findWidget("Switch World", null, false);
        if (SwitchWorldWidget == null) {
            System.out.println("Switch World Widget not found");
            return;
        }
        boolean result = Rs2Widget.clickWidget(SwitchWorldWidget);
        if (result) {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING, 5000);
            sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN, 5000);
            if (Microbot.getClient().getGameState() == GameState.LOGGED_IN) {
                System.out.println("Successfully hopped to world " + world);
            } else {
                Microbot.pauseAllScripts.set(true);;
                System.out.println("Failed to hop to world " + world);
            }
            
        }
    }
    public static int getSkillLevel(Skill skill) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {return Microbot.getClient().getRealSkillLevel(skill);}).orElse(-1);
    }
    public static int getSkillExperience(Skill skill) {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->{return Microbot.getClient().getSkillExperience(skill);} ).orElse(-1);        
    }
    public static int getTickCount() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {return Microbot.getClient().getTickCount();}).orElse(-1);
    }
    public static int getWorld() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {return Microbot.getClient().getWorld();}).orElse(-1);
    }
    public static GameState getGameState() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {return Microbot.getClient().getGameState();}).orElse(null);
    }
    public static Player getPlayer() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {return Microbot.getClient().getLocalPlayer();}).orElse(null);
    }   
    public static WorldPoint getHintArrowPoint() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {return Microbot.getClient().getHintArrowPoint();}).orElse(null);
    }
   
}
