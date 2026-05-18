---
title: JSON HTTP File API
nav_order: 6
parent: EN Home
---

# JSON, HTTP, and File API

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

**Response object:** See [Data Structures - HTTP Response Object](data-structures.md#http-response-object)

Fields: `status`, `ok`, `body`, `headers`, `json`

```python
resp = http_get("https://api.github.com", {"Accept":"application/json"}, 10)
if resp.ok:
    log "status=" + str(resp.status)
    data = resp.json
end
```

## Files

- `file_exists(path)`
- `file_read(path)`
- `file_write(path, content)`
- `file_append(path, content)`
- `file_delete(path)` — deletes file or empty directory
- `file_delete_recursive(path)` — deletes file or directory (with all contents)
- `file_rename(old_path, new_path)`
- `file_list(path)` — returns list of files/directories
- `file_mkdirs(path)`
- `file_read_json(path)`
- `file_write_json(path, value)`

**See also:** [Complete Built-in Reference - File Functions](complete-reference.md#file-functions)

```python
file_mkdirs("sscripts/data")
file_write_json("sscripts/data/state.json", {"online": player_count()})

# Rename file
file_rename("sscripts/old.txt", "sscripts/new.txt")

# List and delete all files except one
files = file_list("sscripts/data/")
for file in files:
    if file != "keep_this.json":
        file_delete("sscripts/data/" + file)
    end
end

# Delete entire directory
file_delete_recursive("sscripts/old_data/")
```
