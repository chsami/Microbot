package net.runelite.client.plugins.microbot.util.coords;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;

/** Sourhog cave: tile objects report instance coordinates, the player and quest steps use template coordinates. */
public class InstanceCoordinatesTest
{
	private static final int BASE_X = 10400;
	private static final int BASE_Y = 8400;
	private WorldView wv;
	private Scene scene;
	private int[][][] chunks;

	private static int chunk(int templateX, int templateY, int rotation)
	{
		return ((templateX / 8) << 14) | ((templateY / 8) << 3) | (rotation << 1);
	}

	@Before
	public void setUp()
	{
		wv = mock(WorldView.class);
		scene = mock(Scene.class);
		chunks = new int[4][13][13];
		for (int[][] plane : chunks)
		{
			for (int[] column : plane)
			{
				Arrays.fill(column, -1);
			}
		}
		// scene chunk (12,12) is a copy of template chunk (3160,9672), no rotation, plane 0
		chunks[0][12][12] = chunk(3160, 9672, 0);
		when(wv.getScene()).thenReturn(scene);
		when(scene.isInstance()).thenReturn(true);
		when(wv.isInstance()).thenReturn(true);
		when(wv.isTopLevel()).thenReturn(true);
		when(wv.getInstanceTemplateChunks()).thenReturn(chunks);
		when(wv.getBaseX()).thenReturn(BASE_X);
		when(wv.getBaseY()).thenReturn(BASE_Y);
		when(wv.getSizeX()).thenReturn(104);
		when(wv.getSizeY()).thenReturn(104);
		when(wv.getPlane()).thenReturn(0);
	}

	@Test
	public void anInstanceTileBecomesItsTemplateTile()
	{
		WorldPoint instance = new WorldPoint(BASE_X + 96 + 3, BASE_Y + 96 + 4, 0);
		assertEquals(new WorldPoint(3163, 9676, 0), InstanceCoordinates.toTemplate(wv, instance));
	}

	@Test
	public void aRotatedChunkIsRotatedBack()
	{
		// rotation 1: chunk-local (3,4) came from template-local (3,3)
		chunks[0][2][2] = chunk(3160, 9672, 1);
		WorldPoint instance = new WorldPoint(BASE_X + 16 + 3, BASE_Y + 16 + 4, 0);
		assertEquals(new WorldPoint(3163, 9675, 0), InstanceCoordinates.toTemplate(wv, instance));
	}

	@Test
	public void aRotatedAreaKeepsBothCornersInsideTheTemplate()
	{
		chunks[0][2][2] = chunk(3160, 9672, 1);
		// a 3 wide, 1 tall object in the instance is 1 wide and 3 tall in the template
		WorldArea area = InstanceCoordinates.toTemplateArea(wv, new WorldPoint(BASE_X + 16 + 1, BASE_Y + 16 + 4, 0), 3, 1);
		assertNotNull(area);
		assertEquals(1, area.getWidth());
		assertEquals(3, area.getHeight());
		assertEquals(3163, area.getX());
		assertEquals(9673, area.getY());
	}

	@Test
	public void aDuplicatedChunkMapsEveryCopyToTheSameTemplate()
	{
		chunks[0][2][2] = chunk(3160, 9672, 0);
		WorldPoint first = new WorldPoint(BASE_X + 16 + 3, BASE_Y + 16 + 4, 0);
		WorldPoint second = new WorldPoint(BASE_X + 96 + 3, BASE_Y + 96 + 4, 0);
		assertEquals(InstanceCoordinates.toTemplate(wv, first), InstanceCoordinates.toTemplate(wv, second));
		assertEquals(2, InstanceCoordinates.toInstanceCopies(wv, new WorldPoint(3163, 9676, 0)).size());
		// the copy nearest the player is the one to walk to
		LocalPoint nearFirst = LocalPoint.fromScene(18, 19);
		LocalPoint nearSecond = LocalPoint.fromScene(98, 99);
		assertEquals(19, InstanceCoordinates.toLocalNear(wv, new WorldPoint(3163, 9676, 0), nearFirst).getSceneX());
		assertEquals(99, InstanceCoordinates.toLocalNear(wv, new WorldPoint(3163, 9676, 0), nearSecond).getSceneX());
	}

	@Test
	public void theCopyOnTheScenesOwnFloorBeatsANearerCopyOnAnotherFloor()
	{
		// the same template chunk is also loaded on floor 1, right next to the player
		chunks[1][1][1] = chunk(3160, 9672, 0);
		WorldPoint template = new WorldPoint(3163, 9676, 0);
		LocalPoint nearTheUpperCopy = LocalPoint.fromScene(11, 12);
		assertEquals(99, InstanceCoordinates.toLocalNear(wv, template, nearTheUpperCopy).getSceneX());
		// with no copy on the scene's floor any floor will do
		when(wv.getPlane()).thenReturn(2);
		assertNotNull(InstanceCoordinates.toLocalNear(wv, template, nearTheUpperCopy));
	}

	@Test
	public void aChunkOutsideTheInstanceIsLeftAlone()
	{
		// chunk (5,5) holds -1: nothing was loaded there
		WorldPoint unloaded = new WorldPoint(BASE_X + 40 + 1, BASE_Y + 40 + 1, 0);
		assertNull(InstanceCoordinates.templateOf(chunks, BASE_X, BASE_Y, unloaded));
		assertEquals(unloaded, InstanceCoordinates.toTemplate(wv, unloaded));
		assertNull(InstanceCoordinates.toTemplateArea(wv, unloaded, 1, 1));
	}

	@Test
	public void tilesOutsideTheSceneAreLeftAlone()
	{
		WorldPoint far = new WorldPoint(3000, 3000, 0);
		assertEquals(far, InstanceCoordinates.toTemplate(wv, far));
		assertNull(InstanceCoordinates.toLocalNear(wv, far, null));
	}

	@Test
	public void outsideAnInstanceNothingChanges()
	{
		when(scene.isInstance()).thenReturn(false);
		WorldPoint point = new WorldPoint(3163, 9676, 0);
		assertEquals(point, InstanceCoordinates.toTemplate(wv, point));
		assertNull(InstanceCoordinates.toTemplateArea(wv, point, 2, 2));
		assertEquals(0, InstanceCoordinates.toInstanceCopies(wv, point).size());
	}
}
