package relish.relishAuthVelocity.utils;

import java.util.Locale;

public final class DurationUtil {
    private DurationUtil() {
        throw new AssertionError((Object)"Cannot instantiate utility class");
    }

    public static long parseToMillis(String duration) {
        if (duration == null || duration.equals("0")) {
            return 0L;
        }
        String numPart = duration.replaceAll("[^0-9]", "");
        if (numPart.isEmpty()) {
            return 0L;
        }
        String unitPart = duration.replaceAll("[0-9]", "").toLowerCase();
        try {
            long num = Long.parseLong(numPart);
            return switch (unitPart) {
                case "s" -> num * 1000L;
                case "m" -> num * 60L * 1000L;
                case "h" -> num * 60L * 60L * 1000L;
                case "d" -> num * 24L * 60L * 60L * 1000L;
                default -> num * 60L * 1000L;
            };
        }
        catch (NumberFormatException e) {
            return 3600000L;
        }
    }

    public static long parseToSeconds(String duration) {
        return DurationUtil.parseToMillis(duration) / 1000L;
    }

    public static String formatDuration(String duration) {
        long amount;
        if (duration == null || duration.isEmpty()) {
            return "Unknown";
        }
        String normalized = duration.toLowerCase(Locale.ROOT).trim();
        if (normalized.equals("0")) {
            return "No Save";
        }
        if (!DurationUtil.isValidDuration(normalized)) {
            return duration;
        }
        String numPart = normalized.replaceAll("[^0-9]", "");
        String unitPart = normalized.replaceAll("[0-9]", "");
        try {
            amount = Long.parseLong(numPart);
        }
        catch (NumberFormatException e) {
            return duration;
        }
        if (amount <= 0L) {
            return duration;
        }
        String unitLabel = switch (unitPart) {
            case "s" -> {
                if (amount == 1L) {
                    yield "Second";
                }
                yield "Seconds";
            }
            case "m" -> {
                if (amount == 1L) {
                    yield "Minute";
                }
                yield "Minutes";
            }
            case "h" -> {
                if (amount == 1L) {
                    yield "Hour";
                }
                yield "Hours";
            }
            case "d" -> {
                if (amount == 1L) {
                    yield "Day";
                }
                yield "Days";
            }
            default -> amount == 1L ? "Minute" : "Minutes";
        };
        return amount + " " + unitLabel;
    }

    public static boolean isValidDuration(String duration) {
        if (duration == null) {
            return false;
        }
        String normalized = duration.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.equals("0")) {
            return true;
        }
        if (!normalized.matches("\\d+[smhd]")) {
            return false;
        }
        try {
            long amount = Long.parseLong(normalized.replaceAll("[^0-9]", ""));
            return amount > 0L;
        }
        catch (NumberFormatException e) {
            return false;
        }
    }
}
