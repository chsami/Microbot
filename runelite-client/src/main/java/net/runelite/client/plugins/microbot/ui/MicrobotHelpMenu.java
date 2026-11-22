package net.runelite.client.plugins.microbot.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.plugins.microbot.diagnostics.BugReportTracker;
import net.runelite.client.plugins.microbot.diagnostics.DiagnosticReportCollector;
import net.runelite.client.plugins.microbot.recovery.StartupRecovery;
import net.runelite.client.ui.ClientTheme;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.LinkBrowser;

@Slf4j
@Singleton
public class MicrobotHelpMenu
{
	static final String BUG_REPORT_URL = "https://github.com/chsami/Microbot/issues/new?template=bug-issue.yml";
	private static final String SAFE_MODE_TITLE = "Safe mode on next start";
	private static final Color UPDATE_DOT = new Color(255, 215, 0);

	private final DiagnosticReportCollector diagnosticReportCollector;
	private final MicrobotBugReporter bugReporter;
	private final BugReportTracker bugReportTracker;
	private final ClientToolbar clientToolbar;
	private final Runnable onReportsChanged = () -> SwingUtilities.invokeLater(this::refreshButton);
	private NavigationButton button;
	private Runnable onSafeModeChanged = () -> { };
	private Runnable onShowTour;

	@Inject
	MicrobotHelpMenu(DiagnosticReportCollector diagnosticReportCollector, MicrobotBugReporter bugReporter, BugReportTracker bugReportTracker,
		ClientToolbar clientToolbar)
	{
		this.diagnosticReportCollector = diagnosticReportCollector;
		this.bugReporter = bugReporter;
		this.bugReportTracker = bugReportTracker;
		this.clientToolbar = clientToolbar;
	}

	public void install()
	{
		if (button != null)
		{
			return;
		}
		button = buildButton(bugReportTracker.unseenCount());
		clientToolbar.addNavigation(button);
		bugReportTracker.onChange(onReportsChanged);
	}

	public void uninstall()
	{
		bugReportTracker.removeListener(onReportsChanged);
		if (button != null)
		{
			clientToolbar.removeNavigation(button);
			button = null;
		}
	}

	private void refreshButton()
	{
		if (button == null)
		{
			return;
		}
		NavigationButton next = buildButton(bugReportTracker.unseenCount());
		if (next.getTooltip().equals(button.getTooltip()))
		{
			return;
		}
		clientToolbar.removeNavigation(button);
		button = next;
		clientToolbar.addNavigation(button);
	}

	private NavigationButton buildButton(int unseen)
	{
		return NavigationButton.builder()
			.tab(false)
			.icon(unseen > 0 ? withDot(bugIcon()) : bugIcon())
			.tooltip(tooltip(unseen))
			.priority(90)
			.onClick(this::showAtPointer)
			.build();
	}

	static String tooltip(int unseen)
	{
		if (unseen <= 0)
		{
			return "Report a problem";
		}
		return "Report a problem (" + unseen + (unseen == 1 ? " report update)" : " report updates)");
	}

	void onSafeModeChanged(Runnable listener)
	{
		onSafeModeChanged = listener;
	}

	NavigationButton getButton()
	{
		return button;
	}

	void onShowTour(Runnable listener)
	{
		onShowTour = listener;
	}

	private void showAtPointer()
	{
		PointerInfo pointer = MouseInfo.getPointerInfo();
		if (pointer == null)
		{
			return;
		}
		Point location = pointer.getLocation();
		for (Window window : Window.getWindows())
		{
			if (window.isShowing() && window.getBounds().contains(location))
			{
				SwingUtilities.convertPointFromScreen(location, window);
				JPopupMenu menu = buildMenu(window);
				Dimension size = menu.getPreferredSize();
				menu.show(window, location.x - size.width, location.y - size.height / 2);
				return;
			}
		}
	}

