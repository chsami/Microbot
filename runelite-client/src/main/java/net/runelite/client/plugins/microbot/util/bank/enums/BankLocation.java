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
    ALDARIN(new WorldPoint(1398, 2927, 0), true),
    AL_KHARID(new WorldPoint(3270, 3166, 0), false),
    ARCEUUS(new WorldPoint(1624, 3745, 0), true),
    ARDOUGNE_NORTH(new WorldPoint(2616, 3332, 0), true),
    ARDOUGNE_SOUTH(new WorldPoint(2655, 3283, 0), true),
    BARBARIAN_OUTPOST(new WorldPoint(2536, 3574, 0), true),
    BLAST_FURNACE_BANK(new WorldPoint(1948, 4957, 0), true),
    BLAST_MINE(new WorldPoint(1502, 3856, 0), true),
    BURGH_DE_ROTT(new WorldPoint(3495, 3212, 0), true),
    CAMELOT(new WorldPoint(2725, 3493, 0), true),
    CAMODZAAL(new WorldPoint(2979, 5798, 0), false),
    CAM_TORUM(new WorldPoint(1453, 9568, 1), true),
    CANIFIS(new WorldPoint(3512, 3480, 0), true),
    CASTLE_WARS(new WorldPoint(2443, 3083, 0), false),
    CATHERBY(new WorldPoint(2808, 3441, 0), true),
    CLAN_HALL(new WorldPoint(1747, 5476, 0), false),
    COOKS_GUILD(new WorldPoint(3147,3450,0), true),
    CORSAIR_COVE(new WorldPoint(2570, 2864, 0), false),
    CRAFTING_GUILD(new WorldPoint(2936, 3281, 0), true),
    DARKMEYER(new WorldPoint(3604, 3366, 0), true),
    DORGESH_KAAN_BANK(new WorldPoint(2702, 5350, 0), true),
    DRAYNOR_VILLAGE(new WorldPoint(3093, 3245, 0), false),
    DUEL_ARENA(new WorldPoint(3381, 3268, 0), true),
    DWARF_MINE_BANK(new WorldPoint(2837, 10207, 0), true),
    EDGEVILLE(new WorldPoint(3094, 3492, 0), false),
    FALADOR_EAST(new WorldPoint(3014, 3355, 0), false),
    FALADOR_WEST(new WorldPoint(2945, 3369, 0), false),
    FARMING_GUILD(new WorldPoint(1253, 3741, 0), true),
    FEROX_ENCLAVE(new WorldPoint(3130, 3631, 0), false),
    FISHING_GUILD(new WorldPoint(2586, 3420, 0), true),
    FOSSIL_ISLAND(new WorldPoint(3739, 3804, 0), true),
    FOSSIL_ISLAND_WRECK(new WorldPoint(3771, 3898, 0), true),
    GNOME_BANK(new WorldPoint(2445, 3425, 1), true),
    GNOME_TREE_BANK_SOUTH(new WorldPoint(2449, 3482, 1), true),
    GNOME_TREE_BANK_WEST(new WorldPoint(2442, 3488, 1), true),
    GRAND_EXCHANGE(new WorldPoint(3166, 3485, 0), false),
    GREAT_KOUREND_CASTLE(new WorldPoint(1612, 3681, 2), true),
    HALLOWED_SEPULCHRE(new WorldPoint(2400, 5983, 0), true),
    HOSIDIUS(new WorldPoint(1749, 3599, 0), true),
    HOSIDIUS_KITCHEN(new WorldPoint(1676, 3617, 0), true),
    HUNTERS_GUILD(new WorldPoint(1542, 3041, 0), true),
    ISLE_OF_SOULS(new WorldPoint(2212, 2859, 0), true),
    JATIZSO(new WorldPoint(2416, 3801, 0), true),
    LANDS_END(new WorldPoint(1512, 3421, 0), true),
    LEGENDS_GUILD(new WorldPoint(2732, 3379, 2), true),
    LLETYA(new WorldPoint(2353, 3163, 0), true),
    LOVAKENGJ(new WorldPoint(1526, 3739, 0), true),
    LUMBRIDGE_FRONT(new WorldPoint(3221, 3217, 0), false),
    LUMBRIDGE_BASEMENT(new WorldPoint(3218, 9623, 0), true),
    LUMBRIDGE_TOP(new WorldPoint(3209, 3220, 2), false),
    LUNAR_ISLE(new WorldPoint(2099, 3919, 0), true),
    MAGE_TRAINING_ARENA(new WorldPoint(3366, 3318, 1), true),
    MINING_GUILD(new WorldPoint(3013, 9718, 0), true),
    MISTROCK(new WorldPoint(1381, 2866, 0), true),
    MOR_UL_REK(new WorldPoint(2541, 5140, 0), true),
    MOTHERLOAD(new WorldPoint(3760, 5666, 0), true),
    MOUNT_KARUULM(new WorldPoint(1324, 3824, 0), true),
    MYTHS_GUILD(new WorldPoint(2463, 2847, 1), true),
    NARDAH(new WorldPoint(3428, 2892, 0), true),
    NEITIZNOT(new WorldPoint(2337, 3807, 0), true),
    PEST_CONTROL(new WorldPoint(2667, 2653, 0), true),
    PISCARILIUS(new WorldPoint(1803, 3790, 0), true),
    PISCATORIS_FISHING_COLONY(new WorldPoint(2330, 3689, 0), true),
    PORT_KHAZARD(new WorldPoint(2664, 3161, 0), true),
    PORT_PHASMATYS(new WorldPoint(3688, 3467, 0), true),
    PRIFDDINAS(new WorldPoint(3257, 6106, 0), true),
    ROGUES_DEN_EMERALD_BENEDICT(new WorldPoint(3043, 4973, 1), true),
    ROGUES_DEN_CHEST(new WorldPoint(3040, 4969, 1), true),
    RUINS_OF_UNKAH(new WorldPoint(3156, 2835, 0), true),
    SHANTY_PASS(new WorldPoint(3308, 3120, 0), true),
    SHAYZIEN_BANK(new WorldPoint(1488, 3592, 0), true),
    SHAYZIEN_CHEST(new WorldPoint(1486, 3646, 0), true),
    SHILO_VILLAGE(new WorldPoint(2852, 2954, 0), true),
    SOPHANEM(new WorldPoint(2799, 5169, 0), true),
    SULPHUR_MINE(new WorldPoint(1453, 3858, 0), true),
    TREE_GNOME_STRONGHOLD_NIEVE(new WorldPoint(2445, 3424, 1), true),
    TZHAAR(new WorldPoint(2446, 5178, 0), true),
    VARLAMORE_EAST(new WorldPoint(1780, 3094, 0), true),
    VARLAMORE_WEST(new WorldPoint(1647, 3118, 0), true),
    VARROCK_EAST(new WorldPoint(3253, 3422, 0), false),
    VARROCK_WEST(new WorldPoint(3183, 3441, 0), false),
    VINERY_BANK(new WorldPoint(1809, 3566, 0), true),
    VOLCANO_BANK(new WorldPoint(3819, 3809, 0), true),
    WINTERTODT(new WorldPoint(1640, 3944, 0), true),
    WARRIORS_GUILD(new WorldPoint(2843, 3542, 0), true),
    WOODCUTTING_GUILD(new WorldPoint(1591, 3479, 0), true),
    YANILLE(new WorldPoint(2613, 3093, 0), true),
    ZANARIS(new WorldPoint(2383, 4458, 0), true),
    ZEAH_SAND_BANK(new WorldPoint(1719, 3465, 0), true);

    private final WorldPoint worldPoint;
    private final boolean members;

    public boolean hasRequirements() {
        if (isMembers()) {
            if (!Rs2Player.isMember() || !Rs2Player.isInMemberWorld()) return false;
        }
        switch (this) {
            case CRAFTING_GUILD:
                boolean hasFaladorHardDiary = Microbot.getVarbitValue(Varbits.DIARY_FALADOR_HARD) == 1;
                boolean hasMaxedCrafting = Rs2Player.getSkillRequirement(Skill.CRAFTING, 99, false);
                boolean isWearingCraftingGuild = (Rs2Equipment.isWearing("brown apron") || Rs2Equipment.isWearing("golden apron")) ||
                        (Rs2Equipment.isWearing("max cape") || Rs2Equipment.isWearing("max hood")) ||
                        (Rs2Equipment.isWearing("crafting cape") || Rs2Equipment.isWearing("crafting hood"));
                return isWearingCraftingGuild && (hasMaxedCrafting || hasFaladorHardDiary);
            case LUMBRIDGE_BASEMENT:
                return Rs2Player.getQuestState(Quest.RECIPE_FOR_DISASTER__ANOTHER_COOKS_QUEST) == QuestState.FINISHED;
            case COOKS_GUILD:
                boolean hasVarrockHardDiary = Microbot.getVarbitValue(Varbits.DIARY_VARROCK_HARD) == 1;
                boolean hasMaxedCooking = Rs2Player.getSkillRequirement(Skill.COOKING, 99, false);
                boolean isWearingCooksGuild = Rs2Equipment.isWearing("chef's hat") ||
                        (Rs2Equipment.isWearing("cooking cape") || Rs2Equipment.isWearing("cooking hood")) ||
                        (Rs2Equipment.isWearing("max cape") || Rs2Equipment.isWearing("max hood")) ||
                        (Rs2Equipment.isWearing("varrock armour 3") || Rs2Equipment.isWearing("varrock armour 4"));
                return isWearingCooksGuild && (hasVarrockHardDiary || hasMaxedCooking);
            case WARRIORS_GUILD:
                return (Rs2Player.getSkillRequirement(Skill.ATTACK, 99, false) || Rs2Player.getSkillRequirement(Skill.STRENGTH, 99, false)) ||
                        (Rs2Player.getRealSkillLevel(Skill.ATTACK) + Rs2Player.getRealSkillLevel(Skill.STRENGTH) >= 130);
            case WOODCUTTING_GUILD:
                return Rs2Player.getSkillRequirement(Skill.WOODCUTTING, 60, true);
            case FARMING_GUILD:
                return Rs2Player.getSkillRequirement(Skill.FARMING, 45, true);
            case MINING_GUILD:
                boolean inRegion = Microbot.getClient().getLocalPlayer().getWorldLocation().getRegionID() == 12183 || Microbot.getClient().getLocalPlayer().getWorldLocation().getRegionID() == 12184;
                return inRegion && Rs2Player.getSkillRequirement(Skill.MINING, 60, true);
            case FISHING_GUILD:
                return Rs2Player.getSkillRequirement(Skill.FISHING, 68, true);
            case HUNTERS_GUILD:
                return Rs2Player.getSkillRequirement(Skill.HUNTER, 46, false);
            case LEGENDS_GUILD:
                return Rs2Player.getQuestState(Quest.LEGENDS_QUEST) == QuestState.FINISHED;
            case MAGE_TRAINING_ARENA:
                return true;
            case PORT_PHASMATYS:
                return Rs2Player.getQuestState(Quest.GHOSTS_AHOY) == QuestState.FINISHED;
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
            case PISCATORIS_FISHING_COLONY:
                // Requires Swan Song
                return Rs2Player.getQuestState(Quest.SWAN_SONG) == QuestState.FINISHED;
            case LUMBRIDGE_FRONT:
                // Requires to be in a PvP World
                return Microbot.getClient().getWorldType().contains(WorldType.PVP);
            default:
                return true;
        }
    }
}