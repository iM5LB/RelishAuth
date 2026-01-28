# RelishAuth
**Advanced Authentication System for Velocity Proxy Servers 🔐**
<div align="center">

![RelishAuthBanner](https://cdn.modrinth.com/data/cached_images/fc12d02a41dd8c2bb482e33b024ea2ecee029f1d_0.webp)

*Secure your Velocity network with advanced authentication, Discord integration, and premium account support*

</div>



## 🌟 Features

### 🔐 **Multi-Method Authentication**
- **Password Authentication**: Traditional secure password system with Argon2 hashing
- **Discord Integration**: Link Discord accounts for seamless authentication
- **Premium Auto-Login**: Automatic authentication for premium Minecraft accounts
- **Hybrid Mode**: Combine password + Discord for maximum security

### 🛡️ **Advanced Security**
- **Session Management**: Configurable session durations (0-1 hour)
- **IP Validation**: Optional IP-based session validation
- **Rate Limiting**: Protection against brute force attacks
- **Premium Verification**: Real-time Mojang API validation
- **Bedrock Support**: Compatible with Floodgate for cross-platform play

### 🤖 **Discord Bot Integration**
- **Real-time Verification**: Instant Discord DM verification
- **Join Notifications**: Security alerts when someone joins with your account
- **Account Management**: Change passwords, manage sessions via Discord
- **Admin Commands**: Full server management through Discord slash commands
- **Rich Embeds**: Beautiful, informative Discord messages

### 🌐 **Multi-Language Support**
- **English** and **Arabic** language packs included
- **Customizable Messages**: Full message customization support

### 💾 **Flexible Database Support**
- **SQLite**: Zero-configuration local database (default)
- **MySQL/MariaDB**: Network database support for multi-server setups
- **Connection Pooling**: High-performance HikariCP integration

---

## 📸 Screenshots
### Discord Integration
![Discord Verification](https://cdn.modrinth.com/data/8BqplnPe/images/675ea2b97cc4779f296b18a9a99b53ef0873f498.png)

*Seamless Discord verification with interactive buttons*

### Admin Dashboard
![Admin Commands](https://cdn.modrinth.com/data/8BqplnPe/images/b5c48e8bfb565f8249bdf5f7899b9053dee89437_350.webp)

*Powerful admin tools accessible through Discord slash commands*

### Security Notifications
![Security Alerts](https://cdn.modrinth.com/data/8BqplnPe/images/a72bcd22b40d6872158b43b3944323d25bee506c_350.webp)

*Real-time security notifications keep your account safe*

### Optimized Limbo world
![Limbo world](https://cdn.modrinth.com/data/8BqplnPe/images/7256b19ae90fd624265c0c7b315913d905027b65_350.webp)

---

## 🚀 Installation

### Prerequisites
- **Velocity Proxy** 3.4.0 or higher
- **Java** 21 or higher
- **LimboAPI** plugin (required dependency)
- **Discord Bot** (optional, for Discord features)

### Step 1: Download and Install
1. Download plugin JAR file
2. Place the JAR file in your Velocity `plugins/` folder
3. Install [LimboAPI](https://github.com/Elytrium/LimboAPI) Place it in the same folder
4. Restart your Velocity proxy

### Step 2: Initial Configuration
1. Navigate to `plugins/relishauth/`
2. Edit `config.yml` to configure your authentication method
3. Set up your database connection
4. Configure Discord bot (optional but recommended)

### Step 3: Discord Bot Setup (Optional)
1. Create a Discord application at [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a bot and copy the token
3. Add the token to your `config.yml`
4. Invite the bot to your Discord server with appropriate permissions

---

## ⚙️ Configuration

### Basic Configuration

```yaml
# Choose your authentication method
authentication:
  method: "password"  # Options: password, discord
  premium-auto-login: true
  allow-bedrock-players: true

# Session management
session:
  duration: "5m"  # Options: 0, 1m, 5m, 15m, 30m, 1h
  allow-different-locations: true

# Database setup
database:
  type: "sqlite"  # Options: sqlite, mysql, mariadb
  sqlite:
    path: "data.db"
```

### Authentication Methods

#### 🔑 Password Authentication
Perfect for traditional servers wanting secure password-based auth:

```yaml
authentication:
  method: "password"
  password:
    min-length: 6
    max-length: 32
    hashing: "argon2"  # Secure password hashing
```

**How it works:**
1. New players create a password when first joining
2. Returning players enter their password to authenticate
3. Sessions are saved based on configured duration
4. Optional Discord linking for additional features

![Password Authentication](https://cdn.modrinth.com/data/8BqplnPe/images/482f84bec2457504c15d6740797fb288cca91ba3_350.webp)

#### 💬 Discord Authentication
Ideal for Discord-centric communities:

```yaml
authentication:
  method: "discord"
discord:
  bot-token: "YOUR_BOT_TOKEN"
  server-id: "YOUR_DISCORD_SERVER_ID"
```

**How it works:**
1. Players enter their Discord username in-game
2. Bot sends verification DM with interactive buttons
3. Players click "Verify" to authenticate
4. Account is permanently linked to Discord

![Discord Authentication](https://cdn.modrinth.com/data/8BqplnPe/images/7256b19ae90fd624265c0c7b315913d905027b65_350.webp)
![DM Verify Message](https://cdn.modrinth.com/data/8BqplnPe/images/675ea2b97cc4779f296b18a9a99b53ef0873f498.png)

#### 🏆 Premium Auto-Login
Streamlined experience for premium players:

```yaml
authentication:
  premium-auto-login: true
  allow-premium-offline: false  # Security: prevent impersonation
```

**How it works:**
1. Premium accounts are automatically verified via Mojang API
2. No password or Discord verification required
3. Instant server access for legitimate premium players
4. Cracked clients cannot impersonate premium accounts

![Premium Auto-Login](https://cdn.modrinth.com/data/8BqplnPe/images/84f7afdd2df2b2fe607e3b457fe10e1c78f91beb.gif)

### Security Configuration

```yaml
security:
  authentication-timeout: 300  # 5 minutes to authenticate
  password-attempts:
    max-attempts: 3
    lock-duration: 15  # Minutes
  premium:
    verification-timeout: 5
    api-url: "https://api.mojang.com/users/profiles/minecraft/"
```

### Limbo World Customization

```yaml
customization:
  limbo:
    dimension: "THE_END"  # OVERWORLD, NETHER, THE_END
    gamemode: "SPECTATOR"
    spawn:
      x: 0
      y: 64
      z: 0
    block-movement: true
```

## 🎮 Commands

### Player Commands

| Command | Description | Usage |
|---------|-------------|-------|
| `/ro password <pass> <confirm>` | Set/change password | `/ro password mypass123 mypass123` |
| `/ro discord <username>` | Link Discord account | `/ro discord john_doe` |
| `/ro logout` | Clear all sessions | `/ro logout` |
| `/ro session [duration]` | Set session duration | `/ro session 30m` |
| `/ro notify <on/off>` | Toggle join notifications | `/ro notify on` |
| `/ro unlink` | Unlink Discord account | `/ro unlink` |
| `/ro info` | View account information | `/ro info` |

### Admin Commands

| Command | Description | Usage |
|---------|-------------|-------|
| `/ro reload` | Reload configuration | `/ro reload` |
| `/ro info <player>` | View player information | `/ro info PlayerName` |
| `/ro unlink <player>` | Unlink player's Discord | `/ro unlink PlayerName` |
| `/ro block <username> <ip>` | Block username from IP | `/ro block Griefer 192.168.1.1` |
| `/ro unblock <identifier>` | Unblock username/IP | `/ro unblock Griefer` |
---

## 🤖 Discord Bot Integration

### Setup Process

1. **Create Discord Application**
   - Go to [Discord Developer Portal](https://discord.com/developers/applications)
   - Click "New Application" and give it a name
   - Navigate to "Bot" section and create a bot

2. **Configure Bot Permissions**
   Required permissions:
   - Send Messages
   - Use Slash Commands
   - Manage Roles (for linked role)
   - Read Message History

3. **Invite Bot to Server**
   ```
   https://discord.com/api/oauth2/authorize?client_id=YOUR_BOT_ID&permissions=268435456&scope=bot%20applications.commands
   ```

4. **Configure in RelishAuth**
   ```yaml
   discord:
     bot-token: "YOUR_BOT_TOKEN"
     server-id: "YOUR_DISCORD_SERVER_ID"
     linked-role-id: "ROLE_ID_FOR_LINKED_USERS"
   ```

### Discord Slash Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/link` | Instructions for linking account | Everyone |
| `/session [duration]` | Set session duration | Linked users |
| `/notifications [toggle]` | Toggle join notifications | Linked users |
| `/info [player]` | View account information | Admin |
| `/kick <player>` | Kick player from server | Admin |
| `/unlink <player>` | Unlink player's account | Admin |
| `/block <username> <ip>` | Block username from IP | Admin |
| `/unblock <identifier>` | Unblock username/IP | Admin |
| `/reload` | Reload plugin configuration | Admin |

---
## 📞 **Support & Links**

<div align="center">

[![Discord](https://img.shields.io/badge/Discord-Support-7289da?style=for-the-badge&logo=discord)](https://discord.gg/jDr2KZcGXk)
[![Issues](https://img.shields.io/badge/🐛%20Issues-Report-orange?style=for-the-badge)](https://github.com/iM5LB/relishauth/issues)
[![GitHub](https://img.shields.io/badge/GitHub-Source-black?style=for-the-badge&logo=github)](https://github.com/im5lb/relishauth)
</div>

---

<div align="center">

**Made with ❤️ by the Relish Development Team**

</div>
