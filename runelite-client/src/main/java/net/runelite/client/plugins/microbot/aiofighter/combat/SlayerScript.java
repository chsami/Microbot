package net.runelite.client.plugins.microbot.aiofighter.combat;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.aiofighter.model.InventorySetupUtil;
import net.runelite.client.plugins.microbot.util.npc.MonsterLocation;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.slayer.Rs2Slayer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
@Slf4j
public class SlayerScript extends Script {

    static WorldPoint cachedMonsterLocation = null;
    static String cachedMonsterLocationName = null;
    AIOFighterConfig config;
    @SneakyThrows
    public boolean run(AIOFighterConfig config) {
        this.config = config;
        Microbot.enableAutoRunOn = false;
        Rs2NpcManager.loadJson();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.slayerMode()) return;


                handleSlayerTask();


            } catch (Exception ex) {
                log.error("Error: " + ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }


    // set attackableNpcs
    public void setAttackableNpcs() {
        List<String> npcNames = Rs2Slayer.getSlayerMonsters();
        // convert npcNames array to string, remove brackets
        assert npcNames != null;
        String npcNamesString = Arrays.toString(npcNames.toArray()).replace("[", "").replace("]", "");
         AIOFighterPlugin.setAttackableNpcs(npcNamesString);
    }

    // handle slayer task
    public void handleSlayerTask() {
         AIOFighterPlugin.setSlayerTask(Rs2Slayer.getSlayerTask());
         AIOFighterPlugin.setRemainingSlayerKills(Rs2Slayer.getSlayerTaskSize());
        if (Rs2Slayer.hasSlayerTask()) {
            setAttackableNpcs();
            if(Rs2Slayer.hasSlayerTaskWeakness()){
                 AIOFighterPlugin.setSlayerHasTaskWeakness(true);
                 AIOFighterPlugin.setSlayerTaskWeaknessItem(Rs2Slayer.getSlayerTaskWeaknessName());
                 AIOFighterPlugin.setSlayerTaskWeaknessThreshold(Rs2Slayer.getSlayerTaskWeaknessThreshold());
            }
            else {
                 AIOFighterPlugin.setSlayerHasTaskWeakness(false);
                 AIOFighterPlugin.setSlayerTaskWeaknessItem("");
                 AIOFighterPlugin.setSlayerTaskWeaknessThreshold(0);
            }
            if (cachedMonsterLocation == null) {
                MonsterLocation monsterLocation = Rs2Slayer.getSlayerTaskLocation(3, true);
                assert monsterLocation != null;
                WorldPoint slayerTaskLocation = monsterLocation.getBestClusterCenter();
                log.info("Monster location: " + slayerTaskLocation);
                InventorySetupUtil.config = config;
                InventorySetupUtil.determineInventorySetup(Rs2Slayer.slayerTaskMonsterTarget);

                cachedMonsterLocation = slayerTaskLocation;
                cachedMonsterLocationName = monsterLocation.getLocationName();
                 AIOFighterPlugin.setSlayerLocationName(cachedMonsterLocationName);
            }
            if (cachedMonsterLocation != null && config.centerLocation() != cachedMonsterLocation) {
                 AIOFighterPlugin.setCenter(cachedMonsterLocation);
            }

        }
        else {
            Microbot.log("No slayer task");
            reset();
            AIOFighterPlugin.setState(State.GETTING_TASK);
            if(Rs2Slayer.walkToSlayerMaster(config.slayerMaster())) {
                Rs2NpcModel npc = Rs2Npc.getNpc(config.slayerMaster().getName());
                if(npc != null) {
                    Rs2Npc.interact(npc, "Assignment");
                    sleepUntil(Rs2Slayer::hasSlayerTask, 5000);
                }
            }
        }
    }

    public static void reset() {
        cachedMonsterLocation = null;
        cachedMonsterLocationName = null;
         AIOFighterPlugin.setSlayerLocationName("None");
         AIOFighterPlugin.setSlayerTask("None");
         AIOFighterPlugin.setSlayerHasTaskWeakness(false);
         AIOFighterPlugin.setSlayerTaskWeaknessItem("");
         AIOFighterPlugin.setSlayerTaskWeaknessThreshold(0);
         AIOFighterPlugin.resetLocation();
         AIOFighterPlugin.setAttackableNpcs("");
         Rs2Slayer.blacklistedSlayerMonsters = AIOFighterPlugin.getBlacklistedSlayerNpcs();
    }


    @Override
    public void shutdown() {
        cachedMonsterLocation = null;
        super.shutdown();
    }
}
