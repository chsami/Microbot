package net.runelite.client.plugins.microbot.mouserecorder;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

public class MouseMacroRecorderOverlay extends OverlayPanel
{
	private final MouseMacroRecorderPlugin plugin;

	@Inject
	private MouseMacroRecorderOverlay(MouseMacroRecorderPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		MouseMacroRecorderPlugin.Status status = plugin.status();
		if (status == MouseMacroRecorderPlugin.Status.OFF || status == MouseMacroRecorderPlugin.Status.LOGGED_OUT)
		{
			return null;
		}
		panelComponent.getChildren().clear();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Input")
			.right(label(status))
			.rightColor(color(status))
			.build());
		String content = plugin.contentName();
		if (status == MouseMacroRecorderPlugin.Status.RECORDING && content != null)
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Content").right(content).build());
		}
		if (plugin.droppedParts() > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Not uploaded")
				.right(plugin.droppedParts() + " chunks")
				.rightColor(Color.ORANGE)
				.build());
		}
		return super.render(graphics);
	}

	private String label(MouseMacroRecorderPlugin.Status status)
	{
		switch (status)
		{
			case RECORDING:
				return "Recording";
			case PAUSED:
				return "Paused (" + pauseLabel(plugin.pauseReason()) + ")";
			case NOT_LINKED:
				return "Link your account to record";
			case DAILY_LIMIT:
				return "Daily limit reached";
			default:
				return "";
		}
	}

	private static String pauseLabel(String reason)
	{
		if (reason == null)
		{
			return "";
		}
		switch (reason)
		{
			case "script":
				return "script running";
			case "focus":
				return "window not focused";
			case "bank-pin":
				return "bank PIN";
			case "text-input":
				return "typing";
			default:
				return reason;
		}
	}

	private static Color color(MouseMacroRecorderPlugin.Status status)
	{
		switch (status)
		{
			case RECORDING:
				return Color.RED;
			case PAUSED:
				return Color.YELLOW;
			default:
				return Color.ORANGE;
		}
	}
}
