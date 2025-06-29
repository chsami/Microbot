package net.runelite.client.plugins.microbot.magic.aiomagic.scripts;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.magic.aiomagic.AIOMagicPlugin;
import net.runelite.client.plugins.microbot.magic.orbcharger.OrbChargerPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class SplashScript extends Script {

	private final AIOMagicPlugin plugin;
	
	@Inject
	public SplashScript(AIOMagicPlugin plugin) {
		this.plugin = plugin;
	}

	public boolean run() {
		Microbot.enableAutoRunOn = false;
		Rs2Antiban.resetAntibanSettings();
		Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
		Rs2AntibanSettings.simulateAttentionSpan = true;
		Rs2AntibanSettings.nonLinearIntervals = true;
		Rs2AntibanSettings.contextualVariability = true;
		Rs2AntibanSettings.usePlayStyle = true;
		Rs2AntibanSettings.moveMouseOffScreen = true;
		Rs2AntibanSettings.moveMouseOffScreenChance = 1.0;
		Rs2Antiban.setActivity(Activity.SPLASHING);
		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
			try {
				if (!Microbot.isLoggedIn()) return;
				if (!super.run()) return;
				long startTime = System.currentTimeMillis();

				if (Rs2Magic.getCurrentAutoCastSpell() != plugin.getCombatSpell()) {
					Rs2Combat.setAutoCastSpell(plugin.getCombatSpell(), false);
					return;
				}

				if (Rs2Player.isMoving() || Rs2Combat.inCombat() || Microbot.pauseAllScripts) return;
				if (Rs2AntibanSettings.actionCooldownActive) return;

				if (Rs2Npc.attack(plugin.getNpcName())) {
					Rs2Antiban.actionCooldown();
				}

				long endTime = System.currentTimeMillis();
				long totalTime = endTime - startTime;
				System.out.println("Total time for loop " + totalTime);

			} catch (Exception ex) {
				System.out.println(ex.getMessage());
			}
		}, 0, 1000, TimeUnit.MILLISECONDS);
		return true;
	}

	@Override
	public void shutdown() {
		Rs2Antiban.resetAntibanSettings();
		super.shutdown();
	}
}
