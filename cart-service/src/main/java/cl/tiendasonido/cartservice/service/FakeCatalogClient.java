package cl.tiendasonido.cartservice.service;

import cl.tiendasonido.cartservice.service.exception.ProductNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * <strong>Adaptador</strong> de {@link CatalogClient} que responde con datos
 * fijos en memoria, sin llamar a ningún servicio externo.
 *
 * <p><strong>Para qué existe:</strong> desacoplar el desarrollo del carrito
 * de que catalog-service ya esté escrito y desplegado. Permite desarrollar y
 * testear todo el flujo de {@code CartService} (agregar, fusionar, errores)
 * hoy mismo; cuando exista el servicio real, se cambia
 * {@code catalog.client} de {@code fake} a {@code http} y esta clase queda
 * inyectable fuera (la anotación {@code @ConditionalOnProperty} la retira
 * del contexto).</p>
 *
 * <p><strong>Qué hace y qué no:</strong> solo <em>busca</em> un producto por
 * id en un mapa y lo devuelve, o lanza {@link ProductNotFoundException} si no
 * existe. <strong>No toca el carrito</strong>: fusionar cantidades, sumar
 * totales y guardar en MongoDB es responsabilidad de
 * {@link CartService} y {@link cl.tiendasonido.cartservice.domain.Cart}.</p>
 *
 * @author Exequiel Pulgar Cerda
 * @see CatalogClient
 */
@Service
@ConditionalOnProperty(name = "catalog.client", havingValue = "fake", matchIfMissing = true)
public class FakeCatalogClient implements CatalogClient {

    /** Catálogo de prueba: basta para recorrer todos los casos del carrito. */
    private final Map<String, ProductSummary> productos = Map.of(
            "demo-1", new ProductSummary("demo-1", "Guitarra electrica", 199_990L),
            "demo-2", new ProductSummary("demo-2", "Bajo 4 cuerdas", 149_990L),
            "demo-3", new ProductSummary("demo-3", "Amplificador 50W", 89_990L));

    /**
     * Busca el producto en el mapa en memoria.
     *
     * <p>Firma idéntica a la futura implementación HTTP: quien llama no sabe
     * (ni debe saber) si los datos vinieron de aquí o de una petición REST.</p>
     *
     * @param productId identificador a buscar ({@code "demo-1"}, {@code "demo-2"}, {@code "demo-3"})
     * @return el producto completo
     * @throws ProductNotFoundException si el id no está en el catálogo falso
     */
    @Override
    public ProductSummary getProduct(String productId) {
        ProductSummary producto = productos.get(productId);
        if (producto == null) {
            throw new ProductNotFoundException(productId);
        }
        return producto;
    }
}
