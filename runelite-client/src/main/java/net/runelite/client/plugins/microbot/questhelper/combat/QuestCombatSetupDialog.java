package net.runelite.client.plugins.microbot.questhelper.combat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.FightSetup;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.GearItem;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.GearSpec;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.InventoryItem;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.Style;

/**
 * Asks what to wear, bring and how to fight for a quest: gear, food, potions, prayer, stamina, extra inventory
 * items, free slots for quest items and a style and gear per fight. Swing, EDT only; game state is read by the caller
 * into a {@link Context} beforehand.
 */
public final class QuestCombatSetupDialog
{
	public enum Outcome
	{
		SAVE, SKIP,
		/** The user pressed Cancel or the window X. Only this stops the quest. */
		CANCEL,
		/** Closed by code (stale dialog, reset); carries no user decision. */
		DISMISSED
	}

	public static final class Result
	{
		public final Outcome outcome;
		public final QuestCombatLoadout loadout;

		Result(Outcome outcome, QuestCombatLoadout loadout)
		{
			this.outcome = outcome;
			this.loadout = loadout;
		}
	}

	/** Everything the dialog shows, gathered off the EDT. */
	public static final class Context
	{
		public String questName = "";
		public boolean hasFights;
		public List<String> notes = new ArrayList<>();
		public List<QuestCombatDetector.Fight> fights = new ArrayList<>();
		public List<GearItem> wornGear = new ArrayList<>();
		public String wornWeapon = "";
		public List<InventoryItem> inventory = new ArrayList<>();
		public int defaultReserve = 4;
	}

	private enum Kind
	{
		SAME_AS_MAIN, WORN, SETUP, CUSTOM
	}

	private static final class GearChoice
	{
		final Kind kind;
		final String setup;
		List<GearItem> items;

		GearChoice(Kind kind, String setup, List<GearItem> items)
		{
			this.kind = kind;
			this.setup = setup;
			this.items = items;
		}

		@Override
		public String toString()
		{
			switch (kind)
			{
				case SAME_AS_MAIN:
					return "Same as main";
				case WORN:
					return "Use what I'm wearing now";
				case SETUP:
					return "Inventory Setup: " + setup;
				default:
					return items.isEmpty() ? "Custom items..." : "Custom items (" + items.size() + ")...";
			}
		}
	}

	private static volatile JDialog openDialog;
	/** Bumped by {@link #dismissOpen()}; a dialog request made under an older epoch is stale. */
	private static final java.util.concurrent.atomic.AtomicInteger EPOCH = new java.util.concurrent.atomic.AtomicInteger();
	private static volatile int openEpoch;

	private QuestCombatSetupDialog()
	{
	}

	/** Names of the user's Inventory Setups; safe when the setups plugin has not loaded any. */
	public static List<String> setupNames()
	{
		List<String> names = new ArrayList<>();
		List<InventorySetup> setups = MInventorySetupsPlugin.getInventorySetups();
		if (setups != null)
		{
			for (InventorySetup setup : new ArrayList<>(setups))
			{
				if (setup != null && setup.getName() != null)
				{
					names.add(setup.getName());
				}
			}
		}
		return names;
	}

	/** Closes a dialog left open by an earlier run so a stale one can never block the next prompt. */
	public static void dismissOpen()
	{
		final int epoch = EPOCH.incrementAndGet();
		SwingUtilities.invokeLater(() ->
		{
			JDialog stale = openDialog;
			// only dialogs opened before this dismissal; never one opened by a later prompt
			if (stale != null && openEpoch < epoch)
			{
				stale.dispose();
			}
		});
	}

