package net.runelite.client.plugins.microbot.util.walker;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.annotations.Component;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.devtools.MovementFlag;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import net.runelite.client.plugins.microbot.util.leaguetransport.SeasonalTransportHandler;
import net.runelite.client.plugins.microbot.util.leaguetransport.SeasonalTransportHandlers;
import net.runelite.client.plugins.microbot.util.logging.Rs2LogRateLimit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.event.Level;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.leaguetransport.LeaguesRegion;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.walker.door.DoorAttemptLedger;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorClassifier;
import net.runelite.client.plugins.microbot.util.walker.door.DoorProbeContext;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorDetection;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorProbe;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorAheadResolver;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorGeometry;
import net.runelite.client.plugins.microbot.util.walker.geometry.WalkerPathGeometry;
import net.runelite.client.plugins.microbot.util.walker.obstacle.MineableResolver;
import net.runelite.client.plugins.microbot.util.walker.obstacle.ObstacleResolution;
import net.runelite.client.plugins.microbot.util.walker.obstacle.PlannedEdge;
import net.runelite.client.plugins.microbot.util.walker.recovery.FrontierDecision;
import net.runelite.client.plugins.microbot.util.walker.recovery.RouteRecovery;
import net.runelite.client.plugins.microbot.util.walker.segment.SegmentGate;
import net.runelite.client.plugins.microbot.util.walker.recovery.TailDecision;
import net.runelite.client.plugins.microbot.util.walker.state.WalkExit;
import net.runelite.client.plugins.microbot.util.walker.state.WalkerRouteState;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorHandler;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2WalkerAwaits;
import net.runelite.client.plugins.microbot.util.walker.door.model.AwaitTicket;
import net.runelite.client.plugins.microbot.util.walker.door.model.DoorResolution;
import net.runelite.client.plugins.microbot.util.walker.banking.Rs2WalkerBankingPlanner;
import net.runelite.client.plugins.microbot.util.walker.awaits.Rs2WalkerRuntimeAwaits;
import net.runelite.client.plugins.microbot.util.walker.puzzles.DraynorBasementSolver;
import net.runelite.client.plugins.microbot.util.walker.stall.Rs2WalkerStallPolicy;
import net.runelite.client.plugins.microbot.util.walker.transport.Rs2WalkerTransportAwaits;
import net.runelite.client.plugins.microbot.util.walker.lifecycle.Rs2WalkerLifecycleRuntime;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import javax.inject.Named;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static net.runelite.client.plugins.microbot.util.Global.*;
import static net.runelite.client.plugins.microbot.util.walker.Rs2Walker.*;
import static net.runelite.client.plugins.microbot.util.walker.Rs2WalkerMovement.*;
import static net.runelite.client.plugins.microbot.util.walker.Rs2WalkerDoors.*;

/**
 * The transport-execution component extracted from {@code Rs2Walker} (Phase E1, 2026-08-13): the
 * per-type transport handlers, the terminal-travel machinery and their private helpers — the
 * dispatcher {@code handleSelectedTransport} and its exclusive call-graph closure, moved verbatim.
 * Shared walker state and helpers remain in {@code Rs2Walker} (same package) and are consumed via
 * static imports; the walker calls back in through the package-private dispatcher.
 */
@lombok.extern.slf4j.Slf4j
final class Rs2WalkerTransports {
    private static final ThreadLocal<Boolean> SERVER_APPROACH_ALLOWED = new ThreadLocal<>();

    private Rs2WalkerTransports() {
    }

    /**
     * Same-plane Chebyshev distance from player to {@code dest} strictly less than {@code maxChebyshevExclusive}.
     * Requires matching {@link WorldPoint#getPlane()} before using {@link WorldPoint#distanceTo2D} — that method only
     * compares X/Y, so same X/Y on different planes still reads as distance {@code 0} without an explicit plane check.
     */
    private static boolean isPlayerWithinChebyshevOf(WorldPoint dest, int maxChebyshevExclusive) {
        if (dest == null) {
            return false;
        }
        WorldPoint pl = Rs2Player.getWorldLocation();
        return pl != null && pl.getPlane() == dest.getPlane()
                && pl.distanceTo2D(dest) < maxChebyshevExclusive;
    }

    /**
     * Same-plane Chebyshev distance {@code <= maxInclusiveChebyshev} (e.g. adjacent transport uses {@code 0} for same tile).
     */
    private static boolean isPlayerWithinChebyshevInclusive(WorldPoint dest, int maxInclusiveChebyshev) {
        if (dest == null) {
            return false;
        }
        WorldPoint pl = Rs2Player.getWorldLocation();
        return pl != null && pl.getPlane() == dest.getPlane()
                && pl.distanceTo2D(dest) <= maxInclusiveChebyshev;
    }






    /**
     * Executes the exact transport retained by the active route through its registered Microbot executor.
     * Candidate discovery must happen through immutable route steps, never by rescanning the mutable
     * transport catalog. The local transport payload is isolated here because POH execution still carries
     * subtype behavior that is not part of the planner-independent edge value.
     */
    static boolean handleSelectedTransport(List<WorldPoint> path,
                                                    int indexOfStartPoint,
                                                    Rs2PathApi.ActiveTransportSelection selection) {
        if (selection == null || !selection.isExecutable()) {
            if (selection != null) {
                WebWalkLog.spWarn("selected transport has no executor | type={} origin={} dest={}",
                        selection.getEdge().getType(),
                        compactWorldPoint(selection.getEdge().getOrigin()),
                        compactWorldPoint(selection.getEdge().getDestination()));
            }
            return false;
        }
        Transport selectedTransport = selection.getLocalExecutionTransport();
        Rs2TerminalTravelMode terminalTravelMode = selection.getEdge().getTerminalTravelMode();
        if (path == null || selectedTransport == null
                || indexOfStartPoint < 0 || indexOfStartPoint >= path.size()) {
            return false;
        }
        if (path != null && indexOfStartPoint >= 0 && indexOfStartPoint < path.size() - 1
                && recentlyOpenedStationaryDoorOnSegment(path.get(indexOfStartPoint), path.get(indexOfStartPoint + 1))) {
            return false;
        }
        if (log.isDebugEnabled()) {
            log.debug("[Walker] handleTransports at {}: exact planned candidate — {} executor={}",
                    path.get(indexOfStartPoint), selectedTransport.getDisplayInfo(), selection.getExecutor());
        }
        // When the player is inside a POH instance, the player's raw world-location plane is
        // the instance-template plane and has no relationship to the POH-transport origin plane.
        // Skip the plane guard in that case so POH transports can actually be considered.
        boolean inPohInstance = Microbot.getClient().getTopLevelWorldView().getScene().isInstance()
                && net.runelite.client.plugins.microbot.shortestpath.PohPanel.getExitPortalTile() != null;

        // Pre-compute path point index map for O(1) lookups instead of repeated O(n) scans
        Map<WorldPoint, Integer> pathFirstIndex = new HashMap<>(path.size());
        for (int idx = 0; idx < path.size(); idx++) {
            pathFirstIndex.putIfAbsent(path.get(idx), idx);
        }

        for (Transport transport : Collections.singletonList(selectedTransport)) {
            Collection<WorldPoint> worldPointCollections;
            //in some cases the getOrigin is null, for teleports that start the player location
            if (transport.getOrigin() == null) {
                worldPointCollections = Collections.singleton(null);
            } else if (inPohInstance && transport.getType() == TransportType.POH) {
                // POH fix: when the player is inside a POH instance, the transport's exit-portal
                // origin is an overworld tile that doesn't map into the player's instance chunks,
                // so toLocalInstance() returns an empty collection and the inner loop never runs.
                // Pass the origin through directly so the per-i dispatch below can execute.
                worldPointCollections = Collections.singleton(transport.getOrigin());
            } else {
                worldPointCollections = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), transport.getOrigin());
            }
            log.debug("[Walker] Considering transport: {} (type={}, origin={}, wpCount={})",
                    transport.getDisplayInfo(), transport.getType(), transport.getOrigin(), worldPointCollections.size());
            originLoop:
            for (WorldPoint origin : worldPointCollections) {
                WorldPoint plOriginLoop = Rs2Player.getWorldLocation();
                if (!inPohInstance && transport.getOrigin() != null && plOriginLoop != null
                        && plOriginLoop.getPlane() != transport.getOrigin().getPlane()) {
                    continue;
                }

                // Hoist path-constant checks out of the inner loop: destination must exist in path
                if (!pathFirstIndex.containsKey(transport.getDestination())) {
                    log.debug("[Walker] skip {}: destination {} not in path", transport.getDisplayInfo(), transport.getDestination());
                    continue;
                }
                // QUETZAL is not {@link TransportType#isTeleport} — without this, stall/off-path recalc can re-open the map and
                // click the same landing repeatedly while already there (no movement → infinite stall loop).
                if (transport.getType() == TransportType.QUETZAL) {
                    if (isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET)) {
                        log.debug("[Walker] skip {}: already within {} tiles of Quetzal destination {}",
                                transport.getDisplayInfo(), OFFSET, transport.getDestination());
                        continue;
                    }
                }
                if (TransportType.isTeleport(transport.getType(), transport.getOrigin())) {
                    if (isPlayerWithinChebyshevOf(transport.getDestination(), TELEPORT_NEAR_SKIP_CHEBYSHEV)) {
                        log.debug("[Walker] skip {}: already near destination", transport.getDisplayInfo());
                        continue;
                    }
                }
                // A crossed edge must never be crossed BACK. After landing on the destination the
                // origin can still sit inside the raw dispatch radius, and the origin-distance
                // check knows nothing about sides: the Kebos shortcut (1389->1391, 2026-08-14
                // 17:00) landed the player on 1391 and the next two passes matched the origin from
                // the far side and re-crossed — in front of other players. Being on the
                // destination SIDE (strictly closer to the destination than the origin, same
                // plane) is proof the edge is behind us — exact tile equality was not enough:
                // 17:31 the player stood ONE TILE off the destination (1391,3310) and the recovery
                // path re-crossed anyway. Strictness keeps staircase chains alive (origin and
                // destination share x,y, both distances 0). A genuine reverse crossing is a
                // DIFFERENT planned edge (origin and destination swapped), which this never skips.
                if (plOriginLoop != null && transport.getOrigin() != null
                        && plOriginLoop.getPlane() == transport.getDestination().getPlane()
                        && plOriginLoop.distanceTo2D(transport.getDestination())
                        < plOriginLoop.distanceTo2D(transport.getOrigin())) {
                    WebWalkLog.spInfo("transport_skip_already_at_destination | name={} origin={} dest={}",
                            transport.getDisplayInfo(),
                            compactWorldPoint(transport.getOrigin()),
                            compactWorldPoint(transport.getDestination()));
                    continue;
                }

                // Pre-compute origin/destination indices once per transport (not per inner iteration)
                int precomputedIndexOfOrigin = -1;
                int precomputedIndexOfDest = -1;
                if (!TransportType.isTeleport(transport.getType(), transport.getOrigin())) {
                    Integer originIdx = pathFirstIndex.get(transport.getOrigin());
                    Integer destIdx = pathFirstIndex.get(transport.getDestination());
                    precomputedIndexOfOrigin = originIdx != null ? originIdx : -1;
                    precomputedIndexOfDest = destIdx != null ? destIdx : -1;
                    if (log.isDebugEnabled()) {
                        log.debug("[Walker] filter4 {}: indexOfOrigin={}, indexOfDestination={}, pathSize={}, originInPath={}, destInPath={}",
                                transport.getDisplayInfo(), precomputedIndexOfOrigin, precomputedIndexOfDest, path.size(),
                                precomputedIndexOfOrigin != -1, precomputedIndexOfDest != -1);
                    }
                    if (precomputedIndexOfDest == -1) continue;
                    if (precomputedIndexOfOrigin == -1) continue;
                    if (precomputedIndexOfDest < precomputedIndexOfOrigin) continue;
                }

