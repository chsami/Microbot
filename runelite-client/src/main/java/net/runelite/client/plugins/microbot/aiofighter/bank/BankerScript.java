package net.runelite.client.plugins.microbot.aiofighter.bank;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.aiofighter.shop.ShopItem;
import net.runelite.client.plugins.microbot.aiofighter.shop.ShopScript;
import net.runelite.client.plugins.microbot.aiofighter.shop.ShopType;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.slayer.Rs2Slayer;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Slf4j
public class BankerScript extends Script {
    AIOFighterConfig config;


    boolean initialized = false;
    public static boolean inventorySetupChanged = false;
    private static boolean bankingTriggered = false;
    @Inject
    private MInventorySetupsPlugin inventorySetupsPlugin;

    public boolean run(AIOFighterConfig config) {
        this.config = config;
        this.inventorySetupsPlugin = Microbot.getPluginManager().getPlugins().stream()
                .filter(plugin -> plugin instanceof MInventorySetupsPlugin)
                .map(plugin -> (MInventorySetupsPlugin) plugin)
                .findFirst()
                .orElse(null);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if(!super.run()) return;
                if (config.bank() && needBanking() && ! AIOFighterPlugin.needShopping) {
                    if (config.eatFoodForSpace() && Rs2Inventory.isFull() && !Rs2Inventory.getInventoryFood().isEmpty())
                        if (Rs2Player.eatAt(100))
                            return;

                    if(handleBanking()){
                        Microbot.log("Banking handled successfully.");
                    }
                } else if (!needBanking() &&
                        config.centerLocation().distanceTo(Rs2Player.getWorldLocation()) > config.attackRadius() &&
                        !config.centerLocation().equals(new WorldPoint(0, 0, 0))) {

                    boolean shouldWalk = false;

                    if (config.slayerMode()) {
                        Map<Integer,Integer> missingIds = Rs2Walker.getMissingTransportItemIdsWithQuantities(
                                Rs2Walker.getTransportsForDestination(config.centerLocation(),true));
                        if (!missingIds.isEmpty()) {
                            Microbot.log("Missing items: " + missingIds);
                            shouldWalk = handleTeleports(missingIds);
                            Microbot.log("Should walk(Slayer Mode): " + shouldWalk);
                        }
                        else shouldWalk = true;
                    }
                    else {
                        shouldWalk = true;
                        Microbot.log("Should walk: " + shouldWalk);
                    }

                    if (shouldWalk && Rs2Bank.closeBank()) {
                         AIOFighterPlugin.setState(State.WALKING);
                        Microbot.log("Walking to center location: " + config.centerLocation());
                        if (Rs2Walker.walkTo(config.centerLocation())) {
                             AIOFighterPlugin.setState(State.IDLE);

                        }
                    }
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 2000, TimeUnit.MILLISECONDS);
        return true;
    }

//    public boolean needsBanking() {
//        return (isUpkeepItemDepleted(config) && config.bank()) || (Rs2Inventory.getEmptySlots() <= config.minFreeSlots() && config.bank()) || needSlayerItems() || inventorySetupChanged;
//    }
    /**
     * Returns true if the player needs to bank (e.g., missing potions, full inventory).
     */
    public boolean needBanking() {
        if(config.currentInventorySetup() == null){
            if(config.defaultInventorySetup() != null) {
                AIOFighterPlugin.setCurrentSlayerInventorySetup(config.defaultInventorySetup());
            } else {
                Microbot.log("No inventory setup configured, skipping banking.");
                return false;
            }
        }        
        if(!config.bank()){
            return false;
        }

        if(bankingTriggered) {
            return true;
        }

        // (1) If inventory is full, we need to bank
        if (Rs2Inventory.isFull()) {
            Microbot.log("Inventory is full, triggering banking.");
            bankingTriggered = true;
            Rs2Inventory.waitForInventoryChanges(2000);
            return true;
        }

        // (2) If there are too few empty slots, missing slayer items, or the inventory setup changed
        if ((Rs2Inventory.getEmptySlots() <= config.minFreeSlots() && config.bank()) || needSlayerItems() || inventorySetupChanged) {
            Microbot.log("Low free slots, missing slayer items, or inventory setup changed, triggering banking.");
            bankingTriggered = true;
            return true;
        }
        
        // Double-check if inventory setup is still valid
        if (config.currentInventorySetup() == null){
            return false; // No current inventory setup, and also no default setup, so no need to bank because of the inventory setup
        }

        String setupName = null;
        if (config.slayerMode()) {
            if (config.currentInventorySetup() != null) {
                setupName = config.currentInventorySetup().getName();
            }
        } else {
            if (config.inventorySetup() != null) {
                setupName = config.inventorySetup().getName();
            }
        }
        
        if (setupName == null) {
            Microbot.log("Invalid inventory setup name, skipping banking.");
            return false;
        }

        Rs2InventorySetup inventorySetup = new Rs2InventorySetup(setupName, mainScheduledFuture);

        // (3) If food is required but not available
        if (needsFood(inventorySetup)) {
            Microbot.log("Food required but not available, triggering banking.");
            return true;
        }

        // (4) For each potion type, check if the setup requires it and if the player is missing it
        if (needsPotion(inventorySetup, Rs2Potion.getPrayerPotionsVariants())) {
            Microbot.log("Prayer potion required but not available, triggering banking.");
            return true;
        }
        if (needsPotion(inventorySetup, Rs2Potion.getRangePotionsVariants())) {
            Microbot.log("Range potion required but not available, triggering banking.");
            return true;
        }
        if (needsPotion(inventorySetup, Rs2Potion.getCombatPotionsVariants())) {
            Microbot.log("Combat potion required but not available, triggering banking.");
            return true;
        }
        if (needsPotion(inventorySetup, Rs2Potion.getMagicPotionsVariants())) {
            Microbot.log("Magic potion required but not available, triggering banking.");
            return true;
        }
        if (needsPotion(inventorySetup, Collections.singletonList(Rs2Potion.getStaminaPotion()))) {
            Microbot.log("Stamina potion required but not available, triggering banking.");
            return true;
        }
        if (needsPotion(inventorySetup, Rs2Potion.getRestoreEnergyPotionsVariants())) {
            Microbot.log("Restore energy potion required but not available, triggering banking.");
            return true;
        }
        if (needsPotion(inventorySetup, Rs2Potion.getAntiPoisonVariants())) {
            Microbot.log("Anti-poison potion required but not available, triggering banking.");
            return true;
        }
        if (needsPotion(inventorySetup, Rs2Potion.getAntifirePotionsVariants())) {
            Microbot.log("Antifire potion required but not available, triggering banking.");
            return true;
        }

        return false;
    }


