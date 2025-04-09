package net.runelite.client.plugins.microbot.pluginscheduler;

import net.runelite.client.plugins.microbot.pluginscheduler.type.Scheduled;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.ScheduleFormPanel;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.ScheduleTablePanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class SchedulerWindow extends JFrame {
    private final SchedulerPlugin plugin;

    private final ScheduleTablePanel tablePanel;
    private final ScheduleFormPanel formPanel;

    public SchedulerWindow(SchedulerPlugin plugin) {
        super("Plugin Scheduler");
        this.plugin = plugin;

        setSize(800, 500);
        setLocationRelativeTo(null); // Center on screen
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Add window listener to handle window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        // Create main panel with border layout
        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Create table panel
        tablePanel = new ScheduleTablePanel(plugin);
        tablePanel.addSelectionListener(this::onPluginSelected);

        // Create form panel
        formPanel = new ScheduleFormPanel(plugin);
        formPanel.setAddButtonAction(e -> onAddPlugin());
        formPanel.setUpdateButtonAction(e -> onUpdatePlugin());
        formPanel.setRemoveButtonAction(e -> onRemovePlugin());

        // Disable edit buttons initially
        formPanel.setEditMode(false);

        // Add components to main panel
        mainPanel.add(tablePanel, BorderLayout.CENTER);
        mainPanel.add(formPanel, BorderLayout.SOUTH);

        // Add main panel to frame
        add(mainPanel);

        // Initialize with data
        refresh();
    }

    void refresh() {
        tablePanel.refreshTable();
        if (formPanel != null) {
            formPanel.updateControlButton();
        }
    }

    private void onPluginSelected(Scheduled plugin) {
        if (plugin != null) {
            formPanel.loadPlugin(plugin);
            formPanel.setEditMode(true);
        } else {
            formPanel.clearForm();
            formPanel.setEditMode(false);
        }
    }

    private void onAddPlugin() {
        Scheduled _plugin = formPanel.getPluginFromForm();
        if (_plugin != null) {
            plugin.addScheduledPlugin(_plugin);
            tablePanel.refreshTable();
            formPanel.clearForm();
        }

        plugin.saveScheduledPlugins();
    }

    private void onUpdatePlugin() {
        Scheduled oldPlugin = tablePanel.getSelectedPlugin();
        Scheduled newPlugin = formPanel.getPluginFromForm();

        if (oldPlugin != null && newPlugin != null) {
            plugin.updateScheduledPlugin(oldPlugin, newPlugin);
            tablePanel.refreshTable();
            formPanel.clearForm();
            formPanel.setEditMode(false);
        }

        plugin.saveScheduledPlugins();
    }

    private void onRemovePlugin() {
        Scheduled _plugin = tablePanel.getSelectedPlugin();
        if (_plugin != null) {
            plugin.removeScheduledPlugin(_plugin);
            tablePanel.refreshTable();
            formPanel.clearForm();
            formPanel.setEditMode(false);
        }

        plugin.saveScheduledPlugins();
    }
}
