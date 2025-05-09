package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.ui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.BankItemCountCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.GatheredResourceCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.InventoryItemCountCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.LootItemCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.ProcessItemCondition;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridLayout;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;




@Slf4j
public class ResourceConditionPanelUtil {
    /**
     * Creates a panel for configuring Inventory Item Count conditions
     */
    public static void createInventoryItemCountPanel(JPanel panel, GridBagConstraints gbc) {
        // Title
        JLabel titleLabel = new JLabel("Inventory Item Count:");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);
        
        // Item name input
        gbc.gridy++;
        JPanel itemNamePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        itemNamePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel itemNameLabel = new JLabel("Item Name (leave empty for any):");
        itemNameLabel.setForeground(Color.WHITE);
        itemNamePanel.add(itemNameLabel);
        
        JTextField itemNameField = new JTextField(15);
        itemNameField.setForeground(Color.WHITE);
        itemNameField.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        itemNamePanel.add(itemNameField);
        
        panel.add(itemNamePanel, gbc);
        
        // Count range
        gbc.gridy++;
        JPanel countPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        countPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minCountLabel = new JLabel("Min Count:");
        minCountLabel.setForeground(Color.WHITE);
        countPanel.add(minCountLabel);
        
        SpinnerNumberModel minCountModel = new SpinnerNumberModel(10, 0, Integer.MAX_VALUE, 1);
        JSpinner minCountSpinner = new JSpinner(minCountModel);
        countPanel.add(minCountSpinner);
        
        JLabel maxCountLabel = new JLabel("Max Count:");
        maxCountLabel.setForeground(Color.WHITE);
        countPanel.add(maxCountLabel);
        
        SpinnerNumberModel maxCountModel = new SpinnerNumberModel(10, 0, Integer.MAX_VALUE, 1);
        JSpinner maxCountSpinner = new JSpinner(maxCountModel);
        countPanel.add(maxCountSpinner);
        
        // Link the min and max spinners
        minCountSpinner.addChangeListener(e -> {
            int minValue = (Integer) minCountSpinner.getValue();
            int maxValue = (Integer) maxCountSpinner.getValue();
            if (minValue > maxValue) {
                maxCountSpinner.setValue(minValue);
            }
        });
        
        maxCountSpinner.addChangeListener(e -> {
            int minValue = (Integer) minCountSpinner.getValue();
            int maxValue = (Integer) maxCountSpinner.getValue();
            if (maxValue < minValue) {
                minCountSpinner.setValue(maxValue);
            }
        });
        
        panel.add(countPanel, gbc);
        
        // Options panel
        gbc.gridy++;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JCheckBox includeNotedCheckbox = new JCheckBox("Include noted items");
        includeNotedCheckbox.setForeground(Color.WHITE);
        includeNotedCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        includeNotedCheckbox.setSelected(true);
        optionsPanel.add(includeNotedCheckbox);
        
    
        
        panel.add(optionsPanel, gbc);
        
        // Add a helpful description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Plugin will stop when you have the target number of items");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        gbc.gridy++;
        JLabel regexLabel = new JLabel("Item name supports regex patterns (.*bones.*)");
        regexLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        regexLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(regexLabel, gbc);
        
