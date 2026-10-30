package net.runelite.client.plugins.microbot.questhelper.planner;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.questhelper.QuestPurchasePlan;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatSetupDialog;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatStore;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import org.slf4j.event.Level;

/**
 * The one quest window. Opens for the planner (pick quests, order them, random mode) and for every quest that starts,
 * unless "Fully automated" is on. Tabs: Add quests (planner only), Items and supplies (required, recommended, not
 * allowed, supplies, gear), Gear and combat (the setup panels: styles, prayer, per-fight gear), Purchases (the
 * bank-first shortfall and its cost). One Start button saves everything and approves the purchases. The panels are
 * the existing ones, embedded: {@link QuestProfileEditor} and {@link QuestCombatSetupDialog.Panel}.
 * Swing, EDT only; non-modal.
 */
public final class QuestWindow
{
	/** What Start hands back. */
	public static final class Result
	{
		public final List<QuestProfile> profiles;
		/** The final loadout of every quest, by quest display name. */
		public final Map<String, QuestCombatLoadout> loadouts;
		public final QuestApproval approval;
		public final boolean automated;
		/** "Start without buying": the quest runs with what the player already has. */
		public final boolean skipBuying;

		Result(List<QuestProfile> profiles, Map<String, QuestCombatLoadout> loadouts, QuestApproval approval,
			boolean automated, boolean skipBuying)
		{
			this.profiles = profiles;
			this.loadouts = loadouts;
			this.approval = approval;
			this.automated = automated;
			this.skipBuying = skipBuying;
		}
	}

	private static volatile JDialog open;

	private QuestWindow()
	{
	}

	/** The planner: all tabs, a queue to build. {@code done} gets null when closed without Start. */
	public static void openPlanner(QuestHelperPlugin plugin, List<QuestPlannerFilter.Row> rows,
		Consumer<Result> done)
	{
		show(new Form(plugin, rows, new ArrayList<>(), true, done));
	}

	/** The window for one quest that is about to start, prefilled from its profile. */
	public static void openForQuest(QuestHelperPlugin plugin, QuestProfile profile, Consumer<Result> done)
	{
		List<QuestProfile> one = new ArrayList<>();
		one.add(profile);
		show(new Form(plugin, new ArrayList<>(), one, false, done));
	}

	/** Closes the window if one is open (a stop or a new start made it stale). */
	public static void dismiss()
	{
		SwingUtilities.invokeLater(() ->
		{
			JDialog stale = open;
			if (stale != null)
			{
				stale.dispose();
			}
		});
	}

	private static void show(Form form)
	{
		JDialog stale = open;
		if (stale != null)
		{
			stale.dispose();
		}
		form.display();
	}

	private static final class Form
	{
		private final QuestHelperPlugin plugin;
		private final List<QuestPlannerFilter.Row> rows;
		private final List<QuestProfile> entries;
		private final boolean planner;
		private final Consumer<Result> done;
		private final QuestQueue queue = QuestQueue.shared();

		private final Map<String, QuestInfo> infos = new ConcurrentHashMap<>();
		private final Map<String, QuestCombatSetupDialog.Panel> panels = new HashMap<>();
		private final Map<String, JComponent> panelViews = new HashMap<>();

		private JDialog dialog;
		private JTabbedPane tabs;
		private final JPanel itemsHost = new JPanel(new BorderLayout());
		private final JPanel combatHost = new JPanel(new BorderLayout());
		private final JPanel purchasesHost = new JPanel(new BorderLayout());
		private final JPanel planHost = new JPanel(new BorderLayout());
		private int planVersion;
		private final DefaultListModel<String> queueModel = new DefaultListModel<>();
		private final JList<String> queueList = new JList<>(queueModel);
		private final DefaultListModel<QuestPlannerFilter.Row> questModel = new DefaultListModel<>();
		private final JList<QuestPlannerFilter.Row> questList = new JList<>(questModel);
		private final JTextField search = new JTextField(16);
		private final JCheckBox showAll = new JCheckBox("Show all quests");
		private final JRadioButton fixed = new JRadioButton("Fixed order", true);
		private final JRadioButton random = new JRadioButton("Random / auto");
		private final JCheckBox restrictPool = new JCheckBox("Only pick from the queue");
		private final JSpinner pauseMin = new JSpinner(new SpinnerNumberModel(1, 0, 600, 1));
		private final JSpinner pauseMax = new JSpinner(new SpinnerNumberModel(10, 0, 600, 1));
		private final JComboBox<String> defaultStamina = new JComboBox<>(new String[] {"0", "1", "2", "3", "4"});
		private final JCheckBox automated = new JCheckBox("Fully automated: accept everything, never open this window");
		private final JSpinner maxSpend = new JSpinner(new SpinnerNumberModel(100_000, 0, 2_000_000_000, 10_000));
		private final JLabel status = new JLabel(" ");
		private final JButton start = new JButton("Start");
		private final JButton startWithoutBuying = new JButton("Start without buying");
		private int purchaseVersion;
		private boolean finished;

