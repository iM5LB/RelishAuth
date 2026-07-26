package relish.relishAuthVelocity.database.schema;

import relish.relishAuthVelocity.config.Config;

public class SchemaConfig {
    private final Config config;
    public String usersTable;
    public String sessionsTable;
    public String usersUuid;
    public String usersUsername;
    public String usersDiscordId;
    public String usersFirstLogin;
    public String usersLastLogin;
    public String usersIpAddress;
    public String usersPassword;
    public String usersAccountType;
    public String usersJoinNotifications;
    public String usersCreatedAt;
    public String usersSessionDuration;
    public String sessionsUuid;
    public String sessionsDiscordId;
    public String sessionsLastSeen;
    public String sessionsIpAddress;

    public SchemaConfig(Config config) {
        this.config = config;
        this.loadFromConfig();
    }

    public void loadFromConfig() {
        this.usersTable = this.config.getString("database.schema.users.table", "users");
        this.sessionsTable = this.config.getString("database.schema.sessions.table", "sessions");
        this.usersUuid = this.config.getString("database.schema.users.columns.uuid", "uuid");
        this.usersUsername = this.config.getString("database.schema.users.columns.username", "username");
        this.usersDiscordId = this.config.getString("database.schema.users.columns.discord_id", "discord_id");
        this.usersFirstLogin = this.config.getString("database.schema.users.columns.first_login", "first_login");
        this.usersLastLogin = this.config.getString("database.schema.users.columns.last_login", "last_login");
        this.usersIpAddress = this.config.getString("database.schema.users.columns.ip_address", "ip_address");
        this.usersPassword = this.config.getString("database.schema.users.columns.password", "password");
        this.usersAccountType = this.config.getString("database.schema.users.columns.account_type", "account_type");
        this.usersJoinNotifications = this.config.getString("database.schema.users.columns.join_notifications", "join_notifications");
        this.usersCreatedAt = this.config.getString("database.schema.users.columns.created_at", "created_at");
        this.usersSessionDuration = this.config.getString("database.schema.users.columns.session_duration", "session_duration");
        this.sessionsUuid = this.config.getString("database.schema.sessions.columns.uuid", "uuid");
        this.sessionsDiscordId = this.config.getString("database.schema.sessions.columns.discord_id", "discord_id");
        this.sessionsLastSeen = this.config.getString("database.schema.sessions.columns.last_seen", "last_seen");
        this.sessionsIpAddress = this.config.getString("database.schema.sessions.columns.ip_address", "ip_address");
    }

    public String buildUsersTableCreationSQL(boolean isMySQL) {
        return this.buildUsersTableCreationSQL(isMySQL, false);
    }

    public String buildUsersTableCreationSQL(boolean isMySQL, boolean isPostgreSQL) {
        if (isMySQL) {
            return String.format("    CREATE TABLE IF NOT EXISTS %s (\n        %s VARCHAR(36) PRIMARY KEY,\n        %s VARCHAR(32) NOT NULL,\n        %s VARCHAR(60) UNIQUE,\n        %s BIGINT NOT NULL,\n        %s BIGINT NOT NULL,\n        %s VARCHAR(45),\n        %s VARCHAR(255),\n        %s VARCHAR(10) NOT NULL DEFAULT 'CRACKED',\n        %s BOOLEAN DEFAULT TRUE,\n        %s BIGINT NOT NULL,\n        %s VARCHAR(10) DEFAULT '1h',\n        %s TEXT\n    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci\n", this.usersTable, this.usersUuid, this.usersUsername, this.usersDiscordId, this.usersFirstLogin, this.usersLastLogin, this.usersIpAddress, this.usersPassword, this.usersAccountType, this.usersJoinNotifications, this.usersCreatedAt, this.usersSessionDuration, "skin_data");
        }
        if (isPostgreSQL) {
            return String.format("    CREATE TABLE IF NOT EXISTS %s (\n        %s VARCHAR(36) PRIMARY KEY,\n        %s VARCHAR(32) NOT NULL,\n        %s VARCHAR(60) UNIQUE,\n        %s BIGINT NOT NULL,\n        %s BIGINT NOT NULL,\n        %s VARCHAR(45),\n        %s VARCHAR(255),\n        %s VARCHAR(10) NOT NULL DEFAULT 'CRACKED',\n        %s BOOLEAN DEFAULT TRUE,\n        %s BIGINT NOT NULL,\n        %s VARCHAR(10) DEFAULT '1h',\n        %s TEXT\n    )\n", this.usersTable, this.usersUuid, this.usersUsername, this.usersDiscordId, this.usersFirstLogin, this.usersLastLogin, this.usersIpAddress, this.usersPassword, this.usersAccountType, this.usersJoinNotifications, this.usersCreatedAt, this.usersSessionDuration, "skin_data");
        }
        return String.format("    CREATE TABLE IF NOT EXISTS %s (\n        %s TEXT PRIMARY KEY,\n        %s TEXT NOT NULL,\n        %s TEXT UNIQUE,\n        %s INTEGER NOT NULL,\n        %s INTEGER NOT NULL,\n        %s TEXT,\n        %s TEXT,\n        %s TEXT NOT NULL DEFAULT 'CRACKED',\n        %s INTEGER DEFAULT 1,\n        %s INTEGER NOT NULL,\n        %s TEXT DEFAULT '1h',\n        %s TEXT\n    )\n", this.usersTable, this.usersUuid, this.usersUsername, this.usersDiscordId, this.usersFirstLogin, this.usersLastLogin, this.usersIpAddress, this.usersPassword, this.usersAccountType, this.usersJoinNotifications, this.usersCreatedAt, this.usersSessionDuration, "skin_data");
    }

