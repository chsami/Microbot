package net.runelite.client.plugins.microbot.moonsOfPeril.handlers;

import net.runelite.client.plugins.microbot.moonsOfPeril.enums.State;

public interface BaseHandler {
    /** Should we run now? */
    boolean validate();

    /**
     * Do the work for this state.
     *
     * @return
     */
    State execute();
}
