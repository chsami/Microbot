package net.runelite.client.plugins.microbot.pluginscheduler.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for filtering and categorizing plugins based on their descriptors and metadata.
 * Dynamically extracts creator information and tags without hardcoding.
 */
@Slf4j
public class PluginFilterUtil {
    
    // Primary filter categories
    public static final String FILTER_ALL = "Show All";
    public static final String FILTER_INTERNAL = "Internal Plugins";
    public static final String FILTER_EXTERNAL = "External Plugins";
    public static final String FILTER_BY_CREATOR = "By Creator";
    public static final String FILTER_BY_TAGS = "By Tags";
    
    // Pattern to extract creator prefixes from plugin names
    private static final Pattern CREATOR_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");
    
    // OSRS Skill and Activity Groups
    private static final Map<String, Set<String>> TAG_GROUPS = createTagGroups();
    
    // Cache for creator constants to avoid repeated reflection
    private static Set<String> creatorConstantsCache = null;
    
    /**
     * Creates the tag groups map using Java 8 compatible syntax
     */
    private static Map<String, Set<String>> createTagGroups() {
        Map<String, Set<String>> groups = new HashMap<>();
        
        // Combat Skills
        groups.put("Combat skills", new HashSet<>(Arrays.asList("combat","combat training",  "attack", "strength", "defence", "hitpoints", "prayer", "magic", "ranged")));
        
        // Gathering Skills
        groups.put("Gathering Skills", new HashSet<>(Arrays.asList("farming", "fishing", "hunter","mininig", "woodcutting")));
        
        // Artisan Skills
        groups.put("Artisan Skills", new HashSet<>(Arrays.asList("cooking", "crafting", "fletching", "herblore", "runecrafting","runecraft", "smithing")));
        
        // Support Skills
        groups.put("Utility Skills", new HashSet<>(Arrays.asList("agility","construction", "firemaking", "thieving",  "slayer")));
        
        // PvM/Bossing
        groups.put("PvM/Bossing", new HashSet<>(Arrays.asList("pvm", "boss", "bossing", "raids", "tob", "cox", "cm", "chambers", "theatre", 
                                        "zulrah", "vorkath", "hydra", "cerberus", "kraken", "thermonuclear", "barrows", 
                                        "jad", "inferno", "gauntlet", "corrupted gauntlet", "nightmare", "phosani", "nex", "bandos", 
                                        "armadyl", "zamorak", "saradomin", "dagannoth", "kalphite", "kq", "kbd", "chaos fanatic", 
                                        "archaeologist", "spider", "bear", "scorpia", "vetion", "callisto", 
                                        "venenatis", "rev", "revenant killer")));
        
        // Money Making
        groups.put("Money Making", new HashSet<>(Arrays.asList("money making", "mm", "gp per hour", "gold", "profit", "farming ", "merching", 
                                               "flipping", "moneymaking")));
        
        // Minigames
        groups.put("Minigames", new HashSet<>(Arrays.asList("minigame", "minigames", "wintertodt", "tempoross", "zalcano", "gotr", "guardians rift",
                                     "tithe", "tithe farm", "mahogany homes", "pest control", "barbarian assault", 
                                     "castle wars", "fight caves", "duel arena", "last man standing", "lms")));
                
        // Questing & Achievement
        groups.put("Quests & Achievement", new HashSet<>(Arrays.asList("quest", "questing", "achievement", "diary", "clue", "clues", "treasure", 
                                                "trails", "casket", "scroll", "beginner", "easy", "medium", "hard", "elite", "master")));
        
        // Utility & QoL
        groups.put("Utility", new HashSet<>(Arrays.asList("utility", "qol", "quality", "life", "helper", "calculator", "timer", "notification", 
                                   "overlay", "highlight", "marker", "tracker", "counter", "solver", "automation")));
        
        // PvP
        groups.put("PvP", new HashSet<>(Arrays.asList("pvp", "player versus player", "pk", "pking", "bounty hunter", "edge", "edgeville", "anti-pk")));
        
        // Bank & Trading
        groups.put("Banking & Trading", new HashSet<>(Arrays.asList("bank", "banking", "ge", "grand", "exchange", "trade", "trading",
                                 "merchant", "merch", "flip", "flipping", "sorter", "organization","muling")));
        
        // Transportation
        groups.put("Transportation", new HashSet<>(Arrays.asList("teleport", "transport", "fairy", "ring", "spirit", "tree", "home", "tab", 
                                          "house", "poh", "portal")));
        
        return groups;
    }
    
