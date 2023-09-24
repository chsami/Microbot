package net.runelite.client.plugins.microbot.nmz;

import net.runelite.api.NpcID;
import net.runelite.api.ObjectID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.VirtualKeyboard;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

import static net.runelite.api.Varbits.NMZ_ABSORPTION;

public class NmzScript extends Script {

    public static double version = 1.0;

    public boolean run(NmzConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;
            try {
                boolean isOutsideNmz = Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(new WorldPoint(2602, 3116, 0)) < 20;
                boolean hasStartedNmz = Microbot.getVarbitValue(3946) > 0;
                if (hasStartedNmz == false) {
                    if (isOutsideNmz) {
                        Rs2Npc.interact(NpcID.DOMINIC_ONION, "Dream");
                        sleep(1000);
                        Rs2Widget.clickWidget("Previous:");
                        sleep(1000);
                        Rs2Widget.clickWidget("Click here to continue");
                        sleep(1000);
                        VirtualKeyboard.typeString("1");
                        VirtualKeyboard.enter();
                    } else {
                        //inside nmz
                        if (Microbot.getClient().getLocalPlayer().isInteracting()) {
                           // Microbot.toggleSpecialAttack(25);
                        }
                        if (Microbot.getClient().getBoostedSkillLevel(Skill.ATTACK) == Microbot.getClient().getRealSkillLevel(Skill.ATTACK)
                                && Rs2Inventory.hasItemContains("overload")) {
                            Rs2Inventory.interact(new String[] {"overload (4)", "overload (3)", "overload (2)", "overload (1)"});
                            sleep(5000);
                        }
                        if (Microbot.getVarbitValue(NMZ_ABSORPTION) < Random.random(300, 600) && Rs2Inventory.hasItemContains("absorption")) {
                            for (int i = 0; i < Random.random(1, 5); i++) {
                                Rs2Inventory.interact(new String[] {"absorption (4)", "absorption (3)", "absorption (2)", "absorption (1)"});
                                sleep(600, 1000);
                            }
                        }
                    }
                } else {
                    if (isOutsideNmz) {
                        if (!Rs2Inventory.hasItemAmountExact("overload (4)", 8)) {
                            Rs2GameObject.interact(ObjectID.OVERLOAD_POTION, "Store");
                            sleep(1000);
                            if (Rs2Widget.hasWidget("Store all your overload potion?")) {
                                VirtualKeyboard.typeString("1");
                                VirtualKeyboard.enter();
                            }
                            Rs2GameObject.interact(ObjectID.OVERLOAD_POTION, "Take");
                            sleep(1000);
                            if (Rs2Widget.hasWidget("How many doses of overload")) {
                                VirtualKeyboard.typeString("32");
                                VirtualKeyboard.enter();
                            }
                        }
                        if (!Rs2Inventory.hasItemAmountExact("absorption (4)", 20)) {
                            Rs2GameObject.interact(ObjectID.ABSORPTION_POTION, "Store");
                            sleep(1000);
                            if (Rs2Widget.hasWidget("Store all your absorption potion?")) {
                                VirtualKeyboard.typeString("1");
                                VirtualKeyboard.enter();
                            }
                            Rs2GameObject.interact(ObjectID.ABSORPTION_POTION, "Take");
                            sleep(1000);
                            if (Rs2Widget.hasWidget("How many doses of absorption")) {
                                VirtualKeyboard.typeString("80");
                                VirtualKeyboard.enter();
                            }
                        }
                        if (Rs2Inventory.hasItemAmount("overload (4)", 8) && Rs2Inventory.hasItemAmount("absorption (4)", 20)) {
                            Rs2GameObject.interact(26291, "drink");
                            sleepUntil(() -> Rs2Widget.hasWidget("Nightmare zone"));
                            Rs2Widget.clickWidget(8454150);
                            sleep(5000);
                        }
                    }
                }




            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }
}
