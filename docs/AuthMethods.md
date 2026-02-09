# Authentication Methods

RelishAuth supports multiple authentication methods to suit different server types and security requirements. This guide explains each method and how to configure them.

## Overview

RelishAuth offers three main authentication approaches:

1. **Password Authentication** - Traditional password-based system
2. **Discord Authentication** - Discord account linking
3. **Premium Auto-Login** - Automatic authentication for premium accounts

You can also combine these methods for enhanced security.

## Password Authentication

### How It Works

1. New players create a password when first joining
2. Returning players enter their password to authenticate
3. Sessions are saved based on configured duration
4. Optional Discord linking for additional features

### Configuration

```yaml
authentication:
  method: "password"
  premium-auto-login: true  # Optional
  password:
    min-length: 6
    max-length: 32
    require-uppercase: false
    require-lowercase: false
    require-numbers: false
    require-special-chars: false
    hashing: "argon2"
```

### Player Experience

**First Join**:
```
Welcome to the server!
Please create a password to secure your account.
Use: /ra password <password> <confirm>
```

**Returning Players**:
```
Welcome back, PlayerName!
Please enter your password to continue.
Use: /ra password <your_password>
```

### Password Requirements

Configure password strength:

**Weak (Not Recommended)**:
```yaml
password:
  min-length: 4
  max-length: 16
```

**Moderate (Default)**:
```yaml
password:
  min-length: 6
  max-length: 32
```

**Strong (Recommended)**:
```yaml
password:
  min-length: 8
  max-length: 32
  require-uppercase: true
  require-numbers: true
  require-special-chars: true
```

### Security Features

- **Argon2 Hashing**: Industry-standard password hashing
- **Rate Limiting**: Protection against brute force attacks
- **Attempt Limiting**: Lock accounts after failed attempts
- **Session Management**: Configurable session durations

### Best For

- Traditional Minecraft servers
- Servers without Discord integration
- Players who prefer password authentication
- Servers with mixed player preferences

## Discord Authentication

### How It Works

1. Players enter their Discord username in-game
2. Bot sends verification DM with interactive buttons
3. Players click "Verify" to authenticate
4. Account is permanently linked to Discord

### Configuration

```yaml
authentication:
  method: "discord"
  premium-auto-login: true  # Optional
  enforce-discord-account-match: true

discord:
  bot-token: "YOUR_BOT_TOKEN_HERE"
  server-id: "YOUR_DISCORD_SERVER_ID"
  linked-role-id: "ROLE_ID_FOR_LINKED_USERS"
  invite-link: "https://discord.gg/your-invite"
```

### Player Experience

**First Join**:
```
Welcome to the server!
Please link your Discord account to continue.
Enter your Discord username: username
```

**Discord DM**:
```
🔐 Account Verification

PlayerName is trying to link this Discord account.
Click "Verify" to confirm, or "Deny" if this wasn't you.

[Verify] [Deny]
```

**After Verification**:
```
✅ Account linked successfully!
You can now join the server anytime.
```

### Discord Bot Setup

