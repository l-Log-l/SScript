---
title: Complete Built-in Reference
nav_order: 8
parent: EN Home
---

# Complete Built-in Functions Reference

This page provides exhaustive documentation of all 80+ built-in functions available in SScript.

> **📚 Object Reference:** For detailed documentation of returned objects (Block, Player, Response, Command), see [Data Structures & Objects](data-structures.md)

## Player Functions

### `players()` → string
Returns a comma-separated list of all online player names.

```python
all_players = players()
log all_players  # Output: "Steve,Alex,Bob"
```

### `player_count()` → number
Returns the total number of online players.

```python
count = player_count()
log "Total players: " + str(count)
```

### `random_player()` → string | null
Returns a random online player's name, or `null` if no players online.

```python
lucky = random_player()
if lucky != null:
    log lucky + " is lucky!"
end
```

### `online(name)` → boolean
Checks if a player with given name is online.

```python
if online("Steve"):
    log "Steve is here!"
end
```

### `get_target(selector)` → object | null
Gets a single player target by selector string or exact name.

Returns player object with fields: `name`, `type`, `dimension`, `x`, `y`, `z`, `pos`, `tags`, `nbt`, `selector`.

```python
target = get_target("Steve")
if target != null:
    log target.name + " is at " + target.pos
end
```

### `get_targets(selector)` → list
Gets all players matching selector. Supports:
- `"@a"` — all players
- `"@a[tag=admin]"` — all players with "admin" tag
- `"PlayerName"` — exact player name

```python
all_targets = get_targets("@a")
for player in all_targets:
    log player.name
end

admins = get_targets("@a[tag=admin]")
log str(len(admins)) + " admins online"
```

## Tag Functions

### `has_tag(player_name, tag)` → boolean
Checks if player has a command tag.

```python
if has_tag("Steve", "vip"):
    log "VIP player!"
end
```

### `tag_add(player_name, tag)` → boolean
Adds a tag to a player.

```python
tag_add("Steve", "admin")
log "Tagged Steve as admin"
```

### `tag_remove(player_name, tag)` → boolean
Removes a tag from a player.

```python
tag_remove("Steve", "admin")
```

### `player_tags(player_name)` → string
Returns all tags for a player as comma-separated string.

```python
tags_str = player_tags("Steve")
log "Tags: " + tags_str  # Example: "vip,admin,verified"
```

## Effect/Potion Functions

### `effect_give(player_name, effect_id, duration_sec, amplifier, hide_particles)` → boolean
Applies a potion effect to a player.

- `effect_id`: e.g., `"minecraft:speed"`, `"minecraft:strength"`, `"minecraft:blindness"`
- `duration_sec`: duration in seconds (auto-converted to ticks × 20)
- `amplifier`: effect level (0 = level I, 1 = level II, etc.)
- `hide_particles`: `true` to hide particle effects

```python
# Give Speed II for 30 seconds
effect_give("Steve", "minecraft:speed", 30, 1, false)

# Apply Blindness without particles
effect_give("Steve", "minecraft:blindness", 10, 0, true)

# Common effects:
# - minecraft:speed
# - minecraft:slowness
# - minecraft:strength
# - minecraft:weakness
# - minecraft:regeneration
# - minecraft:resistance
# - minecraft:fire_resistance
# - minecraft:poison
# - minecraft:saturation
# - minecraft:hunger
# - minecraft:nausea
# - minecraft:mining_fatigue
# - minecraft:blindness
# - minecraft:levitation
```

### `effect_clear(player_name, effect_id)` → boolean
Removes a specific potion effect from a player.

```python
effect_clear("Steve", "minecraft:speed")
```

## Command Functions

### `exec(command)` → object
Executes a server command and returns execution status. Returns object with fields:
- `status`: `"success"` or `"error"`
- `result`: Command result code (positive = success)
- `command`: The command that was executed
- `error` (if status is error): Error message

