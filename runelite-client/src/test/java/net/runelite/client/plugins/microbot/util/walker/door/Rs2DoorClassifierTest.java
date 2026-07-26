package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.ObjectComposition;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless tests for {@link Rs2DoorClassifier} — the pure door name/action heuristics the walker uses to
 * decide what is a door and which menu action walks through it. Getting these under test is the "harness
 * first" prerequisite before the door recovery cascade is unified into the P2 obstacle model
 * (docs/walker-p2-unification.md): every walk-through decision the DoorResolver will make ultimately routes
 * through here, so the classification must be pinned down independently of a live client.
 */
public class Rs2DoorClassifierTest {

    private static ObjectComposition compWithActions(String... actions) {
        ObjectComposition comp = mock(ObjectComposition.class);
        when(comp.getActions()).thenReturn(actions);
        return comp;
    }

    // --- isDoorLikeGameObjectName ------------------------------------------------------------------

    @Test
    public void recognizesDoorLikeNames() {
        assertTrue(Rs2DoorClassifier.isDoorLikeGameObjectName("Door"));
        assertTrue(Rs2DoorClassifier.isDoorLikeGameObjectName("Large door"));
        assertTrue(Rs2DoorClassifier.isDoorLikeGameObjectName("Gate"));
        assertTrue(Rs2DoorClassifier.isDoorLikeGameObjectName("Portcullis"));
        assertTrue(Rs2DoorClassifier.isDoorLikeGameObjectName("Ancient barrier"));
        assertTrue(Rs2DoorClassifier.isDoorLikeGameObjectName("Sturdy stile"));
        assertTrue(Rs2DoorClassifier.isDoorLikeGameObjectName("Archway"));
        assertTrue(Rs2DoorClassifier.isDoorLikeGameObjectName("Cattlegate"));
    }

    @Test
    public void fenceMatchesOnlyAsAWholeWord() {
        // The regression the whole-word guard exists for: "Defence"/"defensive" contain the substring
        // "fence" but are NOT doors and must not be treated as such.
        assertTrue("standalone fence is door-like", Rs2DoorClassifier.isDoorLikeGameObjectName("Wooden fence"));
        assertFalse("'Defence' must not match via the 'fence' substring",
                Rs2DoorClassifier.isDoorLikeGameObjectName("Defence"));
        assertFalse(Rs2DoorClassifier.isDoorLikeGameObjectName("Defensive wall"));
    }

    @Test
    public void rejectsNonDoorNamesAndNull() {
        assertFalse(Rs2DoorClassifier.isDoorLikeGameObjectName("Wall"));
        assertFalse(Rs2DoorClassifier.isDoorLikeGameObjectName("Tree"));
        assertFalse(Rs2DoorClassifier.isDoorLikeGameObjectName(null));
    }

    // --- isNullOrPlaceholderObjectName -------------------------------------------------------------

    @Test
    public void treatsNullEmptyAndLiteralNullAsPlaceholder() {
        assertTrue(Rs2DoorClassifier.isNullOrPlaceholderObjectName(null));
        assertTrue(Rs2DoorClassifier.isNullOrPlaceholderObjectName(""));
        assertTrue(Rs2DoorClassifier.isNullOrPlaceholderObjectName("   "));
        assertTrue(Rs2DoorClassifier.isNullOrPlaceholderObjectName("null"));
        assertTrue(Rs2DoorClassifier.isNullOrPlaceholderObjectName("NULL"));
        assertFalse(Rs2DoorClassifier.isNullOrPlaceholderObjectName("Door"));
    }

    // --- doorActionPriorityIndex + isDoorCloseOrShutAction -----------------------------------------

    @Test
    public void actionPriorityOrdersTollBeforeOpenBeforeUnknown() {
        // pay-toll(0) < pick-lock(1) < walk-through(2) < open(4); prefix + case-insensitive.
        assertTrue(Rs2DoorClassifier.doorActionPriorityIndex("Pay-toll")
                < Rs2DoorClassifier.doorActionPriorityIndex("Open"));
        assertTrue(Rs2DoorClassifier.doorActionPriorityIndex("Pick-lock")
                < Rs2DoorClassifier.doorActionPriorityIndex("Walk-through"));
        assertEquals(Integer.MAX_VALUE, Rs2DoorClassifier.doorActionPriorityIndex("Examine"));
        assertEquals(Integer.MAX_VALUE, Rs2DoorClassifier.doorActionPriorityIndex(null));
    }

    @Test
    public void closeAndShutAreRecognizedAsClosingActions() {
        assertTrue(Rs2DoorClassifier.isDoorCloseOrShutAction("Close"));
        assertTrue(Rs2DoorClassifier.isDoorCloseOrShutAction("close door"));
        assertTrue(Rs2DoorClassifier.isDoorCloseOrShutAction("Shut"));
        assertFalse(Rs2DoorClassifier.isDoorCloseOrShutAction("Open"));
        assertFalse(Rs2DoorClassifier.isDoorCloseOrShutAction(null));
    }

    // --- pickWalkDoorAction ------------------------------------------------------------------------

    @Test
    public void picksHighestPriorityWalkThroughActionExcludingClose() {
        // Open(4) beats Pass(5); Close is excluded entirely.
        assertEquals("Open", Rs2DoorClassifier.pickWalkDoorAction(compWithActions("Pass", "Open", "Close")));
        assertEquals("Pay-toll",
                Rs2DoorClassifier.pickWalkDoorAction(compWithActions("Open", "Pay-toll")));
    }

    @Test
    public void pickWalkDoorActionReturnsNullWhenOnlyCloseOrShut() {
        assertNull(Rs2DoorClassifier.pickWalkDoorAction(compWithActions("Close", "Shut", null)));
        assertNull(Rs2DoorClassifier.pickWalkDoorAction(compWithActions((String[]) new String[]{null, null})));
        assertNull(Rs2DoorClassifier.pickWalkDoorAction(null));
    }

    // --- doorCompositionSpecifiesOnlyCloseOrShut ---------------------------------------------------

    @Test
    public void onlyCloseOrShutDetectsOpenDoorState() {
        assertTrue(Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(
                compWithActions("Close", null, null)));
        assertFalse("a still-openable door is not in the only-close state",
                Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(compWithActions("Open", "Close")));
        assertFalse("no non-null actions -> not classified as open",
                Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(
                        compWithActions((String[]) new String[]{null, null})));
        assertFalse(Rs2DoorClassifier.doorCompositionSpecifiesOnlyCloseOrShut(null));
    }

    // --- getDoorAction -----------------------------------------------------------------------------

    @Test
    public void getDoorActionReturnsHighestPriorityConfiguredMatch() {
        List<String> doorActions = Arrays.asList("open", "pass", "climb-over");
        // Composition exposes Pass + Open; "open" is earlier in doorActions so it wins.
        assertEquals("Open", Rs2DoorClassifier.getDoorAction(compWithActions("Pass", "Open"), doorActions));
        assertNull(Rs2DoorClassifier.getDoorAction(compWithActions("Examine", "Look-at"), doorActions));
        assertNull(Rs2DoorClassifier.getDoorAction(null, doorActions));
    }
}
