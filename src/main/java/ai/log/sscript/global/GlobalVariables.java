package ai.log.sscript.global;

import ai.log.sscript.SScript;
import ai.log.sscript.engine.interpreter.ScriptValue;
import com.google.gson.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persistent global variables stored as JSON on disk.
 */
public class GlobalVariables {

    private static GlobalVariables instance;

    private final Map<String, ScriptValue> globals = new ConcurrentHashMap<>();
    private final Path filePath;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private volatile boolean dirty = false;
    private volatile long lastSaveMs = 0;
    private static final long SAVE_DEBOUNCE_MS = 2000;

    public static final AtomicLong METRIC_SAVES = new AtomicLong();
    public static final AtomicLong METRIC_SAVE_SKIPPED = new AtomicLong();

    public GlobalVariables(Path serverDir) {
        this.filePath = serverDir.resolve("sscripts").resolve("globals.json");
    }

    public static void init(Path serverDir) {
        instance = new GlobalVariables(serverDir);
        instance.load();
        SScript.LOGGER.info("[SScript] Global variables loaded ({} entries)", instance.globals.size());
    }

    public static GlobalVariables getInstance() {
        return instance;
    }

    // ─── Get / Set ─────────────────────────────────────────────

    public ScriptValue get(String key) {
        return globals.getOrDefault(key, ScriptValue.NULL);
    }

    public void set(String key, ScriptValue value) {
        globals.put(key, value);
        dirty = true;
    }

    public void flushIfDirty() {
        if (!dirty) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastSaveMs < SAVE_DEBOUNCE_MS) {
            METRIC_SAVE_SKIPPED.incrementAndGet();
            return;
        }

        dirty = false;
        lastSaveMs = now;
        save();
    }

    public void forceSave() {
        if (!dirty) {
            return;
        }
        dirty = false;
        lastSaveMs = System.currentTimeMillis();
        save();
    }

    // ─── Persistence ───────────────────────────────────────────

    public void load() {
        if (!Files.exists(filePath))
            return;

        try {
            String json = Files.readString(filePath);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            globals.clear();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                globals.put(entry.getKey(), jsonToValue(entry.getValue()));
            }
        } catch (Exception e) {
            SScript.LOGGER.error("[SScript] Failed to load globals.json: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            JsonObject obj = new JsonObject();
            for (Map.Entry<String, ScriptValue> entry : globals.entrySet()) {
                obj.add(entry.getKey(), valueToJson(entry.getValue()));
            }

            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, GSON.toJson(obj));
            METRIC_SAVES.incrementAndGet();
        } catch (IOException e) {
            SScript.LOGGER.error("[SScript] Failed to save globals.json: {}", e.getMessage());
        }
    }

    // ─── JSON conversion ───────────────────────────────────────

    private JsonElement valueToJson(ScriptValue value) {
        return switch (value.getType()) {
            case NUMBER -> new JsonPrimitive(value.asNumber());
            case STRING -> new JsonPrimitive(value.asString());
            case BOOLEAN -> new JsonPrimitive(value.asBoolean());
            case LIST -> {
                JsonArray arr = new JsonArray();
                for (ScriptValue item : value.asList()) {
                    arr.add(valueToJson(item));
                }
                yield arr;
            }
            case OBJECT -> {
                JsonObject obj = new JsonObject();
                for (Map.Entry<String, ScriptValue> entry : value.asObject().entrySet()) {
                    obj.add(entry.getKey(), valueToJson(entry.getValue()));
                }
                yield obj;
            }
            case NULL -> JsonNull.INSTANCE;
        };
    }

    private ScriptValue jsonToValue(JsonElement element) {
        if (element.isJsonNull())
            return ScriptValue.NULL;
        if (element.isJsonPrimitive()) {
            JsonPrimitive prim = element.getAsJsonPrimitive();
            if (prim.isBoolean())
                return ScriptValue.of(prim.getAsBoolean());
            if (prim.isNumber())
                return ScriptValue.of(prim.getAsDouble());
            return ScriptValue.of(prim.getAsString());
        }
        if (element.isJsonArray()) {
            List<ScriptValue> out = new ArrayList<>();
            for (JsonElement jsonElement : element.getAsJsonArray()) {
                out.add(jsonToValue(jsonElement));
            }
            return ScriptValue.ofList(out);
        }
        if (element.isJsonObject()) {
            Map<String, ScriptValue> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                out.put(entry.getKey(), jsonToValue(entry.getValue()));
            }
            return ScriptValue.ofObject(out);
        }
        return ScriptValue.NULL;
    }
}
