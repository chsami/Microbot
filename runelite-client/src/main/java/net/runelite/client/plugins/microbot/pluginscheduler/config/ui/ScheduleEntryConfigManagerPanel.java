package net.runelite.client.plugins.microbot.pluginscheduler.config.ui;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.Microbot;

import net.runelite.client.plugins.microbot.ui.MicrobotConfigPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

import net.runelite.client.ui.UnitFormatterFactory;
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
public class ScheduleEntryConfigManagerPanel extends JPanel {
    private static final int BORDER_OFFSET = 5;
    private static final int PANEL_WIDTH = 220;
    private static final int SPINNER_FIELD_WIDTH = 6;
    private final JPanel mainPanel;
    @Getter
    private final ConfigDescriptor configDescriptor;    
	private final ConfigManager configManager;
    private static final Map<ConfigSectionDescriptor, Boolean> sectionExpandStates = new HashMap<>();
    private static final ImageIcon SECTION_EXPAND_ICON;
	private static final ImageIcon SECTION_RETRACT_ICON;
	static final ImageIcon CONFIG_ICON;
	static final ImageIcon BACK_ICON;
	private final TitleCaseListCellRenderer listCellRenderer = new TitleCaseListCellRenderer();
	@Inject
	private ColorPickerManager colorPickerManager;
	//@Inject
	//private final Provider<NotificationPanel> notificationPanelProvider;
    //private final JPanel configPanel;
    static
	{
		final BufferedImage backIcon = ImageUtil.loadImageResource(MicrobotConfigPlugin.class, "config_back_icon.png");
		BACK_ICON = new ImageIcon(backIcon);

		BufferedImage sectionRetractIcon = ImageUtil.loadImageResource(MicrobotConfigPlugin.class, "/util/arrow_right.png");
		sectionRetractIcon = ImageUtil.luminanceOffset(sectionRetractIcon, -121);
		SECTION_EXPAND_ICON = new ImageIcon(sectionRetractIcon);
		final BufferedImage sectionExpandIcon = ImageUtil.rotateImage(sectionRetractIcon, Math.PI / 2);
		SECTION_RETRACT_ICON = new ImageIcon(sectionExpandIcon);
		BufferedImage configIcon = ImageUtil.loadImageResource(MicrobotConfigPlugin.class, "config_edit_icon.png");
		CONFIG_ICON = new ImageIcon(configIcon);
	}
    /**
     * Constructs a new configuration manager for a specific plugin
     * 
     * @param configDescriptor The config descriptor from the plugin
     */
    public ScheduleEntryConfigManagerPanel(ConfigManager configManager, ConfigDescriptor configDescriptor) {
		this.configManager = configManager;
        this.configDescriptor = configDescriptor;                
		mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        rebuild();
		//mainPanel.setBorder(new EmptyBorder(8, 10, 10, 10));
		//mainPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
		//mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void toggleSection(ConfigSectionDescriptor csd, JButton button, JPanel contents)
    {
        boolean newState = !contents.isVisible();
        contents.setVisible(newState);
        button.setIcon(newState ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
        button.setToolTipText(newState ? "Retract" : "Expand");
        sectionExpandStates.put(csd, newState);
        SwingUtilities.invokeLater(contents::revalidate);
    }
    private void rebuild()
	{
		mainPanel.removeAll();


		ConfigDescriptor cd = getConfigDescriptor();

		final Map<String, JPanel> sectionWidgets = new HashMap<>();
		final Map<ConfigObject, JPanel> topLevelPanels = new TreeMap<>((a, b) ->
			ComparisonChain.start()
			.compare(a.position(), b.position())
			.compare(a.name(), b.name())
			.result());

		if (cd.getInformation() != null) {
			buildInformationPanel(cd.getInformation());
		}

		for (ConfigSectionDescriptor csd : cd.getSections())
		{
			ConfigSection cs = csd.getSection();
			final boolean isOpen = sectionExpandStates.getOrDefault(csd, !cs.closedByDefault());

			final JPanel section = new JPanel();
			section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
			section.setMinimumSize(new Dimension(PANEL_WIDTH, 0));

			final JPanel sectionHeader = new JPanel();
			sectionHeader.setLayout(new BorderLayout());
			sectionHeader.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
			// For whatever reason, the header extends out by a single pixel when closed. Adding a single pixel of
			// border on the right only affects the width when closed, fixing the issue.
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

			String name = cs.name();
			final JLabel sectionName = new JLabel(name);
			sectionName.setForeground(ColorScheme.BRAND_ORANGE);
			sectionName.setFont(FontManager.getRunescapeBoldFont());
			sectionName.setToolTipText("<html>" + name + ":<br>" + cs.description() + "</html>");
			sectionHeader.add(sectionName, BorderLayout.CENTER);

			final JPanel sectionContents = new JPanel();
			sectionContents.setLayout(new DynamicGridLayout(0, 1, 0, 5));
			sectionContents.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
			sectionContents.setBorder(new CompoundBorder(
				new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
				new EmptyBorder(BORDER_OFFSET, 0, BORDER_OFFSET, 0)));
			sectionContents.setVisible(isOpen);
			section.add(sectionContents, BorderLayout.SOUTH);

			// Add listeners to each part of the header so that it's easier to toggle them
			final MouseAdapter adapter = new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					toggleSection(csd, sectionToggle, sectionContents);
				}
			};
			sectionToggle.addActionListener(actionEvent -> toggleSection(csd, sectionToggle, sectionContents));
			sectionName.addMouseListener(adapter);
			sectionHeader.addMouseListener(adapter);

			sectionWidgets.put(csd.getKey(), sectionContents);

			topLevelPanels.put(csd, section);
		}

		for (ConfigItemDescriptor cid : cd.getItems())
		{
			if (cid.getItem().hidden())
			{
				continue;
			}

			JPanel item = new JPanel();
			item.setLayout(new BorderLayout());
			item.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
			String name = cid.getItem().name();
			JLabel configEntryName = new JLabel(name);
			configEntryName.setForeground(Color.WHITE);
			String description = cid.getItem().description();
			if (!"".equals(description))
			{
				configEntryName.setToolTipText("<html>" + name + ":<br>" + description + "</html>");
			}
			
			item.add(configEntryName, BorderLayout.CENTER);

			if (cid.getType() == boolean.class)
			{
				item.add(createCheckbox(cd, cid), BorderLayout.EAST);
			}
			else if (cid.getType() == int.class)
			{
				item.add(createIntSpinner(cd, cid), BorderLayout.EAST);
			}
			else if (cid.getType() == double.class)
			{
				item.add(createDoubleSpinner(cd, cid), BorderLayout.EAST);
			}
			else if (cid.getType() == String.class)
			{
				item.add(createTextField(cd, cid), BorderLayout.SOUTH);
			}
			else if (cid.getType() == Color.class)
			{
				item.add(createColorPicker(cd, cid), BorderLayout.EAST);
			}
			else if (cid.getType() == Dimension.class)
			{
				item.add(createDimension(cd, cid), BorderLayout.EAST);
			}
			else if (cid.getType() instanceof Class && ((Class<?>) cid.getType()).isEnum())
			{
				item.add(createComboBox(cd, cid), BorderLayout.EAST);
			}
			else if (cid.getType() == Keybind.class || cid.getType() == ModifierlessKeybind.class)
			{
				item.add(createKeybind(cd, cid), BorderLayout.EAST);
			}
			else if (cid.getType() == Notification.class)
			{
				//item.add(createNotification(cd, cid), BorderLayout.EAST);
			}
			else if (cid.getType() instanceof ParameterizedType)
			{
				ParameterizedType parameterizedType = (ParameterizedType) cid.getType();
				if (parameterizedType.getRawType() == Set.class)
				{
					item.add(createList(cd, cid), BorderLayout.EAST);
				}
			}

			JPanel section = sectionWidgets.get(cid.getItem().section());
			if (section == null)
			{
				topLevelPanels.put(cid, item);
			}
			else
			{
				section.add(item);
			}
		}

		topLevelPanels.values().forEach(mainPanel::add);
	
		
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

	private JCheckBox createCheckbox(ConfigDescriptor cd, ConfigItemDescriptor cid)
	{
		ConfigManager configManager = Microbot.getConfigManager();
        if (configManager == null)
        {
            return new JCheckBox();
        }
        JCheckBox checkbox = new JCheckBox();
		checkbox.setSelected(Boolean.parseBoolean(configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName())));
		checkbox.addActionListener(ae -> changeConfiguration(checkbox, cd, cid));
		return checkbox;
	}

