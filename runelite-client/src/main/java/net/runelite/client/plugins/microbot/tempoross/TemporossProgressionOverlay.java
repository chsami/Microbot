package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.client.plugins.microbot.tempoross.State.getAllFish;
import static net.runelite.client.plugins.microbot.tempoross.State.getTotalAvailableFishSlots;

public class TemporossProgressionOverlay extends OverlayPanel {

    private final TemporossPlugin plugin;

    @Inject
    public TemporossProgressionOverlay(TemporossPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_CENTER); // Adjust position as needed
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (TemporossScript.isInMinigame()) {
            State currentState = TemporossScript.state;
            if (currentState != null) {
                // Set up the panel's visual properties
                panelComponent.setPreferredSize(new Dimension(300, 150));
                panelComponent.setBackgroundColor(new Color(60, 60, 60, 180)); // Semi-transparent background

                // Title component
                panelComponent.getChildren().add(TitleComponent.builder()
                        .text("Tempoross Progression")
                        .color(Color.CYAN)
                        .build());

                // Add current state as a line component
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Current State:")
                        .right(currentState.name())
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Is completed:")
                        .right(currentState.isComplete() ? "Yes" : "No")
                        .build());

                // Add fish count
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Fish count:")
                        .right(String.valueOf(getAllFish()))
                        .build());
                // Add cooked fish count
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Cooked fish count:")
                        .right(String.valueOf(State.getCookedFish()))
                        .build());
                // Add raw fish count
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Raw fish count:")
                        .right(String.valueOf(State.getRawFish()))
                        .build());
                // Add total available fish slots
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Total available fish slots:")
                        .right(String.valueOf(getTotalAvailableFishSlots()))
                        .build());
                if(currentState.isComplete()){
                    TemporossScript.isFilling = false;
                    TemporossScript.state = currentState.next == null ? State.THIRD_CATCH : currentState.next;
                }

                // Add progression bar
                double progression = calculateProgression(currentState);
                final ProgressBarComponent progressBar = new ProgressBarComponent();
                progressBar.setValue((int) (progression * 100));
                progressBar.setMaximum(100);
                progressBar.setForegroundColor(new Color(37, 196, 37, 255));
                progressBar.setBackgroundColor(new Color(255, 0, 0, 255));
                progressBar.setPreferredSize(new Dimension(280, 30));
                progressBar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.PERCENTAGE);

                panelComponent.getChildren().add(progressBar);
            }
        }
        return super.render(graphics);
    }

    private double calculateProgression(State state) {
        switch (state) {
            case ATTACK_TEMPOROSS:
                // Progression based on energy level, capped at 94 (target).
                return Math.min(TemporossScript.ENERGY / 94.0, 1.0);

            case SECOND_FILL:
                // Progression goes up as cooked fish decreases (0 fish = 100% progress).
                int cookedFishSecondFill = State.getCookedFish();
                return 1.0 - Math.min((double) cookedFishSecondFill / (TemporossScript.temporossConfig.solo() ? 19 : getTotalAvailableFishSlots()), 1.0);

            case INITIAL_FILL:
                // Progression goes up as cooked fish decreases (0 fish = 100% progress).
                int cookedFishInitialFill = State.getCookedFish();
                return 1.0 - Math.min((double) cookedFishInitialFill / (TemporossScript.temporossConfig.solo() ? 17 : getTotalAvailableFishSlots()), 1.0);

            case THIRD_COOK:
                // Progression based on cooked fish count or intensity threshold.
                int cookedFishThirdCook = State.getCookedFish();
                return Math.min((double) cookedFishThirdCook / (TemporossScript.temporossConfig.solo() ? 19 : getAllFish()), 1.0);

            case THIRD_CATCH:
                // Progression based on total fish count, target is 19.
                int allFishThirdCatch = getAllFish();
                return Math.min((double) allFishThirdCatch / (TemporossScript.temporossConfig.solo() ? 19 : getTotalAvailableFishSlots()), 1.0);

            case EMERGENCY_FILL:
                // Progression reaches 100% when all fish count is zero.
                int allFishEmergencyFill = getAllFish();
                return allFishEmergencyFill == 0 ? 1.0 : 0.0;

            case SECOND_COOK:
                // Progression based on cooked fish count reaching 17.
                int cookedFishSecondCook = State.getCookedFish();
                return Math.min((double) cookedFishSecondCook / (TemporossScript.temporossConfig.solo() ? 17 : getAllFish()), 1.0);

            case SECOND_CATCH:
                // Progression based on total fish count, target is 17.
                int allFishSecondCatch = getAllFish();
                return Math.min((double) allFishSecondCatch / (TemporossScript.temporossConfig.solo() ? 17 : getTotalAvailableFishSlots()), 1.0);

            case INITIAL_COOK:
                // Progression reaches 100% when raw fish count is zero.
                int cookedFishInitialCook = State.getCookedFish();
                int allFishInitialCook = getAllFish();
                return Math.min((double) cookedFishInitialCook / allFishInitialCook, 1.0);

            case INITIAL_CATCH:
                // Progression based on raw fish count or total fish count; targets are 7 or 10.
                int rawFishInitialCatch = State.getRawFish();
                int allFishInitialCatch = getAllFish();
                return Math.max(
                        Math.min((double) rawFishInitialCatch / 7.0, 1.0),
                        Math.min((double) allFishInitialCatch / 10.0, 1.0)
                );

            default:
                return 0.0;
        }
    }

}
