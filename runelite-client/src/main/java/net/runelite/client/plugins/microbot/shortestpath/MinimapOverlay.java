package net.runelite.client.plugins.microbot.shortestpath;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.example.ExamplePlugin;
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class MinimapOverlay extends Overlay
{
	@Inject
	MinimapOverlay(ExamplePlugin plugin)
	{
		super(plugin);
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
		drawAfterInterface(InterfaceID.TOPLEVEL_DISPLAY);
		setNaughty();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{

		Shape minimapClip = Rs2MiniMap.getMinimapClipArea();

		Widget minimapWidget = Rs2MiniMap.getMinimapDrawWidget();

		if (minimapWidget != null)
		{
			graphics.setColor(Color.RED);
			graphics.draw(minimapWidget.getBounds());
		}

		if (minimapClip != null)
		{
			graphics.setColor(Color.GREEN);
			graphics.draw(minimapClip);
		}

		return null;
	}
}
