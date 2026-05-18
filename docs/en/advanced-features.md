---
title: Advanced Features & Mechanics
nav_order: 6
parent: EN Home
---

# Advanced Features & Mechanics

Beyond basic scripting, SScript has powerful advanced features for complex logic, error handling, and optimization.

---

## Error Handling with Try-Catch

### Basic Syntax

```python
try:
    // Code that might fail
    result = http_get("https://api.example.com/data")
    data = result.json
    log data.score
catch err:
    // Handle error
    log "Error occurred: " + err
end
```

### Common Errors to Catch

```python
try:
    // Invalid file path
    content = file_read("nonexistent.txt")
catch err:
    log "File not found: " + err
end

try:
    // Network timeout
    resp = http_get("https://slow-server.com")
catch err:
    log "HTTP error: " + err
end

try:
    // JSON parse error
    obj = json_parse("invalid json")
catch err:
    log "JSON parse failed: " + err
end

try:
    // Division by zero or type mismatch
    result = 10 / 0
catch err:
    log "Math error: " + err
end
```

### Error Message Details

When an error is caught, `err` contains:
- **Error type** (FileNotFound, HttpTimeout, JsonParseError, etc.)
- **Message** (human-readable description)
- **Line number** (where error occurred)

**Example:**

```python
try:
    run "invalid command syntax"
catch err:
    log "Error: " + err  // "Error: CommandSyntaxException at line 42"
end
```

---

## Player Selectors

SScript executes Minecraft selectors at runtime to find players.

### Supported Selectors

```python
// All online players
players = get_targets("@a")

// All online players within 100 blocks
nearby = get_targets("@a[distance=..100]")

// Players with specific tag
verified = get_targets("@a[tag=verified]")

// Player with lowest score
loser = get_target("@a[sort=nearest,limit=1]")
```

### Selector Properties

| Selector | Effect | Example |
|----------|--------|---------|
| `distance` | Range in blocks | `@a[distance=0..50]`, `@a[distance=..100]` |
| `tag` | Has/no tag | `@a[tag=admin]`, `@a[tag=!banned]` |
| `scores` | Objective score | `@a[scores={kills=5..}]` |
| `gamemode` | Survival/Creative/etc | `@a[gamemode=survival]` |
| `limit` | Max results | `@a[limit=5]` |
| `sort` | nearest/random/furthest | `@a[sort=nearest]` |
| `name` | Player name | `@a[name=Steve]` |
| `level` | Experience level | `@a[level=10..]` |

### Dynamic Selectors

```python
func find_admins():
    admins = get_targets("@a[tag=admin]")
    log "Found " + str(len(admins)) + " admins"
    return admins
end

on player_join(player):
    nearby = get_targets("@a[distance=0..50,gamemode=survival]")
    for p in nearby:
        tellraw(p.name, player.name + " is nearby!")
    end
end
```

---

## Player Object Structure & NBT Data

Player objects contain Minecraft NBT data extracted at runtime:

### Basic Properties

```python
player.name           // "Steve"
player.uuid           // "a1b2c3d4-..." (unique ID)
player.health         // 20.0 (0-20)
player.max_health     // 20.0
player.food_level     // 10 (0-20)
player.saturation     // 5.2 (decimal)
player.x_pos          // 100.5 (float)
player.y_pos          // 64.2
player.z_pos          // -50.1
player.dimension      // "minecraft:overworld"
player.gamemode       // "survival", "creative", "adventure", "spectator"
player.level          // 42 (XP level)
player.experience     // 0.8 (0.0-1.0, decimal part)
```

### Advanced Properties

```python
player.on_ground      // true/false
player.flying         // true/false
player.sneaking       // true/false
player.sprinting      // true/false
player.swimming       // true/false
player.fall_distance  // 3.2 (blocks fallen)
player.fire           // 0 (ticks on fire)
player.air            // 300 (remaining air while underwater)
player.pose           // "standing", "swimming", "falling", etc
```

### Inventory Example

```python
on player_join(player):
    health = player.health
    level = player.level
    
    if health < 5:
        log player.name + " has low health: " + str(health)
    end
    
    if level >= 30:
        tag_add(player.name, "high_level")
    end
end
```

---

## HTTP Headers & Custom Requests

### Basic GET/POST (Auto Headers)

```python
// GET request - auto Content-Type: application/x-www-form-urlencoded
resp = http_get("https://api.example.com/data")

// POST request - auto Content-Type: application/json if body is JSON
resp = http_post("https://api.example.com/data", {"key": "value"})
```

### Full Request with Custom Headers

```python
response = http_request(
    "POST",
    "https://api.example.com/users",
    {
        "Authorization": "Bearer token123",
        "X-Custom-Header": "myvalue"
    },
    {"name": "Steve", "score": 100}
)

if response.ok:
    log "Request succeeded: " + str(response.status)
else
    log "Request failed: " + str(response.status)
end
```

### Response Object

```python
response.ok         // true if 200-299 status
response.status     // 200, 404, 500, etc.
response.text       // Raw response body as string
response.json       // Parsed JSON object (if JSON response)
response.headers    // Map of response headers
```

