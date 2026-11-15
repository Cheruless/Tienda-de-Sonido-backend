package cl.tiendasonido.cartservice.client;

/**
 * DTO que refleja el JSON de un producto devuelto por catalog-service.
 *
 * <p>Se mapea tal cual llega (id Long, nombres en español, precio Integer en CLP)
 * y luego {@link CatalogoMapper} lo traduce al contrato interno del carrito.</p>
 */
public record CatalogoProductoDto(
        Long id,
        String nombre,
        Integer precio,
        Integer stock) {
}
