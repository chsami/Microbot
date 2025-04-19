package net.runelite.client.plugins.microbot.runecrafting.chillRunecraft;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.client.plugins.microbot.runecrafting.chillRunecraft.AutoRunecraftScript.*;

public class AutoRunecraftOverlay extends OverlayPanel
{

    @Inject
    AutoRunecraftOverlay(AutoRunecraftPlugin plugin)
    {
        super(plugin);
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
                    .text("ChillX Auto RC v1.0.3")
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runs Completed: " + runsCompleted)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runecraft Level: " + runecraftLevel + " (+" + (runecraftLevel - initialRunecraftLevel) + ")")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runecraft XP: " + runecraftXp + " (+" + (runecraftXp - initialRunecraftXp) + ")")
                    .build());

        } catch(Exception ex)
        {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
