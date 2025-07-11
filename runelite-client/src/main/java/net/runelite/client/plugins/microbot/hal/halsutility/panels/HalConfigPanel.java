package net.runelite.client.plugins.microbot.hal.halsutility.panels;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigSectionDescriptor;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalSubPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.*;
import java.util.List;

@Slf4j
public class HalConfigPanel extends PluginPanel {
    private static final int SPINNER_FIELD_WIDTH = 6;
    private static final ImageIcon SECTION_EXPAND_ICON;
    private static final ImageIcon SECTION_RETRACT_ICON;
    static final ImageIcon CONFIG_ICON;
    static final ImageIcon BACK_ICON;

    private static final Map<String, Boolean> sectionExpandStates = new HashMap<>();

    static {
        final BufferedImage backIcon = ImageUtil.loadImageResource(HalConfigPanel.class, "config_back_icon.png");
        BACK_ICON = new ImageIcon(backIcon);

        BufferedImage sectionRetractIcon = ImageUtil.loadImageResource(HalConfigPanel.class, "arrow_right.png");
        sectionRetractIcon = ImageUtil.luminanceOffset(sectionRetractIcon, -121);
        SECTION_EXPAND_ICON = new ImageIcon(sectionRetractIcon);
        final BufferedImage sectionExpandIcon = ImageUtil.rotateImage(sectionRetractIcon, Math.PI / 2);
        SECTION_RETRACT_ICON = new ImageIcon(sectionExpandIcon);
        BufferedImage configIcon = ImageUtil.loadImageResource(HalConfigPanel.class, "config_edit_icon.png");
        CONFIG_ICON = new ImageIcon(configIcon);
    }

    private final ConfigManager configManager;
    private final HalSubPlugin selectedModule;
    private final HalFixedWidthPanel mainPanel;
    private final JLabel title;
    private final HalPluginToggleButton moduleToggle;

    public HalConfigPanel(ConfigManager configManager, HalSubPlugin selectedModule) {
        super(false);
        this.configManager = configManager;
        this.selectedModule = selectedModule;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel topPanel = new JPanel();
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        topPanel.setLayout(new BorderLayout(0, BORDER_OFFSET));
        add(topPanel, BorderLayout.NORTH);

        mainPanel = new HalFixedWidthPanel();
        mainPanel.setBorder(new EmptyBorder(8, 10, 10, 10));
        mainPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel northPanel = new HalFixedWidthPanel();
        northPanel.setLayout(new BorderLayout());
        northPanel.add(mainPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(northPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        JButton topPanelBackButton = new JButton(BACK_ICON);
        SwingUtil.removeButtonDecorations(topPanelBackButton);
        topPanelBackButton.setPreferredSize(new Dimension(22, 0));
        topPanelBackButton.setBorder(new EmptyBorder(0, 0, 0, 5));
        topPanelBackButton.addActionListener(e -> {
            // Find the parent HalPanel and call showMainPanel
            Container parent = getParent();
            while (parent != null && !(parent instanceof HalPanel)) {
                parent = parent.getParent();
            }
            if (parent instanceof HalPanel) {
                ((HalPanel) parent).showMainPanel();
            }
        });
        topPanelBackButton.setToolTipText("Back");
        topPanel.add(topPanelBackButton, BorderLayout.WEST);

        // Add module toggle button
        moduleToggle = new HalPluginToggleButton();
        moduleToggle.setSelected(selectedModule.isRunning());
        moduleToggle.addActionListener(e -> {
            if (moduleToggle.isSelected()) {
                selectedModule.start();
            } else {
                selectedModule.stop();
            }
        });
        topPanel.add(moduleToggle, BorderLayout.EAST);

        title = new JLabel(selectedModule.getDisplayName() + " Configuration");
        title.setForeground(Color.WHITE);
        topPanel.add(title, BorderLayout.CENTER);

        rebuild();
    }

    private void toggleSection(String sectionKey, JButton button, JPanel contents) {
        boolean newState = !contents.isVisible();
        contents.setVisible(newState);
        button.setIcon(newState ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
        button.setToolTipText(newState ? "Retract" : "Expand");
        sectionExpandStates.put(sectionKey, newState);
        SwingUtilities.invokeLater(contents::revalidate);
    }

    private void rebuild() {
        mainPanel.removeAll();

        Config config = selectedModule.getConfig();
        if (config == null) {
            mainPanel.add(new JLabel("No configuration available for this module."));
            return;
        }

        // Get the ConfigDescriptor using RuneLite's system
        ConfigDescriptor cd = configManager.getConfigDescriptor(config);
        if (cd == null) {
            mainPanel.add(new JLabel("Unable to load configuration for this module."));
            return;
        }

        // Show ConfigInformation if present
        if (cd.getInformation() != null) {
            buildInformationPanel(cd.getInformation());
        }

        // Group config items by sections
        final Map<String, JPanel> sectionWidgets = new HashMap<>();
        final Map<ConfigItemDescriptor, JPanel> topLevelPanels = new TreeMap<>((a, b) -> {
            int posCompare = Integer.compare(a.getItem().position(), b.getItem().position());
            if (posCompare != 0) return posCompare;
            return a.getItem().name().compareTo(b.getItem().name());
        });

        // Create sections
        for (ConfigSectionDescriptor csd : cd.getSections()) {
            net.runelite.client.config.ConfigSection cs = csd.getSection();
            final boolean isOpen = sectionExpandStates.getOrDefault(csd.getKey(), !cs.closedByDefault());

            final JPanel section = new JPanel();
            section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
            section.setMinimumSize(new Dimension(PANEL_WIDTH, 0));

            final JPanel sectionHeader = new JPanel();
            sectionHeader.setLayout(new BorderLayout());
            sectionHeader.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
            sectionHeader.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                new EmptyBorder(0, 0, 3, 1)));
            section.add(sectionHeader, BorderLayout.NORTH);

            final JButton sectionToggle = new JButton(isOpen ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
            sectionToggle.setPreferredSize(new Dimension(18, 0));
            sectionToggle.setBorder(new EmptyBorder(0, 0, 0, 5));
            sectionToggle.setToolTipText(isOpen ? "Retract" : "Expand");
            SwingUtil.removeButtonDecorations(sectionToggle);
            sectionHeader.add(sectionToggle, BorderLayout.WEST);

            final JLabel sectionNameLabel = new JLabel(cs.name());
            sectionNameLabel.setForeground(ColorScheme.BRAND_ORANGE);
            sectionNameLabel.setFont(FontManager.getRunescapeBoldFont());
            sectionHeader.add(sectionNameLabel, BorderLayout.CENTER);

            final JPanel sectionContents = new JPanel();
            sectionContents.setLayout(new DynamicGridLayout(0, 1, 0, 5));
            sectionContents.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
            sectionContents.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                new EmptyBorder(BORDER_OFFSET, 0, BORDER_OFFSET, 0)));
            sectionContents.setVisible(isOpen);
            section.add(sectionContents, BorderLayout.SOUTH);

            // Add listeners to each part of the header so that it's easier to toggle them
            final MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggleSection(csd.getKey(), sectionToggle, sectionContents);
                }
            };
            sectionToggle.addActionListener(actionEvent -> toggleSection(csd.getKey(), sectionToggle, sectionContents));
            sectionNameLabel.addMouseListener(adapter);
            sectionHeader.addMouseListener(adapter);

