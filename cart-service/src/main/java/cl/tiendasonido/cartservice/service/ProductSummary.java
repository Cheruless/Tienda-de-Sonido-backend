package cl.tiendasonido.cartservice.service;

/**
 * Snapshot del producto necesario para el carrito.
 */
public record ProductSummary(
        String id,
        String name,
        Long price
) {}
