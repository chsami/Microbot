package net.runelite.client.plugins.microbot.frostyastrals;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.pouch.Pouch;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.RunePouchType;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.stream.Stream;

@Slf4j
public class FrostyAstralsScript extends Script {

    private final FrostyAstralsConfig config;

    private static final WorldPoint ASTRAL_ALTAR = new WorldPoint(2154, 3864, 0);
    private static final WorldPoint MOONCLAN_BANK = new WorldPoint(2110, 3915, 0);

    @Inject
    private Client client;

    private boolean running = false;

    public FrostyAstralsScript(FrostyAstralsConfig config) {
        this.config = config;
    }

    public static int staffID = ItemID.DUST_BATTLESTAFF;

    @Override
    public boolean run() {
        if (!super.run()) {
            return false; // Stop if the script lifecycle is interrupted
        }

        log.info("Starting Frosty Astrals script...");

        while (running) {
            try {
//                executeRun();
                sleep(1000); // Adjust sleep time as needed for efficient loop execution
            } catch (Exception e) {
                log.error("Error during Frosty Astrals execution: ", e);
//                stop(); // Stop the script if an exception occurs
            }
        }

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        running = false;
        log.info("Frosty Astrals script stopped!");
    }

    private void handleBanking() {
        if (Rs2Bank.isOpen()) {
            if (Rs2Inventory.contains(ItemID.ASTRAL_RUNE)) {
                Rs2Bank.depositAll(ItemID.ASTRAL_RUNE);
            } else {
                Rs2Bank.withdrawAll(ItemID.PURE_ESSENCE);
            }
        } else {
            Rs2Bank.walkToBankAndUseBank(BankLocation.LUNAR_ISLE);
        }
    }

    private void handleSetup() {
//        Check if near bank
        if (MOONCLAN_BANK.distanceTo(client.getLocalPlayer().getWorldLocation()) <= 10) {
            handleBanking();
        } else {
            Rs2Walker.walkTo(MOONCLAN_BANK, 2);
            sleepUntil(() -> Rs2Player.distanceTo(MOONCLAN_BANK) <= 10);
        }

        if (Rs2Player.distanceTo(MOONCLAN_BANK) <= 10) {
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 5000);
        }

