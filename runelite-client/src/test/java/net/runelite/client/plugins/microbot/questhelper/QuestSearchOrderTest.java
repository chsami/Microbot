package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** Gertrude's Cat: the lumberyard crates are searched once each, reachable first, nearest first. */
public class QuestSearchOrderTest
{
	private static final class Crate
	{
		final String name;
		final boolean reachable;
		final int distance;

		Crate(String name, boolean reachable, int distance)
		{
			this.name = name;
			this.reachable = reachable;
			this.distance = distance;
		}
	}

	private static final java.util.Comparator<Crate> ORDER = java.util.Comparator
		.comparingInt((Crate c) -> c.reachable ? 0 : 1).thenComparingInt(c -> c.distance);

	private static String next(List<Crate> crates, Set<String> searched)
	{
		Crate crate = QuestObjectApproach.nextToSearch(crates, c -> searched.contains(c.name), ORDER);
		return crate == null ? null : crate.name;
	}

	@Test
	public void eachCrateIsSearchedOnceNearestFirstAndUnreachableOnesLast()
	{
		List<Crate> crates = Arrays.asList(
			new Crate("far", true, 9), new Crate("blocked", false, 1), new Crate("near", true, 2), new Crate("mid", true, 5));
		Set<String> searched = new HashSet<>();
		StringBuilder order = new StringBuilder();
		for (int i = 0; i < 4; i++)
		{
			String crate = next(crates, searched);
			order.append(crate).append(' ');
			searched.add(crate);
		}
		assertEquals("near mid far blocked ", order.toString());
	}

	@Test
	public void afterEveryCrateWasTriedItGoesRoundAgainFromTheBest()
	{
		List<Crate> crates = Arrays.asList(new Crate("a", true, 4), new Crate("b", true, 1));
		assertEquals("b", next(crates, new HashSet<>(Arrays.asList("a", "b"))));
	}

	private static final class Kitten
	{
		final String name;
		final boolean mewing;
		final int distance;

		Kitten(String name, boolean mewing, int distance)
		{
			this.name = name;
			this.mewing = mewing;
			this.distance = distance;
		}
	}

	private static String order(List<Kitten> crates)
	{
		StringBuilder out = new StringBuilder();
		for (Kitten k : QuestObjectApproach.orderForSearch(crates, c -> c.mewing,
			java.util.Comparator.comparingInt(c -> c.distance)))
		{
			out.append(k.name).append(' ');
		}
		return out.toString();
	}

	/** Gertrude's Cat: the crates showing "Mew!" over them are searched first, and the silent ones not while some mew. */
	@Test
	public void cratesShowingMewAreSearchedAloneNearestFirst()
	{
		List<Kitten> crates = Arrays.asList(
			new Kitten("silent-near", false, 1), new Kitten("mew-far", true, 8), new Kitten("mew-near", true, 3));
		assertEquals("mew-near mew-far ", order(crates));
	}

	@Test
	public void whenNoneMewsAllAreSearchedNearestFirst()
	{
		List<Kitten> crates = Arrays.asList(new Kitten("b", false, 5), new Kitten("a", false, 2));
		assertEquals("a b ", order(crates));
	}

	@Test
	public void noCratesGivesNothing()
	{
		assertNull(next(Collections.emptyList(), new HashSet<>()));
	}
}