	private JSpinner createIntSpinner(ConfigDescriptor cd, ConfigItemDescriptor cid)
	{
        ConfigManager configManager = Microbot.getConfigManager();
        if (configManager == null)
        {
            return new JSpinner();
        }
		int value = MoreObjects.firstNonNull(configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName(), int.class), 0);

		Range range = cid.getRange();
		int min = 0, max = Integer.MAX_VALUE;
		if (range != null)
		{
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
		if (units != null)
		{
			// The existing DefaultFormatterFactory with a NumberEditorFormatter. Its model is the same SpinnerModel above.
			JFormattedTextField.AbstractFormatterFactory delegate = spinnerTextField.getFormatterFactory();
			spinnerTextField.setFormatterFactory(new UnitFormatterFactory(delegate, units.value()));
		}

		return spinner;
	}

	private JSpinner createDoubleSpinner(ConfigDescriptor cd, ConfigItemDescriptor cid)
	{
		double value = MoreObjects.firstNonNull(configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName(), double.class), 0d);

		SpinnerModel model = new SpinnerNumberModel(value, 0, Double.MAX_VALUE, 0.1);
		JSpinner spinner = new JSpinner(model);
		Component editor = spinner.getEditor();
		JFormattedTextField spinnerTextField = ((JSpinner.DefaultEditor) editor).getTextField();
		spinnerTextField.setColumns(SPINNER_FIELD_WIDTH);
		spinner.addChangeListener(ce -> changeConfiguration(spinner, cd, cid));