	private JPopupMenu buildMenu(Window owner)
	{
		JPopupMenu menu = new JPopupMenu();

		JMenuItem report = new JMenuItem(BugReportDialog.TITLE + "...");
		report.setToolTipText("Tell us what went wrong. We'll let you know when it's fixed.");
		report.addActionListener(e -> bugReporter.open(owner, null));
		menu.add(report);

		if (bugReporter.hasReports())
		{
			int unseen = bugReporter.unseenReports();
			JMenuItem reports = new JMenuItem(MyReportsDialog.TITLE + (unseen > 0 ? " (" + unseen + " new)" : ""));
			reports.setToolTipText("See what happened to the reports you sent.");
			reports.addActionListener(e -> bugReporter.openMyReports(owner));
			menu.add(reports);
		}

		menu.addSeparator();

		JMenuItem safeMode = new JMenuItem(StartupRecovery.nextStartSafeMode() ? "Cancel safe mode next start" : "Safe mode next start");
		safeMode.setToolTipText("Start Microbot once without external (Hub or sideloaded) plugins and the GPU plugin, to recover from a plugin that freezes or crashes the client. Plugin files and settings are kept.");
		safeMode.addActionListener(e -> toggleNextStartSafeMode(owner));
		menu.add(safeMode);

		Runnable showTour = onShowTour;
		if (showTour != null)
		{
			JMenuItem tour = new JMenuItem("Show the Microbot tour");
			tour.setToolTipText("Replay the short tour of the Microbot client.");
			tour.addActionListener(e -> showTour.run());
			menu.add(tour);
		}

		menu.addSeparator();

		JMenuItem discord = new JMenuItem("Ask for help on Discord");
		discord.addActionListener(e -> LinkBrowser.browse(RuneLiteProperties.getDiscordInvite()));
		menu.add(discord);

		JMenuItem diagnostics = new JMenuItem("Copy client details (for Discord)");
		diagnostics.setToolTipText("Copy your Microbot, plugin and system versions to paste in a Discord message. Leaves out account names, chat, passwords and file paths.");
		diagnostics.addActionListener(e -> copyDiagnostics(owner));
		menu.add(diagnostics);

		JMenuItem issue = new JMenuItem("Open a GitHub issue");
		issue.setToolTipText("For people with a GitHub account. Send a report first and mention its code in the issue.");
		issue.addActionListener(e -> LinkBrowser.browse(BUG_REPORT_URL));
		menu.add(issue);

		return menu;
	}

	private void copyDiagnostics(Window owner)
	{
		try
		{
			String report = diagnosticReportCollector.collectReport();
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(report), null);
			JOptionPane.showMessageDialog(owner,
				"Client details copied. Paste them into your Discord message.\nThey leave out account names, chat, passwords and file paths.",
				"Copy client details", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception ex)
		{
			log.warn("Could not copy diagnostics: {}", ex.getMessage());
			JOptionPane.showMessageDialog(owner, "Could not copy the client details.", "Copy client details", JOptionPane.WARNING_MESSAGE);
		}
	}

	private void toggleNextStartSafeMode(Window owner)
	{
		boolean enable = !StartupRecovery.nextStartSafeMode();
		if (!StartupRecovery.requestNextStartSafeMode(enable))
		{
			JOptionPane.showMessageDialog(owner, "Safe mode could not be scheduled on this system.", SAFE_MODE_TITLE, JOptionPane.WARNING_MESSAGE);
			return;
		}
		onSafeModeChanged.run();
		if (enable)
		{
			JOptionPane.showMessageDialog(owner,
				"The next start will use safe mode: external plugins and the GPU plugin stay off for that session only.\n"
					+ "Close Microbot and start it again. Your plugins and settings are kept, and the start after that is normal.",
				SAFE_MODE_TITLE, JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private static BufferedImage withDot(BufferedImage icon)
	{
		Graphics2D g = icon.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setComposite(AlphaComposite.Clear);
		g.fillOval(8, -2, 10, 10);
		g.setComposite(AlphaComposite.SrcOver);
		g.setColor(UPDATE_DOT);
		g.fillOval(10, 0, 6, 6);
		g.dispose();
		return icon;
	}

	static BufferedImage bugIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(220, 50, 50));
		g.setStroke(new BasicStroke(1.4f));
		g.drawLine(5, 1, 6, 3);
		g.drawLine(11, 1, 10, 3);
		g.drawLine(1, 6, 4, 8);
		g.drawLine(15, 6, 12, 8);
		g.drawLine(1, 10, 4, 10);
		g.drawLine(15, 10, 12, 10);
		g.drawLine(2, 15, 4, 12);
		g.drawLine(14, 15, 12, 12);
		g.fillOval(5, 2, 6, 5);
		g.fillOval(4, 5, 8, 10);
		g.setColor(ClientTheme.classicOr(new Color(40, 40, 40), ColorScheme.DARKER_GRAY_COLOR));
		g.drawLine(8, 8, 8, 14);
		g.dispose();
		return image;
	}
}
