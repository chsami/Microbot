package net.runelite.client.plugins.microbot.zerozero.moonlightmoth;

import net.runelite.api.NpcID;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;

public class MoonlightMothScript extends Script {

    String lastChatMessage = "";

    private enum State {
        CHECK_STATE,
        BANKING,
        SHOPPING,
        TRAVELLING,
        CATCHING
    }

    private State currentState = State.CHECK_STATE;

    public boolean run(MoonlightMothConfig config) {
        shutdown();
        currentState = State.CHECK_STATE;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                switch (currentState) {
                    case CHECK_STATE:
                        checkState(config);
                        break;
                    case BANKING:
                        handleBanking(config);
                        break;
                    case SHOPPING:
                        handleShopping(config);
                        break;
                    case TRAVELLING:
                        handleTravelling(config);
                        break;
                    case CATCHING:
                        handleCatching(config);
                        break;
                }

            } catch (Exception ex) {
                logOnceToChat(ex.getMessage(), false);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        return true;
    }

    private void checkState(MoonlightMothConfig config) {
        Microbot.status = "Checking current state...";

        if (config.equipGraceful() && isGracefulEquipped()) {
            logOnceToChat("Graceful equipment already equipped. Skipping graceful banking.", true);
        }

        // Check if Moonlight Moth is in the inventory
        if (Rs2Inventory.hasItem("Moonlight moth")) {
            logOnceToChat("Moonlight Moths found in inventory. Proceeding to BANKING.", false);
            currentState = State.BANKING;
            return;
        }

        boolean hasCoins = config.actionPreference().equals(MoonlightMothConfig.ActionPreference.SHOP) && Rs2Inventory.contains("Coins");
        boolean hasButterflyJars = Rs2Inventory.contains("Butterfly jar");
        boolean hasButterflyNetEquipped = Rs2Equipment.isWearing("Butterfly net");

        logOnceToChat("Inventory contains Butterfly jar: " + hasButterflyJars, true);
        logOnceToChat("Wearing Butterfly net: " + hasButterflyNetEquipped, true);

        if (config.actionPreference().equals(MoonlightMothConfig.ActionPreference.SHOP)) {
            logOnceToChat("Inventory contains Coins: " + hasCoins, true);
        }

        if (hasButterflyJars && hasButterflyNetEquipped && (config.actionPreference().equals(MoonlightMothConfig.ActionPreference.BANK) || hasCoins)) {
            logOnceToChat("All requirements met. Proceeding to travelling.", false);
            currentState = State.TRAVELLING;
            return;
        }

        if (!hasButterflyJars && (config.actionPreference().equals(MoonlightMothConfig.ActionPreference.SHOP) && hasCoins)) {
            logOnceToChat("Coins found but jars missing. Proceeding to SHOPPING.", false);
            currentState = State.SHOPPING;
            return;
        }

        logOnceToChat("Essential items missing or preference not satisfied. Proceeding to BANKING.", false);
        currentState = State.BANKING;
    }

    private void handleBanking(MoonlightMothConfig config) {
        Microbot.status = "Banking process initiated";

        if (!Rs2Bank.walkToBankAndUseBank()) {
            logOnceToChat("Failed to open bank.", false);
            return;
        }

        if (Rs2Inventory.hasItem("Moonlight moth")) {
            Rs2Bank.depositAll("Moonlight moth");
        }

        if (config.useStamina()) {
            useStaminaPotionIfNeeded(config.staminaThreshold());
        }

        if (config.equipGraceful()) {
            equipGraceful(config);
        }

        if (!Rs2Equipment.isWearing("Butterfly net")) {
            Rs2Bank.withdrawAndEquip("Butterfly net");
        }

        if (config.actionPreference().equals(MoonlightMothConfig.ActionPreference.BANK)) {
            int emptySlots = Rs2Inventory.getEmptySlots();
            if (emptySlots > 0) {
                Rs2Bank.withdrawX("Butterfly jar", emptySlots);
                logOnceToChat("Withdrew " + emptySlots + " Butterfly jars.", true);
            }
        }

        if (config.actionPreference().equals(MoonlightMothConfig.ActionPreference.SHOP)) {
            int requiredAmount = 1000;
            if (!Rs2Inventory.hasItemAmount("Coins", requiredAmount)) {
                Rs2Bank.withdrawX("Coins", 10000);
                logOnceToChat("Withdrew 10,000 coins for shopping.", true);
            } else {
                logOnceToChat("Sufficient coins already in inventory. Skipping withdrawal.", true);
            }
        }

        Rs2Bank.closeBank();
        sleepGaussian(600, 150);
        currentState = State.CHECK_STATE;
        logOnceToChat("Banking process completed.", true);
    }

    private void handleShopping(MoonlightMothConfig config) {
        Microbot.status = "Shopping process initiated";

        if (config.useStamina()) {
            useStaminaPotionIfNeeded(config.staminaThreshold());
        }

        if (!Rs2Walker.walkTo(new WorldPoint(1562, 3056, 0))) {
            logOnceToChat("Failed to walk to shop location.", true);
            return;
        }

        if (!Rs2Shop.openShop("Imia")) {
            logOnceToChat("Failed to open shop with Imia.", true);
            return;
        }

        Rs2Random.waitEx(1800, 300);

        if (!Rs2Shop.isOpen()) {
            logOnceToChat("Shop is not open. Retrying...", true);
            return;
        }

        int emptySlots = Rs2Inventory.getEmptySlots();

        if (emptySlots == 0) {
            logOnceToChat("No inventory space available to buy Butterfly jars.", true);
            Rs2Shop.closeShop();
            return;
        }

        if (!Rs2Shop.hasMinimumStock("Butterfly jar", emptySlots)) {
            logOnceToChat("Shop stock is insufficient for our needs. Hopping to a new world...", false);

            Rs2Shop.closeShop();
            sleepGaussian(600, 150);
            Rs2Player.hopIfPlayerDetected(0, 0, 0);
            sleepGaussian(3000, 500);

            logOnceToChat("Hopped to a new world. Restarting shopping process.", true);
            return;
        }

        boolean success = Rs2Shop.buyItem("Butterfly jar", "50");
        logOnceToChat(success ? "Successfully bought Butterfly jars." : "Failed to buy Butterfly jars.", true);

        Rs2Shop.closeShop();
        sleepGaussian(600, 150);
        currentState = State.TRAVELLING;
        logOnceToChat("Shopping process completed successfully.", false);
    }

