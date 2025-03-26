package net.runelite.client.plugins.microbot.crafting.scripts;

import lombok.Getter;
import lombok.Setter;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.crafting.CraftingConfig;
import net.runelite.client.plugins.microbot.crafting.enums.Staffs;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

@Getter
@Setter
class ProgressiveStaffmakingModel {
    private Staffs itemToCraft;
}

public class StaffScript extends Script {

    ProgressiveStaffmakingModel model = new ProgressiveStaffmakingModel();

    int battleStaff = ItemID.BATTLESTAFF;
    Staffs itemToCraft;

    private int staffsWithdrawn = 0; // Tracks how many staffs to expect
    private int orbsWithdrawn = 0; // Tracks how many staffs to expect
    private boolean debugMessages = false; // Controls chatbox debug messages
    private boolean firstStaff = false; // Controls make "All" widget click

    public void run(CraftingConfig config) {
        sleepUntil(() -> Microbot.isLoggedIn(), 1500);
        debugMessage("Starting staff crafting script");

        if (config.staffType() == Staffs.PROGRESSIVE)
            calculateItemToCraft();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run())
                return;

            // Enable ingame chatbox debug messages according to config
            debugMessages = config.chatMessages();

            // If AFK config variable is set randomly sleep for a period between
            // 15 and 120 seconds before moving on if the trigger occurs
            if (config.Afk() && Rs2Random.nzRandom() < 0.15) {
                debugMessage(String.format("Going AFK for between 15 and 90 seconds"));
                Microbot.status = String.format("Taking randomised AFK break");
                Rs2Antiban.moveMouseOffScreen();
                Rs2Random.wait(15000, 90000);
            }

            if (Rs2Random.nzRandom() < 0.1) {
                debugMessage("Checking skills tab progress");
                if (!Rs2Tab.switchToSkillsTab())
                    Rs2Keyboard.keyPress(KeyEvent.VK_F1);
                Microbot.status = "Checking skills tab";
                Rs2Random.wait(1000, 3000);
            }

            try {
                if (config.staffType() == Staffs.PROGRESSIVE)
                    itemToCraft = model.getItemToCraft();
                else
                    itemToCraft = config.staffType();

                staffsWithdrawn = Rs2Inventory.count(battleStaff);
                orbsWithdrawn = Rs2Inventory.count(itemToCraft.getOrbID());

                if (Rs2Inventory.hasItem(battleStaff) &&
                        Rs2Inventory.hasItem(itemToCraft.getOrbID()))
                    craft(config);

                bank(config);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void bank(CraftingConfig config) {
        // Find, walk and turn to the closest bank
        GameObject bankObject = Rs2GameObject.findBank();
        if (!Rs2Walker.canReach(bankObject.getWorldLocation())) {
            debugMessage("Walking to closest bank");
            Microbot.status = "Walking to bank";
            Rs2Walker.walkCanvas(bankObject.getWorldLocation());
            Rs2Antiban.actionCooldown();
        }
        Rs2Camera.turnTo(bankObject.getLocalLocation());
        Rs2Bank.preHover();

        debugMessage("Opening bank interface");
        sleepUntil(() -> Rs2Bank.openBank(), 500);

        debugMessage("Depositing inventory into bank");
        Rs2Bank.depositAll();
        Rs2Antiban.actionCooldown();

        // Ensure the bank contains at least 1 of each required item
        verifyItemInBank("Battlestaff", battleStaff);
        verifyItemInBank(itemToCraft.getOrbName(), itemToCraft.getOrbID());

        debugMessage("Withdrawing staffs and orbs");
        Rs2Bank.withdrawX(itemToCraft.getOrbID(), 14);
        Rs2Inventory.waitForInventoryChanges(1800);
        Rs2Antiban.actionCooldown();
        Rs2Bank.withdrawX(battleStaff, 14);
        Rs2Inventory.waitForInventoryChanges(1800);
        Rs2Antiban.actionCooldown();

        debugMessage("Exiting bank interface");
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        if (Rs2Bank.isOpen())
            Rs2Bank.closeBank();

        // Store how many staffs & orbs were withdrawn incase uneven total
        staffsWithdrawn = Rs2Inventory.count(battleStaff);
        orbsWithdrawn = Rs2Inventory.count(itemToCraft.getOrbID());
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

        debugMessage("Starting crafting staffs");

        if (!Rs2Tab.switchToInventoryTab())
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);

        // Combine with orb first as battlestaffs have a skill requirement
        // This is pointless because it will auto use the "use" menu item
        if (!Rs2Inventory.hasItem(itemToCraft.getOrbID()) || !Rs2Inventory.hasItem(battleStaff))
            return;

        if (!Rs2Inventory.combine(itemToCraft.getOrbID(), battleStaff))
            Rs2Inventory.combine(itemToCraft.getOrbName(), "Battlestaff");
        Rs2Antiban.actionCooldown();

        debugMessage("Waiting for crafting interface");
        Rs2Dialogue.sleepUntilHasCombinationDialogue();

        // Only on the first time crafting the make "All" widget should be pressed
        if (!firstStaff) {
            firstStaff = true;
            debugMessage("First craft - selecting make all");
            if (!Rs2Widget.clickWidget(17694732))
                Rs2Widget.clickWidgetFast(Rs2Widget.getWidget(17694732, 17694732));
        }

        // Space triggers the make action to craft all battlestaffs
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        Rs2Antiban.actionCooldown();

        Microbot.status = "Crafting " + itemToCraft.getLabel();

        debugMessage("Waiting to finish crafting staffs");

        // Crafting any type of battle staff is a 2 tick action
        sleepUntilTick(Math.min(staffsWithdrawn, orbsWithdrawn) * 2);
        // Ensure the crafting is complete before moving on
        sleepUntil(() -> Rs2Inventory.hasItemAmount(itemToCraft.getItemID(),
                Math.min(staffsWithdrawn, orbsWithdrawn)) && !Rs2Player.isAnimating(), 1500);
        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();
    }

    public ProgressiveStaffmakingModel calculateItemToCraft() {
        int craftinglvl = Microbot.getClient().getRealSkillLevel(Skill.CRAFTING);
        if (craftinglvl < Staffs.EARTH_BATTLESTAFF.getLevelRequired()) {
            debugMessage("Crafting Water Battlestaffs");
            model.setItemToCraft(Staffs.WATER_BATTLESTAFF);
        } else if (craftinglvl < Staffs.FIRE_BATTLESTAFF.getLevelRequired()) {
            debugMessage("Crafting Earth Battlestaffs");
            model.setItemToCraft(Staffs.EARTH_BATTLESTAFF);
        } else if (craftinglvl < Staffs.AIR_BATTLESTAFF.getLevelRequired()) {
            debugMessage("Crafting Fire Battlestaffs");
            model.setItemToCraft(Staffs.FIRE_BATTLESTAFF);
        } else {
            debugMessage("Crafting Air Battlestaffs");
            model.setItemToCraft(Staffs.AIR_BATTLESTAFF);
        }
        return model;
    }

    @Override
    public void shutdown() {
        debugMessage("Shutting down staff crafter script");
        // Reset values
        staffsWithdrawn = 0;
        orbsWithdrawn = 0;
        debugMessages = false;
        firstStaff = false;
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }
        super.shutdown();
    }

    // Display debug messages in the ingame chatbox
    private void debugMessage(String str) {
        if (debugMessages)
            Microbot.log(String.format("[Crafter] %s", str));
    }
}
