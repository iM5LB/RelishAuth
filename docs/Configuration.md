# Configuration Guide

This guide covers all configuration options available in RelishAuth. The main configuration file is located at `plugins/relishauth/config.yml`.

## Core Settings

### Language

Set the language for all plugin messages:

```yaml
language: "en"  # Options: en (English), ar (Arabic)
```

Language files are located in `plugins/relishauth/lang/`:
- `en.yml` - English messages
- `ar.yml` - Arabic messages

You can customize messages by editing these files directly.

### Debug Mode

Enable detailed logging for troubleshooting:

```yaml
debug: false  # Set to true for verbose logging
```

When enabled, RelishAuth will log:
- Authentication attempts
- Database queries
- Discord API calls
- Session management events

### Auto-Update

Automatically update configuration with new keys:

```yaml
auto-update:
  enabled: true
  check-on-startup: true
```

This feature:
- Adds new configuration keys from updates
- Preserves your existing settings
- Creates backups before updating

### Admin Players

Fallback admin list when no permission plugin is available:

```yaml
admin-players:
  - "PlayerName1"
  - "PlayerName2"
```

**Note**: Use a permission plugin like LuckPerms for better permission management.

## Authentication Settings

### Authentication Method

Choose your primary authentication method:

```yaml
authentication:
  method: "password"  # Options: password, discord
```

**Password Method**:
- Players create a password when first joining
- Traditional authentication system
- Works without Discord bot

**Discord Method**:
- Players link their Discord account
- Verification through Discord DMs
- Requires Discord bot setup

[Learn more about authentication methods →](AuthMethods.md)

### Premium Auto-Login

Allow premium Minecraft accounts to skip authentication:

```yaml
authentication:
  premium-auto-login: true
```

When enabled:
- Premium accounts are verified via Mojang API
- No password or Discord verification needed
- Instant server access for legitimate premium players

**Security Note**: Premium players bypass ALL authentication checks and session management.

### Premium Offline Mode

Allow premium usernames in offline/cracked mode:

```yaml
authentication:
  allow-premium-offline: false  # Keep false for security
```

**⚠️ Security Warning**: Enabling this allows anyone to impersonate premium accounts. Only enable if you understand the risks.

### Bedrock Players

Allow Bedrock Edition players (requires Floodgate):

```yaml
authentication:
  allow-bedrock-players: true
```

When disabled, Bedrock players will be blocked from joining.

### Discord Account Enforcement

Prevent Discord account switching after unlinking:

```yaml
authentication:
  enforce-discord-account-match: true
```

When enabled, players can only re-link the same Discord account they originally used.

### Password Requirements

Configure password strength requirements:

```yaml
authentication:
  password:
    min-length: 6
    max-length: 32
    require-uppercase: false
    require-lowercase: false
    require-numbers: false
    require-special-chars: false
```

**Example - Strong Password Policy**:
```yaml
password:
  min-length: 8
  max-length: 32
  require-uppercase: true
  require-numbers: true
  require-special-chars: true
```

### Password Hashing

Configure Argon2 password hashing:

```yaml
authentication:
  password:
    hashing: "argon2"  # Only Argon2 is supported
    argon2:
      iterations: 10      # Computational cost
      memory: 65536       # Memory cost in KB
      parallelism: 1      # Parallel threads
```

**Performance vs Security**:
- Higher values = more secure, but slower
- Lower values = faster, but less secure
- Default values provide good balance

## Session Management

### Session Duration

How long players stay logged in:

```yaml
session:
  duration: "5m"  # Options: 0, 1m, 5m, 15m, 30m, 1h
```

**Duration Options**:
- `0` - No session (authenticate every time)
- `1m` - 1 minute
- `5m` - 5 minutes (default)
- `15m` - 15 minutes
- `30m` - 30 minutes
- `1h` - 1 hour (maximum)

**Note**: Premium auto-login players bypass session checks entirely.

### IP Validation

Require same IP address for session continuation:

```yaml
session:
  allow-different-locations: true
```

When `false`:
- Players must authenticate if IP changes
- More secure, but less convenient
- Recommended for high-security servers

When `true`:
- Players can continue session from different IPs
- More convenient, but less secure
- Recommended for players with dynamic IPs

### Available Durations

Durations players can choose with `/ra session`:

```yaml
session:
  available-durations:
    - "0"
    - "1m"
    - "5m"
    - "15m"
    - "30m"
    - "1h"
```

## Security Settings

### Authentication Timeout

Seconds before kicking unauthenticated players:

```yaml
security:
  authentication-timeout: 300  # 5 minutes
```

### Timeout Warnings

Warn players before timeout:

```yaml
security:
  timeout-warnings:
    warning-threshold: 30  # Start warnings at 30 seconds
    warning-interval: 10   # Warn every 10 seconds
```

### Password Attempts

Limit failed password attempts:

```yaml
security:
  password-attempts:
    max-attempts: 3
    lock-duration: 15  # Minutes
```

After max attempts, player is locked out for the specified duration.

### Premium Verification

Configure Mojang API verification:

```yaml
security:
  premium:
    verification-timeout: 5
    api-connect-timeout: 5000  # Milliseconds
    api-read-timeout: 5000     # Milliseconds
    api-url: "https://api.mojang.com/users/profiles/minecraft/"
```

[Learn more about security settings →](Security.md)

## Limbo World Customization

### Dimension

Choose the limbo world dimension:

```yaml
customization:
  limbo:
    dimension: "THE_END"  # Options: OVERWORLD, NETHER, THE_END
```

**Dimension Characteristics**:
- `OVERWORLD` - Normal world, bright
- `NETHER` - Red tint, darker
- `THE_END` - Dark, void-like (default)

### Spawn Location

Set spawn coordinates:

```yaml
customization:
  limbo:
    spawn:
      x: 0
      y: 64
      z: 0
```

### Game Mode

Set player game mode in limbo:

```yaml
customization:
  limbo:
    gamemode: "SPECTATOR"  # Options: SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR
```

**Recommended**: `SPECTATOR` prevents players from interacting with the world.

### World Time

Set time of day:

```yaml
customization:
  limbo:
    world-time: 6000  # 0 = dawn, 6000 = noon, 12000 = dusk, 18000 = midnight
```

### Movement Control

Block player movement in limbo:

```yaml
customization:
  limbo:
    block-movement: true
    movement-threshold: 0.1  # Sensitivity
```

When enabled, players cannot move while authenticating.

### Timing

Configure message delays:

```yaml
customization:
  limbo:
    timing:
      auth-prompt-delay: 100      # Milliseconds
      discord-prompt-delay: 1000  # Milliseconds
    
    title:
      fade-in: 500    # Milliseconds
      stay: 999999    # Milliseconds (very long)
      fade-out: 500   # Milliseconds
```

### Timeout Monitor

How often to check for authentication timeout:

```yaml
customization:
  limbo:
    monitor:
      check-interval: 1  # Seconds
```

## Command Settings

### Logout Delay

Delay before disconnecting after logout:

```yaml
commands:
  logout:
    disconnect-delay: 100  # Milliseconds
```

### Slash Commands

Enable/disable specific commands:

```yaml
commands:
  commands:
    enabled: true
    
    player-commands:
      password: true
      notify: true
      logout: true
      session: true
      info: true
      unlink: true
    
    admin-commands:
      info-others: true
      unlink-others: true
      reload: true
      block: true
      unblock: true
```

Set to `false` to disable specific commands.

## Configuration Examples

### High-Security Server

```yaml
authentication:
  method: "password"
  premium-auto-login: false
  allow-premium-offline: false
  password:
    min-length: 8
    require-uppercase: true
    require-numbers: true

session:
  duration: "0"  # No session
  allow-different-locations: false

security:
  authentication-timeout: 180
  password-attempts:
    max-attempts: 3
    lock-duration: 30
```

### Community Server

```yaml
authentication:
  method: "discord"
  premium-auto-login: true
  allow-bedrock-players: true

session:
  duration: "1h"
  allow-different-locations: true

discord:
  bot-token: "YOUR_TOKEN"
  server-id: "YOUR_SERVER_ID"
  linked-role-id: "ROLE_ID"
```

### Casual Server

```yaml
authentication:
  method: "password"
  premium-auto-login: true
  password:
    min-length: 4
    max-length: 16

session:
  duration: "1h"
  allow-different-locations: true

security:
  authentication-timeout: 600
  password-attempts:
    max-attempts: 5
    lock-duration: 5
```

## Reloading Configuration

After making changes, reload the configuration:

```
/ra reload
```

Or restart your Velocity proxy.

## Next Steps

- [Set up authentication methods](AuthMethods.md)
- [Configure Discord bot](DiscordBot.md)
- [Set up database](Database.md)
- [Configure security](Security.md)
- [Learn commands](Commands.md)

---

Need help with configuration? [Join our Discord](https://discord.gg/jDr2KZcGXk) for support!
