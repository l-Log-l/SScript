---
title: Функции и Асинхронные Механики
nav_order: 4
parent: РУ Главная
---

# Функции и Асинхронные Механики

SScript имеет мощные возможности функций с множественными способами вызова, которые влияют на поток выполнения. Это ядро понимания того, как выполняется логика скрипта.

## Определение Функции

### Ключевое слово `func`

```python
func greet(name):
    return "Hello, " + name
end

func add(a, b):
    return a + b
end

func update_player_score(player, points):
    current = num(get_global(player))
    set_global(player, current + points)
end
```

### Ключевое слово `def` (Альтернатива)

`def` это псевдоним для `func` — они идентичны:

```python
def greet(name):
    return "Hello, " + name
end

def calculate(x, y):
    return x * y
end
```

**Рекомендация:** Используйте `func` для единообразия, но `def` работает если предпочитаете.

## Три Способа Вызова Функций

### 1. **Синхронный Вызов** — `test()` в runtime контексте

В `.ss` runtime скриптах вызовы функций блокируют до завершения:

```python
# runtime.ss

func slow_calculation():
    result = 0
    for i in range(1, 10000):
        result = result + i
    end
    return result
end

log "Starting..."
value = slow_calculation()
log "Result: " + str(value)
log "Done"
```

**Порядок выполнения:**
1. Напечать "Starting..."
2. Ждать завершения `slow_calculation()`
3. Напечать "Result: ..."
4. Напечать "Done"

**Используется для:** Скриптов инициализации, где нужно последовательное, блокирующее выполнение.

---

### 2. **Асинхронный Вызов** — `test()` в обработчике события (без `wait`, без присваивания)

Когда вызываете функцию внутри обработчика события БЕЗ `wait` и БЕЗ присваивания переменной, она ставится в очередь асинхронно:

```python
# handler.event.ss

func fetch_api_data(player_name):
    resp = http_get("https://api.example.com/players/" + player_name)
    if resp.ok:
        data = resp.json
        log player_name + " has score: " + str(data.score)
    end
end

on player_join(player):
    log "Player joined: " + player.name  // Выполняется сразу
    fetch_api_data(player.name)          // Ставится в очередь асинхронно!
    log "Check queued"                   // Выполняется сразу (не ждет)
end
```

**Порядок выполнения:**
1. Напечать "Player joined: Steve"
2. Напечать "Check queued"
3. **Позже (на следующем tick или когда планировщик запустит):** `fetch_api_data()` выполняется

**Почему?** Обработчики событий не должны блокировать сервер. Тяжелый I/O (HTTP, файлы) выполняется в фоне.

---

### 3. **Wait Вызов** — `wait test()` (явный асинхронный с блокировкой)

Используйте `wait` когда явно хотите поставить функцию в очередь И приостановить обработчик события до завершения:

```python
# handler.event.ss

func verify_player(player_name):
    sleep 2  // Имитация задержки
    if has_tag(player_name, "verified"):
        return true
    else
        return false
    end
end

on player_join(player):
    log "Checking verification..."
    wait verify_player(player.name)  // Блокировать обработчик до завершения
    log "Verification complete"      // Выполняется после завершения wait
end
```

**Порядок выполнения:**
1. Напечать "Checking verification..."
2. **Поставить в очередь verify_player() на следующий tick**
3. **Приостановить обработчик события**
4. В итоге: "Verification complete" печатается когда wait разрешится

**Ключевое отличие от простого вызова:** `wait` **приостанавливает** обработчик события; простой вызов нет.

---

## Присваивание Вызывает Wait Поведение

### Паттерн: `var = test()`

Когда присваиваете возвращаемое значение функции переменной, это автоматически ведет себя как `wait`:

```python
on player_join(player):
    log "A"
    result = fetch_player_level(player.name)  // Это действует как wait!
    log "B - got result: " + str(result)
end

func fetch_player_level(name):
    sleep 1
    return 10
end
```

**Выполнение:**
1. Напечать "A"
2. **Поставить функцию в очередь, приостановить обработчик**
3. В итоге: напечать "B - got result: 10"

**Почему?** Обработчик нуждается в возвращаемом значении для присваивания `result`, поэтому он **должен ждать** завершения `fetch_player_level()`.

---

## Таблица Сравнения

| Сценарий | Синтаксис | Блокирует Обработчик? | Как Используется Результат | Когда Завершено |
|----------|-----------|----------------------|------------------------------|-----------------|
| Runtime (sync) | `result = test()` | ✅ Yes (runtime блокирует) | Сохранено в переменную | Сразу (тот же tick) |
| Event (async, без var) | `test()` | ❌ No (async) | Игнорируется | Фоновый tick |
| Event (явный wait) | `wait test()` | ✅ Yes (обработчик приостановлен) | Игнорируется | Следующий tick и далее |
| Event (assign result) | `var = test()` | ✅ Yes (обработчик приостановлен) | Сохранено в переменную | Обработчик возобновляется после завершена |

