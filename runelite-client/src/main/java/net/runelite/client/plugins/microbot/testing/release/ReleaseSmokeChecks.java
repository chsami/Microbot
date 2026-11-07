package net.runelite.client.plugins.microbot.testing.release;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin;
import net.runelite.client.plugins.microbot.agentserver.AgentServerConfig;
import net.runelite.client.plugins.microbot.agentserver.AgentServerPlugin;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.statemachineexample.StateMachineExamplePlugin;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.game.WorldService;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.testing.release.ReleaseSmokeLive.distance;
import static net.runelite.client.plugins.microbot.testing.release.ReleaseSmokeLive.format;
import static net.runelite.client.plugins.microbot.testing.release.ReleaseSmokeLive.location;
import static net.runelite.client.plugins.microbot.testing.release.ReleaseSmokeLive.requireLoggedIn;
import static net.runelite.client.plugins.microbot.testing.release.ReleaseSmokeLive.waitFor;
import static net.runelite.client.plugins.microbot.testing.release.ReleaseSmokeLive.walkOrFail;
import static net.runelite.client.plugins.microbot.testing.release.ReleaseSmokeLive.walkToStart;

public final class ReleaseSmokeChecks {

    static final WorldPoint LUMBRIDGE_HALL = new WorldPoint(3215, 3218, 0);
    static final WorldPoint LUMBRIDGE_COURTYARD = new WorldPoint(3222, 3218, 0);
    static final WorldPoint LUMBRIDGE_BANK = new WorldPoint(3208, 3220, 2);
    static final WorldPoint VARROCK_WEST_BANK = new WorldPoint(3185, 3436, 0);
    static final WorldPoint GRAND_EXCHANGE = new WorldPoint(3165, 3484, 0);
    static final List<WorldPoint> DOOR_CANDIDATES = List.of(
            new WorldPoint(3226, 3214, 0),
            new WorldPoint(3226, 3223, 0),
            new WorldPoint(3207, 3217, 0));
    static final List<String> JUNK_ITEMS = List.of(
            "Burnt meat", "Burnt fish", "Burnt bread", "Cowhide", "Egg", "Bones", "Ashes", "Bowl", "Pot", "Jug", "Feather");
    static final String DEFAULT_HUB_PLUGIN = "AttackRangesPlusPlugin";
    static final String DIALOGUE_OPTION = "What is this place";
    static final String SKILLS_TEXT = "Total level";
    static final int F2P_BANK_SLOTS = 400;
    static final int SCRIPT_POOL_CORE_SIZE = 10;

    public static final class Settings {
        public String expectedVersion = "";
        public long loginTimeoutMs = 240_000L;
        public String hubPlugin = DEFAULT_HUB_PLUGIN;
        public int hopWorld = -1;
    }

    private final Settings settings;
    private final PluginManager pluginManager;
    private final ConfigManager configManager;
    private final MicrobotPluginManager microbotPluginManager;
    private final WorldService worldService;

    public ReleaseSmokeChecks(Settings settings, PluginManager pluginManager, ConfigManager configManager,
                              MicrobotPluginManager microbotPluginManager, WorldService worldService) {
        this.settings = settings;
        this.pluginManager = pluginManager;
        this.configManager = configManager;
        this.microbotPluginManager = microbotPluginManager;
        this.worldService = worldService;
    }

    public List<ReleaseCheck> all() {
        List<ReleaseCheck> checks = new ArrayList<>();
        checks.add(ReleaseCheck.quick("boot", "Client version, core plugins, clean startup log", 30_000L, this::boot));
        checks.add(ReleaseCheck.quick("login", "Logged in with a known player location", 30_000L, this::login));
        checks.add(ReleaseCheck.quick("state-caches", "Skills, items, varbits/varps and entity queryables at Lumbridge castle", 360_000L, this::stateAndCaches));
        checks.add(ReleaseCheck.quick("widgets", "Tab switching by widget click and widget text search", 60_000L, this::widgets));
        checks.add(ReleaseCheck.quick("walker-transport", "Short walk, closed door and Lumbridge castle stairs", 300_000L, this::walkerTransport));
        checks.add(ReleaseCheck.quick("bank", "Open nearest bank, deposit and withdraw one junk item", 180_000L, this::bank));
        checks.add(ReleaseCheck.quick("npc-dialogue", "Talk to a banker, read and pick a dialogue option, exit", 120_000L, this::npcDialogue));
        checks.add(ReleaseCheck.quick("object-interaction", "Open and close a door, restoring its state", 180_000L, this::objectInteraction));
        checks.add(ReleaseCheck.quick("agent-server", "Agent Server state, inventory, widgets and skills endpoints", 60_000L, this::agentServer));
        checks.add(ReleaseCheck.full("walker-medium", "Lumbridge to Varrock west bank", 420_000L, this::walkerMedium));
        checks.add(ReleaseCheck.full("grand-exchange", "Open and close the Grand Exchange without offers", 300_000L, this::grandExchange));
        checks.add(ReleaseCheck.full("world-hop", "Hop to another F2P world and back", 180_000L, this::worldHop));
        checks.add(ReleaseCheck.full("hub-plugins", "Plugin Hub manifest, install/start/stop/uninstall a harmless plugin", 180_000L, this::hubPlugins));
        checks.add(ReleaseCheck.full("script-lifecycle", "Start and stop a built-in script twice without leaked threads", 90_000L, this::scriptLifecycle));
        checks.add(ReleaseCheck.quick("logout", "Clean logout and full-run client.log scan", 60_000L, this::logout).alwaysRun());
        return checks;
    }

