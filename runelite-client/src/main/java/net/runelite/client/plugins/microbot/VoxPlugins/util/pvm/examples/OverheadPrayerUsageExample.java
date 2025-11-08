package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.examples;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.enums.CombatStyle;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2OverheadPrayerHandler;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2WeaponSwitchHandler;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

import java.util.Arrays;
import java.util.List;

/**
 * example showing how to use overhead prayer detection and weapon switching
 * this is a reference implementation for plugin developers
 */
@Slf4j
public class OverheadPrayerUsageExample {

    /**
     * initialize weapon switching system with gear presets
     * call this once during plugin startup
     */
    public void setupWeaponSwitching() {
        Rs2WeaponSwitchHandler switchHandler = Rs2WeaponSwitchHandler.getInstance();
        
        // register gear presets (items to equip for each style)
        switchHandler.registerGearPreset("melee", Arrays.asList(
            ItemID.ABYSSAL_WHIP,
            22322 // Dragon defender
        ));
        
        switchHandler.registerGearPreset("ranged", Arrays.asList(
            ItemID.TOXIC_BLOWPIPE
        ));
        
        switchHandler.registerGearPreset("mage", Arrays.asList(
            12899, // Trident of the swamp
            11814 // Arcane spirit shield
        ));
        
        // map combat styles to gear presets
        switchHandler.registerStylePreset(CombatStyle.MELEE, "melee");
        switchHandler.registerStylePreset(CombatStyle.RANGED, "ranged");
        switchHandler.registerStylePreset(CombatStyle.MAGIC, "mage");
        
        // map weapon IDs to combat styles (for effectiveness checking)
        switchHandler.registerWeaponStyle(ItemID.ABYSSAL_WHIP, CombatStyle.MELEE);
        switchHandler.registerWeaponStyle(ItemID.TOXIC_BLOWPIPE, CombatStyle.RANGED);
        switchHandler.registerWeaponStyle(12899, CombatStyle.MAGIC); // Trident of the swamp
        
        log.info("Weapon switching system initialized");
    }

    /**
     * example: detect overhead prayer and log information
     */
    public void detectPrayerExample(NPC target) {
        Rs2OverheadPrayerHandler prayerHandler = Rs2OverheadPrayerHandler.getInstance();
        
        // check if NPC has overhead prayer
        if (!prayerHandler.hasOverheadPrayer(target)) {
            log.info("NPC has no overhead prayer");
            return;
        }
        
        // get the prayer
        HeadIcon prayer = prayerHandler.getOverheadPrayer(target);
        if (prayer == null) {
            log.warn("Failed to detect prayer");
            return;
        }
        
        // log prayer information
        log.info("NPC is praying: {}", prayerHandler.getPrayerName(prayer));
        log.info("Short name: {}", prayerHandler.getPrayerShortName(prayer));
        
        // get unprotected styles
        List<CombatStyle> unprotected = prayerHandler.getUnprotectedStyles(prayer);
        log.info("NPC is vulnerable to: {}", unprotected);
        
        // check specific style protection
        boolean protectingMelee = prayerHandler.isPrayingAgainst(target, CombatStyle.MELEE);
        log.info("Protecting against melee: {}", protectingMelee);
    }

    /**
     * example: automatically switch weapon based on prayer
     */
    public void autoSwitchWeaponExample(NPC target) {
        Rs2WeaponSwitchHandler switchHandler = Rs2WeaponSwitchHandler.getInstance();
        Rs2OverheadPrayerHandler prayerHandler = Rs2OverheadPrayerHandler.getInstance();
        
        // check if current weapon is effective
        if (switchHandler.isUsingEffectiveStyle(target)) {
            log.debug("Current weapon is effective, no switch needed");
            return;
        }
        
        // get prayer for logging
        HeadIcon prayer = prayerHandler.getOverheadPrayer(target);
        log.info("NPC praying {}, switching weapon", 
            prayerHandler.getPrayerShortName(prayer));
        
        // automatically switch to counter the prayer
        boolean success = switchHandler.switchToCounterPrayer(target);
        
        if (success) {
            log.info("Successfully switched weapon");
        } else {
            log.warn("Failed to switch weapon (missing gear?)");
        }
    }

