package relish.relishAuthVelocity.validators;

import relish.relishAuthVelocity.config.Config;

public class PasswordValidator {
    private final Config config;
    private final int minLength;
    private final int maxLength;
    private final boolean requireUppercase;
    private final boolean requireLowercase;
    private final boolean requireNumbers;
    private final boolean requireSpecialChars;

    public PasswordValidator(Config config) {
        this.config = config;
        this.minLength = config.getInt("authentication.password.min-length", 6);
        this.maxLength = config.getInt("authentication.password.max-length", 32);
        this.requireUppercase = config.getBoolean("authentication.password.require-uppercase", false);
        this.requireLowercase = config.getBoolean("authentication.password.require-lowercase", false);
        this.requireNumbers = config.getBoolean("authentication.password.require-numbers", false);
        this.requireSpecialChars = config.getBoolean("authentication.password.require-special-chars", false);
    }

    public ValidationResult validate(String password, String confirm) {
        if (password == null || password.isEmpty()) {
            return new ValidationResult(false, "Password cannot be empty");
        }
        if (confirm != null && !password.equals(confirm)) {
            return new ValidationResult(false, "Passwords do not match");
        }
        if (password.length() < this.minLength) {
            return new ValidationResult(false, "Password must be at least " + this.minLength + " characters");
        }
        if (password.length() > this.maxLength) {
            return new ValidationResult(false, "Password must be at most " + this.maxLength + " characters");
        }
        if (this.requireUppercase && !password.matches(".*[A-Z].*")) {
            return new ValidationResult(false, "Password must contain at least one uppercase letter");
        }
        if (this.requireLowercase && !password.matches(".*[a-z].*")) {
            return new ValidationResult(false, "Password must contain at least one lowercase letter");
        }
        if (this.requireNumbers && !password.matches(".*[0-9].*")) {
            return new ValidationResult(false, "Password must contain at least one number");
        }
        if (this.requireSpecialChars && !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            return new ValidationResult(false, "Password must contain at least one special character");
        }
        return new ValidationResult(true, "Valid password");
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return this.valid;
        }

        public String getMessage() {
            return this.message;
        }
    }
}
