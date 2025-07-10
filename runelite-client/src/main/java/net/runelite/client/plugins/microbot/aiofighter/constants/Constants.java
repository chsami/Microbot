package net.runelite.client.plugins.microbot.aiofighter.constants;

import com.google.common.collect.ImmutableSet;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;

import java.util.Set;

public class Constants {

    public static final int CRAFTING_GUILD_REGION = 11571;

    public static final Set<Integer> LOG_IDS = ImmutableSet.of(
            ItemID.LOGS,
            ItemID.OAK_LOGS,
            ItemID.WILLOW_LOGS,
            ItemID.TEAK_LOGS,
            ItemID.MAPLE_LOGS,
            ItemID.MAHOGANY_LOGS,
            ItemID.YEW_LOGS,
            ItemID.MAGIC_LOGS,
            ItemID.REDWOOD_LOGS
    );

    public static final Set<Integer> DIGSITE_PENDANT_IDS = ImmutableSet.of(
            ItemID.NECKLACE_OF_DIGSITE_1,
            ItemID.NECKLACE_OF_DIGSITE_2,
            ItemID.NECKLACE_OF_DIGSITE_3,
            ItemID.NECKLACE_OF_DIGSITE_4,
            ItemID.NECKLACE_OF_DIGSITE_5
    );

    public static final int BIRD_HOUSE_EMPTY_SPACE = ObjectID.BIRDHOUSE_NOT_BUILT;

    public static final int MEADOW_NORTH_SPACE = ObjectID.BIRDHOUSE_1;
    public static final int MEADOW_SOUTH_SPACE = ObjectID.BIRDHOUSE_2;
    public static final int VERDANT_NORTH_SPACE = ObjectID.BIRDHOUSE_3;
    public static final int VERDANT_SOUTH_SPACE = ObjectID.BIRDHOUSE_4;

    public static final Set<Integer> BIRD_HOUSE_SPACES = ImmutableSet.of(
            MEADOW_NORTH_SPACE,
            MEADOW_SOUTH_SPACE,
            VERDANT_NORTH_SPACE,
            VERDANT_SOUTH_SPACE
    );

    public static final Set<Integer> BIRD_HOUSE_IDS = ImmutableSet.of(
            ObjectID.BIRDHOUSE_NORMAL_FULL,
            ObjectID.BIRDHOUSE_NORMAL_BIRD,
            ObjectID.BIRDHOUSE_OAK_FULL,
            ObjectID.BIRDHOUSE_OAK_BIRD,
            ObjectID.BIRDHOUSE_WILLOW_FULL,
            ObjectID.BIRDHOUSE_WILLOW_BIRD,
            ObjectID.BIRDHOUSE_TEAK_FULL,
            ObjectID.BIRDHOUSE_TEAK_BIRD,
            ObjectID.BIRDHOUSE_MAPLE_FULL,
            ObjectID.BIRDHOUSE_MAPLE_BIRD,
            ObjectID.BIRDHOUSE_MAHOGANY_FULL,
            ObjectID.BIRDHOUSE_MAHOGANY_BIRD,
            ObjectID.BIRDHOUSE_YEW_FULL,
            ObjectID.BIRDHOUSE_YEW_BIRD,
            ObjectID.BIRDHOUSE_MAGIC_FULL,
            ObjectID.BIRDHOUSE_MAGIC_BIRD,
            ObjectID.BIRDHOUSE_REDWOOD_FULL,
            ObjectID.BIRDHOUSE_REDWOOD_BIRD
    );

    public static final Set<Integer> BIRD_HOUSE_EMPTY_IDS = ImmutableSet.of(
            ObjectID.BIRDHOUSE_NORMAL_BUILT,
            ObjectID.BIRDHOUSE_OAK_BUILT,
            ObjectID.BIRDHOUSE_WILLOW_BUILT,
            ObjectID.BIRDHOUSE_TEAK_BUILT,
            ObjectID.BIRDHOUSE_MAPLE_BUILT,
            ObjectID.BIRDHOUSE_MAHOGANY_BUILT,
            ObjectID.BIRDHOUSE_YEW_BUILT,
            ObjectID.BIRDHOUSE_MAGIC_BUILT,
            ObjectID.BIRDHOUSE_REDWOOD_BUILT
    );

    public static final Set<Integer> BIRD_HOUSE_ITEM_IDS = ImmutableSet.of(
            ItemID.BIRDHOUSE_NORMAL,
            ItemID.BIRDHOUSE_OAK,
            ItemID.BIRDHOUSE_WILLOW,
            ItemID.BIRDHOUSE_TEAK,
            ItemID.BIRDHOUSE_MAPLE,
            ItemID.BIRDHOUSE_MAHOGANY,
            ItemID.BIRDHOUSE_YEW,
            ItemID.BIRDHOUSE_MAGIC,
            ItemID.BIRDHOUSE_REDWOOD
    );

    public static final Set<Integer> BIRD_HOUSE_SEED_IDS = ImmutableSet.of(
            ItemID.BARLEY_SEED,
            ItemID.HAMMERSTONE_HOP_SEED,
            ItemID.ASGARNIAN_HOP_SEED,
            ItemID.JUTE_SEED,
            ItemID.YANILLIAN_HOP_SEED,
            ItemID.KRANDORIAN_HOP_SEED
    );

    public static final Set<Integer> BIRD_NEST_IDS = ImmutableSet.of(
            ItemID.BIRD_NEST_EGG_RED,
            ItemID.BIRD_NEST_EGG_GREEN,
            ItemID.BIRD_NEST_EGG_BLUE,
            ItemID.BIRD_NEST_SEEDS,
            ItemID.BIRD_NEST_RING,
            ItemID.BIRD_NEST_EMPTY,
            ItemID.CRUSHED_BIRD_NEST,
            ItemID.BIRD_NEST_CHEAPSEEDS,
            ItemID.BIRD_NEST_DECENTSEEDS,
            ItemID.BIRD_NEST_SEEDS_JAN2019,
            ItemID.BIRD_NEST_DECENTSEEDS_JAN2019,
            ItemID.WC_CLUE_NEST_EASY,
            ItemID.WC_CLUE_NEST_MEDIUM,
            ItemID.WC_CLUE_NEST_HARD,
            ItemID.WC_CLUE_NEST_ELITE
    );

    public static final Set<Integer> MAGIC_MUSHTREE_IDS = ImmutableSet.of(
            ObjectID.FOSSIL_MAGIC_MUSHTREE_TRUNK1,
            ObjectID.FOSSIL_MAGIC_MUSHTREE_TRUNK2,
            ObjectID.FOSSIL_MAGIC_MUSHTREE_TRUNK3
    );

    public static final Set<Integer> ESSENCE_IDS = ImmutableSet.of(
            ItemID.BLANKRUNE,
            ItemID.BLANKRUNE_HIGH,
            ItemID.BLANKRUNE_DAEYALT
    );

    public static final Set<Integer> RUNE_IDS = ImmutableSet.of(
            ItemID.AIRRUNE,
            ItemID.MINDRUNE,
            ItemID.WATERRUNE,
            ItemID.EARTHRUNE,
            ItemID.FIRERUNE,
            ItemID.BODYRUNE,
            ItemID.COSMICRUNE,
            ItemID.CHAOSRUNE,
            ItemID.NATURERUNE,
            ItemID.LAWRUNE,
            ItemID.DEATHRUNE,
            ItemID.ASTRALRUNE,
            ItemID.BLOODRUNE,
            ItemID.SOULRUNE,
            ItemID.WRATHRUNE
    );

    public static final Set<Integer> ANVIL_IDS = ImmutableSet.of(
            ObjectID.DORICS_ANVIL,
            ObjectID.ANVIL,
            ObjectID.EXPERIMENTAL_ANVIL,
            ObjectID.VIKING_ANVIL,
            ObjectID.DWARF_KELDAGRIM_ANVIL,
            ObjectID.DORGESH_BLACKSMITH_ANVIL,
            ObjectID.BRUT_ANVIL,
            ObjectID.LOVAKENGJ_ANVIL,
            ObjectID.LOVAKENGJ_ANVIL_ONLOG,
            ObjectID.WINT_ANVIL,
            ObjectID.RAIDS_TEKTON_ANVIL,
            ObjectID.DS2_GUILD_BLACKSMITH_ANVIL,
            ObjectID.DS2_AC_FORGE_ANVIL,
            ObjectID.DS2_AC_FORGE_ANVIL_UNLIT,
            ObjectID.DARKM_ANVIL,
            ObjectID.LUMBRIDGE_ANVIL,
            ObjectID.GH_ANVIL,
            ObjectID.SW_ANVIL,
            ObjectID.GIM_ANVIL,
            ObjectID.LOVAKENGJ_ANVIL_NOOP
    );

    public static final Set<Integer> STAMINA_POTION_IDS = ImmutableSet.of(
            ItemID._4DOSESTAMINA,
            ItemID._3DOSESTAMINA,
            ItemID._2DOSESTAMINA,
            ItemID._1DOSESTAMINA
    );

    public static final Set<Integer> BREW_POTION_IDS = ImmutableSet.of(
            ItemID._1DOSEPOTIONOFSARADOMIN,
            ItemID._2DOSEPOTIONOFSARADOMIN,
            ItemID._3DOSEPOTIONOFSARADOMIN,
            ItemID._4DOSEPOTIONOFSARADOMIN,
            ItemID.RAIDS_VIAL_XERICAID_WEAK_1,
            ItemID.RAIDS_VIAL_XERICAID_WEAK_2,
            ItemID.RAIDS_VIAL_XERICAID_WEAK_3,
            ItemID.RAIDS_VIAL_XERICAID_WEAK_4,
            ItemID.RAIDS_VIAL_XERICAID_1,
            ItemID.RAIDS_VIAL_XERICAID_2,
            ItemID.RAIDS_VIAL_XERICAID_3,
            ItemID.RAIDS_VIAL_XERICAID_4,
            ItemID.RAIDS_VIAL_XERICAID_STRONG_1,
            ItemID.RAIDS_VIAL_XERICAID_STRONG_2,
            ItemID.RAIDS_VIAL_XERICAID_STRONG_3,
            ItemID.RAIDS_VIAL_XERICAID_STRONG_4
    );

    public static final Set<Integer> RESTORE_POTION_IDS = ImmutableSet.of(
            ItemID._1DOSESTATRESTORE,
            ItemID._2DOSESTATRESTORE,
            ItemID._3DOSESTATRESTORE,
            ItemID._4DOSESTATRESTORE,
            ItemID._1DOSE2RESTORE,
            ItemID._2DOSE2RESTORE,
            ItemID._3DOSE2RESTORE,
            ItemID._4DOSE2RESTORE
    );

