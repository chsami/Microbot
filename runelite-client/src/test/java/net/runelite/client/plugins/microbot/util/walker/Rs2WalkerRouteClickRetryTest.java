package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class Rs2WalkerRouteClickRetryTest {

    private static final WorldPoint TARGET = new WorldPoint(3130, 3631, 0);
    private static final WorldPoint BEFORE = new WorldPoint(3061, 3877, 0);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private WorldPoint originalTarget;

    @Before
    public void setUp() {
        Client client = mock(Client.class);
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        player = Mockito.mockStatic(Rs2Player.class);
        player.when(Rs2Player::getWorldLocation).thenReturn(BEFORE);
        originalTarget = Rs2Walker.currentTarget;
        Rs2Walker.currentTarget = TARGET;
    }

    @After
    public void tearDown() {
        Rs2Walker.currentTarget = originalTarget;
        player.close();
        microbot.close();
    }

    @Test
    public void lateMovementStartDoesNotTriggerRetryClick() {
        AtomicInteger polls = new AtomicInteger();
        player.when(Rs2Player::isMoving).thenAnswer(inv -> polls.incrementAndGet() >= 3);

        assertFalse(Rs2Walker.routeClickStillUnanswered(TARGET, BEFORE));
        assertTrue(polls.get() >= 3);
    }

    @Test
    public void positionChangeWithoutPoseDoesNotTriggerRetryClick() {
        player.when(Rs2Player::isMoving).thenReturn(false);
        AtomicInteger reads = new AtomicInteger();
        WorldPoint stepped = new WorldPoint(3062, 3876, 0);
        player.when(Rs2Player::getWorldLocation).thenAnswer(inv -> reads.incrementAndGet() >= 3 ? stepped : BEFORE);

        assertFalse(Rs2Walker.routeClickStillUnanswered(TARGET, BEFORE));
    }

    @Test
    public void clickThatNeverMovesThePlayerIsRetriedAfterTheStartGrace() {
        player.when(Rs2Player::isMoving).thenReturn(false);

        long started = System.currentTimeMillis();
        assertTrue(Rs2Walker.routeClickStillUnanswered(TARGET, BEFORE));
        assertTrue(System.currentTimeMillis() - started >= 1_000);
    }

    @Test
    public void alreadyMovingSkipsTheGraceAndTheRetry() {
        player.when(Rs2Player::isMoving).thenReturn(true);

        long started = System.currentTimeMillis();
        assertFalse(Rs2Walker.routeClickStillUnanswered(TARGET, BEFORE));
        assertTrue(System.currentTimeMillis() - started < 600);
    }

    @Test
    public void cancelledWalkDoesNotRetry() {
        player.when(Rs2Player::isMoving).thenReturn(false);
        Rs2Walker.currentTarget = null;

        assertFalse(Rs2Walker.routeClickStillUnanswered(TARGET, BEFORE));
    }
}
