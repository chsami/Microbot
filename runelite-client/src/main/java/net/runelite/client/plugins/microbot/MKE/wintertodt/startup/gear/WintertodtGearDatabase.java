package net.runelite.client.plugins.microbot.MKE.wintertodt.startup.gear;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;

import java.util.*;
import java.util.stream.Collectors;

import static net.runelite.api.EquipmentInventorySlot.*;

/**
 * Comprehensive database of all possible gear items for Wintertodt optimization.
 * Items are ranked by priority with Pyromancer gear being the best, followed by
 * warm clothing, then other utility gear.
 * 
 * Priority System:
 * - 1000+: Pyromancer gear (Best in slot)
 * - 800-999: Bruma torch and special Wintertodt items
 * - 600-799: Warm clothing items
 * - 400-599: Graceful outfit (weight reduction)
 * - 200-399: High-level combat gear
 * - 100-199: Mid-level gear
 * - 0-99: Basic gear
 * 
 * @author MakeCD
 * @version 1.0.0
 */
public class WintertodtGearDatabase {
    
    private final Map<EquipmentInventorySlot, List<WintertodtGearItem>> gearBySlot;
    private final List<WintertodtGearItem> allGearItems;
    
    public WintertodtGearDatabase() {
        this.allGearItems = createGearDatabase();
        this.gearBySlot = allGearItems.stream()
                .collect(Collectors.groupingBy(WintertodtGearItem::getSlot));
    }
    
    /**
     * Creates the complete gear database with all possible items.
     */
    private List<WintertodtGearItem> createGearDatabase() {
        List<WintertodtGearItem> items = new ArrayList<>();
        
        // HEAD SLOT ITEMS
        items.addAll(createHeadGear());
        
        // BODY SLOT ITEMS  
        items.addAll(createBodyGear());
        
        // LEGS SLOT ITEMS
        items.addAll(createLegsGear());
        
        // FEET SLOT ITEMS
        items.addAll(createFeetGear());
        
        // HANDS SLOT ITEMS
        items.addAll(createHandsGear());
        
        // WEAPON SLOT ITEMS
        items.addAll(createWeaponGear());
        
        // SHIELD/OFFHAND SLOT ITEMS
        items.addAll(createShieldGear());
        
        // NECK SLOT ITEMS
        items.addAll(createNeckGear());
        
        // RING SLOT ITEMS
        items.addAll(createRingGear());
        
        // CAPE SLOT ITEMS
        items.addAll(createCapeGear());
        
        // Sort all items by effective priority (highest first)
        items.sort((a, b) -> Integer.compare(b.getEffectivePriority(), a.getEffectivePriority()));
        
        return items;
    }
    
    private List<WintertodtGearItem> createHeadGear() {
        return Arrays.asList(
            // Pyromancer gear (Best)
            new WintertodtGearItem.Builder(ItemID.PYROMANCER_HOOD, "Pyromancer hood", HEAD)
                .priority(1000).category(WintertodtGearItem.GearCategory.PYROMANCER)
                .providesWarmth().untradeable()
                .description("Best headgear for Wintertodt")
                .build(),
                
            // High-tier warm headgear (800-899)
            new WintertodtGearItem.Builder(ItemID.FIREMAKING_HOOD, "Firemaking hood", HEAD)
                .priority(850).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.FIREMAKING, 99)
                .description("Firemaking skill hood - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FIRE_MAX_HOOD, "Fire max hood", HEAD)
                .priority(840).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Fire max hood - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.INFERNAL_MAX_HOOD, "Infernal max hood", HEAD)
                .priority(830).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Infernal max hood - provides warmth")
                .build(),
                
            // Slayer helmets (820-829)
            new WintertodtGearItem.Builder(ItemID.SLAYER_HELMET, "Slayer helmet", HEAD)
                .priority(820).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .description("Slayer helmet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.SLAYER_HELMET_I, "Slayer helmet (i)", HEAD)
                .priority(825).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .levelRequirement(Skill.DEFENCE, 10)
                .description("Imbued slayer helmet - provides warmth")
                .build(),
                
            // Santa outfit (750-759)
            new WintertodtGearItem.Builder(ItemID.SANTA_HAT, "Santa hat", HEAD)
                .priority(750).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Santa hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BLACK_SANTA_HAT, "Black santa hat", HEAD)
                .priority(748).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Black santa hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.INVERTED_SANTA_HAT, "Inverted santa hat", HEAD)
                .priority(746).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Inverted santa hat - provides warmth")
                .build(),
                
