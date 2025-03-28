package net.runelite.client.plugins.microbot.util.antiban.enums;

import lombok.Getter;
import net.runelite.api.Skill;

/**
 * The Activity enum represents various activities that the player can perform in the game, each associated with a specific
 * category and intensity level.
 *
 * <p>
 * Activities range from general skilling and combat tasks to specific high-intensity boss fights or complex tasks. Each activity
 * is linked to a <code>Category</code> that defines the type of activity (e.g., skilling, combat), and an <code>ActivityIntensity</code>
 * that controls how aggressive or passive the bot's behavior should be during that activity.
 * </p>
 *
 * <h3>Main Features:</h3>
 * <ul>
 *   <li>Wide Range of Activities: Covers general skilling activities such as mining, cooking, and woodcutting,
 *   as well as high-intensity tasks like boss fights and combat encounters.</li>
 *   <li>Category Association: Each activity is linked to a <code>Category</code> that helps the bot identify the
 *   type of activity and how to handle it.</li>
 *   <li>Intensity Levels: Activities have different intensity levels based on their complexity and demands, which are
 *   represented by <code>ActivityIntensity</code>. For example, combat activities tend to have higher intensity, while
 *   skilling tasks may be more moderate or low intensity.</li>
 *   <li>Skill Mapping: The enum provides a method to map game skills to the appropriate general activity,
 *   ensuring that the bot behaves correctly when training a particular skill.</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <p>
 * The <code>Activity</code> enum is used to control bot behavior based on the type of activity the player is performing.
 * Each activity informs the bot of the type of task being executed, its intensity, and the corresponding category to ensure
 * appropriate behavior, such as taking breaks or adjusting actions dynamically.
 * </p>
 *
 * <h3>Example:</h3>
 * <pre>
 * Rs2Antiban.setActivity(Activity activity);
 * Rs2Antiban.getCategory();
 * Rs2Antiban.setActivityIntensity(ActivityIntensity activityIntensity);
 * </pre>
 *
 * <h3>Skill-Based Activity Mapping:</h3>
 * <p>
 * The <code>fromSkill(Skill skill)</code> method maps in-game skills (e.g., Mining, Fishing, Combat) to general activities,
 * allowing the bot to adjust its behavior based on the skill currently being trained. This ensures that the bot
 * behaves consistently for all general activities related to a specific skill.
 * </p>
 *
 * <h3>Activity Categories and Intensities:</h3>
 * <p>
 * Each activity belongs to a <code>Category</code> that defines the overall type of the activity (e.g., skilling, combat,
 * collecting), and each is assigned an <code>ActivityIntensity</code> level, which determines the speed and aggression of
 * the bot's actions during that activity.
 * </p>
 */

