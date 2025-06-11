package net.runelite.client.plugins.microbot.farmTreeRun.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum HardTreeEnums {
    TEAK("Teak sapling", ItemID.TEAK_SAPLING, ItemID.LIMPWURT_ROOT, 15,75),
    MAHOGANY("Mahogany sapling", ItemID.MAHOGANY_SAPLING, ItemID.LIMPWURT_ROOT, 15,75);


    private final String name;
    private final int saplingId;
    private final int paymentId;
    private final int paymentAmount;
    private final int farmingLevel;

    @Override
    public String toString() {
        return name;
    }

    public boolean hasRequiredLevel() {
        return Rs2Player.getSkillRequirement(Skill.FARMING, this.farmingLevel);
    }
}