	/** Shows the dialog on the EDT and hands the answer to {@code callback}; never blocks the caller. */
	public static void showAsync(Context context, Consumer<Result> callback)
	{
		final int requestEpoch = EPOCH.get();
		SwingUtilities.invokeLater(() ->
		{
			Result result = new Result(Outcome.DISMISSED, null);
			try
			{
				if (requestEpoch != EPOCH.get())
				{
					return; // stale request; finally reports DISMISSED
				}
				JDialog stale = openDialog;
				if (stale != null)
				{
					stale.dispose();
				}
				result = new Form(context, requestEpoch).show();
			}
			catch (RuntimeException e)
			{
				net.runelite.client.plugins.microbot.Microbot.log("Quest helper: setup dialog failed: " + e,
					org.slf4j.event.Level.ERROR);
				result = new Result(Outcome.CANCEL, null);
			}
			finally
			{
				callback.accept(result);
			}
		});
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

	private static final class Form
	{
		private final Context ctx;
		private final List<String> setups = setupNames();
		private final List<Component> combatOnly = new ArrayList<>();
		private boolean adjusting;

		private JDialog dialog;
		private final int epoch;
		private Result answer = new Result(Outcome.DISMISSED, null);

		private GearPicker mainGear;
		private JComboBox<Style> style;
		private JLabel detected = new JLabel();
		private JTextField food;
		private JSpinner foodQty;
		private JSpinner eatAt;
		private JComboBox<QuestCombatLoadout.Potion> potion;
		private JSpinner potionQty;
		private JComboBox<QuestCombatLoadout.Prayer> prayer;
		private JSpinner drinkAt;
		private JComboBox<QuestCombatLoadout.StaminaType> staminaType;
		private JSpinner staminaQty;
		private JSpinner staminaAt;
		private JSpinner reserve;
		private DefaultTableModel itemModel;
		private JTable itemTable;
		private JLabel slots = new JLabel();
		private final List<FightRow> rows = new ArrayList<>();

		Form(Context ctx, int epoch)
		{
			this.ctx = ctx;
			this.epoch = epoch;
		}

		Result show()
		{
			dialog = new JDialog(clientFrame(), ctx.questName + " - Quest setup", true);
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

			JTabbedPane tabs = new JTabbedPane();
			tabs.addTab("Gear & style", gearTab());
			tabs.addTab("Supplies", suppliesTab());
			tabs.addTab("Inventory items", itemsTab());
			if (!ctx.fights.isEmpty())
			{
				tabs.addTab("Fights (" + ctx.fights.size() + ")", fightsTab());
			}
			if (!ctx.hasFights)
			{
				tabs.setSelectedIndex(1);
			}

			JButton save = new JButton("Save & start");
			JButton skip = new JButton("Skip combat setup");
			JButton cancel = new JButton("Cancel");
			skip.setEnabled(ctx.hasFights);
			save.addActionListener(e -> finish(Outcome.SAVE));
			skip.addActionListener(e -> finish(Outcome.SKIP));
			cancel.addActionListener(e ->
			{
				answer = new Result(Outcome.CANCEL, null);
				dialog.dispose();
			});

			JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
			buttons.add(save);
			buttons.add(skip);
			buttons.add(cancel);
			JPanel south = new JPanel(new BorderLayout());
			slots.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));
			south.add(slots, BorderLayout.WEST);
			south.add(buttons, BorderLayout.EAST);

			dialog.getContentPane().add(tabs, BorderLayout.CENTER);
			dialog.getContentPane().add(south, BorderLayout.SOUTH);
			dialog.getRootPane().setDefaultButton(save);
			dialog.setPreferredSize(new Dimension(820, 580));
			dialog.pack();
			dialog.setLocationRelativeTo(dialog.getOwner());

			if (!ctx.hasFights)
			{
				combatOnly.forEach(component -> component.setEnabled(false));
			}
			refresh();

			dialog.addWindowListener(new WindowAdapter()
			{
				@Override
				public void windowClosing(WindowEvent e)
				{
					// fired only by the user's window X, never by dispose()
					answer = new Result(Outcome.CANCEL, null);
				}

				@Override
				public void windowOpened(WindowEvent e)
				{
					dialog.toFront();
					dialog.requestFocus();
					Timer timer = new Timer(2500, ev -> dialog.setAlwaysOnTop(false));
					timer.setRepeats(false);
					timer.start();
				}

				@Override
				public void windowClosed(WindowEvent e)
				{
					if (openDialog == dialog)
					{
						openDialog = null;
					}
				}
			});
			dialog.setAlwaysOnTop(true);
			openDialog = dialog;
			openEpoch = epoch;
			dialog.setVisible(true);
			return answer;
		}

