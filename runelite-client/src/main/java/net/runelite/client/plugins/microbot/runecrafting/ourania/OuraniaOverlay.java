package net.runelite.client.plugins.microbot.runecrafting.ourania;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.HorizontalRowComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.ColorUtil;

public class OuraniaOverlay extends OverlayPanel
{
	private final OuraniaPlugin plugin;
	private final OuraniaConfig config;
	private final Color titleColor = ColorUtil.fromHex("0077B6");

	@Inject
	OuraniaOverlay(OuraniaPlugin plugin, OuraniaConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setNaughty();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		try
		{
			panelComponent.setPreferredSize(new Dimension(200, 300));
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("Ourania Altar V" + OuraniaPlugin.version)
				.color(titleColor)
				.build());

			panelComponent.getChildren().add(LineComponent.builder().build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Status:")
				.right(Microbot.status)
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Run time:")
				.right(getFormattedDuration(plugin.getStartTime()))
				.build());

			if (!config.toggleProfitCalculator())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Profit:")
					.right(Integer.toString(plugin.getProfit()))
					.build());
			}

			if (!config.toggleRunesCrafted() && !plugin.getRunesCrafted().isEmpty())
			{
				panelComponent.getChildren().add(TitleComponent.builder()
					.text("Runes")
					.color(titleColor)
					.build());

				panelComponent.getChildren().add(LineComponent.builder().build());

				List<BufferedImage> runeImages = plugin.getRunesCrafted().stream()
					.map(item -> getImage(item.getId(), item.getQuantity()))
					.collect(Collectors.toList());

				final int runesPerRow = 5;

				for (int i = 0; i < runeImages.size(); i += runesPerRow)
				{
					List<BufferedImage> row = runeImages.subList(i, Math.min(i + runesPerRow, runeImages.size()));
					panelComponent.getChildren().add(new HorizontalRowComponent(row));
				}
			}
		}
		catch (Exception ex)
		{
			Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
		}
		return super.render(graphics);
	}

	private BufferedImage getImage(int itemID, int amount)
	{
		BufferedImage image = Microbot.getItemManager().getImage(itemID, amount, true);
		return image;
	}

	private String getFormattedDuration(Duration duration)
	{
		long hours = duration.toHours();
		long minutes = duration.toMinutes() % 60;
		long seconds = duration.getSeconds() % 60;
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}
}
