package cl.tiendasonido.cartservice.service.exception;

public class CartItemNotFoundException extends RuntimeException {

    public CartItemNotFoundException(String productId) {
        super("El producto no esta en el carrito: " + productId);
    }
}
