package dev.kidocolors.backend.analysis;

public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException() { super("Esta análise não possui um relatório completo disponível."); }
}
