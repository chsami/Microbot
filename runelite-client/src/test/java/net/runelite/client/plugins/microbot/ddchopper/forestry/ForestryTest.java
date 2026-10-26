package net.runelite.client.plugins.microbot.ddchopper.forestry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ForestryTest {
	@Test
	public void detectsByNameCaseInsensitively() {
		List<ForestryEvent> e = ForestryEvent.detect(Arrays.asList("woodcutting leprechaun", null),
			Arrays.asList("RISING ROOTS", "Oak"), x -> true);
		assertEquals(Arrays.asList(ForestryEvent.RISING_ROOTS, ForestryEvent.LEPRECHAUN), e);
	}

	@Test
	public void respectsAllowedFilter() {
		List<ForestryEvent> e = ForestryEvent.detect(Collections.singletonList("Pheasant"),
			Collections.singletonList("Beehive"), x -> x != ForestryEvent.BEEHIVES);
		assertEquals(Collections.singletonList(ForestryEvent.PHEASANT_CONTROL), e);
	}

	@Test
	public void nothingAroundMeansNoEvents() {
		assertTrue(ForestryEvent.detect(Collections.emptyList(), null, x -> true).isEmpty());
		assertTrue(ForestryEvent.detect(Collections.singletonList("Man"), Collections.singletonList("Tree"), x -> true)
			.isEmpty());
	}

	@Test
	public void everyEventHasNamesAndActions() {
		for (ForestryEvent e : ForestryEvent.values()) {
			assertTrue(e.label(), e.npcNames().length + e.objectNames().length > 0);
			assertTrue(e.label(), e.actions().length > 0);
		}
		assertEquals(9, ForestryEvent.values().length);
		assertTrue(Arrays.asList(ForestryEvent.allNames(false)).contains("Beehive"));
		assertTrue(Arrays.asList(ForestryEvent.allNames(true)).contains("Pheasant"));
	}

	@Test
	public void trackerGivesUpAfterTimeAndCoolsDown() {
		ForestryTracker t = new ForestryTracker();
		assertTrue(t.attempt(ForestryEvent.BEEHIVES, 0));
		assertTrue(t.attempt(ForestryEvent.BEEHIVES, 10_000));
		assertFalse(t.attempt(ForestryEvent.BEEHIVES, ForestryTracker.MAX_EVENT_MS + 1));
		assertTrue(t.isIgnored(ForestryEvent.BEEHIVES, 100_000));
		assertFalse(t.attempt(ForestryEvent.BEEHIVES, 100_000));
		long later = ForestryTracker.MAX_EVENT_MS + 1 + ForestryTracker.COOLDOWN_MS + 1;
		assertTrue(t.attempt(ForestryEvent.BEEHIVES, later));
	}

	@Test
	public void trackerGivesUpAfterTooManyAttempts() {
		ForestryTracker t = new ForestryTracker();
		for (int i = 0; i < ForestryTracker.MAX_ATTEMPTS; i++) {
			assertTrue(t.attempt(ForestryEvent.POACHERS, i));
		}
		assertFalse(t.attempt(ForestryEvent.POACHERS, 100));
	}

	@Test
	public void doneResetsTheBudgetAndEventsAreIndependent() {
		ForestryTracker t = new ForestryTracker();
		for (int i = 0; i < ForestryTracker.MAX_ATTEMPTS; i++) t.attempt(ForestryEvent.POACHERS, i);
		t.done(ForestryEvent.POACHERS);
		assertTrue(t.attempt(ForestryEvent.POACHERS, 50));
		assertTrue(t.attempt(ForestryEvent.LEPRECHAUN, 50));
	}
}