            sectionWidgets.put(cs.name(), sectionContents);
            mainPanel.add(section);
        }

        // Add config items
        for (ConfigItemDescriptor cid : cd.getItems()) {
            if (cid.getItem().hidden()) {
                continue;
            }

            JPanel item = createConfigItemPanel(cd, cid);
            
            JPanel section = sectionWidgets.get(cid.getItem().section());
            if (section == null) {
                topLevelPanels.put(cid, item);
            } else {
                section.add(item);
            }
        }

        // Add top-level items (items without sections)
        topLevelPanels.values().forEach(mainPanel::add);

        // Add reset button
        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener((e) -> {
            final int result = JOptionPane.showOptionDialog(resetButton, 
                "Are you sure you want to reset this module's configuration?",
                "Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new String[]{"Yes", "No"}, "No");

            if (result == JOptionPane.YES_OPTION) {
                configManager.setDefaultConfiguration(selectedModule.getConfig(), true);
                rebuild();
            }
        });
        mainPanel.add(resetButton);

        revalidate();
    }

    private void buildInformationPanel(ConfigInformation ci) {
        // Create the main panel (similar to a Bootstrap panel)
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(new CompoundBorder(
                new EmptyBorder(10, 10, 10, 10), // Outer padding
                new LineBorder(Color.GRAY, 1)    // Border around the panel
        ));

        // Create the body/content panel
        JPanel bodyPanel = new JPanel();
        bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS)); // Vertical alignment
        bodyPanel.setBorder(new EmptyBorder(10, 10, 10, 10)); // Padding inside the body
        bodyPanel.setBackground(new Color(0, 142, 255, 50));
        JLabel bodyLabel1 = new JLabel("<html>" + ci.value() + "</html>");
        bodyLabel1.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        bodyPanel.add(bodyLabel1);
        bodyPanel.add(Box.createRigidArea(new Dimension(0, 5))); // Spacer between components

        panel.add(bodyPanel, BorderLayout.CENTER);

        mainPanel.add(panel);
    }

    private JPanel createConfigItemPanel(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        JPanel item = new JPanel();
        item.setLayout(new BorderLayout());
        item.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
        
        String name = cid.getItem().name();
        JLabel configEntryName = new JLabel(name);
        configEntryName.setForeground(Color.WHITE);
        String description = cid.getItem().description();
        if (!"".equals(description)) {
            configEntryName.setToolTipText("<html>" + name + ":<br>" + description + "</html>");
        }
        item.add(configEntryName, BorderLayout.CENTER);

        // Create appropriate component based on type
        Component valueComponent = createValueComponent(cd, cid);
        if (valueComponent != null) {
            item.add(valueComponent, BorderLayout.EAST);
        }

        return item;
    }

    private Component createValueComponent(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        Type type = cid.getType();
        
        try {
            if (type == boolean.class || type == Boolean.class) {
                return createCheckbox(cd, cid);
            } else if (type == String.class) {
                return createTextField(cd, cid);
            } else if (type == int.class || type == Integer.class) {
                return createIntSpinner(cd, cid);
            } else if (type == double.class || type == Double.class) {
                return createDoubleSpinner(cd, cid);
            } else if (type instanceof Class) {
                Class<?> clazz = (Class<?>) type;
                if (clazz.isEnum()) {
                    return createComboBox(cd, cid);
                }
            }
        } catch (Exception e) {
            log.error("Error creating config component", e);
        }
        
        return new JLabel("Unsupported type: " + type.toString());
    }

    private JCheckBox createCheckbox(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        JCheckBox checkbox = new JCheckBox();
        checkbox.setSelected(Boolean.parseBoolean(configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName())));
        checkbox.addActionListener(ae -> changeConfiguration(checkbox, cd, cid));
        return checkbox;
    }

    private JTextField createTextField(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        JTextField textField = new JTextField(15);
        textField.setText(configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName()));
        textField.addActionListener(ae -> changeConfiguration(textField, cd, cid));
        return textField;
    }

    private JSpinner createIntSpinner(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
        String value = configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName());
        if (value != null) {
            try {
                spinner.setValue(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                log.warn("Invalid integer config value: {}", value);
            }
        }
        spinner.addChangeListener(ae -> changeConfiguration(spinner, cd, cid));
        return spinner;
    }

    private JSpinner createDoubleSpinner(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, Double.MAX_VALUE, 0.1));
        String value = configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName());
        if (value != null) {
            try {
                spinner.setValue(Double.parseDouble(value));
            } catch (NumberFormatException e) {
                log.warn("Invalid double config value: {}", value);
            }
        }
        spinner.addChangeListener(ae -> changeConfiguration(spinner, cd, cid));
        return spinner;
    }

    private JComboBox<Object> createComboBox(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        Class<?> enumClass = (Class<?>) cid.getType();
        Object[] enumConstants = enumClass.getEnumConstants();
        JComboBox<Object> comboBox = new JComboBox<>(enumConstants);
        
        String value = configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName());
        if (value != null) {
            try {
                Object enumValue = Enum.valueOf((Class<? extends Enum>) enumClass, value);
                comboBox.setSelectedItem(enumValue);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid enum config value: {}", value);
            }
        }
        
        comboBox.addActionListener(ae -> changeConfiguration(comboBox, cd, cid));
        return comboBox;
    }

    private void changeConfiguration(Component component, ConfigDescriptor cd, ConfigItemDescriptor cid) {
        final net.runelite.client.config.ConfigItem configItem = cid.getItem();

        if (!configItem.warning().isEmpty()) {
            final int result = JOptionPane.showOptionDialog(component, configItem.warning(),
                "Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new String[]{"Yes", "No"}, "No");

            if (result != JOptionPane.YES_OPTION) {
                rebuild();
                return;
            }
        }

        if (component instanceof JCheckBox) {
            JCheckBox checkbox = (JCheckBox) component;
            configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), "" + checkbox.isSelected());
        } else if (component instanceof JSpinner) {
            JSpinner spinner = (JSpinner) component;
            configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), "" + spinner.getValue());
        } else if (component instanceof JTextField) {
            JTextField textField = (JTextField) component;
            configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), textField.getText());
        } else if (component instanceof JComboBox) {
            JComboBox<?> comboBox = (JComboBox<?>) component;
            configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), ((Enum<?>) comboBox.getSelectedItem()).name());
        }
    }

    /**
     * Updates the toggle button state to reflect the current module status
     */
    public void updateToggleState() {
        if (moduleToggle != null) {
            moduleToggle.setSelected(selectedModule.isRunning());
        }
    }
} 