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
package net.runelite.client.plugins.inventorysetups;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.inventorysetups.ui.InventorySetupsPluginPanel;
import net.runelite.client.plugins.inventorysetups.ui.InventorySetupsRunePouchPanel;
import net.runelite.client.plugins.inventorysetups.ui.InventorySetupsSlot;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.runelite.client.plugins.inventorysetups.ui.InventorySetupsBoltPouchPanel.BOLT_POUCH_AMOUNT_VARBIT_IDS;
import static net.runelite.client.plugins.inventorysetups.ui.InventorySetupsBoltPouchPanel.BOLT_POUCH_BOLT_VARBIT_IDS;
import static net.runelite.client.plugins.inventorysetups.ui.InventorySetupsRunePouchPanel.RUNE_POUCH_AMOUNT_VARBITS;
import static net.runelite.client.plugins.inventorysetups.ui.InventorySetupsRunePouchPanel.RUNE_POUCH_RUNE_VARBITS;


@PluginDescriptor(
		name = PluginDescriptor.Mocrosoft + "MInventory Setups",
		description = "Save gear setups for specific activities",
		enabledByDefault = true
)

@Slf4j
public class MInventorySetupsPlugin extends Plugin
{

	public static final String CONFIG_GROUP = "inventorysetups";

	public static final String CONFIG_KEY_SECTION_MODE = "sectionMode";
	public static final String CONFIG_KEY_PANEL_VIEW = "panelView";
	public static final String CONFIG_KEY_SORTING_MODE = "sortingMode";
	public static final String CONFIG_KEY_HIDE_BUTTON = "hideHelpButton";
	public static final String CONFIG_KEY_VERSION_STR = "version";
	public static final String CONFIG_KEY_UNASSIGNED_MAXIMIZED = "unassignedMaximized";
	public static final String CONFIG_KEY_MANUAL_BANK_FILTER = "manualBankFilter";
	public static final String CONFIG_KEY_PERSIST_HOTKEYS = "persistHotKeysOutsideBank";
	public static final String TUTORIAL_LINK = "https://github.com/dillydill123/inventory-setups#inventory-setups";
	public static final String SUGGESTION_LINK = "https://github.com/dillydill123/inventory-setups/issues";
	public static final int NUM_INVENTORY_ITEMS = 28;
	public static final int NUM_EQUIPMENT_ITEMS = 14;
	public static final int MAX_SETUP_NAME_LENGTH = 50;
	private static final String OPEN_SECTION_MENU_ENTRY = "Open Section";
	private static final String OPEN_SETUP_MENU_ENTRY = "Open setup";
	private static final String RETURN_TO_OVERVIEW_ENTRY = "Close current setup";
	private static final String FILTER_ADD_ITEMS_ENTRY = "Filter additional items";
	private static final String FILTER_EQUIPMENT_ENTRY = "Filter equipment";
	private static final String FILTER_INVENTORY_ENTRY = "Filter inventory";
	private static final String FILTER_ALL_ENTRY = "Filter all";
	private static final String ADD_TO_ADDITIONAL_ENTRY = "Add to Additional Filtered Items";
	private static final String UNASSIGNED_SECTION_SETUP_MENU_ENTRY = "Unassigned";
	private static final int SPELLBOOK_VARBIT = 4070;
	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_VERTICAL_SPACING = 36;
	private static final int ITEM_HORIZONTAL_SPACING = 48;
	private static final int ITEM_ROW_START = 51;

	@Inject
	@Getter
	private Client client;

	@Inject
	@Getter
	private ItemManager itemManager;

	@Inject
	@Getter
	private SpriteManager spriteManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	@Getter
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	@Getter
	private MInventorySetupsConfig config;

	@Inject
	private Gson gson;

	@Inject
	@Getter
	private ColorPickerManager colorPickerManager;

	private InventorySetupsPluginPanel panel;

	@Getter
	public static List<InventorySetup> inventorySetups;

	@Getter
	private List<InventorySetupsSection> sections;

	@Getter
	private InventorySetupsCache cache;

	private NavigationButton navButton;

	@Inject
	private InventorySetupsBankSearch bankSearch;

	@Inject
	private KeyManager keyManager;

	@Inject
	@Getter
	private ChatboxItemSearch itemSearch;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	private ChatboxTextInput searchInput;

	// global filtering is allowed for any setup
	private boolean internalFilteringIsAllowed;

	@Setter
	@Getter
	private boolean navButtonIsSelected;

	// current version of the plugin
	private String currentVersion;

	@Getter
	private InventorySetupsPersistentDataManager dataManager;

	@Setter
	@Getter
	private InventorySetupsFilteringModeID bankFilteringMode;

