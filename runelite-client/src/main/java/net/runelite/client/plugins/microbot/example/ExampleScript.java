package net.runelite.client.plugins.microbot.example;

import net.runelite.api.HeadIcon;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;

import java.util.concurrent.TimeUnit;


public class ExampleScript extends Script {

    public static boolean test = false;
    public boolean run(ExampleConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                //CODE HERE

                Rs2NpcModel npc = (Rs2NpcModel) Rs2Player.getInteracting();


                System.out.println(npc.getName());

                //     var currentTarget = Rs2Npc.getNpc("guard");

                //  var headIcon = Rs2Reflection.getHeadIcon(currentTarget);

                //  Actor actor = currentTarget;



                //      Microbot.getClientThread().runOnClientThread(() ->  { System.out.println(Microbot.getClient().getLocalPlayer().getInteracting().getName()); return null;});

                //System.out.println(Microbot.getClient().getLocalPlayer().getInteracting().getName());
//               System.out.println(Microbot.getClient().getLocalPlayer().getInteracting() == actor);

                //       System.out.println(Microbot.getClient().getLocalPlayer().getInteracting() == currentTarget);

                //     sleepUntil(() -> Microbot.getClient().getLocalPlayer().getInteracting() == currentTarget, 3000);

                // System.out.println(headIcon.name());

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private Rs2NpcModel findNewTarget() {
        return Rs2Npc.getAttackableNpcs("Tormented Demon")
                .filter(npc -> npc.getInteracting() == null || npc.getInteracting() == Microbot.getClient().getLocalPlayer())
                .filter(npc -> {
                    HeadIcon demonHeadIcon = Rs2Reflection.getHeadIcon(npc);
                    if (demonHeadIcon != null) {
                        //switchGear(config, demonHeadIcon);
                        return true;
                    }
                    //logOnceToChat("Null HeadIcon for NPC " + npc.getName());
                    return false;
                })
                .findFirst()
                .orElse(null);
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}