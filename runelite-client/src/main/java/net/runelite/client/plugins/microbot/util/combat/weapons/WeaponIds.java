package net.runelite.client.plugins.microbot.util.combat.weapons;

import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Getter
public class WeaponIds
{
	// Standard Weapons
	// Weapons that are the same type and share a common range
	// Groups mostly determined from what is listed on the wiki
	private static final Set<Integer> BALLISTAE = Set.of(
		19478,
		19481,
		23630,
		26712,
		27188
	);
	private static final Set<Integer> BLOWPIPES = Set.of(
		12926,
		28688
	);
	private static final Set<Integer> CROSSBOWS = Set.of(
		9174,
		9176,
		9177,
		9179,
		9181,
		9183,
		9185,
		21902,
		23601,
		25916,
		25918,
		26486,
		28053,
		21012
	);
	private static final Set<Integer> SHORTBOWS = Set.of(
		841,
		843,
		849,
		853,
		857,
		861,
		12788,
		20401,
		20403,
		20558,
		28794,
		22333,
		28555,
		23357,
		9705);
	private static final Set<Integer> LONGBOWS = Set.of(
		839,
		845,
		847,
		851,
		855,
		859,
		2883,
		11235, // Dark bow variants
		12765,
		12766,
		12767,
		12768,
		20408,
		27853,
		29599,
		29611);
	private static final Set<Integer> DARTS = Set.of(
		806, // Smithable darts and p variants
		807,
		808,
		809,
		810,
		811,
		812,
		813,
		814,
		815,
		816,
		817,
		818,
		3093, // Black darts and p
		3094,
		5628, // Smithable p+ and p++
		5629,
		5630,
		5631,
		5632,
		5633,
		5634,
		5635,
		5636,
		5637,
		5638,
		5639,
		5640,
		5641,
		11230, // Dragon
		11231,
		11233,
		11234,
		25849,
		25851,
		25855,
		25857);
	private static final Set<Integer> KNIVES = Set.of(
		863,
		864,
		865,
		866,
		867,
		868,
		869,
		870,
		871,
		872,
		873,
		874,
		875,
		876,
		5654,
		5655,
		5656,
		5657,
		5658,
		5659,
		5660,
		5661,
		5662,
		5663,
		5664,
		5665,
		5667,
		22804,
		22806,
		22808,
		22810);
	private static final Set<Integer> THROWNAXES = Set.of(
		800,
		801,
		802,
		803,
		804,
		805,
		20849,
		21207,
		22634,
		27912,
		27914);
	private static final Set<Integer> CHINCHOMPAS = Set.of(10033, 10034, 11959);
	private static final Set<Integer> COMP_BOWS = Set.of(
		10280,
		10282,
		10284,
		23983,
		23985,
		23901, // Gauntlet
		23902,
		23903,
		23855, // Corrupted Gauntlet
		23856,
		23857,
		25862, // Bowfa variants
		25865,
		25867,
		25869,
		25884,
		25886,
		25888,
		25890,
		25892,
		25894,
		25896,
		27187,
		20997, // Twisted bows
		28540
	);
	private static final Set<Integer> POWERED_STAVES = Set.of(
		11905,
		11907,
		11908,
		12899,
		12900,
		22288,
		22290,
		22292,
		22294,
		28583,
		28585,
		22552,
		22555,
		27662,
		27665,
		22323,
		22481,
		25731,
		25733,
		22516
	);
	private static final Set<Integer> STAVES = Set.of(
		1381,
		1383,
		1385,
		1387,
		1391,
		1393,
		1395,
		1397,
		1401,
		1403,
		1405,
		1407,
		3054,
		6563,
		11789,
		12000,
		12796,
		20733,
		20739,
		21200,
		11399,
		3053,
		6562,
		11787,
		11998,
		12795,
		20730,
		20736,
		21198,
		11791,
		12904,
		22296,
		23613,
		24144,
		28988,
		10440,
		10442,
		10444,
		12199,
		12263,
		12275,
		4170,
		21255,
		2415,
		2416,
		2417,
		1409,
		12658,
		29594,
		24422,
		24423,
		24424,
		24425,
		25517,
		29602,
		29609,
		4710,
		4862,
		4863,
		4864,
		4865,
		23653,
		4675,
		20431,
		25489,
		25490,
		25491,
		25492,
		27624,
		27626,
		28260,
		28262,
		28264,
		28266,
		28473,
		28474,
		28475,
		28476,
		27785, // Thammaron's/Accursed sceptre (a) variants act more like regular staves
		27788,
		27676,
		27679,
		7639, // Rod of Ivandis
		7640,
		7641,
		7642,
		7643,
		7644,
		7645,
		7646,
		7647,
		7648,
		22398, // Ivandis flail
		24699, // Blisterwood flail
		30634 // Twinflame staff
	);
	private static final Set<Integer> WANDS = Set.of(
		6908,
		6910,
		6912,
		6914,
		12422,
		20553,
		20556,
		20560,
		21006,
		23626,
		30070 // Dragon hunter wand
	);
	private static final Set<Integer> HALBERDS = Set.of(
		1413,
		3190,
		3192,
		3194,
		3196,
		3198,
		3200,
		3204,
		6599,
		23849, // Corrupted Gauntlet Halberd
		23850,
		23851,
		23895, // Gauntlet Halberd
		23896,
		23897,
		23987,
		23989,
		24125,
		28049,
		29796 // Noxious halberd
	);
	// Non standard Weapons
	// Weapons that have different values than the typical weapon in that class or where there is no standard
	// values in List are [id, range, [longRangeModifier]]
	// Modifier only included if it is not the standard value of 2
	private static final Set<List<Integer>> NON_STANDARD_CROSSBOWS = Set.of(
		List.of(837, 10, 0), // Crossbow
		List.of(767, 5), // Phoenix crossbow
		List.of(11165, 5), // Phoenix crossbow
		List.of(11167, 5), // Phoenix crossbow
		List.of(8880, 6), // Dorgeshuun crossbow
		List.of(10156, 8), // Hunters' crossbow
		List.of(28869, 8), // Hunters' sunlight crossbow
		List.of(4734, 8), // Karil's crossbow
		List.of(4934, 8), // Karil's crossbow
		List.of(4935, 8), // Karil's crossbow
		List.of(4936, 8), // Karil's crossbow
		List.of(4937, 8), // Karil's crossbow
		List.of(23611, 8), // Armadyl crossbow
		List.of(11785, 8), // Armadyl crossbow
		List.of(26374, 8), // Zaryte crossbow
		List.of(27186, 8) // Zaryte crossbow
	);
	private static final Set<List<Integer>> NON_STANDARD_BOWS = Set.of(
		List.of(11708, 6), // Cursed goblin bow
		List.of(22547, 9, 1), // Craw's bow
		List.of(22550, 9, 1), // Craw's bow
		List.of(27652, 9, 1), // Webweaver bow
		List.of(27655, 9, 1), // Webweaver bow
		List.of(4827, 5), // Comp ogre bow
		List.of(29591, 10, 0), // Scorching bow
		List.of(27612, 6), // Venator bow
		List.of(27610, 6) // Venator bow
	);
	private static final Set<List<Integer>> NON_STANDARD_POWERED_STAVES = Set.of(
		List.of(22335, 6), // Starter staff
		List.of(28557, 6), // Starter Staff
		List.of(28796, 8), // Bone staff
		List.of(27275, 8), // Tumeken's shadow
		List.of(27277, 8), // Tumeken's shadow
		List.of(28547, 8), // Tumeken's shadow
		List.of(28549, 8), // Tumeken's shadow
		List.of(23898, 10, 0), // Crystal Staff
		List.of(23899, 10, 0), // Crystal Staff
		List.of(23900, 10, 0), // Crystal Staff
		List.of(23852, 10, 0), // Corrupted Staff
		List.of(23853, 10, 0), // Corrupted Staff
		List.of(23854, 10, 0) // Corrupted Staff
	);
	private static final Set<List<Integer>> NON_STANDARD_THROWN = Set.of(
		List.of(6522, 7), // Toktz-xil-ul
		List.of(29000, 6), // Eclipse atlatl
		List.of(28919, 5), // Tonalztics of ralos
		List.of(28922, 5), // Tonalztics of ralos
		List.of(30390, 7, 0), // Nature's reprisal
		List.of(30392, 7, 0), // Nature's reprisal
		List.of(30373, 6), // Drygore blowpipe
		List.of(30374, 6) // Drygore blowpipe
	);
	private static final Set<List<Integer>> NON_STANDARD_MELEE = Set.of(
		List.of(21015, 1, 4) // Dinh's Bulwark
	);

