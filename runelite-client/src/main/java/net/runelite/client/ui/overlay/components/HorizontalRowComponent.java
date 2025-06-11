package net.runelite.client.ui.overlay.components;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

public class HorizontalRowComponent implements LayoutableRenderableEntity
{
	private final List<BufferedImage> images;
	private final int imageSpacing = 4;

	private Point preferredLocation = new Point();
	private Dimension preferredSize;
	private Rectangle bounds;

	public HorizontalRowComponent(List<BufferedImage> images)
	{
		this.images = images;
	}

	@Override
	public Rectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void setPreferredLocation(Point position)
	{
		this.preferredLocation = position;
	}

	@Override
	public void setPreferredSize(Dimension dimension)
	{
		this.preferredSize = dimension;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		int x = preferredLocation.x;
		int y = preferredLocation.y;
		int maxHeight = 0;

		for (BufferedImage image : images)
		{
			graphics.drawImage(image, x, y, null);
			x += image.getWidth() + imageSpacing;
			maxHeight = Math.max(maxHeight, image.getHeight());
		}

		int totalWidth = x - preferredLocation.x;
		Dimension size = new Dimension(totalWidth, maxHeight);
		this.bounds = new Rectangle(preferredLocation, size);
		return size;
	}
}