    /**
     * Checks if the given setup requires any variant of a specific potion but the player does not have it.
     */
    private static boolean needsPotion(Rs2InventorySetup setup, List<String> potionVariants)
    {
        // If the setup doesn't contain any of these variants, no need to bank for them
        if (!setupHasPotion(setup, potionVariants))
        {
            return false;
        }
        // The setup does require it; if we don't have it, we need to bank
        return !Rs2Inventory.hasItem(potionVariants.toArray(String[]::new));
    }

    /**
     * Checks if the given setup has any item matching any of the given potion variants.
     */
    private static boolean setupHasPotion(Rs2InventorySetup setup, List<String> potionVariants)
    {
        return setup.getInventoryItems().stream().anyMatch(item ->
        {
            if (item.getName() == null)
            {
                return false;
            }
            // Strip off any "(x doses)" part
            String itemBaseName = item.getName().split("\\(")[0].trim().toLowerCase(Locale.ENGLISH);

            // Check if that base name matches any potion variant
            return potionVariants.stream().anyMatch(variant ->
            {
                String variantLower = variant.toLowerCase(Locale.ENGLISH);
                return variantLower.contains(itemBaseName);
            });
        });
    }

    // check if we need food
    private static boolean needsFood(Rs2InventorySetup setup)
    {
        // if setup doesn't have any food, no need to bank for it
        if (!setupHasFood(setup))
        {
            return false;
        }
        // setup does require food; if we don't have it, we need to bank
        return Rs2Inventory.getInventoryFood().isEmpty();
    }

    // check if setup has any food
    private static boolean setupHasFood(Rs2InventorySetup setup)
    {
        return setup.getInventoryItems().stream().anyMatch(item ->
        {
            if (item.getName() == null)
            {
                return false;
            }
            // get id of the item
            int itemId = item.getId();
            return
                    Rs2Food.getIds().contains(itemId);
        });
    }

    public boolean hasSlayerItems() {
        // If the current slayer task does not have a weakness, return true.
        if (!config.slayerHasTaskWeakness()) {
            return true;
        }
        boolean result = Rs2Inventory.contains(config.slayerTaskWeaknessItem());
        if (!result) {
            Microbot.log("hasSlayerItems(): Missing slayer task weakness item from inventory.");
        }
        return result;
    }


