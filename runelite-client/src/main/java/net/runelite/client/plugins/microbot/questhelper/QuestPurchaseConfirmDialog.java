package net.runelite.client.plugins.microbot.questhelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.plugins.microbot.Microbot;
import org.slf4j.event.Level;

/**
 * Tells the player which quest items the preflight is about to buy and what they cost, and lets them approve, skip
 * the purchases or cancel. Swing, EDT only; the script thread never waits on it, it polls the callback's result.
 */
public final class QuestPurchaseConfirmDialog
{
	/** How the dialog was closed. */
	public enum Outcome
	{
		BUY, SKIP, CANCEL,
		/** Closed by code (stale dialog, reset); carries no user decision. */
		DISMISSED
	}

	private static final AtomicInteger EPOCH = new AtomicInteger();
	private static volatile JDialog openDialog;

	private QuestPurchaseConfirmDialog()
	{
	}

	/** Closes any open dialog and makes pending requests stale. Safe from any thread. */
	public static void dismiss()
	{
		EPOCH.incrementAndGet();
		QuestPurchasePlan.setAwaiting(false);
		SwingUtilities.invokeLater(() ->
		{
			JDialog open = openDialog;
			if (open != null)
			{
				open.dispose();
			}
		});
	}

	/** Opens the dialog on the EDT; {@code callback} runs on the EDT with the outcome (exactly once). */
	public static void showAsync(String questName, QuestPurchasePlan plan, Consumer<Outcome> callback)
	{
		final int requestEpoch = EPOCH.get();
		SwingUtilities.invokeLater(() ->
		{
			try
			{
				if (requestEpoch != EPOCH.get())
				{
					callback.accept(Outcome.DISMISSED);
					return;
				}
				JDialog stale = openDialog;
				if (stale != null)
				{
					stale.dispose();
				}
				build(questName, plan, requestEpoch, callback);
			}
			catch (RuntimeException e)
			{
				Microbot.log("Quest helper: purchase dialog failed: " + e, Level.ERROR);
				callback.accept(Outcome.CANCEL);
			}
		});
	}

	private static void build(String questName, QuestPurchasePlan plan, int epoch, Consumer<Outcome> callback)
	{
		Frame owner = clientFrame();
		JDialog dialog = new JDialog(owner, questName + " — items to buy", false);
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		dialog.setAlwaysOnTop(true);

		DefaultTableModel model = new DefaultTableModel(new Object[] {"Item", "Qty", "Est. each", "Est. total"}, 0)
		{
			@Override
			public boolean isCellEditable(int row, int column)
			{
				return false;
			}
		};
		for (QuestPurchasePlan.Line line : plan.lines())
		{
			model.addRow(new Object[] {line.name, line.quantity, gp(line.unitPrice), gp(line.total())});
		}
		JTable table = new JTable(model);
		table.setFillsViewportHeight(true);
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(460, Math.min(260, 40 + 22 * plan.lines().size())));

		JPanel info = new JPanel();
		info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
		info.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
		info.add(label("The quest helper will buy these items from the Grand Exchange before starting."));
		info.add(label("Estimated total: " + gp(plan.total())));
		info.add(label("Coins available: " + gp(plan.coins())
			+ (plan.bankChecked() ? " (inventory + bank)" : " (inventory; bank not checked)")));
		if (!plan.affordable())
		{
			JLabel warn = label(String.format(Locale.ROOT,
				"Not enough coins: need ~%,d more gp. Add coins to your bank, or start without buying.",
				plan.shortfallGp()));
			warn.setForeground(new Color(200, 30, 30));
			info.add(warn);
		}

		JButton buy = new JButton("Buy and start");
		buy.setEnabled(plan.affordable());
		JButton skip = new JButton("Start without buying");
		JButton cancel = new JButton("Cancel");

		boolean[] done = {false};
		Consumer<Outcome> finish = chosen ->
		{
			if (done[0])
			{
				return;
			}
			done[0] = true;
			if (openDialog == dialog)
			{
				openDialog = null;
			}
			QuestPurchasePlan.setAwaiting(false);
			dialog.dispose();
			callback.accept(epoch == EPOCH.get() ? chosen : Outcome.DISMISSED);
		};
		buy.addActionListener(e -> finish.accept(Outcome.BUY));
		skip.addActionListener(e -> finish.accept(Outcome.SKIP));
		cancel.addActionListener(e -> finish.accept(Outcome.CANCEL));
		dialog.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				finish.accept(Outcome.CANCEL);
			}

			@Override
			public void windowClosed(WindowEvent e)
			{
				finish.accept(Outcome.DISMISSED);
			}
		});

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(buy);
		buttons.add(skip);
		buttons.add(cancel);

		JPanel root = new JPanel(new BorderLayout());
		root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		root.add(info, BorderLayout.NORTH);
		root.add(scroll, BorderLayout.CENTER);
		root.add(buttons, BorderLayout.SOUTH);
		dialog.setContentPane(root);
		dialog.pack();
		dialog.setLocationRelativeTo(owner);
		openDialog = dialog;
		dialog.setVisible(true);
		dialog.toFront();
		dialog.requestFocus();
	}

	private static JLabel label(String text)
	{
		JLabel label = new JLabel(text, SwingConstants.LEFT);
		label.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		return label;
	}

	private static String gp(long amount)
	{
		return String.format(Locale.ROOT, "%,d gp", amount);
	}

	private static Frame clientFrame()
	{
		Frame best = null;
		for (Frame frame : Frame.getFrames())
		{
			if (!frame.isShowing())
			{
				continue;
			}
			if (frame.getTitle() != null && frame.getTitle().toLowerCase(Locale.ROOT).contains("runelite"))
			{
				return frame;
			}
			if (best == null)
			{
				best = frame;
			}
		}
		return best;
	}
}
