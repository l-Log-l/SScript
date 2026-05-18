---
title: JSON HTTP Файлы
nav_order: 6
parent: РУ Главная
---

# JSON, HTTP и файловый API

## JSON

- `json_parse(text)`
- `json_stringify(value)`

```python
obj = json_parse("{\"ok\":true}")
log str(obj.ok)
```

## HTTP

- `http_get(url[, headers[, timeoutSec]])`
- `http_post(url, body[, headers[, timeoutSec]])`
- `http_request(method, url[, body[, headers[, timeoutSec]]])`

**Объект ответа:** Смотрите [Структуры Данных - HTTP Response Object](data-structures.md#объект-http-response-ответ-http)

Поля: `status`, `ok`, `body`, `headers`, `json`

```python
resp = http_get("https://api.github.com", {"Accept":"application/json"}, 10)
if resp.ok:
    log "status=" + str(resp.status)
    data = resp.json
end
```

## Файлы

- `file_exists(path)`
- `file_read(path)`
- `file_write(path, content)`
- `file_append(path, content)`
- `file_delete(path)` — удаляет файл или пустую директорию
- `file_delete_recursive(path)` — удаляет файл или директорию (со всем содержимым)
- `file_rename(old_path, new_path)`
- `file_list(path)` — возвращает список файлов/директорий
- `file_mkdirs(path)`
- `file_read_json(path)`
- `file_write_json(path, value)`

**Смотрите также:** [Полный справочник - Файловые функции](complete-reference.md#файловые-функции)

```python
file_mkdirs("sscripts/data")
file_write_json("sscripts/data/state.json", {"online": player_count()})

# Переименовать файл
file_rename("sscripts/old.txt", "sscripts/new.txt")

# Перечислить и удалить все файлы кроме одного
files = file_list("sscripts/data/")
for file in files:
    if file != "keep_this.json":
        file_delete("sscripts/data/" + file)
    end
end

# Удалить всю директорию
file_delete_recursive("sscripts/old_data/")
```
