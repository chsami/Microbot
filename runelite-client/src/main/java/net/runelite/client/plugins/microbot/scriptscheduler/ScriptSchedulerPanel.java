package net.runelite.client.plugins.microbot.scriptscheduler;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ScriptSchedulerPanel extends PluginPanel {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ScriptSchedulerPlugin plugin;
    private final ScriptSchedulerConfig config;

    // Current script section
    private JLabel currentScriptLabel;
    private JLabel runtimeLabel;

    // Next script section
    private JLabel nextScriptNameLabel;
    private JLabel nextScriptTimeLabel;
    private JLabel nextScriptScheduleLabel;

    // Control buttons
    private JButton openConfigButton;

    public ScriptSchedulerPanel(ScriptSchedulerPlugin plugin, ScriptSchedulerConfig config) {
        super(false);
        this.plugin = plugin;
        this.config = config;

        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Create main panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Current script info panel
        JPanel infoPanel = createInfoPanel("Current Script");
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 5, 5, 5);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        JLabel scriptLabel = new JLabel("Script:");
        scriptLabel.setForeground(Color.WHITE);
        scriptLabel.setFont(FontManager.getRunescapeFont());
        infoPanel.add(scriptLabel, c);

        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 2;
        currentScriptLabel = createValueLabel("None");
        infoPanel.add(currentScriptLabel, c);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        JLabel runtimeTitleLabel = new JLabel("Runtime:");
        runtimeTitleLabel.setForeground(Color.WHITE);
        runtimeTitleLabel.setFont(FontManager.getRunescapeFont());
        infoPanel.add(runtimeTitleLabel, c);

        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 2;
        runtimeLabel = createValueLabel("00:00:00");
        infoPanel.add(runtimeLabel, c);

        // Next script info panel
        JPanel nextScriptPanel = createInfoPanel("Next Scheduled Script");
        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 5, 5, 5);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        JLabel nextScriptTitleLabel = new JLabel("Script:");
        nextScriptTitleLabel.setForeground(Color.WHITE);
        nextScriptTitleLabel.setFont(FontManager.getRunescapeFont());
        nextScriptPanel.add(nextScriptTitleLabel, c);

        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 2;
        nextScriptNameLabel = createValueLabel("None");
        nextScriptPanel.add(nextScriptNameLabel, c);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        JLabel nextRunLabel = new JLabel("Next Run:");
        nextRunLabel.setForeground(Color.WHITE);
        nextRunLabel.setFont(FontManager.getRunescapeFont());
        nextScriptPanel.add(nextRunLabel, c);

        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 2;
        nextScriptTimeLabel = createValueLabel("--:--");
        nextScriptPanel.add(nextScriptTimeLabel, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        JLabel scheduleLabel = new JLabel("Schedule:");
        scheduleLabel.setForeground(Color.WHITE);
        scheduleLabel.setFont(FontManager.getRunescapeFont());
        nextScriptPanel.add(scheduleLabel, c);

        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 2;
        nextScriptScheduleLabel = createValueLabel("None");
        nextScriptPanel.add(nextScriptScheduleLabel, c);

        // Button panel
        JPanel buttonPanel = new JPanel(new GridLayout(1, 1, 0, 0));
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttonPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        openConfigButton = createButton("Open Scheduler Config", ColorScheme.BRAND_ORANGE);
        openConfigButton.addActionListener(this::onOpenConfigButtonClicked);
        buttonPanel.add(openConfigButton);

        // Add components to main panel
        mainPanel.add(infoPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(nextScriptPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(buttonPanel);

        add(mainPanel, BorderLayout.NORTH);
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
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        return panel;
    }

    private JLabel createValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeFont());
        return label;
    }

    private JButton createButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 1),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));

        // Add hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.brighter());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });

        return button;
    }

    public void updateScripts() {
        // This method is called from the plugin to update script lists
        updateScriptInfo();
        updateNextScriptInfo();
    }

    public void updateScriptInfo() {
        String currentScriptName = plugin.getCurrentScriptName();
        LocalDateTime startTime = plugin.getScriptStartTime();

        if (currentScriptName != null && !currentScriptName.isEmpty()) {
            currentScriptLabel.setText(currentScriptName);

            if (startTime != null) {
                Duration runtime = Duration.between(startTime, LocalDateTime.now());
                long hours = runtime.toHours();
                long minutes = runtime.toMinutesPart();
                long seconds = runtime.toSecondsPart();
                runtimeLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
            } else {
                runtimeLabel.setText("00:00:00");
            }
        } else {
            currentScriptLabel.setText("None");
            runtimeLabel.setText("00:00:00");
        }
    }

    public void updateNextScriptInfo() {
        ScheduledScript nextScript = plugin.getNextScheduledScript();

        if (nextScript != null) {
            nextScriptNameLabel.setText(nextScript.getScriptName());

            // Calculate the next run time
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextRunTime = nextScript.getNextRunTime(now);

            if (nextRunTime != null) {
                String timeDisplay = nextRunTime.format(TIME_FORMATTER);
                nextScriptTimeLabel.setText(timeDisplay);
            } else {
                nextScriptTimeLabel.setText("--:--");
            }

            // Format the schedule description
            String scheduleDesc = nextScript.getIntervalDisplay();
            if (nextScript.getDuration() != null && !nextScript.getDuration().isEmpty()) {
                scheduleDesc += " for " + nextScript.getDuration();
            }
            nextScriptScheduleLabel.setText(scheduleDesc);
        } else {
            nextScriptNameLabel.setText("None");
            nextScriptTimeLabel.setText("--:--");
            nextScriptScheduleLabel.setText("None");
        }
    }

    private void onOpenConfigButtonClicked(ActionEvent e) {
        plugin.openSchedulerWindow();
    }
}
