package net.runelite.client.plugins.microbot.util.bank.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum BankLocation {
    ALDARIN(new WorldPoint(1398, 2927, 0)),
    AL_KHARID(new WorldPoint(3270, 3166, 0)),
    ARCEUUS(new WorldPoint(1624, 3745, 0)),
    ARDOUGNE_NORTH(new WorldPoint(2616, 3332, 0)),
    ARDOUGNE_SOUTH(new WorldPoint(2655, 3283, 0)),
    BARBARIAN_OUTPOST(new WorldPoint(2536, 3574, 0)),
    BLAST_FURNACE_BANK(new WorldPoint(1948, 4957, 0)),
    BLAST_MINE(new WorldPoint(1502, 3856, 0)),
    BURGH_DE_ROTT(new WorldPoint(3495, 3212, 0)),
    CAMELOT(new WorldPoint(2725, 3493, 0)),
    CAMODZAAL(new WorldPoint(2979, 5798, 0)),
    CAM_TORUM(new WorldPoint(1450, 9557, 1)),
    CANIFIS(new WorldPoint(3512, 3480, 0)),
    CASTLE_WARS(new WorldPoint(2443, 3083, 0)),
    CATHERBY(new WorldPoint(2808, 3441, 0)),
    CLAN_HALL(new WorldPoint(1747, 5476, 0)),
    COOKS_GUILD(new WorldPoint(3147,3450,0)),
    CORSAIR_COVE(new WorldPoint(2570, 2864, 0)),
    CRAFTING_GUILD(new WorldPoint(2936, 3281, 0)),
    DARKMEYER(new WorldPoint(3604, 3366, 0)),
    DIHN_BANK(new WorldPoint(1640, 3944, 0)),
    DORGESH_KAAN_BANK(new WorldPoint(2702, 5350, 0)),
    DRAYNOR_VILLAGE(new WorldPoint(3093, 3245, 0)),
    DUEL_ARENA(new WorldPoint(3381, 3268, 0)),
    DWARF_MINE_BANK(new WorldPoint(2837, 10207, 0)),
    EDGEVILLE(new WorldPoint(3094, 3492, 0)),
    FALADOR_EAST(new WorldPoint(3014, 3355, 0)),
    FALADOR_WEST(new WorldPoint(2945, 3369, 0)),
    FARMING_GUILD(new WorldPoint(1253, 3741, 0)),
    FEROX_ENCLAVE(new WorldPoint(3130, 3631, 0)),
    FISHING_GUILD(new WorldPoint(2586, 3420, 0)),
    FOSSIL_ISLAND(new WorldPoint(3739, 3804, 0)),
    FOSSIL_ISLAND_WRECK(new WorldPoint(3771, 3898, 0)),
    GNOME_BANK(new WorldPoint(2445, 3425, 1)),
    GNOME_TREE_BANK_SOUTH(new WorldPoint(2449, 3482, 1)),
    GNOME_TREE_BANK_WEST(new WorldPoint(2442, 3488, 1)),
    GRAND_EXCHANGE(new WorldPoint(3166, 3485, 0)),
    GREAT_KOUREND_CASTLE(new WorldPoint(1612, 3681, 2)),
    HALLOWED_SEPULCHRE(new WorldPoint(2400, 5983, 0)),
    HOSIDIUS(new WorldPoint(1749, 3599, 0)),
    HOSIDIUS_KITCHEN(new WorldPoint(1676, 3617, 0)),
    HUNTERS_GUILD(new WorldPoint(1542, 3041, 0)),
    ISLE_OF_SOULS(new WorldPoint(2212, 2859, 0)),
    JATIZSO(new WorldPoint(2416, 3801, 0)),
    LANDS_END(new WorldPoint(1512, 3421, 0)),
    LEGENDS_GUILD(new WorldPoint(2732, 3379, 2)),
    LLETYA(new WorldPoint(2353, 3163, 0)),
    LOVAKENGJ(new WorldPoint(1526, 3739, 0)),
    LUMBRIDGE_BASEMENT(new WorldPoint(3218, 9623, 0)),
    LUMBRIDGE_TOP(new WorldPoint(3209, 3220, 2)),
    LUNAR_ISLE(new WorldPoint(2099, 3919, 0)),
    MAGE_TRAINING_ARENA(new WorldPoint(3366, 3318, 1)),
    MINING_GUILD(new WorldPoint(3013, 9718, 0)),
    MOR_UL_REK(new WorldPoint(2541, 5140, 0)),
    MOTHERLOAD(new WorldPoint(3760, 5666, 0)),
    MOUNT_KARUULM(new WorldPoint(1324, 3824, 0)),
    MYTHS_GUILD(new WorldPoint(2463, 2847, 1)),
    NARDAH(new WorldPoint(3428, 2892, 0)),
    NEITIZNOT(new WorldPoint(2337, 3807, 0)),
    PEST_CONTROL(new WorldPoint(2667, 2653, 0)),
    PISCARILIUS(new WorldPoint(1803, 3790, 0)),
    PORT_KHAZARD(new WorldPoint(2664, 3161, 0)),
    PORT_PHASMATYS(new WorldPoint(3688, 3467, 0)),
    PRIFDDINAS(new WorldPoint(3257, 6106, 0)),
    ROGUES_DEN_EMERALD_BENEDICT(new WorldPoint(3043, 4973, 1)),
    ROGUES_DEN_CHEST(new WorldPoint(3040, 4969, 1)),
    RUINS_OF_UNKAH(new WorldPoint(3156, 2835, 0)),
    SHANTY_PASS(new WorldPoint(3308, 3120, 0)),
    SHAYZIEN_BANK(new WorldPoint(1488, 3592, 0)),
    SHAYZIEN_CHEST(new WorldPoint(1486, 3646, 0)),
    SHILO_VILLAGE(new WorldPoint(2852, 2954, 0)),
    SOPHANEM(new WorldPoint(2799, 5169, 0)),
    SULPHUR_MINE(new WorldPoint(1453, 3858, 0)),
    TREE_GNOME_STRONGHOLD_NIEVE(new WorldPoint(2445, 3424, 1)),
    TZHAAR(new WorldPoint(2446, 5178, 0)),
    VARLAMORE_EAST(new WorldPoint(1780, 3094, 0)),
    VARLAMORE_WEST(new WorldPoint(1647, 3118, 0)),
    VARROCK_EAST(new WorldPoint(3253, 3422, 0)),
    VARROCK_WEST(new WorldPoint(3183, 3441, 0)),
    VINERY_BANK(new WorldPoint(1809, 3566, 0)),
    VOLCANO_BANK(new WorldPoint(3819, 3809, 0)),
    WINTERTODT(new WorldPoint(1640, 3944, 0)),
    WARRIORS_GUILD(new WorldPoint(2843, 3542, 0)),
    WOODCUTTING_GUILD(new WorldPoint(1591, 3479, 0)),
    YANILLE(new WorldPoint(2613, 3093, 0)),
    ZANARIS(new WorldPoint(2383, 4458, 0)),
    ZEAH_SAND_BANK(new WorldPoint(1719, 3465, 0));

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

                if (hasLineOfSight && isMember() && (hasMaxedCrafting || hasFaladorHardDiary)) return true;
                return isMember() && isWearingCraftingGuild &&
                        (hasMaxedCrafting || hasFaladorHardDiary);
            case LUMBRIDGE_BASEMENT:
                return isMember() && Rs2Player.getQuestState(Quest.RECIPE_FOR_DISASTER__ANOTHER_COOKS_QUEST) == QuestState.FINISHED;
            case COOKS_GUILD:
                boolean hasVarrockHardDiary = Microbot.getVarbitValue(Varbits.DIARY_VARROCK_HARD) == 1;
                boolean hasMaxedCooking = Rs2Player.getSkillRequirement(Skill.COOKING, 99, false);
                boolean isWearingCooksGuild = Rs2Equipment.isWearing("chef's hat") ||
                        (Rs2Equipment.isWearing("cooking cape") || Rs2Equipment.isWearing("cooking hood")) ||
                        (Rs2Equipment.isWearing("max cape") || Rs2Equipment.isWearing("max hood")) ||
                        (Rs2Equipment.isWearing("varrock armour 3") || Rs2Equipment.isWearing("varrock armour 4"));

                if (hasLineOfSight && isMember() && (hasMaxedCooking || hasVarrockHardDiary)) return true;
                return isMember() && isWearingCooksGuild &&
                        (hasVarrockHardDiary || hasMaxedCooking);
            case WARRIORS_GUILD:
                if (hasLineOfSight && isMember()) return true;
                return isMember() &&
                        (Rs2Player.getSkillRequirement(Skill.ATTACK, 99, false) || Rs2Player.getSkillRequirement(Skill.STRENGTH, 99, false)) ||
                        (Rs2Player.getRealSkillLevel(Skill.ATTACK) + Rs2Player.getRealSkillLevel(Skill.STRENGTH) >= 130);
            case WOODCUTTING_GUILD:
                if (hasLineOfSight && isMember()) return true;
                return isMember() && Rs2Player.getSkillRequirement(Skill.WOODCUTTING, 60, true);
            case FARMING_GUILD:
                if (hasLineOfSight && isMember()) return true;
                return isMember() && Rs2Player.getSkillRequirement(Skill.FARMING, 45, true);
            case MINING_GUILD:
                boolean inRegion = Microbot.getClient().getLocalPlayer().getWorldLocation().getRegionID() == 12183 || Microbot.getClient().getLocalPlayer().getWorldLocation().getRegionID() == 12184;
                if (hasLineOfSight && isMember()) return true;
                return inRegion && isMember() && Rs2Player.getSkillRequirement(Skill.MINING, 60, true);
            case FISHING_GUILD:
                if (hasLineOfSight && isMember()) return true;
                return isMember() && Rs2Player.getSkillRequirement(Skill.FISHING, 68, true);
            case HUNTERS_GUILD:
                if (hasLineOfSight && isMember()) return true;
                return isMember() && Rs2Player.getSkillRequirement(Skill.HUNTER, 46, false);
            case LEGENDS_GUILD:
                if (hasLineOfSight && isMember()) return true;
                return isMember() && Rs2Player.getQuestState(Quest.LEGENDS_QUEST) == QuestState.FINISHED;
            case MAGE_TRAINING_ARENA:
                if (hasLineOfSight && isMember()) return true;
                return isMember();
            case PORT_PHASMATYS:
                if (hasLineOfSight && isMember()) return true;
                return isMember() && Rs2Player.getQuestState(Quest.GHOSTS_AHOY) == QuestState.FINISHED;
            case CORSAIR_COVE:
                // Requires The Corsair Curse
                return Rs2Player.getQuestState(Quest.THE_CORSAIR_CURSE) == QuestState.FINISHED;
            case SOPHANEM:
                return Rs2Player.getQuestState(Quest.CONTACT) == QuestState.FINISHED;
            case CLAN_HALL:
                // Requires Clan Membership, varbit 933
                return Microbot.getVarbitValue(933) > 1;
            case LLETYA:
                // Requires Mournings End Part 1 in progress or completed
                return Rs2Player.getQuestState(Quest.MOURNINGS_END_PART_I) != QuestState.NOT_STARTED;
            case PRIFDDINAS:
                // Requires Song of the elves to be completed
                return Rs2Player.getQuestState(Quest.SONG_OF_THE_ELVES) == QuestState.FINISHED;
            case SHILO_VILLAGE:
                // Requires Shilo Village to enter the village & use the bank
                return Rs2Player.getQuestState(Quest.SHILO_VILLAGE) == QuestState.FINISHED;
            case LUNAR_ISLE:
                // Requires Lunar Diplomacy & Seal of passage OR Dream Mentor
                if (Rs2Player.getQuestState(Quest.DREAM_MENTOR) != QuestState.FINISHED) {
                    return Rs2Player.getQuestState(Quest.LUNAR_DIPLOMACY) == QuestState.FINISHED && Rs2Equipment.hasEquipped(ItemID.SEAL_OF_PASSAGE);
                } else {
                    return Rs2Player.getQuestState(Quest.DREAM_MENTOR) == QuestState.FINISHED;
                }
            case JATIZSO:
            case NEITIZNOT:
                // Requires The Fremennik Trials & The Fremennik Isles
                return Rs2Player.getQuestState(Quest.THE_FREMENNIK_TRIALS) == QuestState.FINISHED && Rs2Player.getQuestState(Quest.THE_FREMENNIK_ISLES) == QuestState.FINISHED;
            case BURGH_DE_ROTT:
                // Requires Priest in Peril & In Aid of the Myreque
                return Rs2Player.getQuestState(Quest.PRIEST_IN_PERIL) == QuestState.FINISHED && Rs2Player.getQuestState(Quest.IN_AID_OF_THE_MYREQUE) != QuestState.NOT_STARTED;
            case CANIFIS:
                // Requires Priest in Peril
                return Rs2Player.getQuestState(Quest.PRIEST_IN_PERIL) == QuestState.FINISHED;
            case CAM_TORUM:
                // Requires Perilous Moons to be started
                return Rs2Player.getQuestState(Quest.PERILOUS_MOONS) != QuestState.NOT_STARTED;
            case ZANARIS:
                // Requires Lost City, Fairytale part 1 & starting Fairytale part 2
                return Rs2Player.getQuestState(Quest.LOST_CITY) == QuestState.FINISHED && 
                        Rs2Player.getQuestState(Quest.FAIRYTALE_I__GROWING_PAINS) == QuestState.FINISHED && 
                        Rs2Player.getQuestState(Quest.FAIRYTALE_II__CURE_A_QUEEN) != QuestState.NOT_STARTED;
            case FOSSIL_ISLAND:
                // TODO: How to check if the chest has been built? maybe there is a varbit?
            case VOLCANO_BANK:
            case FOSSIL_ISLAND_WRECK:
                // Requires Bone Voyage
                return Rs2Player.getQuestState(Quest.BONE_VOYAGE) == QuestState.FINISHED;
            case ALDARIN:
            case VARLAMORE_EAST:
            case VARLAMORE_WEST:
                // Requires Children of the Sun
                return Rs2Player.getQuestState(Quest.CHILDREN_OF_THE_SUN) == QuestState.FINISHED;
            case LOVAKENGJ:
            case HOSIDIUS:
            case PISCARILIUS:
            case ARCEUUS:
            case DIHN_BANK:
            case WINTERTODT:
            case SULPHUR_MINE:
            case ZEAH_SAND_BANK:
            case BLAST_MINE:
            case SHAYZIEN_BANK:
            case SHAYZIEN_CHEST:
            case GREAT_KOUREND_CASTLE:
            case HOSIDIUS_KITCHEN:
            case VINERY_BANK:
            case MOUNT_KARUULM:
                // Requires Client of Kourend
                return Rs2Player.getQuestState(Quest.CLIENT_OF_KOUREND) != QuestState.NOT_STARTED;
            case DWARF_MINE_BANK:
            case BLAST_FURNACE_BANK:
                // Requires The Giant Dwarf
                return Rs2Player.getQuestState(Quest.THE_GIANT_DWARF) != QuestState.NOT_STARTED;
            case CAMODZAAL:
                // Requires Below Ice Mountain
                return Rs2Player.getQuestState(Quest.BELOW_ICE_MOUNTAIN) == QuestState.FINISHED;
            case DORGESH_KAAN_BANK:
                // Requires Death to the Dorgeshuun
                return Rs2Player.getQuestState(Quest.DEATH_TO_THE_DORGESHUUN) == QuestState.FINISHED;
            case DARKMEYER:
                // Requires Sins of the Father
                return Rs2Player.getQuestState(Quest.SINS_OF_THE_FATHER) == QuestState.FINISHED;
            case MYTHS_GUILD:
                // Requires Dragon Slayer 2
                return Rs2Player.getQuestState(Quest.DRAGON_SLAYER_II) == QuestState.FINISHED;
            case ROGUES_DEN_CHEST:
            case CAMELOT:
            case HALLOWED_SEPULCHRE:
            case RUINS_OF_UNKAH:
            case ISLE_OF_SOULS:
            case PORT_KHAZARD:
            case MOR_UL_REK:
            case CATHERBY:
            case NARDAH:
            case LANDS_END:
            case TZHAAR:
            case YANILLE:
            case GNOME_BANK:
            case MOTHERLOAD:
            case PEST_CONTROL:
            case ARDOUGNE_NORTH:
            case ARDOUGNE_SOUTH:
            case GNOME_TREE_BANK_WEST:
            case BARBARIAN_OUTPOST:
            case GNOME_TREE_BANK_SOUTH:
            case ROGUES_DEN_EMERALD_BENEDICT:
            case TREE_GNOME_STRONGHOLD_NIEVE:
                return isMember();
            default:
                return true;
        }
    }
}
