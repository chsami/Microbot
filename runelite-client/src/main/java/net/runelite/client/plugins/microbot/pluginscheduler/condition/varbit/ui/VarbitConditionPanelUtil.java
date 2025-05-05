package net.runelite.client.plugins.microbot.pluginscheduler.condition.varbit.ui;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.varbit.VarbitCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.varbit.VarbitUtil;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

import java.util.*;
import java.util.List;


/**
 * Utility class for creating VarbitCondition configuration UI panels.
 */
@Slf4j
public class VarbitConditionPanelUtil {

    /**
     * Callback interface for variable selection
     */
    private interface VarSelectCallback {
        void onVarSelected(int id);
    }

    /**
     * Creates a panel for configuring VarbitCondition
     * 
     * @param panel The panel to add components to
     * @param gbc GridBagConstraints for layout
     * @param specificCategory Optional specific category to restrict selection to (null for general panel)
     */
    public static void createVarbitConditionPanel(JPanel panel, GridBagConstraints gbc, String specificCategory) {
        // Main label
        String titleText = specificCategory == null 
            ? "Varbit Condition:"
            : "Collection Log - " + specificCategory + " Condition:";
        
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);
        
        // Create var type selector (disabled if category is specified)
        gbc.gridy++;
        JPanel varTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        varTypePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel varTypeLabel = new JLabel("Variable Type:");
        varTypeLabel.setForeground(Color.WHITE);
        varTypePanel.add(varTypeLabel);
        
        String[] varTypes = specificCategory != null ? new String[]{"Varbit"} : new String[]{"Varbit", "VarPlayer"};
        JComboBox<String> varTypeComboBox = new JComboBox<>(varTypes);
        varTypeComboBox.setPreferredSize(new Dimension(120, varTypeComboBox.getPreferredSize().height));
        varTypeComboBox.setEnabled(specificCategory == null); // Only enable if no specific category
        varTypePanel.add(varTypeComboBox);
        
        panel.add(varTypePanel, gbc);
        
        // Add mode selection (relative vs absolute)
        gbc.gridy++;
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel modeLabel = new JLabel("Mode:");
        modeLabel.setForeground(Color.WHITE);
        modePanel.add(modeLabel);
        
        JRadioButton absoluteButton = new JRadioButton("Absolute Value");
        absoluteButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        absoluteButton.setForeground(Color.WHITE);
        absoluteButton.setSelected(true); // Default to absolute mode
        absoluteButton.setToolTipText("Track a specific target value");
        
        JRadioButton relativeButton = new JRadioButton("Relative Change");
        relativeButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        relativeButton.setForeground(Color.WHITE);
        relativeButton.setToolTipText("Track changes from the current value");
        
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(absoluteButton);
        modeGroup.add(relativeButton);
        
        modePanel.add(absoluteButton);
        modePanel.add(relativeButton);
        panel.add(modePanel, gbc);
        
        // Create ID input based on if we have a specific category
        JTextField idField = new JTextField(8);
        
        // Initialize variables that will hold references to the category comboboxes
        JComboBox<String> generalCategoryComboBox = null;
        JComboBox<String> entriesComboBox = null;
        
