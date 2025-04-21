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
package net.runelite.client.plugins.microbot.inventorysetups;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Menu;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.plugins.microbot.inventorysetups.serialization.InventorySetupPortable;
import net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsPluginPanel;
import net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsSlot;
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

import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsRunePouchPanel.RUNE_POUCH_AMOUNT_VARBITS;
import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsRunePouchPanel.RUNE_POUCH_RUNE_VARBITS;


@PluginDescriptor(
		name = PluginDescriptor.Mocrosoft + "MInventory Setups",
		description = "Save gear setups for specific activities",
		enabledByDefault = true,
		alwaysOn = true
)
@PluginDependency(BankTagsPlugin.class)
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
	public static final String CONFIG_KEY_USE_LAYOUTS = "useLayouts";
	public static final String CONFIG_KEY_LAYOUT_DEFAULT = "defaultLayout";
	public static final String CONFIG_KEY_LAYOUT_DUPLICATES = "addDuplicatesInLayouts";
	public static final String CONFIG_KEY_ENABLE_LAYOUT_WARNING = "enableLayoutWarning";
	public static final String CONFIG_GROUP_HUB_BTL = "banktaglayouts";
	// Bank tags will standardize tag names so this must not be modified by that standardization.
	// DO NOT CHANGE THIS. CHANGING THIS WOULD REQUIRE MIGRATION OF USER DATA.
	// IF CHANGING, HUB BTL MUST BE UPDATED AS WELL.
	public static final String LAYOUT_PREFIX_MARKER = "_invsetup_";
	public static final String TUTORIAL_LINK = "https://github.com/dillydill123/inventory-setups#inventory-setups";
	public static final String SUGGESTION_LINK = "https://github.com/dillydill123/inventory-setups/issues";
	public static final int NUM_INVENTORY_ITEMS = 28;
	public static final int NUM_EQUIPMENT_ITEMS = 14;
	public static final int MAX_SETUP_NAME_LENGTH = 50;
	private static final String OPEN_SECTION_MENU_ENTRY = "Open Section";
	private static final String OPEN_SETUP_MENU_ENTRY = "Open setup";
	private static final String RETURN_TO_OVERVIEW_ENTRY = "Close current setup";
	private static final String AUTO_LAYOUT_PRESET_ENTRY = "Auto layout: Preset";
	private static final String AUTO_LAYOUT_ZIGZAG_ENTRY = "Auto layout: ZigZag";
	private static final String ADD_TO_ADDITIONAL_ENTRY = "Add to Additional Filtered Items";
	private static final String UNASSIGNED_SECTION_SETUP_MENU_ENTRY = "Unassigned";
	private static final String ITEM_SEARCH_TAG = "item:";
	private static final String NOTES_SEARCH_TAG = "notes:";
	private static final int SPELLBOOK_VARBIT = 4070;
	private static final int BANK_TAG_OPTIONS = BankTagsService.OPTION_ALLOW_MODIFICATIONS | BankTagsService.OPTION_HIDE_TAG_NAME;

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
	private PluginManager pluginManager;

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
	private BankTagsService bankTagsService;

	@Inject
	private BankTagsPlugin bankTagsPlugin;

	@Inject
	private BankSearch bankSearch;

	@Inject
	@Getter
	private LayoutManager layoutManager;

	@Inject
	@Getter
	private TagManager tagManager;

	@Inject
	private KeyManager keyManager;

	@Getter
	private InventorySetupLayoutUtilities layoutUtilities;

	@Getter
	private Boolean canUseLayouts;

	@Inject
	@Getter
	private ChatboxItemSearch itemSearch;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	private ChatboxTextInput searchInput;

	@Setter
	@Getter
	private boolean navButtonIsSelected;

	// current version of the plugin
	private String currentVersion;

	@Getter
	private InventorySetupsPersistentDataManager dataManager;

	@Getter
	private InventorySetupsAmmoHandler ammoHandler;

	// Used to defer highlighting to GameTick
	private boolean shouldTriggerInventoryHighlightOnGameTick;

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

	@Override
	public void startUp()
	{
		// get current version of the plugin using properties file generated by build.gradle
		try
		{
//			final Properties props = new Properties();
//			InputStream is = MInventorySetupsPlugin.class.getResourceAsStream("/invsetups_version.txt");
//			props.load(is);
			this.currentVersion = "1.20.2";
		}
		catch (Exception e)
		{
			log.warn("Could not determine current plugin version", e);
			this.currentVersion = "";
		}

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

		this.shouldTriggerInventoryHighlightOnGameTick = false;
		this.cache = new InventorySetupsCache();
		inventorySetups = new ArrayList<>();
		this.sections = new ArrayList<>();
		this.dataManager = new InventorySetupsPersistentDataManager(this, configManager, cache, gson, inventorySetups, sections);
		this.ammoHandler = new InventorySetupsAmmoHandler(this, client, itemManager, panel, config);
		this.layoutUtilities = new InventorySetupLayoutUtilities(itemManager, tagManager, layoutManager, config, client);
		this.canUseLayouts = canUseLayouts();

		// load all the inventory setups from the config file
		clientThread.invokeLater(() ->
		{
			switch (client.getGameState())
			{
				case STARTING:
				case UNKNOWN:
				case LOADING:
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

	@Override
	public void shutDown()
	{
		resetBankSearch();
		clientToolbar.removeNavigation(navButton);
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

	private boolean canUseLayouts()
	{
		// If Bank Tags is off, layouts will not work.
		return pluginManager.isPluginEnabled(bankTagsPlugin);
	}

	public void enableLayouts()
	{
		// Turn on Bank Tags and configure hub plugin bank tag layouts setting to be off.
		if (!pluginManager.isPluginEnabled(bankTagsPlugin))
		{
			log.info("Turning on Bank Tags plugin");
			pluginManager.setPluginEnabled(bankTagsPlugin, true);
			try
			{
				pluginManager.startPlugin(bankTagsPlugin);
			}
			catch (Exception e)
			{
				log.error("Failed to start Bank Tags plugin.");
				log.error(e.toString());
			}
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged pluginChanged)
	{
		if (pluginChanged.getPlugin() == bankTagsPlugin)
		{
			this.canUseLayouts = canUseLayouts();
		}
	}

	private void registerHotkeys()
	{
		keyManager.registerKeyListener(returnToSetupsHotkeyListener);
		keyManager.registerKeyListener(filterBankHotkeyListener);
		keyManager.registerKeyListener(sectionModeHotkeyListener);
	}

	private void unregisterHotkeys()
	{
		keyManager.unregisterKeyListener(returnToSetupsHotkeyListener);
		keyManager.unregisterKeyListener(filterBankHotkeyListener);
		keyManager.unregisterKeyListener(sectionModeHotkeyListener);
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
				clientThread.invokeLater(() ->
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
				});
			}
			else if (event.getKey().equals(CONFIG_KEY_USE_LAYOUTS))
			{
				doBankSearch();
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (shouldTriggerInventoryHighlightOnGameTick)
		{
			shouldTriggerInventoryHighlightOnGameTick = false;
			clientThread.invokeLater(panel::doHighlighting);
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
			MenuEntry[] clientEntries = client.getMenu().getMenuEntries();

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
				// If the item is a graceful or weight reducing equipment, we must canonicalize.
				// For items on the ground, it will be the inventory version of the item, so the inverse of worn items must be used.
				boolean canonicalize = InventorySetupsVariationMapping.INVERTED_WORN_ITEMS.containsKey(itemID);
				boolean setupContainsItem = setupContainsItem(panel.getCurrentSelectedSetup(), itemID, true, canonicalize);
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
				boolean putNonSetupEntriesOnTop = config.groundItemMenuSwapPriority() == InventorySetupsGroundItemMenuSwapPriority.OUT;
				int entriesInSetupStartIndex = firstTakeIndex + takeEntriesNotInSetup.size();
				int entriesNotInSetupStartIndex = firstTakeIndex;
				if (putNonSetupEntriesOnTop)
				{
					entriesInSetupStartIndex = firstTakeIndex;
					entriesNotInSetupStartIndex = firstTakeIndex + takeEntriesInSetup.size();
				}

				for (int i = 0; i < takeEntriesInSetup.size(); i++)
				{
					clientEntries[entriesInSetupStartIndex + i] = takeEntriesInSetup.get(i);
				}

				for (int i = 0; i < takeEntriesNotInSetup.size(); i++)
				{
					clientEntries[entriesNotInSetupStartIndex + i] = takeEntriesNotInSetup.get(i);
				}
			}

			client.getMenu().setMenuEntries(clientEntries);

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
				&& bankTagsService.getActiveLayout() == null // If there is an active layout, then the real item behind the fake layout item may be added. So just disallow this menu.
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
				createSectionSubMenuOnWornItems(sectionsToDisplay.get(section.getName()), section.getName(), sectionMenuTargetColor);

			});

			if (!unassignedSetups.isEmpty())
			{
				createSectionSubMenuOnWornItems(unassignedSetups, UNASSIGNED_SECTION_SETUP_MENU_ENTRY, JagexColors.MENU_TARGET);
			}

		}
		else
		{
			for (int i = 0; i < setupsToShowOnWornItemsList.size(); i++)
			{
				final InventorySetup setupToShow = setupsToShowOnWornItemsList.get(setupsToShowOnWornItemsList.size() - 1 - i);
				Color menuTargetColor = setupToShow.getDisplayColor() == null ? JagexColors.MENU_TARGET : setupToShow.getDisplayColor();
				client.getMenu()
						.createMenuEntry(-1)
						.setOption(OPEN_SETUP_MENU_ENTRY)
						.setTarget(ColorUtil.prependColorTag(setupToShow.getName(), menuTargetColor))
						.setType(MenuAction.RUNELITE)
						.onClick(e -> panel.setCurrentInventorySetup(setupToShow, true));
			}
		}

		if (panel.getCurrentSelectedSetup() != null)
		{
			if (panel.getCurrentSelectedSetup().isFilterBank())
			{
				if (this.canUseLayouts && config.useLayouts())
				{
					// Add Auto layouts
					createAutoLayoutSubMenuOnWornItems();
				}

				// add menu entry to re-filter/layout setup
				client.getMenu()
						.createMenuEntry(-1)
						.setOption("Filter Bank")
						.setType(MenuAction.RUNELITE)
						.onClick(e -> doBankSearch());
			}

			// add menu entry to close setup
			client.getMenu()
					.createMenuEntry(-1)
					.setOption(RETURN_TO_OVERVIEW_ENTRY)
					.setType(MenuAction.RUNELITE)
					.onClick(e -> panel.returnToOverviewPanel(false));
		}
	}

	private void createAutoLayoutSubMenuOnWornItems()
	{
		Menu parentMenu = client.getMenu()
				.createMenuEntry(-1)
				.setOption("Auto Layout")
				.setType(MenuAction.RUNELITE)
				.createSubMenu();
		parentMenu.createMenuEntry(0)
				.setOption(AUTO_LAYOUT_PRESET_ENTRY)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> previewNewLayout(panel.getCurrentSelectedSetup(), InventorySetupLayoutType.PRESET));
		parentMenu.createMenuEntry(0)
				.setOption(AUTO_LAYOUT_ZIGZAG_ENTRY)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> previewNewLayout(panel.getCurrentSelectedSetup(), InventorySetupLayoutType.ZIGZAG));

	}

	private void previewNewLayout(final InventorySetup setup, final InventorySetupLayoutType type)
	{
		clientThread.invoke(() ->
		{
			final Layout old = layoutUtilities.getSetupLayout(setup);

			// Don't add any items to the tag yet. We just want to display a layout
			// We can add tags after if the user likes the layout.
			// This stops the case that somebody removed a tag from the inventory setup
			// And this layout won't accidentally bring it back if they decide not to use it.
			final Layout new_ = layoutUtilities.createSetupLayout(setup, type, false);

			// Temporarily save the new layout to open the tag.
			layoutManager.saveLayout(new_);
			bankTagsService.openBankTag(new_.getTag(), BankTagsService.OPTION_HIDE_TAG_NAME);
			resetBankScrollBar();

			// Save the old layout again in case the user hits escape on the menu.
			// The bank will still show the temporary new layout.
			layoutManager.saveLayout(old);

			chatboxPanelManager.openTextMenuInput("Tab laid out using the '" + type.getName() + "' layout.")
					.option("1. Keep", () ->
					{
						// Tag all the items in the setup now since the user likes it.
						clientThread.invoke(() ->
						{
							// Need this to be in a client thread invoke in case the user types 1 instead.
							layoutUtilities.createSetupLayout(setup, type, true);
							layoutManager.saveLayout(new_);
						});

					})
					.option("2. Undo", () ->
					{
						// The old layout is already saved, no need to do anything.
					})
					// If this is not done with invokeLater, I get crashing...
					.onClose(() -> clientThread.invokeLater(() -> bankTagsService.openBankTag(old.getTag(), BANK_TAG_OPTIONS)))
					.build();
		});
	}

	private void createSectionSubMenuOnWornItems(Collection<InventorySetup> setups, String name, Color color)
	{
		Menu subMenu = client.getMenu()
			.createMenuEntry(1)
			.setOption(OPEN_SECTION_MENU_ENTRY)
			.setTarget(ColorUtil.prependColorTag(name, color))
			.setType(MenuAction.RUNELITE)
			.createSubMenu();

		for (final InventorySetup inventorySetup : setups)
		{
			try
			{
				createSectionSubMenuEntryOnWornItems(inventorySetup, subMenu);
			}
			catch (IllegalStateException ignored)
			{
				// This can be thrown if the menu exceeds the menu entry limit,
				// at which point we can just move on to the next section.
				// Each submenu has its own internal limit, so hitting this once
				// does not mean we cannot insert more menu entries elsewhere.
				break;
			}
		}
	}

	private void createSectionSubMenuEntryOnWornItems(InventorySetup setup, Menu parentMenu)
	{
		Color setupMenuTargetColor = setup.getDisplayColor() == null ? JagexColors.MENU_TARGET : setup.getDisplayColor();

		parentMenu.createMenuEntry(0)
				.setOption(OPEN_SETUP_MENU_ENTRY)
				.setTarget(ColorUtil.prependColorTag(setup.getName(), setupMenuTargetColor))
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					resetBankSearch();
					panel.setCurrentInventorySetup(setup, true);
				});
	}

	private void createMenuEntryToAddAdditionalFilteredItem(int inventoryIndex)
	{
		client.getMenu()
			.createMenuEntry(-1)
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
					addAdditionalFilteredItem(newItem.getId(), panel.getCurrentSelectedSetup(), additionalFilteredItems);
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
		if (event.getGroupId() == InterfaceID.BANK )
		{
			if (!config.persistHotKeysOutsideBank())
			{
				unregisterHotkeys();
			}

			if (isInventorySetupTagOpen())
			{
				// Close the bank tag for those who use manual bank filter
				clientThread.invokeLater(() -> bankTagsService.closeBankTag());
			}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANK)
		{
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

			List<InventorySetupsItem> runePouchData = ammoHandler.getRunePouchDataIfInContainer(inv);
			List<InventorySetupsItem> boltPouchData = ammoHandler.getBoltPouchDataIfInContainer(inv);
			List<InventorySetupsItem> quiverData = ammoHandler.getQuiverDataIfInSetup(inv, eqp);

			int spellbook = getCurrentSpellbook();

			final InventorySetup invSetup = new InventorySetup(inv, eqp, runePouchData, boltPouchData, quiverData,
				new HashMap<>(),
				newName,
				"",
				config.highlightColor(),
				config.highlightDifference(),
				config.enableDisplayColor() ? config.displayColor() : null,
				config.bankFilter(),
				config.highlightUnorderedDifference(),
				spellbook, false, -1);

			cache.addSetup(invSetup);
			inventorySetups.add(invSetup);
			dataManager.updateConfig(true, false);

			Layout setupLayout = layoutUtilities.createSetupLayout(invSetup);
			layoutManager.saveLayout(setupLayout);
			tagManager.setHidden(setupLayout.getTag(), true);

			SwingUtilities.invokeLater(() -> panel.redrawOverviewPanel(false));

		});
	}

	public void addInventorySetup(String name) {
		// Use the provided name instead of prompting via a dialog box
		if (null == name || name.isEmpty()) {
			return;
		}

		if (MAX_SETUP_NAME_LENGTH < name.length()) {
			name = name.substring(0, MAX_SETUP_NAME_LENGTH);
		}

		if (cache.getInventorySetupNames().containsKey(name)) {
			String finalName = name;
			InventorySetup inventorySetup = MInventorySetupsPlugin.getInventorySetups().stream().filter(Objects::nonNull).filter(x -> x.getName().equalsIgnoreCase(finalName)).findFirst().orElse(null);
			updateCurrentSetup(inventorySetup,false);
			return;
		}

		final String newName = name;

		clientThread.invokeLater(() ->
		{
			List<InventorySetupsItem> inv = getNormalizedContainer(InventoryID.INVENTORY);
			List<InventorySetupsItem> eqp = getNormalizedContainer(InventoryID.EQUIPMENT);

			List<InventorySetupsItem> runePouchData = ammoHandler.getRunePouchDataIfInContainer(inv);
			List<InventorySetupsItem> boltPouchData = ammoHandler.getBoltPouchDataIfInContainer(inv);
			List<InventorySetupsItem> quiverData = ammoHandler.getQuiverDataIfInSetup(inv, eqp);

			int spellbook = getCurrentSpellbook();

			final InventorySetup invSetup = new InventorySetup(inv, eqp, runePouchData, boltPouchData, quiverData,
					new HashMap<>(),
					newName,
					"",
					config.highlightColor(),
					config.highlightDifference(),
					config.enableDisplayColor() ? config.displayColor() : null,
					config.bankFilter(),
					config.highlightUnorderedDifference(),
					spellbook, false, -1);

			cache.addSetup(invSetup);
			inventorySetups.add(invSetup);
			dataManager.updateConfig(true, false);

			Layout setupLayout = layoutUtilities.createSetupLayout(invSetup);
			layoutManager.saveLayout(setupLayout);
			tagManager.setHidden(setupLayout.getTag(), true);

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
		return inventorySetups.stream()
			.filter(inventorySetup -> shouldDisplaySetup(inventorySetup, textToFilter.trim().toLowerCase()))
			.collect(Collectors.toList());
	}

	private static boolean shouldDisplaySetup(InventorySetup inventorySetup, String trimmedTextToFilterLower)
	{
		if (trimmedTextToFilterLower.startsWith(ITEM_SEARCH_TAG) && trimmedTextToFilterLower.length() > ITEM_SEARCH_TAG.length())
		{
			String itemName = trimmedTextToFilterLower.substring(ITEM_SEARCH_TAG.length()).trim();
			// Find setups containing the given item name
			return containerContainsItemByName(inventorySetup.getInventory(), itemName) || containerContainsItemByName(inventorySetup.getEquipment(), itemName)
				|| containerContainsItemByName(inventorySetup.getRune_pouch(), itemName) || containerContainsItemByName(inventorySetup.getAdditionalFilteredItems().values(), itemName)
				|| containerContainsItemByName(inventorySetup.getBoltPouch(), itemName);
		}
		else if (trimmedTextToFilterLower.startsWith(NOTES_SEARCH_TAG) && trimmedTextToFilterLower.length() > NOTES_SEARCH_TAG.length())
		{
			String noteText = trimmedTextToFilterLower.substring(NOTES_SEARCH_TAG.length()).trim();
			return inventorySetup.getNotes().toLowerCase().contains(noteText);
		}
		// Find setups containing the given setup name (default behaviour)
		return inventorySetup.getName().toLowerCase().contains(trimmedTextToFilterLower);
	}

	private static boolean containerContainsItemByName(Collection<InventorySetupsItem> itemsInContainer, String textToFilterLower)
	{
		if (itemsInContainer == null)
		{
			return false;
		}
		return itemsInContainer.stream()
			.map(item -> item.getName().toLowerCase())
			.anyMatch(itemName -> itemName.contains(textToFilterLower));
	}

	public void doBankSearch()
	{
		clientThread.invoke(() ->
		{
			final InventorySetup currentSelectedSetup = panel.getCurrentSelectedSetup();

			if (currentSelectedSetup == null || !currentSelectedSetup.isFilterBank() || !isFilteringAllowed())
			{
				return;
			}

			if (client.getWidget(ComponentID.BANK_CONTAINER) == null)
			{
				return;
			}

			final String tagName = InventorySetupLayoutUtilities.getTagNameForLayout(currentSelectedSetup.getName());
			if (!config.useLayouts())
			{
				bankTagsService.openBankTag(tagName, BANK_TAG_OPTIONS | BankTagsService.OPTION_NO_LAYOUT);
			}
			else
			{
				bankTagsService.openBankTag(tagName, BANK_TAG_OPTIONS);
				resetBankScrollBar();
			}

		});
	}

	public void resetBankScrollBar()
	{
		// Reset the scroll bar position to 0
		Widget widget = client.getWidget(ComponentID.BANK_SCROLLBAR);
		if (widget != null)
		{
			widget.setScrollY(0);
			client.setVarcIntValue(VarClientInt.BANK_SCROLL, 0);
		}
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


	private boolean additionalFilteredItemsHasItem(int itemId, final Map<Integer, InventorySetupsItem> additionalFilteredItems)
	{
		return additionalFilteredItemsHasItem(itemId, additionalFilteredItems, true, true);
	}

	private boolean additionalFilteredItemsHasItem(int itemId, final Map<Integer, InventorySetupsItem> additionalFilteredItems, boolean allowFuzzy, boolean canonicalize)
	{
		for (final Integer additionalItemKey : additionalFilteredItems.keySet())
		{
			boolean isFuzzy = additionalFilteredItems.get(additionalItemKey).isFuzzy();
			int addItemId = additionalFilteredItems.get(additionalItemKey).getId();

			addItemId = getProcessedID(isFuzzy, allowFuzzy, canonicalize, addItemId);
			int finalItemId = getProcessedID(isFuzzy, allowFuzzy, canonicalize, itemId);
			if (addItemId == finalItemId)
			{
				return true;
			}
		}
		return false;
	}

	private void addAdditionalFilteredItem(int itemId, final InventorySetup setup, final Map<Integer, InventorySetupsItem> additionalFilteredItems)
	{
		// un-noted, un-placeholdered ID
		final int processedItemId = itemManager.canonicalize(itemId);

		clientThread.invokeLater(() ->
		{
			final String name = itemManager.getItemComposition(processedItemId).getName();
			InventorySetupsStackCompareID stackCompareType = panel.isStackCompareForSlotAllowed(InventorySetupsSlotID.ADDITIONAL_ITEMS, 0) ? config.stackCompareType() : InventorySetupsStackCompareID.None;
			final InventorySetupsItem setupItem = new InventorySetupsItem(processedItemId, name, 1, config.fuzzy(), stackCompareType);

			additionalFilteredItems.put(processedItemId, setupItem);
			layoutUtilities.recalculateLayout(setup);
			dataManager.updateConfig(true, false);
			panel.refreshCurrentSetup();
		});
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// Spellbook changed
		if (event.getVarpId() == 439 && client.getGameState() == GameState.LOGGED_IN)
		{
			// must be invoked later otherwise causes freezing.
			clientThread.invokeLater(panel::doHighlighting);
			return;
		}

		Widget bankContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
		boolean bankIsOpen = bankContainer != null && !bankContainer.isHidden();
		// Avoid extra highlighting calls by deferring the highlighting to GameTick after a bunch of varbit changes come
		// If the bank is closed, then onItemContainerChanged will handle the highlighting
		if (bankIsOpen &&
			(RUNE_POUCH_RUNE_VARBITS.contains(event.getVarbitId()) || RUNE_POUCH_AMOUNT_VARBITS.contains(event.getVarbitId())))
		{
			shouldTriggerInventoryHighlightOnGameTick = true;
			return;
		}
	}

	public void resetBankSearch()
	{
		// We only reset the bank search if the active tag is an inventory setup
		// This stops it from closing an open bank tag tab or other plugins opening bank tags.
		if (isInventorySetupTagOpen())
		{
			clientThread.invoke(() -> bankTagsService.closeBankTag());
		}
	}

	public boolean isInventorySetupTagOpen()
	{
		return bankTagsService.getActiveTag() != null && bankTagsService.getActiveTag().startsWith(LAYOUT_PREFIX_MARKER);
	}

	@Subscribe(priority = -1) // Make sure this runs AFTER bank tags plugin.
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_INIT)
		{
			// Do a bank search when the bank is opened
			// This will set the proper layout
			if (!config.manualBankFilter())
			{
				// Only do the filter if manual bank filter is not set.
				doBankSearch();
			}
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
		{
			// Bankmain_build will reset the bank title to "The Bank of Gielinor". So apply our own title.
			// We should only do this if the active tag is an inventory setup tag
			if (panel.getCurrentSelectedSetup() != null && isInventorySetupTagOpen())
			{
				Widget bankTitle = client.getWidget(ComponentID.BANK_TITLE_BAR);
				bankTitle.setText("Inventory Setup <col=ff0000>" + panel.getCurrentSelectedSetup().getName() + "</col>");
			}
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_SEARCH_TOGGLE)
		{
			// cancel the current filtering if the search button is clicked
			resetBankSearch();
		}
	}

	public void updateCurrentSetup(InventorySetup setup)
	{
		updateCurrentSetup(setup, true);
	}

	public void updateCurrentSetup(InventorySetup setup, boolean showConfirmDialog)
	{
		if (showConfirmDialog)
		{
			int confirm = JOptionPane.showConfirmDialog(panel,
					"Are you sure you want update this inventory setup?",
					"Warning", JOptionPane.OK_CANCEL_OPTION);

			// cancel button was clicked
			if (JOptionPane.YES_OPTION != confirm)
			{
				return;
			}
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

			ammoHandler.updateSpecialContainersInSetup(setup, inv, eqp);

			setup.updateInventory(inv);
			setup.updateEquipment(eqp);
			setup.updateSpellbook(getCurrentSpellbook());

			// Regenerate the layout and tag.
			clientThread.invoke(() ->
			{
				String tagName = InventorySetupLayoutUtilities.getTagNameForLayout(setup.getName());
				tagManager.removeTag(tagName);
				layoutManager.removeLayout(tagName);

				Layout newLayout = layoutUtilities.createSetupLayout(setup);
				layoutManager.saveLayout(newLayout);
			});

			dataManager.updateConfig(true, false);
			panel.refreshCurrentSetup();
		});
	}

	private boolean updateAllInstancesInContainerSetupWithNewItem(final InventorySetup inventorySetup, List<InventorySetupsItem> containerToUpdate,
																final InventorySetupsItem oldItem, final InventorySetupsItem newItem, final InventorySetupsSlotID id)
	{
		boolean updated = false;
		for (int i = 0; i < containerToUpdate.size(); i++)
		{
			final InventorySetupsItem item = containerToUpdate.get(i);
			if (item.getId() == oldItem.getId())
			{
				ammoHandler.handleSpecialAmmo(inventorySetup, containerToUpdate.get(i), newItem);
				containerToUpdate.set(i, newItem);
				updated = true;
			}
		}
		return updated;
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
			boolean invUpdated = updateAllInstancesInContainerSetupWithNewItem(inventorySetup, inventorySetup.getInventory(), oldItem, newItem, InventorySetupsSlotID.INVENTORY);
			boolean eqpUpdated = updateAllInstancesInContainerSetupWithNewItem(inventorySetup, inventorySetup.getEquipment(), oldItem, newItem, InventorySetupsSlotID.EQUIPMENT);
			if (invUpdated || eqpUpdated)
			{
				layoutUtilities.recalculateLayout(inventorySetup);
			}
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
				ammoHandler.handleSpecialAmmo(slot.getParentSetup(), oldItem, newItem);
				containerToUpdate.set(slot.getIndexInSlot(), newItem);
				layoutUtilities.recalculateLayout(slot.getParentSetup());
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
							addAdditionalFilteredItem(finalId, slot.getParentSetup(), additionalFilteredItems);
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
				ammoHandler.handleSpecialAmmo(slot.getParentSetup(), itemToBeReplaced, newItem);
				container.set(slot.getIndexInSlot(), newItem);
				layoutUtilities.recalculateLayout(slot.getParentSetup());
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
				!searchInput.getValue().isEmpty() &&
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
				layoutUtilities.recalculateLayout(panel.getCurrentSelectedSetup());
				dataManager.updateConfig(true, false);
				panel.refreshCurrentSetup();
				return;
			}

			final List<InventorySetupsItem> container = getContainerFromSlot(slot);

			// update special containers
			final InventorySetupsItem itemToBeReplaced = container.get(slot.getIndexInSlot());
			final InventorySetupsItem dummyItem = new InventorySetupsItem(-1, "", 0, itemToBeReplaced.isFuzzy(), itemToBeReplaced.getStackCompare());
			ammoHandler.handleSpecialAmmo(slot.getParentSetup(), itemToBeReplaced, dummyItem);

			container.set(slot.getIndexInSlot(), dummyItem);

			// Update the layout
			if (itemToBeReplaced.getId() != -1)
			{
				layoutUtilities.recalculateLayout(slot.getParentSetup());
			}

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

		InventorySetupsItem item = null;

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
			item = additionalFilteredItems.get(keyToMakeFuzzy);
		}
		else
		{
			final List<InventorySetupsItem> container = getContainerFromSlot(slot);
			item = container.get(slot.getIndexInSlot());
		}
		item.toggleIsFuzzy();
		final int itemId = item.getId();
		clientThread.invoke(() ->
		{
			if (itemId == -1)
			{
				return;
			}
			layoutUtilities.recalculateLayout(slot.getParentSetup());
		});

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
		// None of the data functions are called here because the callee does it.
		// If an item is swapped (removed + added) this would result in a double data process
		// Which isn't bad, just a minor optimization

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

			// Remove the layout and tag
			clientThread.invoke(() ->
			{
				String tagName = InventorySetupLayoutUtilities.getTagNameForLayout(setup.getName());
				tagManager.removeTag(tagName);
				layoutManager.removeLayout(tagName);
			});

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
			// We may need to display the warning for this profile so reset it.
			panel.setHasDisplayedLayoutWarning(false);
			SwingUtilities.invokeLater(() -> panel.redrawOverviewPanel(true));
			return true;
		});
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{

		// check to see that the container is the equipment or inventory
		ItemContainer container = event.getItemContainer();

		if (container == client.getItemContainer(InventoryID.INVENTORY) || container == client.getItemContainer(InventoryID.EQUIPMENT))
		{
			panel.doHighlighting();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		panel.doHighlighting();
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
			default:
				return ammoHandler.getNormalizedSpecialContainer(id);
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
		InventorySetupPortable portableSetup = InventorySetupPortable.convertFromInventorySetup(setup, layoutUtilities);
		final String json = gson.toJson(portableSetup);
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

			Type type = new TypeToken<InventorySetupPortable>()
			{

			}.getType();

			final InventorySetupPortable newSetupPortable = gson.fromJson(setup, type);

			if (isSetupPortableInvalid(newSetupPortable))
			{
				throw new RuntimeException("Setup has invalid data.");
			}

			final InventorySetup newSetup = newSetupPortable.getSerializedSetup();

			if (isImportedSetupInvalid(newSetup))
			{
				throw new RuntimeException("Imported setup was missing required fields");
			}

			clientThread.invoke(() ->
			{
				preProcessNewSetup(newSetup);
				cache.addSetup(newSetup);
				inventorySetups.add(newSetup);
				// This will tag all the items in the setup for us, but don't use the layout.
				// Use what's stored in the import.
				Layout temp_layout_ = layoutUtilities.createSetupLayout(newSetup);
				Layout newLayout = new Layout(temp_layout_.getTag(), newSetupPortable.getLayout());
				layoutManager.saveLayout(newLayout);
				tagManager.setHidden(newLayout.getTag(), true);

				dataManager.updateConfig(true, false);
				SwingUtilities.invokeLater(() -> panel.redrawOverviewPanel(false));
			});
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

			Type typeSetups = new TypeToken<ArrayList<InventorySetupPortable>>()
			{

			}.getType();

			final ArrayList<InventorySetupPortable> newSetupPortables = gson.fromJson(json, typeSetups);

			final ArrayList<InventorySetup> newUnprocessedSetups = new ArrayList<>();
			final ArrayList<int[]> newUnprocessedLayouts = new ArrayList<>();
			for (final InventorySetupPortable portable : newSetupPortables)
			{
				if (isSetupPortableInvalid(portable))
				{
					throw new RuntimeException("Setup has invalid data.");
				}

				final InventorySetup setup = portable.getSerializedSetup();
				// Do some additional checking for required fields
				if (isImportedSetupInvalid(setup))
				{
					throw new RuntimeException("Mass import section file was missing required fields");
				}
				newUnprocessedSetups.add(setup);
				newUnprocessedLayouts.add(portable.getLayout());
			}

			clientThread.invoke(() ->
			{
				for (int i = 0; i < newUnprocessedSetups.size(); i++)
				{
					final InventorySetup inventorySetup = newUnprocessedSetups.get(i);
					preProcessNewSetup(inventorySetup);
					cache.addSetup(inventorySetup);
					inventorySetups.add(inventorySetup);

					// This will tag all the items in the setup for us, but don't use the layout.
					// Use what's stored in the import.
					Layout temp_layout_ = layoutUtilities.createSetupLayout(inventorySetup);
					Layout newLayout = new Layout(temp_layout_.getTag(), newUnprocessedLayouts.get(i));
					layoutManager.saveLayout(newLayout);
					tagManager.setHidden(newLayout.getTag(), true);
				}

				dataManager.updateConfig(true, false);
				SwingUtilities.invokeLater(() -> panel.redrawOverviewPanel(false));
			});

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

	private boolean isSetupPortableInvalid(final InventorySetupPortable portable)
	{
		return portable.getSetup() == null || portable.getLayout() == null;
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

	public boolean isHighlightingAllowed()
	{
		return client.getGameState() == GameState.LOGGED_IN;
	}

	public boolean isFilteringAllowed()
	{
		return navButtonIsSelected || !config.requireActivePanelFilter();
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
			default:
				return ammoHandler.getSpecialContainerFromID(inventorySetup, ID);
		}
	}

	public boolean setupContainsItem(final InventorySetup setup, int itemID, boolean allowFuzzy, boolean canonicalize)
	{
		if (additionalFilteredItemsHasItem(itemID, setup.getAdditionalFilteredItems(), allowFuzzy, canonicalize))
		{
			return true;
		}
		if (ammoHandler.specialContainersContainItem(setup, itemID, allowFuzzy, canonicalize))
		{
			return true;
		}
		if (containerContainsItem(itemID, setup.getInventory(), allowFuzzy, canonicalize))
		{
			return true;
		}
		if (containerContainsItem(itemID, setup.getEquipment(), allowFuzzy, canonicalize))
		{
			return true;
		}
		return false;
	}

	public boolean containerContainsItem(int itemID, final List<InventorySetupsItem> setupContainer, boolean allowFuzzy, boolean canonicalize)
	{
		if (setupContainer == null)
		{
			return false;
		}

		for (final InventorySetupsItem item : setupContainer)
		{

			int processedSetupItemId = getProcessedID(item.isFuzzy(), allowFuzzy, canonicalize, item.getId());
			int processedItemId = getProcessedID(item.isFuzzy(), allowFuzzy, canonicalize, itemID);

			if (processedSetupItemId == processedItemId)
			{
				return true;
			}
		}

		return false;
	}

	public boolean containerContainsItemFromSet(final Set<Integer> itemIDs, final List<InventorySetupsItem> setupContainer, boolean allowFuzzy, boolean canonicalize)
	{
		if (setupContainer == null)
		{
			return false;
		}

		for (final InventorySetupsItem item : setupContainer)
		{

			int processedSetupItemId = getProcessedID(item.isFuzzy(), allowFuzzy, canonicalize, item.getId());

			if (itemIDs.contains(processedSetupItemId))
			{
				return true;
			}
		}

		return false;
	}

	private int getProcessedID(boolean itemIsFuzzy, boolean allowFuzzy, boolean canonicalize, int itemId)
	{
		// For equipped weight reducing items or noted items in the inventory
		if (canonicalize || itemIsFuzzy)
		{
			itemId = itemManager.canonicalize(itemId);
		}

		// use fuzzy mapping if needed
		if (itemIsFuzzy && allowFuzzy)
		{
			return InventorySetupsVariationMapping.map(itemId);
		}

		return itemId;
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
		final String oldTag = InventorySetupLayoutUtilities.getTagNameForLayout(setup.getName());
		cache.updateSetupName(setup, newName);
		setup.setName(newName);

		// Update the tag and layout info
		clientThread.invoke(() ->
		{
			// Rename the tag
			String newTag = InventorySetupLayoutUtilities.getTagNameForLayout(setup.getName());
			tagManager.renameTag(oldTag, newTag);

			// Construct the new Layout using the new tag but the old layout info.
			Layout oldLayout = layoutManager.loadLayout(oldTag);
			assert oldLayout != null : "Setup did not have a layout!";
			Layout newLayout = new Layout(newTag, oldLayout.getLayout());

			layoutManager.removeLayout(oldTag);
			layoutManager.saveLayout(newLayout);
			tagManager.setHidden(newLayout.getTag(), true);
		});

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
