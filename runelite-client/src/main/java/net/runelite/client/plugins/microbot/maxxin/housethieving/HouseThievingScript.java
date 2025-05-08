package net.runelite.client.plugins.microbot.maxxin.housethieving;

import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.TileObject;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.event.Level;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.maxxin.housethieving.HouseThievingConstants.*;

public class HouseThievingScript extends Script {
    public static String version = "0.0.1";

    private final HouseThievingPlugin plugin;

    public HouseThievingScript(final HouseThievingPlugin plugin) {
        this.plugin = plugin;
    }

    enum State {
        PICKPOCKETING,
        FINDING_HOUSE,
        THIEVING_HOUSES,
        BANKING
    }
    private State state = State.PICKPOCKETING;
    private TileObject currentThievingObject = null;
    private Long lastThievingSearch = null;
    private ThievingHouse currentThievingHouse = null;

    public boolean run(HouseThievingConfig config) {
        Microbot.pauseAllScripts = false;
        Microbot.enableAutoRunOn = false;
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Microbot.pauseAllScripts) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                var childrenOfTheSunComplete = Rs2Player.getQuestState(Quest.CHILDREN_OF_THE_SUN) == QuestState.FINISHED;
                if(!childrenOfTheSunComplete) {
                    Microbot.showMessage("Children of the Sun quest is required to be complete to use this plugin.");
                    shutdown();
                    return;
                }

                if( state == null )
                    state = State.PICKPOCKETING;

                if( currentThievingHouse == null )
                    currentThievingHouse = getThievingHouse();

