//package net.runelite.client.plugins.microbot.storm.plugins.PlayerMonitor;
//
//import net.runelite.client.ui.overlay.OverlayPanel;
//import net.runelite.client.ui.overlay.components.LineComponent;
//
//import javax.inject.Inject;
//import java.awt.*;
//import java.util.ArrayList;
//import java.util.Collections;
//
//public class MouseOverlay extends OverlayPanel {
//    private final PlayerMonitorConfig config;
//    private final PlayerMonitorPlugin plugin;
//    private int size;
//    private ArrayList<Integer> previousSelections;
//    @Inject
//    MouseOverlay(PlayerMonitorConfig config, PlayerMonitorPlugin plugin) {
//        this.config = config;
//        this.plugin = plugin;
//        this.size = 0;
//        this.previousSelections = new ArrayList<>();
//    }
//    public Dimension render(Graphics2D graphics) {
//        this.panelComponent.getChildren().clear();
//        this.panelComponent.setPreferredSize(new Dimension(getSize(graphics) + 15, 0));
//        if (!config.hideClickCounter()) {
//            this.panelComponent.getChildren().add(LineComponent.builder()
//                    .left("Left Clicks: ")
//                    .right("" + this.plugin.getLeftClickCounter())
//                    .build());
//        }
//        return this.panelComponent.render(graphics);
//    }
//    private int getSize(Graphics2D graphics) {
//        int configSize = 0;
//        ArrayList<Integer> currentSelections = new ArrayList<>();
//        if (!this.config.hideClickCounter()) {
//            String leftString = "Left: " + this.plugin.getLeftClickCounter();
//            int leftSize = graphics.getFontMetrics().stringWidth(leftString);
//            configSize = Math.max(configSize, leftSize);
//            currentSelections.add(Integer.valueOf(1));
//        }
//        Collections.sort(currentSelections);
//        if (!currentSelections.equals(this.previousSelections)) {
//            this.size = configSize;
//            this.previousSelections = currentSelections;
//        } else if (this.size + 5 < configSize) {
//            this.size = configSize;
//        }
//        return this.size;
//    }
//}