    private void handleTravelling(MoonlightMothConfig config) {
        Microbot.status = "TRAVELLING";

        WorldPoint targetLocation = new WorldPoint(1568, 9439, 0);

        if (!Rs2Walker.walkTo(targetLocation)) {
            logOnceToChat("Failed to initiate walking to the target location.", true);
            return;
        }

        boolean arrived = sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(targetLocation) <= 5, 30000);

        logOnceToChat(arrived ? "Successfully arrived at the catching location." : "Failed to arrive at the catching location within the timeout period.", false);
        currentState = arrived ? State.CATCHING : State.CHECK_STATE;
    }

    private void handleCatching(MoonlightMothConfig config) {
        Microbot.status = "Catching Moonlight Moths...";

        if (!Rs2Inventory.hasItem("Butterfly jar")) {
            logOnceToChat("No Butterfly jars left. Switching to BANKING state.", true);
            currentState = State.BANKING;
            return;
        }

        if (config.enableWorldHopping() && Rs2Player.hopIfPlayerDetected(1, 5000, 3)) {
            logOnceToChat("Player detected nearby for too long. Hopped to a new world.", false);
            return; // Stop further actions until the world hop is complete
        }

        WorldArea excludedArea = new WorldArea(1550, 9426, 21, 8, 0);

        Rs2Npc.getNpcs(NpcID.MOONLIGHT_MOTH).filter(moth -> {
            WorldPoint location = Rs2Npc.getWorldLocation(moth);
            return location != null && !excludedArea.contains(location);
        }).findFirst().ifPresent(moth -> {
            if (!Rs2Player.isAnimating() && !Rs2Player.isInteracting()) {
                if (Rs2Npc.interact(moth, "Catch")) {
                    logOnceToChat("Attempting to catch Moonlight Moth at: " + moth.getWorldLocation(), true);
                    Rs2Player.waitForAnimation(2000);
                } else {
                    logOnceToChat("Failed to interact with Moonlight Moth.", true);
                }
            } else {
                logOnceToChat("Player is already performing an action. Waiting...", true);
            }
        });
    }

    private void checkBeforeWithdrawAndEquip(String itemName) {
        if (!Rs2Equipment.isWearing(itemName)) {
            Rs2Bank.withdrawAndEquip(itemName);
        }
    }

    private void equipGraceful(MoonlightMothConfig config) {
        checkBeforeWithdrawAndEquip("GRACEFUL HOOD");
        checkBeforeWithdrawAndEquip("GRACEFUL CAPE");
        checkBeforeWithdrawAndEquip("GRACEFUL BOOTS");
        checkBeforeWithdrawAndEquip("GRACEFUL GLOVES");
        checkBeforeWithdrawAndEquip("GRACEFUL TOP");
        checkBeforeWithdrawAndEquip("GRACEFUL LEGS");
    }

    private boolean isGracefulEquipped() {
        return Rs2Equipment.isWearing("GRACEFUL HOOD")
                && Rs2Equipment.isWearing("GRACEFUL CAPE")
                && Rs2Equipment.isWearing("GRACEFUL BOOTS")
                && Rs2Equipment.isWearing("GRACEFUL GLOVES")
                && Rs2Equipment.isWearing("GRACEFUL TOP")
                && Rs2Equipment.isWearing("GRACEFUL LEGS");
    }

    private void useStaminaPotionIfNeeded(int staminaThreshold) {
        if (Rs2Inventory.hasItem("stamina potion")) {
            // Check if the player needs to drink the stamina potion
            if (!Rs2Player.hasStaminaActive() && Rs2Player.getRunEnergy() < staminaThreshold) {
                Rs2Inventory.interact("stamina potion", "drink");
                sleepGaussian(600, 150);
                logOnceToChat("Drank stamina potion.", true);
            }
        } else if (Rs2Bank.isOpen() || Rs2Bank.openBank()) {
            // Withdraw stamina potion if none are left in the inventory
            if (Rs2Bank.hasItem("stamina potion")) {
                Rs2Bank.withdrawOne("stamina potion");
                sleepUntil(() -> Rs2Inventory.hasItem("stamina potion"), 5000);
                logOnceToChat("Withdrew stamina potion from the bank.", true);
            } else {
                logOnceToChat("No stamina potions available in the bank. Continuing without stamina.", true);
            }
        }
    }


    void logOnceToChat(String message, boolean isDebug) {
        if (!message.equals(lastChatMessage)) {
            if (!isDebug || (isDebug && MoonlightMothConfig.debugMessages())) {
                Microbot.log(message);
            }
            lastChatMessage = message;
        }
    }

    public void stop() {
        logOnceToChat("MoonlightMoth stopped", false);
        currentState = State.CHECK_STATE;
        lastChatMessage = "";
        super.shutdown();
    }
}