            // Antisanta outfit (740-749)
            new WintertodtGearItem.Builder(ItemID.ANTISANTA_MASK, "Anti-santa mask", HEAD)
                .priority(740).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Anti-santa mask - provides warmth")
                .build(),
                
            // Hunter/Animal gear (700-739)
            new WintertodtGearItem.Builder(ItemID.LARUPIA_HAT, "Larupia hat", HEAD)
                .priority(720).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Larupia hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.GRAAHK_HEADDRESS, "Graahk headdress", HEAD)
                .priority(718).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Graahk headdress - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.KYATT_HAT, "Kyatt hat", HEAD)
                .priority(716).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Kyatt hat - provides warmth")
                .build(),
                
            // Clue hunter gear (680-699)
            new WintertodtGearItem.Builder(ItemID.CLUE_HUNTER_GARB, "Clue hunter garb", HEAD)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Clue hunter garb - provides warmth")
                .build(),
                
            // Festive/Winter gear (660-679)
            new WintertodtGearItem.Builder(ItemID.BEARHEAD, "Bearhead", HEAD)
                .priority(670).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Bearhead - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.WOOLLY_HAT, "Woolly hat", HEAD)
                .priority(665).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Woolly hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BOBBLE_HAT, "Bobble hat", HEAD)
                .priority(663).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Bobble hat - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.EARMUFFS, "Earmuffs", HEAD)
                .priority(661).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Earmuffs - provides warmth")
                .build(),
                
            // Animal costume heads (640-659)
            new WintertodtGearItem.Builder(ItemID.CHICKEN_HEAD, "Chicken head", HEAD)
                .priority(650).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Chicken head - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.EVIL_CHICKEN_HEAD, "Evil chicken head", HEAD)
                .priority(648).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Evil chicken head - provides warmth")
                .build(),
                
            // Graceful gear (500-599) - Weight reduction but NO warmth
            new WintertodtGearItem.Builder(ItemID.GRACEFUL_HOOD, "Graceful hood", HEAD)
                .priority(500).category(WintertodtGearItem.GearCategory.GRACEFUL)
                .weight(-3).untradeable()
                .levelRequirement(Skill.AGILITY, 30)
                .description("Weight reduction - does NOT provide warmth")
                .build(),
                
            // High-level combat helms (300-399)
            new WintertodtGearItem.Builder(ItemID.SERPENTINE_HELM, "Serpentine helm", HEAD)
                .priority(350).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 75)
                .reducesDamage()
                .description("High defense - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.HELM_OF_NEITIZNOT, "Neitiznot helm", HEAD)
                .priority(320).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 55)
                .questRequirement("The Fremennik Isles")
                .description("Good prayer bonus - no warmth")
                .build(),
                
            // Basic gear (100-299)
            new WintertodtGearItem.Builder(ItemID.RUNE_FULL_HELM, "Rune full helm", HEAD)
                .priority(150).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.ADAMANT_FULL_HELM, "Adamant full helm", HEAD)
                .priority(120).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 30)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.MITHRIL_FULL_HELM, "Mithril full helm", HEAD)
                .priority(90).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 20)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.STEEL_FULL_HELM, "Steel full helm", HEAD)
                .priority(60).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 5)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.IRON_FULL_HELM, "Iron full helm", HEAD)
                .priority(30).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 1)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BRONZE_FULL_HELM, "Bronze full helm", HEAD)
                .priority(10).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .build()
        );
    }
    
    private List<WintertodtGearItem> createBodyGear() {
        return Arrays.asList(
            // Pyromancer gear (Best)
            new WintertodtGearItem.Builder(ItemID.PYROMANCER_GARB, "Pyromancer garb", BODY)
                .priority(1000).category(WintertodtGearItem.GearCategory.PYROMANCER)
                .providesWarmth().untradeable()
                .description("Best body armor for Wintertodt")
                .build(),
                
            // Warm jumpers and special body gear (750-799)
            new WintertodtGearItem.Builder(ItemID.CHRISTMAS_JUMPER, "Christmas jumper", BODY)
                .priority(780).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Christmas jumper - provides warmth")
                .build(),
                
            // Santa outfit
            new WintertodtGearItem.Builder(ItemID.SANTA_JACKET, "Santa jacket", BODY)
                .priority(750).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Santa jacket - provides warmth")
                .build(),
                
            // Antisanta outfit
            new WintertodtGearItem.Builder(ItemID.ANTISANTA_JACKET, "Antisanta jacket", BODY)
                .priority(740).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Antisanta jacket - provides warmth")
                .build(),
                
            // Hunter gear (700-739)
            new WintertodtGearItem.Builder(ItemID.LARUPIA_TOP, "Larupia top", BODY)
                .priority(720).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Larupia top - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.GRAAHK_TOP, "Graahk top", BODY)
                .priority(718).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Graahk top - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.KYATT_TOP, "Kyatt top", BODY)
                .priority(716).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Kyatt top - provides warmth")
                .build(),
                
            // Animal costumes (680-699)
            new WintertodtGearItem.Builder(ItemID.CHICKEN_WINGS, "Chicken wings", BODY)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Chicken wings - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.EVIL_CHICKEN_WINGS, "Evil chicken wings", BODY)
                .priority(688).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Evil chicken wings - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BUNNY_TOP, "Bunny top", BODY)
                .priority(685).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bunny top - provides warmth")
                .build(),
                
            // Graceful gear (500-599) - Weight reduction but NO warmth
            new WintertodtGearItem.Builder(ItemID.GRACEFUL_TOP, "Graceful top", BODY)
                .priority(500).category(WintertodtGearItem.GearCategory.GRACEFUL)
                .weight(-5).untradeable()
                .levelRequirement(Skill.AGILITY, 35)
                .description("Weight reduction - does NOT provide warmth")
                .build(),
                
            // High-level combat armor (300-399)
            new WintertodtGearItem.Builder(ItemID.BANDOS_CHESTPLATE, "Bandos chestplate", BODY)
                .priority(380).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 65)
                .reducesDamage()
                .description("High defense - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FIGHTER_TORSO, "Fighter torso", BODY)
                .priority(360).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .untradeable()
                .description("Good strength bonus - no warmth")
                .build(),
                
            // Basic gear
            new WintertodtGearItem.Builder(ItemID.RUNE_CHAINBODY, "Rune chainbody", BODY)
                .priority(150).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RUNE_PLATEBODY, "Rune platebody", BODY)
                .priority(145).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.LEATHER_BODY, "Leather body", BODY)
                .priority(20).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .build()
        );
    }
    
    private List<WintertodtGearItem> createLegsGear() {
        return Arrays.asList(
            // Pyromancer gear (Best)
            new WintertodtGearItem.Builder(ItemID.PYROMANCER_ROBE, "Pyromancer robe", LEGS)
                .priority(1000).category(WintertodtGearItem.GearCategory.PYROMANCER)
                .providesWarmth().untradeable()
                .description("Best legs for Wintertodt")
                .build(),
                
            // Santa outfit
            new WintertodtGearItem.Builder(ItemID.SANTA_PANTALOONS, "Santa pantaloons", LEGS)
                .priority(750).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Santa pantaloons - provides warmth")
                .build(),
                
            // Antisanta outfit
            new WintertodtGearItem.Builder(ItemID.ANTISANTA_PANTALOONS, "Antisanta pantaloons", LEGS)
                .priority(740).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Antisanta pantaloons - provides warmth")
                .build(),
                
            // Hunter gear (700-739)
            new WintertodtGearItem.Builder(ItemID.LARUPIA_LEGS, "Larupia legs", LEGS)
                .priority(720).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Larupia legs - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.GRAAHK_LEGS, "Graahk legs", LEGS)
                .priority(718).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Graahk legs - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.KYATT_LEGS, "Kyatt legs", LEGS)
                .priority(716).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Kyatt legs - provides warmth")
                .build(),
                
            // Clue hunter gear (680-699)
            new WintertodtGearItem.Builder(ItemID.CLUE_HUNTER_TROUSERS, "Clue hunter trousers", LEGS)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Clue hunter trousers - provides warmth")
                .build(),
                
            // Animal costumes
            new WintertodtGearItem.Builder(ItemID.CHICKEN_LEGS, "Chicken legs", LEGS)
                .priority(685).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Chicken legs - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.EVIL_CHICKEN_LEGS, "Evil chicken legs", LEGS)
                .priority(683).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Evil chicken legs - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BUNNY_LEGS, "Bunny legs", LEGS)
                .priority(680).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bunny legs - provides warmth")
                .build(),
                
            // Graceful gear (500-599) - Weight reduction but NO warmth
            new WintertodtGearItem.Builder(ItemID.GRACEFUL_LEGS, "Graceful legs", LEGS)
                .priority(500).category(WintertodtGearItem.GearCategory.GRACEFUL)
                .weight(-4).untradeable()
                .levelRequirement(Skill.AGILITY, 40)
                .description("Weight reduction - does NOT provide warmth")
                .build(),
                
            // High-level combat legs (300-399)
            new WintertodtGearItem.Builder(ItemID.BANDOS_TASSETS, "Bandos tassets", LEGS)
                .priority(380).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 65)
                .reducesDamage()
                .description("High defense - no warmth")
                .build(),
                
            // Basic gear
            new WintertodtGearItem.Builder(ItemID.RUNE_PLATELEGS, "Rune platelegs", LEGS)
                .priority(150).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RUNE_PLATESKIRT, "Rune plateskirt", LEGS)
                .priority(148).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .build()
        );
    }
    
    private List<WintertodtGearItem> createFeetGear() {
        return Arrays.asList(
            // Pyromancer gear (Best)
            new WintertodtGearItem.Builder(ItemID.PYROMANCER_BOOTS, "Pyromancer boots", BOOTS)
                .priority(1000).category(WintertodtGearItem.GearCategory.PYROMANCER)
                .providesWarmth().untradeable()
                .description("Best boots for Wintertodt")
                .build(),
                
            // Special warm slippers (750-799)
            new WintertodtGearItem.Builder(ItemID.JAD_SLIPPERS, "Jad slippers", BOOTS)
                .priority(780).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Jad slippers - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BEAR_FEET, "Bear feet", BOOTS)
                .priority(770).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bear feet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.DEMON_FEET, "Demon feet", BOOTS)
                .priority(765).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Demon feet - provides warmth")
                .build(),
                
            // Santa/Antisanta boots
            new WintertodtGearItem.Builder(ItemID.SANTA_BOOTS, "Santa boots", BOOTS)
                .priority(750).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Santa boots - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.ANTISANTA_BOOTS, "Antisanta boots", BOOTS)
                .priority(740).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Antisanta boots - provides warmth")
                .build(),
                
            // Clue hunter gear (680-699)
            new WintertodtGearItem.Builder(ItemID.CLUE_HUNTER_BOOTS, "Clue hunter boots", BOOTS)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Clue hunter boots - provides warmth")
                .build(),
                
            // Animal costume feet
            new WintertodtGearItem.Builder(ItemID.CHICKEN_FEET, "Chicken feet", BOOTS)
                .priority(680).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Chicken feet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.EVIL_CHICKEN_FEET, "Evil chicken feet", BOOTS)
                .priority(678).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Evil chicken feet - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BUNNY_FEET, "Bunny feet", BOOTS)
                .priority(675).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bunny feet - provides warmth")
                .build(),
                
            // Graceful gear (500-599) - Weight reduction but NO warmth
            new WintertodtGearItem.Builder(ItemID.GRACEFUL_BOOTS, "Graceful boots", BOOTS)
                .priority(500).category(WintertodtGearItem.GearCategory.GRACEFUL)
                .weight(-4).untradeable()
                .levelRequirement(Skill.AGILITY, 25)
                .description("Weight reduction - does NOT provide warmth")
                .build(),
                
            // High-level boots (300-399)
            new WintertodtGearItem.Builder(ItemID.PRIMORDIAL_BOOTS, "Primordial boots", BOOTS)
                .priority(380).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 75)
                .description("Best strength bonus boots - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.DRAGON_BOOTS, "Dragon boots", BOOTS)
                .priority(320).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 60)
                .build(),
                
            // Basic gear
            new WintertodtGearItem.Builder(ItemID.RUNE_BOOTS, "Rune boots", BOOTS)
                .priority(150).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.LEATHER_BOOTS, "Leather boots", BOOTS)
                .priority(20).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .build()
        );
    }
    
    private List<WintertodtGearItem> createHandsGear() {
        return Arrays.asList(
            // Pyromancer gear (Best)
            new WintertodtGearItem.Builder(ItemID.WARM_GLOVES, "Warm gloves", GLOVES)
                .priority(1000).category(WintertodtGearItem.GearCategory.PYROMANCER)
                .providesWarmth().untradeable()
                .description("Best gloves for Wintertodt")
                .build(),
                
            // Santa/Antisanta gloves
            new WintertodtGearItem.Builder(ItemID.SANTA_GLOVES, "Santa gloves", GLOVES)
                .priority(750).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Santa gloves - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.ANTISANTA_GLOVES, "Antisanta gloves", GLOVES)
                .priority(740).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Antisanta gloves - provides warmth")
                .build(),
                
            // Special warm gloves (720-730)
            new WintertodtGearItem.Builder(ItemID.GLOVES_OF_SILENCE, "Gloves of silence", GLOVES)
                .priority(725).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Gloves of silence - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FREMENNIK_GLOVES, "Fremennik gloves", GLOVES)
                .priority(720).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Fremennik gloves - provides warmth")
                .build(),
                
            // Colored gloves (700-719)
            new WintertodtGearItem.Builder(ItemID.RED_GLOVES, "Red gloves", GLOVES)
                .priority(710).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Red gloves - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.YELLOW_GLOVES, "Yellow gloves", GLOVES)
                .priority(708).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Yellow gloves - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.PURPLE_GLOVES, "Purple gloves", GLOVES)
                .priority(706).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Purple gloves - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.GREY_GLOVES, "Grey gloves", GLOVES)
                .priority(704).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Grey gloves - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.TEAL_GLOVES, "Teal gloves", GLOVES)
                .priority(702).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Teal gloves - provides warmth")
                .build(),
                
            // Clue hunter gear (680-699)
            new WintertodtGearItem.Builder(ItemID.CLUE_HUNTER_GLOVES, "Clue hunter gloves", GLOVES)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Clue hunter gloves - provides warmth")
                .build(),
                
            // Animal costume hands
            new WintertodtGearItem.Builder(ItemID.BUNNY_PAWS, "Bunny paws", GLOVES)
                .priority(680).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Bunny paws - provides warmth")
                .build(),
                
            // Graceful gear (500-599) - Weight reduction but NO warmth
            new WintertodtGearItem.Builder(ItemID.GRACEFUL_GLOVES, "Graceful gloves", GLOVES)
                .priority(500).category(WintertodtGearItem.GearCategory.GRACEFUL)
                .weight(-3).untradeable()
                .levelRequirement(Skill.AGILITY, 20)
                .description("Weight reduction - does NOT provide warmth")
                .build(),
                
            // High-level gloves (300-399)
            new WintertodtGearItem.Builder(ItemID.BARROWS_GLOVES, "Barrows gloves", GLOVES)
                .priority(380).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 25)
                .questRequirement("Recipe for Disaster")
                .untradeable()
                .description("Best overall gloves - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RUNE_GLOVES, "Rune gloves", GLOVES)
                .priority(300).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 25)
                .questRequirement("Recipe for Disaster")
                .untradeable()
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.LEATHER_GLOVES, "Leather gloves", GLOVES)
                .priority(20).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .build()
        );
    }
    
    private List<WintertodtGearItem> createWeaponGear() {
        return Arrays.asList(
            // Bruma torch (Best for Wintertodt)
            new WintertodtGearItem.Builder(ItemID.BRUMA_TORCH, "Bruma torch", WEAPON)
                .priority(950).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.FIREMAKING, 50)
                .hasSpecialEffect().untradeable().providesWarmth()
                .description("Acts as tinderbox, light source, and provides warmth.")
                .build(),
                
            // Infernal Axe (Excellent tool that provides warmth)
            new WintertodtGearItem.Builder(ItemID.INFERNAL_AXE, "Infernal axe", WEAPON)
                .priority(900).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 61)
                .levelRequirement(Skill.FIREMAKING, 85)
                .levelRequirement(Skill.ATTACK, 60)
                .hasSpecialEffect().untradeable().providesWarmth()
                .description("Burns logs while cutting, provides warmth.")
                .build(),
                
            // --- REGULAR AXES (Essential Tools - NO WARMTH) ---
            new WintertodtGearItem.Builder(ItemID.DRAGON_AXE, "Dragon axe", WEAPON)
                .priority(850).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 61)
                .levelRequirement(Skill.ATTACK, 60)
                .hasSpecialEffect()
                .description("Fastest axe, requires 60 Attack to wield.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.RUNE_AXE, "Rune axe", WEAPON)
                .priority(830).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 41)
                .levelRequirement(Skill.ATTACK, 40)
                .description("Good axe, requires 40 Attack to wield.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.ADAMANT_AXE, "Adamant axe", WEAPON)
                .priority(810).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 31)
                .levelRequirement(Skill.ATTACK, 30)
                .description("Decent axe, requires 30 Attack to wield.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.MITHRIL_AXE, "Mithril axe", WEAPON)
                .priority(800).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 21)
                .levelRequirement(Skill.ATTACK, 20)
                .description("Requires 20 Attack to wield.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.STEEL_AXE, "Steel axe", WEAPON)
                .priority(790).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 6)
                .levelRequirement(Skill.ATTACK, 5)
                .description("Requires 5 Attack to wield.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.IRON_AXE, "Iron axe", WEAPON)
                .priority(780).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 1)
                .levelRequirement(Skill.ATTACK, 1)
                .description("Basic axe, requires 1 Attack to wield.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.BRONZE_AXE, "Bronze axe", WEAPON)
                .priority(770).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 1)
                .description("Most basic axe, no Attack requirement.")
                .build(),

            // --- WARM WEAPONS (Lower priority than axes) ---
            new WintertodtGearItem.Builder(ItemID.INFERNAL_PICKAXE, "Infernal pickaxe", WEAPON)
                .priority(750).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .levelRequirement(Skill.MINING, 61)
                .levelRequirement(Skill.ATTACK, 60)
                .providesWarmth().untradeable()
                .description("Provides warmth, but is a pickaxe.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.LAVA_BATTLESTAFF, "Lava battlestaff", WEAPON)
                .priority(700).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.FIRE_BATTLESTAFF, "Fire battlestaff", WEAPON)
                .priority(698).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.STEAM_BATTLESTAFF, "Steam battlestaff", WEAPON)
                .priority(696).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.SMOKE_BATTLESTAFF, "Smoke battlestaff", WEAPON)
                .priority(694).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),
            
            new WintertodtGearItem.Builder(ItemID.MYSTIC_LAVA_STAFF, "Mystic lava staff", WEAPON)
                .priority(692).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),
            
            new WintertodtGearItem.Builder(ItemID.MYSTIC_FIRE_STAFF, "Mystic fire staff", WEAPON)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),
            
            new WintertodtGearItem.Builder(ItemID.MYSTIC_STEAM_STAFF, "Mystic steam staff", WEAPON)
                .priority(688).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.MYSTIC_SMOKE_STAFF, "Mystic smoke staff", WEAPON)
                .priority(686).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.STAFF_OF_FIRE, "Staff of fire", WEAPON)
                .priority(684).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),
            
            new WintertodtGearItem.Builder(ItemID.INFERNAL_HARPOON, "Infernal harpoon", WEAPON)
                .priority(680).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.VOLCANIC_ABYSSAL_WHIP, "Volcanic abyssal whip", WEAPON)
                .priority(670).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            new WintertodtGearItem.Builder(ItemID.ALE_OF_THE_GODS, "Ale of the gods", WEAPON)
                .priority(660).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Provides warmth.")
                .build(),

            // FASHIONSCAPE/UTILITY WEAPONS
            new WintertodtGearItem.Builder(ItemID.RUBBER_CHICKEN, "Rubber chicken", WEAPON)
                .priority(300).category(WintertodtGearItem.GearCategory.FASHIONSCAPE)
                .untradeable()
                .description("Funny fashionscape item")
                .build(),

            new WintertodtGearItem.Builder(ItemID.GOLDEN_TENCH, "Golden tench", WEAPON)
                .priority(290).category(WintertodtGearItem.GearCategory.FASHIONSCAPE)
                .description("Fish as weapon - pure fashionscape")
                .build(),

            // BASIC WEAPONS (Fallback)
            new WintertodtGearItem.Builder(ItemID.IRON_SCIMITAR, "Iron scimitar", WEAPON)
                .priority(80).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.ATTACK, 1)
                .description("Basic weapon if no axe available")
                .build(),

            new WintertodtGearItem.Builder(ItemID.BRONZE_SCIMITAR, "Bronze scimitar", WEAPON)
                .priority(60).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Most basic weapon")
                .build()
        );
    }
    
    private List<WintertodtGearItem> createShieldGear() {
        return Arrays.asList(
            // Bruma torch offhand (Best for Wintertodt)
            new WintertodtGearItem.Builder(ItemID.BRUMA_TORCH, "Bruma torch (offhand)", SHIELD)
                .priority(900).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.FIREMAKING, 50)
                .hasSpecialEffect().untradeable().providesWarmth()
                .description("Acts as both tinderbox and light source - provides warmth")
                .build(),
                
            // Tome of fire
            new WintertodtGearItem.Builder(ItemID.TOME_OF_FIRE, "Tome of fire", SHIELD)
                .priority(850).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .hasSpecialEffect().untradeable().providesWarmth()
                .description("Firemaking experience bonus - provides warmth")
                .build(),
                
            // Lit bug lantern
            new WintertodtGearItem.Builder(ItemID.LIT_BUG_LANTERN, "Lit bug lantern", SHIELD)
                .priority(820).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Lit bug lantern - provides warmth")
                .build(),
                
            // High-level shields
            new WintertodtGearItem.Builder(ItemID.DRAGON_DEFENDER, "Dragon defender", SHIELD)
                .priority(380).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 60)
                .untradeable()
                .description("Best defender - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RUNE_DEFENDER, "Rune defender", SHIELD)
                .priority(150).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .untradeable()
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RUNE_KITESHIELD, "Rune kiteshield", SHIELD)
                .priority(140).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.DEFENCE, 40)
                .build()
        );
    }
    
    private List<WintertodtGearItem> createNeckGear() {
        return Arrays.asList(
            // Warm scarves (700-750)
            new WintertodtGearItem.Builder(ItemID.WOOLLY_SCARF, "Woolly scarf", AMULET)
                .priority(730).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Woolly scarf - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BOBBLE_SCARF, "Bobble scarf", AMULET)
                .priority(725).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Bobble scarf - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RAINBOW_SCARF, "Rainbow scarf", AMULET)
                .priority(720).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Rainbow scarf - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.GNOME_SCARF, "Gnome scarf", AMULET)
                .priority(715).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Gnome scarf - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.JESTER_SCARF, "Jester scarf", AMULET)
                .priority(710).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Jester scarf - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.TRIJESTER_SCARF, "Tri-jester scarf", AMULET)
                .priority(708).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Tri-jester scarf - provides warmth")
                .build(),
                
            // High-level amulets
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_FURY, "Amulet of fury", AMULET)
                .priority(400).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Excellent all-around stats")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_GLORY4, "Amulet of glory(4)", AMULET)
                .priority(350).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .description("Teleportation and good stats")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_GLORY3, "Amulet of glory(3)", AMULET)
                .priority(345).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_GLORY2, "Amulet of glory(2)", AMULET)
                .priority(340).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_GLORY1, "Amulet of glory(1)", AMULET)
                .priority(335).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_POWER, "Amulet of power", AMULET)
                .priority(250).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.MAGIC, 50)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.AMULET_OF_STRENGTH, "Amulet of strength", AMULET)
                .priority(150).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .levelRequirement(Skill.MAGIC, 49)
                .build()
        );
    }
    
    private List<WintertodtGearItem> createRingGear() {
        return Arrays.asList(
            // Ring of the elements (warm ring)
            new WintertodtGearItem.Builder(ItemID.RING_OF_THE_ELEMENTS, "Ring of the elements", RING)
                .priority(800).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Ring of the elements - provides warmth")
                .build(),
                
            // Utility rings (400-500)
            new WintertodtGearItem.Builder(ItemID.RING_OF_DUELING8, "Ring of dueling(8)", RING)
                .priority(450).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .description("Useful teleportations - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RING_OF_DUELING7, "Ring of dueling(7)", RING)
                .priority(445).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.GAMES_NECKLACE8, "Games necklace(8)", RING)
                .priority(440).category(WintertodtGearItem.GearCategory.UTILITY)
                .hasSpecialEffect()
                .description("Wintertodt teleport - no warmth")
                .build(),
                
            // Combat rings (300-399)
            new WintertodtGearItem.Builder(ItemID.BERSERKER_RING, "Berserker ring", RING)
                .priority(380).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Strength bonus - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.WARRIOR_RING, "Warrior ring", RING)
                .priority(350).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .description("Attack bonus - no warmth")
                .build()
        );
    }
    
    private List<WintertodtGearItem> createCapeGear() {
        return Arrays.asList(
            // Fire-themed capes (850-899) - All provide warmth
            new WintertodtGearItem.Builder(ItemID.INFERNAL_CAPE, "Infernal cape", CAPE)
                .priority(890).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .untradeable().providesWarmth()
                .description("Infernal cape - provides warmth and excellent stats")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.INFERNAL_MAX_CAPE, "Infernal max cape", CAPE)
                .priority(885).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .untradeable().providesWarmth()
                .description("Infernal max cape - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FIRE_CAPE, "Fire cape", CAPE)
                .priority(880).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .untradeable().providesWarmth()
                .description("Fire cape - provides warmth and excellent melee bonuses")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FIRE_MAX_CAPE, "Fire max cape", CAPE)
                .priority(875).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .untradeable().providesWarmth()
                .description("Fire max cape - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.OBSIDIAN_CAPE, "Obsidian cape", CAPE)
                .priority(870).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Obsidian cape - provides warmth")
                .build(),
                
            // Max capes (860-869) - All provide warmth
            new WintertodtGearItem.Builder(ItemID.MAX_CAPE, "Max cape", CAPE)
                .priority(865).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .untradeable().providesWarmth()
                .description("Max cape - provides warmth")
                .build(),
                
            // Skill capes (800-859)
            new WintertodtGearItem.Builder(ItemID.FIREMAKING_CAPE, "Firemaking cape", CAPE)
                .priority(850).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.FIREMAKING, 99)
                .untradeable().providesWarmth()
                .description("Firemaking cape - provides warmth, perfect for Wintertodt")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.WOODCUTTING_CAPE, "Woodcutting cape", CAPE)
                .priority(830).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.WOODCUTTING, 99)
                .untradeable()
                .description("Useful for Wintertodt - no warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.FLETCHING_CAPE, "Fletching cape", CAPE)
                .priority(820).category(WintertodtGearItem.GearCategory.SKILL_GEAR)
                .levelRequirement(Skill.FLETCHING, 99)
                .untradeable()
                .description("Useful for Wintertodt - no warmth")
                .build(),
                
            // Special warm capes (750-799)
            new WintertodtGearItem.Builder(ItemID.WOLF_CLOAK, "Wolf cloak", CAPE)
                .priority(780).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Wolf cloak - provides warmth")
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.RAINBOW_CAPE, "Rainbow cape", CAPE)
                .priority(770).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth()
                .description("Rainbow cape - provides warmth")
                .build(),
                
            // Clue hunter cloak
            new WintertodtGearItem.Builder(ItemID.CLUE_HUNTER_CLOAK, "Clue hunter cloak", CAPE)
                .priority(690).category(WintertodtGearItem.GearCategory.WARM_GEAR)
                .providesWarmth().untradeable()
                .description("Clue hunter cloak - provides warmth")
                .build(),
                
            // Graceful cape (500-599) - Weight reduction but NO warmth
            new WintertodtGearItem.Builder(ItemID.GRACEFUL_CAPE, "Graceful cape", CAPE)
                .priority(500).category(WintertodtGearItem.GearCategory.GRACEFUL)
                .weight(-4).untradeable()
                .levelRequirement(Skill.AGILITY, 15)
                .description("Weight reduction - does NOT provide warmth")
                .build(),
                
            // Basic capes (50-200)
            new WintertodtGearItem.Builder(ItemID.PINK_CAPE, "Pink cape", CAPE)
                .priority(50).category(WintertodtGearItem.GearCategory.FASHIONSCAPE)
                .build(),
                
            new WintertodtGearItem.Builder(ItemID.BLACK_CAPE, "Black cape", CAPE)
                .priority(40).category(WintertodtGearItem.GearCategory.COMBAT_GEAR)
                .build()
        );
    }
    
    /**
     * Gets all gear items for a specific equipment slot.
     */
    public List<WintertodtGearItem> getGearForSlot(EquipmentInventorySlot slot) {
        return gearBySlot.getOrDefault(slot, new ArrayList<>());
    }
    
    /**
     * Gets all gear items in the database.
     */
    public List<WintertodtGearItem> getAllGearItems() {
        return new ArrayList<>(allGearItems);
    }
    
    /**
     * Finds a specific gear item by ID.
     */
    public WintertodtGearItem findGearItemById(int itemId) {
        return allGearItems.stream()
                .filter(item -> item.getItemId() == itemId)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Gets gear items filtered by category.
     */
    public List<WintertodtGearItem> getGearByCategory(WintertodtGearItem.GearCategory category) {
        return allGearItems.stream()
                .filter(item -> item.getCategory() == category)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets the total number of gear items in the database.
     */
    public int getTotalGearCount() {
        return allGearItems.size();
    }
} 