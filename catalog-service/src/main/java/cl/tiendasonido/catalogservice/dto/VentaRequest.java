package cl.tiendasonido.catalogservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Una venta: la lista de productos con las unidades vendidas.
 *
 * <p>Se modela como un LOTE (y no como una venta por producto) para poder
 * descontar todo el stock en UNA sola transacción: o se descuenta todo, o no
 * se descuenta nada. Si llegara un producto sin stock suficiente, la
 * transacción se revierte completa y el cliente recibe 409 — sin necesidad
 * de "deshacer" descuentos parciales a mano.</p>
 *
 * @param items productos vendidos (mínimo uno)
 * @author Exequiel Pulgar Cerda
 */
public record VentaRequest(
        @NotEmpty(message = "La venta debe incluir al menos un producto")
        @Valid List<VentaItem> items) {
}
