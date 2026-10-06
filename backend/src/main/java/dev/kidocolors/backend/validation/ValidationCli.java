package dev.kidocolors.backend.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;

/** Does not start Spring, contact websites, or require a database. */
public final class ValidationCli {
    private ValidationCli() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Uso: ValidationCli rotulos.csv resultado.json");
        Path input = Path.of(args[0]);
        if (Files.size(input) > 1_048_576) throw new IllegalArgumentException("O CSV deve ter no máximo 1 MiB.");
        var result = ValidationEvaluator.evaluate(Files.readAllBytes(input));
        byte[] json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
        Files.write(Path.of(args[1]), json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        System.out.println("Métricas calculadas a partir dos rótulos fornecidos. Arquivo: " + args[1]);
    }
}
