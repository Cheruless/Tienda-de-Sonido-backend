package cl.tiendasonido.cartservice.service;

/**
 * <strong>Puerto</strong> (interfaz) hacia catálogo: define <em>qué</em>
 * necesita el carrito de afuera, sin decidir <em>cómo</em> se obtiene.
 *
 * <p>Este es el patrón de <em>puerto y adaptador</em> (hexagonal a secas):
 * {@code CartService} depende de esta interfaz, no de una clase concreta.
 * Gracias a eso hoy puede inyectarse {@link FakeCatalogClient} (catálogo aún
 * no existe) y mañana una implementación HTTP con {@code RestClient}, sin
 * tocar una línea de {@code CartService} ni de sus tests.</p>
 *
 * <p>Regla: quien implementa esta interfaz traduce los errores del medio
 * externo a las excepciones de dominio del carrito
 * ({@link cl.tiendasonido.cartservice.service.exception.ProductNotFoundException},
 * {@link cl.tiendasonido.cartservice.service.exception.CatalogUnavailableException}),
 * para que el resto del servicio no se entere de si vino de un mapa en
 * memoria, de una llamada HTTP o de una cola.</p>
 *
 * @author Exequiel Pulgar Cerda
 */
public interface CatalogClient {

    /**
     * Obtiene el resumen de un producto por su identificador.
     *
     * @param productId identificador del producto en catálogo
     * @return el producto con nombre y precio (los datos que el carrito necesita copiar)
     * @throws cl.tiendasonido.cartservice.service.exception.ProductNotFoundException si el producto no existe
     * @throws cl.tiendasonido.cartservice.service.exception.CatalogUnavailableException si catálogo no responde
     */
    ProductSummary getProduct(String productId);
}
