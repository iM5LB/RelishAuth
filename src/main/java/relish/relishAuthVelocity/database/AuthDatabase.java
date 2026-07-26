package relish.relishAuthVelocity.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import relish.relishAuthVelocity.config.Config;
import relish.relishAuthVelocity.database.schema.SchemaConfig;
import relish.relishAuthVelocity.exceptions.DatabaseException;
import relish.relishAuthVelocity.exceptions.PluginException;
import relish.relishAuthVelocity.models.PlayerSession;
import relish.relishAuthVelocity.models.User;
import relish.relishAuthVelocity.utils.DurationUtil;

public class AuthDatabase {
    private final HikariDataSource dataSource;
    private final boolean isMySQL;
    private final boolean isPostgreSQL;
    private final SchemaConfig schema;
    private final Config config;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 100L;

    public AuthDatabase(Config config, Path dataDirectory) throws SQLException {
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        Objects.requireNonNull(dataDirectory, "Data directory cannot be null");
        String dbType = config.getString("database.type", "sqlite").toLowerCase();
        this.isMySQL = dbType.equals("mysql") || dbType.equals("mariadb");
        this.isPostgreSQL = dbType.equals("postgresql") || dbType.equals("postgres");
        this.schema = new SchemaConfig(config);
        HikariConfig hikariConfig = this.createHikariConfig(config, dataDirectory, dbType);
        try {
            this.dataSource = new HikariDataSource(hikariConfig);
            this.validateConnection();
            this.initializeTables();
        }
        catch (SQLException e) {
            throw new DatabaseException(PluginException.ErrorCode.DB_CONNECTION_FAILED, "Failed to initialize database connection: " + e.getMessage(), e);
        }
    }

    private HikariConfig createHikariConfig(Config config, Path dataDirectory, String dbType) {
        HikariConfig hikariConfig = new HikariConfig();
        if (this.isMySQL) {
            this.configureMySQL(hikariConfig, config);
        } else if (dbType.equals("postgresql") || dbType.equals("postgres")) {
            this.configurePostgreSQL(hikariConfig, config);
        } else {
            this.configureSQLite(hikariConfig, config, dataDirectory);
        }
        hikariConfig.setConnectionTimeout(config.getInt("database.mysql.pool.connection-timeout", 30000));
        hikariConfig.setIdleTimeout(config.getInt("database.mysql.pool.idle-timeout", 600000));
        hikariConfig.setMaxLifetime(config.getInt("database.mysql.pool.max-lifetime", 1800000));
        hikariConfig.setPoolName("RelishAuth-Pool");
        return hikariConfig;
    }

    private void configureMySQL(HikariConfig hikariConfig, Config config) {
        this.ensureDriverPresent("com.mysql.cj.jdbc.Driver", "MySQL/MariaDB (database.type=mysql|mariadb). Use the full RelishAuth jar if you're on the slim build.");
        String host = config.getString("database.mysql.host", "localhost");
        int port = config.getInt("database.mysql.port", 3306);
        String database = config.getString("database.mysql.database", "relishAuth");
        String username = config.getString("database.mysql.username", "root");
        String password = config.getString("database.mysql.password", "");
        boolean useSSL = config.getBoolean("database.mysql.use-ssl", false);
        hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=%b&allowPublicKeyRetrieval=true&autoReconnect=true&characterEncoding=utf8", host, port, database, useSSL));
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setMaximumPoolSize(config.getInt("database.mysql.pool.maximum-pool-size", 10));
        hikariConfig.setMinimumIdle(config.getInt("database.mysql.pool.minimum-idle", 5));
    }

