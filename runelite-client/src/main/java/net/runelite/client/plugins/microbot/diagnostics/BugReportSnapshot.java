package net.runelite.client.plugins.microbot.diagnostics;

import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BugReportSnapshot
{
	String diagnostics;
	String gameState;
	String log;
	String recorder;
	byte[] screenshot;
	boolean loggedIn;
	@Builder.Default
	Set<String> names = Set.of();
}
