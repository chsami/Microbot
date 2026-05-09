# Rs2LeaguesTransport

**Source:** `runelite-client/.../microbot/util/leaguetransport/Rs2LeaguesTransport.java`  
**Related:** `LeaguesRegion`, `LeagueTransportWidgets`

API driver for Leagues area teleports via UI chain:

`Activities -> Leagues -> View Areas -> Teleport to <region>`

## Primary call

- `LeaguesTeleportResult leaguesTeleport(LeaguesRegion region)`
  - Runs context gates + unlocked-region scan in one client-thread pass
  - Blocks caller thread only (no client-thread blocking)
  - Performs full UI chain internally
  - Returns rich result: success, failure reason enum, message, target, unlocked snapshot

## Gates

- **Seasonal world**: `WorldType.SEASONAL`
- **League save active**: `VarbitID.LEAGUE_ACCOUNT > 0`
- **Region unlocked**: `region.getAreaId()` present in `LEAGUE_AREA_SELECTION_0..5`

## Unlocked regions snapshot (future webwalker interrupt)

- `EnumSet<LeaguesRegion> unlockedRegions()`
  - Returns empty set when not in leagues context
  - Uses `LEAGUE_AREA_SELECTION_0..5` varbits, mapped to `LeaguesRegion` by `areaId`

## Advanced: non-blocking driver

- `Rs2LeaguesTransport.LeaguesTeleportDriver`
  - Returned internally today; intended for advanced callers
  - Call `tick()` from script loop until `!isActive()`

## Widget ids

Widget ids live in package-private `LeagueTransportWidgets`. Re-verify ids after game/client bumps if chain stops working.

