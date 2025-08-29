/*
 * Copyright (c) 2018, NotFoxtrot <https://github.com/NotFoxtrot>
 * Copyright (c) 2018 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
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
package net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.inject.Singleton;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.timetracking.Tab;

import java.awt.*;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class FarmingWorld
{
	@SuppressWarnings("PMD.ImmutableField")
	private Multimap<Integer, FarmingRegion> regions = HashMultimap.create();

	@Getter
	private Map<Tab, Set<FarmingPatch>> tabs = new HashMap<>();

	private final Comparator<FarmingPatch> tabSorter = Comparator
			.comparing(FarmingPatch::getImplementation)
			.thenComparing((FarmingPatch p) -> p.getRegion().getName())
			.thenComparing(FarmingPatch::getName);

	@Getter
	private final FarmingRegion farmingGuildRegion;

	FarmingWorld()
	{
		// Some of these patches get updated in multiple regions.
		// It may be worth it to add a specialization for these patches
		add(new FarmingRegion("Al Kharid", 13106, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.CACTUS, new WorldPoint(3317, 3203, 0), new Polygon(
						new int[]{3315, 3315, 3316, 3316},
						new int[]{3202, 3203, 3203, 3202},
						4
				), NpcID.FARMING_GARDENER_CACTUS)
		), 13362, 13105);

		add(new FarmingRegion("Aldarin", 5421, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.HOPS, new WorldPoint(1366, 2941, 0),
						new Polygon(
								new int[]{1363, 1363, 1366, 1366},
								new int[]{2937, 2940, 2940, 2937},
								4
						), NpcID.FARMING_GARDENER_HOPS_5)
		), 5165, 5166, 5422, 5677, 5678);

		add(new FarmingRegion("Ardougne", 10290, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.BUSH, new WorldPoint(2616, 3226, 0),
						new Polygon(
								new int[]{2617, 2617, 2618, 2618},
								new int[]{3225, 3226, 3226, 3225},
								4
						), NpcID.FARMING_GARDENER_BUSH_4)
		), 10546);
		add(new FarmingRegion("Ardougne", 10548, false,
				new FarmingPatch("North", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.ALLOTMENT, new WorldPoint(2672, 3379, 0),
						new Polygon(
								new int[]{2662, 2662, 2671, 2671, 2663, 2663},
								new int[]{3377, 3379, 3379, 3378, 3378, 3377},
								6
						),
						NpcID.KRAGEN, 0),
				new FarmingPatch("South", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.ALLOTMENT, new WorldPoint(2672, 3371, 0),
						new Polygon(
								new int[]{2662, 2662, 2663, 2663, 2671, 2671},
								new int[]{3370, 3372, 3372, 3371, 3371, 3370},
								6
						),
						NpcID.KRAGEN, 1),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_C, PatchImplementation.FLOWER, new WorldPoint(2668, 3375, 0),
						new Polygon(
								new int[]{2666, 2666, 2667, 2667},
								new int[]{3374, 3375, 3375, 3374},
								4)
				),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_D, PatchImplementation.HERB, new WorldPoint(2672, 3375, 0),
						new Polygon(
								new int[]{2670, 2670, 2671, 2671},
								new int[]{3374, 3375, 3375, 3374},
								4
						)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_E, PatchImplementation.COMPOST, new WorldPoint(2662, 3375, 0))
		));

		add(new FarmingRegion("Avium Savannah", 6702, true,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.HARDWOOD_TREE, new WorldPoint(1685, 2971, 0),
						new Polygon(
								new int[]{1686, 1686, 1688, 1688},
								new int[]{2971, 2973, 2973, 2971},
								4
						),
						NpcID.FROG_QUEST_MARCELLUS)
		), 6446);

		add(new FarmingRegion("Brimhaven", 11058, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.FRUIT_TREE, new WorldPoint(2766, 3213, 0),
						new Polygon(
								new int[]{2764, 2764, 2765, 2765},
								new int[]{3212, 3213, 3213, 3212},
								4
						),
						NpcID.GARTH),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.SPIRIT_TREE, new WorldPoint(2800, 3202, 0),
						new Polygon(
								new int[]{2801, 2801, 2803, 2803},
								new int[]{3202, 3204, 3204, 3202},
								4
						),
						NpcID.FARMING_GARDENER_SPIRIT_TREE_3)
		), 11057);

		add(new FarmingRegion("Catherby", 11062, false,
				new FarmingPatch("North", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.ALLOTMENT, new WorldPoint(2814, 3466, 0),
						new Polygon(
								new int[]{2805, 2805, 2814, 2814, 2806, 2806},
								new int[]{3466, 3468, 3468, 3467, 3467, 3466},
								6
						),
						NpcID.DANTAERA, 0),
				new FarmingPatch("South", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.ALLOTMENT, new WorldPoint(2815, 3460, 0),
						new Polygon(
								new int[]{2805, 2805, 2806, 2806, 2814, 2814},
								new int[]{3459, 3461, 3461, 3460, 3460, 3459},
								6
						),
						NpcID.DANTAERA, 1),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_C, PatchImplementation.FLOWER, new WorldPoint(2810, 3465, 0),
						new Polygon(
								new int[]{2809, 2809, 2810, 2810},
								new int[]{3463, 3464, 3464, 3463},
								4
						)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_D, PatchImplementation.HERB, new WorldPoint(2815, 3464, 0),
						new Polygon(
								new int[]{2813, 2813, 2814, 2814},
								new int[]{3463, 3464, 3464, 3463},
								4
						)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_E, PatchImplementation.COMPOST, new WorldPoint(2805, 3464, 0))
		)
		{
			@Override
			public boolean isInBounds(WorldPoint loc)
			{
				if (loc.getX() >= 2816 && loc.getY() < 3456)
				{
					//Upstairs sends different varbits
					return loc.getX() < 2840 && loc.getY() >= 3440 && loc.getPlane() == 0;
				}
				return true;
			}
		}, 11061, 11318, 11317);
		add(new FarmingRegion("Catherby", 11317, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.FRUIT_TREE, new WorldPoint(2860, 3432, 0),
						new Polygon(
								new int[]{2860, 2860, 2861, 2861},
								new int[]{3433, 3434, 3434, 3433},
								4
						),
						NpcID.FARMING_GARDENER_FRUIT_4)
		)
		{
			//The fruit tree patch is always sent when upstairs in 11317
			@Override
			public boolean isInBounds(WorldPoint loc)
			{
				return loc.getX() >= 2840 || loc.getY() < 3440 || loc.getPlane() == 1;
			}
		});

		add(new FarmingRegion("Civitas illa Fortis", 6192, false,
				new FarmingPatch("North", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.ALLOTMENT, new WorldPoint(1586, 3102, 0),
						new Polygon(
								new int[]{1581, 1581, 1585, 1585, 1582, 1582},
								new int[]{3098, 3103, 3103, 3102, 3102, 3098},
								6
						),
						NpcID.FORTIS_GARDENER, 0),
				new FarmingPatch("South", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.ALLOTMENT, new WorldPoint(1591, 3098, 0),
						new Polygon(
								new int[]{1585, 1585, 1589, 1589, 1590, 1590},
								new int[]{3094, 3095, 3095, 3098, 3098, 3094},
								6
						),
						NpcID.FORTIS_GARDENER, 1),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_C, PatchImplementation.FLOWER, new WorldPoint(1587, 3099, 0),
						new Polygon(
								new int[]{1585, 1585, 1586, 1586},
								new int[]{3098, 3099, 3099, 3098},
								4
						)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_D, PatchImplementation.HERB, new WorldPoint(1581, 3094, 0),
						new Polygon(
								new int[]{1581, 1581, 1582, 1582},
								new int[]{3094, 3095, 3095, 3094},
								4
						)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_E, PatchImplementation.COMPOST, new WorldPoint(1587, 3103, 0))
		), 6447, 6448, 6449, 6191, 6193);

		add(new FarmingRegion("Champions' Guild", 12596, true,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.BUSH, new WorldPoint(3181, 3356, 0),
						new Polygon(
								new int[]{3181, 3181, 3182, 3182},
								new int[]{3357, 3358, 3358, 3357},
								4
						),
						NpcID.FARMING_GARDENER_BUSH_1)
		));

		add(new FarmingRegion("Draynor Manor", 12340, false,
				new FarmingPatch("Belladonna", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.BELLADONNA, new WorldPoint(3088, 3355, 0),
						new Polygon(
								new int[]{3086, 3086, 3087, 3087},
								new int[]{3354, 3355, 3355, 3354},
								4
						))
		));

		add(new FarmingRegion("Entrana", 11060, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.HOPS, new WorldPoint(2812, 3334, 0),
						new Polygon(
								new int[]{2809, 2809, 2812, 2812},
								new int[]{3335, 3338, 3338, 3335},
								4
						),
						NpcID.FRANCIS)
		), 11316);

		add(new FarmingRegion("Etceteria", 10300, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.BUSH, new WorldPoint(2592, 3862, 0),
						new Polygon(
								new int[]{2591, 2591, 2592, 2592},
								new int[]{3863, 3864, 3864, 3863},
								4
						),
						NpcID.FARMING_GARDENER_BUSH_3),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.SPIRIT_TREE, new WorldPoint(2613, 3856, 0),
						new Polygon(
								new int[]{2612, 2612, 2614, 2614},
								new int[]{3857, 3859, 3859, 3857},
								4
						),
						NpcID.FARMING_GARDENER_SPIRIT_TREE_2)
		));

		add(new FarmingRegion("Falador", 11828, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.TREE, new WorldPoint(3004, 3371, 0),
						new Polygon(
								new int[]{3003, 3003, 3005, 3005},
								new int[]{3372, 3374, 3374, 3372},
								4
						),
						NpcID.FARMING_GARDENER_TREE_2)
		), 12084);

		add(new FarmingRegion("Falador", 12083, false,
				new FarmingPatch("North West", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.ALLOTMENT, new WorldPoint(3052, 3307, 0),
						new Polygon(
								new int[]{3050, 3050, 3054, 3054, 3051, 3051},
								new int[]{3307, 3312, 3312, 3311, 3311, 3307},
								6
						),
						NpcID.ELSTAN, 0),
				new FarmingPatch("South East", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.ALLOTMENT, new WorldPoint(3055, 3305, 0),
						new Polygon(
								new int[]{3055, 3055, 3058, 3058, 3059, 3059},
								new int[]{3303, 3304, 3304, 3308, 3308, 3303},
								6
						),
						NpcID.ELSTAN, 1),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_C, PatchImplementation.FLOWER, new WorldPoint(3053, 3307, 0),
						new Polygon(
								new int[]{3054, 3054, 3055, 3055},
								new int[]{3307, 3308, 3308, 3307},
								4
						)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_D, PatchImplementation.HERB, new WorldPoint(3057, 3311, 0),
						new Polygon(
								new int[]{3058, 3058, 3059, 3059},
								new int[]{3311, 3312, 3312, 3311},
								4
						)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_E, PatchImplementation.COMPOST, new WorldPoint(3056, 3311, 0))
		)
		{
			@Override
			public boolean isInBounds(WorldPoint loc)
			{
				//Not on region boundary due to Port Sarim Spirit Tree patch
				return loc.getY() >= 3272;
			}
		});

		add(new FarmingRegion("Fossil Island", 14651, false,
				new FarmingPatch("East", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.HARDWOOD_TREE, new WorldPoint(3713, 3835, 0),
						new Polygon(
								new int[]{3714, 3714, 3716, 3716},
								new int[]{3834, 3836, 3836, 3834},
								4
						),
						NpcID.FOSSIL_SQUIRREL_GARDENER1),
				new FarmingPatch("Middle", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.HARDWOOD_TREE, new WorldPoint(3708, 3835, 0),
						new Polygon(
								new int[]{3707, 3707, 3709, 3709},
								new int[]{3832, 3834, 3834, 3832},
								4
						),
						NpcID.FOSSIL_SQUIRREL_GARDENER2),
				new FarmingPatch("West", VarbitID.FARMING_TRANSMIT_C, PatchImplementation.HARDWOOD_TREE, new WorldPoint(3701, 3836, 0),
						new Polygon(
								new int[]{3701, 3701, 3703, 3703},
								new int[]{3836, 3838, 3838, 3836},
								4
						),
						NpcID.FOSSIL_SQUIRREL_GARDENER3)
		)
		{
			@Override
			public boolean isInBounds(WorldPoint loc)
			{
				//Hardwood tree varbits are sent anywhere on plane 0 of fossil island.
				//Varbits get sent 1 tick earlier than expected when climbing certain ladders and stairs

				//Stairs to house on the hill
				if (loc.getX() == 3753 && loc.getY() >= 3868 && loc.getY() <= 3870)
				{
					return false;
				}

				//East and west ladders to rope bridge
				if ((loc.getX() == 3729 || loc.getX() == 3728 || loc.getX() == 3747 || loc.getX() == 3746)
						&& loc.getY() <= 3832 && loc.getY() >= 3830)
				{
					return false;
				}

				return loc.getPlane() == 0;
			}
		}, 14907, 14908, 15164, 14652, 14906, 14650, 15162, 15163);
		add(new FarmingRegion("Seaweed", 15008, false,
				new FarmingPatch("North", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.SEAWEED, new WorldPoint(3733, 10272, 1),
						new Polygon(
								new int[]{3733, 3733, 3734, 3734},
								new int[]{10273, 10274, 10274, 10273},
								4
						),
						NpcID.FOSSIL_GARDENER_UNDERWATER, 0),
				new FarmingPatch("South", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.SEAWEED, new WorldPoint(3733, 10269, 1),
						new Polygon(
								new int[]{3733, 3733, 3734, 3734},
								new int[]{10267, 10268, 10268, 10267},
								4
						),
						NpcID.FOSSIL_GARDENER_UNDERWATER, 1)
		));

		add(new FarmingRegion("Gnome Stronghold", 9781, true,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.TREE, new WorldPoint(2437, 3417, 0),
						new Polygon(
								new int[]{2437, 2437, 2435, 2435},
								new int[]{3416, 3414, 3414, 3416},
								4
						),
						NpcID.FARMING_GARDENER_TREE_GNOME),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.FRUIT_TREE, new WorldPoint(2475, 3447, 0),
						new Polygon(
								new int[]{2475, 2475, 2476, 2476},
								new int[]{3445, 3446, 3446, 3445},
								4
						),
						NpcID.FARMING_GARDENER_FRUIT_1)
		), 9782, 9526, 9525);

		add(new FarmingRegion("Harmony", 15148, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.ALLOTMENT, new WorldPoint(3795, 2838, 0),
						new Polygon(
								new int[]{3794, 3794},
								new int[]{2833, 2838},
								2
						)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.HERB, new WorldPoint(3790, 3839, 0),
						new Polygon(
								new int[]{3789, 3789, 3790, 3790},
								new int[]{2837, 2838, 2838, 2837},
								4
						))
		));

		add(new FarmingRegion("Kourend", 6967, false,
				new FarmingPatch("North East", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.ALLOTMENT, new WorldPoint(1739, 3553, 0),
						new Polygon(
								new int[]{1733, 1733, 1739, 1739, 1738, 1738},
								new int[]{3558, 3559, 3559, 3554, 3554, 3558},
								6
						),
						NpcID.HOSIDIUS_ALLOTMENT_GARDENER, 0),
				new FarmingPatch("South West", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.ALLOTMENT, new WorldPoint(1735, 3552, 0),
						new Polygon(
								new int[]{1730, 1730, 1731, 1731, 1735, 1735},
								new int[]{3550, 3555, 3555, 3551, 3551, 3550},
								6
						),
						NpcID.HOSIDIUS_ALLOTMENT_GARDENER, 1),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_C, PatchImplementation.FLOWER, new WorldPoint(1735, 3553, 0),
						new Polygon(
								new int[]{1734, 1734, 1735, 1734},
								new int[]{3554, 3555, 3555, 1734},
								4
						)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_D, PatchImplementation.HERB, new WorldPoint(1739, 3552, 0),
						new Polygon(
								new int[]{1738, 1738, 1739, 1739},
								new int[]{3550, 3551, 3551, 3550},
								4
						)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_E, PatchImplementation.COMPOST, new WorldPoint(1730, 3557, 0)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_F, PatchImplementation.SPIRIT_TREE, new WorldPoint(1695, 3543, 0),
						new Polygon(
								new int[]{1692, 1692, 1694, 1694},
								new int[]{3541, 3543, 3543, 3541},
								4
						),
						NpcID.FARMING_GARDENER_SPIRIT_TREE_4)
		), 6711);
		add(new FarmingRegion("Kourend", 7223, false,
				new FarmingPatch("East 1", VarbitID.FARMING_TRANSMIT_A1, PatchImplementation.GRAPES, new WorldPoint(-1, -1, 0)),
				new FarmingPatch("East 2", VarbitID.FARMING_TRANSMIT_A2, PatchImplementation.GRAPES, new WorldPoint(-1, -1, 0)),
				new FarmingPatch("East 3", VarbitID.FARMING_TRANSMIT_B1, PatchImplementation.GRAPES, new WorldPoint(-1, -1, 0)),
				new FarmingPatch("East 4", VarbitID.FARMING_TRANSMIT_B2, PatchImplementation.GRAPES, new WorldPoint(-1, -1, 0)),
				new FarmingPatch("East 5", VarbitID.FARMING_TRANSMIT_C1, PatchImplementation.GRAPES, new WorldPoint(-1, -1, 0)),
				new FarmingPatch("East 6", VarbitID.FARMING_TRANSMIT_C2, PatchImplementation.GRAPES, new WorldPoint(-1, -1, 0)),
				new FarmingPatch("West 1", VarbitID.FARMING_TRANSMIT_D1, PatchImplementation.GRAPES, new WorldPoint(-1, -1, 0)),
				new FarmingPatch("West 2", VarbitID.FARMING_TRANSMIT_D2, PatchImplementation.GRAPES, new WorldPoint(-1, -1, 0)),
				new FarmingPatch("West 3", VarbitID.FARMING_TRANSMIT_E1, PatchImplementation.GRAPES, new WorldPoint(-1, -1, 0)),
				new FarmingPatch("West 4", VarbitID.FARMING_TRANSMIT_E2, PatchImplementation.GRAPES, new WorldPoint(-1, -1, 0)),
				new FarmingPatch("West 5", VarbitID.FARMING_TRANSMIT_F1, PatchImplementation.GRAPES, new WorldPoint(-1, -1, 0)),
				new FarmingPatch("West 6", VarbitID.FARMING_TRANSMIT_F2, PatchImplementation.GRAPES, new WorldPoint(-1, -1, 0))
		));

		add(new FarmingRegion("Lletya", 9265, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.FRUIT_TREE, new WorldPoint(2345, 3162, 0),
						new Polygon(
								new int[]{2346, 2346, 2347, 2347},
								new int[]{3161, 3162, 3162, 3161},
								4
						),
						NpcID.FARMING_GARDENER_FRUIT_TREE_5)
		), 11103);

		add(new FarmingRegion("Lumbridge", 12851, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.HOPS, new WorldPoint(3232, 3317, 0),
						new Polygon(
								new int[]{3227, 3227, 3231, 3231},
								new int[]{3313, 3317, 3317, 3313},
								4
						),
						NpcID.FARMING_GARDENER_HOPS_3)
		));
		add(new FarmingRegion("Lumbridge", 12594, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.TREE, new WorldPoint(3195, 3230, 0),
						new Polygon(
								new int[]{3192, 3192, 3194, 3194},
								new int[]{3230, 3232, 3232, 3230},
								4
						),
						NpcID.FARMING_GARDENER_TREE_4)
		), 12850);

		add(new FarmingRegion("Morytania", 13622, false,
				new FarmingPatch("Mushroom", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.MUSHROOM, new WorldPoint(3451, 3474, 0),
						new Polygon(
								new int[]{3451, 3451, 3452, 3452},
								new int[]{3472, 3473, 3473, 3472},
								4
						))
		), 13878);
		add(new FarmingRegion("Morytania", 14391, false,
				new FarmingPatch("North West", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.ALLOTMENT, new WorldPoint(3597, 3524, 0),
						new Polygon(
								new int[]{3597, 3597, 3601, 3601, 3598, 3598},
								new int[]{3525, 3530, 3530, 3529, 3529, 3525},
								6
						),
						NpcID.LYRA, 0),
				new FarmingPatch("South East", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.ALLOTMENT, new WorldPoint(3601, 3522, 0),
						new Polygon(
								new int[]{3602, 3602, 3605, 3605, 3606, 3606},
								new int[]{3521, 3522, 3522, 3526, 3526, 3521},
								6
						),
						NpcID.LYRA, 1),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_C, PatchImplementation.FLOWER, new WorldPoint(3601, 3524, 0),
						new Polygon(
								new int[]{3601, 3601, 3602, 3602},
								new int[]{3225, 3226, 3226, 3225},
								4
						)
				),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_D, PatchImplementation.HERB, new WorldPoint(3605, 3528, 0),
						new Polygon(
								new int[]{3605, 3605, 3606, 3606},
								new int[]{3529, 3530, 3530, 3529},
								4
						)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_E, PatchImplementation.COMPOST, new WorldPoint(3609, 3522, 0))
		), 14390);

		add(new FarmingRegion("Nemus Retreat", 5427, false,
			new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.TREE, new WorldPoint(0, 0, 0),
					new Polygon(
							new int[]{0, 0, 0, 0},
							new int[]{0, 0, 0, 0},
							4
					),
					NpcID.FARMING_GARDENER_TREE_7)
		));

		add(new FarmingRegion("Port Sarim", 12082, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.SPIRIT_TREE, new WorldPoint(3059, 3257, 0),
						new Polygon(
								new int[]{3059, 3059, 3061, 3061},
								new int[]{3257, 3259, 3259, 3257},
								4
						),
						NpcID.FARMING_GARDENER_SPIRIT_TREE_1)
		)
		{
			@Override
			public boolean isInBounds(WorldPoint loc)
			{
				return loc.getY() < 3272;
			}
		}, 12083);

		add(new FarmingRegion("Rimmington", 11570, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.BUSH, new WorldPoint(2940, 3223, 0),
						new Polygon(
								new int[]{2940, 2940, 2941, 2941},
								new int[]{3221, 3222, 3222, 3221},
								4
						),
						NpcID.FARMING_GARDENER_BUSH_2)
		), 11826);

		add(new FarmingRegion("Seers' Village", 10551, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.HOPS, new WorldPoint(2669, 3522, 0),
						new Polygon(
								new int[]{2664, 2664, 2669, 2669},
								new int[]{3523, 3528, 3528, 3523},
								4
						),
						NpcID.FARMING_GARDENER_HOPS_4)
		), 10550);

		add(new FarmingRegion("Tai Bwo Wannai", 11056, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.CALQUAT, new WorldPoint(2796, 3103, 0),
						new Polygon(
								new int[]{2795, 2795, 2797, 2797},
								new int[]{3100, 3102, 3102, 3100},
								4
						),
						NpcID.FARMING_GARDENER_CALQUAT)
		));

		add(new FarmingRegion("Taverley", 11573, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.TREE, new WorldPoint(2936, 3440, 0),
						new Polygon(
								new int[]{2935, 2935, 2937, 2937},
								new int[]{3437, 3439, 3439, 3437},
								4
						),
						NpcID.FARMING_GARDENER_TREE_1)
		), 11829);

		add(new FarmingRegion("Tree Gnome Village", 9777, true,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.FRUIT_TREE, new WorldPoint(2488, 3180, 0),
						new Polygon(
								new int[]{2489, 2489, 2490, 2490},
								new int[]{3179, 3180, 3180, 3179},
								4
						),
						NpcID.FARMING_GARDENER_FRUIT_2)
		), 10033);

		add(new FarmingRegion("Troll Stronghold", 11321, true,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.HERB, new WorldPoint(2827, 3693, 0),
						new Polygon(
								new int[]{2826, 2826, 2827, 2827},
								new int[]{3694, 3695, 3695, 3694},
								4
						))
		));

		add(new FarmingRegion("Varrock", 12854, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.TREE, new WorldPoint(3229, 3457, 0),
						new Polygon(
								new int[]{3228, 3228, 3230, 3230},
								new int[]{3458, 3460, 3460, 3458},
								4
						),
						NpcID.FARMING_GARDENER_TREE_3_02)
		), 12853);

		add(new FarmingRegion("Yanille", 10288, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.HOPS, new WorldPoint(2573, 3106, 0),
						new Polygon(
								new int[]{2574, 2574, 2577, 2577},
								new int[]{3103, 3106, 3106, 3103},
								4
						),
						NpcID.FARMING_GARDENER_HOPS_1)
		));

		add(new FarmingRegion("Weiss", 11325, false,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.HERB, new WorldPoint(2849, 3933, 0),
						new Polygon(
								new int[]{2848, 2848, 2849, 2849},
								new int[]{3934, 3935, 3935, 3934},
								4
						))
		));

		add(new FarmingRegion("Farming Guild", 5021, true,
				new FarmingPatch("Hespori", VarbitID.FARMING_TRANSMIT_J, PatchImplementation.HESPORI, new WorldPoint(1246, 10085, 0),
						new Polygon(
								new int[]{1246, 1248, 1248, 1246},
								new int[]{10086, 10088, 10088, 10086},
								4
						))
		));

		//Full 3x3 region area centered on farming guild
		add(farmingGuildRegion = new FarmingRegion("Farming Guild", 4922, true,
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_G, PatchImplementation.TREE, new WorldPoint(1233, 3734, 0),
						new Polygon(
								new int[]{1231, 1231, 1233, 1233},
								new int[]{3735, 3737, 3737, 3735},
								4
						),
						NpcID.FARMING_GARDENER_FARMGUILD_T2),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_E, PatchImplementation.HERB, new WorldPoint(1238, 3728, 0),
						new Polygon(
								new int[]{1238, 1238, 1239, 1239},
								new int[]{3726, 3727, 3727, 3726},
								4
						)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.BUSH, new WorldPoint(1261, 3732, 0),
						new Polygon(
								new int[]{1260, 1260, 1261, 1261},
								new int[]{3733, 3734, 3734, 3733},
								4
						),
						NpcID.FARMING_GARDENER_FARMGUILD_T1, 3),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_H, PatchImplementation.FLOWER, new WorldPoint(1261, 3727, 0),
						new Polygon(
								new int[]{1260, 1260, 1261, 1261},
								new int[]{3725, 3726, 3726, 3725},
								4
						)),
				new FarmingPatch("North", VarbitID.FARMING_TRANSMIT_C, PatchImplementation.ALLOTMENT, new WorldPoint(1266, 3732, 0),
						new Polygon(
								new int[]{1267, 1267, 1268, 1268, 1272, 1272},
								new int[]{3732, 3736, 3736, 3733, 3733, 3732},
								6
						),
						NpcID.FARMING_GARDENER_FARMGUILD_T1, 1),
				new FarmingPatch("South", VarbitID.FARMING_TRANSMIT_D, PatchImplementation.ALLOTMENT, new WorldPoint(1266, 3727, 0),
						new Polygon(
								new int[]{1267, 1267, 1272, 1272, 1268, 1268},
								new int[]{3723, 3727, 3727, 3726, 3726, 3723},
								6
						),
						NpcID.FARMING_GARDENER_FARMGUILD_T1, 2),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_N, PatchImplementation.BIG_COMPOST, new WorldPoint(1271, 3729, 0)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_F, PatchImplementation.CACTUS, new WorldPoint(1264, 3746, 0),
						new Polygon(
								new int[]{1264, 1264, 1265, 1265},
								new int[]{3747, 3748, 3748, 3747},
								4
						),
						NpcID.FARMING_GARDENER_FARMGUILD_T1, 0),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.SPIRIT_TREE, new WorldPoint(1251, 3749, 0),
						new Polygon(
								new int[]{1252, 1252, 1254, 1254},
								new int[]{3749, 3751, 3751, 3749},
								4
						),
						NpcID.FARMING_GARDENER_SPIRIT_TREE_5),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_K, PatchImplementation.FRUIT_TREE, new WorldPoint(1243, 3757, 0),
						new Polygon(
								new int[]{1242, 1242, 1243, 1243},
								new int[]{3758, 3759, 3758, 3758},
								4
						),
						NpcID.FARMING_GARDENER_FARMGUILD_T3),
				new FarmingPatch("Anima", VarbitID.FARMING_TRANSMIT_M, PatchImplementation.ANIMA, new WorldPoint(1233, 3725, 0),
						new Polygon(
								new int[]{1231, 1231, 1233, 1233},
								new int[]{3722, 3724, 3724, 3722},
								4
						)),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_L, PatchImplementation.CELASTRUS, new WorldPoint(1245, 3752, 0),
						new Polygon(
								new int[]{1243, 1243, 1245, 1245},
								new int[]{3749, 3751, 3751, 3749},
								4
						),
						NpcID.FARMING_GARDENER_FARMGUILD_CELASTRUS),
				new FarmingPatch("", VarbitID.FARMING_TRANSMIT_I, PatchImplementation.REDWOOD, new WorldPoint(1233, 3752, 0),
						new Polygon(
								new int[]{1225, 1225, 1232, 1232},
								new int[]{3751, 3758, 3758, 3751},
								4
						),
						NpcID.FARMING_GARDENER_FARMGUILD_REDWOOD)
		), 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667);

		//All of Prifddinas, and all of Prifddinas Underground
		add(new FarmingRegion("Prifddinas", 13151, false,
						new FarmingPatch("North", VarbitID.FARMING_TRANSMIT_A, PatchImplementation.ALLOTMENT, new WorldPoint(3293, 6105, 0),
								new Polygon(
										new int[]{3288, 3288, 3293, 3293, 3289, 3289},
										new int[]{6101, 6104, 6104, 6103, 6103, 6101},
										6
								),
								NpcID.PRIF_GARDENER, 0),
						new FarmingPatch("South", VarbitID.FARMING_TRANSMIT_B, PatchImplementation.ALLOTMENT, new WorldPoint(3294, 6096, 0),
								new Polygon(
										new int[]{3288, 3288, 3289, 3289, 3293, 3293},
										new int[]{6095, 6098, 6098, 6096, 6096, 6095},
										6
								),
								NpcID.PRIF_GARDENER, 1),
						new FarmingPatch("", VarbitID.FARMING_TRANSMIT_C, PatchImplementation.FLOWER, new WorldPoint(3294, 6100, 0),
								new Polygon(
										new int[]{3292, 3292, 3293, 3293},
										new int[]{6099, 6100, 6100, 6099},
										4
								)),
						new FarmingPatch("", VarbitID.FARMING_TRANSMIT_E, PatchImplementation.CRYSTAL_TREE, new WorldPoint(3291, 6117, 0),
								new Polygon(
										new int[]{3291, 3291, 3292, 3292},
										new int[]{6118, 6119, 6119, 6118},
										4
								)),
						new FarmingPatch("", VarbitID.FARMING_TRANSMIT_D, PatchImplementation.COMPOST, new WorldPoint(3288, 6100, 0))
				), 12895, 12894, 13150,
				/* Underground */ 12994, 12993, 12737, 12738, 12126, 12127, 13250);

		// Finalize
		this.regions = Multimaps.unmodifiableMultimap(this.regions);
		Map<Tab, Set<FarmingPatch>> umtabs = new TreeMap<>();
		for (Map.Entry<Tab, Set<FarmingPatch>> e : tabs.entrySet())
		{
			umtabs.put(e.getKey(), Collections.unmodifiableSet(e.getValue()));
		}
		this.tabs = Collections.unmodifiableMap(umtabs);
	}

	private void add(FarmingRegion r, int... extraRegions)
	{
		regions.put(r.getRegionID(), r);
		for (int er : extraRegions)
		{
			regions.put(er, r);
		}
		for (FarmingPatch p : r.getPatches())
		{
			tabs
					.computeIfAbsent(p.getImplementation().getTab(), k -> new TreeSet<>(tabSorter))
					.add(p);
		}
	}

	Collection<FarmingRegion> getRegionsForLocation(WorldPoint location)
	{
		return this.regions.get(location.getRegionID()).stream()
				.filter(region -> region.isInBounds(location))
				.collect(Collectors.toSet());
	}
}