		Form(QuestHelperPlugin plugin, List<QuestPlannerFilter.Row> rows, List<QuestProfile> initial,
			boolean planner, Consumer<Result> done)
		{
			this.plugin = plugin;
			this.rows = rows;
			this.entries = initial;
			this.planner = planner;
			this.done = done;
		}

		// ---- window ----

		void display()
		{
			if (planner)
			{
				for (QuestProfile saved : queue.entries())
				{
					entries.add(saved.copy());
				}
			}
			dialog = new JDialog(QuestPlannerLauncher.clientFrame(),
				planner ? "Quest Planner" : "Quest: " + entries.get(0).getQuestName(), false);
			dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			dialog.setAlwaysOnTop(true);
			dialog.addWindowListener(new java.awt.event.WindowAdapter()
			{
				@Override
				public void windowClosing(java.awt.event.WindowEvent e)
				{
					finish(null);
					dialog.dispose();
				}

				@Override
				public void windowClosed(java.awt.event.WindowEvent e)
				{
					if (open == dialog)
					{
						open = null;
					}
					finish(null);
				}
			});

			tabs = new JTabbedPane();
			if (planner)
			{
				tabs.addTab("Add quests", questsTab());
			}
			itemsHost.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
			tabs.addTab("Items & supplies", itemsHost);
			tabs.addTab("Gear & combat", combatHost);
			tabs.addTab("Purchases", purchasesHost);
			tabs.addTab("Requirements plan", planHost);
			for (int i = 0; i < tabs.getTabCount() && i < 9; i++)
			{
				tabs.setMnemonicAt(i, KeyEvent.VK_1 + i);
			}
			tabs.addChangeListener(e -> refreshCurrentTab());

			dialog.setLayout(new BorderLayout(6, 6));
			if (planner)
			{
				JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, queuePane(), tabs);
				split.setResizeWeight(0.0);
				split.setDividerLocation(260);
				dialog.add(split, BorderLayout.CENTER);
			}
			else
			{
				dialog.add(tabs, BorderLayout.CENTER);
			}
			dialog.add(bottomBar(), BorderLayout.SOUTH);

			automated.setSelected(configBool("fullyAutomated", false));
			maxSpend.setValue(configInt("maxSpendPerQuest", 100_000));
			defaultStamina.setSelectedIndex(QuestPlannerLauncher.globalMinStamina());
			if (planner)
			{
				fixed.setSelected(!queue.isRandom());
				random.setSelected(queue.isRandom());
				pauseMin.setValue(queue.pauseMinMinutes());
				pauseMax.setValue(queue.pauseMaxMinutes());
				restrictPool.setSelected(!queue.pool().isEmpty());
				refreshQuestList();
			}
			refreshQueue();
			if (!planner)
			{
				tabs.setSelectedIndex(0);
				refreshCurrentTab();
			}

			dialog.getRootPane().setDefaultButton(start);
			dialog.getRootPane().registerKeyboardAction(e ->
			{
				finish(null);
				dialog.dispose();
			}, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
			dialog.setSize(planner ? 1240 : 1000, 720);
			dialog.setLocationRelativeTo(dialog.getOwner());
			open = dialog;
			dialog.setVisible(true);
			dialog.toFront();
			if (planner)
			{
				search.requestFocusInWindow();
			}
		}

		private JPanel bottomBar()
		{
			JPanel panel = new JPanel(new BorderLayout(6, 0));
			panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
			JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			left.add(automated);
			left.add(new JLabel("max spend per quest"));
			left.add(maxSpend);
			left.add(new JLabel("gp"));
			left.add(new JLabel("   keep at least"));
			left.add(defaultStamina);
			left.add(new JLabel("stamina (default)"));
			JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
			start.setText(planner ? "Start queue" : "Start");
			start.addActionListener(e -> startPressed(false));
			startWithoutBuying.addActionListener(e -> startPressed(true));
			JButton cancel = new JButton("Cancel");
			cancel.addActionListener(e ->
			{
				finish(null);
				dialog.dispose();
			});
			right.add(start);
			if (!planner)
			{
				right.add(startWithoutBuying);
			}
			right.add(cancel);
			JPanel south = new JPanel(new BorderLayout());
			south.add(left, BorderLayout.CENTER);
			south.add(right, BorderLayout.EAST);
			panel.add(status, BorderLayout.NORTH);
			panel.add(south, BorderLayout.CENTER);
			return panel;
		}

		private void finish(Result result)
		{
			if (!finished)
			{
				finished = true;
				done.accept(result);
			}
		}

		// ---- add quests tab (planner) ----

