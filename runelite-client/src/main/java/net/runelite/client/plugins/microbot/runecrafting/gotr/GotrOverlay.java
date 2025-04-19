package net.runelite.client.plugins.microbot.runecrafting.gotr;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;



public class GotrOverlay extends OverlayPanel {

    private final GotrPlugin plugin;
    public static Color PUBLIC_TIMER_COLOR = Color.YELLOW;
    public static int TIMER_OVERLAY_DIAMETER = 20;
    private final ProgressPieComponent progressPieComponent = new ProgressPieComponent();

    int sleepingCounter;

    @Inject
    GotrOverlay(GotrPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro Guardians of the rift V" + GotrScript.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("STATE: " + GotrScript.state)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Elemental points: " + GotrScript.elementalRewardPoints)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Catalytic points: " + GotrScript.catalyticRewardPoints)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time since portal: " + GotrScript.getTimeSincePortal())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total time script loop: " + GotrScript.totalTime + "ms")
                    .build());

        } catch(Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
