package net.runelite.client.plugins.microbot.jad;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class JadScript extends Script {
    public static final String VERSION = "1.0.5";
    public static final Map<Integer, Long> npcAttackCooldowns = new HashMap<>();

    public boolean run(JadConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                var jadNpcs = Rs2Npc.getNpcs("Jad", false);

                for (Rs2NpcModel jadNpc : jadNpcs.collect(Collectors.toList())) {
                    if (jadNpc == null) continue;

                    long currentTimeMillis = System.currentTimeMillis();
                    int npcIndex = jadNpc.getIndex();

                    if (npcAttackCooldowns.containsKey(npcIndex)) {
                        if (currentTimeMillis - npcAttackCooldowns.get(npcIndex) < 4600) {
                            continue;
                        } else {
                            npcAttackCooldowns.remove(npcIndex);
                        }
                    }

                    int npcAnimation = Rs2Reflection.getAnimation(jadNpc);
                    handleJadPrayer(npcAnimation);
                    if (config.shouldAttackHealers()) {
                        handleHealerInteraction();
                        npcAttackCooldowns.put(npcIndex, currentTimeMillis);
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 10, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleHealerInteraction() {
        var healer = Rs2Npc.getNpcs("hurkot", false)
                .filter(npc -> npc != null && npc.getInteracting() != Microbot.getClient().getLocalPlayer())
                .findFirst()
                .orElse(null);

        if (healer != null) {
            Rs2Npc.interact(healer, "attack");
        } else {
            var npc = Rs2Player.getInteracting();
            if (npc == null || npc != null && npc.getName().contains("hurkot")) {
                Rs2Npc.interact(Rs2Npc.getNpc("Jad", false), "attack");
            }
        }

    }

    @Override
    public void shutdown() {
        super.shutdown();
        npcAttackCooldowns.clear();
    }

    private void handleJadPrayer(int animationId) {
        if (animationId == 7592 || animationId == 2656) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        } else if (animationId == 7593 || animationId == 2652) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
        }
    }
}