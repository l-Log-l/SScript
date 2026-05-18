---
title: Examples
nav_order: 7
parent: EN Home
---

# Examples

## Core example: `run` is the center of SScript

```python
on player_join(player):
    run "say [SScript] " + player.name + " joined"
    run "effect give " + player.name + " minecraft:speed 10 0 true"
    run "tellraw " + player.name + " {\"text\":\"Welcome!\",\"color\":\"green\"}"
end
```

This is the main use case: react in script, execute real server commands with `run`.

## Function + run

```python
func reward(player_name):
    run "give " + player_name + " minecraft:diamond 1"
    run "xp add " + player_name + " 5 levels"
end

on block_break(player, block):
    if block.id == "minecraft:diamond_ore":
        reward(player.name)
    end
end
```

## Chat logger

```python
on player_chat(player, message):
    file_mkdirs("sscripts/logs")
    line = player.name + ": " + message + "\n"
    file_append("sscripts/logs/chat.log", line)
end
```

## Weather sync

```python
func sync_weather():
    headers = {
        "Accept": "application/json",
        "User-Agent": "SScriptWeatherSync/1.0"
    }

    resp = http_get("https://api.open-meteo.com/v1/forecast?latitude=55.75&longitude=37.61&current=temperature_2m", headers, 10)

    if resp.ok:
        file_mkdirs("sscripts/data")
        file_write_json("sscripts/data/weather.json", resp.json)
        log "weather saved"
    else:
        log "weather request failed: " + str(resp.status)
    end
end

on load:
    while true:
        sync_weather()
        sleep 600
    end
end
```

## Block placement reaction

```python
on block_place(player, block):
    run "tellraw " + player.name + " {\"text\":\"Placed: " + block.id + "\"}"
end
```

## Run script from console (`/sscript run`)

```text
/sscript run startup
/sscript run startup function reward Steve
```

- First command runs the whole script file.
- Second command calls one function with arguments.
