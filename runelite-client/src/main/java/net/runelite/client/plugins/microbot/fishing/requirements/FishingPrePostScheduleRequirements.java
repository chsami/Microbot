package net.runelite.client.plugins.microbot.fishing.requirements;

import net.runelite.client.plugins.microbot.fishing.aerial.AerialFishingConfig;
import net.runelite.client.plugins.microbot.fishing.barbarian.BarbarianFishingConfig;
import net.runelite.client.plugins.microbot.fishing.eel.EelFishingConfig;
import net.runelite.client.plugins.microbot.fishing.minnows.MinnowsConfig;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data.RequirementCollections;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.LocationRequirement;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

/**
 * Example implementation showing how to use RequirementCollections for a fishing plugin.
 * Demonstrates the new standardized approach to equipment and outfit requirements.
 */
public class FishingPrePostScheduleRequirements extends PrePostScheduleRequirements {
    
    public FishingPrePostScheduleRequirements(MinnowsConfig config) {
        super("Fishing", "Fishing", false);
        // TODO Set location pre schedule requirements - near fishing spots of minnows

        // Set location requirements - near fishing spots or bank      make a enums for the fish, add location requirements for each fish type
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE, true,ScheduleContext.POST_SCHEDULE, Priority.OPTIONAL));
        
        // TODO based on the config, register specific fishing rods, nets, or harpoons
        initializeRequirements();
    }
    public FishingPrePostScheduleRequirements(EelFishingConfig config) {
        super("Fishing", "Fishing", false);
        //  TODO set location pre schedule requirements - near fishing spots  of eals 
        
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE, true,ScheduleContext.POST_SCHEDULE, Priority.OPTIONAL));
        // TODO based on the config, register specific fishing rods, nets, or harpoons
        initializeRequirements();
        
    }
    public FishingPrePostScheduleRequirements(BarbarianFishingConfig config) {
        super("Fishing", "Fishing", false);
        // TODO Set location pre schedule requirements - near fishing spots of barbarian fishing

        // Set location requirements - near fishing spots or bank        
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE, true,ScheduleContext.POST_SCHEDULE, Priority.OPTIONAL));
        // TODO based on the config, register specific fishing rods, nets, or harpoons
        initializeRequirements();
    }
    public FishingPrePostScheduleRequirements(AerialFishingConfig config) {
        super("Fishing", "Fishing", false);
        // Set location pre schedule requirements - near fishing spots of aerial fishing

        // Set location requirements - near fishing spots or bank        
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE, true,ScheduleContext.POST_SCHEDULE, Priority.OPTIONAL));
        // TODO based on the config, register specific fishing rods, nets, or harpoons
        initializeRequirements();
    }

    
    @Override
    protected void initializeRequirements() {
        // Register complete outfit and equipment collections using RequirementCollections
        
        // Angler outfit - provides XP bonus for fishing (including Spirit Angler variants)
        // Example: Skip boots if using graceful boots for run energy
        // TODO: Update RequirementCollections.registerAnglerOutfit to accept ScheduleContext
        RequirementCollections.registerAnglerOutfit(this, Priority.RECOMMENDED, 10,ScheduleContext.PRE_SCHEDULE,false, false, false, true);
                
       
    }
}
