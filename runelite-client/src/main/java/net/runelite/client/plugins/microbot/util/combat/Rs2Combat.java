package net.runelite.client.plugins.microbot.util.combat;

import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.List;

import static net.runelite.client.plugins.microbot.Microbot.log;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

public class Rs2Combat {

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
        log("Setting attack style to " + Rs2Widget.getWidget(widget.getId() + 3).getText());
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
        
        Rs2Tab.switchToCombatOptionsTab();
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
     * @param state boolean, true for enabled, false for disabled
     * @return boolean, whether the action succeeded
     */
    public static boolean setAutoRetaliate(boolean state) {
        Widget widget = Microbot.getClient().getWidget(WidgetInfo.COMBAT_AUTO_RETALIATE);
        if (widget == null) return false;
        if (state == isSelected(widget.getId() + 2)) return true;

        Microbot.getMouse().click(widget.getBounds());
        Global.sleep(600, 1000);
        return true;
    }

    /**
     * Sets the special attack state if currentSpecEnergy >= specialAttackEnergyRequired
     *
     * @param state                       boolean, true for enabled, false for disabled
     * @param specialAttackEnergyRequired int, 1000 = 100%
     * @return boolean, whether the action succeeded
     */
    public static boolean setSpecState(boolean state, int specialAttackEnergyRequired) {
        int currentSpecEnergy = Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT);
        if (Rs2Widget.isHidden(10485795)) return false;
        if (currentSpecEnergy < specialAttackEnergyRequired) return false;
        if (state == getSpecState()) return true;

        Microbot.getMouse().click(Rs2Widget.getWidget(10485795).getBounds());

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
        int currentSpecEnergy = Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT);
        return currentSpecEnergy;
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
        Widget widget = Microbot.getClient().getWidget(WidgetInfo.MINIMAP_SPEC_ORB.getId() + 4);
        if (widget == null) throw new RuntimeException("Somehow the spec orb is null!");

        return widget.getSpriteId() == 1608;
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

    public static boolean enableAutoRetialiate() {
        if (Microbot.getVarbitPlayerValue(172) == 1) {
            Rs2Tab.switchToCombatOptionsTab();
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 2000);
            Rs2Widget.clickWidget(38862879);
        }

        return Microbot.getVarbitPlayerValue(172) == 0;
    }

    public static boolean inCombat() {
        if (!Microbot.isLoggedIn()) return false;
        if (Microbot.getClient().getLocalPlayer().getInteracting() == null) return false;
        if (Microbot.getClientThread().runOnClientThread(() -> Microbot.getClient().getLocalPlayer().getInteracting().getCombatLevel() < 1)) return false;
        return Microbot.getClient().getLocalPlayer().isInteracting() || Microbot.getClient().getLocalPlayer().getAnimation() != -1;
    }
}
