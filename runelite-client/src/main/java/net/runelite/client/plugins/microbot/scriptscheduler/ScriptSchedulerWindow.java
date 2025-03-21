package net.runelite.client.plugins.microbot.scriptscheduler;

import net.runelite.client.plugins.microbot.scriptscheduler.type.ScheduleType;
import net.runelite.client.plugins.microbot.scriptscheduler.ui.ScheduleFormPanel;
import net.runelite.client.plugins.microbot.scriptscheduler.ui.ScheduleTablePanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;

public class ScriptSchedulerWindow extends JFrame {
    private final ScriptSchedulerPlugin plugin;
    private final ScriptSchedulerConfig config;

    private ScheduleTablePanel tablePanel;
    private ScheduleFormPanel formPanel;

    public ScriptSchedulerWindow(ScriptSchedulerPlugin plugin, ScriptSchedulerConfig config) {
        super("Script Scheduler");
        this.plugin = plugin;
        this.config = config;

        setSize(650, 500);
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
        tablePanel.addSelectionListener(this::onScriptSelected);

        // Create form panel
        formPanel = new ScheduleFormPanel(plugin);
        formPanel.setAddButtonAction(e -> onAddScript());
        formPanel.setUpdateButtonAction(e -> onUpdateScript());
        formPanel.setRemoveButtonAction(e -> onRemoveScript());
        // No need to set runNowButton action anymore, it's handled by the controlButton

        // Disable edit buttons initially
        formPanel.setEditMode(false);

        // Add components to main panel
        mainPanel.add(tablePanel, BorderLayout.CENTER);
        mainPanel.add(formPanel, BorderLayout.SOUTH);

        // Add main panel to frame
        add(mainPanel);

        // Initialize with data
        refreshData();
    }

    public void refreshData() {
        tablePanel.refreshTable();
    }

    private void onScriptSelected(ScheduledScript script) {
        if (script != null) {
            formPanel.loadScript(script);
            formPanel.setEditMode(true);
        } else {
            formPanel.clearForm();
            formPanel.setEditMode(false);
        }
    }

    private void onAddScript() {
        ScheduledScript script = formPanel.getScriptFromForm();
        if (script != null) {
            plugin.addScheduledScript(script);
            tablePanel.refreshTable();
            formPanel.clearForm();
        }
    }

    private void onUpdateScript() {
        ScheduledScript oldScript = tablePanel.getSelectedScript();
        ScheduledScript newScript = formPanel.getScriptFromForm();

        if (oldScript != null && newScript != null) {
            plugin.updateScheduledScript(oldScript, newScript);
            tablePanel.refreshTable();
            formPanel.clearForm();
            formPanel.setEditMode(false);
        }
    }

    private void onRemoveScript() {
        ScheduledScript script = tablePanel.getSelectedScript();
        if (script != null) {
            plugin.removeScheduledScript(script);
            tablePanel.refreshTable();
            formPanel.clearForm();
            formPanel.setEditMode(false);
        }
    }

    public void updateControlButton() {
        if (formPanel != null) {
            formPanel.updateControlButton();
        }
    }

}
