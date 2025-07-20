/*
 * Copyright (c) 2025, George M <https://github.com/g-mason0> + TaF <https://github.com/SteffenCarlsen>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.farming;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigGroup(FarmingConfig.configGroup)
@ConfigInformation(
	"• This plugin will aid in completing farm runs, herbs, hops, flowers & both types of trees <br />" +
	"• Create an inventory setup & place the desired seed/sapling types within the `Additional Items Section` <br />" +
	"• Enable seed vault if you would like to withdraw saplings & other seeds from there <br />" +
	"• Plugin will support seed box if placed within the inventory of the inventory setup <br />" +
	"• Start anywhere & plugin will equip gear, then create a farm run route based on the shortest distance between all patches <br />"
)
public interface FarmingConfig extends Config
{
	String configGroup = "micro-farming";

	// Config keys
	String inventorySetup = "inventorySetup";
	String useSeedVault = "useSeedVault";

	// Tree patches config keys
	String enableLumbridgeTree = "enableLumbridgeTree";
	String enableVarrockTree = "enableVarrockTree";
	String enableFaladorTree = "enableFaladorTree";
	String enableTaverlyTree = "enableTaverlyTree";
	String enableGnomeStrongholdTree = "enableGnomeStrongholdTree";
	String enableFarmingGuildTree = "enableFarmingGuildTree";

	// Fruit tree patches config keys
	String enableGnomeStrongholdFruitTree = "enableGnomeStrongholdFruitTree";
	String enableCatherbyFruitTree = "enableCatherbyFruitTree";
	String enableTreeGnomeVillageFruitTree = "enableTreeGnomeVillageFruitTree";
	String enableBrimhavenFruitTree = "enableBrimhavenFruitTree";
	String enableLletyaFruitTree = "enableLletyaFruitTree";
	String enableFarmingGuildFruitTree = "enableFarmingGuildFruitTree";

	// Flower patches config keys
	String enableFaladorFlowerPatch = "enableFaladorFlower";
	String enableMorytaniaFlowerPatch = "enableMorytaniaFlower";
	String enableArdougneFlowerPatch = "enableArdougneFlower";
	String enableKourendFlowerPatch = "enableKourendFlower";
	String enableFarmingGuildFlowerPatch = "enableFarmingGuildFlower";
	String enablePrifddinasFlowerPatch = "enablePrifddinasFlower";
	String enableCivitasFlowerPatch = "enableCivitasFlower";

	// Herb patches config keys
	String enableFaladorHerbPatch = "enableFaladorHerb";
	String enableMorytaniaHerbPatch = "enableMorytaniaHerb";
	String enableCatherbyHerbPatch = "enableCatherbyHerb";
	String enableArdougneHerbPatch = "enableArdougneHerb";
	String enableKourendHerbPatch = "enableKourendHerb";
	String enableTrollStrongholdHerbPatch = "enableTrollStrongholdHerb";
	String enableHarmonyIslandHerbPatch = "enableHarmonyIslandHerb";
	String enableWeissHerbPatch = "enableWeissHerb";
	String enableFarmingGuildHerbPatch = "enableFarmingGuildHerb";
	String enableCivitasHerbPatch = "enableCivitasHerb";

	// Bush patches config keys
	String enableChampionsGuildBush = "enableChampionsGuildBush";
	String enableRimmingtonBush = "enableRimmingtonBush";
	String enableArdougneBush = "enableArdougneBush";
	String enableEtceteriaBush = "enableEtceteriaBush";
	String enableFarmingGuildBush = "enableFarmingGuildBush";

	// Hops patches config keys
	String enableLumbridgeHopsPatch = "enableLumbridgeHops";
	String enableSeersHopsPatch = "enableSeersHops";
	String enableYanilleHopsPatch = "enableYanilleHops";
	String enableEntranaHopsPatch = "enableEntranaHops";
	String enableAldarinHopsPatch = "enableAldarinHops";

	// Flower patches config keys
	String enableFaladorAllotmentPatch = "enableFaladorAllotment";
	String enableMorytaniaAllotmentPatch = "enableMorytaniaAllotment";
	String enableArdougneAllotmentPatch = "enableArdougneAllotment";
	String enableKourendAllotmentPatch = "enableKourendAllotment";
	String enableFarmingGuildAllotmentPatch = "enableFarmingGuildAllotment";
	String enablePrifddinasAllotmentPatch = "enablePrifddinasAllotment";
	String enableCivitasAllotmentPatch = "enableCivitasAllotment";
	String enableHarmonyIslandAllotmentPatch = "enableHarmonyIslandAllotment";


	@ConfigSection(
		name = "General Settings",
		description = "Configure general plugin configuration & preferences",
		position = 0
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = inventorySetup,
		name = "Inventory Setup",
		description = "Select an inventory setup to use for farm runs.",
		position = 0,
		section = generalSection
	)
	default InventorySetup inventorySetup() {
		return null;
	}

	@ConfigItem(
		keyName = useSeedVault,
		name = "Seed Vault",
		description = "Should enable use of the Farming Guild Seed Vault.",
		position = 1,
		section = generalSection
	)
	default boolean useSeedVault() {
		return false;
	}


	@ConfigSection(
		name = "Tree Patches",
		description = "Toggles for which tree patches to include in farm runs.",
		position = 1,
		closedByDefault = true
	)
	String treeSection = "treepatch";

	@ConfigItem(
		keyName = enableLumbridgeTree,
		name = "Lumbridge",
		description = "Should include the Lumbridge tree patch in farm runs.",
		position = 0,
		section = treeSection
	)
	default boolean useLumbridgeTree()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableVarrockTree,
		name = "Varrock",
		description = "Should include the Varrock tree patch in farm runs.",
		position = 1,
		section = treeSection
	)
	default boolean useVarrockTree() {
		return true;
	}

	@ConfigItem(
		keyName = enableFaladorTree,
		name = "Falador",
		description = "Should include the Falador tree patch in farm runs.",
		position = 2,
		section = treeSection
	)
	default boolean useFaladorTree()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableTaverlyTree,
		name = "Taverly",
		description = "Should include the Taverly tree patch in farm runs.",
		position = 3,
		section = treeSection
	)
	default boolean useTaverlyTree()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableGnomeStrongholdTree,
		name = "Gnome Stronghold",
		description = "Should include the Gnome Stronghold tree patch in farm runs.",
		position = 4,
		section = treeSection
	)
	default boolean useGnomeStrongholdTree()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableFarmingGuildTree,
		name = "Farming Guild",
		description = "Should include the Farming Guild tree patch in farm runs.",
		position = 5,
		section = treeSection
	)
	default boolean useFarmingGuildTree()
	{
		return true;
	}

	@ConfigSection(
		name = "Fruit Tree Patches",
		description = "Toggles for which fruit tree patches to include in farm runs.",
		position = 2,
		closedByDefault = true
	)
	String fruitTreeSection = "fruittreepatch";

	@ConfigItem(
		keyName = enableGnomeStrongholdFruitTree,
		name = "Gnome Stronghold",
		description = "Should include the Gnome Stronghold fruit tree patch in farm runs.",
		position = 0,
		section = fruitTreeSection
	)
	default boolean useGnomeStrongholdFruitTree()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableCatherbyFruitTree,
		name = "Catherby",
		description = "Should include the Catherby fruit tree patch in farm runs.",
		position = 1,
		section = fruitTreeSection
	)
	default boolean useCatherbyFruitTree()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableTreeGnomeVillageFruitTree,
		name = "Tree Gnome Village",
		description = "Should include the Tree Gnome Village fruit tree patch in farm runs.",
		position = 2,
		section = fruitTreeSection
	)
	default boolean useTreeGnomeVillageFruitTree()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableBrimhavenFruitTree,
		name = "Brimhaven",
		description = "Should include the Brimhaven fruit tree patch in farm runs.",
		position = 3,
		section = fruitTreeSection
	)
	default boolean useBrimhavenFruitTree()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableLletyaFruitTree,
		name = "Lletya",
		description = "Should include the Lletya fruit tree patch in farm runs.",
		position = 4,
		section = fruitTreeSection
	)
	default boolean useLletyaFruitTree()
	{
		return false;
	}

	@ConfigItem(
		keyName = enableFarmingGuildFruitTree,
		name = "Farming Guild",
		description = "Should include the Farming Guild fruit tree patch in farm runs.",
		position = 5,
		section = fruitTreeSection
	)
	default boolean useFarmingGuildFruitTree()
	{
		return true;
	}

	@ConfigSection(
		name = "Flower Patches",
		description = "Toggles for which flower patches to include in farm runs.",
		position = 3,
		closedByDefault = true
	)
	String flowerSection = "flowerpatch";

	@ConfigItem(
		keyName = enableFaladorFlowerPatch,
		name = "Falador",
		description = "Should include the Falador flower patch in farm runs.",
		position = 0,
		section = flowerSection
	)
	default boolean useFaladorFlowerPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableMorytaniaFlowerPatch,
		name = "Morytania",
		description = "Should include the Morytania flower patch in farm runs.",
		position = 1,
		section = flowerSection
	)
	default boolean useMorytaniaFlowerPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableArdougneFlowerPatch,
		name = "Ardougne",
		description = "Should include the Ardougne flower patch in farm runs.",
		position = 2,
		section = flowerSection
	)
	default boolean useArdougneFlowerPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableKourendFlowerPatch,
		name = "Kourend",
		description = "Should include the Kourend flower patch in farm runs.",
		position = 3,
		section = flowerSection
	)
	default boolean useKourendFlowerPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableFarmingGuildFlowerPatch,
		name = "Farming Guild",
		description = "Should include the Farming Guild flower patch in farm runs.",
		position = 4,
		section = flowerSection
	)
	default boolean useFarmingGuildFlowerPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enablePrifddinasFlowerPatch,
		name = "Prifddinas",
		description = "Should include the Prifddinas flower patch in farm runs.",
		position = 5,
		section = flowerSection
	)
	default boolean usePrifddinasFlowerPatch()
	{
		return false;
	}

	@ConfigItem(
		keyName = enableCivitasFlowerPatch,
		name = "Civitas Illa Fortis",
		description = "Should include the Civitas Illa Fortis flower patch in farm runs.",
		position = 6,
		section = flowerSection
	)
	default boolean useCivitasFlowerPatch()
	{
		return true;
	}


	@ConfigSection(
		name = "Herb Patches",
		description = "Toggles for which herb patches to include in farm runs.",
		position = 4,
		closedByDefault = true
	)
	String herbSection = "herbpatch";

	@ConfigItem(
		keyName = enableFaladorHerbPatch,
		name = "Falador",
		description = "Should include the Falador herb patch in farm runs.",
		position = 0,
		section = herbSection
	)
	default boolean useFaladorHerbPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableMorytaniaHerbPatch,
		name = "Morytania",
		description = "Should include the Morytania herb patch in farm runs.",
		position = 1,
		section = herbSection
	)
	default boolean useMorytaniaHerbPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableCatherbyHerbPatch,
		name = "Catherby",
		description = "Should include the Catherby herb patch in farm runs.",
		position = 2,
		section = herbSection
	)
	default boolean useCatherbyHerbPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableArdougneHerbPatch,
		name = "Ardougne",
		description = "Should include the Ardougne herb patch in farm runs.",
		position = 3,
		section = herbSection
	)
	default boolean useArdougneHerbPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableKourendHerbPatch,
		name = "Kourend",
		description = "Should include the Kourend herb patch in farm runs.",
		position = 4,
		section = herbSection
	)
	default boolean useKourendHerbPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableTrollStrongholdHerbPatch,
		name = "Troll Stronghold",
		description = "Should include the Troll Stronghold herb patch in farm runs.",
		position = 5,
		section = herbSection
	)
	default boolean useTrollStrongholdHerbPatch()
	{
		return false;
	}

	@ConfigItem(
		keyName = enableHarmonyIslandHerbPatch,
		name = "Harmony Island",
		description = "Should include the Harmony Island herb patch in farm runs.",
		position = 6,
		section = herbSection
	)
	default boolean useHarmonyIslandHerbPatch()
	{
		return false;
	}

	@ConfigItem(
		keyName = enableWeissHerbPatch,
		name = "Weiss",
		description = "Should include the Weiss herb patch in farm runs.",
		position = 7,
		section = herbSection
	)
	default boolean useWeissHerbPatch()
	{
		return false;
	}

	@ConfigItem(
		keyName = enableFarmingGuildHerbPatch,
		name = "Farming Guild",
		description = "Should include the Farming Guild herb patch in farm runs.",
		position = 8,
		section = herbSection
	)
	default boolean useFarmingGuildHerbPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableCivitasHerbPatch,
		name = "Civitas Illa Fortis",
		description = "Should include the Civitas Illa Fortis herb patch in farm runs.",
		position = 9,
		section = herbSection
	)
	default boolean useCivitasHerbPatch()
	{
		return true;
	}

	@ConfigSection(
		name = "Bush Patches",
		description = "Toggles for which bush patches to include in farm runs.",
		position = 5,
		closedByDefault = true
	)
	String bushSection = "bushpatch";

	@ConfigItem(
		keyName = enableChampionsGuildBush,
		name = "Champions Guild",
		description = "Should include the Champions Guild bush patch in farm runs.",
		position = 0,
		section = bushSection
	)
	default boolean useChampionsGuildBush()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableRimmingtonBush,
		name = "Rimmington",
		description = "Should include the Rimmington bush patch in farm runs.",
		position = 1,
		section = bushSection
	)
	default boolean useRimmingtonBush()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableArdougneBush,
		name = "Ardougne",
		description = "Should include the Ardougne bush patch in farm runs.",
		position = 2,
		section = bushSection
	)
	default boolean useArdougneBush()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableEtceteriaBush,
		name = "Etceteria",
		description = "Should include the Etceteria bush patch in farm runs.",
		position = 3,
		hidden = true,
		section = bushSection
	)
	default boolean useEtceteriaBush()
	{
		return false;
	}

	@ConfigItem(
		keyName = enableFarmingGuildBush,
		name = "Farming Guild",
		description = "Should include the Farming Guild bush patch in farm runs.",
		position = 4,
		section = bushSection
	)
	default boolean useFarmingGuildBush()
	{
		return true;
	}

	@ConfigSection(
		name = "Hops Patches",
		description = "Toggles for which hops patches to include in farm runs.",
		position = 6,
		closedByDefault = true
	)
	String hopsSection = "hopspatch";

	@ConfigItem(
		keyName = enableLumbridgeHopsPatch,
		name = "Lumbridge",
		description = "Should include the Lumbridge hops patch in farm runs.",
		position = 0,
		section = hopsSection
	)
	default boolean useLumbridgeHopsPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableSeersHopsPatch,
		name = "Seers' Village",
		description = "Should include the Seers' Village hops patch in farm runs.",
		position = 1,
		section = hopsSection
	)
	default boolean useSeersHopsPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableYanilleHopsPatch,
		name = "Yanille",
		description = "Should include the Yanille hops patch in farm runs.",
		position = 2,
		section = hopsSection
	)
	default boolean useYanilleHopsPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableEntranaHopsPatch,
		name = "Entrana",
		description = "Should include the Entrana hops patch in farm runs.",
		position = 3,
		hidden = true,
		section = hopsSection
	)
	default boolean useEntranaHopsPatch()
	{
		return false;
	}

	@ConfigItem(
		keyName = enableAldarinHopsPatch,
		name = "Aldarin",
		description = "Should include the Aldarin hops patch in farm runs.",
		position = 4,
		section = hopsSection
	)
	default boolean useAldarinHopsPatch()
	{
		return true;
	}

	@ConfigSection(
		name = "Allotment Patches",
		description = "Toggles for which allotment patches to include in farm runs.",
		position = 7,
		closedByDefault = true
	)
	String allotmentSection = "allotmentpatch";

	@ConfigItem(
		keyName = enableFaladorAllotmentPatch,
		name = "Falador",
		description = "Should include the Falador allotment patch in farm runs.",
		position = 0,
		section = allotmentSection
	)
	default boolean useFaladorAllotmentPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableMorytaniaAllotmentPatch,
		name = "Morytania",
		description = "Should include the Morytania allotment patch in farm runs.",
		position = 1,
		section = allotmentSection
	)
	default boolean useMorytaniaAllotmentPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableArdougneAllotmentPatch,
		name = "Ardougne",
		description = "Should include the Ardougne allotment patch in farm runs.",
		position = 2,
		section = allotmentSection
	)
	default boolean useArdougneAllotmentPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableKourendAllotmentPatch,
		name = "Kourend",
		description = "Should include the Kourend allotment patch in farm runs.",
		position = 3,
		section = allotmentSection
	)
	default boolean useKourendAllotmentPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableFarmingGuildAllotmentPatch,
		name = "Farming Guild",
		description = "Should include the Farming Guild allotment patch in farm runs.",
		position = 4,
		section = allotmentSection
	)
	default boolean useFarmingGuildAllotmentPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enablePrifddinasAllotmentPatch,
		name = "Prifddinas",
		description = "Should include the Prifddinas allotment patch in farm runs.",
		position = 5,
		section = allotmentSection
	)
	default boolean usePrifddinasAllotmentPatch()
	{
		return false;
	}

	@ConfigItem(
		keyName = enableCivitasAllotmentPatch,
		name = "Civitas Illa Fortis",
		description = "Should include the Civitas Illa Fortis allotment patch in farm runs.",
		position = 6,
		section = allotmentSection
	)
	default boolean useCivitasAllotmentPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = enableHarmonyIslandAllotmentPatch,
		name = "Harmony Island",
		description = "Should include the Harmony Island allotment patch in farm runs.",
		position = 7,
		section = allotmentSection
	)
	default boolean useHarmonyIslandAllotmentPatch()
	{
		return false;
	}
}
