package net.runelite.client.plugins.microbot.banksorter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.ScheduledFuture; // Added for bankMonitor if implemented
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class BankTabSorterScript extends Script {
    // Regex to extract base name and numeric suffix, e.g., "Super attack (4)" -> "Super attack", 4
    private static final Pattern ITEM_NAME_SUFFIX_PATTERN = Pattern.compile("^(.*?)(?:\\s*\\((\\d+)\\))?$");
    // Regex for general numeric parts in names, not just suffixes
    private static final Pattern NUMERIC_PART_PATTERN = Pattern.compile("\\d+");
    // --- Item Data & Definitions ---
    private static final Map<String, Integer> TOOL_LEVELS_PICKAXE = new LinkedHashMap<>();
    private static final Map<String, Integer> TOOL_LEVELS_AXE = new LinkedHashMap<>();
    private static final Map<String, Integer> GEAR_TIERS = new LinkedHashMap<>();
    private static final List<String> GRACEFUL_SET_ORDER = Arrays.asList("hood", "cape", "top", "legs", "gloves", "boots");
    private static final List<String> VOID_KNIGHT_SET_ORDER = Arrays.asList("helm", "top", "robe", "gloves", "deflector");
    // Special item sets grouping with ordering
    private static final Map<String, List<String>> ITEM_SETS = new LinkedHashMap<>() {{
        put("graceful", Arrays.asList("graceful hood", "graceful cape", "graceful top", "graceful legs", "graceful gloves", "graceful boots"));
        put("void knight", Arrays.asList("void knight helm", "void knight top", "void knight robe", "void knight gloves", "void knight deflector")); // Note: original had "void knight helm" twice, assuming one was meant to be unique
        put("barrows-dharok", Arrays.asList("dharok's helm", "dharok's platebody", "dharok's platelegs", "dharok's greataxe"));
        put("barrows-guthan", Arrays.asList("guthan's helm", "guthan's platebody", "guthan's chainskirt", "guthan's warspear"));
        put("barrows-torag", Arrays.asList("torag's helm", "torag's platebody", "torag's platelegs", "torag's hammers"));
        // ... add other barrows sets as needed
    }};
    private static final List<String> RUNE_TYPES_ORDER = Arrays.asList(
            "air rune", "mind rune", "water rune", "earth rune", "fire rune", "body rune",
            "cosmic rune", "chaos rune", "astral rune", "nature rune", "law rune", "death rune",
            "blood rune", "soul rune", "wrath rune"
    );
    private static final Map<String, Integer> GEM_LEVELS = new HashMap<>();
    private static final Map<String, Integer> LOG_LEVELS = new HashMap<>();
    private static final Map<String, Integer> ORE_LEVELS = new HashMap<>();
    private static final Map<String, Integer> BAR_LEVELS = new HashMap<>();
    private static final Map<String, Integer> HERB_LEVELS = new HashMap<>();
    private static final Map<String, Integer> SEED_LEVELS_FARMING = new HashMap<>();
    // Re-ordered to define the "left-to-right" workflow stages for items of the same level
    private static final List<String> RESOURCE_BUCKET_ORDER = Arrays.asList(
            "gem-uncut", "gem-cut", "log", "ore", "bar", "herb-grimy", "herb-cleaned", "seed", "bone", "hide", "fish-raw", "fish-cooked", "other-resource"
    );
    private static final List<String> CATEGORY_ORDER = Arrays.asList(
            "Currency", "Teleportation", "Potions", "Food", "Runes", "Ammunition",
            "Weapon-Melee", "Weapon-Ranged", "Weapon-Magic",
            "Armour-Helmet", "Armour-Cape", "Armour-Amulet", "Armour-Body", "Armour-Legs",
            "Armour-Shield", "Armour-Gloves", "Armour-Boots", "Armour-Ring", "Armour-Set-Graceful", "Armour-Set-Void", "Armour-Set-Barrows",
            "Tool-Pickaxe", "Tool-Axe", "Tool-Fishing", "Tool-Other",
            "Skilling-Resource-Gem", "Skilling-Resource-Log", "Skilling-Resource-Ore", "Skilling-Resource-Bar",
            "Skilling-Resource-Herb", "Skilling-Resource-Seed", "Skilling-Resource-Fish", "Skilling-Resource-Other",
            "Clue-Scrolls", "Quest-Items", "Cosmetic/Holiday", "Miscellaneous"
    );
    private static final Map<String, Map<String, Double>> SIMILARITY_CACHE = new HashMap<>();

    static {
        // Tool Levels (Mining Level for Pickaxes, Woodcutting for Axes)
        TOOL_LEVELS_PICKAXE.put("bronze pickaxe", 1);
        TOOL_LEVELS_PICKAXE.put("iron pickaxe", 1);
        TOOL_LEVELS_PICKAXE.put("steel pickaxe", 6);
        TOOL_LEVELS_PICKAXE.put("black pickaxe", 11);
        TOOL_LEVELS_PICKAXE.put("mithril pickaxe", 21);
        TOOL_LEVELS_PICKAXE.put("adamant pickaxe", 31);
        TOOL_LEVELS_PICKAXE.put("rune pickaxe", 41);
        TOOL_LEVELS_PICKAXE.put("dragon pickaxe", 61);
        TOOL_LEVELS_PICKAXE.put("infernal pickaxe", 61);
        TOOL_LEVELS_PICKAXE.put("crystal pickaxe", 71);
        TOOL_LEVELS_PICKAXE.put("3rd age pickaxe", 61);

        TOOL_LEVELS_AXE.put("bronze axe", 1);
        TOOL_LEVELS_AXE.put("iron axe", 1);
        TOOL_LEVELS_AXE.put("steel axe", 6);
        TOOL_LEVELS_AXE.put("black axe", 11);
        TOOL_LEVELS_AXE.put("mithril axe", 21);
        TOOL_LEVELS_AXE.put("adamant axe", 31);
        TOOL_LEVELS_AXE.put("rune axe", 41);
        TOOL_LEVELS_AXE.put("dragon axe", 61);
        TOOL_LEVELS_AXE.put("infernal axe", 61);
        TOOL_LEVELS_AXE.put("crystal axe", 71);
        TOOL_LEVELS_AXE.put("3rd age axe", 61);

        GEAR_TIERS.put("bronze", 1);
        GEAR_TIERS.put("iron", 10);
        GEAR_TIERS.put("steel", 20);
        GEAR_TIERS.put("black", 25);
        GEAR_TIERS.put("mithril", 30);
        GEAR_TIERS.put("adamant", 40);
        GEAR_TIERS.put("rune", 50);
        GEAR_TIERS.put("dragon", 60);
        GEAR_TIERS.put("barrows", 70);
        GEAR_TIERS.put("bandos", 75);
        GEAR_TIERS.put("armadyl", 75);
        GEAR_TIERS.put("ancestral", 75);
        GEAR_TIERS.put("crystal armour", 75);
        GEAR_TIERS.put("torva", 80);
        GEAR_TIERS.put("masori", 80);
        GEAR_TIERS.put("virtus", 80);

        GEM_LEVELS.putAll(Map.of("uncut opal", 1, "opal", 1, "uncut jade", 13, "jade", 13, "uncut red topaz", 16, "red topaz", 16));
        GEM_LEVELS.putAll(Map.of("uncut sapphire", 20, "sapphire", 20, "uncut emerald", 27, "emerald", 27, "uncut ruby", 34, "ruby", 34));
        GEM_LEVELS.putAll(Map.of("uncut diamond", 43, "diamond", 43, "uncut dragonstone", 55, "dragonstone", 55));
        GEM_LEVELS.putAll(Map.of("uncut onyx", 67, "onyx", 67, "uncut zenyte", 89, "zenyte", 89));

        LOG_LEVELS.putAll(Map.of("logs", 1, "oak logs", 15, "willow logs", 30, "teak logs", 35, "arctic pine logs", 42));
        LOG_LEVELS.putAll(Map.of("maple logs", 45, "mahogany logs", 50, "yew logs", 60, "magic logs", 75, "redwood logs", 90));

        ORE_LEVELS.putAll(Map.of("copper ore", 1, "tin ore", 1, "iron ore", 15, "silver ore", 20, "coal", 30, "gold ore", 40));
        ORE_LEVELS.putAll(Map.of("mithril ore", 55, "adamantite ore", 70, "runite ore", 85, "amethyst", 92));

        BAR_LEVELS.putAll(Map.of("bronze bar", 1, "iron bar", 15, "steel bar", 30, "gold bar", 40));
        BAR_LEVELS.putAll(Map.of("mithril bar", 50, "adamantite bar", 70, "runite bar", 85));

        HERB_LEVELS.putAll(Map.of("grimy guam leaf", 3, "guam leaf", 3, "grimy marrentill", 5, "marrentill", 5, "grimy tarromin", 11, "tarromin", 11));
        HERB_LEVELS.putAll(Map.of("grimy harralander", 20, "harralander", 20, "grimy ranarr weed", 25, "ranarr weed", 25));
        HERB_LEVELS.putAll(Map.of("grimy toadflax", 30, "toadflax", 30, "grimy irit leaf", 40, "irit leaf", 40));
        HERB_LEVELS.putAll(Map.of("grimy avantoe", 48, "avantoe", 48, "grimy kwuarm", 54, "kwuarm", 54));
        HERB_LEVELS.putAll(Map.of("grimy snapdragon", 59, "snapdragon", 59, "grimy cadantine", 65, "cadantine", 65));
        HERB_LEVELS.putAll(Map.of("grimy lantadyme", 67, "lantadyme", 67, "grimy dwarf weed", 70, "dwarf weed", 70));
        HERB_LEVELS.putAll(Map.of("grimy torstol", 75, "torstol", 75));

        SEED_LEVELS_FARMING.put("guam seed", 9);
        SEED_LEVELS_FARMING.put("ranarr seed", 32);
        SEED_LEVELS_FARMING.put("snapdragon seed", 62);
        SEED_LEVELS_FARMING.put("torstol seed", 75);
        SEED_LEVELS_FARMING.put("irit seed", 40);
        SEED_LEVELS_FARMING.put("avantoe seed", 48);
        SEED_LEVELS_FARMING.put("lantadyme seed", 67);
        SEED_LEVELS_FARMING.put("dwarf weed seed", 70);
        SEED_LEVELS_FARMING.put("kwuarm seed", 54);
        SEED_LEVELS_FARMING.put("harralander seed", 20);
        SEED_LEVELS_FARMING.put("marrentill seed", 5);
        SEED_LEVELS_FARMING.put("tarromin seed", 11);

    }

    @Inject
    Client client;
    Mouse mouse;
    private volatile boolean isRunningTask = false;

    // Static helper method, can be called from BankSortItem constructor
    private static String classifyItem(BankSortItem item) {
        String name = item.getBaseName(); // Use baseName for general classification
        String processedName = item.getProcessedName(); // Use processedName for specific matches like runes, herbs

        // Check for sets first using item.getItemSetType() which is determined earlier
        if ("graceful".equals(item.getItemSetType())) return "Armour-Set-Graceful";
        if ("void".equals(item.getItemSetType())) return "Armour-Set-Void";
        if (item.getItemSetType() != null && item.getItemSetType().startsWith("barrows-")) return "Armour-Set-Barrows";


        if (name.contains("coins") || name.equals("platinum token")) return "Currency"; // Adjusted "coin" to "coins"
        if (name.contains("teleport tab") || name.contains("teleport scroll") || name.endsWith(" teleport")) return "Teleportation";
        if (name.contains("potion") && !name.contains("prayer book page")) return "Potions"; // Exclude "prayer book page set" for example.
        // Basic food check - might need expansion
        if (name.contains("shark") || name.contains("monkfish") || name.contains("karambwan") || name.contains("anglerfish") || name.contains("cake") || name.contains("pie") || name.contains("stew") || name.contains("pizza") || name.contains("bread")) return "Food";
        if (processedName.endsWith(" rune")) return "Runes";
        if (name.contains("dart") || name.contains("arrow") || name.contains("bolts") || name.contains("javelin") || name.contains("throwing axe") || name.contains("knife") || name.contains("chinchompa") || name.endsWith(" shot") || name.endsWith(" shell")) return "Ammunition";

        // Tools
        if (name.contains("pickaxe")) return "Tool-Pickaxe";
        if (name.contains(" axe") && !name.contains("battleaxe") && !name.contains("greataxe") && !name.contains("throwing axe")) return "Tool-Axe"; // woodcutting axe
        if (name.contains("harpoon") || name.contains("fishing rod") || name.contains("fly fishing rod") || name.contains("lobster pot") || name.contains("small fishing net") || name.contains("big fishing net") || name.contains("fishing bait") || name.contains("feather")) return "Tool-Fishing";
        if (name.contains("tinderbox") || name.contains("chisel") || name.contains("hammer") || name.contains("spade") || name.contains("rake") || name.contains("seed dibber") || name.contains("secateurs") || name.contains("watering can") || name.contains("glassblowing pipe")) return "Tool-Other";


        // Weapons (Melee, Ranged, Magic) - Order by commonality/exclusivity
        // Magic weapons often contain "staff" or "wand"
        if (name.contains("staff") || name.contains("wand") || name.contains("trident") || name.contains("sceptre") || name.contains("crozier") || name.contains("book of") || name.contains("ancient sceptre")) return "Weapon-Magic";
        // Ranged weapons
        if (name.contains("shortbow") || name.contains("longbow") || name.contains("comp bow") || name.contains("crossbow") || name.contains("c'bow") || name.contains("ballista") || name.contains("blowpipe") || name.contains("crystal bow") || name.contains("dark bow")) return "Weapon-Ranged";
        // Melee (broad category, ensure it's checked after more specific weapon types)
        if (name.contains("scimitar") || name.contains("sword") || name.contains("longsword") || name.contains("dagger") || name.contains("mace") || name.contains("warhammer") || name.contains("battleaxe") || name.contains("halberd") || name.contains("spear") || name.contains("hasta") || name.contains("whip") || name.contains("bludgeon") || name.contains("rapier") || name.contains("greataxe") || name.contains("greatsword") || name.contains("maul") || name.contains("flail") || name.contains("claws")) return "Weapon-Melee";


        // Armour (Helmet, Cape, Amulet, Body, Legs, Shield, Gloves, Boots, Ring)
        // Order is important to prevent "dragon boots" being classified as "Armour-Dragon" before "Armour-Boots"
        if (name.contains("helm") || name.contains("coif") || name.contains("hood") || name.contains("mask") || name.contains("circlet") || name.contains("sallet") || name.contains("med helm") || name.contains("full helm")) return "Armour-Helmet";
        if (name.contains("cape") || name.contains("cloak") || name.contains("avas accumulator") || name.contains("avas assembler")) return "Armour-Cape";
        if (name.contains("amulet") || name.contains("necklace") || name.contains("symbol") || name.contains("stole")) return "Armour-Amulet";
        if (name.contains("platebody") || name.contains("chainbody") || name.contains("body") && !name.contains("body rune") || name.contains("chestplate") || name.contains("hauberk") || name.contains("robe top") || name.contains("apron") || name.contains("d'hide body") || name.contains("leather body")) return "Armour-Body";
        if (name.contains("platelegs") || name.contains("plateskirt") || name.contains("legs") && !name.contains("pegasian") /*pegasian legs*/ || name.contains("skirt") && !name.contains("chainskirt") || name.contains("chaps") || name.contains("tassets") || name.contains("robe bottom") || name.contains("d'hide chaps")) return "Armour-Legs";
        if (name.contains("kiteshield") || name.contains("sq shield") || name.contains("shield") && !name.contains("dragonfire ward") || name.contains("defender") || name.contains("toktz-ket-xil") || name.contains("book of") || name.contains("ward") && !name.contains("dwarf weed")) return "Armour-Shield";
        if (name.contains("gloves") || name.contains("gauntlets") || name.contains("vambraces") || name.contains("bracers") || name.contains("d'hide vamb")) return "Armour-Gloves";
        if (name.contains("boots")) return "Armour-Boots";
        if (name.contains("ring") && !name.contains("slayer") && !name.contains("ring of dueling")) return "Armour-Ring"; // Exclude utility rings if they go elsewhere

        // Skilling Resources
        if (name.contains("uncut") && (GEM_LEVELS.containsKey(processedName) || GEM_LEVELS.containsKey(name.replace("uncut ", "")))) return "Skilling-Resource-Gem"; // Uncut gems
        if (GEM_LEVELS.containsKey(processedName) || GEM_LEVELS.containsKey(name)) return "Skilling-Resource-Gem"; // Cut gems
        if (LOG_LEVELS.containsKey(processedName) || name.endsWith(" logs")) return "Skilling-Resource-Log";
        if (ORE_LEVELS.containsKey(processedName) || name.endsWith(" ore")) return "Skilling-Resource-Ore";
        if (BAR_LEVELS.containsKey(processedName) || name.endsWith(" bar")) return "Skilling-Resource-Bar";
        if (name.contains("grimy") || HERB_LEVELS.containsKey(processedName)) return "Skilling-Resource-Herb";
        if (SEED_LEVELS_FARMING.containsKey(processedName) || name.endsWith(" seed") || name.endsWith(" sapling")) return "Skilling-Resource-Seed";
        if (name.startsWith("raw ") || name.startsWith("cooked ")) return "Skilling-Resource-Fish"; // Crude fish check
        if (name.contains("bone") || name.contains("ashes") || name.contains("ensouled head") || name.contains("hide") || name.contains("fleece") || name.contains("essence") || name.contains("vial of water") || name.contains("vial") && name.length() < 10 || name.contains("eye of newt") || name.contains("limpwurt root") || name.contains("snape grass") || name.contains("bird nest") || name.contains("bucket of")) return "Skilling-Resource-Other";


        if (name.contains("clue scroll") || name.contains("reward casket") || name.contains("master scroll book")) return "Clue-Scrolls";
        if (name.contains("quest item placeholder") || name.contains("key") && !name.contains("crystal key")) return "Quest-Items"; // Generic key check
        if (name.contains("event") || name.contains("holiday") || name.contains("cosmetic") || name.contains("santa hat") || name.contains("partyhat")) return "Cosmetic/Holiday";


        return "Miscellaneous";
    }

    private static String getResourceBucket(String baseName, String processedName) {
        if (GEM_LEVELS.containsKey(processedName)) return processedName.startsWith("uncut") ? "gem-uncut" : "gem-cut";
        if (LOG_LEVELS.containsKey(processedName) || LOG_LEVELS.containsKey(baseName)) return "log";
        if (ORE_LEVELS.containsKey(processedName) || ORE_LEVELS.containsKey(baseName)) return "ore"; // Added baseName check
        if (BAR_LEVELS.containsKey(processedName) || BAR_LEVELS.containsKey(baseName)) return "bar"; // Added baseName check
        if (HERB_LEVELS.containsKey(processedName)) return processedName.startsWith("grimy") ? "herb-grimy" : "herb-cleaned";
        if (SEED_LEVELS_FARMING.containsKey(processedName) || processedName.endsWith(" seed")) return "seed";
        if (processedName.contains("bone")) return "bone";
        if (processedName.contains("hide") || processedName.contains("leather") || processedName.contains("fleece")) return "hide"; // Expanded
        if (processedName.startsWith("cooked ")) return "fish-cooked";
        if (processedName.startsWith("raw ")) return "fish-raw";
        // Add more specific resource types if needed
        return "other-resource";
    }

    private static double jaroSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0; // Null check
        if (s1.equals(s2)) return 1.0;
        int len1 = s1.length(), len2 = s2.length();
        if (len1 == 0 || len2 == 0) return 0.0;

        int matchDistance = Math.max(len1, len2) / 2 - 1;
        if (matchDistance < 0) matchDistance = 0; // Ensure non-negative

        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];
        int matches = 0;
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, len2);
            for (int j = start; j < end; j++) {
                if (s2Matches[j] || s1.charAt(i) != s2.charAt(j)) continue;
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) return 0.0;

        double t = 0; // Transpositions
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) continue;
            while (k < len2 && !s2Matches[k]) k++; // Ensure k advances only up to len2
            if (k < len2 && s1.charAt(i) != s2.charAt(k++)) t++; // Check k < len2 before charAt
        }
        t /= 2.0;
        return ((double) matches / len1 + (double) matches / len2 + (matches - t) / matches) / 3.0;
    }

    private static double jaroWinklerSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0; // Handles case where both are empty too
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        s1 = s1.toLowerCase().trim(); // Normalize
        s2 = s2.toLowerCase().trim();
        if (s1.equals(s2)) return 1.0; // Check again after normalization

        // Cache implementation (simple, consider thread-safety if script becomes multi-threaded)
        String cacheKey = s1.compareTo(s2) < 0 ? s1 + "|" + s2 : s2 + "|" + s1; // Canonical key
        synchronized (SIMILARITY_CACHE) {
            Map<String, Double> cachedEntry = SIMILARITY_CACHE.get(cacheKey);
            if (cachedEntry != null && cachedEntry.containsKey("similarity")) {
                return cachedEntry.get("similarity");
            }
        }

        double jaroSim = jaroSimilarity(s1, s2);
        if (jaroSim < 0.7) { // Optimization: Winkler adjustment is less impactful for low Jaro scores
            // Cache and return early for low Jaro scores
            synchronized (SIMILARITY_CACHE) {
                Map<String, Double> resultMap = new HashMap<>();
                resultMap.put("similarity", jaroSim);
                SIMILARITY_CACHE.put(cacheKey, resultMap);
            }
            return jaroSim;
        }


        int prefixLength = 0;
        int maxPrefixLength = Math.min(Math.min(s1.length(), s2.length()), 4); // Max prefix is 4
        for (int i = 0; i < maxPrefixLength; i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefixLength++;
            else break;
        }

        double result = jaroSim + (prefixLength * 0.1 * (1.0 - jaroSim));
        result = Math.min(result, 1.0); // Ensure result does not exceed 1.0


        synchronized (SIMILARITY_CACHE) {
            Map<String, Double> resultMap = new HashMap<>();
            resultMap.put("similarity", result);
            SIMILARITY_CACHE.put(cacheKey, resultMap);
        }

        return result;
    }

    private static int checkCommonSubstrings(String s1, String s2) {
        int minSubstringLength = 3;
        int maxSubstringLengthConsidered = 7; // Don't look for overly long common substrings
        int boostPoints = 0;
        Set<String> foundSubstrings = new HashSet<>(); // Avoid double counting for same substring

        for (int i = 0; i <= s1.length() - minSubstringLength; i++) {
            for (int j = minSubstringLength; j <= Math.min(s1.length() - i, maxSubstringLengthConsidered); j++) {
                String substring = s1.substring(i, i + j);
                if (s2.contains(substring) && !foundSubstrings.contains(substring)) {
                    boostPoints += (j - minSubstringLength + 1); // Longer matches give more points
                    foundSubstrings.add(substring);
                }
            }
        }
        return boostPoints;
    }

    @Override
    public boolean run() {
        if (isRunningTask) {
            Microbot.log("Script is already running. Please wait for the current execution to finish.");
            return false;
        }

        mouse = Microbot.getMouse(); // Assuming Microbot.getMouse() is thread-safe or called from client thread
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }

        isRunningTask = true;
        mainScheduledFuture = scheduledExecutorService.schedule(() -> {
            try {
                if (!Rs2Bank.isOpen()) {
                    Microbot.log("Bank is not open. Please open the bank and run the script again.");
                    return; // Exit this scheduled task
                }

                List<BankSortItem> itemsInCurrentTab = getItemsInCurrentTab();
                if (itemsInCurrentTab == null || itemsInCurrentTab.isEmpty()) { // Corrected check
                    Microbot.log("Failed to retrieve items from the current bank tab, or tab is empty. Please ensure the bank is open and try again.");
                    return; // Exit this scheduled task
                }

                Microbot.log("Found " + itemsInCurrentTab.size() + " items in the current tab.");
                Microbot.log("Applying workflow-based sorting logic...");
                long startTime = System.currentTimeMillis();
                List<BankSortItem> sortedItems = sortItemsAndAssignSlots(itemsInCurrentTab);
                long endTime = System.currentTimeMillis();
                Microbot.log("Item list sorted in " + (endTime - startTime) + "ms. Calculated " + sortedItems.size() + " target positions.");

                int currentBankTab = Microbot.getVarbitValue(Varbits.CURRENT_BANK_TAB);
                int tabAbsoluteStartIndex = calculateTabAbsoluteStartIndex(currentBankTab);

                rearrangeBankItems(sortedItems, tabAbsoluteStartIndex);

                Microbot.log("Item list sorted with fuzzy similarity prioritization in " + (endTime - startTime) + "ms. Calculated " + sortedItems.size() + " target positions.");
                Microbot.log("It's recommended to review the tab. If issues, run again or manually adjust.");
            } catch (Exception e) { // Single catch block for Exception
                log.error("Error during bank sorting script execution:", e);
                Microbot.log("An unexpected error occurred: " + e.getMessage());
            } finally {
                isRunningTask = false;
            }
        }, 0, TimeUnit.SECONDS);
        // Removed stray code that was here
        return true;
    }

    private List<BankSortItem> getItemsInCurrentTab() {
        List<Widget> allBankItemWidgets = Rs2Bank.getItems();
        if (allBankItemWidgets == null || allBankItemWidgets.isEmpty()) {
            Microbot.log("Rs2Bank.getItems() returned null or empty list.");
            return Collections.emptyList();
        }

        int currentTabVar = Microbot.getVarbitValue(Varbits.CURRENT_BANK_TAB);
        int tabAbsoluteStartIndex = calculateTabAbsoluteStartIndex(currentTabVar);
        int itemsInThisTabCount;

        if (currentTabVar == 0) { // "View All" or main tab items
            int presentItemsInMainSection = 0;

            for (Widget widget : allBankItemWidgets) {
                if (widget.getItemId() != -1 && widget.getItemId() != 6512) { // Filter placeholders

                }
            }
            // This means for tab 0, itemsInThisTabCount must be correctly determined.
            int mainSectionItemCount = 0;
            for(int i = 0; i < allBankItemWidgets.size(); i++) {
                Widget widget = allBankItemWidgets.get(i);
                if (widget.getIndex() >= tabAbsoluteStartIndex && widget.getItemId() != -1 && widget.getItemId() != 6512) {
                    mainSectionItemCount++;
                }
            }
            long totalNonPlaceholderItems = allBankItemWidgets.stream()
                    .filter(w -> w.getItemId() != -1 && w.getItemId() != 6512)
                    .count();
            itemsInThisTabCount = (int) totalNonPlaceholderItems - tabAbsoluteStartIndex;
            if (itemsInThisTabCount < 0) itemsInThisTabCount = 0; // Should not happen if logic is sound


        } else { // Specific tab (1-9)
            itemsInThisTabCount = Microbot.getVarbitValue(Varbits.BANK_TAB_ONE_COUNT + currentTabVar - 1);
        }


        int tabAbsoluteEndIndex = tabAbsoluteStartIndex + itemsInThisTabCount;
        List<BankSortItem> tabItems = new ArrayList<>();
        for (Widget widget : allBankItemWidgets) {
            int widgetAbsIndex = widget.getIndex(); // This is the slot index in the bank
            // We are interested in items whose current slot index falls within the calculated range for the current tab
            if (widgetAbsIndex >= tabAbsoluteStartIndex && widgetAbsIndex < tabAbsoluteEndIndex) {
                if (widget.getItemId() != -1 && widget.getItemId() != 6512) { // Filter out placeholders (item ID 6512) and empty slots
                    String name = widget.getName();
                    if (name != null && !name.isEmpty()) {
                        tabItems.add(new BankSortItem(widget.getItemId(), name, widgetAbsIndex));
                    }
                }
            }
        }
        tabItems.sort(Comparator.comparingInt(BankSortItem::getOriginalIndex));
        return tabItems;
    }

    private int calculateTabAbsoluteStartIndex(int tabNumber) {
        int startIndex = 0;
        int limit = (tabNumber == 0) ? 9 : tabNumber;
        for (int i = 1; i < limit; i++) { // Sum counts of tabs *before* the current tab
            startIndex += Microbot.getVarbitValue(Varbits.BANK_TAB_ONE_COUNT + i - 1);
        }
        if (tabNumber == 0) { // For "View All", the "main" items start after ALL numbered tabs
            startIndex = 0; // Reset, then sum all tab counts
            for (int i = 1; i <= 9; i++) { // Assuming max 9 tabs
                startIndex += Microbot.getVarbitValue(Varbits.BANK_TAB_ONE_COUNT + i - 1);
            }
        }
        return startIndex;
    }

    /**
     * Determines if a category is part of a skilling workflow (e.g., raw -> processed).
     */
    private boolean isWorkflowCategory(String category) {
        return category.startsWith("Skilling-Resource-") || category.equals("Potions");
    }

    /**
     * Sorts bank items by separating them into two main groups: "Workflow" items (like herbs, ores, potions)
     * and "Standard" items (like gear, tools, runes). Workflow items are sorted by their level and processing
     * stage to create an intuitive assembly-line flow. Standard items are sorted by their existing rules.
     */
    private List<BankSortItem> sortItemsAndAssignSlots(List<BankSortItem> items) {
        if (items.isEmpty()) return Collections.emptyList();

        // 1. Separate items into workflow and standard groups.
        List<BankSortItem> workflowItems = items.stream()
                .filter(item -> isWorkflowCategory(item.getCategory()))
                .collect(Collectors.toList());

        Map<String, List<BankSortItem>> otherItemsByCategory = items.stream()
                .filter(item -> !isWorkflowCategory(item.getCategory()))
                .collect(Collectors.groupingBy(BankSortItem::getCategory));

        // 2. Sort the workflow items based on their level and processing stage.
        // This groups items by level (top-to-bottom) and then by stage (left-to-right).
        workflowItems.sort(
                Comparator.comparingInt(BankSortItem::getItemLevel)
                        .thenComparingInt(BankSortItem::getWorkflowStage)
                        .thenComparing(BankSortItem::getBaseName) // Tie-break for items with same level/stage
                        .thenComparing(item -> -item.getDoseOrCharge()) // Higher doses first
        );

        // 3. Sort the standard items using their respective logic (sets, tiers, fuzzy matching).
        Map<String, List<BankSortItem>> sortedOtherItemsByCategory = new LinkedHashMap<>();
        for (Map.Entry<String, List<BankSortItem>> entry : otherItemsByCategory.entrySet()) {
            String category = entry.getKey();
            List<BankSortItem> categoryItems = entry.getValue();

            // Use special sorting for gear, tools, etc., and fuzzy clustering for the rest.
            if (category.startsWith("Armour-") || category.startsWith("Weapon-") ||
                    category.startsWith("Tool-") || category.equals("Runes")) {
                sortSpecialCategory(categoryItems, category);
                sortedOtherItemsByCategory.put(category, categoryItems);
            } else {
                sortedOtherItemsByCategory.put(category, applySimilarityClustering(categoryItems));
            }
        }

        // 4. Recombine all items into a final list, preserving the master CATEGORY_ORDER.
        List<BankSortItem> finalSortedList = new ArrayList<>();
        boolean workflowItemsAdded = false;

        for (String category : CATEGORY_ORDER) {
            // If the category is a workflow type, add the entire sorted workflow block.
            if (isWorkflowCategory(category)) {
                if (!workflowItemsAdded) {
                    finalSortedList.addAll(workflowItems);
                    workflowItemsAdded = true;
                }
            }
            // Otherwise, add the sorted standard items for that category.
            else if (sortedOtherItemsByCategory.containsKey(category)) {
                finalSortedList.addAll(sortedOtherItemsByCategory.get(category));
            }
        }

        // Add any items from categories not present in the master CATEGORY_ORDER to the end.
        sortedOtherItemsByCategory.forEach((category, itemList) -> {
            if (!CATEGORY_ORDER.contains(category)) {
                finalSortedList.addAll(itemList);
            }
        });

        return finalSortedList;
    }

    private List<BankSortItem> applySimilarityClustering(List<BankSortItem> items) {
        if (items.size() <= 1) {
            return items;
        }

        final double SIMILARITY_THRESHOLD = 0.95;
        final int MAX_CLUSTER_SIZE = 30; // Example, adjust as needed

        List<BankSortItem> specialItems = new ArrayList<>();
        List<BankSortItem> regularItems = new ArrayList<>();

        for (BankSortItem item : items) {
            if (item.getItemSetType() != null || item.getItemTier() != null) {
                specialItems.add(item);
            } else {
                regularItems.add(item);
            }
        }

        List<SimilarityCluster> clusters = new ArrayList<>();
        if (!regularItems.isEmpty()) {
            // Sort regular items alphabetically first to make clustering more deterministic/stable
            regularItems.sort(Comparator.comparing(BankSortItem::getProcessedName));
            clusters.add(new SimilarityCluster(regularItems.get(0), SIMILARITY_THRESHOLD));
        }

        for (int i = 1; i < regularItems.size(); i++) {
            BankSortItem item = regularItems.get(i);
            SimilarityCluster bestCluster = null;
            double bestSimilarity = 0.0; // Must be > SIMILARITY_THRESHOLD to be considered

            for (SimilarityCluster cluster : clusters) {
                if (cluster.getItems().size() >= MAX_CLUSTER_SIZE) {
                    continue;
                }
                // Check if item can be added based on centroid similarity first
                if (cluster.canAddItem(item)) {
                    double similarity = cluster.getMaxSimilarityWithItem(item); // More precise check
                    if (similarity > bestSimilarity) { // No need to check > SIMILARITY_THRESHOLD again if canAddItem was true based on it.
                        bestSimilarity = similarity;
                        bestCluster = cluster;
                    }
                }
            }

            if (bestCluster != null) {
                bestCluster.addItem(item);
            } else {
                clusters.add(new SimilarityCluster(item, SIMILARITY_THRESHOLD));
            }
        }

        for (BankSortItem specialItem : specialItems) {
            clusters.add(new SimilarityCluster(specialItem, SIMILARITY_THRESHOLD)); // Effectively a cluster of one
        }


        mergeSimilarClusters(clusters, SIMILARITY_THRESHOLD + 0.05); // Slightly higher threshold for merging clusters

        for (SimilarityCluster cluster : clusters) {
            cluster.getItems().sort(Comparator.comparing(BankSortItem::getBaseName)
                    .thenComparing((i) -> i.getDoseOrCharge() != -1 ? -i.getDoseOrCharge() : Integer.MAX_VALUE) // Sort no-dose last
                    .thenComparing(BankSortItem::getProcessedName)); // Final tie-breaker
        }

        // Sort clusters by their first item's representative name or category
        clusters.sort(Comparator.comparing(cluster -> cluster.getItems().get(0).getBaseName()));

        List<BankSortItem> sortedOutputItems = new ArrayList<>();
        for (SimilarityCluster cluster : clusters) {
            sortedOutputItems.addAll(cluster.getItems());
        }

        return sortedOutputItems;
    }

    private void sortSpecialCategory(List<BankSortItem> items, String category) {
        items.sort((itemA, itemB) -> {
            // Group special item sets (Graceful, Void, Barrows, etc.)
            String setTypeA = itemA.getItemSetType();
            String setTypeB = itemB.getItemSetType();

            if (setTypeA != null || setTypeB != null) { // One or both items are part of a defined set
                if (setTypeA != null && setTypeB == null) return -1; // Set items first
                if (setTypeB != null && setTypeA == null) return 1;
                // Both are set items
                if (setTypeA.equals(setTypeB)) { // Same set type
                    List<String> setOrder = ITEM_SETS.get(setTypeA);
                    if (setOrder != null) {
                        // Use compareSetItems for detailed piece ordering within the same set
                        return compareSetItems(itemA, itemB, setOrder);
                    }
                    // Fallback for sets not in ITEM_SETS detail or if compareSetItems is insufficient
                    return itemA.getProcessedName().compareTo(itemB.getProcessedName());
                } else { // Different set types
                    List<String> globalSetOrder = new ArrayList<>(ITEM_SETS.keySet());
                    int indexA = globalSetOrder.indexOf(setTypeA);
                    int indexB = globalSetOrder.indexOf(setTypeB);
                    if (indexA != -1 && indexB != -1) {
                        return Integer.compare(indexA, indexB);
                    }
                    // Fallback if one set type isn't in global order (shouldn't happen if ITEM_SETS is comprehensive)
                    return setTypeA.compareTo(setTypeB);
                }
            }

            // Tier comparison for Tools, Weapons, Armour not in specific sets
            if (category.startsWith("Tool-") || category.startsWith("Weapon-") || category.startsWith("Armour-")) {
                int tierCompare = compareByTier(itemA, itemB, category);
                if (tierCompare != 0) return tierCompare;
            }

            if ("Runes".equals(category)) {
                int runeIndexA = RUNE_TYPES_ORDER.indexOf(itemA.getProcessedName());
                int runeIndexB = RUNE_TYPES_ORDER.indexOf(itemB.getProcessedName());
                if (runeIndexA != -1 || runeIndexB != -1) { // One or both runes are in the defined order
                    if (runeIndexA != -1 && runeIndexB == -1) return -1; // Known rune first
                    if (runeIndexB != -1 && runeIndexA == -1) return 1;
                    if (runeIndexA != runeIndexB) return Integer.compare(runeIndexA, runeIndexB);
                }
            }

            // NOTE: The logic for 'Skilling-Resource-' and 'Potions' has been moved to the new workflow sorter
            // in sortItemsAndAssignSlots. This method now handles non-workflow special cases.

            // Teleportation items with charges (like glory, dueling)
            if (itemA.getDoseOrCharge() != -1 && itemB.getDoseOrCharge() != -1 &&
                    (category.equals("Teleportation") || itemA.getBaseName().contains("amulet of glory") || itemA.getBaseName().contains("ring of dueling"))) {
                int nameCompare = itemA.getBaseName().compareTo(itemB.getBaseName());
                if (nameCompare != 0) return nameCompare;
                return Integer.compare(itemB.getDoseOrCharge(), itemA.getDoseOrCharge()); // Higher charges first
            }

            // General alphabetical sort by base name, then by dose/charge (higher first), then by full processed name
            int baseNameCompare = itemA.getBaseName().compareTo(itemB.getBaseName());
            if (baseNameCompare != 0) return baseNameCompare;

            if (itemA.getDoseOrCharge() != -1 || itemB.getDoseOrCharge() != -1) {
                // Ensure consistent sorting: items with doses vs no doses, and order of doses
                if (itemA.getDoseOrCharge() != -1 && itemB.getDoseOrCharge() == -1) return -1; // Dosed item first
                if (itemB.getDoseOrCharge() != -1 && itemA.getDoseOrCharge() == -1) return 1;
                if (itemA.getDoseOrCharge() != -1 && itemB.getDoseOrCharge() != -1) {
                    return Integer.compare(itemB.getDoseOrCharge(), itemA.getDoseOrCharge()); // Higher dose first
                }
            }

            return itemA.getProcessedName().compareTo(itemB.getProcessedName());
        });
    }

    private int compareSetItems(BankSortItem itemA, BankSortItem itemB, List<String> setOrderKeywords) {

        int indexA = -1, indexB = -1;
        String nameA = itemA.getProcessedName(); // Use processedName for matching against keywords
        String nameB = itemB.getProcessedName();

        for (int i = 0; i < setOrderKeywords.size(); i++) {
            String keyword = setOrderKeywords.get(i); // e.g., "graceful hood" or just "hood"
            if (nameA.contains(keyword)) { // If keyword is "graceful hood", it will match "graceful hood"
                indexA = i;
            }
            if (nameB.contains(keyword)) {
                indexB = i;
            }
        }

        if (indexA != -1 && indexB != -1) {
            if (indexA != indexB) {
                return Integer.compare(indexA, indexB);
            }
            // If they map to the same keyword index (e.g. void helms), use name compare
            return nameA.compareTo(nameB);
        }
        if (indexA != -1) return -1; // itemA found in order, itemB not
        if (indexB != -1) return 1;  // itemB found in order, itemA not

        // Fallback if pieces are not in setOrderKeywords (should ideally not happen)
        return nameA.compareTo(nameB);
    }

    private int compareByTier(BankSortItem itemA, BankSortItem itemB, String category) {
        Integer tierValueA = null, tierValueB = null;

        // Prioritize specific tool maps if category matches
        if (category.equals("Tool-Pickaxe")) {
            tierValueA = TOOL_LEVELS_PICKAXE.get(itemA.getBaseName()); // Use baseName for tools typically
            tierValueB = TOOL_LEVELS_PICKAXE.get(itemB.getBaseName());
        } else if (category.equals("Tool-Axe")) {
            tierValueA = TOOL_LEVELS_AXE.get(itemA.getBaseName());
            tierValueB = TOOL_LEVELS_AXE.get(itemB.getBaseName());
        } else if (itemA.getItemTier() != null && itemB.getItemTier() != null) {
            // General gear tier comparison using item.getItemTier() (e.g., "rune", "dragon")
            tierValueA = GEAR_TIERS.get(itemA.getItemTier());
            tierValueB = GEAR_TIERS.get(itemB.getItemTier());
        }
        // If only one has a tier (e.g. from item.getItemTier() but not specific tool map)
        else if (itemA.getItemTier() != null) tierValueA = GEAR_TIERS.get(itemA.getItemTier());
        else if (itemB.getItemTier() != null) tierValueB = GEAR_TIERS.get(itemB.getItemTier());


        if (tierValueA != null || tierValueB != null) {
            if (tierValueA != null && tierValueB == null) return -1; // Tiered item first
            if (tierValueB != null && tierValueA == null) return 1;
            if (tierValueA != null && tierValueB != null) { // Both have tiers
                int tierCompare = Integer.compare(tierValueB, tierValueA); // Higher tier value first (e.g. Rune (50) > Adamant (40))
                if (tierCompare != 0) return tierCompare;
            }
        }

        // If tiers are the same or not applicable, sort by armour piece type for "Armour-" categories
        if (category.startsWith("Armour-") && !category.startsWith("Armour-Set-")) { // Only for non-set armour pieces
            List<String> pieceOrder = Arrays.asList(
                    "helm", "full helm", "hood", "coif", "mask", // Head
                    "cape", "cloak", // Cape
                    "amulet", "necklace", // Amulet
                    "top", "platebody", "body", "chestplate", "hauberk", "robe top", // Body
                    "legs", "platelegs", "skirt", "chaps", "tassets", "robe bottom", // Legs
                    "kiteshield", "sq shield", "shield", "defender", "book", "ward", // Shield/Off-hand
                    "gloves", "gauntlets", "vambraces", "bracers", // Gloves
                    "boots", // Boots
                    "ring" // Ring
            );
            String pieceTypeA = getPieceType(itemA.getBaseName(), pieceOrder);
            String pieceTypeB = getPieceType(itemB.getBaseName(), pieceOrder);

            int pieceIndexA = pieceTypeA != null ? pieceOrder.indexOf(pieceTypeA) : pieceOrder.size(); // Unidentified pieces last
            int pieceIndexB = pieceTypeB != null ? pieceOrder.indexOf(pieceTypeB) : pieceOrder.size();

            if (pieceIndexA != pieceIndexB) {
                return Integer.compare(pieceIndexA, pieceIndexB);
            }
        }

        // Fallback to name comparison if tiers/piece types are same or not applicable
        return itemA.getBaseName().compareTo(itemB.getBaseName());
    }

    private String getPieceType(String itemName, List<String> pieceOrder) {
        for (String piece : pieceOrder) {
            if (itemName.contains(piece)) return piece;
        }
        return null;
    }

    private void rearrangeBankItems(List<BankSortItem> sortedItems, int tabAbsoluteStartIndex) {
        if (sortedItems.isEmpty()) {
            Microbot.log("No items to rearrange.");
            return;
        }
        Microbot.log("Rearranging " + sortedItems.size() + " items in bank tab starting at absolute index " + tabAbsoluteStartIndex);
        boolean[] slotCorrect = new boolean[sortedItems.size()];

        for (int i = 0; i < sortedItems.size(); i++) {
            if (!isRunning()) {
                Microbot.log("Script shutdown requested during rearrangement.");
                break;
            }
            if (!Rs2Bank.isOpen()) {
                Microbot.log("Bank closed during rearrangement. Stopping.");
                break;
            }
            if (slotCorrect[i]) continue;

            BankSortItem desiredItemForThisSlot = sortedItems.get(i);
            int targetSlotAbsolute = tabAbsoluteStartIndex + i;

            Widget widgetCurrentlyInTargetSlot = Rs2Bank.getItemWidget(targetSlotAbsolute);
            if (widgetCurrentlyInTargetSlot != null && widgetCurrentlyInTargetSlot.getItemId() == desiredItemForThisSlot.getId()) {
                slotCorrect[i] = true;
                continue;
            }

            Widget sourceWidget = findWidgetToMove(desiredItemForThisSlot, sortedItems, tabAbsoluteStartIndex, slotCorrect, i);
            if (sourceWidget == null) {
                Microbot.log("CRITICAL: Could not find source widget for '" + desiredItemForThisSlot.getProcessedName() +
                        "' (ID: " + desiredItemForThisSlot.getId() + ", OrigIdx: " + desiredItemForThisSlot.getOriginalIndex() +
                        ") to move to target slot " + targetSlotAbsolute + ". Skipping this item.");
                slotCorrect[i] = true;
                continue;
            }

            int sourceSlotAbsolute = sourceWidget.getIndex();
            if (sourceSlotAbsolute == targetSlotAbsolute) {
                slotCorrect[i] = true;
                continue;
            }

            Microbot.log("Moving '" + desiredItemForThisSlot.getProcessedName() + "' from slot " + sourceSlotAbsolute + " to slot " + targetSlotAbsolute);
            Point sourcePoint = calculateWidgetClickPoint(sourceWidget);

            // Get the widget for the target slot (it could be an item, placeholder, or empty slot representation)
            Widget targetSlotWidget = Rs2Bank.getItemWidget(targetSlotAbsolute);
            Point targetPoint;

            if (targetSlotWidget == null) {
                // This implies Rs2Bank.getItemWidget(targetSlotAbsolute) couldn't find a widget for this slot.
                // This is a problem, as we need a target UI element to drag to.
                Microbot.log("CRITICAL: Could not get widget for target slot " + targetSlotAbsolute + ". Skipping move for item '" + desiredItemForThisSlot.getProcessedName() + "'.");
                slotCorrect[i] = true; // Cannot proceed with this move
                continue;
            }
            targetPoint = calculateWidgetClickPoint(targetSlotWidget);


            if (sourcePoint.equals(new Point(-1, -1)) || targetPoint.equals(new Point(-1, -1))) {
                Microbot.log("Invalid source or target point for drag operation. Source: " + sourcePoint + ", Target: " + targetPoint + ". Skipping move for item '" + desiredItemForThisSlot.getProcessedName() + "' to slot " + targetSlotAbsolute);
                slotCorrect[i] = true;
                continue;
            }
            if (sourcePoint.equals(targetPoint)) {
                Microbot.log("Source and target points are identical for item '" + desiredItemForThisSlot.getProcessedName() + "' (Slot " + sourceSlotAbsolute + " to " + targetSlotAbsolute +"). Skipping move to prevent issues.");
                slotCorrect[i] = true; // Mark as handled if source and target are same slot effectively
                continue;
            }

            Microbot.getMouse().drag(sourcePoint, targetPoint);
            sleep(Rs2Random.between(350, 500));
            slotCorrect[i] = true;
        }
        Microbot.log("Item rearrangement loop finished.");
    }

    private Widget findWidgetToMove(BankSortItem itemToPlace, List<BankSortItem> sortedItems,
                                    int tabAbsoluteStartIndex, boolean[] slotCorrectStatus, int currentTargetSlotRelative) {
        List<Widget> currentBankItemWidgets = Rs2Bank.getItems(); // Get fresh list of items
        if (currentBankItemWidgets == null) return null;

        // We are looking for the specific BankSortItem instance (itemToPlace)
        // Its ID is itemToPlace.getId() and its originalIndex is itemToPlace.getOriginalIndex()

        Widget candidateWidget = null;

        for (Widget widget : currentBankItemWidgets) {
            if (widget.getItemId() == itemToPlace.getId()) {
                int widgetCurrentSlotAbsolute = widget.getIndex();
                int widgetCurrentSlotRelative = widgetCurrentSlotAbsolute - tabAbsoluteStartIndex;

                // Is this widget already in its final, correct sorted position?
                boolean isWidgetInFinalCorrectPlace = false;
                if (widgetCurrentSlotRelative >= 0 && widgetCurrentSlotRelative < sortedItems.size()) {
                    if (slotCorrectStatus[widgetCurrentSlotRelative] && sortedItems.get(widgetCurrentSlotRelative).getId() == widget.getItemId()) {
                        if (sortedItems.get(widgetCurrentSlotRelative) == itemToPlace) { // Comparing object reference
                            isWidgetInFinalCorrectPlace = true;
                        }
                    }
                }

                if (!isWidgetInFinalCorrectPlace) {
                    if (widget.getIndex() == itemToPlace.getOriginalIndex()) {
                        return widget; // Best candidate: the item at its original starting position.
                    }
                    if (candidateWidget == null) {
                        candidateWidget = widget; // First available candidate.
                    }
                }
            }
        }
        // If no "ideal" candidate (like the one from originalIndex) was found and returned, return any found candidate.
        return candidateWidget;
    }

    private Point calculateWidgetClickPoint(Widget widget) {
        if (widget == null) return new Point(-1, -1);
        // Ensure width and height are at least 1 to avoid issues with Rs2Random.between if they are 0
        int w = Math.max(1, widget.getWidth());
        int h = Math.max(1, widget.getHeight());
        // Get top-left canvas location
        Point canvasLocation = widget.getCanvasLocation();
        if (canvasLocation == null) return new Point(-1, -1); // Widget might not be rendered

        int clickX = Rs2Random.between(canvasLocation.getX(), canvasLocation.getX() + w -1);
        int clickY = Rs2Random.between(canvasLocation.getY(), canvasLocation.getY() + h -1);
        return new Point(clickX, clickY);
    }

    private int compareResources(BankSortItem itemA, BankSortItem itemB) {
        String bucketA = getResourceBucket(itemA.getBaseName(), itemA.getProcessedName());
        String bucketB = getResourceBucket(itemB.getBaseName(), itemB.getProcessedName());

        int bucketOrderA = RESOURCE_BUCKET_ORDER.indexOf(bucketA);
        int bucketOrderB = RESOURCE_BUCKET_ORDER.indexOf(bucketB);

        if (bucketOrderA != bucketOrderB) {
            // Handle cases where one bucket might not be in the order list
            if (bucketOrderA == -1 && bucketOrderB != -1) return 1;  // A is unknown, comes after B
            if (bucketOrderB == -1 && bucketOrderA != -1) return -1; // B is unknown, comes after A
            return Integer.compare(bucketOrderA, bucketOrderB);
        }

        // If buckets are the same, compare by level within the bucket
        Integer levelA = null, levelB = null;
        String nameA = itemA.getProcessedName(); // Use processedName for level lookups
        String nameB = itemB.getProcessedName();

        switch (bucketA) { // Both items are in the same bucket
            case "gem-cut":
            case "gem-uncut":
                levelA = GEM_LEVELS.get(nameA);
                levelB = GEM_LEVELS.get(nameB);
                break;
            case "log":
                levelA = LOG_LEVELS.get(nameA);
                levelB = LOG_LEVELS.get(nameB);
                break;
            case "ore":
                levelA = ORE_LEVELS.get(nameA);
                levelB = ORE_LEVELS.get(nameB);
                break;
            case "bar":
                levelA = BAR_LEVELS.get(nameA);
                levelB = BAR_LEVELS.get(nameB);
                break;
            case "herb-grimy":
            case "herb-cleaned":
                levelA = HERB_LEVELS.get(nameA);
                levelB = HERB_LEVELS.get(nameB);
                break;
            case "seed":
                levelA = SEED_LEVELS_FARMING.get(nameA);
                levelB = SEED_LEVELS_FARMING.get(nameB);
                break;

        }

        if (levelA != null || levelB != null) {
            if (levelA != null && levelB == null) return -1; // Leveled item first
            if (levelB != null && levelA == null) return 1;
            if (levelA != null && levelB != null) { // Both have levels
                int levelCompare = Integer.compare(levelB, levelA); // Higher level first
                if (levelCompare != 0) return levelCompare;
            }
        }

        // Fallback: if levels are same, or not applicable, or one is null, sort by name
        return nameA.compareTo(nameB);
    }

    private void mergeSimilarClusters(List<SimilarityCluster> clusters, double similarityThreshold) {
        if (clusters.size() <=1) return;
        boolean merged;
        do {
            merged = false;
            for (int i = 0; i < clusters.size(); i++) { // Iterate with index for removal
                SimilarityCluster cluster1 = clusters.get(i);
                if (cluster1.getItems().isEmpty()) continue; // Should not happen

                for (int j = i + 1; j < clusters.size(); j++) {
                    SimilarityCluster cluster2 = clusters.get(j);
                    if (cluster2.getItems().isEmpty()) continue;

                    double clusterSimilarity = getClusterSimilarity(cluster1, cluster2);

                    if (clusterSimilarity > similarityThreshold) {
                        // Merge cluster2 into cluster1
                        cluster1.getItems().addAll(cluster2.getItems()); // Simplistic merge, might need centroid update
                        clusters.remove(j); // Remove cluster2
                        j--; // Adjust index after removal
                        merged = true;
                    }
                }
            }
        } while (merged);
    }

    private double getClusterSimilarity(SimilarityCluster cluster1, SimilarityCluster cluster2) {
        // Using centroid names for similarity
        String centroidBase1 = cluster1.centroidBaseName;
        String centroidBase2 = cluster2.centroidBaseName;
        String centroidProc1 = cluster1.centroidProcessedName;
        String centroidProc2 = cluster2.centroidProcessedName;

        double baseNameSimilarity = jaroWinklerSimilarity(centroidBase1, centroidBase2);
        double processedNameSimilarity = jaroWinklerSimilarity(centroidProc1, centroidProc2);

        // Consider category match as a strong indicator for merging
        // (Assuming first items are representative of category)
        String category1 = cluster1.getItems().get(0).getCategory();
        String category2 = cluster2.getItems().get(0).getCategory();
        double categoryBoost = category1.equals(category2) ? 0.15 : 0.0;

        return Math.min(1.0, Math.max(baseNameSimilarity, processedNameSimilarity) + categoryBoost);
    }

    @Getter
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @ToString(onlyExplicitlyIncluded = true)
    private static class BankSortItem {
        @EqualsAndHashCode.Include
        @ToString.Include
        private final int id;
        private final String originalName;
        @EqualsAndHashCode.Include
        @ToString.Include
        private final int originalIndex;

        @Getter private final String processedName;
        @Getter private final String baseName;
        @Getter private final int doseOrCharge;
        @Getter private final String category;
        @Getter private final String itemSetType;
        @Getter private final String itemTier;
        @Getter private final int numericPartInName;
        // NEW: Fields to support workflow-based sorting
        @Getter private final int itemLevel;
        @Getter private final int workflowStage;

        public BankSortItem(int id, String originalName, int originalIndex) {
            this.id = id;
            this.originalName = originalName;
            this.originalIndex = originalIndex;

            String tempProcessedName = originalName.replaceAll("<col=[^>]*>(.*?)</col>", "$1").trim().toLowerCase();
            this.processedName = tempProcessedName;

            String tempBaseName;
            int tempDoseOrCharge = -1;

            Matcher suffixMatcher = ITEM_NAME_SUFFIX_PATTERN.matcher(this.processedName);
            if (suffixMatcher.matches()) {
                tempBaseName = suffixMatcher.group(1).trim();
                if (suffixMatcher.group(2) != null) {
                    try {
                        tempDoseOrCharge = Integer.parseInt(suffixMatcher.group(2));
                    } catch (NumberFormatException e) { /* remains -1 */ }
                }
            } else {
                tempBaseName = this.processedName;
            }
            this.baseName = tempBaseName;
            this.doseOrCharge = tempDoseOrCharge;

            int tempNumericPartInName = -1;
            Matcher numericPartMatcher = NUMERIC_PART_PATTERN.matcher(this.baseName);
            if (numericPartMatcher.find()) {
                try {
                    tempNumericPartInName = Integer.parseInt(numericPartMatcher.group());
                } catch (NumberFormatException e) { /* remains -1 */ }
            }
            this.numericPartInName = tempNumericPartInName;

            this.itemSetType = determineItemSetType(this.baseName);
            this.itemTier = determineItemTier(this.baseName);
            this.category = classifyItem(this);

            // Initialize workflow data
            int[] workflowData = getWorkflowData(this);
            this.itemLevel = workflowData[0];
            this.workflowStage = workflowData[1];
        }

        /**
         * Determines the level and workflow stage for an item to enable 'assembly line' sorting.
         *
         * @param item The item to analyze.
         * @return An array containing [itemLevel, workflowStage].
         */
        private static int[] getWorkflowData(BankSortItem item) {
            int level = 0;
            int stage = 99; // Default stage for non-workflow items, sorts them last in workflow group

            switch (item.getCategory()) {
                case "Skilling-Resource-Herb":
                    level = HERB_LEVELS.getOrDefault(item.getProcessedName(), 0);
                    stage = item.getProcessedName().startsWith("grimy") ? 0 : 1;
                    break;
                case "Potions":
                    // Infer potion level from its base herb for correct row positioning
                    level = HERB_LEVELS.entrySet().stream()
                            .filter(entry -> !entry.getKey().startsWith("grimy"))
                            .filter(entry -> {
                                String herbName = entry.getKey().replace(" leaf", "").replace(" weed", "");
                                return item.getBaseName().contains(herbName);
                            })
                            .map(Map.Entry::getValue)
                            .findFirst()
                            .orElse(HERB_LEVELS.getOrDefault(item.getBaseName(), 0));
                    stage = item.getBaseName().contains("(unf)") ? 2 : 4;
                    break;
                case "Skilling-Resource-Gem":
                    level = GEM_LEVELS.getOrDefault(item.getProcessedName(), 0);
                    stage = item.getProcessedName().startsWith("uncut") ? 0 : 1;
                    break;
                case "Skilling-Resource-Ore":
                    level = ORE_LEVELS.getOrDefault(item.getProcessedName(), 0);
                    stage = 0;
                    break;
                case "Skilling-Resource-Bar":
                    level = BAR_LEVELS.getOrDefault(item.getProcessedName(), 0);
                    stage = 1;
                    break;
                case "Skilling-Resource-Log":
                    level = LOG_LEVELS.getOrDefault(item.getProcessedName(), 0);
                    stage = 0;
                    break;
                case "Skilling-Resource-Seed":
                    level = SEED_LEVELS_FARMING.getOrDefault(item.getProcessedName(), 0);
                    stage = 0;
                    break;
                case "Skilling-Resource-Fish":
                    // Could add a fish level map for more precise sorting
                    stage = item.getProcessedName().startsWith("raw") ? 0 : 1;
                    break;
                case "Skilling-Resource-Other":
                    // Heuristic for potion secondaries
                    if (item.getBaseName().contains("eye of newt") || item.getBaseName().contains("limpwurt root")) {
                        stage = 3;
                    }
                    break;
            }
            return new int[]{level, stage};
        }


        private static String determineItemSetType(String baseName) {
            if (baseName.contains("graceful")) return "graceful";
            if (baseName.contains("void knight")) return "void"; // "void" not "void knight" to match ITEM_SETS key
            if (baseName.contains("dharok")) return "barrows-dharok";
            if (baseName.contains("guthan")) return "barrows-guthan";
            if (baseName.contains("torag")) return "barrows-torag";
            if (baseName.contains("verac")) return "barrows-verac";
            if (baseName.contains("karil")) return "barrows-karil";
            if (baseName.contains("ahrim")) return "barrows-ahrim";
            return null;
        }

        private static String determineItemTier(String baseName) {
            for (Map.Entry<String, Integer> entry : GEAR_TIERS.entrySet()) {
                if (baseName.contains(entry.getKey())) return entry.getKey();
            }
            if (baseName.contains("crystal pickaxe") || baseName.contains("crystal axe") || baseName.contains("crystal bow") || baseName.contains("crystal halberd") || baseName.contains("crystal shield")) return "crystal";
            if (baseName.contains("3rd age")) return "3rd age";
            return null;
        }
    }

    private static class SimilarityCluster {
        private final List<BankSortItem> items;
        private final Map<String, Double> centroidSimilarities;
        private final double similarityThreshold;
        private final String centroidProcessedName;
        private final String centroidBaseName;

        public SimilarityCluster(BankSortItem initialItem, double threshold) {
            this.items = new ArrayList<>();
            this.items.add(initialItem);
            this.centroidBaseName = initialItem.getBaseName();
            this.centroidProcessedName = initialItem.getProcessedName();
            this.centroidSimilarities = new HashMap<>();
            this.similarityThreshold = threshold;
        }

        public boolean canAddItem(BankSortItem item) {
            // Special items with set types or tiers should be in their own clusters
            if (item.getItemSetType() != null || item.getItemTier() != null) {
                return false;
            }

            // For potions, teleportation items and other items with charges/doses,
            // consider items of the same base name to be similar regardless of dose
            if (item.getDoseOrCharge() != -1 && item.getBaseName().equals(centroidBaseName)) {
                return true;
            }

            // Check fuzzy similarity using various approaches

            // 1. Full processed name similarity (includes numbers, etc.)
            String itemKey = item.getProcessedName();
            double processedNameSimilarity = centroidSimilarities.computeIfAbsent(
                    "proc:" + itemKey,
                    k -> jaroWinklerSimilarity(centroidProcessedName, itemKey));

            // 2. Base name similarity (without numbers)
            double baseNameSimilarity = centroidSimilarities.computeIfAbsent(
                    "base:" + item.getBaseName(),
                    k -> jaroWinklerSimilarity(centroidBaseName, item.getBaseName()));

            // 3. Word-by-word similarity for multi-word items
            double wordSimilarity = getWordByWordSimilarity(centroidBaseName, item.getBaseName());

            // 4. Check for common words or substrings
            double commonWordSimilarity = getCommonWordSimilarity(centroidBaseName, item.getBaseName());

            // Use the highest similarity score from all approaches
            double maxSimilarity = Math.max(
                    Math.max(processedNameSimilarity, baseNameSimilarity),
                    Math.max(wordSimilarity, commonWordSimilarity)
            );

            return maxSimilarity > similarityThreshold;
        }

        private double getWordByWordSimilarity(String str1, String str2) {
            String[] words1 = str1.split("\\s+");
            String[] words2 = str2.split("\\s+");

            // If either string has only one word, we've already checked whole string similarity
            if (words1.length <= 1 || words2.length <= 1) {
                return 0.0;
            }

            double maxWordSimilarity = 0.0;
            int matchedWords = 0;

            // Compare each word in str1 with each word in str2
            for (String word1 : words1) {
                if (word1.length() <= 2) continue; // Skip very short words

                double bestMatch = 0.0;
                for (String word2 : words2) {
                    if (word2.length() <= 2) continue; // Skip very short words

                    double similarity = jaroWinklerSimilarity(word1, word2);
                    bestMatch = Math.max(bestMatch, similarity);
                }

                if (bestMatch > 0.95) { // High similarity threshold for individual words
                    matchedWords++;
                }
                maxWordSimilarity = Math.max(maxWordSimilarity, bestMatch);
            }

            // If we have multiple matching words, boost the similarity
            if (matchedWords >= 2) {
                return Math.min(1.0, maxWordSimilarity + 0.15); // Boost but cap at 1.0
            }

            return maxWordSimilarity;
        }

        private double getCommonWordSimilarity(String str1, String str2) {
            // Check for common words or important keywords
            String[] commonKeywords = {"sword", "shield", "platebody", "platelegs", "robe", "top", "legs", "body",
                    "helm", "granite", "dragon", "rune", "adamant", "mithril", "gold", "iron",
                    "bronze", "potion", "teleport", "seeds", "herb"};

            for (String keyword : commonKeywords) {
                if (str1.contains(keyword) && str2.contains(keyword)) {
                    // If they share an important keyword, boost similarity
                    return 0.9; // Significant boost for sharing important keywords
                }
            }

            return 0.0;
        }

        public void addItem(BankSortItem item) {
            items.add(item);
        }

        public List<BankSortItem> getItems() {
            return items;
        }

        // Get the highest similarity score between this item and any item in the cluster
        public double getMaxSimilarityWithItem(BankSortItem item) {
            double maxSimilarity = 0.0;
            for (BankSortItem clusterItem : items) {
                // Use the same fuzzy matching approach
                double baseNameSimilarity = jaroWinklerSimilarity(clusterItem.getBaseName(), item.getBaseName());
                double processedNameSimilarity = jaroWinklerSimilarity(clusterItem.getProcessedName(), item.getProcessedName());
                double wordSimilarity = getWordByWordSimilarity(clusterItem.getBaseName(), item.getBaseName());
                double commonWordSimilarity = getCommonWordSimilarity(clusterItem.getBaseName(), item.getBaseName());

                double itemSimilarity = Math.max(
                        Math.max(processedNameSimilarity, baseNameSimilarity),
                        Math.max(wordSimilarity, commonWordSimilarity)
                );

                maxSimilarity = Math.max(maxSimilarity, itemSimilarity);
            }
            return maxSimilarity;
        }
    }
}