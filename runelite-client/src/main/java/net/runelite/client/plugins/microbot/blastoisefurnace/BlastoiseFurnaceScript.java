

package net.runelite.client.plugins.microbot.blastoisefurnace;

import java.util.concurrent.TimeUnit;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;

import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.blastoisefurnace.enums.Bars;
import net.runelite.client.plugins.microbot.blastoisefurnace.enums.State;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.api.ItemID.COAL;
import static net.runelite.api.ItemID.GOLD_ORE;

public class BlastoiseFurnaceScript extends Script {
    static final int BAR_DISPENSER = 9092;
    private static final int MAX_ORE_PER_INTERACTION = 27;
    public static double version = 1.0;
    public static State state;
    static int staminaTimer;
    static int previousXP;
    static boolean waitingnexttick;
    static boolean waitingXpDrop;
    static boolean coalBagEmpty;
    static boolean primaryOreEmpty;
    static boolean secondaryOreEmpty;

    static {
        state = State.BANKING;
    }

    boolean initScript = false;
    private BlastoiseFurnaceConfig config;

    public BlastoiseFurnaceScript() {
    }

    private boolean hasRequiredOresForSmithing() {
        int primaryOre = this.config.getBars().getPrimaryOre();
        int secondaryOre = this.config.getBars().getSecondaryOre() == null ? -1 : this.config.getBars().getSecondaryOre();
        boolean hasPrimaryOre = Rs2Bank.hasItem(primaryOre);
        boolean hasSecondaryOre = secondaryOre != -1 && Rs2Bank.hasItem(secondaryOre);
        return hasPrimaryOre && hasSecondaryOre;
    }

    public boolean run(BlastoiseFurnaceConfig config) {
        staminaTimer = 0;
        this.initScript = true;
        this.config = config;
        Microbot.enableAutoRunOn = false;
        state = State.BANKING;
        previousXP = 0;
        waitingXpDrop = true;
        waitingnexttick = false;
        primaryOreEmpty = !Rs2Inventory.hasItem(config.getBars().getPrimaryOre());
        secondaryOreEmpty = !Rs2Inventory.hasItem(config.getBars().getSecondaryOre());
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();


        this.mainScheduledFuture = this.scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }

                if (!super.run()) {
                    return;
                }



