package net.runelite.client.plugins.microbot.pluginscheduler.ui.components;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;

/**
 * A reusable panel component for configuring an initial delay with hours, minutes, and seconds spinners.
 */
public class InitialDelayPanel extends JPanel {
    private final JCheckBox initialDelayCheckBox;
    private final JSpinner hoursSpinner;
    private final JSpinner minutesSpinner;
    private final JSpinner secondsSpinner;

    /**
     * Creates a new InitialDelayPanel with default values.
     */
    public InitialDelayPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        initialDelayCheckBox = new JCheckBox("Initial Delay");
        initialDelayCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        initialDelayCheckBox.setForeground(Color.WHITE);
        add(initialDelayCheckBox);

        // Hours spinner
        SpinnerNumberModel hoursModel = new SpinnerNumberModel(0, 0, 23, 1);
        hoursSpinner = new JSpinner(hoursModel);
        hoursSpinner.setPreferredSize(new Dimension(60, hoursSpinner.getPreferredSize().height));
        hoursSpinner.setEnabled(false);
        add(hoursSpinner);

        JLabel hoursLabel = new JLabel("hr");
        hoursLabel.setForeground(Color.WHITE);
        add(hoursLabel);

        // Minutes spinner
        SpinnerNumberModel minutesModel = new SpinnerNumberModel(0, 0, 59, 1);
        minutesSpinner = new JSpinner(minutesModel);
        minutesSpinner.setPreferredSize(new Dimension(60, minutesSpinner.getPreferredSize().height));
        minutesSpinner.setEnabled(false);
        add(minutesSpinner);

        JLabel minutesLabel = new JLabel("min");
        minutesLabel.setForeground(Color.WHITE);
        add(minutesLabel);

        // Seconds spinner
        SpinnerNumberModel secondsModel = new SpinnerNumberModel(0, 0, 59, 1);
        secondsSpinner = new JSpinner(secondsModel);
        secondsSpinner.setPreferredSize(new Dimension(60, secondsSpinner.getPreferredSize().height));
        secondsSpinner.setEnabled(false);
        add(secondsSpinner);

        JLabel secondsLabel = new JLabel("sec");
        secondsLabel.setForeground(Color.WHITE);
        add(secondsLabel);

        // Enable/disable spinners based on checkbox
        initialDelayCheckBox.addActionListener(e -> {
            boolean selected = initialDelayCheckBox.isSelected();
            hoursSpinner.setEnabled(selected);
            minutesSpinner.setEnabled(selected);
            secondsSpinner.setEnabled(selected);
        });

        // Add overflow logic for spinners
        addSpinnerOverflowLogic();
    }

    private void addSpinnerOverflowLogic() {
        // Seconds overflow
        secondsSpinner.addChangeListener(e -> {
            int seconds = (int) secondsSpinner.getValue();
            if (seconds > 59) {
                secondsSpinner.setValue(0);
                minutesSpinner.setValue((int) minutesSpinner.getValue() + 1);
            }
        });

        // Minutes overflow
        minutesSpinner.addChangeListener(e -> {
            int minutes = (int) minutesSpinner.getValue();
            if (minutes > 59) {
                minutesSpinner.setValue(0);
                hoursSpinner.setValue((int) hoursSpinner.getValue() + 1);
            }
        });
    }

    /**
     * Gets whether the initial delay is enabled.
     *
     * @return True if enabled, false otherwise.
     */
    public boolean isInitialDelayEnabled() {
        return initialDelayCheckBox.isSelected();
    }

    /**
     * Gets the configured hours.
     *
     * @return The hours value.
     */
    public int getHours() {
        return (int) hoursSpinner.getValue();
    }

    /**
     * Gets the configured minutes.
     *
     * @return The minutes value.
     */
    public int getMinutes() {
        return (int) minutesSpinner.getValue();
    }

    /**
     * Gets the configured seconds.
     *
     * @return The seconds value.
     */
    public int getSeconds() {
        return (int) secondsSpinner.getValue();
    }

    /**
     * Gets the hours spinner component.
     *
     * @return The hours spinner.
     */
    public JSpinner getHoursSpinner() {
        return hoursSpinner;
    }

    /**
     * Gets the minutes spinner component.
     *
     * @return The minutes spinner.
     */
    public JSpinner getMinutesSpinner() {
        return minutesSpinner;
    }

    /**
     * Gets the seconds spinner component.
     *
     * @return The seconds spinner.
     */
    public JSpinner getSecondsSpinner() {
        return secondsSpinner;
    }
}
