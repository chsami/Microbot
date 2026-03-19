package net.runelite.client.plugins.microbot.commandcenter.scripts.combat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCScript;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors.*;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class CCCombatScript extends CCScript<CCCombatScript.State> {

    public enum State { FIGHTING, IDLE }

    @Inject private CCCombatConfig config;

    @Override
    protected void configure() {
        if (config != null) {
            registerBehavior(new EatingBehavior(config.eatPercent()));

            String lootCsv = config.lootItems();
            if (lootCsv != null && !lootCsv.trim().isEmpty()) {
                List<String> items = Arrays.stream(lootCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
                if (!items.isEmpty()) {
                    registerBehavior(new LootingBehavior(items, config.lootRadius()));
                }
            }

            if (config.buryBones()) {
                registerBehavior(new BuryBonesBehavior());
            }
        }

        registerBehavior(new DeathRecoveryBehavior(this::getActivityLocation));
        StuckDetectionBehavior stuck = new StuckDetectionBehavior();
        stuck.setScript(this);
        registerBehavior(stuck);

        setAntiBanTemplate(s -> Rs2Antiban.antibanSetupTemplates.applyCombatSetup());
    }

    @Override
    protected State getInitialState() { return State.IDLE; }

    @Override
    protected State onTick(State currentState) {
        switch (currentState) {
            case FIGHTING:
                if (isInCombat()) return State.FIGHTING;
                return State.IDLE; // target died or lost

            case IDLE:
                if (config != null && config.progression()) {
                    checkProgression();
                }
                if (findAndAttackMonster()) return State.FIGHTING;
                return State.IDLE;

            default:
                return State.IDLE;
        }
    }

    // --- Overridable for tests ---

    protected boolean isInCombat() {
        return Rs2Player.isInCombat();
    }

    protected boolean findAndAttackMonster() {
        if (config == null) return false;
        return Rs2Npc.interact(config.monsterName(), "Attack");
    }

    protected void checkProgression() {
        int combatLevel = Rs2Combat.getCombatLevel();
        if (combatLevel >= 40) {
            log.info("[Progression] Combat level {} — consider upgrading to Flesh Crawlers or Hill Giants", combatLevel);
        } else if (combatLevel >= 20) {
            log.info("[Progression] Combat level {} — consider upgrading to Cows or Al-Kharid Warriors", combatLevel);
        }
    }

    protected boolean isProgressionEnabled() {
        return config != null && config.progression();
    }
}
