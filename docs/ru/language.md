---
title: Синтаксис языка
nav_order: 3
parent: РУ Главная
---

# Синтаксис языка

## Типы Файлов

SScript имеет два основных типа файлов с **принципиально разными моделями выполнения**:

### `.ss` (Runtime Скрипты) — Синхронное Выполнение

Эти скрипты выполняются **когда сервер запускается** (или при ручной загрузке командой).

**Характеристики:**
- Запускается один раз при запуске сервера (или при ручной загрузке)
- Код выполняется **последовательно и синхронно** (блокирующий)
- Может содержать определения функций, логику инициализации и bootstrap задачи
- Полезно для: конфигурация при запуске, инициализация базы данных, установка глобального состояния
- Расположение: `sscripts/`

**Пример:**

```python
# sscripts/startup.ss

log "Server initializing..."

// Создать директории
file_mkdirs("sscripts/data")
file_mkdirs("sscripts/logs")

// Инициализировать глобальные переменные
set_global("player_count", 0)
set_global("server_start_time", 0)

// Определить вспомогательные функции (доступны для обработчиков событий)
func welcome_player(name):
    tellraw(name, "Welcome to the server!")
end

log "Server ready!"
```

---

### `.event.ss` (Обработчики Событий) — Выполнение, Управляемое События

Эти скрипты содержат слушатели событий, которые срабатывают при определенных событиях в игре.

**Характеристики:**
- Загружаются при запуске сервера, но код выполняется только при срабатывании событий
- Должны заканчиваться расширением `.event.ss` чтобы быть распознаны движком
- Содержит блоки `on event_name(...)`
- Код внутри обработчиков событий выполняется **асинхронно** (неблокирующий, чтобы избежать задержек)
- Может вызывать функции определенные в `.ss` файлах
- Полезно для: реакция на действия игроков, логирование, автоматизация

**Пример:**

```python
# sscripts/handlers.event.ss

on player_join(player):
    log player.name + " joined"
    run("give " + player.name + " diamond 1")
end

on player_chat(player, message):
    file_mkdirs("sscripts/logs")
    file_append("sscripts/logs/chat.log", player.name + ": " + message + "\n")
end
```

---

## Переменные

```python
x = 10
name = "Steve"
ok = true
```

## Условия

```python
if x > 10:
    log "больше"
elif x == 10:
    log "равно"
else:
    log "меньше"
end
```

## Циклы

```python
for i in range(1, 5):
    log str(i)
end

while true:
    sleep 20
    break
end
```

## Управление потоком

- `break`
- `continue`
- `return`
- `try/catch`

```python
try:
    run("say hi")
catch err:
    log "ошибка: " + err
end
```

## Функции

### Определение

Определяйте функции на **верхнем уровне** (не внутри блоков событий):

```python
func greet(name):
    return "Hello, " + name
end

func add(a, b):
    return a + b
end

func log_info():
    log "This is info"
end
```

Вы можете также использовать `def` вместо `func` (они идентичны):

```python
def multiply(x, y):
    return x * y
end
```

### Базовый Вызов

В `.ss` runtime скриптах вызовы **синхронны и блокирующи**:

```python
result = greet("Steve")
log result

sum_val = add(5, 3)
log sum_val
```

### Асинхронный Вызов в События

В `.event.ss` обработчиках поведение вызовов функций отличается в зависимости от контекста:

- **`test()`** — Ставит функцию в очередь асинхронно, обработчик **продолжает сразу**
- **`wait test()`** — Ставит функцию в очередь асинхронно, обработчик **ждет завершения**
- **`var = test()`** — Ставит функцию в очередь асинхронно, **автоматически действует как `wait`** (так как обработчик нуждается в результате)

**Пример:**

```python
func fetch_data(player):
    sleep 1  // Имитация задержки
    return "data"
end

on player_join(player):
    log "A"
    fetch_data(player)     // Асинхронно, продолжает сразу
    log "B"
    
    result = fetch_data(player)  // Автоматический wait (нужен результат), приостанавливает
    log "C - " + result    // Печатается после fetch завершается
end
```

**Вывод:** A, B, C (C печатается последнее после fetch завершится)

### ⚠️ Важно

Для подробных механик вызова функций, асинхронного поведения, семантики wait/присваивания и паттернов производительности, **смотрите [Functions & Async Mechanics](functions-mechanics.md)**.

## Структуры данных

### Список

```python
items = ["a", "b"]
items.add("c")
items.remove(1)
```

### Объект

```python
obj = {"name": "Alex", "score": 12}
log obj.name
obj.score += 5
```
