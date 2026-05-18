---
title: Language Guide
nav_order: 3
parent: EN Home
---

# Language Guide

## File Types

SScript has two main file types with **fundamentally different execution models**:

### `.ss` (Runtime Scripts) — Synchronous Execution

These scripts execute **when the server starts** (or when manually loaded with a command).

**Characteristics:**
- Runs once at server startup (or on manual load)
- Code executes **sequentially and synchronously** (blocking)
- Can contain function definitions, initialization logic, and bootstrap tasks
- Useful for: startup configuration, database initialization, global state setup
- Located in `sscripts/`

**Example:**

```python
# sscripts/startup.ss

log "Server initializing..."

// Create directories
file_mkdirs("sscripts/data")
file_mkdirs("sscripts/logs")

// Initialize globals
set_global("player_count", 0)
set_global("server_start_time", 0)

// Define helper functions (available for events to use)
func welcome_player(name):
    tellraw(name, "Welcome to the server!")
end

log "Server ready!"
```

**Execution:** Runs completely when server boots. All functions defined here are available to event handlers.

---

### `.event.ss` (Event Handlers) — Event-Driven Execution

These scripts contain event listeners that fire when specific in-game events occur.

**Characteristics:**
- Loaded at server startup, but code only runs when events fire
- Must end with `.event.ss` extension to be recognized by the engine
- Contains `on event_name(...)` blocks
- Code inside event handlers executes **asynchronously** (non-blocking to avoid lag)
- Can call functions defined in `.ss` files
- Useful for: reacting to player actions, logging, automation

**Example:**

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

on block_break(player, block):
    log player.name + " broke " + block.id + " at " + pos(block)
end
```

**Execution:** These events fire continuously during server operation in response to player actions.

---

### Special File: `load.ss`

- **Always runs first** when SScript bootstrap loads
- Runs once before regular scripts
- Good for system-level initialization that must happen before everything else
- Rarely needed for typical usage

## Variables

```python
x = 10
name = "Steve"
ok = true
pi = 3.14
```

### Type Coercion
- `str(value)` — convert to string
- `int(value)` — convert to integer
- `float(value)` — convert to floating point

## Functions

### Definition

Define functions at **top level** (not inside event blocks):

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

You can also use `def` instead of `func` (they are identical):

```python
def multiply(x, y):
    return x * y
end
```

### Basic Calling

In `.ss` runtime scripts, calls are **synchronous and blocking**:

```python
result = greet("Steve")
log result

sum_val = add(5, 3)
log sum_val
```

### Async Calling in Events

In `.event.ss` handlers, function calls behave differently based on context:

- **`test()`** — Queues function asynchronously, handler **continues immediately**
- **`wait test()`** — Queues function asynchronously, handler **waits for completion**
- **`var = test()`** — Queues function asynchronously, **automatically acts like `wait`** (since handler needs the result)

**Example:**

```python
func fetch_data(player):
    sleep 1  // Simulate delay
    return "data"
end

on player_join(player):
    log "A"
    fetch_data(player)     // Async, continues immediately
    log "B"
    
    result = fetch_data(player)  // Auto-wait (needs result), suspends
    log "C - " + result    // Prints after fetch completes
end
```

**Output:** A, B, C (C prints last after fetch completes)

### ⚠️ Important

For detailed mechanics of function calling, async behavior, wait/assignment semantics, and performance patterns, **see [Functions & Async Mechanics](functions-mechanics.md)**.

### Return Values

Functions can return any value:

```python
func get_player_level(player):
    return player.level
end

func is_admin(name):
    return name == "Steve" or name == "Alex"
end

func get_nothing():
    return null
end
```

## Conditions

```python
if x > 10:
    log "big"
elif x == 10:
    log "equal"
else:
    log "small"
end
```

## Loops

### For Loop

```python
for i in range(1, 5):
    log str(i)
end
```

### While Loop

```python
counter = 0
while counter < 10:
    log str(counter)
    counter = counter + 1
end
```

### Loop Control

```python
for i in range(100):
    if i == 5:
        break
    if i == 2:
        continue
    log str(i)
end
```

## Flow Control

### Break

Exit a loop immediately:

```python
for i in range(100):
    if i > 10:
        break
    end
end
```

### Continue

Skip to next iteration:

```python
for i in range(10):
    if i == 5:
        continue
    log str(i)
end
```

### Return

Exit function early:

```python
func check_admin(name):
    if name != "Steve":
        return false
    end
    log "Admin logged in"
    return true
end
```

### Try/Catch

Handle errors gracefully:

```python
try:
    result = http_get("https://api.example.com", {}, 5)
    log result.body
catch err:
    log "Error: " + err
end
```

## Data Structures

### List (Array)

```python
items = ["a", "b", "c"]
items.add("d")
items.remove(1)
log items.size()

items[0]  # access by index
```

### Object (Dictionary)

```python
player_stats = {
    "name": "Steve",
    "level": 10,
    "online": true
}

log player_stats.name
player_stats.score = 100
log player_stats["score"]  # alternative access
```

### Nested Structures

```python
world_data = {
    "players": ["Steve", "Alex", "Bob"],
    "settings": {
        "difficulty": 3,
        "auto_save": true
    }
}

log world_data.settings.difficulty
world_data.players.add("Charlie")
```

## String Operations

```python
text = "Hello World"
log text + " 123"              # concatenation
log text.length()              # get length
log text.substring(0, 5)       # substring
log text.lower()               # lowercase
log text.upper()               # uppercase
log text.contains("World")     # check contains
```

## Comments

```python
# This is a single-line comment

# Multi-line comments:
# Line 1
# Line 2
```

## Operators

### Arithmetic
- `+` addition
- `-` subtraction
- `*` multiplication
- `/` division
- `%` modulo

### Comparison
- `==` equal
- `!=` not equal
- `>` greater than
- `<` less than
- `>=` greater or equal
- `<=` less or equal

### Logical
- `and` logical AND
- `or` logical OR
- `not` logical NOT

```python
if age >= 18 and status == "active":
    log "eligible"
end

if name == "Steve" or name == "Alex":
    log "known player"
end
```

## Special Values

- `null` — no value
- `true` — boolean true
- `false` — boolean false
