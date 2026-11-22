package net.runelite.client.plugins.microbot.util.security;

import net.runelite.api.Client;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;
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
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HardcoreMainWorldFilterTest {

    private static final int HARDCORE_WORLD = 659;
    private static final int NORMAL_WORLD = 422;

    private MockedStatic<Microbot> microbot;
    private Client client;

    @Before
    public void setUp() {
        client = mock(Client.class);
        onNormalWorld();

        WorldResult result = mock(WorldResult.class);
        when(result.getWorlds()).thenReturn(Arrays.asList(
                world(HARDCORE_WORLD, EnumSet.of(WorldType.MEMBERS, WorldType.HARDCORE_MAIN)),
                world(NORMAL_WORLD, EnumSet.of(WorldType.MEMBERS))));
        WorldService worldService = mock(WorldService.class);
        when(worldService.getWorlds()).thenReturn(result);

        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getWorldService).thenReturn(worldService);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    @Test
    public void loginRandomWorldNeverPicksAHardcoreMainWorld() {
        for (int i = 0; i < 50; i++) {
            assertEquals(NORMAL_WORLD, Login.getRandomWorld(true));
        }
    }

    @Test
    public void loginNextWorldSkipsHardcoreMainWorlds() {
        assertEquals(NORMAL_WORLD, Login.getNextWorld(true));
    }

    @Test
    public void loginManagerRandomWorldNeverPicksAHardcoreMainWorld() {
        for (int i = 0; i < 50; i++) {
            assertEquals(NORMAL_WORLD, LoginManager.getRandomWorld(true));
        }
    }

    @Test
    public void loginManagerNextWorldSkipsHardcoreMainWorlds() {
        assertEquals(NORMAL_WORLD, LoginManager.getNextWorld(true));
    }

    @Test
    public void hardcoreMainWorldIsNotAccessibleFromANormalWorld() {
        assertFalse(Rs2WorldUtil.isWorldAccessible(
                world(HARDCORE_WORLD, EnumSet.of(WorldType.MEMBERS, WorldType.HARDCORE_MAIN)), true, false));
        assertTrue(Rs2WorldUtil.isWorldAccessible(
                world(NORMAL_WORLD, EnumSet.of(WorldType.MEMBERS)), true, false));
    }

    @Test
    public void randomWorldStaysOnHardcoreMainWorldsWhenClientIsOnOne() {
        onHardcoreMainWorld();
        for (int i = 0; i < 50; i++) {
            assertEquals(HARDCORE_WORLD, Login.getRandomWorld(true));
            assertEquals(HARDCORE_WORLD, LoginManager.getRandomWorld(true));
        }
    }

    @Test
    public void nextWorldStaysOnHardcoreMainWorldsWhenClientIsOnOne() {
        onHardcoreMainWorld();
        assertEquals(HARDCORE_WORLD, Login.getNextWorld(true));
        assertEquals(HARDCORE_WORLD, LoginManager.getNextWorld(true));
    }

    @Test
    public void onlyHardcoreMainWorldsAreAccessibleWhenClientIsOnOne() {
        onHardcoreMainWorld();
        assertTrue(Rs2WorldUtil.isWorldAccessible(
                world(HARDCORE_WORLD, EnumSet.of(WorldType.MEMBERS, WorldType.HARDCORE_MAIN)), true, false));
        assertFalse(Rs2WorldUtil.isWorldAccessible(
                world(NORMAL_WORLD, EnumSet.of(WorldType.MEMBERS)), true, false));
    }

    private void onNormalWorld() {
        when(client.getWorldType()).thenReturn(EnumSet.of(net.runelite.api.WorldType.MEMBERS));
        when(client.getWorld()).thenReturn(NORMAL_WORLD);
    }

    private void onHardcoreMainWorld() {
        when(client.getWorldType()).thenReturn(EnumSet.of(net.runelite.api.WorldType.MEMBERS, net.runelite.api.WorldType.HARDCORE_MAIN));
        when(client.getWorld()).thenReturn(HARDCORE_WORLD);
    }

    private static World world(int id, EnumSet<WorldType> types) {
        return World.builder().id(id).types(types).players(500).region(WorldRegion.UNITED_STATES_OF_AMERICA).address("x").build();
    }
}