		// ---- tabs ----

		private JPanel gearTab()
		{
			JPanel panel = new JPanel(new GridBagLayout());
			int row = 0;
			if (ctx.hasFights)
			{
				String notes = ctx.notes.isEmpty() ? "This quest lists no combat notes." : String.join("\n", ctx.notes);
				JTextArea notesArea = new JTextArea(notes, 4, 40);
				notesArea.setEditable(false);
				notesArea.setLineWrap(true);
				notesArea.setWrapStyleWord(true);
				row = addRow(panel, row, "Quest combat notes", new JScrollPane(notesArea));
			}
			else
			{
				row = addRow(panel, row, "Combat", new JLabel("This quest has no fights, so combat settings are off."));
			}
			mainGear = new GearPicker(false);
			row = addRow(panel, row, "Gear", mainGear.combo);
			combatOnly.add(mainGear.combo);
			style = new JComboBox<>(Style.values());
			style.setSelectedItem(Style.AUTO);
			style.addActionListener(e -> onMainStyleChanged());
			row = addRow(panel, row, "Combat style", style);
			combatOnly.add(style);
			row = addRow(panel, row, "Worn weapon", detected);
			filler(panel, row);
			return panel;
		}

		private JPanel suppliesTab()
		{
			JPanel panel = new JPanel(new GridBagLayout());
			int row = 0;
			food = new JTextField("Lobster", 14);
			foodQty = spinner(10, 0, 27, 1);
			eatAt = spinner(50, 1, 99, 5);
			potion = new JComboBox<>(QuestCombatLoadout.Potion.values());
			potionQty = spinner(2, 0, 27, 1);
			prayer = new JComboBox<>(QuestCombatLoadout.Prayer.values());
			drinkAt = spinner(20, 1, 98, 1);
			staminaType = new JComboBox<>(QuestCombatLoadout.StaminaType.values());
			staminaType.setSelectedItem(QuestCombatLoadout.StaminaType.NONE);
			staminaQty = spinner(2, 0, 27, 1);
			staminaAt = spinner(30, 1, 99, 5);
			reserve = spinner(ctx.defaultReserve, 0, 27, 1);

			row = addRow(panel, row, "Food", food);
			row = addRow(panel, row, "Food quantity", foodQty);
			row = addRow(panel, row, "Eat at HP %", eatAt);
			row = addRow(panel, row, "Potions", potion);
			row = addRow(panel, row, "Potion quantity", potionQty);
			row = addRow(panel, row, "Prayer", prayer);
			row = addRow(panel, row, "Drink at prayer points", drinkAt);
			combatOnly.addAll(java.util.Arrays.asList(food, foodQty, eatAt, potion, potionQty, prayer, drinkAt));
			row = addRow(panel, row, "Run energy potions", staminaType);
			row = addRow(panel, row, "Potion quantity (stamina/energy)", staminaQty);
			row = addRow(panel, row, "Drink at run energy %", staminaAt);
			row = addRow(panel, row, "Keep free slots for quest items", reserve);
			filler(panel, row);
			for (JSpinner s : new JSpinner[]{foodQty, eatAt, potionQty, drinkAt, staminaQty, staminaAt, reserve})
			{
				s.addChangeListener(e -> refresh());
			}
			for (JComboBox<?> c : new JComboBox<?>[]{potion, prayer, staminaType})
			{
				c.addActionListener(e -> refresh());
			}
			food.addActionListener(e -> refresh());
			return panel;
		}

