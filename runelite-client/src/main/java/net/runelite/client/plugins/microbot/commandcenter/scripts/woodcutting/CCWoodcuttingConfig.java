package net.runelite.client.plugins.microbot.commandcenter.scripts.woodcutting;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("cc-woodcutting")
public interface CCWoodcuttingConfig extends Config {
    @ConfigSection(name = "General", description = "General settings", position = 0)
    String generalSection = "general";

    @ConfigItem(keyName = "tree", name = "Tree Type", description = "Which tree to chop",
        position = 0, section = generalSection)
    default WoodcuttingTree tree() { return WoodcuttingTree.TREE; }

    @ConfigItem(keyName = "bankLogs", name = "Bank Logs", description = "Bank logs instead of dropping",
        position = 1, section = generalSection)
    default boolean bankLogs() { return true; }
}