    public static final Set<Integer> ANTI_POISON_POTION_IDS = ImmutableSet.of(
            ItemID._3DOSEANTIPOISON,
            ItemID._2DOSEANTIPOISON,
            ItemID._1DOSEANTIPOISON,
            ItemID._3DOSE2ANTIPOISON,
            ItemID._2DOSE2ANTIPOISON,
            ItemID._1DOSE2ANTIPOISON,
            ItemID._4DOSEANTIPOISON,
            ItemID._4DOSE2ANTIPOISON,
            ItemID.ANTIDOTE_4,
            ItemID.ANTIDOTE_3,
            ItemID.ANTIDOTE_2,
            ItemID.ANTIDOTE_1,
            ItemID.ANTIDOTE__4,
            ItemID.ANTIDOTE__3,
            ItemID.ANTIDOTE__2,
            ItemID.ANTIDOTE__1,
            ItemID.ANTIVENOM4,
            ItemID.ANTIVENOM3,
            ItemID.ANTIVENOM2,
            ItemID.ANTIVENOM1,
            ItemID.ANTIVENOM_4,
            ItemID.ANTIVENOM_3,
            ItemID.ANTIVENOM_2,
            ItemID.ANTIVENOM_1
    );

    public static final Set<Integer> ANTI_FIRE_POTION_IDS = ImmutableSet.of(
            ItemID._1DOSE1ANTIDRAGON,
            ItemID._2DOSE1ANTIDRAGON,
            ItemID._3DOSE1ANTIDRAGON,
            ItemID._4DOSE1ANTIDRAGON,
            ItemID._1DOSE2ANTIDRAGON,
            ItemID._2DOSE2ANTIDRAGON,
            ItemID._3DOSE2ANTIDRAGON,
            ItemID._4DOSE2ANTIDRAGON,
            ItemID._1DOSE3ANTIDRAGON,
            ItemID._2DOSE3ANTIDRAGON,
            ItemID._3DOSE3ANTIDRAGON,
            ItemID._4DOSE3ANTIDRAGON,
            ItemID._1DOSE4ANTIDRAGON,
            ItemID._2DOSE4ANTIDRAGON,
            ItemID._3DOSE4ANTIDRAGON,
            ItemID._4DOSE4ANTIDRAGON
    );

    public static final Set<Integer> PRAYER_RESTORE_POTION_IDS = ImmutableSet.of(
            ItemID._1DOSE2RESTORE,
            ItemID._2DOSE2RESTORE,
            ItemID._3DOSE2RESTORE,
            ItemID._4DOSE2RESTORE,
            ItemID._1DOSEPRAYERRESTORE,
            ItemID._2DOSEPRAYERRESTORE,
            ItemID._3DOSEPRAYERRESTORE,
            ItemID._4DOSEPRAYERRESTORE
    );

    public static final Set<Integer> STRENGTH_POTION_IDS = ImmutableSet.of(
            ItemID.STRENGTH4,
            ItemID._3DOSE1STRENGTH,
            ItemID._2DOSE1STRENGTH,
            ItemID._1DOSE1STRENGTH,
            ItemID._3DOSE2STRENGTH,
            ItemID._2DOSE2STRENGTH,
            ItemID._1DOSE2STRENGTH,
            ItemID._4DOSE2STRENGTH,
            ItemID._4DOSECOMBAT,
            ItemID._3DOSECOMBAT,
            ItemID._2DOSECOMBAT,
            ItemID._1DOSECOMBAT,
            ItemID._4DOSE2COMBAT,
            ItemID._3DOSE2COMBAT,
            ItemID._2DOSE2COMBAT,
            ItemID._1DOSE2COMBAT,
            ItemID._4DOSEDIVINECOMBAT,
            ItemID._3DOSEDIVINECOMBAT,
            ItemID._2DOSEDIVINECOMBAT,
            ItemID._1DOSEDIVINECOMBAT,
            ItemID._4DOSEDIVINESTRENGTH,
            ItemID._3DOSEDIVINESTRENGTH,
            ItemID._2DOSEDIVINESTRENGTH,
            ItemID._1DOSEDIVINESTRENGTH
    );

    public static final Set<Integer> ATTACK_POTION_IDS = ImmutableSet.of(
            ItemID._1DOSE1ATTACK,
            ItemID._2DOSE1ATTACK,
            ItemID._3DOSE1ATTACK,
            ItemID._4DOSE1ATTACK,
            ItemID._1DOSE2ATTACK,
            ItemID._2DOSE2ATTACK,
            ItemID._3DOSE2ATTACK,
            ItemID._4DOSE2ATTACK,
            ItemID._1DOSEDIVINEATTACK,
            ItemID._2DOSEDIVINEATTACK,
            ItemID._3DOSEDIVINEATTACK,
            ItemID._4DOSEDIVINEATTACK,
            ItemID._1DOSEDIVINECOMBAT,
            ItemID._2DOSEDIVINECOMBAT,
            ItemID._3DOSEDIVINECOMBAT,
            ItemID._4DOSEDIVINECOMBAT,
            ItemID._1DOSECOMBAT,
            ItemID._2DOSECOMBAT,
            ItemID._3DOSECOMBAT,
            ItemID._4DOSECOMBAT,
            ItemID._1DOSE2COMBAT,
            ItemID._2DOSE2COMBAT,
            ItemID._3DOSE2COMBAT,
            ItemID._4DOSE2COMBAT
    );

    public static final Set<Integer> DEFENCE_POTION_IDS = ImmutableSet.of(
            ItemID._1DOSE1DEFENSE,
            ItemID._2DOSE1DEFENSE,
            ItemID._3DOSE1DEFENSE,
            ItemID._4DOSE1DEFENSE,
            ItemID._1DOSE2DEFENSE,
            ItemID._2DOSE2DEFENSE,
            ItemID._3DOSE2DEFENSE,
            ItemID._4DOSE2DEFENSE,
            ItemID._1DOSEDIVINEDEFENCE,
            ItemID._2DOSEDIVINEDEFENCE,
            ItemID._3DOSEDIVINEDEFENCE,
            ItemID._4DOSEDIVINEDEFENCE,
            ItemID._1DOSEDIVINECOMBAT,
            ItemID._2DOSEDIVINECOMBAT,
            ItemID._3DOSEDIVINECOMBAT,
            ItemID._4DOSEDIVINECOMBAT,
            ItemID._1DOSE2COMBAT,
            ItemID._2DOSE2COMBAT,
            ItemID._3DOSE2COMBAT,
            ItemID._4DOSE2COMBAT
    );

    public static final Set<Integer> RANGED_POTION_IDS = ImmutableSet.of(
            ItemID._1DOSERANGERSPOTION,
            ItemID._2DOSERANGERSPOTION,
            ItemID._3DOSERANGERSPOTION,
            ItemID._4DOSERANGERSPOTION,
            ItemID._1DOSEBASTION,
            ItemID._2DOSEBASTION,
            ItemID._3DOSEBASTION,
            ItemID._4DOSEBASTION,
            ItemID._1DOSEDIVINERANGE,
            ItemID._2DOSEDIVINERANGE,
            ItemID._3DOSEDIVINERANGE,
            ItemID._4DOSEDIVINERANGE,
            ItemID._1DOSEDIVINEBASTION,
            ItemID._2DOSEDIVINEBASTION,
            ItemID._3DOSEDIVINEBASTION,
            ItemID._4DOSEDIVINEBASTION,
            ItemID.NZONE1DOSE2RANGERSPOTION,
            ItemID.NZONE2DOSE2RANGERSPOTION,
            ItemID.NZONE3DOSE2RANGERSPOTION,
            ItemID.NZONE4DOSE2RANGERSPOTION
    );

    public static final Set<Integer> MAGIC_POTION_IDS = ImmutableSet.of(
            ItemID._1DOSE1MAGIC,
            ItemID._2DOSE1MAGIC,
            ItemID._3DOSE1MAGIC,
            ItemID._4DOSE1MAGIC,
            ItemID._1DOSEBATTLEMAGE,
            ItemID._2DOSEBATTLEMAGE,
            ItemID._3DOSEBATTLEMAGE,
            ItemID._4DOSEBATTLEMAGE,
            ItemID._1DOSEDIVINEMAGIC,
            ItemID._2DOSEDIVINEMAGIC,
            ItemID._3DOSEDIVINEMAGIC,
            ItemID._4DOSEDIVINEMAGIC,
            ItemID._1DOSEDIVINEBATTLEMAGE,
            ItemID._2DOSEDIVINEBATTLEMAGE,
            ItemID._3DOSEDIVINEBATTLEMAGE,
            ItemID._4DOSEDIVINEBATTLEMAGE
    );

    public static final Set<Integer> ENERGY_POTION_IDS = ImmutableSet.of(
            ItemID._1DOSE1ENERGY,
            ItemID._2DOSE1ENERGY,
            ItemID._3DOSE1ENERGY,
            ItemID._4DOSE1ENERGY,
            ItemID.BRUTAL_1DOSE1ENERGY,
            ItemID.BRUTAL_2DOSE1ENERGY,
            ItemID._1DOSE2ENERGY,
            ItemID._2DOSE2ENERGY,
            ItemID._3DOSE2ENERGY,
            ItemID._4DOSE2ENERGY,
            ItemID.BRUTAL_1DOSE2ENERGY,
            ItemID.BRUTAL_2DOSE2ENERGY
    );

    public static final Set<Integer> MINEABLE_GEM_IDS = ImmutableSet.of(
            ItemID.UNCUT_SAPPHIRE,
            ItemID.UNCUT_EMERALD,
            ItemID.UNCUT_RUBY,
            ItemID.UNCUT_DIAMOND
    );

    public static final Set<Integer> ESSENCE_POUCH_IDS = ImmutableSet.of(
            ItemID.RCU_POUCH_COLOSSAL,
            ItemID.RCU_POUCH_COLOSSAL_DEGRADE,
            ItemID.RCU_POUCH_GIANT,
            ItemID.RCU_POUCH_GIANT_DEGRADE,
            ItemID.RCU_POUCH_LARGE,
            ItemID.RCU_POUCH_LARGE_DEGRADE,
            ItemID.RCU_POUCH_MEDIUM,
            ItemID.RCU_POUCH_MEDIUM_DEGRADE,
            ItemID.RCU_POUCH_SMALL
    );

    public static final Set<Integer> DEGRADED_ESSENCE_POUCH_IDS = ImmutableSet.of(
            ItemID.RCU_POUCH_COLOSSAL_DEGRADE,
            ItemID.RCU_POUCH_GIANT_DEGRADE,
            ItemID.RCU_POUCH_LARGE_DEGRADE,
            ItemID.RCU_POUCH_MEDIUM_DEGRADE
    );

