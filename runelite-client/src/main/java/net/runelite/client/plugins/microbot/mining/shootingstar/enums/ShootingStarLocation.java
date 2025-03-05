package net.runelite.client.plugins.microbot.mining.shootingstar.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * The ShootingStarLocation consists of some static game data that is used for each location where a star can drop
 * (most are included, may need to be updated in the future to include new locations)
 */

@Getter
@RequiredArgsConstructor
public enum ShootingStarLocation {

    YANILLE_BANK(new WorldPoint(2605, 3092, 0), "Yanille bank", "Yanille", false),
    SHAYZIEN_MINE(new WorldPoint(1598, 3644, 0), "Shayzien mine south of Kourend Castle", "Shayzien Mine", false),
    MOUNT_QUIDAMORTEM__BANK(new WorldPoint(1260, 3563, 0), "Chambers of Xeric bank", "CoX bank", false),
    DRAYNOR_VILLAGE(new WorldPoint(3089, 3237, 0), "Draynor Village", "Draynor", false),
    VOLCANIC_MINE_ENTRANCE(new WorldPoint(3820, 3799, 0), "Fossil Island Volcanic Mine entrance", "Volcanic Mine", false),
    MOUNT_KARUULM__BANK(new WorldPoint(1325, 3819, 0), "Mount Karuulm bank", "Mount Karuulm bank", false),
    DWARVEN_MINE_NORTHERN_ENTRANCE(new WorldPoint(3017, 3445, 0), "North Dwarven Mine entrance", "Dwarven Mine", false),
    NARDAH(new WorldPoint(3434, 2891, 0), "Nardah bank", "Nardah", false),
    AGILITY_PYRAMID_MINE(new WorldPoint(3314, 2867, 0), "Agility Pyramid mine", "Agility Pyramid", false),
    UZER_MINE(new WorldPoint(3422, 3159, 0), "Nw of Uzer (Eagle's Eyrie)", "Uzer Mine", false),
    PORT_KHAZARD_MINE(new WorldPoint(2625, 3143, 0), "Port Khazard mine", "Port Khazard", false),
    GRAND_TREE(new WorldPoint(2446, 3491, 0), "West of Grand Tree", "Grand Tree", false),
    BANDIT_CAMP_MINE__HOBGOBLINS(new WorldPoint(3093, 3751, 0), "Hobgoblin mine (lvl 30 Wildy)", "Hobgoblin mine", true),
    FOSSIL_ISLAND_MINE(new WorldPoint(3774, 3815, 0), "Fossil Island rune rocks", "Fossil Island", false),
    TAVERLEY__WHITE_WOLF_TUNNEL_ENTRANCE(new WorldPoint(2884, 3472, 0), "Taverley house portal", "Taverley", false),
    FELDIP_HUNTER_AREA(new WorldPoint(2573, 2965, 0), "Feldip Hills (aks fairy ring)", "Feldip Hills", false),
    RELLEKKA_MINE(new WorldPoint(2680, 3701, 0), "Rellekka mine", "Rellekka mine", false),
    TRAHAEARN_MINE_ENTRANCE(new WorldPoint(2552, 3294, 0), "Prifddinas Zalcano entrance", "Prifddinas Zalcano", false),
    WEST_LUMBRIDGE_SWAMP_MINE(new WorldPoint(3156, 3154, 0), "West Lumbridge Swamp mine", "W Lumbridge Swamp", false),
    RANTZS_CAVE(new WorldPoint(2630, 2991, 0), "Rantz cave", "Rantz cave", false),
    MYNYDD_MINE(new WorldPoint(2173, 3408, 0), "Mynydd nw of Prifddinas", "Mynydd", false),
    SOUTH_EAST_VARROCK_MINE(new WorldPoint(3292, 3353, 0), "Southeast Varrock mine", "SE Varrock mine", false),
    CRAFTING_GUILD(new WorldPoint(2939, 3282, 0), "Crafting guild", "Crafting Guild", false),
    LEGENDS_GUILD_MINE(new WorldPoint(2703, 3332, 0), "South of Legends' Guild", "Legends Guild", false),
    ISAFDAR_MINE(new WorldPoint(2271, 3157, 0), "Isafdar runite rocks", "Isafdar", false),
    CATHERBY_BANK(new WorldPoint(2804, 3438, 0), "Catherby bank", "Catherby", false),
    CORSAIR_COVE(new WorldPoint(2566, 2858, 0), "Corsair Cove bank", "Corsair Cove bank", false),
    CORSAIR_COVE_RESOURCE_AREA(new WorldPoint(2482, 2881, 0), "Corsair Resource Area", "Corsair Resource Area", false),
    COAL_TRUCKS(new WorldPoint(2594, 3479, 0), "Coal Trucks west of Seers'", "Seers Village", false),
    KELDAGRIM_ENTRANCE_MINE(new WorldPoint(2725, 3683, 0), "Keldagrim entrance mine", "Keldagrim entrance", false),
    PISCATORIS_MINE(new WorldPoint(2342, 3632, 0), "Piscatoris mine", "Piscatoris mine", false),
    SOUTH_BRIMHAVEN_MINE(new WorldPoint(2743, 3145, 0), "Southwest of Brimhaven Poh", "SW Brimhaven", false),
    AL_KHARID_MINE(new WorldPoint(3300, 3299, 0), "Al Kharid mine", "Al Kharid mine", false),
    SOUTH_WILDERNESS_MINE__MAGE_OF_ZAMORAK(new WorldPoint(3110, 3570, 0), "Mage of Zamorak mine (lvl 7 Wildy)", "Mage of Zamorak mine", true),
    EMIRS_ARENA(new WorldPoint(3352, 3277, 0), "North of Al Kharid PvP Arena", "Emirs Arena", false),
    AL_KHARID__BANK(new WorldPoint(3275, 3166, 0), "Al Kharid Bank", "Al Kharid Bank", false),
    MAGE_ARENA(new WorldPoint(3093, 3961, 0), "Mage Arena bank (lvl 56 Wildy)", "Mage Arena bank", true),
    MYTHS_GUILD(new WorldPoint(2468, 2846, 0), "Myths' Guild", "Myths' Guild", false),
    VARROCK__EAST_BANK(new WorldPoint(3260, 3412, 0), "Varrock east bank", "Varrock east bank", false),
    SOUTH_EAST_ARDOUGNE_MINE__MONASTERY(new WorldPoint(2607, 3229, 0), "Ardougne Monastery", "Ardougne Monastery", false),
    DENSE_ESSENCE_MINE(new WorldPoint(1761, 3854, 0), "Arceuus dense essence mine", "Arceuus Essence mine", false),
    TREE_GNOME_STRONGHOLD_BANK(new WorldPoint(2454, 3435, 0), "Gnome Stronghold spirit tree", "Gnome Stronghold", false),
    SOUTH_WEST_VARROCK_MINE(new WorldPoint(3180, 3365, 0), "Champions' Guild mine", "Champions' Guild", false),
    EAST_LUMBRIDGE_SWAMP_MINE(new WorldPoint(3232, 3152, 0), "East Lumbridge Swamp mine", "E Lumbridge Swamp", false),
    RIMMINGTON_MINE(new WorldPoint(2974, 3243, 0), "Rimmington mine", "Rimmington", false),
    BURGH_DE_ROTT__BANK(new WorldPoint(3497, 3220, 0), "Burgh de Rott bank", "Burgh de Rott", false),
    KARAMJA_JUNGLE_MINE__NATURE_ALTAR(new WorldPoint(2843, 3033, 0), "Nature Altar mine north of Shilo", "Nature Altar", false),
    SHILO_VILLAGE_MINE(new WorldPoint(2825, 2997, 0), "Shilo Village gem mine", "Shilo Gem Mine", false),
    RESOURCE_AREA(new WorldPoint(3188, 3935, 0), "Wilderness Resource Area", "Wilderness Resource Area", true),
    JATIZSO_MINE_ENTRANCE(new WorldPoint(2392, 3811, 0), "Jatizso mine entrance", "Jatizso", false),
    CENTRAL_FREMENNIK_ISLES_MINE(new WorldPoint(2372, 3833, 0), "Neitiznot south of rune rock", "Neitiznot", false),
    MOUNT_KARUULM_MINE(new WorldPoint(1276, 3811, 0), "Mount Karuulm mine", "Mount Karuulm mine", false),
    KEBOS_LOWLANDS_MINE__KEBOS_SWAMP(new WorldPoint(1210, 3651, 0), "Kebos Swamp mine", "Kebos Swamp mine", false),
    CANIFIS__BANK(new WorldPoint(3503, 3483, 0), "Canifis bank", "Canifis bank", false),
    LLETYA(new WorldPoint(2318, 3268, 0), "Lletya", "Lletya", false),
    HOSIDIUS_MINE(new WorldPoint(1781, 3491, 0), "Hosidius mine", "Hosidius mine", false),
    PORT_PISCARILIUS_MINE(new WorldPoint(1765, 3707, 0), "Port Piscarilius mine in Kourend", "Piscarilius mine", false),
    LOVAKENGJ__BANK(new WorldPoint(1534, 3756, 0), "South Lovakengj bank", "S Lovakengj bank", false),
    ABANDONED_MINE(new WorldPoint(3452, 3240, 0), "Abandoned Mine west of Burgh", "Abandoned Mine", false),
    DESERT_QUARRY(new WorldPoint(3176, 2909, 0), "Desert Quarry mine", "Desert Quarry mine", false),
    NORTH_CRANDOR_MINE(new WorldPoint(2837, 3294, 0), "North Crandor", "North Crandor", false),
    NORTH_BRIMHAVEN_MINE(new WorldPoint(2733, 3220, 0), "Brimhaven northwest gold mine", "Brimhaven gold mine", false),
    LOVAKITE_MINE(new WorldPoint(1437, 3839, 0), "Lovakite mine", "Lovakite mine", false),
    ISLE_OF_SOULS_MINE(new WorldPoint(2201, 2791, 0), "Soul Wars south mine", "Soul Wars south mine", false),
    SOUTH_CRANDOR_MINE(new WorldPoint(2821, 3240, 0), "South Crandor", "South Crandor", false),
    WEST_FALADOR_MINE(new WorldPoint(2908, 3354, 0), "West Falador mine", "West Falador mine", false),
    DAEYALT_ESSENCE_MINE_ENTRANCE(new WorldPoint(3635, 3338, 0), "Darkmeyer ess. mine entrance", "Darkmeyer mine", false),
    VER_SINHAZA__BANK(new WorldPoint(3560, 3212, 0), "Theatre of Blood bank", "ToB bank", false),
    MISCELLANIA_MINE(new WorldPoint(2530, 3887, 0), "Miscellania mine (cip fairy ring)", "Miscellania mine", false),
    SOUTH_WEST_WILDERNESS_MINE(new WorldPoint(3019, 3593, 0), "Skeleton mine (lvl 10 Wildy)", "Skeleton mine", true),
    MOS_LEHARMLESS(new WorldPoint(3683, 2969, 0), "Mos Le'Harmless west bank", "Mos Le'Harmless", false),
    LUNAR_ISLE_MINE_ENTRANCE(new WorldPoint(2140, 3940, 0), "Lunar Isle mine entrance", "Lunar Isle", false),
    MINING_GUILD_ENTRANCE(new WorldPoint(3030, 3347, 0), "East Falador bank", "E Falador bank", false),
    VARLAMORE_SOUTH_EAST_MINE(new WorldPoint(1742, 2957, 0), "Varlamore South East mine", "SE Varlamore mine", false),
    VARLAMORE_COLOSSEUM_ENTRANCE_BANK(new WorldPoint(1771, 3107, 0), "Varlamore colosseum entrance bank", "Colosseum entrance", false),
    MINE_NORTH_WEST_OF_HUNTER_GUILD(new WorldPoint(1486, 3093, 0), "Mine northwest of hunter guild", "Hunter Guild mine", false),
    PIRATES_HIDEOUT_MINE(new WorldPoint(3049, 3945, 0), "Pirates' Hideout (lvl 53 Wildy)", "Pirates' Hideout", true),
    LAVA_MAZE_RUNITE_MINE(new WorldPoint(3057, 3890, 0), "Lava maze runite mine (lvl 46 Wildy)", "Lava maze", true);

