package net.runelite.client.plugins.microbot.virewatch;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class PVirewatchKillerOverlayPanel extends OverlayPanel {

    private final PVirewatchKillerPlugin plugin;

    private final PVirewatchKillerConfig config;

    private final PVirewatchScript script;

    private final PAlcher alchScript;
    @Inject
    PVirewatchKillerOverlayPanel(PVirewatchKillerPlugin plugin, PVirewatchKillerConfig config, PVirewatchScript script, PAlcher alchScript)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        this.script = script;
        this.alchScript = alchScript;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Pumsters Vyrewatch " + PVirewatchKillerPlugin.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            String check = plugin.startingLocation != null ? "\u2713" : "\u2717";
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Starting location set")
                    .right(check)
                    .rightFont(FontManager.getDefaultFont())
                    .rightColor(plugin.startingLocation != null ? Color.GREEN : Color.RED)
                    .build());

            String check4 = plugin.fightArea != null ? "\u2713" : "\u2717";
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Fight area set")
                    .right(check4)
                    .rightFont(FontManager.getDefaultFont())
                    .rightColor(plugin.fightArea != null ? Color.GREEN : Color.RED)
                    .build());

            boolean inRegion =  Rs2Player.getWorldLocation() != null && Rs2Player.getWorldLocation().getRegionID() == 14388;
            String check2 = inRegion ? "\u2713" : "\u2717";
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("In correct region")
                    .right(check2)
                    .rightFont(FontManager.getDefaultFont())
                    .rightColor(inRegion ? Color.GREEN : Color.RED)
                    .build());




            String check3 = config.alchItems() ? "\u2713" : "\u2717";
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Alching drops")
                    .right(check3)
                    .rightFont(FontManager.getDefaultFont())
                    .rightColor(config.alchItems() ? Color.GREEN : Color.RED)
                    .build());

            if(config.alchItems()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Alched items")
                        .right(String.valueOf(plugin.alchedItems))
                        .rightColor(Color.GREEN)
                        .build());
            }

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Ticks not in combat")
                    .right(String.valueOf(plugin.countedTicks))
                    .rightColor(Color.MAGENTA)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Ticks out of area")
                    .right(String.valueOf(plugin.ticksOutOfArea))
                    .rightColor(Color.MAGENTA)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Pray style")
                    .right(config.prayStyle().getName())
                    .rightColor(Color.CYAN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Profit")
                    .right(plugin.getTotalItemValue())
                    .rightColor(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Micro Status")
                    .right(Microbot.status)
                    .build());



        } catch(Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
