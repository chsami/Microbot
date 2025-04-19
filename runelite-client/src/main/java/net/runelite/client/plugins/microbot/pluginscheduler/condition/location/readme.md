
``` Java
// Check if player is at a bank
Condition atGrandExchange = LocationCondition.atBank(BankLocation.GRAND_EXCHANGE, 5);

// Check if player is at any farming patch
Condition atAnyAllotmentPatch = LocationCondition.atFarmingPatch(FarmingPatchLocation.ALLOTMENT, 3);

// Check if player is at a specific yew tree (all yew tree locations)
Condition atYewTree = LocationCondition.atRareTree(RareTreeLocation.YEW, 2);

// Check if player is in an area around a point
Condition inMiningArea = LocationCondition.createArea("Mining Area", new WorldPoint(3230, 3145, 0), 10, 10);
```

``` Java
// Check if player is in any of several areas
WorldArea area1 = new WorldArea(3200, 3200, 10, 10, 0);
WorldArea area2 = new WorldArea(3300, 3300, 5, 5, 0);
Condition inEitherArea = LocationCondition.inAnyArea("Training areas", new WorldArea[]{area1, area2});

// Using raw coordinates
int[][] miningAreas = {
    {3220, 3145, 3235, 3155, 0},  // Mining area 1
    {3270, 3160, 3278, 3168, 0}   // Mining area 2
};
Condition inAnyMiningArea = LocationCondition.inAnyArea("Mining spots", miningAreas);
```