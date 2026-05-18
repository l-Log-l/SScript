package ai.log.sscript.engine.interpreter;

import java.util.HashMap;
import java.util.Map;

/**
 * Variable scope with parent-chain lookup for lexical scoping.
 */
public class Environment {

    private final Map<String, ScriptValue> variables = new HashMap<>();
    private final Environment parent;

    public Environment() {
        this.parent = null;
    }

    public Environment(Environment parent) {
        this.parent = parent;
    }

    /**
     * Get a variable, walking up the scope chain.
     */
    public ScriptValue get(String name) {
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        if (parent != null) {
            return parent.get(name);
        }
        return null;
    }

    /**
     * Set a variable in the nearest scope that already has it,
     * or in the current scope if it's new.
     */
    public void set(String name, ScriptValue value) {
        Environment target = findOwner(name);
        if (target != null) {
            target.variables.put(name, value);
        } else {
            variables.put(name, value);
        }
    }

    /**
     * Define a variable in the current (local) scope only.
     */
    public void defineLocal(String name, ScriptValue value) {
        variables.put(name, value);
    }

    /**
     * Find which scope owns a variable.
     */
    private Environment findOwner(String name) {
        if (variables.containsKey(name))
            return this;
        if (parent != null)
            return parent.findOwner(name);
        return null;
    }

    /**
     * Get all variables in this scope (for monitor/debug).
     */
    public Map<String, ScriptValue> getAll() {
        Map<String, ScriptValue> all = new HashMap<>();
        if (parent != null) {
            all.putAll(parent.getAll());
        }
        all.putAll(variables);
        return all;
    }
}
