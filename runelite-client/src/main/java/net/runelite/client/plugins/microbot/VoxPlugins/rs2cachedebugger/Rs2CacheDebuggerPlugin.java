package net.runelite.client.plugins.microbot.VoxPlugins.rs2cachedebugger;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.cache.*;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2ObjectModel;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;

/**
 * Rs2 Cache Debugger Plugin providing comprehensive cache debugging and entity information overlays.
 * Features configurable overlays for NPCs, objects, and ground items with preset filters,
 * custom colors, render styles, detailed cache statistics, and performance monitoring.
 * 
 * This plugin focuses on debugging and visualizing the Rs2Cache system for NPCs, Objects, and Ground Items.
 * 
 * @author Vox
 * @version 3.0 - Cache Debugging Focus
 */
@PluginDescriptor(
        name = "Rs2 Cache Debugger",
        description = "Debug and visualize Rs2Cache system with configurable overlays, cache statistics, and performance monitoring",
        tags = {"cache", "debug", "overlay", "npc", "object", "ground items", "performance", "vox"}
)
@Slf4j
public class Rs2CacheDebuggerPlugin extends Plugin {
    
    @Inject
    private Rs2CacheDebuggerConfig config;
    
    @Inject
    private OverlayManager overlayManager;
    
    @Inject
    private KeyManager keyManager;
    
    @Inject
    private Rs2CacheDebuggerNpcOverlay npcOverlay;
    
    @Inject
    private Rs2CacheDebuggerObjectOverlay objectOverlay;
    
    @Inject
    private Rs2CacheDebuggerGroundItemOverlay groundItemOverlay;
    
    @Inject
    private Rs2CacheDebuggerInfoPanel infoPanel;
    
    // State tracking
    private boolean npcOverlayEnabled = false;
    private boolean objectOverlayEnabled = false;
    private boolean groundItemOverlayEnabled = false;
    private boolean infoPanelEnabled = false;
    
   
    
    // Performance tracking
    private int tickCounter = 0;
    private long lastCacheStatsLogTime = 0;
    
