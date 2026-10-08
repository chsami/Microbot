package net.runelite.client.plugins.microbot.api;

import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.After;
import org.junit.Test;

import java.util.stream.Stream;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the interrupt-swallow behavior in {@link AbstractEntityQueryable}. When the
 * client thread tears down mid-query, underlying model calls surface
 * {@code RuntimeException} wrapping {@code InterruptedException} from
 * {@code ClientThread.invoke()}. The {@code nearest*} terminals must treat that as
 * a benign shutdown race and return {@code null} rather than letting the noise
 * escape into script logs.
 */
public class AbstractEntityQueryableInterruptionTest {

	@After
	public void clearInterrupt() {
		Thread.interrupted();
	}

	@Test
	public void nearestByAnchor_returnsNullWhenStreamThrowsInterruptWrapper() {
		TestEntity entity = new TestEntity();
		Stream<TestEntity> source = Stream.of(entity).map(e -> {
			throw new RuntimeException("Interrupted waiting for client thread",
					new InterruptedException());
		});

		TestQueryable q = new TestQueryable(source);
		assertNull(q.nearest(new WorldPoint(3200, 3200, 0), 10));
	}

	@Test
	public void nearestByAnchor_returnsNullWhenThreadInterrupted() {
		TestEntity entity = new TestEntity();
		Stream<TestEntity> source = Stream.of(entity).map(e -> {
			Thread.currentThread().interrupt();
			throw new RuntimeException("generic shutdown noise");
		});

		TestQueryable q = new TestQueryable(source);
		assertNull(q.nearest(new WorldPoint(3200, 3200, 0), 10));
		assertTrue("returnNullIfInterrupted must leave the interrupt flag alone",
				Thread.currentThread().isInterrupted());
	}

	@Test
	public void nearestByAnchor_rethrowsUnrelatedRuntimeExceptions() {
		TestEntity entity = new TestEntity();
		Stream<TestEntity> source = Stream.of(entity).map(e -> {
			throw new IllegalStateException("real bug — must surface");
		});

		TestQueryable q = new TestQueryable(source);
		try {
			q.nearest(new WorldPoint(3200, 3200, 0), 10);
			fail("should rethrow non-interrupt RuntimeException");
		} catch (IllegalStateException expected) {
			assertTrue(expected.getMessage().contains("real bug"));
		}
	}

	@Test
	public void nearestByAnchor_returnsEntityWhenStreamIsHealthy() {
		TestEntity near = new TestEntity(new WorldPoint(3201, 3200, 0));
		TestEntity far = new TestEntity(new WorldPoint(3300, 3300, 0));

		TestQueryable q = new TestQueryable(Stream.of(far, near));
		TestEntity result = q.nearest(new WorldPoint(3200, 3200, 0), 100);
		assertTrue("expected the nearer entity", result == near);
	}

	@Test
	public void nearestByAnchor_nullAnchorReturnsNullEvenWithInterruptibleStream() {
		Stream<TestEntity> source = Stream.of(new TestEntity()).map(e -> {
			throw new RuntimeException("should not be consumed", new InterruptedException());
		});
		TestQueryable q = new TestQueryable(source);
		assertNull(q.nearest(null, 10));
	}

	@Test
	public void withinAnchor_skipsEntitiesWithoutLocation() {
		TestEntity detached = new TestEntity(null);
		TestEntity near = new TestEntity(new WorldPoint(3201, 3200, 0));

		TestQueryable q = new TestQueryable(Stream.of(detached, near));
		java.util.List<TestEntity> result = q.within(new WorldPoint(3200, 3200, 0), 5).toList();
		assertTrue(result.size() == 1 && result.get(0) == near);
	}

	// ---- Test fixtures -------------------------------------------------------

	private static class TestQueryable extends AbstractEntityQueryable<TestQueryable, TestEntity> {
		private final Stream<TestEntity> override;

		TestQueryable(Stream<TestEntity> source) {
			super();
			this.override = source;
			this.source = source;
		}

		@Override
		protected Stream<TestEntity> initialSource() {
			return override != null ? override : Stream.empty();
		}
	}

	private static class TestEntity implements IEntity {
		private final WorldPoint loc;

		TestEntity() { this(new WorldPoint(3200, 3200, 0)); }
		TestEntity(WorldPoint loc) { this.loc = loc; }

		@Override public int getId() { return 0; }
		@Override public String getName() { return "test"; }
		@Override public WorldPoint getWorldLocation() { return loc; }
		@Override public LocalPoint getLocalLocation() { return null; }
		@Override public WorldView getWorldView() { return null; }
		@Override public boolean click() { return false; }
		@Override public boolean click(String action) { return false; }
		@Override public boolean isReachable() { return true; }
	}
}
