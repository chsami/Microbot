package net.runelite.client.plugins.microbot.autoCannoner;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.VarPlayer;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.autoCannoner.States;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

import static net.runelite.api.VarPlayer.CANNON_AMMO;


public class AutoCannonerScript extends Script
{

    private States state;
    private boolean initialise;

    public static boolean test = false;
    public boolean run(AutoCannonerConfig config)
    {
        Microbot.enableAutoRunOn = false;

        initialise = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                long startTime = System.currentTimeMillis();

                if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Microbot.pauseAllScripts) return;
                if (Rs2Player.isInteracting()) return;

                if (initialise)
                {
                    Microbot.status = "Initialising...";
                    if (!Rs2Inventory.hasItem(ItemID.CANNONBALL))
                    {
                        Microbot.showMessage("No cannonballs in inventory! Shutting Down.");
                        shutdown();
                        return;
                    }

                    if (Rs2Inventory.hasItem(ItemID.CANNON_BASE))
                    {
                        Microbot.showMessage("Please place the cannon down first!");
                        shutdown();
                        return;
                    }
                    state = States.IDLE;
                }

                switch(state)
                {
                    case IDLE:
                        initialise = false;
                        Microbot.status = "Idle";
                        GameObject brokenCannon = Rs2GameObject.get("Broken multicannon");
                        if (brokenCannon != null)
                        {
                            state = States.FIXING_CANNON;
                            break;
                        }
                        if (Microbot.getClient().getVarpValue(CANNON_AMMO) < Rs2Random.between(config.minBalls(), config.maxBalls()))
                        {
                            state = States.RELOADING_CANNON;
                            break;
                        }

                        break;

                    case FIXING_CANNON:
                        initialise = false;
                        Microbot.status = "Fixing Cannon";
                        Rs2GameObject.interact(14916, "Repair");
                        Rs2Random.wait(3600, 4200);
                        state = States.RELOADING_CANNON;
                        break;

                    case RELOADING_CANNON:
                        initialise = false;
                        Microbot.status = "Reloading Cannon";
                        if (!Rs2Inventory.hasItem(ItemID.CANNONBALL))
                        {
                            Microbot.showMessage("No Cannonballs! Shutting down.");
                            shutdown();
                            return;
                        }
                        Rs2GameObject.interact(6, "Fire");
                        Rs2Random.wait(1800, 2400);
                        state = States.IDLE;
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            }
            catch (Exception ex)
            {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
    }
}