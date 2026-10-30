package net.runelite.client.plugins.microbot.util.misc;

import org.junit.Test;

import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VisibleClickPointTest {

	private static final Rectangle TARGET = new Rectangle(100, 100, 40, 60);

	@Test
	public void nothingInFrontKeepsTheWholeTarget() {
		assertFalse(VisibleClickPoint.isOccluded(TARGET, Collections.emptyList()));
		assertFalse(VisibleClickPoint.isOccluded(TARGET, List.of(new Rectangle(300, 300, 20, 20))));
	}

	@Test
	public void overlapIsDetected() {
		assertTrue(VisibleClickPoint.isOccluded(TARGET, List.of(new Rectangle(90, 90, 30, 30))));
	}

	@Test
	public void pickedPointIsVisibleAndOutsideEveryOccluder() {
		List<Shape> occluders = List.of(new Rectangle(100, 100, 40, 40), new Rectangle(120, 140, 20, 20));
		Random random = new Random(1);
		for (int i = 0; i < 200; i++) {
			Point p = VisibleClickPoint.pick(TARGET, occluders, random).orElseThrow(AssertionError::new);
			assertTrue(TARGET.contains(p));
			for (Shape occluder : occluders) {
				assertFalse("point " + p + " is covered", occluder.contains(p));
			}
		}
	}

	@Test
	public void pickedPointKeepsItsMarginClear() {
		List<Shape> occluders = List.of(new Rectangle(100, 100, 40, 40));
		Random random = new Random(2);
		for (int i = 0; i < 200; i++) {
			Point p = VisibleClickPoint.pick(TARGET, occluders, random).orElseThrow(AssertionError::new);
			assertFalse(occluders.get(0).intersects(p.x - 1, p.y - 1, 3, 3));
		}
	}

	@Test
	public void fullyCoveredTargetHasNothingToPick() {
		assertEquals(Optional.empty(), VisibleClickPoint.pick(TARGET, List.of(new Rectangle(90, 90, 80, 100)), new Random(3)));
	}

	@Test
	public void sliverThinnerThanTheMinimumIsTooSmall() {
		// leaves a 3 px strip along the right edge
		Area visible = VisibleClickPoint.visibleArea(TARGET, List.of(new Rectangle(90, 90, 53, 100)));
		assertTrue(VisibleClickPoint.isTooSmall(visible));
		assertEquals(Optional.empty(), VisibleClickPoint.pick(TARGET, List.of(new Rectangle(90, 90, 53, 100)), new Random(4)));
	}

	@Test
	public void emptyAreaIsTooSmallAndWholeTargetIsNot() {
		assertTrue(VisibleClickPoint.isTooSmall(new Area()));
		assertFalse(VisibleClickPoint.isTooSmall(new Area(TARGET)));
	}

	@Test
	public void picksInsideANonRectangularRemainder() {
		Polygon triangle = new Polygon(new int[]{100, 140, 100}, new int[]{100, 100, 160}, 3);
		List<Shape> occluders = List.of(new Rectangle(100, 100, 25, 30));
		Random random = new Random(5);
		for (int i = 0; i < 100; i++) {
			Point p = VisibleClickPoint.pick(triangle, occluders, random).orElseThrow(AssertionError::new);
			assertTrue(triangle.contains(p));
			assertFalse(occluders.get(0).contains(p));
		}
	}

	private static final Rectangle2D TILE = new Rectangle2D.Double(512, 512, 128, 128);

	@Test
	public void nearerDisjointFootprintIsInFront() {
		Rectangle2D nearer = new Rectangle2D.Double(512, 256, 128, 128);
		assertTrue(VisibleClickPoint.isInFront(nearer, TILE, 576, 0));
		assertFalse(VisibleClickPoint.isInFront(TILE, nearer, 576, 0));
	}

	@Test
	public void footprintOnOrInsideTheTargetIsNeverInFront() {
		Rectangle2D table = new Rectangle2D.Double(384, 384, 384, 384);
		Rectangle2D onTable = new Rectangle2D.Double(512, 512, 128, 128);
		assertFalse(VisibleClickPoint.isInFront(table, onTable, 576, 0));
		assertFalse(VisibleClickPoint.isInFront(onTable, table, 576, 0));
	}

	@Test
	public void edgeNearestTheCameraDecidesNotTheCentre() {
		// the wide object has its centre farther away than the target but its edge closer
		Rectangle2D wide = new Rectangle2D.Double(0, 300, 1000, 100);
		Rectangle2D target = new Rectangle2D.Double(480, 450, 100, 100);
		assertTrue(VisibleClickPoint.isInFront(wide, target, 530, 0));
	}

	@Test
	public void viewerBesideOrBehindTheTargetSeesPastAnOccluder() {
		Point2D target = new Point2D.Double(10, 10);
		List<Point2D> occluders = List.of(new Point2D.Double(1, 0));
		assertTrue(VisibleClickPoint.looksPastAll(new Point2D.Double(10, 13), target, occluders));
		assertTrue(VisibleClickPoint.looksPastAll(new Point2D.Double(7, 10), target, occluders));
		assertFalse(VisibleClickPoint.looksPastAll(new Point2D.Double(14, 10), target, occluders));
	}

	@Test
	public void occluderOnTheTargetTileBlocksNoDirection() {
		assertTrue(VisibleClickPoint.looksPastAll(new Point2D.Double(14, 10), new Point2D.Double(10, 10), List.of(new Point2D.Double(0, 0))));
	}

	@Test
	public void pointOnTheCoveredPartIsNotInTheVisibleArea() {
		Area visible = VisibleClickPoint.visibleArea(TARGET, List.of(new Rectangle(100, 100, 40, 40)));
		assertTrue(visible.contains(new Point(120, 150)));
		assertFalse(visible.contains(new Point(120, 120)));
	}
}
