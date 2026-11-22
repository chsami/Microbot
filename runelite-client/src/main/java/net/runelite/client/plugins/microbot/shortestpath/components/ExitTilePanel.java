package net.runelite.client.plugins.microbot.shortestpath.components;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.ui.ClientTheme;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

import static net.runelite.api.gameval.ObjectID.POH_EXIT_PORTAL;

public class ExitTilePanel extends JPanel {

    String POH_TILE_KEY = "pohExitPortalTile";
    private JTextField tileField;

    public ExitTilePanel() {
        setBorder(new TitledBorder("Exit Portal Location"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets.set(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        tileField = new JTextField();
        tileField.setBorder(null);
        tileField.setOpaque(false);
        tileField.setSelectedTextColor(ClientTheme.classicOr(Color.WHITE, ColorScheme.TEXT_COLOR));
        tileField.setSelectionColor(ColorScheme.BRAND_ORANGE_TRANSPARENT);
        tileField.setPreferredSize(new Dimension(150, 20));
        tileField.setEnabled(false);
        add(tileField, gbc);
        loadTile();
    }

    private void loadTile() {
        try {
            String tile = Microbot.getConfigManager().getConfiguration(ShortestPathPlugin.CONFIG_GROUP, POH_TILE_KEY);
            tileField.setText(tile);
        } catch (NullPointerException e) {
            Microbot.log("Failed to load poh tile config");
            tileField.setText("");
        }
    }

    private void saveTile() {
        Microbot.getConfigManager().setConfiguration(ShortestPathPlugin.CONFIG_GROUP, POH_TILE_KEY, tileField.getText());
    }

    public void detectTile() {
        Rs2TileObjectModel exitPortal = Microbot.getRs2TileObjectCache()
                .query()
                .withId(POH_EXIT_PORTAL)
                .nearest();
        if (exitPortal == null) {
            Microbot.log("Failed to find exit portal");
        } else {
            WorldPoint wp = WorldPoint.fromLocalInstance(Microbot.getClient(), exitPortal.getLocalLocation());
            String tileString = String.format("%s, %s, %s", wp.getX(), wp.getY(), wp.getPlane());
            tileField.setText(tileString);
            saveTile();
        }
    }

    public WorldPoint getTile() {
        String tileString = tileField.getText();
        if (tileString.isEmpty()) {
            return null;
        }
        String[] tileParts = tileString.split(",");
        if (tileParts.length != 3) {
            return null;
        }
        return new WorldPoint(Integer.parseInt(tileParts[0].trim()), Integer.parseInt(tileParts[1].trim()), Integer.parseInt(tileParts[2].trim()));
    }
}
