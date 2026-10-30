package net.runelite.client.plugins.microbot.questhelper.planner;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestPotions;

/**
 * Edits one {@link QuestProfile}: where items come from, Travel or Combat, the worn gear, and the quest's own
 * supplies (stamina, energy fallback, food, potions, teleports and runes), each with a "buy from the Grand Exchange"
 * switch. Swing, EDT only. Every change is written straight into the profile and reported to {@code onChange}.
 */
public final class QuestProfileEditor extends JPanel
{
	private QuestProfile profile;
	private boolean loading;
	private final Runnable onChange;
	private final Supplier<List<String>> otherQuests;
	private final Function<String, QuestProfile> profileOf;
	private final Consumer<QuestProfile> combatEditor;
	private final Supplier<List<QuestCombatLoadout.GearItem>> wornGear;
	/** Only the supplies section: no obtain/type/gear/copy rows (the purchase dialog and the defaults page). */
	private final boolean suppliesOnly;
	private final List<Component> profileRows = new ArrayList<>();

	private final JComboBox<QuestProfile.Obtain> obtain = new JComboBox<>(QuestProfile.Obtain.values());
	private final JComboBox<QuestProfile.Type> type = new JComboBox<>(QuestProfile.Type.values());
	private final JCheckBox bankForbidden = new JCheckBox("Bank items the quest forbids before the steps that need it");
	private final DefaultListModel<String> gearModel = new DefaultListModel<>();
	private final JList<String> gearList = new JList<>(gearModel);
	private final JPanel gearBox = new JPanel(new BorderLayout());
	private final JPanel combatBox = new JPanel(new FlowLayout(FlowLayout.LEFT));

	private final JCheckBox staminaOn = new JCheckBox("Stamina potions");
	private final JSpinner staminaQty = spinner(2, 0, 27);
	private final JSpinner drinkAt = spinner(30, 1, 99);
	private final JComboBox<String> minStamina = new JComboBox<>(
		new String[] {"Global default", "0", "1", "2", "3", "4"});
	private final JCheckBox energyOn = new JCheckBox("Energy potions as fallback");
	private final JSpinner energyQty = spinner(2, 0, 27);
	private final JTextField food = new JTextField(12);
	private final JSpinner foodQty = spinner(0, 0, 27);
	private final JSpinner eatAt = spinner(0, 0, 99);
	private final Map<QuestPotions.Kind, JSpinner> potionQty = new LinkedHashMap<>();
	private final DefaultListModel<String> itemModel = new DefaultListModel<>();
	private final JList<String> itemList = new JList<>(itemModel);
	private final JTextField itemName = new JTextField(12);
	private final JSpinner itemQty = spinner(1, 1, 1000);
	private final Map<String, JCheckBox> buyBoxes = new LinkedHashMap<>();
	private final JCheckBox buySelectedItem = new JCheckBox("Buy selected item from the Grand Exchange");
	private final JComboBox<String> copyFrom = new JComboBox<>();

	/** A supplies-only editor. */
	public static QuestProfileEditor suppliesOnly(Runnable onChange)
	{
		return new QuestProfileEditor(onChange, ArrayList::new, name -> null, profile -> { }, ArrayList::new, true);
	}

	public QuestProfileEditor(Runnable onChange, Supplier<List<String>> otherQuests,
		Function<String, QuestProfile> profileOf, Consumer<QuestProfile> combatEditor,
		Supplier<List<QuestCombatLoadout.GearItem>> wornGear)
	{
		this(onChange, otherQuests, profileOf, combatEditor, wornGear, false);
	}

	private QuestProfileEditor(Runnable onChange, Supplier<List<String>> otherQuests,
		Function<String, QuestProfile> profileOf, Consumer<QuestProfile> combatEditor,
		Supplier<List<QuestCombatLoadout.GearItem>> wornGear, boolean suppliesOnly)
	{
		this.suppliesOnly = suppliesOnly;
		this.onChange = onChange;
		this.otherQuests = otherQuests;
		this.profileOf = profileOf;
		this.combatEditor = combatEditor;
		this.wornGear = wornGear;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		build();
	}

	private static JSpinner spinner(int value, int min, int max)
	{
		return new JSpinner(new SpinnerNumberModel(value, min, max, 1));
	}

	private static JPanel row(Component... parts)
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
		for (Component part : parts)
		{
			panel.add(part);
		}
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private JCheckBox buyBox(String key)
	{
		JCheckBox box = new JCheckBox("buy from GE", true);
		box.addActionListener(e -> changed(false));
		buyBoxes.put(key, box);
		return box;
	}

