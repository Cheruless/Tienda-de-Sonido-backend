package cl.tiendasonido.cartservice.service.exception;

/**
 * Catálogo no está disponible (caído, timeout o error 5xx).
 *
 * <p>Se lanza cuando el cliente HTTP hacia catálogo falla. El
 * {@link cl.tiendasonido.cartservice.web.error.GlobalExceptionHandler} la
 * convierte en <strong>503 Service Unavailable</strong>, que le dice al
 * cliente "no es tu culpa, reintenta más tarde", a diferencia del 404
 * ("ese producto no existe").</p>
 *
 * <p>Lleva la causa original ({@code cause}) para que el log técnico
 * conserve el detalle real (timeout, error de red...) mientras el usuario
 * ve un mensaje amable.</p>
 *
 * @author Exequiel Pulgar Cerda
 */
public class CatalogUnavailableException extends RuntimeException {

    /**
     * Crea la excepción conservando el error original para el log técnico.
     *
     * @param message mensaje legible para el usuario
     * @param cause   error original de la llamada a catálogo, para el log
     */
    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
