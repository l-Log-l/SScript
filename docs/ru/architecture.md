---
title: Архитектура & Продвинутые темы
nav_order: 10
parent: РУ Главная
---

# Архитектура SScript и продвинутые темы

## Архитектура двигателя

### Основные компоненты

```
┌─────────────────────────────────────────┐
│         Minecraft Server                │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │  SScript Mod (ModInitializer)     │  │
│  │  - Регистрирует команды           │  │
│  │  - Регистрирует события (Fabric)  │  │
│  │  - Инициализирует подсистемы      │  │
│  └──────────┬────────────────────────┘  │
│             │                           │
│  ┌──────────┴──────────┬──────────────┐ │
│  │                     │              │ │
│  ▼                     ▼              ▼ │
│ Lexer              Parser          Interpreter
│ (токены)           (AST)      (движок выполнения)
│                                        │
│  ┌──────────────────────────────────┐  │
│  │   EventManager                   │  │
│  │   - Регистрация событий          │  │
│  │   - Отправка событий             │  │
│  │   - Очередь обработчиков         │  │
│  └──────────────────────────────────┘  │
│             │                           │
│  ┌──────────┴──────────────────────┐   │
│  │   ProcessScheduler               │   │
│  │   - Выполнение по тикам          │   │
│  │   - Форкинг процессов            │   │
│  │   - Асинхронные обёртки          │   │
│  └──────────────────────────────────┘   │
│                                         │
│  ┌──────────┬──────────┬───────────┐   │
│  │          │          │           │   │
│  ▼          ▼          ▼           ▼   │
│ Миксины ГлобПеремен ЗагрузчикФайлов Команды
│                                         │
└─────────────────────────────────────────┘
```

### Структура проекта

```
src/main/java/ai/log/sscript/
├── SScript.java
│   └─ ModInitializer, регистрация событий
├── engine/
│   ├── lexer/
│   │   └─ Lexer.java (токенизация)
│   ├── parser/
│   │   ├── Parser.java (построение AST)
│   │   └─ ASTNode.java (определения узлов)
│   └── interpreter/
│       ├── Interpreter.java (выполнение, 79+ встроенных функций)
│       ├── Environment.java (области видимости переменных)
│       └─ ScriptValue.java (обёртка значений)
├── event/
│   ├── EventManager.java (регистрация и отправка)
│   └─ EventType.java (enum событий)
├── mixin/
│   ├── ServerPlayerEntityMixin.java (события игроков)
│   └─ ServerPlayerInteractionManagerMixin.java (события блоков)
├── runtime/
│   ├── ProcessScheduler.java (выполнение по тикам)
│   └─ ScriptProcess.java (отдельный процесс)
├── global/
│   └─ GlobalVariables.java (постоянное хранилище)
└── util/
    ├── ScriptLoader.java (загрузка файлов)
    └─ ErrorHelper.java (валидация)
```

## Модель выполнения

### Линейное выполнение (файлы `.ss`)

```
1. Лексер токенизирует исходный код
2. Парсер строит AST
3. Interpreter.execute(program):
   a. Первый проход: регистрирует все функции
   b. Второй проход: выполняет оператели верхнего уровня
4. Завершение
```

### Выполнение обработчиков событий (файлы `.event.ss`)

```
1. Загрузка скрипта:
   - Лексер → Парсер → AST
   - Регистрируем все функции
   - Извлекаем узлы OnEvent
   - Сохраняем в EventManager

2. Запуск события (когда оно происходит):
   - EventManager.fire("event_name", server, ...args)
   - Для каждого зарегистрированного обработчика:
     a. Создаём новую Environment с параметрами события
     b. Создаём новый Interpreter (делится функциями)
     c. Создаём ScriptProcess с телом обработчика
     d. Отправляем в ProcessScheduler

3. Выполнение по тикам (ProcessScheduler.tick()):
   - Для каждого активного процесса:
     a. Выполняем следующий оператор
     b. Обрабатываем управление потоком (break, continue, return)
     c. Обрабатываем разрывы страниц (итерации цикла)
     d. Помечаем процесс как завершённый
```

### Асинхронные паттерны

#### Паттерн A: Вызов `wait`

```
Событие срабатывает
    ↓
вызов wait(func_name, args)
    ↓
Порождает дочерний ScriptProcess
    ↓
Возвращает AwaitChildException
    ↓
Родительский процесс приостанавливается
    ↓
Следующий тик: дочерний процесс запускается
    ↓
Дочерний завершается → родительский возобновляется
```

#### Паттерн B: `sleep` в обработчике

```
on player_join(player):
    log "присоединился"      // Тик 1
    sleep 20                 // Запрос паузы на 20 тиков
    log "после ожидания"     // Тик 21
end
```

Внутреннее представление:
- `SleepNode` устанавливает счётчик паузы
- ProcessScheduler уменьшает его каждый тик
- Возобновляет, когда счётчик достигает 0

## Валидация скриптов

