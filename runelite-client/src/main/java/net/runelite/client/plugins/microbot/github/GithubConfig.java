package net.runelite.client.plugins.microbot.github;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GithubConfig.GROUP)
@ConfigInformation(
        "• Loads jar files from github repository."
)
public interface GithubConfig extends Config {
    String GROUP = "GithubPlugin";


    @ConfigItem(
            keyName = "repoUrls",
            name = "My repoUrls",
            description = "Comma-separated list of options"
    )
    default String repoUrls()
    {
        return "https://github.com/chsami/microbot";
    }

}
