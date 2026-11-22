/*
 * Copyright (c) 2019, dillydill123 <https://github.com/dillydill123>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.inventorysetups.ui;

import lombok.Getter;
import lombok.Setter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.inventorysetups.*;
import net.runelite.client.plugins.microbot.inventorysetups.serialization.InventorySetupPortable;
import net.runelite.client.ui.ClientTheme;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static net.runelite.api.gameval.InventoryID.INV;
import static net.runelite.api.gameval.InventoryID.WORN;
import static net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin.*;

// The main panel of the plugin that contains all viewing components
public class InventorySetupsPluginPanel extends PluginPanel
{

	private static ImageIcon HELP_ICON;
	private static ImageIcon HELP_HOVER_ICON;
	private static ImageIcon STANDARD_VIEW_ICON;
	private static ImageIcon STANDARD_VIEW_HOVER_ICON;
	private static ImageIcon COMPACT_VIEW_ICON;
	private static ImageIcon COMPACT_VIEW_HOVER_ICON;
	private static ImageIcon ICON_VIEW_ICON;
	private static ImageIcon ICON_VIEW_HOVER_ICON;
	private static ImageIcon SECTION_VIEW_ICON;
	private static ImageIcon SECTION_VIEW_HOVER_ICON;
	private static ImageIcon NO_SECTION_VIEW_ICON;
	private static ImageIcon NO_SECTION_VIEW_HOVER_ICON;
	private static ImageIcon ALPHABETICAL_ICON;
	private static ImageIcon ALPHABETICAL_HOVER_ICON;
	private static ImageIcon NO_ALPHABETICAL_ICON;
	private static ImageIcon NO_ALPHABETICAL_HOVER_ICON;
	private static ImageIcon ADD_ICON;
	private static ImageIcon ADD_HOVER_ICON;
	private static ImageIcon BACK_ICON;
	private static ImageIcon BACK_HOVER_ICON;
	private static ImageIcon IMPORT_ICON;
	private static ImageIcon IMPORT_HOVER_ICON;
	private static ImageIcon UPDATE_ICON;
	private static ImageIcon UPDATE_HOVER_ICON;

	private static String MAIN_TITLE;

	private final JPanel noSetupsPanel; // Panel that is displayed when there are no setups
	private final JPanel updateNewsPanel; // Panel that is displayed for plugin update/news
	private final JPanel setupDisplayPanel; // Panel that is displayed when a setup is selected
	private final JPanel overviewPanel; // Panel that is displayed during overview, contains all setups
	private final JPanel northAnchoredPanel; // Anchored panel in the north that won't scroll
	private final JScrollPane contentWrapperPane; // Panel for wrapping any content so it can scroll

	// The top panel which will have the title and add/import and change views
	private final JPanel overviewTopPanel;

	// The top panel when veiwing a setup
	private final JPanel setupTopPanel;

	private final JLabel mainTitle;
	private final JLabel setupTitle;
	private final JLabel helpButton;
	private final InventorySetupsCycleButton<InventorySetupsPanelViewID> panelViewMarker;
	private final JLabel sortingMarker;
	private final JLabel sectionViewMarker;
	private final JLabel addMarker;
	private final JLabel importMarker;
	private final JLabel updateMarker;
	private final JLabel backMarker;

	private final IconTextField searchBar;

	private final InventorySetupsInventoryPanel inventoryPanel;
	private final InventorySetupsEquipmentPanel equipmentPanel;
	@Getter
	private final InventorySetupsRunePouchPanel runePouchPanel;
	@Getter
	private final InventorySetupsBoltPouchPanel boltPouchPanel;

	private final InventorySetupsSpellbookPanel spellbookPanel;

	private final InventorySetupsAdditionalItemsPanel additionalFilteredItemsPanel;
	private final InventorySetupsNotesPanel notesPanel;

	private final JPanel updateNewsPanelWrapper;

	@Getter
	private InventorySetup currentSelectedSetup;

	private int overviewPanelScrollPosition;

	private final MInventorySetupsPlugin plugin;

	@Getter
	@Setter
	private boolean hasDisplayedLayoutWarning;

	private final JPanel layoutWarningPanel;

	@Getter
	private List<InventorySetup> filteredInventorysetups;

	static
	{
		final BufferedImage helpIcon = ImageUtil.loadImageResource(MInventorySetupsPlugin.class, "help_button.png");
		HELP_ICON = new ImageIcon(helpIcon);
		HELP_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(helpIcon, 0.53f));

		final BufferedImage sectionIcon = ImageUtil.loadImageResource(MInventorySetupsPlugin.class, "section_mode_icon.png");
		final BufferedImage sectionIconHover = ImageUtil.luminanceOffset(sectionIcon, -150);
		SECTION_VIEW_ICON = new ImageIcon(sectionIcon);
		SECTION_VIEW_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(sectionIcon, 0.53f));

		NO_SECTION_VIEW_ICON = new ImageIcon(sectionIconHover);
		NO_SECTION_VIEW_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(sectionIconHover, -100));

		final BufferedImage standardIcon = ImageUtil.loadImageResource(MInventorySetupsPlugin.class, "standard_mode_icon.png");
		STANDARD_VIEW_ICON = new ImageIcon(standardIcon);
		STANDARD_VIEW_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(standardIcon, -100));

		final BufferedImage compactIcon = ImageUtil.loadImageResource(MInventorySetupsPlugin.class, "compact_mode_icon.png");
		COMPACT_VIEW_ICON = new ImageIcon(compactIcon);
		COMPACT_VIEW_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(compactIcon, 0.53f));

		final BufferedImage iconIcon = ImageUtil.loadImageResource(MInventorySetupsPlugin.class, "icon_mode_icon.png");
		ICON_VIEW_ICON = new ImageIcon(iconIcon);
		ICON_VIEW_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(iconIcon, 0.53f));

		final BufferedImage alphabeticalIcon = ImageUtil.loadImageResource(MInventorySetupsPlugin.class, "alphabetical_icon.png");
		final BufferedImage alphabeticalIconHover = ImageUtil.luminanceOffset(alphabeticalIcon, -150);
		ALPHABETICAL_ICON = new ImageIcon(alphabeticalIcon);
		ALPHABETICAL_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(alphabeticalIcon, 0.53f));

		NO_ALPHABETICAL_ICON = new ImageIcon(alphabeticalIconHover);
		NO_ALPHABETICAL_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(alphabeticalIconHover, -100));

		final BufferedImage addIcon = ImageUtil.loadImageResource(MInventorySetupsPlugin.class, "add_icon.png");
		ADD_ICON = new ImageIcon(addIcon);
		ADD_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(addIcon, 0.53f));

		final BufferedImage importIcon = ImageUtil.loadImageResource(MInventorySetupsPlugin.class, "import_icon.png");
		IMPORT_ICON = new ImageIcon(importIcon);
		IMPORT_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(importIcon, 0.53f));

		final BufferedImage updateIcon = ImageUtil.loadImageResource(MInventorySetupsPlugin.class, "update_icon.png");
		UPDATE_ICON = new ImageIcon(updateIcon);
		UPDATE_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(updateIcon, 0.53f));

		final BufferedImage backIcon = ImageUtil.loadImageResource(MInventorySetupsPlugin.class, "back_arrow_icon.png");
		BACK_ICON = new ImageIcon(backIcon);
		BACK_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(backIcon, 0.53f));

		MAIN_TITLE = "Inventory Setups";
	}

	public InventorySetupsPluginPanel(final MInventorySetupsPlugin plugin, final ItemManager itemManager)
	{
		super(false);
		this.currentSelectedSetup = null;
		this.plugin = plugin;
		this.runePouchPanel = new InventorySetupsRunePouchPanel(itemManager, plugin);
		this.boltPouchPanel = new InventorySetupsBoltPouchPanel(itemManager, plugin);
		this.inventoryPanel = new InventorySetupsInventoryPanel(itemManager, plugin, runePouchPanel, boltPouchPanel);
		this.equipmentPanel = new InventorySetupsEquipmentPanel(itemManager, plugin);
		this.spellbookPanel = new InventorySetupsSpellbookPanel(itemManager, plugin);
		this.additionalFilteredItemsPanel = new InventorySetupsAdditionalItemsPanel(itemManager, plugin);
		this.notesPanel = new InventorySetupsNotesPanel(itemManager, plugin);
		this.noSetupsPanel = new JPanel();
		this.updateNewsPanel = new InventorySetupsUpdateNewsPanel(plugin, this);
		this.layoutWarningPanel = new InventorySetupsLayoutWarningPanel(plugin, this);
		this.setupDisplayPanel = new JPanel();
		this.overviewPanel = new JPanel();
		this.overviewTopPanel = new JPanel();
		this.overviewPanelScrollPosition = 0;
		this.filteredInventorysetups = new ArrayList<>();
		this.hasDisplayedLayoutWarning = false;

		// setup the title
		this.mainTitle = new JLabel();
		mainTitle.setText(MAIN_TITLE);
		mainTitle.setForeground(ClientTheme.classicOr(Color.WHITE, ColorScheme.TEXT_COLOR));

		this.helpButton = new JLabel(HELP_ICON);
		helpButton.setToolTipText("Click for help. This button can be hidden in the config.");
		helpButton.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					LinkBrowser.browse(TUTORIAL_LINK);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				helpButton.setIcon(HELP_HOVER_ICON);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				helpButton.setIcon(HELP_ICON);
			}
		});

		this.sortingMarker = new JLabel(ALPHABETICAL_ICON);
		sortingMarker.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					boolean isAlphabeticalMode = plugin.getConfig().sortingMode() == InventorySetupsSortingID.ALPHABETICAL;
					plugin.toggleAlphabeticalMode(isAlphabeticalMode ? InventorySetupsSortingID.DEFAULT : InventorySetupsSortingID.ALPHABETICAL);
					updateSortingMarker();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				boolean isAlphabeticalMode = plugin.getConfig().sortingMode() == InventorySetupsSortingID.ALPHABETICAL;
				sortingMarker.setIcon(isAlphabeticalMode ? ALPHABETICAL_HOVER_ICON : NO_ALPHABETICAL_HOVER_ICON);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				boolean isAlphabeticalMode = plugin.getConfig().sortingMode() == InventorySetupsSortingID.ALPHABETICAL;
				sortingMarker.setIcon(isAlphabeticalMode ? ALPHABETICAL_ICON : NO_ALPHABETICAL_ICON);
			}
		});

		List<ImageIcon> icons = new ArrayList<>(Arrays.asList(STANDARD_VIEW_ICON, COMPACT_VIEW_ICON, ICON_VIEW_ICON));
		List<ImageIcon> hoverIcons = new ArrayList<>(Arrays.asList(STANDARD_VIEW_HOVER_ICON, COMPACT_VIEW_HOVER_ICON, ICON_VIEW_HOVER_ICON));
		List<String> tooltips = new ArrayList<>(Arrays.asList("Switch to compact mode", "Switch to icon mode", "Switch to standard mode"));
		this.panelViewMarker = new InventorySetupsCycleButton<>(plugin, InventorySetupsPanelViewID.getValues(), icons, hoverIcons, tooltips);
		Runnable r = () -> plugin.setConfigValue(CONFIG_KEY_PANEL_VIEW, panelViewMarker.getCurrentState().toString());
		this.panelViewMarker.setRunnable(r);

		JPopupMenu massImportExportMenu = new JPopupMenu();
		JMenuItem massImportSetupsMenu = new JMenuItem("Mass Import Setups");
		JMenuItem massExportSetupsMenu = new JMenuItem("Mass Export Setups");
		JMenuItem massImportSectionsMenu = new JMenuItem("Mass Import Sections");
		JMenuItem massExportSectionsMenu = new JMenuItem("Mass Export Sections");
		massImportExportMenu.add(massImportSetupsMenu);
		massImportExportMenu.add(massExportSetupsMenu);
		massImportExportMenu.add(massImportSectionsMenu);
		massImportExportMenu.add(massExportSectionsMenu);
		// set mass export/import options
		massImportSetupsMenu.addActionListener(e ->
		{
			plugin.massImportSetups();
		});
		massExportSetupsMenu.addActionListener(e ->
		{
			ArrayList<InventorySetupPortable> portables = InventorySetupPortable.convertFromListOfSetups(plugin.getInventorySetups(), plugin.getLayoutUtilities());
			plugin.massExport(portables, "Setups", "inventory_setups");
		});
		massImportSectionsMenu.addActionListener(e ->
		{
			plugin.massImportSections();
		});
		massExportSectionsMenu.addActionListener(e ->
		{
			plugin.massExport(plugin.getSections(), "Sections", "sections");
		});

		JPopupMenu singleImportExportMenu = new JPopupMenu();
		JMenuItem singleImportSetupMenu = new JMenuItem("Import setup..");
		JMenuItem singleImportSectionMenu = new JMenuItem("Import section..");
		singleImportExportMenu.add(singleImportSetupMenu);
		singleImportExportMenu.add(singleImportSectionMenu);
		// set single import options
		singleImportSetupMenu.addActionListener(e ->
		{
			plugin.importSetup();
		});
		singleImportSectionMenu.addActionListener(e ->
		{
			plugin.importSection();
		});

		this.importMarker = new JLabel(IMPORT_ICON);
		importMarker.setToolTipText("Import a new setup or section");
		importMarker.setComponentPopupMenu(massImportExportMenu);
		importMarker.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					final Point location = MouseInfo.getPointerInfo().getLocation();
					SwingUtilities.convertPointFromScreen(location, importMarker);
					singleImportExportMenu.show(importMarker, location.x, location.y);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				importMarker.setIcon(IMPORT_HOVER_ICON);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				importMarker.setIcon(IMPORT_ICON);
			}
		});

		
		// set up the add marker (+ sign)
		this.addMarker = new JLabel(ADD_ICON);
		addMarker.setToolTipText("Add new setup or section");
		final JPopupMenu addMarkerMenu = new JPopupMenu();
		final JMenuItem addMarkerAddNewSetup = new JMenuItem("Add new setup..");
		final JMenuItem addMarkerAddNewSection = new JMenuItem("Add new section..");
		addMarkerMenu.add(addMarkerAddNewSetup);
		addMarkerMenu.add(addMarkerAddNewSection);
		addMarkerAddNewSetup.addActionListener(e ->
		{
			plugin.addInventorySetup();
		});
		addMarkerAddNewSection.addActionListener(e ->
		{
			plugin.addSection();
		});

		addMarker.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					final Point location = MouseInfo.getPointerInfo().getLocation();
					SwingUtilities.convertPointFromScreen(location, addMarker);
					addMarkerMenu.show(addMarker, location.x, location.y);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				addMarker.setIcon(ADD_HOVER_ICON);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				addMarker.setIcon(ADD_ICON);
			}
		});

		this.updateMarker = new JLabel(UPDATE_ICON);
		updateMarker.setToolTipText("Update setup with current inventory and equipment");
		updateMarker.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					plugin.updateCurrentSetup(currentSelectedSetup);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				updateMarker.setIcon(UPDATE_HOVER_ICON);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				updateMarker.setIcon(UPDATE_ICON);
			}
		});

		this.backMarker = new JLabel(BACK_ICON);
		backMarker.setToolTipText("Return to setups");
		backMarker.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					redrawOverviewPanel(false);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				backMarker.setIcon(BACK_HOVER_ICON);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				backMarker.setIcon(BACK_ICON);
			}
		});

		this.sectionViewMarker = new JLabel(COMPACT_VIEW_ICON);
		sectionViewMarker.setToolTipText("Switch to section mode");
		sectionViewMarker.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					toggleSectionMode();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				sectionViewMarker.setIcon(plugin.getConfig().sectionMode() ? SECTION_VIEW_HOVER_ICON : NO_SECTION_VIEW_HOVER_ICON);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				sectionViewMarker.setIcon(plugin.getConfig().sectionMode() ? SECTION_VIEW_ICON : NO_SECTION_VIEW_ICON);
			}
		});

		JPanel overViewMarkers = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		overViewMarkers.add(sectionViewMarker);
		overViewMarkers.add(sortingMarker);
		overViewMarkers.add(panelViewMarker);
		overViewMarkers.add(importMarker);
		overViewMarkers.add(addMarker);
		sortingMarker.setBorder(new EmptyBorder(0, 8, 0, 0));
		panelViewMarker.setBorder(new EmptyBorder(0, 8, 0, 0));
		importMarker.setBorder(new EmptyBorder(0, 8, 0, 0));
		addMarker.setBorder(new EmptyBorder(0, 8, 0, 0));

		final JPanel overviewTitleAndHelpButton = new JPanel();
		overviewTitleAndHelpButton.setLayout(new BorderLayout());
		overviewTitleAndHelpButton.add(mainTitle, BorderLayout.WEST);
		overviewTitleAndHelpButton.add(helpButton, BorderLayout.EAST);

		final JPanel setupViewMarkers = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		setupViewMarkers.add(updateMarker);
		setupViewMarkers.add(backMarker);
		backMarker.setBorder(new EmptyBorder(0, 8, 0, 0));

		this.setupTitle = new JLabel();
		setupTitle.setForeground(ClientTheme.classicOr(Color.WHITE, ColorScheme.TEXT_COLOR));
		final JPanel setupTitleAndButtons = new JPanel();
		setupTitleAndButtons.setLayout(new BorderLayout());
		setupTitleAndButtons.add(setupTitle, BorderLayout.WEST);
		setupTitleAndButtons.add(setupViewMarkers, BorderLayout.EAST);

		this.setupTopPanel = new JPanel(new BorderLayout());
		setupTopPanel.add(setupTitleAndButtons, BorderLayout.CENTER);

		// the panel on the top that holds the title and buttons
		overviewTopPanel.setLayout(new BorderLayout());
		overviewTopPanel.add(overviewTitleAndHelpButton, BorderLayout.NORTH);
		overviewTopPanel.add(Box.createRigidArea(new Dimension(0, 3)), BorderLayout.CENTER);
		overviewTopPanel.add(overViewMarkers, BorderLayout.SOUTH);

		overviewTopPanel.setVisible(true);
		setupTopPanel.setVisible(false);

		this.searchBar = new IconTextField();
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.setMinimumSize(new Dimension(0, 30));
		searchBar.addKeyListener(new KeyListener()
		{
			@Override
			public void keyTyped(KeyEvent e)
			{
			}

			@Override
			public void keyPressed(KeyEvent e)
			{
			}

			@Override
			public void keyReleased(KeyEvent e)
			{
				redrawOverviewPanel(true);
			}
		});
		searchBar.addClearListener(() -> redrawOverviewPanel(true));

		final JPanel topPanel = new JPanel();
		topPanel.setLayout(new BorderLayout());
		topPanel.add(overviewTopPanel, BorderLayout.NORTH);
		topPanel.add(setupTopPanel, BorderLayout.SOUTH);

		// the panel that stays at the top and doesn't scroll
		// contains the title and buttons
		this.northAnchoredPanel = new JPanel();
		northAnchoredPanel.setLayout(new BoxLayout(northAnchoredPanel, BoxLayout.Y_AXIS));
		northAnchoredPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
		northAnchoredPanel.add(topPanel);
		northAnchoredPanel.add(Box.createRigidArea(new Dimension(0, 10)));
		northAnchoredPanel.add(searchBar);

		// the panel that holds the inventory and equipment panels
		final BoxLayout invEqLayout = new BoxLayout(setupDisplayPanel, BoxLayout.Y_AXIS);
		setupDisplayPanel.setLayout(invEqLayout);
		setupDisplayPanel.add(inventoryPanel);
		setupDisplayPanel.add(Box.createRigidArea(new Dimension(0, 10)));
		setupDisplayPanel.add(runePouchPanel);
		setupDisplayPanel.add(Box.createRigidArea(new Dimension(0, 10)));
		setupDisplayPanel.add(boltPouchPanel);
		setupDisplayPanel.add(Box.createRigidArea(new Dimension(0, 10)));
		setupDisplayPanel.add(equipmentPanel);
		setupDisplayPanel.add(Box.createRigidArea(new Dimension(0, 10)));
		setupDisplayPanel.add(spellbookPanel);
		setupDisplayPanel.add(Box.createRigidArea(new Dimension(0, 10)));
		setupDisplayPanel.add(additionalFilteredItemsPanel);
		setupDisplayPanel.add(Box.createRigidArea(new Dimension(0, 10)));
		setupDisplayPanel.add(notesPanel);

		// setup the error panel. It's wrapped around a normal panel
		// so it doesn't stretch to fill the parent panel
		final PluginErrorPanel errorPanel = new PluginErrorPanel();
		errorPanel.setContent("Inventory Setups", "Create an inventory setup.");
		noSetupsPanel.add(errorPanel);

		// the panel that holds the inventory panels, error panel, and the overview panel
		final JPanel contentPanel = new JPanel();
		final BoxLayout contentLayout = new BoxLayout(contentPanel, BoxLayout.Y_AXIS);
		contentPanel.setLayout(contentLayout);
		contentPanel.add(setupDisplayPanel);
		contentPanel.add(noSetupsPanel);
		contentPanel.add(overviewPanel);

		// wrapper for the main content panel to stop it from stretching
		final JPanel contentWrapper = new JPanel(new BorderLayout());
		contentWrapper.add(contentPanel, BorderLayout.NORTH);
		this.contentWrapperPane = new JScrollPane(contentWrapper);
		this.contentWrapperPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		// Wrapper for the update news panel to stop it from stretching
		this.updateNewsPanelWrapper = new JPanel(new BorderLayout());
		updateNewsPanelWrapper.add(Box.createGlue(), BorderLayout.CENTER);
		updateNewsPanelWrapper.add(updateNewsPanel, BorderLayout.NORTH);

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		add(northAnchoredPanel, BorderLayout.NORTH);

		JPanel extraPanelsWrapper = new JPanel();
		extraPanelsWrapper.setLayout(new BoxLayout(extraPanelsWrapper, BoxLayout.Y_AXIS));
		extraPanelsWrapper.add(updateNewsPanelWrapper);
		extraPanelsWrapper.add(layoutWarningPanel);

		JPanel southPanel = new JPanel(new BorderLayout());
		southPanel.add(extraPanelsWrapper, BorderLayout.NORTH);
		southPanel.add(contentWrapperPane, BorderLayout.CENTER);

		add(southPanel, BorderLayout.CENTER);

		// make sure the invEq panel isn't visible upon startup
		setupDisplayPanel.setVisible(false);
		helpButton.setVisible(!plugin.getConfig().hideButton());
		updateSectionViewMarker();
		updatePanelViewMarker();
		updateSortingMarker();
	}

	@Override
	public void onActivate()
	{
		plugin.setNavButtonIsSelected(true);
		if (!plugin.getConfig().manualBankFilter())
		{
			plugin.doBankSearch();
		}
	}

	@Override
	public void onDeactivate()
	{
		plugin.setNavButtonIsSelected(false);
		if (plugin.getConfig().requireActivePanelFilter())
		{
			plugin.resetBankSearch();
		}
	}

	// Redraw the entire overview panel, considering the text in the search bar
	public void redrawOverviewPanel(boolean resetScrollBar)
	{
		InventorySetupUtilities.fastRemoveAll(overviewPanel);
		updateSectionViewMarker();
		updatePanelViewMarker();
		updateSortingMarker();

		filteredInventorysetups.clear();
		if (!searchBar.getText().isEmpty())
		{
			filteredInventorysetups = plugin.filterSetups(searchBar.getText());
		}
		else
		{
			filteredInventorysetups = new ArrayList<>(plugin.getInventorySetups());
			moveFavoriteSetupsToTopOfList(filteredInventorysetups);
		}

		if (plugin.getConfig().sortingMode() == InventorySetupsSortingID.ALPHABETICAL)
		{
			filteredInventorysetups.sort(Comparator.comparing(InventorySetup::getName, String.CASE_INSENSITIVE_ORDER));
		}

		layoutSetups(filteredInventorysetups);
		returnToOverviewPanel(resetScrollBar);

		showCorrectPanel();

		revalidate();
		repaint();
	}

	public InventorySetupsQuiverPanel getQuiverPanel()
	{
		return equipmentPanel.getQuiverPanel();
	}

	public void showCorrectPanel()
	{

		if (!plugin.getSavedVersionString().equals(plugin.getCurrentVersionString()))
		{
			layoutWarningPanel.setVisible(false);
			updateNewsPanelWrapper.setVisible(true);
			northAnchoredPanel.setVisible(false);
			contentWrapperPane.setVisible(false);
		}
		else if (!hasDisplayedLayoutWarning && !plugin.getCanUseLayouts() && plugin.getConfig().enableLayoutWarning())
		{
			layoutWarningPanel.setVisible(true);
			updateNewsPanelWrapper.setVisible(false);
			northAnchoredPanel.setVisible(false);
			contentWrapperPane.setVisible(false);
		}
		else
		{
			layoutWarningPanel.setVisible(false);
			updateNewsPanelWrapper.setVisible(false);
			northAnchoredPanel.setVisible(true);
			contentWrapperPane.setVisible(true);

			// We set this to true now because we only want this menu to show up on startup.
			// If someone modifies settings after startup, just continue.
			hasDisplayedLayoutWarning = true;
		}
	}

	public void moveFavoriteSetupsToTopOfList(final List<InventorySetup> setupsToAdd)
	{
		List<InventorySetup> favSetups = setupsToAdd.stream().filter(InventorySetup::isFavorite).collect(Collectors.toList());
		setupsToAdd.removeAll(favSetups);
		for (int i = favSetups.size() - 1; i >= 0; i--)
		{
			setupsToAdd.add(0, favSetups.get(i));
		}
	}

	public void refreshCurrentSetup()
	{
		if (currentSelectedSetup != null)
		{
			setCurrentInventorySetup(currentSelectedSetup, false);
		}
	}

	public void setCurrentInventorySetup(final InventorySetup inventorySetup, boolean resetScrollBar)
	{
		overviewPanelScrollPosition = contentWrapperPane.getVerticalScrollBar().getValue();
		currentSelectedSetup = inventorySetup;
		inventoryPanel.updatePanelWithSetupInformation(inventorySetup);
		runePouchPanel.updatePanelWithSetupInformation(inventorySetup);
		boltPouchPanel.updatePanelWithSetupInformation(inventorySetup);
		equipmentPanel.updatePanelWithSetupInformation(inventorySetup);
		spellbookPanel.updatePanelWithSetupInformation(inventorySetup);
		additionalFilteredItemsPanel.updatePanelWithSetupInformation(inventorySetup);
		notesPanel.updatePanelWithSetupInformation(inventorySetup);

		overviewTopPanel.setVisible(false);
		setupTopPanel.setVisible(true);

		setupDisplayPanel.setVisible(true);
		noSetupsPanel.setVisible(false);
		overviewPanel.setVisible(false);

		setupTitle.setText(inventorySetup.getName());
		helpButton.setVisible(false);
		searchBar.setVisible(false);

		// only show the rune pouch if the setup has a rune pouch
		runePouchPanel.setVisible(currentSelectedSetup.getRune_pouch() != null);
		boltPouchPanel.setVisible(currentSelectedSetup.getBoltPouch() != null);

		plugin.getClientThread().invoke(this::doHighlighting);

		if (resetScrollBar)
		{
			// reset scrollbar back to top
			setScrollBarPosition(0);
		}

		plugin.doBankSearch();

		validate();
		repaint();

	}

	public void doHighlighting()
	{
		if (currentSelectedSetup == null)
		{
			return;
		}

		if (!setupDisplayPanel.isVisible())
		{
			return;
		}

		final List<InventorySetupsItem> inv = plugin.getNormalizedContainer(INV);
		final List<InventorySetupsItem> eqp = plugin.getNormalizedContainer(WORN);

		highlightContainerPanel(inv, inventoryPanel);
		highlightContainerPanel(eqp, equipmentPanel);
		// pass spellbook a dummy container because it only needs the current selected setup
		highlightContainerPanel(null, spellbookPanel);
		plugin.getAmmoHandler().handleSpecialHighlighting(currentSelectedSetup, inv, eqp);

	}

	public void highlightContainerPanel(final List<InventorySetupsItem> container, final InventorySetupsContainerPanel containerPanel)
	{
		// if the panel is visible, check if highlighting is enabled on the setup and globally
		// if any of the two, reset the slots so they aren't highlighted
		if (!currentSelectedSetup.isHighlightDifference() || !plugin.isHighlightingAllowed())
		{
			containerPanel.resetSlotColors();
			return;
		}

		containerPanel.highlightSlots(container, currentSelectedSetup);
	}

	public void highlightInventory()
	{
		final List<InventorySetupsItem> inv = plugin.getNormalizedContainer(INV);
		highlightContainerPanel(inv, inventoryPanel);
	}

	public void highlightEquipment()
	{
		final List<InventorySetupsItem> eqp = plugin.getNormalizedContainer(WORN);
		highlightContainerPanel(eqp, equipmentPanel);
	}

	public void highlightSpellbook()
	{
		highlightContainerPanel(null, spellbookPanel);
	}

	// returns to the overview panel
	public void returnToOverviewPanel(boolean shouldResetScrollBar)
	{
		noSetupsPanel.setVisible(plugin.getInventorySetups().isEmpty() && !plugin.getConfig().sectionMode());
		overviewPanel.setVisible(!plugin.getInventorySetups().isEmpty() || plugin.getConfig().sectionMode());
		setupDisplayPanel.setVisible(false);
		overviewTopPanel.setVisible(true);
		setupTopPanel.setVisible(false);
		helpButton.setVisible(!plugin.getConfig().hideButton());
		searchBar.setVisible(true);

		if (shouldResetScrollBar)
		{
			overviewPanelScrollPosition = 0;
			setScrollBarPosition(overviewPanelScrollPosition);
		}
		else if (currentSelectedSetup != null)
		{
			setScrollBarPosition(overviewPanelScrollPosition);
		}

		currentSelectedSetup = null;

		plugin.resetBankSearch();

	}

	public boolean isStackCompareForSlotAllowed(final InventorySetupsSlotID inventoryID, final int slotId)
	{
		switch (inventoryID)
		{
			case INVENTORY:
				return inventoryPanel.isStackCompareForSlotAllowed(slotId);
			case EQUIPMENT:
				return equipmentPanel.isStackCompareForSlotAllowed(slotId);
			case ADDITIONAL_ITEMS:
				return additionalFilteredItemsPanel.isStackCompareForSlotAllowed(slotId);
			case SPELL_BOOK:
				return spellbookPanel.isStackCompareForSlotAllowed(slotId);
			default:
				return plugin.getAmmoHandler().isStackCompareForSpecialSlotAllowed(inventoryID, slotId);
		}
	}

	public void toggleSectionMode()
	{
		plugin.setConfigValue(CONFIG_KEY_SECTION_MODE, !plugin.getConfig().sectionMode());
		updateSectionViewMarker();
	}

	private void updateSectionViewMarker()
	{
		sectionViewMarker.setIcon(plugin.getConfig().sectionMode() ? SECTION_VIEW_ICON : NO_SECTION_VIEW_ICON);
		sectionViewMarker.setToolTipText("Switch to " + (plugin.getConfig().sectionMode() ? "standard mode" : "section mode"));
	}

	private void updatePanelViewMarker()
	{
		panelViewMarker.setCurrentState(plugin.getConfig().panelView());
	}

	private void updateSortingMarker()
	{
		boolean isAlphabeticalMode = plugin.getConfig().sortingMode() == InventorySetupsSortingID.ALPHABETICAL;
		sortingMarker.setIcon(isAlphabeticalMode ? ALPHABETICAL_ICON : NO_ALPHABETICAL_ICON);
		sortingMarker.setToolTipText(isAlphabeticalMode ? "Remove alphabetical sorting" : "Alphabetically sort setups");
	}

	private void setScrollBarPosition(int scrollbarValue)
	{
		validate();
		repaint();
		contentWrapperPane.getVerticalScrollBar().setValue(scrollbarValue);
	}

	// Layout setups according
	private void layoutSetups(List<InventorySetup> originalFilteredSetups)
	{
		overviewPanel.setLayout(new GridBagLayout());
		overviewPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		if (plugin.getConfig().sectionMode())
		{
			layoutSections(originalFilteredSetups, constraints);
		}
		else
		{
			if (plugin.getConfig().panelView() == InventorySetupsPanelViewID.ICON)
			{
				// Don't pass a whitelist to indicate we want to include all of the filtered setups given
				JPanel iconGridPanel = InventorySetupsSectionPanel.createIconPanelGrid(plugin, this, originalFilteredSetups, InventorySetupsSectionPanel.MAX_ICONS_PER_ROW, null, null, true);
				overviewPanel.add(iconGridPanel, constraints);
				constraints.gridy++;
			}
			else
			{
				for (final InventorySetup setup : originalFilteredSetups)
				{
					constraints.fill = GridBagConstraints.HORIZONTAL;
					InventorySetupsPanel newPanel = null;
					if (plugin.getConfig().panelView() == InventorySetupsPanelViewID.COMPACT)
					{
						newPanel = new InventorySetupsCompactPanel(plugin, this, setup, null);
					}
					else
					{
						newPanel = new InventorySetupsStandardPanel(plugin, this, setup, null);
					}
					overviewPanel.add(newPanel, constraints);
					constraints.gridy++;

					overviewPanel.add(Box.createRigidArea(new Dimension(0, 10)), constraints);
					constraints.gridy++;
				}
			}
		}

		setupDisplayPanel.setVisible(false);
	}

	private void layoutSections(final List<InventorySetup> setups, final GridBagConstraints constraints)
	{
		Set<String> setupNamesToBeIncluded = setups.stream().map(InventorySetup::getName).collect(Collectors.toSet());
		for (final InventorySetupsSection section : plugin.getSections())
		{
			// For quick look up
			final Map<String, InventorySetup> setupsOfSection = plugin.getCache().getSectionSetupsMap().get(section.getName());
			if (setupsOfSection == null)
			{
				continue;
			}
			Set<String> setupsInSection = setupsOfSection.keySet();
			if (sectionShouldBeHidden(setupNamesToBeIncluded, setupsInSection))
			{
				continue;
			}

			boolean forceMaximization = !searchBar.getText().isEmpty();
			InventorySetupsSectionPanel sectionPanel = new InventorySetupsSectionPanel(plugin, this, section, forceMaximization, true, setupNamesToBeIncluded, setupsInSection, setups);
			overviewPanel.add(sectionPanel, constraints);
			constraints.gridy++;
			overviewPanel.add(Box.createRigidArea(new Dimension(0, 5)), constraints);
			constraints.gridy++;
		}
		// Create the bottom unassigned section
		createUnassignedSection(setups, constraints, setupNamesToBeIncluded);

	}

	private void createUnassignedSection(final List<InventorySetup> setups, final GridBagConstraints constraints, final Set<String> setupNamesToBeDisplayed)
	{
		InventorySetupsSection unassignedSection = new InventorySetupsSection("Unassigned");
		unassignedSection.setMaximized(plugin.getBooleanConfigValue(CONFIG_KEY_UNASSIGNED_MAXIMIZED));

		// For quick look up
		Set<String> setupsInSection = new HashSet<>();
		// Always output the unassigned setups in the defined order of the provided setups
		for (final InventorySetup setup : setups)
		{
			final Map<String, InventorySetupsSection> sectionsOfSetup = plugin.getCache().getSetupSectionsMap().get(setup.getName());
			if (sectionsOfSetup != null && sectionsOfSetup.isEmpty())
			{
				unassignedSection.getSetups().add(setup.getName());
				setupsInSection.add(setup.getName());
			}
		}

		// don't show the unassigned section if there are no unassigned setups
		if (unassignedSection.getSetups().isEmpty() || sectionShouldBeHidden(setupNamesToBeDisplayed, setupsInSection))
		{
			return;
		}

		boolean forceMaximization = !searchBar.getText().isEmpty();

		InventorySetupsSectionPanel sectionPanel = new InventorySetupsSectionPanel(plugin, this, unassignedSection, forceMaximization, false, setupNamesToBeDisplayed, setupsInSection, setups);
		overviewPanel.add(sectionPanel, constraints);
		constraints.gridy++;
	}

	public boolean sectionShouldBeHidden(final Set<String> setupNamesToBeIncluded, final Set<String> setupsInSection)
	{
		// If the search bar is not empty, do not to show empty sections
		if (!searchBar.getText().isEmpty())
		{
			Set<String> intersection = new HashSet<>(setupsInSection);
			intersection.retainAll(setupNamesToBeIncluded);
			return intersection.isEmpty();
		}

		return false;
	}

}
