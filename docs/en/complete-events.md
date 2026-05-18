---
title: Complete Events Reference
nav_order: 9
parent: EN Home
---

# Complete Events Reference

This page documents all events that can be handled in SScript `.event.ss` files.

## Event Lifecycle

1. **Server startup** → `load` event fires
2. **Player connects** → `player_connect` event
3. **Player fully joined** → `player_join` event
4. **Player does actions** → `player_chat`, `block_place`, `block_break`, etc.
5. **Player dies** → `player_death` + `player_dead` events
6. **Player sleeps** → `player_sleep`, `player_sleep_attempt` events
7. **Player respawns** → `player_respawn` event

## Server Events

### `load` → (no parameters)

Fires once when server starts and all scripts are loaded.

**Usage:** Initialization, persistent data setup.

```python
on load:
    log "Server starting..."
    set_global("startup_time", sec(0))
    log "Init complete"
end
```

## Player Connection Events

### `player_connect(player)` → object

Fires when a player's connection is initialized (before full join).

**Parameters:**
- `player`: Player object

**Player object fields:**
- `name` — player's username
- `uuid` — player's UUID
- `type` — entity type (always "minecraft:player")
- `x`, `y`, `z` — position coordinates
- `pos` — formatted position "X Y Z"
- `dimension` — dimension ID (e.g., "minecraft:overworld")
- `health` — current health (0-20)
- `gamemode` — game mode ("survival", "creative", etc.)
- `tags` — list of command tags
- `nbt` — NBT data as string

```python
on player_connect(player):
    log "Connection: " + player.name
end
```

### `player_join(player)` → object

Fires when a player fully joins the server (after login).

**Parameters:**
- `player`: Player object (see above)

**Usage:** Welcome messages, permissions setup, data loading.

```python
on player_join(player):
    log "Welcome " + player.name
    
    # Grant tag if first time
    if not has_tag(player.name, "seen_before"):
        tag_add(player.name, "seen_before")
        tellraw(player.name, "Welcome to the server!")
    end
end
```

### `player_respawn(player, alive)` → object, boolean

Fires when a player respawns after death.

**Parameters:**
- `player`: Player object
- `alive`: `true` = respawned, `false` = loading old player data

```python
on player_respawn(player, alive):
    log player.name + " respawned"
    effect_give(player.name, "minecraft:resistance", 5, 0, false)
end
```

## Chat Event

### `player_chat(player, message)` → object, string

Fires when a player sends a chat message.

**Parameters:**
- `player`: Player object
- `message`: The chat message text

**Usage:** Chat logging, filters, command parsing.

```python
on player_chat(player, message):
    log "[" + player.name + "] " + message
    
    # Log to file
    file_mkdirs("sscripts/logs")
    file_append("sscripts/logs/chat.log", player.name + ": " + message + "\n")
end
```

## Block Events

### `block_interact(player, block)` → object, object

Fires when a player successfully **right-clicks** a block.

**Includes:** Door toggle, button press, lever use, etc. (any interaction the server accepts).

**Parameters:**
- `player`: Player object
- `block`: Block object (see below)

**Block object fields:**
- `id` — block ID (e.g., "minecraft:diamond_ore")
- `x`, `y`, `z` — block coordinates
- `pos` — formatted position "X Y Z"
- `dimension` — dimension ID

```python
on block_interact(player, block):
    log player.name + " interacted with " + block.id + " at " + block.pos
    
    # Example: Log all door interactions
    if contains(block.id, "door"):
        log "Door interaction at " + block.pos
    end
end
```

### `block_place(player, block)` → object, object

Fires when a player **actually places** a new block.

This is semantically different from `block_interact`:
- `block_interact` fires for any accepted right-click (doors, levers, etc.)
- `block_place` fires ONLY when actual block state changes (new block placed)

**Parameters:**
- `player`: Player object
- `block`: Block object (the newly placed block)

**Usage:** Construction logging, grief protection, builds tracking.

```python
on block_place(player, block):
    log player.name + " placed " + block.id + " at " + block.pos
    
    # Prevent obsidian building
    if block.id == "minecraft:obsidian":
        if not has_tag(player.name, "builder"):
            run "setblock " + block.x + " " + block.y + " " + block.z + " air"
            tellraw(player.name, "You can't place obsidian here")
        end
    end
end
```

