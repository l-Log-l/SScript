---
title: Примеры
nav_order: 7
parent: РУ Главная
---

# Примеры

Пример `.mixin.ss` теперь лежит в [run/sscripts/mixin_example.mixin.ss](run/sscripts/mixin_example.mixin.ss).

## Базовый пример: `run` это центр SScript

```python
on player_join(player):
    run "say [SScript] " + player.name + " зашел"
    run "effect give " + player.name + " minecraft:speed 10 0 true"
    run "tellraw " + player.name + " {\"text\":\"Добро пожаловать!\",\"color\":\"green\"}"
end
```

Это главный сценарий: скрипт реагирует на событие, `run` исполняет реальные серверные команды.

## Функция + run

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

## Лог чата

```python
on player_chat(player, message):
    file_mkdirs("sscripts/logs")
    line = player.name + ": " + message + "\n"
    file_append("sscripts/logs/chat.log", line)
end
```

## Синхронизация погоды

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

## Реакция на установку блока

```python
on block_place(player, block):
    run "tellraw " + player.name + " {\"text\":\"Поставлен блок: " + block.id + "\"}"
end
```

## Запуск скрипта из консоли (`/sscript run`)

```text
/sscript run startup
/sscript run startup function reward Steve
```

- Первая команда запускает весь файл скрипта.
- Вторая вызывает конкретную функцию с аргументами.
