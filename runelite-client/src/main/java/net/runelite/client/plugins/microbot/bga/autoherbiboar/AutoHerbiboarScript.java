package net.runelite.client.plugins.microbot.bga.autoherbiboar;

import java.util.List;
import java.util.concurrent.TimeUnit;

import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.herbiboars.HerbiboarPlugin;
import net.runelite.client.plugins.herbiboars.HerbiboarSearchSpot;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoHerbiboarScript extends Script {
    @Setter
    private HerbiboarPlugin herbiboarPlugin;
    private AutoHerbiboarState state = AutoHerbiboarState.INITIALIZING;
    private boolean attackedTunnel;
    private static final WorldPoint BANK_LOCATION = new WorldPoint(3769, 3898, 0);
    private static final WorldPoint RETURN_LOCATION = new WorldPoint(3727, 3892, 0);
    private int tunnelAttackAttempts = 0;
    private java.util.Set<WorldPoint> blacklistedTunnels = new java.util.HashSet<>();
    private AutoHerbiboarConfig config; // store config for access
    private long stateStartTime = System.currentTimeMillis(); // track state start time for timeouts
    
    public static String version = "1.1";
    

    public AutoHerbiboarState getCurrentState() {
        return state;
    }
    
    public void handleConfusionMessage() {
        changeState(AutoHerbiboarState.START);
        attackedTunnel = false;
        tunnelAttackAttempts = 0;
    }
    
    public void handleDeadEndTunnel() {
        log.info("dead end tunnel detected - 'nothing seems to be out of place here.'");
        if (herbiboarPlugin != null) { // if herbiboar plugin is available
            int finishId = herbiboarPlugin.getFinishId(); // get the current finish id
            if (finishId > 0) { // if we have a valid finish id
                WorldPoint deadEndTunnel = herbiboarPlugin.getEndLocations().get(finishId - 1); // get tunnel location
                if (deadEndTunnel != null) { // if the tunnel location is valid
                    blacklistedTunnels.add(deadEndTunnel); // add it to our blacklist
                    log.info("blacklisted tunnel at " + deadEndTunnel + ". total blacklisted: " + blacklistedTunnels.size());
                }
            }
        }
        changeState(AutoHerbiboarState.START); // reset to start state
        attackedTunnel = false; // reset attack flag
        tunnelAttackAttempts = 0; // reset attempt counter
    }
    
    // helper method to change state with timeout reset
    private void changeState(AutoHerbiboarState newState) {
        if (newState != state) {
            log.info("state change: " + state + " -> " + newState);
            state = newState;
            stateStartTime = System.currentTimeMillis(); // reset timeout timer
        }
    }
    
    
    private boolean isNearBank() {
        double bankDistance = Rs2Player.getWorldLocation().distanceTo(BANK_LOCATION); // calculate distance to bank
        boolean nearBank = bankDistance <= 5; // check if we are close enough to bank
        
        log.info("bank distance check - distance: " + bankDistance + ", near bank: " + nearBank);
        
        return nearBank;
    }
    
    private void manageHunterPotions(AutoHerbiboarConfig config) {
        if (!config.useHunterPotions()) { // if hunter potions are disabled
            log.info("hunter potions disabled - skipping");
            return;
        }
        
        int currentHunterLevel = Microbot.getClient().getBoostedSkillLevel(Skill.HUNTER); // get current hunter level
        log.info("current hunter level: " + currentHunterLevel);
        
        if (currentHunterLevel >= 80) { // if hunter level is high enough
            log.info("hunter level sufficient - no potion needed");
            return;
        }
        
        log.info("hunter level below 80 - checking for potions");
        
        // check for hunter potions in order of doses
        if (Rs2Inventory.contains(9998)) { // 4-dose hunter potion
            log.info("drinking 4-dose hunter potion");
            Rs2Inventory.interact(9998, "Drink"); // drink 4-dose potion
        } else if (Rs2Inventory.contains(10000)) { // 3-dose hunter potion
            log.info("drinking 3-dose hunter potion");
            Rs2Inventory.interact(10000, "Drink"); // drink 3-dose potion
        } else if (Rs2Inventory.contains(10002)) { // 2-dose hunter potion
            log.info("drinking 2-dose hunter potion");
            Rs2Inventory.interact(10002, "Drink"); // drink 2-dose potion
        } else if (Rs2Inventory.contains(10004)) { // 1-dose hunter potion
            log.info("drinking 1-dose hunter potion");
            Rs2Inventory.interact(10004, "Drink"); // drink 1-dose potion
        } else {
            log.info("no hunter potions found in inventory");
        }
    }
    
    private void manageRunEnergy(AutoHerbiboarConfig config) {
        int currentEnergy = Microbot.getClient().getEnergy(); // get current run energy
        boolean runEnabled = Microbot.getVarbitPlayerValue(173) == 1; // check if run is enabled
        
        log.info("current run energy: " + currentEnergy);
        log.info("run enabled: " + runEnabled);
        
        // enable run if energy is >= 10 and run is disabled
        if (currentEnergy >= 10 && !runEnabled) {
            log.info("enabling run - energy sufficient");
            Rs2Player.toggleRunEnergy(true); // enable run
            boolean runToggled = sleepUntil(() -> Microbot.getVarbitPlayerValue(173) == 1, 2000); // wait for run to enable
            log.info("run enabled successfully: " + runToggled);
        }
        
        if (currentEnergy >= 20) { // if energy is sufficient
            log.info("run energy sufficient - no energy restoration needed");
            return;
        }
        
        AutoHerbiboarConfig.RunEnergyOption energyOption = config.runEnergyOption(); // get energy option
        log.info("run energy low - using option: " + energyOption);
        
        switch (energyOption) {
            case STAMINA_POTION:
                if (Rs2Inventory.contains(ItemID._4DOSESTAMINA, ItemID._3DOSESTAMINA, ItemID._2DOSESTAMINA, ItemID._1DOSESTAMINA)) {
                    log.info("drinking stamina potion");
                    // drink highest dose available
                    if (Rs2Inventory.contains(ItemID._4DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._4DOSESTAMINA, "Drink"); // drink 4-dose
                    } else if (Rs2Inventory.contains(ItemID._3DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._3DOSESTAMINA, "Drink"); // drink 3-dose
                    } else if (Rs2Inventory.contains(ItemID._2DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._2DOSESTAMINA, "Drink"); // drink 2-dose
                    } else if (Rs2Inventory.contains(ItemID._1DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._1DOSESTAMINA, "Drink"); // drink 1-dose
                    }
                } else {
                    log.info("no stamina potions found in inventory");
                }
                break;
            case SUPER_ENERGY_POTION:
                if (Rs2Inventory.contains(ItemID._4DOSE2ENERGY, ItemID._3DOSE2ENERGY, ItemID._2DOSE2ENERGY, ItemID._1DOSE2ENERGY)) {
                    log.info("drinking super energy potion");
                    // drink highest dose available
                    if (Rs2Inventory.contains(ItemID._4DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._4DOSE2ENERGY, "Drink"); // drink 4-dose
                    } else if (Rs2Inventory.contains(ItemID._3DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._3DOSE2ENERGY, "Drink"); // drink 3-dose
                    } else if (Rs2Inventory.contains(ItemID._2DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._2DOSE2ENERGY, "Drink"); // drink 2-dose
                    } else if (Rs2Inventory.contains(ItemID._1DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._1DOSE2ENERGY, "Drink"); // drink 1-dose
                    }
                } else {
                    log.info("no super energy potions found in inventory");
                }
                break;
            case ENERGY_POTION:
                if (Rs2Inventory.contains(ItemID._4DOSE1ENERGY, ItemID._3DOSE1ENERGY, ItemID._2DOSE1ENERGY, ItemID._1DOSE1ENERGY)) {
                    log.info("drinking energy potion");
                    // drink highest dose available
                    if (Rs2Inventory.contains(ItemID._4DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._4DOSE1ENERGY, "Drink"); // drink 4-dose
                    } else if (Rs2Inventory.contains(ItemID._3DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._3DOSE1ENERGY, "Drink"); // drink 3-dose
                    } else if (Rs2Inventory.contains(ItemID._2DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._2DOSE1ENERGY, "Drink"); // drink 2-dose
                    } else if (Rs2Inventory.contains(ItemID._1DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._1DOSE1ENERGY, "Drink"); // drink 1-dose
                    }
                } else {
                    log.info("no energy potions found in inventory");
                }
                break;
            case STRANGE_FRUIT:
                if (Rs2Inventory.contains(ItemID.MACRO_TRIFFIDFRUIT)) {
                    log.info("eating strange fruit");
                    Rs2Inventory.interact(ItemID.MACRO_TRIFFIDFRUIT, "Eat"); // eat strange fruit
                } else {
                    log.info("no strange fruit found in inventory");
                }
                break;
            case NONE:
            default:
                log.info("no energy restoration configured");
                break;
        }
    }
    
    private void dropConfiguredItems(AutoHerbiboarConfig config) {
        log.info("checking for items to drop");
        
        if (config.dropEmptyVials()) { // if empty vials should be dropped
            log.info("dropping empty vials");
            dropIfPresent(ItemID.VIAL_EMPTY); // drop empty vials
        }
        if (config.dropSmallFossil()) {
            dropIfPresent(ItemID.FOSSIL_SMALL_UNID, ItemID.FOSSIL_SMALL_1, ItemID.FOSSIL_SMALL_2, 
                         ItemID.FOSSIL_SMALL_3, ItemID.FOSSIL_SMALL_4, ItemID.FOSSIL_SMALL_5);
        }
        if (config.dropMediumFossil()) {
            dropIfPresent(ItemID.FOSSIL_MEDIUM_UNID, ItemID.FOSSIL_MEDIUM_1, ItemID.FOSSIL_MEDIUM_2, 
                         ItemID.FOSSIL_MEDIUM_3, ItemID.FOSSIL_MEDIUM_4, ItemID.FOSSIL_MEDIUM_5);
        }
        if (config.dropLargeFossil()) {
            dropIfPresent(ItemID.FOSSIL_LARGE_UNID, ItemID.FOSSIL_LARGE_1, ItemID.FOSSIL_LARGE_2, 
                         ItemID.FOSSIL_LARGE_3, ItemID.FOSSIL_LARGE_4, ItemID.FOSSIL_LARGE_5);
        }
        if (config.dropRareFossil()) {
            dropIfPresent(ItemID.FOSSIL_RARE_UNID, ItemID.FOSSIL_RARE_1, ItemID.FOSSIL_RARE_2, 
                         ItemID.FOSSIL_RARE_3, ItemID.FOSSIL_RARE_4, ItemID.FOSSIL_RARE_5, ItemID.FOSSIL_RARE_6);
        }
        if (config.dropGuam()) {
            dropIfPresent(ItemID.UNIDENTIFIED_GUAM);
        }
        if (config.dropMarrentill()) {
            dropIfPresent(ItemID.UNIDENTIFIED_MARENTILL);
        }
        if (config.dropTarromin()) {
            dropIfPresent(ItemID.UNIDENTIFIED_TARROMIN);
        }
        if (config.dropHarralander()) {
            dropIfPresent(ItemID.UNIDENTIFIED_HARRALANDER);
        }
        if (config.dropRanarr()) {
            dropIfPresent(ItemID.UNIDENTIFIED_RANARR);
        }
        if (config.dropToadflax()) {
            dropIfPresent(ItemID.UNIDENTIFIED_TOADFLAX);
        }
        if (config.dropIrit()) {
            dropIfPresent(ItemID.UNIDENTIFIED_IRIT);
        }
        if (config.dropAvantoe()) {
            dropIfPresent(ItemID.UNIDENTIFIED_AVANTOE);
        }
        if (config.dropKwuarm()) {
            dropIfPresent(ItemID.UNIDENTIFIED_KWUARM);
        }
        if (config.dropSnapdragon()) {
            dropIfPresent(ItemID.UNIDENTIFIED_SNAPDRAGON);
        }
        if (config.dropCadantine()) {
            dropIfPresent(ItemID.UNIDENTIFIED_CADANTINE);
        }
        if (config.dropLantadyme()) {
            dropIfPresent(ItemID.UNIDENTIFIED_LANTADYME);
        }
        if (config.dropDwarfWeed()) {
            dropIfPresent(ItemID.UNIDENTIFIED_DWARF_WEED);
        }
        if (config.dropTorstol()) {
            dropIfPresent(ItemID.UNIDENTIFIED_TORSTOL);
        }
    }
    
    private void dropIfPresent(int... itemIds) {
        for (int itemId : itemIds) {
            if (Rs2Inventory.contains(itemId)) { // if we have this item in inventory
                log.info("dropping item id: " + itemId);
                Rs2Inventory.drop(itemId); // drop the item
            }
        }
    }

    private boolean needsToBank(AutoHerbiboarConfig config) {
        boolean inventoryFull = Rs2Inventory.isFull(); // check if inventory is full
        boolean hasSetup = hasRequiredInventorySetup(config); // check if we have required setup
        boolean needsBank = inventoryFull || !hasSetup; // determine if we need to bank
        
        log.info("=== Banking Check ===");
        log.info("inventory full: " + inventoryFull);
        log.info("has required setup: " + hasSetup);
        log.info("needs to bank: " + needsBank);
        
        return needsBank;
    }

    private boolean hasRequiredInventorySetup(AutoHerbiboarConfig config) {
        AutoHerbiboarConfig.RunEnergyOption energyOption = config.runEnergyOption(); // get energy option setting
        boolean hasEnergyItems = true; // assume we have energy items unless proven otherwise
        
        // check energy items based on configuration
        switch (energyOption) {
            case STAMINA_POTION:
                hasEnergyItems = Rs2Inventory.contains(ItemID._4DOSESTAMINA, ItemID._3DOSESTAMINA, ItemID._2DOSESTAMINA, ItemID._1DOSESTAMINA);
                break;
            case SUPER_ENERGY_POTION:
                hasEnergyItems = Rs2Inventory.contains(ItemID._4DOSE2ENERGY, ItemID._3DOSE2ENERGY, ItemID._2DOSE2ENERGY, ItemID._1DOSE2ENERGY);
                break;
            case ENERGY_POTION:
                hasEnergyItems = Rs2Inventory.contains(ItemID._4DOSE1ENERGY, ItemID._3DOSE1ENERGY, ItemID._2DOSE1ENERGY, ItemID._1DOSE1ENERGY);
                break;
            case STRANGE_FRUIT:
                hasEnergyItems = Rs2Inventory.contains(ItemID.MACRO_TRIFFIDFRUIT);
                break;
            case NONE:
            default:
                hasEnergyItems = true; // no energy items required
                break;
        }
        
        boolean hasHerbSack = true; // assume we have herb sack unless required and missing
        if (config.useHerbSack()) {
            hasHerbSack = Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN); // check for herb sack
        }
        
        boolean hasMagicSecateurs = true; // assume we have secateurs unless required and missing
        if (config.useMagicSecateurs()) {
            hasMagicSecateurs = Rs2Equipment.isWearing(ItemID.FAIRY_ENCHANTED_SECATEURS) ||
                               Rs2Inventory.contains(ItemID.FAIRY_ENCHANTED_SECATEURS); // check for secateurs
        }
        
        boolean hasHunterPotions = true; // assume we have potions unless required and missing
        if (config.useHunterPotions()) {
            hasHunterPotions = Rs2Inventory.contains(9998, 10000, 10002, 10004); // check for hunter potions
        }
        
        boolean hasCompleteSetup = hasEnergyItems && hasHerbSack && hasMagicSecateurs && hasHunterPotions; // check all requirements
        
        log.info("=== Inventory Setup Check ===");
        log.info("energy option: " + energyOption);
        log.info("has energy items: " + hasEnergyItems);
        log.info("needs herb sack: " + config.useHerbSack() + ", has herb sack: " + hasHerbSack);
        log.info("needs secateurs: " + config.useMagicSecateurs() + ", has secateurs: " + hasMagicSecateurs);
        log.info("needs hunter potions: " + config.useHunterPotions() + ", has potions: " + hasHunterPotions);
        log.info("has complete setup: " + hasCompleteSetup);
        
        return hasCompleteSetup;
    }

    public boolean run(AutoHerbiboarConfig config) {
        this.config = config; // store config for debug access
        Microbot.enableAutoRunOn = false; // disable auto run
        changeState(AutoHerbiboarState.INITIALIZING); // start in initializing state
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                long startTime = System.currentTimeMillis(); // remember when this loop iteration started
                
                if (!super.run()) { // if the parent script tells us to stop
                    log.info("super.run() returned false - stopping script");
                    return;
                }
                if (!Microbot.isLoggedIn()) { // if we aren't logged into the game
                    log.info("not logged in - waiting");
                    return;
                }
                if (herbiboarPlugin == null) { // if herbiboar plugin is not available
                    log.info("herbiboar plugin is null - waiting");
                    return;
                }
                
                if (!Rs2Player.isMoving() && !Rs2Player.isInteracting()) {
                    dropConfiguredItems(config);
                    manageRunEnergy(config);
                    manageHunterPotions(config);
                }
                
                if (state != AutoHerbiboarState.INITIALIZING && state != AutoHerbiboarState.CHECK_AUTO_RETALIATE && 
                    state != AutoHerbiboarState.TUNNEL && state != AutoHerbiboarState.HARVEST) {
                    boolean needsBank = needsToBank(config); // if we need to bank
                    if (needsBank) {
                        log.info("transitioning to BANK state - need to bank");
                        changeState(AutoHerbiboarState.BANK); // switch to banking
                    } else if (isNearBank() && state == AutoHerbiboarState.START) { // if we have setup and are near bank in start state
                        log.info("transitioning to RETURN_FROM_ISLAND state - ready to return");
                        changeState(AutoHerbiboarState.RETURN_FROM_ISLAND); // switch to returning
                    }
                }
                if ((Rs2Player.isMoving() || Rs2Player.isInteracting()) && 
                    (state == AutoHerbiboarState.START || state == AutoHerbiboarState.TRAIL || 
                     state == AutoHerbiboarState.TUNNEL || state == AutoHerbiboarState.HARVEST)) { // if we are busy in action states
                    log.info("skipping loop - player is moving or interacting in state: " + state);
                    return; // skip this loop iteration
                }
                
                switch (state) {
                    case INITIALIZING:
                        log.info("=== INITIALIZING State ===");
                        log.info("use herb sack: " + config.useHerbSack());
                        log.info("has closed herb sack: " + Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK));
                        log.info("has open herb sack: " + Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK_OPEN));
                        
                        Microbot.status = "Starting..."; // update status
                        if (config.useHerbSack() && Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK)) { // if we should use herb sack and have a closed one
                            log.info("opening herb sack");
                            Rs2Inventory.interact(ItemID.SLAYER_HERB_SACK, "Open"); // open the herb sack
                            boolean opened = sleepUntil(() -> Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK_OPEN), 2000); // wait for it to open
                            log.info("herb sack opened successfully: " + opened);
                        }
                        
                        log.info("transitioning to CHECK_AUTO_RETALIATE state");
                        changeState(AutoHerbiboarState.CHECK_AUTO_RETALIATE); // switch to auto retaliate check
                        break;
                    case CHECK_AUTO_RETALIATE:
                        log.info("=== CHECK_AUTO_RETALIATE State ===");
                        
                        Microbot.status = "Checking auto retaliate..."; // update status
                        int autoRetaliateValue = Microbot.getVarbitPlayerValue(172); // get current auto retaliate setting
                        
                        log.info("auto retaliate value: " + autoRetaliateValue);
                        
                        if (autoRetaliateValue == 0) { // if auto retaliate is disabled
                            log.info("enabling auto retaliate");
                            Microbot.status = "Enabling auto retaliate..."; // update status
                            Rs2Tab.switchTo(InterfaceTab.COMBAT); // switch to combat tab
                            boolean tabSwitched = sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 2000); // wait for tab switch
                            
                            log.info("combat tab opened: " + tabSwitched);
                            
                            Rs2Widget.clickWidget(38862879); // click auto retaliate button
                            boolean enabled = sleepUntil(() -> Microbot.getVarbitPlayerValue(172) == 1, 3000); // wait for setting to change
                            
                            log.info("auto retaliate enabled: " + enabled);
                        } else {
                            log.info("auto retaliate already enabled");
                        }
                        
                        log.info("transitioning to START state");
                        changeState(AutoHerbiboarState.START); // switch to start state
                        break;
                    case START:
                        log.info("=== START State ===");
                        
                        Microbot.status = "Finding start location"; // update status
                        
                        if (herbiboarPlugin.getCurrentGroup() == null) { // if we don't have an active trail group
                            log.info("no active trail group - looking for start location");
                            
                            TileObject start = herbiboarPlugin.getStarts().values().stream()
                                .filter(s -> !blacklistedTunnels.contains(s.getWorldLocation())) // exclude blacklisted tunnels
                                .min(java.util.Comparator.comparing(s -> Rs2Player.getWorldLocation().distanceTo(s.getWorldLocation()))) // find closest
                                .orElse(null);
                            
                            log.info("looking for start location - blacklisted tunnels: " + blacklistedTunnels.size());
                            
                            if (start != null) {
                                log.info("found valid start at: " + start.getWorldLocation());
                            } else {
                                 log.info("no valid start found - clearing blacklist and retrying");
                                blacklistedTunnels.clear(); // clear blacklist and try again
                                start = herbiboarPlugin.getStarts().values().stream()
                                    .min(java.util.Comparator.comparing(s -> Rs2Player.getWorldLocation().distanceTo(s.getWorldLocation()))) // find closest without blacklist
                                    .orElse(null);
                                if (start != null) {
                                    log.info("found start after clearing blacklist: " + start.getWorldLocation());
                                }
                            }
                            
                            if (start != null) {
                                WorldPoint loc = start.getWorldLocation(); // get start location
                                LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), loc); // convert to local point
                                double distance = Rs2Player.getWorldLocation().distanceTo(loc); // calculate distance
                                
                                log.info("start location: " + loc + ", distance: " + distance);
                                
                                if (localPoint == null) { // if location is not loaded
                                    log.info("start location not loaded - walking to area");
                                    Rs2Walker.walkTo(loc); // walk to load the area
                                } else if (distance >= 50) { // if we are too far away
                                    log.info("too far from start - walking closer");
                                    Rs2Walker.walkTo(loc); // walk closer
                                } else if (!Rs2Player.isAnimating() && !Rs2Player.isInteracting()) { // if we are close and not busy
                                    log.info("interacting with start object");
                                    Rs2GameObject.interact(start, "Search"); // search the start object
                                    Rs2Player.waitForAnimation(); // wait for animation to start
                                    boolean finished = sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 3000); // wait for completion
                                    log.info("start interaction completed: " + finished);
                                } else {
                                     log.info("waiting for player to finish current action");
                                }
                            } else {
                                 log.info("no start objects found - waiting");
                            }
                        } else {
                             log.info("active trail group found - transitioning to TRAIL state");
                            changeState(AutoHerbiboarState.TRAIL); // switch to trail following
                        }
                        break;
                    case TRAIL:
                         log.info("=== TRAIL State ===");
                        
                        Microbot.status = "Following trail"; // update status
                        
                        // check for trail state timeout (30 seconds)
                        if (System.currentTimeMillis() - stateStartTime > 30000) {
                            log.info("trail state timeout - resetting to START state");
                            changeState(AutoHerbiboarState.START);
                            break;
                        }
                        
                        int finishId = herbiboarPlugin.getFinishId(); // get finish id
                        
                         log.info("finish id: " + finishId);
                        
                        if (finishId > 0) { // if we found the tunnel
                             log.info("tunnel found - transitioning to TUNNEL state");
                            changeState(AutoHerbiboarState.TUNNEL); // switch to tunnel state
                            break; 
                        }
                        
                        List<HerbiboarSearchSpot> path = herbiboarPlugin.getCurrentPath(); // get current trail path
                         log.info("trail path length: " + path.size());
                        
                        if (!path.isEmpty()) { // if we have a path to follow
                            WorldPoint loc = path.get(path.size() - 1).getLocation(); // get next spot location
                            LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), loc); // convert to local point
                            TileObject object = herbiboarPlugin.getTrailObjects().get(loc); // get the trail object
                            double distance = Rs2Player.getWorldLocation().distanceTo(loc); // calculate distance
                            
                             {
                                log.info("next trail spot: " + loc + ", distance: " + distance);
                                log.info("trail object exists: " + (object != null));
                            }
                            
                            if (localPoint == null) { // if location is not loaded
                                 log.info("trail location not loaded - walking to area");
                                Rs2Walker.walkTo(loc); // walk to load the area
                            } else if (distance >= 50) { // if we are too far away
                                 log.info("too far from trail spot - walking closer");
                                Rs2Walker.walkTo(loc); // walk closer
                            } else if (!Rs2Player.isAnimating() && !Rs2Player.isInteracting()) { // if we are close and not busy
                                if (object != null) { // if we have a trail object
                                     log.info("searching trail object");
                                    Rs2GameObject.interact(object, "Search"); // search the trail object
                                    Rs2Player.waitForAnimation(); // wait for animation to start
                                    boolean finished = sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 3000); // wait for completion
                                     log.info("trail search completed: " + finished);
                                } else {
                                     log.info("no trail object found at location");
                                }
                            } else {
                                 log.info("waiting for player to finish current action");
                            }
                        } else {
                             log.info("no trail path available - waiting");
                        }
                        break;
                    case TUNNEL:
                         log.info("=== TUNNEL State ===");
                        
                        Microbot.status = "Attacking tunnel"; // update status
                        
                        if (!attackedTunnel) { // if we haven't attacked the tunnel yet
                             log.info("tunnel not yet attacked - preparing attack");
                            
                            int tunnelFinishId = herbiboarPlugin.getFinishId(); // get finish id for tunnel
                            
                             log.info("tunnel finish id: " + tunnelFinishId);
                            
                            if (tunnelFinishId > 0) { // if we have a valid finish id
                                WorldPoint finishLoc = herbiboarPlugin.getEndLocations().get(tunnelFinishId - 1); // get tunnel location
                                
                                 {
                                    log.info("finish id: " + tunnelFinishId + ", finish location: " + finishLoc);
                                }
                                
                                if (finishLoc == null) { // if tunnel location is invalid
                                     log.info("finish location is null - resetting to START state");
                                    Microbot.status = "Invalid tunnel location - resetting..."; // update status
                                    changeState(AutoHerbiboarState.START); // reset to start
                                    attackedTunnel = false; // reset attack flag
                                    tunnelAttackAttempts = 0; // reset attempts
                                    break;
                                }
                                
                                LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), finishLoc); // convert to local point
                                TileObject tunnel = herbiboarPlugin.getTunnels().get(finishLoc); // get tunnel object
                                WorldPoint playerLoc = Rs2Player.getWorldLocation(); // get our location
                                double distance = playerLoc.distanceTo(finishLoc); // calculate distance to tunnel
                                
                                 {
                                    log.info("player location: " + playerLoc + ", tunnel location: " + finishLoc + ", distance: " + distance);
                                    log.info("local point: " + localPoint + ", tunnel object exists: " + (tunnel != null));
                                }
                                
                                if (distance > 6) { // if we are too far from tunnel
                                    if (!Rs2Player.isMoving()) { // if we aren't already moving
                                         log.info("walking to tunnel - distance: " + distance);
                                        Rs2Walker.walkTo(finishLoc); // walk to tunnel
                                        boolean walkStarted = sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.getWorldLocation().distanceTo(finishLoc) <= 6, 3000); // wait for walk to start or arrival
                                        log.info("walk to tunnel started: " + walkStarted);
                                    } else {
                                         log.info("already moving towards tunnel - waiting");
                                    }
                                } else if (!Rs2Player.isAnimating() && !Rs2Player.isInteracting()) { // if we are close and not busy
                                     {
                                        log.info("ready to attack tunnel - object: " + tunnel + ", final distance: " + Rs2Player.getWorldLocation().distanceTo(finishLoc));
                                    }
                                    
                                    if (tunnel != null && Rs2Player.getWorldLocation().distanceTo(finishLoc) <= 8) { // if tunnel exists and we're close enough
                                        tunnelAttackAttempts++; // increment attack attempts
                                         log.info("tunnel attack attempt #" + tunnelAttackAttempts);
                                        boolean attackSuccessful = false; // track if attack was successful
                                        
                                        if (tunnelAttackAttempts <= 3) { // try attack action first
                                             log.info("trying Attack action on tunnel");
                                            attackSuccessful = Rs2GameObject.interact(tunnel, "Attack"); // attack the tunnel
                                        } else if (tunnelAttackAttempts <= 6) { // try search action if attack fails
                                             log.info("trying Search action on tunnel");
                                            attackSuccessful = Rs2GameObject.interact(tunnel, "Search"); // search the tunnel
                                        } else { // fallback approach if both actions fail
                                             log.info("fallback attempt - walking closer to tunnel");
                                            try {
                                                 log.info("fallback walking to: " + finishLoc);
                                                double fallbackInitialDistance = Rs2Player.getWorldLocation().distanceTo(finishLoc); // initial distance
                                                Rs2Walker.walkMiniMap(finishLoc); // walk using minimap
                                                boolean walkSuccess = sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(finishLoc) <= 3, 3000); // wait to get close
                                                double fallbackFinalDistance = Rs2Player.getWorldLocation().distanceTo(finishLoc); // final distance
                                                
                                                 {
                                                    log.info("fallback walk - initial: " + fallbackInitialDistance + ", final: " + fallbackFinalDistance + ", success: " + walkSuccess);
                                                }
                                                
                                                attackSuccessful = Rs2GameObject.interact(tunnel, "Attack") || Rs2GameObject.interact(tunnel, "Search"); // try both actions
                                            } catch (Exception e) {
                                                 {
                                                    log.info("fallback walker exception: " + e.getMessage());
                                                    log.info("fallback failed - resetting to START state");
                                                }
                                                Microbot.status = "Tunnel unreachable after retries - resetting..."; // update status
                                                changeState(AutoHerbiboarState.START); // reset to start
                                                attackedTunnel = false; // reset attack flag
                                                tunnelAttackAttempts = 0; // reset attempts
                                            }
                                        }
                                        
                                         log.info("attack attempt result: " + attackSuccessful);
                                        
                                        if (attackSuccessful) { // if attack was successful
                                             log.info("attack successful - marking tunnel as attacked");
                                            attackedTunnel = true; // mark tunnel as attacked
                                            tunnelAttackAttempts = 0; // reset attempts
                                            Rs2Player.waitForAnimation(); // wait for attack animation to start
                                            boolean animationFinished = sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 5000); // wait for completion
                                             log.info("tunnel attack animation finished: " + animationFinished);
                                        } else if (tunnelAttackAttempts >= 10) { // if we've tried too many times
                                             log.info("max attempts reached (" + tunnelAttackAttempts + ") - resetting to START state");
                                            Microbot.status = "Tunnel attack failed after retries - resetting..."; // update status
                                            changeState(AutoHerbiboarState.START); // reset to start
                                            attackedTunnel = false; // reset attack flag
                                            tunnelAttackAttempts = 0; // reset attempts
                                        } else {
                                             log.info("attack failed - will retry next cycle (attempt " + tunnelAttackAttempts + "/10)");
                                        }
                                    } else {
                                         log.info("tunnel object not found or too far - resetting to START state");
                                        Microbot.status = "Tunnel not found - resetting..."; // update status
                                        changeState(AutoHerbiboarState.START); // reset to start
                                        attackedTunnel = false; // reset attack flag
                                        tunnelAttackAttempts = 0; // reset attempts
                                    }
                                }
                            }
                        } else { // if we have already attacked the tunnel
                             log.info("tunnel already attacked - checking for herbiboar");
                            
                            Rs2NpcModel herb = Rs2Npc.getNpc("Herbiboar"); // look for herbiboar npc
                            
                             log.info("herbiboar npc found: " + (herb != null));
                            
                            if (herb != null) { // if herbiboar appeared
                                 log.info("transitioning to HARVEST state");
                                changeState(AutoHerbiboarState.HARVEST); // switch to harvest state
                                tunnelAttackAttempts = 0; // reset attempts
                            } else {
                                 log.info("waiting for herbiboar to appear");
                            }
                        }
                        break;
                    case HARVEST:
                         log.info("=== HARVEST State ===");
                        
                        Microbot.status = "Harvesting herbiboar"; // update status
                        Rs2NpcModel herb = Rs2Npc.getNpc("Herbiboar"); // find herbiboar npc
                        
                         log.info("herbiboar found: " + (herb != null));
                        
                        if (herb != null) { // if herbiboar exists
                            WorldPoint loc = herb.getWorldLocation(); // get herbiboar location
                            double distance = Rs2Player.getWorldLocation().distanceTo(loc); // calculate distance
                            
                             log.info("herbiboar location: " + loc + ", distance: " + distance);
                            
                            if (distance <= 8) { // if we are close enough to harvest
                                 log.info("harvesting herbiboar");
                                Rs2Npc.interact(herb, "Harvest"); // harvest the herbiboar
                                Rs2Player.waitForAnimation(); // wait for harvest animation to start
                                boolean harvestFinished = sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 5000); // wait for completion
                                
                                 log.info("harvest completed: " + harvestFinished);
                                
                                // find next start location after harvest
                                TileObject start = herbiboarPlugin.getStarts().values().stream()
                                    .min(java.util.Comparator.comparing(s -> Rs2Player.getWorldLocation().distanceTo(s.getWorldLocation()))) // find closest start
                                    .orElse(null);
                                
                                 log.info("next start found: " + (start != null));
                                
                                if (start != null) { // if we found a start location
                                    WorldPoint startLoc = start.getWorldLocation(); // get start location
                                    LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), startLoc); // convert to local point
                                    double startDistance = Rs2Player.getWorldLocation().distanceTo(startLoc); // calculate distance to start
                                    
                                     log.info("next start location: " + startLoc + ", distance: " + startDistance);
                                    
                                    if (localPoint == null) { // if start location is not loaded
                                         log.info("walking to next start area");
                                        Rs2Walker.walkTo(startLoc); // walk to load the area
                                    } else if (startDistance >= 20) { // if we are too far from start
                                         log.info("walking closer to next start");
                                        Rs2Walker.walkTo(startLoc); // walk closer
                                    } else if (!Rs2Player.isAnimating() && !Rs2Player.isInteracting()) { // if we are close and not busy
                                         log.info("searching next start object");
                                        Rs2GameObject.interact(start, "Search"); // search the start object
                                    }
                                } else {
                                     log.info("no next start found");
                                }
                                
                                attackedTunnel = false; // reset attack flag for next hunt
                                tunnelAttackAttempts = 0; // reset attempts
                                
                                boolean needBank = needsToBank(config); // check if we need to bank
                                 log.info("needs banking: " + needBank);
                                
                                if (needBank) {
                                     log.info("transitioning to BANK state");
                                    changeState(AutoHerbiboarState.BANK); // switch to banking
                                } else {
                                     log.info("transitioning to START state");
                                    changeState(AutoHerbiboarState.START); // continue hunting
                                }
                            } else {
                                 log.info("too far from herbiboar - moving closer");
                                Rs2Walker.walkTo(loc); // walk closer to herbiboar
                            }
                        } else {
                             log.info("herbiboar not found - may have been harvested already");
                        }
                        break;
                    case BANK:
                         log.info("=== BANK State ===");
                        
                        Microbot.status = "Banking items"; // update status
                        LocalPoint bankLocalPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), BANK_LOCATION); // get bank local point
                        double bankDistance = Rs2Player.getWorldLocation().distanceTo(BANK_LOCATION); // calculate distance to bank
                        
                         {
                            log.info("bank location: " + BANK_LOCATION + ", distance: " + bankDistance);
                            log.info("bank local point exists: " + (bankLocalPoint != null));
                            log.info("bank is open: " + Rs2Bank.isOpen());
                        }
                        
                        if (bankLocalPoint == null) { // if bank area is not loaded
                             log.info("walking to bank area");
                            Rs2Walker.walkTo(BANK_LOCATION); // walk to bank area
                        } else if (bankDistance >= 5) { // if we are too far from bank
                             log.info("walking closer to bank");
                            Rs2Walker.walkTo(BANK_LOCATION); // walk closer to bank
                        } else if (!Rs2Bank.isOpen()) { // if bank is not open
                             log.info("opening bank");
                            Rs2Bank.openBank(); // open the bank
                            boolean bankOpened = sleepUntil(() -> Rs2Bank.isOpen(), 3000); // wait for bank to open
                             log.info("bank opened successfully: " + bankOpened);
                        } else { // if bank is open and we're close enough
                             log.info("performing banking operations");
                            
                            // empty herb sack if we have one
                            if (config.useHerbSack() && Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK_OPEN)) {
                                 log.info("emptying herb sack");
                                Rs2Inventory.interact(ItemID.SLAYER_HERB_SACK_OPEN, "Empty"); // empty the herb sack
                                boolean sackEmptied = sleepUntil(() -> !Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK_OPEN) || 
                                           Rs2Inventory.hasItem("herbs", "grimy"), 3000); // wait for sack to empty
                                 log.info("herb sack emptied: " + sackEmptied);
                            }
                            
                             log.info("depositing all items");
                            Rs2Bank.depositAll(); // deposit all items
                            boolean inventoryCleared = sleepUntil(Rs2Inventory::isEmpty, 3000); // wait for inventory to clear
                             log.info("inventory cleared: " + inventoryCleared);
                            // withdraw herb sack if needed
                            if (config.useHerbSack() && !Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN)) {
                                 log.info("withdrawing herb sack");
                                
                                boolean hasClosedSack = Rs2Bank.hasItem(ItemID.SLAYER_HERB_SACK); // check for closed sack
                                boolean hasOpenSack = Rs2Bank.hasItem(ItemID.SLAYER_HERB_SACK_OPEN); // check for open sack
                                
                                 {
                                    log.info("bank has closed herb sack: " + hasClosedSack);
                                    log.info("bank has open herb sack: " + hasOpenSack);
                                }
                                
                                if (hasClosedSack) {
                                    Rs2Bank.withdrawX(ItemID.SLAYER_HERB_SACK, 1); // withdraw closed sack
                                } else if (hasOpenSack) {
                                    Rs2Bank.withdrawX(ItemID.SLAYER_HERB_SACK_OPEN, 1); // withdraw open sack
                                }
                                
                                boolean sackWithdrawn = sleepUntil(() -> Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN), 3000); // wait for withdrawal
                                 log.info("herb sack withdrawn: " + sackWithdrawn);
                            }
                            // withdraw magic secateurs if needed
                            if (config.useMagicSecateurs() && !Rs2Equipment.isWearing(ItemID.FAIRY_ENCHANTED_SECATEURS) && !Rs2Inventory.contains(ItemID.FAIRY_ENCHANTED_SECATEURS)) {
                                 log.info("withdrawing magic secateurs");
                                
                                boolean hasSecateurs = Rs2Bank.hasItem(ItemID.FAIRY_ENCHANTED_SECATEURS); // check if bank has secateurs
                                 log.info("bank has magic secateurs: " + hasSecateurs);
                                
                                if (hasSecateurs) {
                                    Rs2Bank.withdrawX(ItemID.FAIRY_ENCHANTED_SECATEURS, 1); // withdraw secateurs
                                    boolean secateursWithdrawn = sleepUntil(() -> Rs2Inventory.contains(ItemID.FAIRY_ENCHANTED_SECATEURS), 3000); // wait for withdrawal
                                     log.info("magic secateurs withdrawn: " + secateursWithdrawn);
                                } else {
                                     log.info("magic secateurs not found in bank");
                                }
                            }
                            
                            // equip magic secateurs if in inventory
                            if (config.useMagicSecateurs() && !Rs2Equipment.isWearing(ItemID.FAIRY_ENCHANTED_SECATEURS) && Rs2Inventory.contains(ItemID.FAIRY_ENCHANTED_SECATEURS)) {
                                log.info("equipping magic secateurs");
                                Rs2Inventory.wield(ItemID.FAIRY_ENCHANTED_SECATEURS); // equip the secateurs
                                boolean secateursEquipped = sleepUntil(() -> Rs2Equipment.isWearing(ItemID.FAIRY_ENCHANTED_SECATEURS), 3000); // wait for equipment
                                log.info("magic secateurs equipped: " + secateursEquipped);
                            }
                            // withdraw hunter potions if needed
                            if (config.useHunterPotions() && !Rs2Inventory.contains(9998, 10000, 10002, 10004)) {
                                 log.info("withdrawing hunter potions");
                                
                                boolean hasHunterPotions = Rs2Bank.hasItem(9998); // check if bank has hunter potions
                                 log.info("bank has hunter potions: " + hasHunterPotions);
                                
                                if (hasHunterPotions) {
                                    Rs2Bank.withdrawX(9998, 6); // withdraw hunter potions
                                    boolean potionsWithdrawn = sleepUntil(() -> (Rs2Inventory.count(9998) + Rs2Inventory.count(10000) + Rs2Inventory.count(10002) + Rs2Inventory.count(10004)) >= 6, 3000); // wait for withdrawal
                                     log.info("hunter potions withdrawn: " + potionsWithdrawn);
                                } else {
                                     log.info("hunter potions not found in bank");
                                }
                            }
                            
                            // withdraw energy items based on configuration
                            AutoHerbiboarConfig.RunEnergyOption energyOption = config.runEnergyOption(); // get energy option
                             log.info("withdrawing energy items - option: " + energyOption);
                            
                            switch (energyOption) {
                                case STAMINA_POTION:
                                     log.info("withdrawing stamina potions");
                                    Rs2Bank.withdrawX(ItemID._4DOSESTAMINA, 6); // withdraw stamina potions
                                    boolean staminaWithdrawn = sleepUntil(() -> (Rs2Inventory.count(ItemID._4DOSESTAMINA) + Rs2Inventory.count(ItemID._3DOSESTAMINA) + Rs2Inventory.count(ItemID._2DOSESTAMINA) + Rs2Inventory.count(ItemID._1DOSESTAMINA)) >= 6, 3000);
                                     log.info("stamina potions withdrawn: " + staminaWithdrawn);
                                    break;
                                case SUPER_ENERGY_POTION:
                                     log.info("withdrawing super energy potions");
                                    Rs2Bank.withdrawX(ItemID._4DOSE2ENERGY, 6); // withdraw super energy potions
                                    boolean superEnergyWithdrawn = sleepUntil(() -> (Rs2Inventory.count(ItemID._4DOSE2ENERGY) + Rs2Inventory.count(ItemID._3DOSE2ENERGY) + Rs2Inventory.count(ItemID._2DOSE2ENERGY) + Rs2Inventory.count(ItemID._1DOSE2ENERGY)) >= 6, 3000);
                                     log.info("super energy potions withdrawn: " + superEnergyWithdrawn);
                                    break;
                                case ENERGY_POTION:
                                     log.info("withdrawing energy potions");
                                    Rs2Bank.withdrawX(ItemID._4DOSE1ENERGY, 6); // withdraw energy potions
                                    boolean energyWithdrawn = sleepUntil(() -> (Rs2Inventory.count(ItemID._4DOSE1ENERGY) + Rs2Inventory.count(ItemID._3DOSE1ENERGY) + Rs2Inventory.count(ItemID._2DOSE1ENERGY) + Rs2Inventory.count(ItemID._1DOSE1ENERGY)) >= 6, 3000);
                                     log.info("energy potions withdrawn: " + energyWithdrawn);
                                    break;
                                case STRANGE_FRUIT:
                                     log.info("withdrawing strange fruit");
                                    Rs2Bank.withdrawX(ItemID.MACRO_TRIFFIDFRUIT, 6); // withdraw strange fruit
                                    boolean fruitWithdrawn = sleepUntil(() -> Rs2Inventory.count(ItemID.MACRO_TRIFFIDFRUIT) >= 6, 3000);
                                     log.info("strange fruit withdrawn: " + fruitWithdrawn);
                                    break;
                                case NONE:
                                default:
                                     log.info("no energy items configured - skipping");
                                    break;
                            }
                            
                             log.info("closing bank");
                            Rs2Bank.closeBank(); // close the bank
                            boolean bankClosed = sleepUntil(() -> !Rs2Bank.isOpen(), 3000); // wait for bank to close
                             log.info("bank closed successfully: " + bankClosed);
                            
                             log.info("transitioning to RETURN_FROM_ISLAND state");
                            changeState(AutoHerbiboarState.RETURN_FROM_ISLAND); // switch to return state
                        }
                        break;
                    case RETURN_FROM_ISLAND:
                         log.info("=== RETURN_FROM_ISLAND State ===");
                        
                        Microbot.status = "Returning to island"; // update status
                        LocalPoint returnLocalPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), RETURN_LOCATION); // get return local point
                        double returnDistance = Rs2Player.getWorldLocation().distanceTo(RETURN_LOCATION); // calculate distance to return location
                        
                         {
                            log.info("return location: " + RETURN_LOCATION + ", distance: " + returnDistance);
                            log.info("return local point exists: " + (returnLocalPoint != null));
                        }
                        
                        if (returnLocalPoint == null) { // if return area is not loaded
                             log.info("walking to return area");
                            Rs2Walker.walkTo(RETURN_LOCATION); // walk to return area
                        } else if (returnDistance >= 5) { // if we are too far from return location
                             log.info("walking to return location");
                            Rs2Walker.walkTo(RETURN_LOCATION); // walk to return location
                        } else { // if we are close enough to return location
                             log.info("arrived at return location - transitioning to START state");
                            changeState(AutoHerbiboarState.START); // switch to start hunting again
                        }
                        break;
                }
                
                 {
                    long endTime = System.currentTimeMillis(); // remember when this loop iteration ended
                    long totalTime = endTime - startTime; // calculate how long this loop took to complete
                    log.info("loop execution time: " + totalTime + "ms");
                }
                
            } catch (Exception ex) {
                log.error("autoherbiboar error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }
    @Override
    public void shutdown() {
        log.info("autoherbiboar script shutting down");
        Microbot.status = "IDLE"; // update status
        super.shutdown(); // call parent shutdown
    }
}