        equipOutfit();
        equipPouches();


    }
    /**
     * Equip graceful or raiments of the eye outfit if configured. As well as lantern and Dust battlestaff.
     */
    private void equipOutfit() {
        int [] outfitItems = null;
        // Equip graceful outfit if configured
        if (config.outfit() == FrostyAstralsConfig.OutfitOption.GRACEFUL) {
            outfitItems = new int[]{ItemID.GRACEFUL_HOOD, ItemID.GRACEFUL_CAPE, ItemID.GRACEFUL_TOP, ItemID.GRACEFUL_LEGS, ItemID.GRACEFUL_GLOVES, ItemID.GRACEFUL_BOOTS};

        } else if (config.outfit() == FrostyAstralsConfig.OutfitOption.RAIMENTS_OF_THE_EYE) {
            outfitItems = new int[]{ItemID.HAT_OF_THE_EYE, ItemID.ROBE_TOP_OF_THE_EYE, ItemID.ROBE_BOTTOMS_OF_THE_EYE, ItemID.BOOTS_OF_THE_EYE};
        }

        if (!Rs2Inventory.containsAll(outfitItems)) {
            outfitItems = null;
            log.warn("Missing outfit items, please ensure you have the correct items in your inventory.");
        }

        if (outfitItems != null) {
            for (int item : outfitItems) {
                if (Rs2Inventory.contains(item)) {
                    Rs2Bank.withdrawAndEquip(item);
                }
            }
        }

        if (config.useDustBattlestaff()) {
            if (Rs2Inventory.contains(ItemID.DUST_BATTLESTAFF)) {
                Rs2Bank.withdrawAndEquip(ItemID.DUST_BATTLESTAFF);
            } else {
                log.warn("Dust battlestaff not found in bank, please ensure you have a dust battlestaff in your bank.");
            }
        }
    }

    /**
     * Equip essence pouches if configured
     */
    private void equipPouches() {
        int[] pouches = new int[]{ItemID.SMALL_POUCH, ItemID.MEDIUM_POUCH, ItemID.LARGE_POUCH, ItemID.GIANT_POUCH, ItemID.COLOSSAL_POUCH};
        if (config.useEssencePouches()) {
            for (int pouch : pouches) {
                if (Rs2Inventory.contains(pouch)) {
                    Rs2Bank.withdrawAll(pouch);
                }
            }
        }
        if (config.useRunePouch()) {
            if (Rs2Inventory.contains(ItemID.RUNE_POUCH)) {
                log.info("Rune pouch already in inventory.");
            }
            if (Rs2Bank.hasItem(ItemID.RUNE_POUCH)) {
                Rs2Bank.withdrawAll(ItemID.RUNE_POUCH);

                // TODO: Check if rune pouch contains the correct runes: cosmic, astral, and law runes

            } else {
                log.warn("Rune pouch not found in bank, please ensure you have a rune pouch in your bank.");
            }
        }
    }

    private void runToAlter() {
        Rs2Walker.walkTo(ASTRAL_ALTAR, 2);
        sleepUntil(() -> Rs2Player.distanceTo(ASTRAL_ALTAR) <= 10);
    }

    private void craftRunes() {
        if (Rs2Player.distanceTo(ASTRAL_ALTAR) <= 10) {
            Rs2GameObject.interact(ObjectID.ALTAR_34771, "Craft-rune");
            Rs2Player.waitForXpDrop(Skill.RUNECRAFT, false);
            if (Rs2Inventory.hasAnyPouch() && !Rs2Inventory.allPouchesEmpty()) {
                Rs2Inventory.emptyPouches();
                return;
            }
        }
    }

    private void travelToBank() {
        if (Rs2Player.distanceTo(MOONCLAN_BANK) > 20) {
            Rs2Magic.cast(MagicAction.MOONCLAN_TELEPORT);
        }
        sleepUntil(() -> Rs2Player.distanceTo(MOONCLAN_BANK) <= 10);
        Rs2Walker.walkTo(MOONCLAN_BANK, 2);
    }

    private void repairPouches() {
        if (config.callNPCRepair() && Rs2Inventory.hasDegradedPouch() && Rs2Magic.getRequiredRunes(Rs2Spells.NPC_CONTACT, Rs2Magic.getRs2Staff(staffID), 1, true).isEmpty()) {
            Rs2Magic.repairPouchesWithLunar();
        }
    }

    private void bank() {
        repairPouches();
        if (Rs2Player.distanceTo(MOONCLAN_BANK) <= 10) {
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 5000);
        }
        if (Rs2Bank.isOpen()) {
            Rs2Bank.depositAll(ItemID.ASTRAL_RUNE);
            // Drink stamina potion if configured
            if (config.useStaminaPotions() && Rs2Bank.hasItem(ItemID.STAMINA_POTION1) && Rs2Player.getRunEnergy() < 63 && !Rs2Player.hasStaminaBuffActive()) {
                Rs2Bank.withdrawOne(ItemID.STAMINA_POTION1);
                Rs2Inventory.waitForInventoryChanges(() -> Rs2Inventory.contains(ItemID.STAMINA_POTION1));
                Rs2Inventory.interact(ItemID.STAMINA_POTION1, "Drink");
                Rs2Inventory.waitForInventoryChanges(1800);
            }

            // Get all RunePouchType IDs
            Integer[] runePouchIds = Arrays.stream(RunePouchType.values())
                    .map(RunePouchType::getItemId)
                    .toArray(Integer[]::new);

            // Get all eligible pouch IDs based on Runecrafting level
            Integer[] eligiblePouchIds = Arrays.stream(Pouch.values())
                    .filter(Pouch::hasRequiredRunecraftingLevel)
                    .flatMap(pouch -> Arrays.stream(pouch.getItemIds()).boxed())
                    .toArray(Integer[]::new);

            // Combine RunePouchType IDs and eligible pouch IDs into a single array
            Integer[] excludedIds = Stream.concat(Arrays.stream(runePouchIds), Arrays.stream(eligiblePouchIds))
                    .toArray(Integer[]::new);



            Rs2Bank.closeBank();
        }
    }
}
