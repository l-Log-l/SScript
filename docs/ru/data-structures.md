---
title: Структуры Данных и Объекты
nav_order: 7
parent: РУ Главная
---

# Структуры Данных и Объекты

Это руководство описывает все структуры объектов, возвращаемые функциями SScript.

## Объект Block (Блок)

Возвращается: `get_block()`, `get_blocks()`, параметры событий

### Поля
| Поле | Тип | Описание |
|------|-----|---------|
| `id` | string | ID блока, например `"minecraft:dirt"`, `"minecraft:chest"` |
| `x` | number | Координата X |
| `y` | number | Координата Y |
| `z` | number | Координата Z |
| `pos` | string | Форматированная позиция: `"x y z"` |
| `dimension` | string | Измерение, например `"minecraft:overworld"` |
| `state` | object | Свойства BlockState (см. State Object ниже) |
| `nbt` | object | Данные блочного объекта (если присутствует, иначе пустой объект) |

### State Object (Объект Состояния)

Содержит свойства текущего состояния блока. Свойства зависят от типа блока.

**Примеры:**

```python
# Получить блок рычага
block = get_block(10, 64, 20)

# Проверить, включен ли (для рычагов, повторителей и т.д.)
if block.state.powered == true:
    log "Блок включен"
end

# Проверить, горит ли (для факелов, костров и т.д.)
if block.state.lit == true:
    log "Блок светит"
end

# Проверить направление (для лестниц, поршней и т.д.)
log "Направление: " + str(block.state.facing)  # например "north", "south"

# Проверить половину (для кроватей, дверей и т.д.)
log "Половина: " + str(block.state.half)  # например "upper", "lower"
```

### NBT Object (Объект NBT)

Для блочных объектов (сундук, печь, воронка и т.д.), `nbt` содержит сохраненные данные:

```python
# Получить блок сундука
block = get_block(100, 64, 200)

# Доступ к предметам в инвентаре
if block.nbt != null:
    log "Количество предметов: " + str(block.nbt.Items.size())
    
    # Первый предмет в сундуке
    if block.nbt.Items.size() > 0:
        first_item = block.nbt.Items[0]
        log "Первый предмет: " + first_item.id  # например "minecraft:diamond"
        log "Количество: " + str(first_item.Count)
    end
end

# Полный пример - проверить топливо в печи
furnace = get_block(50, 64, 50)
if furnace.nbt != null:
    fuel_time = furnace.nbt.BurnTime
    log "Время горения печи: " + str(fuel_time)
end
```

### Полный Пример

```python
# Исследовать любой блок
func inspect_block(x, y, z):
    block = get_block(x, y, z)
    log "Блок: " + block.id
    log "Позиция: " + block.pos
    log "Измерение: " + block.dimension
    
    # Логировать свойства состояния
    if block.state != null:
        log "Состояние: powered=" + str(block.state.powered) + 
            " lit=" + str(block.state.lit)
    end
    
    # Логировать NBT если блочный объект
    if block.nbt != null:
        log "Есть данные NBT"
    end
end
```

---

## Объект Player / Target (Игрок)

Возвращается: `get_target()`, `get_targets()`, параметры событий (`player_join`, `player_chat` и т.д.)

### Поля
| Поле | Тип | Описание |
|------|-----|---------|
| `name` | string | Имя игрока, например `"Steve"` |
| `type` | string | Тип сущности, обычно `"player"` |
| `dimension` | string | Текущее измерение, например `"minecraft:overworld"` |
| `x` | number | Координата X |
| `y` | number | Координата Y |
| `z` | number | Координата Z |
| `pos` | string | Форматированная позиция: `"x y z"` |
| `tags` | string | Разделённые запятой теги, например `"admin,vip"` |
| `nbt` | object | NBT данные игрока (инвентарь, эффекты и т.д.) |
| `selector` | string | Селектор, использованный для получения этого игрока |

### Использование Объектов Игроков

