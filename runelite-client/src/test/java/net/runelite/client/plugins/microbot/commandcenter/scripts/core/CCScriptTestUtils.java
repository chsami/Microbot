package net.runelite.client.plugins.microbot.commandcenter.scripts.core;

import net.runelite.client.config.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reflection-based test utilities for CCScript internals.
 * Provides access to private fields without modifying production code.
 */
public final class CCScriptTestUtils {

    private CCScriptTestUtils() {}

    /** Get the registered behaviors list from a script. */
    @SuppressWarnings("unchecked")
    public static List<CCBehavior> getBehaviors(CCScript<?> script) {
        try {
            Field f = CCScript.class.getDeclaredField("behaviors");
            f.setAccessible(true);
            return Collections.unmodifiableList((List<CCBehavior>) f.get(script));
        } catch (Exception e) {
            throw new RuntimeException("Failed to read behaviors field", e);
        }
    }

    /** Check whether setAntiBanTemplate() was called (template is non-null). */
    public static boolean hasAntiBanTemplate(CCScript<?> script) {
        try {
            Field f = CCScript.class.getDeclaredField("antiBanTemplate");
            f.setAccessible(true);
            return f.get(script) != null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read antiBanTemplate field", e);
        }
    }

    /** Call configure() on a script via reflection (bypasses protected access). */
    public static void callConfigure(CCScript<?> script) {
        try {
            Method m = CCScript.class.getDeclaredMethod("configure");
            m.setAccessible(true);
            m.invoke(script);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call configure()", e);
        }
    }

    /** Inject a config value into a script's @Inject config field via reflection.
     *  Walks the class hierarchy so inherited config fields are found too. */
    public static <C extends Config> void injectConfig(CCScript<?> script, C config) {
        for (Class<?> c = script.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(javax.inject.Inject.class)
                        && Config.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        f.set(script, config);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to inject config", e);
                    }
                    return;
                }
            }
        }
        throw new IllegalStateException(
            "No @Inject Config field found on " + script.getClass().getSimpleName());
    }
}
