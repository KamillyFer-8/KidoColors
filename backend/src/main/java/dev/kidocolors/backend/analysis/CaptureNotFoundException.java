package dev.kidocolors.backend.analysis;

public class CaptureNotFoundException extends RuntimeException {
    public CaptureNotFoundException() { super("Esta análise não possui uma captura disponível."); }
}
