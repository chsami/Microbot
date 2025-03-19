package net.runelite.client.plugins.microbot.scriptscheduler;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ScriptSchedulerPanel extends PluginPanel {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ScriptSchedulerPlugin plugin;
    private final ScriptSchedulerConfig config;

    private JLabel currentScriptLabel;
    private JLabel runtimeLabel;
    private JLabel nextScriptNameLabel;
    private JLabel nextScriptTimeLabel;
    private JLabel nextScriptScheduleLabel;
    private JButton openConfigButton;
    private JButton runNowButton;
    private JButton stopScriptButton;
    private Timer updateTimer;

    public ScriptSchedulerPanel(ScriptSchedulerPlugin plugin, ScriptSchedulerConfig config) {
        super();
        this.plugin = plugin;
        this.config = config;

        setBorder(new EmptyBorder(10, 10, 10, 10));
        setLayout(new BorderLayout());

        // Create main panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // Current script info panel
        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("Current Script"));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 5, 5, 5);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        infoPanel.add(new JLabel("Script:"), c);

        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 2;
        currentScriptLabel = new JLabel("None");
        infoPanel.add(currentScriptLabel, c);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        infoPanel.add(new JLabel("Runtime:"), c);

        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 2;
        runtimeLabel = new JLabel("00:00:00");
        infoPanel.add(runtimeLabel, c);

        // Next script info panel
        JPanel nextScriptPanel = new JPanel(new GridBagLayout());
        nextScriptPanel.setBorder(BorderFactory.createTitledBorder("Next Scheduled Script"));

        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 5, 5, 5);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        nextScriptPanel.add(new JLabel("Script:"), c);

        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 2;
        nextScriptNameLabel = new JLabel("None");
        nextScriptPanel.add(nextScriptNameLabel, c);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        nextScriptPanel.add(new JLabel("Time:"), c);

        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 2;
        nextScriptTimeLabel = new JLabel("--:--");
        nextScriptPanel.add(nextScriptTimeLabel, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        nextScriptPanel.add(new JLabel("Schedule:"), c);

        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 2;
        nextScriptScheduleLabel = new JLabel("None");
        nextScriptPanel.add(nextScriptScheduleLabel, c);

        // Buttons panel
        JPanel buttonsPanel = new JPanel(new GridLayout(3, 1, 0, 5));

        openConfigButton = new JButton("Open Scheduler Config");
        openConfigButton.addActionListener(this::onOpenConfigButtonClicked);
        buttonsPanel.add(openConfigButton);

        stopScriptButton = new JButton("Stop Current Script");
        stopScriptButton.addActionListener(this::onStopScriptButtonClicked);
        stopScriptButton.setEnabled(false);
        buttonsPanel.add(stopScriptButton);

        // Add components to main panel
        mainPanel.add(infoPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(nextScriptPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(buttonsPanel);

        add(mainPanel, BorderLayout.NORTH);

        // Start timer to update runtime and next script info
        updateTimer = new Timer(1000, e -> {
            updateScriptInfo();
            updateNextScriptInfo();
        });
        updateTimer.start();
    }

    public void updateScriptListsWhenReady() {
        // This method is called from the plugin to update script lists
        // It's not needed in the panel since we don't have script lists here
    }

    private void updateScriptInfo() {
        String currentScriptName = plugin.getCurrentScriptName();
        LocalDateTime startTime = plugin.getScriptStartTime();

        if (currentScriptName != null && !currentScriptName.isEmpty()) {
            currentScriptLabel.setText(currentScriptName);
            stopScriptButton.setEnabled(true);

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
            stopScriptButton.setEnabled(false);
        }
    }

    private void updateNextScriptInfo() {
        ScheduledScript nextScript = plugin.getNextScheduledScript();

        if (nextScript != null) {
            nextScriptNameLabel.setText(nextScript.getScriptName());

            // Calculate the next run time
            LocalDateTime nextRunTime = nextScript.getNextRunTime(LocalDateTime.now());
            if (nextRunTime != null) {
                String timeDisplay = nextRunTime.format(TIME_FORMATTER);
                if (nextScript.getScheduleType() == ScheduledScript.ScheduleType.HOURLY) {
                    timeDisplay += " (hourly)";
                }
                nextScriptTimeLabel.setText(timeDisplay);
            } else {
                nextScriptTimeLabel.setText("--:--");
            }

            // Format the schedule description
            String scheduleDesc;
            switch (nextScript.getScheduleType()) {
                case HOURLY:
                    scheduleDesc = "Hourly (from " + nextScript.getStartTime() + ")";
                    break;
                case DAILY:
                    scheduleDesc = "Daily";
                    break;
                case WEEKDAYS:
                    scheduleDesc = "Weekdays";
                    break;
                case WEEKENDS:
                    scheduleDesc = "Weekends";
                    break;
                case CUSTOM:
                    scheduleDesc = "Custom";
                    break;
                default:
                    scheduleDesc = "Unknown";
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

    private void onStopScriptButtonClicked(ActionEvent e) {
        plugin.stopCurrentScript();
    }
}
