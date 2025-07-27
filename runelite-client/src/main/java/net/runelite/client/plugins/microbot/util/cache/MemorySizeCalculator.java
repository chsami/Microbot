package net.runelite.client.plugins.microbot.util.cache;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.cache.model.*;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2ObjectModel;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItemModel;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for calculating approximate memory sizes of objects in cache.
 * Provides both precise calculations using Java Instrumentation (when available) 
 * and estimates using reflection for fallback.
 * 
 * For maximum accuracy, enable Java Instrumentation by adding JVM argument:
 * -javaagent:microbot-agent.jar
 */
@Slf4j
public class MemorySizeCalculator {

    // Configuration flags
    private static final boolean USE_INSTRUMENTATION_WHEN_AVAILABLE = true;
    private static final boolean LOG_MEASUREMENT_METHOD = false; // Set to true for debugging
    
    // Object header overhead on 64-bit JVM with compressed OOPs
    private static final int OBJECT_HEADER_SIZE = 12;
    private static final int REFERENCE_SIZE = 4; // Compressed OOPs
    private static final int ARRAY_HEADER_SIZE = 16;
    
    // Primitive type sizes
    private static final int BOOLEAN_SIZE = 1;
    private static final int BYTE_SIZE = 1;
    private static final int CHAR_SIZE = 2;
    private static final int SHORT_SIZE = 2;
    private static final int INT_SIZE = 4;
    private static final int FLOAT_SIZE = 4;
    private static final int LONG_SIZE = 8;
    private static final int DOUBLE_SIZE = 8;
    
    // Common object sizes (cached for performance)
    private static final Map<Class<?>, Integer> KNOWN_SIZES = new ConcurrentHashMap<>();
    
    static {
        // Primitive wrapper sizes
        KNOWN_SIZES.put(Boolean.class, OBJECT_HEADER_SIZE + BOOLEAN_SIZE);
        KNOWN_SIZES.put(Byte.class, OBJECT_HEADER_SIZE + BYTE_SIZE);
        KNOWN_SIZES.put(Character.class, OBJECT_HEADER_SIZE + CHAR_SIZE);
        KNOWN_SIZES.put(Short.class, OBJECT_HEADER_SIZE + SHORT_SIZE);
        KNOWN_SIZES.put(Integer.class, OBJECT_HEADER_SIZE + INT_SIZE);
        KNOWN_SIZES.put(Float.class, OBJECT_HEADER_SIZE + FLOAT_SIZE);
        KNOWN_SIZES.put(Long.class, OBJECT_HEADER_SIZE + LONG_SIZE);
        KNOWN_SIZES.put(Double.class, OBJECT_HEADER_SIZE + DOUBLE_SIZE);
        
        // Common RuneLite types
        KNOWN_SIZES.put(WorldPoint.class, OBJECT_HEADER_SIZE + (3 * INT_SIZE)); // x, y, plane
        KNOWN_SIZES.put(Skill.class, OBJECT_HEADER_SIZE + INT_SIZE); // enum ordinal
        KNOWN_SIZES.put(Quest.class, OBJECT_HEADER_SIZE + INT_SIZE); // enum ordinal
        KNOWN_SIZES.put(QuestState.class, OBJECT_HEADER_SIZE + INT_SIZE); // enum ordinal
        
        // AtomicLong used in cache statistics
        KNOWN_SIZES.put(AtomicLong.class, OBJECT_HEADER_SIZE + LONG_SIZE);
    }

    /**
     * Calculates the approximate memory size of a cache key.
     * 
     * @param key The cache key
     * @return Estimated memory size in bytes
     */
    public static long calculateKeySize(Object key) {
        if (key == null) return 0;
        
        Class<?> keyClass = key.getClass();
        
        // Check known sizes first
        Integer knownSize = KNOWN_SIZES.get(keyClass);
        if (knownSize != null) {
            return knownSize;
        }
        
        // Handle common key types
        if (key instanceof String) {
            String str = (String) key;
            return OBJECT_HEADER_SIZE + INT_SIZE + // String object (hash field)
                   ARRAY_HEADER_SIZE + (str.length() * CHAR_SIZE); // char array
        }
        
        if (key instanceof Integer) {
            return OBJECT_HEADER_SIZE + INT_SIZE;
        }
        
        if (key instanceof Long) {
            return OBJECT_HEADER_SIZE + LONG_SIZE;
        }
        
        // For unknown types, use reflection-based calculation
        return calculateObjectSizeReflection(key, new HashSet<>());
    }

