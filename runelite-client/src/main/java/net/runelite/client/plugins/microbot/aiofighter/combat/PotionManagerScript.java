package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

public class PotionManagerScript extends Script {
    public boolean run(AIOFighterConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (config.useAntiPoison()) {
                    if(Rs2Player.drinkAntiPoisonPotion())
                        Rs2Player.waitForAnimation();
                }
                if (config.useAntifirePotion())
                {
                    if (Rs2Player.drinkAntiFirePotion())
                        Rs2Player.waitForAnimation();
                }
                if (config.togglePrayerPotions()) {
                    Rs2Player.drinkPrayerPotionAt(Rs2Random.randomGaussian(25,10));
                }
                if (config.toggleRangingPotion()) {
                    if(Rs2Player.drinkCombatPotionAt(Skill.RANGED, false))
                        Rs2Player.waitForAnimation();
                }
                if (config.toggleMagicPotion()) {
                    if (Rs2Player.drinkCombatPotionAt(Skill.MAGIC, false))
                        Rs2Player.waitForAnimation();
                }
                if (config.toggleCombatPotion()) {
                    if(Rs2Player.drinkCombatPotionAt(Skill.STRENGTH))
                        Rs2Player.waitForAnimation();
                    if (Rs2Player.drinkCombatPotionAt(Skill.ATTACK))
                        Rs2Player.waitForAnimation();
                    if (Rs2Player.drinkCombatPotionAt(Skill.DEFENCE))
                        Rs2Player.waitForAnimation();
                }

                if (config.useGoadingPotion()) {
                    if (Rs2Player.drinkGoadingPotion())
                        Rs2Player.waitForAnimation();
                }

            } catch(Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }
}
