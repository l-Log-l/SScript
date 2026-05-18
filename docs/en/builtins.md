---
title: Built-ins Reference
nav_order: 5
parent: EN Home
---

# Built-ins Reference

## Player helpers

- `players()`
- `random_player()`
- `player_count()`
- `online(name)`
- `player_tags(player)`

## Tags and effects

- `has_tag(player, tag)`
- `tag_add(player, tag)`
- `tag_remove(player, tag)`
- `effect_give(name, id, sec, amp, hide)`
- `effect_clear(name, id)`

## Targets and blocks

- `get_targets(selector)`
- `get_target(selectorOrName)`
- `pos(x, y, z)`
- `get_block(...)`
- `get_blocks(...)`
- `has_block(...)`

## Math and string

Math:
- `range`, `int`, `sec`, `random`, `floor`, `ceil`, `abs`, `min`, `max`, `sqrt`, `pow`, `round`

String:
- `len`, `upper`, `lower`, `contains`, `replace`, `substring`, `starts_with`, `ends_with`, `trim`, `index_of`, `split_get`, `split_count`

Type:
- `str`, `num`, `bool`, `type`