    public boolean hasProtectiveSlayerEquipment() {
        String needed = Rs2Slayer.getSlayerTaskProtectiveEquipment();

        if ("None".equals(needed)) {
            return true;
        }

        if (isBypassedBySlayerHelmet(needed)
                && Rs2Equipment.isWearing("Slayer helmet")) {
            return true;
        }

        // check if the needed item is "Rock hammer" or "Rock thrownhammer", if so, check if the player has it in inventory
        if (needed.equals("Rock hammer") || needed.equals("Rock thrownhammer")) {
            if (Rs2Inventory.contains(needed)) {
                return true;
            } else {
                Microbot.log("hasProtectiveSlayerEquipment(): Missing " + needed + " in inventory.");
                return false;
            }
        }

        boolean result = Rs2Equipment.isWearing(needed);
        if (!result) {
            Microbot.log("hasProtectiveSlayerEquipment(): Missing " + needed);
        }
        return result;
    }

    private boolean isBypassedBySlayerHelmet(String item) {
        switch (item) {
            case "Earmuffs":
            case "Facemask":
            case "Nose peg":
            case "Spiny helmet":
            case "Reinforced goggles":
                return true;
            default:
                return false;
        }
    }


    public boolean needDesertProtection() {
        boolean result = config.slayerLocation().contains("Desert")
                && !Rs2Inventory.contains(ItemID.WATER_SKIN4, ItemID.WATER_SKIN3, ItemID.WATER_SKIN2, ItemID.WATER_SKIN1);
        if (result) {
            Microbot.log("needDesertProtection(): In a Desert slayer location but missing watertskins.");
        }
        return result;
    }

    public boolean needProtectiveSlayerEquipment() {
        boolean result = config.slayerMode() && !hasProtectiveSlayerEquipment();
        if (result) {
            Microbot.log("needProtectiveSlayerEquipment(): In slayer mode but missing protective equipment.");
        }
        return result;
    }

    public boolean needSlayerItems() {
        boolean result = config.slayerMode()
                && (!hasSlayerItems() || needDesertProtection() || needProtectiveSlayerEquipment());
        if (result) {
            Microbot.log("needSlayerItems(): Banking triggered due to slayer item deficiency or equipment issues.");
        }
        return result;
    }




