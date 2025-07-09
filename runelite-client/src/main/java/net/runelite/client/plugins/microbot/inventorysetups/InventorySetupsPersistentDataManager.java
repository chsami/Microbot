package net.runelite.client.plugins.microbot.inventorysetups;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.runelite.client.plugins.microbot.inventorysetups.serialization.InventorySetupItemSerializable;
import net.runelite.client.plugins.microbot.inventorysetups.serialization.InventorySetupItemSerializableTypeAdapter;
import net.runelite.client.plugins.microbot.inventorysetups.serialization.InventorySetupSerializable;
import net.runelite.client.plugins.microbot.inventorysetups.serialization.LongTypeAdapter;
import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin.CONFIG_GROUP;
import static net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin.CONFIG_GROUP_HUB_BTL;
import static net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin.LAYOUT_PREFIX_MARKER;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.tabs.Layout;

@Slf4j
public class InventorySetupsPersistentDataManager
{

	private final MInventorySetupsPlugin plugin;
	private final ConfigManager configManager;
	private final InventorySetupsCache cache;

	private Gson gson;

	private final List<InventorySetup> inventorySetups;
	private final List<InventorySetupsSection> sections;


	// Fast non-cryptographic hash with relatively short output. Could use murmur3_32_fixed instead; but being extra cautious of collisions.
	public static final HashFunction hashFunction = Hashing.murmur3_128();

	public static final String CONFIG_KEY_SETUPS_MIGRATED_V2 = "migratedV2";
	public static final String CONFIG_KEY_SETUPS_MIGRATED_V3 = "migratedV3";
	public static final String CONFIG_KEY_SETUPS_MIGRATED_CORE_BTL = "migratedCoreBTL";
	public static final String CONFIG_KEY_SETUPS_MIGRATED_SLOTS = "migratedSlots";
	public static final String CONFIG_KEY_SETUPS = "setups";
	public static final String CONFIG_KEY_SETUPS_V2 = "setupsV2";
	public static final String CONFIG_KEY_SETUPS_V3_PREFIX = "setupsV3_";
	public static final String CONFIG_KEY_SETUPS_ORDER_V3 = "setupsOrderV3_";
	public static final String CONFIG_KEY_SECTIONS = "sections";

	@Inject
	public InventorySetupsPersistentDataManager(final MInventorySetupsPlugin plugin,
												final ConfigManager manager,
												final InventorySetupsCache cache,
												final Gson gson,
												final List<InventorySetup> inventorySetups,
												final List<InventorySetupsSection> sections)
	{
		this.plugin = plugin;
		this.configManager = manager;
		this.cache = cache;
		this.gson = gson;
		this.inventorySetups = inventorySetups;
		this.sections = sections;

		this.gson = this.gson.newBuilder().registerTypeAdapter(long.class, new LongTypeAdapter()).create();
		this.gson = this.gson.newBuilder().registerTypeAdapter(InventorySetupItemSerializable.class, new InventorySetupItemSerializableTypeAdapter()).create();
	}

	public void loadConfig()
	{
		inventorySetups.clear();
		sections.clear();
		cache.clearAll();

		// Handles migration of old setup data
		handleMigrationOfOldData();

		final List<InventorySetup> setupsFromConfig = loadV3Setups();
		inventorySetups.addAll(setupsFromConfig);
		processSetupsFromConfig();

		cleanSetupLayouts();
		Type sectionType = new TypeToken<ArrayList<InventorySetupsSection>>()
		{

		}.getType();
		sections.addAll(loadData(CONFIG_KEY_SECTIONS, sectionType));
		for (final InventorySetupsSection section : sections)
		{
			// Remove any duplicates that exist
			List<String> uniqueSetups = section.getSetups().stream().distinct().collect(Collectors.toList());
			section.setSetups(uniqueSetups);

			// Remove setups which don't exist in a section
			section.getSetups().removeIf(s -> !cache.getInventorySetupNames().containsKey(s));
			cache.addSection(section);
		}
	}

