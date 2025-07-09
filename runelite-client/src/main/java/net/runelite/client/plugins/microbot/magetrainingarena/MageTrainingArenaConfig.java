package net.runelite.client.plugins.microbot.magetrainingarena;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.magetrainingarena.enums.*;

@ConfigGroup("mta")
@ConfigInformation("- Enable the official RuneLite plugin 'Mage Training Arena'<br />" +
        "  <br />" +
        "- Configure staves and tomes, make sure you can equip them.<br />" +
        "- Staves, Tomes, Laws, Cosmic and Nature runes in inventory only, No rune pouch! <br />" +
        "  <br />" +
        "- T6 enchant requires Lava staff OR Tome of Fire and any Earth staff. <br />" +
        "- T5 enchant requires Tome of Water and any Earth staff, OR either water/earth runes. <br />"+
        "  <br />" +
        "- When set to buy rewards, the rooms are cycled until the points are met, the reward will be stored in your bank. <br />" +
        "- If not set to buy rewards, the rooms are cycled as if it would buy the rewards then continues cycling the rooms afterwards. <br />" +
        "  <br />" +
        "- 'All items' will get enough points for you to finish Collection Log'")
public interface MageTrainingArenaConfig extends Config {
    @ConfigSection(
            name = "Rewards",
            description = "Rewards",
            position = 2
    )
    String rewardsSection = "rewards";

    @ConfigSection(
            name = "Graveyard",
            description = "Graveyard",
            position = 3,
            closedByDefault = true
    )
    String graveyardSection = "graveyard";

    @ConfigItem(
            keyName = "Buy rewards",
            name = "Buy rewards",
            description = "Determines whether the bot should buy the selected reward.",
            position = 1,
            section = rewardsSection
    )
    default boolean buyRewards() {
        return true;
    }

    @ConfigItem(
            keyName = "Reward",
            name = "Reward",
            description = "The reward to aim for.",
            position = 2,
            section = rewardsSection
    )
    default Rewards reward() {
        return Rewards.BONES_TO_PEACHES;
    }

    @ConfigItem(
            keyName = "Healing threshold (min)",
            name = "Healing threshold (min)",
            description = "Each time the bot eats it chooses a random threshold (between min and max value) to eat at next time.",
            position = 3,
            section = graveyardSection
    )
    default int healingThresholdMin() {
        return 40;
    }

    @ConfigItem(
            keyName = "Healing threshold (max)",
            name = "Healing threshold (max)",
            description = "Each time the bot eats it chooses a random threshold (between min and max value) to eat at next time.",
            position = 4,
            section = graveyardSection
    )
    default int healingThresholdMax() {
        return 70;
    }
}
