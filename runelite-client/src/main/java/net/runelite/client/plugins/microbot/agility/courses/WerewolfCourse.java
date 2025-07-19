package net.runelite.client.plugins.microbot.agility.courses;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.agility.AgilityPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Operation;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.event.Level;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WerewolfCourse implements AgilityCourseHandler {
    private final static WorldPoint RESET_WORLD_POINT = new WorldPoint(3538, 9872, 0);
    private final static WorldPoint STICK_NPC_WORLD_POINT = new WorldPoint(3529, 9864, 0);

    @Override
    public WorldPoint getStartPoint() {
        return RESET_WORLD_POINT;
    }

    @Override
    public List<AgilityObstacleModel> getObstacles() {
        return List.of(
                new AgilityObstacleModel(ObjectID.WEREWOLF_STEPING_STONE, 3538, 9875, Operation.GREATER_EQUAL, Operation.LESS),
                new AgilityObstacleModel(ObjectID.WEREWOLF_STEPING_STONE, 3538, 9877, Operation.EQUAL, Operation.LESS),
                new AgilityObstacleModel(ObjectID.WEREWOLF_STEPING_STONE, 3540, 9877, Operation.LESS, Operation.EQUAL),
                new AgilityObstacleModel(ObjectID.WEREWOLF_STEPING_STONE, 3540, 9879, Operation.EQUAL, Operation.LESS),
                new AgilityObstacleModel(ObjectID.WEREWOLF_STEPING_STONE, 3540, 9881, Operation.EQUAL, Operation.LESS),
                new AgilityObstacleModel(ObjectID.WEREWOLF_HURDLE_MID, 3540, 9893, Operation.EQUAL, Operation.LESS),
                new AgilityObstacleModel(ObjectID.WEREWOLF_HURDLE_MID, 3540, 9896, Operation.EQUAL, Operation.LESS),
                new AgilityObstacleModel(ObjectID.WEREWOLF_HURDLE_MID, 3540, 9899, Operation.EQUAL, Operation.LESS),
                new AgilityObstacleModel(ObjectID.WAA_PIPE, 3540, 9905, Operation.EQUAL, Operation.LESS),
                new AgilityObstacleModel(ObjectID.WEREWOLF_SKULL_CLIMB_1, 3532, 9909, Operation.GREATER, Operation.GREATER),
                new AgilityObstacleModel(ObjectID.WEREWOLF_SLIDE_CENTER, 3528, 9909, Operation.GREATER_EQUAL, Operation.GREATER_EQUAL),
                new AgilityObstacleModel(ObjectID.WEREWOLF_SLIDE_CENTER, 3538, 9898, Operation.LESS, Operation.LESS) // Helper step when we fail slide
        );
    }

    @Override
    public Integer getRequiredLevel() {
        return 60;
    }

    AgilityObstacleModel matchingObstacle;
    TileObject matchingObject;

    @Override
    public TileObject getCurrentObstacle() {
        WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();

        List<AgilityObstacleModel> matchingObstacles = getObstacles().stream()
                .filter(o -> o.getOperationX().check(playerLocation.getX(), o.getRequiredX()) && o.getOperationY().check(playerLocation.getY(), o.getRequiredY()))
                .collect(Collectors.toList());
        // Store obstacle as reference for handle logic
        matchingObstacle = matchingObstacles.isEmpty() ? null : matchingObstacles.get(0);

        List<Integer> objectIds = matchingObstacles.stream()
                .map(AgilityObstacleModel::getObjectID)
                .collect(Collectors.toList());

        Predicate<TileObject> validObjectPredicate = obj -> {
            if (!objectIds.contains(obj.getId())) {
                return false;
            }
            if (obj.getPlane() != playerLocation.getPlane()) {
                return false;
            }

            if (obj instanceof GroundObject) {
                // Strict match with object required X & Y since we get multiple matches of objects
                if (matchingObstacle != null && (obj.getId() == ObjectID.WEREWOLF_STEPING_STONE || obj.getId() == ObjectID.WAA_PIPE)) {
                    var objLocation = obj.getWorldLocation();
                    return objLocation.getY() == matchingObstacle.getRequiredY() && objLocation.getX() == matchingObstacle.getRequiredX();
                }
                return Rs2GameObject.canReach(obj.getWorldLocation(), 2, 2);
            }

            if (obj instanceof GameObject) {
                GameObject _obj = (GameObject) obj;
                // Strict match with object required X & Y since we get multiple matches of objects
                if(_obj.getId() == ObjectID.WEREWOLF_HURDLE_MID)
                    return obj.getWorldLocation().getY() == matchingObstacle.getRequiredY() && obj.getWorldLocation().getX() == matchingObstacle.getRequiredX();
                else
                    return Rs2GameObject.canReach(_obj.getWorldLocation(), _obj.sizeX() + 2, _obj.sizeY() + 2, 4, 4);
            }
            return true;
        };

        // Store matching object as reference for handling logic
        matchingObject = Rs2GameObject.getAll(validObjectPredicate).stream().findFirst().orElse(null);
        return matchingObject;
    }

    public boolean handleFirstSteppingStone(WorldPoint playerWorldLocation) {
        if(matchingObject instanceof GroundObject && matchingObject.getId() == ObjectID.WEREWOLF_STEPING_STONE) {
            // Deals with login when we are spawned in by entrance, multiple matches of objects and no idea of order makes it so we need a special case the first one
            if(matchingObstacle.getRequiredX() == 3538 && matchingObstacle.getRequiredY() == 9875) {
                if(playerWorldLocation.distanceTo(RESET_WORLD_POINT) <= 5)
                    return false;
                // Try to walk in front of first stepping stone
                if(Rs2Walker.walkTo(RESET_WORLD_POINT, 5))
                    return true;
                else { // Login edge case where we end up in not defined walker area?
                    if(Rs2Walker.walkTo(RESET_WORLD_POINT, 5)) // Try one more time
                        return true;
                    var agilityBoss = Rs2Npc.getNpc(NpcID.WEREWOLF_TRAINER_START); // Try clicking on NPC to move to right area?
                    if(agilityBoss != null) {
                        Rs2Npc.interact(agilityBoss);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean handleStickPickup(WorldPoint playerWorldLocation) {
        if(matchingObject instanceof GameObject && matchingObject.getId() == ObjectID.WAA_PIPE && playerWorldLocation.getY() > matchingObstacle.getRequiredY()) {
            var stickTile = AgilityPlugin.getStickTile();
            if (stickTile != null) {
                var stickGroundItem = AgilityPlugin.getStickTile().getGroundItems().isEmpty() ? null : AgilityPlugin.getStickTile().getGroundItems().get(0);
                if (stickGroundItem != null && Rs2GroundItem.lootItemsBasedOnLocation(stickTile.getWorldLocation(), stickGroundItem.getId())) {
                    Rs2Inventory.waitForInventoryChanges(3000);
                    Rs2Random.wait(800, 200);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean handleSlide() {
        if(matchingObject instanceof GroundObject && matchingObject.getId() == ObjectID.WEREWOLF_SKULL_CLIMB_1) {
            if(Rs2Player.getHealthPercentage() < 20 && Rs2Inventory.getInventoryFood().isEmpty()) {
                Microbot.log("Using zipline may kill player at this point", Level.WARN);
            }
            if (Rs2Equipment.isWearing(EquipmentInventorySlot.HEAD)) {
                var item = Rs2Equipment.get(EquipmentInventorySlot.HEAD);
                if (item == null) return false;
                Rs2Equipment.unEquip(item.getId());
                Rs2Inventory.waitForInventoryChanges(1000);
                return true;
            }
        }
        return false;
    }

    public boolean handleStickReturn(WorldPoint playerWorldLocation) {
        if(matchingObstacle != null) {
            var obstacleCheck = matchingObstacle.getOperationY().check(playerWorldLocation.getY(), matchingObstacle.getRequiredY()) &&
                    matchingObstacle.getOperationX().check(playerWorldLocation.getX(), matchingObstacle.getRequiredX());
            var slideSuccess = matchingObject instanceof GameObject && matchingObject.getId() == ObjectID.WEREWOLF_SLIDE_CENTER;
            var slideFailed = matchingObject == null && matchingObstacle != null && matchingObstacle.getObjectID() == ObjectID.WEREWOLF_SLIDE_CENTER;
            if(obstacleCheck && (slideSuccess || slideFailed)) {
                returnStick(playerWorldLocation);
                Rs2Walker.walkTo(RESET_WORLD_POINT, 5);
                if(playerWorldLocation.getX() < RESET_WORLD_POINT.getX()) {
                    Rs2Walker.walkFastCanvas(RESET_WORLD_POINT);
                    Rs2Player.waitForWalking();
                }
                return true;
            }
        }
        return false;
    }

    private static void returnStick(WorldPoint playerWorldLocation) {
        if (Rs2Inventory.hasItem("Stick")) {
            var stickNpc = Rs2Npc.getNpc(NpcID.WEREWOLF_TRAINER_STICK);
            if(stickNpc == null) {
                Rs2Walker.walkTo(STICK_NPC_WORLD_POINT, 5);
                stickNpc = Rs2Npc.getNpc(NpcID.WEREWOLF_TRAINER_STICK);
            }
            if (stickNpc != null) {
                if (playerWorldLocation.distanceTo(stickNpc.getWorldLocation()) > 5)
                    Rs2Walker.walkTo(stickNpc.getWorldLocation(), 5);
                Rs2Npc.interact(stickNpc, "Give-Stick");
                Rs2Player.waitForWalking();
            } else {
                Microbot.log("Could not find stick NPC!", Level.WARN);
            }
        }
    }
}
