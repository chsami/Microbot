package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class SafeSpot extends Script {

    public WorldPoint currentSafeSpot = null;
    private boolean messageShown = false;

public boolean run(AIOFighterConfig config) {
    AtomicReference<List<String>> npcsToAttack = new AtomicReference<>(Arrays.stream(Arrays.stream(config.attackableNpcs().split(",")).map(String::trim).toArray(String[]::new)).collect(Collectors.toList()));
    mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
        try {
            if (AIOFighterPlugin.getState().equals(State.BANKING) || AIOFighterPlugin.getState().equals(State.WALKING)) return;
            if (!Microbot.isLoggedIn() || !super.run() || !config.toggleSafeSpot() || Rs2Player.isMoving()) return;

            currentSafeSpot = config.safeSpot();
            if(isDefaultSafeSpot(currentSafeSpot)){

                if(!messageShown){
                    Microbot.showMessage("Please set a safespot location");
                    messageShown = true;
                }
                return;
            }
            if (isDefaultSafeSpot(currentSafeSpot) || isPlayerAtSafeSpot(currentSafeSpot)) {
                //ckeck if there is an NPC targeting us
                var npcList = Rs2Npc.getNpcsForPlayer(npc -> true).collect(Collectors.toList());

                //if there is an NPC interacting with us, and we are not Interacting with it, attack it again
                if (!npcList.isEmpty() && !Rs2Player.isInMulti()) {
                    npcList.forEach(npc -> {
                        if (Rs2Player.isInteracting()) {
                            if (npcsToAttack.get().contains(npc.getName())) {
                                Rs2Npc.attack(npc);
                                AIOFighterPlugin.setCooldown(config.playStyle().getRandomTickInterval());
                            }
                        }
                    });
                    return;
                }

                return;
            }

            messageShown = false;

            Rs2Walker.walkFastCanvas(currentSafeSpot);
                Microbot.pauseAllScripts = true;
                sleepUntil(() -> isPlayerAtSafeSpot(currentSafeSpot));
                Microbot.pauseAllScripts = false;
                //ckeck if there is an NPC targeting us
                var npcList = Rs2Npc.getNpcsForPlayer(npc -> true).collect(Collectors.toList());

                //if there is an NPC interacting with us, and we are not Interacting with it, attack it again
                if (!npcList.isEmpty() && !Rs2Player.isInMulti()) {
                    npcList.forEach(npc -> {
                        if (Rs2Player.getInteracting() == null) {
                            if (npcsToAttack.get().contains(npc.getName())) {
                                Rs2Npc.attack(npc);
                                AIOFighterPlugin.setCooldown(config.playStyle().getRandomTickInterval());
                            }

                        }
                    });
                }




        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
    }, 0, 600, TimeUnit.MILLISECONDS);
    return true;
}

private boolean isDefaultSafeSpot(WorldPoint safeSpot) {
    return safeSpot.getX() == 0 && safeSpot.getY() == 0;
}

private boolean isPlayerAtSafeSpot(WorldPoint safeSpot) {
    return safeSpot.equals(Microbot.getClient().getLocalPlayer().getWorldLocation());
}

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