    void boot(CheckContext context) throws IOException {
        String version = RuneLiteProperties.getMicrobotVersion();
        context.detail("microbotVersion", version);
        context.detail("microbotCommit", RuneLiteProperties.getMicrobotCommit());
        context.detail("runeliteVersion", RuneLiteProperties.getVersion());
        context.require(version != null && !version.isBlank() && !version.contains("${") && !"0.0.0".equals(version),
                "Microbot version is not stamped: '" + version + "'");
        if (settings.expectedVersion != null && !settings.expectedVersion.isBlank()) {
            context.require(settings.expectedVersion.equals(version),
                    "Expected Microbot version " + settings.expectedVersion + " but client reports " + version);
        }

        List<String> inactive = new ArrayList<>();
        List<String> active = new ArrayList<>();
        for (Plugin plugin : pluginManager.getPlugins()) {
            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            if (descriptor == null || !descriptor.alwaysOn() || descriptor.isExternal()) {
                continue;
            }
            if (pluginManager.isPluginActive(plugin)) {
                active.add(plugin.getClass().getSimpleName());
            } else {
                inactive.add(plugin.getClass().getSimpleName());
            }
        }
        context.detail("alwaysOnActive", active);
        context.require(active.stream().anyMatch("MicrobotPlugin"::equals), "MicrobotPlugin is not active");
        context.require(inactive.isEmpty(), "Always-on plugins not active: " + inactive);

        List<String> errors = ClientLogScanner.findErrors(
                ClientLogScanner.readCurrentRun(ClientLogScanner.defaultLogFile()), ClientLogScanner.DEFAULT_ALLOWLIST);
        context.detail("startupErrorLines", errors.size());
        context.require(errors.isEmpty(), "ERROR lines during startup: " + summarize(errors));
    }

    void login(CheckContext context) {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        context.detail("uptimeAtHarnessStartMs", uptime);
        requireLoggedIn(context);
        context.require(Microbot.getClient().getGameState() == GameState.LOGGED_IN, "Game state is " + Microbot.getClient().getGameState());
        context.require(uptime <= settings.loginTimeoutMs, "Login took longer than " + settings.loginTimeoutMs + "ms (uptime " + uptime + "ms)");
        WorldPoint location = location();
        context.require(location != null, "Player location is unknown");
        int world = Microbot.getClient().getWorld();
        context.detail("location", format(location));
        context.detail("world", world);
        context.detail("membersWorld", Rs2Player.isInMemberWorld());
        context.require(world > 0, "World number is not known");
    }

