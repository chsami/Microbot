package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.overlay;

import lombok.Getter;
import lombok.Setter;

import java.awt.*;

import com.google.inject.Singleton;

/**
 * singleton configuration for PvM overlay system
 * allows runtime modification of overlay settings
 */
@Getter
@Setter
@Singleton
public class Rs2PvMOverlayConfig {
    
    private static Rs2PvMOverlayConfig instance;
    
    // ========== GENERAL SETTINGS ==========
    
    private boolean enableOverlay = true;
    private boolean showInfoPanel = true;
    
    // ========== PROJECTILE TRACKING ==========
    
    private boolean showProjectiles = true;
    private ProjectileStyle projectileStyle = ProjectileStyle.TARGET_TILE;
    private boolean showProjectileInfo = true;
    private Color aoeProjectileColor = new Color(255, 100, 0, 180); // orange
    private Color playerProjectileColor = new Color(200, 0, 255, 180); // purple
    private int projectileOutlineWidth = 2;
    
    // ========== HAZARD TRACKING ==========
    
    private boolean showHazards = true;
    private boolean showSafeTiles = false;
    private Color hazardColor = new Color(255, 0, 0, 120); // red
    private Color safeTileColor = new Color(0, 255, 0, 100); // green
    private int hazardOutlineWidth = 2;
    
    // ========== ACTION TRACKING ==========
    
    private boolean showActionQueue = true;
    private boolean showActionHistory = false;
    private int maxHistoryEntries = 5;
    
    // ========== PRAYER TRACKING ==========
    
    private boolean showPrayerSuggestion = true;
    private boolean highlightActivePrayer = true;
    private int prayerTextSize = 16;
    
    // ========== TICK LOSS TRACKING ==========
    
    private boolean showTickLoss = true;
    private boolean warnOnTickLoss = false;
    
    // ========== SINGLETON PATTERN ==========
    
    private Rs2PvMOverlayConfig() {
        // private constructor
    }
    
    /**
     * get singleton instance
     */
    public static Rs2PvMOverlayConfig getInstance() {
        if (instance == null) {
            instance = new Rs2PvMOverlayConfig();
        }
        return instance;
    }
    
    /**
     * reset all settings to defaults
     */
    public void resetToDefaults() {
        enableOverlay = true;
        showInfoPanel = true;
        
        showProjectiles = true;
        projectileStyle = ProjectileStyle.TARGET_TILE;
        showProjectileInfo = true;
        aoeProjectileColor = new Color(255, 100, 0, 180);
        playerProjectileColor = new Color(200, 0, 255, 180);
        projectileOutlineWidth = 2;
        
        showHazards = true;
        showSafeTiles = false;
        hazardColor = new Color(255, 0, 0, 120);
        safeTileColor = new Color(0, 255, 0, 100);
        hazardOutlineWidth = 2;
        
        showActionQueue = true;
        showActionHistory = false;
        maxHistoryEntries = 5;
        
        showPrayerSuggestion = true;
        highlightActivePrayer = true;
        prayerTextSize = 16;
        
        showTickLoss = true;
        warnOnTickLoss = false;
    }
    
    // ========== ENUMS ==========
    
    public enum ProjectileStyle {
        TARGET_TILE("Target tile only"),
        FULL_PATH("Full trajectory path"),
        CURRENT_POSITION("Current position");
        
        private final String name;
        
        ProjectileStyle(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
}
