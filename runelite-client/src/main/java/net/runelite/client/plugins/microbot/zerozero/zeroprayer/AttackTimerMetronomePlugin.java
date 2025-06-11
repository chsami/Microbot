package net.runelite.client.plugins.microbot.zerozero.zeroprayer;


/*
 * Copyright (c) 2022, Nick Graves <https://github.com/ngraves95>
 * Copyright (c) 2024, Lexer747 <https://github.com/Lexer747>
 * Copyright (c) 2024, Richardant <https://github.com/Richardant>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.NPCManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

@Slf4j
@PluginDescriptor(
        name = PluginDescriptor.zerozero + "Prayer Helper",
        description = "Prayer helper",
        tags = {"timers", "overlays", "tick", "Lazy Flicking", "Prayer"},
        enabledByDefault = false
)
public class AttackTimerMetronomePlugin extends Plugin
{
    public enum AttackState {
        NOT_ATTACKING,
        DELAYED_FIRST_TICK,
        DELAYED,
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ConfigManager configManager;

    @Inject
    private AttackTimerMetronomeTileOverlay overlay;

    @Inject
    private AttackTimerMetronomeConfig config;

    @Inject
    private ItemManager itemManager;

    @Inject
    private Client client;

    @Inject
    private NPCManager npcManager;

    public int tickPeriod = 0;

    final int ATTACK_DELAY_NONE = 0;

    private int uiUnshowDebounceTickCount = 0;
    private int uiUnshowDebounceTicksMax = 1;

    public int attackDelayHoldoffTicks = ATTACK_DELAY_NONE;

    public AttackState attackState = AttackState.NOT_ATTACKING;
    // The state of the renderer, will lag a few cycles behind the plugin's state. "cycles" in this comment
    // refers to the client.getGameCycle() method, a cycle occurs every 20ms, meaning 30 of them occur per
    // game tick.
    public AttackState renderedState = AttackState.NOT_ATTACKING;

    public static final int RIGOUR_UNLOCKED = 5451;
    public static final int AUGURY_UNLOCKED = 5452;
    public static final int CAMELOT_TRAINING_ROOM_STATUS = 3909;

    public Color CurrentColor = Color.WHITE;

    public int DEFAULT_SIZE_UNIT_PX = 25;

    public static final int SALAMANDER_SET_ANIM_ID = 952; // Used by all 4 types of salamander https://oldschool.runescape.wiki/w/Salamander

    private final int DEFAULT_FOOD_ATTACK_DELAY_TICKS = 3;
    private final int KARAMBWAN_ATTACK_DELAY_TICKS = 2;
    public static final int EQUIPPING_MONOTONIC = 384; // From empirical testing this clientint seems to always increase whenever the player equips an item
    private Spellbook currentSpellBook = Spellbook.STANDARD;
    private int lastEquippingMonotonicValue = -1;
    private int soundEffectTick = -1;
    private int soundEffectId = -1;
    public Dimension DEFAULT_SIZE = new Dimension(DEFAULT_SIZE_UNIT_PX, DEFAULT_SIZE_UNIT_PX);

    private Rs2PrayerEnum activePrayer; // Track the currently active prayer
    private int prayerDeactivationTick = -1; // Tracks when to deactivate the prayer

    private AttackStyle cachedAttackStyle = null;
    private boolean cachedIsChargedStaff = false;
    private int lastCheckedWeaponId = -1;

    private static final int OUT_OF_COMBAT_TIMEOUT_TICKS = 150; // ~3000ms (150 game ticks)
    private int outOfCombatTicks = 0; // Counter for time since last attack

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged)
    {
        if (varbitChanged.getVarbitId() == Varbits.SPELLBOOK)
        {
            currentSpellBook = Spellbook.fromVarbit(varbitChanged.getValue());
        }
    }

    // onVarbitChanged happens when the user causes some interaction therefore we can't rely on some fixed
    // timing relative to a tick. A player can swap many items in the duration of the a tick.
    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged varClientIntChanged)
    {
        final int currentMagicVarBit = client.getVarcIntValue(EQUIPPING_MONOTONIC);
        if (currentMagicVarBit <= lastEquippingMonotonicValue)
        {
            return;
        }
        lastEquippingMonotonicValue = currentMagicVarBit;

        // This windowing safe guards of from late swaps inside a tick, if we have already rendered the tick
        // then we shouldn't perform another attack.
        boolean preAttackWindow = attackState == AttackState.DELAYED_FIRST_TICK && renderedState != attackState;
        if (preAttackWindow)
        {
            // "Perform an attack" this is overwrites the last attack since we now know the user swapped
            // "Something" this tick, the equipped weapon detection will pick up specific weapon swaps. Even
            // swapping more than 1 weapon inside a single tick.
            performAttack();
        }
    }

    // onSoundEffectPlayed used to track spell casts, for when the player casts a spell on first tick coming
    // off cooldown, in some cases (e.g. ice barrage) the player will have no animation. Also they don't have
    // a projectile to detect instead :/
    @Subscribe
    public void onSoundEffectPlayed(SoundEffectPlayed event)
    {
        // event.getSource() will be null if the player cast a spell, it's only for area sounds.
        soundEffectTick = client.getTickCount();
        soundEffectId = event.getSoundId();
    }

    // endregion

    @Provides
    AttackTimerMetronomeConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AttackTimerMetronomeConfig.class);
    }

    private int getItemIdFromContainer(ItemContainer container, int slotID)
    {
        if (container == null) {
            return -1;
        }
        final Item item = container.getItem(slotID);
        return (item != null) ? item.getId() : -1;
    }

    private int getWeaponId() {
        return getItemIdFromContainer(client.getItemContainer(InventoryID.EQUIPMENT),
                EquipmentInventorySlot.WEAPON.getSlotIdx());
    }

    private ItemStats getWeaponStats(int weaponId)
    {
        return itemManager.getItemStats(weaponId);
    }

    private AttackStyle getAttackStyle()
    {
        // Get current weapon and animation
        int weaponId = getWeaponId();
        AnimationData currentAnimation = AnimationData.fromId(client.getLocalPlayer().getAnimation());

        // Check if the weapon changed
        if (weaponId != lastCheckedWeaponId)
        {
            lastCheckedWeaponId = weaponId;
            cachedAttackStyle = null;
            cachedIsChargedStaff = false;
            log.debug("Weapon changed to ID: {}", weaponId);
        }

        // Use cached values if available
        if (cachedAttackStyle != null)
        {
            // Revalidate the attack style if the animation changes
            if (currentAnimation != null && !matchesAttackStyle(currentAnimation.attackStyle, cachedAttackStyle))
            {
                log.debug("Animation changed: resetting cached attack style");
                cachedAttackStyle = null;
            }
            else
            {
                return cachedAttackStyle;
            }
        }

        // Determine if using a charged staff
        PoweredStaves stave = PoweredStaves.getPoweredStaves(weaponId, currentAnimation);
        if (stave != null)
        {
            cachedIsChargedStaff = true;
            cachedAttackStyle = AttackStyle.CASTING;
            log.debug("Detected charged staff: {} with animation: {}", stave, currentAnimation);
            return cachedAttackStyle;
        }

        cachedIsChargedStaff = false;

        // Fallback to varbit-based attack style detection
        final int currentAttackStyleVarbit = client.getVarpValue(VarPlayer.ATTACK_STYLE);
        final int currentEquippedWeaponTypeVarbit = client.getVarbitValue(Varbits.EQUIPPED_WEAPON_TYPE);
        AttackStyle[] attackStyles = WeaponType.getWeaponType(currentEquippedWeaponTypeVarbit).getAttackStyles();

        if (currentAttackStyleVarbit < attackStyles.length)
        {
            cachedAttackStyle = attackStyles[currentAttackStyleVarbit];
            log.debug("Determined attack style: {}", cachedAttackStyle);
            return cachedAttackStyle;
        }

        // Fallback to default
        cachedAttackStyle = AttackStyle.ACCURATE;
        log.debug("Fallback to default attack style: ACCURATE");
        return cachedAttackStyle;
    }

    private boolean matchesAttackStyle(AnimationData.AttackStyle animStyle, AttackStyle cachedStyle)
    {
        // Compare based on a shared property, e.g., name or ordinal
        if (animStyle == null || cachedStyle == null)
        {
            return false;
        }
        return animStyle.name().equalsIgnoreCase(cachedStyle.name());
    }






    private int applyRangedAndMeleeRelicSpeed(int baseSpeed)
    {
        if (baseSpeed >= 4) {
            return baseSpeed / 2;
        } else {
            return (baseSpeed + 1) / 2;
        }
    }

    private boolean isRedKerisSpecAnimation(AnimationData animation)
    {
        return animation == AnimationData.MELEE_RED_KERIS_SPEC;
    }

    static final int BloodMoonSetAnimId = 2792;

    private boolean getBloodMoonProc()
    {
        return client.getLocalPlayer().hasSpotAnim(BloodMoonSetAnimId);
    }

    private boolean getSalamanderAttack()
    {
        return client.getLocalPlayer().hasSpotAnim(SALAMANDER_SET_ANIM_ID);
    }

    private int adjustSpeedForLeaguesIfApplicable(int baseSpeed)
    {
        int leagueRelicVarbit = 0;
        if (client.getWorldType().contains(WorldType.SEASONAL)) {
            leagueRelicVarbit = client.getVarbitValue(Varbits.LEAGUE_RELIC_4);
        }

        AttackStyle attackStyle = getAttackStyle();

        switch (leagueRelicVarbit) {
            case 0:
                // No league relic active - player does not have t4 relic or is not in leagues.
                return baseSpeed;
            case 1:
                // Archer's Embrace (ranged).
                if (attackStyle == AttackStyle.RANGING ||
                        attackStyle == AttackStyle.LONGRANGE) {
                    return applyRangedAndMeleeRelicSpeed(baseSpeed);
                }
                break;
            case 2:
                // Brawler's Resolve (melee)
                if (attackStyle == AttackStyle.ACCURATE ||
                        attackStyle == AttackStyle.AGGRESSIVE ||
                        attackStyle == AttackStyle.CONTROLLED ||
                        attackStyle == AttackStyle.DEFENSIVE) {
                    return applyRangedAndMeleeRelicSpeed(baseSpeed);
                }
                break;
            case 3:
                // Superior Sorcerer (magic)
                if (attackStyle == AttackStyle.CASTING ||
                        attackStyle == AttackStyle.DEFENSIVE_CASTING) {
                    return 2;
                }
                break;
        }

        return baseSpeed;
    }

    private void setAttackDelay()
    {
        int weaponId = getWeaponId();
        AnimationData curAnimation = AnimationData.fromId(client.getLocalPlayer().getAnimation());
        PoweredStaves stave = PoweredStaves.getPoweredStaves(weaponId, curAnimation);
        boolean matchesSpellbook = matchesSpellbook(curAnimation);
        attackDelayHoldoffTicks = getWeaponSpeed(weaponId, stave, curAnimation, matchesSpellbook);
    }

    // matchesSpellbook tries two methods, matching the animation the spell book based on the enum of
    // pre-coded matches, and then the second set of matches against the known sound id of the spell (which
    // unfortunately doesn't work if the player has them disabled).
    private boolean matchesSpellbook(AnimationData curAnimation)
    {
        if (curAnimation != null && curAnimation.matchesSpellbook(currentSpellBook))
        {
            return true;
        }
        if (client.getTickCount() == soundEffectTick)
        {
            return CastingSoundData.getSpellBookFromId(soundEffectId) == currentSpellBook;
        }
        return false;
    }

    private int getWeaponSpeed(int weaponId, PoweredStaves stave, AnimationData curAnimation, boolean matchesSpellbook)
    {
        if (stave != null && stave.getAnimations().contains(curAnimation))
        {
            // We are currently dealing with a staves in which case we can make decisions based on the
            // spellbook flag. We can only improve this by using a deprecated API to check the projectile
            // matches the stave rather than a manual spell, but this is good enough for now.
            return adjustSpeedForLeaguesIfApplicable(4);
        }

        if (matchesSpellbook && isManualCasting(curAnimation))
        {
            // You can cast with anything equipped in which case we shouldn't look to invent for speed, it will instead always be 5.
            return adjustSpeedForLeaguesIfApplicable(5);
        }

        ItemStats weaponStats = getWeaponStats(weaponId);
        if (weaponStats == null) {
            return adjustSpeedForLeaguesIfApplicable(4); // Assume barehanded == 4t
        }
        ItemEquipmentStats e = weaponStats.getEquipment();
        int speed = e.getAspeed();

        if (getAttackStyle() == AttackStyle.RANGING && client.getVarpValue(VarPlayer.ATTACK_STYLE) == 1)
        { // Hack for index 1 => rapid
            speed -= 1; // Assume ranging == rapid. Also works for salamanders which attack 1 tick faster when using the ranged style
        }
        if (getBloodMoonProc())
        { // Similar hack as rapid, blood moon saves a tick when it proc's
            speed -= 1;
        }

        if (isRedKerisSpecAnimation(curAnimation))
        {
            speed += 4; // If the spec missed we are just wrong by 4-ticks IDC, requires spec tracking code similar to the spec plugin if we want this to be correct when we miss.
        }

        return adjustSpeedForLeaguesIfApplicable(speed); // Deadline for next available attack.
    }

    private static final List<Integer> SPECIAL_NPCS = Arrays.asList(10507, 9435, 9438, 9441, 9444); // Combat Dummy + Nightmare Pillars

    private boolean isPlayerAttacking()
    {
        int animationId = client.getLocalPlayer().getAnimation();
        if (AnimationData.isBlockListAnimation(animationId))
        {
            return false;
        }

        // Not walking is either any player animation or the edge cases which don't trigger an animation, e.g Salamander.
        boolean notWalking = animationId != -1 || getSalamanderAttack();

        // Testing if we are attacking by checking the target is more future
        // proof to new weapons which don't need custom code and the weapon
        // stats are enough.
        Actor target = client.getLocalPlayer().getInteracting();
        if (target != null && (target instanceof NPC))
        {
            final NPC npc = (NPC) target;
            boolean containsAttackOption = Arrays.stream(npc.getComposition().getActions()).anyMatch("Attack"::equals);
            Integer health = npcManager.getHealth(npc.getId());
            boolean hasHealthAndLevel = health != null && health > 0 && target.getCombatLevel() > 0;
            boolean attackingNPC = hasHealthAndLevel || SPECIAL_NPCS.contains(npc.getId()) || containsAttackOption;
            // just having a target is not enough the player may be out of range, we must wait for any
            // animation which isn't running/walking/etc
            return attackingNPC && notWalking;
        }
        if (target != null && (target instanceof Player))
        {
            return notWalking;
        }

        AnimationData fromId = AnimationData.fromId(animationId);
        if (fromId == AnimationData.RANGED_BLOWPIPE || fromId == AnimationData.RANGED_BLAZING_BLOWPIPE)
        {
            // These two animations are the only ones which exceed the duration of their attack cooldown (when
            // on rapid), so in this case DO NOT fall back the animation as it is un-reliable.
            return false;
        }
        // fall back to animations.
        return fromId != null;
    }

    private boolean isManualCasting(AnimationData curId)
    {
        // If you use a weapon like a blow pipe which has an animation longer than it's cool down then cast an
        // ancient attack it wont have an animation at all. We can therefore need to detect this with a list
        // of sounds instead. This obviously doesn't work if the player is muted. ATM I can't think of a way
        // to detect this type of attack as a cast, only sound is an indication that the player is on
        // cooldown, melee attacks, etc will trigger an animation overwriting the last frame of the blowpipe's
        // idle animation.
        boolean castingFromSound = client.getTickCount() == soundEffectTick ? CastingSoundData.isCastingSound(soundEffectId) : false;
        boolean castingFromAnimation = AnimationData.isManualCasting(curId);
        return castingFromSound || castingFromAnimation;
    }

    private void performAttack()
    {
        attackState = AttackState.DELAYED_FIRST_TICK;
        setAttackDelay();
        tickPeriod = attackDelayHoldoffTicks;
        uiUnshowDebounceTickCount = uiUnshowDebounceTicksMax;
    }

    public int getTicksUntilNextAttack()
    {
        return 1 + attackDelayHoldoffTicks;
    }

    public int getWeaponPeriod()
    {
        return tickPeriod;
    }

    public boolean isAttackCooldownPending()
    {
        return attackState == AttackState.DELAYED
            || attackState == AttackState.DELAYED_FIRST_TICK
            || uiUnshowDebounceTickCount > 0;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.SPAM)
        {
            return;
        }

        final String message = event.getMessage();

        if (message.startsWith("You eat") ||
                message.startsWith("You drink the wine")) {
            int attackDelay = (message.toLowerCase().contains("karambwan")) ?
                    KARAMBWAN_ATTACK_DELAY_TICKS :
                    DEFAULT_FOOD_ATTACK_DELAY_TICKS;

            if (attackState == AttackState.DELAYED) {
                attackDelayHoldoffTicks += attackDelay;
            }
        }
    }

    // onInteractingChanged is the driver for detecting if the player attacked out side the usual tick window
    // of the onGameTick events.
    @Subscribe
    public void onInteractingChanged(InteractingChanged interactingChanged)
    {
        Actor source = interactingChanged.getSource();
        Actor target = interactingChanged.getTarget();

        Player p = client.getLocalPlayer();

        if (source.equals(p) && (target instanceof NPC)) {
            switch (attackState) {
                case NOT_ATTACKING:
                    if (isPlayerAttacking()) {
                        performAttack();
                    }
                    break;
                case DELAYED_FIRST_TICK:

                case DELAYED:
                    break;
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        AttackTimerMetronomeConfig.PrayerMode prayerMode = config.enableLazyFlicking();
        int ticksUntilAttack = getTicksUntilNextAttack();

        // Skip all prayer logic if PrayerMode is NONE
        if (prayerMode == AttackTimerMetronomeConfig.PrayerMode.NONE)
        {
            Rs2Prayer.disableAllPrayers();
        }

        // Handle Lazy Flick Mode
        if (prayerMode == AttackTimerMetronomeConfig.PrayerMode.LAZY)
        {
            if (ticksUntilAttack > 0)
            {
                if (ticksUntilAttack == 2)
                {
                    Rs2PrayerEnum offensivePrayer = determineOffensivePrayer(getAttackStyle());
                    activatePrayer(offensivePrayer);
                    prayerDeactivationTick = 1;
                }
            }

            if (prayerDeactivationTick == 0)
            {
                Rs2Prayer.disableAllPrayers();
                prayerDeactivationTick = -1; // Reset deactivation tracker
            }

            if (prayerDeactivationTick > 0)
            {
                prayerDeactivationTick--;
            }
        }
        // Handle Normal Mode
        else if (prayerMode == AttackTimerMetronomeConfig.PrayerMode.NORMAL)
        {
            boolean isAttacking = isPlayerAttacking();

            if (isAttacking)
            {
                Rs2PrayerEnum offensivePrayer = determineOffensivePrayer(getAttackStyle());
                if (offensivePrayer != null && !offensivePrayer.equals(activePrayer))
                {
                    activatePrayer(offensivePrayer);
                    activePrayer = offensivePrayer; // Track the currently active prayer
                }
                outOfCombatTicks = 0; // Reset out-of-combat counter
            }
            else
            {
                outOfCombatTicks++;
                if (outOfCombatTicks >= OUT_OF_COMBAT_TIMEOUT_TICKS)
                {
                    Rs2Prayer.disableAllPrayers();
                    activePrayer = null; // Clear the active prayer
                    outOfCombatTicks = 0; // Reset after deactivation
                }
            }
        }

        // Update attack state logic (common for both modes)
        boolean isAttacking = isPlayerAttacking();
        switch (attackState)
        {
            case NOT_ATTACKING:
                if (isAttacking)
                {
                    performAttack();
                }
                else
                {
                    uiUnshowDebounceTickCount--;
                }
                break;

            case DELAYED_FIRST_TICK:
                attackState = AttackState.DELAYED;
                break;

            case DELAYED:
                if (attackDelayHoldoffTicks <= 0)
                {
                    if (isAttacking)
                    {
                        performAttack();
                    }
                    else
                    {
                        attackState = AttackState.NOT_ATTACKING;
                    }
                }
        }

        attackDelayHoldoffTicks--;
    }


    private void activatePrayer(Rs2PrayerEnum prayer) {
        if (prayer != null && !Rs2Prayer.isPrayerActive(prayer)) {
            Rs2Prayer.toggle(prayer, true);
            activePrayer = prayer; // Track the active prayer
        }
    }

    private Rs2PrayerEnum determineOffensivePrayer(AttackStyle attackStyle) {
        switch (attackStyle) {
            case ACCURATE: // Melee styles
            case AGGRESSIVE:
            case CONTROLLED:
            case DEFENSIVE:
                return Rs2Prayer.getBestMeleePrayer();

            case RANGING: // Ranged styles
            case LONGRANGE:
                return Rs2Prayer.getBestRangePrayer();

            case CASTING: // Magic styles
            case DEFENSIVE_CASTING:
                return Rs2Prayer.getBestMagePrayer();

            default:
                return null;
        }
    }




    public AttackStyle getCurrentAttackStyle()
    {
        return getAttackStyle();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (event.getGroup().equals("zprayerhelper"))
        {
            attackDelayHoldoffTicks = 0;

            activePrayer = null;
            outOfCombatTicks = 0;
            prayerDeactivationTick = -1;
            Rs2Prayer.disableAllPrayers();
        }
    }


    @Override
    protected void startUp() throws Exception
    {
        overlayManager.add(overlay);
        overlay.setPreferredSize(DEFAULT_SIZE);
    }

    @Override
    protected void shutDown() throws Exception
    {
        overlayManager.remove(overlay);
        attackDelayHoldoffTicks = 0;
        super.shutDown();
    }
}
