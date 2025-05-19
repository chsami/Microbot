package net.runelite.client.plugins.microbot.rsagent.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark method parameters for RsAgent tools.
 * Used for documentation generation and reflection-based parameter handling.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParameter {
    /**
     * Name of the parameter as it will be referred to in JSON
     */
    String name();

    /**
     * Description of what the parameter does
     */
    String description();

    /**
     * Whether this parameter is optional
     */
    boolean optional() default false;
}