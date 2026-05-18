---
title: Events Reference
nav_order: 4
parent: EN Home
---

# Events Reference

## Core events

- `load`
- `player_connect(player)`
- `player_join(player)`
- `player_chat(player, message)`
- `player_death(player, death_pos)`
- `player_dead(player, death_pos)`
- `player_respawn(player, alive)`
- `player_sleep_attempt(player, pos)`
- `player_sleep(player, pos)`
- `block_break(player, block)`
- `block_interact(player, block)`
- `block_place(player, block)`

## Payload notes

### player object

Common fields:
- `name`, `uuid`, `type`
- `x`, `y`, `z`, `pos`
- `dimension`, `health`, `gamemode`
- `tags`, `nbt`

### block object

Common fields:
- `id`
- `x`, `y`, `z`, `pos`
- `dimension`

## Example

```python
on block_place(player, block):
    log player.name + " placed " + block.id + " at " + block.pos
end
```
