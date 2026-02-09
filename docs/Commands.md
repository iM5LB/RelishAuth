# Commands Reference

Complete reference for all RelishAuth commands, including player commands, admin commands, and Discord slash commands.

## Player Commands

All player commands use the `/ra` or `/relishauth` base command.

### `/ra password <password> [confirm]`

Set or change your password.

**Usage**:
- First time: `/ra password mypass123 mypass123`
- Change password: `/ra password newpass123 newpass123`
- Login: `/ra password mypass123`

**Permission**: `relishauth.player`

**Examples**:
```
/ra password SecurePass123 SecurePass123
/ra password MyNewPassword MyNewPassword
```

### `/ra discord <username>`

Link your Discord account.

**Usage**: `/ra discord YourDiscordUsername`

**Permission**: `relishauth.player`

**Process**:
1. Enter command with your Discord username
2. Check Discord DMs for verification message
3. Click "Verify" button to confirm

**Example**:
```
/ra discord john_doe
```

### `/ra logout`

Clear all your sessions and disconnect.

**Usage**: `/ra logout`

**Permission**: `relishauth.player`

**Effect**:
- Clears all active sessions
- Disconnects you from server
- Requires re-authentication on next join

### `/ra session [duration]`

View or change your session duration.

**Usage**:
- View current: `/ra session`
- Change: `/ra session 30m`

**Permission**: `relishauth.player`

**Available Durations**: `0`, `1m`, `5m`, `15m`, `30m`, `1h`

**Examples**:
```
/ra session          # View current duration
/ra session 15m      # Set to 15 minutes
/ra session 1h       # Set to 1 hour
/ra session 0        # Disable session (auth every time)
```

### `/ra notify <on|off>`

Toggle join notifications via Discord.

**Usage**: `/ra notify on` or `/ra notify off`

**Permission**: `relishauth.player`

**Requirements**: Discord account must be linked

**Examples**:
```
/ra notify on        # Enable notifications
/ra notify off       # Disable notifications
```

### `/ra unlink`

Unlink your Discord account.

**Usage**: `/ra unlink`

**Permission**: `relishauth.player`

**Effect**:
- Removes Discord account link
- Disables Discord features
- Join notifications stop
- May require re-authentication depending on auth method

### `/ra info`

View your account information.

**Usage**: `/ra info`

**Permission**: `relishauth.player`

**Displays**:
- Username
- Account type (Premium/Cracked)
- Discord link status
- Session duration
- Join notification status
- First login date
- Last login date

## Admin Commands

Admin commands require `relishauth.admin` permission.

### `/ra reload`

Reload plugin configuration.

**Usage**: `/ra reload`

**Permission**: `relishauth.admin`

**Effect**:
- Reloads config.yml
- Reloads language files
- Applies new settings without restart
- Does not disconnect players

### `/ra info <player>`

View another player's account information.

**Usage**: `/ra info PlayerName`

**Permission**: `relishauth.admin`

**Displays**:
- All information from player `/ra info`
- IP address
- Session details
- Authentication history

**Example**:
```
/ra info Notch
```

### `/ra unlink <player>`

Unlink another player's Discord account.

**Usage**: `/ra unlink PlayerName`

**Permission**: `relishauth.admin`

**Effect**:
- Removes player's Discord link
- Player must re-link to use Discord features
- Useful for account recovery

**Example**:
```
/ra unlink Notch
```

### `/ra block <username> <ip>`

Block a username from connecting from a specific IP.

**Usage**: `/ra block Username 192.168.1.1`

**Permission**: `relishauth.admin`

**Effect**:
- Prevents username from authenticating from specified IP
- Useful for preventing account theft
- Player can still join from other IPs

**Examples**:
```
/ra block Notch 192.168.1.100
/ra block Griefer 10.0.0.50
```

### `/ra unblock <identifier>`

Unblock a username or IP address.

**Usage**: `/ra unblock Username` or `/ra unblock 192.168.1.1`

**Permission**: `relishauth.admin`

**Examples**:
```
/ra unblock Notch
/ra unblock 192.168.1.100
```

## Discord Slash Commands

Commands available through Discord bot.

### `/link`

Get instructions for linking your Minecraft account.

**Usage**: `/link`

**Permission**: Everyone

**Response**: Instructions on how to link account in-game

### `/session [duration]`

View or change your session duration.

**Usage**:
- View: `/session`
- Change: `/session duration:15m`

