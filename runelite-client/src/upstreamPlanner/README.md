# Pinned upstream planner core

This source set contains the non-UI Java core from `Skretzo/shortest-path` at the exact revision in
`UPSTREAM_REVISION`. It deliberately excludes the upstream RuneLite plugin and overlays so Microbot ships
one plugin owner. `shortestpath.ShortestPathPlugin` is a compatibility anchor with no plugin descriptor.

Keep upstream-derived files byte-identical except for changes listed in `ADAPTER_PATCHES.md`. The drift
checker verifies the pin and adapter patch surface. The classes are packaged in their original
`shortestpath.*` namespace so upstream diffs stay mechanical and reviewable.
