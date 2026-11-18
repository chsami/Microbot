package net.runelite.client.plugins.microbot.util.misc;

import org.junit.Test;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class Rs2UiHelperClearClickAreaTest {

    private static final Rectangle VIEWPORT = new Rectangle(0, 0, 1280, 800);
    private static final Rectangle CHATBOX = new Rectangle(0, 600, 520, 200);
    private static final Rectangle SIDE_PANEL = new Rectangle(1040, 460, 240, 340);
    private static final Rectangle MINIMAP = new Rectangle(1100, 0, 180, 170);
    private static final List<Rectangle> INTERFACES = Arrays.asList(CHATBOX, SIDE_PANEL, MINIMAP);
    private static final UnaryOperator<Rectangle> CLEAR =
            clickbox -> Rs2UiHelper.clearClickArea(clickbox, VIEWPORT, INTERFACES);

    @Test
    public void anNpcInTheOpenIsClickedWithoutTurningTheCamera() {
        Rectangle banker = new Rectangle(600, 300, 40, 80);
        AtomicInteger turns = new AtomicInteger();

        assertEquals(banker, Rs2UiHelper.clearClickArea(() -> banker, CLEAR, turns::incrementAndGet, "Banker", 0));
        assertEquals(0, turns.get());
    }

    @Test
    public void anNpcHalfUnderTheInventoryIsClickedOnItsClearPart() {
        Rectangle guard = new Rectangle(1000, 500, 80, 100);
        AtomicInteger turns = new AtomicInteger();

        Rectangle clickArea = Rs2UiHelper.clearClickArea(() -> guard, CLEAR, turns::incrementAndGet, "Guard", 0);

        assertEquals(new Rectangle(1000, 500, 36, 100), clickArea);
        assertFalse(clickArea.intersects(SIDE_PANEL));
        assertEquals(0, turns.get());
    }

    @Test
    public void anNpcUnderTheInventoryIsClickedWhereItStandsAfterTheCameraTurn() {
        Rectangle covered = new Rectangle(1100, 550, 60, 90);
        Rectangle afterTurn = new Rectangle(620, 200, 60, 90);
        AtomicInteger turns = new AtomicInteger();

        Rectangle clickArea = Rs2UiHelper.clearClickArea(() -> turns.get() == 0 ? covered : afterTurn, CLEAR,
                turns::incrementAndGet, "Goblin", 0);

        assertEquals(afterTurn, clickArea);
        assertEquals(1, turns.get());
    }

    @Test
    public void aGroundItemStillUnderTheInventoryAfterTheCameraTurnIsNotClicked() {
        Rectangle covered = new Rectangle(1100, 550, 60, 40);
        AtomicInteger turns = new AtomicInteger();

        assertNull(Rs2UiHelper.clearClickArea(() -> covered, CLEAR, turns::incrementAndGet, "Bones", 0));
        assertEquals(1, turns.get());
    }

    @Test
    public void aTargetWithoutAClickboxKeepsItsFallbackRectangle() {
        Rectangle fallback = new Rectangle(1, 1, 1280, 800);
        AtomicInteger turns = new AtomicInteger();

        assertSame(fallback, Rs2UiHelper.clearClickArea(() -> fallback, CLEAR, turns::incrementAndGet, "Banker", 0));
        assertEquals(0, turns.get());
    }

    @Test
    public void hoveringAnNpcUnderTheInventoryDoesNotMoveThePointerOntoIt() {
        assertNull(Rs2UiHelper.clearHoverArea(new Rectangle(1100, 550, 60, 90), VIEWPORT, INTERFACES));
    }

    @Test
    public void hoveringAnNpcHalfUnderTheInventoryStaysOnItsClearPart() {
        Rectangle hoverArea = Rs2UiHelper.clearHoverArea(new Rectangle(1000, 500, 80, 100), VIEWPORT, INTERFACES);

        assertEquals(new Rectangle(1000, 500, 36, 100), hoverArea);
        assertFalse(hoverArea.intersects(SIDE_PANEL));
    }

    @Test
    public void hoveringATargetWithoutAClickboxIsSkipped() {
        assertNull(Rs2UiHelper.clearHoverArea(new Rectangle(1, 1, 1280, 800), VIEWPORT, INTERFACES));
        assertNull(Rs2UiHelper.clearHoverArea(null, VIEWPORT, INTERFACES));
    }

    @Test
    public void aTargetWithoutANameIsCalledTheTargetInTheCoveredWarning() {
        assertEquals("the target", Rs2UiHelper.coveredTargetName(null));
        assertEquals("the target", Rs2UiHelper.coveredTargetName(""));
        assertEquals("the target", Rs2UiHelper.coveredTargetName("null"));
        assertEquals("Goblin", Rs2UiHelper.coveredTargetName("Goblin"));
    }

    @Test
    public void theCoveredWarningIsLoggedAtMostOncePerInterval() {
        long start = 1_000_000_000L;
        Rs2UiHelper.shouldWarnCovered(start);

        assertFalse(Rs2UiHelper.shouldWarnCovered(start + Rs2UiHelper.COVERED_WARN_INTERVAL_MS - 1));
        assertTrue(Rs2UiHelper.shouldWarnCovered(start + Rs2UiHelper.COVERED_WARN_INTERVAL_MS));
    }
}
