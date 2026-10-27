package net.runelite.client.plugins.microbot.util.misc;

import org.junit.Test;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class Rs2UiHelperInterfaceClearanceTest {

    private static final Rectangle VIEWPORT = new Rectangle(0, 0, 1280, 800);
    private static final Rectangle CHATBOX = new Rectangle(0, 600, 520, 200);
    private static final Rectangle SIDE_PANEL = new Rectangle(1040, 460, 240, 340);
    private static final Rectangle MINIMAP = new Rectangle(1100, 0, 180, 170);
    private static final List<Rectangle> INTERFACES = Arrays.asList(CHATBOX, SIDE_PANEL, MINIMAP);

    @Test
    public void anUncoveredClickboxIsKeptWhole() {
        Rectangle stairs = new Rectangle(600, 300, 80, 60);

        assertEquals(stairs, Rs2UiHelper.clearOfInterfaces(stairs, VIEWPORT, INTERFACES));
    }

    @Test
    public void aClickboxUnderTheChatboxIsNotClickable() {
        assertNull(Rs2UiHelper.clearOfInterfaces(new Rectangle(100, 650, 60, 40), VIEWPORT, INTERFACES));
    }

    @Test
    public void onlyThePartAboveTheChatboxButtonRowIsKept() {
        Rectangle trapdoor = new Rectangle(200, 560, 100, 100);

        assertEquals(new Rectangle(200, 560, 100, 36), Rs2UiHelper.clearOfInterfaces(trapdoor, VIEWPORT, INTERFACES));
    }

    @Test
    public void theLargestClearPartBesideTheChatboxCornerIsKept() {
        Rectangle stairs = new Rectangle(480, 560, 100, 100);

        Rectangle clear = Rs2UiHelper.clearOfInterfaces(stairs, VIEWPORT, INTERFACES);

        assertEquals(new Rectangle(524, 560, 56, 100), clear);
        assertFalse(clear.intersects(grown(CHATBOX)));
    }

    @Test
    public void aClickboxBetweenTheSidePanelAndMinimapAvoidsBoth() {
        Rectangle object = new Rectangle(1000, 150, 200, 340);

        Rectangle clear = Rs2UiHelper.clearOfInterfaces(object, VIEWPORT, INTERFACES);

        assertEquals(new Rectangle(1000, 174, 200, 282), clear);
        assertFalse(clear.intersects(grown(SIDE_PANEL)));
        assertFalse(clear.intersects(grown(MINIMAP)));
    }

    @Test
    public void aClickboxOutsideTheViewportIsNotClickable() {
        Rectangle fixedModeViewport = new Rectangle(4, 4, 512, 334);

        assertNull(Rs2UiHelper.clearOfInterfaces(new Rectangle(560, 100, 40, 40), fixedModeViewport, Collections.emptyList()));
        assertEquals(new Rectangle(500, 100, 16, 40),
                Rs2UiHelper.clearOfInterfaces(new Rectangle(500, 100, 40, 40), fixedModeViewport, Collections.emptyList()));
    }

    @Test
    public void aClickboxOverTheTopLeftCornerIsNotMistakenForTheFallbackRectangle() {
        Rectangle object = new Rectangle(-50, -40, 200, 200);

        Rectangle clear = Rs2UiHelper.clearOfInterfaces(object, VIEWPORT, INTERFACES);

        assertEquals(new Rectangle(2, 2, 148, 158), clear);
        assertFalse(Rs2UiHelper.isFallbackRectangle(clear));
        assertNull(Rs2UiHelper.clearOfInterfaces(new Rectangle(-10, -10, 11, 11), VIEWPORT, INTERFACES));
        assertEquals(new Rectangle(0, 50, 30, 40),
                Rs2UiHelper.clearOfInterfaces(new Rectangle(-10, 50, 40, 40), VIEWPORT, INTERFACES));
    }

    @Test
    public void missingInputsAreHandled() {
        Rectangle stairs = new Rectangle(600, 300, 80, 60);

        assertNull(Rs2UiHelper.clearOfInterfaces(null, VIEWPORT, INTERFACES));
        assertNull(Rs2UiHelper.clearOfInterfaces(new Rectangle(600, 300, 0, 60), VIEWPORT, INTERFACES));
        assertEquals(stairs, Rs2UiHelper.clearOfInterfaces(stairs, null, null));
        assertEquals(stairs, Rs2UiHelper.clearOfInterfaces(stairs, VIEWPORT, Arrays.asList(null, new Rectangle())));
    }

    private static Rectangle grown(Rectangle bounds) {
        Rectangle grown = new Rectangle(bounds);
        grown.grow(Rs2UiHelper.INTERFACE_CLEARANCE_PX, Rs2UiHelper.INTERFACE_CLEARANCE_PX);
        return grown;
    }
}
