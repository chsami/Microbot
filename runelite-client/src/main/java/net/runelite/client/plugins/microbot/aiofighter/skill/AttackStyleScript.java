package net.runelite.client.plugins.microbot.aiofighter.skill;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import java.util.*;
import java.util.concurrent.TimeUnit;


enum AttackStyle {
    ACCURATE("Accurate", Skill.ATTACK),
    AGGRESSIVE("Aggressive", Skill.STRENGTH),
    DEFENSIVE("Defensive", Skill.DEFENCE),
    CONTROLLED("Controlled", Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE),
    RANGING("Ranging", Skill.RANGED),
    LONGRANGE("Longrange", Skill.RANGED, Skill.DEFENCE),
    CASTING("Casting", Skill.MAGIC),
    DEFENSIVE_CASTING("Defensive Casting", Skill.MAGIC, Skill.DEFENCE),
    OTHER("Other");

    @Getter
    private final String name;
    @Getter
    private final Skill[] skills;

    AttackStyle(String name, Skill... skills) {
        this.name = name;
        this.skills = skills;
    }
}

@Slf4j
public class AttackStyleScript extends Script {

    public static int equippedWeaponTypeVarbit;
    private final Set<Skill> selectedSkills = EnumSet.noneOf(Skill.class);
    boolean initializedLevels = false;
    private AttackStyle attackStyle;
    private AttackStyle attackStyleToTrain;
    // Starting skill levels
    private int attackLevel;
    private int strengthLevel;
    private int defenceLevel;
    // Time delay to no change attack style to often
    private int attackStyleChangeDelay = 0;
    long lastAttackStyleChangeTime = System.currentTimeMillis();

