package cl.tiendasonido.cartservice.service;

/**
 * Forma exacta del producto que el carrito necesita copiar de catálogo:
 * identificador, nombre y precio.
 *
 * <p>Es un <em>record</em> (inmutable) porque es un dato que viaja de ida:
 * el carrito lo recibe, lo copia como snapshot y no lo modifica jamás.
 * Los tres campos son los mínimos que {@link CartService} necesita para
 * construir una línea del carrito.</p>
 *
 * <p><strong>Ojo:</strong> este tipo es el contrato con catálogo. Si
 * catálogo renombra {@code price} por {@code unitPrice} o cambia su tipo,
 * esta clase y quien la consume deben cambiar en conjunto (idealmente con un
 * test de forma/contrato que lo verifique).</p>
 *
 * @param id    identificador del producto
 * @param name  nombre visible del producto
 * @param price precio unitario en CLP (entero; nunca {@code double} para dinero)
 * @author Exequiel Pulgar Cerda
 */
public record ProductSummary(
        String id,
        String name,
        Long price
) {}
