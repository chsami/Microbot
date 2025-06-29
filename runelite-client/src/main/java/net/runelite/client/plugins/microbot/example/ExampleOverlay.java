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
    public final ButtonComponent myButton;
    @Inject
    ExampleOverlay(ExamplePlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
        myButton = new ButtonComponent("Test");
        myButton.setPreferredSize(new Dimension(100, 30));
        myButton.setParentOverlay(this);
        myButton.setFont(FontManager.getRunescapeBoldFont());
        myButton.setOnClick(() -> Microbot.openPopUp("Microbot", String.format("S-1D:<br><br><col=ffffff>%s Popup</col>", "Example")));
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro Example V1.0.0")
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .build());

            panelComponent.getChildren().add(myButton);


        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
