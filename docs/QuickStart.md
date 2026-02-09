# Quick Start Guide

Get RelishAuth up and running in just 5 minutes! This guide will walk you through the basic setup to get your authentication system working.

## Prerequisites

Before you begin, make sure you have:
- Velocity proxy server (3.4.0 or higher)
- Java 21 or higher
- Access to your server files

## Step 1: Download Required Files

1. Download **RelishAuth** plugin JAR
2. Download **[LimboAPI](https://github.com/Elytrium/LimboAPI)** plugin JAR (required dependency)

## Step 2: Install Plugins

1. Navigate to your Velocity proxy folder
2. Place both JAR files in the `plugins/` directory:
   ```
   velocity/
   └── plugins/
       ├── RelishAuth-x.x.x.jar
       └── LimboAPI-x.x.x.jar
   ```

## Step 3: Start Your Server

1. Start (or restart) your Velocity proxy
2. Wait for the server to fully load
3. RelishAuth will generate its configuration files

## Step 4: Basic Configuration

Navigate to `plugins/relishauth/config.yml` and configure the basics:

### Choose Authentication Method

**For Password Authentication** (recommended for beginners):
```yaml
authentication:
  method: "password"
  premium-auto-login: true
```

**For Discord Authentication**:
```yaml
authentication:
  method: "discord"
  premium-auto-login: true

discord:
  bot-token: "YOUR_BOT_TOKEN_HERE"
  server-id: "YOUR_DISCORD_SERVER_ID"
```

### Set Session Duration

Choose how long players stay logged in:
```yaml
session:
  duration: "5m"  # Options: 0, 1m, 5m, 15m, 30m, 1h
```

## Step 5: Reload Configuration

Run this command in your Velocity console:
```
/ra reload
```

## Step 6: Test It Out!

1. Join your server with a Minecraft client
2. You'll be placed in the authentication limbo world
3. Follow the on-screen instructions to authenticate

### For Password Method:
- First-time players: `/ra password <password> <confirm>`
- Returning players: Enter your password when prompted

### For Discord Method:
- Enter your Discord username when prompted
- Check your Discord DMs for verification button
- Click "Verify" to authenticate

## What's Next?

Now that you have basic authentication working, explore these features:

### Enhance Security
- [Configure security settings](Security.md)
- [Set up IP validation](Security.md#ip-validation)
- [Enable rate limiting](Security.md#rate-limiting)

### Add Discord Integration
- [Set up Discord bot](DiscordBot.md)
- [Configure Discord commands](DiscordBot.md#slash-commands)
- [Enable join notifications](DiscordBot.md#join-notifications)

### Customize Experience
- [Change language](Configuration.md#language)
- [Customize limbo world](Configuration.md#limbo-world)
- [Configure session options](Configuration.md#session-management)

### Database Setup
- [Switch to MySQL](Database.md#mysql-setup) for multi-server networks
- [Configure connection pooling](Database.md#connection-pooling)

## Common Issues

### Players can't authenticate
- Make sure LimboAPI is installed
- Check that both plugins loaded successfully in console
- Verify your configuration syntax is correct

### Discord bot not responding
- Verify bot token is correct
- Make sure bot is invited to your Discord server
- Check bot has required permissions

### Premium players can't join
- Ensure `premium-auto-login: true` is set
- Check Mojang API is accessible from your server
- Verify `allow-premium-offline: false` for security

## Need Help?

- **Full Documentation**: Browse the sidebar for detailed guides
- **Discord Support**: [Join our Discord](https://discord.gg/jDr2KZcGXk)
- **Troubleshooting**: [View common issues](Troubleshooting.md)

---

**Congratulations!** 🎉 You've successfully set up RelishAuth. Your server is now protected with advanced authentication.