        if (specificCategory == null) {
            // For general panel, show category selector and direct ID input
            gbc.gridy++;
            JPanel categoryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            categoryPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            
            JLabel categoryLabel = new JLabel("Category:");
            categoryLabel.setForeground(Color.WHITE);
            categoryPanel.add(categoryLabel);
            
            // Create the category selection combobox
            generalCategoryComboBox = new JComboBox<>(VarbitUtil.getCategoryNames());
            generalCategoryComboBox.setPreferredSize(new Dimension(150, generalCategoryComboBox.getPreferredSize().height));
            categoryPanel.add(generalCategoryComboBox);
            
            panel.add(categoryPanel, gbc);
            
            // Create ID input panel with dropdown for common Varbits
            gbc.gridy++;
            JPanel idPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            idPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            
            JLabel idLabel = new JLabel("Variable ID:");
            idLabel.setForeground(Color.WHITE);
            idPanel.add(idLabel);
            
            // Numeric field for direct ID input
            idField.setToolTipText("Enter the Varbit ID or VarPlayer ID directly");
            idPanel.add(idField);
            
            // Add lookup button
            JButton lookupButton = new JButton("Lookup");
            lookupButton.setBackground(ColorScheme.BRAND_ORANGE);
            lookupButton.setForeground(Color.WHITE);
            lookupButton.addActionListener(e -> {
                showVarLookupDialog(panel, varTypeComboBox.getSelectedItem().equals("Varbit"), id -> {
                    idField.setText(String.valueOf(id));
                });
            });
            idPanel.add(lookupButton);
            
            // Add dropdown for entries from selected category
            List<String> varbitOptions = new ArrayList<>();
            varbitOptions.add("Select Varbit");
            entriesComboBox = new JComboBox<>(varbitOptions.toArray(new String[0]));
            entriesComboBox.setPreferredSize(new Dimension(200, entriesComboBox.getPreferredSize().height));
            entriesComboBox.setToolTipText("Select a predefined Varbit");
            
            // Create a final reference to the entries combobox for use in the lambda
            final JComboBox<String> finalEntriesComboBox = entriesComboBox;
            
            entriesComboBox.addActionListener(e -> {
                if (finalEntriesComboBox.getSelectedIndex() > 0) {
                    String selected = (String) finalEntriesComboBox.getSelectedItem();
                    if (selected != null && !selected.isEmpty()) {
                        int openParen = selected.indexOf('(');
                        int closeParen = selected.indexOf(')');
                        if (openParen >= 0 && closeParen > openParen) {
                            String idStr = selected.substring(openParen + 1, closeParen).trim();
                            idField.setText(idStr);
                        }
                    }
                }
            });
            
            // Create a final reference to the category combobox for use in the lambda
            final JComboBox<String> finalCategoryComboBox = generalCategoryComboBox;
            final JComboBox<String> finalEntriesComboBox2 = entriesComboBox;
            
            // Update the varbit combobox when category changes
            generalCategoryComboBox.addActionListener(e -> {
                String category = (String) finalCategoryComboBox.getSelectedItem();
                updateVarbitComboBoxByCategory(finalEntriesComboBox2, category);
            });
            
            idPanel.add(entriesComboBox);
            
            // Add a name label that shows the constant name if available
            JLabel constantNameLabel = new JLabel("");
            constantNameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            idPanel.add(constantNameLabel);
            
            // Update constant name when varId changes
            idField.getDocument().addDocumentListener(new DocumentListener() {
                private void update() {
                    try {
                        int id = Integer.parseInt(idField.getText().trim());
                        boolean isVarbit = varTypeComboBox.getSelectedItem().equals("Varbit");
                        String name = VarbitUtil.getConstantNameForId(isVarbit, id);
                        if (name != null && !name.isEmpty()) {
                            constantNameLabel.setText(name);
                        } else {
                            constantNameLabel.setText("(Unknown ID)");
                        }
                    } catch (NumberFormatException ex) {
                        constantNameLabel.setText("");
                    }
                }
                
                @Override
                public void insertUpdate(DocumentEvent e) { update(); }
                
                @Override
                public void removeUpdate(DocumentEvent e) { update(); }
                
                @Override
                public void changedUpdate(DocumentEvent e) { update(); }
            });
            
            panel.add(idPanel, gbc);
            
            // Store components for later
            panel.putClientProperty("varbitConstantNameLabel", constantNameLabel);
        } else {
            // For category-specific panel (Boss/Minigame), show only dropdown
            gbc.gridy++;
            JPanel idPanel = new JPanel(new BorderLayout());
            idPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            
            String labelText = "Select " + (specificCategory.equals("Bosses") ? "Boss:" : "Minigame:");
            JLabel idLabel = new JLabel(labelText);
            idLabel.setForeground(Color.WHITE);
            idPanel.add(idLabel, BorderLayout.WEST);
            
            // Create a hidden field to store the selected ID
            idField.setVisible(false);
            
            // Initialize the varbit categories if needed
            VarbitUtil.initializeVarbitCategories();
            
            // Populate the combobox with entries from the specific category
            DefaultComboBoxModel<String> entriesModel = new DefaultComboBoxModel<>();
            entriesModel.addElement("Select a " + (specificCategory.equals("Bosses") ? "Boss" : "Minigame") + "...");
            
            List<VarbitUtil.VarEntry> categoryEntries = new ArrayList<>();
            for (Map.Entry<Integer, String> entry : VarbitUtil.getAllVarbitEntries().entrySet()) {
                if (specificCategory.equals("Bosses")) {
                    if (entry.getValue().startsWith("COLLECTION_BOSSES_") || 
                        entry.getValue().startsWith("COLLECTION_RAIDS_")) {
                        String formattedName = VarbitUtil.formatConstantName(entry.getValue());
                        categoryEntries.add(new VarbitUtil.VarEntry(entry.getKey(), formattedName));
                    }
                } else if (specificCategory.equals("Minigames")) {
                    if (entry.getValue().startsWith("COLLECTION_MINIGAMES_")) {
                        String formattedName = VarbitUtil.formatConstantName(entry.getValue());
                        categoryEntries.add(new VarbitUtil.VarEntry(entry.getKey(), formattedName));
                    }
                }
            }
            
            // Sort entries by name
            categoryEntries.sort(Comparator.comparing(e -> e.name));
            
            // Add each entry to the combo box model
            for (VarbitUtil.VarEntry entry : categoryEntries) {
                entriesModel.addElement(entry.name + " (" + entry.id + ")");
            }
            
            entriesComboBox = new JComboBox<>(entriesModel);
            entriesComboBox.setPreferredSize(new Dimension(350, entriesComboBox.getPreferredSize().height));
            entriesComboBox.setToolTipText("Select a " + specificCategory.toLowerCase() + " from the Collection Log");
            
            // Create a final reference to the combobox for use in the lambda
            final JComboBox<String> finalEntriesComboBox = entriesComboBox;
            
            // Update the hidden field when selection changes
            entriesComboBox.addActionListener(e -> {
                if (finalEntriesComboBox.getSelectedIndex() > 0) {
                    String selected = (String) finalEntriesComboBox.getSelectedItem();
                    if (selected != null && !selected.isEmpty()) {
                        int openParen = selected.indexOf('(');
                        int closeParen = selected.indexOf(')');
                        if (openParen >= 0 && closeParen > openParen) {
                            String idStr = selected.substring(openParen + 1, closeParen);
                            idField.setText(idStr);
                        }
                    }
                }
            });
            
            idPanel.add(entriesComboBox, BorderLayout.CENTER);
            panel.add(idPanel, gbc);
        }
        
