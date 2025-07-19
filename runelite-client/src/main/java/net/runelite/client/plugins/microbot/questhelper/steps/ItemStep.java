package net.runelite.client.plugins.microbot.questhelper.steps;

import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.steps.overlay.DirectionArrow;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.awt.*;

public class ItemStep extends DetailedQuestStep
{


	public ItemStep(QuestHelper questHelper, WorldPoint worldPoint, String text, Requirement... requirements)
	{
		super(questHelper, worldPoint, text, requirements);
	}

	public ItemStep(QuestHelper questHelper, String text, Requirement... requirements)
	{
		super(questHelper, text, requirements);
	}

	@Override
	public void renderArrow(Graphics2D graphics)
	{
		tileHighlights.forEach((tile, ids) -> {
			LocalPoint lp = tile.getLocalLocation();

			Polygon poly = Perspective.getCanvasTilePoly(client, lp, 30);
			if (poly == null || poly.getBounds() == null)
			{
				return;
			}

			int startX = poly.getBounds().x + (poly.getBounds().width / 2);
			int startY =  poly.getBounds().y + (poly.getBounds().height / 2);

			DirectionArrow.drawWorldArrow(graphics, questHelper.getConfig().targetOverlayColor(), startX, startY);
		});
	}
}
