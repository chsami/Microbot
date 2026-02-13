# Rs2Spells Class Documentation

## [Back](development.md)

## Overview
The `Rs2Spells` enum defines all supported spells from Modern, Ancient, Lunar, and Arceuus spellbooks. Each enum constant represents a spell and contains information about its required runes, level, and spellbook.

## Methods

### `getElementalRunes`
- **Signature**: `public List<Runes> getElementalRunes()`
- **Description**: Returns only the elemental runes (Air, Water, Earth, Fire) required for this spell. This is useful for checking if a staff or combination runes can satisfy the spell's requirements.

### `getRequiredRunes`
- **Signature**: `public HashMap<Runes, Integer> getRequiredRunes()`
- **Description**: Returns a map of runes and their quantities required to cast this spell.