    public static final Set<Integer> ARDOUGNE_CLOAK_IDS = ImmutableSet.of(
            ItemID.ARDY_CAPE_EASY,
            ItemID.ARDY_CAPE_MEDIUM,
            ItemID.ARDY_CAPE_HARD,
            ItemID.ARDY_CAPE_ELITE,
            ItemID.SKILLCAPE_MAX_ARDY,
            ItemID.CERT_ARRAVCERTIFICATE
    );

    public static final Set<Integer> DUELING_RING_IDS = ImmutableSet.of(
            ItemID.RING_OF_DUELING_1,
            ItemID.RING_OF_DUELING_2,
            ItemID.RING_OF_DUELING_3,
            ItemID.RING_OF_DUELING_4,
            ItemID.RING_OF_DUELING_5,
            ItemID.RING_OF_DUELING_6,
            ItemID.RING_OF_DUELING_7,
            ItemID.RING_OF_DUELING_8
    );

    public static final Set<Integer> EXPLORERS_RING_IDS = ImmutableSet.of(
            ItemID.LUMBRIDGE_RING_MEDIUM,
            ItemID.LUMBRIDGE_RING_HARD,
            ItemID.LUMBRIDGE_RING_ELITE
    );

    public static final Set<Integer> AMULET_OF_GLORY_IDS = ImmutableSet.of(
            ItemID.AMULET_OF_GLORY_1,
            ItemID.AMULET_OF_GLORY_2,
            ItemID.AMULET_OF_GLORY_3,
            ItemID.AMULET_OF_GLORY_4,
            ItemID.AMULET_OF_GLORY_5,
            ItemID.AMULET_OF_GLORY_6,
            ItemID.TRAIL_AMULET_OF_GLORY_1,
            ItemID.TRAIL_AMULET_OF_GLORY_2,
            ItemID.TRAIL_AMULET_OF_GLORY_3,
            ItemID.TRAIL_AMULET_OF_GLORY_4,
            ItemID.TRAIL_AMULET_OF_GLORY_5,
            ItemID.TRAIL_AMULET_OF_GLORY_6,
            ItemID.AMULET_OF_GLORY_INF,
            ItemID.BR_AMULET_OF_GLORY
    );

    public static final Set<Integer> SKILL_NECKLACE_IDS = ImmutableSet.of(
            ItemID.JEWL_NECKLACE_OF_SKILLS_1,
            ItemID.JEWL_NECKLACE_OF_SKILLS_2,
            ItemID.JEWL_NECKLACE_OF_SKILLS_3,
            ItemID.JEWL_NECKLACE_OF_SKILLS_4,
            ItemID.JEWL_NECKLACE_OF_SKILLS_5,
            ItemID.JEWL_NECKLACE_OF_SKILLS_6
    );

    public static final Set<Integer> COMPOST_BIN_IDS = ImmutableSet.of(
            ObjectID.FARMING_COMPOST_BIN_1,
            ObjectID.FARMING_COMPOST_BIN_2,
            ObjectID.FARMING_COMPOST_BIN_3,
            ObjectID.FARMING_COMPOST_BIN_4,
            ObjectID.FARMING_COMPOST_BIN_5
    );

    public static final Set<Integer> NOTABLE_PRODUCE_IDS = ImmutableSet.of(
            ItemID.POTATO,
            ItemID.ONION,
            ItemID.TOMATO,
            ItemID.SWEETCORN,
            ItemID.STRAWBERRY,
            ItemID.WATERMELON,
            ItemID.SNAPE_GRASS,
            ItemID.MARIGOLD,
            ItemID.ROSEMARY,
            ItemID.NASTURTIUM,
            ItemID.WOADLEAF,
            ItemID.LIMPWURT_ROOT,
            ItemID.WHITELILLY,
            ItemID.UNIDENTIFIED_GUAM,
            ItemID.UNIDENTIFIED_MARENTILL,
            ItemID.UNIDENTIFIED_TARROMIN,
            ItemID.UNIDENTIFIED_HARRALANDER,
            ItemID.UNIDENTIFIED_RANARR,
            ItemID.UNIDENTIFIED_TOADFLAX,
            ItemID.UNIDENTIFIED_IRIT,
            ItemID.UNIDENTIFIED_AVANTOE,
            ItemID.UNIDENTIFIED_KWUARM,
            ItemID.UNIDENTIFIED_SNAPDRAGON,
            ItemID.UNIDENTIFIED_CADANTINE,
            ItemID.UNIDENTIFIED_LANTADYME,
            ItemID.UNIDENTIFIED_DWARF_WEED,
            ItemID.UNIDENTIFIED_TORSTOL,
            ItemID.BARLEY,
            ItemID.HAMMERSTONE_HOPS,
            ItemID.ASGARNIAN_HOPS,
            ItemID.JUTE_FIBRE,
            ItemID.YANILLIAN_HOPS,
            ItemID.KRANDORIAN_HOPS,
            ItemID.WILDBLOOD_HOPS,
            ItemID.REDBERRIES,
            ItemID.CADAVABERRIES,
            ItemID.DWELLBERRIES,
            ItemID.JANGERBERRIES,
            ItemID.WHITE_BERRIES,
            ItemID.POISONIVY_BERRIES,
            ItemID.COOKING_APPLE,
            ItemID.BANANA,
            ItemID.ORANGE,
            ItemID.CURRY_LEAF,
            ItemID.PINEAPPLE,
            ItemID.PAPAYA,
            ItemID.COCONUT,
            ItemID.DRAGONFRUIT,
            ItemID.GIANT_SEAWEED,
            ItemID.GRAPES,
            ItemID.BITTERCAP_MUSHROOM,
            ItemID.CACTUS_SPINE,
            ItemID.CACTUS_POTATO
    );

    public static final Set<Integer> TOOL_LEPRECHAUN_IDS = ImmutableSet.of(
            NpcID.FARMING_TOOLS_LEPRECHAUN,
            NpcID.MYARM_LEPRECHAUN,
            NpcID.FOSSIL_LEPRECHAUN_UNDERWATER,
            NpcID.FARMING_TOOLS_LEPRECHAUN_DRAYNOR,
            NpcID.FARMING_TOOLS_LEPRECHAUN_VARLAMORE
    );

    public static final Set<Integer> ALLOTMENT_PATCH_IDS = ImmutableSet.of(
            ObjectID.FARMING_VEG_PATCH_1, // 8550
            ObjectID.FARMING_VEG_PATCH_2, // 8551
            ObjectID.FARMING_VEG_PATCH_3, // 8552
            ObjectID.FARMING_VEG_PATCH_4, // 8553
            ObjectID.FARMING_VEG_PATCH_5, // 8554
            ObjectID.FARMING_VEG_PATCH_6, // 8555
            ObjectID.FARMING_VEG_PATCH_7, // 8556
            ObjectID.FARMING_VEG_PATCH_8, // 8557
            ObjectID.FARMING_VEG_PATCH_9, // 21950
            ObjectID.FARMING_VEG_PATCH_10, // 27113
            ObjectID.FARMING_VEG_PATCH_11, // 27114
            ObjectID.FARMING_VEG_PATCH_13, // 33693
            ObjectID.FARMING_VEG_PATCH_12 // 33694
    );

    public static final Set<Integer> FLOWER_PATCH_IDS = ImmutableSet.of(
            ObjectID.FARMING_FLOWER_PATCH_1, // 7847
            ObjectID.FARMING_FLOWER_PATCH_2, // 7848
            ObjectID.FARMING_FLOWER_PATCH_3, // 7849
            ObjectID.FARMING_FLOWER_PATCH_4, // 7850
            ObjectID.FARMING_FLOWER_PATCH_5, // 27111
            ObjectID.FARMING_FLOWER_PATCH_6 // 33649
    );

    public static final Set<Integer> HERB_PATCH_IDS = ImmutableSet.of(
            ObjectID.FARMING_HERB_PATCH_1, // 8150
            ObjectID.FARMING_HERB_PATCH_2, // 8151
            ObjectID.FARMING_HERB_PATCH_3, // 8152
            ObjectID.FARMING_HERB_PATCH_4, // 8153
            ObjectID.FARMING_HERB_PATCH_5, // 9372
            ObjectID.MYARM_HERBPATCH, // 18816
            ObjectID.FARMING_HERB_PATCH_6, // 27115
            ObjectID.MY2ARM_HERBPATCH, // 33176
            ObjectID.FARMING_HERB_PATCH_7 // 33979
    );

    public static final Set<Integer> HOPS_PATCH_IDS = ImmutableSet.of();

    public static final Set<Integer> BUSH_PATCH_IDS = ImmutableSet.of(34006);

    public static final Set<Integer> TREE_PATCH_IDS = ImmutableSet.of(
            ObjectID.FARMING_TREE_PATCH_1, // 8388
            ObjectID.FARMING_TREE_PATCH_2, // 8389
            ObjectID.FARMING_TREE_PATCH_3, // 8390
            ObjectID.FARMING_TREE_PATCH_4, // 8391
            ObjectID.FARMING_TREE_PATCH_5, // 19147
            ObjectID.FARMING_TREE_PATCH_6 // 33732
    );

    public static final Set<Integer> FRUIT_TREE_PATCH_IDS = ImmutableSet.of(
            ObjectID.FARMING_FRUIT_TREE_PATCH_1, // 7962
            ObjectID.FARMING_FRUIT_TREE_PATCH_2, // 7963
            ObjectID.FARMING_FRUIT_TREE_PATCH_3, // 7964
            ObjectID.FARMING_FRUIT_TREE_PATCH_4, // 7965
            ObjectID.FARMING_FRUIT_TREE_PATCH_5, // 26579
            ObjectID.FARMING_FRUIT_TREE_PATCH_6 // 34007
    );

    public static final Set<Integer> HARDWOOD_TREE_PATCH_IDS = ImmutableSet.of(
            ObjectID.FARMING_HARDWOOD_TREE_PATCH_2, // 30480
            ObjectID.FARMING_HARDWOOD_TREE_PATCH_3, // 30481
            ObjectID.FARMING_HARDWOOD_TREE_PATCH_1 // 30482
    );

    public static final Set<Integer> SPIRIT_TREE_PATCH_IDS = ImmutableSet.of(
            ObjectID.FARMING_SPIRIT_TREE_PATCH_5 // 33733
    );

    public static final Set<Integer> SEAWEED_PATCH_IDS = ImmutableSet.of();

    public static final Set<Integer> CACTUS_PATCH_IDS = ImmutableSet.of(
            ObjectID.FARMING_CACTUS_PATCH_2 // 33761
    );

