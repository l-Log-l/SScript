---
title: Functions & Async Mechanics
nav_order: 4
parent: EN Home
---

# Functions & Async Mechanics

SScript has powerful function capabilities with multiple calling methods that affect execution flow. This is the core of understanding how script logic executes.

## Function Definition

### Using `func` Keyword

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

### Using `def` Keyword (Alternative)

`def` is an alias for `func` — they are identical:

```python
def greet(name):
    return "Hello, " + name
end

def calculate(x, y):
    return x * y
end
```

**Recommendation:** Use `func` for consistency, but `def` works if preferred.

## Three Ways to Call Functions

### 1. **Synchronous Call** — `test()` in runtime context

In `.ss` runtime scripts, function calls block until completion:

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

**Execution order:**
1. Print "Starting..."
2. Wait for `slow_calculation()` to complete
3. Print "Result: ..."
4. Print "Done"

**Use case:** Initialization scripts where you need sequential, blocking execution.

---

### 2. **Async Call** — `test()` in event handler (no `wait`, no assignment)

When you call a function inside an event handler WITHOUT `wait` and WITHOUT assigning to a variable, it queues asynchronously:

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
    log "Player joined: " + player.name  // Executes immediately
    fetch_api_data(player.name)          // Queued asynchronously!
    log "Check queued"                   // Executes immediately (doesn't wait)
end
```

**Execution order:**
1. Print "Player joined: Steve"
2. Print "Check queued"
3. **Later (next tick or when scheduler runs):** `fetch_api_data()` executes

**Why?** Event handlers must not block the server. Heavy I/O (HTTP, file) runs in background.

---

### 3. **Wait Call** — `wait test()` (explicit async with blocking)

Use `wait` when you explicitly want to queue a function AND suspend event handler until it completes:

```python
# handler.event.ss

func verify_player(player_name):
    sleep 2  // Simulate delay
    if has_tag(player_name, "verified"):
        return true
    else
        return false
    end
end

on player_join(player):
    log "Checking verification..."
    wait verify_player(player.name)  // Block handler until verify_player completes
    log "Verification complete"      // Executes after wait finishes
end
```

**Execution order:**
1. Print "Checking verification..."
2. **Queue verify_player() to run next tick**
3. **Suspend event handler**
4. Eventually: "Verification complete" prints when wait resolves

**Key difference from plain call:** `wait` **suspends** the event handler; plain call doesn't.

---

## Assignment Triggers Wait Behavior

### Pattern: `var = test()`

When you assign a function's return value to a variable, it automatically behaves like `wait`:

```python
on player_join(player):
    log "A"
    result = fetch_player_level(player.name)  // This acts like wait!
    log "B - got result: " + str(result)
end

func fetch_player_level(name):
    sleep 1
    return 10
end
```

**Execution:**
1. Print "A"
2. **Queue function, suspend handler**
3. Eventually: print "B - got result: 10"

**Why?** The handler needs the return value to assign to `result`, so it **must wait** for `fetch_player_level()` to complete.

---

## Comparison Table

| Scenario | Syntax | Blocks Handler? | How Result Used | When Complete |
|----------|--------|-----------------|-----------------|-----------------|
| Runtime (sync) | `result = test()` | ✅ Yes (runtime blocks) | Stored in var | Immediately (same tick) |
| Event (async, no var) | `test()` | ❌ No (async) | Ignored | Background tick |
| Event (explicit wait) | `wait test()` | ✅ Yes (handler suspends) | Ignored | Next tick onwards |
| Event (assign result) | `var = test()` | ✅ Yes (handler suspends) | Stored in var | Handler resumes after complete |

---

## Practical Examples

### Example 1: Player Verification on Join

```python
func check_permissions(player_name):
    sleep 1  // Simulate DB query
    if has_tag(player_name, "admin"):
        return true
    end
    return false
end

on player_join(player):
    log "Player: " + player.name
    
    is_admin = check_permissions(player.name)  // Wait for result
    
    if is_admin:
        log player.name + " is admin"
    else
        log player.name + " is regular"
    end
end
```

**Flow:** Handler suspends at `is_admin =`, resumes when `check_permissions()` returns.

---

### Example 2: Background Logging (Fire & Forget)

```python
func log_to_file(message):
    file_mkdirs("sscripts/logs")
    file_append("sscripts/logs/events.log", message + "\n")
end

on player_chat(player, message):
    log_to_file(player.name + ": " + message)  // No wait, no assignment
    // Handler continues immediately, logging happens in background
end
```

**Flow:** Handler doesn't wait, function queued.

---

### Example 3: Chain Multiple Waits

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

**Flow:**
1. Execute `fetch_name()`, suspend
2. When done, execute `fetch_level()`, suspend
3. When done, print result

---

## Performance Tips

### ✅ Do Use Fire-and-Forget for Side Effects

```python
on player_chat(player, message):
    log_to_file(message)  // No wait, no var
    // Handler continues
end
```

### ✅ Use `wait` Only When You Need Result

```python
on player_join(player):
    level = fetch_player_level(player.name)  // Assignment auto-waits
    if level > 10:
        tag_add(player.name, "veteran")
    end
end
```

### ❌ Don't Block Events Unnecessarily

```python
// BAD: Blocking event for no reason
on player_chat(player, message):
    wait heavy_computation()  // No result used!
end

// GOOD: Just call without wait
on player_chat(player, message):
    heavy_computation()  // Fire and forget
end
```

### ❌ Don't Nest Heavy Operations

```python
// BAD: Chains many waits
on player_join(player):
    wait fetch_data_1(player)
    wait fetch_data_2(player)
    wait fetch_data_3(player)
    wait fetch_data_4(player)
    // Suspends handler for 4 ticks
end

// GOOD: If independent, call async
on player_join(player):
    fetch_data_1(player)  // All queue at once
    fetch_data_2(player)
    fetch_data_3(player)
    fetch_data_4(player)
end
```

---

## Under the Hood

### How Async Works

1. **Function Call Detected** → `wait test()` or `test()` or `var = test()`
2. **Process Spawned** → Function queued to ProcessScheduler
3. **Handler State**:
   - `wait` or `=` → Handler suspends, waits for process to complete
   - No `wait`, no `=` → Handler continues immediately
4. **Next Tick** → Scheduler executes queued function
5. **Return Value** → If handler was suspended, resumes with result

### ProcessScheduler

- Executes one function per server tick (50ms game time)
- Multiple functions can be queued independently
- Event handler can suspend and resume

---

## Migration Guide

### From Other Languages

If you're used to JavaScript or Python:

| Pattern | SScript | Notes |
|---------|---------|-------|
| `await` | `wait` or `=` | Use when you need result |
| Promises | Implicit | Every function returns a "promise" (process) |
| Callbacks | Not needed | Use `wait` instead |
| Fire-and-forget | Plain call | `test()` without `wait` or `=` |

---

## Debugging

### How to Debug Function Calls

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

**Output:**
```
[A] Before call
Inside test_function
[B] After call, result: done
```

The fact that `[B]` prints AFTER `Inside test_function` confirms the handler waited.

---

## See Also

- [Language Guide](language.md) — Variables, control flow
- [Complete Events Reference](complete-events.md) — All 13 events
- [Architecture & Advanced](architecture.md) — ProcessScheduler details