                boolean hasGauntlets;
                switch (state) {
                    case BANKING:
                        Microbot.status = "Banking";
                        if (!Rs2Bank.isOpen()) {
                            System.out.println("Opening bank");
                            Rs2Bank.openBank();
                            this.sleepUntil(Rs2Bank::isOpen, 60000);
                        }

                        if (config.getBars().isRequiresCoalBag() && !Rs2Inventory.contains(ItemID.COAL_BAG_12019)) {
                            if (!Rs2Bank.hasItem(ItemID.COAL_BAG_12019)) {
                                Microbot.showMessage("get a coal bag");
                                this.shutdown();
                                return;
                            }

                            Rs2Bank.withdrawItem(ItemID.COAL_BAG_12019);
                        }

                        if (config.getBars().isRequiresGoldsmithGloves()) {
                            hasGauntlets = Rs2Inventory.contains(ItemID.GOLDSMITH_GAUNTLETS) || Rs2Equipment.isWearing(ItemID.GOLDSMITH_GAUNTLETS);
                            if (!hasGauntlets) {
                                if (!Rs2Bank.hasItem(ItemID.GOLDSMITH_GAUNTLETS)) {
                                    Microbot.showMessage("Need goldsmith gauntlets");
                                    this.shutdown();
                                    return;
                                }

                                Rs2Bank.withdrawItem(ItemID.GOLDSMITH_GAUNTLETS);
                            }
                        }

                        if (Rs2Inventory.hasItem("bar")) {
                            Rs2Bank.depositAllExcept(ItemID.COAL_BAG_12019, ItemID.GOLDSMITH_GAUNTLETS, ItemID.ICE_GLOVES, ItemID.SMITHS_GLOVES_I);


                        }

                        if (!this.hasRequiredOresForSmithing()) {
                            System.err.println("not enough required shit");
                            Rs2Player.logout();
                            this.shutdown();
                        }

                        if (!Rs2Player.hasStaminaBuffActive() && Microbot.getClient().getEnergy() < 8100) {
                            this.useStaminaPotions();
                        }

                        this.retrieveItemsForCurrentFurnaceInteraction();
                        state = State.SMITHING;
                        break;
                    case SMITHING:
                        System.out.println("clicking conveyor");
                        int primaryOre = this.config.getBars().getPrimaryOre();

                        if (barsInDispenser(config.getBars()) > 0) {
                            hasGauntlets = Rs2Widget.hasWidget("How many would you like");

                            while (true) {
                                if (hasGauntlets && super.run()) {
                                    Rs2Keyboard.keyPress(32);

                                    if (config.getBars().isRequiresGoldsmithGloves()) {
                                        Rs2Inventory.interact(ItemID.GOLDSMITH_GAUNTLETS, "Wear");
                                    }

                                    sleepUntil(() -> Rs2Inventory.contains(config.getBars().getBarID()), 2000);
                                    break;
                                }

                                // Check if the inventory is full before interacting with the dispenser
                                if (!Rs2Inventory.isFull()) {
                                    Rs2GameObject.interact(BAR_DISPENSER, "Take");

                                    hasGauntlets = sleepUntil(
                                            () -> Rs2Widget.hasWidget("How many would you like"),
                                            () -> Rs2Player.isMoving(),
                                            600
                                    );
                                } else {
                                    System.out.println("Inventory is full, stopping interaction.");
                                    break; // Stop taking if the inventory is full
                                }
                            }
                        }

                        this.openBank();
                        this.sleepUntil(Rs2Bank::isOpen, Rs2Player::isMoving, 300);
                        state = State.BANKING;
                }
            } catch (Exception ex) {

                System.out.println(ex.getMessage());
            }

        }, 00, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    private void retrieveCoalAndPrimary() {
        int primaryOre = this.config.getBars().getPrimaryOre();
        if (!Rs2Inventory.hasItem(primaryOre)) {
            Rs2Bank.withdrawAll(primaryOre);
            return;
        }

      boolean fullCoalBag = Rs2Inventory.interact(ItemID.COAL_BAG_12019, "Fill");
        if (!fullCoalBag)
            return;
        depositOre();
        Rs2Walker.walkFastCanvas(new WorldPoint(1940, 4962, 0));
        sleep(3400);
        sleepUntil(() -> {
            return barsInDispenser(config.getBars()) > 0;
        }, 20000);
    }

    private void retrievePrimary() {
        int primaryOre = config.getBars().getPrimaryOre();
        if (!Rs2Inventory.hasItem(primaryOre)) {
            Rs2Bank.withdrawAll(primaryOre);
            return;
        }
        depositOre();
        Rs2Walker.walkFastCanvas(new WorldPoint(1939, 4963, 0));
        sleep(3400);
        sleepUntil(() -> {
            return barsInDispenser(this.config.getBars()) > 0;
        }, 300000);
    }

    private void retrieveDoubleCoal() {
        if (!Rs2Inventory.hasItem(COAL)) {
            Rs2Bank.withdrawAll(COAL);
            return;
        }
        boolean fullCoalBag = Rs2Inventory.interact(ItemID.COAL_BAG_12019, "Fill");
        if (!fullCoalBag)
            return;
        depositOre();

    }

    private void retrieveCoal() {
        Rs2Bank.withdrawAll(ItemID.COAL);
        sleep(600);
        depositOre();

    }

    private void retrieveGold() {
        if (!Rs2Inventory.hasItem(GOLD_ORE)) {
            Rs2Bank.withdrawAll(GOLD_ORE);
            return;
        }
       depositOre();

        Rs2Walker.walkFastCanvas(new WorldPoint(1939, 4963, 0));

        sleep(3400);
        sleepUntil(() -> {
            return barsInDispenser(config.getBars()) > 5;
        }, 300000);
            Rs2Inventory.interact(ItemID.ICE_GLOVES, "wear");
    }

    private void retrieveItemsForCurrentFurnaceInteraction() {
        if (GOLD_ORE == config.getBars().getPrimaryOre()) {
        }

        switch (config.getBars()) {
            case GOLD_BAR:
                handleGold();
                break;
            case STEEL_BAR:
                handleSteel();
                break;
            case MITHRIL_BAR:
                handleMithril();
                break;
            case ADAMANTITE_BAR:
                handleAdamantite();
                break;
            case RUNITE_BAR:
                handleRunite();
        }

    }

    private void handleGold() {
        int coalInFurnace = Microbot.getVarbitValue(Varbits.BLAST_FURNACE_GOLD_ORE);
        switch (coalInFurnace / MAX_ORE_PER_INTERACTION) {
            case 8:
            case 7:
            case 6:
            case 5:
            case 4:
            case 3:
            case 2:
            case 1:
            case 0:
                retrieveGold();
            break;
            default:
                assert false : "how did you get there";

        }
    }

    private void handleSteel() {
        int coalInFurnace = Microbot.getVarbitValue(Varbits.BLAST_FURNACE_COAL);
        switch (coalInFurnace / MAX_ORE_PER_INTERACTION) {


            case 8:
                retrievePrimary();
                break;
            case 7:
            case 6:

            case 5:
            case 4:
            case 3:
            case 2:

            case 1:
                retrieveCoalAndPrimary();
                break;
            case 0:
                retrieveDoubleCoal();
                break;
            default:
                assert false : "how did you get there";
        }

    }

    private void handleMithril() {
        int coalInFurnace = Microbot.getVarbitValue(Varbits.BLAST_FURNACE_COAL);
        switch (coalInFurnace / MAX_ORE_PER_INTERACTION) {
            case 8:
                retrievePrimary();
                break;
            case 7:
            case 6:

            case 5:
            case 4:
            case 3:
            case 2:
                retrieveCoalAndPrimary();
                break;
            case 1:
                retrieveCoalAndPrimary();
                break;
            case 0:
                retrieveDoubleCoal();
                break;
            default:
                assert false : "how did you get there";

        }

    }

    private void handleAdamantite() {
        int coalInFurnace = Microbot.getVarbitValue(Varbits.BLAST_FURNACE_COAL);
        switch (coalInFurnace / MAX_ORE_PER_INTERACTION) {
            case 8:
                retrievePrimary();
                break;
            case 7:
            case 6:

            case 5:
            case 4:
            case 3:retrieveCoalAndPrimary();
            break;
            case 2:
                retrieveDoubleCoal();
                break;
            case 1:
                retrieveDoubleCoal();
                break;
            case 0:
                retrieveDoubleCoal();
                break;
            default:
                assert false : "how did you get there";
        }

    }

    private void handleRunite() {
        int coalInFurnace = Microbot.getVarbitValue(Varbits.BLAST_FURNACE_COAL);
        switch (coalInFurnace / MAX_ORE_PER_INTERACTION) {
            case 8:
                retrievePrimary();
                break;
            case 7:
            case 6:

            case 5:
            case 4:
                retrieveCoalAndPrimary();
                break;
            case 3:
                retrieveCoalAndPrimary();
                break;

            case 2:
                retrieveDoubleCoal();
                break;
            case 1:
                retrieveDoubleCoal();
                break;
            case 0:
                retrieveDoubleCoal();
                break;
                default:
                assert false : "how did you get there";
        }

    }

    private void openBank() {
        Rs2Bank.openBank(Rs2GameObject.findObjectById(26707));
    }

    private void useStaminaPotions() {

        boolean usedPotion = false;

        // Step 1: Keep using Energy potions until energy is above 71%
        while (Microbot.getClient().getEnergy() < 6900) {
            usedPotion = usePotionIfNeeded("Energy potion", 6900);
            if (!usedPotion) {
                break; // Exit if no Energy potion is available
            }
        }

        // Step 2: If energy is above 71% but below 81%, use Stamina potion if no stamina buff is active
        if (Microbot.getClient().getEnergy() < 8100 && !Rs2Player.hasStaminaBuffActive()) {
            usedPotion = usePotionIfNeeded("Stamina potion", 8100);
        }

        // Sleep after using a potion
        if (usedPotion) {
            this.sleep(161, 197);
        }
    }

    private boolean usePotionIfNeeded(String potionName, int energyThreshold) {
        if (Microbot.getClient().getEnergy() < energyThreshold) {
            if (withdrawPotion(potionName)) {
                if (drinkPotion(potionName)) {
                    depositItems(potionName);
                    return true; // Potion was successfully used
                }
            }
        }
        return false; // Potion was not used
    }

    private boolean withdrawPotion(String potionName) {
        Rs2Bank.withdrawOne(potionName);
        sleep(900);
        return true;
    }

    private boolean drinkPotion(String potionName) {
        Rs2Inventory.interact(potionName, "Drink");
        sleep(900);
        return true;
    }

    private void depositItems(String potionName) {
        if (Rs2Inventory.hasItem(potionName)) {
            Rs2Bank.depositOne(potionName);
        }
        if (Rs2Inventory.hasItem(ItemID.VIAL)) {
            Rs2Bank.depositOne(ItemID.VIAL);
        }
    }

    private void depositOre() {

        Rs2GameObject.interact(ObjectID.CONVEYOR_BELT, "Put-ore-on");
        sleepUntil(() -> !Rs2Inventory.isFull(), 7000); // Wait until the player stops moving

        if (this.config.getBars().isRequiresCoalBag()) {
            Rs2Inventory.interact(ItemID.COAL_BAG_12019, "Empty");
            sleepUntil(() -> Rs2Inventory.isFull(), 1000); // Wait for animation to finish

            Rs2GameObject.interact(ObjectID.CONVEYOR_BELT, "Put-ore-on");
            sleepUntil(() -> !Rs2Inventory.isFull(), 2000); // Wait until the player stops moving
        }
    }

    private boolean hasNoCoalOrOre() {
        return !Rs2Inventory.contains(ItemID.COAL) && !Rs2Inventory.contains(this.config.getBars().getPrimaryOre());
    }

    private boolean hasCoalOrOre() {
        return Rs2Inventory.contains(ItemID.COAL) || Rs2Inventory.contains(this.config.getBars().getPrimaryOre());
    }

    private boolean hasCoalOre() {
        return Rs2Inventory.contains(ItemID.COAL);
    }

    public int barsInDispenser(Bars bar) {
        switch (bar) {
            case GOLD_BAR:
                return Microbot.getVarbitValue(Varbits.BLAST_FURNACE_GOLD_BAR);
            case STEEL_BAR:
                return Microbot.getVarbitValue(Varbits.BLAST_FURNACE_STEEL_BAR);
            case MITHRIL_BAR:
                return Microbot.getVarbitValue(Varbits.BLAST_FURNACE_MITHRIL_BAR);
            case ADAMANTITE_BAR:
                return Microbot.getVarbitValue(Varbits.BLAST_FURNACE_ADAMANTITE_BAR);
            case RUNITE_BAR:
                return Microbot.getVarbitValue(Varbits.BLAST_FURNACE_RUNITE_BAR);
            default:
                return -1;
        }
    }


    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.devDebug = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.universalAntiban = true;
        Rs2AntibanSettings.contextualVariability = true;

    }


    public void shutdown() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
            ShortestPathPlugin.exit();
            if (Microbot.getClientThread().scheduledFuture != null)
                Microbot.getClientThread().scheduledFuture.cancel(true);
            initialPlayerLocation = null;
            Microbot.pauseAllScripts = false;
            Microbot.getSpecialAttackConfigs().reset();
        }



        state = State.BANKING;
        this.initScript = false;
        primaryOreEmpty = false;
        secondaryOreEmpty = false;
        super.shutdown();
    }
}