```python
# Получить одного игрока
player = get_target("Steve")
if player != null:
    log "Найден: " + player.name
    log "В: " + player.pos
    log "Измерение: " + player.dimension
    
    # Проверить теги
    log "Теги: " + player.tags
    
    # Проверить инвентарь
    if player.nbt != null and player.nbt.Inventory.size() > 0:
        log "Есть предметы"
    end
end

# Получить всех онлайн игроков
all_players = get_targets("@a")
for p in all_players:
    log p.name + " в " + p.pos
end

# Получить всех игроков с тегом
admins = get_targets("@a[tag=admin]")
log "Администраторов онлайн: " + str(admins.size())

# Итерировать и проверить теги
for player in all_players:
    if player.tags.contains("vip"):
        log player.name + " - VIP"
    end
end
```

### Доступ к Координатам

```python
player = get_target("Alex")
if player != null:
    log "X: " + str(player.x)
    log "Y: " + str(player.y)
    log "Z: " + str(player.z)
    
    # Вычислить расстояние от начала координат
    dist = sqrt(player.x * player.x + player.y * player.y + player.z * player.z)
    log "Расстояние от 0,0,0: " + str(dist)
end
```

### NBT Данные (Продвинутый Уровень)

NBT игрока содержит инвентарь, эффекты и другие данные:

```python
player = get_target("Steve")

# Проверить инвентарь
if player.nbt != null:
    inventory = player.nbt.Inventory
    log "Предметов в инвентаре: " + str(inventory.size())
    
    # Проверить держимый предмет
    if inventory.size() > 0:
        held_item = inventory[0]
        log "Держит: " + held_item.id
    end
    
    # Проверить активные эффекты
    if player.nbt.ActiveEffects.size() > 0:
        log "Есть эффекты"
    end
end
```

---

## Объект HTTP Response (Ответ HTTP)

Возвращается: `http_get()`, `http_post()`, `http_request()`

### Поля
| Поле | Тип | Описание |
|------|-----|---------|
| `status` | number | HTTP код ответа, например `200`, `404`, `500` |
| `ok` | boolean | `true` если статус 200-299 |
| `body` | string | Тело ответа как чистый текст |
| `headers` | object | Заголовки ответа (ключи - имена заголовков) |
| `json` | object/list | Распарсенный JSON (если JSON валиден) |

### Базовое Использование

```python
# Простой GET запрос
resp = http_get("https://api.example.com/data")

if resp.ok:
    log "Запрос успешен"
    log "Статус: " + str(resp.status)
    log "Тело: " + resp.body
else:
    log "Запрос не удался, статус: " + str(resp.status)
end
```

### JSON Ответ

```python
# Запрос, возвращающий JSON
resp = http_get("https://api.github.com/users/octocat")

if resp.ok:
    user = resp.json
    log "Логин: " + user.login  # "octocat"
    log "Репозиториев: " + str(user.public_repos)
else:
    log "Ошибка: " + str(resp.status)
end
```

### Пользовательские Заголовки

```python
headers = {
    "Authorization": "Bearer YOUR_TOKEN",
    "Content-Type": "application/json"
}

resp = http_get("https://api.example.com/user", headers, 10)

if resp.ok:
    data = resp.json
    log "Данные получены"
end
```

### POST Запрос

```python
# Создать полезную нагрузку
payload = {
    "name": "Steve",
    "age": 30
}

headers = {"Content-Type": "application/json"}

resp = http_post("https://api.example.com/users", 
                 json_stringify(payload), 
                 headers, 
                 10)

if resp.ok:
    log "Пользователь создан"
else:
    log "Ошибка: " + str(resp.status)
end
```

### Доступ к Заголовкам Ответа

```python
resp = http_get("https://api.example.com/data")

if resp.ok:
    # Доступ к заголовкам ответа
    content_type = resp.headers.get("content-type")
    log "Content type: " + str(content_type)
    
    # Проверить пользовательские заголовки
    api_version = resp.headers.get("x-api-version")
    if api_version != null:
        log "Версия API: " + api_version
    end
end
```

### Обработка Ошибок

```python
func fetch_data(url):
    try:
        resp = http_get(url, {}, 5)
        
        if not resp.ok:
            log "HTTP ошибка: " + str(resp.status)
            return null
        end
        
        if resp.json == null:
            log "Неверный JSON ответ"
            return null
        end
        
        return resp.json
    catch err:
        log "Запрос не удался: " + err
        return null
    end
end

# Использование
data = fetch_data("https://api.example.com/items")
if data != null:
    log "Получено " + str(data.items.size()) + " предметов"
end
```

