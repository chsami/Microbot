package net.runelite.client.plugins.microbot.aiofighter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.aiofighter.combat.SlayerScript;
import net.runelite.client.plugins.microbot.util.slayer.Rs2Slayer;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ButtonComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
@Slf4j
public class AIOFighterInfoOverlay extends OverlayPanel {
    private final AIOFighterConfig config;
    public final ButtonComponent myButton;
    public final ButtonComponent blacklistButton;

    @Inject
    AIOFighterInfoOverlay(AIOFighterPlugin plugin, AIOFighterConfig config) {
        super(plugin);
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();

        // Initialize the button with a label and preferred size
        myButton = new ButtonComponent("Pause");
        myButton.setPreferredSize(new Dimension(100, 30));
        myButton.setParentOverlay(this);
        myButton.setFont(FontManager.getRunescapeBoldFont());
        myButton.setOnClick(() -> {
            // Handle button click
            Microbot.pauseAllScripts.set(!Microbot.pauseAllScripts.get());
            if (Microbot.pauseAllScripts.get()) {
                Rs2Walker.setTarget(null);
                myButton.setText("Unpause");
            } else {
                myButton.setText("Pause");
            }
        });

        // Initialize the blacklist button with a label and preferred size
        blacklistButton = new ButtonComponent("Blacklist");
        blacklistButton.setPreferredSize(new Dimension(100, 20));
        blacklistButton.setParentOverlay(this);
        blacklistButton.setFont(FontManager.getRunescapeBoldFont());
        blacklistButton.setOnClick(() -> {
            // Handle button click
             AIOFighterPlugin.addBlacklistedSlayerNpcs(Rs2Slayer.slayerTaskMonsterTarget);
            SlayerScript.reset();
        });


    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(250, 400));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("\uD83E\uDD86 AIO Fighter \uD83E\uDD86")
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Slayer Mode: ")
                    .right(config.slayerMode() ? "Enabled" : "Disabled")
                    .build());

            if (config.slayerMode()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Slayer Task: ")
                        .right(config.slayerTask())
                        .build());
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Slayer Task Location: ")
                        .right(config.slayerLocation())
                        .build());
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Remaining kills: ")
                        .right(String.valueOf(config.remainingSlayerKills()))
                        .build());
            }
            panelComponent.getChildren().add(LineComponent.builder().build());
            if (config.slayerMode()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Slayer Has Task Weakness: ")
                        .right(config.slayerHasTaskWeakness() ? "Yes" : "No")
                        .build());
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Slayer Task Weakness Item: ")
                        .right(config.slayerTaskWeaknessItem())
                        .build());
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Slayer Task Weakness Threshold: ")
                        .right(String.valueOf(config.slayerTaskWeaknessThreshold()))
                        .build());
            }
            panelComponent.getChildren().add(LineComponent.builder().build());
            panelComponent.getChildren().add(blacklistButton);
            panelComponent.getChildren().add(LineComponent.builder().build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .right("Version:" +  AIOFighterPlugin.version)
                    .build());

            // Add the button to the overlay panel
            panelComponent.getChildren().add(myButton);
        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

}
