package net.runelite.client.plugins.microbot.rsagent.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that serve as RsAgent tools.
 * Used for documentation generation and reflection-based execution.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RsAgentTool {
    /**
     * Name of the tool as it will be called via the LLM
     */
    String name();

    /**
     * Description of what the tool does
     */
    String description();

    /**
     * Whether this tool requires the user to be logged in
     */
    boolean requiresLogin() default true;
}