		private JPanel itemsTab()
		{
			JPanel panel = new JPanel(new BorderLayout());
			itemModel = new DefaultTableModel(new Object[]{"Item name or id", "Quantity", "Stackable (1 slot)"}, 0)
			{
				@Override
				public Class<?> getColumnClass(int column)
				{
					return column == 1 ? Integer.class : column == 2 ? Boolean.class : String.class;
				}
			};
			itemTable = new JTable(itemModel);
			itemModel.addTableModelListener(e -> refresh());

			JButton add = new JButton("Add row");
			add.addActionListener(e -> itemModel.addRow(new Object[]{"", 1, Boolean.FALSE}));
			JButton remove = new JButton("Remove row");
			remove.addActionListener(e ->
			{
				int selected = itemTable.getSelectedRow();
				if (selected >= 0)
				{
					itemModel.removeRow(selected);
				}
			});
			JButton snapshot = new JButton("Snapshot my current inventory");
			snapshot.setToolTipText("Fills the list from your inventory, leaving out items this quest needs");
			snapshot.addActionListener(e ->
			{
				itemModel.setRowCount(0);
				for (InventoryItem item : ctx.inventory)
				{
					itemModel.addRow(new Object[]{item.getId() > 0 && item.getName().isEmpty()
						? String.valueOf(item.getId()) : item.getName(), item.getQty(), item.isStackable()});
				}
			});
			JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
			bar.add(add);
			bar.add(remove);
			bar.add(snapshot);
			JLabel hint = new JLabel("Withdrawn in the supply bank trip at the start of the quest, after the free slots above.");
			panel.add(hint, BorderLayout.NORTH);
			panel.add(new JScrollPane(itemTable), BorderLayout.CENTER);
			panel.add(bar, BorderLayout.SOUTH);
			return panel;
		}

		private JScrollPane fightsTab()
		{
			JPanel panel = new JPanel(new GridBagLayout());
			addCell(panel, 0, 0, new JLabel("Fight"), 0);
			addCell(panel, 1, 0, new JLabel("Restriction"), 1);
			addCell(panel, 2, 0, new JLabel("Style"), 0);
			addCell(panel, 3, 0, new JLabel("Gear"), 0);
			int y = 1;
			for (QuestCombatDetector.Fight fight : ctx.fights)
			{
				FightRow fightRow = new FightRow(fight);
				rows.add(fightRow);
				addCell(panel, 0, y, new JLabel(fight.label), 0);
				JTextArea note = new JTextArea(String.join(" ", fight.notes), 3, 28);
				note.setEditable(false);
				note.setLineWrap(true);
				note.setWrapStyleWord(true);
				addCell(panel, 1, y, new JScrollPane(note), 1);
				addCell(panel, 2, y, fightRow.style, 0);
				addCell(panel, 3, y, fightRow.gear.combo, 0);
				addCell(panel, 1, y + 1, fightRow.warning, 1);
				y += 2;
			}
			JPanel wrap = new JPanel(new BorderLayout());
			wrap.add(panel, BorderLayout.NORTH);
			JScrollPane scroll = new JScrollPane(wrap);
			combatOnly.add(panel);
			return scroll;
		}

		// ---- behaviour ----

		private final class FightRow
		{
			final QuestCombatDetector.Fight fight;
			final JComboBox<Style> style = new JComboBox<>(Style.values());
			final GearPicker gear = new GearPicker(true);
			final JLabel warning = new JLabel(" ");
			boolean touched;

			FightRow(QuestCombatDetector.Fight fight)
			{
				this.fight = fight;
				style.setSelectedItem(QuestCombatLoadout.defaultStyle(Style.AUTO, fight.restrictionTexts));
				warning.setForeground(new Color(190, 40, 40));
				style.addActionListener(e ->
				{
					if (!adjusting)
					{
						touched = true;
					}
					refresh();
				});
			}

			Style effective()
			{
				Style chosen = (Style) style.getSelectedItem();
				if (chosen != Style.AUTO)
				{
					return chosen;
				}
				return detect(gear.spec(), mainGear.spec());
			}

			boolean forbidden()
			{
				Style s = effective();
				return s != null && QuestCombatLoadout.forbiddenStyles(fight.restrictionTexts).contains(s);
			}
		}

