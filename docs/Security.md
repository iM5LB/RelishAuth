# Security Settings

Comprehensive guide to RelishAuth's security features and best practices for protecting your server.

## Security Features Overview

RelishAuth provides multiple layers of security:
- **Argon2 Password Hashing** - Industry-standard password protection
- **Rate Limiting** - Protection against brute force attacks
- **Session Management** - Configurable authentication persistence
- **IP Validation** - Location-based security
- **Premium Verification** - Real-time Mojang API validation
- **Account Locking** - Automatic lockout after failed attempts

## Password Security

### Argon2 Hashing

RelishAuth uses Argon2, the winner of the Password Hashing Competition:

```yaml
authentication:
  password:
    hashing: "argon2"
    argon2:
      iterations: 10      # Computational cost
      memory: 65536       # Memory cost in KB (64MB)
      parallelism: 1      # Parallel threads
```

**Security Levels**:

**Low Security** (Fast, less secure):
```yaml
argon2:
  iterations: 5
  memory: 32768
  parallelism: 1
```

**Medium Security** (Balanced - Default):
```yaml
argon2:
  iterations: 10
  memory: 65536
  parallelism: 1
```

**High Security** (Slow, very secure):
```yaml
argon2:
  iterations: 20
  memory: 131072
  parallelism: 2
```

### Password Requirements

Enforce strong passwords:

```yaml
authentication:
  password:
    min-length: 8
    max-length: 32
    require-uppercase: true
    require-lowercase: true
    require-numbers: true
    require-special-chars: true
```

**Example Strong Policy**:
- Minimum 8 characters
- At least one uppercase letter
- At least one number
- At least one special character

## Rate Limiting

### Authentication Timeout

Limit time players have to authenticate:

```yaml
security:
  authentication-timeout: 300  # 5 minutes
```

**Recommendations**:
- **Casual servers**: 600 seconds (10 minutes)
- **Normal servers**: 300 seconds (5 minutes)
- **High-security**: 180 seconds (3 minutes)

### Timeout Warnings

Warn players before timeout:

```yaml
security:
  timeout-warnings:
    warning-threshold: 30  # Start warnings at 30 seconds remaining
    warning-interval: 10   # Warn every 10 seconds
```

### Password Attempt Limiting

Protect against brute force attacks:

```yaml
security:
  password-attempts:
    max-attempts: 3
    lock-duration: 15  # Minutes
```

**How it works**:
1. Player fails password 3 times
2. Account locked for 15 minutes
3. Player cannot attempt authentication
4. Lock automatically expires

**Recommendations**:
- **Casual servers**: 5 attempts, 5 minutes
- **Normal servers**: 3 attempts, 15 minutes
- **High-security**: 3 attempts, 30 minutes

## Session Management

### Session Duration

Control how long players stay authenticated:

```yaml
session:
  duration: "5m"  # Options: 0, 1m, 5m, 15m, 30m, 1h
```

**Security vs Convenience**:

| Duration | Security | Convenience | Best For |
|----------|----------|-------------|----------|
| 0 | Highest | Lowest | High-security servers |
| 1m | Very High | Low | Paranoid security |
| 5m | High | Medium | Balanced (default) |
| 15m | Medium | High | Casual servers |
| 30m | Low | Very High | Friendly servers |
| 1h | Lowest | Highest | Convenience-focused |

### IP Validation

Require same IP for session continuation:

```yaml
session:
  allow-different-locations: false
```

**When `false` (More Secure)**:
- Players must re-authenticate if IP changes
- Prevents session hijacking
- Recommended for high-security servers
- May inconvenience players with dynamic IPs

**When `true` (More Convenient)**:
- Players can continue session from different IPs
- More convenient for mobile players
- Slightly less secure
- Recommended for casual servers

## Premium Account Security

### Premium Auto-Login

```yaml
authentication:
  premium-auto-login: true
  allow-premium-offline: false  # IMPORTANT: Keep false!
```

**Security Configuration**:

**Secure** (Recommended):
```yaml
premium-auto-login: true
allow-premium-offline: false
```
- Premium players auto-login
- Cracked clients cannot impersonate premium accounts
- Mojang API verification required

**Insecure** (Not Recommended):
```yaml
premium-auto-login: true
allow-premium-offline: true  # ⚠️ DANGEROUS!
```
- Anyone can join as premium username
- No verification required
- Allows account impersonation

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

**Timeout Recommendations**:
- **Good connection**: 5 seconds
- **Slow connection**: 10 seconds
- **Very slow**: 15 seconds

## Discord Security

### Account Enforcement

Prevent Discord account switching:

```yaml
authentication:
  enforce-discord-account-match: true
```

**When enabled**:
- Players can only re-link the same Discord account
- Prevents account takeover via Discord
- More secure

**When disabled**:
- Players can link different Discord accounts
- Less secure
- More flexible

### Join Notifications

Security alerts via Discord:

```yaml
discord:
  join-notifications:
    default-enabled: true
    cooldown: 60  # Seconds
```

**Benefits**:
- Players notified when someone joins with their account
- Immediate security alerts
- Helps detect unauthorized access

## IP Blocking

### Block Username from IP

Prevent specific username from authenticating from an IP:

```
/ra block Username 192.168.1.100
```

**Use cases**:
- Prevent account theft from known malicious IPs
- Block compromised accounts from specific locations
- Temporary security measure during investigation

### Unblock

Remove IP block:

```
/ra unblock Username
/ra unblock 192.168.1.100
```

## Security Best Practices

### For All Servers

1. **Use strong password policy**:
   ```yaml
   password:
     min-length: 8
     require-uppercase: true
     require-numbers: true
   ```

2. **Keep `allow-premium-offline: false`**:
   ```yaml
   allow-premium-offline: false
   ```

3. **Enable rate limiting**:
   ```yaml
   password-attempts:
     max-attempts: 3
     lock-duration: 15
   ```

4. **Use Discord notifications**:
   ```yaml
   join-notifications:
     default-enabled: true
   ```

### For High-Security Servers

1. **Disable sessions or use short duration**:
   ```yaml
   session:
     duration: "0"  # or "1m"
   ```

2. **Enable IP validation**:
   ```yaml
   session:
     allow-different-locations: false
   ```

3. **Use strong Argon2 settings**:
   ```yaml
   argon2:
     iterations: 20
     memory: 131072
   ```

4. **Short authentication timeout**:
   ```yaml
   authentication-timeout: 180
   ```

5. **Strict password requirements**:
   ```yaml
   password:
     min-length: 10
     require-uppercase: true
     require-lowercase: true
     require-numbers: true
     require-special-chars: true
   ```

### For Community Servers

1. **Use Discord authentication**:
   ```yaml
   authentication:
     method: "discord"
   ```

2. **Enable join notifications**:
   ```yaml
   join-notifications:
     default-enabled: true
   ```

3. **Moderate session duration**:
   ```yaml
   session:
     duration: "15m"
   ```

4. **Assign linked role**:
   ```yaml
   linked-role-id: "YOUR_ROLE_ID"
   ```

### For Public Servers

1. **Balance security and convenience**:
   ```yaml
   session:
     duration: "30m"
     allow-different-locations: true
   ```

2. **Moderate password requirements**:
   ```yaml
   password:
     min-length: 6
     require-numbers: true
   ```

3. **Enable premium auto-login**:
   ```yaml
   premium-auto-login: true
   allow-premium-offline: false
   ```

## Security Monitoring

### Check Player Info

Monitor player accounts:

```
/ra info PlayerName
```

Shows:
- Account type
- Discord link status
- Last login
- IP address
- Session details

### Console Logging

Enable debug logging:

```yaml
debug: true
```

Logs:
- Authentication attempts
- Failed logins
- Session events
- Discord API calls

### Discord Admin Commands

Monitor and manage security via Discord:

```
/info player:PlayerName
/kick player:Suspicious
/block username:Griefer ip:1.2.3.4
```

## Common Security Scenarios

### Suspected Account Compromise

1. **Check player info**:
   ```
   /ra info PlayerName
   ```

2. **Check recent logins and IPs**

3. **Block suspicious IP**:
   ```
   /ra block PlayerName 192.168.1.100
   ```

4. **Unlink Discord if needed**:
   ```
   /ra unlink PlayerName
   ```

5. **Contact player** via Discord

### Brute Force Attack

1. **Check console** for repeated failed attempts

2. **Account automatically locked** after max attempts

3. **Block attacker IP**:
   ```
   /ra block TargetUsername 192.168.1.100
   ```

4. **Increase lock duration** if needed:
   ```yaml
   password-attempts:
     lock-duration: 60  # 1 hour
   ```

### Premium Account Impersonation

1. **Verify `allow-premium-offline: false`**

2. **Check player is actually premium**:
   ```
   /ra info PlayerName
   ```

3. **If cracked player using premium name**:
   - They cannot authenticate
   - Mojang API verification fails
   - Player is kicked

## Security Checklist

Before going live, verify:

- [ ] `allow-premium-offline: false`
- [ ] Strong password policy configured
- [ ] Rate limiting enabled
- [ ] Session duration appropriate for your server
- [ ] Discord bot configured (if using Discord auth)
- [ ] Join notifications enabled
- [ ] Admin permissions configured
- [ ] Tested authentication flow
- [ ] Tested failed login attempts
- [ ] Tested session expiration
- [ ] Tested IP blocking

## Next Steps

- [Configure authentication methods](AuthMethods.md)
- [Set up Discord bot](DiscordBot.md)
- [Learn commands](Commands.md)
- [View troubleshooting](Troubleshooting.md)

---

Need security advice? [Join our Discord](https://discord.gg/jDr2KZcGXk) for expert help!
