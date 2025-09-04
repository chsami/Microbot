package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.OrRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.ConditionalRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.OrderedRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.RunePouchRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.util.ConditionalRequirementBuilder;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

/**
 * Static collection of requirement registration methods for common OSRS equipment sets,
 * outfits, and progression-based tool collections.
 * 
 * This class provides standardized requirement collections that can be registered
 * with PrePostScheduleRequirements instances to ensure consistency across plugins.
 */
public class ItemRequirementCollection {
    
    /**
     * Registers basic mining equipment requirements for the plugin scheduler.
     * This includes various pickaxes with their respective requirements.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     */
    public static void registerPickAxes(PrePostScheduleRequirements requirements, RequirementPriority priority, TaskContext taskContext) {
        // because all are in the same slot, these are or requirements, any of these is important
        requirements.register(new ItemRequirement(
            ItemID.CRYSTAL_PICKAXE, 1,
            EquipmentInventorySlot.WEAPON, -1,//-1 allows to be in inventory
            priority, 10, "Crystal pickaxe (best for mining fragments)",
            taskContext, Skill.MINING, 71, Skill.ATTACK, 70  // Mining level 71 required to equip
        ));
        requirements.register(new ItemRequirement(
            ItemID.DRAGON_PICKAXE, 1,
            EquipmentInventorySlot.WEAPON,-1,
        
            priority, 8, "Dragon pickaxe (excellent for mining fragments)",
            taskContext, Skill.MINING, 61, Skill.ATTACK, 60  // Mining level 61 required to equip
        ));
        requirements.register(new ItemRequirement(
            ItemID.RUNE_PICKAXE, 1,
            EquipmentInventorySlot.WEAPON,-1, priority, 6, "Rune pickaxe (good for mining fragments)",
            taskContext, Skill.MINING, 41, Skill.ATTACK, 40  // Mining level 41 required to equip
        ));
        requirements.register(new ItemRequirement(
            ItemID.ADAMANT_PICKAXE, 1,
            EquipmentInventorySlot.WEAPON,-1, priority, 4, "Adamant pickaxe (adequate for mining fragments)",
            taskContext, Skill.MINING, 31, Skill.ATTACK, 30  // Mining level 31 required to equip
        ));
        requirements.register(new ItemRequirement(
            ItemID.MITHRIL_PICKAXE, 1,
            EquipmentInventorySlot.WEAPON,-1, priority, 4, "Mithril pickaxe (adequate for mining fragments)",
            taskContext, Skill.MINING, 21, Skill.ATTACK, 20  // Mining level 21 required to equip
        ));
        requirements.register(new ItemRequirement(
            ItemID.STEEL_PICKAXE, 1,
            EquipmentInventorySlot.WEAPON,-1, priority, 2, "Steel pickaxe (for mining fragments)",
            taskContext, Skill.MINING, 6, Skill.ATTACK, 10  // Mining level 6 required to equip
        ));
        requirements.register(new ItemRequirement(
            ItemID.IRON_PICKAXE, 1,
            EquipmentInventorySlot.WEAPON,-1, priority, 2, "Iron pickaxe (for mining fragments)",
            taskContext, Skill.MINING, 0, Skill.ATTACK, 0// Mining level 1 required to equip
        ));
        requirements.register(new ItemRequirement(
            ItemID.BRONZE_PICKAXE, 1,
            EquipmentInventorySlot.WEAPON,-1, priority, 1, "Bronze pickaxe (for mining fragments, if no better option available)",
            taskContext
            // No skill requirement for bronze pickaxe - anyone can use it
        ));
    }

