package net.runelite.client.plugins.microbot.storm.plugins.PlayerMonitor;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.Notifier;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

 @PluginDescriptor(
         name = PluginDescriptor.eXioStorm + "Player Monitor",
         enabledByDefault = false

 )
 public class PlayerMonitorPlugin extends Plugin {
   private static final Logger log = LoggerFactory.getLogger(PlayerMonitorPlugin.class);
 
   
   @Inject
   private Client client;
   @Inject
   private PlayerMonitorConfig config;

   @Inject
   private PlayerMonitorScript playerMonitorScript;
   @Inject
   private OverlayManager overlayManager;
   @Inject
   private MouseOverlay mouseOverlay;
   @Inject
   private FlashOverlay flashOverlay;
   @Inject
   private Notifier notifier;
   @Inject
   private MouseManager mouseManager;
   private MouseListener mouseListener;
   @Getter
   private boolean playerDetected = false;
   private boolean wasPlayerDetected = false;
   static int leftClickCounter;

   private static final File CLICK_TOTAL_DIR = new File(RuneLite.RUNELITE_DIR, "PlayerMonitor");

   private static final File CLICK_TOTAL_FILE = new File(CLICK_TOTAL_DIR, "click_count.log");

   private long previousClickTime;

   static boolean mouseAlarm;
   
   @Getter
   private boolean overlayOn = false;
   private final HashMap<String, Integer> playerNameToTimeInRange = new HashMap<>();

   private boolean isDangerousPlayerNearby() {
     // Get local player's position
     LocalPoint currentPosition = client.getLocalPlayer().getLocalLocation();

     // Get players in combat level range from the Rs2Player utility class
     List<Rs2PlayerModel> threatPlayers = Rs2Player.getPlayersInCombatLevelRange();

     if (!threatPlayers.isEmpty()) {
       //log.debug("Found {} players in combat level range", threatPlayers.size());
     }

     // Check if any of them are within the configured alarm radius
     for (Rs2PlayerModel playerModel : threatPlayers) {
       LocalPoint playerLocation = playerModel.getPlayer().getLocalLocation();
       float distanceInTiles = playerLocation.distanceTo(currentPosition) / 128f;

       if (distanceInTiles <= config.alarmRadius()) {
         log.debug("Player {} is within alarm radius: {} tiles",
                 playerModel.getPlayer().getName(), distanceInTiles);
         return true;
       }
     }

     return false;
   }

   /**
    * Used by the script to check if a player is detected
    * @return true if a dangerous player is detected
    */
   public boolean isPlayerDetected() {
     return playerDetected;
   }

   protected void startUp() throws Exception {
     if(!config.liteMode()){
     overlayManager.add(mouseOverlay);
     loadMouseClicks();
     this.mouseListener = new MouseListener(this.client);
     this.mouseManager.registerMouseListener((net.runelite.client.input.MouseListener) this.mouseListener);
     this.previousClickTime=System.currentTimeMillis();
     playerMonitorScript.run(config, overlayManager);}
     if(config.liteMode()){playerMonitorScript.run(config, overlayManager);}
   }

   @Subscribe
   public void onClientTick(ClientTick clientTick) {
     if(!config.liteMode()) {
     List<Player> dangerousPlayers = getPlayersInRange().stream().filter(this::shouldPlayerTriggerAlarm).collect(Collectors.toList());
     if (this.config.timeoutToIgnore() > 0)
     { updatePlayersInRange(); }
     boolean shouldAlarm = (dangerousPlayers.size() > 0);
     if (shouldAlarm && !this.overlayOn) {
       if (this.config.desktopNotification())
       { this.notifier.notify("Player spotted!"); }
       this.overlayOn = true;
     }
     if (!shouldAlarm) {
       this.overlayOn = false;
     } }
      if(config.liteMode()) {
        if (client.getGameState() != GameState.LOGGED_IN) {
          if (playerDetected) {
            playerDetected = false;
            //log.debug("Not logged in, player detection disabled");
          }
          return;
        }

        // Check if there are dangerous players nearby
        playerDetected = isDangerousPlayerNearby();

        // Log state changes to avoid spamming logs
        if (playerDetected != wasPlayerDetected) {
          if (playerDetected) {
            log.info("Dangerous player detected!");
            Microbot.log("PlayerMonitorLite: Dangerous player detected!");
          } else {
            log.debug("No dangerous players detected");
          }
          wasPlayerDetected = playerDetected;
        }
      }
     }

   private List<Player> getPlayersInRange() {
     LocalPoint currentPosition = this.client.getLocalPlayer().getLocalLocation();
     return this.client.getPlayers()
       .stream()
       .filter(player -> (player.getLocalLocation().distanceTo(currentPosition) / 128 <= this.config.alarmRadius()))
       .collect(Collectors.toList());
   }
   private boolean shouldPlayerTriggerAlarm(Player player) {
     if (player.getId() == this.client.getLocalPlayer().getId())
     { return false; }
     if (this.config.ignoreClan() && player.isClanMember())
     { return false; }
     if (this.config.ignoreFriends() && player.isFriend())
     { return false; }
     if (this.config.ignoreFriendsChat() && player.isFriendsChatMember())
     { return false; }
     if (this.config.ignoreIgnored() && this.client.getIgnoreContainer().findByName(player.getName()) != null)
     { return false; }
     if (this.config.timeoutToIgnore() > 0) {
       int timePlayerIsOnScreen = this.playerNameToTimeInRange.getOrDefault(player.getName(), Integer.valueOf(0)).intValue();
        return timePlayerIsOnScreen <= this.config.timeoutToIgnore() * 1000;
     }
     return true;
   }

     private void updatePlayersInRange() {
     List<Player> playersInRange = getPlayersInRange();
     for (Player player : playersInRange) {
       String playerName = player.getName();
       int timeInRange = this.playerNameToTimeInRange.containsKey(playerName) ? (this.playerNameToTimeInRange.get(playerName).intValue() + 20) : 20;
       this.playerNameToTimeInRange.put(playerName, Integer.valueOf(timeInRange));
     }
     List<String> playerNames = playersInRange.stream().map(Actor::getName).collect(Collectors.toList());
     List<String> playersToReset = this.playerNameToTimeInRange.keySet().stream().filter(playerName -> !playerNames.contains(playerName)).collect(Collectors.toList());
     for (String playerName : playersToReset) {
       this.playerNameToTimeInRange.remove(playerName);
     }
   }
   protected void shutDown() throws Exception {
     this.overlayManager.remove(this.mouseOverlay);
     if (this.flashOverlay != null) {
       overlayManager.remove(flashOverlay);
     }
     saveMouseClicks();
     this.mouseManager.unregisterMouseListener((net.runelite.client.input.MouseListener)this.mouseListener);
     this.mouseListener = null;
       if (this.overlayOn) {
       this.overlayOn = false;
       playerMonitorScript.shutdown();
     }
     playerMonitorScript.shutdown();
   }
   @Subscribe
   public void onGameStateChanged(GameStateChanged event) throws IOException {
     GameState state = event.getGameState();
     if (state == GameState.LOGIN_SCREEN || state == GameState.UNKNOWN)
       saveMouseClicks();
   }
   @Subscribe
   public void onMenuOptionClicked(MenuOptionClicked event) {
     if (this.client.getGameState() == GameState.LOGGED_IN) {
       if (System.currentTimeMillis()-previousClickTime < 55 && !mouseAlarm) { mouseAlarm = true; }
       previousClickTime = System.currentTimeMillis();
       System.out.println("PlayerMonitor : getOption="+event.getMenuOption());
       if (config.doPlayClickSound()) {
         if(!Arrays.stream(config.clickSoundIgnore().split(", ")).collect(Collectors.toList()).contains(event.getMenuOption())) {
           Microbot.getClientThread().invokeLater(() -> Microbot.getClient().playSoundEffect(config.clickSoundID().getId(), 127));
         }
       }
       leftClickCounter++;
       if (leftClickCounter % 50 == 0)
         try {
           saveMouseClicks();
         } catch (IOException e) {
           e.printStackTrace();
         }
     }
   }
   private enum FILE_CLICK_TYPE_INDICES {
     LEFT(0);

     private final int index;

     FILE_CLICK_TYPE_INDICES(int newIndex) {
       this.index = newIndex;
     }

     public int getValue() {
       return this.index;
     }
   }
   public static int getLeftClickCounter() {
     return leftClickCounter;
   }
   public static void resetMouseClickCounterListener() {
    leftClickCounter = 0;
    try {
      saveMouseClicks();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
   public static void saveMouseClicks() throws IOException {
    if (!CLICK_TOTAL_FILE.exists())
      try {
        if (!CLICK_TOTAL_FILE.createNewFile())
          System.out.println("Failed to create log file");
      } catch (IOException e) {
        e.printStackTrace();
      }
    FileWriter writer = new FileWriter(CLICK_TOTAL_FILE);
    Integer[] totals = { Integer.valueOf(getLeftClickCounter()) };
    writer.write("" + totals[FILE_CLICK_TYPE_INDICES.LEFT.getValue()] + " ");
    writer.close();
  }
   public void loadMouseClicks() throws FileNotFoundException {
    if (!this.CLICK_TOTAL_DIR.mkdir() && CLICK_TOTAL_FILE.exists()) {
      Scanner scanner = new Scanner(CLICK_TOTAL_FILE);
      int[] totals = new int[1]; // Only one entry for left-clicks
      int ii = 0;
      while (scanner.hasNextInt()) {
        totals[ii++] = scanner.nextInt();
      }
      if (ii != 1) { // Ensure only one integer is read
        resetMouseClickCounterListener();
      } else {
        this.leftClickCounter = totals[0];
      }
    } else {
      try {
        if (CLICK_TOTAL_FILE.createNewFile()) {
          leftClickCounter = 0; // Initialize to 0 for new file
        } else {
          System.out.println("Failed to create log file");
        }
      } catch (IOException e) {
        System.out.println("An error occurred creating the log file");
        e.printStackTrace();
      }
    }
  }
   @Provides
   PlayerMonitorConfig provideConfig(ConfigManager configManager) {
     return configManager.getConfig(PlayerMonitorConfig.class);
   }

 }


