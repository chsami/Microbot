package net.runelite.client.plugins.microbot;

import lombok.Value;

/** Posted once per script run, when its first repeating task is scheduled. */
@Value
public class ScriptStarted {
    Script script;
}
