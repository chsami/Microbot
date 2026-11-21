package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.plugins.microbot.diagnostics.BugReportStatus;
import net.runelite.client.plugins.microbot.diagnostics.TrackedBugReport;
import net.runelite.client.ui.ColorScheme;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class MyReportsDialogTest
{
	private static TrackedBugReport report(String status, String note)
	{
		TrackedBugReport report = new TrackedBugReport();
		report.setCode("MB-AAAAAA");
		report.setPlugin("Web Walker");
		report.setStatus(status);
		report.setNote(note);
		return report;
	}

	@Test
	public void closedLooksLikeWontFix()
	{
		assertEquals(ColorScheme.LIGHT_GRAY_COLOR, MyReportsDialog.statusColor(BugReportStatus.CLOSED));
		assertEquals(MyReportsDialog.statusColor(BugReportStatus.WONT_FIX), MyReportsDialog.statusColor(BugReportStatus.CLOSED));
	}

	@Test
	public void finishedReportsWithoutANoteSayWhereToAsk()
	{
		assertEquals("Want to know why? Ask on Discord and mention MB-AAAAAA.", MyReportsDialog.askWhyHint(report(BugReportStatus.CLOSED, null)));
		assertEquals("Want to know why? Ask on Discord and mention MB-AAAAAA.", MyReportsDialog.askWhyHint(report(BugReportStatus.WONT_FIX, null)));
		assertNull(MyReportsDialog.askWhyHint(report(BugReportStatus.CLOSED, "We didn't hear back.")));
		assertNull(MyReportsDialog.askWhyHint(report(BugReportStatus.WONT_FIX, "It is a game limit.")));
		assertNull(MyReportsDialog.askWhyHint(report(BugReportStatus.FIXED, null)));
		assertNull(MyReportsDialog.askWhyHint(report(BugReportStatus.INVESTIGATING, null)));
	}
}
