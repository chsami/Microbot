package net.runelite.client.plugins.microbot.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.ClientTheme;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PanelFonts;
import net.runelite.client.ui.FontManager;

class MicrobotTourCallout extends JWindow
{
	private static final int BODY_WIDTH = 460;
	private static final Font TITLE_FONT = PanelFonts.bold().deriveFont(32f);
	private static final Font BODY_FONT = FontManager.getDefaultFont().deriveFont(17f);
	private static final Font SMALL_FONT = PanelFonts.bold();
	private static final int ARC = 10;

	private final JLabel title = new JLabel();
	private final JLabel newChip = MicrobotCloudPanel.newChip();
	private final JLabel body = new JLabel();
	private final JLabel counter = new JLabel();
	private final JButton skip = new JButton("Skip tour");
	private final JButton neverAgain = new JButton("Don't show again");
	private final JButton back = new JButton("Back");
	private final JButton next = new JButton("Next");

	private final JPanel card;
	private final Runnable onMoved;
	private Point dragOffset;
	private boolean draggable;

	MicrobotTourCallout(Window owner, Runnable onSkip, Runnable onNeverAgain, Runnable onBack, Runnable onNext, Runnable onMoved)
	{
		super(owner);
		this.onMoved = onMoved;
		setType(Window.Type.UTILITY);
		setFocusableWindowState(false);
		setAutoRequestFocus(false);

		card = new JPanel(new BorderLayout(0, 14));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new CompoundBorder(new LineBorder(ColorScheme.BRAND_ORANGE, 2), new EmptyBorder(18, 22, 16, 22)));

		JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		header.setOpaque(false);
		title.setFont(TITLE_FONT);
		title.setForeground(ClientTheme.classicOr(Color.WHITE, ColorScheme.TEXT_COLOR));
		header.add(title);
		header.add(Box.createHorizontalStrut(10));
		newChip.setFont(SMALL_FONT);
		newChip.setBorder(new EmptyBorder(2, 8, 2, 8));
		header.add(newChip);
		card.add(header, BorderLayout.NORTH);

		body.setFont(BODY_FONT);
		body.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		body.setVerticalAlignment(JLabel.TOP);
		card.add(body, BorderLayout.CENTER);

		JPanel footer = new JPanel(new BorderLayout(12, 0));
		footer.setOpaque(false);
		counter.setFont(SMALL_FONT);
		counter.setForeground(ColorScheme.MEDIUM_GRAY_COLOR.brighter());
		footer.add(counter, BorderLayout.WEST);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		buttons.setOpaque(false);
		styleLink(skip);
		styleLink(neverAgain);
		style(back);
		style(next);
		next.setBackground(ColorScheme.BRAND_ORANGE);
		next.setForeground(ColorScheme.DARKER_GRAY_COLOR);
		skip.addActionListener(e -> onSkip.run());
		neverAgain.addActionListener(e -> onNeverAgain.run());
		back.addActionListener(e -> onBack.run());
		next.addActionListener(e -> onNext.run());
		buttons.add(neverAgain);
		buttons.add(skip);
		buttons.add(back);
		buttons.add(next);
		footer.add(buttons, BorderLayout.EAST);
		card.add(footer, BorderLayout.SOUTH);

		setContentPane(card);
		installDrag();

		GraphicsDevice device = owner.getGraphicsConfiguration().getDevice();
		if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT))
		{
			addComponentListener(new ComponentAdapter()
			{
				@Override
				public void componentResized(ComponentEvent e)
				{
					setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), ARC, ARC));
				}
			});
		}
	}

	void setStep(MicrobotTourStep step, int position, int total, boolean showBack, boolean last)
	{
		title.setText(step.getTitle());
		newChip.setVisible(step.isNew());
		body.setText("<html><body style='width:" + BODY_WIDTH + "px'>" + escape(step.getBody()) + "</body></html>");
		counter.setText("Step " + position + " of " + total);
		skip.setVisible(!last);
		skip.setText(position == 1 ? "Not now" : "Skip tour");
		neverAgain.setVisible(position == 1 && !last);
		draggable = step == MicrobotTourStep.WELCOME;
		card.setCursor(Cursor.getPredefinedCursor(draggable ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR));
		card.setToolTipText(draggable ? "Drag to move" : null);
		back.setVisible(showBack);
		next.setText(last ? "Done" : "Next");
		pack();
	}

	private void installDrag()
	{
		MouseAdapter drag = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				dragOffset = draggable ? e.getLocationOnScreen() : null;
				if (dragOffset != null)
				{
					dragOffset.translate(-getX(), -getY());
				}
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (dragOffset == null)
				{
					return;
				}
				Point screen = e.getLocationOnScreen();
				setLocation(screen.x - dragOffset.x, screen.y - dragOffset.y);
				onMoved.run();
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				dragOffset = null;
			}
		};
		for (Component component : new Component[]{card, title, body, counter})
		{
			component.addMouseListener(drag);
			component.addMouseMotionListener(drag);
		}
	}

	static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static void style(JButton button)
	{
		button.setFont(SMALL_FONT);
		button.setFocusable(false);
		button.setMargin(new Insets(6, 18, 6, 18));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	private static void styleLink(JButton button)
	{
		style(button);
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setContentAreaFilled(false);
		button.setBorderPainted(false);
		button.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
	}
}