    private void configurePostgreSQL(HikariConfig hikariConfig, Config config) {
        this.ensureDriverPresent("org.postgresql.Driver", "PostgreSQL (database.type=postgresql). Use the full RelishAuth jar if you're on the slim build.");
        String host = config.getString("database.postgresql.host", "localhost");
        int port = config.getInt("database.postgresql.port", 5432);
        String database = config.getString("database.postgresql.database", "relishauth");
        String username = config.getString("database.postgresql.username", "postgres");
        String password = config.getString("database.postgresql.password", "");
        boolean useSSL = config.getBoolean("database.postgresql.use-ssl", false);
        hikariConfig.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s", host, port, database, useSSL ? "require" : "disable"));
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName("org.postgresql.Driver");
        hikariConfig.setMaximumPoolSize(config.getInt("database.postgresql.pool.maximum-pool-size", 10));
        hikariConfig.setMinimumIdle(config.getInt("database.postgresql.pool.minimum-idle", 5));
    }

    private void ensureDriverPresent(String driverClassName, String guidance) {
        try {
            Class.forName(driverClassName, false, AuthDatabase.class.getClassLoader());
        }
        catch (ClassNotFoundException e) {
            throw new IllegalStateException("Database driver not found: " + driverClassName + ". " + guidance, e);
        }
    }