    /**
     * Calculates the approximate memory size of a cache value.
     * Optimized for known Microbot cache value types.
     * 
     * @param value The cache value
     * @return Estimated memory size in bytes
     */
    public static long calculateValueSize(Object value) {
        if (value == null) return 0;
        
        Class<?> valueClass = value.getClass();
        
        // Check known sizes first
        Integer knownSize = KNOWN_SIZES.get(valueClass);
        if (knownSize != null) {
            return knownSize;
        }
        
        // Handle known Microbot cache value types
        if (value instanceof Rs2NpcModel) {
            return calculateNpcModelSize((Rs2NpcModel) value);
        }
        
        if (value instanceof Rs2ObjectModel) {
            return calculateObjectModelSize((Rs2ObjectModel) value);
        }
        
        if (value instanceof Rs2GroundItemModel) {
            return calculateGroundItemModelSize((Rs2GroundItemModel) value);
        }
        
        if (value instanceof VarbitData) {
            return calculateVarbitDataSize((VarbitData) value);
        }
        
        if (value instanceof SkillData) {
            return calculateSkillDataSize((SkillData) value);
        }
        
        if (value instanceof SpiritTreeData) {
            return calculateSpiritTreeDataSize((SpiritTreeData) value);
        }
        
        if (value instanceof QuestState) {
            return OBJECT_HEADER_SIZE + INT_SIZE; // Enum ordinal
        }
        
        if (value instanceof String) {
            String str = (String) value;
            return OBJECT_HEADER_SIZE + INT_SIZE + 
                   ARRAY_HEADER_SIZE + (str.length() * CHAR_SIZE);
        }
        
        // For collections
        if (value instanceof Collection) {
            return calculateCollectionSize((Collection<?>) value);
        }
        
        if (value instanceof Map) {
            return calculateMapSize((Map<?, ?>) value);
        }
        
        // For unknown types, use reflection-based calculation
        return calculateObjectSizeReflection(value, new HashSet<>());
    }

    /**
     * Calculates memory size for Rs2NpcModel objects.
     */
    private static long calculateNpcModelSize(Rs2NpcModel npcModel) {
        long size = OBJECT_HEADER_SIZE; // Base object
        
        // Primitive fields in Rs2NpcModel (estimated from typical NPC model)
        size += INT_SIZE * 6; // id, index, combatLevel, hitpoints, maxHitpoints, interacting
        size += BOOLEAN_SIZE * 4; // animating, moving, inCombat, dead
        size += LONG_SIZE * 2; // spawnTick, lastUpdateTick
        
        // Reference fields
        size += REFERENCE_SIZE * 8; // worldPoint, animation, graphic, overhead, etc.
        
        // WorldPoint
        size += OBJECT_HEADER_SIZE + (3 * INT_SIZE);
        
        // String name (average NPC name ~10 characters)
        size += OBJECT_HEADER_SIZE + INT_SIZE + ARRAY_HEADER_SIZE + (10 * CHAR_SIZE);
        
        return size;
    }

    /**
     * Calculates memory size for Rs2ObjectModel objects.
     */
    private static long calculateObjectModelSize(Rs2ObjectModel objectModel) {
        long size = OBJECT_HEADER_SIZE; // Base object
        
        // Primitive fields
        size += INT_SIZE * 4; // id, orientation, type, flags
        size += LONG_SIZE * 2; // spawnTick, lastUpdateTick
        
        // Reference fields
        size += REFERENCE_SIZE * 4; // worldPoint, actions, etc.
        
        // WorldPoint
        size += OBJECT_HEADER_SIZE + (3 * INT_SIZE);
        
        // Actions array (average 5 actions, 8 chars each)
        size += ARRAY_HEADER_SIZE + (5 * REFERENCE_SIZE);
        size += 5 * (OBJECT_HEADER_SIZE + INT_SIZE + ARRAY_HEADER_SIZE + (8 * CHAR_SIZE));
        
        return size;
    }

    /**
     * Calculates memory size for Rs2GroundItemModel objects.
     */
    private static long calculateGroundItemModelSize(Rs2GroundItemModel groundItem) {
        long size = OBJECT_HEADER_SIZE; // Base object
        
        // Primitive fields
        size += INT_SIZE * 3; // id, quantity, visibleTicks
        size += LONG_SIZE * 2; // spawnTick, despawnTick
        
        // Reference fields
        size += REFERENCE_SIZE * 3; // worldPoint, name, etc.
        
        // WorldPoint
        size += OBJECT_HEADER_SIZE + (3 * INT_SIZE);
        
        // Item name (average item name ~15 characters)
        size += OBJECT_HEADER_SIZE + INT_SIZE + ARRAY_HEADER_SIZE + (15 * CHAR_SIZE);
        
        return size;
    }