        // Create comparison operator selector
        gbc.gridy++;
        JPanel operatorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        operatorPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel operatorLabel = new JLabel("Comparison:");
        operatorLabel.setForeground(Color.WHITE);
        operatorPanel.add(operatorLabel);
        
        // Get operator names from enum
        String[] operators = new String[VarbitCondition.ComparisonOperator.values().length];
        for (int i = 0; i < VarbitCondition.ComparisonOperator.values().length; i++) {
            operators[i] = VarbitCondition.ComparisonOperator.values()[i].getDisplayName();
        }
        
        JComboBox<String> operatorComboBox = new JComboBox<>(operators);
        operatorComboBox.setPreferredSize(new Dimension(150, operatorComboBox.getPreferredSize().height));
        operatorPanel.add(operatorComboBox);
        
        panel.add(operatorPanel, gbc);
        
        // Create target value input with randomization option
        gbc.gridy++;
        JPanel targetValuePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        targetValuePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel targetValueLabel = new JLabel("Target Value:");
        targetValueLabel.setForeground(Color.WHITE);
        targetValuePanel.add(targetValueLabel);
        
        SpinnerNumberModel targetValueModel = new SpinnerNumberModel(1, 0, Integer.MAX_VALUE, 1);
        JSpinner targetValueSpinner = new JSpinner(targetValueModel);
        targetValuePanel.add(targetValueSpinner);
        
        JCheckBox randomizeCheckBox = new JCheckBox("Randomize");
        randomizeCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckBox.setForeground(Color.WHITE);
        targetValuePanel.add(randomizeCheckBox);
        
        panel.add(targetValuePanel, gbc);
        
        // Min/Max panel for randomization
        gbc.gridy++;
        JPanel minMaxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minValueLabel = new JLabel("Min Value:");
        minValueLabel.setForeground(Color.WHITE);
        minMaxPanel.add(minValueLabel);
        
