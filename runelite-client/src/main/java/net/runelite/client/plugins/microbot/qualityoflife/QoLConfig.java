package net.runelite.client.plugins.microbot.qualityoflife;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.fletching.enums.FletchingItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.qualityoflife.enums.CraftingItem;
import net.runelite.client.plugins.microbot.qualityoflife.enums.WintertodtActions;
import net.runelite.client.plugins.microbot.util.misc.SpecialAttackWeaponEnum;

import java.awt.*;

@ConfigGroup("QoL")
public interface QoLConfig extends Config {

    // Section for Overlay Configurations
    @ConfigSection(
            name = "Overlay Configurations",
            description = "Settings related to overlay rendering",
            position = 0
    )
    String overlaySection = "overlaySection";
    // Section for Do-Last Configurations
    @ConfigSection(
            name = "Do-Last Configurations",
            description = "Settings related to Do-Last actions",
            position = 1
    )
    String doLastSection = "doLastSection";
    // Section for Camera Configurations
    @ConfigSection(
            name = "Camera Configurations",
            description = "Settings related to camera tracking",
            position = 2
    )
    String cameraSection = "cameraSection";
    // Bank section
    @ConfigSection(
            name = "Bank Configurations",
            description = "Bank settings",
            position = 2
    )
    String bankSection = "bankSection";
    // Section for Dialogue Configurations
    @ConfigSection(
            name = "Dialogue Configurations",
            description = "Settings related to dialogue interactions",
            position = 3
    )
    String dialogueSection = "dialogueSection";
    // Section for Upkeep Configurations
    @ConfigSection(
            name = "Upkeep Configurations",
            description = "Settings related to upkeep actions",
            position = 4
    )
    String upkeepSection = "upkeepSection";
    // Section for Inventory/Equipment Configurations
    @ConfigSection(
            name = "Inventory/Equipment",
            description = "Settings related to inventory/equipment actions",
            position = 5
    )
    String inventorySection = "inventorySection";

    // UI section
    @ConfigSection(
            name = "UI",
            description = "Settings related to UI",
            position = 6
    )
    String uiSection = "uiSection";
    // Wintertodt section
    @ConfigSection(
            name = "Wintertodt",
            description = "Wintertodt settings",
            position = 70
    )
    String wintertodtSection = "wintertodtSection";

    // Guardian of the rift section
    @ConfigSection(
            name = "Guardian of the Rift",
            description = "Guardian of the Rift settings",
            position = 71
    )
    String guardianOfTheRiftSection = "guardianOfTheRiftSection";

    // Fletching section
    @ConfigSection(
            name = "Fletching",
            description = "Fletching settings",
            position = 80
    )
    String fletchingSection = "fletchingSection";

    // Firemaking section
    @ConfigSection(
            name = "Firemaking",
            description = "Firemaking settings",
            position = 81
    )
    String firemakingSection = "firemakingSection";

    // Runecrafting section
    @ConfigSection(
            name = "Runecrafting",
            description = "Runecrafting settings",
            position = 82
    )
    String runecraftingSection = "runecraftingSection";

    // Magic section
    @ConfigSection(
            name = "Magic",
            description = "Magic settings",
            position = 83
    )
    String magicSection = "magicSection";

    // Crafting section
    @ConfigSection(
            name = "Crafting",
            description = "Crafting settings",
            position = 83
    )
    String craftingSection = "craftingSection";

    // boolean to render Max Hit Overlay
    @ConfigItem(
            keyName = "renderMaxHitOverlay",
            name = "Render Max Hit Overlay",
            description = "Render Max Hit Overlay",
            position = 0,
            section = overlaySection
    )
    default boolean renderMaxHitOverlay() {
        return true;
    }

    // boolean to use Withdraw-Last from bank
    @ConfigItem(
            keyName = "useDoLastBank",
            name = "Use Do-Last Bank",
            description = "Use Do-Last Bank",
            position = 0,
            section = doLastSection
    )
    default boolean useDoLastBank() {
        return true;
    }

    // boolean to use DoLast action on furnace
    @ConfigItem(
            keyName = "useDoLastFurnace",
            name = "Use Do-Last Furnace",
            description = "Use Do-Last Furnace",
            position = 1,
            section = doLastSection
    )
    default boolean useDoLastFurnace() {
        return true;
    }