    /**
     * Registers progression-based woodcutting axes for the plugin scheduler.
     * This includes all axes from bronze to crystal with their respective requirements.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     */
    public static void registerWoodcuttingAxes(PrePostScheduleRequirements requirements, RequirementPriority priority, TaskContext taskContext, int inventorySlot) {
        requirements.register(new ItemRequirement(
            ItemID._3A_AXE, 1,
            EquipmentInventorySlot.WEAPON, inventorySlot, priority, 10, "3rd age axe (best woodcutting axe available)",
            taskContext, Skill.WOODCUTTING, 65, Skill.ATTACK, 65  // 3rd age axe requirements
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.CRYSTAL_AXE, ItemID.CRYSTAL_AXE_INACTIVE), 1,
            EquipmentInventorySlot.WEAPON, inventorySlot, priority, 9, "Crystal axe (excellent for woodcutting)",
            taskContext, Skill.WOODCUTTING, 71, Skill.ATTACK, 70  // Crystal axe requirements
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.INFERNAL_AXE, ItemID.INFERNAL_AXE_EMPTY), 1,
            EquipmentInventorySlot.WEAPON, inventorySlot, priority, 8, "Infernal axe (burns logs automatically)",
            taskContext, Skill.WOODCUTTING, 61, Skill.ATTACK, 60  // Infernal axe requirements
        ));
        requirements.register(new ItemRequirement(
            ItemID.DRAGON_AXE, 1,
            EquipmentInventorySlot.WEAPON, inventorySlot,
            priority, 8, "Dragon axe (excellent for woodcutting)",
            taskContext, Skill.WOODCUTTING, 61, Skill.ATTACK, 60  // Dragon axe requirements
        ));
        requirements.register(new ItemRequirement(
            ItemID.RUNE_AXE, 1,
            EquipmentInventorySlot.WEAPON, inventorySlot, priority, 6, "Rune axe (good for woodcutting)",
            taskContext, Skill.WOODCUTTING, 41, Skill.ATTACK, 40  // Rune axe requirements
        ));
        requirements.register(new ItemRequirement(
            ItemID.ADAMANT_AXE, 1,
            EquipmentInventorySlot.WEAPON, inventorySlot, priority, 4, "Adamant axe (adequate for woodcutting)",
            taskContext, Skill.WOODCUTTING, 31, Skill.ATTACK, 30  // Adamant axe requirements
        ));
        requirements.register(new ItemRequirement(
            ItemID.MITHRIL_AXE, 1,
            EquipmentInventorySlot.WEAPON, inventorySlot, priority, 4, "Mithril axe (adequate for woodcutting)",
            taskContext, Skill.WOODCUTTING, 21, Skill.ATTACK, 20  // Mithril axe requirements
        ));
        requirements.register(new ItemRequirement(
            ItemID.BLACK_AXE, 1,
            EquipmentInventorySlot.WEAPON, inventorySlot, priority, 3, "Black axe (for woodcutting)",
            taskContext, Skill.WOODCUTTING, 6, Skill.ATTACK, 10  // Black axe requirements
        ));
        requirements.register(new ItemRequirement(
            ItemID.STEEL_AXE, 1,
            EquipmentInventorySlot.WEAPON, inventorySlot, priority, 2, "Steel axe (for woodcutting)",
            taskContext, Skill.WOODCUTTING, 6, Skill.ATTACK, 5  // Steel axe requirements
        ));
        requirements.register(new ItemRequirement(
            ItemID.IRON_AXE, 1,
            EquipmentInventorySlot.WEAPON, inventorySlot, priority, 2, "Iron axe (for woodcutting)",
            taskContext, Skill.WOODCUTTING, 1, Skill.ATTACK, 1  // Iron axe requirements
        ));
        requirements.register(new ItemRequirement(
            ItemID.BRONZE_AXE, 1,
            EquipmentInventorySlot.WEAPON, inventorySlot, priority, 1, "Bronze axe (basic woodcutting axe)",
            taskContext
            // No skill requirement for bronze axe - anyone can use it
        ));
    }

    /**
     * Registers the complete Graceful outfit for the plugin scheduler.
     * Includes all regular graceful pieces plus all Zeah house variants.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the graceful outfit (MANDATORY, RECOMMENDED, or OPTIONAL)
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     */
    public static void registerGracefulOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, TaskContext taskContext,int rating) {
        registerGracefulOutfit(requirements, priority, taskContext, rating, false, false, false, false, false, false);
    }

    /**
     * Registers the complete Graceful outfit for the plugin scheduler with convenience flags.
     * Includes all regular graceful pieces plus all Zeah house variants.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the graceful outfit (MANDATORY, RECOMMENDED, or OPTIONAL)
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param skipHead Skip head slot if true
     * @param skipCape Skip cape slot if true
     * @param skipBody Skip body slot if true
     * @param skipLegs Skip legs slot if true
     * @param skipGloves Skip gloves slot if true
     * @param skipBoots Skip boots slot if true
     */
    public static void registerGracefulOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, TaskContext taskContext, int rating,
                                            boolean skipHead, boolean skipCape, boolean skipBody, 
                                            boolean skipLegs, boolean skipGloves, boolean skipBoots) {        
     
        // Combined Graceful outfit (all variants in one requirement)
        if (!skipHead) {
            requirements.register(ItemRequirement.createOrRequirement(
                Arrays.asList(ItemID.GRACEFUL_HOOD, ItemID.ZEAH_GRACEFUL_HOOD_ARCEUUS, ItemID.ZEAH_GRACEFUL_HOOD_PISCARILIUS, 
                            ItemID.ZEAH_GRACEFUL_HOOD_LOVAKENGJ, ItemID.ZEAH_GRACEFUL_HOOD_SHAYZIEN, 
                            ItemID.ZEAH_GRACEFUL_HOOD_HOSIDIUS, ItemID.ZEAH_GRACEFUL_HOOD_KOUREND), 1,
                EquipmentInventorySlot.HEAD, -2,
                priority, rating, "Graceful hood (weight reduction and run energy restoration)",
                taskContext
            ));
        }
        if (!skipCape) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.GRACEFUL_CAPE, ItemID.ZEAH_GRACEFUL_CAPE_ARCEUUS, ItemID.ZEAH_GRACEFUL_CAPE_PISCARILIUS, 
                            ItemID.ZEAH_GRACEFUL_CAPE_LOVAKENGJ, ItemID.ZEAH_GRACEFUL_CAPE_SHAYZIEN, 
                            ItemID.ZEAH_GRACEFUL_CAPE_HOSIDIUS, ItemID.ZEAH_GRACEFUL_CAPE_KOUREND),1,
                EquipmentInventorySlot.CAPE, -2,
                priority, rating, "Graceful cape (weight reduction and run energy restoration)",
                taskContext
            ));
        }
        if (!skipBody) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.GRACEFUL_TOP, ItemID.ZEAH_GRACEFUL_TOP_ARCEUUS, ItemID.ZEAH_GRACEFUL_TOP_PISCARILIUS, 
                            ItemID.ZEAH_GRACEFUL_TOP_LOVAKENGJ, ItemID.ZEAH_GRACEFUL_TOP_SHAYZIEN, 
                            ItemID.ZEAH_GRACEFUL_TOP_HOSIDIUS, ItemID.ZEAH_GRACEFUL_TOP_KOUREND),1,
                EquipmentInventorySlot.BODY,-2,
                priority, rating, "Graceful top (weight reduction and run energy restoration)",
                taskContext
            ));
        }
        if (!skipLegs) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.GRACEFUL_LEGS, ItemID.ZEAH_GRACEFUL_LEGS_ARCEUUS, ItemID.ZEAH_GRACEFUL_LEGS_PISCARILIUS, 
                            ItemID.ZEAH_GRACEFUL_LEGS_LOVAKENGJ, ItemID.ZEAH_GRACEFUL_LEGS_SHAYZIEN, 
                            ItemID.ZEAH_GRACEFUL_LEGS_HOSIDIUS, ItemID.ZEAH_GRACEFUL_LEGS_KOUREND),1,
                EquipmentInventorySlot.LEGS,-2,
                priority, rating, "Graceful legs (weight reduction and run energy restoration)",
                taskContext
            ));
        }
        if (!skipGloves) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.GRACEFUL_GLOVES, ItemID.ZEAH_GRACEFUL_GLOVES_ARCEUUS, ItemID.ZEAH_GRACEFUL_GLOVES_PISCARILIUS, 
                            ItemID.ZEAH_GRACEFUL_GLOVES_LOVAKENGJ, ItemID.ZEAH_GRACEFUL_GLOVES_SHAYZIEN, 
                            ItemID.ZEAH_GRACEFUL_GLOVES_HOSIDIUS, ItemID.ZEAH_GRACEFUL_GLOVES_KOUREND), 1,
                EquipmentInventorySlot.GLOVES,-2,
                priority, rating, "Graceful gloves (weight reduction and run energy restoration)",
                taskContext
            ));
        }
        if (!skipBoots) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.GRACEFUL_BOOTS, ItemID.ZEAH_GRACEFUL_BOOTS_ARCEUUS, ItemID.ZEAH_GRACEFUL_BOOTS_PISCARILIUS, 
                            ItemID.ZEAH_GRACEFUL_BOOTS_LOVAKENGJ, ItemID.ZEAH_GRACEFUL_BOOTS_SHAYZIEN, 
                            ItemID.ZEAH_GRACEFUL_BOOTS_HOSIDIUS, ItemID.ZEAH_GRACEFUL_BOOTS_KOUREND), 1,
                EquipmentInventorySlot.BOOTS, -2,
                priority, rating, "Graceful boots (weight reduction and run energy restoration)",
                taskContext
            ));
        }
    }

    /**
     * Registers the complete Runecrafting Outfit (Robes of the Eye) for the plugin scheduler.
     * This is the specialized outfit for runecrafting activities.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the runecrafting outfit
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     */
    public static void registerRunecraftingOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, TaskContext taskContext) {
        registerRunecraftingOutfit(requirements, priority, taskContext, false, false, false);
    }

    /**
     * Registers the complete Runecrafting Outfit (Robes of the Eye) for the plugin scheduler with convenience flags.
     * This is the specialized outfit for runecrafting activities.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the runecrafting outfit
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param skipHead Skip head slot if true
     * @param skipBody Skip body slot if true
     * @param skipLegs Skip legs slot if true
     */
    public static void registerRunecraftingOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, TaskContext taskContext,
                                                boolean skipHead, boolean skipBody, boolean skipLegs) {
        // Original (default) color variants - highest priority
        if (!skipHead) {
            requirements.register(new ItemRequirement(
                ItemID.HAT_OF_THE_EYE, 1,
                EquipmentInventorySlot.HEAD,
                 priority, 10, "Hat of the Eye (optimal for runecrafting)",
                taskContext
            ));
        }
        if (!skipBody) {
            requirements.register(new ItemRequirement(
                ItemID.ROBE_TOP_OF_THE_EYE, 1,
                EquipmentInventorySlot.BODY, 
                priority, 10, "Robe top of the Eye (optimal for runecrafting)",
                taskContext
            ));
        }
        if (!skipLegs) {
            requirements.register(new ItemRequirement(
                ItemID.ROBE_BOTTOM_OF_THE_EYE, 1,
                EquipmentInventorySlot.LEGS, 
                priority, 10, "Robe bottoms of the Eye (optimal for runecrafting)",
                taskContext
            ));
        }

        // Colored variants (red, green, blue) - slightly lower priority
        int coloredVariantRating = Math.max(1, priority == RequirementPriority.MANDATORY ? 8 : priority == RequirementPriority.RECOMMENDED ? 6 : 4);
        
        if (!skipHead) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.HAT_OF_THE_EYE_RED, ItemID.HAT_OF_THE_EYE_GREEN, ItemID.HAT_OF_THE_EYE_BLUE), 
                EquipmentInventorySlot.HEAD, 
                priority, coloredVariantRating, "Hat of the Eye (colored variants)",
                taskContext
            ));
        }
        if (!skipBody) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.ROBE_TOP_OF_THE_EYE_RED, ItemID.ROBE_TOP_OF_THE_EYE_GREEN, ItemID.ROBE_TOP_OF_THE_EYE_BLUE), 
                EquipmentInventorySlot.BODY, 
                priority, coloredVariantRating, "Robe top of the Eye (colored variants)",
                taskContext
            ));
        }
        if (!skipLegs) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.ROBE_BOTTOM_OF_THE_EYE_RED, ItemID.ROBE_BOTTOM_OF_THE_EYE_GREEN, ItemID.ROBE_BOTTOM_OF_THE_EYE_BLUE), 
                EquipmentInventorySlot.LEGS, 
                priority, coloredVariantRating, "Robe bottoms of the Eye (colored variants)",
                taskContext
            ));
        }
    }

    /**
     * Registers the complete Lumberjack Outfit for the plugin scheduler.
     * This is the specialized outfit for woodcutting activities.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the lumberjack outfit
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     */
    public static void registerLumberjackOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext) {
        registerLumberjackOutfit(requirements, priority, rating, taskContext, false, false, false, false);
    }

    /**
     * Registers the complete Lumberjack Outfit for the plugin scheduler with convenience flags.
     * This is the specialized outfit for woodcutting activities.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the lumberjack outfit
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param skipHead Skip head slot if true
     * @param skipBody Skip body slot if true
     * @param skipLegs Skip legs slot if true
     * @param skipBoots Skip boots slot if true
     */
    public static void registerLumberjackOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext,
                                              boolean skipHead, boolean skipBody, boolean skipLegs, boolean skipBoots) {
        // Combined Lumberjack outfit (all variants in one requirement)
        if (!skipHead) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.FORESTRY_LUMBERJACK_HAT, ItemID.RAMBLE_LUMBERJACK_HAT), 
                EquipmentInventorySlot.HEAD, 
                priority, rating, "Lumberjack hat - optimal for woodcutting XP",
                taskContext
            ));
        }
        if (!skipBody) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.FORESTRY_LUMBERJACK_TOP, ItemID.RAMBLE_LUMBERJACK_TOP), 
                EquipmentInventorySlot.BODY, 
                priority, rating, "Lumberjack top - optimal for woodcutting XP",
                taskContext
            ));
        }
        if (!skipLegs) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.FORESTRY_LUMBERJACK_LEGS, ItemID.RAMBLE_LUMBERJACK_LEGS), 
                EquipmentInventorySlot.LEGS, 
                priority, rating, "Lumberjack legs - optimal for woodcutting XP",
                taskContext
            ));
        }
        if (!skipBoots) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.FORESTRY_LUMBERJACK_BOOTS, ItemID.RAMBLE_LUMBERJACK_BOOTS), 
                EquipmentInventorySlot.BOOTS, 
                priority, rating, "Lumberjack boots - optimal for woodcutting XP",
                taskContext
            ));
        }
    }

    /**
     * Registers the complete Angler Outfit for the plugin scheduler.
     * This is the specialized outfit for fishing activities including all variants.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the angler outfit
     */
    public static void registerAnglerOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext) {
        registerAnglerOutfit(requirements, priority, rating, taskContext, false, false, false, false);
    }

    /**
     * Registers the complete Angler Outfit for the plugin scheduler with convenience flags.
     * This is the specialized outfit for fishing activities including all variants.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the angler outfit
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param skipHead Skip head slot if true
     * @param skipBody Skip body slot if true
     * @param skipLegs Skip legs slot if true
     * @param skipBoots Skip boots slot if true
     */
    public static void registerAnglerOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext,
                                          boolean skipHead, boolean skipBody, boolean skipLegs, boolean skipBoots) {
        // Spirit Angler outfit (enhanced version) - highest priority
        int spiritRating = rating;
        
        if (!skipHead) {
            requirements.register(new ItemRequirement(
                ItemID.SPIRIT_ANGLER_HAT, 1,
                EquipmentInventorySlot.HEAD, 
                priority, spiritRating, "Spirit angler hat - enhanced fishing XP bonus",taskContext
            ));
        }
        if (!skipBody) {
            requirements.register(new ItemRequirement(
                ItemID.SPIRIT_ANGLER_TOP, 1,
                EquipmentInventorySlot.BODY,
                 priority, spiritRating, "Spirit angler top - enhanced fishing XP bonus",taskContext
            ));
        }
        if (!skipLegs) {
            requirements.register(new ItemRequirement(
                ItemID.SPIRIT_ANGLER_LEGS, 1,
                EquipmentInventorySlot.LEGS, 
                priority, spiritRating, "Spirit angler legs - enhanced fishing XP bonus",taskContext
            ));
        }
        if (!skipBoots) {
            requirements.register(new ItemRequirement(
                ItemID.SPIRIT_ANGLER_BOOTS, 1,
                EquipmentInventorySlot.BOOTS, 
                priority, spiritRating, "Spirit angler boots - enhanced fishing XP bonus",taskContext
            ));
        }

        // Regular Angler outfit (Trawler reward)
        int regularRating = Math.max(1,spiritRating-1);
        
        if (!skipHead) {
            requirements.register(new ItemRequirement(
                ItemID.TRAWLER_REWARD_HAT, 1,
                EquipmentInventorySlot.HEAD, 
                priority, regularRating, "Angler hat - provides fishing XP bonus",taskContext
            ));
        }
        if (!skipBody) {
            requirements.register(new ItemRequirement(
                ItemID.TRAWLER_REWARD_TOP, 1,
                EquipmentInventorySlot.BODY, 
                priority, regularRating, "Angler top - provides fishing XP bonus",taskContext
            ));
        }
        if (!skipLegs) {
            requirements.register(new ItemRequirement(
                ItemID.TRAWLER_REWARD_LEGS, 1,
                EquipmentInventorySlot.LEGS, 
                priority, regularRating, "Angler legs - provides fishing XP bonus",taskContext
            ));
        }
        if (!skipBoots) {
            requirements.register(new ItemRequirement(
                ItemID.TRAWLER_REWARD_BOOTS, 1,
                EquipmentInventorySlot.BOOTS,
                 priority, regularRating, "Angler boots - provides fishing XP bonus",taskContext
            ));
        }
    }

    /**
     * Registers high-healing food items for combat and survival activities.
     * Uses the Rs2Food enum to get the most effective food options.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for food items
     * @param quantity The quantity of food to require
     */
    public static void registerHighHealingFood(PrePostScheduleRequirements requirements, RequirementPriority priority, TaskContext taskContext,int quantity) {
        // Register food in order of healing effectiveness (highest heal values first)
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.Dark_Crab.getId()), quantity,
            null, -1, priority, 10, "Dark crab - heals " + Rs2Food.Dark_Crab.getHeal() + " HP (best healing food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.ROCKTAIL.getId()), quantity,
            null, -1, priority, 9, "Rocktail - heals " + Rs2Food.ROCKTAIL.getHeal() + " HP (excellent healing)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.MANTA.getId()), quantity,
            null, -1, priority, 8, "Manta ray - heals " + Rs2Food.MANTA.getHeal() + " HP (very good healing)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.SEA_TURTLE.getId()), quantity,
            null, -1, priority, 7, "Sea turtle - heals " + Rs2Food.SEA_TURTLE.getHeal() + " HP (good healing)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.SHARK.getId()), quantity,
            null, -1, priority, 6, "Shark - heals " + Rs2Food.SHARK.getHeal() + " HP (standard high-level food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.KARAMBWAN.getId()), quantity,
            null, -1, priority, 5, "Cooked karambwan - heals " + Rs2Food.KARAMBWAN.getHeal() + " HP (can combo with other food)",taskContext
        ));
    }

    /**
     * Registers mid-tier healing food items for general use.
     * Includes commonly available and cost-effective food options.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for food items
     * @param quantity The quantity of food to require
     */
    public static void registerMidTierFood(PrePostScheduleRequirements requirements, RequirementPriority priority,TaskContext taskContext ,int quantity) {
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.MONKFISH.getId()), quantity,
            null, -1, priority, 8, "Monkfish - heals " + Rs2Food.MONKFISH.getHeal() + " HP (good mid-tier food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.SWORDFISH.getId()), quantity,
            null, -1, priority, 6, "Swordfish - heals " + Rs2Food.SWORDFISH.getHeal() + " HP (decent mid-tier food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.BASS.getId()), quantity,
            null, -1, priority, 5, "Bass - heals " + Rs2Food.BASS.getHeal() + " HP (alternative mid-tier food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.LOBSTER.getId()), quantity,
            null, -1, priority, 4, "Lobster - heals " + Rs2Food.LOBSTER.getHeal() + " HP (common mid-tier food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.TUNA.getId()), quantity,
            null, -1, priority, 3, "Tuna - heals " + Rs2Food.TUNA.getHeal() + " HP (affordable mid-tier food)",taskContext
        ));
    }

    /**
     * Registers fast food items that can be eaten in 1 tick.
     * Useful for combo eating or quick healing during combat.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for food items
     * @param quantity The quantity of food to require
     */
    public static void registerFastFood(PrePostScheduleRequirements requirements, RequirementPriority priority, TaskContext taskContext,int quantity) {
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.KARAMBWAN.getId()), quantity,
            null, -1, priority, 9, "Cooked karambwan - heals " + Rs2Food.KARAMBWAN.getHeal() + " HP (1-tick food, good for combos)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.CHOCOLATE_CAKE.getId()), quantity,
            null, -1, priority, 6, "Chocolate cake - heals " + Rs2Food.CHOCOLATE_CAKE.getHeal() + " HP (1-tick food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.CAKE.getId()), quantity,
            null, -1, priority, 5, "Cake - heals " + Rs2Food.CAKE.getHeal() + " HP (1-tick food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.PLAIN_PIZZA.getId()), quantity,
            null, -1, priority, 4, "Plain pizza - heals " + Rs2Food.PLAIN_PIZZA.getHeal() + " HP (1-tick food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.MEAT_PIZZA.getId()), quantity,
            null, -1, priority, 4, "Meat pizza - heals " + Rs2Food.MEAT_PIZZA.getHeal() + " HP (1-tick food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.ANCHOVY_PIZZA.getId()), quantity,
            null, -1, priority, 4, "Anchovy pizza - heals " + Rs2Food.ANCHOVY_PIZZA.getHeal() + " HP (1-tick food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.PINEAPPLE_PIZZA.getId()), quantity,
            null, -1, priority, 5, "Pineapple pizza - heals " + Rs2Food.PINEAPPLE_PIZZA.getHeal() + " HP (1-tick food)",taskContext
        ));
    }

    /**
     * Registers basic/emergency food items for low-level activities.
     * Includes cheap and easily obtainable food options.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for food items
     * @param quantity The quantity of food to require
     */
    public static void registerBasicFood(PrePostScheduleRequirements requirements, RequirementPriority priority, TaskContext taskContext,int quantity) {
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.SALMON.getId()), quantity,
            null, -1, priority, 5, "Salmon - heals " + Rs2Food.SALMON.getHeal() + " HP (basic food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.TROUT.getId()), quantity,
            null, -1, priority, 4, "Trout - heals " + Rs2Food.TROUT.getHeal() + " HP (basic food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.PIKE.getId()), quantity,
            null, -1, priority, 4, "Pike - heals " + Rs2Food.PIKE.getHeal() + " HP (basic food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.COD.getId()), quantity,
            null, -1, priority, 3, "Cod - heals " + Rs2Food.COD.getHeal() + " HP (basic food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.HERRING.getId()), quantity,
            null, -1, priority, 3, "Herring - heals " + Rs2Food.HERRING.getHeal() + " HP (basic food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.SARDINE.getId()), quantity,
            null, -1, priority, 2, "Sardine - heals " + Rs2Food.SARDINE.getHeal() + " HP (basic food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.SHRIMPS.getId()), quantity,
            null, -1, priority, 2, "Shrimps - heals " + Rs2Food.SHRIMPS.getHeal() + " HP (basic food)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(Rs2Food.BREAD.getId()), quantity,
            null, -1, priority, 2, "Bread - heals " + Rs2Food.BREAD.getHeal() + " HP (emergency food)",taskContext
        ));       
    }

    /**
     * Registers runes required for NPC Contact spell, which is used for pouch repair.
     * This is recommended for efficiency in the Guardians of the Rift minigame.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     */
    public static void registerRunesForNPCContact(PrePostScheduleRequirements requirements, RequirementPriority priority,TaskContext taskContext, int rating) {
         // NPC Contact runes for pouch repair (recommended for efficiency)
        requirements.register(new ItemRequirement(
            ItemID.COSMICRUNE,
            1, -1, priority, rating, "Cosmic runes (for NPC Contact spell)",taskContext
        ));
        requirements.register(new ItemRequirement(
            ItemID.ASTRALRUNE,
            1, -1, priority, rating, "Astral runes (for NPC Contact spell)",taskContext
        ));
        requirements.register(new ItemRequirement(
            ItemID.AIRRUNE,
            1, -1, priority, rating, "Air runes (for NPC Contact spell)",taskContext
        ));
    }
    
    /**
     * Registers rune pouches for efficient runecrafting.
     * This includes all pouch types with their level requirements.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the rune pouches
     */
    public static void registerRunePouches(PrePostScheduleRequirements requirements, RequirementPriority priority,TaskContext taskContext) {
        // Rune pouches for efficient rune storage
       requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.RCU_POUCH_COLOSSAL, ItemID.RCU_POUCH_COLOSSAL_DEGRADE),1,
            -1, priority, 10, "Colossal pouch (for maximum essence carrying)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.RCU_POUCH_GIANT, ItemID.RCU_POUCH_GIANT_DEGRADE),1,
            -1, priority, 8, "Giant pouch (for high essence carrying)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.RCU_POUCH_LARGE, ItemID.RCU_POUCH_LARGE_DEGRADE),1,
            -1, priority, 6, "Large pouch (for good essence carrying)",taskContext
        ));
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.RCU_POUCH_MEDIUM, ItemID.RCU_POUCH_MEDIUM_DEGRADE),1,
            -1, priority, 4, "Medium pouch (for decent essence carrying)",taskContext
        ));
        requirements.register(new ItemRequirement(
            ItemID.RCU_POUCH_SMALL,
            1, -1, priority, 2, "Small pouch (basic essence carrying)",taskContext
        ));
    }

    /**
     * Registers the Varrock diary armour for the plugin scheduler.
     * This includes all tiers of Varrock diary armour (Easy, Medium, Hard, Elite).
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the Varrock armour
     */
    public static void registerVarrockDiaryArmour(PrePostScheduleRequirements requirements, RequirementPriority priority,TaskContext taskContext) {
        // Varrock armour progression (Elite > Hard > Medium > Easy)
        requirements.register(new ItemRequirement(
            ItemID.VARROCK_ARMOUR_ELITE, 1,
            EquipmentInventorySlot.BODY, priority, 10, "Varrock armour 4 (Elite) - best diary armour with all benefits",taskContext
        ));
        requirements.register(new ItemRequirement(
            ItemID.VARROCK_ARMOUR_HARD, 1,
            EquipmentInventorySlot.BODY, priority, 8, "Varrock armour 3 (Hard) - good diary armour",taskContext
        ));
        requirements.register(new ItemRequirement(
            ItemID.VARROCK_ARMOUR_MEDIUM, 1,
            EquipmentInventorySlot.BODY, priority, 6, "Varrock armour 2 (Medium) - decent diary armour",taskContext
        ));
        requirements.register(new ItemRequirement(
            ItemID.VARROCK_ARMOUR_EASY, 1,
            EquipmentInventorySlot.BODY, priority, 4, "Varrock armour 1 (Easy) - basic diary armour",taskContext
        ));
    }

    /**
     * Registers the complete Prospector/Motherlode Mine outfit for the plugin scheduler.
     * This includes all variants (regular, gold, and fossil variants).
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the prospector outfit
     */
    public static void registerProspectorOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority,int rating,TaskContext taskContext) {
        registerProspectorOutfit(requirements, priority,rating,taskContext, false, false, false, false);
    }

    /**
     * Registers the complete Prospector/Motherlode Mine outfit for the plugin scheduler with convenience flags.
     * This includes all variants (regular, gold, and fossil variants).
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the prospector outfit
     * @param skipHead Skip head slot if true
     * @param skipBody Skip body slot if true
     * @param skipLegs Skip legs slot if true
     * @param skipBoots Skip boots slot if true
     */
    public static void registerProspectorOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority,int rating,TaskContext taskContext,
                                              boolean skipHead, boolean skipBody, boolean skipLegs, boolean skipBoots) {
        
        
          // Prospector outfit pieces for additional mining benefits
        if (!skipHead) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.MOTHERLODE_REWARD_HAT, ItemID.MOTHERLODE_REWARD_HAT_GOLD, ItemID.FOSSIL_MOTHERLODE_REWARD_HAT),
                EquipmentInventorySlot.HEAD, priority, rating, "Prospector helmet", taskContext
            ));
        }
        if (!skipBody) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.MOTHERLODE_REWARD_TOP, ItemID.MOTHERLODE_REWARD_TOP_GOLD, ItemID.FOSSIL_MOTHERLODE_REWARD_TOP),
                EquipmentInventorySlot.BODY, priority, rating, "Prospector jacket", taskContext
            ));
        }
        if (!skipLegs) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.MOTHERLODE_REWARD_LEGS, ItemID.MOTHERLODE_REWARD_LEGS_GOLD, ItemID.FOSSIL_MOTHERLODE_REWARD_LEGS),
                EquipmentInventorySlot.LEGS, priority, rating, "Prospector legs", taskContext
            ));
        }
        if (!skipBoots) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.MOTHERLODE_REWARD_BOOTS, ItemID.MOTHERLODE_REWARD_BOOTS_GOLD, ItemID.FOSSIL_MOTHERLODE_REWARD_BOOTS),
                EquipmentInventorySlot.BOOTS, priority, rating, "Prospector boots", taskContext
            ));
        }
  
   
    }
    public static void registerRunecraftingCape(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext) {
        // Register runecrafting capes for additional benefits
        // Skill capes for additional benefits
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.SKILLCAPE_RUNECRAFTING, ItemID.SKILLCAPE_RUNECRAFTING_TRIMMED),1,
            EquipmentInventorySlot.CAPE, -2,priority, rating, 
            "Runecrafting cape (any variant)",taskContext,null,-1,
             Skill.RUNECRAFT,99        ));
        
    }
    public static void registerMiningCape(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext) {
        // Register mining capes for additional benefits
        requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.SKILLCAPE_MINING, ItemID.SKILLCAPE_MINING_TRIMMED),1,
            EquipmentInventorySlot.CAPE, -2,priority, rating, 
            "Mining cape (any variant)",taskContext,null,-1,
             Skill.MINING,99        ));
    }

    /**
     * Registers the complete Pyromancer Outfit for the plugin scheduler.
     * This is the specialized outfit for firemaking activities from Wintertodt.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the pyromancer outfit
     * @param rating The rating for the pyromancer outfit  
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     */
    public static void registerPyromancerOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext) {
        registerPyromancerOutfit(requirements, priority, rating, taskContext, false, false, false, false, false);
    }

    /**
     * Registers the complete Pyromancer Outfit for the plugin scheduler with convenience flags.
     * This is the specialized outfit for firemaking activities from Wintertodt.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the pyromancer outfit
     * @param rating The rating for the pyromancer outfit
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param skipHead Skip head slot if true
     * @param skipBody Skip body slot if true
     * @param skipLegs Skip legs slot if true
     * @param skipBoots Skip boots slot if true
     * @param skipGloves Skip gloves slot if true
     */
    public static void registerPyromancerOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext,
                                              boolean skipHead, boolean skipBody, boolean skipLegs, boolean skipBoots, boolean skipGloves) {
        if (!skipHead) {
            requirements.register(new ItemRequirement(
                ItemID.PYROMANCER_HOOD, 1,
                EquipmentInventorySlot.HEAD, 
                priority, rating, "Pyromancer hood - provides firemaking XP bonus",
                taskContext
            ));
        }
        if (!skipBody) {
            requirements.register(new ItemRequirement(
                ItemID.PYROMANCER_TOP, 1,
                EquipmentInventorySlot.BODY, 
                priority, rating, "Pyromancer garb - provides firemaking XP bonus",
                taskContext
            ));
        }
        if (!skipLegs) {
            requirements.register(new ItemRequirement(
                ItemID.PYROMANCER_BOTTOM, 1,
                EquipmentInventorySlot.LEGS, 
                priority, rating, "Pyromancer robe - provides firemaking XP bonus",
                taskContext
            ));
        }
        if (!skipBoots) {
            requirements.register(new ItemRequirement(
                ItemID.PYROMANCER_BOOTS, 1,
                EquipmentInventorySlot.BOOTS, 
                priority, rating, "Pyromancer boots - provides firemaking XP bonus",
                taskContext
            ));
        }
        if (!skipGloves) {
            requirements.register(new ItemRequirement(
                ItemID.PYROMANCER_GLOVES, 1,
                EquipmentInventorySlot.GLOVES, 
                priority, rating, "Pyromancer gloves - provides firemaking XP bonus",
                taskContext
            ));
        }
    }

    /**
     * Registers the complete Farmer's Outfit for the plugin scheduler.
     * This is the specialized outfit for farming activities from Tithe Farm.
     * Note: These are cosmetic farmer clothing items, not the actual skilling outfit.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the farmer's outfit
     * @param rating The rating for the farmer's outfit
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     */
    public static void registerFarmersOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext) {
        registerFarmersOutfit(requirements, priority, rating, taskContext, false, false, false, false);
    }

    /**
     * Registers the complete Farmer's Outfit for the plugin scheduler with convenience flags.
     * This is the specialized outfit for farming activities from Tithe Farm.
     * Note: These are cosmetic farmer clothing items, not the actual skilling outfit.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the farmer's outfit
     * @param rating The rating for the farmer's outfit
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param skipHead Skip head slot if true
     * @param skipBody Skip body slot if true
     * @param skipLegs Skip legs slot if true
     * @param skipBoots Skip boots slot if true
     */
    public static void registerFarmersOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext,
                                           boolean skipHead, boolean skipBody, boolean skipLegs, boolean skipBoots) {
        if (!skipHead) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.TITHE_REWARD_HAT_MALE, ItemID.TITHE_REWARD_HAT_FEMALE),  // Farmer's strawhats
                EquipmentInventorySlot.HEAD, 
                priority, rating, "Farmer's strawhat - cosmetic farming item",
                taskContext
            ));
        }
        if (!skipBody) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.TITHE_REWARD_TORSO_MALE,ItemID.TITHE_REWARD_TORSO_FEMALE), // Farmer's jacket
                EquipmentInventorySlot.BODY, 
                priority, rating, "Farmer's jacket - cosmetic farming item",
                taskContext
            ));
        }
        if (!skipLegs) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.TITHE_REWARD_LEGS_MALE, ItemID.TITHE_REWARD_LEGS_FEMALE), // Farmer's boro trousers
                EquipmentInventorySlot.LEGS, 
                priority, rating, "Farmer's boro trousers - cosmetic farming item",
                taskContext
            ));
        }
        if (!skipBoots) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.TITHE_REWARD_FEET_MALE, ItemID.TITHE_REWARD_FEET_FEMALE),  // Farmer's boots
                EquipmentInventorySlot.BOOTS, 
                priority, rating, "Farmer's boots - cosmetic farming item",
                taskContext
            ));
        }
    }

    /**
     * Registers the complete Carpenter's Outfit for the plugin scheduler.
     * This is the specialized outfit for construction activities from Mahogany Homes.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the carpenter's outfit
     * @param rating The rating for the carpenter's outfit
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     */
    public static void registerCarpentersOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext) {
        registerCarpentersOutfit(requirements, priority, rating, taskContext, false, false, false, false);
    }

    /**
     * Registers the complete Carpenter's Outfit for the plugin scheduler with convenience flags.
     * This is the specialized outfit for construction activities from Mahogany Homes.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the carpenter's outfit
     * @param rating The rating for the carpenter's outfit
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param skipHead Skip head slot if true
     * @param skipBody Skip body slot if true
     * @param skipLegs Skip legs slot if true
     * @param skipBoots Skip boots slot if true
     */
    public static void registerCarpentersOutfit(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext,
                                              boolean skipHead, boolean skipBody, boolean skipLegs, boolean skipBoots) {
        if (!skipHead) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(24872),  // Carpenter's helmet
                EquipmentInventorySlot.HEAD, 
                priority, rating, "Carpenter's helmet - provides construction XP bonus",
                taskContext
            ));
        }
        if (!skipBody) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(24874),  // Carpenter's shirt
                EquipmentInventorySlot.BODY, 
                priority, rating, "Carpenter's shirt - provides construction XP bonus",
                taskContext
            ));
        }
        if (!skipLegs) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(24876),  // Carpenter's trousers
                EquipmentInventorySlot.LEGS, 
                priority, rating, "Carpenter's trousers - provides construction XP bonus",
                taskContext
            ));
        }
        if (!skipBoots) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(24878),  // Carpenter's boots
                EquipmentInventorySlot.BOOTS, 
                priority, rating, "Carpenter's boots - provides construction XP bonus",
                taskContext
            ));
        }
    }

    /**
     * Registers the complete Zealot's Robes for the plugin scheduler.
     * This is the specialized outfit for prayer activities from Shade Catacombs.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the zealot's robes
     * @param rating The rating for the zealot's robes
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     */
    public static void registerZealotsRobes(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext) {
        registerZealotsRobes(requirements, priority, rating, taskContext, false, false, false, false);
    }

    /**
     * Registers the complete Zealot's Robes for the plugin scheduler with convenience flags.
     * This is the specialized outfit for prayer activities from Shade Catacombs.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the zealot's robes
     * @param rating The rating for the zealot's robes
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param skipHead Skip head slot if true
     * @param skipBody Skip body slot if true
     * @param skipLegs Skip legs slot if true
     * @param skipBoots Skip boots slot if true
     */
    public static void registerZealotsRobes(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext,
                                          boolean skipHead, boolean skipBody, boolean skipLegs, boolean skipBoots) {
        if (!skipHead) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(25438),  // Zealot's helm
                EquipmentInventorySlot.HEAD, 
                priority, rating, "Zealot's helm - chance to save bones/ensouled heads",
                taskContext
            ));
        }
        if (!skipBody) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(25434),  // Zealot's robe top
                EquipmentInventorySlot.BODY, 
                priority, rating, "Zealot's robe top - chance to save bones/ensouled heads",
                taskContext
            ));
        }
        if (!skipLegs) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(25436),  // Zealot's robe bottom
                EquipmentInventorySlot.LEGS, 
                priority, rating, "Zealot's robe bottom - chance to save bones/ensouled heads",
                taskContext
            ));
        }
        if (!skipBoots) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(25440),  // Zealot's boots
                EquipmentInventorySlot.BOOTS, 
                priority, rating, "Zealot's boots - chance to save bones/ensouled heads",
                taskContext
            ));
        }
    }

    /**
     * Registers the complete Rogue Equipment for the plugin scheduler.
     * This is the specialized outfit for thieving activities from Rogues' Den.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the rogue equipment
     * @param rating The rating for the rogue equipment
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     */
    public static void registerRogueEquipment(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext) {
        registerRogueEquipment(requirements, priority, rating, taskContext, false, false, false, false, false);
    }

    /**
     * Registers the complete Rogue Equipment for the plugin scheduler with convenience flags.
     * This is the specialized outfit for thieving activities from Rogues' Den.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the rogue equipment
     * @param rating The rating for the rogue equipment
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param skipHead Skip head slot if true
     * @param skipBody Skip body slot if true
     * @param skipLegs Skip legs slot if true
     * @param skipGloves Skip gloves slot if true
     * @param skipBoots Skip boots slot if true
     */
    public static void registerRogueEquipment(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext,
                                            boolean skipHead, boolean skipBody, boolean skipLegs, boolean skipGloves, boolean skipBoots) {
        if (!skipHead) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(5554),  // Rogue mask
                EquipmentInventorySlot.HEAD, 
                priority, rating, "Rogue mask - chance for double loot when pickpocketing",
                taskContext
            ));
        }
        if (!skipBody) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.ROGUESDEN_BODY),  // Rogue top
                EquipmentInventorySlot.BODY, 
                priority, rating, "Rogue top - chance for double loot when pickpocketing",
                taskContext
            ));
        }
        if (!skipLegs) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.ROGUESDEN_LEGS), // Rogue trousers
                EquipmentInventorySlot.LEGS, 
                priority, rating, "Rogue trousers - chance for double loot when pickpocketing",
                taskContext
            ));
        }
        if (!skipGloves) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.ROGUESDEN_GLOVES), // Rogue gloves
                EquipmentInventorySlot.GLOVES, 
                priority, rating, "Rogue gloves - chance for double loot when pickpocketing",
                taskContext
            ));
        }
        if (!skipBoots) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.ROGUESDEN_BOOTS),  // Rogue boots
                EquipmentInventorySlot.BOOTS, 
                priority, rating, "Rogue boots - chance for double loot when pickpocketing",
                taskContext
            ));
        }
    }

    /**
     * Registers the complete Smith's Uniform for the plugin scheduler.
     * This is the specialized outfit for smithing activities from Giants' Foundry.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the smith's uniform
     * @param rating The rating for the smith's uniform
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     */
    public static void registerSmithsUniform(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext) {
        registerSmithsUniform(requirements, priority, rating, taskContext, false, false, false, false);
    }

    /**
     * Registers the complete Smith's Uniform for the plugin scheduler with convenience flags.
     * This is the specialized outfit for smithing activities from Giants' Foundry.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the smith's uniform
     * @param rating The rating for the smith's uniform
     * @param TaskContext The schedule context for these requirements (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param skipBody Skip body slot if true
     * @param skipLegs Skip legs slot if true
     * @param skipGloves Skip gloves slot if true
     * @param skipBoots Skip boots slot if true
     */
    public static void registerSmithsUniform(PrePostScheduleRequirements requirements, RequirementPriority priority, int rating, TaskContext taskContext,
                                           boolean skipBody, boolean skipLegs, boolean skipGloves, boolean skipBoots) {
        if (!skipBody) {
            requirements.register(new ItemRequirement(
                ItemID.SMITHING_UNIFORM_TORSO, 1,
                EquipmentInventorySlot.BODY, 
                priority, rating, "Smith's uniform torso - speeds up smithing actions",
                taskContext
            ));
        }
        if (!skipLegs) {
            requirements.register(new ItemRequirement(
                ItemID.SMITHING_UNIFORM_LEGS, 1,
                EquipmentInventorySlot.LEGS, 
                priority, rating, "Smith's uniform legs - speeds up smithing actions",
                taskContext
            ));
        }
        if (!skipGloves) {
            requirements.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.SMITHING_UNIFORM_GLOVES, ItemID.SMITHING_UNIFORM_GLOVES_ICE),
                EquipmentInventorySlot.GLOVES, 
                priority, rating, "Smith's uniform gloves - speeds up smithing actions",
                taskContext
            ));
        }
        if (!skipBoots) {
            requirements.register(new ItemRequirement(
                ItemID.SMITHING_UNIFORM_BOOTS, 1,
                EquipmentInventorySlot.BOOTS, 
                priority, rating, "Smith's uniform boots - speeds up smithing actions",
                taskContext
            ));
        }
    }
    /**
     * Registers mid-tier healing food items using logical OR requirement.
     * This demonstrates the new logical requirement system where only one food type is needed.
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for food items
     * @param TaskContext The schedule context for these requirements
     * @param quantity The quantity of food to require
     */
    public static void registerMidTierFoodLogical(PrePostScheduleRequirements requirements, RequirementPriority priority, 
                                                 TaskContext taskContext, int quantity) {
        // Create individual food item requirements
        ItemRequirement monkfish = new ItemRequirement(
            Rs2Food.MONKFISH.getId(), quantity,
            null, -1, priority, 8, 
            "Monkfish - heals " + Rs2Food.MONKFISH.getHeal() + " HP (good mid-tier food)", taskContext
        );
        
        ItemRequirement swordfish = new ItemRequirement(
            Rs2Food.SWORDFISH.getId(), quantity,
            null, -1, priority, 6, 
            "Swordfish - heals " + Rs2Food.SWORDFISH.getHeal() + " HP (decent mid-tier food)", taskContext
        );
        
        ItemRequirement bass = new ItemRequirement(
            Rs2Food.BASS.getId(), quantity,
            null, -1, priority, 5, 
            "Bass - heals " + Rs2Food.BASS.getHeal() + " HP (alternative mid-tier food)", taskContext
        );
        
        ItemRequirement lobster = new ItemRequirement(
            Rs2Food.LOBSTER.getId(), quantity,
            null, -1, priority, 4, 
            "Lobster - heals " + Rs2Food.LOBSTER.getHeal() + " HP (common mid-tier food)", taskContext
        );
        
        ItemRequirement tuna =new ItemRequirement(
            Rs2Food.TUNA.getId(), quantity,
            null, -1, priority, 3, 
            "Tuna - heals " + Rs2Food.TUNA.getHeal() + " HP (affordable mid-tier food)", taskContext
        );
        
        // Create an OR requirement combining all food options
        // Only one of these food types needs to be available
        OrRequirement midTierFoodOptions = new OrRequirement(
            priority, 8, "Mid-tier food options", taskContext,
            monkfish, swordfish, bass, lobster, tuna
        );
        
        // Register the logical OR requirement
        requirements.register(midTierFoodOptions);
    }
    
    /**
     * Demonstrates a more complex logical requirement with both AND and OR logic.
     * This shows how you might require a complete combat setup where:
     * - You need BOTH a weapon AND armor (AND requirement)
     * - For each category, any suitable option will do (OR requirements)
     *
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param priority The priority level for the combat setup
     * @param TaskContext The schedule context for these requirements
     */
    public static void registerCombatSetupLogical(PrePostScheduleRequirements requirements, RequirementPriority priority, 
                                                 TaskContext taskContext) {
        // TODO: Implement combat setup requirements using ConditionalRequirement
        // Example: Create weapon and armor requirements with OR logic for each category
        // and AND logic between categories
        
    }
    public static void registerStaminaPotions(PrePostScheduleRequirements requirements,int amount, RequirementPriority priority,int rating, 
                                                 TaskContext taskContext,boolean preferLowerCharges) {
        requirements.register(OrRequirement.fromItemIds(
            Arrays.asList(ItemID._1DOSESTAMINA, 
                                ItemID._2DOSESTAMINA, 
                                ItemID._3DOSESTAMINA, 
                                ItemID._4DOSESTAMINA),amount,
            null, -1, 
            priority, rating, "Stamina potions for energy restoration",
            taskContext, preferLowerCharges
        ));
    }
    public static void registerRingOfDueling(PrePostScheduleRequirements requirements, RequirementPriority priority,int rating, 
                                                 TaskContext taskContext,boolean preferLowerCharges) {
        requirements.register(OrRequirement.fromItemIds(
            Arrays.asList(ItemID.RING_OF_DUELING_8, ItemID.RING_OF_DUELING_7, ItemID.RING_OF_DUELING_6,
                         ItemID.RING_OF_DUELING_5, ItemID.RING_OF_DUELING_4, ItemID.RING_OF_DUELING_3,
                         ItemID.RING_OF_DUELING_2, ItemID.RING_OF_DUELING_1),1,
            EquipmentInventorySlot.RING, -1,
            priority, 
            rating, "Ring of dueling for teleports",taskContext,preferLowerCharges

        ));
        
    }
    public static void registerAmuletOfGlory(PrePostScheduleRequirements requirements, RequirementPriority priority,int rating, 
                                                 TaskContext taskContext,boolean preferLowerCharges) {

        // AMULET - Example with charged items
        requirements.register( OrRequirement.fromItemIds(
            Arrays.asList(ItemID.AMULET_OF_GLORY_6, 
                        ItemID.AMULET_OF_GLORY_5, 
                        ItemID.AMULET_OF_GLORY_4, 
                        ItemID.AMULET_OF_GLORY_3, 
                        ItemID.AMULET_OF_GLORY_2, 
                        ItemID.AMULET_OF_GLORY_1),1,
            EquipmentInventorySlot.AMULET, -1,
            priority, rating, 
            "Amulet of glory for teleports",
            taskContext, preferLowerCharges
        ));
     }
     /**
     * Registers a smart mining equipment conditional requirement that upgrades equipment based on player capabilities.
     * This demonstrates the power of conditional requirements over simple AND/OR logic.
     * 
     * Workflow:
     * 1. Ensure basic pickaxe if none available
     * 2. If player has sufficient GP and mining level, upgrade to better pickaxe
     * 3. If player has high mining level, consider dragon pickaxe
     * 
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param TaskContext The schedule context for these requirements
     */
    public static void registerSmartMiningEquipment(PrePostScheduleRequirements requirements, TaskContext taskContext) {
        // Create a conditional requirement for smart mining equipment management
        ConditionalRequirement smartMiningEquipment = ConditionalRequirementBuilder.createEquipmentUpgrader(
                new int[]{ItemID.BRONZE_PICKAXE, ItemID.IRON_PICKAXE}, // Basic equipment
                new int[]{ItemID.RUNE_PICKAXE, ItemID.DRAGON_PICKAXE}, // Upgrade equipment  
                100000, // Min GP for upgrade
                EquipmentInventorySlot.WEAPON,
                "mining pickaxe",
                RequirementPriority.RECOMMENDED,
                taskContext
        );
        
        requirements.register(smartMiningEquipment);
    }
    
    /**
     * Registers a complete preparation workflow for wilderness activities using OrderedRequirement.
    
     * This shows how ordered requirements can manage complex preparation sequences.
     * 
     * Order:
     * 1. Bank valuable items
     * 2. Withdraw wilderness supplies
     * 3. Equip appropriate gear
     * 4. Set up inventory
     * 5. Move to wilderness entry point
     * 
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param TaskContext The schedule context for these requirements
     */
    public static void registerWildernessPreparation(PrePostScheduleRequirements requirements, TaskContext taskContext) {
        OrderedRequirement wildernessPrep = new OrderedRequirement(
                RequirementPriority.MANDATORY, 9, "Complete Wilderness Preparation", taskContext
        );
       /*
        // Step 1: Bank valuable items first
        wildernessPrep.addStep(
                new ItemRequirement(RequirementType.INVENTORY, Priority.MANDATORY, 8,
                        "Bank valuable items before wilderness", Arrays.asList(), taskContext),
                "Bank valuable items"
        );
        
        // Step 2: Withdraw wilderness food
        wildernessPrep.addStep(
                new ItemRequirement(RequirementType.INVENTORY, Priority.MANDATORY, 9,
                        "Food for wilderness survival", 
                        Arrays.asList(ItemID.SHARK, ItemID.KARAMBWAN, ItemID.MANTA_RAY), taskContext),
                "Withdraw food for wilderness"
        );
        
        // Step 3: Equip budget gear (optional step - can proceed without)
        wildernessPrep.addOptionalStep(
                new ItemRequirement(RequirementType.EQUIPMENT, Priority.OPTIONAL, 6,
                        "Budget wilderness combat gear",
                        Arrays.asList(ItemID.RUNE_SCIMITAR, ItemID.RUNE_FULL_HELM), taskContext),
                "Equip budget wilderness gear"
        ); */ 
        
        // Step 4: Final location check
        wildernessPrep.addStep(
                new LocationRequirement(BankLocation.EDGEVILLE, true, -1, taskContext, RequirementPriority.MANDATORY),
                "Move to wilderness entry point"
        );
        
        requirements.register(wildernessPrep);
    }
    
    /**
     * Registers a level-based spellbook progression using ConditionalRequirement.
     * This demonstrates conditional logic based on player skill levels.
     * 
     * Logic:
     * - If Magic < 50: Stay on standard spellbook
     * - If Magic 50-64: Consider Ancient spellbook for combat
     * - If Magic 65+: Consider Lunar spellbook for utility
     * 
     * @param requirements The PrePostScheduleRequirements instance to register the requirements with
     * @param TaskContext The schedule context for these requirements
     */
    public static void registerSmartSpellbookProgression(PrePostScheduleRequirements requirements, TaskContext taskContext) {
        // Ancient spellbook conditional (for combat)
        ConditionalRequirement ancientSpellbook = ConditionalRequirementBuilder.createLevelBasedRequirement(
                Skill.MAGIC, 50,
                new SpellbookRequirement(Rs2Spellbook.ANCIENT, taskContext, RequirementPriority.RECOMMENDED, 7,
                        "Ancient spellbook for combat spells"),
                "Ancient spellbook for combat (Magic 50+)",
                RequirementPriority.RECOMMENDED,
                taskContext
        );
        
        // Lunar spellbook conditional (for utility)
        ConditionalRequirement lunarSpellbook = ConditionalRequirementBuilder.createLevelBasedRequirement(
                Skill.MAGIC, 65,
                new SpellbookRequirement(Rs2Spellbook.LUNAR, taskContext, RequirementPriority.RECOMMENDED, 8,
                        "Lunar spellbook for utility spells"),
                "Lunar spellbook for utility (Magic 65+)",
                RequirementPriority.RECOMMENDED,
                taskContext
        );
        
        requirements.register(ancientSpellbook);
        requirements.register(lunarSpellbook);
    }



    /**
     * Creates a rune pouch requirement for teleportation magic.
     * Requires various teleport runes.
     */
    public static RunePouchRequirement createTeleportRunePouch() {
        Map<Runes, Integer> requiredRunes = new HashMap<>();
        requiredRunes.put(Runes.LAW, 50);     // Law runes for teleports
        requiredRunes.put(Runes.WATER, 50);   // Water runes for various teleports
        requiredRunes.put(Runes.AIR, 50);     // Air runes for various teleports
        requiredRunes.put(Runes.EARTH, 50);   // Earth runes for various teleports
        
        return new RunePouchRequirement(
            requiredRunes,
            false, // Strict matching - no combination runes
            RequirementPriority.MANDATORY,
            9, // Very high rating for essential teleportation
            "Rune pouch for essential teleportation magic",
            TaskContext.PRE_SCHEDULE
        );
    }
    
    /**
     * Creates a rune pouch requirement for alchemy training.
     * Requires Nature runes and Fire runes for High Level Alchemy.
     */
    public static void registerAlchemyRunePouch(int runeAmount,PrePostScheduleRequirements requirements, RequirementPriority priority,int rating, 
                                                 TaskContext taskContext) {
        Map<Runes, Integer> requiredRunes = new HashMap<>();
        requiredRunes.put(Runes.NATURE, runeAmount); // 1000 nature runes for alchemy
        requiredRunes.put(Runes.FIRE, runeAmount*5);   // 5000 fire runes for alchemy
        
        RunePouchRequirement runePouchRequirement =  new RunePouchRequirement(
            requiredRunes,
            false, // Dont Allow lava runes to substitute for fire runes
            priority,
            rating,
            "Rune pouch for high level alchemy training",
            taskContext
        );
        requirements.register(runePouchRequirement);
    }
}
