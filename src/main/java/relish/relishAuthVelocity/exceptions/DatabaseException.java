package relish.relishAuthVelocity.exceptions;

import relish.relishAuthVelocity.exceptions.PluginException;

public class DatabaseException
extends PluginException {
    private final String operation;
    private final String tableName;

    public DatabaseException(String message) {
        super(PluginException.ErrorCode.DB_QUERY_FAILED, message);
        this.operation = null;
        this.tableName = null;
    }

    public DatabaseException(String message, Throwable cause) {
        super(PluginException.ErrorCode.DB_QUERY_FAILED, message, cause);
        this.operation = null;
        this.tableName = null;
    }

    public DatabaseException(PluginException.ErrorCode errorCode, String message) {
        super(errorCode, message);
        this.operation = null;
        this.tableName = null;
    }

    public DatabaseException(PluginException.ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.operation = null;
        this.tableName = null;
    }

    public DatabaseException(PluginException.ErrorCode errorCode, String message, String operation, String tableName) {
        super(errorCode, message, "Operation: " + operation + ", Table: " + tableName);
        this.operation = operation;
        this.tableName = tableName;
    }

    public DatabaseException(PluginException.ErrorCode errorCode, String message, String operation, String tableName, Throwable cause) {
        super(errorCode, message, "Operation: " + operation + ", Table: " + tableName, cause);
        this.operation = operation;
        this.tableName = tableName;
    }

    public String getOperation() {
        return this.operation;
    }

    public String getTableName() {
        return this.tableName;
    }
}
