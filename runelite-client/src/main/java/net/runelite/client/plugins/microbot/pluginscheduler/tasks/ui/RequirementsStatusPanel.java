package net.runelite.client.plugins.microbot.pluginscheduler.tasks.ui;

import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.registry.RequirementRegistry;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * A UI component for displaying the status of pre/post schedule requirements.
 * Shows requirement counts, fulfillment status, and progress information.
 */
public class RequirementsStatusPanel extends JPanel {
    
    private final JLabel totalRequirementsLabel;
    private final JLabel fulfilledRequirementsLabel;
    private final JLabel mandatoryRequirementsLabel;
    private final JLabel optionalRequirementsLabel;
    private final JProgressBar fulfillmentProgressBar;
    
    // State tracking
    private PrePostScheduleRequirements lastRequirements;
    private int lastTotalRequirements = 0;
    private int lastFulfilledRequirements = 0;
    
    public RequirementsStatusPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE, 1),
                "Requirements Status",
                TitledBorder.CENTER,
                TitledBorder.TOP,
                FontManager.getRunescapeSmallFont(),
                Color.WHITE
            ),
            new EmptyBorder(5, 5, 5, 5)
        ));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setOpaque(true);
        
        // Create content panel
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        contentPanel.setOpaque(true);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Total requirements row
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0.0;
        JLabel totalTitle = new JLabel("Total:");
        totalTitle.setFont(FontManager.getRunescapeSmallFont());
        totalTitle.setForeground(Color.LIGHT_GRAY);
        contentPanel.add(totalTitle, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        totalRequirementsLabel = new JLabel("0");
        totalRequirementsLabel.setFont(FontManager.getRunescapeSmallFont());
        totalRequirementsLabel.setForeground(Color.WHITE);
        contentPanel.add(totalRequirementsLabel, gbc);
        
        // Fulfilled requirements row
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel fulfilledTitle = new JLabel("Fulfilled:");
        fulfilledTitle.setFont(FontManager.getRunescapeSmallFont());
        fulfilledTitle.setForeground(Color.LIGHT_GRAY);
        contentPanel.add(fulfilledTitle, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        fulfilledRequirementsLabel = new JLabel("0");
        fulfilledRequirementsLabel.setFont(FontManager.getRunescapeSmallFont());
        fulfilledRequirementsLabel.setForeground(Color.GREEN);
        contentPanel.add(fulfilledRequirementsLabel, gbc);
        
        // Mandatory requirements row
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel mandatoryTitle = new JLabel("Mandatory:");
        mandatoryTitle.setFont(FontManager.getRunescapeSmallFont());
        mandatoryTitle.setForeground(Color.LIGHT_GRAY);
        contentPanel.add(mandatoryTitle, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mandatoryRequirementsLabel = new JLabel("0");
        mandatoryRequirementsLabel.setFont(FontManager.getRunescapeSmallFont());
        mandatoryRequirementsLabel.setForeground(Color.RED);
        contentPanel.add(mandatoryRequirementsLabel, gbc);
        
        // Optional requirements row
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel optionalTitle = new JLabel("Optional:");
        optionalTitle.setFont(FontManager.getRunescapeSmallFont());
        optionalTitle.setForeground(Color.LIGHT_GRAY);
        contentPanel.add(optionalTitle, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        optionalRequirementsLabel = new JLabel("0");
        optionalRequirementsLabel.setFont(FontManager.getRunescapeSmallFont());
        optionalRequirementsLabel.setForeground(Color.YELLOW);
        contentPanel.add(optionalRequirementsLabel, gbc);
        
        // Progress bar row
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        fulfillmentProgressBar = new JProgressBar(0, 100);
        fulfillmentProgressBar.setStringPainted(true);
        fulfillmentProgressBar.setString("0%");
        fulfillmentProgressBar.setFont(FontManager.getRunescapeSmallFont());
        fulfillmentProgressBar.setForeground(ColorScheme.BRAND_ORANGE);
        fulfillmentProgressBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        contentPanel.add(fulfillmentProgressBar, gbc);
        
        add(contentPanel, BorderLayout.CENTER);
        
        // Initially hidden
        setVisible(false);
    }
    
    /**
     * Updates the panel with the current requirements status
     */
    public void updateRequirements(PrePostScheduleRequirements requirements) {
        if (requirements == null) {
            setVisible(false);
            return;
        }
        
        boolean hasChanges = false;
        
        // Get requirement registry
        RequirementRegistry registry = requirements.getRegistry();
        if (registry == null) {
            setVisible(false);
            return;
        }
        
        // Calculate requirement counts
        int totalRequirements = registry.getAllRequirements().size();
        int mandatoryCount = (int) registry.getAllRequirements().stream()
            .filter(req -> req.getPriority() == net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority.MANDATORY)
            .count();
        int optionalCount = totalRequirements - mandatoryCount;
        
        // TODO: Calculate fulfilled count - this would require checking fulfillment state
        // For now, we'll use a placeholder
        int fulfilledCount = 0; // This should be calculated based on actual fulfillment state
        
        // Update total requirements
        if (totalRequirements != lastTotalRequirements) {
            totalRequirementsLabel.setText(String.valueOf(totalRequirements));
            lastTotalRequirements = totalRequirements;
            hasChanges = true;
        }
        
        // Update fulfilled requirements
        if (fulfilledCount != lastFulfilledRequirements) {
            fulfilledRequirementsLabel.setText(String.valueOf(fulfilledCount));
            lastFulfilledRequirements = fulfilledCount;
            hasChanges = true;
        }
        
        // Update mandatory count
        mandatoryRequirementsLabel.setText(String.valueOf(mandatoryCount));
        
        // Update optional count
        optionalRequirementsLabel.setText(String.valueOf(optionalCount));
        
        // Update progress bar
        int percentage = totalRequirements > 0 ? (int) ((fulfilledCount / (double) totalRequirements) * 100) : 0;
        fulfillmentProgressBar.setValue(percentage);
        fulfillmentProgressBar.setString(percentage + "%");
        
        lastRequirements = requirements;
        setVisible(true);
        
        if (hasChanges) {
            repaint();
        }
    }
    
    /**
     * Clears the panel and hides it
     */
    public void clear() {
        totalRequirementsLabel.setText("0");
        fulfilledRequirementsLabel.setText("0");
        mandatoryRequirementsLabel.setText("0");
        optionalRequirementsLabel.setText("0");
        fulfillmentProgressBar.setValue(0);
        fulfillmentProgressBar.setString("0%");
        
        lastRequirements = null;
        lastTotalRequirements = 0;
        lastFulfilledRequirements = 0;
        
        setVisible(false);
        repaint();
    }
}
