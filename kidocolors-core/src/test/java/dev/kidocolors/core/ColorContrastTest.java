package dev.kidocolors.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ColorContrastTest {
    private final RgbColor black = new RgbColor(0, 0, 0);
    private final RgbColor white = new RgbColor(255, 255, 255);

    @Test void convertsHexAndRgb() {
        assertEquals(new RgbColor(18, 171, 239), RgbColor.fromHex("#12abEF"));
        assertEquals("#12ABEF", new RgbColor(18, 171, 239).toHex());
        assertEquals(black, RgbColor.fromHex(black.toHex()));
        assertEquals(white, RgbColor.fromHex(white.toHex()));
    }
    @ParameterizedTest @ValueSource(strings = {"", "FFFFFF", "#FFF", "#GG0000", "#FFFFFFFF", " #123456"})
    void rejectsInvalidHex(String value) {
        assertThrows(IllegalArgumentException.class, () -> RgbColor.fromHex(value));
    }
    @Test void rejectsInvalidChannelsAndNullHex() {
        assertThrows(IllegalArgumentException.class, () -> new RgbColor(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RgbColor(0, 256, 0));
        assertThrows(IllegalArgumentException.class, () -> new RgbColor(0, 0, 256));
        assertThrows(IllegalArgumentException.class, () -> RgbColor.fromHex(null));
    }
    @Test void knownLuminancesAndTransferBoundary() {
        assertEquals(0, black.luminance(), 1e-12);
        assertEquals(1, white.luminance(), 1e-12);
        assertEquals(0.2126, new RgbColor(255, 0, 0).luminance(), 1e-12);
        assertEquals(0.003035269835488375, RgbColor.linearize(10), 1e-12);
        assertEquals(0.003346535763899161, RgbColor.linearize(11), 1e-12);
    }
    @Test void contrastIsSymmetricAndHasKnownExtremes() {
        assertEquals(21, Contrast.ratio(black, white), 1e-12);
        assertEquals(1, Contrast.ratio(white, white), 1e-12);
        RgbColor gray = RgbColor.fromHex("#777777");
        assertEquals(4.478089453577214, Contrast.ratio(gray, white), 1e-10);
        assertEquals(Contrast.ratio(gray, white), Contrast.ratio(white, gray));
        assertFalse(Contrast.assess(gray, white, 16, 400).passesAA());
        assertTrue(Contrast.assess(gray, white, 24, 400).passesAA());
    }
    @Test void appliesLargeTextBoundariesAndDoesNotRoundBeforeComparison() {
        assertEquals(3, Contrast.assess(black, white, 24, 400).aaThreshold());
        assertEquals(4.5, Contrast.assess(black, white, 23.99, 400).aaThreshold());
        assertEquals(3, Contrast.assess(black, white, 14 * 96.0 / 72, 700).aaThreshold());
        assertEquals(4.5, Contrast.assess(black, white, 18.66, 700).aaThreshold());
        assertTrue(Contrast.assess(black, white, 16, 400).passesAAA());
        assertFalse(new Contrast.Assessment(4.49999, 4.5, 7).passesAA());
    }
    @Test void rejectsInvalidTypography() {
        for (double size : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> Contrast.assess(black, white, size, 400));
        }
        assertThrows(IllegalArgumentException.class, () -> Contrast.assess(black, white, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> Contrast.assess(black, white, 16, 1001));
    }
}
