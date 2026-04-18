# Changelog

All notable changes to RelishAuth will be documented in this file.

## [1.0.5] - 2026-04-13

### Added
- `bcrypt2y` password hashing support.
- Limbo auth timeout HUD support:
  - boss bar toggle, color, and overlay

### Changed
- Updated RelishAuth for newer LimboAPI behavior:
  - adjusted limbo session flow for current LimboAPI releases
  - hardened limbo attach/auth handling
  - kept the auth world behavior stable with a fixed End auth world and spectator flow
- Simplified limbo/world customization:
  - removed configurable limbo world layout/dimension/gamemode tuning
  - removed configurable limbo movement blocking
  - movement restriction is now handled internally for stability
- Simplified title timing by hardcoding fade/stay/fade values instead of exposing them in config.
- Improved verification logging and debug output:
  - neutral verification tags such as `[ACCOUNT-VERIFY]` and `[PROFILE-LOOKUP]`
  - clearer auth decision logs
  - less confusing premium wording on offline/cracked paths

### Fixed
- Cached offline/non-premium verification results so cracked players do not hit missing-verification decisions after verification already completed.
- Fixed new config generation after limbo HUD/config changes.
- Fixed missing language keys for new help/admin command entries.
- Fixed limbo session UI/message flow on current LimboAPI builds.

## [1.0.4] - 2026-03-30

### Added
- Language file updater: automatically merges missing keys into `lang/<lang>/plugin.yml` and `lang/<lang>/discord.yml` on startup and `/ra reload` to prevent "Missing message key" spam.
- New admin commands:
  - `/ra setpassword <player> <new> <confirm>`
  - `/ra resetpassword <player> [length]`
  - `/ra block <username> <from>` / `/ra unblock <username> <from>` / `/ra clearblocks <username>`

### Changed
- In-game admin command outputs now use translatable language keys (EN/AR) for `/ra block|unblock|clearblocks`, `/ra setpassword`, `/ra resetpassword`.

### Fixed
- Admin permission node casing mismatch

## [1.0.3] - 2026-03-17

### Added
- Post-auth routing config (`routing.post-auth-server`) with default fallback to Velocity's `attempt-connection-order`.
- Versioned config updates via `config-version` (config updater now bumps the schema version when syncing defaults).

### Changed
- Session duration presets are now fully driven by `session.available-durations` for both:
  - In-game `/ra session` menu
  - Discord `/session` buttons + validation
- Session duration parsing now supports arbitrary `Ns/Nm/Nh/Nd` durations consistently across the proxy.

## [1.0.2] - 2026-03-13

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
