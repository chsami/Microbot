package net.runelite.client.plugins.microbot.util.cache.overlay;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.cache.Rs2ObjectCache;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2ObjectCacheUtils;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2ObjectModel;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import java.awt.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Overlay for rendering cached objects with various highlight options.
 * Based on RuneLite's ObjectIndicatorsOverlay patterns but using the cache system.
 * 
 * @author Vox
 * @version 1.0
 */
@Slf4j
public class Rs2ObjectCacheOverlay extends Rs2BaseCacheOverlay {
    
    // Object-specific colors (Blue theme) - Default fallback colors
    private static final Color OBJECT_BORDER_COLOR = Color.BLUE;
    private static final Color OBJECT_FILL_COLOR = new Color(0, 0, 255, 50); // Blue with alpha
    
    // Default object type colors
    private static final Color GAME_OBJECT_COLOR = new Color(255, 0, 0); // Red
    private static final Color WALL_OBJECT_COLOR = new Color(0, 255, 0); // Green
    private static final Color DECORATIVE_OBJECT_COLOR = new Color(255, 255, 0); // Yellow
    private static final Color GROUND_OBJECT_COLOR = new Color(255, 0, 255); // Magenta
    private static final Color TILE_OBJECT_COLOR = new Color(255, 165, 0); // Orange
    
    // Rendering options
    private boolean renderHull = true;
    private boolean renderClickbox = false;
    private boolean renderTile = false;
    private boolean renderOutline = false;
    private boolean renderObjectInfo = true; // Show object type and ID
    private boolean renderObjectName = true; // Show object names
    private boolean renderWorldCoordinates = false; // Show world coordinates
    private boolean onlyShowTextOnHover = true; // Only show text when mouse is hovering
    
    // Object type enable/disable flags
    private boolean enableGameObjects = true;
    private boolean enableWallObjects = true;
    private boolean enableDecorativeObjects = true;
    private boolean enableGroundObjects = true;
    
    // Statistics tracking
    private static final long STATISTICS_LOG_INTERVAL_MS = 10_000; // 10 seconds
    private long lastStatisticsLogTime = 0;
    
    private Predicate<Rs2ObjectModel> renderFilter;
    
