package net.runelite.client.plugins.microbot.util.cache.overlay;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.cache.Rs2NpcCache;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2NpcCacheUtils;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.Text;
import java.awt.*;
import java.util.function.Predicate;

/**
 * Overlay for rendering cached NPCs with various highlight options.
 * Based on RuneLite's NpcOverlay patterns but using the cache system.
 * 
 * @author Vox
 * @version 1.0
 */
public class Rs2NpcCacheOverlay extends Rs2BaseCacheOverlay {
    
    // NPC-specific colors
    private static final Color NPC_BORDER_COLOR = Color.ORANGE;
    private static final Color NPC_FILL_COLOR = new Color(255, 165, 0, 50); // Orange with alpha
    private static final Color NPC_INTERACTING_COLOR = Color.RED; // Color for NPCs interacting with player
    private static final Color NPC_INTERACTING_FILL_COLOR = new Color(255, 0, 0, 50); // Red with alpha
    
    // Rendering options
    private boolean renderHull = true;
    private boolean renderTile = false;
    private boolean renderTrueTile = false;
    private boolean renderOutline = false;
    private boolean renderName = false;
    private boolean renderNpcInfo = true; // Show NPC ID
    private boolean renderWorldCoordinates = false; // Show world coordinates
    private boolean renderCombatLevel = false; // Show combat level
    private boolean renderDistance = false; // Show distance from player
    private boolean onlyShowTextOnHover = true; // Only show text when mouse is hovering
    private Predicate<Rs2NpcModel> renderFilter;
    
