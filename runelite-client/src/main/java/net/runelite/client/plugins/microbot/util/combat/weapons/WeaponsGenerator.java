package net.runelite.client.plugins.microbot.util.combat.weapons;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Slf4j
@Getter
public class WeaponsGenerator
{
	private static final WeaponIds weaponIds = new WeaponIds();

	public static Map<Integer, Weapon> generate()
	{
		final Map<Integer, Weapon> standardWeapons = generateStandard(weaponIds.getStandardWeapons());
		final Map<Integer, Weapon> nonStandardWeapons = generateNonstandard(weaponIds.getNonStandardWeapons());
		final Map<Integer, Weapon> weapons = new HashMap<>();
		weapons.putAll(standardWeapons);
		weapons.putAll(nonStandardWeapons);
		return weapons;
	}

	private static Map<Integer, Weapon> generateStandard(
		Set<Pair<Set<Integer>, Function<Integer, Weapon>>> standardWeapons)
	{
		final Map<Integer, Weapon> weapons = new HashMap<>();
		for (Pair<Set<Integer>, Function<Integer, Weapon>> idsConstructorPair : standardWeapons)
		{
			for (Integer weaponId : idsConstructorPair.getLeft())
			{
				Weapon weapon = idsConstructorPair.getRight().apply(weaponId);
				if (weapon != null)
				{
					if (weapons.containsKey(weapon.id))
					{
						log.debug("Duplicate weapon entry.");
						log.debug("Trying to insert {}, Existing {}", weapon, weapons.get(weapon.id));
					}
					weapons.put(weapon.id, weapon);
				}
			}
		}
		return weapons;
	}

	private static Map<Integer, Weapon> generateNonstandard(
		Set<Pair<Set<List<Integer>>, Function<List<Integer>, Weapon>>> nonStandardWeapons)
	{
		final Map<Integer, Weapon> weapons = new HashMap<>();
		for (Pair<Set<List<Integer>>, Function<List<Integer>, Weapon>> idsConstructorPair : nonStandardWeapons)
		{
			for (List<Integer> weaponData : idsConstructorPair.getLeft())
			{
				Weapon weapon = idsConstructorPair.getRight().apply(weaponData);
				if (weapon != null)
				{
					weapons.put(weapon.id, weapon);
				}
			}
		}
		return weapons;
	}
}
