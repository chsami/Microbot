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
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import net.runelite.client.plugins.microbot.util.poh.data.HouseStyle;
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
        GameObject portal = Rs2GameObject.getGameObject(NexusPortal.PORTAL_IDS);
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

    private static List<Integer> FAIRY_RING_IDS = fairyRingIds();
    private static List<Integer> SPIRIT_TREE_IDS = spiritTreeIds();

    private static List<Integer> fairyRingIds() {
        List<Integer> ids = new ArrayList<>();
        ids.addAll(Rs2GameObject.getObjectIdsByName("poh_spirit_ring"));
        ids.addAll(Rs2GameObject.getObjectIdsByName("poh_fairy_ring"));
        return ids;
    }

    private static List<Integer> spiritTreeIds() {
        List<Integer> ids = new ArrayList<>();
        ids.addAll(Rs2GameObject.getObjectIdsByName("poh_spirit_ring"));
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
        return FAIRY_RING_IDS.stream().anyMatch(id -> id == tileObject.getId());
    }

    public static boolean isSpiritTree(TileObject tileObject) {
        return SPIRIT_TREE_IDS.stream().anyMatch(id -> id == tileObject.getId());
    }

    public static Map<WorldPoint, Set<Transport>> getTransportsToPoh() {
        HouseStyle style = HouseStyle.getStyle();
        HouseLocation location = HouseLocation.getHouseLocation();
        Map<WorldPoint, Set<Transport>> transportMap = new HashMap<>();
        if (style == null || location == null) return transportMap;
        WorldPoint insidePoint = style.getPohExitWorldPoint();
        WorldPoint outsidePoint = location.getPortalLocation();

        transportMap.put(null, Set.of(
                new Transport(insidePoint, "Teleport to House", TransportType.TELEPORTATION_SPELL, true, 19, Map.of(Skill.MAGIC, 40)),
                new Transport(insidePoint, "Construction cape: Tele to POH", TransportType.TELEPORTATION_ITEM, true, 19, Set.of(Set.of(9789), Set.of(9790))),
                new Transport(insidePoint, "Teleport to House tablet: Outside", TransportType.TELEPORTATION_ITEM, true, 19, Set.of(Set.of(8013)))
        ));
        transportMap.put(outsidePoint, Set.of(
                new Transport(outsidePoint, insidePoint, location.name() + " -> PoH", TransportType.TELEPORTATION_PORTAL, true, "Home", "Portal", location.getPortalId())
        ));
        return transportMap;
    }

}
