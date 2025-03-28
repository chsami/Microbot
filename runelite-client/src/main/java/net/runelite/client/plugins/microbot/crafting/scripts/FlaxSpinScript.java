package net.runelite.client.plugins.microbot.crafting.scripts;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.crafting.CraftingConfig;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.net.StandardProtocolFamily;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

enum State {
    SPINNING,
    BANKING,
    WALKING
}

public class FlaxSpinScript extends Script {
    State state;
    boolean init = true;
    boolean debugMessages = false;

    public boolean run(CraftingConfig config) {
        Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_COLLECTING);
        Rs2Antiban.moveMouseRandomly();
        Microbot.enableAutoRunOn = false;
        initialPlayerLocation = null;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run())
                return;
            if (!Microbot.isLoggedIn())
                return;

            // Enable ingame chatbox debug messages according to config
            debugMessages = config.chatMessages();

            if (init) {
                getState(config);
            }

            if (initialPlayerLocation == null) {
                initialPlayerLocation = Rs2Player.getWorldLocation();
            }

            // If AFK config variable is set randomly sleep for a period between
            // 15 and 90 seconds before moving on if the trigger occurs
            if (config.Afk() && Rs2Random.nzRandom() < 0.05) {
                debugMessage(String.format("Going AFK for between 15 and 90 seconds"));
                Microbot.status = String.format("Taking short AFK break");
                Rs2Antiban.moveMouseOffScreen();
                Rs2Random.wait(15000, 90000);
            }

            try {
                switch (state) {
                    case SPINNING:
                        if (!Rs2Inventory.hasItem(ItemID.FLAX)) {
                            debugMessage("No Flax left going to bank");
                            state = State.BANKING;
                            return;
                        }
                        debugMessage("Spinning flax");
                        Rs2Inventory.useItemOnObject(ItemID.FLAX, config.flaxSpinLocation().getObjectID());
                        sleepUntil(() -> !Rs2Player.isMoving());
                        Rs2Widget.sleepUntilHasWidget("how many do you wish to make?");
                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        sleepUntilTrue(() -> !Rs2Inventory.hasItem(ItemID.FLAX), 600, 150000);
                        debugMessage("Out of flax");
                        Rs2Antiban.actionCooldown();
                        Rs2Antiban.takeMicroBreakByChance();
                        state = State.BANKING;
                        break;
                    case BANKING:
                        Rs2Camera.turnTo(Rs2GameObject.findBank());
                        boolean isBankOpen = Rs2Bank.walkToBankAndUseBank();
                        if (!isBankOpen || !Rs2Bank.isOpen())
                            return;

                        debugMessage("Banking bow strings");
                        Rs2Bank.depositAll(ItemID.BOW_STRING);
                        Rs2Inventory.waitForInventoryChanges(1800);
                        debugMessage("Withdrawing more flax");
                        Rs2Bank.withdrawAll(ItemID.FLAX);
                        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                        if (!Rs2Bank.isOpen())
                            Rs2Bank.closeBank();
                        Rs2Antiban.actionCooldown();
                        Rs2Antiban.takeMicroBreakByChance();
                        state = State.WALKING;
                        break;
                    case WALKING:
                        debugMessage("Walking to spinning wheel");
                        Rs2Walker.walkTo(config.flaxSpinLocation().getWorldPoint(), 4);
                        sleepUntilTrue(() -> isNearSpinningWheel(config, 4) && !Rs2Player.isMoving(), 600, 300000);
                        if (!isNearSpinningWheel(config, 4))
                            return;
                        Optional<GameObject> spinningWheel = Rs2GameObject.getGameObjects().stream()
                                .filter(obj -> obj.getId() == config.flaxSpinLocation().getObjectID())
                                .sorted(Comparator.comparingInt(
                                        obj -> Rs2Player.getWorldLocation().distanceTo(obj.getWorldLocation())))
                                .findFirst();
                        if (spinningWheel.isEmpty()) {
                            debugMessage("No spinning wheel found walking to flax location");
                            Rs2Walker.walkFastCanvas(config.flaxSpinLocation().getWorldPoint());
                            Rs2Antiban.takeMicroBreakByChance();
                            return;
                        }
                        state = State.SPINNING;
                        break;
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        debugMessage("Shutting down flax script");
        Microbot.pauseAllScripts = true;
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone())
            mainScheduledFuture.cancel(true);
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    private void getState(CraftingConfig config) {
        if (!Rs2Inventory.hasItem(ItemID.FLAX))
            state = State.BANKING;
        if (!isNearSpinningWheel(config, 4))
            state = State.WALKING;
        else
            state = State.SPINNING;
        init = false;
    }

    private boolean isNearSpinningWheel(CraftingConfig config, int distance) {
        return Rs2Player.getWorldLocation().distanceTo(config.flaxSpinLocation().getWorldPoint()) <= distance;
    }

    // Display debug messages in the ingame chatbox
    private void debugMessage(String str) {
        if (debugMessages)
            Microbot.log(String.format("[Flax Spinner] %s", str));
    }
}
