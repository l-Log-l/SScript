---
title: Расширенные Особенности и Механики
nav_order: 6
parent: РУ Главная
---

# Расширенные Особенности и Механики

Помимо базового скриптинга, SScript имеет мощные расширенные возможности для сложной логики, обработки ошибок и оптимизации.

---

## Обработка Ошибок с Try-Catch

### Базовый Синтаксис

```python
try:
    // Код который может не промыть
    result = http_get("https://api.example.com/data")
    data = result.json
    log data.score
catch err:
    // Обработать ошибку
    log "Error occurred: " + err
end
```

### Обычные Ошибки для Перехвата

```python
try:
    // Неверный путь к файлу
    content = file_read("nonexistent.txt")
catch err:
    log "File not found: " + err
end

try:
    // Timeout сети
    resp = http_get("https://slow-server.com")
catch err:
    log "HTTP error: " + err
end

try:
    // Ошибка парсинга JSON
    obj = json_parse("invalid json")
catch err:
    log "JSON parse failed: " + err
end

try:
    // Деление на нуль или несовместимость типов
    result = 10 / 0
catch err:
    log "Math error: " + err
end
```

### Детали Сообщения об Ошибке

Когда ошибка поймана, `err` содержит:
- **Тип ошибки** (FileNotFound, HttpTimeout, JsonParseError и т.д.)
- **Сообщение** (понятное описание)
- **Номер строки** (где произошла ошибка)

**Пример:**

```python
try:
    run "invalid command syntax"
catch err:
    log "Error: " + err  // "Error: CommandSyntaxException at line 42"
end
```

---

## Селекторы Игроков

SScript выполняет селекторы Minecraft во время выполнения, чтобы найти игроков.

### Поддерживаемые Селекторы

```python
// Все онлайн игроки
players = get_targets("@a")

// Все онлайн игроки в пределах 100 блоков
nearby = get_targets("@a[distance=..100]")

// Игроки с определенным тегом
verified = get_targets("@a[tag=verified]")

// Игрок с наименьшим расстоянием
loser = get_target("@a[sort=nearest,limit=1]")
```

### Свойства Селектора

| Селектор | Эффект | Пример |
|----------|--------|--------|
| `distance` | Диапазон в блоках | `@a[distance=0..50]`, `@a[distance=..100]` |
| `tag` | Имеет/нет тег | `@a[tag=admin]`, `@a[tag=!banned]` |
| `scores` | Баллы в объективе | `@a[scores={kills=5..}]` |
| `gamemode` | Выживание/Творчество/и т.д | `@a[gamemode=survival]` |
| `limit` | Макс результатов | `@a[limit=5]` |
| `sort` | ближайший/случайный/дальний | `@a[sort=nearest]` |
| `name` | Имя игрока | `@a[name=Steve]` |
| `level` | Уровень опыта | `@a[level=10..]` |

### Динамические Селекторы

```python
func find_admins():
    admins = get_targets("@a[tag=admin]")
    log "Found " + str(len(admins)) + " admins"
    return admins
end

on player_join(player):
    nearby = get_targets("@a[distance=0..50,gamemode=survival]")
    for p in nearby:
        tellraw(p.name, player.name + " is nearby!")
    end
end
```

### Локальный и глобальный чат

`*.mixin.ss` может отменить исходный чат и перенаправить его в local/global режим.

```python
on player_chat(player, message):
    if starts_with(message, "[l]"):
        run "execute at " + player.name + " run tellraw @a[distance=..50] " + json_stringify({"text": player.name + ": " + trim(substring(message, 3))})
        return false
    else:
        run "tellraw @a " + json_stringify({"text": player.name + ": " + message})

        http_post(
            "https://discord.com/api/webhooks/REPLACE_ME",
            {
                "content": player.name + ": " + message,
                "username": "SScript Chat"
            }
        )

        return false
    end
end
```

Если нужен только local chat без webhook, оставь только ветку с `run`.

---

## Структура Объекта Игрока и NBT Данные

Объекты игроков содержат данные NBT Minecraft извлеченные во время выполнения:

### Базовые Свойства

```python
player.name           // "Steve"
player.uuid           // "a1b2c3d4-..." (уникальный ID)
player.health         // 20.0 (0-20)
player.max_health     // 20.0
player.food_level     // 10 (0-20)
player.saturation     // 5.2 (десятичная)
player.x_pos          // 100.5 (float)
player.y_pos          // 64.2
player.z_pos          // -50.1
player.dimension      // "minecraft:overworld"
player.gamemode       // "survival", "creative", "adventure", "spectator"
player.level          // 42 (уровень XP)
player.experience     // 0.8 (0.0-1.0, десятичная часть)
```

### Продвинутые Свойства

```python
player.on_ground      // true/false
player.flying         // true/false
player.sneaking       // true/false
player.sprinting      // true/false
player.swimming       // true/false
player.fall_distance  // 3.2 (блоки упав)
player.fire           // 0 (тики в огне)
player.air            // 300 (оставшийся воздух под водой)
player.pose           // "standing", "swimming", "falling" и т.д
```

### Пример с Инвентарем

```python
on player_join(player):
    health = player.health
    level = player.level
    
    if health < 5:
        log player.name + " has low health: " + str(health)
    end
    
    if level >= 30:
        tag_add(player.name, "high_level")
    end
end
```

---

## HTTP Headers и Пользовательские Запросы

### Базовые GET/POST (Авто Headers)

```python
// GET запрос - авто Content-Type: application/x-www-form-urlencoded
resp = http_get("https://api.example.com/data")

// POST запрос - авто Content-Type: application/json если body это JSON
resp = http_post("https://api.example.com/data", {"key": "value"})
```

### Полный Запрос с Пользовательскими Headers

```python
response = http_request(
    "POST",
    "https://api.example.com/users",
    {
        "Authorization": "Bearer token123",
        "X-Custom-Header": "myvalue"
    },
    {"name": "Steve", "score": 100}
)

if response.ok:
    log "Request succeeded: " + str(response.status)
else
    log "Request failed: " + str(response.status)
end
```

### Объект Response

```python
response.ok         // true если 200-299 статус
response.status     // 200, 404, 500 и т.д.
response.text       // Сырое тело ответа как строка
response.json       // Парсенный JSON объект (если JSON ответ)
response.headers    // Map ответных headers
```

### Пример: Интеграция с API

```python
func fetch_player_stats(player_name):
    try:
        resp = http_get("https://api.example.com/players/" + player_name)
        if resp.ok:
            stats = resp.json
            return stats  // {"kills": 10, "deaths": 2, "wins": 5}
        else
            log "API error: " + str(resp.status)
            return null
        end
    catch err:
        log "Network error: " + err
        return null
    end
end

on player_join(player):
    stats = fetch_player_stats(player.name)
    if stats:
        tellraw(player.name, "Kills: " + str(stats.kills))
    end
end
```

---

## Методы Списков

Списки/массивы поддерживают методы для манипуляции:

### `.add(value)` — Добавить Элемент

```python
items = []
items.add("apple")
items.add("banana")
items.add("apple")

log len(items)  // 3
```

### `.remove(index)` — Удалить по Индексу

```python
items = ["a", "b", "c"]
items.remove(1)      // Удалить "b"

log items           // ["a", "c"]
log len(items)      // 2
```

### Практический Пример

```python
func collect_online_staffs():
    all_players = get_targets("@a")
    staff_list = []
    
    for player in all_players:
        if has_tag(player.name, "staff"):
            staff_list.add(player.name)
        end
    end
    
    return staff_list
end

on player_break_block(player, block):
    staffs = collect_online_staffs()
    
    for staff_name in staffs:
        tellraw(staff_name, player.name + " broke " + block.id)
    end
end
```

---

## Постоянство Глобальных Переменных

Глобальные переменные сохраняются в `sscripts/globals.json` и сохраняются при перезагрузке сервера.

### Автоматическое Поведение Сохранения

- Изменения debounced: **сохраняются максимум каждые 2 секунды**
- Не сохраняются сразу (чтобы избежать I/O перегрузки)
- Сохраняются при мягком выключении сервера

