package net.runelite.client.plugins.microbot.pluginscheduler.ui.PluginScheduleEntry;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;

import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.awt.font.TextAttribute;

@Slf4j
public class ScheduleTablePanel extends JPanel implements ScheduleTableModel {
    private final SchedulerPlugin schedulerPlugin;
    private final JTable scheduleTable;
    private final DefaultTableModel tableModel;
    private Consumer<PluginScheduleEntry> selectionListener;
    private boolean updatingTable = false;
    
    // Colors for different row states with improved visibility
    private static final Color CURRENT_PLUGIN_COLOR = new Color(138, 43, 226, 80); // Purple with transparency
    private static final Color NEXT_PLUGIN_COLOR = new Color(255, 193, 7, 60); // Amber with transparency
    private static final Color SELECTION_COLOR = new Color(0, 120, 215, 150); // Blue with transparency
    private static final Color CONDITION_MET_COLOR = new Color(76, 175, 80, 70); // Green with transparency
    private static final Color CONDITION_NOT_MET_COLOR = new Color(244, 67, 54, 70); // Red with transparency
    private static final Color DEFAULT_PLUGIN_COLOR = new Color(0, 150, 136, 40); // Teal with transparency
    
    // Column indices for easy reference
    private static final int COL_NAME = 0;
    private static final int COL_SCHEDULE = 1;
    private static final int COL_NEXT_RUN = 2;
    private static final int COL_START_COND = 3;
    private static final int COL_STOP_COND = 4;
    private static final int COL_PRIORITY = 5;
    private static final int COL_ENABLED = 6;
    private static final int COL_RANDOM = 7;
    private static final int COL_TIME_STOP = 8;
    private static final int COL_RUNS = 9;
    
    private List<PluginScheduleEntry> rowToPluginMap = new ArrayList<>();
    
    public int getRowCount() {
        if (tableModel == null) {
            return 0;
        }
        return tableModel.getRowCount();
    }

