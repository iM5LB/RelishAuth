package relish.relishAuthVelocity.exceptions;

import relish.relishAuthVelocity.exceptions.PluginException;

public class ConfigurationException
extends PluginException {
    private final String configKey;
    private final Object invalidValue;

    public ConfigurationException(PluginException.ErrorCode errorCode, String message) {
        super(errorCode, message);
        this.configKey = null;
        this.invalidValue = null;
    }

    public ConfigurationException(PluginException.ErrorCode errorCode, String message, String configKey) {
        super(errorCode, message, "Key: " + configKey);
        this.configKey = configKey;
        this.invalidValue = null;
    }

    public ConfigurationException(PluginException.ErrorCode errorCode, String message, String configKey, Object invalidValue) {
        super(errorCode, message, "Key: " + configKey + ", Value: " + String.valueOf(invalidValue));
        this.configKey = configKey;
        this.invalidValue = invalidValue;
    }

    public ConfigurationException(PluginException.ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.configKey = null;
        this.invalidValue = null;
    }

    public ConfigurationException(PluginException.ErrorCode errorCode, String message, String configKey, Throwable cause) {
        super(errorCode, message, "Key: " + configKey, cause);
        this.configKey = configKey;
        this.invalidValue = null;
    }

    public String getConfigKey() {
        return this.configKey;
    }

    public Object getInvalidValue() {
        return this.invalidValue;
    }
}