    /**
     * example: combat loop with automatic weapon switching
     */
    public void combatLoopExample() {
        Rs2WeaponSwitchHandler switchHandler = Rs2WeaponSwitchHandler.getInstance();
        Rs2OverheadPrayerHandler prayerHandler = Rs2OverheadPrayerHandler.getInstance();
        
        // get target NPC
        NPC target = Rs2Npc.getNpc("Demonic Gorilla");
        if (target == null) {
            log.debug("Target not found");
            return;
        }
        
        // check if we're currently switching weapon (1-tick delay)
        if (switchHandler.isSwitchingWeapon()) {
            log.debug("Waiting for weapon switch to complete");
            return;
        }
        
        // check for prayer and switch if needed
        if (prayerHandler.hasOverheadPrayer(target)) {
            // check prayer change timing
            int ticksSinceChange = prayerHandler.getTicksSincePrayerChange(target);
            
            // react to recent prayer changes
            if (ticksSinceChange <= 1) {
                log.info("Prayer changed {} ticks ago, switching weapon", ticksSinceChange);
                switchHandler.switchToCounterPrayer(target);
                return;
            }
            
            // check if current weapon is still effective
            if (!switchHandler.isUsingEffectiveStyle(target)) {
                log.info("Current weapon no longer effective");
                switchHandler.switchToCounterPrayer(target);
                return;
            }
        }
        
        // continue with combat actions...
        log.debug("Weapon is effective, continuing combat");
    }

    /**
     * example: custom NPC ID mapping for special bosses
     */
    public void registerCustomBossExample() {
        Rs2OverheadPrayerHandler prayerHandler = Rs2OverheadPrayerHandler.getInstance();
        
        // register custom boss that changes ID with prayers
        prayerHandler.registerNpcIdMapping(12345, HeadIcon.MELEE);
        prayerHandler.registerNpcIdMapping(12346, HeadIcon.RANGED);
        prayerHandler.registerNpcIdMapping(12347, HeadIcon.MAGIC);
        
        log.info("Registered custom boss prayer mappings");
    }

    /**
     * example: gauntlet corrupted hunllef setup
     */
    public void setupGauntletExample() {
        Rs2WeaponSwitchHandler switchHandler = Rs2WeaponSwitchHandler.getInstance();
        
        // hunllef uses NPC ID changes (pre-registered)
        // IDs: 9021 (melee), 9022 (ranged), 9023 (magic)
        
        // register gauntlet gear
        switchHandler.registerGearPreset("gauntlet_melee", Arrays.asList(
            ItemID.CRYSTAL_HALBERD
        ));
        switchHandler.registerGearPreset("gauntlet_ranged", Arrays.asList(
            ItemID.CRYSTAL_BOW
        ));
        switchHandler.registerGearPreset("gauntlet_mage", Arrays.asList(
            23990 // Crystal staff
        ));
        
        // map styles
        switchHandler.registerStylePreset(CombatStyle.MELEE, "gauntlet_melee");
        switchHandler.registerStylePreset(CombatStyle.RANGED, "gauntlet_ranged");
        switchHandler.registerStylePreset(CombatStyle.MAGIC, "gauntlet_mage");
        
        // map weapons
        switchHandler.registerWeaponStyle(ItemID.CRYSTAL_HALBERD, CombatStyle.MELEE);
        switchHandler.registerWeaponStyle(ItemID.CRYSTAL_BOW, CombatStyle.RANGED);
        switchHandler.registerWeaponStyle(23990, CombatStyle.MAGIC); // Crystal staff
        
        log.info("Gauntlet weapon switching configured");
    }

    /**
     * example: cleanup on plugin shutdown
     */
    public void cleanup() {
        Rs2OverheadPrayerHandler prayerHandler = Rs2OverheadPrayerHandler.getInstance();
        Rs2WeaponSwitchHandler switchHandler = Rs2WeaponSwitchHandler.getInstance();
        
        // clear cached data
        prayerHandler.clearAll();
        switchHandler.clear();
        
        log.info("Overhead prayer system cleaned up");
    }
}
