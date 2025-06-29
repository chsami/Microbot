package net.runelite.client.plugins.microbot.cluesolver;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ButtonComponent;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.*;

@Setter
@Slf4j
public class ClueSolverOverlay extends OverlayPanel {

    @Setter
    private String currentTaskStatus = "Idle";  // Default message
    public final ButtonComponent myButton;

    @Inject
    public ClueSolverOverlay(ClueSolverPlugin plugin) {
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();

        // Initialize the button with a label and preferred size
        myButton = new ButtonComponent("Start");
        myButton.setPreferredSize(new Dimension(100, 30));
        myButton.setParentOverlay(this);
        myButton.setFont(FontManager.getRunescapeBoldFont());
        myButton.setOnClick(() -> {
            boolean isRunning = !ClueSolverPlugin.isSolverRunning;
            // Handle button click
            if (!isRunning) {
                Rs2Walker.setTarget(null);
                myButton.setText("Start");
                plugin.configureSolver();
            } else {
                myButton.setText("Stop");
                plugin.configureSolver();
            }
        });
    }

    /**
     * Updates the overlay message with the current task status.
     * @param status The current status message of the task.
     */
    public void updateTaskStatus(String status) {
        setCurrentTaskStatus(status);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
//            graphics.setColor(Color.WHITE);
//            graphics.drawString("Clue Solver Status:", 10, 20);
//
//            // Display the current task status dynamically
//            graphics.drawString(currentTaskStatus, 10, 40);

            panelComponent.getChildren().add(LineComponent.builder().build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Clue Solver")
                    .right("Status: " + currentTaskStatus)
                    .build());

            // Add the button to the overlay panel
            panelComponent.getChildren().add(myButton);
            // Placeholder for additional information (e.g., clue type, progress)
            // graphics.drawString("Clue Type: " + getClueType(), 10, 60);

        } catch (Exception ex) {
            System.out.println("Error in render: " + ex.getMessage());
        }
        return super.render(graphics);
    }

    /**
     * Example method to fetch the current clue type or details (optional).
     * This method can be used to get more details from ClueSolverPlugin or tasks.
     */
    private String getClueType() {
        // Placeholder: logic to obtain current clue type, if available
        return "Example Clue Type";
    }
}