    public boolean run(AIOFighterConfig config) {
        attackStyleChangeDelay = config.attackStyleChangeDelay();
        lastAttackStyleChangeTime = System.currentTimeMillis();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> scheduledTask(config), 0, 1, TimeUnit.SECONDS);
        return true;
    }

    private void scheduledTask(AIOFighterConfig config) {
        try {
            // Early exit conditions
            if (!Microbot.isLoggedIn() || !super.run() || disableIfMaxed(config.toggleDisableOnMaxCombat()))
                return;


            // Initialize levels if not done yet
            if (!initializedLevels) {
                initializeLevels();
            }

            boolean leveledUp = false;

            // Check if we've leveled up
            if (hasLeveledUp()) {
                resetLevels();
                log.info("Leveled up, resetting levels and timer.");
                leveledUp = true;
            }

            if(config.useMagic()){
                if (Rs2Magic.getCurrentAutoCastSpell() != config.magicSpell()) {
                    Rs2Combat.setAutoCastSpell(config.magicSpell(), false);
                }
                return;
            }

            // Proceed if it's time to change the attack style or if we've just leveled up
            if (!isTimeForAttackStyleChange() && !leveledUp) {
                return;
            }

            // Update the last attack style change time
            lastAttackStyleChangeTime = System.currentTimeMillis();

            // Update attack style information
            updateAttackStyleInfo();

            if (attackStyle == AttackStyle.LONGRANGE || attackStyle == AttackStyle.RANGING) {
                WeaponAttackType weaponAttackType = WeaponAttackType.getById(equippedWeaponTypeVarbit);
                // check if any of the attack options is rapid
                if (weaponAttackType != null && weaponAttackType.getAttackOptions().stream().anyMatch(attackOption -> attackOption.getStyle().equals("Rapid"))) {
                    // if rapid is available, switch to it
                    int attackStyleVarbit = Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE);
                    if (attackStyleVarbit != 1) {
                        changeAttackStyle(config, WidgetInfo.COMBAT_STYLE_TWO);
                    }
                }
                else {
                    // if rapid is not available, switch to first attack style
                    int attackStyleVarbit = Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE);
                    if (attackStyleVarbit != 0) {
                        changeAttackStyle(config, WidgetInfo.COMBAT_STYLE_ONE);
                    }
                }
                return;
            }

            // Select skills based on configuration
            selectSkills(config);

            // Get the component to display
            WidgetInfo componentToDisplay = getComponentToDisplay(config);

            Microbot.log("Current Attack Style: " + attackStyle.getName());
            Microbot.log("Attack Style to Train: " + attackStyleToTrain.getName());
            // Change attack style if needed
            if (attackStyle != attackStyleToTrain) {
                changeAttackStyle(config, componentToDisplay);
            }
        } catch(Exception ex) {
            Microbot.logStackTrace("AttackStyleScript", ex);
        }

    }

    private boolean isTimeForAttackStyleChange() {
        long elapsedTime = System.currentTimeMillis() - lastAttackStyleChangeTime;
        return elapsedTime >= attackStyleChangeDelay * 1000L;
    }

    private void updateAttackStyleInfo() {
        int attackStyleVarbit = Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE);
        equippedWeaponTypeVarbit = Microbot.getVarbitValue(Varbits.EQUIPPED_WEAPON_TYPE);
        int castingModeVarbit = Microbot.getVarbitValue(Varbits.DEFENSIVE_CASTING_MODE);
        updateAttackStyle(equippedWeaponTypeVarbit, attackStyleVarbit, castingModeVarbit);
    }


    private void initializeLevels() {
        attackLevel = getSkillLevel(Skill.ATTACK);
        strengthLevel = getSkillLevel(Skill.STRENGTH);
        defenceLevel = getSkillLevel(Skill.DEFENCE);
        initializedLevels = true;
    }

    private void changeAttackStyle(AIOFighterConfig config, WidgetInfo attackStyleWidgetInfo) {
        if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
            Rs2Tab.switchToCombatOptionsTab();
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 2000);
        }
        Rs2Combat.setAttackStyle(attackStyleWidgetInfo);
    }

    // has any of the skills leveled up
    private boolean hasLeveledUp() {
        return attackLevel < getSkillLevel(Skill.ATTACK) ||
                strengthLevel < getSkillLevel(Skill.STRENGTH) ||
                defenceLevel < getSkillLevel(Skill.DEFENCE);
    }

    // if we have leveled up, reset the levels and set delay to 0
    private void resetLevels() {
        attackLevel = getSkillLevel(Skill.ATTACK);
        strengthLevel = getSkillLevel(Skill.STRENGTH);
        defenceLevel = getSkillLevel(Skill.DEFENCE);
    }

    private void updateSelectedSkills(boolean enabled, Skill skill) {
        if (enabled) {
            selectedSkills.add(skill);
        } else {
            selectedSkills.remove(skill);
        }
    }

    private void updateAttackStyle(int equippedWeaponType, int attackStyleIndex, int castingMode) {
        AttackStyle[] attackStyles = getWeaponTypeStyles(equippedWeaponType);
        if (attackStyleIndex < attackStyles.length) {
            attackStyleIndex = (attackStyleIndex == 4) ? attackStyleIndex + castingMode : attackStyleIndex;
            attackStyle = (attackStyles[attackStyleIndex] != null) ? attackStyles[attackStyleIndex] : AttackStyle.OTHER;
        }
    }

    private AttackStyle[] getWeaponTypeStyles(int weaponType) {
        int weaponStyleEnum = Microbot.getEnum(EnumID.WEAPON_STYLES).getIntValue(weaponType);
        int[] weaponStyleStructs = Microbot.getEnum(weaponStyleEnum).getIntVals();

        AttackStyle[] styles = new AttackStyle[weaponStyleStructs.length];
        int i = 0;
        for (int style : weaponStyleStructs) {
            String attackStyleName = Microbot.getStructComposition(style).getStringValue(ParamID.ATTACK_STYLE_NAME);
            AttackStyle attackStyle = AttackStyle.valueOf(attackStyleName.toUpperCase());

            if (attackStyle == AttackStyle.OTHER)
            {
                // "Other" is used for no style
                ++i;
                continue;
            }

            // "Defensive" is used for Defensive and also Defensive casting
            if (i == 5 && attackStyle == AttackStyle.DEFENSIVE)
            {
                attackStyle = AttackStyle.DEFENSIVE_CASTING;
            }

            styles[i++] = attackStyle;
        }
        return styles;
    }

    // compare Players skill levels to the level target in the config
    private boolean needLevel(int levelRequired, Skill skill) {
        if (isMaxed()) {
            log.info("Maxed, switching between any combat style.");
            return true;
        }

        log.info("Skill: {}, Level Required: {} Current Level: {}", skill, levelRequired, getSkillLevel(skill));
        return getSkillLevel(skill) < levelRequired;
    }

    private void selectSkills(AIOFighterConfig config) {
        boolean balanceCombatSkills = config.toggleBalanceCombatSkills();

        boolean needAttack = needLevel(config.attackSkillTarget(), Skill.ATTACK);
        boolean needStrength = needLevel(config.strengthSkillTarget(), Skill.STRENGTH);
        boolean needDefence = needLevel(config.defenceSkillTarget(), Skill.DEFENCE);

        if (balanceCombatSkills) {
            Skill lowestSkill = getLowestSkill(
                    needAttack ? Skill.ATTACK : null,
                    needStrength ? Skill.STRENGTH : null,
                    needDefence ? Skill.DEFENCE : null
            );

            if (lowestSkill != null) {
                resetSelectedSkills();
                updateSelectedSkills(true, lowestSkill);
            }
        } else {
            updateSelectedSkills(needAttack, Skill.ATTACK);
            updateSelectedSkills(needStrength, Skill.STRENGTH);
            updateSelectedSkills(needDefence, Skill.DEFENCE);
        }
    }

    // reset selected skills
    private void resetSelectedSkills() {
        selectedSkills.clear();
    }

    private Skill getLowestSkill(Skill... skills) {
        Skill lowestSkill = null;
        int lowestLevel = Integer.MAX_VALUE;

        for (Skill skill : skills) {
            if (skill == null) {
                continue;
            }

            int level = getSkillLevel(skill);
            if (level < lowestLevel) {
                lowestSkill = skill;
                lowestLevel = level;
            }
        }

        // Check for multiple skills with the same lowest level
        List<Skill> lowestSkills = new ArrayList<>();
        for (Skill skill : skills) {
            if (skill != null && getSkillLevel(skill) == lowestLevel) {
                lowestSkills.add(skill);
            }
        }

        // If there are more than one skill with the lowest level, select any of them
        if (lowestSkills.size() > 1) {
            log.info("Multiple lowest skills: {}", lowestSkills);
            return lowestSkills.get(0); // or any other selection logic if needed
        }
        log.info("Lowest Skill: {}", lowestSkill);
        return lowestSkill;
    }

    private int getSkillLevel(Skill skill) {
        return Microbot.getClient().getRealSkillLevel(skill);
    }

    private boolean isSkillControlled(AttackStyle attackStyle) {
        return attackStyle.getSkills().length > 1;
    }

    private WidgetInfo getComponentToDisplay(AIOFighterConfig config) {
        List<WidgetInfo> componentsToDisplay = new ArrayList<>();
        AttackStyle[] attackStyles = getWeaponTypeStyles(equippedWeaponTypeVarbit);
        Random random = new Random();

        // Iterate over attack styles
        for (int i = 0; i < attackStyles.length; i++) {
            AttackStyle attackStyle = attackStyles[i];
            if (attackStyle == null) {
                continue;
            }

            boolean selectedSkill = false;
            for (Skill skill : attackStyle.getSkills()) {
                if (isSkillControlled(attackStyle) && config.toggleAvoidControlled()) {
                    continue;
                }
                if (selectedSkills.contains(skill)) {
                    log.info("Selected skill: {}", skill);
                    selectedSkill = true;
                    break;
                }
            }

            // Add appropriate combat option to the list
            if (selectedSkill) {
                attackStyleToTrain = attackStyle;
                switch (i) {
                    case 0:
                        componentsToDisplay.add(WidgetInfo.COMBAT_STYLE_ONE);
                        break;
                    case 1:
                        componentsToDisplay.add(WidgetInfo.COMBAT_STYLE_TWO);
                        break;
                    case 2:
                        componentsToDisplay.add(WidgetInfo.COMBAT_STYLE_THREE);
                        break;
                    case 3:
                        componentsToDisplay.add(WidgetInfo.COMBAT_STYLE_FOUR);
                        break;
                    case 4:
                        componentsToDisplay.add(WidgetInfo.COMBAT_SPELLS);
                        break;
                    case 5:
                        // Magic staves defensive casting mode
                        componentsToDisplay.add(WidgetInfo.COMBAT_DEFENSIVE_SPELL_BOX);
                        componentsToDisplay.add(WidgetInfo.COMBAT_DEFENSIVE_SPELL_ICON);
                        componentsToDisplay.add(WidgetInfo.COMBAT_DEFENSIVE_SPELL_SHIELD);
                        componentsToDisplay.add(WidgetInfo.COMBAT_DEFENSIVE_SPELL_TEXT);
                        break;
                }
            }
        }
        WidgetInfo componentToDisplay = componentsToDisplay.get(random.nextInt(componentsToDisplay.size()));
        attackStyleToTrain = attackStyles[componentToDisplay.ordinal() - 233];
        // Return a random component if the list is not empty, otherwise return null
        return componentToDisplay;
    }

    private boolean isMaxed() {
        return getSkillLevel(Skill.ATTACK) == 99 && getSkillLevel(Skill.STRENGTH) == 99 && getSkillLevel(Skill.DEFENCE) == 99;
    }

    private boolean disableIfMaxed(boolean disable) {
        return isMaxed() && disable;
    }

    @Override
    public void shutdown() {
        super.shutdown();

    }
}