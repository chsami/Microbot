package net.runelite.client.plugins.microbot.agility;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.microbot.util.misc.Operation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;

import static net.runelite.api.ObjectID.*;

@PluginDescriptor(

        name = PluginDescriptor.Mocrosoft + "Agility",
        description = "Microbot agility plugin",
        tags = {"agility", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class MicroAgilityPlugin extends Plugin {
    @Inject
    private MicroAgilityConfig config;

    @Provides
    MicroAgilityConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MicroAgilityConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private MicroAgilityOverlay agilityOverlay;

    @Inject
    AgilityScript agilityScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(agilityOverlay);
        }

        // Draynor
        agilityScript.draynorCourse.add(new AgilityObstacleModel(ROUGH_WALL));
        agilityScript.draynorCourse.add(new AgilityObstacleModel(TIGHTROPE));
        agilityScript.draynorCourse.add(new AgilityObstacleModel(TIGHTROPE_11406));
        agilityScript.draynorCourse.add(new AgilityObstacleModel(NARROW_WALL));
        agilityScript.draynorCourse.add(new AgilityObstacleModel(WALL_11630, -1, 3256, Operation.GREATER, Operation.GREATER_EQUAL));
        agilityScript.draynorCourse.add(new AgilityObstacleModel(GAP_11631, -1, 3255, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.draynorCourse.add(new AgilityObstacleModel(CRATE_11632));

        // Al Kharid
        agilityScript.alkharidCourse.add(new AgilityObstacleModel(ROUGH_WALL_11633));
        agilityScript.alkharidCourse.add(new AgilityObstacleModel(TIGHTROPE_14398));
        agilityScript.alkharidCourse.add(new AgilityObstacleModel(CABLE));
        agilityScript.alkharidCourse.add(new AgilityObstacleModel(ZIP_LINE_14403));
        agilityScript.alkharidCourse.add(new AgilityObstacleModel(TROPICAL_TREE_14404));
        agilityScript.alkharidCourse.add(new AgilityObstacleModel(ROOF_TOP_BEAMS));
        agilityScript.alkharidCourse.add(new AgilityObstacleModel(TIGHTROPE_14409));
        agilityScript.alkharidCourse.add(new AgilityObstacleModel(GAP_14399));

        // Varrock
        agilityScript.varrockCourse.add(new AgilityObstacleModel(ROUGH_WALL_14412));
        agilityScript.varrockCourse.add(new AgilityObstacleModel(CLOTHES_LINE));
        agilityScript.varrockCourse.add(new AgilityObstacleModel(GAP_14414));
        agilityScript.varrockCourse.add(new AgilityObstacleModel(WALL_14832));
        agilityScript.varrockCourse.add(new AgilityObstacleModel(GAP_14833)); // this obstacle doesn't always work for some reason
        agilityScript.varrockCourse.add(new AgilityObstacleModel(GAP_14834));
        agilityScript.varrockCourse.add(new AgilityObstacleModel(GAP_14835, -1, 3402, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.varrockCourse.add(new AgilityObstacleModel(LEDGE_14836, -1, 3408, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.varrockCourse.add(new AgilityObstacleModel(EDGE));

        //gnome stronghold
        agilityScript.gnomeStrongholdCourse.add(new AgilityObstacleModel(LOG_BALANCE_23145, -1, 3436, Operation.GREATER, Operation.GREATER_EQUAL));
        agilityScript.gnomeStrongholdCourse.add(new AgilityObstacleModel(OBSTACLE_NET_23134, 2476, 3426, Operation.LESS_EQUAL, Operation.GREATER_EQUAL));
        agilityScript.gnomeStrongholdCourse.add(new AgilityObstacleModel(TREE_BRANCH_23559));
        agilityScript.gnomeStrongholdCourse.add(new AgilityObstacleModel(BALANCING_ROPE_23557, 2477, -1, Operation.LESS_EQUAL, Operation.GREATER));
        agilityScript.gnomeStrongholdCourse.add(new AgilityObstacleModel(TREE_BRANCH_23560));
        agilityScript.gnomeStrongholdCourse.add(new AgilityObstacleModel(OBSTACLE_NET_23135, 2483, 3425, Operation.GREATER_EQUAL, Operation.LESS_EQUAL));
        agilityScript.gnomeStrongholdCourse.add(new AgilityObstacleModel(OBSTACLE_PIPE_23138, -1, 3430, Operation.GREATER, Operation.LESS_EQUAL));


        //canafis
        agilityScript.canafisCourse.add(new AgilityObstacleModel(TALL_TREE_14843));
        agilityScript.canafisCourse.add(new AgilityObstacleModel(GAP_14844));
        agilityScript.canafisCourse.add(new AgilityObstacleModel(GAP_14845));
        agilityScript.canafisCourse.add(new AgilityObstacleModel(GAP_14848));
        agilityScript.canafisCourse.add(new AgilityObstacleModel(GAP_14846));
        agilityScript.canafisCourse.add(new AgilityObstacleModel(POLEVAULT));
        agilityScript.canafisCourse.add(new AgilityObstacleModel(GAP_14847));
        agilityScript.canafisCourse.add(new AgilityObstacleModel(GAP_14897));

        // Falador
        agilityScript.faladorCourse.add(new AgilityObstacleModel(ROUGH_WALL_14898));
        agilityScript.faladorCourse.add(new AgilityObstacleModel(TIGHTROPE_14899));
        agilityScript.faladorCourse.add(new AgilityObstacleModel(HAND_HOLDS_14901));
        agilityScript.faladorCourse.add(new AgilityObstacleModel(GAP_14903, -1, 3358, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.faladorCourse.add(new AgilityObstacleModel(GAP_14904));
        agilityScript.faladorCourse.add(new AgilityObstacleModel(TIGHTROPE_14905));
        agilityScript.faladorCourse.add(new AgilityObstacleModel(TIGHTROPE_14911));
        agilityScript.faladorCourse.add(new AgilityObstacleModel(GAP_14919));
        agilityScript.faladorCourse.add(new AgilityObstacleModel(LEDGE_14920, 3016, -1, Operation.GREATER_EQUAL, Operation.GREATER));
        agilityScript.faladorCourse.add(new AgilityObstacleModel(LEDGE_14921, -1, 3343, Operation.GREATER, Operation.GREATER_EQUAL));
        agilityScript.faladorCourse.add(new AgilityObstacleModel(LEDGE_14922, -1, 3335, Operation.GREATER, Operation.GREATER_EQUAL));
        agilityScript.faladorCourse.add(new AgilityObstacleModel(LEDGE_14924, 3017, -1, Operation.LESS, Operation.GREATER));
        agilityScript.faladorCourse.add(new AgilityObstacleModel(EDGE_14925));

        // Seers
        agilityScript.seersCourse.add(new AgilityObstacleModel(WALL_14927));
        agilityScript.seersCourse.add(new AgilityObstacleModel(GAP_14928));
        agilityScript.seersCourse.add(new AgilityObstacleModel(TIGHTROPE_14932));
        agilityScript.seersCourse.add(new AgilityObstacleModel(GAP_14929));
        agilityScript.seersCourse.add(new AgilityObstacleModel(GAP_14930));
        agilityScript.seersCourse.add(new AgilityObstacleModel(EDGE_14931));

        // Pollnivneach
        agilityScript.polnivCourse.add(new AgilityObstacleModel(BASKET_14935));
        agilityScript.polnivCourse.add(new AgilityObstacleModel(MARKET_STALL_14936, -1, 2968, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.polnivCourse.add(new AgilityObstacleModel(BANNER_14937, -1, 2976, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.polnivCourse.add(new AgilityObstacleModel(GAP_14938, 3362, -1, Operation.LESS_EQUAL, Operation.GREATER));
        agilityScript.polnivCourse.add(new AgilityObstacleModel(TREE_14939, 3366, -1, Operation.GREATER_EQUAL, Operation.GREATER));
        agilityScript.polnivCourse.add(new AgilityObstacleModel(ROUGH_WALL_14940, -1, 2982, Operation.GREATER, Operation.GREATER_EQUAL));
        agilityScript.polnivCourse.add(new AgilityObstacleModel(MONKEYBARS));
        agilityScript.polnivCourse.add(new AgilityObstacleModel(TREE_14944, -1, 2996, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.polnivCourse.add(new AgilityObstacleModel(DRYING_LINE));

        // Rellekka
        agilityScript.rellekkaCourse.add(new AgilityObstacleModel(ROUGH_WALL_14946));
        agilityScript.rellekkaCourse.add(new AgilityObstacleModel(GAP_14947, -1, 3672, Operation.GREATER, Operation.GREATER));
        agilityScript.rellekkaCourse.add(new AgilityObstacleModel(TIGHTROPE_14987));
        agilityScript.rellekkaCourse.add(new AgilityObstacleModel(GAP_14990));
        agilityScript.rellekkaCourse.add(new AgilityObstacleModel(GAP_14991, -1, 3653, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.rellekkaCourse.add(new AgilityObstacleModel(TIGHTROPE_14992));
        agilityScript.rellekkaCourse.add(new AgilityObstacleModel(PILE_OF_FISH));

        // Ardougne
        agilityScript.ardougneCourse.add(new AgilityObstacleModel(WOODEN_BEAMS));
        agilityScript.ardougneCourse.add(new AgilityObstacleModel(GAP_15609));
        agilityScript.ardougneCourse.add(new AgilityObstacleModel(PLANK_26635));
        agilityScript.ardougneCourse.add(new AgilityObstacleModel(GAP_15610));
        agilityScript.ardougneCourse.add(new AgilityObstacleModel(GAP_15611, -1, 3310, Operation.GREATER, Operation.GREATER_EQUAL));
        agilityScript.ardougneCourse.add(new AgilityObstacleModel(STEEP_ROOF));
        agilityScript.ardougneCourse.add(new AgilityObstacleModel(GAP_15612));

        //Prifddinas
        agilityScript.prifddinasCourse.add(new AgilityObstacleModel(LADDER_36221, 3253, -1, Operation.LESS_EQUAL, Operation.GREATER));
        agilityScript.prifddinasCourse.add(new AgilityObstacleModel(TIGHTROPE_36225, 3258, -1, Operation.LESS_EQUAL, Operation.GREATER));
        agilityScript.prifddinasCourse.add(new AgilityObstacleModel(CHIMNEY_36227, -1, 6106, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.prifddinasCourse.add(new AgilityObstacleModel(ROOF_EDGE, -1, 6115, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.prifddinasCourse.add(new AgilityObstacleModel(DARK_HOLE_36229, -1, 6117, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.prifddinasCourse.add(new AgilityObstacleModel(LADDER_36231, -1, 3392, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.prifddinasCourse.add(new AgilityObstacleModel(ROPE_BRIDGE_36233, 2265, -1, Operation.GREATER_EQUAL, Operation.GREATER));
        agilityScript.prifddinasCourse.add(new AgilityObstacleModel(TIGHTROPE_36234, 2254, -1, Operation.GREATER_EQUAL, Operation.GREATER));
        agilityScript.prifddinasCourse.add(new AgilityObstacleModel(ROPE_BRIDGE_36235, -1, 3398, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.prifddinasCourse.add(new AgilityObstacleModel(TIGHTROPE_36236, -1, 3409, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.prifddinasCourse.add(new AgilityObstacleModel(TIGHTROPE_36237));
        agilityScript.prifddinasCourse.add(new AgilityObstacleModel(DARK_HOLE_36238, -1, 3431, Operation.GREATER, Operation.LESS_EQUAL));

        //Ape Atoll
        agilityScript.apeatollCourse.add(new AgilityObstacleModel(STEPPING_STONE_15412, 2755, 2742, Operation.GREATER_EQUAL, Operation.GREATER_EQUAL));
        agilityScript.apeatollCourse.add(new AgilityObstacleModel(TROPICAL_TREE_15414, -1, 2742, Operation.GREATER, Operation.GREATER_EQUAL));
        agilityScript.apeatollCourse.add(new AgilityObstacleModel(MONKEYBARS_15417));
        agilityScript.apeatollCourse.add(new AgilityObstacleModel(SKULL_SLOPE));
        agilityScript.apeatollCourse.add(new AgilityObstacleModel(ROPE_15487, 2752, -1, Operation.LESS_EQUAL, Operation.GREATER));
        agilityScript.apeatollCourse.add(new AgilityObstacleModel(TROPICAL_TREE_16062, 2756, -1, Operation.GREATER_EQUAL, Operation.GREATER));

        //Wyrm Basic
        agilityScript.wyrmbasicCourse.add(new AgilityObstacleModel(LADDER_55178));
        agilityScript.wyrmbasicCourse.add(new AgilityObstacleModel(TIGHTROPE_55180, -1, 2926, Operation.GREATER, Operation.GREATER_EQUAL));
        agilityScript.wyrmbasicCourse.add(new AgilityObstacleModel(TIGHTROPE_55184, 1647, -1, Operation.GREATER_EQUAL, Operation.GREATER_EQUAL));
        agilityScript.wyrmbasicCourse.add(new AgilityObstacleModel(ROPE_55186, 1635, -1, Operation.LESS_EQUAL, Operation.GREATER_EQUAL));
        agilityScript.wyrmbasicCourse.add(new AgilityObstacleModel(LADDER_55190, 1628, -1, Operation.LESS_EQUAL, Operation.GREATER));
        agilityScript.wyrmbasicCourse.add(new AgilityObstacleModel(ZIPLINE_55179));

        //Wyrm Advanced
        agilityScript.wyrmadvancedCourse.add(new AgilityObstacleModel(LADDER_55178));
        agilityScript.wyrmadvancedCourse.add(new AgilityObstacleModel(TIGHTROPE_55180, -1, 2926, Operation.GREATER, Operation.GREATER_EQUAL));
        agilityScript.wyrmadvancedCourse.add(new AgilityObstacleModel(LADDER_55191, -1, 2911, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.wyrmadvancedCourse.add(new AgilityObstacleModel(EDGE_55192, 1647, -1, Operation.GREATER_EQUAL, Operation.GREATER_EQUAL));
        agilityScript.wyrmadvancedCourse.add(new AgilityObstacleModel(TIGHTROPE_55194));
        agilityScript.wyrmadvancedCourse.add(new AgilityObstacleModel(ZIPLINE_55179));

        //Shayzien Basic
        agilityScript.shayzienbasicCourse.add(new AgilityObstacleModel(LADDER_42209));
        agilityScript.shayzienbasicCourse.add(new AgilityObstacleModel(MONKEYBARS_42211));
        agilityScript.shayzienbasicCourse.add(new AgilityObstacleModel(TIGHTROPE_42212, -1, 3635, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.shayzienbasicCourse.add(new AgilityObstacleModel(BAR_42213));
        agilityScript.shayzienbasicCourse.add(new AgilityObstacleModel(TIGHTROPE_42214));
        agilityScript.shayzienbasicCourse.add(new AgilityObstacleModel(TIGHTROPE_42215, -1, 3643, Operation.GREATER, Operation.GREATER_EQUAL));
        agilityScript.shayzienbasicCourse.add(new AgilityObstacleModel(GAP_42216));

        //Shayzien Advanced
        agilityScript.shayzienadvancedCourse.add(new AgilityObstacleModel(LADDER_42209));
        agilityScript.shayzienadvancedCourse.add(new AgilityObstacleModel(MONKEYBARS_42211));
        agilityScript.shayzienadvancedCourse.add(new AgilityObstacleModel(TIGHTROPE_42212, -1, 3635, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.shayzienadvancedCourse.add(new AgilityObstacleModel(BEAM, -1, -3633, Operation.GREATER, Operation.GREATER_EQUAL));
        agilityScript.shayzienadvancedCourse.add(new AgilityObstacleModel(EDGE_42218, -1, 3635, Operation.GREATER, Operation.GREATER_EQUAL));
        agilityScript.shayzienadvancedCourse.add(new AgilityObstacleModel(EDGE_42219, -1, 3630, Operation.GREATER, Operation.LESS_EQUAL));
        agilityScript.shayzienadvancedCourse.add(new AgilityObstacleModel(BEAM_42220, 1511, -1, Operation.LESS_EQUAL, Operation.GREATER_EQUAL));
        agilityScript.shayzienadvancedCourse.add(new AgilityObstacleModel(ZIPLINE));

        //Penguin

        //Barbarian Outpost

        //Werewolf

        // Wilderness Agility Course
        agilityScript.wildernessCourse.add(new AgilityObstacleModel(OBSTACLE_PIPE_23137)); // Entrada del curso
        agilityScript.wildernessCourse.add(new AgilityObstacleModel(ROPESWING_23132)); // Cuerda para cruzar
        agilityScript.wildernessCourse.add(new AgilityObstacleModel(STEPPING_STONE_23556)); // Piedras en el camino
        agilityScript.wildernessCourse.add(new AgilityObstacleModel(LOG_BALANCE_23542)); // Tronco de equilibrio
        agilityScript.wildernessCourse.add(new AgilityObstacleModel(OBSTACLE_PIPE_23138)); // Salida del curso

// Agility Dispenser - Punto donde se canjean los Wilderness Agility Tickets
       /* agilityScript.wildernessCourse.add(new AgilityObstacleModel(AGILITY_DISPENSER));

// Canjear Wilderness Agility Tickets si se tienen 10 o más
        agilityScript.setOnCourseComplete(() -> {
            int ticketCount = agilityScript.getInventory().count(TICKET_DISPENSER);

            if (ticketCount >= 10) {
                agilityScript.getInventory().interact(TICKET_DISPENSER, "Redeem");
                log.info("Canjeando 10 Wilderness Agility Tickets...");
            }
        });*/

        agilityScript.run(config);
    }

    protected void shutDown() {
        agilityScript.shutdown();
        agilityScript.canafisCourse = new ArrayList<>();
        agilityScript.faladorCourse = new ArrayList<>();
        agilityScript.alkharidCourse = new ArrayList<>();
        agilityScript.draynorCourse = new ArrayList<>();
        agilityScript.gnomeStrongholdCourse = new ArrayList<>();
        agilityScript.polnivCourse = new ArrayList<>();
        agilityScript.varrockCourse = new ArrayList<>();
        agilityScript.rellekkaCourse = new ArrayList<>();
        agilityScript.ardougneCourse = new ArrayList<>();
        agilityScript.prifddinasCourse = new ArrayList<>();
        agilityScript.wyrmbasicCourse = new ArrayList<>();
        agilityScript.wyrmadvancedCourse = new ArrayList<>();
        agilityScript.shayzienbasicCourse = new ArrayList<>();
        agilityScript.shayzienadvancedCourse = new ArrayList<>();
        agilityScript.apeatollCourse = new ArrayList<>();
        agilityScript.wildernessCourse = new ArrayList<>();


        overlayManager.remove(agilityOverlay);
    }
}
