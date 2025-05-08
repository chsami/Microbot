package net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.ui;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JToggleButton;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;
import javax.swing.Box;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridLayout;
import java.awt.BorderLayout;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillLevelCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillXpCondition;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public class SkillConditionPanelUtil {
    /**
     * Creates the skill level condition configuration panel
     */
    public static void createSkillLevelConfigPanel(JPanel panel, GridBagConstraints gbc, boolean stopConditionPanel) {
        // --- Add skill selection section ---
        JComboBox<String> skillComboBox = createSkillSelector();
        addSkillSelectionSection(panel, gbc, skillComboBox);
        
        // --- Add mode selection (relative vs absolute) ---
        JRadioButton relativeButton = new JRadioButton();
        JRadioButton absoluteButton = new JRadioButton();
        JPanel modePanel = createModeSelectionPanel(relativeButton, absoluteButton);
        gbc.gridy++;
        panel.add(modePanel, gbc);
        
        // --- Target level section ---
        JLabel targetLevelLabel = new JLabel("Levels to gain:");
        targetLevelLabel.setForeground(Color.WHITE);
        targetLevelLabel.setFont(FontManager.getRunescapeSmallFont());
        
        SpinnerNumberModel levelModel = new SpinnerNumberModel(1, 1, 99, 1);
        JSpinner levelSpinner = new JSpinner(levelModel);
        levelSpinner.setPreferredSize(new Dimension(70, levelSpinner.getPreferredSize().height));
        
        JCheckBox randomizeCheckBox = new JCheckBox("Randomize");
        randomizeCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckBox.setForeground(Color.WHITE);
        
        JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        levelPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        levelPanel.add(targetLevelLabel);
        levelPanel.add(levelSpinner);
        levelPanel.add(randomizeCheckBox);
        
        gbc.gridy++;
        panel.add(levelPanel, gbc);
        
        // --- Min/Max level panel ---
        JPanel minMaxPanel = createMinMaxPanel("Min Level:", "Max Level:", 1, 99, 1);
        minMaxPanel.setVisible(false);
        gbc.gridy++;
        panel.add(minMaxPanel, gbc);

        // Save references to the spinners
        JSpinner minSpinner = (JSpinner) minMaxPanel.getClientProperty("minSpinner");
        JSpinner maxSpinner = (JSpinner) minMaxPanel.getClientProperty("maxSpinner");
        
        // --- Mode selection listener ---
        setupModeListeners(relativeButton, absoluteButton, targetLevelLabel, "Levels to gain:", "Target level:");
        
        // --- Randomize checkbox behavior ---
        setupRandomizeListener(randomizeCheckBox, levelSpinner, minSpinner, maxSpinner, 
                              relativeButton, minMaxPanel, panel);
                              
        // --- Min/Max validation ---
        setupMinMaxValidation(minSpinner, maxSpinner);
        
        // --- Description ---
        addDescriptionLabel(panel, gbc, stopConditionPanel);
        
        // Store components in configPanel client properties for later access
        storeConfigComponents(panel, skillComboBox, levelSpinner, minSpinner, maxSpinner, 
                             randomizeCheckBox, relativeButton, absoluteButton, targetLevelLabel);
    }

    /**
     * Creates the skill XP condition configuration panel
     */
    public static void createSkillXpConfigPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel) {
        // --- Add skill selection section ---
        JComboBox<String> skillComboBox = createSkillSelector();
        addSkillSelectionSection(panel, gbc, skillComboBox);
        
        // --- Add mode selection (relative vs absolute) ---
        JRadioButton relativeButton = new JRadioButton();
        JRadioButton absoluteButton = new JRadioButton();
        JPanel modePanel = createModeSelectionPanel(relativeButton, absoluteButton);
        gbc.gridy++;
        panel.add(modePanel, gbc);
        
        // --- Target XP section ---
        JLabel targetXpLabel = new JLabel("XP to gain:");
        targetXpLabel.setForeground(Color.WHITE);
        targetXpLabel.setFont(FontManager.getRunescapeSmallFont());
        
        SpinnerNumberModel xpModel = new SpinnerNumberModel(10000, 1, 200000000, 1000);
        JSpinner xpSpinner = new JSpinner(xpModel);
        xpSpinner.setPreferredSize(new Dimension(100, xpSpinner.getPreferredSize().height));
        
        JCheckBox randomizeCheckBox = new JCheckBox("Randomize");
        randomizeCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckBox.setForeground(Color.WHITE);
        
        JPanel xpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        xpPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        xpPanel.add(targetXpLabel);
        xpPanel.add(xpSpinner);
        xpPanel.add(randomizeCheckBox);
        
        gbc.gridy++;
        panel.add(xpPanel, gbc);
        
        // --- Min/Max XP panel ---
        JPanel minMaxPanel = createMinMaxPanel("Min XP:", "Max XP:", 1, 200000000, 1000);
        minMaxPanel.setVisible(false);
        gbc.gridy++;
        panel.add(minMaxPanel, gbc);
        
        // Save references to the spinners
        JSpinner minSpinner = (JSpinner) minMaxPanel.getClientProperty("minSpinner");
        JSpinner maxSpinner = (JSpinner) minMaxPanel.getClientProperty("maxSpinner");
        
        // --- Mode selection listener ---
        setupModeListeners(relativeButton, absoluteButton, targetXpLabel, "XP to gain:", "Target XP:");
        
        // --- Randomize checkbox behavior ---
        setupXpRandomizeListener(randomizeCheckBox, xpSpinner, minSpinner, maxSpinner, 
                               minMaxPanel, panel);
        
        // --- Min/Max validation ---
        setupMinMaxValidation(minSpinner, maxSpinner);
        
        // --- Description ---
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel();
        descriptionLabel.setText("XP is tracked from the time condition is created. Relative mode tracks gains from that point.");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Store components in configPanel client properties for later access
        configPanel.putClientProperty("xpSkillComboBox", skillComboBox);
        configPanel.putClientProperty("xpSpinner", xpSpinner);
        configPanel.putClientProperty("minXpSpinner", minSpinner);
        configPanel.putClientProperty("maxXpSpinner", maxSpinner);
        configPanel.putClientProperty("randomizeSkillXp", randomizeCheckBox);
        configPanel.putClientProperty("xpRelativeMode", relativeButton);
        configPanel.putClientProperty("xpAbsoluteMode", absoluteButton);
        configPanel.putClientProperty("xpTargetLabel", targetXpLabel);
    }
    
    /**
     * Creates a combo box with all skills and "Total" option
     */
    private static JComboBox<String> createSkillSelector() {
        JComboBox<String> skillComboBox = new JComboBox<>();
        for (Skill skill : Skill.values()) {
            skillComboBox.addItem(skill.getName());
        }
        skillComboBox.addItem("Total");
        skillComboBox.setPreferredSize(new Dimension(150, skillComboBox.getPreferredSize().height));
        return skillComboBox;
    }
    
    /**
     * Adds the skill selection section to the panel
     */
    private static void addSkillSelectionSection(JPanel panel, GridBagConstraints gbc, JComboBox<String> skillComboBox) {
        JPanel skillSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        skillSelectionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel skillLabel = new JLabel("Skill:");
        skillLabel.setForeground(Color.WHITE);
        skillLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        skillSelectionPanel.add(skillLabel);
        skillSelectionPanel.add(skillComboBox);
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(skillSelectionPanel, gbc);
    }
    
    /**
     * Creates the mode selection panel (relative vs absolute)
     */
    private static JPanel createModeSelectionPanel(JRadioButton relativeButton, JRadioButton absoluteButton) {
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel modeLabel = new JLabel("Mode:");
        modeLabel.setForeground(Color.WHITE);
        modeLabel.setFont(FontManager.getRunescapeSmallFont());
        modePanel.add(modeLabel);
        
        relativeButton.setText("Relative (gain from current)");
        relativeButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        relativeButton.setForeground(Color.WHITE);
        relativeButton.setSelected(true); // Default to relative mode
        relativeButton.setToolTipText("Track gains from the current value when the condition starts");
        
        absoluteButton.setText("Absolute (reach specific)");
        absoluteButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        absoluteButton.setForeground(Color.WHITE);
        absoluteButton.setToolTipText("Track progress toward a specific target value");
        
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(relativeButton);
        modeGroup.add(absoluteButton);
        
        modePanel.add(relativeButton);
        modePanel.add(absoluteButton);
        return modePanel;
    }
    
    /**
     * Creates a min/max panel for randomization
     */
    private static JPanel createMinMaxPanel(String minLabel, String maxLabel, int minValue, int maxValue, int step) {
        JPanel minMaxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabelComponent = new JLabel(minLabel);
        minLabelComponent.setForeground(Color.WHITE);
        minLabelComponent.setFont(FontManager.getRunescapeSmallFont());
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(minValue, minValue, maxValue, step);
        JSpinner minSpinner = new JSpinner(minModel);
        minSpinner.setPreferredSize(new Dimension(100, minSpinner.getPreferredSize().height));
        
        JLabel maxLabelComponent = new JLabel(maxLabel);
        maxLabelComponent.setForeground(Color.WHITE);
        maxLabelComponent.setFont(FontManager.getRunescapeSmallFont());
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(
            Math.min(maxValue, minValue + (step * 10)), 
            minValue, 
            maxValue, 
            step
        );
        JSpinner maxSpinner = new JSpinner(maxModel);
        maxSpinner.setPreferredSize(new Dimension(100, maxSpinner.getPreferredSize().height));
        
        minMaxPanel.add(minLabelComponent);
        minMaxPanel.add(minSpinner);
        minMaxPanel.add(maxLabelComponent);
        minMaxPanel.add(maxSpinner);
        
        // Store references for later access
        minMaxPanel.putClientProperty("minSpinner", minSpinner);
        minMaxPanel.putClientProperty("maxSpinner", maxSpinner);
        
        return minMaxPanel;
    }
    
    /**
     * Sets up listeners for the mode buttons
     */
    private static void setupModeListeners(JRadioButton relativeButton, JRadioButton absoluteButton, 
                                           JLabel targetLabel, String relativeText, String absoluteText) {
        relativeButton.addActionListener(e -> {
            targetLabel.setText(relativeText);
        });
        
        absoluteButton.addActionListener(e -> {
            targetLabel.setText(absoluteText);
        });
    }
    
    /**
     * Sets up the randomize checkbox behavior for level conditions
     */
    private static void setupRandomizeListener(JCheckBox randomizeCheckBox, JSpinner mainSpinner, 
                                            JSpinner minSpinner, JSpinner maxSpinner, 
                                            JRadioButton relativeButton, JPanel minMaxPanel,
                                            JPanel parentPanel) {
        randomizeCheckBox.addChangeListener(e -> {
            minMaxPanel.setVisible(randomizeCheckBox.isSelected());
            mainSpinner.setEnabled(!randomizeCheckBox.isSelected());
            
            // If enabling randomize, set min/max from current value
            if (randomizeCheckBox.isSelected()) {
                int value = (Integer) mainSpinner.getValue();
                if (relativeButton.isSelected()) {
                    // For relative mode, set reasonable min/max values
                    minSpinner.setValue(Math.max(1, value - 1));
                    maxSpinner.setValue(Math.min(99, value + 1)); 
                } else {
                    // For absolute mode, set wider range
                    minSpinner.setValue(Math.max(1, value - 5));
                    maxSpinner.setValue(Math.min(99, value + 5));
                }
            }
            
            parentPanel.revalidate();
            parentPanel.repaint();
        });
    }
    
    /**
     * Sets up the randomize checkbox behavior for XP conditions
     */
    private static void setupXpRandomizeListener(JCheckBox randomizeCheckBox, JSpinner xpSpinner, 
                                             JSpinner minSpinner, JSpinner maxSpinner,
                                             JPanel minMaxPanel, JPanel parentPanel) {
        randomizeCheckBox.addChangeListener(e -> {
            minMaxPanel.setVisible(randomizeCheckBox.isSelected());
            xpSpinner.setEnabled(!randomizeCheckBox.isSelected());
            
            // If enabling randomize, set min/max from current XP
            if (randomizeCheckBox.isSelected()) {
                int xp = (Integer) xpSpinner.getValue();
                // Set min/max values based on percentage of target XP
                int variation = Math.max(1000, xp / 5); // 20% variation or at least 1000 XP
                minSpinner.setValue(Math.max(1, xp - variation));
                maxSpinner.setValue(Math.min(200000000, xp + variation));
            }
            
            parentPanel.revalidate();
            parentPanel.repaint();
        });
    }
    
    /**
     * Sets up validation for min/max spinners to ensure min <= max
     */
    private static void setupMinMaxValidation(JSpinner minSpinner, JSpinner maxSpinner) {
        // Ensure min doesn't exceed max
        minSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (min > max) {
                maxSpinner.setValue(min);
            }
        });
        
        // Ensure max doesn't go below min
        maxSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (max < min) {
                minSpinner.setValue(max);
            }
        });
    }
    
    /**
     * Adds a description label to the panel
     */
    private static void addDescriptionLabel(JPanel panel, GridBagConstraints gbc, boolean stopConditionPanel) {
        gbc.gridy++;
        JLabel descriptionLabel;
        if (stopConditionPanel) {
            descriptionLabel = new JLabel("Plugin will stop when skill reaches target level");
        } else {
            descriptionLabel = new JLabel("Plugin will only start when skill is at or above target level");
        }
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
    }
    
    /**
     * Stores components in the configPanel for later access
     */
    private static void storeConfigComponents(JPanel configPanel, JComboBox<String> skillComboBox, 
                                           JSpinner levelSpinner, JSpinner minSpinner, JSpinner maxSpinner,
                                           JCheckBox randomizeCheckBox, JRadioButton relativeButton, 
                                           JRadioButton absoluteButton, JLabel targetLabel) {
        configPanel.putClientProperty("skillComboBox", skillComboBox);
        configPanel.putClientProperty("levelSpinner", levelSpinner);
        configPanel.putClientProperty("minLevelSpinner", minSpinner);
        configPanel.putClientProperty("maxLevelSpinner", maxSpinner);
        configPanel.putClientProperty("randomizeSkillLevel", randomizeCheckBox);
        configPanel.putClientProperty("relativeMode", relativeButton);
        configPanel.putClientProperty("absoluteMode", absoluteButton);
        configPanel.putClientProperty("skillLevelLabel", targetLabel);
    }
    
    /**
     * Update min/max spinner ranges based on mode
     */
    private static void updateMinMaxRanges(JSpinner levelSpinner, JSpinner minSpinner, JSpinner maxSpinner) {
        int currentValue = (Integer) levelSpinner.getValue();
        
        // Ensure all spinners use consistent min/max values
        SpinnerNumberModel levelModel = (SpinnerNumberModel) levelSpinner.getModel();
        SpinnerNumberModel minModel = (SpinnerNumberModel) minSpinner.getModel();
        SpinnerNumberModel maxModel = (SpinnerNumberModel) maxSpinner.getModel();
        
        levelModel.setMinimum(1);
        levelModel.setMaximum(99);
        minModel.setMinimum(1);
        minModel.setMaximum(99);
        maxModel.setMinimum(1);
        maxModel.setMaximum(99);
        
        // Update range based on current value
        minSpinner.setValue(Math.max(1, Math.min(currentValue - 1, 98)));
        maxSpinner.setValue(Math.min(99, Math.max(currentValue + 1, 2)));
    }

    /**
     * Creates a skill level condition based on UI components
     */
    public static SkillLevelCondition createSkillLevelCondition(JPanel configPanel) {
        JComboBox<String> skillComboBox = (JComboBox<String>) configPanel.getClientProperty("skillComboBox");
        if (skillComboBox == null) {
            throw new IllegalStateException("Skill combo box not found. Please check the panel configuration.");
        }
        
        JCheckBox randomizeCheckBox = (JCheckBox) configPanel.getClientProperty("randomizeSkillLevel");
        if (randomizeCheckBox == null) {
            throw new IllegalStateException("Randomize checkbox not found. Please check the panel configuration.");
        }
        
        JRadioButton relativeButton = (JRadioButton) configPanel.getClientProperty("relativeMode");
        if (relativeButton == null) {
            throw new IllegalStateException("Mode buttons not found. Please check the panel configuration.");
        }
        
        boolean isRelative = relativeButton.isSelected();
        
        String skillName = (String) skillComboBox.getSelectedItem();
        if (skillName == null) {
            // Provide a default if somehow no skill is selected
            skillName = Skill.ATTACK.getName();
        }
        
        Skill skill = null;
        if (skillName.equals("Total")) {
            skill = null;
        } else {
            skill = Skill.valueOf(skillName.toUpperCase());
        }
        
        if (randomizeCheckBox.isSelected()) {
            JSpinner minLevelSpinner = (JSpinner) configPanel.getClientProperty("minLevelSpinner");
            JSpinner maxLevelSpinner = (JSpinner) configPanel.getClientProperty("maxLevelSpinner");
            
            if (minLevelSpinner == null || maxLevelSpinner == null) {
                throw new IllegalStateException("Min/max level spinners not found. Please check the panel configuration.");
            }
            
            int minLevel = (Integer) minLevelSpinner.getValue();
            int maxLevel = (Integer) maxLevelSpinner.getValue();
            
            if (isRelative) {
                return SkillLevelCondition.createRelativeRandomized(skill, minLevel, maxLevel);
            } else {
                return SkillLevelCondition.createRandomized(skill, minLevel, maxLevel);
            }
        } else {
            JSpinner levelSpinner = (JSpinner) configPanel.getClientProperty("levelSpinner");
            if (levelSpinner == null) {
                throw new IllegalStateException("Level spinner not found. Please check the panel configuration.");
            }
            
            int level = (Integer) levelSpinner.getValue();
            
            if (isRelative) {
                return SkillLevelCondition.createRelative(skill, level);
            } else {
                return new SkillLevelCondition(skill, level);
            }
        }
    }
    
    /**
     * Creates a skill XP condition based on UI components
     */
    public static SkillXpCondition createSkillXpCondition(JPanel configPanel) {
        JComboBox<String> skillComboBox = (JComboBox<String>) configPanel.getClientProperty("xpSkillComboBox");
        if (skillComboBox == null) {
            throw new IllegalStateException("XP skill combo box not found. Please check the panel configuration.");
        }
        
        JCheckBox randomizeCheckBox = (JCheckBox) configPanel.getClientProperty("randomizeSkillXp");
        if (randomizeCheckBox == null) {
            throw new IllegalStateException("Randomize XP checkbox not found. Please check the panel configuration.");
        }
        
        JRadioButton relativeButton = (JRadioButton) configPanel.getClientProperty("xpRelativeMode");
        if (relativeButton == null) {
            throw new IllegalStateException("XP mode buttons not found. Please check the panel configuration.");
        }
        
        boolean isRelative = relativeButton.isSelected();
        
        String skillName = (String) skillComboBox.getSelectedItem();
        if (skillName == null) {
            // Provide a default if somehow no skill is selected
            skillName = Skill.ATTACK.getName();
        }
        
        Skill skill = null;
        if (skillName.equals("Total")) {
            skill = null;
        } else {
            skill = Skill.valueOf(skillName.toUpperCase());
        }
        
        if (randomizeCheckBox.isSelected()) {
            JSpinner minXpSpinner = (JSpinner) configPanel.getClientProperty("minXpSpinner");
            JSpinner maxXpSpinner = (JSpinner) configPanel.getClientProperty("maxXpSpinner");
            
            if (minXpSpinner == null || maxXpSpinner == null) {
                throw new IllegalStateException("Min/max XP spinners not found. Please check the panel configuration.");
            }
            
            int minXp = (Integer) minXpSpinner.getValue();
            int maxXp = (Integer) maxXpSpinner.getValue();
            
            if (isRelative) {
                return SkillXpCondition.createRelativeRandomized(skill, minXp, maxXp);
            } else {
                return SkillXpCondition.createRandomized(skill, minXp, maxXp);
            }
        } else {
            JSpinner xpSpinner = (JSpinner) configPanel.getClientProperty("xpSpinner");
            if (xpSpinner == null) {
                throw new IllegalStateException("XP spinner not found. Please check the panel configuration.");
            }
            
            int xp = (Integer) xpSpinner.getValue();
            
            if (isRelative) {
                return SkillXpCondition.createRelative(skill, xp);
            } else {
                return new SkillXpCondition(skill, xp);
            }
        }
    }

    /**
     * Sets up UI components based on an existing condition
     */
    public static void setupSkillCondition(JPanel panel, Condition condition) {
        if (condition == null) {
            return;
        }

        if (condition instanceof SkillLevelCondition) {
            setupSkillLevelCondition(panel, (SkillLevelCondition) condition);
        } else if (condition instanceof SkillXpCondition) {
            setupSkillXpCondition(panel, (SkillXpCondition) condition);
        }
    }

    /**
     * Sets up UI for an existing skill level condition
     */
    private static void setupSkillLevelCondition(JPanel panel, SkillLevelCondition condition) {
        JComboBox<String> skillComboBox = (JComboBox<String>) panel.getClientProperty("skillComboBox");
        JSpinner levelSpinner = (JSpinner) panel.getClientProperty("levelSpinner");
        JSpinner minLevelSpinner = (JSpinner) panel.getClientProperty("minLevelSpinner");
        JSpinner maxLevelSpinner = (JSpinner) panel.getClientProperty("maxLevelSpinner");
        JCheckBox randomizeCheckBox = (JCheckBox) panel.getClientProperty("randomizeSkillLevel");
        JRadioButton relativeButton = (JRadioButton) panel.getClientProperty("relativeMode");
        JRadioButton absoluteButton = (JRadioButton) panel.getClientProperty("absoluteMode");
        JLabel levelLabel = (JLabel) panel.getClientProperty("skillLevelLabel");
        
        if (skillComboBox != null) {
            Skill skill = condition.getSkill();
            String skillName = skill == null ? "Total" : skill.getName();
            skillComboBox.setSelectedItem(skillName);
        }
        
        // Set mode
        if (relativeButton != null && absoluteButton != null) {
            boolean isRelative = condition.isRelative();
            relativeButton.setSelected(isRelative);
            absoluteButton.setSelected(!isRelative);
            
            // Update label text based on mode
            if (levelLabel != null) {
                levelLabel.setText(isRelative ? "Levels to gain:" : "Target level:");
            }
        }
        
        if (randomizeCheckBox != null) {
            boolean isRandomized = condition.isRandomized();
            randomizeCheckBox.setSelected(isRandomized);
            
            if (isRandomized) {
                if (minLevelSpinner != null && maxLevelSpinner != null) {
                    minLevelSpinner.setValue(condition.getTargetLevelMin());
                    maxLevelSpinner.setValue(condition.getTargetLevelMax());
                }
            } else if (levelSpinner != null) {
                levelSpinner.setValue(condition.getCurrentTargetLevel());
            }
        }
    }

    /**
     * Sets up UI for an existing skill XP condition
     */
    private static void setupSkillXpCondition(JPanel panel, SkillXpCondition condition) {
        JComboBox<String> skillComboBox = (JComboBox<String>) panel.getClientProperty("xpSkillComboBox");
        JSpinner xpSpinner = (JSpinner) panel.getClientProperty("xpSpinner");
        JSpinner minXpSpinner = (JSpinner) panel.getClientProperty("minXpSpinner");
        JSpinner maxXpSpinner = (JSpinner) panel.getClientProperty("maxXpSpinner");
        JCheckBox randomizeCheckBox = (JCheckBox) panel.getClientProperty("randomizeSkillXp");
        JRadioButton relativeButton = (JRadioButton) panel.getClientProperty("xpRelativeMode");
        JRadioButton absoluteButton = (JRadioButton) panel.getClientProperty("xpAbsoluteMode");
        JLabel xpLabel = (JLabel) panel.getClientProperty("xpTargetLabel");
        
        if (skillComboBox != null) {
            Skill skill = condition.getSkill();
            String skillName = skill == null ? "Total" : skill.getName();
            skillComboBox.setSelectedItem(skillName);
        }
        
        // Set mode
        if (relativeButton != null && absoluteButton != null) {
            boolean isRelative = condition.isRelative();
            relativeButton.setSelected(isRelative);
            absoluteButton.setSelected(!isRelative);
            
            // Update label text based on mode
            if (xpLabel != null) {
                xpLabel.setText(isRelative ? "XP to gain:" : "Target XP:");
            }
        }
        
        if (randomizeCheckBox != null) {
            boolean isRandomized = condition.isRandomized();
            randomizeCheckBox.setSelected(isRandomized);
            
            if (isRandomized) {
                if (minXpSpinner != null && maxXpSpinner != null) {
                    minXpSpinner.setValue((int)condition.getTargetXpMin());
                    maxXpSpinner.setValue((int)condition.getTargetXpMax());
                }
            } else if (xpSpinner != null) {
                xpSpinner.setValue((int)condition.getCurrentTargetXp());
            }
        }
    }
}
