package net.runelite.client.plugins.microbot.commandcenter.scripts.mining;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCScript;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors.BankingBehavior;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors.BankingConfig;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors.DeathRecoveryBehavior;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors.StuckDetectionBehavior;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;

@Slf4j
public class CCMiningScript extends CCScript<CCMiningScript.State> {

    public enum State { MINING, IDLE }

    @Inject private CCMiningConfig config;

    @Override
    protected void configure() {
        if (config != null && config.bankOre()) {
            BankingConfig bankConfig = new BankingConfig(
                item -> item.getName().toLowerCase().contains("ore") || item.getName().equalsIgnoreCase("Coal"),
                null, null
            );
            registerBehavior(new BankingBehavior(bankConfig, this::getActivityLocation));
        }
        registerBehavior(new DeathRecoveryBehavior(this::getActivityLocation));
        StuckDetectionBehavior stuck = new StuckDetectionBehavior();
        stuck.setScript(this);
        registerBehavior(stuck);

        setAntiBanTemplate(s -> Rs2Antiban.antibanSetupTemplates.applyMiningSetup());
    }

    @Override
    protected State getInitialState() { return State.MINING; }

    @Override
    protected State onTick(State currentState) {
        switch (currentState) {
            case MINING:
                if (isPlayerBusy()) return State.MINING;
                if (config != null && !config.bankOre() && isInventoryFull()) {
                    dropAllOre();
                    return State.MINING;
                }
                if (findAndMineRock()) return State.MINING;
                return State.IDLE;

            case IDLE:
                if (findAndMineRock()) return State.MINING;
                return State.IDLE;

            default:
                return State.IDLE;
        }
    }

    protected boolean isPlayerBusy() {
        return Rs2Player.isAnimating() || Rs2Player.isMoving();
    }

    protected boolean isInventoryFull() {
        return Rs2Inventory.isFull();
    }

    protected boolean findAndMineRock() {
        if (config == null) return false;
        MiningRock rock = config.rock();
        return Rs2GameObject.interact(rock.getObjectName(), rock.getAction());
    }

    protected void dropAllOre() {
        if (config != null) {
            Rs2Inventory.dropAll(item ->
                item.getName().toLowerCase().contains("ore") || item.getName().equalsIgnoreCase("Coal"));
        }
    }
}