    public ScheduleTablePanel(SchedulerPlugin schedulerPlugin) {
        
        this.schedulerPlugin = schedulerPlugin;
        
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR),
                        BorderFactory.createEmptyBorder(5, 5, 5, 5)
                ),
                "Scheduled Plugins",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                FontManager.getRunescapeBoldFont()
        ));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Update the table model to focus on important columns
        // Removed less important columns to reduce clutter: Default, Random, Time Stop
        tableModel = new DefaultTableModel(
            new Object[]{"Plugin", "Schedule", "Next Run", "Start Conditions", "Stop Conditions", "Priority", "Enabled", "Runs"}, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 6) return Boolean.class; // Enabled column is boolean
                if (column == 5) return Integer.class; // Priority column as Integer
                if (column == 7) return Integer.class; // Run count as Integer
                return String.class;
            }
            @Override
            public boolean isCellEditable(int row, int column) {                
                // Only allow editing Priority and Enabled columns
                return column == 5 || column == 6;
            }
        };
        
        // Update the table model listener to handle Priority column changes
        tableModel.addTableModelListener(e -> {
            if (updatingTable) {                
                return; // Skip processing if we're already updating or it's not our columns
            }            
            if (e.getColumn() == 5 || e.getColumn() == 6) {                
                try {
                    updatingTable = true;
                    int firstRow = e.getFirstRow();
                    int lastRow = e.getLastRow();
                    
                    // Update all rows in the affected range
                    for (int row = firstRow; row <= lastRow; row++) {
                        if (row >= 0 && row < rowToPluginMap.size()) {
                            PluginScheduleEntry scheduled = rowToPluginMap.get(row);
                            
                            if (e.getColumn() == 5) { // Priority column
                                Integer priority = (Integer) tableModel.getValueAt(row, 5);
                                
                                // Check if this is a default plugin that should always have priority 0
                                if (scheduled.isDefault() && priority != 0) {
                                    tableModel.setValueAt(0, row, 5); // Reset to 0
                                    JOptionPane.showMessageDialog(
                                        this,
                                        "Default plugins must have priority 0.",
                                        "Invalid Priority",
                                        JOptionPane.INFORMATION_MESSAGE
                                    );
                                } else {
                                    // For non-default plugins, update the value
                                    //scheduled.setPriority(priority);
                                    // Set default flag based on priority
                                    //scheduled.setDefault(priority == 0);
                                    // Save changes
                                    //schedulerPlugin.saveScheduledPlugins();
                                }
                            }
                            else if (e.getColumn() == 6) { // Enabled column
                                Boolean enabled = (Boolean) tableModel.getValueAt(row, 6);
                                tableModel.setValueAt(enabled, row, 6);
                                
                                Microbot.getClientThread().invokeLater(() -> {
                                    // Update the enabled status of the plugin
                                    scheduled.setEnabled(enabled);
                                });
                            }
                        }
                    }
                    
                    // Save after all updates are done
                    schedulerPlugin.saveScheduledPlugins();
                    
                    // Refresh the table to update visual indicators
                    refreshTable();                
                } finally {
                    updatingTable = false;
                }
            }
        });

        // Create table with custom styling
        scheduleTable = new JTable(tableModel);
        scheduleTable.setFillsViewportHeight(true);
        scheduleTable.setRowHeight(30);
        scheduleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scheduleTable.setShowGrid(false);
        scheduleTable.setIntercellSpacing(new Dimension(0, 0));
        scheduleTable.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scheduleTable.setForeground(Color.WHITE);

        // Set the custom editor for the Priority column
        scheduleTable.getColumnModel().getColumn(5).setCellEditor(new PrioritySpinnerEditor());

        // Add mouse listener to handle clicks outside the table data
        scheduleTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = scheduleTable.rowAtPoint(e.getPoint());
                int col = scheduleTable.columnAtPoint(e.getPoint());

                // If clicked outside the table data area, clear selection
                if (row == -1 || col == -1) {
                    clearSelection();
                    return;
                }
                
                // Handle double-click on already selected row to deselect it
                if (e.getClickCount() == 2) {
                    int selectedRow = scheduleTable.getSelectedRow();
                    if (selectedRow == row) {
                        // Deselect the row
                        clearSelection();
                    }
                }
            }
        });

        // Style the table header
        JTableHeader header = scheduleTable.getTableHeader();
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setForeground(Color.WHITE);
        header.setFont(FontManager.getRunescapeBoldFont());
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.LIGHT_GRAY_COLOR));

        // Add mouse listener to header to clear selection when clicked
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clearSelection();
            }
        });

        // Set column widths
        scheduleTable.getColumnModel().getColumn(0).setPreferredWidth(180); // Plugin
        scheduleTable.getColumnModel().getColumn(1).setPreferredWidth(150); // Schedule
        scheduleTable.getColumnModel().getColumn(2).setPreferredWidth(150); // Next Run
        scheduleTable.getColumnModel().getColumn(3).setPreferredWidth(110); // Start Conditions
        scheduleTable.getColumnModel().getColumn(4).setPreferredWidth(110); // Stop Conditions
        scheduleTable.getColumnModel().getColumn(5).setPreferredWidth(70);  // Priority
        scheduleTable.getColumnModel().getColumn(6).setPreferredWidth(70);  // Enabled
        scheduleTable.getColumnModel().getColumn(7).setPreferredWidth(60);  // Run Count

        // Custom cell renderer for alternating row colors and special highlights
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);                
                if (row >= 0 && row < rowToPluginMap.size()) {
                    PluginScheduleEntry rowPlugin = rowToPluginMap.get(row);
                    
                    if (isSelected) {
                        // Selected row styling takes precedence - use a distinct blue color
                        c.setBackground(SELECTION_COLOR);
                        c.setForeground(Color.WHITE);
                    } 
                    else if (!rowPlugin.isEnabled()) {
                        // Disabled plugin styling
                        c.setBackground(row % 2 == 0 ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.DARK_GRAY_COLOR);
                        c.setForeground(Color.GRAY); // Gray text to indicate disabled
                        // Add strikethrough for disabled plugins
                        if (value instanceof String) {
                            Font originalFont = c.getFont();
                            Map<TextAttribute, Object> attributes = new HashMap<>();
                            attributes.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
                            attributes.put(TextAttribute.FONT, originalFont);
                            c.setFont(Font.getFont(attributes));
                        }
                    }
                    else if (rowPlugin.isRunning() && schedulerPlugin.getCurrentPlugin() != null && 
                            rowPlugin.getName().equals(schedulerPlugin.getCurrentPlugin().getName())) {
                        // Currently running plugin
                        c.setBackground(CURRENT_PLUGIN_COLOR);
                        c.setForeground(Color.WHITE);
                    }
                    else if (isNextToRun(rowPlugin)) {
                        // Next plugin to run
                        c.setBackground(NEXT_PLUGIN_COLOR);
                        c.setForeground(Color.BLACK);
                    }
                    else if (rowPlugin.isDefault()) {
                        // Default plugin styling
                        c.setBackground(DEFAULT_PLUGIN_COLOR);
                        c.setForeground(Color.WHITE);
                    }
                    else {
                        // Normal alternating row colors
                        c.setBackground(row % 2 == 0 ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.DARK_GRAY_COLOR);
                        c.setForeground(Color.WHITE);
                    }
                    
                    // Apply the condition renderer to the condition columns
                    // Custom cell renderer for the condition columns
                    if (row >= 0 && row < rowToPluginMap.size() && !isSelected) {
                        PluginScheduleEntry entry = rowToPluginMap.get(row);
                        
                        // Apply background color based on condition status
                        if (column == 3) { // Start conditions
                            boolean hasStartConditions = entry.hasAnyStartConditions();
                            boolean startConditionsMet = hasStartConditions && entry.getStartConditionManager().areConditionsMet();
                            
                            if (hasStartConditions) {
                                c.setBackground(startConditionsMet ? CONDITION_MET_COLOR : CONDITION_NOT_MET_COLOR);
                                c.setForeground(Color.BLACK);
                            }
                        } else if (column == 4) { // Stop conditions
                            boolean hasStopConditions = entry.hasAnyStopConditions();
                            boolean stopConditionsMet = hasStopConditions && entry.getStopConditionManager().areConditionsMet();
                            
                            if (hasStopConditions) {
                                c.setBackground(stopConditionsMet ? CONDITION_MET_COLOR : CONDITION_NOT_MET_COLOR);
                                c.setForeground(Color.BLACK);
                            }
                        }
                    }
                    
                    // Set tooltip based on column for better information
                    if (column == 0) { // Plugin name column
                        setToolTipText(getPluginDetailsTooltip(rowPlugin));
                    } else if (column == 1) { // Schedule
                        setToolTipText(getScheduleTooltip(rowPlugin));
                    } else if (column == 2) { // Next Run
                        setToolTipText(getNextRunTooltip(rowPlugin));
                    } else if (column == 3) { // Start Conditions
                        setToolTipText(getStartConditionsTooltip(rowPlugin));
                    } else if (column == 4) { // Stop Conditions
                        setToolTipText(getStopConditionsTooltip(rowPlugin));
                    } else if (column == 5) { // Priority
                        setToolTipText(getPriorityTooltip(rowPlugin));
                    }
                }
                setBorder(new EmptyBorder(2, 5, 2, 5));
                return c;
            }
        };

        renderer.setHorizontalAlignment(SwingConstants.LEFT);

        // Apply renderer to all columns except the boolean column
        for (int i = 0; i < scheduleTable.getColumnCount(); i++) {
            if (i != 6) { // Skip Enabled column which is a checkbox
                scheduleTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
            }
        }

        // Add table to scroll pane with custom styling
        JScrollPane scrollPane = new JScrollPane(scheduleTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Style the scrollbar
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Add mouse listener to the scroll pane to clear selection when clicking empty space
        scrollPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clearSelection();
            }
        });

        add(scrollPane, BorderLayout.CENTER);
        
        // Add an improved legend panel with more information
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        legendPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        legendPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // Current plugin indicator
        addLegendItem(legendPanel, CURRENT_PLUGIN_COLOR, "Running", "Currently running plugin");
        
        // Next plugin indicator
        addLegendItem(legendPanel, NEXT_PLUGIN_COLOR, "Next", "Plugin scheduled to run next");
        
        // Default plugin indicator
        addLegendItem(legendPanel, DEFAULT_PLUGIN_COLOR, "Default", "Default plugin (Priority 0)");
        
        // Condition indicators
        addLegendItem(legendPanel, CONDITION_MET_COLOR, "Condition Met", "Condition has been satisfied");
        addLegendItem(legendPanel, CONDITION_NOT_MET_COLOR, "Not Met", "Condition not yet satisfied");

        // Add the legend panel to the bottom of the main panel
        add(legendPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Helper method to add a legend item with consistent styling
     */
    private void addLegendItem(JPanel legendPanel, Color color, String text, String tooltip) {
        JPanel indicator = new JPanel();
        indicator.setBackground(color);
        indicator.setPreferredSize(new Dimension(15, 15));
        indicator.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        indicator.setToolTipText(tooltip);
        
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setToolTipText(tooltip);
        
        legendPanel.add(indicator);
        legendPanel.add(label);
    }
    
    /**
     * Creates a tooltip for the priority column
     */
    private String getPriorityTooltip(PluginScheduleEntry entry) {
        StringBuilder tooltip = new StringBuilder("<html><b>Priority Information</b><br>");
        
        if (entry.isDefault()) {
            tooltip.append("<br>This is a <b>default plugin</b> with priority 0.");
            tooltip.append("<br>Default plugins are always scheduled last.");
        } else {
            tooltip.append("<br>Priority: <b>").append(entry.getPriority()).append("</b>");
            tooltip.append("<br>Higher priority plugins run before lower priority plugins.");
        }
        
        tooltip.append("<br><br>To change a plugin to default status, set its priority to 0.");
        
        return tooltip.toString() + "</html>";
    }
    
    /**
     * Creates a detailed tooltip for schedule information
     */
    private String getScheduleTooltip(PluginScheduleEntry entry) {
        StringBuilder tooltip = new StringBuilder("<html><b>Schedule Details:</b><br>");
        
        // Add schedule type and details
        tooltip.append(entry.getIntervalDisplay());
        
        if (entry.hasAnyOneTimeStartConditions()) {
            tooltip.append("<br>One-time schedule");
            if (entry.hasTriggeredOneTimeStartConditions() && !entry.canStartTriggerAgain()) {
                tooltip.append(" (completed)");
            }
        }
        
        // Add priority information
        tooltip.append("<br><br><b>Priority:</b> ").append(entry.getPriority());
        if (entry.isDefault()) {
            tooltip.append(" (Default plugin)");
        }
        
        // Add random scheduling info
        tooltip.append("<br><b>Random Scheduling:</b> ").append(entry.isAllowRandomScheduling() ? "Enabled" : "Disabled");
        
        return tooltip.toString() + "</html>";
    }
    /**
     * Creates a detailed tooltip for start conditions with improved condition type display
     */
    private String getStartConditionsTooltip(PluginScheduleEntry entry) {
        if (!entry.hasStartConditions()) {
            return "No start conditions defined";
        }
        
        List<Condition> conditions = entry.getStartConditions();
        
        // Determine if start conditions are relevant - when plugin is enabled but not started
        boolean conditionsAreRelevant = entry.isEnabled() && !entry.isRunning();
        
        StringBuilder tooltip = new StringBuilder("<html><b>Start Conditions:</b><br>");
        
        // Group conditions by type
        Map<ConditionType, List<Condition>> grouped = groupConditionsByType(conditions);
        
        // If there are many conditions, add a summary first
        if (conditions.size() > 3) {
            tooltip.append("<i>").append(entry.getDetailedStartConditionsStatus()).append("</i>");
            tooltip.append("<br><br>");
        }
        
        if (grouped.isEmpty()) {
            tooltip.append("Will start as soon as enabled");
        } else {
            tooltip.append("<table>");
            for (Map.Entry<ConditionType, List<Condition>> group : grouped.entrySet()) {
                ConditionType type = group.getKey();
                List<Condition> typeConditions = group.getValue();
                
                // Add a header for this type
                tooltip.append("<tr><td colspan='2'><b>")
                       .append(getConditionTypeIcon(type))
                       .append(" ")
                       .append(formatConditionTypeName(type))
                       .append("</b></td></tr>");
                
                // Add each condition
                for (Condition condition : typeConditions) {
                    boolean isSatisfied = condition.isSatisfied();
                    tooltip.append("<tr>");
                    
                    // Status icon column
                    tooltip.append("<td>");
                    // Show either relevance icon or satisfaction status
                    if (conditionsAreRelevant) {
                        tooltip.append("<font color='gold'>⚡</font> ")
                               .append(getStatusSymbol(isSatisfied));
                    } else {
                        tooltip.append("<font color='gray'>○</font>");
                    }
                    tooltip.append("</td>");
                    
                    // Description column
                    tooltip.append("<td>");
                    String description = formatConditionDescription(condition);
                    if (isSatisfied) {
                        if (conditionsAreRelevant) {
                            tooltip.append("<font color='green'>").append(description).append("</font>");
                        } else {
                            tooltip.append("<font color='gray'>").append(description).append("</font>");
                        }
                    } else {
                        if (conditionsAreRelevant) {
                            tooltip.append("<font color='red'>").append(description).append("</font>");
                        } else {
                            tooltip.append("<font color='gray'>").append(description).append("</font>");
                        }
                    }
                    tooltip.append("</td>");
                    tooltip.append("</tr>");
                }
            }
            tooltip.append("</table>");
        }
        
        // Add overall status
        if (conditionsAreRelevant) {
            tooltip.append("<br><b>Status:</b> <font color='gold'>⚡ Currently relevant</font>");
            if (entry.areStartConditionsMet()) {
                tooltip.append("<br><font color='green'>All conditions met - plugin can start</font>");
            } else {
                tooltip.append("<br><font color='red'>Some conditions not met - waiting to start</font>");
            }
        } else {
            tooltip.append("<br><b>Status:</b> <font color='gray'>Not currently relevant</font>");
        }
        
        tooltip.append("</html>");
        return tooltip.toString();
    }

    /**
     * Creates a detailed tooltip for stop conditions with improved condition type display
     */
    private String getStopConditionsTooltip(PluginScheduleEntry entry) {
        if (!entry.hasStopConditions()) {
            return "No stop conditions defined";
        }
        
        List<Condition> conditions = entry.getStopConditions();
        
        // Determine if stop conditions are relevant - when plugin is currently running
        boolean conditionsAreRelevant = entry.isRunning();
        
        StringBuilder tooltip = new StringBuilder("<html><b>Stop Conditions:</b><br>");
        
        // Group conditions by type
        Map<ConditionType, List<Condition>> grouped = groupConditionsByType(conditions);
        
        // If there are many conditions, add a summary first
        if (conditions.size() > 3) {
            tooltip.append("<i>").append(entry.getDetailedStopConditionsStatus()).append("</i>");
            tooltip.append("<br><br>");
        }
        
        // Show overall progress if relevant and available
        if (conditionsAreRelevant) {
            double progress = entry.getStopConditionProgress();
            if (progress > 0) {
                tooltip.append("<b>Overall Progress: </b>");
                
                String progressColor = progress > 80 ? "green" : 
                                      progress > 50 ? "orange" : "blue";
                
                tooltip.append("<font color='")
                       .append(progressColor)
                       .append("'>")
                       .append(String.format("%.1f%%", progress))
                       .append("</font><br>");
            }
            tooltip.append("<br>");
        }
        
        if (grouped.isEmpty()) {
            tooltip.append("Will run until manually stopped");
        } else {
            tooltip.append("<table>");
            for (Map.Entry<ConditionType, List<Condition>> group : grouped.entrySet()) {
                ConditionType type = group.getKey();
                List<Condition> typeConditions = group.getValue();
                
                // Add a header for this type
                tooltip.append("<tr><td colspan='2'><b>")
                       .append(getConditionTypeIcon(type))
                       .append(" ")
                       .append(formatConditionTypeName(type))
                       .append("</b></td></tr>");
                
                // Add each condition
                for (Condition condition : typeConditions) {
                    boolean isSatisfied = condition.isSatisfied();
                    tooltip.append("<tr>");
                    
                    // Status icon column
                    tooltip.append("<td>");
                    // Show either relevance icon or satisfaction status
                    if (conditionsAreRelevant) {
                        tooltip.append("<font color='gold'>⚡</font> ")
                               .append(getStatusSymbol(isSatisfied));
                    } else {
                        tooltip.append("<font color='gray'>○</font>");
                    }
                    tooltip.append("</td>");
                    
                    // Description column
                    tooltip.append("<td>");
                    String description = formatConditionDescription(condition);
                    if (isSatisfied) {
                        if (conditionsAreRelevant) {
                            tooltip.append("<font color='green'>").append(description).append("</font>");
                        } else {
                            tooltip.append("<font color='gray'>").append(description).append("</font>");
                        }
                    } else {
                        if (conditionsAreRelevant) {
                            tooltip.append("<font color='red'>").append(description).append("</font>");
                        } else {
                            tooltip.append("<font color='gray'>").append(description).append("</font>");
                        }
                    }
                    tooltip.append("</td>");
                    tooltip.append("</tr>");
                }
            }
            tooltip.append("</table>");
        }
        
        // Add overall status
        if (conditionsAreRelevant) {
            tooltip.append("<br><b>Status:</b> <font color='gold'>⚡ Currently relevant</font>");
            if (entry.areStopConditionsMet()) {
                tooltip.append("<br><font color='green'>Stop conditions met - plugin will stop</font>");
            } else {
                tooltip.append("<br><font color='blue'>Waiting for stop conditions</font>");
                
                // If there's timing info available, show it
                String nextTrigger = entry.getNextStopTriggerTimeString();
                if (!nextTrigger.contains("None") && !nextTrigger.isEmpty()) {
                    tooltip.append("<br><font color='blue'>Next potential trigger: ")
                           .append(nextTrigger)
                           .append("</font>");
                }
            }
        } else {
            tooltip.append("<br><b>Status:</b> <font color='gray'>Not currently relevant</font>");
        }
        
        tooltip.append("</html>");
        return tooltip.toString();
    }
    
    /**
     * Groups conditions by their type for organized display
     */
    private Map<ConditionType, List<Condition>> groupConditionsByType(List<Condition> conditions) {
        Map<ConditionType, List<Condition>> groupedConditions = new HashMap<>();
        
        for (Condition condition : conditions) {
            ConditionType type = condition.getType();
            groupedConditions.computeIfAbsent(type, k -> new ArrayList<>()).add(condition);
        }
        
        return groupedConditions;
    }
    
    /**
     * Returns an appropriate icon for a condition type that will reliably display in tooltips
     */
    private String getConditionTypeIcon(ConditionType type) {
        // Using HTML entities and standard characters instead of Unicode emojis
        // as these render more reliably in Swing tooltips
        switch (type) {
            case TIME:
                return "&#9200;"; // Clock icon using HTML entity
            //case SKILL:
              //  return "&#128202;"; // Chart icon using HTML entity
            case SKILL:
                return "&#128200;"; // Chart with upward trend using HTML entity
            case RESOURCE:
                return "&#9635;"; // Square icon
            case LOCATION:
                return "&#9737;"; // Compass icon
            case LOGICAL:
                return "&#8635;"; // Recycling/Loop icon
            case NPC:
                return "&#9786;"; // Face/Person icon
            default:
                return "&#8226;"; // Bullet point
        }
    }
    
    /**
     * Returns a user-friendly name for a condition type
     */
    private String formatConditionTypeName(ConditionType type) {
        switch (type) {
            case TIME: return "Time Conditions";
            case SKILL: return "Skill Conditions";            
            case RESOURCE: return "Resource Conditions";
            case LOCATION: return "Location Conditions";
            case LOGICAL: return "Logical Conditions";
            case NPC: return "NPC Conditions";
            default: return type.toString() + " Conditions";
        }
    }
    
    /**
     * Returns a colored status symbol for condition status that will reliably display in tooltips
     */
    private String getStatusSymbol(boolean satisfied) {
        return satisfied ? 
            "<span style='color:#2ecc71'>&#10004;</span>" : // HTML entity for checkmark
            "<span style='color:#e74c3c'>&#10008;</span>";  // HTML entity for X mark
    }
    
    /**
     * Formats a condition description for better readability
     * Handles special cases like nested logical conditions
     */
    private String formatConditionDescription(Condition condition) {
        String description = condition.getDescription();
        
        // For logical conditions, use HTML formatting when available
        if (condition instanceof LogicalCondition) {
            LogicalCondition logicalCondition = (LogicalCondition) condition;
            // Get a truncated HTML description with reasonable length limit
            return logicalCondition.getHtmlDescription(80)
                     .replace("<html>", "")
                     .replace("</html>", "");
        }
        
        // For logical conditions, include more detailed info about progress
        if (condition.getType() == ConditionType.LOGICAL) {
            // Try to get more detailed info about logical condition's progress
            int metCount = condition.getMetConditionCount();
            int totalCount = condition.getTotalConditionCount();
            
            if (totalCount > 1) {
                description += String.format(" (%d/%d sub-conditions met)", metCount, totalCount);
            }
        }
        
        return description;
    }
    /**
     * Creates a detailed tooltip for next run information
     */
    private String getNextRunTooltip(PluginScheduleEntry entry) {
        StringBuilder tooltip = new StringBuilder("<html><b>Next Run Details:</b><br>");
        
        // Add next run time
        Optional<ZonedDateTime> nextTime = entry.getCurrentStartTriggerTime();
        if (nextTime.isPresent()) {
            tooltip.append("Next scheduled time: ").append(nextTime.get().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } else {
            tooltip.append("No specific next run time");
        }
        
        // Add completed runs
        tooltip.append("<br>Times run: ").append(entry.getRunCount());
        
        // Add enabled status
        tooltip.append("<br><b>Status:</b> ").append(entry.isEnabled() ? "Enabled" : "Disabled");
        if (entry.isRunning()) {
            tooltip.append(" (Currently running)");
        }
        
        return tooltip.toString() + "</html>";
    }
   
    /**
     * Determines if the provided plugin is the next one scheduled to run
     */
    private boolean isNextToRun(PluginScheduleEntry scheduledPlugin) {
        if (!scheduledPlugin.isEnabled()) {
            return false;
        }
        
        PluginScheduleEntry nextPlugin = schedulerPlugin.getNextScheduledPlugin();
        return nextPlugin != null && nextPlugin.equals(scheduledPlugin);
    }   
    private void detectChangesInPluginlist(){
        List<PluginScheduleEntry> sortedPlugins = schedulerPlugin.sortPluginScheduleEntries();
        List<PluginScheduleEntry> currentRowToPluginMap = new ArrayList<>(sortedPlugins.size());
        boolean foundAll = true;
        for (int j = 0; j < rowToPluginMap.size(); j++) {
            for (int i = 0; i < sortedPlugins.size(); i++) {
            
                if (sortedPlugins.get(i).equals(rowToPluginMap.get(j))){
                    currentRowToPluginMap.add(sortedPlugins.get(i));
                    break;
                }
            }
           
        }
        if (currentRowToPluginMap.size() != rowToPluginMap.size()){
            foundAll = false;
            log.info("Plugin list changed, refreshing");
            this.rowToPluginMap = new ArrayList<>(sortedPlugins);
        }

    }


    public void refreshTable() {      
        if (this.updatingTable) {
            return; // Skip if already updating
        }
        
        this.updatingTable = true;
        
        try {
            detectChangesInPluginlist();
            // Save current selection
            PluginScheduleEntry selectedPlugin = getSelectedPlugin();
            
            // Get current plugins and sort them by next run time
            List<PluginScheduleEntry> sortedPlugins = schedulerPlugin.sortPluginScheduleEntries();
            
            
            
            // Create a new row map with the correct size to match the sorted plugins
            List<PluginScheduleEntry> newRowMap = new ArrayList<>(sortedPlugins.size());
            // Initialize with nulls first
            for (int i = 0; i < sortedPlugins.size(); i++) {
                newRowMap.add(null);
            }


            // Save the current row map for comparison
            List<PluginScheduleEntry> previousRowMap = new ArrayList<>(rowToPluginMap);
            // Track if we need to force repaint (visual changes that might not trigger repaint)
            boolean needsRepaint = false;
            
            // Set to track plugins we've processed to avoid duplicates
            Set<PluginScheduleEntry> processedPlugins = new HashSet<>();
            
            // First pass: update existing rows in place if possible
            for (int newIndex = 0; newIndex < sortedPlugins.size(); newIndex++) {
                PluginScheduleEntry plugin = sortedPlugins.get(newIndex);
                
                // Skip if this plugin has already been processed
                if (processedPlugins.contains(plugin)) {
                    continue;
                }
                                            
                    
                                // Same position, just update data in place
                if (newIndex < tableModel.getRowCount()) {
                    updateRowWithPlugin(newIndex, plugin);
                    //tableModel.insertRow(newIndex, createRowData(plugin));
                } else {                
                    tableModel.addRow(createRowData(plugin));
                    needsRepaint = true;        
                }                                      
                newRowMap.set(newIndex, plugin);
            }
            
            // Remove any excess rows
            while (tableModel.getRowCount() > sortedPlugins.size()) {
                tableModel.removeRow(tableModel.getRowCount() - 1);
                needsRepaint = true;
            }
            
            // Update our tracking map
            rowToPluginMap = newRowMap;
            
            // Restore selection if possible
            if (selectedPlugin != null) {
                //selectPlugin(selectedPlugin);
            }
            
            // Force repaint if needed
            if (needsRepaint) {
                scheduleTable.repaint();
            }
        } finally {
            this.updatingTable = false;            
        }
    }
    @Deprecated
    public void refreshTable__() {      
        if (this.updatingTable) {
            return; // Skip if already updating
        }
        
        this.updatingTable = true;
        
        try {
            detectChangesInPluginlist();
            // Save current selection
            PluginScheduleEntry selectedPlugin = getSelectedPlugin();
            
            // Get current plugins and sort them by next run time
            List<PluginScheduleEntry> sortedPlugins = schedulerPlugin.sortPluginScheduleEntries();
            
            
            
            // Create a new row map with the correct size to match the sorted plugins
            List<PluginScheduleEntry> newRowMap = new ArrayList<>(sortedPlugins.size());
            // Initialize with nulls first
            for (int i = 0; i < sortedPlugins.size(); i++) {
                newRowMap.add(null);
            }


            // Save the current row map for comparison
            List<PluginScheduleEntry> previousRowMap = new ArrayList<>(rowToPluginMap);
            // Track if we need to force repaint (visual changes that might not trigger repaint)
            boolean needsRepaint = false;
            
            // Set to track plugins we've processed to avoid duplicates
            Set<PluginScheduleEntry> processedPlugins = new HashSet<>();
            
            // First pass: update existing rows in place if possible
            for (int newIndex = 0; newIndex < sortedPlugins.size(); newIndex++) {
                PluginScheduleEntry plugin = sortedPlugins.get(newIndex);
                
                // Skip if this plugin has already been processed
                if (processedPlugins.contains(plugin)) {
                    continue;
                }
                
                boolean found = false;
                
                // Try to find the same plugin reference in the previous map
                for (int oldIndex = 0; oldIndex < previousRowMap.size(); oldIndex++) {
                    PluginScheduleEntry oldPlugin = previousRowMap.get(oldIndex);
                    if (oldPlugin.equals(plugin)) { // Reference equality
                        if (oldIndex < tableModel.getRowCount()) {
                            if (oldIndex == newIndex) {
                                // Same position, just update data in place
                                updateRowWithPlugin(oldIndex, plugin);
                            } else {
                                // Different position - need to move row
                                // First gather the data
                                Object[] rowData = new Object[tableModel.getColumnCount()];
                                for (int col = 0; col < tableModel.getColumnCount(); col++) {
                                    rowData[col] = tableModel.getValueAt(oldIndex, col);
                                }
                                
                                // Update the data with current plugin state
                                updateRowData(rowData, plugin);
                                
                                // Remove old row first
                                tableModel.removeRow(oldIndex);
                                
                                // Adjust insertion point if needed
                                int adjustedNewIndex = newIndex;
                                if (oldIndex < newIndex && tableModel.getRowCount() >= newIndex) {
                                    adjustedNewIndex = newIndex - 1;
                                }
                                
                                // Insert at the correct position or add to end
                                if (adjustedNewIndex < tableModel.getRowCount()) {
                                    tableModel.insertRow(adjustedNewIndex, rowData);
                                } else {
                                    tableModel.addRow(rowData);
                                }
                                
                                needsRepaint = true;
                            }
                        } else {
                            // Row doesn't exist yet, add it
                            if (newIndex < tableModel.getRowCount()) {
                                tableModel.insertRow(newIndex, createRowData(plugin));
                            } else {
                                tableModel.addRow(createRowData(plugin));
                            }
                            needsRepaint = true;
                        }
                        
                        found = true;
                        break;
                    }
                }
                
                // If plugin wasn't found in previous map, add as new
                if (!found) {
                    if (newIndex < tableModel.getRowCount()) {
                        tableModel.insertRow(newIndex, createRowData(plugin));
                    } else {
                        tableModel.addRow(createRowData(plugin));
                    }
                    needsRepaint = true;
                }
                
                // Mark as processed and update the new row map
                processedPlugins.add(plugin);
                newRowMap.set(newIndex, plugin);
            }
            
            // Remove any excess rows
            while (tableModel.getRowCount() > sortedPlugins.size()) {
                tableModel.removeRow(tableModel.getRowCount() - 1);
                needsRepaint = true;
            }
            
            // Update our tracking map
            rowToPluginMap = newRowMap;
            
            // Restore selection if possible
            if (selectedPlugin != null) {
                //selectPlugin(selectedPlugin);
            }
            
            // Force repaint if needed
            if (needsRepaint) {
                scheduleTable.repaint();
            }
        } finally {
            this.updatingTable = false;            
        }
    }

    /**
     * Updates row data array with current plugin values
     */
    private void updateRowData(Object[] rowData, PluginScheduleEntry plugin) {
        // Get basic information
        String pluginName = plugin.getCleanName();
        
        if (schedulerPlugin.isRunningEntry(plugin)) {
            pluginName = "▶ " + pluginName;
        }
        
        // For default plugins, add a visual indicator
        if (plugin.isDefault()) {
            pluginName = "⭐ " + pluginName;
        }
        
        // Update row data
        rowData[0] = pluginName;
        rowData[1] = getEnhancedScheduleDisplay(plugin);
        rowData[2] = getEnhancedNextRunDisplay(plugin);
        rowData[3] = getStartConditionInfo(plugin);
        rowData[4] = getStopConditionInfo(plugin);
        rowData[5] = plugin.getPriority();
        rowData[6] = plugin.isEnabled();
        rowData[7] = plugin.getRunCount();
    }

    /**
     * Updates existing row in the table with current plugin values
     */
    private void updateRowWithPlugin(int rowIndex, PluginScheduleEntry plugin) {
        // Get basic information
        String pluginName = plugin.getCleanName();
        
        if (schedulerPlugin.isRunningEntry(plugin)) {
            pluginName = "▶ " + pluginName;
        }
        
        // For default plugins, add a visual indicator
        if (plugin.isDefault()) {
            pluginName = "⭐ " + pluginName;
        }
        
        // Update existing row with focused columns
        tableModel.setValueAt(pluginName, rowIndex, 0);
        tableModel.setValueAt(getEnhancedScheduleDisplay(plugin), rowIndex, 1);
        tableModel.setValueAt(getEnhancedNextRunDisplay(plugin), rowIndex, 2);
        tableModel.setValueAt(getStartConditionInfo(plugin), rowIndex, 3);
        tableModel.setValueAt(getStopConditionInfo(plugin), rowIndex, 4);
        tableModel.setValueAt(plugin.getPriority(), rowIndex, 5);
        tableModel.setValueAt(plugin.isEnabled(), rowIndex, 6);
        tableModel.setValueAt(plugin.getRunCount(), rowIndex, 7);
    }

    /**
     * Creates a new row data array for a plugin
     */
    private Object[] createRowData(PluginScheduleEntry plugin) {
        // Get basic information
        String pluginName = plugin.getCleanName();
        
        if (schedulerPlugin.isRunningEntry(plugin)) {
            pluginName = "▶ " + pluginName;
        }
        
        // For default plugins, add a visual indicator
        if (plugin.isDefault()) {
            pluginName = "⭐ " + pluginName;
        }
        
        return new Object[]{
            pluginName,
            getEnhancedScheduleDisplay(plugin),
            getEnhancedNextRunDisplay(plugin),
            getStartConditionInfo(plugin),
            getStopConditionInfo(plugin),
            plugin.getPriority(),
            plugin.isEnabled(),
            plugin.getRunCount()
        };
    }
    @Deprecated
    public void refreshTableOld() {
        log.info("Refreshing schedule table");
        
        // Save current selection
        PluginScheduleEntry selectedPlugin = getSelectedPlugin();
        int selectedRow = scheduleTable.getSelectedRow();
        // Get current plugins and sort them by next run time
        
        List<PluginScheduleEntry> plugins = schedulerPlugin.sortPluginScheduleEntries();
        
        rowToPluginMap.clear();
        
        
               
        
         // Update table model
        tableModel.setRowCount(0);
        
        for (PluginScheduleEntry scheduled : plugins) {
            rowToPluginMap.add(scheduled);
            // Get basic information
            String pluginName = scheduled.getCleanName();
            
            
            if (schedulerPlugin.isRunningEntry(scheduled)) {
                pluginName = "▶ " + pluginName;
            }
            
            // Prepare schedule display with enhanced information
            String scheduleDisplay = getEnhancedScheduleDisplay(scheduled);
            
            // Enhanced next run display
            String nextRunDisplay = getEnhancedNextRunDisplay(scheduled);
            
            // Split condition info
            String startConditionInfo = getStartConditionInfo(scheduled);
            String stopConditionInfo = getStopConditionInfo(scheduled);
            
            // Add row to table
            tableModel.addRow(new Object[]{
                pluginName,
                scheduleDisplay,
                nextRunDisplay,
                startConditionInfo,
                stopConditionInfo,
                scheduled.getPriority(),
                scheduled.isDefault(),
                scheduled.isEnabled(),
                scheduled.isAllowRandomScheduling(),
                scheduled.isNeedsStopCondition(),
                scheduled.getRunCount()
            });
        }
    
        // Remove excess rows if there are more rows than plugins
        while (tableModel.getRowCount() > plugins.size()) {
            tableModel.removeRow(tableModel.getRowCount() - 1);
        }
       
        // Restore selection if possible - using reference equality, not just equals()
        if (selectedPlugin != null) {
            log.info("Restoring selection for plugin: " + selectedPlugin.getName());
            
            // First try to find the exact same object reference
            for (int i = 0; i < plugins.size(); i++) {
                if (plugins.get(i) == selectedPlugin) { // Use reference equality
                    scheduleTable.setRowSelectionInterval(i, i);
                    log.info("Found selected plugin by reference equality: " + selectedPlugin.getName() + " at row " + i);                
                    return;                   
                }
            }
            
            
            // If reference equality fails, try equals() as fallback
            for (int i = 0; i < plugins.size(); i++) {
                if (plugins.get(i).equals(selectedPlugin)) {
                    log.info("Found selected plugin by equals(): " + selectedPlugin.getName() + " at row " + i);
                    scheduleTable.setRowSelectionInterval(i, i);
                    return;
                }
            }
            
            // If all else fails, try by name as a last resort
            for (int i = 0; i < plugins.size(); i++) {
                if (plugins.get(i).getName().equals(selectedPlugin.getName())) {
                    log.info("Found selected plugin by name: " + selectedPlugin.getName() + " at row " + i);
                    scheduleTable.setRowSelectionInterval(i, i);
                    return;
                }
            }
        }else {
            
        }
        
    }
    /**
     * Creates a display of start condition information
     */
    private String getStartConditionInfo(PluginScheduleEntry entry) {
        int startTotal = entry.getStartConditionManager().getConditions().size();
        if (startTotal == 0) {
            return "None";
        }
        
        int startMet = (int) entry.getStartConditionManager().getConditions().stream()
            .filter(Condition::isSatisfied).count();
        
        StringBuilder info = new StringBuilder();
        info.append("<html>");
        info.append(startMet).append("/").append(startTotal);
        
        // Add type indicator
        boolean startRequiresAll = entry.getStartConditionManager().requiresAll();
        info.append("<br>").append(startRequiresAll ? "ALL" : "ANY");
        
        // Add one-time indicator if applicable
        if (entry.hasAnyOneTimeStartConditions()) {
            info.append("<br>One-time");
        }
        
        return info.toString() + "</html>";
    }

    /**
     * Creates a display of stop condition information
     */
    private String getStopConditionInfo(PluginScheduleEntry entry) {
        int stopTotal = entry.getStopConditionManager().getConditions().size();
        if (stopTotal == 0) {
            return "None";
        }
        
        int stopMet = (int) entry.getStopConditionManager().getConditions().stream()
            .filter(Condition::isSatisfied).count();
        
        StringBuilder info = new StringBuilder();
        info.append("<html>");
        info.append(stopMet).append("/").append(stopTotal);
        
        // Add type indicator
        boolean stopRequiresAll = entry.getStopConditionManager().requiresAll();
        info.append("<br>").append(stopRequiresAll ? "ALL" : "ANY");
        
        // Add progress for running plugins
        if (entry.isRunning()) {
            double progress = entry.getStopConditionProgress();
            if (progress > 0) {
                info.append("<br>").append(String.format("%.0f%%", progress));
            }
        }
        
        // Add one-time indicator if applicable
        if (entry.hasAnyOneTimeStopConditions()) {
            info.append("<br>One-time");
        }
        
        return info.toString() + "</html>";
    }

   

    /**
     * Creates an enhanced display of schedule information
     */
    private String getEnhancedScheduleDisplay(PluginScheduleEntry entry) {
        // Check for one-time schedule first
        boolean isOneTime = entry.hasAnyOneTimeStartConditions();
        
        // If it's a one-time schedule that's already triggered, show completion status
        if (isOneTime && entry.hasTriggeredOneTimeStartConditions() && !entry.canStartTriggerAgain()) {
            return "One-time (Completed)";
        }
        
        // Get the base interval display
        String baseDisplay = entry.getIntervalDisplay();
        
        // For one-time schedules, add an indicator
        if (isOneTime) {
            return "One-time: " + baseDisplay;
        }
        
        return baseDisplay;
    }

    /**
     * Creates an enhanced display of the next run time, including last stop info
     */
    private String getEnhancedNextRunDisplay(PluginScheduleEntry entry) {
        StringBuilder display = new StringBuilder();
        display.append("<html>");
        
        // First add the plugin's optimized display method for scheduling
        String baseDisplay = entry.getNextRunDisplay();
        
        // Add extra information for one-time entries that can't run again
        if (entry.hasAnyOneTimeStartConditions() && !entry.canStartTriggerAgain()) {
            if (entry.getRunCount() > 0) {
                display.append("Completed");
            } else {
                display.append("Cannot run");
            }
        } else {
            display.append(baseDisplay);
        }
        
        // Add last stop info for plugins that have run before and aren't currently running
        if (entry.getRunCount() > 0 && !entry.isRunning() && entry.getStopReasonType() != PluginScheduleEntry.StopReason.NONE) {
            // Add a line break
            display.append("<br>");
            
            // Show different info based on stop reason
            switch(entry.getStopReasonType()) {
                case SCHEDULED_STOP:
                    display.append("✓ Stopped");  // Checkmark for normal stop
                    break;
                case PLUGIN_FINISHED:
                    display.append("✓ Finished");  // Checkmark for self-completed
                    break;
                case MANUAL_STOP:
                    display.append("⏹ Manual");  // Square for manual stop
                    break;
                case HARD_STOP_TIMEOUT:
                    display.append("⚠ Timeout");  // Warning for timeout
                    break;
                case ERROR:
                    display.append("❌ Error");  // X for error
                    // Add concise error message
                    String errorMsg = entry.getLastStopReason();
                    if (errorMsg != null && !errorMsg.isEmpty()) {
                        if (errorMsg.length() > 30) {
                            errorMsg = errorMsg.substring(0, 27) + "...";
                        }
                        display.append("<br>").append(errorMsg);
                    }
                    break;
                default:
                    break;
            }
        }
        
        return display.toString() + "</html>";
    }

    /**
     * Creates an enhanced display of condition information
     */
    private String getEnhancedConditionInfo(PluginScheduleEntry entry) {
        StringBuilder info = new StringBuilder();
        
        // Add start condition info if available
        int startTotal = entry.getStartConditionManager().getConditions().size();
        if (startTotal > 0) {
            int startMet = (int) entry.getStartConditionManager().getConditions().stream()
                .filter(Condition::isSatisfied).count();
            
            info.append("Start: ").append(startMet).append("/").append(startTotal);
            
            // Add type indicator
            boolean startRequiresAll = entry.getStartConditionManager().requiresAll();
            info.append(startRequiresAll ? " (ALL)" : " (ANY)");
        }
        
        // Add stop condition info if available
        int stopTotal = entry.getStopConditionManager().getConditions().size();
        if (stopTotal > 0) {
            if (info.length() > 0) {
                info.append(" | ");
            }
            
            int stopMet = (int) entry.getStopConditionManager().getConditions().stream()
                .filter(Condition::isSatisfied).count();
            
            info.append("Stop: ").append(stopMet).append("/").append(stopTotal);
            
            // Add type indicator
            boolean stopRequiresAll = entry.getStopConditionManager().requiresAll();
            info.append(stopRequiresAll ? " (ALL)" : " (ANY)");
            
            // Add progress for running plugins
            if (entry.isRunning()) {
                double progress = entry.getStopConditionProgress();
                if (progress > 0) {
                    info.append(String.format(" (%.0f%%)", progress));
                }
            }
        }
        
        // If no conditions, show "None"
        if (info.length() == 0) {
            return "None";
        }
        
        return info.toString();
    }

    public void addSelectionListener(Consumer<PluginScheduleEntry> listener) {
        this.selectionListener = listener;
        scheduleTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = scheduleTable.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < schedulerPlugin.getScheduledPlugins().size()) {
                    listener.accept(getPluginAtRow(selectedRow));
                } else {
                    listener.accept(null);
                }
            }
        });
    }

    public PluginScheduleEntry getSelectedPlugin() {
        int selectedRow = scheduleTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < schedulerPlugin.getScheduledPlugins().size()) {
            if (rowToPluginMap.size() > selectedRow) {
                // Use the rowToPluginMap to get the plugin
                return rowToPluginMap.get(selectedRow);
            }else {
                // Fallback to the original list if rowToPluginMap is not available
                // This should not happen in normal operation                
                rowToPluginMap = new ArrayList<>(schedulerPlugin.getScheduledPlugins());
                return schedulerPlugin.getScheduledPlugins().get(selectedRow);
            }
            
            //return schedulerPlugin.getScheduledPlugins().get(selectedRow);
        }
        return null;
    }

    /**
     * Clears the current table selection and notifies the selection listener
     */
    public void clearSelection() {
        scheduleTable.clearSelection();
        if (selectionListener != null) {
            selectionListener.accept(null);
        }
    }
    public void addAndSelect(PluginScheduleEntry pluginEntry) {
        if (pluginEntry == null) return;
        
        for (PluginScheduleEntry entry : schedulerPlugin.getScheduledPlugins()) {
            if ( pluginEntry == entry) {
                // Plugin already exists, no need to add -> no duplicate
                return;
            }
        }
        schedulerPlugin.addScheduledPlugin(pluginEntry);
        rowToPluginMap.add(pluginEntry);
        tableModel.addRow(new Object[]{
            pluginEntry.getName(),
            pluginEntry.getIntervalDisplay(),
            pluginEntry.getNextRunDisplay(),
            getStartConditionInfo(pluginEntry),
            getStopConditionInfo(pluginEntry),
            pluginEntry.getPriority(),
            pluginEntry.isDefault(),
            pluginEntry.isEnabled(),
            pluginEntry.isAllowRandomScheduling(),
            pluginEntry.isNeedsStopCondition(),
            pluginEntry.getRunCount()
        });
        scheduleTable.setRowSelectionInterval(getRowCount(), getRowCount());        
    }
    /**
     * Selects the given plugin in the table
     * @param plugin The plugin to select
     */
    public void selectPlugin(PluginScheduleEntry plugin) {
        if (plugin == null) return;
        List<PluginScheduleEntry> plugins = this.rowToPluginMap;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String rowName = String.valueOf(tableModel.getValueAt(i, 0))
                .replaceAll("▶ ", ""); // Remove play indicator if present
             // First try to find the exact same object reference
             
            if (plugins.get(i) == plugin) { // Use reference equality
                scheduleTable.setRowSelectionInterval(i, i);
                // Make sure the selected row is visible
                Rectangle rect = scheduleTable.getCellRect(i, 0, true);
                scheduleTable.scrollRectToVisible(rect);
                
                // Notify listeners
                if (selectionListener != null) {
                    selectionListener.accept(plugin);
                }
                return;
            }
        }
    }
    /**
     * Creates a comprehensive tooltip for plugin details including last stop information
     */
    private String getPluginDetailsTooltip(PluginScheduleEntry entry) {
        StringBuilder tooltip = new StringBuilder("<html><b>Plugin Details:</b> ").append(entry.getCleanName());
        
        // Status section
        tooltip.append("<br><br><b>Status:</b> ");
        if (entry.isRunning()) {
            tooltip.append("<span style='color:green'>Currently Running</span>");
        } else if (!entry.isEnabled()) {
            tooltip.append("<span style='color:gray'>Disabled</span>");
        } else if (isNextToRun(entry)) {
            tooltip.append("<span style='color:orange'>Next to Run</span>");
        } else {
            tooltip.append("Waiting for schedule");
        }
        
        // Run information
        tooltip.append("<br><b>Run Count:</b> ").append(entry.getRunCount());
        
        // Last stop information
        if (entry.getRunCount() > 0 && !entry.isRunning()) {
            tooltip.append("<br><br><b>Last Stop Info:</b>");
            
            // Stop reason
            String stopReason = entry.getLastStopReason();
            if (stopReason != null && !stopReason.isEmpty()) {
                tooltip.append("<br>Reason: ").append(stopReason);
            }
            
            // Stop reason type
            tooltip.append("<br>Type: ");
            switch (entry.getStopReasonType()) {
                case SCHEDULED_STOP:
                    tooltip.append("Scheduled Stop (conditions met)");
                    break;
                case MANUAL_STOP:
                    tooltip.append("Manual Stop (user initiated)");
                    break;
                case HARD_STOP_TIMEOUT:
                    tooltip.append("Hard Stop (forced after timeout)");
                    break;
                case ERROR:
                    tooltip.append("<span style='color:red'>Error</span>");
                    break;
                case PLUGIN_FINISHED:
                    tooltip.append("Plugin Self-reported Completion");
                    break;
                case NONE:
                default:
                    tooltip.append("Unknown");
                    break;
            }
            
            // Success status
            tooltip.append("<br>Success: ");
            tooltip.append(entry.isLastRunSuccessful() ? 
                           "<span style='color:green'>Yes</span>" : 
                           "<span style='color:red'>No</span>");
        }
        
        // Schedule information
        tooltip.append("<br><br><b>Schedule:</b> ").append(entry.getIntervalDisplay());
        
        // Start/Stop condition information - use the detailed tooltip methods
        tooltip.append("<br><br>");
        
        // Extract the content from the start and stop condition tooltips, but exclude the html tags
        String startConditions = getStartConditionsTooltip(entry)
            .replace("<html>", "")
            .replace("</html>", "");
        
        String stopConditions = getStopConditionsTooltip(entry)
            .replace("<html>", "")
            .replace("</html>", "");
        
        tooltip.append(startConditions);
        tooltip.append("<br><br>");
        tooltip.append(stopConditions);
        
        // Configuration
        tooltip.append("<br><br><b>Configuration:</b>");
        tooltip.append("<br>Priority: ").append(entry.getPriority());
        tooltip.append("<br>Default: ").append(entry.isDefault() ? "Yes" : "No");
        tooltip.append("<br>Random Scheduling: ").append(entry.isAllowRandomScheduling() ? "Enabled" : "Disabled");
        
        return tooltip.toString() + "</html>";
    }
    
    /**
     * Implementation of ScheduleTableModel interface - gets the plugin at a specific row
     */
    @Override
    public PluginScheduleEntry getPluginAtRow(int row) {
        if (row >= 0 && row < rowToPluginMap.size()) {
            return rowToPluginMap.get(row);
        }
        if (row >= 0 && row < tableModel.getRowCount()) {
            return schedulerPlugin.getScheduledPlugins().get(row);
        }
        return null;
    }

}
