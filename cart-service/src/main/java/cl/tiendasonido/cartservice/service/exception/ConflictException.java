package cl.tiendasonido.cartservice.service.exception;

/**
 * Se agotaron los reintentos de concurrencia: el carrito cambió debajo de
 * las manos de esta petición.
 *
 * <p>Aquí está la diferencia entre un sistema robusto y uno que pierde datos
 * en silencio: sin esta señal, el usuario recibiría un 500 genérico (o peor,
 * una confirmación de éxito con datos perdidos). Con ella, el cliente sabe
 * que debe volver a intentar. El
 * {@link cl.tiendasonido.cartservice.web.error.GlobalExceptionHandler} la
 * mapea a <strong>409 Conflict</strong>.</p>
 *
 * @author Exequiel Pulgar Cerda
 * @see cl.tiendasonido.cartservice.service.CartService
 */
public class ConflictException extends RuntimeException {

    /**
     * Crea la excepción con el mensaje que verá el cliente.
     *
     * @param message explicación legible para quien consuma la API
     */
    public ConflictException(String message) {
        super(message);
    }
}