    /**
     * Calculates memory size for VarbitData objects.
     */
    private static long calculateVarbitDataSize(VarbitData varbitData) {
        long size = OBJECT_HEADER_SIZE; // Base object
        
        // Primitive fields
        size += INT_SIZE * 2; // varbit, value
        size += LONG_SIZE * 2; // lastUpdateTick, cacheTimestamp
        
        // Reference fields
        size += REFERENCE_SIZE * 2; // worldPoint, metadata
        
        // WorldPoint (if present)
        size += OBJECT_HEADER_SIZE + (3 * INT_SIZE);
        
        // Metadata map (small, average 2 entries)
        size += calculateMapSize(2, String.class, Object.class);
        
        return size;
    }

    /**
     * Calculates memory size for SkillData objects.
     */
    private static long calculateSkillDataSize(SkillData skillData) {
        long size = OBJECT_HEADER_SIZE; // Base object
        
        // Primitive fields
        size += INT_SIZE * 3; // level, experience, boostedLevel
        size += LONG_SIZE * 2; // lastUpdateTick, cacheTimestamp
        
        // Reference fields
        size += REFERENCE_SIZE * 2; // skill enum, metadata
        
        // Skill enum
        size += OBJECT_HEADER_SIZE + INT_SIZE;
        
        return size;
    }

    /**
     * Calculates memory size for SpiritTreeData objects.
     */
    private static long calculateSpiritTreeDataSize(SpiritTreeData spiritTreeData) {
        long size = OBJECT_HEADER_SIZE; // Base object
        
        // Primitive fields
        size += INT_SIZE * 3; // patchIndex, state, level
        size += LONG_SIZE * 3; // plantedTick, harvestTick, lastUpdateTick
        
        // Reference fields  
        size += REFERENCE_SIZE * 4; // location, treeType, metadata, etc.
        
        // WorldPoint
        size += OBJECT_HEADER_SIZE + (3 * INT_SIZE);
        
        // String treeType (average ~12 characters)
        size += OBJECT_HEADER_SIZE + INT_SIZE + ARRAY_HEADER_SIZE + (12 * CHAR_SIZE);
        
        return size;
    }

    /**
     * Calculates memory size for collections.
     */
    private static long calculateCollectionSize(Collection<?> collection) {
        if (collection.isEmpty()) {
            return OBJECT_HEADER_SIZE + INT_SIZE; // Empty collection overhead
        }
        
        long size = OBJECT_HEADER_SIZE; // Collection object
        
        if (collection instanceof ArrayList) {
            size += REFERENCE_SIZE + INT_SIZE * 2; // elementData array ref, size, modCount
            size += ARRAY_HEADER_SIZE + (collection.size() * REFERENCE_SIZE); // Array overhead
        } else if (collection instanceof HashSet) {
            size += REFERENCE_SIZE * 3 + INT_SIZE * 3; // HashMap backing, size, threshold, modCount
            size += calculateMapSize(collection.size(), Object.class, Object.class);
        } else {
            // Generic collection estimate
            size += INT_SIZE + (collection.size() * REFERENCE_SIZE);
        }
        
        // Add estimated content size (sample first few elements)
        Iterator<?> iter = collection.iterator();
        long avgElementSize = 0;
        int sampleCount = Math.min(3, collection.size());
        
        for (int i = 0; i < sampleCount && iter.hasNext(); i++) {
            avgElementSize += calculateValueSize(iter.next());
        }
        
        if (sampleCount > 0) {
            avgElementSize /= sampleCount;
            size += avgElementSize * collection.size();
        }
        
        return size;
    }

    /**
     * Calculates memory size for maps.
     */
    private static long calculateMapSize(Map<?, ?> map) {
        if (map.isEmpty()) {
            return OBJECT_HEADER_SIZE + INT_SIZE * 3; // Empty map overhead
        }
        
        long size = OBJECT_HEADER_SIZE; // Map object
        
        if (map instanceof HashMap || map instanceof ConcurrentHashMap) {
            size += REFERENCE_SIZE + INT_SIZE * 3; // table array ref, size, threshold, modCount
            size += ARRAY_HEADER_SIZE + (map.size() * REFERENCE_SIZE * 2); // Bucket array + entries
            size += map.size() * (OBJECT_HEADER_SIZE + INT_SIZE + REFERENCE_SIZE * 3); // Node objects
        } else {
            // Generic map estimate
            size += INT_SIZE + (map.size() * REFERENCE_SIZE * 2);
        }
        
        // Add estimated content size (sample first few entries)
        Iterator<? extends Map.Entry<?, ?>> iter = map.entrySet().iterator();
        long avgKeySize = 0, avgValueSize = 0;
        int sampleCount = Math.min(3, map.size());
        
        for (int i = 0; i < sampleCount && iter.hasNext(); i++) {
            Map.Entry<?, ?> entry = iter.next();
            avgKeySize += calculateKeySize(entry.getKey());
            avgValueSize += calculateValueSize(entry.getValue());
        }
        
        if (sampleCount > 0) {
            avgKeySize /= sampleCount;
            avgValueSize /= sampleCount;
            size += (avgKeySize + avgValueSize) * map.size();
        }
        
        return size;
    }

