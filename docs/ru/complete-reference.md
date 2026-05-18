---
title: Полный справочник встроенных функций
nav_order: 8
parent: РУ Главная
---

# Полный справочник встроенных функций

Эта страница содержит исчерпывающую документацию всех 80+ встроенных функций SScript.

> **📚 Справочник Объектов:** Для подробной документации о возвращаемых объектах (Block, Player, Response, Command), смотрите [Структуры Данных и Объекты](data-structures.md)

## Функции работы с игроками

### `players()` → строка
Возвращает список имён всех онлайн игроков через запятую.

```python
all_players = players()
log all_players  # Вывод: "Steve,Alex,Bob"
```

### `player_count()` → число
Возвращает количество онлайн игроков.

```python
count = player_count()
log "Игроков онлайн: " + str(count)
```

### `random_player()` → строка | null
Возвращает имя случайного игрока, или `null` если никого нет.

```python
lucky = random_player()
if lucky != null:
    log lucky + " повезло!"
end
```

### `online(name)` → булево
Проверяет, онлайн ли игрок.

```python
if online("Steve"):
    log "Steve здесь!"
end
```

### `get_target(selector)` → объект | null
Получает одну сущность по селектору или имени.

Возвращает объект с полями: `name`, `uuid`, `type`, `dimension`, `x`, `y`, `z`, `pos`, `tags`, `nbt`, `selector`.

```python
target = get_target("Steve")
if target != null:
    log target.name + " на позиции " + target.pos
end
```

### `get_targets(selector)` → список
Получает все сущности по селектору. Поддерживает:
- `"@a"` — все игроки
- `"@e"` — все сущности
- `"@e[tag=admin]"` — все сущности с тегом "admin"
- `"PlayerName"` — точное имя игрока

```python
all_targets = get_targets("@a")
for player in all_targets:
    log player.name
end

admins = get_targets("@a[tag=admin]")
log str(len(admins)) + " администраторов онлайн"
```

## Функции тегов

### `has_tag(target, tag)` → булево
Проверяет, есть ли у сущности тег.

```python
if has_tag("Steve", "vip"):
    log "VIP игрок!"
end
```

### `tag_add(target, tag)` → булево
Добавляет тег к сущности.

```python
tag_add("Steve", "admin")
log "Steve отмечен как admin"
```

### `tag_remove(target, tag)` → булево
Удаляет тег у сущности.

```python
tag_remove("Steve", "admin")
```

### `player_tags(target)` → строка
Возвращает все теги сущности через запятую.

```python
tags_str = player_tags("Steve")
log "Теги: " + tags_str  # Пример: "vip,admin,verified"
```

## Функции эффектов

### `effect_give(player_name, effect_id, duration_sec, amplifier, hide_particles)` → булево
Применяет эффект здоровья игроку.

- `effect_id`: например `"minecraft:speed"`, `"minecraft:strength"`, `"minecraft:blindness"`
- `duration_sec`: длительность в секундах (автоматически конвертится в тики × 20)
- `amplifier`: уровень эффекта (0 = I уровень, 1 = II и т.д.)
- `hide_particles`: `true` чтобы скрыть частицы

```python
# Дать Скорость II на 30 секунд
effect_give("Steve", "minecraft:speed", 30, 1, false)

# Применить Слепоту без частиц
effect_give("Steve", "minecraft:blindness", 10, 0, true)

# Популярные эффекты:
# - minecraft:speed (Скорость)
# - minecraft:slowness (Медлительность)
# - minecraft:strength (Сила)
# - minecraft:weakness (Слабость)
# - minecraft:regeneration (Регенерация)
# - minecraft:fire_resistance (Сопротивление огню)
# - minecraft:blindness (Слепота)
# - minecraft:levitation (Парящий)
```

### `effect_clear(player_name, effect_id)` → булево
Удаляет эффект у игрока.

```python
effect_clear("Steve", "minecraft:speed")
```

## Командные функции

### `exec(command)` → object
Выполняет команду сервера и возвращает статус выполнения. Возвращает объект с полями:
- `status`: `"success"` или `"error"`
- `result`: Код результата команды (положительный = успех)
- `command`: Выполненная команда
- `error` (если status is error): Сообщение об ошибке

