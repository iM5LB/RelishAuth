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

### Update Checking

Check for plugin updates on startup and `/ra reload`:

```yaml
check-for-updates: true
```

When enabled, RelishAuth checks GitHub releases for a newer version and logs an update message.
The download link points to the Modrinth project page.

### Config Version (Internal)

RelishAuth uses a schema version to know when it should sync new default keys into your config:

```yaml
config-version: 2
```

You normally shouldn't change this manually.

### Post-Auth Routing

Control which backend server players are sent to **after** successful authentication/auto-login:

```yaml
routing:
  # Must match a server name in velocity.toml ([servers])
  post-auth-server: "lobby"
```

If empty, RelishAuth falls back to Velocity's `attempt-connection-order` and picks the first non `limbo`/`auth` entry.

### Config Migration (No Config Needed)

RelishAuth automatically merges **missing keys** from the bundled `config.yml` into your existing `plugins/relishauth/config.yml`:
- Runs on proxy startup and `/ra reload`
- Preserves your existing values and comments where possible
- Creates a timestamped backup before writing changes
- If your YAML is invalid, it restores defaults (backup first)

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

### Premium Username Impersonation (Unsafe)

Allow cracked/offline clients to join using a username that belongs to a premium account:

```yaml
authentication:
  allow-premium-username-impersonation: false  # Keep false for security
```

**⚠️ Security Warning**: Enabling this can allow impersonation of premium accounts unless you add extra authentication checks.

### Premium Official UUID Injection (Backend UUIDs)

If you run an offline-mode proxy but want backend Paper servers to see Mojang UUIDs for premium players:

```yaml
authentication:
  premium-use-official-uuid: false
  premium-use-official-uuid-migrate-database: true
```

When enabled, RelishAuth injects the Mojang UUID into the player's login GameProfile so forwarded UUIDs on the backend are "official".

**Important**:
- This changes player identity on the backend (inventories/claims/permissions may not match old offline UUID data)
- Keep `allow-premium-username-impersonation: false` unless you fully understand the security implications

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

## Skin Restoring (Skins & Capes)

RelishAuth can inject a `textures` property into the player's **login GameProfile** so backend Paper servers receive the correct skin on offline-mode proxies.

```yaml
skins:
  enabled: true

  # Keep textures if another plugin already provides them (recommended for Bedrock/Geyser).
  preserve-existing-textures: true

  # Cape options (mainly for offline/cracked mode)
  capes:
    # Inject this cape into UNSIGNED textures payloads when the provider doesn't include a cape.
    # Value can be either:
    # - Full URL (http(s)://...)
    # - A textures.minecraft.net texture hash (64 hex chars)
    default-unsigned-cape: ""

  cache-duration: 604800  # 7 days

  api:
    username-textures-endpoint: ""     # Default: https://skinsystem.ely.by/textures/{nickname}
    username-uuid-lookup-endpoint: ""  # Default: https://api.minecraftservices.com/minecraft/profile/lookup/name/{nickname}
    uuid-session-endpoint: ""          # Default: https://sessionserver.mojang.com/session/minecraft/profile/{uuid}?unsigned=false

    timeout: 10
    login-wait-timeout: 3

    retry-attempts: 2
    retry-delay: 1000
```

**Notes**:
- **Premium** players: UUID lookup + Mojang session returns signed textures (skin + cape when available).
- **Cracked/offline** players: username textures endpoint is used as a best-effort source (often skin only).
- Capes in offline mode are not guaranteed; `default-unsigned-cape` can be used as a fallback.

## Session Management

### Session Duration

How long players can auto-login with valid sessions (without re-authenticating):

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

**How it works**: After successful authentication, players can rejoin within the session duration without needing to authenticate again. Once the session expires, they must authenticate again.

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

Durations players can choose with `/ra session` and Discord `/session` buttons:

```yaml
session:
  available-durations:
    - "0"
    - "1m"
    - "5m"
    - "15m"
    - "30m"
    - "1h"

You can add more presets using `s`, `m`, `h`, or `d` (for example `30s`, `2h`, `1d`).
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

Configure premium verification timeouts:

```yaml
security:
  premium:
    verification-timeout: 5
    api-connect-timeout: 5000  # Milliseconds
    api-read-timeout: 5000     # Milliseconds
```

The UUID lookup endpoint itself is configured under `skins.api.username-uuid-lookup-endpoint`.

[Learn more about security settings →](Security.md)

## Limbo World Customization

RelishAuth now keeps the limbo auth world hardcoded for stability with recent LimboAPI builds.

Current built-in auth world behavior:
- Dimension: `THE_END`
- Spawn: `0, 64, 0`
- Game mode: `SPECTATOR`
- Small barrier platform under spawn

These world/layout settings are no longer exposed in config.

### Movement Control

Limbo movement restriction is now hardcoded for stability with the current LimboAPI flow.
Players are kept near the auth spawn automatically, and there is no config toggle for it anymore.

### Timing

Configure message delays:

```yaml
customization:
  limbo:
    timing:
      auth-prompt-delay: 100      # Milliseconds
      discord-prompt-delay: 1000  # Milliseconds
    
    title:
      enabled: true
    actionbar:
      enabled: true
    bossbar:
      enabled: true
      color: "BLUE"
      overlay: "PROGRESS"
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
  allow-premium-username-impersonation: false
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
