package net.runelite.client.plugins.microbot.util.npc;

import net.runelite.api.NPC;
import net.runelite.client.game.npcoverlay.HighlightedNpc;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;

@SuppressWarnings("deprecation")
public class Rs2NpcNullNpcTest {

    @Test
    public void pickpocketReturnsFalseForNullNpc() {
        assertFalse(Rs2Npc.pickpocket((NPC) null));
    }

    @Test
    public void pickpocketReturnsFalseForNullHighlightedNpc() {
        Map<NPC, HighlightedNpc> highlighted = new HashMap<>();
        highlighted.put(null, null);
        assertFalse(Rs2Npc.pickpocket(highlighted));
    }

    @Test
    public void interactWithActionReturnsFalseForNullNpc() {
        assertFalse(Rs2Npc.interact((NPC) null, "Talk-to"));
    }

    @Test
    public void interactReturnsFalseForNullNpc() {
        assertFalse(Rs2Npc.interact((NPC) null));
    }
}
