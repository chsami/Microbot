# ADR 0005: Separate Transport Description from Execution Capability

- Status: Accepted (2026-08-05)

## Context
Shortest Path transport data describes where an edge goes and what it requires, while Microbot also
needs to execute the edge against a live game client. Those responsibilities cannot be treated as the
same contract: imported data can describe a route that Microbot does not yet know how to operate, and
some local transports carry behavior that a flattened data value cannot preserve. In particular,
`PohTransport` owns an executable POH action and seasonal handlers are a pluggable runtime API.

Converting every interaction handler directly from the local `Transport` class to the immutable
`Rs2TransportEdge` value would remove that behavior or recreate it through type switches and legacy
adapters. Retaining an unrestricted concrete planner object as the public route contract would instead
prevent a future upstream planner adapter.

## Decision
Represent every selected route transport with:

- an immutable, planner-independent `Rs2TransportEdge` description;
- an explicit `Rs2TransportExecutor` capability naming the Microbot runtime branch that owns it; and
- for terminal `SHIP`, `NPC` and `BOAT` travel, an explicit planner-independent interaction mode;
- only for the local engine, an opaque package-private payload containing the exact selected local
  transport needed by the existing handlers.

The pure planner-side `TransportExecutionRegistry` is authoritative for whether a local catalog row is
executable. Unregistered rows fail closed during transport refresh and cannot be selected merely because
their data was imported. Runtime dispatch also rejects `UNSUPPORTED` as a defensive invariant. The local
payload is never recovered by matching endpoints or rescanning the mutable catalog.

Terminal catalog families describe a journey rather than the live target kind: rows may point at an NPC
or a scene object. The runtime resolves that kind from the configured semantic name/action near the exact
selected origin. The registry still owns the interaction sequence. Direct travel and a known dialogue
destination flow are separate modes; rows needing an unimplemented destination selection remain
unregistered even when another row in the same catalog family is executable.

## Consequences
- Upstream catalog convergence cannot silently create routes that stall at an unimplemented interaction.
- An upstream planner adapter can emit the same immutable edge and executor capability without exposing
  its model classes.
- POH and other behavior-bearing transports remain correct while their handlers are gradually moved
  behind Microbot-owned execution interfaces.
- The concrete local payload remains an internal implementation detail rather than a migration target by
  itself; replacing it requires an equivalent executable capability, not a blanket signature rewrite.
- Missing executor families become explicit delivery work. At adoption, the non-Lumbridge home teleports
  and hot-air-balloon network were deliberately fail-closed; the shared exact-name home-teleport executor
  subsequently closed the former gap. The balloon network now has an exact six-destination map executor
  and observed-landing contract; its static, dual-engine and locked-account fail-closed evidence is complete,
  while a successful flight remains pending on an account with an unlocked station.
- Terminal-travel coverage cannot be inferred from `TransportType`. The current audit deliberately keeps
  41 multi-step rows fail-closed: 30 `Board` rows for the multi-destination Boat/Boaty networks, six
  destination-selecting Rowboat rows and five unimplemented `Talk-to` rows. Their exact interaction
  groups are pinned by the catalog capability test.
- Inventory-item interactions are capabilities too. The Barrows dig executor is registered only for the
  six reviewed mound-to-individual-crypt pairs with an exact one-spade requirement; arbitrary object-less
  `Dig` rows remain unregistered. Individual-crypt stairs use ordinary object execution and a representative
  surface-mound anchor because the live exit spawn can vary. Randomized sarcophagus-to-tunnel entry remains
  outside the static catalog until the executor can consume observed run state.