```python
# Execute command and check result
result = exec("give Steve diamond 64")
if result.status == "success":
    log "Command executed with code: " + str(result.result)
else:
    log "Error: " + result.error
end

# Execute multiple commands
exec("say Hello world")
exec("tp Steve 0 64 0")
```

### `tellraw(selector, message_payload)` → null
Sends a raw JSON message to targeted players.

```python
# Simple text
tellraw("Steve", "This is a message")

# With JSON object (advanced formatting)
# For now, pass plain text or formatted string
```

## Position & Block Functions

### `pos(x, y, z)` → object
Creates a position object with fields: `x`, `y`, `z`, `pos` (formatted string).

```python
spawn_pos = pos(0, 64, 0)
log spawn_pos.pos  # Output: "0 64 0"
```

### `get_block(pos[, dimension]) → object | null`
### `get_block(x, y, z[, dimension])` → object | null
Retrieves block information at a specific location.

Returns object with: `id` (block ID), `x`, `y`, `z`, `pos`, `dimension`, `state`, `nbt`.
For block entities, `nbt` is exposed as an object so you can read nested fields like `block.nbt.Items[0]`.
The `state` object contains block properties (e.g., `state.powered` for levers, `state.lit` for torches).

```python
# Using position object
p = pos(0, 64, 0)
block = get_block(p)
log block.id  # e.g., "minecraft:dirt"

# Using coordinates
block = get_block(0, 64, 0)
log "Block: " + block.id

# Specific dimension (optional)
block = get_block(0, 64, 0, "minecraft:nether")
```

### `get_blocks(pos1, pos2[, dimension])` → list
Returns all blocks in a cuboid region between two positions.

```python
p1 = pos(0, 64, 0)
p2 = pos(10, 70, 10)
blocks = get_blocks(p1, p2)

for block in blocks:
    log block.id
end
```

### `has_block(pos1, pos2, block_id[, dimension])` → boolean
Checks if a specific block type exists within a region.

```python
p1 = pos(0, 0, 0)
p2 = pos(100, 100, 100)

if has_block(p1, p2, "minecraft:diamond_ore"):
    log "Found diamond ore!"
end
```

## Mathematics Functions

### `range(start, end)` → list
Generates a list of integers from `start` to `end` inclusive.

```python
nums = range(1, 5)
# Result: [1, 2, 3, 4, 5]

for i in nums:
    log str(i)
end
```

### `random(min, max)` → number
Returns a random integer between `min` (inclusive) and `max` (exclusive).

```python
lucky = random(1, 100)
log "Random number: " + str(lucky)
```

### `floor(n)` → number
Rounds down to nearest integer.

```python
x = floor(3.7)
log str(x)  # 3
```

### `ceil(n)` → number
Rounds up to nearest integer.

```python
x = ceil(3.2)
log str(x)  # 4
```

### `round(n)` → number
Rounds to nearest integer.

```python
x = round(3.5)
log str(x)  # 4
```

### `abs(n)` → number
Absolute value.

```python
x = abs(-10)
log str(x)  # 10
```

### `min(a, b)` → number
Returns minimum of two numbers.

```python
x = min(5, 3)
log str(x)  # 3
```

### `max(a, b)` → number
Returns maximum of two numbers.

```python
x = max(5, 3)
log str(x)  # 5
```

### `sqrt(n)` → number
Square root.

```python
x = sqrt(16)
log str(x)  # 4
```

### `pow(base, exp)` → number
Power/exponentiation: base^exp.

```python
x = pow(2, 3)
log str(x)  # 8
```

## Time Conversion Functions

### `sec(seconds)` → number
Converts seconds to game ticks (multiplies by 20).

Used in effects, delays, etc.

```python
ticks = sec(10)
log str(ticks)  # 200 (ticks)
```

### `int(n)` → number
Explicit integer conversion (floor).

```python
x = int(3.9)
log str(x)  # 3
```

## String Functions

### `len(str)` → number
Returns string length.

```python
length = len("hello")
log str(length)  # 5
```

