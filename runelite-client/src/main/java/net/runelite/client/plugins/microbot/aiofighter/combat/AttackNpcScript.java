package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.api.Actor;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyle;
import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyleMapper;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.util.ActorModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AttackNpcScript extends Script {

    public static Actor currentNpc = null;
    public static List<Rs2NpcModel> filteredAttackableNpcs = new ArrayList<>();
    private boolean messageShown = false;

    public static void skipNpc() {
        currentNpc = null;
    }

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

                if(config.state().equals(State.BANKING) || config.state().equals(State.WALKING))
                    return;

                List<String> npcsToAttack = Arrays.stream(config.attackableNpcs().split(","))
                        .map(x -> x.trim().toLowerCase())
                        .collect(Collectors.toList());

                double healthPercentage = (double) Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) * 100
                        / Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
                if (Rs2Inventory.getInventoryFood().isEmpty() && healthPercentage < 10)
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

                filteredAttackableNpcs = Rs2Npc.getAttackableNpcs(config.attackReachableNpcs())
                        .filter(npc -> npc.getWorldLocation().distanceTo(config.centerLocation()) <= config.attackRadius())
                        .sorted(Comparator.comparingInt((Rs2NpcModel npc) -> npc.getInteracting() == Microbot.getClient().getLocalPlayer() ? 0 : 1)
                                .thenComparingInt(npc -> Rs2Player.getRs2WorldPoint().distanceToPath(npc.getWorldLocation())))
                        .collect(Collectors.toList());

                final List<Rs2NpcModel> attackableNpcs = new ArrayList<>();

                for (var attackableNpc: filteredAttackableNpcs) {
                    if (attackableNpc == null || attackableNpc.getName() == null) continue;
                    for (var npcToAttack: npcsToAttack) {
                        if (npcToAttack.equalsIgnoreCase(attackableNpc.getName())) {
                            attackableNpcs.add(attackableNpc);
                        }
                    }
                }

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
                    sleepUntil(Rs2Player::isInteracting, 1000);

                    if (config.togglePrayer()) {
                        if (!config.toggleQuickPray()) {
                            AttackStyle attackStyle = AttackStyleMapper
                                    .mapToAttackStyle(Rs2NpcManager.getAttackStyle(npc.getId()));
                            if (attackStyle != null) {
                                switch (attackStyle) {
                                    case MAGE:
                                        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
                                        break;
                                    case MELEE:
                                        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
                                        break;
                                    case RANGED:
                                        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
                                        break;
                                }
                            }
                        } else {
                            Rs2Prayer.toggleQuickPrayer(true);
                        }
                    }


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
        if (npc == null) return;
        if (npc.getName().equalsIgnoreCase("desert lizard") && npc.getHealthRatio() < 5) {
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

    public void shutdown() {
        super.shutdown();
    }
}