public enum Activity {
    GENERAL_MINING("General Mining", Category.SKILLING_MINING, ActivityIntensity.LOW),
    GENERAL_SMITHING("General Smithing", Category.SKILLING_SMITHING, ActivityIntensity.LOW),
    GENERAL_FISHING("General Fishing", Category.SKILLING_FISHING, ActivityIntensity.LOW),
    GENERAL_COOKING("General Cooking", Category.SKILLING_COOKING, ActivityIntensity.LOW),
    GENERAL_FIREMAKING("General Firemaking", Category.SKILLING_FIREMAKING, ActivityIntensity.LOW),
    GENERAL_WOODCUTTING("General Woodcutting", Category.SKILLING_WOODCUTTING, ActivityIntensity.LOW),
    GENERAL_FLETCHING("General Fletching", Category.SKILLING_FLETCHING, ActivityIntensity.LOW),
    GENERAL_CRAFTING("General Crafting", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    GENERAL_AGILITY("General Agility", Category.SKILLING_AGILITY, ActivityIntensity.MODERATE),
    GENERAL_THIEVING("General Thieving", Category.SKILLING_THIEVING, ActivityIntensity.MODERATE),
    GENERAL_SLAYER("General Slayer", Category.COMBAT_HIGH, ActivityIntensity.MODERATE),
    GENERAL_RUNECRAFT("General Runecraft", Category.SKILLING_RUNECRAFT, ActivityIntensity.MODERATE),
    GENERAL_HUNTER("General Hunter", Category.SKILLING_HUNTER, ActivityIntensity.MODERATE),
    GENERAL_COMBAT("General Combat", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    GENERAL_HERBLORE("General Herblore", Category.SKILLING_HERBLORE, ActivityIntensity.LOW),
    GENERAL_FARMING("General Farming", Category.SKILLING_FARMING, ActivityIntensity.MODERATE),
    GENERAL_PRAYER("General Prayer", Category.SKILLING_PRAYER, ActivityIntensity.HIGH),
    GENERAL_CONSTRUCTION("General Construction", Category.SKILLING_CONSTRUCTION, ActivityIntensity.MODERATE),
    GENERAL_COLLECTING("General Collecting", Category.COLLECTING, ActivityIntensity.MODERATE),
    COMPLETING_THE_FORTIS_COLOSSEUM_WAVE_12("Completing the Fortis Colosseum (Wave 12)", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_NEX_DUO("Killing Nex (Duo)", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    TOMBS_OF_AMASCUT_SOLO_500_RAID_LEVEL("Tombs of Amascut (solo 500 raid level)", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_NEX_TEAM("Killing Nex (Team)", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    THEATRE_OF_BLOOD_SCYTHE_OF_VITUR("Theatre of Blood (Scythe of vitur)", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    CHAMBERS_OF_XERIC("Chambers of Xeric", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    THEATRE_OF_BLOOD_ABYSSAL_TENTACLE("Theatre of Blood (Abyssal tentacle)", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_KREEARRA("Killing Kreearra", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_PHOSANIS_NIGHTMARE("Killing Phosanis Nightmare", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_VENENATIS("Killing Venenatis", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_VARDORVIS("Killing Vardorvis", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    TOMBS_OF_AMASCUT_EXPERT("Tombs of Amascut (Expert)", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_COMMANDER_ZILYANA("Killing Commander Zilyana", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_SPINDEL("Killing Spindel", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_VETION("Killing Vetion", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_PHANTOM_MUSPAH_TWISTED_BOW("Killing Phantom Muspah (Twisted bow)", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_THE_ALCHEMICAL_HYDRA("Killing the Alchemical Hydra", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    COMPLETING_THE_CORRUPTED_GAUNTLET("Completing The Corrupted Gauntlet", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_CERBERUS("Killing Cerberus", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_GENERAL_GRAARDOR("Killing General Graardor", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_DEMONIC_GORILLAS("Killing demonic gorillas", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_THE_WHISPERER("Killing The Whisperer", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_DUKE_SUCELLUS("Killing Duke Sucellus", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_THE_NIGHTMARE("Killing The Nightmare", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_CALVARION("Killing Calvarion", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_THE_LEVIATHAN("Killing The Leviathan", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    PICKPOCKETING_VYRES("Pickpocketing vyres", Category.SKILLING_THIEVING, ActivityIntensity.HIGH),
    KILLING_CALLISTO("Killing Callisto", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_REVENANTS_CRAWS_BOW("Killing revenants (Craws bow)", Category.COMBAT_MID, ActivityIntensity.HIGH),
    KILLING_ARTIO("Killing Artio", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_VORKATH_DRAGON_HUNTER_LANCE("Killing Vorkath (Dragon hunter lance)", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_VORKATH_DRAGON_HUNTER_CROSSBOW("Killing Vorkath (Dragon hunter crossbow)", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_ZULRAH_MAX_EFFICIENCY("Killing Zulrah (max efficiency)", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    COMPLETING_THE_FORTIS_COLOSSEUM_WAVE_1("Completing the Fortis Colosseum (Wave 1)", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    WILDERNESS_AGILITY_COURSE("Wilderness Agility Course", Category.SKILLING_AGILITY, ActivityIntensity.HIGH),
    KILLING_KRIL_TSUTSAROTH("Killing Kril Tsutsaroth", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_REVENANTS_MAGIC_SHORTBOW("Killing revenants (Magic shortbow)", Category.COMBAT_MID, ActivityIntensity.HIGH),
    PICKPOCKETING_ELVES("Pickpocketing elves", Category.SKILLING_THIEVING, ActivityIntensity.HIGH),
    KILLING_DAGANNOTH_KINGS_SOLO_TRIBRID("Killing Dagannoth Kings (Solo tribrid)", Category.COMBAT_HIGH, ActivityIntensity.MODERATE),
    KILLING_THE_CORPOREAL_BEAST("Killing the Corporeal Beast", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_ZOMBIE_PIRATES("Killing zombie pirates", Category.COMBAT_LOW, ActivityIntensity.HIGH),
    OPENING_ESSENCE_IMPLING_JARS("Opening essence impling jars", Category.PROCESSING, ActivityIntensity.HIGH),
    CRAFTING_WRATH_RUNES("Crafting wrath runes", Category.SKILLING_RUNECRAFT, ActivityIntensity.HIGH),
    KILLING_THE_GIANT_MOLE_TWISTED_BOW("Killing the Giant Mole (Twisted bow)", Category.COMBAT_MID, ActivityIntensity.LOW),
    STEALING_FROM_ROGUES_CASTLE_CHESTS("Stealing from Rogues Castle chests", Category.SKILLING_THIEVING, ActivityIntensity.HIGH),
    COMPLETING_THE_GAUNTLET("Completing The Gauntlet", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    DELIVERING_FOOD_IN_GNOME_RESTAURANT("Delivering food in Gnome Restaurant", Category.COLLECTING, ActivityIntensity.HIGH),
    OPENING_GRUBBY_CHESTS("Opening grubby chests", Category.PROCESSING, ActivityIntensity.HIGH),
    MOONS_OF_PERIL("Moons of Peril", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    CRAFTING_BLOOD_RUNES("Crafting blood runes", Category.SKILLING_RUNECRAFT, ActivityIntensity.HIGH),
    KILLING_VORKATH_TOXIC_BLOWPIPE("Killing Vorkath (Toxic blowpipe)", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    HALLOWED_SEPULCHRE("Hallowed Sepulchre", Category.SKILLING_AGILITY, ActivityIntensity.HIGH),
    OPENING_SINISTER_CHESTS("Opening sinister chests", Category.PROCESSING, ActivityIntensity.HIGH),
    CRAFTING_ASTRAL_RUNES("Crafting astral runes", Category.SKILLING_RUNECRAFT, ActivityIntensity.HIGH),
    OPENING_ECLECTIC_IMPLING_JARS("Opening eclectic impling jars", Category.PROCESSING, ActivityIntensity.HIGH),
    MAKING_TOY_CATS("Making toy cats", Category.PROCESSING, ActivityIntensity.HIGH),
    CRAFTING_SUNFIRE_RUNES("Crafting sunfire runes", Category.SKILLING_RUNECRAFT, ActivityIntensity.HIGH),
    CRAFTING_BLOODS_TRUE_ALTAR("Crafting bloods at true altar", Category.SKILLING_RUNECRAFT, ActivityIntensity.HIGH),
    SMELTING_RUNITE_BARS_AT_BLAST_FURNACE("Smelting runite bars at Blast Furnace", Category.SKILLING_SMITHING, ActivityIntensity.HIGH),
    FILLING_BULLSEYE_LANTERN_EMPTY("Filling bullseye lantern (empty)", Category.PROCESSING, ActivityIntensity.MODERATE),
    KILLING_THE_THERMONUCLEAR_SMOKE_DEVIL("Killing the Thermonuclear smoke devil", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    BUYING_MONKEY_NUTS("Buying monkey nuts", Category.COLLECTING, ActivityIntensity.LOW),
    KILLING_ZALCANO("Killing Zalcano", Category.SKILLING, ActivityIntensity.HIGH),
    KILLING_RUNE_DRAGONS("Killing rune dragons", Category.COMBAT_HIGH, ActivityIntensity.MODERATE),
    KILLING_LIZARDMAN_SHAMANS_CANYON("Killing Lizardman Shamans (Canyon)", Category.COMBAT_HIGH, ActivityIntensity.MODERATE),
    CRAFTING_DEATH_RUNES_THROUGH_THE_ABYSS("Crafting death runes through the Abyss", Category.SKILLING_RUNECRAFT, ActivityIntensity.MODERATE),
    KILLING_ZULRAH("Killing Zulrah", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    CRAFTING_DOUBLE_LAW_RUNES_THROUGH_THE_ABYSS("Crafting double law runes through the Abyss", Category.SKILLING_RUNECRAFT, ActivityIntensity.MODERATE),
    KILLING_LAVA_DRAGONS("Killing lava dragons", Category.COMBAT_HIGH, ActivityIntensity.MODERATE),
    KILLING_DAGANNOTH_KINGS_REX_ONLY("Killing Dagannoth Kings (Rex only)", Category.COMBAT_MID, ActivityIntensity.LOW),
    OPENING_CRYSTAL_CHESTS("Opening crystal chests", Category.PROCESSING, ActivityIntensity.HIGH),
    MAKING_REDWOOD_PYRE_LOGS("Making redwood pyre logs", Category.PROCESSING, ActivityIntensity.MODERATE),
    MAKING_SACRED_OIL("Making sacred oil", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    MAKING_DIVINE_SUPER_COMBAT_POTIONS("Making divine super combat potions", Category.SKILLING_HERBLORE, ActivityIntensity.LOW),
    KILLING_THE_ABYSSAL_SIRE("Killing the Abyssal Sire", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_THE_GIANT_MOLE_DHAROKS("Killing the Giant Mole (Dharoks)", Category.COMBAT_MID, ActivityIntensity.LOW),
    BUYING_KEGS_OF_BEER("Buying kegs of beer", Category.COLLECTING, ActivityIntensity.MODERATE),
    MAKING_AVANTOE_POTIONS("Making avantoe potions", Category.PROCESSING, ActivityIntensity.LOW),
    PICKPOCKETING_HAM_MEMBERS("Pickpocketing H.A.M. members", Category.SKILLING_THIEVING, ActivityIntensity.HIGH),
    HUNTING_BLACK_CHINCHOMPAS("Hunting black chinchompas", Category.SKILLING_HUNTER, ActivityIntensity.MODERATE),
    MAKING_SNAPDRAGON_POTIONS("Making snapdragon potions", Category.PROCESSING, ActivityIntensity.LOW),
    CASTING_TAN_LEATHER("Casting Tan Leather", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    KILLING_THE_GROTESQUE_GUARDIANS("Killing the Grotesque Guardians", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    HUNTING_IMPLINGS("Hunting implings", Category.SKILLING_HUNTER, ActivityIntensity.HIGH),
    CRAFTING_BLOOD_RUNES_THROUGH_THE_ABYSS("Crafting blood runes through the Abyss", Category.SKILLING_RUNECRAFT, ActivityIntensity.HIGH),
    MAKING_MAGIC_PYRE_LOGS("Making magic pyre logs", Category.PROCESSING, ActivityIntensity.MODERATE),
    KILLING_BRUTAL_BLACK_DRAGONS("Killing brutal black dragons", Category.COMBAT_HIGH, ActivityIntensity.LOW),
    MAKING_RANARR_POTIONS("Making ranarr potions", Category.PROCESSING, ActivityIntensity.LOW),
    KILLING_HYDRAS("Killing hydras", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    ENCHANTING_DRAGONSTONE_JEWELLERY("Enchanting dragonstone jewellery", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    MAKING_SANFEW_SERUM4("Making sanfew serum(4)", Category.SKILLING_HERBLORE, ActivityIntensity.MODERATE),
    MAKING_DWARF_WEED_POTIONS("Making dwarf weed potions", Category.PROCESSING, ActivityIntensity.LOW),
    SMELTING_ADAMANTITE_BARS_AT_BLAST_FURNACE("Smelting adamantite bars at Blast Furnace", Category.SKILLING_SMITHING, ActivityIntensity.HIGH),
    MAKING_KWUARM_POTIONS("Making kwuarm potions", Category.PROCESSING, ActivityIntensity.LOW),
    MAKING_CADANTINE_POTIONS("Making cadantine potions", Category.PROCESSING, ActivityIntensity.LOW),
    KILLING_GREEN_DRAGONS_MYTHS_GUILD("Killing green dragons (Myths Guild)", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    MAKING_RAW_WILD_PIES("Making raw wild pies", Category.PROCESSING, ActivityIntensity.HIGH),
    KILLING_VYREWATCH_SENTINELS("Killing Vyrewatch Sentinels", Category.COMBAT_HIGH, ActivityIntensity.LOW),
    KILLING_THE_KRAKEN("Killing the Kraken", Category.COMBAT_HIGH, ActivityIntensity.LOW),
    KILLING_FIYR_SHADES("Killing fiyr shades", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    MAKING_TOADFLAX_POTIONS("Making toadflax potions", Category.PROCESSING, ActivityIntensity.LOW),
    KILLING_GREEN_DRAGONS("Killing green dragons", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    MAKING_LANTADYME_POTIONS("Making lantadyme potions", Category.PROCESSING, ActivityIntensity.LOW),
    BARROWS("Barrows", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    PICKPOCKETING_MASTER_FARMERS("Pickpocketing master farmers", Category.SKILLING_THIEVING, ActivityIntensity.MODERATE),
    KILLING_LIZARDMAN_SHAMANS_SETTLEMENT_OR_TEMPLE("Killing Lizardman Shamans (Settlement or Temple)", Category.COMBAT_HIGH, ActivityIntensity.MODERATE),
    COLLECTING_MORT_MYRE_FUNGI("Collecting mort myre fungi", Category.COLLECTING, ActivityIntensity.LOW),
    HUNTING_MOONLIGHT_ANTELOPES("Hunting moonlight antelopes", Category.SKILLING_HUNTER, ActivityIntensity.MODERATE),
    COMPLETING_ELITE_CLUES_URIUM_REMAINS("Completing elite clues (urium remains)", Category.SKILLING_FIREMAKING, ActivityIntensity.HIGH),
    MAKING_WEAPON_POISON_PLUS_PLUS("Making weapon poison(++)", Category.COLLECTING, ActivityIntensity.HIGH),
    KILLING_MITHRIL_DRAGONS("Killing mithril dragons", Category.COMBAT_HIGH, ActivityIntensity.MODERATE),
    KILLING_URIUM_SHADES("Killing urium shades", Category.COMBAT_HIGH, ActivityIntensity.MODERATE),
    MAKING_GUTHIX_RESTS("Making Guthix rests", Category.PROCESSING, ActivityIntensity.HIGH),
    SMELTING_STEEL_BARS_AT_BLAST_FURNACE("Smelting steel bars at Blast Furnace", Category.SKILLING_SMITHING, ActivityIntensity.HIGH),
    KILLING_SKELETAL_WYVERNS("Killing skeletal wyverns", Category.COMBAT_HIGH, ActivityIntensity.LOW),
    CRAFTING_DRIFT_NETS("Crafting drift nets", Category.SKILLING_CRAFTING, ActivityIntensity.MODERATE),
    SMITHING_RUNE_ITEMS("Smithing rune items", Category.SKILLING_SMITHING, ActivityIntensity.LOW),
    MINING_RUNITE_ORE("Mining runite ore", Category.SKILLING_MINING, ActivityIntensity.LOW),
    MAKING_IRIT_POTIONS("Making irit potions", Category.PROCESSING, ActivityIntensity.LOW),
    LAST_MAN_STANDING("Last Man Standing", Category.COMBAT_LOW, ActivityIntensity.HIGH),
    CREATING_TELEPORT_TABLETS_AT_LECTERN_LUNAR("Creating teleport tablets at Lectern (Lunar)", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    MAKING_ULTRACOMPOST("Making ultracompost", Category.PROCESSING, ActivityIntensity.LOW),
    KILLING_FEVER_SPIDERS_MAXIMUM_EFFICIENCY("Killing fever spiders (maximum efficiency)", Category.COMBAT_LOW, ActivityIntensity.HIGH),
    FLETCHING_MOONLIGHT_ANTLER_BOLTS("Fletching moonlight antler bolts", Category.PROCESSING, ActivityIntensity.LOW),
    KILLING_DISCIPLES_OF_IBAN("Killing disciples of Iban", Category.COMBAT_LOW, ActivityIntensity.MODERATE),
    KILLING_GARGOYLES("Killing gargoyles", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    FLETCHING_OGRE_ARROW_SHAFTS("Fletching ogre arrow shafts", Category.PROCESSING, ActivityIntensity.LOW),
    KILLING_ENTS("Killing ents", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    GRINDING_DESERT_GOAT_HORNS("Grinding desert goat horns", Category.PROCESSING, ActivityIntensity.MODERATE),
    GRINDING_UNICORN_HORNS("Grinding unicorn horns", Category.PROCESSING, ActivityIntensity.MODERATE),
    CRAFTING_DRAGONSTONE_JEWELLERY("Crafting dragonstone jewellery", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    CRAFTING_BLOOD_RUNES_AT_ARCEUUS_MAX("Crafting blood runes at Arceuus (max)", Category.SKILLING_RUNECRAFT, ActivityIntensity.LOW),
    HUNTING_SUNLIGHT_ANTELOPES("Hunting sunlight antelopes", Category.SKILLING_HUNTER, ActivityIntensity.MODERATE),
    PICKPOCKETING_PALADINS("Pickpocketing paladins", Category.SKILLING_THIEVING, ActivityIntensity.MODERATE),
    STEALING_CAVE_GOBLIN_WIRE("Stealing cave goblin wire", Category.COLLECTING, ActivityIntensity.HIGH),
    CRAFTING_COSMIC_RUNES_THROUGH_THE_ABYSS("Crafting cosmic runes through the Abyss", Category.SKILLING_RUNECRAFT, ActivityIntensity.MODERATE),
    MINING_BASALT("Mining basalt", Category.SKILLING_MINING, ActivityIntensity.LOW),
    TANNING_RED_DRAGONHIDE("Tanning red dragonhide", Category.PROCESSING, ActivityIntensity.LOW),
    CRAFTING_BLOOD_RUNES_AT_ARCEUUS_NO_DIARY("Crafting blood runes at Arceuus (no diary)", Category.SKILLING_RUNECRAFT, ActivityIntensity.LOW),
    MAKING_PINEAPPLE_PIZZAS("Making pineapple pizzas", Category.SKILLING_COOKING, ActivityIntensity.HIGH),
    MAKING_HARRALANDER_POTIONS("Making harralander potions", Category.PROCESSING, ActivityIntensity.LOW),
    KILLING_BLUE_DRAGONS("Killing blue dragons", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    MINING_GEMSTONES("Mining gemstones", Category.SKILLING_MINING, ActivityIntensity.MODERATE),
    CRAFTING_OPAL_BRACELETS("Crafting opal bracelets", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    ENCHANTING_TOPAZ_JEWELLERY("Enchanting topaz jewellery", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    KILLING_SARACHNIS("Killing Sarachnis", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    BUYING_BEER_TANKARDS("Buying beer tankards", Category.PROCESSING, ActivityIntensity.MODERATE),
    CRAFTING_XERICIAN_ROBES("Crafting Xerician robes", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    SMELTING_MITHRIL_BARS_AT_BLAST_FURNACE("Smelting mithril bars at Blast Furnace", Category.SKILLING_SMITHING, ActivityIntensity.HIGH),
    CHARGING_AND_ALCHEMISING_BRACELETS_OF_ETHEREUM("Charging and alchemising bracelets of ethereum", Category.PROCESSING, ActivityIntensity.MODERATE),
    CREATING_TELEPORT_TO_HOUSE_TABLETS("Creating teleport to house tablets", Category.PROCESSING, ActivityIntensity.LOW),
    CREATING_BARROWS_TELEPORT_TABLETS("Creating Barrows teleport tablets", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    KILLING_THE_KALPHITE_QUEEN("Killing the Kalphite Queen", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    CRAFTING_LAW_RUNES_THROUGH_THE_ABYSS("Crafting law runes through the Abyss", Category.SKILLING_RUNECRAFT, ActivityIntensity.MODERATE),
    CRAFTING_COSMIC_RUNES("Crafting cosmic runes", Category.SKILLING_RUNECRAFT, ActivityIntensity.HIGH),
    FILLING_BUCKETS_WITH_WATER("Filling buckets with water", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    ENCHANTING_JADE_AMULETS("Enchanting jade amulets", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    HUNTING_HERBIBOARS("Hunting herbiboars", Category.SKILLING_HUNTER, ActivityIntensity.LOW),
    TEMPLE_TREKKING("Temple Trekking", Category.COMBAT_LOW, ActivityIntensity.LOW),
    FLETCHING_RUBY_BOLTS("Fletching ruby bolts", Category.SKILLING_FLETCHING, ActivityIntensity.LOW),
    PLANTING_MITHRIL_SEEDS("Planting mithril seeds", Category.COLLECTING, ActivityIntensity.LOW),
    MAKING_RAW_SUMMER_PIES("Making raw summer pies", Category.PROCESSING, ActivityIntensity.MODERATE),
    CRUSHING_BIRD_NESTS("Crushing bird nests", Category.PROCESSING, ActivityIntensity.MODERATE),
    FLETCHING_FLIGHTED_OGRE_ARROWS("Fletching flighted ogre arrows", Category.PROCESSING, ActivityIntensity.LOW),
    HIGH_ALCHING_MYSTIC_EARTH_STAVES_AT_THE_FOUNTAIN_OF_RUNE("High alching mystic earth staves at the fountain of rune", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    TANNING_BLACK_DRAGONHIDE("Tanning black dragonhide", Category.PROCESSING, ActivityIntensity.LOW),
    HIGH_ALCHING_MYSTIC_WATER_STAVES_AT_THE_FOUNTAIN_OF_RUNE("High alching mystic water staves at the fountain of rune", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    BLAST_MINING("Blast mining", Category.SKILLING_MINING, ActivityIntensity.MODERATE),
    HIGH_ALCHING_MYSTIC_AIR_STAVES_AT_THE_FOUNTAIN_OF_RUNE("High alching mystic air staves at the fountain of rune", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    CRAFTING_SOUL_RUNES("Crafting soul runes", Category.SKILLING_RUNECRAFT, ActivityIntensity.HIGH),
    MAKING_TARROMIN_POTIONS("Making tarromin potions", Category.PROCESSING, ActivityIntensity.LOW),
    KILLING_FEVER_SPIDERS_ALCHING("Killing fever spiders (alching", Category.COMBAT_LOW, ActivityIntensity.HIGH),
    MAKING_ADAMANT_BRUTAL_ARROWS("Making adamant brutal arrows", Category.PROCESSING, ActivityIntensity.LOW),
    CASTING_SUPERGLASS_MAKE("Casting Superglass Make", Category.SKILLING_MAGIC, ActivityIntensity.HIGH),
    KILLING_FEVER_SPIDERS_NO_CANNON("Killing fever spiders (no cannon)", Category.COMBAT_LOW, ActivityIntensity.MODERATE),
    MAKING_RAW_ADMIRAL_PIES("Making raw admiral pies", Category.PROCESSING, ActivityIntensity.HIGH),
    CRAFTING_TOPAZ_BRACELETS("Crafting topaz bracelets", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    MINING_RUNITE_ORE_FREE_TO_PLAY("Mining runite ore (free-to-play)", Category.SKILLING_MINING, ActivityIntensity.MODERATE),
    CREATING_VARROCK_TELEPORT_TABLETS("Creating Varrock teleport tablets", Category.PROCESSING, ActivityIntensity.LOW),
    CLEANING_GRIMY_TORSTOL("Cleaning grimy torstol", Category.SKILLING_HERBLORE, ActivityIntensity.HIGH),
    CHARGING_AIR_ORBS("Charging air orbs", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    KILLING_BRUTAL_RED_DRAGONS("Killing brutal red dragons", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    HUNTING_CARNIVOROUS_CHINCHOMPAS("Hunting carnivorous chinchompas", Category.SKILLING_HUNTER, ActivityIntensity.MODERATE),
    CRAFTING_NATURE_RUNES_THROUGH_THE_ABYSS("Crafting nature runes through the Abyss", Category.SKILLING_RUNECRAFT, ActivityIntensity.HIGH),
    HIGH_ALCHING_PROFITABLE_FREE_TO_PLAY_ITEMS("High alching profitable free-to-play items", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    HUMIDIFYING_CLAY("Humidifying clay", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    SEARCHING_A_HERBLORE_CAPE("Searching a herblore cape", Category.COLLECTING, ActivityIntensity.HIGH),
    KILLING_SKOGRES_AND_ZOGRES("Killing skogres and zogres", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    CRAFTING_BLOOD_RUNES_AT_ARCEUUS_MINIMAL_REQUIREMENTS("Crafting blood runes at Arceuus (minimal requirements)", Category.SKILLING_RUNECRAFT, ActivityIntensity.LOW),
    CATCHING_DARK_CRABS("Catching dark crabs", Category.SKILLING_FISHING, ActivityIntensity.LOW),
    COOKING_RAW_SUNLIGHT_ANTELOPE("Cooking raw sunlight antelope", Category.SKILLING_COOKING, ActivityIntensity.LOW),
    KILLING_ICE_TROLL_RUNTS("Killing ice troll runts", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    SMITHING_CANNONBALLS("Smithing cannonballs", Category.SKILLING_SMITHING, ActivityIntensity.LOW),
    MINING_SALTS("Mining salts", Category.SKILLING_MINING, ActivityIntensity.LOW),
    CUTTING_DIAMOND_BOLT_TIPS("Cutting diamond bolt tips", Category.PROCESSING, ActivityIntensity.LOW),
    MAKING_MARRENTILL_POTIONS("Making marrentill potions", Category.PROCESSING, ActivityIntensity.LOW),
    HIGH_ALCHING_COMBAT_BRACELETS_AT_THE_FOUNTAIN_OF_RUNE("High alching combat bracelets at the fountain of rune", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    CRAFTING_LIMESTONE_BRICKS("Crafting limestone bricks", Category.PROCESSING, ActivityIntensity.HIGH),
    TANNING_BLUE_DRAGONHIDE("Tanning blue dragonhide", Category.PROCESSING, ActivityIntensity.LOW),
    MAKING_MAHOGANY_PLANKS("Making mahogany planks", Category.PROCESSING, ActivityIntensity.HIGH),
    CRAFTING_RUBY_BRACELETS("Crafting ruby bracelets", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    MAKING_GUAM_POTIONS("Making guam potions", Category.PROCESSING, ActivityIntensity.LOW),
    CRAFTING_DIAMOND_BRACELETS("Crafting diamond bracelets", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    CLEANING_GRIMY_KWUARM("Cleaning grimy kwuarm", Category.SKILLING_HERBLORE, ActivityIntensity.HIGH),
    CREATING_CAMELOT_TELEPORT_TABLETS("Creating Camelot teleport tablets", Category.PROCESSING, ActivityIntensity.LOW),
    EXCHANGING_IMPLING_JARS("Exchanging impling jars", Category.PROCESSING, ActivityIntensity.MODERATE),
    HIGH_ALCHING_RUNITE_LIMBS_AT_THE_FOUNTAIN_OF_RUNE("High alching runite limbs at the fountain of rune", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    COLLECTING_CLIMBING_BOOTS("Collecting climbing boots", Category.COLLECTING, ActivityIntensity.HIGH),
    BLESSING_UNBLESSED_SYMBOLS("Blessing unblessed symbols", Category.PROCESSING, ActivityIntensity.LOW),
    CHARGING_FIRE_ORBS("Charging fire orbs", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    MINING_AMETHYST("Mining amethyst", Category.SKILLING_MINING, ActivityIntensity.LOW),
    MINING_IRON_ORE("Mining iron ore", Category.SKILLING_MINING, ActivityIntensity.MODERATE),
    MAKING_OAK_PLANKS("Making oak planks", Category.PROCESSING, ActivityIntensity.MODERATE),
    TANNING_GREEN_DRAGONHIDE("Tanning green dragonhide", Category.PROCESSING, ActivityIntensity.LOW),
    MOTHERLODE_MINE("Motherlode Mine", Category.SKILLING_MINING, ActivityIntensity.LOW),
    MAKING_MAHOGANY_PLANKS_AT_THE_WOODCUTTING_GUILD("Making mahogany planks at the Woodcutting Guild", Category.PROCESSING, ActivityIntensity.MODERATE),
    COLLECTING_SNAPE_GRASS("Collecting snape grass", Category.COLLECTING, ActivityIntensity.LOW),
    CUTTING_AMETHYST_ARROWTIPS("Cutting amethyst arrowtips", Category.PROCESSING, ActivityIntensity.LOW),
    KILLING_THE_KING_BLACK_DRAGON("Killing the King Black Dragon", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    RECHARGING_RINGS_OF_WEALTH("Recharging rings of wealth", Category.PROCESSING, ActivityIntensity.MODERATE),
    COLLECTING_MONKS_ROBES("Collecting monks robes", Category.COLLECTING, ActivityIntensity.LOW),
    LOOTING_OGRE_COFFINS("Looting ogre coffins", Category.COLLECTING, ActivityIntensity.MODERATE),
    CASTING_SPIN_FLAX("Casting Spin Flax", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    KILLING_JUBSTERS("Killing Jubsters", Category.COMBAT_LOW, ActivityIntensity.LOW),
    FLETCHING_HEADLESS_ARROWS("Fletching headless arrows", Category.PROCESSING, ActivityIntensity.LOW),
    CASTING_PLANK_MAKE("Casting plank make", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    MAKING_DYNAMITE("Making dynamite", Category.SKILLING_MINING, ActivityIntensity.MODERATE),
    CRAFTING_SAPPHIRE_BRACELETS("Crafting sapphire bracelets", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    BUYING_IRON_ORE("Buying iron ore", Category.COLLECTING, ActivityIntensity.MODERATE),
    SMITHING_BRONZE_DART_TIPS("Smithing bronze dart tips", Category.SKILLING_SMITHING, ActivityIntensity.LOW),
    CLEANING_GRIMY_CADANTINE("Cleaning grimy cadantine", Category.SKILLING_HERBLORE, ActivityIntensity.HIGH),
    CRAFTING_JADE_BRACELETS("Crafting jade bracelets", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    DEGRIMING_GRIMY_GUAM_LEAF("Degriming grimy guam leaf", Category.SKILLING_HERBLORE, ActivityIntensity.HIGH),
    CHARGING_WATER_ORBS("Charging water orbs", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    CATCHING_RAW_KARAMBWAN("Catching raw karambwan", Category.SKILLING_FISHING, ActivityIntensity.LOW),
    BUYING_TEAM_CAPES("Buying team capes", Category.COLLECTING, ActivityIntensity.LOW),
    MAKING_DYES("Making dyes", Category.PROCESSING, ActivityIntensity.HIGH),
    CHARGING_EARTH_ORBS("Charging earth orbs", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    CRAFTING_RUBY_JEWELLERY("Crafting ruby jewellery", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    CRAFTING_CLOCKWORK_TELEPORT_METHOD("Crafting clockwork (teleport method)", Category.SKILLING_CRAFTING, ActivityIntensity.HIGH),
    MAKING_PIE_SHELLS("Making pie shells", Category.PROCESSING, ActivityIntensity.LOW),
    KILLING_SPIDINES("Killing spidines", Category.COMBAT_LOW, ActivityIntensity.LOW),
    CATCHING_ANGLERFISH("Catching anglerfish", Category.SKILLING_FISHING, ActivityIntensity.MODERATE),
    HUNTING_CHINCHOMPAS("Hunting chinchompas", Category.SKILLING_HUNTER, ActivityIntensity.MODERATE),
    FLETCHING_DIAMOND_BOLTS("Fletching diamond bolts", Category.SKILLING_FLETCHING, ActivityIntensity.LOW),
    CRAFTING_MUD_RUNES("Crafting mud runes", Category.SKILLING_RUNECRAFT, ActivityIntensity.MODERATE),
    CRAFTING_EMERALD_BRACELETS("Crafting emerald bracelets", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    COLLECTING_STEEL_PLATEBODIES_HIGH_ALCHEMY("Collecting steel platebodies (High Alchemy)", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    CRAFTING_DIAMOND_JEWELLERY("Crafting diamond jewellery", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    MAKING_TEAK_PLANKS("Making teak planks", Category.PROCESSING, ActivityIntensity.MODERATE),
    COOKING_RAW_SHARKS("Cooking raw sharks", Category.SKILLING_COOKING, ActivityIntensity.LOW),
    CUTTING_RUBY_BOLT_TIPS("Cutting ruby bolt tips", Category.PROCESSING, ActivityIntensity.LOW),
    CRAFTING_RUNES_AT_OURANIA_ALTAR("Crafting runes at Ourania Altar", Category.SKILLING_RUNECRAFT, ActivityIntensity.MODERATE),
    COLLECTING_BLACK_SCIMITARS_FROM_ARDOUGNE_CASTLE("Collecting black scimitars from Ardougne Castle", Category.COLLECTING, ActivityIntensity.LOW),
    MAKING_UNCOOKED_BERRY_PIES("Making uncooked berry pies", Category.PROCESSING, ActivityIntensity.LOW),
    PICKING_BANANAS("Picking bananas", Category.COLLECTING, ActivityIntensity.MODERATE),
    ENCHANTING_DIAMOND_NECKLACES("Enchanting diamond necklaces", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    KILLING_THE_CRAZY_ARCHAEOLOGIST("Killing the Crazy archaeologist", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    KILLING_FERAL_VAMPYRES("Killing Feral Vampyres", Category.COMBAT_MID, ActivityIntensity.LOW),
    COLLECTING_ANTI_DRAGON_SHIELDS_FREE_TO_PLAY("Collecting anti-dragon shields (free-to-play)", Category.COLLECTING, ActivityIntensity.MODERATE),
    STRINGING_MAPLE_LONGBOWS("Stringing maple longbows", Category.SKILLING_FLETCHING, ActivityIntensity.MODERATE),
    SMITHING_IRON_DART_TIPS("Smithing iron dart tips", Category.SKILLING_SMITHING, ActivityIntensity.LOW),
    HIGH_ALCHING_UNFINISHED_RUNITE_CROSSBOWS_AT_THE_FOUNTAIN_OF_RUNE("High alching unfinished runite crossbows at the fountain of rune", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    KILLING_RED_DRAGONS("Killing red dragons", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    COLLECTING_JANGERBERRIES("Collecting jangerberries", Category.COLLECTING, ActivityIntensity.LOW),
    GRINDING_CHOCOLATE_BARS("Grinding chocolate bars", Category.PROCESSING, ActivityIntensity.MODERATE),
    CRAFTING_EMERALD_JEWELLERY("Crafting emerald jewellery", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    MAKING_GLOVES_OF_SILENCE("Making gloves of silence", Category.PROCESSING_MAGIC, ActivityIntensity.MODERATE),
    CATCHING_MINNOWS("Catching minnows", Category.SKILLING_FISHING, ActivityIntensity.MODERATE),
    KILLING_BLACK_DRAGONS("Killing black dragons", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    STRINGING_MAGIC_LONGBOWS("Stringing magic longbows", Category.SKILLING_FLETCHING, ActivityIntensity.MODERATE),
    HIGH_ALCHING_ATLATL_DARTS_AT_THE_FOUNTAIN_OF_RUNE("High alching atlatl darts at the fountain of rune", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    MAKING_UNCOOKED_APPLE_PIES("Making uncooked apple pies", Category.PROCESSING, ActivityIntensity.LOW),
    KILLING_AVIANSIES("Killing aviansies", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    KILLING_CAVE_HORRORS("Killing cave horrors", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    COOKING_PLAIN_PIZZAS("Cooking plain pizzas", Category.SKILLING_COOKING, ActivityIntensity.LOW),
    CLEANING_GRIMY_SNAPDRAGON("Cleaning grimy snapdragon", Category.SKILLING_HERBLORE, ActivityIntensity.HIGH),
    HIGH_ALCHING_RUNE_ARROWS_AT_THE_FOUNTAIN_OF_RUNE("High alching rune arrows at the fountain of rune", Category.SKILLING_MAGIC, ActivityIntensity.LOW),
    KILLING_SPIRITUAL_MAGES("Killing spiritual mages", Category.COMBAT_HIGH, ActivityIntensity.MODERATE),
    CLEANING_GRIMY_TOADFLAX("Cleaning grimy toadflax", Category.SKILLING_HERBLORE, ActivityIntensity.HIGH),
    COLLECTING_PLANKS("Collecting planks", Category.COLLECTING, ActivityIntensity.LOW),
    COLLECTING_RED_SPIDERS_EGGS("Collecting red spiders eggs", Category.COLLECTING, ActivityIntensity.LOW),
    ENCHANTING_SAPPHIRE_RINGS("Enchanting sapphire rings", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    CATCHING_SACRED_EELS("Catching sacred eels", Category.SKILLING_FISHING, ActivityIntensity.LOW),
    CLIMBING_THE_AGILITY_PYRAMID("Climbing the Agility Pyramid", Category.SKILLING_AGILITY, ActivityIntensity.HIGH),
    CRAFTING_STEAM_RUNES("Crafting steam runes", Category.SKILLING_RUNECRAFT, ActivityIntensity.MODERATE),
    MAKING_ANCHOVY_PIZZAS("Making anchovy pizzas", Category.SKILLING_COOKING, ActivityIntensity.MODERATE),
    CRAFTING_GOLD_BRACELETS("Crafting gold bracelets", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    KILLING_BRUTAL_GREEN_DRAGONS("Killing brutal green dragons", Category.COMBAT_HIGH, ActivityIntensity.HIGH),
    STRINGING_YEW_LONGBOWS("Stringing yew longbows", Category.SKILLING_FLETCHING, ActivityIntensity.MODERATE),
    KILLING_BRYOPHYTA_FREE_TO_PLAY("Killing Bryophyta (free-to-play)", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    COLLECTING_WHITE_BERRIES("Collecting white berries", Category.COLLECTING, ActivityIntensity.LOW),
    COLLECTING_ANTI_DRAGON_SHIELDS("Collecting anti-dragon shields", Category.COLLECTING, ActivityIntensity.HIGH),
    CUTTING_MAHOGANY_LOGS("Cutting mahogany logs", Category.SKILLING_WOODCUTTING, ActivityIntensity.LOW),
    KILLING_CHAOS_DRUIDS("Killing chaos druids", Category.COMBAT_LOW, ActivityIntensity.LOW),
    COLLECTING_FISH_FOOD("Collecting fish food", Category.COLLECTING, ActivityIntensity.MODERATE),
    CATCHING_INFERNAL_EELS("Catching infernal eels", Category.SKILLING_FISHING, ActivityIntensity.LOW),
    MINING_VOLCANIC_ASH("Mining volcanic ash", Category.SKILLING_MINING, ActivityIntensity.LOW),
    CUTTING_YEW_LOGS_FREE_TO_PLAY("Cutting yew logs (free-to-play)", Category.SKILLING_WOODCUTTING, ActivityIntensity.LOW),
    FORGING_GIANT_SWORDS("Forging Giant swords", Category.SKILLING_SMITHING, ActivityIntensity.MODERATE),
    COOKING_RAW_MONKFISH("Cooking raw monkfish", Category.SKILLING_COOKING, ActivityIntensity.LOW),
    MINING_ADAMANTITE_ORE("Mining adamantite ore", Category.SKILLING_MINING, ActivityIntensity.LOW),
    SPINNING_FLAX_TO_BOW_STRINGS("Spinning flax to bow strings", Category.PROCESSING, ActivityIntensity.LOW),
    BUYING_PIES("Buying pies", Category.COLLECTING, ActivityIntensity.MODERATE),
    TANNING_COWHIDE("Tanning cowhide", Category.PROCESSING, ActivityIntensity.LOW),
    KILLING_BRINE_RATS("Killing brine rats", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    CRAFTING_SAPPHIRE_JEWELLERY("Crafting sapphire jewellery", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    COLLECTING_BRONZE_PICKAXES("Collecting bronze pickaxes", Category.COLLECTING, ActivityIntensity.HIGH),
    KILLING_OGRESS_SHAMANS("Killing ogress shamans", Category.COMBAT_HIGH, ActivityIntensity.MODERATE),
    SMITHING_MITHRIL_DART_TIPS("Smithing mithril dart tips", Category.SKILLING_SMITHING, ActivityIntensity.LOW),
    COLLECTING_WINE_OF_ZAMORAK("Collecting wine of zamorak", Category.COLLECTING, ActivityIntensity.MODERATE),
    MAKING_UNCOOKED_MEAT_PIES("Making uncooked meat pies", Category.PROCESSING, ActivityIntensity.LOW),
    CLEANING_GRIMY_IRIT_LEAVES("Cleaning grimy irit leaves", Category.SKILLING_HERBLORE, ActivityIntensity.HIGH),
    CLEANING_GRIMY_HARRALANDER("Cleaning grimy harralander", Category.SKILLING_HERBLORE, ActivityIntensity.HIGH),
    CREATING_BONES_TO_PEACHES_TABLETS("Creating bones to peaches tablets", Category.PROCESSING, ActivityIntensity.LOW),
    COLLECTING_CHAOS_RUNES("Collecting chaos runes", Category.COLLECTING, ActivityIntensity.MODERATE),
    CUTTING_MAGIC_LOGS("Cutting magic logs", Category.SKILLING_WOODCUTTING, ActivityIntensity.LOW),
    FILLING_WATER_CONTAINERS_HOSIDIUS_KITCHEN("Filling water containers (Hosidius kitchen)", Category.PROCESSING, ActivityIntensity.LOW),
    COLLECTING_NATURE_RUNES("Collecting nature runes", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    COLLECTING_RED_SPIDERS_EGGS_FREE_TO_PLAY("Collecting red spiders eggs (free-to-play)", Category.COLLECTING, ActivityIntensity.MODERATE),
    MAKING_TUNA_POTATOES("Making tuna potatoes", Category.PROCESSING, ActivityIntensity.MODERATE),
    CLEANING_GRIMY_TARROMIN("Cleaning grimy tarromin", Category.SKILLING_HERBLORE, ActivityIntensity.HIGH),
    COLLECTING_TINDERBOXES("Collecting tinderboxes", Category.COLLECTING, ActivityIntensity.HIGH),
    MAKING_PASTRY_DOUGH("Making pastry dough", Category.PROCESSING, ActivityIntensity.MODERATE),
    MINING_ADAMANTITE_ORE_FREE_TO_PLAY("Mining adamantite ore (free-to-play)", Category.SKILLING_MINING, ActivityIntensity.LOW),
    CLEANING_GRIMY_GUAM_LEAVES("Cleaning grimy guam leaves", Category.SKILLING_HERBLORE, ActivityIntensity.HIGH),
    RUNNING_LAPS_OF_THE_CANIFIS_ROOFTOP_COURSE("Running laps of the Canifis rooftop course", Category.SKILLING_AGILITY, ActivityIntensity.LOW),
    BAKING_POTATOES("Baking potatoes", Category.SKILLING_COOKING, ActivityIntensity.LOW),
    MAKING_PIZZA_BASES("Making pizza bases", Category.PROCESSING, ActivityIntensity.HIGH),
    COOKING_RAW_KARAMBWAN("Cooking raw karambwan", Category.SKILLING_COOKING, ActivityIntensity.LOW),
    CREATING_ARDOUGNE_TELEPORT_TABLETS("Creating Ardougne teleport tablets", Category.PROCESSING, ActivityIntensity.LOW),
    COLLECTING_COSMIC_RUNES("Collecting cosmic runes", Category.COLLECTING, ActivityIntensity.MODERATE),
    BUYING_BRONZE_BARS("Buying bronze bars", Category.COLLECTING, ActivityIntensity.LOW),
    FLETCHING_UNSTRUNG_MAPLE_LONGBOWS("Fletching unstrung maple longbows", Category.SKILLING_FLETCHING, ActivityIntensity.LOW),
    COOKING_RAW_SWORDFISH("Cooking raw swordfish", Category.SKILLING_COOKING, ActivityIntensity.LOW),
    SMELTING_RUNITE_BARS("Smelting runite bars", Category.SKILLING_SMITHING, ActivityIntensity.LOW),
    COLLECTING_IRON_ORE("Collecting iron ore", Category.COLLECTING, ActivityIntensity.LOW),
    CUTTING_REDWOOD_LOGS("Cutting redwood logs", Category.SKILLING_WOODCUTTING, ActivityIntensity.LOW),
    COOKING_RAW_ANGLERFISH("Cooking raw anglerfish", Category.SKILLING_COOKING, ActivityIntensity.LOW),
    MINING_IRON_ORE_FREE_TO_PLAY("Mining iron ore (free-to-play)", Category.SKILLING_MINING, ActivityIntensity.HIGH),
    MINING_CLAY_MEMBERS("Mining clay members", Category.SKILLING_MINING, ActivityIntensity.MODERATE),
    KILLING_IMPS("Killing imps", Category.COMBAT_LOW, ActivityIntensity.LOW),
    COLLECTING_BIG_BONES_FROM_THE_BONE_YARD("Collecting big bones from the Bone Yard", Category.COLLECTING, ActivityIntensity.MODERATE),
    KILLING_MONKEYS("Killing monkeys", Category.COMBAT_LOW, ActivityIntensity.LOW),
    CATCHING_SHRIMP_AND_ANCHOVIES("Catching shrimp & anchovies", Category.SKILLING_FISHING, ActivityIntensity.LOW),
    COLLECTING_ASHES("Collecting ashes", Category.COLLECTING, ActivityIntensity.LOW),
    KILLING_OGRESS_WARRIORS("Killing ogress warriors", Category.COMBAT_MID, ActivityIntensity.LOW),
    COLLECTING_AND_TANNING_COWHIDE("Collecting and tanning cowhide", Category.COLLECTING, ActivityIntensity.LOW),
    BUYING_ALE_FROM_THE_RISING_SUN_INN("Buying ale from the Rising Sun Inn", Category.COLLECTING, ActivityIntensity.HIGH),
    KILLING_HILL_GIANTS("Killing hill giants", Category.COMBAT_LOW, ActivityIntensity.MODERATE),
    COLLECTING_STEEL_PLATEBODIES("Collecting steel platebodies", Category.COLLECTING, ActivityIntensity.MODERATE),
    COOKING_RAW_LOBSTER("Cooking raw lobster", Category.SKILLING_COOKING, ActivityIntensity.LOW),
    KILLING_COWS_AND_TANNING_COWHIDE("Killing cows and tanning cowhide", Category.COMBAT_LOW, ActivityIntensity.LOW),
    BUYING_JUG_PACKS("Buying jug packs", Category.COLLECTING, ActivityIntensity.LOW),
    KILLING_CHAOS_DWARVES("Killing chaos dwarves", Category.COMBAT_MID, ActivityIntensity.LOW),
    FILLING_WATER_CONTAINERS("Filling water containers", Category.PROCESSING, ActivityIntensity.LOW),
    COLLECTING_WINE_OF_ZAMORAK_FREE_TO_PLAY("Collecting wine of zamorak (free-to-play)", Category.COLLECTING, ActivityIntensity.LOW),
    MAKING_DOUGH_AT_COOKS_GUILD("Making dough at Cooks Guild", Category.PROCESSING, ActivityIntensity.HIGH),
    SMELTING_BRONZE_BARS("Smelting bronze bars", Category.SKILLING_SMITHING, ActivityIntensity.LOW),
    MINING_CLAY("Mining clay", Category.SKILLING_MINING, ActivityIntensity.LOW),
    CRAFTING_GOLD_JEWELLERY("Crafting gold jewellery", Category.SKILLING_CRAFTING, ActivityIntensity.LOW),
    BUYING_KEBABS_IN_AL_KHARID("Buying kebabs in Al Kharid", Category.COLLECTING, ActivityIntensity.HIGH),
    STEALING_WYDINS_BANANAS("Stealing Wydins bananas", Category.COLLECTING, ActivityIntensity.HIGH),
    COLLECTING_GARLIC("Collecting garlic", Category.COLLECTING, ActivityIntensity.MODERATE),
    COOKING_RAW_TUNA("Cooking raw tuna", Category.SKILLING_COOKING, ActivityIntensity.LOW),
    COLLECTING_SPADES("Collecting spades", Category.COLLECTING, ActivityIntensity.MODERATE),
    CUTTING_WILLOW_LOGS("Cutting willow logs", Category.SKILLING_WOODCUTTING, ActivityIntensity.LOW),
    CUTTING_OAK_LOGS("Cutting oak logs", Category.SKILLING_WOODCUTTING, ActivityIntensity.LOW),
    CATCHING_TROUT_AND_SALMON("Catching trout & salmon", Category.SKILLING_FISHING, ActivityIntensity.LOW),
    KILLING_ZAMORAK_MONKS("Killing Zamorak monks", Category.COMBAT_LOW, ActivityIntensity.MODERATE),
    MINING_COAL_AND_SUPERHEATING_ADAMANTITE_BARS("Mining coal and superheating Adamantite bars", Category.SKILLING_MINING, ActivityIntensity.MODERATE),
    SMELTING_SILVER_BARS("Smelting silver bars", Category.SKILLING_SMITHING, ActivityIntensity.LOW),
    KILLING_CHICKENS("Killing chickens", Category.COMBAT_LOW, ActivityIntensity.LOW),
    SHEARING_SHEEP("Shearing sheep", Category.COLLECTING, ActivityIntensity.LOW),
    PICKING_POTATOES("Picking potatoes", Category.COLLECTING, ActivityIntensity.LOW),
    KILLING_HOBGOBLINS("Killing hobgoblins", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    MINING_GOLD_ORE_CRAFTING_GUILD("Mining gold ore (Crafting Guild)", Category.SKILLING_MINING, ActivityIntensity.LOW),
    KILLING_WIZARDS("Killing wizards", Category.COMBAT_LOW, ActivityIntensity.LOW),
    KILLING_BEARS("Killing bears", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    MINING_COAL_AND_SUPERHEATING_MITHRIL_BARS("Mining coal and superheating Mithril bars", Category.SKILLING_MINING, ActivityIntensity.MODERATE),
    MINING_COAL_FREE_TO_PLAY("Mining coal (free-to-play)", Category.SKILLING_MINING, ActivityIntensity.MODERATE),
    SMELTING_IRON_BARS("Smelting iron bars", Category.SKILLING_SMITHING, ActivityIntensity.LOW),
    SMELTING_STEEL_BARS("Smelting steel bars", Category.SKILLING_SMITHING, ActivityIntensity.LOW),
    KILLING_ANKOU_FREE_TO_PLAY("Killing Ankou (free-to-play)", Category.COMBAT_MID, ActivityIntensity.MODERATE),
    BUYING_FROM_WYDINS_FOOD_STORE("Buying from Wydins Food Store", Category.COLLECTING, ActivityIntensity.HIGH),
    COLLECTING_BEER_GLASSES("Collecting beer glasses", Category.COLLECTING, ActivityIntensity.LOW),
    MAKING_FLOUR("Making flour", Category.PROCESSING, ActivityIntensity.LOW),
    COLLECTING_AIR_TALISMANS("Collecting air talismans", Category.COLLECTING, ActivityIntensity.MODERATE),
    KILLING_DARK_WIZARDS("Killing dark wizards", Category.COMBAT_LOW, ActivityIntensity.LOW),
    MINING_GOLD_ORE_FREE_TO_PLAY("Mining gold ore (free-to-play)", Category.SKILLING_MINING, ActivityIntensity.LOW),
    GETTING_INFINITE_MONEY("Getting infinite money", Category.COLLECTING_NONE, ActivityIntensity.HIGH),
    ALCHING("Low/High Alching", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    SUPERHEATING("Superheating items", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    TELEPORT_TRAINING("Teleport training", Category.SKILLING_MAGIC, ActivityIntensity.MODERATE),
    SPLASHING("Splashing spells on monsters", Category.SKILLING_MAGIC, ActivityIntensity.LOW);
    
    @Getter
    private final String method;
    @Getter
    private final Category category;
    private final ActivityIntensity intensity;

    Activity(String method, Category category, ActivityIntensity intensity) {
        this.method = method;
        this.category = category;
        this.intensity = intensity;
    }

    public static Activity fromSkill(Skill skill) {
        switch (skill) {
            case ATTACK:
            case DEFENCE:
            case STRENGTH:
            case RANGED:
            case MAGIC:
                return GENERAL_COMBAT;
            case PRAYER:
                return GENERAL_PRAYER;
            case CONSTRUCTION:
                return GENERAL_CONSTRUCTION;
            case COOKING:
                return GENERAL_COOKING;
            case WOODCUTTING:
                return GENERAL_WOODCUTTING;
            case FLETCHING:
                return GENERAL_FLETCHING;
            case FISHING:
                return GENERAL_FISHING;
            case FIREMAKING:
                return GENERAL_FIREMAKING;
            case CRAFTING:
                return GENERAL_CRAFTING;
            case SMITHING:
                return GENERAL_SMITHING;
            case MINING:
                return GENERAL_MINING;
            case HERBLORE:
                return GENERAL_HERBLORE;
            case AGILITY:
                return GENERAL_AGILITY;
            case THIEVING:
                return GENERAL_THIEVING;
            case SLAYER:
                return GENERAL_SLAYER;
            case RUNECRAFT:
                return GENERAL_RUNECRAFT;
            case HUNTER:
                return GENERAL_HUNTER;
            case FARMING:
                return GENERAL_FARMING;
            default:
                return null;
        }
    }

    public ActivityIntensity getActivityIntensity() {
        return intensity;
    }
}
