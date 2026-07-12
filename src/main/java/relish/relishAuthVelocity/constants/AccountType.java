package relish.relishAuthVelocity.constants;

public enum AccountType {
    PREMIUM("PREMIUM"),
    CRACKED("CRACKED"),
    UNLINKED("UNLINKED");

    private final String value;

    private AccountType(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static AccountType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return CRACKED;
        }
        for (AccountType type : AccountType.values()) {
            if (!type.value.equalsIgnoreCase(value)) continue;
            return type;
        }
        return CRACKED;
    }

    public String toString() {
        return this.value;
    }
}
