package net.runelite.client.plugins.microbot.aiofighter.constants;

import com.google.common.collect.ImmutableSet;
import net.runelite.api.ItemID;
import net.runelite.api.NpcID;
import net.runelite.api.NullObjectID;
import net.runelite.api.ObjectID;

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
            ItemID.DIGSITE_PENDANT_1,
            ItemID.DIGSITE_PENDANT_2,
            ItemID.DIGSITE_PENDANT_3,
            ItemID.DIGSITE_PENDANT_4,
            ItemID.DIGSITE_PENDANT_5
    );

    public static final int BIRD_HOUSE_EMPTY_SPACE = ObjectID.SPACE;

    public static final int MEADOW_NORTH_SPACE = NullObjectID.NULL_30565;
    public static final int MEADOW_SOUTH_SPACE = NullObjectID.NULL_30566;
    public static final int VERDANT_NORTH_SPACE = NullObjectID.NULL_30567;
    public static final int VERDANT_SOUTH_SPACE = NullObjectID.NULL_30568;

    public static final Set<Integer> BIRD_HOUSE_SPACES = ImmutableSet.of(
            MEADOW_NORTH_SPACE,
            MEADOW_SOUTH_SPACE,
            VERDANT_NORTH_SPACE,
            VERDANT_SOUTH_SPACE
    );

    public static final Set<Integer> BIRD_HOUSE_IDS = ImmutableSet.of(
            ObjectID.BIRDHOUSE,
            ObjectID.BIRDHOUSE_30555,
            ObjectID.OAK_BIRDHOUSE,
            ObjectID.OAK_BIRDHOUSE_30558,
            ObjectID.WILLOW_BIRDHOUSE,
            ObjectID.WILLOW_BIRDHOUSE_30561,
            ObjectID.TEAK_BIRDHOUSE,
            ObjectID.TEAK_BIRDHOUSE_30564,
            ObjectID.MAPLE_BIRDHOUSE,
            ObjectID.MAPLE_BIRDHOUSE_31829,
            ObjectID.MAHOGANY_BIRDHOUSE,
            ObjectID.MAHOGANY_BIRDHOUSE_31832,
            ObjectID.YEW_BIRDHOUSE,
            ObjectID.YEW_BIRDHOUSE_31835,
            ObjectID.MAGIC_BIRDHOUSE,
            ObjectID.MAGIC_BIRDHOUSE_31838,
            ObjectID.REDWOOD_BIRDHOUSE,
            ObjectID.REDWOOD_BIRDHOUSE_31841
    );

    public static final Set<Integer> BIRD_HOUSE_EMPTY_IDS = ImmutableSet.of(
            ObjectID.BIRDHOUSE_EMPTY,
            ObjectID.OAK_BIRDHOUSE_EMPTY,
            ObjectID.WILLOW_BIRDHOUSE_EMPTY,
            ObjectID.TEAK_BIRDHOUSE_EMPTY,
            ObjectID.MAPLE_BIRDHOUSE_EMPTY,
            ObjectID.MAHOGANY_BIRDHOUSE_EMPTY,
            ObjectID.YEW_BIRDHOUSE_EMPTY,
            ObjectID.MAGIC_BIRDHOUSE_EMPTY,
            ObjectID.REDWOOD_BIRDHOUSE_EMPTY
    );

    public static final Set<Integer> BIRD_HOUSE_ITEM_IDS = ImmutableSet.of(
            ItemID.BIRD_HOUSE,
            ItemID.OAK_BIRD_HOUSE,
            ItemID.WILLOW_BIRD_HOUSE,
            ItemID.TEAK_BIRD_HOUSE,
            ItemID.MAPLE_BIRD_HOUSE,
            ItemID.MAHOGANY_BIRD_HOUSE,
            ItemID.YEW_BIRD_HOUSE,
            ItemID.MAGIC_BIRD_HOUSE,
            ItemID.REDWOOD_BIRD_HOUSE
    );

    public static final Set<Integer> BIRD_HOUSE_SEED_IDS = ImmutableSet.of(
            ItemID.BARLEY_SEED,
            ItemID.HAMMERSTONE_SEED,
            ItemID.ASGARNIAN_SEED,
            ItemID.JUTE_SEED,
            ItemID.YANILLIAN_SEED,
            ItemID.KRANDORIAN_SEED
    );

    public static final Set<Integer> BIRD_NEST_IDS = ImmutableSet.of(
            ItemID.BIRD_NEST,
            ItemID.BIRD_NEST_5071,
            ItemID.BIRD_NEST_5072,
            ItemID.BIRD_NEST_5073,
            ItemID.BIRD_NEST_5074,
            ItemID.BIRD_NEST_5075,
            ItemID.BIRD_NEST_7413,
            ItemID.BIRD_NEST_13653,
            ItemID.BIRD_NEST_22798,
            ItemID.BIRD_NEST_22800,
            ItemID.CLUE_NEST_EASY,
            ItemID.CLUE_NEST_MEDIUM,
            ItemID.CLUE_NEST_HARD,
            ItemID.CLUE_NEST_ELITE
    );

    public static final Set<Integer> MAGIC_MUSHTREE_IDS = ImmutableSet.of(
            ObjectID.MAGIC_MUSHTREE,
            ObjectID.MAGIC_MUSHTREE_30922,
            ObjectID.MAGIC_MUSHTREE_30924
    );

    public static final Set<Integer> ESSENCE_IDS = ImmutableSet.of(
            ItemID.RUNE_ESSENCE,
            ItemID.PURE_ESSENCE,
            ItemID.DAEYALT_ESSENCE
    );

    public static final Set<Integer> RUNE_IDS = ImmutableSet.of(
            ItemID.AIR_RUNE,
            ItemID.MIND_RUNE,
            ItemID.WATER_RUNE,
            ItemID.EARTH_RUNE,
            ItemID.FIRE_RUNE,
            ItemID.BODY_RUNE,
            ItemID.COSMIC_RUNE,
            ItemID.CHAOS_RUNE,
            ItemID.NATURE_RUNE,
            ItemID.LAW_RUNE,
            ItemID.DEATH_RUNE,
            ItemID.ASTRAL_RUNE,
            ItemID.BLOOD_RUNE,
            ItemID.SOUL_RUNE,
            ItemID.WRATH_RUNE
    );

    public static final Set<Integer> ANVIL_IDS = ImmutableSet.of(
            ObjectID.ANVIL,
            ObjectID.ANVIL_2097,
            ObjectID.AN_EXPERIMENTAL_ANVIL,
            ObjectID.ANVIL_4306,
            ObjectID.ANVIL_6150,
            ObjectID.ANVIL_22725,
            ObjectID.BARBARIAN_ANVIL,
            ObjectID.ANVIL_28563,
            ObjectID.ORNAMENTAL_ANVIL,
            ObjectID.ORNAMENTAL_ANVIL_29310,
            ObjectID.GIANT_ANVIL,
            ObjectID.ANVIL_31623,
            ObjectID.ANVIL_32215,
            ObjectID.ANVIL_32216,
            ObjectID.ANVIL_39242,
            ObjectID.RUSTED_ANVIL,
            ObjectID.GIANT_ANVIL_39724,
            ObjectID.ANVIL_40725,
            ObjectID.ANVIL_42825,
            ObjectID.ANVIL_42860
    );

    public static final Set<Integer> STAMINA_POTION_IDS = ImmutableSet.of(
            ItemID.STAMINA_POTION1,
            ItemID.STAMINA_POTION2,
            ItemID.STAMINA_POTION3,
            ItemID.STAMINA_POTION4
    );

    public static final Set<Integer> BREW_POTION_IDS = ImmutableSet.of(
            ItemID.SARADOMIN_BREW1,
            ItemID.SARADOMIN_BREW2,
            ItemID.SARADOMIN_BREW3,
            ItemID.SARADOMIN_BREW4,
            ItemID.XERICS_AID_1,
            ItemID.XERICS_AID_2,
            ItemID.XERICS_AID_3,
            ItemID.XERICS_AID_4,
            ItemID.XERICS_AID_1_20977,
            ItemID.XERICS_AID_2_20978,
            ItemID.XERICS_AID_3_20979,
            ItemID.XERICS_AID_4_20980,
            ItemID.XERICS_AID_1_20981,
            ItemID.XERICS_AID_2_20982,
            ItemID.XERICS_AID_3_20983,
            ItemID.XERICS_AID_4_20984
    );

    public static final Set<Integer> RESTORE_POTION_IDS = ImmutableSet.of(
            ItemID.RESTORE_POTION1,
            ItemID.RESTORE_POTION2,
            ItemID.RESTORE_POTION3,
            ItemID.RESTORE_POTION4,
            ItemID.SUPER_RESTORE1,
            ItemID.SUPER_RESTORE2,
            ItemID.SUPER_RESTORE3,
            ItemID.SUPER_RESTORE4
    );

    public static final Set<Integer> ANTI_POISON_POTION_IDS = ImmutableSet.of(
            ItemID.ANTIPOISON1,
            ItemID.ANTIPOISON2,
            ItemID.ANTIPOISON3,
            ItemID.ANTIPOISON4,
            ItemID.SUPERANTIPOISON1,
            ItemID.SUPERANTIPOISON2,
            ItemID.SUPERANTIPOISON3,
            ItemID.SUPERANTIPOISON4,
            ItemID.ANTIDOTE1,
            ItemID.ANTIDOTE2,
            ItemID.ANTIDOTE3,
            ItemID.ANTIDOTE4,
            ItemID.ANTIDOTE1_5958,
            ItemID.ANTIDOTE2_5956,
            ItemID.ANTIDOTE3_5954,
            ItemID.ANTIDOTE4_5952,
            ItemID.ANTIVENOM1,
            ItemID.ANTIVENOM2,
            ItemID.ANTIVENOM3,
            ItemID.ANTIVENOM4,
            ItemID.ANTIVENOM4_12913,
            ItemID.ANTIVENOM3_12915,
            ItemID.ANTIVENOM2_12917,
            ItemID.ANTIVENOM1_12919
    );

    public static final Set<Integer> ANTI_FIRE_POTION_IDS = ImmutableSet.of(
            ItemID.ANTIFIRE_POTION1,
            ItemID.ANTIFIRE_POTION2,
            ItemID.ANTIFIRE_POTION3,
            ItemID.ANTIFIRE_POTION4,
            ItemID.EXTENDED_ANTIFIRE1,
            ItemID.EXTENDED_ANTIFIRE2,
            ItemID.EXTENDED_ANTIFIRE3,
            ItemID.EXTENDED_ANTIFIRE4,
            ItemID.SUPER_ANTIFIRE_POTION1,
            ItemID.SUPER_ANTIFIRE_POTION2,
            ItemID.SUPER_ANTIFIRE_POTION3,
            ItemID.SUPER_ANTIFIRE_POTION4,
            ItemID.EXTENDED_SUPER_ANTIFIRE1,
            ItemID.EXTENDED_SUPER_ANTIFIRE2,
            ItemID.EXTENDED_SUPER_ANTIFIRE3,
            ItemID.EXTENDED_SUPER_ANTIFIRE4
    );

    public static final Set<Integer> PRAYER_RESTORE_POTION_IDS = ImmutableSet.of(
            ItemID.SUPER_RESTORE1,
            ItemID.SUPER_RESTORE2,
            ItemID.SUPER_RESTORE3,
            ItemID.SUPER_RESTORE4,
            ItemID.PRAYER_POTION1,
            ItemID.PRAYER_POTION2,
            ItemID.PRAYER_POTION3,
            ItemID.PRAYER_POTION4
    );

    public static final Set<Integer> STRENGTH_POTION_IDS = ImmutableSet.of(
            ItemID.STRENGTH_POTION1,
            ItemID.STRENGTH_POTION2,
            ItemID.STRENGTH_POTION3,
            ItemID.STRENGTH_POTION4,
            ItemID.SUPER_STRENGTH1,
            ItemID.SUPER_STRENGTH2,
            ItemID.SUPER_STRENGTH3,
            ItemID.SUPER_STRENGTH4,
            ItemID.DIVINE_SUPER_STRENGTH_POTION1,
            ItemID.DIVINE_SUPER_STRENGTH_POTION2,
            ItemID.DIVINE_SUPER_STRENGTH_POTION3,
            ItemID.DIVINE_SUPER_STRENGTH_POTION4,
            ItemID.DIVINE_SUPER_COMBAT_POTION1,
            ItemID.DIVINE_SUPER_COMBAT_POTION2,
            ItemID.DIVINE_SUPER_COMBAT_POTION3,
            ItemID.DIVINE_SUPER_COMBAT_POTION4,
            ItemID.COMBAT_POTION1,
            ItemID.COMBAT_POTION2,
            ItemID.COMBAT_POTION3,
            ItemID.COMBAT_POTION4,
            ItemID.SUPER_COMBAT_POTION1,
            ItemID.SUPER_COMBAT_POTION2,
            ItemID.SUPER_COMBAT_POTION3,
            ItemID.SUPER_COMBAT_POTION4
    );

    public static final Set<Integer> ATTACK_POTION_IDS = ImmutableSet.of(
            ItemID.ATTACK_POTION1,
            ItemID.ATTACK_POTION2,
            ItemID.ATTACK_POTION3,
            ItemID.ATTACK_POTION4,
            ItemID.SUPER_ATTACK1,
            ItemID.SUPER_ATTACK2,
            ItemID.SUPER_ATTACK3,
            ItemID.SUPER_ATTACK4,
            ItemID.DIVINE_SUPER_ATTACK_POTION1,
            ItemID.DIVINE_SUPER_ATTACK_POTION2,
            ItemID.DIVINE_SUPER_ATTACK_POTION3,
            ItemID.DIVINE_SUPER_ATTACK_POTION4,
            ItemID.DIVINE_SUPER_COMBAT_POTION1,
            ItemID.DIVINE_SUPER_COMBAT_POTION2,
            ItemID.DIVINE_SUPER_COMBAT_POTION3,
            ItemID.DIVINE_SUPER_COMBAT_POTION4,
            ItemID.COMBAT_POTION1,
            ItemID.COMBAT_POTION2,
            ItemID.COMBAT_POTION3,
            ItemID.COMBAT_POTION4,
            ItemID.SUPER_COMBAT_POTION1,
            ItemID.SUPER_COMBAT_POTION2,
            ItemID.SUPER_COMBAT_POTION3,
            ItemID.SUPER_COMBAT_POTION4
    );

    public static final Set<Integer> DEFENCE_POTION_IDS = ImmutableSet.of(
            ItemID.DEFENCE_POTION1,
            ItemID.DEFENCE_POTION2,
            ItemID.DEFENCE_POTION3,
            ItemID.DEFENCE_POTION4,
            ItemID.SUPER_DEFENCE1,
            ItemID.SUPER_DEFENCE2,
            ItemID.SUPER_DEFENCE3,
            ItemID.SUPER_DEFENCE4,
            ItemID.DIVINE_SUPER_DEFENCE_POTION1,
            ItemID.DIVINE_SUPER_DEFENCE_POTION2,
            ItemID.DIVINE_SUPER_DEFENCE_POTION3,
            ItemID.DIVINE_SUPER_DEFENCE_POTION4,
            ItemID.DIVINE_SUPER_COMBAT_POTION1,
            ItemID.DIVINE_SUPER_COMBAT_POTION2,
            ItemID.DIVINE_SUPER_COMBAT_POTION3,
            ItemID.DIVINE_SUPER_COMBAT_POTION4,
            ItemID.SUPER_COMBAT_POTION1,
            ItemID.SUPER_COMBAT_POTION2,
            ItemID.SUPER_COMBAT_POTION3,
            ItemID.SUPER_COMBAT_POTION4
    );

    public static final Set<Integer> RANGED_POTION_IDS = ImmutableSet.of(
            ItemID.RANGING_POTION1,
            ItemID.RANGING_POTION2,
            ItemID.RANGING_POTION3,
            ItemID.RANGING_POTION4,
            ItemID.BASTION_POTION1,
            ItemID.BASTION_POTION2,
            ItemID.BASTION_POTION3,
            ItemID.BASTION_POTION4,
            ItemID.DIVINE_RANGING_POTION1,
            ItemID.DIVINE_RANGING_POTION2,
            ItemID.DIVINE_RANGING_POTION3,
            ItemID.DIVINE_RANGING_POTION4,
            ItemID.DIVINE_BASTION_POTION1,
            ItemID.DIVINE_BASTION_POTION2,
            ItemID.DIVINE_BASTION_POTION3,
            ItemID.DIVINE_BASTION_POTION4,
            ItemID.SUPER_RANGING_1,
            ItemID.SUPER_RANGING_2,
            ItemID.SUPER_RANGING_3,
            ItemID.SUPER_RANGING_4
    );

    public static final Set<Integer> MAGIC_POTION_IDS = ImmutableSet.of(
            ItemID.MAGIC_POTION1,
            ItemID.MAGIC_POTION2,
            ItemID.MAGIC_POTION3,
            ItemID.MAGIC_POTION4,
            ItemID.BATTLEMAGE_POTION1,
            ItemID.BATTLEMAGE_POTION2,
            ItemID.BATTLEMAGE_POTION3,
            ItemID.BATTLEMAGE_POTION4,
            ItemID.DIVINE_MAGIC_POTION1,
            ItemID.DIVINE_MAGIC_POTION2,
            ItemID.DIVINE_MAGIC_POTION3,
            ItemID.DIVINE_MAGIC_POTION4,
            ItemID.DIVINE_BATTLEMAGE_POTION1,
            ItemID.DIVINE_BATTLEMAGE_POTION2,
            ItemID.DIVINE_BATTLEMAGE_POTION3,
            ItemID.DIVINE_BATTLEMAGE_POTION4
    );

    public static final Set<Integer> ENERGY_POTION_IDS = ImmutableSet.of(
            ItemID.ENERGY_POTION1,
            ItemID.ENERGY_POTION2,
            ItemID.ENERGY_POTION3,
            ItemID.ENERGY_POTION4,
            ItemID.ENERGY_MIX1,
            ItemID.ENERGY_MIX2,
            ItemID.SUPER_ENERGY_MIX1,
            ItemID.SUPER_ENERGY_MIX2,
            ItemID.SUPER_ENERGY1,
            ItemID.SUPER_ENERGY2,
            ItemID.SUPER_ENERGY3,
            ItemID.SUPER_ENERGY4
    );

    public static final Set<Integer> MINEABLE_GEM_IDS = ImmutableSet.of(
            ItemID.UNCUT_SAPPHIRE,
            ItemID.UNCUT_EMERALD,
            ItemID.UNCUT_RUBY,
            ItemID.UNCUT_DIAMOND
    );

    public static final Set<Integer> ESSENCE_POUCH_IDS = ImmutableSet.of(
            ItemID.COLOSSAL_POUCH,
            ItemID.COLOSSAL_POUCH_26786,
            ItemID.GIANT_POUCH,
            ItemID.GIANT_POUCH_5515,
            ItemID.LARGE_POUCH,
            ItemID.LARGE_POUCH_5513,
            ItemID.MEDIUM_POUCH,
            ItemID.MEDIUM_POUCH_5511,
            ItemID.SMALL_POUCH
    );

    public static final Set<Integer> DEGRADED_ESSENCE_POUCH_IDS = ImmutableSet.of(
            ItemID.COLOSSAL_POUCH_26786,
            ItemID.GIANT_POUCH_5515,
            ItemID.LARGE_POUCH_5513,
            ItemID.MEDIUM_POUCH_5511
    );

    public static final Set<Integer> ARDOUGNE_CLOAK_IDS = ImmutableSet.of(
            ItemID.ARDOUGNE_CLOAK_1,
            ItemID.ARDOUGNE_CLOAK_2,
            ItemID.ARDOUGNE_CLOAK_3,
            ItemID.ARDOUGNE_CLOAK_4,
            ItemID.ARDOUGNE_CLOAK,
            ItemID.ARDOUGNE_MAX_CAPE
    );

    public static final Set<Integer> DUELING_RING_IDS = ImmutableSet.of(
            ItemID.RING_OF_DUELING1,
            ItemID.RING_OF_DUELING2,
            ItemID.RING_OF_DUELING3,
            ItemID.RING_OF_DUELING4,
            ItemID.RING_OF_DUELING5,
            ItemID.RING_OF_DUELING6,
            ItemID.RING_OF_DUELING7,
            ItemID.RING_OF_DUELING8
    );

    public static final Set<Integer> EXPLORERS_RING_IDS = ImmutableSet.of(
            ItemID.EXPLORERS_RING_2,
            ItemID.EXPLORERS_RING_3,
            ItemID.EXPLORERS_RING_4
    );

    public static final Set<Integer> AMULET_OF_GLORY_IDS = ImmutableSet.of(
            ItemID.AMULET_OF_GLORY1,
            ItemID.AMULET_OF_GLORY2,
            ItemID.AMULET_OF_GLORY3,
            ItemID.AMULET_OF_GLORY4,
            ItemID.AMULET_OF_GLORY5,
            ItemID.AMULET_OF_GLORY6,
            ItemID.AMULET_OF_GLORY_T1,
            ItemID.AMULET_OF_GLORY_T2,
            ItemID.AMULET_OF_GLORY_T3,
            ItemID.AMULET_OF_GLORY_T4,
            ItemID.AMULET_OF_GLORY_T5,
            ItemID.AMULET_OF_GLORY_T6,
            ItemID.AMULET_OF_ETERNAL_GLORY
    );

    public static final Set<Integer> SKILL_NECKLACE_IDS = ImmutableSet.of(
            ItemID.SKILLS_NECKLACE1,
            ItemID.SKILLS_NECKLACE2,
            ItemID.SKILLS_NECKLACE3,
            ItemID.SKILLS_NECKLACE4,
            ItemID.SKILLS_NECKLACE5,
            ItemID.SKILLS_NECKLACE6
    );

    public static final Set<Integer> COMPOST_BIN_IDS = ImmutableSet.of(
            NullObjectID.NULL_7836,
            NullObjectID.NULL_7837,
            NullObjectID.NULL_7838,
            NullObjectID.NULL_7839,
            NullObjectID.NULL_27112,
            NullObjectID.NULL_34631
    );

    public static final Set<Integer> NOTABLE_PRODUCE_IDS = ImmutableSet.of(
            ItemID.POTATO,
            ItemID.ONION,
            ItemID.TOMATO,
            ItemID.SWEETCORN,
            ItemID.STRAWBERRY,
            ItemID.WATERMELON,
            ItemID.SNAPE_GRASS,
            ItemID.MARIGOLDS,
            ItemID.ROSEMARY,
            ItemID.NASTURTIUMS,
            ItemID.WOAD_LEAF,
            ItemID.LIMPWURT_ROOT,
            ItemID.WHITE_LILY,
            ItemID.GRIMY_GUAM_LEAF,
            ItemID.GRIMY_MARRENTILL,
            ItemID.GRIMY_TARROMIN,
            ItemID.GRIMY_HARRALANDER,
            ItemID.GRIMY_RANARR_WEED,
            ItemID.GRIMY_TOADFLAX,
            ItemID.GRIMY_IRIT_LEAF,
            ItemID.GRIMY_AVANTOE,
            ItemID.GRIMY_KWUARM,
            ItemID.GRIMY_SNAPDRAGON,
            ItemID.GRIMY_CADANTINE,
            ItemID.GRIMY_LANTADYME,
            ItemID.GRIMY_DWARF_WEED,
            ItemID.GRIMY_TORSTOL,
            ItemID.BARLEY,
            ItemID.HAMMERSTONE_HOPS,
            ItemID.ASGARNIAN_HOPS,
            ItemID.JUTE_FIBRE,
            ItemID.YANILLIAN_HOPS,
            ItemID.KRANDORIAN_HOPS,
            ItemID.WILDBLOOD_HOPS,
            ItemID.REDBERRIES,
            ItemID.CADAVA_BERRIES,
            ItemID.DWELLBERRIES,
            ItemID.JANGERBERRIES,
            ItemID.WHITE_BERRIES,
            ItemID.POISON_IVY_BERRIES,
            ItemID.COOKING_APPLE,
            ItemID.BANANA,
            ItemID.ORANGE,
            ItemID.CURRY_LEAF,
            ItemID.PINEAPPLE,
            ItemID.PAPAYA_FRUIT,
            ItemID.COCONUT,
            ItemID.DRAGONFRUIT,
            ItemID.GIANT_SEAWEED,
            ItemID.GRAPES,
            ItemID.MUSHROOM,
            ItemID.CACTUS_SPINE,
            ItemID.POTATO_CACTUS
    );

    public static final Set<Integer> TOOL_LEPRECHAUN_IDS = ImmutableSet.of(
            NpcID.TOOL_LEPRECHAUN,
            NpcID.TOOL_LEPRECHAUN_757,
            NpcID.TOOL_LEPRECHAUN_7757
    );

    public static final Set<Integer> ALLOTMENT_PATCH_IDS = ImmutableSet.of(
            NullObjectID.NULL_8550,
            NullObjectID.NULL_8551,
            NullObjectID.NULL_8552,
            NullObjectID.NULL_8553,
            NullObjectID.NULL_8554,
            NullObjectID.NULL_8555,
            NullObjectID.NULL_8556,
            NullObjectID.NULL_8557,
            NullObjectID.NULL_21950,
            NullObjectID.NULL_27113,
            NullObjectID.NULL_27114,
            NullObjectID.NULL_33693,
            NullObjectID.NULL_33694
    );

    public static final Set<Integer> FLOWER_PATCH_IDS = ImmutableSet.of(
            NullObjectID.NULL_7847,
            NullObjectID.NULL_7848,
            NullObjectID.NULL_7849,
            NullObjectID.NULL_7850,
            NullObjectID.NULL_27111,
            NullObjectID.NULL_33649
    );

    public static final Set<Integer> HERB_PATCH_IDS = ImmutableSet.of(
            NullObjectID.NULL_8150,
            NullObjectID.NULL_8151,
            NullObjectID.NULL_8152,
            NullObjectID.NULL_8153,
            NullObjectID.NULL_9372,
            NullObjectID.NULL_18816,
            NullObjectID.NULL_27115,
            NullObjectID.NULL_33176,
            NullObjectID.NULL_33979
    );

    public static final Set<Integer> HOPS_PATCH_IDS = ImmutableSet.of();

    public static final Set<Integer> BUSH_PATCH_IDS = ImmutableSet.of(NullObjectID.NULL_34006);

    public static final Set<Integer> TREE_PATCH_IDS = ImmutableSet.of(
            NullObjectID.NULL_8388,
            NullObjectID.NULL_8389,
            NullObjectID.NULL_8390,
            NullObjectID.NULL_8391,
            NullObjectID.NULL_19147,
            NullObjectID.NULL_33732
    );

    public static final Set<Integer> FRUIT_TREE_PATCH_IDS = ImmutableSet.of(
            NullObjectID.NULL_7962,
            NullObjectID.NULL_7963,
            NullObjectID.NULL_7964,
            NullObjectID.NULL_7965,
            NullObjectID.NULL_26579,
            NullObjectID.NULL_34007
    );

    public static final Set<Integer> HARDWOOD_TREE_PATCH_IDS = ImmutableSet.of(
            NullObjectID.NULL_30480,
            NullObjectID.NULL_30481,
            NullObjectID.NULL_30482
    );

    public static final Set<Integer> SPIRIT_TREE_PATCH_IDS = ImmutableSet.of(NullObjectID.NULL_33733);

    public static final Set<Integer> SEAWEED_PATCH_IDS = ImmutableSet.of();

    public static final Set<Integer> CACTUS_PATCH_IDS = ImmutableSet.of(NullObjectID.NULL_33761);

    public static final Set<Integer> GRAPE_PATCH_IDS = ImmutableSet.of();

    public static final Set<Integer> MUSHROOM_PATCH_IDS = ImmutableSet.of();

    public static final Set<Integer> BELLADONNA_PATCH_IDS = ImmutableSet.of();

    public static final Set<Integer> HESPORI_PATCH_IDS = ImmutableSet.of(NullObjectID.NULL_34630);

    public static final Set<Integer> ANIMA_PATCH_IDS = ImmutableSet.of(NullObjectID.NULL_33998);

    public static final Set<Integer> CALQUAT_PATCH_IDS = ImmutableSet.of(
            NullObjectID.NULL_7807
    );

    public static final Set<Integer> CRYSTAL_PATCH_IDS = ImmutableSet.of();

    public static final Set<Integer> CELASTRUS_PATCH_IDS = ImmutableSet.of(NullObjectID.NULL_34629);

    public static final Set<Integer> REDWOOD_PATCH_IDS = ImmutableSet.of(NullObjectID.NULL_34055);

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
            ItemID.HAMMERSTONE_SEED,
            ItemID.ASGARNIAN_SEED,
            ItemID.JUTE_SEED,
            ItemID.YANILLIAN_SEED,
            ItemID.KRANDORIAN_SEED,
            ItemID.WILDBLOOD_SEED
    );

    public static final Set<Integer> BUSH_SEED_IDS = ImmutableSet.of(
            ItemID.REDBERRY_SEED,
            ItemID.CADAVABERRY_SEED,
            ItemID.DWELLBERRY_SEED,
            ItemID.JANGERBERRY_SEED,
            ItemID.WHITEBERRY_SEED,
            ItemID.POISON_IVY_SEED
    );

    public static final Set<Integer> TREE_SEED_IDS = ImmutableSet.of(
            ItemID.ACORN,
            ItemID.WILLOW_SEED,
            ItemID.MAPLE_SEED,
            ItemID.YEW_SEED,
            ItemID.MAGIC_SEED,
            ItemID.APPLE_TREE_SEED,
            ItemID.BANANA_TREE_SEED,
            ItemID.ORANGE_TREE_SEED,
            ItemID.CURRY_TREE_SEED,
            ItemID.PINEAPPLE_SEED,
            ItemID.PAPAYA_TREE_SEED,
            ItemID.PALM_TREE_SEED,
            ItemID.DRAGONFRUIT_TREE_SEED,
            ItemID.TEAK_SEED,
            ItemID.MAHOGANY_SEED,
            ItemID.CALQUAT_TREE_SEED,
            ItemID.CRYSTAL_SEED,
            ItemID.SPIRIT_SEED,
            ItemID.CELASTRUS_SEED,
            ItemID.REDWOOD_TREE_SEED
    );

    public static final Set<Integer> TREE_SEEDLING_IDS = ImmutableSet.of(
            ItemID.OAK_SEEDLING,
            ItemID.WILLOW_SEEDLING,
            ItemID.MAPLE_SEEDLING,
            ItemID.YEW_SEEDLING,
            ItemID.MAGIC_SEEDLING,
            ItemID.APPLE_SEEDLING,
            ItemID.BANANA_SEEDLING,
            ItemID.ORANGE_SEEDLING,
            ItemID.CURRY_SEEDLING,
            ItemID.PINEAPPLE_SEEDLING,
            ItemID.PAPAYA_SEEDLING,
            ItemID.PALM_SEEDLING,
            ItemID.DRAGONFRUIT_SEEDLING,
            ItemID.TEAK_SEEDLING,
            ItemID.MAHOGANY_SEEDLING,
            ItemID.CALQUAT_SEEDLING,
            ItemID.CRYSTAL_SEEDLING,
            ItemID.SPIRIT_SEEDLING,
            ItemID.CELASTRUS_SEEDLING,
            ItemID.REDWOOD_SEEDLING
    );

    public static final Set<Integer> TREE_SAPLING_IDS = ImmutableSet.of(
            ItemID.OAK_SAPLING,
            ItemID.WILLOW_SAPLING,
            ItemID.MAPLE_SAPLING,
            ItemID.YEW_SAPLING,
            ItemID.MAGIC_SAPLING
    );

    public static final Set<Integer> FRUIT_TREE_SAPLING_IDS = ImmutableSet.of(
            ItemID.APPLE_SAPLING,
            ItemID.BANANA_SAPLING,
            ItemID.ORANGE_SAPLING,
            ItemID.CURRY_SAPLING,
            ItemID.PINEAPPLE_SAPLING,
            ItemID.PAPAYA_SAPLING,
            ItemID.PALM_SAPLING,
            ItemID.DRAGONFRUIT_SAPLING
    );

    public static final Set<Integer> HARDWOOD_TREE_SAPLING_IDS = ImmutableSet.of(
            ItemID.TEAK_SAPLING,
            ItemID.MAHOGANY_SAPLING
    );

    public static final Set<Integer> ANIMA_SEED_IDS = ImmutableSet.of(
            ItemID.KRONOS_SEED,
            ItemID.IASOR_SEED,
            ItemID.ATTAS_SEED
    );

    public static final Set<Integer> CACTUS_SEED_IDS = ImmutableSet.of(
            ItemID.CACTUS_SEED,
            ItemID.POTATO_CACTUS_SEED
    );

    public static final Set<Integer> COMPOST_IDS = ImmutableSet.of(
            ItemID.COMPOST,
            ItemID.SUPERCOMPOST,
            ItemID.ULTRACOMPOST,
            ItemID.BOTTOMLESS_COMPOST_BUCKET,
            ItemID.BOTTOMLESS_COMPOST_BUCKET_22997
    );

    public static final Set<Integer> WATERING_CAN_IDS = ImmutableSet.of(
            ItemID.WATERING_CAN1,
            ItemID.WATERING_CAN2,
            ItemID.WATERING_CAN3,
            ItemID.WATERING_CAN4,
            ItemID.WATERING_CAN5,
            ItemID.WATERING_CAN6,
            ItemID.WATERING_CAN7,
            ItemID.WATERING_CAN8
    );

    public static final Set<Integer> GRIMY_HERB_IDS = ImmutableSet.of(
            ItemID.GRIMY_GUAM_LEAF,
            ItemID.GRIMY_MARRENTILL,
            ItemID.GRIMY_TARROMIN,
            ItemID.GRIMY_HARRALANDER,
            ItemID.GRIMY_RANARR_WEED,
            ItemID.GRIMY_TOADFLAX,
            ItemID.GRIMY_IRIT_LEAF,
            ItemID.GRIMY_AVANTOE,
            ItemID.GRIMY_KWUARM,
            ItemID.GRIMY_SNAPDRAGON,
            ItemID.GRIMY_CADANTINE,
            ItemID.GRIMY_LANTADYME,
            ItemID.GRIMY_DWARF_WEED,
            ItemID.GRIMY_TORSTOL
    );

    public static final Set<Integer> CLEAN_HERB_IDS = ImmutableSet.of(
            ItemID.GUAM_LEAF,
            ItemID.MARRENTILL,
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
            ItemID.CRAFTING_CAPE,
            ItemID.CRAFTING_CAPET
    );

    public static final Set<Integer> CONSTRUCTION_CAPE_IDS = ImmutableSet.of(
            ItemID.CONSTRUCT_CAPE,
            ItemID.CONSTRUCT_CAPET
    );

    public static final Set<Integer> REJUVENATION_POOL_IDS = ImmutableSet.of(
            ObjectID.POOL_OF_REFRESHMENT,
            ObjectID.ORNATE_POOL_OF_REJUVENATION,
            ObjectID.FROZEN_ORNATE_POOL_OF_REJUVENATION
    );

    public static final Set<Integer> LIZARDMAN_SHAMAN_IDS = ImmutableSet.of(
            NpcID.LIZARDMAN_SHAMAN,
            NpcID.LIZARDMAN_SHAMAN_6767,
            NpcID.LIZARDMAN_SHAMAN_7573,
            NpcID.LIZARDMAN_SHAMAN_7574,
            NpcID.LIZARDMAN_SHAMAN_7745,
            NpcID.LIZARDMAN_SHAMAN_7744,
            NpcID.LIZARDMAN_SHAMAN_8565
    );

    public static final Set<Integer> CELL_IDS = ImmutableSet.of(
            ItemID.WEAK_CELL,
            ItemID.MEDIUM_CELL,
            ItemID.STRONG_CELL,
            ItemID.OVERCHARGED_CELL
    );

    public static final Set<Integer> INACTIVE_CELL_TILE_IDS = ImmutableSet.of(
            ObjectID.INACTIVE_CELL_TILE,
            ObjectID.INACTIVE_CELL_TILE_43739
    );

    public static final Set<Integer> ACTIVE_CELL_TILE_IDS = ImmutableSet.of(
            ObjectID.WEAK_CELL_TILE,
            ObjectID.MEDIUM_CELL_TILE,
            ObjectID.STRONG_CELL_TILE,
            ObjectID.OVERPOWERED_CELL_TILE
    );

    public static final Set<Integer> SPECIAL_AXE_IDS = ImmutableSet.of(
            ItemID.DRAGON_AXE,
            ItemID.DRAGON_AXE_OR,
            ItemID.INFERNAL_AXE,
            ItemID.INFERNAL_AXE_OR,
            ItemID.INFERNAL_AXE_UNCHARGED,
            ItemID.INFERNAL_AXE_UNCHARGED_25371,
            ItemID.CRYSTAL_AXE,
            ItemID.CRYSTAL_AXE_23862,
            ItemID.CRYSTAL_AXE_INACTIVE,
            ItemID._3RD_AGE_AXE
    );

    public static final Set<Integer> SPECIAL_PICKAXE_IDS = ImmutableSet.of(
            ItemID.DRAGON_PICKAXE,
            ItemID.DRAGON_PICKAXE_OR,
            ItemID.INFERNAL_PICKAXE,
            ItemID.INFERNAL_PICKAXE_OR,
            ItemID.INFERNAL_PICKAXE_UNCHARGED,
            ItemID.INFERNAL_PICKAXE_UNCHARGED_25369,
            ItemID.CRYSTAL_PICKAXE,
            ItemID.CRYSTAL_PICKAXE_23863,
            ItemID.CRYSTAL_PICKAXE_INACTIVE,
            ItemID._3RD_AGE_PICKAXE
    );

    public static final Set<Integer> SPECIAL_HARPOON_IDS = ImmutableSet.of(
            ItemID.DRAGON_HARPOON,
            ItemID.DRAGON_HARPOON_OR,
            ItemID.INFERNAL_HARPOON,
            ItemID.INFERNAL_HARPOON_OR,
            ItemID.INFERNAL_HARPOON_UNCHARGED,
            ItemID.INFERNAL_HARPOON_UNCHARGED_25367,
            ItemID.CRYSTAL_HARPOON,
            ItemID.CRYSTAL_HARPOON_23864,
            ItemID.CRYSTAL_HARPOON_INACTIVE
    );

    public static final Set<Integer> PHARAOHS_SCEPTRE_IDS = ImmutableSet.of(
            ItemID.PHARAOHS_SCEPTRE,
            ItemID.PHARAOHS_SCEPTRE_9045,
            ItemID.PHARAOHS_SCEPTRE_9046,
            ItemID.PHARAOHS_SCEPTRE_9047,
            ItemID.PHARAOHS_SCEPTRE_9048,
            ItemID.PHARAOHS_SCEPTRE_9049,
            ItemID.PHARAOHS_SCEPTRE_9050,
            ItemID.PHARAOHS_SCEPTRE_9051,
            ItemID.PHARAOHS_SCEPTRE_13074,
            ItemID.PHARAOHS_SCEPTRE_13075,
            ItemID.PHARAOHS_SCEPTRE_13076,
            ItemID.PHARAOHS_SCEPTRE_13077,
            ItemID.PHARAOHS_SCEPTRE_13078,
            ItemID.PHARAOHS_SCEPTRE_16176,
            ItemID.PHARAOHS_SCEPTRE_21445,
            ItemID.PHARAOHS_SCEPTRE_21446,
            ItemID.PHARAOHS_SCEPTRE_26948,
            ItemID.PHARAOHS_SCEPTRE_26950
    );

    public static final Set<Integer> BARROWS_UNDEGRADED_IDS = ImmutableSet.of(
            ItemID.AHRIMS_HOOD,
            ItemID.AHRIMS_STAFF,
            ItemID.AHRIMS_ROBETOP,
            ItemID.AHRIMS_ROBESKIRT,
            ItemID.DHAROKS_HELM,
            ItemID.DHAROKS_GREATAXE,
            ItemID.DHAROKS_PLATEBODY,
            ItemID.DHAROKS_PLATELEGS,
            ItemID.GUTHANS_HELM,
            ItemID.GUTHANS_WARSPEAR,
            ItemID.GUTHANS_PLATEBODY,
            ItemID.GUTHANS_CHAINSKIRT,
            ItemID.KARILS_COIF,
            ItemID.KARILS_CROSSBOW,
            ItemID.KARILS_LEATHERTOP,
            ItemID.KARILS_LEATHERSKIRT,
            ItemID.TORAGS_HELM,
            ItemID.TORAGS_HAMMERS,
            ItemID.TORAGS_PLATEBODY,
            ItemID.TORAGS_PLATELEGS,
            ItemID.VERACS_HELM,
            ItemID.VERACS_FLAIL,
            ItemID.VERACS_BRASSARD,
            ItemID.VERACS_PLATESKIRT
    );

    public static final Set<Integer> TRIDENT_IDS = ImmutableSet.of(
            ItemID.TRIDENT_OF_THE_SEAS_E,
            ItemID.TRIDENT_OF_THE_SEAS,
            ItemID.TRIDENT_OF_THE_SEAS_FULL,
            ItemID.TRIDENT_OF_THE_SWAMP_E,
            ItemID.TRIDENT_OF_THE_SWAMP
    );

    public static final Set<Integer> TELEPORT_IDS = ImmutableSet.of(
            ItemID.ANNAKARL_TELEPORT,
            ItemID.APE_ATOLL_TELEPORT,
            ItemID.ARCEUUS_LIBRARY_TELEPORT,
            ItemID.ARDOUGNE_TELEPORT,
            ItemID.ARDOUGNE_TELEPORT_SCROLL,
            ItemID.BARBARIAN_TELEPORT,
            ItemID.BARROWS_TELEPORT,
            ItemID.BATTLEFRONT_TELEPORT,
            ItemID.BLIGHTED_TELEPORT_SPELL_SACK,
            ItemID.BRIMHAVEN_TELEPORT,
            ItemID.CAMELOT_TELEPORT,
            ItemID.CARRALLANGER_TELEPORT,
            ItemID.CATHERBY_TELEPORT,
            ItemID.CEMETERY_TELEPORT,
            ItemID.CORRUPTED_TELEPORT_CRYSTAL,
            ItemID.CRYSTAL_TELEPORT_SEED,
            ItemID.DAREEYAK_TELEPORT,
            ItemID.DEADMAN_TELEPORT_TABLET,
            ItemID.DIGSITE_TELEPORT,
            ItemID.DRAYNOR_MANOR_TELEPORT,
            ItemID.ENHANCED_CRYSTAL_TELEPORT_SEED,
            ItemID.ETERNAL_TELEPORT_CRYSTAL,
            ItemID.FALADOR_TELEPORT,
            ItemID.FELDIP_HILLS_TELEPORT,
            ItemID.FENKENSTRAINS_CASTLE_TELEPORT,
            ItemID.FISHING_GUILD_TELEPORT,
            ItemID.GHORROCK_TELEPORT,
            ItemID.HARMONY_ISLAND_TELEPORT,
            ItemID.HOSIDIUS_TELEPORT,
            ItemID.ICE_PLATEAU_TELEPORT,
            ItemID.IORWERTH_CAMP_TELEPORT,
            ItemID.KEY_MASTER_TELEPORT,
            ItemID.KHARYRLL_TELEPORT,
            ItemID.KHAZARD_TELEPORT,
            ItemID.KOUREND_CASTLE_TELEPORT,
            ItemID.LASSAR_TELEPORT,
            ItemID.LUMBERYARD_TELEPORT,
            ItemID.LUMBRIDGE_TELEPORT,
            ItemID.LUNAR_ISLE_TELEPORT,
            ItemID.MIND_ALTAR_TELEPORT,
            ItemID.MOONCLAN_TELEPORT,
            ItemID.MORTTON_TELEPORT,
            ItemID.MOS_LEHARMLESS_TELEPORT,
            ItemID.NARDAH_TELEPORT,
            ItemID.OURANIA_TELEPORT,
            ItemID.PADDEWWA_TELEPORT,
            ItemID.PEST_CONTROL_TELEPORT,
            ItemID.PISCATORIS_TELEPORT,
            ItemID.POLLNIVNEACH_TELEPORT,
            ItemID.PRIFDDINAS_TELEPORT,
            ItemID.RELLEKKA_TELEPORT,
            ItemID.REVENANT_CAVE_TELEPORT,
            ItemID.RIMMINGTON_TELEPORT,
            ItemID.SALVE_GRAVEYARD_TELEPORT,
            ItemID.SCAPERUNE_TELEPORT,
            ItemID.SENNTISTEN_TELEPORT,
            ItemID.SHATTERED_TELEPORT_SCROLL,
            ItemID.SPEEDY_TELEPORT_SCROLL,
            ItemID.TAI_BWO_WANNAI_TELEPORT,
            ItemID.TARGET_TELEPORT,
            ItemID.TARGET_TELEPORT_SCROLL,
            ItemID.TAVERLEY_TELEPORT,
            ItemID.TELEPORT_CARD,
            ItemID.TELEPORT_CRYSTAL,
            ItemID.TELEPORT_CRYSTAL_1,
            ItemID.TELEPORT_CRYSTAL_2,
            ItemID.TELEPORT_CRYSTAL_3,
            ItemID.TELEPORT_CRYSTAL_4,
            ItemID.TELEPORT_CRYSTAL_5,
            ItemID.TELEPORT_FOCUS,
            ItemID.TELEPORT_TO_HOUSE,
            ItemID.TELEPORT_TRAP,
            ItemID.TRAILBLAZER_RELOADED_HOME_TELEPORT_SCROLL,
            ItemID.TRAILBLAZER_TELEPORT_SCROLL,
            ItemID.TROLLHEIM_TELEPORT,
            ItemID.TWISTED_TELEPORT_SCROLL,
            ItemID.VARROCK_TELEPORT,
            ItemID.VOLCANIC_MINE_TELEPORT,
            ItemID.WATCHTOWER_TELEPORT,
            ItemID.WATERBIRTH_TELEPORT,
            ItemID.WATSON_TELEPORT,
            ItemID.WEST_ARDOUGNE_TELEPORT,
            ItemID.WILDERNESS_CRABS_TELEPORT,
            ItemID.WISE_OLD_MANS_TELEPORT_TABLET,
            ItemID.YANILLE_TELEPORT,
            ItemID.ZULANDRA_TELEPORT
    );

    public static final Set<Integer> FOOD_ITEM_IDS = ImmutableSet.of(
            ItemID.ANGLERFISH, ItemID.BLIGHTED_ANGLERFISH,
            ItemID.DARK_CRAB,
            ItemID.COOKED_KARAMBWAN,
            ItemID.TUNA_POTATO,
            ItemID.MANTA_RAY, ItemID.BLIGHTED_MANTA_RAY,
            ItemID.SEA_TURTLE,
            ItemID.SHARK, ItemID.SHARK_6969, ItemID.SHARK_20390,
            ItemID.PADDLEFISH,
            ItemID.PYSK_FISH_0, ItemID.SUPHI_FISH_1, ItemID.LECKISH_FISH_2, ItemID.BRAWK_FISH_3, ItemID.MYCIL_FISH_4, ItemID.ROQED_FISH_5, ItemID.KYREN_FISH_6,
            ItemID.GUANIC_BAT_0, ItemID.PRAEL_BAT_1, ItemID.GIRAL_BAT_2, ItemID.PHLUXIA_BAT_3, ItemID.KRYKET_BAT_4, ItemID.MURNG_BAT_5, ItemID.PSYKK_BAT_6,
            ItemID.UGTHANKI_KEBAB, ItemID.UGTHANKI_KEBAB_1885, ItemID.SUPER_KEBAB,
            ItemID.MUSHROOM_POTATO,
            ItemID.CURRY,
            ItemID.EGG_POTATO,
            ItemID.POTATO_WITH_CHEESE,
            ItemID.MONKFISH, ItemID.MONKFISH_20547,
            ItemID.COOKED_JUBBLY,
            ItemID.COOKED_OOMLIE_WRAP,
            ItemID.CHILLI_POTATO,
            ItemID.POTATO_WITH_BUTTER,
            ItemID.SWORDFISH,
            ItemID.BASS,
            ItemID.TUNA_AND_CORN,
            ItemID.LOBSTER,
            ItemID.STEW,
            ItemID.JUG_OF_WINE,
            ItemID.LAVA_EEL,
            ItemID.CAVE_EEL,
            ItemID.MUSHROOM__ONION,
            ItemID.RAINBOW_FISH,
            ItemID.COOKED_FISHCAKE,
            ItemID.COOKED_CHOMPY, ItemID.COOKED_CHOMPY_7228,
            ItemID.COOKED_SWEETCORN, ItemID.SWEETCORN_7088,
            ItemID.KEBAB,
            ItemID.DRAGONFRUIT,
            ItemID.TUNA, ItemID.CHOPPED_TUNA,
            ItemID.SALMON,
            ItemID.EGG_AND_TOMATO,
            ItemID.PEACH,
            ItemID.COOKED_SLIMY_EEL,
            ItemID.PIKE,
            ItemID.COD,
            ItemID.ROAST_BEAST_MEAT,
            ItemID.TROUT,
            ItemID.PAPAYA_FRUIT,
            ItemID.SPIDER_ON_STICK, ItemID.SPIDER_ON_STICK_6297, ItemID.SPIDER_ON_SHAFT, ItemID.SPIDER_ON_SHAFT_6299, ItemID.SPIDER_ON_SHAFT_6303,
            ItemID.FAT_SNAIL_MEAT,
            ItemID.MACKEREL,
            ItemID.GIANT_CARP,
            ItemID.ROAST_BIRD_MEAT,
            ItemID.FROG_SPAWN,
            ItemID.COOKED_MYSTERY_MEAT,
            ItemID.COOKED_RABBIT,
            ItemID.CHILLI_CON_CARNE,
            ItemID.FRIED_MUSHROOMS,
            ItemID.FRIED_ONIONS,
            ItemID.SCRAMBLED_EGG,
            ItemID.HERRING,
            ItemID.THIN_SNAIL_MEAT, ItemID.LEAN_SNAIL_MEAT,
            ItemID.BREAD,
            ItemID.BAKED_POTATO,
            ItemID.ONION__TOMATO,
            ItemID.SLICE_OF_CAKE, ItemID.CHOCOLATE_SLICE,
            ItemID.SARDINE,
            ItemID.UGTHANKI_MEAT,
            ItemID.COOKED_MEAT, ItemID.COOKED_MEAT_4293,
            ItemID.COOKED_CHICKEN, ItemID.COOKED_CHICKEN_4291,
            ItemID.SPICY_SAUCE,
            ItemID.CHEESE,
            ItemID.SPICY_MINCED_MEAT,
            ItemID.MINCED_MEAT,
            ItemID.BANANA, ItemID.SLICED_BANANA,
            ItemID.TOMATO, ItemID.CHOPPED_TOMATO, ItemID.SPICY_TOMATO,
            ItemID.ANCHOVIES,
            ItemID.SHRIMPS,
            ItemID.POTATO,
            ItemID.WATERMELON_SLICE, ItemID.PINEAPPLE_RING, ItemID.PINEAPPLE_CHUNKS,
            ItemID.ONION, ItemID.CHOPPED_ONION,
            ItemID.ORANGE, ItemID.ORANGE_SLICES,
            ItemID.STRAWBERRY,
            ItemID.CABBAGE,
            ItemID.MINT_CAKE,
            ItemID.PURPLE_SWEETS, ItemID.PURPLE_SWEETS_10476,
            ItemID.HONEY_LOCUST,
            ItemID.BANDAGES, ItemID.BANDAGES_25202, ItemID.BANDAGES_25730,
            ItemID.STRANGE_FRUIT,
            ItemID.WHITE_TREE_FRUIT,
            ItemID.GOUT_TUBER,
            ItemID.JANGERBERRIES,
            ItemID.DWELLBERRIES,
            ItemID.CAVE_NIGHTSHADE,
            ItemID.POT_OF_CREAM,
            ItemID.EQUA_LEAVES,
            ItemID.EDIBLE_SEAWEED,
            ItemID.SCARRED_SCRAPS,
            ItemID.RATIONS
    );

    public static final Set<Integer> GRACEFUL_HOOD = ImmutableSet.of(
            ItemID.GRACEFUL_HOOD,
            ItemID.GRACEFUL_HOOD_11851,
            ItemID.GRACEFUL_HOOD_13579,
            ItemID.GRACEFUL_HOOD_13580,
            ItemID.GRACEFUL_HOOD_13591,
            ItemID.GRACEFUL_HOOD_13592,
            ItemID.GRACEFUL_HOOD_13603,
            ItemID.GRACEFUL_HOOD_13604,
            ItemID.GRACEFUL_HOOD_13615,
            ItemID.GRACEFUL_HOOD_13616,
            ItemID.GRACEFUL_HOOD_13627,
            ItemID.GRACEFUL_HOOD_13628,
            ItemID.GRACEFUL_HOOD_13667,
            ItemID.GRACEFUL_HOOD_13668,
            ItemID.GRACEFUL_HOOD_21061,
            ItemID.GRACEFUL_HOOD_21063,
            ItemID.GRACEFUL_HOOD_24743,
            ItemID.GRACEFUL_HOOD_24745,
            ItemID.GRACEFUL_HOOD_25069,
            ItemID.GRACEFUL_HOOD_25071
    );

    public static final Set<Integer> GRACEFUL_TOP = ImmutableSet.of(
            ItemID.GRACEFUL_TOP,
            ItemID.GRACEFUL_TOP_11855,
            ItemID.GRACEFUL_TOP_13583,
            ItemID.GRACEFUL_TOP_13584,
            ItemID.GRACEFUL_TOP_13595,
            ItemID.GRACEFUL_TOP_13596,
            ItemID.GRACEFUL_TOP_13607,
            ItemID.GRACEFUL_TOP_13608,
            ItemID.GRACEFUL_TOP_13619,
            ItemID.GRACEFUL_TOP_13620,
            ItemID.GRACEFUL_TOP_13631,
            ItemID.GRACEFUL_TOP_13632,
            ItemID.GRACEFUL_TOP_13671,
            ItemID.GRACEFUL_TOP_13672,
            ItemID.GRACEFUL_TOP_21067,
            ItemID.GRACEFUL_TOP_21069,
            ItemID.GRACEFUL_TOP_24749,
            ItemID.GRACEFUL_TOP_24751,
            ItemID.GRACEFUL_TOP_25075,
            ItemID.GRACEFUL_TOP_25077
    );

    public static final Set<Integer> GRACEFUL_LEGS = ImmutableSet.of(
            ItemID.GRACEFUL_LEGS,
            ItemID.GRACEFUL_LEGS_11857,
            ItemID.GRACEFUL_LEGS_13585,
            ItemID.GRACEFUL_LEGS_13586,
            ItemID.GRACEFUL_LEGS_13597,
            ItemID.GRACEFUL_LEGS_13598,
            ItemID.GRACEFUL_LEGS_13609,
            ItemID.GRACEFUL_LEGS_13610,
            ItemID.GRACEFUL_LEGS_13621,
            ItemID.GRACEFUL_LEGS_13622,
            ItemID.GRACEFUL_LEGS_13633,
            ItemID.GRACEFUL_LEGS_13634,
            ItemID.GRACEFUL_LEGS_13673,
            ItemID.GRACEFUL_LEGS_13674,
            ItemID.GRACEFUL_LEGS_21070,
            ItemID.GRACEFUL_LEGS_21072,
            ItemID.GRACEFUL_LEGS_24752,
            ItemID.GRACEFUL_LEGS_24754,
            ItemID.GRACEFUL_LEGS_25078,
            ItemID.GRACEFUL_LEGS_25080
    );

    public static final Set<Integer> RING_OF_ENDURANCE_IDS = ImmutableSet.of(
            ItemID.RING_OF_ENDURANCE,
            ItemID.RING_OF_ENDURANCE_UNCHARGED,
            ItemID.RING_OF_ENDURANCE_UNCHARGED_24844
    );

    public static final Set<Integer> GRACEFUL_BOOTS = ImmutableSet.of(
            ItemID.GRACEFUL_BOOTS,
            ItemID.GRACEFUL_BOOTS_11861,
            ItemID.GRACEFUL_BOOTS_13589,
            ItemID.GRACEFUL_BOOTS_13590,
            ItemID.GRACEFUL_BOOTS_13601,
            ItemID.GRACEFUL_BOOTS_13602,
            ItemID.GRACEFUL_BOOTS_13613,
            ItemID.GRACEFUL_BOOTS_13614,
            ItemID.GRACEFUL_BOOTS_13625,
            ItemID.GRACEFUL_BOOTS_13626,
            ItemID.GRACEFUL_BOOTS_13637,
            ItemID.GRACEFUL_BOOTS_13638,
            ItemID.GRACEFUL_BOOTS_13677,
            ItemID.GRACEFUL_BOOTS_13678,
            ItemID.GRACEFUL_BOOTS_21076,
            ItemID.GRACEFUL_BOOTS_21078,
            ItemID.GRACEFUL_BOOTS_24758,
            ItemID.GRACEFUL_BOOTS_24760,
            ItemID.GRACEFUL_BOOTS_25084,
            ItemID.GRACEFUL_BOOTS_25086
    );

    public static final Set<Integer> GRACEFUL_GLOVES = ImmutableSet.of(
            ItemID.GRACEFUL_GLOVES,
            ItemID.GRACEFUL_GLOVES_11859,
            ItemID.GRACEFUL_GLOVES_13587,
            ItemID.GRACEFUL_GLOVES_13588,
            ItemID.GRACEFUL_GLOVES_13599,
            ItemID.GRACEFUL_GLOVES_13600,
            ItemID.GRACEFUL_GLOVES_13611,
            ItemID.GRACEFUL_GLOVES_13612,
            ItemID.GRACEFUL_GLOVES_13623,
            ItemID.GRACEFUL_GLOVES_13624,
            ItemID.GRACEFUL_GLOVES_13635,
            ItemID.GRACEFUL_GLOVES_13636,
            ItemID.GRACEFUL_GLOVES_13675,
            ItemID.GRACEFUL_GLOVES_13676,
            ItemID.GRACEFUL_GLOVES_21073,
            ItemID.GRACEFUL_GLOVES_21075,
            ItemID.GRACEFUL_GLOVES_24755,
            ItemID.GRACEFUL_GLOVES_24757,
            ItemID.GRACEFUL_GLOVES_25081,
            ItemID.GRACEFUL_GLOVES_25083
    );

    public static final Set<Integer> GRACEFUL_CAPE = ImmutableSet.of(
            ItemID.GRACEFUL_CAPE,
            ItemID.GRACEFUL_CAPE_11853,
            ItemID.GRACEFUL_CAPE_13581,
            ItemID.GRACEFUL_CAPE_13582,
            ItemID.GRACEFUL_CAPE_13593,
            ItemID.GRACEFUL_CAPE_13594,
            ItemID.GRACEFUL_CAPE_13605,
            ItemID.GRACEFUL_CAPE_13606,
            ItemID.GRACEFUL_CAPE_13617,
            ItemID.GRACEFUL_CAPE_13618,
            ItemID.GRACEFUL_CAPE_13629,
            ItemID.GRACEFUL_CAPE_13630,
            ItemID.GRACEFUL_CAPE_13669,
            ItemID.GRACEFUL_CAPE_13670,
            ItemID.GRACEFUL_CAPE_21064,
            ItemID.GRACEFUL_CAPE_21066,
            ItemID.GRACEFUL_CAPE_24746,
            ItemID.GRACEFUL_CAPE_24748,
            ItemID.GRACEFUL_CAPE_25072,
            ItemID.GRACEFUL_CAPE_25074
    );

    public static final Set<Integer> BANK_OBJECT_IDS =
            ImmutableSet.of(
                    ObjectID.BANK_BOOTH,
                    ObjectID.BANK_BOOTH_10083,
                    ObjectID.BANK_BOOTH_10355,
                    ObjectID.BANK_BOOTH_10357,
                    ObjectID.BANK_BOOTH_10517,
                    ObjectID.BANK_BOOTH_10527,
                    ObjectID.BANK_BOOTH_10583,
                    ObjectID.BANK_BOOTH_10584,
                    NullObjectID.NULL_10777,
                    ObjectID.BANK_BOOTH_11338,
                    ObjectID.BANK_BOOTH_12798,
                    ObjectID.BANK_BOOTH_12799,
                    ObjectID.BANK_BOOTH_12800,
                    ObjectID.BANK_BOOTH_12801,
                    ObjectID.BANK_BOOTH_14367,
                    ObjectID.BANK_BOOTH_14368,
                    ObjectID.BANK_BOOTH_16642,
                    ObjectID.BANK_BOOTH_16700,
                    ObjectID.BANK_BOOTH_18491,
                    ObjectID.BANK_BOOTH_20325,
                    ObjectID.BANK_BOOTH_20326,
                    ObjectID.BANK_BOOTH_20327,
                    ObjectID.BANK_BOOTH_20328,
                    ObjectID.BANK_BOOTH_22819,
                    ObjectID.BANK_BOOTH_24101,
                    ObjectID.BANK_BOOTH_24347,
                    ObjectID.BANK_BOOTH_25808,
                    ObjectID.BANK_BOOTH_27254,
                    ObjectID.BANK_BOOTH_27260,
                    ObjectID.BANK_BOOTH_27263,
                    ObjectID.BANK_BOOTH_27265,
                    ObjectID.BANK_BOOTH_27267,
                    ObjectID.BANK_BOOTH_27292,
                    ObjectID.BANK_BOOTH_27718,
                    ObjectID.BANK_BOOTH_27719,
                    ObjectID.BANK_BOOTH_27720,
                    ObjectID.BANK_BOOTH_27721,
                    ObjectID.BANK_BOOTH_28429,
                    ObjectID.BANK_BOOTH_28430,
                    ObjectID.BANK_BOOTH_28431,
                    ObjectID.BANK_BOOTH_28432,
                    ObjectID.BANK_BOOTH_28433,
                    ObjectID.BANK_BOOTH_28546,
                    ObjectID.BANK_BOOTH_28547,
                    ObjectID.BANK_BOOTH_28548,
                    ObjectID.BANK_BOOTH_28549,
                    ObjectID.BANK_BOOTH_32666,
                    NullObjectID.NULL_34810,
                    ObjectID.BANK_BOOTH_36559,
                    ObjectID.BANK_BOOTH_37959,
                    ObjectID.BANK_BOOTH_39238,
                    ObjectID.BANK_BOOTH_42837,
                    ObjectID.BANK_CHEST,
                    ObjectID.BANK_CHEST_4483,
                    ObjectID.BANK_CHEST_10562,
                    ObjectID.BANK_CHEST_14382,
                    ObjectID.BANK_CHEST_14886,
                    ObjectID.BANK_CHEST_16695,
                    ObjectID.BANK_CHEST_16696,
                    ObjectID.BANK_CHEST_19051,
                    ObjectID.BANK_CHEST_21301,
                    ObjectID.BANK_CHEST_26707,
                    ObjectID.BANK_CHEST_26711,
                    ObjectID.BANK_CHEST_28594,
                    ObjectID.BANK_CHEST_28595,
                    ObjectID.BANK_CHEST_28816,
                    ObjectID.BANK_CHEST_28861,
                    ObjectID.BANK_CHEST_29321,
                    ObjectID.BANK_CHEST_30087,
                    ObjectID.BANK_CHEST_30267,
                    ObjectID.BANK_CHESTWRECK,
                    ObjectID.BANK_CHEST_30926,
                    ObjectID.BANK_CHEST_30989,
                    ObjectID.BANK_CHEST_34343,
                    ObjectID.BANK_CHEST_40473,
                    ObjectID.BANK_CHEST_41315,
                    ObjectID.BANK_CHEST_41493,
                    ObjectID.BANK_CHEST_43697,
                    NullObjectID.NULL_12308,
                    ObjectID.BANK_CHEST_44630,
                    ObjectID.GRAND_EXCHANGE_BOOTH,
                    ObjectID.GRAND_EXCHANGE_BOOTH_10061,
                    ObjectID.GRAND_EXCHANGE_BOOTH_30390);

    public static final Set<Integer> BANK_NPC_IDS =
            ImmutableSet.of(
                    NpcID.BANKER,
                    NpcID.BANKER_1479,
                    NpcID.BANKER_1480,
                    NpcID.BANKER_1613,
                    NpcID.BANKER_1618,
                    NpcID.BANKER_1633,
                    NpcID.BANKER_1634,
                    NpcID.BANKER_2117,
                    NpcID.BANKER_2118,
                    NpcID.BANKER_2119,
                    NpcID.BANKER_2292,
                    NpcID.BANKER_2293,
                    NpcID.BANKER_2368,
                    NpcID.BANKER_2369,
                    NpcID.BANKER_2633,
                    NpcID.BANKER_2897,
                    NpcID.BANKER_2898,
                    NpcID.GHOST_BANKER,
                    NpcID.BANKER_3089,
                    NpcID.BANKER_3090,
                    NpcID.BANKER_3091,
                    NpcID.BANKER_3092,
                    NpcID.BANKER_3093,
                    NpcID.BANKER_3094,
                    NpcID.BANKER_TUTOR,
                    NpcID.BANKER_3318,
                    NpcID.SIRSAL_BANKER,
                    NpcID.BANKER_3887,
                    NpcID.BANKER_3888,
                    NpcID.BANKER_4054,
                    NpcID.BANKER_4055,
                    NpcID.NARDAH_BANKER,
                    NpcID.GNOME_BANKER,
                    NpcID.BANKER_6859,
                    NpcID.BANKER_6860,
                    NpcID.BANKER_6861,
                    NpcID.BANKER_6862,
                    NpcID.BANKER_6863,
                    NpcID.BANKER_6864,
                    NpcID.BANKER_6939,
                    NpcID.BANKER_6940,
                    NpcID.BANKER_6941,
                    NpcID.BANKER_6942,
                    NpcID.BANKER_6969,
                    NpcID.BANKER_6970,
                    NpcID.BANKER_7057,
                    NpcID.BANKER_7058,
                    NpcID.BANKER_7059,
                    NpcID.BANKER_7060,
                    NpcID.BANKER_7077,
                    NpcID.BANKER_7078,
                    NpcID.BANKER_7079,
                    NpcID.BANKER_7080,
                    NpcID.BANKER_7081,
                    NpcID.BANKER_7082,
                    NpcID.BANKER_8321,
                    NpcID.BANKER_8322,
                    NpcID.BANKER_8589,
                    NpcID.BANKER_8590,
                    NpcID.BANKER_8666,
                    NpcID.BANKER_9127,
                    NpcID.BANKER_9128,
                    NpcID.BANKER_9129,
                    NpcID.BANKER_9130,
                    NpcID.BANKER_9131,
                    NpcID.BANKER_9132,
                    NpcID.BANKER_9484,
                    NpcID.BANKER_9718,
                    NpcID.BANKER_9719,
                    NpcID.BANKER_10389,
                    NpcID.BANKER_10734,
                    NpcID.BANKER_10735,
                    NpcID.BANKER_10736,
                    NpcID.BANKER_10737);
    public static final Set<Integer> PORTAL_NEXUS_IDS = ImmutableSet.of(
            ObjectID.PORTAL_NEXUS,
            ObjectID.PORTAL_NEXUS_33409,
            ObjectID.PORTAL_NEXUS_33410
    );
}