    public String buildSessionsTableCreationSQL(boolean isMySQL) {
        return this.buildSessionsTableCreationSQL(isMySQL, false);
    }

    public String buildSessionsTableCreationSQL(boolean isMySQL, boolean isPostgreSQL) {
        if (isMySQL) {
            return String.format("    CREATE TABLE IF NOT EXISTS %s (\n        %s VARCHAR(36) PRIMARY KEY,\n        %s VARCHAR(60),\n        %s BIGINT NOT NULL,\n        %s VARCHAR(45),\n        INDEX idx_sessions_discord_id (%s)\n    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci\n", this.sessionsTable, this.sessionsUuid, this.sessionsDiscordId, this.sessionsLastSeen, this.sessionsIpAddress, this.sessionsDiscordId);
        }
        if (isPostgreSQL) {
            return String.format("    CREATE TABLE IF NOT EXISTS %s (\n        %s VARCHAR(36) PRIMARY KEY,\n        %s VARCHAR(60),\n        %s BIGINT NOT NULL,\n        %s VARCHAR(45)\n    )\n", this.sessionsTable, this.sessionsUuid, this.sessionsDiscordId, this.sessionsLastSeen, this.sessionsIpAddress);
        }
        return String.format("    CREATE TABLE IF NOT EXISTS %s (\n        %s TEXT PRIMARY KEY,\n        %s TEXT,\n        %s INTEGER NOT NULL,\n        %s TEXT\n    )\n", this.sessionsTable, this.sessionsUuid, this.sessionsDiscordId, this.sessionsLastSeen, this.sessionsIpAddress);
    }

    public String buildUsersIndicesSQL(boolean isMySQL) {
        if (isMySQL) {
            return "";
        }
        return String.format("    CREATE INDEX IF NOT EXISTS idx_%s_discord_id ON %s(%s);\n    CREATE INDEX IF NOT EXISTS idx_%s_ip_address ON %s(%s);\n    CREATE INDEX IF NOT EXISTS idx_%s_username ON %s(%s);\n    CREATE INDEX IF NOT EXISTS idx_%s_account_type ON %s(%s);\n    CREATE INDEX IF NOT EXISTS idx_%s_uuid ON %s(%s);\n", this.usersTable, this.usersTable, this.usersDiscordId, this.usersTable, this.usersTable, this.usersIpAddress, this.usersTable, this.usersTable, this.usersUsername, this.usersTable, this.usersTable, this.usersAccountType, this.usersTable, this.usersTable, this.usersUuid);
    }

    public String buildSessionsIndicesSQL(boolean isMySQL) {
        if (isMySQL) {
            return "";
        }
        return String.format("    CREATE INDEX IF NOT EXISTS idx_%s_discord_id ON %s(%s);\n    CREATE INDEX IF NOT EXISTS idx_%s_last_seen ON %s(%s);\n    CREATE INDEX IF NOT EXISTS idx_%s_uuid_last_seen ON %s(%s, %s);\n", this.sessionsTable, this.sessionsTable, this.sessionsDiscordId, this.sessionsTable, this.sessionsTable, this.sessionsLastSeen, this.sessionsTable, this.sessionsTable, this.sessionsUuid, this.sessionsLastSeen);
    }
}