	public final Set<Pair<Set<Integer>, Function<Integer, Weapon>>> standardWeapons = new HashSet<>();
	public final Set<Pair<Set<List<Integer>>, Function<List<Integer>, Weapon>>> nonStandardWeapons = new HashSet<>();

	public WeaponIds()
	{
		standardWeapons.add(Pair.of(SHORTBOWS, Shortbow::new));
		standardWeapons.add(Pair.of(LONGBOWS, Longbow::new));
		standardWeapons.add(Pair.of(DARTS, Dart::new));
		standardWeapons.add(Pair.of(KNIVES, Knife::new));
		standardWeapons.add(Pair.of(THROWNAXES, Thrownaxe::new));
		standardWeapons.add(Pair.of(CHINCHOMPAS, Chinchompa::new));
		standardWeapons.add(Pair.of(COMP_BOWS, Compbow::new));
		standardWeapons.add(Pair.of(CROSSBOWS, Crossbow::new));
		standardWeapons.add(Pair.of(BALLISTAE, Ballista::new));
		standardWeapons.add(Pair.of(BLOWPIPES, Blowpipe::new));
		standardWeapons.add(Pair.of(POWERED_STAVES, PoweredStaff::new));
		standardWeapons.add(Pair.of(STAVES, Staff::new));
		standardWeapons.add(Pair.of(WANDS, Wand::new));
		standardWeapons.add(Pair.of(HALBERDS, Halberd::new));

		nonStandardWeapons.add(Pair.of(NON_STANDARD_CROSSBOWS, Crossbow::new));
		nonStandardWeapons.add(Pair.of(NON_STANDARD_POWERED_STAVES, PoweredStaff::new));
		nonStandardWeapons.add(Pair.of(NON_STANDARD_BOWS, Bow::new));
		nonStandardWeapons.add(Pair.of(NON_STANDARD_THROWN, GenericThrown::new));
		nonStandardWeapons.add(Pair.of(NON_STANDARD_MELEE, Melee::new));
	}

}
