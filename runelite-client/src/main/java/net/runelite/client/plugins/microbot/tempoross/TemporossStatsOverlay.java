package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;

public class TemporossStatsOverlay extends OverlayPanel {

    private final TemporossPlugin plugin;
    private final ItemManager itemManager;
    private static final int FISH_IMAGE_ID = ItemID.TEMPOROSS_HARPOONFISH;

    @Inject
    public TemporossStatsOverlay(TemporossPlugin plugin, ItemManager itemManager) {
        super(plugin);
        this.plugin = plugin;
        this.itemManager = itemManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }
    
    private BufferedImage getHarpoonFishImage() {
        try {
            // Get the item image from ItemManager
            BufferedImage img = itemManager.getImage(FISH_IMAGE_ID);
            if (img != null) {
                return ImageUtil.resizeImage(img, 34, 34);
            }
        } catch (Exception e) {
            // Fall back to null if image loading fails
        }
        return null;
    }
    
    // Format numbers with commas for thousands
    private String formatNumber(int number) {
        return String.format("%,d", number);
    }
    
    // Format duration in milliseconds to HH:MM:SS
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        // Set up the panel's visual properties
        panelComponent.setPreferredSize(new Dimension(180, 180));
        panelComponent.setBackgroundColor(new Color(173, 216, 230, 40));

        // Create title line component
        final LineComponent titleLine = LineComponent.builder()
                .right("TEMPOROSS")
                .rightColor(Color.CYAN)
                .build();

        // Create and add the image component dynamically
        BufferedImage fishImage = getHarpoonFishImage();
        if (fishImage != null) {
            ImageComponent imageComponent = new ImageComponent(fishImage);
            panelComponent.getChildren().add(imageComponent);
        }
        
        // Add the title to the panel
        panelComponent.getChildren().add(titleLine);

        // Add game statistics
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Games:")
                .right(String.valueOf(plugin.getTotalGames()))
                .build());

        // Add win/loss statistics
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Wins:")
                .right(String.valueOf(plugin.getWins()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Losses:")
                .right(String.valueOf(plugin.getLosses()))
                .build());
                
        // Add session reward permits information
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Reward Permits gained:")
                .right(String.valueOf(plugin.getSessionRewardPermits()))
                .build());
                
        // Add reward permits per hour information
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Reward Permit p/Hr:")
                .right(String.valueOf(plugin.getRewardPermitsPerHour()))
                .build());
                
        // Add total reward permits information
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Total Reward Permits:")
                .right(String.valueOf(plugin.getTotalRewardPermits()))
                .build());
                
        // Add fishing XP information
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Fishing XP gained:")
                .right(formatNumber(plugin.getSessionFishingXp()))
                .build());
                
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Fishing XP/Hour:")
                .right(formatNumber(plugin.getFishingXpPerHour()))
                .build());
                
        // Add runtime at the bottom
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Runtime:")
                .right(formatTime(plugin.getSessionRuntime()))
                .build());

        return super.render(graphics);
    }
}