package net.runelite.client.plugins.microbot.bga.autoherblore;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.bga.autoherblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.bga.autoherblore.enums.Mode;

@ConfigGroup("AutoHerblore")
public interface AutoHerbloreConfig extends Config {
    @ConfigSection(
        name = "Mode",
        description = "Select the herblore mode",
        position = 0
    )
    String MODE_SECTION = "mode";

    @ConfigItem(
        keyName = "mode",
        name = "Mode",
        description = "Select mode",
        section = MODE_SECTION
    )
    default Mode mode() { return Mode.CLEAN_HERBS; }

    @ConfigSection(
        name = "Finished Potion Type",
        description = "Select which finished potion to create",
        position = 1
    )
    String POTION_SECTION = "potion";

    @ConfigItem(
        keyName = "potion",
        name = "Potion",
        description = "Select potion",
        section = POTION_SECTION
    )
    default HerblorePotion potion() { return HerblorePotion.ATTACK; }
}

