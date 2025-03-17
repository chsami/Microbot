package net.runelite.client.plugins.microbot.nateplugins.skilling.natefishing;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.nateplugins.skilling.natefishing.enums.Fish;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;

@ConfigGroup(AutoFishConfig.configGroup)
public interface AutoFishConfig extends Config {
    
    String configGroup = "micro-fishing";
    @ConfigSection(
            name = "General",
            description = "General",
            position = 0
    )
    String generalSection = "general";
    
    @ConfigSection(
            name = "Banking",
            description = "Bank Configuration",
            position = 1
    )
    String bankingSection = "banking";

    @ConfigItem(
            keyName = "Fish",
            name = "Fish",
            description = "Choose the fish",
            position = 0,
            section = generalSection
    )
    default Fish fish()
    {
        return Fish.SHRIMP;
    }

    @ConfigItem(
            name = "DropOrder",
            keyName = "dropOrder",
            position = 1,
            description = "The order in which to drop items",
            section = generalSection
    )
    default InteractOrder getDropOrder() {
        return InteractOrder.STANDARD;
    }

    @ConfigItem(
            keyName = "UseBank",
            name = "UseBank",
            description = "Use bank and walk back to original location",
            position = 0,
            section = bankingSection
    )
    default boolean useBank()
    {
        return false;
    }

    @ConfigItem(
            keyName = "useDepositBox",
            name = "Use DepositBox",
            description = "Use depositbox and walk back to original location",
            position = 1,
            section = bankingSection
    )
    default boolean useDepositBox()
    {
        return false;
    }

    @ConfigItem(
            keyName = "bankClueBottles",
            name = "Bank Clue Bottles",
            description = "Should bank clue bottles",
            position = 2,
            section = bankingSection
    )
    default boolean shouldBankClueBottles() {
        return true;
    }

    @ConfigItem(
            keyName = "bankCaskets",
            name = "Bank Caskets",
            description = "Should bank caskets",
            position = 3,
            section = bankingSection
    )
    default boolean shouldBankCaskets() {
        return true;
    }

    // boolean if to use Echo harpoon
    @ConfigItem(
            keyName = "UseEchoHarpoon",
            name = "Echo Harpoon",
            description = "Use Echo Harpoon",
            position = 2,
            section = generalSection
    )
    default boolean useEchoHarpoon()
    {
        return false;
    }

}
