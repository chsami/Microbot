/*
 * Copyright (c) 2024 Microbot Contributors
 * All rights reserved.
 */
package net.runelite.client.ui;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.MatteBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Element;
import net.runelite.api.Constants;

final class LogConsolePanel extends JPanel
{
	private static final int MAX_CHARACTERS = 100_000;
	// Trimming shifts the view, so it is deferred while the user is scrolled up - but not forever.
	private static final int MAX_CHARACTERS_SCROLLED_UP = 400_000;
	private static final int PREFERRED_HEIGHT = 160;
	private static final int BOTTOM_TOLERANCE_PX = 4;
	// enough passes for a starved layout to catch up, few enough to give up on an off-screen console
	private static final int MAX_SNAP_ATTEMPTS = 20;

	private final JTextArea textArea = new JTextArea();
	private final JScrollPane scrollPane = new JScrollPane(textArea);

	private final StringBuilder pending = new StringBuilder();
	private boolean flushScheduled;

	private boolean followTail = true;
	private boolean snapQueued;
	private int snapAttempts;
	private int tailCaretDot = -1;

	LogConsolePanel()
	{
		super(new BorderLayout());
		setPreferredSize(new Dimension(Constants.GAME_FIXED_SIZE.width, PREFERRED_HEIGHT));
		setBorder(new MatteBorder(1, 0, 0, 0, ColorScheme.DARKER_GRAY_COLOR));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		textArea.setEditable(false);
		textArea.setLineWrap(false);
		textArea.setWrapStyleWord(false);
		textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		textArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
		textArea.setForeground(ColorScheme.TEXT_COLOR);
		textArea.setBorder(null);

		DefaultCaret caret = new DefaultCaret()
		{
			@Override
			protected void adjustVisibility(Rectangle nloc)
			{
				if (getDot() != tailCaretDot || getMark() != tailCaretDot)
				{
					super.adjustVisibility(nloc);
				}
			}
		};
		caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		textArea.setCaret(caret);

		scrollPane.setBorder(null);
		add(scrollPane, BorderLayout.CENTER);

		// Following is decided by what the user does, never by measuring the view: under a flood the
		// scroll position trails the document, which reads as "the user scrolled up".
		scrollPane.addMouseWheelListener(ev ->
		{
			if (ev.getPreciseWheelRotation() < 0)
			{
				followTail = false;
			}
			reconsiderFollowingTail();
		});

		textArea.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent ev)
			{
				followTail = false;
				SwingUtilities.invokeLater(LogConsolePanel.this::reconsiderFollowingTail);
			}
		});
		textArea.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent ev)
			{
				followTail = false;
			}

			@Override
			public void mouseReleased(MouseEvent ev)
			{
				reconsiderFollowingTail();
			}
		});
		textArea.addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseDragged(MouseEvent ev)
			{
				followTail = false;
			}
		});

		JScrollBar vertical = scrollPane.getVerticalScrollBar();
		vertical.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent ev)
			{
				followTail = false;
			}

			@Override
			public void mouseReleased(MouseEvent ev)
			{
				reconsiderFollowingTail();
			}
		});
		vertical.addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseDragged(MouseEvent ev)
			{
				followTail = false;
			}
		});

		textArea.setComponentPopupMenu(buildContextMenu());

		// opening the console lands on the newest line, wherever the view was left
		addHierarchyListener(ev ->
		{
			if ((ev.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing())
			{
				scrollToBottom();
			}
		});
	}

	void scrollToBottom()
	{
		followTail = true;
		snapAttempts = 0;
		queueSnapToBottom();
	}

	// Snapping waits for the next pass: until the insert's revalidation has run the viewport still
	// reports the pre-insert view size and would snap a line short of the newest one.
	private void queueSnapToBottom()
	{
		if (snapQueued)
		{
			return;
		}

		snapQueued = true;
		SwingUtilities.invokeLater(() ->
		{
			snapQueued = false;
			if (followTail)
			{
				snapToBottom();
			}
		});
	}

	private void snapToBottom()
	{
		int end = textArea.getDocument().getLength();
		moveCaretToTail();

		Rectangle2D newestLine;
		try
		{
			// the viewport's laid-out size trails the document, so ask the text view, which reads the
			// newest line's position from the document itself
			newestLine = textArea.modelToView2D(end);
		}
		catch (BadLocationException | IllegalArgumentException ex)
		{
			return;
		}

		if (newestLine == null)
		{
			return;
		}

		textArea.scrollRectToVisible(newestLine.getBounds());

		JViewport viewport = scrollPane.getViewport();

		// The scroll is clamped to the size the text area was last laid out at, so under load it can
		// land short, and nothing else would correct that - keep trying while the layout catches up.
		boolean reachedNewestLine = newestLine.getMaxY()
			<= viewport.getViewPosition().y + viewport.getExtentSize().height + 1;
		if (reachedNewestLine || ++snapAttempts > MAX_SNAP_ATTEMPTS)
		{
			snapAttempts = 0;
			return;
		}

		queueSnapToBottom();
	}

	// One batch per event queue pass. A line per pass is what breaks the console under load: each
	// insert schedules its own layout, and a busy script logs faster than the queue can run them.
	void append(String text)
	{
		if (text == null || text.isEmpty())
		{
			return;
		}

		synchronized (pending)
		{
			pending.append(text, 0, text.length());

			if (pending.length() > 2 * MAX_CHARACTERS_SCROLLED_UP)
			{
				// the oldest buffered lines would be trimmed out of the document on arrival anyway
				pending.delete(0, lineStartAtOrAfter(pending, pending.length() - MAX_CHARACTERS_SCROLLED_UP));
			}

			if (flushScheduled)
			{
				return;
			}
			flushScheduled = true;
		}

		SwingUtilities.invokeLater(this::flushPending);
	}

	static int lineStartAtOrAfter(CharSequence text, int from)
	{
		if (from <= 0)
		{
			return 0;
		}
		if (text.charAt(from - 1) == '\n')
		{
			return from;
		}
		for (int i = from; i < text.length(); i++)
		{
			if (text.charAt(i) == '\n')
			{
				return i + 1;
			}
		}
		return from;
	}

	private void flushPending()
	{
		String text;
		synchronized (pending)
		{
			flushScheduled = false;
			if (pending.length() == 0)
			{
				return;
			}

			int cap = followTail ? MAX_CHARACTERS : MAX_CHARACTERS_SCROLLED_UP;
			text = pending.substring(lineStartAtOrAfter(pending, pending.length() - cap));
			pending.setLength(0);
		}

		appendOnEdt(text.replace("\r", ""));
	}

	private JPopupMenu buildContextMenu()
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem copy = new JMenuItem("Copy");
		copy.addActionListener(ev -> textArea.copy());
		JMenuItem copyAll = new JMenuItem("Copy all");
		copyAll.addActionListener(ev -> copyToClipboard(textArea.getText()));
		JMenuItem selectAll = new JMenuItem("Select all");
		selectAll.addActionListener(ev ->
		{
			followTail = false;
			textArea.requestFocusInWindow();
			textArea.selectAll();
		});
		JMenuItem clear = new JMenuItem("Clear");
		clear.addActionListener(ev -> clear());
		menu.add(copy);
		menu.add(copyAll);
		menu.add(selectAll);
		menu.addSeparator();
		menu.add(clear);
		menu.addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent ev)
			{
				copy.setEnabled(textArea.getSelectionStart() != textArea.getSelectionEnd());
				boolean hasText = textArea.getDocument().getLength() > 0;
				copyAll.setEnabled(hasText);
				selectAll.setEnabled(hasText);
				clear.setEnabled(hasText);
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent ev)
			{
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent ev)
			{
			}
		});
		return menu;
	}

	private static void copyToClipboard(String text)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}

	void clear()
	{
		synchronized (pending)
		{
			pending.setLength(0);
		}

		if (SwingUtilities.isEventDispatchThread())
		{
			textArea.setText("");
		}
		else
		{
			SwingUtilities.invokeLater(() -> textArea.setText(""));
		}
	}

	OutputStream createOutputStream()
	{
		return new ConsoleOutputStream();
	}

	private void appendOnEdt(String text)
	{
		Document document = textArea.getDocument();
		try
		{
			boolean atBottom = followTail;
			int length = document.getLength();
			document.insertString(length, text, null);
			trimIfNecessary(atBottom);
			if (atBottom)
			{
				moveCaretToTail();
				// NEVER_UPDATE stops the caret dragging the view, so move it ourselves; the attempt
				// budget is per batch, or a flood that outruns the layout would exhaust it for good.
				snapAttempts = 0;
				queueSnapToBottom();
			}
		}
		catch (BadLocationException ex)
		{
			// Ignore append failures to avoid recursive logging.
		}
	}

	private void moveCaretToTail()
	{
		tailCaretDot = textArea.getDocument().getLength();
		textArea.setCaretPosition(tailCaretDot);
	}

	// Deferred so the user's gesture has reached the scrollbar before it is read.
	private void reconsiderFollowingTail()
	{
		SwingUtilities.invokeLater(() ->
		{
			followTail = isScrolledToBottom() && textArea.getSelectionStart() == textArea.getSelectionEnd();
			if (followTail)
			{
				snapAttempts = 0;
				queueSnapToBottom();
			}
		});
	}

	private boolean isScrolledToBottom()
	{
		JScrollBar vertical = scrollPane.getVerticalScrollBar();
		return vertical.getValue() + vertical.getVisibleAmount() >= vertical.getMaximum() - BOTTOM_TOLERANCE_PX;
	}

	private void trimIfNecessary(boolean atBottom)
	{
		Document document = textArea.getDocument();
		int excess = document.getLength() - (atBottom ? MAX_CHARACTERS : MAX_CHARACTERS_SCROLLED_UP);
		if (excess <= 0)
		{
			return;
		}

		Element lines = document.getDefaultRootElement();
		int lineEnd = lines.getElement(lines.getElementIndex(excess - 1)).getEndOffset();
		int cut = lineEnd <= document.getLength() ? lineEnd : excess;

		try
		{
			document.remove(0, cut);
		}
		catch (BadLocationException ex)
		{
			// Ignore trim failures to avoid recursive logging.
		}
	}

	private final class ConsoleOutputStream extends OutputStream
	{
		private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		@Override
		public synchronized void write(int b) throws IOException
		{
			if (b == '\r')
			{
				return;
			}

			buffer.write(b);
			if (b == '\n')
			{
				flushBuffer();
			}
		}

		@Override
		public synchronized void write(byte[] b, int off, int len) throws IOException
		{
			for (int i = 0; i < len; i++)
			{
				write(b[off + i]);
			}
		}

		@Override
		public synchronized void flush() throws IOException
		{
			flushBuffer();
		}

		@Override
		public void close() throws IOException
		{
			flush();
		}

		private void flushBuffer() throws IOException
		{
			if (buffer.size() == 0)
			{
				return;
			}

			String value = buffer.toString(StandardCharsets.UTF_8);
			buffer.reset();
			append(value);
		}
	}
}
