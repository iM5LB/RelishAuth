package relish.relishAuthVelocity.utils;

import at.favre.lib.crypto.bcrypt.BCrypt;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import relish.relishAuthVelocity.config.Config;

public final class PasswordHasher {
    private static final String DEFAULT_ALGORITHM = "argon2";
    private static final int DEFAULT_BCRYPT_ROUNDS = 12;
    private static final int DEFAULT_ARGON2_ITERATIONS = 10;
    private static final int DEFAULT_ARGON2_MEMORY = 65536;
    private static final int DEFAULT_ARGON2_PARALLELISM = 1;

    private static final Argon2 ARGON2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    private PasswordHasher() {
    }

    public static String hash(String password, String algorithm, Config config) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        String normalized = normalizeAlgorithm(algorithm);
        return switch (normalized) {
            case "bcrypt2y", "bcrypt" -> BCrypt.with(BCrypt.Version.VERSION_2Y)
                    .hashToString(getBcryptRounds(config), password.toCharArray());
            case "argon2" -> {
                int iterations = config != null
                        ? config.getInt("authentication.password.argon2.iterations", DEFAULT_ARGON2_ITERATIONS)
                        : DEFAULT_ARGON2_ITERATIONS;
                int memory = config != null
                        ? config.getInt("authentication.password.argon2.memory", DEFAULT_ARGON2_MEMORY)
                        : DEFAULT_ARGON2_MEMORY;
                int parallelism = config != null
                        ? config.getInt("authentication.password.argon2.parallelism", DEFAULT_ARGON2_PARALLELISM)
                        : DEFAULT_ARGON2_PARALLELISM;
                yield ARGON2.hash(iterations, memory, parallelism, password.toCharArray());
            }
            default -> throw new IllegalArgumentException("Unsupported password hashing algorithm: " + algorithm);
        };
    }

    @Deprecated
    public static String hash(String password, String algorithm) {
        return hash(password, algorithm, null);
    }

    public static boolean verify(String password, String hash) {
        if (password == null || hash == null) {
            return false;
        }

        try {
            if (isBcryptHash(hash)) {
                return BCrypt.verifyer().verify(password.toCharArray(), hash).verified;
            }
            if (isArgon2Hash(hash)) {
                return ARGON2.verify(hash, password.toCharArray());
            }
            return ARGON2.verify(hash, password.toCharArray());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String normalizeAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            return DEFAULT_ALGORITHM;
        }
        return algorithm.trim().toLowerCase();
    }

    private static int getBcryptRounds(Config config) {
        if (config == null) {
            return DEFAULT_BCRYPT_ROUNDS;
        }
        int rounds = config.getInt("authentication.password.bcrypt.rounds", DEFAULT_BCRYPT_ROUNDS);
        if (rounds < 4 || rounds > 31) {
            throw new IllegalArgumentException("authentication.password.bcrypt.rounds must be between 4 and 31");
        }
        return rounds;
    }

    private static boolean isArgon2Hash(String hash) {
        return hash.startsWith("$argon2");
    }

    private static boolean isBcryptHash(String hash) {
        return hash.startsWith("$2a$")
                || hash.startsWith("$2b$")
                || hash.startsWith("$2x$")
                || hash.startsWith("$2y$");
    }
}
