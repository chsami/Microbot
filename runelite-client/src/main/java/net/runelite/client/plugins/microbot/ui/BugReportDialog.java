package net.runelite.client.plugins.microbot.ui;

import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.diagnostics.BugReportCollector;
import net.runelite.client.plugins.microbot.diagnostics.BugReportSection;
import net.runelite.client.plugins.microbot.diagnostics.BugReportSnapshot;
import net.runelite.client.plugins.microbot.diagnostics.BugReportTracker;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

class BugReportDialog extends JDialog
{
	static final String TITLE = "Report a problem";
	static final String CLIENT_LABEL = "Microbot";
	static final String NOT_CONNECTED = "<html>Can't reach the Microbot servers right now. Leave this window open: Send turns on by itself<br>"
		+ "when Microbot reconnects, which can take up to 10 minutes. Or click Copy report and paste it in Discord.</html>";
	static final String NO_UPDATES = "<html>'Disable telemetry' is on in the Microbot settings. You can still send this report,<br>"
		+ "but you won't get updates about it here. Turn that setting off to hear when it's fixed.</html>";
	static final String FOLLOW_UP = "<html>We'll let you know in Microbot when there's news, like a fix or a question for you.<br>"
		+ "You can follow it under My reports in the red bug button in the sidebar.</html>";
	static final String FOLLOW_UP_OFF = "<html>We can't send you updates while 'Disable telemetry' is on in the Microbot settings.<br>"
		+ "Turn it off to hear about a fix, or mention your report code on Discord.</html>";
	static final String TELEMETRY_OFF = "<html>Microbot was started with the --disable-telemetry option, so reports can't be sent from here.<br>"
		+ "Click Copy report and paste it in Discord. To send reports and get fix updates,<br>start Microbot without that option.</html>";
	static final String LINK_HINT = "<html>Optional: link your Microbot account<br>to keep your reports with it.</html>";
	private static final String FORM = "form";
	private static final String DONE = "done";
	private static final int PREVIEW_THUMBNAIL_WIDTH = 320;
	private static final int CONNECTION_CHECK_MILLIS = 3000;

	private final BugReportCollector collector;
	private final BugReportTracker tracker;
	private final BooleanSupplier linked;
	private final BooleanSupplier canLink;
	private final Runnable linkAccount;
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JLabel notice = new JLabel(NOT_CONNECTED);
	private final JLabel linkedHint = hint("This report will be linked to your Microbot account.");
	private final JButton openDiscord = new JButton("Open Discord");
	private final JComboBox<PluginChoice> pluginBox = new JComboBox<>();
	private final JLabel pluginHint = hint("Pick the plugin that went wrong so we get its settings and errors.");
	private final JTextArea description = new JTextArea(5, 34);
	private final Map<BugReportSection, JCheckBox> sections = new EnumMap<>(BugReportSection.class);
	private final JLabel status = new JLabel(" ");
	private final JButton preview = new JButton("See what's sent");
	private final JButton copyReport = new JButton("Copy report");
	private final JButton send = new JButton("Send");
	private final JTextField code = new JTextField();
	private final JButton copyCode = new JButton("Copy");
	private final JPanel linkRow = new JPanel();
	private final JLabel followUp = new JLabel(FOLLOW_UP);
	private JPanel doneCard;
	private final Timer connectionCheck;
	private BugReportSnapshot snapshot;
	private boolean sending;
	private boolean failed;
	private boolean done;

	BugReportDialog(Window owner, BugReportCollector collector, BugReportTracker tracker, BooleanSupplier linked, BooleanSupplier canLink,
		Runnable linkAccount, List<Plugin> plugins, Plugin selected)
	{
		super(owner, TITLE, ModalityType.MODELESS);
		this.collector = collector;
		this.tracker = tracker;
		this.linked = linked;
		this.canLink = canLink;
		this.linkAccount = linkAccount;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

		content.add(buildForm(plugins, selected), FORM);
		doneCard = buildDone();
		content.add(doneCard, DONE);
		setContentPane(content);
		pack();
		setMinimumSize(getSize());
		setLocationRelativeTo(owner);

		connectionCheck = new Timer(CONNECTION_CHECK_MILLIS, e -> updateControls());
		if (!collector.isTelemetryDisabled())
		{
			connectionCheck.start();
		}
		showStatus("Collecting client details...", false);
		collector.capture().whenComplete((result, error) -> SwingUtilities.invokeLater(() -> onCaptured(result)));
	}

