package net.runelite.client.plugins.microbot.shortestpath;


import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.components.CheckboxPanel;
import net.runelite.client.plugins.microbot.shortestpath.components.EnumListPanel;
import net.runelite.client.plugins.microbot.shortestpath.components.JewelleryBoxPanel;
import net.runelite.client.plugins.microbot.util.cache.Rs2PohCache;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.poh.data.HouseStyle;
import net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;
import net.runelite.client.plugins.microbot.util.poh.data.PohPortal;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PohPanel extends PluginPanel {

    private EnumListPanel<PohPortal> portalPanel;
    private EnumListPanel<NexusPortal> nexusPanel;
    private CheckboxPanel checkboxPanel;
    private JewelleryBoxPanel jewelleryBoxPanel;

    private JButton detectButton;
    private JScrollPane mainScrollPane;

    private ShortestPathConfig config;
    public static PohPanel instance;

    public PohPanel(ShortestPathConfig config) {
        super(false);
        instance = this;
        this.config = config;
        setLayout(new BorderLayout(8, 8));

        setPreferredSize(null); // Remove any fixed preferred size
        setMaximumSize(null);   // Remove any fixed maximum size

        // Create scrollable content
        mainScrollPane = createMainScrollPane();
        add(mainScrollPane, BorderLayout.CENTER);
        add(buildTopBar(), BorderLayout.SOUTH);
    }

    /**
     * Creates and configures the main scroll pane containing all the content panels.
     *
     * @return A properly configured JScrollPane with all content panels
     */
    private JScrollPane createMainScrollPane() {
        JPanel contentPanel = buildContent();

        JScrollPane scrollPane = new JScrollPane(contentPanel);

        // Configure scroll policies
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Remove default border for cleaner look
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Set background to match the content
        scrollPane.getViewport().setBackground(contentPanel.getBackground());

        // Configure scroll increment for smoother scrolling
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setBlockIncrement(64);

        // Let it fill available space - don't set fixed sizes
        scrollPane.setMinimumSize(new Dimension(120, 0));

        return scrollPane;
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets.set(4, 4, 4, 4);

        // Portals list
        gbc.gridy = 0;
        portalPanel = new EnumListPanel<>(PohPortal.class, "Portal Room");
        root.add(portalPanel, gbc);

        // Nexus panel
        gbc.gridy++;
        nexusPanel = new EnumListPanel<>(NexusPortal.class, "Nexus Teleport");
        root.add(nexusPanel, gbc);

        // Booleans section
        gbc.gridy++;
        checkboxPanel = new CheckboxPanel();
        root.add(checkboxPanel, gbc);

        // Dropdowns section
        gbc.gridy++;
        jewelleryBoxPanel = new JewelleryBoxPanel();
        root.add(jewelleryBoxPanel, gbc);

        // Add a filler component to push content to the top and allow proper stretching
        gbc.gridy++;
        gbc.weighty = 1.0; // This is crucial - it allows vertical expansion
        gbc.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        root.add(filler, gbc);

        return root;
    }

    private JButton buildTopBar() {
        detectButton = new JButton("Detect available POH teleports");
        detectButton.addActionListener(e -> detectPortalsFromPOH());

        return detectButton;
    }


    private void detectPortalsFromPOH() {
        if (!PohTeleports.isInHouse()) {
            Microbot.log("Please make sure you are in the POH first!");
            return;
        }
        detectButton.setEnabled(false);
        detectButton.setText("Detecting...");
        checkboxPanel.detectPohFacilities();
        portalPanel.setAll(PohPortal.findPortalsInPoh());
        nexusPanel.setAll(NexusPortal.getAvailableTeleports());
        jewelleryBoxPanel.detectJewelleryBox();
        detectButton.setEnabled(true);
        detectButton.setText("Detect available POH teleports");
    }

    /**
     * Creates a Set with all available PoH transports directly extracted from cached data
     * Excludes things like Fairy rings and spirit trees as they are based on existing data
     *
     * @return allTransports map with all cached PoH transports added in
     */
    public static Map<WorldPoint, Set<Transport>> getAvailableTransports(Map<WorldPoint, Set<Transport>> allTransports) {
        HouseStyle style = HouseStyle.getStyle();
        if (style == null || instance == null) return allTransports;
        Set<PohTeleport> pohTeleports = new HashSet<>();
        Map<WorldPoint, Set<Transport>> pohTransports = new HashMap<>();
        WorldPoint exitPortal = style.getPohExitWorldPoint();

        pohTeleports.addAll(instance.checkboxPanel.getTeleports());
        pohTeleports.addAll(instance.portalPanel.getTeleports());
        pohTeleports.addAll(instance.nexusPanel.getTeleports());
        pohTeleports.addAll(instance.jewelleryBoxPanel.getTeleports());

        if (instance.checkboxPanel.fairyRingCb.isSelected()) {
            Map<WorldPoint, Set<Transport>> fairyRings = Rs2PohCache.createFairyRingMap(exitPortal, allTransports);
            for (var entry : fairyRings.entrySet()) {
                pohTransports
                        .computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                        .addAll(entry.getValue());
            }
        }
        if (instance.checkboxPanel.spiritTreeCb.isSelected()) {
            Map<WorldPoint, Set<Transport>> spiritTrees = Rs2PohCache.createSpiritTreeMap(exitPortal, allTransports);
            for (var entry : spiritTrees.entrySet()) {
                pohTransports
                        .computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                        .addAll(entry.getValue());
            }
        }
        pohTransports.computeIfAbsent(exitPortal, p -> new HashSet<>()).addAll(pohTeleports.stream().map(
                t -> new PohTransport(exitPortal, t)
        ).collect(Collectors.toList()));

        return pohTransports;
    }

}
