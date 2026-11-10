package cl.tiendasonido.cartservice.service.exception;

public class ProductInvalidException extends RuntimeException {

    public ProductInvalidException(String productId) {
        super("El identificador de producto no es válido: " + productId);
    }
}
