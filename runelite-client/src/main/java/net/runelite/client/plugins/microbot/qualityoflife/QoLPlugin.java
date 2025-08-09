package net.runelite.client.plugins.microbot.qualityoflife;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotPlugin;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.qualityoflife.enums.WintertodtActions;
import net.runelite.client.plugins.microbot.qualityoflife.managers.CraftingManager;
import net.runelite.client.plugins.microbot.qualityoflife.managers.FiremakingManager;
import net.runelite.client.plugins.microbot.qualityoflife.managers.FletchingManager;
import net.runelite.client.plugins.microbot.qualityoflife.managers.GemCuttingManager;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.*;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.bank.BankpinScript;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.pvp.PvpScript;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.wintertodt.WintertodtOverlay;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.wintertodt.WintertodtScript;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.FieldUtil;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.globval.WidgetIndices;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.SplashScreen;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import org.apache.commons.lang3.reflect.FieldUtils;


import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static net.runelite.client.plugins.microbot.qualityoflife.scripts.wintertodt.WintertodtScript.isInWintertodtRegion;
import static net.runelite.client.plugins.microbot.util.Global.awaitExecutionUntil;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + "QoL",
        description = "Quality of Life Plugin",
        tags = {"QoL", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class QoLPlugin extends Plugin {
    public static final List<NewMenuEntry> bankMenuEntries = new LinkedList<>();
    public static final List<NewMenuEntry> furnaceMenuEntries = new LinkedList<>();
    public static final List<NewMenuEntry> anvilMenuEntries = new LinkedList<>();
    private static final AtomicReference<List<?>> pluginList = new AtomicReference<>();
    private static final int HALF_ROTATION = 1024;
    private static final int FULL_ROTATION = 2048;
    private static final int PITCH_INDEX = 0;
    private static final int YAW_INDEX = 1;
    private static final BufferedImage SWITCHER_ON_IMG = getImageFromConfigResource("switcher_on");
    private static final BufferedImage STAR_ON_IMG = getImageFromConfigResource("star_on");
    public static InventorySetup loadoutToLoad = null;
    private static GameState lastGameState = GameState.UNKNOWN;
    private final int[] deltaCamera = new int[3];
    private final int[] previousCamera = new int[3];

    public static NewMenuEntry workbenchMenuEntry;
    public static boolean recordActions = false;
    public static boolean executeBankActions = false;
    public static boolean executeFurnaceActions = false;
    public static boolean executeAnvilActions = false;
    public static boolean executeWorkbenchActions = false;
    public static boolean executeLoadoutActions = false;
    private final String BANK_OPTION = "Bank";
    private final String SMELT_OPTION = "Smelt";
    private final String SMITH_OPTION = "Smith";
    @Inject
    public ConfigManager configManager;
    @Inject
    WintertodtScript wintertodtScript;
    @Inject
    private QoLConfig config;
    @Inject
    private QoLScript qoLScript;
    @Inject
    private AutoRunScript autoRunScript;
    @Inject
    private SpecialAttackScript specialAttackScript;
    @Inject
    private AutoItemDropperScript autoItemDropperScript;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private QoLOverlay qoLOverlay;
    @Inject
    private WintertodtOverlay wintertodtOverlay;
    @Inject
    private QolCannonScript cannonScript;
    @Inject
    @Getter
    private PvpScript pvpScript;
    @Inject
    FletchingManager fletchingManager;
    @Inject
    FiremakingManager firemakingManager;
    @Inject
    GemCuttingManager gemCuttingManager;
    @Inject
    CraftingManager craftingManager;
    @Inject
    EventBus eventBus;

    @Inject
    BankpinScript bankpinScript;
    @Inject
    private PotionManagerScript potionManagerScript;
    @Inject
    private AutoPrayer autoPrayer;

    @Provides
    QoLConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(QoLConfig.class);
    }

    private static BufferedImage getImageFromConfigResource(String imgName) {
        try {
            Class<?> clazz = Class.forName("net.runelite.client.plugins.config.ConfigPanel");
            return ImageUtil.loadImageResource(clazz, imgName.concat(".png"));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static ImageIcon remapImage(BufferedImage image, Color color) {
        if (color != null) {
            BufferedImage img = new BufferedImage(image.getWidth(), image.getHeight(), 2);
            Graphics2D graphics = img.createGraphics();
            graphics.drawImage(image, 0, 0, null);
            graphics.setColor(color);
            graphics.setComposite(AlphaComposite.getInstance(10, 1));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.dispose();
            return new ImageIcon(img);
        } else {
            return null;
        }
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(qoLOverlay);
            overlayManager.add(wintertodtOverlay);
        }
        if (config.useSpecWeapon()) {
            Microbot.getSpecialAttackConfigs().setSpecialAttack(true);
            Microbot.getSpecialAttackConfigs().setSpecialAttackWeapon(config.specWeapon());
            Microbot.getSpecialAttackConfigs().setMinimumSpecEnergy(config.specWeapon().getEnergyRequired());
        }
		if (config.autoRun()) {
			Microbot.enableAutoRunOn = true;
		}
        if (config.autoStamina()) {
            Microbot.useStaminaPotsIfNeeded = true;
            Microbot.runEnergyThreshold = config.staminaThreshold() * 100;
        }
        autoRunScript.run(config);
        specialAttackScript.run(config);
        qoLScript.run(config);
        wintertodtScript.run(config);
        cannonScript.run(config);
        autoItemDropperScript.run(config);
        eventBus.register(fletchingManager);
        eventBus.register(firemakingManager);
        eventBus.register(gemCuttingManager);
        eventBus.register(craftingManager);
        bankpinScript.run(config);
        potionManagerScript.run(config);
        autoPrayer.run(config);
        // pvpScript.run(config);
        awaitExecutionUntil(() ->Microbot.getClientThread().invokeLater(this::updateUiElements), () -> !SplashScreen.isOpen(), 600);
    }

    @Override
    protected void shutDown() {
        qoLScript.shutdown();
        autoRunScript.shutdown();
        specialAttackScript.shutdown();
        cannonScript.shutdown();
        autoItemDropperScript.shutdown();
        overlayManager.remove(qoLOverlay);
        overlayManager.remove(wintertodtOverlay);
        eventBus.unregister(fletchingManager);
        eventBus.unregister(firemakingManager);
        eventBus.unregister(gemCuttingManager);
        eventBus.unregister(craftingManager);
        potionManagerScript.shutdown();
        autoPrayer.shutdown();
    }

    @Subscribe(
            priority = -999
    )
    public void onProfileChanged(ProfileChanged event) {
        log.info("Profile changed");
        log.info("Updating UI elements");
        // Wait for the splash screen to close before updating the UI elements
        awaitExecutionUntil(() ->Microbot.getClientThread().invokeLater(this::updateUiElements), () -> !SplashScreen.isOpen(), 1000);

    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        ChatMessageType chatMessageType = chatMessage.getType();

        if (!Microbot.isLoggedIn()) return;
        if (isInWintertodtRegion()
                && (chatMessageType == ChatMessageType.GAMEMESSAGE || chatMessageType == ChatMessageType.SPAM)) {
            wintertodtScript.onChatMessage(chatMessage);
        }


    }


    @Subscribe
    public void onGameTick(GameTick event) {
        if (!Microbot.isLoggedIn()) return;
        if (config.neverLogout()) {
            NeverLogoutScript.onGameTick(event);
        }
        if (config.useSpecWeapon()) {
            if (Microbot.getSpecialAttackConfigs().getSpecialAttackWeapon() != config.specWeapon()) {
                Microbot.getSpecialAttackConfigs().setSpecialAttack(true);
                Microbot.getSpecialAttackConfigs().setSpecialAttackWeapon(config.specWeapon());
                Microbot.getSpecialAttackConfigs().setMinimumSpecEnergy(config.specWeapon().getEnergyRequired());
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.UNKNOWN && lastGameState == GameState.UNKNOWN) {
            updateUiElements();
        }

        if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            resetMenuEntries();
        }

        if (event.getGameState() == GameState.LOGGED_IN) {
            if (config.fixCameraPitch())
                CameraScript.fixPitch();
            if (config.fixCameraZoom())
                CameraScript.fixZoom();
        }
        lastGameState = event.getGameState();
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if ((config.resumeFletchingKindling() || config.resumeFeedingBrazier())) {
            if (event.getActor() == Microbot.getClient().getLocalPlayer()) {
                if (config.wintertodtActions() != WintertodtActions.NONE) {
                    updateWintertodtInterupted(true);
                }

            }
        }
    }

    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) {
        MenuEntry menuEntry = event.getMenuEntry();
        if (recordActions) {
            if (Rs2Bank.isOpen() && config.useDoLastBank()) {
                handleMenuOptionClicked(menuEntry, bankMenuEntries, "Close");
            } else if ((Rs2Widget.isProductionWidgetOpen() || Rs2Widget.isGoldCraftingWidgetOpen() || Rs2Widget.isSilverCraftingWidgetOpen()) && config.useDoLastFurnace()) {
                handleMenuOptionClicked(menuEntry, furnaceMenuEntries, "Make", "Smelt", "Craft");
            } else if (Rs2Widget.isSmithingWidgetOpen() && config.useDoLastAnvil()) {
                handleMenuOptionClicked(menuEntry, anvilMenuEntries, "Smith");
            }
        }

        if ("Track".equals(event.getMenuOption())) {
            event.consume();
        }
        if(event.getMenuOption().contains("HA Profit")) {
            event.consume();
            Microbot.getClientThread().runOnSeperateThread(() -> {
                customHaProfitOnClicked(menuEntry);

                return null;
            });
        }

        if ((config.resumeFletchingKindling() || config.resumeFeedingBrazier()) && isInWintertodtRegion()) {
            if (event.getMenuOption().contains("Fletch") && event.getMenuTarget().isEmpty() && config.resumeFletchingKindling()) {
                WintertodtActions action = WintertodtActions.FLETCH;
                action.setMenuEntry(createCachedMenuEntry(menuEntry));
                updateLastWinthertodtAction(action);
                updateWintertodtInterupted(false);
                Microbot.log("Setting action to Fletch Kindle");
            }
            if (event.getMenuOption().contains("Feed") && config.resumeFeedingBrazier()) {
                WintertodtActions action = WintertodtActions.FEED;
                action.setMenuEntry(createCachedMenuEntry(menuEntry));
                updateLastWinthertodtAction(action);
                updateWintertodtInterupted(false);
            }
            if (event.getMenuOption().contains("Chop") || event.getMenuOption().contains("Walk")) {
                updateLastWinthertodtAction(WintertodtActions.NONE);
                updateWintertodtInterupted(false);
            }

        }
        if (config.smartWorkbench() && event.getMenuOption().contains("Smart Work-at") && event.getMenuEntry().getIdentifier() == ObjectID.WORKBENCH_43754) {
            if(Rs2Inventory.anyPouchEmpty() && Rs2Inventory.hasItem(ItemID.GUARDIAN_ESSENCE)) {
                event.consume();
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    Rs2Inventory.fillPouches();
                    Rs2GameObject.interact(ObjectID.WORKBENCH_43754);
                    return null;
                });

            }
        }
        if (config.smartGotrMine() && event.getMenuOption().contains("Smart Mine") && event.getMenuEntry().getIdentifier() == ObjectID.HUGE_GUARDIAN_REMAINS) {
            if(Rs2Inventory.anyPouchEmpty() && Rs2Inventory.hasItem(ItemID.GUARDIAN_ESSENCE)) {
                event.consume();
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    Rs2Inventory.fillPouches();
                    Rs2GameObject.interact(ObjectID.HUGE_GUARDIAN_REMAINS);
                    return null;
                });

            }
        }
        if (config.smartRunecraft() && event.getMenuOption().contains("Smart Craft-rune") && event.getMenuTarget().contains("Altar")) {
            if(Rs2Inventory.anyPouchFull()) {
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    Rs2Inventory.waitForInventoryChanges(50000);
                    Global.sleepUntil(() -> !Rs2Inventory.anyPouchFull(), ()-> {
                                Rs2Inventory.emptyPouches();
                                Rs2Inventory.waitForInventoryChanges(3000);
                                Rs2GameObject.interact("Altar");
                                Rs2Inventory.waitForInventoryChanges(3000);
                            }
                            ,10000, 200);
                    return null;
                });

            }
        }
    }

    private void handleMenuOptionClicked(MenuEntry menuEntry, List<NewMenuEntry> menuEntriesList, String... stopOptions) {
        NewMenuEntry cachedMenuEntry = createCachedMenuEntry(menuEntry);
        menuEntriesList.add(cachedMenuEntry);
        if (Arrays.stream(stopOptions).anyMatch(menuEntry.getOption()::contains)) {
            recordActions = false;
            Microbot.log("<col=5F1515>Stopped recording actions</col>");
        }
    }

    private NewMenuEntry createCachedMenuEntry(MenuEntry menuEntry) {
        NewMenuEntry cachedMenuEntry = new NewMenuEntry(
                menuEntry.getOption(),
                menuEntry.getTarget(),
                menuEntry.getIdentifier(),
                menuEntry.getType(),
                menuEntry.getParam0(),
                menuEntry.getParam1(),
                menuEntry.isForceLeftClick()
        );
        cachedMenuEntry.setItemId(menuEntry.getItemId());
        cachedMenuEntry.setWidget(menuEntry.getWidget());
        return cachedMenuEntry;
    }

    @Subscribe
    private void onNpcChanged(NpcChanged event) {
        if (isInWintertodtRegion()) {
            wintertodtScript.onNpcChanged(event);
        }
    }

    @Subscribe
    private void onNpcDespawned(NpcDespawned event) {
        if (isInWintertodtRegion()) {
            wintertodtScript.onNpcDespawned(event);
        }
    }

    @Subscribe
    private void onNpcSpawned(NpcSpawned event) {
        if (isInWintertodtRegion()) {
            wintertodtScript.onNpcSpawned(event);
        }
    }


    // TODO: Rework this method to reduce the size and make it more manageable
    @Subscribe
    private void onMenuEntryAdded(MenuEntryAdded event) {
        String option = event.getOption();
        String target = event.getTarget();
        MenuEntry menuEntry = event.getMenuEntry();
        boolean bankChestCheck = "Bank".equals(option) || ("Use".equals(option) && target.toLowerCase().contains("bank chest"));

        if(config.quickHighAlch() && menuEntry.getItemId() != -1 && !menuEntry.getTarget().isEmpty() && menuEntry.getParam1() == WidgetIndices.ResizableModernViewport.INVENTORY_CONTAINER && menuEntry.getType() != MenuAction.WIDGET_TARGET_ON_WIDGET) {
            Rs2ItemModel item = Rs2Inventory.getItemInSlot(menuEntry.getParam0());
            if(item != null){
                if(item.isHaProfitable()){
                    event.getMenuEntry().setOption("<col=FFA500>HA Profit</col>");
                }
            }


        }

        if (config.rightClickCameraTracking() && menuEntry.getNpc() != null && menuEntry.getNpc().getId() > 0) {
            addMenuEntry(event, "Track", target, this::customTrackOnClicked);
        }
        if(config.useDoLastCooking() && "Cook".equals(option) && (target.contains("Range") || target.contains("Fire"))) {
            addMenuEntry(event, "<col=FFA500>Do-Last</col>", target, this::customCookingOnClicked);
        }

        if (config.useDoLastBank()) {
            if ("Talk-to".equals(option)) {
                for (MenuEntry e : Microbot.getClient().getMenuEntries()) {
                    if ("Bank".equals(e.getOption()) && e.getTarget().equals(target)) {
                        menuEntry.setDeprioritized(true);
                        break;
                    }
                }
            }
            if (bankChestCheck && event.getItemId() == -1) {
                menuEntry.onClick(this::recordNewActions);
                addMenuEntry(event, "<col=FFA500>Do-Last</col>", target, this::customBankingOnClicked);
            }
        }

        if (config.useDoLastFurnace() && "Smelt".equals(option) && target.contains("Furnace")) {
            menuEntry.onClick(this::recordNewActions);
            addMenuEntry(event, "<col=FFA500>Do-Last</col>", target, this::customFurnaceOnClicked);
        }

        if (config.useDoLastAnvil() && "Smith".equals(option) && target.contains("Anvil")) {
            menuEntry.onClick(this::recordNewActions);
            addMenuEntry(event, "<col=FFA500>Do-Last</col>", target, this::customAnvilOnClicked);
        }

        if (config.useQuickTeleportToHouse() && menuEntry.getOption().contains("Open") && menuEntry.getTarget().toLowerCase().contains("rune pouch")) {
            if (Rs2Magic.isSpellbook(Rs2Spellbook.MODERN)) {
                addMenuEntry(event, "<col=FFA500>Teleport to House</col>", target, this::quickTeleportToHouse);
            }
        }

        if (config.smartWorkbench() && menuEntry.getOption().contains("Work-at") && menuEntry.getIdentifier() == ObjectID.WORKBENCH_43754) {
            menuEntry.setOption("<col=FFA500>Smart Work-at</col>");
        }
        if (config.smartGotrMine() && menuEntry.getOption().contains("Mine") && menuEntry.getIdentifier() == ObjectID.HUGE_GUARDIAN_REMAINS) {
            menuEntry.setOption("<col=FFA500>Smart Mine</col>");
        }
        if (config.smartRunecraft() && menuEntry.getOption().contains("Craft-rune") && menuEntry.getTarget().contains("Altar")) {
            menuEntry.setOption("<col=FFA500>Smart Craft-rune</col>");
        }

        if (config.displayInventorySetups() && bankChestCheck && event.getItemId() == -1) {
            addLoadoutMenuEntries(event, target);
        }
    }

    private void customHaProfitOnClicked(MenuEntry entry) {
        NewMenuEntry highAlch = new NewMenuEntry("Cast","High Alch",0,MenuAction.WIDGET_TARGET,-1,14286892,false);
        NewMenuEntry highAlchItem = new NewMenuEntry("Cast","High Alch",0,MenuAction.WIDGET_TARGET_ON_WIDGET,entry.getParam0(),WidgetIndices.ResizableModernViewport.INVENTORY_CONTAINER,false);
        highAlchItem.setItemId(entry.getItemId());
        Rs2Tab.switchToMagicTab();
        Microbot.getMouse().click(Microbot.getClient().getMouseCanvasPosition(), highAlch);
        Global.sleep(Rs2Random.randomGaussian(150,50));
        Microbot.getMouse().click(Microbot.getClient().getMouseCanvasPosition(), highAlchItem);
        Rs2Player.waitForXpDrop(Skill.MAGIC,1000);
        Rs2Tab.switchToInventoryTab();

    }

    private void addLoadoutMenuEntries(MenuEntryAdded event, String target) {
        if (config.displaySetup1() && config.Setup1() != null) {
            addLoadoutMenuEntry(event, "<col=FFA500>Equip: " + config.Setup1().getName() + "</col>", target, e -> customLoadoutOnClicked(e, config.Setup1()));
        }
        if (config.displaySetup2() && config.Setup2() != null) {
            addLoadoutMenuEntry(event, "<col=FFA500>Equip: " + config.Setup2().getName() + "</col>", target, e -> customLoadoutOnClicked(e, config.Setup2()));
        }
        if (config.displaySetup3() && config.Setup3() != null) {
            addLoadoutMenuEntry(event, "<col=FFA500>Equip: " + config.Setup3().getName() + "</col>", target, e -> customLoadoutOnClicked(e, config.Setup3()));
        }
        if (config.displaySetup4() && config.Setup4() != null) {
            addLoadoutMenuEntry(event, "<col=FFA500>Equip: " + config.Setup4().getName() + "</col>", target, e -> customLoadoutOnClicked(e, config.Setup4()));
        }
    }


    @Subscribe
    public void onConfigChanged(ConfigChanged ev) {
        if ("smoothRotation".equals(ev.getKey()) && config.smoothCameraTracking() && Microbot.isLoggedIn()) {
            previousCamera[YAW_INDEX] = Microbot.getClient().getMapAngle();
        }
        if (ev.getKey().equals("accentColor") || ev.getKey().equals("toggleButtonColor") || ev.getKey().equals("pluginLabelColor")) {
            updateUiElements();
        }
        if (ev.getKey().equals("resumeFletchingKindling")) {
            if (config.resumeFletchingKindling()) {
                configManager.setConfiguration("QoL", "quickFletchKindling", true);
            }
        }
        if (ev.getKey().equals("useSpecWeapon") || ev.getKey().equals("specWeapon")) {
            if (config.useSpecWeapon()) {
                Microbot.getSpecialAttackConfigs().setSpecialAttack(true);
                Microbot.getSpecialAttackConfigs().setSpecialAttackWeapon(config.specWeapon());
                Microbot.getSpecialAttackConfigs().setMinimumSpecEnergy(config.specWeapon().getEnergyRequired());
            } else {
                Microbot.getSpecialAttackConfigs().reset();
            }
        }

		if (ev.getKey().equals("autoRun")) {
			Microbot.enableAutoRunOn = config.autoRun();
		}

        if (ev.getKey().equals("autoStamina")) {
            Microbot.useStaminaPotsIfNeeded = config.autoStamina();
        }

        if (ev.getKey().equals("staminaThreshold")) {
            Microbot.runEnergyThreshold = config.staminaThreshold() * 100;
        }
    }

    @Subscribe
    public void onBeforeRender(BeforeRender render) {
        if (!Microbot.isLoggedIn() || !config.smoothCameraTracking()) {
            return;
        }
        applySmoothingToAngle(YAW_INDEX);
    }

    // TODO: These OnClick methods should be moved to a separate class to reduce the size and make this class more manageable

    private void customLoadoutOnClicked(MenuEntry event, InventorySetup loadout) {
        recordActions = false;
        loadoutToLoad = loadout;
        executeLoadoutActions = true;
    }

    private void customBankingOnClicked(MenuEntry event) {
        recordActions = false;
        if (bankMenuEntries.isEmpty()) {
            Microbot.log("<col=5F1515>No actions recorded</col>");
            return;
        }
        Microbot.log("<col=245C2D>Banking</col>");
        executeBankActions = true;
    }

    private void customTrackOnClicked(MenuEntry event) {
        if (Rs2Camera.isTrackingNpc()) {
            Rs2Camera.stopTrackingNpc();
            Microbot.log("<col=5F1515>Stopped tracking old NPC, try again to track new NPC</col>");
            return;
        }
        Rs2Camera.trackNpc(Objects.requireNonNull(event.getNpc()).getId());
    }

    private void customFurnaceOnClicked(MenuEntry event) {
        recordActions = false;
        if (furnaceMenuEntries.isEmpty()) {
            Microbot.log("<col=5F1515>No actions recorded</col>");
            return;
        }
        Microbot.log("<col=245C2D>Furnace</col>");
        executeFurnaceActions = true;
    }

    private void customAnvilOnClicked(MenuEntry event) {
        recordActions = false;
        if (anvilMenuEntries.isEmpty()) {
            Microbot.log("<col=5F1515>No actions recorded</col>");
            return;
        }
        Microbot.log("<col=245C2D>Anvil</col>");
        executeAnvilActions = true;
    }

    private void customWorkbenchOnClicked(MenuEntry event) {
        Microbot.log("<col=245C2D>Workbench</col>");

    }

    private void recordNewActions(MenuEntry event) {
        recordActions = true;
        String option = event.getOption();
        if (BANK_OPTION.equals(option) || "Use".equals(option) && event.getTarget().toLowerCase().contains("bank chest")){
            bankMenuEntries.clear();
        } else if (SMELT_OPTION.equals(option)) {
            furnaceMenuEntries.clear();
        } else if (SMITH_OPTION.equals(option)) {
            anvilMenuEntries.clear();
        }
        Microbot.log("<col=245C2D>Recording actions for: </col>" + option);
    }

    private void customCookingOnClicked(MenuEntry event) {
        Microbot.getClientThread().runOnSeperateThread(() -> {
            Global.sleepUntilTrue(Rs2Widget::isProductionWidgetOpen);
            if (Rs2Widget.isProductionWidgetOpen()) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            }
        Microbot.log("<col=245C2D>Cooking</col>");

            return null;
        });

    }

    private void quickTeleportToHouse(MenuEntry entry) {
        if (!Rs2Inventory.hasRunePouch()) return;

        if (!Rs2Magic.hasRequiredRunes(Rs2Spells.TELEPORT_TO_HOUSE)) {
            Microbot.log("<col=5F1515>Missing Required Runes</col>");
            return;
        }

        Microbot.log("<col=245C2D>Casting: </col>Teleport to House");
        Microbot.getClientThread().runOnSeperateThread(() -> {
            Rs2Magic.cast(MagicAction.TELEPORT_TO_HOUSE);
            return null;
        });
    }


    private void applySmoothingToAngle(int index) {
        int currentAngle = index == YAW_INDEX ? Microbot.getClient().getMapAngle() : 0;
        int newDeltaAngle = getSmallestAngle(previousCamera[index], currentAngle);
        deltaCamera[index] += newDeltaAngle;

        int deltaChange = lerp(deltaCamera[index], 0, 0.8f);
        int changed = previousCamera[index] + deltaChange;

        deltaCamera[index] -= deltaChange;
        if (index == YAW_INDEX) {
            Microbot.getClient().setCameraYawTarget(changed);
        }
        previousCamera[index] += deltaChange;
    }

    private int lerp(int x, int y, double alpha) {
        return x + (int) Math.round((y - x) * alpha);
    }

    private int getSmallestAngle(int x, int y) {
        return ((y - x + HALF_ROTATION) % FULL_ROTATION) - HALF_ROTATION;
    }

    private void addMenuEntry(MenuEntryAdded event, String option, String target, Consumer<MenuEntry> callback) {
        int index = Microbot.getClient().getMenuEntries().length;
        Microbot.getClient().createMenuEntry(index)
                .setOption(option)
                .setTarget(target)
                .setParam0(event.getActionParam0())
                .setParam1(event.getActionParam1())
                .setIdentifier(event.getIdentifier())
                .setType(event.getMenuEntry().getType())
                .onClick(callback);
    }

    private void addLoadoutMenuEntry(MenuEntryAdded event, String option, String target, Consumer<MenuEntry> callback) {
        int index = Microbot.getClient().getMenuEntries().length - 1;
        Microbot.getClient().createMenuEntry(index)
                .setOption(option)
                .setTarget(target)
                .setParam0(event.getActionParam0())
                .setParam1(event.getActionParam1())
                .setIdentifier(event.getIdentifier())
                .setType(event.getMenuEntry().getType())
                .onClick(callback);
    }

    public void updateLastWinthertodtAction(WintertodtActions action) {
        configManager.setConfiguration("QoL", "wintertodtActions", action);
    }

    public void updateWintertodtInterupted(boolean interupted) {
        configManager.setConfiguration("QoL", "interrupted", interupted);
    }


    /**
     * Updates the UI elements by modifying various fields and components based on the provided configuration.
     *
     * This method updates the accent color, the plugin toggle button's ON_SWITCHER field, and modifies the color of labels
     * and toggle buttons for plugins in the plugin list. If any part of the process fails (e.g., the ConfigPlugin is not found),
     * it logs an appropriate error message and returns false.
     *
     * @return true if the UI elements are successfully updated, false otherwise.
     */
    private boolean updateUiElements() {
        try {
            // Get the Field object for the accent color (BRAND_ORANGE) in the ColorScheme class
            Field accentColorField = ColorScheme.class.getDeclaredField("BRAND_ORANGE");
            // Update the accent color with the value from the config
            FieldUtil.setFinalStatic(accentColorField, config.accentColor());

            // Get the PluginToggleButton class to access its ON_SWITCHER field
            Class<?> pluginButton = Class.forName("net.runelite.client.plugins.microbot.ui.MicrobotPluginToggleButton");
            Field onSwitcherPluginPanel = pluginButton.getDeclaredField("ON_SWITCHER");
            onSwitcherPluginPanel.setAccessible(true);
            // Update the ON_SWITCHER field with a remapped image based on the config toggle button color
            FieldUtil.setFinalStatic(onSwitcherPluginPanel, remapImage(SWITCHER_ON_IMG, config.toggleButtonColor()));

            // Find the ConfigPlugin instance from the plugin manager
            MicrobotPlugin microbotPlugin = (MicrobotPlugin) Microbot.getPluginManager().getPlugins().stream()
                    .filter(plugin -> plugin instanceof MicrobotPlugin)
                    .findAny().orElse(null);

            // If ConfigPlugin is not found, log an error and return false
            if (microbotPlugin == null) {
                Microbot.log("Config Plugin not found");
                return false;
            }

            // Get the plugin list panel from the ConfigPlugin instance
            JPanel pluginListPanel = getPluginListPanel(microbotPlugin);
            // Set the plugin list using the retrieved plugin list panel
            pluginList.set(getPluginList(pluginListPanel));

            // If the plugin list is still null, log an error and return false
            if (pluginList.get() == null) {
                Microbot.log("Plugin list is null, waiting for it to be initialized");
                return false;
            }

            // Iterate through each plugin in the plugin list
            for (Object plugin : pluginList.get()) {
                // If the plugin is a JPanel, update the color of any JLabel components within it
                if (plugin instanceof JPanel) {
                    for (Component component : ((JPanel) plugin).getComponents()) {
                        if (component instanceof JLabel) {
                            // Set the label color based on the config
                            component.setForeground(config.pluginLabelColor());
                        }
                    }
                }

                // Get the on/off toggle button for the plugin and update its selected icon
                JToggleButton onOffToggle = (JToggleButton) FieldUtils.readDeclaredField(plugin, "onOffToggle", true);
                onOffToggle.setSelectedIcon(remapImage(SWITCHER_ON_IMG, config.toggleButtonColor()));
            }

            return true;
        } catch (Exception e) {
            // Log any exceptions that occur during the UI update process
            String errorMessage = "QoL Error updating UI elements: " + e.getMessage();
            log.error(errorMessage);
            Microbot.log(errorMessage);
            return false;
        }
    }


    private JPanel getPluginListPanel(MicrobotPlugin microbotPlugin) throws ClassNotFoundException {

        Class<?> pluginListPanelClass = Class.forName("net.runelite.client.plugins.microbot.ui.MicrobotPluginListPanel");
        assert microbotPlugin != null;
        return (JPanel) microbotPlugin.getInjector().getProvider(pluginListPanelClass).get();
    }

    private List<?> getPluginList(JPanel pluginListPanel) throws IllegalAccessException {
        return (List<?>) FieldUtils.readDeclaredField(pluginListPanel, "pluginList", true);
    }

    public static void resetMenuEntries() {
        bankMenuEntries.clear();
        furnaceMenuEntries.clear();
        anvilMenuEntries.clear();
        recordActions = false;
        executeBankActions = false;
        executeFurnaceActions = false;
        executeAnvilActions = false;
        executeWorkbenchActions = false;
        executeLoadoutActions = false;
    }

    @Subscribe
    public void onPlayerChanged(PlayerChanged event) {
        if (config.aggressiveAntiPkMode() && autoPrayer.isFollowingPlayer(event.getPlayer())) {
            autoPrayer.handleAggressivePrayerOnGearChange(event.getPlayer(), config);
        }
    }
}
