``` Java
// Track kills for multiple NPCs with different requirements (must kill ALL to satisfy)
List<String> npcNames = Arrays.asList("Goblin", "Cow", "Chicken");
List<Integer> minCounts = Arrays.asList(10, 5, 15);
List<Integer> maxCounts = Arrays.asList(15, 10, 20);
LogicalCondition killAllCondition = NpcKillCountCondition.createAndCondition(npcNames, minCounts, maxCounts);

// Track kills for multiple NPCs with same requirements (must kill ANY to satisfy)
LogicalCondition killAnyCondition = NpcKillCountCondition.createOrCondition(
    Arrays.asList("Dragon", "Demon", "Giant"), 5, 10);

// Add to condition manager
conditionManager.addCondition(killAllCondition);
```