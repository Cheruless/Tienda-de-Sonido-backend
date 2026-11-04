package cl.tiendasonido.cartservice.web.dto;

/**
 * Una línea del carrito en la respuesta JSON.
 *
 * <p>Es un <em>record</em>: inmutable, con {@code equals}/{@code hashCode}/
 * {@code toString} gratis y una sintaxis que muestra de un vistazo qué
 * campos tiene. Ideal para DTOs, que solo transportan datos.</p>
 *
 * <p>Precios en CLP enteros ({@code long}) porque el peso chileno no tiene
 * decimales; por eso no se usa {@code double}, cuyos errores de coma flotante
 * descalzarían los totales.</p>
 *
 * @param productId  identificador del producto
 * @param productName nombre copiado de catálogo al agregarlo (snapshot)
 * @param unitPrice  precio unitario en CLP al momento de agregarlo (snapshot)
 * @param quantity   unidades en el carrito
 * @param lineTotal  total de la línea ({@code unitPrice * quantity}), calculado al responder
 * @author Exequiel Pulgar Cerda
 */
public record CartItemResponse(
        String productId,
        String productName,
        long unitPrice,
        int quantity,
        long lineTotal) {
}
