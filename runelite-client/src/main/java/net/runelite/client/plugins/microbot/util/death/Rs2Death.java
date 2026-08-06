package net.runelite.client.plugins.microbot.util.death;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.annotations.Component;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID1;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.settings.Rs2Settings;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Death recovery for a normal death: locating the grave, looting it (paying the retrieval fee when
 * required), and falling back to Death's Office once the grave has expired.
 * <p>
 * Nothing here runs on its own. A script polls from its loop and decides what to do:
 * <pre>
 * if (Rs2Death.hasDeathToHandle()) {
 *     Rs2Death.recoverItems(config.deathBudget());   // grave only; 0 = free items only
 *     return State.BANK;                             // script re-gears however it already does
 * }
 * </pre>
 * Death's Office is opt-in, because its fee is uncapped and cannot be read before it is charged:
 * <pre>
 * Rs2Death.recoverItems(config.deathBudget(), true);
 * </pre>
 * The office charges an uncapped fee that it never shows before charging it, so there is deliberately no
 * spending cap here — one would be fiction. A script that wants to decide for itself can walk there,
 * inspect the contents, and back out without paying; only the reclaim costs anything:
 * <pre>
 * if (Rs2Death.walkToDeathsOffice() &amp;&amp; Rs2Death.enterDeathsOffice() &amp;&amp; Rs2Death.openDeathsOffice()) {
 *     Rs2Death.reclaimAll();          // or inspect first and call closeInterfaces() to decline
 *     Rs2Death.closeInterfaces();
 * }
 * </pre>
 * Use {@link #getPredictedGraveFee()} when you want a real number: it reads the figure the game itself
 * computed on the Items Kept on Death panel, rather than estimating one.
 * <p>
 * Or drive the steps directly — {@link #walkToGrave()}, {@link #openGrave()},
 * {@link #getGraveFee()}, {@link #lootGraveFreeItems()}, {@link #lootGravePaidItems(int)} — when the
 * script wants its own logic between them.
 * <p>
 * {@code recoverItems} and the {@code lootGrave*} / {@code reclaimAll} methods take <b>everything</b>.
 * To take only some of it, inspect first and filter:
 * <pre>
 * Rs2Death.openGrave();
 * Rs2Death.lootGraveItems(i -&gt; i.getName().contains("rune"));   // leaves the rest
 *
 * Rs2Death.openDeathsOffice();
 * Rs2Death.reclaimItems(i -&gt; i.getId() == ItemID.DRAGON_SCIMITAR);
 * </pre>
 * {@link #getGraveFreeItems()}, {@link #getGravePaidItems()} and {@link #getDeathsOfficeItems()} show
 * what is waiting. Note the asymmetry: the office charges per item reclaimed, so taking less costs less,
 * whereas a grave's fee covers its whole paid half at once. And anything left in a <b>grave</b> is only
 * safe until the timer expires — it then moves to Death's Office at the higher fee — while anything left
 * with Death keeps indefinitely.
 * <p>
 * Items left behind are not destroyed; they keep in Death's Office indefinitely.
 * <p>
 * Fee schedules, for reference — this class never computes them, it reads what the game reports:
 * a <b>grave</b> charges flat coin amounts per item by tier (1,000 / 10,000 / 100,000 for 100k–1m /
 * 1m–10m / 10m+), total capped at 500,000; <b>Death's Office</b> charges an uncapped 5%. Both test the
 * item's <i>unit</i> price against 100,000, so a large stack of cheap items is free from either —
 * confirmed in game with 862 coal at 146 each. Ironmen pay half. Documented exceptions exist and do not
 * follow the unit-price rule (a stack of amulet of glory (6) over 100,000 is charged 10% at the office),
 * which is why nothing here estimates a fee.
 * <p>
 * The first-death Death's Domain tutorial is <b>not</b> handled here — that stays with
 * {@link net.runelite.client.plugins.microbot.util.events.DeathEvent}, which normally only fires once
 * per account.
 */
@Slf4j
public class Rs2Death {

    /**
     * Grave NPC ids run contiguously from {@code GRAVESTONE_DEFAULT} to {@code GRAVESTONE_ANGEL_255}
     * (516 ids covering every player-name/cosmetic permutation), so match on the range rather than
     * enumerating them.
     */
    private static final int GRAVE_NPC_ID_MIN = NpcID.GRAVESTONE_DEFAULT;
    private static final int GRAVE_NPC_ID_MAX = NpcID.GRAVESTONE_ANGEL_255;

    private static final String GRAVE_LOOT_ACTION = "Loot";

    /** Per-slot action on a grave item, for selective looting. */
    private static final String GRAVE_TAKE_ACTION = "Take";

    /** Per-slot action in Death's Office — verified live; the office selects first, then takes. */
    private static final String DEATH_OFFICE_SELECT_ACTION = "Select";

    /** Death's reclaim dialogue choice, verified in game. Matched as a substring, so it tolerates
     * reordering and the trailing punctuation ("Yes, have you got anything for me?"). */
    private static final String DEATH_RECLAIM_OPTION = "have you got anything for me";

    /** Verified in-game against the Lumbridge entrance object. */
    private static final String ENTER_DEATHS_DOMAIN_ACTION = "Enter Death's Domain";

    /** Death's Domain is its own region; the same one {@code DeathEvent} watches. */
    private static final int DEATH_DOMAIN_REGION_ID = 12633;

    private static final int ENTER_TIMEOUT_MS = 10_000;

    /** {@code GRAVESTONE_DURATION} is measured in game ticks, so convert before reporting a Duration. */
    private static final long GAME_TICK_MS = 600L;

    /** Graves are lootable from up to 7 tiles with line of sight. */
    private static final int GRAVE_INTERACT_DISTANCE = 7;

    /** Widest a loaded scene can be, used as the search radius when locating the grave. */
    private static final int SCENE_RADIUS = 104;

    private static final int INTERFACE_TIMEOUT_MS = 5_000;
    private static final int LOOT_TIMEOUT_MS = 3_000;

    private static final Pattern DIGITS = Pattern.compile("[\\d,]+");

    /** Captures whatever follows "Fee:" in the Items Kept on Death caption, e.g. "(Fee: None)". */
    private static final Pattern FEE_LABEL = Pattern.compile("(?i)fee:\\s*([^)<]+)");


    @Getter
    private static volatile WorldPoint lastDeathLocation;

    @Getter
    private static volatile Instant lastDeathTime;

    /**
     * Whether a grave was ever seen standing since the last recorded death. Without this a PvP death —
     * which hands the tradeables to the killer and spawns no grave at all — looks identical to a grave
     * that expired into Death's Office.
     */
    private static volatile boolean graveSeen;

    // region state

    /**
     * Records the local player's death. Wired from {@code MicrobotPlugin#onActorDeath}.
     */
    public static void handleActorDeath(ActorDeath event) {
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null || event.getActor() != localPlayer) return;

        lastDeathLocation = localPlayer.getWorldLocation();
        lastDeathTime = Instant.now();
        graveSeen = false;
        log.info("Local player died at {} (wilderness level {})",
                lastDeathLocation, Rs2Pvp.getWildernessLevelFrom(lastDeathLocation));
    }

    /**
     * Notes that a grave actually appeared. Wired from {@code MicrobotPlugin#onVarbitChanged}.
     */
    public static void onVarbitChanged(VarbitChanged event) {
        // Non-zero, not == 1: the varbit reads 133 with a grave standing. Matching on 1 never fires,
        // which would leave graveSeen false forever and permanently disable the Death's Office fallback.
        if (event.getVarbitId() == VarbitID.GRAVESTONE_VISIBLE && event.getValue() != 0) {
            graveSeen = true;
        }
    }

    /**
     * Forgets the recorded death. Called automatically once items are recovered; scripts that collect
     * their own grave manually should call this so recovery does not later walk to an empty
     * Death's Office.
     */
    public static void clearDeathState() {
        lastDeathLocation = null;
        lastDeathTime = null;
        graveSeen = false;
    }

    /**
     * @return {@code true} while the local player is playing the death animation. This is only true for
     * the brief window before the respawn — use {@link #hasGrave()} to detect the aftermath.
     */
    public static boolean isDead() {
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> {
                    Player local = Microbot.getClient().getLocalPlayer();
                    return local != null && local.isDead();
                })
                .orElse(false);
    }

    public static boolean hasDiedRecently(long withinMs) {
        Instant died = lastDeathTime;
        return died != null && Duration.between(died, Instant.now()).toMillis() <= withinMs;
    }

    /**
     * @return {@code true} if the player currently has an uncollected grave somewhere in the world.
     * <p>
     * {@code GRAVESTONE_VISIBLE} is <b>not</b> a boolean despite the name. Observed live: {@code 0} with
     * no grave, and a steady {@code 133} with one standing — held constant across repeated samples, so
     * it is neither a flag nor a countdown. Whatever it encodes, only zero versus non-zero is
     * meaningful; testing {@code == 1} reports "no grave" while a grave is standing.
     */
    public static boolean hasGrave() {
        return Microbot.getVarbitValue(VarbitID.GRAVESTONE_VISIBLE) != 0;
    }

    /**
     * Remaining grave time.
     * <p>
     * {@code GRAVESTONE_DURATION} counts <b>game ticks</b>, not seconds — verified live, decrementing
     * 1461 to 1377 over roughly 50 seconds, and starting from 1500 ticks (1500 × 0.6s = 900s = the
     * nominal 15 minutes). Reading it as seconds overstates the remaining time by 40%.
     * <p>
     * The underlying timer pauses while logged out, while the grave interface is open, and while the
     * player stands idle — the idle pause engages after a few ticks rather than immediately, which is why
     * a sample taken right after stopping still shows it decrementing. A grave therefore routinely
     * outlives fifteen minutes of wall-clock time, so read this varbit rather than timing from the death.
     */
    public static Duration getGraveTimeRemaining() {
        int ticks = Math.max(0, Microbot.getVarbitValue(VarbitID.GRAVESTONE_DURATION));
        return Duration.ofMillis(ticks * GAME_TICK_MS);
    }

    /**
     * @return {@code true} when a grave was seen standing after the last death but is no longer there,
     * meaning the items have moved on to Death's Office.
     * <p>
     * Requires the grave to have actually appeared. A PvP death in the Wilderness hands the tradeables
     * straight to the killer and may spawn no grave at all — without that check this would report an
     * expired grave and send the script across the map to an empty Death's Office.
     * <p>
     * Stays {@code true} until {@link #clearDeathState()} runs, which is why recovery clears the record
     * on success: {@code GRAVESTONE_VISIBLE} drops to zero identically whether the grave expired or was
     * emptied, so the varbit alone cannot tell the two apart.
     */
    public static boolean hasGraveExpired() {
        return graveSeen && !hasGrave();
    }

    /**
     * Wilderness level of the spot the player died at, or {@code 0} if that was outside the Wilderness
     * or no death is recorded.
     * <p>
     * Scripts should check this before walking back: returning to a deep-Wilderness death spot is how a
     * script ends up in a die-return-die loop against the same player killer.
     */
    public static int getDeathWildernessLevel() {
        WorldPoint deathLocation = lastDeathLocation;
        return deathLocation == null ? 0 : Rs2Pvp.getWildernessLevelFrom(deathLocation);
    }

    // endregion

    // region items kept on death

    /**
     * @return {@code true} when the "Items Kept on Death" panel is open. Reached from the worn
     * equipment tab; this API only reads it, it does not open it.
     */
    public static boolean isItemsKeptOnDeathOpen() {
        return Rs2Widget.isWidgetVisible(InterfaceID.Deathkeep.KEPT);
    }

    /**
     * The items the player would keep if they died right now — normally the three most valuable, four
     * with Protect Item.
     * <p>
     * Reflects whichever scenario the panel's toggles are set to (Protect Item, PK skull, killed by a
     * player, deep Wilderness), so it answers "what happens under these conditions", not necessarily
     * "what happens on my next death".
     */
    public static List<Rs2ItemModel> getItemsKeptOnDeath() {
        return readDeathkeepItems(InterfaceID.Deathkeep.KEPT);
    }

    /**
     * The items that would go to the gravestone — everything not kept, minus anything the death would
     * destroy outright.
     */
    public static List<Rs2ItemModel> getItemsSentToGrave() {
        return readDeathkeepItems(InterfaceID.Deathkeep.GRAVE);
    }

    /**
     * The gravestone fee the <b>game itself</b> has calculated for the current loadout, read straight off
     * the panel rather than estimated. Authoritative: it already accounts for the per-unit valuation,
     * ironman rates, and any discounted-death allowance.
     * <p>
     * Verified in game — 740 noted coal worth 111,000 in total at 150 each reported {@code Fee: None},
     * because a grave tests each item's unit price, not its stack value.
     *
     * @return the fee in coins, or {@code 0} when the panel reads "None" or is closed.
     */
    public static int getPredictedGraveFee() {
        String label = findDeathkeepLabel(InterfaceID.Deathkeep.GRAVE);
        if (label == null) return 0;

        Matcher matcher = FEE_LABEL.matcher(label);
        return matcher.find() ? parseFeeText(matcher.group(1)) : 0;
    }

    /**
     * The game's own "Guide risk value" for the current loadout — what the panel reports the player is
     * risking, in coins.
     *
     * @return the risk value, or {@code 0} when the panel is closed.
     */
    public static int getRiskValue() {
        return parseFee(Rs2Widget.getWidget(InterfaceID.Deathkeep.VALUE));
    }

    /**
     * Reads the item slots out of one of the panel's containers. The container also holds its own
     * caption as a plain child, so entries without an item id are skipped.
     */
    private static List<Rs2ItemModel> readDeathkeepItems(@Component int componentId) {
        return readItemContainer(componentId);
    }

    /**
     * Reads the item slots out of any of the death interfaces' item containers, in slot order. The
     * slot index is preserved on each {@link Rs2ItemModel}, because it is the {@code param0} needed to
     * click that specific slot.
     */
    private static List<Rs2ItemModel> readItemContainer(@Component int componentId) {
        Widget container = Rs2Widget.getWidget(componentId);
        if (container == null) return Collections.emptyList();

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            List<Rs2ItemModel> items = new ArrayList<>();
            Widget[] children = container.getDynamicChildren();
            if (children == null) return items;

            for (int slot = 0; slot < children.length; slot++) {
                int itemId = children[slot].getItemId();
                if (itemId <= 0) continue;
                items.add(new Rs2ItemModel(itemId, Math.max(1, children[slot].getItemQuantity()), slot));
            }
            return items;
        }).orElseGet(Collections::emptyList);
    }

    /**
     * Finds the caption inside a panel container. It sits alongside the item slots rather than in its own
     * component, so it has to be picked out by content.
     */
    private static String findDeathkeepLabel(@Component int componentId) {
        Widget container = Rs2Widget.getWidget(componentId);
        if (container == null) return null;

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget[] children = container.getDynamicChildren();
            if (children == null) return null;

            for (Widget child : children) {
                String text = child.getText();
                if (text != null && !text.isEmpty()) return text;
            }
            return null;
        }).orElse(null);
    }

    // endregion

    // region grave

    /**
     * Finds the player's grave in the loaded scene. When a death location is known, prefers the grave
     * closest to it so a nearby player's grave is never targeted by mistake.
     */
    public static Rs2NpcModel getGrave() {
        WorldPoint anchor = lastDeathLocation != null ? lastDeathLocation : Rs2Player.getWorldLocation();
        if (anchor == null) return null;

        return Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getId() >= GRAVE_NPC_ID_MIN && npc.getId() <= GRAVE_NPC_ID_MAX)
                .nearest(anchor, SCENE_RADIUS);
    }

    /**
     * Walks to the recorded death location. The grave only spawns into the scene once nearby, so this
     * relies on the location captured by {@link #handleActorDeath(ActorDeath)} rather than on finding
     * the NPC first.
     */
    public static boolean walkToGrave() {
        Rs2NpcModel grave = getGrave();
        if (grave != null) {
            return Rs2Walker.walkTo(grave.getWorldLocation(), GRAVE_INTERACT_DISTANCE);
        }

        WorldPoint deathLocation = lastDeathLocation;
        if (deathLocation == null) {
            log.warn("Cannot walk to grave: no death location recorded");
            return false;
        }
        return Rs2Walker.walkTo(deathLocation, GRAVE_INTERACT_DISTANCE);
    }

    public static boolean isGraveOpen() {
        return Rs2Widget.isWidgetVisible(InterfaceID.GravestoneGeneric.CONTENT);
    }

    /**
     * Opens the grave retrieval interface. {@link Rs2NpcModel#click(String)} matches the action against
     * the NPC composition case-insensitively and logs the available actions when it misses, so a casing
     * change in a game update surfaces as a warning rather than a silent no-op.
     */
    public static boolean openGrave() {
        if (isGraveOpen()) return true;

        Rs2NpcModel grave = getGrave();
        if (grave == null) {
            log.warn("Cannot open grave: no grave NPC in the loaded scene");
            return false;
        }

        if (!grave.click(GRAVE_LOOT_ACTION)) return false;
        return Global.sleepUntil(Rs2Death::isGraveOpen, INTERFACE_TIMEOUT_MS);
    }

    /**
     * @return the coin cost to reclaim the paid half of the grave, or {@code 0} when nothing is
     * outstanding. The in-game fee is tiered per item and capped at 500,000.
     * <p>
     * The {@code FEE} component is prose, not a bare number — verified live as {@code "Fee: Paid"}
     * with the pay section settled. Anything without digits reads as {@code 0}, which means "nothing
     * owed", <b>not</b> "there is nothing to claim". Do not use a zero here to skip clicking
     * {@code PAYBUTTON}.
     */
    public static int getGraveFee() {
        return parseFee(Rs2Widget.getWidget(InterfaceID.GravestoneGeneric.FEE));
    }

    /**
     * The items in the grave's free half — everything that costs nothing to reclaim. Requires the grave
     * interface to be open ({@link #openGrave()}).
     */
    public static List<Rs2ItemModel> getGraveFreeItems() {
        return readItemContainer(InterfaceID.GravestoneGeneric.FREEITEMS);
    }

    /**
     * The items in the grave's paid half — those behind the retrieval fee. Requires the grave interface
     * to be open ({@link #openGrave()}).
     */
    public static List<Rs2ItemModel> getGravePaidItems() {
        return readItemContainer(InterfaceID.GravestoneGeneric.PAYITEMS);
    }

    /**
     * Takes <b>everything</b> in the free half; items behind the fee are untouched and stay put. Use
     * {@link #lootGraveItems(Predicate)} to take only some of it.
     */
    public static boolean lootGraveFreeItems() {
        if (!isGraveOpen()) return false;
        clickAndSettle(InterfaceID.GravestoneGeneric.FREEBUTTON);
        return true;
    }

    /**
     * Takes only the grave items matching {@code filter}, one slot at a time, from both the free and the
     * paid half. Anything not matched is left in the grave — and a grave is consumed once emptied, so
     * whatever is left behind ends up at Death's Office rather than staying put.
     * <p>
     * Slots are clicked highest-index first: taking an item re-packs the container, so descending order
     * keeps the remaining slot indices valid.
     * <p>
     * Paying is still all-or-nothing at the game's level — the fee covers the whole paid half — so a
     * filter that matches anything in the paid half incurs the full fee. Check {@link #getGraveFee()}
     * first if that matters.
     *
     * @param filter chooses which items to take.
     * @return the number of slots successfully clicked.
     */
    public static int lootGraveItems(Predicate<Rs2ItemModel> filter) {
        if (!isGraveOpen()) return 0;

        int taken = takeMatchingSlots(InterfaceID.GravestoneGeneric.FREEITEMS, filter, GRAVE_TAKE_ACTION);
        taken += takeMatchingSlots(InterfaceID.GravestoneGeneric.PAYITEMS, filter, GRAVE_TAKE_ACTION);
        return taken;
    }

    /**
     * Clicks each slot in {@code containerId} whose item matches {@code filter}, in descending slot
     * order so earlier clicks cannot invalidate later indices.
     */
    private static int takeMatchingSlots(@Component int containerId, Predicate<Rs2ItemModel> filter,
                                         String action) {
        List<Rs2ItemModel> items = readItemContainer(containerId);
        int taken = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            Rs2ItemModel item = items.get(i);
            if (filter != null && !filter.test(item)) continue;
            if (Rs2Inventory.isFull()) {
                log.warn("Inventory full after taking {} item(s) — {} left in the interface",
                        taken, i + 1);
                break;
            }
            clickItemSlot(containerId, item, action);
            taken++;
        }
        return taken;
    }

    /**
     * Clicks one item slot in a death interface. {@code param0} is the slot index and {@code param1} the
     * container component, matching how {@code Rs2Bank} drives bank slots.
     */
    private static void clickItemSlot(@Component int containerId, Rs2ItemModel item, String action) {
        Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget container = Rs2Widget.getWidget(containerId);
            if (container == null) return null;
            Widget[] children = container.getDynamicChildren();
            if (children == null || item.getSlot() >= children.length) return null;
            return children[item.getSlot()].getBounds();
        }).orElse(null);

        Microbot.doInvoke(new NewMenuEntry()
                        .param0(item.getSlot())
                        .param1(containerId)
                        .opcode(MenuAction.CC_OP.getId())
                        .identifier(1)
                        .itemId(item.getId())
                        .option(action)
                        .target(item.getName()),
                bounds == null ? new Rectangle(1, 1) : bounds);
        Global.sleepUntilNextTick();
    }

    /**
     * Claims the items behind the retrieval fee, when the account can afford it and the fee fits the
     * budget. Everything lands in the inventory — this interface has no send-to-bank option.
     *
     * The fee is charged to Death's Coffer if it holds anything, and to the bank otherwise — never to
     * carried coins. The player does not need to be holding gold, which matters because a freshly
     * respawned one generally is not.
     *
     * @param budget the highest fee to pay, or {@link Integer#MAX_VALUE} for no limit.
     * @return {@code false} when the paid half was deliberately left behind, which is a normal outcome
     * rather than an error — the items keep in Death's Office.
     */
    public static boolean lootGravePaidItems(int budget) {
        if (!isGraveOpen()) return false;

        // A zero fee is not a reason to skip the claim. The FEE component is prose, not a number —
        // verified live reading "Fee: Paid" — so getGraveFee() legitimately reports 0 when nothing is
        // outstanding. Returning early there would abandon items that cost nothing to take.
        //
        // Deliberately no carried-coin check: the fee comes out of Death's Coffer first and the bank
        // second, never the inventory. Gating on coins in the backpack refuses reclaims the account can
        // comfortably afford — a freshly respawned player is usually carrying nothing at all.
        int fee = getGraveFee();
        if (fee > 0 && fee > budget) {
            log.info("Grave fee {} is over the {} budget, leaving the paid items to Death's Office",
                    fee, budget);
            return false;
        }

        clickAndSettle(InterfaceID.GravestoneGeneric.PAYBUTTON);

        // The varbit is the authoritative signal: the interface can linger open after the last item is
        // claimed, so closing is not proof the grave was emptied.
        boolean emptied = Global.sleepUntil(() -> !hasGrave(), LOOT_TIMEOUT_MS);
        if (!emptied && Rs2Inventory.isFull()) {
            // Unlike Death's Office, a grave expires — anything still in it when the timer runs out
            // moves on and costs the (usually higher) office fee to get back.
            log.warn("Grave not emptied and the inventory is full — {} free item(s) and {} paid item(s) "
                            + "remain, with {} left on the grave timer",
                    getGraveFreeItems().size(), getGravePaidItems().size(), getGraveTimeRemaining());
        }
        return emptied;
    }

    // endregion

    // region death's office

    public static boolean isDeathsOfficeOpen() {
        return Rs2Widget.isWidgetVisible(InterfaceID.DeathOffice.ITEMS_CONTAINER)
                || Rs2Widget.isWidgetVisible(InterfaceID.GravestoneRetrieval.ITEMS_CONTAINER);
    }

    /**
     * Walks to the nearest Death's Office entrance.
     */
    public static boolean walkToDeathsOffice() {
        DeathsOfficeLocation location = DeathsOfficeLocation.getNearest();
        if (location == null) {
            log.warn("Cannot walk to Death's Office: no reachable entrance found");
            return false;
        }
        log.info("Walking to Death's Office via {}", location);
        return Rs2Walker.walkTo(location.getEntrance(), 6);
    }

    /**
     * @return {@code true} when the player is inside Death's Domain, the instanced room holding Death
     * and the retrieval interface.
     */
    public static boolean isInDeathsOffice() {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location != null && location.getRegionID() == DEATH_DOMAIN_REGION_ID;
    }

    /**
     * Steps through the {@code Death's Domain} object into the office. Death stands inside the instance,
     * so walking to the entrance is not enough on its own — without this the NPC is never in the scene
     * and {@link #openDeathsOffice()} finds nothing.
     */
    public static boolean enterDeathsOffice() {
        if (isInDeathsOffice()) return true;

        Rs2TileObjectModel entrance = Microbot.getRs2TileObjectCache().query()
                .withId(ObjectID1.DEATH_OFFICE_ACCESS_GRAVE)
                .nearest();
        if (entrance == null) {
            log.warn("Cannot enter Death's Office: no Death's Domain object in the loaded scene");
            return false;
        }

        if (!entrance.click(ENTER_DEATHS_DOMAIN_ACTION)) return false;
        return Global.sleepUntil(Rs2Death::isInDeathsOffice, ENTER_TIMEOUT_MS);
    }

    /**
     * Opens the item retrieval interface.
     * <p>
     * Entering Death's Domain auto-walks the player to Death and starts the conversation — verified in
     * game — so this does not click the NPC. It advances the dialogue instead: click through Death's
     * lines, then choose <i>"Yes, have you got anything for me?"</i>. The option is matched on text, not
     * on its list position, because the menu carries a "More options..." entry and can reorder.
     */
    public static boolean openDeathsOffice() {
        if (isDeathsOfficeOpen()) return true;

        if (!isInDeathsOffice()) {
            log.warn("Cannot open Death's Office: not inside Death's Domain — call enterDeathsOffice first");
            return false;
        }

        return Global.sleepUntil(Rs2Death::isDeathsOfficeOpen, Rs2Death::advanceReclaimDialogue,
                INTERFACE_TIMEOUT_MS, 600);
    }

    /**
     * One step of Death's reclaim conversation: clear a "click to continue" line, or pick the reclaim
     * option when the choices are up. Called on a poll until the retrieval interface opens.
     */
    private static void advanceReclaimDialogue() {
        if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
        } else if (Rs2Dialogue.hasSelectAnOption()) {
            Rs2Dialogue.clickOption(DEATH_RECLAIM_OPTION);
        }
    }

    /**
     * The items Death is currently holding. Requires the retrieval interface to be open
     * ({@link #openDeathsOffice()}) — the office cannot be inspected from afar, though walking there and
     * declining costs nothing.
     */
    public static List<Rs2ItemModel> getDeathsOfficeItems() {
        return readItemContainer(activeRetrievalItemsContainer());
    }

    /**
     * The item container of whichever retrieval interface is actually open. {@link #isDeathsOfficeOpen()}
     * accepts either variant, so reading {@code DeathOffice.ITEMS} unconditionally would return an empty
     * list whenever the retrieval-service variant is the one up — making an office that still holds items
     * look empty, both to callers and to {@link #reclaimAll()}'s inventory-full warning.
     */
    @Component
    private static int activeRetrievalItemsContainer() {
        return Rs2Widget.isWidgetVisible(InterfaceID.DeathOffice.ITEMS_CONTAINER)
                ? InterfaceID.DeathOffice.ITEMS
                : InterfaceID.GravestoneRetrieval.ITEMS;
    }

    /**
     * Reclaims only the items matching {@code filter}, leaving the rest with Death — where they keep
     * indefinitely, so anything skipped can be collected later.
     * <p>
     * Each slot is taken in two steps, mirroring the interface: click the item ({@code Select}), then the
     * {@code All} quantity button that appears. Slots are processed highest-index first so taking one
     * cannot shift the indices of those still to come.
     * <p>
     * The fee is charged per item reclaimed, so taking less costs less — unlike the grave, where paying
     * covers the whole paid half at once.
     *
     * @param filter chooses which items to reclaim.
     * @return the number of slots successfully taken.
     */
    public static int reclaimItems(Predicate<Rs2ItemModel> filter) {
        if (!isDeathsOfficeOpen()) return 0;

        // Selective reclaim is DeathOffice-only. isDeathsOfficeOpen also accepts the
        // GravestoneRetrieval variant, but that interface has no per-quantity controls at all — its
        // components are BUTTON / BUTTON_BANK / DISCARD, with no 1/5/X/All — so the select-then-take
        // flow below has nothing to click there. Fail loudly rather than reading the wrong container
        // and silently reporting "took nothing".
        if (!Rs2Widget.isWidgetVisible(InterfaceID.DeathOffice.ITEMS_CONTAINER)) {
            log.warn("Selective reclaim needs the Death's Office interface; the retrieval-service "
                    + "variant has no quantity controls. Use reclaimAll() instead.");
            return 0;
        }

        List<Rs2ItemModel> items = getDeathsOfficeItems();
        int taken = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            Rs2ItemModel item = items.get(i);
            if (filter != null && !filter.test(item)) continue;
            if (Rs2Inventory.isFull()) {
                log.warn("Inventory full after reclaiming {} item(s) — {} left with Death", taken, i + 1);
                break;
            }

            // Step 1: select the slot. Step 2: the quantity buttons only become visible once something
            // is selected, so "All" is clicked after, not before.
            clickItemSlot(InterfaceID.DeathOffice.ITEMS, item, DEATH_OFFICE_SELECT_ACTION);
            if (!Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.DeathOffice.ALL),
                    INTERFACE_TIMEOUT_MS)) {
                log.warn("Quantity buttons did not appear after selecting {} — stopping", item.getName());
                break;
            }
            clickAndSettle(InterfaceID.DeathOffice.ALL);
            taken++;
        }
        return taken;
    }

    /**
     * Reclaims everything Death is holding, into the inventory. Death's Office keeps items
     * indefinitely, so a partial reclaim caused by a full inventory is safe to resume later.
     * <p>
     * <b>There is deliberately no spending limit, because one is not possible.</b> The fee is never on
     * screen before it is charged — verified live, {@code INFO} reads "Select an item to retrieve."
     * whether the office is empty or holding items, the {@code 1}/{@code 5}/{@code X}/{@code All}
     * buttons stay hidden until an item is selected, and {@code Take-All} never selects. Any cap here
     * would be fiction.
     * <p>
     * Calling this authorises an unbounded charge against Death's Coffer, and the bank after that.
     * Death's Office holds items indefinitely, so declining to call it is always a safe alternative.
     *
     * @return {@code true} once the retrieval interface has closed with nothing left to collect.
     */

    public static boolean reclaimAll() {
        if (!isDeathsOfficeOpen()) return false;

        @Component int takeAll = Rs2Widget.isWidgetVisible(InterfaceID.DeathOffice.ITEMS_CONTAINER)
                ? InterfaceID.DeathOffice.TAKEALL
                : InterfaceID.GravestoneRetrieval.BUTTON;

        clickAndSettle(takeAll);
        Global.sleepUntil(() -> !isDeathsOfficeOpen() || Rs2Inventory.isFull(), LOOT_TIMEOUT_MS);

        if (isDeathsOfficeOpen()) {
            // The office holds up to 120 stacks against 28 inventory slots, so a full reclaim can simply
            // not fit. Nothing is lost — Death keeps the remainder indefinitely — but the caller needs to
            // know to bank and come back.
            log.warn("Death's Office still holds {} item(s) — inventory has {} free slot(s). Bank and "
                            + "call reclaimAll() again, or use reclaimItems(filter) to choose.",
                    getDeathsOfficeItems().size(), Rs2Inventory.emptySlotCount());
            return false;
        }
        return true;
    }

    // endregion

    // region orchestration

    /**
     * @return {@code true} when there is a death worth acting on — either a grave still standing or
     * items waiting at Death's Office.
     */
    public static boolean hasDeathToHandle() {
        return hasGrave() || hasGraveExpired();
    }

    /**
     * Recovers from the grave, paying whatever the grave asks. Death's Office is left alone — see
     * {@link #recoverItems(int, boolean)}.
     */
    public static boolean recoverItems() {
        return recoverItems(Integer.MAX_VALUE, false);
    }

    /**
     * Recovers from the grave only. Anything that already expired to Death's Office stays there.
     *
     * @param budget the highest grave fee to pay. {@code 0} takes only the free items;
     *               {@link Integer#MAX_VALUE} pays whatever is asked.
     */
    public static boolean recoverItems(int budget) {
        return recoverItems(budget, false);
    }

    /**
     * Walks to the grave and empties it, and optionally falls back to Death's Office once the grave has
     * expired. Everything lands in the inventory; banking and re-gearing afterwards is left to the
     * caller.
     * <p>
     * Safe to call when nothing has happened — it returns {@code true} immediately.
     *
     * @param budget the highest <b>grave</b> fee to pay. A grave publishes its fee in {@code FEE}, so the
     *               limit is real there. It does not apply to Death's Office, which never shows a cost
     *               before charging it.
     * @param includeDeathsOffice whether to make the trip to Death's Office when the grave has already
     *               expired. Defaults to off in the other overloads, and that default is deliberate: the
     *               office charges an uncapped 5% that cannot be checked beforehand, and it holds items
     *               indefinitely, so leaving them is always safe and always reversible by hand.
     * @return {@code true} when the death was dealt with — including the deliberate choices to leave the
     * paid half of a grave behind, or to leave the office untouched.
     */
    public static boolean recoverItems(int budget, boolean includeDeathsOffice) {
        if (!hasDeathToHandle()) {
            log.debug("No death to handle");
            return true;
        }

        if (!hasGrave() && !includeDeathsOffice) {
            // Clear the record so the caller stops seeing a death it has chosen not to act on; the items
            // keep at Death's Office indefinitely and can be collected by hand whenever.
            log.info("Grave has expired and Death's Office recovery was not requested — leaving the "
                    + "items with Death");
            clearDeathState();
            return true;
        }

        boolean collected = hasGrave()
                ? collectFromGrave(budget)
                : collectFromDeathsOffice();

        if (!collected) {
            log.warn("Could not collect after death");
            return false;
        }

        clearDeathState();
        return true;
    }

    /**
     * Collects the grave. A refused paid half is not a failure: those items keep in Death's Office and
     * the script is expected to carry on, which is the whole point of passing a budget.
     */
    private static boolean collectFromGrave(int budget) {
        if (!walkToGrave()) return false;
        if (!openGrave()) return false;
        if (!lootGraveFreeItems()) return false;

        lootGravePaidItems(budget);

        closeInterfaces();
        return true;
    }

    /**
     * Only reached when the caller explicitly opted in, because this spends an amount that cannot be
     * known in advance.
     */
    private static boolean collectFromDeathsOffice() {
        if (!walkToDeathsOffice()) return false;
        if (!enterDeathsOffice()) return false;
        if (!openDeathsOffice()) return false;

        reclaimAll();

        closeInterfaces();
        return true;
    }

    /**
     * Closes whichever retrieval interface is still up. A refused paid half or a fee over budget leaves
     * it open, and the grave timer stays paused while it is, so it must not be left hanging.
     * <p>
     * Public so a script that opened the office only to inspect what Death is holding can decline and
     * walk away cleanly without reclaiming.
     */
    public static void closeInterfaces() {
        if (isGraveOpen()) {
            Rs2Widget.clickWidget(InterfaceID.GravestoneGeneric.CLOSE);
            Global.sleepUntil(() -> !isGraveOpen(), INTERFACE_TIMEOUT_MS);
        }

        if (isDeathsOfficeOpen()) {
            // DeathOffice exposes no CLOSE component in gameval, but the frame carries a dynamic child
            // with a "Close" action — verified live at 669.1 index 11. Match on the action rather than
            // that index, which is a layout detail that can shift between updates.
            Rs2Widget.findWidgetsWithAction("Close", InterfaceID.DEATH_OFFICE, true);
            if (Global.sleepUntil(() -> !isDeathsOfficeOpen(), INTERFACE_TIMEOUT_MS)) return;

            // Escape only works when the player has the setting enabled, so it is the fallback.
            if (Rs2Settings.isEscCloseInterfaceSettingEnabled()) {
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                Global.sleepUntil(() -> !isDeathsOfficeOpen(), INTERFACE_TIMEOUT_MS);
            } else {
                log.warn("Could not close the Death's Office interface: no Close action hit and "
                        + "esc-close is disabled in game settings");
            }
        }
    }

    // endregion

    private static int parseFee(Widget widget) {
        if (widget == null) return 0;
        String text = Microbot.getClientThread().runOnClientThreadOptional(widget::getText).orElse(null);
        return text == null ? 0 : parseFeeText(text);
    }

    private static int parseFeeText(String text) {
        Matcher matcher = DIGITS.matcher(text);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group().replace(",", ""));
        } catch (NumberFormatException e) {
            log.warn("Could not parse fee from '{}'", text);
            return 0;
        }
    }

    private static void clickAndSettle(@Component int componentId) {
        Rs2Widget.clickWidget(componentId);
        Global.sleepUntilNextTick();
    }
}
