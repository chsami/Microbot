package net.runelite.client.plugins.microbot.util.depositbox;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum DepositBoxLocation {
    CLAN_HALL(new WorldPoint(1744, 5475, 0)),
    CORSAIR_COVE(new WorldPoint(2569, 2862, 0)),
    SHANTAY_PASS(new WorldPoint(3309, 3124, 0)),
    DRAYNOR_VILLAGE(new WorldPoint(3094, 3240, 0)),
    EMIRS_ARENA(new WorldPoint(3383, 3273, 0)),
    EDGEVILLE(new WorldPoint(3098, 3499, 0)),
    FALADOR(new WorldPoint(3018, 3358, 0)),
    GEM_MINE(new WorldPoint(2842, 9383, 0)),
    GRAND_EXCHANGE(new WorldPoint(3174, 3493, 0)),
    LUMBRIDGE(new WorldPoint(3210, 3217, 2)),
    PORT_SARIM(new WorldPoint(3045, 3234, 0)),
    VARROCK(new WorldPoint(3180, 3433, 0)),
    ALDARIN(new WorldPoint(1395, 2925, 0)),
    ARCEUUS(new WorldPoint(1626, 3737, 0)),
    ARDOUGNE(new WorldPoint(2654, 3280, 0)),
    BARBARIAN_ASSAULT(new WorldPoint(2537, 3572, 0)),
    BLAST_FURNACE(new WorldPoint(1950, 4956, 0)),
    BURGH_DE_ROTT(new WorldPoint(3498, 3210, 0)),
    CAM_TORUM(new WorldPoint(1449, 9570, 1)),
    CANIFIS(new WorldPoint(3509, 3474, 0)),
    CATHERBY(new WorldPoint(2806, 3439, 0)),
    CIVITAS_ILLA_FORTIS_EAST(new WorldPoint(1781, 3100, 0)),
    CIVITAS_ILLA_FORTIS_WEST(new WorldPoint(1646, 3112, 0)),
    CRAFTING_GUILD(new WorldPoint(2929, 3286, 0)),
    DARKMEYER(new WorldPoint(3602, 3366, 0)),
    DORGESH_KAAN(new WorldPoint(2705, 5345, 0)),
    DWARVEN_MINE(new WorldPoint(3013, 9720, 0)),
    ETCETERIA(new WorldPoint(2619, 3896, 0)),
    FARMING_GUILD(new WorldPoint(1254, 3740, 0)),
    FISHING_GUILD(new WorldPoint(2588, 3416, 0)),
    THE_GAUNTLET_LOBBY(new WorldPoint(3038, 6129, 1)),
    GWENITH(new WorldPoint(2202, 3409, 0)),
    HOSIDIUS(new WorldPoint(1753, 3596, 0)),
    ISLE_OF_SOULS(new WorldPoint(2210, 2861, 0)),
    KELDAGRIM(new WorldPoint(2834, 10209, 0)),
    KOUREND_CASTLE(new WorldPoint(1610, 3683, 2)),
    LANDS_END(new WorldPoint(1507, 3421, 0)),
    LEGENDS_GUILD(new WorldPoint(2731, 3376, 2)),
    LLETYA(new WorldPoint(2350, 3162, 0)),
    LOVAKENGJ(new WorldPoint(1533, 3741, 0)),
    LUNAR_ISLE(new WorldPoint(2102, 3917, 0)),
    MAGE_ARENA(new WorldPoint(3108, 10351, 0)),
    MISTROCK(new WorldPoint(1383, 2869, 0)),
    MOR_UL_REK(new WorldPoint(2451, 5179, 0)),
    MOS_LE_HARMLESS(new WorldPoint(3679, 2984, 0)),
    MOTHERLODE_MINE(new WorldPoint(3759, 5664, 0)),
    MOUNT_KARUULM(new WorldPoint(1322, 3824, 0)),
    MOUNT_QUIDAMORTEM(new WorldPoint(1252, 3571, 0)),
    MYTHS_GUILD(new WorldPoint(2462, 2845, 1)),
    NARDAH(new WorldPoint(3429, 2894, 0)),
    PISCATORIS_FISHING_COLONY(new WorldPoint(2330, 3686, 0)),
    PORT_KHAZARD(new WorldPoint(2664, 3159, 0)),
    PORT_PHASMATYS(new WorldPoint(3686, 3471, 0)),
    PORT_PISCARILIUS(new WorldPoint(1806, 3785, 0)),
    PRIFDDINAS(new WorldPoint(3295, 6062, 0)),
    QUETZACALLI_GORGE(new WorldPoint(1518, 3226, 0)),
    ROGUES_DEN(new WorldPoint(3041, 4964, 1)),
    RUINS_OF_UNKAH(new WorldPoint(3159, 2834, 0)),
    SALTPETRE_MINE(new WorldPoint(1702, 3527, 0)),
    SEERS_VILLAGE(new WorldPoint(2730, 3492, 0)),
    SHAYZIEN(new WorldPoint(1501, 3613, 0)),
    SHILO_VILLAGE(new WorldPoint(2852, 2951, 0)),
    VER_SINHAZA(new WorldPoint(3655, 3209, 0)),
    VOID_KNIGHTS_OUTPOST(new WorldPoint(2669, 2655, 0)),
    OUTSIDE_THE_VOLCANIC_MINE(new WorldPoint(3820, 3808, 0)),
    WARRIORS_GUILD(new WorldPoint(2846, 3536, 0)),
    WINTERTODT_CAMP(new WorldPoint(1636, 3948, 0)),
    WOODCUTTING_GUILD(new WorldPoint(1589, 3476, 0)),
    YANILLE(new WorldPoint(2611, 3088, 0)),
    ZANARIS(new WorldPoint(2382, 4462, 0));

    private final WorldPoint worldPoint;

    private boolean isMember() {
        return Rs2Player.isMember() && Rs2Player.isInMemberWorld();
    }
    
    public boolean hasRequirements() {
        boolean hasLineOfSight = Microbot.getClient().getLocalPlayer().getWorldArea().hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), worldPoint);
        switch (this) {
            case CRAFTING_GUILD:
                boolean hasFaladorHardDiary = Microbot.getVarbitValue(Varbits.DIARY_FALADOR_HARD) == 1;
                boolean hasMaxedCrafting = Rs2Player.getSkillRequirement(Skill.CRAFTING, 99, false);
                boolean isWearingCraftingGuild = (Rs2Equipment.isWearing("brown apron") || Rs2Equipment.isWearing("golden apron")) ||
                        (Rs2Equipment.isWearing("max cape") || Rs2Equipment.isWearing("max hood")) ||
                        (Rs2Equipment.isWearing("crafting cape") || Rs2Equipment.isWearing("crafting hood"));
                
                if (!isMember()) return false;
                if (hasLineOfSight && (hasMaxedCrafting || hasFaladorHardDiary)) return true;
                return isWearingCraftingGuild && (hasMaxedCrafting || hasFaladorHardDiary);
            case WARRIORS_GUILD:
                if (!isMember()) return false;
                if (hasLineOfSight) return true;
                return isMember() &&
                        (Rs2Player.getSkillRequirement(Skill.ATTACK, 99, false) || Rs2Player.getSkillRequirement(Skill.STRENGTH, 99, false)) ||
                        (Rs2Player.getRealSkillLevel(Skill.ATTACK) + Rs2Player.getRealSkillLevel(Skill.STRENGTH) >= 130);
            case WOODCUTTING_GUILD:
                if (!isMember()) return false;
                if (hasLineOfSight) return true;
                return isMember() && Rs2Player.getSkillRequirement(Skill.WOODCUTTING, 60, true);
            case FARMING_GUILD:
                if (!isMember()) return false;
                if (hasLineOfSight) return true;
                return isMember() && Rs2Player.getSkillRequirement(Skill.FARMING, 45, true);
            case FISHING_GUILD:
                if (!isMember()) return false;
                if (hasLineOfSight) return true;
                return isMember() && Rs2Player.getSkillRequirement(Skill.FISHING, 68, true);
            case GEM_MINE:
                if (!isMember()) return false;
                if (hasLineOfSight) return true;
                return isMember() && Microbot.getVarbitValue(Varbits.DIARY_KARAMJA_MEDIUM) == 1;
            case LEGENDS_GUILD:
                if (!isMember()) return false;
                if (hasLineOfSight) return true;
                return isMember() && Rs2Player.getQuestState(Quest.LEGENDS_QUEST) == QuestState.FINISHED;
            case PORT_PHASMATYS:
                if (!isMember()) return false;
                if (hasLineOfSight) return true;
                return isMember() && Rs2Player.getQuestState(Quest.GHOSTS_AHOY) == QuestState.FINISHED;
            case CORSAIR_COVE:
                // Requires The Corsair Curse
                return Rs2Player.getQuestState(Quest.THE_CORSAIR_CURSE) == QuestState.FINISHED;
            case CLAN_HALL:
                // Requires Clan Membership, varbit 933
                return Microbot.getVarbitValue(933) > 1;
            case LLETYA:
                if (!isMember()) return false;
                // Requires Mournings End Part 1 in progress or completed
                return Rs2Player.getQuestState(Quest.MOURNINGS_END_PART_I) == QuestState.IN_PROGRESS || Rs2Player.getQuestState(Quest.MOURNINGS_END_PART_I) == QuestState.FINISHED;
            case THE_GAUNTLET_LOBBY:
            case GWENITH:
            case PRIFDDINAS:
                if (!isMember()) return false;
                // Requires Song of the elves to be completed
                return Rs2Player.getQuestState(Quest.SONG_OF_THE_ELVES) == QuestState.FINISHED;
            case BURGH_DE_ROTT:
                if (!isMember()) return false;
                // Requires Priest in Peril & In Aid of the Myreque
                return Rs2Player.getQuestState(Quest.PRIEST_IN_PERIL) == QuestState.FINISHED && Rs2Player.getQuestState(Quest.IN_AID_OF_THE_MYREQUE) == QuestState.FINISHED;
            case CANIFIS:
                if (!isMember()) return false;
                // Requires Priest in Peril
                return Rs2Player.getQuestState(Quest.PRIEST_IN_PERIL) == QuestState.FINISHED;
            case ZANARIS:
                if (!isMember()) return false;
                // Requires Lost City, Fairytale part 1 & starting Fairytale part 2
                return Rs2Player.getQuestState(Quest.LOST_CITY) == QuestState.FINISHED &&
                        Rs2Player.getQuestState(Quest.FAIRYTALE_I__GROWING_PAINS) == QuestState.FINISHED &&
                        Rs2Player.getQuestState(Quest.FAIRYTALE_II__CURE_A_QUEEN) != QuestState.NOT_STARTED;
            case CAM_TORUM:
                if (!isMember()) return false;
                // Requires Perilous Moons to be started
                return Rs2Player.getQuestState(Quest.PERILOUS_MOONS) != QuestState.NOT_STARTED;
            case LUNAR_ISLE:
                if (!isMember()) return false;
                // Requires Lunar Diplomacy & Seal of passage OR Dream Mentor
                if (Rs2Player.getQuestState(Quest.DREAM_MENTOR) != QuestState.FINISHED) {
                    return Rs2Player.getQuestState(Quest.LUNAR_DIPLOMACY) == QuestState.FINISHED && Rs2Equipment.hasEquipped(ItemID.SEAL_OF_PASSAGE);
                } else {
                    return Rs2Player.getQuestState(Quest.DREAM_MENTOR) == QuestState.FINISHED;
                }
            case DARKMEYER:
                if (!isMember()) return false;
                // Requires Sins of the Father
                return Rs2Player.getQuestState(Quest.SINS_OF_THE_FATHER) == QuestState.FINISHED;
            case BLAST_FURNACE:
            case KELDAGRIM:
                if (!isMember()) return false;
                // Requires The Giant Dwarf
                return Rs2Player.getQuestState(Quest.THE_GIANT_DWARF) == QuestState.FINISHED;
            case DORGESH_KAAN:
                if (!isMember()) return false;
                // Requires Death to the Dorgeshuun
                return Rs2Player.getQuestState(Quest.DEATH_TO_THE_DORGESHUUN) == QuestState.FINISHED;
            case ALDARIN:
            case CIVITAS_ILLA_FORTIS_EAST:
            case CIVITAS_ILLA_FORTIS_WEST:
            case QUETZACALLI_GORGE:
            case MISTROCK:
                if (!isMember()) return false;
                // Requires Children of the Sun
                return Rs2Player.getQuestState(Quest.CHILDREN_OF_THE_SUN) == QuestState.FINISHED;
            case ARCEUUS:
            case LOVAKENGJ:
            case SHAYZIEN:
            case KOUREND_CASTLE:
            case PORT_PISCARILIUS:
            case WINTERTODT_CAMP:
            case MOUNT_QUIDAMORTEM:
            case HOSIDIUS:
            case MOUNT_KARUULM:
                if (!isMember()) return false;
                // Requires Client of Kourend
                return Rs2Player.getQuestState(Quest.CLIENT_OF_KOUREND) != QuestState.NOT_STARTED;
            case SHILO_VILLAGE:
                if (!isMember()) return false;
                // Requires Shilo Village to enter the village & use the bank
                return Rs2Player.getQuestState(Quest.SHILO_VILLAGE) == QuestState.FINISHED;
            case MYTHS_GUILD:
                if (!isMember()) return false;
                // Requires Dragon Slayer 2
                return Rs2Player.getQuestState(Quest.DRAGON_SLAYER_II) == QuestState.FINISHED;
            case OUTSIDE_THE_VOLCANIC_MINE:
                if (!isMember()) return false;
                // Requires Bone Voyage
                return Rs2Player.getQuestState(Quest.BONE_VOYAGE) == QuestState.FINISHED;
            case PISCATORIS_FISHING_COLONY:
                if (!isMember()) return false;
                // Requires to start Swan Song
                return Rs2Player.getQuestState(Quest.SWAN_SONG) != QuestState.NOT_STARTED;
            case MOS_LE_HARMLESS:
                if (!isMember()) return false;
                // Requires Cabin Fever
                return Rs2Player.getQuestState(Quest.CABIN_FEVER) == QuestState.FINISHED;
            case ARDOUGNE:
            case CATHERBY:
            case ETCETERIA:
            case LANDS_END:
            case MAGE_ARENA:
            case MOR_UL_REK:
            case ROGUES_DEN:
            case VER_SINHAZA:
            case PORT_KHAZARD:
            case DWARVEN_MINE:
            case ISLE_OF_SOULS:
            case SEERS_VILLAGE:
            case SALTPETRE_MINE:
            case RUINS_OF_UNKAH:
            case MOTHERLODE_MINE:
            case BARBARIAN_ASSAULT:
            case VOID_KNIGHTS_OUTPOST:
            case NARDAH:
            case YANILLE:
            case SHANTAY_PASS:
                return isMember();
            default:
                return true;
        }
    }
}
