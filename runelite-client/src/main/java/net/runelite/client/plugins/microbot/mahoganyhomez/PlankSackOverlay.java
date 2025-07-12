package net.runelite.client.plugins.microbot.mahoganyhomez;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

import javax.inject.Inject;
import java.awt.*;

public class PlankSackOverlay extends WidgetItemOverlay
{
    private final MahoganyHomesPlugin plugin;

    @Inject
    PlankSackOverlay(MahoganyHomesPlugin plugin)
    {
        this.plugin = plugin;
        showOnInventory();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        if (itemId != ItemID.PLANK_SACK)
        {
            return;
        }

        String text = plugin.getPlankCount() == -1 ? "?" : plugin.getPlankCount() + "";

        drawString(graphics, text, widgetItem.getCanvasBounds().x, widgetItem.getCanvasBounds().y + 10);
    }

    private void drawString(Graphics2D graphics, String text, int drawX, int drawY)
    {
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, drawX + 1, drawY + 1);
        graphics.setColor(plugin.getColour());
        graphics.drawString(text, drawX, drawY);
    }
}