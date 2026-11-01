package net.runelite.client.plugins.microbot.ui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.microbot.ui.MicrobotTourPlacement.Side;

@Getter
@RequiredArgsConstructor
enum MicrobotTourStep
{
	WELCOME("Welcome to Microbot",
		"Microbot is the RuneLite client you already know, with Microbot plugins added that can automate parts of the game for you. "
			+ "This short tour shows where everything is. You can skip it now and replay it later from the red bug button.",
		false, false, Side.CENTER),
	SIDEBAR("The RuneLite sidebar",
		"Each icon in this strip opens a panel. Your regular RuneLite plugins are under the wrench, as in normal RuneLite. "
			+ "Microbot adds its own button at the top.",
		false, true, Side.LEFT),
	COMMUNITY_PLUGINS("Community Plugins",
		"This is the Microbot button. Click it at any time to open your Microbot plugins, the Plugin Hub and Microbot Cloud.",
		false, true, Side.LEFT),
	INSTALLED("Your installed plugins",
		"Your Microbot plugins live here. Flip a plugin's switch to start or stop it, click the gear to change its settings, "
			+ "and click the star to pin it to the top. The search bar finds plugins by name or tag.",
		false, true, Side.LEFT),
	PLUGIN_HUB("Plugin Hub",
		"Browse the Microbot Plugin Hub to find and install more plugins. Each one you install appears under Installed.",
		false, true, Side.LEFT),
	PROFILES("Profiles",
		"A profile is one account: its name is the login username, and it holds the password, bank PIN, member status, world "
			+ "and Discord webhook. Plugin settings are saved per profile. Scripts use the active profile to log in and enter the bank PIN.",
		false, true, Side.LEFT),
	ANTIBAN("Antiban",
		"Antiban makes your bot act more like a person: breaks, micro breaks, natural mouse movement and varied timing. "
			+ "Open this panel to tune it. Scripts that support antiban use these settings while they run.",
		false, true, Side.LEFT),
	INVENTORY_SETUPS("Inventory Setups",
		"Save the gear and items a script should bring as an inventory setup. Some scripts, like AIO Fighter, Barrows and "
			+ "Herb Run, let you pick a setup in their settings and load it from the bank.",
		false, true, Side.LEFT),
	MICROBOT_CLOUD("Microbot Cloud",
		"New: get a Discord or email alert when this client stops responding, a script stops on an error or the game logs out. "
			+ "Bot alerts shows whether they are on and lets you link your Microbot account. Open the dashboard to see your bots.",
		true, true, Side.LEFT),
	REPORT_PROBLEM("Report a problem",
		"Script stuck or client acting up? Click the red bug button, choose Report a problem, say what you expected and what happened, "
			+ "then Send. Your password is never sent. An orange dot means news under My reports, like a fix.",
		false, true, Side.LEFT),
	HELP("Need help?",
		"The red bug button can also start Microbot once in safe mode and links to Discord. "
			+ "You can replay this tour from it too. That's it, have fun!",
		false, true, Side.LEFT);

	private final String title;
	private final String body;
	private final boolean isNew;
	private final boolean requiresTarget;
	private final Side preferredSide;
}
