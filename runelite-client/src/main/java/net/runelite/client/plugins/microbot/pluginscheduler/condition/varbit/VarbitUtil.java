package net.runelite.client.plugins.microbot.pluginscheduler.condition.varbit;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for game variables (Varbits and VarPlayers)
 */
@Slf4j
public class VarbitUtil {
    // Cache of constant names and values
    private static Map<Integer, String> varbitConstantMap = null;
    private static Map<Integer, String> varPlayerConstantMap = null;
    
    // Categories for organizing varbits
    private static final Map<String, List<VarEntry>> varbitCategories = new HashMap<>();
    private static final String[] CATEGORY_NAMES = {
        "Quests",
        "Skills",
        "Minigames",
        "Bosses",
        "Diaries",
        "Combat Achievements",
        "Features",
        "Items",
        "Other"
    };

    /**
     * Initializes the constant maps for Varbits and VarPlayer if not already initialized
     */
    public static synchronized void initConstantMaps() {
        if (varbitConstantMap == null) {
            varbitConstantMap = new HashMap<>();
            for (Field field : VarbitID.class.getDeclaredFields()) {
                if (field.getType() == int.class && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    try {
                        int value = field.getInt(null);
                        varbitConstantMap.put(value, field.getName());
                    } catch (IllegalAccessException e) {
                        log.error("Error accessing field", e);
                    }
                }
            }
        }
        
        if (varPlayerConstantMap == null) {
            varPlayerConstantMap = new HashMap<>();
                    
            // Process VarPlayerID class
            for (Field field : VarPlayerID.class.getDeclaredFields()) {
                if (field.getType() == int.class && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    try {
                        int value = field.getInt(null);
                        varPlayerConstantMap.put(value, field.getName());
                    } catch (IllegalAccessException e) {
                        log.error("Error accessing field", e);
                    }
                }
            }
        }
    }
    
    /**
     * Gets the constant name for a given varbit/varplayer ID
     */
    public static String getConstantNameForId(boolean isVarbit, int id) {
        initConstantMaps();
        Map<Integer, String> map = isVarbit ? varbitConstantMap : varPlayerConstantMap;
        return map.get(id);
    }
    
    /**
     * Helper class to hold a varbit/varplayer entry
     */
    public static class VarEntry {
        public final int id;
        public final String name;
        
        public VarEntry(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
    
    /**
     * Gets a list of predefined Varbit options from the Varbits enum
     * 
     * @return List of formatted Varbit options
     */
    public static List<String> getVarbitOptions() {
        List<String> options = new ArrayList<>();
        options.add("Select Varbit");
        
        try {
            // Initialize constant maps if needed
            initConstantMaps();
            
            // Add all varbit options
            for (Map.Entry<Integer, String> entry : varbitConstantMap.entrySet()) {
                int id = entry.getKey();
                String name = formatConstantName(entry.getValue());
                
                // Add formatted option: "Name (ID)"
                options.add(name + " (" + id + ")");
            }
            
            // Sort options alphabetically after the first "Select Varbit" item
            if (options.size() > 1) {
                List<String> sortedOptions = new ArrayList<>(options.subList(1, options.size()));
                Collections.sort(sortedOptions);
                options = new ArrayList<>();
                options.add("Select Varbit");
                options.addAll(sortedOptions);
            }
            
        } catch (Exception e) {
            log.error("Error getting Varbit options", e);
        }
        
        return options;
    }
    
    /**
     * Formats a constant name for better readability
     * 
     * @param name The raw constant name
     * @return Formatted name
     */
    public static String formatConstantName(String name) {
        name = name.replace('_', ' ').toLowerCase();
        
        // Capitalize words for better readability
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                sb.append(c);
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        
        return sb.toString();
    }

    /**
     * Organizes varbits into meaningful categories
     */
    public static void initializeVarbitCategories() {
        // Initialize the varbit constant map if needed
        initConstantMaps();
        
        // Create category lists
        for (String category : CATEGORY_NAMES) {
            varbitCategories.put(category, new ArrayList<>());
        }
        
        // Populate categories based on name matching
        for (Map.Entry<Integer, String> entry : varbitConstantMap.entrySet()) {
            int id = entry.getKey();
            String name = entry.getValue();
            
            String formattedName = formatConstantName(name);
            String lowerName = name.toLowerCase();
            VarEntry varEntry = new VarEntry(id, formattedName);
            
            // First handle COLLECTION prefixed varbits as they have clear categorization
            if (name.contains("COLLECTION")) {
                if (name.contains("_BOSSES_") || name.contains("_RAIDS_")) {
                    varbitCategories.get("Bosses").add(varEntry);
                    continue;
                } else if (name.contains("_MINIGAMES_")) {
                    varbitCategories.get("Minigames").add(varEntry);
                    continue;
                }
            }
            
            // Check for specific other collections that might fit in our categories
            if (name.contains("SLAYER_") && (name.contains("_TASKS_COMPLETED") || name.contains("_POINTS"))) {
                varbitCategories.get("Skills").add(varEntry);
                continue;
            } 
            if (name.contains("CA_") && (name.contains("_TOTAL_TASKS"))) {
                varbitCategories.get("Combat Achievements").add(varEntry);
                continue;
            } 
            
            // Achievement name
            if (name.contains("_DIARY_") && name.contains("_COMPLETE")) {
                varbitCategories.get("Diaries").add(varEntry);
                continue;
            }
        }
    }
    
    /**
     * Gets the category names
     * 
     * @return Array of category names
     */
    public static String[] getCategoryNames() {
        return CATEGORY_NAMES;
    }
    
    /**
     * Gets the varbit entries for a specific category
     * 
     * @param category The category name
     * @return List of VarEntry objects for the category
     */
    public static List<VarEntry> getVarbitEntriesByCategory(String category) {
        // Initialize categories if needed
        if (varbitCategories.isEmpty()) {
            initializeVarbitCategories();
        }
        
        return varbitCategories.getOrDefault(category, new ArrayList<>());
    }
    
    /**
     * Gets all varbit entries
     * 
     * @return Map of all varbit entries
     */
    public static Map<Integer, String> getAllVarbitEntries() {
        initConstantMaps();
        return new HashMap<>(varbitConstantMap);
    }
    
    /**
     * Gets all varplayer entries
     * 
     * @return Map of all varplayer entries
     */
    public static Map<Integer, String> getAllVarPlayerEntries() {
        initConstantMaps();
        return new HashMap<>(varPlayerConstantMap);
    }
}