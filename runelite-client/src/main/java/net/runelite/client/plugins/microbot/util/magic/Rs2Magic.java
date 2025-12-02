package net.runelite.client.plugins.microbot.util.magic;

import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
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
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
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
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.Microbot.log;
import static net.runelite.client.plugins.microbot.util.Global.*;

public class Rs2Magic {
    //use this boolean to do one time checks
    private static boolean checkedSpellBook = false;

    private static final RuneFilter DEFAULT_RUNE_FILTER = RuneFilter.builder().build();

    /**
     * Check if all the settings are correct before we start interacting with spellbook
     */
    public static boolean oneTimeSpellBookCheck() {
        if (checkedSpellBook) return true;
        if (!Rs2Player.hasCompletedTutorialIsland()) return true;
        if (!Rs2SpellBookSettings.configureSpellbookSettings()) return false;

        checkedSpellBook = true;
        return true;
    }

    /**
     * Determines if the player can cast the given spell by verifying the
     * player's active spellbook and the availability of the required runes.
     *
     * @param spell the spell that needs to be checked for rune availability
     * @return true if the player has the correct spellbook, high enough level and enough runes to cast the spell
     */
    public static boolean canCast(Spell spell) {
        return spell.hasRequirements() && hasRequiredRunes(spell);
    }

    /**
     * Checks if a specific spell can be cast
     * contains all the necessary checks to do a successful check
     * use quickCanCast if the performance of this method is too slow for you
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

        if (!isSpellbook(magicSpell.getSpellbook())) {
            Microbot.log("You need to be on the " + magicSpell.getSpellbook() + " spellbook to cast " + magicSpell.getName() + ".");
            return false;
        }

        if (magicSpell.getName().toLowerCase().contains("enchant")){
            if (Rs2Widget.clickWidget("Jewellery Enchantments", Optional.of(218), 3, true)) {
                sleepUntil(() -> Rs2Widget.hasWidgetText("Jewellery Enchantments", 218, 3, true), 2000);
            }
        } else if (!Rs2Widget.isHidden(14286852)) {
            // back button inside the enchant jewellery interface has no text, that's why we use hardcoded id
            Rs2Widget.clickWidget(14286852);
        }

        Widget widget = Arrays.stream(Rs2Widget.getWidget(218, 3).getStaticChildren()).filter(x -> x.getSpriteId() == magicSpell.getSprite()).findFirst().orElse(null);

        return widget != null;
    }

	public static boolean quickCanCast(Spell spell) {
		return quickCanCast(spell.getMagicAction());
	}

    /**
     * Checks if a specific spell can be cast without checking settings first
     * This method is more performant than the canCast, use this one if you are sure
     * that the settings are correct
     * @param magicSpell
     * @return
     */
    public static boolean quickCanCast(MagicAction magicSpell) {
        if (magicSpell == null) return false;

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
        return quickCanCast(MagicAction.fromString(spellName));
    }

	public static boolean cast(Spell spell) {
		return cast(spell, "cast", 1);
	}

	public static boolean cast(Spell spell, String option, int identifier) {
		return cast(spell.getMagicAction(), option, identifier);
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
        if (magicSpell.getName().toLowerCase().contains("teleport") ||
                magicSpell.getName().toLowerCase().contains("bones to") ||
                (magicSpell.getActions() != null && Arrays.stream(magicSpell.getActions()).anyMatch(x -> x != null && x.equalsIgnoreCase("cast")))) {
            menuAction = MenuAction.CC_OP;
        } else {
            menuAction = MenuAction.WIDGET_TARGET;
        }

        if (magicSpell.getWidgetId() == -1)
            throw new NotImplementedException("This spell has not been configured yet in the MagicAction.java class");

        Microbot.doInvoke(new NewMenuEntry()
                .option(option)
                .param0(-1)
                .param1(magicSpell.getWidgetId())
                .opcode(menuAction.getId())
                .identifier(identifier)
                .itemId(-1)
                .target(magicSpell.getName())
                ,
                new Rectangle(Rs2Widget.getWidget(magicSpell.getWidgetId()).getBounds()));
        //Rs2Reflection.invokeMenu(-1, magicSpell.getWidgetId(), menuAction.getId(), 1, -1, "Cast", "<col=00ff00>" + magicSpell.getName() + "</col>", -1, -1);
        return true;
    }

