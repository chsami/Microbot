package net.runelite.client.plugins.microbot.moaaudit;

import org.junit.Test;

import java.lang.reflect.Field;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Uses reflection on package-private static fields; standard JUnit JVM allows
 * {@link java.lang.reflect.AccessibleObject#setAccessible(boolean) AccessibleObject.setAccessible}.
 */
public class MoaAuditPluginTest
{
    /** Must match {@link MoaAuditPlugin} static field name (update both on rename). */
    private static final String MOA_AUDIT_STOPWORKER_THREAD_ALIVE_EVALS_FOR_WARN = "MOA_AUDIT_STOPWORKER_THREAD_ALIVE_EVALS_FOR_WARN";

    @Test
    public void warnCounterIsAtomicInteger() throws Exception
    {
        Field atomicField = MoaAuditPlugin.class.getDeclaredField(MOA_AUDIT_STOPWORKER_THREAD_ALIVE_EVALS_FOR_WARN);
        atomicField.setAccessible(true);
        assertSame(AtomicInteger.class, atomicField.getType());
        assertEquals(0, ((AtomicInteger) atomicField.get(null)).get());
    }
}
