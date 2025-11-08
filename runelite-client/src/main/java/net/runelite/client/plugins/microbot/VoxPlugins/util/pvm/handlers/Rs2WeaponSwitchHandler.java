package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.enums.CombatStyle;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import javax.inject.Singleton;
import java.util.*;

/**
 * thread-safe handler for weapon switching and gear management
 * tracks weapon changes to detect tick loss from switching
 * supports gear presets for quick style changes
 * integrates with overhead prayer detection for automated weapon switching
 */
@Slf4j
@Singleton
public class Rs2WeaponSwitchHandler {

    private static Rs2WeaponSwitchHandler instance;

    // track last weapon switch timing
    private volatile int lastWeaponSwitchTick = -1;
    private volatile int lastWeaponId = -1;

    // gear presets (e.g., "melee", "ranged", "mage")
    private final Map<String, GearPreset> gearPresets = new HashMap<>();
    
    // combat style to gear preset mapping
    private final Map<CombatStyle, String> styleToPreset = new HashMap<>();
    
    // weapon ID to combat style mapping
    private final Map<Integer, CombatStyle> weaponToCombatStyle = new HashMap<>();

    private Rs2WeaponSwitchHandler() {
    }

    public static synchronized Rs2WeaponSwitchHandler getInstance() {
        if (instance == null) {
            instance = new Rs2WeaponSwitchHandler();
        }
        return instance;
    }

    /**
     * switch to weapon by item ID
     * returns true if already equipped or successfully switched
     */
    public boolean switchToWeapon(int weaponId) {
        // check if already equipped
        if (Rs2Equipment.isWearing(weaponId)) {
            return true;
        }

        // check if in inventory
        if (!Rs2Inventory.hasItem(weaponId)) {
            log.warn("Weapon {} not in inventory", weaponId);
            return false;
        }

        // equip weapon
        boolean success = Rs2Inventory.wield(weaponId);
        if (success) {
            lastWeaponSwitchTick = Microbot.getClient().getTickCount();
            lastWeaponId = weaponId;
            log.debug("Switched to weapon: {}", weaponId);
        }

        return success;
    }

    /**
     * switch to gear preset by name
     * equips all items in preset that are in inventory
     */
    public boolean switchToGearPreset(String presetName) {
        GearPreset preset = gearPresets.get(presetName.toLowerCase());
        if (preset == null) {
            log.warn("Gear preset '{}' not found", presetName);
            return false;
        }
        if(Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY){
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        }
        boolean allEquipped = true;

        // equip each item in preset
        for (int itemId : preset.getItemIds()) {
            if (!Rs2Equipment.isWearing(itemId)) {
                if (Rs2Inventory.hasItem(itemId)) {
                    boolean equipped = Rs2Inventory.wield(itemId);
                    if (!equipped) {
                        log.warn("Failed to equip item {} from preset '{}'", itemId, presetName);
                        allEquipped = false;
                    }
                } else {
                    log.debug("Item {} from preset '{}' not in inventory", itemId, presetName);
                    allEquipped = false;
                }
            }
        }

        if (allEquipped) {
            lastWeaponSwitchTick = Microbot.getClient().getTickCount();
            log.debug("Switched to gear preset: {}", presetName);
        }

        return allEquipped;
    }

    /**
     * check if currently switching weapon (within 1 tick)
     * weapon switching causes 1-tick delay
     */
    public boolean isSwitchingWeapon() {
        if (lastWeaponSwitchTick < 0) {
            return false;
        }

        int currentTick = Microbot.getClient().getTickCount();
        int ticksSinceSwitch = currentTick - lastWeaponSwitchTick;

        // weapon switch causes 1-tick delay
        return ticksSinceSwitch <= 1;
    }

    /**
     * get ticks since last weapon switch
     */
    public int getTicksSinceWeaponSwitch() {
        if (lastWeaponSwitchTick < 0) {
            return Integer.MAX_VALUE;
        }

        int currentTick = Microbot.getClient().getTickCount();
        return currentTick - lastWeaponSwitchTick;
    }

    /**
     * get last weapon ID that was switched to
     */
    public Optional<Integer> getLastWeaponId() {
        if (lastWeaponId < 0) {
            return Optional.empty();
        }
        return Optional.of(lastWeaponId);
    }

    /**
     * register gear preset for quick switching
     */
    public void registerGearPreset(String name, List<Integer> itemIds) {
        GearPreset preset = new GearPreset(name, itemIds);
        gearPresets.put(name.toLowerCase(), preset);
        log.debug("Registered gear preset '{}' with {} items", name, itemIds.size());
    }

    /**
     * check if gear preset exists
     */
    public boolean hasGearPreset(String name) {
        return gearPresets.containsKey(name.toLowerCase());
    }

    /**
     * get current weapon equipped
     */
    public Optional<Integer> getCurrentWeapon() {
        int weaponId = Rs2Equipment.get(EquipmentInventorySlot.WEAPON).getId();
        if (weaponId > 0) {
            return Optional.of(weaponId);
        }
        return Optional.empty();
    }

