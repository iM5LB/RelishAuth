package relish.relishAuthVelocity.utils;

import java.awt.Color;
import relish.relishAuthVelocity.config.Config;

public class ColorConfig {
    public static Color getBlue(Config config) {
        return ColorConfig.parseColor(config.getString("discord.colors.blue", "88,101,242"));
    }

    public static Color getRed(Config config) {
        return ColorConfig.parseColor(config.getString("discord.colors.red", "237,66,69"));
    }

    public static Color getGreen(Config config) {
        return ColorConfig.parseColor(config.getString("discord.colors.green", "87,242,135"));
    }

    public static Color getWarning(Config config) {
        return ColorConfig.parseColor(config.getString("discord.colors.warning", "255,193,7"));
    }

    private static Color parseColor(String rgb) {
        try {
            String[] parts = rgb.split(",");
            if (parts.length == 3) {
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return new Color(r, g, b);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return new Color(88, 101, 242);
    }
}
