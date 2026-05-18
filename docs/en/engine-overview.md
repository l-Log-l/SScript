---
title: Engine Overview
nav_order: 1
parent: EN Home
---

# SScript Engine - Complete Overview

## What is SScript?

**SScript** is a Fabric Mod that adds a Python-like scripting language to Minecraft servers. It allows server administrators to write game logic without modifying Minecraft code.

**Core Features:**
- Event-driven architecture (player joins, chats, breaks blocks, etc.)
- 80+ built-in functions (math, strings, files, HTTP, JSON)
- Persistent global variables (survives server restarts)
- HTTP client (GET, POST, requests)
- File I/O with JSON support
- Tag-based player management
- Command execution
- Async/await capabilities

## Quick Facts

| Aspect | Details |
|--------|---------|
| Language | Python-like syntax |
| Execution Model | Tick-based, async-aware |
| File Types | `.ss`, `.event.ss`, `load.ss` |
| Builtin Functions | 80+ (players, blocks, math, strings, HTTP, files, etc.) |
| Events | 13 (join, chat, block events, death, sleep, etc.) |
| Persistence | Global variables in JSON |
| Performance | 100k loop iterations max, 60s HTTP timeout |
| Target Version | Minecraft 1.21.11 (Fabric) |

## Two Types of Scripts

### 1. Runtime Scripts (`.ss`)
- Execute when server starts or when you manually load them
- Good for: initialization, periodic tasks, one-time setup

```python
# sscripts/init.ss
file_mkdirs("sscripts/data")
log "Server initialized"
set_global("server_uptime", 0)
```

### 2. Event Handlers (`.event.ss`)
- Listen for in-game events and react to them
- Good for: responding to player actions, logging, automation

```python
# sscripts/chat.event.ss
on player_join(player):
    log player.name + " joined"
end

on player_chat(player, message):
    file_append("sscripts/logs/chat.log", player.name + ": " + message + "\n")
end
```

## All 13 Events

| Event | Trigger | Parameters |
|-------|---------|------------|
| `load` | Server startup | (none) |
| `player_connect` | Connection initialized | `(player)` |
| `player_join` | Player fully joined | `(player)` |
| `player_chat` | Message sent | `(player, message)` |
| `player_death` | Player dies | `(player, location)` |
| `player_dead` | Same as death (alt name) | `(player, location)` |
| `player_respawn` | Player respawns | `(player, alive)` |
| `player_sleep_attempt` | Trying to sleep | `(player, bed_pos)` |
| `player_sleep` | Successfully sleeping | `(player, bed_pos)` |
| `block_interact` | Right-click block | `(player, block)` |
| `block_place` | Actual block placed | `(player, block)` |
| `block_break` | Block broken | `(player, block)` |

## Key Builtin Categories

### Player Management (10 functions)
- `players()`, `player_count()`, `random_player()`
- `online(name)`, `get_target()`, `get_targets()`
- `has_tag()`, `tag_add()`, `tag_remove()`, `player_tags()`

### Effects & Commands (4 functions)
- `effect_give()`, `effect_clear()`, `exec()`, `tellraw()`

### Block & Position (4 functions)
- `pos()`, `get_block()`, `get_blocks()`, `has_block()`

### Mathematics (11 functions)
- `range()`, `random()`, `floor()`, `ceil()`, `round()`, `abs()`
- `min()`, `max()`, `sqrt()`, `pow()`, `int()`

### String Operations (15 functions)
- `len()`, `upper()`, `lower()`, `contains()`, `starts_with()`, `ends_with()`
- `trim()`, `replace()`, `substring()`, `index_of()`
- `split_get()`, `split_count()`

### Type Conversion (8 functions)
- `str()`, `num()`, `bool()`, `type()`, `sec()`, `int()`

### JSON Functions (2 functions)
- `json_parse()`, `json_stringify()`

### HTTP Functions (3 functions)
- `http_get()`, `http_post()`, `http_request()`

### File Functions (11 functions)
- `file_exists()`, `file_read()`, `file_write()`, `file_append()`
- `file_delete()`, `file_delete_recursive()`, `file_rename()`, `file_mkdirs()`, `file_list()`
- `file_read_json()`, `file_write_json()`

### Global Variables (2 functions)
- `get_global()`, `set_global()`

## Language Features

