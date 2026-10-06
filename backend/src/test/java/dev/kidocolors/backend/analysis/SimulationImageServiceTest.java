package dev.kidocolors.backend.analysis;

import dev.kidocolors.backend.scanner.*;
import dev.kidocolors.core.Simulation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SimulationImageServiceTest {
    @TempDir Path directory;
    @Test void transformsRealPngPreservingDimensionsAlphaAndOriginal() throws Exception {
        ScannerSettings settings = new ScannerSettings(); settings.setStoragePath(directory.toString());
        CaptureStore store = new CaptureStore(settings);
        BufferedImage source = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x80ff0000); source.setRGB(1, 0, 0xffffffff); source.setRGB(2, 0, 0xff000000);
        ByteArrayOutputStream png = new ByteArrayOutputStream(); ImageIO.write(source, "png", png);
        UUID id = UUID.randomUUID(); store.write(id, png.toByteArray());
        new SimulationImageService(store).generate(id);
        assertArrayEquals(png.toByteArray(), store.read(id));
        for (Simulation simulation : Simulation.values()) {
            BufferedImage result = ImageIO.read(new ByteArrayInputStream(store.read(id, simulation)));
            assertEquals(3, result.getWidth()); assertEquals(1, result.getHeight());
            assertEquals(simulation.applyPixel(0x80ff0000), result.getRGB(0, 0));
            assertEquals(0xffffffff, result.getRGB(1, 0)); assertEquals(0xff000000, result.getRGB(2, 0));
        }
        assertEquals(ScanException.Code.STORAGE,
                assertThrows(ScanException.class, () -> new SimulationImageService(store).generate(UUID.randomUUID())).code());
    }
}
