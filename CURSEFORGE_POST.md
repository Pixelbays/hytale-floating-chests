# Floating Chests

Floating Chests is a lightweight Hytale server mod that removes bottom-support requirements from chest-style furniture blocks.

Instead of rewriting asset JSON, the mod scans loaded item assets at runtime, finds furniture chests, and patches their support rules in memory so they can stay in place even when the block underneath is removed.
So now it supports other modded chests!

## Updated from Asset Pack to Plugin!

Automatic runtime scanning for furniture chests based on loaded item assets  
Support patching for compatible modded chests as well as vanilla-style ones  
Live handling of relevant asset updates during runtime  
Persistent include and exclude overrides through commands  
No asset-pack JSON rewriting

## What the mod changes

Chests no longer require bottom support to remain placed  
Placed chests keep their orientation and contents  
Behavior is applied at runtime and rebuilt on server startup  
Manual overrides persist between restarts

## Commands

`/chestsupport`  
Show a summary of what is being auto-detected and patched

`/chestsupport include add <block_id>`  
Force a chest block to be affected

`/chestsupport include remove <block_id>`  
Remove a forced include

`/chestsupport exclude add <block_id>`  
Prevent a chest block from being affected

`/chestsupport exclude remove <block_id>`  
Remove a forced exclude

`/chestsupport list`  
Show current include, exclude, and applied block ids

`/chestsupport status <block_id>`  
Inspect the current match and override state of a block id

`/chestsupport reapply`  
Re-scan the loaded item assets and rebuild the active patch set

## Compatibility notes

The mod is designed for chest-style item assets that:

Have `chest` in the item id  
Use `Tags.Type = Furniture`  
Contain an inline `BlockType`

If a chest is missed or the scan is too aggressive, use the include and exclude commands to correct it.

## Why use it

Floating Chests is a pure quality-of-life change for builders and survival players who want storage freedom without rewriting every chest asset by hand.