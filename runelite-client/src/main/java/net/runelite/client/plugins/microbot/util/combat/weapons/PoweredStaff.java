package net.runelite.client.plugins.microbot.util.combat.weapons;

import java.util.List;

public class PoweredStaff extends Weapon
{
	public PoweredStaff(int id)
	{
		super(id);
		range = 7;
	}

	public PoweredStaff(List<Integer> weaponData)
	{
		super(weaponData);
	}


	public int getRange(String attackStyle)
	{
		return attackStyle.equals("Defensive") ? range + longRangeModifier : range;
	}
}
