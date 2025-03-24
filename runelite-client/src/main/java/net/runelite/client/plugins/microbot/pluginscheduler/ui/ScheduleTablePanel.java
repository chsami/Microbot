package net.runelite.client.plugins.microbot.pluginscheduler.ui;

import net.runelite.client.plugins.microbot.pluginscheduler.type.Scheduled;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.api.StoppingConditionProvider;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

public class ScheduleTablePanel extends JPanel {
    private final SchedulerPlugin plugin;
    private final JTable scheduleTable;
    private final DefaultTableModel tableModel;
    private Consumer<Scheduled> selectionListener;

    public ScheduleTablePanel(SchedulerPlugin plugin) {
        this.plugin = plugin;

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

        // Create table model
        tableModel = new DefaultTableModel(
                new Object[]{"Plugin", "Schedule", "Duration", "Next Run", "Enabled"}, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 4) return Boolean.class;
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4; // Only enabled column is editable
            }
        };

        // Add listener for enabled column changes
        tableModel.addTableModelListener(e -> {
            if (e.getColumn() == 4) { // Enabled column
                int row = e.getFirstRow();
                Boolean enabled = (Boolean) tableModel.getValueAt(row, 4);
                Scheduled _plugin = plugin.getScheduledPlugins().get(row);
                _plugin.setEnabled(enabled);
                plugin.saveScheduledPlugins();
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

        // Add mouse listener to handle clicks outside the table data
        scheduleTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = scheduleTable.rowAtPoint(e.getPoint());
                int col = scheduleTable.columnAtPoint(e.getPoint());

                // If clicked outside the table data area, clear selection
                if (row == -1 || col == -1) {
                    clearSelection();
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
        scheduleTable.getColumnModel().getColumn(0).setPreferredWidth(150); // Plugin
        scheduleTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Schedule
        scheduleTable.getColumnModel().getColumn(2).setPreferredWidth(80);  // Duration
        scheduleTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Next Run
        scheduleTable.getColumnModel().getColumn(4).setPreferredWidth(60);  // Enabled

        // Custom cell renderer for alternating row colors
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.DARK_GRAY_COLOR);
                } else {
                    c.setBackground(ColorScheme.BRAND_ORANGE);
                    c.setForeground(Color.WHITE);
                }

                setBorder(new EmptyBorder(2, 5, 2, 5));
                return c;
            }
        };

        renderer.setHorizontalAlignment(SwingConstants.LEFT);

        // Apply renderer to all columns except the boolean column
        for (int i = 0; i < scheduleTable.getColumnCount() - 1; i++) {
            scheduleTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
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
    }

    public void refreshTable() {
        // Get current plugins
        List<Scheduled> plugins = plugin.getScheduledPlugins();
        long currentTime = System.currentTimeMillis();

        // Update existing rows and add new ones
        for (int i = 0; i < plugins.size(); i++) {
            Scheduled scheduled = plugins.get(i);
            String pluginName = scheduled.getName();
            
            // Check for custom conditions
            boolean hasCustomConditions = !scheduled.getConditionManager().getConditions().isEmpty();
            
            // Check for plugin-provided conditions
            boolean hasPluginProvidedConditions = scheduled.getPlugin() instanceof StoppingConditionProvider;
            
            // Create schedule display with condition indicators
            String scheduleDisplay = scheduled.getIntervalDisplay();
            if (hasCustomConditions) {
                scheduleDisplay += " + Conditions";
            }
            if (hasPluginProvidedConditions) {
                scheduleDisplay += " + Plugin Conditions";
            }
            
            // Update or add table row
            if (i < tableModel.getRowCount()) {
                // Update existing row
                tableModel.setValueAt(pluginName, i, 0);
                tableModel.setValueAt(scheduleDisplay, i, 1);
                
                // Duration column - show conditions if present
                String durationDisplay = scheduled.getDuration() != null && !scheduled.getDuration().isEmpty() ?
                        scheduled.getDuration() : 
                        hasCustomConditions ? "Until conditions met" : "Until stopped";
                tableModel.setValueAt(durationDisplay, i, 2);
                
                tableModel.setValueAt(scheduled.getNextRunDisplay(currentTime), i, 3);
                tableModel.setValueAt(scheduled.isEnabled(), i, 4);
            } else {
                // Add new row with same logic
                String durationDisplay = scheduled.getDuration() != null && !scheduled.getDuration().isEmpty() ?
                        scheduled.getDuration() : 
                        hasCustomConditions ? "Until conditions met" : "Until stopped";
                
                tableModel.addRow(new Object[]{
                        pluginName,
                        scheduleDisplay,
                        durationDisplay,
                        scheduled.getNextRunDisplay(currentTime),
                        scheduled.isEnabled()
                });
            }
        }

        // Remove excess rows if there are more rows than plugins
        while (tableModel.getRowCount() > plugins.size()) {
            tableModel.removeRow(tableModel.getRowCount() - 1);
        }
    }

    public void addSelectionListener(Consumer<Scheduled> listener) {
        this.selectionListener = listener;
        scheduleTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = scheduleTable.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < plugin.getScheduledPlugins().size()) {
                    listener.accept(plugin.getScheduledPlugins().get(selectedRow));
                } else {
                    listener.accept(null);
                }
            }
        });
    }

    public Scheduled getSelectedPlugin() {
        int selectedRow = scheduleTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < plugin.getScheduledPlugins().size()) {
            return plugin.getScheduledPlugins().get(selectedRow);
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
}