        // Store the components for later access
        panel.putClientProperty("itemNameField", itemNameField);
        panel.putClientProperty("minCountSpinner", minCountSpinner);
        panel.putClientProperty("maxCountSpinner", maxCountSpinner);
        panel.putClientProperty("includeNotedCheckbox", includeNotedCheckbox);        
    }
    public static InventoryItemCountCondition createInventoryItemCountCondition(JPanel configPanel) {
        JTextField itemNameField = (JTextField) configPanel.getClientProperty("itemNameField");
        JSpinner minCountSpinner = (JSpinner) configPanel.getClientProperty("minCountSpinner");
        JSpinner maxCountSpinner = (JSpinner) configPanel.getClientProperty("maxCountSpinner");
        JCheckBox includeNotedCheckbox = (JCheckBox) configPanel.getClientProperty("includeNotedCheckbox");

        
        String itemName = itemNameField.getText().trim();
        int minCount = (Integer) minCountSpinner.getValue();
        int maxCount = (Integer) maxCountSpinner.getValue();
        boolean includeNoted = includeNotedCheckbox.isSelected();        
        
        if (minCount != maxCount) {
            return InventoryItemCountCondition.builder()
                    .itemName(itemName)
                    .targetCountMin(minCount)
                    .targetCountMax(maxCount)
                    .includeNoted(includeNoted)
                    .build();
        } else {
            return InventoryItemCountCondition.builder()
                    .itemName(itemName)
                    .targetCountMin(minCount)
                    .targetCountMax(minCount)                    
                    .includeNoted(includeNoted)
                    .build();
        }
    }
    /**
     * Creates a panel for configuring Bank Item Count conditions
     */
    public static void createBankItemCountPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel) {
        // Title
        JLabel titleLabel = new JLabel("Bank Item Count:");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);
        
        // Item name input
        gbc.gridy++;
        JPanel itemNamePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        itemNamePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel itemNameLabel = new JLabel("Item Name (leave empty for total items):");
        itemNameLabel.setForeground(Color.WHITE);
        itemNamePanel.add(itemNameLabel);
        
        JTextField itemNameField = new JTextField(15);
        itemNameField.setForeground(Color.WHITE);
        itemNameField.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        itemNamePanel.add(itemNameField);
        
        panel.add(itemNamePanel, gbc);
        
        // Count range
        gbc.gridy++;
        JPanel countPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        countPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minCountLabel = new JLabel("Min Count:");
        minCountLabel.setForeground(Color.WHITE);
        countPanel.add(minCountLabel);
        
        SpinnerNumberModel minCountModel = new SpinnerNumberModel(100, 0, Integer.MAX_VALUE, 10);
        JSpinner minCountSpinner = new JSpinner(minCountModel);
        countPanel.add(minCountSpinner);
        
        JLabel maxCountLabel = new JLabel("Max Count:");
        maxCountLabel.setForeground(Color.WHITE);
        countPanel.add(maxCountLabel);
        
        SpinnerNumberModel maxCountModel = new SpinnerNumberModel(100, 0, Integer.MAX_VALUE, 10);
        JSpinner maxCountSpinner = new JSpinner(maxCountModel);
        countPanel.add(maxCountSpinner);
        
        // Link the min and max spinners
        minCountSpinner.addChangeListener(e -> {
            int minValue = (Integer) minCountSpinner.getValue();
            int maxValue = (Integer) maxCountSpinner.getValue();
            if (minValue > maxValue) {
                maxCountSpinner.setValue(minValue);
            }
        });
        
        maxCountSpinner.addChangeListener(e -> {
            int minValue = (Integer) minCountSpinner.getValue();
            int maxValue = (Integer) maxCountSpinner.getValue();
            if (maxValue < minValue) {
                minCountSpinner.setValue(maxValue);
            }
        });
        
        panel.add(countPanel, gbc);
        
        // Options panel
        gbc.gridy++;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        

        
        panel.add(optionsPanel, gbc);
        
        // Add a helpful description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Plugin will stop when you have the target number of items in bank");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        gbc.gridy++;
        JLabel regexLabel = new JLabel("Item name supports regex patterns (.*rune.*)");
        regexLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        regexLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(regexLabel, gbc);
        
        // Store the components for later access
        configPanel.putClientProperty("bankItemNameField", itemNameField);
        configPanel.putClientProperty("bankMinCountSpinner", minCountSpinner);
        configPanel.putClientProperty("bankMaxCountSpinner", maxCountSpinner);        
    }

   

    public static BankItemCountCondition createBankItemCountCondition(JPanel configPanel) {
        JTextField itemNameField = (JTextField) configPanel.getClientProperty("bankItemNameField");
        JSpinner minCountSpinner = (JSpinner) configPanel.getClientProperty("bankMinCountSpinner");
        JSpinner maxCountSpinner = (JSpinner) configPanel.getClientProperty("bankMaxCountSpinner");
        
        
        String itemName = itemNameField.getText().trim();
        int minCount = (Integer) minCountSpinner.getValue();
        int maxCount = (Integer) maxCountSpinner.getValue();        
        
        if (minCount != maxCount) {
            return BankItemCountCondition.builder()
                    .itemName(itemName)
                    .targetCountMin(minCount)
                    .targetCountMax(maxCount)
                    .build();
        } else {
            return BankItemCountCondition.builder()
                    .itemName(itemName)
                    .targetCountMin(minCount)
                    .targetCountMax(minCount)                    
                    .build();
        }
    }
    /**
 * Creates a panel for configuring item collection conditions with enhanced options.
        */
    public static void createItemConfigPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel, boolean stopConditionPanel) {
        // Section title
        JLabel titleLabel = new JLabel(stopConditionPanel ? "Collect Items to Stop:" : "Required Items to Start:");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        panel.add(titleLabel, gbc);
        
        gbc.gridy++;
        
        // Item names input
        JLabel itemsLabel = new JLabel("Item Names (comma-separated, supports regex):");
        itemsLabel.setForeground(Color.WHITE);
        panel.add(itemsLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        JTextField itemsField = new JTextField();
        itemsField.setToolTipText("<html>Examples:<br>" +
                "- 'Bones' - Exact match for Bones<br>" +
                "- 'Shark|Lobster' - Match either Shark OR Lobster<br>" + 
                "- '.*rune$' - All items ending with 'rune'<br>" +
                "- 'Dragon.*,Rune.*' - Multiple patterns (Dragon items AND Rune items)</html>");
        panel.add(itemsField, gbc);
        
        // Logical operator selection (AND/OR) - only visible with multiple items
        gbc.gridy++;
        JPanel logicalPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        logicalPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel logicalLabel = new JLabel("Multiple items logic:");
        logicalLabel.setForeground(Color.WHITE);
        logicalPanel.add(logicalLabel);
        
        JRadioButton andRadioButton = new JRadioButton("Need ALL items (AND)");
        andRadioButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        andRadioButton.setForeground(Color.WHITE);
        andRadioButton.setSelected(true);
        andRadioButton.setToolTipText("All specified items must be collected to satisfy the condition");
        
        JRadioButton orRadioButton = new JRadioButton("Need ANY item (OR)");
        orRadioButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        orRadioButton.setForeground(Color.WHITE);
        orRadioButton.setToolTipText("Any of the specified items will satisfy the condition");
        
        ButtonGroup logicGroup = new ButtonGroup();
        logicGroup.add(andRadioButton);
        logicGroup.add(orRadioButton);
        
        logicalPanel.add(andRadioButton);
        logicalPanel.add(orRadioButton);
        
        // Initially hide the panel - will be shown only when there are commas in the text field
        logicalPanel.setVisible(false);
        panel.add(logicalPanel, gbc);
        
        // Listen for changes in the text field to show/hide the logical panel
        itemsField.getDocument().addDocumentListener(new DocumentListener() {
            private void updateLogicalPanelVisibility() {
                String text = itemsField.getText().trim();
                boolean hasMultipleItems = text.contains(",");
                logicalPanel.setVisible(hasMultipleItems);
                panel.revalidate();
                panel.repaint();
            }
            
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateLogicalPanelVisibility();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                updateLogicalPanelVisibility();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateLogicalPanelVisibility();
            }
        });
        
        // Target amount panel
        gbc.gridy++;
        JPanel amountPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        amountPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel amountLabel = new JLabel("Target Amount:");
        amountLabel.setForeground(Color.WHITE);
        amountPanel.add(amountLabel);
        
     
     
        
        JCheckBox sameAmountCheckBox = new JCheckBox("Same amount for all items");
        sameAmountCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sameAmountCheckBox.setForeground(Color.WHITE);
        sameAmountCheckBox.setSelected(true);
        sameAmountCheckBox.setVisible(false); // Only show with multiple items
        sameAmountCheckBox.setToolTipText("Use the same target amount for all items");
        amountPanel.add(sameAmountCheckBox);
        
    
        
        panel.add(amountPanel, gbc);
        
        // Min/Max panel
        gbc.gridy++;
        JPanel minMaxPanel = new JPanel(new GridLayout(1, 4, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min:");
        minLabel.setForeground(Color.WHITE);
        minMaxPanel.add(minLabel);
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(50, 1, Integer.MAX_VALUE, 10);
        JSpinner minSpinner = new JSpinner(minModel);
        minMaxPanel.add(minSpinner);
        
        JLabel maxLabel = new JLabel("Max:");
        maxLabel.setForeground(Color.WHITE);
        minMaxPanel.add(maxLabel);
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(150, 1, Integer.MAX_VALUE, 10);
        JSpinner maxSpinner = new JSpinner(maxModel);
        minMaxPanel.add(maxSpinner);
        
        minMaxPanel.setVisible(false);
        panel.add(minMaxPanel, gbc);
        
        // Additional options panel
        gbc.gridy++;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JCheckBox includeNotedCheckBox = new JCheckBox("Include noted items");
        includeNotedCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        includeNotedCheckBox.setForeground(Color.WHITE);
        includeNotedCheckBox.setSelected(true);
        includeNotedCheckBox.setToolTipText("<html>If checked, will also count noted versions of the items<br>" +
                "For example, 'Bones' would match both normal and noted bones</html>");
        optionsPanel.add(includeNotedCheckBox);
        
        JCheckBox includeNoneOwnerCheckBox = new JCheckBox("Include unowned items");
        includeNoneOwnerCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        includeNoneOwnerCheckBox.setForeground(Color.WHITE);
        includeNoneOwnerCheckBox.setToolTipText("<html>If checked, items that don't belong to you will also be tracked<br>" +
                "By default, only items that belong to you are counted</html>");
        optionsPanel.add(includeNoneOwnerCheckBox);
        
        panel.add(optionsPanel, gbc);
        
        
        
        
        // Link sameAmountCheckBox visibility to comma presence
        itemsField.getDocument().addDocumentListener(new DocumentListener() {
            private void updateCheckBoxVisibility() {
                String text = itemsField.getText().trim();
                boolean hasMultipleItems = text.contains(",");
                sameAmountCheckBox.setVisible(hasMultipleItems);
                panel.revalidate();
                panel.repaint();
            }
            
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateCheckBoxVisibility();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                updateCheckBoxVisibility();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateCheckBoxVisibility();
            }
        });
        
        // Add value change listeners for min/max validation
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
        
        // Description
        gbc.gridy++;
        JLabel descriptionLabel;
        if (stopConditionPanel) {
            descriptionLabel = new JLabel("Plugin will stop when target amount of items is collected");
        } else {
            descriptionLabel = new JLabel("Plugin will only start when you have the required items");
        }
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Add regex help
        gbc.gridy++;
        JLabel regexHelpLabel = new JLabel("Regex help: Use '|' for OR, '.*' for any characters");
        regexHelpLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        regexHelpLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(regexHelpLabel, gbc);
        
        // Store components
        configPanel.putClientProperty("itemsField", itemsField);
        configPanel.putClientProperty("andRadioButton", andRadioButton);
        configPanel.putClientProperty("sameAmountCheckBox", sameAmountCheckBox);        
        configPanel.putClientProperty("minAmountSpinner", minSpinner);
        configPanel.putClientProperty("maxAmountSpinner", maxSpinner);        
        configPanel.putClientProperty("includeNotedCheckBox", includeNotedCheckBox);
        configPanel.putClientProperty("includeNoneOwnerCheckBox", includeNoneOwnerCheckBox);
    }
   /**
 * Creates an appropriate LootItemCondition or logical condition based on user input
 */
    public static Condition createItemCondition(JPanel configPanel) {
        JTextField itemsField = (JTextField) configPanel.getClientProperty("itemsField");
        JRadioButton andRadioButton = (JRadioButton) configPanel.getClientProperty("andRadioButton");
        JCheckBox sameAmountCheckBox = (JCheckBox) configPanel.getClientProperty("sameAmountCheckBox");        
        JCheckBox includeNotedCheckBox = (JCheckBox) configPanel.getClientProperty("includeNotedCheckBox");
        JCheckBox includeNoneOwnerCheckBox = (JCheckBox) configPanel.getClientProperty("includeNoneOwnerCheckBox");
        
        // Handle potential component errors
        if (itemsField == null) {
            log.error("Items field component not found");
            return null;
        }
        
        boolean includeNoted = includeNotedCheckBox != null && includeNotedCheckBox.isSelected();
        boolean includeNoneOwner = includeNoneOwnerCheckBox != null && includeNoneOwnerCheckBox.isSelected();
        
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
        
        // If only one item, create a simple LootItemCondition
        if (itemNames.size() == 1) {
            
            JSpinner minAmountSpinner = (JSpinner) configPanel.getClientProperty("minAmountSpinner");
            JSpinner maxAmountSpinner = (JSpinner) configPanel.getClientProperty("maxAmountSpinner");
            
            int minAmount = minAmountSpinner != null ? (Integer) minAmountSpinner.getValue() : 1;
            int maxAmount = maxAmountSpinner != null ? (Integer) maxAmountSpinner.getValue() : minAmount;
            
            return LootItemCondition.createRandomized(itemNames.get(0), minAmount, maxAmount, includeNoted, includeNoneOwner);
    
        }
        
        // For multiple items, create a logical condition based on selection
        boolean useAndLogic = andRadioButton == null || andRadioButton.isSelected();
        boolean useSameAmount = sameAmountCheckBox != null && sameAmountCheckBox.isSelected();
        
        if (useSameAmount) {
        
            JSpinner minAmountSpinner = (JSpinner) configPanel.getClientProperty("minAmountSpinner");
            JSpinner maxAmountSpinner = (JSpinner) configPanel.getClientProperty("maxAmountSpinner");
            
            int minAmount = minAmountSpinner != null ? (Integer) minAmountSpinner.getValue() : 1;
            int maxAmount = maxAmountSpinner != null ? (Integer) maxAmountSpinner.getValue() : minAmount;
            
            if (useAndLogic) {
                return LootItemCondition.createAndCondition(itemNames, minAmount, maxAmount, includeNoted, includeNoneOwner);
            } else {
                return LootItemCondition.createOrCondition(itemNames, minAmount, maxAmount, includeNoted, includeNoneOwner);
            }
        
        } else {
        
            JSpinner minAmountSpinner = (JSpinner) configPanel.getClientProperty("minAmountSpinner");
            JSpinner maxAmountSpinner = (JSpinner) configPanel.getClientProperty("maxAmountSpinner");
            
            int minAmount = minAmountSpinner != null ? (Integer) minAmountSpinner.getValue() : 1;
            int maxAmount = maxAmountSpinner != null ? (Integer) maxAmountSpinner.getValue() : minAmount;
            
            // Create lists of min/max amounts for each item
            List<Integer> minAmounts = new ArrayList<>();
            List<Integer> maxAmounts = new ArrayList<>();
            
            for (int i = 0; i < itemNames.size(); i++) {
                minAmounts.add(minAmount);
                maxAmounts.add(maxAmount);
            }
            
            if (useAndLogic) {
                LogicalCondition condition = LootItemCondition.createAndCondition(itemNames, minAmounts, maxAmounts, includeNoted, includeNoneOwner);                                                          
                return condition;
            } else {
                // For OrCondition with different parameters, we need to use the overloaded version
                return LootItemCondition.createOrCondition(itemNames, minAmounts, maxAmounts, includeNoted, includeNoneOwner);
            }
           
        }
    }

    /**
     * Creates a panel for configuring GatheredResourceCondition
     */
    public static void createGatheredResourcePanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel) {
        // Section title
        JLabel titleLabel = new JLabel("Gathered Resource Condition:");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);
        
        // Item name input
        gbc.gridy++;
        JPanel itemNamePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        itemNamePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel itemNameLabel = new JLabel("Resource Name:");
        itemNameLabel.setForeground(Color.WHITE);
        itemNamePanel.add(itemNameLabel);
        
        JTextField itemNameField = new JTextField(15);
        itemNameField.setForeground(Color.WHITE);
        itemNameField.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        itemNamePanel.add(itemNameField);
        
        panel.add(itemNamePanel, gbc);
        
        // Resource type selection (helps with skill detection)
        gbc.gridy++;
        JPanel resourceTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        resourceTypePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel resourceTypeLabel = new JLabel("Resource Type:");
        resourceTypeLabel.setForeground(Color.WHITE);
        resourceTypePanel.add(resourceTypeLabel);
        
        String[] resourceTypes = {"Auto-detect", "Mining", "Fishing", "Woodcutting", "Farming", "Hunter"};
        JComboBox<String> resourceTypeComboBox = new JComboBox<>(resourceTypes);
        resourceTypePanel.add(resourceTypeComboBox);
        
        panel.add(resourceTypePanel, gbc);
        
        // Count panel
        gbc.gridy++;
        JPanel countPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        countPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel countLabel = new JLabel("Target Count:");
        countLabel.setForeground(Color.WHITE);
        countPanel.add(countLabel);
        
        
        
        JPanel minMaxPanel = new JPanel(new GridLayout(1, 4, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min:");
        minLabel.setForeground(Color.WHITE);
        minMaxPanel.add(minLabel);
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(50, 1, 10000, 10);
        JSpinner minSpinner = new JSpinner(minModel);
        minMaxPanel.add(minSpinner);
        
        JLabel maxLabel = new JLabel("Max:");
        maxLabel.setForeground(Color.WHITE);
        minMaxPanel.add(maxLabel);
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(150, 1, 10000, 10);
        JSpinner maxSpinner = new JSpinner(maxModel);
        minMaxPanel.add(maxSpinner);
        
        minMaxPanel.setVisible(false);
        countPanel.add(minMaxPanel);
        
        
        
        panel.add(countPanel, gbc);
        
        // Options panel
        gbc.gridy++;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JCheckBox includeNotedCheckbox = new JCheckBox("Include noted items");
        includeNotedCheckbox.setForeground(Color.WHITE);
        includeNotedCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        includeNotedCheckbox.setSelected(true);
        includeNotedCheckbox.setToolTipText("Count both noted and unnoted versions of the gathered resource");
        optionsPanel.add(includeNotedCheckbox);
        
        panel.add(optionsPanel, gbc);
        
        // Example resources by type
        gbc.gridy++;
        JPanel examplesPanel = new JPanel(new BorderLayout());
        examplesPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel examplesLabel = new JLabel("Examples by type:");
        examplesLabel.setForeground(Color.WHITE);
        examplesPanel.add(examplesLabel, BorderLayout.NORTH);
        
        JLabel examplesContentLabel = new JLabel("<html>Mining: Coal, Iron ore, Gold ore<br>" +
                "Fishing: Shrimp, Trout, Tuna, Shark<br>" +
                "Woodcutting: Logs, Oak logs, Yew logs<br>" +
                "Farming: Potato, Strawberry, Herbs<br>" +
                "Hunter: Bird meat, Rabbit, Chinchompa</html>");
        examplesContentLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        examplesContentLabel.setFont(FontManager.getRunescapeSmallFont());
        examplesPanel.add(examplesContentLabel, BorderLayout.CENTER);
        
        panel.add(examplesPanel, gbc);
        
        // Description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Tracks resources gathered from skilling activities");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Store components for later access
        configPanel.putClientProperty("gatheredItemNameField", itemNameField);
        configPanel.putClientProperty("gatheredResourceType", resourceTypeComboBox);        
        configPanel.putClientProperty("gatheredMinSpinner", minSpinner);
        configPanel.putClientProperty("gatheredMaxSpinner", maxSpinner);        
        configPanel.putClientProperty("gatheredIncludeNotedCheckbox", includeNotedCheckbox);
    }

    /**
     * Creates a GatheredResourceCondition from the panel configuration
     */
    public static GatheredResourceCondition createGatheredResourceCondition(JPanel configPanel) {
        JTextField itemNameField = (JTextField) configPanel.getClientProperty("gatheredItemNameField");
        JComboBox<String> resourceTypeComboBox = (JComboBox<String>) configPanel.getClientProperty("gatheredResourceType");        
        JSpinner minSpinner = (JSpinner) configPanel.getClientProperty("gatheredMinSpinner");
        JSpinner maxSpinner = (JSpinner) configPanel.getClientProperty("gatheredMaxSpinner");        
        JCheckBox includeNotedCheckbox = (JCheckBox) configPanel.getClientProperty("gatheredIncludeNotedCheckbox");
        
        // Get item name
        String itemName = itemNameField.getText().trim();
        if (itemName.isEmpty()) {
            itemName = "resources"; // Default generic name
        }
        
        // Get relevant skills based on resource type selection
        List<Skill> relevantSkills = new ArrayList<>();
        String selectedResourceType = (String) resourceTypeComboBox.getSelectedItem();
        
        if (!"Auto-detect".equals(selectedResourceType)) {
            // Add specific skill based on selection
            switch (selectedResourceType) {
                case "Mining":
                    relevantSkills.add(Skill.MINING);
                    break;
                case "Fishing":
                    relevantSkills.add(Skill.FISHING);
                    break;
                case "Woodcutting":
                    relevantSkills.add(Skill.WOODCUTTING);
                    break;
                case "Farming":
                    relevantSkills.add(Skill.FARMING);
                    break;
                case "Hunter":
                    relevantSkills.add(Skill.HUNTER);
                    break;
            }
        }
        
        // Get target count
        int minCount, maxCount;
        
        minCount = (Integer) minSpinner.getValue();
        maxCount = (Integer) maxSpinner.getValue();


        
        boolean includeNoted = includeNotedCheckbox.isSelected();
        
        // Create the condition
        return GatheredResourceCondition.builder()
                .itemName(itemName)
                .targetCountMin(minCount)
                .targetCountMax(maxCount)
                .includeNoted(includeNoted)
                .relevantSkills(relevantSkills.isEmpty() ? null : relevantSkills)
                .build();
    }

    /**
 * Creates a panel for configuring ProcessItemCondition
 */
public static void createProcessItemPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel) {
    // Section title
    JLabel titleLabel = new JLabel("Process Item Condition:");
    titleLabel.setForeground(Color.WHITE);
    titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.gridwidth = 2;
    panel.add(titleLabel, gbc);
    
    // Tracking mode selection
    gbc.gridy++;
    JPanel trackingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    trackingPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    
    JLabel trackingLabel = new JLabel("Tracking Mode:");
    trackingLabel.setForeground(Color.WHITE);
    trackingPanel.add(trackingLabel);
    
    ButtonGroup trackingGroup = new ButtonGroup();
    
    JRadioButton sourceButton = new JRadioButton("Source Consumption");
    sourceButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    sourceButton.setForeground(Color.WHITE);
    sourceButton.setSelected(true);
    trackingGroup.add(sourceButton);
    trackingPanel.add(sourceButton);
    
    JRadioButton targetButton = new JRadioButton("Target Production");
    targetButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    targetButton.setForeground(Color.WHITE);
    trackingGroup.add(targetButton);
    trackingPanel.add(targetButton);
    
    JRadioButton eitherButton = new JRadioButton("Either");
    eitherButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    eitherButton.setForeground(Color.WHITE);
    trackingGroup.add(eitherButton);
    trackingPanel.add(eitherButton);
    
    JRadioButton bothButton = new JRadioButton("Both");
    bothButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    bothButton.setForeground(Color.WHITE);
    trackingGroup.add(bothButton);
    trackingPanel.add(bothButton);
    
    panel.add(trackingPanel, gbc);
    
    // Source items panel
    gbc.gridy++;
    JPanel sourcePanel = new JPanel(new BorderLayout());
    sourcePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    sourcePanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            "Source Items (items consumed)",
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            FontManager.getRunescapeSmallFont(),
            Color.WHITE
    ));
    
    JPanel sourceInputPanel = new JPanel(new GridLayout(0, 3, 5, 5));
    sourceInputPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    
    JLabel sourceNameLabel = new JLabel("Item Name");
    sourceNameLabel.setForeground(Color.WHITE);
    sourceInputPanel.add(sourceNameLabel);
    
    JLabel sourceQuantityLabel = new JLabel("Quantity Per Process");
    sourceQuantityLabel.setForeground(Color.WHITE);
    sourceInputPanel.add(sourceQuantityLabel);
    
    // Empty label for alignment
    sourceInputPanel.add(new JLabel(""));
    
    JTextField sourceNameField = new JTextField();
    sourceInputPanel.add(sourceNameField);
    
    SpinnerNumberModel sourceQuantityModel = new SpinnerNumberModel(1, 1, 100, 1);
    JSpinner sourceQuantitySpinner = new JSpinner(sourceQuantityModel);
    sourceInputPanel.add(sourceQuantitySpinner);
    
    JButton addSourceButton = new JButton("+");
    addSourceButton.setBackground(ColorScheme.BRAND_ORANGE);
    addSourceButton.setForeground(Color.WHITE);
    sourceInputPanel.add(addSourceButton);
    
    sourcePanel.add(sourceInputPanel, BorderLayout.NORTH);
    
    // Source items list (will be populated dynamically)
    DefaultListModel<String> sourceItemsModel = new DefaultListModel<>();
    JList<String> sourceItemsList = new JList<>(sourceItemsModel);
    sourceItemsList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    sourceItemsList.setForeground(Color.WHITE);
    JScrollPane sourceScrollPane = new JScrollPane(sourceItemsList);
    sourceScrollPane.setPreferredSize(new Dimension(0, 80));
    sourcePanel.add(sourceScrollPane, BorderLayout.CENTER);
    
    // Add source item button action
    addSourceButton.addActionListener(e -> {
        String itemName = sourceNameField.getText().trim();
        if (!itemName.isEmpty()) {
            int quantity = (Integer) sourceQuantitySpinner.getValue();
            sourceItemsModel.addElement(quantity + "x " + itemName);
            sourceNameField.setText("");
        }
    });
    
    panel.add(sourcePanel, gbc);
    
    // Target items panel (similar structure to source panel)
    gbc.gridy++;
    JPanel targetPanel = new JPanel(new BorderLayout());
    targetPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    targetPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            "Target Items (items produced)",
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            FontManager.getRunescapeSmallFont(),
            Color.WHITE
    ));
    
    JPanel targetInputPanel = new JPanel(new GridLayout(0, 3, 5, 5));
    targetInputPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    
    JLabel targetNameLabel = new JLabel("Item Name");
    targetNameLabel.setForeground(Color.WHITE);
    targetInputPanel.add(targetNameLabel);
    
    JLabel targetQuantityLabel = new JLabel("Quantity Per Process");
    targetQuantityLabel.setForeground(Color.WHITE);
    targetInputPanel.add(targetQuantityLabel);
    
    // Empty label for alignment
    targetInputPanel.add(new JLabel(""));
    
    JTextField targetNameField = new JTextField();
    targetInputPanel.add(targetNameField);
    
    SpinnerNumberModel targetQuantityModel = new SpinnerNumberModel(1, 1, 100, 1);
    JSpinner targetQuantitySpinner = new JSpinner(targetQuantityModel);
    targetInputPanel.add(targetQuantitySpinner);
    
    JButton addTargetButton = new JButton("+");
    addTargetButton.setBackground(ColorScheme.BRAND_ORANGE);
    addTargetButton.setForeground(Color.WHITE);
    targetInputPanel.add(addTargetButton);
    
    targetPanel.add(targetInputPanel, BorderLayout.NORTH);
    
    // Target items list
    DefaultListModel<String> targetItemsModel = new DefaultListModel<>();
    JList<String> targetItemsList = new JList<>(targetItemsModel);
    targetItemsList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    targetItemsList.setForeground(Color.WHITE);
    JScrollPane targetScrollPane = new JScrollPane(targetItemsList);
    targetScrollPane.setPreferredSize(new Dimension(0, 80));
    targetPanel.add(targetScrollPane, BorderLayout.CENTER);
    
    // Add target item button action
    addTargetButton.addActionListener(e -> {
        String itemName = targetNameField.getText().trim();
        if (!itemName.isEmpty()) {
            int quantity = (Integer) targetQuantitySpinner.getValue();
            targetItemsModel.addElement(quantity + "x " + itemName);
            targetNameField.setText("");
        }
    });
    
    panel.add(targetPanel, gbc);
    
    // Count panel
    gbc.gridy++;
    JPanel countPanel = new JPanel(new GridLayout(2, 3, 5, 5));
    countPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    
    JLabel countLabel = new JLabel("Target Process Count:");
    countLabel.setForeground(Color.WHITE);
    countPanel.add(countLabel);
  
    // Empty space
    countPanel.add(new JLabel(""));
    
        
    
    JPanel minMaxPanel = new JPanel(new GridLayout(1, 4, 5, 0));
    minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    
    JLabel minLabel = new JLabel("Min:");
    minLabel.setForeground(Color.WHITE);
    minMaxPanel.add(minLabel);
    
    SpinnerNumberModel minModel = new SpinnerNumberModel(25, 1, 10000, 1);
    JSpinner minSpinner = new JSpinner(minModel);
    minMaxPanel.add(minSpinner);
    
    JLabel maxLabel = new JLabel("Max:");
    maxLabel.setForeground(Color.WHITE);
    minMaxPanel.add(maxLabel);
    
    SpinnerNumberModel maxModel = new SpinnerNumberModel(75, 1, 10000, 1);
    JSpinner maxSpinner = new JSpinner(maxModel);
    minMaxPanel.add(maxSpinner);
    
    minMaxPanel.setVisible(false);
    
    
    
    countPanel.add(minMaxPanel);
    
    panel.add(countPanel, gbc);
    
    // Description
    gbc.gridy++;
    JLabel descriptionLabel = new JLabel("Tracks items being processed (crafting, cooking, etc.)");
    descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
    panel.add(descriptionLabel, gbc);
    
    // Store components for later access
    configPanel.putClientProperty("procSourceRadio", sourceButton);
    configPanel.putClientProperty("procTargetRadio", targetButton);
    configPanel.putClientProperty("procEitherRadio", eitherButton);
    configPanel.putClientProperty("procBothRadio", bothButton);
    configPanel.putClientProperty("procSourceItemsModel", sourceItemsModel);
    configPanel.putClientProperty("procTargetItemsModel", targetItemsModel);    
    configPanel.putClientProperty("procMinSpinner", minSpinner);
    configPanel.putClientProperty("procMaxSpinner", maxSpinner);    
}

