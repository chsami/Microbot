package net.runelite.client.plugins.microbot.crafting.scripts;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.crafting.CraftingConfig;
import net.runelite.client.plugins.microbot.crafting.enums.Amethyst;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
class ProgressiveAmethystCuttingModel {
    private Amethyst itemToCut;
}

public class AmethystScript extends Script {

    ProgressiveAmethystCuttingModel model = new ProgressiveAmethystCuttingModel();

    int chisel = ItemID.CHISEL;
    Amethyst itemToCut;

    private boolean debugMessages = false; // Controls chatbox debug messages
    private boolean firstCut = false; // Controls make "All" widget click

    public void run(CraftingConfig config) {
        if (Rs2Player.getRealSkillLevel(Skill.CRAFTING) < Amethyst.BOLT_TIPS.getRequiredLevel()) {
            debugMessage("Crafting level too low to cut amethyst");
            this.shutdown();
        }

        sleepUntil(() -> Microbot.isLoggedIn(), 1500);
        debugMessage("Starting amethyst item crafting script");

        if (config.amethystType() == Amethyst.PROGRESSIVE)
            calculateItemToCut();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run())
                return;

            // Enable ingame chatbox debug messages according to config
            debugMessages = config.chatMessages();

            // If AFK config variable is set randomly sleep for a period between
            // 15 and 90 seconds before moving on if the trigger occurs
            if (config.Afk() && Rs2Random.nzRandom() < 0.05) {
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
                if (config.amethystType() == Amethyst.PROGRESSIVE)
                    itemToCut = model.getItemToCut();
                else
                    itemToCut = config.amethystType();

                if (Rs2Inventory.hasItem(chisel) &&
                        Rs2Inventory.hasItem(ItemID.AMETHYST))
                    craft(config);

                bank(config);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void bank(CraftingConfig config) {
        // Find, walk and turn to the closest bank
        WorldArea bankArea = Rs2Bank.getNearestBank().getWorldPoint().toWorldArea();
        if (!Rs2Walker.canReach(bankArea.toWorldPoint())) {
            debugMessage("Walking to closest bank");
            Microbot.status = "Walking to bank";
            Rs2Walker.walkTo(bankArea.toWorldPoint());
        }
        Rs2Bank.preHover();
        LocalPoint bankTile = Rs2GameObject.findBank().getLocalLocation();
        Rs2Camera.turnTo(bankTile);
        Rs2Bank.preHover();

        debugMessage("Opening bank interface");
        sleepUntil(() -> Rs2Bank.openBank(), 500);
        Microbot.status = "Banking";

        debugMessage("Depositing inventory into bank");
        Rs2Bank.depositAllExcept("Chisel");

        // Ensure the bank contains at least 1 of each required item
        verifyItemInBank("Chisel", chisel);
        verifyItemInBank("Amethyst", ItemID.AMETHYST);

        debugMessage("Withdrawing chisel and amethyst");
        if (!Rs2Inventory.hasItem(chisel)) {
            Rs2Bank.withdrawX(chisel, 1);
        }
        Rs2Bank.withdrawAll(ItemID.AMETHYST);

        debugMessage("Exiting bank interface");
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        if (Rs2Bank.isOpen())
            Rs2Bank.closeBank();
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

        debugMessage("Starting cutting amethyst");

        if (!Rs2Tab.switchToInventoryTab())
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);

        if (!Rs2Inventory.hasItem(chisel) || !Rs2Inventory.hasItem(ItemID.AMETHYST))
            return;

        debugMessage("Waiting for crafting interface");
        Rs2Inventory.combineClosest(chisel, ItemID.AMETHYST);
        Rs2Dialogue.sleepUntilHasCombinationDialogue();

        // Only on the first time crafting the make "All" widget should be pressed
        if (!firstCut) {
            firstCut = true;
            debugMessage("First cut - selecting make all");
            if (!Rs2Widget.clickWidget(17694732))
                Rs2Widget.clickWidgetFast(Rs2Widget.getWidget(17694732, 17694732));
        }

        // Keypress to trigger the make action to craft all amethysts iiems
        if (!Rs2Widget.clickWidget(17694733, 17694733 + itemToCut.getCraftOptionKey())) {
            Rs2Widget.clickWidget("Amethyst " + itemToCut.getItemName());
            Rs2Keyboard.keyPress(itemToCut.getCraftOptionKey());
        }

        int amCount = Rs2Inventory.count(ItemID.AMETHYST);
        sleepUntilTick(amCount * 2);
        sleepUntilTrue(() -> !Rs2Inventory.hasItem(ItemID.AMETHYST));

        Microbot.status = "Crafting " + itemToCut.getItemName();

        debugMessage("Waiting to finish cutting amethyst");

        // Cutting any type of amethyst item is a 2 tick action
        sleepUntilTick(Rs2Inventory.count(ItemID.AMETHYST) * 2);
        Rs2Antiban.actionCooldown();
    }

    public ProgressiveAmethystCuttingModel calculateItemToCut() {
        int craftinglvl = Microbot.getClient().getRealSkillLevel(Skill.CRAFTING);
        if (craftinglvl < Amethyst.ARROWTIPS.getRequiredLevel()) {
            debugMessage("Crafting Amethyst Bolt Tips");
            model.setItemToCut(Amethyst.BOLT_TIPS);
        } else if (craftinglvl < Amethyst.JAVELIN_HEADS.getRequiredLevel()) {
            debugMessage("Crafting Amethyst Arrowtips");
            model.setItemToCut(Amethyst.ARROWTIPS);
        } else if (craftinglvl < Amethyst.DART_TIP.getRequiredLevel()) {
            debugMessage("Crafting Amethyst Javelin Heads");
            model.setItemToCut(Amethyst.JAVELIN_HEADS);
        } else {
            debugMessage("Crafting Amethyst Dart Tips");
            model.setItemToCut(Amethyst.DART_TIP);
        }
        return model;
    }

    @Override
    public void shutdown() {
        debugMessage("Shutting down amethyst item cutting script");
        // Reset values
        debugMessages = false;
        firstCut = false;
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
