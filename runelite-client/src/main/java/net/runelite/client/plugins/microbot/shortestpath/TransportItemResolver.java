package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Rs2Tome;
import net.runelite.client.plugins.microbot.util.magic.Runes;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pinned adapter for symbolic item collections used by Shortest Path transport resources.
 *
 * <p>Only collections whose semantics can be represented by {@link TransportItemRequirement} belong
 * here. Rune symbols delegate to Microbot's canonical rune, staff and tome catalogs so pathfinding and
 * actual casting cannot acquire separate provider lists. Unknown or unsupported symbols fail closed.</p>
 */
final class TransportItemResolver {
    private static final Map<String, Resolution> SYMBOLS = buildSymbols();

    private TransportItemResolver() {
    }

    static Resolution resolve(String symbol) {
        if (symbol == null) {
            return null;
        }
        return SYMBOLS.get(symbol.trim().toUpperCase(Locale.ROOT));
    }

    private static Map<String, Resolution> buildSymbols() {
        Map<String, Resolution> symbols = new LinkedHashMap<>();
        addRune(symbols, "AIR_RUNE", Runes.AIR);
        addRune(symbols, "ASTRAL_RUNE", Runes.ASTRAL);
        add(symbols, "AXE",
                ItemID.BRONZE_AXE, ItemID.IRON_AXE, ItemID.STEEL_AXE, ItemID.BLACK_AXE,
                ItemID.MITHRIL_AXE, ItemID.ADAMANT_AXE, ItemID.RUNE_AXE, ItemID.DRAGON_AXE,
                ItemID.CRYSTAL_AXE, ItemID.TRAIL_GILDED_AXE, ItemID.INFERNAL_AXE, ItemID._3A_AXE);
        add(symbols, "BANANA", ItemID.BANANA);
        addRune(symbols, "BLOOD_RUNE", Runes.BLOOD);
        add(symbols, "BROWN_APRON",
                ItemID.BROWN_APRON, ItemID.GOLDEN_APRON, ItemID.SKILLCAPE_CRAFTING,
                ItemID.SKILLCAPE_CRAFTING_TRIMMED, ItemID.SKILLCAPE_CRAFTING_HOOD);
        add(symbols, "CLIMBING_BOOTS", ItemID.DEATH_CLIMBINGBOOTS, ItemID.CLIMBING_BOOTS_G);
        add(symbols, "COINS", ItemID.COINS);
        add(symbols, "CROSSBOW",
                ItemID.CROSSBOW, ItemID.PHOENIX_CROSSBOW, ItemID.DTTD_BONE_CROSSBOW,
                ItemID.HUNTING_CROSSBOW, ItemID.XBOWS_CROSSBOW_BRONZE, ItemID.XBOWS_CROSSBOW_IRON,
                ItemID.XBOWS_CROSSBOW_STEEL, ItemID.XBOWS_CROSSBOW_MITHRIL,
                ItemID.XBOWS_CROSSBOW_ADAMANTITE, ItemID.XBOWS_CROSSBOW_RUNITE,
                ItemID.XBOWS_CROSSBOW_DRAGON, ItemID.DRAGONHUNTER_XBOW,
                ItemID.BARROWS_KARIL_WEAPON, ItemID.BARROWS_KARIL_WEAPON_BROKEN,
                ItemID.BARROWS_KARIL_WEAPON_25, ItemID.BARROWS_KARIL_WEAPON_50,
                ItemID.BARROWS_KARIL_WEAPON_75, ItemID.BARROWS_KARIL_WEAPON_100,
                ItemID.ACB, ItemID.ZARYTE_XBOW);
        add(symbols, "DUSTY_KEY", ItemID.DUSTY_KEY);
        addRune(symbols, "DUST_RUNE", Runes.DUST);
        addRune(symbols, "EARTH_RUNE", Runes.EARTH);
        add(symbols, "ECTO_TOKEN", ItemID.ECTOTOKEN);
        add(symbols, "GLOWING_FUNGUS", ItemID.GLOWING_FUNGUS);
        addRune(symbols, "FIRE_RUNE", Runes.FIRE);
        addRune(symbols, "LAVA_RUNE", Runes.LAVA);
        addRune(symbols, "LAW_RUNE", Runes.LAW);
        add(symbols, "MACHETE",
                ItemID.MACHETTE, ItemID.MACHETTE_OPAL, ItemID.MACHETTE_JADE, ItemID.MACHETTE_REDTOPAZ);
        add(symbols, "MAX_CAPE",
                ItemID.SKILLCAPE_MAX, ItemID.SKILLCAPE_MAX_WORN, ItemID.SKILLCAPE_MAX_FIRECAPE,
                ItemID.SKILLCAPE_MAX_FIRECAPE_DUMMY, ItemID.SKILLCAPE_MAX_FIRECAPE_TROUVER,
                ItemID.SKILLCAPE_MAX_SARADOMIN, ItemID.SKILLCAPE_MAX_ZAMORAK,
                ItemID.SKILLCAPE_MAX_GUTHIX, ItemID.SKILLCAPE_MAX_ANMA, ItemID.SKILLCAPE_MAX_ARDY,
                ItemID.SKILLCAPE_MAX_INFERNALCAPE, ItemID.SKILLCAPE_MAX_INFERNALCAPE_DUMMY,
                ItemID.SKILLCAPE_MAX_INFERNALCAPE_TROUVER, ItemID.SKILLCAPE_MAX_SARADOMIN2,
                ItemID.SKILLCAPE_MAX_SARADOMIN2_TROUVER, ItemID.SKILLCAPE_MAX_ZAMORAK2,
                ItemID.SKILLCAPE_MAX_ZAMORAK2_TROUVER, ItemID.SKILLCAPE_MAX_GUTHIX2,
                ItemID.SKILLCAPE_MAX_GUTHIX2_TROUVER, ItemID.SKILLCAPE_MAX_ASSEMBLER,
                ItemID.SKILLCAPE_MAX_ASSEMBLER_TROUVER, ItemID.SKILLCAPE_MAX_MYTHICAL,
                ItemID.SKILLCAPE_MAX_ASSEMBLER_MASORI, ItemID.SKILLCAPE_MAX_ASSEMBLER_MASORI_TROUVER,
                ItemID.SKILLCAPE_MAX_DIZANAS, ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER);
        add(symbols, "MAX_HOOD",
                ItemID.SKILLCAPE_MAX_HOOD, ItemID.SKILLCAPE_MAX_HOOD_FIRECAPE,
                ItemID.SKILLCAPE_MAX_HOOD_SARADOMIN, ItemID.SKILLCAPE_MAX_HOOD_ZAMORAK,
                ItemID.SKILLCAPE_MAX_HOOD_GUTHIX, ItemID.SKILLCAPE_MAX_HOOD_ANMA,
                ItemID.SKILLCAPE_MAX_HOOD_ARDY, ItemID.SKILLCAPE_MAX_HOOD_INFERNALCAPE,
                ItemID.SKILLCAPE_MAX_HOOD_SARADOMIN2, ItemID.SKILLCAPE_MAX_HOOD_ZAMORAK2,
                ItemID.SKILLCAPE_MAX_HOOD_GUTHIX2, ItemID.SKILLCAPE_MAX_HOOD_ASSEMBLER,
                ItemID.SKILLCAPE_MAX_HOOD_MYTHICAL, ItemID.SKILLCAPE_MAX_HOOD_ASSEMBLER_MASORI,
                ItemID.SKILLCAPE_MAX_HOOD_DIZANAS);
        add(symbols, "MAZE_KEY", ItemID.MELZARKEY);
        addRune(symbols, "MIND_RUNE", Runes.MIND);
        addRune(symbols, "MIST_RUNE", Runes.MIST);
        add(symbols, "MITH_GRAPPLE", ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE);
        addRune(symbols, "MUD_RUNE", Runes.MUD);
        addRune(symbols, "NATURE_RUNE", Runes.NATURE);
        add(symbols, "PICKAXE",
                ItemID.BRONZE_PICKAXE, ItemID.IRON_PICKAXE, ItemID.STEEL_PICKAXE,
                ItemID.BLACK_PICKAXE, ItemID.MITHRIL_PICKAXE, ItemID.ADAMANT_PICKAXE,
                ItemID.RUNE_PICKAXE, ItemID.DRAGON_PICKAXE, ItemID.CRYSTAL_PICKAXE,
                ItemID.TRAIL_GILDED_PICKAXE, ItemID._3A_PICKAXE, ItemID.DRAGON_PICKAXE_PRETTY,
                ItemID.ZALCANO_PICKAXE, ItemID.TRAILBLAZER_PICKAXE_NO_INFERNAL,
                ItemID.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL, ItemID.INFERNAL_PICKAXE);
        add(symbols, "ROPE", ItemID.ROPE);
        add(symbols, "SHANTAY_PASS", ItemID.SHANTAY_PASS);
        add(symbols, "SKAVID_MAP", ItemID.SKAVIDMAP);
        addRune(symbols, "SMOKE_RUNE", Runes.SMOKE);
        addRune(symbols, "SOUL_RUNE", Runes.SOUL);
        addRune(symbols, "STEAM_RUNE", Runes.STEAM);
        addRune(symbols, "WATER_RUNE", Runes.WATER);
        return Collections.unmodifiableMap(symbols);
    }

