package net.runelite.client.plugins.microbot.util.npc;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.npcoverlay.HighlightedNpc;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Rs2Npc {

    public static NPC getNpcByIndex(int index) {
        return Microbot.getClient().getNpcs().stream()
                .filter(x -> x.getIndex() == index)
                .findFirst()
                .orElse(null);
    }

    public static NPC validateInteractable(NPC npc) {
        if (npc != null) {
            Rs2Walker.walkTo(npc.getWorldLocation());
            Rs2Camera.turnTo(npc);
            return npc;
        }
        return null;
    }

    public static List<NPC> getNpcsForPlayer() {
        return Microbot.getClient().getNpcs().stream()
                .filter(x -> x.getInteracting() == Microbot.getClient().getLocalPlayer())
                .sorted(Comparator
                        .comparingInt(value -> value
                                .getLocalLocation()
                                .distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation())))
                .collect(Collectors.toList());
    }

    public static List<NPC> getNpcsForPlayer(String name) {
        List<NPC> npcs = Microbot.getClient().getNpcs().stream()
                .filter(x -> x.getInteracting() == Microbot.getClient().getLocalPlayer() && x.getName().equalsIgnoreCase(name))
                .sorted(Comparator
                        .comparingInt(value -> value
                                .getLocalLocation()
                                .distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation())))
                .collect(Collectors.toList());

        return npcs;
    }

    public static double getHealth(Actor npc) {
        int ratio = npc.getHealthRatio();
        int scale = npc.getHealthScale();

        double targetHpPercent = (double) ratio / (double) scale * 100;

        return targetHpPercent;
    }

    /**
     * @return
     */
    public static Stream<NPC> getNpcs() {
        Stream<NPC> npcs = Microbot.getClientThread().runOnClientThread(() -> Microbot.getClient().getNpcs().stream()
                .filter(x -> x != null && x.getName() != null && !x.isDead())
                .sorted(Comparator.comparingInt(value -> value.getLocalLocation()
                        .distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation()))));

        return npcs;
    }

    /**
     * @param name
     *
     * @return
     */
    public static Stream<NPC> getNpcs(String name) {
        return getNpcs(name, true);
    }

    /**
     * @param name
     * @param exact
     *
     * @return
     */
    public static Stream<NPC> getNpcs(String name, boolean exact) {
        Stream<NPC> npcs = getNpcs();

        if (exact) {
            npcs = npcs.filter(x -> x.getName().equalsIgnoreCase(name));
        } else {
            npcs = npcs.filter(x -> x.getName().toLowerCase().contains(name.toLowerCase()));
        }

        return npcs;
    }

    /**
     * @param id
     *
     * @return
     */
    public static Stream<NPC> getNpcs(int id) {
        return getNpcs().filter(x -> x.getId() == id);
    }

    public static Stream<NPC> getAttackableNpcs() {
        Stream<NPC> npcs = Microbot.getClient().getNpcs().stream()
                .filter((npc) -> npc.getCombatLevel() > 0 && !npc.isDead())
                .sorted(Comparator.comparingInt(value -> value.getLocalLocation().distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation())));
        if (!Rs2Player.isInMulti()) {
            npcs = npcs.filter((npc) -> !npc.isInteracting());
        }
        return npcs;
    }

    public static Stream<NPC> getAttackableNpcs(String name) {
        return getAttackableNpcs()
                .filter(x -> x.getName().equalsIgnoreCase(name));
    }

    public static NPC[] getPestControlPortals() {
        List<NPC> npcs = Microbot.getClient().getNpcs().stream()
                .filter((npc) -> !npc.isDead() && npc.getHealthRatio() > 0 && npc.getName().equalsIgnoreCase("portal"))
                .sorted(Comparator.comparingInt(value -> value.getLocalLocation().distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation())))
                .collect(Collectors.toList());

        return npcs.toArray(new NPC[npcs.size()]);
    }

    public static NPC getNpc(String name) {
        return getNpc(name, true);
    }

    public static NPC getNpc(String name, boolean exact) {
        return getNpcs(name, exact)
                .findFirst()
                .orElse(null);
    }

    public static NPC getNpc(int id) {
        return getNpcs()
                .filter(x -> x.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public static Optional<NPC> getNpc(int id, List<Integer> excludedIndexes) {
        return getNpcs()
                .filter(x -> x != null && x.getId() == id && !excludedIndexes.contains(x.getIndex()))
                .min(Comparator.comparingInt(value ->
                        value.getLocalLocation().distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation())));
    }

    public static NPC getRandomEventNPC() {
        return getNpcs()
                .filter(value -> (value.getComposition() != null && value.getComposition().getActions() != null &&
                        Arrays.asList(value.getComposition().getActions()).contains("Dismiss")) && value.getInteracting() == Microbot.getClient().getLocalPlayer())
                .findFirst()
                .orElse(null);
    }

    public static NPC getBankerNPC() {
        return getNpcs()
                .filter(value -> (value.getComposition() != null && value.getComposition().getActions() != null &&
                        Arrays.asList(value.getComposition().getActions()).contains("Bank")))
                .findFirst()
                .orElse(null);
    }

    public static boolean interact(NPC npc, String action) {
        if (npc == null) return false;
        Microbot.status = action + " " + npc.getName();
        try {
            NPCComposition npcComposition = Microbot.getClientThread().runOnClientThread(() -> Microbot.getClient().getNpcDefinition(npc.getId()));

            int index = 0;
            for (int i = 0; i < npcComposition.getActions().length; i++) {
                String npcAction = npcComposition.getActions()[i];
                if (npcAction == null || !npcAction.equalsIgnoreCase(action)) continue;
                index = i;
            }

            MenuAction menuAction = getMenuAction(index);

            if (menuAction != null) {
                Microbot.doInvoke(new NewMenuEntry(0, 0, menuAction.getId(), npc.getIndex(), -1, npc.getName(), npc), Rs2UiHelper.getActorClickbox(npc));
            }

        } catch (Exception ex) {
            Microbot.log(ex.getMessage());
        }

        return true;
    }

    @Nullable
    private static MenuAction getMenuAction(int index) {
        MenuAction menuAction = null;

        if (Microbot.getClient().isWidgetSelected()) {
            menuAction = MenuAction.WIDGET_TARGET_ON_NPC;
        } else if (index == 0) {
            menuAction = MenuAction.NPC_FIRST_OPTION;
        } else if (index == 1) {
            menuAction = MenuAction.NPC_SECOND_OPTION;
        } else if (index == 2) {
            menuAction = MenuAction.NPC_THIRD_OPTION;
        } else if (index == 3) {
            menuAction = MenuAction.NPC_FOURTH_OPTION;
        } else if (index == 4) {
            menuAction = MenuAction.NPC_FIFTH_OPTION;
        }
        return menuAction;
    }

    public static boolean interact(NPC npc) {
        return interact(npc, "");
    }

    public static boolean interact(int id) {
        return interact(id, "");
    }

    public static boolean interact(int npcId, String action) {
        NPC npc = getNpc(npcId);

        return interact(npc, action);
    }

    public static boolean attack(NPC npc) {
        if (npc == null) return false;
        if (!hasLineOfSight(npc)) return false;
        if (Rs2Combat.inCombat()) return false;
        if (npc.isInteracting() && npc.getInteracting() != Microbot.getClient().getLocalPlayer() && !Rs2Player.isInMulti())
            return false;

        return interact(npc, "attack");
    }

    public static boolean attack(int npcId) {
        NPC npc = getNpc(npcId);
        return attack(npc);
    }

    public static boolean attack(String npcName) {
        return attack(Collections.singletonList(npcName));
    }

    public static boolean attack(List<String> npcNames) {
        for (String npcName : npcNames) {
            NPC npc = getNpc(npcName);
            if (npc == null) continue;
            if (!hasLineOfSight(npc)) continue;
            if (Rs2Combat.inCombat()) continue;
            if (npc.isInteracting() && npc.getInteracting() != Microbot.getClient().getLocalPlayer() && !Rs2Player.isInMulti())
                continue;

            return interact(npc, "attack");
        }
        return false;
    }

    public static boolean interact(String npcName, String action) {
        NPC npc = getNpc(npcName);

        return interact(npc, action);
    }

    public static boolean pickpocket(String npcName) {
        NPC npc = getNpc(npcName);

        if (npc == null) return false;

        if (!hasLineOfSight(npc)) {
            Rs2Walker.walkTo(npc.getWorldLocation(), 1);
            return false;
        }

        return interact(npc, "pickpocket");
    }

    public static boolean pickpocket(Map<NPC, HighlightedNpc> highlightedNpcs) {
        for (NPC npc : highlightedNpcs.keySet()) {
            if (!hasLineOfSight(npc)) {
                Rs2Walker.walkTo(npc.getWorldLocation(), 1);
                return false;
            }
            return interact(npc, "pickpocket");
        }
        return false;
    }

    public static boolean pickpocket(NPC npc) {
        return interact(npc, "pickpocket");
    }

    public static boolean hasLineOfSight(NPC npc) {
        if (npc == null) return false;
        return new WorldArea(
                npc.getWorldLocation(),
                npc.getComposition().getSize(),
                npc.getComposition().getSize())
                .hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), Microbot.getClient().getLocalPlayer().getWorldLocation().toWorldArea());
    }

    public static WorldPoint getWorldLocation(NPC npc) {
        if (Microbot.getClient().isInInstancedRegion()) {
            LocalPoint l = LocalPoint.fromWorld(Microbot.getClient(), npc.getWorldLocation());
            WorldPoint npcInstancedWorldLocation = WorldPoint.fromLocalInstance(Microbot.getClient(), l);
            return npcInstancedWorldLocation;
        } else {
            return npc.getWorldLocation();
        }
    }

    public static boolean canWalkTo(NPC npc, int distance) {
        if (npc == null) return false;
        var location = getWorldLocation(npc);

        var tiles = Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), distance);
        for (var tile : tiles.keySet()) {
            if (tile.equals(location))
                return true;
        }

        var localLocation = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), location);
        if (localLocation != null && !Rs2Tile.isWalkable(localLocation))
            return tiles.keySet().stream().anyMatch(x -> x.distanceTo(location) < 2);

        return false;
    }

    /**
     * @param player
     *
     * @return
     */
    public static List<NPC> getNpcsAttackingPlayer(Player player) {
        return getNpcs().filter(x -> x.getInteracting() != null && x.getInteracting() == player).collect(Collectors.toList());
    }

    /**
     * gets list of npcs within line of sight for a player by name
     *
     * @param name of the npc
     *
     * @return list of npcs
     */
    public static List<NPC> getNpcsInLineOfSight(String name) {
        return getNpcs().filter(npc -> hasLineOfSight(npc) && npc.getName().equalsIgnoreCase(name)).collect(Collectors.toList());
    }

    /**
     * gets the npc within line of sight for a player by name
     *
     * @param name of the npc
     *
     * @return npc
     */
    public static NPC getNpcInLineOfSight(String name) {
        List<NPC> npcsInLineOfSight = getNpcsInLineOfSight(name);
        if (npcsInLineOfSight.isEmpty()) return null;

        return npcsInLineOfSight.get(0);
    }

    /**
     * Hovers over the given actor (e.g., NPC).
     *
     * @param actor The actor to hover over.
     *
     * @return True if successfully hovered, otherwise false.
     */
    public static boolean hoverOverActor(Actor actor) {
        if (!Rs2AntibanSettings.naturalMouse) {
            Microbot.log("Natural mouse is not enabled, can't hover");
            return false;
        }
        Point point = Rs2UiHelper.getClickingPoint(Rs2UiHelper.getActorClickbox(actor), true);
        if (point.getX() == 1 && point.getY() == 1) {
            return false;
        }
        Microbot.getNaturalMouse().moveTo(point.getX(), point.getY());
        return true;
    }
}