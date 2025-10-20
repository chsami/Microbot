package net.runelite.client.plugins.microbot.util.gameobject;

import net.runelite.api.ObjectID;
import net.runelite.api.TileObject;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.cannon.CannonPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class Rs2Cannon {

    public static boolean repair() {
        TileObject brokenCannon = Rs2GameObject.findObject(new Integer[]{ObjectID.BROKEN_MULTICANNON_14916, ObjectID.BROKEN_MULTICANNON_43028});

        if (brokenCannon == null) return false;

        // Create centered WorldArea (3x3 area with cannon at center)
        WorldArea cannonLocation = new WorldArea(
            brokenCannon.getWorldLocation().getX() - 1, 
            brokenCannon.getWorldLocation().getY() - 1, 
            3, 3, 
            brokenCannon.getWorldLocation().getPlane()
        );
        if (!cannonLocation.toWorldPoint().equals(CannonPlugin.getCannonPosition().toWorldPoint())) return false;

        Microbot.status = "Repairing Cannon";

        Rs2GameObject.interact(brokenCannon, "Repair");
        sleepUntil(() -> !Rs2GameObject.exists(brokenCannon.getId()),5000);
        return true;
    }

    public static boolean refill() {
        return refill(Rs2Random.between(10, 15));
    }

    public static boolean refill(int cannonRefillAmount) {
        if (!Rs2Inventory.hasItemAmount("cannonball", 15, true)) {
            System.out.println("Not enough cannonballs!");
            return false;
        }

        int cannonBallsLeft = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getVarpValue(VarPlayer.CANNON_AMMO)).orElse(0);

        if (cannonBallsLeft > cannonRefillAmount) return false;

        Microbot.status = "Refilling Cannon";

        TileObject cannon = Rs2GameObject.findObject(new Integer[]{ObjectID.DWARF_MULTICANNON, ObjectID.DWARF_MULTICANNON_43027});
        if (cannon == null) return false;

        // Create centered WorldArea (3x3 area with cannon at center)
        WorldArea cannonLocation = new WorldArea(
            cannon.getWorldLocation().getX() - 1, 
            cannon.getWorldLocation().getY() - 1, 
            3, 3, 
            cannon.getWorldLocation().getPlane()
        );
        if (!cannonLocation.toWorldPoint().equals(CannonPlugin.getCannonPosition().toWorldPoint())) return false;
		Microbot.pauseAllScripts.compareAndSet(false, true);
        Rs2GameObject.interact(cannon, "Fire");
        Rs2Player.waitForWalking();
        sleep(1200);
        Rs2GameObject.interact(cannon, "Fire");
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getVarpValue(VarPlayer.CANNON_AMMO)).orElse(0) > Rs2Random.between(10, 15));
		Microbot.pauseAllScripts.compareAndSet(true, false);
        return true;
    }

    public static boolean pickup() {

        Microbot.status = "Picking up Cannon";

        TileObject cannon = Rs2GameObject.findObject(new Integer[]{ObjectID.DWARF_MULTICANNON, ObjectID.DWARF_MULTICANNON_43027});
        if (cannon == null) return false;

        // Create centered WorldArea (3x3 area with cannon at center)
        WorldArea cannonLocation = new WorldArea(
                cannon.getWorldLocation().getX() - 1,
                cannon.getWorldLocation().getY() - 1,
                3, 3,
                cannon.getWorldLocation().getPlane()
        );
        if (!cannonLocation.toWorldPoint().equals(CannonPlugin.getCannonPosition().toWorldPoint())) return false;
        Microbot.pauseAllScripts.compareAndSet(false, true);
        int attempts = 0;
        while (Rs2GameObject.exists(cannon.getId()) && Rs2Inventory.emptySlotCount() >= 4 && attempts < 3){
            Rs2GameObject.interact(cannon, "Pick-up");
            sleepUntil(() -> !Rs2GameObject.exists(cannon.getId()),5000);
            if(!Rs2GameObject.exists(cannon.getId())){
                return true;
            }
            attempts++;
        }
        Microbot.pauseAllScripts.compareAndSet(true, false);
        return false;
    }

    public static boolean start() {
        if (!Rs2Inventory.hasItemAmount("cannonball", 30, true)) {
            //System.out.println("Not enough cannonballs!");
            return false;
        }

        Microbot.status = "Starting up Cannon";

        TileObject cannon = Rs2GameObject.findObject(new Integer[]{ObjectID.DWARF_MULTICANNON, ObjectID.DWARF_MULTICANNON_43027});
        if (cannon == null) return false;

        // Create centered WorldArea (3x3 area with cannon at center)
        WorldArea cannonLocation = new WorldArea(
                cannon.getWorldLocation().getX() - 1,
                cannon.getWorldLocation().getY() - 1,
                3, 3,
                cannon.getWorldLocation().getPlane()
        );
        if (!cannonLocation.toWorldPoint().equals(CannonPlugin.getCannonPosition().toWorldPoint())) return false;
        int attempts = 0;
        while (Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getVarpValue(VarPlayerID.MCANNONMULTI)).orElse(0) == 0 && attempts < 3){
            Microbot.log("Starting Cannon");
            Rs2GameObject.interact(cannon, "Fire");
            sleepUntil(()-> Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getVarpValue(VarPlayerID.MCANNONMULTI)).orElse(0) == 1048576);
            sleep(250);
            if (Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getVarpValue(VarPlayerID.MCANNONMULTI)).orElse(0) == 1048576){
                return true;
            }
            attempts++;
        }
        return false;
    }
}
