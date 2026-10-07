package dev.kidocolors.backend.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class ValidationCliTest {
    @TempDir Path directory;
    @Test void writesFixtureMetricsWithoutOverwritingAnExistingFile() throws Exception {
        Path input = directory.resolve("fixture.csv"), output = directory.resolve("fixture.json");
        Files.writeString(input, "analysis_id,unit_id,reference,detector,reviewer,evidence\n00000000-0000-0000-0000-000000000001,node,FAIL,FAIL,Fixture reviewer,Fixture evidence\n");
        ValidationCli.main(new String[] {input.toString(), output.toString()});
        var result = new ObjectMapper().readTree(Files.readAllBytes(output));
        assertEquals(1, result.path("endToEnd").path("tp").asInt());
        assertThrows(FileAlreadyExistsException.class, () -> ValidationCli.main(new String[] {input.toString(), output.toString()}));
    }
    @Test void refusesEmptyTemplateWithoutCreatingMetrics() throws Exception {
        Path input = directory.resolve("empty.csv"), output = directory.resolve("result.json");
        Files.writeString(input, "analysis_id,unit_id,reference,detector,reviewer,evidence\n");
        assertThrows(IllegalArgumentException.class, () -> ValidationCli.main(new String[] {input.toString(), output.toString()}));
        assertFalse(Files.exists(output));
    }
}
