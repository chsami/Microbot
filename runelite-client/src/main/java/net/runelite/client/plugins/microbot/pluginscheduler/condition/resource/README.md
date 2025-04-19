``` JAVA
// Track making 50 adamant daggers
ProcessItemCondition makeDaggers = ProcessItemCondition.forProduction("adamant dagger", 50);

// Track using 100 adamant bars (regardless of what they make)
ProcessItemCondition useAdamantBars = ProcessItemCondition.forConsumption("adamant bar", 100);

// Track making platebodies specifically (which use 5 bars each)
ProcessItemCondition makePlatebodies = ProcessItemCondition.forRecipe(
    "adamant bar", 5,  // Source: 5 adamant bars
    "adamant platebody", 1,  // Target: 1 platebody
    20  // Make 20 platebodies
);

// Track making any herblore potions with ranarr weed
ProcessItemCondition useRanarrs = ProcessItemCondition.forConsumption("ranarr weed", 50);

// Track making prayer potions specifically
ProcessItemCondition makePrayerPots = ProcessItemCondition.forMultipleConsumption(
    Arrays.asList("ranarr potion (unf)", "snape grass"),
    Arrays.asList(1, 1),
    50  // Make 50 prayer potions
);

// Randomized condition - make between 25-35 necklaces of crafting
ProcessItemCondition makeCraftingNecklaces = ProcessItemCondition.createRandomizedProduction(
    "necklace of crafting", 25, 35
);

```