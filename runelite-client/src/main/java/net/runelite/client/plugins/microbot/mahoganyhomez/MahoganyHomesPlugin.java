package net.runelite.client.plugins.microbot.mahoganyhomez;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.inject.Provides;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = PluginDescriptor.See1Duck + " Mahogany Homes",
        description = "Automates Mahogany Homes contracts",
        enabledByDefault = false,
        tags = {"mahogany", "homes", "construction", "contract", "minigame", "s1d","see1duck","microbot"}
)
public class MahoganyHomesPlugin extends Plugin
{
    private static final List<Integer> PLANKS = Arrays.asList(ItemID.PLANK, ItemID.OAK_PLANK, ItemID.TEAK_PLANK, ItemID.MAHOGANY_PLANK);
    private static final List<String> PLANK_NAMES = Arrays.asList("Plank", "Oak plank", "Teak plank", "Mahogany plank");
    private static final Map<Integer, Integer> MAHOGANY_HOMES_REPAIRS = new HashMap<>();

    static {
        MAHOGANY_HOMES_REPAIRS.put(39981, 4); // Bob large table
        MAHOGANY_HOMES_REPAIRS.put(39985, 2); // Bob bookcase (1)
        MAHOGANY_HOMES_REPAIRS.put(39986, 2); // Bob bookcase (2)
        MAHOGANY_HOMES_REPAIRS.put(39983, 2); // Bob cabinet (1)
        MAHOGANY_HOMES_REPAIRS.put(39984, 2); // Bob cabinet (2)
        MAHOGANY_HOMES_REPAIRS.put(39982, 1); // Bob clock
        MAHOGANY_HOMES_REPAIRS.put(39987, 2); // Bob wardrobe
        MAHOGANY_HOMES_REPAIRS.put(39988, 2); // Bob drawers
        MAHOGANY_HOMES_REPAIRS.put(40007, 2); // Leela small table (1)
        MAHOGANY_HOMES_REPAIRS.put(40008, 2); // Leela small table (2)
        MAHOGANY_HOMES_REPAIRS.put(40009, 3); // Leela table
        MAHOGANY_HOMES_REPAIRS.put(40010, 1); // Leela mirror
        MAHOGANY_HOMES_REPAIRS.put(40291, 3); // Leela double Bed
        MAHOGANY_HOMES_REPAIRS.put(40292, 2); // Leela cupboard
        MAHOGANY_HOMES_REPAIRS.put(40084, 3); // Tau table (1)
        MAHOGANY_HOMES_REPAIRS.put(40085, 3); // Tau table (2)
        MAHOGANY_HOMES_REPAIRS.put(40086, 2); // Tau cupboard
        MAHOGANY_HOMES_REPAIRS.put(40087, 2); // Tau shelves (1)
        MAHOGANY_HOMES_REPAIRS.put(40088, 2); // Tau shelves (2)
        MAHOGANY_HOMES_REPAIRS.put(40295, 1); // Tau hat stand
        MAHOGANY_HOMES_REPAIRS.put(40095, 2); // Larry drawers (1)
        MAHOGANY_HOMES_REPAIRS.put(40096, 2); // Larry drawers (2)
        MAHOGANY_HOMES_REPAIRS.put(40099, 1); // Larry clock
        MAHOGANY_HOMES_REPAIRS.put(40298, 1); // Larry hat stand
        MAHOGANY_HOMES_REPAIRS.put(40097, 3); // Larry table (1)
        MAHOGANY_HOMES_REPAIRS.put(40098, 3); // Larry table (2)
        MAHOGANY_HOMES_REPAIRS.put(40002, 3); // Mariah table
        MAHOGANY_HOMES_REPAIRS.put(40003, 2); // Mariah shelves
        MAHOGANY_HOMES_REPAIRS.put(40004, 2); // Mariah bed
        MAHOGANY_HOMES_REPAIRS.put(40005, 2); // Mariah small table (1)
        MAHOGANY_HOMES_REPAIRS.put(40006, 2); // Mariah small table (2)
        MAHOGANY_HOMES_REPAIRS.put(40288, 2); // Mariah cupboard
        MAHOGANY_HOMES_REPAIRS.put(40289, 1); // Mariah hat Stand
        MAHOGANY_HOMES_REPAIRS.put(40165, 2); // Ross drawers (1)
        MAHOGANY_HOMES_REPAIRS.put(40166, 2); // Ross drawers (2)
        MAHOGANY_HOMES_REPAIRS.put(40167, 3); // Ross double bed
        MAHOGANY_HOMES_REPAIRS.put(40168, 1); // Ross hat stand
        MAHOGANY_HOMES_REPAIRS.put(40169, 2); // Ross bed
        MAHOGANY_HOMES_REPAIRS.put(40170, 1); // Ross mirror
        MAHOGANY_HOMES_REPAIRS.put(39989, 3); // Jeff table
        MAHOGANY_HOMES_REPAIRS.put(39990, 2); // Jeff bookcase
        MAHOGANY_HOMES_REPAIRS.put(39991, 2); // Jeff shelves
        MAHOGANY_HOMES_REPAIRS.put(39992, 3); // Jeff bed
        MAHOGANY_HOMES_REPAIRS.put(39993, 2); // Jeff drawers
        MAHOGANY_HOMES_REPAIRS.put(39994, 2); // Jeff dresser
        MAHOGANY_HOMES_REPAIRS.put(39995, 1); // Jeff mirror
        MAHOGANY_HOMES_REPAIRS.put(39996, 1); // Jeff chair
        MAHOGANY_HOMES_REPAIRS.put(40011, 1); // Barbara clock
        MAHOGANY_HOMES_REPAIRS.put(40012, 3); // Barbara table
        MAHOGANY_HOMES_REPAIRS.put(40013, 2); // Barbara bed
        MAHOGANY_HOMES_REPAIRS.put(40014, 1); // Barbara chair (1)
        MAHOGANY_HOMES_REPAIRS.put(40015, 1); // Barbara chair (2)
        MAHOGANY_HOMES_REPAIRS.put(40294, 2); // Barbara drawers
        MAHOGANY_HOMES_REPAIRS.put(40156, 2); // Noella dresser
        MAHOGANY_HOMES_REPAIRS.put(40157, 2); // Noella cupboard
        MAHOGANY_HOMES_REPAIRS.put(40158, 1); // Noella hat Stand
        MAHOGANY_HOMES_REPAIRS.put(40159, 1); // Noella mirror
        MAHOGANY_HOMES_REPAIRS.put(40160, 2); // Noella drawers
        MAHOGANY_HOMES_REPAIRS.put(40161, 3); // Noella table (1)
        MAHOGANY_HOMES_REPAIRS.put(40162, 3); // Noella table (2)
        MAHOGANY_HOMES_REPAIRS.put(40163, 1); // Noella clock
        MAHOGANY_HOMES_REPAIRS.put(40089, 1); // Norman clock
        MAHOGANY_HOMES_REPAIRS.put(40090, 3); // Norman table
        MAHOGANY_HOMES_REPAIRS.put(40091, 3); // Norman double bed
        MAHOGANY_HOMES_REPAIRS.put(40092, 2); // Norman bookshelf
        MAHOGANY_HOMES_REPAIRS.put(40093, 2); // Norman drawers
        MAHOGANY_HOMES_REPAIRS.put(40094, 2); // Norman small table
        MAHOGANY_HOMES_REPAIRS.put(39997, 3); // Sarah table
        MAHOGANY_HOMES_REPAIRS.put(39998, 2); // Sarah bed
        MAHOGANY_HOMES_REPAIRS.put(39999, 2); // Sarah dresser
        MAHOGANY_HOMES_REPAIRS.put(40000, 2); // Sarah small table
        MAHOGANY_HOMES_REPAIRS.put(40001, 2); // Sarah shelves
        MAHOGANY_HOMES_REPAIRS.put(40171, 2); // Jess drawers (1)
        MAHOGANY_HOMES_REPAIRS.put(40172, 2); // Jess drawers (2)
        MAHOGANY_HOMES_REPAIRS.put(40173, 2); // Jess cabinet (1)
        MAHOGANY_HOMES_REPAIRS.put(40174, 2); // Jess cabinet (2)
        MAHOGANY_HOMES_REPAIRS.put(40175, 3); // Jess bed
        MAHOGANY_HOMES_REPAIRS.put(40176, 3); // Jess table
        MAHOGANY_HOMES_REPAIRS.put(40177, 1); // Jess clock
    }

