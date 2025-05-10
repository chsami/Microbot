package net.runelite.client.plugins.microbot.holidayevent;

import net.runelite.api.events.GameTick;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

public class CollectSnow {
    private static long randomDelay;

    public static boolean nearTheSnow() {
        return Rs2GameObject.findObjectByIdAndDistance(19035, 1) != null;
    }

    public static void onGameTick(GameTick event) {
        if (Rs2Player.checkIdleLogout(randomDelay)) {
            randomDelay = Rs2Random.between(1000,3000);
            if (Rs2GameObject.getGameObjects(obj -> obj.getId() == 19035) != null && nearTheSnow()) {
                Rs2GameObject.interact(19035, "Take");
                Microbot.log("Done tooken snow cus about to log");
            } else {
                Microbot.log("Restart plugin 1 tile to snow");}
            }
        }
    }
