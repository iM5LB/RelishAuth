# Installation Guide

This guide provides detailed instructions for installing RelishAuth on your Velocity proxy server.

## System Requirements

### Minimum Requirements
- **Velocity Proxy**: 3.4.0 or higher
- **Java**: 21 or higher
- **RAM**: 512MB minimum (1GB+ recommended)
- **Storage**: 50MB for plugin and database

### Optional Requirements
- **Discord Bot**: For Discord authentication features
- **MySQL/MariaDB**: For multi-server database support
- **Floodgate**: For Bedrock Edition player support

## Installation Steps

### 1. Download Required Files

#### RelishAuth Plugin
Download the latest version of RelishAuth from:
- [Modrinth](https://modrinth.com/plugin/relishauth) (recommended)
- [GitHub Releases](https://github.com/iM5LB/relishauth/releases)

#### LimboAPI Dependency
RelishAuth requires LimboAPI to function. Download it from:
- [GitHub - Elytrium/LimboAPI](https://github.com/Elytrium/LimboAPI)
- [Modrinth](https://modrinth.com/plugin/limboapi)

### 2. Install Plugins

1. **Locate your Velocity plugins folder**:
   ```
   velocity-proxy/
   └── plugins/
   ```

2. **Copy both JAR files** to the plugins folder:
   ```
   velocity-proxy/
   └── plugins/
       ├── RelishAuth-1.0.0.jar
       └── LimboAPI-1.x.x.jar
   ```

3. **Verify file permissions** (Linux/Mac):
   ```bash
   chmod 644 plugins/RelishAuth-*.jar
   chmod 644 plugins/LimboAPI-*.jar
   ```

### 3. First Startup

1. **Start your Velocity proxy**:
   ```bash
   java -Xms512M -Xmx1G -jar velocity.jar
   ```

2. **Wait for plugins to load**. You should see in console:
   ```
   [INFO] [RelishAuth] Loading RelishAuth v1.0.0
   [INFO] [RelishAuth] Generating default configuration...
   [INFO] [RelishAuth] RelishAuth has been enabled!
   ```

3. **Stop the server** to configure:
   ```
   end
   ```

### 4. Initial Configuration

Navigate to the RelishAuth configuration folder:
```
velocity-proxy/
└── plugins/
    └── relishauth/
        ├── config.yml
        └── lang/
            ├── en.yml
            └── ar.yml
```

Edit `config.yml` with your preferred settings. At minimum, configure:

```yaml
# Choose authentication method
authentication:
  method: "password"  # or "discord"
  premium-auto-login: true

# Set session duration
session:
  duration: "5m"

# Database (SQLite is default, no setup needed)
database:
  type: "sqlite"
```

[View complete configuration guide →](Configuration.md)

### 5. Start Server

Start your Velocity proxy again:
```bash
java -Xms512M -Xmx1G -jar velocity.jar
```

RelishAuth is now active! Players will be prompted to authenticate when joining.

## Post-Installation Setup

### Configure Discord Bot (Optional)

If you want Discord authentication features:

1. **Create Discord Application**:
   - Go to [Discord Developer Portal](https://discord.com/developers/applications)
   - Click "New Application"
   - Navigate to "Bot" section and create a bot
   - Copy the bot token

2. **Configure in RelishAuth**:
   ```yaml
   discord:
     bot-token: "YOUR_BOT_TOKEN_HERE"
     server-id: "YOUR_DISCORD_SERVER_ID"
   ```

3. **Invite bot to your server**:
   ```
   https://discord.com/api/oauth2/authorize?client_id=YOUR_BOT_ID&permissions=268435456&scope=bot%20applications.commands
   ```

[View complete Discord setup guide →](DiscordBot.md)

### Configure Database (Optional)

For multi-server networks, use MySQL:

1. **Create MySQL database**:
   ```sql
   CREATE DATABASE relishauth;
   CREATE USER 'relishauth'@'localhost' IDENTIFIED BY 'secure_password';
   GRANT ALL PRIVILEGES ON relishauth.* TO 'relishauth'@'localhost';
   FLUSH PRIVILEGES;
   ```

2. **Configure in RelishAuth**:
   ```yaml
   database:
     type: "mysql"
     mysql:
       host: "localhost"
       port: 3306
       database: "relishauth"
       username: "relishauth"
       password: "secure_password"
   ```

[View complete database setup guide →](Database.md)

### Set Up Permissions (Optional)

If using a permission plugin like LuckPerms:

```
# Admin permissions
lp group admin permission set relishauth.admin true

# Player permissions (usually default)
lp group default permission set relishauth.player true
```

[View complete permissions guide →](Permissions.md)

## Verification

### Test Authentication

1. **Join your server** with a Minecraft client
2. **You should see**:
   - Limbo world (usually The End dimension)
   - Authentication prompt in chat
   - Title messages with instructions

3. **Test authentication**:
   - For password: `/ra password test123 test123`
   - For Discord: Enter your Discord username

### Check Console

Look for these messages in console:
```
[INFO] [RelishAuth] Player joined: PlayerName
[INFO] [RelishAuth] Authentication method: password
[INFO] [RelishAuth] Player authenticated successfully: PlayerName
```

### Test Commands

Run these commands to verify functionality:
```
/ra info          # View your account info
/ra reload        # Reload configuration (admin)
/ra session 15m   # Change session duration
```

## Troubleshooting Installation

### Plugin Not Loading

**Symptoms**: No RelishAuth messages in console

**Solutions**:
- Verify Java version: `java -version` (must be 21+)
- Check JAR file is not corrupted (re-download)
- Ensure LimboAPI is installed
- Check console for error messages

### LimboAPI Missing Error

**Symptoms**: Error about missing LimboAPI dependency

**Solutions**:
- Download LimboAPI from [GitHub](https://github.com/Elytrium/LimboAPI)
- Place in same plugins folder as RelishAuth
- Restart server

### Configuration Errors

**Symptoms**: Plugin loads but doesn't work correctly

**Solutions**:
- Validate YAML syntax (use [YAML Lint](https://www.yamllint.com/))
- Check for tabs (use spaces only)
- Delete config.yml and regenerate
- Check console for specific error messages

### Database Connection Failed

**Symptoms**: Error connecting to MySQL database

**Solutions**:
- Verify MySQL is running
- Check credentials are correct
- Ensure database exists
- Test connection from server: `mysql -h localhost -u relishauth -p`
- Check firewall allows MySQL port (3306)

### Discord Bot Not Responding

**Symptoms**: Bot doesn't send DMs or respond to commands

**Solutions**:
- Verify bot token is correct
- Check bot is online in Discord
- Ensure bot has required permissions
- Verify server ID is correct
- Check bot is invited to your Discord server

## Updating RelishAuth

### Update Process

1. **Backup your configuration**:
   ```bash
   cp -r plugins/relishauth plugins/relishauth.backup
   ```

2. **Download new version**

3. **Stop server**:
   ```
   end
   ```

4. **Replace JAR file**:
   ```bash
   rm plugins/RelishAuth-*.jar
   cp RelishAuth-NEW-VERSION.jar plugins/
   ```

5. **Start server**:
   ```bash
   java -Xms512M -Xmx1G -jar velocity.jar
   ```

6. **Check console** for update messages:
   ```
   [INFO] [RelishAuth] Configuration updated to v1.x.x
   [INFO] [RelishAuth] Added new config keys: ...
   ```

### Auto-Update Feature

RelishAuth includes an auto-update system for configuration:

```yaml
auto-update:
  enabled: true
  check-on-startup: true
```

This automatically:
- Adds new configuration keys
- Preserves your existing settings
- Creates backups before updating

## Next Steps

Now that RelishAuth is installed:

1. **Configure authentication method**: [Authentication Methods](AuthMethods.md)
2. **Set up Discord bot**: [Discord Bot Setup](DiscordBot.md)
3. **Configure security**: [Security Settings](Security.md)
4. **Customize messages**: [Configuration Guide](Configuration.md)
5. **Learn commands**: [Commands Reference](Commands.md)

## Need Help?

- **Discord Support**: [Join our Discord](https://discord.gg/jDr2KZcGXk)
- **Troubleshooting**: [View common issues](Troubleshooting.md)
- **Configuration**: [Complete configuration guide](Configuration.md)

---

**Installation complete!** 🎉 Your server is now protected with RelishAuth.
