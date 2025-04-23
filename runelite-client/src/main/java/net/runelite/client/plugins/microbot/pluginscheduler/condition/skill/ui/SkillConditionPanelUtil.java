package net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.ui;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillLevelCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillXpCondition;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public class SkillConditionPanelUtil {
    public static void createSkillLevelConfigPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel, boolean stopConditionPanel) {
        // --- Skill selection section ---
        JPanel skillSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        skillSelectionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel skillLabel = new JLabel("Skill:");
        skillLabel.setForeground(Color.WHITE);
        skillLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        skillSelectionPanel.add(skillLabel);
        
        JComboBox<String> skillComboBox = new JComboBox<>();
        for (Skill skill : Skill.values()) {
            skillComboBox.addItem(skill.getName());
        }
        // Add Total as an option
        skillComboBox.addItem("Total");
        
        skillComboBox.setPreferredSize(new Dimension(150, skillComboBox.getPreferredSize().height));
        skillSelectionPanel.add(skillComboBox);
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(skillSelectionPanel, gbc);
        
        // --- Target level section ---
        gbc.gridy++;
        JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        levelPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel targetLevelLabel = new JLabel("Target Level:");
        targetLevelLabel.setForeground(Color.WHITE);
        targetLevelLabel.setFont(FontManager.getRunescapeSmallFont());
        levelPanel.add(targetLevelLabel);
        
        SpinnerNumberModel levelModel = new SpinnerNumberModel(10, 1, 99, 1);
        JSpinner levelSpinner = new JSpinner(levelModel);
        levelSpinner.setPreferredSize(new Dimension(70, levelSpinner.getPreferredSize().height));
        levelPanel.add(levelSpinner);
        
        JCheckBox randomizeCheckBox = new JCheckBox("Randomize");
        randomizeCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckBox.setForeground(Color.WHITE);
        levelPanel.add(randomizeCheckBox);
        
        panel.add(levelPanel, gbc);
        
        // --- Min/Max panel ---
        gbc.gridy++;
        JPanel minMaxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min Level:");
        minLabel.setForeground(Color.WHITE);
        minLabel.setFont(FontManager.getRunescapeSmallFont());
        minMaxPanel.add(minLabel);
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(5, 1, 99, 1);
        JSpinner minSpinner = new JSpinner(minModel);
        minSpinner.setPreferredSize(new Dimension(70, minSpinner.getPreferredSize().height));
        minMaxPanel.add(minSpinner);
        
        JLabel maxLabel = new JLabel("Max Level:");
        maxLabel.setForeground(Color.WHITE);
        maxLabel.setFont(FontManager.getRunescapeSmallFont());
        minMaxPanel.add(maxLabel);
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(15, 1, 99, 1);
        JSpinner maxSpinner = new JSpinner(maxModel);
        maxSpinner.setPreferredSize(new Dimension(70, maxSpinner.getPreferredSize().height));
        minMaxPanel.add(maxSpinner);
        
        minMaxPanel.setVisible(false);
        panel.add(minMaxPanel, gbc);
        
        // --- Randomize checkbox behavior ---
        randomizeCheckBox.addChangeListener(e -> {
            minMaxPanel.setVisible(randomizeCheckBox.isSelected());
            levelSpinner.setEnabled(!randomizeCheckBox.isSelected());
            
            // If enabling randomize, set min/max from current level
            if (randomizeCheckBox.isSelected()) {
                int level = (Integer) levelSpinner.getValue();
                minSpinner.setValue(Math.max(1, level - 5));
                maxSpinner.setValue(Math.min(99, level + 5));
            }
            
            panel.revalidate();
            panel.repaint();
        });
        
        // --- Min/Max validation ---
        minSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (min > max) {
                maxSpinner.setValue(min);
            }
        });
        
        maxSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (max < min) {
                minSpinner.setValue(max);
            }
        });
        
        // --- Description ---
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
        
        // Store components
        configPanel.putClientProperty("skillComboBox", skillComboBox);
        configPanel.putClientProperty("levelSpinner", levelSpinner);
        configPanel.putClientProperty("minLevelSpinner", minSpinner);
        configPanel.putClientProperty("maxLevelSpinner", maxSpinner);
        configPanel.putClientProperty("randomizeSkillLevel", randomizeCheckBox);
    }

    // Update SkillLevelCondition to use randomization
    public static SkillLevelCondition createSkillLevelCondition(JPanel configPanel) {
        JComboBox<String> skillComboBox = (JComboBox<String>) configPanel.getClientProperty("skillComboBox");
        if (skillComboBox == null) {
            throw new IllegalStateException("Skill combo box not found. Please check the panel configuration.");
        }
        
        JCheckBox randomizeCheckBox = (JCheckBox) configPanel.getClientProperty("randomizeSkillLevel");
        if (randomizeCheckBox == null) {
            throw new IllegalStateException("Randomize checkbox not found. Please check the panel configuration.");
        }
        
        String skillName = (String) skillComboBox.getSelectedItem();
        if (skillName == null) {
            // Provide a default if somehow no skill is selected
            skillName = Skill.ATTACK.getName();
        }
        Skill skill = null;
        if (skillName.equals("Total")) {
            skill =null;
        }else{
            skill= Skill.valueOf(skillName.toUpperCase());
        }
        
        
        if (randomizeCheckBox.isSelected()) {
            JSpinner minLevelSpinner = (JSpinner) configPanel.getClientProperty("minLevelSpinner");
            JSpinner maxLevelSpinner = (JSpinner) configPanel.getClientProperty("maxLevelSpinner");
            
            if (minLevelSpinner == null || maxLevelSpinner == null) {
                throw new IllegalStateException("Min/max level spinners not found. Please check the panel configuration.");
            }
            
            int minLevel = (Integer) minLevelSpinner.getValue();
            int maxLevel = (Integer) maxLevelSpinner.getValue();
            
            return SkillLevelCondition.createRandomized(skill, minLevel, maxLevel);
        } else {
            JSpinner levelSpinner = (JSpinner) configPanel.getClientProperty("levelSpinner");
            if (levelSpinner == null) {
                throw new IllegalStateException("Level spinner not found. Please check the panel configuration.");
            }
            
            int level = (Integer) levelSpinner.getValue();
            return new SkillLevelCondition(skill, level);
        }
    }
      
    public static void createSkillXpConfigPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel) {
        // --- Skill selection section ---
        JPanel skillSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        skillSelectionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel skillLabel = new JLabel("Skill:");
        skillLabel.setForeground(Color.WHITE);
        skillLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        skillSelectionPanel.add(skillLabel);
        
        JComboBox<String> skillComboBox = new JComboBox<>();
        for (Skill skill : Skill.values()) {
            skillComboBox.addItem(skill.getName());
        }
        // Add Total as an option
        skillComboBox.addItem("Total");
        
        skillComboBox.setPreferredSize(new Dimension(150, skillComboBox.getPreferredSize().height));
        skillSelectionPanel.add(skillComboBox);
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(skillSelectionPanel, gbc);
        
        // --- Target XP section ---
        gbc.gridy++;
        JPanel xpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        xpPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel targetXpLabel = new JLabel("Target XP:");
        targetXpLabel.setForeground(Color.WHITE);
        targetXpLabel.setFont(FontManager.getRunescapeSmallFont());
        xpPanel.add(targetXpLabel);
        
        SpinnerNumberModel xpModel = new SpinnerNumberModel(10000, 1, 200000000, 1000);
        JSpinner xpSpinner = new JSpinner(xpModel);
        // Make XP spinner wider to accommodate larger numbers
        xpSpinner.setPreferredSize(new Dimension(100, xpSpinner.getPreferredSize().height));
        xpPanel.add(xpSpinner);
        
        JCheckBox randomizeCheckBox = new JCheckBox("Randomize");
        randomizeCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckBox.setForeground(Color.WHITE);
        xpPanel.add(randomizeCheckBox);
        
        panel.add(xpPanel, gbc);
        
        // --- Min/Max panel ---
        gbc.gridy++;
        JPanel minMaxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min XP:");
        minLabel.setForeground(Color.WHITE);
        minLabel.setFont(FontManager.getRunescapeSmallFont());
        minMaxPanel.add(minLabel);
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(5000, 1, 200000000, 1000);
        JSpinner minSpinner = new JSpinner(minModel);
        minSpinner.setPreferredSize(new Dimension(100, minSpinner.getPreferredSize().height));
        minMaxPanel.add(minSpinner);
        
        JLabel maxLabel = new JLabel("Max XP:");
        maxLabel.setForeground(Color.WHITE);
        maxLabel.setFont(FontManager.getRunescapeSmallFont());
        minMaxPanel.add(maxLabel);
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(15000, 1, 200000000, 1000);
        JSpinner maxSpinner = new JSpinner(maxModel);
        maxSpinner.setPreferredSize(new Dimension(100, maxSpinner.getPreferredSize().height));
        minMaxPanel.add(maxSpinner);
        
        minMaxPanel.setVisible(false);
        panel.add(minMaxPanel, gbc);
        
        // --- Randomize checkbox behavior ---
        randomizeCheckBox.addChangeListener(e -> {
            minMaxPanel.setVisible(randomizeCheckBox.isSelected());
            xpSpinner.setEnabled(!randomizeCheckBox.isSelected());
            
            // If enabling randomize, set min/max from current XP
            if (randomizeCheckBox.isSelected()) {
                int xp = (Integer) xpSpinner.getValue();
                minSpinner.setValue(Math.max(1, xp - 5000));
                maxSpinner.setValue(Math.min(200000000, xp + 5000));
            }
            
            panel.revalidate();
            panel.repaint();
        });
        
        // --- Min/Max validation ---
        minSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (min > max) {
                maxSpinner.setValue(min);
            }
        });
        
        maxSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (max < min) {
                minSpinner.setValue(max);
            }
        });
        
        // --- Description ---
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Plugin will stop when skill XP reaches target value");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Store components
        configPanel.putClientProperty("xpSkillComboBox", skillComboBox);
        configPanel.putClientProperty("xpSpinner", xpSpinner);
        configPanel.putClientProperty("minXpSpinner", minSpinner);
        configPanel.putClientProperty("maxXpSpinner", maxSpinner);
        configPanel.putClientProperty("randomizeSkillXp", randomizeCheckBox);
    }
    public static SkillXpCondition createSkillXpCondition(JPanel configPanel) {
        JComboBox<String> skillComboBox = (JComboBox<String>) configPanel.getClientProperty("xpSkillComboBox");
        if (skillComboBox == null) {
            throw new IllegalStateException("XP skill combo box not found. Please check the panel configuration.");
        }
        
        JCheckBox randomizeCheckBox = (JCheckBox) configPanel.getClientProperty("randomizeSkillXp");
        if (randomizeCheckBox == null) {
            throw new IllegalStateException("Randomize XP checkbox not found. Please check the panel configuration.");
        }
        
        String skillName = (String) skillComboBox.getSelectedItem();
        if (skillName == null) {
            // Provide a default if somehow no skill is selected
            skillName = Skill.ATTACK.getName();
        }
        Skill skill = null;
        if (skillName.equals("Total")) {
            skill = null;
        }else{
            skill= Skill.valueOf(skillName.toUpperCase());
        }
        
        
        if (randomizeCheckBox.isSelected()) {
            JSpinner minXpSpinner = (JSpinner) configPanel.getClientProperty("minXpSpinner");
            JSpinner maxXpSpinner = (JSpinner) configPanel.getClientProperty("maxXpSpinner");
            
            if (minXpSpinner == null || maxXpSpinner == null) {
                throw new IllegalStateException("Min/max XP spinners not found. Please check the panel configuration.");
            }
            
            int minXp = (Integer) minXpSpinner.getValue();
            int maxXp = (Integer) maxXpSpinner.getValue();
            
            return SkillXpCondition.createRandomized(skill, minXp, maxXp);
        } else {
            JSpinner xpSpinner = (JSpinner) configPanel.getClientProperty("xpSpinner");
            if (xpSpinner == null) {
                throw new IllegalStateException("XP spinner not found. Please check the panel configuration.");
            }
            
            int xp = (Integer) xpSpinner.getValue();
            return new SkillXpCondition(skill, xp);
        }
    }

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

    private static void setupSkillLevelCondition(JPanel panel, SkillLevelCondition condition) {
        JComboBox<String> skillComboBox = (JComboBox<String>) panel.getClientProperty("skillComboBox");
        JSpinner levelSpinner = (JSpinner) panel.getClientProperty("levelSpinner");
        JSpinner minLevelSpinner = (JSpinner) panel.getClientProperty("minLevelSpinner");
        JSpinner maxLevelSpinner = (JSpinner) panel.getClientProperty("maxLevelSpinner");
        JCheckBox randomizeCheckBox = (JCheckBox) panel.getClientProperty("randomizeSkillLevel");
        
        if (skillComboBox != null) {
            Skill skill = condition.getSkill();
            String skillName = skill == null ? "Total" : skill.getName();
            skillComboBox.setSelectedItem(skillName);
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
                levelSpinner.setValue(condition.getTargetLevelMin());
            }
        }
    }

    private static void setupSkillXpCondition(JPanel panel, SkillXpCondition condition) {
        JComboBox<String> skillComboBox = (JComboBox<String>) panel.getClientProperty("xpSkillComboBox");
        JSpinner xpSpinner = (JSpinner) panel.getClientProperty("xpSpinner");
        JSpinner minXpSpinner = (JSpinner) panel.getClientProperty("minXpSpinner");
        JSpinner maxXpSpinner = (JSpinner) panel.getClientProperty("maxXpSpinner");
        JCheckBox randomizeCheckBox = (JCheckBox) panel.getClientProperty("randomizeSkillXp");
        
        if (skillComboBox != null) {
            Skill skill = condition.getSkill();
            String skillName = skill == null ? "Total" : skill.getName();
            skillComboBox.setSelectedItem(skillName);
        }
        
        if (randomizeCheckBox != null) {
            boolean isRandomized = condition.isRandomized();
            randomizeCheckBox.setSelected(isRandomized);
            
            if (isRandomized) {
                if (minXpSpinner != null && maxXpSpinner != null) {
                    minXpSpinner.setValue(condition.getTargetXpMin());
                    maxXpSpinner.setValue(condition.getTargetXpMax());
                }
            } else if (xpSpinner != null) {
                xpSpinner.setValue(condition.getTargetXpMin());
            }
        }
    }
}
