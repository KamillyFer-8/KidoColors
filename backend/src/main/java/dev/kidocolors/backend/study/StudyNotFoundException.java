package dev.kidocolors.backend.study;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(HttpStatus.NOT_FOUND)
public class StudyNotFoundException extends RuntimeException {
    public StudyNotFoundException() { super("Lote não encontrado."); }
}