	public static boolean quickCast(Spell spell) {
		return quickCast(spell.getMagicAction());
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

	public static boolean castOn(Spell spell, Actor actor) {
		return castOn(spell.getMagicAction(), actor);
	}

    public static boolean castOn(MagicAction magicSpell, Actor actor) {
        if (actor == null) return false;
        cast(magicSpell);
        if (!Global.sleepUntil(() -> Microbot.getClient().isWidgetSelected())) return false;

        if (!Rs2Camera.isTileOnScreen(actor.getLocalLocation())) Rs2Camera.turnTo(actor.getLocalLocation());

        if (actor instanceof Rs2NpcModel) {
            return Rs2Npc.interact(new Rs2NpcModel((NPC) (actor)));
        } else if (actor instanceof Player) {
            return Rs2Player.cast(new Rs2PlayerModel((Player) actor));
        }
        return false;
    }

    public static void alch(Rs2ItemModel item, int sleepMin, int sleepMax) {
        if (Microbot.getClient().getRealSkillLevel(Skill.MAGIC) >= 55) {
            highAlch(item, sleepMin, sleepMax);
        } else {
            lowAlch(item, sleepMin, sleepMax);
        }
    }

    public static void alch(String itemName, int sleepMin, int sleepMax) {
        alch(Rs2Inventory.get(itemName), sleepMin, sleepMax);
    }

    public static void alch(String itemName) {
        alch(Rs2Inventory.get(itemName));
    }

    /**
     * alch item with min sleep of 300 and max sleep of 600
     *
     * @param item to alch
     */
    public static void alch(Rs2ItemModel item) {
        alch(item, 300, 600);
    }

    public static void superHeat(String itemName) {
        superHeat(Rs2Inventory.get(itemName));
    }

    public static void superHeat(String itemName, int sleepMin, int sleepMax) {
        superHeat(Rs2Inventory.get(itemName), sleepMin, sleepMax);
    }

    public static void superHeat(int id) {
        superHeat(Rs2Inventory.get(id));
    }

    public static void superHeat(int id, int sleepMin, int sleepMax) {
        superHeat(Rs2Inventory.get(id), sleepMin, sleepMax);
    }

    public static void superHeat(Rs2ItemModel item) {
        superHeat(item, 300, 600);
    }

    private static boolean setup() {
        Rs2Tab.switchToMagicTab();
        if (!sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC)) return false;
        sleep(50, 150);

        final Widget widget = Rs2Widget.getWidget(218, 4);
        if (widget != null && widget.getActions() != null && Rs2Widget.isWidgetVisible(218, 4) &&
                Arrays.stream(widget.getActions()).anyMatch(x -> x.equalsIgnoreCase("back"))) {
            if (!Rs2Widget.clickWidget(widget)) return false;
            sleep(150, 300);
        }
        return true;
    }

    private static void highAlch(Rs2ItemModel item, int sleepMin, int sleepMax) {
        if (!setup()) return;

        final Widget highAlch = Rs2Widget.findWidget(MagicAction.HIGH_LEVEL_ALCHEMY.getName());
        if (highAlch.getSpriteId() != 41) return;
        alch(highAlch, item, sleepMin, sleepMax);
    }

    private static void lowAlch(Rs2ItemModel item, int sleepMin, int sleepMax) {
        if (!setup()) return;

        Widget lowAlch = Rs2Widget.findWidget(MagicAction.LOW_LEVEL_ALCHEMY.getName());
        if (lowAlch.getSpriteId() != 25) return;
        alch(lowAlch, item, sleepMin, sleepMax);
    }

    private static void interact(Rs2ItemModel item, Point point, String info) {
        if (item == null) {
            Microbot.status = info + " x: " + point.getX() + " y: " + point.getY();
            Microbot.getMouse().click(point);
        } else {
            Microbot.status = info + " " + item.getName();
            Rs2Inventory.interact(item, "cast");
        }
    }

