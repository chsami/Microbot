package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class DDChopperOverlay extends OverlayPanel
{
	/** Wide enough that tree, spot and status values stay on one line. */
	private static final int PANEL_WIDTH = 220;

	private final DDChopperScript script;
	private final DDChopperConfig config;

	@Inject
	DDChopperOverlay(DDChopperScript script, DDChopperConfig config)
	{
		this.script = script;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setNaughty();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.setPreferredSize(new Dimension(PANEL_WIDTH, 0));
		panelComponent.getChildren().add(TitleComponent.builder().text("DD Chopper").color(Color.GREEN).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Level").right(
			config.goalType() == GoalType.TARGET_LEVEL ? script.getCurrentLevel() + " -> " + config.targetLevel() : script.getCurrentLevel() + " (" + config.goalType() + ")").build());
		panelComponent.getChildren().add(LineComponent.builder().left("XP/hr").right(
			String.format("%,d", script.getXpPerHour())).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Next lvl").right(script.getNextLevelEta()).build());
		String goalLabel = script.getGoalEtaLabel();
		if (goalLabel != null)
		{
			panelComponent.getChildren().add(LineComponent.builder().left(goalLabel).right(script.getGoalEta()).build());
		}
		panelComponent.getChildren().add(LineComponent.builder().left("XP gained").right(
			String.format("%,d", script.getXpGained())).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Tree").right(script.getTreeLabel()).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Spot time").right(script.getSpotTimeLeft()).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Axe").right(script.getAxeLabel()).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Mode").right(config.mode().toString()).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Break").right(script.getBreakText()).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Status").right(script.getStatus()).build());
		return super.render(graphics);
	}
}
