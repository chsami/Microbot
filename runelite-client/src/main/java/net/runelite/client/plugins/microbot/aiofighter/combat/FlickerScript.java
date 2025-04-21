package net.runelite.client.plugins.microbot.aiofighter.combat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyle;
import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyleMapper;
import net.runelite.client.plugins.microbot.aiofighter.enums.PrayerStyle;
import net.runelite.client.plugins.microbot.aiofighter.model.Monster;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    int tickToFlick;
    Stream<Rs2NpcModel> npcs;

    /**
     * This method is responsible for running the flicker script.
     * It schedules a task to be run at a fixed delay.
     * @param config The configuration for the player assist.
     * @return true if the script is successfully started, false otherwise.
     */
    public boolean run(AIOFighterConfig config) {
        try {
            Rs2NpcManager.loadJson();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !config.togglePrayer()) return;
                if (config.prayerStyle() != PrayerStyle.LAZY_FLICK && config.prayerStyle() != PrayerStyle.PERFECT_LAZY_FLICK)
                    return;
                if (config.prayerStyle() == PrayerStyle.LAZY_FLICK) {
                    tickToFlick = 1;
                }
                if (config.prayerStyle() == PrayerStyle.PERFECT_LAZY_FLICK) {
                    tickToFlick = 0;
                }
                npcs = Rs2Npc.getNpcsForPlayer();
                usePrayer = config.togglePrayer();
                flickQuickPrayer = config.toggleQuickPray();
                currentTick = Microbot.getClient().getTickCount();
                // Keep track of which monsters still have aggro on the player
                currentMonstersAttackingUs.forEach(monster -> {
                    if (npcs.noneMatch(npc -> npc.getIndex() == monster.npc.getIndex())) {
                        monster.delete = true;
                    }
                });

                currentMonstersAttackingUs.removeIf(monster -> monster.delete);
                if (prayFlickAttackStyle != null) {
                    handlePrayerFlick();
                }
                // if currentMonstersAttackingUs is empty, disable all prayers
                if (currentMonstersAttackingUs.isEmpty() && !Rs2Player.isInteracting() && (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE) || Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC) || Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE) || Rs2Prayer.isQuickPrayerEnabled())){
                    Rs2Prayer.disableAllPrayers();
                }


            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * This method is responsible for handling the prayer flick.
     * It toggles the prayer based on the attack style.
     */
    private void handlePrayerFlick() {
        lastPrayerTick = currentTick;
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
                return;
        }

        prayFlickAttackStyle = null;
        Rs2Prayer.toggle(prayerToToggle, true);

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


                if (currentMonster.lastAttack == tickToFlick && !currentMonster.npc.isDead()) {

                    if(flickQuickPrayer){
                        prayFlickAttackStyle = AttackStyle.MIXED;
                    }
                    else
                        prayFlickAttackStyle = currentMonster.attackStyle;


                }
                resetLastAttack();

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
    public void resetLastAttack(boolean forceReset) {

        for (NPC npc : npcs.collect(Collectors.toList())) {
            Monster currentMonster = currentMonstersAttackingUs.stream().filter(x -> x.npc.getIndex() == npc.getIndex()).findFirst().orElse(null);
            AttackStyle attackStyle = AttackStyleMapper.mapToAttackStyle(Rs2NpcManager.getAttackStyle(npc.getId()));

            if (currentMonster != null) {
                if (forceReset) {
                    currentMonster.lastAttack = currentMonster.rs2NpcStats.getAttackSpeed();
                }
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

    // overload
    public void resetLastAttack() {
        resetLastAttack(false);
    }

}