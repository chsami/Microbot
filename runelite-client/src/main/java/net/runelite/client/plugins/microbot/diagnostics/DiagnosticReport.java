package net.runelite.client.plugins.microbot.diagnostics;

import java.util.Map;
import java.util.regex.Pattern;

public final class DiagnosticReport
{
	static final int MAX_VALUE_LENGTH = 80;
	private static final String UNKNOWN = "unknown";
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
	private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]+");
	private static final Pattern UNIX_PATH = Pattern.compile("(?<![\\w.:/])(?:~(?:/[^\\s,;)\\]]*)?|/(?:home|Users|root|mnt|media|var|tmp|opt|usr|etc|private)\\b(?:/[^\\s,;)\\]]*)?|/[^\\s/,;)\\]]+/[^\\s,;)\\]]*)");
	private static final Pattern WINDOWS_PATH = Pattern.compile("(?i)(?:[a-z]:|\\\\\\\\[^\\\\\\s]+)\\\\[^\\s,;)\\]]*");
	private static final Pattern URL_CREDENTIALS = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://)[^\\s/@]+@");
	private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
	private static final Pattern SECRET = Pattern.compile("[A-Za-z0-9_\\-+/=]{32,}");
	private static final Pattern KEYED_SECRET = Pattern.compile(
		"(?i)\\b(password|passwd|passphrase|token|api[_-]?key|secret|authorization|bank ?pin|pin)(\\s*[:=]\\s*)[^\\s,;)]+");

	private DiagnosticReport()
	{
	}

	public static String render(DiagnosticSnapshot snapshot)
	{
		BuildProvenance provenance = BuildProvenance.resolve(snapshot.getMicrobotCommit(), snapshot.getSourceCommit(),
			snapshot.getSourceDirty(), snapshot.getBuildChannel(), snapshot.getBuildRepository());

		StringBuilder out = new StringBuilder();
		out.append("Microbot diagnostics\n");
		line(out, "Microbot", value(snapshot.getMicrobotVersion()));
		line(out, "RuneLite", value(snapshot.getRuneliteVersion()));
		line(out, "Commit", clean(provenance.getCommit()) + " (" + provenance.getCommitSource() + ")");
		line(out, "Build origin", clean(provenance.getOrigin()));
		line(out, "Launcher", value(snapshot.getLauncherVersion()));
		line(out, "Java", value(snapshot.getJavaVersion()) + " (" + value(snapshot.getJavaVendor()) + ")");
		line(out, "OS", value(snapshot.getOsName()) + " " + value(snapshot.getOsArch()));
		line(out, "Game revision", snapshot.getGameRevision() == null || snapshot.getGameRevision() <= 0 ? UNKNOWN : snapshot.getGameRevision().toString());
		line(out, "Safe mode", flag(snapshot.getSafeMode()));
		line(out, "GPU", "plugin " + (snapshot.getGpuPluginEnabled() == null ? UNKNOWN : snapshot.getGpuPluginEnabled() ? "enabled" : "disabled")
			+ ", renderer " + (snapshot.getGpuRendererActive() == null ? UNKNOWN : snapshot.getGpuRendererActive() ? "active" : "inactive"));
		line(out, "Walker", value(snapshot.getWalker()) + ", planner " + value(snapshot.getPlannerMode()));
		line(out, "Mouse", value(snapshot.getMouse()) + ", aim clicks from cursor " + flag(snapshot.getNaturalMouse()));

		Map<String, String> settings = snapshot.getWalkerSettings();
		if (settings.isEmpty())
		{
			line(out, "Walker settings", UNKNOWN);
		}
		else
		{
			StringBuilder joined = new StringBuilder();
			for (Map.Entry<String, String> entry : settings.entrySet())
			{
				if (joined.length() > 0)
				{
					joined.append(", ");
				}
				joined.append(clean(entry.getKey())).append('=').append(value(entry.getValue()));
			}
			line(out, "Walker settings", joined.toString());
		}

		out.append("External plugins (").append(snapshot.getPlugins().size()).append(")");
		if (snapshot.getPlugins().isEmpty())
		{
			out.append(": none loaded\n");
		}
		else
		{
			out.append(":\n");
			for (DiagnosticSnapshot.Plugin plugin : snapshot.getPlugins())
			{
				out.append("- ").append(value(plugin.getName()))
					.append(" (").append(value(plugin.getInternalName())).append(") ")
					.append(value(plugin.getDescriptorVersion()))
					.append(" [");
				if (plugin.isHubInstalled())
				{
					out.append("hub, installed ").append(value(plugin.getInstalledVersion()));
				}
				else
				{
					out.append("sideloaded or unknown source");
				}
				if (BuildProvenance.isKnown(plugin.getLatestVersion()))
				{
					out.append(", hub latest ").append(clean(plugin.getLatestVersion()));
				}
				out.append(", ").append(plugin.isActive() ? "active" : "inactive").append("]\n");
			}
		}
		out.append("Excluded: account/player names, chat, credentials/tokens, file paths.\n");
		return out.toString();
	}

	public static String clean(String raw)
	{
		if (raw == null)
		{
			return "";
		}
		String text = redact(raw).replaceAll("\\s+", " ").trim();
		if (text.length() > MAX_VALUE_LENGTH)
		{
			text = text.substring(0, MAX_VALUE_LENGTH - 3) + "...";
		}
		return text;
	}

	static String redact(String raw)
	{
		String text = HTML_TAG.matcher(raw).replaceAll(" ");
		text = CONTROL.matcher(text).replaceAll(" ");
		text = URL_CREDENTIALS.matcher(text).replaceAll("$1[redacted]@");
		text = WINDOWS_PATH.matcher(text).replaceAll("[path]");
		text = UNIX_PATH.matcher(text).replaceAll("[path]");
		text = EMAIL.matcher(text).replaceAll("[redacted]");
		text = KEYED_SECRET.matcher(text).replaceAll("$1$2[redacted]");
		text = SECRET.matcher(text).replaceAll("[redacted]");
		return text;
	}

	private static String value(String raw)
	{
		String cleaned = BuildProvenance.isKnown(raw) ? clean(raw) : "";
		return cleaned.isEmpty() ? UNKNOWN : cleaned;
	}

	private static String flag(Boolean value)
	{
		return value == null ? UNKNOWN : value ? "on" : "off";
	}

	private static void line(StringBuilder out, String label, String text)
	{
		out.append(label).append(": ").append(text).append('\n');
	}
}
