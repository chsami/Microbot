package net.runelite.client.plugins.microbot.qualityoflife;

import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.Microbot.log;

public class QoLOverlay extends OverlayPanel {
    QoLConfig config;
    QoLPlugin plugin;

    @Inject
    QoLOverlay(QoLPlugin plugin, QoLConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        //setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {

            if (config.renderMaxHitOverlay())
                renderNpcs(graphics);

            // renderPlayers(graphics);

        } catch (Exception ex) {
            log("Error in QoLOverlay: " + ex.getMessage());
        }
        return super.render(graphics);
    }


    private void renderNpcs(Graphics2D graphics) {
        List<Rs2NpcModel> npcs;
        npcs = Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Npc.getNpcs()
                .filter(npc -> npc.getName() != null)
                .collect(Collectors.toList()))
                .orElse(new ArrayList<>());

        for (Rs2NpcModel npc : npcs) {
            if (npc != null && npc.getCanvasTilePoly() != null) {
                try {
                    String text = ("Max Hit: " + Objects.requireNonNull(Rs2NpcManager.getStats(npc.getId())).getMaxHit());


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

    private void renderPlayers(Graphics2D graphics) {
        for (Player player : Rs2Player.getPlayersInCombat()) {
            System.out.println(Rs2Player.getPlayerEquipmentNames(player));
            String text = (Rs2Player.calculateHealthPercentage(player) + " HP");
            LocalPoint lp = player.getLocalLocation();
            Point textLocation = Perspective.getCanvasTextLocation(Microbot.getClient(), graphics, lp, text, player.getLogicalHeight());
            if (textLocation == null) {
                continue;
            }
            textLocation = new Point(textLocation.getX(), textLocation.getY() - 25);

            OverlayUtil.renderTextLocation(graphics, textLocation, text, Color.YELLOW);
        }
    }
}
