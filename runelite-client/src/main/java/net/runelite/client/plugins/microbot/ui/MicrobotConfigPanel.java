/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.ui;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.MicrobotConfigManager;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2Config;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.PluginStopHelper;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.PluginStopOption;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ColorJButton;
import net.runelite.client.ui.components.TitleCaseListCellRenderer;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.ArrayUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.*;

@Slf4j
class MicrobotConfigPanel extends MicrobotPluginPanel {
    private static final int SPINNER_FIELD_WIDTH = 6;
    private static final ImageIcon SECTION_EXPAND_ICON;
    private static final ImageIcon SECTION_RETRACT_ICON;
    static final ImageIcon CONFIG_ICON;
    static final ImageIcon BACK_ICON;

    private static final Map<ConfigSectionDescriptor, Boolean> sectionExpandStates = new HashMap<>();

    static {
        final BufferedImage backIcon = ImageUtil.loadImageResource(MicrobotConfigPanel.class, "config_back_icon.png");
        BACK_ICON = new ImageIcon(backIcon);

        BufferedImage sectionRetractIcon = ImageUtil.loadImageResource(MicrobotConfigPanel.class, "/util/arrow_right.png");
        sectionRetractIcon = ImageUtil.luminanceOffset(sectionRetractIcon, -121);
        SECTION_EXPAND_ICON = new ImageIcon(sectionRetractIcon);
        final BufferedImage sectionExpandIcon = ImageUtil.rotateImage(sectionRetractIcon, Math.PI / 2);
        SECTION_RETRACT_ICON = new ImageIcon(sectionExpandIcon);
        BufferedImage configIcon = ImageUtil.loadImageResource(MicrobotConfigPanel.class, "config_edit_icon.png");
        CONFIG_ICON = new ImageIcon(configIcon);
    }

    private final MicrobotPluginListPanel pluginList;
    private final ConfigManager configManager;
    private final PluginManager pluginManager;
    private final ExternalPluginManager externalPluginManager;
    private final ColorPickerManager colorPickerManager;
    private final Provider<MicrobotNotificationPanel> notificationPanelProvider;

    private final TitleCaseListCellRenderer listCellRenderer = new TitleCaseListCellRenderer();

    private final MicrobotFixedWidthPanel mainPanel;
    private final JLabel title;
    private final MicrobotPluginToggleButton pluginToggle;

    private MicrobotPluginConfigurationDescriptor pluginConfig = null;
    // add near other fields
    private final JTextField searchField = new JTextField();
    private final Map<JPanel, String> itemIndex = new HashMap<>();     // item panel -> lowercased name
    private final Map<String, JPanel> sectionContentByKey = new HashMap<>(); // section key -> contents panel
    private final Map<ConfigSectionDescriptor, JPanel> sectionPanelByDesc = new HashMap<>(); // whole section panel