    private static void add(Map<String, Resolution> symbols, String name, Integer... itemIds) {
        put(symbols, name, new Resolution(ids(itemIds), Collections.emptySet(),
                Collections.emptySet(), false));
    }

    private static void addRune(Map<String, Resolution> symbols, String name, Runes rune) {
        LinkedHashSet<Integer> itemIds = new LinkedHashSet<>();
        itemIds.add(rune.getItemId());
        Arrays.stream(Runes.getComboRunes(rune))
                .map(Runes::getItemId)
                .forEach(itemIds::add);
        put(symbols, name, new Resolution(
                itemIds,
                Rs2Staff.itemIdsProviding(rune),
                Rs2Tome.itemIdsProviding(rune),
                true));
    }

    private static Set<Integer> ids(Integer... itemIds) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(itemIds)));
    }

    private static void put(Map<String, Resolution> symbols, String name, Resolution resolution) {
        if (resolution.itemIds.isEmpty()
                || resolution.itemIds.stream().anyMatch(id -> id == null || id <= 0)
                || resolution.staffIds.stream().anyMatch(id -> id == null || id <= 0)
                || resolution.offhandIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("invalid item collection: " + name);
        }
        if (symbols.put(name, resolution) != null) {
            throw new IllegalArgumentException("duplicate item collection: " + name);
        }
    }

    static final class Resolution {
        private final Set<Integer> itemIds;
        private final Set<Integer> staffIds;
        private final Set<Integer> offhandIds;
        private final boolean rune;

        private Resolution(Set<Integer> itemIds, Set<Integer> staffIds,
                Set<Integer> offhandIds, boolean rune) {
            this.itemIds = Set.copyOf(itemIds);
            this.staffIds = Set.copyOf(staffIds);
            this.offhandIds = Set.copyOf(offhandIds);
            this.rune = rune;
        }

        Set<Integer> getItemIds() { return itemIds; }
        Set<Integer> getStaffIds() { return staffIds; }
        Set<Integer> getOffhandIds() { return offhandIds; }
        boolean isRune() { return rune; }
    }
}
