package net.runelite.client.plugins.microbot.runecrafting.gotr2.data;


import lombok.Getter;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.runecrafting.gotr2.data.CellType;
import net.runelite.client.plugins.microbot.runecrafting.gotr2.data.RuneType;

public class GuardianPortalInfo {
    @Getter
    private String name;
    @Getter
    private int requiredLevel;
    private int runeId;
    private int talismanId;
    @Getter
    private int spriteId;
    private RuneType runeType;
    private CellType cellType;

    @Getter
    private QuestState questState;

    public GuardianPortalInfo(String name, int requiredLevel, int runeId, int talismanId, int spriteId, RuneType runeType, CellType cellType, QuestState questState) {
        this.name = name;
        this.requiredLevel = requiredLevel;
        this.runeId = runeId;
        this.talismanId = talismanId;
        this.spriteId = spriteId;
        this.runeType = runeType;
        this.cellType = cellType;
        this.questState = questState;
    }
}