                for (int i = indexOfStartPoint; i < path.size(); i++) {
                    WorldPoint plPathLoop = Rs2Player.getWorldLocation();
                    if (plPathLoop == null) {
                        // Cannot verify plane / dispatch — do not burn remaining path indices this tick.
                        break;
                    }
                    if (!inPohInstance && origin != null && origin.getPlane() != plPathLoop.getPlane()) {
                        log.debug("[Walker] skip {} (i={}): plane mismatch", transport.getDisplayInfo(), i);
                        break; // plane won't change across iterations, so break instead of continue
                    }

                    if (i == indexOfStartPoint) {
                        log.debug("[Walker] reached pre-dispatch for {}: i={}, path[i]={}, origin={}, equalsOrigin={}",
                                transport.getDisplayInfo(), i, path.get(i), origin, path.get(i).equals(origin));
                    }

                    if (path.get(i).equals(origin)) {
                        if (selection.getExecutor() == Rs2TransportExecutor.BARROWS_DIG) {
                            WorldPoint digOrigin = transport.getOrigin();
                            WorldPoint playerAtMound = Rs2Player.getWorldLocation();
                            if (digOrigin == null || playerAtMound == null || !playerAtMound.equals(digOrigin)) {
                                // Digging is tile-sensitive. Let the ordinary path click finish the
                                // approach instead of firing the spade from an adjacent mound tile.
                                return false;
                            }
                            boolean dug = attemptObserved(transport,
                                    () -> Rs2Inventory.interact(ItemID.SPADE, "Dig"));
                            if (!dug) {
                                return false;
                            }
                            boolean enteredCrypt = Rs2WalkerRuntimeAwaits.awaitCondition(
                                    () -> isPlayerWithinChebyshevOf(
                                            transport.getDestination(), TRANSPORT_NEAR_LANDING_CHEBYSHEV),
                                    TRANSPORT_LANDING_WAIT_POLL_MS,
                                    TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            if (enteredCrypt) {
                                return finishHandledTransport(transport);
                            }
                            WebWalkLog.spWarn(
                                    "Barrows dig post-travel wait timed out ({}ms) dest={} at={}",
                                    TRANSPORT_LANDING_WAIT_TIMEOUT_MS,
                                    compactWorldPoint(transport.getDestination()),
                                    compactWorldPoint(Rs2Player.getWorldLocation()));
                            return false;
                        }

                        if (isTerminalTravelTransport(transport.getType())) {
                            if (terminalTravelMode == Rs2TerminalTravelMode.UNSUPPORTED) {
                                WebWalkLog.spWarn(
                                        "selected terminal travel has no supported interaction mode | type={} origin={} dest={}",
                                        transport.getType(), compactWorldPoint(transport.getOrigin()),
                                        compactWorldPoint(transport.getDestination()));
                                break originLoop;
                            }

                            // Resolve the SCENE OBJECT before falling back to the NPC lookup.
                            // Rs2Npc#getNpc matches on CONTAINS, so an object terminal whose name is
                            // a substring of some unrelated NPC's name lost this if/else to that NPC,
                            // found none of its actions, and broke the origin loop — abandoning the
                            // walk. Measured at the Tempoross ferry (boats.tsv "Board;Ferry;41311",
                            // loctype tempoross_lobby_boat): the boat sat three tiles away with Board
                            // in its menu and was never clicked.
                            //
                            // The row's id column cannot decide this: it holds an OBJECT id for object
                            // terminals (41311) and an NPC id for NPC terminals (ships.tsv
                            // "Port Piscarilius;Veos;10724"), so it classifies nothing. What does
                            // classify is the scene itself — findTerminalTravelObject demands the
                            // row's EXACT name plus its configured action within 3 tiles of the
                            // origin, which no NPC terminal can satisfy (Veos offers "Talk-to", not
                            // the row's destination label). Object first, NPC otherwise.
                            TileObject declaredTerminalObject = findTerminalTravelObject(transport);
                            Rs2NpcModel npc = declaredTerminalObject != null
                                    ? null
                                    : Rs2Npc.getNpc(transport.getName());
                            boolean npcUsable = npc != null && Rs2Npc.canWalkTo(npc, 20);
                            // Resolve the action up front so an NPC that cannot serve this row falls
                            // THROUGH to the object branch — and, when no object matches either, to
                            // the approach click below — instead of ending the walk. Aborting here is
                            // how the Tempoross ferry died: a contains-matched NPC with no usable
                            // action killed a route the scene could still have travelled.
                            String npcAction = npcUsable
                                    ? resolveTerminalNpcInteractionAction(npc, transport)
                                    : "";
                            if (npcUsable && npcAction.isEmpty()) {
                                WebWalkLog.spInfo(
                                        "terminal NPC has no supported interaction action name={} configured={} dest={} display={} — falling back to the scene object",
                                        transport.getName(), transport.getAction(),
                                        compactWorldPoint(transport.getDestination()),
                                        transport.getDisplayInfo());
                            }
                            if (!npcAction.isEmpty()) {
                                if (!markTerminalTravelAttempt(transport)) {
                                    log.debug("[Walker] terminal travel edge already attempted this walk: {}",
                                            transport.getDisplayInfo());
                                    break originLoop;
                                }
                                if (!npcAction.equalsIgnoreCase(transport.getAction())) {
                                    WebWalkLog.spInfo(
                                            "terminal NPC action fallback name={} configured={} selected={} dest={}",
                                            transport.getName(), transport.getAction(), npcAction,
                                            transport.getDisplayInfo());
                                }

                                // Wrap with observation so Leagues blocked-region chat can attribute this attempt.
                                if (attemptObserved(transport, () -> Rs2Npc.interact(npc, npcAction))) {
                                    Rs2Player.waitForWalking();
                                    sleepUntil(Rs2Dialogue::isInDialogue, 600 * 2);

                                    if (Objects.equals(transport.getName(), "Veos") && Objects.equals(transport.getAction(), "Talk-to")) {
                                        sleepUntil(() -> !Rs2Dialogue.hasContinue(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                        Rs2Dialogue.clickOption("Can you take me somewhere?");
                                        sleepUntil(() -> !Rs2Dialogue.hasContinue() && !Rs2Dialogue.hasSelectAnOption(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                        Rs2Dialogue.clickOption(transport.getDisplayInfo());
                                        sleepUntil(() -> !Rs2Dialogue.hasContinue() && !Rs2Dialogue.hasSelectAnOption(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                    }

                                    if (Objects.equals(transport.getName(), "Captain Magoro") && Objects.equals(transport.getAction(), "Talk-to")) {
                                        sleepUntil(() -> !Rs2Dialogue.hasContinue(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                        Rs2Dialogue.clickOption(transport.getDisplayInfo());
                                        sleepUntil(() -> !Rs2Dialogue.hasContinue() && !Rs2Dialogue.hasSelectAnOption(), Rs2Dialogue::clickContinue, 5000, Rs2Random.between(600, 800));
                                    }

                                    if (Rs2Dialogue.clickOption("I'm just going to Pirates' cove")) {
                                        sleepTickJitter(2);
                                        Rs2Dialogue.clickContinue();
                                    }
                                    // Right-clicking the destination is always preferred and needs no
                                    // dialogue — that is what DIRECT means. But the mode is decided
                                    // statically from a name whitelist, so an NPC whose row names a
                                    // destination it no longer offers (Veos: the row says
                                    // "Port Piscarilius", the game now asks in conversation) resolved
                                    // to DIRECT, skipped destination selection entirely, and left the
                                    // walker staring at the destination menu.
                                    //
                                    // resolveTerminalNpcInteractionAction already told us which action
                                    // the NPC actually offered. If it had to fall back to a generic one
                                    // then the destination was NOT chosen by the click and has to be
                                    // chosen in the dialogue, whatever the static mode says.
                                    Rs2TerminalTravelMode effectiveTravelMode = terminalTravelMode;
                                    if (!npcAction.equalsIgnoreCase(transport.getAction())
                                            && transport.getDisplayInfo() != null
                                            && !transport.getDisplayInfo().isBlank()) {
                                        effectiveTravelMode = Rs2TerminalTravelMode.DIALOGUE_DESTINATION;
                                    }
                                    if (!selectTerminalTravelDialogueDestination(
                                            transport, effectiveTravelMode)) {
                                        break originLoop;
                                    }
                                    final int terminalDestinationIndex = precomputedIndexOfDest;
                                    if (awaitTerminalTravelLanding(
                                            transport, path, terminalDestinationIndex)) {
                                        return finishHandledTransport(transport);
                                    }
                                }
                            } else {
                                TileObject terminalObject = declaredTerminalObject != null
                                        ? declaredTerminalObject
                                        : findTerminalTravelObject(transport);
                                if (terminalObject != null) {
                                    String objectAction = resolveTransportObjectAction(
                                            terminalObject,
                                            Collections.singletonList(transport.getAction()))
                                            .orElse("");
                                    if (objectAction.isEmpty()) {
                                        WebWalkLog.spWarn(
                                            "terminal object has no supported interaction action name={} configured={} dest={} display={}",
                                            transport.getName(), transport.getAction(),
                                            compactWorldPoint(transport.getDestination()),
                                            transport.getDisplayInfo());
                                        break originLoop;
                                    }
                                    if (!markTerminalTravelAttempt(transport)) {
                                        log.debug("[Walker] terminal travel edge already attempted this walk: {}",
                                            transport.getDisplayInfo());
                                        break originLoop;
                                    }
                                    prepareTransportObjectForInteraction(terminalObject);
                                    final TileObject selectedTerminalObject = terminalObject;
                                    if (attemptObserved(transport, () -> Rs2GameObject.interact(
                                        selectedTerminalObject, objectAction))) {
                                        if (!selectTerminalTravelDialogueDestination(
                                                transport, terminalTravelMode)) {
                                            break originLoop;
                                        }
                                        final int terminalDestinationIndex = precomputedIndexOfDest;
                                        if (awaitTerminalTravelLanding(
                                            transport, path, terminalDestinationIndex)) {
                                            return finishHandledTransport(transport);
                                        }
                                    }
                                } else {
                                    WorldPoint originTile = path.get(i);
                                    boolean clicked = Rs2Walker.walkFastCanvas(originTile);
                                    if (!clicked) {
                                        WorldPoint playerLoc = Rs2Player.getWorldLocation();
                                        if (playerLoc != null) {
                                            clicked = walkMiniMapToward(originTile, playerLoc, 13);
                                        }
                                    }
                                    if (!clicked) {
                                        clicked = Rs2Walker.walkMiniMap(originTile);
                                    }
                                    if (!clicked) {
                                        log.debug("[Walker] terminal travel fallback click failed for {}", originTile);
                                    }
                                    sleep(1200, 1600);
                                }
                            }

                            // Terminal travel is terminal for this transport scan. The exact edge can be
                            // clicked at most once in one top-level walk invocation; callers can start
                            // a fresh walk after a surfaced failure, but this invocation never spams the
                            // target for later path indices or another local-instance copy of the origin.
                            break originLoop;
                        }

                        if (transport.getType() == TransportType.CHARTER_SHIP) {
                            if (attemptObserved(transport, () -> handleCharterShip(transport))) {
                                sleepUntil(() -> !Rs2Player.isAnimating());
                                boolean charterLanded = Rs2WalkerRuntimeAwaits.awaitCondition(
                                        () -> isPlayerWithinChebyshevOf(transport.getDestination(), TRANSPORT_NEAR_LANDING_CHEBYSHEV),
                                        TRANSPORT_LANDING_WAIT_POLL_MS,
                                        TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                                if (!charterLanded) {
                                    WebWalkLog.spWarn(
                                            "charter ship post-travel wait timed out ({}ms) dest={} at={}",
                                            TRANSPORT_LANDING_WAIT_TIMEOUT_MS,
                                            compactWorldPoint(transport.getDestination()),
                                            compactWorldPoint(Rs2Player.getWorldLocation()));
                                }
                                sleepTickJitter(4); // wait 4 extra ticks before walking
                                return finishHandledTransport(transport);
                            }
                        }
                    }

                    log.debug("[Walker] Handling {} transport: {} (i={}, path[i]={}, origin={})",
                            transport.getType(), transport.getDisplayInfo(), i, path.get(i), origin);
                    if (transport.getType() == TransportType.POH) {
                        boolean pohResult = attemptObserved(transport, () -> handlePohTransport(transport));
                        log.debug("[Walker] handlePohTransport({}) returned {}", transport.getDisplayInfo(), pohResult);
                        if (pohResult) {
                            // Shares ship/NPC/boat 10s landing budget — intentional single timeout constant.
                            boolean pohNearDest = sleepUntil(
                                    () -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                    SHIP_NPC_BOAT_LANDING_WAIT_MS);
                            if (!pohNearDest) {
                                WebWalkLog.spWarn(
                                        "POH post-travel wait timed out ({}ms) dest={} at={}",
                                        SHIP_NPC_BOAT_LANDING_WAIT_MS,
                                        compactWorldPoint(transport.getDestination()),
                                        compactWorldPoint(Rs2Player.getWorldLocation()));
                            }
                            if (pohNearDest) {
                                return finishHandledTransport(transport);
                            }
                        }
                    }

                    if (transport.getType() == TransportType.CANOE) {
                        if (attemptObserved(transport, () -> handleCanoe(transport))) {
                            sleepTickJitter(2);
                            return finishHandledTransport(transport);
                        }
                    }

					if (transport.getType() == TransportType.HOT_AIR_BALLOON) {
						if (attemptObserved(transport, () -> Rs2HotAirBalloon.handle(selection.getEdge()))) {
                            boolean balloonLanded = Rs2WalkerRuntimeAwaits.awaitCondition(
                                    () -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                    TRANSPORT_LANDING_WAIT_POLL_MS,
                                    TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            if (balloonLanded) {
                                sleepTickJitter(2);
                                return finishHandledTransport(transport);
                            }
                            WebWalkLog.spWarn(
                                    "hot-air balloon post-travel wait timed out ({}ms) dest={} at={}",
                                    TRANSPORT_LANDING_WAIT_TIMEOUT_MS,
                                    compactWorldPoint(transport.getDestination()),
                                    compactWorldPoint(Rs2Player.getWorldLocation()));
                        }
                        // This is a specialized map interaction. Do not fall through to the generic
                        // object handler and click the same basket again during this walker tick.
                        return false;
                    }

                    if (transport.getType() == TransportType.SPIRIT_TREE) {
                        if (!Rs2PathApi.isSpiritTreeTravelEnabled()) {
                            log.debug("[Walker] skip spirit tree transport — setting is off");
                            continue;
                        }
                        if (attemptObserved(transport, () -> handleSpiritTree(transport))) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            boolean spiritLanded = Rs2WalkerRuntimeAwaits.awaitCondition(
                                    () -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                    TRANSPORT_LANDING_WAIT_POLL_MS,
                                    TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            if (!spiritLanded) {
                                WebWalkLog.spWarn(
                                        "spirit tree post-travel wait timed out ({}ms) dest={} at={}",
                                        TRANSPORT_LANDING_WAIT_TIMEOUT_MS,
                                        compactWorldPoint(transport.getDestination()),
                                        compactWorldPoint(Rs2Player.getWorldLocation()));
                            }
                            if (spiritLanded) {
                                return finishHandledTransport(transport);
                            }
                        }
                    }

                    if (transport.getType() == TransportType.QUETZAL) {
                        if (attemptObserved(transport, () -> handleQuetzal(transport))) {
                            boolean landedNearDest = Rs2WalkerRuntimeAwaits.awaitCondition(
                                    () -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                    TRANSPORT_LANDING_WAIT_POLL_MS,
                                    TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            if (!landedNearDest) {
                                WebWalkLog.spWarn(
                                        "quetzal post-travel wait timed out ({}ms) dest={} at={}",
                                        TRANSPORT_LANDING_WAIT_TIMEOUT_MS,
                                        compactWorldPoint(transport.getDestination()),
                                        compactWorldPoint(Rs2Player.getWorldLocation()));
                            }
                            sleepTickJitter(2);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.MAGIC_CARPET) {
                        if (attemptObserved(transport, () -> handleMagicCarpet(transport))) {
                            sleepTickJitter(2);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.WILDERNESS_OBELISK) {
                        if (attemptObserved(transport, () -> handleWildernessObelisk(transport))) {
                            sleepTickJitter(2);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.GNOME_GLIDER) {
                        if (attemptObserved(transport, () -> handleGlider(transport))) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(),
                                            TRANSPORT_NEAR_LANDING_CHEBYSHEV),
                                    TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            sleepTickJitter(3);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.FAIRY_RING) {
                        WorldPoint plFairy = Rs2Player.getWorldLocation();
                        WorldPoint tdFairy = transport.getDestination();
                        boolean alreadyAtFairyDest = plFairy != null && tdFairy != null && plFairy.equals(tdFairy);
                        if (!alreadyAtFairyDest && attemptObserved(transport, () -> handleFairyRing(transport))) {
                            sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                    TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.TELEPORTATION_MINIGAME) {
                        if (attemptObserved(transport, () -> handleMinigameTeleport(transport))) {
                            sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET * 2),
                                    TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.TELEPORTATION_ITEM) {
                        if (attemptObserved(transport, () -> handleTeleportItem(transport))) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                    TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.TELEPORTATION_SPELL) {
                        if (attemptObserved(transport, () -> handleTeleportSpell(transport))) {
                            if (isLumbridgeHomeTeleport(transport)) {
                                sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET), 600, 35000);
                            } else {
                                sleepUntil(() -> !Rs2Player.isAnimating());
                                sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                        TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            }
                            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getType() == TransportType.SEASONAL_TRANSPORT) {
                        if (attemptObservedWithoutAttemptRecord(transport, () -> handleSeasonalTransport(transport))) {
                            sleepUntil(() -> !Rs2Player.isAnimating());
                            sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                                    TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                            return finishHandledTransport(transport);
                        }
                    }

                    if (transport.getObjectId() <= 0) break;

                    final int transportObjectId = transport.getObjectId();
                    final String transportAction = transport.getAction();
                    final List<String> transportActions = getTransportActionOptions(transportAction);
                    // Climb-down transports have a closed-variant (trapdoor/manhole/grate/hatch)
                    // that shares the same tile but a different object ID. Infer the closed
                    // variant from ObjectComposition (any nearby object with an "Open" action
                    // and a matching name) rather than a hardcoded ID pair, so new variants
                    // work without a code change.
                    final boolean allowClosedVariant = "Climb-down".equalsIgnoreCase(transportAction)
                            || "Climb down".equalsIgnoreCase(transportAction);

                    final boolean allowAlKharidTollGateVariant = isAlKharidTollGateObjectId(transportObjectId);
                    // The FIRST transport of a walk costs ~12.7s in the segment handler while the same
                    // transport mid-route costs ~1.8s, and the plane-change waits account for only
                    // ~1.5s of it (measured over three Falador castle runs). This scan runs once per
                    // CANDIDATE transport at the tile, and a staircase tile carries several rows, so
                    // the suspicion is N scans rather than one. Time it and say how many candidates
                    // were queued, so the next run distinguishes "one slow scan" from "many scans".
                    long objectScanStartedAt = System.currentTimeMillis();
                    final Integer legacyClosedId = OPEN_TO_CLOSED_MAPPINGS.get(transportObjectId);
                    // Most catalog transports can use their stable object id. The Al Kharid gate cannot:
                    // its historical catalog ids collide with unrelated live objects in newer injected-client
                    // revisions. Select that edge by its transformed live composition and route geometry instead.
                    // This deliberately has no id fallback: clicking an unrelated object is worse than failing
                    // closed and replanning.
                    List<TileObject> matched;
                    if (allowAlKharidTollGateVariant) {
                        matched = Rs2GameObject.getAll(
                                o -> isAlKharidTollGateSceneCandidate(transport, o),
                                transport.getOrigin(), 3);
                    } else {
                        // Id-only first: these are plain field reads, no composition resolution.
                        matched = Rs2GameObject.getAll(o -> {
                            int id = o.getId();
                            if (id == transportObjectId) return true;
                            return legacyClosedId != null && id == legacyClosedId;
                        }, transport.getOrigin(), 10);
                    }
                    if (matched.isEmpty() && allowClosedVariant) {
                        // Only now pay for compositions, and only on the transport's own tile: a closed
                        // variant (trapdoor/manhole/grate/hatch) sits where the transport is, never ten
                        // tiles away. Previously this ran for EVERY object within 10 tiles whenever the
                        // action was Climb-down, one client-thread hop each — measured at 5.5-10.9
                        // SECONDS for a single scan inside Falador castle, and the reason descending
                        // stairs was slow while ascending was not.
                        matched = Rs2GameObject.getAll(o -> {
                            ObjectComposition comp = Rs2GameObject.convertToObjectComposition(o);
                            if (comp == null || comp.getActions() == null) return false;
                            String nm = comp.getName() == null ? "" : comp.getName().toLowerCase();
                            boolean nameMatches = nm.contains("trapdoor") || nm.contains("manhole")
                                    || nm.contains("grate") || nm.contains("hatch");
                            if (!nameMatches) return false;
                            return Arrays.stream(comp.getActions()).filter(Objects::nonNull)
                                    .anyMatch(a -> a.equalsIgnoreCase("Open"));
                        }, transport.getOrigin(), 2);
                    }
                    List<TileObject> objects = matched.stream()
                            .sorted(Comparator
                                    .comparingInt((TileObject o) -> resolveTransportObjectAction(o, transportActions).isPresent() ? 0 : 1)
                                    .thenComparingInt(o -> o.getWorldLocation().distanceTo(transport.getOrigin())))
                            .collect(Collectors.toList());

                    long objectScanMs = System.currentTimeMillis() - objectScanStartedAt;
                    if (objectScanMs >= TRANSPORT_OBJECT_SCAN_SLOW_MS) {
                        WebWalkLog.spInfo("transport_object_scan | slow scanMs={} objectId={} candidatesAtTile={} matches={} origin={}",
                                objectScanMs, transportObjectId, 1, objects.size(),
                                compactWorldPoint(transport.getOrigin()));
                    }
                    TileObject object = objects.stream().findFirst().orElse(null);
                    if (object instanceof GroundObject) {
                        object = objects.stream()
                                .filter(o -> !Objects.equals(o.getWorldLocation(), Rs2Player.getWorldLocation()))
                                .min(Comparator.comparing(o -> ((TileObject) o).getWorldLocation().distanceTo(transport.getOrigin()))
                                        .thenComparing(o -> ((TileObject) o).getWorldLocation().distanceTo(transport.getDestination()))).orElse(null);
                    }

                    if (object != null) {
                        // Skip reachability check for GroundObjects and Magic Mushtrees
                        if (!(object instanceof GroundObject) && !MagicMushtree.isMagicMushtree(transport.getObjectId())) {
                            if (requiresReachableObjectOrigin(Boolean.TRUE.equals(SERVER_APPROACH_ALLOWED.get()))
                                    && !Rs2Tile.isTileReachable(transport.getOrigin())) {
                                break;
                            }
                        }

                        // Closed variant detection: if the found object doesn't advertise the
                        // transport action but does advertise "Open", open it first and re-find
                        // the now-open object before invoking handleObject.
                        ObjectComposition comp = Rs2GameObject.convertToObjectComposition(object);
                        if (comp != null && comp.getActions() != null) {
                            String[] actions = comp.getActions();
                            boolean hasTransportAction = resolveTransportObjectAction(actions, transportActions).isPresent();
                            boolean hasOpen = Arrays.stream(actions).filter(Objects::nonNull)
                                    .anyMatch(a -> a.equalsIgnoreCase("Open"));
                            if (!hasTransportAction && hasOpen) {
                                log.info("[Walker] Closed transport variant at {} (id={} name={}) — opening before {}",
                                        transport.getOrigin(), object.getId(), comp.getName(), transportAction);
                                final int closedId = object.getId();
                                Rs2GameObject.interact(object, "Open");
                                Rs2Player.waitForAnimation(2000);
                                TileObject reopened = Rs2GameObject.getAll(o -> {
                                    if (o.getId() == closedId) return false;
                                    ObjectComposition c = Rs2GameObject.convertToObjectComposition(o);
                                    if (c == null || c.getActions() == null) return false;
                                    return resolveTransportObjectAction(c.getActions(), transportActions).isPresent();
                                }, transport.getOrigin(), 3).stream()
                                        .min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(transport.getOrigin())))
                                        .orElse(null);
                                if (reopened != null) object = reopened;
                            }
                        }

                        String interactionAction = resolveTransportObjectAction(object, transportActions)
                                .orElse(transportAction);
                        if (!Objects.equals(interactionAction, transportAction)) {
                            log.debug("[Walker] Using object action '{}' for transport action '{}' at {} (id={})",
                                    interactionAction, transportAction, object.getWorldLocation(), object.getId());
                        }
                        prepareTransportObjectForInteraction(object);
                        if (!handleObject(transport, object, interactionAction)) {
                            return false;
                        }
                        sleepUntil(() -> !Rs2Player.isAnimating());
                        WorldPoint destWait = transport.getDestination();
                        int maxInclusive = isAdjacentSamePlaneTransport(transport) ? 0 : OFFSET;
                        if (destWait == null) {
                            return false;
                        }
                        boolean landedAfterObject = waitForPostHandleObjectLanding(transport, destWait, maxInclusive);
                        if (!landedAfterObject) {
                            WorldPoint afterInteraction = Rs2Player.getWorldLocation();
                            // Adjacent same-plane transports demand landing on the EXACT destination
                            // tile (maxInclusive == 0), and agility shortcuts routinely deposit the
                            // player a tile off it — so a crossing can physically succeed while this
                            // check still fails. Suppression previously ran only on the success path,
                            // which left the inverse transport immediately eligible: the walker
                            // crossed, took the same shortcut straight back, and stranded itself. If
                            // we are no longer on the origin we did cross, so suppress both tiles
                            // regardless of the landing verdict. The landing result itself is
                            // unchanged — this still returns false and replans.
                            if (isShortSamePlaneTransport(transport)
                                    && afterInteraction != null
                                    && !afterInteraction.equals(transport.getOrigin())) {
                                markShortSamePlaneTransportHandled(transport, object);
                            }
                            WebWalkLog.spWarn(
                                    "post-handleObject landing unresolved (timeout={}ms) dest={} at={}",
                                    POST_HANDLE_OBJECT_LANDING_WAIT_MS,
                                    compactWorldPoint(destWait),
                                    compactWorldPoint(afterInteraction));
                        }
                        if (landedAfterObject) {
                            markShortSamePlaneTransportHandled(transport, object);
                            return finishHandledTransport(transport);
                        }
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private static boolean waitForPostHandleObjectLanding(Transport transport,
                                                          WorldPoint destWait,
                                                          int maxInclusive) {
        long waitStartedAt = System.currentTimeMillis();
        AtomicBoolean settledAwayFromAdjacentDestination = new AtomicBoolean(false);
        AtomicBoolean settledNearAdjacentDestination = new AtomicBoolean(false);
        boolean completed = sleepUntil(() -> {
            if (isPlayerWithinChebyshevInclusive(destWait, maxInclusive)) {
                return true;
            }
            if (!isAdjacentSamePlaneTransport(transport)
                    || System.currentTimeMillis() - waitStartedAt < POST_HANDLE_OBJECT_FAILED_SETTLE_MS) {
                return false;
            }
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            if (playerLoc == null || destWait == null || playerLoc.getPlane() != destWait.getPlane()
                    || Rs2Player.isMoving() || Rs2Player.isAnimating()) {
                return false;
            }
            if (isSettledNearAdjacentSamePlaneLanding(transport, playerLoc, destWait, maxInclusive)) {
                settledNearAdjacentDestination.set(true);
                return true;
            }
            WorldPoint origin = transport == null ? null : transport.getOrigin();
            boolean settledAwayFromOrigin = origin != null && playerLoc.distanceTo2D(origin) > 1;
            if (playerLoc.distanceTo2D(destWait) > Math.max(1, maxInclusive)
                    && settledAwayFromOrigin) {
                settledAwayFromAdjacentDestination.set(true);
                return true;
            }
            return false;
        }, POST_HANDLE_OBJECT_LANDING_WAIT_MS);

        if (settledNearAdjacentDestination.get()) {
            WebWalkLog.spInfo("post-handleObject adjacent landing accepted | dest={} at={}",
                    compactWorldPoint(destWait), compactWorldPoint(Rs2Player.getWorldLocation()));
            return true;
        }
        if (settledAwayFromAdjacentDestination.get()) {
            WebWalkLog.spInfo("post-handleObject adjacent landing failed | dest={} at={}",
                    compactWorldPoint(destWait), compactWorldPoint(Rs2Player.getWorldLocation()));
            return false;
        }
        return completed;
    }

    static boolean isSettledNearAdjacentSamePlaneLanding(Transport transport,
                                                         WorldPoint playerLoc,
                                                         WorldPoint destWait,
                                                         int maxInclusive) {
        if (!isAdjacentSamePlaneTransport(transport)
                || playerLoc == null
                || destWait == null
                || playerLoc.getPlane() != destWait.getPlane()) {
            return false;
        }
        WorldPoint origin = transport.getOrigin();
        if (origin == null || playerLoc.equals(origin)) {
            return false;
        }
        int destinationDistance = playerLoc.distanceTo2D(destWait);
        if (destinationDistance <= Math.max(1, maxInclusive)
                && playerLoc.distanceTo2D(origin) > 0) {
            return true;
        }
        if (transport.getType() != TransportType.AGILITY_SHORTCUT) {
            return false;
        }

        // Some adjacent shortcut catalogues describe a multi-object animation as one-tile
        // hops. The Falador stepping stones, for example, can carry 3154 -> 3149 while the
        // selected edge says 3154 -> 3153. Accept only a tightly bounded forward, collinear
        // overshoot; sideways movement, reverse movement, and arbitrary teleports still fail.
        int edgeX = destWait.getX() - origin.getX();
        int edgeY = destWait.getY() - origin.getY();
        int movedX = playerLoc.getX() - origin.getX();
        int movedY = playerLoc.getY() - origin.getY();
        int forwardProgress = movedX * edgeX + movedY * edgeY;
        int lateralOffset = Math.abs(movedX * edgeY - movedY * edgeX);
        return forwardProgress > 0
                && forwardProgress <= 6
                && lateralOffset <= 1;
    }

    /**
     * Handles the transportation process specifically for instances of PohTransport.
     * Any Transport param that reaches this is assumed to be a PohTransport.
     *
     * @param transport the transport object to be checked and processed
     * @return true if the transport is an instance of PohTransport and its transport method executes successfully, false otherwise
     */
    private static boolean handlePohTransport(Transport transport) {
        if(!(transport instanceof PohTransport)) {
            throw new IllegalStateException("handlePohTransport should not be called for non-PohTransports");
        }
        return ((PohTransport)transport).execute();
    }

    private static List<String> getTransportActionOptions(String action) {
        if (action == null || action.isBlank()) {
            return Collections.emptyList();
        }

        List<String> actions = new ArrayList<>();
        actions.add(action);
        if ("Bottom-floor".equalsIgnoreCase(action)) {
            actions.add("Climb-down");
            actions.add("Climb down");
        } else if ("Top-floor".equalsIgnoreCase(action)) {
            actions.add("Climb-up");
            actions.add("Climb up");
        }
        return actions;
    }

    private static Optional<String> resolveTransportObjectAction(TileObject object, List<String> actionOptions) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(object);
            if (comp == null || comp.getActions() == null) {
                return Optional.<String>empty();
            }
            return resolveTransportObjectAction(comp.getActions(), actionOptions);
        }).orElse(Optional.empty());
    }

    private static Optional<String> resolveTransportObjectAction(String[] objectActions, List<String> actionOptions) {
        if (objectActions == null || actionOptions == null || actionOptions.isEmpty()) {
            return Optional.empty();
        }

        for (String desired : actionOptions) {
            for (String actual : objectActions) {
                if (actual != null && desired.equalsIgnoreCase(Rs2UiHelper.stripColTags(actual))) {
                    return Optional.of(actual);
                }
            }
        }
        return Optional.empty();
    }

    private static void prepareTransportObjectForInteraction(TileObject tileObject) {
        if (tileObject == null || tileObject.getLocalLocation() == null) {
            return;
        }
        if (!Rs2Camera.isTileOnScreen(tileObject)) {
            Rs2Camera.turnTo(tileObject);
            sleepUntil(() -> Rs2Camera.isTileOnScreen(tileObject), 1200);
        }
    }

    private static boolean handleObject(Transport transport, TileObject tileObject) {
        return handleObject(transport, tileObject, transport.getAction());
    }

    /**
     * A transport may be gated on an item that its own vendor sells on the spot (the Shantay pass
     * pattern: the gate wants a ticket, Shantay sells tickets two tiles away). The catalog rows in
     * {@code purchasable_items.tsv} say which item, which vendor, and how close the vendor must be
     * to the transport origin; the transports.tsv duplicate-row OR (item row + currency-twin row)
     * already made the planner route through such transports for players holding only the coins.
     * This pre-step completes the currency variant: buy the item before interacting. Free rows
     * (e.g. a gate's exit direction) carry neither item nor currency requirements and never match.
     *
     * <p>Vendor interaction is by NPC id — a name lookup once partial-matched the nearer
     * "Shantay Guard" (Actions=[Talk-to, null, Pass]) and the buy silently failed.
     */
    private static void ensureRequiredItemBeforeTransport(Transport transport) {
        PurchasableItemCatalog.PurchasableItem purchasable = PurchasableItemCatalog.forTransport(transport);
        if (purchasable == null || Rs2Inventory.hasItem(purchasable.itemId)) {
            return;
        }
        WebWalkLog.spInfo("purchasable_buy | item={} vendor={} action={} at={}",
                purchasable.itemId, purchasable.vendorNpcId, purchasable.vendorAction,
                compactWorldPoint(Rs2Player.getWorldLocation()));
        if (Rs2Npc.interact(purchasable.vendorNpcId, purchasable.vendorAction)) {
            sleepUntil(() -> Rs2Inventory.hasItem(purchasable.itemId), 4000);
        }
        if (!Rs2Inventory.hasItem(purchasable.itemId)) {
            WebWalkLog.spWarn("purchasable_buy failed | item={} vendor={} action={} — no item acquired",
                    purchasable.itemId, purchasable.vendorNpcId, purchasable.vendorAction);
        }
    }

    private static boolean handleObject(Transport transport, TileObject tileObject, String action) {
        ensureRequiredItemBeforeTransport(transport);
        WorldPoint before = Rs2Player.getWorldLocation();
        if (!Rs2GameObject.interact(tileObject, action)) {
            return false;
        }
        // Unlike the other exception handlers, a toll-gate interaction is not complete merely
        // because the menu action was issued: it may first server-walk from several tiles away and
        // then present a confirmation dialogue. Bubble an unobserved crossing back to the caller so
        // it cannot emit a transport handoff for a player who is still west/east of the gate.
        if (isAlKharidTollGateTransport(transport) && isPayTollAction(transport.getAction())) {
            return handleAlKharidTollGate(transport);
        }
        if (handleObjectExceptions(transport, tileObject)) return true;
        WorldPoint tdObj = transport.getDestination();
        WorldPoint plObj = Rs2Player.getWorldLocation();
        if (tdObj == null || plObj == null) {
            return false;
        }
        if (tdObj.getPlane() == plObj.getPlane()) {
            if (transport.getType() == TransportType.AGILITY_SHORTCUT) {
                Rs2Player.waitForAnimation();
                sleepUntil(() -> {
                    WorldPoint now = Rs2Player.getWorldLocation();
                    return isPlayerWithinChebyshevInclusive(tdObj, 2)
                            || isSettledNearAdjacentSamePlaneLanding(transport, now, tdObj, 0);
                }, 10000);
            } else if (transport.getType() == TransportType.MINECART) {
                if (interactWithAdventureLog(transport)) {
                    sleepTickJitter(2); // wait extra 2 game ticks before moving
                } else {
                    sleepUntil(() -> Rs2Player.getPoseAnimation() == 2148, 5000);
                    sleepUntil(() -> Rs2Player.getPoseAnimation() != 2148, 10000);
                }
            } else if (transport.getType() == TransportType.TELEPORTATION_PORTAL) {
                sleepTickJitter(2); // wait extra 2 game ticks before moving
            } else {
                Rs2Player.waitForWalking();
                Rs2Dialogue.clickOption("Yes please"); //shillo village cart
                if (isAdjacentSamePlaneTransport(transport)) {
                    sleepUntil(() -> {
                        WorldPoint now = Rs2Player.getWorldLocation();
                        return now != null && (now.equals(transport.getDestination())
                                || !now.equals(before)
                                || !Rs2Player.isMoving());
                    }, 2000);
                    WorldPoint afterOpen = Rs2Player.getWorldLocation();
                    if (afterOpen != null && !afterOpen.equals(transport.getDestination())) {
                        boolean clicked = walkMiniMap(transport.getDestination());
                        if (!clicked) {
                            clicked = walkFastCanvas(transport.getDestination());
                        }
                        if (clicked) {
                            sleepUntil(() -> {
                                WorldPoint now = Rs2Player.getWorldLocation();
                                WorldPoint td = transport.getDestination();
                                return now != null && td != null && now.equals(td);
                            }, 3000);
                        }
                    }
                }
            }
            return true;
        } else {
            WorldPoint plZ = Rs2Player.getWorldLocation();
            if (plZ == null) {
                return false;
            }
            int z = plZ.getPlane();
            // Instrumentation: the FIRST plane-change transport of a walk consistently costs ~9.5s
            // while the same kind mid-route costs ~2.2s (measured across two Falador castle runs).
            // The waits below bound at 1800 + 5000 + jitter, and a failed start returns false and is
            // retried, so two attempts would explain it — but that is inference. These timings say
            // which of start-detection, plane-detection or retry actually burns the seconds.
            long planeChangeStartedAt = System.currentTimeMillis();
            boolean started = sleepUntil(() -> {
                WorldPoint p = Rs2Player.getWorldLocation();
                return p != null && (p.getPlane() != z || Rs2Player.isMoving() || Rs2Player.isAnimating());
            }, 1800);
            long startWaitMs = System.currentTimeMillis() - planeChangeStartedAt;
            if (!started) {
                WebWalkLog.spInfo("transport_plane_change | no_start startWaitMs={} obj={} action={} — returning for retry",
                        startWaitMs, tileObject.getId(), transport.getAction());
                return false;
            }
            WorldPoint plAfterStart = Rs2Player.getWorldLocation();
            boolean planeChanged = plAfterStart != null && plAfterStart.getPlane() != z
                    || sleepUntil(() -> {
                        WorldPoint p = Rs2Player.getWorldLocation();
                        return p != null && p.getPlane() != z;
                    }, 5000);
            long planeWaitMs = System.currentTimeMillis() - planeChangeStartedAt - startWaitMs;
            if (planeChanged) {
                // gaussRand is an unbounded Box-Muller draw, so mean 300 / dev 120 goes negative past
                // ~2.5 sigma (about one call in 160) and Thread.sleep throws IllegalArgumentException,
                // killing the whole walk. Seen live: "timeout value is negative" here aborted a
                // Falador castle run into ShortestPathScript auto-retry 1/3. Clamping only removes the
                // impossible tail — the jitter this sleep exists to provide is untouched.
                sleep(Math.max(MIN_PLANE_CHANGE_SETTLE_MS, (int) Rs2Random.gaussRand(300.0, 120.0)));
            }
            WebWalkLog.spInfo("transport_plane_change | changed={} startWaitMs={} planeWaitMs={} totalMs={} obj={}",
                    planeChanged, startWaitMs, planeWaitMs,
                    System.currentTimeMillis() - planeChangeStartedAt, tileObject.getId());
            return planeChanged;
        }
    }

    /**
     * Executes the normal transport dispatcher while allowing a route-ordered object click to let
     * the game server choose its approach tile. The scoped thread-local preserves the established
     * dispatcher signature (and its audited client-thread boundary) without leaking policy between
     * concurrent walker tasks.
     */
    static boolean handleSelectedTransport(List<WorldPoint> path,
                                            int indexOfStartPoint,
                                            Rs2PathApi.ActiveTransportSelection selection,
                                            boolean allowServerApproach) {
        Boolean previous = SERVER_APPROACH_ALLOWED.get();
        SERVER_APPROACH_ALLOWED.set(allowServerApproach);
        try {
            return handleSelectedTransport(path, indexOfStartPoint, selection);
        } finally {
            if (Boolean.TRUE.equals(previous)) {
                SERVER_APPROACH_ALLOWED.set(Boolean.TRUE);
            } else {
                SERVER_APPROACH_ALLOWED.remove();
            }
        }
    }

    /**
     * A route-ordered ranged object interaction deliberately delegates the approach tile to the
     * server. Requiring the catalog origin to be locally reachable here reintroduces the old
     * walk-beside-it behavior and disproportionately rejects closed doors, whose origin is often
     * excluded by live collision until the interaction occurs.
     */
    static boolean requiresReachableObjectOrigin(boolean allowServerApproach) {
        return !allowServerApproach;
    }

    private static boolean finishHandledTransport(Transport transport) {
        long handoffStartedAt = System.currentTimeMillis();
        routeState.lastTransportHandledAtMs = handoffStartedAt;
        routeState.lastTransportOriginLocation = transport != null ? transport.getOrigin() : null;
        routeState.lastTransportDestinationLocation = transport != null ? transport.getDestination() : null;
        WorldPoint goal = currentTarget;
        WorldPoint transportDest = transport != null ? transport.getDestination() : null;
        boolean expectedTransport = consumeExpectedTransportDestination(transportDest);
        boolean hasPrecomputedContinuation = hasPrecomputedContinuationFromTransport(transport);
        if (goal != null) {
            WebWalkLog.tmark("transport_handoff_enter",
                    0L,
                    goal,
                    Rs2Player.getWorldLocation(),
                    "dest=" + compactWorldPoint(transportDest)
                            + " expected=" + expectedTransport
                            + " precomputed=" + hasPrecomputedContinuation
                            + " type=" + (transport != null ? transport.getType() : "null"));
        }
        if ((expectedTransport || hasPrecomputedContinuation) && goal != null) {
            WebWalkLog.tmark(expectedTransport ? "transport_handoff_expected_hit" : "transport_handoff_precomputed_hit",
                    System.currentTimeMillis() - handoffStartedAt,
                    goal,
                    Rs2Player.getWorldLocation(),
                    "dest=" + compactWorldPoint(transportDest));
            return true;
        }
        if (goal != null && transportDest != null) {
            // Destination-aware handoff: prepare next path from known landing tile.
            boolean queued = restartPathfinding(transportDest, goal);
            WebWalkLog.tmark("transport_handoff_restart",
                    System.currentTimeMillis() - handoffStartedAt,
                    goal,
                    Rs2Player.getWorldLocation(),
                    "queued=" + queued + " dest=" + compactWorldPoint(transportDest));
            if (!queued && shouldRecalculatePathAfterTransport(transport)) {
                recalculatePath();
                WebWalkLog.tmark("transport_handoff_recalc_fallback",
                        System.currentTimeMillis() - handoffStartedAt,
                        goal,
                        Rs2Player.getWorldLocation(),
                        "dest=" + compactWorldPoint(transportDest));
            }
        } else if (goal != null && shouldRecalculatePathAfterTransport(transport)) {
            recalculatePath();
            WebWalkLog.tmark("transport_handoff_recalc_goal_only",
                    System.currentTimeMillis() - handoffStartedAt,
                    goal,
                    Rs2Player.getWorldLocation(),
                    "dest=" + compactWorldPoint(transportDest));
        }
        return true;
    }

    private static boolean consumeExpectedTransportDestination(WorldPoint destination) {
        if (destination == null) {
            return false;
        }
        synchronized (expectedTransportDestinations) {
            while (!expectedTransportDestinations.isEmpty()) {
                WorldPoint expected = expectedTransportDestinations.peekFirst();
                if (expected == null) {
                    expectedTransportDestinations.pollFirst();
                    continue;
                }
                if (sameOrNearTransportDestination(expected, destination)) {
                    expectedTransportDestinations.pollFirst();
                    return true;
                }
                break;
            }
            return false;
        }
    }

    private static boolean sameOrNearTransportDestination(WorldPoint a, WorldPoint b) {
        return a != null
                && b != null
                && a.getPlane() == b.getPlane()
                && a.distanceTo2D(b) <= TRANSPORT_DEST_MATCH_CHEBYSHEV;
    }

    private static boolean hasPrecomputedContinuationFromTransport(Transport transport) {
        if (transport == null || transport.getDestination() == null) {
            return false;
        }
        Rs2ActiveRouteStatus routeStatus = Rs2PathApi.getActiveRouteStatus();
        if (!routeStatus.isReady()) {
            return false;
        }
        List<WorldPoint> walkPath = routeStatus.getWalkablePath();
        if (walkPath == null || walkPath.size() < 2) {
            return false;
        }
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        int closest = getClosestTileIndex(walkPath, playerLoc);
        if (closest < 0) {
            return false;
        }
        WorldPoint destination = transport.getDestination();
        for (int i = Math.max(0, closest - 2); i < walkPath.size(); i++) {
            WorldPoint point = walkPath.get(i);
            if (sameOrNearTransportDestination(point, destination)) {
                return i < walkPath.size() - 1;
            }
        }
        return false;
    }

    static boolean shouldRecalculatePathAfterTransport(Transport transport) {
        if (transport == null || transport.getDestination() == null) {
            return false;
        }
        if (TransportType.isTeleport(transport.getType())) {
            return true;
        }
        if (transport.getOrigin() == null) {
            return false;
        }
        return transport.getOrigin().getPlane() != transport.getDestination().getPlane()
                || transport.getOrigin().distanceTo2D(transport.getDestination()) > OFFSET;
    }

    private static void markShortSamePlaneTransportHandled(Transport transport, TileObject tileObject) {
        for (WorldPoint point : adjacentSamePlaneTransportSuppressionPoints(transport, tileObject)) {
            markStationaryDoorOpened(point);
        }
    }

    /**
     * Tiles whose inverse transport must be suppressed immediately after a short same-plane hop.
     * Door catalog edges commonly span two tiles (near-side origin to far-side destination), so an
     * adjacency-only check leaves their reverse row eligible and lets a later handler walk back.
     */
    static Set<WorldPoint> adjacentSamePlaneTransportSuppressionPoints(Transport transport, TileObject tileObject) {
        if (!isShortSamePlaneTransport(transport)) {
            return Collections.emptySet();
        }

        Set<WorldPoint> points = new LinkedHashSet<>();
        points.add(transport.getOrigin());
        points.add(transport.getDestination());
        if (tileObject != null && tileObject.getWorldLocation() != null) {
            points.add(tileObject.getWorldLocation());
        }
        return points;
    }

    static boolean isShortSamePlaneTransport(Transport transport) {
        return transport != null
                && transport.getOrigin() != null
                && transport.getDestination() != null
                && transport.getOrigin().getPlane() == transport.getDestination().getPlane()
                && transport.getOrigin().distanceTo2D(transport.getDestination()) <= 2;
    }

    static boolean isTerminalTravelTransport(TransportType transportType) {
        return transportType == TransportType.SHIP
                || transportType == TransportType.NPC
                || transportType == TransportType.BOAT;
    }

    private static boolean selectTerminalTravelDialogueDestination(
            Transport transport, Rs2TerminalTravelMode mode) {
        if (mode == Rs2TerminalTravelMode.DIRECT) {
            return true;
        }
        if (mode != Rs2TerminalTravelMode.DIALOGUE_DESTINATION
                || transport == null
                || transport.getDisplayInfo() == null
                || transport.getDisplayInfo().isBlank()) {
            return false;
        }
        // Several single-destination NPCs (Mountain Guide) travel the player DIRECTLY off the
        // initial click — no destination dialogue ever appears. This wait then burned its whole
        // budget standing at the destination (Auburn Valley 2026-08-14: 7.7s, resolved=false,
        // rescued only because the next pass found the player already across). Release on the
        // evidence that settles the question either way: the dialogue, or the landing itself.
        // The budget must OUTLAST the longest direct flight: the quetzal legs run 7-8s from click
        // to landing, so a 5s wait always lost the race and warned right at touchdown (18:00 and
        // 18:28 runs). Both releases fire early, so a long budget costs nothing when things work —
        // the timeout is reached only when neither dialogue nor landing ever happened.
        final WorldPoint dialogueWaitStart = Rs2Player.getWorldLocation();
        final WorldPoint terminalDest = transport.getDestination();
        if (!sleepUntil(() -> Rs2Dialogue.hasSelectAnOption()
                || hasLandedAtTerminalDestination(Rs2Player.getWorldLocation(), dialogueWaitStart, terminalDest),
                12_000)) {
            WebWalkLog.spWarn(
                    "terminal travel destination dialogue did not appear name={} dest={}",
                    transport.getName(), transport.getDisplayInfo());
            return false;
        }
        if (!Rs2Dialogue.hasSelectAnOption()
                && hasLandedAtTerminalDestination(Rs2Player.getWorldLocation(), dialogueWaitStart, terminalDest)) {
            WebWalkLog.spInfo("terminal travel landed without destination dialogue name={} dest={}",
                    transport.getName(), transport.getDisplayInfo());
            return true;
        }
        if (Rs2Dialogue.clickOption(transport.getDisplayInfo())) {
            return true;
        }
        // The destination is not in THIS menu. Several ferrymen answer a "can you take me somewhere"
        // option with the destination list, so open it and look again rather than giving up — the
        // walker previously stopped here with the destination menu on screen and walked away.
        for (String opener : TERMINAL_TRAVEL_MENU_OPENERS) {
            if (!Rs2Dialogue.hasSelectAnOption() || !Rs2Dialogue.clickOption(opener)) {
                continue;
            }
            WebWalkLog.spInfo("terminal travel menu opened via '{}' name={} dest={}",
                    opener, transport.getName(), transport.getDisplayInfo());
            sleepUntil(Rs2Dialogue::hasSelectAnOption, 5000);
            if (Rs2Dialogue.clickOption(transport.getDisplayInfo())) {
                return true;
            }
        }
        WebWalkLog.spWarn(
                "terminal travel destination option missing name={} dest={}",
                transport.getName(), transport.getDisplayInfo());
        return false;
    }

    /**
     * Whether the player is standing at a terminal-travel destination they were not standing at
     * when the dialogue wait began. The moved-CLOSER requirement is what keeps a short crossing
     * honest: standing beside the ferryman at an origin that happens to sit near the destination
     * proves nothing — only having closed distance on the destination does. Plane must match; the
     * 5-tile allowance covers the landing AREA around the configured tile (the Mountain Guide
     * dropped the player at 1365,3309 for a 1361,3309 destination, 2026-08-14 17:30, and the old
     * 2-tile radius burned the whole dialogue wait standing there).
     */
    static boolean hasLandedAtTerminalDestination(WorldPoint player, WorldPoint waitStart,
                                                  WorldPoint dest) {
        return player != null && dest != null
                && player.getPlane() == dest.getPlane()
                && player.distanceTo2D(dest) <= 5
                && !player.equals(waitStart)
                && (waitStart == null
                || player.distanceTo2D(dest) < waitStart.distanceTo2D(dest));
    }

    private static TileObject findTerminalTravelObject(Transport transport) {
        if (transport == null || transport.getOrigin() == null) {
            return null;
        }
        TileObject object = Rs2GameObject.getAll(
                candidate -> isTerminalTravelObjectSceneCandidate(transport, candidate),
                transport.getOrigin(), 3).stream().findFirst().orElse(null);
        if (object != null) {
            WebWalkLog.spInfo(
                    "terminal travel object selected type={} name={} action={} origin={} dest={}",
                    transport.getType(), transport.getName(), transport.getAction(),
                    compactWorldPoint(transport.getOrigin()),
                    compactWorldPoint(transport.getDestination()));
        }
        return object;
    }

    private static boolean isTerminalTravelObjectSceneCandidate(Transport transport,
                                                                 TileObject object) {
        if (object == null) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ObjectComposition composition = Rs2DoorDetection.resolveCompositionForDoorProbe(object);
            return composition != null
                    && isTerminalTravelObjectCompositionCandidate(
                    transport,
                    object.getWorldLocation(),
                    composition.getName(),
                    composition.getActions());
        }).orElse(false);
    }

    static boolean isTerminalTravelObjectCompositionCandidate(Transport transport,
                                                               WorldPoint objectLocation,
                                                               String objectName,
                                                               String[] objectActions) {
        if (transport == null
                || !isTerminalTravelTransport(transport.getType())
                || transport.getOrigin() == null
                || objectLocation == null
                || objectName == null
                || transport.getName() == null
                || transport.getAction() == null
                || objectLocation.getPlane() != transport.getOrigin().getPlane()
                || objectLocation.distanceTo2D(transport.getOrigin()) > 3
                || !Rs2UiHelper.stripColTags(objectName).trim().equalsIgnoreCase(
                Rs2UiHelper.stripColTags(transport.getName()).trim())) {
            return false;
        }
        return resolveTransportObjectAction(
                objectActions,
                Collections.singletonList(transport.getAction())).isPresent();
    }

    private static boolean awaitTerminalTravelLanding(Transport transport,
                                                      List<WorldPoint> path,
                                                      int destinationIndex) {
        boolean landed = sleepUntil(
                () -> hasReachedTerminalTravelLanding(
                        transport, path, destinationIndex, Rs2Player.getWorldLocation()),
                SHIP_NPC_BOAT_LANDING_WAIT_MS);
        if (!landed) {
            WebWalkLog.spWarn(
                    "ship/npc/boat post-travel wait timed out ({}ms) dest={} at={}",
                    SHIP_NPC_BOAT_LANDING_WAIT_MS,
                    compactWorldPoint(transport.getDestination()),
                    compactWorldPoint(Rs2Player.getWorldLocation()));
        }
        return landed;
    }

    /**
     * Returns interaction actions in executor preference order. Some legacy ship rows encode their
     * destination label as the direct NPC menu action. The current Port Sarim NPCs instead expose
     * {@code Travel}; keep the configured label first for compatible clients, then use that observed
     * live fallback. Explicit dialogue and quick-travel actions must never be replaced implicitly.
     */
    static List<String> terminalNpcInteractionCandidates(TransportType transportType,
                                                         String configuredAction) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (configuredAction != null && !configuredAction.isBlank()) {
            candidates.add(configuredAction);
        }
        if (transportType == TransportType.SHIP
                && !isExplicitShipMenuAction(configuredAction)) {
            candidates.add("Travel");
        }
        return List.copyOf(candidates);
    }

    private static boolean isExplicitShipMenuAction(String action) {
        return action != null
                && (action.equalsIgnoreCase("Travel")
                || action.equalsIgnoreCase("Talk-to")
                || action.equalsIgnoreCase("Quick-Travel")
                || action.equalsIgnoreCase("Take-boat"));
    }

    private static String resolveTerminalNpcInteractionAction(Rs2NpcModel npc, Transport transport) {
        if (npc == null || transport == null) {
            return "";
        }
        for (String candidate : terminalNpcInteractionCandidates(
                transport.getType(), transport.getAction())) {
            // Query one candidate at a time: Rs2Npc#getAvailableAction otherwise returns NPC-menu
            // order, which commonly places Talk-to before the exact configured action.
            String available = Rs2Npc.getAvailableAction(npc, Collections.singletonList(candidate));
            if (!available.isEmpty()) {
                return available;
            }
        }
        return "";
    }

    static boolean markTerminalTravelAttempt(Transport transport) {
        if (transport == null || transport.getOrigin() == null || transport.getDestination() == null) {
            return false;
        }
        String key = transport.getType()
                + "|" + rangedTransportEdgeKey(transport.getOrigin(), transport.getDestination())
                + "|" + transport.getObjectId()
                + "|" + Objects.toString(transport.getName(), "")
                + "|" + Objects.toString(transport.getAction(), "");
        return TERMINAL_TRAVEL_ATTEMPTED_EDGES.add(key);
    }

    /**
     * Accepts the exact catalogued landing or the immediately following path point. The latter covers
     * modern ship travel that skips an obsolete deck tile and completes the next gangplank step in one
     * server action. It deliberately does not scan arbitrary later route points, which could report a
     * false landing when a route loops near its origin.
     */
    static boolean hasReachedTerminalTravelLanding(Transport transport,
                                                   List<WorldPoint> path,
                                                   int destinationIndex,
                                                   WorldPoint playerLocation) {
        if (transport == null || playerLocation == null || transport.getDestination() == null) {
            return false;
        }
        WorldPoint origin = transport.getOrigin();
        if (origin != null
                && origin.getPlane() == playerLocation.getPlane()
                && origin.distanceTo2D(playerLocation) <= 1) {
            return false;
        }
        if (isNearSamePlane(playerLocation, transport.getDestination(),
                TRANSPORT_NEAR_LANDING_CHEBYSHEV)) {
            return true;
        }
        if (path == null || destinationIndex < 0 || destinationIndex + 1 >= path.size()) {
            return false;
        }
        WorldPoint immediateContinuation = path.get(destinationIndex + 1);
        return immediateContinuation != null
                && !immediateContinuation.equals(transport.getDestination())
                && isNearSamePlane(playerLocation, immediateContinuation,
                TRANSPORT_NEAR_LANDING_CHEBYSHEV);
    }

    private static boolean isAlKharidTollGateTransport(Transport transport) {
        return transport != null
                && isAlKharidTollGateObjectId(transport.getObjectId())
                && AL_KHARID_TOLL_GATE_POINTS.contains(transport.getOrigin())
                && AL_KHARID_TOLL_GATE_POINTS.contains(transport.getDestination());
    }

    private static boolean isAlKharidTollGateObjectId(int objectId) {
        return AL_KHARID_TOLL_GATE_OBJECT_IDS.contains(objectId);
    }

    private static boolean isPayTollAction(String action) {
        return action != null && action.toLowerCase(Locale.ROOT).startsWith("pay-toll");
    }

    private static boolean isAlKharidTollGateSceneCandidate(Transport transport, TileObject object) {
        if (!(object instanceof WallObject) && !(object instanceof GameObject)) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ObjectComposition comp = Rs2DoorDetection.resolveCompositionForDoorProbe(object);
            return comp != null
                    && isAlKharidTollGateCompositionCandidate(
                    transport, object.getWorldLocation(), comp.getName(), comp.getActions())
                    && Rs2DoorGeometry.isDoorOnSegment(
                    object, transport.getOrigin(), transport.getDestination());
        }).orElse(false);
    }

    static boolean isAlKharidTollGateCompositionCandidate(Transport transport,
                                                           WorldPoint objectLocation,
                                                           String objectName,
                                                           String[] objectActions) {
        if (!isAlKharidTollGateTransport(transport)
                || objectLocation == null
                || !AL_KHARID_TOLL_GATE_POINTS.contains(objectLocation)
                || objectName == null
                || !objectName.toLowerCase(Locale.ROOT).contains("gate")) {
            return false;
        }
        return resolveTransportObjectAction(
                objectActions, getTransportActionOptions(transport.getAction())).isPresent();
    }

    static boolean hasReachedAlKharidTollDestination(Transport transport, WorldPoint playerLocation) {
        return isAlKharidTollGateTransport(transport)
                && playerLocation != null
                && playerLocation.equals(transport.getDestination());
    }

    private static boolean handleAlKharidTollGate(Transport transport) {
        // Object interaction can begin out of range. Wait for server-walking, the confirmation
        // dialogue, or the crossing itself instead of sampling isMoving() immediately after click.
        sleepUntil(() -> Rs2Player.isMoving()
                        || Rs2Dialogue.hasSelectAnOption()
                        || hasReachedAlKharidTollDestination(transport, Rs2Player.getWorldLocation()),
                AL_KHARID_TOLL_INTERACTION_START_WAIT_MS);

        if (Rs2Player.isMoving()
                && !hasReachedAlKharidTollDestination(transport, Rs2Player.getWorldLocation())) {
            Rs2Player.waitForWalking();
        }

        boolean confirmed = false;
        if (!hasReachedAlKharidTollDestination(transport, Rs2Player.getWorldLocation())
                && (Rs2Dialogue.hasSelectAnOption()
                || sleepUntil(Rs2Dialogue::hasSelectAnOption,
                AL_KHARID_TOLL_INTERACTION_START_WAIT_MS))) {
            confirmed = Rs2Dialogue.clickOption("Yes, okay", "Yes");
        }

        boolean reachedDestination = hasReachedAlKharidTollDestination(
                transport, Rs2Player.getWorldLocation())
                || sleepUntil(() -> hasReachedAlKharidTollDestination(
                        transport, Rs2Player.getWorldLocation()),
                POST_HANDLE_OBJECT_LANDING_WAIT_MS);
        if (!reachedDestination) {
            WebWalkLog.spWarn(
                    "Al Kharid toll gate crossing unresolved confirmed={} dest={} at={}",
                    confirmed,
                    compactWorldPoint(transport.getDestination()),
                    compactWorldPoint(Rs2Player.getWorldLocation()));
        }
        return reachedDestination;
    }

    private static boolean handleObjectExceptions(Transport transport, TileObject tileObject) {
        for (Map.Entry<Integer, Integer> entry : OPEN_TO_CLOSED_MAPPINGS.entrySet()) {
            final int closedTrapdoorId = entry.getKey();
            final int openTrapdoorId = entry.getValue();

            if (transport.getObjectId() == openTrapdoorId) {
                if (tileObject.getId() == closedTrapdoorId) {
                    Rs2GameObject.interact(tileObject, "Open");
                    sleepUntil(() -> Rs2GameObject.exists(openTrapdoorId));
                    TileObject openTrapdoor = Rs2GameObject.getAll(o -> o.getId() == openTrapdoorId, tileObject.getWorldLocation(), 10).stream().findFirst().orElse(null);
                    if (openTrapdoor != null) {
                        Rs2GameObject.interact(openTrapdoor, transport.getAction());
                    }
                } else if (tileObject.getId() == openTrapdoorId) {
                    Rs2GameObject.interact(tileObject, transport.getAction());
                }
                sleepUntil(() -> !Rs2Player.isAnimating());
                boolean trapdoorLanded = sleepUntilTrue(
                        () -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET),
                        TRANSPORT_LANDING_WAIT_POLL_MS, TRANSPORT_LANDING_WAIT_TIMEOUT_MS);
                if (!trapdoorLanded) {
                    WebWalkLog.spWarn(
                            "trapdoor post-travel wait timed out ({}ms) dest={} at={}",
                            TRANSPORT_LANDING_WAIT_TIMEOUT_MS,
                            compactWorldPoint(transport.getDestination()),
                            compactWorldPoint(Rs2Player.getWorldLocation()));
                }
                return true;
            }
        }

        //Al kharid broken wall will animate once and then stop and then animate again
        if (tileObject.getId() == ObjectID.KHARID_POSHWALL_TOPLESS || tileObject.getId() == ObjectID.KHARID_BIGWINDOW) {
            Rs2Player.waitForAnimation();
            Rs2Player.waitForAnimation();
            return true;
        }
        // Handle Leaves Traps in Isafdar Forest
        if (tileObject.getId() == ObjectID.REGICIDE_PITFALL_SIDE) {
            Rs2Player.waitForAnimation(1200);
            if (Rs2Player.getWorldLocation().getY() > 6400) {
                Rs2GameObject.interact(ObjectID.REGICIDE_TRAP_HAND_HOLDS);
                sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 6400);
            } else {
                sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating());
            }
            return true;
        }
        // Handle Ferox Encalve Barrier
        if (tileObject.getId() == ObjectID.WILDY_HUB_ENTRY_BARRIER || tileObject.getId() == ObjectID.WILDY_HUB_ENTRY_BARRIER_M) {
            if (Rs2Dialogue.isInDialogue()) {
                if (Rs2Dialogue.getDialogueText().toLowerCase().contains("when returning to the enclave")) {
                    Rs2Dialogue.clickContinue();
                    Rs2Dialogue.sleepUntilSelectAnOption();
                    Rs2Dialogue.keyPressForDialogueOption("Yes, and don't ask again.");
                    Rs2Dialogue.sleepUntilNotInDialogue();
                    return true;
                }
            }
        }
        // Handle Cobwebs blocking path
        if (tileObject.getId() == ObjectID.BIGWEB_SLASHABLE && !Rs2Equipment.isWearing(ItemID.ARANEA_BOOTS)) {
            sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(1200));
            final WorldPoint webLocation = tileObject.getWorldLocation();
            final WorldPoint currentPlayerPoint = Rs2Player.getWorldLocation();
            boolean doesWebStillExist = Rs2GameObject.getAll(o -> Objects.equals(webLocation, o.getWorldLocation()) && o.getId() == ObjectID.BIGWEB_SLASHABLE).stream().findFirst().isPresent();
            if (doesWebStillExist) {
                sleepUntil(() -> Rs2GameObject.getAll(o -> Objects.equals(webLocation, o.getWorldLocation()) && o.getId() == ObjectID.BIGWEB_SLASHABLE).stream().findFirst().isEmpty(),
                        () -> {
                            Rs2GameObject.interact(tileObject, "slash");
                            Rs2Player.waitForAnimation();
                        }, 8000, 1200);
            }
            Rs2Walker.walkFastCanvas(transport.getDestination());
            return sleepUntil(() -> !Objects.equals(currentPlayerPoint, Rs2Player.getWorldLocation()));
        }

        // Handle Brimhaven Dungeon Entrance
        if (tileObject.getId() == 20877) {
            if (Rs2Player.isMoving()) {
                Rs2Player.waitForWalking();
            }
            Rs2Dialogue.sleepUntilHasQuestion("Pay 875 coins to enter?");
            Rs2Dialogue.clickOption("Yes");
            sleepUntil(() -> {
                WorldPoint now = Rs2Player.getWorldLocation();
                WorldPoint td = transport.getDestination();
                return now != null && td != null && now.equals(td);
            });
            return true;
        }
        // Handle Brimhaven Dungeon Stepping Stones
        if (tileObject.getId() == ObjectID.KARAM_DUNGEON_STONE1 || tileObject.getId() == ObjectID.KARAM_DUNGEON_STONE2) {
            Rs2Player.waitForAnimation(600 * 7);
            return true;
        }

        // Handle Morte Myre Cave Agility Shortcut
        if (tileObject.getId() == ObjectID.FAIRY2_ROUTE_CAVEWALLTUNNEL) {
            Rs2Player.waitForAnimation((600 * 4 ) + 300);
            return true;
        }

        // Handle Crash Site Cavern Gate
        if (tileObject.getId() == 28807 && transport.getOrigin().equals(new WorldPoint(2435,3519, 0))) {
            if (Rs2Player.isMoving()) {
                Rs2Player.waitForWalking();
            }
            Rs2Dialogue.sleepUntilInDialogue();
            Rs2Dialogue.clickOption("yes");
            return true;
        }

        // Handle Cave Entrance inside of Asgarnia Ice Caves
        if (tileObject.getId() == ObjectID.CAVEWALL_SHORTCUT_ROYAL_TITANS_EAST || tileObject.getId() == ObjectID.CAVEWALL_SHORTCUT_ROYAL_TITANS_WEST) {
            Rs2Player.waitForAnimation();
        }

        // Handle Rev Cave Dialogue
        if (tileObject.getId() == ObjectID.WILD_CAVE_ENTRANCE_LOW) {
            if (Rs2Player.isMoving()) {
                Rs2Player.waitForWalking();
            }
            Widget dialogueSprite = Rs2Dialogue.getDialogueSprite();
            if (dialogueSprite != null && dialogueSprite.getItemId() == 1004) {
                Rs2Dialogue.clickContinue();
                Rs2Dialogue.sleepUntilSelectAnOption();
                Rs2Dialogue.clickOption("Yes, don't ask again");
                Rs2Dialogue.sleepUntilNotInDialogue();
            }
            return true;
        }

        if (tileObject.getId() == ObjectID.HEROROCKSLIDE) {
            Rs2Player.waitForAnimation(600 * 4);
            return true;
        }

        if (Rs2GameObject.getObjectIdsByName("Fossil_Rowboat").contains(tileObject.getId())) {
            if (transport.getDisplayInfo() == null || transport.getDisplayInfo().isEmpty()) return false;

            char option = transport.getDisplayInfo().charAt(0);
            Rs2Dialogue.sleepUntilSelectAnOption();
            Rs2Keyboard.keyPress(option);
            sleepUntil(() -> {
                WorldPoint pl = Rs2Player.getWorldLocation();
                WorldPoint td = transport.getDestination();
                return pl != null && td != null && pl.getPlane() == td.getPlane()
                        && pl.distanceTo2D(td) < OFFSET;
            }, 10000);
            return true;
        }

        // Handle door/gate near wilderness agility course
        if (tileObject.getId() == ObjectID.BALANCEGATE52A || tileObject.getId() == ObjectID.BALANCEGATE52B_RIGHT || tileObject.getId() == ObjectID.BALANCEGATE52B_LEFT) {
            Rs2Player.waitForAnimation(600 * 4);
            return true;
        }

        if (tileObject.getId() == ObjectID.AERIAL_FISHING_BOAT) {
            Rs2Dialogue.sleepUntilSelectAnOption();
            Rs2Dialogue.clickOption(transport.getDisplayInfo(), true);
            sleepUntil(() -> {
                WorldPoint pl = Rs2Player.getWorldLocation();
                WorldPoint td = transport.getDestination();
                return pl != null && td != null && pl.getPlane() == td.getPlane()
                        && pl.distanceTo2D(td) < OFFSET;
            }, 10000);
            return true;
        }

        // Handle Magic Mushtree (Fossil Island Mycelium Transportation System)
        if (MagicMushtree.isMagicMushtree(tileObject)) {
            return MagicMushtree.handleTransport(transport);
        }
        return false;
    }

    private static boolean handleWildernessObelisk(Transport transport) {
        GameObject obelisk = Rs2GameObject.getGameObject(obj -> obj.getId() == transport.getObjectId(), transport.getOrigin());

        if (obelisk != null) {
            Rs2GameObject.interact(obelisk, transport.getAction());
            sleepUntil(() -> Rs2GameObject.getGameObject(obj -> obj.getId() == transport.getObjectId(), transport.getOrigin()) != null);
            walkFastCanvas(transport.getOrigin());
            return sleepUntilTrue(() -> {
                WorldPoint pl = Rs2Player.getWorldLocation();
                WorldPoint td = transport.getDestination();
                return pl != null && td != null && pl.getPlane() == td.getPlane()
                        && pl.distanceTo2D(td) < OFFSET;
            }, 100, 10000);
        }
        return false;
    }

    private static boolean handleTeleportSpell(Transport transport) {
        if (Rs2Pvp.isInWilderness() && !isTeleportAllowedAtWildernessLevel(
                Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()), transport.getMaxWildernessLevel())) return false;
        if (!prepareTeleportSpellProviders(transport)) return false;
        boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");

        String spellName = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                : transport.getDisplayInfo().toLowerCase();

        String option = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[1].trim().toLowerCase()
                : "cast";

        int identifier = hasMultipleDestination
                ? 2
                : 1;

        Optional<TransportExecutionRegistry.HomeTeleport> homeTeleport =
                TransportExecutionRegistry.homeTeleportFor(transport.getDisplayInfo());
        if (homeTeleport.isPresent()) {
            if (!isTeleportSpellUsableDuringCombat(transport, Rs2Player.isInCombat())) {
                return false;
            }
            return Rs2Magic.quickCast(homeTeleport.get().getDisplayName());
        }

        MagicAction magicSpell = Arrays.stream(MagicAction.values()).filter(x -> x.getName().toLowerCase().contains(spellName)).findFirst().orElse(null);
        if (magicSpell != null) {
            return Rs2Magic.cast(magicSpell, option, identifier);
        }
        return false;
    }

    static boolean isTeleportSpellUsableDuringCombat(Transport transport, boolean inCombat) {
        return transport != null && (!inCombat
                || !TransportExecutionRegistry.homeTeleportFor(transport.getDisplayInfo()).isPresent());
    }

    /**
     * Equip any inventory staff/tome selected by a source-aware upstream spell requirement before
     * casting. An item merely present in the inventory never acts as an infinite rune provider.
     */
    private static boolean prepareTeleportSpellProviders(Transport transport) {
        List<TransportItemRequirement> requirements = transport.getItemRequirements();
        if (requirements == null || requirements.isEmpty()) {
            return true;
        }

        Map<Integer, Integer> runeQuantities = new HashMap<>();
        Rs2Magic.getRunes().forEach((rune, quantity) ->
                runeQuantities.put(rune.getItemId(), quantity));
        java.util.function.IntUnaryOperator currentQuantity = itemId -> {
            Runes rune = Runes.byItemId(itemId);
            if (rune != null) {
                return runeQuantities.getOrDefault(itemId, 0);
            }
            int quantity = Rs2Inventory.itemQuantity(itemId);
            Rs2ItemModel equipped = Rs2Equipment.get(itemId);
            return equipped == null ? quantity : quantity + Math.max(1, equipped.getQuantity());
        };

        TransportItemRequirement.ProviderSelection providers =
                TransportItemRequirement.selectProviders(
                        requirements,
                        currentQuantity,
                        itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId),
                        itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId))
                        .orElse(null);
        if (providers == null) {
            return false;
        }
        if (!equipTransportProvider(providers.getStaffItemId())
                || !equipTransportProvider(providers.getOffhandItemId())) {
            return false;
        }

