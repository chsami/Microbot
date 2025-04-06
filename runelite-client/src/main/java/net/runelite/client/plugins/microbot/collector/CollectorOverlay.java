package net.runelite.client.plugins.microbot.collector;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class CollectorOverlay extends OverlayPanel {
    private final CollectorConfig config;

    @Inject
    CollectorOverlay(CollectorPlugin plugin, CollectorConfig config)
    {
        super(plugin);
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Collector v1.0.0")
                    .color(Color.YELLOW)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(CollectorScript.currentState.toString())
                    .build());

            switch (config.collectionType()) {
                case NONE:
                    break;
                case SNAPE_GRASS:
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Snape Grass Collected:")
                            .right(String.valueOf(CollectorScript.totalSnapeGrassCollected))
                            .build());
                    if (CollectorScript.startTimeSnapeGrass > 0) {
                        long timeElapsed = (System.currentTimeMillis() - CollectorScript.startTimeSnapeGrass) / 1000;
                        double hoursElapsed = timeElapsed / 3600.0;
                        int perHour = (int) (CollectorScript.totalSnapeGrassCollected / hoursElapsed);
                        panelComponent.getChildren().add(LineComponent.builder()
                                .left("Per Hour:")
                                .right(String.valueOf(perHour))
                                .build());
                    }
                    break;

                case SUPER_ANTI_POISON:
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Super Antipoison Doses Collected:")
                            .right(String.valueOf(CollectorScript.totalSAPCollected))
                            .build());
                    if (CollectorScript.startTimeSAP > 0) {
                        long timeElapsed = (System.currentTimeMillis() - CollectorScript.startTimeSAP) / 1000;
                        double hoursElapsed = timeElapsed / 3600.0;
                        int perHour = (int) (CollectorScript.totalSAPCollected / hoursElapsed);
                        panelComponent.getChildren().add(LineComponent.builder()
                                .left("Per Hour:")
                                .right(String.valueOf(perHour))
                                .build());
                    }
                    break;

                case MORT_MYRE_FUNGUS:
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Mort Myre Fungus Collected:")
                            .right(String.valueOf(CollectorScript.totalMMFCollected))
                            .build());
                    if (CollectorScript.startTimeMMF > 0) {
                        long timeElapsed = (System.currentTimeMillis() - CollectorScript.startTimeMMF) / 1000;
                        double hoursElapsed = timeElapsed / 3600.0;
                        int perHour = (int) (CollectorScript.totalMMFCollected / hoursElapsed);
                        panelComponent.getChildren().add(LineComponent.builder()
                                .left("Per Hour:")
                                .right(String.valueOf(perHour))
                                .build());
                    }
                    break;
            }
            if (config.enableSeaweed()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Seaweed Spores Collected:")
                        .right(String.valueOf(CollectorScript.totalSporesCollected))
                        .build());
                if (CollectorScript.startTimeSeaweed > 0) {
                    long timeElapsed = (System.currentTimeMillis() - CollectorScript.startTimeSeaweed) / 1000;
                    double hoursElapsed = timeElapsed / 3600.0;
                    int perHour = (int) (CollectorScript.totalSeaweedCollected / hoursElapsed);
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Per Hour:")
                            .right(String.valueOf(perHour))
                            .build());
                }
            }

        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