```python
# Выполнить команду и проверить результат
result = exec("give Steve diamond 64")
if result.status == "success":
    log "Команда выполнена с кодом: " + str(result.result)
else:
    log "Ошибка: " + result.error
end

# Выполнить несколько команд
exec("say Привет мир")
exec("tp Steve 0 64 0")
```

### `tellraw(selector, message)` → null
Отправляет сообщение игроку(-ам).

```python
tellraw("Steve", "Это сообщение")
tellraw("@a", "Внимание всем!")
```

## Функции позиций и блоков

### `pos(x, y, z)` → объект
Создаёт объект позиции с полями: `x`, `y`, `z`, `pos` (форматированная строка).

```python
spawn_pos = pos(0, 64, 0)
log spawn_pos.pos  # "0 64 0"
```

### `get_block(pos[, dimension])` → объект | null
### `get_block(x, y, z[, dimension])` → объект | null
Получает информацию о блоке на позиции.

Возвращает объект: `id` (ID блока), `x`, `y`, `z`, `pos`, `dimension`, `state`, `nbt`.
Для блоков-сущностей `nbt` доступен как объект, поэтому можно обращаться к вложенным полям вроде `block.nbt.Items[0]`.
Объект `state` содержит свойства блока (например, `state.powered` для рычагов, `state.lit` для факелов).

```python
# По объекту позиции
p = pos(0, 64, 0)
block = get_block(p)
log block.id  # например "minecraft:dirt"

# По координатам
block = get_block(0, 64, 0)
log "Блок: " + block.id
```

### `get_blocks(pos1, pos2[, dimension])` → список
Возвращает все блоки в кубической области между двумя позициями.

```python
p1 = pos(0, 64, 0)
p2 = pos(10, 70, 10)
blocks = get_blocks(p1, p2)

for block in blocks:
    log block.id
end
```

### `has_block(pos1, pos2, block_id[, dimension])` → булево
Проверяет, присутствует ли определённый блок в области.

```python
p1 = pos(0, 0, 0)
p2 = pos(100, 100, 100)

if has_block(p1, p2, "minecraft:diamond_ore"):
    log "Найдена алмазная руда!"
end
```

## Математические функции

### `range(start, end)` → список
Генерирует список целых чисел от `start` до `end` включительно.

```python
nums = range(1, 5)
# Результат: [1, 2, 3, 4, 5]

for i in nums:
    log str(i)
end
```

### `random(min, max)` → число
Возвращает случайное число от `min` (включая) до `max` (исключая).

```python
lucky = random(1, 100)
log "Случайное число: " + str(lucky)
```

### `floor(n)` → число
Округляет вниз.

```python
x = floor(3.7)
log str(x)  # 3
```

### `ceil(n)` → число
Округляет вверх.

```python
x = ceil(3.2)
log str(x)  # 4
```

### `round(n)` → число
Округляет до ближайшего целого.

```python
x = round(3.5)
log str(x)  # 4
```

### `abs(n)` → число
Абсолютное значение.

```python
x = abs(-10)
log str(x)  # 10
```

### `min(a, b)` → число
Минимум из двух чисел.

```python
x = min(5, 3)
log str(x)  # 3
```

### `max(a, b)` → число
Максимум из двух чисел.

```python
x = max(5, 3)
log str(x)  # 5
```

### `sqrt(n)` → число
Квадратный корень.

```python
x = sqrt(16)
log str(x)  # 4
```

### `pow(base, exp)` → число
Возведение в степень: base^exp.

```python
x = pow(2, 3)
log str(x)  # 8
```

## Функции времени

### `sec(seconds)` → число
Конвертирует секунды в игровые тики (умножает на 20).

```python
ticks = sec(10)
log str(ticks)  # 200 тиков
```

### `int(n)` → число
Конвертация в целое число.

```python
x = int(3.9)
log str(x)  # 3
```

## Строковые функции

### `len(str)` → число
Длина строки.

```python
length = len("привет")
log str(length)  # 6
```

