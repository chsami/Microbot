package net.runelite.client.plugins.microbot.magic.housetab;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.magic.housetab.enums.HOUSETABS_CONFIG;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public class HouseTabScript extends Script {
    public static String version = "1.0";
    private final int RIMMINGTON_PORTAL_OBJECT = 15478;
    private final int HOUSE_PORTAL_OBJECT = 4525;

    private final int HOUSE_ADVERTISEMENT_OBJECT = 29091;

    private final int HOUSE_ADVERTISEMENT_NAME_PARENT_INTERFACE = 3407881;

    private final int HOUSE_TABLET_INTERFACE = 26411033;

    private final HOUSETABS_CONFIG houseTabConfig;
    private final String[] playerHouses;

    private final ScheduledExecutorService scheduledExecutorService;


    private boolean hasSoftClay() {
        return Rs2Inventory.hasItem(1761);
    }

    private boolean hasSoftClayNoted() {
        return Rs2Inventory.hasItem(1762);
    }

    private boolean hasLawRune() {
        return Rs2Inventory.hasItem(ItemID.LAWRUNE);
    }

    public HouseTabScript(HOUSETABS_CONFIG houseTabConfig, String[] playerHouses) {
        this.houseTabConfig = houseTabConfig;
        this.playerHouses = playerHouses;
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
    }

    private void lookForHouseAdvertisementObject() {
        Widget houseAdvertisementPanel = Microbot.getClient().getWidget(HOUSE_ADVERTISEMENT_NAME_PARENT_INTERFACE);
        if (!hasSoftClay() || houseAdvertisementPanel != null || Rs2GameObject.findObjectById(HOUSE_PORTAL_OBJECT) != null)
            return;


        boolean success = Rs2GameObject
                .interact(HOUSE_ADVERTISEMENT_OBJECT, "View");


        if (success) {
            sleepUntilOnClientThread(() -> Microbot.getClient().getWidget(HOUSE_ADVERTISEMENT_NAME_PARENT_INTERFACE) != null);
        }
    }

    private void lookForPlayerHouse() {
        Widget houseAdvertisementNameWidget = Microbot.getClient().getWidget(HOUSE_ADVERTISEMENT_NAME_PARENT_INTERFACE);
        if (houseAdvertisementNameWidget == null || houseAdvertisementNameWidget.getChildren() == null) return;
        if (!hasSoftClay())
            return;
        if (Rs2GameObject.findObjectById(HOUSE_PORTAL_OBJECT) != null)
            return;

        int enterHouseButtonHeight = 21;
        int houseIndexToJoin = 0;

        for (int i = 0; i < houseAdvertisementNameWidget.getChildren().length; i++) {
            Widget child = houseAdvertisementNameWidget.getChild(i);
            if (child == null) continue;
            if (Arrays.stream(this.playerHouses).anyMatch(x -> child.getText().equalsIgnoreCase(x))) {
                houseIndexToJoin = i;
                break;
            }
        }

        Widget mainWindow = Microbot.getClient().getWidget(3407879);
        if (mainWindow == null) return;
        int HOUSE_ADVERTISEMENT_ENTER_HOUSE_PARENT_INTERFACE = 3407891;
        Widget houseAdvertisementEnterHouseWidget = Microbot.getClient().getWidget(HOUSE_ADVERTISEMENT_ENTER_HOUSE_PARENT_INTERFACE);
        if (houseAdvertisementEnterHouseWidget == null) return;
        Widget enterHouseButton = houseAdvertisementEnterHouseWidget.getChild(houseIndexToJoin);
        int buttonRelativeY = houseAdvertisementEnterHouseWidget.getChild(houseIndexToJoin).getRelativeY() + enterHouseButtonHeight;
        if (buttonRelativeY > (mainWindow.getScrollY() + mainWindow.getHeight())) {
            keepExecuteUntil(() -> {
                int x = (int) mainWindow.getBounds().getCenterX() + Random.random(-50, 50);
                int y = (int) mainWindow.getBounds().getCenterY() + Random.random(-50, 50);
                Microbot.getMouse().scrollDown(new Point(x, y));
            }, () -> buttonRelativeY <= (mainWindow.getScrollY() + mainWindow.getHeight()), 500);
        } else {
            Microbot.getMouse()
                    .click(enterHouseButton.getCanvasLocation());
            sleepUntilOnClientThread(() -> Rs2GameObject.findObjectById(HOUSE_PORTAL_OBJECT) != null);
            sleep(2000, 3000);
        }
    }

    public void lookForLectern() {
        if (!hasSoftClay() || Rs2GameObject.findObjectById(HOUSE_ADVERTISEMENT_OBJECT) != null || Microbot.isGainingExp)
            return;

        Widget houseTabInterface = Microbot.getClient().getWidget(HOUSE_TABLET_INTERFACE);
        if (houseTabInterface != null || Rs2GameObject.findObjectById(HOUSE_PORTAL_OBJECT) == null) return;

        boolean success = Rs2GameObject.interact(new int[]{13647, 37349}, "Study");
        if (success) {
            sleepUntilOnClientThread(() -> Microbot.getClient().getWidget(HOUSE_TABLET_INTERFACE) != null);
        }
    }

    public void createHouseTablet() {
        Widget houseTabInterface = Microbot.getClient().getWidget(HOUSE_TABLET_INTERFACE);
        if (houseTabInterface == null) return;
        if (!hasSoftClay() || Rs2GameObject.findObjectById(HOUSE_PORTAL_OBJECT) == null)
            return;

        while (Microbot.getClient().getWidget(HOUSE_TABLET_INTERFACE) != null) {
            if (mainScheduledFuture.isCancelled()) break;
            Microbot.getMouse()
                    .click(houseTabInterface.getCanvasLocation());
            sleep(1000, 2000);
            Rs2Widget.clickWidget(26411022);
            sleep(1000, 2000);
        }

        sleepUntilOnClientThread(() -> !hasSoftClay()
                || Microbot.getClient().getWidget(HOUSE_TABLET_INTERFACE) != null
                || !Microbot.isGainingExp, 55000);
    }

    public void leaveHouse() {
        if (hasSoftClay() || Rs2GameObject.findObjectById(HOUSE_PORTAL_OBJECT) == null)
            return;

        boolean success = Rs2GameObject.interact(HOUSE_PORTAL_OBJECT, "Enter");
        if (success)
            sleepUntil(() -> Rs2GameObject.findObjectById(HOUSE_PORTAL_OBJECT) == null);
    }

    public void unnoteClay() {
        if (hasSoftClay() || Rs2GameObject.findObjectById(HOUSE_ADVERTISEMENT_OBJECT) == null)
            return;
        if (Microbot.getClient().getWidget(14352385) == null) {
            do {
                Microbot.getClientThread().invoke(() -> {
                    Rs2Inventory.use("Soft clay");
                });
                sleep(300, 380);
            } while (!Rs2Npc.interact("Phials", "Use"));
        }

        sleep(2500, 5000);
        if (Microbot.getClient().getWidget(14352385) != null) {
            Rs2Keyboard.keyPress('3');
            sleep(300, 380);
        }
    }

    public boolean run(HouseTabConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!hasSoftClayNoted() || !hasLawRune()) {
                    shutdown();
                    return;
                }
                if (Microbot.isGainingExp) return;

                Rs2Player.toggleRunEnergy(true);
                if (Microbot.getClient().getEnergy() < 3000 && !Rs2Widget.hasWidget("Teleport to House") && Rs2GameObject.findObject(new Integer[] {ObjectID.XMAS20_POH_POOL_REGENERATION, ObjectID.POH_POOL_REJUVENATION}) != null) {
                    Rs2GameObject.interact(new int[] {ObjectID.XMAS20_POH_POOL_REGENERATION, ObjectID.POH_POOL_REJUVENATION}, "drink");
                    return;
                }

                boolean isInHouse = Rs2GameObject.getGameObject(new Integer[] {ObjectID.POH_LECTERN_8}) != null;
                if (isInHouse) {
                    lookForLectern();
                    createHouseTablet();
                    leaveHouse();
                } else {
                    unnoteClay();
                    if (config.ownHouse()) {
                        if (Rs2GameObject.interact(ObjectID.POH_RIMMINGTON_PORTAL, "Home")) {
                            sleep(800, 1200);
                        }
                        return;
                    }
                    if (Rs2GameObject.interact(ObjectID.POH_RIMMINGTON_PORTAL, "Friend's house")) {
                        sleepUntil(() -> Rs2Widget.hasWidget("Enter name"));
                        if (Rs2Widget.hasWidget(config.housePlayerName())) {
                            Rs2Widget.clickWidget(config.housePlayerName());
                            sleep(800, 1200);
                        } else {
                            if (Rs2Widget.hasWidget("Enter name")) {
                                Rs2Keyboard.typeString(config.housePlayerName());
                                Rs2Keyboard.enter();
                                sleep(1000, 1800);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public ScheduledFuture<?> keepExecuteUntil(Runnable callback, BooleanSupplier awaitedCondition, int time) {
        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (awaitedCondition.getAsBoolean()) {
                scheduledFuture.cancel(true);
                scheduledFuture = null;
                return;
            }
            callback.run();
        }, 0, time, TimeUnit.MILLISECONDS);
        return scheduledFuture;
    }
}