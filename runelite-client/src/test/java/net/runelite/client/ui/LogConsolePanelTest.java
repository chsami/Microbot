package net.runelite.client.ui;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LogConsolePanelTest
{
	private static final int DOCUMENT_CAP = 100_000;

	private LogConsolePanel panel;
	private JScrollPane scrollPane;
	private JTextArea textArea;

	@Before
	public void setUp() throws Exception
	{
		onEdt(() ->
		{
			panel = new LogConsolePanel();
			scrollPane = (JScrollPane) panel.getComponent(0);
			textArea = (JTextArea) scrollPane.getViewport().getView();
			panel.setSize(400, 160);
			layOut();
		});
		panel.append(lines(0, 200));
		settle();
		assertTrue(isAtBottom());
	}

	@Test
	public void bufferCutStartsAtTheNextWholeLine()
	{
		assertEquals(0, LogConsolePanel.lineStartAtOrAfter("one\ntwo\n", 0));
		assertEquals(4, LogConsolePanel.lineStartAtOrAfter("one\ntwo\n", 2));
		assertEquals(4, LogConsolePanel.lineStartAtOrAfter("one\ntwo\n", 4));
		assertEquals(2, LogConsolePanel.lineStartAtOrAfter("one two", 2));
	}

	@Test
	public void clearButtonEmptiesTheLog() throws Exception
	{
		JButton clearButton = onEdtGet(() -> (JButton) ((JPanel) panel.getComponent(1)).getComponent(0));
		assertTrue(onEdtGet(() -> textArea.getDocument().getLength()) > 0);

		onEdt(clearButton::doClick);
		settle();

		assertEquals(0, (int) onEdtGet(() -> textArea.getDocument().getLength()));
	}

	@Test
	public void rightClickMenuOffersCopyOnlyForASelection() throws Exception
	{
		onEdt(() ->
		{
			JPopupMenu menu = textArea.getComponentPopupMenu();
			assertEquals(Arrays.asList("Copy", "Copy all", "Select all", "Clear"), Arrays.stream(menu.getComponents())
				.filter(c -> c instanceof JMenuItem)
				.map(c -> ((JMenuItem) c).getText())
				.collect(Collectors.toList()));
			JMenuItem copy = (JMenuItem) menu.getComponent(0);

			textArea.select(0, 0);
			fireWillBecomeVisible(menu);
			assertFalse(copy.isEnabled());

			textArea.select(0, 5);
			fireWillBecomeVisible(menu);
			assertTrue(copy.isEnabled());
		});
	}

	private static void fireWillBecomeVisible(JPopupMenu menu)
	{
		PopupMenuEvent event = new PopupMenuEvent(menu);
		for (PopupMenuListener listener : menu.getPopupMenuListeners())
		{
			listener.popupMenuWillBecomeVisible(event);
		}
	}

	@Test
	public void wheelUpWinsOverAnAlreadyQueuedSnap() throws Exception
	{
		onEdt(() ->
		{
			panel.append(lines(200, 50));
			SwingUtilities.invokeLater(() ->
			{
				layOut();
				MouseWheelEvent wheelUp = new MouseWheelEvent(scrollPane, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
					0, 10, 10, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1);
				for (MouseWheelListener listener : scrollPane.getMouseWheelListeners())
				{
					listener.mouseWheelMoved(wheelUp);
				}
			});
		});
		settle();
		assertFalse(isAtBottom());

		panel.append(lines(250, 50));
		settle();
		assertFalse(isAtBottom());
	}

	@Test
	public void pageUpStopsFollowing() throws Exception
	{
		press(KeyEvent.VK_PAGE_UP, 0);
		settle();
		assertFalse(isAtBottom());

		panel.append(lines(200, 50));
		settle();
		assertFalse(isAtBottom());
	}

	@Test
	public void ctrlEndAfterPageUpResumesFollowing() throws Exception
	{
		press(KeyEvent.VK_PAGE_UP, 0);
		settle();
		panel.append(lines(200, 50));
		settle();
		assertFalse(isAtBottom());

		press(KeyEvent.VK_END, InputEvent.CTRL_DOWN_MASK);
		settle();
		panel.append(lines(250, 50));
		settle();
		assertTrue(isAtBottom());
	}

	@Test
	public void dragSelectionSurvivesTheNextBatch() throws Exception
	{
		AtomicReference<Point> start = new AtomicReference<>();
		onEdt(() ->
		{
			Point view = scrollPane.getViewport().getViewPosition();
			start.set(new Point(5, view.y + 20));
			textArea.dispatchEvent(mouse(MouseEvent.MOUSE_PRESSED, start.get().x, start.get().y));
			textArea.dispatchEvent(mouse(MouseEvent.MOUSE_DRAGGED, start.get().x + 60, start.get().y + 20));
			textArea.dispatchEvent(mouse(MouseEvent.MOUSE_RELEASED, start.get().x + 60, start.get().y + 20));
		});
		settle();
		String selected = onEdtGet(textArea::getSelectedText);
		assertTrue(selected != null && !selected.isEmpty());

		panel.append(lines(200, 50));
		settle();
		assertEquals(selected, onEdtGet(textArea::getSelectedText));
	}

	@Test
	public void wheelDownToTheBottomResumesFollowing() throws Exception
	{
		onEdt(() ->
		{
			JScrollBar vertical = scrollPane.getVerticalScrollBar();
			vertical.setValue(vertical.getMaximum() / 2);
		});
		wheel(-3);
		settle();
		assertFalse(isAtBottom());

		onEdt(() ->
		{
			JScrollBar vertical = scrollPane.getVerticalScrollBar();
			vertical.setValue(vertical.getMaximum());
		});
		wheel(3);
		settle();
		panel.append(lines(200, 50));
		settle();
		assertTrue(isAtBottom());
	}

	@Test
	public void starvedEdtInsertsOnlyWhatAFollowerKeeps() throws Exception
	{
		List<Integer> inserts = new ArrayList<>();
		onEdt(() -> textArea.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent ev)
			{
				inserts.add(ev.getLength());
			}

			@Override
			public void removeUpdate(DocumentEvent ev)
			{
			}

			@Override
			public void changedUpdate(DocumentEvent ev)
			{
			}
		}));

		CountDownLatch blocked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicBoolean released = new AtomicBoolean();
		SwingUtilities.invokeLater(() ->
		{
			blocked.countDown();
			try
			{
				released.set(release.await(30, TimeUnit.SECONDS));
			}
			catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
			}
		});
		assertTrue(blocked.await(30, TimeUnit.SECONDS));

		int next = 1_000;
		String last = null;
		while (next < 1_000 + 80_000)
		{
			panel.append(lines(next, 1_000));
			next += 1_000;
			last = "line " + (next - 1) + "\n";
		}
		release.countDown();
		settle();
		assertTrue(released.get());

		int largest = inserts.stream().mapToInt(Integer::intValue).max().orElse(0);
		assertTrue("largest insert " + largest, largest <= DOCUMENT_CAP);
		String text = onEdtGet(() ->
		{
			try
			{
				return textArea.getDocument().getText(0, textArea.getDocument().getLength());
			}
			catch (BadLocationException ex)
			{
				throw new IllegalStateException(ex);
			}
		});
		assertTrue(text.length() <= DOCUMENT_CAP);
		assertTrue(text.endsWith(last));
		assertNotEquals(-1, text.indexOf("line " + (next - 2) + "\n"));
	}

	@Test
	public void layoutFailureWhileSnappingStaysOffTheEdt() throws Exception
	{
		AtomicReference<Throwable> uncaught = new AtomicReference<>();
		Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> uncaught.set(ex));
		try
		{
			onEdt(() -> textArea.setUI(new BasicTextAreaUI()
			{
				@Override
				public Rectangle2D modelToView2D(JTextComponent component, int pos, Position.Bias bias)
				{
					throw new IllegalArgumentException("TextHitInfo is out of range");
				}
			}));
			panel.scrollToBottom();
			settle();
		}
		finally
		{
			Thread.setDefaultUncaughtExceptionHandler(previous);
		}
		assertNull(uncaught.get());
	}

	@Test
	public void trimmingDropsWholeLines() throws Exception
	{
		int length = onEdtGet(() -> textArea.getDocument().getLength());
		panel.append("y".repeat(DOCUMENT_CAP - length + 2) + "\n");
		settle();
		String text = onEdtGet(() ->
		{
			try
			{
				return textArea.getDocument().getText(0, textArea.getDocument().getLength());
			}
			catch (BadLocationException ex)
			{
				throw new IllegalStateException(ex);
			}
		});
		assertTrue(text.length() <= DOCUMENT_CAP);
		assertTrue(text.startsWith("line 1\n"));
		assertTrue(text.endsWith("y\n"));
	}

	private void press(int keyCode, int modifiers) throws Exception
	{
		onEdt(() ->
		{
			KeyEvent key = new KeyEvent(textArea, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
				modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
			for (KeyListener listener : textArea.getKeyListeners())
			{
				listener.keyPressed(key);
			}
			Object binding = textArea.getInputMap().get(KeyStroke.getKeyStrokeForEvent(key));
			textArea.getActionMap().get(binding).actionPerformed(new ActionEvent(textArea, ActionEvent.ACTION_PERFORMED, null));
		});
	}

	private void wheel(int rotation) throws Exception
	{
		onEdt(() ->
		{
			MouseWheelEvent event = new MouseWheelEvent(scrollPane, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
				0, 10, 10, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rotation);
			for (MouseWheelListener listener : scrollPane.getMouseWheelListeners())
			{
				listener.mouseWheelMoved(event);
			}
		});
	}

	private MouseEvent mouse(int id, int x, int y)
	{
		int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
		return new MouseEvent(textArea, id, System.currentTimeMillis(), modifiers, x, y, 1, false, MouseEvent.BUTTON1);
	}

	private void layOut()
	{
		for (int i = 0; i < 2; i++)
		{
			panel.doLayout();
			scrollPane.doLayout();
			scrollPane.getViewport().doLayout();
		}
	}

	private void settle() throws Exception
	{
		for (int i = 0; i < 40; i++)
		{
			onEdt(this::layOut);
		}
	}

	private boolean isAtBottom() throws Exception
	{
		return onEdtGet(() ->
		{
			JScrollBar vertical = scrollPane.getVerticalScrollBar();
			return vertical.getValue() + vertical.getVisibleAmount() >= vertical.getMaximum() - 4;
		});
	}

	private static String lines(int from, int count)
	{
		StringBuilder text = new StringBuilder();
		for (int i = from; i < from + count; i++)
		{
			text.append("line ").append(i).append('\n');
		}
		return text.toString();
	}

	private static void onEdt(Runnable runnable) throws Exception
	{
		SwingUtilities.invokeAndWait(runnable);
	}

	private static <T> T onEdtGet(Supplier<T> supplier) throws Exception
	{
		AtomicReference<T> result = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> result.set(supplier.get()));
		return result.get();
	}
}
