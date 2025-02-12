package net.runelite.client.plugins.microbot.aiofighter.skill;

import java.util.List;

public class AttackOption {
    private final AttackType attackType;
    private final String style;
    private final String attackStyle;
    private final List<String> experienceTypes;

    public AttackOption(AttackType attackType, String style, String attackStyle, List<String> experienceTypes) {
        this.attackType = attackType;
        this.style = style;
        this.attackStyle = attackStyle;
        this.experienceTypes = experienceTypes;
    }

    public AttackType getAttackType() {
        return attackType;
    }

    public String getStyle() {
        return style;
    }

    public String getAttackStyle() {
        return attackStyle;
    }

    public List<String> getExperienceTypes() {
        return experienceTypes;
    }
}
