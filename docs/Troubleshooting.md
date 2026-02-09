# Troubleshooting

Common issues and solutions for RelishAuth. If you don't find your issue here, join our [Discord](https://discord.gg/jDr2KZcGXk) for support.

## Installation Issues

### Plugin Not Loading

**Symptoms**: No RelishAuth messages in console

**Solutions**:
1. **Check Java version**:
   ```bash
   java -version
   ```
   Must be Java 21 or higher

2. **Verify JAR file**:
   - Re-download if corrupted
   - Check file size is reasonable (not 0 bytes)

3. **Check LimboAPI is installed**:
   - Download from [GitHub](https://github.com/Elytrium/LimboAPI)
   - Place in same plugins folder

4. **Check console for errors**:
   - Look for stack traces
   - Note any error messages

### LimboAPI Missing Error

**Symptoms**: Error about missing LimboAPI dependency

**Solution**:
```
[ERROR] RelishAuth requires LimboAPI to function!
```

1. Download LimboAPI from [GitHub](https://github.com/Elytrium/LimboAPI)
2. Place in `plugins/` folder
3. Restart Velocity proxy

### Configuration Errors

**Symptoms**: Plugin loads but doesn't work

**Solutions**:
1. **Validate YAML syntax**:
   - Use [YAML Lint](https://www.yamllint.com/)
   - Check for tabs (use spaces only)
   - Verify indentation is correct

2. **Check for typos**:
   - Verify option names are correct
   - Check boolean values are `true` or `false`

3. **Delete and regenerate**:
   ```bash
   mv config.yml config.yml.backup
   # Restart server to generate new config
   ```

## Authentication Issues

### Players Can't Authenticate

**Symptoms**: Players stuck in limbo, can't login

**Solutions**:
1. **Check authentication method**:
   ```yaml
   authentication:
     method: "password"  # or "discord"
   ```

2. **Verify commands are enabled**:
   ```yaml
   commands:
     commands:
       enabled: true
       player-commands:
         password: true
   ```

3. **Check permissions**:
   ```bash
   lp user PlayerName permission check relishauth.player
   ```

4. **Test with different player**:
   - Try with fresh account
   - Check if issue is player-specific

### Password Not Working

**Symptoms**: "Incorrect password" even with correct password

**Solutions**:
1. **Reset player password** (admin):
   ```
   /ra unlink PlayerName
   ```
   Player must create new password

2. **Check database**:
   - Verify database is accessible
   - Check for corruption

3. **Check password requirements**:
   ```yaml
   password:
     min-length: 6
     require-uppercase: false
   ```

### Premium Players Can't Join

**Symptoms**: Premium players stuck at authentication

**Solutions**:
1. **Verify premium auto-login**:
   ```yaml
   authentication:
     premium-auto-login: true
     allow-premium-offline: false
   ```

2. **Check Mojang API access**:
   - Test from server: `curl https://api.mojang.com/users/profiles/minecraft/Notch`
   - Verify no firewall blocking

3. **Check timeout settings**:
   ```yaml
   security:
     premium:
       verification-timeout: 10  # Increase if slow
   ```

### Authentication Timeout

**Symptoms**: Players kicked before they can authenticate

**Solutions**:
1. **Increase timeout**:
   ```yaml
   security:
     authentication-timeout: 600  # 10 minutes
   ```

2. **Check for lag**:
   - Verify server performance
   - Check network latency

3. **Disable timeout for testing**:
   ```yaml
   authentication-timeout: 999999
   ```

## Discord Bot Issues

### Bot Not Connecting

**Symptoms**: Bot offline, no Discord features

**Solutions**:
1. **Verify bot token**:
   ```yaml
   discord:
     bot-token: "YOUR_BOT_TOKEN_HERE"
   ```
   - Check for typos
   - Ensure no extra spaces
   - Token should start with `MTk...` or similar

2. **Check intents enabled**:
   - Server Members Intent
   - Message Content Intent
   - Enable in Discord Developer Portal

3. **Check console for errors**:
   ```
   [ERROR] [RelishAuth] Failed to connect to Discord
   ```

4. **Regenerate token**:
   - Go to Discord Developer Portal
   - Bot section → Reset Token
   - Update config with new token

### Bot Not Sending DMs

**Symptoms**: Players don't receive verification messages

**Solutions**:
1. **Check bot permissions**:
   - Bot needs "Send Messages" permission
   - Verify bot is in server

2. **Check user settings**:
   - User must allow DMs from server members
   - Right-click server → Privacy Settings → Enable DMs

3. **Check bot is online**:
   - View Discord server member list
   - Bot should show as online

4. **Test with different user**:
   - Try with your own Discord account
   - Check if issue is user-specific

### Slash Commands Not Appearing

**Symptoms**: Discord slash commands don't show up

**Solutions**:
1. **Wait for sync**:
   - Commands can take up to 1 hour to appear
   - Restart Discord client to force refresh

2. **Check bot permissions**:
   - Bot needs "Use Slash Commands" permission
   - Verify in server settings

3. **Re-register commands**:
   - Restart Velocity proxy
   - Bot will re-register commands

4. **Check bot scope**:
   - Bot must be invited with `applications.commands` scope
   - Re-invite if necessary

### Verification Buttons Not Working

**Symptoms**: Clicking verify/deny does nothing

**Solutions**:
1. **Check button expiration**:
   ```yaml
   button-expiration-minutes: 5
   ```
   Buttons expire after configured time

2. **Re-run command**:
   ```
   /ra discord username
   ```
   New verification message will be sent

3. **Check bot permissions**:
   - Bot needs to handle interactions
   - Verify bot is online

## Database Issues

### Connection Failed

**Symptoms**: Can't connect to MySQL database

**Solutions**:
1. **Check MySQL is running**:
   ```bash
   sudo systemctl status mysql
   ```

2. **Test connection manually**:
   ```bash
   mysql -h localhost -u relishauth -p
   ```

3. **Verify credentials**:
   ```yaml
   mysql:
     host: "localhost"
     username: "relishauth"
     password: "correct_password"
   ```

4. **Check firewall**:
   ```bash
   sudo ufw allow 3306/tcp
   ```

5. **Check MySQL user permissions**:
   ```sql
   SHOW GRANTS FOR 'relishauth'@'localhost';
   ```

### Database Corruption

**Symptoms**: Errors reading/writing database

**Solutions**:
1. **For SQLite**:
   ```bash
   # Backup first
   cp data.db data.db.backup
   
   # Check integrity
   sqlite3 data.db "PRAGMA integrity_check;"
   ```

2. **For MySQL**:
   ```sql
   CHECK TABLE users;
   CHECK TABLE sessions;
   REPAIR TABLE users;
   REPAIR TABLE sessions;
   ```

3. **Restore from backup**:
   - Use your most recent backup
   - Players may need to re-authenticate

### Too Many Connections

**Symptoms**: "Too many connections" error

**Solutions**:
1. **Reduce pool size**:
   ```yaml
   pool:
     maximum-pool-size: 5
   ```

2. **Increase MySQL max connections**:
   ```sql
   SET GLOBAL max_connections = 200;
   ```

3. **Check for connection leaks**:
   - Restart Velocity proxy
   - Monitor connection count

## Session Issues

### Sessions Not Saving

**Symptoms**: Players must authenticate every time

**Solutions**:
1. **Check session duration**:
   ```yaml
   session:
     duration: "5m"  # Not "0"
   ```

2. **Check database**:
   - Verify sessions table exists
   - Check for write errors in console

3. **Check IP validation**:
   ```yaml
   session:
     allow-different-locations: true
   ```

### Sessions Expiring Too Fast

**Symptoms**: Players kicked unexpectedly

**Solutions**:
1. **Increase session duration**:
   ```yaml
   session:
     duration: "30m"  # or "1h"
   ```

2. **Check system time**:
   - Verify server time is correct
   - Check for time drift

3. **Disable IP validation**:
   ```yaml
   session:
     allow-different-locations: true
   ```

## Performance Issues

### High CPU Usage

**Symptoms**: Server lag, high CPU usage

**Solutions**:
1. **Reduce Argon2 iterations**:
   ```yaml
   argon2:
     iterations: 5  # Lower = faster
     memory: 32768
   ```

2. **Optimize database**:
   ```sql
   OPTIMIZE TABLE users;
   OPTIMIZE TABLE sessions;
   ```

3. **Increase connection pool**:
   ```yaml
   pool:
     maximum-pool-size: 15
   ```

### Slow Authentication

**Symptoms**: Long delay when authenticating

**Solutions**:
1. **Check database latency**:
   - Use local database if possible
   - Optimize MySQL configuration

2. **Reduce Argon2 cost**:
   ```yaml
   argon2:
     iterations: 5
     memory: 32768
   ```

3. **Check network latency**:
   - Test connection to database server
   - Consider moving database closer

### Memory Leaks

**Symptoms**: Memory usage increases over time

**Solutions**:
1. **Restart Velocity proxy** regularly
2. **Update to latest version**
3. **Report issue** on Discord with:
   - Memory usage graph
   - Server specifications
   - Player count

## Permission Issues

### Commands Not Working

**Symptoms**: "You don't have permission" error

**Solutions**:
1. **Check permission**:
   ```bash
   lp user PlayerName permission check relishauth.player
   ```

2. **Grant permission**:
   ```bash
   lp group default permission set relishauth.player true
   ```

3. **Check command is enabled**:
   ```yaml
   commands:
     player-commands:
       password: true
   ```

4. **Use fallback admin**:
   ```yaml
   admin-players:
     - "YourUsername"
   ```

### Admin Commands Not Working

**Symptoms**: Admin commands don't work

**Solutions**:
1. **Check admin permission**:
   ```bash
   lp user PlayerName permission check relishauth.admin
   ```

2. **Grant admin permission**:
   ```bash
   lp user PlayerName permission set relishauth.admin true
   ```

3. **Add to fallback list**:
   ```yaml
   admin-players:
     - "AdminUsername"
   ```

## Bedrock Edition Issues

### Bedrock Players Can't Join

**Symptoms**: Bedrock players kicked or can't authenticate

**Solutions**:
1. **Enable Bedrock support**:
   ```yaml
   authentication:
     allow-bedrock-players: true
   ```

2. **Install Floodgate**:
   - Download from [Floodgate](https://geysermc.org/download#floodgate)
   - Place in plugins folder
   - Restart proxy

3. **Check Bedrock prefix**:
   - Bedrock players have `.` prefix
   - Example: `.BedrockPlayer`

## Common Error Messages

### "Failed to load config.yml"

**Cause**: YAML syntax error

**Solution**:
- Validate YAML at [YAML Lint](https://www.yamllint.com/)
- Check for tabs (use spaces)
- Verify indentation

### "Database connection failed"

**Cause**: Can't connect to database

**Solution**:
- Check database is running
- Verify credentials
- Test connection manually

### "Discord bot token is invalid"

**Cause**: Wrong or expired bot token

**Solution**:
- Verify token in Discord Developer Portal
- Regenerate token if needed
- Update config.yml

### "LimboAPI not found"

**Cause**: LimboAPI plugin not installed

**Solution**:
- Download LimboAPI
- Place in plugins folder
- Restart proxy

### "Premium verification failed"

**Cause**: Can't reach Mojang API

**Solution**:
- Check internet connection
- Verify no firewall blocking
- Increase timeout

## Getting Help

### Before Asking for Help

Gather this information:
1. **RelishAuth version**
2. **Velocity version**
3. **Java version**
4. **Error messages** from console
5. **Configuration** (remove sensitive data)
6. **Steps to reproduce** the issue

### Where to Get Help

1. **Discord Support**: [Join our Discord](https://discord.gg/jDr2KZcGXk)
   - Fastest response
   - Community help
   - Direct developer support

2. **GitHub Issues**: [Report bugs](https://github.com/iM5LB/relishauth/issues)
   - For confirmed bugs
   - Feature requests
   - Technical issues

3. **Documentation**:
   - [Installation Guide](Installation.md)
   - [Configuration Guide](Configuration.md)
   - [Commands Reference](Commands.md)

### Providing Logs

**Get console logs**:
```bash
# Last 100 lines
tail -n 100 logs/latest.log

# Search for errors
grep ERROR logs/latest.log
```

**Enable debug mode**:
```yaml
debug: true
```

**Pastebin logs**:
- Use [Pastebin](https://pastebin.com/)
- Don't paste directly in Discord
- Remove sensitive information

## Still Having Issues?

If you've tried everything and still have issues:

1. **Join our Discord**: [discord.gg/jDr2KZcGXk](https://discord.gg/jDr2KZcGXk)
2. **Open a support ticket**
3. **Provide all requested information**
4. **Be patient** - we'll help you!

---

**Remember**: Most issues are configuration-related. Double-check your config.yml before asking for help!