		private void onMainStyleChanged()
		{
			adjusting = true;
			try
			{
				Style global = (Style) style.getSelectedItem();
				for (FightRow row : rows)
				{
					if (!row.touched)
					{
						row.style.setSelectedItem(QuestCombatLoadout.defaultStyle(global, row.fight.restrictionTexts));
					}
				}
			}
			finally
			{
				adjusting = false;
			}
			refresh();
		}

		/** Style of the weapon a gear choice would put on, falling back to the main gear and then what is worn now. */
		private Style detect(GearSpec spec, GearSpec main)
		{
			for (GearSpec candidate : new GearSpec[]{spec, main})
			{
				if (!candidate.getSetup().isEmpty())
				{
					return null;
				}
				for (GearItem item : candidate.getItems())
				{
					if ("weapon".equals(item.getSlot()))
					{
						return QuestCombatLoadout.styleOfWeapon(item.getName());
					}
				}
			}
			return QuestCombatLoadout.styleOfWeapon(ctx.wornWeapon);
		}

		private void refresh()
		{
			if (mainGear == null || slots == null || reserve == null || itemModel == null || potion == null || staminaType == null)
			{
				return;
			}
			Style mainDetected = detect(mainGear.spec(), new GearSpec());
			detected.setText((ctx.wornWeapon.isEmpty() ? "nothing wielded" : ctx.wornWeapon) + " - "
				+ (mainDetected == null ? "set by the Inventory Setup" : mainDetected.toString().replace(" (from worn weapon)", "")));

			int supplies = 0;
			if (ctx.hasFights)
			{
				supplies += !food.getText().trim().isEmpty() ? (Integer) foodQty.getValue() : 0;
				supplies += potion.getSelectedItem() != QuestCombatLoadout.Potion.NONE ? (Integer) potionQty.getValue() : 0;
			}
			if (staminaType.getSelectedItem() != QuestCombatLoadout.StaminaType.NONE)
			{
				supplies += (Integer) staminaQty.getValue();
			}
			for (InventoryItem item : readItems())
			{
				supplies += item.isStackable() ? 1 : item.getQty();
			}
			int reserved = (Integer) reserve.getValue();
			int total = supplies + reserved;
			slots.setText("Slots used: " + supplies + " supplies + " + reserved + " reserved for quest = " + total
				+ " / 28" + (total > 28 ? "  (too many: lower a quantity)" : ""));
			slots.setForeground(total > 28 ? new Color(190, 40, 40) : UIColor.normal());

			for (FightRow row : rows)
			{
				if (row.forbidden())
				{
					row.warning.setText("Warning: " + row.effective().toString().replace(" (from worn weapon)", "")
						+ " does not work against this fight.");
				}
				else
				{
					row.warning.setText(" ");
				}
			}
		}

		private List<InventoryItem> readItems()
		{
			List<InventoryItem> out = new ArrayList<>();
			if (itemTable != null && itemTable.isEditing())
			{
				itemTable.getCellEditor().stopCellEditing();
			}
			for (int i = 0; i < itemModel.getRowCount(); i++)
			{
				String name = String.valueOf(itemModel.getValueAt(i, 0)).trim();
				if (name.isEmpty() || "null".equals(name))
				{
					continue;
				}
				InventoryItem item = new InventoryItem();
				if (name.matches("\\d+"))
				{
					item.setId(Integer.parseInt(name));
				}
				else
				{
					item.setName(name);
				}
				Object qty = itemModel.getValueAt(i, 1);
				item.setQty(qty instanceof Number ? ((Number) qty).intValue() : 1);
				item.setStackable(Boolean.TRUE.equals(itemModel.getValueAt(i, 2)));
				out.add(item);
			}
			return out;
		}

