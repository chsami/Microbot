package net.runelite.client.plugins.microbot.util.magic;

import com.google.common.collect.ImmutableList;
import lombok.Getter;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.RunePouch;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.settings.Rs2SpellBookSettings;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import org.apache.commons.lang3.NotImplementedException;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.api.Varbits.*;
import static net.runelite.client.plugins.microbot.Microbot.log;
import static net.runelite.client.plugins.microbot.util.Global.*;

public class Rs2Magic {
    //use this boolean to do one time checks
    private static boolean firstInteractionWithSpellBook = true;

    /**
     * Check if all the settings are correct before we start interacting with spellbook
     */
    public static boolean oneTimeSpellBookCheck() {
        if (Rs2Player.isInTutorialIsland())
            return true;
        // We add a one time check to avoid performanec issues. Checking varbits is expensive
        if (firstInteractionWithSpellBook && !Rs2SpellBookSettings.setAllFiltersOn()) {
            return false;
        }
        firstInteractionWithSpellBook = false;
        return true;
    }

    /**
     * Checks if a specific spell can be cast
     * contains all the necessary checks to do a succesfull check
     * use quickCanCast if the performance of this method is to slow for you
     * @param magicSpell
     * @return
     */
    public static boolean canCast(MagicAction magicSpell) {
        if (!oneTimeSpellBookCheck()) {
            Rs2Random.waitEx(800, 150);
            Rs2Dialogue.clickContinue();
            Microbot.log("Your spellbook filtering seems off...Microbot is trying to fix this");
            return false;
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.MAGIC) {
            Rs2Tab.switchToMagicTab();
            sleep(150, 300);
        }

        if ( Microbot.getVarbitValue(Varbits.SPELLBOOK) != magicSpell.getSpellbook().getId()) {
            Microbot.log("You need to be on the " + magicSpell.getSpellbook().getName() + " spellbook to cast " + magicSpell.getName() + ".");
            return false;
        }

        if (magicSpell.getName().toLowerCase().contains("enchant")){
            if (Rs2Widget.clickWidget("Jewellery Enchantments", Optional.of(218), 3, true)) {
                sleepUntil(() -> Rs2Widget.hasWidgetText("Jewellery Enchantments", 218, 3, true), 2000);
            }
        } else if (!Rs2Widget.isHidden(14286852)) {
            // back button inside the enchant jewellery interface has no text, thats why we use hardcoded id
            Rs2Widget.clickWidget(14286852);
        }

        Widget widget = Arrays.stream(Rs2Widget.getWidget(218, 3).getStaticChildren()).filter(x -> x.getSpriteId() == magicSpell.getSprite()).findFirst().orElse(null);

        return widget != null;
    }

    /**
     * Checks if a specific spell can be cast without checking settings first
     * This method is more performant than the canCast, use this one if you are sure
     * that the settings are correct
     * @param magicSpell
     * @return
     */
    public static boolean quickCanCast(MagicAction magicSpell) {
        if (Rs2Tab.getCurrentTab() != InterfaceTab.MAGIC) {
            Rs2Tab.switchToMagicTab();
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC);
        }
        
