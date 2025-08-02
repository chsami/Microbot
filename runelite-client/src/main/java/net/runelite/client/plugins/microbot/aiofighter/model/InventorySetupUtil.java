package net.runelite.client.plugins.microbot.aiofighter.model;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.bank.BankerScript;
import net.runelite.client.plugins.microbot.util.slayer.enums.SlayerTaskMonster;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
@Slf4j
public class InventorySetupUtil {
    public static AIOFighterConfig config;
    
    @Inject
    public InventorySetupUtil(AIOFighterConfig config) {
        InventorySetupUtil.config = config;
    }
    /**
     * Determines the necessary inventory setup for a given monster.
     *
     * @param monsterName the name of the monster (as defined in SlayerTaskMonster)
     * @return the InventorySetup configuration needed
     */
    public static void determineInventorySetup(String monsterName) {
        // Validate the monster name; if invalid, fallback to DEFAULT.
        log.info("Determining inventory setup for monster: " + monsterName);
        if(config.currentInventorySetup() == null) {
            log.info("Current inventory setup is null");
            log.info("Setting current inventory setup to default"+ config.defaultInventorySetup().getName());
            AIOFighterPlugin.setCurrentSlayerInventorySetup(config.defaultInventorySetup());
        }
        if (!SlayerTaskMonster.isValidMonster(monsterName)) {
            if (config.currentInventorySetup() != config.defaultInventorySetup()) {
                BankerScript.inventorySetupChanged = true;
            }
            AIOFighterPlugin.setCurrentSlayerInventorySetup(config.defaultInventorySetup());
        }

        // Retrieve the required items from the enum.
        String[] requiredItems = SlayerTaskMonster.getItemRequirements(monsterName);
        // If there are no required items (or the only element is "None"), return default.
        if (requiredItems.length == 0 ||
                (requiredItems.length == 1 && "None".equalsIgnoreCase(requiredItems[0]))) {
            AIOFighterPlugin.setCurrentSlayerInventorySetup(config.defaultInventorySetup());
        }

        // Convert the required items to a Set for easy lookup.
        Set<String> requiredSet = new HashSet<>(Arrays.asList(requiredItems));

        // Define which items belong to each specialized setup.
        Set<String> dragonWyvernItems = new HashSet<>(Arrays.asList(
                "Anti-dragon shield", "Dragonfire shield", "Antifire potion(4)", "Super antifire potion(4)",
                "Elemental shield", "Mind shield", "Ancient wyvern shield"
        ));

        Set<String> drakeItems = new HashSet<>(Arrays.asList(
                "Boots of stone", "Boots of brimstone", "Granite boots"
        ));

        Set<String> kuraskTurothItems = new HashSet<>(Arrays.asList(
                "Leaf-bladed spear", "Magic Dart", "Broad arrows", "Broad bolts"
        ));

        Set<String> caveMiscItems = new HashSet<>(Arrays.asList(
                "Bullseye lantern (lit)", "Lit bug lantern", "Lockpick", "Fishing explosive", "Slayer bell"
        ));

        // Check if any required item matches one of the specialized setups.
        if (!Collections.disjoint(requiredSet, dragonWyvernItems)) {
            if (config.currentInventorySetup() != config.dragonWyvernInventorySetup()) {
                BankerScript.inventorySetupChanged = true;
            }
            AIOFighterPlugin.setCurrentSlayerInventorySetup(config.dragonWyvernInventorySetup());
            return;
        }
        if (!Collections.disjoint(requiredSet, drakeItems)) {
            if (config.currentInventorySetup() != config.drakeInventorySetup()) {
                BankerScript.inventorySetupChanged = true;
            }
            AIOFighterPlugin.setCurrentSlayerInventorySetup(config.drakeInventorySetup());
            return;
        }
        if (!Collections.disjoint(requiredSet, kuraskTurothItems)) {
            if (config.currentInventorySetup() != config.kuraskTurothInventorySetup()) {
                BankerScript.inventorySetupChanged = true;
            }
            AIOFighterPlugin.setCurrentSlayerInventorySetup(config.kuraskTurothInventorySetup());
            return;
        }
        if (!Collections.disjoint(requiredSet, caveMiscItems)) {
            if (config.currentInventorySetup() != config.caveMiscellaneousInventorySetup()) {
                BankerScript.inventorySetupChanged = true;
            }
            AIOFighterPlugin.setCurrentSlayerInventorySetup(config.caveMiscellaneousInventorySetup());
            return;
        }

        // Fallback: if none of the specialized items are found, use the default setup.
        if (config.currentInventorySetup() != config.defaultInventorySetup()) {
            BankerScript.inventorySetupChanged = true;
        }
        AIOFighterPlugin.setCurrentSlayerInventorySetup(config.defaultInventorySetup());
    }
}