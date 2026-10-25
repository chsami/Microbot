package net.runelite.client.plugins.microbot.util.cannon;

import net.runelite.api.Constants;
import net.runelite.api.ObjectID;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class Rs2Cannon {

    public static boolean repair() {
        Rs2TileObjectModel brokenCannon = nearestCannon(ObjectID.BROKEN_MULTICANNON_14916, ObjectID.BROKEN_MULTICANNON_43028);

        if (brokenCannon == null) return false;

        if (!isOwnedCannon(brokenCannon)) return false;

        Microbot.status = "Repairing Cannon";

        brokenCannon.click("Repair");
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

        Rs2TileObjectModel cannon = nearestCannon(ObjectID.DWARF_MULTICANNON, ObjectID.DWARF_MULTICANNON_43027);
        if (cannon == null) return false;

        if (!isOwnedCannon(cannon)) return false;
		Microbot.pauseAllScripts.compareAndSet(false, true);
        cannon.click("Fire");
        Rs2Player.waitForWalking();
        sleep(1200);
        cannon.click("Fire");
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getVarpValue(VarPlayer.CANNON_AMMO)).orElse(0) > Rs2Random.between(10, 15));
		Microbot.pauseAllScripts.compareAndSet(true, false);
        return true;
    }

    private static Rs2TileObjectModel nearestCannon(int... ids) {
        return Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .ofType(TileObjectType.GAME)
                .withIds(ids)
                .nearestOnClientThread(Constants.SCENE_SIZE / 2);
    }

    private static boolean isOwnedCannon(Rs2TileObjectModel cannon) {
        int ownedCoord = Microbot.getVarbitPlayerValue(VarPlayerID.OWNEDMCANNON);
        if (ownedCoord <= 0) return false;
        WorldPoint location = cannon.getRuneLiteWorldLocation();
        if (location == null) return false;
        WorldPoint southWest = new WorldPoint(location.getX() - 1, location.getY() - 1, location.getPlane());
        return southWest.equals(WorldPoint.fromCoord(ownedCoord));
    }

}