		private JPanel questsTab()
		{
			JPanel panel = new JPanel(new BorderLayout(4, 4));
			panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
			JPanel top = new JPanel();
			top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
			JPanel line1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			line1.add(new JLabel("Search"));
			line1.add(search);
			line1.add(showAll);
			line1.add(new JLabel("   Queue mode:"));
			ButtonGroup group = new ButtonGroup();
			group.add(fixed);
			group.add(random);
			line1.add(fixed);
			line1.add(random);
			JPanel line2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			line2.add(restrictPool);
			line2.add(new JLabel("Rest between quests (min):"));
			line2.add(pauseMin);
			line2.add(new JLabel("to"));
			line2.add(pauseMax);
			JButton clearSkipped = new JButton("Forget skipped quests");
			clearSkipped.addActionListener(e ->
			{
				queue.clearSkipped();
				status.setText("Skipped quests cleared");
			});
			line2.add(clearSkipped);
			top.add(line1);
			top.add(line2);
			panel.add(top, BorderLayout.NORTH);

			questList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			questList.setCellRenderer(new DefaultListCellRenderer()
			{
				@Override
				public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean selected, boolean focus)
				{
					QuestPlannerFilter.Row row = (QuestPlannerFilter.Row) value;
					JLabel label = (JLabel) super.getListCellRendererComponent(list,
						row.available ? row.name : row.name + "  -  " + row.reason, index, selected, focus);
					if (!row.available && !selected)
					{
						label.setForeground(Color.GRAY);
					}
					label.setToolTipText(row.available ? null : row.reason);
					return label;
				}
			});
			questList.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e)
				{
					if (e.getClickCount() == 2)
					{
						addSelected();
					}
				}
			});
			questList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "add");
			questList.getActionMap().put("add", new AbstractAction()
			{
				@Override
				public void actionPerformed(java.awt.event.ActionEvent e)
				{
					addSelected();
				}
			});
			panel.add(new JScrollPane(questList), BorderLayout.CENTER);
			JButton add = new JButton("Add selected to the queue (double-click or Enter)");
			add.addActionListener(e -> addSelected());
			JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
			buttons.add(add);
			panel.add(buttons, BorderLayout.SOUTH);

			search.getDocument().addDocumentListener(new DocumentListener()
			{
				@Override
				public void insertUpdate(DocumentEvent e)
				{
					refreshQuestList();
				}

				@Override
				public void removeUpdate(DocumentEvent e)
				{
					refreshQuestList();
				}

				@Override
				public void changedUpdate(DocumentEvent e)
				{
					refreshQuestList();
				}
			});
			showAll.addActionListener(e -> refreshQuestList());
			return panel;
		}

		private void refreshQuestList()
		{
			questModel.clear();
			for (QuestPlannerFilter.Row row : QuestPlannerFilter.filter(rows, search.getText(), showAll.isSelected()))
			{
				questModel.addElement(row);
			}
		}

		private void addSelected()
		{
			List<String> picked = new ArrayList<>();
			List<String> have = names();
			for (QuestPlannerFilter.Row row : questList.getSelectedValuesList())
			{
				if (!have.contains(row.name))
				{
					picked.add(row.name);
				}
			}
			if (picked.isEmpty())
			{
				return;
			}
			status.setText("Adding " + picked.size() + " quest(s)...");
			Thread thread = new Thread(() ->
			{
				List<QuestProfile> created = new ArrayList<>();
				for (String name : picked)
				{
					created.add(QuestPlannerLauncher.profileFor(name));
				}
				SwingUtilities.invokeLater(() ->
				{
					entries.addAll(created);
					refreshQueue();
					queueList.setSelectedIndex(entries.size() - 1);
					status.setText(created.size() + " quest(s) added; edit each one in the next tabs.");
				});
			}, "quest-window-add");
			thread.setDaemon(true);
			thread.start();
		}

		// ---- queue (planner) ----

		private JPanel queuePane()
		{
			queueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			queueList.addListSelectionListener(e ->
			{
				if (!e.getValueIsAdjusting())
				{
					refreshCurrentTab();
				}
			});
			JPanel panel = new JPanel(new BorderLayout(4, 4));
			panel.setBorder(BorderFactory.createTitledBorder("Queue (runs top to bottom)"));
			panel.add(new JScrollPane(queueList), BorderLayout.CENTER);
			JButton up = new JButton("Up");
			up.addActionListener(e -> moveSelected(-1));
			JButton down = new JButton("Down");
			down.addActionListener(e -> moveSelected(1));
			JButton remove = new JButton("Remove");
			remove.addActionListener(e ->
			{
				int index = queueList.getSelectedIndex();
				if (index >= 0)
				{
					entries.remove(index);
					refreshQueue();
				}
			});
			JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
			buttons.add(up);
			buttons.add(down);
			buttons.add(remove);
			panel.add(buttons, BorderLayout.SOUTH);
			return panel;
		}

		private void moveSelected(int delta)
		{
			int index = queueList.getSelectedIndex();
			if (QuestQueue.move(entries, index, delta))
			{
				refreshQueue();
				queueList.setSelectedIndex(index + delta);
			}
		}

		private void refreshQueue()
		{
			int keep = queueList.getSelectedIndex();
			queueModel.clear();
			for (int i = 0; i < entries.size(); i++)
			{
				queueModel.addElement(label(i));
			}
			if (keep >= 0 && keep < queueModel.size())
			{
				queueList.setSelectedIndex(keep);
			}
			else if (!entries.isEmpty() && planner)
			{
				queueList.setSelectedIndex(0);
			}
			refreshCurrentTab();
		}

		private String label(int i)
		{
			QuestProfile profile = entries.get(i);
			return (i + 1) + ". " + profile.getQuestName() + "  [" + profile.getType() + ", "
				+ (profile.geAllowed() ? "GE ok" : "bank only") + "]";
		}

		private List<String> names()
		{
			List<String> out = new ArrayList<>();
			for (QuestProfile profile : entries)
			{
				out.add(profile.getQuestName());
			}
			return out;
		}

		private QuestProfile selected()
		{
			if (!planner)
			{
				return entries.isEmpty() ? null : entries.get(0);
			}
			int index = queueList.getSelectedIndex();
			return index >= 0 && index < entries.size() ? entries.get(index) : null;
		}

		private void profileChanged()
		{
			int index = planner ? queueList.getSelectedIndex() : 0;
			if (planner && index >= 0 && index < entries.size())
			{
				queueModel.set(index, label(index));
			}
			purchaseVersion++;
		}

		// ---- tab content ----

		private void refreshCurrentTab()
		{
			if (tabs == null)
			{
				return;
			}
			String title = tabs.getSelectedIndex() >= 0 ? tabs.getTitleAt(tabs.getSelectedIndex()) : "";
			QuestProfile profile = selected();
			switch (title)
			{
				case "Items & supplies":
					showItems(profile);
					break;
				case "Gear & combat":
					showCombat(profile);
					break;
				case "Purchases":
					showPurchases();
					break;
				case "Requirements plan":
					showPlan(profile);
					break;
				default:
					break;
			}
		}

		// ---- requirements plan ----

		private void showPlan(QuestProfile profile)
		{
			planHost.removeAll();
			if (profile == null)
			{
				planHost.add(new JLabel("  Add a quest to the queue first."), BorderLayout.NORTH);
				planHost.revalidate();
				planHost.repaint();
				return;
			}
			final String questName = profile.isStep() ? profile.getStepFor() : profile.getQuestName();
			planHost.add(new JLabel("  Working out what " + questName + " needs..."), BorderLayout.NORTH);
			planHost.revalidate();
			planHost.repaint();
			final int mine = ++planVersion;
			Thread thread = new Thread(() ->
			{
				QuestRequirementResolver.Plan plan;
				try
				{
					plan = QuestRequirementPlanner.plan(questName);
				}
				catch (RuntimeException e)
				{
					plan = null;
				}
				final QuestRequirementResolver.Plan result = plan;
				SwingUtilities.invokeLater(() ->
				{
					if (mine == planVersion)
					{
						buildPlan(questName, result);
					}
				});
			}, "quest-window-plan");
			thread.setDaemon(true);
			thread.start();
		}

		private void buildPlan(String questName, QuestRequirementResolver.Plan plan)
		{
			planHost.removeAll();
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
			panel.add(heading("Requirements plan: " + questName));
			if (plan == null)
			{
				panel.add(label("Could not read the requirements of this quest."));
			}
			else if (plan.targetDone || !plan.hasPrerequisites() && !plan.hasBlockers())
			{
				panel.add(label("Nothing to do first: the requirements are met."));
			}
			else
			{
				panel.add(label("These run in front of the quest, in this order. Untick a step to skip it "
					+ "(the quest may then fail its requirement)."));
				for (QuestRequirementResolver.Step step : plan.steps)
				{
					StringBuilder text = new StringBuilder();
					for (int i = 0; i < step.depth; i++)
					{
						text.append("    ");
					}
					text.append(step.label());
					if (step.etaMinutes > 0)
					{
						text.append("   ~").append(step.etaMinutes).append(" min");
					}
					if (!step.reason.isEmpty())
					{
						text.append("   (").append(step.reason).append(")");
					}
					JCheckBox allow = new JCheckBox(text.toString(), !step.skipped);
					allow.setAlignmentX(Component.LEFT_ALIGNMENT);
					allow.setEnabled(step.neededBy != null);
					final String key = step.key();
					allow.addActionListener(e -> QuestRequirementPlanner.setSkipped(key, !allow.isSelected()));
					panel.add(allow);
				}
				panel.add(label("Estimated total: about " + plan.totalEtaMinutes() + " min"));
				panel.add(label("Training buys its supplies bank first, then at the Grand Exchange, inside the same "
					+ "approval and the max spend per quest as the quests."));
			}
			if (plan != null && plan.hasBlockers())
			{
				panel.add(heading("Blockers (cannot be automated; this quest is left out)"));
				for (String blocker : plan.blockers)
				{
					JLabel warn = label(blocker);
					warn.setForeground(new Color(200, 30, 30));
					panel.add(warn);
				}
			}
			JScrollPane outer = new JScrollPane(panel);
			outer.getVerticalScrollBar().setUnitIncrement(16);
			planHost.add(outer, BorderLayout.CENTER);
			planHost.revalidate();
			planHost.repaint();
		}

		private void showItems(QuestProfile profile)
		{
			itemsHost.removeAll();
			if (profile == null)
			{
				itemsHost.add(new JLabel("  Add a quest to the queue first."), BorderLayout.NORTH);
				itemsHost.revalidate();
				itemsHost.repaint();
				return;
			}
			if (profile.isStep())
			{
				itemsHost.add(new JLabel("  " + profile.getQuestName() + " is a requirement step: see the "
					+ "Requirements plan tab."), BorderLayout.NORTH);
				itemsHost.revalidate();
				itemsHost.repaint();
				return;
			}
			itemsHost.add(new JLabel("  Loading " + profile.getQuestName() + "..."), BorderLayout.NORTH);
			itemsHost.revalidate();
			itemsHost.repaint();
			Thread thread = new Thread(() ->
			{
				QuestInfo info = infoFor(profile.getQuestName());
				SwingUtilities.invokeLater(() ->
				{
					if (profile == selected() && "Items & supplies".equals(tabs.getTitleAt(tabs.getSelectedIndex())))
					{
						buildItems(profile, info);
					}
				});
			}, "quest-window-items");
			thread.setDaemon(true);
			thread.start();
		}

		private QuestInfo infoFor(String name)
		{
			QuestInfo cached = infos.get(name);
			if (cached != null)
			{
				return cached;
			}
			QuestHelper helper = QuestHelperQuest.getByName(name);
			if (helper == null)
			{
				return null;
			}
			QuestInfo info = QuestInfo.build(helper, plugin.preflightNeedsFor(helper));
			infos.put(name, info);
			return info;
		}

		private void buildItems(QuestProfile profile, QuestInfo info)
		{
			itemsHost.removeAll();
			JPanel left = new JPanel();
			left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
			left.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

			QuestProfileEditor editor = new QuestProfileEditor(this::profileChanged, this::names,
				name -> profileOfName(name), p -> tabs.setSelectedIndex(tabs.indexOfTab("Gear & combat")),
				QuestCombatHandler::wornGear);

			left.add(heading("Required items"));
			DefaultTableModel model = new DefaultTableModel(
				new Object[] {"Item", "Qty", "On you", "In bank", "Status", "Est. cost"}, 0)
			{
				@Override
				public boolean isCellEditable(int row, int column)
				{
					return false;
				}
			};
			long cost = 0;
			if (info != null)
			{
				for (QuestInfo.ItemLine line : info.required)
				{
					model.addRow(new Object[] {line.name, line.quantity, line.onPlayer, line.inBank, line.status(),
						line.cost() > 0 ? gp(line.cost()) : ""});
					cost += line.cost();
				}
			}
			if (model.getRowCount() == 0)
			{
				model.addRow(new Object[] {"(nothing to buy or bring up front)", "", "", "", "", ""});
			}
			JTable table = new JTable(model);
			table.setFillsViewportHeight(true);
			JScrollPane tableScroll = new JScrollPane(table);
			tableScroll.setPreferredSize(new Dimension(480, 140));
			tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
			left.add(tableScroll);
			left.add(label(profile.geAllowed()
				? "Estimated cost of what is missing (bank first): " + gp(cost)
				: "Bank only: missing items are not bought."));

			left.add(heading("Recommended items (tick to bring)"));
			if (info != null)
			{
				for (QuestInfo.ItemLine line : info.recommended)
				{
					left.add(recommendedBox(line, profile, editor));
				}
			}

			left.add(heading("Not allowed / avoid"));
			if (info == null || info.restrictions.isEmpty())
			{
				left.add(label("Nothing found for this quest."));
			}
			else
			{
				for (QuestRestrictions.Restriction restriction : info.restrictions)
				{
					JLabel warning = label("<html><body style='width:440px'>" + describe(restriction) + "</body></html>");
					warning.setForeground(new Color(190, 120, 20));
					left.add(warning);
				}
			}

			JPanel templates = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
			templates.setAlignmentX(Component.LEFT_ALIGNMENT);
			JButton applyAll = new JButton("Use these supplies for every quest in the queue");
			applyAll.addActionListener(e ->
			{
				for (QuestProfile other : entries)
				{
					if (other != profile)
					{
						QuestProfile holder = new QuestProfile();
						holder.setSupplies(profile.getSupplies());
						other.setSupplies(holder.copy().getSupplies());
					}
				}
				status.setText("Supplies copied to every quest in the queue; override them per quest.");
			});
			JButton saveDefault = new JButton("Save as default for new " + profile.getType() + " quests");
			saveDefault.addActionListener(e ->
			{
				queue.setTemplate(profile.getType(), profile.getSupplies());
				status.setText("Saved as the default " + profile.getType() + " supplies.");
			});
			if (planner)
			{
				templates.add(applyAll);
			}
			templates.add(saveDefault);
			left.add(templates);

			JScrollPane leftScroll = new JScrollPane(left);
			leftScroll.getVerticalScrollBar().setUnitIncrement(16);
			editor.load(profile);
			JScrollPane rightScroll = new JScrollPane(editor);
			rightScroll.getVerticalScrollBar().setUnitIncrement(16);
			JPanel right = new JPanel(new BorderLayout());
			right.setBorder(BorderFactory.createTitledBorder("Your inventory layout, supplies and gear"));
			right.add(rightScroll, BorderLayout.CENTER);
			JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, right);
			split.setResizeWeight(0.45);
			itemsHost.add(split, BorderLayout.CENTER);
			itemsHost.revalidate();
			itemsHost.repaint();
		}

		private QuestProfile profileOfName(String name)
		{
			for (QuestProfile profile : entries)
			{
				if (profile.getQuestName().equals(name))
				{
					return profile;
				}
			}
			return queue.savedProfile(name);
		}

		private static String describe(QuestRestrictions.Restriction restriction)
		{
			String how = restriction.enforce ? " (banked before the steps that need it)" : " (warning only)";
			switch (restriction.kind)
			{
				case EMPTY_INVENTORY:
					return "Empty inventory needed: " + restriction.text + how;
				case NO_WEAPONS_OR_ARMOUR:
					return "No weapons or armour: " + restriction.text + how;
				case NO_FOOD:
					return "No food: " + restriction.text + how;
				case FREE_SLOTS:
					return restriction.slots + " free inventory slots needed: " + restriction.text + how;
				default:
					return "Do not bring " + restriction.item + ": " + restriction.text + how;
			}
		}

		private JCheckBox recommendedBox(QuestInfo.ItemLine line, QuestProfile profile, QuestProfileEditor editor)
		{
			QuestProfile.Supplies supplies = profile.getSupplies();
			boolean isStamina = line.name.toLowerCase(Locale.ROOT).startsWith("stamina");
			boolean ticked = isStamina ? supplies.isStaminaOn() && supplies.getStaminaQty() > 0
				: hasItem(supplies, line.name) || (supplies.getFood().equalsIgnoreCase(line.name) && supplies.getFoodQty() > 0);
			String text = line.name + " x" + line.quantity + (line.note.isEmpty() ? "" : "  -  " + line.note)
				+ (line.unitPrice > 0 ? "  (~" + gp((long) line.unitPrice * line.quantity) + ")" : "");
			JCheckBox box = new JCheckBox(text, ticked);
			box.setAlignmentX(Component.LEFT_ALIGNMENT);
			box.addActionListener(e ->
			{
				supplies.setCustomised(true);
				boolean isFood = line.name.equalsIgnoreCase("Lobster");
				if (isStamina)
				{
					supplies.setStaminaOn(box.isSelected());
					supplies.setStaminaQty(Math.max(line.quantity, supplies.getStaminaQty()));
				}
				else if (isFood)
				{
					supplies.setFood(box.isSelected() ? line.name : "");
					supplies.setFoodQty(box.isSelected() ? line.quantity : 0);
				}
				else if (box.isSelected())
				{
					if (!hasItem(supplies, line.name))
					{
						int id = line.ids.isEmpty() ? -1 : line.ids.get(0);
						supplies.getItems().add(new QuestCombatLoadout.InventoryItem(line.name, id, line.quantity, false));
					}
				}
				else
				{
					supplies.getItems().removeIf(item -> item.getName().equalsIgnoreCase(line.name));
				}
				profileChanged();
				editor.load(profile);
			});
			return box;
		}

		private static boolean hasItem(QuestProfile.Supplies supplies, String name)
		{
			for (QuestCombatLoadout.InventoryItem item : supplies.getItems())
			{
				if (item.getName().equalsIgnoreCase(name))
				{
					return true;
				}
			}
			return false;
		}

		// ---- gear and combat ----

		private void showCombat(QuestProfile profile)
		{
			combatHost.removeAll();
			if (profile == null)
			{
				combatHost.add(new JLabel("  Add a quest to the queue first."), BorderLayout.NORTH);
				combatHost.revalidate();
				combatHost.repaint();
				return;
			}
			if (profile.isStep())
			{
				combatHost.add(new JLabel("  A requirement step has no gear or combat setup."), BorderLayout.NORTH);
				combatHost.revalidate();
				combatHost.repaint();
				return;
			}
			String name = profile.getQuestName();
			JComponent cached = panelViews.get(name);
			if (cached != null)
			{
				combatHost.add(cached, BorderLayout.CENTER);
				combatHost.revalidate();
				combatHost.repaint();
				return;
			}
			combatHost.add(new JLabel("  Loading the combat setup for " + name + "..."), BorderLayout.NORTH);
			combatHost.revalidate();
			combatHost.repaint();
			Thread thread = new Thread(() ->
			{
				QuestHelper helper = QuestHelperQuest.getByName(name);
				QuestCombatSetupDialog.Context context = helper == null ? null : plugin.combatContextFor(helper);
				if (context != null && helper != null)
				{
					QuestCombatLoadout stored = QuestCombatStore.load(helper.getQuest().name());
					context.defaults = stored != null ? stored : QuestCombatStore.loadGlobal();
				}
				final QuestCombatSetupDialog.Context built = context;
				SwingUtilities.invokeLater(() ->
				{
					if (built == null)
					{
						return;
					}
					QuestCombatSetupDialog.Panel panel = new QuestCombatSetupDialog.Panel(built);
					JComponent view = panel.component(dialog);
					panels.put(name, panel);
					panelViews.put(name, view);
					if (profile == selected() && "Gear & combat".equals(tabs.getTitleAt(tabs.getSelectedIndex())))
					{
						showCombat(profile);
					}
				});
			}, "quest-window-combat");
			thread.setDaemon(true);
			thread.start();
		}

		// ---- purchases ----

		private void showPurchases()
		{
			purchasesHost.removeAll();
			purchasesHost.add(new JLabel("  Pricing what is missing (the bank is used first)..."), BorderLayout.NORTH);
			purchasesHost.revalidate();
			purchasesHost.repaint();
			final int mine = ++purchaseVersion;
			List<QuestProfile> snapshot = new ArrayList<>(entries);
			Thread thread = new Thread(() ->
			{
				QuestPlanSummary summary = summarise(snapshot);
				long coins = QuestInfo.coins();
				SwingUtilities.invokeLater(() ->
				{
					if (mine == purchaseVersion || true)
					{
						buildPurchases(snapshot, summary, coins);
					}
				});
			}, "quest-window-purchases");
			thread.setDaemon(true);
			thread.start();
		}

		private QuestPlanSummary summarise(List<QuestProfile> profiles)
		{
			List<QuestPlanSummary.Entry> summaryEntries = new ArrayList<>();
			int globalMin = QuestPlannerLauncher.globalMinStamina();
			for (QuestProfile profile : profiles)
			{
				if (profile.isStep())
				{
					continue;
				}
				QuestInfo info = infoFor(profile.getQuestName());
				List<QuestInfo.ItemLine> supplies = new ArrayList<>();
				for (QuestProfile.SupplyLine line : profile.getSupplies().buyable(profile.effectiveMinStamina(globalMin)))
				{
					supplies.add(QuestInfo.supplyLine(line));
				}
				summaryEntries.add(new QuestPlanSummary.Entry(profile, info == null ? new ArrayList<>() : info.required,
					supplies));
			}
			return QuestPlanSummary.of(summaryEntries);
		}

		private void buildPurchases(List<QuestProfile> snapshot, QuestPlanSummary summary, long coins)
		{
			purchasesHost.removeAll();
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
			panel.add(heading("Bought from the Grand Exchange before starting (bank first, only the shortfall)"));
			DefaultTableModel model = new DefaultTableModel(new Object[] {"Item", "Qty", "Est. each", "Est. total"}, 0)
			{
				@Override
				public boolean isCellEditable(int row, int column)
				{
					return false;
				}
			};
			for (QuestPurchasePlan.Line line : summary.lines())
			{
				model.addRow(new Object[] {line.name, line.quantity, gp(line.unitPrice), gp(line.total())});
			}
			if (model.getRowCount() == 0)
			{
				model.addRow(new Object[] {"(nothing to buy)", "", "", ""});
			}
			JTable table = new JTable(model);
			table.setFillsViewportHeight(true);
			JScrollPane scroll = new JScrollPane(table);
			scroll.setPreferredSize(new Dimension(640, 220));
			scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(scroll);
			panel.add(label("Estimated total: " + gp(summary.total()) + "    Coins on you and in the bank: " + gp(coins)));
			if (summary.total() > coins)
			{
				JLabel warn = label("Not enough coins for all of it: quests whose items cannot be bought are skipped.");
				warn.setForeground(new Color(200, 30, 30));
				panel.add(warn);
			}
			if (automated.isSelected() && summary.total() > (Integer) maxSpend.getValue())
			{
				JLabel warn = label("Over the max spend per quest: Automated would skip quests that cost more.");
				warn.setForeground(new Color(200, 120, 20));
				panel.add(warn);
			}
			panel.add(heading("Per quest"));
			for (QuestProfile profile : snapshot)
			{
				panel.add(label(profile.getQuestName() + " - " + profile.getType()
					+ (profile.geAllowed() ? "" : " (bank only)")));
			}
			panel.add(heading("Start"));
			panel.add(label("Start saves everything and approves these purchases for the run; the shopping list above "
				+ "is the only thing that will be bought."));
			JScrollPane outer = new JScrollPane(panel);
			outer.getVerticalScrollBar().setUnitIncrement(16);
			purchasesHost.add(outer, BorderLayout.CENTER);
			purchasesHost.revalidate();
			purchasesHost.repaint();
		}

		// ---- start ----

		private void startPressed(boolean withoutBuying)
		{
			if (entries.isEmpty() && !(planner && random.isSelected()))
			{
				status.setText("Add at least one quest.");
				return;
			}
			// the combat tab of every quest that was opened is read now; a refused warning keeps the window open
			Map<String, QuestCombatLoadout> fromPanels = new LinkedHashMap<>();
			for (Map.Entry<String, QuestCombatSetupDialog.Panel> entry : panels.entrySet())
			{
				QuestCombatLoadout loadout = entry.getValue().loadout();
				if (loadout == null)
				{
					status.setText("Fix the warning on the Gear & combat tab of " + entry.getKey());
					return;
				}
				fromPanels.put(entry.getKey(), loadout);
			}
			start.setEnabled(false);
			startWithoutBuying.setEnabled(false);
			status.setText("Saving and pricing...");
			final boolean randomMode = planner && random.isSelected();
			final boolean poolIsEverything = randomMode && !restrictPool.isSelected();
			List<QuestProfile> profiles = new ArrayList<>(entries);
			saveOptions();
			Thread thread = new Thread(() ->
			{
				QuestPlanSummary summary = summarise(profiles);
				int globalMin = QuestPlannerLauncher.globalMinStamina();
				Map<String, QuestCombatLoadout> loadouts = new LinkedHashMap<>();
				for (QuestProfile profile : profiles)
				{
					QuestHelper helper = QuestHelperQuest.getByName(profile.getQuestName());
					if (helper == null)
					{
						continue;
					}
					String key = helper.getQuest().name();
					QuestCombatLoadout base = fromPanels.get(profile.getQuestName());
					if (base == null)
					{
						base = QuestCombatStore.load(key);
					}
					loadouts.put(profile.getQuestName(), profile.loadoutFor(base, QuestCombatStore.loadGlobal(), globalMin));
				}
				boolean auto = automated.isSelected();
				QuestApproval approval = auto || poolIsEverything ? QuestApproval.everything() : summary.approval();
				SwingUtilities.invokeLater(() ->
				{
					persist(profiles, loadouts, auto);
					finish(new Result(profiles, loadouts, approval, auto, withoutBuying));
					dialog.dispose();
				});
			}, "quest-window-start");
			thread.setDaemon(true);
			thread.start();
		}

		private void saveOptions()
		{
			QuestPlannerLauncher.setGlobalMinStamina(defaultStamina.getSelectedIndex());
			if (planner)
			{
				queue.setMode(random.isSelected() ? QuestQueue.Mode.RANDOM : QuestQueue.Mode.FIXED);
				queue.setPause((Integer) pauseMin.getValue(), (Integer) pauseMax.getValue());
				queue.setPool(random.isSelected() && restrictPool.isSelected() ? names() : new ArrayList<>());
			}
		}

		private void persist(List<QuestProfile> profiles, Map<String, QuestCombatLoadout> loadouts, boolean auto)
		{
			for (QuestProfile profile : profiles)
			{
				if (profile.isStep())
				{
					continue;
				}
				profile.setReviewed(true);
				queue.saveProfile(profile);
				QuestHelper helper = QuestHelperQuest.getByName(profile.getQuestName());
				QuestCombatLoadout loadout = loadouts.get(profile.getQuestName());
				if (helper != null && loadout != null)
				{
					QuestCombatStore.save(helper.getQuest().name(), loadout);
					QuestCombatStore.saveGlobal(loadout);
				}
			}
			net.runelite.client.config.ConfigManager manager = Microbot.getConfigManager();
			if (manager != null)
			{
				manager.setConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "fullyAutomated", auto);
				manager.setConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "maxSpendPerQuest", (Integer) maxSpend.getValue());
			}
		}

		// ---- small helpers ----

		private static boolean configBool(String key, boolean fallback)
		{
			net.runelite.client.config.ConfigManager manager = Microbot.getConfigManager();
			String value = manager == null ? null : manager.getConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, key);
			return value == null ? fallback : "true".equals(value);
		}

		private static int configInt(String key, int fallback)
		{
			net.runelite.client.config.ConfigManager manager = Microbot.getConfigManager();
			String value = manager == null ? null : manager.getConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, key);
			try
			{
				return value == null ? fallback : Integer.parseInt(value.trim());
			}
			catch (NumberFormatException e)
			{
				return fallback;
			}
		}

		private static JLabel heading(String text)
		{
			JLabel label = new JLabel(text);
			label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
			label.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			return label;
		}

		private static JLabel label(String text)
		{
			JLabel label = new JLabel(text);
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			return label;
		}

		private static String gp(long value)
		{
			return String.format(Locale.ROOT, "%,d gp", value);
		}
	}
}
