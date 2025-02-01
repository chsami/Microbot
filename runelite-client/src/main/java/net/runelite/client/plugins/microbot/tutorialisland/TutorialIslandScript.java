package net.runelite.client.plugins.microbot.tutorialisland;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.NameGenerator;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.*;
import static net.runelite.client.plugins.microbot.util.math.Random.random;
import static net.runelite.client.plugins.microbot.util.settings.Rs2Settings.*;

public class TutorialIslandScript extends Script {

    public static double version = 1.2;
    public static Status status = Status.NAME;
    final int CharacterCreation = 679;
    final int[] CharacterCreation_Arrows = new int[]{13, 17, 21, 25, 29, 33, 37, 44, 48, 52, 56, 60};
    private final TutorialislandPlugin plugin;
    private final int NameCreation = 558;
    private boolean toggledSettings = false;
    private boolean toggledMusic = false;

    @Inject
    public TutorialIslandScript(TutorialislandPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run(TutorialIslandConfig config) {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.simulateMistakes = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                CalculateStatus();

                if (hasContinue()) {
                    clickContinue();
                    return;
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Microbot.pauseAllScripts) return;

                switch (status) {
                    case NAME:
                        Widget nameSearchBar = Rs2Widget.getWidget(NameCreation, 12); // enterName Field text
                        
                        String nameSearchBarText = nameSearchBar.getText();

                        if (nameSearchBarText.endsWith("*")) {
                            nameSearchBarText = nameSearchBarText.substring(0, nameSearchBarText.length() - 1);
                        }
                        
                        if (!nameSearchBarText.isEmpty()) {
                            Rs2Widget.clickWidget(NameCreation, 7); // enterName Field
                            Rs2Random.waitEx(1200, 300);
                            
                            for (int i = 0; i < nameSearchBarText.length(); i++) {
                                Rs2Keyboard.keyPress(KeyEvent.VK_BACK_SPACE);
                                Rs2Random.waitEx(600, 100);
                            }
                            
                            return;
                        }
                        
                        String name = new NameGenerator(random(7, 10)).getName();
                        Rs2Widget.clickWidget(NameCreation, 7); // enterName Field
                        Rs2Random.waitEx(1200, 300);
                        Rs2Keyboard.typeString(name);
                        Rs2Random.waitEx(2400, 600);
                        Rs2Widget.clickWidget(NameCreation, 18); // lookupName Button
                        Rs2Random.waitEx(4800, 600);

                        Widget responseWidget = Rs2Widget.getWidget(NameCreation, 13); // responseText Widget

                        if (responseWidget != null) {
                            String widgetText = responseWidget.getText();
                            String cleanedWidgetText = Rs2UiHelper.stripColTags(widgetText);
                            String expectedText = "Great! The display name " + name + " is available";
                            boolean nameAvailable = cleanedWidgetText.startsWith(expectedText);

                            if (nameAvailable) {
                                Rs2Widget.clickWidget(NameCreation, 19); // setName Button
                                Rs2Random.waitEx(4800, 600);

                                sleepUntil(() -> !isNameCreationVisible());
                            }
                        }
                        break;
                    case CHARACTER:
                        RandomizeCharacter();
                        break;
                    case GETTING_STARTED:
                        GettingStarted();
                        break;
                    case SURVIVAL_GUIDE:
                        SurvivalGuide();
                        break;
                    case COOKING_GUIDE:
                        CookingGuide();
                        break;
                    case QUEST_GUIDE:
                        QuestGuide();
                        break;
                    case MINING_GUIDE:
                        MiningGuide();
                        break;
                    case COMBAT_GUIDE:
                        CombatGuide();
                        break;
                    case BANKER_GUIDE:
                        BankerGuide();
                        break;
                    case PRAYER_GUIDE:
                        PrayerGuide();
                        break;
                    case MAGE_GUIDE:
                        MageGuide();
                        break;
                    case FINISHED:
                        shutdown();
                        break;
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    private boolean isNameCreationVisible() {
        return Rs2Widget.isWidgetVisible(NameCreation, 2);
    }

    private boolean isCharacterCreationVisible() {
        return Rs2Widget.isWidgetVisible(CharacterCreation, 4);
    }

    public void CalculateStatus() {
        if (isNameCreationVisible()) {
            status = Status.NAME;
        } else if (isCharacterCreationVisible()) {
            status = Status.CHARACTER;
        } else if (Microbot.getVarbitPlayerValue(281) < 10) {
            status = Status.GETTING_STARTED;
        } else if (Microbot.getVarbitPlayerValue(281) >= 10 && Microbot.getVarbitPlayerValue(281) < 120) {
            status = Status.SURVIVAL_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) >= 120 && Microbot.getVarbitPlayerValue(281) < 200) {
            status = Status.COOKING_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) >= 200 && Microbot.getVarbitPlayerValue(281) <= 250) {
            status = Status.QUEST_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) >= 260 && Microbot.getVarbitPlayerValue(281) <= 360) {
            status = Status.MINING_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) > 360 && Microbot.getVarbitPlayerValue(281) < 510) {
            status = Status.COMBAT_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) >= 510 && Microbot.getVarbitPlayerValue(281) < 540) {
            status = Status.BANKER_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) >= 540 && Microbot.getVarbitPlayerValue(281) < 610) {
            status = Status.PRAYER_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) >= 610 && Microbot.getVarbitPlayerValue(281) < 1000) {
            status = Status.MAGE_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) == 1000) {
            status = Status.FINISHED;
        }
    }

    public void RandomizeCharacter() {
        if (Rs2Random.diceFractional(0.2)) {
            if (Rs2Random.diceFractional(0.25)) { // chance to change gender 
                System.out.println("changing gender...");
                Widget maleWidget = Rs2Widget.getWidget(CharacterCreation, 68); // maleButton
                Widget femaleWidget = Rs2Widget.getWidget(CharacterCreation, 69); // femaleButton.. nice..
                int selectedColor = 0xaaaaaa;
                
                boolean hasMaleSelected = Arrays.stream(maleWidget.getDynamicChildren()).anyMatch(mdw -> mdw != null && mdw.getTextColor() == selectedColor);
                boolean hasFemaleSelected = Arrays.stream(femaleWidget.getDynamicChildren()).anyMatch(fdw -> fdw != null && fdw.getTextColor() == selectedColor);
                
                if (hasFemaleSelected) {
                    Rs2Widget.clickWidget(maleWidget);
                    Rs2Random.waitEx(1200, 300);
                    sleepUntil(() -> hasMaleSelected);
                } else if (hasMaleSelected) {
                    Rs2Widget.clickWidget(femaleWidget);
                    Rs2Random.waitEx(1200, 300);
                    sleepUntil(() -> hasFemaleSelected);
                }
            }
            
            if (Rs2Random.diceFractional(0.25)) { // chance to change pronouns 
                System.out.println("changing pronouns...");
                Widget pronounWidget = Rs2Widget.getWidget(CharacterCreation, 72); // open pronouns DropDown
                Widget currentPronoun = Arrays.stream(pronounWidget.getDynamicChildren()).filter(pnw -> pnw.getText().toLowerCase().contains("he/him") || pnw.getText().toLowerCase().contains("they/them") || pnw.getText().toLowerCase().contains("she/her")).findFirst().orElse(null);
                Rs2Widget.clickWidget(pronounWidget);
                Rs2Random.waitEx(1200, 300);
                sleepUntil(() -> Rs2Widget.isWidgetVisible(CharacterCreation, 76)); // Pronoun DropDown Options
                Widget[] dynamicPronounWidgets = Rs2Widget.getWidget(CharacterCreation, 78).getDynamicChildren();
                Widget pronounSelectionWidget;
                
                if (currentPronoun != null) {
                    if (currentPronoun.getText().toLowerCase().contains("he/him")) {
                        if (Rs2Random.diceFractional(0.5)) {
                            pronounSelectionWidget = Arrays.stream(dynamicPronounWidgets).filter(dpw -> dpw.getText().toLowerCase().contains("they/them")).findFirst().orElse(null);
                        } else {
                            pronounSelectionWidget = Arrays.stream(dynamicPronounWidgets).filter(dpw -> dpw.getText().toLowerCase().contains("she/her")).findFirst().orElse(null);
                        }
                    } else {
                        if (Rs2Random.diceFractional(0.5)) {
                            pronounSelectionWidget = Arrays.stream(dynamicPronounWidgets).filter(dpw -> dpw.getText().toLowerCase().contains("they/them")).findFirst().orElse(null);
                        } else {
                            pronounSelectionWidget = Arrays.stream(dynamicPronounWidgets).filter(dpw -> dpw.getText().toLowerCase().contains("he/him")).findFirst().orElse(null);
                        }
                    }
                    
                    Rs2Widget.clickWidget(pronounSelectionWidget);
                    Rs2Random.waitEx(1200, 300);
                    sleepUntil(() -> !Rs2Widget.isWidgetVisible(CharacterCreation, 76)); // Pronoun DropDown Options
                }
            }
            
            Rs2Widget.clickWidget(CharacterCreation, 74); // confirm Button
            Rs2Random.waitEx(1200, 300);
            sleepUntil(() -> !isCharacterCreationVisible());
        }

        int randomIndex = (int) Math.floor(Math.random() * CharacterCreation_Arrows.length);
        int item = CharacterCreation_Arrows[randomIndex];
        item += Math.random() < 0.5 ? 2 : 3; // Select Up / Down Arrow for random index
        Widget widget = Rs2Widget.getWidget(CharacterCreation, item);

        for (int i = 0; i < Random.random(1, 3); i++) {
            Rs2Widget.clickWidget(widget.getId());
            Rs2Random.waitEx(1200, 300);
        }
    }

    public void GettingStarted() {
        NPC npc = Rs2Npc.getNpc(NpcID.GIELINOR_GUIDE);

        if (hasContinue()) return;

        if (Microbot.getVarbitPlayerValue(281) < 3) {
            if (isInDialogue()) {
                Rs2Keyboard.typeString(Integer.toString(random(1, 3)));
                return;
            }

            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (Microbot.getVarbitPlayerValue(281) < 8) {

            if (!toggledSettings) {
                Rs2Widget.clickWidget(164, 41);
                toggledSettings = true;
                Rs2Random.waitEx(1200, 300);
                return;
            }

            if (plugin.isToggleMusic() && !toggledMusic) {
                turnOffMusic();
                toggledMusic = true;
                Rs2Random.waitEx(1200, 300);
                return;
            }

            if (plugin.isToggleRoofs() && !isHideRoofsEnabled()) {
                hideRoofs(false);
                Rs2Random.waitEx(1200, 300);
                return;
            }

            if (plugin.isToggleShiftDrop() && !isDropShiftSettingEnabled()) {
                enableDropShiftSetting(false);
                Rs2Random.waitEx(1200, 300);
                return;
            }

            if (plugin.isToggleLevelUp() && isLevelUpNotificationsEnabled()) {
                disableLevelUpNotifications(true);
                Rs2Random.waitEx(1200, 300);
                return;
            }

            Rs2Camera.setZoom(Random.random(400, 450));
            Rs2Random.waitEx(300, 100);
            Rs2Camera.setPitch(280);

            sleepUntil(() -> Rs2Camera.getPitch() > 250);

            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }

        } else {
            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        }
    }

    public void SurvivalGuide() {
        NPC npc = Rs2Npc.getNpc(NpcID.SURVIVAL_EXPERT);

        if (Microbot.getVarbitPlayerValue(281) == 10 || Microbot.getVarbitPlayerValue(281) == 20 || Microbot.getVarbitPlayerValue(281) == 60) {
            if (!Rs2Npc.hasLineOfSight(npc)) {
                Rs2Walker.walkTo(npc.getWorldLocation(), 4);
                Rs2Player.waitForWalking();
            }
            if (Rs2Npc.interact(npc, "talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (Microbot.getVarbitPlayerValue(281) < 40) {
            Rs2Random.waitEx(1200, 300);
            Rs2Widget.clickWidget(164, 55); // switchToInventoryTab
            Rs2Random.waitEx(1200, 300);
        } else if (Microbot.getVarbitPlayerValue(281) < 50) {
            fishShrimp();
        } else if (Microbot.getVarbitPlayerValue(281) < 70) {
            Rs2Widget.clickWidget(164, 53); // switchToSkillsTab
            Rs2Random.waitEx(1200, 300);
            if (Rs2Npc.interact(npc, "talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (Microbot.getVarbitPlayerValue(281) <= 90) {
            if (!Rs2Inventory.hasItem("Bronze Axe") || !Rs2Inventory.hasItem("Tinderbox")) {
                if (Rs2Npc.interact(npc, "talk-to")) {
                    sleepUntil(Rs2Dialogue::isInDialogue);
                }
                return;
            }
            if (!Rs2Inventory.contains("Raw shrimps")) {
                fishShrimp();
                return;
            }
            if (!Rs2Inventory.contains("Logs") && (!Rs2GameObject.exists(ObjectID.FIRE_26185) || Rs2Player.getRealSkillLevel(Skill.WOODCUTTING) == 0)) {
                CutTree();
                return;
            }
            if (!Rs2GameObject.exists(ObjectID.FIRE_26185)) {
                LightFire();
                return;
            }
            Rs2Inventory.useItemOnObject(ItemID.RAW_SHRIMPS_2514, ObjectID.FIRE_26185);
        }
    }

    public void MageGuide() {
        NPC npc = Rs2Npc.getNpc(NpcID.MAGIC_INSTRUCTOR);

        if (Microbot.getVarbitPlayerValue(281) == 610 || Microbot.getVarbitPlayerValue(281) == 620) {
            WorldPoint worldPoint = new WorldPoint(3141, 3088, 0);
            WorldPoint targetPoint = (npc != null) ? npc.getWorldLocation() : worldPoint;
            int distance = Rs2Player.distanceTo(targetPoint);

            if (distance > 8) {
                Rs2Walker.walkTo(targetPoint, 8);
            } else {
                if (Rs2Npc.interact(npc, "Talk-to")) {
                    sleepUntil(Rs2Dialogue::isInDialogue);
                }
            }
        } else if (Microbot.getVarbitPlayerValue(281) == 630) {
            Rs2Widget.clickWidget(164, 58); //switchToMagicTab
            Rs2Random.waitEx(1200, 300);
        } else if (Microbot.getVarbitPlayerValue(281) == 640) {
            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (Microbot.getVarbitPlayerValue(281) == 650) {
            NPC chicken = Rs2Npc.getNpcs("chicken").findFirst().orElse(null);
            Rs2Magic.castOn(MagicAction.WIND_STRIKE, chicken);
        } else if (Microbot.getVarbitPlayerValue(281) == 670) {
            if (isInDialogue()) {
                if (Rs2Widget.hasWidget("Do you want to go to the mainland?")) {
                    Rs2Keyboard.typeString("1");
                    return;
                }
                if (hasSelectAnOption()) {
                    Widget widgetOptions = Rs2Widget.getWidget(219, 1);
                    Widget[] dynamicWidgetOptions = widgetOptions.getDynamicChildren();

                    for (int i = 0; i < dynamicWidgetOptions.length; i++) {
                        String optionText = dynamicWidgetOptions[i].getText();

                        if (optionText.contains("Yes, send me to the mainland") || optionText.contains("No, I'm not planning to do that")) {
                            Rs2Keyboard.typeString(String.valueOf(i));
                            break;
                        }
                    }
                }
            } else {
                if (Rs2Npc.interact(npc, "Talk-to")) {
                    sleepUntil(Rs2Dialogue::isInDialogue);
                }
            }
        }
    }

    public void PrayerGuide() {
        NPC npc = Rs2Npc.getNpc(NpcID.BROTHER_BRACE);

        if (Microbot.getVarbitPlayerValue(281) == 640 || Microbot.getVarbitPlayerValue(281) == 550 || Microbot.getVarbitPlayerValue(281) == 540) {
            Rs2Walker.walkTo(new WorldPoint(3124, 3106, 0));
            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (Microbot.getVarbitPlayerValue(281) == 560) {
            Rs2Widget.clickWidget(164, 57); // switchToPrayerTab
            Rs2Random.waitEx(1200, 300);
        } else if (Microbot.getVarbitPlayerValue(281) == 570) {
            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (Microbot.getVarbitPlayerValue(281) == 580) {
            Rs2Widget.clickWidget(164, 46); // switchToFriendsTab
            Rs2Random.waitEx(1200, 300);
        } else if (Microbot.getVarbitPlayerValue(281) == 600) {
            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        }
    }

    public void BankerGuide() {
        NPC npc = Rs2Npc.getNpc(NpcID.ACCOUNT_GUIDE);

        if (Microbot.getVarbitPlayerValue(281) == 510) {
            Rs2GameObject.interact(ObjectID.BANK_BOOTH_10083);
            sleepUntil(() -> Microbot.getVarbitPlayerValue(281) != 510);


        } else if (Microbot.getVarbitPlayerValue(281) == 520) {
            if (Rs2Widget.isWidgetVisible(289, 5)) {
                Widget widgetOptions = Rs2Widget.getWidget(289, 4);
                Widget[] dynamicWidgetOptions = widgetOptions.getDynamicChildren();

                for (Widget dynamicWidgetOption : dynamicWidgetOptions) {
                    String widgetText = dynamicWidgetOption.getText();

                    if (widgetText != null) {
                        if (widgetText.equalsIgnoreCase("Want more bank space?")) {
                            Rs2Widget.clickWidget(289, 7);
                            Rs2Random.waitEx(1200, 300);
                            break;
                        }
                    }
                }
            }
            
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen());
            Rs2GameObject.interact(26815); //interactWithPollBooth
            sleepUntil(() -> Microbot.getVarbitPlayerValue(281) != 520);
        } else if (Microbot.getVarbitPlayerValue(281) == 525 || Microbot.getVarbitPlayerValue(281) == 530) {
            if (Rs2Widget.isWidgetVisible(310, 2)) {
                Widget widgetOptions = Rs2Widget.getWidget(310, 2);
                Widget[] dynamicWidgetOptions = widgetOptions.getDynamicChildren();

                for (Widget dynamicWidgetOption : dynamicWidgetOptions) {
                    String[] actionsText = dynamicWidgetOption.getActions();

                    if (actionsText != null) {
                        if (Arrays.stream(actionsText).anyMatch(at -> at.equalsIgnoreCase("close"))) {
                            Rs2Widget.clickWidget(dynamicWidgetOption);
                            Rs2Random.waitEx(1200, 300);
                            break;
                        }
                    }
                }
            }

            Rs2Walker.walkTo(npc.getWorldLocation(), 3);
            Rs2Player.waitForWalking();
            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (Microbot.getVarbitPlayerValue(281) == 531) {
            Rs2Widget.clickWidget(10747943); //switchToAccountManagementTab
            Rs2Random.waitEx(1200, 300);
        } else if (Microbot.getVarbitPlayerValue(281) == 532) {
            if (Rs2Dialogue.isInDialogue()) {
                clickContinue();
                return;
            }
            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        }
    }

    public void CombatGuide() {
        NPC npc = Rs2Npc.getNpc(NpcID.COMBAT_INSTRUCTOR);

        if (Microbot.getVarbitPlayerValue(281) <= 370) {
            Rs2Walker.walkTo(new WorldPoint(random(3106, 3108), random(9508, 9510), 0));
            Rs2Player.waitForWalking();
            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (Microbot.getVarbitPlayerValue(281) <= 410) {
            if (isInDialogue()) {
                clickContinue();
                return;
            }
            Rs2Widget.clickWidget(164, 56); //switchToEquipmentMenu
            Rs2Random.waitEx(1200, 300);
            Rs2Widget.clickWidget(387, 1); //openEquipmentStats
            sleepUntil(() -> Rs2Widget.getWidget(84, 1) != null);
            Rs2Random.waitEx(1200, 300);
            Rs2Widget.clickWidget("Bronze dagger");
            Rs2Random.waitEx(2400, 300);
            
            if (Rs2Widget.isWidgetVisible(84, 3)) {
                Widget widgetOptions = Rs2Widget.getWidget(84, 3);
                Widget[] dynamicWidgetOptions = widgetOptions.getDynamicChildren();

                for (Widget dynamicWidgetOption : dynamicWidgetOptions) {
                    String[] actionsText = dynamicWidgetOption.getActions();

                    if (actionsText != null) {
                        if (Arrays.stream(actionsText).anyMatch(at -> at.equalsIgnoreCase("close"))) {
                            Rs2Widget.clickWidget(dynamicWidgetOption);
                            Rs2Random.waitEx(1200, 300);
                            break;
                        }
                    }
                }
            }
            
            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (Microbot.getVarbitPlayerValue(281) == 500) {
            Rs2Walker.walkTo(new WorldPoint(3111, 9526, Rs2Player.getWorldLocation().getPlane()));
            Rs2Player.waitForWalking();
            Rs2GameObject.interact("Ladder", "Climb-up");
            sleepUntil(() -> Microbot.getVarbitPlayerValue(281) != 500);
        } else if (Microbot.getVarbitPlayerValue(281) == 480 || Microbot.getVarbitPlayerValue(281) == 490) {
            Actor rat = Microbot.getClient().getLocalPlayer().getInteracting();
            if (rat != null && rat.getName().equalsIgnoreCase("giant rat")) return;
            Rs2Inventory.wield("Shortbow");
            Rs2Random.waitEx(600, 100);
            Rs2Inventory.wield("Bronze arrow");
            Rs2Random.waitEx(600, 100);
            Rs2Walker.walkTo(new WorldPoint(3110, 9523, 0), 4);
            Rs2Player.waitForWalking();
            Rs2Npc.attack("Giant rat");
        } else if (Microbot.getVarbitPlayerValue(281) == 470) {
            Rs2Walker.walkTo(npc.getWorldLocation());
            Rs2Player.waitForWalking();
            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (Microbot.getVarbitPlayerValue(281) >= 420) {
            if (Microbot.getClient().getLocalPlayer().isInteracting() || Rs2Player.isAnimating()) return;
            if (Rs2Equipment.isWearing("Bronze sword")) {
                Rs2Widget.clickWidget(164, 52); //switchToCombatOptions
                Rs2Random.waitEx(1200, 300);
                WorldPoint worldPoint = new WorldPoint(3105, 9517, 0);
                Rs2Walker.walkTo(worldPoint, 3);
                Rs2Player.waitForWalking();
                Rs2Npc.attack("Giant rat");
            } else {
                Rs2Tab.switchToInventoryTab();
                Rs2Random.waitEx(600, 100);
                Rs2Inventory.wield("Bronze sword");
                Rs2Random.waitEx(600, 100);
                Rs2Inventory.wield("Wooden shield");
            }
        }
    }

    public void MiningGuide() {
        NPC npc = Rs2Npc.getNpc(NpcID.MINING_INSTRUCTOR);

        if (Microbot.getVarbitPlayerValue(281) == 260) {
            Rs2Walker.walkTo(new WorldPoint(random(3082, 3085), random(9502, 9505), 0));
            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else {
            if (Rs2Inventory.contains("Bronze dagger")) {
                Rs2GameObject.interact(ObjectID.GATE_9718, "Open");
                sleepUntil(() -> Microbot.getVarbitPlayerValue(281) > 360);
                return;
            }
            if (Rs2Inventory.contains("Bronze bar") && Rs2Inventory.contains("Hammer")) {
                Rs2GameObject.interact("Anvil", "Smith");
                sleepUntil(Rs2Widget::isSmithingWidgetOpen);
                Rs2Widget.clickWidget(312, 9); // Smith Bronze Dagger
                Rs2Random.waitEx(1200, 300);
                sleepUntil(() -> Rs2Inventory.contains("Bronze dagger") && !Rs2Player.isAnimating(1800));
                return;
            }
            if (Rs2Inventory.contains("Bronze bar") && !Rs2Inventory.contains("Hammer")) {
                if (Rs2Npc.interact(npc, "Talk-to")) {
                    sleepUntil(Rs2Dialogue::isInDialogue);
                }
                return;
            }
            if (Rs2Inventory.contains("Bronze pickaxe") && (!Rs2Inventory.contains("Copper ore") || !Rs2Inventory.contains("Tin ore"))) {
                List<Integer> rockIds = new ArrayList<>();
                if (!Rs2Inventory.contains("Copper ore")) {
                    rockIds.add(ObjectID.COPPER_ROCKS);
                }
                if (!Rs2Inventory.contains("Tin ore")) {
                    rockIds.add(ObjectID.TIN_ROCKS);
                }

                Collections.shuffle(rockIds);
                int rockId = rockIds.get(0);

                Rs2GameObject.interact(rockId, "Mine");
                sleepUntil(() -> {
                    if (rockId == ObjectID.COPPER_ROCKS) {
                        return Rs2Inventory.contains("Copper ore") && !Rs2Player.isAnimating(1800);
                    } else {
                        return Rs2Inventory.contains("Tin ore") && !Rs2Player.isAnimating(1800);
                    }
                });
            } else if (Rs2Inventory.contains("Copper ore") && Rs2Inventory.contains("Tin ore")) {
                int[] ores = {ItemID.TIN_ORE, ItemID.COPPER_ORE};
                Collections.shuffle(Arrays.asList(ores));
                Rs2Inventory.useItemOnObject(ores[0], ObjectID.FURNACE_10082);
                sleepUntil(() -> Rs2Inventory.contains("Bronze bar") && !Rs2Player.isAnimating(1800));
            }
        }
    }

    public void QuestGuide() {
        NPC npc = Rs2Npc.getNpc(NpcID.QUEST_GUIDE);

        if (Microbot.getVarbitPlayerValue(281) == 200 || Microbot.getVarbitPlayerValue(281) == 210) {
            Rs2Walker.walkTo(new WorldPoint(random(3083, 3086), random(3127, 3129), 0));
            Rs2GameObject.interact(9716, "Open");
            Rs2Random.waitEx(1200, 300);
        } else if (Microbot.getVarbitPlayerValue(281) == 220 || Microbot.getVarbitPlayerValue(281) == 240) {
            Rs2Npc.interact(npc, "Talk-to");
            sleepUntil(Rs2Dialogue::isInDialogue);
        } else if (Microbot.getVarbitPlayerValue(281) == 230) {
            Rs2Widget.clickWidget(164, 54); // switchToQuestTab
            Rs2Random.waitEx(1200, 300);
        } else {
            Rs2Tab.switchToInventoryTab();
            Rs2Random.waitEx(600, 100);
            Rs2GameObject.interact(9726, "Climb-down");
            Rs2Random.waitEx(2400, 100);
        }
    }

    public void CookingGuide() {
        NPC npc = Rs2Npc.getNpc(NpcID.MASTER_CHEF);
        
        if (Microbot.getVarbitPlayerValue(281) == 120) {
            Rs2Random.waitEx(1200, 300);
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            Rs2GameObject.interact(ObjectID.GATE_9470, "Open");
            sleepUntil(() -> Microbot.getVarbitPlayerValue(281) != 120);
        } else if (Microbot.getVarbitPlayerValue(281) == 130) {
            Rs2GameObject.interact(ObjectID.DOOR_9709, "Open");
            sleepUntil(() -> Microbot.getVarbitPlayerValue(281) != 130);
        } else if (Microbot.getVarbitPlayerValue(281) == 140) {
            if (Rs2Npc.interact(npc, "Talk-to")) {
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (Microbot.getVarbitPlayerValue(281) >= 150 && Microbot.getVarbitPlayerValue(281) < 200) {
            if (!Rs2Inventory.contains("Bread dough") && !Rs2Inventory.contains("Bread")) {
                Rs2Inventory.combine("Bucket of water", "Pot of flour");
                sleepUntil(() -> Rs2Inventory.contains("Dough"), 2000);
            } else if (Rs2Inventory.contains("Bread dough")) {
                Rs2Inventory.interact("Bread dough");
                Rs2GameObject.interact(9736, "Use");
                sleepUntil(() -> Rs2Inventory.contains("Bread"));
            } else if (Rs2Inventory.contains("Bread")) {
                if (Rs2GameObject.interact(9710, "Open")) {
                    Rs2Random.waitEx(2400, 100);
                }
            }
        }
    }

    public void LightFire() {
        if (Rs2Player.isStandingOnGameObject()) {
            WorldPoint nearestWalkable = Rs2Tile.getNearestWalkableTileWithLineOfSight(Rs2Player.getWorldLocation());
            Rs2Walker.walkFastCanvas(nearestWalkable);
            Rs2Player.waitForWalking();
        }
        Rs2Inventory.combine("Logs", "Tinderbox");
        sleepUntil(() -> !Rs2Inventory.hasItem("Logs") && !Rs2Player.isAnimating(2400));
    }

    public void CutTree() {
        Rs2GameObject.interact("Tree", "Chop down");
        sleepUntil(() -> Rs2Inventory.hasItem("Logs") && !Rs2Player.isAnimating(2400));
    }

    public void fishShrimp() {
        Rs2Npc.interact(NpcID.FISHING_SPOT_3317, "Net");
        sleepUntil(() -> Rs2Inventory.contains("Raw shrimps"));
    }

    enum Status {
        NAME,
        CHARACTER,
        GETTING_STARTED,
        SURVIVAL_GUIDE,
        COOKING_GUIDE,
        QUEST_GUIDE,
        MINING_GUIDE,
        COMBAT_GUIDE,
        BANKER_GUIDE,
        PRAYER_GUIDE,
        IRONMAN_GUIDE,
        MAGE_GUIDE,
        FINISHED
    }
}
