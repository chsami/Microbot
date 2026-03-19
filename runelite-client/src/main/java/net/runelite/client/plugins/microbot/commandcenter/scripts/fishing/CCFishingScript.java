package net.runelite.client.plugins.microbot.commandcenter.scripts.fishing;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCScript;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors.BankingBehavior;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors.BankingConfig;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors.DeathRecoveryBehavior;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors.StuckDetectionBehavior;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;

@Slf4j
public class CCFishingScript extends CCScript<CCFishingScript.State> {

    public enum State { FISHING, IDLE }

    @Inject private CCFishingConfig config;

    @Override
    protected void configure() {
        if (config != null && config.bankFish()) {
            BankingConfig bankConfig = new BankingConfig(
                item -> item.getName().toLowerCase().startsWith("raw "),
                null, null
            );
            registerBehavior(new BankingBehavior(bankConfig, this::getActivityLocation));
        }
        registerBehavior(new DeathRecoveryBehavior(this::getActivityLocation));
        StuckDetectionBehavior stuck = new StuckDetectionBehavior();
        stuck.setScript(this);
        registerBehavior(stuck);

        setAntiBanTemplate(s -> Rs2Antiban.antibanSetupTemplates.applyFishingSetup());
    }

    @Override
    protected State getInitialState() { return State.FISHING; }

    @Override
    protected State onTick(State currentState) {
        switch (currentState) {
            case FISHING:
                if (isPlayerBusy()) return State.FISHING;
                if (config != null && !config.bankFish() && isInventoryFull()) {
                    dropAllFish();
                    return State.FISHING;
                }
                if (!hasRequiredTool()) {
                    log.warn("Missing required fishing tool — stopping");
                    return State.IDLE;
                }
                if (findAndFish()) return State.FISHING;
                return State.IDLE;

            case IDLE:
                if (findAndFish()) return State.FISHING;
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

    protected boolean hasRequiredTool() {
        return true; // Overridable — check for net/rod/cage/harpoon
    }

    protected boolean findAndFish() {
        if (config == null) return false;
        FishType fish = config.fishType();
        return Rs2Npc.interact(fish.getNpcName(), fish.getAction());
    }

    protected void dropAllFish() {
        if (config != null) {
            Rs2Inventory.dropAll(item ->
                item.getName().toLowerCase().startsWith("raw "));
        }
    }
}
