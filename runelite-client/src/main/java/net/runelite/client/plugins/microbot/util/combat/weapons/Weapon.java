package net.runelite.client.plugins.microbot.util.combat.weapons;

import lombok.Data;

import java.util.List;

@Data
public class Weapon
{
	int id;
	int range;
	int longRangeModifier = 2;

	public Weapon(int id)
	{
		this.id = id;
	}

	public Weapon(List<Integer> weaponData)
	{
		this.id = weaponData.get(0);
		this.range = weaponData.get(1);
		if (weaponData.size() == 3)
		{
			this.longRangeModifier = weaponData.get(2);
		}
	}

	public int getRange(String attackStyle)
	{
		return attackStyle.equals("Longrange") ? range + longRangeModifier : range;
	}
}