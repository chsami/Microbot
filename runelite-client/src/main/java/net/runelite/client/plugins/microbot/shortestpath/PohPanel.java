package net.runelite.client.plugins.microbot.shortestpath;


import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.components.CheckboxPanel;
import net.runelite.client.plugins.microbot.shortestpath.components.EnumListPanel;
import net.runelite.client.plugins.microbot.shortestpath.components.ExitTilePanel;
import net.runelite.client.plugins.microbot.shortestpath.components.JewelleryBoxPanel;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
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

import static net.runelite.client.plugins.microbot.shortestpath.TransportType.FAIRY_RING;
import static net.runelite.client.plugins.microbot.shortestpath.TransportType.SPIRIT_TREE;

public class PohPanel extends PluginPanel {

    private EnumListPanel<PohPortal> portalPanel;
    private EnumListPanel<NexusPortal> nexusPanel;
    private CheckboxPanel checkboxPanel;
    private JewelleryBoxPanel jewelleryBoxPanel;
    private ExitTilePanel tilePanel;

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

        // Tile section
        gbc.gridy++;
        tilePanel = new ExitTilePanel();
        root.add(tilePanel, gbc);

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
        try {
            detectSection("house features", () -> checkboxPanel.detectPohFacilities());
            detectSection("portals", () -> portalPanel.setAll(PohPortal.findPortalsInPoh()));
            detectSection("Nexus", () -> nexusPanel.setAll(NexusPortal.getAvailableTeleports()));
            detectSection("jewellery box", () -> jewelleryBoxPanel.detectJewelleryBox());
            detectSection("exit portal", () -> tilePanel.detectTile());
        } finally {
            detectButton.setEnabled(true);
            detectButton.setText("Detect available POH teleports");
        }
    }

    private static void detectSection(String name, Runnable detection) {
        try {
            detection.run();
        } catch (RuntimeException ex) {
            Microbot.log("POH detection failed for " + name + ": " + ex.getClass().getSimpleName());
            Microbot.logStackTrace("POH detection: " + name, ex);
        }
    }

    /**
     * Retrieves a map of transports that take the player to the Player-Owned House (POH).
     * This includes teleports and the external portal location of the house.
     *
     *
     * @return A map where the key is the `WorldPoint` representing the origin of the transport
     *         and the value is a set of `Transport` instances representing the available
     *         methods of transportation to the inside of PoH.
     */
    /**
     * The user-configured POH exit-portal tile. All POH transports registered by this panel
     * (Portal Nexus, Jewellery Box, Fairy Ring, Spirit Tree, etc.) use this tile as their
     * transport origin, so callers that need to start a pathfinder search from inside a POH
     * must remap their current location to this tile.
     */
    public static WorldPoint getExitPortalTile() {
        if (instance == null || instance.tilePanel == null) return null;
        return instance.tilePanel.getTile();
    }

    public static Map<WorldPoint, Set<Transport>> getTransportsToPoh() {
        HouseLocation location = HouseLocation.getHouseLocation();
        Map<WorldPoint, Set<Transport>> transportMap = new HashMap<>();
        if (location == null || instance == null) return transportMap;
        WorldPoint exitPortal = instance.tilePanel.getTile();
        if (exitPortal == null) {
            return transportMap;
        }
        WorldPoint outsidePoint = location.getPortalLocation();

        transportMap.put(null, Set.of(
                new Transport(exitPortal, "Construction cape: Tele to POH", TransportType.TELEPORTATION_ITEM, true, 19, Set.of(Set.of(9789), Set.of(9790))),
                new Transport(exitPortal, "Teleport to House", TransportType.TELEPORTATION_SPELL, true, 19, Map.of(Skill.MAGIC, 40)),
                new Transport(exitPortal, "Teleport to House tablet: Inside", TransportType.TELEPORTATION_ITEM, true, 19, Set.of(Set.of(8013)))
        ));
        transportMap.put(outsidePoint, Set.of(
                new Transport(outsidePoint, exitPortal, location.name() + " -> PoH", TransportType.TELEPORTATION_PORTAL, true, "Home", "Portal", location.getPortalId())
        ));
        return transportMap;
    }

    /**
     * Creates a Set with all available PoH transports directly extracted from cached data
     * Excludes things like Fairy rings and spirit trees as they are based on existing data
     *
     * @return allTransports map with all cached PoH transports added in
     */
    public static Map<WorldPoint, Set<Transport>> getAvailableTransports(Map<WorldPoint, Set<Transport>> allTransports) {
        if (instance == null) return allTransports;
        Set<PohTeleport> pohTeleports = new HashSet<>();
        Map<WorldPoint, Set<Transport>> pohTransports = new HashMap<>();
        WorldPoint exitPortal = instance.tilePanel.getTile();
        if (exitPortal == null) {
            Microbot.log("Failed to load exit portal config");
            return allTransports;
        }

        pohTeleports.addAll(instance.checkboxPanel.getTeleports());
        pohTeleports.addAll(instance.portalPanel.getTeleports());
        pohTeleports.addAll(instance.nexusPanel.getTeleports());
        pohTeleports.addAll(instance.jewelleryBoxPanel.getTeleports());

        if (instance.checkboxPanel.fairyRingCb.isSelected()) {
            Map<WorldPoint, Set<Transport>> fairyRings = createFairyRingMap(exitPortal, allTransports);
            for (var entry : fairyRings.entrySet()) {
                pohTransports
                        .computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                        .addAll(entry.getValue());
            }
        }
        if (instance.checkboxPanel.spiritTreeCb.isSelected()) {
            Map<WorldPoint, Set<Transport>> spiritTrees = createSpiritTreeMap(exitPortal, allTransports);
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


    /**
     * Connecting the PoH Fairy ring to all other fairy rings and vise versa
     *
     * @param pohFairyRing  position of the PoH fairy ring (exitPortal until mapping inside Poh)
     * @param transportsMap map from which currently present fairy rings are found
     * @return new map with PoH fairy rings transports added
     */
    public static Map<WorldPoint, Set<Transport>> createFairyRingMap(
            WorldPoint pohFairyRing,
            Map<WorldPoint, Set<Transport>> transportsMap
    ) {
        //Only used to build actual transports (needs ORIGIN and DESTINATION same)
        Transport pohFairyTransport = new Transport(pohFairyRing, pohFairyRing, "DIQ", FAIRY_RING, true, 5);
        return createTransportsToPoh(pohFairyTransport, transportsMap);
    }

    /**
     * Connecting the PoH spirit tree to all other spirit tree and vise versa
     *
     * @param pohSpiritTree position of the PoH spirit tree (exitPortal until mapping inside Poh)
     * @param transportsMap map from which currently present spirit tree transports are found
     * @return new map with PoH fairy spirit tree transports added
     */
    public static Map<WorldPoint, Set<Transport>> createSpiritTreeMap(
            WorldPoint pohSpiritTree,
            Map<WorldPoint, Set<Transport>> transportsMap
    ) {
        //Only used to build actual transports (needs ORIGIN and DESTINATION same)
        Transport pohSpiritTransport = new Transport(pohSpiritTree, pohSpiritTree, "C: Your house", SPIRIT_TREE, true, 5);
        return createTransportsToPoh(pohSpiritTransport, transportsMap);
    }

    /**
     * Uses a template (PoH) transport to connect all transports to
     * other transports of the same type, both by adding routes from the PoH transport to
     * the existing transports and vice versa.
     *
     * @param pohTempTransport the transport object representing the PoH transport to be connected.
     *                         The TransportType is used to filter for existing transports
     *                         The origin and destination are used to build the connecting transport
     * @param transportsMap    a map of existing transports, where each key represents a WorldPoint
     *                         and the value is a set of transports originating or terminating at
     *                         that point
     * @return a new map containing the updated set of transports where connections have been
     * added between the provided PoH transport and existing transports of the same type
     */
    public static Map<WorldPoint, Set<Transport>> createTransportsToPoh(Transport pohTempTransport, Map<WorldPoint, Set<Transport>> transportsMap) {
        WorldPoint pohExitPortal = pohTempTransport.getOrigin();
        TransportType type = pohTempTransport.getType();
        Map<WorldPoint, Set<Transport>> newTransportsMap = new HashMap<>();
        transportsMap.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(t -> t.getType() == type)).findFirst().ifPresent(e -> {
                    WorldPoint existingRingPoint = e.getKey();
                    for (Transport existingRingTransport : new HashSet<>(e.getValue())) {
                        if (existingRingTransport.getType() != type) continue;
                        // add from poh
                        newTransportsMap
                                .computeIfAbsent(pohExitPortal, k -> new HashSet<>())
                                .add(new Transport(pohTempTransport, existingRingTransport));

                        // add to poh
                        newTransportsMap
                                .computeIfAbsent(existingRingPoint, k -> new HashSet<>())
                                .add(new Transport(existingRingTransport, pohTempTransport));
                    }
                });

        return newTransportsMap;
    }

}