    public static final Set<Integer> GRAPE_PATCH_IDS = ImmutableSet.of();

    public static final Set<Integer> MUSHROOM_PATCH_IDS = ImmutableSet.of();

    public static final Set<Integer> BELLADONNA_PATCH_IDS = ImmutableSet.of();

    public static final Set<Integer> HESPORI_PATCH_IDS = ImmutableSet.of(
            ObjectID.HESPORI_PATCH // 34630
    );

    public static final Set<Integer> ANIMA_PATCH_IDS = ImmutableSet.of(
            ObjectID.FARMING_ANIMA_PATCH_1 // 33998
    );

    public static final Set<Integer> CALQUAT_PATCH_IDS = ImmutableSet.of(
            ObjectID.FARMING_CALQUAT_TREE_PATCH // 7807
    );

    public static final Set<Integer> CRYSTAL_PATCH_IDS = ImmutableSet.of();

    public static final Set<Integer> CELASTRUS_PATCH_IDS = ImmutableSet.of(
            ObjectID.FARMING_CELASTRUS_PATCH_1 // 34629
    );

    public static final Set<Integer> REDWOOD_PATCH_IDS = ImmutableSet.of(
            ObjectID.FARMING_REDWOOD_TREE_PATCH_0_5 // 34055
    );

    public static final Set<Integer> ALLOTMENT_SEED_IDS = ImmutableSet.of(
            ItemID.POTATO_SEED,
            ItemID.ONION_SEED,
            ItemID.CABBAGE_SEED,
            ItemID.TOMATO_SEED,
            ItemID.SWEETCORN_SEED,
            ItemID.STRAWBERRY_SEED,
            ItemID.WATERMELON_SEED,
            ItemID.SNAPE_GRASS_SEED
    );

    public static final Set<Integer> FLOWER_SEED_IDS = ImmutableSet.of(
            ItemID.MARIGOLD_SEED,
            ItemID.ROSEMARY_SEED,
            ItemID.NASTURTIUM_SEED,
            ItemID.WOAD_SEED,
            ItemID.LIMPWURT_SEED,
            ItemID.WHITE_LILY_SEED
    );

    public static final Set<Integer> HERB_SEED_IDS = ImmutableSet.of(
            ItemID.GUAM_SEED,
            ItemID.MARRENTILL_SEED,
            ItemID.TARROMIN_SEED,
            ItemID.HARRALANDER_SEED,
            ItemID.RANARR_SEED,
            ItemID.TOADFLAX_SEED,
            ItemID.IRIT_SEED,
            ItemID.AVANTOE_SEED,
            ItemID.KWUARM_SEED,
            ItemID.SNAPDRAGON_SEED,
            ItemID.CADANTINE_SEED,
            ItemID.LANTADYME_SEED,
            ItemID.DWARF_WEED_SEED,
            ItemID.TORSTOL_SEED
    );

    public static final Set<Integer> HOPS_SEED_IDS = ImmutableSet.of(
            ItemID.BARLEY_SEED,
            ItemID.HAMMERSTONE_HOP_SEED,
            ItemID.ASGARNIAN_HOP_SEED,
            ItemID.JUTE_SEED,
            ItemID.YANILLIAN_HOP_SEED,
            ItemID.KRANDORIAN_HOP_SEED,
            ItemID.WILDBLOOD_HOP_SEED
    );

    public static final Set<Integer> BUSH_SEED_IDS = ImmutableSet.of(
            ItemID.REDBERRY_BUSH_SEED,
            ItemID.CADAVABERRY_BUSH_SEED,
            ItemID.DWELLBERRY_BUSH_SEED,
            ItemID.JANGERBERRY_BUSH_SEED,
            ItemID.WHITEBERRY_BUSH_SEED,
            ItemID.POISONIVY_BUSH_SEED
    );

    public static final Set<Integer> TREE_SEED_IDS = ImmutableSet.of(
            ItemID.ACORN,
            ItemID.WILLOW_SEED,
            ItemID.MAPLE_SEED,
            ItemID.YEW_SEED,
            ItemID.MAGIC_TREE_SEED,
            ItemID.APPLE_TREE_SEED,
            ItemID.BANANA_TREE_SEED,
            ItemID.ORANGE_TREE_SEED,
            ItemID.CURRY_TREE_SEED,
            ItemID.PINEAPPLE_TREE_SEED,
            ItemID.PAPAYA_TREE_SEED,
            ItemID.PALM_TREE_SEED,
            ItemID.DRAGONFRUIT_TREE_SEED,
            ItemID.TEAK_SEED,
            ItemID.MAHOGANY_SEED,
            ItemID.CALQUAT_TREE_SEED,
            ItemID.SOTE_CRYSTAL_SEED,
            ItemID.SPIRIT_TREE_SEED,
            ItemID.CELASTRUS_TREE_SEED,
            ItemID.REDWOOD_TREE_SEED
    );

    public static final Set<Integer> TREE_SEEDLING_IDS = ImmutableSet.of(
            ItemID.PLANTPOT_OAK_SAPLING,
            ItemID.PLANTPOT_WILLOW_SAPLING,
            ItemID.PLANTPOT_MAPLE_SAPLING,
            ItemID.PLANTPOT_YEW_SAPLING,
            ItemID.PLANTPOT_MAGIC_TREE_SEED,
            ItemID.PLANTPOT_APPLE_SAPLING,
            ItemID.PLANTPOT_BANANA_SAPLING,
            ItemID.PLANTPOT_ORANGE_SAPLING,
            ItemID.PLANTPOT_CURRY_SAPLING,
            ItemID.PLANTPOT_PINEAPPLE_SAPLING,
            ItemID.PLANTPOT_PAPAYA_SAPLING,
            ItemID.PLANTPOT_PALM_SAPLING,
            ItemID.PLANTPOT_DRAGONFRUIT_SAPLING,
            ItemID.PLANTPOT_TEAK_SAPLING,
            ItemID.PLANTPOT_MAHOGANY_SAPLING,
            ItemID.PLANTPOT_CALQUAT_SAPLING,
            ItemID.PLANTPOT_CRYSTAL_TREE_SAPLING,
            ItemID.PLANTPOT_SPIRIT_TREE_SEED,
            ItemID.PLANTPOT_CELASTRUS_TREE_SEED,
            ItemID.PLANTPOT_REDWOOD_TREE_SEED
    );

    public static final Set<Integer> TREE_SAPLING_IDS = ImmutableSet.of(
            ItemID.PLANTPOT_OAK_SAPLING,
            ItemID.PLANTPOT_WILLOW_SAPLING,
            ItemID.PLANTPOT_MAPLE_SAPLING,
            ItemID.PLANTPOT_YEW_SAPLING,
            ItemID.PLANTPOT_MAGIC_TREE_SEED
    );

    public static final Set<Integer> FRUIT_TREE_SAPLING_IDS = ImmutableSet.of(
            ItemID.PLANTPOT_APPLE_SAPLING,
            ItemID.PLANTPOT_BANANA_SAPLING,
            ItemID.PLANTPOT_ORANGE_SAPLING,
            ItemID.PLANTPOT_CURRY_SAPLING,
            ItemID.PLANTPOT_PINEAPPLE_SAPLING,
            ItemID.PLANTPOT_PAPAYA_SAPLING,
            ItemID.PLANTPOT_PALM_SAPLING,
            ItemID.PLANTPOT_DRAGONFRUIT_SAPLING
    );

    public static final Set<Integer> HARDWOOD_TREE_SAPLING_IDS = ImmutableSet.of(
            ItemID.PLANTPOT_TEAK_SAPLING,
            ItemID.PLANTPOT_MAHOGANY_SAPLING
    );

    public static final Set<Integer> ANIMA_SEED_IDS = ImmutableSet.of(
            ItemID.KRONOS_SEED,
            ItemID.IASOR_SEED,
            ItemID.ATTAS_SEED
    );

    public static final Set<Integer> CACTUS_SEED_IDS = ImmutableSet.of(
            ItemID.CACTUS_SEED,
            ItemID.POTATO_CACTUS_SEED // 22873
    );

    public static final Set<Integer> COMPOST_IDS = ImmutableSet.of(
            ItemID.BUCKET_COMPOST,
            ItemID.BUCKET_SUPERCOMPOST,
            ItemID.BUCKET_ULTRACOMPOST,
            ItemID.BOTTOMLESS_COMPOST_BUCKET,
            ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED
    );

    public static final Set<Integer> WATERING_CAN_IDS = ImmutableSet.of(
            ItemID.WATERING_CAN_0,
            ItemID.WATERING_CAN_1,
            ItemID.WATERING_CAN_2,
            ItemID.WATERING_CAN_3,
            ItemID.WATERING_CAN_4,
            ItemID.WATERING_CAN_5,
            ItemID.WATERING_CAN_6,
            ItemID.WATERING_CAN_7,
            ItemID.WATERING_CAN_8
    );

    public static final Set<Integer> GRIMY_HERB_IDS = ImmutableSet.of(
            ItemID.UNIDENTIFIED_GUAM,
            ItemID.UNIDENTIFIED_MARENTILL,
            ItemID.UNIDENTIFIED_TARROMIN,
            ItemID.UNIDENTIFIED_HARRALANDER,
            ItemID.UNIDENTIFIED_RANARR,
            ItemID.UNIDENTIFIED_TOADFLAX,
            ItemID.UNIDENTIFIED_IRIT,
            ItemID.UNIDENTIFIED_AVANTOE,
            ItemID.UNIDENTIFIED_KWUARM,
            ItemID.UNIDENTIFIED_SNAPDRAGON,
            ItemID.UNIDENTIFIED_CADANTINE,
            ItemID.UNIDENTIFIED_LANTADYME,
            ItemID.UNIDENTIFIED_DWARF_WEED,
            ItemID.UNIDENTIFIED_TORSTOL
    );

    public static final Set<Integer> CLEAN_HERB_IDS = ImmutableSet.of(
            ItemID.GUAM_LEAF,
            ItemID.MARENTILL,
            ItemID.TARROMIN,
            ItemID.HARRALANDER,
            ItemID.RANARR_WEED,
            ItemID.TOADFLAX,
            ItemID.IRIT_LEAF,
            ItemID.AVANTOE,
            ItemID.KWUARM,
            ItemID.SNAPDRAGON,
            ItemID.CADANTINE,
            ItemID.LANTADYME,
            ItemID.DWARF_WEED,
            ItemID.TORSTOL
    );

    public static final Set<Integer> CRAFTING_CAPE_IDS = ImmutableSet.of(
            ItemID.SKILLCAPE_CRAFTING,
            ItemID.SKILLCAPE_CRAFTING_TRIMMED
    );

