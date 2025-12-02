package net.runelite.client.plugins.microbot.util.prayer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.annotations.Component;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.*;
import java.util.Arrays;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class Rs2Prayer {
    
    @Component
    private static final int QUICK_PRAYER_SELECT_COMPONENT_ID = 5046276;
    @Component
    private static final int QUICK_PRAYER_DONE_BUTTON_COMPONENT_ID = 5046277;
    @Component
    private static final int QUICK_PRAYER_ORB_COMPONENT_ID = 10485779;

	/**
	 * Toggles a prayer on or off. If the prayer is already in the desired state, no action is taken.
	 *
	 * @param prayer the prayer to toggle
	 */
	public static void toggle(Rs2PrayerEnum prayer) {
		if (isOutOfPrayer()) return;
		invokePrayer(prayer, false);
	}

	/**
	 * Toggles a prayer to a specific state (on or off).
	 *
	 * @param prayer the prayer to toggle
	 * @param on true to enable the prayer, false to disable it
	 * @return true if the prayer is in the desired state after the operation, false otherwise
	 */
	public static boolean toggle(Rs2PrayerEnum prayer, boolean on) {
		return toggle(prayer, on, false); // Default to not using the mouse to ensure compatibility with previous behavior
	}

	/**
	 * Toggles a prayer to a specific state (on or off) with optional mouse control.
	 * If using mouse, will automatically switch to the prayer tab if not already active.
	 *
	 * @param prayer the prayer to toggle
	 * @param on true to enable the prayer, false to disable it
	 * @param withMouse true to use mouse
	 * @return true if the prayer is in the desired state after the operation, false otherwise
	 */
	public static boolean toggle(Rs2PrayerEnum prayer, boolean on, boolean withMouse) {
		if (isOutOfPrayer()) return false;

		if (isPrayerActive(prayer) == on) return true;

		if (withMouse && Rs2Tab.getCurrentTab() != InterfaceTab.PRAYER)
		{
			Rs2Tab.switchTo(InterfaceTab.PRAYER);
		}

		invokePrayer(prayer, withMouse);

		return sleepUntil(() -> isPrayerActive(prayer) == on, 10_000);
	}

	/**
	 * Invokes a prayer action
	 * Creates a menu entry and executes it with the appropriate bounds.
	 *
	 * @param prayer the prayer to invoke
	 * @param withMouse true to use mouse clicks with prayer bounds
	 */
	private static void invokePrayer(Rs2PrayerEnum prayer, boolean withMouse) {
		NewMenuEntry menuEntry = new NewMenuEntry()
				.param0(-1)
				.param1(prayer.getIndex())
				.opcode(MenuAction.CC_OP.getId())
				.identifier(1)
				.itemId(-1)
				.option("Activate");

		Rectangle prayerBounds = withMouse ? getPrayerBounds(prayer) : Rs2UiHelper.getDefaultRectangle();

        Microbot.doInvoke(menuEntry, prayerBounds);
        // Microbot.getClient().menuAction(-1, prayer.getIndex(), MenuAction.CC_OP, 1, -1, "Activate", "Activate");
	}

	/**
	 * Gets the bounds of a specific prayer widget
	 * Returns a default rectangle if the widget is not found or has invalid bounds.
	 *
	 * @param prayer the prayer to get bounds for
	 * @return the bounds of the prayer widget, or default rectangle if not available
	 */
	private static Rectangle getPrayerBounds(Rs2PrayerEnum prayer) {
		Widget prayerWidget = Rs2Widget.getWidget(prayer.getIndex());
		if (prayerWidget == null) {
			log.warn("Prayer widget not found: {}", prayer.getName());
			return Rs2UiHelper.getDefaultRectangle(); // return a default rectangle if the widget is not found
		}

		Rectangle bounds = prayerWidget.getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
			log.warn("Invalid prayer bounds for: {}", prayer.getName());
			return Rs2UiHelper.getDefaultRectangle(); // return a default rectangle if bounds are invalid
		}

		return bounds;
	}

    /**
     * Checks if a specific prayer is set as a quick prayer.
     * Quick prayers are prayers that can be activated/deactivated with the quick prayer orb.
     *
     * @param prayer the prayer to check
     * @return true if the prayer is set as a quick prayer, false otherwise
     */
    public static boolean isQuickPrayerSet(Rs2PrayerEnum prayer) {
        final int selectedQuickPrayers = Microbot.getVarbitValue(VarbitID.QUICKPRAYER_SELECTED);
        return (selectedQuickPrayers & (1 << prayer.getQuickPrayerIndex())) != 0;
    }

	/**
	 * Checks if the player has any quick prayers configured.
	 *
	 * @return true if at least one quick prayer is set, false if no quick prayers are configured
	 */
	public static boolean hasAnyQuickPrayers() {
		return Microbot.getVarbitValue(VarbitID.QUICKPRAYER_SELECTED) > 0;
	}

	/**
	 * Checks if a specific prayer is currently active.
	 *
	 * @param prayer the prayer to check
	 * @return true if the prayer is currently active, false otherwise
	 */
	public static boolean isPrayerActive(Rs2PrayerEnum prayer) {
		return Microbot.getVarbitValue(prayer.getVarbit()) == 1;
	}

    /**
     * Checks if quick prayers are currently enabled/active.
     * When quick prayers are active, all configured quick prayers are turned on.
     *
     * @return true if quick prayers are currently active, false otherwise
     */
    public static boolean isQuickPrayerEnabled() {
        return Microbot.getVarbitValue(VarbitID.QUICKPRAYER_ACTIVE) == 1;
    }

    public static boolean setQuickPrayers(Rs2PrayerEnum[] prayers) {
        if (Rs2Widget.isHidden(QUICK_PRAYER_ORB_COMPONENT_ID)) return false;

        // Open the menu
        Microbot.doInvoke(new NewMenuEntry()
                .option("Setup")
                .param0(-1)
                .param1(QUICK_PRAYER_ORB_COMPONENT_ID)
                .opcode(MenuAction.CC_OP.getId())
                .identifier(2)
                .itemId(-1)
                .target("Quick-prayers"), new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));

        sleepUntil(() -> !Rs2Widget.isHidden(QUICK_PRAYER_SELECT_COMPONENT_ID));

        for (Rs2PrayerEnum prayer : prayers) {
            if(isQuickPrayerSet(prayer)) continue;
            Microbot.doInvoke(new NewMenuEntry()
                    .option(prayer.getName())
                    .param0(prayer.getQuickPrayerIndex())
                    .param1(QUICK_PRAYER_SELECT_COMPONENT_ID)
                    .opcode(MenuAction.CC_OP.getId())
                    .identifier(1)
                    .itemId(-1)
                    .target("Toggle"), new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        }

        Microbot.doInvoke(new NewMenuEntry()
                .option("Done")
                .param0(-1)
                .param1(QUICK_PRAYER_DONE_BUTTON_COMPONENT_ID)
                .opcode(MenuAction.CC_OP.getId())
                .identifier(1)
                .itemId(-1)
                .target(""), new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        return true;
    }

	/**
	 * Toggles quick prayers on or off
	 * Does nothing if the player is out of prayer points or has no quick prayers configured.
	 */
	public static void toggleQuickPrayer() {
		if (isOutOfPrayer() || !hasAnyQuickPrayers()) return;
		invokeQuickPrayer(false);
	}

	/**
	 * Toggles quick prayers to a specific state
	 *
	 * @param on true to enable quick prayers, false to disable them
	 * @return true if quick prayers are in the desired state after the operation, false otherwise
	 */
	public static boolean toggleQuickPrayer(boolean on) {
		return toggleQuickPrayer(on, false); // Default to not using the mouse to ensure compatibility with previous behavior
	}

    /**
     * Toggles quick prayers to a specific state with optional mouse control.
     * Quick prayers allow activating/deactivating multiple configured prayers at once.
     *
     * @param on true to enable quick prayers, false to disable them
     * @param withMouse true to use mouse
     * @return true if quick prayers are in the desired state after the operation, false otherwise
     */
    public static boolean toggleQuickPrayer(boolean on, boolean withMouse) {
		if (!hasAnyQuickPrayers()) return false;
		if (isOutOfPrayer()) return false;
		if (on == isQuickPrayerEnabled()) return true;

		boolean wasActive = isQuickPrayerEnabled();

		invokeQuickPrayer(withMouse);

        return sleepUntil(() -> isQuickPrayerEnabled() != wasActive, 10_000);
    }

	/**
	 * Invokes the quick prayer orb action
	 * Creates a menu entry for the quick prayer orb and executes it.
	 *
	 * @param withMouse true to use mouse with orb bounds
	 */
	private static void invokeQuickPrayer(boolean withMouse) {
		NewMenuEntry entry = new NewMenuEntry()
				.param0(-1)
				.param1(QUICK_PRAYER_ORB_COMPONENT_ID)
				.opcode(MenuAction.CC_OP.getId())
				.identifier(1)
				.itemId(-1)
				.option("Quick-prayers");

		Microbot.doInvoke(entry, withMouse ? getQuickPrayerOrbBounds() : Rs2UiHelper.getDefaultRectangle());
	}

	/**
	 * Gets the bounds of the quick prayer orb widget
	 * The quick prayer orb is used to toggle all configured quick prayers at once.
	 * Returns a default rectangle if the widget is not found or has invalid bounds.
	 *
	 * @return the bounds of the quick prayer orb widget, or default rectangle if not available
	 */
	private static Rectangle getQuickPrayerOrbBounds() {
		Widget quickPrayerOrbWidget = Rs2Widget.getWidget(QUICK_PRAYER_ORB_COMPONENT_ID);
		if (quickPrayerOrbWidget == null) {
			log.warn("Quick prayer orb widget not found");
			return Rs2UiHelper.getDefaultRectangle(); // return a default rectangle if the widget is not found
		}

		Rectangle bounds = quickPrayerOrbWidget.getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
			log.warn("Invalid quick prayer orb bounds");
			return Rs2UiHelper.getDefaultRectangle(); // return a default rectangle if bounds are invalid
		}

		return bounds;
	}

    /**
     * Checks if the player has run out of prayer points.
     * When out of prayer points, prayers cannot be activated and existing prayers will be disabled.
     *
     * @return true if the player's current prayer level is 0 or below, false otherwise
     */
    public static boolean isOutOfPrayer() {
        return Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) <= 0;
    }

    /**
     * Disables all active prayers.
     */
    public static void disableAllPrayers() {
        disableAllPrayers(false);
    }

    /**
     * Disables all active prayers.
     * @param withMouse whether to use mouse clicks for disabling prayers
     */
    public static void disableAllPrayers(boolean withMouse) {
        Arrays.stream(Rs2PrayerEnum.values())
            .filter(Rs2Prayer::isPrayerActive)
            .forEach(prayer -> Rs2Prayer.toggle(prayer, false, withMouse));
    }

    /**
     * Disables all active prayers except the ones specified in the array.
     * @param prayersToKeep array of prayers to keep active
     */
    public static void disableAllPrayersExcept(Rs2PrayerEnum[] prayersToKeep) {
        disableAllPrayersExcept(prayersToKeep, false);
    }

    /**
     * Disables all active prayers except the ones specified in the array.
     * @param prayersToKeep array of prayers to keep active
     * @param withMouse whether to use mouse clicks for disabling prayers
     */
    public static void disableAllPrayersExcept(Rs2PrayerEnum[] prayersToKeep, boolean withMouse) {
        Arrays.stream(Rs2PrayerEnum.values())
            .filter(Rs2Prayer::isPrayerActive)
            .filter(prayer -> !Arrays.asList(prayersToKeep).contains(prayer))
            .forEach(prayer -> Rs2Prayer.toggle(prayer, false, withMouse));
    }

    /**
     * Enables the specified prayers.
     * @param prayers array of prayers to enable
     */
    public static void enablePrayers(Rs2PrayerEnum[] prayers) {
        enablePrayers(prayers, false);
    }

    /**
     * Enables the specified prayers.
     * @param prayers array of prayers to enable
     * @param withMouse whether to use mouse clicks for enabling prayers
     */
    public static void enablePrayers(Rs2PrayerEnum[] prayers, boolean withMouse) {
        Arrays.stream(prayers)
            .filter(prayer -> !Rs2Prayer.isPrayerActive(prayer))
            .forEach(prayer -> Rs2Prayer.toggle(prayer, true, withMouse));
    }

    public static Rs2PrayerEnum getActiveProtectionPrayer() {
        return Stream.of(
                        Rs2PrayerEnum.PROTECT_MAGIC,
                        Rs2PrayerEnum.PROTECT_RANGE,
                        Rs2PrayerEnum.PROTECT_MELEE
                )
                .filter(Rs2Prayer::isPrayerActive)
                .findFirst()
                .orElse(null);
    }

    public static void swapOverHeadPrayer(Rs2PrayerEnum prayer) {
        Rs2PrayerEnum activeProtectionPrayer = getActiveProtectionPrayer();
        if (activeProtectionPrayer == prayer) {
            return;
        }
		Rs2Prayer.toggle(prayer, true);
    }

    public static boolean isRangePrayerActive() {
        return Stream.of(
                Rs2PrayerEnum.SHARP_EYE,
                Rs2PrayerEnum.HAWK_EYE,
                Rs2PrayerEnum.EAGLE_EYE,
                Rs2PrayerEnum.RIGOUR
        ).anyMatch(Rs2Prayer::isPrayerActive);
    }

    public static boolean isMagePrayerActive() {
        return Stream.of(
                Rs2PrayerEnum.MYSTIC_WILL,
                Rs2PrayerEnum.MYSTIC_LORE,
                Rs2PrayerEnum.MYSTIC_MIGHT,
                Rs2PrayerEnum.AUGURY
        ).anyMatch(Rs2Prayer::isPrayerActive);
    }

    public static boolean isMeleePrayerActive() {
        return Stream.of(
                Rs2PrayerEnum.PIETY,
                Rs2PrayerEnum.CHIVALRY,
                Rs2PrayerEnum.ULTIMATE_STRENGTH,
                Rs2PrayerEnum.SUPERHUMAN_STRENGTH
        ).anyMatch(Rs2Prayer::isPrayerActive);
    }

    public static Rs2PrayerEnum getBestMagePrayer() {
        int prayerLevel = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        boolean auguryUnlocked = isAuguryUnlocked();

        if (auguryUnlocked && prayerLevel >= Rs2PrayerEnum.AUGURY.getLevel())
            return Rs2PrayerEnum.AUGURY;
        if (prayerLevel >= Rs2PrayerEnum.MYSTIC_MIGHT.getLevel())
            return Rs2PrayerEnum.MYSTIC_MIGHT;
        if (prayerLevel >= Rs2PrayerEnum.MYSTIC_LORE.getLevel())
            return Rs2PrayerEnum.MYSTIC_LORE;
        if (prayerLevel >= Rs2PrayerEnum.MYSTIC_WILL.getLevel())
            return Rs2PrayerEnum.MYSTIC_WILL;

        return null;
    }

    public static Rs2PrayerEnum getBestRangePrayer() {
        int prayerLevel = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        boolean rigourUnlocked = isRigourUnlocked();

        if (rigourUnlocked && prayerLevel >= Rs2PrayerEnum.RIGOUR.getLevel())
            return Rs2PrayerEnum.RIGOUR;
        if (prayerLevel >= Rs2PrayerEnum.EAGLE_EYE.getLevel())
            return Rs2PrayerEnum.EAGLE_EYE;
        if (prayerLevel >= Rs2PrayerEnum.HAWK_EYE.getLevel())
            return Rs2PrayerEnum.HAWK_EYE;
        if (prayerLevel >= Rs2PrayerEnum.SHARP_EYE.getLevel())
            return Rs2PrayerEnum.SHARP_EYE;

        return null;
    }

    public static Rs2PrayerEnum getBestMeleePrayer() {
        int prayerLevel = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        int defenceLevel = Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);
        boolean knightWaveTrainingGroundComplete = Microbot.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE) == 8;

        if (knightWaveTrainingGroundComplete && prayerLevel >= Rs2PrayerEnum.PIETY.getLevel() && defenceLevel >= 70)
            return Rs2PrayerEnum.PIETY;
        if (knightWaveTrainingGroundComplete && prayerLevel >= Rs2PrayerEnum.CHIVALRY.getLevel() && defenceLevel >= 65)
            return Rs2PrayerEnum.CHIVALRY;
        if (prayerLevel >= Rs2PrayerEnum.ULTIMATE_STRENGTH.getLevel())
            return Rs2PrayerEnum.ULTIMATE_STRENGTH;
        if (prayerLevel >= Rs2PrayerEnum.SUPERHUMAN_STRENGTH.getLevel())
            return Rs2PrayerEnum.SUPERHUMAN_STRENGTH;

        return null;
    }
    public static boolean isRigourUnlocked() {
        return !(Microbot.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED) == 0) && Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= 74 && Microbot.getClient().getRealSkillLevel(Skill.DEFENCE) >= 70;
    }

    public static boolean isPietyUnlocked() {
        return Microbot.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE) == 8 && Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= 70 && Microbot.getClient().getRealSkillLevel(Skill.DEFENCE) >= 70;
    }
    public static boolean isChivalryUnlocked() {
        return Microbot.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE) == 8 && Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= 60 && Microbot.getClient().getRealSkillLevel(Skill.DEFENCE) >= 65;
    }

    public static boolean isAuguryUnlocked() {
        return !(Microbot.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED) == 0) && Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= 77 && Microbot.getClient().getRealSkillLevel(Skill.DEFENCE) >= 70;
    }
    public static boolean isPreserveUnlocked() {
        return !(Microbot.getVarbitValue(VarbitID.PRAYER_PRESERVE_UNLOCKED) == 0) && Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= 55;
    }
    public static boolean isDeadeyeUnlocked() {
        return !(Microbot.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED) == 0) && Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= 62;
    }
    public static boolean isMysticVigourUnlocked() {
        return !(Microbot.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) == 0) && Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= 62;
    }
    
}