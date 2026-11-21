package net.runelite.client.plugins.microbot.diagnostics;

import lombok.Data;

@Data
public class BugReportStatus
{
	public static final String OPEN = "open";
	public static final String INVESTIGATING = "investigating";
	public static final String FIXED = "fixed";
	public static final String WONT_FIX = "wont-fix";
	public static final String CLOSED = "closed";

	private String code;
	private String plugin;
	private String status;
	private String fixedInVersion;
	private boolean fixedInPlugin;
	private boolean question;
	private String note;
}
