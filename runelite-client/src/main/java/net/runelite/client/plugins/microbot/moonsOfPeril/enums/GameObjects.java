package net.runelite.client.plugins.microbot.moonsOfPeril.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.NpcID;

@Getter
@RequiredArgsConstructor
public enum GameObjects {
    ECLIPSE_MOON_STATUE_ID(ObjectID.PMOON_ENTRY_STATUE_ECLIPSE),
    ECLIPSE_MOON_NPC_ID(NpcID.PMOON_BOSS_ECLIPSE_MOON_VIS),
    BLOOD_MOON_STATUE_ID(ObjectID.PMOON_ENTRY_STATUE_BLOOD),
    BLOOD_MOON_NPC_ID(NpcID.PMOON_BOSS_BLOOD_MOON_VIS),
    BLUE_MOON_STATUE_ID(ObjectID.PMOON_ENTRY_STATUE_BLUE),
    BLUE_MOON_NPC_ID(NpcID.PMOON_BOSS_BLUE_MOON_VIS),
    SIGIL_NPC_ID(NpcID.PMOON_BOSS_THREAT_CIRCLE),;

    public final int ID;
}