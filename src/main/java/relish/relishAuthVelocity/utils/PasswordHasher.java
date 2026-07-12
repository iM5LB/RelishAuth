package relish.relishAuthVelocity.utils;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import at.favre.lib.crypto.bcrypt.BCrypt;
import relish.relishAuthVelocity.config.Config;

public class PasswordHasher {
    
    private static final Argon2 argon2 = Argon2Factory.create();
    
    public static String hash(String password, String algorithm, Config config) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        
        if (algorithm.equalsIgnoreCase("bcrypt2y") || algorithm.equalsIgnoreCase("bcrypt")) {
            int rounds = config.getInt("authentication.password.bcrypt.rounds", 12);
            return BCrypt.withDefaults().hashToString(rounds, password.toCharArray());
        }
        
        int iterations = config.getInt("authentication.password.argon2.iterations", 10);
        int memory = config.getInt("authentication.password.argon2.memory", 65536);
        int parallelism = config.getInt("authentication.password.argon2.parallelism", 1);
        
        return argon2.hash(iterations, memory, parallelism, password.toCharArray());
    }
    
    @Deprecated
    public static String hash(String password, String algorithm) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        
        if (algorithm.equalsIgnoreCase("bcrypt2y") || algorithm.equalsIgnoreCase("bcrypt")) {
            return BCrypt.withDefaults().hashToString(12, password.toCharArray());
        }
        
        return argon2.hash(10, 65536, 1, password.toCharArray());
    }
    
    public static boolean verify(String password, String hash) {
        if (password == null || hash == null) {
            return false;
        }
        
        if (hash.startsWith("$argon2")) {
            return argon2.verify(hash, password.toCharArray());
        }
        
        if (hash.startsWith("$2y$") || hash.startsWith("$2a$") || hash.startsWith("$2b$")) {
            return BCrypt.verifyer().verify(password.toCharArray(), hash).verified;
        }
        
        return argon2.verify(hash, password.toCharArray());
    }
}