        SpinnerNumberModel minValueModel = new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1);
        JSpinner minValueSpinner = new JSpinner(minValueModel);
        minMaxPanel.add(minValueSpinner);
        
        JLabel maxValueLabel = new JLabel("Max Value:");
        maxValueLabel.setForeground(Color.WHITE);
        minMaxPanel.add(maxValueLabel);
        
        SpinnerNumberModel maxValueModel = new SpinnerNumberModel(10, 0, Integer.MAX_VALUE, 1);
        JSpinner maxValueSpinner = new JSpinner(maxValueModel);
        minMaxPanel.add(maxValueSpinner);
        
        minMaxPanel.setVisible(false); // Initially hidden
        panel.add(minMaxPanel, gbc);
        
        // Set up randomize checkbox behavior
        randomizeCheckBox.addChangeListener(e -> {
            minMaxPanel.setVisible(randomizeCheckBox.isSelected());
            targetValueSpinner.setEnabled(!randomizeCheckBox.isSelected());
            
            if (randomizeCheckBox.isSelected()) {
                int value = (Integer) targetValueSpinner.getValue();
                
                // Set reasonable min/max values based on current target
                minValueSpinner.setValue(Math.max(0, value - 5));
                maxValueSpinner.setValue(value + 5);
            }
            
            panel.revalidate();
            panel.repaint();
        });
        
        // Set up min/max validation
        minValueSpinner.addChangeListener(e -> {
            int min = (Integer) minValueSpinner.getValue();
            int max = (Integer) maxValueSpinner.getValue();
            
            if (min > max) {
                maxValueSpinner.setValue(min);
            }
        });
        
        maxValueSpinner.addChangeListener(e -> {
            int min = (Integer) minValueSpinner.getValue();
            int max = (Integer) maxValueSpinner.getValue();
            
            if (max < min) {
                minValueSpinner.setValue(max);
            }
        });
        
        // Current value display to help the user
        gbc.gridy++;
        JPanel currentValuePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        currentValuePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel currentValueLabel = new JLabel("Current Value:");
        currentValueLabel.setForeground(Color.WHITE);
        currentValuePanel.add(currentValueLabel);
        
        JLabel currentValueDisplay = new JLabel("--");
        currentValueDisplay.setForeground(Color.YELLOW);
        currentValuePanel.add(currentValueDisplay);
        
        JButton checkValueButton = new JButton("Check Current Value");
        checkValueButton.addActionListener(e -> {
            try {
                String idText = idField.getText().trim();
                if (idText.isEmpty()) {
                    if (specificCategory != null) {
                        currentValueDisplay.setText("Please select a " + 
                            (specificCategory.equals("Bosses") ? "boss" : "minigame") + " first");
                    } else {
                        currentValueDisplay.setText("Please enter a valid ID");
                    }
                    return;
                }
                
                int varId = Integer.parseInt(idText);
                boolean isVarbit = varTypeComboBox.getSelectedItem().equals("Varbit");
                int value;
                
                if (isVarbit) {
                    value = Microbot.getVarbitValue(varId);
                } else {
                    value = Microbot.getVarbitPlayerValue(varId);
                }
                
                currentValueDisplay.setText(String.valueOf(value));
                
                // If relative mode is enabled, update the description to show the potential target
                if (relativeButton.isSelected()) {
                    int targetValue = (Integer) targetValueSpinner.getValue();
                    String operator = (String) operatorComboBox.getSelectedItem();
                    
                    if (operator.contains("greater")) {
                        currentValueDisplay.setText(value + " (target would be " + (value + targetValue) + ")");
                    } else if (operator.contains("less")) {
                        currentValueDisplay.setText(value + " (target would be " + (value - targetValue) + ")");
                    } else {
                        currentValueDisplay.setText(value + " (target would be " + (value + targetValue) + ")");
                    }
                }
            } catch (NumberFormatException ex) {
                currentValueDisplay.setText("Invalid ID");
            } catch (Exception ex) {
                currentValueDisplay.setText("Error: " + ex.getMessage());
            }
        });
        currentValuePanel.add(checkValueButton);
        
        panel.add(currentValuePanel, gbc);
        
        // Update target value label based on selected mode
        relativeButton.addActionListener(e -> {
            targetValueLabel.setText("Value Change:");
        });
        
        absoluteButton.addActionListener(e -> {
            targetValueLabel.setText("Target Value:");
        });
        
        // Add a helpful description
        gbc.gridy++;
        String descriptionText;
        if (specificCategory == null) {
            descriptionText = "<html>Varbits and VarPlayers are game values that track states like quest progress, <br/>minigame scores, etc. Great for tracking game completion objectives.</html>";
        } else if (specificCategory.equals("Bosses")) {
            descriptionText = "<html>Collection Log Boss varbits track your boss kills and achievements.<br/>Generally, values of 1 indicate completion or a kill count achievement.</html>";
        } else {
            descriptionText = "<html>Collection Log Minigame varbits track your progress in minigames.<br/>Generally, values of 1 indicate completion or a kill count achievement.</html>";
        }
        
        JLabel descriptionLabel = new JLabel(descriptionText);
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Store components for later access using unified naming scheme
        panel.putClientProperty("varbitTypeComboBox", varTypeComboBox);
        panel.putClientProperty("varbitIdField", idField);
        panel.putClientProperty("varbitOperatorComboBox", operatorComboBox);
        panel.putClientProperty("varbitTargetValueSpinner", targetValueSpinner);
        panel.putClientProperty("varbitCategoryEntriesComboBox", entriesComboBox);
        
        // Store the category selector only if we're in the general panel
        if (specificCategory == null && generalCategoryComboBox != null) {
            panel.putClientProperty("varbitCategoryComboBox", generalCategoryComboBox);
        } else if (specificCategory != null) {
            // Store the specific category as a string property
            panel.putClientProperty("varbitSpecificCategory", specificCategory);
        }
        
        panel.putClientProperty("varbitRelativeMode", relativeButton);
        panel.putClientProperty("varbitAbsoluteMode", absoluteButton);
        panel.putClientProperty("varbitCurrentValueDisplay", currentValueDisplay);
        panel.putClientProperty("varbitRandomize", randomizeCheckBox);
        panel.putClientProperty("varbitMinValueSpinner", minValueSpinner);
        panel.putClientProperty("varbitMaxValueSpinner", maxValueSpinner);
        panel.putClientProperty("varbitMinMaxPanel", minMaxPanel);
    }
    
    /**
     * Creates a panel for configuring general VarbitCondition
     * 
     * @param panel The panel to add components to
     * @param gbc GridBagConstraints for layout     
     */
    public static void createVarbitConditionPanel(JPanel panel, GridBagConstraints gbc) {
        createVarbitConditionPanel(panel, gbc, null);
    }
    
    /**
     * Creates a panel specifically for minigame-related varbit conditions
     * 
     * @param panel The panel to add components to
     * @param gbc GridBagConstraints for layout
     */
    public static void createMinigameVarbitPanel(JPanel panel, GridBagConstraints gbc) {
        createVarbitConditionPanel(panel, gbc, "Minigames");
    }
    
    /**
     * Creates a panel specifically for boss-related varbit conditions
     * 
     * @param panel The panel to add components to
     * @param gbc GridBagConstraints for layout
     */
    public static void createBossVarbitPanel(JPanel panel, GridBagConstraints gbc) {
        createVarbitConditionPanel(panel, gbc, "Bosses");
    }
            
    // ... rest of the code remains unchanged ...
    
    /**
     * Updates the varbit combo box to show only items from a specific category
     * 
     * @param comboBox The combo box to update
     * @param category The category to filter by
     */
    private static void updateVarbitComboBoxByCategory(JComboBox<String> comboBox, String category) {
        comboBox.removeAllItems();
        comboBox.addItem("Select Varbit");
        
        List<VarbitUtil.VarEntry> entries = VarbitUtil.getVarbitEntriesByCategory(category);
        
        if (category.equals("Other") && entries.isEmpty()) {
            // For "Other" category, include all entries if not specifically categorized
            Map<Integer, String> varbitConstantMap = VarbitUtil.getAllVarbitEntries();
            for (Map.Entry<Integer, String> entry : varbitConstantMap.entrySet()) {
                String formattedName = VarbitUtil.formatConstantName(entry.getValue());
                comboBox.addItem(formattedName + " (" + entry.getKey() + ")");
            }
        } else {
            // Sort entries by name
            entries.sort(Comparator.comparing(e -> e.name));
            
            // Add all entries from the selected category
            for (VarbitUtil.VarEntry entry : entries) {
                comboBox.addItem(entry.name + " (" + entry.id + ")");
            }
        }
    }
    
    
    /**
     * Shows a dialog to select from known varbits or varplayers
     */
    private static void showVarLookupDialog(Component parent, boolean isVarbit, VarSelectCallback callback) {
        // Create a dialog for variable selection
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), 
            (isVarbit ? "Select Varbit" : "Select VarPlayer"), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(600, 500);
        dialog.setLocationRelativeTo(parent);
        
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Add category filter dropdown
        JPanel filterPanel = new JPanel(new BorderLayout());
        filterPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        filterPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        
        JPanel categoryPanel = new JPanel(new BorderLayout());
        categoryPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        categoryPanel.add(new JLabel("Category:"), BorderLayout.WEST);
        
        JComboBox<String> categoryComboBox = new JComboBox<>(
            new String[]{"All Categories", "Collection Log", "Bosses", "Minigames", "Quests", "Skills", "Diaries", "Features", "Items", "Other"}
        );
        categoryPanel.add(categoryComboBox, BorderLayout.CENTER);
        
        // Search field
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        
        JTextField searchField = new JTextField();
        searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        searchField.setForeground(Color.WHITE);
        searchPanel.add(searchField, BorderLayout.CENTER);
        
        filterPanel.add(categoryPanel, BorderLayout.NORTH);
        filterPanel.add(searchPanel, BorderLayout.SOUTH);
        
        // List model and list
        DefaultListModel<VarbitUtil.VarEntry> varListModel = new DefaultListModel<>();
        
        // Get entries from VarbitUtil
        Map<Integer, String> constantMap = isVarbit ? VarbitUtil.getAllVarbitEntries() : VarbitUtil.getAllVarPlayerEntries();
        for (Map.Entry<Integer, String> entry : constantMap.entrySet()) {
            varListModel.addElement(new VarbitUtil.VarEntry(entry.getKey(), VarbitUtil.formatConstantName(entry.getValue())));
        }
        
        JList<VarbitUtil.VarEntry> varList = new JList<>(varListModel);
        varList.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        varList.setForeground(Color.WHITE);
        varList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                        boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof VarbitUtil.VarEntry) {
                    VarbitUtil.VarEntry entry = (VarbitUtil.VarEntry) value;
                    setText(entry.name + " (" + entry.id + ")");
                }
                return this;
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(varList);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        // Filter the list when search text changes or category changes
        DocumentListener searchListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filterList(); }
            
            @Override
            public void removeUpdate(DocumentEvent e) { filterList(); }
            
            @Override
            public void changedUpdate(DocumentEvent e) { filterList(); }
            
            private void filterList() {
                String searchText = searchField.getText().toLowerCase();
                String category = (String) categoryComboBox.getSelectedItem();
                DefaultListModel<VarbitUtil.VarEntry> filteredModel = new DefaultListModel<>();
                
                Map<Integer, String> constantMap = isVarbit ? VarbitUtil.getAllVarbitEntries() : VarbitUtil.getAllVarPlayerEntries();
                List<VarbitUtil.VarEntry> filteredEntries = new ArrayList<>();
                
                if ("Collection Log".equals(category)) {
                    // For collection log category
                    for (Map.Entry<Integer, String> entry : constantMap.entrySet()) {
                        if (entry.getValue().contains("COLLECTION")) {
                            String formatted = VarbitUtil.formatConstantName(entry.getValue());
                            if (formatted.toLowerCase().contains(searchText)) {
                                filteredEntries.add(new VarbitUtil.VarEntry(entry.getKey(), formatted));
                            }
                        }
                    }
                } else if (!"All Categories".equals(category)) {
                    // For other specific categories, use the VarbitUtil's categorization
                    List<VarbitUtil.VarEntry> categoryEntries = VarbitUtil.getVarbitEntriesByCategory(category);
                    for (VarbitUtil.VarEntry entry : categoryEntries) {
                        if (entry.name.toLowerCase().contains(searchText)) {
                            filteredEntries.add(entry);
                        }
                    }
                } else {
                    // For "All Categories", search all entries
                    for (Map.Entry<Integer, String> entry : constantMap.entrySet()) {
                        String formatted = VarbitUtil.formatConstantName(entry.getValue());
                        if (formatted.toLowerCase().contains(searchText) || 
                            entry.getValue().toLowerCase().contains(searchText) ||
                            String.valueOf(entry.getKey()).contains(searchText)) {
                            filteredEntries.add(new VarbitUtil.VarEntry(entry.getKey(), formatted));
                        }
                    }
                }
                
                // Sort entries by name
                filteredEntries.sort(Comparator.comparing(e -> e.name));
                
                for (VarbitUtil.VarEntry entry : filteredEntries) {
                    filteredModel.addElement(entry);
                }
                
                varList.setModel(filteredModel);
            }
        };
        
        searchField.getDocument().addDocumentListener(searchListener);
        categoryComboBox.addActionListener(e -> searchListener.changedUpdate(null));
        
        // Buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JButton selectButton = new JButton("Select");
        selectButton.setBackground(ColorScheme.BRAND_ORANGE);
        selectButton.setForeground(Color.WHITE);
        selectButton.addActionListener(e -> {
            VarbitUtil.VarEntry selected = varList.getSelectedValue();
            if (selected != null) {
                callback.onVarSelected(selected.id);
                dialog.dispose();
            }
        });
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(ColorScheme.LIGHT_GRAY_COLOR);
        cancelButton.setForeground(Color.BLACK);
        cancelButton.addActionListener(e -> dialog.dispose());
        
        buttonsPanel.add(cancelButton);
        buttonsPanel.add(selectButton);
        
        // Double-click to select
        varList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    VarbitUtil.VarEntry selected = varList.getSelectedValue();
                    if (selected != null) {
                        callback.onVarSelected(selected.id);
                        dialog.dispose();
                    }
                }
            }
        });
        
        // Add everything to dialog
        contentPanel.add(filterPanel, BorderLayout.NORTH);
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        contentPanel.add(buttonsPanel, BorderLayout.SOUTH);
        
        dialog.add(contentPanel);
        dialog.setVisible(true);
    }
    
   

    /**
     * Custom DocumentListener that can be toggled between auto-updating and manual modes
     */
    private static class EditableDocumentListener implements DocumentListener {
        private boolean userEditing = false;
        private final Runnable updateAction;
        
        public EditableDocumentListener(Runnable updateAction) {
            this.updateAction = updateAction;
        }
        
        public void setUserEditing(boolean editing) {
            this.userEditing = editing;
        }
        
        @Override
        public void insertUpdate(DocumentEvent e) {
            if (!userEditing) {
                // Use invokeLater to avoid mutating during notification
                SwingUtilities.invokeLater(updateAction);
            }
        }
        
        @Override
        public void removeUpdate(DocumentEvent e) {
            if (!userEditing) {
                // Use invokeLater to avoid mutating during notification
                SwingUtilities.invokeLater(updateAction);
            }
        }
        
        @Override
        public void changedUpdate(DocumentEvent e) {
            if (!userEditing) {
                // Use invokeLater to avoid mutating during notification
                SwingUtilities.invokeLater(updateAction);
            }
        }
    }
    
    /**
     * Sets up the varbit condition panel with values from an existing condition
     *
     * @param panel The panel containing the UI components
     * @param condition The varbit condition to read values from
     */
    public static void setupVarbitCondition(JPanel panel, VarbitCondition condition) {
        if (condition == null) return;

        // Get UI components
        JComboBox<String> varTypeComboBox = (JComboBox<String>) panel.getClientProperty("varbitTypeComboBox");
        JTextField idField = (JTextField) panel.getClientProperty("varbitIdField");
        JComboBox<String> operatorComboBox = (JComboBox<String>) panel.getClientProperty("varbitOperatorComboBox");
        JSpinner targetValueSpinner = (JSpinner) panel.getClientProperty("varbitTargetValueSpinner");
        
        JRadioButton relativeMode = (JRadioButton) panel.getClientProperty("varbitRelativeMode");
        JRadioButton absoluteMode = (JRadioButton) panel.getClientProperty("varbitAbsoluteMode");
        JLabel currentValueDisplay = (JLabel) panel.getClientProperty("varbitCurrentValueDisplay");
        JCheckBox randomizeCheckBox = (JCheckBox) panel.getClientProperty("varbitRandomize");
        JSpinner minValueSpinner = (JSpinner) panel.getClientProperty("varbitMinValueSpinner");
        JSpinner maxValueSpinner = (JSpinner) panel.getClientProperty("varbitMaxValueSpinner");
        JPanel minMaxPanel = (JPanel) panel.getClientProperty("varbitMinMaxPanel");
        JComboBox<String> categoryEntriesComboBox = (JComboBox<String>) panel.getClientProperty("varbitCategoryEntriesComboBox");
        
        if (varTypeComboBox == null || idField == null || operatorComboBox == null || targetValueSpinner == null || 
            relativeMode == null || absoluteMode == null) {
            return;
        }
        
        int varId = condition.getVarId();
        idField.setText(String.valueOf(varId));
        
        // Set var type
        varTypeComboBox.setSelectedItem(condition.getVarType() == VarbitCondition.VarType.VARBIT ? "Varbit" : "VarPlayer");
        
        // Set operator
        operatorComboBox.setSelectedItem(condition.getOperator().getDisplayName());
        
        // Set mode
        relativeMode.setSelected(condition.isRelative());
        absoluteMode.setSelected(!condition.isRelative());
        
        // Set randomization
        randomizeCheckBox.setSelected(condition.isRandomized());
        if (condition.isRandomized()) {
            minValueSpinner.setValue(condition.getTargetValueMin());
            maxValueSpinner.setValue(condition.getTargetValueMax());
            minMaxPanel.setVisible(true);
            targetValueSpinner.setEnabled(false);
        } else {
            targetValueSpinner.setValue(condition.getTargetValue());
            minMaxPanel.setVisible(false);
        }
        
        // Set current value
        currentValueDisplay.setText(String.valueOf(condition.getCurrentValue()));
        
        // If we have a category-specific panel, try to select the matching entry
        String specificCategory = (String) panel.getClientProperty("varbitSpecificCategory");
        if (specificCategory != null && categoryEntriesComboBox != null) {
            // Get the varbit name if available
            String varbitName = VarbitUtil.getConstantNameForId(
                condition.getVarType() == VarbitCondition.VarType.VARBIT, varId);
                
            // Try to find and select the matching entry in the dropdown
            if (varbitName != null) {
                for (int i = 0; i < categoryEntriesComboBox.getItemCount(); i++) {
                    String item = categoryEntriesComboBox.getItemAt(i);
                    if (item.contains("(" + varId + ")")) {
                        categoryEntriesComboBox.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * Creates a VarbitCondition from the panel configuration
     *
     * @param panel The panel containing the configuration
     * @return A new VarbitCondition
     */
    public static VarbitCondition createVarbitCondition(JPanel panel) {
        // Get all required UI components using getClientProperty
        JComboBox<?> varTypeComboBox = (JComboBox<?>) panel.getClientProperty("varbitTypeComboBox");
        JTextField idField = (JTextField) panel.getClientProperty("varbitIdField");
        JComboBox<?> operatorComboBox = (JComboBox<?>) panel.getClientProperty("varbitOperatorComboBox");
        JSpinner targetValueSpinner = (JSpinner) panel.getClientProperty("varbitTargetValueSpinner");
        JRadioButton relativeMode = (JRadioButton) panel.getClientProperty("varbitRelativeMode");
        JCheckBox randomizeCheckBox = (JCheckBox) panel.getClientProperty("varbitRandomize");
        JSpinner minValueSpinner = (JSpinner) panel.getClientProperty("varbitMinValueSpinner");
        JSpinner maxValueSpinner = (JSpinner) panel.getClientProperty("varbitMaxValueSpinner");
        
        // Get the category entries dropdown (exists in both general and specific panels)
        JComboBox<?> categoryEntriesComboBox = (JComboBox<?>) panel.getClientProperty("varbitCategoryEntriesComboBox");
        
        // Check if we have the core required components
        if (varTypeComboBox == null || idField == null || operatorComboBox == null || 
            targetValueSpinner == null || relativeMode == null || randomizeCheckBox == null) {
            throw new IllegalStateException("Missing required UI components for Varbit condition");
        }
        
        // Check for category-specific panels
        String specificCategory = (String) panel.getClientProperty("varbitSpecificCategory");
        if (specificCategory != null && categoryEntriesComboBox != null) {
            // For category-specific panels, ensure we have a selection in the dropdown
            if (categoryEntriesComboBox.getSelectedIndex() <= 0) {
                throw new IllegalArgumentException("Please select a " + 
                    (specificCategory.equals("Bosses") ? "boss" : "minigame"));
            }
        }

        // Get values from UI components
        String name = varTypeComboBox.getSelectedItem().toString();
        VarbitCondition.VarType varType = varTypeComboBox.getSelectedItem().equals("Varbit") 
            ? VarbitCondition.VarType.VARBIT 
            : VarbitCondition.VarType.VARPLAYER;
            
        // Parse var ID
        int varId;
        try {
            varId = Integer.parseInt(idField.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid variable ID: " + idField.getText());
        }

        // Get comparison operator - get enum value by ordinal
        int operatorIndex = operatorComboBox.getSelectedIndex();
        VarbitCondition.ComparisonOperator operator = VarbitCondition.ComparisonOperator.values()[operatorIndex];

        // Get target value(s)
        int targetValue = (Integer) targetValueSpinner.getValue();
        boolean isRelative = relativeMode.isSelected();
        
        // Create condition based on configuration
        if (randomizeCheckBox.isSelected()) {
            int minValue = (Integer) minValueSpinner.getValue();
            int maxValue = (Integer) maxValueSpinner.getValue();
            
            if (isRelative) {
                return VarbitCondition.createRelativeRandomized(name, varType, varId, minValue, maxValue, operator);
            } else {
                return VarbitCondition.createRandomized(name, varType, varId, minValue, maxValue, operator);
            }
        } else {
            if (isRelative) {
                return VarbitCondition.createRelative(name, varType, varId, targetValue, operator);
            } else {
                return new VarbitCondition(name, varType, varId, targetValue, operator);
            }
        }
    }
    
}