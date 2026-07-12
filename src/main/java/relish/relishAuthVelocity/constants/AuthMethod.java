package relish.relishAuthVelocity.constants;

public enum AuthMethod {
    PASSWORD("password"),
    DISCORD("discord");

    private final String value;

    private AuthMethod(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static AuthMethod fromString(String value) {
        if (value == null || value.isEmpty()) {
            return PASSWORD;
        }
        for (AuthMethod method : AuthMethod.values()) {
            if (!method.value.equalsIgnoreCase(value)) continue;
            return method;
        }
        return PASSWORD;
    }

    public String toString() {
        return this.value;
    }
}