### `upper(str)` → строка
В ЗАГЛАВНЫЕ.

```python
text = upper("привет")
log text  # "ПРИВЕТ"
```

### `lower(str)` → строка
в строчные.

```python
text = lower("ПРИВЕТ")
log text  # "привет"
```

### `contains(str, substring)` → булево
Содержит ли строка подстроку.

```python
if contains("привет мир", "мир"):
    log "Найдено!"
end
```

### `starts_with(str, prefix)` → булево

```python
if starts_with("minecraft:dirt", "minecraft:"):
    log "Это блок майнкрафта"
end
```

### `ends_with(str, suffix)` → булево

```python
if ends_with("file.txt", ".txt"):
    log "Текстовый файл"
end
```

### `trim(str)` → строка
Убирает пробелы в начале и конце.

```python
text = trim("  привет  ")
log text  # "привет"
```

### `replace(str, old, new)` → строка
Заменяет все вхождения.

```python
text = replace("привет мир", "мир", "SScript")
log text  # "привет SScript"
```

### `substring(str, start[, end])` → строка
Извлекает подстроку.

```python
text = substring("привет", 1, 4)
log text  # "рив"

text = substring("привет", 2)
log text  # "ивет"
```

### `index_of(str, substring)` → число
Находит позицию подстроки (-1 если не найдена).

```python
pos = index_of("привет мир", "мир")
log str(pos)  # 7
```

### `split_get(str, delimiter, index)` → строка
Расщепляет строку и берёт элемент по индексу.

```python
part = split_get("a,b,c,d", ",", 2)
log part  # "c"
```

### `split_count(str, delimiter)` → число
Считает количество частей после расщепления.

```python
count = split_count("a,b,c,d", ",")
log str(count)  # 4
```

## Функции типизации

### `str(value)` → строка
Конвертирует любое значение в строку.

```python
s = str(42)
log s  # "42"

s = str(true)
log s  # "true"
```

### `num(value)` → число
Конвертирует в число.

```python
n = num("42")
log str(n)  # 42

n = num("3.14")
log str(n)  # 3.14

n = num("invalid")
log str(n)  # 0
```

### `bool(value)` → булево
Конвертирует в булево значение.

- `0`, `""`, `null`, `false` → `false`
- Всё остальное → `true`

```python
b = bool(1)
log str(b)  # true

b = bool(0)
log str(b)  # false
```

### `type(value)` → строка
Возвращает имя типа: `"number"`, `"string"`, `"boolean"`, `"object"`, `"list"`, `"null"`.

```python
log type(42)  # "number"
log type("привет")  # "string"
log type([1, 2, 3])  # "list"
```

## JSON функции

### `json_parse(text)` → объект | список | значение
Парсит JSON строку.

```python
data = json_parse("{\"name\": \"Steve\", \"level\": 10}")
log data.name  # "Steve"
log str(data.level)  # "10"
```

### `json_stringify(value)` → строка
Конвертирует значение в JSON строку.

```python
obj = {"name": "Alex", "items": 5}
json_str = json_stringify(obj)
log json_str  # {"name":"Alex","items":5}
```

## HTTP функции

Все HTTP функции **синхронные** (блокирующие). Используйте осторожно!

### `http_get(url[, headers[, timeout_sec]])` → объект
GET запрос.

Возвращает объект: `status` (код), `ok` (булево), `body` (ответ), `headers` (объект), `json` (распарсено если JSON).

```python
resp = http_get("https://api.github.com")
if resp.ok:
    log "Статус: " + str(resp.status)
    log "Тело: " + resp.body
end
```

### `http_post(url, body[, headers[, timeout_sec]])` → объект
POST запрос.

```python
data = {"key": "value"}
headers = {"Content-Type": "application/json"}
resp = http_post("https://example.com/api", data, headers, 10)
```

### `http_request(method, url[, body[, headers[, timeout_sec]]])` → объект
Универсальный HTTP запрос с любым методом (GET, POST, PUT, DELETE, PATCH).

```python
resp = http_request("DELETE", "https://example.com/item/123", null, {}, 5)
```

