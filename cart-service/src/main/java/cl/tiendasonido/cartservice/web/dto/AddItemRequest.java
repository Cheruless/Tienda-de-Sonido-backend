package cl.tiendasonido.cartservice.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Body de {@code POST /api/cart/items}: qué producto y cuántas unidades.
 *
 * <p><strong>Por qué no trae precio:</strong> el cliente jamás debe poder
 * indicar cuánto paga. Precio y nombre los copia el servidor desde catálogo,
 * así que un cliente malicioso solo podría mandar el identificador del
 * producto y una cantidad.</p>
 *
 * <p>Las anotaciones {@code @NotBlank} y {@code @Min} son la validación
 * declarativa de Jakarta Bean Validation: Spring las ejecuta automáticamente
 * antes de entrar al controlador y, si fallan, el
 * {@link cl.tiendasonido.cartservice.web.error.GlobalExceptionHandler}
 * traduce el error a un 400 con el detalle de cada campo.</p>
 *
 * @param productId identificador del producto a agregar (obligatorio)
 * @param quantity  unidades a agregar; mínimo 1 (el carrito no admite 0 ni negativos)
 * @author Exequiel Pulgar Cerda
 */
public record AddItemRequest(
        @NotBlank(message = "El producto es obligatorio") String productId,

        @Min(value = 1, message = "La cantidad debe ser al menos 1") int quantity) {
}
