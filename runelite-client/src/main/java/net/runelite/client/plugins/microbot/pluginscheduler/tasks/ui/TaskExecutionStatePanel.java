package net.runelite.client.plugins.microbot.pluginscheduler.tasks.ui;

import net.runelite.client.plugins.microbot.pluginscheduler.tasks.state.TaskExecutionState;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * A reusable UI component for displaying the current state of pre/post schedule task execution.
 * This panel shows the current phase, execution state, progress, and any error information.
 */
public class TaskExecutionStatePanel extends JPanel {
    
    private final JLabel phaseLabel;
    private final JLabel stateLabel;
    private final JLabel detailsLabel;
    private final JProgressBar progressBar;
    private final JLabel progressLabel;
    private final JLabel currentRequirementLabel;
    private final JLabel errorLabel;
    
    // State tracking for optimized updates
    private TaskExecutionState.ExecutionPhase lastPhase;
    private TaskExecutionState.ExecutionState lastState;
    private String lastDetails;
    private int lastCurrentStep;
    private int lastTotalSteps;
    private boolean lastHasError;
    private String lastErrorMessage;
    private String lastCurrentRequirementName;
    
    public TaskExecutionStatePanel(String title) {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE, 1),
                title,
                TitledBorder.CENTER,
                TitledBorder.TOP,
                FontManager.getRunescapeSmallFont(),
                Color.WHITE
            ),
            new EmptyBorder(5, 5, 5, 5)
        ));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setOpaque(true);
        
        // Create main content panel
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        contentPanel.setOpaque(true);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Phase row
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0.0;
        JLabel phaseTitle = new JLabel("Phase:");
        phaseTitle.setFont(FontManager.getRunescapeSmallFont());
        phaseTitle.setForeground(Color.LIGHT_GRAY);
        contentPanel.add(phaseTitle, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        phaseLabel = new JLabel("Idle");
        phaseLabel.setFont(FontManager.getRunescapeSmallFont());
        phaseLabel.setForeground(Color.WHITE);
        contentPanel.add(phaseLabel, gbc);
        
        // State row
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel stateTitle = new JLabel("State:");
        stateTitle.setFont(FontManager.getRunescapeSmallFont());
        stateTitle.setForeground(Color.LIGHT_GRAY);
        contentPanel.add(stateTitle, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        stateLabel = new JLabel("Starting");
        stateLabel.setFont(FontManager.getRunescapeSmallFont());
        stateLabel.setForeground(Color.WHITE);
        contentPanel.add(stateLabel, gbc);
        
        // Progress bar row
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0 / 0");
        progressBar.setFont(FontManager.getRunescapeSmallFont());
        progressBar.setForeground(ColorScheme.BRAND_ORANGE);
        progressBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        contentPanel.add(progressBar, gbc);
        
        // Progress label row
        gbc.gridy = 3;
        progressLabel = new JLabel("No progress");
        progressLabel.setFont(FontManager.getRunescapeSmallFont());
        progressLabel.setForeground(Color.LIGHT_GRAY);
        contentPanel.add(progressLabel, gbc);
        
        // Current requirement row
        gbc.gridy = 4;
        currentRequirementLabel = new JLabel("");
        currentRequirementLabel.setFont(FontManager.getRunescapeSmallFont());
        currentRequirementLabel.setForeground(Color.CYAN);
        contentPanel.add(currentRequirementLabel, gbc);
        
        // Details row
        gbc.gridy = 5;
        detailsLabel = new JLabel("");
        detailsLabel.setFont(FontManager.getRunescapeSmallFont());
        detailsLabel.setForeground(Color.WHITE);
        contentPanel.add(detailsLabel, gbc);
        
        // Error row
        gbc.gridy = 6;
        errorLabel = new JLabel("");
        errorLabel.setFont(FontManager.getRunescapeSmallFont());
        errorLabel.setForeground(Color.RED);
        contentPanel.add(errorLabel, gbc);
        
        add(contentPanel, BorderLayout.CENTER);
        
        // Set initial visibility
        setVisible(false);
    }
    
    /**
     * Updates the panel with the current task execution state.
     * Only redraws components that have actually changed for performance.
     */
    public void updateState(TaskExecutionState state) {
        if (state == null) {
            setVisible(false);
            return;
        }
        
        boolean hasChanges = false;
        
        // Check phase changes
        TaskExecutionState.ExecutionPhase currentPhase = state.getCurrentPhase();
        if (currentPhase != lastPhase) {
            phaseLabel.setText(currentPhase.getDisplayName());
            lastPhase = currentPhase;
            hasChanges = true;
        }
        
        // Check state changes
        TaskExecutionState.ExecutionState currentState = state.getCurrentState();
        if (currentState != lastState) {
            stateLabel.setText(currentState.getDisplayName());
            lastState = currentState;
            hasChanges = true;
        }
        
        // Check details changes
        String currentDetails = state.getCurrentDetails();
        if (!java.util.Objects.equals(currentDetails, lastDetails)) {
            detailsLabel.setText(currentDetails != null ? currentDetails : "");
            lastDetails = currentDetails;
            hasChanges = true;
        }
        
        // Check progress changes
        int currentStep = state.getCurrentStepNumber();
        int totalSteps = state.getTotalSteps();
        if (currentStep != lastCurrentStep || totalSteps != lastTotalSteps) {
            updateProgressBar(currentStep, totalSteps);
            lastCurrentStep = currentStep;
            lastTotalSteps = totalSteps;
            hasChanges = true;
        }
        
        // Check current requirement changes
        String currentRequirementName = state.getCurrentRequirementName();
        if (!java.util.Objects.equals(currentRequirementName, lastCurrentRequirementName)) {
            updateCurrentRequirement(currentRequirementName, state.getCurrentRequirementIndex(), state.getTotalRequirementsInStep());
            lastCurrentRequirementName = currentRequirementName;
            hasChanges = true;
        }
        
        // Check error state changes
        boolean hasError = state.isHasError();
        String errorMessage = state.getErrorMessage();
        if (hasError != lastHasError || !java.util.Objects.equals(errorMessage, lastErrorMessage)) {
            updateErrorDisplay(hasError, errorMessage);
            lastHasError = hasError;
            lastErrorMessage = errorMessage;
            hasChanges = true;
        }
        
        // Show panel if it was hidden and we have actual execution happening
        if (!isVisible() && currentPhase != TaskExecutionState.ExecutionPhase.IDLE) {
            setVisible(true);
            hasChanges = true;
        }
        
        // Hide panel if back to idle
        if (isVisible() && currentPhase == TaskExecutionState.ExecutionPhase.IDLE) {
            setVisible(false);
            hasChanges = true;
        }
        
        // Repaint if there were changes
        if (hasChanges) {
            repaint();
        }
    }
    
    private void updateProgressBar(int current, int total) {
        if (total > 0) {
            int percentage = (int) ((current / (double) total) * 100);
            progressBar.setValue(percentage);
            progressBar.setString(current + " / " + total);
            progressLabel.setText(String.format("Step %d of %d", current, total));
        } else {
            progressBar.setValue(0);
            progressBar.setString("0 / 0");
            progressLabel.setText("No steps defined");
        }
    }
    
    private void updateCurrentRequirement(String requirementName, int requirementIndex, int totalRequirements) {
        if (requirementName != null && !requirementName.isEmpty()) {
            if (totalRequirements > 0) {
                currentRequirementLabel.setText(String.format("Requirement: %s (%d/%d)", 
                    requirementName, requirementIndex + 1, totalRequirements));
            } else {
                currentRequirementLabel.setText("Requirement: " + requirementName);
            }
            currentRequirementLabel.setVisible(true);
        } else {
            currentRequirementLabel.setText("");
            currentRequirementLabel.setVisible(false);
        }
    }
    
    private void updateErrorDisplay(boolean hasError, String errorMessage) {
        if (hasError && errorMessage != null && !errorMessage.isEmpty()) {
            errorLabel.setText("Error: " + errorMessage);
            errorLabel.setVisible(true);
        } else {
            errorLabel.setText("");
            errorLabel.setVisible(false);
        }
    }
    
    /**
     * Resets the panel to its initial state
     */
    public void reset() {
        phaseLabel.setText("Idle");
        stateLabel.setText("Starting");
        detailsLabel.setText("");
        progressBar.setValue(0);
        progressBar.setString("0 / 0");
        progressLabel.setText("No progress");
        currentRequirementLabel.setText("");
        currentRequirementLabel.setVisible(false);
        errorLabel.setText("");
        errorLabel.setVisible(false);
        
        // Reset state tracking
        lastPhase = null;
        lastState = null;
        lastDetails = null;
        lastCurrentStep = 0;
        lastTotalSteps = 0;
        lastHasError = false;
        lastErrorMessage = null;
        lastCurrentRequirementName = null;
        
        setVisible(false);
        repaint();
    }
}