### `block_break(player, block)` → object, object

Fires when a player **breaks** a block.

**Parameters:**
- `player`: Player object
- `block`: Block object (before destruction)

**Usage:** Mining restrictions, ore tracking, griefing protection.

```python
on block_break(player, block):
    log player.name + " broke " + block.id + " at " + block.pos
    
    # Example: Log all diamond ore mining
    if block.id == "minecraft:diamond_ore":
        set_global("total_diamonds_mined", num(get_global("total_diamonds_mined")) + 1)
    end
end
```

## Player Death Events

### `player_death(player, location)` → object, string

Fires when a player dies (including respawn delay).

**Parameters:**
- `player`: Player object (at death location)
- `location`: Formatted position string "X Y Z"

```python
on player_death(player, location):
    log player.name + " died at " + location
end
```

### `player_dead(player, location)` → object, string

Alternative name for the same `player_death` event (both fire together for compatibility).

```python
on player_dead(player, location):
    # This also fires when player_death fires
end
```

## Sleep Events

### `player_sleep_attempt(player, bed_location)` → object, string

Fires when a player tries to sleep (before success/failure).

**Parameters:**
- `player`: Player object
- `bed_location`: Formatted position string "X Y Z"

```python
on player_sleep_attempt(player, bed_location):
    log player.name + " attempts to sleep"
end
```

### `player_sleep(player, bed_location)` → object, string

Fires when a player **successfully** goes to sleep.

**Parameters:**
- `player`: Player object
- `bed_location`: Formatted position string "X Y Z"

```python
on player_sleep(player, bed_location):
    log player.name + " went to sleep at " + bed_location
    
    # Award sleep achievements
    tag_add(player.name, "has_slept")
end
```

## Event Handling Patterns

### Pattern 1: Conditional Logic

```python
on player_chat(player, message):
    if contains(message, "spam"):
        log "Blocked spam from " + player.name
    end
end
```

### Pattern 2: Tag-Based Filtering

```python
on player_chat(player, message):
    if has_tag(player.name, "muted"):
        log "Muted player tried to chat: " + player.name
        # Could prevent message here
    end
end
```

### Pattern 3: Conditional Block Protection

```python
on block_break(player, block):
    # Protect specific block types for non-admins
    if block.id == "minecraft:obsidian" and not has_tag(player.name, "admin"):
        run "setblock " + block.x + " " + block.y + " " + block.z + " " + block.id
        tellraw(player.name, "You cannot break obsidian!")
    end
end
```

### Pattern 4: Deferred Action with `wait`

```python
on player_join(player):
    log player.name + " joined"
    wait send_welcome, player.name
end

func send_welcome(name):
    sleep 20  # 1 second delay
    tellraw(name, "Welcome to the server!")
end
```

### Pattern 5: Global State Tracking

```python
on player_join(player):
    set_global("last_joined", player.name)
end

on player_chat(player, message):
    if message == "who_was_last":
        last = get_global("last_joined")
        log "Last player: " + str(last)
    end
end
```

## Event Processing Order

1. **Event fired** by server/mixin
2. **All handlers registered** for that event are queued
3. **Each handler gets own process** on ProcessScheduler
4. **Handlers execute independently** (not necessarily sequentially)
5. **Errors in one handler** don't affect others

## Important Notes

- **Event handlers are async**: Use `wait` to defer heavy operations
- **Order not guaranteed**: Multiple handlers may execute in any order
- **Game thread safe**: Mixin hooks are thread-safe
- **Player objects immutable**: Don't modify player object (read-only)
- **Block positions**: Use `get_block()` to get current state if you need updates

## Testing Events

Create `test_events.event.ss`:

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
    log "[BREAK] " + player.name + " broke " + block.id + " at " + block.pos
end

on block_place(player, block):
    log "[PLACE] " + player.name + " placed " + block.id + " at " + block.pos
end

on player_death(player, location):
    log "[DEATH] " + player.name + " died at " + location
end
```
