package cl.tiendasonido.cartservice.service.exception;

/**
 * El producto pedido no está en el carrito (o no existe en catálogo).
 *
 * <p>Excepción de dominio: se lanza desde la capa de servicio o desde
 * {@link cl.tiendasonido.cartservice.service.CatalogClient} y la traduce a
 * HTTP el {@link cl.tiendasonido.cartservice.web.error.GlobalExceptionHandler},
 * que la mapea a <strong>404 Not Found</strong>.</p>
 *
 * <p>Se usa en lugar de devolver {@code null} o un booleano porque hace
 * explícito el fallo en la firma del método y evita el error olvidado de
 * no verificar el retorno.</p>
 *
 * @author Exequiel Pulgar Cerda
 */
public class ProductNotFoundException extends RuntimeException {

    /**
     * Crea la excepción indicando qué producto no existe en catálogo.
     *
     * @param productId identificador que no se encontró; se incluye en el mensaje
     */
    public ProductNotFoundException(String productId) {
        super("Producto no encontrado: " + productId);
    }
}
