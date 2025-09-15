package net.runelite.client.plugins.microbot.autoworldhopper;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.autoworldhopper.scripts.WorldHopScript;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;

import static net.runelite.client.ui.overlay.OverlayUtil.renderPolygon;

public class AutoWorldHopperOverlay extends OverlayPanel {
    
    private static final Color PLAYER_DETECTION_RADIUS = new Color(255, 165, 0, 50); // Orange translucent
    private static final Color DETECTED_PLAYER_OUTLINE = new Color(255, 69, 0); // Red-orange for detected players
    private static final Color FRIEND_PLAYER_OUTLINE = new Color(0, 255, 0); // Green for friends
    
    private final AutoWorldHopperConfig config;
    private final ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    private AutoWorldHopperOverlay(AutoWorldHopperConfig config, ModelOutlineRenderer modelOutlineRenderer) {
        this.config = config;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.HIGH);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.enabled()) {
            return null;
        }

        if (Microbot.getClient().getGameState() != net.runelite.api.GameState.LOGGED_IN) {
            return null;
        }

        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null) {
            return null;
        }

        renderPlayerDetectionRadius(graphics, localPlayer);
        renderDetectedPlayers(graphics);

        return super.render(graphics);
    }

    private void renderPlayerDetectionRadius(Graphics2D graphics, Player localPlayer) {
        if (!config.enablePlayerDetection() || !config.showPlayerRadius()) {
            return; // Don't show radius if player detection is disabled or radius display is off
        }

        LocalPoint playerLocation = localPlayer.getLocalLocation();
        if (playerLocation == null) {
            return;
        }

        // Render the detection radius circle
        int radiusTiles = config.detectionRadius();
        int sizeTiles = Math.max(1, radiusTiles * 2 + 1); // 0 -> 1x1, R -> (2R+1)x(2R+1)
        Polygon radiusPolygon = Perspective.getCanvasTileAreaPoly(
            Microbot.getClient(),
            playerLocation,
            sizeTiles
        );

        if (radiusPolygon != null) {
            renderPolygon(graphics, radiusPolygon, PLAYER_DETECTION_RADIUS);
            
            // Draw radius border
            graphics.setColor(new Color(255, 165, 0, 200));
            graphics.setStroke(new BasicStroke(2));
            graphics.draw(radiusPolygon);
        }
    }

    private void renderDetectedPlayers(Graphics2D graphics) {
        // Only render if plugin is enabled and player detection is active
        if (!config.enabled() || !config.enablePlayerDetection()) {
            return;
        }

        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null) {
            return;
        }

        List<Player> nearbyPlayers = Microbot.getClient().getPlayers();
        if (nearbyPlayers == null) {
            return;
        }

        int detectionRadius = config.detectionRadius();
        
        for (Player player : nearbyPlayers) {
            if (player == null || player == localPlayer) {
                continue;
            }

            // Check if player is within detection radius
            double distance = localPlayer.getWorldLocation().distanceTo(player.getWorldLocation());
            boolean withinRadius = (detectionRadius == 0) ? (distance == 0) : (distance <= detectionRadius);
            
            if (withinRadius) {
                renderDetectedPlayer(graphics, player);
            }
        }
    }

    private void renderDetectedPlayer(Graphics2D graphics, Player player) {
        try {
            // Determine color based on whether player is a friend
            boolean isFriend = Microbot.getClient().isFriended(player.getName(), false);
            Color outlineColor = isFriend ? FRIEND_PLAYER_OUTLINE : DETECTED_PLAYER_OUTLINE;
            
            // Render player outline
            modelOutlineRenderer.drawOutline(player, 2, outlineColor, 3);
            
            // Get player's canvas polygon for additional rendering
            Polygon playerPoly = Perspective.getCanvasTilePoly(Microbot.getClient(), player.getLocalLocation());
            if (playerPoly != null) {
                graphics.setColor(outlineColor);
                graphics.setStroke(new BasicStroke(2));
                graphics.draw(playerPoly);
                
                // Draw player name above the polygon
                Point textPoint = new Point(
                    (int) playerPoly.getBounds().getCenterX(),
                    (int) playerPoly.getBounds().getMinY() - 5
                );
                
                graphics.setColor(Color.WHITE);
                graphics.setFont(new Font("Arial", Font.BOLD, 11));
                String displayName = player.getName();
                if (displayName != null) {
                    FontMetrics fm = graphics.getFontMetrics();
                    int textWidth = fm.stringWidth(displayName);
                    graphics.drawString(
                        displayName, 
                        textPoint.getX() - textWidth / 2, 
                        textPoint.getY()
                    );
                }
            }
            
        } catch (Exception ex) {
            // Safely handle any rendering exceptions
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
    }

    public static void renderMinimapRect(Client client, Graphics2D graphics, Point center, int width, int height, Color color) {
        double angle = client.getCameraYawTarget() * Math.PI / 1024.0d;

        graphics.setColor(color);
        graphics.rotate(angle, center.getX(), center.getY());
        graphics.fillRect(center.getX() - width / 2, center.getY() - height / 2, width, height);
        graphics.rotate(-angle, center.getX(), center.getY());
    }
}