	private final HotkeyListener returnToSetupsHotkeyListener = new HotkeyListener(() -> config.returnToSetupsHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			panel.returnToOverviewPanel(false);
		}
	};

	private final HotkeyListener filterBankHotkeyListener = new HotkeyListener(() -> config.filterBankHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			bankFilteringMode = InventorySetupsFilteringModeID.ALL;
			triggerBankSearchFromHotKey();
		}
	};

	private final HotkeyListener filterInventoryHotkeyListener = new HotkeyListener(() -> config.filterInventoryHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			bankFilteringMode = InventorySetupsFilteringModeID.INVENTORY;
			triggerBankSearchFromHotKey();
		}
	};

	private final HotkeyListener filterEquipmentHotkeyListener = new HotkeyListener(() -> config.filterEquipmentHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			bankFilteringMode = InventorySetupsFilteringModeID.EQUIPMENT;
			triggerBankSearchFromHotKey();
		}
	};

	private final HotkeyListener filterAddItemsHotkeyListener = new HotkeyListener(() -> config.filterAddItemsHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			bankFilteringMode = InventorySetupsFilteringModeID.ADDITIONAL_FILTERED_ITEMS;
			triggerBankSearchFromHotKey();
		}
	};

	private final HotkeyListener sectionModeHotkeyListener = new HotkeyListener(() -> config.sectionModeHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			panel.toggleSectionMode();
		}
	};

	private void registerHotkeys()
	{
		keyManager.registerKeyListener(returnToSetupsHotkeyListener);
		keyManager.registerKeyListener(filterBankHotkeyListener);
		keyManager.registerKeyListener(filterInventoryHotkeyListener);
		keyManager.registerKeyListener(filterEquipmentHotkeyListener);
		keyManager.registerKeyListener(filterAddItemsHotkeyListener);
		keyManager.registerKeyListener(sectionModeHotkeyListener);
	}

	private void unregisterHotkeys()
	{
		keyManager.unregisterKeyListener(returnToSetupsHotkeyListener);
		keyManager.unregisterKeyListener(filterBankHotkeyListener);
		keyManager.unregisterKeyListener(filterInventoryHotkeyListener);
		keyManager.unregisterKeyListener(filterEquipmentHotkeyListener);
		keyManager.unregisterKeyListener(filterAddItemsHotkeyListener);
		keyManager.unregisterKeyListener(sectionModeHotkeyListener);
	}

	private void triggerBankSearchFromHotKey()
	{
		// you must wait at least one game tick otherwise
		// the bank filter will work but then go back to the previous tab.
		// For some reason this can still happen but it is very rare,
		// and only when the user clicks a tab and the hot key extremely shortly after.
		int gameTick = client.getTickCount();
		clientThread.invokeLater(() ->
		{
			int gameTick2 = client.getTickCount();
			if (gameTick2 <= gameTick)
			{
				return false;
			}

			doBankSearch();
			return true;
		});
	}

	@Provides
	MInventorySetupsConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MInventorySetupsConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(CONFIG_GROUP))
		{
			if (event.getKey().equals(CONFIG_KEY_PANEL_VIEW) || event.getKey().equals(CONFIG_KEY_SECTION_MODE) ||
					event.getKey().equals(CONFIG_KEY_SORTING_MODE) || event.getKey().equals(CONFIG_KEY_HIDE_BUTTON) ||
					event.getKey().equals(CONFIG_KEY_UNASSIGNED_MAXIMIZED))
			{
				SwingUtilities.invokeLater(() ->
				{
					panel.redrawOverviewPanel(false);
				});
			}
			else if (event.getKey().equals(CONFIG_KEY_PERSIST_HOTKEYS))
			{
				boolean bankOpen = client.getItemContainer(InventoryID.BANK) != null;
				if (config.persistHotKeysOutsideBank())
				{
					registerHotkeys();
				}
				else if (!bankOpen)
				{
					unregisterHotkeys();
				}
			}
		}
	}

	@Subscribe(priority = -1)
	public void onPostMenuSort(PostMenuSort postMenuSort)
	{
		// The menu is not rebuilt when it is open, so don't swap or else it will
		// repeatedly swap entries
		if (client.isMenuOpen())
		{
			return;
		}

		if (panel.getCurrentSelectedSetup() != null && (config.groundItemMenuSwap() || config.groundItemMenuHighlight()))
		{
			MenuEntry[] clientEntries = client.getMenuEntries();

			// We want to be sure to preserve menu entry order while only sorting the "Take" menu options
			int firstTakeIndex = IntStream.range(0, clientEntries.length)
					.filter(i -> clientEntries[i].getOption().equals("Take"))
					.findFirst().orElse(-1);

			if (firstTakeIndex == -1)
			{
				// no work to be done if no "Take" options are present
				return;
			}

			int lastTakeIndex = IntStream.range(firstTakeIndex, clientEntries.length)
					.map(i -> firstTakeIndex + (clientEntries.length - 1 - i))
					.filter(i -> clientEntries[i].getOption().equals("Take"))
					.findFirst().orElse(-1);

			List<MenuEntry> takeEntriesInSetup = new ArrayList<>();
			List<MenuEntry> takeEntriesNotInSetup = new ArrayList<>();

			// Bucket sort the "Take" entries
			String colorHex = ColorUtil.colorToHexCode(config.groundItemMenuHighlightColor());
			String replacementColorText = "<col=" + colorHex + ">";
			for (int i = firstTakeIndex; i < lastTakeIndex + 1; i++)
			{
				MenuEntry oldEntry = clientEntries[i];
				int itemID = oldEntry.getIdentifier();
				boolean setupContainsItem = setupContainsItem(panel.getCurrentSelectedSetup(), itemID);
				if (setupContainsItem)
				{
					if (config.groundItemMenuHighlight())
					{
						// Change the color of the item to indicate it's in the setup.
						final String newTarget = oldEntry.getTarget().replaceFirst("<col=[a-fA-F0-9]+>", replacementColorText);
						oldEntry.setTarget(newTarget);
					}
					takeEntriesInSetup.add(oldEntry);
				}
				else
				{
					takeEntriesNotInSetup.add(oldEntry);
				}
			}

			if (config.groundItemMenuSwap())
			{
				// Based on the swap priority config, figure out the starting indexes for the entries in and not in the setup
				int entriesInSetupStartIndex = firstTakeIndex + takeEntriesNotInSetup.size();
				int entriesNotInSetupStartIndex = firstTakeIndex;


				for (int i = 0; i < takeEntriesInSetup.size(); i++)
				{
					clientEntries[entriesInSetupStartIndex + i] = takeEntriesInSetup.get(i);
				}

				for (int i = 0; i < takeEntriesNotInSetup.size(); i++)
				{
					clientEntries[entriesNotInSetupStartIndex + i] = takeEntriesNotInSetup.get(i);
				}
			}

			client.setMenuEntries(clientEntries);

		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{

		Widget bankWidget = client.getWidget(ComponentID.BANK_TITLE_BAR);
		if (bankWidget == null || bankWidget.isHidden())
		{
			return;
		}

		// Adds menu entries to show worn items button
		if (event.getOption().equals("Show worn items"))
		{
			createMenuEntriesForWornItems();
		}
		// If shift is held and item is right clicked in the bank while a setup is active,
		// add item to additional filtered items
		else if (panel.getCurrentSelectedSetup() != null
				&& event.getActionParam1() == ComponentID.BANK_ITEM_CONTAINER
				&& client.isKeyPressed(KeyCode.KC_SHIFT)
				&& event.getOption().equals("Examine"))
		{
			createMenuEntryToAddAdditionalFilteredItem(event.getActionParam0());
		}
	}

	private void createMenuEntriesForWornItems()
	{
		List<InventorySetup> filteredSetups = panel.getFilteredInventorysetups();
		List<InventorySetup> setupsToShowOnWornItemsList;
		switch (config.showWornItemsFilter())
		{
			case BANK_FILTERED:
				setupsToShowOnWornItemsList = inventorySetups.stream()
						.filter(InventorySetup::isFilterBank)
						.collect(Collectors.toList());
				break;
			case FAVORITED:
				setupsToShowOnWornItemsList = inventorySetups.stream()
						.filter(InventorySetup::isFavorite)
						.collect(Collectors.toList());
				break;
			default:
				setupsToShowOnWornItemsList = filteredSetups;
				break;
		}

		// Section mode creates the section entries and sub menus
		if (config.sectionMode() && config.wornItemSelectionSubmenu())
		{

			List<InventorySetup> unassignedSetups = new ArrayList<>();
			HashMap<String, List<InventorySetup>> sectionsToDisplay = new HashMap<>();

			// If the sorting mode is default, then the order to appear on the worn items list
			// should be the order they appear in the section, which may not be the filtered order.
			if (config.sortingMode() == InventorySetupsSortingID.DEFAULT)
			{
				Set<String> setupsToShowOnWornItemsListCache = setupsToShowOnWornItemsList.stream()
						.map(InventorySetup::getName)
						.collect(Collectors.toSet());
				sections.forEach(section ->
				{
					List<String> setupsInSection =  section.getSetups();
					setupsInSection.forEach(setupName ->
					{
						if (setupsToShowOnWornItemsListCache.contains(setupName))
						{
							if (!sectionsToDisplay.containsKey(section.getName()))
							{
								sectionsToDisplay.put(section.getName(), new ArrayList<>());
							}
							final InventorySetup inventorySetup = cache.getInventorySetupNames().get(setupName);
							sectionsToDisplay.get(section.getName()).add(inventorySetup);
						}
					});
				});

				setupsToShowOnWornItemsList.forEach(setupToShow ->
				{
					Map<String, InventorySetupsSection> sectionsOfSetup = cache.getSetupSectionsMap().get(setupToShow.getName());
					if (sectionsOfSetup.isEmpty())
					{
						unassignedSetups.add(setupToShow);
					}
				});
			}
			else
			{
				setupsToShowOnWornItemsList.forEach(setupToShow ->
				{
					Map<String, InventorySetupsSection> sectionsOfSetup = cache.getSetupSectionsMap().get(setupToShow.getName());
					if (sectionsOfSetup.isEmpty())
					{
						unassignedSetups.add(setupToShow);
					}
					else
					{
						for (final InventorySetupsSection section : sectionsOfSetup.values())
						{
							if (!sectionsToDisplay.containsKey(section.getName()))
							{
								sectionsToDisplay.put(section.getName(), new ArrayList<>());
							}
							sectionsToDisplay.get(section.getName()).add(setupToShow);
						}
					}
				});
			}

			sections.forEach(section ->
			{

				if (!sectionsToDisplay.containsKey(section.getName()))
				{
					return;
				}

				Color sectionMenuTargetColor = section.getDisplayColor() == null ? JagexColors.MENU_TARGET : section.getDisplayColor();
				MenuEntry menuEntry = client.createMenuEntry(1)
						.setOption(OPEN_SECTION_MENU_ENTRY)
						.setTarget(ColorUtil.prependColorTag(section.getName(), sectionMenuTargetColor))
						.setType(MenuAction.RUNELITE_WIDGET);

				for (final InventorySetup inventorySetup : sectionsToDisplay.get(section.getName()))
				{
					createSectionSubMenuOnWornItems(inventorySetup, menuEntry);
				}

			});

			if (!unassignedSetups.isEmpty())
			{
				MenuEntry unassignedSectionMenuEntry = client.createMenuEntry(1)
						.setOption(OPEN_SECTION_MENU_ENTRY)
						.setTarget(ColorUtil.prependColorTag(UNASSIGNED_SECTION_SETUP_MENU_ENTRY, JagexColors.MENU_TARGET))
						.setType(MenuAction.RUNELITE_WIDGET);

				unassignedSetups.forEach(setup -> createSectionSubMenuOnWornItems(setup, unassignedSectionMenuEntry));
			}

		}
		else
		{
			for (int i = 0; i < setupsToShowOnWornItemsList.size(); i++)
			{
				final InventorySetup setupToShow = setupsToShowOnWornItemsList.get(setupsToShowOnWornItemsList.size() - 1 - i);
				Color menuTargetColor = setupToShow.getDisplayColor() == null ? JagexColors.MENU_TARGET : setupToShow.getDisplayColor();
				client.createMenuEntry(-1)
						.setOption(OPEN_SETUP_MENU_ENTRY)
						.setTarget(ColorUtil.prependColorTag(setupToShow.getName(), menuTargetColor))
						.setType(MenuAction.RUNELITE)
						.onClick(e ->
						{
							resetBankSearch(true);
							panel.setCurrentInventorySetup(setupToShow, true);
						});
			}
		}

		if (panel.getCurrentSelectedSetup() != null)
		{
			// add menu entry to filter add items
			client.createMenuEntry(-1)
					.setOption(FILTER_ADD_ITEMS_ENTRY)
					.setType(MenuAction.RUNELITE)
					.onClick(e -> doBankSearch(InventorySetupsFilteringModeID.ADDITIONAL_FILTERED_ITEMS));

			// add menu entry to filter equipment
			client.createMenuEntry(-1)
					.setOption(FILTER_EQUIPMENT_ENTRY)
					.setType(MenuAction.RUNELITE)
					.onClick(e -> doBankSearch(InventorySetupsFilteringModeID.EQUIPMENT));

			// add menu entry to filter inventory
			client.createMenuEntry(-1)
					.setOption(FILTER_INVENTORY_ENTRY)
					.setType(MenuAction.RUNELITE)
					.onClick(e -> doBankSearch(InventorySetupsFilteringModeID.INVENTORY));

			// add menu entry to filter all
			client.createMenuEntry(-1)
					.setOption(FILTER_ALL_ENTRY)
					.setType(MenuAction.RUNELITE)
					.onClick(e -> doBankSearch(InventorySetupsFilteringModeID.ALL));

			// add menu entry to close setup
			client.createMenuEntry(-1)
					.setOption(RETURN_TO_OVERVIEW_ENTRY)
					.setType(MenuAction.RUNELITE)
					.onClick(e -> panel.returnToOverviewPanel(false));
		}
	}

	private void createSectionSubMenuOnWornItems(InventorySetup setup, MenuEntry menuEntry)
	{
		Color setupMenuTargetColor = setup.getDisplayColor() == null ? JagexColors.MENU_TARGET : setup.getDisplayColor();

		menuEntry.createSubMenu().createMenuEntry(-1)
			.setOption(OPEN_SETUP_MENU_ENTRY)
			.setTarget(ColorUtil.prependColorTag(setup.getName(), setupMenuTargetColor))
			.setType(MenuAction.RUNELITE)
			.onClick(e ->
			{
				resetBankSearch(true);
				panel.setCurrentInventorySetup(setup, true);
			});
	}

	private void createMenuEntryToAddAdditionalFilteredItem(int inventoryIndex)
	{
		client.createMenuEntry(-1)
				.setOption(ADD_TO_ADDITIONAL_ENTRY)
				.onClick(e ->
				{
					final Item newItem = retrieveItemFromBankMenuEntry(inventoryIndex);
					if (newItem == null)
					{
						return;
					}

					final Map<Integer, InventorySetupsItem> additionalFilteredItems =
							panel.getCurrentSelectedSetup().getAdditionalFilteredItems();

					// Item already exists, don't add it again
					if (!additionalFilteredItemsHasItem(newItem.getId(), additionalFilteredItems))
					{
						addAdditionalFilteredItem(newItem.getId(), additionalFilteredItems);
					}
				});
	}

	// Retrieve an item from a selected menu entry in the bank
	private Item retrieveItemFromBankMenuEntry(int inventoryIndex)
	{
		// This should never be hit, as the option only appears when the panel isn't null
		if (panel.getCurrentSelectedSetup() == null)
		{
			return null;
		}

		ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
		if (bankContainer == null)
		{
			return null;
		}
		Item[] items = bankContainer.getItems();
		if (inventoryIndex < 0 || inventoryIndex >= items.length)
		{
			return null;
		}
		return bankContainer.getItems()[inventoryIndex];
	}

	@Subscribe
	private void onWidgetClosed(WidgetClosed event)
	{

		if (event.getGroupId() == InterfaceID.BANK && !config.persistHotKeysOutsideBank())
		{
			unregisterHotkeys();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// when the bank is loaded up allowing filtering again
		// this is to make it so the bank will refilter if a tab was clicked and then the player exited the bank
		if (event.getGroupId() == InterfaceID.BANK)
		{
			// If manual bank filter is selected, don't allow filtering when the bank is opened
			// filtering will only occur if the user selects a setup or uses a filtering hotkey
			// while the bank is already open
			internalFilteringIsAllowed = !config.manualBankFilter();

			if (panel.getCurrentSelectedSetup() != null && panel.getCurrentSelectedSetup().isFilterBank() && isFilteringAllowed())
			{
				// start a bank search so the bank is filtered when it's opened
				doBankSearch();
			}

			if (!config.persistHotKeysOutsideBank())
			{
				registerHotkeys();
			}

		}
	}

	public void setConfigValue(final String key, boolean on)
	{
		configManager.setConfiguration(CONFIG_GROUP, key, on);
	}

	public void setConfigValue(final String key, final String value)
	{
		configManager.setConfiguration(CONFIG_GROUP, key, value);
	}

	public boolean getBooleanConfigValue(final String key)
	{
		try
		{
			String value = configManager.getConfiguration(CONFIG_GROUP, key);
			return Boolean.parseBoolean(value);
		}
		catch (Exception e)
		{
			log.error("Couldn't retrieve config value with key " + key, e);
			return false;
		}
	}

	public void toggleAlphabeticalMode(InventorySetupsSortingID mode)
	{
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SORTING_MODE, mode);
	}

	public String getSavedVersionString()
	{
		final String versionStr = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_VERSION_STR);
		return versionStr == null ? "" : versionStr;
	}

	public void setSavedVersionString(final String newVersion)
	{
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_VERSION_STR, newVersion);
	}

	public String getCurrentVersionString()
	{
		return currentVersion;
	}

	@Override
	public void startUp()
	{
		// get current version of the plugin using properties file generated by build.gradle
		try
		{
			this.currentVersion = "1.16";
		}
		catch (Exception e)
		{
			log.warn("Could not determine current plugin version", e);
			this.currentVersion = "";
		}

		this.internalFilteringIsAllowed = true;
		this.panel = new InventorySetupsPluginPanel(this, itemManager);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "inventorysetups_icon.png");

		this.navButtonIsSelected = false;
		navButton = NavigationButton.builder()
				.tooltip("Inventory Setups")
				.icon(icon)
				.priority(6)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);

		bankFilteringMode = InventorySetupsFilteringModeID.ALL;

		this.cache = new InventorySetupsCache();
		this.inventorySetups = new ArrayList<>();
		this.sections = new ArrayList<>();
		this.dataManager = new InventorySetupsPersistentDataManager(this, panel, configManager, cache, gson, inventorySetups, sections);

		// load all the inventory setups from the config file
		clientThread.invokeLater(() ->
		{
			switch (client.getGameState())
			{
				case STARTING:
				case UNKNOWN:
					return false;
			}

			clientThread.invokeLater(() ->
			{
				dataManager.loadConfig();
				SwingUtilities.invokeLater(() -> panel.redrawOverviewPanel(true));
			});

			return true;
		});

	}

	public void addInventorySetup()
	{
		final String msg = "Enter the name of this setup (max " + MAX_SETUP_NAME_LENGTH + " chars).";
		String name = JOptionPane.showInputDialog(panel,
				msg,
				"Add New Setup",
				JOptionPane.PLAIN_MESSAGE);

		// cancel button was clicked
		if (name == null || name.isEmpty())
		{
			return;
		}

		if (name.length() > MAX_SETUP_NAME_LENGTH)
		{
			name = name.substring(0, MAX_SETUP_NAME_LENGTH);
		}

		if (cache.getInventorySetupNames().containsKey(name))
		{
			JOptionPane.showMessageDialog(panel,
					"A setup with the name " + name + " already exists",
					"Setup Already Exists",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		final String newName = name;

		clientThread.invokeLater(() ->
		{
			List<InventorySetupsItem> inv = getNormalizedContainer(InventoryID.INVENTORY);
			List<InventorySetupsItem> eqp = getNormalizedContainer(InventoryID.EQUIPMENT);

			List<InventorySetupsItem> runePouchData = null;
			final InventorySetupsRunePouchType inventoryRunePouchType = getRunePouchTypeFromContainer(inv);
			List<InventorySetupsItem> boltPouchData = null;
			final boolean inventoryHasBoltPouch = containerContainsBoltPouch(inv);

			if (inventoryRunePouchType != InventorySetupsRunePouchType.NONE)
			{
				runePouchData = getRunePouchData(inventoryRunePouchType);
			}

			if (inventoryHasBoltPouch)
			{
				boltPouchData = getBoltPouchData();
			}

			int spellbook = getCurrentSpellbook();

			final InventorySetup invSetup = new InventorySetup(inv, eqp, runePouchData, boltPouchData, new HashMap<>(), newName, "",
					config.highlightColor(),
					config.highlightDifference(),
					config.enableDisplayColor() ? config.displayColor() : null,
					config.bankFilter(),
					config.highlightUnorderedDifference(),
					spellbook, false, -1);

			cache.addSetup(invSetup);
			inventorySetups.add(invSetup);
			dataManager.updateConfig(true, false);
			SwingUtilities.invokeLater(() -> panel.redrawOverviewPanel(false));

		});
	}

	public void addSection()
	{
		final String msg = "Enter the name of this section (max " + MAX_SETUP_NAME_LENGTH + " chars).";
		String name = JOptionPane.showInputDialog(panel,
				msg,
				"Add New Section",
				JOptionPane.PLAIN_MESSAGE);

		// cancel button was clicked
		if (name == null || name.isEmpty())
		{
			return;
		}

		if (name.length() > MAX_SETUP_NAME_LENGTH)
		{
			name = name.substring(0, MAX_SETUP_NAME_LENGTH);
		}

		if (cache.getSectionNames().containsKey(name))
		{
			JOptionPane.showMessageDialog(panel,
					"A section with the name " + name + " already exists",
					"Section Already Exists",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		final String newName = name;
		InventorySetupsSection newSection = new InventorySetupsSection(newName);
		cache.addSection(newSection);
		sections.add(newSection);

		dataManager.updateConfig(false, true);
		SwingUtilities.invokeLater(() -> panel.redrawOverviewPanel(false));

	}

	public void addSetupToSections(final InventorySetup setup, final List<String> sectionNames)
	{
		for (final String sectionName : sectionNames)
		{
			// Don't add the setup if it's already part of this section
			if (!cache.getSectionSetupsMap().get(sectionName).containsKey(setup.getName()))
			{
				final InventorySetupsSection section = cache.getSectionNames().get(sectionName);
				cache.addSetupToSection(section, setup);
				section.getSetups().add(setup.getName());
			}
		}

		dataManager.updateConfig(false, true);
		panel.redrawOverviewPanel(false);
	}

	public void addSetupsToSection(final InventorySetupsSection section, final List<String> setupNames)
	{
		for (final String setupName : setupNames)
		{
			// Don't add the setup if it's already part of this section
			if (!cache.getSectionSetupsMap().get(section.getName()).containsKey(setupName))
			{
				final InventorySetup setup = cache.getInventorySetupNames().get(setupName);
				cache.addSetupToSection(section, setup);
				section.getSetups().add(setupName);
			}
		}
		dataManager.updateConfig(false, true);
		panel.redrawOverviewPanel(false);
	}

	public void moveSetup(int invIndex, int newPosition)
	{
		// Setup is already in the specified position or is out of position
		if (isNewPositionInvalid(invIndex, newPosition, inventorySetups.size()))
		{
			return;
		}
		InventorySetup setup = inventorySetups.remove(invIndex);
		inventorySetups.add(newPosition, setup);
		panel.redrawOverviewPanel(false);
		dataManager.updateConfig(true, false);
	}

	public void moveSection(int sectionIndex, int newPosition)
	{
		// Setup is already in the specified position or is out of position
		if (isNewPositionInvalid(sectionIndex, newPosition, sections.size()))
		{
			return;
		}
		InventorySetupsSection section = sections.remove(sectionIndex);
		sections.add(newPosition, section);
		panel.redrawOverviewPanel(false);
		dataManager.updateConfig(false, true);
	}

	public void moveSetupWithinSection(final InventorySetupsSection section, int invIndex, int newPosition)
	{
		// Setup is already in the specified position or is out of position
		if (isNewPositionInvalid(invIndex, newPosition, section.getSetups().size()))
		{
			return;
		}
		final String setupName = section.getSetups().remove(invIndex);
		section.getSetups().add(newPosition, setupName);
		panel.redrawOverviewPanel(false);
		dataManager.updateConfig(false, true);
	}

	private boolean isNewPositionInvalid(int oldPosition, int newPosition, int size)
	{
		return oldPosition == newPosition || newPosition < 0 || newPosition >= size;
	}

	public List<InventorySetup> filterSetups(String textToFilter)
	{
		final String textToFilterLower = textToFilter.toLowerCase();
		return inventorySetups.stream()
				.filter(i -> i.getName().toLowerCase().contains(textToFilterLower))
				.collect(Collectors.toList());
	}

	public void doBankSearch(final InventorySetupsFilteringModeID filteringModeID)
	{
		bankFilteringMode = filteringModeID;
		doBankSearch();
	}

	public void doBankSearch()
	{
		final InventorySetup currentSelectedSetup = panel.getCurrentSelectedSetup();
		internalFilteringIsAllowed = true;

		if (currentSelectedSetup != null && currentSelectedSetup.isFilterBank())
		{

			clientThread.invoke(() ->
			{
				client.setVarbit(Varbits.CURRENT_BANK_TAB, 0);
				bankSearch.layoutBank();

				// When tab is selected with search window open, the search window closes but the search button
				// stays highlighted, this solves that issue
				Widget bankContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
				if (bankContainer != null && !bankContainer.isHidden())
				{
					Widget searchBackground = client.getWidget(ComponentID.BANK_SEARCH_BUTTON_BACKGROUND);
					searchBackground.setSpriteId(SpriteID.EQUIPMENT_SLOT_TILE);
				}
			});
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{

		if (event.getMenuAction() == MenuAction.RUNELITE)
		{
			return;
		}

		if (panel.getCurrentSelectedSetup() == null)
		{
			return;
		}

		if (event.getParam1() == ComponentID.BANK_ITEM_CONTAINER && event.getMenuOption().startsWith("View tab"))
		{
			if (config.disableBankTabBar())
			{
				event.consume();
			}
		}
		else if (panel.getCurrentSelectedSetup() != null
				&& (event.getMenuOption().startsWith("View tab") || event.getMenuOption().equals("View all items")))
		{
			internalFilteringIsAllowed = false;
		}
	}

	private boolean additionalFilteredItemsHasItem(int itemId, final Map<Integer, InventorySetupsItem> additionalFilteredItems)
	{
		final int canonicalizedId = itemManager.canonicalize(itemId);
		for (final Integer additionalItemKey : additionalFilteredItems.keySet())
		{
			boolean isFuzzy = additionalFilteredItems.get(additionalItemKey).isFuzzy();
			int addItemId = getProcessedID(isFuzzy, additionalFilteredItems.get(additionalItemKey).getId());
			int finalItemId = getProcessedID(isFuzzy, canonicalizedId);
			if (addItemId == finalItemId)
			{
				return true;
			}
		}
		return false;
	}

	private void addAdditionalFilteredItem(int itemId, final Map<Integer, InventorySetupsItem> additionalFilteredItems)
	{
		// un-noted, un-placeholdered ID
		final int processedItemId = itemManager.canonicalize(itemId);

		clientThread.invokeLater(() ->
		{
			final String name = itemManager.getItemComposition(processedItemId).getName();
			InventorySetupsStackCompareID stackCompareType = panel.isStackCompareForSlotAllowed(InventorySetupsSlotID.ADDITIONAL_ITEMS, 0) ? config.stackCompareType() : InventorySetupsStackCompareID.None;
			final InventorySetupsItem setupItem = new InventorySetupsItem(processedItemId, name, 1, config.fuzzy(), stackCompareType);

			additionalFilteredItems.put(processedItemId, setupItem);
			dataManager.updateConfig(true, false);
			panel.refreshCurrentSetup();
		});
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{

		if (event.getIndex() == 439 && client.getGameState() == GameState.LOGGED_IN)
		{
			// must be invoked later otherwise causes freezing.
			clientThread.invokeLater(() ->
			{
				panel.highlightSpellbook();
			});
		}

	}

	public void resetBankSearch(boolean closeChat)
	{
		// Only reset the bank automatically if filtering is allowed
		// This makes it so that you click the search button again to cancel a filter
		if (isFilteringAllowed())
		{
			bankSearch.reset(closeChat);
		}
	}

	public List<InventorySetupsItem> getRunePouchData(final InventorySetupsRunePouchType runePouchType)
	{
		List<InventorySetupsItem> runePouchData = new ArrayList<>();
		EnumComposition runepouchEnum = client.getEnum(982);
		for (int i = 0; i < runePouchType.getSize(); i++)
		{
			final int varbitVal = client.getVarbitValue(RUNE_POUCH_RUNE_VARBITS.get(i));
			if (varbitVal == 0)
			{
				runePouchData.add(InventorySetupsItem.getDummyItem());
			}
			else
			{
				final int runeId = runepouchEnum.getIntValue(varbitVal);
				int runeAmount = client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS.get(i));
				String runeName = itemManager.getItemComposition(runeId).getName();
				InventorySetupsStackCompareID stackCompareType = panel.isStackCompareForSlotAllowed(InventorySetupsSlotID.RUNE_POUCH, i) ? config.stackCompareType() : InventorySetupsStackCompareID.None;
				runePouchData.add(new InventorySetupsItem(runeId, runeName, runeAmount, false, stackCompareType));
			}
		}

		return runePouchData;
	}

	public List<InventorySetupsItem> getBoltPouchData()
	{
		List<InventorySetupsItem> boltPouchData = new ArrayList<>();

		for (int i = 0; i < BOLT_POUCH_BOLT_VARBIT_IDS.size(); i++)
		{
			int boltVarbitId = client.getVarbitValue(BOLT_POUCH_BOLT_VARBIT_IDS.get(i));
			Bolts bolt = Bolts.getBolt(boltVarbitId);
			boolean boltNotFound = bolt == null;
			int boltAmount = boltNotFound ? 0 : client.getVarbitValue(BOLT_POUCH_AMOUNT_VARBIT_IDS.get(i));
			String boltName = boltNotFound ? "" : itemManager.getItemComposition(bolt.getItemId()).getName();
			int boltItemId = boltNotFound ? -1 : bolt.getItemId();

			if (boltItemId == -1)
			{
				boltPouchData.add(InventorySetupsItem.getDummyItem());
			}
			else
			{
				InventorySetupsStackCompareID stackCompareType =
						panel.isStackCompareForSlotAllowed(InventorySetupsSlotID.BOLT_POUCH, i)
								? config.stackCompareType() : InventorySetupsStackCompareID.None;
				boltPouchData.add(new InventorySetupsItem(boltItemId, boltName, boltAmount, false, stackCompareType));
			}
		}

		return boltPouchData;
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		String eventName = event.getEventName();

		int[] intStack = client.getIntStack();
		int intStackSize = client.getIntStackSize();

		switch (eventName)
		{
			case "bankSearchFilter":
			{
				final InventorySetup currentSetup = panel.getCurrentSelectedSetup();
				// Shared storage uses the bankmain filter scripts too. Allow using tag searches in it but don't
				// apply the tag search from the active tab.
				final boolean bankOpen = client.getItemContainer(InventoryID.BANK) != null;
				if (bankOpen && currentSetup != null && currentSetup.isFilterBank() && isFilteringAllowed())
				{
					int itemId = intStack[intStackSize - 1];
					boolean containsItem = false;
					switch (bankFilteringMode)
					{
						case ALL:
							containsItem = setupContainsItem(currentSetup, itemId);
							break;
						case INVENTORY:
							boolean runePouchContainsItem = false;
							if (currentSetup.getRune_pouch() != null)
							{
								runePouchContainsItem = containerContainsItem(itemId, currentSetup.getRune_pouch());
							}
							boolean boltPouchContainsItem = false;
							if (currentSetup.getBoltPouch() != null)
							{
								boltPouchContainsItem = containerContainsItem(itemId, currentSetup.getBoltPouch());
							}
							containsItem = runePouchContainsItem || boltPouchContainsItem ||
									containerContainsItem(itemId, currentSetup.getInventory());
							break;
						case EQUIPMENT:
							containsItem = containerContainsItem(itemId, currentSetup.getEquipment());
							break;
						case ADDITIONAL_FILTERED_ITEMS:
							containsItem = additionalFilteredItemsHasItem(itemId, currentSetup.getAdditionalFilteredItems());
							break;
					}
					if (containsItem)
					{
						// return true
						intStack[intStackSize - 2] = 1;
					}
					else
					{
						intStack[intStackSize - 2] = 0;
					}
				}
				break;
			}
			case "getSearchingTagTab":
				// Clicking on a bank tab that isn't the first one (main tab),
				// then filtering the bank (either by selecting a setup or hotkey),
				// then clicking on "item" or "note" would cause the bank to show the tab
				// and remove the filter. This stops this from happening.
				final InventorySetup currentSetup = panel.getCurrentSelectedSetup();
				if (currentSetup != null && currentSetup.isFilterBank() && isFilteringAllowed())
				{
					intStack[intStackSize - 1] = 1;
				}
				else
				{
					intStack[intStackSize - 1] = 0;
				}
				break;
		}


	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
		{
			// Bankmain_build will reset the bank title to "The Bank of Gielinor". So apply our own title.
			if (panel.getCurrentSelectedSetup() != null && panel.getCurrentSelectedSetup().isFilterBank() && isFilteringAllowed())
			{
				String postTitle = " - ";
				switch (bankFilteringMode)
				{
					case ALL:
						postTitle += "All Items";
						break;
					case INVENTORY:
						postTitle += "Inventory";
						break;
					case EQUIPMENT:
						postTitle += "Equipment";
						break;
					case ADDITIONAL_FILTERED_ITEMS:
						postTitle += "Additional Items";
						break;
				}
				Widget bankTitle = client.getWidget(ComponentID.BANK_TITLE_BAR);
				bankTitle.setText("Inventory Setup <col=ff0000>" + panel.getCurrentSelectedSetup().getName() + postTitle + "</col>");
			}
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_SEARCH_TOGGLE)
		{
			// cancel the current filtering if the search button is clicked
			resetBankSearch(true);

			// don't allow the bank to retry a filter if the search button is clicked
			internalFilteringIsAllowed = false;
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_SEARCHING)
		{
			// The return value of bankmain_searching is on the stack. If we have a setup active
			// make it return true to put the bank in a searching state.
			boolean bankOpen = client.getItemContainer(InventoryID.BANK) != null;
			if (bankOpen && panel.getCurrentSelectedSetup() != null && panel.getCurrentSelectedSetup().isFilterBank() && isFilteringAllowed())
			{
				client.getIntStack()[client.getIntStackSize() - 1] = 1; // true
			}
		}

		if (event.getScriptId() != ScriptID.BANKMAIN_BUILD)
		{
			return;
		}

		int items = 0;

		Widget itemContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
		if (itemContainer == null)
		{
			return;
		}

		if (panel.getCurrentSelectedSetup() != null && config.removeBankTabSeparator() && isFilteringAllowed())
		{
			Widget[] containerChildren = itemContainer.getDynamicChildren();

			// sort the child array as the items are not in the displayed order
			Arrays.sort(containerChildren, Comparator.comparing(Widget::getOriginalY).thenComparing(Widget::getOriginalX));

			for (Widget child : containerChildren)
			{
				if (child.getItemId() != -1 && !child.isHidden())
				{
					// calculate correct item position as if this was a normal tab
					int adjYOffset = (items / ITEMS_PER_ROW) * ITEM_VERTICAL_SPACING;
					int adjXOffset = (items % ITEMS_PER_ROW) * ITEM_HORIZONTAL_SPACING + ITEM_ROW_START;

					if (child.getOriginalY() != adjYOffset)
					{
						child.setOriginalY(adjYOffset);
						child.revalidate();
					}

					if (child.getOriginalX() != adjXOffset)
					{
						child.setOriginalX(adjXOffset);
						child.revalidate();
					}

					items++;
				}

				// separator line or tab text
				if (child.getSpriteId() == SpriteID.RESIZEABLE_MODE_SIDE_PANEL_BACKGROUND || child.getText().contains("Tab"))
				{
					child.setHidden(true);
				}
			}
		}

	}

	public void updateCurrentSetup(InventorySetup setup)
	{
		int confirm = JOptionPane.showConfirmDialog(panel,
				"Are you sure you want update this inventory setup?",
				"Warning", JOptionPane.OK_CANCEL_OPTION);

		// cancel button was clicked
		if (confirm != JOptionPane.YES_OPTION)
		{
			return;
		}

		// must be on client thread to get names
		clientThread.invokeLater(() ->
		{
			List<InventorySetupsItem> inv = getNormalizedContainer(InventoryID.INVENTORY);
			List<InventorySetupsItem> eqp = getNormalizedContainer(InventoryID.EQUIPMENT);

			// copy over fuzzy attributes
			for (int i = 0; i < inv.size(); i++)
			{
				inv.get(i).setFuzzy(setup.getInventory().get(i).isFuzzy());
				inv.get(i).setStackCompare(setup.getInventory().get(i).getStackCompare());
			}
			for (int i = 0; i < eqp.size(); i++)
			{
				eqp.get(i).setFuzzy(setup.getEquipment().get(i).isFuzzy());
				eqp.get(i).setStackCompare(setup.getEquipment().get(i).getStackCompare());
			}

			List<InventorySetupsItem> runePouchData = null;
			final InventorySetupsRunePouchType inventoryRunePouchType = getRunePouchTypeFromContainer(inv);
			if (inventoryRunePouchType != InventorySetupsRunePouchType.NONE)
			{
				runePouchData = getRunePouchData(inventoryRunePouchType);
			}

			List<InventorySetupsItem> boltPouchData = null;
			if (containerContainsItem(ItemID.BOLT_POUCH, inv))
			{
				boltPouchData = getBoltPouchData();
			}

			setup.updateRunePouch(runePouchData);
			setup.updateBoltPouch(boltPouchData);
			setup.updateInventory(inv);
			setup.updateEquipment(eqp);
			setup.updateSpellbook(getCurrentSpellbook());
			dataManager.updateConfig(true, false);
			panel.refreshCurrentSetup();
		});
	}

	private void updateAllInstancesInContainerSetupWithNewItem(final InventorySetup inventorySetup, List<InventorySetupsItem> containerToUpdate,
															   final InventorySetupsItem oldItem, final InventorySetupsItem newItem, final InventorySetupsSlotID id)
	{
		for (int i = 0; i < containerToUpdate.size(); i++)
		{
			final InventorySetupsItem item = containerToUpdate.get(i);
			if (item.getId() == oldItem.getId())
			{
				boolean runePouchValid = checkAndUpdateSlotIfRunePouchWasSelected(inventorySetup, id, containerToUpdate.get(i), newItem, false);
				boolean boltPouchValid = checkAndUpdateSlotIfBoltPouchWasSelected(inventorySetup, id, containerToUpdate.get(i), newItem, false);
				if (runePouchValid && boltPouchValid)
				{
					containerToUpdate.set(i, newItem);
				}
			}
		}
	}

	private void updateAllInstancesInSetupWithNewItem(final InventorySetupsItem oldItem, final InventorySetupsItem newItem)
	{
		if (oldItem.getId() == -1 || newItem.getId() == -1)
		{
			SwingUtilities.invokeLater(() ->
					JOptionPane.showMessageDialog(panel,
							"You cannot update empty slots or replace all slots with this item with an empty slot",
							"Cannot Update Setups",
							JOptionPane.ERROR_MESSAGE));

			return;
		}

		for (final InventorySetup inventorySetup : inventorySetups)
		{
			updateAllInstancesInContainerSetupWithNewItem(inventorySetup, inventorySetup.getInventory(), oldItem, newItem, InventorySetupsSlotID.INVENTORY);
			updateAllInstancesInContainerSetupWithNewItem(inventorySetup, inventorySetup.getEquipment(), oldItem, newItem, InventorySetupsSlotID.EQUIPMENT);
		}
	}

	public void updateSlotFromContainer(final InventorySetupsSlot slot, boolean updateAllInstances)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			JOptionPane.showMessageDialog(panel,
					"You must be logged in to update from " + (slot.getSlotID().toString().toLowerCase() + "."),
					"Cannot Update Item",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		final InventorySetupsItem oldItem = getContainerFromSlot(slot).get(slot.getIndexInSlot());
		final boolean isFuzzy = oldItem.isFuzzy();
		final InventorySetupsStackCompareID stackCompareType = oldItem.getStackCompare();

		// must be invoked on client thread to get the name
		clientThread.invokeLater(() ->
		{
			final List<InventorySetupsItem> playerContainer = getNormalizedContainer(slot.getSlotID());
			final InventorySetupsItem newItem = playerContainer.get(slot.getIndexInSlot());
			newItem.setFuzzy(isFuzzy);
			newItem.setStackCompare(stackCompareType);

			if (updateAllInstances)
			{
				updateAllInstancesInSetupWithNewItem(oldItem, newItem);
			}
			else
			{
				List<InventorySetupsItem> containerToUpdate =  getContainerFromID(slot.getParentSetup(), slot.getSlotID());
				boolean runePouchValid = checkAndUpdateSlotIfRunePouchWasSelected(slot.getParentSetup(), slot.getSlotID(), oldItem, newItem, true);
				boolean boltPouchValid = checkAndUpdateSlotIfBoltPouchWasSelected(slot.getParentSetup(), slot.getSlotID(), oldItem, newItem, true);
				if (runePouchValid && boltPouchValid)
				{
					containerToUpdate.set(slot.getIndexInSlot(), newItem);
				}
			}

			dataManager.updateConfig(true, false);
			panel.refreshCurrentSetup();
		});

	}

	public void updateSlotFromSearch(final InventorySetupsSlot slot, boolean allowStackable, boolean updateAllInstances)
	{

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			JOptionPane.showMessageDialog(panel,
					"You must be logged in to search.",
					"Cannot Search for Item",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		itemSearch
				.tooltipText("Set slot to")
				.onItemSelected((itemId) ->
				{
					clientThread.invokeLater(() ->
					{
						int finalId = itemManager.canonicalize(itemId);

						if (slot.getSlotID() == InventorySetupsSlotID.ADDITIONAL_ITEMS)
						{
							final Map<Integer, InventorySetupsItem> additionalFilteredItems =
									panel.getCurrentSelectedSetup().getAdditionalFilteredItems();
							if (!additionalFilteredItemsHasItem(finalId, additionalFilteredItems))
							{
								removeAdditionalFilteredItem(slot, additionalFilteredItems);
								addAdditionalFilteredItem(finalId, additionalFilteredItems);
							}
							return;
						}

						final String itemName = itemManager.getItemComposition(finalId).getName();
						final List<InventorySetupsItem> container = getContainerFromSlot(slot);
						final InventorySetupsItem itemToBeReplaced = container.get(slot.getIndexInSlot());
						final InventorySetupsItem newItem = new InventorySetupsItem(finalId, itemName, 1, itemToBeReplaced.isFuzzy(), itemToBeReplaced.getStackCompare());

						// NOTE: the itemSearch shows items from skill guides which can be selected, which may be highlighted

						// if the item is stackable, ask for a quantity
						if (allowStackable && itemManager.getItemComposition(finalId).isStackable())
						{
							searchInput = chatboxPanelManager.openTextInput("Enter amount")
									// only allow numbers and k, m, b (if 1 value is available)
									// stop once k, m, or b is seen
									.addCharValidator(this::validateCharFromItemSearch)
									.onDone((input) ->
									{
										int quantity = InventorySetupUtilities.parseTextInputAmount(input);
										newItem.setQuantity(quantity);
										updateSlotFromSearchHelper(slot, itemToBeReplaced, newItem, container, updateAllInstances);
									}).build();
						}
						else
						{
							updateSlotFromSearchHelper(slot, itemToBeReplaced, newItem, container, updateAllInstances);
						}
					});
				})
				.build();
	}

	private void updateSlotFromSearchHelper(final InventorySetupsSlot slot, final InventorySetupsItem itemToBeReplaced,
											final InventorySetupsItem newItem, final List<InventorySetupsItem> container,
											boolean updateAllInstances)
	{
		clientThread.invokeLater(() ->
		{
			if (updateAllInstances)
			{
				updateAllInstancesInSetupWithNewItem(itemToBeReplaced, newItem);
			}
			else
			{
				// update the rune pouch data
				if (!checkAndUpdateSlotIfRunePouchWasSelected(slot.getParentSetup(), slot.getSlotID(), container.get(slot.getIndexInSlot()), newItem))
				{
					return;
				}
				// update the bolt pouch data
				if (!checkAndUpdateSlotIfBoltPouchWasSelected(slot.getParentSetup(), slot.getSlotID(), container.get(slot.getIndexInSlot()), newItem))
				{
					return;
				}
				container.set(slot.getIndexInSlot(), newItem);
			}

			SwingUtilities.invokeLater(() ->
			{
				dataManager.updateConfig(true, false);
				panel.refreshCurrentSetup();
			});
		});
	}

	private boolean validateCharFromItemSearch(int arg)
	{
		// allow more numbers to be put in if a letter hasn't been detected
		boolean stillInputtingNumbers = arg >= '0' && arg <= '9' &&
				!searchInput.getValue().toLowerCase().contains("k") &&
				!searchInput.getValue().toLowerCase().contains("m") &&
				!searchInput.getValue().toLowerCase().contains("b");

		// if a letter is input, check if there isn't one already and the length is not 0
		boolean letterIsInput = (arg == 'b' || arg == 'B' ||
				arg == 'k' || arg == 'K' ||
				arg == 'm' || arg == 'M') &&
				searchInput.getValue().length() > 0 &&
				!searchInput.getValue().toLowerCase().contains("k") &&
				!searchInput.getValue().toLowerCase().contains("m") &&
				!searchInput.getValue().toLowerCase().contains("b");

		return stillInputtingNumbers || letterIsInput;
	}

	public void updateInventorySetupIcon(final InventorySetup setup)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			JOptionPane.showMessageDialog(panel,
					"You must be logged in to search.",
					"Cannot Search for Item",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		itemSearch
				.tooltipText("Set slot to")
				.onItemSelected((itemId) ->
				{
					int finalId = itemManager.canonicalize(itemId);
					setup.setIconID(finalId);
					dataManager.updateConfig(true, false);
					SwingUtilities.invokeLater(() -> panel.redrawOverviewPanel(false));
				}).build();
	}

	public void removeItemFromSlot(final InventorySetupsSlot slot)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			JOptionPane.showMessageDialog(panel,
					"You must be logged in to remove item from the slot.",
					"Cannot Remove Item",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		// must be invoked on client thread to get the name
		clientThread.invokeLater(() ->
		{

			if (slot.getSlotID() == InventorySetupsSlotID.ADDITIONAL_ITEMS)
			{
				removeAdditionalFilteredItem(slot, panel.getCurrentSelectedSetup().getAdditionalFilteredItems());
				dataManager.updateConfig(true, false);
				panel.refreshCurrentSetup();
				return;
			}

			final List<InventorySetupsItem> container = getContainerFromSlot(slot);

			// update the rune pouch data
			final InventorySetupsItem itemToBeReplaced = container.get(slot.getIndexInSlot());
			final InventorySetupsItem dummyItem = new InventorySetupsItem(-1, "", 0, itemToBeReplaced.isFuzzy(), itemToBeReplaced.getStackCompare());
			if (!checkAndUpdateSlotIfRunePouchWasSelected(slot.getParentSetup(), slot.getSlotID(), container.get(slot.getIndexInSlot()), dummyItem))
			{
				return;
			}

			// update the bolt pouch data
			if (!checkAndUpdateSlotIfBoltPouchWasSelected(slot.getParentSetup(), slot.getSlotID(), container.get(slot.getIndexInSlot()), dummyItem))
			{
				return;
			}

			container.set(slot.getIndexInSlot(), dummyItem);
			dataManager.updateConfig(true, false);
			panel.refreshCurrentSetup();
		});
	}

	public void toggleFuzzyOnSlot(final InventorySetupsSlot slot)
	{
		if (panel.getCurrentSelectedSetup() == null)
		{
			return;
		}

		if (slot.getSlotID() == InventorySetupsSlotID.ADDITIONAL_ITEMS)
		{
			// Empty slot was selected to be toggled, don't do anything
			if (slot.getIndexInSlot() >= slot.getParentSetup().getAdditionalFilteredItems().size())
			{
				return;
			}

			final Map<Integer, InventorySetupsItem> additionalFilteredItems = slot.getParentSetup().getAdditionalFilteredItems();
			final int slotID = slot.getIndexInSlot();
			int j = 0;
			Integer keyToMakeFuzzy = null;
			for (final Integer key : additionalFilteredItems.keySet())
			{
				if (slotID == j)
				{
					keyToMakeFuzzy = key;
					break;
				}
				j++;
			}
			additionalFilteredItems.get(keyToMakeFuzzy).toggleIsFuzzy();
		}
		else
		{
			final List<InventorySetupsItem> container = getContainerFromSlot(slot);
			container.get(slot.getIndexInSlot()).toggleIsFuzzy();
		}

		dataManager.updateConfig(true, false);
		panel.refreshCurrentSetup();
	}

	public void setStackCompareOnSlot(final InventorySetupsSlot slot, final InventorySetupsStackCompareID newStackCompare)
	{
		if (panel.getCurrentSelectedSetup() == null)
		{
			return;
		}

		final List<InventorySetupsItem> container = getContainerFromSlot(slot);
		container.get(slot.getIndexInSlot()).setStackCompare(newStackCompare);

		dataManager.updateConfig(true, false);
		panel.refreshCurrentSetup();
	}

	private void removeAdditionalFilteredItem(final InventorySetupsSlot slot, final Map<Integer, InventorySetupsItem> additionalFilteredItems)
	{

		assert panel.getCurrentSelectedSetup() != null : "Current setup is null";

		final int slotID = slot.getIndexInSlot();

		// Empty slot was selected to be removed, don't do anything
		if (slotID >= additionalFilteredItems.size())
		{
			return;
		}

		int j = 0;
		Integer keyToDelete = null;
		for (final Integer key : additionalFilteredItems.keySet())
		{
			if (slotID == j)
			{
				keyToDelete = key;
				break;
			}
			j++;
		}

		additionalFilteredItems.remove(keyToDelete);

	}

	public void updateSpellbookInSetup(int newSpellbook)
	{
		assert panel.getCurrentSelectedSetup() != null : "Setup is null";
		assert newSpellbook >= 0 && newSpellbook < 5 : "New spellbook out of range";

		clientThread.invokeLater(() ->
		{
			panel.getCurrentSelectedSetup().updateSpellbook(newSpellbook);
			dataManager.updateConfig(true, false);
			panel.refreshCurrentSetup();
		});

	}

	public void updateNotesInSetup(final InventorySetup setup, final String text)
	{
		clientThread.invokeLater(() ->
		{
			setup.updateNotes(text);
			dataManager.updateConfig(true, false);
		});
	}

	public void removeInventorySetup(final InventorySetup setup)
	{
		if (isDeletionConfirmed("Are you sure you want to permanently delete this inventory setup?", "Warning"))
		{
			// Remove the setup from any sections which have it
			for (final InventorySetupsSection section : sections)
			{
				if (cache.getSectionSetupsMap().get(section.getName()).containsKey(setup.getName()))
				{
					section.getSetups().remove(setup.getName());
				}
			}
			cache.removeSetup(setup);
			inventorySetups.remove(setup);
			panel.redrawOverviewPanel(false);
			dataManager.updateConfig(true, true);
		}
	}

	public void removeSection(final InventorySetupsSection section)
	{
		if (isDeletionConfirmed("Are you sure you want to permanently delete this section?", "Warning"))
		{
			cache.removeSection(section);
			sections.remove(section);
			panel.redrawOverviewPanel(false);
			dataManager.updateConfig(false, true);
		}
	}

	public void removeInventorySetupFromSection(final InventorySetup setup, final InventorySetupsSection section)
	{
		// No confirmation needed
		cache.removeSetupFromSection(section, setup);
		section.getSetups().remove(setup.getName());

		panel.redrawOverviewPanel(false);
		dataManager.updateConfig(false, true);
	}

	private boolean isDeletionConfirmed(final String message, final String title)
	{
		int confirm = JOptionPane.showConfirmDialog(panel,
				message, title, JOptionPane.OK_CANCEL_OPTION);

		return confirm == JOptionPane.YES_OPTION;
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged e)
	{
		switchProfile();
	}

	private void switchProfile()
	{
		// config will have changed to local file
		clientThread.invokeLater(() ->
		{
			dataManager.loadConfig();
			SwingUtilities.invokeLater(() -> panel.redrawOverviewPanel(true));
			return true;
		});
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{

		// check to see that the container is the equipment or inventory
		ItemContainer container = event.getItemContainer();

		if (container == client.getItemContainer(InventoryID.INVENTORY))
		{
			panel.highlightInventory();
		}
		else if (container == client.getItemContainer(InventoryID.EQUIPMENT))
		{
			panel.highlightEquipment();
		}

	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		panel.highlightInventory();
		panel.highlightEquipment();
		panel.highlightSpellbook();
	}

	// Must be called on client thread!
	public int getCurrentSpellbook()
	{
		assert client.isClientThread() : "getCurrentSpellbook must be called on Client Thread";
		return client.getVarbitValue(SPELLBOOK_VARBIT);
	}

	public List<InventorySetupsItem> getNormalizedContainer(final InventorySetupsSlotID id)
	{
		switch (id)
		{
			case INVENTORY:
				return getNormalizedContainer(InventoryID.INVENTORY);
			case EQUIPMENT:
				return getNormalizedContainer(InventoryID.EQUIPMENT);
			case RUNE_POUCH:
				return getRunePouchData(InventorySetupsRunePouchType.DIVINE);
			case BOLT_POUCH:
				return getBoltPouchData();
			default:
				assert false : "Wrong slot ID!";
				return null;
		}
	}

	public List<InventorySetupsItem> getNormalizedContainer(final InventoryID id)
	{
		assert id == InventoryID.INVENTORY || id == InventoryID.EQUIPMENT : "invalid inventory ID";

		final ItemContainer container = client.getItemContainer(id);

		List<InventorySetupsItem> newContainer = new ArrayList<>();

		Item[] items = null;
		if (container != null)
		{
			items = container.getItems();
		}

		int size = id == InventoryID.INVENTORY ? NUM_INVENTORY_ITEMS : NUM_EQUIPMENT_ITEMS;

		for (int i = 0; i < size; i++)
		{

			final InventorySetupsStackCompareID stackCompareType = panel != null && panel.isStackCompareForSlotAllowed(InventorySetupsSlotID.fromInventoryID(id), i) ?
					config.stackCompareType() : InventorySetupsStackCompareID.None;
			if (items == null || i >= items.length || items[i].getId() == -1)
			{
				// add a "dummy" item to fill the normalized container to the right size
				// this will be useful to compare when no item is in a slot
				newContainer.add(InventorySetupsItem.getDummyItem());
			}
			else
			{
				final Item item = items[i];
				String itemName = "";

				// only the client thread can retrieve the name. Therefore, do not use names to compare!
				if (client.isClientThread())
				{
					itemName = itemManager.getItemComposition(item.getId()).getName();
				}
				newContainer.add(new InventorySetupsItem(item.getId(), itemName, item.getQuantity(), config.fuzzy(), stackCompareType));
			}
		}

		return newContainer;
	}

	public void exportSetup(final InventorySetup setup)
	{
		final String json = gson.toJson(setup);
		final StringSelection contents = new StringSelection(json);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(contents, null);

		JOptionPane.showMessageDialog(panel,
				"Setup data was copied to clipboard.",
				"Export Setup Succeeded",
				JOptionPane.PLAIN_MESSAGE);
	}

	public void exportSection(final InventorySetupsSection section)
	{
		final String json = gson.toJson(section);
		final StringSelection contents = new StringSelection(json);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(contents, null);

		JOptionPane.showMessageDialog(panel,
				"Section data was copied to clipboard.",
				"Export Setup Succeeded",
				JOptionPane.PLAIN_MESSAGE);
	}

	public <T> void massExport(List<T> data, final String type, final String file_prefix)
	{

		final JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fileChooser.setDialogTitle("Choose Directory to Export " + type);
		fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));

		int returnValue = fileChooser.showSaveDialog(panel);
		if (returnValue == JFileChooser.APPROVE_OPTION)
		{
			final File directory = fileChooser.getSelectedFile();
			String login_name = client.getLocalPlayer() != null ? "_" + client.getLocalPlayer().getName() : "";
			login_name = login_name.replace(" ", "_");
			String newFileName = directory.getAbsolutePath() + "/" + file_prefix + login_name + ".json";
			newFileName = newFileName.replace("\\", "/");
			try
			{
				final String json = gson.toJson(data);
				FileOutputStream outputStream = new FileOutputStream(newFileName);
				outputStream.write(json.getBytes());
				outputStream.close();
			}
			catch (Exception e)
			{
				log.error("Couldn't mass export " + type, e);
				JOptionPane.showMessageDialog(panel,
						"Failed to export " + type + ".",
						"Mass Export Failed",
						JOptionPane.PLAIN_MESSAGE);
				return;
			}

			JLabel messageLabel = new JLabel("<html><center>All " + type + " were exported successfully to<br>" + newFileName);
			messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
			JOptionPane.showMessageDialog(panel,
					messageLabel,
					"Mass Export Succeeded",
					JOptionPane.PLAIN_MESSAGE);
		}
	}

	public void importSetup()
	{
		try
		{
			final String setup = JOptionPane.showInputDialog(panel,
					"Enter setup data",
					"Import New Setup",
					JOptionPane.PLAIN_MESSAGE);

			// cancel button was clicked
			if (setup == null)
			{
				return;
			}

			Type type = new TypeToken<InventorySetup>()
			{

			}.getType();


			final InventorySetup newSetup = gson.fromJson(setup, type);

			if (isImportedSetupInvalid(newSetup))
			{
				throw new RuntimeException("Imported setup was missing required fields");
			}

			preProcessNewSetup(newSetup);
			cache.addSetup(newSetup);
			inventorySetups.add(newSetup);

			dataManager.updateConfig(true, false);
			SwingUtilities.invokeLater(() -> panel.redrawOverviewPanel(false));

		}
		catch (Exception e)
		{
			log.error("Couldn't import setup", e);
			JOptionPane.showMessageDialog(panel,
					"Invalid setup data.",
					"Import Setup Failed",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	public void massImportSetups()
	{
		try
		{
			final Path path = showMassImportFolderDialog();
			if (path == null)
			{
				return;
			}
			final String json = new String(Files.readAllBytes(path));

			Type typeSetups = new TypeToken<ArrayList<InventorySetup>>()
			{

			}.getType();

			final ArrayList<InventorySetup> newSetups = gson.fromJson(json, typeSetups);

			// It's possible that the gson call succeeds but returns setups that have basically nothing
			// This can occur if trying to import a section file instead of a inventory setup file, since they share fields
			// Therefore, do some additional checking for required fields
			for (final InventorySetup setup : newSetups)
			{
				if (isImportedSetupInvalid(setup))
				{
					throw new RuntimeException("Mass import section file was missing required fields");
				}
			}

			for (final InventorySetup inventorySetup : newSetups)
			{
				preProcessNewSetup(inventorySetup);
				cache.addSetup(inventorySetup);
				inventorySetups.add(inventorySetup);
			}

			dataManager.updateConfig(true, false);
			SwingUtilities.invokeLater(() -> panel.redrawOverviewPanel(false));

		}
		catch (Exception e)
		{
			log.error("Couldn't mass import setups", e);
			JOptionPane.showMessageDialog(panel,
					"Invalid setup data.",
					"Mass Import Setup Failed",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private boolean isImportedSetupInvalid(final InventorySetup setup)
	{
		return setup.getName() == null || setup.getInventory() == null || setup.getEquipment() == null || setup.getAdditionalFilteredItems() == null;
	}

	public void importSection()
	{
		try
		{
			final String section = JOptionPane.showInputDialog(panel,
					"Enter section data",
					"Import New Section",
					JOptionPane.PLAIN_MESSAGE);

			// cancel button was clicked
			if (section == null)
			{
				return;
			}

			Type type = new TypeToken<InventorySetupsSection>()
			{

			}.getType();

			final InventorySetupsSection newSection = gson.fromJson(section, type);

			if (isImportedSectionValid(newSection))
			{
				throw new RuntimeException("Imported section was missing required fields");
			}

			preProcessNewSection(newSection);
			cache.addSection(newSection);
			sections.add(newSection);

			dataManager.updateConfig(false, true);
			SwingUtilities.invokeLater(() -> panel.redrawOverviewPanel(false));
		}
		catch (Exception e)
		{
			log.error("Couldn't import setup", e);
			JOptionPane.showMessageDialog(panel,
					"Invalid section data.",
					"Import section Failed",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	public void massImportSections()
	{
		try
		{
			final Path path = showMassImportFolderDialog();
			if (path == null)
			{
				return;
			}
			final String json = new String(Files.readAllBytes(path));

			Type typeSetups = new TypeToken<ArrayList<InventorySetupsSection>>()
			{

			}.getType();

			final ArrayList<InventorySetupsSection> newSections = gson.fromJson(json, typeSetups);

			// It's possible that the gson call succeeds but returns sections that have basically nothing
			// This can occur if trying to import an inventory setup file instead of a section file, since they share fields
			// Therefore, do some additional checking for required fields
			for (final InventorySetupsSection section : newSections)
			{
				if (isImportedSectionValid(section))
				{
					throw new RuntimeException("Mass import section file was missing required fields");
				}
			}

			for (final InventorySetupsSection section : newSections)
			{
				preProcessNewSection(section);
				cache.addSection(section);
				sections.add(section);
			}

			dataManager.updateConfig(false, true);
			SwingUtilities.invokeLater(() -> panel.redrawOverviewPanel(false));

		}
		catch (Exception e)
		{
			log.error("Couldn't mass import sections", e);
			JOptionPane.showMessageDialog(panel,
					"Invalid section data.",
					"Mass Import Section Failed",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private boolean isImportedSectionValid(final InventorySetupsSection section)
	{
		return section.getName() == null || section.getSetups() == null;
	}

	private void preProcessNewSection(final InventorySetupsSection newSection)
	{
		final String newName = InventorySetupUtilities.findNewName(newSection.getName(), cache.getSectionNames().keySet());
		newSection.setName(newName);

		// Remove any duplicates that came in when importing
		newSection.setSetups(newSection.getSetups().stream().distinct().collect(Collectors.toList()));

		// Remove setups which don't exist
		newSection.getSetups().removeIf(s -> !cache.getInventorySetupNames().containsKey(s));

	}

	private void preProcessNewSetup(final InventorySetup newSetup)
	{
		final String newName = InventorySetupUtilities.findNewName(newSetup.getName(), cache.getInventorySetupNames().keySet());
		newSetup.setName(newName);
	}

	private Path showMassImportFolderDialog()
	{
		final JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setDialogTitle("Choose Import File");
		FileFilter jsonFilter = new FileNameExtensionFilter("JSON files", "json");
		fileChooser.setFileFilter(jsonFilter);
		fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));

		int returnValue = fileChooser.showOpenDialog(panel);

		if (returnValue == JFileChooser.APPROVE_OPTION)
		{
			return Paths.get(fileChooser.getSelectedFile().getAbsolutePath());
		}
		else
		{
			return null;
		}
	}

	@Override
	public void shutDown()
	{
		resetBankSearch(true);
		clientToolbar.removeNavigation(navButton);
	}

	public boolean isHighlightingAllowed()
	{
		return client.getGameState() == GameState.LOGGED_IN;
	}

	public boolean isFilteringAllowed()
	{
		boolean allowBasedOnActivePanel = navButtonIsSelected || !config.requireActivePanelFilter();

		return internalFilteringIsAllowed && allowBasedOnActivePanel;
	}

	private List<InventorySetupsItem> getContainerFromSlot(final InventorySetupsSlot slot)
	{
		assert slot.getParentSetup() == panel.getCurrentSelectedSetup() : "Setup Mismatch";
		return getContainerFromID(slot.getParentSetup(), slot.getSlotID());
	}

	private List<InventorySetupsItem> getContainerFromID(final InventorySetup inventorySetup, InventorySetupsSlotID ID)
	{
		switch (ID)
		{
			case INVENTORY:
				return inventorySetup.getInventory();
			case EQUIPMENT:
				return inventorySetup.getEquipment();
			case RUNE_POUCH:
				return inventorySetup.getRune_pouch();
			case BOLT_POUCH:
				return inventorySetup.getBoltPouch();
			default:
				assert false : "Invalid ID given";
				return null;
		}
	}

	public boolean setupContainsItem(final InventorySetup setup, int itemID)
	{
		// So place holders will show up in the bank.
		itemID = itemManager.canonicalize(itemID);

		// Check if this item (inc. placeholder) is in the additional filtered items
		if (additionalFilteredItemsHasItem(itemID, setup.getAdditionalFilteredItems()))
		{
			return true;
		}

		// check the rune pouch to see if it has the item (runes in this case)
		if (setup.getRune_pouch() != null)
		{
			if (containerContainsItem(itemID, setup.getRune_pouch()))
			{
				return true;
			}
		}

		// check the bolt pouch to see if it has the item (bolts in this case)
		if (setup.getBoltPouch() != null)
		{
			if (containerContainsItem(itemID, setup.getBoltPouch()))
			{
				return true;
			}
		}

		return containerContainsItem(itemID, setup.getInventory()) ||
				containerContainsItem(itemID, setup.getEquipment());
	}

	private boolean containerContainsItem(int itemID, final List<InventorySetupsItem> setupContainer)
	{
		return containerContainsItem(itemID, setupContainer, true);
	}

	private boolean containerContainsItem(int itemID, final List<InventorySetupsItem> setupContainer, boolean allowFuzzy)
	{
		// So place holders will show up in the bank.
		itemID = itemManager.canonicalize(itemID);

		for (final InventorySetupsItem item : setupContainer)
		{
			// For equipped weight reducing items or noted items in the inventory
			int setupItemId = itemManager.canonicalize(item.getId());
			if (getProcessedID(item.isFuzzy() && allowFuzzy, itemID) == getProcessedID(item.isFuzzy() && allowFuzzy, setupItemId))
			{
				return true;
			}
		}

		return false;
	}

	private int getProcessedID(boolean isFuzzy, int itemId)
	{
		// use fuzzy mapping if needed
		if (isFuzzy)
		{
			return InventorySetupsVariationMapping.map(itemId);
		}

		return itemId;
	}

	private boolean checkAndUpdateSlotIfRunePouchWasSelected(final InventorySetup inventorySetup, final InventorySetupsSlotID ID,
															 final InventorySetupsItem oldItem, final InventorySetupsItem newItem)
	{
		return checkAndUpdateSlotIfRunePouchWasSelected(inventorySetup, ID, oldItem, newItem, true);
	}

	private boolean checkAndUpdateSlotIfRunePouchWasSelected(final InventorySetup inventorySetup, final InventorySetupsSlotID ID,
															 final InventorySetupsItem oldItem, final InventorySetupsItem newItem,
															 boolean showMessageDialogue)
	{

		final InventorySetupsRunePouchType runePouchTypeNewItem = getRunePouchType(newItem.getId());
		final InventorySetupsRunePouchType runePouchTypeOldItem = getRunePouchType(oldItem.getId());
		if (runePouchTypeNewItem != InventorySetupsRunePouchType.NONE)
		{

			if (ID != InventorySetupsSlotID.INVENTORY)
			{
				if (showMessageDialogue)
				{
					SwingUtilities.invokeLater(() ->
					{
						JOptionPane.showMessageDialog(panel,
								"You can't have a Rune Pouch there.",
								"Invalid Item",
								JOptionPane.ERROR_MESSAGE);
					});
				}

				return false;
			}

			// only display this message if we aren't replacing a rune pouch with a new rune pouch
			if (inventorySetup.getRune_pouch() != null && runePouchTypeOldItem == InventorySetupsRunePouchType.NONE)
			{
				if (showMessageDialogue)
				{
					SwingUtilities.invokeLater(() ->
					{
						JOptionPane.showMessageDialog(panel,
								"You can't have two Rune Pouches.",
								"Invalid Item",
								JOptionPane.ERROR_MESSAGE);
					});
				}
				return false;
			}

			inventorySetup.updateRunePouch(getRunePouchData(runePouchTypeNewItem));
		}
		else if (runePouchTypeOldItem != InventorySetupsRunePouchType.NONE)
		{
			// if the old item is a rune pouch, need to update it to null
			inventorySetup.updateRunePouch(null);
		}

		return true;
	}

	private boolean checkAndUpdateSlotIfBoltPouchWasSelected(final InventorySetup inventorySetup, final InventorySetupsSlotID ID,
															 final InventorySetupsItem oldItem, final InventorySetupsItem newItem)
	{
		return checkAndUpdateSlotIfBoltPouchWasSelected(inventorySetup, ID, oldItem, newItem, true);
	}

	private boolean checkAndUpdateSlotIfBoltPouchWasSelected(final InventorySetup inventorySetup, final InventorySetupsSlotID ID,
															 final InventorySetupsItem oldItem, final InventorySetupsItem newItem,
															 boolean showMessageDialogue)
	{

		if (isItemBoltPouch(newItem.getId()))
		{

			if (ID != InventorySetupsSlotID.INVENTORY)
			{

				if (showMessageDialogue)
				{
					SwingUtilities.invokeLater(() ->
					{
						JOptionPane.showMessageDialog(panel,
								"You can't have a Bolt Pouch there.",
								"Invalid Item",
								JOptionPane.ERROR_MESSAGE);
					});
				}

				return false;
			}

			// only display this message if we aren't replacing a bolt pouch with a new bolt pouch
			if (inventorySetup.getBoltPouch() != null && !isItemBoltPouch(oldItem.getId()))
			{
				if (showMessageDialogue)
				{
					SwingUtilities.invokeLater(() ->
					{
						JOptionPane.showMessageDialog(panel,
								"You can't have two Bolt Pouches.",
								"Invalid Item",
								JOptionPane.ERROR_MESSAGE);
					});
				}
				return false;
			}

			inventorySetup.updateBoltPouch(getBoltPouchData());
		}
		else if (isItemBoltPouch(oldItem.getId()))
		{
			// if the old item is a bolt pouch, need to update it to null
			inventorySetup.updateBoltPouch(null);
		}

		return true;
	}

	public InventorySetupsRunePouchType getRunePouchType(final int itemId)
	{
		if (InventorySetupsRunePouchPanel.RUNE_POUCH_IDS.contains(itemId))
		{
			return InventorySetupsRunePouchType.NORMAL;
		}

		if (InventorySetupsRunePouchPanel.RUNE_POUCH_DIVINE_IDS.contains(itemId))
		{
			return InventorySetupsRunePouchType.DIVINE;
		}

		return InventorySetupsRunePouchType.NONE;
	}

	public boolean isItemBoltPouch(final int itemId)
	{
		return itemId == ItemID.BOLT_POUCH;
	}

	public InventorySetupsRunePouchType getRunePouchTypeFromContainer(final List<InventorySetupsItem> container)
	{
		// Don't allow fuzzy when checking because it will incorrectly assume the type
		for (Integer id : InventorySetupsRunePouchPanel.RUNE_POUCH_IDS)
		{
			if (containerContainsItem(id, container, false))
			{
				return InventorySetupsRunePouchType.NORMAL;
			}
		}

		for (Integer id : InventorySetupsRunePouchPanel.RUNE_POUCH_DIVINE_IDS)
		{
			if (containerContainsItem(id, container, false))
			{
				return InventorySetupsRunePouchType.DIVINE;
			}
		}

		return InventorySetupsRunePouchType.NONE;
	}

	public boolean containerContainsBoltPouch(final List<InventorySetupsItem> container)
	{
		return containerContainsItem(ItemID.BOLT_POUCH, container, false);
	}

	public void openColorPicker(String title, Color startingColor, Consumer<Color> onColorChange)
	{

		RuneliteColorPicker colorPicker = getColorPickerManager().create(
				SwingUtilities.windowForComponent(panel),
				startingColor,
				title,
				false);

		colorPicker.setLocation(panel.getLocationOnScreen());
		colorPicker.setOnColorChange(onColorChange);

		colorPicker.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				dataManager.updateConfig(true, true);
			}
		});

		colorPicker.setVisible(true);
	}

	public void updateSetupName(final InventorySetup setup, final String newName)
	{
		final String originalName = setup.getName();
		for (final InventorySetupsSection section : sections)
		{
			if (cache.getSectionSetupsMap().get(section.getName()).containsKey(originalName))
			{
				final List<String> names = section.getSetups();
				int indexOf = names.indexOf(originalName);
				names.set(indexOf, newName);
			}
		}
		// Make sure not to set the new name of the setup before allowing the cache to update
		cache.updateSetupName(setup, newName);
		setup.setName(newName);
		// config will already be updated by caller so no need to update it here
	}

	public void updateSectionName(final InventorySetupsSection section, final String newName)
	{
		// Make sure not to set the new name of the section before allowing the cache to update
		cache.updateSectionName(section, newName);
		section.setName(newName);
		// config will already be updated by caller so no need to update it here
	}

}