package net.runelite.client.plugins.microbot.aiofighter;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.aiofighter.bank.BankerScript;
import net.runelite.client.plugins.microbot.aiofighter.cannon.CannonScript;
import net.runelite.client.plugins.microbot.aiofighter.combat.*;
import net.runelite.client.plugins.microbot.aiofighter.enums.PrayerStyle;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.aiofighter.loot.LootScript;
import net.runelite.client.plugins.microbot.aiofighter.safety.SafetyScript;
import net.runelite.client.plugins.microbot.aiofighter.shop.ShopScript;
import net.runelite.client.plugins.microbot.aiofighter.skill.AttackStyleScript;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.slayer.Rs2Slayer;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "AIO Fighter",
        description = "Microbot Fighter plugin",
        tags = {"fight", "microbot", "misc", "combat", "playerassistant"},
        enabledByDefault = false
)
@Slf4j
public class AIOFighterPlugin extends Plugin {
    public static final String version = "2.0.2 BETA";
    public static boolean needShopping = false;
    private static final String SET = "Set";
    private static final String CENTER_TILE = ColorUtil.wrapWithColorTag("Center Tile", JagexColors.MENU_TARGET);
    // SAFE_SPOT = "Safe Spot";
    private static final String SAFE_SPOT = ColorUtil.wrapWithColorTag("Safe Spot", JagexColors.CHAT_PRIVATE_MESSAGE_TEXT_TRANSPARENT_BACKGROUND);
    private static final String ADD_TO = "Start Fighting:";
    private static final String REMOVE_FROM = "Stop Fighting:";
    private static final String WALK_HERE = "Walk here";
    private static final String ATTACK = "Attack";
    @Getter
    @Setter
    public static int cooldown = 0;
    private final CannonScript cannonScript = new CannonScript();
    private final AttackNpcScript attackNpc = new AttackNpcScript();

    private final FoodScript foodScript = new FoodScript();
    private final LootScript lootScript = new LootScript();
    private final SafeSpot safeSpotScript = new SafeSpot();
    private final FlickerScript flickerScript = new FlickerScript();
    private final UseSpecialAttackScript useSpecialAttackScript = new UseSpecialAttackScript();
    private final BuryScatterScript buryScatterScript = new BuryScatterScript();
    private final AttackStyleScript attackStyleScript = new AttackStyleScript();
    private final BankerScript bankerScript = new BankerScript();
    private final PrayerScript prayerScript = new PrayerScript();
    private final HighAlchScript highAlchScript = new HighAlchScript();
    private final PotionManagerScript potionManagerScript = new PotionManagerScript();
    private final SafetyScript safetyScript = new SafetyScript();
    private final SlayerScript slayerScript = new SlayerScript();
    private final ShopScript shopScript = new ShopScript();
    private final DodgeProjectileScript dodgeScript = new DodgeProjectileScript();
    @Inject
    private AIOFighterConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AIOFighterOverlay playerAssistOverlay;
    @Inject
    private AIOFighterInfoOverlay playerAssistInfoOverlay;
    private MenuEntry lastClick;
    private Point lastMenuOpenedPoint;
    private WorldPoint trueTile;

    protected ScheduledExecutorService initializerExecutor = Executors.newSingleThreadScheduledExecutor();

    @Provides
    public AIOFighterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AIOFighterConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        //initialize any data on startup
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(() -> {
            if (Microbot.getConfigManager() == null) {
                return;
            }
            setState(State.IDLE);
            // Get the future from the reference and cancel it
            ScheduledFuture<?> scheduledFuture = futureRef.get();
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
            // now that no other tasks run, you can shut down:
            executor.shutdown();
        }, 0, 1, TimeUnit.SECONDS);

        if (overlayManager != null) {
            overlayManager.add(playerAssistOverlay);
            overlayManager.add(playerAssistInfoOverlay);
            playerAssistInfoOverlay.myButton.hookMouseListener();
            playerAssistInfoOverlay.blacklistButton.hookMouseListener();
        }
        if (!config.toggleCenterTile() && Microbot.isLoggedIn() && !config.slayerMode())
            setCenter(Rs2Player.getWorldLocation());
        dodgeScript.run(config);
        lootScript.run(config);
        cannonScript.run(config);
        attackNpc.run(config);
        foodScript.run(config);
        safeSpotScript.run(config);
        flickerScript.run(config);
        useSpecialAttackScript.run(config);
        buryScatterScript.run(config);
        attackStyleScript.run(config);
        prayerScript.run(config);
        highAlchScript.run(config);
        potionManagerScript.run(config);
        safetyScript.run(config);
        slayerScript.run(config);
        
