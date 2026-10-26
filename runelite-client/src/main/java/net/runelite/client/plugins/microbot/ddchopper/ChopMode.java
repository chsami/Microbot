package net.runelite.client.plugins.microbot.ddchopper;

/** What to do with a full inventory of logs. POWER_CHOP is the saved-config name of "Drop". */
public enum ChopMode
{
	POWER_CHOP("Drop"),
	BANK("Bank"),
	BURN("Burn");

	private final String label;

	ChopMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