/**
 * Creates a ProcessItemCondition from the panel configuration
 */
public static ProcessItemCondition createProcessItemCondition(JPanel configPanel) {
    JRadioButton sourceButton = (JRadioButton) configPanel.getClientProperty("procSourceRadio");
    JRadioButton targetButton = (JRadioButton) configPanel.getClientProperty("procTargetRadio");
    JRadioButton eitherButton = (JRadioButton) configPanel.getClientProperty("procEitherRadio");
    JRadioButton bothButton = (JRadioButton) configPanel.getClientProperty("procBothRadio");
    
    DefaultListModel<String> sourceItemsModel = (DefaultListModel<String>) configPanel.getClientProperty("procSourceItemsModel");
    DefaultListModel<String> targetItemsModel = (DefaultListModel<String>) configPanel.getClientProperty("procTargetItemsModel");
        
    JSpinner minSpinner = (JSpinner) configPanel.getClientProperty("procMinSpinner");
    JSpinner maxSpinner = (JSpinner) configPanel.getClientProperty("procMaxSpinner");    
    
    // Determine tracking mode
    ProcessItemCondition.TrackingMode trackingMode;
    if (sourceButton.isSelected()) {
        trackingMode = ProcessItemCondition.TrackingMode.SOURCE_CONSUMPTION;
    } else if (targetButton.isSelected()) {
        trackingMode = ProcessItemCondition.TrackingMode.TARGET_PRODUCTION;
    } else if (eitherButton.isSelected()) {
        trackingMode = ProcessItemCondition.TrackingMode.EITHER;
    } else {
        trackingMode = ProcessItemCondition.TrackingMode.BOTH;
    }
    
    // Parse source items
    List<ProcessItemCondition.ItemTracker> sourceItems = new ArrayList<>();
    for (int i = 0; i < sourceItemsModel.getSize(); i++) {
        String entry = sourceItemsModel.getElementAt(i);
        // Parse "Nx ItemName" format
        int xIndex = entry.indexOf('x');
        if (xIndex > 0) {
            try {
                int quantity = Integer.parseInt(entry.substring(0, xIndex).trim());
                String itemName = entry.substring(xIndex + 1).trim();
                sourceItems.add(new ProcessItemCondition.ItemTracker(itemName, quantity));
            } catch (NumberFormatException e) {
                // If parsing fails, default to quantity 1
                sourceItems.add(new ProcessItemCondition.ItemTracker(entry, 1));
            }
        } else {
            sourceItems.add(new ProcessItemCondition.ItemTracker(entry, 1));
        }
    }
    
    // Parse target items
    List<ProcessItemCondition.ItemTracker> targetItems = new ArrayList<>();
    for (int i = 0; i < targetItemsModel.getSize(); i++) {
        String entry = targetItemsModel.getElementAt(i);
        // Parse "Nx ItemName" format
        int xIndex = entry.indexOf('x');
        if (xIndex > 0) {
            try {
                int quantity = Integer.parseInt(entry.substring(0, xIndex).trim());
                String itemName = entry.substring(xIndex + 1).trim();
                targetItems.add(new ProcessItemCondition.ItemTracker(itemName, quantity));
            } catch (NumberFormatException e) {
                // If parsing fails, default to quantity 1
                targetItems.add(new ProcessItemCondition.ItemTracker(entry, 1));
            }
        } else {
            targetItems.add(new ProcessItemCondition.ItemTracker(entry, 1));
        }
    }
    
    // Get target count
    int minCount, maxCount;
    
    minCount = (Integer) minSpinner.getValue();
    maxCount = (Integer) maxSpinner.getValue();
    
    
    
    
    // Build the condition
    return ProcessItemCondition.builder()
            .sourceItems(sourceItems)
            .targetItems(targetItems)
            .trackingMode(trackingMode)
            .targetCountMin(minCount)
            .targetCountMax(maxCount)
            .build();
}

