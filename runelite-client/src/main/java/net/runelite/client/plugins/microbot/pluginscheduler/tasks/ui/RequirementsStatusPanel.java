package net.runelite.client.plugins.microbot.pluginscheduler.tasks.ui;

import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.registry.RequirementRegistry;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.state.TaskExecutionState;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;

/**
 * A UI component for displaying the status of pre/post schedule requirements.
 * Shows requirement counts, fulfillment status, and progress information.
 * Enhanced with context awareness to display requirements specific to current execution phase.
 */
public class RequirementsStatusPanel extends JPanel {
    
    private final JLabel totalRequirementsLabel;
    private final JLabel fulfilledRequirementsLabel;
    private final JLabel mandatoryRequirementsLabel;
    private final JLabel optionalRequirementsLabel;
    private final JProgressBar fulfillmentProgressBar;
    private final JLabel currentRequirementLabel;
    private final JLabel phaseLabel;
    
    // State tracking
    private PrePostScheduleRequirements lastRequirements;
    private int lastTotalRequirements = 0;
    private int lastFulfilledRequirements = 0;
    private TaskExecutionState lastExecutionState;
    private TaskExecutionState.ExecutionPhase lastPhase;
    
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
        
        // Phase row - shows current execution phase
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
        phaseLabel.setForeground(Color.CYAN);
        contentPanel.add(phaseLabel, gbc);
        
        // Total requirements row
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
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
        gbc.gridx = 0; gbc.gridy = 2;
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
        gbc.gridx = 0; gbc.gridy = 3;
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
        gbc.gridx = 0; gbc.gridy = 4;
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
        
