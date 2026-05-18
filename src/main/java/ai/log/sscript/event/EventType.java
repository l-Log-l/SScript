package ai.log.sscript.event;

public enum EventType {
    LOAD("load"),
    PLAYER_JOIN("player_join"),
    PLAYER_DEATH("player_death"),
    PLAYER_DEAD("player_dead"),
    PLAYER_CHAT("player_chat");

    private final String scriptName;

    EventType(String scriptName) {
        this.scriptName = scriptName;
    }

    public String getScriptName() {
        return scriptName;
    }

    public static EventType fromName(String name) {
        for (EventType type : values()) {
            if (type.scriptName.equals(name)) {
                return type;
            }
        }
        return null;
    }
}
