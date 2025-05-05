package net.runelite.client.plugins.microbot.util.combat.weapons;

import java.util.List;

public class Melee extends Weapon
{
	public Melee(List<Integer> weaponData) { super(weaponData); }

	public Integer getSpecialAttackRange() { return this.getRange() + this.getLongRangeModifier(); }
}
