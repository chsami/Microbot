package net.runelite.client.plugins.microbot.pluginscheduler.serialization;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

import java.util.function.Consumer;

/**
 * Excludes fields that shouldn't be serialized:
 * - Transient fields
 * - Functional interfaces (Consumer, Supplier, etc.)
 * - Thread objects
 * - Any other non-serializable types we identify
 */
public class ExcludeTransientAndNonSerializableFieldsStrategy implements ExclusionStrategy {
    @Override
    public boolean shouldSkipField(FieldAttributes field) {
        // Skip transient fields
        if (field.hasModifier(java.lang.reflect.Modifier.TRANSIENT)) {
            return true;
        }
        
        // Get the field type
        Class<?> fieldType = field.getDeclaredClass();
        
        // Skip functional interfaces and other non-serializable types
        return fieldType != null && (
            java.util.function.Consumer.class.isAssignableFrom(fieldType) ||
            java.util.function.Supplier.class.isAssignableFrom(fieldType) ||
            java.util.function.Function.class.isAssignableFrom(fieldType) ||
            java.util.function.Predicate.class.isAssignableFrom(fieldType) ||
            java.lang.Thread.class.isAssignableFrom(fieldType) ||
            java.util.concurrent.ScheduledFuture.class.isAssignableFrom(fieldType) ||
            java.awt.Component.class.isAssignableFrom(fieldType)
        );
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
        // Skip functional interfaces at class level too
        return Consumer.class.isAssignableFrom(clazz) ||
               java.util.function.Supplier.class.isAssignableFrom(clazz) ||
               java.lang.Thread.class.isAssignableFrom(clazz) ||
               java.util.concurrent.ScheduledFuture.class.isAssignableFrom(clazz) ||
               java.awt.Component.class.isAssignableFrom(clazz);
    }
}