    private void configureSQLite(HikariConfig hikariConfig, Config config, Path dataDirectory) {
        File dbFile;
        File parentDir;
        String dbPath = config.getString("database.sqlite.path", dataDirectory.resolve("data.db").toString());
        if (!(dbPath.startsWith("/") || dbPath.startsWith("plugins/") || dbPath.contains(":"))) {
            dbPath = dataDirectory.resolve("data.db").toString();
        }
        if ((parentDir = (dbFile = new File(dbPath)).getParentFile()) != null && !parentDir.exists()) {
            try {
                Files.createDirectories(parentDir.toPath(), new FileAttribute[0]);
            }
            catch (Exception e) {
                throw new DatabaseException(PluginException.ErrorCode.DB_CONNECTION_FAILED, "Failed to create database directory: " + parentDir.getAbsolutePath(), e);
            }
        }
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbPath);
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setMaximumPoolSize(1);
    }

    private void validateConnection() throws SQLException {
        try (Connection conn = this.dataSource.getConnection();){
            if (!conn.isValid(5)) {
                throw new SQLException("Database connection validation failed");
            }
        }
    }

    private void initializeTables() throws SQLException {
        this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 Statement stmt = conn.createStatement();){
                stmt.execute(this.schema.buildUsersTableCreationSQL(this.isMySQL, this.isPostgreSQL));
                stmt.execute(this.schema.buildSessionsTableCreationSQL(this.isMySQL, this.isPostgreSQL));
                this.ensureUsersSkinDataColumnExists(conn);
                this.ensureUsersUsernameWidth(conn);
                if (this.isPostgreSQL) {
                    this.migrateJoinNotificationsToBoolean(conn);
                    this.migrateSessionsLastSeenToBigint(conn);
                }
                if (!this.isMySQL) {
                    this.executeIndices(stmt, this.schema.buildUsersIndicesSQL(false));
                    this.executeIndices(stmt, this.schema.buildSessionsIndicesSQL(false));
                }
            }
            return null;
        }, "initializeTables");
    }

    private void migrateJoinNotificationsToBoolean(Connection conn) throws SQLException {
        if (conn == null) {
            return;
        }
        String checkSql = "SELECT data_type FROM information_schema.columns WHERE table_name = ? AND column_name = ?";
        String dataType = null;
        try (PreparedStatement stmt2 = conn.prepareStatement(checkSql);){
            stmt2.setString(1, this.schema.usersTable.toLowerCase());
            stmt2.setString(2, this.schema.usersJoinNotifications.toLowerCase());
            try (ResultSet rs = stmt2.executeQuery();){
                if (rs.next()) {
                    dataType = rs.getString("data_type");
                }
            }
        }
        catch (SQLException stmt2) {
            // empty catch block
        }
        if (dataType == null || dataType.equalsIgnoreCase("boolean")) {
            return;
        }
        String migrateSql = String.format("ALTER TABLE %s ALTER COLUMN %s TYPE BOOLEAN USING (%s::boolean)", this.schema.usersTable, this.schema.usersJoinNotifications, this.schema.usersJoinNotifications);
        try (Statement stmt = conn.createStatement();){
            stmt.execute(migrateSql);
        }
        catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("cannot alter")) {
                return;
            }
            throw e;
        }
    }

    private void migrateSessionsLastSeenToBigint(Connection conn) throws SQLException {
        if (conn == null) {
            return;
        }
        String checkSql = "SELECT data_type FROM information_schema.columns WHERE table_name = ? AND column_name = ?";
        String dataType = null;
        try (PreparedStatement stmt2 = conn.prepareStatement(checkSql);){
            stmt2.setString(1, this.schema.sessionsTable.toLowerCase());
            stmt2.setString(2, this.schema.sessionsLastSeen.toLowerCase());
            try (ResultSet rs = stmt2.executeQuery();){
                if (rs.next()) {
                    dataType = rs.getString("data_type");
                }
            }
        }
        catch (SQLException stmt2) {
            // empty catch block
        }
        if (dataType == null || dataType.equalsIgnoreCase("bigint")) {
            return;
        }
        String migrateSql = String.format("ALTER TABLE %s ALTER COLUMN %s TYPE BIGINT USING (%s::bigint)", this.schema.sessionsTable, this.schema.sessionsLastSeen, this.schema.sessionsLastSeen);
        try (Statement stmt = conn.createStatement();){
            stmt.execute(migrateSql);
        }
        catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("cannot alter")) {
                return;
            }
            throw e;
        }
    }

    private void ensureUsersSkinDataColumnExists(Connection conn) throws SQLException {
        if (conn == null) {
            return;
        }
        try {
            if (this.columnExists(conn, this.schema.usersTable, "skin_data")) {
                return;
            }
        }
        catch (SQLException sQLException) {
            // empty catch block
        }
        String sql = String.format("ALTER TABLE %s ADD COLUMN skin_data TEXT", this.schema.usersTable);
        try (Statement stmt = conn.createStatement();){
            stmt.execute(sql);
        }
        catch (SQLException e) {
            String lower;
            String message = e.getMessage();
            if (message != null && ((lower = message.toLowerCase()).contains("duplicate column") || lower.contains("already exists"))) {
                return;
            }
            throw e;
        }
    }

    /**
     * Floodgate usernames are "." + gamertag (up to 17 chars). Older schemas used VARCHAR(16).
     */
    private void ensureUsersUsernameWidth(Connection conn) throws SQLException {
        if (conn == null || (!this.isMySQL && !this.isPostgreSQL)) {
            return;
        }
        try {
            if (this.isMySQL) {
                String sql = String.format("ALTER TABLE %s MODIFY COLUMN %s VARCHAR(32) NOT NULL",
                        this.schema.usersTable, this.schema.usersUsername);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
            } else {
                String sql = String.format("ALTER TABLE %s ALTER COLUMN %s TYPE VARCHAR(32)",
                        this.schema.usersTable, this.schema.usersUsername);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
            }
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("same") || msg.contains("already") || msg.contains("no change")) {
                return;
            }
            // Non-fatal: SQLite needs no change; some hosts disallow ALTER.
        }
    }

    /**
     * Marks Discord as unlinked without rewriting username/skin (avoids Bedrock VARCHAR / skin_data failures).
     * @param markedDiscordId use null to clear the link, or {@code unlinked_<id>} to preserve re-link matching
     */
    public void unlinkDiscord(UUID uuid, String markedDiscordId) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection()) {
                // Java + Floodgate can create two rows for the same Discord; UNIQUE(discord_id)
                // fails if another row already holds unlinked_<id>. Clear conflicts first.
                if (markedDiscordId != null && !markedDiscordId.isBlank()) {
                    this.clearDiscordIdConflicts(conn, uuid, markedDiscordId);
                    if (markedDiscordId.startsWith("unlinked_")) {
                        String rawId = markedDiscordId.substring("unlinked_".length());
                        if (!rawId.isBlank()) {
                            this.clearDiscordIdConflicts(conn, uuid, rawId);
                        }
                    }
                }
                String sql = String.format("UPDATE %s SET %s = ?, %s = ? WHERE %s = ?",
                        this.schema.usersTable,
                        this.schema.usersDiscordId,
                        this.schema.usersAccountType,
                        this.schema.usersUuid);
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, markedDiscordId);
                    stmt.setString(2, "UNLINKED");
                    stmt.setString(3, uuid.toString());
                    stmt.executeUpdate();
                }
            }
            return null;
        }, "unlinkDiscord");
    }

    private void clearDiscordIdConflicts(Connection conn, UUID keepUuid, String discordId) throws SQLException {
        String sql = String.format("UPDATE %s SET %s = NULL WHERE %s = ? AND %s <> ?",
                this.schema.usersTable,
                this.schema.usersDiscordId,
                this.schema.usersDiscordId,
                this.schema.usersUuid);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, discordId);
            stmt.setString(2, keepUuid.toString());
            stmt.executeUpdate();
        }
    }

    public void clearPassword(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        String sql = String.format("UPDATE %s SET %s = NULL WHERE %s = ?",
                this.schema.usersTable,
                this.schema.usersPassword,
                this.schema.usersUuid);
        this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.executeUpdate();
            }
            return null;
        }, "clearPassword");
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String[] tableCandidates;
        DatabaseMetaData meta = conn.getMetaData();
        String[] stringArray = tableCandidates = new String[]{tableName, tableName != null ? tableName.toLowerCase() : null, tableName != null ? tableName.toUpperCase() : null};
        int n = stringArray.length;
        int n2 = 0;
        while (n2 < n) {
            String candidate = stringArray[n2];
            if (candidate != null && !candidate.isEmpty()) {
                try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, candidate, null);){
                    while (rs.next()) {
                        String col = rs.getString("COLUMN_NAME");
                        if (col == null || !col.equalsIgnoreCase(columnName)) continue;
                        boolean bl = true;
                        return bl;
                    }
                }
                catch (SQLException sQLException) {
                    // empty catch block
                }
            }
            ++n2;
        }
        return false;
    }

    private void executeIndices(Statement stmt, String indicesSql) throws SQLException {
        if (indicesSql == null || indicesSql.isEmpty()) {
            return;
        }
        for (String indexSql : indicesSql.split(";")) {
            String trimmed = indexSql.trim();
            if (trimmed.isEmpty()) continue;
            try {
                stmt.execute(trimmed);
            }
            catch (SQLException e) {
                if (e.getMessage().contains("already exists")) continue;
                throw e;
            }
        }
    }

    public boolean isHealthy() {
        if (this.closed.get() || this.dataSource == null || this.dataSource.isClosed()) {
            return false;
        }
        try (Connection conn = this.dataSource.getConnection()) {
            return conn.isValid(3);
        }
        catch (SQLException e) {
            return false;
        }
    }

    public void createUser(User user) {
        Objects.requireNonNull(user, "User cannot be null");
        Objects.requireNonNull(user.getUuid(), "User UUID cannot be null");
        String sql = String.format("INSERT INTO %s (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)\nVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\n", this.schema.usersTable, this.schema.usersUuid, this.schema.usersUsername, this.schema.usersDiscordId, this.schema.usersFirstLogin, this.schema.usersLastLogin, this.schema.usersIpAddress, this.schema.usersPassword, this.schema.usersAccountType, this.schema.usersJoinNotifications, this.schema.usersCreatedAt, this.schema.usersSessionDuration);
        this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, user.getUuid().toString());
                stmt.setString(2, this.sanitizeString(user.getUsername()));
                stmt.setString(3, user.getDiscordId());
                stmt.setLong(4, user.getFirstLogin());
                stmt.setLong(5, user.getLastLogin());
                stmt.setString(6, user.getIpAddress());
                stmt.setString(7, user.getPassword());
                stmt.setString(8, user.getAccountType());
                stmt.setBoolean(9, true);
                stmt.setLong(10, System.currentTimeMillis());
                stmt.setString(11, this.config.getString("session.duration", "1h"));
                stmt.executeUpdate();
            }
            return null;
        }, "createUser");
    }

    public User getUser(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        String sql = String.format("SELECT * FROM %s WHERE %s = ?", this.schema.usersTable, this.schema.usersUuid);
        return this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery();){
                    if (rs.next()) {
                        User user = this.mapUser(rs);
                        return user;
                    }
                }
            }
            return null;
        }, "getUser");
    }

    public User getUserByUsername(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        String sql = String.format("SELECT * FROM %s WHERE %s = ?", this.schema.usersTable, this.schema.usersUsername);
        return this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery();){
                    if (rs.next()) {
                        User user = this.mapUser(rs);
                        return user;
                    }
                }
            }
            return null;
        }, "getUserByUsername");
    }

    public User getUserByDiscordId(String discordId) {
        if (discordId == null || discordId.isEmpty()) {
            return null;
        }
        String sql = String.format("SELECT * FROM %s WHERE %s = ?", this.schema.usersTable, this.schema.usersDiscordId);
        return this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, discordId);
                try (ResultSet rs = stmt.executeQuery();){
                    if (rs.next()) {
                        User user = this.mapUser(rs);
                        return user;
                    }
                }
            }
            return null;
        }, "getUserByDiscordId");
    }

    public void updateUser(User user) {
        Objects.requireNonNull(user, "User cannot be null");
        Objects.requireNonNull(user.getUuid(), "User UUID cannot be null");
        String sql = String.format("UPDATE %s SET %s = ?, %s = ?, %s = ?, %s = ?,\n%s = ?, %s = ?, %s = ?, %s = ?, %s = ? WHERE %s = ?\n", this.schema.usersTable, this.schema.usersUsername, this.schema.usersPassword, this.schema.usersDiscordId, this.schema.usersAccountType, this.schema.usersFirstLogin, this.schema.usersLastLogin, this.schema.usersIpAddress, this.schema.usersJoinNotifications, "skin_data", this.schema.usersUuid);
        this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, this.sanitizeString(user.getUsername()));
                stmt.setString(2, user.getPassword());
                stmt.setString(3, user.getDiscordId());
                stmt.setString(4, user.getAccountType());
                stmt.setLong(5, user.getFirstLogin());
                stmt.setLong(6, user.getLastLogin());
                stmt.setString(7, user.getIpAddress());
                stmt.setBoolean(8, user.isJoinNotifications());
                stmt.setString(9, user.getSkinData());
                stmt.setString(10, user.getUuid().toString());
                stmt.executeUpdate();
            }
            return null;
        }, "updateUser");
    }

    public void updateLastLogin(UUID uuid) {
        if (uuid == null) {
            return;
        }
        String sql = String.format("UPDATE %s SET %s = ? WHERE %s = ?", this.schema.usersTable, this.schema.usersLastLogin, this.schema.usersUuid);
        this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            }
            return null;
        }, "updateLastLogin");
    }

    public boolean migrateUserUuid(UUID fromUuid, UUID toUuid) {
        if (fromUuid == null || toUuid == null || fromUuid.equals(toUuid)) {
            return false;
        }
        String userExistsSql = String.format("SELECT 1 FROM %s WHERE %s = ? LIMIT 1", this.schema.usersTable, this.schema.usersUuid);
        String sessionExistsSql = String.format("SELECT 1 FROM %s WHERE %s = ? LIMIT 1", this.schema.sessionsTable, this.schema.sessionsUuid);
        String updateUserSql = String.format("UPDATE %s SET %s = ? WHERE %s = ?", this.schema.usersTable, this.schema.usersUuid, this.schema.usersUuid);
        String updateSessionSql = String.format("UPDATE %s SET %s = ? WHERE %s = ?", this.schema.sessionsTable, this.schema.sessionsUuid, this.schema.sessionsUuid);
        return this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection()) {
                boolean previousAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    if (!this.rowExists(conn, userExistsSql, fromUuid)) {
                        conn.rollback();
                        return false;
                    }
                    if (this.rowExists(conn, userExistsSql, toUuid) || this.rowExists(conn, sessionExistsSql, toUuid)) {
                        conn.rollback();
                        return false;
                    }
                    int updatedUsers;
                    try (PreparedStatement stmt = conn.prepareStatement(updateUserSql)) {
                        stmt.setString(1, toUuid.toString());
                        stmt.setString(2, fromUuid.toString());
                        updatedUsers = stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement(updateSessionSql)) {
                        stmt.setString(1, toUuid.toString());
                        stmt.setString(2, fromUuid.toString());
                        stmt.executeUpdate();
                    }
                    conn.commit();
                    return updatedUsers > 0;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    try {
                        conn.setAutoCommit(previousAutoCommit);
                    } catch (SQLException ignored) {
                    }
                }
            }
        }, "migrateUserUuid");
    }

    private boolean rowExists(Connection conn, String sql, UUID uuid) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql);){
            boolean bl;
            block12: {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                try {
                    bl = rs.next();
                    if (rs == null) break block12;
                }
                catch (Throwable throwable) {
                    if (rs != null) {
                        try {
                            rs.close();
                        }
                        catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                }
                rs.close();
            }
            return bl;
        }
    }

    public boolean isDiscordLinked(String discordId) {
        if (discordId == null || discordId.isEmpty()) {
            return false;
        }
        String sql = String.format("SELECT COUNT(*) FROM %s WHERE %s = ?", this.schema.usersTable, this.schema.usersDiscordId);
        return this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, discordId);
                try (ResultSet rs = stmt.executeQuery();){
                    if (rs.next()) {
                        Boolean bl = rs.getInt(1) > 0;
                        return bl;
                    }
                }
            }
            return false;
        }, "isDiscordLinked");
    }

    public void createSession(UUID uuid, String discordId, String ipAddress) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        String sql = this.isMySQL ? String.format("INSERT INTO %s (%s, %s, %s, %s)\nVALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE\n%s = VALUES(%s), %s = VALUES(%s), %s = VALUES(%s)\n", this.schema.sessionsTable, this.schema.sessionsUuid, this.schema.sessionsDiscordId, this.schema.sessionsLastSeen, this.schema.sessionsIpAddress, this.schema.sessionsDiscordId, this.schema.sessionsDiscordId, this.schema.sessionsIpAddress, this.schema.sessionsIpAddress, this.schema.sessionsLastSeen, this.schema.sessionsLastSeen) : (this.isPostgreSQL ? String.format("INSERT INTO %s (%s, %s, %s, %s)\nVALUES (?, ?, ?, ?)\nON CONFLICT (%s) DO UPDATE SET\n%s = EXCLUDED.%s, %s = EXCLUDED.%s, %s = EXCLUDED.%s\n", this.schema.sessionsTable, this.schema.sessionsUuid, this.schema.sessionsDiscordId, this.schema.sessionsLastSeen, this.schema.sessionsIpAddress, this.schema.sessionsUuid, this.schema.sessionsDiscordId, this.schema.sessionsDiscordId, this.schema.sessionsIpAddress, this.schema.sessionsIpAddress, this.schema.sessionsLastSeen, this.schema.sessionsLastSeen) : String.format("INSERT OR REPLACE INTO %s (%s, %s, %s, %s)\nVALUES (?, ?, ?, ?)\n", this.schema.sessionsTable, this.schema.sessionsUuid, this.schema.sessionsDiscordId, this.schema.sessionsLastSeen, this.schema.sessionsIpAddress));
        this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, uuid.toString());
                stmt.setString(2, discordId);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.setString(4, ipAddress);
                stmt.executeUpdate();
            }
            return null;
        }, "createSession");
    }

    public void saveSession(PlayerSession session) {
        Objects.requireNonNull(session, "Session cannot be null");
        User user = this.getUser(session.getUuid());
        String discordId = user != null ? user.getDiscordId() : null;
        this.createSession(session.getUuid(), discordId, session.getIpAddress());
    }

    public PlayerSession getSession(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        String sql = String.format("SELECT %s, %s, %s FROM %s WHERE %s = ?", this.schema.sessionsDiscordId, this.schema.sessionsIpAddress, this.schema.sessionsLastSeen, this.schema.sessionsTable, this.schema.sessionsUuid);
        return this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery();){
                    if (rs.next()) {
                        String discordId = rs.getString(this.schema.sessionsDiscordId);
                        String ipAddress = rs.getString(this.schema.sessionsIpAddress);
                        long lastSeen = rs.getLong(this.schema.sessionsLastSeen);
                        PlayerSession session = new PlayerSession(uuid, discordId, ipAddress);
                        session.setLastSeen(lastSeen);
                        PlayerSession playerSession = session;
                        return playerSession;
                    }
                }
            }
            return null;
        }, "getSession");
    }

    public boolean hasValidSession(UUID uuid, String ipAddress) {
        if (uuid == null) {
            return false;
        }
        String sql = String.format("SELECT %s, %s, %s FROM %s WHERE %s = ?", this.schema.sessionsLastSeen, this.schema.sessionsDiscordId, this.schema.sessionsIpAddress, this.schema.sessionsTable, this.schema.sessionsUuid);
        return this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return false;
                    }
                    long lastSeen = rs.getLong(this.schema.sessionsLastSeen);
                    String discordId = rs.getString(this.schema.sessionsDiscordId);
                    String durationKey = this.getPlayerSessionDuration(discordId);
                    long sessionDuration = this.getSessionDurationInSeconds(durationKey) * 1000L;
                    if (sessionDuration == 0L) {
                        this.deleteSession(uuid);
                        return false;
                    }
                    boolean isValid = System.currentTimeMillis() - lastSeen <= sessionDuration;
                    if (!isValid) {
                        this.deleteSession(uuid);
                    }
                    return isValid;
                }
            }
        }, "hasValidSession");
    }

    public void deleteSession(UUID uuid) {
        if (uuid == null) {
            return;
        }
        String sql = String.format("DELETE FROM %s WHERE %s = ?", this.schema.sessionsTable, this.schema.sessionsUuid);
        this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, uuid.toString());
                stmt.executeUpdate();
            }
            return null;
        }, "deleteSession");
    }

    public void clearAllSessions(String discordId) {
        if (discordId == null || discordId.isEmpty()) {
            return;
        }
        String sql = String.format("DELETE FROM %s WHERE %s = ?", this.schema.sessionsTable, this.schema.sessionsDiscordId);
        this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, discordId);
                stmt.executeUpdate();
            }
            return null;
        }, "clearAllSessions");
    }

    public void updateSessionIp(UUID uuid, String newIp) {
        if (uuid == null) {
            return;
        }
        String sql = String.format("UPDATE %s SET %s = ? WHERE %s = ?", this.schema.sessionsTable, this.schema.sessionsIpAddress, this.schema.sessionsUuid);
        this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, newIp);
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            }
            return null;
        }, "updateSessionIp");
    }

    public String getPlayerSessionDuration(String discordId) {
        if (discordId == null) {
            return this.config.getString("session.duration", "1h");
        }
        String sql = String.format("SELECT %s FROM %s WHERE %s = ?", this.schema.usersSessionDuration, this.schema.usersTable, this.schema.usersDiscordId);
        return this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, discordId);
                try (ResultSet rs = stmt.executeQuery();){
                    if (rs.next()) {
                        String duration = rs.getString(this.schema.usersSessionDuration);
                        String string = duration != null ? duration : this.config.getString("session.duration", "1h");
                        return string;
                    }
                }
            }
            return this.config.getString("session.duration", "1h");
        }, "getPlayerSessionDuration");
    }

    public void setPlayerSessionDuration(String discordId, String duration) {
        if (discordId == null) {
            return;
        }
        String sql = String.format("UPDATE %s SET %s = ? WHERE %s = ?", this.schema.usersTable, this.schema.usersSessionDuration, this.schema.usersDiscordId);
        this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, duration);
                stmt.setString(2, discordId);
                stmt.executeUpdate();
            }
            return null;
        }, "setPlayerSessionDuration");
    }

    public long getSessionDurationInSeconds(String durationKey) {
        return DurationUtil.parseToSeconds(durationKey);
    }

    public List<UUID> getAccountsByIp(String ipAddress) {
        ArrayList<UUID> accounts = new ArrayList<UUID>();
        if (ipAddress == null || ipAddress.isEmpty()) {
            return accounts;
        }
        String sql = String.format("SELECT %s FROM %s WHERE %s = ?", this.schema.usersUuid, this.schema.usersTable, this.schema.usersIpAddress);
        return this.executeWithRetry(() -> {
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, ipAddress);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String uuidStr = rs.getString(this.schema.usersUuid);
                        if (uuidStr == null) {
                            continue;
                        }
                        try {
                            accounts.add(UUID.fromString(uuidStr));
                        } catch (IllegalArgumentException ignored) {
                            // Ignore corrupt UUID rows while returning the valid accounts for this IP.
                        }
                    }
                }
            }
            return accounts;
        }, "getAccountsByIp");
    }

    private User mapUser(ResultSet rs) throws SQLException {
        String uuidStr = rs.getString(this.schema.usersUuid);
        if (uuidStr == null) {
            return null;
        }
        try {
            User user = new User(UUID.fromString(uuidStr));
            user.setUsername(rs.getString(this.schema.usersUsername));
            user.setPassword(rs.getString(this.schema.usersPassword));
            user.setDiscordId(rs.getString(this.schema.usersDiscordId));
            user.setAccountType(rs.getString(this.schema.usersAccountType));
            user.setFirstLogin(rs.getLong(this.schema.usersFirstLogin));
            user.setLastLogin(rs.getLong(this.schema.usersLastLogin));
            user.setIpAddress(rs.getString(this.schema.usersIpAddress));
            try {
                boolean joinNotifications = rs.getBoolean(this.schema.usersJoinNotifications);
                user.setJoinNotifications(joinNotifications);
            }
            catch (SQLException e) {
                user.setJoinNotifications(true);
            }
            try {
                user.setSkinData(rs.getString("skin_data"));
            }
            catch (SQLException ignored) {
                user.setSkinData(null);
            }
            return user;
        }
        catch (IllegalArgumentException e) {
            throw new SQLException("Invalid UUID in database: " + uuidStr, e);
        }
    }

    private String sanitizeString(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[\\x00-\\x1F\\x7F]", "");
    }

    private <T> T executeWithRetry(DatabaseOperation<T> operation, String operationName) {
        this.checkNotClosed();
        SQLException lastException = null;
        for (int attempt = 1; attempt <= 3; ++attempt) {
            try {
                return operation.execute();
            }
            catch (SQLException e) {
                lastException = e;
                if (!this.isTransientError(e) || attempt >= 3) break;
                try {
                    Thread.sleep(100L * (long)attempt);
                    continue;
                }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new DatabaseException(PluginException.ErrorCode.DB_QUERY_FAILED, "Operation interrupted: " + operationName, ie);
                }
            }
        }
        throw new DatabaseException(PluginException.ErrorCode.DB_QUERY_FAILED, "Database operation failed after 3 attempts: " + operationName, operationName, null, lastException);
    }

    private boolean isTransientError(SQLException e) {
        String message;
        String sqlState = e.getSQLState();
        if (sqlState != null) {
            if (sqlState.startsWith("08")) {
                return true;
            }
            if (sqlState.equals("40001")) {
                return true;
            }
        }
        if ((message = e.getMessage()) != null) {
            return (message = message.toLowerCase()).contains("connection") || message.contains("timeout") || message.contains("deadlock");
        }
        return false;
    }

    private void checkNotClosed() {
        if (this.closed.get()) {
            throw new DatabaseException(PluginException.ErrorCode.DB_CONNECTION_FAILED, "Database connection is closed");
        }
    }

    public void close() {
        if (this.closed.getAndSet(true)) {
            return;
        }
        if (this.dataSource != null && !this.dataSource.isClosed()) {
            this.dataSource.close();
        }
    }

    public boolean isClosed() {
        return this.closed.get();
    }

    @FunctionalInterface
    private static interface DatabaseOperation<T> {
        public T execute() throws SQLException;
    }
}
