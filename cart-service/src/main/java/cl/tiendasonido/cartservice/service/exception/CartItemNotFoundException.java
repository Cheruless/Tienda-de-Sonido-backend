package cl.tiendasonido.cartservice.service.exception;

/**
 * El producto pedido no está en el carrito.
 *
 * <p>Se lanza desde {@link cl.tiendasonido.cartservice.service.CartService#removeItem}
 * cuando el usuario intenta quitar algo que no agregó (o un carrito
 * inexistente). El {@link cl.tiendasonido.cartservice.web.error.GlobalExceptionHandler}
 * la convierte en <strong>404 Not Found</strong> para el cliente.</p>
 *
 * @author Exequiel Pulgar Cerda
 */
public class CartItemNotFoundException extends RuntimeException {

    /**
     * Crea la excepción indicando qué producto no estaba en el carrito.
     *
     * @param productId producto que no se encontró; queda en el mensaje de error
     */
    public CartItemNotFoundException(String productId) {
        super("El producto no esta en el carrito: " + productId);
    }
}
