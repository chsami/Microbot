package net.runelite.client.plugins.microbot.bans;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum BanReportMode
{
	ASK("Ask me"),
	ALWAYS("Always send"),
	NEVER("Never send");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
