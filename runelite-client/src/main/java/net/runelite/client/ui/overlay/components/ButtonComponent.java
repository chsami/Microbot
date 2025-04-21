/*
 * Example ButtonComponent class.
 */
package net.runelite.client.ui.overlay.components;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseListener;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

@Slf4j
@Setter
public class ButtonComponent implements LayoutableRenderableEntity
{


    final MouseListener mouseListener = new MouseAdapter()
    {
        @Override
        public MouseEvent mouseMoved(MouseEvent mouseEvent)
        {
            if (mouseEvent.getSource().toString().equals("Microbot"))
                return mouseEvent;
            //                log.info("Hovering over button at: " + mouseEvent.getPoint());
            //                log.info("Not hovering over button at: " + mouseEvent.getPoint());
            isHovered = isMouseOver(mouseEvent.getPoint());
            return mouseEvent;
        }

        @Override
        public MouseEvent mousePressed(MouseEvent mouseEvent)
        {
            if(!isMouseOver(mouseEvent.getPoint()))
            {
                return mouseEvent;
            }
            if (mouseEvent.getSource().toString().equals("Microbot"))
                return mouseEvent;
            if (SwingUtilities.isLeftMouseButton(mouseEvent))
            {
                    Microbot.getClientThread().invokeLater(() ->
                    {
                        if (isHovered && isEnabled)
                        {
                            log.info("Button clicked at: " + mouseEvent.getPoint());
                            if (onClick != null)
                            {
                                onClick.run();
                            }
                        }
                    });
                    mouseEvent.consume();

                //mouseEvent.consume();
            }

            return mouseEvent;
        }
    };

    // constructor
    public ButtonComponent(String text)
    {
        this.text = text;
        this.preferredSize = new Dimension(100, 30);
    }

    // init mouse listener
    public void hookMouseListener()
    {
        Microbot.getMouseManager().registerMouseListener(mouseListener);
    }
    // unhook mouse listener
    public void unhookMouseListener()
    {
        Microbot.getMouseManager().unregisterMouseListener(mouseListener);
    }

    // The button text to display
    private String text;

    // Default styles
    private Font font = new Font("Arial", Font.PLAIN, 12);
    private Color backgroundColor = Color.LIGHT_GRAY;
    private Color textColor = Color.BLACK;

    @Getter
    private final Rectangle bounds = new Rectangle();
    @Setter
    @Getter
    private OverlayPanel parentOverlay;

    private Point preferredLocation = new Point();
    private Dimension preferredSize;

    // The button's action listener
    private Runnable onClick;

    // The button's hover state
    private boolean isHovered;

    // The button's enabled state
    private boolean isEnabled = true;

    // The button's tooltip text
    private String tooltip;

    // The button's text color when hovered
    private Color hoverTextColor = textColor.brighter();



    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (text == null || !isEnabled)
        {
            return null;
        }

        // Set the font and calculate text dimensions
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int textHeight = metrics.getHeight();
        int padding = 10; // Padding around text

        // Determine button dimensions based on text size and padding
        int buttonWidth = textWidth + (padding * 2);
        int buttonHeight = textHeight + (padding * 2);

        // Use preferredSize if it has been set
        if (preferredSize != null)
        {
            buttonWidth = Math.max(buttonWidth, preferredSize.width);
            buttonHeight = Math.max(buttonHeight, preferredSize.height);
        }

        // Draw button background
        graphics.setColor(isHovered ? backgroundColor.darker() : backgroundColor);
        graphics.fillRect(preferredLocation.x, preferredLocation.y, buttonWidth, buttonHeight);

        // Draw button border
        graphics.setColor(Color.BLACK);
        graphics.drawRect(preferredLocation.x, preferredLocation.y, buttonWidth, buttonHeight);

        // Draw centered button text
        graphics.setColor(isHovered ? hoverTextColor : textColor);
        int textX = preferredLocation.x + (buttonWidth - textWidth) / 2;
        int textY = preferredLocation.y + (buttonHeight - textHeight) / 2 + metrics.getAscent();
        graphics.drawString(text, textX, textY);

        // Update the bounds for potential click handling or layout purposes
        bounds.setLocation(preferredLocation);
        bounds.setSize(buttonWidth, buttonHeight);

        return new Dimension(buttonWidth, buttonHeight);
    }

    @Override
    public void setPreferredSize(Dimension dimension)
    {
        this.preferredSize = dimension;
    }

    // boolean to check if the mouse is within the button bounds
    private boolean isMouseOver(Point mousePoint)
    {
        Rectangle localBounds = getBounds();
        Rectangle panelBounds = parentOverlay.getBounds();
        // Get true bounds of the button relative to the panel
        Rectangle trueBounds = new Rectangle(
                localBounds.x + panelBounds.x,
                localBounds.y + panelBounds.y,
                localBounds.width,
                localBounds.height
        );
        return trueBounds.contains(mousePoint);
    }

}