    public static final Set<Integer> CONSTRUCTION_CAPE_IDS = ImmutableSet.of(
            ItemID.SKILLCAPE_CONSTRUCTION,
            ItemID.SKILLCAPE_CONSTRUCTION_TRIMMED
    );

    public static final Set<Integer> REJUVENATION_POOL_IDS = ImmutableSet.of(
            ObjectID.WILDY_HUB_POOL, // 39651
            ObjectID.WILDY_HUB_ENTRY_BARRIER, // 39652
            ObjectID.WILDY_HUB_ENTRY_BARRIER_M // 39653
    );

    public static final Set<Integer> LIZARDMAN_SHAMAN_IDS = ImmutableSet.of(
            NpcID.ZEAH_LIZARDSHAMAN_1,
            NpcID.ZEAH_LIZARDSHAMAN_2,
            NpcID.RAIDS_LIZARDSHAMAN_A,
            NpcID.RAIDS_LIZARDSHAMAN_B,
            NpcID.LIZARDMAN_CAVE_SHAMAN_1,
            NpcID.LIZARDMAN_CAVE_SHAMAN_2,
            NpcID.MOLCH_LIZARDSHAMAN_1
    );

    public static final Set<Integer> CELL_IDS = ImmutableSet.of(
            ItemID.GOTR_CELL_TIER1,
            ItemID.GOTR_CELL_TIER2,
            ItemID.GOTR_CELL_TIER3,
            ItemID.GOTR_CELL_TIER4
    );

    public static final Set<Integer> INACTIVE_CELL_TILE_IDS = ImmutableSet.of(
            ObjectID.GOTR_CELL_TILE_INACTIVE_NOOP, // 43738
            ObjectID.GOTR_CELL_TILE_INACTIVE // 43739
    );

    public static final Set<Integer> ACTIVE_CELL_TILE_IDS = ImmutableSet.of(
            ObjectID.GOTR_CELL_TILE_TIER1, // 43740 - WEAK_CELL_TILE
            ObjectID.GOTR_CELL_TILE_TIER2, // 43741 - MEDIUM_CELL_TILE
            ObjectID.GOTR_CELL_TILE_TIER3, // 43742 - STRONG_CELL_TILE
            ObjectID.GOTR_CELL_TILE_TIER4  // 43743 - OVERPOWERED_CELL_TILE
    );

    public static final Set<Integer> SPECIAL_AXE_IDS = ImmutableSet.of(
            ItemID.DRAGON_AXE,
            ItemID.TRAILBLAZER_AXE_NO_INFERNAL,
            ItemID.INFERNAL_AXE,
            ItemID.INFERNAL_AXE_EMPTY,
            ItemID.TRAILBLAZER_AXE,
            ItemID.TRAILBLAZER_AXE_EMPTY,
            ItemID.TRAILBLAZER_RELOADED_AXE,
            ItemID.TRAILBLAZER_RELOADED_AXE_EMPTY,
            ItemID.CRYSTAL_AXE,
            ItemID.CRYSTAL_AXE_INACTIVE,
            ItemID.GAUNTLET_AXE,
            ItemID.GAUNTLET_AXE_HM,
            ItemID._3A_AXE
    );

    public static final Set<Integer> SPECIAL_PICKAXE_IDS = ImmutableSet.of(
            ItemID.DRAGON_PICKAXE,
            ItemID.TRAILBLAZER_PICKAXE_NO_INFERNAL,
            ItemID.INFERNAL_PICKAXE,
            ItemID.INFERNAL_PICKAXE_EMPTY,
            ItemID.TRAILBLAZER_PICKAXE,
            ItemID.TRAILBLAZER_PICKAXE_EMPTY,
            ItemID.TRAILBLAZER_RELOADED_PICKAXE,
            ItemID.TRAILBLAZER_RELOADED_PICKAXE_EMPTY,
            ItemID.CRYSTAL_PICKAXE,
            ItemID.CRYSTAL_PICKAXE_INACTIVE,
            ItemID.GAUNTLET_PICKAXE,
            ItemID.GAUNTLET_PICKAXE_HM,
            ItemID._3A_PICKAXE
    );

    public static final Set<Integer> SPECIAL_HARPOON_IDS = ImmutableSet.of(
            ItemID.DRAGON_HARPOON,
            ItemID.TRAILBLAZER_HARPOON_NO_INFERNAL,
            ItemID.INFERNAL_HARPOON,
            ItemID.INFERNAL_HARPOON_EMPTY,
            ItemID.TRAILBLAZER_HARPOON,
            ItemID.TRAILBLAZER_HARPOON_EMPTY,
            ItemID.TRAILBLAZER_RELOADED_HARPOON,
            ItemID.TRAILBLAZER_RELOADED_HARPOON_EMPTY,
            ItemID.CRYSTAL_HARPOON,
            ItemID.CRYSTAL_HARPOON_INACTIVE,
            ItemID.GAUNTLET_HARPOON,
            ItemID.GAUNTLET_HARPOON_HM
    );

    public static final Set<Integer> PHARAOHS_SCEPTRE_IDS = ImmutableSet.of(
            ItemID.PHARAOHS_SCEPTRE,
            ItemID.CERT_NTK_JEWELLED_SCEPTRE_3,
            ItemID.NTK_JEWELLED_SCEPTRE_2,
            ItemID.CERT_NTK_JEWELLED_SCEPTRE_2,
            ItemID.NTK_JEWELLED_SCEPTRE_1,
            ItemID.CERT_NTK_JEWELLED_SCEPTRE_1,
            ItemID.NTK_JEWELLED_SCEPTRE_0,
            ItemID.CERT_NTK_JEWELLED_SCEPTRE_0,
            ItemID.NTK_JEWELLED_SCEPTRE_8,
            ItemID.NTK_JEWELLED_SCEPTRE_7,
            ItemID.NTK_JEWELLED_SCEPTRE_6,
            ItemID.NTK_JEWELLED_SCEPTRE_5,
            ItemID.NTK_JEWELLED_SCEPTRE_4,
            ItemID.PLACEHOLDER_NTK_JEWELLED_SCEPTRE_8,
            ItemID.PLACEHOLDER_NTK_JEWELLED_SCEPTRE_1,
            ItemID.PLACEHOLDER_NTK_JEWELLED_SCEPTRE_0,
            ItemID.PHARAOHS_SCEPTRE_CHARGED,
            ItemID.PHARAOHS_SCEPTRE_CHARGED_INITIAL
    );

    public static final Set<Integer> BARROWS_UNDEGRADED_IDS = ImmutableSet.of(
            ItemID.BARROWS_AHRIM_HEAD,
            ItemID.BARROWS_AHRIM_WEAPON,
            ItemID.BARROWS_AHRIM_BODY,
            ItemID.BARROWS_AHRIM_LEGS,
            ItemID.BARROWS_DHAROK_HEAD,
            ItemID.BARROWS_DHAROK_WEAPON,
            ItemID.BARROWS_DHAROK_BODY,
            ItemID.BARROWS_DHAROK_LEGS,
            ItemID.BARROWS_GUTHAN_HEAD,
            ItemID.BARROWS_GUTHAN_WEAPON,
            ItemID.BARROWS_GUTHAN_BODY,
            ItemID.BARROWS_GUTHAN_LEGS,
            ItemID.BARROWS_KARIL_HEAD,
            ItemID.BARROWS_KARIL_WEAPON,
            ItemID.BARROWS_KARIL_BODY,
            ItemID.BARROWS_KARIL_LEGS,
            ItemID.BARROWS_TORAG_HEAD,
            ItemID.BARROWS_TORAG_WEAPON,
            ItemID.BARROWS_TORAG_BODY,
            ItemID.BARROWS_TORAG_LEGS,
            ItemID.BARROWS_VERAC_HEAD,
            ItemID.BARROWS_VERAC_WEAPON,
            ItemID.BARROWS_VERAC_BODY,
            ItemID.BARROWS_VERAC_LEGS
    );

    public static final Set<Integer> TRIDENT_IDS = ImmutableSet.of(
            ItemID.TOTS, // 11905 - Trident of the seas (full)
            ItemID.TOTS_CHARGED, // 11907 - Trident of the seas
            ItemID.TOTS_UNCHARGED, // 11908 - Uncharged trident
            ItemID.TOXIC_TOTS_CHARGED, // 12899 - Trident of the swamp
            ItemID.TOXIC_TOTS_UNCHARGED, // 12900 - Uncharged toxic trident
            ItemID.TOTS_I_CHARGED, // 22288 - Trident of the seas (e)
            ItemID.TOTS_I_UNCHARGED, // 22290 - Uncharged trident (e)
            ItemID.TOXIC_TOTS_I_CHARGED, // 22292 - Trident of the swamp (e)
            ItemID.TOXIC_TOTS_I_UNCHARGED // 22294 - Uncharged toxic trident (e)
    );

