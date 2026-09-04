package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Rs2AntibanDefaultsTest
{
    @After
    public void restoreDefaults()
    {
        Rs2Antiban.resetAntibanSettings(true);
    }

    @Test
    public void resetRestoresLowMouseSpeed()
    {
        Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);

        Rs2Antiban.resetAntibanSettings(true);

        assertEquals(ActivityIntensity.LOW, Rs2Antiban.getActivityIntensity());
    }
}
