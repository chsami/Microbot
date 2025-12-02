package net.runelite.client.plugins.microbot.ui;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.ColorScheme;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public class WorldSelectorDialog extends JDialog {
	private static final Color BACKGROUND_COLOR = new Color(40, 40, 40);
	private static final Color TEXT_COLOR = new Color(220, 220, 220);
	private static final Color ACCENT_COLOR = new Color(255, 140, 0);
	private static final Color MEMBERS_COLOR = new Color(255, 200, 50);
	private static final Color F2P_COLOR = new Color(150, 150, 150);

	private Integer selectedWorld = null;
	private JTable worldTable;
	private DefaultTableModel tableModel;
	private JTextField searchField;

	public WorldSelectorDialog(JFrame parent) {
		super(parent, "Select World", true);
		initializeUI();
		loadWorlds();
	}

	private void initializeUI() {
		setLayout(new BorderLayout(10, 10));
		getContentPane().setBackground(BACKGROUND_COLOR);

		// Top panel with search and special options
		JPanel topPanel = new JPanel(new BorderLayout(10, 10));
		topPanel.setOpaque(false);
		topPanel.setBorder(new EmptyBorder(15, 15, 5, 15));

		// Title
		JLabel titleLabel = new JLabel("Select World for Profile");
		titleLabel.setFont(new Font("Roboto", Font.BOLD, 16));
		titleLabel.setForeground(TEXT_COLOR);
		topPanel.add(titleLabel, BorderLayout.NORTH);

		// Search panel
		JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
		searchPanel.setOpaque(false);

		JLabel searchLabel = new JLabel("Search:");
		searchLabel.setForeground(TEXT_COLOR);
		searchPanel.add(searchLabel, BorderLayout.WEST);

		searchField = new JTextField();
		searchField.setBackground(new Color(60, 60, 60));
		searchField.setForeground(TEXT_COLOR);
		searchField.setCaretColor(TEXT_COLOR);
		searchField.setBorder(new EmptyBorder(5, 10, 5, 10));
		searchField.addKeyListener(new java.awt.event.KeyAdapter() {
			public void keyReleased(java.awt.event.KeyEvent evt) {
				filterWorlds();
			}
		});
		searchPanel.add(searchField, BorderLayout.CENTER);

		topPanel.add(searchPanel, BorderLayout.CENTER);

		// Special selection buttons
		JPanel specialPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		specialPanel.setOpaque(false);

		JButton randomMembersBtn = createButton("Random Members World");
		randomMembersBtn.addActionListener(e -> {
			selectedWorld = -1;
			dispose();
		});
		specialPanel.add(randomMembersBtn);

		JButton randomF2PBtn = createButton("Random F2P World");
		randomF2PBtn.addActionListener(e -> {
			selectedWorld = -2;
			dispose();
		});
		specialPanel.add(randomF2PBtn);

		topPanel.add(specialPanel, BorderLayout.SOUTH);

		add(topPanel, BorderLayout.NORTH);

		// Center panel with world table
		JPanel centerPanel = new JPanel(new BorderLayout());
		centerPanel.setOpaque(false);
		centerPanel.setBorder(new EmptyBorder(5, 15, 5, 15));

		String[] columnNames = {"World", "Players", "Type", "Region", "Activity"};
		tableModel = new DefaultTableModel(columnNames, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};

		worldTable = new JTable(tableModel);
		worldTable.setBackground(new Color(50, 50, 50));
		worldTable.setForeground(TEXT_COLOR);
		worldTable.setSelectionBackground(ACCENT_COLOR);
		worldTable.setSelectionForeground(Color.BLACK);
		worldTable.setRowHeight(25);
		worldTable.setGridColor(new Color(70, 70, 70));
		worldTable.setShowGrid(true);
		worldTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// Custom cell renderer
		DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment(JLabel.CENTER);
		for (int i = 0; i < worldTable.getColumnCount(); i++) {
			worldTable.getColumnModel().getColumn(i).setCellRenderer(new WorldTableCellRenderer());
		}

		// Set column widths
		worldTable.getColumnModel().getColumn(0).setPreferredWidth(80);
		worldTable.getColumnModel().getColumn(1).setPreferredWidth(80);
		worldTable.getColumnModel().getColumn(2).setPreferredWidth(100);
		worldTable.getColumnModel().getColumn(3).setPreferredWidth(100);
		worldTable.getColumnModel().getColumn(4).setPreferredWidth(200);

		// Enable sorting
		TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
		worldTable.setRowSorter(sorter);

		// Sort by world ID by default
		sorter.setComparator(0, Comparator.comparingInt(o -> Integer.parseInt(o.toString())));
		sorter.setComparator(1, Comparator.comparingInt(o -> Integer.parseInt(o.toString())));

		// Double-click to select
		worldTable.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				if (evt.getClickCount() == 2) {
					selectWorld();
				}
			}
		});

		JScrollPane scrollPane = new JScrollPane(worldTable);
		scrollPane.setBackground(BACKGROUND_COLOR);
		scrollPane.getViewport().setBackground(new Color(50, 50, 50));
		scrollPane.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));

		centerPanel.add(scrollPane, BorderLayout.CENTER);
		add(centerPanel, BorderLayout.CENTER);

		// Bottom panel with buttons
		JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
		bottomPanel.setOpaque(false);
		bottomPanel.setBorder(new EmptyBorder(5, 15, 15, 15));

		JButton selectBtn = createButton("Select");
		selectBtn.addActionListener(e -> selectWorld());
		bottomPanel.add(selectBtn);

		JButton cancelBtn = createButton("Cancel");
		cancelBtn.addActionListener(e -> {
			selectedWorld = null;
			dispose();
		});
		bottomPanel.add(cancelBtn);

		add(bottomPanel, BorderLayout.SOUTH);

		setSize(700, 500);
		setLocationRelativeTo(getParent());
	}

	private JButton createButton(String text) {
		JButton button = new JButton(text);
		button.setBackground(new Color(60, 60, 60));
		button.setForeground(TEXT_COLOR);
		button.setFocusPainted(false);
		button.setBorder(new EmptyBorder(8, 16, 8, 16));
		button.setCursor(new Cursor(Cursor.HAND_CURSOR));

		button.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseEntered(java.awt.event.MouseEvent evt) {
				button.setBackground(new Color(80, 80, 80));
			}

			public void mouseExited(java.awt.event.MouseEvent evt) {
				button.setBackground(new Color(60, 60, 60));
			}
		});

		return button;
	}

	private void loadWorlds() {
		try {
			WorldResult worldResult = Microbot.getWorldService().getWorlds();
			if (worldResult == null) {
				JOptionPane.showMessageDialog(this,
						"Failed to fetch world list. Please try again.",
						"Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			List<World> worlds = worldResult.getWorlds();
			// Sort by world ID
			worlds.sort(Comparator.comparingInt(World::getId));

			for (World world : worlds) {
				// Skip restricted worlds
				if (world.getTypes().contains(WorldType.PVP) ||
					world.getTypes().contains(WorldType.HIGH_RISK) ||
					world.getTypes().contains(WorldType.BOUNTY) ||
					world.getTypes().contains(WorldType.SKILL_TOTAL) ||
					world.getTypes().contains(WorldType.LAST_MAN_STANDING) ||
					world.getTypes().contains(WorldType.QUEST_SPEEDRUNNING) ||
					world.getTypes().contains(WorldType.BETA_WORLD) ||
					world.getTypes().contains(WorldType.DEADMAN) ||
					world.getTypes().contains(WorldType.PVP_ARENA) ||
					world.getTypes().contains(WorldType.TOURNAMENT) ||
					world.getTypes().contains(WorldType.NOSAVE_MODE) ||
					world.getTypes().contains(WorldType.LEGACY_ONLY) ||
					world.getTypes().contains(WorldType.EOC_ONLY) ||
					world.getTypes().contains(WorldType.FRESH_START_WORLD)) {
					continue;
				}

				String worldType = world.getTypes().contains(WorldType.MEMBERS) ? "Members" : "F2P";
				String region = getRegionDisplay(world.getRegion());
				String activity = world.getActivity() != null ? world.getActivity() : "-";

				Object[] row = {
					world.getId(),
					world.getPlayers(),
					worldType,
					region,
					activity
				};
				tableModel.addRow(row);
			}

		} catch (Exception e) {
			log.error("Error loading worlds", e);
			JOptionPane.showMessageDialog(this,
					"Error loading worlds: " + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private String getRegionDisplay(WorldRegion region) {
		if (region == null) {
			return "Unknown";
		}
		String regionName = region.toString();
		switch (regionName) {
			case "UNITED_STATES":
				return "USA";
			case "UNITED_KINGDOM":
				return "UK";
			case "AUSTRALIA":
				return "AUS";
			case "GERMANY":
				return "GER";
			default:
				// Format other regions nicely (e.g., "SOUTH_AMERICA" -> "South America")
				return formatRegionName(regionName);
		}
	}

	private String formatRegionName(String regionName) {
		if (regionName == null || regionName.isEmpty()) {
			return "Unknown";
		}
		// Replace underscores with spaces and capitalize each word
		String[] words = regionName.toLowerCase().split("_");
		StringBuilder formatted = new StringBuilder();
		for (String word : words) {
			if (word.length() > 0) {
				formatted.append(Character.toUpperCase(word.charAt(0)))
						.append(word.substring(1))
						.append(" ");
			}
		}
		return formatted.toString().trim();
	}

	private void filterWorlds() {
		String searchText = searchField.getText().toLowerCase();
		TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) worldTable.getRowSorter();

		if (searchText.isEmpty()) {
			sorter.setRowFilter(null);
		} else {
			sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText));
		}
	}

	private void selectWorld() {
		int selectedRow = worldTable.getSelectedRow();
		if (selectedRow >= 0) {
			int modelRow = worldTable.convertRowIndexToModel(selectedRow);
			selectedWorld = (Integer) tableModel.getValueAt(modelRow, 0);
			dispose();
		} else {
			JOptionPane.showMessageDialog(this,
					"Please select a world from the table.",
					"No Selection", JOptionPane.WARNING_MESSAGE);
		}
	}

	public Integer getSelectedWorld() {
		return selectedWorld;
	}

	// Custom cell renderer for the world table
	private class WorldTableCellRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value,
													  boolean isSelected, boolean hasFocus,
													  int row, int column) {
			Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

			setHorizontalAlignment(JLabel.CENTER);

			if (!isSelected) {
				c.setBackground(new Color(50, 50, 50));
				c.setForeground(TEXT_COLOR);

				// Color code based on world type (column 2)
				if (column == 2) {
					String type = (String) value;
					if ("Members".equals(type)) {
						c.setForeground(MEMBERS_COLOR);
					} else {
						c.setForeground(F2P_COLOR);
					}
				}
			} else {
				c.setBackground(ACCENT_COLOR);
				c.setForeground(Color.BLACK);
			}

			return c;
		}
	}
}
