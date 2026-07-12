package relish.relishAuthVelocity.exceptions;

public class PluginException
extends RuntimeException {
    private final ErrorCode errorCode;
    private final String context;

    public PluginException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.context = null;
    }

    public PluginException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = null;
    }

    public PluginException(ErrorCode errorCode, String message, String context) {
        super(message);
        this.errorCode = errorCode;
        this.context = context;
    }

    public PluginException(ErrorCode errorCode, String message, String context, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = context;
    }

    public ErrorCode getErrorCode() {
        return this.errorCode;
    }

    public String getContext() {
        return this.context;
    }

    public String getFullMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(this.errorCode.getCode()).append("] ").append(this.getMessage());
        if (this.context != null && !this.context.isEmpty()) {
            sb.append(" (Context: ").append(this.context).append(")");
        }
        return sb.toString();
    }

    public static enum ErrorCode {
        DB_CONNECTION_FAILED("DB1001", "Database connection failed"),
        DB_QUERY_FAILED("DB1002", "Database query failed"),
        DB_USER_NOT_FOUND("DB1003", "User not found in database"),
        DB_SESSION_ERROR("DB1004", "Session operation failed"),
        DB_DUPLICATE_ENTRY("DB1005", "Duplicate entry in database"),
        DB_TRANSACTION_FAILED("DB1006", "Database transaction failed"),
        AUTH_INVALID_CREDENTIALS("AUTH2001", "Invalid credentials"),
        AUTH_USER_LOCKED("AUTH2002", "User account locked"),
        AUTH_SESSION_EXPIRED("AUTH2003", "Session expired"),
        AUTH_NOT_REGISTERED("AUTH2004", "User not registered"),
        AUTH_ALREADY_REGISTERED("AUTH2005", "User already registered"),
        AUTH_DISCORD_LINK_FAILED("AUTH2006", "Discord linking failed"),
        AUTH_PREMIUM_VERIFICATION_FAILED("AUTH2007", "Premium verification failed"),
        CONFIG_LOAD_FAILED("CFG3001", "Configuration load failed"),
        CONFIG_INVALID_VALUE("CFG3002", "Invalid configuration value"),
        CONFIG_MISSING_REQUIRED("CFG3003", "Missing required configuration"),
        CONFIG_VALIDATION_FAILED("CFG3004", "Configuration validation failed"),
        LIMBO_INIT_FAILED("LMB4001", "Limbo initialization failed"),
        LIMBO_SPAWN_FAILED("LMB4002", "Failed to spawn player in limbo"),
        LIMBO_NOT_AVAILABLE("LMB4003", "Limbo system not available"),
        LIMBO_SESSION_ERROR("LMB4004", "Limbo session error"),
        DISCORD_BOT_OFFLINE("DSC5001", "Discord bot is offline"),
        DISCORD_VERIFICATION_FAILED("DSC5002", "Discord verification failed"),
        DISCORD_ALREADY_LINKED("DSC5003", "Discord account already linked"),
        VALIDATION_PASSWORD("VAL6001", "Password validation failed"),
        VALIDATION_USERNAME("VAL6002", "Username validation failed"),
        VALIDATION_INPUT("VAL6003", "Input validation failed"),
        INTERNAL_ERROR("INT9001", "Internal error"),
        INITIALIZATION_FAILED("INT9002", "Initialization failed"),
        RESOURCE_CLEANUP_FAILED("INT9003", "Resource cleanup failed"),
        OPERATION_TIMEOUT("INT9004", "Operation timed out");

        private final String code;
        private final String description;

        private ErrorCode(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return this.code;
        }

        public String getDescription() {
            return this.description;
        }
    }
}
