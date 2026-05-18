---
title: Полный справочник событий
nav_order: 9
parent: РУ Главная
---

# Полный справочник событий

Эта страница документирует все события, которые можно обрабатывать в файлах `.event.ss`.

Файлы `.mixin.ss` работают отдельно: их хук-обработчики вызываются до действия и могут отменять чат, сон, ломание и другие действия через `return false`.

## Цикл жизни событий

1. **Запуск сервера** → событие `load`
2. **Подключение игрока** → событие `player_connect`
3. **Полное присоединение** → событие `player_join`
4. **Действия игрока** → `player_chat`, `block_place`, `block_break` и т.д.
5. **Смерть игрока** → события `player_death` + `player_dead`
6. **Сон игрока** → события `player_sleep`, `player_sleep_attempt`
7. **Возрождение** → событие `player_respawn`

## События сервера

### `load` → (нет параметров)

Срабатывает один раз при запуске сервера и загрузке всех скриптов.

**Применение:** Инициализация, загрузка данных.

```python
on load:
    log "Сервер стартует..."
    set_global("startup_time", sec(0))
    log "Инит завершён"
end
```

## События подключения игрока

### `player_connect(player)` → объект

Срабатывает когда инициализируется подключение игрока (до полного присоединения).

**Параметры:**
- `player`: Объект игрока

**Поля объекта игрока:**
- `name` — имя учётной записи
- `uuid` — UUID игрока
- `type` — тип сущности (всегда "minecraft:player")
- `x`, `y`, `z` — координаты позиции
- `pos` — форматированная позиция "X Y Z"
- `dimension` — ID измерения (например "minecraft:overworld")
- `health` — текущее здоровье (0-20)
- `gamemode` — режим игры ("survival", "creative" и т.д.)
- `tags` — список команд-тегов
- `nbt` — NBT данные строкой

Поля доступны как у объекта события, так и у результата `get_target()` / `get_targets()` для игроков и других сущностей.

```python
on player_connect(player):
    log "Подключение: " + player.name
end
```

### `player_join(player)` → объект

Срабатывает когда игрок полностью присоединился к серверу.

**Параметры:**
- `player`: Объект игрока (см. выше)

**Применение:** Приветственные сообщения, выдача прав, загрузка данных.

```python
on player_join(player):
    log "Добро пожаловать " + player.name
    
    # Дать тег если первый раз
    if not has_tag(player.name, "seen_before"):
        tag_add(player.name, "seen_before")
        tellraw(player.name, "Добро пожаловать на сервер!")
    end
end
```

### `player_respawn(player, alive)` → объект, булево

Срабатывает когда игрок возрождается после смерти.

**Параметры:**
- `player`: Объект игрока
- `alive`: `true` = возрождение, `false` = загрузка данных

```python
on player_respawn(player, alive):
    log player.name + " возродился"
    effect_give(player.name, "minecraft:resistance", 5, 0, false)
end
```

## События чата

### `player_chat(player, message)` → объект, строка

Срабатывает когда игрок отправляет сообщение в чат.

**Параметры:**
- `player`: Объект игрока
- `message`: Текст сообщения

**Применение:** Логирование чата, фильтры, парсинг команд.

```python
on player_chat(player, message):
    log "[" + player.name + "] " + message
    
    # Логируем в файл
    file_mkdirs("sscripts/logs")
    file_append("sscripts/logs/chat.log", player.name + ": " + message + "\n")
end
```

## События блоков

### `block_interact(player, block)` → объект, объект

Срабатывает когда игрок **успешно кликает** правой кнопкой по блоку.

**Включает:** Открытие дверей, нажатие кнопок, рычаги и т.д. (любое взаимодействие).

**Параметры:**
- `player`: Объект игрока
- `block`: Объект блока (см. ниже)

**Поля объекта блока:**
- `id` — ID блока (например "minecraft:diamond_ore")
- `x`, `y`, `z` — координаты блока
- `pos` — форматированная позиция "X Y Z"
- `dimension` — ID измерения

```python
on block_interact(player, block):
    log player.name + " взаимодействовал с " + block.id + " на " + block.pos
    
    # Пример: логируем все взаимодействия с дверями
    if contains(block.id, "door"):
        log "Взаимодействие с дверью на " + block.pos
    end
end
```

### `block_place(player, block)` → объект, объект

Срабатывает когда игрок **теоретически ставит** новый блок.

Это семантически отличается от `block_interact`:
- `block_interact` срабатывает для любого принятого правого клика (двери, рычаги и т.д.)
- `block_place` срабатывает ТОЛЬКО когда состояние блока изменилось (новый блок поставлен)

**Параметры:**
- `player`: Объект игрока
- `block`: Объект блока (вновь поставленный блок)

**Применение:** Логирование строительства, защита от грифа, отслеживание построек.