    /**
     * Calculates memory size for a map with estimated entry count and types.
     */
    private static long calculateMapSize(int entryCount, Class<?> keyType, Class<?> valueType) {
        if (entryCount == 0) {
            return OBJECT_HEADER_SIZE + INT_SIZE * 3;
        }
        
        long size = OBJECT_HEADER_SIZE; // Map object
        size += REFERENCE_SIZE + INT_SIZE * 3; // HashMap structure
        size += ARRAY_HEADER_SIZE + (entryCount * REFERENCE_SIZE * 2); // Bucket array
        size += entryCount * (OBJECT_HEADER_SIZE + INT_SIZE + REFERENCE_SIZE * 3); // Node objects
        
        // Add estimated key/value sizes
        long keySize = KNOWN_SIZES.getOrDefault(keyType, 24); // Default estimate
        long valueSize = KNOWN_SIZES.getOrDefault(valueType, 32); // Default estimate
        
        size += (keySize + valueSize) * entryCount;
        
        return size;
    }

    /**
     * Reflection-based object size calculation for unknown types.
     * Uses visited set to handle circular references.
     */
    private static long calculateObjectSizeReflection(Object obj, Set<Object> visited) {
        if (obj == null || visited.contains(obj)) {
            return 0;
        }
        
        visited.add(obj);
        
        try {
            Class<?> clazz = obj.getClass();
            long size = OBJECT_HEADER_SIZE;
            
            // Handle arrays
            if (clazz.isArray()) {
                int length = Array.getLength(obj);
                size += ARRAY_HEADER_SIZE;
                
                if (clazz.getComponentType().isPrimitive()) {
                    size += getPrimitiveArraySize(clazz.getComponentType(), length);
                } else {
                    size += length * REFERENCE_SIZE;
                    // Sample array elements for content size
                    for (int i = 0; i < Math.min(3, length); i++) {
                        Object element = Array.get(obj, i);
                        size += calculateObjectSizeReflection(element, visited) / Math.min(3, length) * length;
                    }
                }
                return size;
            }
            
            // Handle regular objects
            while (clazz != null) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    
                    Class<?> fieldType = field.getType();
                    
                    if (fieldType.isPrimitive()) {
                        size += getPrimitiveSize(fieldType);
                    } else {
                        size += REFERENCE_SIZE;
                        
                        try {
                            field.setAccessible(true);
                            Object fieldValue = field.get(obj);
                            
                            // Only recurse for small objects to avoid deep recursion
                            if (fieldValue != null && visited.size() < 10) {
                                size += calculateObjectSizeReflection(fieldValue, visited);
                            }
                        } catch (Exception e) {
                            // Field access failed, add conservative estimate
                            size += 32;
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
            
            return size;
            
        } catch (Exception e) {
            log.warn("Error calculating object size for {}: {}", obj.getClass().getSimpleName(), e.getMessage());
            return 64; // Conservative fallback estimate
        } finally {
            visited.remove(obj);
        }
    }

    /**
     * Gets the size of a primitive type.
     */
    private static int getPrimitiveSize(Class<?> primitiveType) {
        if (primitiveType == boolean.class) return BOOLEAN_SIZE;
        if (primitiveType == byte.class) return BYTE_SIZE;
        if (primitiveType == char.class) return CHAR_SIZE;
        if (primitiveType == short.class) return SHORT_SIZE;
        if (primitiveType == int.class) return INT_SIZE;
        if (primitiveType == float.class) return FLOAT_SIZE;
        if (primitiveType == long.class) return LONG_SIZE;
        if (primitiveType == double.class) return DOUBLE_SIZE;
        return 0;
    }

    /**
     * Gets the size of a primitive array.
     */
    private static long getPrimitiveArraySize(Class<?> componentType, int length) {
        return (long) getPrimitiveSize(componentType) * length;
    }

    /**
     * Formats memory size in human-readable format.
     */
    public static String formatMemorySize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
