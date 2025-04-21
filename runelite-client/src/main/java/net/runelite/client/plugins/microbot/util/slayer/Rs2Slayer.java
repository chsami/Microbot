package net.runelite.client.plugins.microbot.util.slayer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EnumID;
import net.runelite.api.ItemID;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.MonsterLocation;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.slayer.enums.ProtectiveEquipment;
import net.runelite.client.plugins.microbot.util.slayer.enums.SlayerMaster;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.slayer.Task;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class Rs2Slayer {

    public static String slayerTaskMonsterTarget;
    public static List<String> blacklistedSlayerMonsters = new ArrayList<>();

    /**
     * Checks if the player has an active Slayer task.
     * Returns true if the player has a Slayer task, false otherwise.
     *
     * @return true if the player has a Slayer task, false otherwise
     */
    public static boolean hasSlayerTask() {
        int taskSize = getSlayerTaskSize();
        return taskSize > 0;
    }

    /**
     * Retrieves the size of the player's current Slayer task.
     *
     * @return the size of the player's current Slayer task as an integer
     */
    public static int getSlayerTaskSize() {
        return Microbot.getVarbitPlayerValue(VarPlayer.SLAYER_TASK_SIZE);
    }

    /**
     * Retrieves the name of the player's current Slayer task creature.
     *
     * @return the name of the Slayer task creature as a String, or null if the player does not have an active task
     */
    public static String getSlayerTask() {
        int taskId = Microbot.getVarbitPlayerValue(VarPlayer.SLAYER_TASK_CREATURE);
        if (taskId == 0) {
            return null;
        }
        return Microbot.getEnum(EnumID.SLAYER_TASK_CREATURE)
                .getStringValue(taskId);
    }

    /**
     * Retrieves the list of Slayer monster names based on the player's current Slayer task.
     * Returns null if the player does not have an active Slayer task.
     *
     * @return a List of String containing the names of Slayer monsters for the current task, or null if there is no active task
     */
    // get slayer monster names
    public static List<String> getSlayerMonsters() {
        String taskName = getSlayerTask();
        if (taskName == null) {
            return null;
        }
        // Check if the monster is blacklisted and remove it from the list
        return Rs2NpcManager.getSlayerMonstersByCategory(taskName).stream()
                .filter(monster -> !blacklistedSlayerMonsters.contains(monster))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the location of the Slayer task creature based on certain parameters.
     *
     * @param minClustering the minimum clustering monsters for the location
     * @param avoidWilderness true if the location should avoid the wilderness, false otherwise
     * @return the WorldPoint representing the location of the Slayer task creature
     */
    public static MonsterLocation getSlayerTaskLocation(int minClustering, boolean avoidWilderness) {
        List<String> names = getSlayerMonsters();
        if (names == null) {
            return null;
        }
        // Check if the monster is blacklisted and remove it from the list
        if (blacklistedSlayerMonsters != null) {
            names.removeAll(blacklistedSlayerMonsters);
        }

        MonsterLocation monsterLocation = Rs2NpcManager.getClosestLocation(names.get(0),minClustering,avoidWilderness);
        slayerTaskMonsterTarget = names.get(0);
        // if location is null, check next name
        if (monsterLocation == null) {
            for (int i = 1; i < names.size(); i++) {
                monsterLocation = Rs2NpcManager.getClosestLocation(names.get(i),minClustering,avoidWilderness);
                if (monsterLocation != null) {
                    slayerTaskMonsterTarget = names.get(i);
                    break;
                }
            }
        }
        assert monsterLocation != null;
        return monsterLocation;
    }

    public static MonsterLocation getSlayerTaskLocation(int minClustering) {
        return getSlayerTaskLocation(minClustering, true);
    }

    public static MonsterLocation getSlayerTaskLocation() {
        return getSlayerTaskLocation(3, true);
    }

    /**
     * Checks if the player's current Slayer task has a weakness.
     * Returns true if the player's Slayer task creature has a weakness, false otherwise.
     *
     * @return true if the player's Slayer task creature has a weakness, false otherwise
     */
    // has slayer task weakness
    public static boolean hasSlayerTaskWeakness() {
        String taskName = getSlayerTask();
        if (taskName == null) {
            return false;
        }
        return Objects.requireNonNull(Task.getTask(getSlayerTask())).getWeaknessItem() != -1;
    }

    /**
     * Retrieves the weakness item for the player's current Slayer task creature.
     *
     * This method first checks if the player has an active Slayer task. If there is no active task,
     * it returns null. Otherwise, it retrieves the weakness item ID for the Slayer task creature
     * and then the name of the item using the Microbot getItemManager to fetch the item composition.
     *
     * @return the name of the weakness item for the Slayer task creature as a String,
     *         or null if the player does not have an active task
     */
    // get weakness item for slayer task
    public static String getSlayerTaskWeaknessName() {
        String taskName = getSlayerTask();
        if (taskName == null) {
            return null;
        }
        int itemId = Objects.requireNonNull(Task.getTask(getSlayerTask())).getWeaknessItem();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(itemId).getName()).orElse("");
    }

    public static int getSlayerTaskWeakness() {
        String taskName = getSlayerTask();
        if (taskName == null) {
            return -1;
        }
        return Objects.requireNonNull(Task.getTask(getSlayerTask())).getWeaknessItem();
    }

    /**
     * Retrieves the weakness threshold for the player's current Slayer task.
     *
     * @return the weakness threshold as an integer value, or -1 if the player does not have an active Slayer task
     */
    // get weakness threshold for slayer task
    public static int getSlayerTaskWeaknessThreshold() {
        String taskName = getSlayerTask();
        if (taskName == null) {
            return -1;
        }
        return Objects.requireNonNull(Task.getTask(getSlayerTask())).getWeaknessThreshold();
    }

    /**
     * Retrieves the protective equipment recommended for the player's current Slayer task creature.
     * If the player does not have an active Slayer task, null is returned.
     *
     * @return the required protective equipment as a String. Returns "None" if no equipment is available.
     */
    // get protective equipment for slayer task
    public static String getSlayerTaskProtectiveEquipment() {
        String taskName = getSlayerTask();
        if (taskName == null) {
            return null;
        }
        String itemName = ProtectiveEquipment.getItemNameByCreature(getSlayerTask());
        return itemName == null ? "None" : itemName;
    }

    public static boolean walkToSlayerMaster(SlayerMaster master) {
        return Rs2Walker.walkTo(master.getWorldPoint());
    }

    public static List<Transport> prepareItemTransports(WorldPoint cachedMonsterLocation) {
        ShortestPathPlugin.getPathfinderConfig().setUseBankItems(true);
        List<Transport> transports = Rs2Walker.getTransportsForPath(Rs2Walker.getWalkPath(cachedMonsterLocation), 0)
                .stream()
                .filter(t -> t.getType() == TransportType.TELEPORTATION_ITEM || t.getType() == TransportType.FAIRY_RING)
                .peek(t -> {
                    if (t.getType() == TransportType.FAIRY_RING) {
                        t.setItemIdRequirements(Set.of(Set.of(ItemID.DRAMEN_STAFF, ItemID.LUNAR_STAFF)));
                    }
                })
                .collect(Collectors.toList());
        ShortestPathPlugin.getPathfinderConfig().setUseBankItems(false);

        transports
                .forEach(t -> log.info("Item required: " + t));

        return getMissingItemTransports(transports);
    }

    private static boolean hasRequiredTeleportItem(Transport transport) {
        if (transport.getType() == TransportType.FAIRY_RING) {
            return Rs2Inventory.hasItem(ItemID.DRAMEN_STAFF) ||
                    Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF) ||
                    Rs2Inventory.hasItem(ItemID.LUNAR_STAFF) ||
                    Rs2Equipment.isWearing(ItemID.LUNAR_STAFF);
        } else if (transport.getType() == TransportType.TELEPORTATION_ITEM) {
            return transport.getItemIdRequirements()
                    .stream()
                    .flatMap(Collection::stream)
                    .anyMatch(itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId));
        }
        return false;
    }

    private static List<Transport> getMissingItemTransports(List<Transport> transports) {
        return transports.stream()
                .filter(t -> !hasRequiredTeleportItem(t))
                .collect(Collectors.toList());
    }

    public static List<Integer> getMissingItemIds(List<Transport> transports) {
        return transports.stream()
                .flatMap(transport -> transport.getItemIdRequirements()
                        .stream()
                        .flatMap(Collection::stream)
                        .filter(Rs2Bank::hasItem)
                        .findFirst().stream())
                .collect(Collectors.toList());
    }

}
