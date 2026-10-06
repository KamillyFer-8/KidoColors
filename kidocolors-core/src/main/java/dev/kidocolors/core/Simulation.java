package dev.kidocolors.core;

import java.util.Objects;

/** Machado severity 1.0 matrices applied to linear sRGB. Tritanopia is an approximation. */
public enum Simulation {
    PROTANOPIA(new double[][]{{0.152286, 1.052583, -0.204868}, {0.114503, 0.786281, 0.099216}, {-0.003882, -0.048116, 1.051998}}),
    DEUTERANOPIA(new double[][]{{0.367322, 0.860646, -0.227968}, {0.280085, 0.672501, 0.047413}, {-0.011820, 0.042940, 0.968881}}),
    TRITANOPIA(new double[][]{{1.255528, -0.076749, -0.178779}, {-0.078411, 0.930809, 0.147602}, {0.004733, 0.691367, 0.303900}});

    private final double[][] matrix;
    private static final double[] LINEAR = new double[256];
    static { for (int value = 0; value < 256; value++) LINEAR[value] = RgbColor.linearize(value); }
    Simulation(double[][] matrix) { this.matrix = matrix; }

    public RgbColor apply(RgbColor color) {
        Objects.requireNonNull(color);
        int pixel = applyPixel((color.red() << 16) | (color.green() << 8) | color.blue());
        return new RgbColor((pixel >>> 16) & 255, (pixel >>> 8) & 255, pixel & 255);
    }

    /** Preserves alpha; PNG IO stays outside the mathematical Core. */
    public int applyPixel(int argb) {
        double red = LINEAR[(argb >>> 16) & 255], green = LINEAR[(argb >>> 8) & 255], blue = LINEAR[argb & 255];
        return (argb & 0xff000000) | (channel(0, red, green, blue) << 16)
                | (channel(1, red, green, blue) << 8) | channel(2, red, green, blue);
    }

    private int channel(int row, double red, double green, double blue) {
        double linear = Math.clamp(matrix[row][0] * red + matrix[row][1] * green + matrix[row][2] * blue, 0, 1);
        double srgb = linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
        return (int) Math.round(255 * srgb);
    }
}