    public Rs2NpcCacheOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer) {
        super(client, modelOutlineRenderer);
    }
    
    @Override
    protected Color getDefaultBorderColor() {
        return NPC_BORDER_COLOR;
    }
    
    @Override
    protected Color getDefaultFillColor() {
        return NPC_FILL_COLOR;
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        if (!isClientReady()) {
            return null;
        }
        if (Rs2NpcCache.getInstance() == null || Rs2NpcCache.getInstance().size() == 0) {
            return null; // No NPCs to render
        }
        // Render all visible NPCs from cache
        Rs2NpcCache.getAllNpcs()
                .filter(npc -> renderFilter == null || renderFilter.test(npc))
                .filter(Rs2NpcCacheUtils::isVisibleInViewport)
                .forEach(npc -> renderNpcOverlay(graphics, npc));
        
        return null;
    }
    
    /**
     * Renders a single NPC with the configured options.
     * 
     * @param graphics The graphics context
     * @param npcModel The NPC model to render
     */
    private void renderNpcOverlay(Graphics2D graphics, Rs2NpcModel npcModel) {
        try {
            // Get the underlying NPC from the model
            NPC npc = npcModel.getRuneliteNpc();
            
            NPCComposition npcComposition = npc.getTransformedComposition();
            if (npcComposition == null || !npcComposition.isInteractible()) {
                return;
            }
            
            Color borderColor = getBorderColorForNpc(npcModel);
            Color fillColor = getFillColorForNpc(npcModel);
            
            // Check if NPC is interacting with the player to override colors
            if (isNpcInteractingWithPlayer(npc)) {
                borderColor = NPC_INTERACTING_COLOR;
                fillColor = NPC_INTERACTING_FILL_COLOR;
            }
            
            float borderWidth = DEFAULT_BORDER_WIDTH;
            Stroke stroke = new BasicStroke(borderWidth);
            
            // Render convex hull
            if (renderHull) {
                Shape hull = npc.getConvexHull();
                renderPolygon(graphics, hull, borderColor, fillColor, stroke);
            }
            
            // Render tile
            if (renderTile) {
                Polygon tilePoly = npc.getCanvasTilePoly();
                renderPolygon(graphics, tilePoly, borderColor, fillColor, stroke);
            }
            
            // Render true tile (centered)
            if (renderTrueTile) {
                renderTrueTile(graphics, npc, npcComposition, borderColor, fillColor, stroke);
            }
            
            // Render outline
            if (renderOutline) {
                modelOutlineRenderer.drawOutline(npc, (int) borderWidth, borderColor, 0);
            }
            
            // Render NPC info (including name if enabled and hovering)
            if (renderNpcInfo || renderWorldCoordinates || renderCombatLevel || renderDistance || renderName) {
                renderNpcInfo(graphics, npc);
            }
            
        } catch (Exception e) {
            // Silent fail to avoid spam
        }
    }
    
    /**
     * Renders the true tile (centered on NPC).
     */
    private void renderTrueTile(Graphics2D graphics, NPC npc, NPCComposition composition, 
                               Color borderColor, Color fillColor, Stroke stroke) {
        LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), npc.getWorldLocation());
        if (lp != null) {
            final int size = composition.getSize();
            final LocalPoint centerLp = lp.plus(
                Perspective.LOCAL_TILE_SIZE * (size - 1) / 2,
                Perspective.LOCAL_TILE_SIZE * (size - 1) / 2);
            Polygon tilePoly = Perspective.getCanvasTileAreaPoly(client, centerLp, size);
            renderPolygon(graphics, tilePoly, borderColor, fillColor, stroke);
        }
    }
    
    /**
     * Checks if the mouse is hovering over an NPC.
    
    /**
     * Renders NPC information (name, ID, coordinates, combat level, distance) above the NPC.
     * All information is displayed in a single line with hover detection and background.
     */
    private void renderNpcInfo(Graphics2D graphics, NPC npc) {
        // Check if we should only show text on hover
        boolean isHovering = isMouseHoveringOver(npc);
        if (onlyShowTextOnHover && !isHovering) {
            return;
        }
        
        // Build detailed information lines (always build them when renderNpcInfo is called)
        java.util.List<String> infoLines = new java.util.ArrayList<>();
            
            // Add NPC name if enabled
            if (renderName && npc.getName() != null) {
                infoLines.add("Name: " + Text.removeTags(npc.getName()));
            }
            
            // Add NPC ID if enabled
            if (renderNpcInfo) {
                infoLines.add("ID: " + npc.getId());
            }
            
            // Add combat level if enabled
            if (renderCombatLevel) {
                infoLines.add("Combat Level: " + npc.getCombatLevel());
            }
            
            // Add world coordinates if enabled
            if (renderWorldCoordinates) {
                WorldPoint wp = npc.getWorldLocation();
                infoLines.add("Coords: " + wp.getX() + ", " + wp.getY() + " (Plane " + wp.getPlane() + ")");
            }
            
            // Add distance from player if enabled
            if (renderDistance) {
                Player player = client.getLocalPlayer();
                if (player != null) {
                    int distance = (int) player.getWorldLocation().distanceTo(npc.getWorldLocation());
                    infoLines.add("Distance: " + distance + " tiles");
                }
            }
            
            // Add NPC composition details
            NPCComposition npcComposition = npc.getComposition();
            if (npcComposition != null) {
                if (npcComposition.getSize() != 1) {
                    infoLines.add("Size: " + npcComposition.getSize() + "x" + npcComposition.getSize());
                }
                
                // Add actions if available
                String[] actions = npcComposition.getActions();
                if (actions != null && actions.length > 0) {
                    java.util.List<String> validActions = new java.util.ArrayList<>();
                    for (String action : actions) {
                        if (action != null && !action.trim().isEmpty()) {
                            validActions.add(action);
                        }
                    }
                    if (!validActions.isEmpty()) {
                        infoLines.add("Actions: " + String.join(", ", validActions));
                    }
                }
            }
            
            // Add interaction status
            if (isNpcInteractingWithPlayer(npc)) {
                infoLines.add("Status: Interacting");
            }
            
            // Only set hover info if we have information to display
            if (!infoLines.isEmpty()) {
                Color borderColor = isNpcInteractingWithPlayer(npc) ? NPC_INTERACTING_COLOR : getDefaultBorderColor();
                String entityType = "NPC";
                
                // Use NPC's canvas location for positioning, or mouse position if hovering
                net.runelite.api.Point displayLocation;
                if (isHovering) {
                    displayLocation = client.getMouseCanvasPosition();
                } else {
                    // Use NPC's text location for non-hover display
                    displayLocation = npc.getCanvasTextLocation(graphics, "", npc.getLogicalHeight() + 60);
                }
                
                if (displayLocation != null) {
                    HoverInfoContainer.HoverInfo hoverInfo = new HoverInfoContainer.HoverInfo(
                        infoLines, displayLocation, borderColor, entityType);
                    HoverInfoContainer.setHoverInfo(hoverInfo);
                }
            }
    }
    
    /**
     * Checks if the mouse is hovering over an NPC.
     * 
     * @param npc The NPC to check
     * @return true if mouse is hovering over the NPC
     */
    private boolean isMouseHoveringOver(NPC npc) {
        net.runelite.api.Point mousePos = client.getMouseCanvasPosition();
        if (mousePos == null) {
            return false;
        }
        
        // Check if mouse is over the NPC's convex hull
        Shape hull = npc.getConvexHull();
        if (hull != null) {
            return hull.contains(mousePos.getX(), mousePos.getY());
        }
        
        // Fallback to tile poly
        Polygon tilePoly = npc.getCanvasTilePoly();
        return tilePoly != null && tilePoly.contains(mousePos.getX(), mousePos.getY());
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
        FontMetrics fm = graphics.getFontMetrics();
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
    }
    
    /**
     * Renders multiple lines of text above an NPC with proper spacing.
     * 
     * @param graphics The graphics context
     * @param npc The NPC to render text above
     * @param lines The lines of text to render
     * @param color The color to use
    /**
     * Checks if an NPC is interacting with the player.
     * An NPC is considered interacting if it's targeting/attacking the player.
     */
    private boolean isNpcInteractingWithPlayer(NPC npc) {
        Actor interacting = npc.getInteracting();
        return interacting != null && interacting.equals(client.getLocalPlayer());
    }
    
    /**
     * Gets the border color for a specific NPC.
     * Can be overridden by subclasses to provide per-NPC coloring.
     * 
     * @param npcModel The NPC model
     * @return The border color for this NPC
     */
    protected Color getBorderColorForNpc(Rs2NpcModel npcModel) {
        return getDefaultBorderColor();
    }
    
    /**
     * Gets the fill color for a specific NPC.
     * Can be overridden by subclasses to provide per-NPC coloring.
     * 
     * @param npcModel The NPC model
     * @return The fill color for this NPC
     */
    protected Color getFillColorForNpc(Rs2NpcModel npcModel) {
        return getDefaultFillColor();
    }
    
    // ============================================
    // Configuration Methods
    // ============================================
    
    public Rs2NpcCacheOverlay setRenderHull(boolean renderHull) {
        this.renderHull = renderHull;
        return this;
    }
    
    public Rs2NpcCacheOverlay setRenderTile(boolean renderTile) {
        this.renderTile = renderTile;
        return this;
    }
    
    public Rs2NpcCacheOverlay setRenderTrueTile(boolean renderTrueTile) {
        this.renderTrueTile = renderTrueTile;
        return this;
    }
    
    public Rs2NpcCacheOverlay setRenderOutline(boolean renderOutline) {
        this.renderOutline = renderOutline;
        return this;
    }
    
    public Rs2NpcCacheOverlay setRenderName(boolean renderName) {
        this.renderName = renderName;
        return this;
    }
    
    public Rs2NpcCacheOverlay setRenderNpcInfo(boolean renderNpcInfo) {
        this.renderNpcInfo = renderNpcInfo;
        return this;
    }
    
    public Rs2NpcCacheOverlay setRenderWorldCoordinates(boolean renderWorldCoordinates) {
        this.renderWorldCoordinates = renderWorldCoordinates;
        return this;
    }
    
    public Rs2NpcCacheOverlay setRenderCombatLevel(boolean renderCombatLevel) {
        this.renderCombatLevel = renderCombatLevel;
        return this;
    }
    
    public Rs2NpcCacheOverlay setRenderDistance(boolean renderDistance) {
        this.renderDistance = renderDistance;
        return this;
    }
    
    public Rs2NpcCacheOverlay setOnlyShowTextOnHover(boolean onlyShowTextOnHover) {
        this.onlyShowTextOnHover = onlyShowTextOnHover;
        return this;
    }
    
    public Rs2NpcCacheOverlay setRenderFilter(Predicate<Rs2NpcModel> renderFilter) {
        this.renderFilter = renderFilter;
        return this;
    }
}