### `upper(str)` → string
Converts to uppercase.

```python
text = upper("hello")
log text  # "HELLO"
```

### `lower(str)` → string
Converts to lowercase.

```python
text = lower("HELLO")
log text  # "hello"
```

### `contains(str, substring)` → boolean
Checks if string contains substring.

```python
if contains("hello world", "world"):
    log "Found!"
end
```

### `starts_with(str, prefix)` → boolean

```python
if starts_with("minecraft:dirt", "minecraft:"):
    log "Is a Minecraft block"
end
```

### `ends_with(str, suffix)` → boolean

```python
if ends_with("file.txt", ".txt"):
    log "Is a text file"
end
```

### `trim(str)` → string
Removes leading/trailing whitespace.

```python
text = trim("  hello  ")
log text  # "hello"
```

### `replace(str, old, new)` → string
Replace all occurrences.

```python
text = replace("hello world", "world", "SScript")
log text  # "hello SScript"
```

### `substring(str, start[, end])` → string
Extracts substring.

```python
text = substring("hello", 1, 4)
log text  # "ell"

text = substring("hello", 2)
log text  # "llo"
```

### `index_of(str, substring)` → number
Finds position of substring (-1 if not found).

```python
pos = index_of("hello world", "world")
log str(pos)  # 6
```

### `split_get(str, delimiter, index)` → string
Splits string and gets element at index.

```python
part = split_get("a,b,c,d", ",", 2)
log part  # "c"
```

### `split_count(str, delimiter)` → number
Counts how many parts after splitting.

```python
count = split_count("a,b,c,d", ",")
log str(count)  # 4
```

## Type Conversion Functions

### `str(value)` → string
Converts any value to string.

```python
s = str(42)
log s  # "42"

s = str(true)
log s  # "true"
```

### `num(value)` → number
Converts to number.

```python
n = num("42")
log str(n)  # 42

n = num("3.14")
log str(n)  # 3.14

n = num("invalid")
log str(n)  # 0 (fallback)
```

### `bool(value)` → boolean
Converts to boolean. 

- `0`, `""`, `null`, `false` → `false`
- Everything else → `true`

```python
b = bool(1)
log str(b)  # true

b = bool(0)
log str(b)  # false

b = bool("hello")
log str(b)  # true
```

### `type(value)` → string
Returns type name: `"number"`, `"string"`, `"boolean"`, `"object"`, `"list"`, `"null"`.

```python
log type(42)  # "number"
log type("hi")  # "string"
log type([1, 2, 3])  # "list"
```

## JSON Functions

### `json_parse(text)` → object | list | value
Parses JSON string into a ScriptValue.

```python
data = json_parse("{\"name\": \"Steve\", \"level\": 10}")
log data.name  # "Steve"
log str(data.level)  # "10"
```

### `json_stringify(value)` → string
Converts any value to JSON string.

```python
obj = {"name": "Alex", "items": 5}
json_str = json_stringify(obj)
log json_str  # {"name":"Alex","items":5}
```

## HTTP Functions

All HTTP functions are **synchronous** (blocking). Use sparingly to avoid server lag.

### `http_get(url[, headers[, timeout_sec]])` → object
Performs HTTP GET request.

Returns object with: `status` (HTTP code), `ok` (boolean), `body` (response text), `headers` (object), `json` (parsed if JSON).

```python
resp = http_get("https://api.github.com")
if resp.ok:
    log "Status: " + str(resp.status)
    log "Body: " + resp.body
end
```

### `http_post(url, body[, headers[, timeout_sec]])` → object
Performs HTTP POST request.

```python
data = {"key": "value"}
headers = {"Content-Type": "application/json"}
resp = http_post("https://example.com/api", data, headers, 10)
```

### `http_request(method, url[, body[, headers[, timeout_sec]]])` → object
Flexible HTTP request with custom method (GET, POST, PUT, DELETE, PATCH).