                var houseNpc = Rs2Npc.getNpc(currentThievingHouse.npcName);
                switch (state) {
                    case PICKPOCKETING:
                        handlePickPocketing(config);
                        break;
                    case FINDING_HOUSE:
                        handleFindingHouse(config);
                        break;
                    case THIEVING_HOUSES:
                        handleThievingHouse(houseNpc);
                        break;
                    case BANKING:
                        handleBanking();
                        break;
                }

            } catch (Exception ex) {
                Microbot.log("Error: " + ex.getMessage(), Level.ERROR);
                ex.printStackTrace();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handlePickPocketing(HouseThievingConfig config) {
        if( Rs2Inventory.getEmptySlots() < 5 ) {
            state = State.BANKING;
            return;
        }

        if(Rs2Inventory.hasItem("House keys")) {
            var houseKeys = Rs2Inventory.get("House keys");
            if( houseKeys.getQuantity() >= config.maxHouseKeys() ) {
                Microbot.log("Have enough house keys, thieving houses", Level.INFO);
                state = State.FINDING_HOUSE;
                return;
            }
        }

        if( Rs2Player.getWorldLocation().distanceTo( PICKPOCKET_LOCATION ) > 3 ) {
            Rs2Walker.walkTo(PICKPOCKET_LOCATION, 2);
        }

        if( Rs2Inventory.hasItem("Coin pouch") ) {
            var coinPouches = Rs2Inventory.get("Coin pouch");
            if( coinPouches.getQuantity() > 27 ) {
                Rs2Inventory.interact(coinPouches, "Open-all");
                Rs2Random.waitEx(800.0, 200.0);
            }
        }

        var aureliaNpc = Rs2Npc.getNpc("Aurelia");
        Rs2NpcModel wealthyCitizen = null;
        if( aureliaNpc != null ) {
            var aureliaAnim = aureliaNpc.getAnimation();
            if( aureliaAnim == 866 || aureliaAnim == 860 ) {
                var aureliaNpcs = Rs2Npc.getNpcs().filter(npc -> npc.getWorldLocation().distanceTo(aureliaNpc.getWorldLocation()) <= 1).collect(Collectors.toList());
                for(var npc : aureliaNpcs) {
                    var npcName = npc.getName();
                    if( npcName != null && npcName.equalsIgnoreCase("Wealthy citizen") && npc.isInteracting() ) {
                        Microbot.log("Found Wealthy citizen: " + npc.getWorldLocation(), Level.INFO);
                        wealthyCitizen = npc;
                    }
                }
            } else {
                Microbot.log(String.format("Waiting %s seconds for Aurelia pickpocket", config.pickpocketWaitTime()), Level.INFO);
                var waitForPickpocket = sleepUntil(() -> aureliaNpc.getAnimation() == 866 || aureliaNpc.getAnimation() == 860, config.pickpocketWaitTime() * 1000);
                Microbot.log(String.format("Finished waiting %s seconds for Aurelia pickpocket", config.pickpocketWaitTime()), Level.INFO);
                if( !waitForPickpocket ) {
                    Microbot.log("World hopping to check for pickpocket", Level.INFO);
                    boolean hoppedWorlds = Microbot.hopToWorld(Login.getRandomWorld(Rs2Player.isMember()));
                    if( hoppedWorlds ) {
                        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
                        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
                    }
                    return;
                }
            }
        }

        if( wealthyCitizen != null && !Rs2Player.isInteracting() ) {
            Rs2Npc.interact(wealthyCitizen, "Pickpocket");
            Rs2Random.waitEx(1000.0, 200.0);
        }
    }

    private void handleFindingHouse(HouseThievingConfig config) {
        if( Rs2Inventory.getEmptySlots() < 5 ) {
            state = State.BANKING;
            return;
        }

        if( Rs2Inventory.hasItem("House keys") ) {
            var houseKeys = Rs2Inventory.get("House keys");
            if( houseKeys != null && houseKeys.getQuantity() < config.minHouseKeys() ) {
                state = State.PICKPOCKETING;
                return;
            }
        }

        var lockedDoorEntrance = currentThievingHouse.lockedDoorEntrance;
        if( Rs2Player.getWorldLocation().distanceTo(lockedDoorEntrance) > 0 ) {
            Rs2Walker.walkTo(lockedDoorEntrance);
            Rs2Random.waitEx(800.0, 200.0);
        }
        var lockedDoorEgress = currentThievingHouse.lockedDoorEgress;
        var lockedDoorTile = Rs2Tile.getTile(
                currentThievingHouse.lockedDoorEgress.getX(),
                currentThievingHouse.lockedDoorEgress.getY());
        if( lockedDoorTile != null ) {
            var wallObject = lockedDoorTile.getWallObject();
            if( wallObject != null && wallObject.getId() == LOCKED_DOOR_ID ) {
                Microbot.log("Current house can be thieved", Level.INFO);
                Rs2GameObject.interact(wallObject);
                Rs2Random.waitEx(3000.0, 100.0);
                if( Rs2Player.getWorldLocation().distanceTo(lockedDoorEgress) < 1 ) {
                    currentThievingObject = null;
                    lastThievingSearch = null;
                    state = State.THIEVING_HOUSES;
                }
            } else {
                Microbot.log("Checking different house", Level.INFO);
                setNextThievingHouse();
            }
        }
    }

    private void handleThievingHouse(Rs2NpcModel houseNpc) {
        if( Rs2Inventory.getEmptySlots() < 5 ) {
            state = State.BANKING;
            return;
        }

        var windowEgress = currentThievingHouse.windowEgress;
        var houseCenter = currentThievingHouse.houseCenter;
        if( houseNpc != null ) {
            var laviniaWorldLoc = houseNpc.getWorldLocation();
            if( laviniaWorldLoc.distanceTo(houseCenter) < 9 ) {
                Microbot.log("Owner in house or approaching currently", Level.INFO);
                var windowTile = Rs2Tile.getTile(currentThievingHouse.windowEntrance.getX(), currentThievingHouse.windowEntrance.getY());
                if( windowTile != null ) {
                    var windowTileWallObject = windowTile.getWallObject();
                    if( windowTileWallObject != null && !Rs2Player.isMoving() ) {
                        Rs2GameObject.interact(windowTileWallObject, "Exit-window");
                        Rs2Random.waitEx(5000.0, 200.0);
                        if( Rs2Player.getWorldLocation().distanceTo(windowEgress) < 1 ) {
                            currentThievingObject = null;
                            lastThievingSearch = null;
                            setNextThievingHouse();
                            state = State.FINDING_HOUSE;
                            return;
                        }
                    }
                }
            }
        }

        var hintArrow = Microbot.getClient().getHintArrowPoint();
        var elapsedTime = lastThievingSearch == null ? null : System.currentTimeMillis() - lastThievingSearch;
        if( hintArrow == null ) {
            if( currentThievingObject == null )
                currentThievingObject = Rs2GameObject.findGameObjectByLocation(currentThievingHouse.initialThievingChest);

            if( currentThievingObject != null && ( lastThievingSearch == null || (elapsedTime != null && elapsedTime > 50000) ) ) {
                Rs2GameObject.interact(currentThievingObject, "Search");
                lastThievingSearch = System.currentTimeMillis();
                Rs2Random.waitEx(1000.0, 200.0);
            }
        } else {
            currentThievingObject = Rs2GameObject.findGameObjectByLocation(hintArrow);
            if( !Rs2Player.isInteracting() || lastThievingSearch == null || (elapsedTime != null && elapsedTime > 50000) ) {
                Rs2GameObject.interact(currentThievingObject, "Search");
                lastThievingSearch = System.currentTimeMillis();
                Rs2Random.waitEx(1000.0, 200.0);
            }
        }
    }

    private void handleBanking() {
        if( Rs2Inventory.getEmptySlots() > 6 ) {
            state = State.PICKPOCKETING;
            return;
        }

        if( Rs2Player.getWorldLocation().distanceTo( BANKING_LOCATION ) > 3 ) {
            Rs2Walker.walkTo(BANKING_LOCATION, 2);
        }

        if( Rs2Player.distanceTo( BANKING_LOCATION ) <= 3 ) {
            var bankingTile = Rs2GameObject.getGameObject(BANKING_TILE_LOCATION);
            if( bankingTile != null )
                Rs2Bank.openBank(bankingTile);
            else
                Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 2);
        }

        if( Rs2Bank.isOpen() ) {
            Rs2Bank.depositAllExcept("Coins", "Coin pouch", "House keys");
            Rs2Inventory.waitForInventoryChanges(600);
        }
    }

    private void setNextThievingHouse() {
        if( currentThievingHouse == ThievingHouse.LAVINIA ) {
            currentThievingHouse = ThievingHouse.VICTOR;
            if( hasLockedDoor(ThievingHouse.CAIUS) )
                currentThievingHouse = ThievingHouse.CAIUS;
        }
        else if( currentThievingHouse == ThievingHouse.VICTOR ) {
            currentThievingHouse = ThievingHouse.CAIUS;
            if( hasLockedDoor(ThievingHouse.LAVINIA) )
                currentThievingHouse = ThievingHouse.LAVINIA;
        }
        else if( currentThievingHouse == ThievingHouse.CAIUS ) {
            currentThievingHouse = ThievingHouse.LAVINIA;
            if( hasLockedDoor(ThievingHouse.VICTOR) )
                currentThievingHouse = ThievingHouse.VICTOR;
        }
    }

    private static ThievingHouse getThievingHouse() {
        ThievingHouse closestHouse = null;
        for( var house : ThievingHouse.values() ) {
            if( hasLockedDoor( house ) )
                return house;
            if( closestHouse == null )
                closestHouse = house;
            var houseDistance = Rs2Player.getWorldLocation().distanceTo(house.lockedDoorEntrance);
            var shortestHouseDistance = Rs2Player.getWorldLocation().distanceTo(closestHouse.lockedDoorEntrance);
            if( houseDistance < shortestHouseDistance ) {
                closestHouse = house;
            }
        }
        return closestHouse;
    }

    private static boolean hasLockedDoor(ThievingHouse thievingHouse) {
        var lockedDoorTile = Rs2Tile.getTile(
                thievingHouse.lockedDoorEgress.getX(),
                thievingHouse.lockedDoorEgress.getY());
        if( lockedDoorTile == null ) return false;
        var wallObject = lockedDoorTile.getWallObject();
        return wallObject != null && wallObject.getId() == LOCKED_DOOR_ID;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

}
