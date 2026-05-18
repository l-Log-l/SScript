---
title: Architecture & Advanced
nav_order: 10
parent: EN Home
---

# SScript Architecture & Advanced Topics

## Engine Architecture

### Core Components

```
┌─────────────────────────────────────────┐
│         Minecraft Server                │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │  SScript Mod (ModInitializer)     │  │
│  │  - Registers commands             │  │
│  │  - Registers events (Fabric API)  │  │
│  │  - Initializes subsystems         │  │
│  └──────────┬────────────────────────┘  │
│             │                           │
│  ┌──────────┴──────────┬──────────────┐ │
│  │                     │              │ │
│  ▼                     ▼              ▼ │
│ Lexer              Parser          Interpreter
│ (tokens)           (AST)      (execution engine)
│                                        │
│  ┌──────────────────────────────────┐  │
│  │   EventManager                   │  │
│  │   - Event registration           │  │
│  │   - Event dispatching            │  │
│  │   - Handler queuing              │  │
│  └──────────────────────────────────┘  │
│             │                           │
│  ┌──────────┴──────────────────────┐   │
│  │   ProcessScheduler               │   │
│  │   - Tick-based execution         │   │
│  │   - Process forking              │   │
│  │   - Async wrapping               │   │
│  └──────────────────────────────────┘   │
│                                         │
│  ┌──────────┬──────────┬───────────┐   │
│  │          │          │           │   │
│  ▼          ▼          ▼           ▼   │
│ Mixins  GlobalVars  ScriptLoader Commands
│                                         │
└─────────────────────────────────────────┘
```

### File Structure

```
src/main/java/ai/log/sscript/
├── SScript.java
│   └─ ModInitializer, event registration
├── engine/
│   ├── lexer/
│   │   └─ Lexer.java (tokenization)
│   ├── parser/
│   │   ├── Parser.java (AST building)
│   │   └─ ASTNode.java (node definitions)
│   └── interpreter/
│       ├── Interpreter.java (execution, 79+ builtins)
│       ├── Environment.java (variable scoping)
│       └─ ScriptValue.java (value wrapper)
├── event/
│   ├── EventManager.java (registration & dispatch)
│   └─ EventType.java (event enum)
├── mixin/
│   ├── ServerPlayerEntityMixin.java (player events)
│   └─ ServerPlayerInteractionManagerMixin.java (block events)
├── runtime/
│   ├── ProcessScheduler.java (tick-based execution)
│   └─ ScriptProcess.java (individual process)
├── global/
│   └─ GlobalVariables.java (persistent KV store)
└── util/
    ├── ScriptLoader.java (file I/O)
    └─ ErrorHelper.java (validation)
```

## Execution Model

### Linear Execution (`.ss` files)

```
1. Lexer tokenizes source code
2. Parser builds AST
3. Interpreter.execute(program):
   a. First pass: register all func definitions
   b. Second pass: execute top-level statements
4. Completion
```

### Event Handler Execution (`.event.ss` files)

```
1. Script loading:
   - Lexer → Parser → AST
   - Register all func definitions
   - Extract OnEventNode handlers
   - Store in EventManager

2. Event firing (when event occurs):
   - EventManager.fire("event_name", server, ...args)
   - For each registered handler:
     a. Create new Environment with event params
     b. Create new Interpreter (shares functions)
     c. Create ScriptProcess with handler body
     d. Submit to ProcessScheduler

3. Tick-based execution (ProcessScheduler.tick()):
   - For each active process:
     a. Execute next statement
     b. Handle control flow (break, continue, return)
     c. Process page breaks (loop iterations)
     d. Mark process done when complete
```

### Async Patterns

#### Pattern A: `wait` Function Call

```
Event fires
    ↓
call wait(func_name, args)
    ↓
Spawns child ScriptProcess
    ↓
Returns AwaitChildException
    ↓
Parent process suspends
    ↓
Next tick: child process runs
    ↓
Child completes → parent resumes
```

#### Pattern B: `sleep` in Handler

```
on player_join(player):
    log "joined"      // Tick 1
    sleep 20          // Request 20 tick pause
    log "after wait"  // Tick 21
end
```

Internal representation:
- `SleepNode` sets pause counter
- ProcessScheduler decrements each tick
- Resumes when counter reaches 0

## Script Validation

Before loading, ErrorHelper validates:

```
✓ Syntax (parser doesn't reject)
✓ Critical errors detection:
  - Invalid control flow
  - Undefined functions
  - Type mismatches
✓ Warnings (loaded anyway):
  - Unused variables
  - Unreachable code
  - Shadowed names
```

## Global Variables

Persistent storage: `sscripts/globals.json`

```
set_global("session_data", {"players": 5})
value = get_global("session_data")

// Saved to disk automatically
// Survives server restarts
```

**Implementation:**
- Backed by JSON file
- Lazy-loaded at startup
- Auto-saved on server stop
- Accessible from any script

## Performance Considerations

### Safe Limits

| Operation | Limit | Reason |
|-----------|-------|--------|
| Loop iterations | 100,000 | Prevent server freeze |
| HTTP timeout | 60s max | Resource limit |
| File operations | Unlimited | NIO is fast |
| Process queue | 1,000 | Memory/CPU |

### Optimization Tips

✅ **Do:**
- Use `wait` for heavy operations
- Cache block lookups
- Query players once per event
- Use global vars for persistence

❌ **Avoid:**
- Synchronous HTTP in event handlers
- Large file writes in loops
- Frequent `get_targets()` calls
- Nested loops with get_blocks()

## Mixin Points

SScript uses Fabric Mixins to inject event hooks without patching Minecraft:

### ServerPlayerEntityMixin

Hooks into player methods:

```java
@Inject into onDeath()         → player_death event
@Inject into copyFrom()        → player_respawn event
@Inject into trySleep()        → player_sleep_attempt, player_sleep
```

### ServerPlayerInteractionManagerMixin

Hooks into interaction:

```java
@Inject into tryBreakBlock()   → block_break event
@Inject into interactBlock()   → block_interact, block_place events
```

### Fabric Events (in SScript.java)

Direct Fabric event listeners:

```java
ServerPlayConnectionEvents.INIT       → player_connect event
ServerPlayConnectionEvents.JOIN       → player_join event
ServerMessageEvents.CHAT_MESSAGE      → player_chat event
ServerLifecycleEvents.SERVER_STARTED  → System init
ServerLifecycleEvents.SERVER_STOPPED  → Cleanup
```

## Error Handling

### Try/Catch

```python
try:
    result = risky_operation()
catch err:
    log "Caught: " + err
    result = default_value
end
```

Catches:
- Runtime errors (API failures)
- Null pointer references
- Type conversion failures
- File I/O errors

### Server logging

All errors logged to:
- Server console (colored)
- `logs/latest.log` (server log)
- `logs/sscript/` (SScript logs)

## Testing & Debugging

### Debug Script

```python
# test_all.event.ss
on load:
    log "=== Starting tests ==="
    file_mkdirs("sscripts/test_output")
end

on player_join(player):
    log "Player joined: " + player.name
    
    # Test JSON
    obj = json_parse("{\"test\": true}")
    file_write_json("sscripts/test_output/player.json", {
        "name": player.name,
        "uuid": player.uuid,
        "health": player.health
    })
