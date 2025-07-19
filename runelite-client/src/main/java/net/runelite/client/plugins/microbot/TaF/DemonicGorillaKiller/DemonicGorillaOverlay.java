package net.runelite.client.plugins.microbot.TaF.DemonicGorillaKiller;


import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class DemonicGorillaOverlay extends OverlayPanel {

    private final DemonicGorillaPlugin plugin;

    @Inject
    DemonicGorillaOverlay(DemonicGorillaPlugin plugin) {
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
                    .text("Demonic Gorilla - v" + DemonicGorillaScript.VERSION)
                    .color(Color.GREEN)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder().build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Running: " + plugin.getTimeRunning())
                    .leftColor(Color.WHITE)
                    .build());
            var state = DemonicGorillaScript.BOT_STATUS == DemonicGorillaScript.State.TRAVEL_TO_GORILLAS ? DemonicGorillaScript.travelStep : DemonicGorillaScript.BOT_STATUS;
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Script status: " + state)
                    .leftColor(Color.WHITE)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Microbot status: " + Microbot.status)
                    .leftColor(Color.WHITE)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kill Count: " + DemonicGorillaScript.killCount + " / " + Microbot.getAggregateLootRecords("Demonic Gorilla").getKills())
                    .leftColor(Color.WHITE)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Current trip kill count: " + DemonicGorillaScript.currentTripKillCount)
                    .leftColor(Color.WHITE)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total loot: " + Microbot.getAggregateLootRecordsTotalGevalue("Demonic Gorilla"))
                    .leftColor(Color.WHITE)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Current defensive prayer: " + DemonicGorillaScript.currentDefensivePrayer)
                    .leftColor(Color.WHITE)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Current gear: " + DemonicGorillaScript.currentGear)
                    .leftColor(Color.WHITE)
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