    void stateAndCaches(CheckContext context) {
        requireLoggedIn(context);
        walkToStart(context, "setupWalk", LUMBRIDGE_HALL, 4, 300_000L);

        Map<String, Integer> levels = new LinkedHashMap<>();
        int total = 0;
        for (Skill skill : Skill.values()) {
            int level = Rs2Player.getRealSkillLevel(skill);
            levels.put(skill.getName(), level);
            total += level;
            context.require(level >= 1 && level <= 99, "Implausible " + skill.getName() + " level " + level);
        }
        context.require(Rs2Player.getRealSkillLevel(Skill.HITPOINTS) >= 10, "Hitpoints below 10");
        context.detail("skills", levels.size());
        context.detail("totalLevel", total);

        List<Rs2ItemModel> inventory = Rs2Inventory.all();
        int rawInventory = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            net.runelite.api.ItemContainer container = Microbot.getClient().getItemContainer(net.runelite.api.InventoryID.INVENTORY);
            if (container == null) {
                return 0;
            }
            int count = 0;
            for (net.runelite.api.Item item : container.getItems()) {
                if (item != null && item.getId() > 0) {
                    count++;
                }
            }
            return count;
        }).orElse(-1);
        context.detail("inventoryItems", inventory.size());
        context.require(inventory.size() == rawInventory, "Inventory cache has " + inventory.size() + " items, client container has " + rawInventory);
        context.require(!inventory.isEmpty(), "Inventory is empty; the release account needs coins and a few junk items");
        context.detail("equipmentItems", Rs2Equipment.items().size());

        int runVarp = Microbot.getVarbitPlayerValue(173);
        int rawRunVarp = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getVarpValue(173)).orElse(-1);
        int stoneVarbit = Microbot.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT);
        int rawStoneVarbit = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT)).orElse(-1);
        context.detail("runVarp", runVarp + "/" + rawRunVarp);
        context.detail("stoneArrangementVarbit", stoneVarbit + "/" + rawStoneVarbit);
        context.require(runVarp == rawRunVarp, "Cached varp 173 " + runVarp + " differs from client " + rawRunVarp);
        context.require(stoneVarbit == rawStoneVarbit, "Cached varbit differs from client: " + stoneVarbit + " vs " + rawStoneVarbit);
        context.require(Rs2Player.isRunEnabled() == (rawRunVarp == 1), "Rs2Player.isRunEnabled disagrees with varp 173");

        WorldPoint here = location();
        List<Rs2NpcModel> npcs = Microbot.getRs2NpcCache().query().within(here, 20).toListOnClientThread();
        List<Rs2TileObjectModel> objects = Microbot.getRs2TileObjectCache().query().within(here, 15).toListOnClientThread();
        List<Rs2TileItemModel> groundItems = Microbot.getRs2TileItemCache().query().within(here, 20).toListOnClientThread();
        List<Rs2PlayerModel> players = Microbot.getRs2PlayerCache().query().toListOnClientThread();
        List<String> npcNames = Microbot.getClientThread().runOnClientThreadOptional(() -> npcs.stream()
                .map(Rs2NpcModel::getName).filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList()))
                .orElse(List.of());
        List<String> itemNames = Microbot.getClientThread().runOnClientThreadOptional(() -> groundItems.stream()
                .map(Rs2TileItemModel::getName).filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList()))
                .orElse(List.of());
        context.detail("npcs", npcs.size() + " " + npcNames);
        context.detail("objects", objects.size());
        context.detail("groundItems", groundItems.size() + " " + itemNames);
        context.detail("otherPlayers", players.size());
        context.require(npcs.size() >= 3, "Expected at least 3 NPCs near Lumbridge castle hall, found " + npcs.size());
        context.require(objects.size() >= 20, "Expected at least 20 tile objects near Lumbridge castle hall, found " + objects.size());
        context.require(!groundItems.isEmpty(), "Expected the Lumbridge kitchen item spawns in the ground item cache");
        context.require(Rs2Player.getLocalPlayer() != null, "Local player model unavailable");
        Rs2NpcModel nearestNpc = Microbot.getRs2NpcCache().query().within(here, 20).nearestOnClientThread();
        context.require(nearestNpc != null, "nearestOnClientThread returned no NPC although the list was non-empty");
    }

    void widgets(CheckContext context) {
        requireLoggedIn(context);
        InterfaceTab original = Rs2Tab.getCurrentTab();
        context.detail("originalTab", original);
        context.onCleanup("restore tab", () -> {
            if (original != null && original != InterfaceTab.NOTHING_SELECTED && Rs2Tab.getCurrentTab() != original) {
                Rs2Tab.switchTo(original);
            }
        });

        int topLevel = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getTopLevelInterfaceId()).orElse(-1);
        context.detail("topLevelInterface", topLevel);
        clickTabStone(context, topLevel, "Skills", InterfaceTab.SKILLS);

        context.require(waitFor(() -> Rs2Widget.hasWidget(SKILLS_TEXT), 3000), "Rs2Widget.hasWidget('" + SKILLS_TEXT + "') returned false on the skills tab");
        Widget totalLevel = Rs2Widget.findWidget(SKILLS_TEXT, false);
        context.require(totalLevel != null, "Widget text search for '" + SKILLS_TEXT + "' found nothing");
        String text = Microbot.getClientThread().runOnClientThreadOptional(totalLevel::getText).orElse("");
        context.detail("searchHit", (totalLevel.getId() >>> 16) + ":" + (totalLevel.getId() & 0xFFFF) + " '" + text.replace("<br>", " ") + "'");
        context.require(text.contains(SKILLS_TEXT), "Search hit does not carry the text '" + SKILLS_TEXT + "': '" + text + "'");

        clickTabStone(context, topLevel, "Inventory", InterfaceTab.INVENTORY);
    }

    private void clickTabStone(CheckContext context, int topLevel, String action, InterfaceTab expected) {
        Optional<int[]> stone = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            for (int child = 0; child < 200; child++) {
                Widget widget = Microbot.getClient().getWidget(topLevel, child);
                if (widget == null || widget.isHidden() || widget.getActions() == null) {
                    continue;
                }
                for (String candidate : widget.getActions()) {
                    if (action.equalsIgnoreCase(candidate)) {
                        return new int[]{topLevel, child};
                    }
                }
            }
            return null;
        });
        context.require(stone.isPresent(), "No visible tab stone with action '" + action + "' under interface " + topLevel);
        boolean clicked = Rs2Widget.clickWidget(stone.get()[0], stone.get()[1]);
        boolean switched = waitFor(() -> Rs2Tab.getCurrentTab() == expected, 5000);
        context.detail(action + "Stone", stone.get()[0] + ":" + stone.get()[1] + " clicked=" + clicked + " tab=" + Rs2Tab.getCurrentTab());
        context.require(switched, "Clicking the " + action + " tab stone did not switch to " + expected + " (now " + Rs2Tab.getCurrentTab() + ")");
    }

    void walkerTransport(CheckContext context) {
        requireLoggedIn(context);
        walkToStart(context, "setupWalk", LUMBRIDGE_HALL, 6, 240_000L);
        List<String> failures = new ArrayList<>();
        step(failures, () -> walkOrFail(context, "shortWalk", LUMBRIDGE_COURTYARD, 1, 60_000L));
        step(failures, () -> walkThroughDoor(context));
        step(failures, () -> {
            ReleaseSmokeLive.WalkResult stairs = walkOrFail(context, "stairsWalk", LUMBRIDGE_BANK, 3, 120_000L);
            context.require(stairs.end.getPlane() == 2, "Stairs walk ended on plane " + stairs.end.getPlane());
        });
        context.require(failures.isEmpty(), String.join(" | ", failures));
    }

    private static void step(List<String> failures, Runnable step) {
        try {
            step.run();
        } catch (CheckFailedException e) {
            failures.add(e.getMessage());
        }
    }

    private void walkThroughDoor(CheckContext context) {
        List<String> attempts = new ArrayList<>();
        for (WorldPoint anchor : DOOR_CANDIDATES) {
            ReleaseSmokeLive.WalkResult approach = ReleaseSmokeLive.walk(anchor, 4, 90_000L);
            if (!approach.arrived(4)) {
                attempts.add(format(anchor) + ": approach " + approach.describe());
                continue;
            }
            Optional<ReleaseSmokeLive.Door> door = ReleaseSmokeLive.doorAt(anchor, 1);
            if (door.isEmpty()) {
                attempts.add(format(anchor) + ": no door found");
                continue;
            }
            boolean initiallyClosed = door.get().isClosed();
            context.detail("door", door.get().describe());
            if (!ReleaseSmokeLive.setDoorState(context, "doorPrepare", anchor, true, 6000)) {
                attempts.add(format(anchor) + ": could not close door before walking");
                continue;
            }
            AtomicBoolean restored = new AtomicBoolean(false);
            context.onCleanup("restore door " + format(anchor), () -> {
                if (restored.get()) {
                    return;
                }
                if (distance(location(), anchor) > 6) {
                    ReleaseSmokeLive.walk(anchor, 3, 90_000L);
                }
                if (!ReleaseSmokeLive.setDoorState(context, "doorRestore", anchor, initiallyClosed, 6000)) {
                    throw new IllegalStateException("door at " + format(anchor) + " not restored to " + (initiallyClosed ? "closed" : "open"));
                }
            });
            ReleaseSmokeLive.Door closed = ReleaseSmokeLive.doorAt(anchor, 1).orElseThrow();
            Optional<Integer> orientation = ReleaseSmokeLive.wallOrientation(closed.location);
            WorldPoint across = orientation.map(value -> ReleaseSmokeLive.acrossWall(closed.location, value)).orElse(null);
            context.require(across != null, "Cannot read wall orientation for " + closed.describe());
            boolean doorTileReachable = ReleaseSmokeLive.reachable(closed.location);
            WorldPoint target = doorTileReachable ? across : closed.location;
            context.detail("doorWalkTarget", format(target) + " (orientation " + orientation.get() + ", doorTileReachable=" + doorTileReachable + ")");
            ReleaseSmokeLive.WalkResult through = ReleaseSmokeLive.walk(target, 0, 90_000L);
            context.detail("doorWalk", through.describe());
            context.detail("doorAfterWalk", ReleaseSmokeLive.doorAt(anchor, 1).map(ReleaseSmokeLive.Door::describe).orElse("none"));
            restored.set(ReleaseSmokeLive.setDoorState(context, "doorRestore", anchor, initiallyClosed, 6000));
            context.require(through.arrived(0), "Walk through closed door to " + format(target) + " failed: " + through.describe());
            return;
        }
        throw new CheckFailedException("No usable door near Lumbridge castle: " + attempts);
    }

    void bank(CheckContext context) {
        requireLoggedIn(context);
        BankLocation nearest = Rs2Bank.getNearestBank(LUMBRIDGE_BANK);
        context.detail("nearestBank", nearest == null ? "null" : nearest.name());
        context.require(nearest != null, "Rs2Bank.getNearestBank returned null at Lumbridge");
        if (distance(location(), nearest.getWorldPoint()) > 6) {
            walkOrFail(context, "bankWalk", nearest.getWorldPoint(), 4, 150_000L);
        }

        Map<Integer, Integer> before = inventorySnapshot();
        context.onCleanup("close bank", () -> {
            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
                if (!waitFor(() -> !Rs2Bank.isOpen(), 5000)) {
                    throw new IllegalStateException("bank still open");
                }
            }
        });

        boolean opened = Rs2Bank.openBank() && waitFor(Rs2Bank::isOpen, 10_000);
        if (!opened && Rs2Bank.isBankPinWidgetVisible()) {
            context.skip("Bank PIN prompt shown and no PIN configured for the profile");
        }
        context.require(opened || Rs2Bank.isOpen(), "Bank did not open");
        waitFor(() -> !Rs2Bank.bankItems().isEmpty(), 5000);
        int bankSize = Rs2Bank.bankItems() == null ? 0 : Rs2Bank.bankItems().size();
        context.detail("bankItemStacks", bankSize);

        Rs2ItemModel junk = pickJunk();
        if (junk == null) {
            context.skip("No junk item in inventory (expected one of " + JUNK_ITEMS + ")");
        }
        int itemId = junk.getId();
        boolean alreadyBanked = Rs2Bank.count(itemId) > 0;
        context.detail("junkItem", junk.getName() + "#" + itemId + (alreadyBanked ? " (stack exists in bank)" : " (new bank slot)"));
        context.onCleanup("withdraw junk back", () -> {
            int missing = before.getOrDefault(itemId, 0) - Rs2Inventory.itemQuantity(itemId);
            if (missing > 0 && Rs2Bank.isOpen()) {
                if (missing == 1) {
                    Rs2Bank.withdrawOne(itemId);
                } else {
                    Rs2Bank.withdrawX(itemId, missing);
                }
                if (!waitFor(() -> Rs2Inventory.itemQuantity(itemId) >= before.getOrDefault(itemId, 0), 5000)) {
                    throw new IllegalStateException("junk item not withdrawn back");
                }
            }
        });

        boolean noted = Rs2Bank.hasWithdrawAsNote();
        context.detail("withdrawAsNote", noted);
        if (noted) {
            context.require(Rs2Bank.setWithdrawAsItem(), "Could not switch withdraw mode to items");
            context.onCleanup("restore withdraw-as-note", () -> {
                if (Rs2Bank.isOpen() && !Rs2Bank.hasWithdrawAsNote()) {
                    Rs2Bank.setWithdrawAsNote();
                }
            });
        }

        int invBefore = Rs2Inventory.itemQuantity(itemId);
        int bankBefore = Rs2Bank.count(itemId);
        context.detail("countsBefore", "inventory=" + invBefore + ", bank=" + bankBefore);
        context.require(Rs2Bank.depositOne(itemId), "depositOne returned false");
        boolean deposited = waitFor(() -> Rs2Inventory.itemQuantity(itemId) == invBefore - 1, 6000);
        if (!deposited && !alreadyBanked && !Rs2Player.isMember() && bankSize >= F2P_BANK_SLOTS) {
            context.skip("Bank holds " + bankSize + " stacks (F2P limit " + F2P_BANK_SLOTS + ") and no junk item already has a bank stack");
        }
        context.require(deposited, "Inventory count did not drop after depositOne (" + Rs2Inventory.itemQuantity(itemId) + ")");
        context.require(waitFor(() -> Rs2Bank.count(itemId) == bankBefore + 1, 6000),
                "Bank count did not rise after deposit (" + bankBefore + " -> " + Rs2Bank.count(itemId) + ")");

        context.require(Rs2Bank.withdrawOne(itemId), "withdrawOne returned false");
        context.require(waitFor(() -> Rs2Inventory.itemQuantity(itemId) == invBefore, 6000),
                "Inventory count not restored after withdraw (" + Rs2Inventory.itemQuantity(itemId) + " vs " + invBefore + ")");
        Rs2Bank.closeBank();
        context.require(waitFor(() -> !Rs2Bank.isOpen(), 5000), "Bank still open after closeBank");

        Map<Integer, Integer> after = inventorySnapshot();
        context.require(before.equals(after), "Inventory not restored: before=" + before + " after=" + after);
    }

    private static Map<Integer, Integer> inventorySnapshot() {
        Map<Integer, Integer> snapshot = new HashMap<>();
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            snapshot.merge(item.getId(), item.getQuantity(), Integer::sum);
        }
        return snapshot;
    }

    private static Rs2ItemModel pickJunk() {
        List<Rs2ItemModel> candidates = new ArrayList<>();
        List<Rs2ItemModel> items = Rs2Inventory.all();
        for (String name : JUNK_ITEMS) {
            for (Rs2ItemModel item : items) {
                if (name.equalsIgnoreCase(item.getName()) && !item.isNoted()) {
                    candidates.add(item);
                }
            }
        }
        return candidates.stream()
                .filter(item -> Rs2Bank.count(item.getId()) > 0)
                .findFirst()
                .orElse(candidates.isEmpty() ? null : candidates.get(0));
    }

    void npcDialogue(CheckContext context) {
        requireLoggedIn(context);
        walkToStart(context, "setupWalk", LUMBRIDGE_BANK, 4, 180_000L);
        context.onCleanup("leave dialogue", () -> {
            if (Rs2Dialogue.isInDialogue()) {
                leaveDialogue();
                if (!waitFor(() -> !Rs2Dialogue.isInDialogue(), 6000)) {
                    throw new IllegalStateException("still in dialogue");
                }
            }
        });

        net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel banker = Rs2Npc.getNpc("Banker", true);
        context.require(banker != null, "No banker found near Lumbridge bank");
        context.require(Rs2Npc.interact(banker, "Talk-to"), "Talk-to banker interaction failed");
        context.require(waitFor(Rs2Dialogue::isInDialogue, 10_000), "Dialogue did not open after Talk-to");

        List<String> transcript = new ArrayList<>();
        boolean pickedOption = false;
        for (int step = 0; step < 8 && Rs2Dialogue.isInDialogue(); step++) {
            if (Rs2Dialogue.hasSelectAnOption()) {
                List<String> options = dialogueOptions();
                transcript.add("options=" + options);
                if (pickedOption) {
                    break;
                }
                boolean hasOption = options.stream().anyMatch(option -> option.contains(DIALOGUE_OPTION));
                context.require(hasOption, "Expected banker option '" + DIALOGUE_OPTION + "' in " + options);
                context.require(Rs2Dialogue.keyPressForDialogueOption(DIALOGUE_OPTION, false), "Could not select dialogue option");
                pickedOption = true;
                waitFor(() -> !Rs2Dialogue.hasSelectAnOption(), 3000);
                continue;
            }
            if (Rs2Dialogue.hasContinue()) {
                String text = Rs2Dialogue.getDialogueText();
                transcript.add("text=" + (text == null ? "" : text.length() > 60 ? text.substring(0, 60) : text));
                Rs2Dialogue.clickContinue();
                waitFor(() -> !Objects.equals(text, Rs2Dialogue.getDialogueText()) || !Rs2Dialogue.isInDialogue(), 4000);
                continue;
            }
            waitFor(() -> Rs2Dialogue.hasContinue() || Rs2Dialogue.hasSelectAnOption() || !Rs2Dialogue.isInDialogue(), 3000);
        }
        context.detail("transcript", transcript);
        context.require(pickedOption, "Never reached the banker option menu: " + transcript);

        if (Rs2Dialogue.isInDialogue()) {
            leaveDialogue();
        }
        context.require(waitFor(() -> !Rs2Dialogue.isInDialogue(), 6000), "Dialogue did not close");
        context.require(!Rs2Bank.isOpen(), "Bank opened during the banker dialogue");
    }

    private static List<String> dialogueOptions() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Dialogue.getDialogueOptions().stream()
                .map(Widget::getText).filter(Objects::nonNull).collect(Collectors.toList())).orElse(List.of());
    }

    private static void leaveDialogue() {
        WorldPoint here = location();
        if (here == null) {
            return;
        }
        for (WorldPoint step : List.of(here.dx(1), here.dx(-1), here.dy(1), here.dy(-1))) {
            if (ReleaseSmokeLive.reachable(step)) {
                Rs2Walker.walkFastCanvas(step);
                return;
            }
        }
    }

    void objectInteraction(CheckContext context) {
        requireLoggedIn(context);
        List<String> attempts = new ArrayList<>();
        for (WorldPoint anchor : DOOR_CANDIDATES) {
            ReleaseSmokeLive.WalkResult approach = ReleaseSmokeLive.walk(anchor, 3, 150_000L);
            if (!approach.arrived(3)) {
                attempts.add(format(anchor) + ": approach " + approach.describe());
                continue;
            }
            Optional<ReleaseSmokeLive.Door> door = ReleaseSmokeLive.doorAt(anchor, 1);
            if (door.isEmpty()) {
                attempts.add(format(anchor) + ": no door");
                continue;
            }
            boolean initiallyClosed = door.get().isClosed();
            context.detail("door", door.get().describe());
            context.onCleanup("restore door " + format(anchor), () -> {
                if (!ReleaseSmokeLive.setDoorState(context, "restore", anchor, initiallyClosed, 6000)) {
                    throw new IllegalStateException("door at " + format(anchor) + " not restored");
                }
            });
            boolean first = ReleaseSmokeLive.setDoorState(context, "toggle1", anchor, !initiallyClosed, 6000);
            if (!first) {
                attempts.add(format(anchor) + ": first toggle had no effect");
                continue;
            }
            boolean second = ReleaseSmokeLive.setDoorState(context, "toggle2", anchor, initiallyClosed, 6000);
            context.require(second, "Door at " + format(anchor) + " did not return to its original state");
            return;
        }
        throw new CheckFailedException("No door could be toggled: " + attempts);
    }

    void agentServer(CheckContext context) throws Exception {
        requireLoggedIn(context);
        String bindMode = configManager.getConfiguration(AgentServerConfig.GROUP, "bindMode");
        if ("UDS".equalsIgnoreCase(bindMode)) {
            context.skip("Agent Server is configured for UDS; this check speaks TCP only");
        }
        AgentServerPlugin plugin = Microbot.getPlugin(AgentServerPlugin.class);
        context.require(plugin != null, "AgentServerPlugin not registered");
        if (!pluginManager.isPluginActive(plugin)) {
            context.detail("startedForCheck", true);
            Microbot.startPlugin(plugin);
            context.require(waitFor(() -> pluginManager.isPluginActive(plugin), 10_000), "Agent Server plugin did not start");
            context.onCleanup("stop agent server", () -> {
                Microbot.stopPlugin(plugin);
                if (!waitFor(() -> !pluginManager.isPluginActive(plugin), 10_000)) {
                    throw new IllegalStateException("agent server still active");
                }
            });
        }
        String portValue = configManager.getConfiguration(AgentServerConfig.GROUP, AgentServerConfig.KEY_PORT);
        int port = portValue == null || portValue.isBlank() ? 8081 : Integer.parseInt(portValue.trim());
        String token = configManager.getConfiguration(AgentServerConfig.GROUP, AgentServerConfig.KEY_TOKEN);
        context.require(token != null && !token.isBlank(), "Agent Server token not configured");
        context.detail("port", port);

        int unauthenticatedStatus = status("http://127.0.0.1:" + port + "/state", null);
        context.detail("unauthenticatedStatus", unauthenticatedStatus);
        context.require(unauthenticatedStatus == 404,
                "Agent Server should answer an unauthenticated request with the opaque 404, got HTTP " + unauthenticatedStatus);

        JsonObject state = getJson(port, token, "/state").getAsJsonObject();
        context.require(state.has("loggedIn") && state.get("loggedIn").getAsBoolean(), "/state does not report loggedIn: " + state);
        JsonObject position = state.getAsJsonObject("player").getAsJsonObject("position");
        WorldPoint reported = new WorldPoint(position.get("x").getAsInt(), position.get("y").getAsInt(), position.get("plane").getAsInt());
        context.detail("statePosition", format(reported));
        context.require(distance(reported, location()) <= 5, "/state position " + format(reported) + " differs from " + format(location()));

        JsonObject inventory = getJson(port, token, "/inventory").getAsJsonObject();
        int reportedCount = inventory.get("count").getAsInt();
        context.detail("inventoryCount", reportedCount);
        context.require(reportedCount == Rs2Inventory.count(), "/inventory count " + reportedCount + " != " + Rs2Inventory.count());

        JsonObject describe = getJson(port, token, "/widgets/describe?groupId=" + net.runelite.api.gameval.InterfaceID.INVENTORY + "&childId=0&depth=2").getAsJsonObject();
        context.detail("widgetsDescribed", describe.get("count").getAsInt());
        context.require(describe.get("count").getAsInt() > 0, "/widgets/describe returned no widgets for the inventory");

        JsonElement skills = getJson(port, token, "/skills");
        int skillCount = countSkills(skills);
        context.detail("skills", skillCount);
        context.require(skillCount >= 23, "/skills returned " + skillCount + " skills");
    }

    private static int countSkills(JsonElement skills) {
        if (skills.isJsonArray()) {
            return skills.getAsJsonArray().size();
        }
        JsonObject object = skills.getAsJsonObject();
        if (object.has("skills")) {
            JsonElement inner = object.get("skills");
            return inner.isJsonArray() ? inner.getAsJsonArray().size() : inner.getAsJsonObject().size();
        }
        return object.size();
    }

    private static int status(String url, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);
        if (token != null) {
            connection.setRequestProperty("X-Agent-Token", token);
        }
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    private static JsonElement getJson(int port, String token, String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("X-Agent-Token", token);
        try {
            int code = connection.getResponseCode();
            InputStream stream = code < 400 ? connection.getInputStream() : connection.getErrorStream();
            String body = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (code != 200) {
                throw new CheckFailedException(path + " returned HTTP " + code + ": " + truncate(body, 200));
            }
            return new JsonParser().parse(body);
        } finally {
            connection.disconnect();
        }
    }

    void walkerMedium(CheckContext context) {
        requireLoggedIn(context);
        walkToStart(context, "setupWalk", LUMBRIDGE_COURTYARD, 6, 300_000L);
        ReleaseSmokeLive.WalkResult walk = walkOrFail(context, "mediumWalk", VARROCK_WEST_BANK, 5, 360_000L);
        context.detail("maxStepJump", walk.maxJump + " " + walk.maxJumpEdge);
        context.detail("teleportObserved", walk.maxJump > 30);
    }

    void grandExchange(CheckContext context) {
        requireLoggedIn(context);
        walkToStart(context, "geWalk", GRAND_EXCHANGE, 6, 240_000L);
        context.onCleanup("close grand exchange", () -> {
            if (Rs2GrandExchange.isOpen()) {
                Rs2GrandExchange.closeExchange();
                if (!waitFor(() -> !Rs2GrandExchange.isOpen(), 5000)) {
                    throw new IllegalStateException("grand exchange still open");
                }
            }
            if (Rs2Dialogue.isInDialogue()) {
                leaveDialogue();
            }
        });
        boolean opened = Rs2GrandExchange.openExchange();
        if (!opened && Rs2Dialogue.isInDialogue()) {
            String text = Rs2Dialogue.getDialogueText();
            context.detail("clerkDialogue", text);
            context.skip("Grand Exchange clerk refused to open the exchange: " + text);
        }
        context.require(opened && Rs2GrandExchange.isOpen(), "Grand Exchange interface did not open");
        Rs2GrandExchange.closeExchange();
        context.require(waitFor(() -> !Rs2GrandExchange.isOpen(), 5000), "Grand Exchange did not close");
    }

    void worldHop(CheckContext context) {
        requireLoggedIn(context);
        int original = Microbot.getClient().getWorld();
        context.detail("originalWorld", original);
        WorldResult worlds = worldService.getWorlds();
        context.require(worlds != null && !worlds.getWorlds().isEmpty(), "World list service returned no worlds");
        int target = settings.hopWorld > 0 ? settings.hopWorld : pickF2pWorld(worlds.getWorlds(), original);
        context.require(target > 0, "No other F2P world available to hop to");
        context.detail("targetWorld", target);
        context.onCleanup("return to original world", () -> {
            if (Microbot.isLoggedIn() && Microbot.getClient().getWorld() != original) {
                if (!Microbot.hopToWorld(original)) {
                    throw new IllegalStateException("could not hop back to world " + original);
                }
            }
        });

        boolean switcherOpenBefore = worldSwitcherOpen();
        context.detail("worldSwitcherOpenBefore", switcherOpenBefore);
        long start = System.currentTimeMillis();
        boolean hopped = Microbot.hopToWorld(target);
        context.detail("hopOutMs", System.currentTimeMillis() - start);
        if (!hopped || Microbot.getClient().getWorld() != target) {
            boolean switcherOpenAfter = worldSwitcherOpen();
            boolean retried = Microbot.hopToWorld(target) && Microbot.getClient().getWorld() == target;
            context.detail("worldSwitcherOpenAfterFirstAttempt", switcherOpenAfter);
            context.detail("secondAttemptHopped", retried);
            throw new CheckFailedException("Microbot.hopToWorld(" + target + ") did not hop (world switcher open before: "
                    + switcherOpenBefore + ", after: " + switcherOpenAfter + ", second attempt hopped: " + retried + ")");
        }
        context.require(waitFor(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN && location() != null, 15_000),
                "Not logged in with a location after hopping");

        start = System.currentTimeMillis();
        boolean back = Microbot.hopToWorld(original);
        context.detail("hopBackMs", System.currentTimeMillis() - start);
        context.require(back && Microbot.getClient().getWorld() == original, "Hop back to world " + original + " failed");
    }

    private static boolean worldSwitcherOpen() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget buttons = Microbot.getClient().getWidget(net.runelite.api.gameval.InterfaceID.Worldswitcher.BUTTONS);
            return buttons != null && !buttons.isHidden();
        }).orElse(false);
    }

    static int pickF2pWorld(List<World> worlds, int current) {
        WorldRegion currentRegion = worlds.stream()
                .filter(world -> world.getId() == current)
                .map(World::getRegion)
                .findFirst()
                .orElse(null);
        return worlds.stream()
                .filter(world -> world.getId() != current)
                .filter(world -> world.getTypes() == null || world.getTypes().isEmpty())
                .filter(world -> "-".equals(world.getActivity()))
                .filter(world -> world.getPlayers() > 0 && world.getPlayers() < 1500)
                .sorted(Comparator.comparing((World world) -> world.getRegion() != currentRegion)
                        .thenComparingInt(World::getPlayers))
                .map(World::getId)
                .findFirst()
                .orElse(-1);
    }

    void hubPlugins(CheckContext context) {
        boolean loaded = waitFor(() -> !microbotPluginManager.getManifestMap().isEmpty(), 60_000);
        Map<String, MicrobotPluginManifest> manifests = microbotPluginManager.getManifestMap();
        context.detail("manifestEntries", manifests.size());
        context.require(loaded && manifests.size() >= 20, "Plugin Hub manifest not loaded (" + manifests.size() + " entries)");
        long withJarInfo = manifests.values().stream()
                .filter(manifest -> manifest.getInternalName() != null && manifest.getVersion() != null)
                .count();
        context.require(withJarInfo == manifests.size(), "Some manifest entries are missing internalName/version");

        String internalName = settings.hubPlugin;
        MicrobotPluginManifest manifest = manifests.get(internalName);
        context.require(manifest != null, "Hub plugin " + internalName + " not in manifest");
        context.detail("hubPlugin", internalName + " " + manifest.getVersion());
        if (manifest.isDisable()) {
            context.skip("Hub plugin " + internalName + " is disabled upstream");
        }

        Optional<Plugin> existing = findExternal(internalName);
        if (existing.isPresent()) {
            context.detail("preinstalled", true);
            boolean wasActive = pluginManager.isPluginActive(existing.get());
            cyclePlugin(context, existing.get());
            if (wasActive) {
                Microbot.startPlugin(existing.get());
                context.require(waitFor(() -> pluginManager.isPluginActive(existing.get()), 10_000), "Could not restore active state");
            }
            return;
        }

        context.onCleanup("uninstall " + internalName, () -> {
            if (findExternal(internalName).isPresent()) {
                var removeOperation = microbotPluginManager.removePlugin(manifest);
                if (!waitFor(removeOperation::isDone, 30_000)) {
                    throw new IllegalStateException("Uninstall operation did not finish for " + internalName);
                }
                var removeResult = removeOperation.getNow(null);
                if (removeResult == null || !removeResult.isSuccess()) {
                    throw new IllegalStateException("Uninstall failed for " + internalName + ": "
                            + (removeResult == null ? "no result" : removeResult.getMessage()));
                }
                if (findExternal(internalName).isPresent()) {
                    throw new IllegalStateException(internalName + " still registered after remove");
                }
            }
        });
        long start = System.currentTimeMillis();
        var installOperation = microbotPluginManager.installPlugin(manifest, null);
        context.require(waitFor(installOperation::isDone, 30_000),
                "Hub plugin " + internalName + " install operation did not finish");
        var installResult = installOperation.getNow(null);
        context.require(installResult != null && installResult.isSuccess(),
                "Hub plugin " + internalName + " installation failed: "
                        + (installResult == null ? "no result" : installResult.getMessage()));
        boolean installed = waitFor(() -> findExternal(internalName).isPresent(), 30_000);
        context.detail("installMs", System.currentTimeMillis() - start);
        context.require(installed, "Hub plugin " + internalName + " did not load after install");
        Plugin plugin = findExternal(internalName).orElseThrow();
        context.detail("activeAfterInstall", pluginManager.isPluginActive(plugin));
        cyclePlugin(context, plugin);

        var removeOperation = microbotPluginManager.removePlugin(manifest);
        context.require(waitFor(removeOperation::isDone, 30_000),
                "Hub plugin " + internalName + " uninstall operation did not finish");
        var removeResult = removeOperation.getNow(null);
        context.require(removeResult != null && removeResult.isSuccess(),
                "Hub plugin " + internalName + " uninstall failed: "
                        + (removeResult == null ? "no result" : removeResult.getMessage()));
        context.require(findExternal(internalName).isEmpty(), "Hub plugin still registered after uninstall");
        context.require(microbotPluginManager.getInstalledPluginVersion(internalName).isEmpty(),
                "Installed version still recorded after uninstall");
    }

    private void cyclePlugin(CheckContext context, Plugin plugin) {
        boolean startReturned = Microbot.startPlugin(plugin);
        boolean started = waitFor(() -> pluginManager.isPluginActive(plugin), 10_000);
        context.detail("startPluginReturned", startReturned);
        context.require(started, plugin.getClass().getSimpleName() + " did not start");
        boolean stopReturned = Microbot.stopPlugin(plugin);
        boolean stopped = waitFor(() -> !pluginManager.isPluginActive(plugin), 10_000);
        context.detail("stopPluginReturned", stopReturned);
        context.require(stopped, plugin.getClass().getSimpleName() + " did not stop");
    }

    private Optional<Plugin> findExternal(String internalName) {
        return pluginManager.getPlugins().stream()
                .filter(plugin -> plugin.getClass().getSimpleName().equalsIgnoreCase(internalName))
                .filter(plugin -> {
                    PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
                    return descriptor != null && descriptor.isExternal();
                })
                .findFirst();
    }

    void scriptLifecycle(CheckContext context) {
        StateMachineExamplePlugin plugin = Microbot.getPlugin(StateMachineExamplePlugin.class);
        context.require(plugin != null, "StateMachineExamplePlugin not registered");
        if (pluginManager.isPluginActive(plugin)) {
            context.skip("State Machine Example is already running; not interfering with a user session");
        }
        context.onCleanup("stop state machine example", () -> {
            if (pluginManager.isPluginActive(plugin)) {
                Microbot.stopPlugin(plugin);
                if (!waitFor(() -> !pluginManager.isPluginActive(plugin), 10_000)) {
                    throw new IllegalStateException("example plugin still active");
                }
            }
        });

        List<Integer> poolSizes = new ArrayList<>();
        List<String> registry = new ArrayList<>();
        List<String> startStates = new ArrayList<>();
        for (int cycle = 1; cycle <= 2; cycle++) {
            int current = cycle;
            Microbot.startPlugin(plugin);
            context.require(waitFor(() -> pluginManager.isPluginActive(plugin), 10_000), "Cycle " + cycle + ": plugin did not start");
            context.require(waitFor(() -> exampleScript().isPresent(), 5000), "Cycle " + cycle + ": script not among Microbot.getActiveScripts()");
            StateMachineScript<?> script = exampleScript().orElseThrow();
            long loopsAtStart = script.getSnapshot() == null ? 0 : script.getSnapshot().loopCount();
            startStates.add(script.getCurrentState() == null ? "null" : script.getCurrentState().name());
            boolean looping = waitFor(() -> script.isRunning() && script.getSnapshot() != null
                    && script.getSnapshot().loopCount() >= loopsAtStart + 3, 15_000);
            context.require(looping, "Cycle " + cycle + ": script loop did not run (" + describeExample() + ")");
            registry.add(StateMachineScript.getRegistry().containsValue(script) ? "registered" : "missing");
            Microbot.stopPlugin(plugin);
            context.require(waitFor(() -> !pluginManager.isPluginActive(plugin), 10_000), "Cycle " + cycle + ": plugin did not stop");
            context.require(waitFor(() -> !script.isRunning(), 5000), "Cycle " + current + ": script loop still running after stop");
            context.require(waitFor(() -> busyScriptThreads().isEmpty(), 5000),
                    "Cycle " + cycle + ": script threads still executing after stop: " + busyScriptThreads());
            poolSizes.add(scriptThreads().size());
        }
        context.detail("scriptPoolThreadsAfterStop", poolSizes);
        context.detail("stateMachineRegistry", registry);
        context.detail("stateAtStart", startStates);
        context.require(poolSizes.stream().allMatch(size -> size <= SCRIPT_POOL_CORE_SIZE),
                "Script pool threads exceed the Script executor core size " + SCRIPT_POOL_CORE_SIZE + ": " + poolSizes);
        context.require(!registry.contains("missing"),
                "StateMachineScript was not in StateMachineScript.getRegistry() while running after a restart: " + registry);
    }

    private static Optional<StateMachineScript<?>> exampleScript() {
        return Microbot.getActiveScripts().stream()
                .filter(script -> script instanceof StateMachineScript)
                .filter(script -> script.getClass().getSimpleName().equals("StateMachineExampleScript"))
                .<StateMachineScript<?>>map(script -> (StateMachineScript<?>) script)
                .findFirst();
    }

    private static String describeExample() {
        return exampleScript().map(script -> "running=" + script.isRunning() + ", state=" + script.getCurrentState()
                + ", loops=" + (script.getSnapshot() == null ? "?" : script.getSnapshot().loopCount())).orElse("not registered");
    }

    private static List<Thread> scriptThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && thread.getName().startsWith("StateMachineExampleScript-"))
                .collect(Collectors.toList());
    }

    private static List<String> busyScriptThreads() {
        List<String> busy = new ArrayList<>();
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            if (!entry.getKey().isAlive()) {
                continue;
            }
            for (StackTraceElement frame : entry.getValue()) {
                if (frame.getClassName().contains(".statemachineexample.")) {
                    busy.add(entry.getKey().getName());
                    break;
                }
            }
        }
        return busy;
    }

    void logout(CheckContext context) throws IOException {
        AutoLoginPlugin autoLogin = Microbot.getPlugin(AutoLoginPlugin.class);
        if (autoLogin != null && pluginManager.isPluginActive(autoLogin)) {
            Microbot.stopPlugin(autoLogin);
            context.detail("autoLoginStopped", waitFor(() -> !pluginManager.isPluginActive(autoLogin), 10_000));
        }
        if (Microbot.isLoggedIn()) {
            context.detail("tabBeforeLogout", Rs2Tab.getCurrentTab());
            context.detail("worldSwitcherOpenBeforeLogout", worldSwitcherOpen());
            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
                waitFor(() -> !Rs2Bank.isOpen(), 5000);
            }
            Rs2Player.logout();
            boolean loggedOut = waitFor(() -> Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN, 20_000);
            context.detail("gameStateAfterLogout", Microbot.getClient().getGameState());
            context.require(loggedOut, "Still logged in after Rs2Player.logout (" + Microbot.getClient().getGameState() + ")");
        } else {
            context.detail("alreadyLoggedOut", true);
        }

        List<String> lines = ClientLogScanner.readCurrentRun(ClientLogScanner.defaultLogFile());
        List<String> errors = ClientLogScanner.findErrors(lines, ClientLogScanner.DEFAULT_ALLOWLIST);
        context.detail("logLinesScanned", lines.size());
        context.detail("errorLines", errors.size());
        context.require(errors.isEmpty(), errors.size() + " ERROR line(s) in client.log during the run: " + summarize(errors));
    }

    static String summarize(List<String> errors) {
        return errors.stream().limit(5).map(line -> truncate(line, 300)).collect(Collectors.joining(" || "))
                + (errors.size() > 5 ? " (+" + (errors.size() - 5) + " more)" : "");
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
