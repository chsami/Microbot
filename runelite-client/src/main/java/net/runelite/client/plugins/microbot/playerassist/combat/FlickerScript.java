package net.runelite.client.plugins.microbot.playerassist.combat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.playerassist.PlayerAssistConfig;
import net.runelite.client.plugins.microbot.playerassist.enums.AttackStyle;
import net.runelite.client.plugins.microbot.playerassist.enums.AttackStyleMapper;
import net.runelite.client.plugins.microbot.playerassist.model.Monster;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for handling the flicker script in the game.
 * It extends the Script class and overrides its methods to provide the functionality needed.
 */
@Slf4j
public class FlickerScript extends Script {
    public static List<Monster> currentMonstersAttackingUs = new ArrayList<>();
    AttackStyle prayFlickAttackStyle = null;
    boolean lazyFlick = false;
    boolean usePrayer = false;
    boolean flickQuickPrayer = false;

    int lastPrayerTick;
    int currentTick;
    List<NPC> npcs;

    /**
     * This method is responsible for running the flicker script.
     * It schedules a task to be run at a fixed delay.
     * @param config The configuration for the player assist.
     * @return true if the script is successfully started, false otherwise.
     */
    public boolean run(PlayerAssistConfig config) {
        Rs2NpcManager.loadJson();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !config.togglePrayer()) return;

                npcs = Rs2Npc.getNpcsForPlayer();
                usePrayer = config.togglePrayer();
                lazyFlick = config.toggleLazyFlick();
                flickQuickPrayer = config.toggleQuickPrayFlick();
                currentTick = Microbot.getClient().getTickCount();
                // Keep track of which monsters still have aggro on the player
                currentMonstersAttackingUs.forEach(monster -> {
                    if (npcs.stream().noneMatch(npc -> npc.getIndex() == monster.npc.getIndex())) {
                        monster.delete = true;
                    }
                });

                currentMonstersAttackingUs.removeIf(monster -> monster.delete);
                if (prayFlickAttackStyle != null) {
                    handlePrayerFlick();
                }


            } catch (Exception ex) {
                System.err.println("Error: " + ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * This method is responsible for handling the prayer flick.
     * It toggles the prayer based on the attack style.
     */
    private void handlePrayerFlick() {
        lastPrayerTick = currentTick;
        log.info("Ficked on tick: " + Microbot.getClient().getTickCount());
        Rs2PrayerEnum prayerToToggle;
        switch (prayFlickAttackStyle) {
            case MAGE:
                prayerToToggle = Rs2PrayerEnum.PROTECT_MAGIC;
                break;
            case MELEE:
                prayerToToggle = Rs2PrayerEnum.PROTECT_MELEE;
                break;
            case RANGED:
                prayerToToggle = Rs2PrayerEnum.PROTECT_RANGE;
                break;
            default:
                prayFlickAttackStyle = null;
                Rs2Prayer.toggleQuickPrayer(true);
                sleep(600);
                Rs2Prayer.toggleQuickPrayer(false);
                return;
        }

        prayFlickAttackStyle = null;
        Rs2Prayer.toggle(prayerToToggle, true);
        sleep(600);
        Rs2Prayer.toggle(prayerToToggle, false);
        log.info("Flick ended on tick: " + Microbot.getClient().getTickCount());
    }

    /**
     * This method is responsible for shutting down the script.
     */
    public void shutdown() {
        super.shutdown();
    }

    /**
     * This method is called on every game tick.
     * It handles the prayer flick for each monster attacking the player.
     */
    public void onGameTick() {
        if (!usePrayer) return;

        if (!currentMonstersAttackingUs.isEmpty()) {

            for (Monster currentMonster : currentMonstersAttackingUs) {
                currentMonster.lastAttack--;
                resetLastAttack();
                if(currentMonster.rs2NpcStats.getAttackSpeed() == 4 && currentMonster.lastAttack == 2)
                {

                    if(flickQuickPrayer){
                        prayFlickAttackStyle = AttackStyle.MIXED;
                    }
                    else
                        prayFlickAttackStyle = currentMonster.attackStyle;


                }
                if (currentMonster.rs2NpcStats.getAttackSpeed() > 4 && currentMonster.lastAttack == 1 && lazyFlick && !currentMonster.npc.isDead()) {

                    if(flickQuickPrayer){
                        prayFlickAttackStyle = AttackStyle.MIXED;
                    }
                    else
                        prayFlickAttackStyle = currentMonster.attackStyle;


                }


            }
        }
    }

    /**
     * This method is called when an NPC despawns.
     * It removes the despawned NPC from the list of monsters attacking the player.
     * @param npcDespawned The despawned NPC.
     */
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        Monster monster = currentMonstersAttackingUs.stream()
                .filter(x -> x.npc.getIndex() == npcDespawned.getNpc().getIndex())
                .findFirst().orElse(null);

        if (monster != null) {
            currentMonstersAttackingUs.remove(monster);
        }
    }

    /**
     * This method is responsible for resetting the last attack of each NPC.
     * It also handles the addition and removal of monsters from the list of monsters attacking the player.
     */
    public void resetLastAttack() {

        for (NPC npc : npcs) {
            Monster currentMonster = currentMonstersAttackingUs.stream().filter(x -> x.npc.getIndex() == npc.getIndex()).findFirst().orElse(null);
            AttackStyle attackStyle = AttackStyleMapper.mapToAttackStyle(Rs2NpcManager.getAttackStyle(npc.getId()));

            if (currentMonster != null) {
                if (!npc.isDead() && currentMonster.lastAttack <= 0)
                    currentMonster.lastAttack = currentMonster.rs2NpcStats.getAttackSpeed();
                if (currentMonster.lastAttack <= -currentMonster.rs2NpcStats.getAttackSpeed() / 2){
                    currentMonstersAttackingUs.remove(currentMonster);

                }
            } else {
                if (!npc.isDead()) {
                    Monster monsterToAdd = new Monster(npc, Objects.requireNonNull(Rs2NpcManager.getStats(npc.getId())));
                    monsterToAdd.attackStyle = attackStyle;
                    currentMonstersAttackingUs.add(monsterToAdd);

                }
            }
        }
    }

}