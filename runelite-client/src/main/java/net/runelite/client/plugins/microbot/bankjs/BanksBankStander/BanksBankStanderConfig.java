package net.runelite.client.plugins.microbot.bankjs.BanksBankStander;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;

@ConfigGroup("BankStander")
@ConfigInformation("This Script will perform bank standing activities. <br /> "
        + "<ul>" +
        "<li>Crafting</li>" +
        "<li>Herblore</li>" +
        "<li>Fletching</li>" +
        "<li>Cooking<li>" +
        "</ul>")
public interface BanksBankStanderConfig extends Config {
    @ConfigSection(
            name = "Item Settings",
            description = "Set Items to Combine",
            position = 1,
            closedByDefault = false
    )
    String itemSection = "itemSection";
    @ConfigSection(
            name = "Toggles",
            description = "Change plugin behaviour",
            position = 2,
            closedByDefault = false
    )
    String toggles = "toggles";
    @ConfigSection(
            name = "Interaction Menu",
            description = "Change the interaction menu; e.g. clean grimy herbs",
            position = 3,
            closedByDefault = true
    )
    String interaction = "interaction";
    @ConfigSection(
            name = "Sleep Settings",
            description = "Set Sleep Settings",
            position = 4,
            closedByDefault = false
    )
    String sleepSection = "sleepSection";
    // Items
    @ConfigItem(
            keyName = "interactOrder",
            name = "Interact Order",
            description = "The order in which to interact with items",
            position = 0,
            section = itemSection
    )
    default InteractOrder interactOrder() {
        return InteractOrder.STANDARD;
    }
    @ConfigItem(
            keyName = "First Item",
            name = "First Item",
            description = "Sets First Item, use either Item ID or Item Name",
            position = 1,
            section = itemSection
    )

    default String firstItemIdentifier() {
        return "Knife";
    }

    @ConfigItem(
            keyName = "First Item Quantity",
            name = "First Item Quantity",
            description = "Sets First Item's Quantity.",
            position = 2,
            section = itemSection
    )
    @Range(
            min = 1,
            max = 28
    )

    default int firstItemQuantity() {
        return 1;
    }

    @ConfigItem(
            keyName = "Second Item",
            name = "Second Item",
            description = "Sets Second Item, use either Item ID or Item Name",
            position = 3,
            section = itemSection
    )

    default String secondItemIdentifier() {
        return "Logs";
    }

    @ConfigItem(
            keyName = "Second Item Quantity",
            name = "Second Item Quantity",
            description = "Sets Second Item's Quantity.",
            position = 4,
            section = itemSection
    )
    @Range(
            min = 0,
            max = 27
    )

    default int secondItemQuantity() {
        return 27;
    }
    @ConfigItem(
            keyName = "Third Item",
            name = "Third Item",
            description = "Sets Third Item, use either Item ID or Item Name",
            position = 5,
            section = itemSection
    )

    default String thirdItemIdentifier() {
        return "";
    }

    @ConfigItem(
            keyName = "Third Item Quantity",
            name = "Third Item Quantity",
            description = "Sets Third Item's Quantity.",
            position = 6,
            section = itemSection
    )
    @Range(
            min = 0,
            max = 27
    )

    default int thirdItemQuantity() {
        return 0;
    }

    @ConfigItem(
            keyName = "Fourth Item",
            name = "Fourth Item",
            description = "Sets Fourth Item, use either Item ID or Item Name",
            position = 7,
            section = itemSection
    )

    default String fourthItemIdentifier() {
        return "";
    }

    @ConfigItem(
            keyName = "Fourth Item Quantity",
            name = "Fourth Item Quantity",
            description = "Sets Fourth Item's Quantity.",
            position = 8,
            section = itemSection
    )
    @Range(
            min = 0,
            max = 27
    )

    default int fourthItemQuantity() {
        return 0;
    }

    @ConfigItem(
            keyName = "pause",
            name = "Pause",
            description = "Pause the script? will pause between states",
            position = 1,
            section = toggles
    )
    default boolean pause() {
        return false;
    }

    @ConfigItem(
            keyName = "Prompt",
            name = "Prompt?",
            description = "Does this combination need to respond to a prompt?",
            position = 2,
            section = toggles
    )
    default boolean needPromptEntry() {
        return true;
    }
    @ConfigItem(
            keyName = "WaitForProcess",
            name = "Wait for process?",
            description = "Does this combination need to wait for animation? ie. wait for inventory to process.",
            position = 5,
            section = toggles
    )
    default boolean waitForAnimation() {
        return true;
    }
    @ConfigItem(
            keyName = "DepositAll",
            name = "Deposit all",
            description = "force the bank to deposit all items each time.",
            position = 6,
            section = toggles
    )
    default boolean depositAll() {
        return false;
    }
    @ConfigItem(
            keyName = "AmuletofChemistry",
            name = "Wear amulet of chemistry",
            description = "wear amulet of chemsitry for potion mixing",
            position = 7,
            section = toggles
    )
    default boolean amuletOfChemistry() {
        return false;
    }
    @ConfigItem(
            keyName = "Interaction Option",
            name = "Interaction Option",
            description = "default is \"use\".",
            position = 0,
            section = interaction
    )
    default String menu() {
        return "use";
    }
    @ConfigItem(
            keyName = "Sleep Min",
            name = "Sleep Min",
            description = "Sets the minimum sleep time.",
            position = 0,
            section = sleepSection
    )
    @Range(
            min = 60,
            max = 20000
    )

    default int sleepMin() {
        return 0;
    }

    @ConfigItem(
            keyName = "Sleep Max",
            name = "Sleep Max",
            description = "Sets the maximum sleep time.",
            position = 0,
            section = sleepSection
    )
    @Range(
            min = 90,
            max = 20000
    )

    default int sleepMax() {
        return 1800;
    }

    @ConfigItem(
            keyName = "Sleep Target",
            name = "Sleep Target",
            description = "This is the Target or Mean of the distribution.",
            position = 0,
            section = sleepSection
    )
    @Range(
            min = 100,
            max = 20000
    )

    default int sleepTarget() {
        return 900;
    }
}
