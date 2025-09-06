package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.api.TileObject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBoxType;
import net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

/**
 * Contains all the functionality for your POH Teleports
 * TODO:
 * 1. Check if the Basic jewellery box & Fancy jewellery box use the same interface as
 * the ornate jewellery box.
 * 2. add fortis colosseum location in jewelleryLocationEnum
 * 3. Add configuration to allow the user to choose between teleports in wilderness or not
 */
public class PohTeleports {

    /**
     * Checks if the player is in their house
     * based on the purple portal and if the player is
     * in an instance
     *
     * @return
     */
    public static boolean isInHouse() {
        return Rs2Player.IsInInstance() && Rs2GameObject.getGameObject(ObjectID.POH_EXIT_PORTAL) != null;
    }

    /**
     * Checks if a player is in their house
     * sends a microbot log if the player is not in their house
     *
     * @return
     */
    public static boolean checkIsInHouse() {
        if (!isInHouse()) {
            Microbot.log("You do not seem to be in a POH.");
            return false;
        }
        return true;
    }

    /**
     * Interacts with the jewelllerybox in a players house
     * The reason we use JewelleryLocationEnum is because it contains all the data we need for
     * jewellery teleports, so there was no need to add a seperate jewellerybox enum for the locations
     * JewelleryLocationEnum is also used for teleporting with jewellery that the player is wearing
     * or has in his inventory
     * Teleport currently not added: Fortis Colosseum.
     * Requirements: Hero	12,000	Ability to teleport to the Colosseum via the ring of dueling
     *
     * @return
     */
    public static boolean useJewelleryBox(JewelleryLocationEnum jewelleryLocationEnum) {

        if (jewelleryLocationEnum == JewelleryLocationEnum.FORTIS_COLOSSEUM) {
            Microbot.log("This teleport has not been added. If you have the coordinates for this teleport please add it in the JewelleryLocationEnum.");
            return false;
        }

        if (!checkIsInHouse()) return false;

        if (getJewelleryBoxInterface() == null) {
            Rs2GameObject.interact(JewelleryBoxType.getObject(), "Teleport Menu");
        }

        sleepUntil(() -> getJewelleryBoxInterface() != null);

        return interactWithJewelleryBoxWidget(jewelleryLocationEnum);
    }

    /**
     * Checks if the jewellerybox interface is open
     *
     * @return
     */
    public static Widget getJewelleryBoxInterface() {
        return Rs2Widget.getWidget(InterfaceID.POH_JEWELLERY_BOX, 0);
    }

    /**
     * Interact with the jewellerybox widget based on the
     * JewelleryLocationEnum destination description
     *
     * @param jewelleryLocationEnum
     * @return
     */
    public static boolean interactWithJewelleryBoxWidget(JewelleryLocationEnum jewelleryLocationEnum) {
        Widget mainWidget = getJewelleryBoxInterface();

        if (mainWidget == null) return false;

        Widget widget = Rs2Widget.findWidget(jewelleryLocationEnum.getDestination().toLowerCase(), Arrays.stream(mainWidget.getStaticChildren()).collect(Collectors.toList()));

        boolean isTeleportDisabled = widget.getText().contains("<str>");

        if (isTeleportDisabled) {
            Microbot.log(jewelleryLocationEnum.getDestination() + " teleport is not unlocked.");
            return false;
        }

        if (!Rs2Widget.clickWidget(widget)) return false;

        Rs2Player.waitForAnimation();

        return true;
    }

    /**
     * Will click on the nexus and interact with the widget
     *
     * @param nexusPortal
     * @return
     */
    public static boolean usePortalNexus(NexusPortal nexusPortal) {
        //TODO: Add config here to inform the user if the teleport is a wilderness teleport
        if (getPortalNexusInterface() == null) {
            TileObject tileObject = Rs2GameObject.getTileObject(NexusPortal.PORTAL_IDS);
            Rs2GameObject.interact(tileObject, "Teleport Menu");
        }

        sleepUntil(() -> getPortalNexusInterface() != null);

        return interactWithPortalNexusWidget(nexusPortal);
    }

