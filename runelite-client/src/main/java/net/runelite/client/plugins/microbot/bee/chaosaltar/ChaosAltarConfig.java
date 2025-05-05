package net.runelite.client.plugins.microbot.bee.chaosaltar;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("chaosaltar")
public interface ChaosAltarConfig extends Config {

    @ConfigItem(
            keyName = "pluginDescription",
            name = "How to Use",
            description = "Best practices for using Chaos Altar plugin",
            position = 1
    )
    default String pluginDescription() {
        return "For best results:\n"
                + "- Activate Player Monitor in LITE_MODE\n"
                + "- Enable AutoLogin\n"
                + "- Keep Burning Amulets and dragon bones in the bank\n"
                + "- ONLY WORKS WITH Dragon Bones\n"
                + "- If you have an alt, consider teleporting to the Lava Maze spot first to distract PKers or bots who can auto-attack when you teleport\n"
                + "(Note: Microbot currently can't log out fast enough for when someone is waiting for you after teleport)";
    }

    @ConfigItem(
            keyName = "f2pHop",
            name = "Enable F2P Hop",
            description = "Hops to F2P worlds and runs to the altar instead of using Burning Amulet. (WIP - non-functional)"
    )
    default boolean f2pHop() {
        return false;
    }

    @ConfigItem(
            keyName = "Boneyard",
            name = "Enable Boneyard Mode",
            description = "Collects bones from boneyard and uses them on chaos altar(WIP - non-functional)"
    )
    default boolean boneYardMode() {
        return false;
    }

    @ConfigItem(
            keyName = "Fast Bones Offering",
            name = "Offer Bones Fast",
            description = "Uses the bones on the altar quickly (more apm)"
    )
    default boolean giveBonesFast() {
        return false;
    }

}
