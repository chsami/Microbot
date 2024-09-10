package net.runelite.client.plugins.microbot.qualityoflife;

import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class QoLOverlay extends OverlayPanel {
    QoLConfig config;

    @Inject
    QoLOverlay(QoLPlugin plugin, QoLConfig config) {
        super(plugin);
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
//            panelComponent.setPreferredSize(new Dimension(200, 300));
//            panelComponent.getChildren().add(TitleComponent.builder()
//                    .text("Micro Example V1.0.0")
//                    .color(Color.GREEN)
//                    .build());
//
//            panelComponent.getChildren().add(LineComponent.builder().build());
//
//            panelComponent.getChildren().add(LineComponent.builder()
//                    .left(Microbot.status)
//                    .build());
//
            if (config.renderMaxHitOverlay())
                renderNpcs(graphics);


        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }

    private void renderNpcs(Graphics2D graphics) {
        List<NPC> npcs;
        npcs = Microbot.getClientThread().runOnClientThread(() -> Rs2Npc.getNpcs()
                .filter(npc -> npc.getName() != null)
                .collect(Collectors.toList()));
        for (NPC npc : npcs) {
            if (npc != null && npc.getCanvasTilePoly() != null) {
                try {
                    String text = ("Max Hit: " + Objects.requireNonNull(Rs2NpcManager.getStats(npc.getId())).getMaxHit());

                    graphics.setFont(new Font("Arial", Font.BOLD, 14));
                    //npc.setOverheadText(text);
                    LocalPoint lp = npc.getLocalLocation();
                    Point textLocation = Perspective.getCanvasTextLocation(Microbot.getClient(), graphics, lp, text, npc.getLogicalHeight());
                    if (textLocation == null) {
                        continue;
                    }
                    textLocation = new Point(textLocation.getX(), textLocation.getY() - 25);

                    OverlayUtil.renderTextLocation(graphics, textLocation, text, Color.YELLOW);
                } catch (Exception ignored) {
                }

            }
        }
    }
}