### Ручные Триггеры Сохранения

```python
set_global("important_data", {"version": 3})
// Будет направлено на диск в течение 2 секунд

// Немедленное сохранение (на следующем tick):
// Выключение сервера вызывает сохранение
```

### Пример: Постоянство Статов Игрока

```python
func record_kill(player_name):
    current = num(get_global("kills_" + player_name))
    set_global("kills_" + player_name, current + 1)
    
    // Автоматически сохранено на диск
end

on player_death(player, location):
    if location.killer:
        record_kill(location.killer)
    end
end

on server_start:
    // Загрузить сохраненные статистики убийств
    log "Loading persistent stats..."
    // (статистика загружена из globals.json)
end
```

---

## Ограничения Производительности и Пределы

### Пределы Выполнения

| Лимит | Значение | Последствие |
|-------|----------|------------|
| Макс запущенные процессы | 500 | Не может создать больше (ставится в очередь вместо) |
| Процессы созданные за tick | 20 | Макс 20 новых процессов/tick (предотвращает всплеск лага) |
| Операторы за tick | 50 | Процесс ограничен до 50 операторов/tick (распределены по нескольким никам) |
| Итерации цикла | 1,000,000 | Бесконечные циклы разрываются (лимит безопасности) |
| While loop guard | Включен | Предотвращает убегающие циклы |
| HTTP timeout | 60 секунд | Долгие запросы отменяются после 60s |
| File I/O timeout | 30 секунд | Файловые операции timeout |

### Оптимизация Памяти

```python
// ❌ ПЛОХО: Создает 100k список сразу
on server_start:
    huge_list = get_blocks(-1000, 0, -1000, 1000, 100, 1000)
end

// ✅ ХОРОШО: Обработать блоки меньшими кусками
on server_start:
    wait process_region_1()
    wait process_region_2()
    wait process_region_3()
end

func process_region_1():
    blocks = get_blocks(-1000, 0, -1000, 0, 100, 1000)
    log "Processed region 1: " + str(len(blocks))
end
```

### Бюджет Tick

Каждый тик сервера (50ms игровое время), SScript процессы завершают:
- **Макс 20 новых процессов** могут быть созданы
- **Каждый запущенный процесс** выполняет ~**50 операторов**
- Оставшееся время выполняет нормальную логику Minecraft

**Последствие:** Долгие задачи автоматически распределяются на несколько тиков.

---

## Разрешение Пути Файла

Файлы разрешаются относительно **корня сервера**:

```python
// Эти эквивалентны (оба создают sscripts/data/players.json):
file_write_json("sscripts/data/players.json", data)
file_write_json("sscripts/data/players.json", data)  // Из корня сервера

// Эти НЕ эквивалентны:
file_write_json("data.json", data)           // Создает в корне сервера
file_write_json("logs/script.log", data)     // Создает в корне сервера/logs
```

**Лучшая практика:** Начинайте пути с `sscripts/` для организации.

---

## Отладка Расширенных Особенностей

### Включить Вывод Отладки

```
/sscript debug on
```

Это логирует:
- Каждый вызов функции
- Присваивание переменных
- Оценку селекторов
- Отправленные HTTP запросы
- Файловые I/O операции

### Трассировка Асинхронного Выполнения

```python
func slow_fetch(player):
    log "[FETCH] Starting for " + player
    wait http_get("https://api.example.com")
    log "[FETCH] Completed for " + player
end

on player_join(player):
    log "[EVENT] Player join: " + player.name
    slow_fetch(player)
    log "[EVENT] Handler complete"
end

// Вывод:
// [EVENT] Player join: Steve
// [EVENT] Handler complete
// [FETCH] Starting for Steve
// [FETCH] Completed for Steve
```

---

## Смотрите Также

- [Functions & Async Mechanics](functions-mechanics.md) — Асинхронные паттерны
- [Commands Reference](commands.md) — Команды сервера
- [Complete Built-in Reference](complete-reference.md) — Все 60+ функций