    private static final Set<Integer> HALLOWED_SEPULCHRE_FIXES = Sets.newHashSet(39527, 39528);
    private static final int CONSTRUCTION_WIDGET_GROUP = 458;
    private static final int CONSTRUCTION_WIDGET_BUILD_IDX_START = 4;
    private static final int CONSTRUCTION_SUBWIDGET_MATERIALS = 3;
    private static final int CONSTRUCTION_SUBWIDGET_CANT_BUILD = 5;
    private static final int SCRIPT_CONSTRUCTION_OPTION_CLICKED = 1405;
    private static final int SCRIPT_CONSTRUCTION_OPTION_KEYBIND = 1632;
    private static final int SCRIPT_BUILD_CONSTRUCTION_MENU_ENTRY = 1404;

    @Data
    private static class BuildMenuItem
    {
        private final Item[] planks;
        private final boolean canBuild;
    }


    @VisibleForTesting
    static final Pattern CONTRACT_PATTERN = Pattern.compile("(Please could you g|G)o see (\\w*)[ ,][\\w\\s,-]*[?.] You can get another job once you have furnished \\w* home\\.");
    @VisibleForTesting
    static final Pattern REMINDER_PATTERN = Pattern.compile("You're currently on an (\\w*) Contract\\. Go see (\\w*)[ ,][\\w\\s,-]*\\. You can get another job once you have furnished \\w* home\\.");
    private static final Pattern CONTRACT_FINISHED = Pattern.compile("You have completed [\\d,]* contracts with a total of [\\d,]* points?\\.");
    private static final Pattern CONTRACT_ASSIGNED = Pattern.compile("(\\w*) Contract: Go see [\\w\\s,-]*\\.");
    private static final Pattern REQUEST_CONTACT_TIER = Pattern.compile("Could I have an? (\\w*) contract please\\?");