    public Rs2ObjectCacheOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer) {
        super(client, modelOutlineRenderer);
    }
    
    @Override
    protected Color getDefaultBorderColor() {
        return OBJECT_BORDER_COLOR;
    }
    
    @Override
    protected Color getDefaultFillColor() {
        return OBJECT_FILL_COLOR;
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        if (!isClientReady()) {
            return null;
        }
        if (Rs2ObjectCache.getInstance() == null || Rs2ObjectCache.getInstance().size() == 0) {
            return null; // No objects to render
        }
        
        // Check if we should log statistics (every 10 seconds)
        long currentTime = System.currentTimeMillis();
        boolean shouldLogStatistics = (currentTime - lastStatisticsLogTime) >= STATISTICS_LOG_INTERVAL_MS;
        
        // Get all objects from cache for statistics
        List<Rs2ObjectModel> allObjects = Rs2ObjectCache.getInstance().stream()
                .collect(Collectors.toList());
        
        // Track statistics by object type for each filtering stage
        Map<Rs2ObjectModel.ObjectType, Integer> totalByType = new EnumMap<>(Rs2ObjectModel.ObjectType.class);
        Map<Rs2ObjectModel.ObjectType, Integer> afterRenderFilterByType = new EnumMap<>(Rs2ObjectModel.ObjectType.class);
        Map<Rs2ObjectModel.ObjectType, Integer> afterTypeEnabledByType = new EnumMap<>(Rs2ObjectModel.ObjectType.class);
        Map<Rs2ObjectModel.ObjectType, Integer> afterViewportByType = new EnumMap<>(Rs2ObjectModel.ObjectType.class);
        
        // Initialize counters
        for (Rs2ObjectModel.ObjectType type : Rs2ObjectModel.ObjectType.values()) {
            totalByType.put(type, 0);
            afterRenderFilterByType.put(type, 0);
            afterTypeEnabledByType.put(type, 0);
            afterViewportByType.put(type, 0);
        }
        
        // Count total objects by type
        for (Rs2ObjectModel obj : allObjects) {
            Rs2ObjectModel.ObjectType type = obj.getObjectType();
            totalByType.put(type, totalByType.get(type) + 1);
        }
        
        // Apply filters and count at each stage
        List<Rs2ObjectModel> afterRenderFilter = allObjects.stream()
                .filter(obj -> renderFilter == null || renderFilter.test(obj))
                .collect(Collectors.toList());
        
        for (Rs2ObjectModel obj : afterRenderFilter) {
            Rs2ObjectModel.ObjectType type = obj.getObjectType();
            afterRenderFilterByType.put(type, afterRenderFilterByType.get(type) + 1);
        }
        
        List<Rs2ObjectModel> afterTypeEnabled = afterRenderFilter.stream()
                .filter(obj -> isObjectTypeEnabled(obj.getObjectType()))
                .collect(Collectors.toList());
        
        for (Rs2ObjectModel obj : afterTypeEnabled) {
            Rs2ObjectModel.ObjectType type = obj.getObjectType();
            afterTypeEnabledByType.put(type, afterTypeEnabledByType.get(type) + 1);
        }
        
        List<Rs2ObjectModel> afterViewport = afterTypeEnabled.stream()
                .filter(Rs2ObjectCacheUtils::isVisibleInViewport)
                .collect(Collectors.toList());
        
        for (Rs2ObjectModel obj : afterViewport) {
            Rs2ObjectModel.ObjectType type = obj.getObjectType();
            afterViewportByType.put(type, afterViewportByType.get(type) + 1);
        }
        
        // Log detailed statistics every 10 seconds
        if (shouldLogStatistics) {
            lastStatisticsLogTime = currentTime;
            logRenderingStatistics(totalByType, afterRenderFilterByType, afterTypeEnabledByType, afterViewportByType);
        }
        
        // Render the visible objects
        afterViewport.forEach(obj -> renderObjectOverlay(graphics, obj));
        
        return null;
    }
    
    /**
     * Logs detailed rendering statistics showing object type distribution at each filtering stage.
     * This helps identify which object types are available, filtered out, and why.
     * 
     * @param totalByType Total count of each object type in cache
     * @param afterRenderFilterByType Count after custom render filter applied
     * @param afterTypeEnabledByType Count after object type enable/disable filter
     * @param afterViewportByType Count after viewport visibility filter (final rendered count)
     */
    private void logRenderingStatistics(Map<Rs2ObjectModel.ObjectType, Integer> totalByType,
                                       Map<Rs2ObjectModel.ObjectType, Integer> afterRenderFilterByType,
                                       Map<Rs2ObjectModel.ObjectType, Integer> afterTypeEnabledByType,
                                       Map<Rs2ObjectModel.ObjectType, Integer> afterViewportByType) {
        
        // Calculate totals across all types
        int totalObjects = totalByType.values().stream().mapToInt(Integer::intValue).sum();
        int afterRenderFilterTotal = afterRenderFilterByType.values().stream().mapToInt(Integer::intValue).sum();
        int afterTypeEnabledTotal = afterTypeEnabledByType.values().stream().mapToInt(Integer::intValue).sum();
        int finalRenderedTotal = afterViewportByType.values().stream().mapToInt(Integer::intValue).sum();
        
        log.info("=== Rs2ObjectCacheOverlay Rendering Statistics ===");
        log.info("Cache Total: {} | After RenderFilter: {} | After TypeEnabled: {} | Final Rendered: {}", 
                totalObjects, afterRenderFilterTotal, afterTypeEnabledTotal, finalRenderedTotal);
        
        // Log statistics for each object type
        for (Rs2ObjectModel.ObjectType type : Rs2ObjectModel.ObjectType.values()) {
            int total = totalByType.get(type);
            int afterRenderFilter = afterRenderFilterByType.get(type);
            int afterTypeEnabled = afterTypeEnabledByType.get(type);
            int finalRendered = afterViewportByType.get(type);
            
            // Skip types with no objects
            if (total == 0) {
                continue;
            }
            
            // Calculate filtering reasons
            int filteredByRenderFilter = total - afterRenderFilter;
            int filteredByTypeEnabled = afterRenderFilter - afterTypeEnabled;
            int filteredByViewport = afterTypeEnabled - finalRendered;
            
            boolean typeEnabled = isObjectTypeEnabled(type);
            
            log.info("  {}: Total={} | RenderFilter={} | TypeEnabled={} ({}) | Viewport={} | Final={}",
                    type.getTypeName(),
                    total,
                    afterRenderFilter,
                    afterTypeEnabled,
                    typeEnabled ? "ENABLED" : "DISABLED",
                    finalRendered,
                    finalRendered);
            
            // Log filtering details if objects were filtered
            if (filteredByRenderFilter > 0) {
                log.info("    -> {} filtered by RenderFilter", filteredByRenderFilter);
            }
            if (filteredByTypeEnabled > 0) {
                log.info("    -> {} filtered by TypeEnabled ({})", 
                        filteredByTypeEnabled, typeEnabled ? "ERROR" : "disabled");
            }
            if (filteredByViewport > 0) {
                log.info("    -> {} filtered by Viewport (not visible)", filteredByViewport);
            }
        }
        
        // Log filtering summary
        int totalFilteredByRender = totalObjects - afterRenderFilterTotal;
        int totalFilteredByType = afterRenderFilterTotal - afterTypeEnabledTotal;
        int totalFilteredByViewport = afterTypeEnabledTotal - finalRenderedTotal;
        
        if (totalFilteredByRender > 0 || totalFilteredByType > 0 || totalFilteredByViewport > 0) {
            log.info("Filtering Summary: RenderFilter removed {} | TypeEnabled removed {} | Viewport removed {}",
                    totalFilteredByRender, totalFilteredByType, totalFilteredByViewport);
        }
        
        // Log current filter settings
        log.info("Current Settings: GameObj={} | WallObj={} | DecorObj={} | GroundObj={} | RenderFilter={}",
                enableGameObjects, enableWallObjects, enableDecorativeObjects, enableGroundObjects,
                renderFilter != null ? "ACTIVE" : "NONE");
        
        log.info("=== End Rs2ObjectCacheOverlay Statistics ===");
    }
    
    /**
     * Renders a single object with the configured options.
     * 
     * @param graphics The graphics context
     * @param objectModel The object model to render
     */
    private void renderObjectOverlay(Graphics2D graphics, Rs2ObjectModel objectModel) {
        try {
            // Validate inputs
            if (objectModel == null) {
                return;
            }
            if (objectModel.getTileObject() == null) {
                return; // No tile object to render
            }
            
            TileObject tileObject = objectModel.getTileObject();
            
            // Check if object is on current plane
            try {
                if (tileObject.getPlane() != client.getTopLevelWorldView().getPlane()) {
                    return;
                }
            } catch (Exception e) {
                // Skip plane check if there's an issue accessing it
                log.debug("Failed to check object plane for object {}: {}", objectModel.getId(), e.getMessage());
            }
            
            // Get colors for this specific object (supports per-object coloring)
            Color borderColor = getBorderColorForObject(objectModel);
            Color fillColor = getFillColorForObject(objectModel);
            float borderWidth = DEFAULT_BORDER_WIDTH;
            Stroke stroke = new BasicStroke(borderWidth);
            
            // Render convex hull
            if (renderHull) {
                renderConvexHull(graphics, tileObject, borderColor, fillColor, stroke);
            }
            
            // Render clickbox
            if (renderClickbox) {
                try {
                    Shape clickbox = tileObject.getClickbox();
                    if (clickbox != null) {
                        renderPolygon(graphics, clickbox, borderColor, fillColor, stroke);
                    }
                } catch (Exception e) {
                    log.debug("Failed to render clickbox for object {}: {}", objectModel.getId(), e.getMessage());
                }
            }
            
            // Render tile
            if (renderTile) {
                try {
                    Polygon tilePoly = tileObject.getCanvasTilePoly();
                    if (tilePoly != null) {
                        renderPolygon(graphics, tilePoly, borderColor, fillColor, stroke);
                    }
                } catch (Exception e) {
                    log.debug("Failed to render tile poly for object {}: {}", objectModel.getId(), e.getMessage());
                }
            }
            
            // Render outline
            if (renderOutline) {
                try {
                    modelOutlineRenderer.drawOutline(tileObject, (int) borderWidth, borderColor, 0);
                } catch (Exception e) {
                    log.debug("Failed to render outline for object {}: {}", objectModel.getId(), e.getMessage());
                }
            }
            
            // Render object information (type, ID, and name)
            if (renderObjectInfo) {
                renderObjectInfo(graphics, objectModel, tileObject);
            }
            
        } catch (Exception e) {
            log.warn("Failed to render object overlay for object {}: {}", 
                    objectModel != null ? objectModel.getId() : "unknown", e.getMessage(), e);
        }
    }
    
    /**
     * Renders the convex hull for different object types.
     * Based on ObjectIndicatorsOverlay pattern.
     * 
     * @param graphics The graphics context
     * @param object The tile object
     * @param color The border color
     * @param fillColor The fill color
     * @param stroke The stroke
     */
    private void renderConvexHull(Graphics2D graphics, TileObject object, Color color, Color fillColor, Stroke stroke) {
        try {
            Shape polygon = null;
            Shape polygon2 = null;

            try {
                if (object instanceof GameObject) {
                    polygon = ((GameObject) object).getConvexHull();
                } else if (object instanceof WallObject) {
                    WallObject wallObject = (WallObject) object;
                    polygon = wallObject.getConvexHull();
                    polygon2 = wallObject.getConvexHull2();
                } else if (object instanceof DecorativeObject) {
                    DecorativeObject decorativeObject = (DecorativeObject) object;
                    polygon = decorativeObject.getConvexHull();
                    polygon2 = decorativeObject.getConvexHull2();
                } else if (object instanceof GroundObject) {
                    polygon = ((GroundObject) object).getConvexHull();
                } else {
                    polygon = object.getCanvasTilePoly();
                }
            } catch (Exception e) {
                log.debug("Failed to get convex hull shapes: {}", e.getMessage());
                // Fallback to tile poly
                try {
                    polygon = object.getCanvasTilePoly();
                } catch (Exception e2) {
                    log.debug("Failed to get tile poly as fallback: {}", e2.getMessage());
                }
            }

            if (polygon != null) {
                renderPolygon(graphics, polygon, color, fillColor, stroke);
            }

            if (polygon2 != null) {
                renderPolygon(graphics, polygon2, color, fillColor, stroke);
            }
        } catch (Exception e) {
            log.debug("Error rendering convex hull: {}", e.getMessage());
        }
    }
    
    /**
     * Renders object information as a detailed info box with each piece of information on a separate line.
     * The info box border uses the object type color for visual identification.
     * 
     * @param graphics The graphics context
     * @param objectModel The object model
     * @param tileObject The tile object
     */
    private void renderObjectInfo(Graphics2D graphics, Rs2ObjectModel objectModel, TileObject tileObject) {
        try {
            // Check if we should only show text on hover
            boolean isHovering = isMouseHoveringOver(tileObject);
            if (onlyShowTextOnHover && !isHovering) {
                return;
            }
            
            // Build information lines (always build them when renderObjectInfo is called)
            java.util.List<String> infoLines = new java.util.ArrayList<>();
                
                // Add object name if enabled and available
                String objectName = objectModel.getName();
                if (renderObjectName && objectName != null && !objectName.equals("Unknown Object") && !objectName.trim().isEmpty()) {
                    infoLines.add("Name: " + objectName);
                }
                
                // Add object type and ID
                if (renderObjectInfo) {
                    infoLines.add("Type: " + objectModel.getObjectType().getTypeName());
                    infoLines.add("ID: " + objectModel.getId());
                    
                    // Object size information
                    infoLines.add("Size: " + objectModel.getSizeX() + "x" + objectModel.getSizeY());
                    
                    // Object composition details
                    ObjectComposition comp = objectModel.getObjectComposition();
                    if (comp != null) {
                        // Map scene and icon IDs
                        int mapSceneId = comp.getMapSceneId();
                        int mapIconId = comp.getMapIconId();
                        if (mapSceneId != -1) {
                            infoLines.add("MapScene: " + mapSceneId);
                        }
                        if (mapIconId != -1) {
                            infoLines.add("MapIcon: " + mapIconId);
                        }
                        
                        // Varbit/VarPlayer information for multiloc objects
                        int varbitId = comp.getVarbitId();
                        int varPlayerId = comp.getVarPlayerId();
                        if (varbitId != -1) {
                            infoLines.add("VarbitID: " + varbitId);
                        }
                        if (varPlayerId != -1) {
                            infoLines.add("VarPlayerID: " + varPlayerId);
                        }
                        
                        // Impostor information for multiloc objects
                        int[] impostorIds = comp.getImpostorIds();
                        if (impostorIds != null && impostorIds.length > 0) {
                            infoLines.add("Impostors: " + impostorIds.length + " variants");
                        }
                    }
                    
                    // Object properties
                    if (objectModel.isSolid()) {
                        infoLines.add("Property: Solid");
                    }
                    if (objectModel.blocksLineOfSight()) {
                        infoLines.add("Property: Blocks LoS");
                    }
                    
                    // Cache timing information
                    infoLines.add("Age: " + objectModel.getTicksSinceCreation() + " ticks");
                }
                
                // Add world coordinates if enabled
                if (renderWorldCoordinates) {
                    WorldPoint wp = objectModel.getLocation();
                    if (wp != null) {
                        infoLines.add("Coords: " + wp.getX() + ", " + wp.getY() + " (Plane " + wp.getPlane() + ")");
                        
                        // Canonical location for multi-tile objects
                        WorldPoint canonical = objectModel.getCanonicalLocation();
                        if (canonical != null && !canonical.equals(wp)) {
                            infoLines.add("Canonical: " + canonical.getX() + ", " + canonical.getY());
                        }
                    }
                }
                
                // Add additional object info
                infoLines.add("Distance: " + objectModel.getDistanceFromPlayer() + " tiles");
                
                // Add object actions if available - prefer ObjectComposition actions for completeness
                String[] actions = null;
                ObjectComposition comp = objectModel.getObjectComposition();
                if (comp != null) {
                    actions = comp.getActions();
                }
                if (actions == null || actions.length == 0) {
                    actions = objectModel.getActions(); // Fallback to model actions
                }
                
                if (actions != null && actions.length > 0) {
                    java.util.List<String> validActions = new java.util.ArrayList<>();
                    for (String action : actions) {
                        if (action != null && !action.trim().isEmpty()) {
                            validActions.add(action);
                        }
                    }
                    if (!validActions.isEmpty()) {
                        // Limit display to first 3 actions to avoid clutter
                        int maxActions = Math.min(validActions.size(), 3);
                        infoLines.add("Actions: " + String.join(", ", validActions.subList(0, maxActions)));
                        if (validActions.size() > 3) {
                            infoLines.add("  ... +" + (validActions.size() - 3) + " more");
                        }
                    }
                }
                
                // Only set hover info if we have information to display
                if (!infoLines.isEmpty()) {
                    Color borderColor = getBorderColorForObject(objectModel);
                    String entityType = "Object (" + objectModel.getObjectType().getTypeName() + ")";
                    
                    // Use object's canvas location for positioning, or mouse position if hovering
                    net.runelite.api.Point displayLocation;
                    if (isHovering) {
                        displayLocation = client.getMouseCanvasPosition();
                    } else {
                        // Use object's text location for non-hover display
                        displayLocation = tileObject.getCanvasTextLocation(graphics, "", 0);
                    }
                    
                    if (displayLocation != null) {
                        HoverInfoContainer.HoverInfo hoverInfo = new HoverInfoContainer.HoverInfo(
                            infoLines, displayLocation, borderColor, entityType);
                        HoverInfoContainer.setHoverInfo(hoverInfo);
                    }
                }
        } catch (Exception e) {
            log.debug("Failed to render object info for object {}: {}", objectModel.getId(), e.getMessage());
        }
    }
    
    /**
     * Renders a detailed info box with multiple lines of information.
     * Each line is rendered separately with a colored border indicating the object type.
     * 
     * @param graphics The graphics context
     * @param infoLines List of information lines to display
     * @param location The location to render the info box
     * @param borderColor The border color (indicates object type)
     */
    private void renderDetailedInfoBox(Graphics2D graphics, java.util.List<String> infoLines, 
                                     net.runelite.api.Point location, Color borderColor) {
        if (infoLines.isEmpty()) return;
        
        FontMetrics fm = graphics.getFontMetrics();
        int lineHeight = fm.getHeight();
        int maxWidth = 0;
        
        // Calculate the maximum width needed
        for (String line : infoLines) {
            int lineWidth = fm.stringWidth(line);
            if (lineWidth > maxWidth) {
                maxWidth = lineWidth;
            }
        }
        
        // Calculate box dimensions
        int padding = 6;
        int boxWidth = maxWidth + (padding * 2);
        int boxHeight = (infoLines.size() * lineHeight) + (padding * 2);
        
        // Calculate box position (centered above the location)
        int boxX = location.getX() - (boxWidth / 2);
        int boxY = location.getY() - boxHeight - 10; // 10 pixels above the object
        
        // Draw the info box background
        Color backgroundColor = new Color(0, 0, 0, 180); // Semi-transparent black
        graphics.setColor(backgroundColor);
        graphics.fillRect(boxX, boxY, boxWidth, boxHeight);
        
        // Draw the border in object type color
        graphics.setColor(borderColor);
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.drawRect(boxX, boxY, boxWidth, boxHeight);
        
        // Draw the text lines
        graphics.setColor(Color.WHITE);
        for (int i = 0; i < infoLines.size(); i++) {
            String line = infoLines.get(i);
            int textX = boxX + padding;
            int textY = boxY + padding + fm.getAscent() + (i * lineHeight);
            graphics.drawString(line, textX, textY);
        }
    }
    
    /**
     * Checks if a specific object type should be rendered.
     * 
     * @param objectType The object type to check
     * @return true if the object type should be rendered
     */
    protected boolean isObjectTypeEnabled(Rs2ObjectModel.ObjectType objectType) {
        switch (objectType) {
            case GAME_OBJECT:
                return enableGameObjects;
            case WALL_OBJECT:
                return enableWallObjects;
            case DECORATIVE_OBJECT:
                return enableDecorativeObjects;
            case GROUND_OBJECT:
                return enableGroundObjects;
            default:
                return true;
        }
    }
    
    /**
     * Checks if the mouse is hovering over a tile object.
     * 
     * @param tileObject The tile object to check
     * @return true if mouse is hovering over the object
     */
    private boolean isMouseHoveringOver(TileObject tileObject) {
        try {
            net.runelite.api.Point mousePos = client.getMouseCanvasPosition();
            if (mousePos == null) {
                return false;
            }
            
            // Check if mouse is over the object's convex hull
            Shape shape = null;
            try {
                if (tileObject instanceof GameObject) {
                    shape = ((GameObject) tileObject).getConvexHull();
                } else if (tileObject instanceof WallObject) {
                    shape = ((WallObject) tileObject).getConvexHull();
                } else if (tileObject instanceof DecorativeObject) {
                    shape = ((DecorativeObject) tileObject).getConvexHull();
                } else if (tileObject instanceof GroundObject) {
                    shape = ((GroundObject) tileObject).getConvexHull();
                }
            } catch (Exception e) {
                log.debug("Failed to get convex hull for hover detection: {}", e.getMessage());
            }
            
            // Fallback to tile poly if no hull available
            if (shape == null) {
                try {
                    shape = tileObject.getCanvasTilePoly();
                } catch (Exception e) {
                    log.debug("Failed to get tile poly for hover detection: {}", e.getMessage());
                    return false;
                }
            }
            
            return shape != null && shape.contains(mousePos.getX(), mousePos.getY());
        } catch (Exception e) {
            log.debug("Error in mouse hover detection: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Renders text with a semi-transparent background for better readability.
     * 
     * @param graphics The graphics context
     * @param text The text to render
     * @param location The location to render at
     * @param color The text color
     */
    private void renderTextWithBackground(Graphics2D graphics, String text, 
                                        net.runelite.api.Point location, Color color) {
        try {
            if (text == null || text.trim().isEmpty() || location == null) {
                return;
            }
            
            FontMetrics fm = graphics.getFontMetrics();
            if (fm == null) {
                return;
            }
            
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            
            // Create background rectangle
            int padding = 4;
            int backgroundX = location.getX() - padding;
            int backgroundY = location.getY() - textHeight - padding;
            int backgroundWidth = textWidth + (padding * 2);
            int backgroundHeight = textHeight + (padding * 2);
            
            // Draw semi-transparent background
            Color backgroundColor = new Color(0, 0, 0, 128); // Semi-transparent black
            graphics.setColor(backgroundColor);
            graphics.fillRect(backgroundX, backgroundY, backgroundWidth, backgroundHeight);
            
            // Draw text
            renderText(graphics, text, location, color);
        } catch (Exception e) {
            log.debug("Error rendering text with background: {}", e.getMessage());
        }
    }
    
    /**
     * Gets the abbreviated object type string.
     * 
     * @param objectType The object type
     * @return Abbreviated type string
     */
    private String getObjectTypeAbbreviation(Rs2ObjectModel.ObjectType objectType) {
        switch (objectType) {
            case GAME_OBJECT:
                return "GO";
            case WALL_OBJECT:
                return "WO";
            case DECORATIVE_OBJECT:
                return "DO";
            case GROUND_OBJECT:
                return "Gnd";
            case TILE_OBJECT:
                return "TO";
            default:
                return "?";
        }
    }
    
    /**
     * Gets the border color for a specific object type.
     * Can be overridden by subclasses to provide type-specific coloring.
     * 
     * @param objectType The object type
     * @return The border color for this object type, or default colors if not overridden
     */
    protected Color getBorderColorForObjectType(Rs2ObjectModel.ObjectType objectType) {
        switch (objectType) {
            case GAME_OBJECT:
                return GAME_OBJECT_COLOR;
            case WALL_OBJECT:
                return WALL_OBJECT_COLOR;
            case DECORATIVE_OBJECT:
                return DECORATIVE_OBJECT_COLOR;
            case GROUND_OBJECT:
                return GROUND_OBJECT_COLOR;
            default:
                return getDefaultBorderColor();
        }
    }
    
    /**
     * Gets the fill color for a specific object type.
     * Can be overridden by subclasses to provide type-specific coloring.
     * 
     * @param objectType The object type
     * @return The fill color for this object type, or null for default
     */
    protected Color getFillColorForObjectType(Rs2ObjectModel.ObjectType objectType) {
        Color borderColor = getBorderColorForObjectType(objectType);
        return new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), 50);
    }
    
    /**
     * Gets the border color for a specific object.
     * Can be overridden by subclasses to provide per-object coloring.
     * 
     * @param objectModel The object model
     * @return The border color for this object
     */
    protected Color getBorderColorForObject(Rs2ObjectModel objectModel) {
        // Check for object type-specific coloring first
        Color typeColor = getBorderColorForObjectType(objectModel.getObjectType());
        if (typeColor != null) {
            return typeColor;
        }
        
        return getDefaultBorderColor();
    }
    
    /**
     * Gets the fill color for a specific object.
     * Can be overridden by subclasses to provide per-object coloring.
     * 
     * @param objectModel The object model
     * @return The fill color for this object
     */
    protected Color getFillColorForObject(Rs2ObjectModel objectModel) {
        // Check for object type-specific coloring first
        Color typeColor = getFillColorForObjectType(objectModel.getObjectType());
        if (typeColor != null) {
            return typeColor;
        }
        
        // Create a fill color based on the border color
        Color borderColor = getBorderColorForObject(objectModel);
        return new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), 50);
    }
    
    // ============================================
    // Configuration Methods
    // ============================================
    
    public Rs2ObjectCacheOverlay setRenderHull(boolean renderHull) {
        this.renderHull = renderHull;
        return this;
    }
    
    public Rs2ObjectCacheOverlay setRenderClickbox(boolean renderClickbox) {
        this.renderClickbox = renderClickbox;
        return this;
    }
    
    public Rs2ObjectCacheOverlay setRenderTile(boolean renderTile) {
        this.renderTile = renderTile;
        return this;
    }
    
    public Rs2ObjectCacheOverlay setRenderOutline(boolean renderOutline) {
        this.renderOutline = renderOutline;
        return this;
    }
    
    public Rs2ObjectCacheOverlay setRenderFilter(Predicate<Rs2ObjectModel> renderFilter) {
        this.renderFilter = renderFilter;
        return this;
    }
    
    public Rs2ObjectCacheOverlay setRenderObjectInfo(boolean renderObjectInfo) {
        this.renderObjectInfo = renderObjectInfo;
        return this;
    }
    
    public Rs2ObjectCacheOverlay setRenderWorldCoordinates(boolean renderWorldCoordinates) {
        this.renderWorldCoordinates = renderWorldCoordinates;
        return this;
    }
    
    public Rs2ObjectCacheOverlay setOnlyShowTextOnHover(boolean onlyShowTextOnHover) {
        this.onlyShowTextOnHover = onlyShowTextOnHover;
        return this;
    }
    
    public Rs2ObjectCacheOverlay setRenderObjectName(boolean renderObjectName) {
        this.renderObjectName = renderObjectName;
        return this;
    }
    
    public Rs2ObjectCacheOverlay setEnableGameObjects(boolean enableGameObjects) {
        this.enableGameObjects = enableGameObjects;
        return this;
    }
    
    public Rs2ObjectCacheOverlay setEnableWallObjects(boolean enableWallObjects) {
        this.enableWallObjects = enableWallObjects;
        return this;
    }
    
    public Rs2ObjectCacheOverlay setEnableDecorativeObjects(boolean enableDecorativeObjects) {
        this.enableDecorativeObjects = enableDecorativeObjects;
        return this;
    }
    
    public Rs2ObjectCacheOverlay setEnableGroundObjects(boolean enableGroundObjects) {
        this.enableGroundObjects = enableGroundObjects;
        return this;
    }
}
