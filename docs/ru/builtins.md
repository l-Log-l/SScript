---
title: Справочник built-in функций
nav_order: 5
parent: РУ Главная
---

# Справочник built-in функций

## Игроки

- `players()`
- `random_player()`
- `player_count()`
- `online(name)`
- `player_tags(target)`

## Теги и эффекты

- `has_tag(target, tag)`
- `tag_add(target, tag)`
- `tag_remove(target, tag)`
- `effect_give(name, id, sec, amp, hide)`
- `effect_clear(name, id)`

## Mixins

В `.mixin.ss`-обработчиках `return true` отменяет действие, а `return false` или отсутствие `return` оставляет его как есть.

## Таргеты и блоки

### `get_target(selector)` → объект | null
Получает одну сущность по селектору или имени.

Поддерживает `@a`, `@e`, `@p`, фильтры по тегам и обычные имена игроков. Возвращает объект с полями: `name`, `uuid`, `type`, `dimension`, `x`, `y`, `z`, `pos`, `tags`, `nbt`, `selector`.

### `get_targets(selector)` → список
Получает все сущности по селектору. Поддерживает:
- `"@a"` — все игроки
- `"@e"` — все сущности
- `"@e[tag=admin]"` — все сущности с тегом "admin"
- `"PlayerName"` — точное имя игрока

## Векторы

- `vec2(x, y)`
- `vec3(x, y, z)`
- `vec_add(a, b)`
- `vec_sub(a, b)`
- `vec_scale(vec, scalar)`
- `vec_dot(a, b)`
- `vec_length(vec)`
- `vec_distance(a, b)`
- `vec_normalize(vec)`

Векторные функции возвращают объект с полями `x`, `y`, `z`.

## Математика и строки

Математика:
- `range`, `int`, `sec`, `random`, `floor`, `ceil`, `abs`, `min`, `max`, `sqrt`, `pow`, `round`

Строки:
- `len`, `upper`, `lower`, `contains`, `replace`, `substring`, `starts_with`, `ends_with`, `trim`, `index_of`, `split_get`, `split_count`

Типы:
- `str`, `num`, `bool`, `type`
