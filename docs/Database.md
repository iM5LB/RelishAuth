# Database Setup

Guide for configuring RelishAuth's database system, including SQLite and MySQL/MariaDB options.

## Database Options

RelishAuth supports multiple database types:
- **SQLite** - Local file-based database (default, zero configuration)
- **MySQL/MariaDB** - Network database for multi-server setups
- **PostgreSQL** - Advanced network database with enterprise features

## SQLite (Default)

### Overview

SQLite is the default database option:
- **Zero configuration** - Works out of the box
- **File-based** - Stored in `plugins/relishauth/data.db`
- **Perfect for single-server** setups
- **No external dependencies**

### Configuration

```yaml
database:
  type: "sqlite"
  sqlite:
    path: "data.db"
```

### Custom Path

Change database file location:

```yaml
database:
  type: "sqlite"
  sqlite:
    path: "custom/path/auth.db"
```

Paths are relative to `plugins/relishauth/` folder.

### Advantages

- ✅ No setup required
- ✅ No external database server needed
- ✅ Fast for single-server
- ✅ Automatic backups easy (just copy file)
- ✅ No network latency

### Disadvantages

- ❌ Not suitable for multi-server networks
- ❌ No concurrent access from multiple proxies
- ❌ Limited scalability

### Best For

- Single Velocity proxy
- Small to medium servers
- Testing and development
- Servers without database expertise

## MySQL/MariaDB

### Overview

MySQL/MariaDB for network database:
- **Multi-server support** - Share data across proxies
- **Scalable** - Handle large player bases
- **Concurrent access** - Multiple proxies simultaneously
- **Professional** - Industry-standard database

### Prerequisites

- MySQL 5.7+ or MariaDB 10.2+
- Database server accessible from Velocity proxy
- Database credentials

## MySQL Setup

### Step 1: Install MySQL

**Ubuntu/Debian**:
```bash
sudo apt update
sudo apt install mysql-server
sudo mysql_secure_installation
```

**CentOS/RHEL**:
```bash
sudo yum install mysql-server
sudo systemctl start mysqld
sudo mysql_secure_installation
```

