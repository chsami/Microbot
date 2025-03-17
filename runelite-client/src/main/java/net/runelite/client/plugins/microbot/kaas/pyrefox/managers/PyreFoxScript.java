package net.runelite.client.plugins.microbot.kaas.pyrefox.managers;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kaas.pyrefox.PyreFoxConfig;
import net.runelite.client.plugins.microbot.kaas.pyrefox.PyreFoxConstants;
import net.runelite.client.plugins.microbot.kaas.pyrefox.PyreFoxPlugin;
import net.runelite.client.plugins.microbot.kaas.pyrefox.enums.PyreFoxState;
import net.runelite.client.plugins.microbot.kaas.pyrefox.helpers.BankHelper;
import net.runelite.client.plugins.microbot.kaas.pyrefox.helpers.PlayerHelper;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;


public class PyreFoxScript extends Script
{
    private PyreFoxConfig _config;
    public static boolean test = false;
    private boolean _shouldReroll = true;

    public boolean run(PyreFoxConfig config)
    {
        this._config = config;
        Microbot.enableAutoRunOn = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                test = false;

                try {
                    _handleLoop();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
//                System.out.println("Total time for loop " + totalTime);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }


    /**
     * Our core loop.
     */
    private void _handleLoop()
    {
        switch (getCurrentState())
        {
            case INITIALIZE:
                _handleInitialize();
                break;
            case WALK_TO_BANK:
                _walkToBank();
                break;
            case BANKING:
                _handleBanking();
                break;
            case LOW_HITPOINTS:
                // will auto-eat.
                _handleBanking();
                break;
            case WALK_TO_PYREFOX:
                _walkToPyreFox();
                break;
            case CATCHING:
                _handleCatching();
                break;
            case CHOPPING_TREES:
                _handleGatheringLogs();
                break;
            case FINISHED:
                super.shutdown();
                break;
            default:
                System.out.println("Unknown state entered");
                break;
        }
    }

    /**
     * What should happen when we reach 25 hitpoints?
     */
    private void _handleGatheringLogs()
    {
        // Find nearby tree.
        // Deliberate while loop, I want the gamestate to not influence the chopping process.
        while (Rs2Inventory.count("Logs") < PyreFoxConstants.GATHER_LOGS_AMOUNT)
        {
            if (!isRunning())
                break;

            // Break the loop when we are below configured hitpoints.
            if (Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) <= _config.runToBankHP())
                break;

            Microbot.log("Looping chopping");
            var tree = Rs2GameObject.findReachableObject("Tree", true, 30, PyreFoxConstants.PYRE_FOX_CENTER_POINT, true, "Chop down");
            if (tree == null)
            {
                _log("No tree found, waiting.");
                return;
            }


            // Walk to it & chop.
            if (!Rs2Camera.isTileOnScreen(tree))
                Rs2Camera.turnTo(tree);

            Rs2GameObject.interact(tree, "Chop down");
            Rs2Player.waitForWalking();
            if (Rs2Player.waitForXpDrop(Skill.WOODCUTTING))
            {
                _log("Gathered "+ Rs2Inventory.count("Logs") +"/"+ PyreFoxConstants.GATHER_LOGS_AMOUNT +" logs gathered.");
            }

            sleep(100); // polling rate.
        }

        // Continues until refill amount.
        // End:  Set a new gather logs at amount.
    }

    /**
     * Walks and opens the hunter guild bank :-)
     */
    private void _walkToBank()
    {
        if (!BankHelper.walkToAndOpenBank(BankLocation.HUNTERS_GUILD))
            return;
    }


    /**
     * Walks and opens the hunter guild bank :-)
     */
    private void _walkToPyreFox()
    {
        int distance = Rs2Player.distanceTo(PyreFoxConstants.PYRE_FOX_CENTER_POINT);
        _log(distance+" distance");
        if (distance >= 6)
            Rs2Walker.walkTo(PyreFoxConstants.PYRE_FOX_CENTER_POINT, 5);
    }


    /**
     * Handles the killing part of our script.
     */
    private void _handleCatching()
    {
        // Check what GameObject ID we're dealing with.
        if (_getTrapObjectAtTrapLocation() == null)
        {
            Microbot.log("No Trap GameObject found.");
            return;
        }

        var trap = _getTrapObjectAtTrapLocation();
        if (trap == null)
        {

            Microbot.log("No trap found.");
            return;
        }

        if (!Rs2Camera.isTileOnScreen(trap))
            Rs2Camera.turnTo(trap);

        int trapId = _getTrapObjectAtTrapLocation().getId();

        // 1. Check if a trap is active.
        // 1a. Wait until fail / success
        // i. collect/reset & re-lay
        // 1b. Lay trap
        switch (trapId)
        {
            case PyreFoxConstants.GAMEOBJECT_ROCK_NO_TRAP:
                _log("No trap, setting one up!");
                _handleSettingUpTrap(trap);
                break;
            case PyreFoxConstants.GAMEOBJECT_ROCK_TRAP:
//                _log("Rock trap is set up, waiting.");
                break;
            case PyreFoxConstants.GAMEOBJECT_ROCK_FOX_CAUGHT:
                _log("We caught a fox!");
                _handleFoxCaught(trap);
                break;
            default:
                _handleFailedTrap(trap);
                break;
        }
    }

    private void _handleSettingUpTrap(GameObject trap)
    {
        Rs2GameObject.interact(trap, "Set-trap");
        Rs2Player.waitForWalking();
        Rs2Player.waitForAnimation();
    }

    private void _handleFailedTrap(GameObject trap)
    {
        if (!Rs2GameObject.interact(trap, "reset"))
        {
            _log("Did not find reset interaction.");
            return;
        }
        Rs2Player.waitForWalking();
        Rs2Player.waitForAnimation();
    }

    private void _handleFoxCaught(GameObject trap)
    {
        Rs2GameObject.interact(trap, "Check");
        Rs2Player.waitForWalking();
        Rs2Player.waitForXpDrop(Skill.HUNTER);
    }

    @Nullable
    private GameObject _getTrapObjectAtTrapLocation()
    {
        var object = Rs2GameObject.getGameObject(_getTrapObjectWorldPoint());
        if (object == null)
            return null;
        return object;
    }

    @Nullable
    private WorldPoint _getTrapObjectWorldPoint()
    {
        if (PyreFoxConstants.TRAP_OBJECT_POINT == null)
        {
            var rock = Rs2GameObject.findObjectByIdAndDistance(PyreFoxConstants.GAMEOBJECT_ROCK_NO_TRAP, 10);
            if (rock == null)
            {
                _log("No rock found");
                return null;
            }

            PyreFoxConstants.TRAP_OBJECT_POINT = rock.getWorldLocation();
        }
        return PyreFoxConstants.TRAP_OBJECT_POINT;
    }


    /**
     * In here we will check if all script requirements are met,
     * and set the booleans/data we'll use throughout the script.
     */
    private void _handleInitialize()
    {
        PyreFoxConstants.rerollGatherAmounts();

        if (_config.UseMeatPouch())
            if (Rs2Inventory.hasItem(_config.MeatPouch().getClosedItemID()))
                if(Rs2Inventory.interact(_config.MeatPouch().getClosedItemID(), "Open"))
                    Microbot.log("Opened meat bag.");

        if (!Rs2Inventory.hasItem(ItemID.KNIFE) || !PlayerHelper.playerHasAxeOnHim())
        {
            Microbot.log("You lack required items - shutting down!");
            shutdown();
        }

        PyreFoxPlugin.hasInitialized = true;
    }


    /**
     * Handles banking of our herbs.
     */
    private void _handleBanking()
    {
        if (!BankHelper.walkToAndOpenBank(BankLocation.HUNTERS_GUILD))
            return;


        // Empty herb sack.
        Rs2Inventory.interact(_config.MeatPouch().getOpenItemID(), "Empty");
        sleep(400, 600);

        // Deposit all non-locked inventory items.
        Rs2Bank.depositAll();
        sleep(50, 1200);

        // Auto-eat until full.
        if (_config.AutoEat())
        {
            while (!Rs2Player.isFullHealth())
            {
                if (!isRunning())
                    break;

                Rs2Bank.withdrawOne(_config.FoodToEatAtBank().getId());
                sleep(200, 400);
                Rs2Inventory.interact(_config.FoodToEatAtBank().getId(), "Eat");
                sleep(200, 400);
            }
        }

        if (Rs2Inventory.contains(_config.FoodToEatAtBank().getId()))
            Rs2Bank.depositAll(_config.FoodToEatAtBank().getId());

        // Close the bank.
        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            sleep(600, 900);
        }
    }

    /**
     * Decides what our core loop will do next.
     */
    public PyreFoxState getCurrentState()
    {
        return PyreFoxPlugin.currentState;
    }

    /**
     * Handles logging only when verbose is enabled.
     * @param message
     */
    private void _log(String message)
    {
        if (_config.EnableVerboseLogging())
            Microbot.log(message);
    }

    @Override
    public void shutdown() {
        Microbot.pauseAllScripts = true;
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}