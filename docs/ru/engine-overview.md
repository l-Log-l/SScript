---
title: Обзор движка
nav_order: 1
parent: РУ Главная
---

# SScript Engine - Полный Обзор

## Что такое SScript?

**SScript** это мод Fabric, который добавляет язык программирования похожий на Python на серверы Minecraft. Позволяет администраторам писать логику игры без модификации кода Minecraft.

**Основные возможности:**
- Архитектура, управляемая событиями (присоединение игроков, чат, разрушение блоков и т.д.)
- 80+ встроенных функций (математика, строки, файлы, HTTP, JSON)
- Постоянные глобальные переменные (сохраняются при перезагрузке сервера)
- HTTP клиент (GET, POST, запросы)
- Работа с файлами и JSON поддержка
- Система тегов для управления игроками
- Выполнение команд
- Асинхронные возможности

## Быстрые факты

| Аспект | Детали |
|--------|--------|
| Язык | Python-подобный синтаксис |
| Модель выполнения | Tick-based, асинхронный |
| Типы файлов | `.ss`, `.event.ss`, `load.ss` |
| Встроенные функции | 80+ (игроки, блоки, математика, строки, HTTP, файлы и т.д.) |
| События | 13 (join, chat, block events, death, sleep и т.д.) |
| Постоянство | Глобальные переменные в JSON |
| Производительность | 100k итераций максимум, 60s HTTP timeout |
| Целевая версия | Minecraft 1.21.11 (Fabric) |

## Два Типа Скриптов

### 1. Runtime Скрипты (`.ss`)
- Выполняются при запуске сервера или при ручной загрузке
- Хорошо для: инициализация, периодические задачи, одноразовая подготовка

```python
# sscripts/init.ss
file_mkdirs("sscripts/data")
log "Server initialized"
set_global("server_uptime", 0)
```

### 2. Обработчики Событий (`.event.ss`)
- Слушают события в игре и реагируют на них
- Хорошо для: реакция на действия игрока, логирование, автоматизация

```python
# sscripts/chat.event.ss
on player_join(player):
    log player.name + " joined"
end

on player_chat(player, message):
    file_append("sscripts/logs/chat.log", player.name + ": " + message + "\n")
end
```

## Все 13 События

| События | Триггер | Параметры |
|---------|---------|-----------|
| `load` | Запуск сервера | (нет) |
| `player_connect` | Соединение инициализировано | `(player)` |
| `player_join` | Игрок полностью присоединился | `(player)` |
| `player_chat` | Сообщение отправлено | `(player, message)` |
| `player_death` | Игрок умер | `(player, location)` |
| `player_dead` | То же что death (альтернативное имя) | `(player, location)` |
| `player_respawn` | Игрок возродился | `(player, alive)` |
| `player_sleep_attempt` | Попытка спать | `(player, bed_pos)` |
| `player_sleep` | Успешно уснул | `(player, bed_pos)` |
| `block_interact` | Клик правой кнопкой по блоку | `(player, block)` |
| `block_place` | Реальный блок размещен | `(player, block)` |
| `block_break` | Блок разрушен | `(player, block)` |

## Основные Категории Функций

### Управление Игроками (10 функций)
- `players()`, `player_count()`, `random_player()`
- `online(name)`, `get_target()`, `get_targets()`
- `has_tag()`, `tag_add()`, `tag_remove()`, `player_tags()`

### Эффекты и Команды (4 функции)
- `effect_give()`, `effect_clear()`, `exec()`, `tellraw()`

### Блоки и Позиции (4 функции)
- `pos()`, `get_block()`, `get_blocks()`, `has_block()`

### Математика (11 функций)
- `range()`, `random()`, `floor()`, `ceil()`, `round()`, `abs()`
- `min()`, `max()`, `sqrt()`, `pow()`, `int()`

### Операции со Строками (15 функций)
- `len()`, `upper()`, `lower()`, `contains()`, `starts_with()`, `ends_with()`
- `trim()`, `replace()`, `substring()`, `index_of()`
- `split_get()`, `split_count()`

### Преобразование Типов (8 функций)
- `str()`, `num()`, `bool()`, `type()`, `sec()`, `int()`

### JSON Функции (2 функции)
- `json_parse()`, `json_stringify()`

### HTTP Функции (3 функции)
- `http_get()`, `http_post()`, `http_request()`

### Работа с Файлами (11 функций)
- `file_exists()`, `file_read()`, `file_write()`, `file_append()`
- `file_delete()`, `file_delete_recursive()`, `file_rename()`, `file_mkdirs()`, `file_list()`
- `file_read_json()`, `file_write_json()`

### Глобальные Переменные (2 функции)
- `get_global()`, `set_global()`

## Особенности Языка

```python
# Переменные
x = 42
name = "Steve"
player = {"name": "Alex", "level": 10}
items = [1, 2, 3]

# Функции (должны быть на верхнем уровне)
func greet(name):
    return "Hello, " + name
end

# Условия
if x > 10 and name == "Steve":
    log "Pass"
end

# Циклы
for i in range(1, 5):
    log str(i)
end

# Асинхронные операции в событиях
on player_join(player):
    log "Joined"
    wait heavy_task, player.name  # Асинхронный вызов
    log "Queued"
end

# Обработка ошибок
try:
    resp = http_get("https://api.example.com")
catch err:
    log "Error: " + err
end
```

