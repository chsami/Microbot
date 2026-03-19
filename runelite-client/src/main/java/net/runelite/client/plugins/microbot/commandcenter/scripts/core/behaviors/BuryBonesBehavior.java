package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCBehavior;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Slf4j
public class BuryBonesBehavior implements CCBehavior {

    @Override public int priority() { return 45; }
    @Override public String name() { return "BuryBones"; }

    @Override
    public boolean shouldActivate() {
        return hasBones() && !isInCombat();
    }

    @Override
    public void execute() {
        if (Rs2Inventory.interact("Bones", "Bury")) {
            log.debug("Buried bones");
        }
    }

    @Override
    public void reset() { /* stateless */ }

    protected boolean hasBones() {
        return Rs2Inventory.contains("Bones");
    }

    protected boolean isInCombat() {
        return Rs2Player.isInCombat();
    }
}
