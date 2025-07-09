package net.runelite.client.plugins.microbot.moonsOfPeril.handlers;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.State;
import net.runelite.client.plugins.microbot.moonsOfPeril.moonsOfPerilConfig;
import net.runelite.client.plugins.microbot.moonsOfPeril.moonsOfPerilScript;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.List;

public class DeathHandler implements BaseHandler {
    private final moonsOfPerilScript script = null;
    private final boolean shutdownOnDeath;
    private final boolean debugLogging;

    public DeathHandler(moonsOfPerilConfig cfg) {
        this.shutdownOnDeath = cfg.shutdownOnDeath();
        this.debugLogging = cfg.debugLogging();
    }

    @Override
    public boolean validate() {
        return Rs2Player.isNearArea(new WorldPoint(3221, 3219, 0), 30); // true if near lumbridge spawn point
    }

    @Override
    public State execute() {
        if (debugLogging) {Microbot.log("Player detected near Lumbridge spawn. Starting death handler sequence");}
        if (shutdownOnDeath) {
            Microbot.showMessage("Script shut down due to player death");
            script.shutdown();
        }

        if (retrieveTravelItems()) {
            sleep(1_200);
            if (retrieveDeathItems()) {
                sleep(1_200);
                equipDeathItems();
                }
            }
        return null;
    }

    /**
     * Travels to nearest bank and retrieves runes required for Civitas illa Fortis teleport.
     */
    private boolean retrieveTravelItems() {
        BreakHandlerScript.setLockState(true);
        Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 1);
        sleep(600);
        if (Rs2Bank.openBank()) {
            sleep(600);
            Rs2Bank.withdrawOne(ItemID.FIRERUNE);
            sleep(600);
            Rs2Bank.withdrawOne(ItemID.EARTHRUNE);
            sleep(600);
            Rs2Bank.withdrawX(ItemID.LAWRUNE, 2);
            sleep(600);
            Rs2Bank.closeBank();
            BreakHandlerScript.setLockState(false);
            return true;
        }
        BreakHandlerScript.setLockState(false);
        return false;
    }

    private boolean retrieveDeathItems() {
        WorldPoint graveLocation = new WorldPoint(1440, 9626, 1);
        if (debugLogging) {Microbot.log("Attempting to walk back to grave site");}
        Rs2Walker.walkTo(graveLocation, 2);
        sleepUntil(() -> (Rs2Player.getWorldLocation().distanceTo(graveLocation) <= 3), 60_000);
        if (Rs2Npc.interact(NpcID.GRAVESTONE_DEFAULT, "Loot")) {
            if (debugLogging) {Microbot.log("Successfully looted gravestone");}
            return true;
        }
        return false;
    }

    private void equipDeathItems() {
        if (debugLogging) {Microbot.log("Attempting to equip items retrieved");}
        List<Rs2ItemModel> items = Rs2Inventory.all();
        for (Rs2ItemModel i:items) {
            Rs2Inventory.equip(i.getId());
            sleep(300);
        }
    }
}
