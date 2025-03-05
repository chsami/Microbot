package net.runelite.client.plugins.microbot.frosty.artioprayer;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

import javax.inject.Inject;
import java.awt.*;

public class ArtioPrayerOverlay extends Overlay {
    private final Client client;
    private final ArtioPrayerPlugin plugin;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public ArtioPrayerOverlay(Client client, ArtioPrayerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);

        // Improve background visuals
        panelComponent.setBorder(new Rectangle(8, 8, 8, 8));
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 180)); // Semi-transparent background
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.getClient().getLocalPlayer() == null) {
            return null;
        }

        NPC artio = Rs2Npc.getNpc(plugin.getBoss().getNpcId());
        if (artio == null) {
            return null; // Hide overlay if Artio isn't around
        }

        int x = 10, y = 40;
        int width = 225, height = 100;

        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.fillRoundRect(x, y, width, height, 10, 10);


        graphics.setFont(new Font("Arial", Font.BOLD, 14));
        graphics.setColor(Color.YELLOW);
        graphics.drawString("Prayer Overlay", x + 10, y + 20);

        int maxPrayer = client.getRealSkillLevel(Skill.PRAYER);
        graphics.drawString("Prayer: " + currentPrayer + "/" + maxPrayer, x + 10, y + 60);

        int barWidth = 160;
        int filledWidth = (int) ((double) currentPrayer / maxPrayer * barWidth);
        graphics.setColor(Color.RED);
        graphics.fillRect(x + 10, y + 70, barWidth, 10);
        graphics.setColor(Color.GREEN);
        graphics.fillRect(x + 10, y + 70, filledWidth, 10);

        return new Dimension(width, height);
    }
}