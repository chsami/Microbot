package net.runelite.client.plugins.microbot.inventorysetups;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.emptyContainer;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.setup;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class InventorySetupsCacheTest
{
	private InventorySetupsCache cache;
	private InventorySetup vorkath;
	private InventorySetup zulrah;

	@Before
	public void setUp()
	{
		cache = new InventorySetupsCache();
		vorkath = setup("Vorkath", emptyContainer(28), emptyContainer(14));
		zulrah = setup("Zulrah", emptyContainer(28), emptyContainer(14));
		cache.addSetup(vorkath);
		cache.addSetup(zulrah);
	}

	private static InventorySetupsSection section(String name, String... setups)
	{
		InventorySetupsSection section = new InventorySetupsSection(name);
		section.setSetups(new ArrayList<>(Arrays.asList(setups)));
		return section;
	}

	@Test
	public void importedSectionLinksItsExistingSetups()
	{
		InventorySetupsSection bossing = section("Bossing", "Vorkath", "Zulrah");

		cache.addSection(bossing);

		assertSame(vorkath, cache.getSectionSetupsMap().get("Bossing").get("Vorkath"));
		assertSame(bossing, cache.getSetupSectionsMap().get("Zulrah").get("Bossing"));
	}

	@Test
	public void renamingASetupKeepsItsSections()
	{
		InventorySetupsSection bossing = section("Bossing", "Vorkath");
		cache.addSection(bossing);

		cache.updateSetupName(vorkath, "Vorkath v2");
		vorkath.setName("Vorkath v2");

		assertSame(vorkath, cache.getInventorySetupNames().get("Vorkath v2"));
		assertFalse(cache.getInventorySetupNames().containsKey("Vorkath"));
		assertSame(vorkath, cache.getSectionSetupsMap().get("Bossing").get("Vorkath v2"));
		assertFalse(cache.getSectionSetupsMap().get("Bossing").containsKey("Vorkath"));
		assertSame(bossing, cache.getSetupSectionsMap().get("Vorkath v2").get("Bossing"));
	}

	@Test
	public void renamingASectionKeepsItsSetups()
	{
		InventorySetupsSection bossing = section("Bossing", "Vorkath");
		cache.addSection(bossing);

		cache.updateSectionName(bossing, "Bosses");
		bossing.setName("Bosses");

		assertSame(bossing, cache.getSectionNames().get("Bosses"));
		assertSame(vorkath, cache.getSectionSetupsMap().get("Bosses").get("Vorkath"));
		assertSame(bossing, cache.getSetupSectionsMap().get("Vorkath").get("Bosses"));
		assertFalse(cache.getSetupSectionsMap().get("Vorkath").containsKey("Bossing"));
	}

	@Test
	public void removingASetupRemovesItFromEverySection()
	{
		cache.addSection(section("Bossing", "Vorkath", "Zulrah"));
		cache.addSection(section("Favourites", "Vorkath"));

		cache.removeSetup(vorkath);

		assertFalse(cache.getInventorySetupNames().containsKey("Vorkath"));
		assertFalse(cache.getSetupSectionsMap().containsKey("Vorkath"));
		assertFalse(cache.getSectionSetupsMap().get("Bossing").containsKey("Vorkath"));
		assertFalse(cache.getSectionSetupsMap().get("Favourites").containsKey("Vorkath"));
		assertTrue(cache.getSectionSetupsMap().get("Bossing").containsKey("Zulrah"));
	}

	@Test
	public void removingASectionKeepsItsSetups()
	{
		InventorySetupsSection bossing = section("Bossing", "Vorkath");
		cache.addSection(bossing);

		cache.removeSection(bossing);

		assertFalse(cache.getSectionNames().containsKey("Bossing"));
		assertTrue(cache.getSetupSectionsMap().get("Vorkath").isEmpty());
		assertSame(vorkath, cache.getInventorySetupNames().get("Vorkath"));
	}

	@Test
	public void setupCanBeAddedToAndRemovedFromASection()
	{
		InventorySetupsSection bossing = section("Bossing");
		cache.addSection(bossing);

		cache.addSetupToSection(bossing, zulrah);
		assertSame(zulrah, cache.getSectionSetupsMap().get("Bossing").get("Zulrah"));

		cache.removeSetupFromSection(bossing, zulrah);
		assertTrue(cache.getSectionSetupsMap().get("Bossing").isEmpty());
		assertTrue(cache.getSetupSectionsMap().get("Zulrah").isEmpty());
	}

	@Test
	public void clearAllEmptiesEveryMap()
	{
		cache.addSection(section("Bossing", "Vorkath"));

		cache.clearAll();

		assertEquals(0, cache.getInventorySetupNames().size());
		assertEquals(0, cache.getSectionNames().size());
		assertEquals(0, cache.getSetupSectionsMap().size());
		assertEquals(0, cache.getSectionSetupsMap().size());
	}
}
