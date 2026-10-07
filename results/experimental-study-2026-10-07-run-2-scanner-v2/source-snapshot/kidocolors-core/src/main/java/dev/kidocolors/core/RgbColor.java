package dev.kidocolors.core;

import java.util.Locale;

/** Opaque 8-bit sRGB. Alpha compositing belongs to the scanner. */
public record RgbColor(int red, int green, int blue) {
    public RgbColor {
        if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
            throw new IllegalArgumentException("RGB channels must be between 0 and 255");
        }
    }

    public static RgbColor fromHex(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Expected #RRGGBB");
        }
        return new RgbColor(Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16), Integer.parseInt(hex.substring(5, 7), 16));
    }

    public String toHex() {
        return String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue);
    }

    static double linearize(int channel) {
        double srgb = channel / 255.0;
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }

    public double luminance() {
        return 0.2126 * linearize(red) + 0.7152 * linearize(green) + 0.0722 * linearize(blue);
    }
}
