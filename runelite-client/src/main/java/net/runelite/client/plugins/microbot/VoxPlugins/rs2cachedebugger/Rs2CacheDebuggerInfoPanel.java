package net.runelite.client.plugins.microbot.VoxPlugins.rs2cachedebugger;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.util.cache.Rs2GroundItemCache;
import net.runelite.client.plugins.microbot.util.cache.Rs2NpcCache;
import net.runelite.client.plugins.microbot.util.cache.Rs2ObjectCache;
import net.runelite.client.plugins.microbot.util.cache.Rs2Cache;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2NpcCacheUtils;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2ObjectCacheUtils;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2GroundItemCacheUtils;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2ObjectModel;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.Overlay;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

/**
 * Rs2 Cache Debugger Info Panel showing detailed cache information and viewport entities.
 * Displays comprehensive information about NPCs, objects, and ground items in the current viewport
 * with focus on cache performance and debugging statistics.
 * 
 * @author Vox
 * @version 3.0 - Cache Debugging Focus
 */
public class Rs2CacheDebuggerInfoPanel extends Overlay {
    
    private Rs2CacheDebuggerConfig config;
    private final PanelComponent panelComponent = new PanelComponent();
    @Inject
    public Rs2CacheDebuggerInfoPanel(Client client) {
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }
    
    /**
     * Set the configuration for this info panel
     */
    public void setConfig(Rs2CacheDebuggerConfig config) {
        this.config = config;
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        if (config == null || !config.enableInfoPanel() || !config.showInfoPanel()) {
            return null;
        }
        
        panelComponent.getChildren().clear();
        
        // Panel title
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Rs2 Cache Debugger")
            .color(Color.CYAN)
            .build());
        
        // Cache statistics
        addCacheStatistics();
        