**Тайм-ауты:**
- Default: 10 секунд
- Minimum: 1 секунда
- Maximum: 60 секунд (автоматически обрезается)

## Файловые функции

Все пути относительны к директории запуска сервера. Абсолютные пути работают как есть.

### `file_exists(path)` → булево
Проверяет существование файла.

```python
if file_exists("sscripts/data/save.json"):
    log "Файл существует"
end
```

### `file_read(path)` → строка | null
Читает весь файл. Возвращает `null` если файла нет.

```python
content = file_read("sscripts/config.txt")
if content != null:
    log content
end
```

### `file_write(path, content)` → булево
Пишет в файл (создаёт или перезаписывает).

Автоматически создаёт родительские директории.

```python
file_write("sscripts/logs/output.txt", "Привет мир!")
```

### `file_append(path, content)` → булево
Добавляет в конец файла (создаёт если нужно).

```python
file_append("sscripts/logs/chat.log", timestamp + " - " + message + "\n")
```

### `file_delete(path)` → булево
Удаляет файл или пустую директорию. Возвращает `true` если успешно. **НЕ удаляет директории с содержимым.**

```python
if file_delete("sscripts/temp.txt"):
    log "Удалено"
end
```

### `file_delete_recursive(path)` → boolean
Удаляет файл или директорию (рекурсивно со всем содержимым). Возвращает `true` если успешно.

```python
# Удалить директорию со всем содержимым
if file_delete_recursive("sscripts/old_data/"):
    log "Директория и её содержимое удалены"
end
```

### `file_mkdirs(path)` → булево
Создаёт директорию (и все родительские).

```python
file_mkdirs("sscripts/data/nested/structure")
```

### `file_read_json(path)` → объект | список | null
Читает JSON файл. Возвращает `null` если не существует или ошибка.

```python
data = file_read_json("sscripts/data/config.json")
if data != null:
    log data.setting1
end
```

### `file_write_json(path, value)` → булево
Пишет значение как JSON в файл.

```python
obj = {"players": 5, "difficulty": 3, "pvp": true}
file_write_json("sscripts/data/world.json", obj)
```
### `file_rename(old_path, new_path)` → булево
Переименовывает или перемещает файл. Возвращает `true` если успешно, `false` если файл не существует.

```python
if file_rename("sscripts/old_name.txt", "sscripts/new_name.txt"):
    log "Файл переименован"
end
```

### `file_list(path)` → список
Возвращает список имен файлов и директорий в директории. Полезно в цикле для удаления всех файлов КРОМЕ одного.

```python
# Получить все файлы в директории
files = file_list("sscripts/data/")

# Удалить все файлы кроме одного
for file in files:
    if file != "keep_this.json":
        file_delete("sscripts/data/" + file)
    end
end
```
## Методы списков

Списки имеют встроенные методы.

```python
items = [1, 2, 3]

items.add(4)  # Добавить
items.remove(1)  # Удалить по индексу 
size = items.size()  # Получить размер
```

## Глобальные переменные

Постоянное хранилище ключ-значение, сохраняется при перезагрузке сервера.

### `get_global(key)` → значение | null
Получает глобальную переменную.

```python
value = get_global("players_seen")
```

### `set_global(key, value)` → null
Сохраняет глобальную переменную.

```python
set_global("last_player", "Steve")
```

## Логирование

### `log(message)` → null
Выводит сообщение в консоль сервера и лог SScript.

```python
log "Это сообщение"
log player.name + " присоединился!"
```

## Служебные команды

### `wait(func_name, args...)` → null
Вызывает функцию на следующем тике сервера (асинхронно).

Используется в обработчиках событий для отложенного выполнения.

```python
func heavy_task():
    log "Выполняем тяжёлую задачу..."
end

on load:
    wait heavy_task
end
```

### `sleep(ticks)` → null
Пауза на N тиков (1 тик = 50мс серверного времени).

```python
on player_join(player):
    log "Игрок присоединился"
    sleep 20  # 1 секунда
    log "Прошла 1 секунда"
end
```

### `try/catch` Блок
Обработка ошибок.

```python
try:
    bad_value = error_function()
catch err:
    log "Ошибка: " + err
end
```
