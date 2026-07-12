package relish.relishAuthVelocity.utils;

import java.security.SecureRandom;

public final class RandomPasswordGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*".toCharArray();

    private RandomPasswordGenerator() {
        throw new AssertionError((Object)"Cannot instantiate utility class");
    }

    public static String generate(int length) {
        int safeLength = Math.max(8, Math.min(length, 64));
        char[] out = new char[safeLength];
        for (int i = 0; i < safeLength; ++i) {
            out[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }

    public static String generateDefault() {
        return RandomPasswordGenerator.generate(12);
    }
}
