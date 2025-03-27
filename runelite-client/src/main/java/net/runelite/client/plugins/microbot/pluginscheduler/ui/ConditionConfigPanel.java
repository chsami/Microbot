package net.runelite.client.plugins.microbot.pluginscheduler.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionManager;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.NotCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.LootItemCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillLevelCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillXpCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.DayOfWeekCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeWindowCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.type.ScheduledPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;


@Slf4j
public class ConditionConfigPanel extends JPanel {
    public static final Color BRAND_BLUE = new Color(25, 130, 196);
    private final JComboBox<String> conditionTypeComboBox;
    private final JPanel configPanel;
    
    private ConditionTreeCellRenderer conditionTreeCellRenderer;
    private final JComboBox<String> logicComboBox;
    // Tree visualization components
    private DefaultMutableTreeNode rootNode;
    private DefaultTreeModel treeModel;
    private JTree conditionTree;
    private JSplitPane splitPane;
    
    // Condition list components
    private DefaultListModel<String> conditionListModel;
    private JList<String> conditionList;
    
    private Consumer<LogicalCondition> userConditionUpdateCallback;
    private Consumer<Boolean> requireAllCallback;
    
    private final SchedulerPlugin schedulerPlugin;
    private ScheduledPlugin selectScheduledPlugin;
    // UI Controls
    private JButton saveButton;
    private JButton loadButton;
    private JButton resetButton;
    
    private JButton editButton;
    private JButton addButton;
    private JButton removeButton;
    

