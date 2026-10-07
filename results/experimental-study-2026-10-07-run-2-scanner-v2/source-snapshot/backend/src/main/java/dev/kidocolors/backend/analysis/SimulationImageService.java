package dev.kidocolors.backend.analysis;

import dev.kidocolors.backend.scanner.CaptureStore;
import dev.kidocolors.backend.scanner.ScanException;
import dev.kidocolors.core.Simulation;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

@Service
public class SimulationImageService {
    private final CaptureStore store;
    public SimulationImageService(CaptureStore store) { this.store = store; }

    public void generate(UUID id) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(store.read(id)));
            if (original == null) throw new IOException("Invalid original PNG");
            try {
                for (Simulation simulation : Simulation.values()) {
                    BufferedImage transformed = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    try {
                        int[] row = new int[original.getWidth()];
                        for (int y = 0; y < original.getHeight(); y++) {
                            original.getRGB(0, y, row.length, 1, row, 0, row.length);
                            for (int x = 0; x < row.length; x++) row[x] = simulation.applyPixel(row[x]);
                            transformed.setRGB(0, y, row.length, 1, row, 0, row.length);
                        }
                        ByteArrayOutputStream png = new ByteArrayOutputStream();
                        if (!ImageIO.write(transformed, "png", png)) throw new IOException("Missing PNG encoder");
                        store.write(id, simulation, png.toByteArray());
                    } finally { transformed.flush(); }
                }
            } finally { original.flush(); }
        } catch (IOException exception) {
            throw new ScanException(ScanException.Code.STORAGE, "Não foi possível gerar as imagens simuladas.", exception);
        }
    }
}
