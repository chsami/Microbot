package net.runelite.client.plugins.microbot.mahoganyhomez;

import lombok.Setter;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

class MahoganyHomesOverlay extends OverlayPanel
{
    static final String RESET_SESSION_OPTION = "Reset";
    static final String CLEAR_OPTION = "Clear";
    static final String TIMEOUT_OPTION = "Timeout";

    private final MahoganyHomesPlugin plugin;
    private final MahoganyHomesConfig config;
    @Setter
    private static List<GameObject> fixableObjects = new ArrayList<>();

    @Inject
    MahoganyHomesOverlay(MahoganyHomesPlugin plugin, MahoganyHomesConfig config)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.UNDER_WIDGETS);
        this.plugin = plugin;
        this.config = config;

        getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Mahogany Homes Overlay"));
        getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY, TIMEOUT_OPTION, "Mahogany Homes Plugin"));
        getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY, RESET_SESSION_OPTION, "Session Data"));
        getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY, CLEAR_OPTION, "Contract"));
    }


    @Override
    public Dimension render(Graphics2D graphics)
    {
        final Home home = plugin.getCurrentHome();
        final Player player = plugin.getClient().getLocalPlayer();
        if (plugin.isPluginTimedOut() || !config.textOverlay() || player == null)
        {
            return null;
        }

        if (home != null)
        {
            addLine(home.getName());
            addLine(home.getHint());

            if (plugin.distanceBetween(home.getArea(), player.getWorldLocation()) > 0)
            {
                if (config.showRequiredMaterials() && plugin.getContractTier() > 0)
                {
                    addLine("");
                    addLine(home.getRequiredPlanksFormated(plugin.getContractTier()));

                    String bars = home.getRequiredSteelBarsFormated(plugin.getContractTier());
                    if (bars != null)
                    {
                        addLine(bars);
                    }
                }

            }
            else
            {
                if (config.showRequiredMaterials() && plugin.getContractTier() > 0)
                {
                    final RequiredMaterials requiredMaterials = home.getHotspotObjects().getRequiredMaterialsForVarbs(plugin.getRepairableVarbs());
                    // We only want to add an empty line if there's something to be displayed
                    if (requiredMaterials.MinPlanks > 0 || requiredMaterials.MinSteelBars > 0)
                    {
                        addLine("");
                    }

                    // Now we can add the actual text for the planks/bars
                    if (requiredMaterials.MinPlanks > 0)
                    {
                        String plural = requiredMaterials.MinPlanks > 1 ? "s" : "";
                        addLine(String.format("%d plank" + plural, requiredMaterials.MinPlanks));
                    }

                    if (requiredMaterials.MinSteelBars > 0)
                    {
                        String plural = requiredMaterials.MinSteelBars > 1 ? "s" : "";
                        addLine(String.format("%d steel bar" + plural, requiredMaterials.MinSteelBars));
                    }
                }

                addLine("");
                final int count = plugin.getCompletedCount();
                if (count > 0)
                {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left(count + " task(s) remaining")
                            .leftColor(Color.RED)
                            .build());
                    if(!fixableObjects.isEmpty()){
                        for(GameObject object : fixableObjects){
                            addObjectLine(object);
                        }
                    }
                }
                else
                {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("All tasks completed, speak to " + home.getName())
                            .leftColor(Color.GREEN)
                            .build());
                }
            }
        }

        if (config.showSessionStats() && plugin.getSessionContracts() > 0)
        {
            if (home != null)
            {
                addLine("");
            }
            addLine("Contracts Done: " + plugin.getSessionContracts());
            addLine("Points Earned: " + plugin.getSessionPoints());
        }

        return super.render(graphics);
    }

    private void addLine(final String left)
    {
        panelComponent.getChildren().add(LineComponent.builder().left(left).build());
    }

    private void addObjectLine(final GameObject left)
    {
        panelComponent.getChildren().add(LineComponent.builder().left(Rs2GameObject.convertGameObjectToObjectComposition(left).getName()).right(": "+Objects.requireNonNull(Hotspot.getByObjectId(left.getId())).getRequiredAction()).build());
    }
}
