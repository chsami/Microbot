package net.runelite.client.plugins.microbot.gboc.scripts;

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.gboc.GbocConfig;
import net.runelite.client.plugins.microbot.gboc.GbocPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;


import javax.inject.Inject;
import java.awt.*;
import java.util.concurrent.TimeUnit;

import static net.runelite.api.Varbits.NMZ_ABSORPTION;

public class NMZScript extends Script {

    private final GbocConfig config; //FIX Implement this later

    @Inject
    public NMZScript(GbocConfig config) {
        this.config = config;
    }

    public enum State {
        OUTSIDE,
        INSIDE,
        UNKNOWN
    }

    @Getter
    private static State currentState;
    private WorldPoint center = new WorldPoint(Rs2Random.between(2270, 2276), Rs2Random.between(4693, 4696), 0);
    @Getter
    private static int minAbsorption = Rs2Random.between(50, 300);
    @Getter
    private static int minGuzzle = Rs2Random.between(2, 6);
    private int overloadStartHP = -1;
    public static String scriptDescription = "Start next to NMZ with gold in coffer, items equipped, points for overloads and absorptions and a rock in the bag.";

    public boolean run() {
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn() || !super.run() || GbocPlugin.currentAction.isRunning()) return;

            currentState = determineState();

            switch (currentState) {
                case OUTSIDE:
                    handleOutside();
                    break;
                case INSIDE:
                    handleInside();
                    break;
            }


        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    public void shutdown() {
        super.shutdown();
    }

    private void handleOutside() {
        boolean hasStartedDream = Microbot.getVarbitValue(3946) > 0;

        if (!hasStartedDream) {
            center = new WorldPoint(Rs2Random.between(2270, 2276), Rs2Random.between(4693, 4696), 0);

            if (Rs2Widget.hasWidget("Agree to pay")) {
                GbocPlugin.currentAction.set("Select next", () -> {
                    Rs2Keyboard.typeString("1");
                    Rs2Keyboard.enter();
                    sleepUntil(() -> Rs2Widget.hasWidget("I've prepared your dream."));
                });
                return;
            }
            if (Rs2Widget.hasWidget("Click here to continue")) {
                GbocPlugin.currentAction.set("Select next", () -> {
                    Rs2Widget.clickWidget("Click here to continue");
                    sleepUntil(() -> Rs2Widget.hasWidget("Agree to pay"));
                });
                return;
            }
            if (Rs2Widget.hasWidget("Previous: Customisable Rumble (hard)")) {
                GbocPlugin.currentAction.set("Select previous", () -> {
                    Rs2Widget.clickWidget("Previous: Customisable Rumble (hard)");
                    sleepUntil(() -> Rs2Widget.hasWidget("Click here to continue"));
                });
                return;
            }
            if (Rs2Widget.getWidget(ComponentID.DIALOG_OPTION_OPTIONS) == null) {
                GbocPlugin.currentAction.set("Talk to dominic", () -> {
                    Rs2Npc.interact(NpcID.DOMINIC_ONION, "Dream");
                    sleepUntil(() -> Rs2Widget.hasWidget("Which dream would you like to experience?"));
                });
                return;
            }
        }

        int absorptionAmt = Microbot.getVarbitValue(3954);
        int overloadAmt = Microbot.getVarbitValue(3953);
        int absorptionDeficit = 255 - absorptionAmt;
        int overloadDeficit = 255 - overloadAmt;
        int requiredAbsorption = 18 * 4 + 4 - absorptionAmt;
        int requiredOverload = 9 * 4 + 4 - overloadAmt;
        int benefitsWidgetId = 13500418;


        if (requiredAbsorption > 0 || requiredOverload > 0) {
            if (Rs2Widget.getWidget(benefitsWidgetId) == null) {
                GbocPlugin.currentAction.set("Open store", () -> {
                    Rs2GameObject.interact(26273);
                    sleepUntil(() -> Rs2Widget.isWidgetVisible(benefitsWidgetId));
                });
                return;
            }


            Widget benefitsBtn = Rs2Widget.getWidget(benefitsWidgetId);
            if (benefitsBtn.getChild(4).getSpriteId() != 813) {
                GbocPlugin.currentAction.set("Select benefits tab", () -> {
                    Rs2Widget.clickWidgetFast(benefitsBtn, 4, 4);
                    sleepUntil(() -> benefitsBtn.getChild(4).getSpriteId() == 813);
                });
                return;
            }

            Widget nmzRewardShop = Rs2Widget.getWidget(206, 6);
            if (nmzRewardShop != null) {
                if (requiredAbsorption > 0) {
                    GbocPlugin.currentAction.set("Buy absorption potions", () -> {
                        var itemBounds = Rs2Widget.getWidget(13500422).getChild(9).getBounds();
                        Microbot.doInvoke(new NewMenuEntry("Buy-X", "<col=ff9040>Absorption (1)", 5, MenuAction.CC_OP, 9, 13500422, false), new Rectangle(itemBounds));
                        sleepUntil(() -> Rs2Widget.hasWidget("Enter amount"));
                        Rs2Keyboard.typeString(String.valueOf(absorptionDeficit));
                        Rs2Keyboard.enter();
                        sleepUntil(() -> absorptionAmt != Microbot.getVarbitValue(3954));
                    });
                } else {
                    GbocPlugin.currentAction.set("Buy overload potions", () -> {
                        var itemBounds = Rs2Widget.getWidget(13500422).getChild(6).getBounds();
                        Microbot.doInvoke(new NewMenuEntry("Buy-X", "<col=ff9040>Overload (1)", 5, MenuAction.CC_OP, 6, 13500422, false), new Rectangle(itemBounds));
                        sleepUntil(() -> Rs2Widget.hasWidget("Enter amount"));
                        Rs2Keyboard.typeString(String.valueOf(overloadDeficit));
                        Rs2Keyboard.enter();
                        sleepUntil(() -> overloadDeficit != Microbot.getVarbitValue(3955));
                    });
                }

                return;
            }


        }
        if (Rs2Inventory.count("Absorption") != 18) {
            if (!Rs2Widget.hasWidget("How many doses ")) {
                GbocPlugin.currentAction.set("Withdraw absorption potions", () -> {
                    withdrawPotions(ObjectID.ABSORPTION_POTION, "Absorption", 18);
                    Rs2Inventory.waitForInventoryChanges(10000);
                });
                return;
            }
        }

        if (Rs2Inventory.count("Overload") != 9) {
            if (!Rs2Widget.hasWidget("How many doses ")) {
                GbocPlugin.currentAction.set("Withdraw overload potions", () -> {
                    withdrawPotions(ObjectID.OVERLOAD_POTION, "Overload", 9);
                    Rs2Inventory.waitForInventoryChanges(10000);
                });
                return;
            }
        }

        GbocPlugin.currentAction.set("Drink potion", () -> {
            Rs2GameObject.interact(26291, "drink");
            sleepUntil(() -> Rs2Widget.getWidget(129, 6) != null);
            Widget widget = Rs2Widget.getWidget(129, 6);
            if (!Microbot.getClientThread().runOnClientThread(widget::isHidden)) {
                Rs2Widget.clickWidget(widget.getId());
            }
            sleepUntil(() -> !isOutside());
        });

    }