    @Inject
    private MicrobotConfigPanel(
            MicrobotPluginListPanel pluginList,
            ConfigManager configManager,
            PluginManager pluginManager,
            ExternalPluginManager externalPluginManager,
            ColorPickerManager colorPickerManager,
            Provider<MicrobotNotificationPanel> notificationPanelProvider
    ) {
        super(false);

        this.pluginList = pluginList;
        this.configManager = configManager;
        this.pluginManager = pluginManager;
        this.externalPluginManager = externalPluginManager;
        this.colorPickerManager = colorPickerManager;
        this.notificationPanelProvider = notificationPanelProvider;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // --- initialize mainPanel first ---
        mainPanel = new MicrobotFixedWidthPanel();
        mainPanel.setBorder(new EmptyBorder(8, 10, 10, 10));
        mainPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel northPanel = new MicrobotFixedWidthPanel();
        northPanel.setLayout(new BorderLayout());
        northPanel.add(mainPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(northPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        // --- topPanel construction ---
        JPanel topPanel = new JPanel(new BorderLayout(0, BORDER_OFFSET));
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(topPanel, BorderLayout.NORTH);

        JPanel header = new JPanel(new BorderLayout());
        JButton backBtn = new JButton(BACK_ICON);
        SwingUtil.removeButtonDecorations(backBtn);
        backBtn.setPreferredSize(new Dimension(22, 0));
        backBtn.setBorder(new EmptyBorder(0, 0, 0, 5));
        backBtn.addActionListener(e -> pluginList.getMuxer().popState());
        backBtn.setToolTipText("Back");

        pluginToggle = new MicrobotPluginToggleButton();
        title = new JLabel();
        title.setForeground(Color.WHITE);

        header.add(backBtn, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        header.add(pluginToggle, BorderLayout.EAST);
        topPanel.add(header, BorderLayout.NORTH);

        // --- search field ---
        searchField.setToolTipText("Filter settings");
        searchField.putClientProperty("JTextField.placeholderText", "Search settings...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }
        });
        searchField.setPreferredSize(new Dimension(10, 26));
        topPanel.add(searchField, BorderLayout.CENTER);
    }


    private void applyFilter() {
        String q = searchField.getText();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        // show all if empty
        boolean noFilter = needle.isEmpty();

        // toggle each item
        for (Map.Entry<JPanel, String> e : itemIndex.entrySet()) {
            boolean match = noFilter || e.getValue().contains(needle);
            e.getKey().setVisible(match);
        }

        // hide section contents with no visible children
        for (Map.Entry<String, JPanel> e : sectionContentByKey.entrySet()) {
            JPanel contents = e.getValue();
            boolean anyVisible = false;
            for (Component c : contents.getComponents()) {
                if (c.isVisible()) {
                    anyVisible = true;
                    break;
                }
            }
            contents.getParent().setVisible(anyVisible); // whole section panel
            contents.setVisible(anyVisible);             // keep contents open when filtering

            // auto-expand sections with matches
            if (!anyVisible) continue;
            // ensure expanded when filtering
            if (!noFilter && !contents.isVisible()) contents.setVisible(true);
        }

        mainPanel.revalidate();
        mainPanel.repaint();
    }

    void init(MicrobotPluginConfigurationDescriptor pluginConfig) {
        assert this.pluginConfig == null;
        this.pluginConfig = pluginConfig;

        String name = pluginConfig.getName();
        title.setText(name);
        title.setForeground(Color.WHITE);
        title.setToolTipText("<html>" + name + ":<br>" + pluginConfig.getDescription() + "</html>");

        String iname = pluginConfig.getInternalPluginHubName();
        JMenuItem uninstallItem = null;
        if (iname != null) {
            uninstallItem = new JMenuItem("Uninstall");
            uninstallItem.addActionListener(ev -> externalPluginManager.remove(iname));
        }

        MicrobotPluginListItem.addLabelPopupMenu(title, pluginConfig.createSupportMenuItem(pluginConfig.getPlugin()), uninstallItem);

        if (pluginConfig.getPlugin() != null) {
            pluginToggle.setConflicts(pluginConfig.getConflicts());
            pluginToggle.setSelected(pluginManager.isPluginEnabled(pluginConfig.getPlugin()));
            pluginToggle.addItemListener(i ->
            {
                if (pluginToggle.isSelected()) {
                    pluginList.startPlugin(pluginConfig.getPlugin());
                } else {
                    pluginList.stopPlugin(pluginConfig.getPlugin());
                }
            });
        } else {
            pluginToggle.setVisible(false);
        }

        rebuild();
    }

    private void toggleSection(ConfigSectionDescriptor csd, JButton button, JPanel contents) {
        boolean newState = !contents.isVisible();
        contents.setVisible(newState);
        button.setIcon(newState ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
        button.setToolTipText(newState ? "Retract" : "Expand");
        sectionExpandStates.put(csd, newState);
        SwingUtilities.invokeLater(contents::revalidate);
    }

    private void rebuild() {
        mainPanel.removeAll();
        itemIndex.clear();
        sectionContentByKey.clear();
        sectionPanelByDesc.clear();

        ConfigDescriptor cd = pluginConfig.getConfigDescriptor();

        final Map<String, JPanel> sectionWidgets = new HashMap<>();
        final Map<ConfigObject, JPanel> topLevelPanels = new TreeMap<>((a, b) ->
                ComparisonChain.start().compare(a.position(), b.position()).compare(a.name(), b.name()).result());

        if (cd.getInformation() != null) {
            buildInformationPanel(cd.getInformation());
        }

        for (ConfigSectionDescriptor csd : cd.getSections()) {
            ConfigSection cs = csd.getSection();
            boolean isOpen = sectionExpandStates.getOrDefault(csd, !cs.closedByDefault());

            JPanel section = new JPanel();
            section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
            section.setMinimumSize(new Dimension(PANEL_WIDTH, 0));

            JPanel sectionHeader = new JPanel(new BorderLayout());
            sectionHeader.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
            sectionHeader.setBorder(new CompoundBorder(
                    new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                    new EmptyBorder(0, 0, 3, 1)));
            section.add(sectionHeader, BorderLayout.NORTH);

            JButton sectionToggle = new JButton(isOpen ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
            sectionToggle.setPreferredSize(new Dimension(18, 0));
            sectionToggle.setBorder(new EmptyBorder(0, 0, 0, 5));
            sectionToggle.setToolTipText(isOpen ? "Retract" : "Expand");
            SwingUtil.removeButtonDecorations(sectionToggle);
            sectionHeader.add(sectionToggle, BorderLayout.WEST);

            String name = cs.name();
            JLabel sectionName = new JLabel(name);
            sectionName.setForeground(ColorScheme.BRAND_ORANGE);
            sectionName.setFont(FontManager.getRunescapeBoldFont());
            sectionName.setToolTipText("<html>" + name + ":<br>" + cs.description() + "</html>");
            sectionHeader.add(sectionName, BorderLayout.CENTER);

            JPanel sectionContents = new JPanel(new DynamicGridLayout(0, 1, 0, 5));
            sectionContents.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
            sectionContents.setBorder(new CompoundBorder(
                    new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                    new EmptyBorder(BORDER_OFFSET, 0, BORDER_OFFSET, 0)));
            sectionContents.setVisible(isOpen);
            section.add(sectionContents, BorderLayout.SOUTH);

            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggleSection(csd, sectionToggle, sectionContents);
                }
            };

            sectionToggle.addActionListener(ev -> toggleSection(csd, sectionToggle, sectionContents));
            sectionName.addMouseListener(adapter);
            sectionHeader.addMouseListener(adapter);

            sectionWidgets.put(csd.getKey(), sectionContents);
            sectionPanelByDesc.put(csd, section);
            sectionContentByKey.put(csd.getKey(), sectionContents);

            topLevelPanels.put(csd, section);
        }
        for (ConfigItemDescriptor cid : cd.getItems()) {
            if (cid.getItem().hidden()) {
                continue;
            }

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
            MicrobotPluginListItem.addLabelPopupMenu(configEntryName, createResetMenuItem(pluginConfig, cid));
            boolean isButtonItem = cid.getType() == ConfigButton.class;
            if (!isButtonItem) {
                item.add(configEntryName, BorderLayout.CENTER);
            }

            if (isBreakHandlerStopConfig(cd, cid)) {
                item.add(createPluginStopComboBox(cd, cid), BorderLayout.EAST);
            } else if (cid.getType() == boolean.class) {
                item.add(createCheckbox(cd, cid), BorderLayout.EAST);
            } else if (cid.getType() == int.class) {
                item.add(createIntSpinner(cd, cid), BorderLayout.EAST);
            } else if (cid.getType() == double.class) {
                item.add(createDoubleSpinner(cd, cid), BorderLayout.EAST);
            } else if (cid.getType() == String.class) {
                item.add(createTextField(cd, cid), BorderLayout.SOUTH);
            } else if (cid.getType() == ConfigButton.class) {
                item.add(createActionButton(cd, cid), BorderLayout.CENTER);
            } else if (cid.getType() == Color.class) {
                item.add(createColorPicker(cd, cid), BorderLayout.EAST);
            } else if (cid.getType() == Dimension.class) {
                item.add(createDimension(cd, cid), BorderLayout.EAST);
            } else if (cid.getType() instanceof Class && ((Class<?>) cid.getType()).isEnum()) {
                item.add(createComboBox(cd, cid), BorderLayout.EAST);
            } else if (cid.getType() == Keybind.class || cid.getType() == ModifierlessKeybind.class) {
                item.add(createKeybind(cd, cid), BorderLayout.EAST);
            } else if (cid.getType() == Notification.class) {
                item.add(createNotification(cd, cid), BorderLayout.EAST);
            } else if (cid.getType() instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) cid.getType();
                if (parameterizedType.getRawType() == Set.class) {
                    item.add(createList(cd, cid), BorderLayout.EAST);
                }
            }

            JPanel section = sectionWidgets.get(cid.getItem().section());
            if (section == null) {
                topLevelPanels.put(cid, item);
            } else {
                section.add(item);
            }
            itemIndex.put(item, name.toLowerCase(Locale.ROOT));
        }

        topLevelPanels.values().forEach(mainPanel::add);

        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener((e) ->
        {
            final int result = JOptionPane.showOptionDialog(resetButton, "Are you sure you want to reset this plugin's configuration?",
                    "Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, new String[]{"Yes", "No"}, "No");

            if (result == JOptionPane.YES_OPTION) {
                configManager.setDefaultConfiguration(pluginConfig.getConfig(), true);
                MicrobotConfigManager.resetProfilePluginProperties(pluginConfig.getPlugin().getClass());
                // Reset non-config panel keys
                Plugin plugin = pluginConfig.getPlugin();
                if (plugin != null) {
                    plugin.resetConfiguration();
                }

                rebuild();
            }
        });
        mainPanel.add(resetButton);

        JButton backButton = new JButton("Back");
        backButton.addActionListener(e -> pluginList.getMuxer().popState());
        mainPanel.add(backButton);

        revalidate();
        applyFilter();
    }

    private boolean isBreakHandlerStopConfig(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        return BreakHandlerV2Config.configGroup.equals(cd.getGroup().value())
                && "pluginToStop".equals(cid.getItem().keyName());
    }

    private JComboBox<PluginStopOption> createPluginStopComboBox(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        List<PluginStopOption> options = PluginStopHelper.buildOptions(pluginManager);
        JComboBox<PluginStopOption> box = new JComboBox<>(new DefaultComboBoxModel<>(options.toArray(new PluginStopOption[0])));

        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PluginStopOption) {
                    setText(((PluginStopOption) value).getDisplayName());
                }
                return this;
            }
        });

        String storedRaw = configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName());
        String normalized = PluginStopHelper.normalizeStoredValue(storedRaw, pluginManager);
        PluginStopOption selected = options.stream()
                .filter(opt -> opt.getClassName().equals(normalized))
                .findFirst()
                .orElse(PluginStopOption.none());

        // migrate legacy enum values to class names
        if (!Objects.equals(storedRaw, normalized)) {
            configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), normalized);
        }

        box.setSelectedItem(selected);
        box.setToolTipText(selected.getDisplayName());
        box.setPreferredSize(new Dimension(box.getPreferredSize().width, 22));

        box.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                PluginStopOption opt = (PluginStopOption) e.getItem();
                configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), opt.getClassName());
                box.setToolTipText(opt.getDisplayName());
            }
        });

        return box;
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

    private JCheckBox createCheckbox(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        JCheckBox checkbox = new JCheckBox();
        checkbox.setSelected(Boolean.parseBoolean(configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName())));
        checkbox.addActionListener(ae -> changeConfiguration(checkbox, cd, cid));
        return checkbox;
    }

    private JSpinner createIntSpinner(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        int value = MoreObjects.firstNonNull(configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName(), int.class), 0);

        Range range = cid.getRange();
        int min = 0, max = Integer.MAX_VALUE;
        if (range != null) {
            min = range.min();
            max = range.max();
        }

        // Config may previously have been out of range
        value = Ints.constrainToRange(value, min, max);

        SpinnerModel model = new SpinnerNumberModel(value, min, max, 1);
        JSpinner spinner = new JSpinner(model);
        Component editor = spinner.getEditor();
        JFormattedTextField spinnerTextField = ((JSpinner.DefaultEditor) editor).getTextField();
        spinnerTextField.setColumns(SPINNER_FIELD_WIDTH);
        spinner.addChangeListener(ce -> changeConfiguration(spinner, cd, cid));

        Units units = cid.getUnits();
        if (units != null) {
            spinnerTextField.setFormatterFactory(new UnitFormatterFactory(units.value()));
        }

        return spinner;
    }

    private JSpinner createDoubleSpinner(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        double value = MoreObjects.firstNonNull(configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName(), double.class), 0d);

        SpinnerModel model = new SpinnerNumberModel(value, 0, Double.MAX_VALUE, 0.1);
        JSpinner spinner = new JSpinner(model);
        Component editor = spinner.getEditor();
        JFormattedTextField spinnerTextField = ((JSpinner.DefaultEditor) editor).getTextField();
        spinnerTextField.setColumns(SPINNER_FIELD_WIDTH);
        spinner.addChangeListener(ce -> changeConfiguration(spinner, cd, cid));

        Units units = cid.getUnits();
        if (units != null) {
            spinnerTextField.setFormatterFactory(new UnitFormatterFactory(units.value()));
        }

        return spinner;
    }

    private JTextComponent createTextField(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        JTextComponent textField;

        if (cid.getItem().secret()) {
            textField = new JPasswordField();
        } else {
            final JTextArea textArea = new JTextArea();
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textField = textArea;
        }

        textField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        textField.setText(configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName()));

        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                changeConfiguration(textField, cd, cid);
            }
        });

        return textField;
    }

    private ColorJButton createColorPicker(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        Color existing = configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName(), Color.class);

        ColorJButton colorPickerBtn;

        boolean alphaHidden = cid.getAlpha() == null;

        if (existing == null) {
            colorPickerBtn = new ColorJButton("Pick a color", Color.BLACK);
        } else {
            String colorHex = "#" + (alphaHidden ? ColorUtil.colorToHexCode(existing) : ColorUtil.colorToAlphaHexCode(existing)).toUpperCase();
            colorPickerBtn = new ColorJButton(colorHex, existing);
        }

        colorPickerBtn.setFocusable(false);
        colorPickerBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                RuneliteColorPicker colorPicker = colorPickerManager.create(
                        SwingUtilities.windowForComponent(MicrobotConfigPanel.this),
                        colorPickerBtn.getColor(),
                        cid.getItem().name(),
                        alphaHidden);
                colorPicker.setLocationRelativeTo(colorPickerBtn);
                colorPicker.setOnColorChange(c ->
                {
                    colorPickerBtn.setColor(c);
                    colorPickerBtn.setText("#" + (alphaHidden ? ColorUtil.colorToHexCode(c) : ColorUtil.colorToAlphaHexCode(c)).toUpperCase());
                });
                colorPicker.setOnClose(c -> changeConfiguration(colorPicker, cd, cid));
                colorPicker.setVisible(true);
            }
        });

        return colorPickerBtn;
    }

    private JPanel createDimension(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        JPanel dimensionPanel = new JPanel();
        dimensionPanel.setLayout(new BorderLayout());

        Dimension dimension = MoreObjects.firstNonNull(configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName(), Dimension.class), new Dimension());
        int width = dimension.width;
        int height = dimension.height;

        SpinnerModel widthModel = new SpinnerNumberModel(width, 0, Integer.MAX_VALUE, 1);
        JSpinner widthSpinner = new JSpinner(widthModel);
        Component widthEditor = widthSpinner.getEditor();
        JFormattedTextField widthSpinnerTextField = ((JSpinner.DefaultEditor) widthEditor).getTextField();
        widthSpinnerTextField.setColumns(4);

        SpinnerModel heightModel = new SpinnerNumberModel(height, 0, Integer.MAX_VALUE, 1);
        JSpinner heightSpinner = new JSpinner(heightModel);
        Component heightEditor = heightSpinner.getEditor();
        JFormattedTextField heightSpinnerTextField = ((JSpinner.DefaultEditor) heightEditor).getTextField();
        heightSpinnerTextField.setColumns(4);

        ChangeListener listener = e ->
                configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), widthSpinner.getValue() + "x" + heightSpinner.getValue());

        widthSpinner.addChangeListener(listener);
        heightSpinner.addChangeListener(listener);

        dimensionPanel.add(widthSpinner, BorderLayout.WEST);
        dimensionPanel.add(new JLabel(" x "), BorderLayout.CENTER);
        dimensionPanel.add(heightSpinner, BorderLayout.EAST);

        return dimensionPanel;
    }

    private JComboBox<Enum<?>> createComboBox(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        Class<? extends Enum> type = (Class<? extends Enum>) cid.getType();

        JComboBox<Enum<?>> box = new JComboBox<Enum<?>>(type.getEnumConstants()); // NOPMD: UseDiamondOperator
        // set renderer prior to calling box.getPreferredSize(), since it will invoke the renderer
        // to build components for each combobox element in order to compute the display size of the
        // combobox
        box.setRenderer(listCellRenderer);
        box.setPreferredSize(new Dimension(box.getPreferredSize().width, 22));

        try {
            Enum<?> selectedItem = Enum.valueOf(type, configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName()));
            box.setSelectedItem(selectedItem);
            box.setToolTipText(Text.titleCase(selectedItem));
        } catch (NullPointerException | IllegalArgumentException ex) {
            log.debug("invalid selected item", ex);
        }
        box.addItemListener(e ->
        {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                changeConfiguration(box, cd, cid);
                box.setToolTipText(Text.titleCase((Enum<?>) box.getSelectedItem()));
            }
        });

        return box;
    }

    private MicrobotHotkeyButton createKeybind(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        Keybind startingValue = configManager.getConfiguration(cd.getGroup().value(),
                cid.getItem().keyName(),
                (Class<? extends Keybind>) cid.getType());

        MicrobotHotkeyButton button = new MicrobotHotkeyButton(startingValue, cid.getType() == ModifierlessKeybind.class);

        button.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                changeConfiguration(button, cd, cid);
            }
        });

        return button;
    }

    private JPanel createNotification(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JButton button = new JButton(MicrobotConfigPanel.CONFIG_ICON);
        SwingUtil.removeButtonDecorations(button);
        button.setPreferredSize(new Dimension(25, 0));
        button.addActionListener(l ->
        {
            var muxer = pluginList.getMuxer();
            var notifPanel = notificationPanelProvider.get();
            notifPanel.init(cd, cid);
            muxer.pushState(notifPanel);
        });
        panel.add(button, BorderLayout.WEST);

        JCheckBox checkbox = new JCheckBox();
        {
            Notification notif = configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName(), Notification.class);
            if (notif == null) {
                checkbox.setSelected(false);
            } else {
                checkbox.setSelected(notif.isEnabled());
            }
        }
        checkbox.addActionListener(ae ->
        {
            button.setVisible(checkbox.isSelected());

            Notification notif = configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName(), Notification.class);
            configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), notif.withEnabled(checkbox.isSelected()));
        });
        checkbox.setBackground(ColorScheme.LIGHT_GRAY_COLOR);
        panel.add(checkbox, BorderLayout.EAST);

        // button visibility is tied to the checkbox
        button.setVisible(checkbox.isSelected());
        return panel;
    }

    private JList<Enum<?>> createList(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        ParameterizedType parameterizedType = (ParameterizedType) cid.getType();
        Class<? extends Enum> type = (Class<? extends Enum>) parameterizedType.getActualTypeArguments()[0];
        Set<? extends Enum> set = configManager.getConfiguration(cd.getGroup().value(), null,
                cid.getItem().keyName(), parameterizedType);

        JList<Enum<?>> list = new JList<Enum<?>>(type.getEnumConstants()); // NOPMD: UseDiamondOperator
        list.setCellRenderer(listCellRenderer);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setLayoutOrientation(JList.VERTICAL);
        list.setSelectedIndices(
                MoreObjects.firstNonNull(set, Collections.emptySet())
                        .stream()
                        .mapToInt(e -> ArrayUtils.indexOf(type.getEnumConstants(), e))
                        .toArray());
        list.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                changeConfiguration(list, cd, cid);
            }
        });

        return list;
    }

    private JButton createActionButton(ConfigDescriptor cd, ConfigItemDescriptor cid) {
        JButton button = new JButton(cid.getItem().name());
        button.addActionListener(e -> {
            ConfigButton next = new ConfigButton(UUID.randomUUID().toString());
            configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), next);
        });
        return button;
    }

    private void changeConfiguration(Component component, ConfigDescriptor cd, ConfigItemDescriptor cid) {
        final ConfigItem configItem = cid.getItem();

        if (!Strings.isNullOrEmpty(configItem.warning())) {
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
        } else if (component instanceof JTextComponent) {
            JTextComponent textField = (JTextComponent) component;
            configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), textField.getText());
        } else if (component instanceof RuneliteColorPicker) {
            RuneliteColorPicker colorPicker = (RuneliteColorPicker) component;
            configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), colorPicker.getSelectedColor().getRGB() + "");
        } else if (component instanceof JComboBox) {
            JComboBox jComboBox = (JComboBox) component;
            Object selected = jComboBox.getSelectedItem();
            if (selected instanceof Enum) {
                configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), ((Enum<?>) selected).name());
            } else {
                configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), selected != null ? selected.toString() : "");
            }
        } else if (component instanceof MicrobotHotkeyButton) {
            MicrobotHotkeyButton hotkeyButton = (MicrobotHotkeyButton) component;
            configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), hotkeyButton.getValue());
        } else if (component instanceof JList) {
            JList<?> list = (JList<?>) component;
            List<?> selectedValues = list.getSelectedValuesList();

            configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), Sets.newHashSet(selectedValues));
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(PANEL_WIDTH + SCROLLBAR_WIDTH, super.getPreferredSize().height);
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        if (event.getPlugin() == this.pluginConfig.getPlugin()) {
            SwingUtilities.invokeLater(() ->
                    pluginToggle.setSelected(event.isLoaded()));
        }
    }

    @Subscribe
    private void onExternalPluginsChanged(ExternalPluginsChanged ev) {
        if (pluginManager.getPlugins().stream()
                .noneMatch(p -> p == this.pluginConfig.getPlugin())) {
            pluginList.getMuxer().popState();
        }
        SwingUtilities.invokeLater(this::rebuild);
    }

    @Subscribe
    private void onProfileChanged(ProfileChanged profileChanged) {
        SwingUtilities.invokeLater(this::rebuild);
    }

    private JMenuItem createResetMenuItem(MicrobotPluginConfigurationDescriptor pluginConfig, ConfigItemDescriptor configItemDescriptor) {
        JMenuItem menuItem = new JMenuItem("Reset");
        menuItem.addActionListener(e ->
        {
            ConfigDescriptor configDescriptor = pluginConfig.getConfigDescriptor();
            ConfigGroup configGroup = configDescriptor.getGroup();
            ConfigItem configItem = configItemDescriptor.getItem();

            // To reset one item we'll just unset it and then apply defaults over the whole group
            configManager.unsetConfiguration(configGroup.value(), configItem.keyName());
            configManager.setDefaultConfiguration(pluginConfig.getConfig(), false);

            rebuild();
        });
        return menuItem;
    }
}