        // Current requirement row - shows what's being processed
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel currentTitle = new JLabel("Current:");
        currentTitle.setFont(FontManager.getRunescapeSmallFont());
        currentTitle.setForeground(Color.LIGHT_GRAY);
        contentPanel.add(currentTitle, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        currentRequirementLabel = new JLabel("None");
        currentRequirementLabel.setFont(FontManager.getRunescapeSmallFont());
        currentRequirementLabel.setForeground(Color.ORANGE);
        contentPanel.add(currentRequirementLabel, gbc);
        
        // Progress bar row
        gbc.gridx = 0; gbc.gridy = 6;
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
     * Updates the panel with the current requirements status and execution state
     */
    public void updateRequirements(PrePostScheduleRequirements requirements) {
        updateRequirements(requirements, null);
    }
    
    /**
     * Enhanced update method that includes TaskExecutionState for progress tracking
     */
    public void updateRequirements(PrePostScheduleRequirements requirements, TaskExecutionState executionState) {
        if (requirements == null) {
            setVisible(false);
            return;
        }
        
        boolean hasChanges = false;
        
        // Update execution phase display
        if (executionState != null) {
            TaskExecutionState.ExecutionPhase currentPhase = executionState.getCurrentPhase();
            if (currentPhase != lastPhase) {
                phaseLabel.setText(currentPhase.getDisplayName());
                
                // Color-code the phase
                switch (currentPhase) {
                    case PRE_SCHEDULE:
                        phaseLabel.setForeground(Color.CYAN);
                        break;
                    case MAIN_EXECUTION:
                        phaseLabel.setForeground(Color.GREEN);
                        break;
                    case POST_SCHEDULE:
                        phaseLabel.setForeground(Color.ORANGE);
                        break;
                    default:
                        phaseLabel.setForeground(Color.LIGHT_GRAY);
                        break;
                }
                
                lastPhase = currentPhase;
                hasChanges = true;
            }
            
            // Update current requirement display
            String currentRequirementName = executionState.getCurrentRequirementName();
            int currentIndex = executionState.getCurrentRequirementIndex();
            int totalInStep = executionState.getTotalRequirementsInStep();
            
            if (currentRequirementName != null && totalInStep > 0) {
                currentRequirementLabel.setText(String.format("%s (%d/%d)", 
                    currentRequirementName, currentIndex, totalInStep));
                currentRequirementLabel.setForeground(Color.ORANGE);
            } else if (currentRequirementName != null) {
                currentRequirementLabel.setText(currentRequirementName);
                currentRequirementLabel.setForeground(Color.ORANGE);
            } else {
                currentRequirementLabel.setText("None");
                currentRequirementLabel.setForeground(Color.LIGHT_GRAY);
            }
            
            lastExecutionState = executionState;
        } else {
            // No execution state - show idle
            phaseLabel.setText("Idle");
            phaseLabel.setForeground(Color.LIGHT_GRAY);
            currentRequirementLabel.setText("None");
            currentRequirementLabel.setForeground(Color.LIGHT_GRAY);
        }
        
        // Get requirement registry
        RequirementRegistry registry = requirements.getRegistry();
        if (registry == null) {
            setVisible(false);
            return;
        }
        
        // Determine which requirements to show based on current execution phase
        List<Requirement> relevantRequirements;
        if (executionState != null) {
            TaskExecutionState.ExecutionPhase currentPhase = executionState.getCurrentPhase();
            
            // Filter requirements based on current execution phase
            if (currentPhase == TaskExecutionState.ExecutionPhase.PRE_SCHEDULE) {
                // Show PRE_SCHEDULE and BOTH requirements
                relevantRequirements = registry.getRequirements(TaskContext.PRE_SCHEDULE).stream()
                    .collect(ArrayList::new, (list, req) -> {
                        if (!list.contains(req)) list.add(req);
                    }, ArrayList::addAll);
                
                registry.getExternalRequirements(TaskContext.PRE_SCHEDULE).stream()
                    .filter(req -> !relevantRequirements.contains(req))
                    .forEach(relevantRequirements::add);
                
                // Add BOTH requirements, excluding duplicates
                registry.getRequirements(TaskContext.BOTH).stream()
                    .filter(req -> !relevantRequirements.contains(req))
                    .forEach(relevantRequirements::add);
                    
                registry.getExternalRequirements(TaskContext.BOTH).stream()
                    .filter(req -> !relevantRequirements.contains(req))
                    .forEach(relevantRequirements::add);
                    
            } else if (currentPhase == TaskExecutionState.ExecutionPhase.POST_SCHEDULE) {
                // Show POST_SCHEDULE and BOTH requirements
                relevantRequirements = registry.getRequirements(TaskContext.POST_SCHEDULE).stream()
                    .collect(ArrayList::new, (list, req) -> {
                        if (!list.contains(req)) list.add(req);
                    }, ArrayList::addAll);
                
                registry.getExternalRequirements(TaskContext.POST_SCHEDULE).stream()
                    .filter(req -> !relevantRequirements.contains(req))
                    .forEach(relevantRequirements::add);
                
                // Add BOTH requirements, excluding duplicates
                registry.getRequirements(TaskContext.BOTH).stream()
                    .filter(req -> !relevantRequirements.contains(req))
                    .forEach(relevantRequirements::add);
                    
                registry.getExternalRequirements(TaskContext.BOTH).stream()
                    .filter(req -> !relevantRequirements.contains(req))
                    .forEach(relevantRequirements::add);
                    
            } else {
                // For IDLE or MAIN_EXECUTION, show all requirements
                relevantRequirements = registry.getAllRequirements().stream()
                    .collect(ArrayList::new, (list, req) -> {
                        if (!list.contains(req)) list.add(req);
                    }, ArrayList::addAll);
                
                // Add external requirements for all contexts, excluding duplicates
                java.util.Arrays.stream(TaskContext.values())
                    .flatMap(context -> registry.getExternalRequirements(context).stream())
                    .filter(req -> !relevantRequirements.contains(req))
                    .forEach(relevantRequirements::add);
            }
        } else {
            // No execution state - show all requirements
            relevantRequirements = registry.getAllRequirements().stream()
                .collect(ArrayList::new, (list, req) -> {
                    if (!list.contains(req)) list.add(req);
                }, ArrayList::addAll);
            
            // Add external requirements for all contexts, excluding duplicates
            java.util.Arrays.stream(TaskContext.values())
                .flatMap(context -> registry.getExternalRequirements(context).stream())
                .filter(req -> !relevantRequirements.contains(req))
                .forEach(relevantRequirements::add);
        }
        
        // Calculate requirement counts based on filtered requirements
        int totalRequirements = relevantRequirements.size();
        int mandatoryCount = (int) relevantRequirements.stream()
            .filter(req -> req.getPriority() == RequirementPriority.MANDATORY)
            .count();
        int optionalCount = totalRequirements - mandatoryCount;
        
        // Calculate fulfilled count from execution state progress
        int fulfilledCount = 0;
        if (executionState != null) {
            // Use execution state step progress to calculate fulfillment
            int currentStepNumber = executionState.getCurrentStepNumber();
            int totalSteps = executionState.getTotalSteps();
            int currentRequirementIndex = executionState.getCurrentRequirementIndex();
            int totalRequirementsInStep = executionState.getTotalRequirementsInStep();
            
            if (totalSteps > 0 && currentStepNumber > 0) {
                // Calculate approximate fulfillment based on step progress
                double stepProgress = (double) (currentStepNumber - 1) / totalSteps;
                
                // Add progress within current step if available
                if (totalRequirementsInStep > 0 && currentRequirementIndex > 0) {
                    double stepCompletionRatio = (double) currentRequirementIndex / totalRequirementsInStep;
                    stepProgress += stepCompletionRatio / totalSteps;
                }
                
                fulfilledCount = (int) Math.round(stepProgress * totalRequirements);
                fulfilledCount = Math.min(fulfilledCount, totalRequirements); // Cap at total
            }
        }
        
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
        
        // Update progress bar color based on fulfillment state
        if (percentage == 100) {
            fulfillmentProgressBar.setForeground(Color.GREEN);
        } else if (percentage > 0) {
            fulfillmentProgressBar.setForeground(ColorScheme.BRAND_ORANGE);
        } else {
            fulfillmentProgressBar.setForeground(Color.RED);
        }
        
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
        phaseLabel.setText("Idle");
        phaseLabel.setForeground(Color.LIGHT_GRAY);
        totalRequirementsLabel.setText("0");
        fulfilledRequirementsLabel.setText("0");
        mandatoryRequirementsLabel.setText("0");
        optionalRequirementsLabel.setText("0");
        currentRequirementLabel.setText("None");
        currentRequirementLabel.setForeground(Color.LIGHT_GRAY);
        fulfillmentProgressBar.setValue(0);
        fulfillmentProgressBar.setString("0%");
        fulfillmentProgressBar.setForeground(Color.RED);
        
        lastRequirements = null;
        lastTotalRequirements = 0;
        lastFulfilledRequirements = 0;
        lastExecutionState = null;
        lastPhase = null;
        
        setVisible(false);
        repaint();
    }
}