    private void handleInside() {
        if (!Rs2Inventory.hasItem("Absorption")) {
            GbocPlugin.currentAction.set("Fighting to the death", () -> {
            });
            return;
        }

        if (center.distanceTo(Rs2Player.getWorldLocation()) > 3) {
            GbocPlugin.currentAction.set("Walk to center", () -> {
                Rs2Walker.walkTo(center, 3);
                sleepUntil(() -> center.distanceTo(Rs2Player.getWorldLocation()) < 3);
            });
            return;
        }

        int currentAbsorption = Microbot.getVarbitValue(NMZ_ABSORPTION);
        if (currentAbsorption < minAbsorption && Rs2Inventory.hasItem("Absorption")) {
            GbocPlugin.currentAction.set("Use absorption", () -> {
                Rs2Inventory.interact("Absorption", "Drink");
                sleepUntil(() -> Microbot.getVarbitValue(NMZ_ABSORPTION) > currentAbsorption);
            });
            if (Microbot.getVarbitValue(NMZ_ABSORPTION) < Rs2Random.between(300, 500)) {
                minAbsorption = 600;
            } else {
                minAbsorption = Rs2Random.between(50, 300);
            }
            return;
        }

        int currentHP = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);

        if (!overloaded() && Rs2Inventory.hasItem("Overload") && currentHP > 50) {
            GbocPlugin.currentAction.set("Use overload", () -> {
                overloadStartHP = currentHP;
                Rs2Inventory.interact("Overload", "Drink");
                sleepUntil(this::overloaded);
            });
            return;
        }

        if (Rs2Inventory.hasItem(ItemID.DWARVEN_ROCK_CAKE_7510)) {
            if (currentHP == 1 && minGuzzle == 1) {
                minGuzzle = Rs2Random.between(2, 6);
                GbocPlugin.currentAction.reset();
                return;
            }

            if (overloadStartHP > 0 && overloaded() && currentHP < overloadStartHP - 45) {
                overloadStartHP = -1;
                GbocPlugin.currentAction.reset();
                return;
            }

            if (overloadStartHP < 0 && currentHP > minGuzzle || (overloadStartHP > 0 && overloadStartHP - 50 > 51)) {
                minGuzzle = 1;
                GbocPlugin.currentAction.set("Rock cake", () -> {
                    Rs2Inventory.interact(ItemID.DWARVEN_ROCK_CAKE_7510, "Guzzle");
                    sleepUntil(() -> Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) < currentHP);
                });
                return;
            }
        }

        GbocPlugin.currentAction.reset();
    }

    private boolean isOutside() {
        return Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(new WorldPoint(2602, 3116, 0)) < 20;
    }

    private State determineState() {
        if (Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(new WorldPoint(2602, 3116, 0)) < 20) {
            return State.OUTSIDE;
        }
        if (Rs2Player.getWorldLocation().getRegionID() == 9033) {
            return State.INSIDE;
        }
        return State.UNKNOWN;
    }

    private void withdrawPotions(int objectId, String itemName, int amount) {
        Rs2GameObject.interact(objectId, "Take");
        String storeWidgetText = "How many doses ";
        sleepUntil(() -> Rs2Widget.hasWidget(storeWidgetText));
        if (Rs2Widget.hasWidget(storeWidgetText)) {
            Rs2Keyboard.typeString(String.valueOf((amount - Rs2Inventory.count(itemName)) * 4));
            Rs2Keyboard.enter();
            sleepUntil(() -> !Rs2Inventory.hasItem(objectId));
        }
    }

    private boolean overloaded() {
        return Microbot.getClient().getBoostedSkillLevel(Skill.RANGED) != Microbot.getClient().getRealSkillLevel(Skill.RANGED);
    }
}
