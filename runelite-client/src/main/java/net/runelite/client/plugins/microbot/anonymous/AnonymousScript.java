package net.runelite.client.plugins.microbot.anonymous;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class AnonymousScript extends Script {

    /* *
     * Stripper method to remove all equipment from the player's character.
     * Back to tutorial island baby :)
     */
    public void stripPlayer(Client client) {
        Player player = client.getLocalPlayer();
        if (player == null) return;

        PlayerComposition comp = player.getPlayerComposition();
        if (comp == null) return;

        int[] equipmentIds = comp.getEquipmentIds();
        Arrays.fill(equipmentIds, 0);

        equipmentIds[KitType.TORSO.getIndex()] = 18 + PlayerComposition.KIT_OFFSET;
        equipmentIds[KitType.LEGS.getIndex()]  = 36 + PlayerComposition.KIT_OFFSET;
        equipmentIds[KitType.HAIR.getIndex()]  = PlayerComposition.KIT_OFFSET;
        equipmentIds[KitType.BOOTS.getIndex()]  = 42 + PlayerComposition.KIT_OFFSET;
        equipmentIds[KitType.HANDS.getIndex()] = 33 + PlayerComposition.KIT_OFFSET;
        equipmentIds[KitType.ARMS.getIndex()]  = 26 + PlayerComposition.KIT_OFFSET;
        equipmentIds[KitType.JAW.getIndex()]  = 10 + PlayerComposition.KIT_OFFSET;

        int[] colors = comp.getColors();
        Arrays.fill(colors, 0);

        comp.setHash();
    }

    /**
     * Interface for anonymizers that can modify the text of a widget.
     */
    public interface Anonymizer {
        void anonymize(Widget widget);
    }

    /**
     * Anonymizer that replaces the widget's entire text with a fixed, static string.
     */
    public static class StaticTextAnonymizer implements Anonymizer {
        private final String replacementText;

        public StaticTextAnonymizer(String replacementText) {
            this.replacementText = replacementText;
        }

        @Override
        public void anonymize(Widget widget) {
            if (widget != null && !widget.getText().equals(replacementText)) {
                widget.setText(replacementText);
            }
        }
    }

    /**
     * Anonymizer that gets the deep nested children widgets of the skills interface
     * and replaces their text with a fixed, static string.
     */
    public static class SkillsAnonymizer implements Anonymizer {
        private final String replacementText;

        public SkillsAnonymizer(String replacementText) {
            this.replacementText = replacementText;
        }

        @Override
        public void anonymize(Widget widget) {
            if (widget == null || widget.isHidden()) return;
            Widget[] skillWidgets = widget.getStaticChildren();
            if (skillWidgets == null || skillWidgets.length < 25) return;
            for (int i = 0; i < 23; i++) {
                Widget skillWidget = skillWidgets[i];
                if (skillWidget == null) continue;
                Widget[] skillWidgetComponents = skillWidget.getDynamicChildren();
                if (skillWidgetComponents == null || skillWidgetComponents.length < 4) continue;
                skillWidgetComponents[3].setText(replacementText);
                skillWidgetComponents[4].setText(replacementText);
            }
            if (skillWidgets[23].getStaticChildren() == null || skillWidgets[23].getStaticChildren()[2] == null) return;
            skillWidgets[23].getStaticChildren()[2].setText("Total level:<br>2277");
        }
    }

    /**
     * Anonymizer that gets the deep nested children widgets of the skills interface
     * and replaces their text with a fixed, static string.
     */
    public static class SkillsTooltipAnonymizer implements Anonymizer {

        public SkillsTooltipAnonymizer() {
        }

        @Override
        public void anonymize(Widget widget) {
            if (widget == null || widget.isHidden()) return;
            Widget[] tooltipChildren = widget.getChildren();
            if (tooltipChildren == null) return;
            if (tooltipChildren[2] == null || tooltipChildren[2].getText() == null || tooltipChildren[2].getText().isEmpty()) return;
            String newText = tooltipChildren[2].getText().split(":")[0]+":";
            tooltipChildren[0].revalidate();
            tooltipChildren[1].revalidate();
            tooltipChildren[2].setText(newText);
            tooltipChildren[2].revalidate();
            tooltipChildren[3].setText("200,000,000");
            tooltipChildren[3].revalidate();
            widget.setOriginalWidth((newText.length() * 5) + 100);
            widget.setOriginalHeight(18);
            widget.revalidate();
        }
    }

    /**
     * Anonymizer that gets the deep nested children widgets of the inventory interface
     * and replaces the quantity with a fixed, static amount.
     */
    public static class ItemQuantityAnonymizer implements Anonymizer {
        public ItemQuantityAnonymizer() {
        }

        @Override
        public void anonymize(Widget widget) {
            if (widget == null || widget.isHidden()) return;
            Widget[] inventoryWidgets = widget.getDynamicChildren();
            if (inventoryWidgets == null) return;
            for (Widget inventoryWidget : inventoryWidgets) {
                if (inventoryWidget == null) continue;
                if (inventoryWidget.getItemQuantityMode() != ItemQuantityMode.ALWAYS && inventoryWidget.getItemQuantityMode() != ItemQuantityMode.STACKABLE) {
                    continue;
                }
                inventoryWidget.setItemQuantity(2147483647);
            }
        }
    }


    /**
     * A highly reusable anonymizer driven by regular expressions.
     * This avoids the need to create a new class for every specific find-and-replace rule.
     */
    public static class RegexAnonymizer implements Anonymizer {
        private final Pattern pattern;
        private final Function<Matcher, String> replacer;

        /**
         * @param regex    The regular expression to find text to replace.
         * @param replacer A function that takes a regex Matcher and returns the replacement string.
         */
        public RegexAnonymizer(String regex, Function<Matcher, String> replacer) {
            this.pattern = Pattern.compile(regex);
            this.replacer = replacer;
        }

        @Override
        public void anonymize(Widget widget) {
            if (widget == null || widget.getText() == null || widget.getText().isEmpty()) {
                return;
            }

            String originalText = widget.getText();
            Matcher matcher = pattern.matcher(originalText);

            StringBuilder buffer = new StringBuilder();
            while (matcher.find()) {
                String replacement = replacer.apply(matcher);
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(buffer);

            String newText = buffer.toString();

            if (!originalText.equals(newText)) {
                widget.setText(newText);
            }
        }
    }

    /**
     * Utility class for creating common replacer functions used in RegexAnonymizer.
     * These functions can be used to maintain consistent naming or formatting while anonymizing text.
     */
    public static class Replacers {
        private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");

        /**
         * Creates a replacer function that maintains a consistent, anonymous mapping for matched text,
         * while preserving any formatting tags.
         *
         * @param prefix The replacement text to use for all names.
         * @param group  The capture group from the regex that contains the name (and tags) to be replaced.
         * @return A function that can be passed to the RegexAnonymizer.
         */
        public static Function<Matcher, String> consistentNamer(String prefix, int group) {
            return (matcher) -> {
                String preColonPart = matcher.group(group);

                Matcher tagMatcher = TAG_PATTERN.matcher(preColonPart);
                StringBuilder tags = new StringBuilder();
                while (tagMatcher.find()) {
                    tags.append(tagMatcher.group(0));
                }

                String newPreColonPart = tags + prefix;

                return newPreColonPart + matcher.group(2);
            };
        }
    }

    /**
     * Enum representing the various elements in the client that can be anonymized.
     */
    private enum AnonymizableElement {
        /**
         * Those are basically the elements in the interface we're going to anonymize.
         * Each element has a widget ID and an associated anonymizer.
         * The widget ID corresponds to the specific interface element in the RuneLite client.
         * The anonymizer defines how the text of that widget should be modified.
         */
        CHATBOX_INPUT(InterfaceID.Chatbox.INPUT, new RegexAnonymizer(
                "^([^:]+)(:.*)$",
                Replacers.consistentNamer("Anonymous", 1)
        )),
        HP_GLOBE(InterfaceID.Orbs.HEALTH_TEXT, new StaticTextAnonymizer("99")),
        PRAYER_GLOBE(InterfaceID.Orbs.PRAYER_TEXT, new StaticTextAnonymizer("99")),
        SKILL_LEVELS(InterfaceID.Stats.UNIVERSE, new SkillsAnonymizer("99")),
        SKILL_LEVELS_TOOLTIP(InterfaceID.Stats.TOOLTIP, new SkillsTooltipAnonymizer()),
        COMBAT_LEVEL(InterfaceID.CombatInterface.LEVEL, new StaticTextAnonymizer("Combat Lvl: 126")),
        INVENTORY_ITEM_QUANTITY(InterfaceID.Inventory.ITEMS, new ItemQuantityAnonymizer()),
        BANK_INVENTORY_QUANTITY(InterfaceID.Bankside.ITEMS, new ItemQuantityAnonymizer()),
        BANK_ITEM_QUANTITY(InterfaceID.Bankmain.ITEMS, new ItemQuantityAnonymizer()),
        XP_DROPS_COUNTER(InterfaceID.XpDrops.COUNTER, new StaticTextAnonymizer("2,147,483,647"));

        @Getter
        private final int widgetId;
        private final Anonymizer anonymizer;

        AnonymizableElement(int widgetId, Anonymizer anonymizer) {
            this.widgetId = widgetId;
            this.anonymizer = anonymizer;
        }

        /**
         * Applies the anonymization to the widget associated with this element.
         * It retrieves the widget by its ID and applies the anonymizer to it.
         *
         * @param client The current client instance.
         */
        public void apply(Client client) {
            Widget widget = client.getWidget(widgetId);
            if (widget != null) {
                anonymizer.anonymize(widget);
            }
        }
    }

    public void anonymize(Client client, @NotNull AnonymousConfig config) {
        if (config.maskTitleName()) Microbot.getConfigManager().setConfiguration("runelite", "usernameInTitle", false);
        if (config.maskCharacterVisual()) stripPlayer(client);
    }

    /**
     * This method is called before rendering the client.
     * It applies the anonymization based on the provided configuration.
     * Without doing before render, widget value may appear flicking.
     * As it's a plugin intended to run only when needed, the possible performance impact shouldn't be a concern.
     *
     * @param client The current client instance.
     * @param config The configuration for anonymization.
     */
    public void onBeforeRender(Client client, @NotNull AnonymousConfig config) {
        if (!Microbot.isLoggedIn() || !super.run() || !isRunning() || client == null) return;

        if (config.maskCharacterName()) AnonymizableElement.CHATBOX_INPUT.apply(client);
        if (config.maskSkillLevels()) AnonymizableElement.SKILL_LEVELS.apply(client);
        if (config.maskSkillLevelTooltips()) AnonymizableElement.SKILL_LEVELS_TOOLTIP.apply(client);
        if (config.maskHPGlobe()) AnonymizableElement.HP_GLOBE.apply(client);
        if (config.maskPrayerGlobe()) AnonymizableElement.PRAYER_GLOBE.apply(client);
        if (config.maskCombatLevel()) AnonymizableElement.COMBAT_LEVEL.apply(client);
        if (config.maskInventoryItemQuantity()) {
            AnonymizableElement.INVENTORY_ITEM_QUANTITY.apply(client);
            AnonymizableElement.BANK_INVENTORY_QUANTITY.apply(client);
        }
        if (config.maskBankItemQuantity()) AnonymizableElement.BANK_ITEM_QUANTITY.apply(client);
        if (config.maskXpDropsCounter()) AnonymizableElement.XP_DROPS_COUNTER.apply(client);
    }

    public boolean run(AnonymousConfig config, Client client) {

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || !isRunning() || client == null || config == null) return;

                anonymize(client, config);

            } catch (Exception ex) {
                log.error("Error during anonymization task", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
