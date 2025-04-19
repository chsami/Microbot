package net.runelite.client.plugins.microbot.pluginscheduler.ui;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;

/**
 * Utility class for consistent UI styling
 */
public class SchedulerUIUtils {

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