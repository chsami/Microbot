package net.runelite.client.plugins.microbot.mining.fxminer;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.example.ExampleConfig;
import net.runelite.client.plugins.microbot.mining.fxminer.enums.State;
import net.runelite.client.plugins.microbot.mining.fxminer.enums.MiningLocation;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static net.runelite.api.ObjectID.CLAY_ROCKS;


public class FxminerScript  extends Script {
    public static State state = State.IDLE;
    public static GameObject oreRock;
    public static boolean lockedStatus = false;
    private static FxminerConfig config;
    private static MiningLocation miningLocation = MiningLocation.NULL;


    public boolean run(FxminerConfig config) {
        FxminerScript.config = config;
        initialize();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::executeTask, 0, 300, TimeUnit.MILLISECONDS);
        Microbot.log("Idiot 1");
        return true;
    }

    private void executeTask() {
        try {
            if (!super.run() || !Microbot.isLoggedIn()) {
                miningLocation = MiningLocation.NULL;
                oreRock = null;
                Microbot.log("Idiot 2");
                return;
            }

            if (Rs2AntibanSettings.actionCooldownActive) return;

            if (Rs2Player.isAnimating() || Microbot.getClient().getLocalPlayer().isInteracting()) return;

            handleInventory();
            handleDPickSpec();

            switch (state) {
                case IDLE:
                    return;
                case MINING:
                    handleMining();
                    break;
                case BANKING:
                    bankOre();
                    break;
            }
        } catch (Exception e) {
            Microbot.log("Error in script" + e.getMessage());
        }
    }

    private void handleDPickSpec() {
        if (Rs2Equipment.isWearing("dragon pickaxe")) {
            Rs2Combat.setSpecState(true, 1000);
        }
    }

    private void handleInventory() {
        if(lockedStatus){
            oreRock = null;
            miningLocation = MiningLocation.NULL;
            return;
        }
        if (!Rs2Inventory.isFull()) {
            state = State.MINING;
        } else {
            oreRock = null;
            miningLocation = MiningLocation.NULL;
            state = State.BANKING;
        }
    }


    private void bankOre() {
        if (Rs2Walker.walkTo(BankLocation.YANILLE.getWorldPoint()))
            bank();
    }

    private void bank() {
        TileObject bank = Rs2GameObject.findObjectById(10355);
        if (Rs2Bank.openBank(bank)) {
            sleepUntil(Rs2Bank::isOpen);

            Rs2Bank.depositAll();
        }
        sleep(100, 300);

    }

    private void handleMining() {
        if (oreRock != null && AntibanPlugin.isMining()) return;
        if (miningLocation == MiningLocation.NULL)
            miningLocation = MiningLocation.getRandomMiningLocation();
        else {
            if (walkToMiningLocation()) {
                if (Rs2Player.isMoving()) return;
                mineRock();
                //Rs2Antiban.takeMicroBreakByChance();
            }
        }

    }

    private boolean mineRock() {
        if (Rs2Player.isMoving()) return false;

        GameObject closestRock = findClosestRock();
        if(closestRock == null) {
            moveToMiningLocation();
            return false;
        }

        interactWithRock(closestRock);
        return true;
    }

    //find reachableobject ore
    private GameObject findClosestRock() {
        return Rs2GameObject.findReachableObject("Clay rocks", true, 5, Rs2Player.getWorldLocation());
    }

    private boolean isRock(GameObject gameObject) {
        int id = gameObject.getId();
        return id == 11362;
    }

    private int distanceToPlayer(GameObject gameObject) {
        WorldPoint closestWalkableNeighbour = Rs2Tile.getNearestWalkableTile(gameObject.getWorldLocation());
        if (closestWalkableNeighbour == null) return 999;
        return Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo2D(closestWalkableNeighbour);
    }

    private void interactWithRock(GameObject rock) {
        if( Rs2GameObject.interact(rock))
            oreRock = rock;
        Rs2Player.waitForXpDrop(Skill.MINING, true);
        //if (!AntibanPlugin.isMining()) {
        //    oreRock = null;
        //}
    }

    private boolean walkToMiningLocation() {
        WorldPoint miningWorldPoint = miningLocation.getWorldPoint();
        return Rs2Walker.walkTo(miningWorldPoint, 8);
    }

    private void moveToMiningLocation() {
        Rs2Walker.walkFastCanvas(miningLocation.getWorldPoint());
    }


    private void initialize() {
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        state = State.IDLE;
        miningLocation = MiningLocation.NULL;
        oreRock = null;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
        state = State.IDLE;
        miningLocation = MiningLocation.NULL;
        oreRock = null;
    }
}
