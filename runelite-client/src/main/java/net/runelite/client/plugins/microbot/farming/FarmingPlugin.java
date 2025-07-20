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

import com.google.inject.Provides;
import java.awt.AWTException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.PredicateCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingHandler;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingRegion;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingWorld;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.PatchImplementation;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = PluginDescriptor.GMason + "Farm run",
	description = "This plugin allows you to manage most farm patches",
	tags = {"farming", "microbot", "skilling", "herb", "tree", "fruit", "fruit tree", "allotment", "flower", "bush", "patches"},
	enabledByDefault = false
)
@Slf4j
public class FarmingPlugin extends Plugin implements SchedulablePlugin
{
	@Getter
	private final double version = 1.0;

	@Inject
	private FarmingConfig config;

	@Provides
	FarmingConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FarmingConfig.class);
	}

	@Inject
	private OverlayManager overlayManager;
	@Inject
	private FarmingOverlay farmingOverlay;

	@Inject
	private FarmingWorld farmingWorld;

	private FarmingHandler farmingHandler;

	@Inject
	private FarmingScript farmingScript;

	@Getter
	private Map<FarmingPatch, CropState> patchStateMap = new LinkedHashMap<>();

	Predicate<CropState> patchStateFilter = state -> state == CropState.EMPTY || state == CropState.HARVESTABLE ||
		state == CropState.DISEASED || state == CropState.DEAD || state == CropState.UNCHECKED;

	/**
	 * Varbits with the suffix _A1, _A2, etc. are not included in this as they appear to be used for the grape patches in Kourend.
	 * See {@link FarmingWorld} for patch definitions and region mapping.
	 */
	private final int[] patchVarbits = new int[]{
		VarbitID.FARMING_TRANSMIT_A,
		VarbitID.FARMING_TRANSMIT_B,
		VarbitID.FARMING_TRANSMIT_C,
		VarbitID.FARMING_TRANSMIT_D,
		VarbitID.FARMING_TRANSMIT_E,
		VarbitID.FARMING_TRANSMIT_F,
		VarbitID.FARMING_TRANSMIT_G,
		VarbitID.FARMING_TRANSMIT_H,
		VarbitID.FARMING_TRANSMIT_I,
		VarbitID.FARMING_TRANSMIT_J,
		VarbitID.FARMING_TRANSMIT_K,
		VarbitID.FARMING_TRANSMIT_L,
		VarbitID.FARMING_TRANSMIT_M,
		VarbitID.FARMING_TRANSMIT_N,
		VarbitID.FARMING_TRANSMIT_O,
		VarbitID.FARMING_TRANSMIT_P,
	};

	/*
	 * TODO:
	 *  x Build a list of farming patches that are within a state where they can be interacted with, WEEDED, HARVEST, PLANT or DEAD
	 *  x Finalize gathering static data for all farming patches, including: Polygon of the patch & worldpoint of where to walk to / path find from.
	 *  x Create a farm run route that will leverage the shortest path plugin to find the shortest path between all patches or areas that need to be visited
	 *  x Make the farm run schedulable using the PluginScheduler, still need to figure out a strong start condition for the plugin (possibly if x amount of patches need attention) (Predicate Condition?)
	 *  x Add a requirements check for configured farming patches, so we can determine if the player has completed the required quests to reach this patch.
	 *  - Rely on inventory setups additional items to determine what TYPE of sapling or seed to plant (possibly create a ItemRequirement to model the specific required data, such as item name & item id)
	 *  - Allow for use of SeedBox within the inventory (we can check if the inventory setups inventory section contains a seed box)
	 *  - Allow for use of the Farming Guild Seed Vault (we could rely on a config option to determine if the seed vault should be used, add level requirements, etc.)
	 *  - Possibly make a quest helper overlay that will visually show the route that is being taken, and the patches that are being visited
	 *  - Leverage all this data to create a farm run script that will handle all the farming patches, including weeding, harvesting, planting, and dead patches
	 */

	protected void startUp() throws AWTException
	{
		if (overlayManager != null)
		{
			overlayManager.add(farmingOverlay);
		}
		farmingScript.run();
	}

	protected void shutDown()
	{
		farmingScript.shutdown();
		overlayManager.remove(farmingOverlay);
		patchStateMap.clear();
	}

	@Schedule(
		period = 100,
		unit = ChronoUnit.MILLIS
	)
	public void update()
	{
		if (!Microbot.isLoggedIn()) return;
		if (patchStateMap.isEmpty())
		{
			log.debug("Patch state map is empty, refreshing states");

			patchStateMap = fetchPatchStateMap();

			Microbot.getClientThread().runOnSeperateThread(() -> {
				sortPatchMap(Microbot.getClient().getLocalPlayer().getWorldLocation());
				return null;
			});
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged ev)
	{
		if (Arrays.stream(patchVarbits).anyMatch(varbit -> varbit == ev.getVarbitId()))
		{
			List<FarmingPatch> patches = getPatchesForRegion();
			if (!patches.isEmpty())
			{
				for (FarmingPatch patch : patches)
				{
					CropState cropState = getFarmingHandler().predictPatch(patch);
					patchStateMap.put(patch, cropState);
				}
			}
		}
	}

	@Subscribe
	public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event)
	{
		if (!Objects.equals(event.getPlugin(), this)) return;

		log.debug("Scheduled soft stop for FarmingPlugin, shutting down script and clearing patch states");
		Microbot.stopPlugin(this);
	}

	@Override
	public LogicalCondition getStartCondition()
	{
		LogicalCondition startCondition = new AndCondition();
		/*
			We use fetchPatchStateMap() to ensure we have the latest patch states before checking conditions.
		 */
		Map<FarmingPatch, CropState> _patchStateMap = Microbot.getClientThread().runOnClientThreadOptional(this::fetchPatchStateMap).orElse(new LinkedHashMap<>());
		if (_patchStateMap.isEmpty())
		{
			log.debug("Patch state map is empty, no patches to check for attention");
			return startCondition;
		}

		Predicate<Map<FarmingPatch, CropState>> allExceptTrees = psm -> psm.entrySet().stream()
			.filter(entry -> {
				PatchImplementation type = entry.getKey().getImplementation();
				return type != PatchImplementation.TREE && type != PatchImplementation.FRUIT_TREE;
			})
			.allMatch(entry -> {
				CropState state = entry.getValue();
				return patchStateFilter.test(state);
			});

		Predicate<Map<FarmingPatch, CropState>> allExceptFruitTrees = psm -> psm.entrySet().stream()
			.filter(entry -> {
				PatchImplementation type = entry.getKey().getImplementation();
				return type != PatchImplementation.FRUIT_TREE;
			})
			.allMatch(entry -> {
				CropState state = entry.getValue();
				return patchStateFilter.test(state);
			});

		Predicate<Map<FarmingPatch, CropState>> combinedPredicate = allExceptTrees.or(allExceptFruitTrees);

		PredicateCondition<Map<FarmingPatch, CropState>> patchesNeedAttention = new PredicateCondition<>(
			"Patches require attention",
			combinedPredicate,
			() -> _patchStateMap,
			"All configured patches except tree patches require attention"
		);

		startCondition.addCondition(patchesNeedAttention);

		return startCondition;
	}

	@Override
	public LogicalCondition getStopCondition()
	{
		LogicalCondition stopCondition = new AndCondition();
		/*
			We use fetchPatchStateMap() to ensure we have the latest patch states before checking conditions.
		 */
		Map<FarmingPatch, CropState> _patchStateMap = Microbot.getClientThread().runOnClientThreadOptional(this::fetchPatchStateMap).orElse(new LinkedHashMap<>());
		if (_patchStateMap.isEmpty())
		{
			log.debug("Patch state map is empty, no patches to check for attention");
			return stopCondition;
		}
		Predicate<Collection<CropState>> noPatchesNeedAttentionPredicate = cs -> cs.stream().noneMatch(patchStateFilter);

		PredicateCondition<Collection<CropState>> noPatchesNeedAttention = new PredicateCondition<>(
			"No patches need attention",
			noPatchesNeedAttentionPredicate,
			_patchStateMap::values,
			"No patches in an attention-worthy state"
		);

		stopCondition.addCondition(noPatchesNeedAttention);

		return stopCondition;
	}

	@Override
	public ConfigDescriptor getConfigDescriptor()
	{
		if (Microbot.getConfigManager() == null)
		{
			return null;
		}
		FarmingConfig conf = Microbot.getConfigManager().getConfig(FarmingConfig.class);
		return Microbot.getConfigManager().getConfigDescriptor(conf);
	}

	/**
	 * Returns the total runtime duration of the farming script.
	 *
	 * @return the script runtime as a Duration
	 */
	public Duration getScriptRuntime()
	{
		return farmingScript.getRunTime();
	}

	/**
	 * Returns the current state of the farming script.
	 *
	 * @return the current FarmingScriptState
	 */
	public String getScriptState()
	{
		return farmingScript != null && farmingScript.getState() != null
			? farmingScript.getState().toString()
			: "Unknown";
	}

	/**
	 * Lazily initializes and returns the FarmingHandler instance.
	 *
	 * @return the FarmingHandler instance
	 */
	private FarmingHandler getFarmingHandler()
	{
		if (farmingHandler == null)
		{
			farmingHandler = new FarmingHandler(Microbot.getClient(), Microbot.getConfigManager());
		}
		return farmingHandler;
	}

	/**
	 * Refreshes the state of farming patches that are enabled in the config.
	 */
	private Map<FarmingPatch, CropState> fetchPatchStateMap()
	{
		Map<FarmingPatch, CropState> _patchStateMap = new LinkedHashMap<>();
		FarmingHandler handler = getFarmingHandler();

		List<FarmingPatch> configuredPatches = getEnabledPatches();

		for (FarmingPatch patch : configuredPatches)
		{
			CropState state = handler.predictPatch(patch);
			if (state != null)
			{
				_patchStateMap.put(patch, state);
			}
		}

		log.debug("Refreshed states for {} configured farming patches", _patchStateMap.size());
		return _patchStateMap;
	}

	/**
	 * Returns a list of enabled farming patches that are located in the same region as the player.
	 *
	 * @return list of farming patches in the player's current region
	 */
	public List<FarmingPatch> getPatchesForRegion()
	{
		final WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
		if (patchStateMap.isEmpty())
		{
			log.debug("No patches found in current region");
			return Collections.emptyList();
		}
		return patchStateMap.keySet().stream()
			.filter(patch -> patch.getRegion().isInBounds(playerLocation))
			.collect(Collectors.toList());
	}

	/**
	 * Gets all patches that need attention (empty, harvestable, diseased, dead).
	 *
	 * @return List of patches that need attention
	 */
	public List<FarmingPatch> getPatchesNeedingAttention()
	{
		List<FarmingPatch> result = new ArrayList<>();

		for (Map.Entry<FarmingPatch, CropState> entry : patchStateMap.entrySet())
		{
			CropState state = entry.getValue();
			if (patchStateFilter.test(state))
			{
				result.add(entry.getKey());
			}
		}

		return result;
	}

	/**
	 * Gets all patches in specified tabs that are enabled in the configuration.
	 *
	 * @return List of enabled patches
	 */
	public List<FarmingPatch> getEnabledPatches()
	{
		List<FarmingPatch> enabledPatches = new ArrayList<>();

		// Tree patches
		addPatchesByType(enabledPatches, "Lumbridge", PatchImplementation.TREE, () -> config.useLumbridgeTree());
		addPatchesByType(enabledPatches, "Varrock", PatchImplementation.TREE, () -> config.useVarrockTree());
		addPatchesByType(enabledPatches, "Falador", PatchImplementation.TREE, () -> config.useFaladorTree());
		addPatchesByType(enabledPatches, "Taverly", PatchImplementation.TREE, () -> config.useTaverlyTree());
		addPatchesByType(enabledPatches, "Gnome Stronghold", PatchImplementation.TREE, () -> config.useGnomeStrongholdTree());
		addPatchesByType(enabledPatches, "Farming Guild", PatchImplementation.TREE, () -> config.useFarmingGuildTree() && Rs2Player.getSkillRequirement(Skill.FARMING, 45));

		// Fruit tree patches
		addPatchesByType(enabledPatches, "Gnome Stronghold", PatchImplementation.FRUIT_TREE, () -> config.useGnomeStrongholdFruitTree());
		addPatchesByType(enabledPatches, "Catherby", PatchImplementation.FRUIT_TREE, () -> config.useCatherbyFruitTree());
		addPatchesByType(enabledPatches, "Tree Gnome Village", PatchImplementation.FRUIT_TREE, () -> config.useTreeGnomeVillageFruitTree());
		addPatchesByType(enabledPatches, "Brimhaven", PatchImplementation.FRUIT_TREE, () -> config.useBrimhavenFruitTree());
		addPatchesByType(enabledPatches, "Lletya", PatchImplementation.FRUIT_TREE, () -> config.useLletyaFruitTree() && Rs2Player.getQuestState(Quest.REGICIDE) == QuestState.FINISHED);
		addPatchesByType(enabledPatches, "Farming Guild", PatchImplementation.FRUIT_TREE, () -> config.useFarmingGuildFruitTree() && Rs2Player.getSkillRequirement(Skill.FARMING, 85));

		// Flower patches
		addPatchesByType(enabledPatches, "Falador", PatchImplementation.FLOWER, () -> config.useFaladorFlowerPatch());
		addPatchesByType(enabledPatches, "Morytania", PatchImplementation.FLOWER, () -> config.useMorytaniaFlowerPatch() && Rs2Player.getQuestState(Quest.PRIEST_IN_PERIL) == QuestState.FINISHED);
		addPatchesByType(enabledPatches, "Ardougne", PatchImplementation.FLOWER, () -> config.useArdougneFlowerPatch());
		addPatchesByType(enabledPatches, "Kourend", PatchImplementation.FLOWER, () -> config.useKourendFlowerPatch() && Rs2Player.getQuestState(Quest.CLIENT_OF_KOUREND) == QuestState.FINISHED);
		addPatchesByType(enabledPatches, "Farming Guild", PatchImplementation.FLOWER, () -> config.useFarmingGuildFlowerPatch() && Rs2Player.getSkillRequirement(Skill.FARMING, 45));
		addPatchesByType(enabledPatches, "Prifddinas", PatchImplementation.FLOWER, () -> config.usePrifddinasFlowerPatch() && Rs2Player.getQuestState(Quest.SONG_OF_THE_ELVES) == QuestState.FINISHED);
		addPatchesByType(enabledPatches, "Civitas illa Fortis", PatchImplementation.FLOWER, () -> config.useCivitasFlowerPatch() && Rs2Player.getQuestState(Quest.CHILDREN_OF_THE_SUN) == QuestState.FINISHED);

		// Herb patches
		addPatchesByType(enabledPatches, "Falador", PatchImplementation.HERB, () -> config.useFaladorHerbPatch());
		addPatchesByType(enabledPatches, "Morytania", PatchImplementation.HERB, () -> config.useMorytaniaHerbPatch() && Rs2Player.getQuestState(Quest.PRIEST_IN_PERIL) == QuestState.FINISHED);
		addPatchesByType(enabledPatches, "Catherby", PatchImplementation.HERB, () -> config.useCatherbyHerbPatch());
		addPatchesByType(enabledPatches, "Ardougne", PatchImplementation.HERB, () -> config.useArdougneHerbPatch());
		addPatchesByType(enabledPatches, "Kourend", PatchImplementation.HERB, () -> config.useKourendHerbPatch() && Rs2Player.getQuestState(Quest.CLIENT_OF_KOUREND) == QuestState.FINISHED);
		addPatchesByType(enabledPatches, "Troll Stronghold", PatchImplementation.HERB, () -> config.useTrollStrongholdHerbPatch() && Rs2Player.getQuestState(Quest.MY_ARMS_BIG_ADVENTURE) == QuestState.FINISHED);
		addPatchesByType(enabledPatches, "Harmony", PatchImplementation.HERB, () -> config.useHarmonyIslandHerbPatch() && Rs2Player.getQuestState(Quest.THE_GREAT_BRAIN_ROBBERY) == QuestState.FINISHED);
		addPatchesByType(enabledPatches, "Weiss", PatchImplementation.HERB, () -> config.useWeissHerbPatch() && Rs2Player.getQuestState(Quest.MAKING_FRIENDS_WITH_MY_ARM) == QuestState.FINISHED);
		addPatchesByType(enabledPatches, "Farming Guild", PatchImplementation.HERB, () -> config.useFarmingGuildHerbPatch() && Rs2Player.getSkillRequirement(Skill.FARMING, 45));
		addPatchesByType(enabledPatches, "Civitas illa Fortis", PatchImplementation.HERB, () -> config.useCivitasHerbPatch() && Rs2Player.getQuestState(Quest.CHILDREN_OF_THE_SUN) == QuestState.FINISHED);

		// Bush patches
		addPatchesByType(enabledPatches, "Champions' Guild", PatchImplementation.BUSH, () -> config.useChampionsGuildBush());
		addPatchesByType(enabledPatches, "Rimmington", PatchImplementation.BUSH, () -> config.useRimmingtonBush());
		addPatchesByType(enabledPatches, "Ardougne", PatchImplementation.BUSH, () -> config.useArdougneBush());
		addPatchesByType(enabledPatches, "Etceteria", PatchImplementation.BUSH, () -> config.useEtceteriaBush());
		addPatchesByType(enabledPatches, "Farming Guild", PatchImplementation.BUSH, () -> config.useFarmingGuildBush() && Rs2Player.getSkillRequirement(Skill.FARMING, 45));

		// Hops patches
		addPatchesByType(enabledPatches, "Lumbridge", PatchImplementation.HOPS, () -> config.useLumbridgeHopsPatch());
		addPatchesByType(enabledPatches, "Seers' Village", PatchImplementation.HOPS, () -> config.useSeersHopsPatch());
		addPatchesByType(enabledPatches, "Yanille", PatchImplementation.HOPS, () -> config.useYanilleHopsPatch());
		addPatchesByType(enabledPatches, "Entrana", PatchImplementation.HOPS, () -> config.useEntranaHopsPatch());
		addPatchesByType(enabledPatches, "Aldarin", PatchImplementation.HOPS, () -> config.useAldarinHopsPatch());

		// Allotment patches
		addPatchesByType(enabledPatches, "Falador", PatchImplementation.ALLOTMENT, () -> config.useFaladorAllotmentPatch());
		addPatchesByType(enabledPatches, "Morytania", PatchImplementation.ALLOTMENT, () -> config.useMorytaniaAllotmentPatch() && Rs2Player.getQuestState(Quest.PRIEST_IN_PERIL) == QuestState.FINISHED);
		addPatchesByType(enabledPatches, "Ardougne", PatchImplementation.ALLOTMENT, () -> config.useArdougneAllotmentPatch());
		addPatchesByType(enabledPatches, "Kourend", PatchImplementation.ALLOTMENT, () -> config.useKourendAllotmentPatch() && Rs2Player.getQuestState(Quest.CLIENT_OF_KOUREND) == QuestState.FINISHED);
		addPatchesByType(enabledPatches, "Farming Guild", PatchImplementation.ALLOTMENT, () -> config.useFarmingGuildAllotmentPatch() && Rs2Player.getSkillRequirement(Skill.FARMING, 45));
		addPatchesByType(enabledPatches, "Prifddinas", PatchImplementation.ALLOTMENT, () -> config.usePrifddinasAllotmentPatch() && Rs2Player.getQuestState(Quest.SONG_OF_THE_ELVES) == QuestState.FINISHED);
		addPatchesByType(enabledPatches, "Civitas illa Fortis", PatchImplementation.ALLOTMENT, () -> config.useCivitasAllotmentPatch() && Rs2Player.getQuestState(Quest.CHILDREN_OF_THE_SUN) == QuestState.FINISHED);
		addPatchesByType(enabledPatches, "Harmony", PatchImplementation.ALLOTMENT, () -> config.useHarmonyIslandAllotmentPatch() && Rs2Player.getQuestState(Quest.THE_GREAT_BRAIN_ROBBERY) == QuestState.FINISHED);

		log.debug("Total enabled patches found: {}", enabledPatches.size());
		return enabledPatches;
	}

	/**
	 * Helper method to add patches of a specific type and region to a list.
	 */
	private void addPatchesByType(List<FarmingPatch> list, String regionName, PatchImplementation implementation, Supplier<Boolean> requirements)
	{
		if (!requirements.get()) {
			log.debug("Skipping patches for {} in {} due to unmet requirements", implementation.name(), regionName);
			return;
		}
		boolean foundMatch = false;
		for (FarmingPatch patch : farmingWorld.getTabs().get(implementation.getTab()))
		{
			if (patch.getRegion().getName().equalsIgnoreCase(regionName) && patch.getImplementation() == implementation)
			{
				list.add(patch);
				foundMatch = true;
				log.debug("Added patch {} from {} in {}", implementation.name(), implementation.getTab().name(), regionName);
			}
		}

		if (!foundMatch)
		{
			log.debug("No matching patches found for implementation {} in region {}", implementation.name(), regionName);
		}
	}

	/**
	 * Sorts the patchStateMap in-place so that patches from the closest region come first, then the next closest, etc.
	 * Uses Pathfinder to optimize region order, then groups all patches from each region together.
	 */
	private void sortPatchMap(WorldPoint start)
	{

		assert !Microbot.getClient().isClientThread() : "sortPatchMap should not be called on the client thread";

		if (patchStateMap.isEmpty()) {
			log.debug("Patch state map is empty");
			return;
		}

		// Group patches by region
		Map<FarmingRegion, List<FarmingPatch>> regionToPatches = patchStateMap.keySet().stream()
			.collect(Collectors.groupingBy(FarmingPatch::getRegion, LinkedHashMap::new, Collectors.toList()));

		// Representative patch for each region
		Map<FarmingRegion, FarmingPatch> regionToRepPatch = regionToPatches.entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)));

		SortedSet<FarmingRegion> unvisited = new TreeSet<>(regionToRepPatch.keySet());
		List<FarmingRegion> ordered = new ArrayList<>();
		WorldPoint current = start;

		while (!unvisited.isEmpty())
		{
			Set<WorldPoint> targets = unvisited.stream()
				.map(region -> regionToRepPatch.get(region).getLocation())
				.collect(Collectors.toSet());

			if (ShortestPathPlugin.getPathfinderConfig().getTransports().isEmpty())
			{
				ShortestPathPlugin.getPathfinderConfig().refresh();
			}

			Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), current, targets);
			pathfinder.run();
			List<WorldPoint> path = pathfinder.getPath();
			if (path == null || path.isEmpty())
			{
				break;
			}


			WorldPoint closestPoint = path.get(path.size() - 1);
			FarmingRegion closestRegion;

			closestRegion = unvisited.stream()
				.filter(region -> region.isInBounds(closestPoint))
				.findFirst()
				.orElse(null);

			if (closestRegion == null)
			{
				WorldArea closestArea = new WorldArea(closestPoint, 2, 2);
				closestRegion = unvisited.stream()
					.filter(region -> regionToPatches.getOrDefault(region, List.of()).stream()
						.anyMatch(patch -> closestArea.intersectsWith2D(new WorldArea(patch.getLocation(), 2, 2))))
					.findFirst()
					.orElse(null);
			}

			if (closestRegion == null)
			{
				break;
			}

			ordered.add(closestRegion);
			unvisited.remove(closestRegion);
			current = closestPoint;
		}

		LinkedHashMap<FarmingPatch, CropState> sortedMap = new LinkedHashMap<>();
		// place patches that have been sorted thus far
		for (FarmingRegion region : ordered)
		{
			for (FarmingPatch patch : regionToPatches.get(region))
			{
				sortedMap.put(patch, patchStateMap.get(patch));
			}
		}

		// place any remaining patches that were not sorted
		patchStateMap.keySet().stream()
			.filter(patch -> !sortedMap.containsKey(patch))
			.forEach(patch -> sortedMap.put(patch, patchStateMap.get(patch)));

		patchStateMap.clear();
		patchStateMap.putAll(sortedMap);
	}
}