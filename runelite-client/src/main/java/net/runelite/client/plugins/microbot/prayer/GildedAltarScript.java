package net.runelite.client.plugins.microbot.prayer;

import net.runelite.api.ObjectID;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class GildedAltarScript extends Script {

    private final int HOUSE_PORTAL_OBJECT = 4525;
    private Widget toggleArrow;
    public Widget targetWidget;
    public String houseOwner;

    public WorldPoint portalCoords;
    public WorldPoint altarCoords;
    public Boolean usePortal;
    public boolean visitedOnce;
    List<String> blacklistNames = new ArrayList<>();


    public static GildedAltarPlayerState state = GildedAltarPlayerState.IDLE;

    private boolean inHouse() {
        return Rs2Npc.getNpc("Phials") == null;
    }

    private boolean hasUnNotedBones() {
        return Rs2Inventory.hasUnNotedItem("bones");
    }

    private boolean hasNotedBones() {
        return Rs2Inventory.hasNotedItem("bones");
    }

    private void calculateState() {
        boolean inHouse = inHouse();
        boolean hasUnNotedBones = hasUnNotedBones();

        // If we have unNoted bones:
        // If we're in the house, use bones on altar. Else, enter the portal
        // If we don't have unNoted bones:
        // If we're in the house, leave house. Else, talk to Phials
        if (hasUnNotedBones) {
            state = inHouse ? GildedAltarPlayerState.BONES_ON_ALTAR : GildedAltarPlayerState.ENTER_HOUSE;
        } else {
            state = inHouse ? GildedAltarPlayerState.LEAVE_HOUSE : GildedAltarPlayerState.UNNOTE_BONES;
        }
    }


    public boolean run(GildedAltarConfig config) {
        blacklistNames = new ArrayList<>();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) {
                    return;
                }
                if (!Rs2Inventory.hasItem(995)) {
                    Microbot.showMessage("No gp found in your inventory");
                    shutdown();
                    return;
                }
                if (!hasNotedBones() && !hasUnNotedBones()) {
                    Microbot.showMessage("No bones found in your inventory");
                    shutdown();
                    return;
                }

                if (Microbot.isGainingExp) return;

                calculateState();

                switch (state) {
                    case LEAVE_HOUSE:
                        leaveHouse();
                        break;
                    case UNNOTE_BONES:
                        unnoteBones();
                        break;
                    case ENTER_HOUSE:
                        enterHouse();
                        break;
                    case BONES_ON_ALTAR:
                        bonesOnAltar();
                        break;
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    public void leaveHouse() {
        System.out.println("Attempting to leave house...");

        // We should only rely on using the settings menu if the portal is several rooms away from the portal. Bringing up 3 different interfaces when we can see the portal on screen is unnecessary.
        if(usePortal) {
            TileObject portalObject = Rs2GameObject.findObjectById(HOUSE_PORTAL_OBJECT);
            if (portalObject == null) {
                System.out.println("Not in house, HOUSE_PORTAL_OBJECT not found.");
                return;
            }
            Rs2GameObject.interact(portalObject);
            Rs2Player.waitForWalking();
            return;
        }

        // Switch to Settings tab
        Rs2Tab.switchToSettingsTab();
        sleep(1200);


        //If the house options button is not visible, player is on Display or Sound settings, need to click Controls.
        String[] actions = Rs2Widget.getWidget(7602235).getActions(); // 116.59
        boolean isControlsInterfaceVisible = actions != null && actions.length == 0;
        if (!isControlsInterfaceVisible) {
            Rs2Widget.clickWidget(7602235);
            sleepUntil(() -> Rs2Widget.isWidgetVisible(7602207));
        }

        // Click House Options
        if (Rs2Widget.clickWidget(7602207)) {
            sleep(1200);
        } else {
            System.out.println("House Options button not found.");
            return;
        }

        // Click Leave House
        if (Rs2Widget.clickWidget(24248341)) {
            sleep(3000);
        } else {
            System.out.println("Leave House button not found.");
        }
    }

    public void unnoteBones() {
        if (Microbot.getClient().getWidget(14352385) == null) {
            if (!Rs2Inventory.isItemSelected()) {
                Rs2Inventory.use("bones");
            } else {
                Rs2Npc.interact("Phials", "Use");
                Rs2Player.waitForWalking();
            }
        } else if (Microbot.getClient().getWidget(14352385) != null) {
            Rs2Keyboard.keyPress('3');
            Rs2Inventory.waitForInventoryChanges(2000);
        }
    }

    private void enterHouse() {
        // If we've already visited a house this session, use 'Visit-Last' on advertisement board
        if (visitedOnce) {
            Rs2GameObject.interact(ObjectID.HOUSE_ADVERTISEMENT, "Visit-Last");
            sleep(2400, 3000);
            return;
        }

        boolean isAdvertisementWidgetOpen = Rs2Widget.isWidgetVisible(3407875);

        if (!isAdvertisementWidgetOpen) {
            Rs2GameObject.interact(ObjectID.HOUSE_ADVERTISEMENT, "View");
            sleep(1200, 1800);
        }

        Widget containerNames = Rs2Widget.getWidget(52, 9);
        Widget containerEnter = Rs2Widget.getWidget(52, 19);
        if (containerNames == null || containerNames.getChildren() == null) return;

        //Sort house advertisements by Gilded Altar availability
        toggleArrow = Rs2Widget.getWidget(3407877);
        if (toggleArrow.getSpriteId() == 1050) {
            Rs2Widget.clickWidget(3407877);
            sleep(600, 1200);
        }

        // Get all names on house board and find the one with the smallest Y value
        if (containerNames.getChildren() != null) {
            int smallestOriginalY = Integer.MAX_VALUE; // Track the smallest OriginalY

            Widget[] children = containerNames.getChildren();

            for (int i = 0; i < children.length; i++) {
                Widget child = children[i];
                if (child.getText() == null || child.getText().isEmpty()|| child.getText() == ""){
                    continue;
                }
                if (child.getText() != null) {
                    if (child.getOriginalY() < smallestOriginalY && !blacklistNames.contains(child.getText())) {
                        houseOwner = child.getText();
                        smallestOriginalY = child.getOriginalY();
                    }
                }
            }

            // Use playername at top of advertisement board as search criteria and find their Enter button
            Widget[] children2 = containerEnter.getChildren();
            for (int i = 0; i < children2.length; i++) {
                Widget child = children2[i];
                if (child == null || child.getOnOpListener() == null) {
                    continue;
                }
                Object[] listenerArray = child.getOnOpListener();
                boolean containsHouseOwner = Arrays.stream(listenerArray)
                        .filter(Objects::nonNull) // Ensure no null elements
                        .anyMatch(obj -> obj.toString().replace("\u00A0", " ").contains(houseOwner)); // Check if houseOwner is part of any listener object
                if (containsHouseOwner) {
                    targetWidget = child;
                    break;
                }
            }
            sleep(600, 1200);
            Rs2Widget.clickChildWidget(3407891, targetWidget.getIndex());
            visitedOnce = true;
            sleep(2400, 3000);
        }
    }

    public void bonesOnAltar() {
        if(portalCoords == null){
            portalCoords = Rs2Player.getWorldLocation();
        }

        if (Rs2Player.isAnimating())  {
            return;
        }

        TileObject altar;

        altar = Rs2GameObject.findObjectById(ObjectID.ALTAR_40878);
        if (altar == null) {
            altar = Rs2GameObject.findObjectById(ObjectID.ALTAR_13197);
        }

        Rs2Inventory.useUnNotedItemOnObject("bones", altar);
        Rs2Player.waitForAnimation();

        // Use bones on the altar if it's valid
        if(altarCoords == null){
            altarCoords = Rs2Player.getWorldLocation();
        }
        // If portal is more than 10 tiles from altar, use settings menu to leave. Else, just walk back to portal.
        if(usePortal == null){
            usePortal = altarCoords.distanceTo(portalCoords) <= 10;
        }
    }

    public void addNameToBlackList() {
        blacklistNames.add(houseOwner);
    }
}