    // Hotkey listeners
    private final HotkeyListener toggleNpcOverlayListener = new HotkeyListener(() -> config.toggleNpcOverlayHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleNpcOverlay();
        }
    };
    
    private final HotkeyListener toggleObjectOverlayListener = new HotkeyListener(() -> config.toggleObjectOverlayHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleObjectOverlay();
        }
    };
    
    private final HotkeyListener toggleGroundItemOverlayListener = new HotkeyListener(() -> config.toggleGroundItemOverlayHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleGroundItemOverlay();
        }
    };
    
    private final HotkeyListener logCacheInfoListener = new HotkeyListener(() -> config.logCacheInfoHotkey()) {
        @Override
        public void hotkeyPressed() {
            logDetailedCacheInfo();
        }
    };
    
    private final HotkeyListener toggleInfoPanelListener = new HotkeyListener(() -> config.toggleInfoPanelHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleInfoPanel();
        }
    };
    
    @Provides
    Rs2CacheDebuggerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(Rs2CacheDebuggerConfig.class);
    }
    
    @Override
    protected void startUp() throws Exception {
        log.info("Starting Rs2 Cache Debugger Plugin");
        
      
        
        // Configure overlays with config
        configureOverlays();
        
        // Register hotkey listeners
        keyManager.registerKeyListener(toggleNpcOverlayListener);
        keyManager.registerKeyListener(toggleObjectOverlayListener);
        keyManager.registerKeyListener(toggleGroundItemOverlayListener);
        keyManager.registerKeyListener(logCacheInfoListener);
        keyManager.registerKeyListener(toggleInfoPanelListener);
        
        // Enable overlays based on config
        if (config.enableNpcOverlay()) {
            enableNpcOverlay();
        }
        
        if (config.enableObjectOverlay()) {
            enableObjectOverlay();
        }
        
        if (config.enableGroundItemOverlay()) {
            enableGroundItemOverlay();
        }
        
        if (config.enableInfoPanel()) {
            enableInfoPanel();
        }
        
        // Reset performance counters
        tickCounter = 0;
        lastCacheStatsLogTime = System.currentTimeMillis();
        
        log.info("Rs2 Cache Debugger Plugin started successfully");
    }
    
    @Override
    protected void shutDown() throws Exception {
        log.info("Shutting down Rs2 Cache Debugger Plugin");
        
        // Unregister hotkey listeners
        keyManager.unregisterKeyListener(toggleNpcOverlayListener);
        keyManager.unregisterKeyListener(toggleObjectOverlayListener);
        keyManager.unregisterKeyListener(toggleGroundItemOverlayListener);
        keyManager.unregisterKeyListener(logCacheInfoListener);
        keyManager.unregisterKeyListener(toggleInfoPanelListener);
        
        // Disable all overlays
        disableNpcOverlay();
        disableObjectOverlay();
        disableGroundItemOverlay();
        disableInfoPanel();
        
        log.info("Rs2 Cache Debugger Plugin shut down successfully");
    }
    
    /**
     * Configure overlays with current config settings
     */
    private void configureOverlays() {
        // Configure NPC overlay
        npcOverlay.setConfig(config);
        npcOverlay.setRenderFilter(createNpcFilter());
        
        // Configure Object overlay
        objectOverlay.setConfig(config);
        objectOverlay.setRenderFilter(createObjectFilter());
        
        // Configure Ground Item overlay
        groundItemOverlay.setConfig(config);
        groundItemOverlay.setRenderFilter(createGroundItemFilter());
        
        // Configure Info Panel
        infoPanel.setConfig(config);
    }
    
    /**
     * Create NPC filter based on config
     */
    private java.util.function.Predicate<net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel> createNpcFilter() {
        NpcFilterPreset preset = config.npcFilterPreset();
        String customFilter = config.npcCustomFilter();
        
        return npcModel -> {
            if (npcModel == null) {
                return false;
            }
            
            // Apply preset filter
            if (!preset.test(npcModel)) {
                return false;
            }
            
            // Apply custom filter if specified
            if (customFilter != null && !customFilter.trim().isEmpty()) {
                String npcName = npcModel.getName();
                if (npcName == null) {
                    return false;
                }
                return npcName.toLowerCase().contains(customFilter.toLowerCase());
            }
            
            return true;
        };
    }
    
    /**
     * Create Object filter based on config
     */
    private java.util.function.Predicate<Rs2ObjectModel> createObjectFilter() {
        ObjectFilterPreset preset = config.objectFilterPreset();
        String customFilter = config.objectCustomFilter();
        
        return objectModel -> {
            if (objectModel == null || objectModel.getTileObject() == null) {
                return false;
            }
            
            // Check object type visibility settings
            switch (objectModel.getObjectType()) {
                case GAME_OBJECT:
                    if (!config.showGameObjects()) {
                        return false;
                    }
                    break;
                case WALL_OBJECT:
                    if (!config.showWallObjects()) {
                        return false;
                    }
                    break;
                case DECORATIVE_OBJECT:
                    if (!config.showDecorativeObjects()) {
                        return false;
                    }
                    break;
                case GROUND_OBJECT:
                    if (!config.showGroundObjects()) {
                        return false;
                    }
                    break;
                default:
                    return false; // Unknown object type
            }
            
            // Apply distance filtering
            int maxDistance = config.objectMaxDistance();
            if (objectModel.getDistanceFromPlayer() > maxDistance) {
                return false;
            }
            
            // Apply preset filter
            if (!preset.test(objectModel)) {
                return false;
            }
            
            // Apply custom filter if specified
            if (customFilter != null && !customFilter.trim().isEmpty()) {
                // Check object name or ID
                String objectName = objectModel.getName();
                if (objectName == null) {
                    return false;
                }
                return objectName.toLowerCase().contains(customFilter.toLowerCase());
            }
            
            return true;
        };
    }
    
    /**
     * Create Ground Item filter based on config
     */
    private java.util.function.Predicate<net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItemModel> createGroundItemFilter() {
        GroundItemFilterPreset preset = config.groundItemFilterPreset();
        String customFilter = config.groundItemCustomFilter();
        
        return itemModel -> {
            if (itemModel == null) {
                return false;
            }
            
            // Apply preset filter
            if (!preset.test(itemModel)) {
                return false;
            }
            
            // Apply custom filter if specified
            if (customFilter != null && !customFilter.trim().isEmpty()) {
                String itemName = itemModel.getName();
                if (itemName == null) {
                    return false;
                }
                return itemName.toLowerCase().contains(customFilter.toLowerCase());
            }
            
            return true;
        };
    }
    
    // Overlay control methods
    public void toggleNpcOverlay() {
        if (npcOverlayEnabled) {
            disableNpcOverlay();
        } else {
            enableNpcOverlay();
        }
    }
    
    public void enableNpcOverlay() {
        if (!npcOverlayEnabled) {
            overlayManager.add(npcOverlay);
            npcOverlayEnabled = true;
            log.debug("NPC cache overlay enabled");
        }
    }
    
    public void disableNpcOverlay() {
        if (npcOverlayEnabled) {
            overlayManager.remove(npcOverlay);
            npcOverlayEnabled = false;
            log.debug("NPC cache overlay disabled");
        }
    }
    
    public void toggleObjectOverlay() {
        if (objectOverlayEnabled) {
            disableObjectOverlay();
        } else {
            enableObjectOverlay();
        }
    }
    
    public void enableObjectOverlay() {
        if (!objectOverlayEnabled) {
            overlayManager.add(objectOverlay);
            objectOverlayEnabled = true;
            log.debug("Object cache overlay enabled");
        }
    }
    
    public void disableObjectOverlay() {
        if (objectOverlayEnabled) {
            overlayManager.remove(objectOverlay);
            objectOverlayEnabled = false;
            log.debug("Object cache overlay disabled");
        }
    }
    
    public void toggleGroundItemOverlay() {
        if (groundItemOverlayEnabled) {
            disableGroundItemOverlay();
        } else {
            enableGroundItemOverlay();
        }
    }
    
    public void enableGroundItemOverlay() {
        if (!groundItemOverlayEnabled) {
            overlayManager.add(groundItemOverlay);
            groundItemOverlayEnabled = true;
            log.debug("Ground item cache overlay enabled");
        }
    }
    
    public void disableGroundItemOverlay() {
        if (groundItemOverlayEnabled) {
            overlayManager.remove(groundItemOverlay);
            groundItemOverlayEnabled = false;
            log.debug("Ground item cache overlay disabled");
        }
    }
    
    public void toggleInfoPanel() {
        if (infoPanelEnabled) {
            disableInfoPanel();
        } else {
            enableInfoPanel();
        }
    }
    
    public void enableInfoPanel() {
        if (!infoPanelEnabled) {
            overlayManager.add(infoPanel);
            infoPanelEnabled = true;
            log.debug("Cache info panel enabled");
        }
    }
    
    public void disableInfoPanel() {
        if (infoPanelEnabled) {
            overlayManager.remove(infoPanel);
            infoPanelEnabled = false;
            log.debug("Cache info panel disabled");
        }
    }
    
    /**
     * Log detailed cache information including performance metrics
     */
    public void logDetailedCacheInfo() {
        log.info("=== Rs2 Cache Debugger - Detailed Cache Analysis ===");
        
        // NPC Cache Analysis
        if (Rs2NpcCache.getInstance() != null) {
            log.info("NPC Cache: {} entries, Mode: {}", 
                Rs2NpcCache.getInstance().size(), Rs2NpcCache.getInstance().getCacheMode());
            
            if (config.showCachePerformanceMetrics()) {
                // Use cache statistics for performance logging
                var npcStats = Rs2NpcCache.getInstance().getStatistics();
                log.info("NPC Cache Performance - Hit Rate: {}%, Total Hits: {}, Total Misses: {}", 
                    String.format("%.1f", npcStats.getHitRate() * 100),
                    npcStats.cacheHits,
                    npcStats.cacheMisses);
            }
            
            // Demonstrate cache utilities
            var closestNpc = Rs2NpcCache.getClosestNpcByGameId(1); // Man NPC ID
            if (closestNpc.isPresent()) {
                log.info("Closest Man NPC: {} at distance {}", 
                    closestNpc.get().getName(), closestNpc.get().getDistanceFromPlayer());
            }
            
            // Cache invalidation info
            var npcStats = Rs2NpcCache.getInstance().getStatistics();
            log.info("NPC Cache Last Invalidation: {}, Mode: {}", 
                npcStats.totalInvalidations > 0 ? "Available" : "None",
                npcStats.cacheMode);
        }
        
        // Object Cache Analysis
        if (Rs2ObjectCache.getInstance() != null) {
            log.info("Object Cache: {} entries, Mode: {}", 
                Rs2ObjectCache.getInstance().size(), Rs2ObjectCache.getInstance().getCacheMode());
                
            if (config.showCachePerformanceMetrics()) {
                var objectStats = Rs2ObjectCache.getInstance().getStatistics();
                log.info("Object Cache Performance - Hit Rate: {}%, Total Hits: {}, Total Misses: {}", 
                    String.format("%.1f", objectStats.getHitRate() * 100),
                    objectStats.cacheHits,
                    objectStats.cacheMisses);
            }
                
            // Demonstrate cache utilities  
            var closestBank = Rs2ObjectCache.getClosestObjectByName("Bank booth");
            if (closestBank.isPresent()) {
                log.info("Closest Bank: {} at distance {}", 
                    closestBank.get().getName(), closestBank.get().getDistanceFromPlayer());
            }
        }
        
        // Ground Item Cache Analysis
        if (Rs2GroundItemCache.getInstance() != null) {
            log.info("Ground Item Cache: {} entries, Mode: {}", 
                Rs2GroundItemCache.getInstance().size(), Rs2GroundItemCache.getInstance().getCacheMode());
                
            if (config.showCachePerformanceMetrics()) {
                var groundItemStats = Rs2GroundItemCache.getInstance().getStatistics();
                log.info("Ground Item Cache Performance - Hit Rate: {}%, Total Hits: {}, Total Misses: {}", 
                    String.format("%.1f", groundItemStats.getHitRate() * 100),
                    groundItemStats.cacheHits,
                    groundItemStats.cacheMisses);
            }
                
            // Demonstrate cache utilities
            var closestItem = Rs2GroundItemCache.getClosestItemByName("Coins");
            if (closestItem.isPresent()) {
                log.info("Closest Coins: {} x{} at distance {}", 
                    closestItem.get().getName(), closestItem.get().getQuantity(), 
                    closestItem.get().getDistanceFromPlayer());
            }
        }
        
        log.info("Active Overlays - NPC: {}, Object: {}, Ground Items: {}, Info Panel: {}", 
            npcOverlayEnabled, objectOverlayEnabled, groundItemOverlayEnabled, infoPanelEnabled);
            
        log.info("Cache Manager: Available");
        log.info("=== End Cache Analysis ===");
    }
    

    
    @Subscribe
    public void onGameTick(GameTick gameTick) {
        tickCounter++;
        
        // Update overlay configurations if they changed
        configureOverlays();
        
        // Log cache statistics at configured interval
        if (config.logCacheStatsTicks() > 0 && tickCounter % config.logCacheStatsTicks() == 0) {
            logPeriodicCacheStats();
        }
    }
    
    /**
     * Log periodic cache statistics
     */
    private void logPeriodicCacheStats() {
        if (!config.verboseLogging()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastLog = currentTime - lastCacheStatsLogTime;
        
        log.debug("Periodic Cache Stats ({}ms interval):", timeSinceLastLog);
        
        if (Rs2NpcCache.getInstance() != null) {
            var npcStats = Rs2NpcCache.getInstance().getStatistics();
            log.debug("  NPC Cache: {} entries, {} hits, {} misses", 
                npcStats.currentSize, npcStats.cacheHits, npcStats.cacheMisses);
        }

        if (Rs2ObjectCache.getInstance() != null) {
            var objectStats = Rs2ObjectCache.getInstance().getStatistics();
            log.debug("  Object Cache: {} entries, {} hits, {} misses", 
                objectStats.currentSize, objectStats.cacheHits, objectStats.cacheMisses);
        }
        
        if (Rs2GroundItemCache.getInstance() != null) {
            var groundItemStats = Rs2GroundItemCache.getInstance().getStatistics();
            log.debug("  Ground Item Cache: {} entries, {} hits, {} misses", 
                groundItemStats.currentSize, groundItemStats.cacheHits, groundItemStats.cacheMisses);
        }
            
        lastCacheStatsLogTime = currentTime;
    }
    
    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged) {
        if (!configChanged.getGroup().equals("rs2cachedebugger")) {
            return;
        }
        
        String key = configChanged.getKey();
        
        // Handle overlay enable/disable toggles
        switch (key) {
            case "enableNpcOverlay":
                if (config.enableNpcOverlay()) {
                    enableNpcOverlay();
                } else {
                    disableNpcOverlay();
                }
                break;
                
            case "enableObjectOverlay":
                if (config.enableObjectOverlay()) {
                    enableObjectOverlay();
                } else {
                    disableObjectOverlay();
                }
                break;
                
            case "enableGroundItemOverlay":
                if (config.enableGroundItemOverlay()) {
                    enableGroundItemOverlay();
                } else {
                    disableGroundItemOverlay();
                }
                break;
                
            case "enableInfoPanel":
                if (config.enableInfoPanel()) {
                    enableInfoPanel();
                } else {
                    disableInfoPanel();
                }
                break;
                
            // Handle filter changes that require reconfiguration
            case "npcFilterPreset":
            case "npcCustomFilter":
            case "npcRenderStyle":
            case "npcBorderColor":
            case "objectFilterPreset":
            case "objectCustomFilter":
            case "objectRenderStyle":
            case "objectBorderColor":
            case "objectShowId":
            case "objectShowCoordinates":
            case "objectMaxDistance":
            case "showGameObjects":
            case "showWallObjects":
            case "showDecorativeObjects":
            case "showGroundObjects":
            case "enableObjectTypeColoring":
            case "enableObjectCategoryColoring":
            case "gameObjectColor":
            case "wallObjectColor":
            case "decorativeObjectColor":
            case "groundObjectColor":
            case "bankColor":
            case "altarColor":
            case "resourceColor":
            case "groundItemFilterPreset":
            case "groundItemCustomFilter":
            case "groundItemRenderStyle":
            case "groundItemBorderColor":
                configureOverlays();
                break;
        }
    }
    
    // Getters for overlay state
    public boolean isNpcOverlayEnabled() {
        return npcOverlayEnabled;
    }
    
    public boolean isObjectOverlayEnabled() {
        return objectOverlayEnabled;
    }
    
    public boolean isGroundItemOverlayEnabled() {
        return groundItemOverlayEnabled;
    }
    
    public boolean isInfoPanelEnabled() {
        return infoPanelEnabled;
    }
    
    // Getters for performance monitoring
    public int getTickCounter() {
        return tickCounter;
    }
    
   
}
