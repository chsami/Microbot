package net.runelite.client.plugins.microbot.chompy;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ChompyScript extends Script {
    public static double version = 1.0;

    // NPCs
    public static int ID_SWAMP_TOAD=1473;
    public static int ID_BLOATED_TOAD_GROUND=1474;
    public static int ID_CHOMPY = 1475;
    public static int ID_DEAD_CHOMPY = 1476; // "Pluck"


    // Game Objects
    public static int ID_BUBBLES=684;

    // Items
    public static int ID_BELLOWS0=2871;
    public static int ID_BELLOWS3=2872;
    public static int ID_BELLOWS2=2873;
    public static int ID_BELLOWS1=2874;
    public static int ID_BLOATED_TOAD_ITEM=2875;
    public static int ID_RAW_CHOMPY=2876;

    public static int chompy_kills = 0;
    public static long start_time = 0;
    public ChompyState state = ChompyState.FILLING_BELLOWS;

    private boolean bloated_toad_on_ground() {
        Stream<Rs2NpcModel> npcs=Rs2Npc.getNpcs();
        long num_toads = npcs.filter(element -> element.getWorldLocation().equals(Rs2Player.getWorldLocation()) && element.getId() == ID_BLOATED_TOAD_GROUND).count();

        return num_toads>0;
    }

    public boolean run(ChompyConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
                    return;
                }

                if (!Rs2Equipment.hasEquippedSlot(EquipmentInventorySlot.AMMO)) {
                    Microbot.showMessage("No ammo - stopping");
                    sleep(10000);
                    state = ChompyState.STOPPED;
                }

                System.out.println(state);
                switch (state)
                {
                    case FILLING_BELLOWS:
                        Rs2GameObject.interact(ID_BUBBLES,"Suck");
                        sleepUntil(() -> Microbot.getClient().getLocalPlayer().isInteracting());
                        state=ChompyState.INFLATING;
                        break;

                    case INFLATING:
                        if (Rs2Npc.interact(ID_CHOMPY,"Attack")) {
                            // First Priority: attack Chompy
                            break;
                        }
                        else if (Rs2Inventory.hasItem(ID_BLOATED_TOAD_ITEM) && !bloated_toad_on_ground())
                        {
                            // Second Priority: drop bloated toads (we can have at most 3 and I don't want to handle that case)
                            System.out.println("Dropping bloated toad");
                            Rs2Inventory.drop(ID_BLOATED_TOAD_ITEM);

                        }
                        else {
                            if (!(Rs2Inventory.hasItem(ID_BELLOWS1) || Rs2Inventory.hasItem(ID_BELLOWS2) || Rs2Inventory.hasItem(ID_BELLOWS3))) {
                                if (Rs2Inventory.hasItem(ID_BELLOWS0)) {
                                    state = ChompyState.FILLING_BELLOWS;
                                } else {
                                    Microbot.showMessage("You need bellows! Aborting");
                                    sleep(10000);
                                    state = ChompyState.STOPPED;
                                }
                            }
                            else if (!Rs2Npc.interact(ID_SWAMP_TOAD, "Inflate")) {
                                Microbot.showMessage("Could not find toads - aborting");
                                sleep(10000);
                                state = ChompyState.STOPPED;
                            }
                        }
                        break;
                    case STOPPED:
                        return;
                }


                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    public void startup() {
        chompy_kills=0;
        start_time=System.currentTimeMillis();
    }

    public void chompy_notch() {
        chompy_kills+=1;
    }

    public void not_my_chompy() {
        state=ChompyState.STOPPED;
        Microbot.showMessage("Someone else is hunting Chompys in this world - aborting");
    }

    public void cant_reach() {
        // There are unreachable swamp bubbles, just try another
        List<GameObject> bubbles=Rs2GameObject.getGameObjects(ID_BUBBLES);
        Random rand = new Random();
        GameObject bubble=bubbles.get(rand.nextInt(bubbles.size()));

        Rs2GameObject.interact(bubble,"Suck");
        sleepUntil(() -> Microbot.getClient().getLocalPlayer().isInteracting());
        state=ChompyState.INFLATING;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
