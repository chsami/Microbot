package net.runelite.client.plugins.microbot.questhelper.steps;

import lombok.Getter;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.steps.widget.WidgetDetails;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import java.awt.*;
import java.util.HashSet;
public class PuzzleStep extends DetailedQuestStep
{

	ButtonHighlighCalculator highlightCalculator;
	@Getter
	private HashSet<WidgetDetails> highlightedButtons = new HashSet<>();

	public PuzzleStep(QuestHelper questHelper, ButtonHighlighCalculator highlightCalculator, Requirement... requirements)
	{
		this(questHelper, "Click the highlighted buttons to complete the puzzle", highlightCalculator, requirements);
	}

	public PuzzleStep(QuestHelper questHelper, String text, ButtonHighlighCalculator highlightCalculator, Requirement... requirements)
	{
		super(questHelper, text, requirements);
		this.highlightCalculator = highlightCalculator;
	}

	@Override
	public void startUp()
	{
		this.highlightedButtons = highlightCalculator.getHighlightedButtons();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		super.onGameTick(event);
		this.highlightedButtons = highlightCalculator.getHighlightedButtons();
	}

	@Override
	public void makeWidgetOverlayHint(Graphics2D graphics, QuestHelperPlugin plugin)
	{
		super.makeWidgetOverlayHint(graphics, plugin);
		for (WidgetDetails button : highlightedButtons)
		{
			if (button == null)
			{
				continue;
			}

			Widget widget = client.getWidget(button.getGroupID(), button.getChildID());
			if (widget != null)
			{
				graphics.setColor(new Color(questHelper.getConfig().targetOverlayColor().getRed(),
					questHelper.getConfig().targetOverlayColor().getGreen(),
					questHelper.getConfig().targetOverlayColor().getBlue(), 65));
				graphics.fill(widget.getBounds());
				graphics.setColor(questHelper.getConfig().targetOverlayColor());
				graphics.draw(widget.getBounds());
			}

		}
	}

	public interface ButtonHighlighCalculator
	{
		HashSet<WidgetDetails> getHighlightedButtons();
	}
}
