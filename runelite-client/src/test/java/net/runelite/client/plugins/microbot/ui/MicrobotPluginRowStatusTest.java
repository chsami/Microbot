package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.plugins.microbot.ui.MicrobotPluginRowStatus.Kind;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginHealth;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MicrobotPluginRowStatusTest
{
    @Test
    public void nothingToDoGivesNoStatus()
    {
        assertNull(MicrobotPluginRowStatus.of(null, null, null, null, null, false));
    }

    @Test
    public void failureComesBeforeBlockedAndUpdate()
    {
        MicrobotPluginRowStatus status = MicrobotPluginRowStatus.of("boom", "broken", BROKEN, "1.4", "1.5", false);

        assertEquals(Kind.FAILED, status.getKind());
        assertEquals("Failed to start", status.getText());
    }

    @Test
    public void blockedComesBeforeUpdateAndSaysWhy()
    {
        MicrobotPluginRowStatus status = MicrobotPluginRowStatus.of(null, "disabled", BROKEN, "1.4", "1.5", false);

        assertEquals(Kind.BLOCKED, status.getKind());
        assertEquals("Not loaded: disabled", status.getText());
    }

    @Test
    public void longBlockedReasonIsShortenedLikeAHealthReason()
    {
        String reason = "version 1.0.12 is confirmed broken: stalls at the bank every single time after the last game update";
        MicrobotPluginRowStatus status = MicrobotPluginRowStatus.of(null, reason, null, null, null, false);

        assertEquals("Not loaded: " + org.apache.commons.lang3.StringUtils.abbreviate(reason, 80), status.getText());
    }

    @Test
    public void longReasonKeepsTheWholeLineForTheTooltip()
    {
        String reason = "version 1.0.12 is confirmed broken: stalls at the bank every single time after the last game update";

        assertEquals("Not loaded: " + reason, MicrobotPluginRowStatus.of(null, reason, null, null, null, false).getFullText());
        assertNull("A short reason needs no tooltip", MicrobotPluginRowStatus.of(null, "disabled", null, null, null, false).getFullText());
    }

    @Test
    public void blockingHealthUsesItsOwnWordsNotAlwaysBroken()
    {
        MicrobotPluginManifest disabled = new MicrobotPluginManifest();
        disabled.setInternalName("A");
        disabled.setVersion("1.0.0");
        disabled.setDisable(true);
        assertEquals(MicrobotPluginHealth.State.DISABLED.getLabel(),
            MicrobotPluginRowStatus.of(null, null, MicrobotPluginHealth.evaluate(disabled, "1.0.0", true), null, null, false).getText());

        MicrobotPluginManifest tooNew = new MicrobotPluginManifest();
        tooNew.setInternalName("B");
        tooNew.setVersion("1.0.0");
        tooNew.setMinClientVersion("9.9.9");
        MicrobotPluginRowStatus status = MicrobotPluginRowStatus.of(null, null, MicrobotPluginHealth.evaluate(tooNew, "1.0.0", false), null, null, false);
        assertEquals("Requires client 9.9.9", status.getText());
        assertTrue(status.isSevere());
    }

    @Test
    public void updateNamesBothVersions()
    {
        MicrobotPluginRowStatus status = MicrobotPluginRowStatus.of(null, null, null, "1.4", "1.5", false);

        assertEquals(Kind.UPDATE, status.getKind());
        assertEquals("Update 1.4 to 1.5", status.getText());
    }

    @Test
    public void updateInProgressSaysSo()
    {
        MicrobotPluginRowStatus status = MicrobotPluginRowStatus.of(null, null, null, "1.4", "1.5", true);

        assertEquals(Kind.UPDATE, status.getKind());
        assertEquals("Updating...", status.getText());
    }

    @Test
    public void updateWaitsForBothVersionsSoUnloadedManifestsShowNothing()
    {
        assertNull(MicrobotPluginRowStatus.of(null, null, null, "1.4", null, false));
        assertNull(MicrobotPluginRowStatus.of(null, null, null, null, "1.5", false));
    }

    private static final MicrobotPluginHealth BROKEN = health(MicrobotPluginHealth.STATUS_BROKEN, "Stops after banking at the Grand Exchange every single time");

    private static MicrobotPluginHealth health(String status, String reason)
    {
        MicrobotPluginHealth.Metadata metadata = new MicrobotPluginHealth.Metadata();
        metadata.setStatus(status);
        metadata.setReason(reason);
        metadata.setAffectedVersions(Collections.singletonList("*"));
        MicrobotPluginManifest manifest = new MicrobotPluginManifest();
        manifest.setVersion("1.0.0");
        manifest.setHealth(metadata);
        return MicrobotPluginHealth.evaluate(manifest, "1.0.0", true);
    }

    @Test
    public void confirmedBrokenComesBeforeUpdateWithAShortReason()
    {
        MicrobotPluginRowStatus status = MicrobotPluginRowStatus.of(null, null, BROKEN, "1.4", "1.5", false);

        assertEquals(Kind.HEALTH, status.getKind());
        assertEquals("Confirmed broken: Stops after banking at the Grand...", status.getText());
        assertTrue(status.isSevere());
    }

    @Test
    public void confirmedBrokenWithoutReasonSaysJustThat()
    {
        assertEquals("Confirmed broken", MicrobotPluginRowStatus.of(null, null, health(MicrobotPluginHealth.STATUS_BROKEN, null), null, null, false).getText());
    }

    @Test
    public void unconfirmedReportsAreAWarning()
    {
        MicrobotPluginRowStatus status = MicrobotPluginRowStatus.of(null, null, health(MicrobotPluginHealth.STATUS_UNVERIFIED, "x"), null, null, false);

        assertEquals(Kind.HEALTH, status.getKind());
        assertEquals("Reported issues", status.getText());
        assertFalse(status.isSevere());
    }

    @Test
    public void healthyOrUnknownHealthGivesNoStatus()
    {
        assertNull(MicrobotPluginRowStatus.of(null, null, health(MicrobotPluginHealth.STATUS_OK, null), null, null, false));
    }
}