	@Override
	public void dispose()
	{
		connectionCheck.stop();
		snapshot = null;
		super.dispose();
	}

	boolean isDone()
	{
		return done;
	}

	void select(Plugin plugin)
	{
		if (plugin == null)
		{
			return;
		}
		for (int i = 0; i < pluginBox.getItemCount(); i++)
		{
			if (pluginBox.getItemAt(i).plugin == plugin)
			{
				pluginBox.setSelectedIndex(i);
				return;
			}
		}
		PluginChoice choice = new PluginChoice(plugin, displayName(plugin));
		pluginBox.addItem(choice);
		pluginBox.setSelectedItem(choice);
	}

	private JPanel buildForm(List<Plugin> plugins, Plugin selected)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(new EmptyBorder(12, 12, 12, 12));

		notice.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
		notice.setBorder(new EmptyBorder(0, 0, 10, 0));
		panel.add(left(notice));

		pluginBox.addItem(new PluginChoice(null, "Client / not sure"));
		choices(plugins, selected).forEach(pluginBox::addItem);
		select(selected);
		pluginBox.addActionListener(e -> updateControls());
		panel.add(row(label("Plugin"), pluginBox));
		panel.add(left(pluginHint));
		panel.add(Box.createVerticalStrut(10));

		panel.add(left(label("What went wrong?")));
		panel.add(left(hint("What were you doing, what did you expect, and what happened instead?")));
		panel.add(Box.createVerticalStrut(4));
		description.setLineWrap(true);
		description.setWrapStyleWord(true);
		description.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateControls();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateControls();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateControls();
			}
		});
		JScrollPane descriptionScroll = new JScrollPane(description);
		descriptionScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(descriptionScroll);
		panel.add(Box.createVerticalStrut(10));

		panel.add(left(label("Include")));
		JCheckBox versions = new JCheckBox("Microbot version and system details (always)", true);
		versions.setToolTipText("Microbot, RuneLite, Java and OS versions, your installed plugins and walker settings. No names, chat or passwords.");
		versions.setEnabled(false);
		panel.add(left(versions));
		addSection(panel, BugReportSection.LOG, "Recent log and errors", true,
			"The last part of the client log and the most recent errors.");
		addSection(panel, BugReportSection.PLUGIN_SETTINGS, "Plugin settings", true,
			"The settings of the chosen plugin. Secret settings are left out.");
		addSection(panel, BugReportSection.GAME_STATE, "Game state (location, inventory, gear)", true,
			"World, location, levels, inventory, equipment and which scripts are running.");
		addSection(panel, BugReportSection.RECORDER, "Recent activity (movement, clicks, game messages)", true,
			"<html>A short record of the last 20 minutes at most. It stays on your computer and is only sent with a report.<br>"
				+ "Where you walked, animations, what you attacked, HP/prayer/run, items gained or lost (as item numbers),<br>"
				+ "game messages, what you clicked, windows opened, game settings, the script's status and anything it printed.<br>"
				+ "Players you click or target show as \"player\" and other players' chat is not included,<br>"
				+ "but anything a script prints is included as it is. Error output is left out when Recent log and errors is unticked.</html>");
		addSection(panel, BugReportSection.SCREENSHOT, "Screenshot of the game (shows your chat and name)", false,
			"Your game screen as it was when you opened this window.");
		panel.add(Box.createVerticalStrut(8));
		panel.add(left(hint("<html>Your password and login details are never sent, and your name is removed from the text.<br>"
			+ "A screenshot shows whatever is on your screen.</html>")));
		panel.add(left(linkedHint));
		panel.add(Box.createVerticalStrut(8));

		status.setFont(FontManager.getRunescapeSmallFont());
		panel.add(left(status));
		panel.add(Box.createVerticalStrut(8));

		preview.addActionListener(e -> showPreview());
		copyReport.addActionListener(e -> copyReport());
		copyReport.setToolTipText("Copy the report text so you can paste it in Discord.");
		send.addActionListener(e -> send());
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(e -> dispose());
		JPanel buttons = new JPanel(new BorderLayout());
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttons.add(preview, BorderLayout.WEST);
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		right.add(cancel);
		openDiscord.setVisible(false);
		openDiscord.addActionListener(e -> LinkBrowser.browse(RuneLiteProperties.getDiscordInvite()));
		right.add(openDiscord);
		right.add(copyReport);
		right.add(send);
		buttons.add(right, BorderLayout.EAST);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttons.getPreferredSize().height));
		panel.add(buttons);

		updateControls();
		return panel;
	}

	private JPanel buildDone()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(new EmptyBorder(16, 16, 16, 16));

		JLabel thanks = label("Thanks, we've got your report");
		thanks.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		panel.add(left(thanks));
		panel.add(Box.createVerticalStrut(8));
		panel.add(left(followUp));
		panel.add(Box.createVerticalStrut(12));

		panel.add(left(hint("Report code")));
		code.setEditable(false);
		code.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
		code.setHorizontalAlignment(JTextField.CENTER);
		copyCode.addActionListener(e -> copyCode());
		JPanel codeRow = new JPanel(new BorderLayout(6, 0));
		codeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		codeRow.add(code, BorderLayout.CENTER);
		codeRow.add(copyCode, BorderLayout.EAST);
		codeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, code.getPreferredSize().height + 12));
		panel.add(codeRow);
		panel.add(Box.createVerticalStrut(4));
		panel.add(left(hint("Want to talk about it? Mention this code on Discord.")));
		panel.add(Box.createVerticalStrut(12));

		linkRow.setLayout(new BorderLayout(8, 0));
		linkRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		linkRow.add(hint(LINK_HINT), BorderLayout.CENTER);
		JButton link = new JButton("Link account");
		link.addActionListener(e -> linkAccount.run());
		linkRow.add(link, BorderLayout.EAST);
		linkRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, linkRow.getPreferredSize().height));
		panel.add(linkRow);
		panel.add(Box.createVerticalStrut(14));

		JButton discord = new JButton("Open Discord");
		discord.addActionListener(e -> LinkBrowser.browse(RuneLiteProperties.getDiscordInvite()));
		JButton close = new JButton("Close");
		close.addActionListener(e -> dispose());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttons.add(discord);
		buttons.add(close);
		panel.add(buttons);

		JPanel centered = new JPanel(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.anchor = GridBagConstraints.NORTH;
		constraints.weightx = 1;
		constraints.weighty = 1;
		centered.add(panel, constraints);
		return centered;
	}

	private void addSection(JPanel panel, BugReportSection section, String text, boolean selected, String tooltip)
	{
		JCheckBox box = new JCheckBox(text, selected);
		box.setToolTipText(tooltip);
		sections.put(section, box);
		panel.add(left(box));
	}

	private void onCaptured(BugReportSnapshot result)
	{
		if (!isDisplayable())
		{
			return;
		}
		snapshot = result;
		if (snapshot == null)
		{
			showStatus("Could not collect client details. Close this window and try again.", true);
		}
		else if (!failed)
		{
			showStatus(" ", false);
		}
		updateControls();
	}

	private void updateControls()
	{
		boolean connected = collector.canSubmit();
		boolean ready = snapshot != null && !sending;
		Plugin plugin = selectedPlugin();
		boolean described = !description.getText().trim().isEmpty();

		String noticeText = collector.isTelemetryDisabled() ? TELEMETRY_OFF
			: !connected ? NOT_CONNECTED
			: tracker.isDisabled() ? NO_UPDATES
			: null;
		notice.setVisible(noticeText != null);
		if (noticeText != null && !noticeText.equals(notice.getText()))
		{
			notice.setText(noticeText);
		}
		linkedHint.setVisible(linked.getAsBoolean());
		pluginHint.setVisible(plugin == null);
		JCheckBox settings = sections.get(BugReportSection.PLUGIN_SETTINGS);
		if (settings != null)
		{
			settings.setEnabled(plugin != null && !sending);
			settings.setToolTipText(plugin == null ? "Pick a plugin above to include its settings." : "The settings of the chosen plugin. Secret settings are left out.");
		}
		JCheckBox screenshot = sections.get(BugReportSection.SCREENSHOT);
		if (screenshot != null)
		{
			boolean available = snapshot != null && snapshot.getScreenshot() != null;
			screenshot.setEnabled(available && !sending);
			if (snapshot != null && !available)
			{
				screenshot.setSelected(false);
				screenshot.setToolTipText("No screenshot could be taken.");
			}
		}
		pluginBox.setEnabled(!sending);
		description.setEnabled(!sending);
		preview.setEnabled(ready);
		copyReport.setVisible(!connected || failed);
		copyReport.setEnabled(ready && described);
		send.setVisible(!collector.isTelemetryDisabled());
		send.setEnabled(ready && connected && described);
		send.setToolTipText(described ? null : "Describe what went wrong first.");
	}

	private Plugin selectedPlugin()
	{
		PluginChoice choice = (PluginChoice) pluginBox.getSelectedItem();
		return choice == null ? null : choice.plugin;
	}

	private String pluginLabel()
	{
		PluginChoice choice = (PluginChoice) pluginBox.getSelectedItem();
		return choice == null || choice.plugin == null ? CLIENT_LABEL : choice.label;
	}

	private Set<BugReportSection> included()
	{
		Set<BugReportSection> included = EnumSet.noneOf(BugReportSection.class);
		sections.forEach((section, box) ->
		{
			if (box.isSelected() && box.isEnabled())
			{
				included.add(section);
			}
		});
		return included;
	}

	private JsonObject payload()
	{
		return collector.payload(snapshot, selectedPlugin(), description.getText(), included());
	}

	private void showPreview()
	{
		JsonObject payload = payload();
		JTextArea text = new JTextArea(collector.preview(payload, pluginLabel(), false));
		text.setEditable(false);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		text.setCaretPosition(0);
		JScrollPane scroll = new JScrollPane(text);
		scroll.setPreferredSize(new Dimension(640, 380));

		JPanel top = new JPanel(new BorderLayout(0, 8));
		top.add(label(linked.getAsBoolean()
			? "This is everything in your report. It will also be linked to your Microbot account."
			: "This is everything in your report."), BorderLayout.NORTH);
		ImageIcon thumbnail = payload.has("screenshot") ? thumbnail(snapshot.getScreenshot()) : null;
		if (thumbnail != null)
		{
			top.add(new JLabel(thumbnail), BorderLayout.CENTER);
		}
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.add(top, BorderLayout.NORTH);
		panel.add(scroll, BorderLayout.CENTER);
		Object[] back = {"Back"};
		JOptionPane.showOptionDialog(this, panel, "What will be sent", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
			null, back, back[0]);
	}

	private void copyReport()
	{
		String text = TITLE + " (" + pluginLabel() + ")\n\n" + collector.preview(payload(), pluginLabel(), true);
		if (copy(text))
		{
			showStatus("Report copied. Paste it in the Microbot Discord.", false);
			openDiscord.setVisible(true);
		}
		else
		{
			showStatus("Could not copy the report. Try again.", true);
		}
	}

	private void send()
	{
		JsonObject payload = payload();
		String plugin = pluginLabel();
		Plugin selected = selectedPlugin();
		String internalName = selected == null ? null : selected.getClass().getSimpleName();
		boolean withScreenshot = payload.has("screenshot");
		String text = description.getText();
		sending = true;
		showStatus("Sending...", false);
		updateControls();
		collector.submit(payload).whenComplete((result, error) -> SwingUtilities.invokeLater(() ->
		{
			sending = false;
			if (error != null)
			{
				failed = true;
				showStatus(failureMessage(error, withScreenshot), true);
				updateControls();
				return;
			}
			tracker.track(result, internalName, plugin, text);
			showDone(result.getCode());
		}));
	}

	private void showDone(String reportCode)
	{
		done = true;
		connectionCheck.stop();
		code.setText(reportCode);
		linkRow.setVisible(!linked.getAsBoolean() && canLink.getAsBoolean());
		followUp.setText(tracker.isDisabled() ? FOLLOW_UP_OFF : FOLLOW_UP);
		cards.show(content, DONE);
		setMinimumSize(null);
		Insets insets = getInsets();
		setSize(getWidth(), doneCard.getPreferredSize().height + insets.top + insets.bottom);
		validate();
	}

	private void copyCode()
	{
		if (copy(code.getText()))
		{
			copyCode.setText("Copied");
		}
		else
		{
			code.requestFocusInWindow();
			code.selectAll();
			copyCode.setText("Select it and copy");
		}
	}

	private void showStatus(String text, boolean error)
	{
		status.setText(text);
		status.setForeground(error ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
	}

	private static boolean copy(String text)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			return true;
		}
		catch (IllegalStateException e)
		{
			return false;
		}
	}

	static String failureMessage(Throwable error, boolean withScreenshot)
	{
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		MicrobotApi.BugReportRejectedException rejected = cause instanceof MicrobotApi.BugReportRejectedException
			? (MicrobotApi.BugReportRejectedException) cause : null;
		int code = rejected == null ? -1 : rejected.getStatus();
		switch (code)
		{
			case 0:
				return "Couldn't reach Microbot. Check your internet and try again.";
			case 403:
				return "<html>Microbot isn't ready to take reports yet.<br>Wait a minute and try again.</html>";
			case 413:
				return "<html>The report is too large. Untick 'Screenshot of the game' or<br>'Recent log and errors' and try again.</html>";
			case 429:
				return "daily-limit".equals(rejected.getReason())
					? "<html>You've sent the most reports allowed today.<br>Click Copy report and paste it in Discord instead.</html>"
					: "<html>You've sent several reports in a short time.<br>Wait a few minutes and try again.</html>";
			case 400:
				return withScreenshot
					? "This report couldn't be accepted. Untick 'Screenshot of the game' and try again."
					: "This report couldn't be accepted. Click Copy report and paste it in Discord.";
			default:
				return "The report couldn't be sent" + (code > 0 ? " (error " + code + ")" : "") + ". Try again later.";
		}
	}

	static List<PluginChoice> choices(List<Plugin> plugins, Plugin selected)
	{
		List<Plugin> all = new ArrayList<>(plugins);
		if (selected != null && !all.contains(selected))
		{
			all.add(selected);
		}
		List<PluginChoice> choices = new ArrayList<>();
		for (Plugin plugin : all)
		{
			choices.add(new PluginChoice(plugin, displayName(plugin)));
		}
		choices.sort(Comparator.comparing(choice -> choice.label, String.CASE_INSENSITIVE_ORDER));
		return choices;
	}

	static String displayName(Plugin plugin)
	{
		PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
		String name = descriptor == null ? plugin.getClass().getSimpleName() : descriptor.name();
		return name.replaceAll("<[^>]*>", "").trim();
	}

	private static ImageIcon thumbnail(byte[] image)
	{
		try
		{
			BufferedImage frame = ImageIO.read(new ByteArrayInputStream(image));
			if (frame == null)
			{
				return null;
			}
			int height = Math.max(1, frame.getHeight() * PREVIEW_THUMBNAIL_WIDTH / frame.getWidth());
			return new ImageIcon(frame.getScaledInstance(PREVIEW_THUMBNAIL_WIDTH, height, Image.SCALE_SMOOTH));
		}
		catch (IOException e)
		{
			return null;
		}
	}

	private static JLabel label(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		return label;
	}

	private static JLabel hint(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	private static JPanel row(JComponent label, JComponent field)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(label, BorderLayout.WEST);
		row.add(field, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
		return row;
	}

	private static JComponent left(JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		return component;
	}

	static final class PluginChoice
	{
		final Plugin plugin;
		final String label;

		PluginChoice(Plugin plugin, String label)
		{
			this.plugin = plugin;
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
