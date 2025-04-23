package net.runelite.client.plugins.microbot.pluginscheduler.ui.PluginScheduleEntry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EventObject;

/**
 * Custom cell editor that provides a spinner for editing plugin priority values
 */
@Slf4j
public class PrioritySpinnerEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
    private JSpinner spinner;
    private SpinnerNumberModel spinnerModel;
    private PluginScheduleEntry currentEntry;
    
    public PrioritySpinnerEditor() {
        // Create spinner model with min 0, max 100, step 1, initial value 0
        spinnerModel = new SpinnerNumberModel(0, 0, 100, 1);
        
        // Setup spinner with custom model and editor
        spinner = new JSpinner(spinnerModel);
        spinner.setBackground(ColorScheme.DARK_GRAY_COLOR);
        spinner.setBorder(new EmptyBorder(2, 5, 2, 5));
        
        // Style the spinner editor
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        editor.getTextField().setForeground(Color.WHITE);
        editor.getTextField().setBackground(ColorScheme.DARK_GRAY_COLOR);
        editor.getTextField().setBorder(BorderFactory.createEmptyBorder());
        
        // Add listener for value changes
        spinner.addChangeListener(e -> {
            // Update the underlying model when the spinner value changes
            if (currentEntry != null) {
                int newValue = (Integer) spinner.getValue();
                
                // If this is a default plugin, don't allow changing priority from 0
                if (currentEntry.isDefault() && newValue != 0) {
                    spinner.setValue(0); // Reset to 0
                    log.debug("Cannot change priority of default plugin {}. Resetting to 0.", currentEntry.getCleanName());
                    return;
                }
                
                currentEntry.setPriority(newValue);
                log.debug("Updated priority for plugin {} to {}", currentEntry.getCleanName(), newValue);
            }
        });
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, 
                                                boolean isSelected, int row, int column) {
        // Get the PluginScheduleEntry for this row
        if (table.getModel().getValueAt(row, 0) instanceof PluginScheduleEntry) {
            currentEntry = (PluginScheduleEntry) table.getModel().getValueAt(row, 0);
        } else {
            // If the first column doesn't contain the PluginScheduleEntry, try to get it from the table model
            if (table.getModel() instanceof ScheduleTableModel) {
                ScheduleTableModel model = (ScheduleTableModel) table.getModel();
                currentEntry = model.getPluginAtRow(row);
            }
        }
        
        // Set current value
        int priority = value instanceof Integer ? (Integer) value : 0;
        spinner.setValue(priority);
        
        // If this is a default plugin, disable editing
        boolean isDefault = false;
        if (table.getModel().getValueAt(row, 6) instanceof Boolean) {
            isDefault = (Boolean) table.getModel().getValueAt(row, 6);
        }
        
        spinner.setEnabled(!isDefault);
        
        // Set background color for selection
        if (isSelected) {
            spinner.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
            JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
            editor.getTextField().setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        } else {
            spinner.setBackground(ColorScheme.DARK_GRAY_COLOR);
            JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
            editor.getTextField().setBackground(ColorScheme.DARK_GRAY_COLOR);
        }
        
        return spinner;
    }

    @Override
    public Object getCellEditorValue() {
        return spinner.getValue();
    }
    
    @Override
    public boolean isCellEditable(EventObject anEvent) {
        // Allow editing if the current plugin is not a default plugin
        return currentEntry != null && !currentEntry.isDefault();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        fireEditingStopped();
    }
}