package net.runelite.client.plugins.microbot.fishing.FishingTrawler;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

@Slf4j
public class FishingTrawlerScript extends Script {
    public static boolean tentacle = false;
    public static boolean wasInsideBoat = false;

    private static final int WIDGET_GANGPLANK_CONTINUE = 15007746;
    private static final int WIDGET_CONTRIBUTION = 23986189;
    private static final int OBJECT_TRAWLERNET = 2483;
    private static final int OBJECT_SHIPSLADDER = 4060;
    private static final int OBJECT_DAISIES = 1189;
    private static final int OBJECT_TENTACLE_LADDER = 4139;

    public boolean run(FishingTrawlerConfig config) {
        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || BreakHandlerScript.isBreakActive()) return;

                long startTime = System.currentTimeMillis();
                if (!Rs2Inventory.hasItem("axe", false) && !Rs2Equipment.isWearing("axe", false)) {
                    Microbot.showMessage("You need an axe in your inventory or equipped.");
                    shutdown();
                    return;
                }

                if (Rs2GameObject.exists(OBJECT_TRAWLERNET)) {
                    if (wasInsideBoat) {
                        Microbot.log("Looting Rewards");
                        Microbot.status = "Looting Rewards";
                        Rs2GameObject.interact(OBJECT_TRAWLERNET, "inspect");
                        Rs2Player.waitForWalking();
                        sleep(Rs2Random.randomGaussian(600, 300));
                        Rs2Widget.clickWidget("Bank-all");
                        sleep(Rs2Random.randomGaussian(600, 300));
                        wasInsideBoat = false;
                        BreakHandlerScript.setLockState(false);
                        if (BreakHandlerScript.isBreakActive()) {
                            Microbot.log("Break time, waiting...");
                            return;
                        }
                    }

                    BreakHandlerScript.setLockState(true);

                    if (Rs2GameObject.interact(new WorldPoint(2675, 3170, 0), "Cross")) {
                        Microbot.log("Crossing Gangplank");
                        Rs2Player.waitForWalking(10000);
                    }
                } else if (Rs2GameObject.exists(OBJECT_DAISIES) && Rs2Player.getWorldLocation().getPlane() == 0) {
                    Microbot.log("Washed up — heading back");
                    Rs2Walker.walkTo(new WorldPoint(2676, 3170, 0));
                }

                Widget gangplankWidget = Rs2Widget.getWidget(WIDGET_GANGPLANK_CONTINUE);
                String gangplankMessage = gangplankWidget != null ? gangplankWidget.getText() : null;

                Widget contributionWidget = Rs2Widget.getWidget(WIDGET_CONTRIBUTION);
                String contributionText = contributionWidget != null ? contributionWidget.getText() : null;

                if (gangplankMessage != null && gangplankMessage.contains("continue")) {
                        Rs2Widget.clickWidget(WIDGET_GANGPLANK_CONTINUE);
                        Microbot.status = "Waiting inside the boat";
                }

                int contributionValue = 0;
                if (contributionText != null && contributionText.contains(":")) {
                    try {
                        contributionValue = Integer.parseInt(contributionText.split(":")[1].strip());
                    } catch (Exception e) {
                        log.debug("Failed to parse contribution value: {} [{}]", contributionText, e.getMessage());
                    }
                } else {
                    log.debug("Contribution widget missing or malformed.");
                }

                if (Rs2Player.getWorldLocation().getPlane() == 0 && Rs2GameObject.exists(OBJECT_SHIPSLADDER)) {
                    Microbot.log("In minigame — heading up ladder");
                    wasInsideBoat = true;
                    Rs2GameObject.interact(new WorldPoint(1884, 4826, 0), "Climb-up");
                    Rs2Player.waitForWalking();
                    Rs2Player.waitForAnimation();
                    Rs2Walker.walkFastCanvas(new WorldPoint(1885, 4827, 1));
                }

                if (config.stopat50() && contributionValue >= 50) return;

                if (!Rs2Player.isInteracting() && !Rs2Player.isMoving()) {
                    log.debug("Tentacle phase");

                    NPC tentacleNpc = Rs2Npc.getNpc("Enormous Tentacle");

                    if (tentacleNpc != null) {
                        if (!tentacle) {
                            Microbot.log("Tentacle found, chopping it down");
                            Rs2Camera.turnTo(tentacleNpc);
                        }
                        sleepUntil(() -> tentacleNpc.getAnimation() == 8953, 10000);
                        Rs2Npc.interact("Enormous Tentacle", "Chop");
                        sleepUntilTick(2);
                        if (!Rs2Player.isInteracting()) Rs2Npc.interact("Enormous Tentacle", "Chop");
                        tentacle = true;
                        wasInsideBoat = true;
                        sleepUntil(() -> !Rs2Player.isInteracting());
                    } else if (tentacle) {
                        GameObject ladderObject = Rs2GameObject.getGameObject(OBJECT_TENTACLE_LADDER);
                        if (ladderObject != null) {
                            WorldPoint current = Rs2Player.getWorldLocation();
                            WorldPoint ladder = ladderObject.getWorldLocation();
                            int plane = current.getPlane();
                            int dx = ladder.dx(1).getX();

                            if (current.getY() == 4823) {
                                Rs2Walker.walkFastCanvas(new WorldPoint(dx, current.dy(4).getY(), plane));
                                tentacle = false;
                            } else if (current.getY() == 4827) {
                                Rs2Walker.walkFastCanvas(new WorldPoint(dx, current.dy(-4).getY(), plane));
                                tentacle = false;
                            }
                        } else {
                            log.debug("Tentacle ladder object not found.");
                        }
                    }
                }

                long endTime = System.currentTimeMillis();
                log.debug("Loop time: {}ms", endTime - startTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        wasInsideBoat = false;
        tentacle = false;
    }
}