---

## Объект Command Result (Результат Команды)

Возвращается: функция `exec()`

### Поля
| Поле | Тип | Описание |
|------|-----|---------|
| `status` | string | `"success"` или `"error"` |
| `result` | number | **Код выхода** команды (>0 = успех, 0 = ошибка). Это НЕ текстовый вывод команды. |
| `command` | string | Выполненная команда || `output` | string | Обратная связь/вывод команды (пока пусто, зарезервировано для будущего) || `error` | string | Сообщение об ошибке (только если status `"error"`) |

### Понимание Кодов Выхода

Поле `result` содержит **код выхода**, возвращённый командой Minecraft:
- Большинство успешных команд возвращают `1`
- Неудачные команды возвращают `0`
- Некоторые команды возвращают специфичные коды

**Важно:** Коды выхода — это НЕ текстовый вывод команды. Если вам нужны данные из команды типа `data get block`, вы должны:
1. Использовать NBT функции напрямую: `get_block(x,y,z).nbt` 
2. Или парсить вывод из чата (продвинуто)

```python
# Выполнить команду
result = exec("give Steve diamond 64")

if result.status == "success":
    log "Команда выполнена успешно"
    log "Код выхода: " + str(result.result)
else:
    log "Команда не удалась"
    log "Ошибка: " + result.error
end
```

### Использование Результатов

```python
# Проверить, была ли команда успешна
result = exec("tp Steve 0 64 0")

if result.result > 0:
    log "Телепортирован успешно"
else:
    log "Телепортация не удалась"
end

# Всегда сохранять результаты
result = exec("say Привет, игроки!")
log "Выполнено: " + result.command
log "Статус: " + result.status
```

### Обработка Ошибок

```python
func safe_execute(command):
    result = exec(command)
    
    if result.status == "error":
        log "[ОШИБКА] Команда не удалась: " + result.error
        log "[ИНФО] Команда была: " + result.command
        return false
    end
    
    log "[OK] Команда выполнена"
    return true
end

# Использование
safe_execute("give @a diamond 10")
safe_execute("invalid_command")
```

### Цепь Команд

```python
func broadcast(message):
    result = exec("say " + message)
    return result.status == "success"
end

# Несколько команд
for i in range(1, 3):
    broadcast("Сообщение сервера " + str(i))
end
```

---

## Объект Position (Позиция)

Возвращается: функция `pos()`, события блоков/игроков

### Поля
| Поле | Тип | Описание |
|------|-----|---------|
| `x` | number | Координата X |
| `y` | number | Координата Y |
| `z` | number | Координата Z |
| `pos` | string | Форматированная строка `"x y z"` |

### Использование

```python
# Создать позицию
spawn = pos(0, 64, 0)
log spawn.pos  # "0 64 0"

# Использовать в командах
exec("tp Steve " + spawn.pos)

# Извлечь координаты
log spawn.x  # 0
log spawn.y  # 64
log spawn.z  # 0
```

---

## Паттерны Доступа к Объектам

### Доступ к Вложенным Полям

```python
# Доступ по индексу
items = block.nbt.Items
first = items[0]
log first.id

# Доступ к объекту
data = resp.json
log data.user.name
log data.settings["difficulty"]

# Безопасный доступ (проверить null сначала)
if player.nbt != null and player.nbt.Inventory.size() > 0:
    item = player.nbt.Inventory[0]
    log item.id
end
```

### Проверка Типов

```python
# Проверить, существует ли объект
if block.nbt != null:
    log "Блок имеет NBT"
end

# Проверить, пуст ли список/массив
if player.tags.size() > 0:
    log "У игрока есть теги"
end

# Проверить существование поля
if resp.json.error != null:
    log "В ответе есть ошибка"
end
```

### Паттерны Итерации

```python
# Итерировать по массиву
blocks = get_blocks(p1, p2)
for block in blocks:
    if block.id == "minecraft:diamond_ore":
        log "Найдена алмазная руда в " + block.pos
    end
end

# Итерировать по игрокам
players = get_targets("@a")
for player in players:
    log player.name
end
```
