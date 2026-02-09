# RelishAuth

**Advanced Authentication System for Velocity Proxy Servers** 🔐

RelishAuth is a powerful, feature-rich authentication plugin designed specifically for Velocity proxy servers. It provides multiple authentication methods, Discord integration, premium account support, and robust security features to protect your Minecraft network.

## ✨ Key Features

### 🔐 Multi-Method Authentication
- **Password Authentication** - Traditional secure password system with Argon2 hashing
- **Discord Integration** - Link Discord accounts for seamless authentication
- **Premium Auto-Login** - Automatic authentication for premium Minecraft accounts
- **Hybrid Mode** - Combine password + Discord for maximum security

### 🛡️ Advanced Security
- **Session Management** - Configurable session durations (0-1 hour)
- **IP Validation** - Optional IP-based session validation
- **Rate Limiting** - Protection against brute force attacks
- **Premium Verification** - Real-time Mojang API validation
- **Bedrock Support** - Compatible with Floodgate for cross-platform play

### 🤖 Discord Bot Integration
- **Real-time Verification** - Instant Discord DM verification
- **Join Notifications** - Security alerts when someone joins with your account
- **Account Management** - Change passwords, manage sessions via Discord
- **Admin Commands** - Full server management through Discord slash commands
- **Rich Embeds** - Beautiful, informative Discord messages

### 🌐 Multi-Language Support
- **English** and **Arabic** language packs included
- **Customizable Messages** - Full message customization support

### 💾 Flexible Database Support
- **SQLite** - Zero-configuration local database (default)
- **MySQL/MariaDB** - Network database support for multi-server setups
- **Connection Pooling** - High-performance HikariCP integration

## 🚀 Quick Start

Get RelishAuth up and running in minutes:

1. **Download** the plugin JAR file
2. **Install** [LimboAPI](https://github.com/Elytrium/LimboAPI) (required dependency)
3. **Place** both JARs in your Velocity `plugins/` folder
4. **Restart** your Velocity proxy
5. **Configure** `plugins/relishauth/config.yml` to your needs
6. **Optional**: Set up Discord bot for enhanced features

[View detailed installation guide →](Installation.md)

## 📖 Documentation

### Getting Started
- [Quick Start Guide](QuickStart.md) - Get up and running in 5 minutes
- [Installation](Installation.md) - Detailed installation instructions
- [Configuration](Configuration.md) - Complete configuration guide

### Features
- [Authentication Methods](AuthMethods.md) - Password, Discord, and Premium authentication
- [Discord Bot Setup](DiscordBot.md) - Set up Discord integration
- [Commands](Commands.md) - Player and admin commands
- [Permissions](Permissions.md) - Permission nodes and setup

### Advanced
- [Security Settings](Security.md) - Security features and best practices
- [Database Setup](Database.md) - SQLite and MySQL configuration
- [Developer API](API.md) - API for developers
- [Troubleshooting](Troubleshooting.md) - Common issues and solutions

## 🎯 Use Cases

### Community Servers
Perfect for community servers that want Discord-integrated authentication:
- Players link their Discord accounts
- Instant verification through Discord DMs
- Join notifications for account security
- Admin management through Discord commands

### Premium Networks
Ideal for networks with premium and cracked player support:
- Premium players auto-login instantly
- Cracked players use password authentication
- Session management for convenience
- IP validation for security

### High-Security Servers
For servers requiring maximum security:
- Hybrid authentication (password + Discord)
- Short session durations
- IP validation enabled
- Rate limiting and brute force protection

## 🔧 Requirements

- **Velocity Proxy** 3.4.0 or higher
- **Java** 21 or higher
- **LimboAPI** plugin (required dependency)
- **Discord Bot** (optional, for Discord features)

## 📞 Support & Links

- **Discord**: [Join our Discord](https://discord.gg/jDr2KZcGXk) for support and updates
- **Modrinth**: [Download from Modrinth](https://modrinth.com/plugin/relishauth)
- **GitHub**: [View on GitHub](https://github.com/iM5LB/relishauth)
- **Donate**: [Support development](https://creators.sa/m5lb)

---

**Made with ❤️ by the Relish Development Team**