    public void withdrawUpkeepItems(AIOFighterConfig config) {
        if (config.useInventorySetup() || config.slayerMode()) {
            String setupName = null;
            if (config.slayerMode() && config.currentInventorySetup() != null) {
                setupName = config.currentInventorySetup().getName();
            } else if (!config.slayerMode() && config.inventorySetup() != null) {
                setupName = config.inventorySetup().getName();
            }
            
            if (setupName == null) {
                Microbot.log("Cannot load inventory setup - null setup name");
                return;
            }
            
            Rs2InventorySetup inventorySetup = new Rs2InventorySetup(setupName, mainScheduledFuture);
            if (!Rs2Bank.isOpen()) {
                Microbot.log("Bank didn't open, returning.");
                return;
            }
            if (config.currentInventorySetup() != null) {
                Microbot.log("Loading equipment for: " + config.currentInventorySetup().getName());
            } else {
                Microbot.log("Loading equipment for unknown setup (null)");
            }
            inventorySetup.loadEquipment();
            inventorySetup.loadInventory();


            Rs2Bank.emptyGemBag();
            Rs2Bank.emptyHerbSack();
            Rs2Bank.emptySeedBox();

            bankingTriggered = false;



            if(needSlayerItems()){
                if (config.slayerHasTaskWeakness()) {
                    if (Rs2Bank.hasBankItem(config.slayerTaskWeaknessItem())) {
                        Rs2ItemModel item = Rs2Bank.getBankItem(config.slayerTaskWeaknessItem());
                        if(Rs2Bank.hasBankItem(config.slayerTaskWeaknessItem(), item.isStackable() ? Rs2Slayer.getSlayerTaskSize() : 1,false)) {
                            Rs2Bank.withdrawX(true, config.slayerTaskWeaknessItem(), item.isStackable() ? Rs2Slayer.getSlayerTaskSize() : 1);
                        }
                        else {
                            ItemComposition itemComp = Microbot.getRs2ItemManager().getItemComposition(Rs2Slayer.getSlayerTaskWeakness());
                            boolean isGeItem = itemComp.getId() == ItemID.SHANTAY_PASS;
                            ShopScript.shopItems.add(new ShopItem(config.slayerTaskWeaknessItem(), itemComp.isStackable() ? Rs2Slayer.getSlayerTaskSize() : 1, itemComp.getId() , isGeItem ? ShopType.GRAND_EXCHANGE : ShopType.SLAYER_SHOP));
                             AIOFighterPlugin.needShopping = true;
                        }

                    }
                    else {
                        ItemComposition item = Microbot.getRs2ItemManager().getItemComposition(Rs2Slayer.getSlayerTaskWeakness());
                        boolean isGeItem = item.getId() == ItemID.SHANTAY_PASS;
                        ShopScript.shopItems.add(new ShopItem(config.slayerTaskWeaknessItem(), item.isStackable() ? Rs2Slayer.getSlayerTaskSize() : 1, item.getId() , isGeItem ? ShopType.GRAND_EXCHANGE : ShopType.SLAYER_SHOP));
                         AIOFighterPlugin.needShopping = true;
                    }
                }
                if ( needDesertProtection()) {
                    if(Rs2Bank.hasBankItem(ItemID.WATER_SKIN4, Rs2Inventory.emptySlotCount()/2))
                        Rs2Bank.withdrawX(true, ItemID.WATER_SKIN4, Rs2Inventory.emptySlotCount()/2);
                    else {
                        ShopScript.shopItems.add(new ShopItem("Waterskin(4)", Rs2Inventory.emptySlotCount()/2, ItemID.WATER_SKIN4 , ShopType.GRAND_EXCHANGE));
                         AIOFighterPlugin.needShopping = true;

                    }
                }
                if (needProtectiveSlayerEquipment()) {
                    if (Rs2Bank.hasBankItem(Rs2Slayer.getSlayerTaskProtectiveEquipment())) {
                        Rs2Bank.withdrawAndEquip(Rs2Slayer.getSlayerTaskProtectiveEquipment());
                        sleep(3000);
                        inventorySetupsPlugin.addInventorySetup("cache");
                        InventorySetup inventorySetup1 = MInventorySetupsPlugin.getInventorySetups().stream().filter(Objects::nonNull).filter(x -> x.getName().equalsIgnoreCase("cache")).findFirst().orElse(null);
                         AIOFighterPlugin.setCurrentSlayerInventorySetup(inventorySetup1);
                    } else {
                        ShopScript.shopItems.add(new ShopItem(Rs2Slayer.getSlayerTaskProtectiveEquipment(), 1, Microbot.getRs2ItemManager().getItemId(Rs2Slayer.getSlayerTaskProtectiveEquipment()), ShopType.SLAYER_SHOP));
                         AIOFighterPlugin.needShopping = true;

                    }


                }
//                if( AIOFighterBetaPlugin.needShopping)
//                    return;
            }
            inventorySetupChanged = false;
        }

    }

//    public boolean depositAllExcept(AIOFighterConfig config) {
//        List<Integer> ids = Arrays.stream(ItemToKeep.values())
//                .filter(item -> item.isEnabled(config))
//                .flatMap(item -> item.getIds().stream())
//                .collect(Collectors.toList());
//        Rs2Bank.depositAllExcept(ids.toArray(new Integer[0]));
//        return Rs2Bank.isOpen();
//    }

//    public boolean isUpkeepItemDepleted(AIOFighterConfig config) {
//        return Arrays.stream(ItemToKeep.values())
//                .filter(item -> item != ItemToKeep.TELEPORT && item.isEnabled(config))
//                .anyMatch(item -> item.getIds().stream().mapToInt(Rs2Inventory::count).sum() == 0);
//    }
//
//    public boolean goToBank() {
//        return Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 8);
//    }

    public boolean handleBanking() {
         AIOFighterPlugin.setState(State.BANKING);
        //bankingTriggered = true;
        Rs2Prayer.disableAllPrayers();
        if (Rs2Bank.walkToBankAndUseBank()) {
            withdrawUpkeepItems(config);
        }
        return !needBanking();
    }
    public boolean handleTeleports(Map<Integer,Integer> ids_quantity) {
         AIOFighterPlugin.setState(State.BANKING);
        Rs2Prayer.disableAllPrayers();
        if (Rs2Bank.walkToBankAndUseBank()) {
            for (Map.Entry<Integer, Integer> entry : ids_quantity.entrySet()) {
                int id = entry.getKey();
                int quantity = entry.getValue();
                if (Rs2Bank.hasItem(id)) {
                    Rs2Bank.withdrawX(true, id, quantity);
                    Rs2Inventory.waitForInventoryChanges(2000);
                    Rs2Random.waitEx(1200, 600);
                }
            }
        }
        int[] idArray = ids_quantity.keySet().stream()
                .mapToInt(Integer::intValue)
                .toArray();
        return Rs2Inventory.contains(idArray);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        // reset the initialized flag
        initialized = false;

    }
}
