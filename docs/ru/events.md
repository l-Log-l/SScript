---
title: Справочник событий
nav_order: 4
parent: РУ Главная
---

# Справочник событий

## Основные события

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

## Payload

### player

Обычно содержит:
- `name`, `uuid`, `type`
- `x`, `y`, `z`, `pos`
- `dimension`, `health`, `gamemode`
- `tags`, `nbt`

### block

Обычно содержит:
- `id`
- `x`, `y`, `z`, `pos`
- `dimension`

## Пример

```python
on block_place(player, block):
    log player.name + " поставил " + block.id + " в " + block.pos
end
```
