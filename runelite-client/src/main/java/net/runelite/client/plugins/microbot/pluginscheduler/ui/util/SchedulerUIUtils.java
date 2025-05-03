package net.runelite.client.plugins.microbot.pluginscheduler.ui.util;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerConfig;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerState;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for consistent UI styling
 */
@Slf4j
public class SchedulerUIUtils {
    /**
     * Shows a dialog when user attempts to log into a members world without membership.
     * The dialog runs on a separate thread to avoid blocking the client thread.
     * 
     * @param currentPlugin The current plugin being run, can be null
     * @param config The scheduler configuration
     * @param resultCallback A callback that receives the dialog result:
     *        - true if the user chose to switch to free worlds
     *        - false if the user chose not to switch or the dialog timed out
     */
    public static void showNonMemberWorldDialog(PluginScheduleEntry currentPlugin, SchedulerConfig config, Consumer<Boolean> resultCallback) {
        Microbot.getClientThread().runOnSeperateThread(() -> {
            // Create dialog with timeout
            final JOptionPane optionPane = new JOptionPane(
                    "You do not have membership and tried to log into a members world.\n" +
                    "Would you like to switch to free worlds for this login?",
                    JOptionPane.QUESTION_MESSAGE,
                    JOptionPane.YES_NO_OPTION);

            final JDialog dialog = optionPane.createDialog("Membership Required");

            // Create timer for dialog timeout
            int timeoutSeconds = config.dialogTimeoutSeconds();
            if (timeoutSeconds <= 0) {
                timeoutSeconds = 30; // Default timeout if config value is invalid
            }

            final Timer timer = new Timer(timeoutSeconds * 1000, e -> {
                dialog.setVisible(false);
                dialog.dispose();
            });
            timer.setRepeats(false);
            timer.start();

            // Update dialog title to show countdown
            final int finalTimeoutSeconds = timeoutSeconds;
            final Timer countdownTimer = new Timer(1000, new ActionListener() {
                int remainingSeconds = finalTimeoutSeconds;

                @Override
                public void actionPerformed(ActionEvent e) {
                    remainingSeconds--;
                    if (remainingSeconds > 0) {
                        dialog.setTitle("Membership Required (Timeout: " + remainingSeconds + "s)");
                    } else {
                        dialog.setTitle("Membership Required (Timing out...)");
                    }
                }
            });
            countdownTimer.start();

            try {
                dialog.setVisible(true); // blocks until dialog is closed or timer expires
            } finally {
                timer.stop();
                countdownTimer.stop();
            }

            // Handle user choice or timeout
            Object selectedValue = optionPane.getValue();
            int result = selectedValue instanceof Integer ? (Integer) selectedValue : JOptionPane.CLOSED_OPTION;

            if (result == JOptionPane.YES_OPTION) {
                // User wants to switch to free worlds
                if (Microbot.getConfigManager() != null) {
                    Microbot.getConfigManager().setConfiguration("AutoLoginConfig", "World",
                            Login.getRandomWorld(false));
                    Microbot.getConfigManager().setConfiguration("PluginScheduler", "worldType", 0);
                }
                // Notify caller that user chose to switch to free worlds
                resultCallback.accept(true);
            } else {
                // User chose not to switch to free worlds or dialog timed out
                log.info("Login to member world canceled");
                // Notify caller that user chose not to switch or timed out
                resultCallback.accept(false);
            }
            return null;
        });
    }
    
    /**
     * Applies consistent styling to a JComboBox
     */
    public static <T> void styleComboBox(JComboBox<T> comboBox) {
        // Set colors
        comboBox.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        comboBox.setForeground(Color.WHITE);
        comboBox.setFont(comboBox.getFont().deriveFont(Font.PLAIN));
        
        // Add a visible border to make it stand out
        comboBox.setBorder(new CompoundBorder(
            new LineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(2, 4, 2, 0)
        ));
        
        // Custom renderer for dropdown items
        comboBox.setRenderer(new BasicComboBoxRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, 
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                if (isSelected) {
                    setBackground(ColorScheme.BRAND_ORANGE);
                    setForeground(Color.WHITE);
                } else {
                    setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
                    setForeground(Color.WHITE);
                }
                
                // Add some padding
                setBorder(new EmptyBorder(2, 5, 2, 5));
                return this;
            }
        });
        
        // Use a custom UI to improve the arrow button appearance
        comboBox.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton button = super.createArrowButton();
                button.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
                button.setBorder(BorderFactory.createEmptyBorder());
                return button;
            }
        });
    }
}