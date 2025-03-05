package net.runelite.client.plugins.microbot.woodcutting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum WoodcuttingTree {
    TREE("tree" , "Logs", ItemID.LOGS, 1, "Chop down"),
    OAK("oak tree", "Oak logs", ItemID.OAK_LOGS,15, "Chop down"),
    WILLOW("willow tree", "Willow logs", ItemID.WILLOW_LOGS, 30, "Chop down"),
    TEAK_TREE("teak tree", "Teak logs", ItemID.TEAK_LOGS, 35, "Chop down"),
    MAPLE("maple tree", "Maple logs", ItemID.MAPLE_LOGS, 45, "Chop down"),
    MAHOGANY("mahogany tree", "Mahogany logs", ItemID.MAHOGANY_LOGS, 50, "Chop down"),
    YEW("yew tree", "Yew logs", ItemID.YEW_LOGS, 60, "Chop down"),
    BLISTERWOOD("blisterwood tree", "Blisterwood logs", ItemID.BLISTERWOOD_LOGS, 62, "Chop"),
    MAGIC("magic tree", "Magic logs", ItemID.MAGIC_LOGS, 75, "Chop down"),
    REDWOOD("redwood tree", "Redwood logs", ItemID.REDWOOD_LOGS, 90, "Cut"),
    EVERGREEN_TREE("evergreen tree" , "Logs", ItemID.LOGS, 1, "Chop down"),
    DEAD_TREE("dead tree" , "Logs", ItemID.LOGS, 1, "Chop down");


    private final String name;
    private final String log;
    private final int logID;
    private final int woodcuttingLevel;
    private final String action;

    @Override
    public String toString() {
        return name;
    }

    public boolean hasRequiredLevel() {
        return Rs2Player.getSkillRequirement(Skill.WOODCUTTING, this.woodcuttingLevel);
    }
}
