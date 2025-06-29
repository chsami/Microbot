package net.runelite.client.plugins.microbot.cooking.scripts;

import net.runelite.api.AnimationID;
import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.cooking.AutoCookingConfig;
import net.runelite.client.plugins.microbot.cooking.enums.CookingAreaType;
import net.runelite.client.plugins.microbot.cooking.enums.CookingItem;
import net.runelite.client.plugins.microbot.cooking.enums.CookingLocation;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

enum CookingState {
    COMBINE,
    COOKING,
    WALKING,
    BANKING,
    DROPPING,
}

public class AutoCookingScript extends Script {

    private CookingState state;
    private boolean init;
    private CookingLocation location;

    public boolean run(AutoCookingConfig config) {
        Microbot.enableAutoRunOn = false;
        CookingItem cookingItem = config.cookingItem();
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCookingSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_COOKING);
        init = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                if (init) {
                    if (initialPlayerLocation == null) {
                        initialPlayerLocation = Rs2Player.getWorldLocation();
                    }

                    if (config.useNearestCookingLocation()) {
                        location = CookingLocation.findNearestCookingLocation(cookingItem);
                    } else {
                        location = config.cookingLocation();
                        if (cookingItem.getCookingAreaType() != CookingAreaType.BOTH) {
                            if (location.getCookingAreaType() != cookingItem.getCookingAreaType()) {
                                Microbot.showMessage("Cooking Area does not match item's cooking area");
                                shutdown();
                                return;
                            }
                        }
                    }

                    getState(config, location);
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Microbot.pauseAllScripts) return;

                switch (state) {
                    case COOKING:
                        if (!cookingItem.hasRequirements()) {
                            Microbot.showMessage("You do not meet the requirements to cook this item");
                            shutdown();
                            return;
                        }

                        TileObject cookingObject = Rs2GameObject.findObjectById(location.getCookingObjectID());

                        if (cookingObject != null) {
                            if (!Rs2Camera.isTileOnScreen(cookingObject.getLocalLocation())) {
                                Rs2Camera.turnTo(cookingObject.getLocalLocation());
                                return;
                            }
                            Rs2Inventory.useItemOnObject(cookingItem.getRawItemID(), cookingObject.getId());
                            sleepUntil(() -> !Rs2Player.isMoving() && Rs2Widget.findWidget("like to cook?", null, false) != null);

                            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                            Microbot.status = "Cooking " + cookingItem.getRawItemName();
                            
                            Rs2Antiban.actionCooldown();
                            Rs2Antiban.takeMicroBreakByChance();

                            sleepUntil(() -> (Rs2Player.getAnimation() != AnimationID.IDLE));
                            sleepUntilTrue(() -> (!hasRawItem(cookingItem) && !Rs2Player.isAnimating(3500))
                                    || Rs2Dialogue.isInDialogue() || Rs2Player.isMoving(), 500, 150000);
                            
                            if (hasRawItem(cookingItem)) {
                                break;
                            }
                            
                            if (config.shouldDropBurntItems() && hasBurntItem(cookingItem) && !cookingItem.getBurntItemName().isEmpty()) {
                                state = CookingState.DROPPING;
                                return;
                            }

                            state = CookingState.BANKING;
                            break;
                        }
                    case DROPPING:
                        Microbot.status = "Dropping " + cookingItem.getBurntItemName();
                        Rs2Inventory.dropAll(item -> item.getName().equalsIgnoreCase(cookingItem.getBurntItemName()), config.getDropOrder());
                        sleepUntilTrue(() -> !hasBurntItem(cookingItem), 500, 150000);
                        state = CookingState.BANKING;
                        break;
                    case BANKING:
                        if (location == CookingLocation.ROUGES_DEN) {
                            NPC npc = Rs2Npc.getBankerNPC();
                            boolean isNPCBankOpen = Rs2Bank.openBank(npc);
                            if (!isNPCBankOpen) return;
                        } else {
                            boolean isBankOpen = Rs2Bank.walkToBankAndUseBank();
                            if (!isBankOpen || !Rs2Bank.isOpen()) return;
                        }

                        Rs2Bank.depositAll();
                        Rs2Inventory.waitForInventoryChanges(1800);
                        
                        if (!hasRawItem(cookingItem)) {
                            Microbot.showMessage("No Raw Food Item found in Bank");
                            shutdown();
                            return;
                        }
                        if (Objects.equals(config.cookingItem().getRawItemName(), "giant seaweed")) {
                            Rs2Bank.withdrawX(cookingItem.getRawItemName(), 4, true);
                            Rs2Inventory.waitForInventoryChanges(1800);

                        } else {
                            Rs2Bank.withdrawAll(cookingItem.getRawItemName(), true);
                            Rs2Inventory.waitForInventoryChanges(1800);
                        }
                        
                        state = CookingState.WALKING;
                        Rs2Bank.closeBank();
                        break;
                    case WALKING:
                        if (!isNearCookingLocation(location, 10)) {
                            boolean walkTo = Rs2Walker.walkTo(location.getCookingObjectWorldPoint(), 2);
                            if (!walkTo) return;
                        } else if (!isNearCookingLocation(location, 2)) {
                            Rs2Walker.walkFastCanvas(location.getCookingObjectWorldPoint());
                        }

                        if (hasRawItem(cookingItem)) {
                            state = CookingState.COOKING;
                        } else {
                            state = CookingState.BANKING;
                        }
                        break;
                }
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }
    
    @Override
    public void shutdown(){
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    private void getState(AutoCookingConfig config, CookingLocation location) {
        if (!hasRawItem(config.cookingItem())) {
            if (hasBurntItem(config.cookingItem())) {
                state = CookingState.DROPPING;
                init = false;
                return;
            }
            state = CookingState.BANKING;
            init = false;
            return;
        }

        if (!isNearCookingLocation(location, 4)) {
            state = CookingState.WALKING;
            init = false;
            return;
        }

        state = CookingState.COOKING;
        init = false;
    }

    private boolean isNearCookingLocation(CookingLocation location, int distance) {
        return Rs2Player.getWorldLocation().distanceTo(location.getCookingObjectWorldPoint()) <= distance && !Rs2Player.isMoving();
    }

    private boolean hasRawItem(CookingItem cookingItem) {
        if (Rs2Bank.isOpen()) {
            return Rs2Bank.hasBankItem(cookingItem.getRawItemName(), true);
        }
        return Rs2Inventory.hasItem(cookingItem.getRawItemName(), true);
    }

    private boolean hasCookedItem(CookingItem cookingItem) {
        return Rs2Inventory.hasItem(cookingItem.getCookedItemName(), true);
    }

    private boolean hasBurntItem(CookingItem cookingItem) {
        return Rs2Inventory.hasItem(cookingItem.getBurntItemName(), true);
    }
}
