package net.runelite.client.plugins.microbot.storm.plugins.PlayerMonitor;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.storm.plugins.PlayerMonitor.enums.SoundEffectID;
import net.runelite.client.plugins.microbot.storm.plugins.PlayerMonitor.enums.emergencyOptions;

import java.awt.*;
 

 @ConfigGroup("PlayerMonitor")
 @ConfigInformation("Use middle mouse click to <br />" +
         "turn off mouse alarm <br />" +
         "and reset counter.")
 public interface PlayerMonitorConfig
   extends Config
 {
   @ConfigSection(
           name = "Player",
           description = "When other players detected",
           position = 0,
           closedByDefault = true
   )
   String playerAlarm = "Player Alarm";

   @Range(max = 30, min = 0)
   @ConfigItem(keyName = "alarmRadius", name = "Alarm radius", description = "Distance for another player to trigger the alarm. WARNING: Players within range that are not rendered will not trigger the alarm.", position = 0, section = playerAlarm)
   default int alarmRadius() { return 15; }
   @ConfigItem(keyName = "desktopNotification", name = "Desktop notification", description = "Receive a desktop notification when the alarm triggers", position = 1, section = playerAlarm)
   default boolean desktopNotification() { return false; }
   @ConfigItem(keyName = "ignoreFriends", name = "Ignore friends", description = "Do not alarm for players on your friends list", position = 3, section = playerAlarm)
   default boolean ignoreFriends() { return true; }
   @ConfigItem(keyName = "ignoreClan", name = "Ignore clan", description = "Do not alarm for players in your clan", position = 4, section = playerAlarm)
   default boolean ignoreClan() { return true; }
   @ConfigItem(keyName = "ignoreFriendsChat", name = "Ignore friends chat", description = "Do not alarm for players in the same friends chat as you", position = 5, section = playerAlarm)
   default boolean ignoreFriendsChat() { return false; }
   @ConfigItem(keyName = "ignoreIgnored", name = "Ignore 'ignore list'", description = "Do not alarm for players on your ignore list", position = 6, section = playerAlarm)
   default boolean ignoreIgnored() { return false; }
   @ConfigItem(keyName = "timeoutToIgnore", name = "Timeout", description = "Ignores players after they've been present for the specified time (in seconds). A value of 0 means players won't be ignored regardless of how long they are present.", position = 7, section = playerAlarm)
   default int timeoutToIgnore() { return 0; }
   @ConfigItem(keyName = "useFlash", name = "Use Flash?", description = "Toggle the screen flash effect", position = 0, section = playerAlarm)
   default boolean useFlash() { return true; }
   @Alpha
   @ConfigItem(keyName = "flashColor", name = "Flash color", description = "Sets the color of the alarm flashes", position = 8, section = playerAlarm)
   default Color flashColor()
   { return new Color(255, 255, 0, 70); }
   @ConfigItem(keyName = "playSound", name = "Play Sound?", description = "Would you like the alarm to be audible as well?", position = 9, section = playerAlarm)
   default boolean playAlarmSound() { return true; }
   @ConfigItem(keyName = "PASoundIDs", name = "SoundID", description = "Pick Sound ID to play", position = 10, section = playerAlarm)
   default SoundEffectID alarmSoundID() { return SoundEffectID.GE_INCREMENT_PLOP; }
   @ConfigItem(keyName = "Emergency Options", name = "Emergency Options", description = "Do something when other players are detected?", position = 11, section = playerAlarm)
   default boolean useEmergency() { return true; }
   @ConfigItem(keyName = "OnlyWilderness", name = "Only Wilderness?", description = "Apply said action only while in the wilderness?", position = 12, section = playerAlarm)
   default boolean onlyWilderness() { return true; }
   @ConfigItem(keyName = "Emergency Action", name = "Emergency Action", description = "What do you want to do when other players are detected?", position = 13, section = playerAlarm)
   default emergencyOptions emergencyAction() { return emergencyOptions.USE_ITEM; }
   @ConfigItem(keyName = "Emergency Item", name = "Emergency Item", description = "What's the item to be used when other players are detected?(ID supported)", position = 14, section = playerAlarm)
   default String emergencyItem() { return "Varrock Teleport"; }
   @ConfigItem(keyName = "Emergency Item Menu", name = "Emergency Item Menu", description = "What's the menu option to be used when other players are detected?", position = 15, section = playerAlarm)
   default String emergencyItemMenu() { return "Break"; }
   @ConfigSection(
           name = "Mouse",
           description = "When mouse events occur",
           position = 1,
           closedByDefault = true
   )
   String mouse = "Mouse";
   @ConfigItem(position = 0, keyName = "hideClickCounter", name = "Hide Click Counter", description = "Toggle the display of any click count", section = mouse)
   default boolean hideClickCounter() { return false; }
   @ConfigItem(position = 1, keyName = "doClickSound", name = "Play Click Sound", description = "Play a sound when a new click is registered?", section = mouse)
   default boolean doPlayClickSound() {
     return false;
   }
   @ConfigItem(position = 2, keyName = "ClickSoundIDs", name = "SoundID", description = "Pick Sound ID to play", section = mouse)
   default SoundEffectID clickSoundID() { return SoundEffectID.UI_BOOP; }
   @ConfigItem(position = 3, keyName = "ClickSoundIgnore", name = "Sound Ignore", description = "Which actions should be muted? separate with comma.", section = mouse)
   default String clickSoundIgnore() { return ""; }
   @ConfigItem(position = 4, keyName = "mouseAlarm", name = "Alarm unnatural fast clicks?", description = "Would you like the mouse listener to alarm unnaturally fast clicks?", section = mouse)
   default boolean mouseAlarm() { return true; }
   @ConfigItem(position = 5, keyName = "MouseAlarmSound", name = "Alarm Sound", description = "Pick Sound ID to play", section = mouse)
   default SoundEffectID mouseAlarmSound() { return SoundEffectID.ATTACK_HIT; }
   @ConfigItem(keyName = "combatLevelRange", name = "LITE_MODE", description = "Logs out if someone in radius can attack you", position = 16, section = playerAlarm)
   default boolean liteMode() { return false;}
 }