package net.runelite.client.plugins.microbot.pluginscheduler.ui;

import net.runelite.api.Item;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.*;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ConditionConfigPanel extends JPanel {
    private final JComboBox<String> conditionTypeComboBox;
    private final JPanel configPanel;
    private final List<Condition> conditions = new ArrayList<>();
    private final JCheckBox stopOnConditionsMetCheckbox;
    private final JComboBox<String> logicComboBox;
    private final DefaultListModel<String> conditionListModel;
    private final JList<String> conditionList;
    
    private Consumer<List<Condition>> conditionUpdateCallback;
    private Consumer<Boolean> stopOnConditionsMetCallback;
    private Consumer<Boolean> requireAllCallback;
    
    public ConditionConfigPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR),
                        BorderFactory.createEmptyBorder(5, 5, 5, 5)
                ),
                "Stop Conditions",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                FontManager.getRunescapeBoldFont(),
                Color.WHITE
        ));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JPanel topPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        topPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Logic selection
        JLabel logicLabel = new JLabel("Logic:");
        logicLabel.setForeground(Color.WHITE);
        topPanel.add(logicLabel);
        
        logicComboBox = new JComboBox<>(new String[]{"All conditions must be met (AND)", "Any condition can be met (OR)"});
        logicComboBox.addActionListener(e -> {
            if (requireAllCallback != null) {
                requireAllCallback.accept(logicComboBox.getSelectedIndex() == 0);
            }
        });
        topPanel.add(logicComboBox);
        
        // Stop checkbox
        JLabel stopLabel = new JLabel("Auto-stop:");
        stopLabel.setForeground(Color.WHITE);
        topPanel.add(stopLabel);
        
        stopOnConditionsMetCheckbox = new JCheckBox("Stop when conditions are met");
        stopOnConditionsMetCheckbox.setForeground(Color.WHITE);
        stopOnConditionsMetCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        stopOnConditionsMetCheckbox.setSelected(true);
        stopOnConditionsMetCheckbox.addActionListener(e -> {
            if (stopOnConditionsMetCallback != null) {
                stopOnConditionsMetCallback.accept(stopOnConditionsMetCheckbox.isSelected());
            }
        });
        topPanel.add(stopOnConditionsMetCheckbox);
        
        add(topPanel, BorderLayout.NORTH);
        
        // Condition list
        conditionListModel = new DefaultListModel<>();
        conditionList = new JList<>(conditionListModel);
        conditionList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        conditionList.setForeground(Color.WHITE);
        conditionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        JScrollPane scrollPane = new JScrollPane(conditionList);
        scrollPane.setPreferredSize(new Dimension(0, 100));
        add(scrollPane, BorderLayout.CENTER);
        
        // Config panel for condition type
        configPanel = new JPanel();
        configPanel.setLayout(new BoxLayout(configPanel, BoxLayout.Y_AXIS));
        configPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        configPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        // Add condition type selector
        JPanel selectorPanel = new JPanel(new BorderLayout(5, 0));
        selectorPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        conditionTypeComboBox = new JComboBox<>(new String[]{"Time", "Skill Level", "Skill XP", "Item Collection"});
        conditionTypeComboBox.addActionListener(e -> updateConfigPanel());
        selectorPanel.add(conditionTypeComboBox, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JButton addButton = createButton("Add", ColorScheme.PROGRESS_COMPLETE_COLOR);
        addButton.addActionListener(e -> addCurrentCondition());
        buttonPanel.add(addButton);
        
        JButton editButton = createButton("Edit", ColorScheme.BRAND_ORANGE);
        editButton.addActionListener(e -> editSelectedCondition());
        buttonPanel.add(editButton);
        
        JButton removeButton = createButton("Remove", ColorScheme.PROGRESS_ERROR_COLOR);
        removeButton.addActionListener(e -> removeSelectedCondition());
        buttonPanel.add(removeButton);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bottomPanel.add(selectorPanel, BorderLayout.NORTH);
        bottomPanel.add(configPanel, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Initialize the config panel
        updateConfigPanel();
    }
    
    private JButton createButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 1),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
            }
            
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
        });
        
        return button;
    }
    
    private void updateConfigPanel() {
        configPanel.removeAll();
        
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 2, 2);
        
        int selectedIndex = conditionTypeComboBox.getSelectedIndex();
        
        switch (selectedIndex) {
            case 0: // Time
                createTimeConfigPanel(panel, gbc);
                break;
            case 1: // Skill Level
                createSkillLevelConfigPanel(panel, gbc);
                break;
            case 2: // Skill XP
                createSkillXpConfigPanel(panel, gbc);
                break;
            case 3: // Item Collection
                createItemConfigPanel(panel, gbc);
                break;
        }
        
        configPanel.add(panel);
        configPanel.revalidate();
        configPanel.repaint();
    }
    
    private void createTimeConfigPanel(JPanel panel, GridBagConstraints gbc) {
        
        
        
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        JPanel minMaxPanel = new JPanel(new GridLayout(2, 2, 5, 2));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minHoursLabel = new JLabel("Min Hours:");
        minHoursLabel.setForeground(Color.WHITE);
        minMaxPanel.add(minHoursLabel);
        
        SpinnerNumberModel minHoursModel = new SpinnerNumberModel(1, 0, 24, 1);
        JSpinner minHoursSpinner = new JSpinner(minHoursModel);
        minMaxPanel.add(minHoursSpinner);
        
        JLabel maxHoursLabel = new JLabel("Max Hours:");
        maxHoursLabel.setForeground(Color.WHITE);
        minMaxPanel.add(maxHoursLabel);
        
        SpinnerNumberModel maxHoursModel = new SpinnerNumberModel(2, 0, 24, 1);
        JSpinner maxHoursSpinner = new JSpinner(maxHoursModel);
        minMaxPanel.add(maxHoursSpinner);
        
        minMaxPanel.setVisible(false);
        panel.add(minMaxPanel, gbc);
        
        
        
        // Store these components for accessing when creating conditions
        
        configPanel.putClientProperty("timeMinHoursSpinner", minHoursSpinner);
        configPanel.putClientProperty("timeMaxHoursSpinner", maxHoursSpinner);
    }
    
    private void createSkillLevelConfigPanel(JPanel panel, GridBagConstraints gbc) {
        JLabel skillLabel = new JLabel("Skill:");
        skillLabel.setForeground(Color.WHITE);
        panel.add(skillLabel, gbc);
        
        gbc.gridx++;
        JComboBox<String> skillComboBox = new JComboBox<>();
        for (Skill skill : Skill.values()) {
            skillComboBox.addItem(skill.getName());
        }
        panel.add(skillComboBox, gbc);
        
       
        
       
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        JPanel minMaxPanel = new JPanel(new GridLayout(1, 4, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min:");
        minLabel.setForeground(Color.WHITE);
        minMaxPanel.add(minLabel);
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(5, 1, 99, 1);
        JSpinner minSpinner = new JSpinner(minModel);
        minMaxPanel.add(minSpinner);
        
        JLabel maxLabel = new JLabel("Max:");
        maxLabel.setForeground(Color.WHITE);
        minMaxPanel.add(maxLabel);
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(10, 1, 99, 1);
        JSpinner maxSpinner = new JSpinner(maxModel);
        minMaxPanel.add(maxSpinner);
        
        minMaxPanel.setVisible(false);
        panel.add(minMaxPanel, gbc);
        
     
        
        configPanel.putClientProperty("skillComboBox", skillComboBox);        
        configPanel.putClientProperty("minLevelSpinner", minSpinner);
        configPanel.putClientProperty("maxLevelSpinner", maxSpinner);
    }
    
    private void createSkillXpConfigPanel(JPanel panel, GridBagConstraints gbc) {
        JLabel skillLabel = new JLabel("Skill:");
        skillLabel.setForeground(Color.WHITE);
        panel.add(skillLabel, gbc);
        
        gbc.gridx++;
        JComboBox<String> skillComboBox = new JComboBox<>();
        for (Skill skill : Skill.values()) {
            skillComboBox.addItem(skill.getName());
        }
        panel.add(skillComboBox, gbc);
        
       
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        JPanel minMaxPanel = new JPanel(new GridLayout(1, 4, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min XP:");
        minLabel.setForeground(Color.WHITE);
        minMaxPanel.add(minLabel);
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(5000, 1, 13000000, 1000);
        JSpinner minSpinner = new JSpinner(minModel);
        minMaxPanel.add(minSpinner);
        
        JLabel maxLabel = new JLabel("Max XP:");
        maxLabel.setForeground(Color.WHITE);
        minMaxPanel.add(maxLabel);
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(15000, 1, 13000000, 1000);
        JSpinner maxSpinner = new JSpinner(maxModel);
        minMaxPanel.add(maxSpinner);
        
        minMaxPanel.setVisible(false);
        panel.add(minMaxPanel, gbc);
                
        
        configPanel.putClientProperty("xpSkillComboBox", skillComboBox);        
        configPanel.putClientProperty("minXpSpinner", minSpinner);
        configPanel.putClientProperty("maxXpSpinner", maxSpinner);
    }
    
    private void createItemConfigPanel(JPanel panel, GridBagConstraints gbc) {
        JLabel itemsLabel = new JLabel("Item Names (comma-separated, supports regex):");
        itemsLabel.setForeground(Color.WHITE);
        panel.add(itemsLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        JTextField itemsField = new JTextField();
        panel.add(itemsField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        JLabel amountLabel = new JLabel("Target Amount:");
        amountLabel.setForeground(Color.WHITE);
        panel.add(amountLabel, gbc);
   ;              
        
       
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        JPanel minMaxPanel = new JPanel(new GridLayout(1, 4, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min:");
        minLabel.setForeground(Color.WHITE);
        minMaxPanel.add(minLabel);
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(50, 1, 100000, 10);
        JSpinner minSpinner = new JSpinner(minModel);
        minMaxPanel.add(minSpinner);
        
        JLabel maxLabel = new JLabel("Max:");
        maxLabel.setForeground(Color.WHITE);
        minMaxPanel.add(maxLabel);
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(150, 1, 100000, 10);
        JSpinner maxSpinner = new JSpinner(maxModel);
        minMaxPanel.add(maxSpinner);
        
        minMaxPanel.setVisible(false);
        panel.add(minMaxPanel, gbc);
                       
        configPanel.putClientProperty("itemsField", itemsField);       
        configPanel.putClientProperty("minAmountSpinner", minSpinner);
        configPanel.putClientProperty("maxAmountSpinner", maxSpinner);
    }
    
    public void setConditionUpdateCallback(Consumer<List<Condition>> callback) {
        this.conditionUpdateCallback = callback;
    }
    
    public void setStopOnConditionsMetCallback(Consumer<Boolean> callback) {
        this.stopOnConditionsMetCallback = callback;
    }
    
    public void setRequireAllCallback(Consumer<Boolean> callback) {
        this.requireAllCallback = callback;
    }
    
    private void notifyConditionUpdate() {
        if (conditionUpdateCallback != null) {
            conditionUpdateCallback.accept(new ArrayList<>(conditions));
        }
    }
    
    private void addCurrentCondition() {
        int selectedIndex = conditionTypeComboBox.getSelectedIndex();
        Condition condition = null;
        
        switch (selectedIndex) {
            case 0: // Time
                condition = createTimeCondition();
                break;
            case 1: // Skill Level
                condition = createSkillLevelCondition();
                break;
            case 2: // Skill XP
                condition = createSkillXpCondition();
                break;
            case 3: // Item Collection
                List<LootItemCondition> LootItemCondition = createItemCondition();
                if (LootItemCondition != null) {
                    conditions.addAll(LootItemCondition);
                    for (LootItemCondition itemCondition : LootItemCondition) {
                        conditionListModel.addElement(itemCondition.getDescription());
                    }
                    notifyConditionUpdate();
                }
                break;
        }
        
        if (condition != null) {
            conditions.add(condition);
            conditionListModel.addElement(condition.getDescription());
            notifyConditionUpdate();
        }
    }
    
    private TimeCondition createTimeCondition() {
        
        
        
        JSpinner minHoursSpinner = (JSpinner) configPanel.getClientProperty("timeMinHoursSpinner");
        JSpinner maxHoursSpinner = (JSpinner) configPanel.getClientProperty("timeMaxHoursSpinner");
        
        int minHours = (Integer) minHoursSpinner.getValue();
        int maxHours = (Integer) maxHoursSpinner.getValue();
        
        Duration minDuration = Duration.ofHours(minHours);
        Duration maxDuration = Duration.ofHours(maxHours);
        
        return TimeCondition.createRandomized(minDuration, maxDuration);
    
    }
    
    private SkillLevelCondition createSkillLevelCondition() {
        JComboBox<String> skillComboBox = (JComboBox<String>) configPanel.getClientProperty("skillComboBox");
        
        
        String skillName = (String) skillComboBox.getSelectedItem();
        Skill skill = Skill.valueOf(skillName.toUpperCase());
        
        
        JSpinner minLevelSpinner = (JSpinner) configPanel.getClientProperty("minLevelSpinner");
        JSpinner maxLevelSpinner = (JSpinner) configPanel.getClientProperty("maxLevelSpinner");
        
        int minLevel = (Integer) minLevelSpinner.getValue();
        int maxLevel = (Integer) maxLevelSpinner.getValue();
        
        return SkillLevelCondition.createRandomized(skill, minLevel, maxLevel);
      
    }
    
    private SkillXpCondition createSkillXpCondition() {
        JComboBox<String> skillComboBox = (JComboBox<String>) configPanel.getClientProperty("xpSkillComboBox");
        
        
        String skillName = (String) skillComboBox.getSelectedItem();
        Skill skill = Skill.valueOf(skillName.toUpperCase());
        
        
        JSpinner minXpSpinner = (JSpinner) configPanel.getClientProperty("minXpSpinner");
        JSpinner maxXpSpinner = (JSpinner) configPanel.getClientProperty("maxXpSpinner");
        
        int minXp = (Integer) minXpSpinner.getValue();
        int maxXp = (Integer) maxXpSpinner.getValue();
        
        return SkillXpCondition.createRandomized(skill, minXp, maxXp);
    }
    private List<LootItemCondition> createItemCondition() {
        JTextField itemsField = (JTextField) configPanel.getClientProperty("itemsField");        
        
        
        String itemNamesString = itemsField.getText().trim();
        if (itemNamesString.isEmpty()) {
            return null; // Invalid item name
        }
        
        // Split by comma and trim each item name
        String[] itemNamesArray = itemNamesString.split(",");
        List<String> itemNames = new ArrayList<>();
        
        for (String itemName : itemNamesArray) {
            itemName = itemName.trim();
            if (!itemName.isEmpty()) {
                itemNames.add(itemName);
            }
        }
        
        if (itemNames.isEmpty()) {
            return null;
        }
        List<LootItemCondition> lootItemConditions = new ArrayList<>();
        for (String itemName : itemNames) {
            if (itemName.isEmpty()) {
                return null; // Invalid item name
            }
            JSpinner minAmountSpinner = (JSpinner) configPanel.getClientProperty("minAmountSpinner");
            JSpinner maxAmountSpinner = (JSpinner) configPanel.getClientProperty("maxAmountSpinner");
            
            int minAmount = (Integer) minAmountSpinner.getValue();
            int maxAmount = (Integer) maxAmountSpinner.getValue();
            
            lootItemConditions.add(LootItemCondition.createRandomized( itemName, minAmount, maxAmount));
        }
        
        return lootItemConditions;
    }
    
    private void editSelectedCondition() {
        int selectedIndex = conditionList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= conditions.size()) {
            return;
        }
        
        Condition condition = conditions.get(selectedIndex);
        // Editing is complex and depends on condition type
        // A full implementation would load the condition values back into the UI
        // For simplicity, we'll just remove and let the user re-add
        removeSelectedCondition();
    }
    
    private void removeSelectedCondition() {
        int selectedIndex = conditionList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= conditions.size()) {
            return;
        }
        
        conditions.remove(selectedIndex);
        conditionListModel.remove(selectedIndex);
        notifyConditionUpdate();
    }
    
    public void loadConditions(List<Condition> conditionList, boolean stopOnConditionsMet, boolean requireAll) {
        conditions.clear();
        conditionListModel.clear();
        
        if (conditionList != null) {
            for (Condition condition : conditionList) {
                conditions.add(condition);
                conditionListModel.addElement(condition.getDescription());
            }
        }
        
        stopOnConditionsMetCheckbox.setSelected(stopOnConditionsMet);
        logicComboBox.setSelectedIndex(requireAll ? 0 : 1);
    }
    
    public List<Condition> getConditions() {
        return new ArrayList<>(conditions);
    }
    
    public boolean isStopOnConditionsMet() {
        return stopOnConditionsMetCheckbox.isSelected();
    }
    
    public boolean isRequireAll() {
        return logicComboBox.getSelectedIndex() == 0;
    }
}