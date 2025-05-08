//package net.runelite.client.plugins.microbot.storm.plugins.PlayerMonitor;
//
//import java.awt.*;
//
//import net.runelite.client.plugins.microbot.Microbot;
//import net.runelite.client.ui.overlay.Overlay;
//import net.runelite.client.ui.overlay.OverlayLayer;
//import net.runelite.client.ui.overlay.OverlayPosition;
//import net.runelite.client.ui.overlay.OverlayPriority;
//
//public class FlashOverlay extends Overlay {
//    private Color flashColor = new Color(0, 0, 0, 0); // Start with fully transparent color
//
//    // Zero-argument constructor for dependency injection
//    public FlashOverlay() {
//        setPosition(OverlayPosition.DYNAMIC);
//        setLayer(OverlayLayer.ABOVE_SCENE);
//        setPriority(OverlayPriority.HIGH);
//    }
//    // Setter method for the color
//    public void setFlashColor(Color newColor) {
//        this.flashColor = newColor;
//    }
//    public Color getFlashColor(){
//        return this.flashColor;
//    }
//
//    @Override
//    public Dimension render(Graphics2D g) {
//        if (flashColor.getAlpha() > 0) { // Only draw if not fully transparent
//            g.setColor(flashColor);
//            g.fillRect(0, 0, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()); // Cover the entire screen
//        }
//        return null;
//    }
//}
