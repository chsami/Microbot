package net.runelite.client.plugins.microbot.OreWorks;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mining.enums.AltOre;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

enum State {
    MINING,
    RESETTING,
}

public class OreWorksScript extends Script {

    public static final String version = "1.0.0";
    private static final int GEM_MINE_UNDERGROUND = 11410;
    private static final int BASALT_MINE = 11425;
    private static final WorldPoint[] FURNACES = {
            new WorldPoint(3275, 3186, 0), // Al-Kharid
            new WorldPoint(3109, 3499, 0), // Edgeville
            new WorldPoint(2973, 3373, 0)  // Falador
    };

    boolean miningCoal = false;
    State state = State.MINING;

    public boolean run(OreWorksConfig config) {
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.1;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn() || Rs2AntibanSettings.actionCooldownActive) return;

                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                if (!config.ORE().hasRequiredLevel()) {
                    Microbot.log("You do not have the required mining level to mine this ore.");
                    return;
                }

                if (Rs2Equipment.isWearing("Dragon pickaxe")) {
                    Rs2Combat.setSpecState(true, 1000);
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Microbot.pauseAllScripts) return;

                switch (state) {
                    case MINING:
                        miningLogic(config);
                        break;
                    case RESETTING:
                        resettingLogic(config);
                        break;
                }

            } catch (Exception ex) {
                Microbot.log("Error: " + ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void miningLogic(OreWorksConfig config) {
        if (Rs2Inventory.isFull()) {
            miningCoal = false;
            if (config.smelt()) {
                Microbot.status = "Smelting ores...";
                doSmeltingLogic(config);
                Microbot.status = "Finished smelting.";
            }
            state = State.RESETTING;
            return;
        }

        if (miningCoal && !Rs2Inventory.hasItem(ItemID.COAL)) {
            miningCoal = false;
        }

        if ((miningCoal || (config.mineCoal() && Rs2Inventory.getEmptySlots() == config.coalAmount()))) {
            miningCoal = true;
            Microbot.status = "Mining Coal...";
            GameObject coalRock = Rs2GameObject.findReachableObject("Coal rocks", true, config.distanceToStray(), initialPlayerLocation);
            if (coalRock != null && Rs2GameObject.interact(coalRock)) {
                Rs2Player.waitForXpDrop(Skill.MINING, true);
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
            return;
        }

        Microbot.status = "Mining " + config.ORE().getName() + "...";
        GameObject rock = Rs2GameObject.findReachableObject(config.ORE().getName(), true, config.distanceToStray(), initialPlayerLocation);
        if (rock != null && Rs2GameObject.interact(rock)) {
            Rs2Player.waitForXpDrop(Skill.MINING, true);
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
        }
    }

    private void resettingLogic(OreWorksConfig config) {
        if (config.smelt() && inventoryHasOres(config)) {
            Microbot.status = "Resmelting leftovers...";
            doSmeltingLogic(config);
            return;
        }

        List<String> itemsToBank = config.smelt()
                ? Collections.singletonList(config.barsToBank().toString().toLowerCase())
                : Arrays.stream(config.itemsToBank().split(",")).map(String::trim).map(String::toLowerCase).collect(Collectors.toList());

        if (config.useBank()) {
            Microbot.status = "Walking to bank...";
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.walkToBankAndUseBank();
                sleep(1000);
                if (!Rs2Bank.isOpen()) {
                    Microbot.log("Failed first bank open attempt, retrying...");
                    Rs2Bank.walkToBankAndUseBank();
                    return;
                }
            }
            for (String itemName : itemsToBank) {
                Rs2Bank.depositAll(itemName);
            }
            Rs2Bank.closeBank();
            if (initialPlayerLocation != null) {
                Microbot.status = "Walking back to mining...";
                Rs2Walker.walkTo(initialPlayerLocation, config.distanceToStray());
            }
        } else {
            Microbot.status = "Dropping ores...";
            Rs2Inventory.dropAllExcept(false, config.interactOrder(),
                    Arrays.stream(config.itemsToKeep().split(",")).map(String::trim).toArray(String[]::new));
        }
        state = State.MINING;
    }

    private void doSmeltingLogic(OreWorksConfig config) {
        WorldPoint nearestFurnace = Arrays.stream(FURNACES)
                .min((a, b) -> Integer.compare(Rs2Player.getWorldLocation().distanceTo(a), Rs2Player.getWorldLocation().distanceTo(b)))
                .orElse(FURNACES[0]);

        if (Rs2Player.getWorldLocation().distanceTo(nearestFurnace) > 8) {
            Microbot.status = "Walking to furnace...";
            Rs2Walker.walkTo(nearestFurnace);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(nearestFurnace) <= 5, 10000);
        }

        GameObject furnace = Rs2GameObject.findObject("furnace", true, 10, false, Rs2Player.getWorldLocation());
        if (furnace == null) {
            Microbot.log("No furnace found after walking! Skipping smelting...");
            return;
        }

        Rs2Random.waitEx(500, 250);

        if (Rs2GameObject.interact(furnace, "smelt")) {
            if (Rs2Widget.sleepUntilHasWidgetText("What would you like to smelt?", 270, 5, false, 5000)) {
                selectSmeltBar(config);
                Rs2Widget.sleepUntilHasNotWidgetText("What would you like to smelt?", 270, 5, false, 10000);

                int attempts = 0;
                while (inventoryHasOres(config) && attempts < 50) {
                    sleepUntil(() -> Rs2Player.isAnimating(), 4000);
                    sleepUntil(() -> !Rs2Player.isAnimating(), 4000);
                    Rs2Antiban.actionCooldown();
                    attempts++;
                }
                Microbot.log("Finished smelting.");
            } else {
                Microbot.log("Smelting interface didn't open, trying again...");
            }
        } else {
            Microbot.log("Failed to interact with furnace.");
        }
    }

    private void selectSmeltBar(OreWorksConfig config) {
        String ore = config.ORE().name();
        AltOre altOre = config.altOre();
        boolean mineCoal = config.mineCoal();

        if (ore.equals("IRON") && !mineCoal) Rs2Keyboard.typeString("2");
        else if (ore.equals("IRON") && mineCoal) Rs2Keyboard.typeString("4");
        else if ((ore.equals("TIN") && altOre == AltOre.COPPER) || (ore.equals("COPPER") && altOre == AltOre.TIN)) Rs2Keyboard.typeString("1");
        else if (ore.equals("SILVER")) Rs2Keyboard.typeString("3");
        else if (ore.equals("GOLD")) Rs2Keyboard.typeString("5");
        else if (ore.equals("MITHRIL") && altOre == AltOre.COAL) Rs2Keyboard.typeString("6");
        else if (ore.equals("ADAMANTITE") && altOre == AltOre.COAL) Rs2Keyboard.typeString("7");
        else if (ore.equals("RUNITE") && altOre == AltOre.COAL) Rs2Keyboard.typeString("8");
        else {
            Microbot.log("Defaulting to Iron bar.");
            Rs2Keyboard.typeString("2");
        }
    }

    private boolean inventoryHasOres(OreWorksConfig config) {
        return hasPrimaryOre(config) && (config.altOre() == AltOre.NONE || hasAltOre(config));
    }

    private boolean hasPrimaryOre(OreWorksConfig config) {
        switch (config.ORE()) {
            case IRON: return Rs2Inventory.hasItem(ItemID.IRON_ORE);
            case TIN: return Rs2Inventory.hasItem(ItemID.TIN_ORE);
            case COPPER: return Rs2Inventory.hasItem(ItemID.COPPER_ORE);
            case MITHRIL: return Rs2Inventory.hasItem(ItemID.MITHRIL_ORE);
            case ADAMANTITE: return Rs2Inventory.hasItem(ItemID.ADAMANTITE_ORE);
            case RUNITE: return Rs2Inventory.hasItem(ItemID.RUNITE_ORE);
            default: return false;
        }
    }

    private boolean hasAltOre(OreWorksConfig config) {
        switch (config.altOre()) {
            case COAL: return Rs2Inventory.hasItem(ItemID.COAL);
            case TIN: return Rs2Inventory.hasItem(ItemID.TIN_ORE);
            case COPPER: return Rs2Inventory.hasItem(ItemID.COPPER_ORE);
            default: return false;
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }
}