    public static final Set<Integer> TELEPORT_IDS = ImmutableSet.of(
            ItemID.TABLET_ANNAKARL, // 12775
            ItemID.TELETAB_APE, // 19631
            ItemID.TELETAB_LUMBRIDGE, // 8008
            ItemID.POH_TABLET_ARDOUGNETELEPORT, // 8011
            ItemID.LUNAR_TABLET_BARBARIAN_TELEPORT, // 24955
            ItemID.TELETAB_BARROWS, // 19629
            ItemID.TELETAB_BATTLEFRONT, // 22949
            ItemID.BLIGHTED_SACK_TELEBLOCK, // 24615
            ItemID.NZONE_TELETAB_BRIMHAVEN, // 11745
            ItemID.POH_TABLET_CAMELOTTELEPORT, // 8010
            ItemID.TABLET_CARRALLANGAR, // 12776
            ItemID.LUNAR_TABLET_CATHERBY_TELEPORT, // 24961
            ItemID.TELETAB_CEMETERY, // 19627
            ItemID.GAUNTLET_TELEPORT_CRYSTAL_HM, // 23858
            ItemID.ELF_CRYSTAL_TINY, // 6103
            ItemID.TABLET_DAREEYAK, // 12777
            ItemID.DEADMAN_TOURNAMENT_TELETAB, // 13666
            ItemID.TELEPORTSCROLL_DIGSITE, // 12403
            ItemID.TELETAB_DRAYNOR, // 19615
            ItemID.PRIF_TELEPORT_SEED, // 23959
            ItemID.PRIF_TELEPORT_CRYSTAL, // 23946
            ItemID.POH_TABLET_FALADORTELEPORT, // 8009
            ItemID.TELEPORTSCROLL_FELDIP, // 12404
            ItemID.TELETAB_FENK, // 19621
            ItemID.LUNAR_TABLET_FISHING_GUILD_TELEPORT, // 24959
            ItemID.TABLET_GHORROCK, // 12778
            ItemID.TELETAB_HARMONY, // 19625
            ItemID.NZONE_TELETAB_KOUREND, // 19651
            ItemID.LUNAR_TABLET_ICE_PLATEAU_TELEPORT, // 24963
            ItemID.TELEPORTSCROLL_ELF, // 12410
            ItemID.TELEPORTSCROLL_CERBERUS, // 13249
            ItemID.TABLET_KHARYLL, // 12779
            ItemID.LUNAR_TABLET_KHAZARD_TELEPORT, // 24957
            ItemID.POH_TABLET_KOURENDTELEPORT, // 28790
            ItemID.TABLET_LASSAR, // 12780
            ItemID.TELEPORTSCROLL_LUMBERYARD, // 12642
            ItemID.POH_TABLET_LUMBRIDGETELEPORT, // 8008
            ItemID.TELEPORTSCROLL_LUNARISLE, // 12405
            ItemID.TELETAB_MIND_ALTAR, // 19617
            ItemID.LUNAR_TABLET_MOONCLAN_TELEPORT, // 24949
            ItemID.TELEPORTSCROLL_MORTTON, // 12406
            ItemID.TELEPORTSCROLL_MOSLES, // 12411
            ItemID.TELEPORTSCROLL_NARDAH, // 12402
            ItemID.LUNAR_TABLET_OURANIA_TELEPORT, // 24951
            ItemID.TABLET_PADDEWA, // 12781
            ItemID.TELEPORTSCROLL_PESTCONTROL, // 12407
            ItemID.TELEPORTSCROLL_PISCATORIS, // 12408
            ItemID.NZONE_TELETAB_POLLNIVNEACH, // 11743
            ItemID.NZONE_TELETAB_PRIFDDINAS, // 23771
            ItemID.NZONE_TELETAB_RELLEKKA, // 11744
            ItemID.TELEPORTSCROLL_REVENANTS, // 21802
            ItemID.NZONE_TELETAB_RIMMINGTON, // 11741
            ItemID.TELETAB_SALVE, // 19619
            ItemID.XMAS19_TABLET_SCAPE_RUNE, // 24441
            ItemID.TABLET_SENNTISTEN, // 12782
            ItemID.LEAGUE_3_HOME_TELEPORT_SCROLL, // 26500
            ItemID.SPEEDY_TELEPORT_SCROLL, // 27416
            ItemID.TELEPORTSCROLL_TAIBWO, // 12409
            ItemID.TABLET_TARGET, // 24336
            ItemID.TWISTED_HOME_TELEPORT_SCROLL, // 24460
            ItemID.NZONE_TELETAB_TAVERLEY, // 11742
            ItemID.CHRONICLE_TELE_CARD, // 13658
            ItemID.GAUNTLET_TELEPORT_CRYSTAL, // 23904
            ItemID.MOURNING_TELEPORT_CRYSTAL_1, // 6102
            ItemID.MOURNING_TELEPORT_CRYSTAL_2, // 6101
            ItemID.MOURNING_TELEPORT_CRYSTAL_3, // 6100
            ItemID.MOURNING_TELEPORT_CRYSTAL_4, // 6099
            ItemID.MOURNING_TELEPORT_CRYSTAL_5, // 13102
            ItemID.POH_TABLET_TELEPORTTOHOUSE, // 8013
            ItemID.POH_TELEPORT_TRAP, // 8147
            ItemID.LEAGUE_4_HOME_TELEPORT_SCROLL, // 28705
            ItemID.NZONE_TELETAB_TROLLHEIM, // 11747
            ItemID.TRAILBLAZER_HOME_TELEPORT_SCROLL, // 25087
            ItemID.POH_TABLET_VARROCKTELEPORT, // 8007
            ItemID.FOSSIL_TABLET_VOLCANOTELEPORT, // 21541
            ItemID.POH_TABLET_WATCHTOWERTELEPORT, // 8012
            ItemID.LUNAR_TABLET_WATERBIRTH_TELEPORT, // 24953
            ItemID.TELEPORTSCROLL_WATSON, // 23387
            ItemID.TELETAB_WESTARDY, // 19623
            ItemID.TABLET_WILDYCRABS, // 24251
            ItemID.XMAS17_TELETAB, // 21863
            ItemID.NZONE_TELETAB_YANILLE, // 11746
            ItemID.TELEPORTSCROLL_ZULANDRA // 12938
    );

    public static final Set<Integer> FOOD_ITEM_IDS = ImmutableSet.of(
            ItemID.ANGLERFISH, ItemID.BLIGHTED_ANGLERFISH,
            ItemID.DARK_CRAB,
            ItemID.TBWT_COOKED_KARAMBWAN,
            ItemID.POTATO_TUNA_SWEETCORN,
            ItemID.MANTARAY, ItemID.BLIGHTED_MANTARAY,
            ItemID.SEATURTLE,
            ItemID.SHARK, ItemID.CERT_BAGUETTE, ItemID.BR_SHARK,
            ItemID.GAUNTLET_FOOD,
            ItemID.RAIDS_FISH0_COOKED, ItemID.RAIDS_FISH1_COOKED, ItemID.RAIDS_FISH2_COOKED, ItemID.RAIDS_FISH3_COOKED, ItemID.RAIDS_FISH4_COOKED, ItemID.RAIDS_FISH5_COOKED, ItemID.RAIDS_FISH6_COOKED,
            ItemID.RAIDS_BAT0_COOKED, ItemID.RAIDS_BAT1_COOKED, ItemID.RAIDS_BAT2_COOKED, ItemID.RAIDS_BAT3_COOKED, ItemID.RAIDS_BAT4_COOKED, ItemID.RAIDS_BAT5_COOKED, ItemID.RAIDS_BAT6_COOKED,
            ItemID.UGTHANKI_KEBAB_BAD, ItemID.UGTHANKI_KEBAB, ItemID.SUPER_KEBAB,
            ItemID.POTATO_MUSHROOM_ONION,
            ItemID.CURRY,
            ItemID.POTATO_EGG_TOMATO,
            ItemID.POTATO_CHEESE,
            ItemID.MONKFISH, ItemID.BR_MONKFISH,
            ItemID._100_JUBBLY_MEAT_COOKED,
            ItemID.COOKED_OOMLIE,
            ItemID.POTATO_CHILLI_CARNE,
            ItemID.POTATO_BUTTER,
            ItemID.SWORDFISH,
            ItemID.BASS,
            ItemID.BOWL_TUNA_SWEETCORN,
            ItemID.LOBSTER,
            ItemID.STEW,
            ItemID.JUG_WINE,
            ItemID.LAVA_EEL,
            ItemID.CAVE_EEL,
            ItemID.BOWL_MUSHROOM_ONION,
            ItemID.HUNTING_FISH_SPECIAL,
            ItemID.HUNDRED_PIRATE_FISHCAKE,
            ItemID.COOKED_CHOMPY, ItemID.CHOMPY_COOKED,
            ItemID.SWEETCORN_COOKED, ItemID.BOWL_SWEETCORN,
            ItemID.KEBAB,
            ItemID.DRAGONFRUIT,
            ItemID.TUNA, ItemID.BOWL_TUNA,
            ItemID.SALMON,
            ItemID.BOWL_EGG_TOMATO,
            ItemID.PEACH,
            ItemID.MORT_SLIMEY_EEL_COOKED,
            ItemID.PIKE,
            ItemID.COD,
            ItemID.SPIT_ROASTED_BEAST_MEAT,
            ItemID.TROUT,
            ItemID.TBW_SPIDER_ON_STICK_RAW, ItemID.TBW_SPIDER_ON_STICK_COOKED, ItemID.TBW_SPIDER_ON_SHAFT_RAW, ItemID.TBW_SPIDER_ON_SHAFT_COOKED, ItemID.TBW_SPIDER_ON_SHAFT_BURNT,
            ItemID.SNAIL_CORPSE_COOKED3,
            ItemID.MACKEREL,
            ItemID.GIANT_CARP,
            ItemID.SPIT_ROASTED_BIRD_MEAT,
            ItemID.GIANT_FROGSPAWN,
            ItemID.COOKED_MYSTERY_MEAT,
            ItemID.COOKED_RABBIT,
            ItemID.BOWL_CHILLI_CARNE,
            ItemID.BOWL_MUSHROOM_FRIED,
            ItemID.BOWL_ONION_FRIED,
            ItemID.BOWL_EGG_SCRAMBLED,
            ItemID.HERRING,
            ItemID.SNAIL_CORPSE_COOKED1, ItemID.SNAIL_CORPSE_COOKED2,
            ItemID.BREAD,
            ItemID.POTATO_BAKED,
            ItemID.BOWL_ONIONTOMATO,
            ItemID.CAKE_SLICE, ItemID.CHOCOLATE_SLICE,
            ItemID.SARDINE,
            ItemID.COOKED_UGTHANKI_MEAT,
            ItemID.COOKED_MEAT, ItemID.COOKED_MEAT_UNDEAD,
            ItemID.COOKED_CHICKEN, ItemID.COOKED_CHICKEN_UNDEAD,
            ItemID.BOWL_CHILLI,
            ItemID.CHEESE,
            ItemID.BOWL_SPICYMEAT,
            ItemID.BOWL_CARNE,
            ItemID.BANANA, ItemID.TBWT_SLICED_BANANA,
            ItemID.TOMATO, ItemID.BOWL_TOMATO, ItemID.BOWL_SPICYTOMATO,
            ItemID.ANCHOVIES,
            ItemID.SHRIMP,
            ItemID.POTATO,
            ItemID.WATERMELON_SLICE, ItemID.PINEAPPLE_RING, ItemID.PINEAPPLE_CHUNKS,
            ItemID.ONION, ItemID.BOWL_ONION,
            ItemID.ORANGE, ItemID.ORANGE_SLICES,
            ItemID.STRAWBERRY,
            ItemID.CABBAGE,
            ItemID.ALUFT_GNOME_MINT_CAKE,
            ItemID.EASTER_EGG_2005_PURPLE, ItemID.TRAIL_SWEETS,
            ItemID.TOA_HONEY_LOCUST,
            ItemID.CASTLEWARS_BANDAGES, ItemID.SOUL_WARS_BANDAGES, ItemID.TOB_BANDAGES,
            ItemID.MACRO_TRIFFIDFRUIT,
            ItemID.GARDEN_WHITE_TREE_FRUIT,
            ItemID.VILLAGE_RARE_TUBER,
            ItemID.JANGERBERRIES,
            ItemID.DWELLBERRIES,
            ItemID.NIGHTSHADE,
            ItemID.POT_OF_CREAM,
            ItemID.EQUA_LEAVES,
            ItemID.EDIBLE_SEAWEED,
            ItemID.DT2_SCAR_MAZE_STAMINA,
            ItemID.DT2_GHORROCK_RATIONS
    );

