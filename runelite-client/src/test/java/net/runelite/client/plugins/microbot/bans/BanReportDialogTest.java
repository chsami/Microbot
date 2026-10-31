package net.runelite.client.plugins.microbot.bans;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BanReportDialogTest
{
	@Test
	public void sendCarriesTheChosenKind()
	{
		BanReportDialog.Answer answer = BanReportDialog.answer(BanReportDialog.SEND, BanKind.TEMPORARY, true);

		assertTrue(answer.isSend());
		assertEquals(BanKind.TEMPORARY, answer.getKind());
		assertTrue(answer.isRemember());
	}

	@Test
	public void notABanIsNeverSentOrRemembered()
	{
		BanReportDialog.Answer answer = BanReportDialog.answer(BanReportDialog.SEND, BanKind.NOT_A_BAN, true);

		assertFalse(answer.isSend());
		assertFalse(answer.isRemember());
		assertEquals(BanKind.NOT_A_BAN, answer.getKind());
	}

	@Test
	public void closingTheWindowDecidesNothing()
	{
		BanReportDialog.Answer answer = BanReportDialog.answer(null, BanKind.PERMANENT, true);

		assertFalse(answer.isSend());
		assertFalse(answer.isRemember());
	}

	@Test
	public void dontSendCanBeRemembered()
	{
		BanReportDialog.Answer answer = BanReportDialog.answer(BanReportDialog.DONT_SEND, BanKind.UNKNOWN, true);

		assertFalse(answer.isSend());
		assertTrue(answer.isRemember());
	}

	@Test
	public void banReportsAskByDefault()
	{
		assertEquals(BanReportMode.ASK, new net.runelite.client.plugins.microbot.MicrobotConfig() { }.banReports());
	}
}
