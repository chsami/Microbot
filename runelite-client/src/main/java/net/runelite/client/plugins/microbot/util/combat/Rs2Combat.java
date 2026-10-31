package net.runelite.client.plugins.microbot.util.combat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.combat.weapons.*;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.util.List;
import java.util.Map;

import static net.runelite.client.plugins.microbot.Microbot.log;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

@Slf4j
public class Rs2Combat {

    private static final Map<Integer, Weapon> WEAPONS_MAP = WeaponsGenerator.generate();

    /**
     * Sets the attack style
     *
     * @param style WidgetInfo. ex. COMBAT_STYLE_ONE
     * @return boolean, whether the action succeeded
     */
    public static boolean setAttackStyle(WidgetInfo style) {
        Widget widget = Microbot.getClient().getWidget(style);
        if (widget == null) return false;
//        if (isSelected(widget.getId() + 1)) {
//            return true;
//        }
        Widget label = Rs2Widget.getWidget(widget.getId() + 3);
        log("Setting attack style to " + (label != null ? label.getText() : style.name()));
        Rs2Widget.clickWidget(widget);
        return true;
    }

    /**
     * Sets the auto-cast spell with an option to use defensive casting.
     *
     * @param combatSpell      The spell to auto-cast.
     * @param useDefensiveCast Whether to use defensive casting mode.
     * @return true if the spell is successfully set, false otherwise.
     */
    public static boolean setAutoCastSpell(Rs2CombatSpells combatSpell, boolean useDefensiveCast) {
        if (combatSpell == null) return false;
        if (!Rs2Magic.canCast(combatSpell.getMagicAction())) return false;
        if (Rs2Magic.getCurrentAutoCastSpell() == combatSpell && Microbot.getVarbitValue(Varbits.DEFENSIVE_CASTING_MODE) == (useDefensiveCast ? 1 : 0)) return true;
        
        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT);

        Widget autoCastWidget = useDefensiveCast
                ? Rs2Widget.getWidget(WidgetInfo.COMBAT_DEFENSIVE_SPELL_BOX.getId())
                : Rs2Widget.getWidget(WidgetInfo.COMBAT_SPELL_BOX.getId());

        Rs2Widget.clickWidget(autoCastWidget);
        sleepUntil(() -> Rs2Widget.isWidgetVisible(201, 1));
        
        Widget autoCastOptions = Rs2Widget.getWidget(201, 1);
        if (autoCastOptions == null) return false;
        
        Widget spellSprite = Rs2Widget.findWidget(combatSpell.getMagicAction().getSprite(), List.of(autoCastOptions));
        if (spellSprite == null) return false;

        Rs2Widget.clickWidget(spellSprite);