    // boolean to use DoLast action on anvil
    @ConfigItem(
            keyName = "useDoLastAnvil",
            name = "Use Do-Last Anvil",
            description = "Use Do-Last Anvil",
            position = 2,
            section = doLastSection
    )
    default boolean useDoLastAnvil() {
        return true;
    }

    // boolean to use DoLast action on cooking
    @ConfigItem(
            keyName = "useDoLastCooking",
            name = "Use Do-Last Cooking",
            description = "Use Do-Last Cooking",
            position = 3,
            section = doLastSection
    )
    default boolean useDoLastCooking() {
        return true;
    }

    // boolean for Smart Workbench
    @ConfigItem(
            keyName = "smartWorkbench",
            name = "Smart Workbench",
            description = "Fills empty pouches and continues crafting Guardian essence",
            position = 1,
            section = guardianOfTheRiftSection
    )
    default boolean smartWorkbench() {
        return true;
    }

    // boolean for Smart Mine
    @ConfigItem(
            keyName = "smartGotrMine",
            name = "Smart Mine",
            description = "Fills empty pouches and continues and continues mining huge remains",
            position = 2,
            section = guardianOfTheRiftSection
    )
    default boolean smartGotrMine() {
        return true;
    }

    // Right click camera tracking
    @ConfigItem(
            keyName = "rightClickCameraTracking",
            name = "Right Click NPC Tracking",
            description = "Right Click NPC Tracking",
            position = 0,
            section = cameraSection
    )
    default boolean rightClickCameraTracking() {
        return false;
    }

    // smooth camera tracking
    @ConfigItem(
            keyName = "smoothCameraTracking",
            name = "Smooth Camera Tracking",
            description = "Smooth Camera Tracking",
            position = 1,
            section = cameraSection
    )
    default boolean smoothCameraTracking() {
        return true;
    }


    // boolean to use Dialogue auto continue
    @ConfigItem(
            keyName = "useDialogueAutoContinue",
            name = "Dialogue Auto Continue",
            description = "Use Dialogue Auto Continue",
            position = 0,
            section = dialogueSection
    )
    default boolean useDialogueAutoContinue() {
        return true;
    }

    // boolean to automatically enter bankpin
    @ConfigItem(
            keyName = "useBankPin",
            name = "Use bankpin",
            description = "Automatically uses bankpin stored in runelite profile",
            position = 1,
            section = bankSection
    )
    default boolean useBankPin() {
        return false;
    }

    // boolean to automate quest dialogue options
    @ConfigItem(
            keyName = "useQuestDialogueOptions",
            name = "Quest Dialogue Options",
            description = "Automate Quest Dialogue Options",
            position = 1,
            section = dialogueSection
    )
    default boolean useQuestDialogueOptions() {
        return true;
    }

    // boolean to enable Potion Manager
    @ConfigItem(
            keyName = "enablePotionManager",
            name = "Auto Potion Manager",
            description = "Toggle the Potion Manager on or off",
            position = 0,
            section = upkeepSection
    )
    default boolean enablePotionManager() {
        return false;
    }
    // boolean to auto eat food
    @ConfigItem(
            keyName = "autoEatFood",
            name = "Auto Eat Food",
            description = "Auto Eat Food",
            position = 2,
            section = upkeepSection
    )
    default boolean autoEatFood() {
        return false;
    }

    @Range(
            min = 10,
            max = 99
    )
    // percentage of health to eat food
    @ConfigItem(
            keyName = "eatFoodPercentage",
            name = "Eat Food Percentage",
            description = "Eat Food Percentage",
            position = 2,
            section = upkeepSection
    )
    default int eatFoodPercentage() {
        return 50;
    }

    // boolean to drink prayer pot
    @ConfigItem(
            keyName = "autoDrinkPrayerPot",
            name = "Auto Drink Prayer Pot",
            description = "Auto Drink Prayer Pot (if potion manager is enabled this setting will be ignored)",
            position = 1,
            section = upkeepSection
    )
    default boolean autoDrinkPrayerPot() {
        return false;
    }

    @Range(
            min = 1,
            max = 99
    )

    @ConfigItem(
            keyName = "drinkPrayerPotPoints",
            name = "Drink Prayer Pot Points",
            description = "Drink Prayer Pot Points (if potion manager is enabled this setting will be ignored)",
            position = 1,
            section = upkeepSection
    )
    default int drinkPrayerPotPoints() {
        return 35;
    }

