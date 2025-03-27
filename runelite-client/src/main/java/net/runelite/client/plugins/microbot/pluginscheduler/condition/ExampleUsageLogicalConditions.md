``` Java
// Example of creating complex logical conditions
ConditionManager manager = new ConditionManager();

// Create a condition that will stop when:
// (player has 100 feathers OR player has 50 logs) AND (player has reached level 20 Fishing OR has gained 10,000 Fishing XP)

// Create item conditions
LootItemCondition feathersCondition = LootItemCondition.builder()
    .itemName("Feather")
    .itemId(ItemID.FEATHER)
    .targetAmount(100)
    .build();

LootItemCondition logsCondition = LootItemCondition.builder()
    .itemName("Logs")
    .itemId(ItemID.LOGS)
    .targetAmount(50)
    .build();

// Create skill conditions
SkillLevelCondition fishingLevelCondition = new SkillLevelCondition(Skill.FISHING, 20);
SkillXpCondition fishingXpCondition = new SkillXpCondition(Skill.FISHING, 10000);

// Create logical structure
OrCondition itemsOr = manager.or()
    .addCondition(feathersCondition)
    .addCondition(logsCondition);

OrCondition skillsOr = manager.or()
    .addCondition(fishingLevelCondition)
    .addCondition(fishingXpCondition);

AndCondition rootLogicalCondition = manager.and()
    .addCondition(itemsOr)
    .addCondition(skillsOr);

manager.setRootLogicalCondition(rootLogicalCondition);

```