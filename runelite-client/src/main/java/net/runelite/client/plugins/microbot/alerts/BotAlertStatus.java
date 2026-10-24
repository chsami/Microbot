package net.runelite.client.plugins.microbot.alerts;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BotAlertStatus
{
	TELEMETRY_OFF("Off, telemetry is disabled", "Turn on telemetry"),
	LINKING("Waiting for approval", "Show code"),
	LINKED("On", "Manage"),
	LINKED_BY_LAUNCHER("On, linked by the launcher", "Manage"),
	REVOKED("Off, link was removed", "Link again"),
	NOT_LINKED("Not linked", "Link to Microbot account");

	private final String text;
	private final String action;

	public boolean isOn()
	{
		return this == LINKED || this == LINKED_BY_LAUNCHER;
	}
}