	public void updateConfig(boolean updateSetups, boolean updateSections)
	{
		if (updateSetups)
		{
			// Rather than escaping the name to pick a config key, instead use hash of the name. Benefits:
			//   Changes to the escaping function or supported characters in the name string don't affect config keys.
			//   Standardizes length; preventing overflow issues from extremely long names
			//   Keeps json as the single source of truth for names; other plugins won't try to grab names from config keys.
			final String wholePrefix = ConfigManager.getWholeKey(CONFIG_GROUP, null, CONFIG_KEY_SETUPS_V3_PREFIX);
			final List<String> oldSetupKeys = configManager.getConfigurationKeys(wholePrefix);
			Set<String> oldSetupHashes = oldSetupKeys.stream().map(key -> key.substring(wholePrefix.length())).collect(Collectors.toSet());

			final List<String> setupsOrder = new ArrayList<>();
			for (final InventorySetup setup : inventorySetups)
			{
				final String hash = hashFunction.hashUnencodedChars(setup.getName()).toString();
				setupsOrder.add(hash);
				oldSetupHashes.remove(hash);
				final String data = gson.toJson(InventorySetupSerializable.convertFromInventorySetup(setup));
				configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_V3_PREFIX + hash, data);
			}

			for (final String removedSetupHash : oldSetupHashes)
			{
				// Any hashes still in the oldSetupHashes set were for setups that were either renamed (and saved to a new hash above) or deleted.
				configManager.unsetConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_V3_PREFIX + removedSetupHash);
			}

