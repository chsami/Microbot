package net.runelite.client.plugins.microbot.pluginscheduler;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.pluginscheduler.type.Scheduled;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin.configGroup;

public class SchedulerPanel extends PluginPanel {
    private final SchedulerPlugin plugin;

    // Current plugin section
    private final JLabel currentPluginLabel;
    private final JLabel runtimeLabel;

    // Next plugin section
    private final JLabel nextPluginNameLabel;
    private final JLabel nextPluginTimeLabel;
    private final JLabel nextPluginScheduleLabel;


    public SchedulerPanel(SchedulerPlugin plugin, ConfigManager configManager) {
        super(false);
        this.plugin = plugin;

        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Create main panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Current plugin info panel
        JPanel infoPanel = createInfoPanel("Current Plugin");

        JLabel pluginLabel = new JLabel("Plugin:");
        pluginLabel.setForeground(Color.WHITE);
        pluginLabel.setFont(FontManager.getRunescapeFont());
        infoPanel.add(pluginLabel, createGbc(0, 0));

        currentPluginLabel = createValueLabel("None");
        infoPanel.add(currentPluginLabel, createGbc(1, 0));

        JLabel runtimeTitleLabel = new JLabel("Runtime:");
        runtimeTitleLabel.setForeground(Color.WHITE);
        runtimeTitleLabel.setFont(FontManager.getRunescapeFont());
        infoPanel.add(runtimeTitleLabel, createGbc(0, 1));

        runtimeLabel = createValueLabel("00:00:00");
        infoPanel.add(runtimeLabel, createGbc(1, 1));

        // Next plugin info panel
        JPanel nextPluginPanel = createInfoPanel("Next Scheduled Plugin");
        JLabel nextPluginTitleLabel = new JLabel("Plugin:");
        nextPluginTitleLabel.setForeground(Color.WHITE);
        nextPluginTitleLabel.setFont(FontManager.getRunescapeFont());
        nextPluginPanel.add(nextPluginTitleLabel, createGbc(0, 0));

        nextPluginNameLabel = createValueLabel("None");
        nextPluginPanel.add(nextPluginNameLabel, createGbc(1, 0));

        JLabel nextRunLabel = new JLabel("Next Run:");
        nextRunLabel.setForeground(Color.WHITE);
        nextRunLabel.setFont(FontManager.getRunescapeFont());
        nextPluginPanel.add(nextRunLabel, createGbc(0, 1));

        nextPluginTimeLabel = createValueLabel("--:--");
        nextPluginPanel.add(nextPluginTimeLabel, createGbc(1, 1));

        JLabel scheduleLabel = new JLabel("Schedule:");
        scheduleLabel.setForeground(Color.WHITE);
        scheduleLabel.setFont(FontManager.getRunescapeFont());
        nextPluginPanel.add(scheduleLabel, createGbc(0, 2));

        nextPluginScheduleLabel = createValueLabel("None");
        nextPluginPanel.add(nextPluginScheduleLabel, createGbc(1, 2));

        // Button panel
        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 0, 5));
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttonPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        // Control buttons
        JButton openConfigButton = createButton();
        openConfigButton.addActionListener(this::onOpenConfigButtonClicked);
        buttonPanel.add(openConfigButton);

        boolean logout = Boolean.parseBoolean(configManager.getConfiguration(configGroup, "logOut"));
        JToggleButton logoutToggleButton = new JToggleButton("Automatic logout: " + (logout ? "Enabled" : "Disabled"));
        logoutToggleButton.setSelected(logout);
        logoutToggleButton.setFont(FontManager.getRunescapeSmallFont());
        logoutToggleButton.setFocusPainted(false);
        logoutToggleButton.setForeground(Color.WHITE);

        logoutToggleButton.addActionListener(e -> {
            boolean newState = logoutToggleButton.isSelected();
            configManager.setConfiguration(configGroup, "logOut", newState);
            logoutToggleButton.setText("Automatic logout: " + (!newState ? "Disabled" : "Enabled"));
        });
        buttonPanel.add(logoutToggleButton);

        // Add components to main panel
        mainPanel.add(infoPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(nextPluginPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(buttonPanel);

        add(mainPanel, BorderLayout.NORTH);
        refresh();
    }

    private GridBagConstraints createGbc(int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = 1;
        gbc.gridheight = 1;
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.anchor = (x == 0) ? GridBagConstraints.WEST : GridBagConstraints.EAST;
        gbc.fill = (x == 0) ? GridBagConstraints.BOTH
                : GridBagConstraints.HORIZONTAL;

        gbc.weightx = (x == 0) ? 0.1 : 1.0;
        gbc.weighty = 1.0;
        return gbc;
    }

    private JPanel createInfoPanel(String title) {
        JPanel panel = new JPanel(new GridBagLayout());

        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.MEDIUM_GRAY_COLOR),
                        BorderFactory.createEmptyBorder(5, 5, 5, 5)
                ),
                title,
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                FontManager.getRunescapeBoldFont(),
                Color.WHITE
        ));
        return panel;
    }

    private JLabel createValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeFont());
        return label;
    }

    private JButton createButton() {
        JButton button = new JButton("Open Scheduler");
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(ColorScheme.BRAND_ORANGE);
        button.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE.darker(), 1),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));

        // Add hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(ColorScheme.BRAND_ORANGE.brighter());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(ColorScheme.BRAND_ORANGE);
            }
        });

        return button;
    }

    void refresh() {
        updatePluginInfo();
        updateNextPluginInfo();
    }

    void updatePluginInfo() {
        Scheduled currentPlugin = plugin.getCurrentPlugin();

        if (currentPlugin != null) {
            long startTime = currentPlugin.getLastRunTime();
            currentPluginLabel.setText(currentPlugin.getCleanName());

            if (startTime > 0) {
                long runtimeMillis = System.currentTimeMillis() - startTime;
                long hours = TimeUnit.MILLISECONDS.toHours(runtimeMillis);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(runtimeMillis) % 60;
                long seconds = TimeUnit.MILLISECONDS.toSeconds(runtimeMillis) % 60;
                runtimeLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
            } else {
                runtimeLabel.setText("00:00:00");
            }
        } else {
            currentPluginLabel.setText("None");
            runtimeLabel.setText("00:00:00");
        }
    }
    void updateNextPluginInfo() {
        Scheduled nextPlugin = plugin.getNextScheduledPlugin();

        if (nextPlugin != null) {
            nextPluginNameLabel.setText(nextPlugin.getName());
            nextPluginTimeLabel.setText(nextPlugin.getNextRunDisplay());

            // Format the schedule depluginion
            String scheduleDesc = nextPlugin.getIntervalDisplay();
            if (nextPlugin.getDuration() != null && !nextPlugin.getDuration().isEmpty()) {
                scheduleDesc += " for " + nextPlugin.getDuration();
            }
            nextPluginScheduleLabel.setText(scheduleDesc);
        } else {
            nextPluginNameLabel.setText("None");
            nextPluginTimeLabel.setText("--:--");
            nextPluginScheduleLabel.setText("None");
        }
    }

    private void onOpenConfigButtonClicked(ActionEvent e) {
        plugin.openSchedulerWindow();
    }
}
