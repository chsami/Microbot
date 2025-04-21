/*
 * Copyright (c) 2018, Nachtmerrie <https://github.com/Nachtmerrie>
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

package net.runelite.client.plugins.microbot.kittentracker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.infobox.Timer;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


@Slf4j
@PluginDescriptor(
        name = PluginDescriptor.See1Duck + " Kitten Tracker",
        description = "Automates the kitten growth process and provides notifications",
        enabledByDefault = false,
        tags = {"cat", "kitten", "duck", "s1d","see1duck","microbot"}
)
public class KittenPlugin extends Plugin {
    private static final int VAR_PLAYER_FOLLOWER = 447;
    private static final int WIDGET_ID_DIALOG_NOTIFICATION_GROUP_ID = 229;
    private static final int WIDGET_ID_DIALOG_PLAYER_TEXT = 6;
    private static final int WIDGET_ID_DIALOG_NOTIFICATION_TEXT = 1;

    private static final String DIALOG_CAT_STROKE = "That cat sure loves to be stroked.";
    private static final String DIALOG_CAT_BALL_OF_WOOL = "That kitten loves to play with that ball of wool."; // The typo is intentional and how the game reads it...
    private static final String DIALOG_CAT_GROWN = "Your kitten has grown into a healthy cat that can hunt for itself.";
    private static final String DIALOG_CAT_OVERGROWN = "Your cat has grown into a mighty feline, but it will no longer be able to chase vermin.";
    private static final String DIALOG_AFTER_TAKING_A_GOOD_LOOK = "After taking a good look at your kitten you guess that its age is: ";
    private static final String DIALOG_HAND_OVER_CAT_CIVILIAN = "You hand over the cat.You are given";
    private static final String DIALOG_GERTRUDE_GIVES_YOU_ANOTHER_KITTEN = "Gertrude gives you another kitten.";

    private static final String CHAT_STROKE_CAT = "You softly stroke your cat.";
    private static final String CHAT_THE_KITTEN_GOBBLES_UP_THE_FISH = "The kitten gobbles up the fish.";
    private static final String CHAT_THE_KITTEN_LAPS_UP_THE_MILK = "The kitten laps up the milk.";
    private static final String CHAT_YOUR_KITTEN_IS_HUNGRY = "Your kitten is hungry.";
    private static final String CHAT_YOUR_KITTEN_IS_VERY_HUNGRY = "Your kitten is very hungry.";
    private static final String CHAT_YOUR_KITTEN_WANTS_ATTENTION = "Your kitten wants attention.";
    private static final String CHAT_YOUR_KITTEN_REALLY_WANTS_ATTENTION = "Your kitten really wants attention.";
    private static final String CHAT_YOUR_KITTEN_GOT_LONELY_AND_RAN_OFF = "Your kitten got lonely and ran off.";
    private static final String CHAT_THE_CAT_HAS_RUN_AWAY = "The cat has run away.";

    private static final String NOTIFICATION_KITTEN_WILL_RUN_AWAY_IN_1_MINUTE = "Kitten will run away in 1 minute!";

    private static final String TOOLTIP_APPROXIMATE_TIME_LEFT_TO_GROW_INTO_A_CAT = "Approximate time left to grow into a cat";
    private static final String TOOLTIP_APPROXIMATE_TIME_LEFT_TO_TRANSFORM_INTO_AN_OVERGROWN_CAT = "Approximate time left to transform into an overgrown cat";
    private static final String TOOLTIP_TIME_UNTIL_YOUR_KITTEN_LEAVES_YOU_FOR_BEING_UNDERFED = "Time until your kitten leaves you for being underfed";
    private static final String TOOLTIP_APPROXIMATE_TIME_UNTIL_YOUR_KITTEN_LEAVES_YOU_FOR_BEING_NEGLECTFUL = "Approximate time until your kitten leaves you for being neglectful";

    public static final int HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS = 6 * 60; // 6 MINUTES
    public static final int HUNGRY_FINAL_WARNING_TIME_LEFT_IN_SECONDS = 3 * 60; // 3 MINUTES
    public static final long HUNGRY_TIME_ONE_MINUTE_WARNING_MS = 60 * 1000; // 1 MINUTE
    private static final int HUNGRY_TIME_BEFORE_FIRST_WARNING_IN_MINUTES = 24; // 24 MINUTES
    private static final int HUNGRY_TIME_BEFORE_FINAL_WARNING_IN_MINUTES = 27; // 27 MINUTES
    private static final int HUNGRY_TIME_BEFORE_KITTEN_RUNS_AWAY_IN_SECONDS = 30 * 60; // 30 MINUTES

    public static final int ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS = 6 * 90; // 9 MINUTES
    public static final int ATTENTION_FINAL_WARNING_TIME_LEFT_IN_SECONDS = 3 * 90; // 4.5 MINUTES
    public static final long ATTENTION_TIME_ONE_MINUTE_WARNING_MS = 60 * 1000; // 1 MINUTE
    public static final int ATTENTION_TIME_NEW_KITTEN_IN_SECONDS = 17 * 90; // 25.5 MINUTES
    public static final int ATTENTION_TIME_SINGLE_STROKE_IN_SECONDS = 18 * 60; // 18 MINUTES
    public static final int ATTENTION_TIME_MULTIPLE_STROKES_IN_SECONDS = 15 * 90; // 22.5 MINUTES
    public static final int ATTENTION_TIME_BALL_OF_WOOL_IN_SECONDS = 51 * 60; // 51 MINUTES
    private static final int ATTENTION_TIME_BEFORE_KITTEN_RUNS_AWAY_NEW_KITTEN_IN_SECONDS = ATTENTION_TIME_NEW_KITTEN_IN_SECONDS + ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS; // 36 MINUTES
    private static final int ATTENTION_TIME_BEFORE_KITTEN_RUNS_AWAY_SINGLE_STROKE_IN_SECONDS = ATTENTION_TIME_SINGLE_STROKE_IN_SECONDS + ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS; // 28.5 MINUTES
    private static final int ATTENTION_TIME_BEFORE_KITTEN_RUNS_AWAY_MULTIPLE_STROKES_IN_SECONDS = ATTENTION_TIME_MULTIPLE_STROKES_IN_SECONDS + ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS; // 31.5 MINUTES
    private static final int ATTENTION_TIME_BEFORE_KITTEN_RUNS_AWAY_BALL_OF_WOOL_IN_SECONDS = ATTENTION_TIME_BALL_OF_WOOL_IN_SECONDS + ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS; // 61.5 MINUTES

    private static final int TIME_TO_ADULTHOOD_IN_SECONDS = 3 * 3600; // 3 HOURS
    private static final int TIME_TILL_OVERGROWN_IN_SECONDS = (int) (2.5 * 3600); // 2-3 HOURS -> 2.5 HOURS

    private static final int GROWTH_TICK_IN_SECONDS = 90;  // kitten can only progress growth once every 90s.
    private static final int TICKS_TO_ADULTHOOD = 120; // 3 hours - each growth tick is 90 seconds.
    private static final int TICKS_TO_OVERGROWN = 100;  // 2.5 hours - assuming above timing is correct.
    private static final int TICKS_TO_HUNGER_RUN_AWAY = 20; // 30 min - each growth tick is 90 seconds.
    private static final int TICKS_HUNGER_FIRST_WARNING = 4; // 6 min
    private static final int TICKS_HUNGER_FINAL_WARNING = 2; // 3 min

    /*  See notes on attention timers/notifications in the README.  tl;dr: it's variable
    based on a few factors, but testing seems consistent, so it could be "solved."
    For now, I'm adding in 3 ticks as an "expected" delay - that is what happens next after being fed & stroked 2x
    at the same time, then waiting for notifications for your cat to tell you it needs food/attention,
    which seems like it would be the most common case.  Most people won't probably proactively feed
    their kitten before getting the notification.  I'll only add this in for the multiple stroke constant,
    as the others will likely not be synced up with the hunger timer as closely.
     */
    private static final int EXPECTED_ATTENTION_DELAY_TICKS = 3;

    /* 36 min - each growth tick is 90s.  Normally it *should* be 21 ticks/31.5 mins, but hunger notifications often delay this.
    should the hunger notification delays be "solved", this could get adjusted in the future, it's just a work-around
    for now. */
    private static final int TICKS_TO_ATTENTION_RUN_AWAY_MULTIPLE_STROKES = 21 + EXPECTED_ATTENTION_DELAY_TICKS;
    private static final int TICKS_TO_ATTENTION_RUN_AWAY_SINGLE_STROKE = 16; // 24m - it's variable so this isn't exactly accurate.
    private static final int TICKS_TO_ATTENTION_RUN_AWAY_BALL_OF_WOOL = 40; // 60
    private static final int TICKS_ATTENTION_FIRST_WARNING = 6; // 9m
    private static final int TICKS_ATTENTION_FINAL_WARNING = 3; // 4.5m

    private boolean ready;
    private Instant kittenLastAttentionTime;
    private int timeNeglected = 0;
    private int followerID = 0;
    private int previousFollowerId = 0;
    private KittenAttentionType lastAttentionType = KittenAttentionType.NEW_KITTEN;

    private int growthTicksAlive = 0;
    private int nextHungryTick = 0;
    private int nextAttentionTick = 0;
    private int secondsInTick = 0;
    private Duration timeInTick;
    private Instant growthTickStartTime = null;
    private Instant lastLoadingTime = null;
    private final ArrayList<Instant> growthTimes = new ArrayList<>();
    // the first growth tick can be very early upon login - probably something to do with loading the game or plugin.
    private boolean noGrowthSinceLoggedIn = true;

    /* Widget Group IDs for interfaces that stall kitten growth.  This is not all-inclusive.  It's just a makeshift
    replacement for watching for overhead text that would normally indicate kitten growth when the player count
    is very high and cant load all entities and your kitten might not be rendered.  The ones listed individually here
    are not included in WidgetID.java and I had to determine them manually.
    Rule of thumb: if you can't move around and keep the interface open, it will delay kitten growth if it is open.
     */
    private static final int KOUREND_FAVOUR_TASK_LIST_GROUP_ID = 626;
    private static final int EQUIPMENT_STATS_GROUP_ID = 84;
    private static final int COMBAT_ACHIEVEMENTS_OVERVIEW_GROUP_ID = 717;
    private static final int COMBAT_ACHIEVEMENTS_TASK_LIST_GROUP_ID = 715;
    private static final int COMBAT_ACHIEVEMENTS_BOSSES_GROUP_ID = 716;

    private static final int COMBAT_ACHIEVEMENTS_REWARDS_GROUP_ID = 714;

    private static final int BOND_POUCH_GROUP_ID = 65;
    private static final int NAME_CHANGER_GROUP_ID = 589;  // believe it or not this does pause kitten growth
    private static final int POLL_GROUP_ID = 345;
    private static final int POLL_HISTORY_GROUP_ID = 310;
    private static final int STEEL_KEY_RING_GROUP_ID = 127;
    private static final int MASTER_SCROLL_BOOK_GROUP_ID = 597;
    private static final int FORESTRY_KIT_GROUP_ID = 823;
    private static final int FORESTRY_KIT_GROUP_ID_2 = 822; // both 822 & 823 are open when the kit is open
    private static final int BANK_COLLECTION_BOX_GROUP_ID = 402;
    private static final int GRAND_EXCHANGE_ITEM_SETS_GROUP_ID = 451;
    private static final int GRAND_EXCHANGE_ITEM_SETS_2_GROUP_ID = 430;
    private static final int GRAND_EXCHANGE_HISTORY_GROUP_ID = 383;
    private static final int XP_LAMP_GROUP_ID = 240;  // same for book of knowledge from dunce random event

    List<Integer> unsafeIDs = new ArrayList<>(Arrays.asList(WidgetID.ACHIEVEMENT_DIARY_SCROLL_GROUP_ID,
            WidgetID.GUIDE_PRICE_GROUP_ID, WidgetID.KEPT_ON_DEATH_GROUP_ID, WidgetID.COLLECTION_LOG_ID,
            WidgetID.DIALOG_PLAYER_GROUP_ID, WidgetID.DIALOG_NPC_GROUP_ID, WidgetID.DIALOG_OPTION_GROUP_ID,
            WidgetID.DIALOG_DOUBLE_SPRITE_GROUP_ID, WidgetID.DIALOG_SPRITE_GROUP_ID,
            WidgetID.DIARY_QUEST_GROUP_ID, WidgetID.SEED_BOX_GROUP_ID, WidgetID.RUNE_POUCH_GROUP_ID,
            WidgetID.CLUE_SCROLL_GROUP_ID, WidgetID.CLUE_SCROLL_REWARD_GROUP_ID,
            WidgetID.BEGINNER_CLUE_MAP_CHAMPIONS_GUILD, WidgetID.BEGINNER_CLUE_MAP_DRAYNOR,
            WidgetID.BEGINNER_CLUE_MAP_VARROCK_EAST_MINE, WidgetID.BEGINNER_CLUE_MAP_NORTH_OF_FALADOR,
            WidgetID.BEGINNER_CLUE_MAP_WIZARDS_TOWER, KOUREND_FAVOUR_TASK_LIST_GROUP_ID, EQUIPMENT_STATS_GROUP_ID,
            COMBAT_ACHIEVEMENTS_OVERVIEW_GROUP_ID, COMBAT_ACHIEVEMENTS_TASK_LIST_GROUP_ID,
            COMBAT_ACHIEVEMENTS_BOSSES_GROUP_ID, COMBAT_ACHIEVEMENTS_REWARDS_GROUP_ID, BOND_POUCH_GROUP_ID,
            NAME_CHANGER_GROUP_ID, POLL_GROUP_ID, POLL_HISTORY_GROUP_ID, STEEL_KEY_RING_GROUP_ID,
            MASTER_SCROLL_BOOK_GROUP_ID, FORESTRY_KIT_GROUP_ID, FORESTRY_KIT_GROUP_ID_2,
            WidgetID.GRAND_EXCHANGE_GROUP_ID, WidgetID.DEPOSIT_BOX_GROUP_ID, BANK_COLLECTION_BOX_GROUP_ID,
            GRAND_EXCHANGE_ITEM_SETS_GROUP_ID, GRAND_EXCHANGE_ITEM_SETS_2_GROUP_ID, GRAND_EXCHANGE_HISTORY_GROUP_ID,
            WidgetID.SEED_VAULT_GROUP_ID, WidgetID.QUEST_COMPLETED_GROUP_ID, WidgetID.LEVEL_UP_GROUP_ID,
            WidgetID.FAIRY_RING_GROUP_ID, WidgetID.SHOP_GROUP_ID, WidgetID.SLAYER_REWARDS_GROUP_ID,
            WidgetID.DESTROY_ITEM_GROUP_ID, WidgetID.BANK_PIN_GROUP_ID, XP_LAMP_GROUP_ID, WidgetID.BANK_GROUP_ID));

    private Timer kittenAttentionTimer, growthTimer, kittenHungryTimer;

    private FollowerKind followerKind = FollowerKind.NON_FELINE;

    private boolean attentionNotificationSend = false;
    private boolean hungryNotificationSend = false;

    private final HashMap<Integer, Instant> openedWidgets = new HashMap<>();

    @Inject
    private Client client;
    @Inject
    private Notifier notifier;
    @Inject
    private KittenConfig config;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ItemManager itemManager;
    @Inject
    private InfoBoxManager infoBoxManager;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private KittenOverlay overlay;
    @Inject
    private KittenScript script;
    @Inject
    private KittenAttentionEvent kittenAttentionEvent;
    @Inject
    private FeedKittenEvent feedKittenEvent;

    @Provides
    KittenConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KittenConfig.class);
    }

    @Override
    public void startUp() {
        clientThread.invokeLater(this::checkForFollower);
        previousFollowerId = config.felineId();
        overlayManager.add(overlay);
        script.run(config);
        Microbot.getBlockingEventManager().add(kittenAttentionEvent);
        Microbot.getBlockingEventManager().add(feedKittenEvent);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        byeFollower();
        Microbot.getBlockingEventManager().remove(kittenAttentionEvent);
        Microbot.getBlockingEventManager().remove(feedKittenEvent);
        script.shutdown();
    }

    private void checkForFollower() {
        if (GameState.LOGGED_IN != client.getGameState()) {
            return;
        }

        if (playerHasFollower()) {
            newFollower();
        }
    }


    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (playerHasFollower() && followerID != getCurrentFollowerId()) { // player has a new follower
            checkForNewFollower();
        }

        if (!playerHasFollower() && 0 != followerID) // player lost its follower
        {
            byeFollower();
        }
    }

    private void checkForNewFollower() {
        followerID = getCurrentFollowerId();
        if (0 < followerID) // Varbit needs to fill up first after logging in
        {
            previousFollowerId = followerID; // config.felineId();
            newFollower();
        }
    }

    private int getCurrentFollowerId() {
        int followerVarPlayerValue = client.getVarpValue(VAR_PLAYER_FOLLOWER);
        // followerID is the first 2 bytes
        return (followerVarPlayerValue >> 16);
    }

    private void newFollower() {
        attentionNotificationSend = false;
        hungryNotificationSend = false;
        followerKind = FollowerKind.getFromFollowerId(followerID);

        switch (followerKind) {
            case KITTEN:
                if (followerID == previousFollowerId) // The same kitten is back!
                {
                    secondsInTick = 0;
                    growthTicksAlive = config.growthTicksAlive();
                    nextHungryTick = config.nextHungryTick();
                    nextAttentionTick = config.nextAttentionTick();
                    growthTickStartTime = Instant.now();
                    lastAttentionType = config.lastAttentionType();
                    // no need to subtract secondsInTick here - it will be zero by definition

                    addKittenGrowthBox((TICKS_TO_ADULTHOOD - growthTicksAlive) * GROWTH_TICK_IN_SECONDS);
                    addAttentionTimer((nextAttentionTick - growthTicksAlive) * 90);
                    addHungryTimer((nextHungryTick - growthTicksAlive) * 90);

                } else // new kitten, new timer
                {
                    config.lastAttentionType(KittenAttentionType.NEW_KITTEN);
                    kittenLastAttentionTime = Instant.now();

                    addKittenGrowthBox(TIME_TO_ADULTHOOD_IN_SECONDS);
                    addHungryTimer(HUNGRY_TIME_BEFORE_KITTEN_RUNS_AWAY_IN_SECONDS);
                    addAttentionTimer(ATTENTION_TIME_BEFORE_KITTEN_RUNS_AWAY_MULTIPLE_STROKES_IN_SECONDS);

                    // new stuff
                    config.growthTicksAlive(0);
                    growthTickStartTime = Instant.now();
                    nextHungryTick = TICKS_TO_HUNGER_RUN_AWAY;
                    nextAttentionTick = TICKS_TO_ATTENTION_RUN_AWAY_MULTIPLE_STROKES;
                }
                break;
            case NORMAL_CAT:
                if (followerID == previousFollowerId) { // The same cat is back!
                    growthTicksAlive = config.growthTicksAlive();
                    growthTickStartTime = Instant.now();
                    addKittenGrowthBox(((TICKS_TO_ADULTHOOD + TICKS_TO_OVERGROWN) - growthTicksAlive) *
                            GROWTH_TICK_IN_SECONDS);
                } else { // new cat, new timer
                    growthTickStartTime = Instant.now();
                    addKittenGrowthBox(TIME_TILL_OVERGROWN_IN_SECONDS);
                }
                break;
            case LAZY_CAT:
            case WILY_CAT:
            case OVERGROWN_CAT:
            case NON_FELINE:
                break;
        }
    }

    private void byeFollower() {
        switch (followerKind) {
            case KITTEN:
                saveGrowthProgress();
                growthTickStartTime = null;
                kittenLastAttentionTime = null;
                break;
            case NORMAL_CAT:
                saveGrowthProgress();
                growthTickStartTime = null;
                break;
            case LAZY_CAT:
            case WILY_CAT:
            case OVERGROWN_CAT:
            case NON_FELINE:
                growthTickStartTime = null;
                kittenLastAttentionTime = null;
                break;
        }

        infoBoxManager.removeIf(t -> t instanceof KittenGrowthTimer);
        infoBoxManager.removeIf(t -> t instanceof KittenHungryTimer);
        infoBoxManager.removeIf(t -> t instanceof KittenAttentionTimer);

        followerID = 0;
    }

    private void saveGrowthProgress() {
        switch (followerKind) {
            case KITTEN: {
                config.felineId(followerID);
                if (null != growthTickStartTime){
                    config.growthTicksAlive(growthTicksAlive);
                    config.nextHungryTick(nextHungryTick);
                    config.nextAttentionTick(nextAttentionTick);
                } else {
                    log.debug("growthTickStartTime is null, no follower...");
                }
                config.lastAttentionType(lastAttentionType);
                break;
            }
            case NORMAL_CAT: {
                config.felineId(followerID);
                if (null != growthTickStartTime) {
                    config.growthTicksAlive(growthTicksAlive);
                } else {
                    log.debug("growthTickStartTime is null, no follower...");
                }
                break;
            }
            case LAZY_CAT:
            case WILY_CAT:
            case OVERGROWN_CAT:
            case NON_FELINE:
                break;
        }
    }

    private void addKittenGrowthBox(int seconds) {
        if (0 >= seconds) {
            return;
        }
        Felines feline = Felines.find(followerID);

        if (null == feline) {
            return;
        }

        growthTimer = new KittenGrowthTimer(feline, itemManager.getImage(feline.getItemSpriteId()), this, Duration.ofSeconds(seconds));
    }

    private void addHungryTimer(int seconds) {
        if (0 >= seconds) {
            return;
        }
        kittenHungryTimer = new KittenHungryTimer(itemManager.getImage(ItemID.SEASONED_SARDINE), this, Duration.ofSeconds(seconds));
    }

    private void addAttentionTimer(int seconds) {
        if (0 >= seconds) {
            return;
        }
        kittenAttentionTimer = new KittenAttentionTimer(itemManager.getImage(1759), this, Duration.ofSeconds(seconds));
    }

    private void checkToProgressGrowthTickOnOverheadText() {
        int secondsBeforeCheckingForGrowth = 87;
        if (noGrowthSinceLoggedIn){
            secondsBeforeCheckingForGrowth = 80;  // first growth tick can be very early upon first logging in.
        }
        if (secondsInTick >= secondsBeforeCheckingForGrowth && 200 > client.getPlayers().size()) {
                /* Progress growth tick when your kitten has overhead text
                secondsInTick has to be >= 88 (should theoretically be 90, but I think it gets both truncated as well
                as only getting checked every game tick (.6s) so there's a potential 2s left off) -
                we don't want this progressing growth ticks whenever the player feeds/plays with the cat (this also
                gives overhead text from the cat).
                It almost always gives me 89s but on rare occasion 88s or 90s. On one occasion upon login, I even had
                it give me 83s.  However, currently I'm going to set it to 87 to be safe.  (and 80s upon login)

                We also will only use this when there's less than 200 players nearby.  Otherwise we'll use the method
                checkToProgressGrowthInterfaceMethod().  We don't want them both triggering though - for ex.
                if you have 200 players nearby and your kitten is rendered, I believe it will advance 2 growth ticks,
                one for each method since they will trigger simultaneously on the same tick and secondsInTick won't
                have a chance to reset.  I've now written a method to handle that anyway, but we might as well be
                proactive about it.
                 */
            advanceGrowthTick();
            // log.debug("Advancing growth tick to " + growthTicksAlive + " - overhead method");
        }
    }
    @Subscribe
    public void onOverheadTextChanged(OverheadTextChanged e) {
        /* Check for kitten's/cat's overhead text, indicating a growth tick.  The kitten/cat growth only happens as follows:
        - Every 90 seconds, if you are not in a dialogue/interface, the cat grows one growth tick (90s)
            - If you are in a dialogue/interface, it will pause and finish that growth tick as soon as you close out of it.
        - Every time the cat/kitten is picked up, it will reset that growth tick and no progress is made for that tick.
            - This also includes hopping worlds/logging out.
        - If you remain in that interface and the cat does not have a chance to grow upon logging out (for ex., you afk out
          while in a bank interface), the cat's growth tick also does not complete, and it resets upon login.
        - Every time the cat/kitten successfully progresses through a growth tick, it will always have overhead text.
            - We will use that overhead text to track growth.
        - Sometimes, when you're in a high population area (at a shooting star), not all entities will render, meaning
          your kitten might not either, and will not have overhead text.  checkToProgressGrowthInterfaceMethod()
          will take over at that point.
        - Your kitten/cat will also not have overhead text if you teleport at the same time as a growth tick occurs.
          For this, instead we will check if you have loaded into a new area recently and use the interface method again.
     */
        if(e.getActor().equals(client.getFollower())) { // if follower has overhead text
            // log.debug("Kitten says " + e.getOverheadText() + " and secondsInTick is " + secondsInTick);
            checkToProgressGrowthTickOnOverheadText();
        }
    }

    private void checkToProgressGrowthInterfaceMethod() {
        /* If your cat hasn't progressed growth when it is able to, and there's a lot of players nearby, sometimes
        this is because with all the entities loaded in, your kitten gets unloaded.  When that happens, we can't
        check for growth by its overhead text, as we can't see it.  Instead, we'll just check to make sure you're
        out of some common widgets (interfaces) that would cause the game to pause the kitten's timer until they're closed.

        Note: This isn't a reliable way to track growth normally, as there are a LOT of widgets that pause
        the kitten's growth... and a lot of widgets that do not pause the kitten's growth (minimap, full size map,
        settings interface, inventory, etc.).  If anyone reading this knows of a reliable way to tell the difference,
        and not on a case-by-case basis for hundreds of widgets (not to mention new widgets being added to the game),
        feel free to re-code this section to that.  As of now, we will likely miss some of the widgets that will pause
        the kitten's growth, but this is better than nothing.

        In its current state, this tracks just very slightly quickly that can add up over time.  This means the timer
        will likely freeze for a few seconds at the end of the current growth tick upon returning to a low-population
        area, if you spent a long time in a high population area.  This is better than the alternative - if it moves
        too quickly it will likely freeze for a full growth tick upon returning to the other method.
        (Instead of advancing after secondsInTick >=89, I tried 88.5s, but that was too quick.)
         */
        HashTable<WidgetNode> componentTable = client.getComponentTable();
        boolean stallingInterfaceOpen = false;
        for (WidgetNode widgetNode : componentTable) {
            for (Integer unsafe : unsafeIDs)
                if (widgetNode.getId() == unsafe){
                    // an interface is open that we know to pause kitten growth
                    stallingInterfaceOpen = true;
                    break;
                }
            if (stallingInterfaceOpen){
                break;
            }
        }
        if (!stallingInterfaceOpen) {
            // no interfaces that pause growth found.  kitten can grow.
            advanceGrowthTick();
            // log.debug("Advancing growth tick to " + growthTicksAlive + " - interface method");
        }
    }

    private void advanceGrowthTick(){
        growthTickStartTime = Instant.now();
        growthTicksAlive += 1;
        followerKind = FollowerKind.getFromFollowerId(followerID);
        if (FollowerKind.KITTEN == followerKind) {
            addKittenGrowthBox((TICKS_TO_ADULTHOOD - growthTicksAlive) * GROWTH_TICK_IN_SECONDS);
            addAttentionTimer((nextAttentionTick - growthTicksAlive) * 90);
            addHungryTimer((nextHungryTick - growthTicksAlive) * 90);
        }
        else if (FollowerKind.NORMAL_CAT == followerKind) {
            addKittenGrowthBox((TICKS_TO_OVERGROWN + TICKS_TO_ADULTHOOD - growthTicksAlive) * GROWTH_TICK_IN_SECONDS);
        }
        /* to track the most recent growth times.  if times are too close to each other, we will subtract a growth tick instead.
        this could happen with the loading condition triggering simultaneously with the overhead condition and advancing
        2 ticks instead of 1.
         */
        growthTimes.add(growthTickStartTime);
        noGrowthSinceLoggedIn = false;
    }
    private void checkifDuplicateGrowthTicks() {
        /* this method exists for possible overlap between methods (which does happen).  one such example is loading
        into a new area (running into it, not teleporting), and your kitten has overhead text, and the game is loading/
        loaded recently so the plugin thinks you may have teleported.  this happens on the same tick, so secondsInTick
        doesn't have a chance to reset, and the plugin thinks the kitten advanced two growth ticks instead of one (one
        for loading into a new area when a growth tick could occur, and one for overhead text).  this method corrects
        that behavior.  If two growth ticks were recorded in a span of 2s or less from each other, one growth tick is
        undone.  This only checks for a max of two duplicate growth ticks (only checks the last 3 recorded times),
        since there are only 3 methods that currently advance growth ticks, so we should be covered.
         */

        if (1 >= growthTimes.size()) {
            // if there's only one tick recorded, there can't be duplicates.
            return;
        }
        // remove older growth ticks than the last 3 - technically you should currently only need 2 but there are
        //  3 methods to advance growth ticks - high population on a timer, overhead text, loading on a timer
        while (3 < growthTimes.size()){
            growthTimes.remove(0);
        }
        int growthTicksToSubtract = 0;
        for(int i = 0; i < growthTimes.size()-1; i++){
            if (2 > Math.toIntExact(Duration.between(growthTimes.get(i), growthTimes.get(i + 1)).getSeconds())) {
                growthTicksToSubtract += 1;

            }
        }
        /* now that the existing growth tick times have been compared, let's remove the other growth times
        as to not subtract these growth ticks again the next time this is called. we'll keep the most recent growth time
        as it should still be able to be compared to the next future growth time.
         */
        while (1 < growthTimes.size()){
            growthTimes.remove(0);
        }
        if (0 < growthTicksToSubtract){
            subtractGrowthTicks(growthTicksToSubtract);
        }
    }
    private void subtractGrowthTicks(int numTicksToRemove){
        /* called when we find duplicate growth ticks recorded when it should have been just one.
        note: we are already partway through the growth tick, so we don't want to reset the growth tick start time,
        as we do when advancing to the next growth tick.  for that same reason we will also want to subtract
        secondsInTick here, compared to not doing that in advanceGrowthTick()
         */
        growthTicksAlive -= numTicksToRemove;
        System.out.println("Subtracting growth tick!! (numTicksToRemove: " + numTicksToRemove + ")");
        followerKind = FollowerKind.getFromFollowerId(followerID);
        if (FollowerKind.KITTEN == followerKind) {
            addKittenGrowthBox((TICKS_TO_ADULTHOOD - growthTicksAlive) * GROWTH_TICK_IN_SECONDS - secondsInTick);
            addAttentionTimer((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
            addHungryTimer((nextHungryTick - growthTicksAlive) * 90 - secondsInTick);
        }
        else if (FollowerKind.NORMAL_CAT == followerKind) {
            addKittenGrowthBox((TICKS_TO_OVERGROWN + TICKS_TO_ADULTHOOD - growthTicksAlive) *
                    GROWTH_TICK_IN_SECONDS - secondsInTick);
        }
    }

        // This is where the player interaction checks occur
    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (ChatMessageType.GAMEMESSAGE != event.getType()) {
            return;
        }
        String message = Text.removeTags(event.getMessage());
        switch (message) {
            case CHAT_STROKE_CAT: {
                timeInTick = Duration.between(growthTickStartTime, Instant.now());
                int secondsInTick = Math.toIntExact(timeInTick.getSeconds());

                if (null != kittenLastAttentionTime) { // if kitten has had attention within the time since spawn
                    long timeSinceLastAttentionSeconds = Duration.between(kittenLastAttentionTime, Instant.now()).toMillis() / 1000 + timeNeglected;

                    // max time depending on whether previous stroke already was a single stroke or a multistroke
                    int maxTimePastForMultiStrokeSeconds = -1;
                    if (null != lastAttentionType) {
                        switch (lastAttentionType) {
                            case SINGLE_STROKE:
                            case MULTIPLE_STROKES:
                                maxTimePastForMultiStrokeSeconds = lastAttentionType.getAttentionTime();
                                break;
                        }
                    }

                    // check if is valid multistroke
                    if (timeSinceLastAttentionSeconds < maxTimePastForMultiStrokeSeconds) {
                        if (config.kittenAttentionOverlay()) {
                            nextAttentionTick = growthTicksAlive + TICKS_TO_ATTENTION_RUN_AWAY_MULTIPLE_STROKES;
                            addAttentionTimer((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
                        }
                        lastAttentionType = KittenAttentionType.MULTIPLE_STROKES;
                    } else { // set timer to single stroke
                        if (config.kittenAttentionOverlay()) {
                            nextAttentionTick = growthTicksAlive + TICKS_TO_ATTENTION_RUN_AWAY_SINGLE_STROKE;
                            addAttentionTimer((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
                        }
                        lastAttentionType = KittenAttentionType.SINGLE_STROKE;
                    }
                } else { // set timer to single stroke
                    if (config.kittenAttentionOverlay()) {
                        nextAttentionTick = growthTicksAlive + TICKS_TO_ATTENTION_RUN_AWAY_SINGLE_STROKE;
                        addAttentionTimer((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
                    }
                    lastAttentionType = KittenAttentionType.SINGLE_STROKE;
                }
                kittenLastAttentionTime = Instant.now();
                timeNeglected = 0;
                break;
            }
            case CHAT_THE_KITTEN_GOBBLES_UP_THE_FISH:
            case CHAT_THE_KITTEN_LAPS_UP_THE_MILK: {
                if (config.kittenHungryOverlay()) {
                    nextHungryTick = growthTicksAlive + TICKS_TO_HUNGER_RUN_AWAY;
                    timeInTick = Duration.between(growthTickStartTime, Instant.now());
                    secondsInTick = Math.toIntExact(timeInTick.getSeconds());
                    addHungryTimer(TICKS_TO_HUNGER_RUN_AWAY * 90 - secondsInTick);
                }
                break;
            }
            case CHAT_YOUR_KITTEN_IS_HUNGRY: // 6 minute warning
            {
                if (config.kittenNotifications()) {
                    notifier.notify(message);
                }
                if (config.kittenHungryOverlay()) {
                    addHungryTimer(HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS - secondsInTick);
                }
                nextHungryTick = growthTicksAlive + TICKS_HUNGER_FIRST_WARNING;
                break;
            }
            case CHAT_YOUR_KITTEN_IS_VERY_HUNGRY: { // 3 minute warning
                if (config.kittenNotifications()) {
                    notifier.notify(message);
                }
                if (config.kittenHungryOverlay()) {
                    addHungryTimer(HUNGRY_FINAL_WARNING_TIME_LEFT_IN_SECONDS - secondsInTick);
                }
                nextHungryTick = growthTicksAlive + TICKS_HUNGER_FINAL_WARNING;
                break;
            }
            case CHAT_YOUR_KITTEN_WANTS_ATTENTION: { // 9 minute warning
                if (config.kittenNotifications()) {
                    notifier.notify(message);
                }
                nextAttentionTick = growthTicksAlive + TICKS_ATTENTION_FIRST_WARNING;
                if (config.kittenAttentionOverlay()) {
                    addAttentionTimer((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
                }
                break;
            }
            case CHAT_YOUR_KITTEN_REALLY_WANTS_ATTENTION: { // 4.5 minute warning
                if (config.kittenNotifications()) {
                    notifier.notify(message);
                }
                nextAttentionTick = growthTicksAlive + TICKS_ATTENTION_FINAL_WARNING;
                if (config.kittenAttentionOverlay()) {
                    addAttentionTimer((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
                }
                break;
            }
            case CHAT_YOUR_KITTEN_GOT_LONELY_AND_RAN_OFF:
            case CHAT_THE_CAT_HAS_RUN_AWAY: //shoo away option
            {
                if (config.kittenNotifications()) {
                    notifier.notify(message);
                }
                // new stuff
                growthTickStartTime = null;
                nextHungryTick = TICKS_TO_HUNGER_RUN_AWAY;
                nextAttentionTick = TICKS_TO_ATTENTION_RUN_AWAY_MULTIPLE_STROKES;
                growthTicksAlive = 0;

                kittenLastAttentionTime = null;
                previousFollowerId = 0;
                config.felineId(0); // in case the new kitten has the same NpcID. We need to track growth progress from the beginning.

                infoBoxManager.removeIf(t -> t instanceof KittenGrowthTimer);
                infoBoxManager.removeIf(t -> t instanceof KittenHungryTimer);
                infoBoxManager.removeIf(t -> t instanceof KittenAttentionTimer);
                followerID = 0;
                break;
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {

        // need to update secondsInTick regularly to keep timers tracking properly... but make sure it's not null (kitten is not out)
        if (null != growthTickStartTime) {
            timeInTick = Duration.between(growthTickStartTime, Instant.now());
            /* i ended up not using the millisecond version anyway, since checking for growth at 89.5s caused the
               interface method to run slowly which was more of an issue than it running quickly. */
            // double secondsInTickWithMs = (double) timeInTick.toMillis() / 1000;
            secondsInTick = Math.toIntExact(timeInTick.getSeconds());

            // check our other methods of growth progress:
            // a) high population world where your kitten may or not be rendered.  200 players is relatively arbitrary btw
            // b) you teleported recently and your kitten grew but didn't have overhead text as you were teleporting
            Duration timeSinceLastLoad = Duration.between(lastLoadingTime, Instant.now());
            if (89 <= secondsInTick && 200 <= client.getPlayers().size()){
                checkToProgressGrowthInterfaceMethod();
            } else if (89 <= secondsInTick && 2 >= Math.toIntExact(timeSinceLastLoad.getSeconds())){
                // check if the kitten is ready to grow on teleports.  apparently the kitten will not have overhead text
                //  if you teleport on the same tick as when it grows.  by the way you need to check if there was a
                //  recent teleport, client.getGameState() = GameState.LOADING doesn't work all the time.
                checkToProgressGrowthInterfaceMethod();
            }
        }

        checkifDuplicateGrowthTicks();
        // Send notification on 1 minute before kitten runs away (attention)
        long timeBeforeNeedingAttention = getTimeBeforeNeedingAttention();
        if (!attentionNotificationSend && 0 != timeBeforeNeedingAttention && ATTENTION_TIME_ONE_MINUTE_WARNING_MS > timeBeforeNeedingAttention) {
            notifier.notify(NOTIFICATION_KITTEN_WILL_RUN_AWAY_IN_1_MINUTE);
            attentionNotificationSend = true;
        }

        // Send notification on 1 minute before kitten runs away (hunger)
        long timeBeforeHungry = getTimeBeforeHungry();
        if (!hungryNotificationSend && 0 != timeBeforeHungry && HUNGRY_TIME_ONE_MINUTE_WARNING_MS > timeBeforeHungry) {
            notifier.notify(NOTIFICATION_KITTEN_WILL_RUN_AWAY_IN_1_MINUTE);
            hungryNotificationSend = true;
        }

        Widget playerDialog = Rs2Widget.findWidget(DIALOG_CAT_BALL_OF_WOOL);

        if (null != playerDialog) {
            log.info("Kitten is playing with the ball of wool");

            if (config.kittenAttentionOverlay()) {
                nextAttentionTick = growthTicksAlive + TICKS_TO_ATTENTION_RUN_AWAY_BALL_OF_WOOL;
                addAttentionTimer(ATTENTION_TIME_BEFORE_KITTEN_RUNS_AWAY_BALL_OF_WOOL_IN_SECONDS - secondsInTick);
            }
            lastAttentionType = KittenAttentionType.BALL_OF_WOOL;
            kittenLastAttentionTime = Instant.now();
            timeNeglected = 0;


        }
        Widget notificationDialog = client.getWidget(WIDGET_ID_DIALOG_NOTIFICATION_GROUP_ID, WIDGET_ID_DIALOG_NOTIFICATION_TEXT);
        if (null != notificationDialog) {
            String notificationText = Text.removeTags(notificationDialog.getText()); // remove color and linebreaks
            if (notificationText.equals(DIALOG_GERTRUDE_GIVES_YOU_ANOTHER_KITTEN)) { // new kitten
                config.lastAttentionType(KittenAttentionType.NEW_KITTEN);
                kittenLastAttentionTime = Instant.now();

                growthTicksAlive = 0;
                growthTickStartTime = Instant.now();
                nextHungryTick = TICKS_TO_HUNGER_RUN_AWAY;
                nextAttentionTick = TICKS_TO_ATTENTION_RUN_AWAY_MULTIPLE_STROKES;
                config.growthTicksAlive(growthTicksAlive);
                config.nextHungryTick(nextHungryTick);
                config.nextAttentionTick(nextAttentionTick);
                addKittenGrowthBox(TIME_TO_ADULTHOOD_IN_SECONDS);
                addHungryTimer(HUNGRY_TIME_BEFORE_KITTEN_RUNS_AWAY_IN_SECONDS);
                addAttentionTimer(ATTENTION_TIME_BEFORE_KITTEN_RUNS_AWAY_MULTIPLE_STROKES_IN_SECONDS);
            } else if (notificationText.equals(DIALOG_CAT_GROWN)) {
                followerKind = FollowerKind.NORMAL_CAT;
                // commenting this out - this is also called with onVarbitChanged, which will be called when cat grows up
                // checkForNewFollower();
                /* growthTicksAlive should be 120 at this point anyway, but this is a band-aid covering up a different issue...
                previousFollowerID is set to followerID in checkForNewFollower() immediately before calling
                newFollower(), where it checks if previousFollowerID == followerID... so it's always true.  so the plugin
                ALWAYS thinks you have the same kitten/cat as last time even if that's not true.  the kitten has other
                resets when running away/being turned in so most people probably haven't noticed this bug. */
                growthTicksAlive = TICKS_TO_ADULTHOOD;
                config.growthTicksAlive(growthTicksAlive);
                infoBoxManager.removeIf(t -> t instanceof KittenAttentionTimer);
                infoBoxManager.removeIf(t -> t instanceof KittenHungryTimer);
            } else if (notificationText.equals(DIALOG_CAT_OVERGROWN)) {
                followerKind = FollowerKind.OVERGROWN_CAT;
                // commenting this out - this is also called with onVarbitChanged, which will be called when cat grows up
                // checkForNewFollower();
            } else if (notificationText.startsWith(DIALOG_AFTER_TAKING_A_GOOD_LOOK)) {
                String ageStr = notificationText.substring(DIALOG_AFTER_TAKING_A_GOOD_LOOK.length());
                int end = ageStr.indexOf("And approximate time until");
                ageStr = ageStr.substring(0, end);
                int hoursIndex = ageStr.indexOf("hours");
                if (0 > hoursIndex) {
                    hoursIndex = ageStr.indexOf("hour");
                }

                String hoursStr = "";
                if (0 < hoursIndex) {
                    hoursStr = ageStr.substring(0, hoursIndex);
                    hoursStr = hoursStr.trim();
                }
                int minutesIndex = ageStr.indexOf("minutes");
                if (0 > minutesIndex) {
                    minutesIndex = ageStr.indexOf("minute");
                }

                String minutesStr = "";
                if (0 < minutesIndex) {
                    if (0 < hoursIndex) {
                        minutesStr = ageStr.substring(hoursIndex + "hours".length(), minutesIndex);
                        minutesStr = minutesStr.trim();
                    } else {
                        minutesStr = ageStr.substring(0, minutesIndex);
                        minutesStr = minutesStr.trim();
                    }
                }

                int hours = 0;
                int minutes = 0;
                if (StringUtils.isNotEmpty(hoursStr)) {
                    try {
                        hours = Integer.parseInt(hoursStr);
                    } catch (NumberFormatException ex) {
                        log.debug(ex.getMessage());
                    }
                }

                if (StringUtils.isNotEmpty(minutesStr)) {
                    try {
                        minutes = Integer.parseInt(minutesStr);
                    } catch (NumberFormatException ex) {
                        log.debug(ex.getMessage());
                    }
                }

                int ageMinutes = (hours * 60) + minutes;
                int ageSeconds;

                if (0 != ageMinutes / 1.5) {
                    // unit given is not an exact number, they truncated it.  add 30s to timer
                    ageSeconds = ageMinutes * 60 + 30;
                } else {
                    // unit given is an exact number
                    ageSeconds = ageMinutes * 60;
                }

                int ticksAliveInDialog = ageSeconds / 90;
                if (ticksAliveInDialog == growthTicksAlive) {
                    // ticks alive is accurate.  don't adjust it, it's tracking as it should.
                    return;
                } else {

                    double dialogMinutes = ticksAliveInDialog * 1.5;
                    double inaccurateMinutes = growthTicksAlive * 1.5;
                    log.debug("Kitten's growth ticks alive is NOT accurate: adjusting from " + growthTicksAlive +
                            " to " + ticksAliveInDialog + " ticks alive. ("  + inaccurateMinutes + " to " +
                            dialogMinutes + " min.)");
                    growthTicksAlive = ticksAliveInDialog;

                    /* update attn/growth to minimum values if we know they are inaccurate from new kitten growth time.
                    for example, in the case of turning in a cat on mobile, and then you got a new kitten that
                    is the same color (same follower ID), nextHungryTick and nextAttentionTick will not have been reset.
                    if attention required tick OR hunger required tick is too far away to be possible, reset
                    BOTH to the given values upon getting a new kitten so the user doesn't think they're good to go
                    for like 2.5h or whatever.  if one is inaccurate, the other will be too.  these will update
                    accordingly once the user feeds/plays with kitten, or in-game notifications warn of hunger/attention.
                    Need to do this before updating timer values, so it displays properly during first shown growth tick
                     */
                    if (TICKS_TO_ATTENTION_RUN_AWAY_BALL_OF_WOOL < nextAttentionTick - growthTicksAlive ||
                            TICKS_TO_HUNGER_RUN_AWAY < nextHungryTick - growthTicksAlive) {

                        nextAttentionTick = TICKS_TO_ATTENTION_RUN_AWAY_MULTIPLE_STROKES;
                        nextHungryTick = TICKS_TO_HUNGER_RUN_AWAY;
                    }

                    /* note: this will not give you the age in a round increment.  it should give you the exact growth
                    progress that your kitten has.  even though the overall age was incorrect, the progress within the
                    tick is still being accurately tracked, and we will use that here.
                     */
                    if (90 <= secondsInTick) {
                        // don't overshoot growth progress if progress paused because you're in the dialog menu
                        addKittenGrowthBox((TICKS_TO_ADULTHOOD - growthTicksAlive - 1) * 90);
                        addAttentionTimer((nextAttentionTick - growthTicksAlive - 1) * 90);
                        addHungryTimer((nextHungryTick - growthTicksAlive - 1) * 90);
                    } else {
                        addKittenGrowthBox((TICKS_TO_ADULTHOOD - growthTicksAlive) * 90 - secondsInTick);
                        addAttentionTimer((nextAttentionTick - growthTicksAlive) * 90 - secondsInTick);
                        addHungryTimer((nextHungryTick - growthTicksAlive) * 90 - secondsInTick);
                    }
                }
            }
        }
        Widget dialog = client.getWidget(WidgetID.DIALOG_SPRITE_GROUP_ID, 2);
        if (null != dialog) {
            String notificationText = Text.removeTags(dialog.getText());
            if (notificationText.startsWith(DIALOG_HAND_OVER_CAT_CIVILIAN)) {
                growthTickStartTime = null;
                kittenLastAttentionTime = null;
                previousFollowerId = 0;
                config.felineId(0); // in case the new kitten has the same NpcID. We need to track growth progress from the beginning.

                infoBoxManager.removeIf(t -> t instanceof KittenGrowthTimer);
                infoBoxManager.removeIf(t -> t instanceof KittenHungryTimer);
                infoBoxManager.removeIf(t -> t instanceof KittenAttentionTimer);
                followerID = 0;
            }
        }
    }
    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("kittenConfig")) {
            return;
        }

        if (event.getKey().equals("kittenInfoBox")) {
            if (event.getNewValue().equals("true")) {
                if (isKitten()) {
                    growthTicksAlive = config.growthTicksAlive();
                    timeInTick = Duration.between(growthTickStartTime, Instant.now());
                    int secondsInTick = Math.toIntExact(timeInTick.getSeconds());
                    addKittenGrowthBox((TICKS_TO_ADULTHOOD - growthTicksAlive) * GROWTH_TICK_IN_SECONDS - secondsInTick);
                }
            }
            if (event.getNewValue().equals("false")) {
                if (isKitten()) {
                    infoBoxManager.removeIf(t -> t instanceof KittenGrowthTimer);
                }
            }
        }

        if (event.getKey().equals("catInfoBox")) {
            if (event.getNewValue().equals("true")) {
                if (isCat()) {
                    growthTicksAlive = config.growthTicksAlive();
                    timeInTick = Duration.between(growthTickStartTime, Instant.now());
                    int secondsInTick = Math.toIntExact(timeInTick.getSeconds());
                    addKittenGrowthBox(TIME_TILL_OVERGROWN_IN_SECONDS + TIME_TO_ADULTHOOD_IN_SECONDS -
                            growthTicksAlive * GROWTH_TICK_IN_SECONDS - secondsInTick);

                }
            }
            if (event.getNewValue().equals("false")) {
                if (isCat()) {
                    infoBoxManager.removeIf(t -> t instanceof KittenGrowthTimer);
                }
            }
        }

        if (event.getKey().equals("kittenAttentionBox")) {
            if (event.getNewValue().equals("true")) {
                timeInTick = Duration.between(growthTickStartTime, Instant.now());
                int secondsInTick = Math.toIntExact(timeInTick.getSeconds());
                int secondsTillAttention = (nextAttentionTick - growthTicksAlive) * GROWTH_TICK_IN_SECONDS - secondsInTick;
                addAttentionTimer(secondsTillAttention);
            }
            if (event.getNewValue().equals("false")) {
                infoBoxManager.removeIf(t -> t instanceof KittenAttentionTimer);
            }
        }

        if (event.getKey().equals("kittenHungryBox")) {
            if (event.getNewValue().equals("true")) {
                timeInTick = Duration.between(growthTickStartTime, Instant.now());
                int secondsInTick = Math.toIntExact(timeInTick.getSeconds());
                int secondsTillHungry = (nextHungryTick - growthTicksAlive) * GROWTH_TICK_IN_SECONDS - secondsInTick;
                addAttentionTimer(secondsTillHungry);
            }
            if (event.getNewValue().equals("false")) {
                infoBoxManager.removeIf(t -> t instanceof KittenHungryTimer);
            }
        }
    }


    /*
    // 2023-01-24
    // Commenting this out for now until a better assessment can be made if it is needed or not.
    // Personal testing has shown that time does not need to be added back after Widgets (main ones tested was
    //   dialog boxes and using a bank interface). Not sure if cutscenes count as a widget and if they affect it.
    @Subscribe
    private void onWidgetLoaded(WidgetLoaded ev) {
        openedWidgets.put(ev.getGroupId(), Instant.now());
    }

    @Subscribe
    private void onWidgetClosed(WidgetClosed ev) {
        Instant openedWidgetTime = openedWidgets.get(ev.getGroupId());
        if (openedWidgetTime != null) {
            if (ev.getModalMode() != WidgetModalMode.NON_MODAL) {
                addDurationToTimers(Duration.between(openedWidgetTime, Instant.now()));
            }
            openedWidgets.remove(ev.getGroupId());
        }
    }
    */

    private void addDurationToTimers(Duration duration) {
        if (null == duration) {
            return;
        }

        Duration timerDuration;
        if (null != growthTimer) {
            timerDuration = growthTimer.getDuration();
            if (null != growthTimer && null != timerDuration) {
                growthTimer.setDuration(timerDuration.plus(duration));
            }
        }

        if (null != kittenAttentionTimer) {
            timerDuration = kittenAttentionTimer.getDuration();
            if (null != kittenAttentionTimer && null != timerDuration) {
                kittenAttentionTimer.setDuration(timerDuration.plus(duration));
            }
        }

        if (null != kittenHungryTimer) {
            timerDuration = kittenHungryTimer.getDuration();
            if (null != kittenHungryTimer && null != timerDuration) {
                kittenHungryTimer.setDuration(timerDuration.plus(duration));
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        GameState state = event.getGameState();
        switch (state) {

            case LOGGING_IN:
            case HOPPING:
                noGrowthSinceLoggedIn = true;
                // this doesn't need to break here, and really I want both logging in & hopping to set this to true.
            case CONNECTION_LOST: // CHECK: this may be a condition causing the timer not to stop when the window is closed
                ready = true;
                break;
            case LOGGED_IN:
                if (ready) {
                    ready = false; // Currently not used for anything?
                }
                break;
            case LOGIN_SCREEN:
                byeFollower();
                break;
            case LOADING:
                lastLoadingTime = Instant.now();

        }
    }

    @Inject
    public boolean playerHasFollower() {
        return 0 < (client.getVarpValue(VAR_PLAYER_FOLLOWER));
    }

    public boolean isKitten() {
        return FollowerKind.KITTEN == followerKind;
    }

    public Long getTimeUntilFullyGrown() {
        if (null == growthTimer) {
            return 0L;
        }
        if (isKitten()) {
            long ret;
            timeInTick = Duration.between(growthTickStartTime, Instant.now());
            int secondsInTick = Math.toIntExact(timeInTick.getSeconds());
            if (90 < secondsInTick) {
                // paused at the end of the tick, but it hasn't progressed yet (player is in menus)
                ret = (long) (TICKS_TO_ADULTHOOD - growthTicksAlive - 1) * 90 * 1000;
                if (0 > ret){
                    return 0L;
                } else {
                    return ret;
                }
            } else {
                // `cull` returns true if timer is less than or equal to zero
                // This will keep the timer from going negative
                if (growthTimer.cull()) {
                    return 0L;
                } else {
                    return Math.abs(growthTimer.getEndTime().until(Instant.now(), ChronoUnit.MILLIS));
                }
            }
        }
        return 0L;
    }

    public boolean isCat() {
        return FollowerKind.NORMAL_CAT == followerKind;
    }

    public boolean isOverGrown() {
        return FollowerKind.OVERGROWN_CAT == followerKind;
    }

    public Long getTimeUntilOvergrown() {
        if (null == growthTimer) {
            return 0L;
        }

        long ret;
        timeInTick = Duration.between(growthTickStartTime, Instant.now());
        int secondsInTick = Math.toIntExact(timeInTick.getSeconds());
        if (90 <= secondsInTick) {
            // paused at the end of the tick, but it hasn't progressed yet (player is in menus)
            ret = (long) (TICKS_TO_ADULTHOOD + TICKS_TO_OVERGROWN - growthTicksAlive - 1) * 90 * 1000;
            if (0 > ret){
                return 0L;
            } else {
                return ret;
            }
        } else {
            ret = Math.abs(growthTimer.getEndTime().until(Instant.now(), ChronoUnit.MILLIS));
        }

        if (isCat()) {
            return ret;
        }
        return ret + TIME_TILL_OVERGROWN_IN_SECONDS;
    }

    public Long getTimeBeforeHungry() {
        if (null == kittenHungryTimer) {
            return 0L;
        }
        if (isKitten()) {
            if (90 <= secondsInTick) {
                // paused at the end of the tick, but it hasn't progressed yet (player is in menus)
                long ret = (long) (nextHungryTick - growthTicksAlive - 1) * 90 * 1000;
                if (0 > ret){
                    return 0L;
                } else {
                    return ret;
                }
            } else {
                // `cull` returns true if timer is less than or equal to zero
                // This will keep the timer from going negative
                if (kittenHungryTimer.cull()) {
                    return 0L;
                } else {
                    return Math.abs(kittenHungryTimer.getEndTime().until(Instant.now(), ChronoUnit.MILLIS));
                }
            }
        }
        return 0L;
    }

    public Long getTimeBeforeNeedingAttention() {
        if (null == kittenAttentionTimer) {
            return 0L;
        }
        if (isKitten()) {
            if (90 <= secondsInTick) {
                // paused at the end of the tick, but it hasn't progressed yet (player is in menus)
                long ret = (long) (nextAttentionTick - growthTicksAlive - 1) * 90 * 1000;
                if (0 > ret){
                    return 0L;
                } else {
                    return ret;
                }
            } else {
                // `cull` returns true if timer is less than or equal to zero
                // This will keep the timer from going negative
                if (kittenAttentionTimer.cull()) {
                    return  0L;
                } else {
                    return Math.abs(kittenAttentionTimer.getEndTime().until(Instant.now(), ChronoUnit.MILLIS));
                }
            }
        }
        return 0L;
    }
}
