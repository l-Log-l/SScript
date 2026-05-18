package ai.log.sscript.engine.interpreter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime value in the SScript language.
 * Wraps numbers, strings, booleans, and null.
 */
public class ScriptValue {

    public enum Type {
        NUMBER, STRING, BOOLEAN, LIST, OBJECT, NULL
    }

    private final Type type;
    private final Object value;

    public static final ScriptValue NULL = new ScriptValue(Type.NULL, null);
    public static final ScriptValue TRUE = new ScriptValue(Type.BOOLEAN, true);
    public static final ScriptValue FALSE = new ScriptValue(Type.BOOLEAN, false);

    public ScriptValue(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    public static ScriptValue of(double num) {
        return new ScriptValue(Type.NUMBER, num);
    }

    public static ScriptValue of(String str) {
        return new ScriptValue(Type.STRING, str);
    }

    public static ScriptValue of(boolean bool) {
        return bool ? TRUE : FALSE;
    }

    public static ScriptValue ofList(List<ScriptValue> list) {
        return new ScriptValue(Type.LIST, list);
    }

    public static ScriptValue ofObject(Map<String, ScriptValue> map) {
        return new ScriptValue(Type.OBJECT, map);
    }

    public static ScriptValue fromObject(Object obj) {
        if (obj == null)
            return NULL;
        if (obj instanceof ScriptValue sv)
            return sv;
        if (obj instanceof Boolean b)
            return of(b);
        if (obj instanceof Number n)
            return of(n.doubleValue());
        if (obj instanceof String s)
            return of(s);
        if (obj instanceof List<?> list) {
            List<ScriptValue> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(fromObject(item));
            }
            return ofList(out);
        }
        if (obj instanceof Map<?, ?> map) {
            Map<String, ScriptValue> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), fromObject(e.getValue()));
            }
            return ofObject(out);
        }
        return of(String.valueOf(obj));
    }

    public Type getType() {
        return type;
    }

    public double asNumber() {
        if (type == Type.NUMBER)
            return (double) value;
        if (type == Type.STRING) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        if (type == Type.BOOLEAN)
            return (boolean) value ? 1 : 0;
        if (type == Type.LIST)
            return asList().size();
        if (type == Type.OBJECT)
            return asObject().size();
        return 0;
    }

    public String asString() {
        if (type == Type.NULL)
            return "null";
        if (type == Type.NUMBER) {
            double d = (double) value;
            if (d == (long) d)
                return String.valueOf((long) d);
            return String.valueOf(d);
        }
        if (type == Type.LIST)
            return asList().toString();
        if (type == Type.OBJECT) {
            Map<String, ScriptValue> obj = asObject();
            if (obj.containsKey("name"))
                return obj.get("name").asString();
            if (obj.containsKey("id"))
                return obj.get("id").asString();
            if (obj.containsKey("pos"))
                return obj.get("pos").asString();
            return obj.toString();
        }
        return String.valueOf(value);
    }

    public boolean asBoolean() {
        return switch (type) {
            case NULL -> false;
            case BOOLEAN -> (boolean) value;
            case NUMBER -> (double) value != 0;
            case STRING -> !((String) value).isEmpty();
            case LIST -> !asList().isEmpty();
            case OBJECT -> !asObject().isEmpty();
        };
    }

    @SuppressWarnings("unchecked")
    public List<ScriptValue> asList() {
        if (type == Type.LIST) {
            return (List<ScriptValue>) value;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public Map<String, ScriptValue> asObject() {
        if (type == Type.OBJECT) {
            return (Map<String, ScriptValue>) value;
        }
        return new LinkedHashMap<>();
    }

    public ScriptValue getMember(String name) {
        if (type == Type.OBJECT) {
            return asObject().getOrDefault(name, NULL);
        }
        return NULL;
    }

    public void setMember(String name, ScriptValue memberValue) {
        if (type != Type.OBJECT) {
            throw new IllegalStateException("Cannot assign member on non-object value: " + getTypeName());
        }
        asObject().put(name, memberValue);
    }

    public ScriptValue getIndex(ScriptValue idx) {
        if (type == Type.LIST) {
            int i = (int) idx.asNumber();
            List<ScriptValue> list = asList();
            if (i < 0 || i >= list.size()) {
                return NULL;
            }
            return list.get(i);
        }
        if (type == Type.OBJECT) {
            return asObject().getOrDefault(idx.asString(), NULL);
        }
        return NULL;
    }

    public void setIndex(ScriptValue idx, ScriptValue newValue) {
        if (type == Type.LIST) {
            int i = (int) idx.asNumber();
            List<ScriptValue> list = asList();
            if (i < 0) {
                throw new IllegalStateException("Negative list index: " + i);
            }
            while (i >= list.size()) {
                list.add(NULL);
            }
            list.set(i, newValue);
            return;
        }
        if (type == Type.OBJECT) {
            asObject().put(idx.asString(), newValue);
            return;
        }
        throw new IllegalStateException("Cannot assign index on type: " + getTypeName());
    }

    public Object toJavaObject() {
        return switch (type) {
            case NULL -> null;
            case BOOLEAN -> asBoolean();
            case NUMBER -> asNumber();
            case STRING -> asString();
            case LIST -> {
                List<Object> out = new ArrayList<>();
                for (ScriptValue sv : asList()) {
                    out.add(sv.toJavaObject());
                }
                yield out;
            }
            case OBJECT -> {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<String, ScriptValue> e : asObject().entrySet()) {
                    out.put(e.getKey(), e.getValue().toJavaObject());
                }
                yield out;
            }
        };
    }

    // ─── Arithmetic ────────────────────────────────────────────

    public ScriptValue add(ScriptValue other) {
        // String concatenation if either side is a string
        if (this.type == Type.STRING || other.type == Type.STRING) {
            return ScriptValue.of(this.asString() + other.asString());
        }
        return ScriptValue.of(this.asNumber() + other.asNumber());
    }

    public ScriptValue subtract(ScriptValue other) {
        return ScriptValue.of(this.asNumber() - other.asNumber());
    }

    public ScriptValue multiply(ScriptValue other) {
        return ScriptValue.of(this.asNumber() * other.asNumber());
    }

    public ScriptValue divide(ScriptValue other) {
        double divisor = other.asNumber();
        if (divisor == 0)
            throw new ArithmeticException("Division by zero");
        return ScriptValue.of(this.asNumber() / divisor);
    }

    public ScriptValue modulo(ScriptValue other) {
        double divisor = other.asNumber();
        if (divisor == 0)
            throw new ArithmeticException("Modulo by zero");
        return ScriptValue.of(this.asNumber() % divisor);
    }

    public ScriptValue negate() {
        return ScriptValue.of(-this.asNumber());
    }

    // ─── Comparison ────────────────────────────────────────────

    public ScriptValue eq(ScriptValue other) {
        if (this.type == Type.NULL && other.type == Type.NULL)
            return TRUE;
        if (this.type == Type.NULL || other.type == Type.NULL)
            return FALSE;
        if (this.type == Type.OBJECT && other.type == Type.STRING) {
            ScriptValue id = asObject().get("id");
            if (id != null) {
                return ScriptValue.of(id.asString().equals(other.asString()));
            }
        }
        if (this.type == Type.STRING && other.type == Type.OBJECT) {
            ScriptValue id = other.asObject().get("id");
            if (id != null) {
                return ScriptValue.of(this.asString().equals(id.asString()));
            }
        }
        if (this.type == Type.LIST || other.type == Type.LIST || this.type == Type.OBJECT || other.type == Type.OBJECT) {
            return ScriptValue.of(this.asString().equals(other.asString()));
        }
        if (this.type == Type.STRING || other.type == Type.STRING) {
            return ScriptValue.of(this.asString().equals(other.asString()));
        }
        return ScriptValue.of(this.asNumber() == other.asNumber());
    }

    public ScriptValue neq(ScriptValue other) {
        return ScriptValue.of(!this.eq(other).asBoolean());
    }

    public ScriptValue lt(ScriptValue other) {
        return ScriptValue.of(this.asNumber() < other.asNumber());
    }

    public ScriptValue gt(ScriptValue other) {
        return ScriptValue.of(this.asNumber() > other.asNumber());
    }

    public ScriptValue lte(ScriptValue other) {
        return ScriptValue.of(this.asNumber() <= other.asNumber());
    }

    public ScriptValue gte(ScriptValue other) {
        return ScriptValue.of(this.asNumber() >= other.asNumber());
    }

    public String getTypeName() {
        return switch (type) {
            case NUMBER -> "number";
            case STRING -> "string";
            case BOOLEAN -> "boolean";
            case LIST -> "list";
            case OBJECT -> "object";
            case NULL -> "null";
        };
    }

    @Override
    public String toString() {
        return asString();
    }
}
