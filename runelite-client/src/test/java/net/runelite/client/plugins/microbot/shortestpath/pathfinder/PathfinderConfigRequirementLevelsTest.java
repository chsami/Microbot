package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PathfinderConfigRequirementLevelsTest {

    @Test
    public void readsRequirementLevelsIntoCallerOwnedArray() {
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(50);
        when(client.getBoostedSkillLevel(Skill.AGILITY)).thenReturn(73);
        when(client.getTotalLevel()).thenReturn(1500);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getCombatLevel()).thenReturn(96);
        when(client.getVarpValue(VarPlayer.QUEST_POINTS)).thenReturn(120);

        int[] levels = new int[Transport.REQUIREMENT_LEVEL_COUNT];
        PathfinderConfig.readRequirementLevels(client, levels);

        assertEquals(73, levels[Skill.AGILITY.ordinal()]);
        assertEquals(50, levels[Skill.ATTACK.ordinal()]);
        assertEquals(1500, levels[Transport.TOTAL_LEVEL_INDEX]);
        assertEquals(96, levels[Transport.COMBAT_LEVEL_INDEX]);
        assertEquals(120, levels[Transport.QUEST_POINTS_INDEX]);
    }

    @Test
    public void missingLocalPlayerReportsZeroCombatLevel() {
        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(null);

        int[] levels = new int[Transport.REQUIREMENT_LEVEL_COUNT];
        levels[Transport.COMBAT_LEVEL_INDEX] = 99;
        PathfinderConfig.readRequirementLevels(client, levels);

        assertEquals(0, levels[Transport.COMBAT_LEVEL_INDEX]);
    }
}
