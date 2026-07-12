package relish.relishAuthVelocity.exceptions;

import java.util.UUID;
import relish.relishAuthVelocity.exceptions.PluginException;

public class LimboException
extends PluginException {
    private final UUID playerUuid;

    public LimboException(PluginException.ErrorCode errorCode, String message) {
        super(errorCode, message);
        this.playerUuid = null;
    }

    public LimboException(PluginException.ErrorCode errorCode, String message, UUID playerUuid) {
        super(errorCode, message, "UUID: " + String.valueOf(playerUuid));
        this.playerUuid = playerUuid;
    }

    public LimboException(PluginException.ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.playerUuid = null;
    }

    public LimboException(PluginException.ErrorCode errorCode, String message, UUID playerUuid, Throwable cause) {
        super(errorCode, message, "UUID: " + String.valueOf(playerUuid), cause);
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() {
        return this.playerUuid;
    }
}
