# Authentication Methods

RelishAuth supports multiple authentication methods to suit different server types and security requirements. This guide explains each method and how to configure them.

## Overview

RelishAuth offers these authentication approaches:

1. **Password Authentication** - Traditional password-based system
2. **Discord Authentication** - Discord account linking
3. **Hybrid Authentication** - Password + required Discord linking
4. **Premium Auto-Login** - Automatic authentication for premium accounts
5. **Login Method Chooser** - When both password and Discord are set, pick how to log in

You can combine password/Discord features with premium auto-login and Bedrock (Floodgate) support.

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
Type your new password in chat (or use /ra password).
```

**Returning Players**:
```
Welcome back, PlayerName!
Please enter your password to continue.
Type your password in chat (or use /ra password).
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
5. If no password is set, a chat tip can appear on backend join (`set-password-tip-on-join`)

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
 Account Verification

PlayerName is trying to link this Discord account.
Click "Verify" to confirm, or "Deny" if this wasn't you.

[Verify] [Deny]
```

**After Verification**:
```
 Account linked successfully!
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
  allow-premium-username-impersonation: false  # Security: prevent impersonation

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
  allow-premium-username-impersonation: false  # Prevents impersonation
```

**Unsafe Configuration** (Not Recommended):
```yaml
authentication:
  premium-auto-login: true
  allow-premium-username-impersonation: true  # Warning: Allows impersonation!
```

### Backend UUIDs (Optional)

If you run an offline-mode proxy but want backend Paper servers to see Mojang UUIDs for premium players:

```yaml
authentication:
  premium-use-official-uuid: true
  premium-use-official-uuid-migrate-database: true
```

**Important**: This changes player identity on the backend (inventories/claims/permissions may not match old offline UUID data).

### Best For

- Servers with premium and cracked player support
- Streamlined experience for premium players
- Reducing authentication friction
- Networks with mixed player types

## Hybrid Authentication

### Password + Discord (required)

Require both a password and a linked Discord account before players leave limbo:

```yaml
authentication:
  method: "hybrid"
  premium-auto-login: true
```

Flow:
1. Player registers/logs in with a password
2. RelishAuth then requires Discord linking (DM verification)
3. Only after both succeed does the player join the backend

Premium players with auto-login still must have Discord linked in hybrid mode.
The login method chooser is **disabled** in hybrid mode (both factors are always required).

### Benefits

- **Maximum security**: Password alone is not enough
- **Account binding**: Discord identity is mandatory
- **Join Notifications**: Security alerts via Discord
- **Admin tooling**: Discord slash commands remain available

### Configuration

```yaml
authentication:
  method: "hybrid"
  premium-auto-login: true

discord:
  bot-token: "YOUR_BOT_TOKEN_HERE"
  server-id: "YOUR_DISCORD_SERVER_ID"
  
  join-notifications:
    default-enabled: true
    cooldown: 60
```

## Login Method Chooser

When an account already has **both** a password and a linked Discord account, RelishAuth can show a chooser in limbo instead of forcing one method.

```yaml
authentication:
  method: "password"   # or "discord"
  login-chooser:
    enabled: true
```

### When It Appears

- Account has a password **and** Discord linked
- `login-chooser.enabled` is `true`
- Auth method is `password` or `discord` (not `hybrid`)

### How Players Choose

In limbo chat, type:
- `password` / `1` / `p` — password login
- `discord` / `2` / `d` — Discord verify

Java may also receive clickable chat buttons; LimboAPI often drops those components, so **typing always works**.

## Bedrock Edition Support

### Configuration

```yaml
authentication:
  allow-bedrock-players: true
```

### Requirements

- **Floodgate** plugin installed on Velocity (typically with Geyser)
- Bedrock players have `.` prefix (e.g., `.BedrockPlayer`)

### How It Works

Bedrock players authenticate in LimboAPI with **titles + chat prompts**:
- Password and Discord flows work the same as Java (type in chat)
- Login method chooser uses the same `password` / `discord` (or `1` / `2`) chat input
- **Floodgate/Cumulus forms are not used** — they cannot be delivered while the player is inside LimboAPI
- Premium auto-login is not applicable to Bedrock accounts

## Unlink & Password Tips

```yaml
authentication:
  # After Discord unlink (/ra unlink or Discord /unlink), clear password so it cannot be reused
  clear-password-on-discord-unlink: true
  # After joining backend with no password, show a chat tip (similar to Discord join-alert tip)
  set-password-tip-on-join: true
  # After unlink, only allow re-linking the same Discord account
  enforce-discord-account-match: true
```

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

| Feature | Password | Discord | Hybrid | Premium Auto-Login |
|---------|----------|---------|--------|-------------------|
| Setup Complexity | Low | Medium | Medium | Low |
| Security Level | High | Very High | Maximum | Medium |
| User Convenience | Medium | High | Medium | Very High |
| Discord Required | No | Yes | Yes | No |
| Join Notifications | Optional | Yes | Yes | No |
| Account Recovery | Manual | Via Discord | Via Discord | N/A |
| Admin Management | In-game | Discord + In-game | Discord + In-game | In-game |
| Session Management | Yes | Yes | Yes | No |
| Login Chooser | Yes* | Yes* | No | N/A |

\* Chooser only when the account already has both password and Discord linked.

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
- **Method**: `hybrid`
- **Premium Auto-Login**: Disabled (or enabled with Discord still required)
- **Why**: Maximum security with password + Discord before leaving limbo

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
