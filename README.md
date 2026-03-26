<h1 style="text-align: center;">Floating Chests (Hytale)</h1>

Floating Chests is a lightweight behavior mod that removes bottom-support requirements from chest-style furniture blocks at runtime.

### What this mod does

In vanilla Hytale behavior, many chests will break if the supporting block beneath them is removed. Floating Chests changes that at boot and during asset reloads by scanning loaded item assets for furniture chests and patching their block support rules in memory.

The mod does not rewrite any JSON files. All changes are applied live while the server is running.

### Key Features

Chests no longer break when the block below them is removed  
Chests stay exactly where they were placed, preserving orientation and contents  
Works with vanilla-style furniture chests and compatible modded chests that follow the same asset pattern  
Updates automatically when relevant item assets reload  
Manual include and exclude overrides are available through a command and persist between restarts  
No new blocks, GUIs, or recipes, this is a pure behavior change  
Fully compatible with existing worlds (no migration required)

### How it works

The plugin looks for loaded item assets that:

Have `chest` in the item id  
Have `Tags.Type` set to `Furniture`  
Define an inline `BlockType`

When a match is found, the plugin patches the block's `Support.Down` rules so bottom support is ignored. This is applied in memory only.

### Commands

Use `/chestsupport` to inspect or override behavior.

`/chestsupport`  
Shows a summary of automatic matches, manual overrides, and active patched blocks

`/chestsupport include add <block_id>`  
Force a block id to be affected even if it is not auto-detected

`/chestsupport include remove <block_id>`  
Remove a forced include

`/chestsupport exclude add <block_id>`  
Prevent a block id from being affected even if it is auto-detected

`/chestsupport exclude remove <block_id>`  
Remove a forced exclude

`/chestsupport list`  
List current include, exclude, and applied block ids

`/chestsupport status <block_id>`  
Show whether a block is auto-matched, included, excluded, effective, and currently applied

`/chestsupport reapply`  
Force a fresh re-scan and rebuild of the runtime chest patch set

### Persistence

Manual include and exclude overrides are saved in plugin config and persist between restarts.

The support-rule patch itself is not saved into asset files. On startup, the server loads assets normally and Floating Chests reapplies its runtime changes.

### How this affects gameplay

This mod is designed to reduce frustration and expand building freedom:  
Builders can create floating platforms, airships, tree builds, caves, and decorative storage without worrying about chest physics  
Players can safely clear terrain or change their floor without accidentally destroying storage

### Why download this mod?

If you’ve ever:  
Broken a chest by accident changing the floor  
Wanted more freedom when designing builds  
Needed storage that doesn’t rely on block support

Then Floating Chests provides a clean, lightweight solution that simply makes the game behave the way you expect.