package net.runelite.client.plugins.microbot.winecollector;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;

@ConfigGroup("winecollector")
@ConfigInformation(
        "<h2>Wine Collector</h2>" +
                "<h3>Version: 1.0.0</h3>" +
                "<p><strong>Start Location:</strong> Start the plugin inside <em>Guildmaster Apatura's quarters</em> — top floor of the Hunter's Guild, near the Eclipse Red wine.</p>" +
                "<p><strong>Requirements:</strong> Must have completed <em>Children of the Sun</em>.</p>" +
                "<p><strong>Profit:</strong> Around <em>240k gp/hr</em> automatically looting Eclipse Red wine.</p>" +
                "<p><strong>Ironmen:</strong> The wine can be sold at <em>Moonrise Wines</em> in <strong>Aldarin</strong>.</p>" +
                "<p><strong>Others:</strong> You can also sell it at the <strong>Grand Exchange</strong>.</p>"
)
public interface WineCollectorConfig extends Config {
    // No options required — the plugin is toggled via the plugin panel
}
