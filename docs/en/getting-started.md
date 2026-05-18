---
title: Getting Started
nav_order: 2
parent: EN Home
---

# Getting Started

## 1. Script folders

- Runtime scripts: `sscripts/`
- Event handlers should be in: `*.event.ss`

## 2. Minimal event script

```python
on player_join(player):
    log "Welcome " + player.name
end
```

## 3. The main thing: `run` command execution

Use `run "..."` inside scripts to execute Minecraft commands.

```python
on player_join(player):
    run "say " + player.name + " joined the server"
    run "give " + player.name + " minecraft:bread 3"
end
```

This is the primary SScript workflow: script logic decides what to do, then `run` executes server commands.

## 4. Function placement rule

Define reusable functions at top-level:

```python
func hello(name):
    return "Hi " + name
end

on load:
    log hello("Server")
end
```

Do not define `func` inside an event block if you need to call it globally.

## 5. Running scripts from console

Use `/sscript run` to launch script files manually:

```
/sscript run startup
/sscript run startup function hello Steve
```

## 6. Reloading

- Restart server or use your reload command flow.
- If event did not fire, confirm file name ends with `.event.ss`.