    // avoid logging out
    @ConfigItem(
            keyName = "neverLogOut",
            name = "Never log out",
            description = "Never log out",
            position = 3,
            section = upkeepSection
    )
    default boolean neverLogout() {
        return false;
    }

    // boolean to use custom spec weapon
    @ConfigItem(
            keyName = "useSpecWeapon",
            name = "Use Spec Weapon",
            description = "Use Spec Weapon",
            position = 5,
            section = upkeepSection
    )
    default boolean useSpecWeapon() {
        return false;
    }

    // spec weapon
    @ConfigItem(
            keyName = "specWeapon",
            name = "Spec Weapon",
            description = "Spec Weapon",
            position = 6,
            section = upkeepSection
    )
    default SpecialAttackWeaponEnum specWeapon() {
        return SpecialAttackWeaponEnum.DRAGON_DAGGER;
    }

    // boolean to auto use stamina potion
    @ConfigItem(
            keyName = "autoStamina",
            name = "Auto Stamina",
            description = "Auto Stamina",
            position = 8,
            section = upkeepSection
    )
    default boolean autoStamina() {
        return false;
    }

    // run energy threshold to use stamina potion
    @Range(
            min = 1,
            max = 99
    )
    @ConfigItem(
            keyName = "staminaThreshold",
            name = "Stamina Threshold",
            description = "Stamina Threshold",
            position = 9,
            section = upkeepSection
    )
    default int staminaThreshold() {
        return 50;
    }

    @ConfigItem(
            keyName = "refillCannopn",
            name = "Refill cannon",
            description = "Refill & Repair cannon",
            position = 10,
            section = upkeepSection
    )
    default boolean refillCannon() {
        return false;
    }


    // boolean to display Inventory setups as a menu option in the bank
    @ConfigItem(
            keyName = "displayInventorySetups",
            name = "Display Inventory Setups",
            description = "Display Inventory Setups",
            position = 0,
            section = inventorySection
    )
    default boolean displayInventorySetups() {
        return true;
    }

    // boolean to display Setup 1
    @ConfigItem(
            keyName = "displaySetup1",
            name = "Setup 1",
            description = "Display Setup 1",
            position = 1,
            section = inventorySection
    )
    default boolean displaySetup1() {
        return false;
    }

    // String for Setup 1
    @ConfigItem(
            keyName = "Setup1",
            name = "Name:",
            description = "Setup 1",
            position = 2,
            section = inventorySection
    )
    default InventorySetup Setup1(){
        return null;
    }

    // boolean to display Setup 2
    @ConfigItem(
            keyName = "displaySetup2",
            name = "Setup 2",
            description = "Display Setup 2",
            position = 3,
            section = inventorySection
    )
    default boolean displaySetup2() {
        return false;
    }

    // String for Setup 2
    @ConfigItem(
            keyName = "Setup2",
            name = "Name:",
            description = "Setup 2",
            position = 4,
            section = inventorySection
    )
    default InventorySetup Setup2(){
        return null;
    }

    // boolean to display Setup 3
    @ConfigItem(
            keyName = "displaySetup3",
            name = "Setup 3",
            description = "Display Setup 3",
            position = 5,
            section = inventorySection
    )
    default boolean displaySetup3() {
        return false;
    }

    // String for Setup 3
    @ConfigItem(
            keyName = "Setup3",
            name = "Name:",
            description = "Setup 3",
            position = 6,
            section = inventorySection
    )
    default InventorySetup Setup3(){
        return null;
    }

    // boolean to display Setup 4
    @ConfigItem(
            keyName = "displaySetup4",
            name = "Setup 4",
            description = "Display Setup 4",
            position = 7,
            section = inventorySection
    )
    default boolean displaySetup4() {
        return false;
    }

    // String for Setup 4
    @ConfigItem(
            keyName = "Setup4",
            name = "Name:",
            description = "Setup 4",
            position = 8,
            section = inventorySection
    )
    default InventorySetup Setup4(){
        return null;
    }

    // Boolean to use auto drop
    @ConfigItem(
            keyName = "autoDrop",
            name = "Auto Drop",
            description = "Auto Drop",
            position = 9,
            section = inventorySection
    )
    default boolean autoDrop() {
        return false;
    }