**Permission**: Linked users only

**Options**: `0`, `1m`, `5m`, `15m`, `30m`, `1h`

### `/notifications [toggle]`

Toggle join notifications.

**Usage**:
- View: `/notifications`
- Change: `/notifications toggle:on`

**Permission**: Linked users only

**Options**: `on`, `off`

### `/info [player]`

View account information.

**Usage**:
- Your info: `/info`
- Other player: `/info player:Notch`

**Permission**: 
- Own info: Linked users
- Other players: Admin role

### `/kick <player>`

Kick a player from the server.

**Usage**: `/kick player:Notch`

**Permission**: Admin role

**Effect**: Immediately disconnects player from server

### `/unlink <player>`

Unlink a player's Discord account.

**Usage**: `/unlink player:Notch`

**Permission**: Admin role

### `/block <username> <ip>`

Block a username from an IP address.

**Usage**: `/block username:Notch ip:192.168.1.1`

**Permission**: Admin role

### `/unblock <identifier>`

Unblock a username or IP.

**Usage**: `/unblock identifier:Notch`

**Permission**: Admin role

### `/reload`

Reload plugin configuration.

**Usage**: `/reload`

**Permission**: Admin role

**Effect**: Same as in-game `/ra reload`

## Command Permissions

### Permission Nodes

```
relishauth.player          # All player commands
relishauth.admin           # All admin commands
relishauth.command.password    # /ra password
relishauth.command.discord     # /ra discord
relishauth.command.logout      # /ra logout
relishauth.command.session     # /ra session
relishauth.command.notify      # /ra notify
relishauth.command.unlink      # /ra unlink
relishauth.command.info        # /ra info
relishauth.command.reload      # /ra reload
relishauth.command.block       # /ra block
relishauth.command.unblock     # /ra unblock
```

### LuckPerms Setup

```bash
# Give all players basic commands
lp group default permission set relishauth.player true

# Give admins all commands
lp group admin permission set relishauth.admin true

# Give specific command access
lp user PlayerName permission set relishauth.command.reload true
```

## Disabling Commands

You can disable specific commands in `config.yml`:

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

Set to `false` to disable a command.

## Command Aliases

The following aliases are available:

- `/ra` = `/relishauth`
- All commands work with both aliases

## Command Cooldowns

Some commands have built-in cooldowns:

- `/ra notify`: 5 seconds
- `/ra session`: 10 seconds
- `/ra discord`: 30 seconds (per verification attempt)

## Common Command Scenarios

### First-Time Player Setup

```bash
# 1. Create password
/ra password MySecurePass123 MySecurePass123

# 2. Link Discord (optional)
/ra discord my_username

# 3. Set session duration
/ra session 30m

# 4. Enable notifications
/ra notify on
```

### Changing Authentication

```bash
# Change password
/ra password NewPassword123 NewPassword123

# Change session duration
/ra session 1h

# Disable session (auth every time)
/ra session 0
```

### Account Recovery (Admin)

```bash
# View player info
/ra info PlayerName

# Unlink Discord if needed
/ra unlink PlayerName

# Block suspicious IP
/ra block PlayerName 192.168.1.100
```

### Security Management (Admin)

```bash
# Block griefer
/ra block Griefer 10.0.0.50

# Unblock after appeal
/ra unblock Griefer

# Reload config after changes
/ra reload
```

## Troubleshooting Commands

### Command Not Working

**Check permissions**:
```bash
lp user YourName permission check relishauth.player
```

**Check if command is enabled**:
- View `config.yml` → `commands` section

**Check console for errors**:
- Look for error messages when running command

### Discord Commands Not Responding

**Check bot is online**:
- View Discord server member list

**Check bot permissions**:
- Bot needs "Use Slash Commands" permission

**Re-sync commands**:
- Restart Velocity proxy
- Bot will re-register commands

### Session Command Not Working

**Check available durations**:
- View `config.yml` → `session` → `available-durations`

**Verify duration format**:
- Use: `0`, `1m`, `5m`, `15m`, `30m`, `1h`
- Don't use: `5`, `5min`, `5 minutes`

## Next Steps

- [Learn about permissions](Permissions.md)
- [Set up Discord bot](DiscordBot.md)
- [Configure security](Security.md)
- [View troubleshooting](Troubleshooting.md)

---

Need help with commands? [Join our Discord](https://discord.gg/jDr2KZcGXk) for support!
