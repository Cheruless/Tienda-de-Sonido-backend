package cl.tiendasonido.cartservice.web.error;

import cl.tiendasonido.cartservice.service.exception.CartItemNotFoundException;
import cl.tiendasonido.cartservice.service.exception.CatalogUnavailableException;
import cl.tiendasonido.cartservice.service.exception.ConflictException;
import cl.tiendasonido.cartservice.service.exception.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las excepciones de la aplicación a respuestas HTTP con un formato
 * de error consistente.
 *
 * <p><strong>Por qué existe:</strong> sin este archivo, cada excepción que
 * llegue al controlador produce una página de error genérica de Spring (o
 * un 500 con stack trace en el log). Aquí, en un solo lugar, se decide qué
 * código de estado y qué cuerpo JSON recibe el cliente para cada fallo.
 * Centralizarlo evita repetir el mapeo en cada controlador y garantiza que
 * todos los errores se vean igual.</p>
 *
 * <p><strong>Tabla de traducción de este servicio:</strong></p>
 * <ul>
 *   <li>{@code MethodArgumentNotValidException} &rarr; <strong>400</strong>
 *       (fallo de validación: campo vacío, cantidad &lt; 1...)</li>
 *   <li>{@code HttpMessageNotReadableException} &rarr; <strong>400</strong>
 *       (JSON malformado o ausente)</li>
 *   <li>{@code MethodArgumentTypeMismatchException} &rarr; <strong>400</strong>
 *       (parámetro con tipo inválido)</li>
 *   <li>{@link ProductNotFoundException} &rarr; <strong>404</strong>
 *       (el producto no existe en catálogo)</li>
 *   <li>{@link CartItemNotFoundException} &rarr; <strong>404</strong>
 *       (el producto no está en el carrito)</li>
 *   <li>{@link ConflictException} &rarr; <strong>409</strong>
 *       (concurrencia: se agotaron los reintentos)</li>
 *   <li>{@link CatalogUnavailableException} &rarr; <strong>503</strong>
 *       (catálogo no responde)</li>
 * </ul>
 *
 * <p>La autenticación (401) y autorización (403) no aparecen aquí: las
 * resuelve Spring Security antes de que la petición llegue a este advice
 * (ver {@code SecurityConfig}).</p>
 *
 * @author Exequiel Pulgar Cerda
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maneja los fallos de validación de los DTOs ({@code @Valid}).
     *
     * <p>Recopila el primer mensaje de cada campo inválido en
     * {@code detalles}, para que el frontend pueda resaltar exactamente qué
     * corregir. {@code putIfAbsent} evita duplicados si un campo tiene
     * varias violaciones.</p>
     *
     * @param ex excepción con los errores de campo
     * @return 400 con {@code {"error": ..., "detalles": {campo: mensaje}}}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> detalles = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> detalles.putIfAbsent(e.getField(), e.getDefaultMessage()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Datos invalidos");
        body.put("detalles", detalles);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Maneja cuerpos JSON ilegibles (sintaxis rota o body ausente).
     *
     * @param ex excepción de lectura del body
     * @return 400 con mensaje genérico (no se filtra el detalle técnico)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "El cuerpo de la peticion es invalido o esta ausente"));
    }

    /**
     * Maneja parámetros con tipo inválido (por ejemplo un {@code productId}
     * que no es un UUID cuando la ruta lo exige).
     *
     * @param ex excepción con el nombre y valor del parámetro
     * @return 400 indicando cuál parámetro está mal
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "El valor del parametro '" + ex.getName() + "' no es valido"));
    }

    /**
     * Producto inexistente en catálogo (consultar o agregar algo que nadie vende).
     *
     * @param ex excepción de dominio
     * @return 404 con el id que no se encontró
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleProductNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    /**
     * Intento de quitar del carrito un producto que no está en él.
     *
     * @param ex excepción de dominio
     * @return 404 con el id que no estaba en el carrito
     */
    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCartItemNotFound(CartItemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    /**
     * Concurrencia agotada: el carrito cambió en cada reintento.
     *
     * <p>El 409 le dice al cliente que el fallo es temporal y que reintentar
     * tiene sentido (a diferencia del 400, que es error del request).</p>
     *
     * @param ex excepción con el mensaje para el cliente
     * @return 409 con la explicación
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    /**
     * Catálogo caído o inalcanzable.
     *
     * <p>El mensaje es fijo a propósito: no se expone al cliente el error
     * técnico real (queda en el log vía la excepción original).</p>
     *
     * @param ex excepción con la causa original para el log
     * @return 503 pidiendo reintento
     */
    @ExceptionHandler(CatalogUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleCatalogUnavailable(CatalogUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Catalogo no disponible, reintenta mas tarde"));
    }
}
