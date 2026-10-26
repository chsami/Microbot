package net.runelite.client.plugins.microbot.ddchopper;

/** Config dropdown: best usable axe, or one specific axe. */
public enum AxeChoice
{
	AUTO("Auto (best available)", null),
	BRONZE("Bronze", Axe.BRONZE),
	IRON("Iron", Axe.IRON),
	STEEL("Steel", Axe.STEEL),
	BLACK("Black", Axe.BLACK),
	MITHRIL("Mithril", Axe.MITHRIL),
	ADAMANT("Adamant", Axe.ADAMANT),
	RUNE("Rune", Axe.RUNE),
	DRAGON("Dragon", Axe.DRAGON),
	CRYSTAL("Crystal", Axe.CRYSTAL);

	private final String label;
	private final Axe axe;

	AxeChoice(String label, Axe axe)
	{
		this.label = label;
		this.axe = axe;
	}

	/** The specific axe, or null for {@link #AUTO}. */
	public Axe axe()
	{
		return axe;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
