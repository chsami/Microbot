package net.runelite.client.plugins.microbot.util.sailing;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.annotations.Varbit;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.sailing.data.BoatType;
import net.runelite.client.plugins.microbot.util.sailing.data.Heading;
import net.runelite.client.plugins.microbot.util.sailing.data.PortTaskData;
import net.runelite.client.plugins.microbot.util.sailing.data.PortTaskVarbits;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static net.runelite.api.gameval.ObjectID.*;
import static net.runelite.client.plugins.microbot.Microbot.log;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class Rs2Sailing {

    public static void handleChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE) {
            String message = event.getMessage();
            if (message.contains("You feel a gust of wind")) {
                Microbot.getClientThread().runOnSeperateThread(Rs2Sailing::trimSails);
            }
        }
    }

    //temp fix for disembark plank
    public static final int[] ganpgplank_disembark = {
            SAILING_GANGPLANK_PORT_SARIM,
            SAILING_GANGPLANK_THE_PANDEMONIUM,
            SAILING_GANGPLANK_LANDS_END,
            SAILING_GANGPLANK_MUSA_POINT,
            SAILING_GANGPLANK_HOSIDIUS,
            SAILING_GANGPLANK_RIMMINGTON,
            SAILING_GANGPLANK_CATHERBY,
            SAILING_GANGPLANK_PORT_PISCARILIUS,
            SAILING_GANGPLANK_BRIMHAVEN,
            SAILING_GANGPLANK_ARDOUGNE,
            SAILING_GANGPLANK_PORT_KHAZARD,
            SAILING_GANGPLANK_WITCHAVEN,
            SAILING_GANGPLANK_ENTRANA,
            SAILING_GANGPLANK_CIVITAS_ILLA_FORTIS,
            SAILING_GANGPLANK_CORSAIR_COVE,
            SAILING_GANGPLANK_CAIRN_ISLE,
            SAILING_GANGPLANK_SUNSET_COAST,
            SAILING_GANGPLANK_THE_SUMMER_SHORE,
            SAILING_GANGPLANK_ALDARIN,
            SAILING_GANGPLANK_RUINS_OF_UNKAH,
            SAILING_GANGPLANK_VOID_KNIGHTS_OUTPOST,
            SAILING_GANGPLANK_PORT_ROBERTS,
            SAILING_GANGPLANK_RED_ROCK,
            SAILING_GANGPLANK_RELLEKKA,
            SAILING_GANGPLANK_ETCETERIA,
            SAILING_GANGPLANK_PORT_TYRAS,
            SAILING_GANGPLANK_DEEPFIN_POINT,
            SAILING_GANGPLANK_JATIZSO,
            SAILING_GANGPLANK_NEITIZNOT,
            SAILING_GANGPLANK_PRIFDDINAS,
            SAILING_GANGPLANK_PISCATORIS,
            SAILING_GANGPLANK_LUNAR_ISLE,
            SAILING_MOORING_ISLE_OF_SOULS,
            SAILING_MOORING_WATERBIRTH_ISLAND,
            SAILING_MOORING_WEISS,
            SAILING_MOORING_DOGNOSE_ISLAND,
            SAILING_MOORING_REMOTE_ISLAND,
            SAILING_MOORING_THE_LITTLE_PEARL,
            SAILING_MOORING_THE_ONYX_CREST,
            SAILING_MOORING_LAST_LIGHT,
            SAILING_MOORING_CHARRED_ISLAND,
            SAILING_MOORING_VATRACHOS_ISLAND,
            SAILING_MOORING_ANGLERS_RETREAT,
            SAILING_MOORING_MINOTAURS_REST,
            SAILING_MOORING_ISLE_OF_BONES,
            SAILING_MOORING_TEAR_OF_THE_SOUL,
            SAILING_MOORING_WINTUMBER_ISLAND,
            SAILING_MOORING_THE_CROWN_JEWEL,
            SAILING_MOORING_RAINBOWS_END,
            SAILING_MOORING_SUNBLEAK_ISLAND,
            SAILING_MOORING_SHIMMERING_ATOLL,
            SAILING_MOORING_LAGUNA_AURORAE,
            SAILING_MOORING_CHINCHOMPA_ISLAND,
            SAILING_MOORING_LLEDRITH_ISLAND,
            SAILING_MOORING_YNYSDAIL,
            SAILING_MOORING_BUCCANEERS_HAVEN,
            SAILING_MOORING_DRUMSTICK_ISLE,
            SAILING_MOORING_BRITTLE_ISLE,
            SAILING_MOORING_GRIMSTONE
    };

    public static Heading currentHeading = Heading.SOUTH;

    public static BoatType getBoatType() {
        if (Microbot.getVarbitPlayerValue(VarPlayerID.SAILING_SIDEPANEL_BOAT_TYPE) == 8110) {
            return BoatType.RAFT;
        }
        return BoatType.RAFT;
    }

    public static int getSteeringForBoatType() {
        switch (getBoatType()) {
            case RAFT:
                return SAILING_BOAT_STEERING_KANDARIN_1X3_WOOD;
            case SKIFF:
                // Return SKIFF steering object ID
            case SLOOP:
                // Return SLOOP steering object ID
            default:
                return SAILING_BOAT_STEERING_KANDARIN_1X3_WOOD;
        }
    }

    /**
     * Check if the player is currently on a boat.
     * @return
     */
    public static boolean isOnBoat() {
        return Microbot.getVarbitValue(VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT) == 1;
    }

    public static boolean isNavigating() {
        return Microbot.getVarbitValue(VarbitID.SAILING_SIDEPANEL_PLAYER_AT_HELM) == 1;
    }

    public static boolean navigate() {
        if (!isOnBoat()) return false;
        if (isNavigating()) return true;
        Rs2GameObject.interact(getSteeringForBoatType(), "Navigate");
        sleepUntil(Rs2Sailing::isNavigating, 5000);
        return isNavigating();
    }

    /**
     * Get the player's location relative to the boat they are on.
     * @return
     */
    public static WorldPoint getPlayerBoatLocation() {
        return Microbot.getClientThread().invoke(() -> {
            if (!isOnBoat()) {
                if (Microbot.getClient().getTopLevelWorldView().getScene().isInstance()) {
                    LocalPoint l = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), Microbot.getClient().getLocalPlayer().getWorldLocation());
                    return WorldPoint.fromLocalInstance(Microbot.getClient(), l);
                } else {
                    return Microbot.getClient().getLocalPlayer().getWorldLocation();
                }
            }
            WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
            LocalPoint localPoint = LocalPoint.fromWorld(
                    Microbot.getClient().getLocalPlayer().getWorldView(),
                    playerLocation
            );

            var mainWorldProjection = Microbot.getClient()
                    .getLocalPlayer()
                    .getWorldView()
                    .getMainWorldProjection();

            if (mainWorldProjection == null) {
                return playerLocation;
            }

            float[] projection = mainWorldProjection
                    .project(localPoint.getX(), 0, localPoint.getY());

            return Microbot.getClientThread().invoke(() -> WorldPoint.fromLocal(
                    Microbot.getClient().getTopLevelWorldView(),
                    (int) projection[0],
                    (int) projection[2],
                    0
            ));
        });
    }

    public static boolean boardBoat() {
        if (isOnBoat()) {
            return true;
        }
        int[] SAILING_GANGPLANKS = {
                59831, // DISEMBARK
                59832, // EMBARK
                59833, // MOORING_DISEMBARK
                59834, // MOORING_EMBARK
                59835, // PORT_SARIM
                59836, // THE_PANDEMONIUM
                59837, // LANDS_END
                59838, // MUSA_POINT
                59839, // HOSIDIUS
                59840, // RIMMINGTON
                59841, // CATHERBY
                59842, // PORT_PISCARILIUS
                59843, // BRIMHAVEN
                59844, // ARDOUGNE
                59845, // PORT_KHAZARD
                59846, // WITCHAVEN
                59847, // ENTRANA
                59848, // CIVITAS_ILLA_FORTIS
                59849, // CORSAIR_COVE
                59850, // CAIRN_ISLE
                59851, // SUNSET_COAST
                59852, // THE_SUMMER_SHORE
                59853, // ALDARIN
                59854, // RUINS_OF_UNKAH
                59855, // VOID_KNIGHTS_OUTPOST
                59856, // PORT_ROBERTS
                59857, // RED_ROCK
                59858, // RELLEKKA
                59859, // ETCETERIA
                59860, // PORT_TYRAS
                59861, // DEEPFIN_POINT
                59862, // JATIZSO
                59863, // NEITIZNOT
                59864, // PRIFDDINAS
                59865, // PISCATORIS
                59866  // LUNAR_ISLE
        };
        Rs2GameObject.interact(SAILING_GANGPLANKS, "Board");
        sleepUntil(Rs2Sailing::isOnBoat, 5000);
        return isOnBoat();
    }

    /**
     * This will be removed once reworked with new api, for now it works
     * @return
     */
    public static boolean disembarkBoat() {
        if (!isOnBoat()) {
            return true;
        }
        int[] SAILING_GANGPLANKS = {
                59831, // DISEMBARK
                59832, // EMBARK
                59833, // MOORING_DISEMBARK
                59834, // MOORING_EMBARK
                59835, // PORT_SARIM
                59836, // THE_PANDEMONIUM
                59837, // LANDS_END
                59838, // MUSA_POINT
                59839, // HOSIDIUS
                59840, // RIMMINGTON
                59841, // CATHERBY
                59842, // PORT_PISCARILIUS
                59843, // BRIMHAVEN
                59844, // ARDOUGNE
                59845, // PORT_KHAZARD
                59846, // WITCHAVEN
                59847, // ENTRANA
                59848, // CIVITAS_ILLA_FORTIS
                59849, // CORSAIR_COVE
                59850, // CAIRN_ISLE
                59851, // SUNSET_COAST
                59852, // THE_SUMMER_SHORE
                59853, // ALDARIN
                59854, // RUINS_OF_UNKAH
                59855, // VOID_KNIGHTS_OUTPOST
                59856, // PORT_ROBERTS
                59857, // RED_ROCK
                59858, // RELLEKKA
                59859, // ETCETERIA
                59860, // PORT_TYRAS
                59861, // DEEPFIN_POINT
                59862, // JATIZSO
                59863, // NEITIZNOT
                59864, // PRIFDDINAS
                59865, // PISCATORIS
                59866  // LUNAR_ISLE
        };
        WorldView wv = Microbot.getClient().getTopLevelWorldView();

        Scene scene = wv.getScene();
        Tile[][][] tiles = scene.getTiles();

        int z = wv.getPlane();

        for (int x = 0; x < tiles[z].length; ++x) {
            for (int y = 0; y < tiles[z][x].length; ++y) {
                Tile tile = tiles[z][x][y];

                if (tile == null) {
                    continue;
                }

                Player player = Microbot.getClient().getLocalPlayer();
                if (player == null) {
                    continue;
                }

                if (tile.getGroundObject() == null) {
                    continue;
                }

                if (Arrays.stream(SAILING_GANGPLANKS).anyMatch(id -> id == tile.getGroundObject().getId())) {
                    Rs2GameObject.clickObject(tile.getGroundObject(), "Disembark");
                    sleepUntil(() -> !isOnBoat(), 5000);
                }
            }
        }
        Rs2GameObject.interact(SAILING_GANGPLANKS, "disembark");
        sleepUntil(() -> !isOnBoat(), 5000);
        return !isOnBoat();
    }

    public static boolean isMovingForward() {
        final int movingForward = 2;
        return Microbot.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_MOVE_MODE) == movingForward;
    }

    public static boolean isMovingBackward() {
        final int movingBackward = 3;
        return Microbot.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_MOVE_MODE) == movingBackward;
    }

    public static boolean isStandingStill() {
        final int standingStill = 0;
        return Microbot.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_MOVE_MODE) == standingStill;
    }

    public static boolean clickSailButton()  {
        var widget = Rs2Widget.getWidget(InterfaceID.SailingSidepanel.FACILITIES_ROWS);
        var setSailButton = widget.getDynamicChildren()[0];
        return Rs2Widget.clickWidget(setSailButton);
    }

    /**
     * Set the sails to full speed ahead.
     */
    public static void setSails()
    {
        if(!isNavigating())
        {
            return;
        }
        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        if (!isMovingForward()) {
            clickSailButton();
            sleepUntil(Rs2Sailing::isMovingForward, 2500);
        }
    }

    /**
     * Set the sails to neutral.
     */
    public static void unsetSails()
    {
        if(!isNavigating())
        {
            return;
        }
        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        if (!isStandingStill()) {
            clickSailButton();
            sleepUntil(Rs2Sailing::isStandingStill, 2500);
        }
    }

    public static void sailTo(WorldPoint target) {
        if (!isOnBoat()) {
            var result = boardBoat();
            if (!result) {
                log("Failed to board boat.");
            }
            return;
        }
        if (!isNavigating()) {
            var result = navigate();
            if (!result) {
                log("Failed to navigate boat.");
            }
            return;
        }

        var direction = getDirection(target);
        var heading = Heading.getHeading(direction);
        setHeading(heading);

        if (!isMovingForward()) {
            setSails();
        }
    }

    public static int getDirection(WorldPoint target) {
        double angle = getAngle(target);

        double rotated = 270.0 - angle;

        // Normalize [0, 360)
        rotated %= 360.0;
        if (rotated < 0) {
            rotated += 360.0;
        }

        return (int) Math.round(rotated / 22.5) & 0xF;
    }

    private static double getAngle(WorldPoint target) {
        WorldPoint worldPoint = Rs2Sailing.getPlayerBoatLocation();
        int playerX = worldPoint.getX();
        int playerY = worldPoint.getY();

        int targetX = target.getX();
        int targetY = target.getY();

        double dx = targetX - playerX;
        double dy = targetY - playerY;

        return Math.toDegrees(Math.atan2(dy, dx));
    }

    /**
     * Set the boat's heading.
     * @param heading
     */
    public static void setHeading(Heading heading) {
        if (heading == currentHeading) {
            return;
        }
        currentHeading = heading;
        var menuEntry = new NewMenuEntry()
                .option("Set-Heading")
                .target("")
                .identifier(heading.getValue())
                .type(MenuAction.SET_HEADING)
                .param0(0)
                .param1(0)
                .forceLeftClick(false);
        var worldview = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldView());

        if (worldview == null)  {
            menuEntry.setWorldViewId(Microbot.getClient().getTopLevelWorldView().getId());
        } else {
            menuEntry.setWorldViewId(worldview.getId());
        }
        Microbot.doInvoke(menuEntry, new java.awt.Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
    }

    public static boolean trimSails() {
        sleep(2500, 3500);
        if (!isOnBoat()) {
            return false;
        }
        System.out.println("interacting to trim sails");
        final int[] SAIL_IDS = {
                SAILING_BOAT_SAIL_KANDARIN_1X3_WOOD,
                SAILING_BOAT_SAIL_KANDARIN_1X3_OAK,
                SAILING_BOAT_SAIL_KANDARIN_1X3_TEAK,
                SAILING_BOAT_SAIL_KANDARIN_1X3_MAHOGANY,
                SAILING_BOAT_SAIL_KANDARIN_1X3_CAMPHOR,
                SAILING_BOAT_SAIL_KANDARIN_1X3_IRONWOOD,
                SAILING_BOAT_SAIL_KANDARIN_1X3_ROSEWOOD,

                SAILING_BOAT_SAIL_KANDARIN_2X5_WOOD,
                SAILING_BOAT_SAIL_KANDARIN_2X5_OAK,
                SAILING_BOAT_SAIL_KANDARIN_2X5_TEAK,
                SAILING_BOAT_SAIL_KANDARIN_2X5_MAHOGANY,
                SAILING_BOAT_SAIL_KANDARIN_2X5_CAMPHOR,
                SAILING_BOAT_SAIL_KANDARIN_2X5_IRONWOOD,
                SAILING_BOAT_SAIL_KANDARIN_2X5_ROSEWOOD,

                SAILING_BOAT_SAIL_KANDARIN_3X8_WOOD,
                SAILING_BOAT_SAIL_KANDARIN_3X8_OAK,
                SAILING_BOAT_SAIL_KANDARIN_3X8_TEAK,
                SAILING_BOAT_SAIL_KANDARIN_3X8_MAHOGANY,
                SAILING_BOAT_SAIL_KANDARIN_3X8_CAMPHOR,
                SAILING_BOAT_SAIL_KANDARIN_3X8_IRONWOOD,
                SAILING_BOAT_SAIL_KANDARIN_3X8_ROSEWOOD,

                SAILING_BOAT_SAILS_COLOSSAL_REGULAR
        };
        Rs2GameObject.interact(SAIL_IDS, "trim");
        return sleepUntil(() -> Microbot.isGainingExp, 5000);
    }

    /**
     * Open the cargo hold of the boat.
     * @return
     */
    public static boolean openCargo() {
        if (!isOnBoat()) {
            return false;
        }
        final int[] SAILING_BOAT_CARGO_HOLDS = {
                SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT,
                SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT_OPEN,

                SAILING_BOAT_CARGO_HOLD_OAK_RAFT,
                SAILING_BOAT_CARGO_HOLD_OAK_RAFT_OPEN,

                SAILING_BOAT_CARGO_HOLD_TEAK_RAFT,
                SAILING_BOAT_CARGO_HOLD_TEAK_RAFT_OPEN,

                SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT,
                SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT_OPEN,

                SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT,
                SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT_OPEN,

                SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT,
                SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT_OPEN,

                SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT,
                SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT_OPEN,

                SAILING_BOAT_CARGO_HOLD_REGULAR_2X5,
                SAILING_BOAT_CARGO_HOLD_REGULAR_2X5_OPEN,

                SAILING_BOAT_CARGO_HOLD_OAK_2X5,
                SAILING_BOAT_CARGO_HOLD_OAK_2X5_OPEN,

                SAILING_BOAT_CARGO_HOLD_TEAK_2X5,
                SAILING_BOAT_CARGO_HOLD_TEAK_2X5_OPEN,

                SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5,
                SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5_OPEN,

                SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5,
                SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5_OPEN,

                SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5,
                SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5_OPEN,

                SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5,
                SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5_OPEN,

                SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE,
                SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE_OPEN,

                SAILING_BOAT_CARGO_HOLD_OAK_LARGE,
                SAILING_BOAT_CARGO_HOLD_OAK_LARGE_OPEN,

                SAILING_BOAT_CARGO_HOLD_TEAK_LARGE,
                SAILING_BOAT_CARGO_HOLD_TEAK_LARGE_OPEN,

                SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE,
                SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE_OPEN,

                SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE,
                SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE_OPEN,

                SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE,
                SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE_OPEN,

                SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE,
                SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE_OPEN
        };
        return Rs2GameObject.interact(SAILING_BOAT_CARGO_HOLDS, "open");
    }

    /**
     * Get all port task varbits with values greater than 0.
     * @return
     */
    public static Map<PortTaskVarbits, Integer> getPortTasksVarbits()
    {
        return Arrays.stream(PortTaskVarbits.values())
                .map(x -> Map.entry(x, Microbot.getVarbitValue(x.getId())))
                .filter(e -> e.getValue() > 0 && e.getKey().getType() == PortTaskVarbits.TaskType.ID)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Get the PortTaskData for a specific varbit ID.
     * @param varbitValue
     * @return
     */
    public static PortTaskData getPortTaskData(int varbitValue)
    {
        if (varbitValue <= 0) {
            return null;
        }

        return Arrays.stream(PortTaskData.values())
                .filter(x -> x.getId() == varbitValue)
                .findFirst()
                .orElse(null);
    }
}
