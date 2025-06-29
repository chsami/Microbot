package net.runelite.client.plugins.microbot.aiofighter.combat;

import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * This class is responsible for handling the flicker script in the game.
 * It extends the Script class and overrides its methods to provide the functionality needed.
 */
@Slf4j
public class FlickerScript extends Script {
    public static final AtomicReference<List<Monster>> currentMonstersAttackingUs = new AtomicReference<>(new ArrayList<>());
    private final AtomicReference<List<Rs2NpcModel>> npcs = new AtomicReference<>(new ArrayList<>());

    AttackStyle prayFlickAttackStyle = null;
    boolean usePrayer = false;
    boolean flickQuickPrayer = false;

    int lastPrayerTick;
    int currentTick;
    int tickToFlick = 0;

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
                if (config.prayerStyle() != PrayerStyle.LAZY_FLICK && config.prayerStyle() != PrayerStyle.PERFECT_LAZY_FLICK) return;

                tickToFlick = config.prayerStyle() == PrayerStyle.PERFECT_LAZY_FLICK ? 0 : 1;

                npcs.set(Rs2Npc.getNpcsForPlayer().collect(Collectors.toList()));

                usePrayer = config.togglePrayer();
                flickQuickPrayer = config.toggleQuickPray();
                currentTick = Microbot.getClient().getTickCount();

                currentMonstersAttackingUs.updateAndGet(oldList -> {
                    List<Monster> updated = new ArrayList<>(oldList);

                    for (Monster m : updated) {
                        boolean stillThere = npcs.get().stream().anyMatch(npc -> npc.getIndex() == m.npc.getIndex());
                        if (!stillThere) {
                            m.delete = true;
                        }
                    }

                    updated.removeIf(m -> m.delete);
                    return updated;
                });

                List<Monster> snapshot = currentMonstersAttackingUs.get();

                if (prayFlickAttackStyle != null) {
                    handlePrayerFlick();
                }

                if (snapshot.isEmpty()
                        && !Rs2Player.isInteracting()
                        && (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE)
                        || Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)
                        || Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)
                        || Rs2Prayer.isQuickPrayerEnabled())) {
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

    @Override
    public void shutdown() {
        super.shutdown();
    }

    /**
     * This method is called on every game tick.
     * It handles the prayer flick for each monster attacking the player.
     */
    public void onGameTick() {
        if (!usePrayer) return;

        List<Monster> snapshot = currentMonstersAttackingUs.get();
        if (!snapshot.isEmpty()) {
            for (Monster m : snapshot) {
                m.lastAttack--;
                if (m.lastAttack == tickToFlick && !m.npc.isDead()) {
                    prayFlickAttackStyle = flickQuickPrayer ? AttackStyle.MIXED : m.attackStyle;
                }
            }
            resetLastAttack();
        }
    }

    /**
     * This method is called when an NPC despawns.
     * It removes the despawned NPC from the list of monsters attacking the player.
     * @param npcDespawned The despawned NPC.
     */
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        currentMonstersAttackingUs.updateAndGet(oldList -> {
            List<Monster> updated = new ArrayList<>(oldList);
            updated.removeIf(m -> m.npc.getIndex() == npcDespawned.getNpc().getIndex());
            return updated;
        });
    }

    /**
     * This method is responsible for resetting the last attack of each NPC.
     * It also handles the addition and removal of monsters from the list of monsters attacking the player.
     */
    public void resetLastAttack(boolean forceReset) {
        currentMonstersAttackingUs.updateAndGet(oldList -> {
            List<Monster> updated = new ArrayList<>(oldList);

            for (Rs2NpcModel npc : npcs.get()) {
                Monster existing = updated.stream()
                        .filter(m -> m.npc.getIndex() == npc.getIndex())
                        .findFirst()
                        .orElse(null);

                String style = Rs2NpcManager.getAttackStyle(npc.getId());
                if (style == null) continue;
                AttackStyle attackStyle = AttackStyleMapper.mapToAttackStyle(style);

                if (existing != null) {
                    if (forceReset && existing.lastAttack <= 0) {
                        existing.lastAttack = existing.rs2NpcStats.getAttackSpeed();
                    }
                    if ((!npc.isDead() && existing.lastAttack <= 0) || (!npc.isDead() && npc.getGraphic() != -1)) {
                        if (npc.getGraphic() != -1 && attackStyle != AttackStyle.MELEE) {
                            existing.lastAttack = existing.rs2NpcStats.getAttackSpeed() - 2 + tickToFlick;
                        } else {
                            existing.lastAttack = existing.rs2NpcStats.getAttackSpeed();
                        }
                    }

                    if (existing.lastAttack <= -existing.rs2NpcStats.getAttackSpeed() / 2) {
                        updated.remove(existing);
                    }

                } else {
                    if (!npc.isDead()) {
                        Monster toAdd = new Monster(npc, Objects.requireNonNull(Rs2NpcManager.getStats(npc.getId())));
                        toAdd.attackStyle = attackStyle;
                        updated.add(toAdd);
                    }
                }
            }

            return updated;
        });
    }

    // overload
    public void resetLastAttack() {
        resetLastAttack(false);
    }
}