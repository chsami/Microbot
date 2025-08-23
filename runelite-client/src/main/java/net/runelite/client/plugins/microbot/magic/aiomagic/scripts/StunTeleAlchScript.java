package net.runelite.client.plugins.microbot.magic.aiomagic.scripts;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.magic.aiomagic.AIOMagicPlugin;
import net.runelite.client.plugins.microbot.magic.aiomagic.enums.MagicState;
import net.runelite.client.plugins.microbot.magic.aiomagic.enums.TeleportSpell;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import javax.inject.Inject;
import java.util.concurrent.TimeUnit;
public class StunTeleAlchScript extends Script {
    private MagicState state = MagicState.CASTING;
    private final AIOMagicPlugin plugin;
    // HARD-LOCKS
    private static final String FIXED_TARGET_NPC = "Guard";
    private static final TeleportSpell FIXED_TELEPORT = TeleportSpell.ARDOUGNE_TELEPORT;
    @Inject
    public StunTeleAlchScript(AIOMagicPlugin plugin) {
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
        Rs2Antiban.setActivity(Activity.TELEPORT_TRAINING);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();
                switch (state) {
                    case CASTING:
                        // Ensure we have an alch item present
                        if (plugin.getAlchItemNames().isEmpty()
                                || plugin.getAlchItemNames().stream().noneMatch(Rs2Inventory::hasItem)) {
                            Microbot.log("Missing alch items...");
                            return;
                        }
                        // Ensure we can alch
                        if (!Rs2Magic.hasRequiredRunes(plugin.getAlchSpell())) {
                            Microbot.log("Unable to cast alchemy spell");
                            return;
                        }
                        // Resolve the alch item
                        Rs2ItemModel alchItem = plugin.getAlchItemNames().stream()
                                .filter(Rs2Inventory::hasItem)
                                .map(Rs2Inventory::get)
                                .findFirst()
                                .orElse(null);
                        if (alchItem == null) {
                            Microbot.log("Missing alch items...");
                            return;
                        }
                        // Optional inventory slot normalization for natural mouse
                        if (Rs2AntibanSettings.naturalMouse) {
                            int inventorySlot = Rs2Player.getSkillRequirement(Skill.MAGIC, 55) ? 11 : 4;
                            if (alchItem.getSlot() != inventorySlot) {
                                Rs2Inventory.moveItemToSlot(alchItem, inventorySlot);
                                return;
                            }
                        }
                        // 1) STUN: always the hard-locked target
                        Rs2NpcModel target = Rs2Npc.getNpc(FIXED_TARGET_NPC);
                        if (target == null) {
                            Microbot.log("Unable to find NPC: " + FIXED_TARGET_NPC);
                            return;
                        }
                        Rs2Magic.castOn(plugin.getStunSpell().getRs2Spell().getMagicAction(), target);
                        // 2) ALCH
                        if (Rs2AntibanSettings.naturalMouse) {
                            Rs2Magic.alch(alchItem, 10, 50);
                        } else {
                            Rs2Magic.alch(alchItem);
                            sleep(200, 300);
                        }
                        // 3) TELEPORT: always the hard-locked teleport
                        Rs2Magic.cast(FIXED_TELEPORT.getRs2Spell().getMagicAction());
                        sleep(100, 200);
                        break;
                }
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }
    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}

