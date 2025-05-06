package net.runelite.client.plugins.microbot.maxxin.astralrc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;

@ConfigGroup(AstralRunesConfig.configGroup)
@ConfigInformation(
        "Plugin to craft Astral runes. <br/>" +
        "<b>REQUIRED ITEMS</b><br/>" +
        "- Rune pouch w/ Law, Astrals, Cosmic runes <br/>" +
        "- Dust staff equipped <br/>" +
        "- Pure essence, Stamina potions, Lobsters in bank <br/>" +
        "Make sure spell book is set to Lunar. <br/>" +
        "Only supports Colossal pouch and will use NPC contact for repairs.<br/>" +
        "Equipping a dust staff and and having a Rune pouch setup with Law, Astrals, and Cosmic runes is required. <br/>" +
        "Runecraft cape should be supported. Only Law and Astrals are required in Rune pouch if using RC cape (or no Colossal pouch). <br/>" +
        "The only food supported is Lobster, required to survive hits from mobs when banking. <br/>"
)
public interface AstralRunesConfig extends Config {
    String configGroup = "astral-runes";
}
