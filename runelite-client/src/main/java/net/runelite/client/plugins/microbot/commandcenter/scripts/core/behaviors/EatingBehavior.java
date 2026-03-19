package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCBehavior;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Slf4j
public class EatingBehavior implements CCBehavior {

    private final int hpThresholdPercent;

    public EatingBehavior(int hpThresholdPercent) {
        this.hpThresholdPercent = hpThresholdPercent;
    }

    @Override
    public int priority() { return 10; }

    @Override
    public boolean shouldActivate() {
        return getHpPercent() < hpThresholdPercent && hasFood();
    }

    @Override
    public void execute() {
        if (Rs2Player.eatAt(hpThresholdPercent)) {
            log.debug("Ate food at {}% HP", getHpPercent());
        }
    }

    @Override
    public void reset() { /* stateless */ }

    @Override
    public String name() { return "Eating"; }

    // --- Overridable for tests ---

    protected int getHpPercent() {
        int max = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
        int current = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        return max > 0 ? (100 * current / max) : 100;
    }

    protected boolean hasFood() {
        return !Rs2Inventory.getInventoryFood().isEmpty();
    }
}