    private final WorldPoint worldPoint;
    private final String rawLocationName;
    private final String shortLocationName;
    private final boolean isInWilderness;

    public String getLocationName() {
        return getShortLocationName();
    }

    public boolean hasRequirements() {

        if (!Microbot.isLoggedIn()) return false;

        boolean hasLineOfSight = Microbot.getClient().getLocalPlayer().getWorldArea().hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), this.getWorldPoint());
        switch (this) {
            case CRAFTING_GUILD:
                // Requires Crafting Guild Items
                boolean isWearingCraftingGuild = (Rs2Equipment.isWearing("brown apron") || Rs2Equipment.isWearing("golden apron") ||
                        Rs2Equipment.isWearing("crafting cape") || Rs2Equipment.isWearing("crafting hood") ||
                        Rs2Equipment.isWearing("max cape") || Rs2Equipment.isWearing("max hood"));
                
                // If has line of sight (already in crafting guild)
                if (hasLineOfSight) return true;
                return isWearingCraftingGuild;
            case MYTHS_GUILD:
                // Requires Dragon Slayer II Quest
                return Rs2Player.getQuestState(Quest.DRAGON_SLAYER_II) == QuestState.FINISHED;
            case JATIZSO_MINE_ENTRANCE:
            case CENTRAL_FREMENNIK_ISLES_MINE:
                // Requires The Fremennik Trials & The Fremennik Isles
                return (Rs2Player.getQuestState(Quest.THE_FREMENNIK_TRIALS) == QuestState.FINISHED) && 
                        (Rs2Player.getQuestState(Quest.THE_FREMENNIK_ISLES) == QuestState.IN_PROGRESS || Rs2Player.getQuestState(Quest.THE_FREMENNIK_ISLES) == QuestState.FINISHED);
            case MOS_LEHARMLESS:
                // Requires Cabin Fever Quest
                return Rs2Player.getQuestState(Quest.CABIN_FEVER) == QuestState.FINISHED;
            case SOUTH_CRANDOR_MINE:
            case NORTH_CRANDOR_MINE:
                // Requires start of Dragon Slayer I
                return Rs2Player.getQuestState(Quest.DRAGON_SLAYER_I) == QuestState.IN_PROGRESS || 
                        Rs2Player.getQuestState(Quest.DRAGON_SLAYER_I) == QuestState.FINISHED;
            case FOSSIL_ISLAND_MINE:
            case VOLCANIC_MINE_ENTRANCE:
                // Requires Bone Voyage
                return Rs2Player.getQuestState(Quest.BONE_VOYAGE) == QuestState.FINISHED;
            case LLETYA:
            case ISAFDAR_MINE:
                // Requires Mournings End Part I
                return Rs2Player.getQuestState(Quest.MOURNINGS_END_PART_I) == QuestState.IN_PROGRESS || 
                        Rs2Player.getQuestState(Quest.MOURNINGS_END_PART_I) == QuestState.FINISHED;
            case BURGH_DE_ROTT__BANK:
                // Requires Priest in Peril & In Aid of the Myreque
                return Rs2Player.getQuestState(Quest.PRIEST_IN_PERIL) == QuestState.FINISHED && Rs2Player.getQuestState(Quest.IN_AID_OF_THE_MYREQUE) == QuestState.FINISHED;
            case MYNYDD_MINE:
            case TRAHAEARN_MINE_ENTRANCE:
                // Requires Song of the Elves
                return Rs2Player.getQuestState(Quest.SONG_OF_THE_ELVES) == QuestState.FINISHED;
            case SHILO_VILLAGE_MINE:
                // Requires Shilo Village
                return Rs2Player.getQuestState(Quest.SHILO_VILLAGE) == QuestState.FINISHED;
            case DAEYALT_ESSENCE_MINE_ENTRANCE:
                // Requires Sins of the Father
                return Rs2Player.getQuestState(Quest.SINS_OF_THE_FATHER) == QuestState.FINISHED;
            case VER_SINHAZA__BANK:
                // Requires Priest In Peril
                return Rs2Player.getQuestState(Quest.PRIEST_IN_PERIL) == QuestState.FINISHED;
            case MISCELLANIA_MINE:
                // Requires The Fremennik Trials
                return Rs2Player.getQuestState(Quest.THE_FREMENNIK_TRIALS) == QuestState.FINISHED;
            case LUNAR_ISLE_MINE_ENTRANCE:
                // Requires Lunar Diplomacy & The Fremennik Trials
                return Rs2Player.getQuestState(Quest.THE_FREMENNIK_TRIALS) == QuestState.FINISHED && 
                        (Rs2Player.getQuestState(Quest.LUNAR_DIPLOMACY) == QuestState.IN_PROGRESS || Rs2Player.getQuestState(Quest.LUNAR_DIPLOMACY) == QuestState.FINISHED);
            case VARLAMORE_SOUTH_EAST_MINE:
            case VARLAMORE_COLOSSEUM_ENTRANCE_BANK:
            case MINE_NORTH_WEST_OF_HUNTER_GUILD:
                // Requires Children of the Sun
                return Rs2Player.getQuestState(Quest.CHILDREN_OF_THE_SUN) == QuestState.FINISHED;
            case CORSAIR_COVE:
                // Requires The Corsair Curse if not Member
                if (!Rs2Player.isMember()) {
                    return (Rs2Player.getQuestState(Quest.THE_CORSAIR_CURSE) == QuestState.IN_PROGRESS || Rs2Player.getQuestState(Quest.THE_CORSAIR_CURSE) == QuestState.FINISHED);
                }
                return true;
            case CORSAIR_COVE_RESOURCE_AREA:
                // Requires Dragon Slayer I
                return Rs2Player.getQuestState(Quest.DRAGON_SLAYER_I) == QuestState.FINISHED;
            case CANIFIS__BANK:
                return Rs2Player.getQuestState(Quest.PRIEST_IN_PERIL) == QuestState.FINISHED;
            default:
                return true;
        }
    }
}
