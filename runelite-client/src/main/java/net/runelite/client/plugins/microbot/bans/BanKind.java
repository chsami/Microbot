package net.runelite.client.plugins.microbot.bans;

import lombok.AllArgsConstructor;

@AllArgsConstructor
enum BanKind
{
	PERMANENT("permanent", "Permanent ban"),
	TEMPORARY("temporary", "Temporary ban"),
	UNKNOWN("unknown", "I don't know yet"),
	NOT_A_BAN(null, "Not a ban (locked, or something else)");

	final String value;
	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
