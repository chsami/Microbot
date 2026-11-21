package net.runelite.client.plugins.microbot.inventorysetups.ui;

import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PanelFonts;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;


// Displays a warning that layouts will not work.
public class InventorySetupsLayoutWarningPanel extends JPanel
{

	InventorySetupsLayoutWarningPanel(MInventorySetupsPlugin plugin, InventorySetupsPluginPanel panel)
	{
		final String warningText = "Inventory Setups has detected that layouts will not work with your current settings.\n\n" +
				"The Bank Tags Plugin must be ON to use bank filtering and layouts.\n\n" +
				"Inventory Setups can turn it on for you. This is recommended so you can use all features of Inventory Setups.\n\n" +
				"Hub Plugin \"Bank Tag Layouts\" is not required with Inventory Setups. If you only used it with Inventory Setups, you can safely uninstall it.\n\n" +
				"You can disable this warning in the settings.";

		JTextArea textArea = new JTextArea(2, 20);
		textArea.setText(warningText);
		textArea.setWrapStyleWord(true);
		textArea.setLineWrap(true);
		textArea.setOpaque(false);
		textArea.setEditable(false);
		textArea.setFocusable(false);
		textArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
		Font textAreaFont = PanelFonts.regular();
		textAreaFont = textAreaFont.deriveFont(textAreaFont.getStyle(), (float)textAreaFont.getSize() - (float)0.1);
		textArea.setFont(textAreaFont);

		textArea.setBorder(new EmptyBorder(0, 0, 0, 0));

		final JLabel configureSettings = new JLabel("Do you want Inventory Setups to ");
		final JLabel configureSettings2 = new JLabel("change your settings to use layouts?");
		final JButton configureSettingsButton = new JButton("Turn on Bank Tags.");
		configureSettingsButton.setForeground(Color.GREEN);
		configureSettingsButton.addActionListener(e ->
		{
			plugin.enableLayouts();
			panel.setHasDisplayedLayoutWarning(true);
			panel.showCorrectPanel();
		});
		configureSettings.setFont(PanelFonts.small());
		configureSettings2.setFont(PanelFonts.small());
		configureSettings.setHorizontalAlignment(JLabel.CENTER);
		configureSettings2.setHorizontalAlignment(JLabel.CENTER);

		final JPanel configureSettingsPanelInfo = new JPanel();
		configureSettingsPanelInfo.setLayout(new BorderLayout());
		configureSettingsPanelInfo.add(configureSettings, BorderLayout.NORTH);
		configureSettingsPanelInfo.add(configureSettings2, BorderLayout.CENTER);
		configureSettingsPanelInfo.add(configureSettingsButton, BorderLayout.SOUTH);

		final JLabel noConfigureSettings = new JLabel("Use Inventory Setups without layouts.");
		final JPanel noConfigureSettingsPanel = new JPanel(new BorderLayout());
		final JButton noConfigureSettingsButton = new JButton("Do not use layouts.");
		noConfigureSettingsButton.setForeground(Color.RED);
		noConfigureSettingsButton.addActionListener(e ->
		{
			panel.setHasDisplayedLayoutWarning(true);
			panel.showCorrectPanel();
		});
		noConfigureSettings.setFont(PanelFonts.small());
		noConfigureSettings.setHorizontalAlignment(JLabel.CENTER);
		noConfigureSettingsPanel.add(noConfigureSettings, BorderLayout.NORTH);
		noConfigureSettingsPanel.add(noConfigureSettingsButton, BorderLayout.SOUTH);


		final JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.add(textArea);
		contentPanel.add(Box.createRigidArea(new Dimension(0, 24)));
		contentPanel.add(configureSettingsPanelInfo);
		contentPanel.add(Box.createRigidArea(new Dimension(0, 16)));
		contentPanel.add(noConfigureSettingsPanel);

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(5, 10, 5, 10));
		add(contentPanel, BorderLayout.NORTH);
	}

}
