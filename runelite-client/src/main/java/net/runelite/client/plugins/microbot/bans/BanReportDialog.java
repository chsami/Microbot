package net.runelite.client.plugins.microbot.bans;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
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
	static final String MESSAGE = "<html><body style='width: 340px'>"
		+ "<b>This account shows the \"account disabled\" login screen.</b><br><br>"
		+ "Sharing an anonymous report helps every Microbot player see which scripts and habits get accounts banned.<br><br>"
		+ "The report contains the scripts this account ran in the last 30 days and for how long, membership, total level, "
		+ "the longest session and whether breaks were on. It never contains your account name, email, IP or location."
		+ "</body></html>";

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

	static void ask(Component parent, Consumer<Answer> onAnswer)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 10));
		panel.add(new JLabel(MESSAGE), BorderLayout.NORTH);

		JPanel kinds = new JPanel();
		kinds.setLayout(new BoxLayout(kinds, BoxLayout.Y_AXIS));
		kinds.setBorder(BorderFactory.createTitledBorder("What did the message centre say?"));
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

		JCheckBox remember = new JCheckBox("Remember my choice (change it later under Microbot > General > Ban reports)");
		panel.add(remember, BorderLayout.SOUTH);

		Object[] options = {SEND, DONT_SEND};
		JOptionPane pane = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION, null, options, SEND);
		JDialog dialog = pane.createDialog(parent, TITLE);
		dialog.setModal(false);
		AtomicBoolean answered = new AtomicBoolean();
		pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, e ->
		{
			if (e.getNewValue() == JOptionPane.UNINITIALIZED_VALUE || !answered.compareAndSet(false, true))
			{
				return;
			}
			dialog.dispose();
			onAnswer.accept(answer(e.getNewValue(), selected(buttons), remember.isSelected()));
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