        Widget spellbookWidget = Rs2Widget.getWidget(218, 3);
        if (spellbookWidget == null) return false;
        Widget widget = Rs2Widget.findWidget(magicSpell.getName(), List.of(spellbookWidget));
        if (widget == null) return false;
        return widget.getSpriteId() == magicSpell.getSprite();
    }

    public static boolean quickCanCast(String spellName) {
        if (spellName == null) return false;
        MagicAction magicAction = Arrays.stream(MagicAction.values()).filter(x -> x.getName().toLowerCase().contains(spellName.toLowerCase())).findFirst().orElse(null);
        if (magicAction == null) return false;
        return quickCanCast(magicAction);
    }
    
    public static boolean cast(MagicAction magicSpell) {
        return cast(magicSpell, "cast", 1);
    }

    public static boolean cast(MagicAction magicSpell, String option, int identifier) {
        MenuAction menuAction;
        Rs2Tab.switchToMagicTab();
        Microbot.status = "Casting " + magicSpell.getName();
        sleep(150, 300);
        if (!canCast(magicSpell)) {
            log("Unable to cast " + magicSpell.getName());
            return false;
        }
        if (magicSpell.getName().toLowerCase().contains("teleport") || magicSpell.getName().toLowerCase().contains("Bones to") || (magicSpell.getActions() != null && Arrays.stream(magicSpell.getActions()).anyMatch(x -> x != null && x.equalsIgnoreCase("cast")))) {
            menuAction = MenuAction.CC_OP;
        } else {
            menuAction = MenuAction.WIDGET_TARGET;
        }

        if (magicSpell.getWidgetId() == -1)
            throw new NotImplementedException("This spell has not been configured yet in the MagicAction.java class");

        Microbot.doInvoke(new NewMenuEntry(option, -1, magicSpell.getWidgetId(), menuAction.getId(), identifier, -1, magicSpell.getName()), new Rectangle(Rs2Widget.getWidget(magicSpell.getWidgetId()).getBounds()));
        //Rs2Reflection.invokeMenu(-1, magicSpell.getWidgetId(), menuAction.getId(), 1, -1, "Cast", "<col=00ff00>" + magicSpell.getName() + "</col>", -1, -1);
        return true;
    }

    public static boolean quickCast(MagicAction magicSpell) {
        Microbot.status = "Casting " + magicSpell.getName();

        if(quickCanCast(magicSpell)) {
            Widget widget = Rs2Widget.findWidget(magicSpell.getName());
            Microbot.click(widget.getBounds());
            return true;
        }
        log("Unable to cast " + magicSpell.getName());
        return false;
    }

    public static void castOn(MagicAction magicSpell, Actor actor) {
        if (actor == null) return;
        cast(magicSpell);
        Global.sleepUntil(() -> Microbot.getClient().isWidgetSelected());
        if (!Rs2Camera.isTileOnScreen(actor.getLocalLocation())) {
            Rs2Camera.turnTo(actor.getLocalLocation());
            return;
        }
        if (actor instanceof Rs2NpcModel) {
            Rs2Npc.interact(new Rs2NpcModel((NPC) (actor)));
        } else {
            if (actor instanceof Player) {
                Rs2Player.cast(new Rs2PlayerModel((Player) actor));
            }
        }
    }

    public static void alch(String itemName, int sleepMin, int sleepMax) {
        Rs2ItemModel item = Rs2Inventory.get(itemName);
        if (Microbot.getClient().getRealSkillLevel(Skill.MAGIC) >= 55) {
            highAlch(item, sleepMin, sleepMax);
        } else {
            lowAlch(item, sleepMin, sleepMax);
        }
    }

    public static void alch(String itemName) {
        Rs2ItemModel item = Rs2Inventory.get(itemName);
        if (Microbot.getClient().getRealSkillLevel(Skill.MAGIC) >= 55) {
            highAlch(item, 300, 600);
        } else {
            lowAlch(item, 300, 600);
        }
    }

    /**
     * alch item with minsleep of 300 and maxsleep of 600
     *
     * @param item
     */
    public static void alch(Rs2ItemModel item) {
        alch(item, 300, 600);
    }

    /**
     * @param item
     * @param sleepMin
     * @param sleepMax
     */
    public static void alch(Rs2ItemModel item, int sleepMin, int sleepMax) {
        if (Microbot.getClient().getRealSkillLevel(Skill.MAGIC) >= 55) {
            highAlch(item, sleepMin, sleepMax);
        } else {
            lowAlch(item, sleepMin, sleepMax);
        }
    }

    public static void superHeat(String itemName) {
        Rs2ItemModel item = Rs2Inventory.get(itemName);
        superHeat(item, 300, 600);
    }

    public static void superHeat(String itemName, int sleepMin, int sleepMax) {
        Rs2ItemModel item = Rs2Inventory.get(itemName);
        superHeat(item, sleepMin, sleepMax);
    }

    public static void superHeat(int id) {
        Rs2ItemModel item = Rs2Inventory.get(id);
        superHeat(item, 300, 600);
    }

    public static void superHeat(int id, int sleepMin, int sleepMax) {
        Rs2ItemModel item = Rs2Inventory.get(id);
        superHeat(item, sleepMin, sleepMax);
    }

    public static void superHeat(Rs2ItemModel item) {
        superHeat(item, 300, 600);
    }

    public static void superHeat(Rs2ItemModel item, int sleepMin, int sleepMax) {
        sleepUntil(() -> {
            Rs2Tab.switchToMagicTab();
            sleep(50, 150);
            return Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC;
        });
        if (Rs2Widget.isWidgetVisible(218, 4) && Arrays.stream(Rs2Widget.getWidget(218, 4).getActions()).anyMatch(x -> x.equalsIgnoreCase("back"))){
            Rs2Widget.clickWidget(218, 4);
            sleep(150, 300);
        }
        Widget superHeat = Rs2Widget.findWidget(MagicAction.SUPERHEAT_ITEM.getName());
        if (superHeat.getSpriteId() != SpriteID.SPELL_SUPERHEAT_ITEM) return;
        superHeat(superHeat, item, sleepMin, sleepMax);
    }

    private static void highAlch(Rs2ItemModel item, int sleepMin, int sleepMax) {
        sleepUntil(() -> {
            Rs2Tab.switchToMagicTab();
            sleep(50, 150);
            return Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC;
        });
        if (Rs2Widget.isWidgetVisible(218, 4) && Arrays.stream(Rs2Widget.getWidget(218, 4).getActions()).anyMatch(x -> x.equalsIgnoreCase("back"))){
            Rs2Widget.clickWidget(218, 4);
            sleep(150, 300);
        }
        Widget highAlch = Rs2Widget.findWidget(MagicAction.HIGH_LEVEL_ALCHEMY.getName());
        if (highAlch.getSpriteId() != 41) return;
        alch(highAlch, item, sleepMin, sleepMax);
    }

    private static void lowAlch(Rs2ItemModel item, int sleepMin, int sleepMax) {
        sleepUntil(() -> {
            Rs2Tab.switchToMagicTab();
            sleep(50, 150);
            return Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC;
        });
        if (Rs2Widget.isWidgetVisible(218, 4) && Arrays.stream(Rs2Widget.getWidget(218, 4).getActions()).anyMatch(x -> x.equalsIgnoreCase("back"))){
            Rs2Widget.clickWidget(218, 4);
            sleep(150, 300);
        }
        Widget lowAlch = Rs2Widget.findWidget(MagicAction.LOW_LEVEL_ALCHEMY.getName());
        if (lowAlch.getSpriteId() != 25) return;
        alch(lowAlch, item, sleepMin, sleepMax);
    }

    private static void alch(Widget alch, Rs2ItemModel item, int sleepMin, int sleepMax) {
        if (alch == null) return;
        Point point = new Point((int) alch.getBounds().getCenterX(), (int) alch.getBounds().getCenterY());
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC).orElse(false), 5000);
        sleep(sleepMin, sleepMax);
        Microbot.getMouse().click(point);
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY).orElse(false), 5000);
        sleep(sleepMin, sleepMax);
        if (item == null) {
            Microbot.status = "Alching x: " + point.getX() + " y: " + point.getY();
            Microbot.getMouse().click(point);
        } else {
            Microbot.status = "Alching " + item.name;
            Rs2Inventory.interact(item, "cast");
        }
    }

    private static void superHeat(Widget superheat, Rs2ItemModel item, int sleepMin, int sleepMax) {
        if (superheat == null) return;
        Point point = new Point((int) superheat.getBounds().getCenterX(), (int) superheat.getBounds().getCenterY());
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC).orElse(false), 5000);
        sleep(sleepMin, sleepMax);
        Microbot.getMouse().click(point);
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY).orElse(false), 5000);
        sleep(sleepMin, sleepMax);
        if (item == null) {
            Microbot.status = "Superheating x: " + point.getX() + " y: " + point.getY();
            Microbot.getMouse().click(point);
        } else {
            Microbot.status = "Superheating " + item.name;
            Rs2Inventory.interact(item, "cast");
        }
    }

    // humidify
    public static void humidify() {
        sleepUntil(() -> {
            Rs2Tab.switchToMagicTab();
            sleep(50, 150);
            return Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC;
        });
        Widget humidify = Rs2Widget.findWidget(MagicAction.HUMIDIFY.getName());
        if (humidify.getSpriteId() == 1972) {
            Microbot.click(humidify.getBounds());
        }
    }

    public static boolean npcContact(String npcName) {
        if (!isLunar()) {
            Microbot.log("Tried casting npcContact, but lunar spellbook was not found.");
            return false;
        }
        final int chooseCharacterWidgetId = 4915200;
        boolean didCast = cast(MagicAction.NPC_CONTACT);
        if (!didCast) return false;
        boolean result = sleepUntilTrue(() -> Rs2Widget.getWidget(chooseCharacterWidgetId) != null && !Rs2Widget.isHidden(chooseCharacterWidgetId), 100, 5000);
        if (!result) return false;
        Widget chooseCharacterWidget = Rs2Widget.getWidget(chooseCharacterWidgetId);
        Widget npcWidget = Rs2Widget.findWidget(npcName);
        // check if npc widget is fully visible inside the choose character widget
        Rectangle npcBounds = npcWidget.getBounds();
        Rectangle chooseCharacterBounds = chooseCharacterWidget.getBounds();
        if (!Rs2UiHelper.isRectangleWithinRectangle(chooseCharacterBounds, npcBounds)) {
            Microbot.log("NPC widget is not fully visible inside the choose character widget, scrolling...");
            Global.sleepUntil(() -> Rs2UiHelper.isRectangleWithinRectangle(chooseCharacterBounds, Rs2Widget.findWidget(npcName).getBounds()), () -> {
                boolean isBelow = npcBounds.y > chooseCharacterBounds.y;
                if (isBelow) Microbot.getMouse().scrollDown(Rs2UiHelper.getClickingPoint(chooseCharacterWidget.getBounds(),true));
                else Microbot.getMouse().scrollUp(Rs2UiHelper.getClickingPoint(chooseCharacterWidget.getBounds(),true));
            }, 5000, 300);
        }
        boolean clickResult = Rs2Widget.clickWidget(npcName, Optional.of(75), 0, false);
        if (!clickResult) return false;
        Rs2Player.waitForAnimation();
        return true;
    }

    public static boolean repairPouchesWithLunar() {
        log("Repairing pouches...");
        if (npcContact("dark mage")) {
            sleep(Rs2Random.randomGaussian(1100, 200));
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            Rs2Dialogue.clickContinue();
            sleep(Rs2Random.randomGaussian(1100, 200));
            Rs2Widget.sleepUntilHasWidget("Can you repair my pouches?");
            sleep(Rs2Random.randomGaussian(900, 300));
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            Rs2Widget.clickWidget("Can you repair my pouches?", Optional.of(162), 0, true);
            sleep(Rs2Random.randomGaussian(900, 200));
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleepGaussian(700,200);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleep(Rs2Random.randomGaussian(1500, 300));
            Rs2Tab.switchToInventoryTab();
        }
        return !Rs2Inventory.hasDegradedPouch();
    }

    private static void alch(Widget alch) {
        alch(alch, null, 300, 600);
    }

    private static void superHeat(Widget superHeat) {
        superHeat(superHeat, null, 300, 600);
    }

    public static boolean isLunar() {
        return Microbot.getVarbitValue(Varbits.SPELLBOOK) == 2;
    }

    public static boolean isAncient() {
        return Microbot.getVarbitValue(Varbits.SPELLBOOK) == 1;
    }

    public static boolean isModern() {
        return Microbot.getVarbitValue(Varbits.SPELLBOOK) == 0;
    }

    public static boolean isArceeus() {
        return Microbot.getVarbitValue(Varbits.SPELLBOOK) == 3;
    }

    public static boolean isShadowVeilActive() {
        return Microbot.getVarbitValue(SHADOW_VEIL) == 1;
    }
    public static boolean isThrallActive() {
        return (Microbot.getVarbitValue(RESURRECT_THRALL) == 1||Microbot.getVarbitValue(RESURRECT_THRALL_COOLDOWN) == 1);
    }
    /**
     * Gets the currently selected auto-cast spell based on varbit 276.
     *
     * @return the matching Rs2CombatSpells or null if none matches.
     */
    public static Rs2CombatSpells getCurrentAutoCastSpell() {
        int currentVarbitValue = Microbot.getVarbitValue(276);
        int ancientVarbitOffset = 10;
        int offset = isAncient() ? ancientVarbitOffset : 0;
        for (Rs2CombatSpells spell : Rs2CombatSpells.values()) {
            if ((spell.getVarbitValue() + offset) == currentVarbitValue) {
                return spell;
            }
        }
        return null;
    }
    
    public static Rs2Spells getRs2Spell(String spellName){
        return Arrays.stream(Rs2Spells.values())
                .filter(spell -> spell.getName().toLowerCase().contains(spellName.toLowerCase()))
                .findFirst()
                .orElse(null);
    }
    
    public static Rs2Staff getRs2Staff(int itemID) {
        return Stream.of(Rs2Staff.values())
                .filter(staff -> staff.getItemID() == itemID)
                .findFirst()
                .orElse(Rs2Staff.NONE);
    }

    public static Rs2Tome getRs2Tome(int itemID) {
        return Stream.of(Rs2Tome.values())
                .filter(tome -> tome.getItemID() == itemID)
                .findFirst()
                .orElse(Rs2Tome.NONE);
    }

    public static List<Rs2Staff> findStavesByRunes(List<Runes> runes) {
        return Stream.of(Rs2Staff.values())
                .filter(staff -> staff.getRunes().containsAll(runes))
                .collect(Collectors.toList());
    }
    
    /**
     * Calculates the runes required to cast a specified spell a certain number of times,
     * taking into account equipped staves, inventory, and optionally, rune pouch runes.
     *
     * This method dynamically determines the number of runes still needed to meet the
     * casting requirement by checking available runes in the inventory and rune pouch
     * and accounting for any runes provided by equipped staves.
     *
     * @param spell          The combat spell to cast, represented as an {@link Rs2CombatSpells} enum.
     * @param equippedStaff  The currently equipped staff, represented as an {@link Rs2Staff} object,
     *                       which can reduce the number of required runes.
     * @param casts          The number of times the spell should be cast. Must be greater than 0.
     * @param checkRunePouch A boolean indicating whether to include runes from the rune pouch in the calculation.
     * @return A {@link Map} where the key is a {@link Runes} enum representing the type of rune, 
     *         and the value is an {@code Integer} representing the quantity of that rune still needed.
     *         If all required runes are available, the map will be empty.
     * @throws IllegalArgumentException if the {@code casts} parameter is less than or equal to 0.
     */
    public static Map<Runes, Integer> getRequiredRunes(Rs2CombatSpells spell, Rs2Staff equippedStaff, int casts, boolean checkRunePouch) {
        if (casts <= 0) {
            throw new IllegalArgumentException("Number of casts must be greater than 0.");
        }

        // Calculate total required runes for the desired number of casts
        Map<Runes, Integer> requiredRunes = new HashMap<>();
        spell.getRequiredRunes().forEach((rune, amount) -> requiredRunes.put(rune, amount * casts));

        // Subtract runes provided by the equipped staff
        if (equippedStaff != null) {
            for (Runes providedRune : equippedStaff.getRunes()) {
                requiredRunes.remove(providedRune);
            }
        }

        // Gather available runes from inventory
        Map<Runes, Integer> availableRunes = new HashMap<>();
        for (Rs2ItemModel item : Rs2Inventory.items()) {
            Arrays.stream(Runes.values())
                    .filter(rune -> rune.getItemId() == item.getId())
                    .findFirst()
                    .ifPresent(rune -> availableRunes.merge(rune, item.getQuantity(), Integer::sum));
        }

        // Optionally add runes from the rune pouch
        if (checkRunePouch) {
            RunePouch.getRunes().forEach((runeId, quantity) -> {
                Arrays.stream(Runes.values())
                        .filter(r -> r.getItemId() == runeId)
                        .findFirst()
                        .ifPresent(rune -> availableRunes.merge(rune, quantity, Integer::sum));
            });
        }

        // Calculate remaining runes needed
        for (Runes rune : requiredRunes.keySet()) {
            int requiredAmount = requiredRunes.get(rune);
            int availableAmount = availableRunes.getOrDefault(rune, 0);

            if (availableAmount >= requiredAmount) {
                requiredRunes.put(rune, 0);
            } else {
                requiredRunes.put(rune, requiredAmount - availableAmount);
            }
        }

        // Remove runes that are fully satisfied
        requiredRunes.entrySet().removeIf(entry -> entry.getValue() <= 0);

        return requiredRunes;
    }

    /**
     * Checks if the player has the required runes to cast a specified combat spell.
     *
     * This method checks runes from the following sources:
     * - Inventory: All runes present in the player's inventory.
     * - Rune Pouch: If the player has a rune pouch, runes from it are counted.
     * - Bank: If specified, runes from the bank are considered.
     * - Equipped Items: Runes provided by equipped staffs and tomes are excluded from the requirements.
     * - Combination Runes: Supports runes like Mist, Mud, Smoke, Steam, Dust, and Lava as substitutes.
     *
     * @param spell       The combat spell to cast, represented as an {@link Rs2CombatSpells} enum.
     * @param hasRunePouch Whether the player has a rune pouch.
     * @param hasInBank    Whether to consider runes available in the bank.
     * @return true if all required runes are available; false otherwise.
     */
    public static boolean hasRequiredRunes(Rs2CombatSpells spell, boolean hasRunePouch, boolean hasInBank) {
        Map<Runes, Integer> requiredRunes = new HashMap<>(spell.getRequiredRunes());

        // Remove runes provided by equipped staffs
        Rs2ItemModel equippedWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (equippedWeapon != null) {
            Rs2Staff equippedStaff = getRs2Staff(equippedWeapon.getId());
            if (equippedStaff != Rs2Staff.NONE) {
                equippedStaff.getRunes().forEach(requiredRunes::remove);
            }
        }

        // Remove runes provided by equipped tomes
        Rs2ItemModel equippedShield = Rs2Equipment.get(EquipmentInventorySlot.SHIELD);
        if (equippedShield != null) {
            Rs2Tome equippedTome = getRs2Tome(equippedShield.getId());
            if (equippedTome != Rs2Tome.NONE) {
                equippedTome.getRunes().forEach(requiredRunes::remove);
            }
        }

        // Gather available runes from inventory
        Map<Runes, Integer> availableRunes = new HashMap<>();
        Rs2Inventory.items().forEach(item -> {
            Arrays.stream(Runes.values())
                    .filter(rune -> rune.getItemId() == item.getId())
                    .findFirst()
                    .ifPresent(rune -> availableRunes.merge(rune, item.getQuantity(), Integer::sum));
        });

        // Gather runes from the rune pouch
        if (hasRunePouch) {
            RunePouch.getRunes().forEach((runeId, quantity) -> {
                Arrays.stream(Runes.values())
                        .filter(r -> r.getItemId() == runeId)
                        .findFirst()
                        .ifPresent(rune -> availableRunes.merge(rune, quantity, Integer::sum));
            });
        }

        // Gather runes from the bank if needed
        if (hasInBank) {
            Rs2Bank.bankItems().stream()
                    .flatMap(item -> Arrays.stream(Runes.values())
                            .filter(r -> r.getItemId() == item.getId())
                            .map(rune -> Map.entry(rune, item.getQuantity())))
                    .forEach(entry -> availableRunes.merge(entry.getKey(), entry.getValue(), Integer::sum));
        }

        // Check each required rune, using combination runes if necessary
        for (Map.Entry<Runes, Integer> entry : requiredRunes.entrySet()) {
            Runes rune = entry.getKey();
            int requiredAmount = entry.getValue();
            int availableAmount = availableRunes.getOrDefault(rune, 0);

            // Use regular runes first
            if (availableAmount >= requiredAmount) {
                continue;
            }

            int deficit = requiredAmount - availableAmount;
            int comboRuneCount = 0;

            // Calculate combination rune availability
            switch (rune) {
                case AIR:
                    comboRuneCount = availableRunes.getOrDefault(Runes.MIST, 0) + availableRunes.getOrDefault(Runes.SMOKE, 0) + availableRunes.getOrDefault(Runes.DUST, 0);
                    break;
                case WATER:
                    comboRuneCount = availableRunes.getOrDefault(Runes.MIST, 0) + availableRunes.getOrDefault(Runes.MUD, 0) + availableRunes.getOrDefault(Runes.STEAM, 0);
                    break;
                case EARTH:
                    comboRuneCount = availableRunes.getOrDefault(Runes.MUD, 0) + availableRunes.getOrDefault(Runes.DUST, 0) + availableRunes.getOrDefault(Runes.LAVA, 0);
                    break;
                case FIRE:
                    comboRuneCount = availableRunes.getOrDefault(Runes.LAVA, 0) + availableRunes.getOrDefault(Runes.SMOKE, 0) + availableRunes.getOrDefault(Runes.STEAM, 0);
                    break;
                default:
                    comboRuneCount = 0;
            }

            // Check if combination runes are sufficient to cover the deficit
            if (comboRuneCount < deficit) {
                return false;
            }
        }

        return true;
    }

    /**
     * Overloaded method that checks if the player has the required runes to cast a specified combat spell.
     *
     * This method automatically determines if the player has a rune pouch and excludes runes from equipped staffs and tomes.
     *
     * @param spell The combat spell to cast, represented as an {@link Rs2CombatSpells} enum.
     * @return true if all required runes are available; false otherwise.
     */
    public static boolean hasRequiredRunes(Rs2CombatSpells spell) {
        return hasRequiredRunes(spell, Rs2Inventory.hasRunePouch(), false);
    }

    /**
     * Calculates the runes required to cast a specified spell a certain number of times,
     * taking into account equipped staves, inventory, and optionally, rune pouch runes.
     *
     * This method dynamically determines the number of runes still needed to meet the
     * casting requirement by checking available runes in the inventory and rune pouch
     * and accounting for any runes provided by equipped staves.
     *
     * @param spell          The spell to cast, represented as an {@link Rs2Spells} enum.
     * @param equippedStaff  The currently equipped staff, represented as an {@link Rs2Staff} object,
     *                       which can reduce the number of required runes.
     * @param casts          The number of times the spell should be cast. Must be greater than 0.
     * @param checkRunePouch A boolean indicating whether to include runes from the rune pouch in the calculation.
     * @return A {@link Map} where the key is a {@link Runes} enum representing the type of rune, 
     *         and the value is an {@code Integer} representing the quantity of that rune still needed.
     *         If all required runes are available, the map will be empty.
     * @throws IllegalArgumentException if the {@code casts} parameter is less than or equal to 0.
     */
    public static Map<Runes, Integer> getRequiredRunes(Rs2Spells spell, Rs2Staff equippedStaff, int casts, boolean checkRunePouch) {
        if (casts <= 0) {
            throw new IllegalArgumentException("Number of casts must be greater than 0.");
        }

        // Calculate total required runes for the desired number of casts
        Map<Runes, Integer> requiredRunes = new HashMap<>();
        spell.getRequiredRunes().forEach((rune, amount) -> requiredRunes.put(rune, amount * casts));

        // Subtract runes provided by the equipped staff
        if (equippedStaff != null) {
            for (Runes providedRune : equippedStaff.getRunes()) {
                requiredRunes.remove(providedRune);
            }
        }

        // Gather available runes from inventory
        Map<Runes, Integer> availableRunes = new HashMap<>();
        for (Rs2ItemModel item : Rs2Inventory.items()) {
            Arrays.stream(Runes.values())
                    .filter(rune -> rune.getItemId() == item.getId())
                    .findFirst()
                    .ifPresent(rune -> availableRunes.merge(rune, item.getQuantity(), Integer::sum));
        }

        // Optionally add runes from the rune pouch
        if (checkRunePouch) {
            RunePouch.getRunes().forEach((runeId, quantity) -> {
                Arrays.stream(Runes.values())
                        .filter(r -> r.getItemId() == runeId)
                        .findFirst()
                        .ifPresent(rune -> availableRunes.merge(rune, quantity, Integer::sum));
            });
        }

        // Calculate remaining runes needed
        for (Runes rune : requiredRunes.keySet()) {
            int requiredAmount = requiredRunes.get(rune);
            int availableAmount = availableRunes.getOrDefault(rune, 0);

            if (availableAmount >= requiredAmount) {
                requiredRunes.put(rune, 0);
            } else {
                requiredRunes.put(rune, requiredAmount - availableAmount);
            }
        }

        // Remove runes that are fully satisfied
        requiredRunes.entrySet().removeIf(entry -> entry.getValue() <= 0);

        return requiredRunes;
    }

    /**
     * Checks if the player has the required runes to cast a specified spell.
     *
     * This method checks runes from the following sources:
     * - Inventory: All runes present in the player's inventory.
     * - Rune Pouch: If the player has a rune pouch, runes from it are counted.
     * - Bank: If specified, runes from the bank are considered.
     * - Equipped Items: Runes provided by equipped staffs and tomes are excluded from the requirements.
     * - Combination Runes: Supports runes like Mist, Mud, Smoke, Steam, Dust, and Lava as substitutes.
     *
     * @param spell       The spell to cast, represented as an {@link Rs2Spells} enum.
     * @param hasRunePouch Whether the player has a rune pouch.
     * @param hasInBank    Whether to consider runes available in the bank.
     * @return true if all required runes are available; false otherwise.
     */
    public static boolean hasRequiredRunes(Rs2Spells spell, boolean hasRunePouch, boolean hasInBank) {
        Map<Runes, Integer> requiredRunes = new HashMap<>(spell.getRequiredRunes());

        // Check if we have a staff equipped that provides the runes
        Rs2ItemModel equippedWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (equippedWeapon != null) {
            Rs2Staff equippedStaff = getRs2Staff(equippedWeapon.getId());
            if (equippedStaff != Rs2Staff.NONE) {
                equippedStaff.getRunes().forEach(requiredRunes::remove);
            }
        }

        // Remove runes provided by equipped tomes
        Rs2ItemModel equippedShield = Rs2Equipment.get(EquipmentInventorySlot.SHIELD);
        if (equippedShield != null) {
            Rs2Tome equippedTome = getRs2Tome(equippedShield.getId());
            if (equippedTome != Rs2Tome.NONE) {
                equippedTome.getRunes().forEach(requiredRunes::remove);
            }
        }

        // Collect available runes from inventory
        Map<Runes, Integer> availableRunes = new HashMap<>();
        Rs2Inventory.items().forEach(item -> {
            Arrays.stream(Runes.values())
                    .filter(rune -> rune.getItemId() == item.getId())
                    .findFirst()
                    .ifPresent(rune -> availableRunes.merge(rune, item.getQuantity(), Integer::sum));
        });

        // Collect runes from the rune pouch if we have it in inventory
        if (hasRunePouch) {
            RunePouch.getRunes().forEach((runeId, quantity) -> {
                Arrays.stream(Runes.values())
                        .filter(r -> r.getItemId() == runeId)
                        .findFirst()
                        .ifPresent(rune -> availableRunes.merge(rune, quantity, Integer::sum));
            });
        }

        // Add runes from bank if required
        if (hasInBank) {
            Rs2Bank.bankItems().stream()
                    .flatMap(item -> Arrays.stream(Runes.values())
                            .filter(r -> r.getItemId() == item.getId())
                            .map(rune -> Map.entry(rune, item.getQuantity())))
                    .forEach(entry -> availableRunes.merge(entry.getKey(), entry.getValue(), Integer::sum));
        }

        // Check each required rune, using combination runes if necessary
        for (Map.Entry<Runes, Integer> entry : requiredRunes.entrySet()) {
            Runes rune = entry.getKey();
            int requiredAmount = entry.getValue();
            int availableAmount = availableRunes.getOrDefault(rune, 0);

            // Use regular runes first
            if (availableAmount >= requiredAmount) {
                continue;
            }

            int deficit = requiredAmount - availableAmount;
            int comboRuneCount = 0;

            // Calculate combination rune availability
            switch (rune) {
                case AIR:
                    comboRuneCount = availableRunes.getOrDefault(Runes.MIST, 0) + availableRunes.getOrDefault(Runes.SMOKE, 0) + availableRunes.getOrDefault(Runes.DUST, 0);
                    break;
                case WATER:
                    comboRuneCount = availableRunes.getOrDefault(Runes.MIST, 0) + availableRunes.getOrDefault(Runes.MUD, 0) + availableRunes.getOrDefault(Runes.STEAM, 0);
                    break;
                case EARTH:
                    comboRuneCount = availableRunes.getOrDefault(Runes.MUD, 0) + availableRunes.getOrDefault(Runes.DUST, 0) + availableRunes.getOrDefault(Runes.LAVA, 0);
                    break;
                case FIRE:
                    comboRuneCount = availableRunes.getOrDefault(Runes.LAVA, 0) + availableRunes.getOrDefault(Runes.SMOKE, 0) + availableRunes.getOrDefault(Runes.STEAM, 0);
                    break;
                default:
                    comboRuneCount = 0;
            }

            // Check if combination runes are sufficient to cover the deficit
            if (comboRuneCount < deficit) {
                return false;
            }
        }

        return true;
    }

    /**
     * Overloaded method that checks if the player has the required runes to cast a specified spell.
     *
     * This method automatically determines if the player has a rune pouch and excludes runes from equipped staffs and tomes.
     *
     * @param spell The combat spell to cast, represented as an {@link Rs2CombatSpells} enum.
     * @return true if all required runes are available; false otherwise.
     */
    public static boolean hasRequiredRunes(Rs2Spells spell) {
        return hasRequiredRunes(spell, Rs2Inventory.hasRunePouch(), false);
    }

    //DATA

    @Getter
    private final List<Integer> runeIds = ImmutableList.of(
            ItemID.NATURE_RUNE,
            ItemID.LAW_RUNE,
            ItemID.BODY_RUNE,
            ItemID.DUST_RUNE,
            ItemID.LAVA_RUNE,
            ItemID.STEAM_RUNE,
            ItemID.SMOKE_RUNE,
            ItemID.SOUL_RUNE,
            ItemID.WATER_RUNE,
            ItemID.AIR_RUNE,
            ItemID.EARTH_RUNE,
            ItemID.FIRE_RUNE,
            ItemID.MIND_RUNE,
            ItemID.CHAOS_RUNE,
            ItemID.DEATH_RUNE,
            ItemID.BLOOD_RUNE,
            ItemID.COSMIC_RUNE,
            ItemID.ASTRAL_RUNE,
            ItemID.MIST_RUNE,
            ItemID.MUD_RUNE,
            ItemID.WRATH_RUNE,
            ItemID.SUNFIRE_RUNE);
}