	private void build()
	{
		JPanel obtainRow = row(new JLabel("Missing items:"), obtain, new JLabel("Profile:"), type);
		JPanel bankRow = row(bankForbidden);
		profileRows.add(obtainRow);
		profileRows.add(bankRow);
		add(obtainRow);
		add(bankRow);
		bankForbidden.addActionListener(e -> changed(false));
		obtain.addActionListener(e -> changed(false));
		type.addActionListener(e ->
		{
			changed(false);
			showTypeParts();
		});

		JPanel gearButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
		JButton capture = new JButton("Capture worn gear");
		capture.addActionListener(e ->
		{
			profile.setTravelGear(new ArrayList<>(wornGear.get()));
			loadGear();
			changed(false);
		});
		JButton removeGear = new JButton("Remove selected");
		removeGear.addActionListener(e ->
		{
			int index = gearList.getSelectedIndex();
			if (index >= 0 && index < profile.getTravelGear().size())
			{
				profile.getTravelGear().remove(index);
				loadGear();
				changed(false);
			}
		});
		JButton clearGear = new JButton("Clear (leave gear alone)");
		clearGear.addActionListener(e ->
		{
			profile.getTravelGear().clear();
			loadGear();
			changed(false);
		});
		gearButtons.add(capture);
		gearButtons.add(removeGear);
		gearButtons.add(clearGear);
		gearList.setVisibleRowCount(5);
		gearBox.add(new JLabel("Gear to wear:"), BorderLayout.NORTH);
		gearBox.add(new JScrollPane(gearList), BorderLayout.CENTER);
		gearBox.add(gearButtons, BorderLayout.SOUTH);
		gearBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(gearBox);
		profileRows.add(gearBox);

		JButton editCombat = new JButton("Edit combat setup...");
		editCombat.addActionListener(e -> combatEditor.accept(profile));
		combatBox.add(editCombat);
		combatBox.add(new JLabel("Exact gear, food, potions, prayer and style, saved for this quest."));
		combatBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(combatBox);
		profileRows.add(combatBox);

		add(row(new JLabel("Supplies (this quest only)")));
		add(row(staminaOn, staminaQty, new JLabel("drink at energy %"), drinkAt, buyBox("stamina")));
		add(row(new JLabel("Keep at least"), minStamina, new JLabel("stamina potions in the inventory")));
		add(row(energyOn, energyQty, buyBox("energy")));
		add(row(new JLabel("Food"), food, foodQty, new JLabel("eat at HP %"), eatAt, new JLabel("(0 = global)"),
			buyBox("food")));
		for (QuestPotions.Kind kind : QuestProfile.POTION_KINDS)
		{
			JSpinner spinner = spinner(0, 0, 27);
			potionQty.put(kind, spinner);
			spinner.addChangeListener(e -> changed(true));
			add(row(new JLabel(kind.itemName()), spinner, buyBox("potion:" + kind.key())));
		}
		for (JSpinner spinner : new JSpinner[] {staminaQty, drinkAt, energyQty, foodQty, eatAt})
		{
			spinner.addChangeListener(e -> changed(true));
		}
		staminaOn.addActionListener(e -> changed(true));
		energyOn.addActionListener(e -> changed(true));
		minStamina.addActionListener(e -> changed(false));
		food.addActionListener(e -> changed(true));
		food.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override
			public void focusLost(java.awt.event.FocusEvent e)
			{
				changed(true);
			}
		});

		add(row(new JLabel("Teleports, runes and other things to bring:")));
		itemList.setVisibleRowCount(4);
		JScrollPane itemScroll = new JScrollPane(itemList);
		itemScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(itemScroll);
		JButton addItem = new JButton("Add");
		addItem.addActionListener(e ->
		{
			String name = itemName.getText().trim();
			if (!name.isEmpty())
			{
				profile.getSupplies().getItems().add(new QuestCombatLoadout.InventoryItem(name, -1,
					(Integer) itemQty.getValue(), false));
				itemName.setText("");
				loadItems();
				changed(true);
			}
		});
		JButton removeItem = new JButton("Remove");
		removeItem.addActionListener(e ->
		{
			int index = itemList.getSelectedIndex();
			if (index >= 0 && index < profile.getSupplies().getItems().size())
			{
				QuestCombatLoadout.InventoryItem gone = profile.getSupplies().getItems().remove(index);
				profile.getSupplies().setBuys("item:" + gone.getName().trim(), true);
				loadItems();
				changed(true);
			}
		});
		add(row(itemName, itemQty, addItem, removeItem));
		buySelectedItem.addActionListener(e ->
		{
			int index = itemList.getSelectedIndex();
			if (index >= 0 && index < profile.getSupplies().getItems().size())
			{
				profile.getSupplies().setBuys("item:" + profile.getSupplies().getItems().get(index).getName().trim(),
					buySelectedItem.isSelected());
				changed(false);
			}
		});
		itemList.addListSelectionListener(e -> syncItemBuy());
		add(row(buySelectedItem));

		JButton copy = new JButton("Copy settings from");
		copy.addActionListener(e ->
		{
			Object chosen = copyFrom.getSelectedItem();
			QuestProfile other = chosen == null ? null : profileOf.apply(chosen.toString());
			if (other != null)
			{
				profile.copySettingsFrom(other);
				load(profile);
				changed(false);
			}
		});
		JPanel copyRow = row(copy, copyFrom);
		profileRows.add(copyRow);
		add(copyRow);
		showTypeParts();
	}

	private void syncItemBuy()
	{
		int index = itemList.getSelectedIndex();
		boolean valid = profile != null && index >= 0 && index < profile.getSupplies().getItems().size();
		buySelectedItem.setEnabled(valid);
		if (valid)
		{
			loading = true;
			buySelectedItem.setSelected(
				profile.getSupplies().buys("item:" + profile.getSupplies().getItems().get(index).getName().trim()));
			loading = false;
		}
	}

	private void showTypeParts()
	{
		boolean travel = type.getSelectedItem() == QuestProfile.Type.TRAVEL;
		gearBox.setVisible(!suppliesOnly && travel);
		combatBox.setVisible(!suppliesOnly && !travel);
		if (suppliesOnly)
		{
			for (Component part : profileRows)
			{
				part.setVisible(false);
			}
		}
		revalidate();
		repaint();
	}

	private void loadGear()
	{
		gearModel.clear();
		for (QuestCombatLoadout.GearItem item : profile.getTravelGear())
		{
			gearModel.addElement(item.getSlot() + ": " + item.getName());
		}
	}

	private void loadItems()
	{
		itemModel.clear();
		for (QuestCombatLoadout.InventoryItem item : profile.getSupplies().getItems())
		{
			itemModel.addElement(item.getName() + " x" + item.getQty());
		}
		syncItemBuy();
	}

	/** Shows {@code next} for editing; null clears the editor. */
	public void load(QuestProfile next)
	{
		profile = next;
		setVisible(next != null);
		if (next == null)
		{
			return;
		}
		loading = true;
		QuestProfile.Supplies supplies = next.getSupplies();
		obtain.setSelectedItem(next.getObtain());
		type.setSelectedItem(next.getType());
		bankForbidden.setSelected(next.isBankForbidden());
		staminaOn.setSelected(supplies.isStaminaOn());
		staminaQty.setValue(supplies.getStaminaQty());
		drinkAt.setValue(supplies.getDrinkStaminaAtEnergy());
		minStamina.setSelectedIndex(next.getMinStamina() < 0 ? 0 : next.getMinStamina() + 1);
		energyOn.setSelected(supplies.isEnergyFallback());
		energyQty.setValue(supplies.getEnergyQty());
		food.setText(supplies.getFood());
		foodQty.setValue(supplies.getFoodQty());
		eatAt.setValue(supplies.getEatAtPercent());
		for (Map.Entry<QuestPotions.Kind, JSpinner> entry : potionQty.entrySet())
		{
			entry.getValue().setValue(supplies.potionQty(entry.getKey()));
		}
		for (Map.Entry<String, JCheckBox> entry : buyBoxes.entrySet())
		{
			entry.getValue().setSelected(supplies.buys(entry.getKey()));
		}
		loadGear();
		loadItems();
		copyFrom.removeAllItems();
		for (String other : otherQuests.get())
		{
			if (!other.equals(next.getQuestName()))
			{
				copyFrom.addItem(other);
			}
		}
		showTypeParts();
		loading = false;
	}

	private void changed(boolean suppliesEdited)
	{
		if (loading || profile == null)
		{
			return;
		}
		QuestProfile.Supplies supplies = profile.getSupplies();
		profile.setObtain((QuestProfile.Obtain) obtain.getSelectedItem());
		profile.setType((QuestProfile.Type) type.getSelectedItem());
		profile.setBankForbidden(bankForbidden.isSelected());
		profile.setMinStamina(minStamina.getSelectedIndex() - 1);
		supplies.setStaminaOn(staminaOn.isSelected());
		supplies.setStaminaQty((Integer) staminaQty.getValue());
		supplies.setDrinkStaminaAtEnergy((Integer) drinkAt.getValue());
		supplies.setEnergyFallback(energyOn.isSelected());
		supplies.setEnergyQty((Integer) energyQty.getValue());
		supplies.setFood(food.getText().trim());
		supplies.setFoodQty((Integer) foodQty.getValue());
		supplies.setEatAtPercent((Integer) eatAt.getValue());
		for (Map.Entry<QuestPotions.Kind, JSpinner> entry : potionQty.entrySet())
		{
			supplies.setPotionQty(entry.getKey(), (Integer) entry.getValue().getValue());
		}
		for (Map.Entry<String, JCheckBox> entry : buyBoxes.entrySet())
		{
			supplies.setBuys(entry.getKey(), entry.getValue().isSelected());
		}
		if (suppliesEdited)
		{
			supplies.setCustomised(true);
		}
		if (onChange != null)
		{
			onChange.run();
		}
	}
}
