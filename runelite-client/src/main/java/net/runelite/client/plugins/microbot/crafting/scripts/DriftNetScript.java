package net.runelite.client.plugins.microbot.crafting.scripts;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.crafting.CraftingConfig;
import net.runelite.client.plugins.microbot.crafting.enums.Loom;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

import java.awt.event.KeyEvent;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DriftNetScript extends Script {
    private int juteFibre = ItemID.JUTE_FIBRE;

    private int fibreWithdrawn;
    private boolean debugMessages = false; // Controls chatbox debug messages
    private boolean firstNet = false; // Controls make "All" widget click

    public void run(CraftingConfig config) {
        sleepUntil(() -> Microbot.isLoggedIn(), 1500);
        debugMessage("Starting drift net weaving script");

        if (config.loomLocation() == Loom.MUSEUM_CAMP) {
            debugMessage("Checking fossil island requirements are ment");
            Microbot.status = "Checking bank and loom exist first";
            boolean fossilLoom = Rs2GameObject.findObjectById(ObjectID.LOOM_30936) == null;
            if (!fossilLoom || Rs2Bank.getNearestBank() != BankLocation.FOSSIL_ISLAND) {
                Microbot.showMessage("You need to construct the Loom and Bank first", 60);
                shutdown();
            }
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run())
                return;

            // Enable ingame chatbox debug messages according to config
            debugMessages = config.chatMessages();

            // If AFK config variable is set randomly sleep for a period between
            // 15 and 90 seconds before moving on if the trigger occurs
            if (config.Afk() && (!Rs2Player.isMoving() || !Rs2Player.isInteracting()) && Rs2Random.nzRandom() < 0.05) {
                debugMessage(String.format("Going AFK for between 15 and 90 seconds"));
                int delay = Rs2Random.between(15000, 90000);
                Microbot.status = String.format("Taking short AFK break %02s", delay / 1000);
                Rs2Antiban.moveMouseOffScreen();
                sleep(delay);
            }

            if (Rs2Random.nzRandom() < 0.025) {
                debugMessage("Checking skills tab progress");
                if (!Rs2Tab.switchToSkillsTab())
                    Rs2Keyboard.keyPress(KeyEvent.VK_F1);
                Microbot.status = "Checking skills tab";
                sleep(1000, 3000);
                if (!Rs2Tab.switchToInventoryTab())
                    Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            }

            try {
                fibreWithdrawn = Rs2Inventory.count(juteFibre);

                if (fibreWithdrawn >= 2)
                    craft(config);

                bank(config);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void bank(CraftingConfig config) {
        // Find, walk and turn to the closest bank
        debugMessage("Walking to closest bank");
        Microbot.status = "Walking to bank";
        if (!Rs2Bank.walkToBank())
            Rs2Bank.openBank();
        if (!Rs2Bank.isOpen())
            Rs2Player.waitForWalking(18000);
        Rs2Camera.turnTo(Rs2GameObject.findBank());

        debugMessage("Opening bank interface");
        sleepUntil(() -> !Rs2Bank.isOpen() && Rs2Bank.openBank());
        Microbot.status = "Banking";
        debugMessage("Depositing inventory into bank");
        Rs2Bank.depositAll();

        // Ensure the bank contains at least 1 of each required item
        verifyItemInBank("Jute fibre", juteFibre);

        debugMessage("Withdrawing jute fibre");
        Rs2Bank.withdrawAll(juteFibre);
        Rs2Inventory.waitForInventoryChanges(18000);
        Rs2Antiban.actionCooldown();

        debugMessage("Exiting bank interface");
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        if (Rs2Bank.isOpen())
            Rs2Bank.closeBank();

        // Store how many jute fibres were withdrawn incase uneven total
        fibreWithdrawn = Rs2Inventory.count(juteFibre);
    }

    private void verifyItemInBank(String name, int item) {
        if (Rs2Bank.isOpen() && !Rs2Bank.hasItem(item)) {
            // Double check the required items are not noted in player's inventory
            if (Rs2Inventory.hasNotedItem(name) || Rs2Inventory.hasItem(item)) {
                Rs2Bank.depositAll(item);
                if (!Rs2Bank.closeBank())
                    Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                Rs2Antiban.actionCooldown();
                return;
            }
            Microbot.status = "[Shutting down] - Reason: " + name + " not found in the bank.";
            Microbot.showMessage(Microbot.status);
            this.shutdown();
        }
    }

    private void craft(CraftingConfig config) {
        // If bank is open exit with escape key then continue
        if (Rs2Bank.isOpen())
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);

        debugMessage("Starting weaving drift nets");

        if (!Rs2Tab.switchToInventoryTab())
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);

        if (!Rs2Inventory.hasItem(juteFibre))
            return;

        GameObject loom = Rs2GameObject.getGameObject(config.loomLocation().loomWorldPoint);
        if (Rs2Player.distanceTo(loom.getWorldLocation()) > 4) {
            if (Rs2Walker.canReach(loom.getWorldLocation())) {
                Rs2Walker.walkWithState(loom.getWorldLocation());
                Rs2Player.waitForWalking(18000);
            }
        }
        Rs2Camera.turnTo(loom.getLocalLocation());
        Rs2Inventory.useItemOnObject(juteFibre, loom.getId());

        // Only on the first time crafting the make "All" widget should be pressed
        if (!firstNet) {
            firstNet = true;
            debugMessage("First net - selecting make all");
            if (!Rs2Widget.clickWidget(17694732))
                Rs2Widget.clickWidgetFast(Rs2Widget.getWidget(17694732, 17694732));
        }

        // Keypress 2 after using on loom to trigger the make action to iiems otherwise
        // its
        // space once been used once as a repeat keycode
        Microbot.status = "Weaving drift nets";
        if (!Rs2Widget.sleepUntilHasWidgetText("Drift net", 17694736, 17694733, false, 300)) {
            if (firstNet)
                Rs2Keyboard.keyPress('2');
            else
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        }

        debugMessage("Waiting to finish weaving drift nets");
        // Weaving a drift net is a 3 tick action
        sleepUntilTick(fibreWithdrawn * 3);
        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();
    }

    public boolean hasRequirements(CraftingConfig config) {
        if (!Rs2Player.isMember())
            return false;
        if (Rs2Player.getRealSkillLevel(Skill.CRAFTING) < 26)
            return false;
        Loom loom = config.loomLocation();
        switch (loom) {
            case NONE:
                return false;
            case PRIFDDINAS:
                if (Rs2Player.getQuestState(Quest.SONG_OF_THE_ELVES) != QuestState.FINISHED)
                    return false;
                return true;
            case HOSIDIUS:
                return true;
            case SOUTH_FALADOR_FARM:
                return true;
            case ALDARIN:
                return true;
            case MUSEUM_CAMP:
                if (Rs2Player.getQuestState(Quest.BONE_VOYAGE) != QuestState.FINISHED)
                    return false;
                if (Rs2Player.getRealSkillLevel(Skill.CONSTRUCTION) < 29)
                    return false;
                if (Rs2Inventory.count(ItemID.OAK_PLANK) < 2 &&
                        Rs2Inventory.count("nails") < 5 &&
                        !Rs2Inventory.hasItem("Rope"))
                    return false;
                return true;
        }
        return true;
    }

    @Override
    public void shutdown() {
        Microbot.pauseAllScripts = true;
        debugMessage("Shutting down drift net weaver script");
        // Reset values
        debugMessages = false;
        firstNet = false;
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }
        super.shutdown();
    }

    // Display debug messages in the in game chatbox
    private void debugMessage(String str) {
        if (debugMessages)
            Microbot.log(String.format("[Drift Net] %s", str));
    }
}
