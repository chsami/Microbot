package net.runelite.client.plugins.microbot.util.poh;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBoxType;
import net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.*;
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
@Slf4j
public class PohTeleports {

    /**
     * Checks if the player is in their house
     * based on the purple portal and if the player is
     * in an instance
     *
     * @return
     */
    public static boolean isInHouse() {
        if (!Rs2Player.IsInInstance()) return false;
        // Use the tile-object cache rather than Rs2GameObject.getGameObject; the latter
        // routes through Rs2Player.getWorldLocation() as a scene anchor which returns the
        // overworld-template tile inside a POH instance and breaks the scene lookup.
        return Microbot.getRs2TileObjectCache()
                .query()
                .withId(ObjectID.POH_EXIT_PORTAL)
                .nearest() != null;
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
     * Checks if the player has a house
     *
     * @return true if a house location is found
     */
    public static boolean hasHouse() {
        return HouseLocation.getHouseLocation() != null;
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

        if (widget == null) {
            Microbot.log(jewelleryLocationEnum.getDestination() + " widget not found in jewellery box");
            return false;
        }

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
        GameObject portal = findPohObjectAnywhere(NexusPortal.PORTAL_IDS);
        if (getPortalNexusInterface() == null) {
            if (portal != null) {
                Rs2GameObject.interact(portal, "Teleport Menu");
            } else {
                log.warn("Portal nexus not found");
            }
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

    private static final List<Integer> POH_SPIRIT_RING_IDS = Rs2GameObject.getObjectIdsByName("poh_spirit_ring");
    private static final Set<Integer> FAIRY_RING_IDS = fairyRingIds();
    private static final Set<Integer> SPIRIT_TREE_IDS = spiritTreeIds();

    private static Set<Integer> fairyRingIds() {
        Set<Integer> ids = new LinkedHashSet<>(POH_SPIRIT_RING_IDS);
        ids.addAll(Rs2GameObject.getObjectIdsByName("poh_fairy_ring"));
        return ids;
    }

    private static Set<Integer> spiritTreeIds() {
        Set<Integer> ids = new LinkedHashSet<>(POH_SPIRIT_RING_IDS);
        ids.addAll(Rs2GameObject.getObjectIdsByName("poh_spirit_tree"));
        return ids;
    }

    public static GameObject getFairyRings() {
        return Rs2GameObject.getGameObject(PohTeleports::isFairyRing);
    }

    public static GameObject getSpiritTree() {
        return Rs2GameObject.getGameObject(PohTeleports::isSpiritTree);
    }

    public static boolean isFairyRing(TileObject tileObject) {
        return FAIRY_RING_IDS.contains(tileObject.getId());
    }

    public static boolean isSpiritTree(TileObject tileObject) {
        return SPIRIT_TREE_IDS.contains(tileObject.getId());
    }

    /**
     * Scans the entire loaded scene for any GameObject matching the given ids, bypassing the
     * {@link Rs2Player#getWorldLocation()} anchor that breaks every Rs2GameObject search when
     * the player is in a POH instance.
     *
     * <p>Inside a POH, {@code Rs2Player.getWorldLocation()} returns the overworld-template tile
     * (e.g. (1877, 7052, 1)), which isn't present in the actual loaded scene grid, so any
     * search routed through that anchor converts to a null LocalPoint and returns nothing
     * even when the object is plainly visible. This helper walks the raw
     * {@code Scene.getTiles()[][][]} structure directly on the client thread, which is always
     * valid regardless of instance.</p>
     */
    private static GameObject findPohObjectAnywhere(Integer[] ids) {
        java.util.Set<Integer> idSet = java.util.Arrays.stream(ids).collect(java.util.stream.Collectors.toSet());
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            net.runelite.api.Client client = Microbot.getClient();
            if (client == null) return null;
            net.runelite.api.Scene scene = client.getTopLevelWorldView().getScene();
            net.runelite.api.Tile[][][] tiles = scene.getTiles();
            for (net.runelite.api.Tile[][] plane : tiles) {
                for (net.runelite.api.Tile[] row : plane) {
                    for (net.runelite.api.Tile tile : row) {
                        if (tile == null) continue;
                        for (GameObject go : tile.getGameObjects()) {
                            if (go != null && idSet.contains(go.getId())) return go;
                        }
                    }
                }
            }
            return null;
        }).orElse(null);
    }
}
