package net.runelite.client.plugins.microbot.banksorter;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;

public class BankTabSorterOverlay extends Overlay {

    private final BankTabSorterPlugin plugin;
    private final Client client;
    private final Color BUTTON_COLOR = new Color(60, 60, 60, 220);
    private final Color BUTTON_HOVER_COLOR = new Color(80, 80, 80, 240);
    private final Color BUTTON_TEXT_COLOR = new Color(255, 223, 0);
    private final Color BUTTON_BORDER_COLOR = new Color(0, 0, 0, 255);

    @Inject
    private BankTabSorterOverlay(BankTabSorterPlugin plugin, Client client) {
        this.plugin = plugin;
        this.client = client;

        // Set overlay properties to ensure proper rendering
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        // Check if bank is open
        Widget bankContainer = client.getWidget(ComponentID.BANK_CONTAINER);
        if (bankContainer == null || bankContainer.isHidden()) {
            return null;
        }

        if (Rs2Bank.getCurrentTab() == 0) {
            return null;
        }

        // Check if placeholders are enabled and show warning if not
        Widget placeholderButton = client.getWidget(786472);
        boolean placeholdersEnabled = placeholderButton != null && placeholderButton.getSpriteId() == 179;

        // Get the bank title bar widget
        Widget titleBar = client.getWidget(ComponentID.BANK_TITLE_BAR);
        if (titleBar == null) {
            return null;
        }

        // Get the tutorial button (positioned to the right in the bank interface)
        Widget tutorialButton = client.getWidget(ComponentID.BANK_TUTORIAL_BUTTON);
        if (tutorialButton == null) {
            // If tutorial button not found, use a fallback position
            int buttonWidth = 70;
            int buttonHeight = 20;
            int x = titleBar.getCanvasLocation().getX() + 150;
            int y = titleBar.getCanvasLocation().getY() + 2;
            return renderButton(graphics, x, y, buttonWidth, buttonHeight, placeholdersEnabled);
        }

        // Create a button to the left of the "show tutorial" button
        int buttonWidth = 70;
        int buttonHeight = 20;

        // Position it to the left of the tutorial button
        int x = tutorialButton.getCanvasLocation().getX() - buttonWidth - 8;
        int y = tutorialButton.getCanvasLocation().getY();

        return renderButton(graphics, x, y, buttonWidth, buttonHeight, placeholdersEnabled);
    }

    private Dimension renderButton(Graphics2D graphics, int x, int y, int width, int height, boolean placeholdersEnabled) {
        // Save original settings
        Stroke originalStroke = graphics.getStroke();
        Font originalFont = graphics.getFont();

        // Set button bounds for click detection
        Rectangle buttonBounds = new Rectangle(x, y, width, height);
        plugin.setSortButtonBounds(buttonBounds);

        // Use anti-aliasing for smoother rendering
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw button background with stronger opacity
        Color buttonColor = placeholdersEnabled ?
                (plugin.isHovering() ? BUTTON_HOVER_COLOR : BUTTON_COLOR) :
                new Color(150, 60, 60, 220); // Red-tinted when placeholders disabled

        graphics.setColor(buttonColor);
        graphics.fillRect(x, y, width, height);

        // Draw button border with thicker line
        graphics.setColor(BUTTON_BORDER_COLOR);
        graphics.setStroke(new BasicStroke(2));
        graphics.drawRect(x, y, width, height);

        // Draw button text with a slightly bolder font
        graphics.setColor(BUTTON_TEXT_COLOR);
        Font boldFont = graphics.getFont().deriveFont(Font.BOLD, 12f);
        graphics.setFont(boldFont);

        FontMetrics fm = graphics.getFontMetrics();
        String text = placeholdersEnabled ? "Sort Tab" : "Enable PH";
        int textX = x + (width - fm.stringWidth(text)) / 2;
        int textY = y + ((height - fm.getHeight()) / 2) + fm.getAscent();
        graphics.drawString(text, textX, textY);

        // Restore original settings
        graphics.setStroke(originalStroke);
        graphics.setFont(originalFont);

        return new Dimension(width, height);
    }

}