    public static Widget getPortalNexusInterface() {
        return Rs2Widget.getWidget(InterfaceID.TELENEXUS_TELEPORT, 0);
    }

    /**
     * Will interact with the portal nexus widget if it's open
     *
     * @param nexusPortal
     * @return
     */
    public static boolean interactWithPortalNexusWidget(NexusPortal nexusPortal) {
        Widget portalNexusWidget = getPortalNexusInterface();
        if (portalNexusWidget == null) return false;

        Widget widget = Rs2Widget.findWidget(nexusPortal.getText().toLowerCase(), Arrays.stream(portalNexusWidget.getStaticChildren()).collect(Collectors.toList()));

        if (widget == null) return false;

        // Regular expression to capture text between <col=ffffff> and </col>
        String regex = "<col=ffffff>(.*?)</col>";

        // Use regex to extract the letter O
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(widget.getText());

        if (!matcher.find()) return false;

        // Extract the matched text
        String shortKey = matcher.group(1);
        // The reason we use the shortkeys instead of clicking the menu is to avoid scrolling
        // Some of the teleports are not visible in the ui to click on
        // Using shortkeys should always work even if the teleport is not visible on the screen
        Rs2Keyboard.typeString(String.valueOf(shortKey));

        boolean isWildernessInterfaceOpen = sleepUntilTrue(Rs2Widget::isWildernessInterfaceOpen, 100, 1000);

        if (isWildernessInterfaceOpen) {
            Rs2Widget.enterWilderness();
        }

        sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(nexusPortal.getLocation()) < 10);

        return true;
    }

    /**
     * Attempts to teleport the player to their Player-Owned House (POH) using available methods in the following order:
     * 1. If the player is wearing the “Construct. cape”, it interacts with it to teleport.
     * 2. If the player has the “Construct. cape” in their inventory, it interacts with it to teleport.
     * 3. If the player has a “Teleport to house” item in their inventory, it uses the item to teleport.
     * 4. If the player can cast the "Teleport to house" spell, it casts the spell.
     * If none of these conditions are met, the teleport will fail.
     *
     * @return true if the teleport action was successfully performed; false otherwise
     */
    public static boolean teleportToPoh() {
        if (Rs2Equipment.isWearing("Construct. cape", false)) {
            return Rs2Equipment.interact("Construct. cape", "Tele to POH");
        } else if (Rs2Inventory.contains("Construct. cape", false)) {
            return Rs2Inventory.interact("Construct. cape", "Tele to POH");
        } else if (Rs2Inventory.contains("Teleport to house")) {
            return Rs2Inventory.interact("Teleport to house", "Break");
        } else if (Rs2Magic.canCast(Rs2Spells.TELEPORT_TO_HOUSE)) {
            return Rs2Magic.cast(Rs2Spells.TELEPORT_TO_HOUSE);
        }
        return false;
    }

    /**
     * Determines whether the player has a method to teleport to their Player-Owned House (POH).
     * This can be satisfied by the following criteria:
     * 1. The player has a "Construct. cape" in their inventory or is wearing it.
     * 2. The player has a "Teleport to house" item in their inventory.
     * 3. The player can cast the "Teleport to house" spell.
     *
     * @return true if the player possesses at least one method to teleport to their POH, false otherwise.
     */
    public static boolean hasTeleportToPoh() {
        if (Rs2Inventory.contains(false, "Construct. cape") || Rs2Equipment.isWearing("Construct. cape", false)) {
            return true;
        }
        if (Rs2Inventory.contains("Teleport to house")) {
            return true;
        }
        return Rs2Magic.canCast(Rs2Spells.TELEPORT_TO_HOUSE);
    }
}
