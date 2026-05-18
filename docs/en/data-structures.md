---
title: Data Structures & Objects
nav_order: 7
parent: EN Home
---

# Data Structures & Objects

This guide documents all the object structures returned by SScript functions.

## Block Object

Returned by: `get_block()`, `get_blocks()`, event parameters

### Fields
| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Block ID, e.g. `"minecraft:dirt"`, `"minecraft:chest"` |
| `x` | number | X coordinate |
| `y` | number | Y coordinate |
| `z` | number | Z coordinate |
| `pos` | string | Formatted position: `"x y z"` |
| `dimension` | string | Dimension, e.g. `"minecraft:overworld"` |
| `state` | object | BlockState properties (see State Object below) |
| `nbt` | object | Block entity data (if present, otherwise empty object) |

### State Object

Contains properties of the block's current state. Properties vary by block type.

**Examples:**

```python
# Get lever block
block = get_block(10, 64, 20)

# Check if powered (works for levers, repeaters, etc.)
if block.state.powered == true:
    log "Block is powered"
end

# Check if lit (works for torches, campfires, etc.)
if block.state.lit == true:
    log "Block is lit"
end

# Check direction facing (works for stairs, pistons, etc.)
log "Facing: " + str(block.state.facing)  # e.g. "north", "south"

# Check half (works for beds, doors, etc.)
log "Half: " + str(block.state.half)  # e.g. "upper", "lower"
```

### NBT Object

For block entities (chest, furnace, hopper, etc.), `nbt` contains serialized data:

```python
# Get chest block
block = get_block(100, 64, 200)

# Access inventory items
if block.nbt != null:
    log "Items count: " + str(block.nbt.Items.size())
    
    # First item in chest
    if block.nbt.Items.size() > 0:
        first_item = block.nbt.Items[0]
        log "First item: " + first_item.id  # e.g. "minecraft:diamond"
        log "Count: " + str(first_item.Count)
    end
end

# Full example - check furnace fuel
furnace = get_block(50, 64, 50)
if furnace.nbt != null:
    fuel_time = furnace.nbt.BurnTime
    log "Furnace burn time: " + str(fuel_time)
end
```

### Complete Example

```python
# Inspect any block
func inspect_block(x, y, z):
    block = get_block(x, y, z)
    log "Block: " + block.id
    log "Position: " + block.pos
    log "Dimension: " + block.dimension
    
    # Log state properties
    if block.state != null:
        log "State: powered=" + str(block.state.powered) + 
            " lit=" + str(block.state.lit)
    end
    
    # Log NBT if block entity
    if block.nbt != null:
        log "Has NBT data"
    end
end
```

---

## Player / Target Object

Returned by: `get_target()`, `get_targets()`, event parameters (`player_join`, `player_chat`, etc.)

### Fields
| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Player username, e.g. `"Steve"` |
| `type` | string | Entity type, usually `"player"` |
| `dimension` | string | Current dimension, e.g. `"minecraft:overworld"` |
| `x` | number | X coordinate |
| `y` | number | Y coordinate |
| `z` | number | Z coordinate |
| `pos` | string | Formatted position: `"x y z"` |
| `tags` | string | Comma-separated tags, e.g. `"admin,vip"` |
| `nbt` | object | Player NBT data (inventory, effects, etc.) |
| `selector` | string | The selector used to retrieve this player |

### Using Player Objects

```python
# Get single player
player = get_target("Steve")
if player != null:
    log "Found: " + player.name
    log "At: " + player.pos
    log "Dimension: " + player.dimension
    
    # Check tags
    log "Tags: " + player.tags
    
    # Check inventory
    if player.nbt != null and player.nbt.Inventory.size() > 0:
        log "Has items"
    end
end

# Get all online players
all_players = get_targets("@a")
for p in all_players:
    log p.name + " at " + p.pos
end

# Get all players with tag
admins = get_targets("@a[tag=admin]")
log "Admins online: " + str(admins.size())

# Iterate and check tags
for player in all_players:
    if player.tags.contains("vip"):
        log player.name + " is VIP"
    end
end
```

### Coordinate Access

```python
player = get_target("Alex")
if player != null:
    log "X: " + str(player.x)
    log "Y: " + str(player.y)
    log "Z: " + str(player.z)
    
    # Calculate distance from origin
    dist = sqrt(player.x * player.x + player.y * player.y + player.z * player.z)
    log "Distance from 0,0,0: " + str(dist)
end
```

### NBT Data (Advanced)

Player NBT contains inventory, effects, and other data:

```python
player = get_target("Steve")

# Check inventory
if player.nbt != null:
    inventory = player.nbt.Inventory
    log "Inventory items: " + str(inventory.size())
    
    # Check held item
    if inventory.size() > 0:
        held_item = inventory[0]
        log "Held: " + held_item.id
    end
    
    # Check active effects
    if player.nbt.ActiveEffects.size() > 0:
        log "Has effects"
    end
end
```

---

## HTTP Response Object

Returned by: `http_get()`, `http_post()`, `http_request()`

### Fields
| Field | Type | Description |
|-------|------|-------------|
| `status` | number | HTTP status code, e.g. `200`, `404`, `500` |
| `ok` | boolean | `true` if status is 200-299 |
| `body` | string | Response body as raw text |
| `headers` | object | Response headers (keys are header names) |
| `json` | object/list | Pre-parsed JSON (if valid JSON in body) |