    private JButton negateButton;
    private JButton removeGroupButton;
    private JButton convertToAndButton;
    private JButton convertToOrButton;
    private JButton ungroupButton;
    private JPanel titlePanel;
    private JLabel titleLabel;
    private final boolean stopConditionPanel;
    private boolean[] updatingSelectionFlag = new boolean[1];
    public ConditionConfigPanel(SchedulerPlugin schedulerPlugin, boolean stopConditionPanel) {        
        this.schedulerPlugin = schedulerPlugin;
        this.stopConditionPanel = stopConditionPanel;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR),
                        BorderFactory.createEmptyBorder(5, 5, 5, 5)
                ),
                stopConditionPanel ? "Stop Conditions" : "Start Conditions",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                FontManager.getRunescapeBoldFont(),
                Color.WHITE
        ));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Initialize title panel
        titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        titlePanel.setName("titlePanel");
        
        titleLabel = new JLabel(stopConditionPanel ? "Stop Conditions for: None" : "Start Conditions for: None");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titlePanel.add(titleLabel);
        
        add(titlePanel, BorderLayout.NORTH);
        
        // Initialize save and load buttons
        initializeSaveButton();
        initializeLoadButton();
        initializeResetButton(); 
        
        JPanel topPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        topPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Logic selection
        JLabel logicLabel = new JLabel("Logic:");
        logicLabel.setForeground(Color.WHITE);
        topPanel.add(logicLabel);
        
        logicComboBox = new JComboBox<>(new String[]{"All conditions must be met (AND)", "Any condition can be met (OR)"});
        logicComboBox.addActionListener(e -> {
            if (requireAllCallback != null) {
                requireAllCallback.accept(logicComboBox.getSelectedIndex() == 0);
            }
            updateTreeFromConditions();
        });
        topPanel.add(logicComboBox);
        
        // Stop checkbox
        JLabel stopLabel = new JLabel("Auto-stop:");
        stopLabel.setForeground(Color.WHITE);
        topPanel.add(stopLabel);
        
        // Add a panel for save/load buttons
        JPanel buttonControlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonControlPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttonControlPanel.add(loadButton);
        buttonControlPanel.add(saveButton);
        buttonControlPanel.add(resetButton);
        topPanel.add(buttonControlPanel);
        
        add(topPanel, BorderLayout.NORTH);
        
        // Define condition types based on panel type
        String[] conditionTypes;
        if (stopConditionPanel) {
            conditionTypes = new String[]{
                "Time Duration", 
                "Skill Level", 
                "Skill XP Goal", 
                "Item Collection",
                "Time Window", 
                "Day of Week"
            };
        } else {
            conditionTypes = new String[]{
                "Time Window", 
                "Day of Week", 
                "Skill Level Required",
                "Item Required"
            };
        }
        
        conditionTypeComboBox = new JComboBox<>(conditionTypes);
        
        // Rest of constructor implementation...
        
        // Create split pane for list and tree views
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5); // Equal space for both components
        splitPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Initialize condition list
        JPanel listPanel = createConditionListPanel();
        splitPane.setTopComponent(listPanel);
        
        // Initialize condition tree
         // Initialize condition tree with multi-selection support
         //initializeConditionTree();
        JPanel treePanel = createLogicalTreePanel();
        splitPane.setBottomComponent(treePanel);
        
        add(splitPane, BorderLayout.CENTER);
        
        // Config panel for condition type
        configPanel = new JPanel();
        configPanel.setLayout(new BoxLayout(configPanel, BoxLayout.Y_AXIS));
        configPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        configPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        // Add condition type selector
        JPanel selectorPanel = new JPanel(new BorderLayout(5, 0));
        selectorPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        conditionTypeComboBox.addActionListener(e -> updateConfigPanel());
        selectorPanel.add(conditionTypeComboBox, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        this.addButton = createButton("Add", ColorScheme.PROGRESS_COMPLETE_COLOR);
        addButton.addActionListener(e -> addCurrentCondition());
        buttonPanel.add(addButton);
        
        this.editButton = createButton("Edit", ColorScheme.BRAND_ORANGE);
        editButton.addActionListener(e -> editSelectedCondition());
        buttonPanel.add(editButton);
        
        this.removeButton = createButton("Remove", ColorScheme.PROGRESS_ERROR_COLOR);
        removeButton.addActionListener(e -> removeSelectedCondition());
        buttonPanel.add(removeButton);
        // Initialize logical operations toolbar
        //initializeLogicalOperationsToolbar(buttonPanel);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bottomPanel.add(selectorPanel, BorderLayout.NORTH);
        bottomPanel.add(configPanel, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Initialize the config panel
        updateConfigPanel();
        
        fixSelectionPersistence();
       
    }
    /**
     * Fixes selection persistence in the tree and list view with improved event blocking
     */
    private void fixSelectionPersistence() {
                
        // Store in a class field to allow other methods to access it            
        // Create a tree selection listener that doesn't trigger when programmatically updating
        conditionTree.addTreeSelectionListener(e -> {
            if (updatingSelectionFlag[0]) return;
            
            updateLogicalButtonStates();
            
            // Sync with list - only if there's a valid selection
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) conditionTree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof Condition) {
                Condition condition = (Condition) node.getUserObject();
                int index = getCurrentConditions().indexOf(condition);
                if (index >= 0) {
                    try {
                        updatingSelectionFlag[0] = true;
                        conditionList.setSelectedIndex(index);
                    } finally {
                        updatingSelectionFlag[0] = false;
                    }
                }
            }
        });
        
        // Create a list selection listener that doesn't trigger when programmatically updating
        conditionList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || updatingSelectionFlag[0]) return;
            
            int index = conditionList.getSelectedIndex();
            if (index >= 0 && index < getCurrentConditions().size()) {
                try {
                    updatingSelectionFlag[0] = true;
                    selectNodeForCondition(getCurrentConditions().get(index));
                } finally {
                    updatingSelectionFlag[0] = false;
                }
            }
        });
    }
    /**
     * Gets the current conditions from the selected plugin
     * @return List of conditions, or empty list if no plugin selected
     */
    private List<Condition> getCurrentConditions() {
        return selectScheduledPlugin != null ? 
            selectScheduledPlugin.getConditions() : new ArrayList<>();
    }
    
   
    List<Condition> lastRefreshConditions = new CopyOnWriteArrayList<>();
  /**
 * Refreshes the UI to display the current plugin conditions
 * while preserving selection and expansion state
 */
    private void refreshDisplay() {      
        if (selectScheduledPlugin == null) {
            log.debug("refreshDisplay: No plugin selected, skipping refresh");
            return;
        }
        
        List<Condition> currentConditions = getCurrentConditions();
        log.debug("refreshDisplay: Found {} conditions in plugin", currentConditions.size());
        
        // Store both list and tree selection states with better debugging
        int selectedListIndex = conditionList.getSelectedIndex();
        log.debug("refreshDisplay: Current list selection index: {}", selectedListIndex);
        
        // list selection tracking
        Condition selectedListCondition = null;
        if (selectedListIndex >= 0 && selectedListIndex < currentConditions.size()) {
            selectedListCondition = currentConditions.get(selectedListIndex);
            log.debug("refreshDisplay: List selection mapped to condition: {}", 
                    selectedListCondition.getDescription());
        }
        
        // Remember tree selection with better logging
        Set<Condition> selectedTreeConditions = new HashSet<>();
        TreePath[] selectedTreePaths = conditionTree.getSelectionPaths();
        if (selectedTreePaths != null && selectedTreePaths.length > 0) {
            log.debug("refreshDisplay: Found {} selected tree paths", selectedTreePaths.length);
            for (TreePath path : selectedTreePaths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (node != null && node.getUserObject() instanceof Condition) {
                    Condition condition = (Condition) node.getUserObject();
                    selectedTreeConditions.add(condition);
                    log.debug("refreshDisplay: Added selected tree condition: {}", 
                            condition.getDescription());
                }
            }
        } else {
            log.debug("refreshDisplay: No tree paths selected");
        }
        
        // robust expansion state tracking with better debugging
        Set<Condition> expandedConditions = new HashSet<>();
        Map<Condition, TreePath> expandedPathMap = new HashMap<>(); // Store path for easier restoration
        
        // First check if root node exists
        if (rootNode != null) {
            TreePath rootPath = new TreePath(rootNode.getPath());
            log.debug("refreshDisplay: Getting expanded nodes from root path: {}", rootPath);
            
            Enumeration<TreePath> expandedPaths = conditionTree.getExpandedDescendants(rootPath);
            if (expandedPaths != null && expandedPaths.hasMoreElements()) {
                log.debug("refreshDisplay: Found expanded paths");
                int expandedCount = 0;
                while (expandedPaths.hasMoreElements()) {
                    TreePath path = expandedPaths.nextElement();
                    expandedCount++;
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (node != null && node.getUserObject() instanceof Condition) {
                        Condition condition = (Condition) node.getUserObject();
                        expandedConditions.add(condition);
                        expandedPathMap.put(condition, path);
                        log.debug("refreshDisplay: Added expanded condition: {}", 
                                condition.getDescription());
                    }
                }
                log.debug("refreshDisplay: Found {} expanded paths, {} are conditions", 
                        expandedCount, expandedConditions.size());
            } else {
                log.debug("refreshDisplay: No expanded paths found");
            }
        } else {
            log.debug("refreshDisplay: Root node is null, can't get expanded paths");
        }
        
        // Flag to track update types needed
        boolean needsStructureUpdate = false;  // Complete rebuild needed
        boolean needsTextUpdate = false;       // Just text needs refreshing
        
        // Check if structure has changed
        if (lastRefreshConditions.size() != currentConditions.size()) {
            log.debug("refreshDisplay: Condition count changed from {} to {}, structure update needed", 
                    lastRefreshConditions.size(), currentConditions.size());
            needsStructureUpdate = true;
        } else {
            // Check if conditions have changed or reordered
            for (int i = 0; i < lastRefreshConditions.size(); i++) {
                if (!lastRefreshConditions.get(i).equals(currentConditions.get(i))) {
                    log.debug("refreshDisplay: Condition at index {} changed, structure update needed", i);
                    needsStructureUpdate = true;
                    break;
                }
            }
            
            // If structure unchanged, check if descriptions need updating
            if (!needsStructureUpdate) {
                for (int i = 0; i < currentConditions.size(); i++) {
                    String existingDesc = conditionListModel.getElementAt(i);
                    String newDesc = descriptionForCondition(currentConditions.get(i));
                    if (!existingDesc.equals(newDesc)) {
                        log.debug("refreshDisplay: Description at index {} changed from '{}' to '{}', text update needed", 
                                i, existingDesc, newDesc);
                        needsTextUpdate = true;
                        break;
                    }
                }
            }
        }
        
        // Use a flag to prevent selection events during refresh
        updatingSelectionFlag[0] = true;
        log.debug("refreshDisplay: Setting updatingSelectionFlag to prevent event feedback");
        
        try {
            // Case 1: Full structure update needed
            if (needsStructureUpdate) {
                log.debug("refreshDisplay: Performing full structure update");
                lastRefreshConditions = new CopyOnWriteArrayList<>(currentConditions);
                
                // Update list model
                conditionListModel.clear();
                for (Condition condition : currentConditions) {
                    conditionListModel.addElement(descriptionForCondition(condition));
                }
                
                // Update tree
                updateTreeFromConditions();
                
                // Expand all category nodes by default
                for (int i = 0; i < conditionTree.getRowCount(); i++) {
                    TreePath path = conditionTree.getPathForRow(i);
                    if (path == null) continue;
                    
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (node.getUserObject() instanceof String) {
                        log.debug("refreshDisplay: Auto-expanding category node: {}", node.getUserObject());
                        conditionTree.expandPath(path);
                    }
                }
                
                // Restore expansion state for condition nodes
                if (!expandedConditions.isEmpty()) {
                    log.debug("refreshDisplay: Restoring {} expanded conditions", expandedConditions.size());
                    
                    for (int i = 0; i < conditionTree.getRowCount(); i++) {
                        TreePath path = conditionTree.getPathForRow(i);
                        if (path == null) continue;
                        
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.getUserObject() instanceof Condition) {
                            Condition condition = (Condition) node.getUserObject();
                            if (expandedConditions.contains(condition)) {
                                log.debug("refreshDisplay: Expanding condition node: {}", condition.getDescription());
                                conditionTree.expandPath(path);
                            }
                        }
                    }
                } else {
                    log.debug("refreshDisplay: No expanded conditions to restore");
                }
                
                // Restore selection state
                if (!selectedTreeConditions.isEmpty()) {
                    log.debug("refreshDisplay: Restoring {} selected tree conditions", selectedTreeConditions.size());
                    List<TreePath> pathsToSelect = new ArrayList<>();
                    
                    // Find paths to all selected conditions
                    for (int i = 0; i < conditionTree.getRowCount(); i++) {
                        TreePath path = conditionTree.getPathForRow(i);
                        if (path == null) continue;
                        
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.getUserObject() instanceof Condition) {
                            Condition condition = (Condition) node.getUserObject();
                            if (selectedTreeConditions.contains(condition)) {
                                pathsToSelect.add(path);
                                log.debug("refreshDisplay: Found path for selected condition: {}", 
                                        condition.getDescription());
                            }
                        }
                    }
                    
                    if (!pathsToSelect.isEmpty()) {
                        log.debug("refreshDisplay: Setting {} tree selection paths", pathsToSelect.size());
                        conditionTree.setSelectionPaths(pathsToSelect.toArray(new TreePath[0]));
                    } else {
                        log.debug("refreshDisplay: Could not find any paths for selected conditions");
                    }
                } else {
                    log.debug("refreshDisplay: No tree selections to restore");
                }
                
                // Restore list selection
                if (selectedListCondition != null) {
                    int newIndex = currentConditions.indexOf(selectedListCondition);
                    if (newIndex >= 0) {
                        log.debug("refreshDisplay: Restoring list selection to index {}", newIndex);
                        conditionList.setSelectedIndex(newIndex);
                    } else {
                        log.debug("refreshDisplay: Could not find list selection in current conditions");
                    }
                }
            }
            // Case 2: Only text descriptions need updating
            else if (needsTextUpdate) {
                log.debug("refreshDisplay: Performing text-only update");
                
                // Update just the text in the list model without rebuilding
                for (int i = 0; i < currentConditions.size(); i++) {
                    String newDesc = descriptionForCondition(currentConditions.get(i));
                    if (!conditionListModel.getElementAt(i).equals(newDesc)) {
                        log.debug("refreshDisplay: Updating description at index {} to '{}'", i, newDesc);
                        conditionListModel.setElementAt(newDesc, i);
                    }
                }
                
                // Update tree nodes' text by forcing renderer refresh without rebuilding
                log.debug("refreshDisplay: Repainting tree to refresh node text");
                conditionTree.repaint();
            } else {
                log.debug("refreshDisplay: No updates needed");
            }
        } finally {
            // Re-enable selection events
            updatingSelectionFlag[0] = false;
            log.debug("refreshDisplay: Resetting updatingSelectionFlag to allow events");
        }
    }

    /**
     * Helper method to get consistent description for a condition
     */
    private String descriptionForCondition(Condition condition) {
        // Check if this is a plugin-defined condition
        boolean isPluginDefined = false;
        if (selectScheduledPlugin != null && selectScheduledPlugin.getStopConditionManager() != null) {
            isPluginDefined = selectScheduledPlugin.getStopConditionManager().isPluginDefinedCondition(condition);
        }
        
        // Add with appropriate tag for plugin-defined conditions
        String description = condition.getDescription();
        if (isPluginDefined) {
            description = "[Plugin] " + description;
        }
        
        return description;
    }
    /**
     * Updates the panel when a new plugin is selected
     * 
     * @param selectedPlugin The newly selected plugin, or null if selection cleared
     */
    public void setSelectScheduledPlugin(ScheduledPlugin selectedPlugin) {
        if (selectedPlugin == this.selectScheduledPlugin) {            
            return;
        }else{
            log.info("setSelectScheduledPlugin: Changing selected plugin from {} to {} - reload list and tree", 
                    this.selectScheduledPlugin==null ? "null": this.selectScheduledPlugin.getCleanName() , selectedPlugin==null ? "null" : selectedPlugin.getCleanName());  
        }
                    
        // Store the selected plugin
        this.selectScheduledPlugin = selectedPlugin;
        
        // Enable/disable controls based on whether a plugin is selected
        boolean hasPlugin = (selectedPlugin != null);
        
        saveButton.setEnabled(hasPlugin);
        loadButton.setEnabled(hasPlugin);
        resetButton.setEnabled(hasPlugin);
        editButton.setEnabled(hasPlugin);
        addButton.setEnabled(hasPlugin);
        removeButton.setEnabled(hasPlugin);
        logicComboBox.setEnabled(hasPlugin);
        conditionTypeComboBox.setEnabled(hasPlugin);
        conditionList.setEnabled(hasPlugin);
        conditionTree.setEnabled(hasPlugin);
        
        // Update the plugin name display
        setScheduledPluginNameLabel();
        
        // If a plugin is selected, load its conditions
        if (hasPlugin) {
            // Set the logic type combo box based on the plugin's condition manager
            boolean requireAll = selectedPlugin.getStopConditionManager().requiresAll();
            logicComboBox.setSelectedIndex(requireAll ? 0 : 1);
            
            // Load the conditions from the plugin
            loadConditions(selectedPlugin.getConditions(), requireAll);
        } else {
            // Clear conditions if no plugin selected
            loadConditions(new ArrayList<>(), true);
        }
        
        // Update the tree and list displays
        refreshDisplay();
    }
    
      
    private JPanel createConditionListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                "Condition List",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                FontManager.getRunescapeSmallFont(),
                Color.WHITE
        ));
        
        conditionListModel = new DefaultListModel<>();
        conditionList = new JList<>(conditionListModel);
        conditionList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, 
                                                         boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                List<Condition> currentConditions = getCurrentConditions();
                if (index >= 0 && index < currentConditions.size()) {
                    Condition condition = currentConditions.get(index);
                    
                    if (selectScheduledPlugin != null && 
                        selectScheduledPlugin.getStopConditionManager() != null &&
                        selectScheduledPlugin.getStopConditionManager().isPluginDefinedCondition(condition)) {
                        
                        if (!isSelected) {
                            setForeground(new Color(0, 128, 255)); // Blue for plugin conditions
                        }
                        setFont(getFont().deriveFont(Font.ITALIC)); // Italic for plugin conditions
                    }
                }
                
                return c;
            }
        });
        conditionList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        conditionList.setForeground(Color.WHITE);
        conditionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        /*conditionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int index = conditionList.getSelectedIndex();
                if (index >= 0 && index < getCurrentConditions().size()) {
                    // Select corresponding node in tree
                    selectNodeForCondition(getCurrentConditions().get(index));
                }
            }
        });*/
        
        JScrollPane scrollPane = new JScrollPane(conditionList);
        scrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }
    
        /**
     * Creates the logical condition tree panel with controls
     */
    private JPanel createLogicalTreePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Condition Structure"));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Initialize tree
        rootNode = new DefaultMutableTreeNode("Conditions");
        treeModel = new DefaultTreeModel(rootNode);
        conditionTree = new JTree(treeModel);
        conditionTree.setRootVisible(false);
        conditionTree.setShowsRootHandles(true);
        this.conditionTreeCellRenderer = new ConditionTreeCellRenderer(schedulerPlugin);
        conditionTree.setCellRenderer(this.conditionTreeCellRenderer);
        conditionTree.getSelectionModel().setSelectionMode(
            TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION); // Enable multi-select
        
        // Add tree selection listener to update button states
        boolean[] preventFeedback = {false};  // Use array to allow modification in lambda

        /*conditionTree.addTreeSelectionListener(e -> {
            if (preventFeedback[0]) {
                return;  // Skip processing if we're in the middle of a programmatic selection
            }
            
            updateLogicalButtonStates();
            
            // Sync selection with list
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) conditionTree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof Condition) {
                Condition condition = (Condition) node.getUserObject();
                int index = getCurrentConditions().indexOf(condition);
                if (index >= 0) {
                    preventFeedback[0] = true;  // Prevent feedback loop
                    conditionList.setSelectedIndex(index);
                    preventFeedback[0] = false; // Re-enable processing
                }
            }
        });*/
        
        JScrollPane treeScrollPane = new JScrollPane(conditionTree);
        panel.add(treeScrollPane, BorderLayout.CENTER);
        
        // Create the logical operations toolbar
        JPanel logicalOpPanel = new JPanel();
        logicalOpPanel.setLayout(new BoxLayout(logicalOpPanel, BoxLayout.X_AXIS));
        logicalOpPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        logicalOpPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Group operations section
        JButton createAndButton = createButton("Group as AND", ColorScheme.BRAND_ORANGE);
        createAndButton.setToolTipText("Group selected conditions with AND logic");
        createAndButton.addActionListener(e -> createLogicalGroup(true));
        
        JButton createOrButton = createButton("Group as OR", BRAND_BLUE);
        createOrButton.setToolTipText("Group selected conditions with OR logic");
        createOrButton.addActionListener(e -> createLogicalGroup(false));
        
        // Negation button
        JButton negateButton = createButton("Negate", new Color(220, 50, 50));
        negateButton.setToolTipText("Negate the selected condition (toggle NOT)");
        negateButton.addActionListener(e -> negateSelectedCondition());
        
        // Convert operation buttons
        JButton convertToAndButton = createButton("Convert to AND", ColorScheme.BRAND_ORANGE);
        convertToAndButton.setToolTipText("Convert selected logical group to AND type");
        convertToAndButton.addActionListener(e -> convertLogicalType(true));
        
        JButton convertToOrButton = createButton("Convert to OR", BRAND_BLUE);
        convertToOrButton.setToolTipText("Convert selected logical group to OR type");
        convertToOrButton.addActionListener(e -> convertLogicalType(false));
        
        // Ungroup button
        JButton ungroupButton = createButton("Ungroup", ColorScheme.LIGHT_GRAY_COLOR);
        ungroupButton.setToolTipText("Remove the logical group but keep its conditions");
        ungroupButton.addActionListener(e -> ungroupSelectedLogical());
        
        // Add buttons to panel with separators
        logicalOpPanel.add(createAndButton);
        logicalOpPanel.add(Box.createHorizontalStrut(5));
        logicalOpPanel.add(createOrButton);
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(new JSeparator(SwingConstants.VERTICAL));
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(negateButton);
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(new JSeparator(SwingConstants.VERTICAL));
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(convertToAndButton);
        logicalOpPanel.add(Box.createHorizontalStrut(5));
        logicalOpPanel.add(convertToOrButton);
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(new JSeparator(SwingConstants.VERTICAL));
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(ungroupButton);
        
        // Store references to buttons that need context-sensitive enabling
        this.negateButton = negateButton;
        this.convertToAndButton = convertToAndButton;
        this.convertToOrButton = convertToOrButton;
        this.ungroupButton = ungroupButton;
        
        // Initial state
        updateLogicalButtonStates();
        
        panel.add(logicalOpPanel, BorderLayout.SOUTH);
        
        return panel;
    }

    /**
     * Creates a styled button with consistent appearance
     */
    private JButton createButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.BLACK),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        
        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(color.brighter());
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(color);
            }
        });
        
        return button;
    }

        private void updateConfigPanel() {
        configPanel.removeAll();
        
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 2, 2);
        
        String selectedType = (String) conditionTypeComboBox.getSelectedItem();
        
        if (stopConditionPanel) {
            switch (selectedType) {
                case "Time Duration":
                    createTimeConfigPanel(panel, gbc);
                    break;
                case "Skill Level":
                    createSkillLevelConfigPanel(panel, gbc);
                    break;
                case "Skill XP Goal":
                    createSkillXpConfigPanel(panel, gbc);
                    break;
                case "Item Collection":
                    createItemConfigPanel(panel, gbc);
                    break;
                case "Time Window":
                    createTimeWindowConfigPanel(panel, gbc);
                    break;
                case "Day of Week":
                    createDayOfWeekConfigPanel(panel, gbc);
                    break;
            }
        } else {
            switch (selectedType) {
                case "Time Window":
                    createTimeWindowConfigPanel(panel, gbc);
                    break;
                case "Day of Week":
                    createDayOfWeekConfigPanel(panel, gbc);
                    break;
                case "Skill Level Required":
                    createSkillLevelConfigPanel(panel, gbc);
                    break;
                case "Item Required":
                    createItemConfigPanel(panel, gbc);
                    break;
            }
        }
        
        configPanel.add(panel);
        configPanel.revalidate();
        configPanel.repaint();
    }
    
    private void createTimeConfigPanel(JPanel panel, GridBagConstraints gbc) {
        JLabel durationLabel = new JLabel("Duration Range:");
        durationLabel.setForeground(Color.WHITE);
        durationLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(durationLabel, gbc);
        
        // Time range panel with combined HH:MM format
        gbc.gridy++;
        JPanel timeRangePanel = new JPanel(new GridLayout(2, 2, 5, 5));
        timeRangePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Min time input
        JLabel minTimeLabel = new JLabel("Min Duration (HH:MM):");
        minTimeLabel.setForeground(Color.WHITE);
        timeRangePanel.add(minTimeLabel);
        
        // Create combined min time panel
        JPanel minTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        minTimePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Use a formatted text field with validation for "HH:MM" format
        JTextField minTimeField = new JTextField(5);
        minTimeField.setText("01:00"); // Default 1 hour
        minTimeField.setForeground(Color.WHITE);
        minTimeField.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        minTimePanel.add(minTimeField);
        
        timeRangePanel.add(minTimePanel);
        
        // Max time input
        JLabel maxTimeLabel = new JLabel("Max Duration (HH:MM):");
        maxTimeLabel.setForeground(Color.WHITE);
        timeRangePanel.add(maxTimeLabel);
        
        // Create combined max time panel
        JPanel maxTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        maxTimePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Use a formatted text field with validation for "HH:MM" format
        JTextField maxTimeField = new JTextField(5);
        maxTimeField.setText("02:30"); // Default 2:30 hours
        maxTimeField.setForeground(Color.WHITE);
        maxTimeField.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        maxTimePanel.add(maxTimeField);
        
        timeRangePanel.add(maxTimePanel);
        
        panel.add(timeRangePanel, gbc);
        
        // Add input validation for time fields
        minTimeField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                validateTimeField(minTimeField, maxTimeField, true);
            }
        });
        
        maxTimeField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                validateTimeField(minTimeField, maxTimeField, false);
            }
        });
        
        // Randomization options
        gbc.gridy++;
        JPanel randomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        randomPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JCheckBox randomizeCheckBox = new JCheckBox("Randomize within range");
        randomizeCheckBox.setForeground(Color.WHITE);
        randomizeCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckBox.setSelected(true);
        randomPanel.add(randomizeCheckBox);
        
        JLabel randomFactorLabel = new JLabel("Random Factor:");
        randomFactorLabel.setForeground(Color.WHITE);
        randomPanel.add(randomFactorLabel);
        
        SpinnerNumberModel randomFactorModel = new SpinnerNumberModel(0.3, 0.0, 1.0, 0.05);
        JSpinner randomFactorSpinner = new JSpinner(randomFactorModel);
        randomFactorSpinner.setPreferredSize(new Dimension(70, randomFactorSpinner.getPreferredSize().height));
        randomPanel.add(randomFactorSpinner);
        
        // Disable random factor when randomize is unchecked
        randomizeCheckBox.addChangeListener(e -> {
            randomFactorSpinner.setEnabled(randomizeCheckBox.isSelected());
        });
        
        panel.add(randomPanel, gbc);
        
        // Add a helpful description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Plugin will stop after a random time between min and max");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Store components for later access when creating conditions
        configPanel.putClientProperty("minTimeField", minTimeField);
        configPanel.putClientProperty("maxTimeField", maxTimeField);
        configPanel.putClientProperty("randomizeCheckBox", randomizeCheckBox);
        configPanel.putClientProperty("randomFactorSpinner", randomFactorSpinner);
    }

    /**
     * Validates a time field to ensure it contains a valid HH:MM format
     * Enforces min ≤ max constraint when comparing fields
     */
    private void validateTimeField(JTextField minField, JTextField maxField, boolean isMinField) {
        try {
            // Parse the current field
            String text = isMinField ? minField.getText() : maxField.getText();
            String[] parts = text.split(":");
            
            if (parts.length != 2) {
                throw new NumberFormatException("Invalid format");
            }
            
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            
            // Validate ranges
            hours = Math.min(23, Math.max(0, hours));
            minutes = Math.min(59, Math.max(0, minutes));
            
            // Format properly
            String formatted = String.format("%02d:%02d", hours, minutes);
            
            if (isMinField) {
                minField.setText(formatted);
                
                // Check min ≤ max
                String[] maxParts = maxField.getText().split(":");
                if (maxParts.length == 2) {
                    int maxHours = Integer.parseInt(maxParts[0]);
                    int maxMinutes = Integer.parseInt(maxParts[1]);
                    
                    if (hours > maxHours || (hours == maxHours && minutes > maxMinutes)) {
                        maxField.setText(formatted);
                    }
                }
            } else {
                maxField.setText(formatted);
                
                // Check max ≥ min
                String[] minParts = minField.getText().split(":");
                if (minParts.length == 2) {
                    int minHours = Integer.parseInt(minParts[0]);
                    int minMinutes = Integer.parseInt(minParts[1]);
                    
                    if (hours < minHours || (hours == minHours && minutes < minMinutes)) {
                        minField.setText(formatted);
                    }
                }
            }
        } catch (NumberFormatException e) {
            // Reset to a default valid value if parsing fails
            if (isMinField) {
                minField.setText("01:00");
            } else {
                maxField.setText("02:00");
            }
        }
    }

    private void createTimeWindowConfigPanel(JPanel panel, GridBagConstraints gbc) {
        JLabel startTimeLabel = new JLabel("Start Time (24h format):");
        startTimeLabel.setForeground(Color.WHITE);
        panel.add(startTimeLabel, gbc);
        
        // Start time panel with hour and minute
        gbc.gridy++;
        JPanel startTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        startTimePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        SpinnerNumberModel startHourModel = new SpinnerNumberModel(9, 0, 23, 1);
        JSpinner startHourSpinner = new JSpinner(startHourModel);
        startTimePanel.add(startHourSpinner);
        
        JLabel hourLabel = new JLabel("hour");
        hourLabel.setForeground(Color.WHITE);
        startTimePanel.add(hourLabel);
        
        SpinnerNumberModel startMinuteModel = new SpinnerNumberModel(0, 0, 59, 1);
        JSpinner startMinuteSpinner = new JSpinner(startMinuteModel);
        startTimePanel.add(startMinuteSpinner);
        
        JLabel minuteLabel = new JLabel("min");
        minuteLabel.setForeground(Color.WHITE);
        startTimePanel.add(minuteLabel);
        
        panel.add(startTimePanel, gbc);
        
        // End time
        gbc.gridy++;
        JLabel endTimeLabel = new JLabel("End Time (24h format):");
        endTimeLabel.setForeground(Color.WHITE);
        panel.add(endTimeLabel, gbc);
        
        gbc.gridy++;
        JPanel endTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        endTimePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        SpinnerNumberModel endHourModel = new SpinnerNumberModel(17, 0, 23, 1);
        JSpinner endHourSpinner = new JSpinner(endHourModel);
        endTimePanel.add(endHourSpinner);
        
        JLabel endHourLabel = new JLabel("hour");
        endHourLabel.setForeground(Color.WHITE);
        endTimePanel.add(endHourLabel);
        
        SpinnerNumberModel endMinuteModel = new SpinnerNumberModel(0, 0, 59, 1);
        JSpinner endMinuteSpinner = new JSpinner(endMinuteModel);
        endTimePanel.add(endMinuteSpinner);
        
        JLabel endMinuteLabel = new JLabel("min");
        endMinuteLabel.setForeground(Color.WHITE);
        endTimePanel.add(endMinuteLabel);
        
        panel.add(endTimePanel, gbc);
        
        // Description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Plugin will only run during specified hours");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        gbc.gridy++;
        JLabel crossDayLabel = new JLabel("Note: If start > end, window crosses midnight");
        crossDayLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        crossDayLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(crossDayLabel, gbc);
        
        // Store the components for later
        configPanel.putClientProperty("startHourSpinner", startHourSpinner);
        configPanel.putClientProperty("startMinuteSpinner", startMinuteSpinner);
        configPanel.putClientProperty("endHourSpinner", endHourSpinner);
        configPanel.putClientProperty("endMinuteSpinner", endMinuteSpinner);
    }


    private void createDayOfWeekConfigPanel(JPanel panel, GridBagConstraints gbc) {
        JLabel daysLabel = new JLabel("Active Days:");
        daysLabel.setForeground(Color.WHITE);
        panel.add(daysLabel, gbc);
        
        // Preset options
        gbc.gridy++;
        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        presetPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JButton weekdaysButton = new JButton("Weekdays");
        weekdaysButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        weekdaysButton.setForeground(Color.WHITE);
        
        JButton weekendsButton = new JButton("Weekends");
        weekendsButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        weekendsButton.setForeground(Color.WHITE);
        
        JButton allDaysButton = new JButton("All Days");
        allDaysButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        allDaysButton.setForeground(Color.WHITE);
        
        presetPanel.add(weekdaysButton);
        presetPanel.add(weekendsButton);
        presetPanel.add(allDaysButton);
        
        panel.add(presetPanel, gbc);
        
        // Day checkboxes
        gbc.gridy++;
        JPanel daysPanel = new JPanel(new GridLayout(0, 3));
        daysPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        String[] dayNames = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        JCheckBox[] dayCheckboxes = new JCheckBox[7];
        
        for (int i = 0; i < dayNames.length; i++) {
            dayCheckboxes[i] = new JCheckBox(dayNames[i]);
            dayCheckboxes[i].setBackground(ColorScheme.DARKER_GRAY_COLOR);
            dayCheckboxes[i].setForeground(Color.WHITE);
            daysPanel.add(dayCheckboxes[i]);
        }
        
        // Set up weekdays button
        weekdaysButton.addActionListener(e -> {
            for (int i = 0; i < 5; i++) {
                dayCheckboxes[i].setSelected(true);
            }
            dayCheckboxes[5].setSelected(false);
            dayCheckboxes[6].setSelected(false);
        });
        
        // Set up weekends button
        weekendsButton.addActionListener(e -> {
            for (int i = 0; i < 5; i++) {
                dayCheckboxes[i].setSelected(false);
            }
            dayCheckboxes[5].setSelected(true);
            dayCheckboxes[6].setSelected(true);
        });
        
        // Set up all days button
        allDaysButton.addActionListener(e -> {
            for (JCheckBox checkbox : dayCheckboxes) {
                checkbox.setSelected(true);
            }
        });
        
        panel.add(daysPanel, gbc);
        
        // Description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Plugin will only run on selected days of the week");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Store components for later access
        configPanel.putClientProperty("dayCheckboxes", dayCheckboxes);
    }
    private void createSkillLevelConfigPanel(JPanel panel, GridBagConstraints gbc) {
        JLabel skillLabel = new JLabel("Skill:");
        skillLabel.setForeground(Color.WHITE);
        panel.add(skillLabel, gbc);
        
        gbc.gridx++;
        JComboBox<String> skillComboBox = new JComboBox<>();
        for (Skill skill : Skill.values()) {
            skillComboBox.addItem(skill.getName());
        }
        panel.add(skillComboBox, gbc);
        
        // Target level panel
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        
        JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        levelPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel targetLevelLabel = new JLabel("Target Level:");
        targetLevelLabel.setForeground(Color.WHITE);
        levelPanel.add(targetLevelLabel);
        
        SpinnerNumberModel levelModel = new SpinnerNumberModel(10, 1, 99, 1);
        JSpinner levelSpinner = new JSpinner(levelModel);
        levelPanel.add(levelSpinner);
        
        JCheckBox randomizeCheckBox = new JCheckBox("Randomize");
        randomizeCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckBox.setForeground(Color.WHITE);
        levelPanel.add(randomizeCheckBox);
        
        panel.add(levelPanel, gbc);
        
        // Min/Max panel (only visible when randomize is checked)
        gbc.gridy++;
        JPanel minMaxPanel = new JPanel(new GridLayout(1, 4, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min:");
        minLabel.setForeground(Color.WHITE);
        minMaxPanel.add(minLabel);
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(5, 1, 99, 1);
        JSpinner minSpinner = new JSpinner(minModel);
        minMaxPanel.add(minSpinner);
        
        JLabel maxLabel = new JLabel("Max:");
        maxLabel.setForeground(Color.WHITE);
        minMaxPanel.add(maxLabel);
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(10, 1, 99, 1);
        JSpinner maxSpinner = new JSpinner(maxModel);
        minMaxPanel.add(maxSpinner);
        
        minMaxPanel.setVisible(false);
        panel.add(minMaxPanel, gbc);
        
        // Toggle min/max panel visibility based on randomize checkbox
        randomizeCheckBox.addChangeListener(e -> {
            minMaxPanel.setVisible(randomizeCheckBox.isSelected());
            levelSpinner.setEnabled(!randomizeCheckBox.isSelected());
            
            // If enabling randomize, set min/max from current level
            if (randomizeCheckBox.isSelected()) {
                int level = (Integer) levelSpinner.getValue();
                minSpinner.setValue(Math.max(1, level - 5));
                maxSpinner.setValue(Math.min(99, level + 5));
            }
            
            panel.revalidate();
            panel.repaint();
        });
        
        // Add value change listeners for min/max validation
        minSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (min > max) {
                maxSpinner.setValue(min);
            }
        });
        
        maxSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (max < min) {
                minSpinner.setValue(max);
            }
        });
        
        // Description
        gbc.gridy++;
        JLabel descriptionLabel;
        if (stopConditionPanel) {
            descriptionLabel = new JLabel("Plugin will stop when skill reaches target level");
        } else {
            descriptionLabel = new JLabel("Plugin will only start when skill is at or above target level");
        }
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Store components
        configPanel.putClientProperty("skillComboBox", skillComboBox);
        configPanel.putClientProperty("levelSpinner", levelSpinner);
        configPanel.putClientProperty("minLevelSpinner", minSpinner);
        configPanel.putClientProperty("maxLevelSpinner", maxSpinner);
        configPanel.putClientProperty("randomizeSkillLevel", randomizeCheckBox);
    }

    // Improved Skill XP panel
    private void createSkillXpConfigPanel(JPanel panel, GridBagConstraints gbc) {
        JLabel skillLabel = new JLabel("Skill:");
        skillLabel.setForeground(Color.WHITE);
        panel.add(skillLabel, gbc);
        
        gbc.gridx++;
        JComboBox<String> skillComboBox = new JComboBox<>();
        for (Skill skill : Skill.values()) {
            skillComboBox.addItem(skill.getName());
        }
        panel.add(skillComboBox, gbc);
        
        // Target XP panel
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        
        JPanel xpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        xpPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel targetXpLabel = new JLabel("Target XP:");
        targetXpLabel.setForeground(Color.WHITE);
        xpPanel.add(targetXpLabel);
        
        SpinnerNumberModel xpModel = new SpinnerNumberModel(10000, 1, 200000000, 1000);
        JSpinner xpSpinner = new JSpinner(xpModel);
        // Make XP spinner wider to accommodate larger numbers
        xpSpinner.setPreferredSize(new Dimension(100, xpSpinner.getPreferredSize().height));
        xpPanel.add(xpSpinner);
        
        JCheckBox randomizeCheckBox = new JCheckBox("Randomize");
        randomizeCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckBox.setForeground(Color.WHITE);
        xpPanel.add(randomizeCheckBox);
        
        panel.add(xpPanel, gbc);
        
        // Min/Max panel (only visible when randomize is checked)
        gbc.gridy++;
        JPanel minMaxPanel = new JPanel(new GridLayout(1, 4, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min XP:");
        minLabel.setForeground(Color.WHITE);
        minMaxPanel.add(minLabel);
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(5000, 1, 200000000, 1000);
        JSpinner minSpinner = new JSpinner(minModel);
        minSpinner.setPreferredSize(new Dimension(100, minSpinner.getPreferredSize().height));
        minMaxPanel.add(minSpinner);
        
        JLabel maxLabel = new JLabel("Max XP:");
        maxLabel.setForeground(Color.WHITE);
        minMaxPanel.add(maxLabel);
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(15000, 1, 200000000, 1000);
        JSpinner maxSpinner = new JSpinner(maxModel);
        maxSpinner.setPreferredSize(new Dimension(100, maxSpinner.getPreferredSize().height));
        minMaxPanel.add(maxSpinner);
        
        minMaxPanel.setVisible(false);
        panel.add(minMaxPanel, gbc);
        
        // Toggle min/max panel visibility based on randomize checkbox
        randomizeCheckBox.addChangeListener(e -> {
            minMaxPanel.setVisible(randomizeCheckBox.isSelected());
            xpSpinner.setEnabled(!randomizeCheckBox.isSelected());
            
            // If enabling randomize, set min/max from current XP
            if (randomizeCheckBox.isSelected()) {
                int xp = (Integer) xpSpinner.getValue();
                minSpinner.setValue(Math.max(1, xp - 5000));
                maxSpinner.setValue(Math.min(200000000, xp + 5000));
            }
            
            panel.revalidate();
            panel.repaint();
        });
        
        // Add value change listeners for min/max validation
        minSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (min > max) {
                maxSpinner.setValue(min);
            }
        });
        
        maxSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (max < min) {
                minSpinner.setValue(max);
            }
        });
        
        // Description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Plugin will stop when skill XP reaches target value");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Store components
        configPanel.putClientProperty("xpSkillComboBox", skillComboBox);
        configPanel.putClientProperty("xpSpinner", xpSpinner);
        configPanel.putClientProperty("minXpSpinner", minSpinner);
        configPanel.putClientProperty("maxXpSpinner", maxSpinner);
        configPanel.putClientProperty("randomizeSkillXp", randomizeCheckBox);
    }
    
    // Improved Item Collection panel
    private void createItemConfigPanel(JPanel panel, GridBagConstraints gbc) {
        JLabel itemsLabel = new JLabel("Item Names (comma-separated, supports regex):");
        itemsLabel.setForeground(Color.WHITE);
        panel.add(itemsLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        JTextField itemsField = new JTextField();
        itemsField.setToolTipText("Examples: 'Bones', 'Shark|Lobster', '.*rune$' (all items ending with 'rune')");
        panel.add(itemsField, gbc);
        
        // Tracking options
        gbc.gridy++;
        JPanel trackingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        trackingPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JCheckBox trackInventoryCheckBox = new JCheckBox("Track Inventory");
        trackInventoryCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        trackInventoryCheckBox.setForeground(Color.WHITE);
        trackInventoryCheckBox.setSelected(true);
        trackingPanel.add(trackInventoryCheckBox);
        
        JCheckBox trackBankCheckBox = new JCheckBox("Track Bank");
        trackBankCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        trackBankCheckBox.setForeground(Color.WHITE);
        trackBankCheckBox.setSelected(false);
        trackingPanel.add(trackBankCheckBox);
        
        panel.add(trackingPanel, gbc);
        
        // Target amount panel
        gbc.gridy++;
        JPanel amountPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        amountPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel amountLabel = new JLabel("Target Amount:");
        amountLabel.setForeground(Color.WHITE);
        amountPanel.add(amountLabel);
        
        SpinnerNumberModel amountModel = new SpinnerNumberModel(100, 1, 2147483647, 10);
        JSpinner amountSpinner = new JSpinner(amountModel);
        amountSpinner.setPreferredSize(new Dimension(100, amountSpinner.getPreferredSize().height));
        amountPanel.add(amountSpinner);
        
        JCheckBox randomizeCheckBox = new JCheckBox("Randomize");
        randomizeCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckBox.setForeground(Color.WHITE);
        amountPanel.add(randomizeCheckBox);
        if (!stopConditionPanel) {
            randomizeCheckBox.setVisible(false);
        }
        panel.add(amountPanel, gbc);
        
        // Min/Max panel
        gbc.gridy++;
        JPanel minMaxPanel = new JPanel(new GridLayout(1, 4, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min:");
        minLabel.setForeground(Color.WHITE);
        minMaxPanel.add(minLabel);
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(50, 1, 2147483647, 10);
        JSpinner minSpinner = new JSpinner(minModel);
        minMaxPanel.add(minSpinner);
        
        JLabel maxLabel = new JLabel("Max:");
        maxLabel.setForeground(Color.WHITE);
        minMaxPanel.add(maxLabel);
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(150, 1, 2147483647, 10);
        JSpinner maxSpinner = new JSpinner(maxModel);
        minMaxPanel.add(maxSpinner);
        
        minMaxPanel.setVisible(false);
        panel.add(minMaxPanel, gbc);
        
        // Toggle min/max panel based on randomize checkbox
        randomizeCheckBox.addChangeListener(e -> {
            minMaxPanel.setVisible(randomizeCheckBox.isSelected());
            amountSpinner.setEnabled(!randomizeCheckBox.isSelected());
            
            // If enabling randomize, set min/max from current amount
            if (randomizeCheckBox.isSelected()) {
                int amount = (Integer) amountSpinner.getValue();
                minSpinner.setValue(Math.max(1, amount - 50));
                maxSpinner.setValue(amount + 50);
            }
            
            panel.revalidate();
            panel.repaint();
        });
        
        // Add value change listeners for min/max validation
        minSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (min > max) {
                maxSpinner.setValue(min);
            }
        });
        
        maxSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (max < min) {
                minSpinner.setValue(max);
            }
        });
        
        // Description
        gbc.gridy++;
        JLabel descriptionLabel;
        if (stopConditionPanel) {
            descriptionLabel = new JLabel("Plugin will stop when target amount of items is collected");
        } else {
            descriptionLabel = new JLabel("Plugin will only start when you have the required items");
        }
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Add regex help
        gbc.gridy++;
        JLabel regexHelpLabel = new JLabel("Regex help: Use '|' for OR, '.*' for any characters");
        regexHelpLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        regexHelpLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(regexHelpLabel, gbc);
        
        // Store components
        configPanel.putClientProperty("itemsField", itemsField);        
        configPanel.putClientProperty("amountSpinner", amountSpinner);
        configPanel.putClientProperty("minAmountSpinner", minSpinner);
        configPanel.putClientProperty("maxAmountSpinner", maxSpinner);
        configPanel.putClientProperty("randomizeItemAmount", randomizeCheckBox);
    }
    
    public void setUserConditionUpdateCallback(Consumer<LogicalCondition> callback) {
        this.userConditionUpdateCallback = callback;
    }
        
        
    private TimeWindowCondition createTimeWindowCondition() {
        JSpinner startHourSpinner = (JSpinner) configPanel.getClientProperty("startHourSpinner");
        JSpinner startMinuteSpinner = (JSpinner) configPanel.getClientProperty("startMinuteSpinner");
        JSpinner endHourSpinner = (JSpinner) configPanel.getClientProperty("endHourSpinner");
        JSpinner endMinuteSpinner = (JSpinner) configPanel.getClientProperty("endMinuteSpinner");
        
        int startHour = (Integer) startHourSpinner.getValue();
        int startMinute = (Integer) startMinuteSpinner.getValue();
        int endHour = (Integer) endHourSpinner.getValue();
        int endMinute = (Integer) endMinuteSpinner.getValue();
        
        LocalTime startTime = LocalTime.of(startHour, startMinute);
        LocalTime endTime = LocalTime.of(endHour, endMinute);
        
        return new TimeWindowCondition(startTime, endTime);
    }
    
    // Add method to create DayOfWeekCondition
    private DayOfWeekCondition createDayOfWeekCondition() {
        JCheckBox[] dayCheckboxes = (JCheckBox[]) configPanel.getClientProperty("dayCheckboxes");
        Set<DayOfWeek> activeDays = EnumSet.noneOf(DayOfWeek.class);
        
        // Map checkbox indices to DayOfWeek enum values (0 = Monday)
        if (dayCheckboxes[0].isSelected()) activeDays.add(DayOfWeek.MONDAY);
        if (dayCheckboxes[1].isSelected()) activeDays.add(DayOfWeek.TUESDAY);
        if (dayCheckboxes[2].isSelected()) activeDays.add(DayOfWeek.WEDNESDAY);
        if (dayCheckboxes[3].isSelected()) activeDays.add(DayOfWeek.THURSDAY);
        if (dayCheckboxes[4].isSelected()) activeDays.add(DayOfWeek.FRIDAY);
        if (dayCheckboxes[5].isSelected()) activeDays.add(DayOfWeek.SATURDAY);
        if (dayCheckboxes[6].isSelected()) activeDays.add(DayOfWeek.SUNDAY);
        
        return new DayOfWeekCondition(activeDays);
    }
    
    // Update SkillLevelCondition to use randomization
    private SkillLevelCondition createSkillLevelCondition() {
        JComboBox<String> skillComboBox = (JComboBox<String>) configPanel.getClientProperty("skillComboBox");
        JCheckBox randomizeCheckBox = (JCheckBox) configPanel.getClientProperty("randomizeSkillLevel");
        
        String skillName = (String) skillComboBox.getSelectedItem();
        Skill skill = Skill.valueOf(skillName.toUpperCase());
        
        if (randomizeCheckBox.isSelected()) {
            JSpinner minLevelSpinner = (JSpinner) configPanel.getClientProperty("minLevelSpinner");
            JSpinner maxLevelSpinner = (JSpinner) configPanel.getClientProperty("maxLevelSpinner");
            
            int minLevel = (Integer) minLevelSpinner.getValue();
            int maxLevel = (Integer) maxLevelSpinner.getValue();
            
            return SkillLevelCondition.createRandomized(skill, minLevel, maxLevel);
        } else {
            JSpinner levelSpinner = (JSpinner) configPanel.getClientProperty("levelSpinner");
            int level = (Integer) levelSpinner.getValue();
            
            return new SkillLevelCondition(skill, level);
        }
    }
    
    // Update SkillXpCondition to use randomization
    private SkillXpCondition createSkillXpCondition() {
        JComboBox<String> skillComboBox = (JComboBox<String>) configPanel.getClientProperty("xpSkillComboBox");
        JCheckBox randomizeCheckBox = (JCheckBox) configPanel.getClientProperty("randomizeSkillXp");
        
        String skillName = (String) skillComboBox.getSelectedItem();
        Skill skill = Skill.valueOf(skillName.toUpperCase());
        
        if (randomizeCheckBox.isSelected()) {
            JSpinner minXpSpinner = (JSpinner) configPanel.getClientProperty("minXpSpinner");
            JSpinner maxXpSpinner = (JSpinner) configPanel.getClientProperty("maxXpSpinner");
            
            int minXp = (Integer) minXpSpinner.getValue();
            int maxXp = (Integer) maxXpSpinner.getValue();
            
            return SkillXpCondition.createRandomized(skill, minXp, maxXp);
        } else {
            JSpinner xpSpinner = (JSpinner) configPanel.getClientProperty("xpSpinner");
            int xp = (Integer) xpSpinner.getValue();
            
            return new SkillXpCondition(skill, xp);
        }
    }
    
    // Update LootItemCondition to handle regex and track options
    private List<LootItemCondition> createItemCondition() {
        JTextField itemsField = (JTextField) configPanel.getClientProperty("itemsField");        
        
        JCheckBox randomizeCheckBox = (JCheckBox) configPanel.getClientProperty("randomizeItemAmount");
        
        String itemNamesString = itemsField.getText().trim();
        if (itemNamesString.isEmpty()) {
            return null; // Invalid item name
        }
        
        // Split by comma and trim each item name
        String[] itemNamesArray = itemNamesString.split(",");
        List<String> itemNames = new ArrayList<>();
        
        for (String itemName : itemNamesArray) {
            itemName = itemName.trim();
            if (!itemName.isEmpty()) {
                itemNames.add(itemName);
            }
        }
        
        if (itemNames.isEmpty()) {
            return null;
        }
        
        List<LootItemCondition> lootItemConditions = new ArrayList<>();
        for (String itemName : itemNames) {
            LootItemCondition condition;
            
            if (randomizeCheckBox.isSelected()) {
                JSpinner minAmountSpinner = (JSpinner) configPanel.getClientProperty("minAmountSpinner");
                JSpinner maxAmountSpinner = (JSpinner) configPanel.getClientProperty("maxAmountSpinner");
                
                int minAmount = (Integer) minAmountSpinner.getValue();
                int maxAmount = (Integer) maxAmountSpinner.getValue();
                
                condition = LootItemCondition.createRandomized(itemName, minAmount, maxAmount);
            } else {
                JSpinner amountSpinner = (JSpinner) configPanel.getClientProperty("amountSpinner");
                int amount = (Integer) amountSpinner.getValue();
                
                condition = new LootItemCondition(itemName, amount);
            }                                            
            lootItemConditions.add(condition);
        }
        
        return lootItemConditions;
    }
    
    private IntervalCondition createTimeCondition() {
        JTextField minTimeField = (JTextField) configPanel.getClientProperty("minTimeField");
        JTextField maxTimeField = (JTextField) configPanel.getClientProperty("maxTimeField");
        JCheckBox randomizeCheckBox = (JCheckBox) configPanel.getClientProperty("randomizeCheckBox");
        JSpinner randomFactorSpinner = (JSpinner) configPanel.getClientProperty("randomFactorSpinner");
        
        // Parse min time
        String[] minParts = minTimeField.getText().split(":");
        int minHours = Integer.parseInt(minParts[0]);
        int minMinutes = Integer.parseInt(minParts[1]);
        
        // Parse max time
        String[] maxParts = maxTimeField.getText().split(":");
        int maxHours = Integer.parseInt(maxParts[0]);
        int maxMinutes = Integer.parseInt(maxParts[1]);
        
        boolean randomize = randomizeCheckBox.isSelected();
        double randomFactor = randomize ? (Double) randomFactorSpinner.getValue() : 0.0;
        
        Duration minDuration = Duration.ofHours(minHours).plusMinutes(minMinutes);
        Duration maxDuration = Duration.ofHours(maxHours).plusMinutes(maxMinutes);
        
        // Check if min and max are the same
        if (minHours == maxHours && minMinutes == maxMinutes) {
            return new IntervalCondition(minDuration, randomize, randomFactor);
        }
        
        // Create randomized interval condition
        return IntervalCondition.createRandomized(minDuration, maxDuration);
    }
    private void editSelectedCondition() {
        int selectedIndex = conditionList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= getCurrentConditions().size()) {
            return;
        }
        
        // Editing is complex and depends on condition type
        // A full implementation would load the condition values back into the UI
        // For simplicity, we'll just remove and let the user re-add
        removeSelectedCondition();
    }
       
    
    private void loadConditions(List<Condition> conditionList, boolean requireAll) {
        boolean needsUpdate = false;
        for (Condition condition : conditionList) {
            if (! lastRefreshConditions.contains(condition)){
                needsUpdate = true;
            }
        }
        conditionListModel.clear();
        
        if (conditionList != null) {
            for (Condition condition : conditionList) {
                // Check if this is a plugin-defined condition
                boolean isPluginDefined = false;
                if (selectScheduledPlugin != null && selectScheduledPlugin.getStopConditionManager() != null) {
                    isPluginDefined = selectScheduledPlugin.getStopConditionManager().isPluginDefinedCondition(condition);
                }
                
                // Add with appropriate tag for plugin-defined conditions
                String description = condition.getDescription();
                if (isPluginDefined) {
                    description = "[Plugin] " + description;
                }
                
                conditionListModel.addElement(description);
            }
        }
                
        logicComboBox.setSelectedIndex(requireAll ? 0 : 1);
        updateTreeFromConditions();
    }
    /**
    * Updates the tree from conditions while preserving selection and expansion state
    */
    private void updateTreeFromConditions() {
        // Store selected conditions and expanded state before rebuilding
        Set<Condition> selectedConditions = new HashSet<>();
        Set<Condition> expandedConditions = new HashSet<>();
        
        // Remember selected conditions
        TreePath[] selectedPaths = conditionTree.getSelectionPaths();
        if (selectedPaths != null) {
            for (TreePath path : selectedPaths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (node.getUserObject() instanceof Condition) {
                    selectedConditions.add((Condition) node.getUserObject());
                }
            }
        }
        
        // Remember expanded nodes
        Enumeration<TreePath> expandedPaths = conditionTree.getExpandedDescendants(new TreePath(rootNode.getPath()));
        if (expandedPaths != null) {
            while (expandedPaths.hasMoreElements()) {
                TreePath path = expandedPaths.nextElement();
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (node.getUserObject() instanceof Condition) {
                    expandedConditions.add((Condition) node.getUserObject());
                }
            }
        }
        
        // Clear and rebuild tree
        rootNode.removeAllChildren();
        
        if (selectScheduledPlugin == null) {
            treeModel.nodeStructureChanged(rootNode);
            return;
        }
        
        // Build the tree from the condition manager
        ConditionManager manager = selectScheduledPlugin.getStopConditionManager();
        
        // If there is a plugin condition, show plugin and user sections separately
        if (manager.getPluginCondition() != null && !manager.getPluginCondition().getConditions().isEmpty()) {
            // Add plugin section
            DefaultMutableTreeNode pluginNode = new DefaultMutableTreeNode("Plugin Conditions");
            rootNode.add(pluginNode);
            buildConditionTree(pluginNode, manager.getPluginCondition());
            
            // Add user section if it has conditions
            if (manager.getUserLogicalCondition() != null && 
                !manager.getUserLogicalCondition().getConditions().isEmpty()) {
                
                DefaultMutableTreeNode userNode = new DefaultMutableTreeNode("User Conditions");
                rootNode.add(userNode);
                buildConditionTree(userNode, manager.getUserLogicalCondition());
            }
        } 
        // Otherwise just build from the root logical or flat conditions
        else if (manager.getUserLogicalCondition() != null) {
            LogicalCondition rootLogical = manager.getUserLogicalCondition();
            
            // For the root logical, show its children directly if it matches the selected type
            boolean isSelectedTypeAndRoot = 
                (rootLogical instanceof AndCondition && logicComboBox.getSelectedIndex() == 0) ||
                (rootLogical instanceof OrCondition && logicComboBox.getSelectedIndex() == 1);
            
            if (isSelectedTypeAndRoot) {
                for (Condition child : rootLogical.getConditions()) {
                    buildConditionTree(rootNode, child);
                }
            } else {
                buildConditionTree(rootNode, rootLogical);
            }
        }
        else {
            // This handles the case where we have flat conditions without logical structure
            for (Condition condition : getCurrentConditions()) {
                buildConditionTree(rootNode, condition);
            }
        }
        
        // Update tree model
        treeModel.nodeStructureChanged(rootNode);
        
        // First expand all nodes that were previously expanded
        for (int i = 0; i < conditionTree.getRowCount(); i++) {
            TreePath path = conditionTree.getPathForRow(i);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            
            if (node.getUserObject() instanceof Condition) {
                Condition condition = (Condition) node.getUserObject();
                if (expandedConditions.contains(condition)) {
                    conditionTree.expandPath(path);
                }
            } else if (node.getUserObject() instanceof String) {
                // Always expand category headers
                conditionTree.expandPath(path);
            }
        }
        
        // Then restore selection
        List<TreePath> pathsToSelect = new ArrayList<>();
        for (int i = 0; i < conditionTree.getRowCount(); i++) {
            TreePath path = conditionTree.getPathForRow(i);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            
            if (node.getUserObject() instanceof Condition) {
                Condition condition = (Condition) node.getUserObject();
                if (selectedConditions.contains(condition)) {
                    pathsToSelect.add(path);
                }
            }
        }
        
        if (!pathsToSelect.isEmpty()) {
            conditionTree.setSelectionPaths(pathsToSelect.toArray(new TreePath[0]));
        }
    }
    /**
     * Recursively searches for a tree node containing the specified condition
     */
    private DefaultMutableTreeNode findTreeNodeForCondition(DefaultMutableTreeNode parent, Condition target) {
        // Check if this node contains our target
        if (parent.getUserObject() == target) {
            return parent;
        }
        
        // Check all children
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            DefaultMutableTreeNode result = findTreeNodeForCondition(child, target);
            if (result != null) {
                return result;
            }
        }
        
        return null;
    }
    private void buildConditionTree(DefaultMutableTreeNode parent, Condition condition) {
        if (condition instanceof AndCondition) {
            AndCondition andCondition = (AndCondition) condition;
            DefaultMutableTreeNode andNode = new DefaultMutableTreeNode(andCondition);
            parent.add(andNode);
            
            for (Condition child : andCondition.getConditions()) {
                buildConditionTree(andNode, child);
            }
        } else if (condition instanceof OrCondition) {
            OrCondition orCondition = (OrCondition) condition;
            DefaultMutableTreeNode orNode = new DefaultMutableTreeNode(orCondition);
            parent.add(orNode);
            
            for (Condition child : orCondition.getConditions()) {
                buildConditionTree(orNode, child);
            }
        } else if (condition instanceof NotCondition) {
            NotCondition notCondition = (NotCondition) condition;
            DefaultMutableTreeNode notNode = new DefaultMutableTreeNode(notCondition);
            parent.add(notNode);
            
            buildConditionTree(notNode, notCondition.getCondition());
        } else {
            // Add leaf condition
            parent.add(new DefaultMutableTreeNode(condition));
        }
    }
   
    private void groupSelectedWithLogical(LogicalCondition logicalCondition) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) conditionTree.getLastSelectedPathComponent();
        if (selectedNode == null || selectedNode == rootNode) {
            return;
        }
        
        // Get the parent node
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
        if (parentNode == null) {
            return;
        }
        
        // Get the selected condition
        Object userObject = selectedNode.getUserObject();
        if (!(userObject instanceof Condition)) {
            return;
        }
        
        Condition condition = (Condition) userObject;
        
        // Remove the condition from its current position
        int index = getCurrentConditions().indexOf(condition);
        if (index >= 0) {
            getCurrentConditions().remove(index);
            conditionListModel.remove(index);
        }
        
        // Add it to the new logical condition
        logicalCondition.addCondition(condition);
        
        // Add the logical condition to the list
        getCurrentConditions().add(logicalCondition);
        conditionListModel.addElement(logicalCondition.getDescription());
        
        updateTreeFromConditions();
        notifyConditionUpdate();
    }
    private void negateSelected() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) conditionTree.getLastSelectedPathComponent();
        if (selectedNode == null || selectedNode == rootNode) {
            return;
        }
        
        // Get the selected condition
        Object userObject = selectedNode.getUserObject();
        if (!(userObject instanceof Condition)) {
            return;
        }
        
        Condition condition = (Condition) userObject;
        
        // Remove the condition from its current position
        int index = getCurrentConditions().indexOf(condition);
        if (index >= 0) {
            getCurrentConditions().remove(index);
            conditionListModel.remove(index);
        }
        
        // Create a NOT condition
        NotCondition notCondition = new NotCondition(condition);
        
        // Add the NOT condition to the list
        getCurrentConditions().add(notCondition);
        conditionListModel.addElement(notCondition.getDescription());
        
        updateTreeFromConditions();
        notifyConditionUpdate();
    }
    /**
     * Removes the selected condition, properly handling nested logic conditions
     */
    private void removeSelectedFromTree() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) conditionTree.getLastSelectedPathComponent();
        if (selectedNode == null || selectedNode == rootNode) {
            return;
        }
        
        // Get the selected condition
        Object userObject = selectedNode.getUserObject();
        if (!(userObject instanceof Condition)) {
            return;
        }
        
        Condition condition = (Condition) userObject;
        
        // Check if this is a plugin-defined condition that shouldn't be removed
        if (selectScheduledPlugin != null && 
            selectScheduledPlugin.getStopConditionManager().isPluginDefinedCondition(condition)) {
            JOptionPane.showMessageDialog(this,
                    "This condition is defined by the plugin and cannot be removed.",
                    "Plugin Condition",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Get parent logical condition if any
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
        if (parentNode != rootNode && parentNode.getUserObject() instanceof LogicalCondition) {
            LogicalCondition parentLogical = (LogicalCondition) parentNode.getUserObject();
            parentLogical.removeCondition(condition);
            
            // If logical condition is now empty and not the root, remove it too
            if (parentLogical.getConditions().isEmpty() && 
                parentNode.getParent() != rootNode && 
                parentNode.getParent() instanceof DefaultMutableTreeNode) {
                
                DefaultMutableTreeNode grandparentNode = (DefaultMutableTreeNode) parentNode.getParent();
                if (grandparentNode.getUserObject() instanceof LogicalCondition) {
                    LogicalCondition grandparentLogical = (LogicalCondition) grandparentNode.getUserObject();
                    grandparentLogical.removeCondition(parentLogical);
                }
            }
        } else {
            // Direct removal from condition manager
            selectScheduledPlugin.getStopConditionManager().removeCondition(condition);
        }
        
        updateTreeFromConditions();
        notifyConditionUpdate();
    }
        /**
     * Selects a tree node corresponding to the condition, preserving expansion state
     */
    private void selectNodeForCondition(Condition condition) {
        if (condition == null) {
            log.debug("selectNodeForCondition: Cannot select null condition");
            return;
        }
        
        log.debug("selectNodeForCondition: Attempting to select condition: {}", condition.getDescription());
        
        // Store current expansion state
        Set<TreePath> expandedPaths = new HashSet<>();
        Enumeration<TreePath> expanded = conditionTree.getExpandedDescendants(new TreePath(rootNode.getPath()));
        if (expanded != null) {
            while (expanded.hasMoreElements()) {
                expandedPaths.add(expanded.nextElement());
            }
            log.debug("selectNodeForCondition: Saved {} expanded paths", expandedPaths.size());
        } else {
            log.debug("selectNodeForCondition: No expanded paths to save");
        }
        
        // Find the node corresponding to the condition
        DefaultMutableTreeNode targetNode = null;
        Enumeration<TreeNode> e = rootNode.breadthFirstEnumeration();
        while (e.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
            if (node.getUserObject() == condition) {
                targetNode = node;
                break;
            }
        }
        
        if (targetNode != null) {
            log.debug("selectNodeForCondition: Found node for condition: {}", condition.getDescription());
            TreePath path = new TreePath(targetNode.getPath());
            
            // Make all parent nodes visible - expanding the path as needed
            TreePath parentPath = path.getParentPath();
            if (parentPath != null) {
                log.debug("selectNodeForCondition: Expanding parent path");
                conditionTree.expandPath(parentPath);
            }
            
            // Set selection
            log.debug("selectNodeForCondition: Setting selection path");
            conditionTree.setSelectionPath(path);
            
            // Ensure the selected node is visible
            log.debug("selectNodeForCondition: Scrolling to make path visible");
            conditionTree.scrollPathToVisible(path);
            
            // Restore previously expanded paths
            log.debug("selectNodeForCondition: Restoring {} expanded paths", expandedPaths.size());
            for (TreePath expandedPath : expandedPaths) {
                conditionTree.expandPath(expandedPath);
            }
        } else {
            log.debug("selectNodeForCondition: Could not find node for condition: {}", condition.getDescription());
        }
    }
    
  
    
    public boolean isRequireAll() {
        return logicComboBox.getSelectedIndex() == 0;
    }
    private void initializeSaveButton() {
        saveButton = createButton("Save Conditions", ColorScheme.PROGRESS_COMPLETE_COLOR);
        saveButton.addActionListener(e -> saveConditionsToScheduledPlugin());
        saveButton.setEnabled(selectScheduledPlugin != null);
    }
    private void initializeLoadButton() {
        loadButton = createButton("Load Current Conditions", ColorScheme.PROGRESS_COMPLETE_COLOR);
        loadButton.addActionListener(e -> setSelectScheduledPlugin(selectScheduledPlugin));
        loadButton.setEnabled(selectScheduledPlugin != null);
    }
    private void initializeResetButton() {
        resetButton = createButton("Reset Conditions", ColorScheme.PROGRESS_ERROR_COLOR);

        resetButton.addActionListener(e -> {
                    loadConditions(new ArrayList<>(), true);
                    saveConditionsToScheduledPlugin();  
                }
            );
        
    }
    
    private void saveConditionsToScheduledPlugin() {
        if (selectScheduledPlugin == null) return;
        
        // Set condition manager properties based on UI selection
        if (isRequireAll()) {
            selectScheduledPlugin.getStopConditionManager().setRequireAll();
        } else {
            selectScheduledPlugin.getStopConditionManager().setRequireAny();
        }
        
        // Save to config
        
        schedulerPlugin.saveScheduledPlugins();
        setScheduledPluginNameLabel(); // Update label
    }
    public void setScheduledPluginNameLabel() {
        String pluginName = selectScheduledPlugin != null ? selectScheduledPlugin.getName() : "None";
        titleLabel.setText("Stop Conditions for: " + pluginName);
    }
    /**
     * Notifies any external components of condition changes
     * and ensures changes are saved to the config.
     */
    private void notifyConditionUpdate() {
        if (selectScheduledPlugin == null) {
            return;
        }
        // Call any registered callback (if implementing callback pattern)
        if (userConditionUpdateCallback != null) {
            userConditionUpdateCallback.accept( selectScheduledPlugin.getStopConditionManager().getUserLogicalCondition());
        }
        
        // Save changes to the plugin configuration
        schedulerPlugin.saveScheduledPlugins();
    }
    /**
     * Refreshes the condition list and tree if conditions have changed in the selected plugin.
     * This should be called periodically to keep the UI in sync with the plugin state.
     * 
     * @return true if conditions were refreshed, false if no changes were detected
     */
    public boolean refreshConditions() {
        if (selectScheduledPlugin == null) {
            return false;
        }
        
        refreshDisplay();
        return true;
    }
    private void addCurrentCondition() {
        String selectedType = (String) conditionTypeComboBox.getSelectedItem();
        
        if (stopConditionPanel) {
            switch (selectedType) {
                case "Time Duration":
                    addConditionToPlugin(createTimeCondition());
                    break;
                case "Skill Level":
                    addConditionToPlugin(createSkillLevelCondition());
                    break;
                case "Skill XP Goal":
                    addConditionToPlugin(createSkillXpCondition());
                    break;
                case "Item Collection":
                    List<LootItemCondition> lootItemConditions = createItemCondition();
                    if (lootItemConditions != null && !lootItemConditions.isEmpty()) {
                        for (LootItemCondition condition : lootItemConditions) {
                            addConditionToPlugin(condition);
                        }
                    }
                    break;
                case "Time Window":
                    addConditionToPlugin(createTimeWindowCondition());
                    break;
                case "Day of Week":
                    addConditionToPlugin(createDayOfWeekCondition());
                    break;
            }
        } else {
            switch (selectedType) {
                case "Time Window":
                    addConditionToPlugin(createTimeWindowCondition());
                    break;
                case "Day of Week":
                    addConditionToPlugin(createDayOfWeekCondition());
                    break;
                case "Skill Level Required":
                    addConditionToPlugin(createSkillLevelCondition());
                    break;
                case "Item Required":
                    List<LootItemCondition> lootItemConditions = createItemCondition();
                    if (lootItemConditions != null && !lootItemConditions.isEmpty()) {
                        for (LootItemCondition condition : lootItemConditions) {
                            addConditionToPlugin(condition);
                        }
                    }
                    break;
            }
        }
    }
    
    /**
     * Finds the logical condition that should be the target for adding a new condition
     * based on the current tree selection
     */
    private LogicalCondition findTargetLogicalForAddition() {
        if (selectScheduledPlugin == null) {
            return null;
        }
        
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) conditionTree.getLastSelectedPathComponent();
        if (selectedNode == null) {
            // No selection, use root logical
            return selectScheduledPlugin.getStopConditionManager().getUserLogicalCondition();
        }
        
        Object userObject = selectedNode.getUserObject();
        
        // If selected node is a logical condition, use it directly
        if (userObject instanceof LogicalCondition) {
            // Check if this is a plugin-defined condition
            if (selectScheduledPlugin.getStopConditionManager().isPluginDefinedCondition((LogicalCondition)userObject)) {               
                return selectScheduledPlugin.getStopConditionManager().getUserLogicalCondition();
            }
            return (LogicalCondition) userObject;
        }
        
        // If selected node is a regular condition, find its parent logical
        if (userObject instanceof Condition && 
            selectedNode.getParent() != null && 
            selectedNode.getParent() != rootNode) {
            
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
            if (parentNode.getUserObject() instanceof LogicalCondition) {
                // Check if this is a plugin-defined condition
                
                if (selectScheduledPlugin.getStopConditionManager().isPluginDefinedCondition((LogicalCondition)parentNode.getUserObject())) {               
                    return selectScheduledPlugin.getStopConditionManager().getUserLogicalCondition();
                }
                return (LogicalCondition) parentNode.getUserObject();
            }
        }
        
        // Default to user logical condition
        return selectScheduledPlugin.getStopConditionManager().getUserLogicalCondition();
    }

    /**
     * Adds a condition to the appropriate logical structure based on selection
     */
    private void addConditionToPlugin(Condition condition) {
        if (selectScheduledPlugin == null || condition == null) {
            return;
        }
        
        ConditionManager manager = selectScheduledPlugin.getStopConditionManager();        
        // Find target logical condition based on selection
        LogicalCondition targetLogical = findTargetLogicalForAddition();        
        // Add the condition
        manager.addCondition(condition, targetLogical);            
        // Update UI
        updateTreeFromConditions();        
        schedulerPlugin.saveScheduledPlugins();        
        notifyConditionUpdate();        
        // Select the newly added condition
        selectNodeForCondition(condition);
    }

    /**
     * Removes the selected condition from the logical structure
     */
    private void removeSelectedCondition() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) conditionTree.getLastSelectedPathComponent();
        if (selectedNode == null || selectedNode == rootNode) {
            return;
        }
        
        Object userObject = selectedNode.getUserObject();
        if (!(userObject instanceof Condition)) {
            return;
        }
        
        Condition condition = (Condition) userObject;
        ConditionManager manager = selectScheduledPlugin.getStopConditionManager();
        
        // Check if this is a plugin-defined condition
        if (manager.isPluginDefinedCondition(condition)) {
            JOptionPane.showMessageDialog(this,
                    "This condition is defined by the plugin and cannot be removed.",
                    "Plugin Condition",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Remove the condition from its logical structure
        boolean removed = manager.removeCondition(condition);
        
        if (!removed) {
            log.warn("Failed to remove condition: {}", condition.getDescription());
        }
        
        // Update UI
        updateTreeFromConditions();
        schedulerPlugin.saveScheduledPlugins();
        notifyConditionUpdate();
    }

   

    /**
     * Finds the common parent logical condition for a set of tree nodes
     */
    private LogicalCondition findCommonParent(DefaultMutableTreeNode[] nodes) {
        if (nodes.length == 0) {
            return null;
        }
        
        // Get the parent of the first node
        DefaultMutableTreeNode firstParent = (DefaultMutableTreeNode) nodes[0].getParent();
        if (firstParent == null || firstParent == rootNode) {
            return selectScheduledPlugin.getStopConditionManager().getUserLogicalCondition();
        }
        
        if (!(firstParent.getUserObject() instanceof LogicalCondition)) {
            return null;
        }
        
        LogicalCondition parentLogical = (LogicalCondition) firstParent.getUserObject();
        
        // Check if all nodes have the same parent
        for (int i = 1; i < nodes.length; i++) {
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) nodes[i].getParent();
            if (parent != firstParent) {
                return null;
            }
        }
        
        return parentLogical;
    }

    /**
     * Negates the selected condition
     */
    private void negateSelectedCondition() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) conditionTree.getLastSelectedPathComponent();
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof Condition)) {
            return;
        }
        
        Condition selectedCondition = (Condition) selectedNode.getUserObject();
        ConditionManager manager = selectScheduledPlugin.getStopConditionManager();
        
        // Can't negate plugin conditions
        if (manager.isPluginDefinedCondition(selectedCondition)) {
            JOptionPane.showMessageDialog(this,
                    "Plugin-defined conditions cannot be negated",
                    "Operation Not Allowed",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Find parent logical condition
        LogicalCondition parentLogical = manager.findContainingLogical(selectedCondition);
        if (parentLogical == null) {
            JOptionPane.showMessageDialog(this,
                    "Could not determine which logical group contains this condition",
                    "Operation Failed",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // If condition is already a NOT, unwrap it
        if (selectedCondition instanceof NotCondition) {
            NotCondition notCondition = (NotCondition) selectedCondition;
            Condition innerCondition = notCondition.getCondition();
            
            // Replace NOT with its inner condition
            int index = parentLogical.getConditions().indexOf(selectedCondition);
            if (index >= 0) {
                parentLogical.getConditions().remove(index);
                parentLogical.addConditionAt(index, innerCondition);
            }
        } 
        // Otherwise, wrap it in a NOT
        else {
            // Create NOT condition
            NotCondition notCondition = new NotCondition(selectedCondition);
            
            // Replace original with NOT version
            int index = parentLogical.getConditions().indexOf(selectedCondition);
            if (index >= 0) {
                parentLogical.getConditions().remove(index);
                parentLogical.addConditionAt(index, notCondition);
            }
        }
        
        // Update UI
        updateTreeFromConditions();
        schedulerPlugin.saveScheduledPlugins();
        notifyConditionUpdate();
    }

    /**
     * Initializes the logical operations toolbar
     */
    private void initializeLogicalOperationsToolbar(JPanel configPanel) {
     
        JPanel logicalOpPanel = new JPanel();
        logicalOpPanel.setLayout(new BoxLayout(logicalOpPanel, BoxLayout.X_AXIS));
        logicalOpPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        logicalOpPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Group operations section
        JButton createAndButton = createButton("Group as AND", ColorScheme.BRAND_ORANGE);
        createAndButton.setToolTipText("Group selected conditions with AND logic");
        createAndButton.addActionListener(e -> createLogicalGroup(true));
        
        JButton createOrButton = createButton("Group as OR", BRAND_BLUE);
        createOrButton.setToolTipText("Group selected conditions with OR logic");
        createOrButton.addActionListener(e -> createLogicalGroup(false));
        
        // Negation button
        JButton negateButton = createButton("Negate", new Color(220, 50, 50));
        negateButton.setToolTipText("Negate the selected condition (toggle NOT)");
        negateButton.addActionListener(e -> negateSelectedCondition());
        
        // Convert operation buttons
        JButton convertToAndButton = createButton("Convert to AND", ColorScheme.BRAND_ORANGE);
        convertToAndButton.setToolTipText("Convert selected logical group to AND type");
        convertToAndButton.addActionListener(e -> convertLogicalType(true));
        
        JButton convertToOrButton = createButton("Convert to OR", BRAND_BLUE);
        convertToOrButton.setToolTipText("Convert selected logical group to OR type");
        convertToOrButton.addActionListener(e -> convertLogicalType(false));
        
        // Ungroup button
        JButton ungroupButton = createButton("Ungroup", ColorScheme.LIGHT_GRAY_COLOR);
        ungroupButton.setToolTipText("Remove the logical group but keep its conditions");
        ungroupButton.addActionListener(e -> ungroupSelectedLogical());
        
        // Add buttons to panel with separators
        logicalOpPanel.add(createAndButton);
        logicalOpPanel.add(Box.createHorizontalStrut(5));
        logicalOpPanel.add(createOrButton);
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(new JSeparator(SwingConstants.VERTICAL));
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(negateButton);
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(new JSeparator(SwingConstants.VERTICAL));
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(convertToAndButton);
        logicalOpPanel.add(Box.createHorizontalStrut(5));
        logicalOpPanel.add(convertToOrButton);
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(new JSeparator(SwingConstants.VERTICAL));
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(ungroupButton);
        
        // Store references to buttons that need context-sensitive enabling
        this.negateButton = negateButton;
        this.convertToAndButton = convertToAndButton;
        this.convertToOrButton = convertToOrButton;
        this.ungroupButton = ungroupButton;
        
        // Add selection listener to enable/disable buttons based on context
        conditionTree.addTreeSelectionListener(e -> updateLogicalButtonStates());
        
        // Initial state
        updateLogicalButtonStates();
        if (configPanel == null) {                    
            add(logicalOpPanel, BorderLayout.NORTH);
        }else {
            configPanel.add(logicalOpPanel, BorderLayout.NORTH);
        }
        

    }

    /**
     * Updates button states based on current selection
     */
    private void updateLogicalButtonStates() {
        DefaultMutableTreeNode[] selectedNodes = getSelectedConditionNodes();
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) conditionTree.getLastSelectedPathComponent();
        
        // Disable all buttons by default
        negateButton.setEnabled(false);
        convertToAndButton.setEnabled(false);
        convertToOrButton.setEnabled(false);
        ungroupButton.setEnabled(false);
        
        // If nothing selected, we're done
        if (selectedNode == null) {
            return;
        }
        
        // Special handling for logical groups
        if (selectedNode.getUserObject() instanceof LogicalCondition) {
            LogicalCondition logical = (LogicalCondition) selectedNode.getUserObject();
            
            // Enable convert buttons based on current type
            boolean isAnd = logical instanceof AndCondition;
            convertToAndButton.setEnabled(!isAnd);
            convertToOrButton.setEnabled(isAnd);
            
            // Enable ungroup button if this isn't the root logical
            ungroupButton.setEnabled(selectedNode.getParent() != rootNode);
            
            return;
        }
        
        // Enable negate button for regular conditions (not logical groups)
        if (selectedNode.getUserObject() instanceof Condition && 
            !(selectedNode.getUserObject() instanceof LogicalCondition)) {
            
            // But not for plugin-defined conditions
            Condition condition = (Condition) selectedNode.getUserObject();
            boolean isPluginDefined = selectScheduledPlugin.getStopConditionManager()
                                      .isPluginDefinedCondition(condition);
            
            negateButton.setEnabled(!isPluginDefined);
        }
    }
    
    /**
     * Initializes the condition tree with multi-selection support
     */
    private void initializeConditionTree() {
        rootNode = new DefaultMutableTreeNode("Conditions");
        treeModel = new DefaultTreeModel(rootNode);
        conditionTree = new JTree(treeModel);
        conditionTree.setRootVisible(false);
        conditionTree.setShowsRootHandles(true);
        this.conditionTreeCellRenderer = new ConditionTreeCellRenderer(schedulerPlugin);
        conditionTree.setCellRenderer(this.conditionTreeCellRenderer);
        
        conditionTree.getSelectionModel().setSelectionMode(
            TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION); // Enable multi-select
        
        // Add popup menu for right-click operations
        JPopupMenu popupMenu = createTreePopupMenu();
        conditionTree.setComponentPopupMenu(popupMenu);
        
        JScrollPane treeScrollPane = new JScrollPane(conditionTree);
        treeScrollPane.setPreferredSize(new Dimension(400, 300));
        
        add(treeScrollPane, BorderLayout.CENTER);
    }

    /**
     * Creates a popup menu for the condition tree
     */
    private JPopupMenu createTreePopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        
        // Negate option
        JMenuItem negateItem = new JMenuItem("Negate");
        negateItem.addActionListener(e -> negateSelectedCondition());
        
        // Group options
        JMenuItem groupAndItem = new JMenuItem("Group as AND");
        groupAndItem.addActionListener(e -> createLogicalGroup(true));
        
        JMenuItem groupOrItem = new JMenuItem("Group as OR");
        groupOrItem.addActionListener(e -> createLogicalGroup(false));
        
        // Convert options
        JMenuItem convertToAndItem = new JMenuItem("Convert to AND");
        convertToAndItem.addActionListener(e -> convertLogicalType(true));
        
        JMenuItem convertToOrItem = new JMenuItem("Convert to OR");
        convertToOrItem.addActionListener(e -> convertLogicalType(false));
        
        // Ungroup option
        JMenuItem ungroupItem = new JMenuItem("Ungroup");
        ungroupItem.addActionListener(e -> ungroupSelectedLogical());
        
        // Remove option
        JMenuItem removeItem = new JMenuItem("Remove");
        removeItem.addActionListener(e -> removeSelectedCondition());
        
        // Add all items
        menu.add(negateItem);
        menu.addSeparator();
        menu.add(groupAndItem);
        menu.add(groupOrItem);
        menu.addSeparator();
        menu.add(convertToAndItem);
        menu.add(convertToOrItem);
        menu.add(ungroupItem);
        menu.addSeparator();
        menu.add(removeItem);
        
        // Add popup listener to control enabled state of menu items
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                DefaultMutableTreeNode[] selectedNodes = getSelectedConditionNodes();
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) conditionTree.getLastSelectedPathComponent();
                boolean isLogical = selectedNode != null && selectedNode.getUserObject() instanceof LogicalCondition;
                boolean isAnd = isLogical && selectedNode.getUserObject() instanceof AndCondition;
                boolean isPluginDefined = selectedNode != null && 
                                         selectedNode.getUserObject() instanceof Condition &&
                                         selectScheduledPlugin.getStopConditionManager()
                                            .isPluginDefinedCondition((Condition)selectedNode.getUserObject());
                
                // Enable/disable items based on context
                negateItem.setEnabled(selectedNode != null && !isLogical && !isPluginDefined);
                groupAndItem.setEnabled(selectedNodes.length >= 2);
                groupOrItem.setEnabled(selectedNodes.length >= 2);
                convertToAndItem.setEnabled(isLogical && !isAnd);
                convertToOrItem.setEnabled(isLogical && isAnd);
                ungroupItem.setEnabled(isLogical && selectedNode.getParent() != rootNode);
                removeItem.setEnabled(selectedNode != null && !isPluginDefined);
            }
            
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
            
            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {}
        });
        
        return menu;
    }

    /**
     * Gets an array of tree nodes representing the selected conditions
     */
    private DefaultMutableTreeNode[] getSelectedConditionNodes() {
        TreePath[] paths = conditionTree.getSelectionPaths();
        if (paths == null) {
            return new DefaultMutableTreeNode[0];
        }
        
        List<DefaultMutableTreeNode> nodes = new ArrayList<>();
        for (TreePath path : paths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node != rootNode && node.getUserObject() instanceof Condition) {
                nodes.add(node);
            }
        }
        
        return nodes.toArray(new DefaultMutableTreeNode[0]);
    }

    /**
     * Converts a logical group from one type to another (AND <-> OR)
     */
    private void convertLogicalType(boolean toAnd) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) conditionTree.getLastSelectedPathComponent();
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof LogicalCondition)) {
            return;
        }
        
        LogicalCondition oldLogical = (LogicalCondition) selectedNode.getUserObject();
        
        // Skip if already the desired type
        if ((toAnd && oldLogical instanceof AndCondition) || 
            (!toAnd && oldLogical instanceof OrCondition)) {
            return;
        }
        
        // Create new logical of the desired type
        LogicalCondition newLogical = toAnd ? new AndCondition() : new OrCondition();
        
        // Transfer all conditions to the new logical
        for (Condition condition : new ArrayList<>(oldLogical.getConditions())) {
            newLogical.addCondition(condition);
        }
        
        // Find parent logical
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
        if (parentNode == rootNode) {
            // This is the root logical - replace in condition manager
            selectScheduledPlugin.getStopConditionManager().setUserLogicalCondition(newLogical);
        } else if (parentNode.getUserObject() instanceof LogicalCondition) {
            // Replace in parent
            LogicalCondition parentLogical = (LogicalCondition) parentNode.getUserObject();
            int index = parentLogical.getConditions().indexOf(oldLogical);
            if (index >= 0) {
                parentLogical.getConditions().remove(index);
                parentLogical.addConditionAt(index, newLogical);
            }
        }
        
        // Update UI
        updateTreeFromConditions();
        selectNodeForCondition(newLogical);
        schedulerPlugin.saveScheduledPlugins();
        notifyConditionUpdate();
    }

    /**
     * Removes a logical group but keeps its conditions
     */
    private void ungroupSelectedLogical() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) conditionTree.getLastSelectedPathComponent();
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof LogicalCondition)) {
            return;
        }
        
        // Don't allow ungrouping the root logical
        if (selectedNode.getParent() == rootNode) {
            JOptionPane.showMessageDialog(this,
                    "Cannot ungroup the root logical condition.",
                    "Operation Not Allowed",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        LogicalCondition logicalToUngroup = (LogicalCondition) selectedNode.getUserObject();
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
        
        // Only proceed if parent is a logical condition
        if (!(parentNode.getUserObject() instanceof LogicalCondition)) {
            return;
        }
        
        LogicalCondition parentLogical = (LogicalCondition) parentNode.getUserObject();
        
        // Find position in parent
        int index = parentLogical.getConditions().indexOf(logicalToUngroup);
        if (index < 0) {
            return;
        }
        
        // Remove the logical from its parent
        parentLogical.getConditions().remove(index);
        
        // Add all of its conditions to the parent at the same position
        int currentIndex = index;
        for (Condition condition : new ArrayList<>(logicalToUngroup.getConditions())) {
            parentLogical.addConditionAt(currentIndex++, condition);
        }
        
        // Update UI
        updateTreeFromConditions();
        schedulerPlugin.saveScheduledPlugins();
        notifyConditionUpdate();
    }

    /**
     * Creates a new logical group from the selected conditions
     */
    private void createLogicalGroup(boolean isAnd) {
        DefaultMutableTreeNode[] selectedNodes = getSelectedConditionNodes();
        if (selectedNodes.length < 2) {
            JOptionPane.showMessageDialog(this,
                    "Please select at least two conditions to group",
                    "Selection Required",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // Verify all nodes have the same parent
        DefaultMutableTreeNode firstParent = (DefaultMutableTreeNode) selectedNodes[0].getParent();
        for (int i = 1; i < selectedNodes.length; i++) {
            if (selectedNodes[i].getParent() != firstParent) {
                JOptionPane.showMessageDialog(this,
                        "All conditions must have the same parent to group them",
                        "Invalid Selection",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        
        // Create new logical condition
        LogicalCondition newLogical = isAnd ? new AndCondition() : new OrCondition();
        
        // Determine parent logical
        LogicalCondition parentLogical;
        if (firstParent == rootNode) {
            parentLogical = selectScheduledPlugin.getStopConditionManager().getUserLogicalCondition();
        } else if (firstParent.getUserObject() instanceof LogicalCondition) {
            parentLogical = (LogicalCondition) firstParent.getUserObject();
        } else {
            JOptionPane.showMessageDialog(this,
                    "Cannot determine parent logical group",
                    "Operation Failed",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Collect all selected conditions
        List<Condition> conditionsToGroup = new ArrayList<>();
        for (DefaultMutableTreeNode node : selectedNodes) {
            if (node.getUserObject() instanceof Condition) {
                conditionsToGroup.add((Condition) node.getUserObject());
            }
        }
        
        // Remove conditions from parent
        for (Condition condition : conditionsToGroup) {
            parentLogical.getConditions().remove(condition);
        }
        
        // Add conditions to new logical
        for (Condition condition : conditionsToGroup) {
            newLogical.addCondition(condition);
        }
        
        // Add new logical to parent
        parentLogical.addCondition(newLogical);
        
        // Update UI
        updateTreeFromConditions();
        selectNodeForCondition(newLogical);
        schedulerPlugin.saveScheduledPlugins();
        notifyConditionUpdate();
    }
}