**Windows**:
- Download from [MySQL Downloads](https://dev.mysql.com/downloads/mysql/)
- Run installer
- Follow setup wizard

### Step 2: Create Database

Connect to MySQL:
```bash
mysql -u root -p
```

Create database and user:
```sql
CREATE DATABASE relishauth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'relishauth'@'localhost' IDENTIFIED BY 'secure_password_here';
GRANT ALL PRIVILEGES ON relishauth.* TO 'relishauth'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

**For remote access** (if Velocity is on different server):
```sql
CREATE USER 'relishauth'@'%' IDENTIFIED BY 'secure_password_here';
GRANT ALL PRIVILEGES ON relishauth.* TO 'relishauth'@'%';
FLUSH PRIVILEGES;
```

### Step 3: Configure RelishAuth

Edit `config.yml`:

```yaml
database:
  type: "mysql"
  mysql:
    host: "localhost"
    port: 3306
    database: "relishauth"
    username: "relishauth"
    password: "secure_password_here"
    use-ssl: false
```

### Step 4: Configure Connection Pool

```yaml
database:
  mysql:
    pool:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### Step 5: Restart and Verify

Restart Velocity proxy and check console:
```
[INFO] [RelishAuth] Connecting to MySQL database...
[INFO] [RelishAuth] Database connection established!
[INFO] [RelishAuth] Created tables successfully
```

## MariaDB Setup

MariaDB setup is identical to MySQL:

```yaml
database:
  type: "mariadb"  # or "mysql" - both work
  mysql:  # Configuration is the same
    host: "localhost"
    port: 3306
    database: "relishauth"
    username: "relishauth"
    password: "secure_password_here"
```

## PostgreSQL

### Overview

PostgreSQL for advanced database needs:
- **Enterprise features** - Advanced data types and functions
- **High performance** - Excellent for large datasets
- **Multi-server support** - Share data across proxies
- **ACID compliance** - Reliable transactions
- **Extensible** - Custom functions and extensions

### Prerequisites

- PostgreSQL 10+ (recommended 12+)
- Database server accessible from Velocity proxy
- Database credentials

### Step 1: Install PostgreSQL

**Ubuntu/Debian**:
```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

**CentOS/RHEL**:
```bash
sudo yum install postgresql-server postgresql-contrib
sudo postgresql-setup initdb
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

**Windows**:
- Download from [PostgreSQL Downloads](https://www.postgresql.org/download/windows/)
- Run installer
- Follow setup wizard

### Step 2: Create Database

Connect to PostgreSQL:
```bash
sudo -u postgres psql
```

Create database and user:
```sql
CREATE DATABASE relishauth;
CREATE USER relishauth WITH ENCRYPTED PASSWORD 'secure_password_here';
GRANT ALL PRIVILEGES ON DATABASE relishauth TO relishauth;
\q
```

**For remote access** (if Velocity is on different server):
Edit `/etc/postgresql/*/main/postgresql.conf`:
```
listen_addresses = '*'
```

Edit `/etc/postgresql/*/main/pg_hba.conf`:
```
host    relishauth    relishauth    0.0.0.0/0    md5
```

Restart PostgreSQL:
```bash
sudo systemctl restart postgresql
```

### Step 3: Configure RelishAuth

Edit `config.yml`:

```yaml
database:
  type: "postgresql"
  postgresql:
    host: "localhost"
    port: 5432
    database: "relishauth"
    username: "relishauth"
    password: "secure_password_here"
    use-ssl: false
```

### Step 4: Configure Connection Pool

```yaml
database:
  postgresql:
    pool:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### Step 5: Restart and Verify

Restart Velocity proxy and check console:
```
[INFO] [RelishAuth] Connecting to PostgreSQL database...
[INFO] [RelishAuth] Database connection established!
[INFO] [RelishAuth] Created tables successfully
```

### Advantages

- ✅ Enterprise-grade reliability
- ✅ Advanced data types (JSON, arrays, etc.)
- ✅ Excellent performance with large datasets
- ✅ Strong ACID compliance
- ✅ Extensible with custom functions
- ✅ Multi-server support

### Disadvantages

- ❌ More complex setup than MySQL
- ❌ Requires PostgreSQL knowledge
- ❌ Overkill for small servers

### Best For

- Large server networks (1000+ players)
- Enterprise environments
- Servers requiring advanced database features
- High-availability setups

## Connection Pool Settings

### HikariCP Configuration

RelishAuth uses HikariCP for connection pooling:

```yaml
database:
  mysql:  # For MySQL/MariaDB
    pool:
      maximum-pool-size: 10      # Max connections
      minimum-idle: 5            # Min idle connections
      connection-timeout: 30000  # Connection timeout (ms)
      idle-timeout: 600000       # Idle timeout (ms)
      max-lifetime: 1800000      # Max connection lifetime (ms)

  postgresql:  # For PostgreSQL
    pool:
      maximum-pool-size: 10      # Max connections
      minimum-idle: 5            # Min idle connections
      connection-timeout: 30000  # Connection timeout (ms)
      idle-timeout: 600000       # Idle timeout (ms)
      max-lifetime: 1800000      # Max connection lifetime (ms)
```

### Tuning for Server Size

**Small Server** (< 50 players):
```yaml
pool:
  maximum-pool-size: 5
  minimum-idle: 2
```

**Medium Server** (50-200 players):
```yaml
pool:
  maximum-pool-size: 10
  minimum-idle: 5
```

**Large Server** (200+ players):
```yaml
pool:
  maximum-pool-size: 20
  minimum-idle: 10
```

**Multi-Proxy Network**:
```yaml
pool:
  maximum-pool-size: 15
  minimum-idle: 8
```

## SSL/TLS Connection

### Enable SSL

**For MySQL/MariaDB**:
```yaml
database:
  mysql:
    use-ssl: true
```

**For PostgreSQL**:
```yaml
database:
  postgresql:
    use-ssl: true
```

### Configure MySQL for SSL

1. **Generate certificates** (on MySQL server)
2. **Configure MySQL** to use SSL
3. **Update RelishAuth config**:
   ```yaml
   use-ssl: true
   ```

### When to Use SSL

- ✅ Database on different server
- ✅ Untrusted network
- ✅ Compliance requirements
- ❌ Database on same server (localhost)
- ❌ Trusted local network

## Database Schema

### Tables

RelishAuth creates these tables:

**users**:
- `uuid` - Player UUID
- `username` - Player username
- `discord_id` - Linked Discord ID
- `first_login` - First login timestamp
- `last_login` - Last login timestamp
- `ip_address` - Last IP address
- `password` - Hashed password
- `account_type` - Premium/Cracked
- `join_notifications` - Notification preference
- `created_at` - Account creation time
- `session_duration` - Session duration preference

**sessions**:
- `uuid` - Player UUID
- `discord_id` - Discord ID (if linked)
- `last_seen` - Last seen timestamp
- `ip_address` - Session IP address

### Custom Schema

You can customize table and column names:

```yaml
database:
  schema:
    users:
      table: "auth_users"
      columns:
        uuid: "player_uuid"
        username: "player_name"
        # ... other columns
    
    sessions:
      table: "auth_sessions"
      columns:
        uuid: "player_uuid"
        # ... other columns
```

**Note**: Only change if you have specific requirements.

## Migration

### SQLite to MySQL

1. **Set up MySQL** database
2. **Export SQLite data**:
   ```bash
   sqlite3 data.db .dump > backup.sql
   ```
3. **Convert to MySQL format** (manual editing required)
4. **Import to MySQL**:
   ```bash
   mysql -u relishauth -p relishauth < backup.sql
   ```
5. **Update config** to use MySQL
6. **Restart** Velocity proxy

### MySQL to SQLite

Not recommended for production. Use for testing only.

## Backup and Restore

### SQLite Backup

**Manual backup**:
```bash
cp plugins/relishauth/data.db plugins/relishauth/data.db.backup
```

**Automated backup** (Linux cron):
```bash
0 2 * * * cp /path/to/plugins/relishauth/data.db /path/to/backups/data-$(date +\%Y\%m\%d).db
```

### MySQL Backup

**Manual backup**:
```bash
mysqldump -u relishauth -p relishauth > relishauth-backup.sql
```

**Automated backup** (Linux cron):
```bash
0 2 * * * mysqldump -u relishauth -p'password' relishauth > /path/to/backups/relishauth-$(date +\%Y\%m\%d).sql
```

### Restore

**SQLite**:
```bash
cp data.db.backup data.db
```

**MySQL**:
```bash
mysql -u relishauth -p relishauth < relishauth-backup.sql
```

## Troubleshooting

### Connection Failed

**Check MySQL is running**:
```bash
sudo systemctl status mysql
```

**Test connection**:
```bash
mysql -h localhost -u relishauth -p
```

**Check firewall**:
```bash
sudo ufw allow 3306/tcp
```

**Verify credentials** in config.yml

### Too Many Connections

**Increase MySQL max connections**:
```sql
SET GLOBAL max_connections = 200;
```

**Reduce pool size**:
```yaml
pool:
  maximum-pool-size: 5
```

### Slow Queries

**Check connection pool settings**
**Increase pool size** for high traffic
**Optimize MySQL** configuration
**Add database indexes** (automatic)

### SSL Connection Failed

**Verify SSL is enabled** on MySQL server
**Check certificates** are valid
**Try without SSL** first to isolate issue

## Performance Tips

### For SQLite

- Keep database file on fast storage (SSD)
- Regular VACUUM to optimize:
  ```sql
  VACUUM;
  ```
- Don't use for multi-server

### For MySQL

- Use connection pooling (enabled by default)
- Tune pool size for your server
- Use local database when possible
- Enable query cache in MySQL
- Regular maintenance:
  ```sql
  OPTIMIZE TABLE users;
  OPTIMIZE TABLE sessions;
  ```

## Multi-Server Setup

### Shared Database

All proxies connect to same MySQL database:

**Proxy 1 config.yml**:
```yaml
database:
  type: "mysql"
  mysql:
    host: "db.example.com"
    database: "relishauth"
```

**Proxy 2 config.yml**:
```yaml
database:
  type: "mysql"
  mysql:
    host: "db.example.com"
    database: "relishauth"
```

### Benefits

- Players authenticate once across network
- Sessions work across all proxies
- Centralized player data
- Unified administration

## Next Steps

- [Configure authentication](AuthMethods.md)
- [Set up security](Security.md)
- [Learn commands](Commands.md)
- [View troubleshooting](Troubleshooting.md)

---

Need database help? [Join our Discord](https://discord.gg/jDr2KZcGXk) for support!
