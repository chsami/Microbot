package net.runelite.client.plugins.microbot.kaas.pyrefox;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class PyreFoxOverlay extends OverlayPanel
{
    private static final Color WHITE_TRANSLUCENT = new Color(0, 255, 255, 127);
    private PyreFoxPlugin _plugin;

    @Inject
    PyreFoxOverlay(PyreFoxPlugin plugin)
    {
        super(plugin);
        this._plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("KaaS - Pyrefox")
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Running: ")
                    .right(_plugin.getTimeRunning())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(PyreFoxPlugin.currentState.toString())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Caught:")
                    .right(String.format("%,d", PyreFoxPlugin.catchCounter))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Chop at amount:")
                    .right(String.format("%,d", PyreFoxConstants.GATHER_LOGS_AT_AMOUNT))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Chop until")
                    .right(String.format("%,d", PyreFoxConstants.GATHER_LOGS_AMOUNT))
                    .build());

            var trap = PyreFoxConstants.TRAP_OBJECT_POINT;
            if (trap != null)
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Trap")
                        .right(String.format("%d,%d,%d", trap.getX(), trap.getY(), trap.getPlane()))
                        .build());
            }

            boolean hasPouch = Rs2Inventory.contains(ItemID.LARGE_MEAT_POUCH_OPEN) || Rs2Inventory.contains(ItemID.LARGE_MEAT_POUCH) || Rs2Inventory.contains(ItemID.SMALL_MEAT_POUCH) || Rs2Inventory.contains(ItemID.SMALL_MEAT_POUCH_OPEN);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Meat Pouch:")
                    .right(hasPouch ? "✓" : "x")
                    .rightColor((!hasPouch) ? Color.RED : Color.GREEN)
                    .build());

            boolean hasKnife = Rs2Inventory.contains(ItemID.KNIFE);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Knife:")
                    .right(hasKnife ? "✓" : "x")
                    .rightColor((!hasKnife) ? Color.RED : Color.GREEN)
                    .build());
        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
