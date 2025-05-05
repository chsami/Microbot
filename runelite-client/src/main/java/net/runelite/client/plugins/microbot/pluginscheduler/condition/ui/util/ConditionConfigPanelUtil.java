package net.runelite.client.plugins.microbot.pluginscheduler.condition.ui.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

import net.runelite.client.plugins.microbot.pluginscheduler.condition.ui.renderer.ConditionTreeCellRenderer;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionManager;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionManager;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.NotCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Utility class for common methods used in the ConditionConfigPanel
 */
@Slf4j
public class ConditionConfigPanelUtil {
    
    /**
     * Creates a styled button with consistent appearance
     * 
     * @param text The button text
     * @param color The background color
     * @return A styled JButton
     */
    public static JButton createButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setMargin(new Insets(5, 10, 5, 10));
        button.setBorder(new EmptyBorder(5, 10, 5, 10));
        return button;
    }
    
    /**
     * Creates a button with neutral appearance for common actions
     * 
     * @param text The button text
     * @return A styled JButton with neutral appearance
     */
    public static JButton createNeutralButton(String text) {
        return createButton(text, new Color(60, 60, 60));
    }
    
    /**
     * Creates a button with positive/success appearance
     * 
     * @param text The button text
     * @return A styled JButton with positive appearance
     */
    public static JButton createPositiveButton(String text) {
        return createButton(text, new Color(0, 160, 0));
    }
    
    /**
     * Creates a button with negative/danger appearance
     * 
     * @param text The button text
     * @return A styled JButton with negative appearance
     */
    public static JButton createNegativeButton(String text) {
        return createButton(text, new Color(180, 60, 60));
    }
    
    /**
     * Creates a standard titled section panel for consistent visual style
     * 
     * @param title The title for the panel
     * @return A JPanel with titled border
     */
    public static JPanel createTitledPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                title,
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                FontManager.getRunescapeBoldFont(),
                Color.WHITE
        ));
        return panel;
    }
    
    /**
     * Creates a scrollable panel with consistent styling
     * 
     * @param component The component to make scrollable
     * @return A JScrollPane containing the component
     */
    public static JScrollPane createScrollPane(Component component) {
        JScrollPane scrollPane = new JScrollPane(component);
        scrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16); // For smoother scrolling
        return scrollPane;
    }
    
    /**
     * Styles a JComboBox for consistent appearance
     * 
     * @param comboBox The JComboBox to style
     */
    public static void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        comboBox.setForeground(Color.WHITE);
        comboBox.setFocusable(false);
        comboBox.setFont(FontManager.getRunescapeSmallFont());
    }
    
    /**
     * Creates a styled label with consistent appearance
     * 
     * @param text The label text
     * @return A styled JLabel
     */
    public static JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeSmallFont());
        return label;
    }
    
    /**
     * Creates a styled header label with larger, bold font
     * 
     * @param text The header text
     * @return A styled header JLabel
     */
    public static JLabel createHeaderLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeBoldFont());
        return label;
    }
    
    /**
     * Adds a labeled separator to a panel at the given grid position
     * 
     * @param panel The panel to add the separator to
     * @param gbc The GridBagConstraints to use
     * @param text The text for the separator (can be null for plain separator)
     */
    public static void addSeparator(JPanel panel, GridBagConstraints gbc, String text) {
        int originalGridy = gbc.gridy;
        
        if (text != null && !text.isEmpty()) {
            JLabel label = createHeaderLabel(text);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(label, gbc);
            gbc.gridy++;
        }
        
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        separator.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(text != null ? 2 : 10, 5, 10, 5);
        panel.add(separator, gbc);
        
        // Restore original insets
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridy++;
    }
    
    /**
     * Sets consistent padding for a component
     * 
     * @param component The component to pad
     * @param padding The padding size in pixels
     */
    public static void setPadding(JComponent component, int padding) {
        component.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
    }
    
    /**
     * Sets consistent maximum width for a component while keeping its preferred height
     * 
     * @param component The component to constrain
     * @param maxWidth The maximum width in pixels
     */
    public static void setMaxWidth(JComponent component, int maxWidth) {
        Dimension prefSize = component.getPreferredSize();
        component.setMaximumSize(new Dimension(maxWidth, prefSize.height));
    }
    
    /**
     * Loads and scales an icon from resources
     * 
     * @param name The resource name/path
     * @param width The desired width
     * @param height The desired height
     * @return An Icon, or the default leaf icon if loading fails
     */
    public static Icon getResourceIcon(String name, int width, int height) {
        try {
            URL resourceUrl = ConditionConfigPanelUtil.class.getResource(
                    "/net/runelite/client/plugins/microbot/pluginscheduler/" + name);
            
            if (resourceUrl == null) {
                log.warn("Resource not found: /net/runelite/client/plugins/microbot/pluginscheduler/" + name);
                return UIManager.getIcon("Tree.leafIcon");
            }
            
            BufferedImage originalImage = ImageIO.read(resourceUrl);
            
            if (originalImage == null) {
                log.warn("Could not load resource: " + name);
                return UIManager.getIcon("Tree.leafIcon");
            }
            
            if (originalImage.getWidth() == width && originalImage.getHeight() == height) {
                return new ImageIcon(originalImage);
            }
            
            // Scale the image to desired size
            BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = scaledImage.createGraphics();
            
            // Use high quality scaling
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            g2d.drawImage(originalImage, 0, 0, width, height, null);
            g2d.dispose();
            
            return new ImageIcon(scaledImage);
        } catch (IOException e) {
            log.warn("Failed to load icon: " + name, e);
            // Fallback to default icon
            return UIManager.getIcon("Tree.leafIcon");
        }
    }
    
    /**
     * Convenience method for default 24x24 icons
     * 
     * @param name The resource name
     * @return An Icon sized 24x24
     */
    public static Icon getResourceIcon(String name) {
        return getResourceIcon(name, 24, 24);
    }
    
    /**
     * Shows a confirmation dialog with consistent styling
     * 
     * @param parentComponent The parent component for the dialog
     * @param message The message to display
     * @param title The dialog title
     * @return true if user confirms, false otherwise
     */
    public static boolean showConfirmDialog(Component parentComponent, String message, String title) {
        // Create custom button text
        Object[] options = {"Yes", "No"};
        
        // Create styled option pane
        JOptionPane optionPane = new JOptionPane(
                message,
                JOptionPane.QUESTION_MESSAGE,
                JOptionPane.YES_NO_OPTION,
                null, // No custom icon
                options,
                options[1] // Default to "No" for safety
        );
        
        // Create and configure dialog
        JDialog dialog = optionPane.createDialog(parentComponent, title);
        dialog.setVisible(true);
        
        // Get the result (returns the selected value or null if closed)
        Object selectedValue = optionPane.getValue();
        
        // Check if user selected "Yes"
        return selectedValue != null && selectedValue.equals(options[0]);
    }
    
    /**
     * Shows an error dialog with consistent styling
     * 
     * @param parentComponent The parent component for the dialog
     * @param message The error message to display
     * @param title The dialog title
     */
    public static void showErrorDialog(Component parentComponent, String message, String title) {
        JOptionPane.showMessageDialog(
                parentComponent,
                message,
                title,
                JOptionPane.ERROR_MESSAGE
        );
    }
    
    /**
     * Shows a warning dialog with consistent styling
     * 
     * @param parentComponent The parent component for the dialog
     * @param message The warning message to display
     * @param title The dialog title
     */
    public static void showWarningDialog(Component parentComponent, String message, String title) {
        JOptionPane.showMessageDialog(
                parentComponent,
                message,
                title,
                JOptionPane.WARNING_MESSAGE
        );
    }
    
    /**
     * Shows an information dialog with consistent styling
     * 
     * @param parentComponent The parent component for the dialog
     * @param message The information message to display
     * @param title The dialog title
     */
    public static void showInfoDialog(Component parentComponent, String message, String title) {
        JOptionPane.showMessageDialog(
                parentComponent,
                message,
                title,
                JOptionPane.INFORMATION_MESSAGE
        );
    }
    
    /**
     * Creates a logical operations toolbar with buttons for manipulating logical conditions
     * 
     * @param createAndAction The action to perform when creating an AND group
     * @param createOrAction The action to perform when creating an OR group
     * @param negateAction The action to perform when negating a condition
     * @param convertToAndAction The action to perform when converting to AND
     * @param convertToOrAction The action to perform when converting to OR
     * @param ungroupAction The action to perform when ungrouping a logical condition
     * @return A panel containing the logical operations buttons
     */
    public static JPanel createLogicalOperationsToolbar(
            Runnable createAndAction,
            Runnable createOrAction,
            Runnable negateAction,
            Runnable convertToAndAction,
            Runnable convertToOrAction,
            Runnable ungroupAction) {
        
        JPanel logicalOpPanel = new JPanel();
        logicalOpPanel.setLayout(new BoxLayout(logicalOpPanel, BoxLayout.X_AXIS));
        logicalOpPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        logicalOpPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Group operations section
        JButton createAndButton = createButton("Group as AND", ColorScheme.BRAND_ORANGE);
        createAndButton.setToolTipText("Group selected conditions with AND logic");
        createAndButton.addActionListener(e -> createAndAction.run());
        
        JButton createOrButton = createButton("Group as OR", new Color(25, 130, 196));
        createOrButton.setToolTipText("Group selected conditions with OR logic");
        createOrButton.addActionListener(e -> createOrAction.run());
        
        // Negation button
        JButton negateButton = createButton("Negate", new Color(220, 50, 50));
        negateButton.setToolTipText("Negate the selected condition (toggle NOT)");
        negateButton.addActionListener(e -> negateAction.run());
        
        // Convert operation buttons
        JButton convertToAndButton = createButton("Convert to AND", ColorScheme.BRAND_ORANGE);
        convertToAndButton.setToolTipText("Convert selected logical group to AND type");
        convertToAndButton.addActionListener(e -> convertToAndAction.run());
        
        JButton convertToOrButton = createButton("Convert to OR", new Color(25, 130, 196));
        convertToOrButton.setToolTipText("Convert selected logical group to OR type");
        convertToOrButton.addActionListener(e -> convertToOrAction.run());
        
        // Ungroup button
        JButton ungroupButton = createButton("Ungroup", ColorScheme.LIGHT_GRAY_COLOR);
        ungroupButton.setToolTipText("Remove the logical group but keep its conditions");
        ungroupButton.addActionListener(e -> ungroupAction.run());
        
        // Add buttons to panel with separators
        logicalOpPanel.add(createAndButton);
        logicalOpPanel.add(Box.createHorizontalStrut(5));
        logicalOpPanel.add(createOrButton);
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(new JSeparator(SwingConstants.VERTICAL));
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(negateButton);
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(new JSeparator(SwingConstants.VERTICAL));
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(convertToAndButton);
        logicalOpPanel.add(Box.createHorizontalStrut(5));
        logicalOpPanel.add(convertToOrButton);
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(new JSeparator(SwingConstants.VERTICAL));
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(ungroupButton);
        
        return logicalOpPanel;
    }
    
    /**
     * Creates a logical condition operations panel that handles button state management
     * 
     * @param panel The panel to add the toolbar to
     * @param negateButtonRef Reference to store the negate button
     * @param convertToAndButtonRef Reference to store the convert to AND button
     * @param convertToOrButtonRef Reference to store the convert to OR button
     * @param ungroupButtonRef Reference to store the ungroup button
     * @param createAndAction The action to perform when creating an AND group
     * @param createOrAction The action to perform when creating an OR group
     * @param negateAction The action to perform when negating a condition
     * @param convertToAndAction The action to perform when converting to AND
     * @param convertToOrAction The action to perform when converting to OR
     * @param ungroupAction The action to perform when ungrouping a logical condition
     */
    public static void addLogicalOperationsToolbar(
            JPanel panel,
            JButton[] negateButtonRef,
            JButton[] convertToAndButtonRef,
            JButton[] convertToOrButtonRef,
            JButton[] ungroupButtonRef,
            Runnable createAndAction,
            Runnable createOrAction,
            Runnable negateAction,
            Runnable convertToAndAction,
            Runnable convertToOrAction,
            Runnable ungroupAction) {
        
        JPanel logicalOpPanel = new JPanel();
        logicalOpPanel.setLayout(new BoxLayout(logicalOpPanel, BoxLayout.X_AXIS));
        logicalOpPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        logicalOpPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Group operations section
        JButton createAndButton = createButton("Group as AND", ColorScheme.BRAND_ORANGE);
        createAndButton.setToolTipText("Group selected conditions with AND logic");
        createAndButton.addActionListener(e -> createAndAction.run());
        
        JButton createOrButton = createButton("Group as OR", new Color(25, 130, 196));
        createOrButton.setToolTipText("Group selected conditions with OR logic");
        createOrButton.addActionListener(e -> createOrAction.run());
        
        // Negation button
        JButton negateButton = createButton("Negate", new Color(220, 50, 50));
        negateButton.setToolTipText("Negate the selected condition (toggle NOT)");
        negateButton.addActionListener(e -> negateAction.run());
        if (negateButtonRef != null && negateButtonRef.length > 0) {
            negateButtonRef[0] = negateButton;
        }
        
        // Convert operation buttons
        JButton convertToAndButton = createButton("Convert to AND", ColorScheme.BRAND_ORANGE);
        convertToAndButton.setToolTipText("Convert selected logical group to AND type");
        convertToAndButton.addActionListener(e -> convertToAndAction.run());
        if (convertToAndButtonRef != null && convertToAndButtonRef.length > 0) {
            convertToAndButtonRef[0] = convertToAndButton;
        }
        
        JButton convertToOrButton = createButton("Convert to OR", new Color(25, 130, 196));
        convertToOrButton.setToolTipText("Convert selected logical group to OR type");
        convertToOrButton.addActionListener(e -> convertToOrAction.run());
        if (convertToOrButtonRef != null && convertToOrButtonRef.length > 0) {
            convertToOrButtonRef[0] = convertToOrButton;
        }
        
        // Ungroup button
        JButton ungroupButton = createButton("Ungroup", ColorScheme.LIGHT_GRAY_COLOR);
        ungroupButton.setToolTipText("Remove the logical group but keep its conditions");
        ungroupButton.addActionListener(e -> ungroupAction.run());
        if (ungroupButtonRef != null && ungroupButtonRef.length > 0) {
            ungroupButtonRef[0] = ungroupButton;
        }
        
        // Add buttons to panel with separators
        logicalOpPanel.add(createAndButton);
        logicalOpPanel.add(Box.createHorizontalStrut(5));
        logicalOpPanel.add(createOrButton);
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(new JSeparator(SwingConstants.VERTICAL));
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(negateButton);
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(new JSeparator(SwingConstants.VERTICAL));
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(convertToAndButton);
        logicalOpPanel.add(Box.createHorizontalStrut(5));
        logicalOpPanel.add(convertToOrButton);
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(new JSeparator(SwingConstants.VERTICAL));
        logicalOpPanel.add(Box.createHorizontalStrut(10));
        logicalOpPanel.add(ungroupButton);
        
        // Initialize button states
        negateButton.setEnabled(false);
        convertToAndButton.setEnabled(false);
        convertToOrButton.setEnabled(false);
        ungroupButton.setEnabled(false);
        
        panel.add(logicalOpPanel, "North");
    }
    
    /**
     * Creates a condition manipulation panel with add, edit, and remove buttons
     * 
     * @param addAction The action to perform when adding a condition
     * @param editAction The action to perform when editing a condition
     * @param removeAction The action to perform when removing a condition
     * @return A panel containing the condition manipulation buttons
     */
    public static JPanel createConditionManipulationPanel(
            Runnable addAction,
            Runnable editAction,
            Runnable removeAction) {
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttonPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        JButton addButton = createButton("Add", ColorScheme.PROGRESS_COMPLETE_COLOR);
        addButton.addActionListener(e -> addAction.run());
        buttonPanel.add(addButton);
        
        JButton editButton = createButton("Edit", ColorScheme.BRAND_ORANGE);
        editButton.addActionListener(e -> editAction.run());
        buttonPanel.add(editButton);
        
        JButton removeButton = createButton("Remove", ColorScheme.PROGRESS_ERROR_COLOR);
        removeButton.addActionListener(e -> removeAction.run());
        buttonPanel.add(removeButton);
        
        return buttonPanel;
    }
    
    /**
     * Safely negates a condition within its parent logical condition
     * 
     * @param condition The condition to negate
     * @param conditionManager The condition manager
     * @param parentComponent The parent component for error dialogs
     * @return true if negation was successful, false otherwise
     */
    public static boolean negateCondition(Condition condition, ConditionManager conditionManager, Component parentComponent) {
        // Don't allow negating null conditions
        if (condition == null) {
            return false;
        }
        
        // Check if this is a plugin-defined condition that shouldn't be modified
        if (conditionManager != null && conditionManager.isPluginDefinedCondition(condition)) {
            showWarningDialog(
                    parentComponent,
                    "Cannot negate plugin-defined conditions. These conditions are protected.",
                    "Protected Conditions"
            );
            return false;
        }
        
        // Find parent logical condition
        LogicalCondition parentLogical = conditionManager.findContainingLogical(condition);
        if (parentLogical == null) {
            showWarningDialog(
                    parentComponent,
                    "Could not determine which logical group contains this condition",
                    "Operation Failed"
            );
            return false;
        }
        
        // Toggle NOT status
        int index = parentLogical.getConditions().indexOf(condition);
        if (index < 0) {
            return false;
        }
        
        // If condition is already a NOT, unwrap it
        if (condition instanceof NotCondition) {
            NotCondition notCondition = (NotCondition) condition;
            Condition innerCondition = notCondition.getCondition();
            
            // Replace NOT with its inner condition
            parentLogical.getConditions().remove(index);
            parentLogical.addConditionAt(index, innerCondition);
        } 
        // Otherwise, wrap it in a NOT
        else {
            // Create NOT condition
            NotCondition notCondition = new NotCondition(condition);
            
            // Replace original with NOT version
            parentLogical.getConditions().remove(index);
            parentLogical.addConditionAt(index, notCondition);
        }
        
        return true;
    }
    
    /**
     * Converts a logical condition from one type to another
     * 
     * @param logical The logical condition to convert
     * @param toAnd true to convert to AND, false to convert to OR
     * @param conditionManager The condition manager
     * @param parentComponent The parent component for error dialogs
     * @return true if conversion was successful, false otherwise
     */
    public static boolean convertLogicalType(
            LogicalCondition logical,
            boolean toAnd,
            ConditionManager conditionManager,
            Component parentComponent) {
        
        // Skip if already the desired type
        if ((toAnd && logical instanceof AndCondition) || 
            (!toAnd && logical instanceof OrCondition)) {
            return false;
        }
        
        // Check if this is a plugin-defined logical group
        if (conditionManager != null && conditionManager.isPluginDefinedCondition(logical)) {
            showWarningDialog(
                    parentComponent,
                    "Cannot modify plugin-defined condition groups. These conditions are protected.",
                    "Protected Conditions"
            );
            return false;
        }
        
        // Create new logical of the desired type
        LogicalCondition newLogical = toAnd ? new AndCondition() : new OrCondition();
        
        // Transfer all conditions to the new logical
        for (Condition condition : logical.getConditions()) {
            newLogical.addCondition(condition);
        }
        
        // Find parent logical
        LogicalCondition parentLogical = conditionManager.findContainingLogical(logical);
        
        if (parentLogical == logical) {
            // This is the root logical - replace in condition manager
            if (toAnd) {
                conditionManager.setUserLogicalCondition((AndCondition) newLogical);
            } else {
                conditionManager.setUserLogicalCondition((OrCondition) newLogical);
            }
        } else if (parentLogical != null) {
            // Replace in parent
            int index = parentLogical.getConditions().indexOf(logical);
            if (index >= 0) {
                parentLogical.getConditions().remove(index);
                parentLogical.addConditionAt(index, newLogical);
            }
        } else {
            // Couldn't find parent
            showWarningDialog(
                    parentComponent,
                    "Couldn't find the parent logical condition for this group.",
                    "Operation Failed"
            );
            return false;
        }
        
        return true;
    }
    
    /**
     * Creates a condition tree panel with full functionality
     * 
     * @param rootNode The root node for the tree
     * @param treeModel The tree model
     * @param conditionTree The tree component
     * @param conditionManager The condition manager for rendering
     * @return A panel containing the condition tree with scrolling
     */
    public static JPanel createConditionTreePanel(
            DefaultMutableTreeNode rootNode,
            DefaultTreeModel treeModel, 
            JTree conditionTree,
            ConditionManager conditionManager,boolean isStopConditionRenderer) {
        
        // Create the panel with border
        JPanel panel = createTitledPanel("Condition Structure");
        panel.setLayout(new BorderLayout());
        
        // Initialize tree if not already done
        if (rootNode == null) {
            rootNode = new DefaultMutableTreeNode("Conditions");
        }
        
        if (treeModel == null) {
            treeModel = new DefaultTreeModel(rootNode);
        }
        
        if (conditionTree == null) {
            conditionTree = new JTree(treeModel);
            conditionTree.setRootVisible(false);
            conditionTree.setShowsRootHandles(true);
            
            // Set up tree cell renderer
            conditionTree.setCellRenderer(new ConditionTreeCellRenderer(conditionManager,isStopConditionRenderer));
            
            // Set up tree selection mode
            conditionTree.getSelectionModel().setSelectionMode(
                TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION); // Enable multi-select
        }
        
        // Create scroll pane for the tree
        JScrollPane treeScrollPane = createScrollPane(conditionTree);
        treeScrollPane.setPreferredSize(new Dimension(400, 300));
        
        // Add to panel
        panel.add(treeScrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Creates a title panel with plugin name display
     * 
     * @param isRunning Whether the plugin is running
     * @param isEnabled Whether the plugin is enabled
     * @param pluginName The name of the plugin
     * @return A panel with the title display
     */
    public static JPanel createTitlePanel(boolean isRunning, boolean isEnabled, String pluginName) {
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        titlePanel.setName("titlePanel");
        
        // Create and format the title label
        JLabel titleLabel = new JLabel(formatPluginTitle(isRunning, isEnabled, pluginName));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        
        titlePanel.add(titleLabel);
        
        return titlePanel;
    }
    
    /**
     * Formats the plugin title with appropriate HTML styling
     * 
     * @param isRunning Whether the plugin is running
     * @param isEnabled Whether the plugin is enabled
     * @param pluginName The name of the plugin
     * @return Formatted HTML title string
     */
    public static String formatPluginTitle(boolean isRunning, boolean isEnabled, String pluginName) {
        if (pluginName == null || pluginName.isEmpty()) {
            return "<html><i>No plugin selected</i></html>";
        }
        
        // Apply color based on plugin state
        String colorHex;
        if (isEnabled) {
            if (isRunning) {
                // Running plugin - bright green
                colorHex = "#4CAF50";
            } else {
                // Enabled but not running - blue
                colorHex = "#2196F3";
            }
        } else {
            // Disabled plugin - orange/amber
            colorHex = "#FFC107";
        }
        
        // Format with HTML for color and bold styling
        return "<html><b><span style='color: " + colorHex + ";'>" + 
                pluginName + "</span></b></html>";
    }
    
    /**
     * Creates a top control panel with title and buttons
     * 
     * @param titlePanel The title panel to include
     * @param saveAction The action to perform when saving
     * @param loadAction The action to perform when loading
     * @param resetAction The action to perform when resetting
     * @return A panel with title and control buttons
     */
    public static JPanel createTopControlPanel(
            JPanel titlePanel,
            Runnable saveAction,
            Runnable loadAction,
            Runnable resetAction) {
        
        // Create button panel with right alignment
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Create control buttons
        JButton loadButton = createButton("Load Current Conditions", ColorScheme.PROGRESS_COMPLETE_COLOR);
        loadButton.addActionListener(e -> loadAction.run());
        
        JButton saveButton = createButton("Save Conditions", ColorScheme.PROGRESS_COMPLETE_COLOR);
        saveButton.addActionListener(e -> saveAction.run());
        
        JButton resetButton = createButton("Reset Conditions", ColorScheme.PROGRESS_ERROR_COLOR);
        resetButton.addActionListener(e -> resetAction.run());
        
        // Add buttons to panel
        buttonPanel.add(loadButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(resetButton);
        
        // Create main top panel
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        topPanel.add(titlePanel, BorderLayout.WEST);
        topPanel.add(buttonPanel, BorderLayout.EAST);
        
        return topPanel;
    }
}