### Example: API Integration

```python
func fetch_player_stats(player_name):
    try:
        resp = http_get("https://api.example.com/players/" + player_name)
        if resp.ok:
            stats = resp.json
            return stats  // {"kills": 10, "deaths": 2, "wins": 5}
        else
            log "API error: " + str(resp.status)
            return null
        end
    catch err:
        log "Network error: " + err
        return null
    end
end

on player_join(player):
    stats = fetch_player_stats(player.name)
    if stats:
        tellraw(player.name, "Kills: " + str(stats.kills))
    end
end
```

---

## List Methods

Lists/arrays support methods for manipulation:

### `.add(value)` — Add Element

```python
items = []
items.add("apple")
items.add("banana")
items.add("apple")

log len(items)  // 3
```

### `.remove(index)` — Remove by Index

```python
items = ["a", "b", "c"]
items.remove(1)      // Remove "b"

log items           // ["a", "c"]
log len(items)      // 2
```

### Practical Example

```python
func collect_online_staffs():
    all_players = get_targets("@a")
    staff_list = []
    
    for player in all_players:
        if has_tag(player.name, "staff"):
            staff_list.add(player.name)
        end
    end
    
    return staff_list
end

on player_break_block(player, block):
    staffs = collect_online_staffs()
    
    for staff_name in staffs:
        tellraw(staff_name, player.name + " broke " + block.id)
    end
end
```

---

## Global Variables Persistence

Global variables are saved to `sscripts/globals.json` and persist across server restarts.

### Automatic Save Behavior

- Changes are debounced: **saved at most every 2 seconds**
- Not saved immediately (to avoid I/O overhead)
- Saved when server stops gracefully

### Manual Save Triggers

```python
set_global("important_data", {"version": 3})
// Will be flushed to disk within 2 seconds

// Immediate save (on next tick):
// Server shutdown triggers save
```

### Example: Player Stats Persistence

```python
func record_kill(player_name):
    current = num(get_global("kills_" + player_name))
    set_global("kills_" + player_name, current + 1)
    
    // Saved to disk automatically
end

on player_death(player, location):
    if location.killer:
        record_kill(location.killer)
    end
end

on server_start:
    // Load saved kill counts
    log "Loading persistent stats..."
    // (stats loaded from globals.json)
end
```

---

## Performance Constraints & Limits

### Execution Limits

| Limit | Value | Consequence |
|-------|-------|------------|
| Max running processes | 500 | Cannot spawn more (queue instead) |
| Processes spawned per tick | 20 | Max 20 new processes/tick (prevents lag spike) |
| Statements per tick | 50 | Process limited to 50 statements/tick (spread over multiple ticks) |
| Loop iterations | 1,000,000 | Infinite loops break (security limit) |
| While loop guard | Enabled | Prevents runaway loops |
| HTTP timeout | 60 seconds | Long requests abort after 60s |
| File I/O timeout | 30 seconds | File operations timeout |

### Memory Optimization

```python
// ❌ BAD: Creates 100k list at once
on server_start:
    huge_list = get_blocks(-1000, 0, -1000, 1000, 100, 1000)
end

// ✅ GOOD: Process blocks in smaller chunks
on server_start:
    wait process_region_1()
    wait process_region_2()
    wait process_region_3()
end

func process_region_1():
    blocks = get_blocks(-1000, 0, -1000, 0, 100, 1000)
    log "Processed region 1: " + str(len(blocks))
end
```

### Tick Budget

Each server tick (50ms game time), SScript processes completes:
- **Max 20 new processes** can be spawned
- **Each running process** executes ~**50 statements**
- Remaining time runs normal Minecraft logic

**Implication:** Long-running tasks automatically spread across multiple ticks.

---

## File Path Resolution

Files are resolved relative to **server root**:

```python
// These are equivalent (both create sscripts/data/players.json):
file_write_json("sscripts/data/players.json", data)
file_write_json("sscripts/data/players.json", data)  // From server root

// These are NOT equivalent:
file_write_json("data.json", data)           // Creates in server root
file_write_json("logs/script.log", data)     // Creates in server root/logs
```

**Best practice:** Start paths from `sscripts/` for organization.

---

## Debugging Advanced Features

### Enable Debug Output

```
/sscript debug on
```

This logs:
- Every function call
- Variable assignments
- Selector evaluations
- HTTP requests sent
- File I/O operations

### Tracing Async Execution

```python
func slow_fetch(player):
    log "[FETCH] Starting for " + player
    wait http_get("https://api.example.com")
    log "[FETCH] Completed for " + player
end

on player_join(player):
    log "[EVENT] Player join: " + player.name
    slow_fetch(player)
    log "[EVENT] Handler complete"
end

// Output:
// [EVENT] Player join: Steve
// [EVENT] Handler complete
// [FETCH] Starting for Steve
// [FETCH] Completed for Steve
```

---

## See Also

- [Functions & Async Mechanics](functions-mechanics.md) — Async patterns
- [Commands Reference](commands.md) — Server commands
- [Complete Built-in Reference](complete-reference.md) — All 60+ functions
