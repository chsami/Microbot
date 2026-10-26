package net.runelite.client.plugins.microbot.questhelper.overlays;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.plugins.microbot.questhelper.QuestPause;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Shows PAUSED on the game screen while the quest automation is paused. */
public class QuestPauseOverlay extends Overlay
{
	@Inject
	public QuestPauseOverlay()
	{
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
		setDragTargetable(false);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!QuestPause.isPaused())
		{
			return null;
		}
		String text = "QUEST HELPER PAUSED";
		graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 18f));
		int width = graphics.getFontMetrics().stringWidth(text) + 20;
		int height = 30;
		graphics.setColor(new Color(0, 0, 0, 170));
		graphics.fillRoundRect(0, 0, width, height, 8, 8);
		graphics.setColor(new Color(235, 70, 55));
		graphics.drawString(text, 10, 21);
		return new Dimension(width, height);
	}
}
