package net.runelite.client.plugins.microbot.anonymous;

import net.runelite.client.config.*;

@ConfigGroup("anonymous")
@ConfigInformation("<b>-- ANONYMOUS MODE --</b><br /><br />" +
        "This plugin helps to mask and change certain visual elements of the game interface, making it harder for you character to be recognized.<br /><br />" +
        "This can be useful when you want to record or take a screenshot for debugging purposes or to share it with others without exposing 'sensitive' information.<br /><br />" +
        "You can select which elements to mask and change in the settings below.<br /><br/>" +
        "<b>Note:</b> This plugin may affect your FPS if your machine is not powerful.")
public interface AnonymousConfig extends Config {
    @ConfigSection(
            name = "Masking",
            description = "Which elements to mask in the client.",
            position = 0
    )
    String maskingSection = "masking";

    @ConfigItem(
            keyName = "maskCharacterName",
            name = "Character Name",
            description = "Replace character name in the chat with 'username'.",
            position = 0,
            section = maskingSection
    )
    default boolean maskCharacterName() {
        return true;
    }

    @ConfigItem(
            keyName = "maskTitleName",
            name = "Character Name (Title)",
            description = "Remove the character name from the window title.",
            position = 1,
            section = maskingSection
    )
    default boolean maskTitleName() {
        return true;
    }

    @ConfigItem(
            keyName = "maskHPGlobe",
            name = "HP Globe",
            description = "Mask the HP globe with 99.",
            position = 2,
            section = maskingSection
    )
    default boolean maskHPGlobe() {
        return true;
    }

    @ConfigItem(
            keyName = "maskPrayerGlobe",
            name = "HP Globe",
            description = "Mask the HP globe with 99.",
            position = 3,
            section = maskingSection
    )
    default boolean maskPrayerGlobe() {
        return true;
    }

    @ConfigItem(
            keyName = "maskCharacterVisual",
            name = "Character Visual",
            description = "Mask the character visual by removing all items and making it naked.",
            position = 4,
            section = maskingSection
    )
    default boolean maskCharacterVisual() {
        return true;
    }

    @ConfigItem(
            keyName = "maskSkillLevels",
            name = "Skill Levels",
            description = "Mask skill levels with 99s.",
            position = 5,
            section = maskingSection
    )
    default boolean maskSkillLevels() {
        return true;
    }

    @ConfigItem(
            keyName = "maskSkillsTooltips",
            name = "Skill Tooltips",
            description = "Mask skill/total level tooltips with 2147M.",
            position = 6,
            section = maskingSection
    )
    default boolean maskSkillLevelTooltips() {
        return true;
    }

    @ConfigItem(
            keyName = "maskCombatLevel",
            name = "Combat Level",
            description = "Mask the combat level to 126.",
            position = 7,
            section = maskingSection
    )
    default boolean maskCombatLevel() {
        return true;
    }

    @ConfigItem(
            keyName = "maskInventoryItemQuantity",
            name = "Inventory Items Quantity",
            description = "Mask the quantity of items to 2147M.",
            position = 8,
            section = maskingSection
    )
    default boolean maskInventoryItemQuantity() {
        return true;
    }

    @ConfigItem(
            keyName = "maskBankItemQuantity",
            name = "Bank Items Quantity",
            description = "Mask the quantity of items inside the bank stash to 2147M.",
            position = 9,
            section = maskingSection
    )
    default boolean maskBankItemQuantity() {
        return true;
    }

    @ConfigItem(
            keyName = "maskXpDropsCounter",
            name = "XP Drops Counter",
            description = "Mask the XP drops counter to 2B.",
            position = 10,
            section = maskingSection
    )
    default boolean maskXpDropsCounter() {
        return true;
    }


}
