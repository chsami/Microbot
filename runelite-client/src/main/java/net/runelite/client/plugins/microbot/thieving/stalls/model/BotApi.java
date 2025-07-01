package net.runelite.client.plugins.microbot.thieving.stalls.model;

import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

public class BotApi {
    private static final String STEAL_ACTION = "Steal-from";

    public boolean walkTo(final WorldPoint worldPoint)
    {
        return Rs2Walker.walkTo(worldPoint, 0);
    }

    public GameObject getGameObject(final int id, final WorldPoint worldPoint)
    {
        return Rs2GameObject.findObject(id, worldPoint);
    }

    public void steal(final GameObject gameObject)
    {
        Rs2GameObject.interact(gameObject, STEAL_ACTION);
    }

    public void dropAll(int... ids)
    {
        Rs2Inventory.dropAll(ids);
    }

    public void sleepUntilNextTick()
    {
        Rs2Player.waitForXpDrop(Skill.THIEVING);
    }

    public boolean isInventoryFull()
    {
        return Rs2Inventory.isFull();
    }
}