    /**
     * Gets all available primary filter categories
     */
    public static List<String> getPrimaryFilterCategories() {
        return Arrays.asList(FILTER_ALL, FILTER_INTERNAL, FILTER_EXTERNAL, FILTER_BY_CREATOR, FILTER_BY_TAGS);
    }
    
    /**
     * Gets secondary filter options based on the primary filter selection
     */
    public static List<String> getSecondaryFilterOptions(String primaryFilter, List<Plugin> plugins) {
        List<String> options = new ArrayList<>();
        
        switch (primaryFilter) {
            case FILTER_BY_CREATOR:
                options = getAvailableCreators(plugins);
                break;
            case FILTER_BY_TAGS:
                options = getAvailableTagGroups(plugins);
                break;
            case FILTER_INTERNAL:
            case FILTER_EXTERNAL:
                options.add("All");
                break;
            case FILTER_ALL:
            default:
                options.add("All Plugins");
                break;
        }
        
        return options.stream().sorted().collect(Collectors.toList());
    }
    
    /**
     * Filters plugins based on the selected primary and secondary filters
     */
    public static List<Plugin> filterPlugins(List<Plugin> plugins, String primaryFilter, String secondaryFilter) {
        return plugins.stream()
                .filter(plugin -> plugin instanceof SchedulablePlugin)
                .filter(plugin -> matchesFilter(plugin, primaryFilter, secondaryFilter))
                .collect(Collectors.toList());
    }
    
    /**
     * Checks if a plugin matches the given filters
     */
    private static boolean matchesFilter(Plugin plugin, String primaryFilter, String secondaryFilter) {
        if (FILTER_ALL.equals(primaryFilter) || secondaryFilter == null || "All Plugins".equals(secondaryFilter)) {
            return true;
        }
        
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        if (descriptor == null) {
            return false;
        }
        
        switch (primaryFilter) {
            case FILTER_BY_CREATOR:
                return matchesCreator(descriptor, secondaryFilter);
            case FILTER_BY_TAGS:
                return matchesTagGroup(descriptor, secondaryFilter);
            case FILTER_INTERNAL:
                return !descriptor.isExternal();
            case FILTER_EXTERNAL:
                return descriptor.isExternal();
            default:
                return true;
        }
    }
    
    /**
     * Checks if a plugin matches the selected creator by extracting creator from plugin name
     */
    private static boolean matchesCreator(PluginDescriptor descriptor, String selectedCreator) {
        String pluginCreator = extractCreatorFromName(descriptor.name());
        return pluginCreator.equals(selectedCreator);
    }
    
    /**
     * Checks if a plugin matches the selected tag group
     */
    private static boolean matchesTagGroup(PluginDescriptor descriptor, String selectedTagGroup) {
        String[] tags = descriptor.tags();
        Set<String> groupTags = TAG_GROUPS.get(selectedTagGroup);
        
        if (groupTags == null) {
            return false;
        }
        
        return Arrays.stream(tags)
                .filter(tag -> !isCreatorTag(tag)) // Exclude creator tags
                .anyMatch(tag -> matchesAnyGroupTag(tag.toLowerCase(), groupTags));
    }
    
