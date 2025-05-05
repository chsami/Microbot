package net.runelite.client.plugins.microbot.TaF.RoyalTitans;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.TaF.RoyalTitans.RoyalTitansShared.*;

public class RoyalTitansLooterScript extends Script {
    @Inject
    public RoyalTitansScript royalTitansScript;
    public String itemsToLoot = "Giantsoul amulet,Fire element staff crown,Ice element staff crown,Mystic vigour prayer scroll,Deadeye prayer scroll,Mystic fire staff,Mystic water staff,Fire battlestaff,Water battlestaff,Rune plateskirt,Rune platelegs,Rune scimitar,Rune pickaxe,Rune sq shield,Rune axe,Chaos rune,Death rune,Nature rune,Law rune,Soul rune,Blood rune,Rune arrow,Fire rune,Water rune,Gold ore,Fire orb,Water orb,Coal,Grimy avantoe,Grimy cadantine,Grimy dwarf weed,Grimy irit leaf,Grimy kwuarm,Grimy lantadyme,Grimy ranarr weed,Avantoe seed,Cadantine seed,Dwarf weed seed,Irit seed,Kwuarm seed,Lantadyme seed,Ranarr seed,Maple seed,Palm tree seed,Yew seed,Coins,Prayer potion(4),Desiccated page,Clue scroll (hard),Clue scroll (elite),Bran";
    int minFreeSlots = 0;
    private RoyalTitansConfig.RoyalTitan LootedTitan = null;
    private Instant lastLootTime = Instant.now();

    public RoyalTitansLooterScript() {

    }

    public boolean run(RoyalTitansConfig config, RoyalTitansScript script) {
        royalTitansScript = script;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (royalTitansScript.state.equals(RoyalTitansBotStatus.BANKING) || royalTitansScript.state.equals(RoyalTitansBotStatus.TRAVELLING) || royalTitansScript.state.equals(RoyalTitansBotStatus.WAITING))
                    return;
                if (!isInBossRegion()) return;
                var iceTitanDead = Rs2Npc.getNpcs(ICE_TITAN_DEAD_ID).findFirst().orElse(null);
                var fireTitanDead = Rs2Npc.getNpcs(FIRE_TITAN_DEAD_ID).findFirst().orElse(null);
                var iceTitan = Rs2Npc.getNpcs(ICE_TITAN_ID).findFirst().orElse(null);
                var fireTitan = Rs2Npc.getNpcs(FIRE_TITAN_ID).findFirst().orElse(null);
                boolean looted = false;
                // Only loot when the giants are dead to not obstruct the fight
                if (iceTitan != null && !iceTitan.isDead() || fireTitan != null && !fireTitan.isDead()) {
                    return;
                }
                // Both titans are dead and we have looted - Check ground for items
                if (fireTitanDead != null && iceTitanDead != null) {
                    royalTitansScript.subState = "Looting ground items";
                    lootItemsOnName(config);
                    lootRunes(config);
                    lootCoins(config);
                    lootUntradeableItems(config);
                }
                if (LootedTitanLastIteration) {
                    if (lastLootTime.plusSeconds(30).isBefore(Instant.now())) {
                        // If 30 seconds have passed, reset the flag and try looting again
                        LootedTitanLastIteration = false;
                        Microbot.log("30 seconds passed since last loot - trying again");
                    } else {
                        // Less than 30 seconds have passed since last loot, skip looting
                        return;
                    }
                }

                royalTitansScript.subState = "Handling looting from Titans";
                switch (config.loot()) {
                    case ICE_TITAN:
                        looted = lootTitan(iceTitanDead);
                        break;
                    case FIRE_TITAN:
                        looted = lootTitan(fireTitanDead);
                        break;
                    case ALTERNATE:
                        if (LootedTitan == null) {
                            LootedTitan = RoyalTitansConfig.RoyalTitan.ICE_TITAN;
                        } else if (LootedTitan == RoyalTitansConfig.RoyalTitan.ICE_TITAN) {
                            LootedTitan = RoyalTitansConfig.RoyalTitan.FIRE_TITAN;
                        } else {
                            LootedTitan = RoyalTitansConfig.RoyalTitan.ICE_TITAN;
                        }
                        if (LootedTitan == RoyalTitansConfig.RoyalTitan.ICE_TITAN) {
                            looted = lootTitan(fireTitanDead);
                        } else {
                            looted = lootTitan(iceTitanDead);
                        }
                        break;
                    case RANDOM:
                        if (Math.random() < 0.5) {
                            looted = lootTitan(iceTitanDead);
                        } else {
                            looted = lootTitan(fireTitanDead);
                        }
                        break;
                }
                if (looted && !LootedTitanLastIteration) {
                    Rs2Player.waitForAnimation(2400);
                    royalTitansScript.kills++;
                    LootedTitanLastIteration = true;
                    lastLootTime = Instant.now();
                    evaluateAndConsumePotions(config);
                    Microbot.log("Looted the titans");
                }
                // Animation: 829 for looting

            } catch (Exception ex) {
                System.out.println("Royal Titan Looter: " + ex.getMessage());
                ex.printStackTrace();
            }

        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    private static boolean lootTitan(Rs2NpcModel iceTitanDead) {
        Rs2Npc.interact(iceTitanDead, "Loot");
        sleepUntil(() -> !Rs2Player.isMoving(), 3200);
        var looted = Rs2Npc.interact(iceTitanDead, "Loot");
        Rs2Player.waitForAnimation(1800);
        return looted;
    }


    private void lootUntradeableItems(RoyalTitansConfig config) {
        LootingParameters untradeableItemsParams = new LootingParameters(
                15,
                1,
                1,
                minFreeSlots,
                false,
                false,
                "untradeable"
        );
        if (Rs2GroundItem.lootUntradables(untradeableItemsParams)) {
            Microbot.pauseAllScripts = false;
        }
    }

    private void lootRunes(RoyalTitansConfig config) {
        LootingParameters runesParams = new LootingParameters(
                15,
                1,
                1,
                minFreeSlots,
                false,
                false,
                " rune"
        );
        if (Rs2GroundItem.lootItemsBasedOnNames(runesParams)) {
            Microbot.pauseAllScripts = false;
        }
    }

    private void lootCoins(RoyalTitansConfig config) {
        LootingParameters coinsParams = new LootingParameters(
                15,
                1,
                1,
                minFreeSlots,
                false,
                false,
                "coins"
        );
        if (Rs2GroundItem.lootCoins(coinsParams)) {
            Microbot.pauseAllScripts = false;
        }
    }

    private void lootItemsOnName(RoyalTitansConfig config) {
        LootingParameters valueParams = new LootingParameters(
                15,
                1,
                1,
                minFreeSlots,
                false,
                false,
                itemsToLoot.trim().split(",")
        );
        if (Rs2GroundItem.lootItemsBasedOnNames(valueParams)) {
            Microbot.pauseAllScripts = false;
        }
    }
}
