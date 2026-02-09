# Discord Bot Setup

Complete guide for setting up and configuring the RelishAuth Discord bot integration.

## Overview

The Discord bot provides:
- Real-time account verification
- Join notifications for security
- Account management via Discord
- Admin commands through Discord
- Rich embed messages

## Prerequisites

- Discord account
- Discord server (guild)
- Server administrator permissions
- RelishAuth installed on Velocity

## Step 1: Create Discord Application

### 1.1 Access Developer Portal

1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Click "New Application"
3. Enter application name (e.g., "RelishAuth Bot")
4. Click "Create"

### 1.2 Create Bot

1. Navigate to "Bot" section in left sidebar
2. Click "Add Bot"
3. Click "Yes, do it!"
4. **Copy the bot token** (you'll need this later)

⚠️ **Important**: Never share your bot token publicly!

### 1.3 Configure Bot Settings

Enable these settings under "Privileged Gateway Intents":
- ✅ **Server Members Intent**
- ✅ **Message Content Intent**

## Step 2: Configure Bot Permissions

### Required Permissions

The bot needs these permissions:
- Send Messages
- Embed Links
- Use Slash Commands
- Manage Roles (for linked role feature)
- Read Message History

### Generate Invite Link

1. Go to "OAuth2" → "URL Generator"
2. Select scopes:
   - ✅ `bot`
   - ✅ `applications.commands`
3. Select permissions:
   - ✅ Send Messages
   - ✅ Embed Links
   - ✅ Use Slash Commands
   - ✅ Manage Roles
   - ✅ Read Message History
4. Copy the generated URL

## Step 3: Invite Bot to Server

1. Open the generated invite URL in your browser
2. Select your Discord server
3. Click "Authorize"
4. Complete the captcha

The bot should now appear in your server's member list.

## Step 4: Get Server and Role IDs

### Enable Developer Mode

1. Open Discord Settings
2. Go to "Advanced"
3. Enable "Developer Mode"

### Get Server ID

1. Right-click your server icon
2. Click "Copy ID"
3. Save this ID

### Get Role ID (Optional)

For linked role feature:
1. Go to Server Settings → Roles
2. Right-click the role you want to assign
3. Click "Copy ID"
4. Save this ID

### Get Admin Role ID (Optional)

For admin commands:
1. Right-click your admin role
2. Click "Copy ID"
3. Save this ID

## Step 5: Configure RelishAuth

Edit `plugins/relishauth/config.yml`:

```yaml
discord:
  # Bot token from Step 1.2
  bot-token: "YOUR_BOT_TOKEN_HERE"
  
  # Server ID from Step 4
  server-id: "123456789012345678"
  
  # Admin role ID (optional)
  # Leave empty to require Discord Administrator permission
  admin-role-id: "987654321098765432"
  
  # Linked role - assigned when user links account
  linked-role-id: "111222333444555666"
  
  # Your Discord server invite link
  invite-link: "https://discord.gg/your-invite"
```

### Full Discord Configuration

```yaml
discord:
  bot-token: "YOUR_BOT_TOKEN_HERE"
  server-id: "YOUR_SERVER_ID"
  admin-role-id: ""
  linked-role-id: ""
  invite-link: ""
  
  # Bot status
  status:
    enabled: true
    activity-type: "WATCHING"  # PLAYING, WATCHING, LISTENING, COMPETING
    message: "{players} players online"
    update-interval: 120  # Seconds
  
  # Button expiration
  button-expiration-minutes: 5
  
  # Shutdown timeout
  shutdown-timeout: 5
  
  # Embed colors (RGB format)
  colors:
    blue: "88,101,242"
    red: "237,66,69"
    green: "87,242,135"
    warning: "255,193,7"
  
  # API timeouts
  api:
    request-timeout: 10
    member-load-timeout: 5
  
  # Verification cleanup
  verification:
    pending-cleanup-minutes: 5
  
  # Guild cache
  cache:
    member-threshold: 100
  
  # Join notifications
  join-notifications:
    default-enabled: true
    cooldown: 60  # Seconds between notifications
```

## Step 6: Reload Configuration

In Velocity console or in-game:
```
/ra reload
```

Check console for:
```
[INFO] [RelishAuth] Discord bot connected successfully!
[INFO] [RelishAuth] Registered X slash commands
```

## Discord Features

### Account Verification

**Player Experience**:
1. Player joins server
2. Enters Discord username in-game
3. Receives DM from bot:

```
🔐 Account Verification

PlayerName is trying to link this Discord account.

If this was you, click Verify below.
If this wasn't you, click Deny.

[Verify] [Deny]
```

4. Clicks "Verify" to complete linking

### Join Notifications

When enabled, players receive DMs when someone joins with their account:

```
🔔 Login Notification

Someone just joined the server with your account!

Username: PlayerName
IP Address: 192.168.1.100
Time: 2026-02-09 16:45:32

If this wasn't you, please contact an administrator immediately.
```

**Enable/Disable**:
- In-game: `/ra notify on` or `/ra notify off`
- Discord: `/notifications toggle:on` or `/notifications toggle:off`

### Slash Commands

All Discord slash commands are automatically registered.

**Player Commands**:
- `/link` - Get linking instructions
- `/session` - View/change session duration
- `/notifications` - Toggle join notifications
- `/info` - View your account info

**Admin Commands**:
- `/info player:Name` - View player info
- `/kick player:Name` - Kick player
- `/unlink player:Name` - Unlink player's Discord
- `/block username:Name ip:1.2.3.4` - Block username from IP
- `/unblock identifier:Name` - Unblock username/IP
- `/reload` - Reload configuration

### Bot Status

The bot can display server status:

```yaml
status:
  enabled: true
  activity-type: "WATCHING"
  message: "{players} players online"
  update-interval: 120
```

**Placeholders**:
- `{players}` - Current player count
- `{max}` - Maximum player count

**Activity Types**:
- `PLAYING` - "Playing {message}"
- `WATCHING` - "Watching {message}"
- `LISTENING` - "Listening to {message}"
- `COMPETING` - "Competing in {message}"

### Linked Role

Automatically assign a role when players link their Discord:

```yaml
discord:
  linked-role-id: "YOUR_ROLE_ID"
```

Benefits:
- Identify linked players in Discord
- Give linked players special permissions
- Create linked-only channels

## Troubleshooting

### Bot Not Connecting

**Check bot token**:
- Verify token is correct
- Ensure no extra spaces
- Token should start with `MTk...` or similar

**Check intents**:
- Server Members Intent enabled
- Message Content Intent enabled

**Check console**:
- Look for connection errors
- Verify no firewall blocking Discord API

### Bot Not Sending DMs

**Check bot permissions**:
- Bot needs "Send Messages" permission
- User must allow DMs from server members

**User Settings**:
1. Right-click server icon
2. Privacy Settings
3. Enable "Allow direct messages from server members"

### Slash Commands Not Appearing

**Wait for sync**:
- Commands can take up to 1 hour to appear
- Restart Discord client to force refresh

**Check bot permissions**:
- Bot needs "Use Slash Commands" permission
- Bot must be in the server

**Re-register commands**:
- Restart Velocity proxy
- Bot will re-register commands

### Verification Buttons Not Working

**Check button expiration**:
```yaml
button-expiration-minutes: 5
```

**Buttons expire after configured time**:
- Player must re-run `/ra discord username`
- New verification message will be sent

### Join Notifications Not Working

**Check Discord link**:
- Player must have Discord linked
- Use `/ra info` to verify link status

**Check notifications enabled**:
- Use `/ra notify on` to enable
- Check cooldown hasn't been hit

**Check bot can DM**:
- User must allow DMs from server members

## Security Best Practices

### Protect Bot Token

- Never share bot token publicly
- Don't commit token to Git
- Use environment variables if possible
- Regenerate token if compromised

### Admin Role Configuration

**Option 1: Use Admin Role ID** (Recommended)
```yaml
admin-role-id: "YOUR_ADMIN_ROLE_ID"
```
Only users with this role can use admin commands.

**Option 2: Use Discord Administrator Permission**
```yaml
admin-role-id: ""
```
Only users with Discord Administrator permission can use admin commands.

### Rate Limiting

Discord has rate limits:
- 50 requests per second per bot
- 5 DMs per second
- RelishAuth handles this automatically

## Advanced Configuration

### Custom Embed Colors

```yaml
colors:
  blue: "88,101,242"      # Info messages
  red: "237,66,69"        # Error messages
  green: "87,242,135"     # Success messages
  warning: "255,193,7"    # Warning messages
```

Format: `"R,G,B"` (0-255 for each value)

### API Timeouts

```yaml
api:
  request-timeout: 10        # Seconds
  member-load-timeout: 5     # Seconds
```

Increase if you have a slow connection to Discord API.

### Verification Cleanup

```yaml
verification:
  pending-cleanup-minutes: 5
```

How long to keep pending verifications in memory.

## Next Steps

- [Learn Discord commands](Commands.md#discord-slash-commands)
- [Configure authentication](AuthMethods.md)
- [Set up security](Security.md)
- [View troubleshooting](Troubleshooting.md)

---

Need help with Discord setup? [Join our Discord](https://discord.gg/jDr2KZcGXk) for support!