Перед загрузкой ErrorHelper проверяет:

```
✓ Синтаксис (парсер не отвергает)
✓ Обнаружение критических ошибок:
  - Неправильный управляющий поток
  - Неопределённые функции
  - Несовместимость типов
✓ Предупреждения (загружается в любом случае):
  - Неиспользуемые переменные
  - Недостижимый код
  - Затенённые имена
```

## Глобальные переменные

Постоянное хранилище: `sscripts/globals.json`

```
set_global("session_data", {"players": 5})
value = get_global("session_data")

// Автоматически сохраняется на диск
// Сохраняется после перезагрузки сервера
```

**Реализация:**
- Поддержана JSON файлом
- Ленивая загрузка при запуске
- Автосохранение при остановке сервера
- Доступно из любого скрипта

## Соображения производительности

### Безопасные лимиты

| Операция | Лимит | Причина |
|----------|-------|---------|
| Итерации цикла | 100,000 | Предотвращение заморозки |
| Тайм-аут HTTP | 60с макс | Лимит ресурсов |
| Файловые операции | Неограниченно | NIO быстро |
| Очередь процессов | 1,000 | Память/CPU |

### Советы оптимизации

✅ **Делайте:**
- Используйте `wait` для тяжёлых операций
- Кешируйте поиск блоков
- Запрашивайте игроков один раз за событие
- Используйте глобальные переменные для persistence

❌ **Избегайте:**
- Синхронные HTTP в обработчиках событий
- Большую запись файлов в циклах
- Частые вызовы `get_targets()`
- Вложенные циклы с get_blocks()

## Точки Mixin'а

SScript использует Fabric Mixins для внедрения хуков событий без патчинга Minecraft:

### ServerPlayerEntityMixin

Хуки в методы игрока:

```java
@Inject into onDeath()         → событие player_death
@Inject into copyFrom()        → событие player_respawn
@Inject into trySleep()        → события player_sleep_attempt, player_sleep
```

### ServerPlayerInteractionManagerMixin

Хуки в взаимодействие:

```java
@Inject into tryBreakBlock()   → событие block_break
@Inject into interactBlock()   → события block_interact, block_place
```

### События Fabric (в SScript.java)

Прямые слушатели событий Fabric:

```java
ServerPlayConnectionEvents.INIT       → событие player_connect
ServerPlayConnectionEvents.JOIN       → событие player_join
ServerMessageEvents.CHAT_MESSAGE      → событие player_chat
ServerLifecycleEvents.SERVER_STARTED  → инит системы
ServerLifecycleEvents.SERVER_STOPPED  → очистка
```

### Выполнение mixin-хуков (файлы `.mixin.ss`)

`*.mixin.ss` загружаются отдельно от `*.event.ss`. Их обработчики выполняются синхронно до действия и могут отменять его через `return false`.

```java
ServerMessageEvents.ALLOW_CHAT_MESSAGE → mixin-хук player_chat (можно отменить сообщение)
@Inject into trySleep()               → mixin-хук player_sleep_attempt (можно отменить сон)
@Inject into tryBreakBlock()          → mixin-хук block_break (можно отменить ломание)
@Inject into interactBlock()          → mixin-хук block_place (можно отменить взаимодействие/размещение)
```

## Обработка ошибок

### Try/Catch

```python
try:
    result = risky_operation()
catch err:
    log "Поймано: " + err
    result = default_value
end
```

Ловит:
- Ошибки API (отказы)
- Ссылки на null
- Ошибки конвертации типов
- Ошибки файлового ввода-вывода

### Логирование на сервер

Все ошибки логируются в:
- Консоль сервера (цветной)
- `logs/latest.log` (логи сервера)
- `logs/sscript/` (логи SScript)

## Тестирование и отладка

### Скрипт отладки

```python
# test_all.event.ss
on load:
    log "=== Начало тестов ==="
    file_mkdirs("sscripts/test_output")
end

on player_join(player):
    log "Игрок присоединился: " + player.name
    
    # Тест JSON
    obj = json_parse("{\"test\": true}")
    file_write_json("sscripts/test_output/player.json", {
        "name": player.name,
        "uuid": player.uuid,
        "health": player.health
    })
end

on player_chat(player, message):
    if message == "test":
        log "Health: " + str(player.health)
        log "Position: " + player.pos
    end
end
```

## Цепочка вызовов

```python
// Пример сложной цепочки

on player_join(player):
    // 1. Синхронно
    tag_add(player.name, "verified")
    
    // 2. Запрашиваем асинхронно
    wait check_ban, player.name
end

func check_ban(name):
    // 1. Проверяем файл
    banned_list = file_read_json("sscripts/bans.json")
    
    // 2. Если забанен
    if banned_list != null:
        for banned in banned_list:
            if banned == name:
                run "ban " + name + " Вы забаненны"
                return
            end
        end
    end
    
    // 3. Если нет - выдаём права
    tag_add(name, "allowed")
end
```
