package cl.tiendasonido.catalogservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Un producto y la cantidad vendida dentro de una {@link VentaRequest}.
 *
 * @param id       id del producto en catálogo
 * @param cantidad unidades vendidas (mínimo 1; un 0 o un negativo no tendrían
 *                 sentido y además romperían el descuento de stock)
 * @author Exequiel Pulgar Cerda
 */
public record VentaItem(
        @NotNull(message = "El producto es obligatorio") Long id,

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer cantidad) {
}
