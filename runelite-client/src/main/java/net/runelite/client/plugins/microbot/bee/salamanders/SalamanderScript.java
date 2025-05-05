package net.runelite.client.plugins.microbot.bee.salamanders;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import static net.runelite.api.gameval.ItemID.ROPE;

public class SalamanderScript extends Script {
    WorldArea AREA_LUMBRIDGE = new WorldArea(3205, 3200, 34, 37, 0);
    WorldArea AREA_VERDE_SALAMANDER = new WorldArea(3531, 3444, 10, 9, 0);
    WorldArea AREA_ROJA_SALAMANDER = new WorldArea(2443, 3217, 13, 12, 0);
    WorldArea AREA_NEGRA_SALAMANDER = new WorldArea(3290, 3663, 14, 16, 0);

    public boolean run() {
        try {
            long startTime = System.currentTimeMillis();
            int hunterLevel = Microbot.getClient().getRealSkillLevel(Skill.HUNTER);

            if (AREA_LUMBRIDGE.contains(Rs2Player.getWorldLocation())) {
                if (Rs2Inventory.count(ROPE) < 5 || Rs2Inventory.count(303) < 5) {
                    Rs2Bank.walkToBank();
                    sleep(2000,3000);
                    Rs2Bank.walkToBankAndUseBank();
                    if(!Rs2Bank.isOpen()){
                    Rs2Bank.openBank();
                    sleepUntil(Rs2Bank::isOpen, 180000);}

                    Rs2Bank.withdrawX(ROPE, 5);
                    if (!sleepUntil(() -> Rs2Inventory.count(ROPE) >= 5, 5000)) {
                        System.out.println("No se pudieron retirar 5 ROPE. Terminando...");
                        shutdown();
                        return false;
                    }

                    Rs2Bank.withdrawX(303, 5);
                    if (!sleepUntil(() -> Rs2Inventory.count(303) >= 5, 5000)) {
                        System.out.println("No se pudieron retirar 5 SMALL FISHING NET. Terminando...");
                        shutdown();
                        return false;
                    }

                    System.out.println("Todos los ítems requeridos fueron retirados con éxito.");
                } else {
                    System.out.println("Ya tienes los ítems requeridos en el inventario.");
                }
            }

            if (hunterLevel < 59 && !AREA_VERDE_SALAMANDER.contains(Rs2Player.getWorldLocation())) {
                Rs2Walker.walkTo(3537, 3448, 0);
            }

            if (hunterLevel > 58 && hunterLevel < 67 && !AREA_ROJA_SALAMANDER.contains(Rs2Player.getWorldLocation())) {
                Rs2Walker.walkTo(2450, 3220, 0);
            }

            if (hunterLevel > 66 && !AREA_NEGRA_SALAMANDER.contains(Rs2Player.getWorldLocation())) {
                Rs2Walker.walkTo(3297, 3672, 0);
            }

            boolean arbolTerminado = Rs2GameObject.findObjectByIdAndDistance(9004, 8) != null
                    || Rs2GameObject.findObjectByIdAndDistance(8996, 8) != null
                    || Rs2GameObject.findObjectByIdAndDistance(8986, 8) != null;
            boolean arbolLibre = Rs2GameObject.findObjectByIdAndDistance(9341, 8) != null
                    || Rs2GameObject.findObjectByIdAndDistance(9000, 8) != null
                    || Rs2GameObject.findObjectByIdAndDistance(8990, 8) != null;

            if (arbolLibre && !enElSuelo()) {
                if (Rs2GameObject.exists(9341)) Rs2GameObject.interact(9341, "Set-trap");
                if (Rs2GameObject.exists(9000) && !siFaltaItemNegra()) Rs2GameObject.interact(9000, "Set-trap");
                if (Rs2GameObject.exists(8990)) Rs2GameObject.interact(8990, "Set-trap");
                sleep(3500, 4500);
            }

            Microbot.log("No se encontró Arbol abierto o hay algo en el suelo.");

            if (arbolTerminado && !enElSuelo()) {
                if (Rs2GameObject.exists(9004)) Rs2GameObject.interact(9004, "Check");
                if (Rs2GameObject.exists(8996)) Rs2GameObject.interact(8996, "Check");
                if (Rs2GameObject.exists(8986)) Rs2GameObject.interact(8986, "Check");
                sleep(2500, 3500);
            }

            if (enElSuelo()) checkFallo();
            if (!arbolLibre || !arbolTerminado) sleep(2500, 3500);

            if (arbolLibre && !enElSuelo()) {
                if (Rs2GameObject.exists(9341)) Rs2GameObject.interact(9341, "Set-trap");
                if (Rs2GameObject.exists(9000) && !siFaltaItemNegra()) Rs2GameObject.interact(9000, "Set-trap");
                if (Rs2GameObject.exists(8990)) Rs2GameObject.interact(8990, "Set-trap");
                sleep(3500, 4500);
            }
            if (!arbolLibre && arbolTerminado && !enElSuelo()) {
                if (Rs2GameObject.exists(9004)) Rs2GameObject.interact(9004, "Check");
                if (Rs2GameObject.exists(8996)) Rs2GameObject.interact(8996, "Check");
                if (Rs2GameObject.exists(8986)) Rs2GameObject.interact(8986, "Check");
                sleep(2500, 3500);
            } else {
                if (Rs2Inventory.getEmptySlots() < 3) {
                    while (true) {
                        if (System.currentTimeMillis() - startTime > 15000) {
                            System.out.println("El bucle se ha detenido porque excedió el límite de 15 segundos.");
                            break;
                        }

                        if (Rs2Inventory.contains(10147) || Rs2Inventory.contains(10148) || Rs2Inventory.contains(10149)) {
                            for (Rs2ItemModel item : Rs2Inventory.items()) {
                                if (item.getId() == 10147 || item.getId() == 10148 || item.getId() == 10149) {
                                    Rs2Inventory.interact(item, "Release");
                                    sleep(150, 350);
                                }
                            }
                        } else {
                            break;
                        }
                    }
                }
                if (enElSuelo()) checkFallo();
                if (!arbolLibre || !arbolTerminado) sleep(2500, 3500);
            }

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            System.out.println("Total time for loop " + totalTime);
            return true;

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            return false;
        }
    }

public boolean enElSuelo() {
    return Rs2GroundItem.exists(ROPE, 7) || Rs2GroundItem.exists(303, 7);
}

public boolean siFaltaItemNegra() {
    return !Rs2Inventory.contains(ROPE) ;
}

private void checkFallo() {
    int hunterLevel = Microbot.getClient().getRealSkillLevel(Skill.HUNTER);

    // Condición 1: Hunter menor a 40
    if (hunterLevel < 40) {
        if (Rs2Inventory.count(ROPE) < 3 && Rs2GroundItem.exists(ROPE, 7)) {
            manejarRecoleccion();
        }
    }

    // Condición 2: Hunter entre 40 y 59
    if (hunterLevel > 39 && hunterLevel < 60) {
        if (Rs2Inventory.count(ROPE) < 2 && Rs2GroundItem.exists(ROPE, 7)) {
            manejarRecoleccion();
        }
    }

    // Condición 3: Hunter mayor a 59
    if (hunterLevel > 59) {
        if (Rs2GroundItem.exists(ROPE, 7) || Rs2GroundItem.exists(303, 7)) {
            manejarRecoleccionConCantidad();
        }
    }
}

// Manejar la recolección simple (sin cantidad inicial)
private void manejarRecoleccion() {
    // Recoger ítem 303
    Rs2GroundItem.loot(303);
    sleepUntil(() -> Rs2Inventory.contains(303), 5000);

    // Recoger ROPE
    Rs2GroundItem.loot(ROPE);
    sleepUntil(() -> Rs2Inventory.contains(ROPE), 5000);
}

// Manejar la recolección con cantidades iniciales
private void manejarRecoleccionConCantidad() {
    // Obtener la cantidad inicial de 303
    int cantidadInicial303 = Rs2Inventory.itemQuantity(303);

    // Recoger ítem 303
    Rs2GroundItem.loot(303);
    sleepUntil(() -> Rs2Inventory.itemQuantity(303) > cantidadInicial303, 5000);
    System.out.println("Cantidad actual de 303: " + Rs2Inventory.itemQuantity(303));

    // Obtener la cantidad inicial de ROPE
    int cantidadInicialRope = Rs2Inventory.itemQuantity(ROPE);

    // Recoger ROPE
    Rs2GroundItem.loot(ROPE);
    sleepUntil(() -> Rs2Inventory.itemQuantity(ROPE) > cantidadInicialRope, 5000);
    System.out.println("Cantidad actual de ROPE: " + Rs2Inventory.itemQuantity(ROPE));
}


@Override
public void shutdown() {
    super.shutdown();
}
}