        Map<Integer, Integer> verifiedRuneQuantities = new HashMap<>();
        Rs2Magic.getRunes().forEach((rune, quantity) ->
                verifiedRuneQuantities.put(rune.getItemId(), quantity));
        return TransportItemRequirement.selectProviders(
                requirements,
                itemId -> {
                    Runes rune = Runes.byItemId(itemId);
                    if (rune != null) {
                        return verifiedRuneQuantities.getOrDefault(itemId, 0);
                    }
                    int quantity = Rs2Inventory.itemQuantity(itemId);
                    Rs2ItemModel equipped = Rs2Equipment.get(itemId);
                    return equipped == null ? quantity : quantity + Math.max(1, equipped.getQuantity());
                },
                Rs2Equipment::isWearing,
                Rs2Equipment::isWearing).isPresent();
    }

    private static boolean equipTransportProvider(int itemId) {
        if (itemId <= 0 || Rs2Equipment.isWearing(itemId)) {
            return true;
        }
        return Rs2Inventory.hasItem(itemId)
                && Rs2Inventory.wield(itemId)
                && sleepUntil(() -> Rs2Equipment.isWearing(itemId), 3000);
    }

    private static boolean isLumbridgeHomeTeleport(Transport transport) {
        return transport.getDisplayInfo() != null
                && transport.getDisplayInfo().toLowerCase().startsWith("lumbridge home teleport");
    }

    private static boolean handleTeleportItem(Transport transport) {
        WorldPoint plWild = Rs2Player.getWorldLocation();
        if (Rs2Pvp.isInWilderness() && plWild != null
                && !isTeleportAllowedAtWildernessLevel(
                        Rs2Pvp.getWildernessLevelFrom(plWild), transport.getMaxWildernessLevel())) {
            return false;
        }
        boolean succesfullAction = false;
        for (Set<Integer> itemIds : transport.getItemIdRequirements()) {
            if (succesfullAction)
                break;
            for (Integer itemId : itemIds) {
                if (Rs2Walker.currentTarget == null) break;
                // reachedDistance <= 0: do not treat as "already at destination" (legacy: raw distance < 0 never true).
                int reachRd = reachedDistanceOrDefault();
                if (reachRd > 0 && isPlayerWithinChebyshevOf(transport.getDestination(), reachRd)) {
                    break;
                }
                if (succesfullAction) break;

                //If an action is succesfully we break out of the loop
                succesfullAction = handleWearableTeleports(transport, itemId) || handleInventoryTeleports(transport, itemId);
            }
        }
        return succesfullAction;
    }

    private static boolean handleInventoryTeleports(Transport transport, int itemId) {
        Rs2ItemModel rs2Item = Rs2Inventory.get(itemId);
        if (rs2Item == null) return false;

        // A list of generic teleports that can be used if no parsable destination action is found
        List<String> genericKeyWords = Arrays.asList(
                "invoke", "empty", "consume", "open", "teleport", "rub", "break", "reminisce", "signal", "play", "commune", "squash", "blow"
        );

        // Return true when the item does not use a generic keyword to teleport to its destination
        boolean hasParsableDestination = transport.getDisplayInfo().contains(":");
        String destination = teleportItemLeafAction(transport.getDisplayInfo());

        boolean wildernessTransport = Rs2PathApi.isInWilderness(transport.getDestination());

        log.debug("Trying to find action for destination={}", destination);
        // Check if item has destination as direct action
        String itemAction = rs2Item.getAction(destination);

        // Check if item has destination as sub-menu action
        Map.Entry<String,Integer> sub = rs2Item.getIndexOfSubAction(destination);
        if (itemAction == null && sub != null && sub.getKey() != null) {
            itemAction = destination;
        }

        // If there's only one destination with the item possible, a generic action will also work
        if (itemAction == null && !hasParsableDestination) {
            itemAction = rs2Item.getActionFromList(genericKeyWords);
        }

        if (itemAction != null) {
            boolean interaction = Rs2Inventory.interact(rs2Item, itemAction);
            if (!interaction) {
                return false;
            } else if (wildernessTransport) {
                Rs2Dialogue.sleepUntilInDialogue();
                return Rs2Dialogue.clickOption("Yes", "Okay");
            } else if (isQuetzalWhistleItemId(itemId)) {
                return finishQuetzalWhistleTransport(transport);
            }
            return true;
        }

        // If no location-based action found, try generic actions
        itemAction = rs2Item.getActionFromList(genericKeyWords);

        if (itemAction == null) {
            log.debug("No generic keyword found for={}, genericKeywords={}", itemAction, String.join(",", genericKeyWords));
            return false;
        }

        if (Rs2Inventory.interact(itemId, itemAction)) {
            log.debug("Traveling with genericAction={}, to {} - ({})", itemAction, transport.getDisplayInfo(), transport.getDestination());

            if (itemAction.equalsIgnoreCase("open") && itemId == ItemID.BOOKOFSCROLLS_CHARGED) {
                return handleMasterScrollBook(destination);
            } else if (isQuetzalWhistleItemId(itemId)) {
                return finishQuetzalWhistleTransport(transport);
            } else if (isDialogueBasedTeleportItem(transport.getDisplayInfo())) {
                // Multi-destination teleport items: wait for destination selection dialogue
                Rs2Dialogue.sleepUntilSelectAnOption();
                Rs2Dialogue.clickOption(destination);
                log.info("Traveling to {} - ({})", transport.getDisplayInfo(), transport.getDestination());
                return true;
            } else if (transport.getDisplayInfo().toLowerCase().contains("burning amulet")) {
                // Burning amulet in inventory: confirm wilderness teleport
                Rs2Dialogue.sleepUntilInDialogue();
                Rs2Dialogue.clickOption("Okay, teleport to level");
                log.info("Traveling to {} - ({})", transport.getDisplayInfo(), transport.getDestination());
                return true;
            } else if (wildernessTransport) {
                Rs2Dialogue.sleepUntilInDialogue();
                return Rs2Dialogue.clickOption("Yes", "Okay");
            } else {
                Rs2Player.waitForAnimation();
                log.info("Unsure how to handle this itemTransport={} action={}", transport, itemAction);
            }
        }
        return false;
    }

    private static boolean handleWearableTeleports(Transport transport, int itemId) {
        Rs2ItemModel rs2Item = Rs2Equipment.get(itemId);
        if (rs2Item == null) return false;
        if (transport.getDisplayInfo().contains(":")) {
            String destination = teleportItemLeafAction(transport.getDisplayInfo());

            if (transport.getDisplayInfo().toLowerCase().contains("slayer ring")) {
                Rs2Equipment.invokeMenu(rs2Item, "teleport");
                Rs2Dialogue.sleepUntilSelectAnOption();
                Rs2Dialogue.clickOption(destination);
            } else {
                Rs2Equipment.invokeMenu(rs2Item, destination);
                if (transport.getDisplayInfo().toLowerCase().contains("burning amulet")) {
                    Rs2Dialogue.sleepUntilInDialogue();
                    Rs2Dialogue.clickOption("Okay, teleport to level");
                }
            }
            log.info("Traveling to {} - ({})", transport.getDisplayInfo(), transport.getDestination());
            return true;
        }
        return false;
    }

    /**
     * Returns the executable leaf from a display hierarchy. Upstream labels may describe nested
     * categories (for example {@code Max cape: POH Portals: Rimmington}); RuneLite item sub-ops are
     * looked up by their leaf action, not by the intermediate display category.
     */
    static String teleportItemLeafAction(String displayInfo) {
        if (displayInfo == null) {
            return "";
        }
        String[] segments = displayInfo.split(":");
        return segments[segments.length - 1].trim().toLowerCase(Locale.ROOT);
    }

    static boolean isTeleportAllowedAtWildernessLevel(int currentLevel, int maximumLevel) {
        return currentLevel <= maximumLevel;
    }

    /**
     * Checks if the teleport item requires dialogue-based destination selection.
     * These are items that, when rubbed/activated, show a dialogue menu to choose destination.
     *
     * @param displayInfo the displayInfo from the transport
     * @return true if the item requires dialogue handling
     */
    private static boolean isDialogueBasedTeleportItem(String displayInfo) {
        if (displayInfo == null) return false;
        String lowerDisplayInfo = displayInfo.toLowerCase();
        return lowerDisplayInfo.contains("slayer ring")
                || lowerDisplayInfo.contains("games necklace")
                || lowerDisplayInfo.contains("skills necklace")
                || lowerDisplayInfo.contains("ring of dueling")
                || lowerDisplayInfo.contains("ring of wealth")
                || lowerDisplayInfo.contains("amulet of glory")
                || lowerDisplayInfo.contains("combat bracelet")
                || lowerDisplayInfo.contains("digsite pendant")
                || lowerDisplayInfo.contains("necklace of passage")
                || lowerDisplayInfo.contains("giantsoul amulet");
    }

    /**
     * Forwards to {@link Rs2LeaguesTransport#recordTransportAttempt} for Leagues locked-region chat correlation.
     * Delegate records only teleport-like transports while Leagues is active (seasonal + spells/items, e.g. ectophial).
     */
    public static void recordTransportAttempt(Transport transport)
    {
        Rs2LeaguesTransport.recordTransportAttempt(transport);
    }

    /**
     * Writes {@code phase="result"} for {@link Rs2LeaguesTransport#appendTransportObservation} (seasonal rows only).
     */
    private static void recordTransportResult(Transport transport, boolean success)
    {
        if (transport == null || transport.getType() != TransportType.SEASONAL_TRANSPORT)
        {
            return;
        }
        if (!Rs2LeaguesTransport.isLeaguesActive())
        {
            return;
        }
        Rs2LeaguesTransport.appendTransportObservation("result", transport, success, success ? "ok" : "fail");
    }

    /** Wraps an action with {@link #recordTransportAttempt} + {@link #recordTransportResult} (seasonal JSONL, Leagues snapshot for teleports).
     * @see net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport
     */
    private static boolean attemptObserved(Transport transport, BooleanSupplier action)
    {
        if (transport == null || action == null)
        {
            return false;
        }
        boolean leaguesActive = Rs2LeaguesTransport.isLeaguesActive();
        // Snapshot attempt for Leagues locked-region chat correlation (avoid churn outside leagues).
        if (leaguesActive)
        {
            recordTransportAttempt(transport);
        }
        boolean ok = action.getAsBoolean();
        if (leaguesActive)
        {
            recordTransportResult(transport, ok);
        }
        return ok;
    }

    /**
     * Like {@link #attemptObserved} but does not call {@link #recordTransportAttempt} before the action.
     * Seasonal handlers record attempts at their click sites so {@link Rs2LeaguesTransport#getLastTransportAttemptSnapshot}
     * matches the handler that actually ran (Leagues Area vs MoA).
     */
    private static boolean attemptObservedWithoutAttemptRecord(Transport transport, BooleanSupplier action)
    {
        if (transport == null || action == null)
        {
            return false;
        }
        boolean leaguesActive = Rs2LeaguesTransport.isLeaguesActive();
        boolean ok = action.getAsBoolean();
        if (leaguesActive)
        {
            recordTransportResult(transport, ok);
        }
        return ok;
    }

    /**
     * Tries configured seasonal transport handlers for the same {@link Transport} row.
     * Attempt recording is done inside each handler (for built-ins, {@link Rs2LeaguesTransport#tryHandleLeaguesAreaTransportResult})
     * — use {@link #attemptObservedWithoutAttemptRecord} at the call site.
     */
    private static boolean handleSeasonalTransport(Transport transport) {
        if (transport == null) {
            return false;
        }
        String displayInfo = transport.getDisplayInfo();
        if (displayInfo == null) return false;

        List<SeasonalTransportHandler> handlers = seasonalTransportHandlers;
        for (SeasonalTransportHandler h : handlers)
        {
            if (h == null)
            {
                continue;
            }
            if (!h.matches(transport))
            {
                continue;
            }
            if (h.tryUse(transport))
            {
                return true;
            }
        }
        Telemetry.incrementSeasonalHandlerMiss();
        if (log.isDebugEnabled() && SEASONAL_HANDLER_MISS_LOGGED_COUNT.get() < SEASONAL_HANDLER_MISS_LOG_CAP)
        {
            WorldPoint destWp = transport.getDestination();
            String hash = Integer.toHexString(displayInfo.hashCode());
            String tail = displayInfo.length() > 160
                    ? displayInfo.substring(0, 160) + "|h" + hash
                    : displayInfo + "|h" + hash;
            final String missKey;
            Integer packedTileOrNull = null;
            if (destWp != null)
            {
                packedTileOrNull = WorldPointUtil.packWorldPoint(destWp);
                missKey = Integer.toHexString(packedTileOrNull) + "|" + tail;
            }
            else
            {
                missKey = "nodest|" + tail;
            }
            if (SEASONAL_HANDLER_MISS_LOGGED.add(missKey))
            {
                // Best-effort cap: only increment while below cap; duplicates and races are fine for debug-only logs.
                for (;;)
                {
                    int prev = SEASONAL_HANDLER_MISS_LOGGED_COUNT.get();
                    if (prev >= SEASONAL_HANDLER_MISS_LOG_CAP)
                    {
                        break;
                    }
                    if (SEASONAL_HANDLER_MISS_LOGGED_COUNT.compareAndSet(prev, prev + 1))
                    {
                        break;
                    }
                }
                String sample = displayInfo.length() > 160 ? displayInfo.substring(0, 160) + "…" : displayInfo;
                if (packedTileOrNull != null)
                {
                    sample = sample + " destPacked=" + Integer.toHexString(packedTileOrNull);
                }
                log.debug("[Walker] seasonal transport unmatched by configured handlers (expect pathfinder-only matching rows); key={} sample={}",
                        missKey, sample);
            }
        }
        return false;
    }

    private static boolean handleSpiritTree(Transport transport) {
        // Get Transport Information
        String displayInfo = transport.getDisplayInfo();
        int objectId = transport.getObjectId();
        if (log.isDebugEnabled())
        {
            log.debug("[Walker] handleSpiritTree: displayInfo={}, objectId={}", displayInfo, objectId);
        }
        if (displayInfo == null || displayInfo.isEmpty()) {
            if (log.isDebugEnabled())
            {
                log.debug("[Walker] handleSpiritTree: displayInfo empty, returning false");
            }
            return false;
        }

        if (!Rs2Widget.isWidgetVisible(ComponentID.ADVENTURE_LOG_CONTAINER)) {
            TileObject spiritTree = Rs2GameObject.findObjectById(objectId);
            if (log.isDebugEnabled())
            {
                log.debug("[Walker] handleSpiritTree: findObjectById({}) returned {}",
                        objectId, spiritTree != null ? "non-null @ " + spiritTree.getWorldLocation() : "NULL");
            }
            if (spiritTree == null) {
                // POH fix: handleSpiritTree's findObjectById uses the transport's objectId
                // which is keyed from the TSV. Inside a POH the spirit tree is a different
                // object id than the overworld TSV expects. Fall back to the PohTeleports
                // helper which knows the full set of POH spirit-tree ids.
                spiritTree = PohTeleports.getSpiritTree();
                if (log.isDebugEnabled())
                {
                    log.debug("[Walker] handleSpiritTree: POH fallback getSpiritTree() returned {}",
                            spiritTree != null ? "non-null @ " + spiritTree.getWorldLocation() : "NULL");
                }
            }
            boolean interactResult = Rs2GameObject.interact(spiritTree, "Travel");
            if (log.isDebugEnabled())
            {
                log.debug("[Walker] handleSpiritTree: interact(spiritTree, Travel) returned {}", interactResult);
            }
            if (!interactResult) {
                return false;
            }
        }

        boolean result = interactWithAdventureLog(transport);
        if (log.isDebugEnabled())
        {
            log.debug("[Walker] handleSpiritTree: interactWithAdventureLog returned {}", result);
        }
        return result;
    }

    private static boolean handleMinigameTeleport(Transport transport) {
        final Object[] selectedOpListener = new Object[]{489, 0, 0};
        final List<Integer> teleportGraphics = List.of(800, 802, 803, 804);

        @Component final int GROUPING_BUTTON_COMPONENT_ID = 46333957; // 707.5

        @Component final int DROPDOWN_BUTTON_COMPONENT_ID = 4980760; // 76.24
        final int DROPDOWN_SELECTED_SPRITE_ID = 773;

        @Component final int MINIGAME_LIST = 4980758; // 76.22
        @Component final int SELECTED_MINIGAME = 4980747; // 76.11
        @Component final int TELEPORT_BUTTON = 4980768; // 76.32

        // Minigame teleports cant be used if a dialogue is open.
        if (Rs2Dialogue.isInDialogue()) {
            var playerLocation = Rs2Player.getLocalLocation();
            walkFastLocal(playerLocation);
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.CHAT) {
            Rs2Tab.switchTo(InterfaceTab.CHAT);
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.CHAT);
        }

        Widget groupingBtn = Rs2Widget.getWidget(GROUPING_BUTTON_COMPONENT_ID);
        if (groupingBtn == null) return false;

        if (!Arrays.equals(groupingBtn.getOnOpListener(), selectedOpListener)) {
            Rs2Widget.clickWidget(groupingBtn);
            sleepUntil(() -> Arrays.equals(groupingBtn.getOnOpListener(), selectedOpListener));
        }

        boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
        String destination = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                : transport.getDisplayInfo().trim().toLowerCase();

        Widget selectedWidget = Rs2Widget.getWidget(SELECTED_MINIGAME);
        if (selectedWidget == null) return false;
        if (!selectedWidget.getText().equalsIgnoreCase(destination)) {
            Widget dropdownBtn = Rs2Widget.getWidget(DROPDOWN_BUTTON_COMPONENT_ID);
            if (dropdownBtn == null) return false;

            if (dropdownBtn.getSpriteId() != DROPDOWN_SELECTED_SPRITE_ID) {
                Rs2Widget.clickWidget(dropdownBtn);
                sleepUntil(() -> Rs2Widget.findWidget(DROPDOWN_SELECTED_SPRITE_ID, List.of(Rs2Widget.getWidget(DROPDOWN_BUTTON_COMPONENT_ID))) != null);
            }

            Widget minigameWidgetParent = Rs2Widget.getWidget(MINIGAME_LIST);
            if (minigameWidgetParent == null) return false;
            List<Widget> minigameWidgetList = Arrays.stream(minigameWidgetParent.getDynamicChildren())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            Widget destinationWidget = Rs2Widget.findWidget(destination, minigameWidgetList);
            if (destinationWidget == null) return false;

            NewMenuEntry destinationMenuEntry = new NewMenuEntry()
                    .option("Select")
                    .target("")
                    .identifier(1)
                    .type(MenuAction.CC_OP)
                    .param0(destinationWidget.getIndex())
                    .param1(minigameWidgetParent.getId())
                    .forceLeftClick(false);

            Microbot.doInvoke(destinationMenuEntry, new Rectangle(1, 1));
            sleepUntil(() -> Rs2Widget.getWidget(SELECTED_MINIGAME).getText().equalsIgnoreCase(destination));
        }

        Widget teleportBtn = Rs2Widget.getWidget(TELEPORT_BUTTON);
        if (teleportBtn == null) return false;
        Rs2Widget.clickWidget(teleportBtn);

        if (transport.getDisplayInfo().toLowerCase().contains("rat pits")) {
            Rs2Dialogue.sleepUntilSelectAnOption();
            Rs2Dialogue.clickOption(transport.getDisplayInfo().split(":")[1].trim().toLowerCase());
        }

        sleepUntil(Rs2Player::isAnimating);
        return sleepUntilTrue(() -> !Rs2Player.isAnimating() && teleportGraphics.stream().noneMatch(Rs2Player::hasSpotAnimation), 100, 20000);
    }

    static int canoeMapMainComponentId(int stationObjectId) {
        if (stationObjectId >= 60845 && stationObjectId <= 60849) {
            return InterfaceID.CanoeMapDougne.MAIN_MAP;
        }
        if ((stationObjectId >= 12163 && stationObjectId <= 12166) || stationObjectId == 39638) {
            return InterfaceID.CanoeMapLum.MAIN_MAP;
        }
        return -1;
    }

    static int canoeMapDestinationsComponentId(int stationObjectId) {
        if (stationObjectId >= 60845 && stationObjectId <= 60849) {
            return InterfaceID.CanoeMapDougne.DESTINATIONS;
        }
        if ((stationObjectId >= 12163 && stationObjectId <= 12166) || stationObjectId == 39638) {
            return InterfaceID.CanoeMapLum.DESTINATIONS;
        }
        return -1;
    }

    private static boolean handleCanoe(Transport transport) {
        String displayInfo = transport.getDisplayInfo();
        if (displayInfo == null || displayInfo.isEmpty()) return false;

        List<String> validActions = List.of("chop-down", "shape-canoe", "float canoe", "paddle canoe");
        ObjectComposition CANOE_COMPOSITION = Rs2GameObject.convertToObjectComposition(transport.getObjectId());
        if (CANOE_COMPOSITION == null) return false;

        String currentAction = Arrays.stream(CANOE_COMPOSITION.getActions())
                .filter(Objects::nonNull)
                .filter(act -> validActions.contains(act.toLowerCase())).findFirst().orElse(null);
        if (currentAction == null || currentAction.isEmpty()) {
            log.error("Unable to find canoe action");
            return false;
        }

        switch (currentAction) {
            case "Chop-down":
                Rs2GameObject.interact(transport.getObjectId(), "Chop-down");
                sleepUntil(() -> Rs2Player.isAnimating(1200));
                return sleepUntilTrue(() -> {
                    ObjectComposition composition = Rs2GameObject.convertToObjectComposition(transport.getObjectId());

                    if (composition == null) return false;
                    return Arrays.stream(composition.getActions()).filter(Objects::nonNull).noneMatch(currentAction::equals) && !Rs2Player.isAnimating();
                }, 300, 10000);
            case "Shape-Canoe":
                @Component final int CANOE_SELECTION_PARENT = 27262976; // 416.3
                @Component final int CANOE_SHAPING_TEXT = 27262986; // 416.10

                Rs2GameObject.interact(transport.getObjectId(), "Shape-Canoe");
                boolean isCanoeShapeTextVisible = sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(CANOE_SHAPING_TEXT), 100, 10000);
                if (!isCanoeShapeTextVisible) {
                    log.error("Canoe shape text is not visible within timeout period");
                    return false;
                }

                final int woodcuttingLevel = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
                String canoeOption;
                if (woodcuttingLevel >= 57) {
                    canoeOption = "Waka canoe";
                } else if (woodcuttingLevel >= 42) {
                    canoeOption = "Stable dugout canoe";
                } else if (woodcuttingLevel >= 27) {
                    canoeOption = "Dugout canoe";
                } else if (woodcuttingLevel >= 12) {
                    canoeOption = "Log canoe";
                } else {
                    // Not high enough level to make any canoe
                    return false;
                }

                Widget canoeSelectionParentWidget = Rs2Widget.getWidget(CANOE_SELECTION_PARENT);
                if (canoeSelectionParentWidget == null) return false;
                Widget canoeSelectionWidget = Rs2Widget.findWidget("Make " + canoeOption, List.of(canoeSelectionParentWidget));
                Rs2Widget.clickWidget(canoeSelectionWidget);
                sleepUntil(() -> Rs2Player.isAnimating(1200));
                return sleepUntilTrue(() -> {
                    ObjectComposition composition = Rs2GameObject.convertToObjectComposition(transport.getObjectId());

                    if (composition == null) return false;
                    return Arrays.stream(composition.getActions()).filter(Objects::nonNull).noneMatch(currentAction::equals) && !Rs2Player.isAnimating();
                }, 300, 10000);
            case "Float Canoe":
                Rs2GameObject.interact(transport.getObjectId(), "Float Canoe");
                sleepUntil(() -> Rs2Player.isAnimating(1200));
                return sleepUntilTrue(() -> {
                    ObjectComposition composition = Rs2GameObject.convertToObjectComposition(transport.getObjectId());

                    if (composition == null) return false;
                    return Arrays.stream(composition.getActions()).filter(Objects::nonNull).noneMatch(currentAction::equals) && !Rs2Player.isAnimating();
                }, 300, 10000);
            case "Paddle Canoe":
                int canoeMapMain = canoeMapMainComponentId(transport.getObjectId());
                int canoeMapDestinations = canoeMapDestinationsComponentId(transport.getObjectId());
                if (canoeMapMain < 0 || canoeMapDestinations < 0) {
                    log.error("Unsupported canoe station object id: {}", transport.getObjectId());
                    return false;
                }
                if (!Rs2GameObject.interact(transport.getObjectId(), "Paddle Canoe")) {
                    log.error("Failed to interact with canoe station");
                    return false;
                }

                // Wait for the player to actually walk to the canoe station and stop moving
                // before checking for the destination map widget. The interact call only
                // queues the click; the player still has to walk there.
                sleepUntil(Rs2Player::isMoving, 2000);
                sleepUntilTrue(() -> !Rs2Player.isMoving(), 100, 30000);

                // OSRS uses separate interfaces for the River Lum and River Dougne chains.
                boolean isDestinationMapVisible = sleepUntilTrue(
                        () -> Rs2Widget.isWidgetVisible(canoeMapMain),
                        100, 10000);
                if (!isDestinationMapVisible) {
                    log.error("Canoe destination map not visible within timeout period for station {}",
                            transport.getObjectId());
                    return false;
                }

                Widget destinationListWidget = Rs2Widget.getWidget(canoeMapDestinations);
                if (destinationListWidget == null) return false;
                Widget destination = Rs2Widget.findWidget("Travel to " + displayInfo, List.of(destinationListWidget), false);
                if (destination == null) {
                    log.error("Could not find canoe destination widget for: {}", displayInfo);
                    return false;
                }
                Rs2Widget.clickWidget(destination);

                Rs2Dialogue.waitForCutScene(100, 15000);
                return sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET * 2), 100, 5000);
        }
        return false;
    }

    private static boolean isQuetzalWhistleItemId(int itemId) {
        return itemId == ItemID.HG_QUETZALWHISTLE_BASIC
                || itemId == ItemID.HG_QUETZALWHISTLE_ENHANCED
                || itemId == ItemID.HG_QUETZALWHISTLE_PERFECTED
                || itemId == ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE;
    }

    /**
     * Labels match {@code quetzals.tsv} destination rows (map icon text).
     */
    static String quetzalMapLabelForDestination(WorldPoint dest) {
        assert dest != null;
        final int[][] coords = {
                {1389, 2901, 0}, {1697, 3140, 0}, {1585, 3053, 0}, {1510, 3222, 0}, {1548, 2995, 0},
                {1437, 3171, 0}, {1779, 3111, 0}, {1700, 3037, 0}, {1670, 2933, 0}, {1446, 3108, 0},
                {1613, 3300, 0}, {1226, 3091, 0}, {1344, 3022, 0}, {1411, 3361, 0},
        };
        final String[] labels = {
                "Aldarin", "Civitas illa Fortis", "Hunter Guild", "Quetzacalli Gorge", "Sunset Coast",
                "The Teomat", "Fortis Colosseum", "Outer Fortis", "Colossal Wyrm Remains", "Cam Torum",
                "Salvager Overlook", "Tal Teklan", "Kastori", "Auburnvale",
        };
        assert coords.length == labels.length;
        // Bank / script targets often sit several tiles off quetzals.tsv landing coords.
        final int matchTiles = 15;
        for (int i = 0; i < coords.length; i++) {
            WorldPoint p = new WorldPoint(coords[i][0], coords[i][1], coords[i][2]);
            if (dest.distanceTo2D(p) <= matchTiles && dest.getPlane() == p.getPlane()) {
                return labels[i];
            }
        }
        return null;
    }

    /**
     * Option text on the Quetzal map — Renu uses {@link InterfaceID.QuetzalMenu}, whistle uses {@link InterfaceID.QuetzalwhistleMenu}
     * (same icon labels). Prefers resolving from {@link Transport#getDestination()} so bank/custom tiles match.
     */
    private static String resolveQuetzalMapOptionLabel(Transport transport) {
        assert transport != null;
        WorldPoint dest = transport.getDestination();
        if (dest != null) {
            String byCoords = quetzalMapLabelForDestination(dest);
            if (byCoords != null && !byCoords.isEmpty()) {
                return byCoords;
            }
        }
        String di = transport.getDisplayInfo();
        if (di != null && di.contains(":")) {
            String[] parts = di.split(":", 2);
            if (parts.length >= 2) {
                String loc = parts[1].trim();
                if (!loc.isEmpty()) {
                    return loc;
                }
            }
        }
        return dest != null ? quetzalMapLabelForDestination(dest) : null;
    }

    /** True when any Quetzal or whistle-map layer is visible (CONTENTS alone can stay hidden while MAP/ICONS show). */
    private static boolean isQuetzalMapInterfaceVisible() {
        return Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.UNIVERSE)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.MAP)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.ICONS)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.CONTENTS)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalwhistleMenu.UNIVERSE)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalwhistleMenu.MAP)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalwhistleMenu.ICONS)
                || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalwhistleMenu.CONTENTS);
    }

    private static boolean finishQuetzalWhistleTransport(Transport transport) {
        assert transport != null;
        WorldPoint dest = transport.getDestination();
        assert dest != null;
        WorldPoint pl = Rs2Player.getWorldLocation();
        if (pl != null && pl.getPlane() == dest.getPlane() && pl.distanceTo2D(dest) < OFFSET) {
            log.debug("Quetzal whistle: already within {} tiles of {}, skipping map", OFFSET, dest);
            return true;
        }
        String mapLabel = resolveQuetzalMapOptionLabel(transport);
        if (mapLabel == null || mapLabel.isEmpty()) {
            log.warn("Quetzal whistle: could not resolve map label (displayInfo={}, destination={})",
                    transport.getDisplayInfo(), dest);
            return false;
        }
        Rs2Player.waitForAnimation(1800);
        sleepUntil(() -> isQuetzalMapInterfaceVisible() || !Rs2Player.isAnimating(), 1400);
        sleep(Rs2Random.between(120, 260));
        return clickQuetzalMapDestination(mapLabel, dest);
    }

    /**
     * Finds destination row/icon; map can open before icon layer is built — search full subtree from several roots,
     * not only {@link Widget#getDynamicChildren()} of {@link InterfaceID.QuetzalMenu#ICONS}.
     */
    private static Widget findQuetzalMapDestinationWidget(String mapOptionLabel) {
        assert mapOptionLabel != null && !mapOptionLabel.isEmpty();
        int[] roots = {
                InterfaceID.QuetzalMenu.ICONS,
                InterfaceID.QuetzalMenu.MAP,
                InterfaceID.QuetzalMenu.SCROLL,
                InterfaceID.QuetzalMenu.CONTENTS,
                InterfaceID.QuetzalMenu.UNIVERSE,
                InterfaceID.QuetzalwhistleMenu.ICONS,
                InterfaceID.QuetzalwhistleMenu.MAP,
                InterfaceID.QuetzalwhistleMenu.SCROLL,
                InterfaceID.QuetzalwhistleMenu.CONTENTS,
                InterfaceID.QuetzalwhistleMenu.UNIVERSE,
        };
        for (int rootId : roots) {
            // Widget#getDynamicChildren / isHidden must not run off the client thread — use marshalled helpers.
            if (Rs2Widget.isHidden(rootId)) {
                continue;
            }
            Widget root = Rs2Widget.getWidget(rootId);
            if (root == null) {
                continue;
            }
            Widget hit = Rs2Widget.findWidget(mapOptionLabel, List.of(root), false);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * Opens no NPC — caller must already have opened the Quetzal map (whistle or Renu).
     */
    private static boolean clickQuetzalMapDestination(String mapOptionLabel, WorldPoint expectedDestination) {
        assert mapOptionLabel != null && !mapOptionLabel.isEmpty();
        assert expectedDestination != null;
        long quetzalStartAt = System.currentTimeMillis();

        WorldPoint here = Rs2Player.getWorldLocation();
        if (here != null && here.getPlane() == expectedDestination.getPlane()
                && here.distanceTo2D(expectedDestination) < OFFSET) {
            log.debug("Quetzal map: already within {} tiles of {}, skipping map click", OFFSET, expectedDestination);
            return true;
        }

        boolean mapVisible = sleepUntilTrue(() -> isQuetzalMapInterfaceVisible(), 100, QUETZAL_MAP_VISIBLE_WAIT_MS);
        if (!mapVisible) {
            log.error("Quetzal map UI not visible within timeout (label={}, checked UNIVERSE/MAP/ICONS/CONTENTS)",
                    mapOptionLabel);
            return false;
        }
        WebWalkLog.tmark("quetzal_ui_opened", System.currentTimeMillis() - quetzalStartAt, expectedDestination, Rs2Player.getWorldLocation(),
                "label=" + mapOptionLabel);

        // ICONS subtree can attach shortly after the shell — brief pause before walking widget tree from walker thread.
        sleep(Rs2Random.between(80, 160));

        AtomicReference<Widget> destRef = new AtomicReference<>();
        boolean iconReady = sleepUntilTrue(() -> {
            Widget w = findQuetzalMapDestinationWidget(mapOptionLabel);
            destRef.set(w);
            return w != null;
        }, 120, QUETZAL_ICON_READY_WAIT_MS);
        Widget actionWidget = destRef.get();
        if (!iconReady || actionWidget == null) {
            log.error("Could not find Quetzal map icon for: {} (waited for widget tree after map visible)", mapOptionLabel);
            return false;
        }
        WebWalkLog.tmark("quetzal_option_found", System.currentTimeMillis() - quetzalStartAt, expectedDestination, Rs2Player.getWorldLocation(),
                "label=" + mapOptionLabel);

        Rs2Widget.clickWidget(actionWidget);
        log.info("Quetzal map: traveling to {} -> {}", mapOptionLabel, expectedDestination);
        WebWalkLog.tmark("quetzal_click_sent", System.currentTimeMillis() - quetzalStartAt, expectedDestination, Rs2Player.getWorldLocation(),
                "label=" + mapOptionLabel);
        return sleepUntilTrue(() -> isPlayerWithinChebyshevOf(expectedDestination, OFFSET), 100, 8000);
    }

    private static boolean handleQuetzal(Transport transport) {
        String displayInfo = transport.getDisplayInfo();
        if (displayInfo == null || displayInfo.isEmpty()) return false;

        WorldPoint destCheck = transport.getDestination();
        WorldPoint plCheck = Rs2Player.getWorldLocation();
        if (destCheck != null && plCheck != null && plCheck.getPlane() == destCheck.getPlane()
                && plCheck.distanceTo2D(destCheck) < OFFSET) {
            log.debug("Quetzal Renu: already within {} tiles of {}, skip travel UI", OFFSET, destCheck);
            return true;
        }

        Rs2NpcModel renu = Rs2Npc.getNpc(NpcID.QUETZAL_CHILD_GREEN);

        if (Rs2Tile.isTileReachable(transport.getOrigin()) && Rs2Npc.interact(renu, "travel")) {
            Rs2Player.waitForWalking();
            WorldPoint dest = transport.getDestination();
            String mapLabel = resolveQuetzalMapOptionLabel(transport);
            if (mapLabel == null || mapLabel.isEmpty() || dest == null) {
                return false;
            }
            return clickQuetzalMapDestination(mapLabel, dest);
        }
        return false;
    }

    private static boolean handleMasterScrollBook(String destination) {
        boolean isMasterScrollBookOpen = sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(InterfaceID.Bookofscrolls.CONTENTS), 100, 10000);
        if (!isMasterScrollBookOpen) {
            log.error("Master Scroll Book did not open within timeout period");
            return false;
        }

        Widget bookOfScrollsWidget = Rs2Widget.getWidget(InterfaceID.Bookofscrolls.CONTENTS);
        List<Widget> bookOfScrollsChildren = Arrays.stream(bookOfScrollsWidget.getStaticChildren())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Widget destinationWidget = Rs2Widget.findWidget(destination, bookOfScrollsChildren, false);
        if (destinationWidget == null) return false;
        boolean interaction = Rs2Widget.clickWidget(destinationWidget);
        if (interaction && destination.equalsIgnoreCase("Revenant cave")) {
            Rs2Dialogue.sleepUntilInDialogue();
            return Rs2Dialogue.clickOption("Yes, teleport me now");
        }
        return interaction;
    }

    private static boolean handleMagicCarpet(Transport transport) {
        final int flyingPoseAnimation = 6936;
        var rugMerchant = Rs2Npc.getNpc(transport.getObjectId());
        if (rugMerchant == null) return false;

        Rs2Npc.interact(rugMerchant, transport.getAction());
        Rs2Dialogue.sleepUntilInDialogue();
        Rs2Dialogue.clickOption(transport.getDisplayInfo());
        sleepUntil(() -> Rs2Player.getPoseAnimation() == flyingPoseAnimation, 10000);
        return sleepUntilTrue(() -> Rs2Player.getPoseAnimation() != flyingPoseAnimation, 600,60000);
    }

    private static boolean handleCharterShip(Transport transport) {
        String npcName = transport.getName();

        Rs2NpcModel npc = Rs2Npc.getNpc(npcName);
        log.info("Charter Ship NPC: " + npcName + " - " + (npc != null ? npc.getId() : "not found"));
        if (Rs2Npc.canWalkTo(npc, 20) && Rs2Npc.interact(npc, transport.getAction())) {
            Rs2Player.waitForWalking();
            if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(885, 4), 5000)) {
                return false;
            }

            Widget destinationWidget = findCharterDestinationWidget(transport.getDisplayInfo());
            if (!invokeCharterDestinationWidget(destinationWidget, transport.getDisplayInfo())) {
                return false;
            }
            confirmCharterTravelIfPrompted();
            return true;
        }
        return false;
    }

    private static Widget findCharterDestinationWidget(String destinationText) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget root = Microbot.getClient().getWidget(885, 4);
            if (root == null || root.isHidden()) {
                return null;
            }

            Widget textMatch = findCharterDestinationTextWidget(root, destinationText);
            if (textMatch == null) {
                return null;
            }

            Widget clickable = findClickableCharterWidget(textMatch, root);
            return clickable != null ? clickable : textMatch;
        }).orElse(null);
    }

    private static Widget findCharterDestinationTextWidget(Widget widget, String destinationText) {
        if (widget == null || widget.isHidden()) {
            return null;
        }
        if (charterWidgetMatchesDestination(widget, destinationText)) {
            return widget;
        }

        Widget[] staticChildren = widget.getStaticChildren();
        Widget found = findCharterDestinationTextWidget(staticChildren, destinationText);
        if (found != null) {
            return found;
        }

        Widget[] dynamicChildren = widget.getDynamicChildren();
        found = findCharterDestinationTextWidget(dynamicChildren, destinationText);
        if (found != null) {
            return found;
        }

        return findCharterDestinationTextWidget(widget.getNestedChildren(), destinationText);
    }

    private static Widget findCharterDestinationTextWidget(Widget[] widgets, String destinationText) {
        if (widgets == null) {
            return null;
        }
        for (Widget widget : widgets) {
            Widget found = findCharterDestinationTextWidget(widget, destinationText);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean charterWidgetMatchesDestination(Widget widget, String destinationText) {
        String needle = normalizeCharterWidgetText(destinationText);
        if (needle.isEmpty()) {
            return false;
        }
        if (normalizeCharterWidgetText(widget.getText()).contains(needle)
                || normalizeCharterWidgetText(widget.getName()).contains(needle)) {
            return true;
        }
        String[] actions = widget.getActions();
        if (actions == null) {
            return false;
        }
        return Arrays.stream(actions)
                .filter(Objects::nonNull)
                .map(Rs2Walker::normalizeCharterWidgetText)
                .anyMatch(action -> action.contains(needle));
    }


    private static Widget findClickableCharterWidget(Widget widget, Widget root) {
        Widget current = widget;
        while (current != null) {
            if (hasWidgetActions(current)) {
                return current;
            }
            if (current == root) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean hasWidgetActions(Widget widget) {
        String[] actions = widget.getActions();
        return actions != null && Arrays.stream(actions).anyMatch(action -> action != null && !action.isEmpty());
    }

    private static boolean invokeCharterDestinationWidget(Widget widget, String destinationText) {
        if (widget == null) {
            return false;
        }

        String option = getFirstWidgetAction(widget);
        if (option == null || option.isBlank()) {
            option = destinationText;
        }

        NewMenuEntry destinationMenuEntry = new NewMenuEntry()
                .option(option)
                .target("")
                .identifier(1)
                .type(MenuAction.CC_OP)
                .param0(widget.getIndex())
                .param1(widget.getId())
                .forceLeftClick(false);

        Rectangle bounds = widget.getBounds();
        Microbot.doInvoke(destinationMenuEntry, bounds != null ? bounds : Rs2UiHelper.getDefaultRectangle());
        return true;
    }

    private static String getFirstWidgetAction(Widget widget) {
        String[] actions = widget.getActions();
        if (actions == null) {
            return null;
        }
        return Arrays.stream(actions)
                .filter(action -> action != null && !action.isEmpty())
                .findFirst()
                .orElse(null);
    }

    private static void confirmCharterTravelIfPrompted() {
        if (sleepUntil(Rs2Dialogue::hasSelectAnOption, 2000)) {
            Rs2Dialogue.clickOption("Yes", true);
        }
    }

    private static boolean isMinecartMenuVisible() {
        return !Rs2Widget.isHidden(MINECART_MENU_GROUP, MINECART_MENU_LIST_CHILD);
    }

    private static boolean interactWithAdventureLog(Transport transport) {
        if (transport.getDisplayInfo() == null || transport.getDisplayInfo().isEmpty()) return false;

        // Two menus arrive here, and they are different interfaces: spirit trees and their kin open
        // the adventure log (187), but the Lovakengj minecart opens its own list (947, "Minecart
        // rides: 20 coins"). Waiting on 187 alone made every minecart trip time out for 10s and
        // return false without ever seeing its menu — the user-visible "it never selects the
        // destination". Verified live at Hosidius South: 947:9 holds "1: Arceuus".."C: Shayzien
        // West" as plain TEXT entries, and clicking the row by its verbatim displayInfo rides.
        boolean menuVisible = sleepUntilTrue(
                () -> !Rs2Widget.isHidden(ComponentID.ADVENTURE_LOG_CONTAINER) || isMinecartMenuVisible(),
                Rs2Player::isMoving, 100, 10000);

        if (!menuVisible) {
            log.warn("[Walker] destination menu (187/947) did not open for {}", transport.getDisplayInfo());
            return false;
        }
        if (isMinecartMenuVisible()) {
            return selectMinecartDestination(transport);
        }

        String displayInfo = transport.getDisplayInfo();
        // The menu prefixes every option with its shortcut key — digits for the first nine entries
        // and LETTERS after that (the Lovakengj minecart runs 1-9 then A: Port Piscarilius through
        // C: Shayzien West, read off the live interface). The old strip handled only digit prefixes,
        // so letter-keyed destinations searched for "A: Port Piscarilius" verbatim and could never
        // match a widget that stores the name apart from its key.
        String destinationString = displayInfo.replaceAll("^[0-9A-Za-z]:\\s*", "");

        // Null-safe on purpose: the old List.of(getWidget(187, 3)) THREW on a null child rather than
        // returning false, and the null branch below used to return with no log at all — this class
        // of failure reached the user as "it just doesn't select".
        Widget optionsRoot = Rs2Widget.getWidget(187, 3);
        Widget destinationWidget = optionsRoot == null ? null
                : Rs2Widget.findWidget(destinationString, List.of(optionsRoot));
        if (destinationWidget != null) {
            Rs2Widget.clickWidget(destinationWidget);
            log.info("Traveling to {} - ({})", displayInfo, transport.getDestination());
            return sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET), 100, 5000);
        }

        // Text lookup failed. This menu is BUILT for keyboard selection — child 187:1 is literally
        // named "keylisteners" in the cache, and every option's shortcut key is the displayInfo
        // prefix we just stripped. Pressing it is also what a human at this menu actually does.
        char shortcutKey = Character.toLowerCase(displayInfo.charAt(0));
        boolean hasShortcut = displayInfo.length() > 1 && displayInfo.charAt(1) == ':'
                && Character.isLetterOrDigit(shortcutKey);
        if (hasShortcut) {
            log.warn("[Walker] destination '{}' not found by text in menu 187:3 (rootNull={}); pressing shortcut '{}'",
                    destinationString, optionsRoot == null, shortcutKey);
            Rs2Keyboard.keyPress(shortcutKey);
            log.info("Traveling to {} - ({})", displayInfo, transport.getDestination());
            return sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET), 100, 5000);
        }

        log.warn("[Walker] destination '{}' not found in menu 187:3 and displayInfo '{}' carries no shortcut key",
                destinationString, displayInfo);
        return false;
    }

    /**
     * Selects a station in the minecart list (947:9). The tsv displayInfo is the row's verbatim text
     * ("7: Lovakengj"), so a text click is the primary path — verified live to ride. The rows are
     * also keyboard-built (the prefix is the shortcut), so a failed click falls back to the key.
     */
    private static boolean selectMinecartDestination(Transport transport) {
        String displayInfo = transport.getDisplayInfo();
        boolean selected = Rs2Widget.clickWidget(displayInfo,
                Optional.of(MINECART_MENU_GROUP), MINECART_MENU_LIST_CHILD, true);
        if (!selected && displayInfo.length() > 1 && displayInfo.charAt(1) == ':'
                && Character.isLetterOrDigit(displayInfo.charAt(0))) {
            char shortcutKey = Character.toLowerCase(displayInfo.charAt(0));
            log.warn("[Walker] minecart row '{}' not clickable; pressing shortcut '{}'", displayInfo, shortcutKey);
            Rs2Keyboard.keyPress(shortcutKey);
            selected = true;
        }
        if (!selected) {
            log.warn("[Walker] minecart destination '{}' not found in menu 947:9", displayInfo);
            return false;
        }
        log.info("Traveling to {} - ({}) via minecart menu", displayInfo, transport.getDestination());
        return sleepUntilTrue(() -> isPlayerWithinChebyshevOf(transport.getDestination(), OFFSET), 100, 10000);
    }

    private static boolean handleGlider(Transport transport) {
        int TA_QUIR_PRIW = 9043972;
        int SINDARPOS = 9043975;
        int LEMANTO_ANDRA = 9043978;
        int KAR_HEWO = 9043981;
        int GANDIUS = 9043984;
        int OOKOOKOLLY_UNDRI = 9043993;
        int LEMANTOLLY_UNDRI = 9043989;

        // Get Transport Information
        String displayInfo = transport.getDisplayInfo();
        String npcName = transport.getName();
        String action = transport.getAction();

        final int GLIDER_PARENT_WIDGET = 138;
        final int GLIDER_CHILD_WIDGET = 0;

        // Check if the widget is already visible
        boolean isGliderMenuVisible = Rs2Widget.getWidget(GLIDER_PARENT_WIDGET, GLIDER_CHILD_WIDGET) != null;
        if (!isGliderMenuVisible) {
            // Find the glider NPC
            var gnome = Rs2Npc.getNpc(npcName);  // Use the NPC name to find the NPC
            if (gnome == null) {
                return false;
            }

            // Interact with the gnome glider NPC
            if (Rs2Npc.interact(gnome, action)) {
                sleepUntil(() -> !Rs2Widget.isHidden(GLIDER_PARENT_WIDGET, GLIDER_CHILD_WIDGET));
            }
        }


        // Wait for the widget to become visible
        boolean widgetVisible = sleepUntilTrue(() -> !Rs2Widget.isHidden(GLIDER_PARENT_WIDGET, GLIDER_CHILD_WIDGET), Rs2Player::isMoving, 100, 10000);

        if (!widgetVisible) {
            log.error("Widget did not become visible within the timeout.");
            return false;
        }

        if (displayInfo.isEmpty()) return false;

        switch (displayInfo) {
            case "Kar-Hewo":
                return Rs2Widget.clickWidget(KAR_HEWO);
            case "Ta Quir Priw":
                return Rs2Widget.clickWidget(TA_QUIR_PRIW);
            case "Sindarpos":
                return Rs2Widget.clickWidget(SINDARPOS);
            case "Lemanto Andra":
                return Rs2Widget.clickWidget(LEMANTO_ANDRA);
            case "Gandius":
                return Rs2Widget.clickWidget(GANDIUS);
            case "Ookookolly Undri":
                return Rs2Widget.clickWidget(OOKOOKOLLY_UNDRI);
            case "Lemantolly Undri":
                return Rs2Widget.clickWidget(LEMANTOLLY_UNDRI);
            default:
                log.error("{} not found on the interface.", displayInfo);
                return false;
        }
    }

    private static boolean handleFairyRing(Transport transport) {

        Rs2ItemModel startingWeapon = null;

        TileObject fairyRingObject = PohTeleports.isInHouse() ? PohTeleports.getFairyRings() : Rs2GameObject.getAll(o -> Objects.equals(o.getWorldLocation(), transport.getOrigin())).stream().findFirst().orElse(null);
        if (fairyRingObject == null) return false;

        if (!PohTeleports.isInHouse() && !Rs2GameObject.canWalkTo(fairyRingObject, 25)) return false;

        boolean hasLumbridgeElite = Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1;

        if (!hasLumbridgeElite) {
            if (Rs2Equipment.isWearing(EquipmentInventorySlot.WEAPON)) {
                startingWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
            }

            if (!Rs2Equipment.isWearing("Dramen staff") && !Rs2Equipment.isWearing("Lunar staff")) {
                if (Rs2Inventory.contains("Dramen staff")) {
                    Rs2Inventory.equip("Dramen staff");
                    sleepUntil(() -> Rs2Equipment.isWearing("Dramen staff"));
                } else if (Rs2Inventory.contains("Lunar staff")) {
                    Rs2Inventory.equip("Lunar staff");
                    sleepUntil(() -> Rs2Equipment.isWearing("Lunar staff"));
                } else {
                    return false;
                }
            }
        }

        String lastDestinationAction = "last-destination (" + transport.getDisplayInfo() + ")";
        String treeLastDestinationAction = "Ring-last-destination (" + transport.getDisplayInfo() + ")";
        ObjectComposition composition = Rs2GameObject.convertToObjectComposition(fairyRingObject);
        log.info("Interacting with Fairy Ring @ {}", fairyRingObject.getWorldLocation());

        // we can use the last-destination to handle fairy rings
        if (Rs2GameObject.hasAction(composition, lastDestinationAction, true)) {
            Rs2GameObject.interact(fairyRingObject, lastDestinationAction);
        } else if (Rs2GameObject.hasAction(composition, treeLastDestinationAction, true)) {
            Rs2GameObject.interact(fairyRingObject, treeLastDestinationAction);
        } else {
            // We have to configure fairy rings through the interface
            if (Rs2GameObject.hasAction(composition, "Configure", true)) {
                Rs2GameObject.interact(fairyRingObject, "Configure");
            } else if (Rs2GameObject.hasAction(composition, "Ring-configure", true)) {
                Rs2GameObject.interact(fairyRingObject, "Ring-configure");
            }
            sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Widget.isHidden(ComponentID.FAIRY_RING_TELEPORT_BUTTON), 10000);

            if (Rs2Widget.isHidden(ComponentID.FAIRY_RING_TELEPORT_BUTTON)) {
                log.warn("Fairy ring interface did not open (interrupted by combat?). Retrying next iteration.");
                return false;
            }

            Widget slotOne = Rs2Widget.getWidget(SLOT_ONE);
            Widget slotTwo = Rs2Widget.getWidget(SLOT_TWO);
            Widget slotThree = Rs2Widget.getWidget(SLOT_THREE);
            if (slotOne == null || slotTwo == null || slotThree == null) {
                log.warn("Fairy ring slot widget(s) are null; interface may have closed unexpectedly.");
                return false;
            }

            rotateSlotToDesiredRotation(SLOT_ONE, slotOne.getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(0)), SLOT_ONE_ACW_ROTATION, SLOT_ONE_CW_ROTATION);
            rotateSlotToDesiredRotation(SLOT_TWO, slotTwo.getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(1)), SLOT_TWO_ACW_ROTATION, SLOT_TWO_CW_ROTATION);
            rotateSlotToDesiredRotation(SLOT_THREE, slotThree.getRotationY(), getDesiredRotation(transport.getDisplayInfo().charAt(2)), SLOT_THREE_ACW_ROTATION, SLOT_THREE_CW_ROTATION);
            Rs2Widget.clickWidget(ComponentID.FAIRY_RING_TELEPORT_BUTTON);
        }

        sleepUntil(() -> Rs2Player.getGraphicId() == fairyRingGraphicId, 5000);
        sleepUntil(() -> Objects.equals(Rs2Player.getWorldLocation(), transport.getDestination()) && Rs2Player.getGraphicId() != fairyRingGraphicId, 10000);

        if (startingWeapon != null) {
            Rs2ItemModel finalStartingWeapon = startingWeapon;
            Rs2Inventory.equip(finalStartingWeapon.getId());
            sleepUntil(() -> Rs2Equipment.isWearing(finalStartingWeapon.getId()));
        }
        return true;
    }

    /**
     * Rotates a fairy ring slot to the desired rotation value.
     * Calculates the most efficient rotation direction (clockwise or anticlockwise)
     * and performs the necessary number of rotations to reach the target.
     *
     * @param slotId The widget ID of the slot to rotate
     * @param currentRotation The current rotation value of the slot
     * @param desiredRotation The target rotation value to achieve
     * @param slotAcwRotationId The widget ID for anticlockwise rotation button
     * @param slotCwRotationId The widget ID for clockwise rotation button
     */
    private static void rotateSlotToDesiredRotation(int slotId, int currentRotation, int desiredRotation, int slotAcwRotationId, int slotCwRotationId) {
        int anticlockwiseTurns = (desiredRotation - currentRotation + 2048) % 2048;
        int clockwiseTurns = (currentRotation - desiredRotation + 2048) % 2048;

        int turns = Math.min(clockwiseTurns, anticlockwiseTurns) / 512;
        boolean rotateCW = clockwiseTurns <= anticlockwiseTurns;
        int rotationWidget = rotateCW ? slotCwRotationId : slotAcwRotationId;

        for (int i = 0; i < turns; i++) {
            final int previousRotation = currentRotation;
            Rs2Widget.clickWidget(rotationWidget);

            sleepUntil(() -> {
                Widget slotWidget = Rs2Widget.getWidget(slotId);
                return slotWidget != null && slotWidget.getRotationY() != previousRotation;
            }, 2000);

            Widget slotWidget = Rs2Widget.getWidget(slotId);
            if (slotWidget != null) {
                currentRotation = slotWidget.getRotationY();
            } else {
                break;
            }
        }

        sleepUntil(() -> {
            Widget slotWidget = Rs2Widget.getWidget(slotId);
            return slotWidget != null && slotWidget.getRotationY() == desiredRotation;
        }, 3000);
    }

    /**
     * Maps fairy ring letters to their corresponding rotation values.
     * Each letter corresponds to a specific rotation degree needed for fairy ring teleportation.
     *
     * @param letter The fairy ring letter (A-Z) to get rotation for
     * @return The rotation value (0, 512, 1024, or 1536) for the letter, or -1 if invalid
     */
    private static int getDesiredRotation(char letter) {
        switch (letter) {
            case 'A':
            case 'I':
            case 'P':
                return 0;
            case 'B':
            case 'J':
            case 'Q':
                return 512;
            case 'C':
            case 'K':
            case 'R':
                return 1024;
            case 'D':
            case 'L':
            case 'S':
                return 1536;
            default:
                return -1;
        }
    }
}
