package cl.tiendasonido.authservice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Falla @Valid: campos vacíos, email inválido, tamaños, etc.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> detalles = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(e -> detalles.putIfAbsent(e.getField(), e.getDefaultMessage()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Datos inválidos");
        body.put("detalles", detalles);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // JSON mal formado o ausente
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "El cuerpo de la petición es inválido o está ausente"));
    }

    // Parámetro de ruta con tipo incorrecto, por ejemplo /users/abc/activo
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "El valor del parámetro '" + ex.getName() + "' no es válido"));
    }
}