		private void finish(Outcome outcome)
		{
			QuestCombatLoadout loadout = build();

			if (ctx.hasFights && outcome == Outcome.SAVE)
			{
				if (loadout.getStyle() == Style.MAGIC && QuestCombatLoadout.mentionsMagicImmunity(ctx.notes)
					&& !confirm("The quest notes say magic does not work against these fights.\nUse Magic anyway?"))
				{
					return;
				}
				for (FightRow row : rows)
				{
					if (row.forbidden() && !confirm(row.fight.label + ": "
						+ row.effective().toString().replace(" (from worn weapon)", "")
						+ " does not work against it (" + String.join(" ", row.fight.notes)
						+ ").\nUse it anyway?"))
					{
						return;
					}
				}
			}
			if (outcome == Outcome.SKIP)
			{
				loadout.setSkip(true);
				loadout.setGearSetup("");
				loadout.setGearItems(new ArrayList<>());
				loadout.setFoodQty(0);
				loadout.setPotion(QuestCombatLoadout.Potion.NONE);
				loadout.setPotionQty(0);
				loadout.setPrayer(QuestCombatLoadout.Prayer.OFF);
				loadout.setFights(new java.util.LinkedHashMap<>());
			}
			answer = new Result(outcome, loadout.normalised());
			dialog.dispose();
		}

