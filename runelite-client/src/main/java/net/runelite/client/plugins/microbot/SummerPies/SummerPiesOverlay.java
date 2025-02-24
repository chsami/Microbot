package net.runelite.client.plugins.microbot.SummerPies;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

public class SummerPiesOverlay extends Overlay {
    private BufferedImage summerPieImage;

    @Inject
    SummerPiesOverlay(SummerPiesPlugin plugin) {
        this.setPosition(OverlayPosition.TOP_LEFT);
        loadImage();
    }

    private void loadImage() {
        try {
            URL imageUrl = new URL("https://oldschool.runescape.wiki/images/Raw_summer_pie_detail.png");
            summerPieImage = ImageIO.read(imageUrl);
        } catch (IOException e) {
            System.err.println("Failed to load image: " + e.getMessage());
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        // Tamaño del overlay
        int panelWidth = 150;
        int panelHeight = 150;
        Dimension dimension = new Dimension(panelWidth, panelHeight);

        // Dibujar un fondo semitransparente
        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.fillRect(0, 0, panelWidth, panelHeight);

        // Dimensiones de la imagen
        int imgWidth = 64;
        int imgHeight = 64;

        // Posición centrada para la imagen
        int imgX = (panelWidth - imgWidth) / 2;
        int imgY = 20; // Espacio desde la parte superior

        if (summerPieImage != null) {
            graphics.drawImage(summerPieImage, imgX, imgY, imgWidth, imgHeight, null);
        } else {
            graphics.setColor(Color.RED);
            graphics.drawString("Image not loaded!", 10, 20);
        }

        // Dibujar el texto centrado debajo de la imagen
        String text = "Cooking: Raw Summer Pie";
        graphics.setFont(new Font("Arial", Font.BOLD, 12));
        graphics.setColor(Color.green);

        // Obtener el ancho del texto para centrarlo
        FontMetrics metrics = graphics.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int textX = (panelWidth - textWidth) / 2;
        int textY = imgY + imgHeight + 20; // Espacio debajo de la imagen

        graphics.drawString(text, textX, textY);

        return dimension;
    }
}
