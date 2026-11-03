package cl.tiendasonido.cartservice.web.dto;

/**
 * Precios en CLP enteros. lineTotal se calcula, no se persiste.
 */
public record CartItemResponse(
        String productId,
        String productName,
        long unitPrice,
        int quantity,
        long lineTotal) {
}