		private boolean confirm(String message)
		{
			return JOptionPane.showConfirmDialog(dialog, message, "Check this fight", JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
		}

		private QuestCombatLoadout build()
		{
			QuestCombatLoadout loadout = new QuestCombatLoadout();
			if (ctx.hasFights)
			{
				GearSpec gear = mainGear.spec();
				loadout.setGearSetup(gear.getSetup());
				loadout.setGearItems(gear.getItems());
				loadout.setFood(food.getText());
				loadout.setFoodQty((Integer) foodQty.getValue());
				loadout.setEatAtPercent((Integer) eatAt.getValue());
				loadout.setPotion((QuestCombatLoadout.Potion) potion.getSelectedItem());
				loadout.setPotionQty((Integer) potionQty.getValue());
				loadout.setPrayer((QuestCombatLoadout.Prayer) prayer.getSelectedItem());
				loadout.setDrinkAtPrayer((Integer) drinkAt.getValue());
				loadout.setStyle((Style) style.getSelectedItem());
				for (FightRow row : rows)
				{
					FightSetup setup = new FightSetup();
					setup.setStyle((Style) row.style.getSelectedItem());
					GearSpec own = row.gear.spec();
					setup.setGearSetup(own.getSetup());
					setup.setGearItems(own.getItems());
					loadout.getFights().put(row.fight.key, setup);
				}
			}
			else
			{
				loadout.setSkip(true);
				loadout.setFoodQty(0);
				loadout.setPotionQty(0);
			}
			QuestCombatLoadout.StaminaType type = (QuestCombatLoadout.StaminaType) staminaType.getSelectedItem();
			loadout.setUseStamina(type != QuestCombatLoadout.StaminaType.NONE);
			if (type != QuestCombatLoadout.StaminaType.NONE)
			{
				loadout.setStaminaType(type);
			}
			loadout.setStaminaQty((Integer) staminaQty.getValue());
			loadout.setDrinkStaminaAtEnergy((Integer) staminaAt.getValue());
			loadout.setInventoryItems(readItems());
			loadout.setReserveSlots((Integer) reserve.getValue());
			return loadout;
		}

		// ---- gear picker ----

		private final class GearPicker
		{
			final JComboBox<GearChoice> combo = new JComboBox<>();
			private final GearChoice custom = new GearChoice(Kind.CUSTOM, "", new ArrayList<>());
			private final GearChoice worn = new GearChoice(Kind.WORN, "", ctx.wornGear);
			private Object previous;

			GearPicker(boolean allowSame)
			{
				GearChoice same = new GearChoice(Kind.SAME_AS_MAIN, "", Collections.emptyList());
				if (allowSame)
				{
					combo.addItem(same);
				}
				combo.addItem(worn);
				for (String name : setups)
				{
					combo.addItem(new GearChoice(Kind.SETUP, name, Collections.emptyList()));
				}
				combo.addItem(custom);
				combo.setSelectedItem(allowSame ? same : worn);
				previous = combo.getSelectedItem();
				combo.addActionListener(e ->
				{
					Object selected = combo.getSelectedItem();
					if (selected == custom)
					{
						List<GearItem> edited = editGear(custom.items);
						if (edited == null)
						{
							combo.setSelectedItem(previous);
							return;
						}
						custom.items = edited;
						combo.repaint();
					}
					previous = combo.getSelectedItem();
					refresh();
				});
			}

			GearSpec spec()
			{
				GearChoice choice = (GearChoice) combo.getSelectedItem();
				if (choice == null)
				{
					return new GearSpec();
				}
				switch (choice.kind)
				{
					case WORN:
						return new GearSpec("", ctx.wornGear);
					case SETUP:
						return new GearSpec(choice.setup, null);
					case CUSTOM:
						return new GearSpec("", choice.items);
					default:
						return new GearSpec();
				}
			}
		}

		private List<GearItem> editGear(List<GearItem> initial)
		{
			DefaultTableModel model = new DefaultTableModel(new Object[]{"Slot", "Item name or id"}, 0);
			for (GearItem item : initial)
			{
				model.addRow(new Object[]{item.getSlot(), item.getId() > 0 && item.getName().isEmpty()
					? String.valueOf(item.getId()) : item.getName()});
			}
			JTable table = new JTable(model);
			table.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(
				new JComboBox<>(QuestCombatLoadout.SLOT_NAMES.toArray(new String[0]))));
			JButton add = new JButton("Add item");
			add.addActionListener(e -> model.addRow(new Object[]{"weapon", ""}));
			JButton remove = new JButton("Remove item");
			remove.addActionListener(e ->
			{
				int selected = table.getSelectedRow();
				if (selected >= 0)
				{
					model.removeRow(selected);
				}
			});
			JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
			bar.add(add);
			bar.add(remove);
			JPanel panel = new JPanel(new BorderLayout());
			panel.add(new JScrollPane(table), BorderLayout.CENTER);
			panel.add(bar, BorderLayout.SOUTH);
			panel.setPreferredSize(new Dimension(420, 260));
			if (JOptionPane.showConfirmDialog(dialog, panel, "Custom gear", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
			{
				return null;
			}
			if (table.isEditing())
			{
				table.getCellEditor().stopCellEditing();
			}
			List<GearItem> out = new ArrayList<>();
			for (int i = 0; i < model.getRowCount(); i++)
			{
				String text = String.valueOf(model.getValueAt(i, 1)).trim();
				if (text.isEmpty() || "null".equals(text))
				{
					continue;
				}
				String slot = String.valueOf(model.getValueAt(i, 0));
				if (text.matches("\\d+"))
				{
					out.add(new GearItem(Integer.parseInt(text), "", slot, 1));
				}
				else
				{
					out.add(new GearItem(-1, text, slot, 1));
				}
			}
			return out;
		}
	}

	private static final class UIColor
	{
		static Color normal()
		{
			Color color = javax.swing.UIManager.getColor("Label.foreground");
			return color == null ? Color.BLACK : color;
		}
	}

	private static JSpinner spinner(int value, int min, int max, int step)
	{
		return new JSpinner(new SpinnerNumberModel(value, min, max, step));
	}

	private static void filler(Container panel, int row)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = row;
		c.weighty = 1;
		panel.add(new JPanel(), c);
	}

	private static void addCell(JPanel panel, int x, int y, Component component, double weightx)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = x;
		c.gridy = y;
		c.weightx = weightx;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTHWEST;
		c.insets = new Insets(3, 3, 3, 6);
		panel.add(component, c);
	}

	private static int addRow(JPanel panel, int row, String label, Component field)
	{
		GridBagConstraints left = new GridBagConstraints();
		left.gridx = 0;
		left.gridy = row;
		left.anchor = GridBagConstraints.WEST;
		left.insets = new Insets(3, 6, 3, 8);
		panel.add(new JLabel(label), left);

		GridBagConstraints right = new GridBagConstraints();
		right.gridx = 1;
		right.gridy = row;
		right.fill = GridBagConstraints.HORIZONTAL;
		right.weightx = 1;
		right.insets = new Insets(3, 2, 3, 6);
		panel.add(field, right);
		return row + 1;
	}
}
