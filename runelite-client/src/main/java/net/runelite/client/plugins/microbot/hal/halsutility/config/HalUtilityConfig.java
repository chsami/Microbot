package net.runelite.client.plugins.microbot.hal.halsutility.config;

import lombok.Getter;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalModuleCategory;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalSubPlugin;
import net.runelite.client.util.Text;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@ConfigGroup("halsutility")
public interface HalUtilityConfig extends Config {
    String HAL_UTILITY_GROUP = "halsutility";
    String HAL_UTILITY_BACKGROUND_GROUP = "halsutilityvars";
    
    @ConfigItem(
            keyName = "filterModulesBy",
            name = "Filter",
            description = "Choose which category of modules to display in the side panel"
    )
    default ModuleFilter filterModulesBy() {
        return ModuleFilter.ALL;
    }

    @ConfigItem(
            keyName = "pinnedModules",
            name = "Pinned Modules",
            description = "Comma-separated list of pinned module names",
            hidden = true
    )
    default String pinnedModules() {
        return "";
    }

    enum ModuleFilter implements Predicate<HalSubPlugin>
    {
        ALL(p -> true),
        SKILLING(p -> p.getCategory() == HalModuleCategory.SKILLING),
        MONEY_MAKING(p -> p.getCategory() == HalModuleCategory.MONEY),
        ACTIVITY(p -> p.getCategory() == HalModuleCategory.ACTIVITY),
        BOSSING(p -> p.getCategory() == HalModuleCategory.BOSSING),
        UTILITY(p -> p.getCategory() == HalModuleCategory.UTILITY); // matches any assigned category

        private final Predicate<HalSubPlugin> predicate;

        @Getter
        private final String displayName;

        private final boolean shouldDisplay;

        ModuleFilter(Predicate<HalSubPlugin> predicate) {
            this(predicate, true);
        }

        ModuleFilter(Predicate<HalSubPlugin> predicate, boolean shouldDisplay) {
            this.predicate = predicate;
            this.displayName = Text.titleCase(this);
            this.shouldDisplay = shouldDisplay;
        }

        @Override
        public boolean test(HalSubPlugin module) {
            return predicate.test(module);
        }

        public List<HalSubPlugin> test(Collection<HalSubPlugin> allModules) {
            return allModules.stream().filter(this).collect(Collectors.toList());
        }

        public static ModuleFilter[] visibleFilters() {
            return Arrays.stream(ModuleFilter.values())
                    .filter(ModuleFilter::isShouldDisplay)
                    .toArray(ModuleFilter[]::new);
        }

        public boolean isShouldDisplay() {
            return shouldDisplay;
        }
    }
}
