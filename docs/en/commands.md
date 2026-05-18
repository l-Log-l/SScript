---
title: Commands Reference
nav_order: 5
parent: EN Home
---

# SScript Commands Reference

All SScript commands are executed on the server console or by OPs using `/sscript`. Each command manages script execution, monitoring, and debugging.

## Permission Requirements

**All `/sscript` commands require Operator (OP) permission.**

---

## Command Syntax

### 1. Run Scripts/Functions

```
/sscript run <file> [function <name> [args...]]
```

**Purpose:** Execute a script file or call a specific function.

**Parameters:**
- `<file>` — Script filename (with or without `.ss` extension)
  - Example: `startup`, `startup.ss`, `handlers.event.ss`
  - Resolves from `sscripts/` folder
- `function <name>` — (Optional) Call specific function instead of running entire script
  - MUST be defined with `func` or `def` in that file
- `[args...]` — (Optional) Arguments to pass to function

**Examples:**

```
# Run entire startup.ss script
/sscript run startup

# Call greet() function from startup.ss with "Steve" argument
/sscript run startup function greet Steve

# Call calculate() with multiple arguments
/sscript run utils function calculate 10 20
```

**Output:** Creates a new process, logs execution, returns result (if function called).

---

### 2. Monitor Running Processes

```
/sscript monitor [all|<id>]
```

**Purpose:** View status of running or queued processes.

**Parameters:**
- (No args) — Show summary of all running processes
- `all` — Show detailed info for all processes
- `<id>` — Show details for specific process ID

**Example Output:**

```
Running Processes (5 total):
  #1 [RUNNING] startup.ss (started 5s ago)
  #2 [QUEUED]  handler_verify (waiting)
  #3 [RUNNING] fetch_api (95% complete)
  #4 [SLEEPING] sleep_task (1.2s remaining)
  #5 [IDLE] monitor_job
```

---

### 3. Stop Processes

```
/sscript stop [all|<id>|file <file>]
```

**Purpose:** Terminate running or queued processes.

**Parameters:**
- (No args) — Stop last process
- `all` — Stop ALL running processes
- `<id>` — Stop process with ID #<id>
- `file <file>` — Stop all processes from specific file

**Examples:**

```
# Stop process #3
/sscript stop 3

# Stop all currently running processes
/sscript stop all

# Stop all processes from startup.ss
/sscript stop file startup
```

**Output:** Confirmation of stopped processes, cleanup of resources.

---

### 4. Reload Processes

```
/sscript reload [all|<id>|file <file>]
```

**Purpose:** Restart processes (useful for testing code changes).

**Parameters:**
- (No args) — Reload last process
- `all` — Reload ALL running processes
- `<id>` — Reload specific process ID
- `file <file>` — Reload all processes from specific file

**Examples:**

```
# Reload process #2
/sscript reload 2

# Reload all running processes
/sscript reload all

# Reload all handlers from events.event.ss
/sscript reload file events.event.ss
```

---

### 5. Toggle Debug Logging

```
/sscript debug [on|off]
```

**Purpose:** Enable/disable verbose debug output for script execution.

**Parameters:**
- `on` — Enable debug logging (shows every statement, variable assignment)
- `off` — Disable debug logging (normal operation)
- (No args) — Toggle current state

**Example:**

```
/sscript debug on
# Now all script statements are logged in detail

/sscript debug off
# Return to normal logging
```

**Output:**
- When **on**: Every executed line, variable changes, function calls logged
- Useful for: Debugging logic errors, tracing execution flow, performance analysis

---

### 6. Reload Event Handlers

```
/sscript events reload
```

**Purpose:** Reload all `.event.ss` files without restarting server.

**Use Case:** Testing event handler changes without full server restart.

**Output:** Re-registers all event handlers from `sscripts/*.event.ss` files.

---

## Process ID Reference

Each running script is assigned a **Process ID** (e.g., `#1`, `#2`) for tracking.

**Process Lifecycle:**

```
QUEUED → RUNNING → DONE/SLEEPING → (potential RESUME)
```

**States:**
- `QUEUED` — Waiting to execute on next tick
- `RUNNING` — Currently executing
- `SLEEPING` — Paused by `sleep N` command
- `DONE` — Completed successfully
- `ERROR` — Failed with exception

---

## Practical Examples

### Example 1: Load Player Data Module

```
/sscript run player_data function initialize_all_players
```

This calls `initialize_all_players()` from `player_data.ss`, passes through to all connected players.

---

### Example 2: Monitor Long-Running Task

```
/sscript run bulk_export function export_stats
/sscript monitor 1
/sscript monitor 1
# (check progress multiple times)
```

---

### Example 3: Debug Event Handler Issue

```
/sscript debug on
/sscript events reload
# (watch console output in detail)
/sscript debug off
```

---

### Example 4: Graceful Shutdown

```
/sscript stop all
# (ensures no scripts are running before server shutdown)
```

---

## Limits & Constraints

| Constraint | Value | Impact |
|-----------|-------|--------|
| Max running processes | 500 | Cannot spawn more than 500 processes |
| Processes created per tick | 20 | Max 20 new processes/tick to prevent lag |
| Statements per tick | 50 | Each process executes max 50 statements/tick |
| Loop iterations | 1,000,000 | Infinite loops break after 1M iterations |
| While loop guard | Enabled | Prevents infinite loops |
| HTTP timeout | 60 seconds | Long requests abort after 60s |
| Global variable save | 2s debounce | Saves to disk at most every 2 seconds |

---

## Error Handling

### What Happens if Command Fails?

1. **Invalid file:** `error: Script file not found: startup.ss`
2. **Permission denied:** `error: Operator permission required`
3. **Function not found:** `error: Function greet not defined in startup.ss`
4. **Process already stopped:** `info: Process #5 already completed`

### Debug Script Errors

```
/sscript debug on
/sscript run startup
# Watch console for detailed error trace
```

---

## Advanced Usage

### Chaining Commands

```
/sscript stop all
/sscript events reload
/sscript run startup
```

This safely resets all state before reloading.

### Monitoring Before Stopping

```
/sscript monitor all
/sscript stop all
```

Check what's running before you stop it.

### Performance Profiling

```
/sscript debug on
/sscript run startup
# (execute and watch tick times)
/sscript debug off
```

---

## See Also

- [Functions & Async Mechanics](functions-mechanics.md) — How functions execute
- [Complete Events Reference](complete-events.md) — Event handler details
- [Complete Built-in Reference](complete-reference.md) — All 60+ functions