    public static final Set<Integer> GRACEFUL_HOOD = ImmutableSet.of(
            ItemID.GRACEFUL_HOOD,
            ItemID.GRACEFUL_HOOD_WORN, // 11851
            ItemID.ZEAH_GRACEFUL_HOOD_ARCEUUS, // 13579
            ItemID.ZEAH_GRACEFUL_HOOD_ARCEUUS_WORN, // 13580
            ItemID.ZEAH_GRACEFUL_HOOD_PISCARILIUS, // 13591
            ItemID.ZEAH_GRACEFUL_HOOD_PISCARILIUS_WORN, // 13592
            ItemID.ZEAH_GRACEFUL_HOOD_LOVAKENGJ, // 13603
            ItemID.ZEAH_GRACEFUL_HOOD_LOVAKENGJ_WORN, // 13604
            ItemID.ZEAH_GRACEFUL_HOOD_SHAYZIEN, // 13615
            ItemID.ZEAH_GRACEFUL_HOOD_SHAYZIEN_WORN, // 13616
            ItemID.ZEAH_GRACEFUL_HOOD_HOSIDIUS, // 13627
            ItemID.ZEAH_GRACEFUL_HOOD_HOSIDIUS_WORN, // 13628
            ItemID.ZEAH_GRACEFUL_HOOD_KOUREND, // 13667
            ItemID.ZEAH_GRACEFUL_HOOD_KOUREND_WORN, // 13668
            ItemID.GRACEFUL_HOOD_SKILLCAPECOLOUR, // 21061
            ItemID.GRACEFUL_HOOD_SKILLCAPECOLOUR_WORN, // 21063
            ItemID.GRACEFUL_HOOD_HALLOWED, // 24743
            ItemID.GRACEFUL_HOOD_HALLOWED_WORN, // 24745
            ItemID.GRACEFUL_HOOD_TRAILBLAZER, // 25069
            ItemID.GRACEFUL_HOOD_TRAILBLAZER_WORN // 25071
    );

    public static final Set<Integer> GRACEFUL_TOP = ImmutableSet.of(
            ItemID.GRACEFUL_TOP,
            ItemID.GRACEFUL_TOP_WORN, // 11855
            ItemID.ZEAH_GRACEFUL_TOP_ARCEUUS, // 13583
            ItemID.ZEAH_GRACEFUL_TOP_ARCEUUS_WORN, // 13584
            ItemID.ZEAH_GRACEFUL_TOP_PISCARILIUS, // 13595
            ItemID.ZEAH_GRACEFUL_TOP_PISCARILIUS_WORN, // 13596
            ItemID.ZEAH_GRACEFUL_TOP_LOVAKENGJ, // 13607
            ItemID.ZEAH_GRACEFUL_TOP_LOVAKENGJ_WORN, // 13608
            ItemID.ZEAH_GRACEFUL_TOP_SHAYZIEN, // 13619
            ItemID.ZEAH_GRACEFUL_TOP_SHAYZIEN_WORN, // 13620
            ItemID.ZEAH_GRACEFUL_TOP_HOSIDIUS, // 13631
            ItemID.ZEAH_GRACEFUL_TOP_HOSIDIUS_WORN, // 13632
            ItemID.ZEAH_GRACEFUL_TOP_KOUREND, // 13671
            ItemID.ZEAH_GRACEFUL_TOP_KOUREND_WORN, // 13672
            ItemID.GRACEFUL_TOP_SKILLCAPECOLOUR, // 21067
            ItemID.GRACEFUL_TOP_SKILLCAPECOLOUR_WORN, // 21069
            ItemID.GRACEFUL_TOP_HALLOWED, // 24749
            ItemID.GRACEFUL_TOP_HALLOWED_WORN, // 24751
            ItemID.GRACEFUL_TOP_TRAILBLAZER, // 25075
            ItemID.GRACEFUL_TOP_TRAILBLAZER_WORN // 25077
    );

    public static final Set<Integer> GRACEFUL_LEGS = ImmutableSet.of(
            ItemID.GRACEFUL_LEGS,
            ItemID.GRACEFUL_LEGS_WORN, // 11857
            ItemID.ZEAH_GRACEFUL_LEGS_ARCEUUS, // 13585
            ItemID.ZEAH_GRACEFUL_LEGS_ARCEUUS_WORN, // 13586
            ItemID.ZEAH_GRACEFUL_LEGS_PISCARILIUS, // 13597
            ItemID.ZEAH_GRACEFUL_LEGS_PISCARILIUS_WORN, // 13598
            ItemID.ZEAH_GRACEFUL_LEGS_LOVAKENGJ, // 13609
            ItemID.ZEAH_GRACEFUL_LEGS_LOVAKENGJ_WORN, // 13610
            ItemID.ZEAH_GRACEFUL_LEGS_SHAYZIEN, // 13621
            ItemID.ZEAH_GRACEFUL_LEGS_SHAYZIEN_WORN, // 13622
            ItemID.ZEAH_GRACEFUL_LEGS_HOSIDIUS, // 13633
            ItemID.ZEAH_GRACEFUL_LEGS_HOSIDIUS_WORN, // 13634
            ItemID.ZEAH_GRACEFUL_LEGS_KOUREND, // 13673
            ItemID.ZEAH_GRACEFUL_LEGS_KOUREND_WORN, // 13674
            ItemID.GRACEFUL_LEGS_SKILLCAPECOLOUR, // 21070
            ItemID.GRACEFUL_LEGS_SKILLCAPECOLOUR_WORN, // 21072
            ItemID.GRACEFUL_LEGS_HALLOWED, // 24752
            ItemID.GRACEFUL_LEGS_HALLOWED_WORN, // 24754
            ItemID.GRACEFUL_LEGS_TRAILBLAZER, // 25078
            ItemID.GRACEFUL_LEGS_TRAILBLAZER_WORN // 25080
    );

    public static final Set<Integer> RING_OF_ENDURANCE_IDS = ImmutableSet.of(
            ItemID.RING_OF_ENDURANCE,
            ItemID.RING_OF_ENDURANCE_UNCHARGED,
            ItemID.RING_OF_ENDURANCE_NOCHARGES
    );

    public static final Set<Integer> GRACEFUL_BOOTS = ImmutableSet.of(
            ItemID.GRACEFUL_BOOTS,
            ItemID.GRACEFUL_BOOTS_WORN, // 11861
            ItemID.ZEAH_GRACEFUL_BOOTS_ARCEUUS, // 13589
            ItemID.ZEAH_GRACEFUL_BOOTS_ARCEUUS_WORN, // 13590
            ItemID.ZEAH_GRACEFUL_BOOTS_PISCARILIUS, // 13601
            ItemID.ZEAH_GRACEFUL_BOOTS_PISCARILIUS_WORN, // 13602
            ItemID.ZEAH_GRACEFUL_BOOTS_LOVAKENGJ, // 13613
            ItemID.ZEAH_GRACEFUL_BOOTS_LOVAKENGJ_WORN, // 13614
            ItemID.ZEAH_GRACEFUL_BOOTS_SHAYZIEN, // 13625
            ItemID.ZEAH_GRACEFUL_BOOTS_SHAYZIEN_WORN, // 13626
            ItemID.ZEAH_GRACEFUL_BOOTS_HOSIDIUS, // 13637
            ItemID.ZEAH_GRACEFUL_BOOTS_HOSIDIUS_WORN, // 13638
            ItemID.ZEAH_GRACEFUL_BOOTS_KOUREND, // 13677
            ItemID.ZEAH_GRACEFUL_BOOTS_KOUREND_WORN, // 13678
            ItemID.GRACEFUL_BOOTS_SKILLCAPECOLOUR, // 21076
            ItemID.GRACEFUL_BOOTS_SKILLCAPECOLOUR_WORN, // 21078
            ItemID.GRACEFUL_BOOTS_HALLOWED, // 24758
            ItemID.GRACEFUL_BOOTS_HALLOWED_WORN, // 24760
            ItemID.GRACEFUL_BOOTS_TRAILBLAZER, // 25084
            ItemID.GRACEFUL_BOOTS_TRAILBLAZER_WORN // 25086
    );

    public static final Set<Integer> GRACEFUL_GLOVES = ImmutableSet.of(
            ItemID.GRACEFUL_GLOVES,
            ItemID.GRACEFUL_GLOVES_WORN,
            ItemID.ZEAH_GRACEFUL_GLOVES_ARCEUUS,
            ItemID.ZEAH_GRACEFUL_GLOVES_ARCEUUS_WORN,
            ItemID.ZEAH_GRACEFUL_GLOVES_PISCARILIUS,
            ItemID.ZEAH_GRACEFUL_GLOVES_PISCARILIUS_WORN,
            ItemID.ZEAH_GRACEFUL_GLOVES_LOVAKENGJ,
            ItemID.ZEAH_GRACEFUL_GLOVES_LOVAKENGJ_WORN,
            ItemID.ZEAH_GRACEFUL_GLOVES_SHAYZIEN,
            ItemID.ZEAH_GRACEFUL_GLOVES_SHAYZIEN_WORN,
            ItemID.ZEAH_GRACEFUL_GLOVES_HOSIDIUS,
            ItemID.ZEAH_GRACEFUL_GLOVES_HOSIDIUS_WORN,
            ItemID.ZEAH_GRACEFUL_GLOVES_KOUREND,
            ItemID.ZEAH_GRACEFUL_GLOVES_KOUREND_WORN,
            ItemID.GRACEFUL_GLOVES_SKILLCAPECOLOUR,
            ItemID.GRACEFUL_GLOVES_SKILLCAPECOLOUR_WORN,
            ItemID.GRACEFUL_GLOVES_HALLOWED,
            ItemID.GRACEFUL_GLOVES_HALLOWED_WORN,
            ItemID.GRACEFUL_GLOVES_TRAILBLAZER,
            ItemID.GRACEFUL_GLOVES_TRAILBLAZER_WORN
    );

