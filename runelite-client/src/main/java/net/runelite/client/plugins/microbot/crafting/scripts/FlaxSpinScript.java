package net.runelite.client.plugins.microbot.crafting.scripts;

import net.runelite.api.GameObject;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.crafting.CraftingConfig;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
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
    public static double version = 1.0;

    State state;
    boolean init = true;

    public boolean run(CraftingConfig config) {
        Microbot.enableAutoRunOn = false;
        initialPlayerLocation = null;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                long startTime = System.currentTimeMillis();

                if (init) {
                    getState(config);
                }

                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                switch (state) {
                    case SPINNING:
                        if (!Rs2Inventory.hasItem(ItemID.FLAX)) {
                            state = State.BANKING;
                            return;
                        }
                        Rs2Inventory.useItemOnObject(ItemID.FLAX, config.flaxSpinLocation().getObjectID());
                        sleepUntil(() -> !Rs2Player.isMoving());
                        Rs2Widget.sleepUntilHasWidget("how many do you wish to make?");
                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        sleepUntilTrue(() -> !Rs2Inventory.hasItem(ItemID.FLAX), 600, 150000);
                        state = State.BANKING;
                        break;
                    case BANKING:
                        boolean isBankOpen = Rs2Bank.walkToBankAndUseBank();
                        if (!isBankOpen || !Rs2Bank.isOpen()) return;

                        Rs2Bank.depositAll(ItemID.BOW_STRING);
                        sleep(Random.random(600, 800));
                        Rs2Bank.withdrawAll(ItemID.FLAX);
                        sleep(Random.random(600, 800));
                        Rs2Bank.closeBank();
                        state = State.WALKING;
                        break;
                    case WALKING:
                        Rs2Walker.walkTo(config.flaxSpinLocation().getWorldPoint(), 4);
                        sleepUntilTrue(() -> isNearSpinningWheel(config, 4) && !Rs2Player.isMoving(), 600, 300000);
                        if (!isNearSpinningWheel(config, 4)) return;
                        Optional<GameObject> spinningWheel = Rs2GameObject.getGameObjects().stream()
                                .filter(obj -> obj.getId() == config.flaxSpinLocation().getObjectID())
                                .sorted(Comparator.comparingInt(obj -> Rs2Player.getWorldLocation().distanceTo(obj.getWorldLocation())))
                                .findFirst();
                        if (spinningWheel.isEmpty()) {
                            Rs2Walker.walkFastCanvas(config.flaxSpinLocation().getWorldPoint());
                            return;
                        }
                        state = State.SPINNING;
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private void getState(CraftingConfig config) {
        if (!Rs2Inventory.hasItem(ItemID.FLAX)) {
            state = State.BANKING;
            init = false;
            return;
        }
        if (!isNearSpinningWheel(config, 4)) {
            state = State.WALKING;
            init = false;
            return;
        }

        state = State.SPINNING;
        init = false;
    }

    private boolean isNearSpinningWheel(CraftingConfig config, int distance) {
        return Rs2Player.getWorldLocation().distanceTo(config.flaxSpinLocation().getWorldPoint()) <= distance;
    }
}
