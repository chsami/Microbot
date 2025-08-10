package net.runelite.client.plugins.microbot.mining.requirements;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.mining.AutoMiningConfig;
import net.runelite.client.plugins.microbot.mining.amethyst.AmethystMiningConfig;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data.ItemRequirementCollection;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

/**
 * Example implementation showing how to use ItemRequirementCollection for a mining plugin.
 * Demonstrates the new standardized approach to equipment and outfit requirements,
 * including Varrock diary armour and prospector outfit variants.
 */
@Slf4j
public class MiningPrePostScheduleRequirements extends PrePostScheduleRequirements {
    public MiningPrePostScheduleRequirements(AmethystMiningConfig config) {
        super("Mining", "Mining", false);
        //TODO Set location pre-schedule requirements - near mining spots for amethyst

        //set location post-schedule requirements - near bank for amethyst, make a enums for the ores, add location requirements for each ore type
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE,true, ScheduleContext.PRE_SCHEDULE, Priority.MANDATORY));
        initializeRequirements();
    }
    public MiningPrePostScheduleRequirements(AutoMiningConfig config) {
        super("Mining", "Mining", false);
        // Set location pre-schedule requirements - near mining spots 
        // TODO: Register specific tree locations based on the selected rock type, check wiki for locations, 
        // enchance the  enum to include locations or use a separate mapping
        // also we must check if accessable -> via  qeust unlocked    Rs2Player.getQuestState ->  if a optimal resourse have a
        switch (config.ORE()) {
            case TIN:
               
                break;
            case COPPER:
               
                break;
            case IRON:
               
                break;
            case COAL:
               
                break;
            case GOLD:
               
                break;
            case MITHRIL:
               
                break;
            case ADAMANTITE:
               
                break;
            case RUNITE:
                
                break;
            default:
                log.error("Unsupported ore type: " + config.ORE());
        }
        // Set location requirements - near mining spots or bank
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE, true,ScheduleContext.POST_SCHEDULE, Priority.OPTIONAL));
        
        initializeRequirements();
    }
    
    @Override
    protected void initializeRequirements() {
        // Register complete outfit and equipment collections using ItemRequirementCollection
        
        // Mining pickaxes - progression-based from bronze to crystal
        ItemRequirementCollection.registerPickAxes(this,Priority.MANDATORY, ScheduleContext.PRE_SCHEDULE);
        
        // Prospector/Motherlode Mine outfit - provides XP bonus for mining (includes all variants)
        // we must ensure we equip the varrock armour if available ?  becasue of the bonus or is the motherlode outfit also providing the same bonus? ->  check wiki
        // TODO: Update ItemRequirementCollection.registerProspectorOutfit to accept ScheduleContext
         ItemRequirementCollection.registerProspectorOutfit(this, Priority.RECOMMENDED,8, ScheduleContext.PRE_SCHEDULE, false, false, false, false);
        
        // Varrock diary armour - provides benefits like chance of smelting ore while mining
        // TODO: Update ItemRequirementCollection.registerVarrockDiaryArmour to accept ScheduleContext
        ItemRequirementCollection.registerVarrockDiaryArmour(this, Priority.RECOMMENDED, ScheduleContext.PRE_SCHEDULE);
        
        
    }
}