    public static final Set<Integer> GRACEFUL_CAPE = ImmutableSet.of(
            ItemID.GRACEFUL_CAPE,
            ItemID.GRACEFUL_CAPE_WORN,
            ItemID.ZEAH_GRACEFUL_CAPE_ARCEUUS,
            ItemID.ZEAH_GRACEFUL_CAPE_ARCEUUS_WORN,
            ItemID.ZEAH_GRACEFUL_CAPE_PISCARILIUS,
            ItemID.ZEAH_GRACEFUL_CAPE_PISCARILIUS_WORN,
            ItemID.ZEAH_GRACEFUL_CAPE_LOVAKENGJ,
            ItemID.ZEAH_GRACEFUL_CAPE_LOVAKENGJ_WORN,
            ItemID.ZEAH_GRACEFUL_CAPE_SHAYZIEN,
            ItemID.ZEAH_GRACEFUL_CAPE_SHAYZIEN_WORN,
            ItemID.ZEAH_GRACEFUL_CAPE_HOSIDIUS,
            ItemID.ZEAH_GRACEFUL_CAPE_HOSIDIUS_WORN,
            ItemID.ZEAH_GRACEFUL_CAPE_KOUREND,
            ItemID.ZEAH_GRACEFUL_CAPE_KOUREND_WORN,
            ItemID.GRACEFUL_CAPE_SKILLCAPECOLOUR,
            ItemID.GRACEFUL_CAPE_SKILLCAPECOLOUR_WORN,
            ItemID.GRACEFUL_CAPE_HALLOWED,
            ItemID.GRACEFUL_CAPE_HALLOWED_WORN,
            ItemID.GRACEFUL_CAPE_TRAILBLAZER,
            ItemID.GRACEFUL_CAPE_TRAILBLAZER_WORN
    );

    public static final Set<Integer> BANK_OBJECT_IDS =
            ImmutableSet.of(
                    ObjectID.DWARF_KELDAGRIM_BANKBOOTH, // 6084
                    ObjectID.NEWBIEBANKBOOTH, // 10083
                    ObjectID.BANKBOOTH, // 10355
                    ObjectID.BANKBOOTH_DEADMAN, // 10357
                    ObjectID.ELID_BANKBOOTH, // 10517
                    ObjectID.BANKPRIVATEBOOTH, // 10527
                    ObjectID.FAI_VARROCK_BANKBOOTH, // 10583
                    ObjectID.FAI_VARROCK_BANKBOOTH_DEADMAN, // 10584
                    ObjectID.PVPW_BANKCHEST, // 10777
                    ObjectID.FEVER_BANKBOOTH, // 11338
                    ObjectID.BURGH_BANKBOOTH_REPAIRED, // 12798
                    ObjectID.BURGH_BANKBOOTH_DAMAGED, // 12799
                    ObjectID.BURGH_BANKBOOTH_TOO_DAMAGED, // 12800
                    ObjectID.BURGH_BANKBOOTH_TOO_DAMAGED2, // 12801
                    ObjectID.PEST_BANKBOOTH, // 14367
                    ObjectID.PEST_BANKBOOTH_CLOSED, // 14368
                    ObjectID.AHOY_BANKBOOTH, // 16642
                    ObjectID.LUNAR_MOONCLAN_BANKBOOTH, // 16700
                    ObjectID.AIDE_BANKBOOTH, // 18491
                    ObjectID.CONTACT_BANK_BOOTH, // 20325
                    ObjectID.CONTACT_BANK_BOOTH_BROKEN_01, // 20326
                    ObjectID.CONTACT_BANK_BOOTH_BROKEN_02, // 20327
                    ObjectID.CONTACT_BANK_BOOTH_BROKEN_03, // 20328
                    ObjectID.DORGESH_BANK_BOOTH, // 22819
                    ObjectID.FAI_FALADOR_BANKBOOTH, // 24101
                    ObjectID.CANAFIS_BANKBOOTH, // 24347
                    ObjectID.KR_BANKBOOTH, // 25808
                    ObjectID.FAI_FALADOR_BANKBOOTH_DEADMAN, // 27254
                    ObjectID.PEST_BANKBOOTH_DEADMAN, // 27260
                    ObjectID.CONTACT_BANK_BOOTH_DEADMAN, // 27263
                    ObjectID.KR_BANKBOOTH_DEADMAN, // 27265
                    ObjectID.AHOY_BANKBOOTH_DEADMAN, // 27267
                    ObjectID.AIDE_BANKBOOTH_DEADMAN, // 27292
                    ObjectID.PISCARILIUS_BANK_BOOTH_01, // 27718
                    ObjectID.PISCARILIUS_BANK_BOOTH_02, // 27719
                    ObjectID.PISCARILIUS_BANK_BOOTH_03, // 27720
                    ObjectID.PISCARILIUS_BANK_BOOTH_04, // 27721
                    ObjectID.ARCHEEUS_BANK_BOOTH_CLOSED, // 28429
                    ObjectID.ARCHEEUS_BANK_BOOTH_OPEN_01, // 28430
                    ObjectID.ARCHEEUS_BANK_BOOTH_OPEN_02, // 28431
                    ObjectID.ARCHEEUS_BANK_BOOTH_OPEN_03, // 28432
                    ObjectID.ARCHEEUS_BANK_BOOTH_OPEN_04, // 28433
                    ObjectID.LOVA_BANK_BOOTH_01, // 28546
                    ObjectID.LOVA_BANK_BOOTH_02, // 28547
                    ObjectID.LOVA_BANK_BOOTH_03, // 28548
                    ObjectID.LOVA_BANK_BOOTH_04, // 28549
                    ObjectID.EXCHANGE_BANK_WALL_BANK, // 10060
                    ObjectID.EXCHANGE_BANK_WALL_EXCHANGE, // 10061
                    ObjectID.EXCHANGE_BANK_WALL_BANK_3OPS // 30390
            );

    public static final Set<Integer> BANK_NPC_IDS =
            ImmutableSet.of(
                    NpcID.MISC_BANKER, // 766
                    NpcID.MOURNING_ELF_BANKERM, // 1479
                    NpcID.MOURNING_ELF_BANKERF, // 1480
                    NpcID.BANKER1, // 1613
                    NpcID.BANKER2, // 1618
                    NpcID.BANKER1_WEST, // 1633
                    NpcID.BANKER1_EAST, // 1634
                    NpcID.WOM_BANKER1, // 2117
                    NpcID.WOM_BANKER2, // 2118
                    NpcID.WOM_BANKER3, // 2119
                    NpcID.DORGESH_FEMALE_BANKER, // 2292
                    NpcID.DORGESH_MALE_BANKER, // 2293
                    NpcID.DWARF_CITY_BANKER1, // 2368
                    NpcID.DWARF_CITY_BANKER2, // 2369
                    NpcID.WEREWOLFBANKER, // 2633
                    NpcID.BANKER1_NEW, // 2897
                    NpcID.BANKER2_NEW, // 2898
                    NpcID.AHOY_GHOST_BANKER, // 3003
                    NpcID.BANKER2_EAST, // 3089
                    NpcID.KHARIDBANKER1, // 3090
                    NpcID.KHARIDBANKER2, // 3091
                    NpcID.FAIRY_BANKER, // 3092
                    NpcID.SHILOBANKER, // 3093
                    NpcID.FALADOR_BANKER, // 3094
                    NpcID.AIDE_TUTOR_BANKER, // 3227
                    NpcID.NOOBBANKER, // 3318
                    NpcID.LUNAR_MOONCLAN_MONK_MAN, // 3843
                    NpcID.CONTACT_BANKER_MALE, // 3887
                    NpcID.CONTACT_BANKER_FEMALE, // 3888
                    NpcID.FEVER_BANKER_01, // 4054
                    NpcID.FEVER_BANKER_02, // 4055
                    NpcID.ELID_BANKER, // 4762
                    NpcID.GNOMEBANKER, // 6084
                    NpcID.SHAYZIEN_BANKER_M_NORTH, // 6859
                    NpcID.SHAYZIEN_BANKER_M_EAST, // 6860
                    NpcID.SHAYZIEN_BANKER_M_WEST, // 6861
                    NpcID.SHAYZIEN_BANKER_F_NORTH, // 6862
                    NpcID.SHAYZIEN_BANKER_F_EAST, // 6863
                    NpcID.SHAYZIEN_BANKER_F_WEST, // 6864
                    NpcID.HOSIDIUS_BANKER_M_WEST, // 6939
                    NpcID.HOSIDIUS_BANKER_M_EAST, // 6940
                    NpcID.HOSIDIUS_BANKER_F_WEST, // 6941
                    NpcID.HOSIDIUS_BANKER_F_EAST, // 6942
                    NpcID.PISCARILIUS_BANKER_1, // 6969
                    NpcID.PISCARILIUS_BANKER_2, // 6970
                    NpcID.ARCEUUS_BANKER_1_WEST, // 7057
                    NpcID.ARCEUUS_BANKER_1_EAST, // 7058
                    NpcID.ARCEUUS_BANKER_2_WEST, // 7059
                    NpcID.ARCEUUS_BANKER_2_EAST, // 7060
                    NpcID.LOVAKENGJ_BANKER_F_WEST, // 7077
                    NpcID.LOVAKENGJ_BANKER_F_NORTH, // 7078
                    NpcID.LOVAKENGJ_BANKER_F_EAST, // 7079
                    NpcID.LOVAKENGJ_BANKER_F_SOUTH, // 7080
                    NpcID.LOVAKENGJ_BANKER_M_EAST, // 7081
                    NpcID.LOVAKENGJ_BANKER_M_SOUTH, // 7082
                    NpcID.VAMPYRE_BANKER_FEMALE, // 8321
                    NpcID.VAMPYRE_BANKER_MALE, // 8322
                    NpcID.FARMING_GUILD_BANKER1, // 8589
                    NpcID.FARMING_GUILD_BANKER2, // 8590
                    NpcID.VARROCK_BANK_DOORGUARD, // 8666
                    NpcID.PRIF_BANKERM1, // 9127
                    NpcID.PRIF_BANKERM2, // 9128
                    NpcID.PRIF_BANKERM3, // 9129
                    NpcID.PRIF_BANKERM4, // 9130
                    NpcID.PRIF_BANKERF1, // 9131
                    NpcID.PRIF_BANKERF2, // 9132
                    NpcID.TUT2_BANK_TUTOR, // 9484
                    NpcID.DARKM_BANKER_FEMALE, // 9718
                    NpcID.DARKM_BANKER_MALE, // 9719
                    NpcID.WILDY_HUB_BANKER, // 10389
                    NpcID.CLAN_HALL_BANKER_0, // 10734
                    NpcID.CLAN_HALL_BANKER_1, // 10735
                    NpcID.CLAN_HALL_BANKER_2, // 10736
                    NpcID.CLAN_HALL_BANKER_3 // 10737
            );
    public static final Set<Integer> PORTAL_NEXUS_IDS = ImmutableSet.of(
        ObjectID.POH_NEXUS_PORTAL_1,
        ObjectID.POH_NEXUS_PORTAL_2,
        ObjectID.POH_NEXUS_PORTAL_3
    );
}