package net.runelite.client.plugins.microbot.frosty.threatdetector;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup("ThreatDetector")
public interface ThreatDetectorConfig extends Config {
    @Range(min = 0, max = 30)
    @ConfigItem(
            keyName = "alarmRadius",
            name = "Alarm Radius",
            description = "Distance for a player to trigger the alarm."
    )
    default int alarmRadius() {
        return 15;
    }

    @ConfigItem(
            keyName = "flashColor",
            name = "Flash color",
            description = "Sets the color of the alarm flashes",
            position = 8
    )
    default Color flashColor()
    {
        return new Color(255, 255, 0, 70);
    }

    @ConfigItem(
            keyName = "flashLayer",
            name = "Flash layer",
            description = "Advanced: control the layer that the flash renders on",
            position = 10
    )
    default flashLayer flashLayer() { return flashLayer.ABOVE_SCENE; }

    @ConfigItem(
            keyName = "flashControl",
            name = "Flash speed",
            description = "Control the cadence at which the screen will flash with the chosen color",
            position = 9
    )
    default FlashSpeed flashControl() { return FlashSpeed.NORMAL; }

    @ConfigItem(
            keyName = "ignoreClan",
            name = "Ignore Clan",
            description = "Do not alarm for clan members."
    )
    default boolean ignoreClan() {
        return true;
    }

    @ConfigItem(
            keyName = "ignoreFriends",
            name = "Ignore Friends",
            description = "Do not alarm for friends."
    )
    default boolean ignoreFriends() {
        return true;
    }

    @ConfigItem(
            keyName = "ignoreFriendsChat",
            name = "Ignore Friends Chat",
            description = "Do not alarm for friends in the same chat."
    )
    default boolean ignoreFriendsChat() {
        return false;
    }

    @ConfigItem(
            keyName = "ignoreIgnored",
            name = "Ignore 'ignore list'",
            description = "Do not alarm for players on your ignore list",
            position = 6
    )
    default boolean ignoreIgnored()
    {
        return false;
    }

    @ConfigItem(
            keyName = "customIgnoresList",
            name = "Custom Ignore List",
            description = "Comma-separated list of names to ignore."
    )
    default String customIgnoresList() {
        return "";
    }
}