```python
# Variables
x = 42
name = "Steve"
player = {"name": "Alex", "level": 10}
items = [1, 2, 3]

# Functions (must be top-level)
func greet(name):
    return "Hello, " + name
end

# Conditions
if x > 10 and name == "Steve":
    log "Pass"
end

# Loops
for i in range(1, 5):
    log str(i)
end

# Async in events
on player_join(player):
    log "Joined"
    wait heavy_task, player.name  # Async call
    log "Queued"
end

# Error handling
try:
    resp = http_get("https://api.example.com")
catch err:
    log "Error: " + err
end
```

## Execution Model

### Linear Execution (`.ss` files)
1. Load script
2. Register all functions
3. Execute top-level statements
4. Done

### Event-based Execution (`.event.ss` files)
1. Load script
2. Register functions and event handlers
3. When event fires:
   - Create process for each matching handler
   - Run handler on scheduler (tick-based)
4. Multiple handlers run independently

### Async Pattern with `wait`
```
on player_join(player):
    log "A"           // Executes immediately
    wait fetch_data   // Queues async
    log "B"           // Executes immediately (doesn't wait)
end
// fetch_data runs on next tick
```

## File Organization

### Locations
- Scripts: `sscripts/` directory
- Global variables: `sscripts/globals.json`
- Logs: `logs/latest.log` + `logs/sscript/` folder

### File Extensions
- `.ss` — Runtime scripts
- `.event.ss` — Event handlers
- Special: `load.ss` runs first
- Regular `.ss` files run when loaded

## Performance Tips

✅ **Do:**
- Use `wait` for HTTP requests in events
- Cache lookups in variables
- Use tags for player organization
- Batch file operations

❌ **Don't:**
- Call `http_get()` synchronously in events
- Create huge lists with `get_blocks()`
- Use infinite loops (100k limit)
- Nest multiple heavy operations

## Common Patterns

### Pattern 1: Chat Logger
```python
on player_chat(player, message):
    file_mkdirs("sscripts/logs")
    file_append("sscripts/logs/chat.log", 
                player.name + ": " + message + "\n")
end
```

### Pattern 2: Player Verifier
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

### Pattern 3: Global State
```python
on player_join(player):
    count = num(get_global("total_joins"))
    set_global("total_joins", count + 1)
end
```

### Pattern 4: Deferred Action
```python
on player_break_block(player, block):
    wait check_permission, player.name, block.id
end

func check_permission(name, block_id):
    sleep 1  // 1 tick delay
    if not has_tag(name, "build"):
        log name + " cannot build " + block_id
    end
end
```

## Commonly Used Recipes

### Send message to player
```python
tellraw(player_name, "Some message")
```

### Get all online players
```python
players_list = get_targets("@a")
for p in players_list:
    log p.name
end
```

### Save/load player data
```python
// Save
player_data = {"name": "Steve", "level": 10}
file_write_json("sscripts/data/steve.json", player_data)

// Load
data = file_read_json("sscripts/data/steve.json")
log data.name
```

### HTTP request
```python
resp = http_get("https://api.github.com/users/octocat")
if resp.ok:
    user = resp.json
    log user.login  // "octocat"
end
```

### Apply potion effect
```python
effect_give("Steve", "minecraft:strength", 30, 1, false)
// Steve gets Strength II for 30 seconds (with particles)
```

## Architecture Summary

```
Lexer           Parse tokens
  ↓
Parser          Build AST
  ↓
Interpreter     Execute statements
  ↓
EventManager    Register/fire events
  ↓
ProcessScheduler  Tick-based execution
  ↓
Mixins          Hook Minecraft events
  ↓
GlobalVariables  Persistent storage
```

## Next Steps

1. **Get Started**: Read [Getting Started](getting-started.md) guide
2. **Learn Language**: Study [Language Guide](language.md)
3. **Master Functions**: Deep dive into [Functions & Async Mechanics](functions-mechanics.md)
4. **Server Commands**: Learn [Commands Reference](commands.md)
5. **Master Events**: Dive into [Complete Events Reference](complete-events.md)
6. **Advanced Features**: Explore [Advanced Features & Mechanics](advanced-features.md) (try-catch, selectors, NBT, HTTP headers, limits)
7. **API Reference**: Browse [Complete Built-in Reference](complete-reference.md)
8. **Architecture**: Understand [Architecture & Advanced](architecture.md)

## Support

- Check server logs: `logs/latest.log`
- SScript logs: `logs/sscript/`
- GitHub Repository: [l-log-l/sscript](https://github.com/l-log-l/sscript)