### Basic Usage

```python
# Simple GET request
resp = http_get("https://api.example.com/data")

if resp.ok:
    log "Request succeeded"
    log "Status: " + str(resp.status)
    log "Body: " + resp.body
else:
    log "Request failed with status: " + str(resp.status)
end
```

### JSON Response

```python
# Request that returns JSON
resp = http_get("https://api.github.com/users/octocat")

if resp.ok:
    user = resp.json
    log "Login: " + user.login  # "octocat"
    log "Repos: " + str(user.public_repos)
else:
    log "Failed: " + str(resp.status)
end
```

### Custom Headers

```python
headers = {
    "Authorization": "Bearer YOUR_TOKEN",
    "Content-Type": "application/json"
}

resp = http_get("https://api.example.com/user", headers, 10)

if resp.ok:
    data = resp.json
    log "Data received"
end
```

### POST Request

```python
# Create payload
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
    log "Created user"
else:
    log "Error: " + str(resp.status)
end
```

### Response Headers Access

```python
resp = http_get("https://api.example.com/data")

if resp.ok:
    # Access response headers
    content_type = resp.headers.get("content-type")
    log "Content type: " + str(content_type)
    
    # Check for custom headers
    api_version = resp.headers.get("x-api-version")
    if api_version != null:
        log "API Version: " + api_version
    end
end
```

### Error Handling

```python
func fetch_data(url):
    try:
        resp = http_get(url, {}, 5)
        
        if not resp.ok:
            log "HTTP error: " + str(resp.status)
            return null
        end
        
        if resp.json == null:
            log "Invalid JSON response"
            return null
        end
        
        return resp.json
    catch err:
        log "Request failed: " + err
        return null
    end
end

# Usage
data = fetch_data("https://api.example.com/items")
if data != null:
    log "Got " + str(data.items.size()) + " items"
end
```

---

## Command Result Object

Returned by: `exec()` function

### Fields
| Field | Type | Description |
|-------|------|-------------|
| `status` | string | `"success"` or `"error"` |
| `result` | number | **Exit code** from command (>0 = success, 0 = error). This is NOT the command output text. |
| `command` | string | The command that was executed |
| `output` | string | Command feedback/output text (currently empty, reserved for future use) |
| `error` | string | Error message (only if status is `"error"`) |

### Understanding Exit Codes

`result` field contains the **exit code** returned by the Minecraft server command:
- Most successful commands return `1`
- Failed commands return `0`
- Some commands return specific codes

**Important:** Exit codes are NOT the text output of commands. If you need data from a command like `data get block`, you must:
1. Use NBT functions directly: `get_block(x,y,z).nbt` 
2. Or parse command output from chat (advanced)

```python
# Execute a command
result = exec("give Steve diamond 64")

if result.status == "success":
    log "Command succeeded"
    log "Exit code: " + str(result.result)
else:
    log "Command failed"
    log "Error: " + result.error
end
```

### Using Results

```python
# Check if command was successful
result = exec("tp Steve 0 64 0")

if result.result > 0:
    log "Teleported successfully"
else:
    log "Teleport failed"
end

# Always capture results
result = exec("say Hello, players!")
log "Executed: " + result.command
log "Status: " + result.status
```

### Error Handling

```python
func safe_execute(command):
    result = exec(command)
    
    if result.status == "error":
        log "[ERROR] Command failed: " + result.error
        log "[INFO] Command was: " + result.command
        return false
    end
    
    log "[OK] Command executed"
    return true
end

# Usage
safe_execute("give @a diamond 10")
safe_execute("invalid_command")
```

### Command Chain

```python
func broadcast(message):
    result = exec("say " + message)
    return result.status == "success"
end

# Multiple commands
for i in range(1, 3):
    broadcast("Server message " + str(i))
end
```

---

## Position Object

Returned by: `pos()` function, block/player events

### Fields
| Field | Type | Description |
|-------|------|-------------|
| `x` | number | X coordinate |
| `y` | number | Y coordinate |
| `z` | number | Z coordinate |
| `pos` | string | Formatted string `"x y z"` |

### Usage

```python
# Create position
spawn = pos(0, 64, 0)
log spawn.pos  # "0 64 0"

# Use in commands
exec("tp Steve " + spawn.pos)

# Extract coordinates
log spawn.x  # 0
log spawn.y  # 64
log spawn.z  # 0
```

---

## Object Access Patterns

### Accessing Nested Fields

```python
# Array access
items = block.nbt.Items
first = items[0]
log first.id

# Object access
data = resp.json
log data.user.name
log data.settings["difficulty"]

# Safe access (check null first)
if player.nbt != null and player.nbt.Inventory.size() > 0:
    item = player.nbt.Inventory[0]
    log item.id
end
```

### Type Checking

```python
# Check if object exists
if block.nbt != null:
    log "Block has NBT"
end

# Check if list/array is empty
if player.tags.size() > 0:
    log "Player has tags"
end

# Check field existence
if resp.json.error != null:
    log "Response has error"
end
```

### Iteration Patterns

```python
# Iterate over array
blocks = get_blocks(p1, p2)
for block in blocks:
    if block.id == "minecraft:diamond_ore":
        log "Found diamond at " + block.pos
    end
end

# Iterate over players
players = get_targets("@a")
for player in players:
    log player.name
end
```
