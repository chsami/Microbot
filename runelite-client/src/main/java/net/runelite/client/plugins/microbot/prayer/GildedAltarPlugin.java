package net.runelite.client.plugins.microbot.prayer;

import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
import javax.inject.Inject;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.ChatMessageType;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Gilded Altar",
        description = "Gilded Altar plugin",
        tags = {"prayer", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class GildedAltarPlugin extends Plugin {
    @Inject
    private GildedAltarConfig config;

    @Provides
    GildedAltarConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GildedAltarConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GildedAltarOverlay gildedAltarOverlay;
    @Inject
    private Client client;

    @Getter
    @Setter
    int skipTicks;
    private final int HOUSE_PORTAL_OBJECT = 4525;
    private Widget toggleArrow;
    boolean listen = false;
    Widget targetWidget;
    String houseOwner;
    private TileObject cachedAltar = null; // Cache for the altar object
    private WorldPoint portalCoords;
    private WorldPoint altarCoords;
    private Boolean usePortal;
    private boolean visitedOnce;


    GildedAltarPlayerState state = GildedAltarPlayerState.IDLE;

    private boolean inHouse() {
        return Rs2Npc.getNpc("Phials") == null;
    }

    private boolean hasUnNotedBones() {
        return Rs2Inventory.hasUnNotedItem("bones");
    }

    private boolean hasNotedBones() {
        return Rs2Inventory.hasNotedItem("bones");
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(gildedAltarOverlay);
        }
    }

    /**
     * @param name plugin name
     * @return the plguin
     */
    public static Plugin findPlugin(String name) {
        return Microbot.getPluginManager().getPlugins().stream().filter(x -> x.getName().equals(name)).findFirst().orElse(null);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(gildedAltarOverlay);
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        if (client.getGameState() != GameState.LOGGED_IN)
            return;

        if (skipTicks > 0) {
            skipTicks--;
            return;
        }

        long startTime = System.currentTimeMillis();

        if (!Rs2Inventory.hasItem(995)) {
            Microbot.showMessage("No gp found in your inventory");
            setSkipTicks(10);
            return;
        }
        if (!hasNotedBones() && !hasUnNotedBones()) {
            Microbot.showMessage("No bones found in your inventory");
            setSkipTicks(10);
            return;
        }

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

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        System.out.println("Script loop took: " + totalTime);
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
            setSkipTicks(5);
            return;
        }

        // Switch to Settings tab
        Rs2Tab.switchToSettingsTab();
        setSkipTicks(2);

        //If the house options button is not visible, player is on Display or Sound settings, need to click Controls.
        if(!(Rs2Widget.isWidgetVisible(7602207))){
            Rs2Widget.clickWidget(7602243);
            setSkipTicks(1);
        }

        // Click House Options
        if (Rs2Widget.clickWidget(7602207)) {
            setSkipTicks(2);
        } else {
            System.out.println("House Options button not found.");
            return;
        }

        // Click Leave House
        if (Rs2Widget.clickWidget(24248341)) {
            listen = true;
            setSkipTicks(5);
        } else {
            System.out.println("Leave House button not found.");
        }
    }

    public void unnoteBones() {
        if (client.getWidget(14352385) == null) {
            if (!Rs2Inventory.isItemSelected()) {
                Rs2Inventory.use("bones");
            } else {
                Rs2Npc.interact("Phials", "Use");
                setSkipTicks(2);
            }
        } else if (client.getWidget(14352385) != null) {
            Rs2Keyboard.keyPress('3');
            setSkipTicks(2);
        }
    }

    private void enterHouse() {
        // If we've already visited a house this session, use 'Visit-Last' on advertisement board
        cachedAltar = null;
        if (visitedOnce) {
            Rs2GameObject.interact(ObjectID.HOUSE_ADVERTISEMENT, "Visit-Last");
            setSkipTicks(4);
            listen = false;
            return;
        }

        boolean isAdvertisementWidgetOpen = Rs2Widget.isWidgetVisible(3407875);

        if (!isAdvertisementWidgetOpen) {
            Rs2GameObject.interact(ObjectID.HOUSE_ADVERTISEMENT, "View");
            setSkipTicks(2);
        }

        Widget containerNames = Rs2Widget.getWidget(52, 9);
        Widget containerEnter = Rs2Widget.getWidget(52, 19);
        if (containerNames == null || containerNames.getChildren() == null) return;

        //Sort house advertisements by Gilded Altar availability
        toggleArrow = Rs2Widget.getWidget(3407877);
        if (toggleArrow.getSpriteId() == 1050) {
            Rs2Widget.clickWidget(3407877);
            setSkipTicks(1);
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
                    if (child.getOriginalY() < smallestOriginalY) {
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
                        .anyMatch(obj -> obj.toString().contains(houseOwner)); // Check if houseOwner is part of any listener object
                if (containsHouseOwner) {
                    targetWidget = child;
                    break;
                }
            }
            setSkipTicks(1);
            Rs2Widget.clickChildWidget(3407891, targetWidget.getIndex());
            visitedOnce = true;
            listen = false;
            setSkipTicks(5);
        }
    }

    public void bonesOnAltar() {
        // If we haven't cached the altar yet or it's no longer valid, find it
        if (cachedAltar == null) {
            cachedAltar = Rs2GameObject.findObjectById(ObjectID.ALTAR_40878);
            if (cachedAltar == null) {
                cachedAltar = Rs2GameObject.findObjectById(ObjectID.ALTAR_13197);
            }
            if (cachedAltar == null) {
                cachedAltar = Rs2GameObject.findObjectById(ObjectID.ALTAR_13198);
            }
            if (cachedAltar == null) {
                cachedAltar = Rs2GameObject.findObjectById(ObjectID.ALTAR_13199);
            }
        }
        if(portalCoords == null){
            portalCoords = Rs2Player.getWorldLocation();
        }

        // Use bones on the altar if it's valid
        if (cachedAltar != null) {
            Rs2Inventory.useUnNotedItemOnObject("bones", cachedAltar);
        }
        if(altarCoords == null){
            altarCoords = Rs2Player.getWorldLocation();
        }
        // If portal is more than 10 tiles from altar, use settings menu to leave. Else, just walk back to portal.
        if(usePortal == null){
            usePortal = altarCoords.distanceTo(portalCoords) <= 10;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if(!listen){
            return;
        }
        if(chatMessage.getType() == ChatMessageType.PUBLICCHAT){
            return;
        }
        String chatMsg = chatMessage.getMessage().toLowerCase();
        if(chatMsg.contains("that player is offline")||chatMsg.contains("haven't visited anyone this session")){
            // If we try to use Visit-Last unsuccessfully, these chat messages will appear, and we need to reset vars.
            visitedOnce= false;
            cachedAltar= null;
            usePortal = null;
            altarCoords = null;
            portalCoords = null;
        }
    }
}
