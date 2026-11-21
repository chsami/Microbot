package net.runelite.client.ui.components;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import net.runelite.client.ui.ColorScheme;

/** The side panel's on/off switch: accent track when on, slate when off. */
public final class SwitchIcons
{
	public static final ImageIcon ON = new ImageIcon(paint(true));
	public static final ImageIcon OFF = new ImageIcon(paint(false));

	private SwitchIcons()
	{
	}

	private static BufferedImage paint(boolean on)
	{
		BufferedImage img = new BufferedImage(26, 14, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(on ? ColorScheme.BRAND_ORANGE : ColorScheme.MEDIUM_GRAY_COLOR);
		g.fillRoundRect(0, 0, 26, 14, 14, 14);
		g.setColor(on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		g.fillOval(on ? 14 : 2, 2, 10, 10);
		g.dispose();
		return img;
	}
}