    private static void alch(Widget alch, Rs2ItemModel item, int sleepMin, int sleepMax) {
        if (alch == null) return;
        Point point = new Point((int) alch.getBounds().getCenterX(), (int) alch.getBounds().getCenterY());
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC).orElse(false), 5000);
        sleep(sleepMin, sleepMax);
        Microbot.getMouse().click(point);
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY).orElse(false), 5000);
        sleep(sleepMin, sleepMax);
        interact(item, point, "Alching");
    }

    public static void superHeat(Rs2ItemModel item, int sleepMin, int sleepMax) {
        if (!setup()) return;
        final Widget superheat = Rs2Widget.findWidget(MagicAction.SUPERHEAT_ITEM.getName());
        if (superheat == null) return;
        if (superheat.getSpriteId() != SpriteID.SPELL_SUPERHEAT_ITEM) return;

        if (!quickCast(MagicAction.SUPERHEAT_ITEM)) return;
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY).orElse(false), 5000);
        sleep(sleepMin, sleepMax);

        final Point point = new Point((int) superheat.getBounds().getCenterX(), (int) superheat.getBounds().getCenterY());
        interact(item, point, "Superheating");
    }

    private final static int CHOOSE_CHARACTER_WIDGET_ID = 4915200;
    public static boolean npcContact(String npcName) {
        if (!isSpellbook(Rs2Spellbook.LUNAR)) {
            Microbot.log("Tried casting npcContact, but lunar spellbook was not found.");
            return false;
        }

        if (!cast(MagicAction.NPC_CONTACT)) return false;
        if (!sleepUntilTrue(() -> !Rs2Widget.isHidden(CHOOSE_CHARACTER_WIDGET_ID), 100, 5000)) return false;

        final Widget chooseCharacterWidget = Rs2Widget.getWidget(CHOOSE_CHARACTER_WIDGET_ID);
        if (chooseCharacterWidget == null) return false;

        final Widget npcWidget = Rs2Widget.findWidget(npcName);
        if (npcWidget == null) return false;

        // check if npc widget is fully visible inside the choose character widget
        final Rectangle npcBounds = npcWidget.getBounds();
        final Rectangle chooseCharacterBounds = chooseCharacterWidget.getBounds();
        if (!Rs2UiHelper.isRectangleWithinRectangle(chooseCharacterBounds, npcBounds)) {
            Microbot.log("NPC widget is not fully visible inside the choose character widget, scrolling...");
            Global.sleepUntil(() -> Rs2UiHelper.isRectangleWithinRectangle(chooseCharacterBounds, Rs2Widget.findWidget(npcName).getBounds()), () -> {
                boolean isBelow = npcBounds.y > chooseCharacterBounds.y;
                if (isBelow) Microbot.getMouse().scrollDown(Rs2UiHelper.getClickingPoint(chooseCharacterWidget.getBounds(),true));
                else Microbot.getMouse().scrollUp(Rs2UiHelper.getClickingPoint(chooseCharacterWidget.getBounds(),true));
            }, 5000, 300);
        }

        if (!Rs2Widget.clickWidget(npcName, Optional.of(75), 0, false)) return false;
        Rs2Player.waitForAnimation();
        return true;
    }

    public static boolean repairPouchesWithLunar() {
        if (!Rs2Inventory.hasDegradedPouch()) return false;
        log("Repairing pouches...");
        if (!npcContact("dark mage")) return false;

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
        Rs2Tab.switchToInventoryTab(); // TODO: can this be removed?

        return !Rs2Inventory.hasDegradedPouch();
    }

    public static Rs2Spellbook getSpellbook() {
        return Rs2Spellbook.getCurrentSpellbook();
    }

    public static boolean isSpellbook(Rs2Spellbook spellbook) {
        return getSpellbook() == spellbook;
    }

    public static boolean isShadowVeilActive() {
        return Microbot.getVarbitValue(VarbitID.ARCEUUS_SHADOW_VEIL_ACTIVE) == 1;
    }

    public static boolean isThrallActive() {
        return Rs2Thrall.isActive();
    }

    private static final int ANCIENT_VARBIT_OFFSET = 10;
    /**
     * Gets the currently selected auto-cast spell based on varbit 276.
     *
     * @return the matching Rs2CombatSpells or null if none matches.
     */
    public static Rs2CombatSpells getCurrentAutoCastSpell() {
        final int autoCastSpell = Microbot.getVarbitValue(VarbitID.AUTOCAST_SPELL) - (isSpellbook(Rs2Spellbook.ANCIENT) ? ANCIENT_VARBIT_OFFSET : 0);
        return Arrays.stream(Rs2CombatSpells.values())
                .filter(spell -> spell.getVarbitValue() == autoCastSpell)
                .findAny().orElse(null);
    }
    
    public static Rs2Spells getRs2Spell(String spellName){
        return Arrays.stream(Rs2Spells.values())
                .filter(spell -> spell.getName().toLowerCase().contains(spellName.toLowerCase()))
                .findFirst().orElse(null);
    }
    
    public static Rs2Staff getRs2Staff(int itemID) {
        return Stream.of(Rs2Staff.values())
                .filter(staff -> staff.getItemID() == itemID)
                .findAny().orElse(Rs2Staff.NONE);
    }

    public static Rs2Tome getRs2Tome(int itemID) {
        return Stream.of(Rs2Tome.values())
                .filter(tome -> tome.getItemID() == itemID)
                .findAny().orElse(Rs2Tome.NONE);
    }

    public static List<Rs2Staff> findStavesByRunes(List<Runes> runes) {
        return Stream.of(Rs2Staff.values())
                .filter(staff -> staff.getRunes().containsAll(runes))
                .collect(Collectors.toList());
    }

    private static int limitSum(int i, int j) {
        try {
            return Math.addExact(i,j);
        } catch (ArithmeticException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static Map<Runes, Integer> addInventoryRunes(Map<Runes, Integer> runes) {
        for (Runes rune : Runes.values()) {
            runes.merge(rune, Rs2Inventory.itemQuantity(rune.getItemId()), Rs2Magic::limitSum);
        }
        return runes;
    }

    private static Map<Runes, Integer> addEquipmentRunes(Map<Runes, Integer> runes) {
        final Rs2ItemModel equippedWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (equippedWeapon != null) {
            Rs2Staff equippedStaff = getRs2Staff(equippedWeapon.getId());
            if (equippedStaff != Rs2Staff.NONE) {
                equippedStaff.getRunes().forEach(rune -> runes.put(rune, Integer.MAX_VALUE));
            }
        }

        // Remove runes provided by equipped tomes
        final Rs2ItemModel equippedShield = Rs2Equipment.get(EquipmentInventorySlot.SHIELD);
        if (equippedShield != null) {
            Rs2Tome equippedTome = getRs2Tome(equippedShield.getId());
            if (equippedTome != Rs2Tome.NONE) {
                equippedTome.getRunes().forEach(rune -> runes.put(rune, Integer.MAX_VALUE));
            }
        }
        return runes;
    }

    private static Map<Runes, Integer> addBankRunes(Map<Runes, Integer> runes) {
        Rs2Bank.bankItems().stream()
                .flatMap(item -> Arrays.stream(Runes.values())
                        .filter(r -> r.getItemId() == item.getId())
                        .map(rune -> Map.entry(rune, item.getQuantity())))
                .forEach(entry -> runes.merge(entry.getKey(), entry.getValue(), Rs2Magic::limitSum));
        return runes;
    }

    private static Map<Runes, Integer> addComboRunes(Map<Runes, Integer> runes) {
        runes.replaceAll((key, value) -> {
            final Runes[] comboRunes = Runes.getComboRunes(key);
            if (comboRunes.length == 0) return value;

            final int comboQuantity = Arrays.stream(comboRunes)
                    .map(rune -> runes.getOrDefault(rune, 0))
                    .reduce(0, Rs2Magic::limitSum);
            return limitSum(value, comboQuantity);
        });
        return runes;
    }

    /**
     * Calculates the available runes
     *
     * @param runeFilter which Inventories to search for rune
     * @return A {@link Map} where the key is {@link ItemID} of the rune,
     * and the value is an {@code Integer} representing the quantity of that rune available
     * or {@code Integer.MAX_VALUE} if the rune is provided by equipment.
     */
    public static Map<Runes, Integer> getRunes(RuneFilter runeFilter) {
        final Map<Runes, Integer> availableRunes = runeFilter.isIncludeRunePouch() && Rs2Inventory.hasRunePouch() ?
                Rs2RunePouch.getRunes() : new HashMap<>();

        if (runeFilter.isIncludeInventory()) addInventoryRunes(availableRunes);
        if (runeFilter.isIncludeEquipment()) addEquipmentRunes(availableRunes);
        if (runeFilter.isIncludeBank()) addBankRunes(availableRunes);
        if (runeFilter.isIncludeComboRunes()) addComboRunes(availableRunes);

        return availableRunes;
    }

    /**
     * Calculates the available runes
     *
     * @return A {@link Map} where the key is {@link ItemID} of the rune,
     * and the value is an {@code Integer} representing the quantity of that rune available
     * or {@code Integer.MAX_VALUE} if the rune is provided by equipment.
     */
    public static Map<Runes, Integer> getRunes() {
        return getRunes(DEFAULT_RUNE_FILTER);
    }

    @Deprecated(since = "Use getMissingRunes")
    public static Map<Runes, Integer> getRequiredRunes(Spell spell, int casts, boolean checkRunePouch) {
        final RuneFilter filter = RuneFilter.builder().includeRunePouch(checkRunePouch).build();
        return getMissingRunes(spell, casts, filter);
    }

    /**
     * Calculates the required runes to cast {@code spell} for {@code casts}
     * amount of times.
     *
     * @return A {@link Map} where the key is {@link ItemID} of the rune,
     * and the value is an {@link Integer} representing the quantity of that runes required
     */
    public static Map<Runes, Integer> getRequiredRunes(Spell spell, int casts) {
        return spell.getRequiredRunes(casts);
    }

    public static Map<Runes, Integer> getRequiredRunes(Spell spell) {
        return spell.getRequiredRunes();
    }

    /**
     * Calculates how many runes we are missing in Inventory, Equipment, Rune Pouch & Bank
     * to meet the Rune Requirements {@code reqRunes}
     *
     * @param reqRunes the Rune Requirements (ItemID -> requiredQuantity)
     * @param runeFilter which Inventories to search for runes
     * @return A {@link Map} where the key is {@link ItemID} of the rune, and the
     * value is an {@link Integer} representing the quantity of missing runes.
     */
    public static Map<Runes, Integer> getMissingRunes(Map<Runes, Integer> reqRunes, RuneFilter runeFilter) {
        if (reqRunes.isEmpty()) return reqRunes;

        final Map<Runes, Integer> runes = getRunes(runeFilter);
        reqRunes.replaceAll((key, value) -> Math.max(0,value-runes.getOrDefault(key, 0)));
        reqRunes.keySet().removeIf(e -> reqRunes.get(e) <= 0);

        return reqRunes;
    }

    public static Map<Runes, Integer> getMissingRunes(Map<Runes, Integer> reqRunes) {
        return getMissingRunes(reqRunes, DEFAULT_RUNE_FILTER);
    }

    public static Map<Runes, Integer> getMissingRunes(Spell spell, int casts, RuneFilter runeFilter) {
        return getMissingRunes(getRequiredRunes(spell, casts), runeFilter);
    }

    public static Map<Runes, Integer> getMissingRunes(Spell spell, int casts) {
        return getMissingRunes(spell, casts, DEFAULT_RUNE_FILTER);
    }

    public static Map<Runes, Integer> getMissingRunes(Spell spell) {
        return getMissingRunes(spell, 1);
    }

    /**
     * Checks if the player has the required runes to cast a specified spell.
     *
     * @param reqRunes the Rune Requirements (ItemID -> requiredQuantity)
     * @param runeFilter which Inventories to search for runes
     * @return true if all required runes are available; false otherwise.
     */
    public static boolean hasRequiredRunes(Map<Runes, Integer> reqRunes, RuneFilter runeFilter) {
        return getMissingRunes(reqRunes, runeFilter).isEmpty();
    }

    public static boolean hasRequiredRunes(Map<Runes, Integer> reqRunes) {
        return hasRequiredRunes(reqRunes, DEFAULT_RUNE_FILTER);
    }

    public static boolean hasRequiredRunes(Spell spell, int casts, RuneFilter runeFilter) {
        return hasRequiredRunes(getRequiredRunes(spell, casts), runeFilter);
    }

    public static boolean hasRequiredRunes(Spell spell, int casts) {
        return hasRequiredRunes(spell, casts, DEFAULT_RUNE_FILTER);
    }

    public static boolean hasRequiredRunes(Spell spell, RuneFilter runeFilter) {
        return hasRequiredRunes(spell, 1, runeFilter);
    }

    public static boolean hasRequiredRunes(Spell spell) {
        return hasRequiredRunes(spell, 1);
    }
}
