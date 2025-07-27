package net.runelite.client.plugins.microbot.util.cache.overlay;

import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import java.awt.Color;
import java.util.List;

/**
 * Container for hover information shared between cache overlays and the top-level info box overlay.
 * This allows info boxes to appear on top of all other overlays.
 * 
 * @author Vox
 * @version 1.0
 */
public class HoverInfoContainer {
    private static volatile HoverInfo currentHoverInfo = null;
    
    /**
     * Sets the current hover information.
     * 
     * @param info The hover information to display, or null to clear
     */
    public static void setHoverInfo(HoverInfo info) {
        currentHoverInfo = info;
    }
    
    /**
     * Gets the current hover information.
     * 
     * @return The current hover info or null if none
     */
    public static HoverInfo getCurrentHoverInfo() {
        return currentHoverInfo;
    }
    
    /**
     * Clears the current hover information.
     */
    public static void clearHoverInfo() {
        currentHoverInfo = null;
    }
    
    /**
     * Checks if there is currently hover information available.
     * 
     * @return true if hover info is available
     */
    public static boolean hasHoverInfo() {
        return currentHoverInfo != null;
    }
    
    /**
     * Container for hover information data.
     */
    public static class HoverInfo {
        private final List<String> infoLines;
        private final Point location;
        private final Color borderColor;
        private final String entityType;
        private final long creationTime;
        
        public HoverInfo(List<String> infoLines, Point location, Color borderColor, String entityType) {
            this.infoLines = infoLines;
            this.location = location;
            this.borderColor = borderColor;
            this.entityType = entityType;
            this.creationTime = System.currentTimeMillis();
        }
        
        public List<String> getInfoLines() {
            return infoLines;
        }
        
        public Point getLocation() {
            return location;
        }
        
        public Color getBorderColor() {
            return borderColor;
        }
        
        public String getEntityType() {
            return entityType;
        }
        
        public long getCreationTime() {
            return creationTime;
        }
        
        /**
         * Checks if this hover info is still fresh (not too old).
         * 
         * @param maxAgeMs Maximum age in milliseconds
         * @return true if the info is still fresh
         */
        public boolean isFresh(long maxAgeMs) {
            return (System.currentTimeMillis() - creationTime) <= maxAgeMs;
        }
    }
    
    // Static overlay management
    private static Rs2CacheInfoBoxOverlay infoBoxOverlay;
    private static boolean overlayRegistered = false;
    
    /**
     * Manually registers the info box overlay with an overlay manager.
     * This should be called by overlay managers or plugins that use the hover system.
     * 
     * @param overlayManager The overlay manager to register with
     */
    public static void registerInfoBoxOverlay(OverlayManager overlayManager) {
        if (infoBoxOverlay == null && Microbot.getClient() != null) {
            infoBoxOverlay = new Rs2CacheInfoBoxOverlay(Microbot.getClient());
            overlayManager.add(infoBoxOverlay);
            overlayRegistered = true;
        }
    }
    
    /**
     * Manually unregisters the info box overlay from an overlay manager.
     * 
     * @param overlayManager The overlay manager to unregister from
     */
    public static void unregisterInfoBoxOverlay(OverlayManager overlayManager) {
        if (infoBoxOverlay != null && overlayRegistered) {
            overlayManager.remove(infoBoxOverlay);
            infoBoxOverlay = null;
            overlayRegistered = false;
        }
    }
    
    /**
     * Checks if the info box overlay is currently registered.
     * 
     * @return true if registered
     */
    public static boolean isInfoBoxOverlayRegistered() {
        return overlayRegistered && infoBoxOverlay != null;
    }
}
