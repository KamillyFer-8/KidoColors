package dev.kidocolors.backend.scanner;

import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.UUID;
import dev.kidocolors.core.Simulation;

@Component
public class CaptureStore {
    private final Path root;
    public CaptureStore(ScannerSettings settings) {
        root = Path.of(settings.getStoragePath()).toAbsolutePath().normalize();
    }
    public void write(UUID id, byte[] png) {
        writeFile(path(id), png);
    }
    public void write(UUID id, Simulation simulation, byte[] png) { writeFile(path(id, simulation), png); }
    private void writeFile(Path target, byte[] png) {
        try {
            Files.createDirectories(root);
            Files.write(target, png);
        } catch (IOException exception) {
            throw new ScanException(ScanException.Code.STORAGE, "Não foi possível salvar a screenshot.", exception);
        }
    }
    public byte[] read(UUID id) throws IOException { return Files.readAllBytes(path(id)); }
    public byte[] read(UUID id, Simulation simulation) throws IOException { return Files.readAllBytes(path(id, simulation)); }
    private Path path(UUID id) { return root.resolve(id + ".png"); }
    private Path path(UUID id, Simulation simulation) { return root.resolve(id + "-" + simulation.name() + ".png"); }
}
