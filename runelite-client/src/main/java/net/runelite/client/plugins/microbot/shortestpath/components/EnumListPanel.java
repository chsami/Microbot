package net.runelite.client.plugins.microbot.shortestpath.components;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin.CONFIG_GROUP;

public class EnumListPanel<T extends Enum<T> & PohTeleport> extends JPanel {

    private final DefaultListModel<T> selectedModel = new DefaultListModel<>();
    private JList<T> teleportList;

    private final String title;
    private final Class<T> enumClass;

    public EnumListPanel(Class<T> enumClass, String title) {
        this.title = title;
        this.enumClass = enumClass;
        setBorder(new TitledBorder(title));
        setLayout(new GridBagLayout());

        teleportList = new JList<>(selectedModel);
        teleportList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        teleportList.setVisibleRowCount(6);
        teleportList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value.toString());
                return this;
            }
        });

        JScrollPane scroll = new JScrollPane(teleportList);
        // Ensure the panel doesn't render too small
        Dimension minSize = new Dimension(180, 150);
        scroll.setMinimumSize(minSize);
        scroll.setPreferredSize(minSize);


        GridBagConstraints scrollGbc = new GridBagConstraints();
        scrollGbc.gridx = 0;
        scrollGbc.gridy = 0;
        scrollGbc.gridwidth = 1;
        scrollGbc.gridheight = 1;
        scrollGbc.weightx = 0.0; // Don't expand horizontally
        scrollGbc.weighty = 1.0; // Allow vertical expansion
        scrollGbc.fill = GridBagConstraints.BOTH; // Fill available space in both directions
        scrollGbc.anchor = GridBagConstraints.NORTHWEST; // Anchor to northwest
        scrollGbc.insets = new Insets(0, 0, 8, 0); // Add spacing below the scroll pane
        add(scroll, scrollGbc);

        // Load initial state
        loadTeleportsFromConfig().forEach(selectedModel::addElement);
    }

    public void setAll(java.util.List<T> teleports) {
        selectedModel.clear();
        teleports.forEach(selectedModel::addElement);
        saveTeleportsToConfig();
    }

    public void saveTeleportsToConfig() {
        java.util.List<T> teleports = new ArrayList<>();
        for (int i = 0; i < selectedModel.getSize(); i++) {
            teleports.add(selectedModel.get(i));
        }
        String serialized = teleports.stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
        Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, title, serialized);
    }

    public java.util.List<T> loadTeleportsFromConfig() {
        String raw = Microbot.getConfigManager().getConfiguration(CONFIG_GROUP, title);
        if (raw == null || raw.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(name ->
                {
                    try {
                        return Enum.valueOf(enumClass, name);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Set<PohTeleport> getTeleports() {
        Set<PohTeleport> teleports = new HashSet<>();
        for (int i = 0; i < selectedModel.getSize(); i++) {
            teleports.add((PohTeleport) selectedModel.get(i));
        }
        return teleports;
    }
}