		Units units = cid.getUnits();
		if (units != null)
		{
			// The existing DefaultFormatterFactory with a NumberEditorFormatter. Its model is the same SpinnerModel above.
			JFormattedTextField.AbstractFormatterFactory delegate = spinnerTextField.getFormatterFactory();
			spinnerTextField.setFormatterFactory(new UnitFormatterFactory(delegate, units.value()));
		}

		return spinner;
	}

	private JTextComponent createTextField(ConfigDescriptor cd, ConfigItemDescriptor cid)
	{
		JTextComponent textField;

		if (cid.getItem().secret())
		{
			textField = new JPasswordField();
		}
		else
		{
			final JTextArea textArea = new JTextArea();
			textArea.setLineWrap(true);
			textArea.setWrapStyleWord(true);
			textField = textArea;
		}

		textField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		textField.setText(configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName()));

		textField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				changeConfiguration(textField, cd, cid);
			}
		});

		return textField;
	}

	private ColorJButton createColorPicker(ConfigDescriptor cd, ConfigItemDescriptor cid)
	{
		Color existing = configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName(), Color.class);

		ColorJButton colorPickerBtn;

		boolean alphaHidden = cid.getAlpha() == null;

		if (existing == null)
		{
			colorPickerBtn = new ColorJButton("Pick a color", Color.BLACK);
		}
		else
		{
			String colorHex = "#" + (alphaHidden ? ColorUtil.colorToHexCode(existing) : ColorUtil.colorToAlphaHexCode(existing)).toUpperCase();
			colorPickerBtn = new ColorJButton(colorHex, existing);
		}

		colorPickerBtn.setFocusable(false);
		colorPickerBtn.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				RuneliteColorPicker colorPicker = colorPickerManager.create(
					ScheduleEntryConfigManagerPanel.this,
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

	private JPanel createDimension(ConfigDescriptor cd, ConfigItemDescriptor cid)
	{
		
        
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

	private JComboBox<Enum<?>> createComboBox(ConfigDescriptor cd, ConfigItemDescriptor cid)
	{
		Class<? extends Enum> type = (Class<? extends Enum>) cid.getType();

		JComboBox<Enum<?>> box = new JComboBox<Enum<?>>(type.getEnumConstants()); // NOPMD: UseDiamondOperator
		// set renderer prior to calling box.getPreferredSize(), since it will invoke the renderer
		// to build components for each combobox element in order to compute the display size of the
		// combobox
		box.setRenderer(listCellRenderer);
		box.setPreferredSize(new Dimension(box.getPreferredSize().width, 22));

		try
		{
			Enum<?> selectedItem = Enum.valueOf(type, configManager.getConfiguration(cd.getGroup().value(), cid.getItem().keyName()));
			box.setSelectedItem(selectedItem);
			box.setToolTipText(Text.titleCase(selectedItem));
		}
		catch (IllegalArgumentException ex)
		{
			log.debug("invalid selected item", ex);
		}
		box.addItemListener(e ->
		{
			if (e.getStateChange() == ItemEvent.SELECTED)
			{
				changeConfiguration(box, cd, cid);
				box.setToolTipText(Text.titleCase((Enum<?>) box.getSelectedItem()));
			}
		});

		return box;
	}

	private HotkeyButton createKeybind(ConfigDescriptor cd, ConfigItemDescriptor cid)
	{
		Keybind startingValue = configManager.getConfiguration(cd.getGroup().value(),
			cid.getItem().keyName(),
			(Class<? extends Keybind>) cid.getType());

		HotkeyButton button = new HotkeyButton(startingValue, cid.getType() == ModifierlessKeybind.class);

		button.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				changeConfiguration(button, cd, cid);
			}
		});

		return button;
	}

	

	private JList<Enum<?>> createList(ConfigDescriptor cd, ConfigItemDescriptor cid)
	{
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
		list.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				changeConfiguration(list, cd, cid);
			}
		});

		return list;
	}

    private void changeConfiguration(Component component, ConfigDescriptor cd, ConfigItemDescriptor cid)
	{
        ConfigManager configManager = Microbot.getConfigManager();
        if (configManager == null)
        {
            return;
        }
        
        final ConfigItem configItem = cid.getItem();

		if (!Strings.isNullOrEmpty(configItem.warning()))
		{
			final int result = JOptionPane.showOptionDialog(component, configItem.warning(),
				"Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
				null, new String[]{"Yes", "No"}, "No");

			if (result != JOptionPane.YES_OPTION)
			{
				rebuild();
				return;
			}
		}

		if (component instanceof JCheckBox)
		{
			JCheckBox checkbox = (JCheckBox) component;
			configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), "" + checkbox.isSelected());
		}
		else if (component instanceof JSpinner)
		{
			JSpinner spinner = (JSpinner) component;
			configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), "" + spinner.getValue());
		}
		else if (component instanceof JTextComponent)
		{
			JTextComponent textField = (JTextComponent) component;
			configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), textField.getText());
		}
		else if (component instanceof RuneliteColorPicker)
		{
			RuneliteColorPicker colorPicker = (RuneliteColorPicker) component;
			configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), colorPicker.getSelectedColor().getRGB() + "");
		}
		else if (component instanceof JComboBox)
		{
			JComboBox jComboBox = (JComboBox) component;
			configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), ((Enum) jComboBox.getSelectedItem()).name());
		}
		else if (component instanceof HotkeyButton)
		{
			HotkeyButton hotkeyButton = (HotkeyButton) component;
			configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), hotkeyButton.getValue());
		}
		else if (component instanceof JList)
		{
			JList<?> list = (JList<?>) component;
			List<?> selectedValues = list.getSelectedValuesList();

			configManager.setConfiguration(cd.getGroup().value(), cid.getItem().keyName(), Sets.newHashSet(selectedValues));
		}
	}
}
