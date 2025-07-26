package net.runelite.client.plugins.microbot.aiofighter.model;

import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyle;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcStats;

public class Monster {
    public int id;
    public int attackAnim;
    public Rs2NpcModel npc;
    public Rs2NpcStats rs2NpcStats;
    public boolean delete;

    public int lastAttack = 0;
    public AttackStyle attackStyle;

    public Monster(int npcId, int attackAnim) {
        this.id = npcId;
        this.attackAnim = attackAnim;
    }
    public Monster(Rs2NpcModel npc, Rs2NpcStats rs2NpcStats) {
        this.npc = npc;
        this.rs2NpcStats = rs2NpcStats;
        this.lastAttack = Rs2NpcManager.getAttackSpeed(npc.getId());
    }
}