			final String setupsOrderJson = gson.toJson(setupsOrder);
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_ORDER_V3, setupsOrderJson);
		}

		if (updateSections)
		{
			final String jsonSections = gson.toJson(sections);
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SECTIONS, jsonSections);
		}

	}

	private <T> List<T> loadData(final String configKey, Type type)
	{
		final String storedData = configManager.getConfiguration(CONFIG_GROUP, configKey);
		if (Strings.isNullOrEmpty(storedData))
		{
			return new ArrayList<>();
		}
		else
		{
			try
			{
				// serialize the internal data structure from the json in the configuration
				return gson.fromJson(storedData, type);
			}
			catch (Exception e)
			{
				log.error("Exception occurred while loading data", e);
				return new ArrayList<>();
			}
		}
	}

	private List<InventorySetup> loadV1Setups()
	{
		Type setupTypeV1 = new TypeToken<ArrayList<InventorySetup>>()
		{

		}.getType();
		return loadData(CONFIG_KEY_SETUPS, setupTypeV1);
	}

	private List<InventorySetup> loadV2Setups()
	{
		Type setupTypeV2 = new TypeToken<ArrayList<InventorySetupSerializable>>()
		{

		}.getType();
		List<InventorySetupSerializable> issList = new ArrayList<>(loadData(CONFIG_KEY_SETUPS_V2, setupTypeV2));
		List<InventorySetup> isList = new ArrayList<>();
		for (final InventorySetupSerializable iss : issList)
		{
			isList.add(InventorySetupSerializable.convertToInventorySetup(iss));
		}
		return isList;
	}

	private InventorySetup loadV3Setup(String configKey)
	{
		final String storedData = configManager.getConfiguration(CONFIG_GROUP, configKey);
		try
		{
			return InventorySetupSerializable.convertToInventorySetup(gson.fromJson(storedData, InventorySetupSerializable.class));
		}
		catch (Exception e)
		{
			log.error(String.format("Exception occurred while loading %s", configKey), e);
			throw e;
		}
	}

	private List<InventorySetup> loadV3Setups()
	{
		final String wholePrefix = ConfigManager.getWholeKey(CONFIG_GROUP, null, CONFIG_KEY_SETUPS_V3_PREFIX);
		final List<String> loadedSetupWholeKeys = configManager.getConfigurationKeys(wholePrefix);
		Set<String> loadedSetupKeys = loadedSetupWholeKeys.stream().map(
			key -> key.substring(wholePrefix.length() - CONFIG_KEY_SETUPS_V3_PREFIX.length())
		).collect(Collectors.toSet());

		Type setupsOrderType = new TypeToken<ArrayList<String>>()
		{

		}.getType();
		final String setupsOrderJson = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_ORDER_V3);
		List<String> setupsOrder = gson.fromJson(setupsOrderJson, setupsOrderType);
		if (setupsOrder == null)
		{
			setupsOrder = new ArrayList<>();
		}

		List<InventorySetup> loadedSetups = new ArrayList<>();
		for (final String configHash : setupsOrder)
		{
			final String configKey = CONFIG_KEY_SETUPS_V3_PREFIX + configHash;
			if (loadedSetupKeys.remove(configKey))
			{ // Handles if hash is present only in configOrder.
				final InventorySetup setup = loadV3Setup(configKey);
				loadedSetups.add(setup);
			}
		}
		for (final String configKey : loadedSetupKeys)
		{
			// Load any remaining setups not present in setupsOrder. Useful if updateConfig crashes midway.
			log.info("Loading setup that was missing from Order key: " + configKey);
			final InventorySetup setup = loadV3Setup(configKey);
			loadedSetups.add(setup);
		}
		return loadedSetups;
	}

	private void processSetupsFromConfig()
	{
		final String migratedCoreBTL = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_MIGRATED_CORE_BTL);
		final boolean hasMigratedToCoreBTL = !Strings.isNullOrEmpty(migratedCoreBTL) && migratedCoreBTL.equals("true");
		for (final InventorySetup setup : inventorySetups)
		{
			final List<InventorySetupsItem> potentialRunePouch = plugin.getAmmoHandler().getRunePouchDataIfInContainer(setup.getInventory());
			if (setup.getRune_pouch() == null && potentialRunePouch != null)
			{
				setup.updateRunePouch(potentialRunePouch);
			}
			final List<InventorySetupsItem> potentialBoltPouch = plugin.getAmmoHandler().getBoltPouchDataIfInContainer(setup.getInventory());
			if (setup.getBoltPouch() == null && potentialBoltPouch != null)
			{
				setup.updateBoltPouch(potentialBoltPouch);
			}
			final List<InventorySetupsItem> potentialQuiver = plugin.getAmmoHandler().getQuiverDataIfInSetup(setup.getInventory(), setup.getEquipment());
			if (setup.getQuiver() == null && potentialQuiver != null)
			{
				setup.updateQuiver(potentialQuiver);
			}
			if (setup.getNotes() == null)
			{
				setup.updateNotes("");
			}
			if (setup.getAdditionalFilteredItems() == null)
			{
				setup.updateAdditionalItems(new HashMap<>());
			}

			// Fix layouts
			processSetupLayout(setup, hasMigratedToCoreBTL);

			// Makes sure all tags are hidden. This should be done as the plugin manages tags but this acts as
			// a safeguard in case somehow it's missed.
			final String tag = InventorySetupLayoutUtilities.getTagNameForLayout(setup.getName());
			plugin.getTagManager().setHidden(tag, true);

			cache.addSetup(setup);

			// add Item names to all the items in the setup.
			InventorySetup.getSetupItems(setup).forEach(this::addItemName);
		}

		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_MIGRATED_CORE_BTL, "true");

	}

	private void processSetupLayout(final InventorySetup setup, final boolean hasMigratedToCoreBTL)
	{
		// Fix any issues with layouts this setup might have.
		final String tag = InventorySetupLayoutUtilities.getTagNameForLayout(setup.getName());
		final Layout setupLayout = plugin.getLayoutManager().loadLayout(tag);
		if (setupLayout != null)
		{
			return;
		}

		// No layout exists for this setup, let's create one. This will add the tag too.
		final Layout newLayout = plugin.getLayoutUtilities().createSetupLayout(setup);

		if (hasMigratedToCoreBTL)
		{
			// Somehow this setup doesn't have a layout even though it's not the first time using the plugin.
			// This shouldn't be hit unless some config surgery was done. Save the layout and be done.
			plugin.getLayoutManager().saveLayout(newLayout);
			return;
		}

		// This is the first time using the plugin since the core BTL migration. Check for a BTL Layout in the config.
		// Sanitization code was taken from bank tag layouts
		final String sanitizedSetupName = setup.getName().replaceAll("&", "&amp;").replaceAll(":", "&#58;");
		final String expectedHubBTLKey = "inventory_setups_layout_" + sanitizedSetupName;
		final String btlHubLayoutData = configManager.getConfiguration(CONFIG_GROUP_HUB_BTL, expectedHubBTLKey);
		if (Strings.isNullOrEmpty(btlHubLayoutData))
		{
			// No BTL Hub data found to migrate, just save the layout and finish.
			plugin.getLayoutManager().saveLayout(newLayout);
			return;
		}

		log.info("Found Hub Bank Tag Layout for setup " + setup.getName());

		// A BTL hub layout was found, convert it!
		final Layout hubBankTagLayout = plugin.getLayoutUtilities().convertHubBankTagLayoutToCoreBankTagLayout(btlHubLayoutData, tag);
		if (hubBankTagLayout == null)
		{
			log.warn("Failed to convert Hub Bank Tag Layout for setup " + setup.getName());
			// Some problem occurred during the conversion, just save the layout and finish.
			plugin.getLayoutManager().saveLayout(newLayout);
		}
		else
		{
			log.info("Migrating Hub Bank Tag Layout for setup " + setup.getName());
			// This could possibly have some weird items in the layout, but the recalculate method will fix these
			// When a user updates the layout.
			plugin.getLayoutManager().saveLayout(hubBankTagLayout);

			// Someday we might want to delete the hubBTL config key, but not until the migration is stable.
			// This could be done in the caller of this function. Not a huge priority.
		}
	}

	private void cleanSetupLayouts()
	{
		final String layoutKeyPrefix = ConfigManager.getWholeKey(BankTagsPlugin.CONFIG_GROUP, null, BankTagsPlugin.TAG_LAYOUT_PREFIX);
		final List<String> layoutKeys = configManager.getConfigurationKeys(layoutKeyPrefix);

		Set<String> inventorySetupLayoutKeys = layoutKeys.stream()
				.filter(key -> key.contains(LAYOUT_PREFIX_MARKER))
				.collect(Collectors.toSet());

		for (final InventorySetup setup: inventorySetups)
		{
			String key = layoutKeyPrefix + InventorySetupLayoutUtilities.getTagNameForLayout(setup.getName());
			inventorySetupLayoutKeys.remove(key);
		}

		// The remaining keys are those which have the inventory setup layout prefix but no setup exists for them.
		// Meaning they are dangling layouts. Remove them.
		int keyLengthMinusHash = (layoutKeyPrefix + LAYOUT_PREFIX_MARKER).length();
		for (final String key : inventorySetupLayoutKeys)
		{
			String removedSetupHash = key.substring(keyLengthMinusHash);

			// This will remove the "banktag.layouts_" key and remove the tag from every "banktags.item_" that references this tag.
			// Due to the nature of this cleanup algorithm, if a "banktags.item_" key refers to a tag
			// that doesn't have a "banktag.layouts_" key, then it will not be found and remain dangling.
			// This will likely never be the case. This cleanup is primarily to ensure no rogue inventory setup tags appear
			// in the Bank or chat UI when users edit or see tags.
			plugin.getTagManager().removeTag(LAYOUT_PREFIX_MARKER + removedSetupHash);
		}
	}

	private void addItemName(final InventorySetupsItem item)
	{
		item.setName(plugin.getItemManager().getItemComposition(item.getId()).getName());
	}

	private void handleMigrationOfOldData()
	{
		String hasMigratedToV2 = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_MIGRATED_V2);
		if (Strings.isNullOrEmpty(hasMigratedToV2))
		{
			log.info("Migrating data from V1 to V3");
			inventorySetups.addAll(loadV1Setups());
			updateConfig(true, false);
			inventorySetups.clear();
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_MIGRATED_V2, "True");
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_MIGRATED_V3, "True");
		}

		String oldV1Data = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS);
		if (oldV1Data != null)
		{
			log.info("Removing old v1 data key");
			configManager.unsetConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS);
		}

		String hasMigratedToV3 = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_MIGRATED_V3);
		if (Strings.isNullOrEmpty(hasMigratedToV3))
		{
			log.info("Migrating data from V2 to V3");
			inventorySetups.addAll(loadV2Setups());
			updateConfig(true, false);
			inventorySetups.clear();
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_MIGRATED_V3, "True");
		}

		String oldV2Data = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_V2);
		if (oldV2Data != null)
		{
			log.info("Removing old v2 data key");
			configManager.unsetConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_V2);
		}

		String hasMigratedSlots = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_MIGRATED_SLOTS);
		if (Strings.isNullOrEmpty(hasMigratedSlots))
		{
			log.info("Migrating inventory setup items to include slot and locked fields");
			inventorySetups.addAll(loadV3Setups());

			for (final InventorySetup setup : inventorySetups)
			{
				migrateSetupItemSlots(setup);
			}

			updateConfig(true, false);
			inventorySetups.clear();
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_MIGRATED_SLOTS, "true");
		}
	}

	private String fixOldJSONData(final String json)
	{
		JsonElement je = this.gson.fromJson(json, JsonElement.class);
		JsonArray ja = je.getAsJsonArray();
		for (JsonElement elem : ja)
		{
			JsonObject setup = elem.getAsJsonObject();

			// Example if needed in the future
//			if (setup.getAsJsonPrimitive("stackDifference").isBoolean())
//			{
//				int stackDiff = setup.get("stackDifference").getAsBoolean() ? 1 : 0;
//				setup.remove("stackDifference");
//				setup.addProperty("stackDifference", stackDiff);
//			}
		}
		return je.toString();
	}

	/**
	 * Migrates existing inventory setups to populate slot field for items
	 * This method is called when loading inventory setups that were created before
	 * the slot and locked fields were added
	 *
	 * @param setup The inventory setup to migrate
	 */
	private void migrateSetupItemSlots(final InventorySetup setup)
	{
		log.info("Migrating item slots for setup: " + setup.getName());

		if (setup.getInventory() != null)
		{
			for (int i = 0; i < setup.getInventory().size(); i++)
			{
				InventorySetupsItem item = setup.getInventory().get(i);
				if (item != null && !InventorySetupsItem.itemIsDummy(item))
				{
					item.setSlot(i);
					item.setLocked(false);
				}
			}
		}
		if (setup.getEquipment() != null)
		{
			for (int i = 0; i < setup.getEquipment().size(); i++)
			{
				InventorySetupsItem item = setup.getEquipment().get(i);
				if (item != null && !InventorySetupsItem.itemIsDummy(item))
				{
					item.setSlot(i);
					item.setLocked(false);
				}
			}
		}

		if (setup.getRune_pouch() != null)
		{
			for (int i = 0; i < setup.getRune_pouch().size(); i++)
			{
				InventorySetupsItem item = setup.getRune_pouch().get(i);
				if (item != null && !InventorySetupsItem.itemIsDummy(item))
				{
					item.setSlot(i);
					item.setLocked(false);
				}
			}
		}

		if (setup.getBoltPouch() != null)
		{
			for (int i = 0; i < setup.getBoltPouch().size(); i++)
			{
				InventorySetupsItem item = setup.getBoltPouch().get(i);
				if (item != null && !InventorySetupsItem.itemIsDummy(item))
				{
					item.setSlot(i);
					item.setLocked(false);
				}
			}
		}

		if (setup.getQuiver() != null)
		{
			for (int i = 0; i < setup.getQuiver().size(); i++)
			{
				InventorySetupsItem item = setup.getQuiver().get(i);
				if (item != null && !InventorySetupsItem.itemIsDummy(item))
				{
					item.setSlot(i);
					item.setLocked(false);
				}
			}
		}
	}
}
