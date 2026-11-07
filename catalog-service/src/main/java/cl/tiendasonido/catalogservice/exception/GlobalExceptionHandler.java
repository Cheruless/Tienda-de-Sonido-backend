package cl.tiendasonido.catalogservice.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorRespuesta> noEncontrado(RecursoNoEncontradoException ex) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorRespuesta> conflicto(ConflictoException ex) {
        return responder(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> detalles = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> detalles.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return responder(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos", detalles);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> cuerpoInvalido(HttpMessageNotReadableException ex) {
        return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es válido", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorRespuesta> integridad(DataIntegrityViolationException ex) {
        return responder(HttpStatus.CONFLICT, "La operación viola una restricción de la base de datos", null);
    }

    private ResponseEntity<ErrorRespuesta> responder(HttpStatus estado, String mensaje, Map<String, String> detalles) {
        return ResponseEntity.status(estado).body(new ErrorRespuesta(estado.value(), mensaje, detalles));
    }
}
