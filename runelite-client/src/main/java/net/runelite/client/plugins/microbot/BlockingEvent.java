package net.runelite.client.plugins.microbot;

/**
 * Represents an event that can block the execution of a script if a specific issue is encountered.
 * Blocking events are used to handle situations that would prevent a script from running properly.
 */
public interface BlockingEvent {

    /**
     * Determines whether this blocking event should be triggered.
     * If this method returns {@code true}, it indicates that an issue is present 
     * and the event needs to be executed to resolve it.
     *
     * @return {@code true} if the event should be executed, otherwise {@code false}
     */
    boolean validate();

    /**
     * Executes the logic associated with resolving the blocking event.
     * This method is called when {@link #validate()} returns {@code true}.
     *
     * @return {@code true} if the execution was successful, otherwise {@code false}
     */
    boolean execute();

    /**
     * Retrieves the priority level of this blocking event.
     * The priority determines the order in which blocking events are processed.
     *
     * @return the {@link BlockingEventPriority} of this event
     */
    BlockingEventPriority priority();

    /**
     * Retrieves the name of this blocking event.
     * By default, it returns the simple name of the implementing class.
     * This can be useful for logging, debugging, or displaying human-readable
     * identifiers for different blocking events.
     *
     * @return the simple name of the implementing class
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}