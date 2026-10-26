package net.runelite.client.plugins.microbot.ddchopper;

public enum ChopMode
{
	POWER_CHOP("Power-chop (drop logs)"),
	BANK("Bank logs");

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
