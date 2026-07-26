# RelishAuth

**Advanced Authentication System for Velocity Proxy Servers**

RelishAuth is a powerful authentication plugin designed for Velocity proxy servers. It provides multiple authentication methods, Discord integration, premium account support, and robust security features to protect your Minecraft network.

## ✨ Key Features

### 🔐 Multi-Method Authentication
- **Password Authentication** - Traditional secure password system with Argon2 / BCrypt hashing
- **Discord Integration** - Link Discord accounts for seamless authentication
- **Premium Auto-Login** - Automatic authentication for premium Minecraft accounts
- **Hybrid Mode** - Password first, then required Discord linking before leaving limbo
- **Login Method Chooser** - When an account has both password and Discord linked, pick how to log in

### 🛡️ Advanced Security
- **Session Management** - Configurable session durations (0-1 hour)
- **IP Validation** - Optional IP-based session validation
- **Rate Limiting** - Protection against brute force attacks
- **Premium Verification** - Real-time Mojang API validation
- **Bedrock Support** - Floodgate players authenticate via limbo chat/titles (no Cumulus forms in limbo)
- **Unlink Safety** - Discord unlink can clear the account password so it cannot be reused

### 🤖 Discord Bot Integration
- **Real-time Verification** - Instant Discord DM verification
- **Join Notifications** - Security alerts when someone joins with your account
- **Account Management** - Change passwords, manage sessions via Discord
- **Admin Commands** - Full server management through Discord slash commands
- **Group Sync** - Optional Discord role → LuckPerms group mapping
- **Rich Embeds** - Beautiful, informative Discord messages

### 🌐 Multi-Language Support
- **English** and **Arabic** language packs included
- **Customizable Messages** - Full message customization support

### 💾 Flexible Database Support
- **SQLite** - Zero-configuration local database (default)
- **MySQL/MariaDB** - Network database support for multi-server setups
- **PostgreSQL** - Enterprise-grade database with advanced features
- **Connection Pooling** - High-performance HikariCP integration

## 📥 Download

- **Modrinth**: [Download RelishAuth](https://modrinth.com/plugin/relishauth)
- **GitHub**: [View on GitHub](https://github.com/iM5LB/relishauth)

## 📚 Documentation

- [Quick Start Guide](QuickStart.md) - Get up and running in 5 minutes
- [Installation Guide](Installation.md) - Detailed installation instructions
- [Configuration Guide](Configuration.md) - Complete configuration reference
- [Authentication Methods](AuthMethods.md) - Password, Discord, and Premium auth
- [Discord Bot Setup](DiscordBot.md) - Set up Discord integration
- [Commands Reference](Commands.md) - All available commands
- [Security Settings](Security.md) - Security features and best practices

## 🎯 Quick Start

1. Download RelishAuth from [Modrinth](https://modrinth.com/plugin/relishauth)
2. Download [LimboAPI](https://github.com/Elytrium/LimboAPI) (required dependency)
3. Place both JARs in your Velocity `plugins/` folder
4. Restart your Velocity proxy
5. Configure `plugins/relishauth/config.yml`
6. Optional: Set up Discord bot for enhanced features

[View detailed quick start guide →](QuickStart.md)

## 💬 Support

- **Discord**: [Join our server](https://discord.gg/jDr2KZcGXk)
- **Issues**: [Report on GitHub](https://github.com/iM5LB/relishauth/issues)
- **Documentation**: [Full docs](https://im5lb.github.io/relishauth/)

## 🔧 Requirements

- **Velocity Proxy**: 3.4.0 or higher
- **Java**: 21 or higher
- **LimboAPI**: Required dependency
- **Floodgate**: Optional, for Bedrock players
- **LuckPerms** (Velocity): Optional, for Discord → group sync
- **Discord Bot**: Optional, for Discord features

## 🌟 Why RelishAuth?

- **Multiple Auth Methods** - Password, Discord, hybrid, or Premium auto-login
- **Highly Secure** - Argon2/BCrypt hashing, rate limiting, IP validation
- **Discord Integration** - Full Discord bot with slash commands
- **Multi-Language** - English and Arabic support
- **Flexible Database** - SQLite, MySQL/MariaDB, or PostgreSQL
- **Active Development** - Regular updates and new features

## 🎮 Use Cases

### Community Servers
Perfect for Discord-centric communities with seamless Discord authentication and join notifications.

### Premium Networks
Ideal for networks with premium and cracked player support - premium players auto-login instantly.

### High-Security Servers
For servers requiring maximum security with hybrid authentication and strict session management.

## 📊 Authentication Methods

| Method | Setup | Security | Best For |
|--------|-------|----------|----------|
| **Password** | Easy | High | Traditional servers |
| **Discord** | Medium | Very High | Discord communities |
| **Premium** | Easy | Medium | Premium players |
| **Hybrid** | Medium | Maximum | High-security servers |

[Learn more about authentication methods →](AuthMethods.md)

## 🚀 Features Overview

### Player Features
- Create and manage passwords
- Link Discord accounts
- Configurable session durations
- Join notifications via Discord
- Account information display

### Admin Features
- Reload configuration without restart
- View player account information
- Unlink player Discord accounts
- Block usernames from specific IPs
- Set / reset passwords
- Sync Discord roles → LuckPerms (`/ra syncgroups`)
- Full Discord admin commands

### Security Features
- Argon2 / BCrypt password hashing
- Brute force protection
- Session management with IP validation
- Premium account verification
- Authentication timeout system
- Optional password clear on Discord unlink

[View complete feature list →](Configuration.md)

---

<div align="center">

**Made with ❤️ by M5LB**

[Discord](https://discord.gg/jDr2KZcGXk) • [Documentation](https://im5lb.github.io/relishauth/) • [Download](https://modrinth.com/plugin/relishauth)

</div>
