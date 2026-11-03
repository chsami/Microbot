package net.runelite.client.plugins.microbot.util.security;

import net.runelite.api.Client;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LoginTest {

    private MockedStatic<Microbot> microbot;
    private WorldService worldService;

    @Before
    public void setUp() {
        Client client = mock(Client.class);
        when(client.getWorldType()).thenReturn(EnumSet.noneOf(net.runelite.api.WorldType.class));
        worldService = mock(WorldService.class);
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getWorldService).thenReturn(worldService);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    private void stubWorlds(List<World> worlds) {
        WorldResult result = mock(WorldResult.class);
        when(result.getWorlds()).thenReturn(worlds);
        when(worldService.getWorlds()).thenReturn(result);
    }

    private static World world(int id, WorldRegion region, WorldType... types) {
        World world = mock(World.class);
        when(world.getId()).thenReturn(id);
        when(world.getRegion()).thenReturn(region);
        when(world.getPlayers()).thenReturn(100);
        EnumSet<WorldType> set = EnumSet.noneOf(WorldType.class);
        set.addAll(Arrays.asList(types));
        when(world.getTypes()).thenReturn(set);
        return world;
    }

    @Test
    public void returnsMembersFallbackWhenOnlyFreeWorldsAreListed() {
        stubWorlds(Collections.singletonList(world(301, WorldRegion.UNITED_STATES_OF_AMERICA)));

        assertEquals(360, Login.getRandomWorld(true));
    }

    @Test
    public void returnsFreeFallbackWhenEveryWorldIsFiltered() {
        stubWorlds(Arrays.asList(
                world(302, WorldRegion.UNITED_STATES_OF_AMERICA, WorldType.MEMBERS),
                world(318, WorldRegion.UNITED_STATES_OF_AMERICA, WorldType.PVP)));

        assertEquals(383, Login.getRandomWorld(false));
    }

    @Test
    public void returnsFallbackWhenNoWorldMatchesTheRegion() {
        stubWorlds(Collections.singletonList(world(302, WorldRegion.UNITED_STATES_OF_AMERICA, WorldType.MEMBERS)));

        assertEquals(360, Login.getRandomWorld(true, WorldRegion.AUSTRALIA));
    }

    @Test
    public void returnsFallbackWhenWorldListIsEmpty() {
        stubWorlds(Collections.emptyList());

        assertEquals(383, Login.getRandomWorld(false));
    }

    @Test
    public void picksAMatchingWorldWhenOneSurvivesTheFilters() {
        stubWorlds(Arrays.asList(
                world(301, WorldRegion.UNITED_STATES_OF_AMERICA),
                world(302, WorldRegion.UNITED_STATES_OF_AMERICA, WorldType.MEMBERS)));

        assertEquals(302, Login.getRandomWorld(true));
    }
}
