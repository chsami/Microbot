package net.runelite.client.plugins.microbot.jad;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class JadScript extends Script {
    public static final String VERSION = "1.0.4";
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

                    if (npcAnimation == 7592 || npcAnimation == 2656) {
                        handleHealerInteraction("hurkot", Rs2PrayerEnum.PROTECT_MAGIC);
                        npcAttackCooldowns.put(npcIndex, currentTimeMillis);
                    } else if (npcAnimation == 7593 || npcAnimation == 2652) {
                        handleHealerInteraction("hurkot", Rs2PrayerEnum.PROTECT_RANGE);
                        npcAttackCooldowns.put(npcIndex, currentTimeMillis);
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 10, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleHealerInteraction(String healerName, Rs2PrayerEnum prayer) {
        var healer = Rs2Npc.getNpcs(healerName, false)
                .filter(npc -> npc != null && npc.getInteracting() != Microbot.getClient().getLocalPlayer())
                .findFirst()
                .orElse(null);

        if (healer != null) {
            Rs2Npc.interact(healer, "attack");
            sleep(600);
            Rs2Prayer.toggle(prayer, true);
        } else {
            if (Microbot.getClient().getLocalPlayer().getInteracting() == null || Microbot.getClient().getLocalPlayer().getInteracting() != null && Microbot.getClient().getLocalPlayer().getInteracting().getName().contains(healerName)) {
                Rs2Npc.interact(Rs2Npc.getNpc("Jad", false), "attack");
            }
            Rs2Prayer.toggle(prayer, true);
        }

    }

    @Override
    public void shutdown() {
        super.shutdown();
        npcAttackCooldowns.clear();
    }
}