        // Viewport entities
        if (config.infoPanelShowNpcs() || config.infoPanelShowObjects() || config.infoPanelShowGroundItems()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("").right("").build()); // Spacer
            
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Viewport Entities:")
                .leftColor(Color.WHITE)
                .build());
        }
        
        // NPCs in viewport
        if (config.infoPanelShowNpcs()) {
            addNpcInfo();
        }
        
        // Objects in viewport
        if (config.infoPanelShowObjects()) {
            addObjectInfo();
        }
        
        // Ground items in viewport
        if (config.infoPanelShowGroundItems()) {
            addGroundItemInfo();
        }
        
        return panelComponent.render(graphics);
    }
    
    /**
     * Add cache statistics to the panel
     */
    private void addCacheStatistics() {
        Rs2Cache<Integer,Rs2NpcModel> npcCache = Rs2NpcCache.getInstance();
        Rs2Cache<String,Rs2ObjectModel> objectCache = Rs2ObjectCache.getInstance();
        Rs2Cache<String,Rs2GroundItemModel> groundItemCache = Rs2GroundItemCache.getInstance();
        
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Cache Statistics:")
            .leftColor(Color.WHITE)
            .build());
        
        panelComponent.getChildren().add(LineComponent.builder()
            .left("NPCs:")
            .right(String.valueOf(npcCache.size()))
            .leftColor(Color.ORANGE)
            .rightColor(Color.WHITE)
            .build());
        
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Objects:")
            .right(String.valueOf(objectCache.size()))
            .leftColor(Color.BLUE)
            .rightColor(Color.WHITE)
            .build());
        
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Ground Items:")
            .right(String.valueOf(groundItemCache.size()))
            .leftColor(Color.GREEN)
            .rightColor(Color.WHITE)
            .build());
    }
    
    /**
     * Add NPC information to the panel
     */
    private void addNpcInfo() {
        List<Rs2NpcModel> visibleNpcs = Rs2NpcCacheUtils.getAllInViewport()
            .limit(5) // Limit to first 5 for display
            .collect(Collectors.toList());
        
        if (!visibleNpcs.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("NPCs (" + visibleNpcs.size() + "):")
                .leftColor(Color.ORANGE)
                .build());
            
            for (Rs2NpcModel npc : visibleNpcs) {
                String npcInfo = buildNpcInfo(npc);
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("  " + npcInfo)
                    .leftColor(Color.LIGHT_GRAY)
                    .build());
            }
        }
    }
    
    /**
     * Add object information to the panel
     */
    private void addObjectInfo() {
        List<Rs2ObjectModel> visibleObjects = Rs2ObjectCacheUtils.getAllInViewport()
            .limit(5) // Limit to first 5 for display
            .collect(Collectors.toList());
        
        if (!visibleObjects.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Objects (" + visibleObjects.size() + "):")
                .leftColor(Color.BLUE)
                .build());
            
            for (Rs2ObjectModel obj : visibleObjects) {
                String objInfo = buildObjectInfo(obj);
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("  " + objInfo)
                    .leftColor(Color.LIGHT_GRAY)
                    .build());
            }
        }
    }
    
    /**
     * Add ground item information to the panel
     */
    private void addGroundItemInfo() {
        List<Rs2GroundItemModel> visibleItems = Rs2GroundItemCacheUtils.getAllInViewport()
            .limit(5) // Limit to first 5 for display
            .collect(Collectors.toList());
        
        if (!visibleItems.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Ground Items (" + visibleItems.size() + "):")
                .leftColor(Color.GREEN)
                .build());
            
            for (Rs2GroundItemModel item : visibleItems) {
                String itemInfo = buildGroundItemInfo(item);
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("  " + itemInfo)
                    .leftColor(Color.LIGHT_GRAY)
                    .build());
            }
        }
    }
    
    /**
     * Build info string for an NPC
     */
    private String buildNpcInfo(Rs2NpcModel npc) {
        StringBuilder info = new StringBuilder();
        
        String name = npc.getName();
        if (name != null) {
            info.append(name);
        } else {
            info.append("Unknown");
        }
        
        info.append(" [ID:").append(npc.getId()).append("]");
        
        if (npc.getCombatLevel() > 0) {
            info.append(" (CB: ").append(npc.getCombatLevel()).append(")");
        }
        
        info.append(" (").append(npc.getDistanceFromPlayer()).append("t)");
        
        // Add interaction status
        if (npc.getInteracting() != null) {
            info.append(" [INTERACTING]");
        }
        
        // Add coordinates if config allows
        if (config != null && config.npcShowCoordinates()) {
            info.append(" @(").append(npc.getWorldLocation().getX())
                .append(",").append(npc.getWorldLocation().getY()).append(")");
        }
        
        return info.toString();
    }
    
    /**
     * Build info string for an object
     */
    private String buildObjectInfo(Rs2ObjectModel obj) {
        StringBuilder info = new StringBuilder();
        
        String name = obj.getName();
        if (name != null && !name.trim().isEmpty()) {
            info.append(name);
        } else {
            info.append("Object");
        }
        
        info.append(" [ID:").append(obj.getId()).append("]");
        
        // Add object type abbreviation
        String typeAbbr = getObjectTypeAbbreviation(obj.getObjectType());
        info.append(" (").append(typeAbbr).append(")");
        
        info.append(" (").append(obj.getDistanceFromPlayer()).append("t)");
        
        // Add coordinates if config allows
        if (config != null && config.objectShowCoordinates()) {
            info.append(" @(").append(obj.getLocation().getX())
                .append(",").append(obj.getLocation().getY()).append(")");
        }
        
        return info.toString();
    }
    
    /**
     * Get object type abbreviation for display
     */
    private String getObjectTypeAbbreviation(Rs2ObjectModel.ObjectType objectType) {
        switch (objectType) {
            case GAME_OBJECT:
                return "G";
            case WALL_OBJECT:
                return "W";
            case DECORATIVE_OBJECT:
                return "D";
            case GROUND_OBJECT:
                return "Gnd";
            default:
                return "?";
        }
    }
    
    /**
     * Build info string for a ground item
     */
    private String buildGroundItemInfo(Rs2GroundItemModel item) {
        StringBuilder info = new StringBuilder();
        
        String name = item.getName();
        if (name != null) {
            info.append(name);
        } else {
            info.append("Unknown Item");
        }
        
        info.append(" [ID:").append(item.getId()).append("]");
        
        if (item.getQuantity() > 1) {
            info.append(" x").append(item.getQuantity());
        }
        
        // Add value information
        if (item.getValue() > 0) {
            info.append(" (").append(item.getValue()).append("gp)");
        }
        
        info.append(" (").append(item.getDistanceFromPlayer()).append("t)");
        
        if (item.isOwned()) {
            info.append(" [OWNED]");
        }
        
        // Add coordinates if config allows
        if (config != null && config.groundItemShowCoordinates()) {
            info.append(" @(").append(item.getLocation().getX())
                .append(",").append(item.getLocation().getY()).append(")");
        }
        
        return info.toString();
    }
}
