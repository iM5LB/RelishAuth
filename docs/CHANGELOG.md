# Changelog

All notable changes to RelishAuth will be documented in this file.

## [1.1.1] - 2026-07-26

### Fixed
- Restored missing `BuildConstants` template and `settings.gradle`.
- Restored broken `AuthDatabase.migrateUserUuid` (CFR decompile stub that always threw).
- Password login no longer succeeds for accounts with a null/empty password (premium/Discord-only accounts).
- Discord auth now ignores `unlinked_*` Discord IDs and compares link ownership with the resolved account UUID.
- Hardened async Discord username lookup against disconnect NPEs.
- Password hasher edge cases (algorithm normalization, `$2x$` bcrypt, null-safe config).
- Group sync no longer wipes LuckPerms groups when Discord role lookup fails or Discord bot is offline.
- Group sync resolves account UUID correctly, awaits LuckPerms saves, and clears mapped groups on unlink/guild leave.
- Group sync re-initializes on `/ra reload`.
- Login method chooser Java buttons now use limbo `runCommand` (`/password` / `/discord`) instead of Adventure callbacks, which LimboAPI does not execute.
- `/ra unlink` self-unlink actually unlinks Discord again (it previously always replied with a hard-coded “cannot unlink” stub).
- Discord button clicks no longer double-acknowledge interactions (`DiscordBot` + `DiscordCommands` both listened and both replied on expiry / `setduration`).
- Bedrock/Floodgate: premium-verification cache now matches Floodgate `.` username prefix so reconnects can use Discord/password sessions instead of always being forced back to limbo.
- Discord auth no longer double-sends verify DMs.
- Discord slash/button interactions now acknowledge immediately with a blocking `deferReply().complete()` so they are not delayed past Discord’s 3s window behind other REST traffic (fixes `10062 Unknown interaction`).
- Removed Floodgate Cumulus forms (they cannot be delivered inside LimboAPI). Bedrock auth uses limbo titles + chat prompts only; FloodgateHelper now only detects Bedrock players.
- Login method chooser also appears when `authentication.method` is `discord` if the account has both a password and Discord linked (still disabled for hybrid).
- Fixed Bedrock `/ra unlink`: targeted Discord unlink SQL (avoids full `updateUser` failures), Floodgate username/UUID resolution, and widened username column for `.` + gamertag.
- Login chooser chat now always sends plain-text options (LimboAPI often drops clickable components); Java and Bedrock both see type `password` / `discord`.
- Discord unlink can clear the account password (`authentication.clear-password-on-discord-unlink`, default true) so the old password cannot be reused.
- Accounts without a password get a chat tip on backend join (`authentication.set-password-tip-on-join`), similar to the Discord join-alert tip.

### Added
- Hybrid authentication mode (`authentication.method: hybrid`): password login/register, then required Discord linking before leaving limbo.
- Login method chooser when a player has both password and Discord linked (`authentication.login-chooser.enabled`):
  - In limbo, type `password` / `discord` (or `1` / `2`); Java may also get clickable buttons (often dropped by LimboAPI)
  - Works with `method` password or discord; disabled in hybrid mode (both factors required)
- Backend chat tip when joining without a password (`authentication.set-password-tip-on-join`)
- Language keys for `/ra syncgroups` (EN/AR), hybrid Discord prompt, and login chooser.

## [1.1.0] - 2026-05-31

### Added
- Discord role to LuckPerms group sync via `group-sync.role-to-group`.
- Manual `/ra syncgroups [player]` command for queueing a group sync.

## [1.0.9] - 2026-05-30

### Fixed
- Forced-host routing for already-authenticated/session-valid players now respects Velocity's selected initial server when `routing.post-auth-server` is empty.

## [1.0.8] - 2026-05-23

### Changed
- Discord config: `discord.server-id` now accepts either a single guild ID string or a list of guild IDs

### Fixed
- Forced-host routing: players now leave limbo to the server chosen by Velocity (respects `forced-hosts`) instead of always using `attempt-connection-order`

## [1.0.7] - 2026-05-21

### Fixed
- PostgreSQL: correct column types (`BOOLEAN`, `BIGINT`) with auto-migration on startup for existing installs
- PostgreSQL: `createSession` using SQLite-only `INSERT OR REPLACE` syntax
- Config validation incorrectly warning on `postgresql` database type
- Eliminated all `password4j` `PropertyReader` startup warnings
- Missing language key `error-auth-failed-generic` in EN and AR

## [1.0.6] - 2026-05-13

### Added
- PostgreSQL database support:
  - Full PostgreSQL configuration options (host, port, database, credentials)
  - HikariCP connection pooling support
  - Config schema customization for PostgreSQL tables

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