## Модель Выполнения

### Линейное Выполнение (`.ss` файлы)
1. Загрузить скрипт
2. Зарегистрировать все функции
3. Выполнить верхнеуровневые операторы
4. Завершено

### Выполнение Управляемое События (`.event.ss` файлы)
1. Загрузить скрипт
2. Зарегистрировать функции и обработчики событий
3. Когда событие срабатывает:
   - Создать процесс для каждого совпадающего обработчика
   - Запустить обработчик на планировщике (tick-based)
4. Несколько обработчиков работают независимо

### Асинхронный Паттерн с `wait`
```
on player_join(player):
    log "A"           // Выполняется сразу
    wait fetch_data   // Ставится в очередь асинхронно
    log "B"           // Выполняется сразу (не ждет)
end
// fetch_data выполняется на следующем tick
```

## Организация Файлов

### Места Расположения
- Скрипты: `sscripts/` директория
- Глобальные переменные: `sscripts/globals.json`
- Логи: `logs/latest.log` + `logs/sscript/` папка

### Расширения Файлов
- `.ss` — Runtime скрипты
- `.event.ss` — Обработчики событий
- Специальные: `load.ss` запускается первым
- Обычные `.ss` файлы запускаются при загрузке

## Советы по Производительности

✅ **Делайте:**
- Используйте `wait` для HTTP запросов в событиях
- Кешируйте операции поиска в переменные
- Используйте теги для организации игроков
- Пакетируйте файловые операции

❌ **Не делайте:**
- Синхронные `http_get()` вызовы в событиях
- Создавайте огромные списки с `get_blocks()`
- Используйте бесконечные циклы (100k лимит)
- Вложенные тяжелые операции

## Общие Паттерны

### Паттерн 1: Логгер Чата
```python
on player_chat(player, message):
    file_mkdirs("sscripts/logs")
    file_append("sscripts/logs/chat.log", 
                player.name + ": " + message + "\n")
end
```

### Паттерн 2: Проверка Игрока
```python
func verify_player(name):
    if not online(name):
        return false
    end
    return has_tag(name, "verified")
end

on player_join(player):
    if not verify_player(player.name):
        tag_add(player.name, "verified")
    end
end
```

### Паттерн 3: Глобальное Состояние
```python
on player_join(player):
    count = num(get_global("total_joins"))
    set_global("total_joins", count + 1)
end
```

### Паттерн 4: Отложенное Действие
```python
on player_break_block(player, block):
    wait check_permission, player.name, block.id
end

func check_permission(name, block_id):
    sleep 1  // 1 tick задержка
    if not has_tag(name, "build"):
        log name + " cannot build " + block_id
    end
end
```

## Часто Используемые Рецепты

### Отправить сообщение игроку
```python
tellraw(player_name, "Some message")
```

### Получить всех онлайн игроков
```python
players_list = get_targets("@a")
for p in players_list:
    log p.name
end
```

### Сохранить/загрузить данные игрока
```python
// Сохранить
player_data = {"name": "Steve", "level": 10}
file_write_json("sscripts/data/steve.json", player_data)

// Загрузить
data = file_read_json("sscripts/data/steve.json")
log data.name
```

### HTTP запрос
```python
resp = http_get("https://api.github.com/users/octocat")
if resp.ok:
    user = resp.json
    log user.login  // "octocat"
end
```

### Применить эффект зелья
```python
effect_give("Steve", "minecraft:strength", 30, 1, false)
// Steve получает Strength II на 30 секунд (с частицами)
```

## Краткий Обзор Архитектуры

```
Лексор           Парсинг токенов
  ↓
Парсер           Построение AST
  ↓
Интерпретатор    Выполнение операторов
  ↓
EventManager     Регистрация/срабатывание событий
  ↓
ProcessScheduler  Tick-based выполнение
  ↓
Миксины          Хуки Minecraft событий
  ↓
GlobalVariables  Постоянное хранилище
```

## Следующие Шаги

1. **Начните**: Прочитайте [Getting Started](getting-started.md) гайд
2. **Учите Язык**: Изучите [Language Guide](language.md)
3. **Овладейте Функции**: Изучите [Functions & Async Mechanics](functions-mechanics.md)
4. **Команды Сервера**: Изучите [Commands Reference](commands.md)
5. **Овладейте События**: Изучите [Complete Events Reference](complete-events.md)
6. **Продвинутые Особенности**: Изучите [Advanced Features & Mechanics](advanced-features.md) (try-catch, селекторы, NBT, HTTP headers, пределы)
7. **API Справка**: Просмотрите [Complete Built-in Reference](complete-reference.md)
8. **Архитектура**: Поймите [Architecture & Advanced](architecture.md)

## Поддержка

- Проверьте логи сервера: `logs/latest.log`
- SScript логи: `logs/sscript/`
- GitHub Репозиторий: [l-log-l/sscript](https://github.com/l-log-l/sscript)
