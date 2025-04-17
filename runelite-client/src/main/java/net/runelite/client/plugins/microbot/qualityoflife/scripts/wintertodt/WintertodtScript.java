package net.runelite.client.plugins.microbot.qualityoflife.scripts.wintertodt;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.ObjectID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.qualityoflife.QoLConfig;
import net.runelite.client.plugins.microbot.qualityoflife.QoLPlugin;
import net.runelite.client.plugins.microbot.qualityoflife.enums.WintertodtActions;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WintertodtScript extends Script {
    public static QoLConfig config;
    public static GameObject unlitBrazier;
    public static GameObject brokenBrazier;
    public static NPC pyromancer;
    public static NPC incapitatedPyromancer;
    public static boolean helpedIncapitatedPyromancer = false;
    public static boolean isWintertodtAlive = false;
    public static int wintertodtHp = -1;
    @Inject
    private QoLPlugin qolPlugin;

    public static boolean isInWintertodtRegion() {
        return Rs2Player.getWorldLocation().getRegionID() == 6462;
    }

    public boolean run(QoLConfig config) {
        WintertodtScript.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || !isInWintertodtRegion()) {
                    return;
                }

                Widget wintertodtHealthbar = Rs2Widget.getWidget(25952276);
                isWintertodtAlive = Rs2Widget.hasWidget("Wintertodt's Energy");
                if (wintertodtHealthbar != null && isWintertodtAlive) {
                    String widgetText = wintertodtHealthbar.getText();
                    wintertodtHp = Integer.parseInt(widgetText.split("\\D+")[1]);
                } else {
                    wintertodtHp = -1;
                }
                brokenBrazier = Rs2GameObject.getGameObjects(ObjectID.BRAZIER_29313).stream().filter(gameObject -> gameObject.getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation()) < 5).findFirst().orElse(null);
                unlitBrazier = Rs2GameObject.getGameObjects(ObjectID.BRAZIER_29312).stream().filter(gameObject -> gameObject.getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation()) < 5).findFirst().orElse(null);

                shouldEat();

                if (!config.interrupted())
                    return;

                NewMenuEntry actionToResume = config.wintertodtActions().getMenuEntry();
                if (config.wintertodtActions().equals(WintertodtActions.FEED)) {
                    GameObject fireBrazier = Rs2GameObject.getGameObjects(ObjectID.BURNING_BRAZIER_29314).stream().findFirst().orElse(null);
                    if (fireBrazier != null && fireBrazier.getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation()) < 5) {
                        if (!Rs2Inventory.contains(ItemID.BRUMA_ROOT) && !Rs2Inventory.contains(ItemID.BRUMA_KINDLING)) {
                            qolPlugin.updateLastWinthertodtAction(WintertodtActions.NONE);
                            qolPlugin.updateWintertodtInterupted(false);
                            Microbot.log("No resources found in inventory, cancelling action.");
                            return;
                        }
                        Microbot.doInvoke(actionToResume, new java.awt.Rectangle(1, 1));
                        qolPlugin.updateWintertodtInterupted(false);
                    }
                }
                if (config.wintertodtActions().equals(WintertodtActions.FLETCH)) {
                    if (!Rs2Inventory.contains(ItemID.BRUMA_ROOT)) {
                        qolPlugin.updateLastWinthertodtAction(WintertodtActions.NONE);
                        qolPlugin.updateWintertodtInterupted(false);
                        return;
                    }

                    Microbot.doInvoke(actionToResume, new java.awt.Rectangle(1, 1));
                    WintertodtActions.fletchBrumaRootsOnClicked();
                    qolPlugin.updateWintertodtInterupted(false);
                }

            } catch (Exception e) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), e);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    // shutdown
    @Override
    public void shutdown() {
        super.shutdown();
        Microbot.log("Winterdotd script shutting down");
    }

    public void onNpcChanged(NpcChanged event) {
        if (event.getNpc().getId() == 7372) {
            incapitatedPyromancer = event.getNpc();

        }
        if (event.getNpc().getId() == 7371) {
            pyromancer = event.getNpc();
            incapitatedPyromancer = null;
            if (helpedIncapitatedPyromancer) {
                if (config.lightUnlitBrazier()) {
                    if (Rs2Equipment.hasEquipped(ItemID.BRUMA_TORCH) || Rs2Equipment.hasEquipped(ItemID.BRUMA_TORCH_OFFHAND) || Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                        scheduledFuture = scheduledExecutorService.schedule(() -> Rs2GameObject.interact(unlitBrazier, "Light"), 300, TimeUnit.MILLISECONDS);
                    }
                }
            }
            helpedIncapitatedPyromancer = false;
        }
    }

    public void onNpcSpawned(NpcSpawned event) {
        if (event.getNpc().getId() == 7372) {
            if (incapitatedPyromancer != null) {
                if (incapitatedPyromancer.getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation()) > event.getNpc().getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation())) {
                    incapitatedPyromancer = event.getNpc();
                    return;
                }
            }
            incapitatedPyromancer = event.getNpc();
        }
        if (event.getNpc().getId() == 7371) {
            if (pyromancer != null) {
                if (pyromancer.getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation()) > event.getNpc().getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation())) {
                    pyromancer = event.getNpc();
                    return;
                }
            }
            pyromancer = event.getNpc();
        }
    }

    public void onNpcDespawned(NpcDespawned event) {
        if (event.getNpc().equals(incapitatedPyromancer)) {
            incapitatedPyromancer = Rs2Npc.getNpc("Incapacitated Pyromancer");
        }
        if (event.getNpc().equals(pyromancer)) {
            pyromancer = Rs2Npc.getNpc("Pyromancer");
        }
    }

    public void onChatMessage(ChatMessage chatMessage) {
        var message = chatMessage.getMessage();
        if (message.contains("The brazier is broken and shrapnel")) {
            if (config.fixBrokenBrazier()) {
                qolPlugin.updateWintertodtInterupted(false);
                scheduledFuture = scheduledExecutorService.schedule(() -> Rs2GameObject.interact(brokenBrazier, "Fix"), 300, TimeUnit.MILLISECONDS);
            }
        }

        if (message.startsWith("The brazier has gone out")) {
            if (incapitatedPyromancer != null) {
                if (!config.healPyromancer())
                    return;
                scheduledFuture = scheduledExecutorService.schedule(() -> Rs2Npc.interact(incapitatedPyromancer, "Help"), 300, TimeUnit.MILLISECONDS);
                helpedIncapitatedPyromancer = true;
            } else {
                if (config.lightUnlitBrazier()) {
                    if (Rs2Equipment.hasEquipped(ItemID.BRUMA_TORCH) || Rs2Equipment.hasEquipped(ItemID.BRUMA_TORCH_OFFHAND) || Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                        scheduledFuture = scheduledExecutorService.schedule(() -> Rs2GameObject.interact(unlitBrazier, "Light"), 300, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }

        if (message.startsWith("You fix the brazier")) {
            if (incapitatedPyromancer != null) {
                if (!config.healPyromancer())
                    return;
                scheduledFuture = scheduledExecutorService.schedule(() -> Rs2Npc.interact(incapitatedPyromancer, "Help"), 300, TimeUnit.MILLISECONDS);
                helpedIncapitatedPyromancer = true;
            } else {
                if (config.lightUnlitBrazier()) {
                    if (Rs2Equipment.hasEquipped(ItemID.BRUMA_TORCH) || Rs2Equipment.hasEquipped(ItemID.BRUMA_TORCH_OFFHAND) || Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                        scheduledFuture = scheduledExecutorService.schedule(() -> Rs2GameObject.interact(unlitBrazier, "Light"), 300, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }

        if (message.startsWith("Heal the Pyromancer")) {

                if (!config.healPyromancer())
                    return;
                scheduledFuture = scheduledExecutorService.schedule(() -> Rs2Npc.interact(incapitatedPyromancer, "Help"), 300, TimeUnit.MILLISECONDS);
                helpedIncapitatedPyromancer = true;

        }

        if (message.startsWith("You light the brazier")) {
            if (config.wintertodtActions().equals(WintertodtActions.NONE)) {
                return;
            }
            qolPlugin.updateWintertodtInterupted(true);
        }

        if (message.startsWith("You have gained a supply crate")) {
            qolPlugin.updateWintertodtInterupted(false);
            qolPlugin.updateLastWinthertodtAction(WintertodtActions.NONE);
        }

        if (message.startsWith("You did not earn enough points")) {
            qolPlugin.updateWintertodtInterupted(false);
            qolPlugin.updateLastWinthertodtAction(WintertodtActions.NONE);
        }

        if (message.startsWith("You have run out of bruma roots")) {
            qolPlugin.updateWintertodtInterupted(false);
            qolPlugin.updateLastWinthertodtAction(WintertodtActions.NONE);
        }
    }

    public int getWarmthLevel() {
        String warmthWidgetText = Rs2Widget.getChildWidgetText(396, 20);

        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(warmthWidgetText);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return 100;
    }

    private boolean shouldEat() {
        if (getWarmthLevel() <= config.eatFoodPercentage()) {
                List<Rs2ItemModel> rejuvenationPotions = Rs2Inventory.getPotions();

                if (rejuvenationPotions.isEmpty()) {
                    return false;
                }

                Rs2Inventory.interact(rejuvenationPotions.get(0), "Drink");
                sleepGaussian(600, 150);
                qolPlugin.updateWintertodtInterupted(true);
                return true;

        }
        return false;
    }
}
