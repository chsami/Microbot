package net.runelite.client.plugins.microbot.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.plugins.microbot.alerts.BotAlertStatus;
import net.runelite.client.plugins.microbot.alerts.DeviceLinkFlow;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PanelFonts;
import net.runelite.client.util.LinkBrowser;

@Singleton
public class MicrobotCloudPanel extends JPanel
{
	private static final String DASHBOARD = "https://dashboard.microbot.cloud";
	private static final Icon EXPAND_ICON = new ArrowIcon(false);
	private static final Icon RETRACT_ICON = new ArrowIcon(true);

	private final DeviceLinkFlow deviceLinkFlow;
	private final JLabel title = new JLabel("Microbot Cloud");
	private final JLabel newBadge = newChip();
	private final JPanel body = new JPanel(new GridLayout(0, 1, 0, 4));
	private final JLabel alertStatus = new JLabel();
	private final JButton alertAction = new JButton();
	private final Timer refresher = new Timer(1000, e -> refresh());
	private BotAlertStatus shown;
	private Runnable onUsed = () -> { };

	@Inject
	MicrobotCloudPanel(DeviceLinkFlow deviceLinkFlow)
	{
		this.deviceLinkFlow = deviceLinkFlow;

		setLayout(new BorderLayout());
		setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR), new EmptyBorder(4, 10, 4, 10)));

		title.setForeground(ColorScheme.BRAND_ORANGE);
		title.setFont(PanelFonts.bold());
		title.setIconTextGap(6);
		title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		title.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				applyCollapsed(body.isVisible());
				onUsed.run();
			}
		});
		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.add(title, BorderLayout.CENTER);
		newBadge.setVisible(false);
		header.add(newBadge, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		body.setBorder(new EmptyBorder(4, 0, 0, 0));
		JLabel alertsName = new JLabel("Bot alerts");
		alertsName.setFont(PanelFonts.small());
		alertsName.setToolTipText("<html>Get a Discord or email alert when this client stops responding,<br>a script stops on an error or the game logs out</html>");
		JPanel alertsRow = new JPanel(new BorderLayout(6, 0));
		alertsRow.add(alertsName, BorderLayout.WEST);
		alertStatus.setFont(PanelFonts.small());
		alertStatus.setHorizontalAlignment(JLabel.RIGHT);
		alertsRow.add(alertStatus, BorderLayout.CENTER);
		body.add(alertsRow);

		alertAction.setFont(PanelFonts.small());
		alertAction.setFocusable(false);
		alertAction.addActionListener(e ->
		{
			deviceLinkFlow.start();
			refresh();
			onUsed.run();
		});
		body.add(alertAction);

		JButton dashboard = new JButton("Open dashboard");
		dashboard.setFont(PanelFonts.small());
		dashboard.setFocusable(false);
		dashboard.setToolTipText(DASHBOARD);
		dashboard.addActionListener(e ->
		{
			LinkBrowser.browse(DASHBOARD);
			onUsed.run();
		});
		body.add(dashboard);
		add(body, BorderLayout.CENTER);

		applyCollapsed(false);
		refresh();
	}

	public void setActive(boolean active)
	{
		if (active)
		{
			refresh();
			refresher.start();
		}
		else
		{
			refresher.stop();
		}
	}

	static JLabel newChip()
	{
		JLabel chip = new JLabel("NEW");
		chip.setOpaque(true);
		chip.setBackground(ColorScheme.BRAND_ORANGE);
		chip.setForeground(ColorScheme.DARKER_GRAY_COLOR);
		chip.setFont(PanelFonts.small());
		chip.setBorder(new EmptyBorder(0, 4, 0, 4));
		chip.setToolTipText("New in Microbot");
		return chip;
	}

	void setNewBadgeVisible(boolean visible)
	{
		if (newBadge.isVisible() != visible)
		{
			newBadge.setVisible(visible);
			revalidate();
			repaint();
		}
	}

	boolean isNewBadgeVisible()
	{
		return newBadge.isVisible();
	}

	void onUsed(Runnable listener)
	{
		onUsed = listener == null ? () -> { } : listener;
	}

	void expand()
	{
		applyCollapsed(false);
	}

	private void applyCollapsed(boolean collapsed)
	{
		body.setVisible(!collapsed);
		title.setIcon(collapsed ? EXPAND_ICON : RETRACT_ICON);
		title.setToolTipText(collapsed ? "Show Microbot Cloud" : "Minimize Microbot Cloud");
		revalidate();
	}

	private void refresh()
	{
		BotAlertStatus status = deviceLinkFlow.status();
		if (status.isOn() && newBadge.isVisible())
		{
			setNewBadgeVisible(false);
			onUsed.run();
		}
		if (status == shown)
		{
			return;
		}
		shown = status;
		alertStatus.setText(status.getText());
		alertStatus.setForeground(status.isOn() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
		alertAction.setVisible(status.getAction() != null);
		if (status.getAction() != null)
		{
			alertAction.setText(status.getAction());
		}
	}

	private static class ArrowIcon implements Icon
	{
		private static final int SIZE = 8;
		private final boolean down;

		ArrowIcon(boolean down)
		{
			this.down = down;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			if (down)
			{
				g2.fillPolygon(new int[]{x, x + SIZE, x + SIZE / 2}, new int[]{y + 2, y + 2, y + SIZE - 1}, 3);
			}
			else
			{
				g2.fillPolygon(new int[]{x + 1, x + SIZE - 2, x + 1}, new int[]{y, y + SIZE / 2, y + SIZE}, 3);
			}
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}
}
