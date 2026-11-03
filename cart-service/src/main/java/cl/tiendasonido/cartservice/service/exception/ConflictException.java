package cl.tiendasonido.cartservice.service.exception;

/**
 * El carrito fue modificado por otra peticion y se agotaron los reintentos.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