        return sleepUntilTrue(() -> Rs2Magic.getCurrentAutoCastSpell() == combatSpell && Microbot.getVarbitValue(Varbits.DEFENSIVE_CASTING_MODE) == (useDefensiveCast ? 1 : 0));
    }

    /**
     * Sets the auto retaliate state
     *
     * @param enable boolean, true for enabled, false for disabled
     * @return boolean, whether the action succeeded
     */
    public static boolean setAutoRetaliate(boolean enable) {
        final int expectedVarPlayerValue = enable ? 0 : 1;
        if (Microbot.getVarbitPlayerValue(VarPlayerID.OPTION_NODEF) == expectedVarPlayerValue) return true;

        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        if (!sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT && Rs2Widget.getWidget(InterfaceID.CombatInterface.RETALIATE) != null, 2_000)) {
            log.warn("Failed to {} auto retaliate could not find widget", enable ? "enable" : "disable");
            return false;
        }
        Rs2Widget.clickWidget(InterfaceID.CombatInterface.RETALIATE);

        return sleepUntil(() -> Microbot.getVarbitPlayerValue(VarPlayerID.OPTION_NODEF) == expectedVarPlayerValue, 2_000);
    }

    /**
     * Sets the special attack state if currentSpecEnergy >= specialAttackEnergyRequired
     *
     * @param state                       boolean, true for enabled, false for disabled
     * @param specialAttackEnergyRequired int, 1000 = 100%
     * @return boolean, whether the action succeeded
     */
    public static boolean setSpecState(boolean state, int specialAttackEnergyRequired) {
        int currentSpecEnergy = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT)
        ).orElse(0);
        Rectangle specBounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget widget = Microbot.getClient().getWidget(10485795);
            if (widget == null || widget.isHidden()) return null;
            return widget.getBounds();
        }).orElse(null);
        if (specBounds == null) return false;
        if (currentSpecEnergy < specialAttackEnergyRequired) return false;
        if (state == getSpecState()) return true;

        Microbot.getMouse().click(specBounds);

        log("Used special attack");

       //  Microbot.doInvoke(new NewMenuEntry(-1, 10485795, MenuAction.CC_OP.getId(), 1, -1, "Special Attack"), new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        //Rs2Reflection.invokeMenu(-1, 10485795, MenuAction.CC_OP.getId(), 1, -1, "Use", "Special Attack", -1, -1);
        return true;
    }

    /**
     * get special attack energy (1000 is full spec bar)
     *
     * @return
     */
    public static int getSpecEnergy() {
        return Microbot.getVarbitPlayerValue(VarPlayerID.SA_ENERGY);
    }

    /**
     * Sets the special attack state
     *
     * @param state boolean, true for enabled, false for disabled
     * @return boolean, whether the action succeeded
     */
    public static boolean setSpecState(boolean state) {
        return setSpecState(state, -1);
    }

    /**
     * Checks the state of the spec widget
     *
     * @return boolean, whether the spec is enabled
     */
    public static boolean getSpecState() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget widget = Microbot.getClient().getWidget(WidgetInfo.MINIMAP_SPEC_ORB.getId() + 4);
            if (widget == null) return false;
            return widget.getSpriteId() == 1608;
        }).orElse(false);
    }

    /**
     * Checks if the widget is selected (based on the red background)
     *
     * @param widgetId int, the widget id
     * @return boolean, whether the widget is selected
     */
    private static boolean isSelected(int widgetId) {
        return Rs2Widget.getChildWidgetSpriteID(widgetId, 0) == 1150;
    }

    @Deprecated(since = "use setAutoRetaliate")
    public static boolean enableAutoRetialiate() {
        return setAutoRetaliate(true);
    }

    public static boolean inCombat() {
        if (!Microbot.isLoggedIn()) return false;

        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) return false;

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    Actor interactingActor = player.getInteracting();
                    if (interactingActor == null || interactingActor.getCombatLevel() < 1) return false;
                    return player.getAnimation() != -1 || player.isInteracting();
                })
                .orElse(false);
    }

    /**
     * Computes the player's current attack range based on equipped weapon, chosen attack style,
     * and whether manual-cast or special attacks should be included.
     * <p>
     * If no weapon is equipped or the equipped weapon is not recognized, returns {@code 1}.
     *
     * @param includeManualCast    {@code true} to include manual-cast range (e.g., spells), {@code false} to ignore
     * @param includeSpecialAttack {@code true} to include the weapon’s special-attack range (if melee), {@code false} otherwise
     * @return the effective attack range in tiles (minimum of {@code 1})
     */
    public static int getAttackRange(boolean includeManualCast, boolean includeSpecialAttack) {
        final Rs2ItemModel equippedWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        final Map<Integer, Weapon> weaponsMap = WEAPONS_MAP;

        if (equippedWeapon == null || !weaponsMap.containsKey(equippedWeapon.getId())) {
            return 1;
        }

        Weapon weapon = weaponsMap.get(equippedWeapon.getId());
        String attackStyle = getWeaponAttackStyle();

        int unmodifiedRange;
        if (weapon instanceof ManualCastable) {
            unmodifiedRange = ((ManualCastable) weapon).getRange(attackStyle, includeManualCast);
        } else if (weapon instanceof Melee) {
            return includeSpecialAttack ? ((Melee) weapon).getSpecialAttackRange() : 1;
        } else {
            unmodifiedRange = weapon.getRange(attackStyle);
        }

        return Math.max(unmodifiedRange, 1);
    }

    /**
     * Computes the player's default attack range (excluding manual-cast and special attacks).
     *
     * @return the default attack range in tiles (minimum of {@code 1})
     * @see #getAttackRange(boolean, boolean)
     */
    public static int getAttackRange() {
        return getAttackRange(false, false);
    }

    /**
     * Resolves the string name of the currently selected attack style for a weapon.
     * <p>
     * It looks up the weapon-styles enum to map varbit indices to style names.
     *
     * @return the human-readable attack style name (e.g., "Slash", "Magic"), or {@code null} if it cannot be resolved
     */
    public static String getWeaponAttackStyle() {
        final int attackStyleVarbit = Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE);
        final int weaponTypeVarbit = Microbot.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
        EnumComposition weaponStyles = Microbot.getEnum(EnumID.WEAPON_STYLES);
        if (weaponStyles == null) return null;
        int weaponStyleEnum = weaponStyles.getIntValue(weaponTypeVarbit);
        if (weaponStyleEnum == -1) return null;
        EnumComposition weaponStyleComposition = Microbot.getEnum(weaponStyleEnum);
        if (weaponStyleComposition == null) return null;
        int[] weaponStyleStructs = weaponStyleComposition.getIntVals();
        if (weaponStyleStructs == null || attackStyleVarbit < 0 || attackStyleVarbit >= weaponStyleStructs.length) return null;
        StructComposition attackStylesStruct = Microbot.getStructComposition(weaponStyleStructs[attackStyleVarbit]);
        return attackStylesStruct == null ? null : attackStylesStruct.getStringValue(ParamID.ATTACK_STYLE_NAME);
    }
}
