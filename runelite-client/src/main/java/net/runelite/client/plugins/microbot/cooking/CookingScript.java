package net.runelite.client.plugins.microbot.cooking;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.cooking.enums.CookingEnum;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.VirtualKeyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.math.Random.random;

public class CookingScript extends Script {

    public static double version = 1.0;

    List<String> missingItemsInBank = new ArrayList<>(Arrays.asList());

    public boolean run(int gameObjectId) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;
            try {

                String itemToCook = getItemToCook();

                if (!Rs2Inventory.isFull() || !Rs2Inventory.hasItem(itemToCook)) {
                    if (!Rs2Bank.isOpen()) {
                        boolean bankIsOnScreen = Rs2Bank.useBank();
                        if (!bankIsOnScreen) {
                            Rs2Bank.walkToBank();
                        }
                    }
                    if (Rs2Bank.isOpen()) {
                        if (!Rs2Bank.hasItem(getItemToCook())) {
                            missingItemsInBank.add(getItemToCook());
                            return;
                        }
                        Rs2Bank.depositAll();
                        Rs2Bank.withdrawItemAll(true, itemToCook);
                    }
                }

                if (Rs2Inventory.hasItem(itemToCook)) {
                    Microbot.getWalker().walkMiniMap(new WorldPoint(3273 + random(-2, 2), 3180+ random(-2, 2), 0));

                    TileObject cookingRange = Rs2GameObject.findObjectById(gameObjectId);
                    if (cookingRange != null) {
                        if (!Camera.isTileOnScreen(cookingRange.getLocalLocation())) {
                            Camera.turnTo(cookingRange.getLocalLocation());
                            return;
                        }
                        if (Rs2Widget.getWidget(17694734) == null)
                        {
                            Rs2GameObject.interact(cookingRange);
                            sleepUntilOnClientThread(() -> Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(cookingRange.getWorldLocation()) < 2, 10000);
                            sleepUntilOnClientThread(() -> Rs2Widget.getWidget(17694734) != null);
                            sleep(600, 1600);
                        }
                        VirtualKeyboard.keyPress(KeyEvent.VK_SPACE);
                        sleep(5000);
                        while (true) {
                            long rawFoodCount = Rs2Inventory.getAmountForItem(itemToCook);
                            sleep(3000);
                            if (rawFoodCount == Rs2Inventory.getAmountForItem(itemToCook))
                                break;
                        }
                    }
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }


    private String getItemToCook() {
        for (CookingEnum cookingEnum: CookingEnum.values()) {
            if (Microbot.getClient().getRealSkillLevel(Skill.COOKING) >= cookingEnum.getLevelRequired()
                    && !missingItemsInBank.contains(cookingEnum.getRawFoodName())) {
                return cookingEnum.getRawFoodName();
            }
        }
        return "";
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
