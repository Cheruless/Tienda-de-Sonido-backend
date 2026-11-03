package cl.tiendasonido.cartservice.service.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productId) {
        super("Producto no encontrado: " + productId);
    }
}
