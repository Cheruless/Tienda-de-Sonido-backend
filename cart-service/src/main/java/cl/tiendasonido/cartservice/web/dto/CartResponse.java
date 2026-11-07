package cl.tiendasonido.cartservice.web.dto;

import java.util.List;

/**
 * Respuesta completa del carrito: sus líneas y el total general.
 *
 * <p>Este es el contrato JSON de {@code GET /api/cart} y
 * {@code POST /api/cart/items}. No expone campos internos como
 * {@code version} (propiedad del optimistic locking) ni
 * {@code createdAt}/{@code updatedAt}: la API muestra solo lo que el
 * frontend necesita.</p>
 *
 * <p>Los totales se calculan en cada respuesta desde los items; no se
 * persisten, así que nunca pueden quedar desactualizados en la BD.</p>
 *
 * @param items líneas del carrito; vacío si el usuario aún no agregó nada
 * @param total suma de los {@code lineTotal}, en CLP (entero)
 * @author Exequiel Pulgar Cerda
 */
public record CartResponse(
        List<CartItemResponse> items,
        long total) {
}
