package net.runelite.client.plugins.microbot.agility;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class MicroAgilityOverlay extends OverlayPanel
{
	final MicroAgilityPlugin plugin;
	final MicroAgilityConfig config;

	@Inject
	MicroAgilityOverlay(MicroAgilityPlugin plugin, MicroAgilityConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setNaughty();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		try
		{
			panelComponent.setPreferredSize(new Dimension(200, 300));
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("Micro Agility V" + AgilityScript.version)
				.color(Color.GREEN)
				.build());

			panelComponent.getChildren().add(LineComponent.builder().build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Agility Exp")
				.right(Integer.toString(Microbot.getClient().getSkillExperience(Skill.AGILITY)))
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Current Obstacle")
				.right(Integer.toString(config.agilityCourse().getHandler().getCurrentObstacleIndex()))
				.build());

		}
		catch (Exception ex)
		{
			Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
		}
		return super.render(graphics);
	}
}
