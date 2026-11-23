package net.runelite.client.plugins.microbot.questhelper.tools;

import java.awt.image.BufferedImage;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.ImageUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class QuestHelperWorldMapPointTest
{
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);

	@Test
	public void drawsBackgroundOnlyWhenQuestImageIsNull()
	{
		BufferedImage background = ImageUtil.loadImageResource(QuestHelperWorldMapPoint.class, "/util/clue_arrow.png");

		QuestHelperWorldMapPoint point = new QuestHelperWorldMapPoint(LUMBRIDGE, null);

		BufferedImage image = point.getImage();
		assertNotNull(image);
		assertEquals(background.getWidth(), image.getWidth());
		assertEquals(background.getHeight(), image.getHeight());
		for (int x = 0; x < image.getWidth(); x++)
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				assertEquals(background.getRGB(x, y), image.getRGB(x, y));
			}
		}
	}

	@Test
	public void drawsQuestImageOverBackground()
	{
		BufferedImage background = ImageUtil.loadImageResource(QuestHelperWorldMapPoint.class, "/util/clue_arrow.png");
		BufferedImage questIcon = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 4; x++)
		{
			for (int y = 0; y < 4; y++)
			{
				questIcon.setRGB(x, y, 0xFFFF00FF);
			}
		}

		QuestHelperWorldMapPoint point = new QuestHelperWorldMapPoint(LUMBRIDGE, questIcon);

		int offset = background.getWidth() / 2 - 2;
		assertEquals(0xFFFF00FF, point.getImage().getRGB(offset, offset));
		assertNotEquals(background.getRGB(offset, offset), point.getImage().getRGB(offset, offset));
	}
}
