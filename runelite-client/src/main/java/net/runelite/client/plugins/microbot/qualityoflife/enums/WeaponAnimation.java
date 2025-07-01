package net.runelite.client.plugins.microbot.qualityoflife.enums;

import lombok.Getter;

@Getter
public enum WeaponAnimation {
    // --- Magic Animations ---
    // TODO: Add animation IDs for each magic weapon/animation
    // Example: STANDARD_CAST("Standard Cast", "Magic", 711),
    // ...
    // (List all Magic Weapon/animation names here as enum entries)

    // --- Ranged Animations ---
    // TODO: Add animation IDs for each ranged weapon/animation
    // Example: BOW_ATTACK("Bow Attack", "Ranged", 426),
    // ...
    // (List all Ranged Weapon/animation names here as enum entries)

    // --- Melee Animations ---
    // TODO: Add animation IDs for each melee weapon/animation
    // Example: SLASH_ATTACK("Slash Attack", "Melee", 451),
    // ...
    // (List all Melee Weapon/animation names here as enum entries)

    // --- Special Attack Animations ---
    // TODO: Add animation IDs for each special attack animation
    // Example: DRAGON_DAGGER_SPEC("Dragon Dagger Spec", "Special", 1062),
    // ...
    // (List all Special Attack animation names here as enum entries)

    STANDARD_CAST("Standard Cast", "Magic", 711),
    ENTANGLE_CAST("Entangle Cast", "Magic", 1162),
    STUN_CAST("Stun Cast", "Magic", 1167),
    ICE_BLITZ("Ice Blitz", "Magic", 1978),
    ICE_BARRAGE("Ice Barrage", "Magic", 1979),
    GOD_SPELL("God Spell", "Magic", 811),
    BOW_ATTACK("Bow Attack", "Ranged", 426),
    CROSSBOW_ATTACK("Crossbow Attack", "Ranged", 427),
    PUNCH("Punch", "Melee", 422),
    KICK("Kick", "Melee", 423),
    SLASH_ATTACK("Slash Attack", "Melee", 451),
    HALBERD_SLASH("Halberd Slash", "Melee", 440),
    DRAGON_DAGGER_STAB("Dragon Dagger Stab", "Melee", 402, 4520, 3300, 4225),
    TWOH_SLASH("2H Slash", "Melee", 407),
    SCYTHE_ATTACK("Scythe Attack", "Melee", 408);
    // Add more as needed from QoLScript

    private final String animationName;
    private final String attackType;
    private final int[] animationIds;

    WeaponAnimation(String animationName, String attackType, int... animationIds) {
        this.animationName = animationName;
        this.attackType = attackType;
        this.animationIds = animationIds;
    }

    public static WeaponAnimation getByAnimationId(int animationId) {
        for (WeaponAnimation anim : values()) {
            for (int id : anim.getAnimationIds()) {
                if (id == animationId) {
                    return anim;
                }
            }
        }
        return null;
    }

    public int[] getAnimationIds() {
        return animationIds;
    }
}