    /**
     * Checks if a tag matches any of the group tags, including partial matches for multi-word phrases
     */
    private static boolean matchesAnyGroupTag(String tag, Set<String> groupTags) {
        // Direct match
        if (groupTags.contains(tag)) {
            return true;
        }
        
        // Check if tag contains any of the group tag words
        for (String groupTag : groupTags) {
            if (tag.contains(groupTag) || groupTag.contains(tag)) {
                return true;
            }
        }
        
        // Split tag on spaces and check each word
        String[] tagWords = tag.split("\\s+");
        for (String word : tagWords) {
            if (groupTags.contains(word.trim())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if a tag is likely a creator tag by checking against known creator constants
     */
    private static boolean isCreatorTag(String tag) {
        if (tag == null) {
            return false;
        }
        
        Set<String> creators = getCreatorConstants();
        // Check against variable names (case-sensitive)
        if (creators.contains(tag)) {
            return true;
        }
        
        // Check against variations (case-insensitive) for short tags
        if (tag.length() <= 6) {
            return creators.contains(tag.toUpperCase()) || creators.contains(tag.toLowerCase());
        }
        
        return false;
    }
    
    /**
     * Gets all available creators from the plugin list by analyzing plugin names and PluginDescriptor constants
     */
    private static List<String> getAvailableCreators(List<Plugin> plugins) {
        Set<String> creators = new HashSet<>();
        
        // Extract creators from plugin names
        for (Plugin plugin : plugins) {
            if (!(plugin instanceof SchedulablePlugin)) {
                continue;
            }
            
            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            if (descriptor != null) {
                String creator = extractCreatorFromName(descriptor.name());
                if (!"Unknown".equals(creator)) { // Filter out "Unknown"
                    creators.add(creator);
                }
            }
        }
        
        return creators.stream()
                .filter(creator -> !"Unknown".equals(creator))
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * Gets available tag groups that have matching plugins
     */
    private static List<String> getAvailableTagGroups(List<Plugin> plugins) {
        Set<String> availableGroups = new HashSet<>();
        
        for (Plugin plugin : plugins) {
            if (!(plugin instanceof SchedulablePlugin)) {
                continue;
            }
            
            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            if (descriptor != null) {
                String[] tags = descriptor.tags();
                
                // Check which tag groups this plugin belongs to
                for (Map.Entry<String, Set<String>> groupEntry : TAG_GROUPS.entrySet()) {
                    String groupName = groupEntry.getKey();
                    Set<String> groupTags = groupEntry.getValue();
                    
                    boolean hasMatchingTag = Arrays.stream(tags)
                            .filter(tag -> !isCreatorTag(tag)) // Exclude creator tags
                            .anyMatch(tag -> groupTags.contains(tag.toLowerCase()));
                            
                    if (hasMatchingTag) {
                        availableGroups.add(groupName);
                    }
                }
            }
        }
        
        return new ArrayList<>(availableGroups);
    }
    
    /**
     * Gets creator constants with caching
     */
    private static Set<String> getCreatorConstants() {
        if (creatorConstantsCache == null) {
            creatorConstantsCache = extractCreatorConstantsFromPluginDescriptor();
        }
        return creatorConstantsCache;
    }
    
    /**
     * Dynamically extracts creator constants from PluginDescriptor class using reflection
     */
    private static Set<String> extractCreatorConstantsFromPluginDescriptor() {
        Set<String> creators = new HashSet<>();
        
        try {
            Field[] fields = PluginDescriptor.class.getDeclaredFields();
            for (Field field : fields) {
                if (field.getType() == String.class && 
                    java.lang.reflect.Modifier.isStatic(field.getModifiers()) &&
                    java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                    
                    try {
                        String value = (String) field.get(null);
                        if (value != null && value.contains("[") && value.contains("]")) {
                            // Use the field name as the creator identifier
                            String fieldName = field.getName();
                            if (!fieldName.isEmpty()) {
                                creators.add(fieldName);
                            }
                        }
                    } catch (IllegalAccessException e) {
                        log.debug("Could not access field {}: {}", field.getName(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting creator constants from PluginDescriptor: {}", e.getMessage());
        }
        
        return creators;
    }
    
    /**
     * Extracts creator name from PluginDescriptor constant value
     */
    private static String extractCreatorFromValue(String value) {
        // Pattern to match content between > and < in HTML format
        Pattern pattern = Pattern.compile(">([^<]+)<");
        Matcher matcher = pattern.matcher(value);
        
        if (matcher.find()) {
            String extracted = matcher.group(1);
            // Handle special characters and emoji
            if (extracted.length() <= 4) { // Most creator codes are short
                return extracted;
            }
        }
        
        // Fallback: try to extract from brackets
        Matcher bracketMatcher = CREATOR_PATTERN.matcher(value);
        if (bracketMatcher.find()) {
            return bracketMatcher.group(1);
        }
        
        return "";
    }
    
    /**
     * Extracts creator from plugin name using various patterns
     */
    private static String extractCreatorFromName(String pluginName) {
        if (pluginName == null) {
            return "Unknown";
        }
        
        // Try to find the variable name from PluginDescriptor constants
        String variableName = extractCreatorVariableName(pluginName);
        if (variableName != null && !variableName.equals("Unknown")) {
            return variableName;
        }
        
        // Fallback: First try to extract from [Creator] pattern
        Matcher matcher = CREATOR_PATTERN.matcher(pluginName);
        if (matcher.find()) {
            String creator = cleanHtmlTags(matcher.group(1));
            return creator.isEmpty() ? "Unknown" : creator;
        }
        
        // Check if plugin name starts with HTML format and extract creator
        if (pluginName.startsWith("<html>")) {
            String extracted = extractCreatorFromValue(pluginName);
            if (!extracted.isEmpty()) {
                return cleanHtmlTags(extracted);
            }
        }
        
        return "Unknown";
    }
    
    /**
     * Extracts the variable name from PluginDescriptor by matching the plugin name against constant values
     */
    private static String extractCreatorVariableName(String pluginName) {
        try {
            Field[] fields = PluginDescriptor.class.getDeclaredFields();
            for (Field field : fields) {
                if (field.getType() == String.class && 
                    java.lang.reflect.Modifier.isStatic(field.getModifiers()) &&
                    java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                    
                    try {
                        String value = (String) field.get(null);
                        if (value != null && pluginName.startsWith(value)) {
                            // Found matching constant - return field name
                            return field.getName();
                        }
                    } catch (IllegalAccessException e) {
                        log.debug("Could not access field {}: {}", field.getName(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting creator variable name: {}", e.getMessage());
        }
        
        return "Unknown";
    }
    
    /**
     * Removes HTML tags and cleans up text
     */
    private static String cleanHtmlTags(String text) {
        if (text == null) {
            return "";
        }
        
        // Remove HTML tags
        String cleaned = text.replaceAll("<[^>]*>", "").trim();
        
        // Remove common HTML entities
        cleaned = cleaned.replace("&nbsp;", " ")
                         .replace("&lt;", "<")
                         .replace("&gt;", ">")
                         .replace("&amp;", "&")
                         .replace("&quot;", "\"");
        
        return cleaned.trim();
    }
    
    /**
     * Gets the creator name for a specific plugin
     */
    public static String getPluginCreator(Plugin plugin) {
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        if (descriptor == null) {
            return "Unknown";
        }
        
        String creator = extractCreatorFromName(descriptor.name());
        return "Unknown".equals(creator) ? "Unknown" : creator;
    }
    
    /**
     * Gets the tags for a specific plugin, excluding creator tags
     */
    public static List<String> getPluginTags(Plugin plugin) {
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        if (descriptor == null) {
            return Collections.emptyList();
        }
        
        return Arrays.stream(descriptor.tags())
                .filter(tag -> !isCreatorTag(tag)) // Exclude creator tags
                .collect(Collectors.toList());
    }
    
    /**
     * Gets a clean display name for a plugin without HTML formatting
     */
    public static String getPluginDisplayName(Plugin plugin) {
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        if (descriptor == null) {
            return plugin.getName();
        }
        
        String name = descriptor.name();
        // Remove HTML tags and clean up the name
        return cleanHtmlTags(name);
    }
    
    /**
     * Gets a formatted display name for a plugin including creator prefix in a clean format
     */
    public static String getPluginFormattedDisplayName(Plugin plugin) {
        String creator = getPluginCreator(plugin);
        String cleanName = getPluginDisplayName(plugin);
        
        if ("Unknown".equals(creator)) {
            return cleanName;
        }
        
        return String.format("[%s] %s", creator, cleanName);
    }
    
    /**
     * Gets which tag group a plugin belongs to (if any)
     */
    public static String getPluginTagGroup(Plugin plugin) {
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        if (descriptor == null) {
            return "Other";
        }
        
        String[] tags = descriptor.tags();
        
        // Check which tag group this plugin belongs to
        for (Map.Entry<String, Set<String>> groupEntry : TAG_GROUPS.entrySet()) {
            String groupName = groupEntry.getKey();
            Set<String> groupTags = groupEntry.getValue();
            
            boolean hasMatchingTag = Arrays.stream(tags)
                    .filter(tag -> !isCreatorTag(tag)) // Exclude creator tags
                    .anyMatch(tag -> groupTags.contains(tag.toLowerCase()));
                    
            if (hasMatchingTag) {
                return groupName;
            }
        }
        
        return "Other";
    }
}