1. Create Discord application at [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a bot and copy the token
3. Enable these intents:
   - Server Members Intent
   - Message Content Intent
4. Invite bot with these permissions:
   - Send Messages
   - Use Slash Commands
   - Manage Roles
   - Read Message History

[View complete Discord setup guide →](DiscordBot.md)

### Security Features

- **Real-time Verification**: Instant Discord DM verification
- **Join Notifications**: Alerts when someone joins with your account
- **Account Management**: Change settings via Discord
- **Admin Commands**: Full server management through Discord

### Best For

- Discord-centric communities
- Servers with active Discord presence
- Enhanced security requirements
- Servers wanting join notifications

## Premium Auto-Login

### How It Works

1. Premium accounts are automatically verified via Mojang API
2. No password or Discord verification required
3. Instant server access for legitimate premium players
4. Cracked clients cannot impersonate premium accounts

### Configuration

```yaml
authentication:
  premium-auto-login: true
  allow-premium-offline: false  # Security: prevent impersonation

security:
  premium:
    verification-timeout: 5
    api-connect-timeout: 5000
    api-read-timeout: 5000
    api-url: "https://api.mojang.com/users/profiles/minecraft/"
```

### Player Experience

**Premium Players**:
```
Welcome, PremiumPlayer!
Your premium account has been verified.
Connecting to the server...
```

**Cracked Players** (if premium-auto-login is enabled):
```
Welcome to the server!
Please authenticate to continue.
[Password or Discord authentication prompt]
```

### Security Considerations

**Safe Configuration** (Recommended):
```yaml
authentication:
  premium-auto-login: true
  allow-premium-offline: false  # Prevents impersonation
```

**Unsafe Configuration** (Not Recommended):
```yaml
authentication:
  premium-auto-login: true
  allow-premium-offline: true  # ⚠️ Allows impersonation!
```

### Best For

- Servers with premium and cracked player support
- Streamlined experience for premium players
- Reducing authentication friction
- Networks with mixed player types

## Hybrid Authentication

### Password + Discord

Combine password and Discord for maximum security:

```yaml
authentication:
  method: "password"
  premium-auto-login: true
```

Players can:
- Authenticate with password
- Optionally link Discord for notifications
- Use Discord commands for account management

### Benefits

- **Flexibility**: Players choose their preferred method
- **Enhanced Security**: Discord provides additional verification
- **Join Notifications**: Security alerts via Discord
- **Account Recovery**: Discord-based password reset

### Configuration

```yaml
authentication:
  method: "password"
  premium-auto-login: true

discord:
  bot-token: "YOUR_BOT_TOKEN_HERE"
  server-id: "YOUR_DISCORD_SERVER_ID"
  
  join-notifications:
    default-enabled: true
    cooldown: 60
```

## Bedrock Edition Support

### Configuration

```yaml
authentication:
  allow-bedrock-players: true
```

### Requirements

- **Floodgate** plugin installed on Velocity
- Bedrock players have `.` prefix (e.g., `.BedrockPlayer`)

### How It Works

Bedrock players authenticate the same way as Java players:
- Password authentication works normally
- Discord authentication works normally
- Premium auto-login not applicable (Bedrock accounts)

## Switching Authentication Methods

### From Password to Discord

1. Update configuration:
   ```yaml
   authentication:
     method: "discord"
   ```

2. Reload configuration:
   ```
   /ra reload
   ```

3. Existing players:
   - Password authentication still works
   - Players can link Discord accounts
   - Gradually migrate players to Discord

### From Discord to Password

1. Update configuration:
   ```yaml
   authentication:
     method: "password"
   ```

2. Reload configuration:
   ```
   /ra reload
   ```

3. Existing players:
   - Discord-linked players can set passwords
   - Discord features remain available
   - Players can still use Discord commands

## Comparison Table

| Feature | Password | Discord | Premium Auto-Login |
|---------|----------|---------|-------------------|
| Setup Complexity | Low | Medium | Low |
| Security Level | High | Very High | Medium |
| User Convenience | Medium | High | Very High |
| Discord Required | No | Yes | No |
| Join Notifications | No | Yes | No |
| Account Recovery | Manual | Via Discord | N/A |
| Admin Management | In-game | Discord + In-game | In-game |
| Session Management | Yes | Yes | No |

## Recommendations

### For Community Servers
- **Method**: Discord
- **Premium Auto-Login**: Enabled
- **Why**: Seamless integration with Discord community

### For Public Servers
- **Method**: Password
- **Premium Auto-Login**: Enabled
- **Why**: No Discord requirement, accessible to all

### For High-Security Servers
- **Method**: Password + Discord (Hybrid)
- **Premium Auto-Login**: Disabled
- **Why**: Maximum security with multiple verification layers

### For Casual Servers
- **Method**: Password
- **Premium Auto-Login**: Enabled
- **Why**: Simple setup, low maintenance

## Next Steps

- [Configure security settings](Security.md)
- [Set up Discord bot](DiscordBot.md)
- [Learn commands](Commands.md)
- [Configure database](Database.md)

---

Need help choosing an authentication method? [Join our Discord](https://discord.gg/jDr2KZcGXk) for advice!
