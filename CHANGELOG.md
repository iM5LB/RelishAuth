# Changelog

All notable changes to RelishAuth will be documented in this file.

## [1.0.2] - 2025-03-13

### Added
- Skin restore improvements:
  - Cached-skin resolver with offline/premium UUID fallbacks and safeguards to prevent wrong skins being applied to the wrong username.
  - Optional default cape injection for unsigned textures (`skins.capes.default-unsigned-cape`).
  - Preserve existing textures to avoid overriding Bedrock/Geyser skins (`skins.preserve-existing-textures`).
  - Configurable skin API endpoints for username textures, UUID lookup, and Mojang session (`skins.api.*`).
  - Optional login wait for skin fetching (`skins.api.login-wait-timeout`).
- Premium official UUID injection (`authentication.premium-use-official-uuid`) with optional DB migration (`authentication.premium-use-official-uuid-migrate-database`).

### Changed
- Simplified update/config options:
  - Removed the old `auto-update` config section.
  - Config migration now runs automatically on startup and `/ra reload` (backs up before changes).
- Renamed config key `authentication.allow-premium-offline` → `authentication.allow-premium-username-impersonation` (legacy key is still supported).

## [1.0.1] - 2025-01-31

### Changed
- Updated command prefix from `/ro` to `/ra` to match plugin name :)
- Updated all language files to reflect new command prefix
- Updated documentation and help messages

### Fixed
- Fixed plugin ID format to comply with Velocity standards
- Fixed template path for BuildConstants generation
- Resolved build compilation issues

## [1.0.0] - 2025-01-31

### Added
- Initial release of RelishAuth
- Multi-method authentication (Password, Discord, Premium Auto-Login)
- Discord bot integration with slash commands
- Real-time Discord verification system
- Session management with configurable durations
- Multi-language support (English, Arabic)
- Database support (SQLite, MySQL, MariaDB)
- Premium account verification via Mojang API
- Bedrock player support via Floodgate
- LimboAPI integration for authentication limbo
- Admin management commands
- Security features (rate limiting, IP validation)
- Join notifications via Discord
- Account linking and management
- Customizable messages and configuration

### Commands
- `/ra password` - Set/change password
- `/ra discord` - Link Discord account
- `/ra logout` - Clear sessions
- `/ra session` - Set session duration
- `/ra notify` - Toggle notifications
- `/ra unlink` - Unlink Discord
- `/ra info` - View account info
- `/ra reload` - Reload config (admin)

### Discord Features
- Real-time DM verification
- Interactive button verification
- Admin slash commands
- Join notifications
- Account management
- Server integration

### Security
- Argon2 password hashing
- Session-based authentication
- Premium account verification
- Rate limiting protection
- IP-based validation
