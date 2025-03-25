package net.runelite.client.plugins.microbot.crafting.scripts;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.AnimationID;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.crafting.CraftingConfig;
import net.runelite.client.plugins.microbot.crafting.enums.Staffs;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

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
        debugMessage("Starting staff crafting script");

        // Everyone makes mistakes
        Rs2AntibanSettings.simulateMistakes = true;

        if (config.staffType() == Staffs.PROGRESSIVE)
            calculateItemToCraft();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run())
                return;

            // Enable ingame chatbox debug messages according to config
            debugMessages = config.chatMessages();

            // If AFK config variable is set randomly sleep for a period between
            // 1 and 5 minutes before moving on if the trigger occurs
            if (config.Afk() && Math.random() < 0.1) {
                Rs2Antiban.moveMouseOffScreen();
                int breakDuration = Rs2Random.between(1, 5);
                debugMessage(String.format("Going AFK for: %d minutes", breakDuration));
                sleep(breakDuration * 1000 * 60);
            }

            try {
                if (config.staffType() == Staffs.PROGRESSIVE)
                    itemToCraft = model.getItemToCraft();
                else
                    itemToCraft = config.staffType();

                staffsWithdrawn = Rs2Inventory.count(battleStaff);
                orbsWithdrawn = Rs2Inventory.count(itemToCraft.getOrbID());
                if (staffsWithdrawn == 0 || orbsWithdrawn == 0)
                    bank(config);

                if (Rs2Inventory.hasItem(battleStaff) && Rs2Inventory.hasItem(itemToCraft.getOrbID())) {
                    craft(config);
                    sleepUntil(() -> !Rs2Player.isAnimating(), 15000);
                }

                bank(config);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void bank(CraftingConfig config) {
        // Turn camera to the bank - this seems more like a human interaction
        Rs2NpcModel bankNpc = Rs2Npc.getNearestNpcWithAction("Bank");
        Rs2Camera.turnTo(bankNpc);
        sleepUntil(() -> Rs2Bank.openBank(bankNpc), 500);

        debugMessage("Depositing inventory into bank");
        Rs2Bank.depositAll();

        // Ensure the bank contains at least 1 of each required item
        verifyItemInBank("Battlestaff", battleStaff);
        verifyItemInBank(itemToCraft.getOrbName(), itemToCraft.getOrbID());

        debugMessage("Withdrawing staffs and orbs");
        Rs2Bank.withdrawX(itemToCraft.getOrbID(), 14);
        sleepUntil(() -> Rs2Inventory.hasItem(itemToCraft.getOrbID()), 500);
        Rs2Bank.withdrawX(battleStaff, 14);
        sleepUntil(() -> Rs2Inventory.hasItem(battleStaff), 500);

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

        // If already crafting finish and exit early
        if (Rs2Player.getLocalPlayer().getAnimation() == AnimationID.CRAFTING_BATTLESTAVES) {
            debugMessage("Already crafting");
            sleepUntil(() -> Rs2Inventory.hasItemAmount(itemToCraft.getOrbID(), 0)
                    || Rs2Inventory.hasItemAmount(battleStaff, 0), 15000);
            return;
        }

        debugMessage("Starting crafting staffs");

        // Combine with orb first as battlestaffs have a skill requirement
        // This is pointless because it will auto use the "use" menu item
        Rs2Inventory.combine(itemToCraft.getOrbID(), battleStaff);

        debugMessage("Waiting for crafting interface");
        sleepUntil(() -> Rs2Widget.isWidgetVisible(17694734), 1500);

        // Only on the first time crafting the make "All" widget should be pressed
        if (!firstStaff) {
            debugMessage("First craft - selecting make all");
            firstStaff = true;
            if (!Rs2Widget.clickWidget(17694732))
                Rs2Widget.clickWidgetFast(Rs2Widget.getWidget(17694732, 17694732));
        }

        // If clicking the widget fails fallback to pressing space (1 would work too)
        sleepUntil(() -> Rs2Widget.isWidgetVisible(17694733, 17694734), 1500);
        if (!Rs2Widget.clickWidget(17694733, 17694734))
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);

        debugMessage("Waiting to finish crafting staffs");

        // Crafting any type of battle staff is a 2 tick action
        sleepUntilTick(Math.min(staffsWithdrawn, orbsWithdrawn) * 2);
        // Ensure the crafting is complete before moving on
        sleepUntil(() -> Rs2Inventory.hasItemAmount(itemToCraft.getItemID(),
                Math.min(staffsWithdrawn, orbsWithdrawn)) && !Rs2Player.isAnimating(), 1500);
    }

    public ProgressiveStaffmakingModel calculateItemToCraft() {
        int craftinglvl = Microbot.getClient().getRealSkillLevel(Skill.CRAFTING);
        if (craftinglvl < Staffs.EARTH_BATTLESTAFF.getLevelRequired()) {
            model.setItemToCraft(Staffs.WATER_BATTLESTAFF);
        } else if (craftinglvl < Staffs.FIRE_BATTLESTAFF.getLevelRequired()) {
            model.setItemToCraft(Staffs.EARTH_BATTLESTAFF);
        } else if (craftinglvl < Staffs.AIR_BATTLESTAFF.getLevelRequired()) {
            model.setItemToCraft(Staffs.FIRE_BATTLESTAFF);
        } else if (craftinglvl < 99) {
            model.setItemToCraft(Staffs.AIR_BATTLESTAFF);
        }
        return model;
    }

    @Override
    public void shutdown() {
        // Reset values
        staffsWithdrawn = 0;
        orbsWithdrawn = 0;
        debugMessages = false;
        firstStaff = false;
        super.shutdown();
    }

    // Display debug messages in the ingame chatbox
    private void debugMessage(String str) {
        if (debugMessages)
            Microbot.log(String.format("[Crafter] %s", str));
    }
}
