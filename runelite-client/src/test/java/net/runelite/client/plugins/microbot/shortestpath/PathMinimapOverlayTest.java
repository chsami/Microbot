package net.runelite.client.plugins.microbot.shortestpath;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PathMinimapOverlayTest
{
	private Pathfinder previousPathfinder;

	@Before
	public void saveStaticPathfinder()
	{
		previousPathfinder = ShortestPathPlugin.pathfinder;
	}

	@After
	public void restoreStaticPathfinder()
	{
		ShortestPathPlugin.pathfinder = previousPathfinder;
	}

	@Test
	public void hiddenMinimapKeepsGraphicsClip() throws Exception
	{
		ShortestPathPlugin plugin = mock(ShortestPathPlugin.class);
		plugin.drawMinimap = true;
		when(plugin.getMinimapClipArea()).thenReturn(null);

		Pathfinder pathfinder = mock(Pathfinder.class);
		when(pathfinder.getPath()).thenReturn(Collections.emptyList());
		ShortestPathPlugin.pathfinder = pathfinder;

		Constructor<PathMinimapOverlay> constructor = PathMinimapOverlay.class
			.getDeclaredConstructor(Client.class, ShortestPathPlugin.class, ShortestPathConfig.class);
		constructor.setAccessible(true);
		PathMinimapOverlay overlay = constructor.newInstance(mock(Client.class), plugin, mock(ShortestPathConfig.class));

		Rectangle clip = new Rectangle(0, 0, 50, 50);
		Graphics2D graphics = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB).createGraphics();
		graphics.setClip(clip);
		try
		{
			overlay.render(graphics);
			assertNotNull(graphics.getClip());
			assertEquals(clip, graphics.getClip());
		}
		finally
		{
			graphics.dispose();
		}
	}
}
