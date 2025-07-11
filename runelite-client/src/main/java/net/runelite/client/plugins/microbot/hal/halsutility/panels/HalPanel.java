package net.runelite.client.plugins.microbot.hal.halsutility.panels;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.hal.halsutility.HalsUtilityandPluginsPlugin;
import net.runelite.client.plugins.microbot.hal.halsutility.config.HalUtilityConfig;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalSubPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.SwingUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;

@Slf4j
public class HalPanel extends PluginPanel {
    private final HalsUtilityandPluginsPlugin plugin;
    private final HalUtilityConfig config;
    private JComboBox<HalUtilityConfig.ModuleFilter> filterComboBox;
    private HalFixedWidthPanel modulesPanel;
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private JPanel configPanelContainer;
    
    // Search functionality
    private IconTextField searchBar;
    private JScrollPane scrollPane;
    private List<HalModuleListItem> moduleList;

    public HalPanel(HalsUtilityandPluginsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager().getConfig(HalUtilityConfig.class);
        
        cardLayout = new CardLayout();
        setLayout(cardLayout);
        
        initializeMainPanel();
        initializeConfigPanelContainer();
        
        add(mainPanel, "main");
        add(configPanelContainer, "config");
        
        cardLayout.show(this, "main");
        rebuildModuleList();
    }

    private void initializeMainPanel() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Search bar (like PluginListPanel)
        searchBar = new IconTextField();
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                onSearchBarChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                onSearchBarChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                onSearchBarChanged();
            }
        });
        
        // Add category suggestions
        searchBar.getSuggestionListModel().addElement("Skilling");
        searchBar.getSuggestionListModel().addElement("Money");
        searchBar.getSuggestionListModel().addElement("Activity");
        searchBar.getSuggestionListModel().addElement("Bossing");
        searchBar.getSuggestionListModel().addElement("Utility");

        JPanel topPanel = new JPanel();
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        topPanel.setLayout(new BorderLayout(0, BORDER_OFFSET));
        topPanel.add(searchBar, BorderLayout.CENTER);
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Modules panel
        modulesPanel = new HalFixedWidthPanel();
        modulesPanel.setBorder(new EmptyBorder(8, 10, 10, 10));
        modulesPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
        modulesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JPanel northPanel = new HalFixedWidthPanel();
        northPanel.setLayout(new BorderLayout());
        northPanel.add(modulesPanel, BorderLayout.NORTH);
        
        scrollPane = new JScrollPane(northPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
    }

    private void initializeConfigPanelContainer() {
        configPanelContainer = new JPanel(new BorderLayout());
        configPanelContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
    }

    /**
     * Gets the set of pinned module names from the config
     */
    private Set<String> getPinnedModules() {
        String pinned = config.pinnedModules();
        if (pinned == null || pinned.trim().isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(pinned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public void rebuildModuleList() {
        // Get pinned modules
        Set<String> pinnedModules = getPinnedModules();
        
        // Create module list items
        moduleList = plugin.getLoadedModules().values().stream()
            .map(module -> new HalModuleListItem(this, module))
            .sorted((a, b) -> {
                // First sort by pinned status (pinned modules first)
                boolean aPinned = pinnedModules.contains(a.getSearchableName());
                boolean bPinned = pinnedModules.contains(b.getSearchableName());
                
                if (aPinned && !bPinned) {
                    return -1;
                } else if (!aPinned && bPinned) {
                    return 1;
                } else {
                    // Then sort alphabetically
                    return a.getSearchableName().compareToIgnoreCase(b.getSearchableName());
                }
            })
            .collect(Collectors.toList());

        modulesPanel.removeAll();
        refresh();
    }

    void refresh() {
        // Update enabled/disabled status of all items
        moduleList.forEach(listItem ->
        {
            final HalSubPlugin module = plugin.getLoadedModules().values().stream()
                .filter(m -> m.getDisplayName().equals(listItem.getSearchableName()))
                .findFirst()
                .orElse(null);
            if (module != null)
            {
                listItem.setModuleEnabled(module.isRunning());
            }
            
            // Update pinned state
            listItem.updatePinnedState();
        });

        int scrollBarPosition = scrollPane.getVerticalScrollBar().getValue();
        onSearchBarChanged();
        searchBar.requestFocusInWindow();
        validate();
        scrollPane.getVerticalScrollBar().setValue(scrollBarPosition);
    }

    private void onSearchBarChanged() {
        final String text = searchBar.getText().toLowerCase();
        modulesPanel.removeAll();
        
        // Get pinned modules for sorting
        Set<String> pinnedModules = getPinnedModules();
        
        List<HalModuleListItem> filteredList = moduleList.stream()
            .filter(item -> text.isEmpty() || 
                item.getKeywords().stream().anyMatch(keyword -> keyword.contains(text)) ||
                item.getSearchableName().toLowerCase().contains(text))
            .sorted((a, b) -> {
                // Sort by pinned status first, then alphabetically
                boolean aPinned = pinnedModules.contains(a.getSearchableName());
                boolean bPinned = pinnedModules.contains(b.getSearchableName());
                
                if (aPinned && !bPinned) {
                    return -1;
                } else if (!aPinned && bPinned) {
                    return 1;
                } else {
                    return a.getSearchableName().compareToIgnoreCase(b.getSearchableName());
                }
            })
            .collect(Collectors.toList());
        
        filteredList.forEach(modulesPanel::add);
        revalidate();
    }

    /**
     * Toggles the pinned status of a module
     */
    public void toggleModulePinned(String moduleName) {
        Set<String> pinnedModules = new HashSet<>(getPinnedModules());
        
        if (pinnedModules.contains(moduleName)) {
            pinnedModules.remove(moduleName);
        } else {
            pinnedModules.add(moduleName);
        }
        
        // Save the updated pinned modules
        String pinnedString = pinnedModules.stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
        
        plugin.getConfigManager().setConfiguration("halsutility", "pinnedModules", pinnedString);
        
        // Rebuild the module list to reflect the new order
        rebuildModuleList();
    }

    /**
     * Checks if a module is pinned
     */
    public boolean isModulePinned(String moduleName) {
        Set<String> pinnedModules = getPinnedModules();
        return pinnedModules.contains(moduleName);
    }

    public void showModuleConfig(HalSubPlugin subPlugin) {
        // Create config panel for the selected module
        HalConfigPanel configPanel = new HalConfigPanel(plugin.getConfigManager(), subPlugin);
        
        // Update the config panel container
        configPanelContainer.removeAll();
        configPanelContainer.add(configPanel, BorderLayout.CENTER);
        configPanelContainer.revalidate();
        configPanelContainer.repaint();
        
        // Show the config panel
        cardLayout.show(this, "config");
    }

    public void showMainPanel() {
        cardLayout.show(this, "main");
        // Refresh the module list to update toggle states
        refresh();
    }
}