        // Configure special attack settings
        if (config.useSpecialAttack() && config.specWeapon() != null) {
            Microbot.getSpecialAttackConfigs()
                    .setSpecialAttack(true)
                    .setSpecialAttackWeapon(config.specWeapon())
                    .setMinimumSpecEnergy(config.specWeapon().getEnergyRequired());
        } else {
            Microbot.getSpecialAttackConfigs()
                    .setSpecialAttack(config.useSpecialAttack());
        }
        
        Rs2Slayer.blacklistedSlayerMonsters = getBlacklistedSlayerNpcs();
        bankerScript.run(config);
        shopScript.run(config);
    }

    protected void shutDown() {
        highAlchScript.shutdown();
        lootScript.shutdown();
        cannonScript.shutdown();
        attackNpc.shutdown();
        dodgeScript.shutdown();
        foodScript.shutdown();
        safeSpotScript.shutdown();
        flickerScript.shutdown();
        useSpecialAttackScript.shutdown();
        buryScatterScript.shutdown();
        attackStyleScript.shutdown();
        bankerScript.shutdown();
        prayerScript.shutdown();
        potionManagerScript.shutdown();
        safetyScript.shutdown();
        slayerScript.shutdown();
        shopScript.shutdown();
        resetLocation();
        Microbot.getSpecialAttackConfigs().reset();
        overlayManager.remove(playerAssistOverlay);
        overlayManager.remove(playerAssistInfoOverlay);
        playerAssistInfoOverlay.myButton.unhookMouseListener();
        playerAssistInfoOverlay.blacklistButton.unhookMouseListener();
    }

    public static void resetLocation() {
        setCenter(new WorldPoint(0, 0, 0));
        setSafeSpot(new WorldPoint(0, 0, 0));
    }

    public static void setCenter(WorldPoint worldPoint)
    {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "centerLocation",
                worldPoint
        );
    }
    // set safe spot
    public static void setSafeSpot(WorldPoint worldPoint)
    {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "safeSpotLocation",
                worldPoint
        );


    }
    // Set remainingSlayerKills
    public static void setRemainingSlayerKills(int remainingSlayerKills) {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "remainingSlayerKills",
                remainingSlayerKills
        );
    }
    // Set slayerLocation
    public static void setSlayerLocationName(String slayerLocation) {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "slayerLocation",
                slayerLocation
        );
    }
    // Set slayerTask
    public static void setSlayerTask(String slayerTask) {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "slayerTask",
                slayerTask
        );
    }
    // Set slayerTaskWeaknessThreshold
    public static void setSlayerTaskWeaknessThreshold(int slayerTaskWeaknessThreshold) {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "slayerTaskWeaknessThreshold",
                slayerTaskWeaknessThreshold
        );
    }
    // Set slayerTaskWeaknessItem
    public static void setSlayerTaskWeaknessItem(String slayerTaskWeaknessItem) {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "slayerTaskWeaknessItem",
                slayerTaskWeaknessItem
        );
    }
    // Set slayerHasTaskWeakness
    public static void setSlayerHasTaskWeakness(boolean slayerHasTaskWeakness) {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "slayerHasTaskWeakness",
                slayerHasTaskWeakness
        );
    }
    // Set currentInventorySetup
    public static void setCurrentSlayerInventorySetup(InventorySetup currentInventorySetup) {
        Microbot.log("Setting current inventory setup to: " + currentInventorySetup.getName());
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "currentInventorySetup",
                currentInventorySetup
        );
    }
    // Get currentInventorySetup
    public static InventorySetup getCurrentSlayerInventorySetup() {
        return Microbot.getConfigManager().getConfiguration(
                AIOFighterConfig.GROUP,
                "currentInventorySetup",
                InventorySetup.class
        );
    }
    // Get defaultInventorySetup
    public static InventorySetup getDefaultInventorySetup() {
        return Microbot.getConfigManager().getConfiguration(
                AIOFighterConfig.GROUP,
                "defaultInventorySetup",
                InventorySetup.class
        );
    }
    // Add NPC to blacklist blacklistedSlayerNpcs
    public static void addBlacklistedSlayerNpcs(String npcName) {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "blacklistedSlayerNpcs",
                Microbot.getConfigManager().getConfiguration(
                        AIOFighterConfig.GROUP,
                        "blacklistedSlayerNpcs",
                        String.class
                ) + npcName + ","
        );
    }
    // Get blacklistedSlayerNpcs as a list
    public static List<String> getBlacklistedSlayerNpcs() {
        return Arrays.asList(Microbot.getConfigManager().getConfiguration(
                AIOFighterConfig.GROUP,
                "blacklistedSlayerNpcs",
                String.class
        ).toString().split(","));
    }
    //set Inventory Setup
    private void setInventorySetup(InventorySetup inventorySetup) {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "inventorySetupHidden",
                inventorySetup
        );
    }


    public static State getState() {
        return Microbot.getConfigManager().getConfiguration(
                AIOFighterConfig.GROUP,
                "state",
                State.class
        );
    }

    public static void setState(State state) {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "state",
                state
        );
    }
    public static String getNpcAttackList() {
       return Microbot.getConfigManager().getConfiguration(
               AIOFighterConfig.GROUP,
                "monster"
        );
    }
    public static void addNpcToList(String npcName) {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "monster",
                getNpcAttackList() + npcName + ","
        );

    }
    public static void removeNpcFromList(String npcName) {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "monster",
                Arrays.stream(getNpcAttackList().split(","))
                        .filter(n -> !n.equalsIgnoreCase(npcName))
                        .collect(Collectors.joining(","))
        );
    }

    // set attackable npcs
    public static void setAttackableNpcs(String npcNames) {
        Microbot.getConfigManager().setConfiguration(
                AIOFighterConfig.GROUP,
                "monster",
                npcNames
        );
    }

    private String getNpcNameFromMenuEntry(String menuTarget) {
        return menuTarget.replaceAll("<[^>]*>|\\(.*\\)", "").trim();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getMessage().contains("reach that")) {
            AttackNpcScript.skipNpc();
        }
    }
    // on setting change
    @Subscribe
    public void onConfigChanged(ConfigChanged event) {


        if (event.getKey().equals("Safe Spot")) {

            if (!config.toggleSafeSpot()) {
                // reset safe spot to default
                setSafeSpot(new WorldPoint(0, 0, 0));
            }
        }
        if(event.getKey().equals("Combat")) {
            if (!config.toggleCombat() && config.toggleCenterTile()) {
                setCenter(new WorldPoint(0, 0, 0));
            }
            if (config.toggleCombat() && !config.toggleCenterTile()) {
                setCenter(Rs2Player.getWorldLocation());
            }

        }
        // Handle special attack weapon config changes
        if (event.getKey().equals("Use special attack") || event.getKey().equals("Spec weapon")) {
            if (config.useSpecialAttack() && config.specWeapon() != null) {
                Microbot.getSpecialAttackConfigs()
                        .setSpecialAttack(true)
                        .setSpecialAttackWeapon(config.specWeapon())
                        .setMinimumSpecEnergy(config.specWeapon().getEnergyRequired());
            } else {
                Microbot.getSpecialAttackConfigs().reset();
            }
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        Projectile projectile = event.getProjectile();
        if (projectile.getTargetActor() == null) {
            //Projectiles that have targetActor null are targeting a WorldPoint and are dodgeable.
            dodgeScript.projectiles.add(event.getProjectile());
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        try {
            //execute flicker script
            if(config.togglePrayer())
                flickerScript.onGameTick();
        } catch (Exception e) {
            log.info("AIO Fighter Plugin onGameTick Error: " + e.getMessage());
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        try {
            if(config.togglePrayer())
                flickerScript.onNpcDespawned(npcDespawned);
        } catch (Exception e) {
            log.info("AIO Fighter Plugin onNpcDespawned Error: " + e.getMessage());
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event){
        try {
            if (event.getActor() != Microbot.getClient().getLocalPlayer()) return;
            final Hitsplat hitsplat = event.getHitsplat();

            if ((hitsplat.isMine()) && event.getActor().getInteracting() instanceof NPC && config.togglePrayer() && (config.prayerStyle() == PrayerStyle.LAZY_FLICK) || (config.prayerStyle() == PrayerStyle.PERFECT_LAZY_FLICK) || (config.prayerStyle() == PrayerStyle.MIXED_LAZY_FLICK)) {
                flickerScript.resetLastAttack(true);
                Rs2Prayer.disableAllPrayers();
                if (config.toggleQuickPray())
                    Rs2Prayer.toggleQuickPrayer(false);


            }
        } catch (Exception e) {
            log.info("AIO Fighter Plugin onHitsplatApplied Error: " + e.getMessage());
        }
    }
    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        lastMenuOpenedPoint = Microbot.getClient().getMouseCanvasPosition();
        trueTile = getSelectedWorldPoint();
    }
    @Subscribe
    private void onMenuEntryAdded(MenuEntryAdded event) {
        if (Microbot.getClient().isKeyPressed(KeyCode.KC_SHIFT) && event.getOption().equals(WALK_HERE) && event.getTarget().isEmpty() && config.toggleCenterTile()) {
            addMenuEntry(event, SET, CENTER_TILE, 1);
        }
        if (Microbot.getClient().isKeyPressed(KeyCode.KC_SHIFT) && event.getOption().equals(WALK_HERE) && event.getTarget().isEmpty()) {
            addMenuEntry(event, SET, SAFE_SPOT, 1);
        }
        if (event.getOption().equals(ATTACK) && config.attackableNpcs().contains(getNpcNameFromMenuEntry(Text.removeTags(event.getTarget())))) {
            addMenuEntry(event, REMOVE_FROM, event.getTarget(), 1);
        }
        if (event.getOption().equals(ATTACK) && !config.attackableNpcs().contains(getNpcNameFromMenuEntry(Text.removeTags(event.getTarget())))) {
            addMenuEntry(event, ADD_TO, event.getTarget(), 1);
        }

    }

    private WorldPoint getSelectedWorldPoint() {
        if (Microbot.getClient().getWidget(ComponentID.WORLD_MAP_MAPVIEW) == null) {
            if (Microbot.getClient().getSelectedSceneTile() != null) {
                return Microbot.getClient().isInInstancedRegion() ?
                        WorldPoint.fromLocalInstance(Microbot.getClient(), Microbot.getClient().getSelectedSceneTile().getLocalLocation()) :
                        Microbot.getClient().getSelectedSceneTile().getWorldLocation();
            }
        } else {
            return calculateMapPoint(Microbot.getClient().isMenuOpen() ? lastMenuOpenedPoint : Microbot.getClient().getMouseCanvasPosition());
        }
        return null;
    }
    public WorldPoint calculateMapPoint(Point point) {
        WorldMap worldMap = Microbot.getClient().getWorldMap();
        float zoom = worldMap.getWorldMapZoom();
        final WorldPoint mapPoint = new WorldPoint(worldMap.getWorldMapPosition().getX(), worldMap.getWorldMapPosition().getY(), 0);
        final Point middle = mapWorldPointToGraphicsPoint(mapPoint);

        if (point == null || middle == null) {
            return null;
        }

        final int dx = (int) ((point.getX() - middle.getX()) / zoom);
        final int dy = (int) ((-(point.getY() - middle.getY())) / zoom);

        return mapPoint.dx(dx).dy(dy);
    }
    public Point mapWorldPointToGraphicsPoint(WorldPoint worldPoint) {
        WorldMap worldMap = Microbot.getClient().getWorldMap();

        float pixelsPerTile = worldMap.getWorldMapZoom();

        Widget map = Microbot.getClient().getWidget(ComponentID.WORLD_MAP_MAPVIEW);
        if (map != null) {
            Rectangle worldMapRect = map.getBounds();

            int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);
            int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);

            Point worldMapPosition = worldMap.getWorldMapPosition();

            int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
            int yTileOffset = (yTileMax - worldPoint.getY() - 1) * -1;
            int xTileOffset = worldPoint.getX() + widthInTiles / 2 - worldMapPosition.getX();

            int xGraphDiff = ((int) (xTileOffset * pixelsPerTile));
            int yGraphDiff = (int) (yTileOffset * pixelsPerTile);

            yGraphDiff -= (int) (pixelsPerTile - Math.ceil(pixelsPerTile / 2));
            xGraphDiff += (int) (pixelsPerTile - Math.ceil(pixelsPerTile / 2));

            yGraphDiff = worldMapRect.height - yGraphDiff;
            yGraphDiff += (int) worldMapRect.getY();
            xGraphDiff += (int) worldMapRect.getX();

            return new Point(xGraphDiff, yGraphDiff);
        }
        return null;
    }
    private void onMenuOptionClicked(MenuEntry entry) {



        if (entry.getOption().equals(SET) && entry.getTarget().equals(CENTER_TILE)) {
            setCenter(trueTile);
        }
        if (entry.getOption().equals(SET) && entry.getTarget().equals(SAFE_SPOT)) {
            setSafeSpot(trueTile);
        }


        if (entry.getType() != MenuAction.WALK) {
            lastClick = entry;
        }
    }


    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event.getMenuOption().equals(ADD_TO)) {
            addNpcToList(getNpcNameFromMenuEntry(event.getMenuTarget()));
        }
        if (event.getMenuOption().equals(REMOVE_FROM)) {
            removeNpcFromList(getNpcNameFromMenuEntry(event.getMenuTarget()));
        }
    }
    private void addMenuEntry(MenuEntryAdded event, String option, String target, int position) {
        List<MenuEntry> entries = new LinkedList<>(Arrays.asList(Microbot.getClient().getMenuEntries()));

        if (entries.stream().anyMatch(e -> e.getOption().equals(option) && e.getTarget().equals(target))) {
            return;
        }

        Microbot.getClient().createMenuEntry(position)
                .setOption(option)
                .setTarget(target)
                .setParam0(event.getActionParam0())
                .setParam1(event.getActionParam1())
                .setIdentifier(event.getIdentifier())
                .setType(MenuAction.RUNELITE)
                .onClick(this::onMenuOptionClicked);
    }
}
