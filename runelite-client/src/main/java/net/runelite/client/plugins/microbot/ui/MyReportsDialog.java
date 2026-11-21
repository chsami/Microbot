package net.runelite.client.plugins.microbot.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.plugins.microbot.diagnostics.BugReportStatus;
import net.runelite.client.plugins.microbot.diagnostics.BugReportTracker;
import net.runelite.client.plugins.microbot.diagnostics.TrackedBugReport;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

@Slf4j
class MyReportsDialog extends JDialog
{
	static final String TITLE = "My reports";
	static final int MAX_ANSWER_CHARS = 1000;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault());

	private final BugReportTracker tracker;
	private final JPanel list = new JPanel();
	private final JButton refresh = new JButton("Check now");
	private final JLabel checked = hint(" ");
	private final Runnable onChange = () -> SwingUtilities.invokeLater(this::reload);

	MyReportsDialog(Window owner, BugReportTracker tracker, BooleanSupplier linked, BooleanSupplier canLink, Runnable linkAccount)
	{
		super(owner, TITLE, ModalityType.MODELESS);
		this.tracker = tracker;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

		JPanel panel = new JPanel(new BorderLayout(0, 10));
		panel.setBorder(new EmptyBorder(12, 12, 12, 12));
		panel.add(hint("<html>Reports you sent from this computer. We'll let you know here when there's news.</html>"), BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		JPanel top = new JPanel(new BorderLayout());
		top.add(list, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(top);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setPreferredSize(new Dimension(440, 320));
		panel.add(scroll, BorderLayout.CENTER);

		JPanel bottomRow = new JPanel(new BorderLayout());
		if (!linked.getAsBoolean() && canLink.getAsBoolean())
		{
			JPanel link = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			JButton linkButton = new JButton("Link account");
			linkButton.setToolTipText("Link your Microbot account so reports you send from now on are saved to it.");
			linkButton.addActionListener(e -> linkAccount.run());
			link.add(linkButton);
			bottomRow.add(link, BorderLayout.WEST);
		}
		refresh.addActionListener(e -> checkNow());
		if (tracker.isDisabled())
		{
			refresh.setEnabled(false);
			checked.setText(tracker.disabledReason());
		}
		JButton close = new JButton("Close");
		close.addActionListener(e -> dispose());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.add(refresh);
		buttons.add(close);
		bottomRow.add(buttons, BorderLayout.EAST);
		JPanel bottom = new JPanel(new BorderLayout(0, 6));
		bottom.add(checked, BorderLayout.NORTH);
		bottom.add(bottomRow, BorderLayout.CENTER);
		panel.add(bottom, BorderLayout.SOUTH);

		setContentPane(panel);
		tracker.onChange(onChange);
		reload();
		pack();
		setLocationRelativeTo(owner);
	}

	@Override
	public void dispose()
	{
		tracker.removeListener(onChange);
		tracker.markSeen();
		super.dispose();
	}

	private void checkNow()
	{
		refresh.setEnabled(false);
		refresh.setText("Checking...");
		tracker.refresh().whenComplete((changed, error) -> SwingUtilities.invokeLater(() ->
		{
			refresh.setEnabled(true);
			refresh.setText("Check now");
			if (error != null)
			{
				checked.setText("Couldn't check right now. Try again later.");
			}
			else if (changed == 0)
			{
				checked.setText("Checked just now. No news yet.");
			}
			else
			{
				checked.setText("Checked just now. " + changed + (changed == 1 ? " report" : " reports") + " updated.");
			}
		}));
	}

	private void reload()
	{
		list.removeAll();
		List<TrackedBugReport> reports = tracker.reports();
		if (reports.isEmpty())
		{
			list.add(hint("You haven't sent any reports from this computer yet."));
		}
		for (TrackedBugReport report : reports)
		{
			list.add(row(report));
			list.add(Box.createVerticalStrut(8));
		}
		list.revalidate();
		list.repaint();
	}

	private JComponent row(TrackedBugReport report)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel title = new JLabel(report.getPlugin() + "  ·  " + DATE.format(Instant.ofEpochMilli(report.getCreatedAt())));
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		row.add(title);

		JLabel status = new JLabel(tracker.statusLabel(report) + (report.isSeen() ? "" : "  (new)"));
		status.setForeground(statusColor(report.getStatus()));
		row.add(status);

		if (report.getNote() != null)
		{
			row.add(Box.createVerticalStrut(4));
			row.add(new JLabel(html("From the Microbot team: " + report.getNote())));
		}
		if (BugReportTracker.asksQuestion(report))
		{
			row.add(Box.createVerticalStrut(6));
			row.add(answerBox(report));
		}
		if (report.getAnswer() != null)
		{
			row.add(Box.createVerticalStrut(4));
			row.add(hint(html("You answered: " + report.getAnswer())));
		}
		String askWhy = askWhyHint(report);
		if (askWhy != null)
		{
			row.add(Box.createVerticalStrut(4));
			row.add(hint(askWhy));
		}
		if (report.getSummary() != null && !report.getSummary().isEmpty())
		{
			row.add(Box.createVerticalStrut(4));
			row.add(hint(html("You wrote: " + report.getSummary())));
		}
		row.add(hint("Report code: " + report.getCode()));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JComponent answerBox(TrackedBugReport report)
	{
		JPanel box = new JPanel(new BorderLayout(6, 4));
		box.setOpaque(false);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		JTextField answer = new JTextField();
		answer.putClientProperty("JTextField.placeholderText", "Type your answer");
		JButton send = new JButton("Send answer");
		JLabel answerHint = hint("Your answer goes straight to the Microbot team.");
		Runnable submit = () ->
		{
			String text = answer.getText().trim();
			if (text.isEmpty())
			{
				answerHint.setText("Type your answer first.");
				return;
			}
			if (text.length() > MAX_ANSWER_CHARS)
			{
				answerHint.setText("Keep your answer under " + MAX_ANSWER_CHARS + " characters.");
				return;
			}
			send.setEnabled(false);
			answer.setEnabled(false);
			answerHint.setText("Sending...");
			tracker.answer(report.getCode(), text).whenComplete((ignored, error) -> SwingUtilities.invokeLater(() ->
			{
				if (error != null)
				{
					send.setEnabled(true);
					answer.setEnabled(true);
					answerHint.setText("Couldn't send your answer. Try again, or answer on Discord.");
				}
			}));
		};
		send.addActionListener(e -> submit.run());
		answer.addActionListener(e -> submit.run());
		box.add(answer, BorderLayout.CENTER);
		box.add(send, BorderLayout.EAST);

		JPanel below = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		below.setOpaque(false);
		below.add(answerHint);
		below.add(Box.createHorizontalStrut(8));
		JButton discord = new JButton("or answer on Discord");
		discord.setBorderPainted(false);
		discord.setContentAreaFilled(false);
		discord.setFont(FontManager.getRunescapeSmallFont());
		discord.addActionListener(e -> answerHint.setText(answerOnDiscord(report.getCode())
			? "Code copied. Paste it with your answer in the Microbot Discord."
			: "Mention " + report.getCode() + " with your answer."));
		below.add(discord);
		box.add(below, BorderLayout.SOUTH);
		box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
		return box;
	}

	private static boolean answerOnDiscord(String code)
	{
		boolean copied;
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
			copied = true;
		}
		catch (IllegalStateException e)
		{
			log.debug("Could not copy the report code", e);
			copied = false;
		}
		LinkBrowser.browse(RuneLiteProperties.getDiscordInvite());
		return copied;
	}

	static String askWhyHint(TrackedBugReport report)
	{
		boolean finishedWithoutReason = (BugReportStatus.WONT_FIX.equals(report.getStatus()) || BugReportStatus.CLOSED.equals(report.getStatus()))
			&& report.getNote() == null;
		return finishedWithoutReason ? "Want to know why? Ask on Discord and mention " + report.getCode() + "." : null;
	}

	static Color statusColor(String status)
	{
		switch (status)
		{
			case BugReportStatus.FIXED:
				return ColorScheme.PROGRESS_COMPLETE_COLOR;
			case BugReportStatus.WONT_FIX:
			case BugReportStatus.CLOSED:
				return ColorScheme.LIGHT_GRAY_COLOR;
			case BugReportStatus.INVESTIGATING:
				return ColorScheme.PROGRESS_INPROGRESS_COLOR;
			default:
				return ColorScheme.TEXT_COLOR;
		}
	}

	private static String html(String text)
	{
		return "<html><body style='width: 300px'>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</body></html>";
	}

	private static JLabel hint(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}
}
