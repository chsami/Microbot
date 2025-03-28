package net.runelite.client.plugins.microbot.crafting.scripts;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.crafting.CraftingConfig;
import net.runelite.client.plugins.microbot.crafting.enums.BoltTips;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes.Name;

public class GemsScript extends Script {
    private boolean debugMessages = false;
    private boolean firstCut = true;
    private boolean init = true;

    public boolean run(CraftingConfig config) {
        if (!Rs2Player.getSkillRequirement(Skill.CRAFTING, config.gemType().getLevelRequired())) {
            Microbot.showMessage("Crafting level too low to craft " + config.gemType().getName());
            shutdown();
            return false;
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn())
                    return;
                if (!super.run())
                    return;
                //
                // Enable ingame chatbox debug messages according to config
                debugMessages = config.chatMessages();

                // If AFK config variable is set randomly sleep for a period between
                // 15 and 90 seconds before moving on if the trigger occurs
                if (config.Afk() && (!Rs2Player.isMoving() || !Rs2Player.isInteracting())
                        && Rs2Random.nzRandom() < 0.05) {
                    debugMessage(String.format("Going AFK for between 15 and 90 seconds"));
                    int delay = Rs2Random.between(15000, 90000);
                    Microbot.status = String.format("Taking short AFK break %02s", delay / 1000);
                    Rs2Antiban.moveMouseOffScreen();
                    sleep(delay);
                }

                if (init) {
                    init = false;
                    debugMessage("Initialising setup");
                    Microbot.status = "INITIALISE";

                    if (Rs2Bank.getNearestBank(Rs2Player.getWorldLocation()) == (BankLocation) null) {
                        // Find, walk and turn to the closest bank
                        debugMessage("Walking to closest bank");
                        TileObject bank = Rs2GameObject.findReachableObject("Bank", true, 500,
                                Rs2Player.getWorldLocation());
                        Rs2Walker.walkWithState(bank.getWorldLocation());
                        Rs2Player.waitForWalking(18000);
                        Rs2Camera.turnTo(Rs2GameObject.findBank());
                    }

                    debugMessage("Opening bank interface");
                    sleepUntil(() -> Rs2Bank.openBank(), 500);

                    debugMessage("Depositing inventory into bank");
                    Rs2Bank.depositAllExcept(ItemID.CHISEL, ItemID.CHISEL_5601, ItemID.CHISEL_28414);

                    // Ensure the chisel is in the inventory or fail without one
                    debugMessage("Withdrawing chisel");
                    if (!Rs2Inventory.hasItem("Chisel")) {
                        Rs2Bank.withdrawOne("Chisel");
                    }

                    if (Rs2Inventory.count("Chisel") == 0) {
                        Microbot.showMessage("Chisel missing and is essntial to all aspects of this script");
                        this.shutdown();
                    }

                    debugMessage("Exiting bank interface");
                    Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                    if (Rs2Bank.isOpen())
                        Rs2Bank.closeBank();
                }

                final String uncutGemName = "uncut " + config.gemType().getName();
                if (!Rs2Inventory.hasItem(uncutGemName)) {
                    Microbot.status = "BANKING";
                    Rs2Bank.depositAllExcept(ItemID.CHISEL, ItemID.CHISEL_5601, ItemID.CHISEL_28414);

                    debugMessage("Withdrawing uncut gems");
                    if (Rs2Bank.hasItem(uncutGemName)) {
                        Rs2Bank.withdrawAll(true, uncutGemName);
                    } else {
                        Microbot.showMessage("You've ran out of materials!");
                        shutdown();
                    }
                    debugMessage("Exiting bank interface");
                    Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                    if (Rs2Bank.isOpen())
                        Rs2Bank.closeBank();

                    debugMessage("Beginning to cut gems");
                    Microbot.status = "CUTTING GEMS";
                    int gemCount = Rs2Inventory.count(uncutGemName);
                    Rs2Inventory.combineClosest("Chisel", uncutGemName);
                    Rs2Dialogue.sleepUntilHasCombinationDialogue();

                    // Only on the first time crafting the make "All" widget should be pressed
                    if (!firstCut) {
                        firstCut = true;
                        debugMessage("First cut - selecting make all");
                        if (!Rs2Widget.clickWidget(17694732))
                            Rs2Widget.clickWidgetFast(Rs2Widget.getWidget(17694732, 17694732));

                        if (!Rs2Widget.clickChildWidget(17694733, 17694733))
                            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);

                        // Cutting any type of gem is a 2 tick action
                        sleepUntilTick(gemCount * 2);
                        sleepUntil(() -> !Rs2Inventory.hasItem(uncutGemName));

                        Rs2Antiban.actionCooldown();
                        Rs2Antiban.takeMicroBreakByChance();
                    }

                    if (config.fletchIntoBoltTips()) {
                        Microbot.status = "FLETCHING BOLT TIPS";
                        debugMessage("Fletching gems into bolt tips");

                        BoltTips boltTip = BoltTips.valueOf(config.gemType().name());
                        if (Rs2Player.getSkillRequirement(Skill.FLETCHING, boltTip.getFletchingLevelRequired()) &&
                                Rs2Inventory.hasItem(config.gemType().getName()) &&
                                Rs2Inventory.hasItem("chisel")) {
                            int cutGemCount = Rs2Inventory.count(config.gemType().getName());

                            Rs2Inventory.combine("Chisel", config.gemType().getName());
                            Rs2Inventory.use(config.gemType().getName());
                            Rs2Dialogue.sleepUntilHasCombinationDialogue();
                            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                            sleepUntilTick(cutGemCount * 2);
                            sleepUntil(() -> !Rs2Inventory.hasItem(config.gemType().getName()));

                            debugMessage("Make bolt tips of all cut gems");
                            Rs2Antiban.actionCooldown();
                            Rs2Antiban.takeMicroBreakByChance();
                        }
                    }
                }
                Microbot.status = "IDLE";
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        Microbot.pauseAllScripts = true;
        debugMessage("Shutting down gem cutting script");
        init = true;
        firstCut = true;
        debugMessages = false;
        Microbot.status = "IDLE";
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone())
            mainScheduledFuture.cancel(true);
        super.shutdown();
    }

    // Display debug messages in the ingame chatbox
    private void debugMessage(String str) {
        if (debugMessages)
            Microbot.log(String.format("[Gem Cutter] %s", str));
    }
}
