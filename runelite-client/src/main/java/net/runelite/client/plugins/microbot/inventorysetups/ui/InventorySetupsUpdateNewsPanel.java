package net.runelite.client.plugins.microbot.inventorysetups.ui;

import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PanelFonts;
import net.runelite.client.util.LinkBrowser;

import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import static net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin.SUGGESTION_LINK;
import static net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin.TUTORIAL_LINK;

public class InventorySetupsUpdateNewsPanel extends JPanel
{

	private static final String DONATION_LINK = "https://www.buymeacoffee.com/dillydill123";

	InventorySetupsUpdateNewsPanel(MInventorySetupsPlugin plugin, InventorySetupsPluginPanel panel)
	{
		final JLabel welcomeText = new JLabel("Inventory Setups " + plugin.getCurrentVersionString());
		welcomeText.setFont(PanelFonts.bold());
		welcomeText.setHorizontalAlignment(JLabel.CENTER);

		final JPanel welcomePanel = new JPanel(new BorderLayout());
		welcomePanel.add(welcomeText, BorderLayout.NORTH);

		final JPanel latestUpdatePanelInfo = getLatestUpdateInfoPanel();

		final JLabel newUser = new JLabel("Are you a new user?");
		final JLabel newUser2 = new JLabel("For help and support, click here");
		final JButton linkToHelp = new JButton("View Guide");
		linkToHelp.addActionListener(e ->
		{
			LinkBrowser.browse(TUTORIAL_LINK);
		});
		newUser.setFont(PanelFonts.small());
		newUser2.setFont(PanelFonts.small());
		newUser.setHorizontalAlignment(JLabel.CENTER);
		newUser2.setHorizontalAlignment(JLabel.CENTER);

		final JPanel newUserPanelInfo = new JPanel();
		newUserPanelInfo.setLayout(new BorderLayout());
		newUserPanelInfo.add(newUser, BorderLayout.NORTH);
		newUserPanelInfo.add(newUser2, BorderLayout.CENTER);
		newUserPanelInfo.add(linkToHelp, BorderLayout.SOUTH);

		final JLabel suggestions = new JLabel("Have a suggestion? Found a bug?");
		final JLabel suggestions2 = new JLabel("Click here to create an issue");
		final JButton linkToSuggestion = new JButton("Make a Suggestion");
		linkToSuggestion.addActionListener(e ->
		{
			LinkBrowser.browse(SUGGESTION_LINK);
		});
		suggestions.setFont(PanelFonts.small());
		suggestions2.setFont(PanelFonts.small());
		suggestions.setHorizontalAlignment(JLabel.CENTER);
		suggestions2.setHorizontalAlignment(JLabel.CENTER);

		final JPanel suggestionPanelInfo = new JPanel();
		suggestionPanelInfo.setLayout(new BorderLayout());
		suggestionPanelInfo.add(suggestions, BorderLayout.NORTH);
		suggestionPanelInfo.add(suggestions2, BorderLayout.CENTER);
		suggestionPanelInfo.add(linkToSuggestion, BorderLayout.SOUTH);

		final JPanel closePanel = new JPanel(new BorderLayout());
		final JButton returnToSetups = new JButton("Return to Setups");
		returnToSetups.addActionListener(e ->
		{
			plugin.setSavedVersionString(plugin.getCurrentVersionString());
			panel.showCorrectPanel();
		});
		final JLabel clickButtonToLeave = new JLabel("Click here to hide this window");
		final JLabel clickButtonToLeave2 = new JLabel("until the next update");
		clickButtonToLeave.setFont(PanelFonts.small());
		clickButtonToLeave2.setFont(PanelFonts.small());
		clickButtonToLeave.setHorizontalAlignment(JLabel.CENTER);
		clickButtonToLeave2.setHorizontalAlignment(JLabel.CENTER);
		closePanel.add(clickButtonToLeave, BorderLayout.NORTH);
		closePanel.add(clickButtonToLeave2, BorderLayout.CENTER);
		closePanel.add(returnToSetups, BorderLayout.SOUTH);

		final JLabel donations = new JLabel("Want to make a donation?");
		final JLabel donations2 = new JLabel("Click here to buy me a coffee");
		final JButton linkToDonations = new JButton("Donate");
		linkToDonations.addActionListener(e ->
		{
			LinkBrowser.browse(DONATION_LINK);
		});
		donations.setFont(PanelFonts.small());
		donations2.setFont(PanelFonts.small());
		donations.setHorizontalAlignment(JLabel.CENTER);
		donations2.setHorizontalAlignment(JLabel.CENTER);

		final JPanel donationPanelInfo = new JPanel();
		donationPanelInfo.setLayout(new BorderLayout());
		donationPanelInfo.add(donations, BorderLayout.NORTH);
		donationPanelInfo.add(donations2, BorderLayout.CENTER);
		donationPanelInfo.add(linkToDonations, BorderLayout.SOUTH);

		final JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.add(welcomePanel);
		contentPanel.add(Box.createRigidArea(new Dimension(0, 8)));
		contentPanel.add(latestUpdatePanelInfo);
		contentPanel.add(Box.createRigidArea(new Dimension(0, 8)));
		contentPanel.add(closePanel);
		contentPanel.add(Box.createRigidArea(new Dimension(0, 8)));
		contentPanel.add(newUserPanelInfo);
		contentPanel.add(Box.createRigidArea(new Dimension(0, 8)));
		contentPanel.add(suggestionPanelInfo);
		contentPanel.add(Box.createRigidArea(new Dimension(0, 8)));
		contentPanel.add(donationPanelInfo);

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(5, 10, 5, 10));
		add(contentPanel, BorderLayout.NORTH);
	}


	private JPanel getLatestUpdateInfoPanel()
	{
		final JLabel patchNotesLabel = new JLabel("Patch Notes");
		patchNotesLabel.setFont(PanelFonts.small());
		patchNotesLabel.setHorizontalAlignment(JLabel.CENTER);

		final JPanel patchTitlePanel = new JPanel(new BorderLayout());
		patchTitlePanel.add(patchNotesLabel, BorderLayout.NORTH);

		String updateText =	"Updated Inventory Setups for the latest RuneLite update.\n\n" +
							"Fuzzy placeholders will now appear at the bottom of the layout when updating a setup or using a preset.\n\n" +
							"The '_invsetup_' tags are now hidden from the user in the bank.\n\n" +
							"Fixed manual bank filter config option.";

		JTextArea textArea = new JTextArea(2, 20);
		textArea.setText(updateText);
		textArea.setWrapStyleWord(true);
		textArea.setLineWrap(true);
		textArea.setOpaque(false);
		textArea.setEditable(false);
		textArea.setFocusable(false);
		textArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
		Font textAreaFont = PanelFonts.small();
		textAreaFont = textAreaFont.deriveFont(textAreaFont.getStyle(), (float)textAreaFont.getSize() - (float)0.1);
		textArea.setFont(textAreaFont);

		textArea.setBorder(new EmptyBorder(0, 0, 0, 0));

		final JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.add(patchTitlePanel);
		contentPanel.add(Box.createRigidArea(new Dimension(0, 3)));
		contentPanel.add(getJSeparator(ColorScheme.LIGHT_GRAY_COLOR));
		contentPanel.add(Box.createRigidArea(new Dimension(0, 3)));
		contentPanel.add(textArea);
		contentPanel.add(Box.createRigidArea(new Dimension(0, 3)));
		contentPanel.add(getJSeparator(ColorScheme.LIGHT_GRAY_COLOR));

		final JPanel updatePanel = new JPanel(new BorderLayout());
		updatePanel.add(contentPanel, BorderLayout.CENTER);

		// DO NOT TOUCH - For some reason this stops the panel from expanding
		updatePanel.getPreferredSize();

		return updatePanel;
	}

	private JSeparator getJSeparator(Color color)
	{
		JSeparator sep = new JSeparator();
		sep.setBackground(color);
		sep.setForeground(color);
		return sep;
	}

}
