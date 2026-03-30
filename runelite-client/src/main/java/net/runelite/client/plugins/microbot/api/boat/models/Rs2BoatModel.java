package net.runelite.client.plugins.microbot.api.boat.models;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.api.boat.data.BoatType;
import net.runelite.client.plugins.microbot.api.boat.data.Heading;
import net.runelite.client.plugins.microbot.api.boat.data.PortTaskData;
import net.runelite.client.plugins.microbot.api.boat.data.PortTaskVarbits;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static net.runelite.api.gameval.ObjectID1.*;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class Rs2BoatModel implements WorldEntity, IEntity {

    private static final int[] GANGPLANK_IDS = {
            59831, 59832, 59833, 59834, 59835, 59836, 59837, 59838, 59839, 59840,
            59841, 59842, 59843, 59844, 59845, 59846, 59847, 59848, 59849, 59850,
            59851, 59852, 59853, 59854, 59855, 59856, 59857, 59858, 59859, 59860,
            59861, 59862, 59863, 59864, 59865, 59866
    };

    private static final int[] SAIL_IDS = {
            SAILING_BOAT_SAIL_KANDARIN_1X3_WOOD, SAILING_BOAT_SAIL_KANDARIN_1X3_OAK,
            SAILING_BOAT_SAIL_KANDARIN_1X3_TEAK, SAILING_BOAT_SAIL_KANDARIN_1X3_MAHOGANY,
            SAILING_BOAT_SAIL_KANDARIN_1X3_CAMPHOR, SAILING_BOAT_SAIL_KANDARIN_1X3_IRONWOOD,
            SAILING_BOAT_SAIL_KANDARIN_1X3_ROSEWOOD, SAILING_BOAT_SAIL_KANDARIN_2X5_WOOD,
            SAILING_BOAT_SAIL_KANDARIN_2X5_OAK, SAILING_BOAT_SAIL_KANDARIN_2X5_TEAK,
            SAILING_BOAT_SAIL_KANDARIN_2X5_MAHOGANY, SAILING_BOAT_SAIL_KANDARIN_2X5_CAMPHOR,
            SAILING_BOAT_SAIL_KANDARIN_2X5_IRONWOOD, SAILING_BOAT_SAIL_KANDARIN_2X5_ROSEWOOD,
            SAILING_BOAT_SAIL_KANDARIN_3X8_WOOD, SAILING_BOAT_SAIL_KANDARIN_3X8_OAK,
            SAILING_BOAT_SAIL_KANDARIN_3X8_TEAK, SAILING_BOAT_SAIL_KANDARIN_3X8_MAHOGANY,
            SAILING_BOAT_SAIL_KANDARIN_3X8_CAMPHOR, SAILING_BOAT_SAIL_KANDARIN_3X8_IRONWOOD,
            SAILING_BOAT_SAIL_KANDARIN_3X8_ROSEWOOD, SAILING_BOAT_SAILS_COLOSSAL_REGULAR
    };

    private static final int[] CARGO_HOLD_IDS = {
            SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT,
            SAILING_BOAT_CARGO_HOLD_OAK_RAFT,
            SAILING_BOAT_CARGO_HOLD_TEAK_RAFT,
            SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT,
            SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT,
            SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT,
            SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT,
            SAILING_BOAT_CARGO_HOLD_REGULAR_2X5,
            SAILING_BOAT_CARGO_HOLD_OAK_2X5,
            SAILING_BOAT_CARGO_HOLD_TEAK_2X5,
            SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5,
            SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5,
            SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5,
            SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5,
            SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE,
            SAILING_BOAT_CARGO_HOLD_OAK_LARGE,
            SAILING_BOAT_CARGO_HOLD_TEAK_LARGE,
            SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE,
            SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE,
            SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE,
            SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE
    };

    private static final int MOVE_MODE_STANDING_STILL = 0;
    private static final int MOVE_MODE_FORWARD = 2;
    private static final int MOVE_MODE_BACKWARD = 3;

    private static final int SAILING_BOAT_RAFT = 8110;
    private static final int SAILING_BOAT_SKIFF = 8111;
    private static final int SAILING_BOAT_SLOOP = 8112;
    private static final int SAILING_BOAT_WILL_ANNE = 8113;

    protected final WorldEntity boat;

    public Rs2BoatModel(WorldEntity boat)
    {
        this.boat = boat;
    }

    @Override
    public WorldView getWorldView()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(boat::getWorldView).orElse(null);
    }

    @Override
    public LocalPoint getCameraFocus()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(boat::getCameraFocus).orElse(null);
    }

    @Override
    public LocalPoint getLocalLocation()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(boat::getLocalLocation).orElse(null);
    }

    @Override
    public WorldPoint getWorldLocation()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            WorldView worldView = boat.getWorldView();
            LocalPoint localLocation = boat.getLocalLocation();

            if (worldView == null || localLocation == null)
            {
                return null;
            }

            return WorldPoint.fromLocal(worldView, localLocation.getX(), localLocation.getY(), worldView.getPlane());
        }).orElse(null);
    }

    @Override
    public int getOrientation()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(boat::getOrientation).orElse(0);
    }

    @Override
    public LocalPoint getTargetLocation()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(boat::getTargetLocation).orElse(null);
    }

    @Override
    public int getTargetOrientation()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(boat::getTargetOrientation).orElse(0);
    }

    @Override
    public LocalPoint transformToMainWorld(LocalPoint point)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> boat.transformToMainWorld(point)).orElse(null);
    }

    @Override
    public boolean isHiddenForOverlap()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(boat::isHiddenForOverlap).orElse(false);
    }

    @Override
    public WorldEntityConfig getConfig()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(boat::getConfig).orElse(null);
    }

    @Override
    public int getOwnerType()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(boat::getOwnerType).orElse(OWNER_TYPE_NOT_PLAYER);
    }

    @Override
    public int getId()
    {
        WorldEntityConfig config = getConfig();
        return config != null ? config.getId() : -1;
    }

    @Override
    public String getName()
    {
        return "WorldEntity";
    }

    @Override
    public boolean click()
    {
        return click("");
    }

    @Override
    public boolean click(String action)
    {
        return false;
    }

    public BoatType getBoatType()
    {
        int boatTypeValue = Microbot.getVarbitPlayerValue(VarPlayerID.SAILING_SIDEPANEL_BOAT_TYPE);
        switch (boatTypeValue)
        {
            case SAILING_BOAT_SKIFF:
                return BoatType.SKIFF;
            case SAILING_BOAT_SLOOP:
                return BoatType.SLOOP;
            case SAILING_BOAT_WILL_ANNE:
                return BoatType.WILL_ANNE;
            case SAILING_BOAT_RAFT:
            default:
                return BoatType.RAFT;
        }
    }

    public int getSteeringForBoatType()
    {
        return SAILING_BOAT_STEERING_KANDARIN_1X3_WOOD;
    }

    public boolean isOnBoat()
    {
        return boat != null;
    }

    public boolean isNavigating()
    {
        return Microbot.getVarbitValue(VarbitID.SAILING_SIDEPANEL_PLAYER_AT_HELM) == 1;
    }

    public boolean navigate()
    {
        if (!isOnBoat())
        {
            return false;
        }

        if (isNavigating())
        {
            return true;
        }

        Microbot.getRs2TileObjectCache().query().withId(getSteeringForBoatType()).interact("Navigate");
        sleepUntil(() -> isNavigating(), 5000);
        return isNavigating();
    }

    public WorldPoint getPlayerBoatLocation()
    {
        if (boat == null)
        {
            return null;
        }

        return Microbot.getClientThread().invoke(() ->
        {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null)
            {
                return null;
            }

            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            LocalPoint localPoint = LocalPoint.fromWorld(
                    player.getWorldView(),
                    playerLocation
            );

            var mainWorldProjection = player
                    .getWorldView()
                    .getMainWorldProjection();

            if (mainWorldProjection == null)
            {
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

    public boolean boardBoat()
    {
        if (isOnBoat())
        {
            return true;
        }
        Microbot.getRs2TileObjectCache().query().withIds(GANGPLANK_IDS).interact("Board");
        sleepUntil(() -> isOnBoat(), 5000);
        return isOnBoat();
    }

    public boolean disembarkBoat()
    {
        if (!isOnBoat())
        {
            return true;
        }
        Microbot.getRs2TileObjectCache().query().withIds(GANGPLANK_IDS).interact("Disembark");
        sleepUntil(() -> !isOnBoat(), 5000);
        return !isOnBoat();
    }

    private int getMoveMode()
    {
        return Microbot.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_MOVE_MODE);
    }

    public boolean isMovingForward()
    {
        return getMoveMode() == MOVE_MODE_FORWARD;
    }

    public boolean isMovingBackward()
    {
        return getMoveMode() == MOVE_MODE_BACKWARD;
    }

    public boolean isStandingStill()
    {
        return getMoveMode() == MOVE_MODE_STANDING_STILL;
    }

    public boolean clickSailButton()
    {
        var widget = Rs2Widget.getWidget(InterfaceID.SailingSidepanel.FACILITIES_ROWS);
        var setSailButton = widget.getDynamicChildren()[0];
        return Rs2Widget.clickWidget(setSailButton);
    }

    public void setSails()
    {
        if (!isNavigating())
        {
            return;
        }
        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        if (!isMovingForward())
        {
            clickSailButton();
            sleepUntil(() -> isMovingForward(), 2500);
        }
    }

    public void unsetSails()
    {
        if (!isNavigating())
        {
            return;
        }
        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        if (!isStandingStill())
        {
            clickSailButton();
            sleepUntil(() -> isStandingStill(), 2500);
        }
    }

    public void sailTo(WorldPoint target)
    {
        if (!isOnBoat())
        {
            var result = boardBoat();
            if (!result)
            {
                log.info("Failed to board boat.");
            }
            return;
        }
        if (!isNavigating())
        {
            var result = navigate();
            if (!result)
            {
                log.info("Failed to navigate boat.");
            }
            return;
        }

        var direction = getDirection(target);
        var heading = Heading.getHeading(direction);
        setHeading(heading);

        if (!isMovingForward())
        {
            setSails();
        }
    }

    public int getDirection(WorldPoint target)
    {
        WorldPoint current = getPlayerBoatLocation();
        int deltaX = target.getX() - current.getX();
        int deltaY = target.getY() - current.getY();

        if (deltaX == 0 && deltaY == 0)
        {
            return Heading.SOUTH.getValue();
        }

        double angleDegrees = Math.toDegrees(Math.atan2(deltaY, deltaX));
        double headingDegrees = (270.0 - angleDegrees + 360.0) % 360.0;
        return (int) ((headingDegrees + 11.25) / 22.5) & 0xF;
    }

    public Heading getCurrentHeading()
    {
        int orientation = getOrientation();
        int headingValue = ((orientation + 64) / 128) & 0xF;
        return Heading.getHeading(headingValue);
    }

    public void setHeading(Heading heading)
    {
        if (heading == getCurrentHeading())
        {
            return;
        }
        var menuEntry = new NewMenuEntry()
                .option("Set-Heading")
                .target("")
                .identifier(heading.getValue())
                .type(MenuAction.SET_HEADING)
                .param0(0)
                .param1(0)
                .forceLeftClick(false);
        var worldview = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldView());

        if (worldview == null)
        {
            menuEntry.setWorldViewId(Microbot.getClient().getTopLevelWorldView().getId());
        }
        else
        {
            menuEntry.setWorldViewId(worldview.getId());
        }
        Microbot.doInvoke(menuEntry, new java.awt.Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
    }

    public boolean trimSails()
    {
        if (!isOnBoat())
        {
            return false;
        }
        Microbot.getRs2TileObjectCache().query().fromWorldView().withIds(SAIL_IDS).interact("trim");
        return sleepUntil(() -> Microbot.isGainingExp, 5000);
    }

    public boolean openCargo()
    {
        if (!isOnBoat())
        {
            return false;
        }
        return Microbot.getRs2TileObjectCache().query().withIds(CARGO_HOLD_IDS).interact("open");
    }

    public Map<PortTaskVarbits, Integer> getPortTasksVarbits()
    {
        return Arrays.stream(PortTaskVarbits.values())
                .map(x -> Map.entry(x, Microbot.getVarbitValue(x.getId())))
                .filter(e -> e.getValue() > 0 && e.getKey().getType() == PortTaskVarbits.TaskType.ID)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public PortTaskData getPortTaskData(int varbitValue)
    {
        if (varbitValue <= 0)
        {
            return null;
        }

        return Arrays.stream(PortTaskData.values())
                .filter(x -> x.getId() == varbitValue)
                .findFirst()
                .orElse(null);
    }


}