    @Getter
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    @Getter
    @Inject
    private MahoganyHomesConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MahoganyHomesOverlay textOverlay;

    @Inject
    private PlankSackOverlay plankSackOverlay;

    @Inject
    private MahoganyHomesHighlightOverlay highlightOverlay;

    @Inject
    private MahoganyHomesScript script;


    @Inject
    private WorldMapPointManager worldMapPointManager;

    @Provides
    MahoganyHomesConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(MahoganyHomesConfig.class);
    }

    @Getter
    public int plankCount = -1;
    private int buildCost = 0;


    private Multiset<Integer> inventorySnapshot;
    private boolean checkForUpdate = false;

    private int menuItemsToCheck = 0;
    private final List<BuildMenuItem> buildMenuItems = new ArrayList<>();

    private boolean watchForAnimations = false;
    private int lastAnimation = -1;

    @Getter
    private final List<GameObject> objectsToMark = new ArrayList<>();
    // Varb values: 0=default, 1=Needs repair, 2=Repaired, 3=Remove 4=Bulld 5-8=Built Tiers
    private final HashMap<Integer, Integer> varbMap = new HashMap<>();

    private BufferedImage mapIcon;
    private BufferedImage mapArrow;

    @Getter
    @Setter
    private Home currentHome;
    private boolean varbChange;
    private boolean wasTimedOut;
    @Getter
    private int contractTier = 0;


    // Used to auto disable plugin if nothing has changed recently.
    private Instant lastChanged;
    private int lastCompletedCount = -1;

    @Getter
    private int sessionContracts = 0;
    @Getter
    private int sessionPoints = 0;


    @Override
    public void startUp()
    {
        overlayManager.add(textOverlay);
        overlayManager.add(highlightOverlay);
        overlayManager.add(plankSackOverlay);
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            loadFromConfig();
            clientThread.invoke(this::updateVarbMap);
        }
        script.run(config);
    }

    @Override
    public void shutDown()
    {
        overlayManager.remove(textOverlay);
        overlayManager.remove(highlightOverlay);
        overlayManager.remove(plankSackOverlay);
        worldMapPointManager.removeIf(MahoganyHomesWorldPoint.class::isInstance);
        client.clearHintArrow();
        varbMap.clear();
        objectsToMark.clear();
        currentHome = null;
        mapIcon = null;
        mapArrow = null;
        lastChanged = null;
        lastCompletedCount = -1;
        contractTier = 0;
        script.shutdown();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged c)
    {
        if (!c.getGroup().equals(MahoganyHomesConfig.GROUP_NAME))
        {
            return;
        }

        switch (c.getKey()) {
            case MahoganyHomesConfig.WORLD_MAP_KEY:
                worldMapPointManager.removeIf(MahoganyHomesWorldPoint.class::isInstance);
                if (config.worldMapIcon() && currentHome != null) {
                    worldMapPointManager.add(new MahoganyHomesWorldPoint(currentHome.getLocation(), this));
                }
                break;
            case MahoganyHomesConfig.HINT_ARROW_KEY:
                client.clearHintArrow();
                if (client.getLocalPlayer() != null) {
                    refreshHintArrow(client.getLocalPlayer().getWorldLocation());
                }
                break;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        // Defer to game tick for better performance
        varbChange = true;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e)
    {
        if (e.getGameState() == GameState.LOADING)
        {
            objectsToMark.clear();
        }
    }

    @Subscribe
    public void onUsernameChanged(UsernameChanged e)
    {
        loadFromConfig();
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        processGameObjects(event.getGameObject(), null);
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        processGameObjects(null, event.getGameObject());
    }

    @Subscribe
    public void onOverlayMenuClicked(OverlayMenuClicked e)
    {
        if (!e.getOverlay().equals(textOverlay))
        {
            return;
        }

        if (e.getEntry().getOption().equals(MahoganyHomesOverlay.CLEAR_OPTION))
        {
            setCurrentHome(null);
            updateConfig();
            lastChanged = null;
        }


        if (e.getEntry().getOption().equals(MahoganyHomesOverlay.RESET_SESSION_OPTION))
        {
            sessionContracts = 0;
            sessionPoints = 0;
        }
    }

    private void plankSackCheck()
    {
        if (menuItemsToCheck <= 0)
        {
            return;
        }

        for (int i = 0; i < menuItemsToCheck; i++)
        {
            int idx = CONSTRUCTION_WIDGET_BUILD_IDX_START + i;
            Widget widget = client.getWidget(CONSTRUCTION_WIDGET_GROUP, idx);
            if (widget == null)
            {
                continue;
            }

            boolean canBuild = widget.getDynamicChildren()[CONSTRUCTION_SUBWIDGET_CANT_BUILD].isHidden();
            Widget materialWidget = widget.getDynamicChildren()[CONSTRUCTION_SUBWIDGET_MATERIALS];
            if (materialWidget == null)
            {
                continue;
            }

            String[] materialLines = materialWidget.getText().split("<br>");
            List<Item> materials = new ArrayList<>();
            for (String line : materialLines)
            {
                String[] data = line.split(": ");
                if (data.length < 2)
                {
                    continue;
                }

                String name = data[0];
                int count = Integer.parseInt(data[1]);
                if (PLANK_NAMES.contains(name))
                {
                    materials.add(new Item(PLANKS.get(PLANK_NAMES.indexOf(name)), count));
                }
            }
            buildMenuItems.add(new BuildMenuItem(materials.toArray(new Item[0]), canBuild));
        }
        menuItemsToCheck = 0;
    }

    @Subscribe
    public void onGameTick(GameTick t)
    {

        plankSackCheck();
        if (contractTier == 0 || currentHome == null)
        {
            checkForContractTierDialog();
        }

        checkForAssignmentDialog();

        if (currentHome == null)
        {
            return;
        }

        if (varbChange)
        {
            varbChange = false;
            updateVarbMap();

            // If we couldn't find their contract tier recalculate it when they get close
            if (contractTier == 0)
            {
                calculateContractTier();
            }

            final int completed = getCompletedCount();
            if (completed != lastCompletedCount)
            {

                    // Refreshes hint arrow and world map icon if necessary
                    setCurrentHome(currentHome);
                    updateVarbMap();


                lastCompletedCount = completed;
                lastChanged = Instant.now();
            }
        }


        refreshHintArrow(Rs2Player.getWorldLocation());
    }

    @Subscribe
    public void onChatMessage(ChatMessage e)
    {
        if (!e.getType().equals(ChatMessageType.GAMEMESSAGE))
        {
            return;
        }

        final String message = e.getMessage();
        if (message.startsWith("Basic\u00A0planks:"))
        {
            String stripped = Text.removeTags(e.getMessage());
            setPlankCount(Arrays.stream(stripped.split(",")).mapToInt(s -> Integer.parseInt(s.split(":\u00A0")[1])).sum());
        }
        else if (message.equals("You haven't got any planks that can go in the sack."))
        {
            checkForUpdate = false;
        }
        else if (message.equals("Your sack is full."))
        {
            setPlankCount(28);
            checkForUpdate = false;
        }
        else if (message.equals("Your sack is empty."))
        {
            setPlankCount(0);
            checkForUpdate = false;
        }

        final Matcher matcher = CONTRACT_ASSIGNED.matcher(Text.removeTags(e.getMessage()));
        if (matcher.matches())
        {
            final String type = matcher.group(1).toLowerCase();
            setContactTierFromString(type);
        }

        if (CONTRACT_FINISHED.matcher(Text.removeTags(e.getMessage())).matches())
        {
            sessionContracts++;
            sessionPoints += getPointsForCompletingTask();
            setCurrentHome(null);
            updateConfig();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() != InventoryID.INVENTORY.getId())
        {
            return;
        }

        if (checkForUpdate)
        {
            checkForUpdate = false;
            Multiset<Integer> currentInventory = createSnapshot(event.getItemContainer());
            Multiset<Integer> deltaMinus = Multisets.difference(currentInventory, inventorySnapshot);
            Multiset<Integer> deltaPlus = Multisets.difference(inventorySnapshot, currentInventory);
            deltaPlus.forEachEntry((id, c) -> plankCount += c);
            deltaMinus.forEachEntry((id, c) -> plankCount -= c);
            setPlankCount(plankCount);
        }

    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event.getWidget() != null)
        {
            // Interact in inventory
            // Right click use in bank
            if (event.getWidget().getItemId() == ItemID.PLANK_SACK &&
                    (event.getMenuOption().equals("Fill") || event.getMenuOption().equals("Empty") || event.getMenuOption().equals("Use")))
            {
                inventorySnapshot = createSnapshot(client.getItemContainer(InventoryID.INVENTORY));
                checkForUpdate = true;
            }
            // Use plank on sack or sack on plank
            else if (event.getMenuOption().equals("Use")
                    && event.getMenuAction() == MenuAction.WIDGET_TARGET_ON_WIDGET
                    && client.getSelectedWidget() != null)
            {
                int firstSelectedItemID = client.getSelectedWidget().getItemId();
                int secondSelectedItemID = event.getWidget().getItemId();

                if ((firstSelectedItemID == ItemID.PLANK_SACK && PLANKS.contains(secondSelectedItemID))
                        || (PLANKS.contains(firstSelectedItemID) && secondSelectedItemID == ItemID.PLANK_SACK))
                {
                    inventorySnapshot = createSnapshot(client.getItemContainer(InventoryID.INVENTORY));
                    checkForUpdate = true;
                }
            }
        }
        else if ((event.getMenuOption().equals("Repair") || event.getMenuOption().equals("Build")) &&
                MAHOGANY_HOMES_REPAIRS.containsKey(event.getId()) && !watchForAnimations)
        {
            watchForAnimations = true;
            buildCost = MAHOGANY_HOMES_REPAIRS.get(event.getId());
            inventorySnapshot = createSnapshot(client.getItemContainer(InventoryID.INVENTORY));
        }
        else if (event.getMenuOption().equals("Fix") && HALLOWED_SEPULCHRE_FIXES.contains(event.getId()))
        {
            inventorySnapshot = createSnapshot(client.getItemContainer(InventoryID.INVENTORY));
        }
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
        // Construction menu option selected
        // Construction menu option selected with keybind
        if (event.getScriptId() != SCRIPT_CONSTRUCTION_OPTION_CLICKED
                && event.getScriptId() != SCRIPT_CONSTRUCTION_OPTION_KEYBIND)
        {
            return;
        }

        Widget widget = event.getScriptEvent().getSource();
        int idx = TO_CHILD(widget.getId()) - CONSTRUCTION_WIDGET_BUILD_IDX_START;
        if (idx >= buildMenuItems.size())
        {
            return;
        }
        BuildMenuItem item = buildMenuItems.get(idx);
        if (item != null && item.canBuild)
        {
            Multiset<Integer> snapshot = createSnapshot(client.getItemContainer(InventoryID.INVENTORY));
            if (snapshot != null)
            {
                for (Item i : item.planks)
                {
                    if (!snapshot.contains(i.getId()))
                    {
                        plankCount -= i.getQuantity();
                    }
                    else if (snapshot.count(i.getId()) < i.getQuantity())
                    {
                        plankCount -= i.getQuantity() - snapshot.count(i.getId());
                    }
                }
                setPlankCount(plankCount);
            }
        }

        buildMenuItems.clear();
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() != SCRIPT_BUILD_CONSTRUCTION_MENU_ENTRY)
        {
            return;
        }
        // Construction menu add object
        menuItemsToCheck += 1;
        // Cancel repair-based animation checking
        watchForAnimations = false;
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        if (!watchForAnimations || event.getActor() != client.getLocalPlayer() || client.getLocalPlayer() == null)
        {
            return;
        }

        int anim = client.getLocalPlayer().getAnimation();
        if ((lastAnimation == AnimationID.CONSTRUCTION || lastAnimation == AnimationID.CONSTRUCTION_IMCANDO)
                && anim != lastAnimation)
        {
            Multiset<Integer> current = createSnapshot(client.getItemContainer(InventoryID.INVENTORY));
            Multiset<Integer> delta = Multisets.difference(inventorySnapshot, current);

            int planksUsedFromInventory = delta.size();
            int planksUsedFromSack = buildCost - planksUsedFromInventory;

            if(planksUsedFromSack > 0)
            {
                setPlankCount(plankCount - planksUsedFromSack);
            }

            watchForAnimations = false;
            lastAnimation = -1;
            buildCost = 0;
        }
        else
        {
            lastAnimation = anim;
        }
    }

    private void checkForContractTierDialog()
    {
        final Widget dialog = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
        if (dialog == null)
        {
            return;
        }

        final String text = Text.sanitizeMultilineText(dialog.getText());
        final Matcher matcher = REQUEST_CONTACT_TIER.matcher(text);
        if (matcher.matches())
        {
            final String type = matcher.group(1).toLowerCase();
            setContactTierFromString(type);
        }
    }

    private void setContactTierFromString (String tier)
    {
        switch (tier)
        {
            case "beginner":
                contractTier = 1;
                break;
            case "novice":
                contractTier = 2;
                break;
            case "adept":
                contractTier = 3;
                break;
            case "expert":
                contractTier = 4;
                break;
        }
    }

    // Check for NPC dialog assigning or reminding us of a contract
    private void checkForAssignmentDialog()
    {
        final Widget dialog = client.getWidget(ComponentID.DIALOG_NPC_TEXT);
        if (dialog == null)
        {
            return;
        }

        final String npcText = Text.sanitizeMultilineText(dialog.getText());
        final Matcher startContractMatcher = CONTRACT_PATTERN.matcher(npcText);
        final Matcher reminderContract = REMINDER_PATTERN.matcher(npcText);
        String name = null;
        int tier = -1;
        if (startContractMatcher.matches())
        {
            name = startContractMatcher.group(2);
        }
        else if (reminderContract.matches())
        {
            name = reminderContract.group(2);
            tier = getTierByText(reminderContract.group(1));
        }

        if (name != null)
        {
            // They may have asked for a contract but already had one, check the configs
            if (contractTier == 0)
            {
                loadFromConfig();
                // If the config matches the assigned value then do nothing
                if (currentHome != null && currentHome.getName().equalsIgnoreCase(name))
                {
                    return;
                }
            }

            // If we could parse the tier from the message (only for reminders) make sure the current tier matches it
            // update the tier and config with the parsed value
            if (tier != -1)
            {
                contractTier = tier;
            }

            for (final Home h : Home.values())
            {
                if (h.getName().equalsIgnoreCase(name) && (currentHome != h || isPluginTimedOut()))
                {
                    setCurrentHome(h);
                    updateConfig();
                    break;
                }
            }
        }
    }

    public void setCurrentHome(final Home h)
    {
        currentHome = h;
        client.clearHintArrow();
        lastChanged = Instant.now();
        lastCompletedCount = 0;
        varbMap.clear();

        if (currentHome == null)
        {
            worldMapPointManager.removeIf(MahoganyHomesWorldPoint.class::isInstance);
            contractTier = 0;
            return;
        }

        if (config.worldMapIcon())
        {
            worldMapPointManager.removeIf(MahoganyHomesWorldPoint.class::isInstance);
            worldMapPointManager.add(new MahoganyHomesWorldPoint(h.getLocation(), this));
        }

        if (config.displayHintArrows() && client.getLocalPlayer() != null)
        {
            refreshHintArrow(client.getLocalPlayer().getWorldLocation());
        }

    }



    private void processGameObjects(final GameObject cur, final GameObject prev)
    {
        objectsToMark.remove(prev);

        if (cur == null || (!Hotspot.isHotspotObject(cur.getId()) && !Home.isLadder(cur.getId())))
        {
            return;
        }

        // Filter objects inside highlight overlay
        objectsToMark.add(cur);
    }

    private void updateVarbMap()
    {
        varbMap.clear();

        for (final Hotspot spot : Hotspot.values())
        {
            varbMap.put(spot.getVarb(), client.getVarbitValue(spot.getVarb()));
        }
    }

    private void loadFromConfig()
    {
        final String group = MahoganyHomesConfig.GROUP_NAME + "." + client.getAccountHash();
        final String name = configManager.getConfiguration(group, MahoganyHomesConfig.HOME_KEY);
        if (name == null)
        {
            return;
        }

        try
        {
            final Home h = Home.valueOf(name.trim().toUpperCase());
            setCurrentHome(h);
        }
        catch (IllegalArgumentException e)
        {
            log.warn("Stored unrecognized home: {}", name);
            currentHome = null;
            configManager.setConfiguration(group, MahoganyHomesConfig.HOME_KEY, null);
        }

        // Get contract tier from config if home was loaded successfully
        if (currentHome == null)
        {
            return;
        }

        final String tier = configManager.getConfiguration(group, MahoganyHomesConfig.TIER_KEY);
        if (tier == null)
        {
            return;
        }

        try
        {
            contractTier = Integer.parseInt(tier);
        }
        catch (IllegalArgumentException e)
        {
            log.warn("Stored unrecognized contract tier: {}", tier);
            contractTier = 0;
            configManager.unsetConfiguration(group, MahoganyHomesConfig.TIER_KEY);
        }
    }

    private void updateConfig()
    {
        final String group = MahoganyHomesConfig.GROUP_NAME + "." + client.getAccountHash();
        if (currentHome == null)
        {
            configManager.unsetConfiguration(group, MahoganyHomesConfig.HOME_KEY);
            configManager.unsetConfiguration(group, MahoganyHomesConfig.TIER_KEY);
        }
        else
        {
            configManager.setConfiguration(group, MahoganyHomesConfig.HOME_KEY, currentHome.getName());
            configManager.setConfiguration(group, MahoganyHomesConfig.TIER_KEY, contractTier);
        }
    }

    private void refreshHintArrow(final WorldPoint playerPos)
    {
        client.clearHintArrow();
        if (currentHome == null || !config.displayHintArrows())
        {
            return;
        }

        if (distanceBetween(currentHome.getArea(), playerPos) > 0)
        {
            client.setHintArrow(currentHome.getLocation());
        }
        else
        {
            // We are really close to house, only display a hint arrow if we are done.
            if (getCompletedCount() != 0)
            {
                return;
            }

            final Optional<NPC> npc = client.getNpcs().stream().filter(n -> n.getId() == currentHome.getNpcId()).findFirst();
            if (npc.isPresent())
            {
                client.setHintArrow(npc.get());
                return;
            }

            // Couldn't find the NPC, find the closest ladder to player
            WorldPoint location = null;
            int distance = Integer.MAX_VALUE;
            for (final GameObject obj : objectsToMark)
            {
                if (Home.isLadder(obj.getId()))
                {
                    // Ensure ladder isn't in a nearby home.
                    if (distanceBetween(currentHome.getArea(), obj.getWorldLocation()) > 0)
                    {
                        continue;
                    }

                    int diff = obj.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation());
                    if (diff < distance)
                    {
                        distance = diff;
                        location = obj.getWorldLocation();
                    }
                }
            }

            if (location != null)
            {
                client.setHintArrow(location);
            }
        }
    }

    int getCompletedCount()
    {
        if (currentHome == null)
        {
            return -1;
        }

        int count = 0;
        for (final Hotspot hotspot : Hotspot.values())
        {
            final boolean requiresAttention = doesHotspotRequireAttention(hotspot.getVarb());
            if (!requiresAttention)
            {
                continue;
            }

            count++;
        }

        return count;
    }

    boolean doesHotspotRequireAttention(final int varb)
    {
        final Integer val = varbMap.get(varb);
        if (val == null)
        {
            return false;
        }

        return val == 1 || val == 3 || val == 4;
    }

    // This check assumes objects are on the same plane as the WorldArea (ignores plane differences)
    int distanceBetween(final WorldArea area, final WorldPoint point)
    {
        return area.distanceTo(new WorldPoint(point.getX(), point.getY(), area.getPlane()));
    }

    BufferedImage getMapIcon()
    {
        if (mapIcon != null)
        {
            return mapIcon;
        }

        mapIcon = ImageUtil.getResourceStreamFromClass(getClass(), "map-icon.png");
        return mapIcon;
    }

    BufferedImage getMapArrow()
    {
        if (mapArrow != null)
        {
            return mapArrow;
        }

        mapArrow = ImageUtil.getResourceStreamFromClass(getClass(), "map-arrow-icon.png");
        return mapArrow;
    }

    boolean isPluginTimedOut()
    {
        return false;
    }

    int getPointsForCompletingTask()
    {
        // Contracts reward 2-5 points depending on tier
        return getContractTier() + 1;
    }

    private void calculateContractTier()
    {
        int tier = 0;
        // Values 5-8 are the tier of contract completed
        for (int val : varbMap.values())
        {
            tier = Math.max(tier, val);
        }

        // Normalizes tier from 5-8 to 1-4
        tier -= 4;
        contractTier = Math.max(tier, 0);
    }

    public Set<Integer> getRepairableVarbs()
    {
        return varbMap.keySet()
                .stream()
                .filter(this::doesHotspotRequireAttention)
                .collect(Collectors.toSet());
    }



    private int getTierByText(final String tierText)
    {
        switch (tierText)
        {
            case "Beginner":
                return 1;
            case "Novice":
                return 2;
            case "Adept":
                return 3;
            case "Expert":
                return 4;
            default:
                return -1;
        }
    }

    private void setPlankCount(int count)
    {
        plankCount = Ints.constrainToRange(count, 0, 28);

    }

    private Multiset<Integer> createSnapshot(ItemContainer container)
    {
        if (container == null)
        {
            return null;
        }
        Multiset<Integer> snapshot = HashMultiset.create();
        Arrays.stream(container.getItems())
                .filter(item -> PLANKS.contains(item.getId()))
                .forEach(i -> snapshot.add(i.getId(), i.getQuantity()));
        return snapshot;
    }

    private void updateInfobox(ItemContainer container)
    {

    }

    Color getColour()
    {
        if (plankCount <= 0)
        {
            return Color.RED;
        }
        else if (plankCount < 14)
        {
            return Color.YELLOW;
        }
        else
        {
            return Color.WHITE;
        }
    }

    private static int TO_CHILD(int id)
    {
        return id & 0xFFFF;
    }
}
