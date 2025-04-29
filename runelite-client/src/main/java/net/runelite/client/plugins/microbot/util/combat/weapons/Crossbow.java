package net.runelite.client.plugins.microbot.util.combat.weapons;

import java.util.List;

public class Crossbow extends Weapon
{
	public Crossbow(int id)
	{
		super(id);
		range = 7;
	}

	public Crossbow(List<Integer> weaponData)
	{
		super(weaponData);
	}
}