```python
resp = http_request("DELETE", "https://example.com/item/123", null, {}, 5)

resp = http_request("PUT", "https://example.com/update", {"value": 42}, {}, 5)
```

**Timeout defaults:**
- Default: 10 seconds
- Minimum: 1 second
- Maximum: 60 seconds (auto-clamped)

## File Functions

All file paths are relative to server's run directory. Absolute paths work as-is.

### `file_exists(path)` → boolean
Checks if file exists.

```python
if file_exists("sscripts/data/save.json"):
    log "Save file exists"
end
```

### `file_read(path)` → string | null
Reads entire file as string. Returns `null` if file doesn't exist.

```python
content = file_read("sscripts/config.txt")
if content != null:
    log content
end
```

### `file_write(path, content)` → boolean
Writes content to file (creates or overwrites).

Creates parent directories automatically.

```python
file_write("sscripts/logs/output.txt", "Hello world!")
```

### `file_append(path, content)` → boolean
Appends content to file (creates if doesn't exist).

```python
file_append("sscripts/logs/chat.log", timestamp + " - " + message + "\n")
```

### `file_delete(path)` → boolean
Deletes a file or empty directory. Returns `true` if successful. **Does NOT delete directories with contents.**

```python
if file_delete("sscripts/temp.txt"):
    log "Deleted"
end
```

### `file_delete_recursive(path)` → boolean
Deletes a file or directory (recursively with all contents). Returns `true` if successful.

```python
# Delete directory and all its files
if file_delete_recursive("sscripts/old_data/"):
    log "Directory and contents deleted"
end
```

### `file_mkdirs(path)` → boolean
Creates directory (and all parent directories).

```python
file_mkdirs("sscripts/data/nested/structure")
```

### `file_read_json(path)` → object | list | null
Reads JSON file. Returns `null` if file doesn't exist or JSON is invalid.

```python
data = file_read_json("sscripts/data/config.json")
if data != null:
    log data.setting1
end
```

### `file_write_json(path, value)` → boolean
Writes value as JSON to file.

```python
obj = {"players": 5, "difficulty": 3, "pvp": true}
file_write_json("sscripts/data/world.json", obj)
```

### `file_rename(old_path, new_path)` → boolean
Renames or moves a file. Returns `true` if successful, `false` if file doesn't exist.

```python
if file_rename("sscripts/old_name.txt", "sscripts/new_name.txt"):
    log "File renamed successfully"
end
```

### `file_list(path)` → list
Returns list of file and directory names in a directory. Useful with loops to delete all files except one.

```python
# Get all files in a directory
files = file_list("sscripts/data/")

# Delete all files except one
for file in files:
    if file != "keep_this.json":
        file_delete("sscripts/data/" + file)
    end
end
```

## List Methods

Lists are accessed via object notation with inline methods.

```python
items = [1, 2, 3]

# Built-in methods
items.add(4)  # Append
items.remove(1)  # Remove at index 1  
size = items.size()  # 3
```

## Global Variables

Persistent key-value storage that survives server restarts.

### `get_global(key)` → value | null
Retrieves a global variable.

```python
value = get_global("players_seen")
```

### `set_global(key, value)` → null
Stores a global variable.

```python
set_global("last_player", "Steve")
```

## Logging & Debug

### `log(message)` → null
Prints message to server console and SScript log file.

```python
log "This is a message"
log player.name + " joined!"
```

## Utility Commands

### `wait(func_name, args...)` → null
Calls a function on the next server tick (asynchronously).

Used in event handlers to defer execution.

```python
func heavy_task():
    log "Running heavy task..."
end

on load:
    wait heavy_task
end
```

### `sleep(ticks)` → null
Pauses execution for N ticks (1 tick = 50ms server time).

```python
on player_join(player):
    log "Player joined"
    sleep 20  # 1 second
    log "1 second later"
end
```

### `try/catch` Block
Error handling.

```python
try:
    bad_value = error_function()
catch err:
    log "Error caught: " + err
end
```