    // String for item list to auto drop
    @ConfigItem(
            keyName = "autoDropItems",
            name = "Items:",
            description = "Items to auto drop, separated by commas",
            position = 10,
            section = inventorySection
    )
    default String autoDropItems() {
        return "";
    }

    // Boolean to exclude items from auto drop
    @ConfigItem(
            keyName = "excludeItems",
            name = "Exclude Items",
            description = "Exclude Items instead of including them in auto drop",
            position = 11,
            section = inventorySection
    )
    default boolean excludeItems() {
        return false;
    }


    // boolean to fix camera pitch on login
    @ConfigItem(
            keyName = "fixCameraPitch",
            name = "Fix Login Camera Pitch",
            description = "Fixes the camera pitch on login",
            position = 2,
            section = cameraSection
    )
    default boolean fixCameraPitch() {
        return true;
    }

    // boolean to fix camera zoom on login
    @ConfigItem(
            keyName = "fixCameraZoom",
            name = "Fix Login Camera Zoom",
            description = "Fixes the camera zoom on login",
            position = 3,
            section = cameraSection
    )
    default boolean fixCameraZoom() {
        return true;
    }

    // color picker for Accent Color
    @ConfigItem(
            keyName = "accentColor",
            name = "Accent Color",
            description = "Accent Color",
            position = 0,
            section = uiSection
    )
    default Color accentColor() {
        return new Color(220, 138, 0);
    }

    // color picker for toggle button color
    @ConfigItem(
            keyName = "toggleButtonColor",
            name = "Toggle Button Color",
            description = "Toggle Button Color",
            position = 1,
            section = uiSection
    )
    default Color toggleButtonColor() {
        return new Color(220, 138, 0);
    }

    // color picker for plugin label color
    @ConfigItem(
            keyName = "pluginLabelColor",
            name = "Plugin Label Color",
            description = "Plugin Label Color",
            position = 2,
            section = uiSection
    )
    default Color pluginLabelColor() {
        return new Color(255, 255, 255);
    }

    // boolean to quick fletch kindling
    @ConfigItem(
            keyName = "quickFletchKindling",
            name = "Quick Fletch Kindling",
            description = "Quick Fletch Kindling",
            position = 0,
            section = wintertodtSection
    )
    default boolean quickFletchKindling() {
        return true;
    }

    // boolean to resume fletching kindling if interrupted
    @ConfigItem(
            keyName = "resumeFletchingKindling",
            name = "Resume Fletching",
            description = "Resume Fletching Kindling if interrupted",
            position = 1,
            section = wintertodtSection
    )
    default boolean resumeFletchingKindling() {
        return true;
    }

    // boolean to resume feeding brazier if interrupted
    @ConfigItem(
            keyName = "resumeFeedingBrazier",
            name = "Resume Feeding Brazier",
            description = "Resume Feeding Brazier if interrupted",
            position = 2,
            section = wintertodtSection
    )
    default boolean resumeFeedingBrazier() {
        return true;
    }

    // boolean to fix brazier

    @ConfigItem(
            keyName = "fixBrazier",
            name = "Fix Brazier",
            description = "Fix Brazier",
            position = 3,
            section = wintertodtSection
    )
    default boolean fixBrokenBrazier() {
        return true;
    }

    // boolean to light unlit brazier
    @ConfigItem(
            keyName = "lightUnlitBrazier",
            name = "Light Unlit Brazier",
            description = "Light Unlit Brazier",
            position = 4,
            section = wintertodtSection
    )
    default boolean lightUnlitBrazier() {
        return true;
    }

    // boolean to heal Pyromancer
    @ConfigItem(
            keyName = "healPyromancer",
            name = "Heal Pyromancer",
            description = "Heal Pyromancer",
            position = 5,
            section = wintertodtSection
    )
    default boolean healPyromancer() {
        return true;
    }

    @ConfigItem(
            keyName = "showProgressOverlay",
            name = "Shows Progress Overlay",
            description = "Shows Progress Overlay",
            position = 6,
            section = wintertodtSection
    )
    default boolean showWintertodtProgressOverlay() {
        return true;
    }

    // boolean to quick cut gems
    @ConfigItem(
            keyName = "quickCutGems",
            name = "Quick Cut Gems",
            description = "Option to quick cut gems",
            position = 0,
            section = craftingSection
    )
    default boolean quickCutGems() {
        return false;
    }

