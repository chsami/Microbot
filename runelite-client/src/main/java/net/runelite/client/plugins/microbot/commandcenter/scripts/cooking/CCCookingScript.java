package net.runelite.client.plugins.microbot.commandcenter.scripts.cooking;

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
import java.util.List;

@Slf4j
public class CCCookingScript extends CCScript<CCCookingScript.State> {

    public enum State { COOKING, IDLE }

    @Inject private CCCookingConfig config;

    @Override
    protected void configure() {
        if (config != null) {
            String rawName = config.food().getRawName();
            BankingConfig bankConfig = new BankingConfig(
                item -> !item.getName().equalsIgnoreCase(rawName),
                List.of(rawName), null
            );
            registerBehavior(new BankingBehavior(bankConfig, this::getActivityLocation));
        }
        registerBehavior(new DeathRecoveryBehavior(this::getActivityLocation));
        StuckDetectionBehavior stuck = new StuckDetectionBehavior();
        stuck.setScript(this);
        registerBehavior(stuck);

        setAntiBanTemplate(s -> Rs2Antiban.antibanSetupTemplates.applyCookingSetup());
    }

    @Override
    protected State getInitialState() { return State.COOKING; }

    @Override
    protected State onTick(State currentState) {
        switch (currentState) {
            case COOKING:
                if (isPlayerBusy()) return State.COOKING;
                if (!hasRawFood()) return State.IDLE;
                if (findAndCook()) return State.COOKING;
                return State.IDLE;

            case IDLE:
                if (hasRawFood()) return State.COOKING;
                return State.IDLE;

            default:
                return State.IDLE;
        }
    }

    protected boolean isPlayerBusy() {
        return Rs2Player.isAnimating() || Rs2Player.isMoving();
    }

    protected boolean hasRawFood() {
        if (config == null) return false;
        return Rs2Inventory.contains(config.food().getRawName());
    }

    protected boolean findAndCook() {
        if (config == null) return false;
        // Use raw food on a range
        return Rs2GameObject.interact("Range", "Cook") || Rs2GameObject.interact("Fire", "Cook");
    }
}
