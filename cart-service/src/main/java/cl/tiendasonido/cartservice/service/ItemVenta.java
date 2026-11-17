package cl.tiendasonido.cartservice.service;

/**
 * Un producto y las unidades vendidas dentro de una compra.
 *
 * Es la forma que el carrito manda a catálogo: catálogo solo necesita saber
 * QUÉ se vendió y CUÁNTO, no cómo se veía el producto.
 *
 * @param productId id numérico del producto (como string: así vive en el carrito)
 * @param cantidad  unidades vendidas (mínimo 1)
 * @author Exequiel Pulgar Cerda
 */
public record ItemVenta(String productId, int cantidad) {
}
