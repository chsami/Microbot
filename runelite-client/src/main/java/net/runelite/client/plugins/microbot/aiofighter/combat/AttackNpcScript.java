package net.runelite.client.plugins.microbot.aiofighter.combat;

import lombok.SneakyThrows;
import net.runelite.api.Actor;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.ActorModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class AttackNpcScript extends Script {

    public static Actor currentNpc = null;
    public static AtomicReference<List<Rs2NpcModel>> filteredAttackableNpcs = new AtomicReference<>(new ArrayList<>());
    public static Rs2WorldArea attackableArea = null;
    private boolean messageShown = false;

    public static void skipNpc() {
        currentNpc = null;
    }

    @SneakyThrows
    public void run(AIOFighterConfig config) {
        try {
            Rs2NpcManager.loadJson();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || !config.toggleCombat())
                    return;

                if(config.centerLocation().distanceTo(Rs2Player.getWorldLocation()) < config.attackRadius() &&
                        !config.centerLocation().equals(new WorldPoint(0, 0, 0)) &&  AIOFighterPlugin.getState() != State.BANKING) {
                    if(ShortestPathPlugin.getPathfinder() != null)
                        Rs2Walker.setTarget(null);
                    AIOFighterPlugin.setState(State.IDLE);
                }

                attackableArea = new Rs2WorldArea(config.centerLocation().toWorldArea());
                attackableArea = attackableArea.offset(config.attackRadius());
                List<String> npcsToAttack = Arrays.stream(config.attackableNpcs().split(","))
                        .map(x -> x.trim().toLowerCase())
                        .collect(Collectors.toList());

                filteredAttackableNpcs.set(
                        Rs2Npc.getAttackableNpcs(config.attackReachableNpcs())
                                .filter(npc -> npc.getWorldLocation().distanceTo(config.centerLocation()) <= config.attackRadius())
                                .filter(npc -> {
                                    String name = npc.getName();
                                    if (name == null || name.isEmpty()) return false;
                                    return !npcsToAttack.isEmpty() && npcsToAttack.stream().anyMatch(name::equalsIgnoreCase);
                                })
                                .sorted(Comparator.comparingInt((Rs2NpcModel npc) -> Objects.equals(npc.getInteracting(), Microbot.getClient().getLocalPlayer()) ? 0 : 1)
                                        .thenComparingInt(npc -> Rs2Player.getRs2WorldPoint().distanceToPath(npc.getWorldLocation())))
                                .collect(Collectors.toList())
                );
                final List<Rs2NpcModel> attackableNpcs = new ArrayList<>();

                for (var attackableNpc: filteredAttackableNpcs.get()) {
                    if (attackableNpc == null || attackableNpc.getName() == null) continue;
                    for (var npcToAttack: npcsToAttack) {
                        if (npcToAttack.equalsIgnoreCase(attackableNpc.getName())) {
                            attackableNpcs.add(attackableNpc);
                        }
                    }
                }

                filteredAttackableNpcs.set(attackableNpcs);

                if(config.state().equals(State.BANKING) || config.state().equals(State.WALKING))
                    return;

                if (config.toggleCenterTile() && config.centerLocation().getX() == 0
                        && config.centerLocation().getY() == 0) {
                    if (!messageShown) {
                        Microbot.showMessage("Please set a center location");
                        messageShown = true;
                    }
                    return;
                }
                messageShown = false;

                if (AIOFighterPlugin.getCooldown() > 0 || Rs2Combat.inCombat()) {
                    AIOFighterPlugin.setState(State.COMBAT);
                    handleItemOnNpcToKill();
                    return;
                }

                if (!attackableNpcs.isEmpty()) {
                    Rs2NpcModel npc = attackableNpcs.stream().findFirst().orElse(null);

                    if (!Rs2Camera.isTileOnScreen(npc.getLocalLocation()))
                        Rs2Camera.turnTo(npc);

                    Rs2Npc.interact(npc, "attack");
                    Microbot.status = "Attacking " + npc.getName();
                    AIOFighterPlugin.setCooldown(config.playStyle().getRandomTickInterval());

                } else {
                    Microbot.log("No attackable NPC found");
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
    }


    /**
     * item on npcs that need to kill like rockslug
     */
    private void handleItemOnNpcToKill() {
        Rs2NpcModel npc = Rs2Npc.getNpcsForPlayer(ActorModel::isDead).findFirst().orElse(null);
        List<String> lizardVariants = new ArrayList<>(Arrays.asList("Lizard", "Desert Lizard", "Small Lizard"));
        if (npc == null) return;
        if (lizardVariants.contains(npc.getName()) && npc.getHealthRatio() < 5) {
            Rs2Inventory.useItemOnNpc(ItemID.ICE_COOLER, npc);
            Rs2Player.waitForAnimation();
        } else if (npc.getName().equalsIgnoreCase("rockslug") && npc.getHealthRatio() < 5) {
            Rs2Inventory.useItemOnNpc(ItemID.BAG_OF_SALT, npc);
            Rs2Player.waitForAnimation();
        } else if (npc.getName().equalsIgnoreCase("gargoyle") && npc.getHealthRatio() < 3) {
            Rs2Inventory.useItemOnNpc(ItemID.ROCK_HAMMER, npc);
            Rs2Player.waitForAnimation();
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}