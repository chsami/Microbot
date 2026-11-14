package net.runelite.client.plugins.microbot.mouserecorder;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

public class MouseMacroRecorderOverlay extends OverlayPanel
{
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

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
			panelComponent.getChildren().add(LineComponent.builder().left("Doing").right(content).build());
		}
		int lost = plugin.droppedParts();
		if (lost > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Not uploaded")
				.right("about " + Math.max(1, (lost + 1) / 2) + " min")
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
			case RETRYING:
				return "Can't reach microbot.cloud, retrying";
			case PAUSED:
				return "Paused (" + pauseLabel(plugin.pauseReason(), plugin.botName()) + ")";
			case NOT_LINKED:
				return "Not linked: see Microbot Cloud in the sidebar";
			case LINK_REMOVED:
				return "Link removed: link again in Microbot Cloud";
			case TELEMETRY_OFF:
				return "Off: telemetry is disabled";
			case DAILY_LIMIT:
				long until = plugin.limitUntilMs();
				return until > 0 ? "Daily limit reached, retrying at " + TIME.format(Instant.ofEpochMilli(until)) : "Daily limit reached";
			default:
				return "";
		}
	}

	private static String pauseLabel(String reason, String bot)
	{
		if (reason == null)
		{
			return "";
		}
		switch (reason)
		{
			case "script":
				return bot == null ? "a script is controlling the client" : bot + " is running";
			case "focus":
				return "window not focused";
			case "bank-pin":
				return "bank PIN";
			case "prompt":
				return "chatbox prompt";
			case "typing":
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
			case RETRYING:
				return Color.YELLOW;
			default:
				return Color.ORANGE;
		}
	}
}
