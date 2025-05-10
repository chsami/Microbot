package net.runelite.client.plugins.microbot.giantsfoundry;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.GameObject;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.Heat;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage.*;

public class GiantsFoundryState {
    // heat and progress are from 0-1000
    private static final int VARBIT_HEAT = 13948;
    private static final int VARBIT_PROGRESS = 13949;

    public static final int VARBIT_ORE_COUNT = 13934;
    public static final int VARBIT_FORTE_SELECTED = 13910;
    public static final int VARBIT_BLADE_SELECTED = 13911;
    public static final int VARBIT_TIP_SELECTED = 13912;

    // 0 - load bars
    // 1 - set mould
    // 2 - collect preform
    // 3 -
    static final int VARBIT_GAME_STAGE = 13914;

    private static final int WIDGET_HEAT_PARENT = 49414153;
    private static final int WIDGET_LOW_HEAT_PARENT = 49414163;
    private static final int WIDGET_MED_HEAT_PARENT = 49414164;
    private static final int WIDGET_HIGH_HEAT_PARENT = 49414165;

    static final int WIDGET_PROGRESS_PARENT = 49414219;
    // children with type 3 are stage boxes
    // every 11th child is a sprite

    private static final int SPRITE_ID_TRIP_HAMMER = 4442;
    private static final int SPRITE_ID_GRINDSTONE = 4443;
    private static final int SPRITE_ID_POLISHING_WHEEL = 4444;

    @Setter
    @Getter
    private boolean enabled;

    private static final List<Stage> stages = new ArrayList<>();
    private static double heatRangeRatio = 0;

    public static void reset() {
        stages.clear();
        heatRangeRatio = 0;
    }

    public static int getHeatAmount() {
        return Microbot.getVarbitValue(VARBIT_HEAT);
    }

    public static int getProgressAmount() {
        return Microbot.getVarbitValue(VARBIT_PROGRESS);
    }

    public static double getHeatRangeRatio() {
        if (heatRangeRatio == 0) {
            Widget heatWidget = Rs2Widget.getWidget(WIDGET_HEAT_PARENT);
            Widget medHeat = Rs2Widget.getWidget(WIDGET_MED_HEAT_PARENT);
            if (medHeat == null || heatWidget == null) {
                return 0;
            }

            heatRangeRatio = medHeat.getWidth() / (double) heatWidget.getWidth();
        }

        return heatRangeRatio;
    }

    public static int[] getLowHeatRange() {
        return new int[]{
                (int) ((1 / 6d - getHeatRangeRatio() / 2) * 1000),
                (int) ((1 / 6d + getHeatRangeRatio() / 2) * 1000),
        };
    }

    public static int[] getMedHeatRange() {
        return new int[]{
                (int) ((3 / 6d - getHeatRangeRatio() / 2) * 1000),
                (int) ((3 / 6d + getHeatRangeRatio() / 2) * 1000),
        };
    }

    public static int[] getHighHeatRange() {
        return new int[]{
                (int) ((5 / 6d - getHeatRangeRatio() / 2) * 1000),
                (int) ((5 / 6d + getHeatRangeRatio() / 2) * 1000),
        };
    }

    public static List<Stage> getStages() {
        if (stages.isEmpty()) {
            Widget progressParent = Rs2Widget.getWidget(WIDGET_PROGRESS_PARENT);
            if (progressParent == null || progressParent.getChildren() == null) {
                return new ArrayList<>();
            }

            for (Widget child : progressParent.getChildren()) {
                switch (child.getSpriteId()) {
                    case SPRITE_ID_TRIP_HAMMER:
                        stages.add(TRIP_HAMMER);
                        break;
                    case SPRITE_ID_GRINDSTONE:
                        stages.add(GRINDSTONE);
                        break;
                    case SPRITE_ID_POLISHING_WHEEL:
                        stages.add(POLISHING_WHEEL);
                        break;
                }
            }
        }

        return stages;
    }

    public static GameObject getStageObject(Stage stage) {
        switch (stage) {
            case TRIP_HAMMER:
                return Rs2GameObject.getGameObject("trip hammer");
            case GRINDSTONE:
                return Rs2GameObject.getGameObject("grindstone");
            case POLISHING_WHEEL:
                return Rs2GameObject.getGameObject("polishing wheel");
        }
        return null;
    }

    public static Stage getCurrentStage() {
        int index = (int) (getProgressAmount() / 1000d * getStages().size());
        if (index < 0 || index > getStages().size() - 1) {
            return null;
        }

        return getStages().get(index);
    }

    public static Heat getCurrentHeat() {
        int heat = getHeatAmount();

        int[] low = getLowHeatRange();
        if (heat > low[0] && heat < low[1]) {
            return Heat.LOW;
        }

        int[] med = getMedHeatRange();
        if (heat > med[0] && heat < med[1]) {
            return Heat.MED;
        }

        int[] high = getHighHeatRange();
        if (heat > high[0] && heat < high[1]) {
            return Heat.HIGH;
        }

        return Heat.NONE;
    }

    /**
     * Get the amount of progress each stage needs
     */
    public static double getProgressPerStage() {
        return 1000d / getStages().size();
    }

    public static int getActionsLeftInStage() {
        int progress = getProgressAmount();
        double progressPerStage = getProgressPerStage();
        double progressTillNext = progressPerStage - progress % progressPerStage;

        Stage current = getCurrentStage();
        return (int) Math.ceil(progressTillNext / current.getProgressPerAction());
    }

    public static Heat getHeatStage()
    {
        if (getCurrentStage() == null) return Heat.NONE;

        return getCurrentStage().getHeat();

}

    public static int getHeatChangeNeeded()
    {
        Heat requiredHeat = getCurrentStage().getHeat();
        int heat = getHeatAmount();

        int[] range;
        switch (requiredHeat)
        {
            case LOW:
                range = getLowHeatRange();
                break;
            case MED:
                range = getMedHeatRange();
                break;
            case HIGH:
                range = getHighHeatRange();
                break;
            default:
                return 0;
        }

        if (heat < range[0])
            return range[0] - heat;
        else if (heat > range[1])
            return range[1] - heat;
        else
            return 0;
    }


    public static int[] getCurrentHeatRange() {
        switch (getCurrentStage()) {
            case POLISHING_WHEEL:
                return getLowHeatRange();
            case GRINDSTONE:
                return getMedHeatRange();
            case TRIP_HAMMER:
                return getHighHeatRange();
            default:
                return new int[]{0, 0};
        }
    }

    /**
     * Get the amount of current stage actions that can be
     * performed before the heat drops too high or too low to
     * continue
     */
    public static int getActionsForHeatLevel() {
        Heat heatStage = getCurrentHeat();
        Stage stage = getCurrentStage();
        if (stage == null) return 0;
        if (heatStage != stage.getHeat()) {
            // not the right heat to start with
            return 0;
        }

        int[] range = getCurrentHeatRange();
        int actions = 0;
        int heat = getHeatAmount();
        while (heat > range[0] && heat < range[1]) {
            actions++;
            heat += stage.getHeatChange();
        }

        return actions;
    }
    public static HeatActionStateMachine heatingCoolingState = new HeatActionStateMachine();
}