/**
 * Sets up panel with values from an existing condition
 * 
 * @param panel The panel containing the UI components
 * @param condition The resource condition to read values from
 */
public static void setupResourceCondition(JPanel panel, Condition condition) {
    if (condition == null) {
        return;
    }

    if (condition instanceof InventoryItemCountCondition) {
        setupInventoryItemCountCondition(panel, (InventoryItemCountCondition) condition);
    } else if (condition instanceof BankItemCountCondition) {
        setupBankItemCountCondition(panel, (BankItemCountCondition) condition);
    } else if (condition instanceof LootItemCondition || condition instanceof LogicalCondition) {
        setupLootItemCondition(panel, condition);
    } else if (condition instanceof ProcessItemCondition) {
        setupProcessItemCondition(panel, (ProcessItemCondition) condition);
    } else if (condition instanceof GatheredResourceCondition) {
        setupGatheredResourceCondition(panel, (GatheredResourceCondition) condition);
    }
}

/**
 * Sets up inventory item count condition panel
 */
private static void setupInventoryItemCountCondition(JPanel panel, InventoryItemCountCondition condition) {
    JTextField itemNameField = (JTextField) panel.getClientProperty("itemNameField");
    JSpinner minCountSpinner = (JSpinner) panel.getClientProperty("minCountSpinner");
    JSpinner maxCountSpinner = (JSpinner) panel.getClientProperty("maxCountSpinner");
    JCheckBox includeNotedCheckbox = (JCheckBox) panel.getClientProperty("includeNotedCheckbox");    
    
    if (itemNameField != null) {
        itemNameField.setText(condition.getItemPattern().toString());
    }
    
    if (minCountSpinner != null && maxCountSpinner != null) {
        
        minCountSpinner.setValue(condition.getTargetCountMin());
        maxCountSpinner.setValue(condition.getTargetCountMax());
                
        
    }         
    if (includeNotedCheckbox != null) {
        includeNotedCheckbox.setSelected(condition.isIncludeNoted());
    }
}

