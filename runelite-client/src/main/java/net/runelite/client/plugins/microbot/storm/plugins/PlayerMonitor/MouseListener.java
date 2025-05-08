//package net.runelite.client.plugins.microbot.storm.plugins.PlayerMonitor;
//
//import net.runelite.api.Client;
//import net.runelite.api.GameState;
//import net.runelite.client.input.MouseAdapter;
//import javax.swing.*;
//import java.awt.event.MouseEvent;
//
//public class MouseListener extends MouseAdapter {
//
//  private final Client client;
//
//
//  MouseListener(Client client){
//    this.client = client;
//  }
//
//  public MouseEvent mousePressed(MouseEvent event) {
//    if (this.client.getGameState() == GameState.LOGGED_IN) {
//      if(SwingUtilities.isMiddleMouseButton(event)) {
//        if (PlayerMonitorPlugin.mouseAlarm) { PlayerMonitorPlugin.mouseAlarm=false; }
//        PlayerMonitorPlugin.resetMouseClickCounterListener();
//      }
//    }
//    return event;
//  }
//}
