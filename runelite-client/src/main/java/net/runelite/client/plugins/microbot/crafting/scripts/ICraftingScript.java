package net.runelite.client.plugins.microbot.crafting.scripts;

import java.util.Map;

public interface ICraftingScript {
    String getName();
    String getVersion();
    String getState();
    Map<String, String> getCustomProperties();
}