/**
 * Sets up bank item count condition panel
 */
private static void setupBankItemCountCondition(JPanel panel, BankItemCountCondition condition) {
    JTextField itemNameField = (JTextField) panel.getClientProperty("bankItemNameField");
    JSpinner minCountSpinner = (JSpinner) panel.getClientProperty("bankMinCountSpinner");
    JSpinner maxCountSpinner = (JSpinner) panel.getClientProperty("bankMaxCountSpinner");
    
    
    if (itemNameField != null) {
        itemNameField.setText(condition.getItemPattern().toString());
    }
    
    if (minCountSpinner != null && maxCountSpinner != null) {
        
        minCountSpinner.setValue(condition.getTargetCountMin());
        maxCountSpinner.setValue(condition.getTargetCountMax());       
        
    }
}

/**
 * Sets up item collection condition panel
 */
private static void setupLootItemCondition(JPanel panel, Condition condition) {
    // Retrieve the UI components
    JTextField itemsField = (JTextField) panel.getClientProperty("itemsField");
    JRadioButton andRadioButton = (JRadioButton) panel.getClientProperty("andRadioButton");
    JRadioButton orRadioButton = (JRadioButton) panel.getClientProperty("orRadioButton");    
    JCheckBox sameAmountCheckBox = (JCheckBox) panel.getClientProperty("sameAmountCheckBox");    
    JSpinner minAmountSpinner = (JSpinner) panel.getClientProperty("minAmountSpinner");
    JSpinner maxAmountSpinner = (JSpinner) panel.getClientProperty("maxAmountSpinner");
    JCheckBox includeNotedCheckBox = (JCheckBox) panel.getClientProperty("includeNotedCheckBox");
    JCheckBox includeNoneOwnerCheckBox = (JCheckBox) panel.getClientProperty("includeNoneOwnerCheckBox");
    
    if (condition instanceof LootItemCondition || condition instanceof LogicalCondition) {
        Condition conditionBaseCondition = condition;
        boolean isAndLogic = true;
        if (!(condition instanceof LootItemCondition)) {
            conditionBaseCondition = ((LogicalCondition) condition).getConditions().get(0);
            if (condition instanceof OrCondition) {
                isAndLogic =false;
            }
        }
        LootItemCondition itemCondition = (LootItemCondition) conditionBaseCondition;
        
        // Set item names
        if (itemsField != null) {
            StringBuilder itemsText = new StringBuilder();
            // for now only allow update 1 item at time
            //for (String pattern : itemCondition.getItemPattern()) {
            //    if (itemsText.length() > 0) {
            //        itemsText.append(",");
            //    }
             //   itemsText.append(pattern);
            //}
            itemsText.append(itemCondition.getItemPattern());            
            itemsField.setText(String.join(",", itemsText));
        }
        
        // Set logical operator
        if (andRadioButton != null && orRadioButton != null) {
            
            andRadioButton.setSelected(isAndLogic);
            orRadioButton.setSelected(!isAndLogic);
        }
                        
        // Set min/max
        if (minAmountSpinner != null && maxAmountSpinner != null) {
            minAmountSpinner.setValue(itemCondition.getTargetAmountMin());
            maxAmountSpinner.setValue(itemCondition.getTargetAmountMax());
        }
        
        // Set same amount for all
        if (sameAmountCheckBox != null) {
            sameAmountCheckBox.setSelected(false);
        }
        if (includeNotedCheckBox != null) {
            includeNotedCheckBox.setSelected(itemCondition.isIncludeNoted());
        }
        if (includeNoneOwnerCheckBox != null) {
            includeNoneOwnerCheckBox.setSelected(itemCondition.isIncludeNoneOwner());
        }

    }
}

