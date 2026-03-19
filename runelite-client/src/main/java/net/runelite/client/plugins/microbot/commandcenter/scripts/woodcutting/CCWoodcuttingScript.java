package net.runelite.client.plugins.microbot.commandcenter.scripts.woodcutting;

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
public class CCWoodcuttingScript extends CCScript<CCWoodcuttingScript.State> {

    public enum State { CHOPPING, IDLE }

    @Inject private CCWoodcuttingConfig config;

    @Override
    protected void configure() {
        if (config != null && config.bankLogs()) {
            BankingConfig bankConfig = new BankingConfig(
                item -> item.getName().toLowerCase().contains("logs"),
                null, null
            );
            registerBehavior(new BankingBehavior(bankConfig, this::getActivityLocation));
        }
        registerBehavior(new DeathRecoveryBehavior(this::getActivityLocation));
        StuckDetectionBehavior stuck = new StuckDetectionBehavior();
        stuck.setScript(this);
        registerBehavior(stuck);

        setAntiBanTemplate(s -> Rs2Antiban.antibanSetupTemplates.applyWoodcuttingSetup());
    }

    @Override
    protected State getInitialState() { return State.CHOPPING; }

    @Override
    protected State onTick(State currentState) {
        switch (currentState) {
            case CHOPPING:
                if (isPlayerBusy()) return State.CHOPPING;
                if (config != null && !config.bankLogs() && isInventoryFull()) {
                    dropAllLogs();
                    return State.CHOPPING;
                }
                if (findAndChopTree()) return State.CHOPPING;
                return State.IDLE;

            case IDLE:
                if (findAndChopTree()) return State.CHOPPING;
                return State.IDLE;

            default:
                return State.IDLE;
        }
    }

    // --- Overridable for tests ---

    protected boolean isPlayerBusy() {
        return Rs2Player.isAnimating() || Rs2Player.isMoving();
    }

    protected boolean isInventoryFull() {
        return Rs2Inventory.isFull();
    }

    protected boolean findAndChopTree() {
        if (config == null) return false;
        WoodcuttingTree tree = config.tree();
        return Rs2GameObject.interact(tree.getObjectName(), tree.getAction());
    }

    protected void dropAllLogs() {
        if (config != null) {
            Rs2Inventory.dropAll(item ->
                item.getName().toLowerCase().contains("logs"));
        }
    }
}