```python
on block_place(player, block):
    log player.name + " поставил " + block.id + " на " + block.pos
    
    # Запретить обсидиан
    if block.id == "minecraft:obsidian":
        if not has_tag(player.name, "builder"):
            run "setblock " + block.x + " " + block.y + " " + block.z + " air"
            tellraw(player.name, "Вы не можете ставить обсидиан здесь")
        end
    end
end
```

### `block_break(player, block)` → объект, объект

Срабатывает когда игрок **ламает** блок.

**Параметры:**
- `player`: Объект игрока
- `block`: Объект блока (перед уничтожением)

**Применение:** Ограничения добычи, отслеживание руд, защита от грифа.

```python
on block_break(player, block):
    log player.name + " ломал " + block.id + " на " + block.pos
    
    # Пример: логируем весь добытый алмаз
    if block.id == "minecraft:diamond_ore":
        set_global("total_diamonds_mined", num(get_global("total_diamonds_mined")) + 1)
    end
end
```

## События смерти игрока

### `player_death(player, location)` → объект, строка

Срабатывает когда игрок умирает (включая задержку возрождения).

**Параметры:**
- `player`: Объект игрока (в месте смерти)
- `location`: Форматированная строка позиции "X Y Z"

```python
on player_death(player, location):
    log player.name + " умер на " + location
end
```

### `player_dead(player, location)` → объект, строка

Альтернативное имя для события `player_death` (оба срабатывают вместе для совместимости).

```python
on player_dead(player, location):
    # Это тоже срабатывает при player_death
end
```

## События сна

### `player_sleep_attempt(player, bed_location)` → объект, строка

Срабатывает когда игрок пытается спать (до успеха/неудачи).

**Параметры:**
- `player`: Объект игрока
- `bed_location`: Форматированная позиция "X Y Z"

```python
on player_sleep_attempt(player, bed_location):
    log player.name + " пытается спать"
end
```

### `player_sleep(player, bed_location)` → объект, строка

Срабатывает когда игрок **успешно** ложится спать.

**Параметры:**
- `player`: Объект игрока
- `bed_location`: Форматированная позиция "X Y Z"

```python
on player_sleep(player, bed_location):
    log player.name + " лёг спать на " + bed_location
    
    # Выдаём достижение за сон
    tag_add(player.name, "has_slept")
end
```

## Паттерны обработки событий

### Паттерн 1: Условная логика

```python
on player_chat(player, message):
    if contains(message, "spam"):
        log "Заблокирован спам от " + player.name
    end
end
```

### Паттерн 2: Фильтрация по тегам

```python
on player_chat(player, message):
    if has_tag(player.name, "muted"):
        log "Молчаливый игрок пытался писать: " + player.name
    end
end
```

### Паттерн 3: Условная защита блоков

```python
on block_break(player, block):
    # Защищаем определённые блоки для не-админов
    if block.id == "minecraft:obsidian" and not has_tag(player.name, "admin"):
        run "setblock " + block.x + " " + block.y + " " + block.z + " " + block.id
        tellraw(player.name, "Вы не можете ломать обсидиан!")
    end
end
```

### Паттерн 4: Отложенное действие с `wait`

```python
on player_join(player):
    log player.name + " присоединился"
    wait send_welcome, player.name
end

func send_welcome(name):
    sleep 20  # 1 секунда задержки
    tellraw(name, "Добро пожаловать на сервер!")
end
```

### Паттерн 5: Отслеживание глобального состояния

```python
on player_join(player):
    set_global("last_joined", player.name)
end

on player_chat(player, message):
    if message == "who_was_last":
        last = get_global("last_joined")
        log "Последний игрок: " + str(last)
    end
end
```

## Порядок обработки событий

1. **Событие срабатывает** на сервере/миксине
2. **Все обработчики** регистрируются в очередь
3. **Каждый обработчик** получает свой процесс
4. **Обработчики выполняются** независимо (не обязательно по порядку)
5. **Ошибки в одном** не влияют на других

## Важные замечания

- **Обработчики асинхронные**: Используйте `wait` для отложенного выполнения
- **Порядок не гарантирован**: Несколько обработчиков могут выполняться в любом порядке
- **Потокобезопасно**: Миксин-хуки потокобезопасны
- **Объекты игроков неизменяемы**: Не изменяйте объект игрока (только чтение)
- **Позиции блоков**: Используйте `get_block()` для получения текущего состояния

## Тестирование событий

Создайте `test_events.event.ss`:

```python
on load:
    log "=== SScript Event System Ready ==="
end

on player_join(player):
    log "[JOIN] " + player.name + " (" + player.uuid + ")"
end

on player_chat(player, message):
    log "[CHAT] " + player.name + ": " + message
end

on block_break(player, block):
    log "[BREAK] " + player.name + " ломал " + block.id + " на " + block.pos
end

on block_place(player, block):
    log "[PLACE] " + player.name + " ставил " + block.id + " на " + block.pos
end

on player_death(player, location):
    log "[DEATH] " + player.name + " умер на " + location
end
```