/**
 * Sets up process item condition panel
 */
private static void setupProcessItemCondition(JPanel panel, ProcessItemCondition condition) {
    
    JTextField inputItemsField = (JTextField) panel.getClientProperty("procSourceItemsModel");
    JTextField outputItemsField = (JTextField) panel.getClientProperty("procTargetItemsModel");    
    JSpinner minCountSpinner = (JSpinner) panel.getClientProperty("procMinSpinner");
    JSpinner maxCountSpinner = (JSpinner) panel.getClientProperty("procMaxSpinner");
    
    JRadioButton eitherButton = (JRadioButton) panel.getClientProperty("eitherButton");
    JRadioButton targetButton = (JRadioButton) panel.getClientProperty("targetButton");
    JRadioButton sourceButton = (JRadioButton) panel.getClientProperty("sourceButton");
    JRadioButton procBothRadio = (JRadioButton) panel.getClientProperty("procBothRadio");
   
    
    
    
    
  
    ProcessItemCondition.TrackingMode trackingMode = condition.getTrackingMode();
    switch (trackingMode) {   
        case SOURCE_CONSUMPTION:
            sourceButton.setSelected(true);
                        break;
        case TARGET_PRODUCTION:
            targetButton.setSelected(true);
            break;
        case EITHER:
            eitherButton.setSelected(true);    
            break;
        case BOTH:
            procBothRadio.setSelected(true);            
            break;
    }
  
    
    if (inputItemsField != null) {
        inputItemsField.setText(String.join(",", condition.getInputItemPatternStrings()));
    }
    
    if (outputItemsField != null) {
        outputItemsField.setText(String.join(",", condition.getOutputItemPatternStrings()));
    }
     

    
    if (minCountSpinner != null && maxCountSpinner != null) {
        minCountSpinner.setValue(condition.getTargetCountMin());
        maxCountSpinner.setValue(condition.getTargetCountMax());
    }
}

/**
 * Sets up gathered resource condition panel
 */
private static void setupGatheredResourceCondition(JPanel panel, GatheredResourceCondition condition) {
    JComboBox<String> resourceTypeComboBox = (JComboBox<String>) panel.getClientProperty("resourceTypeComboBox");
    JTextField resourceNameField = (JTextField) panel.getClientProperty("resourceNameField");    
    JSpinner minCountSpinner = (JSpinner) panel.getClientProperty("minCountSpinner");
    JSpinner maxCountSpinner = (JSpinner) panel.getClientProperty("maxCountSpinner");
    
    if (resourceTypeComboBox != null) {
        resourceTypeComboBox.setSelectedItem("Auto-detect");
    }
    
    if (resourceNameField != null) {
        resourceNameField.setText(condition.getItemPatternString());
    }
                
    
    if (minCountSpinner != null && maxCountSpinner != null) {
        minCountSpinner.setValue(condition.getTargetCountMin());
        maxCountSpinner.setValue(condition.getTargetCountMax());
    }
}
}