    // boolean to quick craft items
    @ConfigItem(
            keyName = "quickCraftItems",
            name = "Quick Craft Items",
            description = "Option to quick craft items",
            position = 1,
            section = craftingSection
    )
    default boolean quickCraftItems() {
        return false;
    }
    // enum for crafting item
    @ConfigItem(
            keyName = "craftingItem",
            name = "Crafting Item",
            description = "Crafting Item",
            position = 2,
            section = craftingSection
    )
    default CraftingItem craftingItem() {
        return CraftingItem.BODY;
    }

    // boolean to quick high alch
    @ConfigItem(
            keyName = "quickHighAlch",
            name = "Quick High Alch",
            description = "Option to quick high alch profitable items",
            position = 0,
            section = magicSection
    )
    default boolean quickHighAlch() {
        return false;
    }

    // Hidden enum for Wintertodt actions
    @ConfigItem(
            keyName = "wintertodtActions",
            name = "Wintertodt Actions",
            description = "Wintertodt Actions",
            hidden = true
    )
    default WintertodtActions wintertodtActions() {
        return WintertodtActions.NONE;
    }

    // Hidden boolean to check if we were interrupted
    @ConfigItem(
            keyName = "interrupted",
            name = "Interrupted",
            description = "Interrupted",
            hidden = true
    )
    default boolean interrupted() {
        return false;
    }

    // boolean to quick fletch items
    @ConfigItem(
            keyName = "quickFletchItems",
            name = "Quick Fletch Items",
            description = "Option to quick fletch logs into items when hovering over knife",
            position = 0,
            section = fletchingSection
    )
    default boolean quickFletchItems() {
        return false;
    }

    // fletching item enum
    @ConfigItem(
            keyName = "fletchingItem",
            name = "Fletching Item",
            description = "Fletching Item",
            position = 1,
            section = fletchingSection
    )
    default FletchingItem fletchingItem() {
        return FletchingItem.ARROW_SHAFT;
    }

    // boolean to quick fletch darts
    @ConfigItem(
            keyName = "quickFletchDarts",
            name = "Quick Fletch Darts",
            description = "Option to quick fletch darts, make x setting must be disabled",
            position = 2,
            section = fletchingSection
    )
    default boolean quickFletchDarts() {
        return false;
    }

    // boolean to quick fletch arrows
    @ConfigItem(
            keyName = "quickFletchArrows",
            name = "Quick Fletch Arrows",
            description = "Option to quick fletch arrows",
            position = 3,
            section = fletchingSection
    )
    default boolean quickFletchArrows() {
        return false;
    }

    // boolean to quick fletch bolts
    @ConfigItem(
            keyName = "quickFletchBolts",
            name = "Quick Fletch Bolts",
            description = "Option to quick fletch bolts",
            position = 4,
            section = fletchingSection
    )
    default boolean quickFletchBolts() {
        return false;
    }

    // boolean to quick fletch headless arrows
    @ConfigItem(
            keyName = "quickFletchHeadlessArrows",
            name = "Quick Fletch Headless Arrows",
            description = "Option to quick fletch headless arrows",
            position = 5,
            section = fletchingSection
    )
    default boolean quickFletchHeadlessArrows() {
        return false;
    }

    // boolean to quick firemake logs
    @ConfigItem(
            keyName = "quickFiremakeLogs",
            name = "Quick Firemake Logs",
            description = "Option to quick firemake logs",
            position = 0,
            section = firemakingSection
    )
    default boolean quickFiremakeLogs() {
        return false;
    }

    // boolean to Smart Runecraft
    @ConfigItem(
            keyName = "smartRunecraft",
            name = "Smart Runecraft",
            description = "Fills empty pouches and continues crafting runes",
            position = 0,
            section = runecraftingSection
    )
    default boolean smartRunecraft() {
        return true;
    }

    // boolean to add quick teleport to house menu entry
    @ConfigItem(
            keyName = "useQuickTeleportToHouse",
            name = "Quick Teleport to House",
            description = "Adds a custom menu entry to rune-pouches to quickly cast teleport to house",
            position = 0,
            section = magicSection
    )
    default boolean useQuickTeleportToHouse() {
        return true;
    }

}
