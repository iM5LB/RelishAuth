package relish.relishAuthVelocity.exceptions;

import java.util.UUID;
import relish.relishAuthVelocity.exceptions.PluginException;

public class AuthenticationException
extends PluginException {
    private final UUID playerUuid;
    private final String username;

    public AuthenticationException(PluginException.ErrorCode errorCode, String message) {
        super(errorCode, message);
        this.playerUuid = null;
        this.username = null;
    }

    public AuthenticationException(PluginException.ErrorCode errorCode, String message, UUID playerUuid) {
        super(errorCode, message, "UUID: " + String.valueOf(playerUuid));
        this.playerUuid = playerUuid;
        this.username = null;
    }

    public AuthenticationException(PluginException.ErrorCode errorCode, String message, UUID playerUuid, String username) {
        super(errorCode, message, "UUID: " + String.valueOf(playerUuid) + ", Username: " + username);
        this.playerUuid = playerUuid;
        this.username = username;
    }

    public AuthenticationException(PluginException.ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.playerUuid = null;
        this.username = null;
    }

    public AuthenticationException(PluginException.ErrorCode errorCode, String message, UUID playerUuid, Throwable cause) {
        super(errorCode, message, "UUID: " + String.valueOf(playerUuid), cause);
        this.playerUuid = playerUuid;
        this.username = null;
    }

    public UUID getPlayerUuid() {
        return this.playerUuid;
    }

    public String getUsername() {
        return this.username;
    }
}
