package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * DD Chopper side panel. Every control writes straight to the plugin config under the same keys as
 * {@link DDChopperConfig}: reads happen on the Swing thread (cheap config reads), writes are handed to a single
 * background thread so ConfigManager never runs on the EDT.
 */
public class DDChopperPanel extends PluginPanel
{
	private static final Color GREEN = new Color(0x4CAF50);
	private static final Color BLUE = new Color(0x4FA3E0);
	private static final Color ORANGE = new Color(0xE0903A);
	private static final Color RED = new Color(0xE05050);

	private static final int REFRESH_MS = 1000;
	/** Width of wrapped hint text, sized to the sidebar. */
	private static final int WRAP_WIDTH_PX = 170;
	private static final int LABEL_WIDTH = 70;

	private final ConfigManager configManager;
	private final DDChopperConfig config;
	private final DDChopperScript script;
	private final ExecutorService writer = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "ddchopper-config-writer");
		t.setDaemon(true);
		return t;
	});
	private final List<Runnable> syncers = new ArrayList<>();
	private final Timer refresh;
	private boolean syncing;

	private JComboBox<Spot> spotBox;
	private JLabel handlingHint;
	private JLabel statusLabel;
	private JLabel levelLabel;
	private JLabel xpLabel;
	private JLabel nextLevelLabel;
	private JLabel goalEtaLabel;
	private JLabel forestryNote;
	private JSpinner targetLevel;
	private JSpinner runHours;
	private JSpinner runMinutes;

	public DDChopperPanel(ConfigManager configManager, DDChopperConfig config, DDChopperScript script)
	{
		super(false);
		this.configManager = configManager;
		this.config = config;
		this.script = script;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		body.add(header());
		body.add(Box.createVerticalStrut(8));
		body.add(resourceSection());
		body.add(Box.createVerticalStrut(8));
		body.add(goalSection());
		body.add(Box.createVerticalStrut(8));
		body.add(displaySection());
		body.add(Box.createVerticalStrut(8));
		body.add(forestrySection());
		body.add(Box.createVerticalStrut(8));
		body.add(runtimeSection());
		body.add(Box.createVerticalStrut(8));
		JLabel foot = new JLabel("<html>Breaks and world hopping remain in plugin configuration.</html>");
		foot.setForeground(Color.GRAY);
		body.add(foot);
		add(body, BorderLayout.NORTH);

		refresh = new Timer(REFRESH_MS, e -> refreshAll());
		refresh.start();
		refreshAll();
	}

	/** Stops the refresh timer and the writer thread. */
	public void dispose()
	{
		refresh.stop();
		writer.shutdown();
	}

	// ---------------------------------------------------------------------------------------------
	// Sections
	// ---------------------------------------------------------------------------------------------

	private JComponent header()
	{
		JPanel p = box(null);
		JLabel title = new JLabel("DD Chopper");
		title.setForeground(GREEN);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		JLabel sub = new JLabel("<html>Auto woodcutting: best axe, best tree, human timing.</html>");
		sub.setForeground(Color.LIGHT_GRAY);
		p.add(title);
		p.add(sub);
		return p;
	}

	private JComponent resourceSection()
	{
		JPanel p = box("Resource collection");

		JComboBox<TreeChoice> tree = combo(TreeChoice.values(), "tree", config::tree);
		p.add(row("Tree", tree));

		spotBox = new JComboBox<>();
		fillSpots(config.tree());
		spotBox.setSelectedItem(config.spot());
		spotBox.addActionListener(e ->
		{
			if (!syncing && spotBox.getSelectedItem() != null)
			{
				write("spot", ((Spot) spotBox.getSelectedItem()).name());
			}
		});
		syncers.add(() ->
		{
			fillSpots(config.tree());
			spotBox.setSelectedItem(config.spot());
		});
		tree.addActionListener(e ->
		{
			if (!syncing)
			{
				TreeChoice choice = (TreeChoice) tree.getSelectedItem();
				fillSpots(choice);
				Spot current = config.spot();
				if (spotBox.getItemCount() > 0)
				{
					spotBox.setSelectedItem(current);
					if (spotBox.getSelectedItem() != current)
					{
						spotBox.setSelectedItem(Spot.AUTO);
					}
				}
			}
		});
		p.add(row("Spot", spotBox));

		p.add(row("Axe", combo(AxeChoice.values(), "axe", config::axe)));
		p.add(row("Handling", combo(ChopMode.values(), "mode", config::mode)));
		handlingHint = new JLabel();
		p.add(handlingHint);
		return p;
	}

	private JComponent goalSection()
	{
		JPanel p = box("Goal");
		p.add(row("Goal", combo(GoalType.values(), "goalType", config::goalType)));
		targetLevel = spinner("targetLevel", 2, 99, config::targetLevel);
		p.add(row("Target level", targetLevel));
		runHours = spinner("runHours", 0, 99, config::runHours);
		p.add(row("Run hours", runHours));
		runMinutes = spinner("runMinutes", 0, 59, config::runMinutes);
		p.add(row("Run minutes", runMinutes));
		return p;
	}

	private JComponent displaySection()
	{
		JPanel p = box("Display");
		p.add(check("Show in-game overlay", "showOverlay", config::showOverlay));
		return p;
	}

	private JComponent forestrySection()
	{
		JPanel p = box("Forestry");
		p.add(check("Enable Forestry events", "forestryEnabled", config::forestryEnabled));
		p.add(check("Rising Roots", "forestryRoots", config::forestryRoots));
		p.add(check("Struggling Sapling", "forestrySapling", config::forestrySapling));
		p.add(check("Friendly Entlings", "forestryEntlings", config::forestryEntlings));
		p.add(check("Beehives", "forestryBeehives", config::forestryBeehives));
		p.add(check("Pheasant Control", "forestryPheasant", config::forestryPheasant));
		p.add(check("Poachers / Fox", "forestryPoachers", config::forestryPoachers));
		p.add(check("Enchantment Ritual", "forestryRitual", config::forestryRitual));
		p.add(check("Woodcutting Leprechaun", "forestryLeprechaun", config::forestryLeprechaun));
		p.add(check("Flowering Tree", "forestryFlowering", config::forestryFlowering));
		p.add(Box.createVerticalStrut(4));
		p.add(check("Prefer busy world for events", "forestryBusyWorld", config::forestryBusyWorld));
		p.add(row("Min players at spot", spinner("forestryMinPlayers", 1, 50, config::forestryMinPlayers)));
		forestryNote = new JLabel();
		forestryNote.setForeground(RED);
		p.add(forestryNote);
		return p;
	}

	private JComponent runtimeSection()
	{
		JPanel p = box("Runtime");
		statusLabel = new JLabel();
		levelLabel = new JLabel();
		xpLabel = new JLabel();
		nextLevelLabel = new JLabel();
		goalEtaLabel = new JLabel();
		p.add(statusLabel);
		p.add(levelLabel);
		p.add(xpLabel);
		p.add(nextLevelLabel);
		p.add(goalEtaLabel);
		p.add(Box.createVerticalStrut(4));
		JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
		buttons.setOpaque(false);
		JButton start = new JButton("Start");
		JButton pause = new JButton("Pause");
		JButton stop = new JButton("Stop");
		start.addActionListener(e -> offload(() -> script.startRun(config)));
		pause.addActionListener(e -> script.pause());
		stop.addActionListener(e -> offload(script::stopRun));
		buttons.add(start);
		buttons.add(pause);
		buttons.add(stop);
		p.add(buttons);
		return p;
	}

	// ---------------------------------------------------------------------------------------------
	// Refresh
	// ---------------------------------------------------------------------------------------------

	private void refreshAll()
	{
		syncing = true;
		try
		{
			for (Runnable r : syncers)
			{
				r.run();
			}
			updateHint();
			updateRuntime();
			boolean timed = config.goalType() == GoalType.RUN_TIME;
			boolean leveled = config.goalType() == GoalType.TARGET_LEVEL;
			targetLevel.setEnabled(leveled);
			runHours.setEnabled(timed);
			runMinutes.setEnabled(timed);
			forestryNote.setText(wrap(CrowdRule.conflictNote(config.forestryEnabled(), config.forestryBusyWorld(),
				config.hopWhenCrowded())));
		}
		finally
		{
			syncing = false;
		}
	}

	private void updateHint()
	{
		ChopMode mode = config.mode();
		Color c = mode == ChopMode.BANK ? BLUE : mode == ChopMode.BURN ? ORANGE : GREEN;
		handlingHint.setForeground(c);
		handlingHint.setText(wrap(ResourceHandling.hint(mode)));
	}

	private void updateRuntime()
	{
		RuntimeStatus.State state = script.getRunState();
		statusLabel.setForeground(state == RuntimeStatus.State.RUNNING ? GREEN
			: state == RuntimeStatus.State.STOPPED ? RED : ORANGE);
		statusLabel.setText(wrap(RuntimeStatus.describe(state, reasonFor(state))));
		levelLabel.setText("Level: " + RuntimeStatus.levelLine(config.goalType(), script.getCurrentLevel(),
			config.targetLevel()));
		xpLabel.setText("XP/hr: " + String.format("%,d", script.getXpPerHour()));
		nextLevelLabel.setText("Next lvl: " + script.getNextLevelEta());
		String goalLabel = script.getGoalEtaLabel();
		goalEtaLabel.setVisible(goalLabel != null);
		goalEtaLabel.setText(goalLabel == null ? "" : goalLabel + ": " + script.getGoalEta());
	}

	private String reasonFor(RuntimeStatus.State state)
	{
		return state == RuntimeStatus.State.RUNNING ? script.getNotice() : script.getBlockReason();
	}

	private static String wrap(String text)
	{
		return text == null || text.isEmpty() ? "" : "<html><div style='width:" + WRAP_WIDTH_PX + "px'>" + text + "</div></html>";
	}

	// ---------------------------------------------------------------------------------------------
	// Widgets
	// ---------------------------------------------------------------------------------------------

	private void fillSpots(TreeChoice choice)
	{
		boolean was = syncing;
		syncing = true;
		try
		{
			Object keep = spotBox.getSelectedItem();
			spotBox.removeAllItems();
			spotBox.addItem(Spot.AUTO);
			for (Spot s : Spot.values())
			{
				if (s != Spot.AUTO && (choice == null || choice.tree() == null || s.getTree() == choice.tree()))
				{
					spotBox.addItem(s);
				}
			}
			if (keep != null)
			{
				spotBox.setSelectedItem(keep);
			}
		}
		finally
		{
			syncing = was;
		}
	}

	private <E extends Enum<E>> JComboBox<E> combo(E[] values, String key, Supplier<E> current)
	{
		JComboBox<E> box = new JComboBox<>(values);
		box.setSelectedItem(current.get());
		box.addActionListener(e ->
		{
			if (!syncing && box.getSelectedItem() != null)
			{
				write(key, ((Enum<?>) box.getSelectedItem()).name());
			}
		});
		syncers.add(() ->
		{
			if (box.getSelectedItem() != current.get())
			{
				box.setSelectedItem(current.get());
			}
		});
		return box;
	}

	private JCheckBox check(String label, String key, Supplier<Boolean> current)
	{
		JCheckBox box = new JCheckBox(label, current.get());
		box.setOpaque(false);
		box.setForeground(Color.WHITE);
		box.addActionListener(e ->
		{
			if (!syncing)
			{
				write(key, box.isSelected());
			}
		});
		syncers.add(() ->
		{
			if (box.isSelected() != current.get())
			{
				box.setSelected(current.get());
			}
		});
		return box;
	}

	private JSpinner spinner(String key, int min, int max, Supplier<Integer> current)
	{
		JSpinner s = new JSpinner(new SpinnerNumberModel((int) current.get(), min, max, 1));
		s.addChangeListener(e ->
		{
			if (!syncing)
			{
				write(key, (Integer) s.getValue());
			}
		});
		syncers.add(() ->
		{
			if (!s.getValue().equals(current.get()) && !isEditing(s))
			{
				s.setValue(current.get());
			}
		});
		return s;
	}

	private static boolean isEditing(JSpinner s)
	{
		return s.getEditor() != null && s.getEditor().getComponent(0).isFocusOwner();
	}

	private JPanel box(String title)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(title == null ? BorderFactory.createEmptyBorder(6, 6, 6, 6) : BorderFactory.createCompoundBorder(
			BorderFactory.createTitledBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), title,
				TitledBorder.LEFT, TitledBorder.TOP, null, GREEN),
			BorderFactory.createEmptyBorder(4, 6, 6, 6)));
		return p;
	}

	private JPanel row(String label, JComponent field)
	{
		JPanel r = new JPanel(new BorderLayout(6, 0));
		r.setOpaque(false);
		JLabel l = new JLabel(label);
		l.setForeground(Color.WHITE);
		l.setPreferredSize(new Dimension(LABEL_WIDTH, 20));
		r.add(l, BorderLayout.WEST);
		r.add(field, BorderLayout.CENTER);
		r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		return r;
	}

	// ---------------------------------------------------------------------------------------------
	// Threading
	// ---------------------------------------------------------------------------------------------

	/** Saves one value to the shared config key, off the Swing thread. */
	private void write(String key, Object value)
	{
		offload(() -> configManager.setConfiguration(DDChopperConfig.GROUP, key, value));
	}

	private void offload(Runnable task)
	{
		if (writer.isShutdown())
		{
			return;
		}
		writer.submit(() ->
		{
			try
			{
				task.run();
			}
			catch (Exception e)
			{
				SwingUtilities.invokeLater(() -> statusLabel.setText("Error: " + e.getMessage()));
			}
		});
	}
}
