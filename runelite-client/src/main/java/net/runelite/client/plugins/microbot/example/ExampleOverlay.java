package net.runelite.client.plugins.microbot.example;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ButtonComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ExampleOverlay extends OverlayPanel {
    private final ButtonComponent logButton;

    @Inject
    public ExampleOverlay(ExamplePlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();

        logButton = new ButtonComponent("Log Locked Items");
        logButton.setPreferredSize(new Dimension(140, 30));
        logButton.setParentOverlay(this);
        logButton.setFont(FontManager.getRunescapeBoldFont());

        // ✅ Call the static method directly
        logButton.setOnClick(() -> ExampleScript.printLockedItems());
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(200, 100));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Example Tool")
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(Microbot.status)
                .build());

        panelComponent.getChildren().add(logButton);
        return super.render(graphics);
    }
}
