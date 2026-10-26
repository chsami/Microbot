package net.runelite.client.plugins.microbot.questhelper.panel;

import java.awt.Color;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.questhelper.QuestPause;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestPlannerLauncher;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestQueue;
import net.runelite.client.ui.ColorScheme;

/**
 * Always visible in the Quest Helper side panel: Pause or Resume (freezes the automation where it is, keeping the quest,
 * step, queue and purchase progress), Stop (ends the run and the queue) and the quest planner. Swing, EDT only.
 */
public final class QuestAutomationControls extends JPanel
{
	private final JButton pause = new JButton("Pause");
	private final JLabel state = new JLabel(" ");
	private final ConfigManager configManager;

	public QuestAutomationControls(QuestHelperPlugin plugin, ConfigManager configManager)
	{
		this.configManager = configManager;
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new java.awt.BorderLayout(0, 4));
		setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

		JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		pause.addActionListener(e ->
		{
			QuestPause.toggle();
			refresh();
		});
		JButton stop = new JButton("Stop");
		stop.addActionListener(e ->
		{
			QuestPause.resume();
			QuestQueue.shared().stop();
			if (configManager != null)
			{
				configManager.setConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "TurnOn", false);
			}
			refresh();
		});
		JButton planner = new JButton("Planner");
		planner.addActionListener(e -> QuestPlannerLauncher.open(plugin));
		buttons.add(pause);
		buttons.add(stop);
		buttons.add(planner);
		add(buttons, java.awt.BorderLayout.NORTH);
		add(state, java.awt.BorderLayout.SOUTH);

		Timer timer = new Timer(500, e -> refresh());
		timer.start();
		refresh();
	}

	private boolean automationOn()
	{
		String value = configManager == null ? null
			: configManager.getConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "TurnOn");
		return !"false".equals(value);
	}

	private void refresh()
	{
		boolean paused = QuestPause.isPaused();
		pause.setText(paused ? "Resume" : "Pause");
		if (paused)
		{
			state.setText("PAUSED");
			state.setForeground(new Color(230, 80, 60));
		}
		else if (QuestQueue.shared().isActive())
		{
			state.setText("Running the queue");
			state.setForeground(Color.LIGHT_GRAY);
		}
		else
		{
			state.setText(automationOn() ? "Automation on" : "Automation off");
			state.setForeground(Color.GRAY);
		}
	}
}
