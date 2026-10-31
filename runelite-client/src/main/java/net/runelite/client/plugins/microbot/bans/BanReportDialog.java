package net.runelite.client.plugins.microbot.bans;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import lombok.Value;

final class BanReportDialog
{
	static final String TITLE = "Share an anonymous ban report?";
	static final String SEND = "Send report";
	static final String DONT_SEND = "Don't send";
	static final String KIND_TITLE = "What does your message centre on runescape.com say?";
	static final String KIND_HINT = "Log in on the website to check. If you're not sure, leave \"I don't know yet\".";
	static final String REMEMBER = "Remember my choice (change it later under 'Ban reports' in the Microbot settings)";
	static final String CLOSE_HINT = "Closing this window decides nothing: you are asked again after the client restarts.";

	@Value
	static class Answer
	{
		boolean send;
		BanKind kind;
		boolean remember;
	}

	private BanReportDialog()
	{
	}

	static String message(String accountName)
	{
		String who = accountName == null || accountName.isBlank()
			? "This account shows"
			: "<b>" + escapeHtml(accountName.trim()) + "</b> shows";
		return "<html><body style='width: 360px'>"
			+ who + " the \"account disabled\" login screen. The name stays on this computer.<br><br>"
			+ "Sharing an anonymous report helps every Microbot player see which scripts and habits get accounts banned.<br><br>"
			+ "The report contains the scripts this account ran in the last 30 days and for how long, membership, total level "
			+ "(rounded), the longest session and whether breaks were on. It never contains your account name, email, IP or "
			+ "location, just a scrambled ID so the same ban isn't counted twice."
			+ "</body></html>";
	}

	private static String escapeHtml(String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	static void ask(Component parent, String accountName, Consumer<Answer> onAnswer)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 10));
		panel.add(new JLabel(message(accountName)), BorderLayout.NORTH);

		JPanel kinds = new JPanel();
		kinds.setLayout(new BoxLayout(kinds, BoxLayout.Y_AXIS));
		kinds.setBorder(BorderFactory.createTitledBorder(KIND_TITLE));
		kinds.add(new JLabel(KIND_HINT));
		ButtonGroup group = new ButtonGroup();
		JRadioButton[] buttons = new JRadioButton[BanKind.values().length];
		for (BanKind kind : BanKind.values())
		{
			JRadioButton button = new JRadioButton(kind.toString(), kind == BanKind.UNKNOWN);
			group.add(button);
			kinds.add(button);
			buttons[kind.ordinal()] = button;
		}
		panel.add(kinds, BorderLayout.CENTER);

		JPanel footer = new JPanel();
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
		JCheckBox remember = new JCheckBox(REMEMBER);
		footer.add(remember);
		footer.add(new JLabel(CLOSE_HINT));
		panel.add(footer, BorderLayout.SOUTH);

		JButton send = new JButton(SEND);
		JButton dontSend = new JButton(DONT_SEND);
		JOptionPane pane = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
			new Object[]{send, dontSend}, send);
		JDialog dialog = pane.createDialog(parent, TITLE);
		dialog.setModal(false);

		Runnable syncButtons = () ->
		{
			boolean notABan = buttons[BanKind.NOT_A_BAN.ordinal()].isSelected();
			send.setEnabled(!notABan);
			remember.setEnabled(!notABan);
			dontSend.setText(notABan ? "Close" : DONT_SEND);
		};
		for (JRadioButton button : buttons)
		{
			button.addActionListener(e -> syncButtons.run());
		}

		AtomicBoolean answered = new AtomicBoolean();
		Consumer<Object> finish = choice ->
		{
			if (!answered.compareAndSet(false, true))
			{
				return;
			}
			dialog.dispose();
			onAnswer.accept(answer(choice, selected(buttons), remember.isSelected()));
		};
		send.addActionListener(e -> finish.accept(SEND));
		dontSend.addActionListener(e -> finish.accept(DONT_SEND));
		pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, e ->
		{
			if (e.getNewValue() != JOptionPane.UNINITIALIZED_VALUE)
			{
				finish.accept(null);
			}
		});
		dialog.setVisible(true);
	}

	static Answer answer(Object choice, BanKind kind, boolean remember)
	{
		if (!SEND.equals(choice) && !DONT_SEND.equals(choice))
		{
			return new Answer(false, BanKind.UNKNOWN, false);
		}
		boolean send = SEND.equals(choice) && kind != BanKind.NOT_A_BAN;
		return new Answer(send, kind, remember && kind != BanKind.NOT_A_BAN);
	}

	private static BanKind selected(JRadioButton[] buttons)
	{
		for (BanKind kind : BanKind.values())
		{
			if (buttons[kind.ordinal()].isSelected())
			{
				return kind;
			}
		}
		return BanKind.UNKNOWN;
	}
}
