# Permissions

Complete reference for RelishAuth permission nodes and setup with popular permission plugins.

## Permission Nodes

### Player Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `relishauth.player` | Access to all player commands | true |
| `relishauth.command.password` | Use `/ra password` command | true |
| `relishauth.command.discord` | Use `/ra discord` command | true |
| `relishauth.command.logout` | Use `/ra logout` command | true |
| `relishauth.command.session` | Use `/ra session` command | true |
| `relishauth.command.notify` | Use `/ra notify` command | true |
| `relishauth.command.unlink` | Use `/ra unlink` command | true |
| `relishauth.command.info` | Use `/ra info` command | true |

### Admin Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `relishauth.admin` | Access to all admin commands | op |
| `relishauth.command.reload` | Use `/ra reload` command | op |
| `relishauth.command.info.others` | View other players' info | op |
| `relishauth.command.unlink.others` | Unlink other players | op |
| `relishauth.command.block` | Use `/ra block` command | op |
| `relishauth.command.unblock` | Use `/ra unblock` command | op |

### Special Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `relishauth.bypass` | Bypass authentication (not recommended) | false |
| `relishauth.bypass.timeout` | Bypass authentication timeout | false |

## LuckPerms Setup

### Installation

1. Download LuckPerms from [LuckPerms Downloads](https://luckperms.net/download)
2. Place in Velocity `plugins/` folder
3. Restart proxy

### Basic Configuration

**Give all players basic permissions**:
```bash
lp group default permission set relishauth.player true
```

**Give admins all permissions**:
```bash
lp group admin permission set relishauth.admin true
```

### Detailed Configuration

**Create RelishAuth admin group**:
```bash
lp creategroup relishauth-admin
lp group relishauth-admin permission set relishauth.admin true
lp group relishauth-admin parent add default
```

**Add user to admin group**:
```bash
lp user PlayerName parent add relishauth-admin
```

### Per-Command Permissions

**Allow specific commands only**:
```bash
# Allow password command
lp group default permission set relishauth.command.password true

# Allow session command
lp group default permission set relishauth.command.session true

# Deny logout command
lp group default permission set relishauth.command.logout false
```

### Temporary Permissions

**Grant temporary admin access**:
```bash
lp user PlayerName permission settemp relishauth.admin true 1h
```

## Fallback Admin System

If no permission plugin is installed, use the fallback system:

```yaml
admin-players:
  - "PlayerName1"
  - "PlayerName2"
  - "AdminUsername"
```

Players in this list have full admin permissions.

**Note**: This is less flexible than using a permission plugin.

## Discord Permissions

### Discord Bot Permissions

Required Discord permissions for the bot:
- Send Messages
- Embed Links
- Use Slash Commands
- Manage Roles
- Read Message History

### Discord Admin Role

Configure admin role for Discord commands:

```yaml
discord:
  admin-role-id: "123456789012345678"
```

**If empty**: Requires Discord Administrator permission

**If set**: Users with this role can use admin commands

### Discord Slash Command Permissions

**Player commands** (everyone):
- `/link`
- `/session`
- `/notifications`
- `/info`

**Admin commands** (admin role or Administrator):
- `/info player:Name`
- `/kick`
- `/unlink`
- `/block`
- `/unblock`
- `/reload`

## Permission Examples

### Default Player

```bash
lp group default permission set relishauth.player true
```

Grants:
- `/ra password`
- `/ra discord`
- `/ra logout`
- `/ra session`
- `/ra notify`
- `/ra unlink`
- `/ra info`

### Moderator

```bash
lp creategroup moderator
lp group moderator parent add default
lp group moderator permission set relishauth.command.info.others true
lp group moderator permission set relishauth.command.unlink.others true
```

Grants:
- All player commands
- `/ra info <player>`
- `/ra unlink <player>`

### Administrator

```bash
lp group admin permission set relishauth.admin true
```

Grants:
- All player commands
- All moderator commands
- `/ra reload`
- `/ra block`
- `/ra unblock`

### VIP Player

```bash
lp creategroup vip
lp group vip parent add default
lp group vip permission set relishauth.bypass.timeout true
```

Grants:
- All player commands
- No authentication timeout

## Permission Inheritance

### Group Hierarchy

```
default (all players)
  ├── vip (premium players)
  ├── moderator (staff)
  │   └── admin (administrators)
  └── owner (server owner)
```

### Setup Hierarchy

```bash
# Create groups
lp creategroup vip
lp creategroup moderator
lp creategroup admin
lp creategroup owner

# Set parents
lp group vip parent add default
lp group moderator parent add default
lp group admin parent add moderator
lp group owner parent add admin

# Set permissions
lp group default permission set relishauth.player true
lp group moderator permission set relishauth.command.info.others true
lp group admin permission set relishauth.admin true
lp group owner permission set relishauth.* true
```

## Disabling Commands

### Via Configuration

Disable commands in `config.yml`:

```yaml
commands:
  commands:
    enabled: true
    
    player-commands:
      password: true
      notify: false      # Disable notify command
      logout: true
      session: true
      info: true
      unlink: false      # Disable unlink command
    
    admin-commands:
      info-others: true
      unlink-others: true
      reload: true
      block: true
      unblock: false     # Disable unblock command
```

### Via Permissions

Deny specific commands:

```bash
lp group default permission set relishauth.command.logout false
lp group default permission set relishauth.command.unlink false
```

## Bypass Permissions

### Authentication Bypass

**⚠️ Not Recommended**: Allows players to skip authentication entirely.

```bash
lp user TrustedPlayer permission set relishauth.bypass true
```

**Use cases**:
- Testing
- Trusted administrators
- Special accounts

**Security risk**: Player can join without authentication!

### Timeout Bypass

Allow players unlimited time to authenticate:

```bash
lp user SlowPlayer permission set relishauth.bypass.timeout true
```

**Use cases**:
- Players with slow connections
- Players who need extra time
- Accessibility requirements

## Permission Checking

### Check Player Permissions

**In-game**:
```
/lp user PlayerName permission check relishauth.admin
```

**Console**:
```
lp user PlayerName permission check relishauth.admin
```

### List Player Permissions

```bash
lp user PlayerName permission info
```

### Debug Permissions

Enable verbose mode:
```bash
lp verbose on
```

Run command, then:
```bash
lp verbose paste
```

## Common Permission Setups

### Public Server

```bash
# All players can authenticate
lp group default permission set relishauth.player true

# Admins have full access
lp group admin permission set relishauth.admin true
```

### Whitelist Server

```bash
# Only whitelisted players can use commands
lp group default permission set relishauth.player false
lp group whitelisted permission set relishauth.player true
lp group whitelisted parent add default

# Admins have full access
lp group admin permission set relishauth.admin true
```

### Staff Hierarchy

```bash
# Helper - can view player info
lp group helper permission set relishauth.command.info.others true

# Moderator - can unlink players
lp group moderator parent add helper
lp group moderator permission set relishauth.command.unlink.others true

# Admin - full access
lp group admin parent add moderator
lp group admin permission set relishauth.admin true
```

## Troubleshooting Permissions

### Command Not Working

**Check permission**:
```bash
lp user PlayerName permission check relishauth.command.password
```

**Check group permissions**:
```bash
lp group default permission info
```

**Check inheritance**:
```bash
lp user PlayerName info
```

### Permission Not Applying

**Clear cache**:
```bash
lp reloadconfig
```

**Check for negations**:
```bash
lp user PlayerName permission info
```

Look for permissions with `false` value.

### Admin Commands Not Working

**Check admin permission**:
```bash
lp user PlayerName permission check relishauth.admin
```

**Check fallback admin list**:
- View `config.yml` → `admin-players`

**Check Discord admin role**:
- View `config.yml` → `discord` → `admin-role-id`

## Best Practices

### Use Permission Plugin

- ✅ Use LuckPerms or similar
- ❌ Don't rely on fallback admin list
- ✅ Organize permissions in groups
- ✅ Use permission inheritance

### Principle of Least Privilege

- Give minimum required permissions
- Don't give `relishauth.*` to everyone
- Use specific command permissions
- Review permissions regularly

### Regular Audits

- Check who has admin permissions
- Remove permissions from inactive staff
- Review bypass permissions
- Update permission structure as needed

## Next Steps

- [Learn commands](Commands.md)
- [Configure security](Security.md)
- [Set up Discord bot](DiscordBot.md)
- [View troubleshooting](Troubleshooting.md)

---

Need permission help? [Join our Discord](https://discord.gg/jDr2KZcGXk) for support!