---

## Практические Примеры

### Пример 1: Проверка Прав при Присоединении

```python
func check_permissions(player_name):
    sleep 1  // Имитация запроса БД
    if has_tag(player_name, "admin"):
        return true
    end
    return false
end

on player_join(player):
    log "Player: " + player.name
    
    is_admin = check_permissions(player.name)  // Ждать результат
    
    if is_admin:
        log player.name + " is admin"
    else
        log player.name + " is regular"
    end
end
```

**Поток:** Обработчик приостановлен на `is_admin =`, возобновляется когда `check_permissions()` возвращает результат.

---

### Пример 2: Фоновое Логирование (Fire & Forget)

```python
func log_to_file(message):
    file_mkdirs("sscripts/logs")
    file_append("sscripts/logs/events.log", message + "\n")
end

on player_chat(player, message):
    log_to_file(player.name + ": " + message)  // Без wait, без присваивания
    // Обработчик продолжает сразу, логирование происходит в фоне
end
```

**Поток:** Обработчик не ждет, функция ставится в очередь.

---

### Пример 3: Цепь Нескольких Wait

```python
func fetch_name():
    sleep 1
    return "fetched_name"
end

func fetch_level():
    sleep 1
    return 42
end

on player_join(player):
    log "Starting fetch..."
    name = fetch_name()      // Wait 1
    level = fetch_level()    // Wait 2
    log "Name: " + name + ", Level: " + str(level)
end
```

**Поток:**
1. Выполнить `fetch_name()`, приостановить
2. Когда завершено, выполнить `fetch_level()`, приостановить
3. Когда завершено, напечать результат

---

## Советы по Производительности

### ✅ Используйте Fire-and-Forget для Побочных Эффектов

```python
on player_chat(player, message):
    log_to_file(message)  // Без wait, без переменной
    // Обработчик продолжает
end
```

### ✅ Используйте `wait` Только Когда Нужен Результат

```python
on player_join(player):
    level = fetch_player_level(player.name)  // Присваивание автоматически ждет
    if level > 10:
        tag_add(player.name, "veteran")
    end
end
```

### ❌ Не Блокируйте События Без Причины

```python
// ПЛОХО: Блокирующее событие без причины
on player_chat(player, message):
    wait heavy_computation()  // Результат не используется!
end

// ХОРОШО: Просто вызовите без wait
on player_chat(player, message):
    heavy_computation()  // Fire and forget
end
```

### ❌ Не Вкладывайте Тяжелые Операции

```python
// ПЛОХО: Цепь множественных wait
on player_join(player):
    wait fetch_data_1(player)
    wait fetch_data_2(player)
    wait fetch_data_3(player)
    wait fetch_data_4(player)
    // Приостанавливает обработчик на 4 tick
end

// ХОРОШО: Если независимы, вызовите асинхронно
on player_join(player):
    fetch_data_1(player)  // Все ставятся в очередь сразу
    fetch_data_2(player)
    fetch_data_3(player)
    fetch_data_4(player)
end
```

---

## Под Капотом

### Как Работает Асинхронность

1. **Обнаружен Вызов Функции** → `wait test()` или `test()` или `var = test()`
2. **Процесс Создан** → Функция ставится в очередь к ProcessScheduler
3. **Состояние Обработчика**:
   - `wait` или `=` → Обработчик приостановлен, ждет завершения процесса
   - Без `wait`, без `=` → Обработчик продолжает сразу
4. **Следующий Tick** → Планировщик выполняет поставленную функцию
5. **Возвращаемое Значение** → Если обработчик был приостановлен, возобновляется с результатом

### ProcessScheduler

- Выполняет одну функцию за один тик сервера (50ms игрового времени)
- Множественные функции могут быть поставлены в очередь независимо
- Обработчик события может приостановиться и возобновиться

---

## Миграционный Гайд

### Из Других Языков

Если вы привыкли к JavaScript или Python:

| Паттерн | SScript | Примечания |
|---------|---------|-----------|
| `await` | `wait` или `=` | Используйте когда нужен результат |
| Promises | Implicit | Каждая функция возвращает "promise" (процесс) |
| Callbacks | Не нужны | Используйте `wait` вместо |
| Fire-and-forget | Простой вызов | `test()` без `wait` или `=` |

---

## Отладка

### Как Отладить Вызовы Функций

```python
func test_function():
    log "Inside test_function"
    sleep 1
    return "done"
end

on player_join(player):
    log "[A] Before call"
    result = test_function()
    log "[B] After call, result: " + result
end
```

**Вывод:**
```
[A] Before call
Inside test_function
[B] After call, result: done
```

Тот факт, что `[B]` печатается ПОСЛЕ `Inside test_function`, подтверждает что обработчик ждал.

---

## Смотрите Также

- [Language Guide](language.md) — Переменные, управление потоком
- [Complete Events Reference](complete-events.md) — Все 13 событий
- [Architecture & Advanced](architecture.md) — Детали ProcessScheduler