    /**
     * check if specific weapon is equipped
     */
    public boolean isWeaponEquipped(int weaponId) {
        return Rs2Equipment.isWearing(weaponId);
    }

    /**
     * clear all tracking data
     */
    public void clear() {
        lastWeaponSwitchTick = -1;
        lastWeaponId = -1;
        log.debug("Cleared weapon switch tracking");
    }
    
    /**
     * register weapon to combat style mapping
     * helps determine what style a weapon uses
     */
    public void registerWeaponStyle(int weaponId, CombatStyle style) {
        weaponToCombatStyle.put(weaponId, style);
    }
    
    /**
     * register combat style to gear preset mapping
     * enables automatic gear switching based on overhead prayers
     */
    public void registerStylePreset(CombatStyle style, String presetName) {
        styleToPreset.put(style, presetName);
        log.debug("Registered style {} -> preset '{}'", style, presetName);
    }
    
    /**
     * switch weapon to counter NPC's overhead prayer
     * returns true if switched or already using effective style
     */
    public boolean switchToCounterPrayer(NPC npc) {
        Rs2OverheadPrayerHandler prayerHandler = Rs2OverheadPrayerHandler.getInstance();
        HeadIcon prayer = prayerHandler.getOverheadPrayer(npc);
        
        if (prayer == null) {
            log.debug("No overhead prayer detected on NPC");
            return false;
        }
        
        // get unprotected combat styles
        List<CombatStyle> unprotectedStyles = prayerHandler.getUnprotectedStyles(prayer);
        
        if (unprotectedStyles.isEmpty()) {
            log.warn("NPC is protecting against all combat styles");
            return false;
        }
        
        // find best style based on player's levels
        CombatStyle bestStyle = selectBestCombatStyle(unprotectedStyles);
        
        log.debug("NPC has {} prayer, switching to {} (unprotected: {})", 
            prayerHandler.getPrayerShortName(prayer), bestStyle, unprotectedStyles);
        
        // switch to corresponding gear preset
        String presetName = styleToPreset.get(bestStyle);
        if (presetName != null && hasGearPreset(presetName)) {
            return switchToGearPreset(presetName);
        }
        
        log.warn("No gear preset registered for style: {}", bestStyle);
        return false;
    }
    
    /**
     * select best combat style from available options
     * prioritizes player's highest combat levels
     */
    private CombatStyle selectBestCombatStyle(List<CombatStyle> availableStyles) {
        if (availableStyles.isEmpty()) {
            return CombatStyle.MELEE; // default
        }
        
        if (availableStyles.size() == 1) {
            return availableStyles.get(0);
        }
        
        // calculate scores for each style based on player levels
        Map<CombatStyle, Integer> styleScores = new HashMap<>();
        
        for (CombatStyle style : availableStyles) {
            int score = calculateStyleScore(style);
            styleScores.put(style, score);
        }
        
        // return style with highest score
        return styleScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(availableStyles.get(0));
    }
    
    /**
     * calculate combat style score based on player's levels
     * higher levels = higher score
     */
    private int calculateStyleScore(CombatStyle style) {
        switch (style) {
            case MELEE:
                // average of attack, strength, defence
                int attack = Rs2Player.getRealSkillLevel(Skill.ATTACK);
                int strength = Rs2Player.getRealSkillLevel(Skill.STRENGTH);
                int defence = Rs2Player.getRealSkillLevel(Skill.DEFENCE);
                return (attack + strength + defence) / 3;
                
            case RANGED:
                return Rs2Player.getRealSkillLevel(Skill.RANGED);
                
            case MAGIC:
                return Rs2Player.getRealSkillLevel(Skill.MAGIC);
                
            default:
                return 1;
        }
    }
    
    /**
     * get current combat style based on equipped weapon
     * returns null if weapon style not registered
     */
    public CombatStyle getCurrentCombatStyle() {
        Optional<Integer> weapon = getCurrentWeapon();
        if (!weapon.isPresent()) {
            return null;
        }
        
        return weaponToCombatStyle.get(weapon.get());
    }
    
    /**
     * check if currently using effective combat style against NPC
     * returns true if NPC isn't protecting against our style
     */
    public boolean isUsingEffectiveStyle(NPC npc) {
        CombatStyle currentStyle = getCurrentCombatStyle();
        if (currentStyle == null) {
            return true; // unknown style, assume effective
        }
        
        Rs2OverheadPrayerHandler prayerHandler = Rs2OverheadPrayerHandler.getInstance();
        return !prayerHandler.isPrayingAgainst(npc, currentStyle);
    }

    /**
     * immutable gear preset definition
     */
    private static class GearPreset {
        private final String name;
        private final List<Integer> itemIds;

        GearPreset(String name, List<Integer> itemIds) {
            this.name = name;
            this.itemIds = List.copyOf(itemIds); // immutable copy
        }

        public String getName() {
            return name;
        }

        public List<Integer> getItemIds() {
            return itemIds;
        }
    }
}
